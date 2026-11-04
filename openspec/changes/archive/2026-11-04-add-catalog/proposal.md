# Proposal

## Why

Every other feature of the library network (readers borrowing books, holds, transfers between
branches) needs to know which branches exist, which books are in the shared catalog and where the
physical copies are. This change introduces that foundation so later changes can build on it.

## What Changes

- New REST API to register and update **branches** of the library network.
- New REST API for the shared **book catalog**: create, update, get and search books by title,
  author or ISBN. ISBN is validated and unique across the catalog.
- New REST API for **copies**: register a physical copy of a book in a branch, look it up, list
  copies by book, branch and status, and see per-branch availability of a book.
- Copy status model with the full set of statuses used by the network: `ON_SHELF`, `ON_LOAN`,
  `ON_HOLD`, `IN_TRANSIT`, `LOST`. In this change a copy can only be created (`ON_SHELF`),
  marked lost from the shelf, and marked found. Other transitions belong to later changes.
- Paginated list responses with a common shape.

## Capabilities

### New Capabilities

- `branches`: registering and maintaining library branches.
- `catalog`: the shared book catalog (book records, ISBN rules, search).
- `copies`: physical copies of books in branches, their status and per-branch availability.

### Modified Capabilities

None.

## Out of scope

- Deleting branches, books or copies (would orphan loans and history; may come later).
- Moving a copy to another branch (comes with `add-transfers`).
- Status transitions caused by loans, holds and transfers (`ON_LOAN`, `ON_HOLD`, `IN_TRANSIT`).
- Readers, loans, fines and anything that needs a reader.
- Authentication and authorization.
- Full-text search, book covers, genres, publishers and other bibliographic metadata.

## Configuration

No new business configuration properties. Page size defaults (20, max 100) are API conventions,
not business rules.

## Impact

- New domain package `catalog` (branches, books, copies) and a shared pagination response in `common`.
- New Flyway migration with tables for branches, books, book authors and copies.
- New endpoints under `/api/branches`, `/api/books`, `/api/copies`, visible in OpenAPI.
