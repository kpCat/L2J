# Goal034 closure 6 — acquisition/manor Player future cleanup

## Статус

`BLOCKED` — известный predecessor blocker `[Player._skillListTask]` детерминирован, исправлен и прошёл focused/full verify. Первый fresh real acceptance выявил одну новую Phase C family: после native restart schedule-aware gen2 не восстанавливает полный admitted `ACTIVE` set. Для неё добавлен focused regression и bounded lifecycle ownership/order fix, но единственный разрешённый real retry снова завершился той же parity family. Бюджет TASK исчерпан; SUCCESS docs и Goal035 не начаты.

- Branch: `feature/phantom-world`.
- Required parent/HEAD/origin до изменений: `d2096a37d1458977c2dd5e81e329c6732e11aa59`.
- Production database не использовалась.

## Read-first, scope и локальный паттерн

- Прочитаны closure6 TASK/CONTEXT/ACCEPTANCE/package manifest, closure5 package/report, module `Agents.md`, root README, relevant roadmap/contract/efficiency docs, build targets/launcher и ближайшие Player/materialization/background/manor owners.
- Parent/root `AGENTS.md`, отдельный module README, code-map и pattern-файлы не найдены; повторный широкий поиск не выполнялся.
- Локальные аналоги: canonical `Player.stopAllTasks()`, `PhantomMaterializedPlayer.cleanup()`, materialization lifecycle port, terminal Future residue assertion, background recovery lifecycle regressions.
- Сохранены текущие base services, DI/lifecycle ports, naming и Java style; `Player.java`, schema, configs, schedules, admission и ecology semantics не менялись.
- Bounded exception: 11 task files — пять code/test files, этот report и пять переданных closure6 package files. Независимые artifact families не смешивались.
- Непроверенным до real run оставался только restart population parity; именно он не прошёл final acceptance.

## Focused reproduction и точный `_skillListTask` edge

- Первый factual `ant phantom-acquisition-manor-active-test` на required parent: `2/2 PASS`; старый failure не объявлялся transient.
- Добавлен deterministic production-backed regression `production-materialization.21-final-player-task-stop-after-lifecycle-rearm` через existing lifecycle port и реальный `Player.sendSkillList()`.
- До production fix regression дал `20/21`, exact failure: `Expected <[]> but was <[Player._skillListTask]>` на final cleanup boundary.
- Доказанный порядок: до cleanup `_skillListTask` live; после initial canonical `Player.stopAllTasks()` — `[]`; на входе `afterStore` после `storeMe()` — `[]`; вызов реального `sendSkillList()` внутри `afterStore` снова arm-ит `[Player._skillListTask]`; без final stop future остаётся live до delete boundary.
- Это причина A: `PhantomMaterializedPlayer` закрывал canonical tasks слишком рано, хотя lifecycle hook законно мог re-arm Player task. General defect `Player.stopAllTasks()` не доказан; `Player.java` не менялся.
- Production fix сохраняет ранний `stopAllTasks()` для quiesce и в `finally` вызывает final canonical `stopAllTasks()` после `beforeStore/storeMe/afterStore`, но до delete/release.
- После fix regression: `21/21 PASS`; после `afterStore`, непосредственно до `deleteMe()` и после `deleteMe()` live Future list равен `[]`.
- Sleep/wait/timeout/filter masking, reflection cancel/null, manor-specific branch и изменение `sendSkillList()` не применялись.

## Focused validation

- `phantom-acquisition-manor-active-test`: `2/2 PASS`.
- `phantom-production-materialization-test`: `21/21 PASS`.
- `phantom-background-lifecycle-test`: `4/4 PASS`.
- `phantom-background-server-integration-test`: `5/5 PASS`.
- `phantom-server-shutdown-handoff-test`: `7/7 PASS`.
- `phantom-population-ecology-goal033-test`: `9/9 PASS`.
- `phantom-black-box-local-stack-goal034-contract-test`: `18/18 PASS`.
- `phantom-db-guard-negative-control`: `1/1 PASS`, expected Java exit `2`, driver loads `0`, connection attempts `0`.

## Full verify и jars

- Fresh full verify после Player cleanup fix: `BUILD SUCCESSFUL`, `22:32`.
- Standalone jar этого состояния: `BUILD SUCCESSFUL`, `0:18`; позже invalidated разрешённой Phase C production-правкой.
- После Phase C fix выполнен новый fresh full verify: `BUILD SUCCESSFUL`, `25:13`.
- Единственный standalone final jar для последнего production state: `BUILD SUCCESSFUL`, `0:14`.
- Итого closure6: два green full verify, каждый после соответствующего production state; повторов после green state не было.

## Первый fresh real acceptance и Phase C regression

Run `20260908-003203-fdb37c7c`:

- gen1 managed/desired/expected/online `10/5/5/5`; desired=actual IDs `5655,5659,5660,5663,5664`.
- Native restart expected=observed `2026-09-07T22:39:00Z`; exit `2`; normal Phantom drain observed.
- gen2 desired/expected/online `5/5/4`; actual `5655,5660,5663,5664`; missing `5659`; unexpected none.
- Остальные gen2 invariants true: subset, readyManaged, catchupTerminal, pendingCatchups=0, unique, ownership, catalogParity, canonicalOnline. Identity/ecology continuity=false.
- Cleanup: population `10`, registration=true, forced=false, orphans.none=true, working.integrity=true.

