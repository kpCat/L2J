# PhantomNavigationService contract

## Статус и граница

Goal 009 вводит inert bounded `PhantomNavigationService`. Сервис рассчитывает и
наблюдает маршрут, но не владеет `Player`, `Creature`, AI intention, packet,
движением или телепортацией. Goal 010, topology, anchors и rooms не входят в
этот контракт.

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

## Direct path first

Каждый принятый запрос сначала выполняет одну direct-проверку.

- При geo на обеих точках успешный результат — `DIRECT_VALIDATED`.
- Без полного geo успешный результат —
  `DIRECT_UNVERIFIED_NO_GEODATA`.
- Заблокированный direct без полного geo возвращает `NO_GEODATA`.
- При выключенном pathfinding возвращается `PATHFINDING_DISABLED`.
- Только `GEODATA_PATHFINDING` может перейти к local A*.

После неуспешного или заблокированного `PathFinding` прямой fallback запрещён.

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

Cancellation кооперативна: уже выполняющийся legacy A* не прерывается, worker
и request ownership сохраняются до возврата. После возврата cancellation token
и deadline проверяются до cache/publish, поэтому поздний маршрут отбрасывается.

## Cache и cooldown

Только вычисленный bounded local route может быть cacheable. Cache использует
access-order LRU, TTL и capability snapshot в ключе. На hit каждый сегмент
повторно проверяется через door/fence-aware direct API; dynamic obstacle
инвалидирует запись.

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
