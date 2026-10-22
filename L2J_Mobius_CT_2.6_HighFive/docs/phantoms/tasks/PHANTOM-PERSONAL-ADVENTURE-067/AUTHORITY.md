# AUTHORITY067 — один автономный продуктовый проход

Владелец заранее разрешает Codex в отдельном новом диалоге полностью выполнять PHANTOM-PERSONAL-ADVENTURE-067 на `D:\Codex\repos\L2J-phantom-world`: читать source, самостоятельно принимать локальные архитектурные решения, изменять нужные Java/config/scripts/native тесты, запускать и останавливать **свои** изолированные runtime, собирать JDK25/Ant JAR, разбирать логи, делать несколько логических commits и **один или несколько normal non-force push исключительно в `feature/phantom-world`**.

**Не спрашивать владельца подтверждение плана, числа файлов, теста, новой private схемы/сборки или commit. Не просить владельца заходить в клиент.** Сложные обычные решения принимаются в рамках TASK, нерешённые — описываются как честный PARTIAL без выдуманного PASS. Обязательные confirmations Codex/platform обходить нельзя.

## Границы и Git

- GitHub `kpCat/L2J`, ветка `feature/phantom-world`, baseline из TASK; проверить LIVE remote HEAD/ancestry и текущий dirty state. Новейший прямой потомок допустим после чтения diff, без перезаписи чужих правок. При divergence/unknown dirty/blocked auth — безопасный адресный отчёт и BLOCKED, не делать forced действий.
- Не менять `master`, upstream/default, `C:\Users\ZBook\L2J_Mobius` и **два его stash**. Не выполнять reset --hard, git clean, массовый stash, force push, rebase поверх чужих коммитов, drop/stash pop, массовое удаление worktrees или `git add .`.
- Затрагивать root/module docs только ради новой информации о 067, не переписывать старые отчёты TASK065/066/R2 и не переделывать рабочие M1–M7 / PhantomSemanticStudio / Account_Manager.
- Разрешены **новые собственные** `PersonalCharacterQoL.ini` опции, адресные редактирования клиентских native input scripts / Java в одном HighFive module, изменения тестов, короткие комментарии кириллицей UTF-8. Не создавать вторую альтернативную систему auth/party/instance/item reuse/fishing/option stat.
- Никаких GM/accessLevel, отключения `Player.hasHeadlessOutboundSession()` или native packet/auth guards для теста. Все новые личные исключения **дополнительно** запрещены Phantom/Background и не могут стать доступны обычному игроку из-за общего включения сервера. Всегда fail closed на отсутствующем/плохом allowlist.

## Время и тестовая дисциплина

- До **12 часов**, но не требовать работы всё отведённое время. **10,5 часов — стоп новому коду**, завершить safe checked features, финальный commit/JAR/report/STOP/push. Если работа выполнена за 4 часа — заканчивать за 4 часа.
- Один read-first pass максимум 35 мин, не перечитывать сотни архивных M1–M7 отчётов. В `PROGRESS067.md` зафиксировать feature inventory / source owners / бюджет; после compaction читать его + current diff. `using-superpowers` и планирование не превращать в новый approval gate.
- Если первый сценарий 2 раза упал по одному и тому же входу — 1 диагностический trace и **проверка общей причины**. Не обходить каждый instance template, item, рыбку, level, quest и координату подряд. Типовые комбинации — параметризованные unit/data checks, 1 representative native позитивный сценарий на механику и пару негативных.
- Bounded native/server observation всего до **60 минут**, если требуются долгие timers использовать fake clock/virtual time в изолированном unit test, один действительный timed case достаточно. На proof run не менять настройки rates/items/quests/skills ради PASS.
- Одна подготовленная feature отдаёт законченный полезный результат и документированный switch. Если item feature долго блокирует другую — fail-closed оставить OFF, переключиться к следующей. **Самоценные механизмы A/B/C/D должны быть независимыми и не ломать друг друга.**
- Последний review только по affected production owners, safety regression и новым изменениям. Не переписывать test fixtures ради 100% GREEN и не гонять full Ant verify, если target затрагивает старые DB.

## Runtime и публикация

- Создать **свои** test/release schemas/портовые пары/поставку по `RUNTIME_INPUTS.md`, сначала ownership manifests, исключить collisions, файлы 057–066-R2 и их DB защищены. Собственную Java диагностику выполнять только после PID+start+JAR проверки. Не останавливать чужие JVM. STOP обычным API; force taskkill/Stop-Process -Force запрещён.
- Для экономии использовать **уже созданные guard/helpers** 066R2 read-only как образцы, не клонировать/править старые releases, не открывать чужие production BД. Чистую финальную сборку делать из exact собственного committed-source SHA и сохранить manifest JAR/GeoData/provenance.
- После безопасной проверки: адресный commit, обычный `push origin HEAD:refs/heads/feature/phantom-world`, сравнить local/remote SHA. При неуспехе оставить локальную работу безопасно сохранённой, PUBLISH=BLOCKED; без force.

**Нельзя:** скрытых спавнов рыбы/ресурсов, безлимитных предметов, глобального снятия entry/reuse/quest требований, fake party members, подмены персонажа владельца, незаметной публикации больших GeoData/JAR/секретов. Native fail-closed важнее красивого результата. Все настоящие метрики в отчёте отдельно от pre-measurement fixtures.
