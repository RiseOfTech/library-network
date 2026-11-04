# Финальное ревью add-readers (группы 1–6) — 2026-11-04
Прогон: УСПЕШНО (`./gradlew build -q`, 180 тестов, 0 падений/пропусков; `scripts/build-report.sh` в репозитории нет)

| Пункт | Статус | Доказательство / причина |
|---|---|---|
| 1.1 `ClockConfiguration` + `ClockProvider` | ✅ | `common/ClockConfiguration`, `ValidationClockTest` |
| 1.2 Фиксированный `Clock` в тестах | ✅ | `FixedClockConfiguration` (`@Primary`, 2026-11-04), импорт в `IntegrationTest` |
| 1.3 LIKE-экранирование в `common` | ✅ | `common/LikePatterns`, используется в `BookService`; `BookApiTest` зелёный |
| 1.4 Тесты экранирования и clock | ✅ | `LikePatternsTest`, `ValidationClockTest` |
| 2.1 `V3__readers.sql` | ✅ | sequence, `reader`, `uk_reader_card_number`, `ck_reader_contact`, `ix_reader_last_name`, `ix_reader_category` |
| 2.2 `ApplicationSmokeTest` с `validate` | ✅ | сущность `Reader` есть, Hibernate `validate` проходит, тест зелёный |
| 3.1 `ReaderCategoriesProperties` + дефолты | ✅ | `@Validated` record, `application.yml` adult 5/21, student 10/30, child 3/14/14; `@ConfigurationPropertiesScan` |
| 3.2 `ReaderCategory`, `ReaderCategories` | ✅ | upper-case, `TreeMap`, `find`/`all`/`isAllowedFor`; повтор кода → ошибка старта |
| 3.3 `@KnownCategory` | ✅ | валидатор с внедрением `ReaderCategories`; `unknownCategoryIsRejected`, `categoryIsMatchedExactly` |
| 3.4 `GET /api/reader-categories` | ✅ | `ReaderCategoryController`, `ReaderCategoryApiTest` |
| 3.5 Тесты категорий | ✅ | `ReaderCategoriesTest` (день до/в/после 14-летия, без лимита), `ReaderCategoriesPropertiesTest` (пустая map, `loan-limit=0`, `max-age=0`), `ReaderCategoryApiTest` + `@Nested` с `student.loan-limit=8` |
| 4.1 `Reader` + `ReaderRepository` | ✅ | `updatable = false`, `@Version`; `nextCardNumber`, `findByCardNumber`, `search` с опц. категорией; сортировка в сервисе |
| 4.2 Исключения | ✅ | `ReaderNotFoundException` (404), `ReaderCategoryNotAllowedException` (409) |
| 4.3 `ReaderService` | ✅ | `LN-%06d`, проверка возраста при регистрации и при смене категории/даты рождения, blank→null, `saveAndFlush` |
| 4.4 Тесты домена | ✅ | `ReaderServiceTest`, `ReaderRepositoryTest` |
| 5.1 DTO | ✅ | `ReaderDtos.ReaderRequest` с нужными ограничениями, без `cardNumber`; `@ContactRequired` → поле `email` |
| 5.2 `ReaderController` | ✅ | POST/PUT/GET/search, `@KnownCategory` на `category`, page ≥0, size 1–100 |
| 5.3 `ReaderApiTest` | ✅ | покрыты все сценарии `readers/spec.md` (кроме concurrency) и сценарии «Only configured categories» / «Category age limit» |
| 5.4 `ReaderConcurrencyTest` | ✅ | параллельные POST → разные карты; параллельные PUT → 200/409 `CONCURRENT_MODIFICATION`, сохранено одно из двух тел |
| 6.1 Запуск через docker compose + curl | ✅ | выполнено ревьюером: `bootRun` против postgres из `compose.yaml`; Flyway: схема на V3; в `/v3/api-docs` есть `/api/readers`, `/api/readers/{id}`, `/api/reader-categories`; категории ADULT/CHILD/STUDENT → POST 201 `LN-000002` (`cardNumber` из запроса проигнорирован) → поиск `q=ln-000002` нашёл читателя → PUT в STUDENT 200 → PUT в CHILD 409 `READER_CATEGORY_NOT_ALLOWED` |
| 6.2 `./gradlew build` зелёный | ✅ | прогон этого ревью |

Регрессии: нет (каталог и `BookApiTest` зелёные)
Замечания:
- Код групп 3–5 только в индексе (staged), не закоммичен; изменения `tasks.md`, `application.yml`, `LibraryNetworkApplication.java` не в индексе.
- progress.md нет — переносить в design.md нечего, раздел «Принятые решения» не добавлен.

- Во время проверки 6.1 в локальной БД остался тестовый читатель `LN-000002` (Smoke Review); эндпоинта для удаления нет.

Готовность к `/opsx:archive`: готов, открытых пунктов нет. Сначала закоммитить изменения.
