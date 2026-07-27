# Goal 011 — authoritative Game Knowledge

## Status and immutable handoff

```text
Original implementation: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Original parent: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Independent review verdict: FIX_REQUIRED
Corrective task: Goal 011A
Goal 011A status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Branch: feature/phantom-world
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```

Goal 011 создал immutable authoritative Game Knowledge, но независимый аудит
нашёл четыре factual/query-contract дефекта: переупорядочивание drop lists до
назначения ordinals, self-referential parity, widening запрошенных пустых
target filters и unbounded nested spawn-area responses. Goal 011A устраняет
только эти findings. Goal 012/013 не начаты.

## Сохранённая bounded datapack correction

Единственная принятая datapack correction Goal 011 не менялась:

```text
Source: data/stats/npcs/29100-29199.xml
NPC ID: 29181
Item ID: 57
Old: <item id="57" min="9000000" max="1100000" chance="100" />
Current: <item id="57" min="9000000" max="11000000" chance="100" />
```

Curated knowledge XML, loaders, `PhantomSystem`, config и DB schema также
заморожены.

## Архитектурная граница

Game Knowledge остаётся одним language-independent immutable snapshot:

```text
loaded server facts
static Seeds.xml facts
accepted topology snapshot
strict curated recommendations
→ deterministic validation
→ complete immutable indexes
→ atomic publication
→ map/index/page-only queries
```

Production path не использует DB, worker, executor, raw thread, per-profile
state, lazy reload или automatic query.

## Исправленная drop/spoil truth

Grouped death facts получают:

```text
groupOrdinal = index in NpcTemplate.getDropGroups()
itemOrdinal = index in DropGroupHolder.getDropList()
```

Ungrouped death и spoil получают `groupOrdinal = -1` и точный list index как
`itemOrdinal`. Ни groups, ни holders не сортируются до назначения ordinal.
Canonical snapshot/hash order применяется только к уже скопированным facts.

Known Zaken regression подтверждает runtime-порядок первой группы:

```text
head item = 13144
tail item = 13143
```

Он намеренно отличается от item-ID order. `npcDropSpoilHash` включает runtime
group/item ordinals: перестановка outer collection hash не меняет, перестановка
item или group ordinal меняет.

## Independent parity

Expected facts больше не строятся повторным вызовом knowledge backend.
`PhantomGameKnowledgeParitySuite` напрямую использует:

- `ItemData` для всех item facts;
- `NpcData` и точные `NpcTemplate` lists для NPC/drop/spoil facts;
- `SpawnTable` и `MapRegionData` для всех spawn facts;
- `RecipeData` для recipe cardinality, list identities, fields и ingredients;
- отдельный strict parser для static manor.

На текущем corpus проверяются `19200` items, `10482` NPC templates, `56483`
death-drop facts, `7335` spoil facts, `42283` spawn facts и `1000` recipes.

`RecipeData` содержит две loaded recipes с recipe-item ID `5008`. First-match
`getRecipeByItemId()` неоднозначен и больше не используется для дедупликации.
Backend обнаруживает duplicate item identity, реконструирует обе записи через
bounded public `getRecipeList(listId)`, сверяет exact list count и item-ID
multiset. Если полное уникальное list resolution невозможно, build fail-closed
с категорией `ambiguity`; silent `continue` отсутствует.

## Query truth и public bounds

Для `topologyNodeId`, `mapRegionLocId`, `dropsItemId`, `spoilsItemId`:

```text
null → фильтр не запрошен
значение + отсутствующий index entry → requested empty set
любой requested empty set → empty page
```

Unknown/known-empty topology, unknown map, valid item without drop/spoil,
unknown item, empty intersection и arbitrary cursor на empty result покрыты
focused suite.

Complete internal spawn facts/areas сохранены. Public API:

```text
spawnAreas(npcId, page) → KnowledgePage<SpawnAreaSummary>
spawnFacts(npcId, page) → KnowledgePage<SpawnFact>
```

`SpawnAreaSummary` не содержит representative points. `TargetFact` содержит
total area count, truncation flag и не более `64` summaries. Exact points
доступны только через paged `spawnFacts`, максимум `256` на страницу.

## Hashes и diagnostics

Текущий deterministic corpus:

```text
itemsHash=b1f91522bcd0dbc16aaa2e0207752a17dd1b8b348bbe2aebf45c35bb303ad435
npcDropSpoilHash=b1a5bc2ee6d9be11c1d5976701ad025a1435db67abae095517eb16b629089615
spawnHash=94280ba0e38d355ed55ebf22174b7d99c91edf2c22835dac972f299d574009df
recipeHash=ca467b38946328aecb3f23948c124305fbfccc5b4479c8e3b78e6c0509ef9594
manorHash=991eed8c95c8a723f0d2f08e75a46e36ed1180081e488c632a9a4b9367dd39dc
classCapabilityHash=e8e548fe90d8d9d0e9e852030bf4f48011aacaf892bad58da001be14534674d9
contentRequirementHash=4dd788339b9fe141dbc4073cb90ee8e53542ca39cff5b59efc6fc64f4e2a1c37
topologyHash=f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
combinedHash=bada3c9f2de5c925e32dff959bcdfed0b9ed8060e508cc67072ae66ae952a554
```

`ServiceSnapshot` публикует один immutable hashes record со всеми девятью
значениями. Inactive/failed states используют только `none`.

## Проверки Goal 011A

Focused smoke до final verification:

```text
knowledge-core: 50/50
knowledge-parity: 21/21
knowledge-content: 18/18
knowledge-query-truth: 13/13
knowledge-performance: 8/8
phantom-skeleton: 12/12
```

Performance route выполняет по `100000` item source, recipe reverse, class
capability и bounded target queries, не обращаясь после build к loaders/files/DB.
Final repeated runs, cumulative regressions, `verify`, `jar` и verifier
фиксируются в отчёте Goal 011A.

## Scope и ограничения

- Другие хроники не изменялись.
- Zaken fix и другие datapack files не изменялись.
- Curated XML, loaders, `PhantomSystem`, config и schema не изменялись.
- Production DB `l2jmobiush5` не использовалась.
- Реальные geodata files являются пользовательскими untracked artifacts и не
  входят в Goal 011A.
- Semantic Pack, combat/actions, movement, party solver, Goal 012 и Goal 013 не
  реализовывались.

## Git

Goal 011A создаёт один ordinary child commit от `dc4659fe...` с subject:

```text
fix(phantoms): harden game knowledge parity and queries
```

Точный commit SHA и push result передаются во внешнем final handoff.

## Next step

Нужен независимый review Goal 011A по exact scope, runtime loader order,
independent parity, recipe list preservation, query truth, nested bounds,
component hashes и cumulative gates. До его принятия:

```text
Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```
