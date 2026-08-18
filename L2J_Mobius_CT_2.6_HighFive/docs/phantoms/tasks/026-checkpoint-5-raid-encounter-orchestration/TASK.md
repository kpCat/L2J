# Goal 026 Checkpoint 5 — complete raid encounter orchestration

## Identity
Branch: `feature/phantom-world`
Required parent: `f6402b512d5b22982e44f256506d7383a6b3d7c1`
Required commit subject: `feat(phantoms): complete raid encounter orchestration`
Seed: `26002651`
Target verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

This is the intended FINAL substantive checkpoint for Goal026.

Read PRIOR_INDEPENDENT_REVIEW.md and CONTEXT.md first. Implement one vertical
product result:

`raid.prepare -> assembly -> READY_AT_STAGING -> canonical entry/mechanic ->
raid combat/support -> objective retreat OR canonical victory -> canonical loot
settlement -> leader/participant terminal`

## Required deliverables

1. Add additive explicit RAID/GRAND_BOSS Combat path without weakening ordinary
   normal-monster Combat safety.
2. Add bounded encounter profile/catalog and runtime/script adapter contracts.
3. Add ENTRY_GATED readiness for canonical pre-spawn instance encounters.
4. Extend CP4 staging to exact entry-NPC SpawnTable authority for ENTRY_GATED.
5. Fully support source-backed Queen Ant `epic.29001`.
6. Fully support source-backed Zaken83 `epic.zaken.83` using the existing
   CavernOfThePirateCaptain script as entry/mechanic/reward authority.
7. Add bounded no-worker PhantomRaidAttemptService.
8. Integrate per-Party PhantomPartyTactics support and explicit raid Combat
   sessions for offensive PHANTOMs.
9. Add objective retreat/abort and truthful canonical victory.
10. Keep native Attackable/CommandChannel loot authority; exactly one stable
    collector may use existing Combat loot phase.
11. Complete PhantomRaidDecision end-to-end: leader goal finishes only after
    actual victory; participants wait/participate until leader terminal.
12. Wire production lifecycle and update both canonical status docs.

## Explicit raid Combat safety

Normal PhantomCombatRequest/startSession and TargetSnapshot.validFor semantics
must remain unchanged.

Raid session must require exact:
- runtime objectId;
- npcId;
- ContentKind RAID/EPIC;
- expected GameKnowledge NpcKind RAID_BOSS/GRAND_BOSS;
- attackable/targetable/non-invulnerable live target;
- same instance/surrounding/no-peace;
- bounded distance;
- exact attempt authority hash.

Use defaults on new actor-lease methods to avoid forcing unrelated adapters.
Reuse existing session ownership/loadout/shots/loot/cleanup rather than copying
CombatService.

## Encounter catalog / adapters

Create a small bounded High Five encounter catalog, preferably data-backed under
`data/phantoms/raid/`, with exact source refs.

Required executable profiles:
- `epic.29001` Queen Ant;
- `epic.zaken.83` Zaken83.

Zaken83 profile facts:
target29181, entryNpc32713, template135, minLevel78, minPlayers9,maxPlayers27,
candle mechanic.

CavernOfThePirateCaptain installs the adapter. Normal `enter83` and adapter MUST
use one shared check implementation and same enterInstance path. The adapter
cannot expose hidden candle-blue truth and cannot directly mutate mechanic
state.

## ENTRY_GATED

ENTRY_GATED means canonical entry workflow exists but boss is not yet live.
Never construct fake BossObservation/live target.

CP1 composition can become GROUP_READY for ENTRY_GATED. CP3/CP4 must support it.
CP4 staging for ENTRY_GATED may use only explicit anchors or exact encounter
entryNpc SpawnTable spawns, never live boss fallback.

## Queen Ant

Use current exact live GrandBoss authority. Enforce source-backed +8 overlevel
curse guard when raid curse is enabled. Preserve script-owned leash/minions,
status/respawn and drops. No Phantom mutation of Queen/minions.

## Zaken83 flow

