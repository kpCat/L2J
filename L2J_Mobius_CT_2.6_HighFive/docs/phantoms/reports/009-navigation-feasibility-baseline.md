# Goal 009 — navigation feasibility baseline

## Status

```text
Status: SUCCESS
Manual gate: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Branch: feature/phantom-world
Subject: feat(phantoms): add navigation service baseline
Goal 008: ACCEPT after Goal 008A
Goal 008A: ACCEPT
Goal 009: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 010: NOT_STARTED
Goal 011: NOT_STARTED
```

## Summary

Реализован inert bounded navigation core без управления существом:

- factual per-request capability через lazy High Five adapter;
- door/fence-aware direct path всегда проверяется первым;
- no-geodata direct явно маркируется
  `DIRECT_UNVERIFIED_NO_GEODATA`;
- заблокированный direct не превращается в небезопасный fallback;
- local A* использует bounded `ArrayBlockingQueue` и не более двух transient
  shared workers существующего `ThreadPool`;
- cancellation/deadline отбрасывают поздний результат до cache/publish;
- computed routes ограничены, хранятся в TTL/LRU cache и повторно проверяются
  посегментно;
- per-profile cooldown применяется только к A*;
- pure tracker определяет progress, arrival, stuck и total attempt timeout;
- `PhantomSystem` владеет пустым сервисом только в enabled path и сохраняет
  in-flight ownership при stop.

`Creature` movement, `GeoEngine`, `PathFinding`, config, schema, topology и
Goal 010 не изменялись. Production DB не использовалась.

## Goal 008A closure

Независимый review зафиксировал commit
`6ecd8ba155e63a2dedeeafd65c1961fdb57bf261` как exact remote baseline:
Goal 008 — `ACCEPT after Goal 008A`, Goal 008A — `ACCEPT`, revert —
`NOT_REQUIRED`, Goal 009 — `ALLOWED`. Immutable handoff добавлен только в
предыдущий отчёт и отдельный review; decision implementation не изменялась.

## Read-first evidence and local patterns

Прочитаны обязательные master/workflow/task документы, отчёты и review
Goal 006–008A, navigation task package, `GeoEngine`, `PathFinding`, buffers,
`Creature` movement, `ThreadPool`, `PhantomSystem`, scheduler, decision,
materialization, Fake Player, Offline Play/Trade, AutoPlay, build и тестовые
launchers.

Локальные паттерны:

- monitor claim → external call → exact reconcile из scheduler/decision;
- retention ownership до quiescence из materialization/lifecycle;
- zero-delay shared dispatch через `ThreadPool.schedule(..., 0)` с проверяемым
  acceptance result;
- immutable records/value objects и fixed aggregate metrics.

