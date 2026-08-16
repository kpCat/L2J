# Goal 026 Checkpoint 1 — architecture contract

## Purpose

Provide one reusable, bounded, read-only answer:

> Which configured RAID/EPIC content exists, is the exact boss currently
> observable as available, and is the actor's current Party/CommandChannel
> composition plausibly ready according to existing Goal011 capability
> recommendations?

It is a feasibility/readiness layer, not raid orchestration.

## Ownership

- Goal011 owns content/NPC/capability knowledge.
- `RaidBossSpawnManager` / `GrandBossManager` own live boss truth.
- Goal017 owns Party membership/capabilities and the read-only large-group snapshot seam.
- Goal026 CP1 only joins immutable facts and evaluates conservative readiness.
- Goal009 navigation, Goal012 combat, Goal020 conversation remain untouched.

## Suggested production family

A small `phantoms/raid` family is appropriate, for example:
- `PhantomRaidModel`
- `PhantomRaidAuthority`
- `L2jPhantomRaidAuthority`
- `PhantomRaidReadinessService`

Names may adapt to repository conventions. The service should be stateless/worker-free.

### Content snapshot

For each configured RAID/EPIC content:
- contentId;
- kind;
- exact npcId;
- exact NPC kind/level from Goal011;
- recommendation hash/source refs;
- recommended current group-size range;
- required/optional capabilities.

Reject/fail closed if npcId is absent, RAID points to non-RAID_BOSS, EPIC points
to non-GRAND_BOSS, content/NPC fact is missing, or identity is ambiguous.

### Boss availability

Use an enum capable of uncertainty: `AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`.

Standard RAID:
- exact `RaidBossStatus.ALIVE` + consistent exact live boss may yield AVAILABLE;
- DEAD/scheduled may yield UNAVAILABLE;
- UNDEFINED/mismatch -> UNKNOWN.

EPIC:
- exact current live non-dead GrandBoss may yield AVAILABLE;
- exact no-live-object plus future respawn evidence may yield UNAVAILABLE;
- raw script status alone must not be globally interpreted; ambiguous cases -> UNKNOWN.

Never mutate manager state.

### Current force snapshot

Goal017 should expose a narrow immutable current-force snapshot from the exact
actor profile:
- current Party if present;
- optional current CommandChannel identity/leader;
- bounded canonical parties/member identities;
- total member count and channel level;
- per-member current class/capability evidence through existing Goal017 seams.

Do not enumerate unrelated parties/players/profiles. Start from the exact
materialized actor and its current `Party#getCommandChannel()` only.

Use explicit defensive bounds. If canonical state exceeds the snapshot bound,
fail closed instead of truncating in a way that can fabricate readiness.
Do not claim these defensive bounds are High Five server-law maxima.

### Feasibility result

A result may distinguish:
- TARGET_UNKNOWN
- TARGET_UNAVAILABLE
- GROUP_ABSENT
- GROUP_INCOMPLETE
- GROUP_INCAPABLE
- GROUP_READY

Rules:
- target availability must be AVAILABLE before GROUP_READY;
- recommended size and required capability ranks/counts are curated Phantom
  recommendations, not server-law;
- every `required=true` capability must be satisfied by current canonical members;
- missing required tank/heal/resurrection etc => never GROUP_READY;
- optional capability absence alone is not a hard failure;
- no damage formula, DPS simulation, boss-HP TTK or victory probability.

`GROUP_READY` means only that current observed group satisfies conservative
Phantom readiness policy, not that it will win.

## Command-channel caveat

Do not use `CommandChannel.meetRaidWarCondition()` as a universal raid-entry or
victory rule. It may be copied only as an additional canonical loot-privilege
observation when an exact live Raid target exists.

## Lifecycle

- no new worker/thread/timer/Future;
- no new persistence component in CP1;
- no Decision candidate/step handler in CP1;
- no periodic poll;
- passive construction in `PhantomSystem` is allowed only if useful for later
  checkpoints; disabled mode remains inert.

## No server-core mutation

Do not modify `CommandChannel`, `Party`, `RaidBossSpawnManager`,
`GrandBossManager` or boss scripts merely to help Phantom. Adapter-level read
seams come first. If a generic core read seam is genuinely missing, report the
blocker instead of broadening this checkpoint.
