# LIVE-002-B — spatial/topology candidate graph

Дата: 23.09.2026. Ветка `feature/phantom-world`; обязательный исходный tracked HEAD `c7219c2db451eaa6a36be1e8b1c9bcf7077aac25`. Статус **SUCCESS / candidate graph GREEN**. Первый implementation commit `e0f6d883d36e2654da8a43b6cadb61412749acd0`; итоговый corrective commit указан в handoff: SHA самого commit нельзя поместить внутрь него без изменения SHA.

## Scope и источники

Созданы `Generate-TopologyCandidates.ps1`, `Test-TopologyCandidates.ps1`, `Validate-TopologyCandidates.ps1` и четыре канонических файла в `docs/phantoms/live-world/`. `WORLD_COVERAGE.tsv` использован без изменения. Источники: 28 mapregion XML, 48 zone XML, 198 teleporter XML и существующий `high-five-core.xml`; SHA-256 каждого из 275 файлов записан в `TOPOLOGY_CANDIDATE_MANIFEST.json`. Core SHA-256: `9a516aa07ee681f05a39f779c131cedd014d444d6bde890e85e6c3288bd164d0`. Teleporter `data/teleporters/town/30256.xml`: `3fc772116712fda45b233eb3e06ce73e60afe0cecd210168fa0a99f1b545a623`.

Принятые SHA-256 неизменны: coverage generator `2bb686b8f0d6494f69ee92c6098390388c1670c9617407bb9cf75def798d1c2a`, input aggregate `468f2411920df9d5a40833a97ecdff70fba7a1586080e20fa8fe33037f8b7695`, `WORLD_COVERAGE.tsv` `84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e`, `WORLD_DATA_MANIFEST.json` `94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4`.

Read-first: `AGENTS.md`, master plan, workflow/task standards, TASK/EVIDENCE/ACCEPTANCE, LIVE-002 roadmap/STATE, LIVE-002-A report, оба coverage scripts, topology loader/query/snapshot/policy/backend и две topology suites (ограниченные диапазоны). Представительные source XML: `mapregion/giran_castle_town.xml` (grid), `zones/zone.xml` (polygon), `teleporters/town/30256.xml` (Ruins и направленная destination), `spawns/Others/22_21.xml` (point), `spawns/ElvenTerritory/ElvenStarting.xml` (polygon), `phantoms/topology/high-five-core.xml` (node/anchor/edge). Поиски были точечными; корпус XML и тысячи строк TSV модель не читала. Отдельные `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md`, `DEVELOPMENT_CHAT_HANDOFF.md` и code-map в модуле не найдены.

## Алгоритм и результат

Генератор перепроверяет LIVE-002-A validator и принятые хеши до генерации. Native source group сопоставляется с coverage по source path, group и geometry fingerprint. Exact core требует того же source, instance, точной polygon/point geometry и совместимого anchor NPC, если он указан; сходство имён не считается доказательством. Mapregion определяется native grid `(x >> 15)+20, (y >> 15)+18` с тем же детерминированным порядком map files, что в coverage generator. Zone relation использует XY point-in-polygon native zone; без source Z это только пространственная ассоциация. Landmark — ближайшая native teleporter destination по XY к source sample point; расстояние не доказывает членство. `source_refs` будущей topology entity ограничены её native spawn source и, для core, core XML; подробные spatial evidence refs остаются в отдельной таблице.

| Показатель | Значение |
|---|---:|
| Ordinary READY_STATIC accounting | 2 652 / 2 652, по одной строке |
| Exact existing core | 20 |
| Generated farming candidates | 2 632 |
| `READY_FOR_GEODATA` | 81 |
| `NEEDS_ANCHOR_GEODATA` | 2 551 |
| Ambiguous / duplicate node / anchor / route IDs | 0 / 0 / 0 / 0 |
| Unmatched existing core farming nodes | 3 |
| Closed-source groups without new walking edges | 676 |
| Existing core edges | 83 |
| New LOCAL_WALK / REGION_LINK | 3 442 / 108 |
| Factual directed teleport rows | 1 474 |

Три core farming nodes без доказанного exact match: `giran.farming.22859`, `population.farming.human-fighter.20545`, `population.farming.kamael.22228`. Они не переназначены по имени. Generated IDs — digest от неизменного `coverage_key`, проверяются на коллизии с core и друг с другом. Polygon без source-backed Z не получает выдуманный anchor. Все 3 550 новых walking/region rows — `NEEDS_GEODATA`; максимальная степень нового walking graph — 4, расстояние ограничено 12 000, индекс — 3×3 соседних native grid cells, instance совпадает. После RED regression для catacomb-to-outdoor edge из walking index исключены 676 ordinary groups с фактическими source paths `data/spawns/Catacombs/*`, `Aden/TowerOfInsolence.xml`, `Giran/DevilsIsle.xml`, `Goddard/ImperialTomb.xml`, `Oren/IvoryTower.xml`: для них нет явного door/room route evidence. Их topology accounting rows и существующие core edges сохранены. Existing edges сохраняют ID, factual teleport идёт только от native NPC к заявленной destination; обратная дуга не выводится. Ни один кандидат не загружен runtime и не получает `backgroundEligible`.

