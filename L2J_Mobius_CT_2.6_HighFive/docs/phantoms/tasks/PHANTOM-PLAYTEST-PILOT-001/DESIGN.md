# Дизайн: локальный пилот, не вторая система ботов

## Выбранный подход и границы

Выбран file-mailbox + ограниченный server action adapter над уже подключённым Player. Альтернативы отвергнуты: GUI-робот хрупок и зависит от окна; фальшивый GameClient нарушает текущий архитектурный контракт и ничего не доказывает о настоящем клиенте; универсальный HTTP/admin endpoint добавляет ненужную поверхность доступа. Само серверное управление здесь разрешённая тестовая функция владельца LocalPlay.

Один экземпляр сервиса на GameServer, один пилот, один исполняемый сценарий. Никаких потоков на фантома. Не превращать пилот в планировщик всей игры. Poller/очередь ограничены; файловый I/O не помещать в hot path игрового пакета и не выполнять под lock Player. Нативные действия выполняются сериализованно с повторной проверкой session generation непосредственно перед side effect.

## 1. Consent и lifecycle

Состояния: `DISABLED → WAITING_ARM → ARMED_IDLE ↔ RUNNING → ARMED_IDLE`; `OFF/EXPIRED/DISCONNECTED/RESTARTED` отзывают полномочия. `stop` завершает исполнение; `off` дополнительно снимает consent/idle exemption. Список игровых проверок задаёт runner; наличие согласия не запускает действия само по себе.

Prepare создаёт одноразовый непредсказуемый arm nonce (не меньше 128 бит), expected character name, process incarnation, expiry (например 10 минут на arm). Запись только в private runtime. `.playtest arm` принимает nonce лишь от online, IN_GAME, non-headless Player с настоящим текущим GameClient и ожидаемым именем. После проверки атомарно потребляет nonce и фиксирует account/objectId/session generation. Не использовать OR-account-wide QoL predicate как достаточную авторизацию.

Consent по умолчанию 120 минут, максимум 240; время согласия отдельно от времени исполнения сценария. Operator heartbeat одного run имеет короткий watchdog (например 30 секунд); потеря runner прекращает действия и освобождает execution ownership. Согласие не продлевается автоматом. Для elapsed deadlines использовать монотонное время; для документов — UTC timestamp. Разрыв TCP, смена персонажа, новый GameClient даже того же account/objectId, stop server и process restart немедленно делают старые запросы недействительными.

Обычные значимые действия пользователя — движение/атака/каст/ручной gameplay chat — должны preempt активный run. Не принимать за ручной ввод пассивные ValidatePosition/keepalive и ожидаемые native ответы на телепорт; иначе пилот будет сам себя прерывать. `stop/off` всегда приоритетнее очереди. Cleanup нельзя выполнять под взаимно инвертированными network/player locks.

## 2. Локальный транспорт

Предлагаемый каталог: `artifacts/local-play/runtime/playtest-pilot/` с `inbox`, `processing`, `results`, `journal`. Это runtime-only; task reports — отдельно под docs.

Протокол: небольшой versioned XML с безопасным JDK parser (DTD/XXE/external access выключены). PowerShell использует XML APIs, не строковую конкатенацию непроверенного текста. Request содержит protocolVersion, requestId, sessionId, monotonically increasing sequence, deadline, operation из enum и типизированные аргументы. Client/character identity берётся из lease, не из произвольного request.playerId.

Публикация atomic temp→rename; reader игнорирует .tmp. Имена файлов только UUID/sequence по whitelist, пути из payload не принимаются. Проверять canonical root, symlink/reparse escapes. Windows ACL private-каталога — только текущий пользователь и нужные системные principals; при невозможности безопасно ограничить права fail closed, не включать Everyone write.

Границы: один run; не более 64 KiB на request, 32 pending requests, 128 steps на сценарий; отдельный hard runtime budget сценария (по умолчанию до 10 минут, максимум 20 для этой версии). Удержание journal/results ограничить количеством/размером. Удалять только собственные завершённые private records по retention, не произвольные файлы и не DB.

Claim-before-execute + result journal: повторный requestId возвращает прежний результат, не повторяет side effect. После crash между исполнением и подтверждением — `UNCERTAIN`, а не автоматический replay. Requests не переживают process/session incarnation. Не обещать exactly-once внешнего side effect без transactional доказательства.

## 3. Минимальный полезный набор действий

