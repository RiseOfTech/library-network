# reader-categories Specification

## Purpose

Defines the reader categories of the library network: how many loans a reader of each category may
have, for how many days, and which readers a category is allowed for.

## Requirements

### Requirement: Categories come from configuration
The system SHALL offer a fixed set of reader categories defined in configuration, each with a
`loanLimit` and a `loanPeriodDays` (positive integers) and an optional `maxAgeExclusive`. Default
categories: `ADULT` 5 / 21 days, `STUDENT` 10 / 30 days, `CHILD` 3 / 14 days with `maxAgeExclusive`
14. Changing the numbers requires only a configuration change.

#### Scenario: Default categories are listed
- **WHEN** the application runs with the default configuration and a client sends `GET /api/reader-categories`
- **THEN** the system responds `200 OK` with a JSON array ordered by `code`: `{"code": "ADULT", "loanLimit": 5, "loanPeriodDays": 21, "maxAgeExclusive": null}`, `{"code": "CHILD", "loanLimit": 3, "loanPeriodDays": 14, "maxAgeExclusive": 14}`, `{"code": "STUDENT", "loanLimit": 10, "loanPeriodDays": 30, "maxAgeExclusive": null}`

#### Scenario: Configured numbers are used
- **WHEN** the configuration sets the `STUDENT` loan limit to 8 and a client sends `GET /api/reader-categories`
- **THEN** the system responds `200 OK` and the `STUDENT` entry has `loanLimit` 8

### Requirement: Only configured categories are accepted
The system SHALL accept as a reader's `category` only one of the configured category codes, matched
exactly.

#### Scenario: Known category is accepted
- **WHEN** a client sends `POST /api/readers` with valid reader data and `category` `STUDENT`
- **THEN** the system responds `201 Created` and the returned reader has `category` `STUDENT`

#### Scenario: Unknown category is rejected
- **WHEN** a client sends `POST /api/readers` with valid reader data and `category` `SENIOR`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and `category` listed in `errors`

#### Scenario: Unknown category in the search filter
- **WHEN** a client sends `GET /api/readers?category=SENIOR`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and `category` listed in `errors`

### Requirement: Category age limit
The system SHALL allow a category that has `maxAgeExclusive` only for readers whose age in full years
on the current date is below that value. The check runs on registration and on every update that
changes the category or the date of birth. Categories without `maxAgeExclusive` have no age check.

#### Scenario: Child category for a 13-year-old
- **WHEN** today is 2026-11-04 and a client sends `POST /api/readers` with `category` `CHILD` and `dateOfBirth` `2012-11-05`
- **THEN** the system responds `201 Created` with `category` `CHILD`

#### Scenario: Child category on the 14th birthday
- **WHEN** today is 2026-11-04 and a client sends `POST /api/readers` with `category` `CHILD` and `dateOfBirth` `2012-11-04`
- **THEN** the system responds `409 Conflict` with code `READER_CATEGORY_NOT_ALLOWED` and no reader is created

#### Scenario: Changing an adult to child
- **WHEN** a reader born on 1990-01-01 has category `ADULT` and a client sends `PUT /api/readers/{id}` with `category` `CHILD`
- **THEN** the system responds `409 Conflict` with code `READER_CATEGORY_NOT_ALLOWED` and the reader is unchanged

#### Scenario: Changing the date of birth of a child
- **WHEN** a reader has category `CHILD` and a client sends `PUT /api/readers/{id}` keeping `category` `CHILD` and setting a `dateOfBirth` that makes the reader 20 years old
- **THEN** the system responds `409 Conflict` with code `READER_CATEGORY_NOT_ALLOWED` and the reader is unchanged

#### Scenario: Child who has grown up can still be updated
- **WHEN** a reader registered as `CHILD` is now 14 and a client sends `PUT /api/readers/{id}` with a new `phone`, keeping `category` and `dateOfBirth` unchanged
- **THEN** the system responds `200 OK` with the new `phone` and `category` `CHILD`

#### Scenario: Adult category has no age check
- **WHEN** a client sends `POST /api/readers` with `category` `ADULT` and `dateOfBirth` of a 10-year-old
- **THEN** the system responds `201 Created` with `category` `ADULT`
