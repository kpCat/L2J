# Codex report — 002a-test-infrastructure-safety-hotfix

## Status

`SUCCESS`

Все P1/P2 findings Task 002 закрыты в test-only/build/documentation scope.
Production Java и production DB не изменялись.

## Starting baseline

- Branch: `feature/phantom-world`.
- Baseline и parent hotfix commit:
  `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`.
- Baseline parent:
  `7aa24faf202567add0fa81561242d37453c6055f`.
- Начальные local HEAD и `origin/feature/phantom-world` совпадали с baseline.
- Ожидаемый untracked package:
  `docs/phantoms/tasks/002a-test-infrastructure-safety-hotfix/**`.
- Независимый pre-existing `docs/agent-tasks/**` не читался, не менялся и
  исключён из scope/staging.

`README.md`, code-map и pattern-документы в рабочем модуле не найдены.

## Independent findings addressed

- Foreign provisioning lock deletion.
- Stale schema false green.
- Partial `beforeAll` cleanup gap.
- JDBC query authentication properties.
- Недостаточная redaction JDBC query/SQL secrets.
- Post-commit provenance placeholders отчёта Task 002.

Task 003 не начинался.

## Lock ownership design

`PhantomProvisioningLock` использует persistent
`.phantom-local/test-db.lock`, `FileChannel`, `FileLock.tryLock()` и owner
token. Только process, получивший OS lock, записывает token и входит в
provisioning/JDBC path.

Busy process закрывает только собственный channel, не удаляет lock-файл, не
перезаписывает token, не удаляет local config/manifest и не выполняет JDBC.
`close()` идемпотентно освобождает только собственные `FileLock`/channel.
Существующий stale файл без активного OS lock не препятствует следующему
acquisition.

Provisioner использует try-with-resources lock вокруг admin/JDBC/destructive
flow. Безусловный `deleteIfExists(lockFile)` удалён.

## Cross-process lock evidence

Target `phantom-provisioning-lock-control` запускает bounded child JVM:

1. holder получает lock и публикует owner token;
2. contender получает exact busy/config exit `2`;
3. lock-файл остаётся, token после release совпадает с holder token;
4. protected JDBC/destructive marker contender не создаёт;
5. после normal release новый contender получает lock;
6. после forced process death следующий contender также получает OS lock;
7. child processes имеют timeout/kill-on-timeout;
8. cleanup вызывается повторно и остаётся идемпотентным.

Фактический результат: `PASS`.

## Schema freshness design

`PhantomTestSchemaManifest` строит deterministic snapshot текущего stable SQL
inventory:

- login SQL;
- game SQL;
- все test migrations.

Canonical durable path:
`.phantom-local/schema-manifest.properties`.

Формат:

```text
schemaVersion=1
scriptCount=117
statementCount=205
aggregateSha256=A3C9FC62C662DC5E0E690D6E7D6E63B5B0268BAD3019348E75F565DA5C84453A
```

Timestamp и credentials отсутствуют. Запись выполняется через temporary file в
том же каталоге и `ATOMIC_MOVE + REPLACE_EXISTING`. `init-test` очищает только
build output и manifest не удаляет.

## Pre-Hikari stale-manifest evidence

`PhantomTestDatabaseBootstrap` имеет единый порядок:

1. `PhantomTestDatabaseGuard.validate`;
2. current repository inventory/snapshot;
3. read local manifest;
4. exact version/count/hash compare;
5. только затем `DatabaseFactory.initFromConfig`.

`phantom-schema-freshness-negative-control` создаёт valid local config с
`SentinelJdbcDriver` и отдельный stale manifest, затем вызывает тот же
bootstrap, что DB suite.

Фактический результат:

```text
exit=2
sentinel marker=absent
driverLoads=0
connectionAttempts=0
```

## DB metadata evidence

Migration
`002_create_phantom_test_schema_manifest.sql` создаёт idempotent metadata table
`phantom_test_schema_manifest` с canonical row key `repository-schema`.

Provisioner после schema install и повторного migration pass выполняет
upsert version/count/hash, затем немедленно делает exact read-back compare.
После Hikari initialization DB suite отдельно сравнивает current/local snapshot
с canonical DB row.

