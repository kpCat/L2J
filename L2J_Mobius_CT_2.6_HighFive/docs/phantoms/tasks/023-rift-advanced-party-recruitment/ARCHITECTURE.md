# Goal 023 — architecture

## Dependency direction

```text
current Rift source/config
        ↓
immutable Rift facts + Phantom-only composition policy
        ↓
canonical Party roster + Goal 013/017 member capabilities
        ↓
role/readiness evaluation
        ↓
bounded candidate selection
        ↓
Goal 017 invite/refuse lifecycle
        ↓
Goal 017 shared party route
        ↓
READY_TO_ENTER
```

No layer below Party/Rift authority depends on language.

## Truth owners

| Truth | Owner |
|---|---|
| current Party roster | canonical Party / Goal 017 backend |
| invite/accept/refuse | Goal 017 + PartyInvitationService |
| class/capability facts | Goal 013 progression |
| role matching | Goal 017 RoleMatcher/catalog |
| Rift rooms/spawns | current DimensionalRift.xml |
| Rift runtime requirements | current GeneralConfig + canonical Rift owner |
| member inventory/equipment | canonical Player |
| party travel | Goal 017 route coordinator |
| wording/chat | Goal 020 |
| Rift preparation state | Goal 023 component |

## No second party kernel

Goal 023 may request Goal 017 operations but never writes canonical party
membership itself.

## Staleness rule

Every action is keyed by:

```text
Goal revision
party group/generation
canonical roster evidence hash
Rift source/config/policy hashes
role evidence hash
candidate identity
```

Any change forces re-evaluation before mutation.

## Ready rule

READY_TO_ENTER is an observation, not a Rift-entry side effect.