| Действие | Контракт |
|---|---|
| STATUS / CAPABILITIES | Реальные координаты, instance, world/client/session identity, party, HP/MP, cast/move state, supported action list; никаких игровых изменений. |
| SNAPSHOT_PHANTOMS | Bounded snapshot только выбранных IDs: admission/materialization/Goal/candidate/last result, без signal/materialize. Не сканировать 10k каждый тик. |
| TELEPORT_SELF | Позиционирование только пилота. Перед/после записать координаты; native teleport и подтверждение завершения. Не насильно менять XYZ для мнимого успеха. В этой версии — только существующий текущий instance; не вход в чужой instance/raid. |
| MOVE_SELF / STOP_MOVE | Native движение к проверенной доступной близкой точке; обычные ограничения/геодата; verify arrival, timeout и отмена. Без teleport fallback. |
| SIT / STAND | Native Player действия с обычными ограничениями; проверить фактическую позу. |
| SELECT_TARGET | Только объект из разрешённого bounded scenario roster, текущий instance и валидная доступность. |
| SAY | GENERAL/WHISPER/PARTY от пилота через общий native validation/dispatch path; обычные mute/channel/length/filter/event/blocklist условия. Не напрямую ConversationService.plan и не только отправка CreatureSay. Начальные '.'/'/' как произвольные команды запретить. |
| PARTY_INVITE / PARTY_RESPOND / PARTY_LEAVE | Native PartyInvitationService. Для respond — точная pending invitation identity. Не addMember/setParty и не автопринятие со стороны бота. Не выгонять чужую реальную пати. |
| ATTACK_NPC | Native запрос атаки обычного доступного моба; только явно разрешённый тестовый target. Игроки, эпики, guards и raid NPC запрещены в этой версии. |
| CAST_LEARNED_SKILL | Только фактически выученный навык; native MP/cooldown/range/target checks. Только self, согласованный союзник или разрешённый mob. Никакого прямого изменения HP/баффов. |

Иные операции возвращают `UNSUPPORTED` без эффекта. MAIL/BUY/SELL/quests/instance-bypass/admin/eval не включать «на всякий случай». Структура adapters должна позволять позднее добавить конкретную разрешённую команду без переделки всего пилота.

SAY требует особого внимания: существующий Say2 выполняет validation, mute, filters/events и openClientDispatch перед channel handler. Извлечь минимальный общий player-chat ingress при необходимости; не копировать сокращённую реализацию, обходящую эти gates. Существующий путь игрока не ухудшить. Внутренний пилотский запрос не является реально полученным сетевым пакетом: маркировать `driver=LOCALPLAY_PILOT` в evidence, сохраняя обычное server-side событие общения. Не добавлять боту тестовый флаг, который заставляет отвечать пилоту.

## 4. Результат действия и сценария

Различать `ACCEPTED/PENDING` и наблюдённый `SUCCEEDED`. После движения проверять координаты/завершение; после party invite — существование точного native invitation, затем отдельно принятие/отказ; после chat — native dispatch и отдельно ответ фантома; после каста — фактический native outcome. Timeout отдельной возможности бота не должен бесконечно удерживать runner.

Поля result: requestId, session/runId (без секрета), action, start/end UTC, native outcome/reason, before/after, actor objectId, target identity, evidence level, driver, `HARNESS_RESULT`, `GAMEPLAY_RESULT`, failure classification. Failure types: HARNESS_BUG, ENVIRONMENT_BLOCKED, GAMEPLAY_GAP, EXPECTED_REFUSAL, NOT_OBSERVED, UNCERTAIN, CANCELLED, TIMEOUT.

`GAMEPLAY_GAP` не закрывать пилотским обходом. Например REAL invitation может штатно дойти, но бот без JOIN goal его отложит; это корректно измеренный gap, не разрешение форсировать приём. Доказанная работа транспорта не повышает VISION_MATRIX gameplay-строки.

## 5. Восстановление и независимость

Сохранять исходные координаты/instance/позу/target и принадлежность к party до run. Cleanup возвращает только безопасно обратимые, принадлежащие сценарию изменения. Не делать rollback inventory/XP/денег/мира прямым SQL. При смерти, смене instance, сетевом разрыве либо ручном вмешательстве не тянуть персонажа насильно обратно. Party leave только если run сам создал/вступил в неё и membership всё ещё соответствует ожиданиям.

Стартовать smoke предпочтительно в безопасной зоне отдельным персонажем без party/store. Не выполнять случайный PvP и не трогать других REAL игроков. При невозможности безопасного cleanup — отчёт, остановка действий; никогда не «починить» базу вручную.

## 6. Доказательства

- `UNIT/CONTRACT`: deterministic guards/lease/protocol/таймеры. Не REAL клиент.
- `NATIVE_TEST`: штатные Player/services на guarded TEST DB, без fake GameClient; реальный packet/client rendering не доказан.
- `CONNECTED_SERVER`: настоящий GameClient подключён, пилот действует над его Player и сервер подтверждает эффекты. Пилот, не ручной сетевой ввод.
- `CLIENT_OBSERVED`: человек действительно увидел результат в игровом клиенте. Только по такому свидетельству, не по отправке пакета.

Пилот снижает число ручных действий. Он не может гарантировать корректность картинки, текста «как живой» или полного protocol login своим внутренним вызовом.
