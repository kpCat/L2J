# Acceptance — нельзя получить зелёный результат одними fixtures

TASK_RESULT=GREEN только если:
- COLD_RECOVERY_PASS: retained452 resolved обычным bootstrap и second restart idempotent;
- LIVE_CHECKPOINT_PASS: true native arrival/store succeeds or recoverably defers without
  orphaned SEALED; next sameepoch native play; actual firstproducer corrected;
- CALLBACK_DRAIN_PASS: due earned callback runs normally under pending control, no
  cancellation/manual executor bypass/timeouts inflated;
- REGRESSION_PASS: relevant suites clean or paired unchanged-base issue fully explained;
  новый safety regression всегда запрет;
- FARM_A/B + COHORT_PASS: two frozen natural scenes per SCENARIOS;
- PERSISTENCE_PASS: whole group sealed→SQL exact, current receipts finalized;
- RESTART_PASS: sameDB replay idempotent and repeated useful native life;
- CLEANUP_PASS: first healthy earned stop pending0/retained0, no force, dependencies/pools
  ended correctly; later empty cleanup не заменяет FAIL первого stop.

Ожидаемые отрицательные tests не являются failed gameplay. Необъяснённые relevant
failures и missing snapshots — FAIL/INCONCLUSIVE, не «неважный debt».
STOP по внешнему scope/safety/budget с незавершённой задачей=BLOCKED;
выполненная final проверка с красными product gates=FAILED. Один итог, не три повторения.

M1 всегда OPEN, пока все inherited server gates (включая death/soft return/crash/loot)
не доказаны. Если они тоже PASS на этомSHA — SERVER_M1_PASS=true и WAITING_FINAL_CLIENT.
Без реального клиента REAL_FINAL_PASS=NOT_RUN. Полный M1_CLOSED не выдумывать.

Не ослаблять evaluator threshold и не заменять цели «вся группа играет» на один профиль.
Не считать TASK025 иной нумерацией roadmap: M2 не начинается.
