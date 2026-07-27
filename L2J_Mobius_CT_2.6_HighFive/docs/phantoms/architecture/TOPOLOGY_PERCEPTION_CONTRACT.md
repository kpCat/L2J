# Phantom topology and perception contract

## Статус и граница

Goal 010 вводит inert production-owned topology service и explicit perception
providers. Он описывает проверенную геометрию, semantic anchors и one-hop
perceptibility, но не владеет `Player`, `Creature`, движением, materialization,
combat actions, population или Game Knowledge.

Production остаётся выключенной общим Phantom feature flag. При включённой
системе после startup зарегистрировано ноль topology profiles и нет событий.

## Две независимые истины

Versioned XML в `data/phantoms/topology` курирует:

- стабильные node/anchor/edge IDs;
- semantic roles;
- area hierarchy;
- adjacency/travel/perception meaning;
- exact source evidence.

Фактические серверные сущности проверяются только узким read-only backend:

- map-region claim — `MapRegionData.getMapRegionLocId`;
- NPC template — `NpcData.getTemplate`;
- spawn coordinate — `SpawnTable.getSpawns`, заполненная `SpawnData`;
- door geometry и live state — `DoorData.getDoor`;
- координаты — границы `World`.

Имена и titles NPC не участвуют в идентичности или определении роли. Semantic
роль существует только потому, что явно объявлена в curated XML и имеет source
evidence; loader лишь подтверждает IDs, spawn coordinates и тип Monster для
farming anchor.

## Dataset и immutable snapshot

Поддерживается `schemaVersion=1`, stable dataset ID и положительная
`datasetVersion`. Идентификаторы соответствуют
`^[a-z][a-z0-9_.-]{0,95}$`.

Фиксированные bounds:

```text
files 64
nodes 100000
anchors 100000
edges 200000
hierarchy depth 8
tags/entity 16
source refs/entity 8
polygon vertices 32
```

Loader читает XML в детерминированном порядке, отклоняет неизвестные
schema/element/attribute, invalid geometry, cycles, dangling refs, неверные
factual claims и semantic edge duplicates. Candidate целиком строится и
валидируется до publication. Canonical SHA-256 вычисляется по sorted semantic
content и source evidence, поэтому перестановка XML entities hash не меняет.

Reload — только explicit API без watcher/task. Invalid candidate сохраняет
предыдущий snapshot. Успешная смена generation атомарна и сбрасывает только
устаревшее profile-to-node resolution.

## Geometry и indexes

Поддержаны `POINT_RADIUS`, `CUBOID`, простой `POLYGON`; все координаты и Z bounds
валидируются. Immutable snapshot содержит:

```text
nodeById
anchorById
edgeById
childrenByParent
edgesByNode
anchorsByNode
anchorsByRole
bounded node/anchor spatial buckets
```

Oversized areas ограничены отдельным bounded list. `locate` возвращает не более
64 nodes, сортируя deepest hierarchy → smallest area → ID. `nearestAnchors`
имеет limit `1..64` и radius до `100000`; adjacency возвращает не более 1024
edges; route hint ограничен 256 graph nodes и не дублирует A*.

Door open/closed/dead/missing state не входит в snapshot и запрашивается при
каждом `isTraversable`/`isPerceptible`. Только live `CLOSED` блокирует door edge.

## Explicit profile-position registry

Profile появляется только через `register(profileId)`. Нет World scan,
repository discovery или автоматической scheduler registration.

Registry хранит только profile ID, immutable point, monotonic sequence,
optional resolved node и topology generation. Stale update отклоняется.
Unresolved position представлена явно. Node-bucket membership меняется
атомарно; lists immutable и ограничены общей capacity.

## Perception providers

Typed events не содержат chat text:

```text
LocalChatEvent
CombatEvent
TargetabilityEvent
```

Local chat и combat выбирают profiles только из explicit registry. Same-node
profile perceptible. Neighbor perceptible только по одному direct edge, если
channel объявлен, radius допускает point distance и live door не закрыта.
Traversal дальше одного edge запрещён.

Fixed scheduler sources и minimum states:

```text
topology.local_chat   → NEARBY_PERCEPTIBLE
topology.combat       → participant ACTIVE, neighbor NEARBY_PERCEPTIBLE
topology.targetability → active target ACTIVE, inactive withdraw
```

State ниже `NEARBY_PERCEPTIBLE` perceptible recipient получить не может.
Scheduler вызывается только через port с `submit`/`withdraw`; adapter использует
только `PhantomScheduler.submitSignal` и `withdrawSignal`. Он не регистрирует
profiles. `BACKPRESSURE`/`NOT_REGISTERED` изолированы на одном recipient.

## Event ownership и lifecycle

Fanout синхронен на caller thread, но ограничен 32 concurrent events, 1024
recipients/event, 64 neighbor nodes и radius `100000`. Новый executor, task,
future или per-profile worker не создаётся.

Event получает exact token/generation под monitor, захватывает immutable
candidates, выполняет внешнюю проверку/delivery без monitor и освобождает тот же
token. Узкий delivery gate упорядочивает scheduler call и `beginStop`.

```text
NEW → RUNNING → STOPPING → STOPPED
```

После `beginStop` новый event/profile operation отклоняется и ни один новый
scheduler delivery не начинается. `finishStop` возвращает `false`, пока exact
event token в полёте; registry очищается только после quiescence.

## PhantomSystem ordering

Enabled startup:

```text
repository → materialization → decision → navigation → topology → scheduler
```

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

Disabled path не создаёт topology loader/service и не сканирует dataset.
Server-level snapshot/log содержат только aggregate topology state, profile/event
counts и generation, без IDs/coordinates.

## Явные исключения

Goal 010 не добавляет:

- Game Knowledge, item/drop/spoil/recipe indexes;
- combat actions или movement;
- population/discovery;
- config/schema;
- packet/chat/combat listeners;
- party route policy;
- Goal 011/012 behavior.
