# Independent review — Task 004A real-login lease cleanup hardening

## Verdict

```text
Task 004 technical feasibility: ACCEPT
Task 004A: FIX_REQUIRED
Revert: NOT_REQUIRED
Task 004B: REQUIRED
ADR 0001: Proposed
Goal 005: BLOCKED
```

Task 004A корректно закрыла гонку `CharacterSelect/onDisconnection`, добавила
общий connection-state gate, fail-closed retention, retryable Phantom cleanup и
terminal `STORED`. Эти изменения сохраняются; rollback не требуется.

## Findings

### P1 retained REAL_LOGIN owner bypassed while disabled

Policy `phantomSystemEnabled || currentOwner == PHANTOM` возвращала `false` для
disabled + `REAL_LOGIN`. Поэтому retained lease после неполного cleanup мог быть
проигнорирован следующим ordinary login. Legacy path допустим только при полном
отсутствии owner.

### P1 wrong-character cleanup may release another lease

`Disconnection` проверял наличие любого client lease, но не равенство
`lease.objectId` и `cleanupPlayer.objectId`. Cleanup Player B мог освободить
retained lease Player A. Release должен быть object-ID scoped и fail-closed.

### P1/P2 cleanup postcondition is exact-instance scoped

Cleanup policy принимала отсутствие exact `Player` instance, даже если в World
или autosave оставался другой объект с тем же object ID. Identity invariant
object-ID scoped, поэтому обе World maps и autosave должны быть пусты по ID.

## Accepted Task 004A work

- shared `playerLock` и `AUTHENTICATED/DISCONNECTED` ordering;
- bounded retention warning;
- отсутствие автоматического unbounded retry;
- operation failure сохраняет Player/output/lease;
- повторный cleanup достигает `STORED`;
- успешный повторный cleanup остаётся no-op;
- исходная failure matrix `11/11`.

## Required closure

Task 004B ограничена corrected truth table, exact lease/object ID release и
object-ID cleanup postconditions. DB schema/config, `Player`, packet seam,
roadmap и Goal 005 менять нельзя.

Успешная реализация может рекомендовать:

```text
FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Это не переводит ADR в `Accepted` и не разблокирует Goal 005.
