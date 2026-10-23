# AUTHORITY072 — границы автономной работы

**User authorization**: TASK072 разрешено выполнить самостоятельно в одной кампании: read-first, минимальные изменения Java/datapack/test/scripts/документации по TASK.md; создать новые тестовые/релизные каталоги и БД **только после подтверждённого отсутствия и точной принадлежности**, запустить локально native Java/JDK25/Ant/MariaDB loopback, сделать одну связанную приёмку, нормальную остановку, исправить адресную доказанную первопричину, собрать кандидата и оформить обычный commit/push в feature/phantom-world. Не ожидать промежуточного user approval. Окончательное клиентское QA остаётся у владельца.

## Единственное рабочее дерево и ветка
- `D:\Codex\repos\L2J-phantom-world` / `L2J_Mobius_CT_2.6_HighFive`.
- Исходная публикация071: `031ed5a3b73a44ba31d71d041a897a4f9247f16e`, read actual local/remote HEAD first. Не делать worktrees и не «чистить» деревья.
- Достоверные артефакты 071 и старее навсегда защищены. Особенно `D:\PhantomWorld\Living-Combat-071-candidate`, `Economy-QoL-070-candidate`, ранее созданные *-candidate и schemas `l2jmobiush5_phantom_(test|release)_057..071`, historic owner accounts/characters/flags and `C:\Users\ZBook\...`. Ни одна из этих папок/БД/сервисов не является разрешённой целью изменения, удаления или reseed.

## Только OWN072
- Изолированный private root: `<repo>\.phantom-local\task072\` с TEST `runtime-test`; отчёт/лог/credentials там, никогда в Git.
- Физический RELEASE: `D:\PhantomWorld\Sustainable-Life-072-candidate` — создавать только если отсутствует; если существует, не переписывать без точной OWN072 provenance и безопасного non-mutating сравнения, иначе остановка.
- DB: `l2jmobiush5_phantom_test_072`, `l2jmobiush5_phantom_release_072`. MariaDB 11.4 localhost:3308, читать локальные разрешённые operator configs, не копировать пароли/hexid в текст ответов/публичные отчёты. GameServer 4GB heap, 203 физических geodata, maxPool4 если это соответствует работающему OWN071 профилю.
- Предлагаемые loopback роли TEST login2252/game7952/control9252; RELEASE login2106/game7777/control9152 **только после точного определения отсутствия чужого слушателя и ownership**. Не менять firewall, сетевые маршруты или system services. Отсутствие свободного стандартного порта — `BLOCKED_PORT`, чужой процесс не гасить.
- Runtime должен нести собственный ownership/version/schema/installationUUID/sha и проверять them end-to-end. Временные файлы, PID, jars/test imports строго own072; проверка API SQLException/invalid database must fail closed. Reuse proven 071 patterns по source, но не перенаправлять скрипт 071 к каталогу072 и не изменять старые ownership manifests.

## Git / sensitive state
- Разрешены read-only branch/status/worktree list/commit inspect/remote compare, exact-path stage/commit and normal push feature/phantom-world.
- Запрещены `git reset --hard`, `git clean`, `git stash/drop`, `git push --force`, rebase, `git add .`, удаление других worktrees, массовая normalization чужих путей и операции с master/main/default.
- Не подключать публичные внешние LLM, API, сторонние сети; не отправлять secrets/tokens, credentials, personal real player data или большие binaries в Git/лог.
- Existing `068V` user dirt либо иной dirty paths сохранить и исключить; при конфликте единственного файла — ограничить собственную правку или `BLOCKED_CONFLICT` с точным путём, не затирать.

## Ограничение исполнения
- Target1.5–3h, HARD5h, freeze product4h. Один основной исполнитель владеет Git, runtime и DB. Субагенты допустимы только для независимого read-only обзора, не для конкурирующих DB/game starts. От 2 одинаковых необъяснённых сбоев одного сценария — стоп ретраи, анализ фактов и переход к остальному. Лучший частичный опубликованный результат лучше фиктивного FULL_PASS.
- Во всех остановках сначала stock graceful shutdown+drain; не применять force kill/terminate или глобальную остановку MariaDB.