Точные core mapping rows из `TOPOLOGY_CANDIDATES.tsv` приведены ниже. Для каждого из этих 20 rows `anchor_id` равен указанному `node_id` и сохранён из `high-five-core.xml`.

| Coverage key | Existing core node/anchor ID |
|---|---|
| `d3658f356d4b9c218c7cbc490e8533938cd0d9ac513cc3b2eae0b8cc9381e587` | `elven.farming.oren04-2019-02s` |
| `a8c0cfcec953b90b09bfd4224a43f6b254279853ed169dc0249d1625908ed1ae` | `elven.farming.oren04-2019-03s` |
| `d13185ae56587f5cb80fb75cbb15f8a069de965e5aee823b48a038db236fda23` | `elven.farming.oren04-2019-07s` |
| `f12e1ec9c2f5eded835d648b03a054a833889e8140def87b25c8e2485615ea81` | `elven.farming.oren04-2019-08s` |
| `96ae8108caf85bb707da82d808aa816e7cffde121af3179e2d3aba186b530276` | `elven.farming.oren04-2019-10s` |
| `68c39494dd0b7414fb22f5d4cb5174dbc8fa62c4a3d6b57cda68850595f4c895` | `elven.farming.oren04-2019-11s` |
| `ad16d4b9e3930e84930708adb30c971a069e56a3a2ce1421293733af2e02d789` | `elven.farming.oren04-2019-15s` |
| `bc72510879513ea6016e6ff66915ea364ae3acefc11216f6a835cb9bc3683d17` | `elven.farming.oren04-2019-16s` |
| `1e0614f9fd9c18e6537aa5f7931489526339fb7fbce2fee7cc37e34c5d5064b1` | `elven.farming.oren04-2019-19s` |
| `83d3f1fec9580d5628932e77857a4e49f1a9222a7d3fbd440b5c0512cdb6cc4b` | `elven.farming.oren04-2019-21` |
| `8d07a64410270111dda4bb6d9bd4fcfd6859210a814bd09042496829b343ea1f` | `elven.farming.oren04-2019-241s` |
| `f81fed46952eb35c183673d5c53b88238630e2766fe45d7aaec5e709a6ac2a50` | `elven.farming.oren06-2120-04` |
| `c955dad602eac346477cfbdce3ebd832a98f65a299797ae135ab6c2ee77673e8` | `elven.farming.oren06-2120-06` |
| `f80d2dc48bbde40c138e7d8a0d6b26b47bc1d2be86f4b585adb2ae4eb1971877` | `elven.farming.oren06-2120-07` |
| `52e04851f566584b91543d5d7f34f2aadaf1710885e88a91aafc1d382c94b4b7` | `population.farming.dark-elf.20529` |
| `38c89747141fb9d2d99ed3f325c90565d1e7de5dc51afe28e21a03175357430f` | `population.farming.dwarf.20533` |
| `4b565d1fb9786be38cf9f51e86cc5b226a7682bb2e524576100ca76e1a985a68` | `population.farming.elf.20534` |
| `f468bea42714b1542d09ef43b13cb6379641b3c7870f03111a25599574f95af8` | `population.farming.human-mystic.20481` |
| `b23351f226ffa46e9e3b15380ec02e9910eb4b47a858bbb98d2d32fa3b63a9d3` | `population.farming.orc.20535` |
| `05f9c3cd9ed64ffb35b5263b11786547e0e7a7f509a3744cd16e71ffec705ba8` | `talking-island.farming.gludio31-1624-11s` |

## Level bands

Группы с диапазоном NPC level, пересекающим band, учитываются в каждом пересечённом band. Компоненты относятся к **невалидированному candidate graph**, не к реальной проходимости.

| Band | Groups | Regions | Core | Generated | Await geodata/anchor | Candidate components |
|---|---:|---:|---:|---:|---:|---:|
| 1–5 | 99 | 6 | 5 | 94 | 94 | 11 |
| 6–10 | 149 | 5 | 0 | 149 | 149 | 22 |
| 11–19 | 223 | 7 | 15 | 208 | 208 | 15 |
| 20–39 | 584 | 11 | 0 | 584 | 584 | 122 |
| 40–51 | 563 | 9 | 0 | 563 | 563 | 235 |
| 52–60 | 254 | 9 | 0 | 254 | 254 | 143 |
| 61–75 | 717 | 8 | 0 | 717 | 717 | 279 |
| 76–80 | 376 | 8 | 0 | 376 | 376 | 103 |
| 81–85 | 81 | 4 | 0 | 81 | 81 | 10 |

