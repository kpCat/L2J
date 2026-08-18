# Goal 026 CP5 — pre-audited source context

## Accepted stack

CP1 PhantomRaidReadinessService owns content, live target authority and current
force readiness. Hard capability truth remains exact key/rank +
intrinsic+learned+readyNow.

CP2 CommandChannelInvitationService owns MPCC lifecycle.

CP3 PhantomRaidRecruitmentService owns max16 deterministic deficit recruitment.

CP4 PhantomRaidAssemblyService owns raid.prepare / raid.participate, bilateral
consent, structural force evidence, physical per-Party gathering and
READY_AT_STAGING.

Do not clone any of these.

## Combat safety finding

Ordinary PhantomCombatBackend.TargetSnapshot.validFor currently requires
`normalMonster && knowledgeMonster`; L2jCombatBackend grounds knowledgeMonster
only as NpcKind.MONSTER. This intentionally prevents ordinary farming combat
from targeting RAID_BOSS/GRAND_BOSS.

CP5 must keep that predicate unchanged and add a separate explicit,
authority-bound raid combat path.

Recommended additive seam:
- RaidTargetSnapshot;
- default inert raid methods on PhantomCombatActorLease;
- PhantomRaidCombatRequest;
- PhantomCombatService.startRaidSession(...);
- exact L2jCombatBackend raid target/action implementation.

Raid target validity must require exact runtime objectId, expected npcId,
ContentKind RAID/EPIC, exact GameKnowledge NpcKind RAID_BOSS/GRAND_BOSS,
canonical Attackable/Monster raid target, targetable/auto-attackable,
non-invulnerable, same instance/surrounding region/no peace restriction, bounded
distance and an exact attempt authority hash.

Reuse the existing CombatService actor ownership, loadout, shots, polling,
low-HP stop, timeout, terminal results, loot and cleanup. Do not fork a second
combat engine.

## Party support

PhantomPartyTactics already plans HEAL/RECHARGE/RESURRECT and dispatches through
Combat external-action ownership. Use it per exact Party <=9. Reserve minimum
stable required support providers away from offensive raid sessions; other
PHANTOM members use raid Combat sessions. REAL members are never controlled.

## Canonical loot

High Five Attackable assigns first attacking CommandChannel looting rights and
runs normal drop/auto-loot logic. CP5 never creates or gives ordinary raid drops.
Choose exactly one stable offensive PHANTOM collector with lootAfterVictory=true;
all other raid sessions false. Zaken script-owned direct rewards remain
script-owned.

## Queen Ant source facts

Fully executable profile: `epic.29001`.

H5 source facts:
- npc 29001, GrandBoss, level40;
- script nest/leash 2000;
- Nurse/Royal/Guard are canonical script/minion behavior and nurses heal;
- raid curse, when enabled, can apply when attacker is >8 levels above target;
- Queen death sets GrandBossManager status DEAD and schedules respawn;
- normal NPC data owns drops.

Profile must not copy Queen AI. It may enforce the +8 overlevel preflight using
a narrow exact member-level runtime read. Victory comes only from actual
canonical target death/status evidence.

## Zaken source facts

Fully executable profile: `epic.zaken.83`.

Current CavernOfThePirateCaptain source proves:
- entry NPC 32713;
- Zaken83 npc29181;
- template135;
- min level78;
- 9..27 members;
- exact Party/CC leader entry;
- all members within 1000;
- InstanceManager reentry check;
- same InstanceScript.enterInstance handles canonical world creation and group
  teleport;
- 36 candles spawn;
- hidden blue state is internal;
- four blue candle first-talk interactions reveal/unparalyze Zaken;
- red candles spawn canonical mobs;
- Zaken83 onKill owns timed rewards and finishInstance.

Also keep factual catalog facts for Zaken60 day/night, but do not claim those
variants executable unless they fall out safely. Required executable Zaken
variant is 83.

## Script-owned adapter

Add a small generic process-local raid-script adapter registry under Phantom
raid domain. Core Phantom code must NOT import the instance script package.

CavernOfThePirateCaptain installs its Zaken83 adapter from its constructor.
Refactor only enough that ordinary client `enter83` and Phantom adapter use the
same condition implementation and the same InstanceScript.enterInstance path.

Adapter may expose exact visible unused candle objectIds/positions and used
state, but MUST NOT expose hidden `isBlue`. Candle interaction must validate
same instance, exact candle NPC, unused state and physical player proximity, then
delegate to the same onFirstTalk logic. It must never set blue count, script
value, Zaken visibility/paralysis or instance completion directly.

manageNpcSpawn may retain a bounded exact candle list in InstanceWorld
parameters so the adapter can observe the same spawned objects.

