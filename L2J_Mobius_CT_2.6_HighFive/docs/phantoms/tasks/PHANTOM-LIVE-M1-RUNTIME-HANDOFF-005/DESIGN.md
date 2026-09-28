# DESIGN — выбранное решение и точные точки изменения

Это проект исправления по проверенным исходникам, а не отчёт об уже исправленном runtime. Подтверждённые дефекты отделены от неподтверждённой причины конкретного profile 5079.

## D1. Развести calendar presence и техническую готовность

Изменять `population/PhantomPopulationManager.java::processReadySchedule`, `evaluateScheduleLocked` и публикацию presence; `PhantomPresenceRegistry` остаётся источником логического online, а не состояния JDBC.

Сейчас выполняется:
```java
entry._effectiveState = ecologyNotReady ? SLEEPING : effectiveStateLocked(entry);
_presence.schedule(profileId, effective);
```
Изменение:
- Сначала получить настоящий результат существующего calendar evaluator с nextBoundary. READY/managed/не-retired профиль считается calendar-online при допустимом несонном calendar state.
- `_presence.schedule` получает calendar state. Техническое отсутствие готового baseline не меняет календарь и не стирает human-local demand.
- `_effectiveState` и scheduler по-прежнему могут оставаться SLEEPING/DEFERRED до готовности. Важно не переименовать этот technical state в calendar-online во всех потребителях.
- Настоящий schedule OFFLINE и retirement сохраняют старую семантику; восстановление readiness само по себе не пробуждает реально offline профиль.
- `presenceReason` и `readinessReason` показывать раздельно. Не добавлять рекурсивный readiness callback в topology query: online-предикат должен оставаться дешёвым memory-only lookup.

В `PhantomHumanLocalityControl` сохранить фильтр online-before-limit. Локальный спрос на scheduled-online профиль разрешён при pending readiness; он не является разрешением materialize до commit. Если присутствует временная публикация `_local = Set.of()` в начале refresh, собирать новый immutable set локально и публиковать одной заменой, без промежуточного ложного «человек ушёл».

## D2. Reconciliation как заявка, а не JDBC внутри scheduler

Использовать существующий `PhantomPopulationEcologyService`, его `_entries`, `_due/_queued`, `_materializationDue/_materializationQueued`, `Entry._claimed`, PersistencePort и HistoricalPort. Не создавать новый catch-up engine.

Добавить в этот сервис memory-only due view и request API. Названия предлагаемых НОВЫХ методов: `requestMaterializationDue`, `requestBackgroundDue`, `dueSnapshot`. Переиспользовать текущий DueReconciliation/добавить совместимые поля, а не новый протокол во всём проекте.

Due view содержит минимально: profile, requested horizon minute, committed cursor, initialCatchupComplete, requestPending, queued/running, readiness revision и reason. Счётчики очереди — производные, не самостоятельный источник ready. READY только после успешного canonical commit и проверки cursor/request, не по факту принятия Runnable.

Алгоритм:
1. Под monitor зарегистрировать/обновить demand и дедуплицировать профиль в existing priority queue. Повторный запрос не сбрасывает прогресс, ordinal или deadline.
2. Вернуть immutable READY/PENDING/BLOCKED snapshot немедленно. Не читать DB, не вызывать historical.begin/advance, не ждать Future на scheduler/local-pulse thread.
3. `onPopulationPulse` больше не исполняет historical/JDBC inline. Он учитывает existing pulse budget и dispatch одного bounded drain batch через общий `ThreadPool.execute`; максимум один ecology worker in-flight. Для TEST Dispatcher передаётся через overload, как уже сделано в navigation service.
4. В batch переиспользовать нынешние `process`, `advanceRequest`, `beginNextWindow` и canonical transactions. Все periodic/local/general заявки проходят один claim/budget; не оставлять старый синхронный вход, позволяющий обойти эту сериализацию.
5. Приоритет — local materialization demand; для обычной due-очереди сохранить ненулевую долю существующего бюджета. Не забирать квоту заново на каждый профиль/каждый refresh. Один blocked профиль не блокирует обход остальных записей очереди.
6. При завершении slice обновить snapshot после commit, снять claim в finally, переочередить незавершённую работу. Запуск следующего batch — через существующий pulse; не делать tight self-dispatch loop, заново тратящий бюджет до следующего pulse.
7. Использовать уже имеющийся `PopulationEvents.ecologyFenceChanged(profileId)` для планирования обработки этого же профиля после изменения готовности. Callback вызывается вне ecology monitor. В scheduler нет ожидания этого callback.
8. Добавить stop/drain API для нового worker ownership и подключить shutdown в PhantomSystem: новые заявки прекращаются до закрытия исторического сервиса/репозиториев; запущенный canonical commit заканчивается; незавершённый drain не объявляется STOPPED.

