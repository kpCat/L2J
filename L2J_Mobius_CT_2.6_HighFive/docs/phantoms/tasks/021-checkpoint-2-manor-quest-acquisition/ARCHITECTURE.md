# Goal 021 Checkpoint 2 — architecture

## Dependency direction

```text
acquire.item Goal
→ acquisition source planner
→ static/dynamic manor authority OR curated quest rule
→ acquisition.state method binding
→ active canonical handler/event path OR background projection
→ canonical inventory truth
→ baseline-derived Goal progress
```

No canonical manor or quest implementation depends on Phantom code.

## Truth owners

| Fact | Owner |
|---|---|
| static seed/crop/reward relation | Game Knowledge Seeds.xml facts |
| current seed object/mode/rate | CastleManorManager/config/item data |
| castle area | MapRegionData/current topology point |
| live seed/harvest target state | canonical Attackable/Monster |
| seed consumption | Sow effect |
| active crop grant | Harvesting effect |
| kill and ordinary loot | Combat/server reward path |
| quest state/cond/vars | QuestState/character_quests |
| active quest item grant | loaded quest script OnAttackableKill |
| supported background quest formula | source-hashed curated rule |
| actual item count | active inventory/background canonical rows |
| source and chain phase | acquisition.state |

## Manor causal invariant

```text
owned seed
→ canonical sow attempt and seed consumption
→ observed exact seeded target/seeder
→ canonical kill
→ canonical harvest attempt
→ observed crop delta
```

No step may be inferred merely from a static ManorFact.

## Quest causal invariant

Active:

```text
exact started quest
→ exact supported target kill
→ real delayed quest callback
→ observed quest item delta
```

Background:

```text
exact locked quest state
→ source-hashed pure collection rule
→ deterministic bounded roll
→ one atomic quest-item/background/Goal/acquisition transaction
```

## Quest scope invariant

Checkpoint 2 never:

```text
starts a quest
turns in a quest
completes dialogue
changes quest state/cond/vars
interprets arbitrary Java
```

## Transition invariant

An active sow, harvest or quest callback in an uncertain state blocks background
projection until exact reconciliation.

## Goal 022 boundary

Recipe, manor crop and quest items may be planned/acquired. Craft execution,
trade, private stores, crop exchange and enchant remain Goal 022.
