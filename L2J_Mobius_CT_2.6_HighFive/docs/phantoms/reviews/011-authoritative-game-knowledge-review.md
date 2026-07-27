# Independent review Goal 011 — authoritative Game Knowledge

## Verdict

```text
Reviewed commit: dc4659fea3e76a78841dfee0429bc4ab1ed2b185
Verdict: FIX_REQUIRED
Corrective task: Goal 011A
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```

## Findings

### P1 — runtime drop order was replaced by canonical content order

`L2jGameKnowledgeBackend` sorted drop holders, then sorted groups, and only
после этого назначал `groupOrdinal`/`itemOrdinal`. Эти ordinals не отражали
порядок, который реально использует `NpcTemplate` runtime.

Concrete regression: первая loaded группа Zaken, NPC `29181`, начинается с
item `13144` и заканчивается item `13143` после loader chance ordering. Snapshot
Goal 011 представлял её в item-ID order.

### P1 — parity reused the system under test

Parity fixture повторно вызывала тот же `L2jGameKnowledgeBackend` и сравнивала
его output со snapshot. Такая проверка не могла обнаружить пропуск,
переупорядочивание или reinterpretation loader facts.

### P1 — requested empty target filter widened the result

Отсутствующая запись в topology/map/drop/spoil index превращалась в `null`,
который query интерпретировал как «фильтр не задан». Unknown и known-empty
filter мог вернуть нефильтрованный target page.

### P2 — nested public spawn areas bypassed bounds

Public `spawnAreas()` возвращал internal `SpawnAreaFact` с nested exact points,
а `TargetFact` включал полный unpaged `spawnAreasByNpc`. Формальный page bound
не ограничивал фактический response size.

### P2 — recipe ambiguity could silently omit a loaded list

`RecipeData.getAllItemIds()` содержит одну запись на loaded recipe, а
`getRecipeByItemId()` возвращает первый match. При duplicate recipe-item ID
backend повторно получал одну list identity и выполнял silent `continue`,
теряя вторую recipe.

### P2 — lifecycle diagnostics omitted component hashes

Immutable snapshot имел component hashes, но `ServiceSnapshot` публиковал
только combined hash.

## Required closure

Goal 011A должен доказать:

- exact `NpcTemplate` group/item и ungrouped death/spoil list ordinals;
- независимую реконструкцию expected facts из public loaders;
- отсутствие silent recipe omission и fail-closed unresolved ambiguity;
- empty page для любого requested empty filter;
- lightweight public area summaries без points;
- максимум `64` summaries в `TargetFact`;
- exact points только через paged `spawnFacts`;
- все component hashes в service diagnostics;
- unchanged Zaken fix, curated XML, loaders, lifecycle, config/schema;
- все focused и cumulative gates.

## Review boundary после Goal 011A

Этот файл не принимает corrective implementation автоматически. После ordinary
commit Goal 011A нужен новый независимый review exact child commit. До него:

```text
Goal 011: FIX_REQUIRED
Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 012: BLOCKED
Goal 013: NOT_STARTED
```
