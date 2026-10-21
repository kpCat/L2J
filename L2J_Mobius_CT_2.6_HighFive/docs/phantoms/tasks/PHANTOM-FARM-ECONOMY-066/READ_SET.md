# READ_SET066 — предварительно проверенные точки, без чтения всего проекта

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`, кроме корневого `AGENTS.md`. Факты сверены автором на `934c19f906aa67349f1fd498eacfbb5a56d9b90f`; это source review, не новый runtime PASS.
Для файлов >1000 строк: `rg -n` по указанным symbols и ограниченные диапазоны. Структуру соседних пакетов смотреть по именам, не печатать все файлы.

## Initial read-first: до12 файлов, до6 адресных поисков до первого исправления
1. Корневой `AGENTS.md`.
2. `Agents.md` — scope/ownership; строки о C:/отсутствии Geo устарели и заменены RUNTIME_INPUTS066.
3. `PHANTOM_DEVELOPMENT_MASTER_PLAN.md` — только действующие инварианты соответствующих подсистем.
4. `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`.
5. `docs/phantoms/TASK_PACKAGE_STANDARD.md` — формат; старый root распаковки не использовать.
6. `docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md` — TASK066 уточняет staged read-set/безопасный impacted verification вместо общего ant verify.
7. `docs/phantoms/PHANTOM_CURRENT_STATUS.md` — последние факты/authority, не старые summary.
8. `docs/phantoms/RELEASE_CONTRACT.md` — сборка/поставка/сохранность, без полного исторического closure.
9. `docs/PHANTOM_BOTS_ROADMAP.md` — только относящиеся contracts, старую версию не удалять.
10. `docs/phantoms/PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md` — A/C и независимые последующие этапы.
11. `docs/phantoms/reports/PHANTOM-LIVING-PRESENCE-065.md` — P7, используемый observer, tools, guards, environment.
12. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — requireOffensiveShots, visible life wiring, retention/demand, operatorLifeProgress.
Документы самого пакета в этот лимит исходников не входят. Далее каждый этап имеет свой узкий набор, не читать их все целиком заранее. Дополнительные файлы — только path/symbol/причина в PLAN066.

## A/B: бой, ordinary AutoPlay, добыча (общий prefix java/org/l2jmobius/gameserver/)
- `model/actor/Creature.java`: doAttack, beginCast; paid-shot refusal стоит до native исполнения (~1037, ~1860).
- `model/actor/PlayerNativeWork.java`: Owner/prepareOffensiveShot (~348); запрет stale owner нельзя смешивать с пустым stock.
- `phantoms/combat/PhantomNativeShotControl.java`: prepare/activate/charge, владение автозарядкой.
- `phantoms/player/PhantomNativeWorkScope.java`: shot owner, native admission/epoch/earned/checkpoint.
- `phantoms/background/PhantomVisibleAutoPlay.java`: configure, targetDirective, acquireTick, yieldAfterCurrentTarget, observeProgress, resourcePause.
- `taskmanagers/AutoPlayTaskManager.java`: existing Sweeper (~150), pickup (~262), ранние HOLD/target branches; pickup сейчас radius200.
- `taskmanagers/AutoUseTaskManager.java`: Spoil/native skill selection и совместный command gate.
- `phantoms/combat/L2jCombatBackend.java`, `PhantomCombatService.java`: lootCandidates/eligibleLoot/pickUp/processLoot; отдельный путь не забыть.
- `model/actor/Player.java`: doPickupItem; текущие owner/party/protection и inventory мутации. Тип drop protection и native Sweep/Spoil handlers найти по вызовам.
- `phantoms/commerce/L2jPhantomSupplyJourney.java`: proposal/needsOrOwns/choices/yield, бюджет нулевого actor.
- `phantoms/background/L2jPhantomBackgroundAuthority.java`: shotContract/loadout и обычные farm inputs, только если затронуты A.

## C: использовать существующий capacity/retirement контур
- `phantoms/player/PhantomMaterializationService.java`: semaphore/admission/release/snapshot; сейчас strict `_maximumMaterialized`.
- `phantoms/player/PhantomMaterializedPlayer.java`: drain/store, retain-on-failure; не освобождать ресурс до завершения.
- `phantoms/activity/PhantomMaterializationRetentionPolicy.java`: RECENT_HUMAN, retained/reclaimable/permitsNewActions; grace задаётся существующим wiring (сейчас60s).
- `phantoms/activity/PhantomReconcileFirstActivityPort.java`, `phantoms/PhantomScheduler.java`: readiness, soft reclamation, grace, queued retry.
- `phantoms/topology/PhantomHumanLocalityControl.java`, `PhantomNativeLocalityEnvelope.java`: current demand и native visibility.
- `phantoms/population/PhantomPopulationEcologyService.java`: physical/preparation slots и shared focus.
- `config/custom/PhantomPlayersConfig.java`, `dist/game/config/Custom/PhantomPlayers.ini`: settings и validation. Искать все зависимые `availablePermits`/`maximumMaterialized` адресно.

## D: готовые хозяйственные механизмы, а не новая экономика
- `phantoms/commerce/L2jCommerceBackend.java`: quoteSell/execute/snapshot; SELL уже есть, refund и zero-sell сейчас rejected (~197–254).
- `phantoms/commerce/PhantomCommerceService.java`, `PhantomCommerceReceipt.java`, `PhantomSupplyIntentStore.java`: приём операции, durable receipt и повторное исполнение.
- `phantoms/economy/PhantomEconomyConflictPort.java`, `PhantomEconomyReservationService.java`: существующие economy reservations.
- `qol/CrystallizationService.java`: inspect/crystallize и фактическая mutation. Реиспользование требует проверки native-work/economy fences, не только вызова метода.
- `model/itemcontainer/PlayerWarehouse.java`, `ItemContainer.java`: validateCapacity/transferItem; stock warehouse уже есть.
- `network/clientpackets/SendWareHouseDepositList.java`, `SendWareHouseWithDrawList.java`, `RequestSellItem.java`: native правила/reference, не внутренний Phantom API.
- `phantoms/progression/PhantomNativeEquipmentPolicy.java`: защита подходящей экипировки; `data/xml/RecipeData.java` и `dist/game/data/Recipes.xml`: связь key-part→product.
- `phantoms/economy/PhantomAutonomousMarketProducer.java`, `PhantomStoreService.java`, `PhantomPrivateMarketPricingAuthority.java`: existing stores/quote/surplus; warehouse не считать дополнительным продаваемым inventory.

## Наблюдение/сборка/проверки — читать при подготовке среды
- `localplay/LocalPlayPilotService.java` с java-prefix: startConfigured, loadObservationOrigin (~653), autonomousObservationPoint, refreshObserverFocus и observer output (~638–800).
- `config/custom/LocalPlayPilotConfig.java`, `dist/game/config/Custom/LocalPlayPilot.ini`: EnableLocalPlayPilot, EnableLocalPlayPilotAutoAttach, LocalPlayPilotAutoAttachCharacters, LocalPlayObserverProfileIds.
- `LocalPlaySyntheticCharacterObjectId/Name` используются для persisted viewpoint и при `EnableLocalPlaySyntheticHuman=False`; это не команда загрузить настоящего Player.
- `build.xml`, существующий `PhantomTestLauncher`: выявить актуальные affected modes, не угадывать несуществующие ant targets.
- В `tools/` найти по basename `Build-Task065.ps1`, `Runtime065.ps1`, Assemble065 и вызываемый PersistentLane/guards; создать точный066 binding по образцу. Старые pinned065 скрипты не ломать.

## Важные отрицательные факты
Обычный background допускает MONSTER, а RAID/GRAND_BOSS обслуживаются иным native контуром; «виртуальные убийства эпиков уже работают» не подтверждено. Не добавлять их в066.
Нынешний market producer зависит от materialized owners, а не готовый пул сотен offline actors. Не менять это в066.
`PersonalCharacterQoL.ini/AllowedAccounts` не является текущим selector наблюдателя. LocalPlay использует свой явный autoattach список имён и проверенную real-client identity; не выдавать через это QoL/GM-права.
