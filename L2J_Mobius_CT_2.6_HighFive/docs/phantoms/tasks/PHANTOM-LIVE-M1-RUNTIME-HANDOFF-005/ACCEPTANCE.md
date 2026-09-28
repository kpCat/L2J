# ACCEPTANCE — три автоматических группы + одна connected-сцена

Это проверки общих механизмов, а не матрица локаций/классов. Existing regression cases сохраняются; здесь нет требования переаудита всего проекта.

## A. Production wiring: schedule → readiness → Player

Один интегрированный fixture в новом `PhantomM1RuntimeHandoffSuite` связывает настоящий PopulationManager, EcologyService, PresenceRegistry, HumanLocalityControl, ReconcileFirstActivityPort и Scheduler. Допустимы управляемые Clock/Dispatcher и приостанавливаемый HistoricalPort/PersistencePort на границе I/O; недопустимы заглушки, просто объявляющие readiness успешной. Отдельный существующий native TEST fixture подтверждает реальные Player identity/materialization/store.

Обязательные варианты в одном aggregate:
- Calendar-online профиль с pending catch-up остаётся логически online, получает local demand, но не materialize до commit. Повторные signals не создают дубликаты работы и не обнуляют horizon/progress.
- Блокируем worker на I/O latch: scheduler/local pulse продолжают отвечать; другой уже ready local профиль обрабатывается. Снятие latch доводит ТОТ ЖЕ профиль до READY и ровно одной normal materialization без нового входа человека.
- Настоящая calendar OFFLINE граница не обходится. Worker может безопасно завершить commit, но stale demand не создаёт online Player; expiry/retirement не теряются.
- Catch-up сменил committed anchor: повторная locality/admission проверка не делает spawn по устаревшему месту.
- Повторная READY-проверка не удваивает награды/advancedIntervals. Stop во время batch не освобождает owner раньше commit/drain и не запускает worker после STOPPED.

Считать выполнением не только счётчик «заявка получена», а конечные committed state + materialization identity. Причины deferred должны различаться, не все быть `ecology.unavailable`.

## B. Navigation: один контракт и реальный consumer

Проверить через `PhantomVisibleFarmTravel`, а не прямой вызов pathfinder в обход него:
- Immediate success, async success и REJECTED+nonzero requestId+immediateResult. Для последнего результат сразу классифицирован, отсутствует ожидание nonexistent completed result; нет fake travel hold на полный deadline.
- COOLDOWN/backpressure не вносит маршрут в map-unreachable список. Подтверждённый NO_PATH освобождает запрос/hold и запускает существующую ограниченную альтернативу, не тот же бесконечный retry.
- Тот же normalized path проходит одинаковую structural runtime validation в runtime/test: одноточечный/многоточечный ответ, повтор старта, несовпадение endpoint, превышение waypoint/range budget и blocked segment. Не расширять ограничения для PASS.
- В guarded native fixture Player физически проходит проверенную ногу, фиксирует arrival и передаёт управление stock AutoPlay. Проверить движение дольше прежнего единого минутного порога управляемым временем: прогресс отличать от застревания.

Для known native connectors использовать существующие данные. Проверка графовой достижимости и actual execution — разные результаты. Сводка 85/85 не заменяет этот consumer test.

## C. Continuity без новых live-минут ожидания

Переиспользовать tests 004: native-visible, REAL-party/action hard pins; recent-human soft 60 s; возврат до expiry сохраняет object/birth; по expiry без hard owner разрешён store; cap pressure освобождает только soft-only. Таймеры — управляемым временем, не минутным sleep. Проверить что новый readiness pending не инвалидирует действующую native party и не запускает historical mutations поверх materialized Player.

Death lifecycle не менять. Запускать существующую death regression только если реально затронут её call path/ownership.

## D. Одна connected-сцена

Подготовка кода/данных/runner — автоматическая, без персонажа владельца. Операции consent-gated Pilot до arm не вызывать. Финальный динамический выбор одной естественной schedule-online когорты и проходимого native маршрута выполняется сразу после arm, до начала измеряемого движения. Не выбирать по заранее полученному удачному результату materialization; pending readiness не отбрасывать. При наличии данных nextBoundary должна покрывать плановую длительность сцены, без изменения расписания. Повторно сверить эти условия при фактическом старте после arm.

Сценарий: outside → обычный approach/prewarm → visible native life → leave около 15 секунд → возврат с той же live identity → автоматический возврат TestAdmin/stop. Не делать owner wait у границы для искусственного положительного prewarm margin. Не увеличивать таймаут до удобного PASS; задержка фиксируется относительно естественного времени подхода и фактической готовности данных.

Записывать на ключевых переходах snapshot D6 и native action/target/position. Census — текущая естественная visible cohort в пределах cap128, с unique profiles, числом eligible и причинами idle; не выдавать 80 строк об одном персонаже за 80 ботов. Не нужны отдельные посещения других рас/уровней.

Обязательное наблюдение: нормальная materialization при актуальном demand, отсутствие visible disappearance, полезное native движение/бой/переход из travel к действию, сохранение identity при коротком возвращении. IDLE с ACTIVE farm goal и повторным failed navigation — RED, не LEGIT_IDLE. Дефицит мобов, смерть/recovery и занятость учитываются отдельно; не требовать, чтобы все атаковали одновременно.

Если когорта слишком мала для оценки массового стояния, указать `COHORT_COVERAGE_INSUFFICIENT`, не объявлять массовый idle закрытым. Цель подготовки — не менее четырёх естественных eligible profiles в одной сцене, но нельзя насильно создавать их или путешествовать по карте ради счётчика. Малый sample — ограничение покрытия, не доказательство сломанного spawn.

Если истинное расписание/anchor инвалидирует выбранный сценарий — завершить с typed причиной и вернуть TestAdmin; не форсировать персонажа online и не заявлять поломку GeoEngine. Без прохождения обязательного connected пункта — не M1 GREEN.

## Итоговая форма

`AUTOMATED`, `CONNECTED`, `NOT_OBSERVED` отдельно. Code SHA != docs SHA != deployed artifact hash. Успех этой задачи означает замкнутый M1 runtime handoff в описанном объёме, а не готовность M2–M10 или доказательство каждой точки мира.
