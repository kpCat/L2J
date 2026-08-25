# Goal 030 Checkpoint 2A — Java 8 handler unblock and CP2 resume

## Status

**BLOCKED — `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`**

Java 8 blocker canonical `MasterHandler` устранён, targeted smoke прошёл. Возобновлённый CP2 run 1/2 остановился в `beforeAll` до `PhantomSystem.startConfiguredForTesting`: production owner `java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingDecision.java:49` передаёт `minimumAcceptedScore = 1100`, тогда как `PhantomDecisionCandidate` разрешает только `0..1000`. Это не fixture/API defect, поэтому production behavior не изменялось, run 2 не выполнялся.

Goal030 остаётся `IN_PROGRESS`; CP2 остаётся `BLOCKED / IN_PROGRESS`. Coverage/matrix не повышались, PASS-only gates и `jar` не запускались.

## Summary

- Required parent/HEAD: `bbbe7bfd86f2ef87fc61d346c818c730fcc3c0dc`.
- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- Authoritative `docs/PHANTOM_BOTS_ROADMAP.md` reconciled: Goal030C2A `ACCEPT`, Goal030C2B `ACCEPT`, CP2 `BLOCKED / IN_PROGRESS`, Goal030 `IN_PROGRESS`.
- `ScriptExecutor.OPTIONS` подтверждён без изменений: `-source 1.8`, `-target 1.8`.
- `MasterHandler` и `ChatWhisper` не изменялись; direct `ChatWhisper` execution не использовался.
- Existing `PhantomSystem` test-start seam и CP2 scenario scaffolding оставлены без изменений.

## Java 8 compatibility change

В `AdminPhantom.java` заменены ровно шесть `final var` на exact compile-time types:

1. `replay` → `PhantomDecisionReplay.ReplayResult` (`ReplayResult` import).
2. `states` → `List<Long>`.
3. `current` → `PhantomEconomicAuditView.CurrentOperation`.
4. `summary` → `PhantomEconomicAuditView.RetainedSummary`.
5. `audit` → `PhantomEconomyReservationService.AuditRecord`.
6. `receipt` → `PhantomEconomicAuditView.ReceiptView`.

`Object`, raw types, casts, strings, control flow, API и behavior не менялись. `var` в `AdminPhantom.java` не осталось.

## Source 8 / MasterHandler / WHISPER proof

Target `phantom-master-handler-java8-compat-goal030cp2a-test`, seed `30003021`, forked cwd `dist/game`, timeout `120000 ms`, DB-free:

- `ConfigLoader.init()` загружает canonical datapack config.
- Accepted bootstrap `EffectHandler.getInstance().executeScript()` регистрирует 165 effect handlers и предотвращает legacy warning flood.
- Suite выполняет `ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE)`.
- Canonical source closure успешно компилируется `ScriptExecutor` с `-source 1.8 -target 1.8`.
- `handlers.MasterHandler.main` завершён; `ChatHandler` сообщает 14 handlers.
- `ChatHandler.getHandler(ChatType.WHISPER)` существует.
- Exact runtime class: `handlers.chat.channels.ChatWhisper`.
- Итог: `PASS`, 1/1 tests, total target time 28 s.

## Resumed CP2 runtime

Fresh successor budget used: **1 of maximum 2 runtime runs**. Parent attempts do not count.

Run 1:

- target: `phantom-cross-domain-autonomous-alpha-goal030cp2-test`;
- seed: `30003002`;
- guarded DB: `l2jmobiush5_phantom_test`;
- schema aggregate SHA-256: `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`;
- headless bootstrap: 39 initialized and 5 transitive singletons;
- primary/observer object IDs: `268435465` / `268435467`;
- canonical MasterHandler completed before PhantomSystem start;
- result: `FAIL before-all`, `IllegalArgumentException: Minimum accepted score must be between 0 and 1000.`;
- exact owner evidence: `PhantomFarmingDecision.java:49` supplies `1100` as `minimumAcceptedScore` to `PhantomDecisionCandidate`;
- framework cleanup: ThreadPool shut down and HikariCP closed.

Run 2: **NOT RUN**. The only permitted second-run correction is a concrete fixture/API fix. Changing the production farming candidate threshold/score contract would be a behavior fix outside CP2A scope, so the required stop status is `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`.

## Causal contract evidence

The causal product chain was not reached and is not claimed:

- Population identity → Scheduler/materialization: not reached after production candidate registration failure.
- Autonomous non-population decision: not reached.
- `пригласи меня` → real Party/social: not reached.
- `где взять адену` → ITEM57 generated response: not reached.
- `покинь группу` → real leave: not reached.
- offline/reactivate same identity+memory: not reached.
- canonical shutdown/exact cleanup: system owner never started; harness cleanup completed.

## Matrix and release gates

