# Приёмка без ложного GREEN

## Один компактный набор регрессий
Реальные inputs приведены отдельно от synthetic cases в fixtures. Один параметризованный
suite, а не отдельная задача на строку. Обязательные проверки:

| Проверка | Ожидаемый результат |
|---|---|
| STORED без live | COMMITTED используется как прогноз, NEW ещё не доказан |
| ACTIVE, live далеко от committed | Маршрут/visibility используют LIVE; durable запись не меняется |
| Движение во время подхода | Устаревший маршрут обновляется в пределах budget; контакт останавливает подход |
| Меняется committed до появления | Переподготовка версии того же target, не автоматический gameplay RED |
| Объект меняет epoch при том же objectId после lock | CONTINUITY_PLAYER_REMATERIALIZED; id не маскирует пересоздание |
| TRANSITION/неполное World membership | Короткая revalidation, не ложный возврат в COMMITTED |
| regionCanKnow=true, clientVisible=false | CONTACT не PASS; firstClientVisible не заполняется |
| Новая материализация появилась только на первом visible снимке | Не доказан prewarm; не выдавать за заранее появившегося Player |
| Player live с OUTSIDE | EXISTING_START, не доказательство NEW_MATERIALIZATION |
| LEAVE к старому start остался внутри prewarm | Не считать отсутствием; вычислить новый outside |
| RETURN target переместился | Свежий ticket/точка или ограниченная invalidation, не произвольный teleport |
| Wrong token/actor/run/XYZ/epoch/instance, TTL, reuse | REJECTED; обычный fallback не используется |
| DateTime/Offset/ISO/null | Корректная дата либо typed invalid input, без arm/перемещения в offline-test |
| Census отключён | Нет goal/DB/mob enumeration; request/page budget соблюдён |
| Ошибка TSV, snapshot, movement или cancellation | Cleanup/stop вызываются; реальный restore не выдумывается |
| Наблюдаются лишь self-heal/autoPlay=true/смена goal | NATIVE_FARM не PASS |
| Остались unknown histories вне выбранной сцены | Не очищать; записать ограничение без глобального ожидания нуля |

В коротком guarded native TEST: actual Player вне старой точки, тот же observed API,
штатное перемещение, сохранность committed baseline и настоящий путь action guards.
Не выдавать mock observer за проверку всей PLAY-производительности.

## Connected matrix
1. **NEW_MATERIALIZATION:** естественный STORED_START, demand до native visibility,
   тот же профиль появляется раньше входа в couldKnow, без принудительного spawn/teleport
   фантома. При другой исходной ветке или недоказанном порядке — NOT_OBSERVED/UNPROVEN.
2. **CONTACT:** выбранный verified live Player достигает server-side clientVisible
   при подходе TestAdmin, не при teleport к нему для первого контакта.
3. **NATIVE_LIFE:** выбранный Player реально перемещается/действует; обычный фарм
   наблюдается у него или явно указанного участника той же сцены. Самолечение не фарм.
4. **COHORT:** минимум 4 естественно подходящих обычных профиля за три census.
   Если естественно меньше — coverage недостаточен, не спавнить/таскать их для счётчика.
   Не должно быть повторного unexplained ACTIVE_IDLE/failed navigation у eligible участников
   во всём окне без фактического native progress. Причины death/party/store/target-deficit
   записывать отдельно, не прятать в «все активны».
5. **SOFT_RETURN:** 15 s реального отсутствия человека вне текущего prewarm, возврат
   в пределах grace, тот же objectId И materializedAtNanos, снова clientVisible.
   Если pressure честно вытеснил soft pin — это ограничение no-churn теста, не успех.
6. **RESTORE/STOP:** исходная позиция TestAdmin и REAL_LOGIN подтверждены, run остановлен;
   ARMED_IDLE/OFF указаны без подмены. Не скрывать неудачный restore.

Полный итог `GREEN — M1 VISIBLE WORLD COMPLETE (1280)` только при PASS всех шести
пунктов; CONNECTED_SERVER, не заявлять SCREEN_RENDER_PROVEN.
Если только EXISTING_START работает — `PARTIAL — EXISTING_PLAYER_CONTINUITY_PASS;
NEW_MATERIALIZATION_NOT_OBSERVED; M1_OPEN`, не незаметный перенос требований в M2.
Настоящий gameplay RED остаётся RED, но его reproducer и точный boundary включаются
в RESULT этой же задачи; не создавать ещё одну proof-only задачу.

## Производительность
В diff нет нового scheduler, SQL на движение, полной синхронизации topology/world,
увеличения worker/cap. При выключенном Pilot новое наблюдение не вызывается.
В тестах считать количество обращений к наблюдаемому объекту, pathfinding и census;
не использовать только wall-time как единственное доказательство bounded overhead.
Во время сцены снять owned GameServer TotalProcessorTime/PrivateMemorySize64 в начале
и конце и доступные штатные queue/pulse metrics, без нового profiler/агента/нагрузочного
прогона. Это контрольный снимок, не benchmark и не обещание отсутствия любых утечек.
