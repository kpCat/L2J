# Goal 010 — topology, anchors and perception graph

## Status

```text
Status: SUCCESS
Manual gate: PENDING_INDEPENDENT_REVIEW
Accepted baseline: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Expected parent: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Branch: feature/phantom-world
Subject: feat(phantoms): add topology perception graph
Goal 009: ACCEPT after Goal 009A
Goal 009A: ACCEPT
Goal 010: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: NOT_STARTED
Goal 012: NOT_STARTED
```

## Summary

Реализована data-driven immutable topology `high-five-core` schema/version
`1/1`: region/city/shop/farming/route/dungeon/room/corridor nodes, semantic
anchors, walk/door/background edges и все contract edge/channel enums.
Snapshot имеет deterministic canonical SHA-256, bounded spatial/adjacency
indexes и live door overlay.

Добавлен explicit-only profile-position registry и synchronous event-driven
local-chat/combat/targetability provider. Provider передаёт только abstract
`PhantomRelevanceSignal` через narrow scheduler port, не регистрирует scheduler
profiles и не вызывает materialization/navigation. Same-node и разрешённый
one-hop neighbor всегда получают минимум `NEARBY_PERCEPTIBLE`; combat
participant и active target получают `ACTIVE`.

Production по-прежнему inert:

```text
registered topology profiles = 0
events in flight = 0
automatic discovery/registration = 0
automatic movement/actions = 0
```

## Goal 009A closure

Создан independent review
`docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review.md` и
добавлен immutable handoff в отчёт Goal 009A:

```text
Commit: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Parent: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Push/remote: exact
Navigation core: 50/50 ×3
Navigation performance: 1/1 ×2
Shutdown handoff: 7/7 ×3
Final verifier: 56/56 ×2, byte-identical
Independent review: ACCEPT
Goal 010: ALLOWED
```

Отсутствующий во внешнем handoff post-push verifier hash не выдумывался.

## Read-first and local patterns

Прочитаны обязательные master/workflow/package/report документы, packages,
reports, reviews и architecture contracts Goals 007–009A; текущие scheduler,
decision, navigation, `PhantomSystem`, shutdown, metrics, build/tests; factual
`MapRegionData`, `NpcData`/`NpcTemplate`, `SpawnData`/`SpawnTable`/`Spawn`,
`DoorData`/`Door`, `World`, `WorldRegion`, `ZoneManager`; выбранные High Five
datapack sources и весь Task 010 package.

Project README и отдельный docs code-map/index не найдены. Переиспользованы:

