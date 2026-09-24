# LIVE-002-40-51-HERMETIC-GEO-CLOSEOUT — итоговый отчёт

Дата: 25.09.2026. **Статус: SUCCESS / GREEN** для одного implementation Goal GLOBAL ordinary `40–51 >=1`. Ветка `feature/phantom-world`; исходный tracked HEAD точно `2b0f8ed14060f090e33774faf46634991bc29c3c`. SHA итогового commit и результат normal push указаны в финальном сообщении: собственный SHA нельзя записать внутрь того же commit.

## Причина и исправление proof infrastructure

До правки `PhantomTravelGeoProbe` явно вызывал `DoorData/FenceData`, public `GeoEngine.canMoveToTarget` вызывал их повторно, а `PathFinding.findPath` вызывал public movement через postfilter. `DoorData` и `FenceData` при загрузке создают игровые объекты; прежний proof `SKIPPED_UNSAFE` нельзя было публиковать.

`java/org/l2jmobius/gameserver/geoengine/GeoEngine.java` теперь содержит один прежний NSWE/height movement loop с package-local `MovementCollisionOracle`. Public overload делегирует production oracle с **точными прежними** `DoorData.checkIfDoorsBetween(..., false)` и `FenceData.checkIfFenceBetween(...)` после нормализации Z, в прежнем порядке и с прежним short circuit. Test-tooling bridge вызывает тот же loop с read-only XML oracle. `java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java` предоставляет package-local raw path через тот же alloc/find/construct, пропуская только `applyPostFiltering`; public путь сохраняет postfilter. `PhantomGeoValidationRules.route` перепроверяет каждый raw segment через shared movement seam.

Test-owned `StaticXmlCollisionOracle` безопасно читает только `Doors.xml` и `FenceData.xml`, запрещает DTD/external entities/schema, не создаёт `Door`, `Fence`, `World` или managers. Для двери используется консервативное пересечение отрезка с polygon face и диапазоном `nodeZ..nodeZ+height`, для fence — rectangle и Z-допуск ±100; любое потенциальное пересечение блокирует proof независимо от default/open state. Instance только 0; malformed XML/geometry fail closed. Oracle хранит IDs/counts и segment records. Fixture проверяет door, fence, clear geometry и отказ DTD.

Статические входы: `Doors.xml` SHA-256 `a2b2199efec80c8d296064f15ef5a6fb9125ede887c51e3fc068e3d0d7b596f8` (1301 геометрий, 1289 collidable); `FenceData.xml` SHA-256 `3f1658aa5a563c91a1bdef508a6e15a1f520075a437e693f81dd337c63502214` (1 геометрия). В focused source dependency test probe не содержит production collision manager/World/DB/Hikari вызовов, а GeoEngine public и PathFinding public/raw routing проверены. Один fresh JVM probe показал только `GeoEngine: Loaded 203 regions` и `TRAVEL_GEO_PROBE candidates=5 proven=5`; Hikari/DatabaseFactory/server-manager initialization markers не появились. Это подтверждается конструкцией зависимостей и конкретным fresh-process запуском, а не одним только отсутствием строки в логе.

## Пять точных Geo directions

| Направление | Fresh status | Raw длина / сегменты | Буфер | Door / fence potential |
|---|---|---:|---:|---:|
| `_06 → _05` | VALID_DIRECT | 2376 / 1 | 324/500 | 0 / 0 |
| `_02 → ROUTE-A` | VALID_DIRECT | 1482 / 1 | 240/500 | 0 / 0 |
| ROUTE-A → ROUTE-B | VALID_PATH | 4215 / 21 | 468/500 | 0 / 0 |
| ROUTE-B → `_30` | VALID_PATH | 2129 / 27 | 280/500 | 0 / 0 |
| Schuttgart ROUTE → Bilia | VALID_DIRECT | 107 / 1 | 74/500 | 0 / 0 |

Оба raw `VALID_PATH` имеют больше точек и длину, чем старые postfiltered 3590/5 и 1917/4: это ожидаемое следствие обязательного пропуска production postfilter. Статусы, endpoints, normalized Z, буферы и отсутствие static collisions совпали с требуемым proof. Повторного world/grid/route search не было. Всего пять заданных route checks; лимит ≤8 соблюдён. Все raw path segments перепроверены тем же GeoEngine loop и collision oracle.

## Публикация и provenance

`high-five-generated-03.xml` SHA-256 `5be7c4902e35794947f572eabb4f6695b5b193e6728ab9f21639ea0c4ee4ce90`: ровно Schuttgart ROUTE anchor, два Plunderous ROUTE anchors и четыре направленных Plunderous BACKGROUND edges, без reverse. `PLUNDEROUS_40_FINAL_ROUTE.tsv` SHA-256 `704f98a8d08a2c833681a493b01ff0e9af2fdd287137ee601a8fa6735d178d09` связывает каждый edge с hermetic proof и `STATIC_XML_CLEAR`. Полный пятистрочный `LIVE002_40_51_HERMETIC_GEO_PROOF.tsv` SHA-256 `294d1569dfefe97c778c6e7cd81555d2f54fc38a2e2bf2aabe53f9d9e344bad2` содержит exact coordinates и counts.

