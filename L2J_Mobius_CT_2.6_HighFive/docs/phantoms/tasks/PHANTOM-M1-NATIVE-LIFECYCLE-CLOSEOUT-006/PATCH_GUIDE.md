# PATCH GUIDE — source baseline 461a4abe

Фрагменты CURRENT ниже — проверенные выдержки, не полные методы. Anchors и SHA —
SOURCE_MAP.tsv. REQUIRED SHAPE/proposals — проектируемое изменение, требует actual RED.
Нельзя применить пример текстовой заменой ко всему репозиторию без проверки local diff.

## 1. AutoPlay / AutoUse dispatch

```java
// CURRENT (AutoPlayTaskManager.AutoPlay.run; тот же null-policy pattern в AutoUse)
final PhantomPolicy phantomPolicy = PHANTOM_POLICIES.get(player);
final TickLease lease = phantomPolicy == null ? () -> {} : phantomPolicy.acquire(player);
if (lease == null)
{
    stopAutoPlay(player);
    continue PLAY;
}
```

PROBLEM: null policy — не доказательство «это обычный игрок»; P1 rejection — не разрешение
удалить P2. Snapshot policy read должен соответствовать тому, что worker реально выполняет.

```java
// REQUIRED SHAPE; до ordinary online/config-stop ветвей.
final PhantomPolicy observedPolicy = PHANTOM_POLICIES.get(player);
if (player.isPhantomAutoPlayManaged() && (observedPolicy == null))
{
    continue PLAY; // Не stop(player): replacement мог уже зарегистрироваться.
}
final TickLease lease = observedPolicy == null ? NOOP_LEASE : observedPolicy.acquire(player);
if (lease == null)
{
    stopPhantomAutoPlay(player, observedPolicy);
    continue PLAY;
}
```

Аналогично AutoUse (его loop без PLAY label). Все другие ветви stop/catch в этих loops
также должны знать observedPolicy: не исправлять только этот один if.
NOOP_LEASE допускается только stock actor. Не ловить null policy через NPE.

## 2. Atomic conditional removal

```java
// CURRENT (AutoPlayTaskManager.stopAutoPlay)
final boolean phantom = PHANTOM_POLICIES.remove(player) != null;
for (Set<Player> pool : POOLS)
{
    if (pool.remove(player))
    {
        player.setAutoPlaying(false);
        // existing summon/IDLE_COUNT cleanup follows
    }
}
```

PROBLEM: наблюдатель P1 может удалить текущий P2. Отдельный CAS policies.remove без lock
не защищает последующее pool.remove. И pre-remove перед existing stop теряет phantom flag.

Точные method shapes в `proposals/conditional-registration.java-snippet`.
Сохранить native cleanup и stock semantics; синхронизировать unconditional mutation с start.
В catch вызвать conditional P1 cleanup каждого manager последовательно, не под nested locks.

## 3. Session liveness

```java
// CURRENT (PhantomVisibleAutoPlay.start)
if ((previous != null) && (previous.player() == player)
    && (previous.goalId() == goal.goalId()) && (previous.revision() == goal.revision())
    && player.isAutoPlaying())
{
    return true;
}
```

```java
// CURRENT (PhantomVisibleAutoPlay.running)
return (session != null) && (session.goalId() == goal.goalId())
    && (session.revision() == goal.revision()) && session.player().isAutoPlaying()
    && current(profileId, goal);
```

REQUIRED SHAPE: оба fast-path проверяют exact registrations через Session.policy().
Session publication/replacement в DESIGN D3. Удалять `_sessions.remove(id, expected)`.
Откатывать только expected policy pair. Не ограничиться добавлением autoUse=true поля:
поле не доказывает фактическое присутствие в native pool.

## 4. Exception masking

```java
// CURRENT pattern (Player native owned store/resume boundary)
try { storeNative(false, snapshot); completed = true; }
finally { boundary.afterStore(completed); }
```

PhantomMaterializedPlayer.cleanup оборачивает beforeStore → storeMe → afterStore в
`finally { player.stopAllTasks(); }`. Это два независимых места masking.
REQUIRED SHAPE в `proposals/preserve-primary.java-snippet`.
Сохранить порядок/условия finalizers, completed=false при native throw, true послеуспеха.
First failure record фиксируется до retry overwrite; source record cause не теряется.

## 5. Post-FINALIZE mismatch

CURRENT control-flow: FINALIZE success → finalizedSuccessfully=true →
ownedProgressMatches/ownedInventoryMatches → possible OWNED_STORE_RUNTIME_CHANGED_AFTER_FINALIZE;
finally очищает intent при finalizedSuccessfully.
Это **описание порядка**, не дословная цитата всего метода.

REQUIRED: добавить precise before/after identity/progress/inventory-digest evidence;
в Q tests установить exact writer. НЕ менять guard/clear-semantics наугад. Если runtime
подтверждённо меняется, исправить producer/quiescence, не подавлять afterStore exception.

## 6. Travel terminal classification

```java
// CURRENT (PhantomVisibleFarmTravel.Failure.routeFailure)
return reason.equals("travel.route_absent") || reason.equals("travel.destination_unproven")
    || reason.equals("travel.gatekeeper_unproven") || reason.equals("travel.navigation_no_path")
    || reason.equals("travel.navigation_route_obstructed")
    || reason.equals("travel.navigation_route_budget_exceeded")
    || reason.equals("travel.native_progress_stuck")
    || reason.equals("travel.native_progress_timeout");
```

PROBLEM: production callback основан на bool, а terminal reasons шире.
REQUIRED: typed outcome у всех production producers + bounded resolution exact goal/revision.
Рекомендованный mapping в `proposals/travel-terminal-matrix.tsv`. Это не команда навсегда
исключить маршрут на любом таймауте. Тестировать actual PhantomSystem callback wiring.

## 7. Acceptance

```powershell
# CURRENT (Run-M1RuntimeHandoff.ps1)
$nativeAction = $script:nativeDisplacement -or $script:selectedNativeFarm
$matrix.NATIVE_LIFE = if ($nativeAction -and ($script:selectedNativeFarm -or ($ordinaryFarm.Count -gt 0))) { 'PASS' } else { 'NOT_OBSERVED' }
$matrix.COHORT = if ($script:censusComplete -and ($eligibleProfiles -ge 4) -and ($failedIdle.Count -eq 0)) { 'PASS' } else { 'INSUFFICIENT_OR_FAILED' }
```

REQUIRED: evaluate selected native proof отдельно и immutable cohort denominator.
Pure grading helper с fixtures/negativecontrols; observer только passive.
FIELDS/proposed predicates в `proposals/acceptance-contract.md`. Точность датчика важнее
названия поля: растущий чужой aggregate damage — не attribution нашему Phantom.
