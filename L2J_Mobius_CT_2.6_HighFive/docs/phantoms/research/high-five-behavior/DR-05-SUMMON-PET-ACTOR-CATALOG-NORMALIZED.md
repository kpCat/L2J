# DR-05 — summon/pet actor catalog

- `Summon` — отдельный `Playable` с собственным AI.
- Follow и hold используют реальные AI intentions; move и attack поддерживаются actor API.
- Soulshot/spiritshot расход на удар берётся из `NpcTemplate`.
- Servitor lifetime, upkeep и EXP multiplier задаются конкретным summon effect.
- Pet имеет control item, food list, inventory, persistent level/EXP и pickup.
- Wyvern присутствует в `PetData` с `itemId=-1`; для него нельзя изобретать `SummonPet` skill identity.
- Baby pet skills берутся из текущего pet XML/loader.
- Servitor pickup текущим API не поддерживается.
- Cubic — controlled actor без отдельного `Playable` object identity.

Каталог только наблюдает actors и mechanics; команды summon/pet combat в Goal 013 не исполняются.

Источники: `Summon`, `Servitor`, `Pet`, `BabyPet`, `Cubic`, `PetDataTable`, `data/stats/pets/*.xml`, summon effect XML.

Authority: `SERVER_CODE_FACT`, `SERVER_LOADER_FACT` и `DATAPACK_FACT`. Confidence: `HIGH`.
