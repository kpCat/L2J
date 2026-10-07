# Точные места текущего source и требуемая форма

Base fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6; line numbers получать rg по названным методам,
не перечитывать половину репозитория. Ниже CURRENT — реальные фрагменты с pinned base.

## PlayerNativeWork.java / capture и requireCapturedEarnedParticipants
```java
// CURRENT
if (kind.startsWith("attack-") || kind.startsWith("cast-"))
{
    captureCombatRecipients(actor, seen, participants);
    if (targets != null) { for (WorldObject target : targets) { captureCombatRecipients(target, seen, participants); } }
}
// CURRENT — даже current OPEN newcomer провоцирует исключение:
if (!participant.current() || current(participant.owner()) == null)
{
    final var failure = new IllegalStateException("NATIVE_EARNED_RECIPIENT_NOT_CAPTURED");
    recordFailure(failure);
    if (participant.owner() != null) { participant.owner().recordFailure(failure); }
    throw failure;
}
```
PROBLEM: общий snapshot участников будущих attack/cast перепутан с окончательным roster
конкретной native write-operation. Threshold damage<=1 исправляет только hate-only case.
REQUIRED SHAPE: explicit operation boundary + immutable participants + all reservations
before writers. Использовать existing ParticipantWork, не новую hierarchy сервисов.
Старый generic delayed validation не ослаблять. Positive newcomer OPEN и negative sealed
должны иметь разный результат, а cast не должен оставаться навечно true после safe denial.

## Attackable.java / calculateRewards
```java
// CURRENT
final long damage = info.getDamage();
if (damage > 1)
{
    if (calculateDistance3D(attacker) > PlayerConfig.ALT_PARTY_RANGE) { continue; }
    totalDamage += damage;
    final DamageDoneInfo reward = rewards.computeIfAbsent(attacker, DamageDoneInfo::new);
    reward.addDamage(damage);
}
// ... doItemDrop / EventDropManager, затем native addExpAndSp / Party.distributeXpAndSp
```
REQUIRED SHAPE: native eligibility/formulas unchanged. Если нужен boundary-hook — он
охватывает фактические native recipients до любого item/HP/reward writer. Нельзя сослаться
на этот threshold и затем позволить писать незарегистрированному реальному recipient.

## PhantomM1DynamicRecipientChecks.java / run
```java
// CURRENT — OLD EXPECTATION, общий для managed OPEN и SEALED
if (managed)
{
    // ... wait for firstNativeIncident ...
    PhantomAssertions.assertFalse(first.scope.open(), "Q12 failed original lifetime reopened.");
    PhantomAssertions.assertThrows(IllegalStateException.class, origin::storeMe,
        "Q12 dynamic failure became successful canonical store.");
}
```
Это не неприкосновенное требование «OPEN newcomer всегда ломает бой». TASK023 разрешает
исправить ожидание именно для законного enrolled OPEN. SEALED/stale/epoch negative, no
partial writers и no implicit reopen остаются. В EXPECTATION_CHANGES.md указать каждое
изменённое ожидание и новый позитивный/негативный парный тест; не просто уменьшить suite.

## MagicUseTask / finalizer
CURRENT: switch фаз вызывает onMagicLaunchedTimer, onMagicHitTimer, onMagicFinalizer.
REQUIRED: при допустимом отказе до write текущий native cast должен завершить состояние
через существующий native cancel/finalize только для exact task/player. Не затирать новый
cast старым callback; не вызывать phase3 как успешный hit после частичного writer exception.

## LocalPlaySyntheticHumanSession / готовый observer
```java
_lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(_objectId, OwnerKind.LOCALPLAY_TEST_HUMAN);
try (var suppression = PlayerAutoSaveTaskManager.suppressPopulationLoad(_objectId)) { _actor = Player.load(_objectId); }
_actor.setOnlineStatus(true, false);
_actor.spawnMe();
```
Это production-style locality actor; отдельный новый observer engine не нужен.
Использовать через Start-LocalPlaySynthetic.ps1 и ActorMode Synthetic.

## LocalPlaySyntheticHumanService / реальные лимиты
CURRENT: _started.size()>=5; _deadlineNanos=now+525_000_000_000L; heartbeat30s; sequence<=400.
Поэтому scene<=420s, каждое fresh UUID, heartbeat каждые10s, clean STOP. Новый JVM/новая
валидная сессия только между эпизодами, а не искусственная смена epoch бота для farm PASS.

## Новый narrow API (только если fresh admission отсутствует в существующих helpers)
```java
public enum NativeBoundaryOutcome { EXECUTED, DEFERRED_BEFORE_WRITE, REJECTED_STALE }
public static NativeBoundaryOutcome runAtNativeWriteBoundary(
    Creature actor, java.util.Collection<? extends WorldObject> frozenRecipients,
    String kind, Runnable nativeBody)
```
Разместить внутри PlayerNativeWork, а не нового global service. Строгие обычные run/schedule
не перенаправлять в него автоматически. На deferred/rejected nativeBody не вызывается;
real exception из запущенного body остаётся exception, а не переводится в DEFERRED.
Caller обязан обработать незапущенную подоперацию native cancel/defer без eternal CAST.
Тот же immutable native roster используется в body — не читать новый состав после допуска.

Важно: newly reserved admission ещё не означает заработанную награду. Не маркировать
каждую неуспешную попытку резервирования нового OPEN recipient как отказ уже опубликованной
EARNED работы. Новые pre-write reservations должны быть отменяемыми до linearization;
после успешного допуска всех участников native earned child scopes защищают writers и
публикуемые continuations. Existing captured earned reservations не отменять/не понижать.
Возможно использовать CANCELLABLE admission roots + EARNED children существующего scope;
каждый such child резервируется ДО соответствующего writer и публикуется один раз.
Если эта реализация требует переопределить b4 guarantee — не делать это незаметно: RED,
precise safety ledger, либо bounded BLOCKED_ARCHITECTURE при недостаточном доказательстве.
