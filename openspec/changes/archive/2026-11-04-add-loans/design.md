# Design

## Context

`add-catalog` and `add-readers` are archived. Relevant existing code:
- `catalog/copy/Copy` has `@Version` and changes status only through behavior methods
  (`markLost`, `markFound`), which throw `CopyStatusConflictException` (`409 COPY_STATUS_CONFLICT`).
  `CopyService.markLost` uses optimistic locking only; `CopyConcurrencyTest` already accepts
  `COPY_STATUS_CONFLICT` or `CONCURRENT_MODIFICATION` for the loser of a race.
- `CopyRepository.availability` counts `total` as "not `LOST`" and `onShelf` as `ON_SHELF`, so
  `ON_LOAN` copies already show correctly; the copies delta only adds scenarios.
- `readers/reader/Reader` (`@Version`, stored `category` code) and `ReaderCategories.find(code)`
  returning `loanLimit` and `loanPeriodDays`.
- `common`: `ApiException` → problem+json with `code`, `ConflictException`, `NotFoundException`,
  `PageResponse`, `UniqueConstraints.isViolated`, the `Clock` bean (fixed at `2026-11-04T12:00Z` in
  integration tests via `FixedClockConfiguration`), optimistic lock → `409 CONCURRENT_MODIFICATION`.
- Migrations `V1`-`V3`. Database is PostgreSQL, default isolation READ COMMITTED.

See proposal.md for scope and the decisions on open details; specs/loans/spec.md for behavior.

## Goals / Non-Goals

**Goals:**
- Copy and limit rules hold under concurrent requests without retries in the client.
- A loan model that renewals (new due date), holds (copy hand-over on return), transfers (return at a
  foreign branch) and fines (late return) can extend without reshaping the table.

**Non-Goals:**
- Serializable isolation or application-level locks (e.g. advisory locks, in-memory mutexes).
- Lock timeouts tuned for load; this is a librarian API with short transactions.

## Decisions

### Package layout: `loans`
`io.github.riseoftech.library.loans`: `Loan` entity, `LoanStatus` (`OPEN`, `RETURNED`),
`LoanRepository`, `LoanService`, `LoanController`, `LoanDtos`, exceptions
(`LoanNotFoundException` 404, `LoanLimitExceededException`, `CopyNotAvailableException`,
`LoanWrongBranchException`, `LoanAlreadyReturnedException`, all 409). Flat package, since there is
one aggregate. `loans` depends on `catalog` and `readers`; they do not depend on `loans`.

Copy status transitions stay in `Copy`: new `checkOut()` (`ON_SHELF -> ON_LOAN`) and `checkIn()`
(`ON_LOAN -> ON_SHELF`), guarded by the existing `requireStatus`. `LoanService` checks the status
itself first and throws `CopyNotAvailableException` (the loans-facing code); the guard in `Copy` is a
safety net that would surface as `COPY_STATUS_CONFLICT` only on a bug.

### Loan status derived from `returned_at`
`OPEN` ⇔ `returned_at is null`. No `status` column: one source of truth, no check constraint to keep
two columns consistent. The `status` filter maps to `returned_at is null / is not null`.
Alternative: a `status` column. Rejected for now; if `add-overdue-job` needs a stored state it adds a
column in its own migration (the proposal keeps overdue derived here).

### Due date and period snapshot
At checkout: `today = LocalDate.now(clock)`, `checkedOutAt = Instant.now(clock)`,
`loanPeriodDays = category.loanPeriodDays()`, `dueDate = today.plusDays(loanPeriodDays)`. Both
`loan_period_days` and `due_date` are stored. The period is stored even though `dueDate` alone fixes
the date, because `add-renewals` extends "by the category period" and the response shows which
period was applied. The category is resolved from the reader's current stored code; if it is no
longer configured, checkout throws `ReaderCategoryNotAllowedException` (new constructor with a
message for an unconfigured category; same code).

### `overdue` computed at read time
`LoanResponse.from(loan, today)` sets `overdue = returnedAt == null && dueDate.isBefore(today)`,
with `today` from the `Clock` in `LoanService`. Nothing is stored, so no job or migration is needed
to keep it right.