- immutable records и bounded policy из navigation;
- monitor claim → external work → exact reconcile;
- dispatch/delivery gate против `STOPPING`;
- complete candidate → atomic generation swap;
- fixed aggregate metrics без dynamic labels.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/topology/**`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/Shutdown.java`;
- `dist/game/data/phantoms/topology/high-five-core.xml`.

Tests/build:

- четыре `PhantomTopology*Suite.java`;
- `PhantomTestLauncher.java`;
- `PhantomServerShutdownHandoffSuite.java`;
- `build.xml`;
- `tools/phantoms/verify-task-010.ps1`.

Documentation:

- roadmap progress;
- topology/perception contract;
- Goal 009A report/review;
- Task 010 package;
- этот отчёт.

Bounded exception по числу файлов — одна artifact family Goal 010: topology
model/loader/query, perception boundary, focused tests, seed, lifecycle wiring и
gate evidence. Другие хроники не затронуты.

## Factual server-loader audit and truth boundary

Curated XML задаёт stable IDs, semantic roles, hierarchy, connectivity и exact
source paths. Loader не читает NPC name/title и не определяет роль по тексту.

Factual validation:

```text
MapRegionData.getMapRegionLocId
NpcData.getTemplate
SpawnData → SpawnTable.getSpawns
DoorData.getDoor
World coordinate bounds
```

Value objects не хранят `Player`, `Creature`, `Npc`, `Door`, `Spawn`,
`WorldObject`, XML node или mutable loader collection.

## XML schema, canonical hash and reload

Поддерживаются `POINT_RADIUS`, `CUBOID`, простой `POLYGON`; identifiers и
entity/string/depth/count bounds соответствуют TASK. Unknown
schema/element/attribute, invalid geometry, duplicate/dangling/cyclic
references и неверные server facts отклоняются.

Entity lists canonical-sorted. SHA-256 использует length-prefixed semantic
content и evidence, поэтому XML entity order не влияет на hash. Explicit reload
строит candidate вне service monitor, меняет snapshot только после полной
валидации и оставляет предыдущую generation при ошибке. Watcher/reload task
нет.

Production dataset:

```text
datasetId: high-five-core
schemaVersion: 1
datasetVersion: 1
canonical SHA-256: f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
nodes: 8
anchors: 8
edges: 3
```

## Production seed corpus and exact coverage

Giran cluster:

- `giran.region`, `giran.city`, shop and route nodes;
- map-region locId `918`;
- Gatekeeper NPC/spawn `30080` at `83396,147904,-3404`;
- shop NPC/spawn `30081` at `80518,147922,-3506`;
- city center from `giran_castle_town.xml`;
- outdoor Monster `22859` at `87439,121072,-3061`;
- route anchor и background-eligible edge.

Door/room corpus:

- `SSQDisciplesNecropolisPast.xml` явно содержит first/second room spawn groups
  и door list;
- script явно связывает `DOOR_1` с `17240102`;
- `Doors.xml` содержит factual geometry door `17240102`;
- curated first ROOM и adjacent CORRIDOR соединены одним DOOR edge с двумя
  bounded factual door-side anchors.

Это representative base-instance geometry, не утверждение полной карты или
runtime instance-template remapping.

## Spatial/adjacency indexes and live overlay

Immutable snapshot содержит `nodeById`, `anchorById`, `edgeById`,
`childrenByParent`, `edgesByNode`, `anchorsByNode`, `anchorsByRole`, node/anchor
spatial buckets и bounded oversized-area list.

Bounds:

```text
locate <= 64 nodes
nearest limit <= 64, radius <= 100000
edges <= 1024
route-hint BFS <= 256 nodes
perception traversal = event node + one edge
```

Most-specific resolution: deepest hierarchy, затем smallest area, затем ID.
Nearest ordering: distance, затем ID. Door state отсутствует в snapshot и
читается через backend на каждом query. Live `CLOSED` блокирует traversal и
perception; open/dead/missing не кэшируются.

Route hint возвращает только anchor/edge IDs и не вычисляет A*: локальный путь
остаётся ответственностью `PhantomNavigationService`.

## Profile registry and perception providers

Registry имеет explicit `register/update/unregister/find/list`, capacity,
monotonic sequence, explicit unresolved state и atomic node membership. Нет
World/repository scan, Player ref, timer/task/future или автоматической
scheduler registration.

Fixed sources:

```text
topology.local_chat
topology.combat
topology.targetability
```

Provider fanout ограничен 32 in-flight events, 1024 recipients, 64 neighbor
nodes и radius `100000`. Backpressure/NOT_REGISTERED изолированы per recipient.
Event records не содержат message text.

Hard gate:

```text
local-chat same/neighbor → NEARBY_PERCEPTIBLE
combat participant → ACTIVE
combat neighbor → NEARBY_PERCEPTIBLE
active target → ACTIVE
inactive target → withdraw
```

State ниже `NEARBY_PERCEPTIBLE` для perceptible recipient программно
отклоняется.

## Event stop ownership

Event exact token/generation захватывается под monitor. Geometry и delivery
выполняются без service monitor, после чего освобождается тот же token. Узкий
delivery gate упорядочивает scheduler call и `beginStop`.

После `STOPPING` новый profile/event operation и новый scheduler delivery не
начинаются. `finishStop=false`, пока event token в полёте; registry/sequences
очищаются только после quiescence. Новых executor/thread/task нет.

## PhantomSystem and shutdown

Enabled:

```text
repository → materialization → decision → navigation → topology → scheduler
```

Disabled path не создаёт topology service/loader и не сканирует data.

Shutdown:

```text
scheduler.beginStop
→ topology.beginStop
→ decision.beginStop
→ navigation.beginStop
→ materialization drain
→ scheduler.finishStop
→ topology.finishStop
→ decision.finishStop
→ navigation.finishStop
```

Aggregate snapshot/log дополнены только `topologyState`,
`topologyRegisteredProfiles`, `topologyEventsInFlight`, `topologyGeneration`.
IDs и координаты в shutdown log не выводятся. Ровно две server-level shutdown
попытки до shared `ThreadPool.shutdown()` сохранены.

При включённом Phantom feature topology является первым consumer существующих
factual singleton loaders и инициирует `SpawnData`, если `SpawnTable` ещё не
заполнена. Это необходимо для startup factual validation в разрешённом scope;
loader sources не менялись.

## Metrics and diagnostics

Fixed aggregate snapshot включает load/reload/failure, entity counts,
spatial/nearest/edge/door queries, profile current/peak/rejected updates,
event in-flight/peak, recipient delivery/backpressure/unregistered, три signal
channel outcomes и stop failures. Dataset ID/version/hash, generation и
последняя fixed failure category bounded; raw XML, path list, coordinates и
event history отсутствуют.

## Tests and performance

Seed `20260725001`. Использован локальный Apache Ant 1.10.15 launcher вне
репозитория, потому что `ant` отсутствует в `PATH`.

```text
compile-tests: PASS
topology core: 38/38 ×3
topology perception: 28/28 ×3
production corpus: 6/6 ×2
topology performance: 1/1 ×2
navigation core: 50/50 ×3
navigation performance: 1/1 ×2
shutdown handoff: 7/7 ×3
decision core/persistence/performance: 35/35, 23/23, 2/2
activity scheduler: 20/20
production materialization: 20/20
headless player: 18/18
profile persistence: 18/18
database integration: 9/9
harness unit: 66/66
skeleton: 12/12
```

Performance structural shape:

```text
nodes=10000
edges=20000
anchors=50000
profiles=10000
localChatEvents=1000
combatEvents=1000
nearestLimit=16
maximumRecipientsObserved=3
datasetHash=e4c1dc8945ae9bb8c15ec688c73e249b05ed110877a7da809cde46a3472f5a05
elapsed evidence=855 ms, 898 ms
```

Elapsed — evidence only, не speed gate.

Дополнительные результаты:

```text
ant test: PASS; штатный lifecycle negative control воспроизведён
ant verify: PASS
ant jar: PASS
static verifier pre-commit: 82/82
GameServer.jar topology entries: present
GameServer.jar test entries: absent
```

Post-commit verifier, exact diff и push evidence дополняются внешним final
handoff; self commit SHA/push не выдумываются.

## DB, config and scope safety

```text
Production DB l2jmobiush5: не использовалась
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Phantom config: unchanged
Game Knowledge: not added
Combat actions/movement/population: not added
Goal 011/012: NOT_STARTED
```

Pre-change `ant verify` и frozen verifier прошли runtime suites, но ожидаемо
имели только два scope findings из-за уже распакованного untracked Task 010
package; frozen verifier summary `54/56`.

## Encoding checks

- mojibake-маркеры в 48 изменённых текстовых файлах проверены: совпадений нет;
- escaped Cyrillic в 48 изменённых текстовых файлах проверены: совпадений нет.

## Static verifier

`tools/phantoms/verify-task-010.ps1` проверяет baseline/one-child scope,
frozen loaders/subsystems, schema/corpus/factual adapters, immutable
indexes/live door, explicit registry, one-hop signals, lifecycle ordering,
tests/build/docs, encoding, credentials, JAR separation и собственную
read-only/deterministic форму.

## Deviations, limitations and risks

- Production corpus покрывает только Giran cluster, один outdoor Monster spawn
  и одну доказанную room/corridor door boundary.
- Base door geometry имеет instance `0`; динамическое instance-template
  remapping не входит в Goal 010.
- Teleport/Gatekeeper/Passage modes поддержаны data model и validation rules,
  но seed не объявляет полноту соответствующих сетей.
- Providers не подключены к chat/combat packet listeners: будущие subsystems
  вызывают их явно.
- Нет geodata claim, movement или A* в topology.
- Goal 010 не self-accepted; independent review обязателен.

## Git

Git-команды разрешены TASK.md для baseline/scope, одного ordinary commit и
push.

```text
Expected parent: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

## Next step

Только independent review Goal 010. Goal 011 и Goal 012 остаются
`NOT_STARTED`.

Result:
`TOPOLOGY_PERCEPTION_GRAPH_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