From exact CP4 staging:
- call the script-owned entry adapter;
- adapter reuses script conditions and InstanceScript.enterInstance;
- observe same canonical instance/roster;
- select a stable Phantom scout;
- blindly explore unused candles by visible identity/position;
- physically reach each candle before same onFirstTalk;
- handle normal spawned attackers with existing ordinary Combat if required;
- wait for script to reveal/unparalyze exact Zaken;
- then explicit raid Combat path attacks 29181;
- script owns onKill timed rewards and finishInstance.

No direct script parameter changes.

## Attempt / roles

Attempt service is process-local bounded (64 live / <=256 terminal), explicit
advance only.

Per Party:
- reserve minimum stable required support providers for exact usable
  heal/resurrection/recharge actions;
- support via existing PhantomPartyTactics;
- offensive PHANTOMs choose deterministic supported damage mode and start exact
  raid sessions;
- one stable offensive collector only has lootAfterVictory=true;
- REAL members are observations only.

Release support leases after observed action completion.

## Retreat

Hard inability before canonical victory triggers retreat/abort. Temporary
cooldown alone is not inability.

Open-world retreat -> existing per-Party route to CP4 slots.
Zaken retreat -> script-exposed factual safe in-instance entry point.
No town teleport/finishInstance/destroyInstance. REAL players are not moved.

## Victory / loot

Victory requires actual target death plus exact authority/script confirmation.
Target disappearance alone is failure.

Never manually create or assign raid drops. Native Attackable/Party/CC rules
remain authoritative. Collector may consume only existing Combat loot
observations. Zaken script direct rewards stay script-owned.

## Decision

raid.prepare does NOT COMPLETE at READY_AT_STAGING. It drives AttemptService and
completes only at actual VICTORY.

raid.participate waits before leader startup, remains active through attempt,
and completes/fails with matching leader terminal.

## Hard out of scope

No universal epic solver, Antharas/Baium/Valakas/Beleth implementation, clans,
sieges, quests framework, new raid DB saga, global player scan, direct boss
mutation, custom drops, direct REAL control, second combat engine, other
chronicles.

## Read budget

Read only:
- this package;
- current public raid CP1-CP4 classes/models;
- exact Combat branch points needed for additive raid session;
- PhantomPartyTactics public API;
- Attackable raid loot section;
- QueenAnt script + 29001 data;
- CavernOfThePirateCaptain entry/candle/kill sections + Zaken NPC data;
- SpawnTable.getSpawns;
- PhantomSystem raid/Decision wiring;
- focused fixtures.

Do not re-audit historical Goals or whole unrelated subsystems.

## Verification budget

Implement coherent blocks, then test. No test-after-every-edit.

Focused gates:
A raid Combat safety;
B ENTRY_GATED CP1/CP3/CP4;
C Queen Ant profile;
D Zaken83 script parity/entry/candle/target;
E attempt/support/retreat/victory/loot;
F Decision end-to-end.

Direct affected regressions only:
- CP1 readiness;
- CP3 recruitment for ENTRY_GATED;
- CP4 assembly/gathering/Decision final-flow changes;
- normal Combat safety/core for additive raid session;
- GameKnowledge content;
- script compilation.

Freeze one Goal026 CP5 aggregate containing only the above, then ONE ant jar,
diff/scope/strict UTF-8/mojibake/escaped-Cyrillic.

Forbidden:
plain `ant verify`; Goal025 aggregate; broad Goal017; broad all-Combat;
all-Phantom; unrelated economy/social/PvP/Rift; stress loops; rerun green gates
after docs-only edits.

First automatic context compaction = STOP new discovery, finish safe coherent
block, mandatory focused gates, factual handoff.

## Delivery

Ordinary commit exact subject:
`feat(phantoms): complete raid encounter orchestration`

Ordinary push:
`git push origin feature/phantom-world`

Push safe coherent result even PARTIAL/BLOCKED.
No amend/rebase/squash/reset/force-push.

Final report: branch, parent, SHA, remote HEAD, subject, verdict, raid-combat
safety, ENTRY_GATED, Queen Ant, Zaken83, support/offense ownership, retreat,
victory, loot, Decision E2E, lifecycle, exact tests, unfinished findings and
`occurred_context_compaction: yes|no`.

Success token:
`GOAL_026_CHECKPOINT_5_RAID_ENCOUNTER_ORCHESTRATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`
