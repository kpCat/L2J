# Goal034 closure 7 — post-restart materialization loss

## Статус

`BLOCKED` — preserved closure6 boundary `gen1 5/5/5 -> gen2 5/5/1` не воспроизвёлся ни в faithful cold-restart regression, ни в единственном разрешённом real diagnostic run: fresh gen2 восстановил полный schedule-aware set `5/5/5`. Классификация причины — `F`: стабильный production defect семейств A–E не доказан, поэтому speculative semantic fix запрещён. Обязательная валидация остановила closure на втором независимом predecessor blocker после разрешённого подтверждения первого; standalone final jar и финальный acceptance verdict не выполнялись. Goal035 не начат.

- Branch: `feature/phantom-world`.
- Required parent/HEAD/origin до изменений: `9c47b57f0ae0675b1e770135e8017fca4210cb`.
- Production database не использовалась.

## Read-first, scope и локальный паттерн

- Прочитаны module `Agents.md`, root README, master plan, current status/handoff/roadmap, closure7 TASK/CONTEXT/ACCEPTANCE/package manifest/launcher, closure6 task/report, build targets и ближайшие Scheduler/Population/Ecology/materialization/background owners и focused suites.
- Parent/root `AGENTS.md`, отдельный module README, code-map и pattern-файлы не найдены; повторный широкий поиск не выполнялся.
- Локальные аналоги: production-composed Goal033 runtime fixture, canonical `PhantomSystem.shutdownRuntime()` drain, schedule-owned desired/effective ACTIVE assertions, existing diagnostic flag и immutable test result snapshots.
- Переиспользованы existing Scheduler owner, Population/Ecology reconciliation, materialization lifecycle port и canonical World `Player` truth. Новый scheduler/timer/thread, force-online, дополнительные pulses/sleeps/timeouts и ослабление schedules/ecology/reconciliation не добавлялись.
- Bounded scope: пять code/test files и пять documentation files. Public schema, project structure, configs, schedules, DB schema, generator/runtime integration и Goal035–039 scope не менялись.
- До real run оставался непроверенным exact production boundary исходных profiles `6028,6029,6032,6033`; preserved closure6 logs не содержали per-profile first failure status, поэтому прошлую точную причину восстановить нельзя.

## Preserved closure6 evidence и faithful regression

Closure6 run `20260908-014127-c0932b31` сохранён без изменения:

- gen1 desired/expected/online `5/5/5`: `6024,6028,6029,6032,6033`;
- native restart/drain PASS;
- gen2 desired/expected/online `5/5/1`: online только `6024`, missing `6028,6029,6032,6033`;
- cleanup PASS, `forced=false`, orphan processes отсутствуют, production DB unused.

Новый focused regression в `PhantomPopulationEcologyProductionGoal033Suite` моделирует production-composed cold boundary, а не удалённый упрощённый five-player diagnostic:

1. gen1 запускается с реальным Population/Ecology/Scheduler/materialization composition и schedule-aware ACTIVE set;
2. проверяются пять scheduler-requested ACTIVE, пять effective/stable ACTIVE и пять canonical World `Player`;
3. выполняется normal `shutdownRuntime()`, все gen1 Players обязаны исчезнуть;
4. создаётся fresh gen2 runtime с новым Scheduler owner;
5. проверяется тот же полный chain и те же schedule-aware IDs.

Test-only clock помещает сценарий внутрь уже существующего evening window; production schedule не меняется. Финальный результат regression: `2/2 PASS`. На required parent не получен red before-fix и stable A–E defect не доказан, поэтому production semantics не изменялись.

## Bounded observability

- `PhantomMaterializationServiceActivityPort` при включённой существующей diagnostics настройке сохраняет только первый non-success `MaterializationService.ResultStatus` для profile и один warning snapshot.
- Snapshot bounded до 1024 profile IDs и отдаётся immutable view; polling, per-tick logging, новый thread/timer и lifecycle mutation отсутствуют.
- Existing constructor остаётся совместимым и по умолчанию выключает capture; `PhantomSystem` включает его только через `EnablePhantomDiagnostics`.
- Isolated Goal034 sandbox явно включает diagnostics. Добавлен focused exact-status test; shipped safe defaults не менялись.

## Focused validation

Финальное focused состояние — `52/52 PASS`:

- `phantom-production-materialization-test`: `21/21 PASS`;
- `phantom-black-box-local-stack-goal034-contract-test`: `18/18 PASS`;
- faithful production cold gen1 drain -> fresh gen2 regression: `2/2 PASS`;
- `phantom-population-ecology-goal033-test`: `9/9 PASS`;
- closest population integration: `1/1 PASS`;
- `phantom-db-guard-negative-control`: `1/1 PASS`, expected Java exit `2`, driver loads `0`, connection attempts `0`.

