# Выбранное решение: исправить наблюдателя, а не сохранять каждый шаг

## Owned store — 02.10.2026, текущая production integration gate

Доказанный RED: настоящий NORMAL native class25/race1/level7 Player; ARRIVAL_AFTER_STORE, CLEANUP_AFTER_STORE, CLEANUP_CAPTURE_COMMIT и ARRIVAL_MUTATION оставляют старую MATERIALIZED farm projection против новых canonical vitals/XYZH. MP/maxMP fixture126 отличаются от PLAY169; HP133/185, CP7/92 →185/185,54/92 и обе XYH пары воспроизведены. Это доказывает общий split writer, но не выбирает единственный исторический producer352.

Реализованный протокол; production integration явно разрешена пользователем 02.10.2026:

1. `PhantomBackgroundTransaction.prepareOwnedStore`: profile/goal/component/canonical/items/skills locks; bounded `background.owned-store` intent и BG VERIFY_PENDING в одном commit; canonical не меняется. Intent хранит old payload hash/state, exact pending rowVersion, BEFORE/AFTER snapshots, target и materialization epoch.
2. `Player.OwnedStoreSnapshot`: native storeCharBase использует immutable vitals/XYZH/progression/class/race; base и active subclass progression сохраняются раздельно. Inventory проходит штатный flush до PREPARE, затем один immutable runtime witness сравнивается с locked canonical rows; owned store не повторяет inventory flush. Внешняя native item mutation остаётся typed conflict, generic item locks не добавляются. Обычный Player store без callback имеет прежнее поведение.
3. `finalizeOwnedStore`: exact payload/version/identity/epoch/full skill hash; AFTER → target и удалить intent атомарно. BEFORE во время live finalize отклоняется. Restart coherent BEFORE → old side; AFTER → intended; neither → typed OWNED_STORE_CANONICAL_NEITHER / INCONSISTENT, receipt retained. Exact BEFORE с уже persisted native level-up skills допускает только absolute retry attested AFTER: exact skill/inventory facts, higher intended level, authoritative auto-get tree, identity lease и отсутствие обеих World maps/AutoSave owner; UPDATE progression/vitals/XYZH и maxima, strict reread и finalize в одной transaction. Rewards/items/skills/clock/receipts не переигрываются, durableMatches не ослаблен.
4. Guarded TEST `09-owned-store-before-after-neither-and-snapshot` PASS: normal arrival/cleanup, before/after store restart, rollback finalize, immutable snapshot mutation, partial canonical fail-closed, stale epoch; receipts/progression/items не переигрываются.

Интеграция: `PhantomBackgroundService.afterPlayerLoad` устанавливает per-Player owned callback; beforeStore проверяет exact identity lease token/World/autosave/service owner, адресно загружает goal/state и PREPARE, afterStore выполняет FINALIZE. ARRIVAL передаёт captured snapshot через ограниченный per-profile context; CLEANUP задаёт target READY/DEAD. Native max refresh и другие reachable native stores используют тот же callback. Owner/goal guards срабатывают до canonical store; flush native inventory отдельно attested до PREPARE. Целевые изменения: Player.java, PhantomBackgroundService.java, existing native TEST и LocalPlay journal/configuration hook. Никаких historical/ecology algorithm changes.

Автоматический approval review дважды отклонил применение callback к production lifecycle: возможные store-path failures/stuck MATERIALIZED и недостаточно доказанный blast radius. Отклонённые patch не применялись. После explicit consent пользователя интеграция подключена; focused regression gates продолжаются до deploy. Pending receipt разрешается bounded при повторном store под тем же owner/epoch; completed или original BEFORE после commit uncertainty узнаётся по exact facts. Если cleanup завершает старый pending receipt, далее выполняется один свежий owned cleanup capture/store. Unsupported goal при absent background сохраняет прежний native-only lifecycle.

