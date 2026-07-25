# TEST CASES — Task 002

## 1. Unit suite

### URL guard

| ID | Input | Expected |
|---|---|---|
| db-url.mysql-valid | exact mysql local/test DB | accept |
| db-url.localhost-valid | localhost:3308/test DB | accept |
| db-url.production | `l2jmobiush5` | reject |
| db-url.empty | no database | reject |
| db-url.unknown | other database | reject |
| db-url.case | case-changed test DB | reject |
| db-url.trailing | test DB plus slash | reject |
| db-url.extra-path | second segment | reject |
| db-url.encoded-production | encoded production DB | reject |
| db-url.credentials | userinfo in URL | reject |
| db-url.remote | non-local host | reject |
| db-url.port | non-3308/missing port | reject |
| db-url.multihost | multiple hosts | reject |
| db-url.fragment | fragment | reject |

### Config guard

- production config path rejected;
- path outside `.phantom-local` rejected;
- missing file rejected;
- missing URL rejected;
- missing user rejected;
- wrong user rejected;
- backup enabled rejected;
- connection fan-out enabled rejected;
- max pool above safe test limit rejected;
- password never appears in exception.

### Seed/scenario

- same seed gives same values;
- different seed changes sequence;
- first ten values match context;
- final checksum exact:
  `A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45`.

### Runner

- explicit registration count > 0;
- stable order;
- assertion failure sets exit 1;
- config failure sets exit 2;
- internal failure sets exit 3;
- XML escapes special characters;
- password redaction.

### SQL executor

Tests based on actual repository syntax:

- comments;
- blank lines;
- semicolon termination;
- quoted semicolon;
- backtick identifiers;
- stable file order;
- unsupported `DELIMITER` detection if unsupported;
- SQL failure propagates;
- no continue after failure.

## 2. Runner negative control

- run intentionally failing test;
- expected process exit `1`;
- Ant target PASS only when exact exit received;
- report contains one intentional failure;
- main `verify` treats this as successful negative control.

## 3. DB guard negative control

Config:

```text
Driver = SentinelJdbcDriver
URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5
Login = l2j_phantom_test
```

Expected:

- guard rejection before driver class load;
- process exit `2`;
- sentinel marker absent;
- connect count zero;
- Hikari not initialized.

## 4. Provisioning tests

- admin URL local/no schema;
- target DB constant exact;
- production name cannot be passed;
- recreate succeeds twice;
- partial failure drops test DB;
- partial failure deletes local config;
- login scripts strict pass;
- game scripts strict pass;
- migration pass;
- script inventory/hash recorded;
- dedicated user created;
- grants only test DB.

## 5. DB integration suite

1. config path guard PASS;
2. explicit DatabaseFactory init PASS;
3. `SELECT DATABASE()` exact;
4. current user begins `l2j_phantom_test@`;
5. `SHOW GRANTS`:
   - test DB present;
   - production DB absent;
   - global ALL absent;
6. core tables:
   - `accounts`;
   - `characters`;
   - `items`;
7. harness table exists;
8. deterministic fixture cleanup;
9. transaction insert;
10. select inside transaction;
11. rollback;
12. absent after rollback;
13. committed insert;
14. select committed;
15. cleanup once;
16. cleanup twice;
17. zero residue;
18. pool close;
19. no leaked non-daemon Hikari thread after bounded wait.

## 6. Scenario suite

- fixture properties loaded;
- seed exact;
- count 64;
- bound 1000;
- checksum exact;
- report includes seed/checksum.

## 7. Performance smoke

- 200 000 or more operations;
- O(1) state;
- fixed seed;
- deterministic checksum;
- elapsed < 30 seconds;
- no queue growth;
- no thread/executor creation;
- report elapsed and operation count.

## 8. Full verify

Expected target sequence passes:

```text
jar
test
phantom-negative-control
phantom-db-guard-negative-control
phantom-db-test
phantom-scenario-test
phantom-performance-smoke
phantom-static-verify
```

No suite may be silently skipped.
