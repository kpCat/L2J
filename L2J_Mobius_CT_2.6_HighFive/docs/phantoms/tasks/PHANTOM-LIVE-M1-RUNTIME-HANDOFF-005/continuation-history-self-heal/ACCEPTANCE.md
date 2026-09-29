# ACCEPTANCE

## Automated
1. Ordinary farm: a deterministic first encounter whose ordinary loot exceeds internal object mutation limits still advances encounter/history; excess is ground loss; no acquisition-target weakening.
2. Legacy `model.object_cap_indivisible` durable failure resumes the same request/cursor without duplicated XP/SP/items.
3. Legacy `catchup.authority.unsupported` is re-evaluated through typed authority handling; target/resource/position/hash branches take the specified recovery path.
4. A target-specific failed goal can exclude its current `npc@anchor` and select a deterministic alternative.
5. Planner strict ±2 wins when available; fallback tiers are used only when strict has no reachable candidate; no target above L+2 is introduced by fallback.
6. True no-reachable-target executes one idempotent historical idle interval: cursor advances one, XP/SP/inventory/adena/vitals/position unchanged.
7. Unknown failure stays `FAILED_REPLAN_REQUIRED` and does not get silently completed.
8. Mixed synthetic failure cohort (at least the previous proportions scaled to a bounded fixture) makes progress without starving ordinary work or human-demand focus.
9. Existing death, native travel, AutoPlay damage, retention, resize 1280 and pending-history preservation regressions remain PASS.

## Runtime before arm
- Config: 1280/64/128/100; maxScheduled remains 10000 capacity.
- Inventory settles at 1280 READY + 8720 RETIRED; no population reset/reseed.
- Read-only failure histogram is recorded. Known-recoverable count need not be zero, but worker/recovery must be live and no selected connected candidate may have unknown/inconsistent history failure.
- No TestAdmin movement before fresh arm.

## Connected
One existing runner only:
- physical demand appears before could-know/native visibility;
- selected profile reaches readiness and materializes before it becomes client-visible;
- same native Player performs useful travel/farm/AutoPlay action;
- 15 s leave/re-entry keeps the same identity under soft continuity;
- natural local census is recorded once;
- TestAdmin restored; Pilot stopped.

GREEN = connected scene PASS. No requirement to wait for every background history in the 1280 population to become COMPLETE before declaring M1 visible-world behavior functional.
