# Точные точки чтения — baseline d2ae5704031ca85e417c3cb959de47cd72618821

Все пути относительно `L2J_Mobius_CT_2.6_HighFive`. Это карта для адресного чтения, не просьба перечитать весь репозиторий.

## Проверено непосредственно в GitHub при подготовке

| Путь | Что проверено / что использовать |
|---|---|
| `dist/game/data/scripts/handlers/chat/commands/voiced/PhantomStatus.java` | `onCommand`, `localProof`: PersonalCharacterQoL admission, настоящий online/non-headless Player, private LocalPlay marker, одноразовый permit с PID/objectId, native `teleToLocation`. Аналог локального разрешения; не копировать слабую проверку только существования marker как весь security contract. |
| `java/org/l2jmobius/gameserver/qol/PersonalCharacterQoLService.java` | `isPersonalUser`: enabled, не headless, character-id OR account allowlist. НЕ расширять список ради пилота: тот же сервис включает игровые QoL. |
| `java/org/l2jmobius/gameserver/network/GameClient.java` | Связь Player, GameClient, connection state, `onDisconnection`; есть identity lease. Реальный клиент нельзя заменить фиктивным. |
| `java/org/l2jmobius/gameserver/network/Disconnection.java` | Даже constructor выполняет stopAllTasks и снимает ссылки client/player. Нельзя создавать Disconnection «для проверки» или ставить anti-AFK guard после destructive constructor. |
| `java/org/l2jmobius/gameserver/network/clientpackets/ClientPacket.java` | `run` вызывает runImpl; обработка ошибок. Это не API для создания synthetic сетевых пакетов. |
| `java/org/l2jmobius/gameserver/network/clientpackets/BypassUserCmd.java` | `_command → UserCommandHandler.getHandler → onCommand`. Здесь непосредственно flood/mute нет. Найти конкретный handler /loc по регистрации, не смешивать с Say2. |
| `java/org/l2jmobius/gameserver/network/clientpackets/Say2.java` | `runImpl`, `dispatchFinalFiltered`: validation/chat bans/events/filter/item publication; финальный `openClientDispatch` scope вокруг native handler. Для пилота нужен общий безопасный вход, а не прямой CreatureSay. |
| `dist/game/data/scripts/handlers/chat/channels/ChatGeneral.java` | Voiced dispatch выполняется раньше general broadcast; обычный канал проверяет mute/min level и native blocklist. Из этого не следует, что клиент сам не ограничивает ввод. |
| `java/org/l2jmobius/gameserver/handler/VoicedCommandHandler.java` | Регистрация/поиск/удаление handler. Включить новый handler через существующий механизм загрузки scripts, не оставлять незарегистрированным. |
| `java/org/l2jmobius/gameserver/util/FloodProtectors.java` | Раздельные protectors для chat, transaction, mail, item, bypass и др. Не освобождать всё одним `return true`. |
| `java/org/l2jmobius/gameserver/util/FloodProtectorAction.java` | `canPerformAction`: GM bypass, interval/request counters, punitive kick/ban/jail. Прямого типа наказания mute в проверенном switch нет. Это не доказывает источник наблюдённого ограничения. |
| `java/org/l2jmobius/commons/network/Client.java` | Настоящее соединение, send/read, disconnect cleanup. Не подавлять disconnect ради «вечного online». |
| `java/org/l2jmobius/commons/network/ConnectionConfig.java` | Общая network configuration. В прочитанном файле не обнаружена универсальная игровая AFK policy; не делать из этого вывод об отсутствии её во всём проекте. |
| `java/org/l2jmobius/gameserver/GameServer.java` | Реальный startup и загрузка scripts/services. Врезку включения пилота делать после нужной native инфраструктуры. |
| `java/org/l2jmobius/gameserver/config/ServerConfig.java` | DATAPACK_ROOT, конфигурация restart/HWID и др. Проверять effective private config, не менять глобально по предположению. |
| `tools/phantom-local-play/{Start,Stop,Check}-LocalPlay.ps1` | Эти точные файлы присутствуют в tracked tools. Использовать их; не taskkill java целиком. |
| `tools/phantom-local-play/LocalPlay-Ownership.ps1` | Существующий ownership tooling, источник правил current owned runtime. |

## Следующие адресные чтения исполнителя

- `java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java`: invite/respond/leave и pending invitation identity.
- `java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java`: normal delivery observation, существующий scope/registration.
- `java/org/l2jmobius/gameserver/network/clientpackets/MoveBackwardToLocation.java`, `ValidatePosition.java`, `Action.java`: проверить текущие имена и native request path; shared helpers/entry only, не переписывать Player.
- `java/org/l2jmobius/gameserver/model/actor/Player.java`: только вызываемые move/teleport/action/disconnect методы и identity/seams. Не читать весь огромный класс.
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`: только operatorStatus/admission/locality snapshots; не менять gameplay composition ради пилота.
- Existing headless native test fixture: `test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`, launcher и затронутые chat/party suites. Headless evidence маркировать TEST, не настоящий пользователь.
- Регистрация scripts: найти ссылку на `PhantomStatus.class`/его загрузку внутри `dist/game/data/scripts/handlers/` одним targeted search, затем использовать тот же механизм.
- Idle lookup: один bounded `rg` по `gameserver/{network,taskmanagers,config,model/actor}`, `commons/network` и связанным private configs для `idle|inactiv|afk|kick|timeout|disconnect`. Дальше только найденные реальные callers, без поиска всех хроник.

## Предлагаемые новые task-owned пути

Не существующие файлы, а места для создаваемой реализации:

- `java/org/l2jmobius/gameserver/localplay/LocalPlayPilotService.java`
- `.../LocalPlayPilotProtocol.java`, `.../LocalPlayPilotActions.java`, `.../LocalPlayPilotLease.java` (разделить по ответственности без лишних абстракций)
- `java/org/l2jmobius/gameserver/config/custom/LocalPlayPilotConfig.java`
- `dist/game/config/Custom/LocalPlayPilot.ini` (OFF по умолчанию)
- `dist/game/data/scripts/handlers/chat/commands/voiced/PlayTest.java`
- `tools/phantom-local-play/{Prepare,Get,Invoke,Stop}-LocalPlayPilot.ps1`
- `tools/phantom-local-play/Run-LocalPlayPilotScenario.ps1`
- `tools/phantom-local-play/scenarios/pilot-smoke.xml`
- `test/java/org/l2jmobius/tests/phantoms/LocalPlayPilotSuite.java`
- узкие изменения `build.xml`, `PhantomTestLauncher.java`, подтверждённых startup/shutdown/chat/input/idle seams.

Имена новых классов можно уточнить под реальные аналоги до правок, записав карту. Новые runtime/secrets/logs остаются private. Запрещена широкая переработка network/security/Player; обнаруженная необходимость такой переработки — отдельный scope blocker.
