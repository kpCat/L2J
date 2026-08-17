# Goal 026 Checkpoint 3 — source-backed context

Baseline: `bbd29495a19a322c0629509c85c31fe508ae8d07`

## Existing accepted authority

Goal026 CP1 already provides `PhantomRaidReadinessService.assess(actor,
contentId)` over authoritative RAID/EPIC Game Knowledge, live boss authority and
Goal017 current Party/CommandChannel facts.

It already enforces:

- target must be AVAILABLE;
- authoritative recommendedMinParty / recommendedMaxParty;
- exact current force;
- hard capability requirements;
- capability truth only when exact key/rank matches AND
  `intrinsic && learned && readyNow`.

`RaidReadiness` already carries content, target, current force, capability
assessments, status and reason. Do not create another readiness model.

Goal017 already exposes bounded canonical observations:

- `observe(MemberRef)` -> exact one-Party snapshot;
- `memberSnapshot(MemberRef)` -> current bounded member/capability snapshot;
- `currentForce(MemberRef)` -> Party or CommandChannel force;
- MAX_FORCE_PARTIES = 16;
- MAX_FORCE_MEMBERS = 144.

Goal026 CP2 already exposes exact MPCC actions through `PhantomPartyBackend`:

- inviteCommandChannel;
- respondCommandChannel with exact invitation identity;
- observeCommandChannelInvitation;
- dismissCommandChannel.

The generic `CommandChannelInvitationService` remains the sole MPCC lifecycle
owner. CP3 must not bypass or redesign it.

## Candidate discovery boundary

There is no accepted generic raid-party discovery index. Therefore CP3 receives
a caller-supplied bounded list of already-known exact candidate Party leaders.

No World.getPlayers(), profile population scan, name search, scheduler scan or
new discovery mechanism is allowed.

## Goal026 remaining roadmap

Goal026 still needs composition, preparation, gathering, route/timing,
recruitment, retreat and win feasibility. Master plan additionally calls out
real-player invite, attempt/retreat, loot policy and Queen Ant/Zaken profiles.

CP3 is only the composition/recruitment bridge between accepted CP1 readiness
and accepted CP2 CommandChannel lifecycle. Gathering/navigation, entry, combat,
retreat, loot and boss-specific execution remain later checkpoints.

Existing Rift code may be read only as a small structural precedent for exact
evidence/canonical invitation truth. Do not clone its large service or reuse
Rift persistence as raid state.