`LocalPlayPhantomStoreJournal` подключён как disabled-by-default private synchronous rotating journal (3×1MiB, no thread/timer/scan/endpoint). Он пишет runtime before/after, receipt BEFORE/AFTER, full skills hash, verified result projection/digest и rejection reason. Private directory необходимо provision/проверить при deploy. Position9/9, historical44/44, linked/native Pilot10/10, observability12/12/Pilot6/6/offline runner/clean-build PASS. Historical ensureBaseline интегрирует только retained-owner VERIFY_PENDING retry для того же PENDING goal/revision; awards/planning не меняются. Deploy/post-fix observations ещё required. PLAY352 не recovery/quarantine/skip; actual synthetic остаётся2/5, real arm отсутствует, M2 не начат.

## 1. Источник положения
Оставить старый `OperatorLocalityTarget.committedPosition` и publisher с их прежним
смыслом. Добавить только для M1 адресный observer API в PhantomSystem:

`Optional<OperatorM1TargetSnapshot> operatorM1TargetSnapshot(long profileId)`

Снимок содержит profileId, objectId, materializedAtNanos, sampledAtNanos,
committedPosition/sequence, observedPosition, positionSource и worldPresent.
Для переходного/неполного состояния observedPosition может отсутствовать;
это TRANSITION/UNAVAILABLE, а не COMMITTED по умолчанию.

Источник LIVE допустим только при согласованности materialization snapshot, реального
World Player, objectId, headless identity и состояния ACTIVE. Проверить стабильность
materialization epoch до/после чтения. Не держать монитор через pathfinding или SQL.
При одновременных commit и materialization сначала разрешить LIVE identity; старый
COMMITTED_ANCHOR_CHANGED не должен отвергать уже проверенный живой объект.
Если менеджер сообщает lifecycle-переход, повторить только лёгкое чтение в пределах
3 секунд. Если объект пропал после continuity lock — это уже проверяемое событие,
а не повод снова считать его STORED-кандидатом.
COMMITTED допустим, когда live ownership действительно отсутствует и запись STORED.
Координаты в памяти читать один раз в локальную immutable value, не по три getX/getY
в разных ветвях одного результата. Использовать доступный native location snapshot;
при отсутствии атомарного snapshot явно не заявлять атомарность чтения движения.

Чистые решения вынести в небольшой `localplay/LocalPlayM1Observation.java`:
выбор LIVE/COMMITTED/TRANSITION, сравнение observation/epoch, проверка короткого
M1 transport ticket и условия смены фаз. Этот helper НЕ сервис: не владеет Player,
не имеет executor, глобального индекса, БД или собственного игрового clock.

## 2. Один выбранный профиль и два вида доказательства
INITIAL использует ограниченный in-memory список кандидатов в существующем
M1_TARGET_SEARCH_RADIUS, максимум 8 проб маршрута. Источник положения для каждого
кандидата и подсчёта когорты — observed position, а не committed для уже live.
Получить список один раз, ранжировать по реальному расстоянию/id, не использовать
последовательный afterProfileId как замену сортировке по расстоянию.
Факты календаря/READY и busy читать из существующих in-memory API.
Unknown/canonical-conflict кандидат не обходится; исключение фиксируется явно.
Не подбирать только history=COMPLETE: recoverable pending — нормальный кандидат.

Предпочесть естественный STORED_START с календарным окном >=240 s и природной
когортой >=4. Не дематериализовывать уже живого бота ради получения STORED_START.
Если есть лишь EXISTING_START, корректно провести наблюдение, но newMaterialization
останется NOT_OBSERVED: автоматического полного M1 GREEN нет. Не скрывать эту ветку.
Проверка выбора before first movement может выбрать другого кандидата из той же
ограниченной страницы, но после начала approach profileId фиксирован.

Первая фиксация objectId недостаточна: id персонажа может сохраняться при
пересоздании. Continuity identity = (profileId, objectId, materializedAtNanos).
Epoch передавать строкой, не через JSON double и не вычислять заново из UTC-age.