## ENTRY_GATED readiness

Zaken does not exist as a live boss before canonical instance creation.

Add explicit TargetAvailability `ENTRY_GATED` (or exact equivalent with the same
meaning): the boss is NOT claimed live; an authoritative registered encounter
adapter says this exact content has a canonical entry workflow.

RaidReadiness may produce GROUP_READY for AVAILABLE or ENTRY_GATED only.
UNKNOWN/UNAVAILABLE remain fail-closed. Ordinary open-world semantics stay
unchanged. CP3 can recruit ENTRY_GATED. CP4 can assemble/gather it.

CP4 must not use live-boss location fallback for ENTRY_GATED. Staging priority:
1 content topology anchor;
2 goal selectedAnchor;
3 exact encounter entry NPC spawns from SpawnTable.getSpawns(entryNpcId),
  deterministically selected using current actor position;
4 otherwise fail closed.
Do not scan the full spawn table.

Add GameKnowledge content `epic.zaken.83`:
- EPIC, npc29181;
- recommendedMinParty 9, recommendedMaxParty 27;
- conservative required tank/heal/resurrection capabilities compatible with the
  existing catalog;
- source refs to CavernOfThePirateCaptain and NPC 29100-29199 data.

## Zaken mechanic

After canonical entry:
- preserve exact Party/CommandChannel structural roster;
- allow canonical instanceId transition;
- choose one stable PHANTOM scout;
- expose only unused candle identities/positions, never blue truth;
- deterministically choose a candle;
- physically route only the scout using existing movement ownership;
- interact only after arrival;
- red-candle mobs are not bypassed; bounded exact normal-monster attackers may
  be cleared with existing ordinary Combat sessions;
- poll script until exact Zaken target is visible/unparalyzed;
- only then engage through explicit raid combat.

## Attempt state

Create bounded process-local PhantomRaidAttemptService. No worker/thread/Future.
Advance from Decision.

States should cover validating, entering, mechanic, engaging, fighting,
loot-settling, retreating and terminal victory/aborted/wiped/expired/cancelled.

max64 live attempts, terminal history <=256, one live attempt per leader,
identity = leader + goalId/revision + content + CP4 structural hash. Same exact
terminal identity idempotent; newer revision fresh. Preserve accepted separate
wall/logical clocks.

## Final Decision behavior

The existing `raid.prepare` goal remains the leader orchestration goal through
the attempt. READY_AT_STAGING must no longer COMPLETE the leader goal.

raid.prepare:
- assembly intermediate -> REPLAN;
- READY -> start/advance AttemptService;
- attempt intermediate -> REPLAN;
- VICTORY -> COMPLETE_GOAL;
- ABORTED/WIPED/EXPIRED -> FAIL_GOAL;
- cancellation cleans assembly+attempt and CANCELLED.

raid.participate:
- valid participation before leader assembly exists waits/REPLAN until its own
  deadline, rather than failing simply because leader Decision has not run yet;
- after canonical join, remain REPLAN while matching assembly/attempt is active;
- leader VICTORY -> COMPLETE_GOAL;
- leader abort/wipe/expiry -> FAIL_GOAL;
- never creates a leader assembly/attempt.

## Feasibility and retreat

Never use temporary readyNow=false alone to retreat.

Before canonical victory, retreat/abort on hard facts:
- deadline;
- structural force change;
- target identity loss/replacement without death;
- alive member count below recommended minimum;
- all providers of a required capability dead/unavailable when judged by
  intrinsic+learned+rank (temporary cooldown ignored);
- no controlled offensive session can continue and no target-death evidence;
- invalid instance/profile mechanic.

Retreat first cancels attempt-owned combat/support/mechanic actions.

Open-world: per-Party existing PartyRouteCoordinator back to CP4 staging slots.
Zaken: route living controlled members to a factual safe in-instance entry point
exposed by the script adapter. Never teleport or destroy/finish the instance.
REAL members never move automatically.

## Victory truth

No prediction and no synthetic win.

Victory requires actual target-death evidence after engagement plus fresh
boss/encounter confirmation that the same exact target died or the exact script
completed its kill lifecycle.

Target disappearance without death evidence = abort, never victory.

Phantom raid code must not call setCurrentHp, doDie, set boss status DEAD,
deleteMe or finishInstance.

## Goal026 status

After CP5 delivery docs must say:
- CP1+026A ACCEPT
- CP2 ACCEPT
- CP3+026B ACCEPT
- CP4+026C ACCEPT at f6402b512d5b22982e44f256506d7383a6b3d7c1
- CP5 IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
- Goal026 overall IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
- Goal027 NOT_STARTED

Do not self-ACCEPT Goal026.
