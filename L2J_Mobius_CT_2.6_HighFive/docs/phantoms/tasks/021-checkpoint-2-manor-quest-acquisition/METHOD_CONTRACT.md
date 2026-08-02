# Manor and quest method contract

## Manor source identity

```text
MANOR_V1
static ManorFact stable key
runtime Seed identity
target crop item
seed item
castle
target NPC
topology node/anchor/instance
seed/harvester handler and skill identities
formula/rate fingerprint
knowledge/topology/manor hashes
```

Only the direct crop item is produced by the chain.

## Manor recovery matrix

| Durable phase | Evidence | Result |
|---|---|---|
| SOW_DISPATCHING | exact sow cast active | wait |
| SOW_DISPATCHING | exact target seeded by owner | SOW_OBSERVED |
| SOW_DISPATCHING | seed decreased, no target proof | uncertain/failure |
| SOW_DISPATCHING | no effect/cast/count delta | bounded retry |
| HARVEST_DISPATCHING | crop increased | VERIFYING |
| HARVEST_DISPATCHING | exact harvest cast active | wait |
| HARVEST_DISPATCHING | harvestable owned corpse | bounded retry |
| HARVEST_DISPATCHING | target gone/reused | uncertain/failure |

## Quest rule identity

```text
QUEST_COLLECTION_V1
rule ID
quest ID/name
source path and full SHA-256
required STARTED state
allowed cond set
declared read-only vars
target NPC
quest item/cap
grant shape/chance/rate/count
summon policy
catalog hash
```

## Supported quest branch

A supported branch may read:

```text
QuestState state
cond
<=4 declared variables
target NPC
summon flag
current quest-item count
current rate config
```

It may perform only:

```text
0 or 1 bounded quest-item grant
optional sound/message
```

Everything else is unsupported.

## Background quest conservation

For one committed operation:

```text
quest item after
= min(cap, quest item before + exact rule result)
```

No other quest row or item changes are permitted by the quest projection.
