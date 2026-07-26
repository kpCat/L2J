# Goal 007 — shared activity scheduler

## Status и baseline

```text
Status: ACTIVITY_SCHEDULER_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Baseline/parent: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Branch: feature/phantom-world
Subject: feat(phantoms): add shared activity scheduler
Manual gate: PENDING_INDEPENDENT_REVIEW
Goal 008: NOT_STARTED
Goal 009: NOT_STARTED
```

## Goal 006B и Stage I

До production-изменений Goal 007 выполнено независимое review неизменного
baseline `82a03342...`. Подтверждены strict managed-actor classifier,
двухфазный drain, in-flight reuse и fail-closed retention.

```text
Goal 006B: ACCEPT
Goal 006 overall: ACCEPT
Stage I: COMPLETE
```

Immutable review:
`docs/phantoms/reviews/006b-server-shutdown-handoff-review.md`. Roadmap изменён
только в части progress/status: Goal 007 ожидает независимого review, Goal 008
и Goal 009 не начаты.

## Config и disabled behavior

Добавлены ровно три scheduler-настройки:

| Key | Default | Enabled range |
|---|---:|---:|
| `MaxScheduledPhantomProfiles` | 10000 | 1..1000000 |
| `PhantomSchedulerPulseMillis` | 100 | 10..1000 ms |
| `PhantomSchedulerProfilesPerPulse` | 128 | 1..10000 |

`MaxScheduledPhantomProfiles` обязан быть не меньше
`MaxMaterializedPhantoms`. Missing/malformed/out-of-range enabled config
fail-closed. Disabled settings имеют materialization/scheduler capacity и
intervals `0`; repository, materialization service, scheduler и recurring
future не создаются, DB не открывается.

## State, signals и boundedness

Реализованы только пять состояний со стабильными кодами:
`ACTIVE(10)`, `NEARBY_PERCEPTIBLE(20)`, `WARM(30)`, `BACKGROUND(40)`,
`SLEEPING(50)`.

Immutable relevance signal содержит валидированный source key, монотонную
sequence, required state и TTL. Source regex:
`^[a-z][a-z0-9_.-]{0,63}$`; TTL `1..86_400_000` ms; максимум 16 source keys.
Stale update/withdrawal не меняет состояние.

Registry ограничен configured capacity. Ready queue — bounded
`ArrayBlockingQueue<Long>`, due ordering — `TreeSet` с максимум одной due entry
на профиль. Signal mutation выполняется только после успешного ready reserve;
saturation возвращает backpressure без изменения signal. Updates уже
enqueued profile coalesce в одну entry.

## State machine, lifecycle и retry

Requested state — наиболее детальное среди живых signals. Promotion immediate;
demotion проходит deterministic hysteresis grace. Один profile не
обрабатывается повторно в одном pulse.

`ACTIVE` и `NEARBY_PERCEPTIBLE` effective только после успешной canonical
materialization. Production bridge отображает принятые lifecycle results в
`SUCCESS`, `TRANSIENT_BLOCK`, `RETAINED_FAILURE`; вызовы выполняются вне
глобального scheduler lock. Clean capacity/transient block получает bounded
backoff. Retained lifecycle failure не повторяется автоматически и требует
`retryTransition(profileId)`.

Реальный adapter materialize/dematerialize проверен в guarded test
DB/headless environment. Materialization service/core, identity/profile
packages, `Player` и `Shutdown` не менялись.

## Pulse, fairness, work и overload

Production создаёт один recurring pulse на существующем
`ThreadPool.scheduleAtFixedRate`. Item budget задаётся
`PhantomSchedulerProfilesPerPulse`; дополнительный wall-clock budget bounded.
Due ordering использует deadline/fairness sequence, hot profile не может
обработаться дважды в одном pulse.

Typed work sink получает immutable технический item без `Player` и domain
objects. Production sink — `no-op`; production стартует с `0` profiles, без
auto registration/materialization. Исключение sink изолируется.

Overload использует уровни `NORMAL/ELEVATED/HIGH/CRITICAL`. Состояние и signals
не меняются: cadence `ACTIVE`/`NEARBY_PERCEPTIBLE` неизменна, только
`WARM`/`BACKGROUND` деградируют множителем `1/2/4/8`.

