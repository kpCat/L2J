# DESIGN — recoverable historical failures without another M1 gate spiral

## 1. Why the current state is blocked
The 1280 population change worked, but 590 active histories are stored as `FAILED_REPLAN_REQUIRED`. Three reasons dominate. The current catch-up path treats most non-stale planned failures as terminal, so reducing population cannot repair those records by itself.

The fix is not to clear failure rows or mark them complete. It is to make the production semantics recoverable where the underlying game event is recoverable.

## 2. Object-cap semantics
`MAX_CHANGED_ITEM_OBJECTS` and `MAX_NEW_NON_STACKABLE_OBJECTS` are transaction/model bounds, not a Lineage rule saying the character can never kill that monster again.

For ordinary death-drop farming, model the player as picking up the deterministic prefix of ordinary loot that fits the mutation envelope and leaving overflow on the ground. This matches an ordinary player better than permanently killing the history request.

Implementation shape:
- create a pure helper in `PhantomBackgroundModel` that partitions one encounter's ordinary acquired awards into `accepted` and `ground overflow` against the current batch mutation state;
- stable ordering must use existing drop facts (group/item ordinal, then item id), never HashMap iteration;
- run the existing inventory/weight/slot checks on the accepted subset;
- acquisition modes bypass this overflow downgrade.

Persisted `model.object_cap_indivisible` is then retried on the same interval. If the model still reports an impossible internal contract after the ordinary overflow rule, keep it typed as blocker.

## 3. Typed authority attempt
The existing `farmInput` exception surface hides whether a persisted goal is stale, position is stale, the loadout changed, or the target contains unsupported loot.

Add an additive result type to `PhantomBackgroundAuthority`, e.g.:
- `FarmInputAttempt(FarmInput input, FarmInputFailure failure, String reason)`
- `FarmInputFailure { NONE, AUTHORITY_STALE, POSITION_STALE, TARGET_STALE, RESOURCE_STALE, UNSUPPORTED_LOOT, UNKNOWN }`

The exact names may follow existing style, but preserve these semantics. Keep the old throwing `farmInput(...)` methods for compatibility if other callers/tests need them; production historical farming should use the typed attempt.

`L2jPhantomBackgroundAuthority` should classify at the point each validation fails rather than parse exception text later.

## 4. Replan / no-target behavior
Planner search is tiered, not location-specific. Reuse immutable game knowledge, topology anchors, and `PhantomNormalGatekeeperTravel`.

Strict candidates remain preferred. Fallback only increases tolerance downward in mob level; it does not send an under-geared character to substantially higher targets.

For target-specific failures, one failed `npc@anchor` is excluded from that recovery attempt. Do not permanently blacklist a target globally.

If all tiers have no reachable candidate, historical time still exists. A canonical `HISTORICAL_IDLE` minute is the safe fallback: no reward and no movement, only catch-up ownership/cursor progression. This prevents one content hole from permanently fencing the character and lets later intervals retry planning.

## 5. Durable failed-state recovery
`FAILED_REPLAN_REQUIRED` remains a valid state for true blockers. Add one reason classifier in `PhantomHistoricalBackgroundService`:
- object-cap legacy/current -> retry current interval under new ordinary-loot semantics;
- legacy authority-unsupported -> run typed authority resolution, then refresh or replan;
- target-or-route absent -> rerun tiered planner; if none, historical idle;
- stale generation -> existing recovery path;
- everything else -> preserve existing RED behavior.

The recovery writes must use the existing optimistic stores and catch-up/goal row versions. `withPlan(...)` already transitions a failed planned state back to RUNNING while clearing the failure reason; use that rather than inventing a second state machine.

## 6. M1 gate semantics
M1 is a user-visible runtime milestone, not a requirement that all 1280 historical backlogs finish before one human can see a bot.

The connected candidate must be healthy and demand-driven preparation must outrank unrelated backlog. Global recoverable failures are observability, not a universal gate. Unknown/inconsistent failures remain visible and must not be hidden.
