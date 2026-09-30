# PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005 — observer closeout

Status: **CONNECTED RED; M1 OPEN; final manual gate REQUIRED**. После свежего TestAdmin arm выполнен ровно один connected-run. Повтор, исправление runtime boundary и M2 не выполнялись.

## Реализация

- `LocalPlayM1Observation.java`: чистый выбор LIVE/COMMITTED/TRANSITION, guard отсутствия STORED ownership, решение о контакте и точные одноразовые M1 tickets с TTL.
- `PhantomSystem.java`: адресный immutable снимок проверенного native Player с object/lease/epoch, live Location и visibility; ограниченная выборка кандидатов только на INITIAL. Существующие history/ecology/topology publishers не менялись.
- `LocalPlayPilotActions.java`: runId-bound INITIAL/APPROACH/LEAVE/RETURN, динамическая позиция того же Player, ограниченная переподготовка до lock, точные LEAVE/RETURN tickets, лёгкие снимки и отдельные observed/committed поля.
- Существующий `Run-M1RuntimeHandoff.ps1`: динамический bounded подход; NEW и EXISTING разделены; 40 s наблюдения, три census, 15 s отсутствия, до 45 s возврата, независимые cleanup/restore/stop и budget mailbox/deadline. При dot-source PLAY не запускается.
- Тесты: `PhantomOperatorObservabilitySuite.java`, `PhantomBackgroundSuite.java`, `LocalPlayPilotNativeSuite.java` и фактический runner через `Test-M1ObserverOffline.ps1`. Bounded exception для native Pilot suite защищает отказ неверного ticket без fallback-телепорта; второго runner или proof-задачи нет.
- Изменения охватывают одну M1 observer-цепочку и входной пакет этой же continuation. Production scope ограничен тремя Java-файлами и существующим runner; новых фоновых потоков, SQL-записей координат и повышения caps нет.

## Проверки до arm

- Operator observability: **11/11 PASS**; guarded native TEST position canonicalization: **7/7 PASS**; native Pilot action guards: **6/6 PASS**.
- Offline fake transport/clock выполняет реальные функции runner: **M1_OBSERVER_OFFLINE_PASS**, включая движение, visibility, epoch, даты, ticket/cleanup/TSV/UNCERTAIN negatives.
- Native TEST использовал существующий launcher, эталонные TEST manifest/geodata и отдельную TEST DB; SQL/EOL не менялись. Это подтверждает native Player и action guards, но не заменяет connected PLAY.
- Exact-source clean `ant jar`: **BUILD SUCCESSFUL**. Развёрнутый GameServer.jar SHA-256 `7496414678163E62203ED0440D4FDE1643C9FE20F4F23C504A82E8C7A14461A9`, source `eb4a98f77e91f97cf9c6a5c54748a7d2be98f11d`. Login JAR сохранён.
- Перед одним owned restart сделаны приватные backup JAR/INI/manifest и согласованный native PLAY dump: 64 026 808 байт, completion marker, SHA-256 `ADFCB18C42D9DA446FFAE94D8FA72E9FFAE6FAB078D87FD9D46195932CBA65DD`. Dump не публикуется.
- После deploy штатный `Check-LocalPlay.ps1`: **CONFIG PASS**, Login/Game RUNNING, ownership и порты подтверждены. INI: 1280 READY target, active64, cap128, pulse100 ms, profilesPerPulse256, maxScheduled10000. Старую служебную метку manifest profilesPerPulse128 исправили на фактические 256 без изменения INI. Read-only PLAY SELECT перед arm: READY1280, RETIRED8720, всего10000.
- Предыдущий connected RED `NORMAL_MATERIALIZATION_DURING_APPROACH_NOT_OBSERVED` остаётся исходной причиной continuation; новая реализация не объявляет его закрытым без connected evidence.

## Один connected-run: RED

- Run ID `23360eec-d7d8-442f-8da9-e37770b209bb`; локальное evidence: `M1_CONNECTED_RESULT.txt` и `M1_CONNECTED_WORLD.tsv`. Первичный отказ: `PREPARE_APPROACH_REJECTED:NO_NATIVE_APPROACH_ROUTE`, requests9.
- INITIAL выбрал profile882 как `STORED_START`: `COMMITTED` sequence1, точка (44126,42751,-3488), native Player ещё отсутствовал. TestAdmin с `REAL_LOGIN` находился снаружи в (49216,42751,-3491), расстояние5090; `humanPrewarm=false`, `regionCanKnow=false`, `clientVisible=false`.
- На фазе APPROACH server-side `nativePath` вернул null до выдачи маршрута. Наблюдаемый контракт объединяет отказ двустороннего GeoEngine, отсутствие/слишком длинный PathFinding route и лимит длины; из этих данных точную внутреннюю ветку различить нельзя. `MOVE_SELF` в подходе не начат, firstLocal/firstMaterialized/firstCouldKnow/firstClientVisible отсутствуют. `historicalStatus=RUNNING` и `readinessReason=catchup.recovery.baseline_or_goal_missing` наблюдались одновременно, но причинная связь с route rejection не доказана.
- Cleanup без ошибок: TestAdmin возвращён к исходной позиции с `REAL_LOGIN` (`RESTORE=PASS`), Pilot run остановлен (`STOP=PASS`). После stop consent session осталась `ARMED_IDLE`, `runActive=false`; это не `OFF`. GameServer остался owned/RUNNING, CONFIG PASS.
- Owned Game process за короткий run: CPU 4 994 641→4 998 250 ms, private bytes 509 652 992→511 016 960. Это контрольные снимки, не benchmark.

## Connected matrix

| Наблюдение | Статус единственного run |
|---|---|
| NEW_MATERIALIZATION | NOT_OBSERVED |
| CONTACT | NOT_OBSERVED |
| NATIVE_LIFE | NOT_OBSERVED |
| COHORT | NOT_OBSERVED |
| SOFT_RETURN | NOT_OBSERVED |
| RESTORE / STOP | PASS / PASS |

Полная сцена не состоялась: census/очереди во время наблюдения native life не получены. Ограничения overhead проверены структурой пути (одна выборка на INITIAL, затем адресное чтение, три census), а не PLAY benchmark. Глобальные historical failures не обнулялись и не объявлялись M1 gate.

Итог: **RED_OR_UNPROVEN — M1_OPEN**. На точной runtime boundary остановились без второго connected-run и без самостоятельного исправления. Дальнейшие действия только по новому указанию пользователя; M1 GREEN не заявлен.
