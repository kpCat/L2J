# Read-first: подтверждённое и неизвестное
Проверено coordinator по remote `9aeb4ac6c52970372f97637d26a5eb54c760ed1a`. Windows JVM/DB здесь не запускались.

## Установлено отчётом027 (не новый runtime)
1. Lifecycle027: actual death/return450/459, 16/27 cycles в НОВЫХ epochs; healthy
   stop2.7–7.2с до pools; full retained group SEALED→SQL8/8 в c/d/g.
2. Natural C:6/7 вместо hardcoded8 — baseline не начат. D:322.3155669с,7/8 на
   ПРЕРВАННОЙ сцене. 506 ABSENT. Это не доказанные две GREEN farm scenes.
3. Read request `f400cdc9-df2a-4dfa-ad63-d2d0a96903c3` стал UNCERTAIN в D.
4. Soft MOVE request `77b3663e-f2d8-49b9-9020-050f08299a21` стал UNCERTAIN;
   dry bidirectional path63 samples прошёл, фактический return/remat не доказан.
5. Отдельный E-session действительно потерял heartbeat до первого чтения. Его
   исправление НЕ объясняет автоматически два предыдущих request.
6. EVENT24354 исходного026 остаётся UNKNOWN; здоровый новый callback не объясняет
   старый. В028 не переисследовать старый queue case без его повторения.

## Установлено текущим source
A. Observe027 генерирует runner через Observe026→Observe025→Observe024 и string
   replacements. Census026 СНАЧАЛА делает VisibleCensus026 (mailbox), даже когда
   FullObserve уже запущен и затем читает `full-cohort-latest.json` напрямую.
   Следовательно, непрерывная телеметрия сейчас зависит от ненужного mailbox roundtrip.
B. Synthetic: absolute TTL525с, heartbeat stale30с, максимум400 sequenced requests,
   до5 starts/JVM. `writeState()` каждый раз публикует expiresUtcMillis=now+600000,
   хотя реальная monotonic deadline НЕ сдвигается. Это конкретный metadata defect,
   не доказанная причина D/MOVE UNCERTAIN. Исправлять через тест абсолютного expiry.
C. Synthetic process сначала CLAIMED/sequence++, затем execute, затем writeResult.
   RuntimeException из execute превращается в UNCERTAIN и закрывает synthetic session.
   Внешний safePoll при exception прекращает mailbox. Если запись результата потеряна,
   UNCERTAIN не доказывает, что action не исполнялся.
D. Invoke-LocalPlayPilot.poll проверяет identity request/session/sequence/operation,
   но не runId. Библиотека читает result только<=64KiB. Сам writeResult не имеет
   симметричного ограничителя. Это точки обязательных negative tests, не диагноз D.
E. Native MOVE_SELF ограничен400XY/200Z, проверяет GeoEngine и возвращает
   ACCEPTED/ARRIVAL_PENDING — это не доказательство прибытия.
F. Active behavior УЖЕ разделён на BackgroundDecision/VisibleFarmTravel/VisibleAutoPlay.
   prepareVisibleDecision уже делает exact revision sync и atomic replacePlan.
   Нельзя добавлять параллельный «общий AI», повторяющий этих владельцев.
G. Retention ACTIVE_ACTION включает движение/бой/cast и admitted actions. Если после
   ухода observer бесконечно выпускать новые auto roots, это может постоянно удерживать
   actor. Это РИСК по source; реальный self-pin028 требуется воспроизвести, не предположить.

## Что Codex должен определить, максимум30мин targeted read
Для двух exact IDs: request/processing/journal/result, root и session properties,
server exception stack, sender return и heartbeat timeline. Классы: RESULT_TIMEOUT,
NATIVE_EXCEPTION, REJECTED_PRECONDITION, IDENTITY_CLOSED, TTL/HEARTBEAT,
SERIALIZATION_OR_IO, SEQUENCE, UNKNOWN. Не объявлять причиной nearest log line.
Если request artifacts нет в Git, читать retained027 private runtime READ ONLY.
Новые snapshot gaps сами по себе не являются AI failure, но не дают PASS.
