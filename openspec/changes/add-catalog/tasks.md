# Tasks

## 1. Shared API infrastructure

- [ ] 1.1 Add `PageResponse<T>` record in `common` with a factory from Spring Data `Page`; verify with a unit test for `items`, `page`, `size`, `totalElements`, `totalPages`
- [ ] 1.2 Map `HandlerMethodValidationException` and `MethodArgumentTypeMismatchException` to `400 VALIDATION_FAILED` with the parameter in `errors`; verify with new cases in `ErrorFormatTest`
- [ ] 1.3 Map `ObjectOptimisticLockingFailureException` to `409 CONCURRENT_MODIFICATION`; verify with a case in `ErrorFormatTest`

## 2. Database schema

- [ ] 2.1 Add `V2__catalog.sql` with tables `branch`, `book`, `book_author`, `copy`, named unique and check constraints, and indexes from design.md; verify `ApplicationSmokeTest` passes (Flyway applies V2, Hibernate `validate` succeeds once entities exist)

## 3. Branches

- [ ] 3.1 Add `Branch` entity and repository in `catalog/branch`; verify Hibernate schema validation passes in the smoke test
- [ ] 3.2 Add `BranchService` (create, update, get, list) with `BRANCH_NOT_FOUND` and `BRANCH_CODE_TAKEN`, including the unique-constraint race mapping
- [ ] 3.3 Add `BranchController` for `POST/PUT/GET /api/branches` with request validation (code format, non-blank name and address)
- [ ] 3.4 Add `BranchApiTest` (Testcontainers) covering every scenario in `specs/branches/spec.md`; verify it passes

## 4. Books

- [ ] 4.1 Add `Isbn` helper (normalize, validate ISBN-13 check digit) and `@ValidIsbn` constraint; verify with a unit test for valid, wrong check digit, wrong length, hyphens and spaces
- [ ] 4.2 Add `Book` entity with ordered authors (`@ElementCollection` + `@OrderColumn`) and repository with the search query from design.md; verify Hibernate schema validation passes
- [ ] 4.3 Add `BookService` (create, update, get, search) with ISBN normalization, `BOOK_NOT_FOUND` and `BOOK_ISBN_TAKEN` on create and update
- [ ] 4.4 Add `BookController` for `POST/PUT/GET /api/books` and `GET /api/books?q=&page=&size=` with page size limits
- [ ] 4.5 Add `BookApiTest` (Testcontainers) covering every scenario in `specs/catalog/spec.md`, including pagination; verify it passes

## 5. Copies

- [ ] 5.1 Add `CopyStatus` enum and `Copy` entity with `@Version`, `markLost()` and `markFound()` guarded by `COPY_STATUS_CONFLICT`; verify with a unit test of allowed and rejected transitions from every status
- [ ] 5.2 Add `CopyRepository` with filtered paging (`bookId`, `branchId`, `status`) and the availability aggregate query
- [ ] 5.3 Add `CopyService` (register, get, list, mark lost, mark found, availability) with `COPY_NOT_FOUND`, `COPY_INVENTORY_NUMBER_TAKEN`, `BOOK_NOT_FOUND`, `BRANCH_NOT_FOUND`
- [ ] 5.4 Add `CopyController` for `POST/GET /api/copies`, `POST /api/copies/{id}/lost`, `POST /api/copies/{id}/found` and `GET /api/books/{id}/availability`
- [ ] 5.5 Add `CopyApiTest` (Testcontainers) covering every scenario in `specs/copies/spec.md`; verify it passes
- [ ] 5.6 Add a concurrency test: two parallel `POST /api/copies/{id}/lost` on one copy result in exactly one `200` and one `409`; verify it passes

## 6. Integration check

- [ ] 6.1 Run `./gradlew build` and verify it is green
- [ ] 6.2 Run the app against `docker compose`, check that the new endpoints appear in `/v3/api-docs`, and walk through create branch → create book → register copy → availability with curl

## Workflow follow-up

- Show the result for review, propose a commit message.
- Archive the change with `/opsx:archive` after approval.