## Ruins of Despair candidate-set evidence

Native `data/teleporters/town/30256.xml` содержит `Ruins of Despair` в `(-19120,136816,-3752)` как destination NPC 30256; это фактический teleporter label/location, не геоданное членство spawn group. Для следующего checkpoint ниже **10 ближайших ordinary READY_STATIC coverage groups по расстоянию XY до ближайшей source vertex/point**. Для polygon это расстояние до вершины, а не до границы или доказанный путь. Ключи и уровни взяты из принятого registry.

| Coverage key | NPC levels | XY distance |
|---|---:|---:|
| `b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3` | 15–29 | 14 334 |
| `38437b55476d236dac633021da7467aec32ccb5971a087c0d7364c0364b71f2a` | 25–27 | 30 027 |
| `a993df05ccc2b7aacfce6b420e50459a29da86cb9c499e6996e426c60a7e8a55` | 25–26 | 31 197 |
| `e5f729fdaee258fc47cd7e7d98f3deaa401842fb9448589958eae93b1663a44d` | 25–27 | 32 271 |
| `71ca298b5ca60e08e7df1bc9cde9a00f004be5bd9cbce5f117aa0c5e1b3467fe` | 28–30 | 32 480 |
| `a569d06074b5842ba1ba13c18b41ddd16c267b2f4d5f81178491272f6ce0057b` | 24–26 | 33 164 |
| `286654e4ee4ce4c034fbee2f2535a788803c0b549649e3d0b63f2ca99367b842` | 27–30 | 33 270 |
| `61295e4685dc1ed6dd4c6cd6181cdab14ca6353f81278d2c8e2376a690a307b9` | 27–29 | 33 435 |
| `6b0c3b8a8b12f57e49ffddee812bee7c88aa50e2162b384cb9c237447b4728d2` | 25–27 | 33 995 |
| `29e338532c483e5b6e97557dfbdd9e980987f639d5e6c7bc5a28dd6673389f19` | 35–35 | 35 243 |

У ordinary groups нет source sample point в радиусе 12 000; ближайший ordinary source vertex/point — 14 334. Шесть source groups в радиусе 12 000 по sample point принадлежат принятому классу `NON_FARMING / EXCLUDED`; этот checkpoint не переклассифицирует их. Кандидатный список выше — предмет проверки LIVE-002-C, **не** утверждение, что группы находятся в Ruins of Despair или достижимы от него.

## Проверки и границы

- `Test-TopologyCandidates.ps1`: GREEN; focused fixture покрывает exact core/false positive, mapregion/zone/teleport, polygon anchor, collision, reordering/path normalization, same-instance routes, fanout, catacomb isolation, teleport direction и byte identity. Catacomb regression сначала RED, затем GREEN.
- Два неизменных production generation run: GREEN и byte-identical для четырёх canonical outputs. SHA-256: spatial `8ce940eeba2285ced494b5880be5f624eb8eba91a4ed24b5c7d5688e15600935`; topology `aec029b27e0f9e8f04a86afc16b3c1a5e7e6ddbaedc7a37f25b0b2f08b7c873f`; routes `b757c0690c4890f5d3464cc921897087841261766f560b838f90a80f5a829bfa`; manifest `d5ebd388bf59a9ee24c59d8bfe801c72c0f2783a2c92c49f4b0a1d5948039a6a`.
- `Validate-TopologyCandidates.ps1`: GREEN; независимо сверены 2 652 keys, accepted inputs, output/source hashes, NPC/level facts, ID uniqueness, core ownership, все 1 474 native направленные teleport relations, same-instance walking, closed-source exclusion, degree/distance bounds, statuses и отсутствие validated новых walking routes.
- `git diff --check` и exact staged scope guard: GREEN перед commit. Полный `ant verify` — 0, `ant jar` — 0; server/runtime/DB — 0. Topology Java corpus suite не запускалась: её `beforeAll` инициализирует headless GameServer environment, тогда как этот checkpoint не требует boot.
- Existing `high-five-core.xml`, loader, runtime, пользовательские config/rates/heap/schedules/population и другие хроники не изменялись. Предсуществовавшие dirty/untracked user files не включены в staging.

Goal `/goal` вызван один раз; подготовка, корректировка после аудита, генерация и проверка заняли около 40 минут. Ограничение результата: геодата, walkability и публикация topology остаются LIVE-002-C. **NEXT_ACTION: LIVE-002-C — GeoEngine/path validation and publication of validated generated topology.** Автоматически не начинать.
