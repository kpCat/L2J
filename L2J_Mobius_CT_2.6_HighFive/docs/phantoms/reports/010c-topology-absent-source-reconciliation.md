# Goal 010C — topology absent-source reconciliation

## Status

```text
Status: SUCCESS
Manual gate: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Base: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Expected parent: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Branch: feature/phantom-world
Subject: fix(phantoms): reconcile absent topology sources
Goal 010: FIX_REQUIRED
Goal 010A: ACCEPT
Goal 010B: ACCEPT_WITH_010C_INTEGRATION_BOUNDARY
Goal 010C: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: NOT_STARTED / BLOCKED
Goal 012: NOT_STARTED
```

## Summary

Real-scheduler absent-source reconciliation исправлена только в
`PhantomPerceptionProvider`. `STALE` withdrawal теперь безопасен при локально
доказанных `NEVER_SUBMITTED` и `INACTIVE_CONFIRMED`; оба переходят в
`INACTIVE_CONFIRMED`. Такой результат не устанавливает `schedulerAbsent` и не
освобождает retained ledger.

`POSSIBLY_ACTIVE` и `OWNERSHIP_UNCERTAIN` при `STALE` остаются fail-closed.
Submit classification, scheduler implementation, generation coordinator,
ledger structure, topology XML/loaders, navigation, decision, lifecycle,
config/schema и production DB не изменялись. Goal 011/012 не начинались.

## Read-first audit and reused patterns

Прочитаны `AGENTS.md`, master plan, roadmap, workflow/package/report standards,
весь Goal 010C package, отчёты/reviews/contracts Goals 010/010A/010B,
`PhantomPerceptionProvider`, fixed ledger, реальные scheduler
`submitSignal/withdrawSignal`, production adapter, focused scheduler tests,
signal-ledger/generation suites, launcher и build routes.

Project `README.md`, отдельный docs code-map/index и pattern-файлы не найдены.

Переиспользованы:

- fixed per-profile ledger с тремя source slots;
- monotonic provider sequence;
- synchronous delivery gate;
- deterministic in-memory topology fixtures и временный reload candidate;
- отдельный focused Ant route с real scheduler adapter.

До independent review остаётся непроверенным только ручное принятие Goal 010C.
Внешние event producers и runtime без geodata остаются вне scope.

## Changed files

Production:

- `PhantomPerceptionProvider.java` — safe `STALE` classification;
- `PhantomTopologySignalLedger.java` — comment-only exclusive ownership invariant.

Tests/build:

- `PhantomTopologySchedulerSignalIntegrationSuite.java`;
- `PhantomTopologySignalLedgerSuite.java`;
- `PhantomTestLauncher.java`;
- `build.xml`;
- `tools/phantoms/verify-task-010c.ps1`.

Documentation:

- roadmap progress;
- topology/perception contract;
- Goal 010B immutable handoff и independent review;
- Task 010C package;
- этот отчёт.

## Architecture decisions

- `NEVER_SUBMITTED` является proof только для source keys, эксклюзивно
  эмитируемых этим topology provider в пределах lifetime одного service.
- Safe `STALE` переводит source в `INACTIVE_CONFIRMED`.
- Safe `STALE` не доказывает scheduler profile absence; release по-прежнему
  требует all-three `NOT_REGISTERED` одного cleanup pass.
- Неоднозначное active/uncertain ownership не очищается.
- Новые executor, thread, Future, background retry или per-profile task не
  добавлены.

## Database and configuration

- Production DB `l2jmobiush5` не использовалась.
- DB/schema/migrations не менялись.
- Config keys/defaults не менялись.
- Production topology XML не менялся.

## Commands and test results

Seed: `20260725001`.

Использован существующий локальный Apache Ant 1.10.15 launcher, поскольку
`ant` отсутствует в `PATH`.

Baseline:

```text
HEAD == origin/feature/phantom-world == 030184205c6...
ant verify: runtime routes PASS; ожидаемый static FAIL 84/85 только из-за
untracked Task 010C package
verify-task-010b.ps1: ожидаемый FAIL 84/85 по той же exact-scope причине
```

Обязательная матрица:

```text
compile-tests: PASS, 1983 production + 44 test source files
real scheduler integration: 5/5 ×3 PASS
signal ledger: 20/20 ×3 PASS
generation ownership: 17/17 ×3 PASS
topology perception: 28/28 PASS
topology core: 38/38 PASS
topology production corpus: 6/6 PASS
topology performance: 1/1 PASS
navigation core/performance: 50/50, 1/1 PASS
shutdown handoff: 7/7 PASS
decision core/persistence/performance: 35/35, 23/23, 2/2 PASS
activity scheduler: 20/20 PASS
production materialization: 20/20 PASS
headless player: 18/18 PASS
profile persistence/DB integration: 18/18, 9/9 PASS
ordinary ant test: 66/66 PASS; lifecycle negative control ожидаемо воспроизведён
skeleton: 12/12 PASS
ant verify: BUILD SUCCESSFUL; real scheduler 5/5; verifier 67/67
ant jar: BUILD SUCCESSFUL; GameServer/LoginServer/DatabaseInstaller собраны
Goal 010C verifier: 67/67 PASS
```

## Performance measurements

Topology performance smoke: `1/1 PASS`. Navigation performance smoke:
`1/1 PASS`, `directRequests=10000`, `pathRequests=1000`, `cacheHits=999`,
`cacheMisses=1`, `backendPathCalls=1`, `peakQueue=1`, `peakWorkers=1`,
`peakCache=1`, `maxWaypoints=3`, `cancelled=0`, `timedOut=0`,
`elapsedNanos=66587300`.

Нового production hot path, коллекции или worker нет.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Deviations, limitations and risks

- Reload integration использует deterministic test XML во временном каталоге,
  а не production topology corpus.
- Real scheduler periodic pulse принадлежит существующему shared `ThreadPool`;
  suite явно запускает и останавливает scheduler, а assertions не зависят от
  pulse timing.
- Goal 010C не принимает собственный manual gate.

## Git

Git-команды разрешены `TASK.md` для baseline/scope guard, одного ordinary
commit, push и remote-exact confirmation.

```text
Expected parent: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

Amend, rebase, merge и force push не используются.

## Recommended next step

Только independent review Goal 010C. Goal 011 остаётся `NOT_STARTED / BLOCKED`,
Goal 012 — `NOT_STARTED`.

Result:
`TOPOLOGY_ABSENT_SOURCE_RECONCILED_PENDING_INDEPENDENT_REVIEW`.

## Immutable independent-review handoff

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
