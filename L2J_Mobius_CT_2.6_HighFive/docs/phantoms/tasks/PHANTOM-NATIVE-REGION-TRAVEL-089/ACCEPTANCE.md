# ACCEPTANCE — строгое закрытие 089

Итоговое поле отчёта: `TOTAL089=SERVER_VERIFIED | PARTIAL | BLOCKED`, отдельно `IMPLEMENTED`, `FOCUSED_VERIFIED`, `NATIVE_VERIFIED`, `OWNER_QA_PENDING`, без смешения.

| Gate | Проверяемое доказательство | PASS только если |
|---|---|---|
| P0 — authority/safety | actual local/upstream/live SHA, scope; stage/input/Geo/SHA, fresh 089 UUID & absent DB, source diff | чужие tracked/untracked/runtime/DB/JVM не затронуты, source-config088 не менялся |
| P1 — source/graph | proof H/E/O + Giran control; native spawn+TeleporterData NORMAL+topology; 2 разных destination/corridor choices | certified first-mile из реального старта, выбор плана без подгонки 3 NPC, validated sourceRefs/provenance, цена/достижимость |
| P2 — physical reachability | собранные Geo/path evidence на каждом WALK, issued native MOVE_TO, observed successive arrival XYZ/time, checkpoint resumes | H/E/O пройдут реальные локальные сегменты до GK/дальней области; no artificial setXYZ/teleport/path skipping |
| P3 — native payment | npc canInteract, NORMAL holder/index, native action, before/after Adena, actual landing, native receipt | **минимум два** подтверждённых ненулевых GK debit на двух разных genuine Player families, никакого двойного списания |
| P4 — farm after travel | same Player identity at remote NPC, actual attack HP change; native earned XP/SP/Adena/loot receipt | **все три** H/E/O продолжают охоту и получают подтверждённое native вознаграждение в завершённом journey; fixture stock не засчитывается |
| P5 — interrupted journey/restart | restart sameDB/same-source build, saved profile/objectId/goal/revision/wallet/item/coords/segment/action state | хотя бы один **реально начатый paid journey** прерван на контролируемой точке и затем продолжен без повторного debit; action double-retry и ambiguous paid-without-arrival негативы отвергаются |
| P6 — regressions | direct/nongeo/water/Z/doors, GK unavailable/insufficient/karma/castle, stale goal, bound limits, party/store/death, farm074/spot085/064 focused | никакой опасной native regression в затронутом контуре; cache/cancel/backpressure остаются ограниченными |
| P7 — efficiency and delivery | latency, queries/nodes/cpu, memory/GC, 1 compiled source, own processes stopped; report/diff/commit/push | нет бесконечного full-graph native loop, leak и unbounded queue, tracked clean, report честный |

**Условия полного `TOTAL089=SERVER_VERIFIED`: P0–P7 PASS.** Три семьи — Human/Elf/Orc с реальными stock classes/gear/skills (могут быть другие законно квалифицированные классы и уровни, но состав объяснить). Минимум **два платных** NORMAL телепорта, один post-restart continuation и три native reward episodes. Только 087 fixture без native action — 0 соответствующих gate. При отсутствии хотя бы одного hard native proof — `PARTIAL`, независимо от красноречивости отчёта.

Ограничения: отсутствие 5000 durable профилей/cap128+/200 offline stores и OWNER client QA **не** блокирует отдельное 089 verification, но и не считается достигнутым. По итогам незакрытых gates указать один causal blocker/root cause на каждый, какие fixes действительно были опубликованы и почему дальнейшая часть не доказана.

**Запрещённые ложные PASS:** route quote, feeCount из XML, availability=true, manual port/spawn/xyz reposition, background projection tick, `World=0`, fixture setAdena/reward, один подготовленный Player, перезапуск другого profile, пересоздание 089 DB между epochs, unit PASS без actual native movement.
