# DATA MODEL — Goal 011

Immutable facts:

```text
ItemFact
NpcFact
DropFact
SpawnFact / SpawnAreaFact
RecipeFact / IngredientFact
ManorFact
ClassIntrinsicFact
ClassCapabilityFact
ContentRequirementFact
```

All identities are numeric server IDs or bounded stable keys. Localized names
are not identity and are excluded from canonical hashes.

Required reverse indexes:

```text
item -> death-drop NPC facts
item -> spoil NPC facts
item -> static manor facts
NPC -> drops/spoil/spawns/areas
level/topology/map-region -> attackable targets
recipe/product -> ingredients
ingredient -> recipes/products
class -> intrinsic/capability facts
capability -> classes/content requirements
```

Every internal index is complete within policy bounds. Public result pages are
bounded and deterministic; internal facts are never silently truncated.

Canonical SHA-256 components:

```text
items
NPC/drop/spoil
spawns
recipes
manor
class capabilities
content requirements
topology
combined
```
