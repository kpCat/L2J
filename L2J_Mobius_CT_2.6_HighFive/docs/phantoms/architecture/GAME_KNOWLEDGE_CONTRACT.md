# Game Knowledge contract

## Назначение

Game Knowledge — один immutable, language-independent snapshot механических
фактов High Five и versioned рекомендаций. Он связывает стабильные item, NPC,
recipe, class, skill, topology и content IDs. Snapshot не принимает решений,
не создаёт goals/actions и не выполняет movement, combat, commerce или party
optimization.

## Authority

Каждый опубликованный факт имеет одну authority:

- `SERVER_LOADER_FACT` — immutable-копия `ItemData`, `NpcData`, `SpawnTable`,
  `RecipeData`, `SkillTreeData`, `SkillData` или `PlayerClass`;
- `STATIC_DATAPACK_FACT` — статические связи из `data/Seeds.xml`;
- `TOPOLOGY_SNAPSHOT_FACT` — привязка точного spawn к immutable topology
  snapshot Goal 010;
- `CURATED_RECOMMENDATION` — versioned capability/content рекомендация,
  подтверждённая стабильными IDs и существующими source paths.

Loader objects, XML DOM, DB connections/results, `Player`, `Creature`,
`NpcTemplate`, `ItemTemplate`, `Spawn`, `RecipeList` и `Skill` не переходят
границу snapshot.

## Drop и spoil

Сохраняются отдельно:

- grouped death drop и ungrouped death drop;
- spoil;
- `GROUP_CUMULATIVE` и `UNGROUPED_INDEPENDENT`;
- raw group/item chance fields;
- raw minimum/maximum count fields;
- runtime/source group и item ordinals.

Для grouped death drop `groupOrdinal` равен индексу в
`NpcTemplate.getDropGroups()`, а `itemOrdinal` — индексу в
`DropGroupHolder.getDropList()`. Для ungrouped death и spoil
`groupOrdinal = -1`, а `itemOrdinal` равен точному индексу loader list.
Сортировка holders или groups до назначения ordinal запрещена. После копирования
immutable facts разрешена canonical сортировка по уже назначенным ordinals.

Raw chance не является фактической вероятностью результата одного runtime kill.
Runtime результат дополнительно зависит от rates, level difference,
premium/champion/raid modifiers, seeded state и item-specific overrides.
Game Knowledge не вычисляет effective chance, expected value или
probability-based ranking.

## Static manor

`PhantomStaticManorParser` строго и read-only разбирает `data/Seeds.xml`.
Snapshot содержит только castle, seed/crop/mature/reward связи, seed level,
alternative flag и raw limits. `CastleManorManager`, current procurement,
prices, amounts, DB и manor tasks не используются.

## Build и lifecycle

Production build выполняется ровно один раз:

```text
topology RUNNING
→ Game Knowledge BUILDING
→ complete candidate validation
→ atomic immutable publication
→ Game Knowledge RUNNING
→ scheduler start
```

Состояния: `NEW`, `BUILDING`, `RUNNING`, `FAILED`, `STOPPED`. Partial snapshot
не публикуется. Lazy build, reload, worker, executor, thread, task и per-profile
state отсутствуют.

Disabled Phantom path не создаёт service и не читает loaders/XML. Inert test
path публикует пустой immutable snapshot. Shutdown сначала запрещает новые query
acquisitions, затем очищает service-owned query/snapshot; уже выданные immutable
value objects остаются безопасными.

## Complete indexes

Snapshot строит полные immutable indexes:

```text
itemById
npcById
dropSourcesByItem
spoilSourcesByItem
manorFactsByItem
dropFactsByNpc
spoilFactsByNpc
spawnFactsByNpc
spawnAreasByNpc
npcsByTopologyNode
npcsByMapRegion
npcsByLevel
recipeByListId
recipesByProduct
recipesByIngredient
classFactsByClassId
capabilitiesByClassId
classesByCapability
contentById
contentByCapability
```

Внутренний индекс не обрезается. Превышение fixed policy bounds блокирует build.

## Query contract

Query принимает только опубликованный snapshot и metrics. Разрешены direct map
lookup, bounded page slicing и bounded filtering/merge уже построенных indexes.
Loader, file, XML и DB scan в query path запрещены.

