# EVIDENCE — что сохранить для независимого review

Один публичный sanitized отчёт `docs/phantoms/reports/PHANTOM-NATIVE-REGION-TRAVEL-089.md`. Доказательства могут остаться private в own089 stage, но в отчёте должны быть reproducible identifiers/полные SHA256 **публичных** sources и artifacts, не реальные private credentials.

Минимальная таблица по H/E/O (в отчёте одна строка на actor+leg, не aggregate magic):

| Поле | Фактическое значение |
|---|---|
| authority | live HEAD, source SHA, task-package SHA, class + level + stock gear fixture-before |
| start / route | real Player objectId/profileId, start XYZ/instance, from/topology anchor, target NPC/region, goalId/revision |
| path | corridor/from/to, native Geo source+outcome, segment count+MOVE_TO issued/arrivals, `SEARCH_LIMIT` vs `NO_PROVEN_CORRIDOR`, wall/queries |
| gatekeeper | stock NPCId/list/index, canInteract result at action time, castle/karma, fee quote, **native before/after Adena**, actual landing/failed action, verified native sourceRefs |
| farm | Mob NPCId and actual spawn, HP before/after, native EXP/SP/Adena/items/drop pickup before/after (without fabricated reward) |
| restart | sameDB JDK/JAR/source SHA, old/new epoch, same Player/profile/goal/cursor, wallet/items/XYZ, payment receipt + idempotency result |
| negatives | actual exact expected refusal, and evidence no move/fee/reward for guarded failures |

Категории: `SOURCE_PROVEN`, `PROJECTION_ONLY`, `REAL_PLAYER_PREPARED`, `REAL_WALK_ISSUED`, `REAL_WALK_ARRIVED`, `GK_NATIVE_ACTION`, `GK_PAID`, `NATIVE_ATTACK`, `NATIVE_REWARD`, `PERSISTED_RESTART`, `OWNER_QA_PENDING`. Никакого повышения класса без receipt.

Записать first failing build/test/native attempts, их subsequent fixed/NOT_RUN; единственный final source tree/build SHA, exact compilation/exit codes, собственные own089 JVM PID/start/termination, final DB connections0, exact staged file list+diff, commit/push SHA and branch parity. Если SHA актуального live HEAD сдвинулся после подготовки пакета — diff compatibility отмечается в начале отчёта.

При PARTIAL объяснить не «всё хорошо», а конкретный root blocker, исправленные product файлы, что ещё не доказано, и возможное узкое продолжение **без автоматического запуска**. Допустима простая диаграмма последовательности, но не дублировать тысячи строк raw logs.