При настоящем OFFLINE/уходе наблюдателя убрать только срочный materialization demand. Не откатывать уже выполненный commit и не присваивать current cursor без симуляции. Общая background-очередь может закончить законную работу.

При DB/worker stall pending нельзя выдать за готовность. Один worker не обещает преодолеть зависшую DB; цель — не блокировать им scheduler и готовые локальные профили. Исключения сохранять с typed reason; не проглатывать их и не делать unbounded retry.

## D3. Замкнуть все входы, а не одну новую обёртку

В `PhantomSystem.start` перевести следующие production-входы на D2:
- `_reconcileMaterializationActivity.install(...)`: calendar/spatial demand → request → snapshot gate;
- `_backgroundService.installPeriodicFarm(...)`;
- productionWorkSink background-due ветку;
- `PhantomPopulationManager.controlPulse` через обновлённый `onPopulationPulse`.

Пока pending, нормальная background mutation/normal materialization не исполняется параллельно с catch-up. Existing historical/lifecycle fencing и per-profile identity/action leases не убирать. Отдельно проверить окно между постановкой заявки и durable beginRequest: readiness view уже PENDING, до того как worker начал JDBC. После READY повторные запросы не дублируют XP/вещи и не публикуют повторно advancedIntervals как новое выполнение; результат commit учитывать ровно один раз по revision/receipt.

Если после catch-up изменилась committed position, повторно вычислить locality по новой позиции до materialization. Старое решение «рядом» не является правом появиться где угодно. После фактической материализации native live position/retention имеют прежний приоритет.

`PhantomReconcileFirstActivityPort` должен возвращать причину policy-defer из того же due view, а не только false. Предпочтительный минимальный API: reason в `TransitionOutcome` с compatibility constructor/factories; сохранение последней причины в slot/snapshot. Не менять смысл SUCCESS/DEFERRED/TRANSIENT_BLOCK/RETAINED_FAILURE и не обнулять backoff настоящей retained/identity ошибки. Не создавать новый универсальный diagnostics service.

## D4. Корректно потреблять протокол navigation

Файлы: `background/PhantomVisibleFarmTravel.java::walk/clearRoute/retryOrFail/fail`, `navigation/PhantomNavigationService.java::Submission/processDirect/consume`.

Проверенный случай: `_queue.offer` вернул false → `completeLocked(..., false)` → REJECTED + ненулевой requestId + immediateResult. Ожидание consume для такого id неверно. Обычные немедленные завершения также могут сохраняться в completed; НЕ считать все immediateResult «потерянными».

После submit:
```text
immediateResult есть → обработать один раз сейчас, убрать возможный retained duplicate;
ACCEPTED + id > 0 + нет immediateResult → ждать только этот pending id;
REJECTED/COMPLETED без обязательного результата → typed protocol failure, не вечное pending.
```
Один обработчик terminal result используется для immediate и consume. При отмене не затрагивать чужой/newer request. Потерянный/evicted terminal result не превращать в бесконечное ожидание: existing deadline и typed terminal reason.

