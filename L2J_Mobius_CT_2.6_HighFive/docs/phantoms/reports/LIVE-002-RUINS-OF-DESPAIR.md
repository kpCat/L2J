# LIVE-002-RUINS-OF-DESPAIR — bounded ordinary farm gate

Дата: 25.09.2026. **Статус: BLOCKED — `NO_EXACT_STABLE_RUINS_POINT_FARM`.** Ветка `feature/phantom-world`, исходный HEAD `4737956cb808966bac0e99701e5ce2e66bf5a1ec` подтверждены. `RUINS_OF_DESPAIR_REACHABLE_FARM=1` не доказан и не заявляется.

## Read-first и scope

Прочитаны TASK/EVIDENCE/ACCEPTANCE только из заданного абсолютного каталога, локальный AGENTS.md, master plan, workflow contract, task standard, текущий STATE, Ruins-раздел LIVE-002-FINAL-GEO, RUINS_GEODATA_VALIDATION, active generated-03/04/05, targeted supplement/manifest, generic GK generator, native 31964/30256 teleporter XML и три заданных seed spawn sources. README.md и отдельные docs/code-map/pattern-файлы для этой задачи не найдены. Локальные аналоги: Schuttgart/Aden/Goddard/Oren exact ROUTE hub, strict `VALID_IDENTITY`, Oren radius-1 POINT farm, `PhantomTravelGeoProbe` и независимый DB-free active graph validator. Предполагалось переиспользовать именно их, без новых Java semantics. До preflight оставались непроверенными Geo Z и локальный Ruins маршрут.

На исходном дереве были несвязанные dirty/untracked файлы; они не изменены и не включены в задачу. Другая хроника, runtime, DB, closed areas, LIVE-003 не затронуты. Отдельных `.csproj`/props/targets здесь нет; Java/Ant сборку задача не меняет. UI не затрагивается.

## Независимый baseline и factual transport

Свежий DB-free запуск `python tools/phantom-world-data/live002_81_85_final.py --output <TEMP>/live002_ruins_baseline.tsv` завершился с кодом 0 и дал `40–51=5`, `52–60=13`, `61–75=15`, `76–80=3`, `81–85=1`. После записи audit-файлов повторный запуск дал те же counts и тот же SHA-256 вывода `103dc43a2222ff2fdfe80a7220b926c3b007b7302a90ca8768cdc6c04f97f0ef`. Историческую Ruins оценку из FINAL-GEO не использовали как проверку нынешнего backbone.

Native `31964.xml` и machine facts подтверждают Bilia `transition.e8a8dea18661b81ca8c39204` → The Town of Gludio `-12787,122779,-3112`, fee 85000 Adena, destinationCastleId 1. Native `19_21.xml` подтверждает spawn Bella30256 `-12736,122816,-3114`; native `30256.xml` и machine facts подтверждают `transition.6e5c4bb7d4430ac8102fe4e2` → Ruins of Despair `-19120,136816,-3752`, fee 610 Adena. Свежий hermetic `PhantomTravelGeoProbe` для Gludio arrival→Bella вернул `VALID_DIRECT`, length 64, 1 segment, required buffer 72/max 500, `STATIC_XML_CLEAR`, door/fence 0/0. Первую попытку probe отклонил из-за неверного TSV header; вход исправлен и повторный запуск завершился с кодом 0.

## Бounded local preflight и точный blocker

Проверены только три заданные source families. `WORLD_COVERAGE.tsv` считает `Others/19_21.xml` целиком `NON_FARMING/EXCLUDED` с причиной `native_npc_type_or_flags`, хотя ближайшая native Monster point лежит в 7438.39 XY; эту группу нельзя объявить ordinary farm. Ближайшая native territory vertex `Gludio/Wasteland.xml` находится в 32480.06 XY, за пределом 16000. Активных FARMING anchors в пределах 16000 XY нет. Единственная локальная `ORDINARY_WORLD/READY_STATIC/instance0` группа из проверенных — `Others/18_22.xml`, `POINT`, coverage `b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3`, ближайшая native NPC point 14334.23 XY. Machine facts для группы дают Monster. Подробный ранжированный ledger: `docs/phantoms/live-world/RUINS_LOCAL_PLAN.tsv`.

В пределах лимита проверены восемь ближайших exact NPC points этой группы через существующий `PhantomPointFarmProof` в восьми fresh DB-free JVM. У всех `hasGeo=true`, обе высоты совпали друг с другом, но ни одна не совпала с native Z: `−4044→−3936`, `−3774→−3776`, `−3988→−3984`, `−3479→−3480`, `−3817→−3816`, `−3919→−3920`, `−3934→−3936`, `−3905→−3904`. Каждый процесс вернул `NO_EXACT_STABLE_POINT_FARM`. Это исчерпывает разрешённые восемь попыток для выбранной POINT group. Девятая точка не пробовалась по quota. NPOLY group внутри 16000 среди разрешённых трёх sources нет. Уровневый путь destination→farm не проверялся, поскольку допустимый FARMING target не найден; новых directed Ruins movement checks — 0 из 36.

## Публикация и проверки

`generated-06`, targeted connectors, manifest, GK catalog, task-owned final validator/proof не публиковались: они не могли бы доказать конечный FARMING anchor. Следовательно, двух generation runs для новых generated/canonical artifacts нет; исходные generated-01..05 и текущий каталог не изменены. Для BLOCKED сохранены только audit ledger, этот отчёт и запись STATE. Файлов production code, schema, migrations и configs не меняли; Ant compile/test не требовался для audit-only изменения. `ant jar=0`, full verify=0, старый full geodata corpus и guarded targets NOT RUN, DB/Hikari/GameServer/LoginServer runtime=0.

**Следующий шаг только после отдельного task/review:** определить допустимый ordinary Ruins FARMING target с exact stable native Geo Z либо отдельно разрешить иной native farm contract. Текущий gate остаётся открытым; closed-area и LIVE-003 не начаты.

Git: точный baseline/branch/upstream, рабочее состояние и exact-path staged diff/check проверены по правилам AGENTS.md. Один audit commit и обычный push в `feature/phantom-world` выполняются отдельно; SHA и результат push указаны в финальном сообщении.
