# Codex report — 002-automated-test-infrastructure

## Status

`SUCCESS`

Реализация, targeted suites, aggregate `ant verify`, отдельный `ant jar` и
pre-commit verifier были завершены успешно в исходной реализации.

Независимое ревью исходной реализации: `FIX REQUIRED`.

Task 002A закрывает findings отдельным hotfix commit и имеет manual gate
`PENDING_INDEPENDENT_REVIEW`.

Task 003: `NOT_STARTED`; до независимого ревью Task 002A остаётся заблокирован.

## Starting baseline

- Branch: `feature/phantom-world`.
- Accepted parent: `7aa24faf202567add0fa81561242d37453c6055f`.
- Starting local HEAD и `origin/feature/phantom-world` совпадали с accepted
  parent.
- Стартовые изменения: только untracked supplied package Task 002.
- Во время работы появился независимый untracked
  `docs/agent-tasks/HELPER-LISTVIEW-SORTER-005/**`; он не читался, не менялся и
  исключён из Task 002 staging/scope.

## Summary

Создан JDK-only test harness с explicit suite registration, ordinal ordering,
exit contract `0/1/2/3`, plain-text/XML reports и deterministic seed. Добавлены
fail-closed test DB guard, strict schema provisioner, dedicated local config,
runner/DB negative controls, unit/DB/scenario/performance suites и полный Ant
contract Task 002.

Production Phantom runtime, Task 003, `Player`, `GameClient`, `GameServer`,
network packets, `PhantomPlayers.ini`, runtime feature flags и production DB
schema не изменялись.

## Changed files

- `.gitignore` — игнорируется только module-local `.phantom-local`.
- `build.xml` — отдельные test paths/classpath и обязательные Task 002 targets.
- `java/org/l2jmobius/commons/config/DatabaseConfig.java` — default path
  опубликован как constant и добавлен explicit strict load.
- `java/org/l2jmobius/commons/database/DatabaseFactory.java` — общий pool setup,
  generic explicit fail-fast init, reusable close и read-only lifecycle state.
- `test/java/org/l2jmobius/tests/phantoms/**` — 16 JDK-only harness/guard/SQL/
  provisioning/suite classes.
- `test/resources/phantoms/**` — test migration и deterministic scenario
  fixture.
- `tools/phantoms/prepare-test-db.ps1` — guarded provisioning launcher.
- `tools/phantoms/verify-task-002.ps1` — deterministic read-only scope/static
  verifier.
- `docs/phantoms/tasks/002-automated-test-infrastructure/**` — supplied package,
  сохранён без архитектурного расширения.
- этот отчёт.

Это bounded exception к предпочтению 8–10 файлов: набор заранее определён
Task 002 и образует один test-infrastructure artifact family; другие подсистемы
не затронуты.

## Test runtime architecture

- Только JDK 25; JUnit/TestNG/Maven/Gradle и новые JAR отсутствуют.
- Suites регистрируются явно, reflection/annotation discovery отсутствует.
- Имена тестов сортируются ordinal и имеют вид `<suite>.<test>`.
- Каждая Ant suite запускается в отдельном forked JVM.
- Failure report содержит type/message/seed после redaction.
- Reports: `../build/phantom-test/reports/<mode>.txt|xml`.
- Test classes/resources компилируются в `../build/phantom-test`, отдельно от
  production `../build/bin`, и не входят в production JAR.
- Фактический `jar tf`: `GameServer.jar` test entries `0`,
  `LoginServer.jar` test entries `0`.

## Architecture decisions

- Existing Ant layout, `ConfigReader` naming, Hikari setup и stable SQL filename
  order переиспользованы.
- Interactive `DatabaseInstaller` не используется: его continue-on-error parser
  не удовлетворяет strict provisioning.
- Exact test DB policy находится только в test guard/provisioner; production
  `DatabaseFactory` остаётся generic.
- Test runtime не создаёт executor/thread на test; Hikari закрывается в
  `finally`.

## Production compatibility

- `DatabaseConfig.load()` продолжает загружать
  `./config/Database.ini` с прежними defaults.
- `DatabaseConfig.load(String)` валидирует explicit path и затем использует те
  же keys/defaults.
