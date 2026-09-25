# LIVE-002-RUINS-GEOPATH-BRIDGE — BLOCKED

Дата: 25.09.2026. **Статус: BLOCKED — `PRODUCTION_TARGETED_SHA_PIN_CONFLICT`.** `RUINS_OF_DESPAIR_REACHABLE_FARM=1` не заявлен.

## Read-first и scope

Прочитаны текущие `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md` из заданного абсолютного каталога, локальный `AGENTS.md`, master plan, workflow contract, task standard, предыдущий Ruins отчёт и текущий STATE. Сверены `PathFinding`, `NodeBuffer`, `GeoNode`, `GeoLocation`, normalized farm proof, `PhantomTravelGeoProbe`, `generated-03/04/05`, targeted supplement/manifest, generic GK generator и 81–85 final validator. Аналоги: штатный test-only hermetic Geo probe, Oren ROUTE/FARMING публикация в generated-05, directed BACKGROUND edges в generated-03. `README.md` и отдельные code-map/pattern-файлы в модуле не найдены; `readme.txt` прочитан. Соседние хроники не изменялись.

Отклонение от пользовательского read-scope: дополнительно были прочитаны первые 170 строк `docs/phantoms/tasks/LIVE-002-RUINS-NORMALIZED-POINT-FARM/TASK.md`. Этот task-файл находился внутри разрешённого `docs`, но вне единственного разрешённого каталога task-файлов. Repo-wide поиск `TASK.md` не выполнялся; предыдущий task-файл не менялся и не использовался для расширения scope.

Tracked HEAD до изменений `7033027dc8edb08972726de3bf1a0eebd67478ad`, ветка `feature/phantom-world`, upstream `origin/feature/phantom-world`. Несвязанные dirty/untracked файлы обнаружены и не включаются в task scope. Fresh DB-free Python graph audit на активных shards 01–05 и исходном GK catalog подтвердил ordinary progression **`5/13/15/3/1`**, 40 NORMAL legs.

## DB-free Geo proof

Добавлен только test-only `PhantomOversizedRawPathProof`: использует существующие `new NodeBuffer(required)`, `lock()`, `findPath` на Geo координатах, копирует `GeoNode` parent chain до `free()`. Production `PathFinding`, `NodeBuffer`, `PathFindBuffers` не менялись. Все три разрешённые oversized попытки израсходованы: primary Ruins→farm required1868 вернул null; Ruins→factual NPC20359 `-23089,130525,-3664` required852 и NPC→farm required1370 дали полную цепь из 1750 raw узлов. На каждом вызове существует только один временный buffer, размер ≤2048. Scratch `.phantom-local/logs/LIVE-002-RUINS-GEOPATH-BRIDGE/raw-a-star.tsv` не публикуется.

Из той же parent chain выбраны пять точных Geo точек с raw ordinals `240,623,825,1188,1572`; final endpoint — принятый factual farm anchor `-33539,137701,-3480`. Шесть fresh hops через штатный `PhantomTravelGeoProbe` с текущим production max500 прошли: пять `VALID_DIRECT`, последний `VALID_PATH`, required `448,448,448,448,448,238`, `STATIC_XML_CLEAR`, door/fence `0/0` на каждом. Отдельный Gludio→Bella connector прошёл `VALID_DIRECT`, length64, required72/max500, `STATIC_XML_CLEAR`. Всего 7 новых normal validations, backoff 0/20. Канонические выбранные rows записаны в `RUINS_GEODATA_PATH_PROOF.tsv` и datapack evidence TSV; их промежуточные координаты совпадают с raw ordinals. Первый и последний endpoint помечены ordinal `-1` как factual, а не raw node. Native NPC20059 center `-33539,137701,-3479`, normalized Geo Z `-3480`, radius1 подтверждены предыдущим accepted proof; в этом task повторный farm mapping после публикации не запускался.

Первый `ant compile-tests` не прошёл из-за sandbox `AccessDeniedException` при чтении существующего `HikariCP-7.0.2.jar`. Повтор той же цели с разрешённым compile-time доступом завершился `BUILD SUCCESSFUL` (2 старых deprecation warnings). Все Geo proof JVM запущены с classpath только `build/bin` и `build/phantom-test/bin`, без HikariCP jar. Hikari initialization/pool, DatabaseFactory, DB connections, GameServer/LoginServer = 0.

Проверка выбранных ordinal/координат против scratch raw chain и шести accepted proof rows прошла; canonical и datapack TSV побайтно совпали. Mojibake-маркеры в изменённых файлах проверены: 0. Escaped Cyrillic в изменённых файлах проверены отдельным regex: 0.

## Точный blocker публикации

Три требуемые targeted connector rows и generated-06 были пробно построены, generic GK catalog получил 53 NORMAL legs. Но production `PhantomNormalGatekeeperTravel.java` проверяет `TARGETED_CONNECTORS_SHA` как константу старого supplement (`881c6be...`). Новый supplement имеет иной SHA, поэтому текущий runtime loader отвергнет корректно регенерированный catalog. Задача явно запрещает изменения production Java travel, а запись старого SHA в новый catalog нарушила бы provenance. **Это конфликт требований на publication boundary, не Geo blocker и не доказательство GREEN.** Production Java и runtime semantic не менялись.

Пробные generated-06 и три connector rows удалены; targeted supplement, manifest, generic GK catalog и Python generator возвращены к исходным байтам. Активные generated-01..05 сохранены. Только test-only helper, два evidence TSV, этот отчёт и STATE остаются task-артефактами. Independent final validator, topology publication gate и два byte-identical generation runs не выполнялись после остановки: активного Ruins shard нет. `ant jar`, full verify, старый geodata corpus, guarded DB targets — 0. Closed/LIVE-003 не начаты.

## Scope guard и дальнейшее действие

Exact allowlist этого BLOCKED checkpoint:

- `test/java/org/l2jmobius/gameserver/geoengine/pathfinding/PhantomOversizedRawPathProof.java`
- `dist/game/data/phantoms/evidence/live002-ruins-geodata-path.tsv`
- `docs/phantoms/live-world/RUINS_GEODATA_PATH_PROOF.tsv`
- `docs/phantoms/reports/LIVE-002-RUINS-GEOPATH-BRIDGE.md`
- `docs/phantoms/live-world/STATE.md`

Для GREEN нужен отдельный review, разрешающий обновить production SHA pin без изменения travel semantic, либо согласованное иное provenance решение. До этого generated-06/connectors/catalog активировать нельзя. DB/migrations/config changes отсутствуют; performance impact на runtime отсутствует. Audit commit `d0e22cf83919d69c122d54a5d183f86c1098d760`; SHA отдельного report correction commit и результат normal push приведены в финальном сообщении выполнения task.
