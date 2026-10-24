# PHANTOM-LIVING-SYSTEMS-074

**TOTAL074: PARTIAL.** Production A–D изменён и проверен; недоказанные игровые цепи остаются PARTIAL. Это один выпуск074, без R2/075. Визуальная приёмка владельцем — OWNER_VISUAL_PENDING.

Начало2026-10-23 23:43UTC; product freeze2026-10-24 05:43UTC; hard stop06:43UTC. Baseline local/remote6057cc69728e5050d79806e5e6aa3c5a2bf898c8, ветка feature/phantom-world. Все действия ограничены OWN074; исходные071–073 акторы/БД/candidates/private inputs и human accounts сохранены.

| Направление | Результат | Новое реальное действие и граница |
|---|---|---|
| A | PASS основной цепи; acquisition tree PARTIAL | Сохранённый profile2 продал ненужный sword1 за295A, купил180D1463 за3780A, вернулся, выполнил2rewarded боя180→159. Новый JVM подтвердил inventory/EXP/SP/receipt и следующий бой159→150. На final JAR ещё один бой150→141. |
| B | PARTIAL | Local-first global topology fallback и admission до CAS проходят focused. Два первых native profile3 контроля:2369 equipped,129 inventory, physicalOffense7.848,0proposals. Один новый причинный контроль с production supply/equipment bridge прошёл7548units к магазину, но farm CAS/segment не доказал: последний участок ROUTE_BUDGET_EXCEEDED. Fixture не ремонтировалась. |
| C | PARTIAL | Исправлен native selected-target context, вызывавший TARGET_CAP после16targets; native RED0→0/GREEN0→1. На final JAR два законных herb receipts, VICTORY_LOOTED, нужные1871/1872/1463 KEEP; surplus SOLD295A. Уже принятый pickup/missed-wake race8611 полностью не закрыт. |
| D | PARTIAL полной связанной сцены; recovery PASS | Первый эпизод:2native skill hits[3,255], shots9→0, CP2→1, но retreat expired. Final JAR: тот же actor после native подхода противника2287→32 и29incoming damage,0shots, без наступления прошёл553units к600-unit Geo точке; ровно1encounter/1retreat и supply intent. Unbroken skill+CP+shots→retreat на final JAR не переигрывался; refill не произведён. |

Объявленные4000A A-актора,10000/40000A покупателей рынка,9D/2CP D-актора и finite seller stock — PREMEASUREMENT fixtures, не заработок074. Три A prebuy kills дали EXP/SP, но0A;295A — штатная продажа prior property, разрешённая TEST_PLAN074. Это не повтор35kills и не ремонт кошелька.

Изменены16существующих production Java owners, без нового AI/commerce engine. Первый diagnostic actor1 не входит в durable PASS: после ошибки cleanup его физическое состояние не сохранилось, COMMITTED receipt остался. Он сохранён в TEST без replay/компенсации; RELEASE его не содержит. Проверен graceful restart исправленного actor2, не произвольный crash между NPC effect и store.

Дополнительный read-first аудит B обнаружил конкретное отличие измерителя от `PhantomSystem699–721`: отсутствовали SupplyJourney/useEquipment и visible/accepted intent bridge. Helper-only commit `02e9b4f6241d1e4c6b568337599b20d2bb46e306` подключил эти прежние owners; production/JAR не менялся. Один разрешённый TEST_PLAN074 причинный контроль120s на том же actor268481777/profile3 прошёл от(-71081,254981,-3232) до(-77443,250923,-3416),7548.26units. Это снабжение, не новый farm segment. CAS=false, weapon2369 и wallet5000A остались; итоговый requested(-80107,249543,-3880), merchant destination(-84125,240482,-3720), native ROUTE_BUDGET_EXCEEDED. Штатное закрытие и отдельный SQL-read подтвердили сохранённую новую позицию, прежние exact11item rows,129 в inventory,0покупок. Raw111rows — NATIVE_FARM_COMPOSITION074; SQL subset — NATIVE_FARM_STORED074. Новая попытка/fixture repair не выполнялась; общий B PARTIAL.

## Native экономика

В combined scene profile5 действительно купил60B1465/4800A у offline SELL: buyer10000→5200A, seller300→240, native matching-grade charge60→59; NG/D stacks не затронуты. Operation `f2a74358399350ab09139a2787932d58b0a1b5cea1e0827c25799a9514d93f4c` COMMITTED.

Profile6 через подключённый production SupplyJourney приобрёл1Bcrystal1460/9000A,54SoulOre1785/13500A и оплатил native MANUFACTURE22/100A. Native RecipeData:55inputs→450B1465, MP789→689, success100%; buyer40000→17400A, charge450→449. Все3audit COMMITTED; повторные исходные children не списали деньги/предметы. Это два разных механизма, не поштучная кампания грейдов.

Платёжные проверки выполнены на первом committed JAR19677d0e…; изменённый затем typed cached index на final9b24… проверен native без повторной покупки. В новом JVM сохранены59/449B и четыре COMMITTED audit. Из тех же finite stores повторно опубликованы240B@80 и native recipe22@100; exact1465 index содержит SELL+MFG, stale paid listing hash отклонён. Reserve500 сохранён: запрос60 при5200A допускает только58/4640A. Inventory/wallet/root/intents/audits не изменились, native membership очищен штатно.

CRYSTALLIZATION: actual12200(Common B, crystal_count5) native SUCCESS→5B1460; foreign preview read-only, owned inspect/skill248:3 обязателен, equipped/needed/foreign mutation/replay negatives соблюдены. Полная paid gearBUY→CRYST→ore→MFG цепь не доказана.

