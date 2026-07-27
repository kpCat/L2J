# Goal 009A — navigation route ownership hardening

## Status

```text
Status: SUCCESS
Manual gate: ACCEPT
Required baseline: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Expected parent: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Branch: feature/phantom-world
Subject: fix(phantoms): harden navigation route ownership
Goal 009 architecture direction: ACCEPT
Goal 009 commit: ACCEPT after Goal 009A
Goal 009A: ACCEPT
Goal 010: ALLOWED
Goal 011: NOT_STARTED
```

## Summary

Закрыты только findings независимого review Goal 009:

- deadline и точный impossible route budget проверяются до capability/direct/A*;
- каждый нормализованный computed segment проходит door/fence-aware direct
  validation, включая автоматически добавленный exact destination;
- obstruction имеет стабильный `ROUTE_OBSTRUCTED`, cooldown и отдельные
  aggregate metrics, но не route/cache publication;
- cancellation/deadline проверяются между segment calls и при финальном
  success reconcile;
- worker claim/dispatch атомарно упорядочены с `STOPPING`, rejected/exception/
  inline dispatch освобождают exact claim без double decrement;
- server shutdown snapshot и final `SEVERE` diagnostic показывают aggregate
  materialization и navigation ownership.

`GeoEngine`, `PathFinding`, `Creature`, config, schema, decision/lifecycle
contracts и будущие Goal 010/011 не изменялись. Автоматическая навигация не
добавлялась.

## Read-first evidence and reused patterns

До изменений полностью прочитаны `AGENTS.md`, master plan, roadmap,
workflow/package/report standards, весь Goal 009A package, Goal 009 package,
navigation contract/report, Goal 008A closure, navigation production package и
focused suites, `ThreadPool` dispatch/rejected-handler, `PhantomSystem`,
`Shutdown`, shutdown handoff suite, фактические
`GeoEngine.canMoveToTarget`/`PathFinding.findPath` и scheduler stop patterns.

Project `README.md` и отдельный docs index не найдены. Переиспользованы локальные
паттерны:

- claim под monitor → внешний вызов → exact reconcile;
- retained ownership до quiescent worker/store operation;
- двухфазный Phantom shutdown до остановки shared `ThreadPool`;
- fixed aggregate metrics без dynamic labels.

