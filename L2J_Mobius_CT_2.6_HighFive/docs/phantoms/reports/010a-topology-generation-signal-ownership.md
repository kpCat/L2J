# Goal 010A — topology generation и signal ownership

## Status

```text
Status: SUCCESS
Manual gate: ACCEPT_WITH_010B_BOUNDARY
Base: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Branch: feature/phantom-world
Subject: fix(phantoms): harden topology generation ownership
Goal 010: FIX_REQUIRED
Goal 010A: ACCEPT_WITH_010B_BOUNDARY
Goal 010B: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

## Summary

Закрыты только findings Goal 010 по exact topology generation и lifecycle
provider-owned signals. Один fair generation coordinator упорядочивает profile
update, perception delivery, reload и stop. Успешный reload под write ownership
пересобирает memberships всех сохранённых points, сохраняет position sequences,
invalidates все три topology sources и только затем публикует candidate.

Unregister удаляет membership до финальных withdrawals local-chat, combat и
targetability. Inactive targetability выполняет withdraw и после unregister.
Cleanup failure явный, сохраняется как pending, блокирует unsafe
re-registration и завершается explicit retry с monotonic sequences.

Topology XML, loader/query/snapshot model, server loaders, navigation,
decision, scheduler semantics, materialization, lifecycle ordering,
config/schema и production DB не изменялись. Goal 011/012 не начинались.

## Read-first и локальные паттерны

Прочитаны обязательные master/workflow/package/report документы, весь пакет
Goal 010/010A, отчёты и review Goals 009A/010, topology/scheduler contracts,
актуальный topology production code, четыре topology suites, scheduler signal
boundary, build и launcher. README, отдельный code-map/index и
`REPORT_STANDARD.md` не найдены.

Переиспользованы локальные паттерны:

- bounded fair ownership без нового executor;
- complete candidate → deterministic validation → atomic swap;
- monotonic scheduler source sequence;
- explicit lifecycle token и delivery gate;
- immutable snapshots и focused deterministic suites.

## Changed files

Production:

- `PhantomTopologyGenerationCoordinator.java`;
- `PhantomTopologyService.java`;
- `PhantomTopologyProfileRegistry.java`;
- `PhantomPerceptionProvider.java`;
- `PhantomRelevanceSignalPort.java`;
- `PhantomTopologyMetrics.java`.

Tests/build:

- `PhantomTopologyGenerationSuite.java`;
- `PhantomTopologyCoreSuite.java`;
- `PhantomTopologyPerceptionSuite.java`;
- `PhantomTopologyPerformanceSuite.java`;
- `PhantomTestLauncher.java`;
- `build.xml`;
- `tools/phantoms/verify-task-010a.ps1`.

Documentation:

- roadmap progress;
- topology/perception contract;
- Goal 010 immutable handoff и review;
- Task 010A package;
- этот отчёт.

## Architecture decisions

- Service владеет ровно одним fair `ReentrantReadWriteLock` через маленький
  package-private coordinator.
- Profile update держит read lease от exact query view до registry commit.
- Event держит read lease до последнего scheduler submit/withdraw.
- Reload строит candidate вне write lease; под write lease повторно разрешает
  все stored points, invalidates sources, устанавливает memberships и делает
  snapshot/query swap.
- Signal invalidation failure возвращает отдельный result и оставляет прежние
  snapshot/hash/generation/membership активными.
- Unregister и event delivery сериализованы одним delivery gate; scheduler call
  не выполняется под service или registry monitor.
- Cleanup остаётся синхронным и bounded; background worker не добавлен.

## Database changes

Нет. Production DB `l2jmobiush5` не использовалась. DB regressions направлены
только в `l2jmobiush5_phantom_test`.

## Configuration changes

Нет. Config keys, defaults, schema и migrations не менялись.

## Commands and test results

До изменений cumulative runtime suites прошли; frozen verifier ожидаемо
остановился только на untracked Task 010A package:

```text
ant verify: runtime suites PASS, frozen Goal 010 verifier 81/82
verify-task-010.ps1: 81/82
```

Обязательные focused и regression routes:

```text
compile-tests: PASS, 1982 production + 42 test source files
topology core: 38/38 ×3
topology perception: 28/28 ×3
topology generation ownership: 17/17 ×3
production corpus: 6/6 ×2
topology performance: 1/1 ×2 required evidence repeats
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
static verifier pre-commit: 82/82
ant verify pre-commit: PASS
ant jar pre-commit: PASS
GameServer.jar topology entries: 66
GameServer.jar test entries: 0
```

Штатный intentional lifecycle negative control внутри `ant test` воспроизвёл
ожидаемые 2 failures, а harness route завершился `BUILD SUCCESSFUL`.
Post-commit verify/jar/verifier evidence фиксируется во внешнем final handoff,
чтобы не выдумывать self commit SHA до его создания.

## Performance measurements

Два финальных evidence-прогона:

```text
nodes=10000
edges=20000
anchors=50000
profiles=10000
localChatEvents=1000
combatEvents=1000
maximumRecipients=3
datasetHash=e4c1dc8945ae9bb8c15ec688c73e249b05ed110877a7da809cde46a3472f5a05
elapsedMillis=947, 842
```

Elapsed используется только как evidence, а не как speed gate. Два более
ранних smoke-прогона также завершились 1/1; измеряемыми обязательными повторами
для отчёта являются два значения выше.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Совпадений в изменённом scope нет.

## Deviations, limitations and risks

- Откат уже выполненной части signal invalidation при последующем failure не
  выполняется; активной остаётся старая topology generation, а безопасные
  withdrawals могут быть повторены.
- Goal 010A не подключает новые event producers и не расширяет topology corpus.
- Независимое review обязательно; этот task не принимает свой manual gate.

## Git

```text
Expected parent: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

Git-команды разрешены `TASK.md` для baseline/scope guard, единственного
ordinary commit и push. Amend, rebase, merge и force push не используются.

## Independent review и immutable handoff

```text
Commit: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Parent: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Push/remote: exact
Core: 38/38 ×3
Perception: 28/28 ×3
Generation: 17/17 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation: 50/50 ×3
Shutdown: 7/7 ×3
Verifier: 82/82 ×2, byte-identical
Verifier SHA-256:
5751E0AEED65FB392D36CC66716DC985CE747F4801FDB2A99AA085CA5B72A802
Independent review:
- generation/signal ordering ACCEPT
- bounded signal ledger FIX_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
```

Generation/reload/unregister ordering Goal 010A принято без revert. Отдельные
findings lifetime capacity, never-owned inactive allocation и ambiguous
STALE/submit truth переданы bounded Goal 010B.

## Recommended next step

Только Goal 010B и её независимый review. Goal 011 остаётся `BLOCKED`, Goal 012
— `NOT_STARTED`.

Historical implementation result:
`TOPOLOGY_GENERATION_SIGNAL_OWNERSHIP_HARDENED_PENDING_INDEPENDENT_REVIEW`.

Current review result:
`TOPOLOGY_GENERATION_SIGNAL_ORDERING_ACCEPT_WITH_010B_BOUNDARY`.