Project `README.md`, `docs/README.md`, существующий navigation package и
navigation contract до Goal 009 не найдены.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/navigation/**`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java`.

Tests/build:

- `build.xml`;
- `PhantomTestLauncher`;
- `PhantomNavigationCoreSuite`;
- `PhantomNavigationPerformanceSuite`;
- минимальный `PhantomSkeletonSuite`;
- `verify-task-009.ps1`.

Docs:

- Goal 008A immutable closure/review;
- navigation service contract;
- roadmap progress;
- Goal 009 task package и этот отчёт.

## Architecture decisions

Direct route — самостоятельный typed result, а не pathfinding fallback.
Capability вычисляется до direct-проверки, но singleton adapter вызывается
только по explicit request. Route не хранит server `GeoLocation`.

Очередь, active/completed registries, cooldown и cache имеют жёсткие bounds.
Workers краткоживущие и общие; backend никогда не вызывается под service
monitor. Legacy A* не имеет preemptive cancellation, поэтому service честно
сохраняет worker/request ownership и отбрасывает late result.

Runtime не зависит от UI, LLM, generator или внешнего provider.

## Factual High Five geo/pathfinding audit

Подтверждены реальные сигнатуры и поведение:

- `GeoEngine.hasGeo(x, y)` сообщает наличие region;
- `GeoEngine.canMoveToTarget(...)` до geo traversal проверяет `DoorData` и
  `FenceData`;
- при нуле загруженных regions `GeoEngine` переводит `PATHFINDING` в `0`;
- `PathFinding.findPath(..., instanceId, playable)` синхронный и может вернуть
  `null`;
- `Creature.moveToLocation` содержит direct fallback после неуспешного A*, но
  service этот fallback не переиспользует;
- `ThreadPool.schedule(Runnable, 0)` возвращает nullable acceptance handle и
  позволяет не создавать новый executor.

Capability modes: `NO_GEODATA`, `PARTIAL_GEODATA`,
`GEODATA_DIRECT_ONLY`, `GEODATA_PATHFINDING`.

## Request, result and route contracts

Request хранит positive profile ID, exact immutable origin/destination одного
instance, monotonic submission/deadline и route budget. Service генерирует
overflow-protected request ID и разрешает ровно один nonterminal request на
profile.

Typed results различают direct validated/unverified, computed path,
no-geodata, disabled/no path, route budget, queue/profile backpressure,
cooldown, cancellation, deadline, backend failure и stopped service.

Route хранит Phantom-owned point values, `1..64` immutable waypoints, exact
destination, overflow-safe total distance, factual capability snapshot и
cacheable flag. Mutable `GeoLocation` или backend list наружу не публикуются.

## Direct path and no-geodata semantics

Capability определяется первым, затем каждый accepted request делает ровно
один initial door/fence-aware direct call. При полном geo direct route —
`DIRECT_VALIDATED`; иначе — `DIRECT_UNVERIFIED_NO_GEODATA`. Если direct
заблокирован без полного geo, возвращается `NO_GEODATA`. После failed A*
direct fallback отсутствует.

## Queue, workers, cancellation and deadlines

Очередь имеет capacity `256`; не более двух transient service-level drain
workers отправляются на существующий `ThreadPool`. Active/completed/profile
state bounded, backend работает вне monitor, dispatcher failure не оставляет
worker/request ownership.

Queued cancellation удаляет запрос до A*. In-flight cancellation не прерывает
legacy вызов и сохраняет ownership до возврата. Queue deadline исключает запуск,
compute deadline и cancellation отбрасывают late result до cache/publish.

## Cache, revalidation and cooldown

Access-order LRU ограничен `1024` entries и TTL `5000 ms`. Key включает exact
origin/destination/instance и capability generation/mode. Каждый hit повторно
проверяет до 64 сегментов текущим direct API; obstacle или stale TTL удаляет
entry. Cooldown `1000 ms` устанавливается только после фактической неудачной
A* попытки и не блокирует новый direct route.

## Progress, stuck and timeout

Pure tracker не владеет actor, timer или movement command. Один attempt/profile
наблюдается по immutable point и logical time. Total attempt timeout проверяется
до arrival/stuck; meaningful progress обновляет stuck window, arrival и stale
наблюдения имеют отдельные typed results.

## PhantomSystem lifecycle and metrics

Enabled start создаёт пустой navigation service после decision и до scheduler,
не обращаясь к geo singleton и не отправляя worker. Disabled path возвращает
inactive zero-capacity snapshot без service allocation.

Shutdown order: scheduler begin, decision begin, navigation begin,
materialization drain, scheduler finish, decision finish, navigation finish.
Если legacy worker ещё работает, system остаётся `FAILED` и повторная
server-level попытка завершает stop после quiescence.

`PhantomMetrics.NavigationSnapshot` содержит только fixed aggregate counters и
current/peak gauges; profile/request labels и path logging отсутствуют.

## DB, migrations and config

```text
Production DB: not accessed
Test DB: existing l2jmobiush5_phantom_test routes only
Schema/migrations: unchanged
Config: unchanged
GeoEngine/PathFinding settings: unchanged
Geodata files: absent; no-geodata production runtime invocation not performed
```

Factual source result: при отсутствии обоих endpoint region files capability
равна `NO_GEODATA`; door/fence-aware direct success остаётся
`DIRECT_UNVERIFIED_NO_GEODATA`. Если direct заблокирован, результат
`NO_GEODATA`, а legacy A* не вызывается.

## Tests and commands

Seed: `20260725001`.

| Gate | Result |
|---|---:|
| `ant compile-tests` | PASS; 1959 production / 37 test sources |
| Navigation core | PASS `38/38 ×3` |
| Navigation performance | PASS `1/1 ×2`, canonical summaries byte-identical |
| Performance canonical | `directRequests=10000 pathRequests=1000 directResults=10000 pathResults=1000 cacheHits=999 cacheMisses=1 backendPathCalls=1 peakQueue=1 peakWorkers=1 peakCache=1 maximumWaypoints=3 cancelled=0 timedOut=0` |
| Performance canonical SHA-256 | `D8B8BC902073847DF5C5E3AE28DE380540E43108C4B7420D778FE1659B71E377` |
| Decision core | PASS `35/35 ×3` |
| Decision persistence | PASS `23/23 ×3` |
| Decision performance | PASS `2/2 ×2` |
| Scheduler | PASS `20/20 ×3` |
| Materialization | PASS `20/20 ×3` |
| Shutdown | PASS `5/5 ×3` |
| Headless / profile / DB | PASS `18/18`, `18/18`, `9/9` |
| Harness / skeleton | PASS `66/66`, `12/12` |
| Headless/materialization/scheduler performance | PASS `2/2`, `2/2`, `2/2` |
| Scenario / harness performance | PASS `1/1`, `1/1` |
| Goal 009 verifier | PASS `61/61 ×2`, byte-identical |
| Verifier output SHA-256 | `E935FD5EA010BB968435FB7C3C8625AAC314F4D910B31B72D91A9CDDB28EDB96` |
| `ant verify` | PASS; `1 min 16 s` |
| separate `ant jar` | PASS; `12 s` |

Negative-control summaries с intentional failures завершились успешными Ant
targets и не считаются regression failures.

Основные выполненные команды:

```text
<local-ant-1.10.15>\bin\ant.bat compile-tests
<local-ant-1.10.15>\bin\ant.bat phantom-navigation-core-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-navigation-performance-smoke ×2
<local-ant-1.10.15>\bin\ant.bat phantom-decision-core-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-decision-persistence-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-decision-performance-smoke ×2
<local-ant-1.10.15>\bin\ant.bat phantom-activity-scheduler-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-production-materialization-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-server-shutdown-handoff-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-headless-player-test
<local-ant-1.10.15>\bin\ant.bat phantom-profile-persistence-test
<local-ant-1.10.15>\bin\ant.bat phantom-db-test
<local-ant-1.10.15>\bin\ant.bat test
<local-ant-1.10.15>\bin\ant.bat phantom-skeleton-test
<local-ant-1.10.15>\bin\ant.bat verify
<local-ant-1.10.15>\bin\ant.bat jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009.ps1 ×2
git diff --check
git status --short --branch
```

## Performance

Структурные bounds подтверждаются независимо от скорости машины:

```text
requests: 10000 direct + 1000 repeated computed
cache hit rate: 99.9%
queue capacity: 256
worker limit: 2
cache limit: 1024
waypoint limit: 64
cancellation/timeouts: 0/0 in canonical performance corpus
```

Elapsed time сохраняется только как evidence и не является speed gate.

## Deviations, limitations and risks

- Installed geodata отсутствует, поэтому реальный region-backed A* benchmark
  не выполнялся; deterministic injected backend проверяет контракт и bounds.
- Legacy `PathFinding` нельзя безопасно прервать; cancellation/deadline
  гарантируют только late-result discard и retained ownership.
- Сервис не начинает автоматическую навигацию и не предоставляет global static
  request API.
- Topology, anchors, rooms, Gatekeepers, party routes и background travel
  остаются вне scope.
- Pre-change cumulative verify ожидаемо дошёл до старого frozen Goal 008A
  verifier и обнаружил только untracked package Goal 009 вне старого allowlist;
  runtime regressions до gate прошли.
- Первый вспомогательный PowerShell wrapper после двух успешных performance
  runs попытался вызвать отсутствующий в Windows PowerShell 5.1 static
  `SHA256.HashData`; canonical строки уже совпали, а hash сразу пересчитан через
  совместимый `SHA256.Create().ComputeHash`.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 34 text artifacts, 0 matches;
- escaped Cyrillic в изменённых файлах проверены: 34 text artifacts, 0 matches.

## Git

Git-команды разрешены TASK.md для baseline/scope guard, одного ordinary commit
и push.

```text
Expected parent: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

## Next step

Только независимое review Goal 009. Goal 010: NOT_STARTED.

Result:
`NAVIGATION_SERVICE_BASELINE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