- `DatabaseFactory.init()` сохраняет production catch/log semantics, pool name,
  limits/timeouts и `initializationFailTimeout=-1`.
- `DatabaseFactory.initFromConfig(String)` использует общий setup, но
  fail-fast и propagates failure.
- `close()` обнуляет закрытый pool и позволяет повторный test-JVM lifecycle.
- Test DB allowlist, seed и Phantom policy в production support code
  отсутствуют.

## DB guard ordering proof

Negative bootstrap выполняет:

1. canonical config/path checks;
2. parse URL/host/port/database/user/config safety;
3. exact rejection production database;
4. только после успешного guard test path мог бы вызвать
   `DatabaseFactory.initFromConfig`.

Production-negative config содержит driver
`org.l2jmobius.tests.phantoms.SentinelJdbcDriver`. Его static initializer и
`connect()` создают marker. Фактический результат: expected exit `2`, marker
отсутствует, `driverLoads=0`, `connectionAttempts=0`; Hikari/DriverManager не
вызывались.

## Test DB provisioning

- Target: `127.0.0.1:3308/l2jmobiush5_phantom_test`.
- Admin URL принимается только server-level, local, port `3308`, без schema,
  credentials, query или fragment.
- До `Class.forName`, подключения и destructive SQL выполнены constant guard и
  preflight всех schema scripts.
- Destructive identifiers hardcoded; CLI database/user arguments отсутствуют.
- Flow удаляет/создаёт только exact test DB и host bindings dedicated user.
- Partial failure удаляет partial test DB/user и local config.
- Single-process lock: `.phantom-local/test-db.lock`, stale PID проверяется.
- Config создаётся temp → atomic move.
- Recreate после успешного provisioning выполнен повторно: PASS.

Первый provisioning run выявил false-positive grant check: test DB содержит
production name как prefix. Run корректно завершился failure/cleanup; проверка
заменена exact schema-token match и защищена regression test. Два последующих
полных provisioning runs завершились успешно.

## Schema script inventory/hashes

- Login scripts: `4`.
- Login stable aggregate SHA-256:
  `F228E317F68121A1B724F76B02713E5BAE912A2C57ABAC08B65475FF09E424F6`.
- Game scripts: `111`.
- Game stable aggregate SHA-256:
  `36D04EC40513407AC4F66C64F750823555B00EA65C9AC6ED58CA356559ECB2CB`.
- Test migrations: `1`.
- Migration SHA-256:
  `5C08C6831F082BE25E84ADD26626335D8057C9BE41833D89F919D296B5F74C05`.
- Installed scripts total: `116`.
- Statements total: `204`.
- Full runtime manifest aggregate SHA-256:
  `359A59D2B28475C0C3727974E2C9A94E1CE86F611537EE5B82E521E954E139F9`.

Generated `schema-manifest.txt` содержит relative path, SHA-256 и statement
count каждого script. Он находится только в ignored build reports.

Audit syntax: `DELIMITER=0`, `SOURCE=0`, routines/events/triggers=0`,
`executable comments=0`, `#` comments в 5 files, line `--` в 9, inline `--` в
12, backticks в 113, quoted semicolon в 2. Strict runner поддерживает
наблюдаемые comments/quotes/backticks, UTF-8 и останавливается на первой ошибке
с relative file + statement index. Unsupported syntax отклоняется preflight до
первого SQL statement.

## Database changes

Production DB `l2jmobiush5` не выбиралась, не читалась, не изменялась, не
клонировалась и не использовалась как fixture source.

Создана только local test DB `l2jmobiush5_phantom_test`; existing repository
login/game schema установлена с нуля. Existing installer SQL не менялся.
Test-only migration создаёт idempotent table `phantom_test_harness` и была
повторно применена в том же schema для проверки idempotency.

## Dedicated user/grants

- Username: `l2j_phantom_test`.
- Созданы только host bindings `127.0.0.1` и `localhost` с одним generated
  random password.
- Grants: только `l2jmobiush5_phantom_test.*`.
- `SHOW GRANTS` подтвердил test grant, отсутствие production grant и отсутствие
  global `ALL PRIVILEGES ON *.*`.
