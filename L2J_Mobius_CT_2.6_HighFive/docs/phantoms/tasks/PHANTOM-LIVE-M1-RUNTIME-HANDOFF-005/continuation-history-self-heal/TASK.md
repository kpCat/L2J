# PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005 — continuation-history-self-heal

## Goal
Close the current pre-arm blocker without creating another proof-only milestone: make the existing 1280-participant world self-heal the three dominant durable `FAILED_REPLAN_REQUIRED` classes, keep the remaining 8720 retired identities/history intact, then perform exactly one representative connected M1 scene.

This is a continuation of task 005, not M2.

## Pinned baseline
- Repository: `kpCat/L2J`
- Branch: `feature/phantom-world`
- Evidence/docs HEAD when this package was prepared: `b80cdf78560c06a9d88b8ec761e1582f0e55afba`
- Deployed code reported by previous continuation: `ca2dbc753106d165dc73a1b03ba89aadc69f917e`
- LocalPlay intended runtime: population target `1280`, active `64`, materialized cap `128`, scheduler pulse `100 ms`, `MaxScheduledPhantomProfiles=10000` only as preserved inventory capacity.

Before editing, verify `origin/feature/phantom-world`. If code moved beyond the pinned baseline, compare only the files in SOURCE_MAP. Preserve later compatible changes; stop only for a real semantic conflict.

## Observed PLAY blocker
Read-only PLAY snapshot from the previous task:
- population: `1280 READY + 8720 RETIRED`;
- active historical statuses: `COMPLETE=303`, `RUNNING=387`, `FAILED_REPLAN_REQUIRED=590`;
- failures: `model.object_cap_indivisible=354`, `catchup.authority.unsupported=121`, `planner.target_or_route.absent=114`, stale=1.

These are canonical model/content failures, not evidence that 1280 native Players exist. Do not increase population and do not restore target 10000.

## Required implementation
Implement DESIGN.md as one vertical. The executor is not being asked to rediscover the architecture.

### A. Ordinary farm object-cap is not a permanent character failure
`PhantomBackgroundModel` has an internal mutation envelope (`MAX_CHANGED_ITEM_OBJECTS`, `MAX_NEW_NON_STACKABLE_OBJECTS`). For ordinary death-drop farming only, a single encounter that would exceed that internal envelope must not permanently fail historical life.

Implement deterministic bounded overflow handling:
- preserve the encounter, RNG progression, XP/SP, death/vitals, resource consumption and existing inventory facts;
- keep collectible ordinary drops that fit the existing mutation envelope;
- move only the excess ordinary loot to bounded `groundLosses` in stable drop order;
- never spill an acquisition target, manor/quest target, shot/summon resource consumption or any required transaction input;
- acquisition modes retain their stricter existing contract;
- do not raise the existing model mutation caps merely to make tests pass.

Old persisted `FAILED_REPLAN_REQUIRED(model.object_cap_indivisible)` must be resumable on the same request/cursor after this behavior exists. No history reset, no cursor jump, no double reward.

### B. Stop collapsing farm authority failures into one opaque string
Add a typed ordinary-farm input attempt at the existing `PhantomBackgroundAuthority` boundary (additive API; do not create a second authority service). `L2jPhantomBackgroundAuthority` must distinguish at least:
- `AUTHORITY_STALE` — generation/hash changed;
- `POSITION_STALE` — persisted anchor/position no longer canonical;
- `TARGET_STALE` — NPC/spawn facts no longer authoritative;
- `RESOURCE_STALE` — persisted shot/summon/spoil capability contract no longer matches durable character state;
- `UNSUPPORTED_LOOT` — native item/drop policy cannot be represented by the bounded model;
- `UNKNOWN` only for an actually unclassified runtime exception.

`PhantomBackgroundService` must consume the typed result. Do not map every exception to `catchup.authority.unsupported`.

Recovery policy:
- authority/hash stale -> existing generation/canonical refresh path;
- position stale -> existing canonical baseline refresh, then retry;
- target stale / unsupported loot -> replan while excluding the current `npcId@anchorId`;
- resource stale -> replan from the current durable loadout (`replaceFromState` semantics), not the old persisted resource contract;
- UNKNOWN remains an explicit blocker with evidence; do not silently mark complete.

Old persisted `catchup.authority.unsupported` is a legacy recoverable reason: on first retry, re-evaluate through the typed attempt above and take the corresponding branch.

### C. Planner must degrade intelligently when ±2 has no reachable target
Do not add spot-specific XML, race-specific tables or hand-written 1..85 routes.

Keep strict preference `level ±2`, then use bounded deterministic fallback tiers over existing knowledge/topology/travel facts:
1. `[max(1,L-2), L+2]`
2. `[max(1,L-5), L+2]`
3. `[max(1,L-10), L+2]`

