# Decision / Goal / Plan contract

## Статус и граница

Контракт введён Goal 008. Он определяет только domain-neutral goal, Utility AI,
typed plan и bounded executor. Пакет `phantoms/decision` не знает `Player`,
`GameClient`, packets, combat, navigation, Game Knowledge, population, LLM или
конкретные игровые capability/action keys.

Production Goal 008 запускает decision engine с нулём attached profiles и двумя
пустыми sealed registries. Scheduler profiles, goals и materialization
автоматически не создаются.

## Immutable domain model

`PhantomDomainRef` содержит namespace
`^[a-z][a-z0-9_.-]{0,31}$` и visible-ASCII key длиной `1..128`. Reference не
выполняет lookup и не содержит catalog.

Capability key использует bounded-key syntax и rank `1..1000`.
`PhantomCapabilitySet` — ordinal-sorted immutable map максимум из 128 entries.
Candidate принимает максимум 16 requirements.

`PhantomGoal` имеет schema version 1 и одну immutable revision. Goal ID
положителен; status — `ACTIVE`, `COMPLETED`, `ABANDONED` или `FAILED`;
amounts/budgets/deadline/priority валидируются; sources и constraints
канонизируются и ограничены 16 entries. На profile существует не более одного
current goal. Replacement допускается только с revision строго больше текущей.

## Persistence boundary

Используется существующий component envelope:

```text
component_type = goal.runtime
component_schema_version = 1
payload <= 4096 bytes
```

`PhantomGoalStateCodec` — deterministic binary с magic, format version и goal
schema version. До allocation проверяются string lengths и collection counts.
Decoder отклоняет truncation, trailing bytes, неизвестные versions/status и
нарушения immutable model.

`PhantomGoalStateStore` переиспользует `PhantomProfileRepository`:

- explicit attach/reload — единственные read paths;
- insert/replace/delete используют component API;
- replace/delete требуют expected component row version;
- ordinary scheduler tick не читает БД;
- persistence conflict не retry-ится автоматически и переводит runtime в
  `PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD`.

Persisted state содержит только goal. Plan, handler, token, candidate
explanations и executor progress не сериализуются. После restart ACTIVE goal
получает `NEEDS_REPLAN` без plan.

## Sealed registries и Utility AI

Candidate и step-handler registries:

- максимум 256 unique bounded keys;
- registration разрешена только до `seal()`;
- после seal публикуется immutable ordinal snapshot;
- production snapshot Goal 008 пуст.

Candidate ограничивает goal types, activity states, requirements,
considerations и threshold. Каждая consideration имеет weight `1..1000` и
возвращает score `0..1000` с bounded reason key.

Score вычисляется overflow-safe integer arithmetic:

```text
floor(sum(score_i * weight_i) / sum(weight_i))
```

Capability requirements проверяются до considerations. Exception, null или
invalid score блокирует только текущий candidate. Winner определяется highest
score, затем ASCII candidate key ascending. Hash/insertion/random order не
используется. Runtime хранит максимум восемь candidate evaluations,
отсортированных score-desc/key-asc.

## Typed plan

Plan immutable и содержит:

- positive plan ID и exact goal ID;
- selected candidate key;
- `1..32` contiguous steps;
- total logical timeout `1..86_400_000 ms`;
- logical creation time.

Step содержит bounded action key, optional `DomainRef`, максимум 16
ordinal-sorted numeric arguments, timeout `1..3_600_000 ms`, attempts `1..10` и
reason key. Plan не хранит callback/Runnable.

Handler получает immutable profile/goal/plan/step/activity/logical-time context
и cooperative cancellation token. Результат typed:
`SUCCESS`, `RETRY`, `REPLAN`, `COMPLETE_GOAL`, `FAIL_GOAL`, `CANCELLED`.

## Executor и generations

На attached profile допускается один plan и один handler in flight. Один
`PhantomActivityWorkItem` вызывает не более одного handler. Planning и handler
не создают thread/future/executor; handler выполняется вне global engine lock.

Generation меняется при goal replacement, detach, activity/dispatch generation
change и stop. Она проверяется до и после handler. Stale result отбрасывается,
а token позволяет handler увидеть cancellation без interruption.

Timeout/retry используют scheduler logical monotonic time. RETRY имеет bounded
delay и maximum attempts. Exhaustion/timeout/REPLAN удаляют plan и требуют
нового решения на следующем work item. Terminal handler result сначала
optimistic-persist-ит terminal goal status.

`PhantomActivityWorkItem.activityGeneration` меняется только при effective
activity или lifecycle ownership change. Обычная замена signal с тем же
effective/lifecycle truth не отменяет plan.

## Lifecycle integration

Startup production:

```text
repository open
→ materialization service start
→ empty candidate/handler registries seal
→ goal store + decision engine start
→ scheduler(decision engine work sink) start
```

Shutdown:

```text
scheduler.beginStop
→ decisionEngine.beginStop
→ materialization service drain
→ scheduler.finishStop after pulse quiescence
→ decisionEngine.finishStop after handler quiescence
```

Незавершённый scheduler/engine сохраняет `PhantomSystem` в `FAILED` для
следующего explicit shutdown.

## Fixed metrics и snapshots

Метрики агрегированы и не используют dynamic candidate/goal labels:
attached current/peak; mutation/reload rejects; decisions/no-goal/no-candidate;
candidate evaluated/blocked/failed; plan outcomes; step outcomes; conflicts;
stale results и stop failures.

Runtime snapshot bounded: goal identity/revision/status, runtime state,
decision sequence, selected candidate/score, plan/step/attempt, last result,
reason key, top-eight evaluations, in-flight marker, generations и component
row version. История, exception/stack trace и chat text не сохраняются.
