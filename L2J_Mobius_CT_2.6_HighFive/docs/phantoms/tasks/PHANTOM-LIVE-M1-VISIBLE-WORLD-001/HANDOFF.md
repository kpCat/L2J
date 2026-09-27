# HANDOFF — PHANTOM-LIVE-M1-VISIBLE-WORLD-001

## Exact baseline

Repository:
`C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

GitHub:
`https://github.com/kpCat/L2J`

Branch:
`feature/phantom-world`

Required initial HEAD:
`7872e18fbf37177cf08ab23f342a25e3742bb1d0`

If branch or HEAD differs, STOP:
`BASELINE_MISMATCH`

## Accepted facts — DO NOT REAUDIT

The following are accepted and pushed:
- 15/15 progression route proof;
- production locality and real materialization path;
- runtime 1280 GREEN;
- runtime 5000 GREEN + soak;
- functional runtime 10000 GREEN + soak;
- 10000 durable identities READY/linked/unique;
- inventoryReady lost-edge fix;
- bounded creation-stage continuation throughput fix;
- PHANTOM-VISION-AUDIT-001 complete;
- PHANTOM-PLAYTEST-PILOT-001 GREEN.

Do NOT rerun scale gates.
Do NOT rerun pilot implementation/security/ACL investigation.
Do NOT redesign the pilot.
Do NOT repeat the 45-action pilot smoke.

Historical performance fact remains:
`LIVE-003D 45-minute 5000->10000 ramp performance gate NOT MET`.
It is unrelated to M1.

## Current runtime/tooling facts

At the close of the pilot:
- LocalPlay intentionally left RUNNING;
- population=10000;
- ActiveTarget=64;
- MaxMaterialized=128;
- scheduler pulse=100 ms;
- TestAdmin is a dedicated REAL level-1 QA character;
- TestAdmin is NOT GM;
- TestAdmin is NOT added to PersonalCharacterQoL allowlist;
- LocalPlay Pilot uses an 8-character human arm code;
- pilot connected STATUS/pose/move/teleport/idle/reuse are GREEN;
- chat/invite were NOT sent because the pilot snapshot reported `NO_CANDIDATE`;
- connected visual motion was not claimed as CLIENT_OBSERVED.

Re-use current LocalPlay Pilot. Only a tiny read-only pilot extension is allowed if existing pilot evidence cannot expose the already-existing phantom state required by M1. No second automation framework.

## User-visible symptom

The user personally entered the High Five client as TestAdmin and saw multiple level-1 phantom players around the Gremlin/newbie area.

Observed symptom:
- several phantom characters were visibly present;
- they appeared to stand idle;
- the user did not observe spontaneous normal gameplay;
- earlier short LIVE-003 observation similarly saw several phantoms, one sitting, most apparently idle;
- current materialization uses topology locality, not a measured client-visibility prewarm envelope.

This is the product bug M1 addresses.

## Existing audit conclusions to start from, not re-prove broadly

From PHANTOM-VISION-AUDIT-001:
- `PhantomHumanLocalityControl` refreshes every ~1000 ms;
- locality is based on topology `TARGETABILITY`, not explicit camera/known-list distance;
- signal TTL is 3000 ms;
- scheduler has temporal demotion grace, but no proven spatial enter/exit hysteresis;
- materialization ultimately calls native `Player.spawnMe()`;
- therefore a player may already be able to see the point when spawn occurs;
- `ACTIVE/STABLE/WORK_DELIVERED` does NOT prove movement/combat/chat;
- ordinary visible solo-farm Goal production was not found by the audit;
- exact idle root cause of an observed profile was not previously proven.

## Existing observability — REUSE IT

Do not invent a new trace system.

Existing production classes:
- `java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomDecisionReplay.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java`

Existing operator seam in `PhantomSystem` includes selected decision trace selection, snapshot/history and replay capture/run/clear.

The selected trace already contains activity, Goal, candidate, plan, step, attempt, result and reason. Use this before adding diagnostics.

## Relevant implementation/test areas

Likely production touchpoints:
- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java`
- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java`
- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyQuery.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationServiceActivityPort.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`
- `java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java`
- `java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`

Focused suites already present:
- `PhantomTopologySchedulerSignalIntegrationSuite`
- `PhantomTopologyPerceptionSuite`
- `PhantomTopologyCoreSuite`
- `PhantomActivitySchedulerSuite`
- `PhantomProductionMaterializationSuite`
- `PhantomProductionMaterializationPerformanceSuite`
- `PhantomDecisionCoreSuite`
- `PhantomFarmingSuite`
- `PhantomBackgroundSuite`
- `PhantomBlackBoxLocalStackGoal034`
- LocalPlay pilot suites as needed.

Do not run full verify.