## 3. Расширить существующие операции, не делать новый Pilot
`PREPARE_M1_ENVELOPE` получает ограниченный аргумент stage:
INITIAL (по умолчанию), APPROACH, LEAVE, RETURN.
Все non-INITIAL операции работают только с ранее выбранным профилем в этом runId.
Не добавлять новые operation enum или обход consent.

INITIAL выбирает снимок и исходный внешний пункт/путь. Подготовленные XY
привязаны к observation revision и positionSource; вернуть selectionKind.
Один разрешённый начальный перенос TestAdmin во внешний пункт остаётся setup,
но setup нельзя засчитывать как естественный подход/prewarm.
После переноса повторно проверить, что точка действительно вне prewarm/visibility
относительно АКТУАЛЬНОЙ цели. При гонке до approach разрешён один переподбор внешнего
пункта того же профиля; не фиксировать ложный OUTSIDE.

APPROACH не телепортирует. Адресно перечитывает выбранную цель и строит путь от
ТЕКУЩЕГО TestAdmin к её observedPosition через существующие nativePath/GeoEngine.
Проверки couldKnow, distance и clientVisible используют тот же источник.
Не делать полный perimeter search для каждого такого запроса.
Native путь: максимум 64 точек и 10000 единиц, прежние native геопроверки сохраняются.

LEAVE выбирает grounded пункт вне prewarm и couldKnow относительно live снимка.
RETURN выбирает grounded пункт встречи возле ТЕКУЩЕГО live Player в том же instance,
с подтверждённой native видимостью/доступностью. Это observer transport, не путь бота
и не доказательство первой материализации. Не использовать старый route.start для ухода.
При смене instance не переходить самовольно в другой instance.

## 4. Разрешение переноса TestAdmin
Существующий TELEPORT_SELF не разрешает любую live/start точку: это надо исправить
в одном проходе с LEAVE/RETURN, а не обнаружить следующим RED.
Сохранить старые origin/candidate/nearby условия для обычных запросов.
Для M1 сервер выдаёт один pending ticket:
(token, runId, actorObjectId, profileId, objectId, materializedAtNanos,
 purpose LEAVE/RETURN, immutable destination XYZ+instance, expiresAtNanos).
TTL 15 s; один ticket в памяти; одно успешное использование; новый заменяет старый.
Ticket выдаётся только в согласованном M1-сеансе, после проверки позиции сервером.
TELEPORT_SELF принимает m1Token и точные XYZ. При наличии токена сначала валидировать
все binding/TTL/назначение; неверный токен не должен проваливаться в обычный candidate fallback.
В execute передать реальный request.runId в prepare/teleport, не доверять args.runId.
Никаких persistent allowlist, записи consent-файла или глобального bypass.

Перед использованием ещё раз проверить identity/epoch/instance и смысл назначения:
LEAVE всё ещё вне prewarm; RETURN всё ещё в разрешённой близости актуального Player.
При устаревшем ticket допускается одна повторная подготовка той же фазы; дальше
SCENE_INVALIDATED с фактом, не многократные телепорты. Принудительно двигать/останавливать,
лечить, воскрешать или закреплять фантома средствами Pilot запрещено.

## 5. Runner — реакция на текущее состояние вместо маршрута к старому адресу
Существующий Run-M1RuntimeHandoff.ps1 сохраняется единственным runner.
Заменить статический foreach(route) и Follow-Native на отслеживаемую фазу:

OUTSIDE → APPROACH → OBSERVE(40 s) → LEAVE → ABSENT(15 s) → RETURN → RESTORE.

Во время APPROACH:
- сначала проверить текущую видимость выбранного Player;
- при LIVE прицеливаться в live, при STORED — committed;
- двигать только TestAdmin штатным MOVE_SELF: горизонтальный шаг <=300, |dz|<=150,
  на каждом фактическом шаге действуют существующие Geo/instance/player guards;
