# SOURCE_MAP — проверенные пути на HEAD 48a5c7f1 (25.10.2026)

Код ниже **лично сверён с опубликованной веткой `feature/phantom-world`** при составлении пакета через GitHub. `sha` — Git blob SHA-1; это review anchor, **не** SHA256 manifest файла. Перед работой Codex повторяет fetch и actual local чтение; при changed HEAD не притворяется, будто подписи остались прежними.

Все пути Java/данных ниже относительны модулю `L2J_Mobius_CT_2.6_HighFive/`.

| Owner/source | Git blob SHA1 | Что реально найдено |
|---|---|---|
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java` | `15a943961c054d105e2f4a862f3d88354378bfb9` | bounded graph overlay: 128/512/32/4096, 1024 stride и 3000 local span; NO_ROUTE и SEARCH_LIMIT отдельные |
| `java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java` | `da69567fc3398dbf6474ed689930fdc27ebd2e8a` | `findWalkCorridor`, `planTravel`; 7 limited near single-GK proposals; native PathFinding; `canWalkDirect` |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeTravelActions.java` | `cc995005be7fb5daf9dc40c63c46f7e3bad0c06d` | `snapshot`, `gatekeeperAt`, `available`, `gatekeeper` invokes native NPC bypass after actual canInteract, records funds/coords |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeRouteContract.java` | `98504b5145fd1a8e01980f26f90cffd97359a69c` | `normalize`/`validateSegments`: completed corridor proofs |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java` | `823ee6ae9817bd1fa16264eb8172e043149e9eb3` | `load/parse/matchesNative/observeNativeLeg/route/search/fee`; D1 sha provenance and topology + native NORMAL graph |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java` | `839ad558a57d5c692b1949c1ff14f08271eb6652` | `proposedStep/resumeStep`, `advance`, `executePlan`, `walk`, `terminalResult`, arrival checkpoint; native `MOVE_TO`, owner+retry |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java` | `2514495a9157fca8ae68ac3f9c0c71281cdad5dc` | `candidate()` requires `_authority.travelQuery(_topology).route(currentAnchorId, anchorId)` for global target |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyLoader.java` | `7266f35b8b6282e444b3837da18ecd09c7a22c39` | source-linked anchors, edges with validation; read first before any source change |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationPolicy.java` | verified live path | immutable bounds; production defaults 12k local, 64 waypoints,100k route,1s deadline |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomTravelStrategy.java` | verified live path | Candidate contains ≤64 stages/64 waypoints, selects time/cost under actual resources |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java` | verified live path | queue, workers, cache/cancel, `submitWalkCorridor`, `submitTravelPlan`, progress tracker |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java` | verified live path | existing durable background/arrival/checkpoint/goal owner; use instead of parallel state machine |
| `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java` | verified live path | travelQuery/native source, locality, committed anchor authority |
| `dist/game/data/phantoms/travel/high-five-normal-gk.xml` | `851c77f77d64ac3ef27399826f5daab2c29c0dcf` | 39.6KB LIVE-002-D2/1, native provenance exact SHA fields, lacks 30006/30146/30576 as certified departures |
| `tools/phantom-runtime/Task087.ps1` | `bd5959c552143a02bfd1315a6e3d152b2a5fa9b4` | owned physical copy, mode flags/ACL, focused isolated test lane. **Reference only**, not 089 runtime. |
| `test/java/org/l2jmobius/tests/phantoms/PhantomUniversalRegionTravel087.java` | `81fade9d6cad40e3edbaa6fc384f864a340a81b0` | 087 qualified actor/new JVM native measurements, no product fix |
| `test/java/org/l2jmobius/tests/phantoms/PhantomTask087Lane.java` | `21536394818a7fe4776b75f71c7e33aadc9e2a66` | one fresh schema UUID/ACL/source hash authorization, verify no reuse for089 |

**Data source anchors:**

- native teleports: `dist/game/data/teleporters/town/{30006,30146,30576,30080}.xml` — проверить существование каждого и actual NORMAL list. Не утверждать для отсутствующего файла наличие конкретной таблицы без read-first (087 видел corresponding holders).
- source spawn refs from audited087: `dist/game/data/spawns/Others/{17_25,21_19,18_14,22_22}.xml`.
- generated/curated topology: `dist/game/data/phantoms/topology/high-five-core.xml`, `high-five-generated-01.xml` ... `high-five-generated-07.xml`, другие validated XML. **Не mass rewrite**.
- 087 source audit: `test/region_travel087_source_audit.py` — `travel_backbone.extract_facts`, source-only provenance; not runtime source publisher.
- tests to reuse: `test/java/org/l2jmobius/tests/phantoms/{PhantomWalkCorridor064Test,PhantomNormalGatekeeperPureTest,PhantomTravelPlanning033RSuite,PhantomNormalGatekeeperTravelSuite,PhantomGeneratedHistoricalRouteSuite,PhantomLocalFarmRecoverySuite}.java`; focused farm074, spot085.

**Подтверждённые native facts из отчёта087:** 203 Geo, NPC 10482, spawn facts 30127, topology anchors 1817; три стартовых first-mile `SEARCH_LIMIT`, 0 paid GK, 0 farm rewards. Тестовые деньги/класс/экипировка были fixture, а не заработанными. Для фактической 089 сцены пользоваться только source data из **новой** guarded stage и текущим stock runtime.

Полные истории старых goals и исторические пути C:-checkout НЕ authoritative. Узкие дополнительные source файлы обнаруживает Codex read-first **до** stage, а не придумывает их имя или содержимое.
