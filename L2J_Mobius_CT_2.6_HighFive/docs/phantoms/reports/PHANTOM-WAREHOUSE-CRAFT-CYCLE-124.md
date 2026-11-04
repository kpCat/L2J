# TASK124 — автономный Warehouse → paid MFG → durable supply

Дата: 2026-11-04. Итог: **FULL124=PASS по A+B+C+D**. STOP124; TASK125/R2/R3 не создавались.
C принят именно как продуктивный совместимый supply по ACCEPTANCE.md; **ActualShotConsume=NOT_OBSERVED**.
Не утверждается применение сосок в бою: native CONSUME57×100 — плата, а не CONSUME1463.

## Baseline, изоляция и чтение

GitHub `kpCat/L2J`, `feature/phantom-world`: `00ba06dc70551f349b032f07012aff6bfe8214d6`, проверен независимо и перед каждым runtime.
Физический `clone --no-hardlinks --single-branch --branch feature/phantom-world`; основной checkout с WIP109 не изменялся.
Source: `%TEMP%/phantom-task124-20261104/src`; evidence/scripts: `%TEMP%/phantom-task124-20261104`.
Stage: `%TEMP%/Phantom-Warehouse-Craft-124-78e4d910-fb83-4e01-88ce-f2fd65cb34c7`, собственный owner+SYSTEM ACL.
Единственная новая schema: `l2jmobiush5_phantom_test_124`, localhost:3308, game port17784, один CREATE, без drop/reseed.
Прочитаны все семь Markdown пакета, GOAL/MANIFEST, root/module AGENTS, workflow/efficiency/package standard, current status, README, build.xml и report123.
Выше Git root дополнительных AGENTS нет; docs/phantoms/tasks/package AGENTS и module README.md не найдены; повторный широкий поиск не выполнялся.
Аналоги: maintenance SELL/deposit/withdraw, OfflineSupply/componentPurchase, Craft101 native preparation, combat content-retreat, recovery Owned STORE и private123/122 G0.
Java25/Ant сохранены; новых движков, SQL schema, config rebalance, Player/Item writers, UI или серверных зависимостей нет.

## Причины и точные изменения

До изменений установлен writer graph: disposition → child intent CAS → ACTION/native Commerce → COMMITTED receipt → child revision CAS.
MFG: native offer/owner/recipe → multiparty reservation/operation → ManufactureService observer → audit/offer consumption → terminal CAS.
STORE: quiescent native owner → actual inventory/warehouse → Owned STORE/verify-pending → guarded rematerialization.
Проверены причины отсутствия цепочки: только item IDs вместо BOM quantities; warehouse inputs не участвовали в выгодном offer; возврат/BUY завершал need до MFG; native busy/death блокировал путь.

