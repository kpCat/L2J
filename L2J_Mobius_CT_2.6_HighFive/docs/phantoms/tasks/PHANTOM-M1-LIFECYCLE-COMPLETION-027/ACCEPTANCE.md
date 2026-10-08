# Acceptance

## TASK027 GREEN (LIFECYCLE_COMPLETION_PASS)
Только если выполнены все условия своего среза:
1. Composed RED доказал pending→premature return до materialization; post-fix это bounded
   typed progress, без monitor/pool self-wait и без общего увеличения timeout.
2. Actual native delayed kill-event + earned child завершаются один раз до store.
3. Первый штатный full-server stop имеет completed lifecycle, pending0/retained0 до
   ThreadPool.shutdown; late process exit/retry не подменяет этот PASS.
4. Real materialized death и cold DEAD оба доведены до штатного native recovery/store;
   один recovered natural actor делает3 новых полных farm cycles в новой exact epoch.
5. Eight enrolled actors accounted, full native SEALED→SQL/доказанный no-live durable state;
   два sameDB restarts сохраняют native payload/receipts, lawful state transitions отдельно.
6. Same-target/callback/old-owner negative controls и18 regression routes026 не регрессировали.
7. Нет новых unclassified native incidents; frozen artifact/clean build/scope проверены.

## Что НЕ позволяет закрыть M1
TASK027 GREEN не стирает FARM_A/B strictFAIL. Final natural scenes с initial cohort и
progress in final120s обязательны как контрольные. Их outcome показывать независимо.
M1=WAITING_FINAL_CLIENT только если на finalSHA пройдены farm whole cohort, loot,
death/return, soft demat/background/remat, healthy shutdown, restart и actual crash gates.
Без real final client M1_CLOSED запрещён.

## Допустимые исходы
GREEN — вышеуказанный lifecycle срез пройден; M1_OPEN допустим и объяснён.
FAILED — запуск состоялся, но один из своих обязательных product gates RED.
BLOCKED — конкретный safe/base/environment/outside-design barrier. Не «нужен пользователь».
Нельзя пропускать missing/dead actors или снижать требования тестов для получения GREEN.
