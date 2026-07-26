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

### Ownership перехода

Slot сохраняет bounded-маркеры `processing`, `boundaryInFlight` и
`boundaryGeneration`. Перед выходом из monitor к lifecycle port scheduler
фиксирует boundary ownership, а после любого результата снимает его под тем же
monitor. Физическое удаление slot запрещено, пока активен `processing` или
`boundaryInFlight`.

`unregister` во время обработки или lifecycle boundary переводит slot в
`UNREGISTER_PENDING`, очищает живые signal values, запрашивает `SLEEPING` и
резервирует одну coalesced следующую возможность. Поздний успешный materialize
сначала получает canonical cleanup; slot удаляется только после
non-materialized terminal state без retained ownership.

Retained failure проверяется раньше равенства requested/effective, grace и
обычного transition planning. Signal update, withdrawal и TTL expiry могут
менять requested state, но не могут снять retained ownership или требование
explicit retry.

Успешный `retryCleanup` всегда означает отсутствие lifecycle ownership. Для
requested `WARM`, `BACKGROUND` или `SLEEPING` effective state становится этим
правдивым non-materialized состоянием. Для requested `ACTIVE` или
`NEARBY_PERCEPTIBLE` effective state сначала становится `SLEEPING`, после чего
планируется отдельный fresh materialize. Cleanup success не публикует
materialized state напрямую.

Production adapter классифицирует retained по фактическому наличию service
entry через узкий `hasLifecycleOwnership(profileId)`, а не только по имени
`ResultStatus`. Cleanup считается успешным только при отсутствии service
ownership.

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

Scheduler-wide marker `pulseInFlight` допускает не более одного pulse. После
`beginStop` новые lifecycle boundary и work-sink вызовы не начинаются.
Уже начатый внешний вызов может завершиться и быть согласован, но `finishStop`
возвращает `false` и ничего не очищает, пока активен `pulseInFlight`,
`processing` или `boundaryInFlight`. После quiescence отдельный явный
`finishStop` очищает ready/due/slots. `PhantomSystem` переходит в `STOPPED`
только если `finishStop` вернул `true`; иначе сохраняет configured instance в
`FAILED` для следующего явного shutdown.
