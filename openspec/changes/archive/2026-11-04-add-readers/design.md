# Design

## Context

`add-catalog` is archived. The code has a `catalog` package and shared infrastructure in `common`:
`ApiException` subclasses rendered as problem+json with `code`, `PageResponse<T>`,
`UniqueConstraints.isViolated` for mapping a lost uniqueness race by constraint name, optimistic
locking mapped to `409 CONCURRENT_MODIFICATION`, and `@RequestParam` validation mapped to
`VALIDATION_FAILED`. The global handler renders only **field** errors into `errors`; class-level
(global) errors are not shown. There is no `Clock` bean and no `@ConfigurationProperties` class yet.
Migrations: `V1__baseline.sql` (empty), `V2__catalog.sql`. See proposal.md for scope and the fixed
decisions (card number, required fields, search, category change).

## Goals / Non-Goals

**Goals:**
- A category lookup that `add-loans` can use as is to get the loan limit and loan period of a reader.
- A reader model that loans, holds and fines can reference by id without reshaping it.
- Rules that depend on "today" are testable with a fixed clock.

**Non-Goals:**
- Storing categories in the database or keeping history of a reader's category changes.
- Duplicate-person detection (same name and date of birth).

## Decisions

### Package layout: `readers/category` and `readers/reader`
`readers/category`: `ReaderCategoriesProperties`, `ReaderCategory` (record: `code`, `loanLimit`,
`loanPeriodDays`, `maxAgeExclusive`), `ReaderCategories` (lookup bean), `ReaderCategoryController`,
`@KnownCategory` constraint. `readers/reader`: entity, repository, service, controller, DTOs,
exceptions. Same per-feature layout as `catalog/*`. Later changes depend on `ReaderCategories` and
`Reader`, never on the properties class.

### Categories: `@ConfigurationProperties("library.readers")` with a map, not an enum
`record ReaderCategoriesProperties(@NotEmpty Map<String, @Valid Category> categories)` with
`record Category(@Positive int loanLimit, @Positive int loanPeriodDays, @Positive Integer maxAgeExclusive)`,
annotated `@Validated`, so a broken configuration fails at startup. Defaults are in `application.yml`
under lower-case keys (`adult`, `student`, `child`); `ReaderCategories` converts keys to upper case
once and keeps an immutable map sorted by code. Lower-case keys avoid surprises with relaxed binding
of map keys from environment variables.
Alternative: a Java enum `ReaderCategory` with numbers from configuration. Rejected: the request is
that the category set itself is not in code, and a map lets a deployment add a category without a
release. Consequence: no rule in code mentions `CHILD`; the age rule is generic (`maxAgeExclusive`).

### Category validation as a field error
`@KnownCategory` is a Bean Validation constraint whose validator is a Spring-managed bean that asks
`ReaderCategories`. It is used on the `category` field of request bodies and on the `category`
`@RequestParam` of search, so unknown values become `400 VALIDATION_FAILED` with `category` in
`errors` through the existing handler. `null` is left to `@NotNull` on the body field and means "no
filter" on the query parameter.

### Age rule in the service, with the injected `Clock`
`ReaderService` checks `maxAgeExclusive` on create, and on update only if `category` or
`dateOfBirth` differs from the stored value. Age = `Period.between(dateOfBirth, LocalDate.now(clock)).getYears()`;
the reader is allowed when `age < maxAgeExclusive`. A violation throws
`ReaderCategoryNotAllowedException` (`409`, `READER_CATEGORY_NOT_ALLOWED`) before anything is
written. `409` follows the project convention for business-rule rejections (e.g. the README example
`LOAN_LIMIT_EXCEEDED`).
Alternative: a cross-field Bean Validation constraint. Rejected: on update it needs the stored reader
to know whether the category changed, and a business rule should not look like a malformed request.

### `Clock` bean and Bean Validation clock
New `common/ClockConfiguration` with `@Bean Clock clock()` returning `Clock.systemDefaultZone()`.
A `ValidationConfigurationCustomizer` sets a `ClockProvider` backed by the same bean, so
`@PastOrPresent` on `dateOfBirth` uses it too. Integration tests replace the bean with a fixed clock
(`2026-11-04`) through a `@TestConfiguration` with `@Primary`, imported by `IntegrationTest`.

