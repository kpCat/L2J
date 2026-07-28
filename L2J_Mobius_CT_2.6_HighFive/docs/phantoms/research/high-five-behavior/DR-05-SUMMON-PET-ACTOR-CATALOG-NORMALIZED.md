# DR-05 — summon/pet actor catalog

| Claim ID | Нормализованный факт | Authority | Confidence | Source paths |
|---|---|---|---|---|
| `DR05-SUMMON-001` | `Summon` — отдельный `Playable` с собственным AI; follow/hold используют AI intentions, move/attack поддерживаются actor API. | `SERVER_CODE_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/Summon.java` |
| `DR05-SUMMON-002` | Soulshot/spiritshot расход на удар берётся из `NpcTemplate`. | `SERVER_CODE_FACT`, `DATAPACK_FACT` | `HIGH` | `Summon.java`; `java/org/l2jmobius/gameserver/model/actor/templates/NpcTemplate.java` |
| `DR05-SUMMON-003` | Servitor lifetime, upkeep и EXP multiplier задаются конкретным summon effect. | `SERVER_LOADER_FACT`, `DATAPACK_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/instance/Servitor.java`; summon effect XML |
| `DR05-SUMMON-004` | Pet имеет control item, food list, inventory, persistent level/EXP и pickup. Servitor pickup текущим API не поддерживается. | `SERVER_CODE_FACT`, `SERVER_LOADER_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/instance/Pet.java`; `Servitor.java`; `java/org/l2jmobius/gameserver/data/xml/PetDataTable.java`; `dist/game/data/stats/pets/*.xml` |
| `DR05-SUMMON-005` | Wyvern присутствует в `PetData` с `itemId=-1`; для него нельзя фабриковать `SummonPet` skill identity. | `DATAPACK_FACT` | `HIGH` | `PetDataTable.java`; `dist/game/data/stats/pets/*.xml` |
| `DR05-SUMMON-006` | Baby pet own heal/recharge/buff evidence берётся из loaded pet skills, а не из owner class. | `SERVER_CODE_FACT`, `DATAPACK_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/instance/BabyPet.java`; `dist/game/data/stats/pets/*.xml` |
| `DR05-SUMMON-007` | Cubic — controlled actor без отдельного `Playable` body; coordinates, HP/MP и body commands для него неприменимы. | `SERVER_CODE_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/instance/Cubic.java`; `java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java` |
| `DR05-SUMMON-008` | Body-bearing runtime snapshot копирует exact object/kind/instance/position/HP/MP/target/dead facts и не фабрикует Player CP. | `CURRENT_SERVER_IMPLEMENTATION` | `HIGH` | `java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java`; `PhantomProgressionModel.java` |
| `DR05-SUMMON-009` | Catalog только наблюдает actor variants и mechanics; summon commands, owner–summon tactical choice и reconciliation Goal 013A не исполняет. | `BOUNDED_SCOPE_CONTRACT` | `HIGH` | `docs/phantoms/tasks/013a-progression-capability-extensibility-hardening/TASK.md`; `docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md` |

Recommendations и disputed retail behavior отсутствуют.