- полилиния пути может оставаться прежней, пока конечная цель сдвинулась <=256 и
  регион/источник не сменились. Не пересчитывать её после каждого getX;
- обновить путь при смене источника или смещении цели >256/смене региона; максимум
  6 перепланировок, интервал между ними >=2 s, общий deadline approach=120 s;
- немедленно завершить подход при worldPresent && clientVisible и расстоянии <=900,
  не требовать попасть в старую точку/центр персонажа. Не телепортировать для первого контакта;
- ACCEPTED от MOVE_SELF — не факт движения. Проверять displacement/достижение шага;
  отсутствие движения не должно бесконечно возвращать успешный WAIT. Один повтор
  геопути расходует тот же replan budget, далее typed approach failure;
- переходный native teleport не читать как окончательное отсутствие: ограниченный
  transition wait. Исчезновение/смену epoch при активном continuity lock не прощать.

Snapshot prewarm и initialMaterialized записывать ДО первого clientVisible. Если впервые
видимый Player появился в том же снимке и более ранней проверки до visibility нет,
не выдумывать точный порядок: отметить PREWARM_TIMING_UNPROVEN.
Для STORED_START пропуск срока материализации при реальном входе в couldKnow — RED.
Для EXISTING_START birth-before-approach не выдавать за этот тест.

firstCouldKnowUtc и firstClientVisibleUtc — разные поля. Первое не заменяет второе.
Флаг clientVisible — серверное eligibility, не доказательство рендера кадра клиентом.
Отчёт должен использовать уровень CONNECTED_SERVER и не обещать визуальное отсутствие
flicker на экране без соответствующего наблюдения.

Continuity lock включается после первого local-demand+worldPresent или контакта;
до него обычное освобождение далёкого PREEXISTING объекта не считать доказанной
поломкой retention. Уже полученные факты не стирать и ветку не переименовывать в NEW.
После lock сравнивать epoch до конца короткого возврата. При legit soft eviction из-за
capacity pressure это не VISIBLE_DISAPPEARANCE, но no-churn сценарий остаётся NOT_PROVEN.

## 6. Полезная жизнь и малая стоимость снимков
В SNAPSHOT_M1_ENVELOPE добавить includeCensus: runner передаёт false на шаговых снимках.
Чтобы не сломать старых callers, отсутствие аргумента сохраняет старое поведение.
В лёгком снимке только выбранная identity/location/epoch, cached readiness, native
moving/attacking/casting/autoPlay, targetId и простые target flags. Не запрашивать
ordinaryGoal из БД и не обходить мобов для каждого poll.
Census запросить на входе в OBSERVE, примерно через 20 s и после RETURN; существующие
страницы ограничены cap128/24, максимум 6. Неполную pagination не выдавать за полный census.

В течение 40 s наблюдения выбранный фантом должен выполнить native движение
с изменением координат и/или боевое действие по подходящему монстру. autoPlay=true,
смена goal, self-heal, travel.reason без displacement и движение TestAdmin не являются
достаточным результатом. Когда бот находится в путешествии, честно написать TRAVEL,
а не заявлять урон. Для M1 farm observation нужен реальный attack/cast по монстру
выбранного персонажа либо другого явно названного обычного участника той же сцены.
Не присваивать выбранному игроку чужой damage. Не подключать постоянный combat listener.

При уходе начать отсчёт 15 s лишь после снимка, в котором observed live цель реально
вне local/prewarm/visibility относительно TestAdmin. При неожиданном сближении
одна повторная LEAVE-подготовка допустима; нельзя засчитать отсутствие, которого не было.
RETURN использует ticket к актуальной цели и снова требует clientVisible, прежний epoch.
Весь absent+return должен уложиться в существующую 60 s soft-grace; ограничить 45 s,
не менять сам grace и не добавлять test-only pin.

