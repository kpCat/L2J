# Независимое review Goal 007 — shared activity scheduler

## Verdict

```text
Reviewed commit: 9958edd9e133557f4966eed0a4124e68326401b3
Parent: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Architecture direction: ACCEPT
Implementation verdict: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 007A: REQUIRED
Goal 008 / 009: BLOCKED
```

Пять activity states, explicit registration, bounded signal/ready/due
structures, один shared pulse, fairness, hysteresis, overload cadence, typed
work items, canonical materialization bridge и explicit retained retry
сохраняются. Архитектурная переработка Goal 007 не требуется.

## Findings

### P1 — in-flight unregister orphan

Slot с effective `SLEEPING`, `WARM` или `BACKGROUND` мог быть удалён во время
внешнего `materialize(profileId)`. Поздний успешный результат оставлял
service-owned canonical actor без scheduler owner.

### P1 — retained ownership silently cleared

Проверка `requested == effective` выполнялась раньше retained failure и могла
снять требование explicit cleanup retry после signal update, withdrawal или TTL
expiry.

### P1 — false ACTIVE after cleanup retry

Успешный retained-dematerialization `retryCleanup` мог напрямую скопировать
новый requested `ACTIVE`/`NEARBY_PERCEPTIBLE` в effective state, хотя canonical
actor уже был удалён.

### P1 — adapter status/ownership mismatch

Specific status, включая `WORLD_OBJECT_IDENTITY_BUSY`, мог сохранять service
entry, но adapter классифицировал retained только по
`MATERIALIZATION_FAILED_RETAINED`.

### P1/P2 — STOPPING not quiescent

Pulse, уже вышедший из scheduler monitor, мог начать lifecycle boundary или
work после `beginStop`, а `finishStop` очищал состояние без доказанной
quiescence pulse и slot.

## Required closure

Goal 007A обязан добавить bounded processing/boundary ownership, retained
precedence, truthful cleanup retry с отдельным fresh materialize, фактическую
service ownership classification и `pulseInFlight`/`finishStop` quiescence.
Goal 006 lifecycle, config/schema, Goal 008 и Goal 009 остаются frozen.

Это review фиксирует verdict над неизменным commit `9958edd9...` и не принимает
реализацию Goal 007A автоматически.