COOLDOWN/QUEUE_BACKPRESSURE/PROFILE_BUSY/SERVICE_NOT_RUNNING/BACKEND_FAILURE — сервисная причина, не доказательство непроходимости anchor. Сохранить bounded retry и прекращение hold; не записывать такой отказ как map-unreachable. NO_PATH/подтверждённая непроходимость — адресное временное исключение данного маршрута, затем существующий goal replan. NO_GEODATA/PATHFINDING_DISABLED — capability gap с явной причиной, а не разрешение идти сквозь стены. ROUTE_BUDGET_EXCEEDED — разбить имеющийся corridor либо выбрать достижимую альтернативу, не увеличить лимиты глобально.

В 004 hold уже ограничен и есть Failure callback. Исправлять этот механизм, не возвращать старое eager indefinite retention. Pending допускает только ограниченный срок ожидания, не считается полезным движением. Общее время Journey и отсутствие физического прогресса — разные условия: длительный нормально продвигающийся путь не должен отменяться лишь потому, что ему исполнилась минута. Использовать существующие progress policy/tracker и верхний общий deadline, не per-profile таймер.

## D5. Один исполнимый route-контракт

Подтверждено: `PhantomGeoValidationRules.route` допускает path.size до 256, а `PhantomNavigationPolicy.productionDefaults` — maximumWaypoints=64; probe использует PathFindingRawAccess/HermeticGeoMovement, runtime — свой adapter/validator. На baseline local straight bound=12000, total route bound=100000. Эти величины не означают неисправность каждого пути, но INVALID runtime-route не становится рабочим из-за старого VALID_PATH.

Выбранное изменение: извлечь общую нормализацию/segment-validation из runtime navigation в небольшой `PhantomNativeRouteContract` в пакете navigation. Core принимает origin/destination/points, текущую policy, проверку сегмента и cancellation/deadline; без DB, Player ownership и своего pathfinder. Runtime остаётся единственным местом выдачи действующего native route.

- Нормализовать старт/повторяющиеся точки и точный endpoint; одноточечный ответ проверять по геометрии, а не отвергать только по длине списка.
- Для сокращения пути допустим лишь shortcut, заново проверенный native GeoEngine; нельзя обрезать список до 64 или соединять точки через стену. При невозможности — явный отказ с причиной.
- Длинный macro route идёт последовательностью коротких разрешённых ног. Делить только по native-подтверждённой геометрии коридора, не по произвольной интерполяции между endpoints.
- Probe/test использует тот же core + production limits для ОТДЕЛЬНОЙ проверки runtime compatibility. Старый геометрический proof/manifest не переписывать задним числом и не объявлять все его edges исполнимыми.
- Динамические doors/instance state перепроверяются в runtime. Статический proof не отменяет runtime collision.

Новый normalizer — не повод регенерировать всю карту. Сначала один общий failing fixture и уже имеющийся native connector/corridor; broad canonical rerun один раз в конце при изменении planner/route contract. Если publish действительно меняется, только generic generator + полный учёт изменённых inputs; ручные spot patches запрещены.

## D6. Проверять не другую систему

Новый итоговый admission snapshot объединяет уже существующие факты: calendarState/online/nextBoundary; readinessReason/cursor/horizon/requestPending/queued/running/revision; humanLocality; last transition reason; native visibility и retention pins. Снимок memory-only: чтение STATUS не запускает catch-up. В runner все обращения используют один согласованный runId; не открывать параллельные диагностические runs.

Pilot использует этот же view при prepare и при каждом переходе. Настоящий OFFLINE, остановка расписания или смена anchor до входа — SCENE_INVALIDATED с точной причиной, не «спавн сломан» и не GREEN. Технический pending при calendar-online — обязательный сценарий готовности, его запрещено отфильтровать только ради успешного теста.

Никаких глобальных обещаний по 85/85. Требуется связь между тем, что проверил validator, тем, что исполняет server, и тем, что увидел один итоговый connected run.
