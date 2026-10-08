# Crash/restart: завершение, а не просто остановившийся процесс

1. Retained452 — read/export024c→025a; before snapshots/hash; start новыйcode;
   no Player grants. Production recovery завершает exact existing receipt.
2. Second ordinary sameDB restart025a должен быть idempotent. Не считать повтор SELECT
   повторным restart. Оба запуска входят в бюджет, не упираются в старый8starts cap.
3. New AFTER_NATIVE025e: выбрать natural actor с native-earned progress; stock checkpoint
   fault point AFTER_OWNED_NATIVE_STORE, current owner/epoch/receipt exact, REALcount0.
   SHA/immutable snapshot/hash/dump заранее, exact halt по этому окну. Expected AFTER SQL.
4. Restart025e: receipt resolved без ручного finalize/UPDATE; before допуска игры подтвердить
   durable proof. После этого normal materialization/recovery/farm. Затем повторный restart.
5. Optional AFTER_FINALIZE025f: только если025e resolved, остаток времени≥25мин;
   no duplicate native writes/awards, reconstruct index, normal continuation.

Предыдущие TASK024 crashes не проводить ещё раз на его исходных БД. Code must preserve
old payload codecs, not delete receipt. canonicalNEITHER intentionally fenced.
Если snapshot захвачен доPREPARE, после flush либо afterFINALIZE — это разные expected
стадии; не сопоставлять их как одно quiescent state без exact receipt identity.

User authorization: TASK.md. Halting endpoint exit≠graceful stop PASS. Отдельные флаги
processExit, ownedReceiptResolved, indexPublished, gameplayResumed, healthyDrain.
