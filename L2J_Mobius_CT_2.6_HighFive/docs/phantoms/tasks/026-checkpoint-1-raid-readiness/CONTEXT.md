# Goal 026 Checkpoint 1 — source-backed context

This checkpoint is deliberately read-only with respect to raid orchestration.
It establishes facts and feasibility only.

## Existing authoritative owners already audited

### Goal011 Game Knowledge

Current model already contains `NpcKind.RAID_BOSS` / `GRAND_BOSS`,
`ContentKind.RAID` / `EPIC`, immutable `ContentRequirementFact`, class capability
facts, required capability counts/ranks, exact NPC lookup and spawn facts.

Current curated High Five knowledge already includes:
- `raid.25001` -> npc 25001;
- `epic.29001` -> npc 29001.

These are curated Phantom feasibility recommendations, not server-law access
rules. Do not create a second raid catalog or parse the knowledge XML again.

A minimal Goal011-owned paged query such as
`contents(ContentKind, PageRequest)` is authorized if needed. It must page the
existing immutable content requirements and preserve deterministic ordering.

### Standard raid authority

`RaidBossSpawnManager` owns standard raid spawn/status truth.
Useful exact read seams:
- `getRaidBossStatusId(npcId)`;
- exact live boss entry;
- `isDefined(npcId)`;
- stored respawn information.

Phantom code must not call update/delete/spawn/status mutation APIs and must not
create schedules.

### Epic / grand-boss authority

`GrandBossManager` owns exact current grand-boss object, raw script status,
stored respawn facts and BossZone state.

Raw grand-boss status integers are content-script-specific. Do not invent one
global integer -> ALIVE/DEAD mapping.

For Checkpoint 1:
- an exact live, non-dead current GrandBoss object may establish live presence;
- raw status and respawn values may be copied as evidence;
- absent/ambiguous state must become UNKNOWN / NOT_READY, never guessed ready.

No writes to GrandBossManager.

### Goal017 Party authority

`PhantomPartyBackend` already copies exact current Party state and member
capabilities. `L2jPhantomPartyBackend` owns canonical Player/Party reads.

`CommandChannel` is canonical server group truth. It exposes current leader,
parties, members, member count and level. Checkpoint 1 may add a narrow read-only
snapshot through Goal017. It must not construct/add/remove/disband a
CommandChannel.

Do not duplicate party membership inside Goal026.

### Goal023 Rift precedent

`L2jPhantomRiftBackend` is the local pattern for bounded immutable live-state
copies, reusing Goal017 `PhantomPartyBackend`, current member/party facts and
capability evidence. Reuse the pattern, not the Rift feature itself.

## Scope boundary

Checkpoint 1 does NOT:
- form a CommandChannel;
- recruit/invite;
- gather;
- navigate;
- enter raid/epic zones;
- attack a boss;
- execute retreat;
- predict victory/damage;
- persist a raid saga;
- create a scheduler worker.

Those belong to later Goal026 checkpoints.
