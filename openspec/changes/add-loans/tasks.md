# Tasks

## 1. Database schema

- [ ] 1.1 Add `V4__loans.sql` with table `loan`, foreign keys, `ck_loan_period_positive`, `ck_loan_returned_after_checkout`, partial unique index `uk_loan_open_copy`, and indexes `ix_loan_reader_open`, `ix_loan_reader`, `ix_loan_copy` exactly as in design.md; `V1`-`V3` untouched
- [ ] 1.2 Verify `ApplicationSmokeTest` passes (Flyway applies V4; Hibernate `validate` succeeds once the `Loan` entity exists in 2.2)

## 2. Domain

- [ ] 2.1 Add `Copy.checkOut()` (`ON_SHELF -> ON_LOAN`) and `Copy.checkIn()` (`ON_LOAN -> ON_SHELF`) using `requireStatus`; extend `CopyTest` with success and wrong-status cases for both; verify it passes
- [ ] 2.2 Add `loans` package: `Loan` entity (lazy `@ManyToOne` reader/copy/branch, `checkedOutAt` `Instant`, `loanPeriodDays`, `dueDate`, `returnedAt`, `@Version`; factory computing `dueDate = today + period`; `returnAt(Instant)` failing with `LoanAlreadyReturnedException` when already returned; `status()` derived from `returnedAt`) and `LoanStatus` (`OPEN`, `RETURNED`)
- [ ] 2.3 Add exceptions `LoanNotFoundException` (404 `LOAN_NOT_FOUND`), `LoanLimitExceededException` (409 `LOAN_LIMIT_EXCEEDED`), `CopyNotAvailableException` (409 `COPY_NOT_AVAILABLE`), `LoanWrongBranchException` (409 `LOAN_WRONG_BRANCH`), `LoanAlreadyReturnedException` (409 `LOAN_ALREADY_RETURNED`); add a `ReaderCategoryNotAllowedException` constructor for a category that is not configured (same code)
- [ ] 2.4 Add `@Lock(PESSIMISTIC_WRITE)` `findByIdForUpdate` to `ReaderRepository`, `CopyRepository` and `LoanRepository`; add `LoanRepository.countOpenByReader(readerId)` and the filtered paged query (`readerId`, `copyId`, open/returned, `join fetch` reader/copy/branch with a separate count query)
- [ ] 2.5 Add unit tests for `Loan`: due date for 21 and 14 days across a month boundary, status derivation, second `returnAt` rejected, overdue rule (due yesterday → true, due today → false, returned → false); verify they pass

## 3. Service and API

- [ ] 3.1 Add `LoanService.checkout` with the lock order and check order from design.md (reader lock → copy lock → branch → category → branch match → `ON_SHELF` → limit → insert + `copy.checkOut()` + `saveAndFlush`); map a `uk_loan_open_copy` violation to `CopyNotAvailableException` via `UniqueConstraints`
- [ ] 3.2 Add `LoanService.returnLoan` (loan lock → branch → open → copy lock → branch match → `copy.checkIn()` + `returnedAt` from `Clock`), `get` and `list` (sort `checkedOutAt desc, id desc`); `LoanResponse.from(loan, today)` with nested reader/copy/branch summaries and the derived `overdue`
- [ ] 3.3 Add `LoanDtos` (`CheckoutRequest` with `@NotNull` `readerId`, `copyId`, `branchId`; `ReturnRequest` with `@NotNull branchId`; response records) and `LoanController` for `POST /api/loans` (201 + `Location`), `POST /api/loans/{id}/return`, `GET /api/loans/{id}`, `GET /api/loans?readerId=&copyId=&status=&page=&size=` (size 1-100, default 20); verify the endpoints appear in `/v3/api-docs` via a test or the smoke test
- [ ] 3.4 Add `LoanApiTest` (Testcontainers, fixed clock 2026-11-04) covering every non-concurrent scenario of "Check out a copy", "Only a copy on the shelf can be checked out", "Checkout only in the copy's branch", "Due date from the reader category" (including category change after checkout and an unconfigured category set directly in the database), "Loan limit by reader category", "Return a loan", "Return only at the copy's branch", "Overdue flag" (past due dates set through the database), "Get a loan" and "List loans" in `specs/loans/spec.md`; verify it passes
- [ ] 3.5 Extend `CopyApiTest` with the two new `copies` scenarios: `POST /api/copies/{id}/lost` on an `ON_LOAN` copy → `409 COPY_STATUS_CONFLICT` with the loan still `OPEN`, and availability `total`/`onShelf` before checkout, after checkout and after return; make existing catalog/reader tests truncate `loan` where they truncate `copy` or `reader`; verify `CopyApiTest` and the rest of the suite pass

## 4. Concurrency

- [ ] 4.1 Add `LoanConcurrencyTest` (Testcontainers, latch-started threads, several rounds like `CopyConcurrencyTest`): two readers check out one copy → exactly one `201`, one `409 COPY_NOT_AVAILABLE`, exactly one open loan for the copy; verify it passes
- [ ] 4.2 In `LoanConcurrencyTest`: a `CHILD` reader with 2 open loans checks out 3 different copies in parallel → exactly one `201`, two `409 LOAN_LIMIT_EXCEEDED`, 3 open loans, the two losers' copies still `ON_SHELF`; verify it passes
- [ ] 4.3 In `LoanConcurrencyTest`: two parallel returns of one loan → one `200`, one `409 LOAN_ALREADY_RETURNED`, copy `ON_SHELF`; and checkout vs. `POST /api/copies/{id}/lost` on one copy → exactly one succeeds, the loser gets `COPY_NOT_AVAILABLE`, `COPY_STATUS_CONFLICT` or `CONCURRENT_MODIFICATION`, and the final copy status matches the winner; verify it passes

## 5. Integration check

- [ ] 5.1 Run the app against `docker compose` and walk through register branch/book/copy/reader → checkout → availability → list loans of the reader → return with curl; check `/api/loans` endpoints in `/v3/api-docs`
- [ ] 5.2 Run `./gradlew build -q` and verify it is green

## Workflow follow-up

- Run `task-review` after each task group and before archive.
- Show the result for review, propose a commit message.
- Archive the change with `/opsx:archive` after approval.
