# Spec Delta

## Purpose

Tracks the physical copies of catalog books: which branch holds each copy, its inventory number and
its current status, and how many copies of a book are available in each branch.

## ADDED Requirements

### Requirement: Register a copy
The system SHALL allow registering a copy of an existing book in an existing branch with an
`inventoryNumber`: 3-30 characters of uppercase letters, digits and hyphens, unique across the
network. A new copy has status `ON_SHELF`.

#### Scenario: Copy is registered
- **WHEN** a client sends `POST /api/copies` with `{"bookId": 1, "branchId": 1, "inventoryNumber": "CEN-000123"}` for an existing book and branch
- **THEN** the system responds `201 Created` with a `Location` header pointing to `/api/copies/{id}` and a body containing `id`, `bookId`, `branchId`, `inventoryNumber` and `status` `ON_SHELF`

#### Scenario: Inventory number is already taken
- **WHEN** a copy with inventory number `CEN-000123` exists and a client sends `POST /api/copies` with the same `inventoryNumber`
- **THEN** the system responds `409 Conflict` with code `COPY_INVENTORY_NUMBER_TAKEN`

#### Scenario: Unknown book
- **WHEN** a client sends `POST /api/copies` with a `bookId` that does not exist
- **THEN** the system responds `404 Not Found` with code `BOOK_NOT_FOUND`

#### Scenario: Unknown branch
- **WHEN** a client sends `POST /api/copies` with a `branchId` that does not exist
- **THEN** the system responds `404 Not Found` with code `BRANCH_NOT_FOUND`

#### Scenario: Invalid copy data
- **WHEN** a client sends `POST /api/copies` without `bookId` or with `inventoryNumber` `x`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and the invalid fields listed in `errors`

### Requirement: Copy statuses
Every copy SHALL have exactly one status: `ON_SHELF`, `ON_LOAN`, `ON_HOLD`, `IN_TRANSIT` or `LOST`.
The status is returned with the copy and cannot be set directly by the client; it only changes
through the operations defined for copies, loans, holds and transfers.

#### Scenario: Status is part of the copy
- **WHEN** a client sends `GET /api/copies/{id}` for an existing copy
- **THEN** the system responds `200 OK` with the copy including its `status`

#### Scenario: Unknown copy
- **WHEN** a client sends `GET /api/copies/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `COPY_NOT_FOUND`

### Requirement: Mark a copy as lost
The system SHALL allow marking a copy that is `ON_SHELF` as `LOST`. A copy in any other status
cannot be marked lost through this operation.

#### Scenario: Copy on the shelf is marked lost
- **WHEN** a client sends `POST /api/copies/{id}/lost` for a copy with status `ON_SHELF`
- **THEN** the system responds `200 OK` with the copy and `status` `LOST`

#### Scenario: Copy is not on the shelf
- **WHEN** a client sends `POST /api/copies/{id}/lost` for a copy with status `LOST`
- **THEN** the system responds `409 Conflict` with code `COPY_STATUS_CONFLICT` and the copy status is unchanged

### Requirement: Mark a lost copy as found
The system SHALL allow returning a `LOST` copy to `ON_SHELF` in the branch it belongs to.

#### Scenario: Lost copy is found
- **WHEN** a client sends `POST /api/copies/{id}/found` for a copy with status `LOST`
- **THEN** the system responds `200 OK` with the copy and `status` `ON_SHELF`

#### Scenario: Copy is not lost
- **WHEN** a client sends `POST /api/copies/{id}/found` for a copy with status `ON_SHELF`
- **THEN** the system responds `409 Conflict` with code `COPY_STATUS_CONFLICT`

### Requirement: List copies
The system SHALL list copies filtered by any combination of optional `bookId`, `branchId` and
`status` parameters, ordered by `inventoryNumber` and paginated like other list endpoints.

#### Scenario: Copies of a book in a branch
- **WHEN** a book has 2 copies in branch A and 1 in branch B and a client sends `GET /api/copies?bookId={book}&branchId={A}`
- **THEN** the system responds `200 OK` with the 2 copies from branch A in `items` and `totalElements` 2

#### Scenario: Filter by status
- **WHEN** a client sends `GET /api/copies?status=LOST`
- **THEN** the system responds `200 OK` with only copies whose status is `LOST`

#### Scenario: Unknown status value
- **WHEN** a client sends `GET /api/copies?status=BROKEN`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED`

### Requirement: Book availability by branch
The system SHALL return, for a book, one entry per branch that holds at least one copy of it, with
`branchId`, `branchCode`, `total` (copies in any status except `LOST`) and `onShelf` (copies with
status `ON_SHELF`), ordered by `branchCode`.

#### Scenario: Availability across branches
- **WHEN** a book has in branch `CENTRAL` 2 copies `ON_SHELF` and 1 `LOST`, and in branch `NORTH` 1 copy `ON_SHELF`, and a client sends `GET /api/books/{id}/availability`
- **THEN** the system responds `200 OK` with `[{"branchCode": "CENTRAL", "total": 2, "onShelf": 2, ...}, {"branchCode": "NORTH", "total": 1, "onShelf": 1, ...}]`

#### Scenario: Book without copies
- **WHEN** a book has no copies and a client sends `GET /api/books/{id}/availability`
- **THEN** the system responds `200 OK` with an empty array

#### Scenario: Availability of an unknown book
- **WHEN** a client sends `GET /api/books/{id}/availability` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BOOK_NOT_FOUND`
