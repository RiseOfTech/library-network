# library-network

Backend service for a network of city libraries with several branches:
a shared catalog, readers with a network-wide membership, loans, holds,
transfers between branches and overdue fines.

The project is developed spec-first with [OpenSpec](https://github.com/Fission-AI/OpenSpec):
every feature is a separate change with a proposal, specs, design and tasks.
Current specs live in [`openspec/specs`](openspec/specs), finished changes in
[`openspec/changes/archive`](openspec/changes/archive).

## Roadmap

Each line is one OpenSpec change, done strictly in this order: the next change starts only
after the previous one is archived. Numbers are proposed defaults; the final values are fixed
in each change's proposal and live in configuration.

| # | Change | Scope | Boundary with other changes |
|---|---|---|---|
| 1 | `add-catalog` | Branches, books (ISBN-13, search), copies with statuses `ON_SHELF`, `ON_LOAN`, `ON_HOLD`, `IN_TRANSIT`, `LOST`; mark lost / found; availability per branch | Only lost/found transitions; others come with loans, holds, transfers |
| 2 | `add-readers` | Reader categories from configuration (adult: 5 loans / 21 days, student: 10 / 30, child: 3 / 14), reader registration with a unique card number, update, search | No loans yet; fines and blocking come later |
| 3 | `add-loans` | Checkout of an `ON_SHELF` copy in its branch, return, category loan limit, due date from category period, two readers cannot check out the same copy | Return only at the copy's own branch (returns elsewhere come with transfers); no renewals, holds or fines |
| 4 | `add-renewals` | Renew a loan up to 2 times, each by the category period from the current due date; no renewal of an overdue loan | The "no renewal when a hold queue exists" rule is added by `add-holds` |
| 5 | `add-holds` | Hold queue per book (not per copy) with a pickup branch; a returned or free copy in the pickup branch goes to the first in queue (`ON_HOLD`), pickup window 5 days; checkout of a held copy only by its holder; last-copy protection under concurrency; blocks renewals when the queue is not empty | Only copies already in the pickup branch serve holds; cross-branch delivery comes with transfers |
| 6 | `add-transfers` | Transfer of a copy between branches: requested, shipped (`IN_TRANSIT`), received. Created automatically when a hold can only be served from another branch; return at a foreign branch sends the copy home | — |
| 7 | `add-fines` | Overdue fine per day (0.20 per day, in minor units), charged on late return; payments; checkout blocked while unpaid fines exceed 5.00 | Daily accrual for still-open loans comes with the job |
| 8 | `add-overdue-job` | Nightly scheduled job (cron in configuration): marks overdue loans, accrues fines for open overdue loans, expires uncollected holds and passes the copy to the next in queue. Idempotent per day | — |
| 9 | `add-notifications` | Notifications through a pluggable sender (log stub by default): hold ready for pickup, loan due in 2 days (sent by the nightly job) | — |

## Stack

Java 21, Spring Boot 4, Gradle (Kotlin DSL), PostgreSQL, Flyway,
springdoc-openapi, JUnit 5, Testcontainers.

## Requirements

- JDK 17+ to launch Gradle. The build uses a Java 21 toolchain and downloads it if missing.
- Docker (with Docker Compose) for the local database and for integration tests.

## Run locally

```bash
docker compose up -d          # PostgreSQL on localhost:5432
./gradlew bootRun             # application on http://localhost:8080
```

Database connection can be overridden with `DB_URL`, `DB_USER`, `DB_PASSWORD`.

Without docker compose, the app can run against a throwaway Testcontainers database:

```bash
./gradlew bootTestRun
```

Useful endpoints:

- Health: http://localhost:8080/actuator/health
- OpenAPI: http://localhost:8080/v3/api-docs
- Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

```bash
./gradlew test       # unit and integration tests (needs Docker)
./gradlew build      # compile, test, package
```

Integration tests start PostgreSQL via Testcontainers.

## API errors

All errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
`application/problem+json` with an extra machine-readable `code`:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Reader has reached the loan limit",
  "code": "LOAN_LIMIT_EXCEEDED"
}
```

Validation errors use `code: VALIDATION_FAILED` and list invalid fields in `errors`.
