# PHANTOM-PLAYTEST-PILOT-001 — evidence

## READ_FIRST (до изменений)

- Исходный HEAD: `d2ae5704031ca85e417c3cb959de47cd72618821`, ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`. В дереве есть чужие изменённые и незарегистрированные файлы; пилот будет добавлен в commit только по точным путям.
- Прочитаны `AGENTS.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `TASK_PACKAGE_STANDARD.md`, файлы текущего пакета `TASK`, `DESIGN`, `REPO_MAP`, `ACCEPTANCE`, `SCENARIOS`, `HANDOFF`, `VISION_DELTA`; адресно прочитаны `PhantomStatus`, `PersonalCharacterQoLConfig`, `ConfigLoader`, `GameServer`, `GameClient`, `Say2`, `ChatGeneral`, `BypassUserCmd`, `Loc`, `MoveToLocation`, `Action`, `RequestMagicSkillUse`, `AttackRequest`, `PartyInvitationService`, `FloodProtectorAction`, `PhantomTestLauncher`, headless fixture, LocalPlay ownership/start/check scripts и `build.xml`. README проекта и дополнительные code-map/pattern файлы не найдены; карта задачи — `REPO_MAP.md`.
- Локальные аналоги: `PhantomStatus` — voiced handler и локальное разрешение; `MasterHandler` — регистрация scripts; `PersonalCharacterQoLConfig`/`ConfigLoader` — fail-closed config; `PartyInvitationService` — native invitation identity и leave; `PhantomTestLauncher`/Ant — узкий test route; `LocalPlay-Ownership.ps1` — PID + start-time ownership.
- Вместо отсутствующего `MoveBackwardToLocation.java` фактически используется `MoveToLocation.java`; synthetic client packet не является внутренним API. `Say2` содержит валидацию и observation scope; общий player-chat ingress потребует точечного выделения.
- `/loc` — `BypassUserCmd → Loc`, без обнаруженного flood/mute в этом пути. `FloodProtectorAction` раздельно считает запросы и может kick/ban/jail; он не доказывает источник прежнего mute. В просмотренных network/config и effective private config универсальная server AFK policy не найдена; найденный `DisconnectAfterDeath=False` к обычному простою не относится. Причина прошлых разрывов остаётся `IDLE_CAUSE_UNCONFIRMED`; отключать сетевой cleanup нельзя.
- Ограничения: JDK 25, Ant, только High Five, private LocalPlay root, default OFF, без fake GameClient/GM/SQL DML/DDL, без изменения 10k и игровых бюджетов, один `USER_CLIENT_ACTION` после автоматических gate. TEST fixture не выдаётся за связанный клиент. Не трогаем чужие изменения. Пакет явно допускает bounded exception к правилу 8–10 файлов: transport, native adapter, scripts, PowerShell runner, focused tests и evidence вместе образуют один проверяемый пилот.
- Непроверено: действующий GameServer и его owned incarnation, TEST DB gate, детали конечных native outcome для каждого action, клиентская видимость и реальная причина прошлых idle/flood симптомов.

## План исполнения

- [ ] RED: deterministic lease, parser/replay/stop negative controls; затем минимальная реализация.
- [ ] Private config, owned LocalPlay mailbox, voiced arm/off/stop, session-bound polling и result journal.
- [ ] Native action adapters, общее server-side chat ingress, адресная preemption.
- [ ] Prepare/Get/Invoke/Run/Stop PowerShell и bounded pilot-smoke без ручных промежуточных команд.
- [ ] Узкий Ant target и native TEST subcases; затронутые regression suites; диагностика AFK/flood без глобального обхода.
- [ ] Code review, exact diff/text checks, exact-path commit/push, clean detached build и deployment с backup/hash.
- [ ] Одна клиентская привязка; connected batch или честный `READY_FOR_CLIENT_BINDING`; остановить runner, оставить исправный LocalPlay пользователю.

## Проверки и наблюдения

