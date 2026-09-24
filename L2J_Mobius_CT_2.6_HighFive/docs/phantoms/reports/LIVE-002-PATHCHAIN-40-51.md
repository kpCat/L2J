# LIVE-002-PATHCHAIN-40-51 — диагностика лимита PathFinding и bounded chain

Дата: 24.09.2026. Статус: **BLOCKED — `NO_BOUNDED_WAYPOINT_CHAIN`**. Ветка `feature/phantom-world`; исходный tracked HEAD `a9a2fa5fd831c0763a8f948191b8ee072570aa9a` совпал с обязательным baseline. В рабочем дереве до задачи были чужие dirty/untracked файлы; они сохранены и не включены в scope.

## Root cause прежнего `NO_PATH`

Фактический `dist/game/config/GeoEngine.ini` задаёт `PathFinding=2` и `PathFindBuffers=100x6;128x6;192x6;256x4;320x4;384x4;500x2`; максимальный `mapSize=500`. Native `PathFinding.findPath()` вычисляет `64 + 2*max(abs(deltaGeoX),abs(deltaGeoY))` и возвращает `null`, когда `alloc()` не находит буфер. Для `Abandoned Coal Mines (139714,-177456,-1536) → generated.farm.4c20d26f8b57611bdafd0d33.anchor (124885,-159590,-1288)` координаты geodata равны `(49692,25773) → (48765,26889)`, `required_buffer=2296`. Реальный DB-free GeoEngine probe повторно подтвердил `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE`, `path_length=0`, `segments=0`; это исправленная причина прежнего `NO_PATH`, а не доказательство физической геодатной непроходимости.

`PhantomTravelGeoProbe` теперь пишет `required_buffer`, `max_pathfind_buffer` и различает `DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE` от `NO_PATH_WITHIN_BUFFER`. `PhantomGeoValidationRules` сохраняет native direct-first, door/fence/instance checks и проверку каждого сегмента пути. Production `PathFinding`, `GeoEngine`, `GeoEngineConfig`, geodata и `GeoEngine.ini` не менялись. Два fresh proof TSV для точной пары побайтно совпали (`fc /b`: differences=0); журналы и оба TSV — в `.phantom-local/logs/LIVE-002-PATHCHAIN-40-51/`.

## Hierarchical corridor probe

Проверены ровно текущий destination и один обычный опубликованный 40–51 farm. Фактический набор внутри bounding box ±8192: 9 действующих topology anchors и 1 native NORMAL GK destination; поиск только по ним не замкнул путь. Детерминированный grid 2048 имел 282 кандидата; уточнение 1024 только у разъединённых source/target и наблюдённых фронтов дало максимум 620 кандидатов в обратном диагностическом запуске и 582 в итоговом прямом. Пределы: не более 2500 nodes, 8 compass neighbors на узел, только instance 0. Z каждой пробной точки получен повторяемым `GeoEngine.getHeight`; исходные фактические точки сохранены отдельно. Каждый рассмотренный hop допускался только после `canMoveToTarget` или локального `PathFinding.findPath` в пределах текущего буфера с повторной `canMoveToTarget` проверкой segments. Длинный direct hop мог пройти только при native direct proof.

Итоговый прямой поиск: `582` candidates, `63` достигнутые точки, `0` hops полного маршрута. Ближайший достигнутый к farm фронт `(128981,-170288,-200)`; обратный диагностический фронт со стороны farm — около `(122837,-165734,120)`. Локальное 1024 уточнение обоих фронтов не связало их. Это **bounded negative result**, а не утверждение, что геодата физически разъединена в целом. `BAND_40_51_PATHCHAIN.tsv` имеет только schema header: выбранных доказанных hops нет. Новых `ROUTE` anchors и `BACKGROUND` edges не опубликовано; reverse edge также нет. D2 catalog и транзакционная семантика не менялись, generic regeneration не требовалась.

Независимый read-only пересчёт активного topology и пяти admitted D2 catalog legs: `GLOBAL_40_51_RECOMPUTED 0`, опубликовано `136` ordinary farm anchors. До задачи также было `0`. Поэтому GREEN gates полного маршрута, `GLOBAL_40_51>=1` и двух byte-identical **chain** proofs не достигнуты. `BAND_40_51_PROOF.tsv` не менялся по правилу TASK: его прежняя строка `NO_GEODATA_PATH:destination_connector:NO_PATH` сохраняется как историческая blocked запись; актуальная причинная классификация указана выше и в новых proof TSV.

## Проверки, scope, доставка

- RED: `phantom-geodata-rules-test` завершился ожидаемыми ошибками отсутствующих `requiredBuffer`, `maximumBuffer` и cap-aware overload; затем focused GREEN.
- Финальный `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml phantom-geodata-rules-test phantom-topology-core-test`: exit 0; Geo rules PASS; topology core 38/38.
- `python -m unittest test_final_geo test_travel_backbone test_normal_gk_catalog`: 14/14, exit 0.
- Mojibake-маркеры в изменённых файлах проверены: 0 совпадений.
- Escaped Cyrillic в изменённых файлах проверены: 0 совпадений.
- SHA-256: `PhantomGeoValidationRules.java` `347163be99b6db482d16e8f473c9e673b8a4a16fe322264e1d1eb7588bd83f64`; `PhantomPathChainSearch.java` `f7e6a3d58cecf1276411b2dfd83db981809c7618744906091eb08920c0f74a25`; `PhantomPathChainProbe.java` `1d7df1ee80fa07804b56ffee8214a16e6ad3d1082f368e941066a4b4b5a15f4e`; `BAND_40_51_PATHCHAIN.tsv` `9f1565c23969d907ddb48ebb547e9ebdbe4283050d7eab6047bf65be5d3cc5f0`. Отчёт и STATE имеют отдельные hashes после записи и не фиксируют собственный hash внутри себя.
- Actual GeoEngine: 203 geodata regions, 1301 native doors, 1 fence; запуск DB-free с ожидаемыми startup предупреждениями о неинициализированном DB pool. GameServer/LoginServer не запускались, DB connection/mutation=0, `ant jar=0`, full `ant verify=0`.
- Изменены только `test/java/org/l2jmobius/tests/phantoms/PhantomGeoValidationRules.java`, `PhantomGeoValidationRulesTest.java`, `PhantomTravelGeoProbe.java`, новые `PhantomPathChainSearch.java` и `PhantomPathChainProbe.java`, `docs/phantoms/live-world/BAND_40_51_PATHCHAIN.tsv`, этот отчёт и LIVE-002 checkpoint в `STATE.md`. Пользовательские dirty/untracked файлы и другие хроники сохранены. Production topology/catalog/runtime/DB не менялись.
- QUOTA_GUARD: одна root-cause и один corridor; ниже target 160k Goal tokens. Другие bands, Ruins, Cat/Necro, 52+, LIVE-003/004/005 не начаты.

Следующее действие требует отдельного review: иной bounded factual corridor или дополнительное фактическое evidence прохода между фронтами. Текущий blocker — ровно `NO_BOUNDED_WAYPOINT_CHAIN`; LIVE-002 остаётся открытым. Exact-path commit и normal push выполняются после финального diff/scope guard; SHA и результат push сообщаются отдельно, так как SHA нельзя записать в содержащий его commit.
