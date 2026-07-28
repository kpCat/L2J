# Independent review findings — Goal 013

## Verdict

`FIX_REQUIRED / Goal 013A`

The dependency direction is fundamentally sound, so
`ARCHITECTURE_RECONSIDERATION_REQUIRED` is not warranted. The defects are
bounded to progression capability/resource/summon/equipment contracts and their
tests.

## Accepted findings

- Goal 013 is one ordinary child of the expected baseline.
- Scope is confined to the High Five module and the Goal 013 allowlist.
- Root `.gitignore` and geodata are absent from the commit.
- No production profession mutation, synthetic EXP/level grant, request packet,
  NPC bypass, direct paperdoll insertion, new executor or per-profile task was
  found.
- Class graph, terminal reconstruction, Male/Female Soul Hound and
  Inspector/Judicator identities are data-derived.
- Active/base class, canonical learned skills and immutable snapshots are
  separate.
- Catalog does not import tactical doctrine and contains no central class
  switch.
- Lifecycle, disabled behavior, operation serialization and shutdown ordering
  are structurally sound.
- Research normalization separates current-server facts, recommendations,
  disputed retail claims and deferred goals.

## Direct defects

### F-013-01 — variant identity is too narrow

`L2jProgressionBackend.copyCapabilityRules` rejects a second
`(classId, capabilityKey)`. `PhantomProgressionCapabilityEvaluator` selects the
first learned evidence skill. Multiple alternatives of one capability cannot be
represented/evaluated independently.

### F-013-02 — READY_NOW omits skill item consumption

`addCapability` supplies an empty required-item list. The canonical readiness
probe checks conditions, MP/HP and reuse but not `Skill.itemConsumeId/count`.
A capability may be reported ready without its required item.

### F-013-03 — required-item reference validation is dead

`PhantomProgressionCatalogBuilder` checks:

```java
!equipmentIds.contains(item.itemId()) && item.itemId() <= 0
```

`RequiredItem` already requires a positive ID, so the branch cannot validate
real required-item references. It also compares consumables to the equippable
item subset.

### F-013-04 — cubic/summon command truth is incorrect or insufficient

All parsed raw summons, including cubics, receive follow/hold/move/attack flags.
A cubic is not a separate `Playable` body. Servitor own skills/mechanics and
runtime body state are insufficient for future coordinated tactics.

### F-013-05 — equipment candidates are pre-ranked globally

Owned items are cut to a global bounded set by compatibility, crystal grade,
enchant and item-ID tie-break. Future contextual doctrine can never see an
excluded situational item. The API exposes a final-looking preference score
before context exists.

### F-013-06 — production catalog composition is not tested

Goal 013 real-loader fixtures use inert Game Knowledge. Production
`PhantomSystem` merges ordinary Game Knowledge class capabilities with Goal 013
progression seeds. The reported 17 capability rules and combined hash therefore
describe the inert fixture, not the normal production composition.

### F-013-07 — skill-learning partial-failure safety is not proven

Required items are destroyed sequentially. A failure after an earlier
successful destruction returns failure without restoring the prefix. The
focused tests do not inject this boundary.

### F-013-08 — combat resolver collapses the first static-rank match

`PhantomCombatCapabilityResolver` orders capability evidence by static `rank`
and returns on the first matching capability group. For ranged magic it returns
an empty result immediately when that first entry has no supported skill,
without trying a later variant. Static catalog rank therefore becomes an
implicit winner and future same-group variants would be lost.

## Test gaps

- “60 catalog cases” are 20 synthetic variants repeated three times.
- “40 runtime cases” are 16 synthetic variants repeated.
- operation tests mostly pass configured synthetic backend statuses through.
- no production Game Knowledge + progression composition suite;
- no real main/subclass/main isolation;
- no real servitor/pet/cubic runtime proof;
- no >64 mixed equipment reachability test;
- no same-capability multi-variant test;
- no skill consumable READY_NOW negative test;
- no multi-item partial-failure injection;
- no structural proof that a new doctrine avoids persistence/scheduler/
  materialization changes.

## Documentation/provenance gaps

- normalized documents are scoped correctly but do not use granular stable
  claim IDs;
- some normalized wording describes a broader capability matrix than the
  fixture-only Goal 013 count/hash proves;
- fixture and production composition hashes are not distinguished.

## Out of scope, not defects

- full class PvE/PvP/party tactics;
- target priorities and matchup logic;
- song/dance set selection;
- summon command execution;
- party/Rift/raid/epic coordination;
- item buying, selling, enchant or augmentation;
- automatic profession/subclass/Noble progression.

## Required disposition

Correct F-013-01 through F-013-08 and add structural proof. Do not implement
future tactical depth.
