# Goal 030 Checkpoint 2B — farming utility contract unblock and CP2 resume

## Status

**BLOCKED — `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`**

Исправление farming utility выполнено и покрыто focused regression; existing Goal024A acquisition gate прошёл. Fresh CP2 run 1/2 дошёл до production Population/Scheduler/materialization/autonomous-decision chain, но canonical WHISPER не доставляет сообщение headless Phantom: `ChatWhisper` возвращает offline при `receiver.getClient() == null`, хотя Phantom имеет `HeadlessPlayerOutboundSession`. Это новый production owner defect вне разрешённого CP2B scope, поэтому run 2 не выполнялся.

Goal030 остаётся `IN_PROGRESS`; CP2 остаётся `BLOCKED / IN_PROGRESS`. Matrix сохранена `11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030`.

## Score contract: old → new

- Global contract не менялся: candidate threshold и consideration evaluation остаются exact `0..1000`.
- Было: farming work evaluation `1100`, `minimumAcceptedScore=1100`.
- Стало: единая production constant `CONFLICT_UTILITY_SCORE=1000` используется для work evaluation и minimum threshold.
- `PhantomDecisionCandidate`, `PhantomUtilitySelector`, global tie-break, `PhantomAcquisitionDecision`, `PhantomAcquisitionService`, `PhantomFarmingService` не изменялись.
- Candidate key, goal type, states, plan, handler, retries/timeouts не менялись.

## Focused candidate/evaluation evidence

Target `phantom-farming-decision-utility-goal030cp2b-test`, seed `30003022`, DB-free, forked, timeout `120000 ms`: **PASS**, 1/1, target total 23 s.

Real production evidence:

1. `new PhantomFarmingDecision(fixture.service).registerCandidates(registry)` завершился успешно; registry sealed.
2. Sealed snapshot содержит ровно один exact `candidate.farming.conflict`.
3. Candidate `minimumAcceptedScore == 1000`.
4. No-work real selector evaluation: score `0`, status `BELOW_THRESHOLD`.
5. Deterministic current conflict после реальных `FarmingService.advance` дал `service.hasWork(1)==true`; real `PhantomUtilitySelector` evaluation: score `1000`, status `ELIGIBLE`, selected candidate `candidate.farming.conflict`.
6. В selector передан только real farming candidate, поэтому proof не зависит от lexicographic tie-break или priority tie.

## Goal024A acquisition gate

`phantom-farming-goal024a-acquisition-integration-test`: запущен ровно один раз, seed `24002402`, allowlisted `l2jmobiush5_phantom_test`; **PASS**, 1/1, target total 36 s.

Existing case `acquisition-farming-lifecycle.01-real-goal021-switch-fulfils-old-move-exactly-once` подтвердил frozen Goal021↔Goal024 production ownership/lifecycle gate. Goal024 aggregate и `verify` не запускались.


## Fresh CP2 runtime

Target `phantom-cross-domain-autonomous-alpha-goal030cp2-test`, seed `30003002`, fresh successor budget:

- Run 1/2: **FAIL**, 1/6 passed, 5/6 failed, target total 41 s.
- Run 2/2: **NOT RUN**; разрешён только для одной concrete fixture/API correction, но причина является новым production owner defect.

Causal evidence run 1:

- Population create → Scheduler ACTIVE → materialization/headless/PHANTOM lease/cap → non-Population autonomous decision: **PASS** (`01-production-population-scheduler-materialization`).
- Human `пригласи меня` → real WHISPER delivery: **FAIL** до Party action.
- `где взять адену` ITEM57 response, `покинь группу`, offline/reactivate same identity+memory, canonical shutdown scenario assertions: **not reached as valid causal PASS**; downstream cases failed because no conversation delivery/state existed.
- Harness `afterAll` still shut down ThreadPool and closed HikariCP.

Exact blocker evidence: native `dist/game/data/scripts/handlers/chat/channels/ChatWhisper.java` resolves the materialized Phantom from `World`, then rejects it when `(receiver.getClient() == null) || receiver.getClient().isDetached()`. Headless materialization intentionally has no fake/null network `GameClient`; it owns a `HeadlessPlayerOutboundSession`. Sender receives offline response, no `CreatureSay` is sent to the Phantom, and `ChatObservationService` records no actual counterpart delivery. Fixing the native chat owner/headless delivery contract is outside CP2B and is not silently performed.

