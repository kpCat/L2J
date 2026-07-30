# Current-code map — Goal 017

This is a pre-audited map. Codex must verify exact symbols, not rediscover the
whole repository.

## Canonical party lifecycle

`RequestJoinParty` currently validates:

```text
requestor/target existence and visibility
offline/detached target
party ban and event compatibility
already-in-party and block list
self/cursed weapon/jail/Olympiad
request-processing ownership
leader-only invite
party capacity 9
one pending invitation
Dimensional Rift exclusion
distribution type
```

It then uses `Player.onTransactionRequest`, `activeRequester`,
`Party.pendingInvitation`, and `AskJoinParty`.

`RequestAnswerJoinParty` accepts/refuses and on accept calls canonical
`Player.joinParty`, creating `new Party` when the requestor has no party. It
clears request ownership and pending invitation.

`Party` is runtime-only. It owns:

```text
CopyOnWrite member list, leader at index zero, max nine by handlers
distribution type
pending invitation timeout
add/remove/disband/leader transfer
command-channel/Rift references
ordinary packet broadcasts
```

`Player.joinParty`, `leaveParty`, `setParty` are the canonical membership
methods. Server restart does not persist a `Party` object.

## Existing Phantom seams

- exact materialized Player access exists only through `ActionLease`;
- one persistent character has one identity owner;
- progression exposes active-class, learned-skill, equipment, resource and
  `CapabilityEvaluation` facts;
- capability identity is `(capabilityKey, variantKey)` and includes exact action
  skill, target scope and READY_NOW;
- combat currently owns one monster-combat session per profile and rejects
  concurrent sessions;
- combat backend currently targets normal monsters only;
- navigation produces bounded immutable routes and a progress tracker, but owns
  no actor movement;
- scheduler has one control port; Goal 016 occupies it;
- profile components support bounded optimistic persistence without schema
  changes;
- decision engine stores one current goal per profile.

## Dependency constraints

- core `Party`/packet code must not import Phantom party classes;
- shared canonical invitation logic may depend on a small generic delivery port;
- Phantom party code may depend on canonical Party, progression, combat,
  navigation, topology, scheduler and profile ports;
- party policy must not enter progression/catalog;
- no class-ID switch or one-script-per-class;
- Rift composition belongs to Goal 023; personality to Goal 018; text to
  Goals 019–020; PvP to Goal 025.
