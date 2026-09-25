# LIVE-002-FINAL-CONTENT-ACCESS — итог 25.09.2026

Статус: **GREEN_PARTIAL**; `LIVE002_DATA_COMPLETE=1` означает полную точную классификацию данных, а не доступность каждого подземелья любому профилю. Ветка `feature/phantom-world`, обязательный исходный HEAD `3dff43640890e329bb3d8a1c9776b7593bf612c1`. Завершающие commit/push фиксируются в итоговом сообщении после записи отчёта. Несвязанные dirty/untracked файлы не входят в Goal.

## Read-first и границы

Прочитаны только абсолютные `TASK.md`, `EVIDENCE.md`, `ACCEPTANCE.md` заданного LIVE-002 пакета; локальный `AGENTS.md`, master plan, workflow/task standard, STATE, прежний Ruins Geo отчёт, связанные generator/validator scripts, topology/travel loader и native teleporter/spawn/door XML. `README.md` в модуле, отдельные code-map/pattern-файлы и родительский `AGENTS.md` не найдены. Переиспользованы deterministic TSV/XML writer и SHA-проверки D1, generic NORMAL join, directed active BFS, hermetic Geo probe, production topology loader и proof-only oversized NodeBuffer. Не проверены live-login персонажа, фактическое получение noblesse/SSQ состояния и DB/runtime. Один крупный Goal по явному запросу охватывает более 10 файлов: bounded exception ограничен пятью family shards, их evidence, двумя каталогами, одним точечным Java SHA pin, валидаторами и отчётом.

## Phase 0: Ruins

Исходный committed `RUINS_GEODATA_PATH_PROOF` SHA-256 `a686fa4a17bb7a06e81cfcf83aa0bb133b1936e5f2658d6ed1e4a2a64deab253` использован без повтора oversized A*. Шесть опубликованных directed hops имеют production buffer не более 500, `STATIC_XML_CLEAR`, door/fence 0/0. `high-five-generated-06.xml` SHA-256 `e55037269fec57dc37066c2f25f42d413216bc6a350ecc9bd7bfca55084c096b`; три точных Ruins connector rows вошли в supplement. Native Bilia/Bella NORMAL и NPC20059 source подтверждены. Независимый active validator доказал `RUINS_OF_DESPAIR_REACHABLE_FARM=1` и progression `5/13/15/3/1`.

Production `PhantomNormalGatekeeperTravel.TARGETED_CONNECTORS_SHA`: старый `881c6be02318523aefaaf726c343004d0991273d6d8ee75f1162e35d62e97fad` → итоговый `e5fefd3a8ae7678f8fe0ac5d53fc2c3fba0305c5a08fac5d4f98d359cfe06a44`. Промежуточный Ruins-only `c436039da63f72a93fb6d8eafe7f1b76d2e7fe033302da76be5588d31fa330bd` также отвергается pure loader test. D1 SHA constants не менялись. Targeted supplement 9→13 rows: +3 Ruins, +1 exact Devil's Isle NORMAL destination identity. Generic NORMAL catalog 40→54 legs; SHA `cf4396dac631f6a4c5a91fe2bf39b64ff5efe5fc22d8efe001a00c0c3f5732ac` → `f12ac5c5ea47367bc932e732b202541d76d73c6920aa1b2e4f6a5d2ed1da8f2c`. Fee/replay/cursor/castle semantics в Java не менялись.

Exact baseline XML comparison подтвердил, что все прежние 40 NORMAL legs сохранили идентификаторы и все атрибуты без изменения; добавлено 14 новых generic factual legs.

## 676 исторических closed rows

Machine reconciliation exact unique keys: **676/676**, zero blank/unknown/UNPROVEN. Каждая group row в `FINAL_CONTENT_ACCESS_PROOF.tsv` содержит status, native owner/condition, farm anchor, component и точный blocker. Все 676 native spawn territories совпали с NPOLY source; 6865 bounded candidates дали 676 stable, local, `STATIC_XML_CLEAR` anchors. В активные shards включены только proven компоненты. Индивидуальные door/Geo blockers сохранены даже при успешном соседнем компоненте.