- Диагностика по коду и effective private config: серверная общая AFK policy для online Player не найдена (`NO_SERVER_IDLE_POLICY_FOUND`); network EOF/error, client logout, server shutdown и death-disconnect остаются обычными путями. Причина прежнего idle-разрыва `IDLE_CAUSE_UNCONFIRMED`. `/loc` не использует chat; `.phantomstatus` и `.playtest` проходят voiced path, но в проверенном маршруте нет punitive mute/flood. Защиты и бюджеты не менялись.
- Первый sandboxed `ant phantom-localplay-pilot-test` завершился внутренним `javac` `AccessDeniedException` при чтении `HikariCP-7.0.2.jar`; это не ошибка исходников. Повтор того же target с разрешённым доступом к файловой системе прошёл 3/3. Временная диагностическая попытка `fork=true` не помогла и была откатана.
- Security review обнаружил и исправил: одноразовый arm при I/O failure, проверку private ACL, полный reparse check mailbox, hard 20-minute run cap, strict owned-record/manifest format и JAR hash, изначально неограниченный monster target, запрет area/group/ground skills, cleanup созданной пилотом party и cancellation точного pending invite. Runner подтверждает stop через свежий session state; при сбое выполняет только bounded native cleanup для принадлежащих сценарию изменений. Реальный TCP-разрыв не подавляется.
- `ant phantom-localplay-pilot-test` (первая расширенная версия): contract 5/5; native TEST 2/3, стояние после `sitDown()` ещё было в штатной 2.5-секундной анимации. Исправлен именно тест и сценарная пауза, без принудительной смены позы. Повтор `ant phantom-localplay-pilot-native-test`: 3/3 на allowlisted TEST DB, без fake GameClient.
- `ant phantom-chat-observation-test`: 2/2; `ant phantom-party-server-integration-test`: 10/10 на guarded TEST DB. Эти запуски не доказывают настоящий подключённый клиент.
- `ant phantom-humanized-goal038-behavior-test`: 6/6. Первый `phantom-conversation-chat-integration-test` упал до запуска subcases: historical test через Reflection искал перенесённый `Say2.dispatchFinalFiltered`. Тест переведён на фактический общий `PlayerChatIngress.dispatchFinalFiltered`; будет выполнен targeted rerun. Публичный chat behavior не изменялся ради теста.
- Targeted rerun `ant phantom-conversation-chat-integration-test phantom-localplay-pilot-test`: conversation chat 5/5, pilot contract 5/5, guarded native 3/3. После skill target guard: `ant phantom-localplay-pilot-test` — contract 5/5, guarded native 3/3, включая отказ для уже известного AREA-навыка. После manifest canonical-path проверки: `ant phantom-localplay-pilot-contract-test` — 5/5.
- `Check-LocalPlay.ps1` вне sandbox: `CONFIG PASS`, LoginServer и GameServer остановлены. Сохранённые private effective values: Population=10000, Active=64, MaterializedCap=128, PulseMs=100, databaseConfig=FRESH_LOCAL_PROVISIONED. Старт/пересборка через `Build-LocalPlay.ps1` с его пресетом Lively изменили бы эти бюджеты, поэтому deployment будет только exact-file из чистой сборки в существующий runtime с сохранением конфигурации.
- `LOCALPLAY_PILOT` отделён от `CLIENT_CHAT` в evidence, но допущен в обычные conversation eligibility/memory rules как речь реального Player; ботам не добавлены pilot response overrides.
- PowerShell parser/scenario XML (2 сегмента) и локальные тесты private ACL/atomic record проведены; финальный сценарий пока не исполнялся без отдельного подключённого клиента.

## Граница доказательства

`UNIT/CONTRACT` и `NATIVE_TEST` подтверждают lease, парсер, часть native adapters и существующие party/chat seams. `CONNECTED_SERVER` и `CLIENT_OBSERVED` отсутствуют до фактического `.playtest arm` с отдельного клиента. Отправка chat/party action не повышает gameplay capability ботов.
