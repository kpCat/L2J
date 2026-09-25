# LIVE-002-RUINS-NORMALIZED-POINT-FARM — bounded Ruins route blocker

Дата: 25.09.2026. **Статус: BLOCKED — `NO_BOUNDED_RUINS_LOCAL_ROUTE`.** `RUINS_OF_DESPAIR_REACHABLE_FARM=1` не доказан.

## Read-first и baseline

Прочитаны `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md` только из заданного абсолютного каталога, локальный `AGENTS.md`, master plan, workflow contract, task standard, предыдущий отчёт, текущие STATE и Ruins ledger. Сверены `generated-03/04/05`, targeted supplement/manifest, normal GK generator, 81–85 final validator, `PhantomTopologyArea`, `PhantomTopologyQuery`, `PhantomTopologySnapshot`, GameKnowledge exact mapping, canonical committed anchor и native `Others/18_22.xml`/`Others/19_21.xml`. Файл README.md в модуле и AGENTS.md выше модуля не найдены. Отдельные docs/code-map/pattern-файлы не использовались. Локальные аналоги: Oren POINT_RADIUS farm в `generated-05`, ROUTE hub в `generated-04`, `PhantomPointFarmProof`, `PhantomTravelGeoProbe`, `PhantomTopologyCoreSuite` и DB-free `live002_81_85_final.py`.