### Response shape
`LoanResponse(id, reader{id, cardNumber}, copy{id, inventoryNumber, bookId}, branch{id, code},
status, checkedOutAt, dueDate, loanPeriodDays, returnedAt, overdue)`. Small nested summaries instead
of bare ids, so a librarian screen does not need three more calls. `checkedOutAt`/`returnedAt` are
`Instant` (ISO-8601 UTC), `dueDate` is a `LocalDate`. `Loan` has lazy `@ManyToOne` to `Reader`,
`Copy`, `Branch`; the list query fetches them with `join fetch` (count query separate) to avoid N+1.
`branch` is the branch where the checkout happened; with checkout and return restricted to the
copy's branch it equals the copy's branch.

### Request shapes and check order
- `POST /api/loans` body `CheckoutRequest(@NotNull Long readerId, @NotNull Long copyId, @NotNull Long branchId)`.
  Order: validation (400) → lock reader (`READER_NOT_FOUND`) → lock copy (`COPY_NOT_FOUND`) →
  load branch (`BRANCH_NOT_FOUND`) → category configured (`READER_CATEGORY_NOT_ALLOWED`) →
  `copy.branch == branchId` (`LOAN_WRONG_BRANCH`) → copy `ON_SHELF` (`COPY_NOT_AVAILABLE`) →
  open loans < limit (`LOAN_LIMIT_EXCEEDED`) → insert loan, `copy.checkOut()`, flush.
  `201 Created` with `Location: /api/loans/{id}`.
- `POST /api/loans/{id}/return` body `ReturnRequest(@NotNull Long branchId)`.
  Order: validation (400) → lock loan (`LOAN_NOT_FOUND`) → load branch (`BRANCH_NOT_FOUND`) →
  open (`LOAN_ALREADY_RETURNED`) → lock copy by `loan.copy` id (before touching any copy field) →
  `copy.branch == branchId` (`LOAN_WRONG_BRANCH`) → `copy.checkIn()`,
  `loan.returnedAt = Instant.now(clock)`, flush. `200 OK`.
- `GET /api/loans?readerId=&copyId=&status=&page=&size=`: all filters optional, `status` bound to
  `LoanStatus` (unknown value → `400 VALIDATION_FAILED` through the existing type-mismatch handler),
  `page`/`size` validated like other lists (size 1-100, default 20). Sort `checkedOutAt desc, id desc`
  (the id tiebreak keeps order stable when the fixed test clock gives equal timestamps). Unknown
  `readerId`/`copyId` simply return an empty page, like `GET /api/copies?bookId=`.

### Transactions and concurrency
One transaction per write request (`@Transactional` on `checkout` and `returnLoan`); reads are
`readOnly = true`. Locks are row locks taken with `select ... for update` (`@Lock(PESSIMISTIC_WRITE)`
repository methods `findByIdForUpdate` on `ReaderRepository`, `CopyRepository`, `LoanRepository`)
and released at commit.

Checkout locks **reader, then copy**, before reading anything else about them in the transaction
(locking an entity already loaded into the persistence context would compare versions instead of
re-reading the row).
- *Two readers, one copy*: both lock their own reader, then queue on the copy row. The second gets
  the row after the first commits; PostgreSQL re-reads the committed row for `for update`, so it
  sees `ON_LOAN` → `409 COPY_NOT_AVAILABLE`. If the first rolled back, the second proceeds.
- *One reader, several copies*: both queue on the reader row. The open-loan count runs after the
  lock; under READ COMMITTED each statement sees rows committed before it starts, so the second
  counts the first's loan → `409 LOAN_LIMIT_EXCEEDED`. The reader lock is what makes count-then-insert
  safe; locking only the copy would not.
- *Safety net*: partial unique index `uk_loan_open_copy on loan (copy_id) where returned_at is null`.
  A violation (only possible if a code path skips the lock) is mapped with `UniqueConstraints` to
  `409 COPY_NOT_AVAILABLE`. There is no database-level guard for the limit (it depends on
  configuration); the reader lock is the only guard, and the concurrency test checks it.

Return locks **loan, then copy**.
- *Two returns of one loan*: the second waits on the loan row, re-reads it with `returned_at` set →
  `409 LOAN_ALREADY_RETURNED`.
- *Return vs. checkout of the same copy*: a checkout of an `ON_LOAN` copy fails on status anyway;
  if it waits on the copy row while the return commits, it then sees `ON_SHELF` and succeeds,
  which is correct.