## PhantomSystem и shutdown

Start: repository → materialization service → scheduler → один pulse. Stop:
scheduler begin-stop → materialization drain/retry → scheduler finish-stop
только после service `STOPPED`. Неудачный drain оставляет scheduler `STOPPING`,
slots и configured instance retained для явного повторного shutdown.

## Metrics и diagnostics

Добавлены только fixed aggregate counters: current/peak registrations и
states, signal/queue/pulse/due/work/transition/retry/stop/overload categories.
State counts используют fixed `AtomicLongArray`; dynamic labels отсутствуют.
Per-pulse/per-profile INFO/WARNING и строковый trace на каждый pulse не
добавлены.

## Tests и performance

Seed: `20260725001`. Scheduler suite содержит 12 deterministic cases с manual
clock/pulse driver, без sleeps и DB. Actual lifecycle adapter проверяется
production materialization suite на `l2jmobiush5_phantom_test`.

| Gate | Result |
|---|---:|
| Scheduler suite run 1 | PASS 12/12 |
| Scheduler suite run 2 | PASS 12/12 |
| Scheduler suite run 3 | PASS 12/12 |
| Performance smoke run 1 | PASS 2/2 |
| Performance smoke run 2 | PASS 2/2 |
| Deterministic summaries | byte-identical; SHA-256 `67b7fc26b98141661890dfaae5f307b86bb5c768ea82a2df6a8d1f1556f7ee30` |
| Production materialization bridge/regression | PASS 19/19 ×3 |
| Shutdown-handoff regression | PASS 4/4 ×3 |
| Headless Player | PASS 18/18 |
| Profile persistence | PASS 18/18 |
| DB integration | PASS 9/9 |
| Harness unit | PASS 66/66 |
| Skeleton | PASS 12/12 |
| Existing performance suites | PASS 2/2, 2/2 и 1/1 |
| Existing scenario smoke | PASS 1/1 |
| Expected negative controls | PASS |
| `ant verify` pre-commit | PASS; 1 min 44 s |
| `ant jar` pre-commit | PASS; 1924 production sources; 13 s |
| Production `GameServer.jar` test entries | 0 |
| Static verifier pre | PASS 57/57 |
| Static verifier final 1/2 | pending |

Scale smoke регистрирует 10,000 dormant `SLEEPING` profiles: `ready=0`,
`due=0`, per-slot `Future/Thread/Executor/Player` отсутствуют. Затем 10,000
profiles переводятся в `WARM` при budget 128; ready/due остаются bounded,
fairness требует первый delivery всему cohort до второго delivery любому
профилю. Измерено 79 pulses до первой возможности всего cohort,
`maximumReady=10000`, `maximumDue=10000`, CRITICAL WARM due =
`24_000_000 ns`. Production DB `l2jmobiush5` не читается и не изменяется.

Ранний combined targeted run обнаружил, что standalone skeleton suite не
инициализировал shared `ThreadPool`: inert baseline раньше этого не требовал.
Исправлен только suite lifecycle симметричными `ThreadPool.init()/shutdown()`;
повторный skeleton run и cumulative verify прошли. Production workaround не
добавлялся.

## Scope, ограничения и handoff

Bounded exception по числу файлов задан TASK 007: scheduler contracts/runtime,
ровно три config keys, focused suites/build/verifier и обязательные
review/contract/report/progress artifacts являются одной artifact family.

Не реализованы topology, goals, Utility AI, plan executor, population,
schedules, navigation, Goal 008 или Goal 009. Persistence/schema/migrations и
автоматическое profile discovery не добавлены. Work sink намеренно `no-op`.

Commit SHA, push, post-commit verify/jar и два byte-identical verifier outputs
фиксируются во внешнем final handoff после commit этого отчёта. Self-accept
Goal 007 запрещён.

## Encoding и Git

- mojibake-маркеры в изменённых файлах проверены: 0 совпадений;
- escaped Cyrillic в изменённых файлах проверены: 0 совпадений.

Git используется по прямому требованию TASK 007 для baseline/scope audit,
одного ordinary commit и push. Точные команды перечисляются во внешнем final
handoff.

Result:
`ACTIVITY_SCHEDULER_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
