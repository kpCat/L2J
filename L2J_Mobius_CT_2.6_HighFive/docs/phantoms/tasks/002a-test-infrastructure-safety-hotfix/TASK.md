# TASK 002A — Safety, freshness и lifecycle hotfix тестовой инфраструктуры

## 1. Идентификатор

- **Task ID:** `002a-test-infrastructure-safety-hotfix`
- **Тип:** обязательный hotfix после независимого ревью Task 002
- **Целевая ветка:** `feature/phantom-world`
- **Исходный baseline Task 002A:** `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`
- **Parent Task 002:** `7aa24faf202567add0fa81561242d37453c6055f`
- **Git-корень:** `C:\Users\endim\L2J_Mobius\`
- **Рабочий модуль:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Production DB:** `l2jmobiush5`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Dedicated user:** `l2j_phantom_test`
- **Deterministic seed:** `20260725001`
- **Модель Codex:** Sol
- **Effort:** Very High

## 2. Статус предыдущей задачи

Независимое ревью Task 002:

```text
Task 002: FIX REQUIRED
Task 003: BLOCKED
Revert: не требуется
```

Приняты без замечаний:

- JDK-only test runner;
- Ant integration;
- отдельная test DB;
- dedicated test user;
- production DB guard в основном path;
- strict SQL executor;
- DB/scenario/performance suites;
- production compatibility `DatabaseConfig`/`DatabaseFactory`;
- commit/push/scope.

Обязательные P1/P2 findings перечислены в `REVIEW_FINDINGS.md`.

## 3. Цель

Исправить только обнаруженные safety/freshness/lifecycle-дефекты Task 002:

1. process, который не получил provisioning lock, не имеет права удалять или изменять чужой lock;
2. schema test DB обязана соответствовать текущим repository SQL/migrations;
3. freshness mismatch отклоняется до JDBC driver/Hikari;
4. `afterAll` выполняется после любой попытки `beforeAll`, включая partial failure;
5. JDBC query не может содержать authentication properties;
6. secret sanitizer закрывает JDBC query secrets и `IDENTIFIED BY`;
7. отчёт Task 002 получает фактические post-commit результаты;
8. Task 003 не начинается.

После hotfix:

```text
Manual gate: PENDING_INDEPENDENT_REVIEW
Task 003: NOT_STARTED
```

## 4. Обязательные документы

До изменений полностью прочитать:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `Agents.md`;
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
4. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
5. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`;
6. `docs/phantoms/tasks/002-automated-test-infrastructure/TASK.md`;
7. `docs/phantoms/reports/002-automated-test-infrastructure.md`;
8. `tools/phantoms/verify-task-002.ps1`;
9. все production/test/tooling файлы commit `36e5411e...`;
10. этот `TASK.md`;
11. `CONTEXT.md`;
12. `REVIEW_FINDINGS.md`;
13. `ARCHITECTURE.md`;
14. `TEST_CASES.md`;
15. `ACCEPTANCE.md`.

## 5. Предварительный Git/code audit

