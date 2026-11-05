# Spec Delta

## Purpose

Lets librarians check out a copy to a reader in the copy's branch and take it back on return,
enforcing the reader category's loan limit and loan period, and look up current and past loans.

## ADDED Requirements

### Requirement: Check out a copy
The system SHALL allow checking out a copy to a reader with `readerId`, `copyId` and `branchId` (the
branch where the checkout happens). The response is an `OPEN` loan with `checkedOutAt` from the
current time; the copy becomes `ON_LOAN`.

#### Scenario: Copy is checked out
- **WHEN** an `ADULT` reader has no loans, a copy in branch `CENTRAL` is `ON_SHELF`, and a client sends `POST /api/loans` with `{"readerId": 1, "copyId": 10, "branchId": <CENTRAL>}`
- **THEN** the system responds `201 Created` with a `Location` header pointing to `/api/loans/{id}` and a body containing `id`, `reader`, `copy`, `branch`, `status` `OPEN`, `checkedOutAt`, `dueDate`, `loanPeriodDays`, `returnedAt` `null` and `overdue` `false`
- **AND** `GET /api/copies/10` returns `status` `ON_LOAN`

#### Scenario: Unknown reader
- **WHEN** a client sends `POST /api/loans` with a `readerId` that does not exist
- **THEN** the system responds `404 Not Found` with code `READER_NOT_FOUND` and no loan is created

#### Scenario: Unknown copy
- **WHEN** a client sends `POST /api/loans` with a `copyId` that does not exist
- **THEN** the system responds `404 Not Found` with code `COPY_NOT_FOUND`

#### Scenario: Unknown branch
- **WHEN** a client sends `POST /api/loans` with a `branchId` that does not exist
- **THEN** the system responds `404 Not Found` with code `BRANCH_NOT_FOUND`

#### Scenario: Missing fields
- **WHEN** a client sends `POST /api/loans` without `readerId`, `copyId` or `branchId`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and each missing field listed in `errors`

### Requirement: Only a copy on the shelf can be checked out
The system SHALL check out only a copy with status `ON_SHELF`. A copy in any other status is
rejected and stays unchanged.

#### Scenario: Copy already on loan
- **WHEN** a copy is `ON_LOAN` and a client sends `POST /api/loans` for it with another reader
- **THEN** the system responds `409 Conflict` with code `COPY_NOT_AVAILABLE`, no loan is created and the existing loan is unchanged

#### Scenario: Lost copy
- **WHEN** a copy is `LOST` and a client sends `POST /api/loans` for it
- **THEN** the system responds `409 Conflict` with code `COPY_NOT_AVAILABLE` and the copy stays `LOST`

### Requirement: Checkout only in the copy's branch
The system SHALL check out a copy only when the request's `branchId` is the branch the copy belongs
to.

#### Scenario: Checkout in the copy's branch
- **WHEN** a copy belongs to branch `NORTH` and a client sends `POST /api/loans` with `branchId` of `NORTH`
- **THEN** the system responds `201 Created` and the loan's `branch` is `NORTH`

#### Scenario: Checkout in another branch
- **WHEN** a copy belongs to branch `NORTH` and a client sends `POST /api/loans` with `branchId` of `CENTRAL`
- **THEN** the system responds `409 Conflict` with code `LOAN_WRONG_BRANCH` and the copy stays `ON_SHELF`

### Requirement: Due date from the reader category
The system SHALL set `dueDate` to the checkout date plus `loanPeriodDays` of the reader's category at
checkout and return that period as the loan's `loanPeriodDays`. Later changes of the reader's
category or of the configured period do not change the due date of an existing loan.

#### Scenario: Due date for an adult
- **WHEN** today is 2026-11-04, the `ADULT` loan period is 21 days and a client checks out a copy for an `ADULT` reader with `POST /api/loans`
- **THEN** the system responds `201 Created` with `dueDate` `2026-11-25` and `loanPeriodDays` 21

#### Scenario: Due date for a child
- **WHEN** today is 2026-11-04, the `CHILD` loan period is 14 days and a client checks out a copy for a `CHILD` reader with `POST /api/loans`
- **THEN** the system responds `201 Created` with `dueDate` `2026-11-18` and `loanPeriodDays` 14

