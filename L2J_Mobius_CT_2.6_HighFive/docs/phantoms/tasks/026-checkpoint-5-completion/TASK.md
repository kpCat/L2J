# Goal 026 Checkpoint 5 completion

Branch: `feature/phantom-world`
Required parent: `a44421c1cec30e027aeb33e5588fb00373e30f1b`
Required subject: `feat(phantoms): finish raid encounter orchestration`
Seed: `26002652`

This is continuation of the SAME CP5 after required compaction-stop. It is not
a new checkpoint.

The original task remains at:
`docs/phantoms/tasks/026-checkpoint-5-raid-encounter-orchestration/TASK.md`.
Do not reread it wholesale. This file defines the remaining execution scope.

## 1. Raid Combat final hardening
Preserve the a444 additive raid path.
Close H0265-01 and H0265-02 from PRIOR_INDEPENDENT_REVIEW.
Add dynamic service-level coverage: exact live start, wrong
object/npc/kind/instance/level reject, exact live->dead VICTORY, wrong/replaced
dead identity TARGET_LOST, collector-only existing loot phase, ordinary PvE
safety unchanged.

## 2. ENTRY_GATED + encounter catalog
Add typed encounter profiles for:
- generic open-world RAID;
- Queen Ant `epic.29001`;
- Zaken83 `epic.zaken.83`.

Other EPIC remains typed unsupported.

Add `ENTRY_GATED` target availability:
- never claims boss live;
- exact registered script adapter means canonical entry workflow exists;
- GROUP_READY may be AVAILABLE or ENTRY_GATED only;
- CP3 may recruit it;
- CP4 may gather it;
- open-world AVAILABLE/UNAVAILABLE semantics stay unchanged.

ENTRY_GATED CP4 staging priority:
content anchor -> goal anchor -> exact profile entryNpcId via
`SpawnTable.getSpawns(entryNpcId)` -> fail closed.
Never use live-boss fallback for ENTRY_GATED and never scan all spawns.

Add curated `epic.zaken.83`: EPIC, npc29181, min9/max27, conservative existing
tank/heal/resurrection capability requirements.

## 3. Generic script adapter + Zaken83
Core owns only bounded exact-key adapter interface/registry; core must not import
the Zaken script package. Script registration is inert until AttemptService uses
it and must be reload-safe.

`CavernOfThePirateCaptain` installs `epic.zaken.83`.

Preserve canonical script facts:
Pathfinder32713, Zaken83=29181, template135, level>=78, group9..27, exact
Party/CC leader, every member within1000, InstanceManager reentry.

Refactor only enough that normal `enter83` and adapter entry use the SAME
condition implementation and SAME `InstanceScript.enterInstance`.

Adapter exposes:
- typed entry result/world instance;
- <=36 unused candle objectId+position+used state;
- revealed/unparalyzed exact Zaken target only after script makes it so;
- factual safe in-instance retreat point.

Never expose `isBlue`, blue count, Zaken room or equivalent hidden truth.

Candle interaction requires exact same instance, exact live unused candle and
physical canonical NPC interaction distance, then delegates the SAME
`onFirstTalk`. Never set candle/blue/Zaken script state directly.

Stable first Phantom scout chooses unused candles deterministically from public
evidence, physically routes to them, and interacts blindly. Red-candle mobs are
not bypassed: pause mechanic and clear exact bounded ordinary attackers through
existing normal Combat before continuing.

## 4. Queen Ant
Profile `epic.29001`: exact GrandBoss29001 level40, open-world, leash fact2000.
When `NpcConfig.RAID_DISABLE_CURSE == false`, any exact force member level>48
blocks attempt; level48 does not.
Add one narrow exact current member-level read seam without DB/global scans.
Never mutate Queen/minions/status/respawn.

## 5. Bounded PhantomRaidAttemptService
No owned worker/thread/Future.
max64 live, max256 terminal, one live attempt per leader, exact terminal
idempotency/new revision fresh, separate wall-ms + logical-nanos clocks.

Identity includes leader, goal id/revision, contentId and exact CP4 structural
hash. Add exact CP4 receipt lookup by assembly identity if needed; never start
from merely "latest receipt for leader".

Preflight: exact active raid.prepare, deadline, exact READY receipt, unchanged
structural force, fresh CP1 GROUP_READY (AVAILABLE/ENTRY_GATED), supported
profile, profile constraints.

Mint authority hash from exact AttemptIdentity + content recommendation hash +
CP4 structural hash + profile/encounter evidence; after entry bind exact target
object/npc/instance before raid sessions. Track only sessions successfully
started for the current attempt hash.

States cover validation, entry, mechanic, engaging/fighting, loot, retreat and
terminal victory/abort/wipe/expiry/cancel.

