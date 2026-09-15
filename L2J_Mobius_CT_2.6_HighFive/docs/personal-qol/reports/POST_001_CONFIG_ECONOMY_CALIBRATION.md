# L2-POST-001 — конфиги оператора и аудит цен Phantom

Статус: **BLOCKED** для экономики, безопасная часть Config UX выполнена. На `feature/phantom-world` исходный HEAD перед правками точно `caab05ee2c7ca17c853cb4f77b8ca79003fbb873`. Текущие unrelated изменённые `PhantomMaterializationService.java`, `PhantomClanDirectiveIntegrationGoal030C2ASuite.java`, `PhantomMultipartyEconomySuite.java`, GOAL launch files и другие untracked task packages не правились и не предназначены для staging.

## Census фактически поставляемых конфигов

История путей `dist/game/config` и Java config owners дали ровно четыре project-owned operator-facing ini:

| Shipped ini | Java owner | Ключей | Назначение |
|---|---|---:|---|
| `Custom/PhantomPlayers.ini` | `PhantomPlayersConfig` | 23 | lifecycle, разговор, scheduler, population/ecology |
| `Custom/PersonalCharacterQoL.ini` | `PersonalCharacterQoLConfig` | 12 | личный allowlist и функции персонажа |
| `Custom/PersonalPremiumQoL.ini` | `PersonalPremiumQoLConfig` | 3 | личная витрина и путь level-gap catalog |
| `Custom/PersonalProgressionQoL.ini` | `PersonalProgressionQoLConfig` | 2 | личное quest relief и глобальное Noblesse |

Каждый ключ получил соседнее русское описание диапазона/default, игрового смысла, CPU/RAM/БД-диска/сети/scheduler, масштаба, выбора малого сервера, dependencies и restart. Имена ключей и shipped значения не менялись. `PhantomSchedulerPulseMillis` поясняет 1000/100/50/10 ms как 1/10/20/100 wakeups в секунду с bounded per-pulse work. Durable target, ACTIVE target и materialized cap разведены по реальной стоимости. Личные функции не распространяются на Phantom, `EnableServerWideAutoNoblesse` глобален.

`PhantomPlayersConfig.read` строго валидирует cap `1..10000`, scheduled `1..1000000`, pulse `10..1000`, profiles `1..10000`, target `0..scheduled`, ACTIVE `0..min(target,materialized)`, creation `1..64`, boundaries `1..10000`, party `10..10000`, social cache `16..10000`, ecology preset `FRESH/LIVING/MATURE`, world age `-1` или `0..3650`, archive `1..1000000`. `PersonalCharacterQoLConfig` ограничивает duration multiplier `0.01..100.0`, список `4096` символами/`256` элементами, account `45` символами и duration override точными парами. `PersonalPremiumQoLConfig` принимает относительный путь не длиннее `160` без `..`. Допустимые Boolean tokens — `True/False`. Жизненный цикл всех четырёх config owners — загрузка при старте Game Server, поэтому изменение требует перезапуска.

Packaging: `build.xml` задаёт `datapack=dist`; `adding-datapack` обновляет zip непосредственно файлами `dist` без перекодировки/перевода. Исполняемый `dist/game/config` — эти же файлы. `ant -q adding-datapack` — **BUILD SUCCESSFUL**, 2 min 25 s. Python `zipfile` byte-check всех четырёх `game/config/Custom/*.ini` против локального `dist` — **4/4 bytes identical**, каждый файл декодируется как UTF-8 с читаемой кириллицей. Ручная post-build translation не нужна.

## Census всех price producers и входов private market

Поиск exact symbols в production `java` и `dist/game/data/phantoms`, затем во всех `test/java`, дал:

| Место | Фактическая роль |
|---|---|
| `PhantomStorePlan.Line.price` | Явное неотрицательное поле в durable plan; сам plan не вычисляет цену. |
| `PhantomStoreService.install` | Копирует `line.price` в native `TradeList`/`ManufactureItem`, проверяет наличие SELL item и Adena для BUY. `restore` возвращает записанную цену. |
| `PhantomSocialEconomyGoalSpec` | Парсит уже указанную `price`/`listingPrice` из закрытого goal target. Не создаёт goal и не выводит fair value. |
| `PhantomMultipartyEconomyService` | Сверяет snapshot native listing с goal, reservation и receipts; для manufacture сверяет native recipe/success/fee. Не выбирает цену. |
| `PhantomSystem` | Создаёт `PhantomStoreService`; найденные production-вызовы — только `shutdown`. Production-вызова `open`/`restore` с priced plan нет. |
| `test/java/.../PhantomMultipartyEconomySuite` | Только тесты создают priced store plans и store goals; конкретные fixture prices `4/5/6/10` не market policy. Файл уже dirty до POST-001, не изменён нами. |

