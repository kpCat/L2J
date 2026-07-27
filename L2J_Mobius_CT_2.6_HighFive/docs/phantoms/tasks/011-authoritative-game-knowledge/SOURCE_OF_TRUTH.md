# SOURCE OF TRUTH — Goal 011

## Authorities

```text
SERVER_LOADER_FACT
STATIC_DATAPACK_FACT
TOPOLOGY_SNAPSHOT_FACT
CURATED_RECOMMENDATION
```

## Loader facts

Copy immutable values from already loaded:

- `ItemData`;
- `NpcData` / `NpcTemplate` grouped drops, ungrouped drops and spoil;
- `SpawnTable`;
- `RecipeData`;
- `PlayerClass`, `SkillTreeData`, `SkillData`;
- accepted Goal 010 topology snapshot.

Never retain mutable loader objects.

## Static datapack facts

Parse `data/Seeds.xml` with a dedicated strict read-only parser. Do not call or
construct `CastleManorManager`; it performs DB reads and task scheduling.

## Curated recommendations

Class capabilities and content requirements live in strict versioned Goal 011
XML. Every statement cites stable factual IDs and source paths. Curated data is
recommendation metadata, never a mechanical server-enforcement claim.

## Drop semantics

Preserve raw group/item chance, group/item ordering model and count bounds.
Runtime rates, level gap, premium, champion, raid and seeded state remain outside
the snapshot.
