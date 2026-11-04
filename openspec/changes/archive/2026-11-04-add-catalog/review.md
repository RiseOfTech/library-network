# Финальное ревью add-catalog — 2026-11-03
Прогон: УСПЕШНО (`./gradlew build -q`, exit 0; 106 тестов, 0 failures/errors/skipped).
`scripts/build-report.sh` в репозитории нет, прогон выполнен напрямую.

| Пункт | Статус | Доказательство / причина |
|---|---|---|
| 1.1 PageResponse | ✅ | `common/PageResponse.java`, `PageResponseTest` (3) |
| 1.2 Ошибки параметров → VALIDATION_FAILED | ✅ | `GlobalExceptionHandler.handleHandlerMethodValidationException`, `handleTypeMismatch`; `ErrorFormatTest.requestParam*` |
| 1.3 Optimistic lock → 409 CONCURRENT_MODIFICATION | ✅ | `GlobalExceptionHandler.handleOptimisticLock`; `ErrorFormatTest.optimisticLockFailureReturnsConcurrentModification` |
| 2.1 V2__catalog.sql | ✅ | таблицы, `uk_*`, `ck_copy_status`, индексы из design.md; `ApplicationSmokeTest.migrationsAreApplied`, `ddl-auto: validate` |
| 3.1 Branch + репозиторий | ✅ | `Branch`, `BranchRepository`; контекст поднимается с validate |
| 3.2 BranchService | ✅ | `BRANCH_NOT_FOUND`, `BRANCH_CODE_TAKEN`, обработка гонки; `BranchApiTest.duplicateCodeViolatesNamedConstraint` |
| 3.3 BranchController + валидация | ✅ | `BranchDtos` (`@Pattern`, `@NotBlank`); `malformedCodeIsRejected`, `blankNameIsRejected` |
| 3.4 BranchApiTest | ✅ | все 8 сценариев `specs/branches`, 11 тестов зелёные |
| 4.1 Isbn + @ValidIsbn | ✅ | `Isbn`, `ValidIsbn`; `IsbnTest` (18): check digit, длина, дефисы/пробелы |
| 4.2 Book + авторы + поиск | ✅ | `@ElementCollection` + `@OrderColumn`, `BookRepository.searchByTitleOrAuthor` |
| 4.3 BookService | ✅ | нормализация, `BOOK_NOT_FOUND`, `BOOK_ISBN_TAKEN` на create/update; `updatingBookToAnotherBooksIsbn` |
| 4.4 BookController | ✅ | `size` `@Min(1) @Max(100)`, `page` `@Min(0)`; `pageSizeOutOfRange`, `negativePageIsRejected` |
| 4.5 BookApiTest | ✅ | все сценарии `specs/catalog`, включая пагинацию и ISBN-10; 25 тестов |
| 5.1 CopyStatus + Copy | ✅ | `@Version`, `markLost/markFound`; `CopyTest` (11) с параметризацией по всем статусам |
| 5.2 CopyRepository | ✅ | `findFiltered`, `availability` (`sum(case ...)`, DTO projection) |
| 5.3 CopyService | ✅ | все 4 кода ошибок; `CopyApiTest` |
| 5.4 CopyController | ✅ | 6 эндпоинтов, включая `/api/books/{id}/availability` |
| 5.5 CopyApiTest | ✅ | все сценарии `specs/copies`; 25 тестов |
| 5.6 Тест конкурентности | ✅ | `CopyConcurrencyTest`: `containsExactlyInAnyOrder(200, 409)` в каждом раунде |
| 6.1 `./gradlew build` зелёный | ✅ | прогон этого ревью |
| 6.2 Ручной прогон через docker compose + curl | ✅ | 2026-11-04: `bootRun` на базе из compose, Flyway V1+V2 применены (`success = t`); все 9 путей `/api/*` есть в `/v3/api-docs`; branch → book (ISBN с дефисами нормализован) → copy `ON_SHELF` → availability `total 1, onShelf 1`; lost → `0/0`, повторный lost → `409 COPY_STATUS_CONFLICT`, found → `1/1`; `copy.version = 2` |
| Workflow: показать результат, предложить commit message | 👤 | вне кода |
| Workflow: `/opsx:archive` после одобрения | 👤 | вне кода |

Регрессии: нет (`ErrorFormatTest`, `ApplicationSmokeTest` из скелета зелёные).

Замечания, не блокируют:
- `progress.md` нет. Решения перенесены в design.md («Принятые решения») по коду и тестам.
- `publicationYear` не валидируется по диапазону. Спека этого не требует.

Рекомендация: все пункты кода закрыты, change готов к `/opsx:archive` после одобрения человеком.