## 7. Deadline, ошибки и cleanup
Monotonic Stopwatch для duration; UTC только для подписей и календаря.
Read-Field должен поддерживать ISO/DateTime/DateTimeOffset/null; null важного поля —
typed failure, не превращать его в координату 0. Не менять deadline при смене phase,
кроме отдельного защищённого cleanup окна 45 s.
Считать каждый mailbox-запрос, включая pages/status/move; max400, work<=368,
32 reserve на stop/restore. Проверить headroom до arm; не очищать действующий lease.

Cleanup allowlist runner допускает STOP_MOVE, TELEPORT_SELF к origin и STATUS;
не запрашивает новое PREPARE или полный census.
finally разделить на независимые вложенные try/finally: попытка остановки движения
и возврата origin → сохранение результата/TSV → Stop-LocalPlayPilot. Ошибка записи
лога/TSV не должна пропустить stop. Primary failure сохранять отдельно от cleanup
failures; не терять первоначальную причину при ошибке возврата. `_origin` — clone исходного location, instance
не захардкоживать в cleanup. Подтвердить REAL_LOGIN и позицию через STATUS.
При UNCERTAIN сначала штатно остановить текущий run, не повторять неизвестно выполненную
мутацию и не заполнять restore=true. При утраченном consent авто-restore может быть
невозможен: сообщить это явно, без обхода ограничений.
Stop-LocalPlayPilot останавливает run, но ARMED_IDLE может остаться. OFF писать только
после действительного штатного отзыва. Не вводить `.playtest off` за пользователя.

## Synthetic human lane (2026-10-01, согласованный scope)

Один обычный Player.load configured character, GameClient=null, без headless sink.
LOCALPLAY_TEST_HUMAN lease берётся до load; real login при этом owner отклоняется
до legacy double-login cleanup. Native online/spawn естественно проходят существующий
World human supplier. Runtime не получает новый gameplay policy или scheduler.

LocalPlaySyntheticHumanService использует существующий Pilot poller и отдельный
private mailbox. EnableLocalPlaySyntheticHuman по умолчанию False; exact objectId/name
задаются private ini. Owned manifest/hash/PID/incarnation и ACL проверяются до START.
START только по private control, server startup человека не создаёт. Lease, World,
autosave и отсутствие Phantom profile проверяются до load и повторно после lease.
Load использует существующий PopulationLoadSuppression. Origin/vitals сохраняются
до spawn. STOP/watchdog/shutdown выполняют owned cleanup: отмена действий/invite,
restore instance/location/vitals, единственный storeMe, deleteMe, World/autosave checks,
release lease. Повторный STOP идемпотентен; неполный cleanup удерживает ownership.

Run-M1RuntimeHandoff -ActorMode Synthetic меняет только START/transport/STOP/owner
и evidence grade. Phase engine и семь matrix guards общие. REAL_CLIENT realClient()
и arm/consent неизменны. Synthetic grade = SYNTHETIC_SERVER_GREEN, M1 остаётся OPEN.
Максимум пять синтетических runs; новые UUID и bounded mailbox/deadline/heartbeat.

INITIAL имеет адресную read-only проверку attested legacy quarantine: exact profile,
character, background payload/state/rowVersion и canonical witness. До movement
допустимы максимум восемь KNOWN_PREFIX_FAIL_CLOSED skips; unknown INCONSISTENT = RED.
Quarantine V2 private: ровно42 exact pre-9050 durable/canonical пары, pinned SHA,
sorted unique ids; historical writer/authority UNKNOWN допустимы только для этого
read-only исключения из evidence. Recovery и production данные не изменяются.
Любой новый post-9050 unknown drift останавливает synthetic loop до root cause.

Bounded exception >10 файлов: единый LocalPlay lane затрагивает config/service,
identity arbitration, private tooling/shared runner, focused tests и текущие docs.
Новые task/stage/proof, gameplay core, schema, caps и чужие SQL/EOL исключены.
