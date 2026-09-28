# TASK — PHANTOM-LIVE-M1-SMART-CONTINUITY-004

## Goal

Finish the remaining M1 connected RED through **generic smart continuity**, not per-location repairs.

Required initial remote HEAD:

`0c2d305ca1759fbdfadf487e584409db4c7ee71b`

Branch:

`feature/phantom-world`

Repository/module:

`C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Read `HANDOFF.md`, `DESIGN.md`, `PLAN.md`, `SCENARIOS.md`, `ACCEPTANCE.md` before production edits.

## Product problems to solve

1. M1-003 connected prewarm inherited a stale scheduler transition backoff and materialized ~30 s late after valid human locality.
2. Existing retention is failure-shaped (`TRANSIENT_BLOCK`) and global demotion grace is only 2 s; short human leave/return should preserve continuity instead of churn.
3. Native Party with a REAL player must be a hard materialization retention reason so ordinary locality loss cannot randomly break the party in future M2.
4. Failed `PhantomVisibleFarmTravel` can retain its own travel presence while navigation repeatedly fails, producing a materialized IDLE loop.
5. Route/target failure must cause bounded generic alternate replan, not hard-coded spot repair.

## Required implementation behavior

Follow DESIGN.md. In particular:

- distinguish policy `DEFERRED` from real transient transition failure;
- no stale exponential backoff inheritance after fresh human locality;
- introduce reusable hard/soft retention reasons;
- ~60 s soft `RECENT_HUMAN` continuity;
- native-visible + REAL-party hard retention;
- soft retention reclaimable under cap pressure;
- terminal travel failure releases hold and triggers alternate generic replan;
- bounded no-target fallback prevents indefinite exact-NPC idle;
- reuse stock AutoPlay, existing planner, native GeoEngine/PathFinding/GK facts.

## What NOT to do

- Do not lower the 30 s retry maximum as the fix.
- Do not increase MaxMaterialized/ActiveTarget or other private budgets.
- Do not add manual coordinates/routes for one race/level/spot to make acceptance pass.
- Do not special-case Gremlin, Elven/Dwarf/Human start areas, level 20, profile IDs, or acceptance anchors.
- Do not run live checks across many locations.
- Do not create a second farming/combat engine.
- Do not start M2.
- Do not create another proof-only task.
- Do not mutate PLAY DB.

## Likely production scope

Expected files/classes to read and potentially edit:

- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityTransitionStatus.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerPolicy.java`
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java`
- optional new `activity/PhantomMaterializationRetentionPolicy.java` (or equally narrow equivalent)
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java` only if needed for generic no-target fallback
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`

`PhantomNavigationService` / `L2jNavigationBackend` are read-first and should remain unchanged unless a concrete backend/service defect is demonstrated. The current connected evidence primarily implicates caller semantics.

Party code is read-first. Prefer native party truth in the retention policy; do not redesign `PhantomPartyCoordinator` unless the current APIs cannot expose the required invariant.

Test files may be extended only for focused semantics. Task docs in this folder are task-owned.

If production scope must expand, record the exact file and source-backed reason in EVIDENCE before editing it. Do not use broad `git add .`.

## Git

Allowed:

- read-only Git inspection;
- exact-path `git add -- <task-owned paths>`;
- `git commit -m ...`;
- normal `git push origin feature/phantom-world`.

Forbidden:

- `git add .`
- `reset`
- `clean`
- `stash`
- `rebase`
- force push
- modifying unrelated user dirt.

## Efficient validation policy

Do not burn context/limit on repetitive ceremony.

- read-first once;
- focused tests during implementation as needed;
- broad 85/85 gate once at final automated validation only if planner changed;
- mojibake/escaped Cyrillic once before final commit;
- one final clean detached build;
- one representative connected acceptance after deployment;
- raw logs as files, concise EVIDENCE.

Expected user action: one TestAdmin arm. Hard max two only if the first final connected run exposes a genuine production defect requiring restart.

## Completion

GREEN means `GREEN — M1 SMART CONTINUITY COMPLETE` and M1 is closed.

Do not start M2 in this task. Report final remote SHA, exact code SHA, build/deployment state, and remaining product limitations only.
