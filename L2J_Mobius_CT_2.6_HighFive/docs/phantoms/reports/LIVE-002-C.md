# LIVE-002-C — GeoEngine validation and topology publication

Дата: 23.09.2026. Ветка `feature/phantom-world`; исходный tracked HEAD `dac4df2e31ec2eda1dde14f0d62fd4b520de0586`. **CODE_STATUS=GREEN; RUNTIME_STATUS=PARTIAL; LIVE-002 в целом остаётся открытым.** Это checkpoint C с честно неполным географическим покрытием, а не приёмка всех level bands.

## Scope и исходная среда

До изменений проверены ветка/HEAD, существующие dirty/untracked файлы, шесть accepted A/B SHA-256 из TASK/EVIDENCE. Все шесть совпали, исходные TSV/JSON не менялись. `GeoEngine.ini`: `PathFinding=2`, путь `./data/geodata`; 204 файла в каталоге, из них 203 `.l2j`; тестовый и игровой GeoEngine загрузили 203 региона. Существующий core corpus/geodata control прошёл 7/7 до публикации. Private runtime первоначально STOPPED.

Read-first: `AGENTS.md`, `README.md`, task/EVIDENCE/ACCEPTANCE, LIVE-002-B report, LIVE-002 section `STATE.md`, два B-скрипта, `GeoEngine.java`, `PathFinding.java`, topology loader/snapshot/query, historical planner/authority, production corpus suite. Для конкретной интеграции точечно прочитаны GeoConfig, headless environment, knowledge builder/query, background goal/state и Goal033A/033A1 suites, XML core/siege, runtime ownership scripts и schema/component contracts. Это bounded exception к лимиту дополнительных файлов в TASK: без них нельзя было проверить factual geometry, 2000-unit planner target bound, загрузку geodata, Goal033A proof и read-only runtime snapshot. Whole-project просмотр и ручная оценка тысяч строк не выполнялись. Отдельные `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md`, `DEVELOPMENT_CHAT_HANDOFF.md`, `docs/AGENTS.md` и code-map в этом модуле не найдены.

Переиспользованы headless bootstrap production corpus, строгий loader, существующий `PhantomHistoricalBackgroundPlanner`, native `GeoEngine.canMoveToTarget`, `PathFinding.findPath`, `L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition`, B candidate accounting и exact source geometry. Production runtime, schema, rates, population, schedules, heap/collector и QoL не менялись. Предсуществующие dirty/untracked файлы сохранены и не staged; собранный JAR, как и вся сборка этой рабочей копии, содержит уже существовавшие dirty Java изменения пользователя.

## Машинная GeoEngine проверка

Java validator дал ровно 2632 anchor proof rows и 8657 route proof rows: 3550 walking/region candidates проверены отдельно в двух направлениях (7100 rows), плюс 1474 teleport и 83 core evidence-only rows. Для anchors использованы не более 32 детерминированных XY попыток из factual geometry; max=32, p95=29. Опубликованный Z взят из повторно стабильного `GeoEngine.getHeight`; проверены source geometry, mapregion, short local movement, canonical position, уникальная factual polygon mapping и фактическая близость anchor к вершинам, требуемая Game Knowledge Builder. Для routes проверены direct `canMoveToTarget`, затем PathFinding при необходимости и повторно каждый segment. Null/blocked/cross-instance, door/fence и несоответствующие endpoints не публиковались.

| Anchor status | Групп |
|---|---:|
| VALID | 919 |
| CLOSED_SOURCE_NO_DOOR_PATH | 676 |
| PLANNER_TARGET_DISTANCE | 501 |
| OUTSIDE_GEOMETRY | 319 |
| AMBIGUOUS_GEOMETRY | 174 |
| UNSUPPORTED_POINT_AREA | 37 |
| NO_LOCAL_MOVEMENT | 3 |
| INVALID_SOURCE_GEOMETRY | 2 |
| MAP_REGION_MISMATCH | 1 |

Из направлений маршрутов опубликованы 771 `VALID_DIRECT` и 653 `VALID_PATH`; 4728 получили `ENDPOINT_BLOCKED`, 541 `NO_PATH`, 407 `PATH_SEGMENT_BLOCKED`. Teleport 1474 остаются только evidence. Все 676 closed/multi-floor groups заблокированы до factual door/room/corridor path. В Ruins candidate set 10/10 anchors блокированы; landmark имеет стабильный geodata Z, но доказанного пути и reachable coverage key нет. Семантическая привязка ближайшей spawn group к Ruins не утверждается.

## Публикация и детерминизм

Активный `high-five-generated-01.xml`: 919 новых farming nodes, 919 anchors, 1424 **направленных** `BACKGROUND` edges с `backgroundEligible=true`, `bidirectional=false` и exact `fromAnchorId`/`toAnchorId`. Время = max(1000 ms, validated path length × 10 ms), локальная консервативная конверсия 100 world units/s. Shard 1 313 494 bytes (<4 MiB); всего 3 topology XML (<64). Core и siege сохранили все entities: изменён только root `datasetVersion` 3→4. Combined production corpus: 1031 nodes / 1032 anchors / 1507 edges, datasetVersion 4. Строгий suite проверил соответствие **каждого** generated XML entity proof rows и отсутствие invalid entities.

Два отдельных fresh JVM validation/publication run (`.phantom-local/logs/LIVE-002-C/run6`, `run7`) побайтно совпали между собой и с опубликованными файлами:

Для canonical `.tsv`/`.json` и generated XML добавлены узкие `.gitattributes` с `eol=lf`: рабочая копия имеет `core.autocrlf=true`, поэтому без них следующий checkout изменил бы доказанные SHA-256.

