# Goal 026 CP5 — focused scenario matrix

## Raid Combat
- normal PvE continues rejecting RAID/GRAND_BOSS;
- explicit exact raid session accepts correct object/npc/kind/hash;
- wrong identity/kind/hash/instance/peace/invul rejects;
- canonical target death -> VICTORY;
- only selected collector enters loot phase;
- cancellation releases exact ownership.

## ENTRY_GATED
- adapter does not claim boss live;
- CP1 GROUP_READY can use explicit ENTRY_GATED;
- CP3 recruits it;
- CP4 gathers it using exact entryNpc SpawnTable authority;
- no live-boss location fallback;
- missing adapter fails closed;
- ordinary AVAILABLE/UNAVAILABLE regression unchanged.

## Queen Ant
- exact profile npc29001 GrandBoss level40;
- source-backed 2000 leash/+8 curse facts;
- when curse enabled level49 blocks, level48 does not;
- no Phantom source mutates HP/status/respawn/minions;
- actual target death + canonical manager evidence required for win.

## Zaken83
- exact profile target29181/entry32713/template135/min78/9..27;
- client enter83 and adapter share one condition function;
- nonleader/size/level/radius/reentry rejection preserved;
- valid CC enters through same InstanceScript path;
- adapter exposes <=36 unused candle IDs/positions but no isBlue;
- out-of-range candle interaction rejected;
- in-range interaction calls same onFirstTalk;
- no direct blue/script/visibility state mutation;
- red spawned mobs are not bypassed;
- after canonical four-blue script state exact Zaken becomes visible/unparalyzed;
- only then raid combat can engage;
- Phantom never calls giveItems/finishInstance.

## Attempt
- stale READY/changed force/expired goal cannot engage;
- per-Party support providers reserved from offense;
- offensive Phantoms use raid sessions;
- REAL members receive no action;
- alive count below min or all required providers dead => retreat;
- readyNow cooldown alone does not;
- target lost without death => ABORT;
- canonical death => VICTORY;
- terminal history bounded/idempotent/new revision fresh.

## Retreat
- open-world cancels combat/support then PartyRoutes to CP4 slots;
- Zaken routes living controlled members to adapter safe in-instance point;
- no teleport/finish/destroy; REAL unmoved;
- failure to retreat remains truthful ABORTED.

## Loot
- native Attackable/CommandChannel authority untouched;
- no manual item generation/give/add;
- exactly one collector can loot;
- no drop still valid victory;
- collector LOOTED/PARTIAL/BLOCKED recorded;
- Zaken script rewards remain script-owned.

## Decision E2E
Leader:
raid.prepare -> assembly -> READY (not complete) -> attempt -> canonical victory
-> COMPLETE_GOAL; abort/wipe/expiry -> FAIL; cancel cleans all owners.

Participant:
valid goal before leader exists -> REPLAN; joined through active attempt ->
REPLAN; leader victory -> COMPLETE; leader abort -> FAIL; never creates leader
attempt.

## Static negatives
No raid code setCurrentHp/doDie/setStatus-DEAD/deleteMe/finishInstance/giveItems/
inventory add/teleport shortcut/World.getPlayers/new raid worker/direct REAL
movement/direct CommandChannel mutation/other chronicle.

One final CP5 aggregate + one jar.