### Contact rule: class-level constraint reported on `email`
`@ContactRequired` on the create/update request record checks that `email` or `phone` is non-blank.
Its violation is built with `addPropertyNode("email")`, so it becomes a field error and appears in
`errors` (the handler drops global errors). Formats: `@Email @Size(max = 254)` for email; phone
`^\+?[0-9][0-9 ()-]{4,19}$`. Blank strings are normalized to `null` before saving, so the database
check below holds. The database also has `ck_reader_contact` as a safety net.

### Card number from a database sequence
`ReaderRepository.nextCardNumber()` runs native `select nextval('reader_card_number_seq')`, and the
service formats it as `"LN-%06d"` (more digits once past 999999). The number is taken in the same
transaction as the insert; a rolled-back registration leaves a gap, which is acceptable. Sequences
are not transactional, so concurrent registrations never get the same value and need no lock.
Deviation from the add-catalog uniqueness pattern: there is no client-provided unique value, so there
is no pre-check and no `*_TAKEN` code. `uk_reader_card_number` stays as a guard; if it were ever
violated it is a bug and surfaces as `500 INTERNAL_ERROR`, deliberately not mapped to a 409.
`card_number` is `updatable = false` and request DTOs have no `cardNumber` field, so a value sent by
the client is ignored by JSON binding.
Alternative: `LN-` + id from the identity column. Rejected: the card number would be known only after
insert, needing a second update, and it would couple the public number to an internal key.

### Search
`GET /api/readers?q=&category=&page=&size=`. If `q` matches `(?i)^LN-\d+$`, the service upper-cases
it and queries `card_number = :q` (plus the optional category). Otherwise one JPQL query:
`lower(firstName) like :p or lower(lastName) like :p or lower(concat(firstName, ' ', lastName)) like :p`,
with `%`, `_` and `\` escaped as in book search, and `(:category is null or category = :category)`.
The escaping helper moves from the book search into `common` so both use it. Blank `q` means no name
filter. Fixed sort `lastName, firstName, id`. Response is `PageResponse`, `page`/`size` validated like
books (size 1-100, default 20).

### Transactions and concurrency
One transaction per write request (`@Transactional` on service methods), reads `readOnly = true`;
create and update use `saveAndFlush` like the catalog services.
- Two registrations at once: different sequence values, no conflict (spec scenario).
- Two updates of one reader at once: `reader.version` (`@Version`). Each request loads, modifies and
  flushes in its own transaction; if they overlap, the second flush fails with an optimistic lock
  error → `409 CONCURRENT_MODIFICATION`. If they do not overlap, both succeed in order (last write
  wins). The request body carries no version: this is a librarian form, not a long-lived edit.
- The age check reads the stored reader and writes in the same transaction, so a concurrent change of
  `dateOfBirth` is caught by the same version check.

### Database schema: `V3__readers.sql`
- `create sequence reader_card_number_seq start with 1`
- `reader(id bigint generated always as identity primary key, card_number varchar(20) not null,
  first_name varchar(100) not null, last_name varchar(100) not null, date_of_birth date not null,
  category varchar(20) not null, email varchar(254), phone varchar(20), version bigint not null default 0)`
- Constraints: `uk_reader_card_number unique (card_number)`,
  `ck_reader_contact check (email is not null or phone is not null)`. No check on `category`: the
  set of values is in configuration.
- Indexes: `ix_reader_last_name (lower(last_name))`, `ix_reader_category (category)`.

`V1` and `V2` are not touched.

## Risks / Trade-offs

- [A category removed from configuration while readers still have it] → readers keep the stored
  code and are returned as is; updating them requires choosing a configured category. `add-loans`
  must decide what checkout does for such a reader. Not checked at startup, to keep the database out
  of configuration validation.
- [`like %q%` name search does not use the `lower(last_name)` index for a leading wildcard] →
  acceptable for a demo-sized dataset; `pg_trgm` later if needed. Card lookup uses the unique index.
- [Sequence gaps after rolled-back registrations] → card numbers are identifiers, not a count; gaps
  are harmless.
- [Age computed with the server's time zone] → one `Clock` for the whole service; birthdays shift by
  at most a day near midnight, acceptable for a librarian API.
- [`CHILD` readers who turn 14 keep the category] → explicit in proposal (out of scope); the librarian
  updates the category.
