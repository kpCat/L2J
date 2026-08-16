# Goal 026 Checkpoint 1 — focused test matrix

Seed: `26002601`.

## A. Goal011 content enumeration
1. bounded deterministic RAID/EPIC enumeration;
2. pagination/cursor stable;
3. existing `content(contentId)` truth unchanged;
4. RAID content -> exact RAID_BOSS NPC;
5. EPIC content -> exact GRAND_BOSS NPC;
6. missing/wrong-kind content fails closed.

## B. Standard raid availability
Through an injectable/read-only authority seam:
1. exact live ALIVE raid -> AVAILABLE;
2. exact DEAD/scheduled raid -> UNAVAILABLE;
3. UNDEFINED/missing/mismatched live object -> UNKNOWN;
4. no manager mutation method is called.

Prefer a fake authority for pure policy. One focused server integration
assertion is allowed only if an existing safe fixture can prove it.

## C. Epic availability
1. exact live non-dead GrandBoss -> AVAILABLE;
2. clear future respawn + no live boss -> UNAVAILABLE;
3. raw script status without enough live evidence -> UNKNOWN;
4. no universal mapping of grand-boss status integers.

## D. Current force
1. actor with no Party -> GROUP_ABSENT;
2. exact current Party only -> bounded Party snapshot;
3. current CommandChannel -> bounded exact party/member snapshot;
4. unrelated Player/Party is never discovered;
5. stale actor/party identity -> unavailable, not fabricated;
6. over-bound snapshot fails closed rather than truncating readiness.

## E. Capability/readiness
1. below recommended minimum -> GROUP_INCOMPLETE;
2. required tank missing -> GROUP_INCAPABLE;
3. required healer missing -> GROUP_INCAPABLE;
4. EPIC required resurrection missing -> GROUP_INCAPABLE;
5. all required counts/ranks + size satisfied + target AVAILABLE -> GROUP_READY;
6. optional capability absence alone does not hard-fail readiness;
7. target UNKNOWN/UNAVAILABLE prevents GROUP_READY.

## F. Negative controls
- no CommandChannel constructor/add/remove/disband from Goal026;
- no Party mutation;
- no navigation request;
- no combat request;
- no boss status mutation;
- no global `World.getPlayers()` or profile scan;
- no worker/timer/Future/thread;
- no victory/DPS/damage simulation;
- disabled Phantom remains inert.

## G. Affected regressions
Run only:
- focused Goal011 query tests if Game Knowledge query changed;
- focused Goal017 Party/backend test if its read-only seam changed;
- new Goal026 CP1 focused suite.

No Goal025 aggregate. No all-Phantom aggregate. No plain `ant verify`.

After feature freeze:
- one CP1 focused aggregate;
- one `ant jar`;
- `git diff --check`;
- focused scope/encoding checks.