| Canonical artifact | SHA-256 |
|---|---|
| `ANCHOR_GEODATA_VALIDATION.tsv` | `42a3987c12a74cc9cad369e919680ef6798488262eee4dcfbddc3ef391b79b04` |
| `ROUTE_GEODATA_VALIDATION.tsv` | `1c1d4b19669d704c162e971fcc6a24bdd3cb1325bc3783d9cfadc552a326a22b` |
| `RUINS_GEODATA_VALIDATION.tsv` | `785d75c6a5909180bb88c3069786ef0b663bbbbbb1d1f2cb6da10f5214c5fb4b` |
| `high-five-generated-01.xml` | `9242aaf4f4d711cbf99311f2be4f049110c83acb1e242bd7ca22dbcd03e051c4` |
| `VALIDATED_TOPOLOGY_MANIFEST.json` | `82e76d497c496cdd69c13df49c98dcfff25e4b26bff813ee8acc97b5724775ce` |

По машинному directed anchor graph от существующих ingress anchors (validated / reachable / unreachable / anchor-blocked):

| Level band | Validated | Reachable | Unreachable | Anchor-blocked |
|---|---:|---:|---:|---:|
| 1–5 | 38 | 8 | 30 | 56 |
| 6–10 | 50 | 0 | 50 | 99 |
| 11–19 | 42 | 0 | 42 | 166 |
| 20–39 | 192 | 0 | 192 | 392 |
| 40–51 | 136 | 0 | 136 | 427 |
| 52–60 | 52 | 0 | 52 | 202 |
| 61–75 | 337 | 0 | 337 | 380 |
| 76–80 | 154 | 0 | 154 | 222 |
| 81–85 | 36 | 0 | 36 | 45 |

Для этих bands `route-blocked = unreachable validated` (30/50/42/192/136/52/337/154/36). Graph reachability является необходимой проверкой, не утверждением, что любой target выбирается историческим planner; отдельный focused planner test ниже доказал конкретный новый маршрут. Нулевые bands блокируют статус GREEN для LIVE-002 целиком. Недостающие связи не придуманы.

## Tests и historical planner

Focused Geo rules 17/17 (no-geo, Z, local movement, direction, path segments, null, cross-instance, UTF-8 shard split); topology generation GREEN; strict production corpus 9/9 (loader metadata match/mismatch и полный proof accounting); Goal033A1 ingress 4/4; отдельный historical generated route 1/1; полный Goal033A 10/10 на контрольном повторе. Первый запуск Goal033A дал 9/10 из-за позднего `MATERIALIZATION_FAILED_CLEAN` после COMPLETE catch-up; предыдущий LIVE-003-0C report отмечал тот же intermittent case, он не исправлялся в C. `git diff --check` успешен; full `ant verify` = 0.

Исторический planner реально принял новый `generated.route.7b289551494b049c4a895f74.r` после существующего маршрута от `population.ingress.dark-elf.05` к generated farming anchor для NPC 18342. Focused suite отдельно зафиксировал путь от elf ingress через `generated.route.9409df7aec7f97dc8919f37c.r`; использованы production topology/knowledge/authority, канонический test DB baseline и `remainsSuitable`, а не синтетический bypass planner.

## Delivery и bounded smoke

Единственный `ant jar` успешен; GameServer JAR SHA-256 `312E42BFEB8D41BD1FFEF7EE3A290A9C236C19C03070791E41DC8A3455A039F5`. Штатный CHECK перед delivery: оба сервера STOPPED. Backup прежних GameServer JAR/core/siege: `.phantom-local/backups/LIVE-002-C-20260923/`. JAR и три XML доставлены в `artifacts/local-play/runtime`, SHA JAR/shard после копирования совпали. `Start-LocalPlay.ps1 -Background -LoginTimeoutSeconds 60 -GameTimeoutSeconds 600` поднял owned Login PID 18584 / Game PID 24984, порты 2106/9014/7777 принадлежали им. Game log подтвердил GeoEngine 203 regions, GameServer loaded и связь с Login. После bounded smoke штатный STOP остановил оба процесса; финальный CHECK: оба STOPPED, staleRecord=False, все три порта закрыты. Reset/reseed, прямых play-DB mutations и глобального taskkill не было.

Read-only JDBC `SELECT` snapshots до/во время/после smoke: `.phantom-local/logs/LIVE-002-C/play-*-{before,during,after}.tsv`; parser `summarize_snapshot.py`. Профили 1280→1280, links 1280→1280, characters 1281→1281, accounts 1281→1281. Catch-up COMPLETE 780→942, RUNNING 300→145, FAILED 200→193; ecology initial complete 426→427. `planner.target_or_route.absent` **186→186**. Естественный ACTIVE `.phantomstatus` через клиент в этом коротком окне не получен. Снижение topology blocker или продвижение именно по новому generated route в play DB не доказано, поэтому `RUNTIME_STATUS=PARTIAL`. Дополнительные LIVE этапы не начаты.

## Контроль файлов

Изменены только `build.xml`, два core/siege metadata XML, новый generated shard, четыре canonical evidence/manifest файла, две узкие `.gitattributes`, focused Java validator/publisher/tests и два близких topology/Goal033A suites с launcher. Предсуществующие dirty/untracked файлы оставлены. Mojibake-маркеры в изменённых файлах проверены. Escaped Cyrillic в изменённых файлах проверены. Git-команды использованы только разрешённые TASK branch/HEAD/status baseline, bounded scope diff/status, `git diff --check`, затем exact-path stage/commit и normal push; commit/push IDs фиксируются итоговым handoff, не внутри файла.
