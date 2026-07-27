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

## Goal 010A: generation и signal ownership

`PhantomTopologyService` владеет единственным fair read/write coordinator.
Порядок захвата фиксирован: generation lease → короткий service/registry
monitor → scheduler port. Service monitor не удерживается при ожидании
generation lock.

Profile update удерживает read ownership одной exact generation от получения
query до atomic membership commit. Commit допускается только при совпадении
registry generation; recipient lookup также принимает и проверяет exact
generation.

Успешный reload:

1. строит и валидирует candidate вне write ownership;
2. под write ownership повторно разрешает точки всех сохранённых profiles;
3. сохраняет их position sequences и явно сохраняет unresolved результат;
4. withdraw-ит `topology.local_chat`, `topology.combat` и
   `topology.targetability` для всех сохранённых profiles;
5. только после успешной invalidation устанавливает candidate memberships и
   публикует snapshot/query новой generation.

`BACKPRESSURE`, `REJECTED`, `NOT_RUNNING` или исчерпание signal sequence во
время reload invalidation отклоняют reload. Старые snapshot, canonical hash,
generation и memberships остаются активными; частично выполненные withdrawals
не откатываются.

Каждое perception event удерживает read ownership до завершения всех scheduler
deliveries. Список recipients и финальная проверка непосредственно перед
`submit` требуют ту же exact generation. Unregister сериализован с delivery:
сначала удаляется membership, затем newer monotonic sequences withdraw-ят все
три provider-owned sources. После финальных withdrawals новый submit для
профиля невозможен.

Inactive targetability всегда выполняет withdraw, даже если target уже удалён
из topology registry. Cleanup failure возвращается явно, сохраняется как
pending и завершается только явным retry; повторная регистрация до успешного
cleanup не допускается. Source sequence использует overflow-safe allocation и
не может перейти в отрицательное значение.

Новых executor, thread, Future, background cleanup task или per-profile worker
Goal 010A не добавляет.

## Goal 010B: bounded signal ledger

Provider владеет одним bounded `profileId → signal ledger` map. Capacity равен
`maximumRegisteredProfiles` и одновременно учитывает:

- текущие topology registrations;
- retained sequence tombstones;
- failed-cleanup tombstones;
- cleanup operations in flight.

Ledger содержит только три fixed source slots: local chat, combat и
targetability. Каждый slot хранит monotonic sequence и одно из состояний
`NEVER_SUBMITTED`, `POSSIBLY_ACTIVE`, `INACTIVE_CONFIRMED`,
`OWNERSHIP_UNCERTAIN`. Отдельных sequence map, pending-cleanup set, event history
или per-profile task нет.

Новая registration резервирует ledger до registry publication. Retained
identity переиспользует прежние sequences; при исчерпании общей capacity
возвращается `SIGNAL_LEDGER_CAPACITY` без registry mutation. Inactive
targetability для never-owned ID возвращает unregistered outcome без ledger
allocation и scheduler call.

`ACCEPTED`/`COALESCED` submit переводит source в `POSSIBLY_ACTIVE`.
`BACKPRESSURE` и `NOT_REGISTERED` потребляют sequence, но не меняют локальную
истину. `STALE`, `REJECTED` и `NOT_RUNNING` submit являются явным
`SIGNAL_FAILURE`; неоднозначное ownership становится
`OWNERSHIP_UNCERTAIN`.

Withdrawal подтверждает inactive только при `ACCEPTED`, `COALESCED` или
`NOT_REGISTERED`. `STALE` безопасен исключительно тогда, когда ledger уже
локально фиксирует `INACTIVE_CONFIRMED`; иначе cleanup fail-closed.
`BACKPRESSURE`, `REJECTED`, `NOT_RUNNING` и sequence exhaustion не очищают
возможное ownership.

После unregister успешные `ACCEPTED`/`COALESCED` withdrawals сохраняют ledger
для monotonic re-registration. Ledger освобождается только когда все три
withdrawals одного final cleanup pass вернули scheduler `NOT_REGISTERED` и
profile отсутствует в topology registry, либо при final `STOPPED`. Reload
использует только существующие registered ledgers, не создаёт новые и не
публикует candidate при uncertain/failed invalidation.

## Goal 010C: reconciliation отсутствующего scheduler source

Три reserved source key эмитирует исключительно `PhantomPerceptionProvider`.
Ledger создаётся до topology registration и переживает unregister/re-register
в пределах lifetime одного topology service. Поэтому `NEVER_SUBMITTED`
локально доказывает, что этот provider ещё не выполнил успешный
`ACCEPTED`/`COALESCED` submit данного source.

Если реальный scheduler возвращает `STALE` для отсутствующего source, withdrawal
считается безопасным только при предыдущем локальном состоянии
`NEVER_SUBMITTED` или `INACTIVE_CONFIRMED`. Оба состояния переходят в
`INACTIVE_CONFIRMED`. Для `POSSIBLY_ACTIVE` и `OWNERSHIP_UNCERTAIN` тот же
`STALE` остаётся ownership failure и сохраняет fail-closed cleanup.

Безопасный `STALE` не является доказательством отсутствия scheduler profile,
не участвует в all-three `NOT_REGISTERED` evidence и не освобождает ledger.
Этот инвариант относится только к эксклюзивно принадлежащим topology provider
source keys и не обобщается на произвольных signal providers.
