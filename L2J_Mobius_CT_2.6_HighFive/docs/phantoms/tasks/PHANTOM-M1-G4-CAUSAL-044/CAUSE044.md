# CAUSE044 — offline input closure

D1 profile1189: key/sequence/expiry отсутствуют в raw043. Existing flight recorder
поддерживает accepted/coalesced key, но exporter043 не включал session/watch/capture
этих событий; поиск retained raw не нашёл SCHED_SIGNAL_ACCEPTED/COALESCED. Число
activeSources1 и поздний NONATOMIC_VOLATILE кадр не дают causal owner RED.

C1 profile776: actual absent window77.229855с,77 successive native frames;
во всём window scheduler SLEEPING,requestedState=SLEEPING,activeSources0,
nextDueNanos0,tickSequence1423,lastTransitionNanos665058564471200.
Нет actual BACKGROUND work due, поэтому ожидание автоматической обычной producer
операции за77с было неверной предпосылкой. Обычный firstWorkDue BACKGROUND вычисляется
от transition logicalNow + policy.cadenceMillis(profile,tickSequence+1):300000..900000мс.
SLEEPING не подготавливает work item. Эти фактические состояния важнее label
BACKGROUND_ABSENT в runner043.

Альтернативный ecology producer: readiness horizon29865614→29865616, cursor
остаётся29865613 до RETURN; nativeRelease catch-up служит отдельная очередь.
updateMaterializationDemand detects previous native owner loss и queueLocked
с _nativeReleasePending; work pending выполняет existing historical/CAS producer.
Recorded queue/currentStage не являются actual successful commit. Raw подтверждает
отсутствие committed producer в measured window, но не causal defect этой очереди.
Scheduler cadence не даёт deadline для отдельной ecology очереди.

Минимальный разрешённый snapshot добавлен только из-за этого evidence gap.
BASE compile-contract RED:7 missing signalSources references; GREEN existing
activity-scheduler28/28, включая2 focused immutable/replacement/withdraw/overdue
теста. Эта проверка доказывает diagnostic contract, а не gameplay defect.
Единственный native boot/diagnostic044a завершён с INPUT FAIL до MOVE.
В selected cohort отсутствовал living current-ready input; tentative profile68
был MATERIALIZED/COMPLETED, farmAllowed=true, но readinessComplete=false,
committedCursor29858535/requestedHorizon29865710 и ecology.preparation_capacity.
G4 preflight в reused runner оказался сильнее, чем нужно для passive source view;
one-profile collector не был подключён. Поэтому A1 остаётся NOT_VERIFIED.
Новый snapshot API не делает retrospective attribution D1 и не заменяет live proof.

Один same-boot passive read заранее выбранного68 после Synthetic STOPPED был
отклонён до чтения неправильным runtime identity аргументом. Output отсутствует;
этот шаг не является away proof, owner key ему не присвоен. Второго native boot,
повторной сцены и полного G4 не было. Causal gameplay RED отсутствует, source fix
producer не внесён. Исходные failures сохранены, лимит diagnostic не расширен.

Private shutdown RED: copied agent ограничивал DB regex043; actual044 stop request
отклонён до Shutdown. Исправлена только exact044a tooling mask и class identity
(новый класс нужен, поскольку JVM уже загрузила старый отказавший agent class).
Native shutdown guards PID/start-time/runtime/role/root/catalog сохранены.
GREEN: Game20640/Login25468 STOCK_GRACEFUL_STOP_CONFIRMED; own JVM0.
Original failed monitor успел поставить TASK032_STOP_MONITOR_TIMEOUT в RUN_END;
поздний DONE/COMPLETE/retained0 не стирает failure. Strict conservation FAIL
EXPORTER_GAP_OR_RUN_NOT_ENDED;82 native committed witnesses и44 stopped full
canonical profiles отдельно PASS,75 births все terminal,35702 owning commits,
RUN_END dropped0. Это не FULL_SAVE/GLOBAL_DRAIN PASS полного run.

Следующий конкретный scope: source-only operator capture через signalSources для
заранее закреплённого живого native profile после real settled-away boundary,
без зависимости selection от BG current-readiness. В текущем044 такой capture
не разрешено повторять. Ни topology.combat, ни population.schedule, ни
background.native_context не объявлены реальным владельцем D1 без ключа.

Input paths/SHA и compact phase facts: evidence/OFFLINE044.json.

## Разрешённый дополнительный source-only trace

Пользователь явно разрешил ещё один source-only trace и отдельное окно до30мин,
12:21–12:51 UTC14.10.2026. Исходный one-trace limit расширен только этим ответом;
полный G4 в новом окне запрещён. Первый failure/DB/raw сохранены. Новый ordinary
boot выполнен на прежнем044a/source80/JAR E502, без reset/reimport/heal.

Prospective profile73/object268485461/epoch708784394245000, native earned
14cycles/14rewards/37damage при selection; первый one-profile frame уже15/15/38.
Actual one-profile getter дал55 кадров. Единственный
ключ human.local, NEARBY_PERCEPTIBLE, TTL10000мс, sequence135→186; первая expiry
708924189968400 при sample708915100643700, последняя expiry708976590030600 при
sample708971408971400. Все snapshot remaining положительные, expiredAtRead=false.
Existing PhantomHumanLocalityControl владеет human.local и обновляет его раз в1с
для действительного prewarm: observer оставался у origin, MOVE не выполнялся.
Это законный актуальный locality source; ACTIVE source в этих кадрах отсутствует.

В пределах45с quiet boundary для закреплённого73 не появился. Runner сохранил
LIFE044_SAFE_BOUNDARY_INPUT_ABSENT, остановил Synthetic и штатно завершил own JVM.
Первая quiet запись в сохранённых кадрах находится через51.4393965с от первого
кадра, уже после окончания input wait; она не даёт права переиграть departure.
AWAY_ARRIVED/away frames0. Новый trace не воспроизвёл состояние historical1189;
его actual owner key/seq/expiry по-прежнему UNKNOWN. Causal gameplay RED не получен,
gameplay patch/full G4 не выполнялись; новых попыток нет.

Новый run conservation отдельно PASS:40 lifetimes terminal,4182 owning commits,
48/48 native witnesses,25/25 stopped canonical profiles; FULL_SAVE/GLOBAL_DRAIN/
NATIVE_BOUNDARY_INTERVAL/FINAL_STOPPED_CUT true, RUN_END failure empty/dropped0.
Исходный conservation044 strict FAIL не переписывается и не переносится в PASS.
Exact источники/пины: evidence/ADDITIONAL_SOURCE_RESULT044.json и ADDITIONAL_FINAL044.json.