Во время разработки regression test-only clock был исправлен с законного `3/3/3` daytime результата на существующее evening window; неприменимый DB `characters.online` oracle удалён, потому что fixture до store подтверждает canonical runtime truth через World/Player. Эти итерации не маскировали production behavior.

## Единственный real Goal034 diagnostic run

Run `20260908-112158-bfe101b7`, artifact `.phantom-local/blackbox/goal034/20260908-112158-bfe101b7/artifacts/manifest.properties`:

- guarded DB: `127.0.0.1:3308/l2jmobiush5_phantom_test`; `database.production.used=false`;
- gen1 desired/expected/online `5/5/5`, IDs `6119,6123,6124,6127,6128`;
- gen1 ready/catchup/unique/ownership/catalog/canonical invariants true;
- native restart expected=observed at `2026-09-08T09:29:00Z`, exit `2`, normal drain true;
- gen2 desired/expected/online `5/5/5`, те же IDs, missing/unexpected none;
- все gen2 truth flags, identity continuity и ecology continuity true;
- второй native restart expected=observed at `2026-09-08T09:36:00Z`, exit `2`, normal drain true;
- LoginServer continuity retained, `native.restarts=2`;
- cleanup: population `10`, registration true, `forced=false`, orphans none, working integrity true; fingerprints stable.

В gen1 bounded diagnostics зафиксировала первые `MATERIALIZATION_FAILED_CLEAN` для пяти admitted profiles; существующий scheduler bounded retry без изменения policy естественно восстановил parity. В gen2 failure warnings отсутствовали и полный set восстановился сразу. Это подтверждает классификацию `F`: closure6 loss не является стабильно воспроизводимым A–E defect, а точный исторический first divergence profiles `6028,6029,6032,6033` остаётся недоказанным. Diagnostic run сам по себе PASS, но не может стать финальным acceptance без green verify и standalone final jar.

## Full verify stop condition

1. Первый fresh `ant verify` остановился на независимом predecessor `combat-server-integration.02-canonical-player-ai-attack-and-death`: `19/20`, `Victory cleanup retained the exact dead combat target`.
2. Разрешённое TASK §16 одно focused confirmation `ant phantom-combat-server-integration-test` прошло `20/20 PASS`; failure классифицирован как transient, после чего разрешён ровно один full repeat.
3. Единственный повтор `ant verify` остановился на втором независимом predecessor `acquisition-manor-active.after-all`: `2/3`, `Cleanup retained live Player futures. Expected <[]> but was <[Player._skillListTask]>`.

По обязательному stop condition TASK §16 второй независимый predecessor blocker завершает closure как `BLOCKED`: дальнейшие verify/rerun/fix loops запрещены. Standalone final `ant jar` не запускался. Более ранний diagnostic jar и jar, собранный зависимостью verify, не считаются standalone final jar.

## Safety, cleanup и изменённые файлы

- Production `l2jmobiush5` не probe/read/cleanup; `prepare-phantom-test-db` не выполнялся; wildcard/global Java kill не применялся.
- Real run использовал только allowlisted test DB и завершил cleanup без forced termination и orphan processes.
- Production: `PhantomSystem.java`, `PhantomMaterializationServiceActivityPort.java` — только bounded diagnostics wiring/capture, без semantic materialization fix.
- Tests: `PhantomPopulationEcologyProductionGoal033Suite.java`, `PhantomProductionMaterializationSuite.java`, `PhantomBlackBoxLocalStackGoal034.java`.
- Docs: этот report, `PHANTOM_CURRENT_STATUS.md`, `NEW_DIALOG_START_MESSAGE.txt`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `PHANTOM_BOTS_ROADMAP.md`.
- User-owned `PhantomMaterializationService.java`, `PhantomClanDirectiveIntegrationGoal030C2ASuite.java`, `PhantomMultipartyEconomySuite.java`, closure7 package и unrelated untracked artifacts не изменялись и не staged этой closure.
- Mojibake-маркеры в изменённых файлах проверены отдельным exact-scope scan: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельным exact-scope scan: совпадений нет.

## Git и следующий explicit resume

- Bounded Git inspection использован только по TASK: fetch/rev-parse/branch/status, exact-path diff/diff-check и final commit verification. Reset/restore/checkout/clean/rebase/merge/amend/stash не выполнялись.
- BLOCKED commit subject: `phantom(goal-034): record closure 7 blocker`; commit выполняется только exact allowlist из десяти files и отправляется non-force в `origin/feature/phantom-world`.
- Следующий explicit Goal034 resume должен начать с этой closure7 evidence и отдельно восстановить green predecessor/full-verify baseline; preserved real run не следует переинтерпретировать как SUCCESS без обязательного final jar/acceptance chain.
- Goal035 остаётся `NOT_STARTED`.
