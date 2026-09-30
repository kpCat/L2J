# PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005 — observer closeout

Status: **WAITING_ARM; M1 OPEN; final manual gate REQUIRED**. Исправление точной boundary предыдущего connected RED подготовлено, но новый arm и connected-run не выполнялись.

## Текущая подготовка после RED

- Причина `NO_NATIVE_APPROACH_ROUTE`: INITIAL уже доказал outside→prewarm и prewarm→inside, а APPROACH повторно запрашивал длинный outside→inside через `nativePath`. Теперь INITIAL сохраняет immutable envelope с runId/profileId/committedSequence, outside, prewarm и inside path. Для того же COMMITTED target APPROACH выдаёт ограниченный остаток этого маршрута; после COMMITTED→LIVE использует текущую позицию того же objectId/materializedAtNanos. Движение и выбор цели остаются в observer/runner.
- APPROACH проверяет только forward geodata; каждый фактический MOVE_SELF сохраняет собственный native forward guard. Отказы типизированы: DIRECT_FORWARD, PATHFIND_NULL, PATH_TOO_LONG, DISTANCE_LIMIT, FORWARD_SEGMENT_REJECTED, TARGET_TRANSITION, а также stale envelope/identity. Временный LIVE no-path учитывается в прежних 6 replans/120 s; точная причина записывается в evidence. LEAVE/RETURN ticket и cleanup guards не ослаблены.
- Изменены только `LocalPlayM1Observation.java`, `LocalPlayPilotActions.java`, существующий `Run-M1RuntimeHandoff.ps1`, `PhantomOperatorObservabilitySuite.java`, `LocalPlayPilotNativeSuite.java`, существующий `Test-M1ObserverOffline.ps1` и эти два документа. Нет новых DB position UPDATE, потоков/таймеров, периодических full scans и правок history/ecology/background core; caps сохранены.
- RED→GREEN: focused observability **12/12 PASS**; guarded native Pilot **7/7 PASS** и native position **7/7 PASS**; фактический offline fake runner **M1_OBSERVER_OFFLINE_PASS**, включая cached COMMITTED route, forward MOVE_SELF, COMMITTED→LIVE relocation того же epoch, transient typed no-path, CONTACT, OBSERVE, LEAVE, 15 s ABSENT, RETURN, RESTORE/STOP. Шесть transient failures исчерпывают ровно bounded budget. Aggregate 205 не запускался.
- Exact code commit/push `7653282fccfec15a1d62a2d0ea6b5382f81073a5`; clean `ant -q -Dbuild=.phantom-local/m1-005-clean-7653282 jar` **BUILD SUCCESSFUL**. Развёрнутый GameServer.jar SHA-256 `8A7638F563612D810B43070C6151F45FA9FE8117A7D4BC581F6667DE183C34DC`; Login JAR hash прежний `3B79A86276AA544E6886881F54814BE159FAC1412241852408781E86EF64A9E8`.
- Owned stop → приватный backup `artifacts/local-play/m1-005-backup-7653282-20260930` → native PLAY dump **64 420 864 байт**, completion marker, stderr 0, SHA-256 `C958FC8EB1F8EF6C6258A785322F1DBAEEA9CB738822A8CC00EF465B97378848` → controlled deploy → один owned restart. Временная ошибка форматирования manifest при deploy исправлена из backup до старта; итоговый JSON и source/JAR hash проверены.
- После deploy `Check-LocalPlay.ps1`: **CONFIG PASS**, Login PID21388 и Game PID29396 owned/RUNNING; Pilot **OFF**, runActive=false. Deployed runner hash совпадает с source. Read-only PLAY SELECT: **READY1280, RETIRED8720**. INI/manifest 1280/64/128/100 ms, profilesPerPulse256, maxScheduled10000; глобальный FAILED_REPLAN_REQUIRED не используется как gate.
- Текущая connected matrix: NEW_MATERIALIZATION/CONTACT/NATIVE_LIFE/COHORT/SOFT_RETURN = **NOT_RUN**; RESTORE/STOP нового consent-сеанса = **NOT_APPLICABLE_PRE_ARM**. Новый TestAdmin PREPARE/MOVE и arm-код не запускались. Единственный следующий шаг — свежий consent для ровно одного Run-M1RuntimeHandoff.

## Предыдущий connected RED и его evidence

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

Предыдущий итог: **RED_OR_UNPROVEN — M1_OPEN**. Точная boundary исправлена в этой continuation по новому указанию пользователя; M1 GREEN остаётся недоказанным до одного нового connected-run. M2 не начат.
