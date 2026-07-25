# ARCHITECTURE — Task 002

## 1. Component map

```text
build.xml
  ├─ compile production
  ├─ compile-tests
  ├─ test
  ├─ negative controls
  ├─ phantom-db-test
  ├─ phantom-scenario-test
  ├─ phantom-performance-smoke
  ├─ phantom-static-verify
  └─ verify

PhantomTestLauncher
  ├─ explicit suite registry
  ├─ deterministic context
  ├─ stable result collector
  ├─ text output
  ├─ XML report
  └─ process exit code

PhantomTestDatabaseProvisioner
  ├─ admin env validation
  ├─ exact destructive guard
  ├─ test DB/user recreate
  ├─ strict login/game schema install
  ├─ test migration
  ├─ grant verification
  └─ atomic local config

PhantomTestDatabaseGuard
  ├─ config path guard
  ├─ JDBC URL parsing
  ├─ host/port/database allowlist
  ├─ dedicated user allowlist
  └─ validated settings

DatabaseConfig
  └─ default load + explicit config load

DatabaseFactory
  └─ production init + explicit fail-fast init
```

## 2. Production compatibility

### Before

```text
GameServer
  -> DatabaseFactory.init()
  -> DatabaseConfig.load()
  -> ./config/Database.ini
  -> Hikari
```

### After

```text
GameServer
  -> DatabaseFactory.init()
  -> same default config
  -> same production semantics

Test JVM
  -> guard explicit local test config
  -> DatabaseFactory.initFromConfig(testConfig)
  -> fail-fast Hikari
```

Default call path must remain behaviorally equivalent.

## 3. Runner API

No annotation discovery.

Suggested contract:

```java
interface PhantomTestSuite
{
    String id();
    void register(PhantomTestRegistry registry);
}
```

or equivalent explicit structure.

Test identity:

```text
<suite-id>.<test-id>
```

Stable sorting uses `String.CASE_SENSITIVE_ORDER`/ordinal equivalent.

Exit codes:

```text
0 = all expected tests passed
1 = test assertion/failure
2 = configuration/guard/bootstrap rejection
3 = internal runner error
```

Negative targets assert exact expected exit.

## 4. Test report

Each suite writes:

```text
../build/phantom-test/reports/<mode>.txt
../build/phantom-test/reports/<mode>.xml
```

XML minimum:

- suite;
- seed;
- total/passed/failed;
- test name/status;
- sanitized failure type/message.

Duration can be included but is not used for deterministic static verifier comparison.

## 5. Config path contract

Default local path:

```text
<module>/.phantom-local/Database.test.ini
```

Canonical path must remain under `.phantom-local`.

Reject symlink/path escape where Windows canonical path allows detection.

## 6. JDBC URL parser

Allowed shape:

```text
jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test?<properties>
```

Optional `localhost`.

Do not accept:

- embedded `user:password@`;
- multi-host/loadbalance/replication;
- missing port;
- database in query instead of path;
- extra `/`;
- case variation;
- percent-encoded production name;
- fragments;
- empty database.

## 7. Provisioning ownership

Constants, not arbitrary user input:

```text
TARGET_DATABASE = l2jmobiush5_phantom_test
TARGET_USER = l2j_phantom_test
TARGET_PORT = 3308
```

Random password:

- at least 32 hex chars;
- `SecureRandom`;
- never output;
- local config only.

Provisioning lock:

- atomic file create;
- contains process ID/start marker but no secret;
- cleanup in finally;
- stale lock only removed after proving owning process is absent or explicit safe age policy.

## 8. SQL schema installer

Input roots after actual audit:

```text
dist/db_installer/sql/login
dist/db_installer/sql/game
```

Order:

1. login files stable sorted;
2. game files stable sorted;
3. test migrations numeric sorted.

Before execute:

- inventory files;
- compute SHA-256;
- scan unsupported syntax;
- fail before first statement if unsupported.

On script error:

- throw with relative file and statement number;
- no continue;
- outer provisioner drops test DB.

## 9. Secrets

Forbidden in Java exception/output/report:

- password values;
- Properties dump;
- raw environment;
- connection object `toString` if it can expose URL properties.

Create helper redaction and test it.

## 10. Negative connection proof

`SentinelJdbcDriver`:

- must not be loaded in rejected production-config test;
- static initializer and/or connect writes a marker/counter;
- marker path is temp/build outside tracked source;
- after guard rejection:
  - expected exit 2;
  - marker absent;
  - no Hikari pool.

This proves ordering, not merely string rejection.

## 11. DB integration lifecycle

```text
guard
 -> init explicit pool
 -> SELECT DATABASE
 -> SELECT CURRENT_USER
 -> SHOW GRANTS
 -> verify core tables
 -> cleanup deterministic fixture
 -> transaction insert/select/rollback
 -> assert absent
 -> committed insert/select
 -> cleanup
 -> cleanup again
 -> assert zero
 -> close pool
```

All cleanup in `finally`.

## 12. Performance and concurrency

Task 002 runner:

- no executor;
- one JVM thread except Hikari internal threads in DB suite;
- Hikari closed;
- bounded result list equals registered test count;
- no unbounded logs;
- performance workload holds O(1) state.
