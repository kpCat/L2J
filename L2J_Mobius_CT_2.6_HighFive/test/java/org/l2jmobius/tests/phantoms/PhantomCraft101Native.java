/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
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

/** One OWN101 campaign, two sameDB/sameJAR generations. No generic fixture reset. */
public final class PhantomCraft101Native
{
    private static Path _receipts;
    private static org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService _ordinary;
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1 || !Set.of("1", "1-replay", "2", "2-replay", "3").contains(args[0]) || !PhantomTask101Lane.enabled()) { throw new IllegalArgumentException("OWN101 exact authorized generation required."); }
        final int generation = args[0].equals("1-replay") ? 1 : args[0].equals("2-replay") ? 2 : Integer.parseInt(args[0]); final String label = args[0];
        final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
        PhantomTask101Lane.validate(module, Path.of(System.getProperty("phantom.test.config")));
        _receipts = PhantomTask101Lane.stage().resolve("private/receipts"); Files.createDirectories(_receipts);
        if (label.equals("1-replay")) { check(Files.isRegularFile(PhantomTask101Lane.stage().resolve("private/repair.completed")) && Files.isRegularFile(_receipts.resolve("start1.tsv")) && !Files.exists(_receipts.resolve("ids1.txt")) && !Files.exists(_receipts.resolve("cycle.properties")), "Causal replay requires preserved RED before grants/trade, not a successful generation."); }
        Files.writeString(_receipts.resolve("start" + label + ".begun"), java.time.Instant.now().toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        final var receipt = new StringBuilder("stage\tvalue\n");
        PhantomOfflineMerchantService service = null; Player customer = null;
        boolean drained = false;
        try
        {
            PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(10110101L, module, _receipts));
            InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
            org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().loadInstances();
            org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().updateReferences();
            SpawnData.getInstance(); DoorData.getInstance(); OfflineTraderTable.getInstance().restoreOfflineTraders();
            final var backend = new L2jTopologyValidationBackend();
            final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), backend, PhantomTopologyPolicy.productionDefaults()).load(1);
            final var topology = new PhantomTopologyQuery(topologySnapshot, backend, new PhantomTopologyMetrics());
            final var kp = PhantomGameKnowledgePolicy.productionDefaults(); final var kb = new L2jGameKnowledgeBackend();
            final var knowledge = new PhantomGameKnowledgeBuilder(kb, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), kp), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), kb, kp), topology, kp).build();
            final var commerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
            final var pricing = new PhantomPrivateMarketPricingAuthority(knowledge, commerce, Rates.capture(), new Policy(500, 500), () -> World.getInstance().getVisibleObjects());
            final var catalog = new PhantomMerchantCatalog(pricing); final var policy = PhantomMerchantPolicy.load(Path.of(PhantomMerchantPolicy.FILE));
            check(policy != null && policy.targetStores() == 200 && policy.preparedOfflineIds().isEmpty(), "OWN101 exact200 without adopted makers required.");
            final var arrivals = topologySnapshot.anchors().stream().filter(anchor -> anchor.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.GATEKEEPER).map(anchor -> new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(anchor.point().x(), anchor.point().y(), anchor.point().z(), anchor.point().instanceId())).toList();
            service = new PhantomOfflineMerchantService(PhantomProfileRepository.open(), policy, () -> pricing, arrivals); service.start();
            if (generation == 2)
            {
                final var prior = PhantomTask101Lane.read(_receipts.resolve("continuation2.properties"));
                final var stack = PhantomTask101Lane.read(_receipts.resolve("stack-merge-proof.properties"));
                check("RECOVERED_PARTIAL_START1".equals(prior.getProperty("source")) && prior.getProperty("jarSha256").equals(PhantomTask101Lane.hash(module.resolve("dist/libs/GameServer.jar"))), "Continuation changed current JAR/provenance.");
                check("EXACT_OLD_SHA_RECONSTRUCTED".equals(stack.getProperty("source")) && stack.getProperty("oldRawHash").equals(prior.getProperty("durableHash")) && stack.getProperty("jarSha256").equals(prior.getProperty("jarSha256")), "Native stack normalization lacks exact original-byte proof.");
                check(prior.getProperty("ids").equals(service.characterIds().toString()) && stack.getProperty("canonicalHash").equals(durableHash()), "Restart changed identities/ledger/physical quantities/qualification.");
                receipt.append("RECOVERED_PAID_CRAFT\t").append(prior.getProperty("paidCraft")).append(";originalSTART1=PARTIAL;facts=SQL_AND_ORIGINAL_NATIVE_EVENTS\n");
            }
            if (generation == 3)
            {
                check(Files.readString(_receipts.resolve("ids2.txt")).equals(service.characterIds().toString()) && Files.readString(_receipts.resolve("durable2.txt")).equals(durableHash()), "BUY cutpoint restart changed identities/finite ledger/inventory/qualification.");
            }
            final long deadline = System.currentTimeMillis() + 100000;
            while (service.snapshot().nonemptyStores() < 200 && System.currentTimeMillis() < deadline)
            {
                service.onPulse(); Thread.sleep(1000);
                if (service.snapshot().blocked() > 0) { break; }
            }
            census(service, policy, catalog, pricing, receipt);
            check(service.snapshot().nonemptyStores() == 200 && service.snapshot().nativeOwners() == 200 && service.snapshot().identityLeases() == 200 && service.snapshot().combatOrMaterializationOwners() == 0, "Actual native200 gate failed: " + service.snapshot());
            final var before = service.snapshot();
            customer = generation == 1 ? customer() : resumeCustomer();
            final var maker = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(player -> player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE).findFirst().orElseThrow();
            ordinaryRecipeFixture(generation, maker, pricing, receipt);
            if (generation == 1) { negatives(customer, maker, service, pricing, commerce, receipt); manufacture(customer, maker, service, receipt); startCirculation(customer, service, receipt); }
            else if (generation == 2) { startCirculation(customer, service, receipt); }
            else { finishCirculation(customer, service, receipt); }
            census(service, policy, catalog, pricing, receipt);
            check(service.snapshot().nonemptyStores() == 200 && before.issuedCapital() == service.snapshot().issuedCapital() && before.issuedStockValue() == service.snapshot().issuedStockValue(), "Native200/finite bootstrap changed.");
            check(service.beginStop(), "Pending native craft at STOP; no forced cleanup."); drained = true;
            final var events = new java.util.concurrent.CopyOnWriteArrayList<org.l2jmobius.gameserver.managers.RecipeCraftObserver.Event>();
            place(customer, maker);
            check(org.l2jmobius.gameserver.services.ManufactureService.getInstance().manufacture(customer, maker.getObjectId(), maker.getManufactureItems().values().iterator().next().getRecipeId(), events::add) == org.l2jmobius.gameserver.services.ManufactureService.Result.REJECTED_BEFORE_EFFECT && events.isEmpty(), "STOP accepted native manufacture.");
            OfflineTraderTable.getInstance().storeOffliners();
            check(scalar("SELECT COUNT(*) FROM character_offline_trade") == 200, "Persisted store owners !=200.");
            customer.getInventory().updateDatabase(); customer.storeMe();
            if (generation <= 2)
            {
                Files.writeString(_receipts.resolve("ids" + generation + ".txt"), service.characterIds().toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                Files.writeString(_receipts.resolve("durable" + generation + ".txt"), durableHash(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            receipt.append("final\t").append(service.snapshot()).append(";C=NATIVE_NATURAL_PENDING;D=NOT_RUN\n");
            System.out.println("OWN101_NATIVE_PASS generation=" + generation + ";" + service.snapshot());
        }
        finally
        {
            try
            {
                if (_ordinary != null) { _ordinary.shutdown(); check(_ordinary.shutdownSnapshot().state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED, "Ordinary fixture retained native owner."); }
                if (service != null && !drained) { drained = service.beginStop(); }
                if (service != null && drained)
                {
                    for (int id : service.characterIds())
                    {
                        final var owner = World.getInstance().getPlayer(id); if (owner == null) { continue; }
                        owner.stopAllTasks(); owner.storeMe(); owner.deleteMe(); check(World.getInstance().getPlayer(id) == null, "Native cleanup retained owner.");
                    }
                    service.releaseDeletedOwners(); check(service.snapshot().identityLeases() == 0, "STOP leaked leases.");
                }
                if (customer != null) { customer.stopAllTasks(); customer.storeMe(); if (World.getInstance().getPlayer(customer.getObjectId()) == customer) { customer.deleteMe(); } }
                receipt.append("cleanup\tdrained=").append(drained).append(";remainingOwners=").append(service == null ? 0 : service.snapshot().nativeOwners()).append('\n');
                Files.writeString(_receipts.resolve("start" + label + ".tsv"), receipt, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
        }
    }

    private static void census(PhantomOfflineMerchantService service, PhantomMerchantPolicy policy, PhantomMerchantCatalog catalog, PhantomPrivateMarketPricingAuthority pricing, StringBuilder receipt) throws Exception
    {
        final var types = new java.util.TreeMap<String, Integer>(); final var ids = new java.util.TreeSet<Integer>();
        final var owners = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).toList();
        check(Set.copyOf(service.characterIds()).size() == service.characterIds().size(), "Duplicate native IDs.");
        for (var player : owners)
        {
            check(player != null && PhantomOfflineMerchantService.ownsNative(player) && player.getClient() == null && player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && player.getInstanceId() == 0, "Native owner/peace zone differs.");
            check(service.admissionSlots().stream().anyMatch(slot -> slot.x() == player.getX() && slot.y() == player.getY() && slot.z() == player.getZ()), "Actual owner outside immutable source admission set.");
            check(org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().hasGeo(player.getX(), player.getY()), "Actual store lacks Geo.");
            for (var other : owners) { if (other != player) { check(player.calculateDistance3D(other) >= 60, "Store overlap."); } }
            check(PhantomOfflineMerchantService.nonempty(player), "Empty native store."); types.merge(player.getPrivateStoreType().name(), 1, Integer::sum);
            if (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE)
            {
                check(org.l2jmobius.gameserver.phantoms.progression.PhantomCraftRecipePolicy.creator(player.getActiveClass()), "Manufacturer lacks creator class.");
                for (var line : player.getManufactureItems().values())
                {
                    final var recipe = RecipeData.getInstance().getRecipeList(line.getRecipeId());
                    check(recipe != null && player.hasRecipeList(recipe.getId()) && player.getSkillLevel(org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId()) >= recipe.getLevel() && line.getCost() == pricing.manufactureFee(recipe.getId(), recipe.getItemId()), "Native MFG recipe/rank/fee differs."); ids.add(recipe.getItemId());
                }
            }
            else
            {
                final var list = player.getPrivateStoreType() == PrivateStoreType.BUY ? player.getBuyList() : player.getSellList(); long budget = 0;
                for (var line : list.getItems())
                {
                    final var quote = pricing.quoteItem(line.getItem().getId()); check(quote.priced() && line.getCount() > 0, "Unpriced or empty native stock."); ids.add(line.getItem().getId());
                    if (player.getPrivateStoreType() == PrivateStoreType.BUY) { check(quote.bidAllowed() && line.getPrice() == quote.bid(), "Native BUY price drift."); budget += Math.multiplyExact(line.getCount(), line.getPrice()); }
                    else { check(line.getPrice() == quote.ask() && count(player, line.getItem().getId()) >= line.getCount(), "Native SELL price/stock drift."); }
                }
                check(budget <= player.getAdena(), "Native BUY exceeds funds.");
            }
        }
        check(types.getOrDefault("MANUFACTURE", 0) >= 1, "No native manufacturers.");
        check(org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().isEmpty(), "Human observer suppresses native census.");
        receipt.append("census\t").append(service.snapshot()).append(";types=").append(types).append(";uniqueItemIds=").append(ids.size()).append(";catalog=").append(catalog.entries().size()).append(";NPCs=").append(World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof org.l2jmobius.gameserver.model.actor.Npc).count()).append(";layout=IMMUTABLE_ADMISSION_PLUS_CURRENT_GEO_PEACE_SPACING;mutableSnapshotEquality=NOT_REQUIRED\n");
    }

    private static Player customer() throws Exception
    {
        try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('fixture101','DISABLED_FIXTURE_CUSTOMER',-1,1)")) { query.executeUpdate(); }
        final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "fixture101", "Customer101", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
        check(player != null && player.getClient() == null && !player.isGM(), "OWN101 fixture customer invalid.");
        player.stopAllTasks(); player.setOnlineStatus(true, false); if (player.isAlikeDead()) { player.doRevive(); }
        player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.addAdena(ItemProcessType.REWARD, 100000000, player, false); return player;
    }
    private static Player resumeCustomer() throws Exception
    {
        final int id;
        try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='fixture101' AND char_name='Customer101' AND accesslevel=0"))
        { try (var rows = query.executeQuery()) { check(rows.next(), "OWN101 original customer missing."); id = rows.getInt(1); check(!rows.next(), "Ambiguous OWN101 customer."); } }
        final var player = Player.load(id); check(player != null && !player.isAlikeDead() && player.getClient() == null, "OWN101 customer restore differs; no grant."); player.stopAllTasks(); player.setOnlineStatus(true, false); return player;
    }
    private static void place(Player customer, Player owner)
    { if (World.getInstance().getPlayer(customer.getObjectId()) == customer) { customer.setXYZ(owner.getX() + 20, owner.getY(), owner.getZ()); } else { customer.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ()); } customer.revalidateZone(true); }

    private static void manufacture(Player customer, Player maker, PhantomOfflineMerchantService service, StringBuilder receipt) throws Exception
    {
        final var line = maker.getManufactureItems().values().iterator().next(); final var recipe = RecipeData.getInstance().getRecipeList(line.getRecipeId());
        check(recipe.getSuccessRate() == 100, "Native paid witness requires100% source recipe.");
        for (var input : recipe.getRecipes()) { customer.getInventory().addItem(ItemProcessType.REWARD, input.getItemId(), input.getQuantity(), customer, "CUSTOMER_FIXTURE_BOM101"); }
        final var bom = new java.util.TreeMap<Integer, Long>(); for (var input : recipe.getRecipes()) { bom.put(input.getItemId(), count(customer, input.getItemId())); }
        place(customer, maker); final long makerFunds = maker.getAdena(), customerFunds = customer.getAdena(), products = count(customer, recipe.getItemId()); final double mp = maker.getCurrentMp();
        final var events = new java.util.concurrent.CopyOnWriteArrayList<org.l2jmobius.gameserver.managers.RecipeCraftObserver.Event>();
        check(org.l2jmobius.gameserver.services.ManufactureService.getInstance().manufacture(customer, maker.getObjectId(), recipe.getId(), events::add) == org.l2jmobius.gameserver.services.ManufactureService.Result.STARTED, "Native manufacture rejected qualified owner.");
        final long deadline = System.currentTimeMillis() + 30000;
        while (events.stream().noneMatch(event -> event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.SUCCESS_PRODUCT || event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.RARE_PRODUCT || event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.ABORTED) && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
        final var output = events.stream().filter(event -> event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.SUCCESS_PRODUCT || event.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.RARE_PRODUCT).findFirst().orElseThrow();
        final long expectedProducts = output.type() == org.l2jmobius.gameserver.managers.RecipeCraftObserver.Type.RARE_PRODUCT ? output.authority().rareProductCount() : output.authority().productCount();
        check(output.items().size() == 1 && output.items().getFirst().itemId() == recipe.getItemId() && output.items().getFirst().count() == expectedProducts && maker.getAdena() == makerFunds + line.getCost() && customer.getAdena() == customerFunds - line.getCost() && count(customer, recipe.getItemId()) == products + expectedProducts, "Native paid MFG product/fee conservation failed: " + events);
        for (var input : recipe.getRecipes()) { check(count(customer, input.getItemId()) == bom.get(input.getItemId()) - input.getQuantity(), "Native BOM conservation failed."); }
        receipt.append("PAID_NATIVE_CRAFT\towner=").append(maker.getObjectId()).append(";class=").append(maker.getActiveClass()).append(";level=").append(maker.getLevel()).append(";recipe=").append(recipe.getId()).append(";BOM=").append(bom).append(";fee=").append(line.getCost()).append(";MP=").append(mp).append("->").append(maker.getCurrentMp()).append(";events=").append(events).append(";qualification=OWNER_PROVISIONED_CRAFT;inputs=CUSTOMER_FIXTURE\n");
    }

    private static void negatives(Player customer, Player maker, PhantomOfflineMerchantService service, PhantomPrivateMarketPricingAuthority pricing, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog commerce, StringBuilder receipt) throws Exception
    {
        check(!pricing.quoteItem(-1).priced(), "Unknown acquisition source priced.");
        place(customer, maker);
        final var recipe = RecipeData.getInstance().getRecipeList(maker.getManufactureItems().values().iterator().next().getRecipeId());
        final long totalMoney = customer.getAdena() + maker.getAdena(), products = count(customer, recipe.getItemId());
        final var events = new java.util.concurrent.CopyOnWriteArrayList<org.l2jmobius.gameserver.managers.RecipeCraftObserver.Event>();
        check(org.l2jmobius.gameserver.services.ManufactureService.getInstance().manufacture(customer, maker.getObjectId(), recipe.getId(), events::add) == org.l2jmobius.gameserver.services.ManufactureService.Result.REJECTED_BEFORE_EFFECT, "Native invalid BOM accepted.");
        check(customer.getAdena() + maker.getAdena() == totalMoney && count(customer, recipe.getItemId()) == products, "Invalid BOM changed money/product.");
        Player seller = null; org.l2jmobius.gameserver.network.holders.TradeItem sale = null; org.l2jmobius.gameserver.model.buylist.BuyListHolder list = null;
        for (int ownerId : service.characterIds())
        {
            final var candidate = World.getInstance().getPlayer(ownerId); if (candidate.getPrivateStoreType() != PrivateStoreType.SELL) { continue; }
            for (var line : candidate.getSellList().getItems())
            {
                if (line.getPrice() <= 1) { continue; }
                for (var offer : commerce.findBuyOffers(line.getItem().getId(), 0, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.MAX_PAGE_SIZE).values())
                {
                    final var buyList = org.l2jmobius.gameserver.data.xml.BuyListData.getInstance().getBuyList(offer.listId());
                    if (buyList != null && buyList.getProductByItemId(line.getItem().getId()) != null && World.getInstance().getVisibleObjects().stream().anyMatch(value -> value instanceof org.l2jmobius.gameserver.model.actor.instance.Merchant merchant && offer.npcIds().contains(merchant.getId()) && buyList.isNpcAllowed(merchant.getId()) && merchant.getMpc() != null)) { seller = candidate; sale = line; list = buyList; break; }
                }
                if (seller != null) { break; }
            }
            if (seller != null) { break; }
        }
        check(seller != null, "No source-backed native NPC price-negative fixture."); place(customer, seller);
        final int itemId = sale.getItem().getId(); final long price = sale.getPrice(), money = customer.getAdena() + seller.getAdena(), stock = count(customer, itemId) + count(seller, itemId);
        final var request = Set.of(new RequestTrade(sale.getObjectId(), itemId, 1, price));
        try { sale.setPrice(price - 1); check(PrivateStoreService.getInstance().buy(customer, seller.getObjectId(), Set.of(new RequestTrade(sale.getObjectId(), itemId, 1, price - 1))) == PrivateStoreService.Result.REJECTED, "Native listing price drift accepted."); }
        finally { sale.setPrice(price); }
        final var original = list.getProductByItemId(itemId); final boolean onlyGmFree = org.l2jmobius.gameserver.config.GeneralConfig.ONLY_GM_ITEMS_FREE;
        try
        {
            org.l2jmobius.gameserver.config.GeneralConfig.ONLY_GM_ITEMS_FREE = false;
            list.addProduct(new org.l2jmobius.gameserver.model.buylist.Product(list.getListId(), original.getItem(), 0, original.getRestockDelay(), original.getMaxCount()));
            check(!pricing.quoteItem(itemId).priced(), "Free ordinary NPC acquisition priced.");
            check(PrivateStoreService.getInstance().buy(customer, seller.getObjectId(), request) == PrivateStoreService.Result.REJECTED, "Free-source stale listing transacted.");
        }
        finally { list.addProduct(original); org.l2jmobius.gameserver.config.GeneralConfig.ONLY_GM_ITEMS_FREE = onlyGmFree; }
        check(pricing.quoteItem(itemId).priced() && money == customer.getAdena() + seller.getAdena() && stock == count(customer, itemId) + count(seller, itemId), "Negative source/price case changed goods or failed to restore original entry.");
        receipt.append("NATIVE_NEGATIVES\tunknownSource=DENIED;invalidBOM=DENIED;listingPriceDrift=DENIED;freeNPC=DENIED;item=").append(itemId).append(";originalNativeEntry=RESTORED;goodsCurrency=UNCHANGED\n");
    }

    private static void ordinaryRecipeFixture(int generation, Player maker, PhantomPrivateMarketPricingAuthority pricing, StringBuilder receipt) throws Exception
    {
        final var profiles = PhantomProfileRepository.open();
        final int id;
        if (generation == 1)
        {
            try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('recipe_fixture101','TEST_FIXTURE_PROMOTED_ORDINARY',-1,1)")) { query.executeUpdate(); }
            final var fixture = Player.create(PlayerTemplateData.getInstance().getTemplate(53), "recipe_fixture101", "Recipe101", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
            check(fixture != null, "OWN101 ordinary fixture creation failed."); id = fixture.getObjectId();
            // TEST ONLY: authorized single promoted ordinary. This cannot witness earned progression gate C.
            fixture.setPlayerClass(56); fixture.setPlayerClass(57); fixture.setPlayerClass(118); fixture.setBaseClass(118); fixture.setLearningClass(fixture.getPlayerClass());
            fixture.getStat().setLevel((byte) 82); fixture.setExp(fixture.getStat().getExpForLevel(82));
            for (var skill : List.of(org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN, org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON))
            {
                final int rank = org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.sourceRank(118, 82, skill.getId());
                fixture.addSkill(org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(skill.getId(), rank), true);
            }
            fixture.setXYZ(maker.getX() + 20, maker.getY() + 20, maker.getZ()); if (fixture.isAlikeDead()) { fixture.doRevive(); }
            fixture.getStatus().setCurrentHp(fixture.getMaxHp()); fixture.setCurrentMp(fixture.getMaxMp());
            Thread.sleep(150); fixture.stopAllTasks(); fixture.storeMe(); profiles.create(id);
        }
        else
        {
            try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='recipe_fixture101' AND char_name='Recipe101'"))
            { try (var rows = query.executeQuery()) { check(rows.next(), "Original ordinary fixture missing."); id = rows.getInt(1); check(!rows.next(), "Ambiguous ordinary fixture."); } }
        }
        final var profile = profiles.findByCharacterObjectId(id).orElseThrow();
        final var metrics = new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
        _ordinary = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService(profiles, org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(), metrics, new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
        check(_ordinary.start() && _ordinary.materialize(profile.profileId()).status() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, "OWN101 ordinary fixture materialization failed.");
        final var rewards = org.l2jmobius.gameserver.phantoms.progression.PhantomOrdinaryCraftRecipes.load(Path.of(org.l2jmobius.gameserver.phantoms.progression.PhantomOrdinaryCraftRecipes.FILE), profiles, _ordinary, pricing);
        try (var outerAction = _ordinary.tryAcquireAction(profile.profileId()).orElseThrow())
        {
            final var player = outerAction.player(); final long exp = player.getExp(), sp = player.getSp(); final int level = player.getLevel(), rank = player.getSkillLevel(org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId());
            check(rank == 10, "Source Maestro82 Create Item10 fixture missing.");
            rewards.catchUp(profile.profileId()); rewards.catchUp(profile.profileId());
            final var saved = profiles.findComponent(profile.profileId(), "ordinary.craft.recipe.v1.0.10").orElseThrow();
            final var proof = org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt.decode(saved);
            check(proof.state().equals("READY") && proof.source().equals("PROGRESSION_RECIPE_BONUS") && proof.recipes().size() == 2 && player.getDwarvenRecipeBook().size() == 2 && player.getExp() == exp && player.getSp() == sp && player.getLevel() == level && player.getSkillLevel(org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId()) == rank, "Ordinary receipt/replay/artificial grant regression.");
            receipt.append("ORDINARY_FIXTURE\tsource=TEST_FIXTURE_PROMOTED_ORDINARY;profile=").append(profile.profileId()).append(";class=118;level=82;rank=10;bonus=PROGRESSION_RECIPE_BONUS;recipes=").append(proof.recipes()).append(";READY=true;outerActionSelfDrain=ABSENT;replayGrants=0;earnedNatural=NOT_PROVEN\n");
        }
        _ordinary.shutdown(); check(_ordinary.shutdownSnapshot().state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState.STOPPED, "Ordinary fixture STOP failed."); _ordinary = null;
    }

    private static void startCirculation(Player customer, PhantomOfflineMerchantService service, StringBuilder receipt) throws Exception
    {
        final var owner = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(player -> player.getPrivateStoreType() == PrivateStoreType.BUY).findFirst().orElseThrow(); place(customer, owner);
        final var bid = owner.getBuyList().getItems().iterator().next(); final int itemId = bid.getItem().getId();
        check(count(owner, itemId) == 0, "BUY witness must start without stock.");
        final var source = customer.getInventory().addItem(ItemProcessType.REWARD, itemId, 2, customer, "CUSTOMER_FIXTURE101");
        final long ownerMoney = owner.getAdena(), customerMoney = customer.getAdena(), customerCount = count(customer, itemId);
        check(PrivateStoreService.getInstance().sell(customer, owner.getObjectId(), new RequestTrade[] {new RequestTrade(source.getObjectId(), itemId, 2, bid.getPrice())}) == PrivateStoreService.Result.COMMITTED, "Native BUY2 denied.");
        check(count(owner, itemId) == 2 && count(customer, itemId) == customerCount - 2 && owner.getAdena() == ownerMoney - 2 * bid.getPrice() && customer.getAdena() == customerMoney + 2 * bid.getPrice(), "Native BUY2 conservation failed.");
        final int actualObject = owner.getInventory().getItemByItemId(itemId).getObjectId();
        final var facts = new java.util.Properties(); facts.setProperty("owner", Integer.toString(owner.getObjectId())); facts.setProperty("item", Integer.toString(itemId)); facts.setProperty("object", Integer.toString(actualObject)); facts.setProperty("bid", Long.toString(bid.getPrice())); facts.setProperty("ownerInitialMoney", Long.toString(ownerMoney)); facts.setProperty("customerInitialMoney", Long.toString(customerMoney)); facts.setProperty("customerInitialCount", Long.toString(customerCount));
        try (var writer = Files.newBufferedWriter(_receipts.resolve("cycle.properties"), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)) { facts.store(writer, "OWN101 PAID_NATIVE_TRADE cutpoint after BUY2; no replay"); }
        receipt.append("BUY2_CUTPOINT\towner=").append(owner.getObjectId()).append(";item=").append(itemId).append(";customerObject=").append(source.getObjectId()).append(";merchantActualObject=").append(actualObject).append(";bid=").append(bid.getPrice()).append(";ownerAdena=").append(ownerMoney).append("->").append(owner.getAdena()).append(";customerAdena=").append(customerMoney).append("->").append(customer.getAdena()).append(";source=CUSTOMER_FIXTURE;mechanism=PAID_NATIVE_TRADE;restartBeforeReopen=true\n");
    }
    private static void finishCirculation(Player customer, PhantomOfflineMerchantService service, StringBuilder receipt) throws Exception
    {
        final var facts = PhantomTask101Lane.read(_receipts.resolve("cycle.properties"));
        final int ownerId = Integer.parseInt(facts.getProperty("owner")), itemId = Integer.parseInt(facts.getProperty("item")), objectId = Integer.parseInt(facts.getProperty("object"));
        final var owner = World.getInstance().getPlayer(ownerId); check(owner != null && count(owner, itemId) == 2 && owner.getInventory().getItemByObjectId(objectId) != null, "Restart lost actual purchased object."); place(customer, owner);
        waitType(owner, PrivateStoreType.SELL, service);
        final var line = owner.getSellList().getItems().stream().filter(value -> value.getObjectId() == objectId).findFirst().orElseThrow(); final long ask = line.getPrice(), bid = Long.parseLong(facts.getProperty("bid")); check(ask > bid && line.getCount() == 2 && owner.getBuyList().getItemCount() == 0, "Native BUY/SELL overlap or price spread differs.");
        final long money = owner.getAdena() + customer.getAdena(), items = count(customer, itemId) + count(owner, itemId);
        for (int quantity = 1; quantity >= 0; quantity--)
        {
            check(PrivateStoreService.getInstance().buy(customer, ownerId, Set.of(new RequestTrade(objectId, itemId, 1, ask))) == PrivateStoreService.Result.COMMITTED, "Paid native resale denied.");
            check(count(owner, itemId) == quantity && count(owner, itemId) + count(customer, itemId) == items && owner.getAdena() + customer.getAdena() == money, "Paid resale conservation failed.");
        }
        waitType(owner, PrivateStoreType.BUY, service);
        check(owner.getAdena() == Long.parseLong(facts.getProperty("ownerInitialMoney")) - 2 * bid + 2 * ask && customer.getAdena() == Long.parseLong(facts.getProperty("customerInitialMoney")) + 2 * bid - 2 * ask && count(customer, itemId) == Long.parseLong(facts.getProperty("customerInitialCount")), "Three-leg cumulative conservation failed.");
        receipt.append("CIRCULATION\towner=").append(ownerId).append(";item=").append(itemId).append(";object=").append(objectId).append(";qty=0->2->1->0;bid=").append(bid).append(";ask=").append(ask).append(";final=BUY;conserved=true;bootstrapReplay=false;sameDBcutpoint=true\n");
    }
    private static void waitType(Player owner, PrivateStoreType type, PhantomOfflineMerchantService service) throws Exception
    {
        // Shared queue: due200/batch5 needs up to40 pulses, in addition to the30s eligibility horizon.
        final long deadline = System.currentTimeMillis() + 100000;
        while (owner.getPrivateStoreType() != type && System.currentTimeMillis() < deadline) { service.onPulse(); Thread.sleep(1000); }
        check(owner.getPrivateStoreType() == type, "Causal shared rotation failed to reach " + type + ": " + service.snapshot());
    }
    private static String durableHash() throws Exception
    {
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (String sql : new String[] {"SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type", "SELECT owner_id,item_id,SUM(count),loc FROM items WHERE owner_id IN(SELECT charId FROM characters WHERE account_name LIKE 'phm_%') GROUP BY owner_id,item_id,loc ORDER BY owner_id,item_id,loc"})
        { try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery(sql)) { while (rows.next()) { for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { final String value = rows.getString(i); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } } }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static long scalar(String sql) throws Exception { try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery(sql)) { check(rows.next(), "Missing scalar."); return rows.getLong(1); } }
    private static long count(Player player, int item) { return player.getInventory().getInventoryItemCount(item, -1); }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