Interaction with existing writers:
- *Mark lost vs. checkout*: `markLost` uses optimistic locking. If checkout commits first, `markLost`
  either sees `ON_LOAN` (`COPY_STATUS_CONFLICT`) or fails its versioned update
  (`CONCURRENT_MODIFICATION`), because `checkOut()` bumps `copy.version`. If `markLost` commits
  first, checkout's lock returns `LOST` → `COPY_NOT_AVAILABLE`.
- *Reader update vs. checkout*: `PUT /api/readers/{id}` updates the row (row lock until commit), so
  checkout's `for update` waits and then uses the new category, or the update waits for the checkout.
  `select for update` does not change `reader.version`, so a reader update after a checkout does not
  get a spurious `CONCURRENT_MODIFICATION`.

Deadlocks: lock orders are reader → copy (checkout) and loan → copy (return); no path locks copy
before reader or loan, and `markLost`/`markFound`/reader update take a single row. No cycle is
possible.

Copies are also guarded by `@Version`; the pessimistic lock makes version conflicts on the loan
paths impossible, but a stray conflict would still map to `409 CONCURRENT_MODIFICATION`.

### Database schema: `V4__loans.sql`
```
create table loan (
    id               bigint generated always as identity primary key,
    reader_id        bigint      not null,
    copy_id          bigint      not null,
    branch_id        bigint      not null,
    checked_out_at   timestamptz not null,
    loan_period_days integer     not null,
    due_date         date        not null,
    returned_at      timestamptz,
    version          bigint      not null default 0,
    constraint fk_loan_reader foreign key (reader_id) references reader (id),
    constraint fk_loan_copy   foreign key (copy_id)   references copy (id),
    constraint fk_loan_branch foreign key (branch_id) references branch (id),
    constraint ck_loan_period_positive check (loan_period_days > 0),
    constraint ck_loan_returned_after_checkout check (returned_at is null or returned_at >= checked_out_at)
);
create unique index uk_loan_open_copy on loan (copy_id) where returned_at is null;
create index ix_loan_reader_open on loan (reader_id) where returned_at is null;
create index ix_loan_reader on loan (reader_id, checked_out_at desc);
create index ix_loan_copy on loan (copy_id, checked_out_at desc);
```
`ix_loan_reader_open` serves the limit count; the other two serve the list filters. `version` is
there for `add-renewals` (a renewal racing a return). `V1`-`V3` are not touched. Test cleanup must add
`loan` to the `truncate` lists (or rely on `cascade`, which `truncate(...)` already uses).

### Decisions taken during implementation
- `Loan.open(reader, copy, branch, checkedOutAt, today, periodDays)` is the only way to create a loan;
  it rejects a non-positive period (mirrors `ck_loan_period_positive`).
- The overdue rule lives on the entity (`Loan.isOverdue(today)`) so it is unit-tested once and
  `LoanResponse.from` only passes `today` from the `Clock`.
- The `status` filter is passed to the repository as a nullable `Boolean open`, so one JPQL query with
  a separate count query serves all filter combinations.
- `get` uses plain `findById` inside the read-only transaction; lazy summaries load there, only the
  list query needs `join fetch`.

## Risks / Trade-offs

- [Pessimistic locks block instead of failing fast] → transactions are a few short statements; no
  lock timeout is set. If a lock wait ever hangs, a `jakarta.persistence.lock.timeout` hint can be
  added without changing the API.
- [The limit has no database constraint] → guarded only by the reader lock; covered by the
  one-reader concurrency test with more parallel requests than free slots.
- [`overdue` depends on the server's `Clock` zone] → same single `Clock` as the rest of the service;
  near midnight the flag can flip a few hours early or late, acceptable for a read-only flag.
- [Category removed from configuration blocks checkout for its readers] → explicit
  `READER_CATEGORY_NOT_ALLOWED`; the librarian changes the reader's category. Existing loans and
  returns are unaffected because the period is stored on the loan.
- [Existing tests truncate `copy`/`reader` without `loan`] → `truncate ... cascade` also empties
  `loan`; new loan tests truncate `loan` explicitly.

## Migration Plan

Additive: Flyway applies `V4__loans.sql` on startup; existing data has no loans and every copy keeps
its status. Rollback is deploying the previous version; the empty `loan` table can stay.
