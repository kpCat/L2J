# Goal 008A — decision persistence and timeout hardening

## Status

```text
Status: SUCCESS
Manual gate: PENDING_INDEPENDENT_REVIEW
Required baseline: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Branch: feature/phantom-world
Subject: fix(phantoms): harden decision persistence and timeouts
Goal 008: FIX_REQUIRED
Goal 008A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 009: NOT_STARTED / BLOCKED
```

## Summary

Закрыты только findings независимого review Goal 008:

- ни один `GoalStore`/JDBC вызов больше не выполняется под global
  decision-engine monitor;
- attach использует bounded pending reservation и не публикует поздний runtime
  после stop;
- mutation, reload и terminal writes используют двухфазный
  claim → external store call → exact reconcile;
- на runtime допускается ровно один persistence claim с уникальным operation
  token;
- concurrent ownership, optimistic conflict и store exception имеют разные
  `BUSY`, `PERSISTENCE_CONFLICT` и `PERSISTENCE_FAILED` результаты;
- detach/stop сохраняют slot до возврата уже owned persistence call;
- cancellation token, `beginStop()` и другой profile остаются responsive при
  blocked store;
- step timeout использует явный unset sentinel `-1`, поэтому logical time `0`
  остаётся допустимым временем старта;
- goal/activity/stop boundaries очищают stale candidate/score/explanations и
  last-result evidence.

Immutable model, codec, Utility scoring, schema, config, Goal 006 lifecycle,
scheduler ownership и будущий Goal 009 scope не изменялись.

## Read-first evidence and reused patterns

Перед изменениями прочитаны обязательные master/workflow/task документы,
Goal 008 report и contract, independent review package, весь decision package,
`PhantomSystem`, релевантные scheduler/profile persistence seams и focused
suites. Parent `AGENTS.md` выше модуля, project `README.md` и отдельный
`docs/*.md` index не найдены; повторный поиск не выполнялся.

Переиспользованы локальные паттерны:

- scheduler claim под monitor → внешний вызов → exact reconcile;
- scheduler/materialization retention до quiescence owned operation;
- optimistic component row version без automatic retry;
- bounded lifecycle finish, который возвращает `false`, пока ownership не
  quiescent.

До реализации оставались непроверенными blocked-store responsiveness и
logical-zero timeout; они закрыты focused regression tests.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java`.

Tests/build:

- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java`;
- `build.xml`;
- `tools/phantoms/verify-task-008a.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md`;
- `docs/phantoms/reports/008-goal-utility-plan-core.md`;
- `docs/phantoms/reviews/008-goal-utility-plan-core-review.md`;
- пакет `docs/phantoms/tasks/008a-decision-persistence-timeout-hardening/**`;
- этот отчёт.

Task 008A прямо разрешает bounded exception по числу файлов для одной
artifact family: decision persistence ownership, focused regressions,
successor verifier и gate documentation. Другие хроники не изменялись.

## Architecture decisions

### Bounded pending attach

`attach()` резервирует profile ID в `_pendingAttaches` под monitor. Общий
capacity check учитывает published slots и pending reservations. `profileExists`
и `load` вызываются вне monitor. Reconcile удаляет reservation и публикует slot
только если engine всё ещё `RUNNING`; иначе возвращается
`CANCELLED_BY_STOP`. Store exception возвращает `PERSISTENCE_FAILED`, не
создавая runtime.

### One persistence claim per runtime

Immutable `PersistenceClaim` фиксирует exact slot identity, operation ID/kind,
expected goal identity, expected component row version и replacement payload.
Runtime публикует `_persistenceInFlight` и ownership metadata под monitor.
Второй mutation/reload не ждёт store и возвращает `BUSY`.

External `insert`, `replace`, `delete` и `load` выполняются вне monitor.
Reconcile проверяет exact slot identity и все claim fields. Hidden retries,
executor, future, background worker и second persistence owner не добавлены.

### Conflict and failure

Optimistic `false` и repository exception не объединяются:

- conflict → explicit `PERSISTENCE_CONFLICT` и
  `PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD`;
- exception → explicit `PERSISTENCE_FAILED` и
  `PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD`;
- recovery остаётся только через explicit reload.

Добавлена агрегированная метрика persistence failures без dynamic labels и
exception text.

### Terminal persistence and retention

Terminal handler result создаёт terminal goal и persistence claim под monitor,
оставляя handler/runtime in flight. Store write выполняется снаружи; только
exact successful reconcile публикует terminal goal. Conflict и failure
публикуют разные recovery states.

Detach/stop сначала меняют generation и cancellation token, но не удаляют slot
с owned handler/persistence. Slot удаляется после reconcile, когда оба
ownership markers quiescent. `finishStop()` возвращает `false` при pending
attach, handler или persistence in flight.

### Timeout sentinel and evidence ownership

`STEP_START_UNSET = -1`; legitimate logical start `0` не считается unset.
Snapshot evidence сбрасывается при goal replace/clear/reload,
activity-generation change и stop. Final ordinary step `SUCCESS` не завершает
ACTIVE goal автоматически: runtime возвращается в `NEEDS_REPLAN`.

## DB, migrations and config

```text
Production DB: не использовалась
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Config: unchanged
Goal component schema/codec: unchanged
Goal 006 lifecycle: unchanged
```

## Tests and commands

