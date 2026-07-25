# TASK 002 — Автоматическая тестовая инфраструктура и изоляция test DB

## 1. Идентификатор

- **Task ID:** `002-automated-test-infrastructure`
- **Этап master plan:** `002. Автоматическая тестовая инфраструктура`
- **Целевая ветка:** `feature/phantom-world`
- **Принятый baseline:** `7aa24faf202567add0fa81561242d37453c6055f`
- **Git-корень:** `C:\Users\endim\L2J_Mobius\`
- **Единственный рабочий модуль:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Каталог запуска Codex:** рабочий модуль High Five
- **Production DB:** `l2jmobiush5`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Dedicated test user:** `l2j_phantom_test`
- **Deterministic seed:** `20260725001`
- **Модель Codex:** Sol
- **Effort:** Very High

## 2. Цель

Создать реальную автоматическую тестовую инфраструктуру Phantom World, пригодную для последующих Task 003–030:

1. отдельное test source/resources дерево;
2. интеграция с Apache Ant;
3. минимальный deterministic test runtime без новых внешних библиотек;
4. отдельная MariaDB `l2jmobiush5_phantom_test`;
5. отдельный пользователь БД с правами только на test DB;
6. explicit test config, не использующий production `Database.ini`;
7. fail-closed guard до загрузки JDBC driver/Hikari;
8. versioned test migration;
9. repeatable fixture create/use/cleanup;
10. отрицательные контрольные тесты;
11. Ant-цели:
    - `test`;
    - `verify`;
    - `phantom-db-test`;
    - `phantom-scenario-test`;
    - `phantom-performance-smoke`;
    - negative controls;
12. machine-readable и человекочитаемые результаты;
13. commit/push и полный отчёт.

Task 002 не реализует Phantom runtime, `PhantomPlayer`, packet sink, scheduler, AI, config `PhantomPlayers.ini` или materialization.

## 3. Обязательные источники

Перед изменениями полностью прочитать:

1. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
2. `Agents.md`;
3. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`;
4. `docs/phantoms/TASK_PACKAGE_STANDARD.md`;
5. `docs/phantoms/CODEX_REPORT_TEMPLATE.md`;
6. `docs/phantoms/reports/001-baseline-architecture-audit.md`;
7. `docs/phantoms/reports/001a-review-closure.md`;
8. `docs/phantoms/reviews/001-baseline-architecture-audit-review.md`;
9. `docs/phantoms/audits/001-baseline-architecture-audit/NEXT_TASK_GATES.md`;
10. `docs/phantoms/adr/0001-headless-player-integration-seam.md`;
11. этот `TASK.md`;
12. `CONTEXT.md`;
13. `ARCHITECTURE.md`;
14. `TEST_CASES.md`;
15. `ACCEPTANCE.md`.

Принятые gates:

```text
Task 001: ACCEPT
Task 001A: ACCEPT
Current baseline: 7aa24faf202567add0fa81561242d37453c6055f
ADR 0001: Proposed
Task 002 preparation: allowed
Task 003/004 implementation: forbidden in this task
```

## 4. Предварительный Git и code audit

