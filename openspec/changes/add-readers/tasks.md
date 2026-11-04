# Tasks

## 1. Shared infrastructure

- [x] 1.1 Add `common/ClockConfiguration` with a `Clock` bean (`Clock.systemDefaultZone()`) and a `ValidationConfigurationCustomizer` that sets a `ClockProvider` backed by it
- [x] 1.2 Add a test `@TestConfiguration` with a `@Primary` fixed `Clock` at `2026-11-04` and import it in `IntegrationTest`
- [x] 1.3 Move the LIKE escaping from `BookService` into a `common` helper (lower-case, escape `\`, `%`, `_`, wrap in `%`) and use it from `BookService`; `BookApiTest` still passes
- [x] 1.4 Add tests: unit test for the escaping helper (`%`, `_`, `\`, mixed case); integration test that `@PastOrPresent` uses the fixed clock (a date one day after the fixed date is rejected, the fixed date itself is accepted); verify they pass

## 2. Database schema

- [ ] 2.1 Add `V3__readers.sql` with `reader_card_number_seq`, table `reader`, `uk_reader_card_number`, `ck_reader_contact` and indexes `ix_reader_last_name`, `ix_reader_category` from design.md
- [ ] 2.2 Verify `ApplicationSmokeTest` passes (Flyway applies V3; Hibernate `validate` succeeds once the `Reader` entity exists)

## 3. Reader categories

- [ ] 3.1 Add `ReaderCategoriesProperties` (`library.readers`, `@Validated`: non-empty map, positive `loanLimit`, `loanPeriodDays`, optional positive `maxAgeExclusive`) and the defaults `adult` 5/21, `student` 10/30, `child` 3/14 with `max-age-exclusive: 14` in `application.yml`
- [ ] 3.2 Add `ReaderCategory` record and `ReaderCategories` bean (upper-cased codes, sorted by code, `find(code)`, `all()`, `isAllowedFor(category, dateOfBirth, today)`)
- [ ] 3.3 Add `@KnownCategory` constraint with a Spring-managed validator backed by `ReaderCategories`
- [ ] 3.4 Add `ReaderCategoryController` for `GET /api/reader-categories`
- [ ] 3.5 Add tests: unit test for `ReaderCategories` (key upper-casing, ordering, age rule on the day before, on and after the 14th birthday, category without age limit); context test that invalid configuration (empty map, zero `loan-limit`) fails startup; `ReaderCategoryApiTest` (Testcontainers) for both scenarios of "Categories come from configuration", including a property override `library.readers.categories.student.loan-limit=8`; verify they pass

## 4. Readers: domain

- [ ] 4.1 Add `Reader` entity (`card_number` `updatable = false`, `@Version`) and `ReaderRepository` with `nextCardNumber()` (native `nextval`), exact card lookup with optional category, and the paged name search with optional category from design.md (sort `lastName, firstName, id`)
- [ ] 4.2 Add `ReaderNotFoundException` (`404 READER_NOT_FOUND`) and `ReaderCategoryNotAllowedException` (`409 READER_CATEGORY_NOT_ALLOWED`)
- [ ] 4.3 Add `ReaderService`: register (generate `LN-%06d`, age check), update (age check only when category or date of birth changes, card number untouched), get, search (card form `(?i)^LN-\d+$` → exact match, else name search); blank contacts normalized to `null`; `saveAndFlush` in write methods
- [ ] 4.4 Add tests: unit test for card number formatting (`1` → `LN-000001`, `1234567` → `LN-1234567`) and card-form detection of `q`; repository integration test that `nextCardNumber()` returns increasing values; verify they pass

## 5. Readers: API

- [ ] 5.1 Add request/response DTOs: `@NotBlank @Size(max = 100)` names, `@NotNull @PastOrPresent` `dateOfBirth`, `@NotNull @KnownCategory` `category`, `@Email @Size(max = 254)` email, phone pattern, no `cardNumber` in requests; class-level `@ContactRequired` reported on `email`
- [ ] 5.2 Add `ReaderController` for `POST /api/readers`, `PUT /api/readers/{id}`, `GET /api/readers/{id}` and `GET /api/readers?q=&category=&page=&size=` (`@KnownCategory` on `category`, page/size limits as for books, `PageResponse`)
- [ ] 5.3 Add `ReaderApiTest` (Testcontainers) covering every scenario in `specs/readers/spec.md` except the concurrency ones, and the "Only configured categories are accepted" and "Category age limit" scenarios in `specs/reader-categories/spec.md` (fixed clock 2026-11-04); verify it passes
- [ ] 5.4 Add `ReaderConcurrencyTest`: parallel `POST /api/readers` all return `201` with distinct card numbers; two parallel `PUT /api/readers/{id}` on one reader end with the stored reader equal to one of the two requests and any failure being `409 CONCURRENT_MODIFICATION`; verify it passes

## 6. Integration check

- [ ] 6.1 Run the app against `docker compose`, check that `/api/readers` and `/api/reader-categories` appear in `/v3/api-docs`, and walk through list categories → register reader → search by card → update category with curl
- [ ] 6.2 Run `./gradlew build` and verify it is green

## Workflow follow-up

- Run `task-review` after each task group and before archive.
- Show the result for review, propose a commit message.
- Archive the change with `/opsx:archive` after approval.
