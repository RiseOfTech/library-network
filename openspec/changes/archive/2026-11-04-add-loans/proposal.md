# Proposal

## Why

Catalog and readers exist, but a reader still cannot take a book home. Loans are the core of the
library network and the base that renewals, holds, transfers, fines and the nightly job (roadmap
rows 4-9) build on, so checkout and return come next (roadmap row #3).

## What Changes

- **Checkout** `POST /api/loans` with `readerId`, `copyId` and `branchId` (the branch where the
  checkout happens). Allowed only for a copy with status `ON_SHELF` and only in the copy's own
  branch. The copy becomes `ON_LOAN`, the loan is `OPEN`.
- **Due date** = checkout date (from the injectable `Clock`) + `loanPeriodDays` of the reader's
  current category. The period is stored on the loan at checkout, so later category or
  configuration changes do not move existing due dates.
- **Loan limit**: a reader cannot have more open loans than `loanLimit` of their current category;
  a checkout that would exceed it is rejected with `409 LOAN_LIMIT_EXCEEDED`.
- **Concurrency**: two readers cannot check out the same copy, and parallel checkouts by one reader
  cannot exceed the limit (row locks on reader and copy, plus a database guard of one open loan per
  copy). Covered by a concurrency integration test.
- **Return** `POST /api/loans/{id}/return` with `branchId`. Allowed only at the copy's own branch and
  only for an open loan. The copy goes back to `ON_SHELF`, the loan becomes `RETURNED` with
  `returnedAt` from the `Clock`.
- **Lookup**: `GET /api/loans/{id}` and `GET /api/loans?readerId=&copyId=&status=&page=&size=`
  (paginated like other list endpoints). A loan response has reader, copy and branch summaries,
  `status`, `checkedOutAt`, `dueDate`, `loanPeriodDays`, `returnedAt` and a derived read-only
  `overdue` flag (open and `dueDate` before today by the `Clock`).
- **Copies**: the existing rules stay. An `ON_LOAN` copy still cannot be marked lost
  (`409 COPY_STATUS_CONFLICT`), and availability per branch counts `ON_LOAN` copies in `total` but
  not in `onShelf`. Both requirements get explicit `ON_LOAN` scenarios; the rules themselves do not
  change.
- **Error codes**: new `LOAN_NOT_FOUND` (404), `LOAN_LIMIT_EXCEEDED` (409), `COPY_NOT_AVAILABLE`
  (409, copy not `ON_SHELF`), `LOAN_WRONG_BRANCH` (409, checkout/return outside the copy's branch),
  `LOAN_ALREADY_RETURNED` (409). Reused: `READER_NOT_FOUND`, `COPY_NOT_FOUND`, `BRANCH_NOT_FOUND`,
  `VALIDATION_FAILED`.
- **Schema**: new Flyway migration `V4__loans.sql` with table `loan`. New domain package `loans`.

Decisions taken on details the request left open (recorded so they can be challenged in review):
- A loan is overdue only while it is open; a returned loan always has `overdue` `false`. The due
  date itself is not overdue (`dueDate < today`).
- A reader whose stored category is no longer configured cannot check out
  (`409 READER_CATEGORY_NOT_ALLOWED`, existing code); returns are not affected.
- `LOAN_WRONG_BRANCH` is `409` like other business-rule rejections.

## Configuration

No new or changed properties. Checkout reads the existing
`library.readers.categories.<code>.loan-limit` and `.loan-period-days`
(defaults: adult 5 / 21, student 10 / 30, child 3 / 14).

## Out of scope

- Renewals (`add-renewals`).
- Holds and checkout of `ON_HOLD` copies (`add-holds`); an `ON_HOLD` copy is rejected as
  `COPY_NOT_AVAILABLE` here.
- Return at a foreign branch and transfers (`add-transfers`).
- Fines and blocking checkout by unpaid fines (`add-fines`).
- Marking loans overdue by a scheduled job, stored overdue state (`add-overdue-job`).
- Notifications (`add-notifications`).
- Lost-while-on-loan flow; marking an `ON_LOAN` copy lost stays rejected.
- Deleting or editing loans, back-dated checkouts or returns.

## Capabilities

### New Capabilities
- `loans`: checkout and return of copies, loan limit and due date by reader category, loan lookup
  and listing with the derived overdue flag.

### Modified Capabilities
- `copies`: "Mark a copy as lost" and "Book availability by branch" get explicit scenarios for
  `ON_LOAN` copies (behavior unchanged, now reachable through loans).

## Impact

- New package `io.github.riseoftech.library.loans` (entity, repository, service, controller, DTOs,
  exceptions).
- `catalog/copy/Copy` gets `checkOut()` / `checkIn()` status transitions; `CopyRepository` and
  `ReaderRepository` get a pessimistic-lock lookup.
- New migration `V4__loans.sql`; `V1`-`V3` are not touched.
- New endpoints under `/api/loans` in the OpenAPI document.
- README roadmap row #3 needs no boundary change.