Seed сохранён из Goal 008: `20260725001`. Apache Ant 1.10.17 запускался
абсолютным локальным launcher, поскольку `ant` отсутствует в `PATH`.

| Gate | Result |
|---|---:|
| `ant compile-tests` | PASS; 1948 production / 35 test sources |
| Decision core | PASS `35/35 ×3` |
| Decision persistence | PASS `23/23 ×3` |
| Decision performance | PASS `2/2 ×2`, canonical summaries byte-identical |
| Decision performance canonical SHA-256 | `8DE1D8C99704CFA12E25EBEE022A1F2535B7A0A8A7BFFAA9D4F13E99F2AE12DE` |
| Scheduler regressions/integration | PASS `20/20 ×3` |
| Scheduler scale | PASS `2/2 ×2`, canonical summaries byte-identical |
| Scheduler scale canonical SHA-256 | `66D3B9D99FB7B59FC5A1AF2F051D7B4CFD347793138CA1F11BAC65D57B00E563` |
| Production materialization | PASS `20/20 ×3` |
| Shutdown handoff | PASS `5/5 ×3` |
| Headless Player / performance | PASS `18/18` / `2/2` |
| Profile persistence / DB integration | PASS `18/18` / `9/9` |
| Harness unit / skeleton | PASS `66/66` / `12/12` |
| Scenario / harness performance | PASS `1/1` / `1/1` |
| Production materialization performance | PASS `2/2` |
| `ant verify` pre-commit | PASS; `1 min 31 s` |
| отдельный `ant jar` pre-commit | PASS; `13 s` |
| Production `GameServer.jar` decision entries | `50` |
| Production `GameServer.jar` test entries | `0` |
| Goal 008A verifier pre-commit | PASS `58/58 ×2`, byte-identical |
| Pre-commit verifier output SHA-256 | `615165C7F2988E544C55F2111560665F0469C50CA5A152AA0922456BB50CD5C2` |
| Post-commit `verify` / `jar` / verifier | во внешнем final handoff |

Ожидаемые внутренние negative-control outputs не считаются failures, если
соответствующая Ant target завершается `BUILD SUCCESSFUL`.

Основные команды:

```text
<local-ant-1.10.17>\bin\ant.bat compile-tests
<local-ant-1.10.17>\bin\ant.bat phantom-decision-core-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-decision-persistence-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-decision-performance-smoke ×2
<local-ant-1.10.17>\bin\ant.bat phantom-activity-scheduler-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-activity-scheduler-performance-smoke ×2
<local-ant-1.10.17>\bin\ant.bat phantom-production-materialization-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-server-shutdown-handoff-test ×3
<local-ant-1.10.17>\bin\ant.bat verify
<local-ant-1.10.17>\bin\ant.bat jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-008a.ps1 ×2
```

## Performance and liveness

Сохраняется Goal 008 performance shape: 1000 runtimes, 64 candidates, 8
considerations и dispatch budget 32. Production decision package не содержит
`Thread`, `Future` или `Executor`.

Blocked-store regressions используют controlled latches и требуют:

- cancellation-token read менее чем за 1 секунду;
- `beginStop()` менее чем за 1 секунду;
- snapshot/find/list и operation другого profile менее чем за 1 секунду;
- отсутствие late attach publication и потерянного retained ownership.

## Deviations, limitations and risks

- Pre-change `ant verify` дошёл до static gate и ожидаемо обнаружил новый
  untracked Goal 008A package вне frozen allowlist старого Goal 008 verifier.
  Production и regression targets до него прошли.
- Один ранний параллельный запуск двух Ant targets столкнулся за общий build
  directory и дал `NoClassDefFoundError`; targets сразу перезапущены
  последовательно. Это runner/setup race, а не product failure.
- Решение остаётся cooperative: уже начатый JDBC вызов не прерывается.
  Bounded guarantee состоит в responsiveness других profiles/stop и retention
  ownership до возврата store.
- Goal 009 не начат.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 19 text artifacts,
  0 matches;
- escaped Cyrillic в изменённых файлах проверены: 19 text artifacts,
  0 matches.

## Git

Git разрешён прямым требованием Task 008A для baseline/scope audit, одного
ordinary commit и push.

```text
Expected commit parent: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Commit SHA: во внешнем final handoff для сохранения одного ordinary commit
Push result: во внешнем final handoff
```

## Next step

Только независимое review Goal 008A. Goal 009 остаётся
`NOT_STARTED / BLOCKED`.

Result:
`DECISION_PERSISTENCE_TIMEOUT_HARDENED_PENDING_INDEPENDENT_REVIEW`.

## Immutable independent-review handoff

```text
Commit: 6ecd8ba155e63a2dedeeafd65c1961fdb57bf261
Parent: b6c58c37f1ba77e92b61e9499a30d17d09c82086
Push/remote: exact
Core: 35/35 ×3
Persistence: 23/23 ×3
Performance: 2/2 ×2
Scheduler: 20/20 ×3
Materialization: 20/20 ×3
Shutdown: 5/5 ×3
Final verifier: 58/58 ×2, byte-identical
Independent review: ACCEPT
Goal 008: ACCEPT after Goal 008A
Goal 008A: ACCEPT
Goal 009: ALLOWED
```

Этот блок фиксирует уже опубликованный результат как неизменяемый baseline.
Следующая задача не изменяет decision persistence implementation и не
переоткрывает принятый gate.
