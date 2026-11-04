# Ревью группы 2 — 2026-11-04
Прогон: УСПЕШНО (`./gradlew build -q`, 114 тестов, 0 падений; `scripts/build-report.sh` в репозитории нет)

| Пункт | Статус | Доказательство / причина |
|---|---|---|
| 2.1 `V3__readers.sql` | ✅ | `db/migration/V3__readers.sql`: `reader_card_number_seq`, `reader` (колонки и типы как в design.md), `uk_reader_card_number`, `ck_reader_contact`, `ix_reader_last_name (lower(last_name))`, `ix_reader_category`; нет check на `category`; V1/V2 не тронуты. `ApplicationSmokeTest.migrationsAreApplied` — pending пуст |
| 2.2 `ApplicationSmokeTest` (Flyway + Hibernate `validate`) | ❌ | частично: Flyway применяет V3, тест зелёный. Сущности `Reader` ещё нет (4.1), поэтому `validate` таблицу `reader` не проверяет. Отметить после 4.1 повторным прогоном |

Регрессии: нет
Замечание: `V3__readers.sql` не закоммичен (untracked). progress.md нет — сворачивать нечего.
Рекомендация: можно переходить к группе 3; 2.2 закрыть после 4.1