Исходный HEAD `14e4e10543c420fe376980cf0b426d057253cac6`; после audit commit continuation baseline `04a89675af03b879ddf0b8f54e4516f9b8fd8e57`, ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`. Несвязанные dirty/untracked файлы обнаружены и не менялись. Fresh DB-free `live002_81_85_final.py` до и после проверок завершился с кодом 0: ordinary progression **`5/13/15/3/1`**, SHA-256 `103dc43a2222ff2fdfe80a7220b926c3b007b7302a90ca8768cdc6c04f97f0ef`.

## Подтверждённая причина и безопасный аудит

Старый `PhantomPointFarmProof` требует `nativeZ == Geo Z`, поэтому кандидат NPC20059 `-33539,137701,-3479 → -3480` отвергался именно старым exact-Z contract. Существующий `PhantomTopologyArea.pointRadius` проверяет 3D расстояние; source-centered radius 1 геометрически содержит native spawn и Geo anchor. GameKnowledge для EXACT spawn вызывает `mostSpecificNode` с native Z, а `canonicalCommittedAnchorPosition` требует стабильной Geo-высоты anchor. Глобальный 81–85 exact contract не менялся.

Native `Others/18_22.xml` содержит NPC20059 `-33539,137701,-3479`; NPC stats указывают `Hungry Eye`, level 22, `Monster`. Coverage `b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3` — `ORDINARY_WORLD / READY_STATIC / POINT / instance0`. Native `TRAVEL_TRANSITIONS.tsv` содержит заданные factual Bilia→Gludio и Bella→Ruins NORMAL rows.

## Fresh DB-free Geo и movement proof

Предыдущий `BLOCKED_HERMETIC_PROBE_UNAVAILABLE` снят явным разрешением пользователя: `ant compile-tests` завершился успешно после чтения локального HikariCP jar только как compile-time classpath. Все DB-free Java proof процессы запускались с runtime classpath **без** HikariCP jar. Новый `PhantomNormalizedPointFarmProof --self-test` подтвердил bounded delta 0/1/4, отказ delta 5 и отсутствие anchor вне radius1. Fresh GeoEngine загрузил 203 geodata regions: primary NPC20059 имеет `hasGeo=true`, `h1=h2=h3=-3480`, delta1/radius1. Один same-group witness NPC20059 `-33984,135888,-3919 → Geo -3920` также имеет стабильный delta1. Hermetic movement от primary Geo anchor к witness — `VALID_DIRECT`, length1918, 1 segment, `STATIC_XML_CLEAR`, door/fence 0/0. Fresh Gludio destination `-12787,122779,-3112` → Bella `-12736,122816,-3114` — `VALID_DIRECT`, length64, 1 segment, `STATIC_XML_CLEAR`, door/fence 0/0.

Дополнительный prepublication test обнаружил, что `PhantomTopologySnapshot.validateAnchors()` требовал native spawn в tolerance0 от самого anchor и потому отклонял delta1. Focused topology core regression дал RED 38/39 с точным `Topology NPC anchor has no factual spawn within tolerance`. Узкий marker-gated путь `normalized-point-farm` принимает только FARMING/POINT_RADIUS instance0 с exact native center, тем же x/y Geo anchor, sourceRefs equality, delta1–4 и минимальным radius; unmarked exact 81–85 contract остался прежним. После исправления `ant compile-tests` и topology core 39/39 GREEN. Отдельный temporary snapshot из всех 1038 активных nodes и provisional Ruins node дал `POINT_MAPPING node=generated.farm.b729b4b54a41170f2da2aa85 anchor=generated.farm.b729b4b54a41170f2da2aa85.anchor canonical=-33539,137701,-3480 activeNodes=1039`. Native spawn и anchor находятся внутри node. Это проверка публикационного контракта до записи `generated-06`, а не заявление о committed shard.

Первый обязательный Ruins destination `-19120,136816,-3752` → primary Geo anchor `-33539,137701,-3480` вернул `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE`: required buffer1868 > max500; PathFinding для этой пары не применялся.

## Bounded factual chain и точный blocker

Только из `Others/19_21.xml` и `Others/18_22.xml` task-owned deterministic selector удержал 21 factual native NPC point в пределах 16k XY от одного из концов. Два fresh run selector дали побайтно одинаковый TSV. Batch GeoEngine проверка: 18/21 stable Geo points с delta<=16; три отвергнуты при delta106/138/188. Ни одного active topology anchor в 16k XY от Ruins destination нет. Ближайшая разрешённая native NPC точка лежит в 7438.39 XY.

Четыре ближайших Geo-valid first-hop точки исчерпывают разрешённый fanout4. Все четыре fresh hermetic directed checks имеют `STATIC_XML_CLEAR`, door/fence 0/0, но movement не доказан:

| Native NPC x/y/z → movement Geo Z | XY от Ruins | Результат | required/max buffer |
| --- | ---: | --- | ---: |
| 20359 `-23089,130525,-3664 → -3664` | 7438.39 | `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` | 852/500 |
| 20359 `-26310,129875,-3723 → -3720` | 9993.68 | `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` | 964/500 |
| 20055 `-27296,130944,-3684 → -3688` | 10066.15 | `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` | 1086/500 |
| 20359 `-26001,128437,-3515 → -3512` | 10842.32 | `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` | 1112/500 |

Без положительного first hop невозможна полная directed chain в разрешённом nearest-neighbor fanout4. Это **`NO_BOUNDED_RUINS_LOCAL_ROUTE`**, а не утверждение о всех возможных путях вне данного quota/источников. Использовано 1/4 farm candidates, 21/24 route points, 7/32 directed checks (local, Gludio, direct Ruins, четыре first hops), 0/5 new ROUTE anchors; сетка/interpolation не использовались.

Поскольку full route заблокирован, `generated-06`, targeted connectors, catalog, committed anchor и final proof не публиковались. Full active node set плюс provisional farm подтвердил `mostSpecificNode(native spawn)` и `canonicalCommittedAnchorPosition`; committed shard не существует. Два generation runs для generated/canonical artifacts и независимый Ruins final validator не запускались. Два run factual waypoint selector побайтно совпали; `ant compile-tests`, topology core 39/39 и текущий progression validator GREEN. Изменены только `PhantomTopologySnapshot.java`, `PhantomTopologyCoreSuite.java`, task-owned `PhantomNormalizedPointFarmProof.java`, `live002_ruins_candidates.py`, `live002_ruins_point_preflight.py`, этот отчёт и STATE. Старый full geodata corpus, guarded DB targets, `phantom.test.config`, GameServer/LoginServer, `ant jar`, full verify — не запускались; Hikari initialization/pool, DatabaseFactory, DB connections/runtime — 0. Closed/LIVE-003 не начаты.

## Следующее действие

Потребуется отдельный task/review с иными factual waypoint sources или иной bounded reachability policy для первого hop от Ruins destination. Текущий task остановлен на точном blocker; следующий Goal/Slice не начат.

## Final artifact scope guard

Exact allowlist этого BLOCKED checkpoint:

- `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySnapshot.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomNormalizedPointFarmProof.java`
- `tools/phantom-world-data/live002_ruins_candidates.py`
- `tools/phantom-world-data/live002_ruins_point_preflight.py`
- `docs/phantoms/reports/LIVE-002-RUINS-NORMALIZED-POINT-FARM.md`
- `docs/phantoms/live-world/STATE.md`

Ни один historical `.llmgc`/procedural artifact, native source, generated-01..05, targeted supplement или unrelated dirty/untracked файл не изменён для Goal. Проверка staged exact-path list выполняется перед commit.
