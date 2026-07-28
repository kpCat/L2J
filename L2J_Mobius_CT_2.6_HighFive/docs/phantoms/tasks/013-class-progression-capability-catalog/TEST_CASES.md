# TEST CASES — Goal 013

Catalog:

- all PlayerClass facts, no cycles and exact terminal reconstruction;
- all class skill learns and referenced IDs;
- item/equipment and summon/pet completeness;
- deterministic hashes.

Runtime:

- intrinsic/learned/ready separation;
- representative tank/heal/recharge/buff/damage/spoil/craft/summon matrix;
- target scope and current actor state.

Operations:

- exact class skill learn with SP/item conservation;
- trainer, previous-level, level, item and cancellation failures;
- exact canonical equip and foreign/incompatible rejection;
- profession CANONICAL_QUEST_REQUIRED and observed canonical change.

Real:

- canonical EXP/SP/level observation;
- real trainer skill acquisition;
- real owned item equip;
- subclass/Noble snapshot;
- real servitor/pet facts;
- no packet/direct profession/XP mutation.

Research:

- required normalized docs;
- source paths and stable IDs;
- no raw turn citations, class rankings or scope leakage.
