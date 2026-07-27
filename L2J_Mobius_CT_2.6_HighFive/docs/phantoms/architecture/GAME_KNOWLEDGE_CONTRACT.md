# Game Knowledge contract

## Назначение

Game Knowledge — один immutable, language-independent snapshot механических
фактов High Five и versioned рекомендаций. Он связывает стабильные item, NPC,
recipe, class, skill, topology и content IDs. Snapshot не принимает решений,
не создаёт goals/actions и не выполняет movement, combat, commerce или party
optimization.

## Authority

Каждый опубликованный факт имеет одну authority:

- `SERVER_LOADER_FACT` — immutable-копия `ItemData`, `NpcData`,
  `SpawnTable`, `RecipeData`, `SkillTreeData`, `SkillData` или `PlayerClass`;
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
- canonical group/item ordinals.

Raw chance не является фактической вероятностью результата одного runtime
kill. Runtime результат дополнительно зависит от rates, level difference,
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
не публикуется. Lazy build, reload, worker, executor, thread, task и
per-profile state отсутствуют.

Disabled Phantom path не создаёт service и не читает loaders/XML. Inert test
path публикует пустой immutable snapshot. Shutdown сначала запрещает новые
query acquisitions, затем очищает service-owned query/snapshot; уже выданные
immutable value objects остаются безопасными.

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

Внутренний индекс не обрезается. Превышение fixed policy bounds блокирует
build.

## Query contract

Query принимает только опубликованный snapshot и metrics. Разрешены direct
map lookup, bounded page slicing и bounded filtering/merge уже построенных
indexes. Loader, file, XML и DB scan в query path запрещены.

Страница содержит `1..256` фактов и stable fact-key cursor. Target lookup:

- level range width не более `100`;
- merge только requested level buckets;
- optional topology node, map region, NPC kind, attackable/targetable,
  `canBeSown`, drop-item и spoil-item filters;
- порядок: distance от preferred level, затем NPC level, затем NPC ID;
- результат не утверждает, что NPC жив, доступен или достижим сейчас.

Target lookup является factual filtering, а не Utility AI ranking.

## Spawn и topology

Каждый loaded spawn сохраняет instance, coordinates, amount, location ID и
явный `EXACT` либо `TERRITORY_OR_UNRESOLVED`. Только exact point может быть
сопоставлен через `mostSpecificNode` принятого immutable topology snapshot.
Отсутствующий node не выдумывается.

Area summary группирует NPC/instance/topology node/map region, хранит полный
count и total configured amount, а representative points ограничены `256`.
Runtime spawn changes после one-time build не отслеживаются.

## Recipe graph

Каждый уникальный loaded recipe list сохраняет recipe item, normal/rare
product, craft level, success rate, dwarven flag и полный ingredient list.
Полные reverse edges строятся от normal/rare product и каждого ingredient.

## Curated capability/content

XML имеет `schemaVersion`, `datasetId`, `datasetVersion`; неизвестные
elements/attributes и duplicate identities отклоняются. Capability требует:

- существующий `PlayerClass` ID;
- rank `1..1000`;
- factual skill ID/level из complete class skill tree;
- существующий source path;
- authority `CURATED_RECOMMENDATION`.

Production corpus покрывает каждый terminal `PlayerClass` хотя бы одной
combat/profession capability и все обязательные capability keys.

Content recommendation требует существующие capability keys и source paths,
валидные optional NPC/topology references и хотя бы один класс, удовлетворяющий
каждому minimum rank/count. Rift, RaidBoss и GrandBoss entries — стартовый
recommendation set, а не server admission enforcement и не полная стратегия
party composition для каждого encounter.

## Hashes и diagnostics

Каждый компонент и combined snapshot используют SHA-256 и length-prefixed
canonical encoding. Exact doubles кодируются raw IEEE bits. Порядок loader/XML
collections, wall clock, object identity и localized names в hash не входят.
Combined hash включает имена компонентов, component hashes, topology hash,
schema/dataset/generation.

Diagnostics и metrics агрегированы и имеют fixed categories: build
started/completed/failed, fact counts, query categories, pages, target
candidates, rejected queries, parity failures и build duration. Dynamic
item/NPC/class/content metric labels и per-fact INFO/WARNING отсутствуют.

## Ограничения покрытия

- snapshot отражает startup loader state, а не последующие world mutations;
- manor содержит static schema, а не текущую castle economy;
- content corpus является небольшим evidence-backed starting set;
- нет natural-language/Russian parsing, Semantic Pack, action execution,
  combat kernel, commerce или Goal 012/013 behavior;
- runtime reachability без geodata не утверждается.