До реализации оставались непроверенными реальные race outcomes и segment-level
late precedence; они закрыты focused deterministic tests. Реальная geodata
отсутствует и остаётся непроверенной вне injected backend.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java`;
- `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationResult.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/Shutdown.java`.

Tests/build:

- `test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java`;
- `build.xml`;
- `tools/phantoms/verify-task-009a.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md`;
- `docs/phantoms/reports/009-navigation-feasibility-baseline.md`;
- `docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md`;
- пакет `docs/phantoms/tasks/009a-navigation-route-ownership-hardening/**`;
- этот отчёт.

Bounded exception по числу файлов относится к одной safety closure family:
navigation truth/ownership, aggregate shutdown observability, focused tests,
successor verifier и gate documentation. Другие хроники не затронуты.

## Architecture decisions

### Zero-backend input preflight

После резервирования profile/request ownership сервис повторно проверяет state
и cancellation, читает logical clock и завершает уже просроченный запрос без
backend. Затем точная 3D distance сравнивается с
`min(request.maximumRouteDistance, policy.maximumRouteDistance)`; non-finite
или impossible input также не вызывает capability/direct/A*. Local straight
distance gate остаётся только после неуспешного direct перед A*.

Focused counters подтверждают:

```text
expired: capability=0, initialDirect=0, segmentDirect=0, path=0
impossible budget: capability=0, initialDirect=0, segmentDirect=0, path=0
```

### Computed route truth

Backend path копируется в Phantom-owned list, одна exact leading origin
удаляется, null/wrong-instance/adjacent duplicate отклоняются. Exact
destination добавляется только при отсутствии; после waypoint/distance bounds
каждый segment проверяется тем же `canMoveDirect`.

False становится `ROUTE_OBSTRUCTED`, exception — `BACKEND_FAILURE`,
cancellation/deadline имеют приоритет до публикации. Успешный direct/cache
result дополнительно reconciled под monitor, поэтому stop/cancel/deadline не
могут заменить terminal cancellation поздним route. Только полностью
проверенный `PATH_FOUND` попадает в LRU cache.

Computed и cache obstruction имеют разные fixed counters. Obstruction после
фактической A* попытки получает cooldown, который не блокирует новый доступный
direct route.

### Dispatch/STOPPING ownership

`WorkerClaim` представляет exact service worker ownership. Узкий
`_dispatchGate` упорядочивает dispatcher decision и `beginStop()`:

```text
claim → dispatch gate → recheck → dispatch/rollback
beginStop → dispatch gate → STOPPING/cancel/unaccepted rollback
```

Backend не выполняется под gate/monitor. Accepted worker сохраняет claim до
возврата; rejected/exception/stop-lost claim освобождается идемпотентно.
Queued entry получает `BACKEND_FAILURE` только при отсутствии accepted worker,
который может её забрать. Inline dispatcher не deadlock и не освобождает claim
дважды; `_workers` не становится отрицательным.

### Aggregate shutdown truth

`ConfiguredShutdownSnapshot` теперь содержит materialization state/retained
entries и `navigationState`, active/queued requests, workers. Initial/final
server logs используют subsystem-wide wording. Navigation-only blocker
сохраняет configured system и final failure diagnostic, даже когда
materialization уже `STOPPED` с нулём retained entries. Порядок ровно двух
Phantom shutdown calls перед `ThreadPool.shutdown()` сохранён.

## DB, migrations and config

```text
Production DB: не использовалась
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Config: unchanged
GeoEngine/PathFinding/Creature: unchanged
Goal 010/011 artifacts: unchanged
```

## Tests and commands

Seed: `20260725001`. Apache Ant 1.10.15 запускался абсолютным локальным
launcher вне репозитория, поскольку `ant` отсутствует в `PATH`.

| Gate | Result |
|---|---:|
| `ant compile-tests` | PASS; 1959 production / 37 test sources |
| Navigation core targeted | PASS `50/50` |
| Navigation core repeat | PASS `50/50 ×3` |
| Navigation performance targeted | PASS `1/1` |
| Navigation performance repeat | PASS `1/1 ×2`, canonical identical |
| Shutdown handoff targeted | PASS `7/7` |
| Shutdown handoff repeat | PASS `7/7 ×3` |
| Decision core / persistence / performance | PASS `35/35`, `23/23`, `2/2` |
| Scheduler / materialization | PASS `20/20`, `20/20` |
| Headless / profile / DB | PASS `18/18`, `18/18`, `9/9` |
| Harness / skeleton | PASS `66/66`, `12/12` |
| `ant verify` pre-commit | PASS; `1 min 18 s` |
| отдельный `ant jar` pre-commit | PASS; `11 s` |
| Production `GameServer.jar` navigation class entries | `37` |
| Production `GameServer.jar` test entries | `0` |
| Goal 009A verifier pre-commit | PASS `56/56 ×2`, byte-identical |
| Pre-commit verifier output SHA-256 | `0CC17166F2E296CC46F4FD12E74AEB8329158CD1379E4DEB65F22AF878FDE397` |
| Post-commit `verify` / `jar` / verifier | во внешнем final handoff |

`ant test` содержит intentional negative-control summaries; target завершился
`BUILD SUCCESSFUL`, поэтому они не являются regression failures.

Основные команды:

```text
<local-ant-1.10.15>\bin\ant.bat compile-tests
<local-ant-1.10.15>\bin\ant.bat phantom-navigation-core-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-navigation-performance-smoke ×2
<local-ant-1.10.15>\bin\ant.bat phantom-server-shutdown-handoff-test ×3
<local-ant-1.10.15>\bin\ant.bat phantom-decision-core-test
<local-ant-1.10.15>\bin\ant.bat phantom-decision-persistence-test
<local-ant-1.10.15>\bin\ant.bat phantom-decision-performance-smoke
<local-ant-1.10.15>\bin\ant.bat phantom-activity-scheduler-test
<local-ant-1.10.15>\bin\ant.bat phantom-production-materialization-test
<local-ant-1.10.15>\bin\ant.bat phantom-headless-player-test
<local-ant-1.10.15>\bin\ant.bat phantom-profile-persistence-test
<local-ant-1.10.15>\bin\ant.bat phantom-db-test
<local-ant-1.10.15>\bin\ant.bat test
<local-ant-1.10.15>\bin\ant.bat phantom-skeleton-test
<local-ant-1.10.15>\bin\ant.bat verify
<local-ant-1.10.15>\bin\ant.bat jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-009a.ps1
```

## Performance

Обе repeat canonical строки совпали:

```text
directRequests=10000 pathRequests=1000 directResults=10000 pathResults=1000 cacheHits=999 cacheMisses=1 directBackendCalls=10000 computedBackendDirectCalls=3000 backendPathCalls=1 peakQueue=1 peakWorkers=1 peakCache=1 maximumWaypoints=3 cancelled=0 timedOut=0
```

Canonical SHA-256:
`0F7391E50732132B86F931C62120344281203B05FDFE169AB51BE80A7BC148F3`.

Дополнительные `computedBackendDirectCalls=3000` честно отражают initial direct
и два segment validation calls для каждой из 1000 path/cache requests.
Cache hit rate остаётся 99,9%, backend A* — один вызов, structural bounds
детерминированы. Elapsed time не используется как speed gate.

## Deviations, limitations and risks

- Pre-change `ant verify` и frozen Goal 009 verifier прошли runtime regressions,
  но ожидаемо остановились на единственном allowlist finding: новый untracked
  Goal 009A package отсутствует в frozen Goal 009 scope.
- Реальных geodata region files нет; contract проверен deterministic injected
  backend, но region-backed door/fence/A* runtime остаётся за пределами задачи.
- Legacy A* не прерывается: cancellation/deadline обеспечивают late discard и
  retained worker ownership до возврата.
- Dispatcher contract предполагает, что `false` означает отсутствие принятой
  задачи; production race со shared pool закрыт shutdown ordering gate.
- Topology, anchors, rooms, Gatekeepers, party routes, automatic movement,
  Goal 010 и Goal 011 не начинались.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 24 text artifacts,
  0 matches;
- escaped Cyrillic в изменённых файлах проверены: 24 text artifacts,
  0 matches.

## Git

Git-команды разрешены TASK.md для baseline/scope audit, exact diff, одного
ordinary commit и push.

```text
Expected commit parent: b6e893f6bb8abf26908e441ee79b92d6f910eb91
Commit SHA: во внешнем final handoff для сохранения одного ordinary commit
Push result: во внешнем final handoff
```

## Immutable independent handoff

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

Полный post-push verifier hash во внешнем handoff не сохранился и здесь не
восстанавливается предположением.

## Next step

Goal 009A закрыта независимым review. Разрешена только bounded Goal 010;
Goal 011 остаётся `NOT_STARTED`.

Result:
`NAVIGATION_ROUTE_OWNERSHIP_HARDENED_PENDING_INDEPENDENT_REVIEW`.
