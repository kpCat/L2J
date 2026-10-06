# Независимый read-first: факты и гипотезы

Base0205d04b подтверждён remote. Последний production patch337bfbc — TASK021; дальнейшие commits содержат его артефакты. Основной источник наблюдения — task021/EVIDENCE021.json и RESULT.md, не пересказ.

## Подтверждено

1. Profile110/object268485779/epoch31633062495600: 19 samples, 166.395653s. Goal/runtime30/30; COMMON_GUARDS_CLEAR. Native counters нулевые после baseline; selectedTargetSequence1 уже был до baseline.
2. Decision trace остаётся sequence7 / WAITING_RETRY / attempt4 / step1. ageMillis604 в baseline и167199 в последнем sample. Следовательно, `background.visible.autoplay_running` — последний сохранённый результат, не повторно подтверждённое running() в течение166 секунд.
3. Player: autoPlay=false, casting=true, intention=CAST, target0. REGEN since31636063545700, deadline31756063545700. В последнем sample nativeTime31821565542000 уже позже этого deadline.
4. PlayerNativeEvidence.snapshot()/checkLiveHorizon() делают _overflow=true при просроченной REGEN/COMBAT/phase. Это общий UNPROVEN-флаг. По одному bool нельзя заключить «буфер переполнился».
5. PhantomVisibleAutoPlay.healthy() уже требует player.isAutoPlaying(), оба exact registrations, current goal и owner. Поэтому нельзя исправлять гипотетическое «healthy не проверяет native flag»: оно проверяет.
6. Обе native manager loops трактуют null из PhantomPolicy.acquire как удаление Phantom-регистрации. acquire объединяет several reasons, включая недоступный ActionLease. Причина конкретного stop021 пока неизвестна.
7. PhantomNativeWorkScope.recordFailure() сохраняет failure и переводит OPEN→DRAINING; observationEvidence() требует nativeObservationHealthy. Это законный fail-closed путь; проигнорировать его ради новых counters нельзя.
8. Shutdown: stock JVMs остановились, но8 retained materialization entries и MATERIALIZED marker остались. Это не успешный drain Phantom.
9. Q00255_Tutorial.onKill/qs=null присутствует в runtime report. В этом исходнике конструктор добавляет kill listener к TUTORIAL_GREMLIN18342; выбранная цель110 —20534. Само соседство сообщений не доказывает общую причину.

## НЕ доказано, проверить один раз в начале

A. Worker/registration lock не вернулся или общий scheduler pulse перестал поступать.
B. Actor был отписан после временного checkpoint/admission отказа; native callbacks оставили cast state незавершённым.
C. Native listener/callback exception отравил нужный exact owner; Q00255 может относиться к другому Phantom.
D. Native rewards прошли, но evidence attribution потеряно из-за ordering/неправильного owner. Нулевые counters сами по себе не опровергают сторонний бой.
E. Общая причина stall и8retained entries. Без первой native incident и work snapshot связь предположительна.

## Порядок диагностики, а не бесконечный аудит

1. Читать retained021 raw GameServer logs: first exception/registration stop/native incident ДО возникновения stall, с точным profile/object/epoch и caller.
2. Проверить источник schedule/pre-work/handler completion по актуальному исходнику. Нужны last-start/last-finish/inFlight и thread/lock stack, не очередной общий reason.
3. Существующий native fixture воспроизводит scheduler/adapter + оба manager loops и реальный cast/reward/next-target. Не ограничиваться registration.start()==true.
4. Для найденного producer построить RED. Потом исправить этот producer и связанные continuation края по DESIGN. Не выбрать наиболее удобную гипотезу вместо подтверждённой.
