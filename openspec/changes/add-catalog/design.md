# Design

## Context

The project has only the skeleton: Spring Boot 4 with Spring Data JPA, Flyway with an empty
baseline migration `V1__baseline.sql`, and a global error handler in `common` that renders
`ApiException` subclasses (`NotFoundException`, `ConflictException`) as problem+json with `code`.
Validation errors from `@Valid @RequestBody` already map to `VALIDATION_FAILED`. Errors from
`@RequestParam` constraints and type mismatches currently fall back to the generic `BAD_REQUEST`
code. See proposal.md for scope.

## Goals / Non-Goals

**Goals:**
- Data model for branches, books and copies that loans, holds and transfers can extend without
  reshaping it.
- A copy status model in one place, with transitions guarded by the domain object itself.
- Uniqueness rules enforced by the database, so they hold under concurrent requests.

**Non-Goals:**
- Search performance beyond simple indexed queries (no full-text search engine).
- A generic state machine framework for copy status.

## Decisions

### One `catalog` package for branches, books and copies
Layout: `catalog/branch`, `catalog/book`, `catalog/copy`, each with entity, repository, service,
controller and DTOs. They are one domain area (what the network owns and where), and later areas
(`readers`, `loans`, `holds`) depend on it. Alternative: three top-level packages. Rejected: they
would depend on each other more than on anything else.

### Long identity keys
`bigint generated always as identity` for all tables. Simple, readable in tests and API examples.
Alternative: UUID. Not needed: there is no public, unauthenticated API to protect from enumeration,
and no distributed id generation.

### Entities expose behavior, not setters for status
`Copy` has methods `markLost()` and `markFound()` that check the current status and throw
`CopyStatusConflictException` (`409`, `COPY_STATUS_CONFLICT`). Later changes add methods such as
`lend()` or `startTransfer()` in the same place. `CopyStatus` is a Java enum stored as `varchar`
with a `CHECK` constraint listing the five values.

### Uniqueness through database constraints
Unique constraints on `branch.code`, `book.isbn` and `copy.inventory_number`. Services check for
an existing row first to return a clear error in the common case. For the race where two requests
pass the check at the same time, the service catches `DataIntegrityViolationException` on flush
and maps it to the same `409` code by constraint name. Transactions: one transaction per write
request (`@Transactional` on service methods); reads use `readOnly = true`.

### Optimistic locking on copies
`copy` gets a `version` column (`@Version`). Two concurrent status changes on one copy: the second
commit fails with `ObjectOptimisticLockingFailureException`, which the global handler maps to
`409 CONCURRENT_MODIFICATION`. This change only needs it for lost/found. `add-holds` will decide
whether it needs pessimistic locks for the last copy on top of this.

### ISBN handling
A small `Isbn` value helper normalizes input (drops hyphens and spaces) and validates the ISBN-13
check digit. It backs a custom Bean Validation constraint `@ValidIsbn` on request DTOs, so an
invalid ISBN is reported as a field error inside `VALIDATION_FAILED`. The service normalizes before
saving and before ISBN search.

### Authors as an ordered collection
`book_author(book_id, position, name)` as a JPA `@ElementCollection` with `@OrderColumn`. Author
order matters for display, and authors are not a separate entity in this project. Alternative: an
`author` table with many-to-many. Rejected: no requirement to manage authors on their own.

### Search
`GET /api/books?q=` runs one JPQL query: if `q` normalizes to a valid ISBN, match by ISBN only;
otherwise `lower(title) like %q%` or an author `lower(name) like %q%` (`distinct`), ordered by title.
Good enough for a demo-sized catalog. Alternative: PostgreSQL full-text search. Not needed now.

### Pagination response
A `PageResponse<T>` record in `common` (`items`, `page`, `size`, `totalElements`, `totalPages`),
built from Spring Data `Page`. Controllers take `page` and `size` as `@RequestParam` with `@Min` /
`@Max` and build a `PageRequest` with a fixed sort. Alternative: Spring Data `Pageable` binding and
`PagedModel`. Rejected: the JSON shape would then be dictated by the library, and arbitrary `sort`
parameters would leak into the API.

### Request parameter errors map to VALIDATION_FAILED
Extend `GlobalExceptionHandler` so that `HandlerMethodValidationException` (constraint on
`@RequestParam`) and `MethodArgumentTypeMismatchException` (for example, `status=BROKEN`) return
`400` with `VALIDATION_FAILED` and the parameter in `errors`, the same as body validation.

### Availability query
`GET /api/books/{id}/availability` runs one aggregate query over `copy` joined with `branch`,
grouped by branch: `count(*) filter (status <> 'LOST')` as `total` and
`count(*) filter (status = 'ON_SHELF')` as `onShelf`. Implemented as a JPQL query with
`sum(case ...)` and a DTO projection.

### Database schema: `V2__catalog.sql`
- `branch(id, code unique, name, address)`
- `book(id, title, isbn unique char(13), publication_year null)`
- `book_author(book_id fk, position, name, primary key (book_id, position))`
- `copy(id, book_id fk, branch_id fk, inventory_number unique, status check(...), version)`
- Indexes: `copy(book_id, branch_id)`, `copy(status)`, `book(lower(title))`.

The empty `V1__baseline.sql` stays as is: migrations that are already applied are never edited.

## Risks / Trade-offs

- [`like %q%` search does not use the title index for leading wildcards] → acceptable for a
  demo-sized catalog. If needed later, switch to `pg_trgm` in a separate change.
- [Mapping `DataIntegrityViolationException` by constraint name couples code to migration names] →
  constraint names are set explicitly in `V2__catalog.sql` and covered by integration tests.
- [Optimistic locking returns 409 to one of two concurrent clients, who must retry] → expected for
  a librarian API. The error code says what happened.
