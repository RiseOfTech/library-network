# catalog Specification

## Purpose

Maintains the shared book catalog of the library network: one record per book (title, authors,
ISBN), independent of how many physical copies exist and where.

## Requirements

### Requirement: Add a book to the catalog
The system SHALL allow creating a book with a `title`, a non-empty list of `authors`, an `isbn` and
an optional `publicationYear`. The response contains the book with its generated `id`.

#### Scenario: Book is created
- **WHEN** a client sends `POST /api/books` with `{"title": "Dune", "authors": ["Frank Herbert"], "isbn": "978-0-441-17271-9", "publicationYear": 1965}`
- **THEN** the system responds `201 Created` with a `Location` header pointing to `/api/books/{id}` and a body containing `id`, `title`, `authors`, `isbn` and `publicationYear`

#### Scenario: Missing title or authors
- **WHEN** a client sends `POST /api/books` with a blank `title`, or with `authors` empty or containing a blank name
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and the invalid fields listed in `errors`

### Requirement: ISBN is a valid ISBN-13
The system SHALL accept an `isbn` only if it is a valid ISBN-13 (13 digits with a correct check
digit). Hyphens and spaces in the input are ignored. The book is stored and returned with the ISBN
as 13 digits without separators.

#### Scenario: ISBN with hyphens is normalized
- **WHEN** a client sends `POST /api/books` with `isbn` `978-0-441-17271-9`
- **THEN** the system responds `201 Created` and the returned `isbn` is `9780441172719`

#### Scenario: ISBN with a wrong check digit
- **WHEN** a client sends `POST /api/books` with `isbn` `9780441172710`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and `isbn` listed in `errors`

#### Scenario: ISBN of wrong length
- **WHEN** a client sends `POST /api/books` with `isbn` `0441172717` (ISBN-10)
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED` and `isbn` listed in `errors`

### Requirement: ISBN is unique in the catalog
The system SHALL keep at most one book per ISBN, both on create and on update.

#### Scenario: Creating a book with an existing ISBN
- **WHEN** a book with ISBN `9780441172719` exists and a client sends `POST /api/books` with `isbn` `978-0441172719`
- **THEN** the system responds `409 Conflict` with code `BOOK_ISBN_TAKEN`

#### Scenario: Updating a book to another book's ISBN
- **WHEN** books A and B exist and a client sends `PUT /api/books/{idOfA}` with the ISBN of book B
- **THEN** the system responds `409 Conflict` with code `BOOK_ISBN_TAKEN` and book A is unchanged

### Requirement: Update a book
The system SHALL allow replacing `title`, `authors`, `isbn` and `publicationYear` of an existing
book, with the same validation as on create.

#### Scenario: Book is updated
- **WHEN** a client sends `PUT /api/books/{id}` with a new `title` and `authors` for an existing book
- **THEN** the system responds `200 OK` with the updated book

#### Scenario: Updating an unknown book
- **WHEN** a client sends `PUT /api/books/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BOOK_NOT_FOUND`

### Requirement: Get a book
The system SHALL return a single book by id.

#### Scenario: Get a book by id
- **WHEN** a client sends `GET /api/books/{id}` for an existing book
- **THEN** the system responds `200 OK` with the book

#### Scenario: Get an unknown book
- **WHEN** a client sends `GET /api/books/{id}` for an id that does not exist
- **THEN** the system responds `404 Not Found` with code `BOOK_NOT_FOUND`

### Requirement: Search the catalog
The system SHALL return books matching an optional `q` parameter: a case-insensitive substring of the
title or of any author name, or an exact ISBN (with or without separators). Without `q` all books
are returned. Results are ordered by title and paginated.

#### Scenario: Search by title fragment
- **WHEN** books "Dune" and "Dune Messiah" and "Solaris" exist and a client sends `GET /api/books?q=dune`
- **THEN** the system responds `200 OK` with "Dune" and "Dune Messiah" in `items`

#### Scenario: Search by author
- **WHEN** a client sends `GET /api/books?q=herbert`
- **THEN** the system responds `200 OK` with the books whose authors include "Frank Herbert"

#### Scenario: Search by ISBN
- **WHEN** a client sends `GET /api/books?q=978-0-441-17271-9`
- **THEN** the system responds `200 OK` with exactly the book with ISBN `9780441172719`

#### Scenario: Nothing matches
- **WHEN** a client sends `GET /api/books?q=nonexistent`
- **THEN** the system responds `200 OK` with an empty `items` and `totalElements` 0

### Requirement: Paginated list responses
List endpoints that can grow without bound SHALL accept `page` (0-based, default 0) and `size`
(default 20, 1 to 100) and return `items`, `page`, `size`, `totalElements` and `totalPages`.

#### Scenario: Second page
- **WHEN** 25 books exist and a client sends `GET /api/books?page=1&size=10`
- **THEN** the system responds `200 OK` with 10 `items`, `page` 1, `size` 10, `totalElements` 25 and `totalPages` 3

#### Scenario: Page size out of range
- **WHEN** a client sends `GET /api/books?size=500`
- **THEN** the system responds `400 Bad Request` with code `VALIDATION_FAILED`
