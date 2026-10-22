/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue.Rates;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Persistent own068 native role scene. Fixture customer funds are explicitly not earned income. */
public final class PhantomMerchant068Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2 || !PhantomTask068Lane.enabled()) { throw new IllegalArgumentException("Own068 target and measurement seconds required."); }
		final int target = Integer.parseInt(args[0]), seconds = Integer.parseInt(args[1]);
		if (target < 1 || target > 200 || seconds < 1 || seconds > 90) { throw new IllegalArgumentException("Bounded merchant scene required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path reports = module.getParent().resolve(".phantom-local/task068/reports");
		final StringBuilder receipt = new StringBuilder("stage\tvalue\n");
		PhantomOfflineMerchantService service = null;
		Player customer = null;
		Player peer = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(68006803L, module, reports));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().loadInstances();
			org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().updateReferences();
			SpawnData.getInstance(); DoorData.getInstance();
			OfflineTradeConfig.OFFLINE_TRADE_ENABLE = true; OfflineTradeConfig.OFFLINE_CRAFT_ENABLE = true; OfflineTradeConfig.RESTORE_OFFLINERS = true;
			OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME = true; OfflineTradeConfig.OFFLINE_DISCONNECT_FINISHED = true;
			OfflineTraderTable.getInstance().restoreOfflineTraders();
			final int preparedMaker = Boolean.getBoolean("phantom.task068.merchant.prepareManufacturer") ? prepareManufacturer(receipt) : 0;
			final var topologyBackend = new L2jTopologyValidationBackend();
			final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final var topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
			final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
			final var backend = new L2jGameKnowledgeBackend();
			final var knowledge = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, knowledgePolicy), topology, knowledgePolicy).build();
			final var pricing = new PhantomPrivateMarketPricingAuthority(knowledge, new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog(), Rates.capture(), new Policy(500, 500), () -> World.getInstance().getVisibleObjects());
			final var catalog = new PhantomMerchantCatalog(pricing);
			receipt.append("catalog\t").append(catalog.entries().stream().map(entry -> entry.category() + ':' + entry.item().getId()).toList()).append('\n');
			Files.createDirectories(reports);
			final Path declared = reports.resolve("merchant-policy-" + target + ".ini");
			Files.writeString(declared, Files.readString(module.resolve("dist/game/" + PhantomMerchantPolicy.FILE), StandardCharsets.UTF_8).replace("EnableOfflinePhantomMerchants = False", "EnableOfflinePhantomMerchants = True").replace("MerchantTargetStores = 200", "MerchantTargetStores = " + target).replace("MerchantPreparedOfflineCharacterIds =", "MerchantPreparedOfflineCharacterIds =" + (preparedMaker == 0 ? "" : Integer.toString(preparedMaker))), StandardCharsets.UTF_8);
			final var policy = PhantomMerchantPolicy.load(declared);
			final var arrivals = topologySnapshot.anchors().stream().filter(anchor -> anchor.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.GATEKEEPER).map(anchor -> new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(anchor.point().x(), anchor.point().y(), anchor.point().z(), anchor.point().instanceId())).toList();
			service = new PhantomOfflineMerchantService(PhantomProfileRepository.open(), policy, () -> pricing, arrivals);
			service.start();
			if (preparedMaker > 0) { check(service.characterIds().contains(preparedMaker) && PhantomOfflineMerchantService.ownsNative(World.getInstance().getPlayer(preparedMaker)), "Prepared native manufacturer was not adopted."); }
			System.out.println("OWN068_MERCHANT_START " + service.snapshot());
			receipt.append("restore\t").append(service.snapshot()).append('\n');
			final long admissionDeadline = System.currentTimeMillis() + 90000;
			while (service.snapshot().nonemptyStores() < target && System.currentTimeMillis() < admissionDeadline)
			{
				service.onPulse(); Thread.sleep(1000);
				if (service.snapshot().blocked() > 0) { throw new AssertionError("Merchant admission blocked: " + service.snapshot()); }
			}
			check(service.snapshot().nonemptyStores() >= target, "Target not reached: " + service.snapshot());
			check(service.snapshot().combatOrMaterializationOwners() == 0, "Parked role registered combat work.");
			check(service.characterIds().size() == Set.copyOf(service.characterIds()).size(), "Duplicate character ids.");
			final var before = service.snapshot();
			customer = customer();
			if (target == 200) { peer = customer("Market068Peer"); }
			if (Boolean.getBoolean("phantom.task068.merchant.asyncPendingProbe")) { org.l2jmobius.gameserver.config.PlayerConfig.ALT_GAME_CREATION = true; receipt.append("PREMEASUREMENT\tnative ALT_GAME_CREATION=True; no chance or stat override\n"); }
			if (preparedMaker > 0) { manufacture(customer, World.getInstance().getPlayer(preparedMaker), receipt, service); }
			if (Boolean.getBoolean("phantom.task068.merchant.asyncPendingProbe"))
			{
				check(preparedMaker > 0 && service.beginStop(), "Completed native ALT craft did not drain.");
				stoppedManufacture(customer, World.getInstance().getPlayer(preparedMaker), receipt);
				OfflineTraderTable.getInstance().storeOffliners();
				System.out.println("OWN068_ASYNC_MANUFACTURE_STOP_PASS"); return;
			}
			final var sellers = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(player -> player.getPrivateStoreType() == PrivateStoreType.SELL).toList();
			final var buyers = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(player -> player.getPrivateStoreType() == PrivateStoreType.BUY).toList();
			check(!sellers.isEmpty() && !buyers.isEmpty(), "Real BUY and SELL required.");
			// Keep the idle scale sample independent from a deliberate end-stock replacement episode.
			trade(customer, sellers.stream().max(java.util.Comparator.comparingInt(player -> player.getSellList().getItemCount())).orElseThrow(), buyers.getFirst(), receipt);
			final var runtime = ManagementFactory.getRuntimeMXBean();
			final var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			final long cpuStart = os.getProcessCpuTime(), wallStart = System.nanoTime(), sqlStart = statements();
			for (int i = 0; i < seconds; i++) { service.onPulse(); Thread.sleep(1000); }
			final var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
			receipt.append("measurement\ttarget=").append(target).append(";seconds=").append(seconds).append(";cpuNs=").append(os.getProcessCpuTime() - cpuStart).append(";wallNs=").append(System.nanoTime() - wallStart).append(";heapUsed=").append(memory.getUsed()).append(";heapCommitted=").append(memory.getCommitted()).append(";threads=").append(ManagementFactory.getThreadMXBean().getThreadCount()).append(";sqlGlobalDelta=").append(statements() - sqlStart).append(";uptime=").append(runtime.getUptime()).append('\n');
			receipt.append("final\t").append(service.snapshot()).append('\n');
			check(service.snapshot().issuedCapital() == before.issuedCapital() && service.snapshot().issuedStockValue() == before.issuedStockValue(), "Measurement reissued bootstrap.");
			if (peer != null) { lastStackRace(customer, peer, sellers, receipt); }
			check(service.beginStop(), "Pending native work at STOP.");
			if (preparedMaker > 0 && Boolean.getBoolean("phantom.task068.merchant.stopCraftProbe"))
			{
				stoppedManufacture(customer, World.getInstance().getPlayer(preparedMaker), receipt);
			}
			OfflineTraderTable.getInstance().storeOffliners();
			receipt.append("identities\t").append(service.characterIds()).append('\n');
			receipt.append("production\t").append(Player.class.getProtectionDomain().getCodeSource().getLocation()).append('\n');
			System.out.println("OWN068_MERCHANT_PASS " + service.snapshot());
		}
		finally
		{
			try
			{
				if (service != null) { service.beginStop(); for (int id : service.characterIds()) { final var owner = World.getInstance().getPlayer(id); if (owner != null) { owner.stopAllTasks(); owner.storeMe(); owner.deleteMe(); } } service.releaseDeletedOwners(); }
				if (customer != null && World.getInstance().getPlayer(customer.getObjectId()) == customer) { customer.stopAllTasks(); customer.storeMe(); customer.deleteMe(); }
				if (peer != null && World.getInstance().getPlayer(peer.getObjectId()) == peer) { peer.stopAllTasks(); peer.storeMe(); peer.deleteMe(); }
				Files.createDirectories(reports); Files.writeString(reports.resolve("merchant-" + target + "-" + System.currentTimeMillis() + ".tsv"), receipt, StandardCharsets.UTF_8);
			}
			finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
	}

	private static void trade(Player customer, Player seller, Player buyer, StringBuilder receipt)
	{
		placeCustomer(customer, seller);
		final var sale = seller.getSellList().getItems().iterator().next();
		final long customerBefore = customer.getAdena(), sellerBefore = seller.getAdena(), itemsBefore = customer.getInventory().getInventoryItemCount(sale.getItem().getId(), -1);
		check(PrivateStoreService.getInstance().buy(customer, seller.getObjectId(), Set.of(new RequestTrade(sale.getObjectId(), sale.getItem().getId(), 1, sale.getPrice()))) == PrivateStoreService.Result.COMMITTED, "Native SELL transaction failed.");
		check(customer.getAdena() == customerBefore - sale.getPrice() && seller.getAdena() == sellerBefore + sale.getPrice() && customer.getInventory().getInventoryItemCount(sale.getItem().getId(), -1) == itemsBefore + 1, "Native SELL conservation differs.");
		check(World.getInstance().getPlayer(seller.getObjectId()) == seller && PhantomOfflineMerchantService.ownsNative(seller), "Native depleted seller lost owner.");
		receipt.append("SELL\titem=").append(sale.getItem().getId()).append(";count=1;price=").append(sale.getPrice()).append(";conserved=true\n");
		customer.setXYZ(buyer.getX() + 20, buyer.getY(), buyer.getZ());
		final var bid = buyer.getBuyList().getItems().iterator().next();
		final var fixtureItem = customer.getInventory().addItem(ItemProcessType.REWARD, bid.getItem().getId(), 1, customer, "TEST_PREMEASUREMENT_STOCK");
		final long buyBefore = buyer.getAdena(), fundsBefore = customer.getAdena(), inventoryBefore = buyer.getInventory().getInventoryItemCount(bid.getItem().getId(), -1);
		check(PrivateStoreService.getInstance().sell(customer, buyer.getObjectId(), new RequestTrade[] {new RequestTrade(fixtureItem.getObjectId(), fixtureItem.getId(), 1, bid.getPrice())}) == PrivateStoreService.Result.COMMITTED, "Native BUY transaction failed.");
		check(buyer.getAdena() == buyBefore - bid.getPrice() && customer.getAdena() == fundsBefore + bid.getPrice() && buyer.getInventory().getInventoryItemCount(bid.getItem().getId(), -1) == inventoryBefore + 1, "Native BUY conservation differs.");
		customer.storeMe();
		receipt.append("BUY\titem=").append(bid.getItem().getId()).append(";count=1;price=").append(bid.getPrice()).append(";conserved=true;customerSource=TEST_PREMEASUREMENT\n");
	}
	private static Player customer() throws Exception
	{
		return customer("Market068Buyer");
	}
	private static Player customer(String name) throws Exception
	{
		Integer id = null;
		try (var connection = DatabaseFactory.getConnection())
		{
			try (var insert = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task068market','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
			try (var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task068market' AND char_name=? AND accesslevel=0")) { query.setString(1, name); try (var rows = query.executeQuery()) { if (rows.next()) { id = rows.getInt(1); check(!rows.next(), "Ambiguous market customer."); } } }
		}
		final var player = id == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task068market", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(id);
		check(player != null && !player.isGM() && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Ordinary access0 customer required.");
		player.stopAllTasks(); player.setOnlineStatus(true, false);
		// A reused scene customer can have persisted creation HP=0. Native crafting rejects a corpse
		// before it reads the service fee; prepare this customer before any measured transaction.
		if (player.isAlikeDead()) { System.out.println("OWN068_CUSTOMER_PREMEASUREMENT nativeRevive=" + player.getObjectId()); player.doRevive(); }
		player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		if (player.getAdena() < 100000000) { player.addAdena(ItemProcessType.REWARD, 100000000 - player.getAdena(), player, false); }
		return player;
	}
	private static void lastStackRace(Player first, Player second, List<Player> sellers, StringBuilder receipt) throws Exception
	{
		final var owner = sellers.stream().filter(player -> player.getSellList().getItemCount() > 1 && player.getSellList().getItems().stream().anyMatch(line -> line.getCount() > 1 && line.getPrice() < 1000)).findFirst().orElseThrow();
		final var line = owner.getSellList().getItems().stream().filter(item -> item.getCount() > 1 && item.getPrice() < 1000).findFirst().orElseThrow();
		first.setXYZ(owner.getX() + 20, owner.getY(), owner.getZ()); second.spawnMe(owner.getX() - 20, owner.getY(), owner.getZ());
		final long count = line.getCount(), price = line.getPrice(); final int itemId = line.getItem().getId(), objectId = line.getObjectId();
		final long funds = first.getAdena() + second.getAdena() + owner.getAdena();
		final long items = first.getInventory().getInventoryItemCount(itemId, -1) + second.getInventory().getInventoryItemCount(itemId, -1) + owner.getInventory().getInventoryItemCount(itemId, -1);
		final var ready = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2))
		{
			final var results = new java.util.ArrayList<java.util.concurrent.Future<PrivateStoreService.Result>>();
			for (var buyer : List.of(first, second)) { results.add(executor.submit(() -> { ready.await(); return PrivateStoreService.getInstance().buy(buyer, owner.getObjectId(), Set.of(new RequestTrade(objectId, itemId, count, price))); })); }
			ready.countDown(); int committed = 0;
			for (var result : results) { if (result.get(10, java.util.concurrent.TimeUnit.SECONDS) == PrivateStoreService.Result.COMMITTED) { committed++; } }
			check(committed == 1, "Two buyers duplicated the last stack or neither committed.");
		}
		check(funds == first.getAdena() + second.getAdena() + owner.getAdena() && items == first.getInventory().getInventoryItemCount(itemId, -1) + second.getInventory().getInventoryItemCount(itemId, -1) + owner.getInventory().getInventoryItemCount(itemId, -1), "Last-stack native race violated conservation.");
		receipt.append("LAST_STACK_RACE\titem=").append(itemId).append(";count=").append(count).append(";buyers=2;committed=1;conserved=true\n");
		// The ordinary null-client/offline close still uses stock Disconnection and its own exact row.
		final var ordinary = first;
		check(!PhantomOfflineMerchantService.ownsNative(ordinary), "Ordinary customer acquired merchant lifecycle exemption.");
		final var controlItem = ordinary.getInventory().getItems().stream().filter(item -> item.getId() != 57 && item.isTradeable() && item.getCount() > 0).findFirst().orElseThrow();
		check(ordinary.getSellList().addItem(controlItem.getObjectId(), 1, 1) != null, "Ordinary control listing failed.");
		ordinary.setOfflineStartTime(System.currentTimeMillis()); ordinary.setPrivateStoreType(PrivateStoreType.SELL); ordinary.setOnlineStatus(true, true);
		OfflineTraderTable.getInstance().onTransaction(ordinary, false, true); World.OFFLINE_TRADE_COUNT++;
		final var pairCalls = new java.util.concurrent.atomic.AtomicInteger();
		final PrivateStoreService.Observer pairObserver = new PrivateStoreService.Observer()
		{
			@Override public boolean beforeMutation(PrivateStoreService.Direction direction, Player actor, Player ownerPlayer, org.l2jmobius.gameserver.network.holders.TradeList list, String hash) { pairCalls.incrementAndGet(); return true; }
			@Override public void afterMutation(PrivateStoreService.Direction direction, Player actor, Player ownerPlayer, org.l2jmobius.gameserver.network.holders.TradeList list, String before, String after, boolean success) { pairCalls.incrementAndGet(); }
		};
		final PrivateStoreService.OwnerObserver denyOwner = new PrivateStoreService.OwnerObserver()
		{
			@Override public boolean beforeTransaction(Player ownerPlayer, org.l2jmobius.gameserver.network.holders.TradeList list) { return false; }
			@Override public void afterTransaction(Player ownerPlayer, org.l2jmobius.gameserver.network.holders.TradeList list, boolean success) { throw new AssertionError("Pre-effect rejection completed an unstarted transaction."); }
		};
		final long controlFunds = ordinary.getAdena() + second.getAdena(), controlCount = controlItem.getCount();
		try (var ownerGate = PrivateStoreService.getInstance().observeOwner(ordinary.getObjectId(), denyOwner);
			var pairGate = PrivateStoreService.getInstance().observe(PrivateStoreService.Direction.BUY_FROM_SELL_STORE, second.getObjectId(), ordinary.getObjectId(), pairObserver))
		{
			check(PrivateStoreService.getInstance().buy(second, ordinary.getObjectId(), Set.of(new RequestTrade(controlItem.getObjectId(), controlItem.getId(), 1, 1))) == PrivateStoreService.Result.REJECTED, "Owner gate did not reject before native mutation.");
			check(pairCalls.get() == 0 && controlFunds == ordinary.getAdena() + second.getAdena() && controlCount == controlItem.getCount(), "Owner rejection began durable observation or changed funds/items.");
		}
		receipt.append("OWNER_ADMISSION_ORDER\tpre-effect reject;pairCallbacks=0;funds/items unchanged\n");
		ordinary.setPrivateStoreType(PrivateStoreType.NONE);
		final long deadline = System.currentTimeMillis() + 5000;
		while (World.getInstance().getPlayer(ordinary.getObjectId()) == ordinary && System.currentTimeMillis() < deadline) { Thread.sleep(50); }
		check(World.getInstance().getPlayer(ordinary.getObjectId()) == null, "Ordinary offline close no longer disconnects.");
		receipt.append("ORDINARY_CONTROL\tstock close removed ordinary owner; merchant exemption=false\n");
	}
	private static long statements() throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW GLOBAL STATUS LIKE 'Questions'")) { check(rows.next(), "SQL status unavailable."); return rows.getLong(2); }
	}
	private static int prepareManufacturer(StringBuilder receipt) throws Exception
	{
		final int id;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task068craft' AND char_name='D068Crafter' AND accesslevel=0"))
		{
			try (var rows = query.executeQuery()) { check(rows.next(), "Existing declared D native crafter required."); id = rows.getInt(1); check(!rows.next(), "Ambiguous D crafter."); }
		}
		var maker = World.getInstance().getPlayer(id);
		if (maker != null) { check(nonemptyManufacturer(maker), "Restored prepared manufacturer differs."); return id; }
		maker = Player.load(id); check(maker != null && !maker.isAlikeDead(), "Prepared crafter must already be alive.");
		maker.stopAllTasks(); maker.setOnlineStatus(true, false);
		final var recipe = maker.getDwarvenRecipeBook().stream().filter(value -> value.getSuccessRate() == 100).min(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.item.recipe.RecipeList::getId)).orElseThrow();
		final int skillId = org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId();
		if (maker.getSkillLevel(skillId) < recipe.getLevel())
		{
			final int level = maker.getLevel();
			final var learn = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(maker.getPlayerClass()).values().stream().filter(value -> value.getSkillId() == skillId && value.getSkillLevel() >= recipe.getLevel() && value.getGetLevel() <= level).min(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.skill.holders.SkillLearn::getSkillLevel)).orElseThrow();
			maker.addSkill(org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(skillId, learn.getSkillLevel()), true);
			receipt.append("PREMEASUREMENT\tpersist existing D fixture native craft skill; no levels/stock/capital added\n");
		}
		final int z = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(maker.getX(), maker.getY(), maker.getZ());
		maker.spawnMe(maker.getX(), maker.getY(), z);
		check(maker.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && maker.canOpenPrivateStore(), "Prepared maker lacks a native town store slot.");
		maker.getManufactureItems().clear(); maker.getManufactureItems().put(recipe.getId(), new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(recipe.getId(), 100));
		maker.setStoreName("Craft service"); maker.setPrivateStoreType(PrivateStoreType.MANUFACTURE); maker.sitDown(); maker.setOfflineStartTime(System.currentTimeMillis()); maker.storeMe();
		OfflineTraderTable.getInstance().onTransaction(maker, false, true); World.OFFLINE_TRADE_COUNT++;
		receipt.append("PREMEASUREMENT\texisting paid D fixture manufacturer offline;id=").append(id).append(";nativeRecipe=").append(recipe.getId()).append(";no bootstrap\n");
		return id;
	}
	private static boolean nonemptyManufacturer(Player player) { return PhantomOfflineMerchantService.nonempty(player) && player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE; }
	private static void manufacture(Player customer, Player maker, StringBuilder receipt, PhantomOfflineMerchantService service) throws Exception
	{
		final var line = maker.getManufactureItems().values().iterator().next();
		final var recipe = org.l2jmobius.gameserver.data.xml.RecipeData.getInstance().getRecipeList(line.getRecipeId());
		// Customer-only finite scene inputs. The maker receives no subsidy when adopted/restored.
		final int batches = Boolean.getBoolean("phantom.task068.merchant.stopCraftProbe") ? 2 : 1;
		for (var input : recipe.getRecipes()) { final long deficit = Math.max(0, Math.multiplyExact(input.getQuantity(), batches) - customer.getInventory().getInventoryItemCount(input.getItemId(), -1)); if (deficit > 0) { customer.getInventory().addItem(ItemProcessType.REWARD, input.getItemId(), deficit, customer, "TEST_PREMEASUREMENT_CRAFT_INPUT"); } }
		placeCustomer(customer, maker);
		System.out.println("OWN068_CRAFT_PREIMAGE makerAlive=" + !maker.isAlikeDead() + ";customerAlive=" + !customer.isAlikeDead() + ";makerTransaction=" + maker.isProcessingTransaction() + ";customerTransaction=" + customer.isProcessingTransaction() + ";skill=" + maker.getSkillLevel((recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON).getId()) + ";fee=" + line.getCost());
		final long makerMoney = maker.getAdena(), customerMoney = customer.getAdena(), products = customer.getInventory().getInventoryItemCount(recipe.getItemId(), -1);
		final var events = new java.util.concurrent.CopyOnWriteArrayList<org.l2jmobius.gameserver.managers.RecipeCraftObserver.Event>();
		check(org.l2jmobius.gameserver.services.ManufactureService.getInstance().manufacture(customer, maker.getObjectId(), recipe.getId(), events::add) == org.l2jmobius.gameserver.services.ManufactureService.Result.STARTED, "Native manufacture service rejected its valid prepared listing.");
		if (Boolean.getBoolean("phantom.task068.merchant.asyncPendingProbe"))
		{
			check(org.l2jmobius.gameserver.managers.RecipeManager.getInstance().isMakingItem(maker), "Queued native ALT craft is invisible to its own manager.");
			check(!service.beginStop(), "STOP failed to retain pending native ALT craft.");
			receipt.append("MANUFACTURE_PENDING\tnative ALT accepted; manager pending; STOP retained owner\n");
		}
		final long deadline = System.currentTimeMillis() + 10000;
		while (events.stream().noneMatch(event -> event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.SUCCESS_PRODUCT || event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.ABORTED) && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
		check(events.stream().anyMatch(event -> event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.SUCCESS_PRODUCT) && maker.getAdena() == makerMoney + line.getCost() && customer.getAdena() == customerMoney - line.getCost() && customer.getInventory().getInventoryItemCount(recipe.getItemId(), -1) == products + recipe.getCount(), "Adopted native manufacture conservation failed: " + events);
		maker.storeMe(); customer.storeMe();
		receipt.append("ADOPTED_MANUFACTURE\t").append(events).append(";makerBootstrap=0;conserved=true\n");
	}
	private static void stoppedManufacture(Player customer, Player maker, StringBuilder receipt)
	{
		final int recipeId = maker.getManufactureItems().values().iterator().next().getRecipeId();
		placeCustomer(customer, maker);
		final var events = new java.util.concurrent.CopyOnWriteArrayList<org.l2jmobius.gameserver.managers.RecipeCraftObserver.Event>();
		check(org.l2jmobius.gameserver.services.ManufactureService.getInstance().manufacture(customer, maker.getObjectId(), recipeId, events::add) == org.l2jmobius.gameserver.services.ManufactureService.Result.REJECTED_BEFORE_EFFECT && events.isEmpty(), "STOP accepted a new native manufacture: " + events);
		receipt.append("MANUFACTURE_STOP\trejected before effect;events=0\n");
	}
	private static void placeCustomer(Player customer, Player owner)
	{
		if (customer.isSpawned()) { customer.setXYZ(owner.getX() + 20, owner.getY(), owner.getZ()); }
		else { customer.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ()); }
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