Кеш хранит publication membership, не разрешение сделки. Native stock, price, listing hash, owner/access/skill/MP и receipts проверяются заново. Сопоставление exact item/grade и полная стоимость партии применяются до выбора самой дешёвой полезной альтернативы; thin offers не скрывают четвёртого продавца. Расстояния6000/nearest16 не являются границей рынка. Обработка идёт по matching published index; hardcap200 для всех native stores не заявляется.

Конкретные PARTIAL универсального дерева: SELF_CRAFT под FARM-child (canonical executor требует acquire.item root/state); NPC-покупка и warehouse-изъятие BOM-компонентов; объединение одного ингредиента от нескольких продавцов; normal/jewelry crystallization; строгая paid basis уже смешанных own stacks; interrupted-mid-BOM restart и полная gear→CRYST→MFG сцена. Существующий native engine/ledger/schema не подменялись. Reference valuation собственных материалов явно остаётся оценкой, не фактической покупной ценой.

## Проверки и выпуск

Final JAR:132/132 focused в существующих suites и4standalone control classes. Исходный неправильный PvP seed, старые DB-suite cwd guards и неудачные native controls сохранены как ошибки запуска/PARTIAL, не скрыты в PASS. Независимое review проверило publication identity/lock ordering, quote admission, pickup ownership и stale/new PvP damage. THINK-resumption отклонён из-за обратного порядка item/AI locks; общие AI setters не менялись.

Main: RUNNING подтверждён01:41:30UTC, штатный STOP requested01:45:34/завершён01:45:45. SameDB/sameJAR restart RUNNING status записан01:47:54, STOP requested01:55:19/завершён01:55:30. Внешняя stdout-обвязка задержала завершение команды до STOP; поздняя строка READY01:55:29 с пустым gamePid не используется как доказательство старта. Это задержка orchestration, не дополнительный soak/reseed.

После restart неизменны216identities,17profiles, merchant quota SHA,200offline owners и600store lines. Vortexbra/profile2: level2→3, EXP87→367, SP6→14; item rows765→767. Есть обычные смерти; HP не исправлялся. Изменение координат в этих snapshots не выдаётся за causal B route proof.

Ассортимент проверен по настоящим persisted SELL owner/object/stock:49SKU/450SELL lines из600общих store lines. Есть800каждого1463(D),1464(C),1465(B),1466(A) в8лавках на SKU.1467(S) и Blessed Spiritshot3947–3952 в SELL не найдены; это не доказательство отсутствия MANUFACTURE в мире.200лавок не означают полный рынок; бесконечный refill или новое производство высоких грейдов не добавлены. Полный SELL список — MAIN snapshots.

Final production source `104f87e2e85935657caf082f4f08277a146a7925`; archive SHA `442C37A8F6E5AC428D4B475EDA1A8F99329298FB89934FCB9BEF2C1F70721FC8`; tree `E666B495EB37948A8A4330EC0695B0569321BA57D280907FDF4F12C80B4C3A27`.

GameServer.jar `9B24BA22B14CE28585A2EC61533B87372D82C9BE44B3FB439F8A8BEDD9694C72`; LoginServer.jar `3BB59FF1ED11BEB3180E7044E71D17B023ABC545E818584B4150086982B22763`. Две итоговые Ant сборки, вторая — единственная разрешённая correction; каждая фактически выполняет2production javac phases+1test phase. Отдельные helper-only commits/Javac не пересобирают JAR; последний helper source `02e9b4f6241d1e4c6b568337599b20d2bb46e306`, manifest прилагается. Предыдущий ee5f manifest сохранён отдельно как HELPERS074-market-index.json. Все16production owners после104f совпадают; третья JAR сборка запрещена текущим TEST_PLAN074. Полное закрытие оставшихся HARD gates не заявляется.

Candidate `D:\PhantomWorld\Living-Systems-074-candidate`; TEST `.phantom-local/task074/runtime-test`. Оба STOPPED, own DB connections0, marked JVM0, шесть own listeners0. Physical203 Geo source hashes и два начальных protected hashes сохранены;071–073 DB/actors/client не использовались. START/STATUS/STOP self-contained, stock heap, GUI off; прежние артефакты correction побайтово сохранены вне checkout. MariaDB service оставлен работающим.

Source/helper normal push `ee5f3afb5a79b503bc3bd36c39286ff4243be4b2` проверен на remote. Итоговые evidence публикуются отдельным report commit тем же normal push в feature/phantom-world; его SHA/remote equality приведены в финальном ответе. Точные команды — GIT074.md. Никаких новых worktrees, force/history rewrite или автоматической следующей задачи.

Для неизменённого owner TASK074 package добавлен существующий Task071 `-text` паттерн в .gitattributes. Original9content hashes сохранены. Документы внутри ранних committed source archives были нормализованы Git по EOL и семантически совпадают, но не являются byte-exact owner packet; архивы/receipts не переписывались. Финальная публикация task input byte-pinned. Эта document-only correction не вызывает запрещённую третью JAR сборку.

Подробности: [журнал](IMPLEMENTATION074.md), [матрица](PHANTOM-LIVING-SYSTEMS-074-data/ACCEPTANCE074.tsv), [runtime](PHANTOM-LIVING-SYSTEMS-074-data/RUNTIME074.md), [Git](PHANTOM-LIVING-SYSTEMS-074-data/GIT074.md), [source delta](PHANTOM-LIVING-SYSTEMS-074-data/SOURCE_CHANGE074.tsv).

- mojibake-маркеры в изменённых файлах проверены:81scoped UTF-8 files,0matches.
- escaped Cyrillic в изменённых файлах проверены: все6regex,0matches.