Fresh DB integration result: `9/9 PASS`, включая
`database-integration.schema-manifest-metadata`.

## Runner lifecycle fix

Runner отмечает lifecycle начавшимся непосредственно перед вызовом
`beforeAll`. `afterAll` вызывается из `finally` после любой начавшейся попытки
`beforeAll`, но не после registration failure.

Original `beforeAll` failure остаётся отдельным result. Если `afterAll` также
падает, cleanup failure добавляется вторым result, а exit code остаётся
максимальным по contract.

Control suite создаёт marker, бросает configuration failure и удаляет marker в
null-safe/idempotent `afterAll`. Ant target получил exit `2`, marker отсутствует,
original failure присутствует в report. Unit regression дополнительно
подтвердила два отдельных results и максимальный exit `3` при injected cleanup
failure.

## JDBC query allowlist

Guard percent-decode-ит raw keys/values и принимает отсутствие query либо
canonical subset generated properties:

```text
useSSL=false
allowPublicKeyRetrieval=true
serverTimezone=UTC
characterEncoding=UTF-8
```

Case-sensitive canonical keys/values обязательны. Duplicate keys отклоняются
case-insensitive. Empty/malformed pairs, blank key/value, unknown properties и
decoded separators отклоняются.

Regression tests закрывают `user`, `password`, mixed case,
`%70assword`, `password1/2/3`, duplicate/mixed duplicate, unknown, blank
key/value и encoded separator ambiguity.

## Secret redaction

`PhantomTestLauncher.sanitize` маскирует:

- named `Password=...`;
- JDBC userinfo;
- JDBC query `user`, `password`, `password1`, `password2`, `password3`;
- SQL `IDENTIFIED BY '...'`;
- SQL `IDENTIFIED BY "..."`.

Unit regression проверяет отсутствие каждого исходного secret. Provisioner
применяет тот же sanitizer к exception message, поэтому generated password не
печатается даже при SQL exception со statement text.

## Ant targets

Добавлены:

```text
phantom-provisioning-lock-control
phantom-schema-freshness-negative-control
phantom-lifecycle-negative-control
phantom-static-verify-002a
```

`verify` включает jar, unit, оба прежних negative controls, три новых controls,
DB integration, scenario, performance, historical target и verifier Task 002A.
Все Java invocations forked.

Historical `verify-task-002.ps1` сохранён byte-for-byte. Его исходный
one-commit/token contract намеренно описывает только Task 002 и отвергает
исправленные ordering seams. Поэтому historical Ant target делегирует
cumulative проверку новому verifier, который отдельно проверяет неизменность
старого файла.

## Test counts

- Unit: `66/66 PASS`.
- Runner negative: expected child exit `1`, wrapper `PASS`.
- Production DB guard negative: expected exit `2`, sentinel untouched.
- Cross-process lock: `PASS`.
- Schema freshness negative: expected exit `2`, sentinel untouched.
- Lifecycle negative: expected exit `2`, marker absent.
- DB integration: `9/9 PASS`.
- Scenario: `1/1 PASS`.
- Performance: `1/1 PASS`.

## Performance measurements

- Workload: `250000` операций, два deterministic passes.
- State: O(1), без executor/queue/collection growth.
- Checksum: `BC2F4B1A43621F54`.
- Focused elapsed в aggregate verify: `6 ms`.
- Gate: `< 30000 ms`, `PASS`.

Это measurement test harness, а не Phantom runtime benchmark.

## Re-provisioning

Fresh destructive recreate выполнялся только для
`l2jmobiush5_phantom_test` explicit provisioning target.

- Login scripts: `4`.
- Game scripts: `111`.
- Test migrations: `2`.
- Total scripts: `117`.
- Total statements: `205`.
- Aggregate:
  `A3C9FC62C662DC5E0E690D6E7D6E63B5B0268BAD3019348E75F565DA5C84453A`.
- Dedicated grants: test DB only.
- Local config и manifest: ignored/untracked.
- Admin credentials supplied through environment: yes.
- Credentials recorded: no.

Repeated provisioning дал тот же version/count/hash.