Выполнить:

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 36e5411e01e8e73f8a0fd4d9460e327c28a6798b
git diff --name-status 7aa24faf202567add0fa81561242d37453c6055f..36e5411e01e8e73f8a0fd4d9460e327c28a6798b
```

Нормальная стартовая точка:

```text
HEAD == origin/feature/phantom-world == 36e5411e...
```

Распакованный task package является ожидаемым untracked scope.

Независимые `docs/agent-tasks/**`:

- не читать;
- не менять;
- не удалять;
- не stage/commit;
- точно перечислить в отчёте как excluded pre-existing work.

Если появились Task 003 или несвязанные production-изменения — `BLOCKED`.

## 6. Архитектурное решение

Точный контракт находится в `ARCHITECTURE.md`. Codex не должен изобретать альтернативную инфраструктуру.

### 6.1. Provisioning lock

Текущую PID-file схему заменить ownership-safe lock abstraction:

```text
PhantomProvisioningLock implements AutoCloseable
```

Предпочтительная реализация:

- `FileChannel`;
- `FileLock.tryLock()`;
- `CREATE`, `READ`, `WRITE`;
- один persistent ignored lock file;
- после успешного lock acquisition файл содержит owner token;
- stale file без активного OS lock не блокирует новый process;
- process, не получивший OS lock:
  - не удаляет файл;
  - не перезаписывает token;
  - не вызывает JDBC;
  - не вызывает DB cleanup;
- `close()` освобождает только собственный `FileLock`/channel;
- lock file может оставаться; его удаление не является обязательным.

Не использовать схему «проверил PID → удалил → создал»: она имеет TOCTOU.

Provisioner:

```java
try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))
{
    // driver, admin connection, destructive test DB flow
}
```

Никакого безусловного `deleteIfExists(lockFile)` в общем `finally`.

### 6.2. Schema freshness contract

Создать test-only schema manifest abstraction:

```text
PhantomTestSchemaManifest
```

Canonical local file:

```text
.phantom-local/schema-manifest.properties
```

Manifest:

```text
schemaVersion=1
scriptCount=<N>
statementCount=<N>
aggregateSha256=<64 uppercase hex>
```

Требования:

- deterministic;
- no timestamps;
- no credentials;
- atomic temp → move;
- переживает `init-test`;
- ignored/untracked;
- строится из current stable inventory:
  - login SQL;
  - game SQL;
  - test migrations.

Provisioner:

1. выполняет SQL inventory/preflight до JDBC;
2. получает manifest snapshot;
3. provision-ит schema;
4. применяет migrations и повторный idempotency pass;
5. записывает snapshot в test DB metadata table;
6. пишет local manifest;
7. пишет local DB config;
8. только после полного успеха завершает flow.

Добавить migration:

```text
test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql
```

Она создаёт idempotent metadata table.

DB metadata хранит минимум:

```text
manifest_key
schema_version
script_count
statement_count
aggregate_sha256
```

Одна canonical row, например key `repository-schema`.

### 6.3. Pre-Hikari bootstrap

Создать единый test bootstrap:

```text
PhantomTestDatabaseBootstrap
```

Порядок:

1. `PhantomTestDatabaseGuard.validate`;
2. current repository schema inventory/hash;
3. read `.phantom-local/schema-manifest.properties`;
4. exact compare schemaVersion/counts/hash;
5. только затем `DatabaseFactory.initFromConfig`.

DB integration suite обязана использовать именно этот bootstrap.

Если manifest:

- отсутствует;
- malformed;
- stale;
- hash/count отличается;

результат:

```text
PhantomTestConfigurationException
exit code 2
driver loads 0
connection attempts 0
```

После подключения DB suite дополнительно сравнивает current/local manifest с canonical row в test DB.

### 6.4. Partial `beforeAll` cleanup

Изменить runner lifecycle:

- `afterAll` вызывается после любой начавшейся попытки `beforeAll`;
- не только после успешного `beforeAll`;
- cleanup exception добавляется как отдельный failed result;
- original failure не теряется;
- exit code остаётся максимальным по contract;
- `afterAll` suite implementation null-safe/idempotent.

Недопустимо вызывать `afterAll`, если suite registration не завершилась и lifecycle не начинался.

### 6.5. JDBC query allowlist

В `PhantomTestDatabaseGuard.validateJdbcUrl`:

- parse raw query;
- percent-decode keys/values;
- reject duplicate keys case-insensitive;
- reject empty/malformed pair;
- использовать strict allowlist только для generated test URL.

Разрешённые canonical keys:

```text
useSSL
allowPublicKeyRetrieval
serverTimezone
characterEncoding
```

Разрешить отсутствие query.

Отклонить любые другие keys, включая:

```text
user
password
password1
password2
password3
```

Отклонение должно учитывать:

- mixed case;
- percent-encoded key;
- duplicated key;
- encoded separators;
- blank key/value.

### 6.6. Secret redaction

`PhantomTestLauncher.sanitize` должен маскировать:

- `Password=...`;
- JDBC userinfo;
- JDBC query:
  - `user`;
  - `password`;
  - `password1`;
  - `password2`;
  - `password3`;
- SQL:
  - `IDENTIFIED BY '...'`;
  - `IDENTIFIED BY "..."`.

Не печатать generated password даже если JDBC/SQL exception включает statement text.

## 7. Требуемые regression controls

### 7.1. Cross-process provisioning lock control

Добавить DB-free Java control, запускающий child JVMs:

1. holder process получает lock и сигнализирует ready;
2. contender пытается получить тот же lock;
3. contender получает exact config/busy exit;
4. contender:
   - не удаляет lock file;
   - не изменяет owner token;
   - не создаёт JDBC/destructive marker;
5. holder освобождает lock;
6. новый contender получает lock успешно;
7. все processes имеют bounded timeout;
8. cleanup идемпотентен.

Добавить Ant target:

```text
phantom-provisioning-lock-control
```

### 7.2. Schema freshness negative control

Создать valid test config с `SentinelJdbcDriver`, но stale manifest.

Использовать тот же `PhantomTestDatabaseBootstrap`, что DB suite.

Ожидается:

```text
exit 2
sentinel marker absent
driverLoads=0
connectionAttempts=0
```

Добавить target:

```text
phantom-schema-freshness-negative-control
```

### 7.3. Partial beforeAll cleanup control

Добавить test suite:

- `beforeAll` создаёт marker/resource;
- затем бросает configuration/internal failure;
- `afterAll` удаляет marker;
- runner возвращает expected non-zero exit;
- marker отсутствует;
- cleanup result не скрывает original failure.

Добавить target:

```text
phantom-lifecycle-negative-control
```

Либо unit test может вызывать runner in-process, но отдельный Ant target обязателен для видимого gate.

### 7.4. JDBC query tests

Добавить минимум:

- valid no query;
- valid generated allowlist query;
- `?user=root`;
- `?password=secret`;
- `?Password=secret`;
- `?%70assword=secret`;
- `?password1=...`;
- `?password2=...`;
- `?password3=...`;
- duplicate `useSSL`;
- unknown key;
- blank key;
- blank value;
- encoded separator/query ambiguity.

### 7.5. Manifest tests

- deterministic same inventory;
- aggregate changes on one script change;
- read/write roundtrip;
- malformed version/count/hash rejected;
- missing manifest rejected;
- stale manifest rejected before sentinel;
- DB metadata exact compare;
- `init-test` не удаляет local manifest;
- repeated provisioning updates manifest consistently.

## 8. Ant contract

Обновить `build.xml` минимально.

Добавить targets:

```text
phantom-provisioning-lock-control
phantom-schema-freshness-negative-control
phantom-lifecycle-negative-control
```

Обновить `verify`:

```text
jar
test
phantom-negative-control
phantom-db-guard-negative-control
phantom-provisioning-lock-control
phantom-schema-freshness-negative-control
phantom-lifecycle-negative-control
phantom-db-test
phantom-scenario-test
phantom-performance-smoke
phantom-static-verify
phantom-static-verify-002a
```

Можно оставить historical `phantom-static-verify`, но Task 002A получает новый:

```text
tools/phantoms/verify-task-002a.ps1
```

и target:

```text
phantom-static-verify-002a
```

Все Java invocations forked.

Missing/stale manifest — FAIL, не SKIP.

## 9. Scope

Разрешено изменять/создавать только:

```text
L2J_Mobius_CT_2.6_HighFive/build.xml

L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/**
L2J_Mobius_CT_2.6_HighFive/test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql

L2J_Mobius_CT_2.6_HighFive/tools/phantoms/verify-task-002a.ps1

L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/002a-test-infrastructure-safety-hotfix/**
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/002-automated-test-infrastructure.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reviews/002-automated-test-infrastructure-review.md
```

Предпочтительные новые Java files:

```text
PhantomProvisioningLock.java
PhantomProvisioningLockControl.java
PhantomTestSchemaManifest.java
PhantomTestDatabaseBootstrap.java
PhantomLifecycleFailureControlSuite.java
```

Допустим отдельный маленький helper для control process, если responsibilities иначе смешиваются.

## 10. Жёсткий out of scope

Запрещено менять:

- `.gitignore`;
- production `java/**`;
- `DatabaseConfig`;
- `DatabaseFactory`;
- master plan;
- `Agents.md`;
- ADR;
- previous audits;
- `verify-task-002.ps1`;
- `prepare-test-db.ps1`;
- existing SQL login/game;
- migration 001;
- production config/data;
- `Player`, `GameClient`, `GameServer`;
- Task 003 files;
- test DB/name/user contract;
- dependencies/JAR;
- Maven/Gradle/JUnit;
- production DB;
- other chronicles.

Запрещено:

- amend;
- rebase;
- force push;
- массовое форматирование;
- изменение balance/rates;
- ручной runtime GameServer test.

## 11. Concurrency/lifecycle

Обязательные invariants:

1. не владеющий lock process никогда не удаляет lock;
2. OS lock освобождается при normal close и process death;
3. lock contention не запускает JDBC;
4. schema manifest write atomic;
5. local config и manifest принадлежат одному successful provisioning result;
6. beforeAll partial acquisition всегда имеет afterAll attempt;
7. cleanup безопасен при null/uninitialized pool;
8. child controls имеют timeout и kill-on-timeout;
9. нет lingering child process;
10. no executor/thread-per-test.

## 12. DB safety

Production DB:

```text
connection/read/mutation = forbidden
```

Test DB может быть destructive recreated только explicit provisioning target.

Hotfix verification не требует повторно читать production DB.

Provisioner после hotfix:

- повторно recreate test DB;
- проверяет schema manifest DB row;
- dedicated grants остаются test-only;
- local config/password остаются ignored/untracked;
- manifest не содержит secret.

## 13. Static verifier Task 002A

`verify-task-002a.ps1`:

- base `36e5411e...`;
- one-commit shape;
- exact allowed scope;
- high-five only;
- no production Java;
- no Task 003;
- no old verifier modification;
- no config/SQL outside migration 002;
- required new files;
- lock uses `FileChannel`/`FileLock`/`tryLock`;
- provisioner uses try-with-resources lock;
- no unconditional lock delete;
- manifest local path;
- atomic manifest write;
- bootstrap ordering tokens;
- DB suite uses bootstrap before factory;
- DB metadata test;
- query allowlist/auth rejection tests;
- lifecycle control;
- new Ant targets and verify dependencies;
- Task 002 report final provenance;
- Task 002A report headings;
- review record;
- UTF-8/mojibake/escaped Cyrillic;
- no credentials/binaries;
- deterministic sorted output;
- no DB/network/write.

## 14. Обязательные команды

### DB-free targeted controls

```bat
ant test
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-provisioning-lock-control
ant phantom-schema-freshness-negative-control
ant phantom-lifecycle-negative-control
```

### Re-provision test DB

Использовать environment-only admin credentials:

```powershell
$env:PHANTOM_DB_ADMIN_URL = 'jdbc:mysql://127.0.0.1:3308/'
$env:PHANTOM_DB_ADMIN_USER = '<local-admin-user>'
$env:PHANTOM_DB_ADMIN_PASSWORD = '<local-admin-password>'
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\prepare-test-db.ps1
Remove-Item Env:PHANTOM_DB_ADMIN_URL,Env:PHANTOM_DB_ADMIN_USER,Env:PHANTOM_DB_ADMIN_PASSWORD
```

`prepare-test-db.ps1` изменять запрещено.

### Full gates

```bat
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002a.ps1
git diff --check
```

`ant verify` должен пройти после fresh re-provision.

### Post-commit

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 36e5411e01e8e73f8a0fd4d9460e327c28a6798b...HEAD
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002a.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002a.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Два final verifier outputs сравнить byte-for-byte/SHA-256 вне repo.

## 15. Обновление отчёта Task 002

`docs/phantoms/reports/002-automated-test-infrastructure.md`:

Добавить:

- original Task 002 commit:
  `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`;
- parent:
  `7aa24faf...`;
- push successful;
- final verifier run 1:
  `70/70`;
- final verifier run 2:
  `70/70`;
- outputs identical:
  `863B235A99D686D99F8B1DA98762DCBD3A683D0E729F66CB88590954A609CE0C`;
- independent review:
  `FIX REQUIRED`;
- P1/P2 findings;
- Task 002A closure reference;
- Task 003 remains blocked pending review.

Удалить post-commit placeholders `pending`.

Не приписывать Task 002A tests исходному commit Task 002.

## 16. Review record

Создать:

```text
docs/phantoms/reviews/002-automated-test-infrastructure-review.md
```

Зафиксировать:

```text
Original Task 002 implementation: FIX REQUIRED
Revert: NOT_REQUIRED
Task 002A closure: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Task 003: NOT_STARTED
```

Findings:

- foreign lock deletion;
- stale schema false green;
- partial beforeAll cleanup;
- JDBC query auth properties;
- report placeholders.

## 17. Отчёт Task 002A

Создать:

```text
docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md
```

Обязательные разделы:

- Status;
- Starting baseline;
- Independent findings addressed;
- Lock ownership design;
- Cross-process lock evidence;
- Schema freshness design;
- Pre-Hikari stale-manifest evidence;
- DB metadata evidence;
- Runner lifecycle fix;
- JDBC query allowlist;
- Secret redaction;
- Ant targets;
- Test counts;
- Re-provisioning;
- Production DB safety;
- Scope;
- Commands/exit codes;
- Pre/final verifier;
- Branch/parent/commit/push;
- Manual gate:
  `PENDING_INDEPENDENT_REVIEW`;
- Task 003:
  `NOT_STARTED`.

## 18. Acceptance

Полный checklist — `ACCEPTANCE.md`.

Критические gates:

1. foreign active lock remains intact;
2. no-owner process does not enter JDBC/destructive path;
3. lock reusable after owner exit;
4. current SQL fingerprint persisted outside build;
5. stale/missing manifest rejects before sentinel;
6. DB row fingerprint exact;
7. `afterAll` executes on partial `beforeAll`;
8. auth query properties rejected;
9. sanitizer covers query/IDENTIFIED BY;
10. fresh provisioning and DB tests pass;
11. `ant verify` pass;
12. `ant jar` pass;
13. static verifier pre/final twice;
14. production DB untouched;
15. ordinary commit/push;
16. Task 003 not started.

## 19. Commit/push

Commit:

```text
test(phantoms): harden test infrastructure safety
```

Один обычный commit поверх `36e5411e...`.

Запрещены amend/rebase/force push.

## 20. BLOCKED behavior

Если hotfix не может доказать lock/freshness ordering:

- Task 003 не начинать;
- production DB не трогать;
- partial test DB cleanup only exact test target;
- оставить safe tests/audit/report;
- ordinary commit/push status `BLOCKED`;
- указать точный blocker.

## 21. Финальное сообщение Codex

```text
Статус:
Исправленные findings:
Baseline:
Lock control:
Schema freshness:
Stale-manifest sentinel:
Lifecycle cleanup:
JDBC query controls:
Secret redaction:
Unit/negative controls:
Re-provision:
DB integration:
ant verify:
ant jar:
Static verifier pre:
Static verifier final 1:
Static verifier final 2:
Outputs identical:
Production DB access/mutation:
Credentials committed/logged:
Commit:
Parent:
Branch:
Push:
Remote ref:
Отчёт:
Manual gate:
Task 003:
Ограничения/блокеры:
```
