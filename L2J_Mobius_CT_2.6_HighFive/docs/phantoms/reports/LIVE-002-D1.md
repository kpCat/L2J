# LIVE-002-D1 — factual world travel backbone

Дата: 23.09.2026. Ветка `feature/phantom-world`; обязательный tracked baseline `0b7732eaeaa7368776e56b42113491e3f1e1c5ed`. **CODE_STATUS=GREEN для D1 structural evidence; LIVE-002 в целом открыт.** Factual GK transitions здесь логические доказательные связи, а не исполняемые `BACKGROUND` edges и не runtime travel.

## Read-first и scope

Прочитаны `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md`, отчёт LIVE-002-C, LIVE-002 checkpoint в `STATE.md`, C manifest, README, релевантные части core/generated/siege XML, native `TeleporterData`, `TeleportHolder`, `TeleportLocation`, `SpawnData`, `MapRegionData`, `GeoEngine`, `PathFinding`, `GeoEngineConfig`, `DoorData`, `FenceData`, текущие world-data scripts и `PhantomGeoValidationRules`. Дополнительные exact sources понадобились для `TeleportType` (типы), `Inventory` (Adena ID=57), `PlayerConfig` (free level/karma/siege), native XML XSD и fixture runner. Отдельных AGENTS.md, code-map, `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md` и `DEVELOPMENT_CHAT_HANDOFF.md` в модуле нет; применены переданные пользователем AGENTS.md инструкции.

Локальные аналоги: A/B PowerShell source scan и canonical TSV/manifest; C Java `PhantomGeoValidationRules.route` (direct, bounded path, повторная проверка сегментов) и headless geodata proof; существующий LF `.gitattributes`. Переиспользованы native типы, порядок holder locations и текущие core/generated topology anchors. Bounded exception по числу файлов: D1 TASK сам требует 10 выходных артефактов; добавлены только Python machine builder и Java Geo probe к указанным scripts/artifacts. Active topology XML, planner, background authority, economy, materialization, runtime, DB и конфиги не менялись.

До патча проверены branch/HEAD и все пять принятых C SHA-256: `ANCHOR_GEODATA_VALIDATION.tsv` `42a3987c12a74cc9cad369e919680ef6798488262eee4dcfbddc3ef391b79b04`; `ROUTE_GEODATA_VALIDATION.tsv` `1c1d4b19669d704c162e971fcc6a24bdd3cb1325bc3783d9cfadc552a326a22b`; `RUINS_GEODATA_VALIDATION.tsv` `785d75c6a5909180bb88c3069786ef0b663bbbbbb1d1f2cb6da10f5214c5fb4b`; generated XML `9242aaf4f4d711cbf99311f2be4f049110c83acb1e242bd7ca22dbcd03e051c4`; C manifest `82e76d497c496cdd69c13df49c98dcfff25e4b26bff813ee8acc97b5724775ce`. Принятые A/B outputs также совпали; generator проверяет их перед каждым run. Native aggregate SHA-256: teleporter `b16c00b7d5907db15e01f49d1cb664e61ad1acaf0f897dae47ef7b0f5d357558`, spawn `888b5be81a9329292743ee67d58678fb3a58fa4a2db3f944315d47878f0b44d2`, mapregion `fab659b2649f10f27efd538ebbaaefdf72bc3b7fb258046dcdbd2f0b9a7c992f`, geodata `e94d36db89fa58e13e679f90673cd7bc62e5362292e3390cf878a6d2ef4da7c2`. GeoEngine загрузил 203 региона.

## Факты, connectors и стоимость