Выполнить:

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git log -1 --format=fuller HEAD
git diff --name-status 7aa24faf202567add0fa81561242d37453c6055f...HEAD
```

Ожидается:

- local HEAD и remote branch равны baseline;
- кроме распакованного Task 002 package нет pre-existing изменений.

Если baseline изменился:

1. исследовать drift;
2. перечитать изменённые документы/код;
3. не продолжать при начатой Task 003, production Phantom-коде или конфликте;
4. при безопасном документальном drift записать новый SHA и адаптировать только доказательно.

Дополнительно аудировать:

- `build.xml`;
- `.gitignore`;
- `DatabaseConfig`;
- `DatabaseFactory`;
- `ConfigReader`;
- `DatabaseInstaller`;
- `dist/db_installer/sql/login`;
- `dist/db_installer/sql/game`;
- текущие JAR в `dist/libs`;
- существующие test каталоги и frameworks;
- доступный Ant/JDK;
- MariaDB server `127.0.0.1:3308`.

Зафиксировать:

- фактическое число login/game SQL scripts;
- наличие `DELIMITER`, stored routines, `SOURCE`, executable comments и иных конструкций;
- SHA-256 каждого SQL script либо stable aggregate manifest;
- наличие/отсутствие JUnit/TestNG;
- существующий DB driver;
- фактический current `DatabaseConfig`/`DatabaseFactory` lifecycle.

## 5. Заранее определённая архитектура

Архитектура зафиксирована в `ARCHITECTURE.md`. Не заменять её другим framework или build system без доказанного блокера.

### 5.1. Test runtime

В Task 002 не добавлять JUnit/TestNG/Maven/Gradle и не коммитить сторонние test JAR.

Причины:

- baseline не содержит test framework;
- Ant-проект должен оставаться offline/reproducible;
- сторонний binary не нужен для foundational harness.

Создать минимальный JDK-only test runtime:

```text
test/java/org/l2jmobius/tests/phantoms/
```

Он не должен превращаться в универсальный framework. Требуются только:

- explicit suite registration;
- stable ordinal test ordering;
- assertions;
- PASS/FAIL;
- deterministic seed;
- exit code;
- failure exception/type/message;
- plain text summary;
- machine-readable XML report;
- no reflection/annotation discovery;
- no network;
- no hidden skip.

Каждый Ant suite запускается в отдельном forked JVM.

### 5.2. Production-neutral DB config seam

Допустимы только два минимальных изменения production support-кода:

#### `DatabaseConfig`

- сделать default config path доступным как constant;
- `load()` сохраняет текущее поведение;
- добавить explicit `load(String configFile)`;
- default `load()` делегирует на existing `./config/Database.ini`;
- отсутствующий/пустой explicit path даёт явную ошибку;
- default server startup поведение не меняется.

#### `DatabaseFactory`

- `init()` сохраняет production semantics;
- добавить explicit fail-fast initialization from supplied config path;
- вынести общий pool setup без дублирования;
- test path не должен молча проглатывать initialization failure;
- `close()` обязан позволять повторный test-JVM lifecycle;
- добавить read-only `isInitialized()` только если он нужен tests;
- production pool name/default limits не менять без необходимости.

Запрещено добавлять Phantom policy в production `DatabaseFactory`. Exact test DB allowlist принадлежит test bootstrap/guard.

### 5.3. Test DB guard

До любого из действий:

- `Class.forName`;
- `DriverManager.getConnection`;
- Hikari config/pool construction;
- `DatabaseFactory.initFromConfig`;

test bootstrap обязан:

1. canonicalize test config path;
2. запретить production `dist/game/config/Database.ini`;
3. требовать config внутри module `.phantom-local`;
4. parse JDBC URL;
5. разрешать только:
   - `jdbc:mysql://`;
   - при фактической необходимости `jdbc:mariadb://`;
6. требовать local host `127.0.0.1` или `localhost`;
7. требовать ожидаемый port `3308`;
8. извлечь database path после percent-decoding;
9. case-sensitive exact match:
   `l2jmobiush5_phantom_test`;
10. reject:
    - `l2jmobiush5`;
    - empty;
    - unknown;
    - extra path segment;
    - encoded/trailing ambiguity;
    - credentials in URL;
    - non-local host;
11. требовать username:
    `l2j_phantom_test`;
12. не логировать password.

Guard возвращает immutable validated settings либо бросает отдельную guard exception.

### 5.4. Dedicated local config

Provisioning создаёт:

```text
.phantom-local/Database.test.ini
```

Файл:

- не коммитится;
- добавляется в `.gitignore` через module-specific path;
- создаётся atomic temp → move;
- содержит generated random password;
- имеет:
  - exact test DB URL;
  - dedicated user;
  - max pool 4;
  - connection fan-out test disabled;
  - backup disabled;
- никогда не копируется в `dist/game/config`;
- не читается обычным GameServer startup.