Within all tiers retain existing ordering: closest level first, then route cost/length, deterministic tie-break, NPC/anchor. Reuse `PhantomNormalGatekeeperTravel`; no teleport shortcuts.

When a target-specific execution failure is being repaired, exclude the current `npcId@anchorId` for that replan attempt.

If no reachable target exists even after the final tier, this is not permission to corrupt or freeze history. Add one canonical **historical idle** operation using the existing background transaction/catch-up ownership:
- advances exactly the catch-up minute/cursor;
- changes no XP, SP, inventory, adena, vitals or committed position;
- creates no fake loot or combat receipt;
- is idempotent/replayable like other historical action kinds;
- retries planning on a later productive interval.

Do not call it `DEAD_IDLE` for an alive character. Add a distinct action kind only if the existing operation-key contract requires it.

### D. Recover durable `FAILED_REPLAN_REQUIRED` instead of fencing forever
In `PhantomHistoricalBackgroundService`, before returning a non-stale planned `FAILED_REPLAN_REQUIRED` as terminal, route the known recoverable classes through the policies above:
- `model.object_cap_indivisible` (and legacy `model.object_cap`);
- `catchup.authority.unsupported`;
- `planner.target_or_route.absent`.

Use existing `withPlan(...)`/`retryRunning()` semantics and `_store.replacePlan(...)` so the same request identity, cursor and interval ordinal remain authoritative. No delete/recreate of goal/history.

Unknown/canonical-conflict/inconsistent failures remain blockers. Do not turn every failure into a success.

### E. M1 must not be globally blocked on draining all 1280 histories
The previous task made the entire manual gate wait for system-wide failure count. Remove that policy from the runner/pre-arm preparation.

Before arm, require instead:
- LocalPlay CONFIG/ownership healthy;
- population remains exactly `1280 READY + 8720 RETIRED` (allow transient retirement bookkeeping only during controlled restart; settle before gate);
- targeted recovery mechanisms pass guarded TEST;
- no unknown/inconsistent failure in the candidate selected for connected acceptance;
- selected natural local cohort has enough usable candidates to evaluate useful life (target >=4 when naturally available; otherwise report coverage insufficiency, do not travel the user around to manufacture count);
- demand-driven catch-up for the selected candidate reaches readiness within the prewarm opportunity rather than waiting behind unrelated backlog.

System-wide recoverable failures may continue healing in background after M1. Report their counts before/after, but do not require zero as a prerequisite for the connected gate.

## Verification budget
Do not restart/test after every edit.

Required sequence:
1. one read-first and exact baseline/scope check;
2. write focused regressions for A-D and verify they fail for the intended reason;
3. implement A-D;
4. one focused test run;
5. one aggregate `phantom-m1-runtime-handoff-test` at the end;
6. one clean detached JAR build;
7. one controlled deploy preserving target1280 and the 8720 retired identities;
8. read-only PLAY health/failure-count snapshot;
9. only then request one fresh TestAdmin arm and execute one connected runner.

No 10k scale test. No all-location matrix. No manual race/level tour. No broad 1..85 rerun unless the planner tier change breaks an existing planner test and a single existing broad target is needed to qualify it.

## Connected acceptance
One existing runner, one fresh arm, one scene:
`outside -> physical prewarm -> materialized before native visibility -> useful native travel/farm/AutoPlay -> leave ~15 s -> return -> same Player identity -> automatic TestAdmin restore -> Pilot stop`.

Census the natural local cohort once. A character actively recovering, dead in its allowed revive window, traveling, or in another explicit activity is not fake idle. `ACTIVE farm goal + repeated failed navigation/no native action` remains RED.

If connected RED occurs after these changes: stop and record the actual runtime boundary. Do not create another proof task or start an unbounded audit in the same chat.

## Data / safety
- PLAY database: SELECT/SHOW diagnostics only outside normal server operation. No manual UPDATE/DELETE/INSERT to repair histories.
- Mutating fixtures only through the existing guarded TEST DB gate.
- Do not reset/reseed population.
- Do not delete the 8720 retired profiles or their ecology/history/components.
- Do not change schema, world data, rates, geodata, max materialized cap or scheduler pulse to obtain PASS.
- Do not modify other chronicles.
- Preserve unrelated dirty files.

## Git
Use the permissions already established for this task family: read-only inspection and exact-path add/commit/push only for task-owned files. No `git add .`, reset, clean, stash, rebase or force push.

## Completion
GREEN only after the connected scene. Automated PASS alone is not M1 GREEN.
M2 remains forbidden in this task.
