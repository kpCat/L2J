# Goal034 closure 5 — restart ecology-fence parity

## Baseline
Это bounded closure существующего Goal034, не новый Goal035.

- Branch: `feature/phantom-world`
- Required parent/HEAD/origin: `79184f2ed8ed4d3d787ac70e9e306b45c6893e7e`
- Module: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Previous report: `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-schedule-aware-closure.md`
- Goal035 НЕ начинать.

Parent уже доказал:
- Goal034 focused 17/17;
- Goal033 ecology 8/8;
- DB negative guard PASS;
- full `ant verify` 3/3 PASS;
- standalone `ant jar` 3/3 PASS;
- real LS/GS READY+registered;
- gen1 desired/expected/online=5/5/5;
- native restart + Phantom drain PASS;
- gen2 desired/expected/online=5/5/4, subset=true;
- cleanup/integrity/DB safety PASS.

## Known restart gap
Перед production patch сначала доказать focused regression.

Source sequence:
1. `PhantomPopulationManager.start()` restores READY profiles while ecology rows are still loading.
2. Until ecology permits scheduling, READY schedule processing can publish effective SLEEPING.
3. Ecology rows load through bounded `onPopulationPulse()`.
4. `PhantomPopulationEcologyService.publish()` calls `refreshInventoryLocked()` immediately.
5. The last loaded row can make `_inventoryReady=true` inside the processing loop.
6. End-of-pulse `inventoryBecameReady` detection can then miss the false→true edge.
7. Existing `ecologyFenceChanged(profileId)` calls are tied to catch-up/calendar operations, not one owner of every scheduling-permission edge.

This is consistent with cold gen1 PASS and restored gen2 4-of-5.
Do NOT force online=5 or alter admission/schedules to hide the gap.

## Goal
1. Focused-reproduce restart restore of existing ecology rows.
2. Implement the smallest bounded scheduling-permission edge fix.
3. Preserve missing-profile evidence on black-box FAIL before cleanup.
4. Fresh verify/jar.
5. Real gen1→restart→gen2.
6. Goal034 SUCCESS only if gen2 parity closes.

## Safety
Allowed only:
- DB `l2jmobiush5_phantom_test`;
- `127.0.0.1`/`localhost`;
- port `3308`;
- user `l2j_phantom_test`;
- canonical guard-approved mysql/mariadb URL.

Production `l2jmobiush5` forbidden even for probe/read/cleanup.
`prepare-phantom-test-db` forbidden.
No wildcard/global Java kill.
No schema change.
No forced exact-five semantics.

## READ_SET
Before patch:
1. closure4 report;
2. `PhantomPopulationEcologyService.java`;
3. `PhantomPopulationManager.java` — startup/reconcile/schedule refresh only;
4. `PhantomPopulationEcologyGoal033Suite.java`;
5. `PhantomBlackBoxLocalStackGoal034.java` — fail evidence only;
6. `PhantomMaterializationService.java` + activity adapter read-only unless evidence points there.

Searches <=3 before focused reproduction.

## Phase A — reproduce BEFORE fix
Add a test modeling restart of EXISTING durable managed ecology rows.

Fixture:
- 10 READY population snapshots;
- persisted ecology components already initial-catch-up complete;
- `register()` makes inventory initially not ready;
- load through real bounded `onPopulationPulse()` limits;
- PopulationEvents records `reconcilePopulation()` and `ecologyFenceChanged(profileId)`;
- at least 5 schedules ACTIVE at fixed instant.

Parent must demonstrate the gap:
- profiles can initially be schedule-fenced while inventory loads;
- once inventory/calendar permits scheduling, every eligible READY profile must eventually receive the refresh needed to reopen scheduling;
- current parent should FAIL this exact assertion before production fix.

Do not weaken existing Goal033 tests.

## Production invariant
Fix scheduling permission as a bounded EDGE.

Required behavior:
- Ecology owns whether each profile currently permits scheduling.
- false→true or true→false permission changes produce the needed `ecologyFenceChanged(profileId)`.
- restart restore eventually reopens every MANAGED/initial-complete/no-request eligible profile.
- beginning catch-up closes fence;
- completing catch-up reopens fence;
- duplicate/no-op pulses do not spam refresh forever.
- no full O(N) burst at inventory-ready transition.