- DB suite подтвердила `CURRENT_USER()` prefix
  `l2j_phantom_test@`.

## Local config

- Path: `.phantom-local/Database.test.ini`.
- Файл ignored/untracked и не копируется в `dist/game/config`.
- URL exact local test DB; user dedicated; pool max `4`;
  `TestDatabaseConnections=false`; `BackupDatabase=false`.
- Password generated через `SecureRandom`, хранится только локально и не
  выводится.
- Missing config приводит `phantom-db-test`/`verify` к FAIL, а не SKIP.

## Negative controls

- Runner negative control: намеренный assertion FAIL, JVM exit `1`; Ant wrapper
  PASS только при exact `1`.
- Production DB guard negative control: guard rejection, JVM exit `2`,
  sentinel marker отсутствует, driver loads/connections `0`.

## Fixture lifecycle

DB suite проверяет exact current DB/user/grants/core tables, затем:

- deterministic cleanup;
- transaction insert/select/rollback;
- отсутствие row после rollback;
- committed fixture/select;
- cleanup;
- повторный cleanup;
- zero owned residue;
- pool close/reopen;
- final close и отсутствие live non-daemon Hikari thread.

Fake account/character/item fixture не создаётся.

## Ant targets

Добавлены:

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

Existing `compile`/`jar` production output/copy behavior сохранён.

## Suite/test counts

- Unit: `41/41 PASS`.
- Runner negative: `1` intentional FAIL, wrapper PASS.
- DB guard negative: `1/1 expected rejection PASS`.
- DB integration: `8/8 PASS`.
- Scenario: `1/1 PASS`.
- Performance: `1/1 PASS`.
- Registered ordinary positive tests: `50`.

## Determinism

- Canonical seed: `20260725001`.
- Unit suite проверяет repeatability, different seed и first ten values.
- Reports включают seed, а failures — seed/type/sanitized message.
- SQL/test ordering ordinal и стабильный.

## Scenario checksum

`SplittableRandom(20260725001)`, 64 × `nextInt(1000)`, SHA-256 big-endian int
stream:

`A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45`

Fixture и фактический checksum совпали.

## Performance smoke measurement

- Operations: `250000` на один deterministic pass; два passes для checksum
  equality.
- State: O(1), без collection/queue/executor/thread.
- Rolling checksum: `BC2F4B1A43621F54`.
- Focused elapsed: `7 ms`; aggregate `ant verify` run: `6 ms`.
- Gate: `< 30000 ms`, PASS.

Это measurement только harness, не Phantom runtime benchmark.

## Secrets redaction

- Admin credentials supplied through environment: yes.
- Credentials recorded: no.
- Admin/test passwords в stdout/stderr/report/Git отсутствуют.
- Environment variable names документированы, значения — нет.
- Settings/password holder `toString()` redacted; runner sanitizes named
  password и JDBC userinfo.

## Scope

Exact Task 002 allowlist соблюдён. Other chronicles, master plan, `Agents.md`,
ADR 0001, previous reports/audits/reviews/verifiers, production config/data/SQL,
GameServer/LoginServer/Player/GameClient/network и Task 003 не менялись.

Независимый untracked `docs/agent-tasks/**` сохранён и исключён из staging.

## Commands/exit codes

