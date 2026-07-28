# Architecture contract — Goal 013A

## 1. Capability composition

```text
CapabilityGroup
  key
  factual semantics

CapabilityVariant
  classId
  capabilityKey
  variantKey
  exact action/evidence skill
  exact target/equipment/resource requirements
  provenance
  catalog rank metadata

CapabilityEvaluation
  variant identity
  INTRINSIC
  LEARNED
  READY_NOW
  exact failure reason
```

`variantKey` is stable data identity. It is not a localized name and not a
tactical score.

A class may have:

- several capability groups;
- several variants in one group;
- several simultaneous ready variants.

No value overwrites another merely because it has the same capability group.

## 2. Fact/policy boundary

Catalog facts may state:

- the skill exists and belongs to the active class tree;
- the actor learned it;
- exact resources/equipment/state are available;
- exact target/condition/reuse checks pass.

Catalog facts may not state:

- this is the best action;
- this is the class’s permanent role;
- this summon should be selected now;
- this item is globally best;
- this party composition is preferred.

## 3. Controlled actor boundary

A body-bearing summon/pet snapshot and a cubic fact are different shapes.

Body facts are immutable copies under the existing actor lease. Mutable
`Summon`, `Pet`, `Skill`, `Item`, AI or target objects never leave the lease.

A cubic has no fabricated object body, coordinates, HP/MP or movement commands.

Canonical `Player` combat state represents CP separately from HP/MP as
`currentCp` and `maximumCp`. These values are copied under the existing actor
lease and remain immutable snapshot facts. Controlled actors do not receive
fabricated Player CP.

## 4. Equipment boundary

The catalog exposes raw authoritative item/template facts and a bounded way to
reach all matching owned items. Ordering is deterministic identity ordering.

Tactical/economic scoring is injected later by doctrine. It is not stored in
`OwnedEquipmentFact` as a universal preference.

## 5. Dependency direction

```text
server loaders/data
      ↓
progression factual adapters
      ↓
immutable catalog/query ports
      ↓
future doctrine/provider
      ↓
planner/combat semantic action
      ↓
canonical action facade
```

Forbidden reverse edges:

- progression → doctrine/planner;
- catalog → combat mode;
- persistence/scheduler/materialization → class or doctrine tables;
- class-specific code inside generic planner/executor/combat loop.

## 6. Extension proof

After Goal 013A:

- new class already present in canonical server data requires loader/data/test
  coverage, not planner changes;
- new capability group requires semantics/data/provider/tests;
- new variant of an existing group requires data/provider/tests, not central
  contract changes;
- new summon variant requires canonical datapack facts/provider/tests, not a
  class switch;
- new doctrine requires a separate future layer and tests, not catalog,
  persistence, scheduler or materialization migration.
