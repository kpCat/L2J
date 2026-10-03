# Crash / failure windows

| Окно | Что требуется | Что запрещено |
|---|---|---|
| До PREPARE | current runtime остаётся единственным materialized owner | вручную очистить background |
| PREPARE записан, native store не выполнен | reconcile существующего immutable intent | новый intent на mutable runtime |
| Native store частично/полностью выполнен, FINALIZE отсутствует | existing b4f verification/reconciliation | назвать READY по одному marker |
| FINALIZE завершён, runtime изменился | сохранить receipt/первую ошибку, investigated mutator | удалить mismatch guard |
| afterStore failure плюс finally failure | primary + suppressed, failed ownership retained | заменить primary cleanup noise |
| После store, до deleteMe | повторяемый cleanup, snapshot consistency | release identity раньше World cleanup |
| После delete, до identity/outbound release | idempotent completion, exact postconditions | новый Player со старым owner |
| Retry cleanup | first immutable, latest separate | переписать FIRST последним exception |
| P1 отменён, worker держит ссылку | managed fail-closed/conditional P1 stop | NOOP lease/остановить P2 |
| P2 опубликован только в одном pool | running=false, controlled own repair | autoPlay=true ⇒ здоров |
| Journey terminal, следующий decision | same-revision budget/resolution сохранены | startedNanos reset-loop |

Существующие owned C/D сценарии из предыдущего RESULT установить по локальным именам,
включить в VERIFY и сохранить restart evidence. Таблица не заменяет их.
Crash replay выполняется только на guarded TEST fixtures, не kill/reset PLAY JVM.
StopAllTasks/abortAttack не считать самостоятельным crash-consistency proof.
