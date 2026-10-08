# PATCH GUIDE — проверено на required base

## A. PhantomSystem.java (основной участок)
`shutdownIfStarted`, примерно1765–1815: wait есть только для populationEcology.
`shutdownClaimed`, примерно1190–1245: backgroundReady false → FAILED/return до materialization.shutdown.
`backgroundReadyForMaterializationShutdown`, примерно1560: death.drained + background quiescence.
Реальные CURRENT выдержки в source-excerpts/01 и02.

// PROBLEM
Boolean объединяет pending и terminal failure. Stock Shutdown делает два быстрых calls
и закрывает pools; pending callbacks теряют исполнитель до завершения lifecycle.

// REQUIRED SHAPE
`beginStopAttempt` — однократно;
`advanceStopAttempt` — typed progress без wait под monitors;
`awaitStopAttempt` — только внешний shutdown caller, один deadline, bounded condition/poll;
legacy boolean facade возвращает true только после actual COMPLETE.
Использовать существующие counts/service states и stop methods, не отдельные shadow owners.
FAILED branch/startup failure должны сохранять безопасное cleanup продолжение.
Отдельно тестировать null instance/no-op: не менять их established return semantics ради PASS.

## B. PhantomBackgroundService.java
`recover`/`recoverOwned`1480–1640 держат `_recoveries` и currentOperations до конца
синхронного materialization.dematerialize. Стек B подтверждает вызов из scheduler.
Разделить live request/continuation и safe terminal store при необходимости после RED.
Не wait пока удерживается ActionLease или synchronized(player) из native recovery.
Не удалять `_recoveries` в середине ещё выполняющейся операции; publish completion finally.
`beginStop/materializationQuiescence` должны учитывать принятую operation до её завершения.

## C. PhantomOrdinaryDeathRecovery.java
onDeath проходит до публикации dead flag (существующий комментарий), pulse ждёт100ms,
corpse wait45s, `_reconcile` уже использует instant executor. Не инвертировать isDead
проверку без real ordering test. Уточнить draining queued/running отдельно; running count
не должен пропускать опубликованную reconciliation, которая ещё в очереди.
close не теряет обязанность already-earned store и не разрешает новый revive после stop.
Добавить exact observation death registration→native return→stored→resumed.

## D. PhantomMaterializationService.java
cleanupEntry/retryCleanup/automatic drain retry: re-use exact Entry, released/cleanupInProgress,
materialization completion и current NativeWorkScope; stale retry no-op.
Если требуется control request API, один bounded request на Entry. Нельзя переносить
storeMe под ActionLease или завершать tickets вручную. Один retry на реальную quiescence,
не busy-loop с искусственной задержкой и сбросом deadlines.

## E. PlayerNativeWork / NativeWorkScope
В ParticipantWork.run добавить observation EXECUTOR_ENTERED перед `start()`.
В WorkTicket.tryStart RUNNING публикуется уже внутри owner monitor — это другой boundary.
Новый observation method default no-op; существующий dynamic dispatch contract не менять.
Скалярный immutable snapshot получаем после release owner lock; file/DB I/O в hooks запрещён.
Если в actual B queue delay остаётся без source proof, отдельный NOT_RESOLVED,
а не blanket «исправлено starvation».

## F. Нативный shutdown caller
Shutdown.java читается как integration call site. Изменять можно только существующие
Phantom hook calls/log-result classification, если prove API нуждается в этом; никак
не менять глобальный ThreadPool.shutdown/DB shutdown/REAL disconnect semantics.
Предпочтительно всё waiting исправить внутри PhantomSystem.shutdownIfStarted.
