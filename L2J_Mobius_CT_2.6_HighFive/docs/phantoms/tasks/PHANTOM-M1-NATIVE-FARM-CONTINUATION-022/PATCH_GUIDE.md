# Source-grounded patch guide

Выдержки проверены на base0205d04b. Это locator/contract, не готовый patch. Не повторять полный аудит репозитория.

## A. PhantomVisibleAutoPlay.java: healthy()/running(), Policy.acquire()

```java
// CURRENT (healthy содержит также обе hasPhantomRegistration-проверки)
return (session != null) && session._current.get()
    && (_sessions.get(profileId) == session)
    && (session.player() == player)
    && (session.goalId() == goal.goalId())
    && (session.revision() == goal.revision())
    && player.isAutoPlaying() /* ... */;
```

PROBLEM: evidence lastDecisionReason устарел. Не добавлять ещё один isAutoPlaying как «fix» — он уже здесь. Найти переставший возвращаться/вызываться path, сопоставить fresh registration snapshot и возраст решения.

```java
// CURRENT Policy.acquire(), часть
final ActionLease action = _materialization.tryAcquireAction(_profileId).orElse(null);
if (action == null) { return null; }
```

REQUIRED: обнаружить конкретную причину null и вернуть различимый временный pause/окончательный revoke, если RED подтверждает именно эту проблему. Сохранить owner/session/goal equality; stale policy не удаляет successor.

## B. AutoPlayTaskManager / AutoUseTaskManager: run() и Phantom stop

```java
// CURRENT AutoPlay; AutoUse делает аналогично
final TickLease lease = phantomPolicy == null ? () -> {} : phantomPolicy.acquire(player);
if (lease == null)
{
    stopPhantomAutoPlay(player, phantomPolicy);
    continue PLAY;
}
```

PROBLEM CLASS: одно null используется как final removal; конкретный temporary/final reason не виден. REQUIRED SHAPE при подтверждении: ACQUIRED → stock body/finally-close; PAUSED → bounded skip; REVOKED → exact stop. Синхронизация удаления registry не должна охватывать ожидающий drain/cast/store.

`stopPhantomAutoPlay` использует `withAutoPlayRegistration`, `removeAutoPlay`, затем `finishAutoPlayRegistrationStop`. Перед перемещением lock boundaries проверить реальный call chain Player. Не переписывать pickup/attack loop.

## C. PlayerNativeEvidence.java: snapshot(long)/checkLiveHorizon

```java
// CURRENT, фрагмент snapshot
if ((_phase != Phase.NONE)
    && ((_phaseSinceNanos > _observedNanos) || (_phaseDeadlineNanos <= _observedNanos)))
{
    _overflow = true;
}
```

PROBLEM CLASS: один bool покрывает phase expiration и недостоверные counters. REQUIRED: первое точное bounded invalidation reason; никакого `_overflow=false`, новогоepoch или увеличенияdeadline ради PASS. native phase retire привязан к действительно окончившейся работе.

`selected(Target,long)` завершает cycle, когда предыдущий target уже damaged/killed/exp/sp. Проверить late reward ordering. Исправить bookkeeping только при genuine ordering RED; значения EXP/SP брать из native write, а не восстановить по таблице наград.

## D. PhantomNativeWorkScope.java: recordFailure/checkpoint/drain

```java
// CURRENT
if (_failure.isEmpty()) { _failure = "NATIVE_WORK_FAILED:" + failure.getClass().getName(); }
if (_state == State.OPEN) { _state = State.DRAINING; }
```

```java
// CURRENT checkpoint / drainAndSeal
if (PlayerNativeWork.current(this) != null)
{
    throw new IllegalStateException("NATIVE_WORK_SELF_DRAIN");
}
```

REQUIRED: сохранить эти safety semantics. Первая incident/work-kind/parent/epoch должна попасть в diagnosis. Если caller дренирует себя, перенести его continuation на существующую control boundary после закрытия ticket. Не завершать/вычитать running ticket досрочно.

## E. Tutorial

Конструктор Q00255 добавляет kill listener для TUTORIAL_GREMLIN18342. Это не20534 selected profile110. До null-guard/fix установить реальный actor+target и отсутствие/наличие состояния; не предполагать, что каждый onKill NPE остановил farm110.

## F. Изменения сообщений

Runtime reason не переписывать в running просто для красивого отчёта. Выводить отдельно cached reason+age и fresh native state. STOP причина берётся из TASK/бюджета/доказанного отказа, не придуманной команды пользователя.
