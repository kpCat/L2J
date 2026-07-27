# PhantomNavigationService contract

## Статус и граница

Goal 009 вводит inert bounded `PhantomNavigationService`, а Goal 009A закрывает
его route truth, backend preflight, dispatch/stop ordering и shutdown
observability findings. Сервис рассчитывает и наблюдает маршрут, но не владеет
`Player`, `Creature`, AI intention, packet, движением или телепортацией.
Goal 010, topology, anchors и rooms не входят в этот контракт.

## Factual capability

Production adapter лениво использует только подтверждённые High Five API:

- `GeoEngine.hasGeo` для обеих конечных точек;
- `GeoEngine.canMoveToTarget` для door/fence-aware direct проверки;
- `GeoEngineConfig.PATHFINDING`;
- `PathFinding.findPath(..., playable=true)`.

Создание и старт сервиса не инициализируют `GeoEngine` или `PathFinding`.
Capability имеет четыре фактических состояния:

```text
NO_GEODATA
PARTIAL_GEODATA
GEODATA_DIRECT_ONLY
GEODATA_PATHFINDING
```

## Input preflight и direct path first

После резервирования request ownership, но до первого backend-вызова сервис
повторно проверяет lifecycle/cancellation, deadline и точную прямую 3D-дистанцию.
Просроченный запрос завершается `DEADLINE_EXPIRED`, а non-finite или заведомо
невозможный бюджет — `ROUTE_BUDGET_EXCEEDED`; capability/direct/A* в обоих
случаях не вызываются.

Остальные принятые запросы выполняют ровно одну начальную direct-проверку.

- При geo на обеих точках успешный результат — `DIRECT_VALIDATED`.
- Без полного geo успешный результат —
  `DIRECT_UNVERIFIED_NO_GEODATA`.
- Заблокированный direct без полного geo возвращает `NO_GEODATA`.
- При выключенном pathfinding возвращается `PATHFINDING_DISABLED`.
- Только `GEODATA_PATHFINDING` может перейти к local A*.

После неуспешного или заблокированного `PathFinding` прямой fallback запрещён.
Ограничение `maximumLocalStraightDistance` применяется только после неуспешного
direct и перед A*, поэтому не отклоняет доступный прямой маршрут.

## Computed route truth

Результат legacy A* считается недоверенным candidate path. Сервис копирует
Phantom-owned точки, удаляет не более одной точной начальной origin, отклоняет
null, другой instance и adjacent duplicates, при необходимости добавляет точный
destination, затем проверяет waypoint count и полный route-distance budget.

До `PATH_FOUND` каждый вычисленный segment последовательно проходит
door/fence-aware `canMoveDirect`, включая автоматически добавленный exact
destination. Между segment-проверками повторно проверяются cancellation
generation и deadline. Приоритет позднего состояния до публикации:

```text
CANCELLED
DEADLINE_EXPIRED
BACKEND_FAILURE
ROUTE_OBSTRUCTED
```

`ROUTE_OBSTRUCTED` — отдельный terminal unsuccessful A* result. Он не содержит
route, не публикуется и не попадает в cache; после фактической A* попытки
устанавливается cooldown. Fixed aggregate metrics различают obstruction
первичной computed route и cache revalidation без profile/coordinate labels.

## Bounded ownership

Production defaults:

```text
ArrayBlockingQueue capacity: 256
transient shared ThreadPool workers: 2
tracked profile states: 10000
LRU cache entries: 1024
cache TTL: 5000 ms
pathfinding cooldown: 1000 ms
local straight distance: 12000
waypoints: 64
route distance: 100000
request deadline: 1000 ms
```

Ровно один nonterminal request допускается на profile. Worker является
service-level drain task на существующем `ThreadPool`, не постоянной и не
per-profile задачей. Backend выполняется вне service monitor.

Узкий dispatch gate атомарно упорядочивает worker claim/dispatch и `STOPPING`.
Dispatcher вызывается не более одного раза на claim. Если dispatch отклонён,
выбросил исключение или stop победил до dispatch, exact worker claim и request
ownership синхронно освобождаются. Уже принятый worker сохраняет ownership до
возврата, даже если фактически стартует после `beginStop`; отрицательный
`workers` и stranded claim запрещены.

Cancellation кооперативна: уже выполняющийся legacy A* не прерывается, worker
и request ownership сохраняются до возврата. После возврата cancellation token
и deadline проверяются до cache/publish, поэтому поздний маршрут отбрасывается.

## Cache и cooldown

Только полностью нормализованный и segment-validated bounded local route может
быть cacheable. Cache использует access-order LRU, TTL и capability snapshot в
ключе. На hit каждый segment повторно проверяется тем же bounded
door/fence-aware helper; dynamic obstacle, cancellation, deadline или backend
failure инвалидируют запись и не публикуют частичный route.

Cooldown применяется после A* timeout/failure/no-path и проверяется только
после новой direct-проверки. Поэтому новый прямой маршрут cooldown не блокирует.

## Pure progress tracker

Tracker получает immutable route, observed point и logical time. Он не создаёт
timer и не вызывает движение. Порядок terminal checks:

```text
attempt timeout
arrival
meaningful progress
stuck window
tracking
```

Stale request, instance или регресс logical time не меняют active attempt.

## Lifecycle

Enabled startup:

```text
materialization → decision → navigation → scheduler
```

Shutdown:

```text
scheduler.beginStop
→ decision.beginStop
→ navigation.beginStop
→ materialization drain
→ scheduler.finishStop
→ decision.finishStop
→ navigation.finishStop
```

Disabled startup не создаёт service, queue, cache, worker или geo singleton.
Если legacy A* ещё выполняется, `finishStop` возвращает `false`, а повторный
server-level shutdown завершает stop после возврата worker.

Server-level aggregate shutdown snapshot и initial/final diagnostics содержат:

```text
systemState
materializationServiceState
retainedMaterializationEntries
navigationState
navigationActiveRequests
navigationQueuedRequests
navigationWorkers
```

Они не раскрывают profile IDs, coordinates или routes. Если materialization уже
остановлена, но navigation ещё удерживает request/worker, final diagnostic
остаётся subsystem-wide `SEVERE`; success до удаления configured instance
запрещён.