#### Scenario: Category change keeps the due date
- **WHEN** an `ADULT` reader has an open loan with `dueDate` `2026-11-25` and a client changes the reader's category to `STUDENT` with `PUT /api/readers/{id}`
- **THEN** `GET /api/loans/{loanId}` responds `200 OK` with `dueDate` `2026-11-25` and `loanPeriodDays` 21

#### Scenario: Reader category no longer configured
- **WHEN** a reader's stored category is not among the configured categories and a client sends `POST /api/loans` for that reader
- **THEN** the system responds `409 Conflict` with code `READER_CATEGORY_NOT_ALLOWED` and no loan is created

### Requirement: Loan limit by reader category
The system SHALL reject a checkout when the reader already has as many open loans as `loanLimit` of
their current category. Returned loans do not count.

#### Scenario: Last loan within the limit
- **WHEN** the `CHILD` loan limit is 3, a `CHILD` reader has 2 open loans and a client sends `POST /api/loans` for that reader
- **THEN** the system responds `201 Created`

#### Scenario: Limit reached
- **WHEN** the `CHILD` loan limit is 3, a `CHILD` reader has 3 open loans and a client sends `POST /api/loans` for that reader
- **THEN** the system responds `409 Conflict` with code `LOAN_LIMIT_EXCEEDED` and the copy stays `ON_SHELF`

#### Scenario: Returned loans free the limit
- **WHEN** a `CHILD` reader had 3 loans, one of them is returned, and a client sends `POST /api/loans` for that reader
- **THEN** the system responds `201 Created`

#### Scenario: Limit of the current category
- **WHEN** a reader has 4 open loans as `STUDENT` (limit 10), the category is changed to `CHILD` (limit 3), and a client sends `POST /api/loans` for that reader
- **THEN** the system responds `409 Conflict` with code `LOAN_LIMIT_EXCEEDED`; the existing 4 loans stay open

### Requirement: Concurrent checkouts
The system SHALL keep the copy and limit rules under concurrent requests: one copy has at most one
open loan, and parallel checkouts by one reader never leave more open loans than the limit.

#### Scenario: Two readers check out the same copy at once
- **WHEN** a copy is `ON_SHELF` and two clients send `POST /api/loans` for it with different readers at the same time
- **THEN** exactly one request responds `201 Created`, the other responds `409 Conflict` with code `COPY_NOT_AVAILABLE`, and the copy has exactly one open loan

#### Scenario: One reader checks out several copies at once
- **WHEN** a `CHILD` reader (limit 3) has 2 open loans and clients send `POST /api/loans` for that reader with 3 different `ON_SHELF` copies at the same time
- **THEN** exactly one request responds `201 Created`, the others respond `409 Conflict` with code `LOAN_LIMIT_EXCEEDED`, and the reader has 3 open loans

### Requirement: Return a loan
The system SHALL allow returning an open loan with the `branchId` where the return happens. The loan
becomes `RETURNED` with `returnedAt` from the current time and the copy goes back to `ON_SHELF`.

#### Scenario: Loan is returned
- **WHEN** a loan is `OPEN` for a copy of branch `CENTRAL` and a client sends `POST /api/loans/{id}/return` with `{"branchId": <CENTRAL>}`
- **THEN** the system responds `200 OK` with the loan, `status` `RETURNED`, `returnedAt` set and `overdue` `false`
- **AND** `GET /api/copies/{copyId}` returns `status` `ON_SHELF`

#### Scenario: Late return is accepted
- **WHEN** an open loan has `dueDate` before today and a client sends `POST /api/loans/{id}/return` at the copy's branch
- **THEN** the system responds `200 OK` with `status` `RETURNED`; no fine is charged

#### Scenario: Loan already returned
- **WHEN** a loan is `RETURNED` and a client sends `POST /api/loans/{id}/return`
- **THEN** the system responds `409 Conflict` with code `LOAN_ALREADY_RETURNED` and `returnedAt` is unchanged

