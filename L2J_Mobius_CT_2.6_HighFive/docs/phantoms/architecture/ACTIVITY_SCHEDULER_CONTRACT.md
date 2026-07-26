# Контракт shared activity scheduler

## Назначение и границы

`PhantomScheduler` — единый bounded runtime-механизм для явно
зарегистрированных profile IDs. Он не определяет topology, игровые цели,
Utility AI, population, schedules или navigation и не хранит `Player`.

Production использует один recurring pulse через существующий
`ThreadPool.scheduleAtFixedRate`. Отдельные `Thread`, `Future`, executor или
таймер на профиль запрещены. При старте зарегистрировано `0` профилей,
work sink — `no-op`; автоматического scan/registration/materialization нет.

## Activity state

Стабильный порядок детализации и коды:

| State | Code | Canonical materialization |
|---|---:|---|
| `ACTIVE` | 10 | required |
| `NEARBY_PERCEPTIBLE` | 20 | required |
| `WARM` | 30 | absent |
| `BACKGROUND` | 40 | absent |
| `SLEEPING` | 50 | absent |

`ACTIVE` и `NEARBY_PERCEPTIBLE` становятся effective только после успешного
ответа materialization port. До него snapshot сохраняет прежнее effective
состояние. Переход между `ACTIVE` и `NEARBY_PERCEPTIBLE` не вызывает повторный
lifecycle action.

## Relevance signal

`PhantomRelevanceSignal` — immutable value: `sourceKey`, монотонная `sequence`,
`requiredState`, `ttlMillis`.

- source: `^[a-z][a-z0-9_.-]{0,63}$`;
- sequence: `>= 0`, stale replacement/withdrawal отвергается;
- TTL: `1..86_400_000` ms;
- максимум 16 source keys на профиль;
- requested state — наивысшая детализация среди неистёкших signals.

Promotion выполняется без задержки. Demotion получает deterministic grace.
Истечение TTL и снятие signal проходят через ту же coalesced ready очередь.

## Bounded data structures

- registry: capacity `MaxScheduledPhantomProfiles`;
- ready: `ArrayBlockingQueue<Long>` той же capacity;
- due: один `DueEntry` в `TreeSet` на профиль;
- source map: максимум 16 entries на профиль;
- snapshots и metrics используют фиксированные категории без dynamic labels.

Signal принимается только после резервирования ready entry. При saturation
возвращается backpressure без мутации signal state. Повторные изменения уже
готового profile coalesce в одну ready entry. Один profile обрабатывается не
более одного раза за pulse; due ordering использует deadline и fairness
sequence.

## Lifecycle bridge

Materialization port возвращает `SUCCESS`, `TRANSIENT_BLOCK` или
`RETAINED_FAILURE`. Внешний вызов выполняется без scheduler global lock.

- clean/transient block повторяется с bounded exponential backoff;
- retained materialization/cleanup failure автоматически не повторяется;
- для retained failure требуется явный `retryTransition(profileId)`;
- это правило является `explicit retry`, а не автоматическим retry loop;
- effective materialized state не публикуется до `SUCCESS`;
- unregister materialized profile остаётся pending до успешного cleanup.

Production adapter переиспользует только принятый
`PhantomMaterializationService`: `materialize`, `dematerialize`,
`retryCleanup`, `find`.

## Work, fairness и overload

Typed `PhantomActivityWorkItem` содержит только технический profile/state/due
контекст. Он не содержит `Player` или domain plan. Исключение sink изолируется
на профиль.

Pulse ограничен `PhantomSchedulerProfilesPerPulse` и wall-clock budget.
Overload определяется bounded ready pressure. Он не меняет state и не удаляет
signals: cadence `ACTIVE`/`NEARBY_PERCEPTIBLE` остаётся неизменной, а только
`WARM`/`BACKGROUND` получают множитель `1/2/4/8`.

## Start/stop

Порядок production start:

```text
config validation
→ profile repository open
→ materialization service start
→ scheduler construct
→ one recurring pulse start
```

Disabled config возвращает управление до repository/service/scheduler и имеет
effective capacities/intervals `0`.

Порядок stop:

```text
scheduler begin-stop
→ reject input and cancel pulse
→ materialization service shutdown/retry
→ scheduler finish-stop only after service STOPPED
```

Если service drain не завершён, scheduler остаётся `STOPPING` и сохраняет slots
для явного повторного shutdown.
