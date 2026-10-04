# Patch guide — current source и требуемая форма

CURRENT ниже прочитан в d924a7d2… (source совпадает с соответствующими baseline blobs006).
REQUIRED SHAPE — проектируемый интерфейс, не существующий API и не проверенный патч.
Точные ссылки/полнота чтения в SOURCE_MAP и READ_FIRST_AUDIT. Сохранять соседнюю native
семантику, layout и кодировку; не заменять большие методы целиком без необходимости.

## 1. EventDispatcher.java: notifyEventAsyncDelayed

```java
// CURRENT
ThreadPool.schedule(() -> notifyEvent(event, container, null), delay);

// PROBLEM
// Ownership не резервируется до scheduling. Cleanup не знает queued/running listener.
// ScheduledFuture отсутствует у Player; cancel Player tasks не охватывает event.

// REQUIRED SHAPE (новые helpers)
// 1) Вычислить typed participants / inherited exact owner.
// 2) Зарезервировать work ticket до submit.
// 3) Submit wrapper через native strict submission seam.
// 4) Wrapper enter → тот же notifyEvent → finally completion.
// 5) Rejection/throw → unsuccessful outcome, release accounting, не successful handoff.
```

Тот же аудит для single/varargs async. Не переписывать local/global listener order.
Event runtime getAttacker().objectId не должен разрешать rebind к новому Player.

## 2. ThreadPool.java: ложная уверенность в scheduling

```java
// CURRENT: schedule(...)
try
{
    return SCHEDULED_POOL.schedule(new RunnableWrapper(runnable), validateDelay(delay), TimeUnit.MILLISECONDS);
}
catch (Exception e)
{
    // native log
    return null;
}

// CURRENT: RejectedExecutionHandlerImpl.rejectedExecution(...)
if (executor.isShutdown())
{
    return;
}

// PROBLEM
// try/catch вокруг callsite не увидит часть отказов. Future != null не полный ack.

// REQUIRED SHAPE
// Additive strict scheduleOrThrow/executeOrThrow для owned work.
// Shutdown rejection становится observable; legacy wrappers по-прежнему обрабатывают
// ошибку своим прежним contract. Native accepted tasks остаются в native executor.
```

Не переводить весь сервер на новый pool. Проверить rejection concurrent shutdown,
accepted-before-shutdownNow, callback-before-submit-return. Нет retry заработанного
callback после uncertain submit: можно выдать предмет дважды.

## 3. QuestTimer / TimerHolder

```java
// CURRENT: QuestTimer.ScheduleTimerTask.run
if (_scheduler == null) { return; }
if (!_isRepeating) { cancel(); }
_quest.notifyEvent(_name, _npc, _player);

// CURRENT: TimerHolder.run
_postExecutor.onTimerPostExecute(this);
_eventScript.onTimerEvent(this);

// PROBLEM
// Из списка producer уже исчез, тело ещё выполняется. Future.cancel(false) не drain.

// REQUIRED SHAPE
// Atomic registration/start/cancel state; execution ticket охватывает всё тело.
// Для ambient timer pending registration cancellable, running body дожидается finally.
// Earned inherited one-shot использует reservation от момента scheduling.
```

Не вызывать blocking drain под Player._questTimers/_timerHolders locks и не менять
native distinction cancelTask vs cancelTimer/cancel callback.

## 4. Candidate006 Player.runPhantomNativeAction

```java
// CURRENT CANDIDATE (не production HEAD)
if (!_phantomNativeActionManaged || Boolean.TRUE.equals(_phantomNativeActionActive.get()))
{
    action.run();
    return;
}
final var admission = _phantomNativeActionAdmission;
if (admission == null) { return; }
final var lease = admission.get();
if (lease == null) { return; }

// PROBLEM
// Entry-only lease не видит queued callback. return может отбросить уже законное
// completion, а Boolean не несёт exact owner/parent/scope.

// REQUIRED SHAPE
// New root до side effect либо inherited exact ticket для ранее scheduled completion.
// No implicit late lookup of current materialization by objectId.
```

Не заменить все return на acquire-while-closed: это откроет произвольные новые actions.
Разделить root и continuation; общий counter/seal в lifecycle scope.

