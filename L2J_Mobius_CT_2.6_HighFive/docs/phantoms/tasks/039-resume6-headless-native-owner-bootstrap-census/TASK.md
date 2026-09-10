# Goal039 Resume 6 — close legacy headless native-owner bootstrap family

Required parent: `eaac00e0cbdd3a6d076e4d9290ab4760e4c1cbf0`
Branch: `feature/phantom-world`
Module: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Primary blocker: `GOAL039_RESUME5_GOAL032_HEADLESS_QUEST_OWNER_NOT_LOADED`.
Broader proven family: `LEGACY_HEADLESS_FULL_PHANTOMSYSTEM_MISSING_SUPPORTED_OWNER_BOOTSTRAP`.
This is Goal039 Resume 6, NOT Goal040.

## Goal
Close the complete TEST-only family where an older suite initializes `PhantomHeadlessPlayerTestEnvironment` (EffectMaster-only) and directly starts modern `PhantomSystem.startConfiguredForTesting(...)` without Goal036 native-owner prerequisites. Then resume Goal039.

## Precondition
Require `HEAD == origin/feature/phantom-world == eaac00e0cbdd3a6d076e4d9290ab4760e4c1cbf0` and exact branch. Record CURRENT dirty tree after package extraction and preserve all user-owned paths.

## Census before edit
Search TEST sources for every `PhantomSystem.startConfiguredForTesting(` call and classify per `CENSUS_CONTRACT.md`.
Known same-family minimum: Goal032 reseed + ownership, Goal031 readiness, Goal030 CP3 restart + rollback, Goal030 CP2 cross-domain. Goal033 is already fixed; Goal036 is reference.

## Allowed correction
Reuse existing TEST-only `PhantomSupportedContentScriptBootstrap.loadGoal036Owners(context)` exactly once per affected forked suite JVM, after headless initialize and before first full PhantomSystem start. Never invoke it from `startRuntime()`/restart methods. For CrossDomain CP2 replace its old direct `MASTER_HANDLER_FILE` setup with helper; do not execute both.

A small verify-owned TEST-only structural guard may be added/extended.

## Production scope
Expected production changes: 0. Do not change PhantomSystem, GameServer, generic headless environment, validateRuntime, gameplay/scripts/catalogs/hashes/pins/topology, or DB schema/data. Do not add `executeScriptList()` or fake owners. If production correction seems necessary, STOP/BLOCKED.

## Validation / continuation
Run `TEST_PLAN.md`. Missing bootstrap in another census-proven same-family seam is in scope. A NEW independent family gets one focused confirmation then STOP/BLOCKED.

When green continue original Goal039: final domain aggregate -> scale/endurance -> rollback -> fresh verify -> standalone final jar -> fresh Goal034 real stack on THAT clean jar -> freeze.

Production `l2jmobiush5` forbidden even read/probe. `prepare-phantom-test-db` forbidden.

SUCCESS subject: `phantom(goal-039): freeze declared full vision`
BLOCKED subject: `phantom(goal-039): record resume 6 blocker`
Exact-path stage, one normal commit, non-force push. No Goal040.