No PASS-only promotion was performed. Matrix truth remains:

- `COVERED_PRIOR = 11`;
- `COVERED_CP1 = 6`;
- `COVERED_CP2 = 0`;
- `PENDING_GOAL030 = 3`.

Pending rows remain `activity-materialization: CP2`, `restart-failure-recovery: CP3`, `rollback-release-control: CP3`. The requested post-PASS counts `11 / 6 / 1 / 2` were not applied.

Final gates:

- MasterHandler Java8 smoke: `PASS` and frozen.
- CP2: `FAIL before-all` on production behavior defect.
- CP1 baseline: `NOT RUN` (PASS-only chain not entered).
- conversation checkpoint2: `NOT RUN`.
- Party server integration: `NOT RUN`.
- production materialization: `NOT RUN`.
- `jar`: `NOT RUN`, count `0`.
- 030A/B/C reruns, soak, aggregate, verify and geodata: not run as required.

## Commands and results

- `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`: exact parent/branch/upstream confirmed; unrelated user changes inventoried and preserved.
- PATH `ant phantom-master-handler-java8-compat-goal030cp2a-test`: infrastructure failure, `ant` absent from PATH; no test started.
- Bundled IntelliJ Apache Ant was used through `org.apache.tools.ant.launch.Launcher`.
- Smoke invocation 1: compile-tests FAIL, new suite method lacked declared checked `Exception`; fixture-only signature fixed.
- Smoke invocation 2: FAIL `ExceptionInInitializerError`; DB-free `ConfigLoader.init()` bootstrap added.
- Smoke invocation 3: MasterHandler source8 compilation succeeded but timed out at 120 s because missing effect handler registration caused legacy warning flood.
- Smoke invocation 4: PASS after reuse of accepted `EffectHandler.getInstance().executeScript()` bootstrap.
- CP2 successor runtime run 1: FAIL before `PhantomSystem` start on production farming threshold `1100`.
- CP2 successor runtime run 2: not run.

## Process truth

- `TASK.md` read once.
- Initial targeted searches before edit: 3; no architecture rediscovery.
- Initial `apply_patch`: exactly 1 invocation, ACL-rejected before read/mutation, applied changes `0`; no retry.
- All successful mutations used exact-anchor UTF-8-no-BOM temp files plus atomic `Move-Item`; failed precondition attempts did not mutate targets.
- Context compactions observed: `0`.
- Goal counter snapshot while preparing report: `268887 tokens`, `1085 s`.
- Git commands used only for task-required parent/branch/status and exact diff/scope verification; no history rewriting.

## Changed files

- `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java`
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomMasterHandlerJava8CompatibilityGoal030CP2ASuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/reports/030-checkpoint-2a-java8-handler-unblock-and-resume.md`
- `docs/phantoms/reports/030-checkpoint-2-cross-domain-autonomous-alpha.md`

`ScriptExecutor`, `ScriptEngine`, `MasterHandler`, `ChatWhisper`, production `PhantomSystem`, CP2 suite, release matrix/validator and user task packages were not changed. Existing user modification in `PhantomReleaseBaselineGoal030Checkpoint1Suite.java` was preserved and excluded from task staging.

## Architecture, DB, config, performance

Production behavior, APIs, control flow, database schema/migrations, shipped config and dependencies were not changed. The smoke is DB-free; its legacy admin initializer logs missing DB/ThreadPool but completes in 28 s. CP2 used only the dedicated allowlisted test database and closed its pool. No CP2 performance claim is made because failure occurred before `PhantomSystem` startup.

## Git and next step

- Preferred subject: `fix(phantoms): unblock autonomous world alpha`.
- Commit SHA: current task commit; exact SHA is returned in the final message because the report itself is part of the commit.
- Push: performed after final diff/encoding checks; exact result is returned in the final message.
- Required next bounded task: decide and test the production farming candidate score/threshold correction so both evaluation and `minimumAcceptedScore` obey the canonical `0..1000` contract, then resume CP2 with a fresh runtime budget. CP3 must not start.

## Successor CP2B outcome

Corrective CP2B на exact parent `fc0e5cce104ea633bae1c5d26935d7c0d7ef8db9` устранил farming utility blocker (`1100/1100` → `1000/1000`) без изменения global decision core/tie-break. Focused DB-free utility regression и existing Goal024A acquisition integration gate прошли.

Fresh CP2B runtime run 1/2 прошёл первый production causal case до materialization/autonomous decision, но human WHISPER не был фактически доставлен: native `ChatWhisper` отвергает headless receiver при null `GameClient`, хотя у Phantom attached `HeadlessPlayerOutboundSession`. Это новый production owner defect, а не fixture/API correction; run 2 не выполнялся. Итог CP2B: `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`. CP2/matrix остаются `BLOCKED / 11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030`; final PASS-only gates и jar не запускались.