## Matrix and final gates

No PASS-only promotion:

- `COVERED_PRIOR = 11`
- `COVERED_CP1 = 6`
- `COVERED_CP2 = 0`
- `PENDING_GOAL030 = 3`

Pending remain: `activity-materialization: CP2`, `restart-failure-recovery: CP3`, `rollback-release-control: CP3`.

- Utility: PASS (frozen).
- Acquisition: PASS (frozen, one invocation).
- CP2: FAIL on production behavior defect (one fresh runtime run).
- CP1 baseline, conversation checkpoint2, Party server integration, production materialization: NOT RUN (PASS-only chain not entered).
- MasterHandler smoke: NOT RUN; script/compiler/handler files were not changed.
- `jar`: NOT RUN, count `0`.
- 030A/B/C, Goal024 aggregate, soak, `verify`, geodata: NOT RUN.


## Changed files

- `java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingDecision.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `docs/phantoms/reports/030-checkpoint-2b-farming-utility-unblock-and-resume.md`
- `docs/phantoms/reports/030-checkpoint-2-cross-domain-autonomous-alpha.md`
- `docs/phantoms/reports/030-checkpoint-2a-java8-handler-unblock-and-resume.md`

User-modified `PhantomReleaseBaselineGoal030Checkpoint1Suite.java` and untracked task packages were preserved and excluded from staging.

## Architecture, DB, config, performance

Production behavior change is limited to the two illegal farming utility values represented by one constant. No decision core/tie-break, acquisition/farming owner, schema/migration, shipped config, dependency, thread or logging change. Utility is DB-free. Acquisition/CP2 used only the guarded phantom test DB; production DB was not used. No performance claim beyond bounded target wall times.

## Commands and process truth

- Exact parent `fc0e5cce104ea633bae1c5d26935d7c0d7ef8db9`, branch `feature/phantom-world`, upstream `origin/feature/phantom-world` confirmed.
- TASK read once; 4 targeted searches before edit.
- `apply_patch`: exactly 1 invocation; ACL-rejected before read/mutation, applied `0`; no retry.
- Successful mutations: exact-anchor UTF-8-no-BOM temp + atomic `Move-Item`. One CRLF anchor precondition mismatch and one PowerShell parser failure applied `0`; corrected fallback commands did not leave partial files.
- Utility invocations: 1/2; acquisition invocations: exactly 1; CP2 runtime invocations: 1/2.
- Required Ant targets each declare `depends="compile-tests"`; consequently compile/compile-tests executed in each of the three target invocations. This is an explicit deviation from requested max-one compile cycle; no standalone/speculative compile was run.
- Context compactions observed: 0.
- Goal counter snapshot before report: 159775 tokens, 638 s.

## Git

- Required parent: `fc0e5cce104ea633bae1c5d26935d7c0d7ef8db9`.
- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- Preferred subject: `fix(phantoms): restore farming decision utility bounds`.
- Commit SHA and push result: returned in the final message because this report is part of the commit.
- No history rewriting or force push.

## Next step

A separate bounded corrective task must define and test the canonical native WHISPER/headless recipient delivery seam without creating a fake/null-network `GameClient`. After independent review, CP2 can resume with a fresh runtime budget. CP3 must not start.

## Successor CP2C outcome

Corrective CP2C на exact parent `7374e5cc3f6bcceeeb48c264585a029cc3fd9c8e` устранил native headless WHISPER blocker одной semantic заменой: direct receiver GameClient predicate → `receiver.isInOfflineMode()`. Focused canonical MasterHandler/headless regression seed `30003023` прошёл 3/3 и доказал exact `CreatureSay.runImpl`/`publishDelivered` path для null-client HEADLESS receiver, а transportless negative сохранил offline rejection.

Fresh CP2C runs 1/2 и 2/2 дошли дальше прежнего blocker: real WHISPER delivery и Population/Scheduler/materialization/autonomy подтверждены. После единственной visibility fixture correction Conversation/Party execution всё равно не создал real invitation, ITEM57 outbound не завершился, execution entries остались in-flight. Итог `BLOCKED_030CP2_PRODUCTION_BEHAVIOR_DEFECT`; CP2/matrix остаются `BLOCKED / 11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030`; final PASS-only gates и jar не запускались.
