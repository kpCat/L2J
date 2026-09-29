# Implementation plan

## Task 1 — qualify the three persisted failure classes
**Modify/Test:**
- `test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomHistoricalBackgroundGoal033ASuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomM1RuntimeHandoffSuite.java`

Add failing regressions that start from durable `FAILED_REPLAN_REQUIRED` snapshots for the three exact reasons. Assert same request identity/cursor ownership and no reset/reward duplication.

## Task 2 — ordinary-loot overflow
**Modify:** `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java`

Implement deterministic ordinary-loot spill described in DESIGN §2. Tests must prove first encounter can complete when excess ordinary non-stackable loot would previously produce `OBJECT_CAP`, while acquisition-target overflow still remains strict.

## Task 3 — typed farm authority
**Modify:**
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`

Add the typed attempt/result and map each production validation branch. Historical path consumes it. Do not catch a broad RuntimeException and return the old generic reason.

## Task 4 — bounded planner fallback
**Modify:** `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`

Implement the three search tiers and target exclusion. Keep deterministic ranking and existing route authority. Add planner regressions for strict preference, lower-level fallback, exclusion and true no-route.

## Task 5 — historical idle + self-heal
**Modify as required by existing operation-key/transaction contracts:**
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java` (only if action enum lives here)
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java` only if the existing command/replay switch requires the new action kind.

Implement reason classifier and canonical idle. Replay/idempotency must recognize the new action. Unknown failures remain failed.

## Task 6 — remove all-history pre-arm gate
**Modify:**
- `docs/phantoms/tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/Run-M1RuntimeHandoff.ps1`
- `java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java` only if current preparation API itself rejects a healthy candidate because another profile is failed.

Candidate preparation uses current physical locality, calendar-online, non-inconsistent status and demand readiness. Report global failure counts, do not require zero known-recoverable histories.

## Task 7 — final verification / deploy / one connected run
Run focused tests once, then the aggregate once. Build from a clean detached exact code SHA. Controlled deploy must preserve `1280 READY + 8720 RETIRED` inventory and configs `1280/64/128/100`.

Take one read-only PLAY snapshot of failure counts before arm. Request one fresh arm only after deployment/health is ready. Execute the existing single connected runner and stop.
