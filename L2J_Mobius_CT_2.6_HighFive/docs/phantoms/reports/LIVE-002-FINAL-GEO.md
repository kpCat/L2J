# LIVE-002-FINAL-GEO — bounded factual GEO checkpoint

Дата: 24.09.2026. Ветка `feature/phantom-world`; обязательный tracked baseline `700811df18562a9313d122373d21865fbb10d592` подтверждён до правок. **Статус: BLOCKED; LIVE-002 не GREEN.** Runtime и DB не запускались; `ant jar=0`, full `ant verify=0`.

QUOTA_GUARD: checkpoint на ~260k, подготовка BLOCKED на ~300k; на момент closeout ~344k Goal tokens. После checkpoint не добавлялись новый GK flow, anchor salvage или production subsystem.

## Read-first и scope

До патча прочитаны TASK/EVIDENCE/ACCEPTANCE, D1/D2 reports, LIVE-002 section STATE, bounded `travel_backbone.py`/`normal_gk_catalog.py`, C Geo validator/publisher, core topology, native DoorData/Doors, GeoEngine probe/API и ZoneManager. Отдельных модульных AGENTS.md, `DEVELOPMENT_CHAT_HANDOFF.md`, `CURRENT_GENERATOR_STATE.*`, `CONTEXT_INDEX.md` и code-map не найдено; применены переданные пользователем AGENTS.md. Локальный паттерн: A/B/C canonical TSV + immutable C proof, D1 bounded GeoEngine probe/manifest, C directed `BACKGROUND` edge schema, D2 generic NORMAL GK catalog. Bounded exception к 8–10 файлам: task прямо требует несколько canonical diagnostic/data outputs, generator, focused test и report/STATE; production Java/GK semantics не менялись. User dirty/untracked файлы сохранены.

Все принятые C и D1 SHA-256 совпали с TASK; strict D2 catalog остаётся `5ccf7ac85e89027ba4506f20d64f41cc7167af285ceb88f1ef5a93f16cc5398f` (5 legs). Исторические C/D1 outputs не переписаны.

## Phase 0 — до изменения публикации

`WORLD_GAP_CLASSIFICATION.tsv` учитывает **2 652/2 652** ordinary READY_STATIC groups по одному разу. Взаимоисключающие primary blocker counts: `REACHABLE=23`, `OPEN_COMPONENT_DISCONNECTED=916`, `PLANNER_TARGET_DISTANCE=501`, `ANCHOR_GEOMETRY_BLOCKED=532`, `OTHER_EXPLICIT=4`, `CLOSED_SOURCE_ENTRANCE_REQUIRED=676`. Три `NO_LOCAL_MOVEMENT` оставлены как explicit C reason, а не переименованы в недоказанное отсутствие geodata. Итого anchor-validation failures **1 037**; closed/multi-floor **676**. Данные имеют source family, level band, исходный C status, component, reachability и evidence refs; полные component/band counts находятся в `.phantom-local/logs/LIVE-002-FINAL-GEO/phase0.log`.

D1 `8192`/fanout-8 был реальной причиной пропущенных **14 inbound компонентов** с **23 опубликованными groups**: в ограниченном adaptive поиске для них появился GeoEngine-proven вход при radius `16384/32768` или rank `>8`. Это положительная нижняя граница, а не утверждение, что остальные unreachable группы не имеют пути. Из 377 исходно disconnected open компонентов 106 получили кандидатов в пределах 32 768 за две волны; у 20 был хотя бы один proven direction, у 17 — входящий direction. У 86 проверенных компонентов ни один из выбранных bounded кандидатов не прошёл GeoEngine; у оставшихся 271 в пределах этого бюджета вообще не было кандидата. Поэтому отсутствие любого возможного geodata path для них **не доказано**. Только XY/radius никогда не принимался как bridge.

## Open bridges и фактическая достижимость

Spatial cell 8 192, детерминированные radii 8 192 → 16 384 → 32 768, максимум 32 направленных проверки на исходный компонент. Реальный `PhantomTravelGeoProbe` загрузил 203 geo regions, native doors/fence; `PhantomGeoValidationRules.route` проверил direct/path и каждый segment. Всего **2 012** direction checks: `VALID_DIRECT=104`, `VALID_PATH=41`, `NO_PATH=1849`, `PATH_SEGMENT_BLOCKED=18`. Детерминированный `high-five-generated-02.xml` публикует только **145** proven направлений, каждое с exact endpoints, source refs, path-length travel time, `bidirectional=false`; отдельные 676 closed candidates туда не включены. Никаких переходов между этажами или сквозь закрытую дверь из XY не создавалось.

Перед checkpoint GLOBAL reachable farms по bands `1–5/6–10/11–19/20–39/40–51/52–60/61–75/76–80/81–85`: `13/0/6/4/0/0/0/0/0`. После активного bridge shard и **admitted** D2 legs: **`29/7/12/9/0/0/0/0/0`**. В 20–39 все 9 witnesses остаются только у Dwarf; non-Dwarf >20 gate не выполнен. Salvage C-blocked anchors не выполнялся: после quota checkpoint оставались независимые high-band, Ruins и closed-access blockers, а бесконтрольная обработка 1 037 отказов противоречила task scope. Имеющаяся C validation продолжает явно учитывать каждый отказ.

