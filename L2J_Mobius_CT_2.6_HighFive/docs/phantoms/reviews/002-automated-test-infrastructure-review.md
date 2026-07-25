# Independent review — 002-automated-test-infrastructure

## Decision

```text
Original Task 002 implementation: FIX REQUIRED
Revert: NOT_REQUIRED
Task 002A closure: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Task 003: NOT_STARTED
```

## Accepted Task 002 foundation

- JDK-only test runner и forked Ant suites.
- Изолированные test DB, dedicated user и local config.
- Основной production DB guard.
- Strict SQL executor, DB/scenario/performance suites.
- Production-compatible `DatabaseConfig`/`DatabaseFactory` seam.
- Scope, ordinary commit и push.

## Findings requiring Task 002A

### Foreign lock deletion

Contender не получал PID lock, но общий `finally` удалял lock-файл владельца.
Это создавало окно для третьего destructive provisioner.

### Stale schema false green

Schema manifest находился в очищаемом build-каталоге. Последующие запуски не
могли доказать соответствие test DB текущему repository SQL.

### Partial beforeAll cleanup

Runner вызывал `afterAll` только после полностью успешного `beforeAll`.
Частично созданный pool/resource мог остаться без явного cleanup.

### JDBC query authentication properties

Guard отклонял URI userinfo, но не имел strict query allowlist и принимал
`user`/`password` properties в JDBC URL.

### Report placeholders

Отчёт Task 002 не содержал уже известных immutable commit/push/final-verifier
фактов.

## Required closure

Task 002A ограничен ownership-safe cross-process lock, durable schema
fingerprint с pre-Hikari и DB-row comparison, lifecycle cleanup, strict JDBC
query allowlist, secret redaction и provenance документацией.

Revert исходного Task 002 не требуется. Task 003 остаётся заблокирован до
независимого решения по Task 002A.
