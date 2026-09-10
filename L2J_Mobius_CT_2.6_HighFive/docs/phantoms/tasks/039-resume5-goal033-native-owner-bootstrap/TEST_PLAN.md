# Goal039 Resume 5 test plan

## Phase 0 — precondition

Require:
`HEAD == origin/feature/phantom-world ==
8e77b4b94e0a58eafac29504a2845a96b02a3e51`

Branch:
`feature/phantom-world`.

Record the CURRENT dirty tree after this package is extracted.
Preserve all user-owned tracked/untracked files.

No reset/restore/checkout/stash/clean/rebase/merge/amend/force.

## Phase 1 — read-first confirmation

Read:
- `Agents.md`
- this package
- current Goal039 report/matrix
- production `GameServer` script/Phantom ordering
- `PhantomHeadlessPlayerTestEnvironment`
- `PhantomPopulationEcologyProductionGoal033Suite`
- `PhantomQuestInstanceGoal036Suite`
- current supported-content catalog
- `PhantomQuestInstanceCatalog.validateRuntime`
- `PhantomQuestInstanceService.start`

Confirm the root-cause facts from CONTEXT.md before edit.

## Phase 2 — one baseline reproduction

In a clean no-geodata exact-parent candidate, run:

`phantom-population-ecology-production-goal033-test`

once.

Require the known Q401 profession-owner diagnostic.

Do not spend extra retries before correction.

## Phase 3 — implement test-only helper

Expected code changes for blocker:
1. new test-only helper;
2. Goal036 suite delegates existing bootstrap to helper;
3. Goal033 production suite invokes helper once before first PhantomSystem start.

Production changes expected: 0.

If production code/data/config/build needs modification, STOP/BLOCKED before it.

## Phase 4 — bootstrap proof

Prove:
- generic headless remains EffectMaster-only;
- helper loads all seven exact supported owner identities;
- current supported-content runtime validation passes;
- helper uses the accepted bounded sequence;
- no full `executeScriptList()`;
- no mock owners;
- helper invoked once per suite JVM;
- Goal033 PhantomSystem restart does not rerun it.

Do not deliberately bootstrap twice in one JVM just to test duplication.

## Phase 5 — focused affected gates

Clean no-geodata candidate, minimum:

1. Goal036 focused.
2. Goal037 native non-1x mode.
3. Goal033 production-composed (both tests).
4. Goal033 focused.
5. Goal033A.
6. Goal033A1.
7. Background position canonicalization.
8. Goal021 acquisition catalog/current.
9. Goal021 Q102/Q152 ACTIVE.
10. Goal021 Q102/Q152 BACKGROUND.
11. Goal021 restart/atomic.
12. Goal037 static.
13. Goal039 static/safety.
14. DB negative guard.
15. Goal032 reset/reseed directly affected route.

If local external geodata remains available:
- Goal033A1 geodata-present PASS;
- Background position geodata-present PASS.

`prepare-phantom-test-db` must not run.

## Phase 6 — cleanup proof

Goal033 production must retain its existing cleanup guarantees:
- no configured PhantomSystem after shutdown;
- owned `phantom_profiles` zero;
- owned `phantom_profile_components` zero;
- headless fixture residue zero;
- infrastructure threads/pools stopped according to existing harness contract.

Record loaded owners and helper invocation count.

## Phase 7 — resume original Goal039

Only after focused green, use CLEAN candidate and continue:

1. final Goal039 domain aggregate:
   - Goal033 lineage
   - Goal035
   - Goal036
   - Goal037
   - Goal038
   - Goal029 scale/environment/endurance
   - Goal030 rollback/release control
   - shipped-disabled regression
2. one fresh full `ant verify`
3. standalone final `ant -q jar`
4. one fresh Goal034 real local-stack acceptance using THAT clean-candidate JAR
5. final docs/freeze consistency
6. commit/push

No production Java/data/config/build change after final JAR.

## Stop budget

Resume 5 may fix only:

`GOAL039_RESUME4_GOAL033_HEADLESS_QUEST_OWNER_NOT_LOADED`

If a NEW independent blocker appears:
- one focused confirmation;
- STOP/BLOCKED;
- do not fix that family in Resume 5;
- no Goal040.

## Report

Update:
`docs/phantoms/reports/039-final-full-vision-release-gate.md`

Record:
- root cause
- production changes count
- helper path
- exact script sequence
- seven owner identities
- helper invocation count
- Goal036/Goal037-native results
- Goal033 production and cleanup
- remaining Goal039 evidence

Historical reports stay historical.

On ACCEPT:
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`
and no Goal040.

On BLOCKED:
marker absent and no Goal040.