## Ruins и closed ordinary corpus

Native Ruins fact: `data/teleporters/town/30256.xml`, NPC 30256, fact `fact.abdc7798f7b2491d57793ef2`. После bridge shard `ingress→Bella GK=false`, admitted NORMAL transition `false`, destination→published anchor connector `false`, local ordinary farm coverage keys пусты. Статус **BLOCKED**, proximity не засчитана.

`CLOSED_AREA_ACCESS.tsv` учитывает **676/676** исключённых C groups: 429 Catacombs/Necropolis (195 Catacomb, 234 Necropolis), Devil's Isle 96, Tower of Insolence 92, Ivory Tower 39, Imperial Tomb 20. Каждая строка содержит native spawn territory/floor-Z, instance=0, zone ref, source ref, nearby door ID/default state только при совпадении XY bounding box **и Z floor**. Native `Doors.xml` имеет 1 301 doors; только у 3 groups есть door в том же floor box. Это proximity evidence, не проход через дверь. Outside/inside entrance и script/portal transition ни у одной группы не доказаны; все 676 имеют `entrance_status=UNPROVEN`, `geodata_status=NOT_PROBED_CLOSED`, `planner_status=BLOCKED`. Представительный ordinary Catacomb/Necropolis доступ не доказан. SSQ instance не засчитан как ordinary и free portal не создан. Это accounting checkpoint, не закрытие F16/F17 access gate.

## Детерминизм и проверки

Два fresh GeoEngine run по каждой из двух adaptive волн дали одинаковые proof SHA-256: wave 1 `b4fac4b1d0693a5bad65ac0b66f5a94cb6d84f31d187fb7abd82ec68e98d26a6`, wave 2 `86f4077b7d197e75473d2430a3a71981976c242f342516954550f2041da3e011`; обе пары совпали с первоначальными proof. Два fresh generator run дали byte-identical canonical outputs:

| Artifact | SHA-256 |
|---|---|
| `WORLD_GAP_CLASSIFICATION.tsv` | `2b7dc06855f873098c24180e9d3fe8a7dbb8dee46e1b342ecce8ab8d3469d311` |
| `WORLD_COMPONENT_BRIDGES.tsv` | `fa8665b2cbe9dbf62c0f75a7e3e4a4ef5dd868bbf128a186e05ac46abb096028` |
| `CLOSED_AREA_ACCESS.tsv` | `8036add79cb10acb00cf7145ac8200f6169733bf067d8fe87011363f18aea041` |
| `LIVE002_FINAL_REACHABILITY.tsv` | `27de6d2a6d0dc1f6a00dd6ce685b01d230676865bb05f04d512506d1e51a4158` |
| `high-five-generated-02.xml` | `7d92baaa21e40aa80010d7975ce64ab8050671a839fda49e0a6a2491cac47343` |
| `LIVE002_FINAL_GEO_MANIFEST.json` | `75a73077b240ba50ccb38311acdd44a66867b59ab2e332b39e07883c84781b87` |

Focused Python `test_final_geo.py test_travel_backbone.py test_normal_gk_catalog.py`: **14/14**. Existing D1 `Validate-TravelBackbone.ps1`: GREEN, 1 813 facts, 1 602 connectors, 72 reachability rows. D2 catalog regeneration: 5 legs, byte-identical hash above. `ant phantom-topology-core-test phantom-geodata-rules-test`: **38/38 + 17/17**, after retry with sandbox escalation because first compile could not read `HikariCP-7.0.2.jar`. Active production corpus suite was not run: its launcher requires test DB config and hardcodes the pre-shard 1 507-edge count; it would violate this task's no-DB gate. Independent production final-reachability validator (F20) therefore remains **not GREEN**. Generator fixture, GeoEngine proofs and existing strict loader fixture are GREEN; full production acceptance is not claimed.

Mojibake-маркеры в изменённых файлах проверены. Escaped Cyrillic в изменённых файлах проверены. Full `ant verify=0`, `ant jar=0`, LoginServer/GameServer=0, DB=0.

## Exact blocker и следующий task

First exact progression blocker: **GLOBAL band 40–51 has zero admitted factual ordinary farm paths** after all proven open bridges within 32 768. This prevents LIVE-002 GREEN even before the separate Ruins and closed-access gates. `NEXT_ACTION`: one bounded task to prove an ingress→native NORMAL GK source and destination→ordinary 40–51 farm connector with GeoEngine, then feed that factual leg through the existing generic D2 catalog and independent static validator. Do not start a broad new audit or production state machine. Ruins and closed access remain explicit unmet gates for later review; no GREEN claim is made.

Exact-path stage/commit and normal push details are recorded at closeout. Git was used only for required branch/baseline/scope verification and exact-path delivery.
