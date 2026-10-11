# PATCH_GUIDE036 — актуальные места и допустимая форма

## 1. Наблюдение scheduled cast — сначала этот дешёвый пробел
File: `java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java`
Методы WorkTicket constructor, dispatchStage/executorEntered/bodyExited/finish,
publishWork/diagnosticScalars. Base range примерно445–620.

```java
// CURRENT (из base)
_traceDispatch = kind.startsWith("EVENT:") && org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled();
// PROBLEM: cast-launch исключён из подробного dispatch trace.
// REQUIRED SHAPE: сохранить EVENT и включить только известную cast-цепочку,
// в том же diagnostic switch; семантика tickets не меняется.
_traceDispatch = (kind.startsWith("EVENT:") || isCastContinuationKind(kind))
    && org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled();
```
В `proposals/CastDispatchFilter.java-snippet` — точный фильтр. Перед интеграцией добавить RED,
который подтверждает отсутствие cast dispatch fields при diagnostics=true на base.
Проверить завершение до bind Future: запоздалый SUBMITTED не может воскресить finished reference.
Скалярное archive/captured count не означает данные по всем событиям при кольце8: указать границы.

## 2. Общий publisher уже умеет эти этапы
File: `model/actor/PlayerNativeWork.java`, примерно376–394:
```java
// CURRENT
work.dispatchStage("SUBMITTING", delay, null);
final var future = ThreadPool.scheduleOrThrow(() -> work.run(action), delay);
work.dispatchStage("SUBMITTED", delay, future);
return new ParticipantFuture(future, work);
```
Не строить второй scheduler. Путь Creature использует overload с actor+targets, а не
single-Player overload внизу файла. Менять overload наугад по совпавшему имени запрещено.
При подтверждённом lifecycle defect минимальная правка должна покрыть reserve/start/complete
и все участники одной операции, с original Throwable и exactly-once retirement.

```java
// CURRENT: ParticipantFuture
@Override public boolean cancel(boolean interrupt) {
    return work.cancel() && future.cancel(interrupt);
}
// CURRENT: ParticipantWork.cancel
if (_state == State.RUNNING) { return true; }
if ((_state != State.RESERVED) || (_semantics == Semantics.EARNED)) { return false; }
```
Это защитная семантика, не заранее установленная ошибка.

## 3. Native origin
File: `Creature.java`, beginCast около2160–2210:
```java
// CURRENT
_skillCast = PlayerNativeWork.schedule(this, mut.getTargets(), "cast-launch",
    PlayerNativeWork.Semantics.EARNED, mut, Math.max(0, skillTime - 400));
```
Остальная цепочка: onMagicLaunchedTimer→cast-hit400ms,
onMagicHitTimer→cast-finalizer0ms. До delayed publish уже могли измениться initial MP,
items/cooldown; «оно ещё RESERVED» НЕ даёт права вычеркнуть его из accounting.
MagicUseTask.run содержит current-task guard. Stock no-op и wrapper retirement надо проверять отдельно.
Никаких новых проверок «эта цель занята другим Phantom» в этой задаче.

## 4. Cleanup после реального завершения
File: `PhantomMaterializationService.java`,421–630:
```java
// CURRENT
if (entry._controlCleanupResult != null) { return entry._controlCleanupResult; }
// ...
if (!entry._materializedPlayer.prepareCleanupDrain()) {
    return new DematerializeResult(ResultStatus.CLEANUP_PENDING, snapshot(entry));
}
```
Есть retry-on-quiescent и exact-entry checks. Если trace доказал stale result,
тестировать одну completed control attempt, не добавлять бессрочный цикл retry.
Снятие entry/permit допускается только после STORED и законченного materialization completion.

## 5. Global stop
`PhantomSystem.shutdownClaimed` single10s monotonic deadline; `Shutdown.startShutdownActions`
вызывает Phantom drain дважды, затем останавливает shared pools даже после FAILED.
Это объясняет возможную границу, но не доказательство исторической причины27.
Разрешён лишь подтверждённый ordering/failure-isolation fix: ждать принятые действия в пределах
ИСХОДНОГО оставшегося срока с живыми зависимостями; не перезапускать весь deadline и не менять
общесерверную ThreadPool.shutdown implementation. Если безопасный контракт не помещается сюда — BLOCKED_ARCHITECTURE.

## 6. Контроль classpath
Исполняемый GameServer.jar должен идти раньше любых старых build/bin production classes.
Reuse035 runner, расширенный исключительно на own036t. Если после последнего теста изменена
production-семантика — её предыдущие runtime PASS более не относятся к финальному кандидату.