#### Scenario: Two returns at once
- **WHEN** a loan is `OPEN` and two clients send `POST /api/loans/{id}/return` at the same time
- **THEN** exactly one request responds `200 OK` and the other responds `409 Conflict` with code `LOAN_ALREADY_RETURNED`

#### Scenario: Unknown loan
- **WHEN** a client sends `POST /api/loans/{id}/return` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `LOAN_NOT_FOUND`

#### Scenario: Unknown return branch
- **WHEN** a client sends `POST /api/loans/{id}/return` with a `branchId` that does not exist
- **THEN** the system responds `404 Not Found` with code `BRANCH_NOT_FOUND`

#### Scenario: Missing return branch
- **WHEN** a client sends `POST /api/loans/{id}/return` without `branchId`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and `branchId` listed in `errors`

### Requirement: Return only at the copy's branch
The system SHALL accept a return only when the request's `branchId` is the branch the copy belongs
to.

#### Scenario: Return at another branch
- **WHEN** a loan is `OPEN` for a copy of branch `NORTH` and a client sends `POST /api/loans/{id}/return` with `branchId` of `CENTRAL`
- **THEN** the system responds `409 Conflict` with code `LOAN_WRONG_BRANCH`, the loan stays `OPEN` and the copy stays `ON_LOAN`

### Requirement: Overdue flag
The system SHALL return with every loan a read-only `overdue` flag that is `true` only when the loan
is `OPEN` and its `dueDate` is before today. It is computed on every read and never stored.

#### Scenario: Open loan past its due date
- **WHEN** today is 2026-11-04 and an open loan has `dueDate` `2026-11-03`, and a client sends `GET /api/loans/{id}`
- **THEN** the system responds `200 OK` with `overdue` `true`

#### Scenario: Open loan due today
- **WHEN** today is 2026-11-04 and an open loan has `dueDate` `2026-11-04`, and a client sends `GET /api/loans/{id}`
- **THEN** the system responds `200 OK` with `overdue` `false`

#### Scenario: Returned loan is never overdue
- **WHEN** today is 2026-11-04 and a loan with `dueDate` `2026-11-01` was returned, and a client sends `GET /api/loans/{id}`
- **THEN** the system responds `200 OK` with `status` `RETURNED` and `overdue` `false`

### Requirement: Get a loan
The system SHALL return a single loan by id with `reader` (`id`, `cardNumber`), `copy` (`id`,
`inventoryNumber`, `bookId`), `branch` (`id`, `code`), `status`, `checkedOutAt`, `dueDate`,
`loanPeriodDays`, `returnedAt` and `overdue`.

#### Scenario: Get a loan by id
- **WHEN** a loan exists and a client sends `GET /api/loans/{id}`
- **THEN** the system responds `200 OK` with the loan

#### Scenario: Get an unknown loan
- **WHEN** a client sends `GET /api/loans/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `LOAN_NOT_FOUND`

### Requirement: List loans
The system SHALL list loans filtered by any combination of optional `readerId`, `copyId` and
`status` (`OPEN` or `RETURNED`), newest checkout first, paginated like other list endpoints.

#### Scenario: Open loans of a reader
- **WHEN** a reader has 2 open and 1 returned loan and a client sends `GET /api/loans?readerId={id}&status=OPEN`
- **THEN** the system responds `200 OK` with the 2 open loans in `items` and `totalElements` 2

#### Scenario: Loan history of a copy
- **WHEN** a copy was checked out and returned twice and is checked out again, and a client sends `GET /api/loans?copyId={id}`
- **THEN** the system responds `200 OK` with 3 loans in `items`, the open one first

#### Scenario: Paginated loan list
- **WHEN** 25 loans exist and a client sends `GET /api/loans?page=2&size=10`
- **THEN** the system responds `200 OK` with 5 `items`, `page` 2, `size` 10, `totalElements` 25 and `totalPages` 3

#### Scenario: Unknown status value
- **WHEN** a client sends `GET /api/loans?status=OVERDUE`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED`

#### Scenario: Page size out of range
- **WHEN** a client sends `GET /api/loans?size=0`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED`