```text
git rev-parse --show-toplevel                                      # 0
git status --short --branch                                       # 0
git remote -v                                                     # 0
git fetch origin --prune                                          # 0
git rev-parse HEAD                                                # 0
git rev-parse origin/feature/phantom-world                        # 0
git log -1 --format=fuller HEAD                                   # 0
git diff --name-status 7aa24faf...HEAD                            # 0, empty
java -version                                                     # 0, 25.0.4 LTS
ant -version / ant -p                                             # command absent from PATH
<external-ant-1.10.15>\bin\ant.bat -version                      # 0
<external-ant-1.10.15>\bin\ant.bat -p                            # 0
<external-ant-1.10.15>\bin\ant.bat compile-tests                 # 0
<external-ant-1.10.15>\bin\ant.bat test                          # 0
<external-ant-1.10.15>\bin\ant.bat phantom-negative-control      # 0, expected child exit 1
<external-ant-1.10.15>\bin\ant.bat phantom-db-guard-negative-control # 0, expected child exit 2
powershell ... tools\phantoms\prepare-test-db.ps1                 # first run 1, safe cleanup
powershell ... tools\phantoms\prepare-test-db.ps1                 # 0
powershell ... tools\phantoms\prepare-test-db.ps1                 # 0, repeat recreate
<external-ant-1.10.15>\bin\ant.bat phantom-db-test                # 0
<external-ant-1.10.15>\bin\ant.bat phantom-scenario-test          # 0
<external-ant-1.10.15>\bin\ant.bat phantom-performance-smoke      # 0
<external-ant-1.10.15>\bin\ant.bat verify                         # 0
<external-ant-1.10.15>\bin\ant.bat jar                            # 0
powershell ... tools\phantoms\verify-task-002.ps1                 # 0, 70/70 pre-commit
```

`ant` отсутствует в PATH. Использован уже существующий official Apache Ant
1.10.15 из OS temp Task 001; binary в repository не добавлялся.

## Test results

Все targeted suites и повторное provisioning прошли. Aggregate `ant verify`
PASS, exit `0`, final total time 17 seconds. Отдельный `ant jar` PASS, exit `0`,
total time 12 seconds; GameServer/LoginServer JAR скопированы штатным target.

## Pre/final verifier

- Development verifier run исправил собственные path/scope parsing issues и
  обнаружил отсутствующий на тот момент report.
- Required pre-commit verifier: PASS, `70/70`, exit `0`.
- Final run 1: PASS, `70/70`, exit `0`.
- Final run 2: PASS, `70/70`, exit `0`.
- Final outputs identical byte-for-byte.
- SHA-256 обоих outputs:
  `863B235A99D686D99F8B1DA98762DCBD3A683D0E729F66CB88590954A609CE0C`.

## Performance measurements

См. `Performance smoke measurement`. Production server, client и Phantom
runtime не запускались.

## Deviations from TASK.md

- `ant` отсутствует в PATH; использован разрешённый existing external Ant
  1.10.15.
- Независимые untracked `docs/agent-tasks/**` появились после стартового
  status. Они сохранены и не включаются в Task 002 diff/commit; поэтому общий
  working tree после commit может оставаться не clean, хотя Task 002 index/diff
  будет clean.

Архитектурных deviations нет.

## Known limitations

- Local test DB/user/config намеренно остаются для последующих Task после
  independent review; они могут быть безопасно пересозданы explicit target.
- Harness не запускает LoginServer/GameServer и не является gameplay runtime.
- ADR 0001 остаётся `Proposed`.

## Risks

- External unrelated untracked files требуют exact-path staging.
- Test DB provisioning требует local MariaDB и достаточные admin grants.
- Любой drift repository SQL должен менять manifest hash и проходить strict
  parser заново.

## Branch/parent/commit/push

- Branch: `feature/phantom-world`.
- Parent: `7aa24faf202567add0fa81561242d37453c6055f`.
- Original Task 002 commit:
  `36e5411e01e8e73f8a0fd4d9460e327c28a6798b`.
- Commit subject: `test(phantoms): add isolated automated test infrastructure`.
- Push: successful.
- Remote ref после push:
  `origin/feature/phantom-world = 36e5411e01e8e73f8a0fd4d9460e327c28a6798b`.
- Force push: не используется.

## Git

Git-команды использовались только потому, что TASK.md и пользователь прямо
требуют provenance, exact scope, commit и push. До commit выполнялись только
перечисленные read-only Git-команды; amend/rebase/reset/restore не
использовались.

## Manual gate

Исходная реализация Task 002: `FIX REQUIRED`.

Findings:

- foreign provisioning lock deletion;
- stale schema false green;
- partial `beforeAll` cleanup gap;
- JDBC query authentication properties;
- post-commit report placeholders.

Closure: Task 002A `002a-test-infrastructure-safety-hotfix`,
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.

## Task 003

`NOT_STARTED`

## Recommended next step

Провести независимое ревью Task 002A. Task 003 до отдельного review-решения не
начинать.