Страница содержит `1..256` фактов и stable fact-key cursor. Для optional target
filters действуют два разных состояния:

- `null` — фильтр не запрошен;
- поле задано — используется requested exact set, в том числе пустой.

Если заданный `topologyNodeId`, `mapRegionLocId`, `dropsItemId` или
`spoilsItemId` отсутствует в соответствующем index, результатом является empty
page. Пустой set не превращается в отсутствие фильтра. Пересечение всех
запрошенных sets точное.

Target lookup:

- level range width не более `100`;
- merge только requested level buckets;
- optional topology node, map region, NPC kind, attackable/targetable,
  `canBeSown`, drop-item и spoil-item filters;
- порядок: distance от preferred level, затем NPC level, затем NPC ID;
- результат не утверждает, что NPC жив, доступен или достижим сейчас.

`TargetFact` содержит total spawn-area count, флаг truncation и не более `64`
`SpawnAreaSummary`. Target lookup является factual filtering, а не Utility AI
ranking.

## Spawn и topology

Каждый loaded spawn сохраняет instance, coordinates, amount, location ID и
явный `EXACT` либо `TERRITORY_OR_UNRESOLVED`. Только exact point может быть
сопоставлен через `mostSpecificNode` принятого immutable topology snapshot.
Отсутствующий node не выдумывается.

Complete internal `SpawnAreaFact` может хранить до `256` representative points.
Public `spawnAreas(npcId, page)` возвращает только lightweight
`SpawnAreaSummary` без nested points. Точные точки доступны исключительно через
paged `spawnFacts(npcId, page)` с пределом `256`. Runtime spawn changes после
one-time build не отслеживаются.

## Recipe graph

Каждый уникальный loaded recipe list сохраняет recipe item, normal/rare product,
craft level, success rate, dwarven flag и полный ingredient list. Полные reverse
edges строятся от normal/rare product и каждого ingredient.

`RecipeData.getAllItemIds()` задаёт точную loaded cardinality. Если recipe-item
IDs неоднозначны, backend не использует first-match lookup для исключения
дубликатов: все записи реконструируются через bounded public
`RecipeData.getRecipeList(listId)`, а item-ID multiset и list count сверяются.
Если уникальные list identities нельзя восстановить целиком в пределах policy,
build завершается fail-closed с категорией `ambiguity`. Silent `continue` и
потеря recipe запрещены.

## Curated capability/content

XML имеет `schemaVersion`, `datasetId`, `datasetVersion`; неизвестные
elements/attributes и duplicate identities отклоняются. Capability требует
существующие class/skill IDs, rank `1..1000`, существующий source path и
authority `CURATED_RECOMMENDATION`.

Production corpus покрывает каждый terminal `PlayerClass` хотя бы одной
combat/profession capability и все обязательные capability keys. Rift, RaidBoss
и GrandBoss entries — стартовый recommendation set, а не server admission
enforcement и не полная стратегия party composition.

## Hashes и diagnostics

Каждый компонент и combined snapshot используют SHA-256 и length-prefixed
canonical encoding. Exact doubles кодируются raw IEEE bits. Runtime/source drop
ordinals входят в `npcDropSpoilHash`; изменение ordinal меняет hash, а изменение
только внешнего порядка immutable facts — нет.

`ServiceSnapshot` публикует fixed immutable hashes record:

```text
itemsHash
npcDropSpoilHash
spawnHash
recipeHash
manorHash
classCapabilityHash
contentRequirementHash
topologyHash
combinedHash
```

Для inactive/failed generation все значения равны `none`. Diagnostics не
содержат raw facts, IDs или source paths. Metrics агрегированы и имеют fixed
categories: build started/completed/failed, fact counts, query categories,
pages, target candidates, rejected queries, parity failures и build duration.

## Ограничения покрытия

- snapshot отражает startup loader state, а не последующие world mutations;
- manor содержит static schema, а не текущую castle economy;
- content corpus является небольшим evidence-backed starting set;
- нет natural-language/Russian parsing, Semantic Pack, action execution,
  combat kernel, commerce или Goal 012/013 behavior;
- runtime reachability без geodata не утверждается.