Exact allowlist, все пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java` — exact BOM withdrawal, два bank objects, handoff после собственного committed withdraw, честный NPC return-point cost; retreat через существующий combat owner.
2. `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomOfflineSupply.java` — native recipe deficits, owned Warehouse BOM, bounded batches/trips и quantity/weight/cost quotation.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — одна lifecycle binding существующего CombatService.
4. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java` — additive guarded cancel/consume по generation/target/owner атомарно под существующим monitor.
5. `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java` — настоящий native TOWN остаётся допустимым при отсутствии optional topology projection.
6. `test/java/org/l2jmobius/gameserver/phantoms/commerce/PhantomWarehouse124Checks.java` — DB-free recipe/BOM/weight/batches/restart/receipt/recovery contracts.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java` — foreign/replaced retreat handle не отменяет и не поглощает чужую session.
8. `docs/phantoms/reports/PHANTOM-WAREHOUSE-CRAFT-CYCLE-124.md` — этот отчёт.

Границы: не более двух BOM objects за посещение, четырёх operations, 600 единиц/operation, native capacity и aggregate60% weight; не покупаются повторно собственные bank inputs.
Один подходящий настоящий maker/recipe; учитываются полные quantities/fees/MP/HP/skill/chance, product slots/weight, reserve и возврат к исходному field.
После банка handoff сохраняет root/binding/deadline и требует exact последний committed receipt; NPC comparison фильтруется сразу с тем же field return point.
Survival не выполняет атаку/loot: только canonical retreat до30s и исходного deadline, без pending STORE/economy/неразрешённого commerce effect.
All-writer audit recovery: прежние ACTION → guarded native revive/TOWN/Geo completion → Owned STORE → verify-pending/CAS → rematerialization; изменён лишь destination choice.
Фактический native off-anchor XYZ уже сохранялся Authority с anchor lineage; headless eligibility, topology и farm eligibility не расширены.

## G0 и PREPARED_NOT_EARNED

Перед CREATE: привилегированная OS attestation PID/datadir/image/SID/ACL, metadata/full grant visibility, schema absent, foreign sessions0, JVM/listeners/SQL TCP0.
Созданы stock121 SQL files/203 statements; 18 DROP statements не исполнялись. Старые188 schemas не изменялись.
Original inputs29085, физические Geo203 и protected1235 preimages проверены; WIP109/MAIN/PLAY/releases057–061/OwnerConfig/57 amount5 chance1 сохранены.
PRE-CUT NORMAL: profile1/object268480990/classIndex0/activeClass1, level24, D-weapon70, D-armour, Adena30000, product1463=0.
PRE-CUT Warehouse: ore1785 object268481065×9, crystal1458 object268481066×3; inventory charcoal1871 object268481067×606, Shirt21×1, native potions.
Выбранный offline maker: own124maker/object268481201, Artisan56 level24, skill172 rank2, native recipe20, fee100, wallet10000, persisted stock offline listing.
Recipe20: 100%, 9 ore +3 D crystals →468 D Soulshot1463, MP72; native reference inputs4200+fee100=4300 против NPC14×468=6552; номинальная разница2252 до travel.
Live source-priced policy/itinerary проверяются production selector; цена/шанс/формулы ради PASS не менялись. Исторический native Craft101 не засчитан как новый craft.
Подготовленный обычный observer — не GM/не invulnerable; после CUT только native load/spawn на сохранённых координатах.
Все level/BOM/capital/recipe/HP/position подготовки завершены до CUT1; preparation124.exit0/precut124.final.pass/readback сохранены.
PRE-CUT ошибки исправлялись с сохранением FAIL: отсутствующий Warehouse topology anchor, zone flag unspawned maker, class/level не сохранены после Player.create, partial corpse maker.
Повторный provisioning автоматически отклонён approval review до проверки partial state; SELECT-only inventory preimage и exact guards подтвердили отсутствие повторной выдачи, затем безопасное продолжение разрешено.
Один SELECT reader во время Ant отказал FOREIGN_JAVA до JDBC; повторён только после завершения Ant. Первый ACL helper не импортировал Security — ACL исправлен и проверен до G0.
После CUT: harness grants/goal/XYZ/HP/MP/EXP/SP/cash/warehouse writes=0; прямых manufacture вызовов за NORMAL=0, repair/reseed=0.

## RED → GREEN и четыре native starts

DB-free первый warehouse RED: отсутствовал quantity-demand метод (NoSuchMethodException); это contract RED, не полноценный live selector witness.
Настоящий recovery RED: реальный первый Dion native respawnPoint отвергался при canonical=null; сохранён AssertionError, затем тот же тест GREEN.
Native1 RED: NORMAL начал настоящий путь, но paused AutoPlay+combat busy оставили его под mob aggro; умер. Склад/MFG ещё0; законный native death penalty24→23.
Исправление survival проверено guarded replacement regression; Native2 выявил следующий точный RED `recovery.canonical_town_absent`, без складских effects.
До Native2 один input-manifest name mismatch отказал до JDBC/CUT/GameServer; FAIL сохранён, manifest-name lookup исправлен без ослабления hashes.
Native1/2 имели healthy STOP/STORE/exit0/postflight. Новые кандидаты ставились только после postflight с byte-preimages и отдельными runtime-inputs manifests.
Native3: CUT13:58:24Z, READY13:59:39Z; настоящий corpse recovery14:00:54Z, далее autonomous maintenance и paid MFG.
Native4: READY14:05:13Z, same DB/JAR/helper/input; natural supplies decision продолжил путь к полю. Окно остановлено без лишнего ожидания, actualShotConsume=false.
Native1/2 ограничены150s после READY. Native3/4:150s без нового economic/recovery witness; только реальное изменение ledger или восстановление продлевает progress window, hard8min. Movement/ticks/HP не продлевают.

## A — native Warehouse PASS

Общий root goal4029513984366660917; Warehouse NPC30058/object268467727, SELL NPC32876/object268467801; native epoch599192743323600, все receipts COMMITTED/resume0.
SELL21×1 rev0: `14fef3df974bba53c653f7ecaa94bd59d479722c8f7daa9c4f3c1086b5978340`, buyer32640→32696.
DEPOSIT1871×600 rev1: `ebf51d8882df9e83201c6d96f98448976031365363254d3fc893e20c033f96c6`, inventory606→6, Warehouse0→600, fee30, buyer32696→32666.
WITHDRAW1785×9 rev2: `134689bca46695dbdb7279d53bac93e6794bbc2c08e747565131c9e721d77174`, Warehouse9→0, inventory0→9, native fee0.
WITHDRAW1458×3 rev3: `bfccc256aa605c37e0962d1bd0a26326bea4c5929ac367c552ca01d29ee2cc55`, Warehouse3→0, inventory0→3, native fee0.
Final SQL: Warehouse object268485199/1871×600, inventory original268481067/1871×6; PRE-CUT warehouse stock не выдан за after-CUT deposit.

## B — native оплаченный MFG PASS; C — productive supply PASS

Autonomous goal rev5: operation `6c9a2525dbaacfcfdf8b1cfbd458c01cdb27e45cb4ebb5b24ac3bd608fea749a`, PLAYER_MANUFACTURE/COMMITTED/SUCCESS/result.success, rowVersion4.
Offer `257937e5ce892dcdce961efc838d0f010eac3ea082f8cd2160edb68c23f19007`, CONSUMED/rowVersion4, native maker268481201/recipe20/fee100/product1463×468.
Audit1 authority `dac8d39fe2d6331d19de2fd7c7fe1f4ac7d0da90bfb81f8302332acc0c7628b6`; intent `5867a266c6fffe9703142f84eff7b01a637c59b75f06732611f5cf5d40958db6`.
Audit: consumed12 (ore9+crystal3), produced468, AdenaSource100/AdenaSink100; consequence `PLAYER_MANUFACTURE|1|12|468|100`.
14:01:51Z: buyer32666→32566; maker10000→10100; BOM inventory9/3→0/0; product object268485204/1463:0→468. Final goal COMPLETED rev6/economy.social.committed.
Продукт получен native paid service для существующей D-shot потребности, совместим с D Claymore70, сохранён и повторно валидирован при restart. BOM reference4200 — оценка PREPARED сырья, не заработанный expense.
Native shot consumption/equip/sale результата не наблюдались; C основан на явно разрешённом productive supply, не на listing/quote или callback списания денег.
Полный wallet ledger: prepared40000 + canonical historical2640 + native SELL56 −bank30 = final buyer32566+maker10100=42666; fee100 — внутренний перевод.
Дополнительных paid travel debits в этой цепочке0; с отдельным bankfee30 номинальная экономия относительно NPC2222. Это не заявление earned farming.

## D — STORE и sameDB/sameJAR PASS

Кандидат пары3→4 JAR SHA256 `678233068e9cdeda3c5a2ca672e2e4ee34f1505944df3dc0834a3f55675cfdb4`; source/data/helper/input между фазами не изменялись.
STORE3 transaction15/epoch599192743323600: receipt `facd8e3bbf48b97dc70d759a90c435b83754dd68f55d4c7295cd049e3c283592`, nativeAfterMatched=true.
STORE4 transaction12/epoch599482482942300: receipt `4ab37fe5774bb3164ddfc1f0805f2a00aa3b1657446e25f5306e2e03e3aa231a`, nativeAfterMatched=true.
Оба full inventory SHA `c101327176261d70f439bdfa8e30c8721715f4d0eb28f429b6808fa8ba697f13`.
stored3→before4: все87 фактических SQL rows exact. before4→stored4: все78 inventory/maker/commerce/economy rows exact; character XYZ/HP естественно менялись.
Same profile1/object268480990/classIndex0/class1; Warehouse600/reserve6/product468/buyer32566/maker10100; operations1/audit1/consumed offer1/reservations0.
Новых MFG/debits/products нет, IDs/versions/terminal payload сохранены. Возникла настоящая следующая supply-потребность1061. Durable STORE4 XYZ29375,135120,-3032 совпадает SQL; это не forced XYZ.
Все четыре STOP: drained=true/stopFailed=false/retained0, native STORE, exit0, own JVM/ports/SQL/charactersOnline0, postflight PASS; hard kill/lease clear/DB repair не применялись.
Private evidence: cutN/readyN/samplesN.tsv/lifeN.log/warehouse-mfgN.log/progressN.log/pickupsN.log/beforeN.tsv/storedN.tsv/store-nativeN.log/stopN.txt/postflightN.log.

## Проверки, review, расходы и доставка

Final `ant ... compile-tests` exit0; existing5 deprecation warnings. `PhantomWarehouse124Checks` exit0/DB=false, включая native Dion fallback и four-operation restart.
Final launcher seed140014: supply0719/9, commerce-receipt9/9, combat-core72/72, combat-ownership17/17; все exit0.
Дополнительно existing Craft101 DB-free contracts3 и EarnedMarket119 contrasts5 — exit0; native старых кампаний не запускались.
Final `ant ... jar` exit0, затем native3/4 именно на нём. Ant выполнялся последовательно; общий verify/DB suites и старые product campaigns NOT_RUN.
Missing/foreign/stale maker/epoch/BOM, skill/chance/funds/overflow/weight и COMMITTING guards сохранены source audit; отдельной destructive native negative кампании не было.
Independent source aggregate review семи source/test путей: P1=0/P2=0; independent evidence review разрешает A–D FULL именно с оговоркой C productive supply.
P3 advisory: local retreat cap admission может временно overshoot при concurrent calls; canonical Combat остаётся bounded. warehouseTrips worst-case O(types²), не O(item count).
Exact allowlist/diff-check и повторный byte-identical scope/hash guard выполнены перед commit; protected1235/Geo203 проверены postflight4.
mojibake-маркеры в изменённых файлах проверены: весь набор27, совпадений0.
escaped Cyrillic в изменённых файлах проверены: все шесть regex, совпадений0.
Расходы: 10 успешных compile-tests, четыре jar, четыре GameServer starts; повторения PRE-CUT helpers и два причинных native RED сохранены, а не скрыты.
Usage snapshot перед отчётом: goal tokensUsed1215805/timeUsed8183s (~2ч16м); денежная стоимость tool/API недоступна и не выдумана.
Git использовался только для baseline/isolation/scope/delivery: `ls-remote URL refs/heads/feature/phantom-world`, `clone --no-hardlinks --single-branch --branch feature/phantom-world URL <src>`, `rev-parse HEAD`, `symbolic-ref --short HEAD`, `status --porcelain=v1 -uall`, `diff -- <exact allowlist>`, `diff --stat -- <exact allowlist>`, `diff --check -- <exact allowlist>`.
Запись только в clone: `add -- <семь exact code/test paths>`, scoped code commit; затем `add -- <этот report>`, scoped report commit; `push https://github.com/kpCat/L2J.git HEAD:refs/heads/feature/phantom-world` после live parity. Это прямо разрешено TASK124 §10/SAFETY §7 и текущим запросом.
Code commit: `be9b1d9eb70d589584c7c78a7e0a189f3dda6f22` (ровно семь code/test путей). Report доставляется отдельным commit.
Exact argv/exit/stdout Git delivery сохраняются в `git-delivery-code.json`, `git-delivery-report.json`, `git-delivery-push.json`; итоговый local/remote SHA — в private `delivery124.json` после ordinary push и независимого ls-remote.
No force/reset/restore/clean/stash/merge/rebase/main checkout writes. Исходный физический clone и private witnesses сохранены для проверки.
