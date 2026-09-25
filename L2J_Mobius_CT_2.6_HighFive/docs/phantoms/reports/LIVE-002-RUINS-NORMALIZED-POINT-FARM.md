# LIVE-002-RUINS-NORMALIZED-POINT-FARM — проверка остановлена

Дата: 25.09.2026. **Статус: BLOCKED — `BLOCKED_HERMETIC_PROBE_UNAVAILABLE`.** `RUINS_OF_DESPAIR_REACHABLE_FARM=1` не доказан.

## Read-first и baseline

Прочитаны `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md` только из заданного абсолютного каталога, локальный `AGENTS.md`, master plan, workflow contract, task standard, предыдущий отчёт, текущие STATE и Ruins ledger. Сверены `generated-03/04/05`, targeted supplement/manifest, normal GK generator, 81–85 final validator, `PhantomTopologyArea`, `PhantomTopologyQuery`, GameKnowledge exact mapping, canonical committed anchor и native `Others/18_22.xml`. Файл README.md в модуле и AGENTS.md выше модуля не найдены. Отдельные docs/code-map/pattern-файлы не использовались. Локальные аналоги: Oren POINT_RADIUS farm в `generated-05`, ROUTE hub в `generated-04`, `PhantomPointFarmProof`, `PhantomTravelGeoProbe` и DB-free `live002_81_85_final.py`.

HEAD `14e4e10543c420fe376980cf0b426d057253cac6`, ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`. Несвязанные dirty/untracked файлы обнаружены и не менялись. Fresh DB-free запуск `python tools/phantom-world-data/live002_81_85_final.py --output "$env:TEMP\live002_ruins_baseline_new.tsv"` завершился с кодом 0: ordinary progression **`5/13/15/3/1`**, SHA-256 `103dc43a2222ff2fdfe80a7220b926c3b007b7302a90ca8768cdc6c04f97f0ef`.

## Подтверждённая причина и безопасный аудит

Старый `PhantomPointFarmProof` требует `nativeZ == Geo Z`, поэтому исторический кандидат NPC20059 `-33539,137701,-3479 → -3480` не проходит именно старый exact-Z contract. Существующий `PhantomTopologyArea.pointRadius` проверяет 3D расстояние, значит source-centered radius 1 геометрически может содержать native spawn и нормализованный anchor; GameKnowledge для EXACT spawn вызывает `mostSpecificNode` с исходным native Z. `canonicalCommittedAnchorPosition` требует стабильной Geo-высоты самого anchor. Это подтверждает допустимость отдельного bounded contract как направления решения, но не заменяет свежих Geo/movement проверок.

Native `Others/18_22.xml` содержит NPC20059 `-33539,137701,-3479`; NPC stats указывают `Hungry Eye`, level 22, `Monster`. Coverage `b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3` — `ORDINARY_WORLD / READY_STATIC / POINT / instance0`. Предыдущее Geo Z `-3480` пока является только принятым историческим evidence, не свежим результатом этого task. Native `TRAVEL_TRANSITIONS.tsv` содержит заданные factual Bilia→Gludio и Bella→Ruins NORMAL rows. Fresh Gludio→Bella, local same-group movement и Ruins→farm не проверены в этой попытке.

## Остановка проверки

Для task-owned Java proof был временно подготовлен отдельный test helper; он удалён до публикации после отказа среды, поэтому неподтверждённый код не оставлен. Запуск разрешённой TASK.md целевой команды `ant compile-tests` не дошёл до успешной компиляции: `javac` получил `AccessDeniedException` при чтении `dist/libs/HikariCP-7.0.2.jar`; вследствие этого Ant очистил временный `..\build\bin`. Две попытки запуска этой же команды с `require_escalated` отклонены автоматической проверкой разрешений. Причина отказа: проверка трактует чтение HikariCP jar и пересборку тестовых артефактов как конфликт с требованием `DB/Hikari/runtime=0`, несмотря на явно разрешённый в TASK.md `compile-tests` при изменении test Java. Косвенные способы запуска Java/Geo probe после отказа не применялись.

Без fresh `h1==h2==h3`, canonical anchor, same-group `STATIC_XML_CLEAR`, Gludio→Bella и Ruins route доказательств невозможно честно публиковать `generated-06`, targeted connectors, catalog и финальный `RUINS_OF_DESPAIR_REACHABLE_FARM=1`. Новые topology, travel, source, production Java и proof artifacts не опубликованы. Два generation runs, topology core и независимый финальный validator не запускались. Старый full geodata corpus, guarded DB targets, GameServer/LoginServer, `ant jar`, full verify — не запускались; DB connections/runtime — 0. Closed/LIVE-003 не начаты.

## Следующее действие

Нужно явное разрешение на `ant compile-tests` и запуск task-owned hermetic Geo proof с существующим HikariCP jar только как compile/runtime classpath без подключения к БД. После разрешения следует продолжить тот же bounded task с fresh проверками и не считать этот checkpoint GREEN заранее.