## Production DB safety

Production DB `l2jmobiush5` не подключалась, не выбиралась, не читалась и не
изменялась. Schema создавалась только из repository SQL в отдельной test DB.
Destructive identifiers остаются hardcoded allowlist constants.

## Scope

Изменения ограничены:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/**`;
- migration 002;
- новым verifier Task 002A;
- supplied task package;
- отчётами Task 002/002A;
- review record Task 002.

Production Java, старый verifier, `prepare-test-db.ps1`, migration 001,
existing login/game SQL, `.gitignore`, configs/data, другие хроники и Task 003
не менялись.

Это bounded exception к обычному лимиту числа файлов: один заранее заданный
test-infrastructure artifact family и supplied documentation package.

- Mojibake-маркеры в изменённых файлах проверены: `0` совпадений.
- Escaped Cyrillic в изменённых файлах проверены: `0` совпадений.

## Commands/exit codes

```text
git rev-parse --show-toplevel                                      # 0
git status --short --branch                                       # 0
git remote -v                                                     # 0
git fetch origin --prune                                          # 0
git rev-parse HEAD / origin/feature/phantom-world                 # 0, baseline equal
git show --stat --oneline 36e5411e...                             # 0
git diff --name-status 7aa24faf...36e5411e...                     # 0
java -version                                                     # 0, 25.0.4 LTS
external Apache Ant 1.10.15 -version                              # 0
ant compile-tests                                                 # 0
ant test                                                          # 0, 66/66
ant phantom-negative-control                                      # 0, expected child 1
ant phantom-db-guard-negative-control                             # 0, expected child 2
ant phantom-provisioning-lock-control                             # 0
ant phantom-schema-freshness-negative-control                     # 0, expected child 2
ant phantom-lifecycle-negative-control                            # 0, expected child 2
tools/phantoms/prepare-test-db.ps1                                # 0
ant phantom-db-test                                               # 0, 9/9
ant phantom-scenario-test                                         # 0, 1/1
ant phantom-performance-smoke                                     # 0, 1/1
tools/phantoms/prepare-test-db.ps1                                # 0, repeat
ant verify                                                        # 0, 22 s
ant jar                                                           # 0, 13 s
powershell ... verify-task-002a.ps1                               # 0, 52/52
```

`ant` отсутствует в PATH. Использован уже существующий external official
Apache Ant 1.10.15 из OS temp; binary в repository не добавлялся.

## Pre/final verifier

- Pre-commit Task 002A verifier: `52/52 PASS`, exit `0`.
- Final run 1 и run 2 выполняются на одном immutable commit.
- Их stdout сохраняется вне repository и сравнивается byte-for-byte/SHA-256.
- Historical `verify-task-002.ps1` не изменён.

## Branch/parent/commit/push

- Branch: `feature/phantom-world`.
- Parent: `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`.
- Subject: `test(phantoms): harden test infrastructure safety`.
- Commit SHA, push result и exact remote ref фиксируются в final handoff,
  поскольку commit не может содержать собственный self-referential SHA.
- Amend/rebase/force push не используются.

## Manual gate

`PENDING_INDEPENDENT_REVIEW`

## Task 003

`NOT_STARTED`

## Deviations

- `ant` отсутствует в PATH; использован разрешённый existing external Ant
  1.10.15.
- Активный Windows file lock не позволяет controller читать locked byte range.
  Holder публикует тот же owner token через ready-файл, а lock-file token
  сравнивается сразу после release; contention marker проверяется во время
  удержания OS lock.
- Historical Ant target делегирует cumulative gate verifier Task 002A: старый
  verifier остаётся неизменным, но его frozen Task 002 token/order checks не
  могут валидировать исправленный hotfix working tree.

Архитектурных deviations нет.

## Known limitations and risks

- Local test DB/user/config/manifest намеренно остаются для следующего
  независимого review и могут быть пересозданы только explicit target.
- Harness не запускает GameServer/LoginServer.
- Независимый `docs/agent-tasks/**` требует exact-path staging и остаётся
  untracked.

## Recommended next step

Провести независимое ревью hotfix commit. Task 003 до решения review не
начинать.