### 5.5. Test DB provisioning

Создать explicit provisioning flow:

```text
tools/phantoms/prepare-test-db.ps1
  -> compile test tooling
  -> fork PhantomTestDatabaseProvisioner
  -> local MariaDB only
```

Admin credentials:

- поступают только из environment:
  - `PHANTOM_DB_ADMIN_URL`;
  - `PHANTOM_DB_ADMIN_USER`;
  - `PHANTOM_DB_ADMIN_PASSWORD`;
- не коммитятся;
- не печатаются;
- не записываются в report;
- после provisioning удаляются из shell environment, насколько это возможно.

`PHANTOM_DB_ADMIN_URL` обязан указывать на local server без production schema.

Provisioner:

1. fail-closed проверяет constants;
2. подключается только к server-level URL;
3. удаляет только exact test DB/user;
4. создаёт exact test DB;
5. создаёт/пересоздаёт dedicated random-password user;
6. выдаёт права только на `l2jmobiush5_phantom_test.*`;
7. устанавливает existing login и game schema scripts в stable order;
8. применяет versioned test migrations;
9. проверяет core tables;
10. проверяет grants;
11. пишет local test config;
12. при failure:
    - удаляет partial test DB;
    - удаляет partial dedicated user;
    - удаляет temp/local config;
    - не касается production DB.

Production DB `l2jmobiush5` нельзя:

- `USE`;
- читать;
- изменять;
- удалять;
- использовать как source fixture;
- клонировать через live SELECT.

Schema строится только из versioned repository SQL.

### 5.6. SQL execution

Не использовать interactive `DatabaseInstaller` как test runner.

Создать strict test-only SQL executor:

- stable case-insensitive filename order;
- UTF-8;
- comments/quotes handled according to actually observed scripts;
- fail on first SQL error;
- file + statement index in error;
- password/data secrets excluded;
- no silent continue;
- unsupported syntax fails before partial success;
- schema install manifest/count/hash recorded.

Если existing SQL uses unsupported `DELIMITER`, routines или complex constructs:

- либо реализовать ограниченную tested support;
- либо `BLOCKED`;
- нельзя silently skip.

### 5.7. Fixture ownership

Task 002 test migration:

```text
test/resources/phantoms/db/migrations/
  001_create_phantom_test_harness.sql
```

Она создаёт только harness-owned table, например:

```text
phantom_test_harness
```

Обязательные поля:

- deterministic fixture key;
- seed;
- value/checksum;
- created marker.

Migration:

- versioned;
- idempotent;
- test DB only;
- не попадает в production DB installer;
- stable hash.

DB integration suite:

- проверяет current database;
- проверяет current/dedicated user;
- проверяет grants;
- проверяет core schema tables;
- выполняет transaction insert/select/rollback;
- проверяет rollback;
- выполняет committed fixture;
- cleanup вызывается дважды;
- проверяет zero owned residue;
- всегда закрывает pool в `finally`.

Не создавать fake game character в Task 002. Player fixture относится к Task 004.

### 5.8. Determinism

Canonical seed:

```text
20260725001
```

Каждый test failure печатает seed.

Scenario harness smoke:

- `SplittableRandom(seed)`;
- 64 значения `nextInt(1000)`;
- SHA-256 big-endian int stream;
- expected:

```text
A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45
```

Это infrastructure scenario, не имитация gameplay.

Secret generation использует `SecureRandom`, не deterministic seed.

### 5.9. Negative controls

Обязательны два разных контроля.

#### Runner negative control

- отдельный suite намеренно FAIL;
- JVM exit code должен быть `1`;
- Ant wrapper target считается PASS только если получил exact expected failure;
- пустой/always-green runner не проходит.

#### Production DB guard negative control

- temp config указывает на `l2jmobiush5`;
- driver = test `SentinelJdbcDriver`;
- static initializer/connect создаёт marker только если driver был затронут;
- bootstrap обязан завершиться guard rejection с отдельным exit code до driver/Hikari;
- marker отсутствует;
- connection attempts = 0.