Таким образом, источник Phantom private-store цены **до** задачи — явный test/external listing input. Действующего production producer собственных Phantom BUY/SELL/MANUFACTURE orders нет. Приписывать `PhantomStoreService` формулу или утверждать, что текущие фантомы выставляют calibrated orders, было бы неверно. Создание producer вместе с lifecycle/goal scheduling/stock selection — новая product integration; точный seam и граница final-freeze не заданы package. Поэтому production economy edits остановлены по `AGENTS.md` правилу «при архитектурной неоднозначности нельзя молча менять архитектуру»; цены **после** этой безопасной части остаются прежними. Для завершения экономики требуется отдельное конкретное решение: кто создаёт private-store plan/goal, в какой фазе, из какого inventory/acquisition snapshot, с какой promotion/validation границей.

## Проверенные canonical owners для будущей authority

| Input | Класс | Проверенный источник/ограничение |
|---|---|---|
| `ItemTemplate.referencePrice`, `isSellable`, `isTradeable` | `PRICE_ANCHOR` | `ItemData`/`ItemTemplate`; `ItemFact` индексируется в immutable `PhantomGameKnowledgeSnapshot`. Reference price не есть scarcity. |
| Merchant liquidation `L_npc` | `PRICE_ANCHOR` | `RequestSellItem` и `ExBuySellList`: sellable item даёт `referencePrice/2` за штуку, независимо от enchant; `MerchantZeroSellPrice=True` выключает выплату. `L2jCommerceBackend.quoteSell` пользуется тем же `/2`, но дополнительно отвергает refund/zero режим для своего Phantom route. Stock owner не менялся. |
| Merchant acquisition `P_npc` | `PRICE_ANCHOR` | `RequestBuyItem`: `Product.getPrice()` плюс реальные base/castle tax и отдельный siege-guard rate. `PhantomCommerceCatalog` хранит raw BuyOffer с NPC IDs/limited-stock flag; raw цена не равна автоматически фактической доступной цене. GM/нулевые и limited-stock lists нельзя считать unrestricted acquisition. |
| Death drop, spoil, manor, quest source facts | `SUPPLY_RATE`/`UNSAFE` | `PhantomGameKnowledgeSnapshot` содержит indexed raw drop/spoil/recipe/manor facts; `PhantomAcquisitionSourcePlanner` знает методы. Источник нужно проверять на легальную доступность, group chance, level gaps, quest conditions и ограничения stock. |
| `DeathDropAmountMultiplier`, `SpoilDropAmountMultiplier`, `DeathDropChanceMultiplier`, `SpoilDropChanceMultiplier`, `DropAmountMultiplierByItemId`, `DropChanceMultiplierByItemId` | `SUPPLY_RATE` | `RatesConfig` + `NpcTemplate`; item-specific overrides, включая Adena `57`, меняют применимую поставку. Нельзя подменять всё одним `RateDropItems`. |
| `RateQuestRewardAdena`, `RateQuestRewardMaterial`, `RateQuestRewardScroll`, `UseQuestRewardMultipliers`, `RateDropManor` | `CURRENCY_RATE`/`SUPPLY_RATE` условно | `RatesConfig`, `Quest` и manor owners; применять только к подтверждённым quest/manor routes. `RateXp/RateSp` — `NOT_RELEVANT` к цене одного item без доказанной модели времени. |
| Recipe ingredient quantities/product count/success | `CRAFT_COST` | `RecipeData`, `RecipeList`, `PhantomGameKnowledgeSnapshot.RecipeFact`; `60%` success требует expected inputs, recursive BOM и cycle guard. Native manufacture listing fee — отдельно заданная цена, а не обязательная цена самого recipe. |
| Enchant scroll/support/legal grade/chance | `CRAFT_COST`/`UNSAFE` до fair scroll anchor | `EnchantItemData.xml`, `EnchantItemGroups.xml`, `EnchantScroll`, `EnchantSupportItem`, `EnchantItemService`. Без defensible value scroll/support route нельзя использовать для enchanted quote. |