Preferred implementation: small per-entry in-memory last-published permission state, evaluated through existing bounded ecology `_due` processing. No new thread/executor/timer/future.

If a smaller implementation proves the same invariant, document it.

After fix:
- new restart-restore regression PASS;
- existing Goal033 ecology suite PASS.

## Wrong-layer fixes forbidden
Do NOT:
- force `online=5`;
- special-case gen2;
- edit schedule XML;
- inject DB online flags;
- increase wait time as the primary fix;
- add permanent admin/test API;
- treat transient materialization failure as SUCCESS;
- change Scheduler admission quota without evidence.

## Black-box failure evidence
Before cleanup, any parity failure must persist:
- acceptance instant;
- desiredActiveIds;
- actualOnlineIds;
- `missingDesiredIds = desiredActiveIds - actualOnlineIds`;
- unexpectedOnlineIds;
- for each missing profile: `profileId, characterObjectId, scheduleTemplate, phaseMinutes, homeRegion, desiredState, online`;
- exact materialization/scheduler failure category only if existing logs/evidence provide it.

Artifacts survive cleanup. No passwords/secrets.

## Validation budget
Known ecology-fence fix does not count as a new blocker.

Phase A:
- reproduction FAIL on parent;
- fix;
- focused regression PASS;
- Goal033 ecology PASS;
- Goal034 contract PASS;
- DB negative guard PASS.

Phase B:
- one fresh `ant verify`;
- one repeat allowed after one new predecessor blocker;
- second independent predecessor blocker => BLOCKED;
- after green verify: one standalone final `ant jar`.

Phase C:
- real Goal034 black-box;
- max 2 new independent Phase-C blockers after known fence repair;
- exact evidence → minimal fix → focused regression → honest verify/jar cycle if production changed → repeat black-box;
- third new blocker => BLOCKED.

## Expected real-stack semantics
At each generation:
`expectedAdmitted=min(activeTarget=5,maxMaterialized,desiredActiveCount)`.

Require:
- managed=10;
- READY/MANAGED terminal;
- unique ownership;
- actualOnline count == expectedAdmitted;
- actualOnline subset desiredActive;
- no non-ACTIVE online.

Gen1/gen2 online sets may differ only across genuine schedule boundaries.
Durable identity/ecology fingerprint must stay stable.

## Restart
Keep sandbox all-days restart:
- `ServerRestartScheduleEnabled=True`;
- `ServerRestartDays=1,2,3,4,5,6,7`;
- bounded future HH:mm;
- observed scheduled instant validated before wait.

Require native restart, normal Phantom drain, LS continuity, no force kill on SUCCESS.

## Cleanup/integrity
PASS/FAIL:
- exact population cleanup;
- registration cleanup;
- exact child cleanup;
- no orphan PID/ports;
- forced=false on SUCCESS;
- working integrity=true;
- canonical data fingerprint unchanged;
- production DB unused.

## Docs/report
On SUCCESS update current Roadmap v4/current status/handoff: Goal034=`SUCCESS`.
Historical BLOCKED reports immutable.

Create:
`docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-success.md`

On BLOCKED create bounded resume with missing-profile evidence.

Report <=160 lines:
- parent/status;
- focused reproduction before/after;
- fence root cause/fix;
- gen1/gen2 desired/expected/online + missing IDs;
- PIDs/ports;
- restart/drain;
- continuity;
- cleanup/integrity;
- verify/jar/run counts;
- DB safety;
- changed files;
- elapsed/tokens;
- commit/push.

## Out of scope
No Goal035/036/037/038.
No rates/quests/gameplay changes.
No schema redesign.
No new scheduler/thread.
No unrelated user files.

## Git
Before: fetch + HEAD/origin exact required parent.

Allowed:
- bounded status/diff/show/log/rev-parse;
- exact-path add;
- one commit;
- non-force push.

Forbidden:
reset/restore/rebase/merge/amend/force/history rewrite.

SUCCESS subject:
`phantom(goal-034): close restart ecology fence parity`

BLOCKED subject:
`phantom(goal-034): record restart ecology fence blocker`

## SUCCESS
Goal034 becomes SUCCESS only if focused restart restore proves the fence fix, verify/jar green, real gen1 parity, native restart/drain, real gen2 parity, durable identity/ecology continuity, exact cleanup/no orphans/integrity, production DB unused, docs/report/commit/push complete.

After SUCCESS next Goal is Goal035.
