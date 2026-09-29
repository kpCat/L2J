# Исходные факты

Проверенная ветка: `kpCat/L2J`, `feature/phantom-world`.
Документационный HEAD: `4e461f3fbf15828de9ddf62ff5f0d53574a31768`.
Развёрнутый исходный Java-код по последнему отчёту:
`9e3576311af6ef8c53e662955e2330b7cee00143`.
JAR SHA256: `FA609D7E5E3B8A269500F54D03C339C65C40A560569C7D5927FB0639EC7CD3EE`.
PID из старого отчёта не использовать как текущий: определить owned процесс заново.

PLAY: 1280 READY + 8720 RETIRED. Параметры 1280/64/128/100,
MaxScheduledPhantomProfiles=10000 — вместимость сохранённого населения.
Резерв и его pending histories нельзя менять. После предыдущего run было 13
FAILED_REPLAN_REQUIRED: 12 transaction.item_conflict_canonical и 1 stale.
Это исторический снимок, а не утверждение об их нынешнем количестве.
Неизвестные/canonical-conflict отказы не обходить и не обнулять.

## Последний run
`1ae855b5-db2e-47d0-9eb2-bd1e4fe0cf2b`, 49 снимков.
Профиль 545, objectId 268489445, historicalStatus=COMPLETE.
Уже в OUTSIDE был worldPresent=true. В конце оставался тот же objectId,
regionCanKnow=false, clientVisible=false.
Committed XY=(44126,42751), live XY=(45975,47879).
Точные Z и весь трек в данном пакете не представлены; не выдумывать их.
Исходные файлы, если сохранились, находятся в исходном модуле:
`.phantom-local/m1-005-connected-1ae855b5-db2e-47d0-9eb2-bd1e4fe0cf2b/`.
Их отсутствие не блокирует регрессию на минимальном примере из fixtures.
TestAdmin был возвращён; после пользовательского `.playtest off` Pilot стал OFF.

## Подтверждённые противоречия кода
1. `LocalPlayPilotActions.envelopeRoute()` берёт `target.committedPosition()`;
   `snapshotM1Envelope()` рассчитывает расстояние/visibility по live Player, когда он есть.
2. Runner проходит подготовленный статический маршрут целиком, а Follow-Native
   вызывает лишь после первоначальной проверки visibility.
3. Follow-Native — это TELEPORT_SELF, а не штатное движение. `teleport()` разрешает
   origin, отдельный `_candidatePosition` или ближайшую точку <=2000 при Geo-проверке.
   Подготовленные M1 start/return точки не получают собственное разрешение.
4. SHORT_LEAVE возвращает на старый start независимо от текущего местоположения бота.
5. firstVisibleUtc присваивается по regionCanKnow, а не по clientVisible.
6. Capture вызывает census и его страницы на каждом шаге движения.
7. После первого worldPresent Capture требует неизменности Player даже в OUTSIDE,
   когда наблюдатель ещё не создал demand/continuity-обязательство.

## Важная граница вывода
Расхождение committed/live во время движения само по себе не ошибка сохранения.
`PhantomTopologyPositionPublisher` намеренно публикует подтверждённые позиции.
Последний отчёт не доказывает работоспособность всех боевых действий, отсутствие
утечки памяти, фактическое рисование на клиентском экране или нормальность оставшихся
canonical-conflicts. Предположение «только runner и ничего другого» ещё надо проверить.
