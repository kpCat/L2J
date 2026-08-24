# Goal 030 Checkpoint 1 — release baseline, fresh bootstrap and coverage closure

## Статус

- Delivery status: `SUCCESS`.
- Goal 030 Checkpoint 1: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 030 overall: `IN_PROGRESS`; Goal 030 не `ACCEPT`.
- Required parent: exact `b6e634aa17cc287e658a89a45c4632bc50672e93`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- `occurred_context_compaction`: `no`.
- Goal token usage at pre-report snapshot: `274995`; elapsed goal time: `1443` seconds.
- `apply_patch` invocation count: exact `0`.

## Summary

Goal 030 CP1 установил release baseline без gameplay-разработки. Canonical guarded test DB создана с нуля ровно один раз, shipped Phantom configuration доказана как disabled-by-default и инертная, 20 release-доменов получили machine-verifiable mapping на принятый Goal lineage, реальные production owners и focused Ant evidence. Core shutdown, population, progression, operator, replay и scale-environment foundations повторно прошли на fresh bootstrap. Выполнена ровно одна финальная `jar`-сборка.

Production Java/config/schema не изменялись. Конкретный production composition defect не обнаружен; `BLOCKED_030CP1_PRODUCTION_COMPOSITION_DEFECT` не возник.

## Dependency closure Goals 001–029

Authoritative overall closure подтверждён до изменений:

- Goals 001–016 — финальные принятые overall statuses; explicit future contracts у Goals 016 и ранее принятых seam-контрактов не являются unresolved blockers.
- Goal 017 — `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS` по master plan; roadmap `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW` является stale historical wording.
- Goals 018–021 — финальные принятые overall statuses.
- Goal 022 overall — `ACCEPT` на baseline `1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb`; historical CP2 self-status superseded overall closure.
- Goal 023 overall — `ACCEPT` после Goal 023C на baseline `e67298697eaecc629a03b215a78ffa947233efd3`; historical corrective wording не является blocker.
- Goals 024–028 overall — `ACCEPT`.
- Goal 029D — `ACCEPT`; Goal 029C — `ACCEPT after Goal 029D`; Goal 029 Checkpoint 3 — `ACCEPT after Goal 029D`; Goal 029 overall — `ACCEPT` по independent-review truth текущей задачи.

Реальный unresolved overall blocker Goals 001–029 не найден; `BLOCKED_030CP1_DEPENDENCY_CLOSURE` не возник. Roadmap обновлён только prescribed статусами Goal 029/Goal 030.

## Fresh guarded bootstrap