Машинно учтены 1 756 native NPC/list/destination отношений, развёрнутых в 1 813 строк с каждым factual point spawn отдельно. Для 396 строк источник spawn либо runtime list merge не даёт однозначной статической точки/holder; они явным образом `BLOCKED_SOURCE`. Остальные: 256 `NORMAL`, 237 `NOBLESSE`, 924 `SPECIAL_OR_UNMODELED`. Factual transition counts: 256 `FACTUAL_NORMAL`, 237 `FACTUAL_CONDITIONAL`, 924 `UNSUPPORTED_SEMANTICS`, 396 `BLOCKED_SOURCE`. Reverse transition не создаётся. `feeId`, `feeCount`, destination index, `castleId`, NPC/list/type и source сохраняются по native XML/TeleportLocation defaults; 783 zero fee, 908 Adena fee, 122 other-item fee facts. Native conditions указаны ссылками на `TeleportHolder.doTeleport`, `shouldPayFee`, `calculateFee` и `PlayerConfig`; D1 не вычисляет affordability, noble/karma/siege/flag или временную скидку конкретного профиля.

Spatial index: cell 8 192, same + eight adjacent cells, XY radius ≤8 192, ≤8 ближайших eligible anchors на endpoint, направления проверяются отдельно. Eligible roles: `ROUTE`, `RESPAWN`, `CITY_CENTER`, `GATEKEEPER`, `FARMING`; room/door anchors не берутся в кандидаты. Из 1 602 directional connector candidates GeoEngine доказал 84 `VALID_DIRECT` и 89 `VALID_PATH`; заблокированы 1 366 `NO_PATH`, 61 `PATH_SEGMENT_BLOCKED`, 2 `NO_MOVEMENT`. Every accepted path segment повторно прошёл native `canMoveToTarget`; тот же native вызов проверяет двери и заборы, cross-instance отвергается до GeoEngine. XY proximity без положительного proof не опубликована. Все 1 602 candidates имеют отдельный result row; unique connector/transition IDs и полный source accounting проверены валидатором.

## Reachability

Граф содержит существующие/published directed `BACKGROUND` movements, доказанные local connectors и только `FACTUAL_NORMAL` transitions во втором режиме. Всего 939 published factual farming groups (919 generated и 20 exact core); пересечение level intervals даёт totals по band ниже. В manifest перечислены все 38 ingress roots семи factual families с source refs, координатами и component ID; матрица содержит 72 строки (7 families + GLOBAL × 9 bands). `WALK_ONLY` не включает GK transition. `WITH_FACTUAL_NORMAL_GK` означает лишь структурную достижимость при будущей D2 реализации native NORMAL условий.

| Band | Published groups | WALK_ONLY | NORMAL_GK | Remaining | Disconnected components |
|---|---:|---:|---:|---:|---:|
| 1–5 | 43 | 13 | 13 | 30 | 20 |
| 6–10 | 50 | 0 | 0 | 50 | 31 |
| 11–19 | 57 | 0 | 6 | 51 | 30 |
| 20–39 | 192 | 0 | 4 | 188 | 83 |
| 40–51 | 136 | 0 | 0 | 136 | 73 |
| 52–60 | 52 | 0 | 0 | 52 | 20 |
| 61–75 | 337 | 0 | 0 | 337 | 100 |
| 76–80 | 154 | 0 | 0 | 154 | 86 |
| 81–85 | 36 | 0 | 0 | 36 | 28 |

Только Dwarf ingress получает новые GK reachability: 6 groups в 11–19 и 4 в 20–39, каждый shortest proof использует один NORMAL transition. Пример 11–19: `population.ingress.dwarf.01` → native `population.travel.dwarf.*` → `population.farming.dwarf.20533` → `generated.route.4e6554c822ccedad3b0e40d8.f` → `generated.farm.ea72d768f3c8ec3d79c1a530.anchor` → `connector.b0c14ba43d9b0dcc750959ab` → factual GK `spawn.cd48f4a126ad9fb6d9a60ca2` (NPC 30540) → `transition.e4b886ff65a767c9d77f3d3f` (NORMAL, feeId 57, feeCount 970) → `dest.c22f3e4ebc26119cb4e14b22` → `connector.7e6e29d68eef267f072df2f2` → `generated.farm.088e8bc59766f22991a08e5d.anchor`, coverage `088e8bc59766f22991a08e5d3c7c53d499e6ed75997691ceabef230793bf46a4`. Для 20–39: тот же proven GK access, `transition.e904d3aed33a9cecd852c15c` (NORMAL, feeId 57, feeCount 12000) → `dest.5a41f45cf22daa12bbd6a4c0` → `connector.3182520a63d2e193cfb5cbc1` → `generated.farm.475038779aaee6a2e82aa06b.anchor`, coverage `475038779aaee6a2e82aa06bb07020267f709faa0fe11c99ccc8ece14784ee34`. Полные deterministic witness paths для всех reachable `(family, band)` лежат в `TRAVEL_REACHABILITY.tsv`.

