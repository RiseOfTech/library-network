# Spec Delta

## Purpose

Lets librarians register and maintain the branches of the library network, so copies, loans and
holds can refer to a concrete branch.

## ADDED Requirements

### Requirement: Register a branch
The system SHALL allow creating a branch with a `code`, a `name` and an `address`. `code` is 2-20
characters of uppercase letters, digits and hyphens, unique across the network, and cannot be
changed later. `name` and `address` are required and non-blank. The response contains the branch
with its generated `id`.

#### Scenario: Branch is created
- **WHEN** a client sends `POST /api/branches` with `{"code": "CENTRAL", "name": "Central Library", "address": "1 Main St"}`
- **THEN** the system responds `201 Created` with a `Location` header pointing to `/api/branches/{id}` and a body containing `id`, `code`, `name` and `address`

#### Scenario: Branch code is already taken
- **WHEN** a branch with code `CENTRAL` exists and a client sends `POST /api/branches` with code `CENTRAL`
- **THEN** the system responds `409 Conflict` with code `BRANCH_CODE_TAKEN`

#### Scenario: Invalid branch data
- **WHEN** a client sends `POST /api/branches` with a blank `name` or a `code` like `central lib`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and the invalid fields listed in `errors`

### Requirement: Update a branch
The system SHALL allow changing the `name` and `address` of an existing branch. The `code` stays
unchanged.

#### Scenario: Branch is updated
- **WHEN** a client sends `PUT /api/branches/{id}` with `{"name": "Central City Library", "address": "2 Main St"}` for an existing branch
- **THEN** the system responds `200 OK` with the updated branch and the original `code`

#### Scenario: Updating an unknown branch
- **WHEN** a client sends `PUT /api/branches/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BRANCH_NOT_FOUND`

### Requirement: Read branches
The system SHALL return a single branch by id and the list of all branches ordered by `code`.

#### Scenario: Get a branch by id
- **WHEN** a client sends `GET /api/branches/{id}` for an existing branch
- **THEN** the system responds `200 OK` with the branch

#### Scenario: Get an unknown branch
- **WHEN** a client sends `GET /api/branches/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BRANCH_NOT_FOUND`

#### Scenario: List branches
- **WHEN** branches `NORTH` and `CENTRAL` exist and a client sends `GET /api/branches`
- **THEN** the system responds `200 OK` with a JSON array of both branches, `CENTRAL` first