- `prepare-phantom-test-db` вызван ровно один раз.
- Exact DB identity: `127.0.0.1:3308/l2jmobiush5_phantom_test`.
- Dedicated test user: `l2j_phantom_test`.
- Production DB `l2jmobiush5`: запрещена guard-ом и не использовалась.
- Schema scripts: login `4`, game `115`, migrations `2`, total `121`.
- Schema manifest: version `1`, scripts `121`, statements `214`.
- Aggregate SHA-256: `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.
- Admin env names и provisioning target выполнялись в одном PowerShell process; значения credentials не печатались и не записывались; env удалён в `finally`.
- Manual SQL/manifest/config repair не выполнялся; provisioning не повторялся.

`BLOCKED_030CP1_ADMIN_PROVISIONING_ENV_REQUIRED` и `BLOCKED_030CP1_GUARDED_REPROVISION_FAILED` не возникли.

## Release coverage matrix

Matrix: `test/resources/phantoms/release/goal030-release-coverage.tsv`.

- Required domains: exact `20/20`, без missing/duplicate rows.
- `COVERED_PRIOR`: `11`.
- `COVERED_CP1`: `6`.
- `PENDING_GOAL030`: `3`.

Pending rows ограничены release-specific gaps:

1. `activity-materialization` — CP2, cross-domain autonomous alpha.
2. `restart-failure-recovery` — CP3, release-level restart/failure recovery.
3. `rollback-release-control` — CP3, final release decision and rollback proof.

Suite проверяет owner existence, Ant target declarations, Goal range 001–029, status/checkpoint contract, запрет generic `verify` как единственного evidence и запрет выдавать old scenario-smoke checksum за living-world E2E.

## Shipped disabled no-mutation proof

`PhantomPlayersConfig.read(actual shipped PhantomPlayers.ini)` подтвердил:

- `enabled=false`, diagnostics disabled;
- materialization/scheduler capacities и pulse budgets — `0`;
- population target/ACTIVE target — exact `0/0`;
- population creation/boundary, Party и social-cache effective capacities — `0`.

`new PhantomSystem(Settings.disabled()).start()` вернул `false`, state перешёл `NEW -> DISABLED`. Scheduler, Decision, Navigation, Topology, Game Knowledge, Semantic, Progression, Combat, Population, Social, Conversation и Conversation Execution snapshots остались inactive/zero; Background runtime отсутствовал; lifecycle metrics, diagnostic trace и selected trace остались zero/disabled.

Static shipped config был загружен canonical `PhantomPlayersConfig.load()`. `operatorEnable()` вернул `CONFIG_DISABLED`, `startConfigured()` вернул `false`, configured runtime instance не публиковался.

Guarded DB counts до/после disabled path: profiles `0/0`, components `0/0`. Suite-level afterAll повторно подтвердил отсутствие изменения counts. Production DB не использовалась.

## Exact changed files

1. `build.xml` — seed `30003001` и forked, guarded, no-provision target `phantom-release-baseline-goal030cp1-test`, timeout `600000`, cwd `dist/game`.
2. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — launcher mode нового suite.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomReleaseBaselineGoal030Checkpoint1Suite.java` — matrix validation, fresh DB identity и disabled no-mutation assertions.
4. `test/resources/phantoms/release/goal030-release-coverage.tsv` — exact 20-domain release matrix.
5. `docs/phantoms/PHANTOM_RELEASE_GATE.md` — operational CP1/CP2/CP3 release gate и rollback contract.
6. `docs/PHANTOM_BOTS_ROADMAP.md` — prescribed accepted Goal029 lineage и Goal030 CP1/overall statuses.
7. `docs/phantoms/reports/030-checkpoint-1-release-baseline-fresh-bootstrap-coverage.md` — этот отчёт.

Production Java/config/schema diff: exact zero. Shipped `dist/game/config/Custom/PhantomPlayers.ini` не изменялся. Другие хроники и user-owned untracked task packages не изменялись и не staging.

## Architecture and process decisions

- Переиспользованы существующие `PhantomTestLauncher`, `PhantomTestDatabaseBootstrap`, `PhantomTestDatabaseGuard`, suite registry/reporting и forked Ant target pattern.
- Новые production owners, abstraction layers, config keys, schema/migrations и gameplay behavior не добавлялись.
- CP1 status отражает release baseline, а не living-world E2E. CP2 и CP3 targets в pending rows являются explicit planned targets и не выдаются за существующее evidence.
- Rollback документирован через существующие bounded operator drain/disable controls и shipped disabled config.
- Все изменения выполнялись exact-anchor/новый-file UTF-8-no-BOM temp + same-directory atomic `Move-Item`; `apply_patch` не вызывался.

## Focused gates and build