| Family | Native entrance | Итог для 676 rows | Shard nodes/edges, SHA-256 |
|---|---|---|---|
| SSQ Catacombs/Necropolis (429) | 28 exact Ziggurat `OTHER`, owners `dungeon/31095..31125`; OracleTeleport rift branch имеет отдельные level/quest/item/Adena predicates | 429 `NATIVE_CONDITION_UNMODELED`; 413 nodes/518 Geo-only edges, все `backgroundEligible=false` | 413/518, `6280aa1871f98ff271d43b9ecd3bf336607170d94af57e1e3de59458ef5cf432` |
| Devil's Isle (96) | Giran NPC30080 `NORMAL`, native `43408,206881,-3752` | 47 `REACHABLE_ORDINARY`, 46 `GEODATA_INTERNAL_BLOCKED`, 3 `DOOR_STATE_BLOCKED` по conservative collision proof | 48/61, `cb3aff88c7f7f3c3e4483e782860a05cbbf99af92bdfbe18012e3bac367e2a49` |
| Tower of Insolence (92) | Aden NPC30848 `NORMAL` entrance; exact noblesse 3/5/7/10/13 floors conditional | 23 `REACHABLE_CONDITIONAL`, 66 `GEODATA_INTERNAL_BLOCKED`, 3 `DOOR_STATE_BLOCKED` с native `default_status=close` | 26/31, `bd8139748c6e1e84e9d0f6f936152ae73fc96b04929411e086794ca7f5d1a9db` |
| Ivory Tower (39) | Oren NPC30177 `NORMAL`; internal owners только `others/IvoryTower/*.xml` | 39 `CONTENT_TELEPORT_SEMANTIC_BLOCKED`; нет доказанного native entry→farm пути, внутренний `OTHER` не исполняется | 0/0, `b0d24060fafd8fa871a9fa42b9227afaec04de454e42047f98c418baf5bf814d` |
| Imperial Tomb (20) | Goddard NPC31275 exact `NOBLES_TOKEN`/`NOBLES_ADENA`, item13722×1 либо Adena1000 | 20 `REACHABLE_CONDITIONAL`; произвольный фон не допускается | 24/33, `7b51a41277cbf1c174eeac1d4b794c51680976a1d20aba5b8b0112723e7f0623` |

Directed Geo batch limits: SSQ 1018/1024, Devil 512/512, ToI 512/512, Ivory 109/512, Imperial 96/512. Imperial proof-only raw chain SHA `e89c00deeb2eaabc87bfe2cf3a07093b82c27ad872774999d9d0eca100caf19c`; четыре опубликованных decomposition hops повторно прошли production max500 и `STATIC_XML_CLEAR`. Ruins oversized A* не повторялся. Только Devil's Isle ordinary edges имеют `backgroundEligible=true`; noblesse, SSQ и Ivory rows нет. Generic executable `OTHER` не вводился: exact holder runtime condition не доказан. Нового route state machine/BFS в production нет.

`CONDITIONAL_CONTENT_ACCESS.tsv` содержит 51 exact scoped native entries: SSQ 28 `OTHER`, ToI 10 noblesse floor destinations + 3 closed doors, Ivory 8 `OTHER`, Imperial 2 noblesse destination. Все `background_eligible=0`. Затронутые group keys в каталоге задают family scope; это не утверждение о достижимости каждой группы через каждую запись.

## Проверки и результат

- Две свежие полные hermetic Geo проверки: 6865 anchor candidates, 31 directed proof file за проход, без oversized A*. После каждого прохода пересобран пакет из fixed proof evidence. Все 112 canonical/generated artifact hashes побайтно совпали: aggregate SHA-256 `723ce3d3f9559946cdb2fe1b9d3c340d6b3346f70bf6efbc547717ecb7643ab2`.
- Независимый final validator парсит active topology, NORMAL catalog/pin, native Monster territories/teleporter/doors, conditional catalog и proof files; `LIVE002_DATA_COMPLETE=1`, Ruins=1, progression `5/13/15/3/1`, zero UNPROVEN. `FINAL_CONTENT_ACCESS_PROOF.tsv` SHA `82a8b920d0e27697c35bf139d6e789f123ef8c37d908e0ea0c4522af07a437f3`; final validation SHA `413b2c5f864b414c3a6316b7b85d5539ae000da712b94d02d6645fbe01e17fd5`.
- `ant compile-tests` GREEN (два уже существовавших предупреждения `runFinalization`); Hikari jar прочитан компилятором только compile-time. Pure NORMAL test: stale old/interim pins отвергнуты; DB-free production topology loader принял пять closed shards: 511 nodes, 511 anchors, 643 edges.
- GameServer/LoginServer, DB, Hikari initialization/pool, `ant jar`, full `ant verify`: **0**. DB-dependent production corpus suite не запускался. Оценка token/time окружением не предоставлена.

Статус GREEN_PARTIAL не откатывает Ruins, Devil's Isle, ToI или Imperial Tomb. Следующий LIVE-003 Goal не начинался.
