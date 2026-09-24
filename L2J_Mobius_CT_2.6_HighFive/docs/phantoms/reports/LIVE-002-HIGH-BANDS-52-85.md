# LIVE-002-HIGH-BANDS-52-85 — bounded closeout

Дата: 25.09.2026. **Статус: PARTIAL / GREEN_PARTIAL.** Ветка `feature/phantom-world`; обязательный tracked baseline `4b2c5a8fb045b05b959966c87998053f40183891` подтверждён до изменений. Итоговый SHA commit и normal push указаны в финальном сообщении: собственный SHA нельзя включить в тот же commit.

## Read-first и scope

Прочитаны только абсолютные `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md` указанного пакета, локальный `AGENTS.md`, master plan, workflow contract, task standard, прежний отчёт hermetic closeout, связанные Python generators/validators, схемы canonical TSV, generated-03, native spawn/teleporter XML выбранных направлений и production loader/pin. `README.md` модуля не найден. Переиспользованы directed BFS из `live002_40_51_final.py`, generic factual join из `normal_gk_catalog.py`, deterministic TSV/XML writer из `travel_backbone.py` и неизменённый hermetic `PhantomTravelGeoProbe`. Код соседних хроник, инфраструктура GeoEngine и Java travel semantics не менялись. Bounded exception к порогу 8–10 файлов: один data-routing Goal требует одновременно generator, независимый validator, shard, connector/catalog provenance, proof и отчёт.

## Machine preflight

Текущий active graph до публикации: core/siege/generated-01/02/03, 12 factual NORMAL GK legs, 6 dwarf ingress. Независимый пересчёт: ordinary `40–51=1`, `52–60=0`, `61–75=0`, `76–80=0`, `81–85=0`. `HIGH_BANDS_52_85_PLAN.tsv` содержит 1428 band-overlap inventory rows, включая source, native level, anchor status, active/reachable flags, factual transition и destination connector. Closed/multifloor content исключён из выбора.

| Band | Ранг 1: единственный выбранный witness | Native level | Недостающее звено |
|---|---|---:|---|
| 52–60 | Aden NPC30848 → Seal of Shilen → `data/spawns/Aden/Cemetery.xml`, `aden09_2518_24` | 55 | Aden arrival→GK |
| 61–75 | Goddard NPC31275 → Hot Springs → `data/spawns/Goddard/HotSprings.xml`, `godard05_2414_13` | 73–75 | Goddard arrival→GK |
| 76–80 | Goddard NPC31275 → Varka Silenos Stronghold → `data/spawns/Goddard/VarkaSlenosOutpost.xml`, `godard28_2316_03` | 77 | Тот же Goddard arrival→GK |
| 81–85 | Допустимого активного open-world FARMING anchor нет | — | `NO_OPEN_ORDINARY_CANDIDATE` |

Для первых трёх выбранных целей уже были `VALID_DIRECT`/`VALID_PATH` destination connectors и активные same-source BACKGROUND paths до farm. Поэтому новых target-side ROUTE points и edges не потребовалось. Для 81–85 inventory дал SelMahums 31 и Field of Silence/Whispers 2 ordinary groups с `UNSUPPORTED_POINT_AREA` и нулём активных anchors; 25 Krateis Cube groups и 23 Giant's Cave groups являются закрытым/многоэтажным content. Новую farm anchor или runtime semantic этот Goal не добавлял. Candidate family attempts: 1/1/1/0, новых movement checks: 2 всего (Aden 1, Goddard 1), меньше лимитов 12/band и 48/Goal.

## Fresh Geo и публикация

Native Bilia NPC31964 ведёт в Aden `146783,25808,-2008` (castleId 5) и Goddard `148024,-55281,-2728` (castleId 7). В этих exact arrival points созданы два ROUTE anchors. Строгий `DEST_TO_ANCHOR VALID_IDENTITY` использует равные canonical XYZ и нулевые метрики. Один свежий hermetic JVM с 203 GeoEngine regions проверил только два положительных направления: Aden arrival→NPC30848 `146737,25807,-2013`, `VALID_DIRECT`, длина 47, buffer 68/500; Goddard arrival→NPC31275 `147966,-55228,-2728`, `VALID_DIRECT`, длина 79, buffer 72/500. Оба `STATIC_XML_CLEAR`, door/fence intersections 0/0. Runtime/DB/Hikari initialization не запускались.

`high-five-generated-04.xml`: 2 ROUTE nodes, 2 anchors, 0 edges, SHA-256 `39a69217a5d2c6cb713338e3943fd254d625f83819fbf68a504b7a6dbe9a99a7`. Targeted supplement: прежние 2 строки сохранены, всего 6; SHA-256 `689e6adb216b7ba609f6fd4a59db1d5fca19439838d30b015142a9a250e6492e` → `c4db321d399b9b5c14b858d203957da328668cf36bc49ae7009084ff47a88278`. Manifest привязан к generated-03/04, прежним proofs и новому Geo proof. Generic factual catalog: 12 → 30 legs, SHA-256 `ac1993910a88ec96f9842e0f8c435707331ea53be44346a856c485f1fcf8767b` → `b5bb0256ac5bd2a5d5384941d9b8ee2db5f7b21f128455efd6330be35f5d5551`. Production Java изменён ровно в одном SHA pin targeted supplement; parser, limits, routing и native semantics не менялись.

## Независимый итоговый proof

DB-free final validator независимо разбирает шесть active topology shards, catalog, base+targeted connector provenance, native teleporter/spawn XML, native NPC levels и coverage. Он проверяет exact identity, положительные source connectors, SHA pin, selected target role `FARMING` и native level overlap. ROUTE anchor не считается farm. `HIGH_BANDS_52_85_PROOF.tsv` содержит полные ordered ingress→GK→farm witnesses и точный blocker 81–85.

| GLOBAL ordinary | До | После |
|---|---:|---:|
| 40–51 | 1 | 1 |
| 52–60 | 0 | 10 |
| 61–75 | 0 | 15 |
| 76–80 | 0 | 3 |
| 81–85 | 0 | 0 (`NO_OPEN_ORDINARY_CANDIDATE`) |

Два fresh generation run побайтно совпали по 7/7 artifacts: plan, high-band proof, Geo proof, generated-04, targeted supplement, manifest, GK catalog. Старый 40–51 final proof повторно имеет прежний SHA-256 `9447c798ef3a280a46d308d6a88338f1c056f5ca0d845d2e76bbd45b7052bd4c`.

## Проверки и ограничения

- Focused Python `test_normal_gk_catalog`, `test_live002_40_51_final`, `test_live002_high_bands_final`: 7/7 PASS. Старый 40–51 validator: `GLOBAL_ORDINARY_40_51=1`. Новый final validator: `1/10/15/3/0` и точный blocker.
- `ant phantom-topology-core-test`: первая sandbox попытка остановилась на `AccessDeniedException` при чтении `HikariCP-7.0.2.jar` в compile classpath; разрешённый повтор прошёл, 38/38. Компиляция Java/test Java успешна с двумя старыми deprecation warnings. Pure Java `PhantomNormalGatekeeperPureTest`: PASS.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.
- Запрещённые DB/Hikari/runtime/corpus targets не запускались; GameServer/LoginServer и DB connections/mutations = 0; `ant jar`=0; full `ant verify`=0. Ruins, Cat/Necro, instances и LIVE-003+ не начаты.
- Goal token/time счётчик средой не предоставлен. Ограничение результата: 81–85 требует отдельной задачи для допустимого open-world FARMING anchor/source geometry; в этом Goal `NO_OPEN_ORDINARY_CANDIDATE`.
