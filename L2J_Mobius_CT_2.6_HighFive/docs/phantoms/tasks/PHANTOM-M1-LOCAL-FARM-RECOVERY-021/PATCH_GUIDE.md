# Точные места и форма правок

Base всех CURRENT: 2bf2936083bf6b081104c3e03c254ab8f07afc87.
Это выдержки из опубликованного source, а REQUIRED — предлагаемые изменения, не готовый
проверенный patch. Полные классы повторно в контекст не выводить.

## 1. PhantomVisibleFarmTravel.advance / attempt

```java
// CURRENT
final Attempt attempt = attempt(profileId, goal, player, epoch);
if ((attempt == null) || attempt.terminal) { return false; }
```

```java
// PROBLEM
// false объединяет PENDING, устаревший goal и окончательно запрещённый маршрут.
// REQUIRED SHAPE (аддитивный typed result, old boolean API допустимо сохранить)
enum ArrivalKind { ARRIVED, PENDING, STALE_GOAL, TERMINAL }
// В result включить binding goalId/revision/objectId/epoch/failureSequence и reason.
// Терминальный receipt не пересоздаёт Attempt; повторное чтение не имеет side effects.
```

Непосредственно до navigation submit и MOVE_TO сравнить переданный goal с фактическим
`_background.ordinaryGoal(profileId)`; загружать один раз для каждого проверяемого этапа,
не из-под `_attempts` lock. При смене goal между submit/consume старый route не исполнять.

## 2. PhantomBackgroundDecision.bindVisibleLife / startVisible

```java
// CURRENT
if (!travel.arrive(profileId, goal))
{
    autoPlay.stop(profileId);
    return false;
}
return autoPlay.start(profileId, goal);
```

```java
// REQUIRED SHAPE
// PENDING -> existing bounded RETRY
// STALE_GOAL -> REPLAN (pre-work sync before next execution)
// TERMINAL -> enqueue exact idempotent local recovery, REPLAN with original reason
// ARRIVED -> existing autoPlay.start(profileId, goal)
```

Сохранить legacy constructors для existing tests/callers; добавить typed internal adapter
без размножения общего handler framework. Родной runtime terminal не прятать в false.

## 3. History.recordVisibleTravelFailure

```java
// CURRENT
if ((action == null) || (action.player() != failure.player())
    || !Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), failure.goal()))
{
    return false;
}
```

Эту защиту НЕ ослаблять. Она правильно отклоняет чужую revision. Исправить caller/runtime
coherence и явно различать STALE_FAILURE_IGNORED и CURRENT_TERMINAL_ACCEPTED. Старую
ошибку версии28 нельзя выдавать за подтверждённый water failure версии29.

## 4. History.replanVisibleFarmIfOutgrown

```java
// CURRENT
if ((catchup == null) || (catchup.state().status() != Status.COMPLETE)
    || (catchup.state().goalId() != goal.goalId()))
{
    return !failedTarget;
}
```

Этот COMPLETE-only путь не является recovery для pending active native handoff.
Добавить отдельный foreground-authorized path по DESIGN, не делать общий bypass.

```java
// CURRENT: две независимые publication boundaries
if (!replacement.ready()
    || (decision.setGoal(profileId, replacement.goal()) != PhantomDecisionEngine.MutationResult.APPLIED))
{
    return false;
}
// ... позже _store.replace(profileId, catchup, updated);
// catch RuntimeException: next renewal resolves stale plan revision
```

```java
// REQUIRED SHAPE: existing atomic store, потом runtime projection
PlannedSnapshot committed = _store.replacePlan(
    profileId, expectedCatchup, planFieldsOnly, expectedStoredGoal, replacement.goal());
// Exact expected->committed foreground permit rebind (если был native handoff).
// decision.reload вне in-flight handler; проверка exact committed row/revision.
```

## 5. Planner

CURRENT: `replan(...) -> plan(...)`, сортировка сначала по разнице уровней, затем числу
routeEdgeIds; candidate проверяет topology/GK route, а не безопасность каждого native leg.
REQUIRED: отдельный visible-local overload, не менять background plan по умолчанию.
Фильтры radius/instance/no-global-leg применить до фиксации tier/candidate.
Геометрию оставлять native navigation + unsafeSegment, не писать новый pathfinder.

## 6. Water guard

```java
// CURRENT — СОХРАНИТЬ
if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null)
{
    if (dry) { return "travel.native_segment_water_entry"; }
}
else { dry = true; }
```

Разрешён небольшой diagnostic witness: from/waypoint/to, первая отвергнутая cell x/y/z,
waterExit, binding current goal/epoch. Не менять WaterZone, GeoEngine и height tolerance.
Не утверждать ошибку геодаты без отдельного доказательства реальной поверхности.

## 7. Опасность существующего withPlan

```java
// CURRENT (PhantomBackgroundCatchupState.withPlan)
status == Status.FAILED_REPLAN_REQUIRED ? Status.RUNNING : status
// ... failureReason становится ""
```

Для foreground-only intent replacement status/failureReason сохранять. Это не historical
recovery и не award interval. Негативный тест обязателен.