Новый Schuttgart ROUTE имеет canonical `87126,-143520,-1288`, instance 0. `TARGETED_TRAVEL_CONNECTORS.tsv` содержит ровно два D1-schema row: strict `DEST_TO_ANCHOR VALID_IDENTITY` с exact XYZ, всеми нулевыми метриками и `CANONICAL_IDENTITY`; `ANCHOR_TO_GK VALID_DIRECT` до Bilia `87048,-143448,-1293`, длина 107. Generic same-point остаётся `NO_MOVEMENT`. Supplement SHA-256 `689e6adb216b7ba609f6fd4a59db1d5fca19439838d30b015142a9a250e6492e`, manifest SHA-256 `105aa55e432ad7d20cf9f38d685bf504f5e198f32f4d62c079d7c9b63cf6f8df`. Исходный D1 connector SHA `fe0c0433e8975ff43f397470eca5caf0ae3b545e4d66d0ece2096d1ed97c1926` остаётся immutable; runtime catalog root связывает оба SHA.

`normal_gk_catalog.py` выполняет общий factual join, source connector требует positive VALID_DIRECT/PATH, destination дополнительно допускает только exact identity. Native `30540→Schuttgart` несёт `destinationCastleIds=9`, fee 4400; Bilia→Plunderous — пустой список и fee 1600. Java parser хранит immutable canonical IDs, `matchesNative` сравнивает их с native `TeleportLocation.getCastleId()` точно. При выключенном `TELEPORT_WHILE_SIEGE_IN_PROGRESS` source-town правило сохранено, а каждый destination castle разрешается и проверяется fail closed; native bypass при включённом флаге сохранён. Pure Java test проверяет пустой/9/неверный список, unresolved/siege policy и ранний отказ tampered supplement SHA без manager initialization. Fee/replay/transaction/cursor код не менялся.

Catalog: **5 → 12** factual legs, SHA-256 `ac1993910a88ec96f9842e0f8c435707331ea53be44346a856c485f1fcf8767b`. Дополнительные legs возникают только из общего join уже factual transitions и connectors; маршрут не захардкожен. Python fixture доказывает реконструкцию `BACKGROUND→GK1→GK2→BACKGROUND`; production-data static validator читает active topology, GK XML, factual TSV и native 30540/31964 teleporter XML, Plunderous spawn XML и NPC level. Из `population.ingress.dwarf.01` путь содержит 14 шагов, GK transitions `da2bf...` затем `bb1ab...`, и заканчивается в `_30`, где native Bandit Captain NPC22026 имеет уровень 40. Baseline GLOBAL ordinary 40–51 = **0**, опубликованный GLOBAL = **1**. `LIVE002_40_51_FINAL.tsv` SHA-256 `9447c798ef3a280a46d308d6a88338f1c056f5ca0d845d2e76bbd45b7052bd4c`.

Два свежих data-generation run побайтно совпали для 7/7 artifacts: generated-03, hermetic proof, Plunderous proof, supplement, manifest, catalog, final route. Все семь опубликованных файлов побайтно совпали с fresh run.

## Проверки и границы

- `ant compile-tests`: GREEN после ожидаемых RED компиляций новых seam contracts; первоначальная sandbox попытка получила `AccessDeniedException` при закрытии classpath JAR `HikariCP-7.0.2.jar`, разрешённый повтор прошёл. Чтение JAR компилятором не являлось Hikari initialization.
- `ant phantom-geodata-rules-test`: GREEN, включая fake door/fence oracle, raw bridge, XML fixture, secure parser и probe dependency controls.
- `ant phantom-topology-core-test`: GREEN, 38/38.
- `python -m unittest test_normal_gk_catalog test_live002_40_51_final`: GREEN, 5/5.
- Direct pure `PhantomNormalGatekeeperPureTest`: `NORMAL GK PURE: PASS`.
- Independent `live002_40_51_final.py`: `GLOBAL_ORDINARY_40_51=1`, baseline=0.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- Forbidden `phantom-normal-gatekeeper-travel-test`, `phantom-normal-gatekeeper-d2-focused-test`, `phantom-topology-production-corpus-test`, historical/canonical ingress и background DB targets: **NOT RUN**. `phantom.test.config` не использовался. `ant jar`=0; full `ant verify`=0; GameServer/LoginServer/runtime/DB connections/mutations=0.

Ограничение: production runtime/native manager integration намеренно не запускался по абсолютному safety gate. Здесь доказаны компиляция, pure contracts, hermetic GeoEngine и независимая static route; живое осадное состояние не проверялось. Другие хроники, 52+, Ruins, Cat/Necro и следующие Goals не затронуты. Goal token/time счётчик не предоставлен, число не выдумывается.