### 5.10. Performance smoke

Task 002 performance smoke проверяет только harness:

- deterministic bounded workload;
- не менее 200 000 простых операций;
- fixed seed;
- rolling checksum;
- no unbounded collection;
- generous timeout не менее 30 секунд;
- elapsed публикуется как measurement;
- это не Phantom runtime benchmark.

## 6. Ant contract

Изменить `build.xml` минимально и в текущем стиле.

Добавить properties:

```text
test.src
test.resources
build.test
build.test.bin
build.test.resources
build.test.reports
phantom.test.seed
phantom.test.config
```

Production `compile`/`jar` semantics сохраняются.

Обязательные targets:

```text
init-test
compile-tests
test
prepare-phantom-test-db
phantom-db-guard-negative-control
phantom-negative-control
phantom-db-test
phantom-scenario-test
phantom-performance-smoke
phantom-static-verify
verify
```

Contract:

- `compile-tests` зависит от production `compile`;
- tests компилируются отдельно от production bin;
- production JAR не содержит test classes/resources;
- `test` — DB-free unit suite;
- `prepare-phantom-test-db` — explicit destructive recreate только test DB;
- `phantom-db-test` — использует `.phantom-local/Database.test.ini`;
- scenario/performance — DB-free;
- `phantom-static-verify` запускает `verify-task-002.ps1`;
- `verify` зависит минимум от:
  - `jar`;
  - `test`;
  - двух negative controls;
  - `phantom-db-test`;
  - `phantom-scenario-test`;
  - `phantom-performance-smoke`;
  - static verifier;
- отсутствующий test config не вызывает SKIP: `verify` FAIL;
- все `<java>` tests выполняются `fork="true"`;
- result reports идут в `../build/phantom-test/reports`;
- test JVM не стартует LoginServer/GameServer.

## 7. Ожидаемые файлы

Предпочтительный exact production/support scope:

```text
.gitignore
L2J_Mobius_CT_2.6_HighFive/build.xml
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/commons/config/DatabaseConfig.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/commons/database/DatabaseFactory.java
```

