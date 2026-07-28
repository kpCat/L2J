# DR-01 — class progression

| Claim ID | Нормализованный факт | Authority | Confidence | Source paths |
|---|---|---|---|---|
| `DR01-CLASS-001` | `PlayerClass.values()` задаёт полное пространство class identity текущей хроники: 103 значения. Graph строится по numeric `classId`. | `SERVER_CODE_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java` |
| `DR01-CLASS-002` | Enum parent и XML `parentClassId` skill tree сохраняются как разные факты. | `SERVER_CODE_FACT`, `DATAPACK_FACT` | `HIGH` | `PlayerClass.java`; `dist/game/data/stats/players/skillTrees/**/*.xml` |
| `DR01-CLASS-003` | `SkillTreeData.getCompleteClassSkillTree(PlayerClass)` задаёт наследуемое CLASS learning-множество; direct source, cost, level, previous skill и required items копируются без вывода по именам. | `SERVER_LOADER_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/data/xml/SkillTreeData.java`; `dist/game/data/stats/players/skillTrees/**/*.xml` |
| `DR01-CLASS-004` | Male Soul Hound `132` и Female Soul Hound `133` — разные identity. Inspector `135` наследуется от Warder `126`; Judicator `136` — от Inspector и терминален. | `SERVER_CODE_FACT` | `HIGH` | `java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java` |
| `DR01-CLASS-005` | Profession graph описывает только structurally allowed immediate targets. Общего безопасного non-packet facade для profession quest нет, поэтому mutation не выполняется, а target возвращается как `CANONICAL_QUEST_REQUIRED`. | `CURRENT_SERVER_IMPLEMENTATION` | `HIGH` | `SkillTreeData.java`; `java/org/l2jmobius/gameserver/network/clientpackets/RequestAcquireSkill.java`; `java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java` |

Recommendations и retail claims отсутствуют.
