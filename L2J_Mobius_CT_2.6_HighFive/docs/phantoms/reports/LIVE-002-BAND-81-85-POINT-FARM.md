# LIVE-002-BAND-81-85-POINT-FARM — final ordinary band

Дата: 25.09.2026. **Статус: SUCCESS / GREEN.** Ветка `feature/phantom-world`, tracked baseline `22d496d382febcb19d06d237976941b62fa45de2` подтверждён до изменений. В дереве были несвязанные dirty/untracked файлы; они не включены в задачу.

## Read-first и причина

Прочитаны абсолютные TASK/EVIDENCE/ACCEPTANCE указанного пакета, локальный AGENTS.md, master plan, workflow contract, task standard, предыдущий high-bands report, релевантные generator/validator/catalog, generated-04, native SelMahums/NPC/30177/31964, topology query и GameKnowledgeBuilder. README.md модуля не найден. Локальные аналоги: generated-04 ROUTE hub, deterministic targeted connectors и manifest, generic NORMAL GK join, независимый high-bands final validator. Переиспользованы те же exact identity и hermetic movement contracts. Bounded exception к порогу 8–10 файлов: один data-routing Goal требует shard, proof, supplement/catalog provenance, DB-free query/final validator, focused assertions и отчёт. Production travel semantic, схема и другие chronicle modules не менялись.

До правок независимый `live002_high_bands_final.py` подтвердил GLOBAL ordinary `40–51=1`, `52–60=10`, `61–75=15`, `76–80=3`, `81–85=0`. Evidence package учитывает 33 открытые 81–85 группы, из них 31 SelMahum POINT. В `PhantomTopologyGeodataValidator.java` ветка `geometry_kind == POINT` безусловно ставит `UNSUPPORTED_POINT_AREA` до Geo validation. Это причина отсутствия active anchor, не отказ геодаты. Старый C corpus и `ANCHOR_GEODATA_VALIDATION.tsv` сохранены без правок; старый full validator не запускался.

## Exact POINT и Geo

Попытка была только в `SelMahums.xml / smtg_drill_group_09`, coverage `7617d478280b73549ec67bfd3c697cf422243d3545197efd42908486a281fb70`, ordinary READY_STATIC instance0, уровни 83–84. Preferred NPC22775 lvl84 `85484,60762,-3391` отвергнут: `hasGeo=true`, GeoEngine высота дважды `−3400`, native Z `−3391`. Переноса или нормализации native xyz нет.

Второй exact кандидат в порядке `(level desc,npc_id,x,y,z)` — NPC22782 Monster lvl83 `84902,60870,-3440`: `hasGeo=true`, обе GeoEngine высоты `−3440`. Другой exact NPC22782 той же группы `84904,60980,-3440` даёт local `VALID_DIRECT`, length111/1 segment, buffer78/500, `STATIC_XML_CLEAR`, door/fence 0/0. Центр `87448,61460,-3664` → выбранный farm даёт `VALID_PATH`, length3010/11 segments, buffer382/500, `STATIC_XML_CLEAR`, door/fence 0/0. Всего 5 новых hermetic movement направлений, максимум 8 кандидатов/group не достигнут: проверено 2; fallback group22/group08 не открывались.

`high-five-generated-05.xml` содержит только Oren ROUTE node/anchor и SelMahum FARMING_AREA `POINT_RADIUS radius=1` node/anchor. FARMING anchor имеет native xyz, instance0, tolerance0, npcId22782 и exact spawn source. Edge нет. Pure DB-free loader собрал **все 1038 active nodes** из семи shards, а реальный `PhantomTopologyQuery.mostSpecificNode(84902,60870,-3440,0)` вернул `generated.farm.7617d478280b73549ec67bfd`. Это соответствует EXACT mapping в `PhantomGameKnowledgeBuilder`. SHA-256 generated-05: `2c2da58595c7c0c80869bc59cba6bae0c52c4c7ef9743f9ae734bddcb0263b76`.

## Factual Oren transport и публикация

Bilia31964 `transition.d0ea8a9408f232160c3b78fb` → Town of Oren `82971,53207,-1488`, castleId4, fee59000. Точная arrival→Oren ROUTE связь `VALID_IDENTITY`, 0/0. Oren ROUTE→native NPC30177 `82992,53171,-1492`: `VALID_DIRECT`, length42/1 segment, buffer68/500, `STATIC_XML_CLEAR`, door/fence 0/0. NPC30177 `transition.107143092d3473f5ec928e09` → Sel Mahum Training Grounds (Center), fee1800, с указанным выше положительным destination→farm connector. Три строки добавлены в targeted supplement, прежние шесть сохранены.

Targeted supplement SHA `c4db321d399b9b5c14b858d203957da328668cf36bc49ae7009084ff47a88278` → `881c6be02318523aefaaf726c343004d0991273d6d8ee75f1162e35d62e97fad`, rows 6→9. Generic GK catalog SHA `b5bb0256ac5bd2a5d5384941d9b8ee2db5f7b21f128455efd6330be35f5d5551` → `cf4396dac631f6a4c5a91fe2bf39b64ff5efe5fc22d8efe001a00c0c3f5732ac`, factual legs 30→40. Дополнительные legs возникли из generic factual join; Java изменён только в SHA pin. `POINT_FARM_81_85_PROOF.tsv` фиксирует native, Geo, local/destination collision, connector и query mapping. Его SHA `7a3d25ceb4495c26e3e0206c800d23beadf0da34ecaa4671c1585b9d4f2900c0`.

## Независимый final gate

`live002_81_85_final.py` заново разбирает семь active topology shards, native spawn/NPC/teleporter через прежнюю независимую factual catalog validation, proof/provenance и полный active node query. Ordered ingress route содержит Bilia→Oren и Oren→SelMahum Center, заканчивается выбранным FARMING anchor, не ROUTE. `LIVE002_81_85_FINAL.tsv`: 9 route steps, SHA `103dc43a2222ff2fdfe80a7220b926c3b007b7302a90ca8768cdc6c04f97f0ef`.

| GLOBAL ordinary | До | После | Требование |
|---|---:|---:|---:|
| 40–51 | 1 | 5 | ≥1 |
| 52–60 | 10 | 13 | ≥10 |
| 61–75 | 15 | 15 | ≥15 |
| 76–80 | 3 | 3 | ≥3 |
| 81–85 | 0 | 1 | ≥1 |

`ant phantom-topology-core-test` (включая compile-tests): 38/38 PASS; `python -m unittest test_normal_gk_catalog test_live002_40_51_final test_live002_high_bands_final`: 7/7 PASS; independent final validator PASS. Два fresh generation run побайтно совпали по 6/6: generated-05, point proof, targeted supplement, manifest, GK catalog и final proof. Первый sandbox `ant compile-tests` получил известный `AccessDeniedException` на classpath HikariCP JAR; разрешённый повтор прошёл. Это не Hikari initialization. Mojibake-маркеры в изменённых файлах проверены: совпадений нет. Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Запрещённые DB/Hikari/runtime/corpus targets **NOT RUN**; GameServer/LoginServer, DB connections/mutations=0; `ant jar`=0, full verify=0. Ни Ruins, ни closed/instances, ни LIVE-003 не начаты. Среда не предоставила достоверный Goal token/time counter. Этот GREEN закрывает только ordinary progression band 81–85; новый Goal не запускать.
