# Spec Delta

## MODIFIED Requirements

### Requirement: Mark a copy as lost
The system SHALL allow marking a copy that is `ON_SHELF` as `LOST`. A copy in any other status
cannot be marked lost through this operation.

#### Scenario: Copy on the shelf is marked lost
- **WHEN** a client sends `POST /api/copies/{id}/lost` for a copy with status `ON_SHELF`
- **THEN** the system responds `200 OK` with the copy and `status` `LOST`

#### Scenario: Copy is not on the shelf
- **WHEN** a client sends `POST /api/copies/{id}/lost` for a copy with status `LOST`
- **THEN** the system responds `409 Conflict` with code `COPY_STATUS_CONFLICT` and the copy status is unchanged

#### Scenario: Copy on loan cannot be marked lost
- **WHEN** a copy is checked out (`ON_LOAN`) and a client sends `POST /api/copies/{id}/lost`
- **THEN** the system responds `409 Conflict` with code `COPY_STATUS_CONFLICT`, the copy stays `ON_LOAN` and its loan stays `OPEN`

### Requirement: Book availability by branch
The system SHALL return, for a book, one entry per branch that holds at least one copy of it, with
`branchId`, `branchCode`, `total` (copies in any status except `LOST`) and `onShelf` (copies with
status `ON_SHELF`), ordered by `branchCode`.

#### Scenario: Availability across branches
- **WHEN** a book has in branch `CENTRAL` 2 copies `ON_SHELF` and 1 `LOST`, and in branch `NORTH` 1 copy `ON_SHELF`, and a client sends `GET /api/books/{id}/availability`
- **THEN** the system responds `200 OK` with `[{"branchCode": "CENTRAL", "total": 2, "onShelf": 2, ...}, {"branchCode": "NORTH", "total": 1, "onShelf": 1, ...}]`

#### Scenario: Copies on loan are counted but not on the shelf
- **WHEN** a book has 2 copies in branch `CENTRAL`, one of them is checked out with `POST /api/loans`, and a client sends `GET /api/books/{id}/availability`
- **THEN** the system responds `200 OK` with `CENTRAL` `total` 2 and `onShelf` 1; after the loan is returned with `POST /api/loans/{loanId}/return` the same request shows `onShelf` 2

#### Scenario: Book without copies
- **WHEN** a book has no copies and a client sends `GET /api/books/{id}/availability`
- **THEN** the system responds `200 OK` with an empty array

#### Scenario: Availability of an unknown book
- **WHEN** a client sends `GET /api/books/{id}/availability` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BOOK_NOT_FOUND`