1. `prepare-phantom-test-db` — PASS, один вызов, total `25 seconds`; compile `2208` production + `113` test sources.
2. `phantom-release-baseline-goal030cp1-test` — `3/3 PASS`, seed `30003001`, total `21 seconds`.
3. `phantom-server-shutdown-handoff-test` — `7/7 PASS`, total `36 seconds`.
4. `phantom-population-server-integration-test` — `1/1 PASS`, total `38 seconds`.
5. `phantom-progression-production-composition-test` — `9/9 PASS`, total `51 seconds`.
6. `phantom-operator-runtime-controls-goal028cp2-test` — `6/6 PASS`, total `20 seconds`.
7. `phantom-deterministic-replay-goal028cp5-test` — `7/7 PASS`, total `20 seconds`.
8. Первый `phantom-scale-environment-goal029cp2-test` attempt — fail-closed в `before-all` с `BLOCKED_029CP2_ADMIN_STATUS_ENV_REQUIRED`; cases не выполнялись, DB provisioning не запускался.
9. Повтор только CP2 target с требуемыми admin status env names в том же PowerShell process — `4/4 PASS`, total `58 seconds`; env удалён в `finally`.
10. Ровно один финальный `jar` — `BUILD SUCCESSFUL`, total `20 seconds`; LoginServer/GameServer/DatabaseInstaller jars собраны, GameServer.jar и LoginServer.jar скопированы в рабочий `dist/libs`.

`jar` invocation count: exact `1`. Goal029 30-minute soak не повторялся: Goal029D/029C/CP3 independently accepted. `verify`, historical broad aggregates, geodata stress, Goal030 CP2 и CP3 не запускались.

Каждая compile-tests фаза сообщала только две существующие JDK removal warnings для bounded `System.runFinalization()` в Goal029 CP2/CP3 suites; новых warnings нет.

## Performance and lifecycle

CP1 не добавляет hot-path code и не изменяет runtime performance. Focused scale environment gate повторно подтвердил 10 000 durable SHELL bootstrap, queue recovery, zero-DB scheduler window и two-wave overload recovery в пределах accepted Goal029 CP2 budgets. Long soak evidence переиспользовано из independently accepted Goal029D; новый 30-minute run не выполнялся.

Все запущенные JVM owners завершили Hikari/ThreadPool lifecycle. CP1 disabled path не создал scheduler future, navigation worker, runtime owner или DB mutation.

## DB, migrations and configs

- Schema/migration changes: none.
- Production/test DB config tracked changes: none.
- Shipped Phantom config changes: none.
- Fresh bootstrap использовал только canonical provisioner и generated local untracked config/manifest.
- Production database: not used.
- Manual SQL и post-provision repair: none.

## Diagnostics, deviations and limitations

- Первый Goal029 CP2 invocation был ожидаемо отвергнут fail-closed из-за очищенного provisioning env. После bounded diagnosis target повторён с требуемым read-only admin status env; fresh provisioning не повторялся.
- Один launcher edit command завершился parser error до write; один roadmap command завершился anchor mismatch до write; две build here-string попытки не изменили target. Финальные exact-anchor записи выполнены bounded array/chunk operations; partial target files и `*.goal030cp1.tmp` отсутствуют.
- CP1 не доказывает cross-domain autonomous alpha, release-level restart/failure recovery или final release decision. Эти gaps остаются только CP2/CP3 pending rows.
- CP1 не присваивает Goal030 статус `ACCEPT`.

## Static, encoding and scope

- `apply_patch` invocation count: exact `0`; `BLOCKED_030CP1_FORBIDDEN_APPLY_PATCH_ATTEMPT` не возник.
- UTF-8 without BOM и strict UTF-8 decode для exact changed allowlist: PASS.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- `git diff --check`, полный exact diff и production-diff: PASS; staged allowlist — exact 7 files, PASS.

## Git and delivery

TASK/Agents.md разрешают обязательный baseline Git inspection, bounded exact diff/scope verification, ordinary commit и push. Использованы только разрешённые Git-команды для status/branch/HEAD/upstream/diff/staging/commit/push; amend/rebase/reset/merge/force push не используются.

Preferred commit subject: `test(phantoms): establish release baseline`.

Commit SHA и push result указываются в финальном сообщении, поскольку report-bearing commit не может содержать собственный SHA.

## Next step

Независимый review Goal 030 CP1. После принятия CP1 следующий этап — только Goal 030 CP2 cross-domain autonomous alpha. Goal 030 CP3 release decision/rollback/restart-failure не начинается до принятия CP2.