## 6. Support/offense
Operate per exact Party<=9, never whole CC in PartyTactics.
Reserve deterministic minimum providers for required heal/resurrection/recharge
using intrinsic+learned+sufficient rank and exact usable action. Temporary
readyNow=false does not erase provider capability.
Reserved providers use existing `PhantomPartyTactics` support and do not
simultaneously own offensive sessions.
Other PHANTOM members get deterministic supported CombatMode and existing raid
Combat sessions. Respect existing Combat capacity. REAL members are observed
only.

At most one stable offensive Phantom is loot collector with
`lootAfterVictory=true`; all others false. No manual drop/item creation.

## 7. Retreat and victory
Retreat/abort before canonical victory when deadline, structural force drift,
alive count below recommendedMinParty, all intrinsic+learned required providers
dead/unavailable, target replaced/lost, no controllable offense, or mechanic
invalid. Temporary cooldown/readyNow alone is not incapability.

On retreat: cancel owned combat/support/mechanic actions. Open-world uses one
existing PartyRoute per Party back to CP4 staging slots. Zaken routes living
controlled members to adapter safe in-instance point. REAL never moved. No town
teleport/finishInstance.

Victory is never predicted:
- exact owned raid session observes requested target death;
- AND read-only boss/profile authority confirms exact death;
- Zaken uses exact script-adapter death/completion evidence.
Disappearance without death confirmation is abort/TARGET_LOST.

Phantom raid code must not call setCurrentHp, doDie, boss setStatus DEAD,
deleteMe, finishInstance, giveItems/addItem.

## 8. Decision E2E
Update `PhantomRaidDecision`.

raid.prepare:
assembly intermediate => REPLAN;
READY_AT_STAGING => start/advance AttemptService, NOT COMPLETE;
attempt intermediate/retreat => REPLAN;
VICTORY => COMPLETE_GOAL;
ABORT/WIPE/EXPIRED => FAIL_GOAL;
cancel => clean attempt+assembly then CANCELLED.

raid.participate:
valid active goal before leader startup => REPLAN until own deadline;
joined while matching assembly/attempt active => REPLAN;
leader VICTORY => COMPLETE_GOAL;
leader abort/wipe/expiry => FAIL_GOAL;
never creates leader assembly/attempt.

## 9. Production/docs
Wire catalog/registry/AttemptService before raid Decision registry sealing.
Attempt cleanup precedes Assembly/Party/Combat/Navigation teardown.
Disabled system inert.

Update both master plan and roadmap:
CP1-CP4 + 026A/B/C ACCEPT;
CP5 and Goal026 overall `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`;
Goal027 NOT_STARTED.
Do not self-ACCEPT Goal026.

## 10. Out of scope
No universal epic solver, other epics, clans/sieges/quests, raid DB saga,
global scans, instance bypass, direct boss mutation, custom drops, REAL control,
second Combat engine, other chronicles.

## 11. Context budget
Mandatory reading only:
- this package;
- delivered PhantomRaidCombatRequest/RaidTargetSnapshot/processRaid branch;
- CP4 ReadyReceipt/public APIs;
- PhantomRaidDecision;
- PhantomPartyTactics public API;
- Zaken script only entry/onEnter/onFirstTalk/manageNpcSpawn/onKill;
- Queen script only constants/curse/onKill;
- SpawnTable.getSpawns;
- PhantomSystem raid wiring;
- one exact search for curated file containing `epic.29001`.

Do not read whole CombatService/PartyCoordinator/history/other boss scripts.

Implementation order:
A combat hardening + ENTRY_GATED/contracts;
B profiles + Zaken mechanic;
C AttemptService/support/retreat/victory/loot;
D Decision/lifecycle/docs;
E verification.

First automatic context compaction = STOP new discovery and safe PARTIAL
delivery; do not reconstruct context and continue.

## 12. Verification
Focused:
1. final dynamic raid-combat;
2. ENTRY_GATED + Queen/Zaken profile/script;
3. Attempt/support/retreat/victory/loot;
4. Decision E2E;
plus only directly affected CP1, CP3, CP4, one ordinary Combat safety/core,
GameKnowledge content and script compile.

Then ONE final CP5 aggregate + ONE ant jar + diff/scope/UTF8 gates.

Forbidden: plain ant verify, Goal025, broad Goal017/all-Combat/all-Phantom,
unrelated economy/social/PvP/Rift, stress loops.

## 13. Delivery
Ordinary commit exact subject:
`feat(phantoms): finish raid encounter orchestration`

Ordinary push origin feature/phantom-world even PARTIAL/BLOCKED.
No amend/rebase/squash/reset/force-push.

Final report: branch, parent, SHA, remote HEAD, subject, verdict, H0265-01,
authority-hash truth, ENTRY_GATED, Queen, Zaken, support/offense, retreat,
canonical victory/native loot, Decision E2E, lifecycle, tests, unfinished scope,
`occurred_context_compaction: yes|no`.

Success token:
`GOAL_026_CHECKPOINT_5_RAID_ENCOUNTER_ORCHESTRATION_COMPLETED_PENDING_INDEPENDENT_REVIEW`
