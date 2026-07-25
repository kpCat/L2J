# CONTEXT — Task 002

## 1. Принятый baseline

```text
Branch: feature/phantom-world
Commit: 7aa24faf202567add0fa81561242d37453c6055f
Task 001: ACCEPT
Task 001A: ACCEPT
Gate verdict: FEASIBLE_WITH_SEAM
ADR 0001: Proposed
Task 002: NOT_STARTED
```

## 2. Проверенное состояние build

`build.xml`:

- `src = java`;
- production compile идёт в `../build/bin`;
- classpath — JAR из `dist/libs`;
- JDK source/target 25;
- `jar` зависит от `compile`;
- `jar` создаёт и копирует GameServer/LoginServer JAR;
- test targets отсутствуют.

Нужно сохранить production target behavior.

## 3. Проверенное состояние DB config

`DatabaseConfig`:

- hardcoded `./config/Database.ini`;
- static mutable fields;
- только `load()` без explicit path.

`DatabaseFactory`:

- `init()` вызывает `DatabaseConfig.load()`;
- создаёт Hikari pool;
- production init catches/logs failure;
- `getConnection()` throws runtime on failure;
- `close()` закрывает pool;
- explicit test config path отсутствует.

Task 002 разрешает только минимальный generic explicit-config seam.

## 4. Production Database.ini

Текущий production URL указывает на:

```text
127.0.0.1:3308/l2jmobiush5
```

Этот файл и schema нельзя использовать test runtime.

Admin credentials могут использоваться только как ephemeral environment input для provisioning local test DB. Обычные tests используют generated dedicated credentials.

## 5. Database installer

Существующий `DatabaseInstaller`:

- interactive GUI/console;
- создаёт database;
- читает `sql/login` и `sql/game`;
- сортирует SQL files;
- имеет простой line/semicolon parser;
- при отдельных SQL errors печатает ошибку и продолжает.

Поэтому он непригоден как строгий automated test provisioner. Task 002 создаёт test-only strict executor и не изменяет DatabaseInstaller/existing SQL.

## 6. Почему JDK-only test runtime

Baseline audit не обнаружил JUnit/TestNG или test source tree.

Task 002 не должна:

- зависеть от скачивания;
- коммитить binary;
- внедрять Maven/Gradle.

Минимальный explicit runner достаточен для foundational unit/DB/scenario/performance gates и может расширяться test suites без runtime dependency.

## 7. DB safety model

```text
Admin environment
  -> local server URL without schema
  -> exact allowlist
  -> recreate only l2jmobiush5_phantom_test
  -> create l2j_phantom_test
  -> grant test DB only
  -> strict schema install
  -> versioned test migration
  -> atomic local config

Ant test JVM
  -> .phantom-local/Database.test.ini
  -> guard path/url/host/port/db/user
  -> only then DatabaseFactory explicit init
  -> tests
  -> DatabaseFactory.close
```

## 8. Deterministic scenario fixture

Algorithm:

```text
SplittableRandom(20260725001)
64 × nextInt(1000)
SHA-256 of big-endian 4-byte integers
```

Expected:

```text
A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45
```

First ten values for diagnostic cross-check:

```text
841 9 973 990 258 913 774 550 98 870
```

## 9. Expected future use

Task 003 tests:

- disabled config;
- no-op startup/shutdown;
- no DB/network side effects.

Task 004 tests:

- full game schema;
- Player fixture;
- materialization;
- packet effects;
- cleanup/restart.

Task 002 must not implement those behaviors now.