## 5. PhantomBackgroundService.prepareStore

```java
// CURRENT (последовательность фрагментов метода)
player.getInventory().updateDatabase();
// ... authority capture ...
final var witnessed = captureOwnedInventory(player, captured);
final var prepared = transaction(() -> _transactions.prepareOwnedStore(witnessed, goal, entry.materializedAtNanos(), target));

// PROBLEM
// Drain только перед delete слишком поздний. Нужен seal до inventory flush/capture.

// REQUIRED SHAPE
// requireExactSealedCheckpoint(owner, Player, epoch), полученный вне counted caller;
// затем прежний protocol, без изменения intent payload для скрытия concurrent writer.
```

CURRENT afterStore сохраняет post-FINALIZE runtime/inventory comparison; не удалять.
`PhantomVisibleFarmTravel.arrive` вызывает resumeVisibleOwnedStore внутри ActionLease:
перенести ожидание в owner control continuation, не вызвать self-drain.

## 6. PhantomVisibleAutoPlay.configure и noTargetExpired

```java
// CURRENT
player.getAutoPlaySettings().setPickup(true);

// CURRENT: noTargetExpired
final boolean offensive = (player.isMoving() || player.isAttackingNow() || player.isCastingNow())
    && (selected instanceof Creature creature) && selectableTarget(player, creature, npcId);
// target = offensive || anyMatch(selectableTarget...)
// if target -> session.noTargetSince = -1

// PROBLEM
// Target есть, но native действия могли не давать никакого полезного эффекта.

// REQUIRED SHAPE
// Сохранить no-target detection, добавить independent useful-progress debt.
// lastNativeDamage/reward + path progress + bounded recovery, не флаги.
```

В stock AutoPlay Pickup уже вызывает doPickupItem; проверять именно эту ветку,
ownership/protection/reachability/capacity и item persistence. Новый loot engine не нужен.

## 7. PhantomVisibleFarmTravel.arrive

```java
// CURRENT
if ((_clock.getAsLong() - journey.startedNanos) >= _navigation.policy().maximumAttemptDurationNanos())
{
    fail(profileId, journey, "travel.journey_deadline");
    return false;
}

// PROBLEM
// Journey удаляется на terminal. Новый Journey может стартовать с новым startedNanos
// в том же goal/revision. Boolean routeFailure не покрывает все terminal producers.

// REQUIRED SHAPE
// Failure carries disposition + exact goal/revision/lifetime + original budget.
// Same attempt ledger survives Journey recreation; producer-specific terminal feeds
// actual production resolution/replan and downstream native farm.
```

Не дублировать PhantomSystem callback лямбдой внутри TEST: использовать тот же production
factory/composition. Не путать COMMITTED background coordinate с LIVE Player position
или APPROACH coordinate observer; previous «удалить архитектурные слои» не применять.

## 8. Run-M1RuntimeHandoff.ps1

CURRENT: OBSERVE 40000 ms; initial calendar check nextBoundary > Now+240 s; snapshot
flags присваивают selectedNativeFarm; final grade допускает чужой ordinaryFarm; SOFT_RETURN
проверяет SAME_PLAYER_NO_CHURN после короткого отсутствия 15 s.

REQUIRED SHAPE: OBSERVE до 180 s с truthful early-success condition после полных cycles;
общий existing action budget 480 s и cleanup45 s не игнорировать. Initial calendar horizon
вычислять по оставшемуся worst-case scene budget+cleanup margin, а не оставить 240 s
после удлинения OBSERVE. Payload/mailbox bounds пересчитать и проверить offline.

SOFT_RETURN сохранить как короткое отсутствие с тем же Player/epoch. Длинный уход,
background dematerialization и subsequent reentry — ОТДЕЛЬНЫЙ W06 native scenario,
не требовать dematerialization в 15-секундном soft-return window.

Для второй natural scene добавить optional excludePreviouslySelectedProfileIds в
INITIAL selection contract/runner, если existing selector этого не умеет. Разрешённое
исключение — только actor предыдущей завершённой scene, записанный в ledger, не_FAILED
или «неудобные» профили. Не менять eligibility/состояние profiles. Новый option проверить
через Pilot allowlist и offline parser; не слать несуществующий параметр старому runner.
