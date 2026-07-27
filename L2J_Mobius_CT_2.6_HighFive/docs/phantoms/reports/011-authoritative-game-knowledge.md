# Goal 011 — authoritative Game Knowledge

## Status and baseline

```text
Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Parent: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Branch: feature/phantom-world
Subject: feat(phantoms): add authoritative game knowledge
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 012: NOT_STARTED
Goal 013: NOT_STARTED
```

## Bounded authoritative datapack correction

В ходе exhaustive parity найден и по явному bounded scope exception исправлен
один внутренне невозможный authoritative High Five drop range:

```text
Source: data/stats/npcs/29100-29199.xml
NPC ID: 29181
Item ID: 57
Old: <item id="57" min="9000000" max="1100000" chance="100" />
New: <item id="57" min="9000000" max="11000000" chance="100" />
Reason: old maximum was below minimum; confirmed Zaken Adena range is
        9000000..11000000.
```

Изменён только `max`. `min`, item/group chance, group и остальные drop entries
не менялись. Verifier проверяет exact two-line diff и весь authoritative NPC
corpus на `maximumCount >= minimumCount`. Parity читает исправленный raw source
без нормализации, перестановки или исключения.

## Goal 010C closure

Goal 010C независимо принят:

```text
Commit: 7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2
Parent: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Push/remote: exact
Real scheduler integration: 5/5 ×3
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
Final verifier: 67/67 ×2, byte-identical
Verifier SHA-256:
03F88A544D1C2D744B6E493AE3140521C97CBEAD21B0FDC7C17F0AE07CB41BE9
Independent review: ACCEPT
Goal 010: ACCEPT after Goal 010A/010B/010C
Goal 011: ALLOWED
```

Review создан в
`docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md`.

## Factual source audit

Прочитаны и проверены текущие High Five APIs:

- `ItemData` / item template types;
- `NpcData`, `NpcTemplate`, `DropGroupHolder`, `DropHolder`;
- `SpawnData`, `SpawnTable`, `Spawn`, `MapRegionData`;
- `RecipeData`, `RecipeList`, `RecipeHolder`;
- `SkillTreeData`, `SkillData`, `SkillLearn`, `PlayerClass`;
- `CastleManorManager` и `Seed` только для factual boundary audit;
- topology snapshot/query/service;
- `PhantomSystem`, launcher, Ant routes и соседние suites;
- `Seeds.xml`, selected skill-tree, Rift, RaidBoss и GrandBoss sources.

Project `README.md`, отдельный docs code-map и pattern-файлы не найдены.

Локальные паттерны: immutable topology snapshot, loader copying backend,
candidate validation до publication, fixed aggregate metrics, explicit
service lifecycle и focused Ant suites.

## Authority model

Реализованы:

- `SERVER_LOADER_FACT`;
- `STATIC_DATAPACK_FACT`;
- `TOPOLOGY_SNAPSHOT_FACT`;
- `CURATED_RECOMMENDATION`.

Mutable server objects, DOM nodes и DB values не экспортируются.

## Package, model and indexes

Создан `java/org/l2jmobius/gameserver/phantoms/knowledge/**`:

- immutable facts/model;
- bounded compile-time policy;
- High Five loader-copying backend;
- strict static manor и curated XML parsers;
- candidate validator/builder;
- immutable snapshot с полными reverse indexes;
- map/index/page-only query;
- one-build atomic service;
- fixed aggregate metrics.

Полные indexes соответствуют contract: item/NPC, drop/spoil в обе стороны,
manor by item, spawn facts/areas, topology/map/level targets,
recipe/product/ingredient, class/capability, content/capability.

## Item, NPC, drop and spoil facts

Items и NPC копируются полностью из loaders. Grouped death, ungrouped death и
spoil факты сохраняют raw group/item fields, count bounds, source kind,
chance model и canonical ordinals.

Raw chance fields не называются фактической runtime вероятностью. Effective
rate/expected value/ranking не вычисляются.

Production parity:

```text
Items: 19200
NPCs: 10482
Death drop facts: 56483
Spoil facts: 7335
```

## Spawn and topology

Loaded exact/unresolved spawns копируются детерминированно. Exact points
сопоставляются через принятый immutable Goal 010 topology query. Area summary
сохраняет полный count/amount и до 256 representative points. Node не
фабрикуется.

`Spawn.getSpawnLocation()` используется как loader-owned base point, поэтому
runtime random monster offsets не попадают в canonical facts. Territory и
location-based spawns сохраняются явно с `TERRITORY_OR_UNRESOLVED`,
`locationId`, amount и стабильным coordinate sentinel `0/0/0`. Exact points
вне topology world bounds сохраняются без fabricated node.

```text
Spawn facts: 42283
Spawn areas: 3864
Topology mapped: 110
Unmapped exact: 20047
Territory/unresolved: 22126
```

## Recipe graph

Каждый unique recipe list копируется через `RecipeData.getAllItemIds()` и
factual lookup. Normal/rare products и все ingredients имеют полные reverse
indexes.

## Static manor parser

`PhantomStaticManorParser` строго разбирает `data/Seeds.xml`, сохраняет только
static relations/raw limits и не ссылается на `CastleManorManager`, mutable
castle economy, DB или scheduled tasks.

## Class capability corpus

Versioned `high-five-core-v1.xml` содержит 37 capability facts для 36 terminal
High Five classes. Все 12 обязательных keys покрыты. Каждый факт цитирует
skill ID/level из complete class tree и существующий exact source path.

Content suite на реальных class/skill loaders: `18/18 PASS`.

## Content requirement corpus

Созданы evidence-backed `CURATED_RECOMMENDATION` entries:

