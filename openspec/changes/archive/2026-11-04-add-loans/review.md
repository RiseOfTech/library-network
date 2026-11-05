# Финальное ревью add-loans — 2026-11-04
Прогон: УСПЕШНО — `./gradlew cleanTest build -q`, 241 тест, 0 упавших
(`scripts/build-report.sh` в репозитории нет, использован эквивалентный тихий прогон).

Ограничение: ревью сделано в той же сессии, что и реализация, а не в свежей.

| Пункт | Статус | Доказательство / причина |
|---|---|---|
| 1.1 Миграция `V4__loans.sql` | ✅ | `db/migration/V4__loans.sql` совпадает с design.md (FK, оба check, `uk_loan_open_copy`, 3 индекса); V1–V3 в diff отсутствуют |
| 1.2 Smoke-тест | ✅ | `ApplicationSmokeTest` зелёный (Flyway без pending, `ddl-auto: validate` с `Loan`) |
| 2.1 `Copy.checkOut/checkIn` | ✅ | `Copy.java` через `requireStatus`; `CopyTest` — успех и все неверные статусы для обоих |
| 2.2 `Loan`, `LoanStatus` | ✅ | `Loan.open` (dueDate = today + period), `returnAt`, `status()`, `isOverdue`; ленивые `@ManyToOne`, `@Version` |
| 2.3 Исключения | ✅ | 5 классов в `loans` с нужными кодами/статусами; новый конструктор `ReaderCategoryNotAllowedException(long, String)` |
| 2.4 Репозитории | ✅ | `findByIdForUpdate` с `PESSIMISTIC_WRITE` в Reader/Copy/LoanRepository; `countOpenByReader`; `findFiltered` с `join fetch` и отдельным `countQuery` |
| 2.5 Unit-тесты `Loan` | ✅ | `LoanTest`: 21/14 дней через границу месяца, статус, повторный возврат, overdue (вчера/сегодня/возвращён) |
| 3.1 `checkout` | ✅ | `LoanService.checkout`: порядок блокировок и проверок как в design.md; `uk_loan_open_copy` → `CopyNotAvailableException`. Замечание: сам маппинг нарушения индекса тестом не покрыт (в каталоге аналог есть — `duplicateInventoryNumberViolatesNamedConstraint`) |
| 3.2 `returnLoan`, `get`, `list` | ✅ | порядок loan → branch → open → copy lock → branch; сортировка `checkedOutAt desc, id desc`; `LoanResponse.from(loan, today)` |
| 3.3 DTO и контроллер | ✅ | `LoanDtos`, `LoanController` (201 + `Location`, size 1–100); `ApplicationSmokeTest.loanEndpointsAreDocumented` |
| 3.4 `LoanApiTest` | ✅ | 35 тестов, все не-конкурентные сценарии из `specs/loans/spec.md` (сверено по списку) |
| 3.5 `CopyApiTest` и truncate | ✅ | `copyOnLoanCannotBeMarkedLost`, `copiesOnLoanAreCountedButNotOnShelf`; `loan` добавлен во все 6 truncate-списков |
| 4.1 Два читателя, одна копия | ✅ | `LoanConcurrencyTest.twoReadersCheckOutOneCopy`, 10 раундов |
| 4.2 Лимит CHILD параллельно | ✅ | `oneReaderChecksOutSeveralCopiesAtTheLimit`: 201/409/409, 3 открытых, копии проигравших `ON_SHELF` |
| 4.3 Два возврата; checkout vs lost | ✅ | `twoReturnsOfOneLoan`, `checkoutAgainstMarkLost` |
| 5.1 Ручной прогон через curl | ✅👤 | подтверждено пользователем 2026-11-04. Прогон на порту 8091 (8080 занят другим процессом): 201 + Location, availability 1/0 → 1/1, 409 `COPY_NOT_AVAILABLE`, пути `/api/loans` в api-docs |
| 5.2 `./gradlew build -q` | ✅ | прогон выше |

Регрессии: нет.

Замечания (не блокируют):
- `checkedOutAt`/`returnedAt` в ответе POST берутся из `Clock` без усечения; на платформах с наносекундными часами ответ POST может отличаться от GET (в БД микросекунды). На macOS не проявляется.
- Нет теста, что нарушение `uk_loan_open_copy` распознаётся по имени (защитный путь из 3.1).

Рекомендация: change готов к `/opsx:archive`.
