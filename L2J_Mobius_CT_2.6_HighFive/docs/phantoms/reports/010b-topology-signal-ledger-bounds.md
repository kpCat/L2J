# Task 010B — bounded topology signal ownership

## Status

```text
Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Base: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Expected parent: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Branch: feature/phantom-world
Subject: fix(phantoms): bound topology signal ownership
Goal 010: FIX_REQUIRED
Goal 010A: ACCEPT_WITH_010B_BOUNDARY
Goal 010B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

## Summary

Unbounded `_sequences` и отдельный `_pendingCleanup` удалены из
`PhantomPerceptionProvider`. Вместо них один bounded map хранит не более
`maximumRegisteredProfiles` записей `PhantomTopologySignalLedger`. Каждая
запись содержит только три фиксированных sequence slot, три фиксированных
source state и два cleanup-флага.

Capacity теперь учитывает одновременно зарегистрированные профили, retained
sequence tombstones и failed-cleanup identities. Ledger резервируется до
публикации профиля в registry; исчерпание возвращает отдельный
`SIGNAL_LEDGER_CAPACITY`. Пустая резервация откатывается при неуспешной
регистрации.

Inactive targetability для never-owned profile ID не создаёт ledger, не
выделяет sequence и не вызывает scheduler port. Retained identity сохраняет
sequence ownership при повторной регистрации. Ledger удаляется только после
одного cleanup pass, в котором все три fixed source вернули
`NOT_REGISTERED`, либо при final stop.

`STALE` withdrawal считается безопасным только при локальном предыдущем
`INACTIVE_CONFIRMED`. Неоднозначный `STALE` и невозможные submit-статусы
`STALE`, `REJECTED`, `NOT_RUNNING`, `SEQUENCE_EXHAUSTED` fail closed.

## Read-first audit and reused patterns

Прочитаны:

- `AGENTS.md`, master plan, workflow contract и task package standard;
- roadmap, topology/perception contract, packages и отчёты Goals 010, 010A,
  текущий package 010B;
- текущие provider/service/profile registry/metrics/signal port;
- generation coordinator, scheduler adapter и scheduler submit/withdraw
  contracts;
- релевантные topology, generation, perception, performance, navigation,
  shutdown и regression suites;
- `build.xml`, launch routes и verifier Goal 010A.

Project `README.md`, отдельные `docs/README.md`, code-map и pattern-файлы не
найдены.

Переиспользованы локальные паттерны:

- reserve-before-publication и rollback пустой резервации из decision
  ownership;
- fixed-source monotonic truth из scheduler signal ownership;
- current/peak/capacity aggregate gauges из scheduler/navigation metrics;
- существующие generation lease, service lifecycle и synchronous delivery
  gate без нового executor/thread/task.

Непроверенным до independent review остаётся только ручное принятие границы
Goal 010B. Внешние event producers и runtime без geodata не проверялись,
поскольку они вне scope.

## Changed files

Production:

- `PhantomTopologySignalLedger.java` — fixed per-profile ledger;
- `PhantomPerceptionProvider.java` — bounded reservation, source truth,
  cleanup/re-registration/reload/stop ownership;
- `PhantomTopologyProfileRegistry.java` — explicit ledger-capacity result;
- `PhantomTopologyMetrics.java` — current/peak/capacity gauges;
- `PhantomTopologyService.java` — aggregate ledger observability.

Tests/build/verifier:

- `PhantomTopologySignalLedgerSuite.java` — 20 focused cases;
- `PhantomTopologyGenerationSuite.java`;
- `PhantomTopologyPerceptionSuite.java`;
- `PhantomTopologyPerformanceSuite.java`;
- `PhantomTestLauncher.java`;
- `build.xml`;
- `tools/phantoms/verify-task-010b.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md`;
- `docs/phantoms/reports/010a-topology-generation-signal-ownership.md`;
- `docs/phantoms/reviews/010a-topology-generation-signal-ownership-review.md`;
- весь переданный task package
  `docs/phantoms/tasks/010b-topology-signal-ledger-bounds/**`;
- этот отчёт.

Это bounded exception к обычному порогу 8–10 файлов: точный artifact allowlist
задан `TASK.md`; production-изменение ограничено пятью topology-классами и
одним новым маленьким ledger.

## Architecture decisions

- Единственная динамическая identity-структура provider — capped
  `Map<Long, PhantomTopologySignalLedger>`.
- Ledger содержит ровно `local_chat`, `combat`, `targetability`; динамической
  source/history collection нет.
- Sequence увеличивается до каждого submit/withdraw и не сбрасывается при
  unregister/re-register.
- `ACCEPTED`/`COALESCED` submit означает `POSSIBLY_ACTIVE`;
  `ACCEPTED`/`COALESCED`/`NOT_REGISTERED` withdrawal означает
  `INACTIVE_CONFIRMED`.
- `BACKPRESSURE` и submit `NOT_REGISTERED` не подменяют локальную source truth;
  невозможные статусы возвращают `SIGNAL_FAILURE`.
- Cleanup release требует all-three `NOT_REGISTERED` и отсутствия профиля в
  topology registry. Обычный успешный withdrawal сохраняет tombstone.
- Reload использует только уже существующие ledgers. Final stop очищает весь
  ledger map после quiescence.

Topology XML/canonical corpus, loaders/query/snapshot, generation coordinator
и ordering, scheduler implementation/semantics, navigation, decision,
materialization, lifecycle order, config и schema не изменены.

## DB, migrations and configs

- Production DB `l2jmobiush5` не изменялась.
- Использовалась только существующая test DB route
  `l2jmobiush5_phantom_test`.
- Миграций и schema changes нет.
- Config keys/defaults и `PhantomPlayers.ini` не изменялись.
- Production topology XML не изменялся.

## Tests and commands

Seed: `20260725001`.

Использован официальный Apache Ant 1.10.15 launcher во временном каталоге,
поскольку `ant` отсутствует в `PATH`. Архив launcher проверен по официальному
SHA-512. JDK: `25.0.4`.

Pre-change:

```text
ant verify: runtime routes PASS; ожидаемый static FAIL 81/82 только из-за
untracked task package 010B
verify-task-010a.ps1: ожидаемый FAIL 81/82 по той же exact-scope причине
```

Focused и frozen-semantics routes:

```text
topology signal ledger: 20/20 ×3
topology generation: 17/17 ×3
topology perception: 28/28 ×3
topology core: 38/38 ×3
production corpus: 6/6 ×2
topology performance: 1/1 ×2, плюс два evidence-прогона
navigation core: 50/50 ×3
navigation performance: 1/1 ×3
shutdown handoff: 7/7 ×3
```

Regression routes:

```text
decision core/persistence/performance: 35/35, 23/23, 2/2
activity scheduler: 20/20
production materialization: 20/20
headless player: 18/18
profile persistence: 18/18
database integration: 9/9
harness unit: 66/66
skeleton: 12/12
lifecycle negative control: ожидаемые 0/2 при успешном control target
```

До создания этого отчёта verifier дал `PASS=83 FAIL=2 TOTAL=85`; оба FAIL
были только отсутствующим отчётом 010B и зависимой `docs.required`.

Финальный pre-commit gate:

```text
ant verify: PASS
ant jar: PASS
verify-task-010b.ps1: PASS 85/85
GameServer.jar: production ledger присутствует, test classes отсутствуют
```

Post-commit `verify`/`jar`, два byte-identical verifier-прогона и push result
фиксируются во внешнем final handoff.

## Performance measurements

Два измеряемых evidence-прогона:

```text
nodes=10000
edges=20000
anchors=50000
profiles=10000
signalLedgersCurrent=10000
signalLedgersPeak=10000
signalLedgerCapacity=10000
localChatEvents=1000
combatEvents=1000
maximumRecipients=3
datasetHash=e4c1dc8945ae9bb8c15ec688c73e249b05ed110877a7da809cde46a3472f5a05
elapsedMillis=919, 932
```

Elapsed используется как evidence, а не как speed gate. Bounded invariant
проверен отдельными high-churn, exact-capacity и concurrent focused cases.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Совпадений в изменённых user-facing строках нет.

## Deviations, limitations and risks

- Cleanup не пытается откатить уже выполненные withdrawals, если следующий
  fixed source вернул failure; ledger сохраняется и cleanup можно безопасно
  повторить с более новыми sequences.
- Retained tombstones намеренно могут заполнить весь cap. Это fail-closed
  bounded ownership, а не автоматическое вытеснение identity.
- Goal 010B не подключает producer listeners, не меняет navigation/scheduler
  semantics и не исправляет отсутствие geodata.
- Goal 010B не принимает собственный manual gate; independent review
  обязательно. Goal 011 остаётся заблокирован, Goal 012 не начат.

## Git

Разрешённые `TASK.md` git-проверки использовались только для baseline,
scope/diff guard, одного ordinary commit, push и remote-exact confirmation.
Использованные inspection-команды:

```text
git rev-parse --show-toplevel
git status --short --branch
git branch --show-current
git rev-parse HEAD
git fetch origin feature/phantom-world
git diff --check
git diff --stat
git diff --name-status
git ls-files --others --exclude-standard
```

Commit/push commands после всех pre-commit gates:

```text
git add -- <exact TASK.md allowlist>
git commit -m "fix(phantoms): bound topology signal ownership"
git push origin feature/phantom-world
git ls-remote --heads origin feature/phantom-world
```

Amend, rebase, merge, reset, restore и force push не используются.

```text
Commit SHA: во внешнем final handoff
Push result: во внешнем final handoff
```

## Next step

Только independent review Goal 010B с проверкой bounded capacity, fixed source
truth, all-three absence proof и frozen-scope evidence. Goal 011/012 до
принятия gate не начинать.

## Independent review и immutable handoff

```text
Commit: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Parent: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Push/remote: exact
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
Perception: 28/28 ×3
Core: 38/38 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation and shutdown regressions: PASS
Final verifier: 85/85 ×2, byte-identical
External verifier SHA: abbreviated handoff only
ADA98158...25CCA
Independent review:
- bounded ledger architecture ACCEPT
- absent-source real scheduler reconciliation FIX_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
```

Отсутствующий полный SHA внешнего verifier не восстанавливался предположением.
Bounded ledger architecture, capacity и all-three `NOT_REGISTERED` release
приняты без revert. Узкое real-scheduler absent-source finding закрывается
только Goal 010C; до её независимого review Goal 011 остаётся заблокированной.
