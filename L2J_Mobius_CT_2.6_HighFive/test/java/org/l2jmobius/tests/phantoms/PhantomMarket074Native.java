/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeShotControl;
import org.l2jmobius.gameserver.phantoms.commerce.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.economy.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** One finite B-grade market case; native bootstrap and JVM lifecycle belong to the OWN074 caller. */
final class PhantomMarket074Native
{
	static boolean run(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, StringBuilder report) throws Exception
	{
		return run(knowledge, topology, report, false);
	}
	static boolean runManufacture(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, StringBuilder report) throws Exception
	{
		return run(knowledge, topology, report, true);
	}
	/** Existing paid state only: republish the same finite stores, query the corrected index, then close. */
	static boolean runRetainedIndex(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, StringBuilder report) throws Exception
	{
		check(PhantomTask074Lane.enabled(), "Explicit OWN074 retained-index lane required.");
		final String before = retainedMarketResources(); final var profiles = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(profiles); final var intents = new PhantomSupplyIntentStore(profiles, goals);
		check(profiles.find(5).orElseThrow().characterObjectId() == 268481805 && profiles.find(6).orElseThrow().characterObjectId() == 268481814, "Retained market profile links differ.");
		final var root5 = goals.load(5); final var root6 = goals.load(6); final var intent5 = intents.intent(5); final var intent6 = intents.intent(6);
		final var policy = PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml")); final var reservations = new PhantomEconomyReservationService(policy);
		final var audit5 = reservations.findAudit(5, 16); final var audit6 = reservations.findAudit(6, 16);
		check(audit5.size() == 1 && audit6.size() == 3, "Retained paid audit cardinality differs.");
		for (var audit : java.util.stream.Stream.concat(audit5.stream(), audit6.stream()).toList()) { check(audit.state() == PhantomEconomyOperation.State.COMMITTED && audit.result() == PhantomEconomyOperation.Result.SUCCESS && reservations.find(audit.operationId()).orElseThrow().state() == PhantomEconomyOperation.State.COMMITTED && reservations.findReservations(audit.operationId()).isEmpty(), "Retained paid operation is not committed/drained."); }
		final var cleanup = new java.util.ArrayList<AutoCloseable>(); Player seller = null, maker = null;
		try
		{
			for (int id : new int[] {268481805, 268481810, 268481814, 268481822}) { check(World.getInstance().findObject(id) == null && World.getInstance().getPlayer(id) == null, "Retained market actor is already active; no replacement."); }
			seller = Player.load(268481810); maker = Player.load(268481822); check(seller != null && maker != null && !seller.isGM() && !maker.isGM(), "Retained native store owners did not load.");
			final var stock = seller.getInventory().getItemByObjectId(268481811); check(stock != null && stock.getId() == 1465 && stock.getCount() == 240 && !stock.isEquipped() && seller.getAdena() == 4800, "Retained finite SELL stock/wallet differs.");
			check(maker.getSkillLevel(172) == 6 && maker.hasRecipeList(22) && maker.getCurrentMp() > 0 && maker.getAdena() == 100, "Retained learned maker/MP/fee differs.");
			for (var owner : List.of(seller, maker)) { owner.stopAllTasks(); owner.spawnMe(); owner.revalidateZone(true); check(owner.canOpenPrivateStore() && owner.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "Retained native counter is no longer lawful."); }
			check(seller.getSellList().getItems().isEmpty() || seller.getSellList().getItems().size() == 1 && seller.getSellList().getItems().stream().allMatch(line -> line.getObjectId() == stock.getObjectId() && line.getCount() == 240 && line.getPrice() == 80), "Retained seller listing changed.");
			if (seller.getSellList().getItems().isEmpty()) { check(seller.getSellList().addItem(stock.getObjectId(), 240, 80) != null, "Retained native SELL publication refused."); }
			check(maker.getManufactureItems().isEmpty() || maker.getManufactureItems().size() == 1 && maker.getManufactureItems().get(22) != null && maker.getManufactureItems().get(22).getCost() == 100, "Retained manufacture listing changed.");
			if (maker.getManufactureItems().isEmpty()) { maker.getManufactureItems().put(22, new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(22, 100)); }
			seller.getSellList().setTitle("OWN074 finite B Soulshot"); maker.setStoreName("OWN074 finite B production");
			seller.sitDown(); seller.setOfflineStartTime(System.currentTimeMillis()); seller.setPrivateStoreType(PrivateStoreType.SELL); OfflineTraderTable.getInstance().onTransaction(seller, false, true);
			maker.sitDown(); maker.setOfflineStartTime(System.currentTimeMillis()); maker.setPrivateStoreType(PrivateStoreType.MANUFACTURE); OfflineTraderTable.getInstance().onTransaction(maker, false, true);
			final var metrics = new PhantomMetrics(); final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
			cleanup.add(() -> check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "Retained market materialization did not STOP."));
			check(materialization.start() && materialization.materialize(5).status() == PhantomMaterializationService.ResultStatus.SUCCESS && materialization.materialize(6).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Retained buyers did not ordinarily materialize.");
			final var buyer = World.getInstance().getPlayer(268481805); final var crafted = World.getInstance().getPlayer(268481814);
			check(buyer != null && crafted != null && buyer.getAdena() == 5200 && buyer.getInventory().getInventoryItemCount(1465, -1) == 59 && crafted.getAdena() == 17400 && crafted.getInventory().getInventoryItemCount(1465, -1) == 449 && crafted.getInventory().getInventoryItemCount(1785, -1) == 0 && crafted.getInventory().getInventoryItemCount(1460, -1) == 0, "Retained paid buyers/resources differ.");
			final var indexed = World.getInstance().getPrivateStorePlayers(java.util.Set.of(1465)); final var crystals = World.getInstance().getPrivateStoreCrystalPlayers(1460);
			check(indexed.contains(seller) && indexed.contains(maker) && !stock.getTemplate().isCrystallizable() && !crystals.contains(seller) && !crystals.contains(maker), "Corrected typed native SELL/MFG/crystal membership differs.");
			// Passive service views: no start/recovery, discover, claim, advance, dispatch or replay.
			final var social = new PhantomMultipartyEconomyService(policy, reservations, new PhantomEconomyOfferService(), materialization, intents, profiles); final var offline = new PhantomOfflineSupply(social);
			offline.bindRecipePlanning(new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner(knowledge, org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml")).limits()));
			final var root = root5.orElseThrow().goal(); final var binding = new PhantomSupplyIntentStore.Binding(root.revision(), buyer.getObjectId(), buyer.getActiveClass(), buyer.getClassIndex(), buyer.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(buyer), 0);
			final var supplies = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
			final var proposal = offline.offer(buyer, root, binding, 1, List.of(ItemData.getInstance().getTemplate(1465)), 60, System.currentTimeMillis() + 60000, Long.MAX_VALUE, owner -> 0, fees -> supplies.availableBudget(buyer.getAdena(), fees, 0, true)).orElseThrow();
			final var quote = PhantomOfflineSupply.quote(buyer, proposal, owner -> 0).orElseThrow(); final var old = intent5.orElseThrow().goal();
			check(PhantomOfflineSupply.spec(proposal) instanceof PhantomSocialEconomyGoalSpec.StoreBuy buy && buy.counterpartyCharacterObjectId() == seller.getObjectId() && quote.purchaseAdena() == 4640 && quote.count() == 58 && supplies.availableBudget(5200, 0, 0, true) == 4700 && PhantomOfflineSupply.quote(buyer, old, owner -> 0).isEmpty(), "Current reserve-bound quote or original stale listing rejection differs.");
			check(root5.equals(goals.load(5)) && root6.equals(goals.load(6)) && intent5.equals(intents.intent(5)) && intent6.equals(intents.intent(6)) && audit5.equals(reservations.findAudit(5, 16)) && audit6.equals(reservations.findAudit(6, 16)), "Read-only quote changed a retained root/intent/paid audit.");
			report.append("RETAINED_MARKET074_INDEX\tREAD_ONLY_ECONOMIC_CASE;nativeRepublishSameFiniteSELL240x1465@80_and_MFG22@100;typed1465=").append(indexed.stream().map(Player::getObjectId).sorted().toList()).append(";crystal1460=").append(crystals.stream().map(Player::getObjectId).sorted().toList()).append(";freshQuote=").append(quote).append(";requested60;budget4700;admitted58/4640A;originalPaidListingHashRejected=true;buyer59/449B preserved;audits1+3COMMITTED=").append(audit5).append(audit6).append(";noAutomaticNeedClaim;noNewPaidEffects\n");
		}
		finally
		{
			try { PhantomPvp072Native.closeAll(cleanup); } finally { try { closeOwner(maker); } finally { closeOwner(seller); } }
			check(before.equals(retainedMarketResources()), "Retained-index native publication/cleanup changed exact item rows/wallets.");
			check(World.getInstance().getPrivateStorePlayers(java.util.Set.of(1465)).stream().noneMatch(owner -> owner.getObjectId() == 268481810 || owner.getObjectId() == 268481822) && World.getInstance().getPrivateStorePlayers().stream().noneMatch(owner -> owner.getObjectId() == 268481810 || owner.getObjectId() == 268481822), "Retained stores survived ordinary index cleanup.");
			report.append("RETAINED_MARKET074_CLEANUP\tPASS exactFourActorsInventoryWalletRowsUnchanged;nativeStoreIndexMembershipCleared;noPurchaseOrProductionRepeated\n");
		}
		return true;
	}
	private static String retainedMarketResources() throws Exception
	{
		final var expected = Map.of(268481805, "task074market", 268481810, "task074seller", 268481814, "task074craft", 268481822, "task074maker"); final var result = new StringBuilder();
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); check(PhantomTask074Lane.TEST.equals(connection.getCatalog()), "Retained index requires exact OWN074 TEST schema.");
			try (var query = connection.prepareStatement("SELECT charId,account_name,accesslevel FROM characters WHERE charId IN(268481805,268481810,268481814,268481822) ORDER BY charId"); var rows = query.executeQuery()) { int count = 0; while (rows.next()) { check(expected.get(rows.getInt(1)).equals(rows.getString(2)) && rows.getInt(3) == 0, "Retained index actor identity differs."); count++; } check(count == 4, "Retained index actor absent; no reseed."); }
			try (var query = connection.prepareStatement("SELECT owner_id,object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id IN(268481805,268481810,268481814,268481822) ORDER BY owner_id,object_id"); var rows = query.executeQuery()) { while (rows.next()) { for (int column = 1; column <= 7; column++) { result.append(rows.getString(column)).append(':'); } result.append(';'); } }
		}
		return result.toString();
	}
	private static boolean run(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, StringBuilder report, boolean manufacture) throws Exception
	{
		final var cleanup = new java.util.ArrayList<AutoCloseable>(); Player seller = null, maker = null;
		try
		{
			final var merchant = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Merchant npc && npc.getId() == 30063 && npc.isSpawned() && npc.getInstanceId() == 0 && npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE)).map(object -> (Merchant) object).findFirst().orElseThrow(() -> new AssertionError("MARKET074 native peace-zone Lara30063 absent."));
			final var counter = PhantomOfflineSupply060Fixture.counterPoint(new PhantomNavigationPoint(merchant.getX() + 1000, merchant.getY(), merchant.getZ(), 0), merchant.getX(), merchant.getY(), merchant.getZ());
			check(counter != null, "MARKET074 no lawful native offline counter.");
			final var paths = new L2jNavigationBackend(); final var stand = nearbyStand(paths, counter, null);
			final var makerPoint = manufacture ? nearbyStand(paths, stand, counter) : null;
			final String buyerAccount = manufacture ? "task074craft" : "task074market", sellerAccount = manufacture ? "task074inputs" : "task074seller";
			requireAbsentAccounts(manufacture ? new String[] {buyerAccount, sellerAccount, "task074maker"} : new String[] {buyerAccount, sellerAccount});
			report.append("MARKET074_PREFLIGHT\tcounter=").append(counter).append(";buyerStand=").append(stand).append(";makerStand=").append(makerPoint).append(";bounded48samplesPerStand;nativeGeoDirectBothWays;all accounts absent before fixture\n");
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,'DISABLED_NATIVE_TEST_ONLY',-1,1),(?,'DISABLED_NATIVE_TEST_ONLY',-1,1)")) { statement.setString(1, buyerAccount); statement.setString(2, sellerAccount); statement.executeUpdate(); }
			final var setup = player(buyerAccount, manufacture ? "Craft074" : "Market074", 2, 52, manufacture ? stand : new PhantomNavigationPoint(counter.x() + 7000, counter.y(), counter.z(), 0));
			final var weapon = setup.addItem(ItemProcessType.REWARD, 79, 1, setup, false); setup.getInventory().equipItem(weapon); setup.giveAvailableAutoGetSkills(); setup.refreshExpertisePenalty();
			check(setup.getAdena() == 0, "MARKET074 refuses an existing buyer wallet."); setup.addAdena(ItemProcessType.REWARD, manufacture ? 40000 : 10000, setup, false); setup.addItem(ItemProcessType.REWARD, 1835, 3, setup, false); setup.addItem(ItemProcessType.REWARD, 1463, 3, setup, false);
			setup.getInventory().updateDatabase(); setup.storeMe(); final int buyerId = setup.getObjectId(); setup.deleteMe();
			final var profiles = PhantomProfileRepository.open(); final long profile = profiles.create(buyerId).profileId(); final var metrics = new PhantomMetrics(); final var goals = new PhantomGoalStateStore(profiles);
			seller = player(sellerAccount, manufacture ? "Inputs074" : "Seller074", 53, 5, counter); seller.setOnlineStatus(true, false); seller.spawnMe(); seller.revalidateZone(true);
			check(seller.canOpenPrivateStore() && seller.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "MARKET074 native seller cannot publish here.");
			for (long[] line : manufacture ? new long[][] {{1785, 108, 250}, {1460, 2, 9000}} : new long[][] {{1465, 300, 80}}) { final var stock = seller.addItem(ItemProcessType.REWARD, (int) line[0], line[1], seller, false); check(stock != null && seller.getSellList().addItem(stock.getObjectId(), line[1], line[2]) != null, "MARKET074 finite declared listing rejected."); }
			seller.getSellList().setTitle(manufacture ? "OWN074 finite native B components" : "OWN074 finite B Soulshot"); seller.sitDown(); seller.setOfflineStartTime(System.currentTimeMillis()); seller.setPrivateStoreType(PrivateStoreType.SELL); seller.getInventory().updateDatabase(); seller.storeMe(); OfflineTraderTable.getInstance().onTransaction(seller, false, true);
			check(PhantomOfflineSupply.offline(seller), "MARKET074 published seller is not a native offline store."); final Player owner = seller;
			if (manufacture)
			{
				final var recipe = RecipeData.getInstance().getRecipeList(22); final var skill = SkillData.getInstance().getSkill(172, 6);
				check(recipe != null && recipe.getLevel() == 6 && recipe.getItemId() == 1465 && recipe.getCount() == 450 && recipe.getSuccessRate() == 100 && skill != null && recipe.getRecipes().length == 2 && java.util.Arrays.stream(recipe.getRecipes()).anyMatch(item -> item.getItemId() == 1785 && item.getQuantity() == 54) && java.util.Arrays.stream(recipe.getRecipes()).anyMatch(item -> item.getItemId() == 1460 && item.getQuantity() == 1) && java.util.Arrays.stream(recipe.getStatUse()).filter(stat -> stat.getType() == org.l2jmobius.gameserver.data.enums.StatType.MP).mapToLong(stat -> stat.getValue()).sum() == 100, "CRAFT074 recipe22 native contract differs.");
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task074maker','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { statement.executeUpdate(); }
				maker = player("task074maker", "Maker074", 57, 52, makerPoint); maker.addSkill(skill, true); maker.registerDwarvenRecipeList(recipe, true); maker.getManufactureItems().put(22, new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(22, 100)); maker.setOnlineStatus(true, false); maker.spawnMe(); maker.revalidateZone(true); check(maker.canOpenPrivateStore() && maker.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "CRAFT074 lawful maker counter unavailable."); maker.setStoreName("OWN074 finite B production"); maker.sitDown(); maker.setOfflineStartTime(System.currentTimeMillis()); maker.setPrivateStoreType(PrivateStoreType.MANUFACTURE); maker.storeMe(); OfflineTraderTable.getInstance().onTransaction(maker, false, true);
			}
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1); cleanup.add(() -> { final var stopped = materialization.shutdown(); report.append("MARKET074_SHUTDOWN\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "MARKET074 materialization STOP pending."); });
			check(materialization.start() && materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "MARKET074 native buyer did not materialize."); final var buyer = World.getInstance().getPlayer(buyerId); check(buyer != null && !buyer.isGM() && buyer.getClient() == null && buyer.getLevel() == 52 && buyer.getActiveWeaponItem().getCrystalTypePlus() == CrystalType.B, "MARKET074 prepared B-grade ordinary buyer differs.");
			final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> buyer.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
			final var root = new PhantomGoal(manufacture ? 74007406L : 74007405L, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)), new PhantomDomainRef("npc", "20418"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20418@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture074.market_representative", 1); goals.insert(profile, root);
			final var policy = PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml")); final var reservations = new PhantomEconomyReservationService(policy); check(reservations.start(), "MARKET074 native reservations unavailable."); cleanup.add(() -> reservations.shutdown(System.currentTimeMillis()));
			final var supplyStore = new PhantomSupplyIntentStore(profiles, goals); final var social = new PhantomMultipartyEconomyService(policy, reservations, new PhantomEconomyOfferService(), materialization, supplyStore, profiles); cleanup.add(() -> check(social.shutdown(System.currentTimeMillis()).successful(), "MARKET074 multiparty STOP pending."));
			final var exactNeed = ItemData.getInstance().getTemplate(1465); check(exactNeed != null && exactNeed.getCrystalType() == CrystalType.B && buyer.getActiveWeaponItem().getSoulShotCount() == 1, "MARKET074 exact item/grade/per-hit facts changed.");
			try (var lease = materialization.tryAcquireAction(profile).orElseThrow())
			{
				final var binding = new PhantomSupplyIntentStore.Binding(root.revision(), buyerId, buyer.getActiveClass(), buyer.getClassIndex(), buyer.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(buyer), 0);
				if (!manufacture) { PhantomOfflineMarket074Checks.globalDiscovery(new PhantomOfflineSupply(social), buyer, root, binding, exactNeed, owner.getObjectId(), report); }
				buyer.setXYZ(stand.x(), stand.y(), stand.z()); buyer.revalidateZone(true); buyer.setRunning();
			}
			check(buyer.calculateDistance3D(owner) <= 150 && paths.canWalkDirect(PhantomNativeTravelActions.point(buyer), counter), "MARKET074 final declared buyer/counter fixture is not physically reachable.");
			final var loaded = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var commerce = new PhantomCommerceService(loaded, receipts, supplyStore, new L2jCommerceBackend(materialization, loaded.catalog(), java.time.Clock.systemDefaultZone())); check(commerce.start(), "MARKET074 commerce unavailable."); cleanup.add(() -> { commerce.beginStop(); check(commerce.finishStop(), "MARKET074 commerce STOP pending."); });
			final var navigation = new PhantomNavigationService(metrics); check(navigation.start(), "MARKET074 navigation unavailable."); cleanup.add(() -> { navigation.beginStop(); await(navigation::finishStop, 12000, "MARKET074 navigation STOP pending."); });
			final var original = new java.util.concurrent.atomic.AtomicReference<PhantomGoal>();
			final var originals = new java.util.concurrent.ConcurrentSkipListMap<Long, PhantomGoal>();
			final var journey = new L2jPhantomSupplyJourney(supplyStore, goals, materialization, commerce, receipts, navigation, () -> knowledge, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology), id -> true, (id, deadline) -> { supplyStore.intent(id).filter(stored -> PhantomSupplyIntentStore.social(stored.goal()) && stored.goal().status() == PhantomGoalStatus.ACTIVE).ifPresent(stored -> { original.compareAndSet(null, stored.goal()); originals.putIfAbsent(stored.goal().revision(), stored.goal()); }); return true; }, (id, goal) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"))); journey.useOfflineSupply(social); journey.useInventoryMaintenance(id -> reservations.findActive(id).isEmpty()); check(journey.start(), "MARKET074 connected offline journey unavailable."); cleanup.add(() -> { journey.beginStop(); check(journey.finishStop(), "MARKET074 journey STOP pending."); });
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final long buyerBefore = buyer.getAdena(), sellerBefore = seller.getAdena(), sellerStock = seller.getInventory().getInventoryItemCount(1465, -1); final long ng = buyer.getInventory().getInventoryItemCount(1835, -1), d = buyer.getInventory().getInventoryItemCount(1463, -1);
			final double makerMp = maker == null ? 0 : maker.getCurrentMp(); final long makerAdena = maker == null ? 0 : maker.getAdena();
			if (manufacture)
			{
				final var readonly = new PhantomOfflineSupply(social);
				readonly.bindRecipePlanning(new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner(knowledge, org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.load(Path.of("data/phantoms/acquisition/high-five-acquisition-v1.xml")).limits()));
				final var binding = new PhantomSupplyIntentStore.Binding(root.revision(), buyerId, buyer.getActiveClass(), buyer.getClassIndex(), buyer.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(buyer), 0);
				final var proposed = readonly.offer(buyer, root, binding, 1, List.of(exactNeed), 60, System.currentTimeMillis() + 480000, Long.MAX_VALUE, ownerForFee -> 0, fee -> buyer.getAdena() - 500).orElseThrow();
				final var beforeQuote = PhantomOfflineSupply.quote(buyer, proposed, ownerForFee -> 0).orElseThrow();
				check(beforeQuote.purchaseAdena() == 22600 && beforeQuote.count() == 450, "CRAFT074 full exact BOM quote omitted an input or fee.");
				try
				{
					maker.getManufactureItems().put(22, new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(22, 50000));
					final var changed = PhantomOfflineSupply.quote(buyer, proposed, ownerForFee -> 0).orElseThrow();
					check(changed.purchaseAdena() > buyer.getAdena() && buyer.getAdena() == buyerBefore && seller.getInventory().getInventoryItemCount(1785, -1) == 108, "CRAFT074 stale first-input quote hid changed whole-BOM cost or wrote resources.");
					report.append("CRAFT074_REQUOTE_NEGATIVE\tPREMEASUREMENT owned-maker quote temporarily changed/restored;first-input listing unchanged;before=").append(beforeQuote).append(";fresh=").append(changed).append(";whole plan unaffordable;native money/items unchanged;no dispatch or price config edit\n");
				}
				finally { maker.getManufactureItems().put(22, new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(22, 100)); }
			}
			if (manufacture) { check(buyer.getInventory().getInventoryItemCount(1785, -1) == 0 && buyer.getInventory().getInventoryItemCount(1460, -1) == 0 && seller.getInventory().getInventoryItemCount(1785, -1) == 108 && seller.getInventory().getInventoryItemCount(1460, -1) == 2, "CRAFT074 exact finite component preparation differs."); }
			report.append(manufacture ? "CRAFT074_MEASUREMENT\t" : "MARKET074_MEASUREMENT\t").append("buyer=").append(buyerId).append(";profile=").append(profile).append(";seller=").append(seller.getObjectId()).append(";finitePriorWallet=").append(buyerBefore).append(manufacture ? ";finiteSellerInputs=108x1785@250+2x1460@9000;buyerInputs=0;recipe22=54ore+1Bcrystal->450B1465/MP100/chance100;makerFee100;maker=" + maker.getObjectId() + ";makerMP=" + makerMp : ";finiteSellerStock=" + sellerStock + ";quotedUnit=80").append(";item1465/B/SOULSHOT;equipped79/B;perHit=1;wrongNG=").append(ng).append(";wrongD=").append(d).append(";fixture finalplacement explicitly PREMEASUREMENT, not a walked route;no resource/XYZ writes after marker;prior stock/wealth not current earned\n");
			try (var lease = materialization.tryAcquireAction(profile).orElseThrow(); var shots = new PhantomNativeShotControl(buyer)) { check(shots.activate(false) == ShotOutcome.UNAVAILABLE && buyer.getInventory().getInventoryItemCount(1835, -1) == ng && buyer.getInventory().getInventoryItemCount(1463, -1) == d && !buyer.isChargedShot(ShotType.SOULSHOTS), "MARKET074 wrong NG/D grade unexpectedly charged B weapon."); }
			int sequence = 0; String last = ""; final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(manufacture ? 125 : 40);
			while (System.nanoTime() < until)
			{
				final var planning = new PhantomPlanningContext(profile, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), ++sequence); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(profile, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), sequence, () -> false)); }
				final var intent = journey.automaticIntent(profile).orElse(null);
				final var reason = journey.reason(profile); if (!reason.equals(last)) { report.append("MARKET074_JOURNEY\t").append(reason).append(";intent=").append(intent).append(";wallet=").append(buyer.getAdena()).append(";Bstock=").append(buyer.getInventory().getInventoryItemCount(1465, -1)).append('\n'); last = reason; }
				if (intent != null && intent.status() == PhantomGoalStatus.COMPLETED && PhantomSupplyIntentStore.Binding.parse(intent).retryAfterEpochMillis() > 0 && (!manufacture || buyer.getInventory().getInventoryItemCount(1465, -1) == 450)) { break; }
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { break; } Thread.sleep(100);
			}
			if (manufacture)
			{
				final var audits = reservations.findAudit(profile, 16).stream().filter(row -> row.state() == PhantomEconomyOperation.State.COMMITTED).toList();
				report.append("CRAFT074_NATIVE_AUDITS\t").append(audits).append(";capturedActualChildren=").append(originals.values()).append(";wallet=").append(buyer.getAdena()).append(";Bstock=").append(buyer.getInventory().getInventoryItemCount(1465, -1)).append(";makerMP=").append(maker.getCurrentMp()).append('\n');
				final long buys = audits.stream().filter(row -> row.kind() == PhantomEconomyOperation.Kind.PRIVATE_STORE_BUY && row.result() == PhantomEconomyOperation.Result.SUCCESS).count();
				check(buys >= 1 && buys <= 2 && audits.size() == buys + 1, "CRAFT074 autonomous native ingredient BUY operations did not commit.");
				final var craft = audits.stream().filter(row -> row.kind() == PhantomEconomyOperation.Kind.PLAYER_MANUFACTURE && row.result() == PhantomEconomyOperation.Result.SUCCESS).findFirst().orElseThrow(() -> new AssertionError("CRAFT074 native manufacture did not commit."));
				check(craft.itemsConsumed() == 55 && craft.itemsProduced() == 450 && buyer.getAdena() == buyerBefore - 22600 && seller.getAdena() == sellerBefore + 22500 && maker.getAdena() == makerAdena + 100 && seller.getInventory().getInventoryItemCount(1785, -1) == 54 && seller.getInventory().getInventoryItemCount(1460, -1) == 1 && buyer.getInventory().getInventoryItemCount(1785, -1) == 0 && buyer.getInventory().getInventoryItemCount(1460, -1) == 0 && buyer.getInventory().getInventoryItemCount(1465, -1) == 450 && maker.getCurrentMp() < makerMp, "CRAFT074 paid ingredients/native MP/fee/output conservation differs.");
				for (var audit : audits) { check(reservations.findReservations(audit.operationId()).isEmpty(), "CRAFT074 committed step retained reservations."); }
				for (var child : originals.values()) { report.append("CRAFT074_REPLAY\t").append(new PhantomOfflineSupply(social).advance(profile, child, 1, ++sequence)).append('\n'); }
				check(buyer.getAdena() == buyerBefore - 22600 && seller.getAdena() == sellerBefore + 22500 && maker.getAdena() == makerAdena + 100 && buyer.getInventory().getInventoryItemCount(1465, -1) == 450 && reservations.findAudit(profile, 16).stream().filter(row -> row.state() == PhantomEconomyOperation.State.COMMITTED).count() == audits.size(), "CRAFT074 delayed step replay duplicated physical consequences.");
				try (var lease = materialization.tryAcquireAction(profile).orElseThrow(); var shots = new PhantomNativeShotControl(buyer)) { check(shots.activate(false) == ShotOutcome.ACTIVATED && buyer.isChargedShot(ShotType.SOULSHOTS) && buyer.getInventory().getInventoryItemCount(1465, -1) == 449 && buyer.getInventory().getInventoryItemCount(1835, -1) == ng && buyer.getInventory().getInventoryItemCount(1463, -1) == d, "CRAFT074 native produced B shot was not usable at its exact equipped grade."); }
				report.append("CRAFT074_RESULT\tPASS bounded autonomous componentBUY1785+BUY1460->native paid manufacture22/MP100/55inputs->450B1465->matchingBcharge449;prior money/stock/recipe skill fixture declared;no selfcraft/full recipe tree or earned career claim\n"); return true;
			}
			check(original.get() != null, "MARKET074 autonomous need did not select an offline supply channel."); final var spec = PhantomOfflineSupply.spec(original.get()); check(spec instanceof PhantomSocialEconomyGoalSpec.StoreBuy buy && buy.counterpartyCharacterObjectId() == seller.getObjectId() && buy.lines().stream().allMatch(line -> line.itemId() == 1465 && line.price() == 80), "MARKET074 autonomous choice changed the declared seller/item/grade/price.");
			final var buy = (PhantomSocialEconomyGoalSpec.StoreBuy) spec; final long quantity = buy.lines().stream().mapToLong(line -> line.count()).sum(), paid = buy.lines().stream().mapToLong(line -> Math.multiplyExact(line.count(), line.price())).sum();
			final var audit = reservations.findAudit(profile, 16).stream().filter(row -> row.state() == PhantomEconomyOperation.State.COMMITTED).findFirst().orElseThrow(() -> new AssertionError("MARKET074 no native paid COMMITTED operation audit.")); final var operation = reservations.find(audit.operationId()).orElseThrow();
			check(audit.result() == PhantomEconomyOperation.Result.SUCCESS && operation.state() == PhantomEconomyOperation.State.COMMITTED && buyer.getAdena() == buyerBefore - paid && seller.getAdena() == sellerBefore + paid && seller.getInventory().getInventoryItemCount(1465, -1) == sellerStock - quantity && buyer.getInventory().getInventoryItemCount(1465, -1) == quantity && reservations.findReservations(audit.operationId()).isEmpty(), "MARKET074 native paid conservation/ownership mismatch.");
			final var replay = new PhantomOfflineSupply(social).advance(profile, original.get(), 1, ++sequence); check(buyer.getAdena() == buyerBefore - paid && seller.getAdena() == sellerBefore + paid && buyer.getInventory().getInventoryItemCount(1465, -1) == quantity && reservations.findAudit(profile, 16).stream().filter(row -> row.state() == PhantomEconomyOperation.State.COMMITTED).count() == 1, "MARKET074 replay duplicated native payment/items.");
			try (var lease = materialization.tryAcquireAction(profile).orElseThrow(); var shots = new PhantomNativeShotControl(buyer)) { check(shots.activate(false) == ShotOutcome.ACTIVATED && buyer.isChargedShot(ShotType.SOULSHOTS) && buyer.getInventory().getInventoryItemCount(1465, -1) == quantity - buyer.getActiveWeaponItem().getSoulShotCount() && buyer.getInventory().getInventoryItemCount(1835, -1) == ng && buyer.getInventory().getInventoryItemCount(1463, -1) == d, "MARKET074 purchased matching B shot did not charge/debit through native handler."); }
			report.append("MARKET074_PAID\toperation=").append(operation).append(";audit=").append(audit).append(";buyerA=").append(buyerBefore).append("->").append(buyer.getAdena()).append(";sellerA=").append(sellerBefore).append("->").append(seller.getAdena()).append(";sellerStock=").append(sellerStock).append("->").append(seller.getInventory().getInventoryItemCount(1465, -1)).append(";bought=").append(quantity).append(";nativeBchargeStock=").append(buyer.getInventory().getInventoryItemCount(1465, -1)).append(";replay=").append(replay).append(";intent=").append(journey.automaticIntent(profile)).append("\nMARKET074_RESULT\tPASS representative B paid offlineSELL+native matching-grade charge consumption;not a rewarded B battle or full A/S/S80 assortment proof\n");
			return true;
		}
		catch (Exception | AssertionError failure) { failure.printStackTrace(System.err); report.append(manufacture ? "CRAFT074_RESULT\tPARTIAL;" : "MARKET074_RESULT\tPARTIAL;").append(failure.getClass().getSimpleName()).append(':').append(failure.getMessage()).append('\n'); return false; }
		finally
		{
			try { PhantomPvp072Native.closeAll(cleanup); }
			finally { try { closeOwner(maker); } finally { closeOwner(seller); } }
		}
	}
	private static PhantomNavigationPoint nearbyStand(L2jNavigationBackend paths, PhantomNavigationPoint from, PhantomNavigationPoint otherStore)
	{
		final var zones = org.l2jmobius.gameserver.managers.ZoneManager.getInstance();
		for (int radius : new int[] {64, 96, 128}) { for (int direction = 0; direction < 16; direction++)
		{
			final double angle = direction * Math.PI / 8; final int x = from.x() + (int) Math.round(radius * Math.cos(angle)), y = from.y() + (int) Math.round(radius * Math.sin(angle));
			final var point = paths.walkSurface(new PhantomNavigationPoint(x, y, from.z(), from.instanceId()), 256).orElse(null);
			if (point == null || point.distanceTo(from) > 150 || !paths.canWalkDirect(from, point) || !paths.canWalkDirect(point, from)
				|| zones.getZone(x, y, point.z(), org.l2jmobius.gameserver.model.zone.type.PeaceZone.class) == null
				|| zones.getZone(x, y, point.z(), org.l2jmobius.gameserver.model.zone.type.NoStoreZone.class) != null
				|| zones.getZone(x, y, point.z(), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null) { continue; }
			if (otherStore != null && point.distanceTo(otherStore) <= Math.max(48, org.l2jmobius.gameserver.config.custom.PrivateStoreRangeConfig.SHOP_MIN_RANGE_FROM_PLAYER + 1)) { continue; }
			final var region = World.getInstance().getRegion(x, y, point.z());
			if (region == null || java.util.Arrays.stream(region.getSurroundingRegions()).flatMap(value -> value.getVisibleObjects().stream()).anyMatch(object -> object instanceof org.l2jmobius.gameserver.model.actor.Creature creature && creature.getInstanceId() == from.instanceId() && creature.getMinShopDistance() > 0 && creature.calculateDistance3D(x, y, point.z()) <= creature.getMinShopDistance() + creature.getTemplate().getCollisionRadius() + 16)) { continue; }
			return point;
		} }
		throw new AssertionError("MARKET074 no lawful native direct stand within150 after48 bounded radial samples; no account/actor created.");
	}
	private static void requireAbsentAccounts(String[] accounts) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM accounts WHERE login=?),(SELECT COUNT(*) FROM characters WHERE account_name=?)"))
		{
			connection.setReadOnly(true); for (var account : accounts) { statement.setString(1, account); statement.setString(2, account); try (var rows = statement.executeQuery()) { check(rows.next() && rows.getInt(1) == 0 && rows.getInt(2) == 0, "MARKET074 exact account already exists: " + account + "; retained state preserved; no duplicate/reseed."); } }
		}
	}
	private static Player player(String account, String name, int classId, int level, PhantomNavigationPoint point)
	{
		final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(classId == 53 || classId == 57 ? 53 : 0), account, name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); check(player != null && !player.isGM(), "MARKET074 ordinary finite fixture unavailable."); player.stopAllTasks(); player.setPlayerClass(classId); player.setBaseClass(classId); player.getStat().setExp(player.getStat().getExpForLevel(level)); player.getStat().setLevel((byte) level); player.giveAvailableAutoGetSkills(); player.setXYZ(point.x(), point.y(), point.z()); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp()); return player;
	}
	private static void closeOwner(Player owner) { if (owner != null) { OfflineTraderTable.getInstance().onTransaction(owner, true, false); owner.setOfflineStartTime(0); owner.stopAllTasks(); owner.storeMe(); owner.deleteMe(); } }
	private static void await(java.util.function.BooleanSupplier condition, long millis, String message) throws Exception { final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); boolean done = condition.getAsBoolean(); while (!done && System.nanoTime() < until) { Thread.sleep(50); done = condition.getAsBoolean(); } check(done, message); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