Stock `EnchantItemGroups.xml`: general weapon/armor/accessory current `0..2` chance `100`, full armor `0..3` chance `100`, затем current `3..15` или `4..15` chance `66`, current `16+` chance `0`. `EnchantScroll.getChance(ItemTemplate,level)` и `calculateSuccess` добавляют scroll/support bonus с cap `100`; `isValid` проверяет grade/type/level. `EnchantItemService` расходует scroll/support на каждой попытке; normal failure уничтожает base item и при crystallizable случае выдаёт формульные crystals, blessed failure сбрасывает уровень в `0`, safe failure оставляет level. `Player.ini` ограничивает over-enchant; stock chance не принадлежит Phantom ini.

Реальные H5 scroll IDs: D weapon/armor `955/956`, C `951/952`, B `947/948`, A `729/730`, S `959/960`, из `EnchantItemData.xml`. Например `stats/npcs/18100-18199.xml` содержит death-drop `729/730` и B/C/D scrolls; `multisell/103.xml` содержит Olympiad Token routes для B/A/S. Castle/специальные buylists местами имеют limited stock, а `buylists/0009931.xml` содержит нулевую цену: без eligibility audit использовать такую строку как свободный `P_npc=0` нельзя. Scarcity и rate-aware fair value этих scrolls **NOT_CALCULATED**.

## Невыполненные acceptance и gate

Canonical private-market pricing authority, Bid/Ask corridor, craft/enchant expected-cost DP, risk range, representative x1/mixed/high-rate matrix, manufacture fee coherence и NPC/Phantom arbitrage tests **NOT_IMPLEMENTED/NOT_RUN**. Не выдаём audit за экономическую калибровку и не сочиняем формулу/числа. Stock NPC commerce, inventory/Adena conservation, QOL-001..009 и Goal039 production owners не правились. Production DB **NOT_USED**; `prepare-phantom-test-db` **NOT_RUN**.

Focused config/parser preflight: первый `ant -q phantom-skeleton-test phantom-humanized-goal038-catalog-test qol-personal-skills-test qol-effect-duration-test qol-economy-progression-closure-test` в sandbox упал до тестов с `javac AccessDeniedException` на `dist/libs/HikariCP-7.0.2.jar`. `jar tf` и ACL read проверены; тот же focused `ant -q phantom-skeleton-test` с разрешённым elevated execution прошёл. Затем `ant -q phantom-humanized-goal038-catalog-test qol-personal-skills-test qol-effect-duration-test qol-economy-progression-closure-test` с тем же разрешённым execution — **BUILD SUCCESSFUL**, 3 min 38 s. `ant -q phantom-full-vision-goal039-static-test phantom-full-vision-goal039-documentation-test` — **BUILD SUCCESSFUL**, 1 min 9 s. Bounded baseline `ant -q phantom-economy-private-store-buy-test phantom-economy-private-store-sell-test phantom-economy-manufacture-test phantom-commerce-test` — **BUILD SUCCESSFUL**, 3 min 24 s. Это существующие tests сохранения owner/conservation, а не новые POST-001 Bid/Ask/craft/enchant guarantees. Required economy acceptance preflight всё ещё не зелёный, поэтому fresh full `ant verify` **NOT_RUN** согласно verify policy.

Mojibake-маркеры в пяти изменённых текстовых файлах проверены отдельным `rg`: совпадений нет.

Escaped Cyrillic в пяти изменённых текстовых файлах проверена отдельным `rg` по всем шести заданным regex: совпадений нет.

Изменены только четыре перечисленных ini и этот отчёт. Новые Java API, тесты, DB/migrations, Rates.ini, enchant XML, других хроник нет. Git использован для разрешённого task inspection (`status --short --branch`, `rev-parse HEAD`, `branch --show-current`, `merge-base`, bounded `log --name-only` для config inventory, bounded `diff --check` и key/default diff). Commit/push и remote SHA отражаются в финальном результате после точного staging.
