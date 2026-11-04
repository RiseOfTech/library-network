# library-network

Backend service for a network of city libraries with several branches:
a shared catalog, readers with a network-wide membership, loans, holds,
transfers between branches and overdue fines.

The project is developed spec-first with [OpenSpec](https://github.com/Fission-AI/OpenSpec):
every feature is a separate change with a proposal, specs, design and tasks.
Current specs live in [`openspec/specs`](openspec/specs), finished changes in
[`openspec/changes/archive`](openspec/changes/archive).

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
