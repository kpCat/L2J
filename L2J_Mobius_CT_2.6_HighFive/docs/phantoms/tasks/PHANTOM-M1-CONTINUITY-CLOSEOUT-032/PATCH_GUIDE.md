# PATCH GUIDE — проверено на df6ee99c71a4c8b07ad05545b200e113d3b0d3c9

## 1. HistoricalBackgroundService.java:1107–1115

// CURRENT
```java
public boolean visibleFarmReady(long profileId, PhantomGoal goal)
{
    final var failures = _visibleFailures.get(profileId);
    final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
    final var episode = _visibleEpisodes.get(profileId);
    if ((episode != null) && (episode.epoch == epoch) && (_visibleClock.getAsLong() < episode.cooldownUntil)) { return false; }
    if ((failures != null) && (failures.protocolBlocked(goal, epoch) || failures.routeBlocked(goal, epoch) || failures.exclusions(_visibleClock.getAsLong()).targets().contains(targetKey(goal)))) { return false; }
    return Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), goal);
}
```

// PROBLEM
Шесть результатов представлены одним boolean; runtime43 не установил первый FALSE.

// REQUIRED SHAPE
`proposals/VisibleFarmReadiness.java-snippet` разделяет результаты, сохраняя порядок
вычислений, calls и exception behavior. Это diagnostic shape, НЕ разрешение на action.
Встроить в существующую диагностику либо снять JDI именно на возвращающей ветке.
Не вызывать evaluator вторично после stop: состояние уже могло измениться.

## 2. BackgroundDecision.java:241–245,256–260

// CURRENT
```java
if (!_visibleSuitable.apply(context.profileId(), context.goal()))
{
    _visibleStop.accept(context.profileId());
    return PhantomStepResult.of(Type.REPLAN, "background.visible.replan_required");
}
```

// PROBLEM
Это consumer отказа, не обязательно producer ошибки. Удаление stop даёт unsafe action.

// REQUIRED SHAPE
После установления первого reason связать его с pre-work recovery. Сохранить handler
read-only suitability и action cancellation. Если добавлен typed result, оставить
совместимый boolean wrapper старым callers, единый evaluator и targeted parity tests.
Не допускать «false исключаем из статистики — тогда PASS».

## 3. HistoricalBackgroundService.java:1118–1270

// CURRENT — реальные места, не новая архитектура
- pending `_visiblePublications` → `finishVisiblePublication`;
- `runtimeMatches` / `decision.reload` до handler;
- `protocolBlocked`, `episode.cooldownUntil`, authority renewal;
- `replanVisibleLocal`, `currentClaim`, `_store.replacePlan`, exact rebind.

// REQUIRED SHAPE
Снимать firstReason именно до stop/replan; для35s позднего снимка нельзя утверждать
первоначальную причину. RED должен пройти цепочку producer→prepare→handler→exit,
а не только руками положить значение в map и получить true.

## 4. HistoricalBackgroundService.java:~1500–1600 — тонкая граница

// CURRENT
```java
void unavailable(long now) {
    reason = "LOCAL_FARM_UNAVAILABLE";
    cooldownUntil = Math.max(now + 10_000_000_000L, started + 60_000_000_000L);
}
```
`VisibleFailures` содержит TTL120s для target/step exclusions, но `routeBlocked` и
`protocolBlocked` сравнивают goal/revision/epoch без TTL.

// REQUIRED SHAPE
Не удалять безопасность по таймеру. Проверить, что successful new-plan publication
действительно освобождает ТОЛЬКО старое применимое ограничение. Protocol violation
не превращается в transient только ради восстановления liveness.

## 5. VisibleAutoPlay.java:resourcePause / observeProgress

C26 уже исправил reentry: реально sitting affordable actor попадает в stock standUp.
Не откатывать. Для117 выписать timeline до первого debt, включая native lastUseful,
resourceSince, recovering, phase deadline, current goal, stop caller и next decision.
Не приписывать ему orphan rest по сходству с прежним238. Ниже45/90/120 limits read-only.

## 6. Accept031.ps1:ReadArrivalFrame031,WaitArrival031,Walk031

// CURRENT (конечный commit)
```powershell
$frame=ReadArrivalFrame031
$dx=[double]$point.x-[double]$frame.observer.x
$dy=[double]$point.y-[double]$frame.observer.y
```
Оба movement места уже используют arrival stream, не старый1Hz кадр.

// REQUIRED SHAPE
Проверить runtime, не повторить тот же патч. Для CURRENT_MOVE snapshot проверять
run/incarnation/identity и freshness; не смешивать stale target path с новым actor origin.
Pre-publication Synthetic setup032 — адаптация существующего C13, не teleport после
появления у Giran с занятием7слотов. Endpoint dry + outside-demand подтверждаются
до выполнения, фактическое arrival — после каждого принятого MOVE.