Новая family локализована в recovery уже существующего scheduler-owned `ACTIVE` materialization:

- До fix recovery повторно вызывал `materialize()`; `beforeMaterialize` с уже `MATERIALIZED` background state возвращал `BACKGROUND_RECONCILIATION_BLOCKED`, а unconditional dematerialize мог снять чужое lifecycle ownership.
- Focused regression `background-recovery-teleport.04-recovery-preserves-preexisting-materialization` до fix: `3/4`, expected `FAIL_GOAL`, actual `RETRY`, reason `recovery.materialization_background_reconciliation_blocked`.
- Bounded fix пропускает duplicate initial materialize, восстанавливает Player через action lease, проходит canonical dematerialize/store и durable `READY` verification, затем возвращает ранее существовавший `ACTIVE` обычным materialize lifecycle.
- Regression после fix: `4/4 PASS`; materialization остаётся `ACTIVE`, background state — `MATERIALIZED`.
- Временный broad five-player diagnostic прошёл `2/2` и был полностью удалён до final scope; он показал, что core manager/materialization restart без recovery не воспроизводит failure.

## Единственный real retry и stop condition

Run `20260908-014127-c0932b31`:

- gen1 managed/desired/expected/online `10/5/5/5`; desired=actual IDs `6024,6028,6029,6032,6033`; missing/unexpected none.
- Native restart configured `2026-09-07T23:41:57.124154200Z`; expected=observed `2026-09-07T23:48:00Z`; exit `2`; Phantom drain=true (`35 ms`); LoginServer continuity retained.
- gen2 desired/expected/online `5/5/1`; actual `6024`; missing `6028,6029,6032,6033`; unexpected none.
- Missing evidence: `6028/268485590/evening/-9/home 910`, `6029/268485591/evening/+5/home 914`, `6032/268485623/evening/-24/home 915`, `6033/268485625/evening/-25/home 914`.
- Остальные gen2 invariants true: subset, readyManaged, catchupTerminal, pendingCatchups=0, unique, ownership, catalogParity, canonicalOnline. Identity/ecology continuity=false.
- Cleanup: population `10`, registration=true, forced=false, orphans.none=true, working.integrity=true.
- Canonical/sandbox data fingerprint одинаков: `f793178f4ab857bcf7f260a7d8db21d086980e9e6c1dc5f61f9caf12eb28395d`.
- Artifact: `.phantom-local/blackbox/goal034/20260908-014127-c0932b31/artifacts/manifest.properties`; missing profiles сохранены до cleanup, secrets отсутствуют.
- Это повтор той же schedule-aware post-restart parity family после её единственного bounded fix/retry. Новый real run или следующая production-правка запрещены Phase C budget, поэтому closure6 обязана остановиться `BLOCKED`.

## Safety, cleanup и изменённые файлы

- Использовалась только guarded DB `127.0.0.1:3308/l2jmobiush5_phantom_test`, user `l2j_phantom_test`; `database.production.used=false`.
- Production `l2jmobiush5` не probe/read/cleanup; `prepare-phantom-test-db` не выполнялся; wildcard/global Java kill не применялся.
- Production: `PhantomMaterializedPlayer.java`, `PhantomBackgroundService.java`.
- Tests: `PhantomHeadlessPlayerTestEnvironment.java`, `PhantomProductionMaterializationSuite.java`, `PhantomBackgroundSuite.java`.
- Docs: этот report и closure6 task package (`TASK/ACCEPTANCE/CONTEXT/PACKAGE_MANIFEST/CODEX_LAUNCHER`).
- `PhantomMaterializationService.java`, `PhantomClanDirectiveIntegrationGoal030C2ASuite.java`, `PhantomMultipartyEconomySuite.java` и unrelated untracked artifacts принадлежат пользователю и не изменялись/не staged этой closure.
- Current Roadmap v4/status/handoff и historical reports не менялись; Goal034 остаётся `BLOCKED`, Goal035 — `NOT_STARTED`.
- Mojibake-маркеры в изменённых файлах проверены отдельным exact-scope scan: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельным exact-scope scan: совпадений нет.
- `git diff --check` по code/test scope: PASS; line-ending warnings не являются whitespace errors.

## Git и resume

- Bounded Git inspection использован только по TASK: fetch/rev-parse/branch/status, parent show и exact-path diff/diff-check; reset/restore/checkout/clean/rebase/merge/amend/stash не выполнялись.
- BLOCKED commit subject: `phantom(goal-034): record closure 6 blocker`; exact SHA и non-force push result будут в final handoff, amend не выполняется.
- Следующий resume должен начинаться с preserved gen2 evidence и отдельно детерминировать post-restart loss четырёх scheduler-admitted materializations; новый real run возможен только по новой explicit task.
- Usage snapshot перед report: `757628` tokens, `7994` seconds.
- Goal035 не начат.
- Roadmap v5 sync deferred to next documentation-only task.
