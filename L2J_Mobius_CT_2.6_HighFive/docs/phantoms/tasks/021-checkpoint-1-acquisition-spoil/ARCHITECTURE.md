# Goal 021 Checkpoint 1 — architecture

## Dependency direction

```text
acquire.item Goal
→ acquisition source planner
→ immutable Game Knowledge/topology/progression facts
→ acquisition.state
→ Decision step
→ existing navigation/combat/background owner
→ canonical inventory observation/transaction
→ acquisition progress
```

No acquisition class may own Player inventory or create a second combat loop.

## Truth owners

| Fact | Owner |
|---|---|
| item/NPC/drop/spoil/recipe facts | Game Knowledge |
| topology node/anchor | Topology snapshot |
| actual known active skills | Progression/materialized actor |
| durable background skills | background.state auto-get evidence |
| active monster/spoil/corpse state | canonical Monster/skill mechanics |
| active combat and pickup | Combat service |
| background item/progress mutation | Background transaction |
| actual inventory count | Player/background state |
| desired acquisition delta | acquire.item Goal |
| source choice and chain progress | acquisition.state |
| craft transaction | Goal 022 |

## No-resource-without-source invariant

Every positive target-item delta has a durable method, exact source ID, authority
hashes, before/after count and operation ID. No source proof means no progress.

## Incremental amount invariant

Goal progress is `current inventory count - acquisition baseline count`. It never
counts pre-existing inventory or probability alone.

## Active spoil invariant

```text
canonical spoil cast → canonical kill → canonical sweep cast
→ authoritative inventory observation
```

Acquisition never changes Monster spoil fields or Player inventory.

## Background parity invariant

Background uses the same exact Game Knowledge spoil facts and capability
evidence as active mode. Only execution differs.

## Recipe boundary

A recipe plan is a dependency graph, not a reservation, craft attempt or promise.

## Checkpoint 2 boundary

Manor and quest collection require separate canonical state owners. Checkpoint 1
may preserve/defer these leaves but cannot simulate or execute them.
