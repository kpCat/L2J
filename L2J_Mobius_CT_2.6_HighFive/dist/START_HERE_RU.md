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

Эта поставка не объявляет М1 закрытым до личного принятия пользователя и не разрешает автоматический М2. Zariche отложен пользователем. Клиентский маршрут и текущие acceptance-порты указываются в отдельном финальном отчёте041; исходный клиент и работающий040a не меняются.

Полная cumulative поставка после М7 обязательна по docs/phantoms/RELEASE_CONTRACT.md исходного проекта. Текущие будущие функции М2–М7 не считаются реализованными.
