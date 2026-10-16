# High Five: самостоятельная поставка

Запустите START.cmd. Повторный запуск проверяет уже работающую пару. STATUS.cmd читает фактические PID, incarnation, порты, код и свежесть пассивного наблюдателя. STOP.cmd вызывает штатное сохранение GameServer и затем останавливает собственный LoginServer. При таймауте прочитайте сообщение и логи: принудительного завершения нет. Окно запуска можно закрыть после успешного START.

Требуются Windows PowerShell5.1, установленный JDK25 и доступная локальная MariaDB/MySQL со своей заранее подготовленной схемой. Путь JDK задаётся в runtime.local.ini. Реквизиты БД находятся в game/config/Database.ini и login/config/Database.ini; схема должна совпадать с DatabaseName и local-play.json. START выполняет read-only JDBC preflight, не создаёт и не перезаписывает БД.

Конфиги изменяются без компиляции; после изменения нужен штатный STOP→START:

| Файл | Назначение |
|---|---|
| runtime.local.ini | JDK, подтверждённая схема, таймауты оператора |
| game/config/Server.ini | GameserverPort и LoginPort для связи с LoginServer |
| login/config/Server.ini | LoginserverPort для клиента и LoginPort для GameServer |
| game/config/Custom/PhantomPlayers.ini | EnablePhantomSystem, population/active/materialized caps, scheduler, ecology, diagnostics |
| game/config/Custom/LocalPlayPilot.ini | Пассивный наблюдатель и точный allowlist имён реальных персонажей |
| game/config/Custom/PersonalCharacterQoL.ini | AllowedAccounts, навыки, кристаллизация, Seven Signs, поддержка группы, длительности эффектов |
| game/config/Custom/PersonalPremiumQoL.ini | Личный premium/shop |
| game/config/Custom/PersonalProgressionQoL.ini | Quest overlevel relief и server-wide AutoNoblesse |

Логи запуска: local-play/logs. Обычные игровые логи: game/log и login/log. Пассивный observer пишет playtest-pilot/observer.properties; REAL означает фактическую сетевую сессию allowlisted персонажа, AUTONOMOUS — работу без него. Synthetic в обычной поставке выключен. Observe не управляет реальным персонажем без отдельного существующего consent.

BUILD_INFO.json фиксирует исходный commit, свежие JAR и внешний ввод геоданных. START не требует Codex, private ops, debugger или стартового javaagent. tools/runtime-shutdown.jar — минимальный операторский вызов существующего stock shutdown, загружаемый только при STOP после guards.

TASK058: владелец разрешил серверное продолжение М2–М7 на принятой базе М1 с отложенными G4/G6 и полной личной приёмкой. М1 остаётся OPEN, G4 — DEFERRED_BY_OWNER_NOT_PROVEN, G6 — BLOCKED_PLATFORM_DEFERRED. Текущие результаты этапов и границы server/owner acceptance указаны в ACCEPTANCE_STATUS.json и отчёте058 исходного проекта. OWNER_QA_PENDING не запрещает следующую серверную разработку. Zariche отложен пользователем; исходный клиент и остальные среды не меняются.

TASK058 candidate использует отдельную `l2jmobiush5_phantom_release_058a`: один consistent stopped export из собственной058a, все124 таблицы сверены. Login для отдельного настроенного H5 клиента —2138, Game —7838, internal control —9138. Старую057 не запускать и её конфиги не менять.

Для ручной проверки: account `localplayer`, персонаж `TestAdmin` (не GM, class94/level80). Пароль находится в отдельном приватном `OWNER_LOGIN058.txt` каталога candidate. Имена TEST партнёров: `T058Tank`90/80, `T058Mage`94/80, `T058Heal`97/80, `T058Buff`100/80, `T058Mana`105/80 и начинающий `T058Scout`0/1. Подготовка уровня/навыков/gear — fixture, не заработанная карьера. Сохранённая точка у Talking Island: x=-71350, y=258050..258170, z=-3104. После START и обычного входа проверьте фактическое появление партнёров; сохранённые XYZ сами по себе не доказывают live presence.

Проверка на5–10 минут: адресуйте партнёру «го пати», дождитесь ответа и примите штатное приглашение; затем пройдите вокруг ближайшего препятствия и проверьте follow. Запросите assist/heal/buff/recharge по текущей роли и навыкам, отправьте сообщение в partychat. Res требует обычного погибшего участника и штатного согласия. Полная матрица и открытые границы — `ACCEPTANCE_STATUS.json`/`KNOWN_ISSUES.md`; клиентская приёмка OWNER_QA_PENDING. После проверки используйте STOP.cmd.
