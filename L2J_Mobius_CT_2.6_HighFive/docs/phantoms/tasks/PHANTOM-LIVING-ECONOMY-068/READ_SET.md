# READ_SET068 — проверенная карта исходников

Все короткие пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`. Java-prefix **J** = `java/org/l2jmobius/gameserver/`. На подготовке проверены GitHub HEAD `b7e18670704d9b14661594b13d576df97a7ae770` и перечисленные ключевые seams. Это статическое чтение, не повторный runtime-аудит локального Windows.

## Первый проход

Root `AGENTS.md`, ближайшие/module `Agents.md`; текущий пакет; верх `docs/phantoms/PHANTOM_CURRENT_STATUS.md`, `CODEX_WORKFLOW_CONTRACT.md`, `RELEASE_CONTRACT.md`. Сохраняются `docs/PHANTOM_BOTS_ROADMAP.md` (исторический) и `docs/phantoms/PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md` (продуктовый); читать только affected scope.
`docs/phantoms/reports/PHANTOM-PERSONAL-ADVENTURE-067.md`: feature-status + runtime binding. `.../PHANTOM-FARM-ECONOMY-066-R2.md`: только failed purchase/Scavenger и starter compatibility. Не перечитывать039–066 suites.

## Проверено перед упаковкой

| Источник | Факт для проектирования |
|---|---|
| J/phantoms/economy/PhantomAutonomousMarketProducer.java | shared pulse15s; active materialization owner; cap долей ACTIVE; автоматический manufacture fail-closed |
| J/phantoms/economy/PhantomStoreService.java | ActionLease материального Player; open/restore/close и durable plan; shutdown проходит по profiles |
| J/data/sql/OfflineTraderTable.java | native BUY/SELL/MANUFACTURE save/restore; Player.load; stock bulk-clear/save — опасный seam для второго phantom writer |
| dist/game/config/Custom/PhantomMarket.ini | maxStores2, допустимо1..16; lifetime120s; autonomous pricing включён, manufacture fee0 |
| J/phantoms/economy/PhantomPrivateMarketPricingAuthority.java | existing source/craft/enchant valuation, NPC floor/ceiling; manufactureFee=0; BUY nonstackable запрещён из-за ambiguity |
| J/model/spawns/Spawn.java | champion eligibility и bool reset/set; это место расширения selection, не второй spawn scheduler |
| J/network/serverpackets/AbstractNpcInfo.java | champion title и `_npc.getTeam().getId()` уже в NPC_INFO; не новый network field |
| J/qol/PersonalInstanceAdmissionService.java | immutable Party roster/solo snapshot, повторная проверка состава; сохранить для Камалоки |
| docs/phantoms/tasks/PHANTOM-PERSONAL-ADVENTURE-067/RIFT_ANALYSIS067.md | конкретные room/timer/death/restart lifecycle дефекты и exact symbols; scope теперь разрешён шире singleton |

## A/B — existing decision, acquisition и бухгалтерия

J/phantoms/commerce/{L2jPhantomSupplyJourney,PhantomAutonomousSupplyPolicy,PhantomLootDispositionPolicy,PhantomInventoryMaintenanceIntent,PhantomCommerceService,L2jCommerceBackend,PhantomCommerceReceipt,PhantomSupplyIntentStore}.java.
J/phantoms/commerce/{PhantomStarterShotBudget,PhantomStarterShotService}.java — сохранность фонда, не новый subsidy API.
J/phantoms/background/{PhantomBackgroundModel,PhantomBackgroundService,PhantomBackgroundTransaction,L2jPhantomBackgroundAuthority,PhantomVisibleAutoPlay}.java; existing historical/normal travel, acquisition and decision symbols по вызовам.
J/phantoms/population/{PhantomPopulationManager,PhantomPopulationEcologyService,PopulationInitializationContract,PhantomPopulationStore}.java и profile repository/components — canonical birth, history cursor и role handoff.

## C/D — native store/craft/equipment

Проверенные market classes выше; J/phantoms/economy/PhantomEconomyReservationService.java и остальные owners по callers; J/services/PrivateStoreService.java; J/network/holders/TradeList.java; J/data/sql/OfflineTraderTable.java, J/model/actor/Player.java store/offline/load/cleanup methods; native offline command handler найти по `enterOfflineMode`/OfflineTraderTable, не вызывать packet через fake client.
J/data/xml/RecipeData.java; J/model/item/recipe/{RecipeList,ManufactureItem}.java; native recipe handlers/managers найти по call tree. J/phantoms/progression/PhantomNativeEquipmentPolicy.java + acquisition/knowledge indexes.
`dist/game/config/Custom/OfflineTrade.ini`, `PhantomMarket.ini`, `PhantomPlayers.ini`, recipe/item/skill data — только нужные фрагменты.

## E — источники цен/Alt+B

J/phantoms/economy/{PhantomPrivateMarketPricingAuthority,PhantomPrivateMarketQuote,PhantomRateAwareLootFairValue,PhantomRecipeExpectedCost,PhantomNpcAcquisitionPrice,PhantomEnchantExpectedCost}.java.
Actual Alt+B handler/bypass и его reachable buylist/multisell roots найти адресно в `dist/game/data/scripts` и связанном CommunityBoard. Не предполагать, что весь `data/multisell` относится к Alt+B. `dist/game/config/Rates.ini`, native reward/shop price/tax code и `data/Recipes.xml` — фактические inputs. Existing CLI сначала найти по symbols; при отсутствии создать `tools/phantom-economy/Price-PhantomEconomy.ps1` и небольшой shared pricing exporter, не второй калькулятор цен.

## F — champions

`dist/game/config/Custom/ChampionMonsters.ini`, J/config/custom/ChampionMonstersConfig.java, J/model/spawns/Spawn.java; J/model/actor/Attackable.java `setChampion`, actual stats/reward/spoil callers; J/network/serverpackets/AbstractNpcInfo.java. Найти все `CHAMPION_`/`isChampion` consumers одним bounded search в High Five. Не переписывать other chronicles.

## G — Rift

Сначала RIFT_ANALYSIS067.md. J/managers/DimensionalRiftManager.java; `DimensionalRift`/room actual files по imports; J/model/groups/Party.java; Player.doDie/doRevive; RequestRestartPoint.portPlayer; PartyInvitationService invite/accept и native Rift script. Начинать с фактических owners, не вставлять global personal exception во все instances. QoL config/service и PersonalInstanceAdmissionService067 сохраняются.

## Tools/tests

`build.xml`, existing PhantomTestLauncher и targeted suites commerce/background/equipment/market/ownership; точные modes читать, не угадывать.
`tools/phantom-runtime/Build-Task067.ps1`, `Assemble067.ps1`, `Runtime067.ps1` если есть в live tree; иначе их проверенная own067 поставка — read-only источник. Аналоги066R2 также read-only. Выбрать один минимальный safe template, не копировать десяток исторических lane classes.
`LocalPlayPilot.ini`/LocalPlayPilotService — passive observation, syntheticOFF. Private tests/setup не должны подменять product pipeline.

## Ссылки ревизии для аудита

Repository: https://github.com/kpCat/L2J/tree/b7e18670704d9b14661594b13d576df97a7ae770
Source base: https://github.com/kpCat/L2J/blob/b7e18670704d9b14661594b13d576df97a7ae770/L2J_Mobius_CT_2.6_HighFive/
Названия в таблице — конкретные прочитанные файлы; grouped paths — карта дальнейшего адресного чтения, а не заявление, что каждый метод уже изучен. Если HEAD продвинулся, сверить affected diff и переиспользовать новую реализацию, не дублировать.