### Ruins of Despair

Native source `data/teleporters/town/30256.xml`: NPC 30256, NORMAL destination index 16, `(-19120, 136816, -3752)`, feeId 57, feeCount 610. Factual spawn `spawn.c0fbe411a8ad3f8c4ad97ee4`; directed fact `fact.abdc7798f7b2491d57793ef2`. Из семи ingress families нет доказанного доступа к этому GK spawn; у Ruins endpoint нет GeoEngine-proven `DEST_TO_ANCHOR` connector к published graph. Structural reachability **не доказана**, exact reachable farming coverage keys: пустой набор. Близость 10 C candidate groups не заменяет путь: C уже заблокировал все 10 anchors. Это D1 structural result, не вывод о runtime fee/state.

## Verification и delivery

Canonical SHA-256 двух побайтно одинаковых fresh runs:

| Artifact | SHA-256 |
|---|---|
| `GATEKEEPER_FACTS.tsv` | `a8318075ef6ea3c3f266aa975ef1565fb8d6c93d2f074f076a059ed5d2ea2fe4` |
| `TRAVEL_CONNECTORS.tsv` | `fe0c0433e8975ff43f397470eca5caf0ae3b545e4d66d0ece2096d1ed97c1926` |
| `TRAVEL_TRANSITIONS.tsv` | `d378de9ddb395914c7e36480f149143f3cb9a2b84284ca625708d5c880f3e09d` |
| `TRAVEL_REACHABILITY.tsv` | `21a1e2d6fbe5f4e8dc1c18ea2b40a5b251d59661d4e32271ade13de065753421` |
| `TRAVEL_BACKBONE_MANIFEST.json` | `9da8da25ff76490ee0492f7a85279ade444981d5858ca1a1f043811e105fdec3` |

Fixture `Test-TravelBackbone.ps1 -SkipCompile`: 5 focused Python tests GREEN; существующий `PhantomGeoValidationRulesTest`: 17/17 GREEN (one-way, direct/path, segment block, no path, cross-instance, no geodata). `ant compile-tests` завершился GREEN после sandbox escalation; два первых sandboxed запуска падали из-за `AccessDeniedException` JDK при закрытии существующего library JAR, а не из-за D1 source. Два fresh production GeoEngine run дали побайтно одинаковые пять TSV/JSON файлов; штатный focused production validator самостоятельно повторно создал GeoEngine proof и дал GREEN для source facts, metadata, proof rows, reachability, unique IDs и output hashes. Source code hashes в manifest нормализуют LF/CRLF до UTF-8 LF, чтобы `core.autocrlf=true` не вызывал ложный drift после checkout; canonical TSV/JSON hash остаётся побайтовым. Raw logs: `.phantom-local/logs/LIVE-002-D1/`. Geo helper не инициализировал DB pool и не установил DB connection; native DoorData bootstrap вывел предупреждения об отсутствующем pool, но реальные DB операции не выполнялись.

Full `ant verify`=0, `ant jar`=0, LoginServer/GameServer=0, MariaDB connections=0, runtime delivery=0. Предсуществующие dirty/untracked файлы сохранены. Mojibake-маркеры в изменённых файлах проверены. Escaped Cyrillic в изменённых файлах проверены. На момент записи отчёта счётчик Goal: 252 679 tokens, 1 617 секунд; итоговый счётчик приводится в handoff. Git использован только для разрешённой baseline/scope проверки, `git diff --check`, exact-path stage/commit и normal push; commit/push IDs фиксируются итоговым handoff.

NEXT_ACTION: **LIVE-002-D2 — native background gatekeeper travel semantics**. Автоматически не начинать.
