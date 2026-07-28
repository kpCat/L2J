# Source of truth — Goal 013A

Priority is current High Five server truth.

## Class/progression

- `PlayerClass`
- `SkillTreeData`
- class skill-tree XML
- `Player` active/base/subclass state
- `VillageMaster` and `PlayerConfig`
- canonical skill acquisition code and `SkillLearn`

## Skill/resource

- `SkillData`
- `Skill`
- effect and condition objects
- exact item, MP, HP, charge/soul and reuse APIs exposed by current server code

## Equipment

- `ItemData`
- `ItemTemplate`
- concrete `Item`
- canonical inventory/equip methods and item conditions

## Summon/pet/cubic

- summon effect XML/handlers
- `NpcData` / `NpcTemplate`
- `Summon`, `Servitor`, `Pet`, `BabyPet`, `Cubic`
- `PetDataTable` and pet XML
- current server Olympiad/attribute/command behavior where relevant to facts

## Accepted Phantom dependencies

- existing Game Knowledge query is read-only input;
- progression adapts it without changing Game Knowledge production code/data;
- existing actor/materialization lease owns mutable `Player` access;
- existing combat resolver consumes generic capability facts only.

External retail/forum claims never override current code/datapack facts.
Disputed 20/80 or other claims remain documented as disputed and are not used as
runtime truth.