```text
Rift: rift.high-five-core
Source: data/DimensionalRift.xml

RaidBoss: raid.25001
NPC ID: 25001
Source: data/stats/npcs/25000-25099.xml

GrandBoss/epic: epic.29001
NPC ID: 29001
Source: data/stats/npcs/29000-29099.xml
```

Requirements валидируются по capability key/rank/count. Corpus — starting
recommendation set, не admission enforcement и не party solver.

## Hashes and counts

Snapshot реализует:

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

Используются SHA-256, length-prefixed canonical encoding, stable sorting и raw
IEEE double bits.

```text
itemsHash=b1f91522bcd0dbc16aaa2e0207752a17dd1b8b348bbe2aebf45c35bb303ad435
npcDropSpoilHash=270036907a72b380733e778453f8e2177f318c121fc96692a939ccdeea778c4f
spawnHash=94280ba0e38d355ed55ebf22174b7d99c91edf2c22835dac972f299d574009df
recipeHash=0f867bc030f671659595e5b34aadf3f282c1b837f445e03550aa2a8cce9de965
manorHash=991eed8c95c8a723f0d2f08e75a46e36ed1180081e488c632a9a4b9367dd39dc
classCapabilityHash=e8e548fe90d8d9d0e9e852030bf4f48011aacaf892bad58da001be14534674d9
contentRequirementHash=4dd788339b9fe141dbc4073cb90ee8e53542ca39cff5b59efc6fc64f4e2a1c37
topologyHash=f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
combinedHash=b3374afcd5a70ce22ff8f7f8c062878180908dc3a719167846af993890a5634e
```

Два независимых parity JVM дали одинаковые component counts, `spawnHash` и
`combinedHash`.

## Query API

Реализованы direct item/NPC/content/recipe lookups, bounded pages для
drop/spoil/manor/spawn/recipe/class/content reverse indexes и target lookup.

```text
Page: 1..256
Target level width: <=100
Target order: preferred-level distance, NPC level, NPC ID
Query dependencies: snapshot maps/indexes only
Loader/file/DB access after build: 0
```

## Startup, disabled path and shutdown

Production order:

```text
repository → materialization → decision → navigation → topology
→ Game Knowledge build → scheduler → RUNNING
```

Disabled path не создаёт service/source scan. Inert test path публикует пустой
snapshot. Shutdown order содержит `scheduler.beginStop`,
`knowledge.beginStop`, `topology.beginStop`, затем соответствующие finish
steps.

Automatic knowledge queries/actions/profile state/workers: `0`.

## Tests and commands

Seed: `20260725001`.

Использован локальный Apache Ant 1.10.15, потому что `ant` отсутствует в PATH.

Выполнено:

```text
pre-change ant verify: runtime PASS; expected static 65/67
pre-change verify-task-010c.ps1: expected 65/67
compile-tests: PASS
knowledge core: 42/42 PASS ×3
knowledge parity: 14/14 PASS ×2
knowledge content: 18/18 PASS ×3
phantom skeleton/lifecycle: 12/12 PASS
all required regression targets: BUILD SUCCESSFUL, 3:44
knowledge performance: 8/8 PASS ×2
full verify: BUILD SUCCESSFUL, 2:31
standalone jar: BUILD SUCCESSFUL, 0:13
verify-task-011.ps1: 154/154 PASS
```

Core покрывает validation/immutability, order-independent hashes, exact double
hashing, grouped/ungrouped semantics, all reverse indexes, topology/map/level
targets, stable cursors, bounds, atomic failure, inert lifecycle и отсутствие
source seam в query.

Performance suite выполнил по `100000` запросов каждой категории item source,
recipe reverse, class capability и bounded target:

```text
Run 1: build=735 ms; item=33 ms; recipe=27 ms; class=7 ms; target=3886 ms
Run 2: build=765 ms; item=41 ms; recipe=28 ms; class=8 ms; target=3969 ms
Iterations: 400000 per run
combinedHash: b3374afcd5a70ce22ff8f7f8c062878180908dc3a719167846af993890a5634e
```

## Metrics and diagnostics

Fixed counters: builds started/completed/failed, query categories, pages,
target candidates considered/returned, rejected queries и parity failures.
Service snapshot содержит state, schema/dataset/generation, combined hash,
counts, fixed failure category и build duration. Dynamic metric labels,
per-fact logging и hot-path INFO/WARNING отсутствуют.

## Production DB safety

Knowledge production package не импортирует DB API и не вызывает DB.
Focused real-loader tests используют только allowlisted
`l2jmobiush5_phantom_test` через существующий test environment. Production DB
`l2jmobiush5` не использовалась.

## Scope, deviations, limitations and risk

- Другие хроники, config, schema, loaders, topology/navigation/decision/
  scheduler/materialization behavior не менялись.
- Russian parsing, Semantic Pack, Utility AI, party optimizer, combat,
  commerce и Goal 012/013 не добавлялись.
- Bounded exception затронул только
  `data/stats/npcs/29100-29199.xml:3867`: Zaken Adena maximum
  `1100000 → 11000000`; verifier защищает exact diff.
- Production wiring остаётся fail-fast: partial snapshot не публикуется.

## Git

Git использовался только в разрешённых baseline, exact scope/diff/verifier
аудитах и final ordinary commit/push flow.

```text
Commit SHA: one ordinary child of baseline; exact SHA in external final handoff
Push: ordinary origin/feature/phantom-world; exact result in external final handoff
```

## Next step

Независимо проверить Goal 011 на baseline/parent, scope, raw parity,
canonical hashes, lifecycle/query bounds, performance и datapack correction.
Manual gate остаётся `PENDING_INDEPENDENT_REVIEW`. Goal 012 и Goal 013 остаются
`NOT_STARTED`.