Test runtime:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomAssertions.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestContext.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestResult.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomHarnessUnitSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomScenarioSmokeSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomPerformanceSmokeSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomNegativeControlSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/SentinelJdbcDriver.java
test/java/org/l2jmobius/tests/phantoms/StrictSqlScriptRunner.java
```

Resources/tooling:

```text
test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql
test/resources/phantoms/scenarios/harness-smoke.properties
tools/phantoms/prepare-test-db.ps1
tools/phantoms/verify-task-002.ps1
docs/phantoms/reports/002-automated-test-infrastructure.md
docs/phantoms/tasks/002-automated-test-infrastructure/**
```

Имена могут быть скорректированы только для соблюдения Java responsibility/file-size, но architecture, package boundary и behavior нельзя менять. Все deviations объяснить.

## 8. Scope

Разрешено изменять/создавать только:

```text
.gitignore
L2J_Mobius_CT_2.6_HighFive/build.xml
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/commons/config/DatabaseConfig.java
L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/commons/database/DatabaseFactory.java
L2J_Mobius_CT_2.6_HighFive/test/**
L2J_Mobius_CT_2.6_HighFive/tools/phantoms/prepare-test-db.ps1
L2J_Mobius_CT_2.6_HighFive/tools/phantoms/verify-task-002.ps1
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/002-automated-test-infrastructure/**
L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/002-automated-test-infrastructure.md
```

## 9. Out of scope

Запрещено:

- другие хроники;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `Agents.md`;
- ADR 0001;
- предыдущие reports/audits/reviews/verifiers;
- production `GameServer`, `Player`, `GameClient`, network packets;
- новый production package `gameserver.phantoms`;
- `PhantomPlayers.ini`;
- runtime Phantom feature flags;
- startup/shutdown Phantom managers;
- AI/scheduler/metrics;
- Player materialization;
- DB changes в `l2jmobiush5`;
- committed credentials;
- committed `.phantom-local`;
- изменения existing DB installer SQL;
- Maven/Gradle;
- JUnit/TestNG или test JAR;
- CI workflow;
- server/client manual runtime test;
- geodata;
- dependency update;
- mass formatting;
- amend/rebase/force push.

## 10. Конкурентность и lifecycle

Обязательные свойства:

- отдельный forked JVM на suite;
- no shared static DB config между suites;
- pool close в `finally`;
- provisioner single-process lock:
  `.phantom-local/test-db.lock`;
- stale lock обрабатывается безопасно;
- atomic local config write;
- no lingering non-daemon test threads;
- no concurrent provisioning;
- cleanup idempotent;
- negative control marker создаётся только вне repo build/temp;
- test runner не создаёт per-test executor;
- stable suite/test order.

## 11. DB и безопасность

### Hard guards

Любая destructive DB operation допустима только после exact checks:

```text
database == l2jmobiush5_phantom_test
database != l2jmobiush5
host local
port == 3308
user target == l2j_phantom_test
```

Identifiers hardcoded/allowlisted, не принимаются произвольными CLI args.

### Dedicated grants

Dedicated user:

- не имеет global privileges;
- не имеет privileges на `l2jmobiush5.*`;
- имеет только test DB privileges;
- password random и local-only.

### Reports/logging

Запрещено выводить:

- admin password;
- test password;
- full config;
- JDBC URL с credentials;
- stack traces, содержащие credentials.

URL можно печатать только sanitized host/port/database.

## 12. Автоматические тесты

Полный matrix — `TEST_CASES.md`.

Минимальные gates:

### Unit

- JDBC URL parser positive/negative;
- exact case-sensitive DB;
- encoded/trailing/multi-path rejection;
- non-local rejection;
- production config path rejection;
- username rejection;
- seed repeatability;
- scenario checksum;
- SQL splitter observed syntax;
- unsupported SQL rejection;
- XML report escaping;
- secret redaction.

### Negative

- runner expected FAIL;
- production DB rejected before sentinel driver load/connect.

### DB integration

- schema provision;
- core tables exist;
- `SELECT DATABASE()` exact;
- dedicated current user;
- grants no production/global access;
- transaction rollback;
- committed fixture;
- double cleanup;
- zero residue;
- pool closed.

### Scenario

- expected checksum exact.

### Performance

- bounded workload;
- timeout;
- checksum;
- no queue/collection growth.

## 13. Static verifier Task 002

`tools/phantoms/verify-task-002.ps1` обязан:

- работать из любого repo subdirectory;
- default base `7aa24faf...`;
- проверять branch/one-task commit shape;
- required files;
- exact/allowed scope;
- no other chronicles;
- no Task 003;
- no forbidden production files;
- no credentials;
- `.phantom-local` ignored and untracked;
- build targets;
- test source separate from production;
- production JAR exclusion by directory structure;
- explicit DB config overload tokens;
- guard constants;
- negative controls;
- seed/checksum;
- report headings;
- UTF-8;
- mojibake;
- escaped Cyrillic;
- no binary/test JAR;
- stable ordinal output;
- exit `0` only all PASS;
- no DB/network/write operations.

## 14. Команды выполнения

### Environment

```bat
java -version
ant -version
ant -p
```

Если `ant` отсутствует в PATH:

- использовать уже доступный official Ant 1.10.15 вне repository;
- не коммитить distribution;
- записать deviation/path без binary.

### DB provisioning

В текущем PowerShell process, значения не печатать:

```powershell
$env:PHANTOM_DB_ADMIN_URL = 'jdbc:mysql://127.0.0.1:3308/'
$env:PHANTOM_DB_ADMIN_USER = '<local-admin-user>'
$env:PHANTOM_DB_ADMIN_PASSWORD = '<local-admin-password>'
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\prepare-test-db.ps1
Remove-Item Env:PHANTOM_DB_ADMIN_URL,Env:PHANTOM_DB_ADMIN_USER,Env:PHANTOM_DB_ADMIN_PASSWORD
```

Report записывает только:

```text
Admin credentials supplied through environment: yes
Credentials recorded: no
```

### Required tests

```bat
ant test
ant phantom-negative-control
ant phantom-db-guard-negative-control
ant phantom-db-test
ant phantom-scenario-test
ant phantom-performance-smoke
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002.ps1
git diff --check
git status --short --branch
```

`verify` и static verifier выполнить до commit.

После commit:

```bat
git rev-parse HEAD
git show --stat --oneline --decorate HEAD
git diff --check 7aa24faf202567add0fa81561242d37453c6055f...HEAD
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-002.ps1
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
git status --short --branch
```

Два final static verifier outputs сохранить вне repo, сравнить byte-for-byte/SHA-256.

## 15. Критерии приёмки

Полный checklist — `ACCEPTANCE.md`.

Критические gates:

1. exact baseline/branch;
2. production startup behavior не изменён;
3. no external test dependency;
4. tests не входят в production JAR;
5. guard выполняется до driver/Hikari;
6. production DB negative control proves zero driver/connect attempt;
7. separate DB/user/config;
8. full repository schema installed from versioned scripts;
9. test migration applied;
10. fixture rollback/commit/double-cleanup/zero residue;
11. ordinary tests do not use production config;
12. deterministic scenario checksum;
13. negative runner control;
14. all mandatory Ant targets;
15. `ant verify` PASS;
16. `ant jar` PASS;
17. static verifier PASS pre/final twice;
18. no credentials/binaries;
19. report;
20. ordinary commit/push;
21. Task 003 not started.

## 16. Отчёт

Создать:

```text
docs/phantoms/reports/002-automated-test-infrastructure.md
```

Обязательные дополнительные разделы:

- Starting baseline;
- Test runtime architecture;
- Production compatibility;
- DB guard ordering proof;
- Test DB provisioning;
- Schema script inventory/hashes;
- Dedicated user/grants;
- Local config;
- Negative controls;
- Fixture lifecycle;
- Ant targets;
- Suite/test counts;
- Determinism;
- Scenario checksum;
- Performance smoke measurement;
- Secrets redaction;
- Scope;
- Commands/exit codes;
- Pre/final verifier;
- Branch/parent/commit/push;
- Manual gate:
  `PENDING_INDEPENDENT_REVIEW`;
- Task 003:
  `NOT_STARTED`.

Не писать passwords/credential values.

## 17. Commit и push

Commit message:

```text
test(phantoms): add isolated automated test infrastructure
```

Один обычный commit поверх accepted baseline.

Запрещено:

- amend;
- rebase;
- reset history;
- force push.

В staging — только exact Task 002 scope.

## 18. Поведение при блокировке

При `BLOCKED`:

1. не касаться production DB;
2. удалить partial test DB/user/local config, если создано;
3. не оставлять сломанный `build.xml` или production support code;
4. сохранить безопасные unit tests, verifier, audit/report;
5. не начинать Task 003;
6. создать обычный commit безопасной части;
7. push;
8. указать точный blocker.

BLOCKED, если:

- baseline drift конфликтует;
- MariaDB недоступна;
- admin grants недостаточны;
- existing schema scripts нельзя выполнить строго;
- test DB guard не может доказать pre-connection rejection;
- production startup compatibility нарушена;
- `ant verify` не GREEN;
- credentials попали в Git/output;
- dedicated user имеет production/global privileges;
- push rejected.

## 19. Финальное сообщение Codex

```text
Статус:
Что реализовано:
Baseline:
Production compatibility:
Test DB:
Dedicated test user:
Schema scripts:
Ant targets:
Unit tests:
Negative controls:
DB integration:
Scenario checksum:
Performance smoke:
ant verify:
ant jar:
Static verifier pre-commit:
Static verifier final run 1:
Static verifier final run 2:
Final outputs identical:
DB production access/mutation:
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
