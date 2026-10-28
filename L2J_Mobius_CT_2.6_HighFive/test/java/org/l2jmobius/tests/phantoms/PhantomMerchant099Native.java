/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
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

/** One guarded OWN099 campaign, two JVM generations. No merchant fixture grant or historical lane. */
public final class PhantomMerchant099Native
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1 || !Set.of("1", "1-replay", "2").contains(args[0]) || !PhantomTask099Lane.enabled()) { throw new IllegalArgumentException("OWN099 generation 1/2 or one causal 1-replay required."); }
        final boolean replay = args[0].equals("1-replay");
        final int generation = replay ? 1 : Integer.parseInt(args[0]); final String start = args[0];
        final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
        PhantomTask099Lane.validate(module, Path.of(System.getProperty("phantom.test.config")));
        final Path receipts = PhantomTask099Lane.stage().resolve("private/receipts"); Files.createDirectories(receipts);
        if (replay)
        {
            check(Files.isRegularFile(receipts.resolve("start1.tsv")) && Files.isRegularFile(PhantomTask099Lane.stage().resolve("private/preimage/PhantomOfflineMerchantService.java")) && Files.isRegularFile(PhantomTask099Lane.stage().resolve("private/preimage/GameServer-red.jar")), "Causal replay requires preserved original RED receipt/source/JAR.");
            check(!Files.exists(receipts.resolve("ids1.txt")), "Successful START1 cannot be replayed.");
        }
        final Path begun = receipts.resolve("start" + start + ".begun");
        Files.writeString(begun, java.time.Instant.now().toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        final StringBuilder receipt = new StringBuilder("stage\tvalue\n");
        PhantomOfflineMerchantService service = null; Player customer = null, peer = null;
        boolean drained = false; String persisted = null;
        try
        {
            PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(99009901L, module, receipts));
            InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
            org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().loadInstances();
            org.l2jmobius.gameserver.data.MerchantPriceConfigTable.getInstance().updateReferences();
            SpawnData.getInstance(); DoorData.getInstance();
            OfflineTraderTable.getInstance().restoreOfflineTraders();
            final var backend = new L2jTopologyValidationBackend();
            final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), backend, PhantomTopologyPolicy.productionDefaults()).load(1);
            final var topology = new PhantomTopologyQuery(topologySnapshot, backend, new PhantomTopologyMetrics());
            final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults(); final var knowledgeBackend = new L2jGameKnowledgeBackend();
            final var knowledge = new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, knowledgePolicy), topology, knowledgePolicy).build();
            final var pricing = new PhantomPrivateMarketPricingAuthority(knowledge, new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog(), Rates.capture(), new Policy(500, 500), () -> World.getInstance().getVisibleObjects());
            final var catalog = new PhantomMerchantCatalog(pricing);
            final var policy = PhantomMerchantPolicy.load(Path.of(PhantomMerchantPolicy.FILE));
            check(policy != null && policy.targetStores() == 200 && policy.preparedOfflineIds().isEmpty(), "Current merchant200 policy and no invented prepared crafter required.");
            final var arrivals = topologySnapshot.anchors().stream().filter(anchor -> anchor.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.GATEKEEPER).map(anchor -> new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(anchor.point().x(), anchor.point().y(), anchor.point().z(), anchor.point().instanceId())).toList();
            service = new PhantomOfflineMerchantService(PhantomProfileRepository.open(), policy, () -> pricing, arrivals); service.start();
            layout(service, policy, arrivals, receipts, start);
            check(!pricing.quoteItem(-1).priced(), "Unknown item received a market quote.");
            receipt.append("start\t").append(service.snapshot()).append('\n');
            if (generation == 2)
            {
                check(Files.readString(receipts.resolve("ids1.txt")).equals(service.characterIds().toString()), "Restart changed merchant identities.");
                check(Files.readString(receipts.resolve("durable1.txt")).equals(durableHash()), "Restart changed finite ledger/roles/inventory before measurement.");
            }
            final long deadline = System.currentTimeMillis() + 90000;
            while (service.snapshot().nonemptyStores() < 200 && System.currentTimeMillis() < deadline)
            {
                service.onPulse(); Thread.sleep(1000);
                if (service.snapshot().blocked() > 0) { break; }
            }
            census(start, service, policy, catalog, pricing, receipts, receipt);
            check(service.snapshot().nonemptyStores() >= 200, "Bounded admission did not reach 200: " + service.snapshot());
            check(service.snapshot().nativeOwners() == service.snapshot().identityLeases() && service.snapshot().combatOrMaterializationOwners() == 0, "Owner lease/combat mismatch.");
            final var before = service.snapshot();
            if (generation == 1)
            {
                customer = customer("Customer099"); peer = customer("Peer099");
                trade(customer, service, receipt);
                lastStack(customer, peer, service, receipt);
                // Owner gate rejects the stale original stack before any transfer, then the normal pulse repairs listings.
                service.onPulse(); Thread.sleep(1000); service.onPulse();
                sample(service, receipts, receipt);
                census(start, service, policy, catalog, pricing, receipts, receipt);
            }
            check(before.issuedCapital() == service.snapshot().issuedCapital() && before.issuedStockValue() == service.snapshot().issuedStockValue(), "Measurement replayed bootstrap.");
            check(service.beginStop(), "Native work pending at STOP; no forced cleanup."); drained = true;
            // Stopped owner admission must reject a real native request before currency/items change.
            if (customer != null) { stoppedTrade(customer, service, receipt); }
            OfflineTraderTable.getInstance().storeOffliners();
            final long offline = scalar("SELECT COUNT(*) FROM character_offline_trade");
            check(offline == service.snapshot().nonemptyStores(), "STORE durable owner rows differ from native nonempty count.");
            check(scalar("SELECT COUNT(*) FROM character_offline_trade_items") > 0, "STORE persisted no listings.");
            if (generation == 1)
            {
                Files.writeString(receipts.resolve("ids1.txt"), service.characterIds().toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                Files.writeString(receipts.resolve("durable1.txt"), durableHash(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            receipt.append("store\towners=").append(offline).append(";durable=").append(durableHash()).append('\n');
            persisted = durableHash() + offlineHash();
            receipt.append("final\t").append(service.snapshot()).append('\n');
            System.out.println("OWN099_NATIVE_PASS generation=" + generation + ";" + service.snapshot() + ";MFG=NO_ELIGIBLE_MFG;CLIENT_QA=DEFERRED");
        }
        finally
        {
          try
          {
            if (service != null && !drained) { drained = service.beginStop(); }
            if (service != null && drained)
            {
                for (int id : service.characterIds())
                {
                    final var owner = World.getInstance().getPlayer(id);
                    check(owner != null && PhantomOfflineMerchantService.ownsNative(owner), "Cleanup owner identity changed; retain ambiguity.");
                    owner.stopAllTasks(); owner.storeMe(); owner.deleteMe();
                    check(World.getInstance().getPlayer(id) == null, "Native deleteMe retained owner.");
                }
                service.releaseDeletedOwners(); check(service.snapshot().identityLeases() == 0, "STOP leaked merchant leases.");
            }
            for (var actor : new Player[] {customer, peer}) { if (actor != null && World.getInstance().getPlayer(actor.getObjectId()) == actor) { actor.stopAllTasks(); actor.storeMe(); actor.deleteMe(); } }
            if (persisted != null) { check(persisted.equals(durableHash() + offlineHash()), "Cleanup changed durable merchant/offline snapshot."); receipt.append("postCleanup\tmerchantInventoryLedgerOffline=UNCHANGED\n"); }
            receipt.append("cleanup\tdrained=").append(drained).append(";remainingOwners=").append(service == null ? 0 : service.snapshot().nativeOwners()).append('\n');
            Files.writeString(receipts.resolve("start" + start + ".tsv"), receipt, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
          }
          finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
        }
    }

    private static void census(String generation, PhantomOfflineMerchantService service, PhantomMerchantPolicy policy, PhantomMerchantCatalog catalog, PhantomPrivateMarketPricingAuthority pricing, Path receipts, StringBuilder receipt) throws Exception
    {
        final var rows = new StringBuilder("generation\towner\ttype\tsite\tx\ty\tz\titem\tcategory\tgrade\tcount\tprice\tsource\n");
        final var types = new java.util.TreeMap<String, Integer>(); final var sites = new java.util.TreeMap<Integer, Integer>();
        final var categories = new java.util.TreeMap<String, Integer>(); final var grades = new java.util.TreeMap<String, Integer>(); final var items = new java.util.TreeSet<Integer>();
        final var owners = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).toList();
        check(service.characterIds().size() == Set.copyOf(service.characterIds()).size(), "Duplicate merchant object IDs.");
        for (var player : owners)
        {
            check(player != null && player.getClient() == null && player.isOnline() && player.getOfflineStartTime() > 0 && PhantomOfflineMerchantService.ownsNative(player), "Actual World/native owner mismatch.");
            check(player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && player.getInstanceId() == 0, "Store outside native peaceful world.");
            for (var other : owners) { if (other != player) { check(player.calculateDistance3D(other.getX(), other.getY(), other.getZ()) >= 60, "Overlapping merchant slots."); } }
            int siteIndex = -1;
            for (int i = 0; i < policy.sites().size(); i++)
            {
                final var site = policy.sites().get(i); final int dx = player.getX() - site.x(), dy = player.getY() - site.y();
                if (dx >= 0 && dy >= 0 && dx % site.spacing() == 0 && dy % site.spacing() == 0 && dx / site.spacing() < site.columns() && dy / site.spacing() < site.rows() && Math.abs((long) player.getZ() - site.z()) <= 200) { siteIndex = i; break; }
            }
            check(siteIndex >= 0, "Store not on a source-configured site.");
            if (!PhantomOfflineMerchantService.nonempty(player)) { continue; }
            sites.merge(siteIndex, 1, Integer::sum); types.merge(player.getPrivateStoreType().name(), 1, Integer::sum);
            final var list = player.getPrivateStoreType() == PrivateStoreType.BUY ? player.getBuyList() : player.getSellList(); long budget = 0;
            for (var line : list.getItems())
            {
                final int id = line.getItem().getId(); final var quote = pricing.quoteItem(id);
                check(quote.priced() && line.getCount() > 0 && line.getPrice() > 0, "Unpriced/empty native listing.");
                if (player.getPrivateStoreType() == PrivateStoreType.BUY) { check(quote.bidAllowed() && line.getPrice() <= quote.bid(), "BUY price drift."); budget = Math.addExact(budget, Math.multiplyExact(line.getCount(), line.getPrice())); }
                else { check(line.getCount() <= player.getInventory().getInventoryItemCount(id, -1) && line.getPrice() >= quote.ask(), "SELL stock/price drift."); }
                final String category = catalog.entries().stream().filter(entry -> entry.item().getId() == id).map(PhantomMerchantCatalog.Entry::category).findFirst().orElse("existing");
                final String grade = line.getItem().getCrystalType().name();
                items.add(id); categories.merge(category, 1, Integer::sum); grades.merge(grade, 1, Integer::sum);
                rows.append(generation).append('\t').append(player.getObjectId()).append('\t').append(player.getPrivateStoreType()).append('\t').append(siteIndex).append('\t').append(player.getX()).append('\t').append(player.getY()).append('\t').append(player.getZ()).append('\t').append(id).append('\t').append(category).append('\t').append(grade).append('\t').append(line.getCount()).append('\t').append(line.getPrice()).append('\t').append(quote.sourceReason()).append('\n');
            }
            check(budget <= player.getAdena(), "BUY listing exceeds physical currency.");
        }
        Files.writeString(receipts.resolve("market-census-start" + generation + ".tsv"), rows, StandardCharsets.UTF_8);
        receipt.append("census\t").append(service.snapshot()).append(";types=").append(types).append(";sites=").append(sites).append(";uniqueItems=").append(items.size()).append(";categories=").append(categories).append(";grades=").append(grades).append('\n');
        check(sites.size() >= 2 || service.snapshot().nonemptyStores() < 200, "200 stores occupy fewer than two clusters.");
    }

    private static Player customer(String name) throws Exception
    {
        try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('fixture099','DISABLED_FIXTURE_CUSTOMER',-1,1)")) { statement.executeUpdate(); }
        final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "fixture099", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
        check(player != null && player.getClient() == null && !player.isGM(), "Native access0 fixture customer required.");
        player.stopAllTasks(); player.setOnlineStatus(true, false); if (player.isAlikeDead()) { player.doRevive(); }
        player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
        player.addAdena(ItemProcessType.REWARD, 100000000, player, false);
        return player;
    }
    private static void place(Player customer, Player owner)
    {
        if (World.getInstance().getPlayer(customer.getObjectId()) == customer) { customer.setXYZ(owner.getX() + 20, owner.getY(), owner.getZ()); }
        else { customer.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ()); }
    }
    private static void trade(Player customer, PhantomOfflineMerchantService service, StringBuilder receipt)
    {
        final var owners = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).toList();
        final var seller = owners.stream().filter(p -> p.getPrivateStoreType() == PrivateStoreType.SELL && p.getSellList().getItems().stream().anyMatch(line -> line.getCount() > 1)).findFirst().orElseThrow();
        place(customer, seller); final var sale = seller.getSellList().getItems().stream().filter(line -> line.getCount() > 1).findFirst().orElseThrow();
        final int id = sale.getItem().getId(); final long price = sale.getPrice(), customerBefore = customer.getAdena(), sellerBefore = seller.getAdena(), before = count(customer, id), stockBefore = count(seller, id);
        check(PrivateStoreService.getInstance().buy(customer, seller.getObjectId(), Set.of(new RequestTrade(sale.getObjectId(), id, 1, price))) == PrivateStoreService.Result.COMMITTED, "Native SELL rejected.");
        check(customer.getAdena() == customerBefore - price && seller.getAdena() == sellerBefore + price && count(customer, id) == before + 1 && count(seller, id) == stockBefore - 1, "SELL conservation.");
        receipt.append("SELL\titem=").append(id).append(";price=").append(price).append(";sellerAdena=").append(sellerBefore).append("->").append(seller.getAdena()).append(";customerAdena=").append(customerBefore).append("->").append(customer.getAdena()).append(";sellerQty=").append(stockBefore).append("->").append(count(seller, id)).append(";tax=0;conserved=true\n");
        final var buyer = owners.stream().filter(p -> p.getPrivateStoreType() == PrivateStoreType.BUY).findFirst().orElseThrow(); place(customer, buyer);
        final var bid = buyer.getBuyList().getItems().iterator().next();
        final var fixture = customer.getInventory().addItem(ItemProcessType.REWARD, bid.getItem().getId(), 1, customer, "FIXTURE_CUSTOMER");
        final long funds = customer.getAdena(), buyerFunds = buyer.getAdena(), owned = count(buyer, fixture.getId()), customerStock = count(customer, fixture.getId());
        check(PrivateStoreService.getInstance().sell(customer, buyer.getObjectId(), new RequestTrade[] {new RequestTrade(fixture.getObjectId(), fixture.getId(), 1, bid.getPrice())}) == PrivateStoreService.Result.COMMITTED, "Native BUY rejected.");
        check(customer.getAdena() == funds + bid.getPrice() && buyer.getAdena() == buyerFunds - bid.getPrice() && count(buyer, fixture.getId()) == owned + 1 && count(customer, fixture.getId()) == customerStock - 1, "BUY conservation.");
        receipt.append("BUY\titem=").append(fixture.getId()).append(";price=").append(bid.getPrice()).append(";buyerAdena=").append(buyerFunds).append("->").append(buyer.getAdena()).append(";customerAdena=").append(funds).append("->").append(customer.getAdena()).append(";buyerQty=").append(owned).append("->").append(count(buyer, fixture.getId())).append(";customerQty=").append(customerStock).append("->").append(count(customer, fixture.getId())).append(";source=FIXTURE_CUSTOMER;tax=0;conserved=true\n");
    }
    private static void lastStack(Player first, Player second, PhantomOfflineMerchantService service, StringBuilder receipt) throws Exception
    {
        final var owner = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(p -> p.getPrivateStoreType() == PrivateStoreType.SELL && p.getSellList().getItemCount() > 1 && p.getSellList().getItems().stream().anyMatch(line -> line.getCount() > 1 && line.getPrice() < 1000)).findFirst().orElseThrow();
        final var line = owner.getSellList().getItems().stream().filter(value -> value.getCount() > 1 && value.getPrice() < 1000).findFirst().orElseThrow(); place(first, owner); place(second, owner);
        final int id = line.getItem().getId(); final long total = count(first, id) + count(second, id) + count(owner, id), funds = first.getAdena() + second.getAdena() + owner.getAdena();
        final var request = Set.of(new RequestTrade(line.getObjectId(), id, line.getCount(), line.getPrice())); final var ready = new java.util.concurrent.CountDownLatch(1); int committed = 0;
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2))
        {
            final var results = new java.util.ArrayList<java.util.concurrent.Future<PrivateStoreService.Result>>();
            for (var customer : List.of(first, second)) { results.add(executor.submit(() -> { ready.await(); return PrivateStoreService.getInstance().buy(customer, owner.getObjectId(), request); })); }
            ready.countDown(); for (var result : results) { if (result.get(10, java.util.concurrent.TimeUnit.SECONDS) == PrivateStoreService.Result.COMMITTED) { committed++; } }
        }
        check(committed == 1 && total == count(first, id) + count(second, id) + count(owner, id) && funds == first.getAdena() + second.getAdena() + owner.getAdena(), "Last-stack duplicated stock/currency.");
        receipt.append("LAST_STACK\tcustomers=2;committed=1;conserved=true\n");
    }
    private static void stoppedTrade(Player customer, PhantomOfflineMerchantService service, StringBuilder receipt)
    {
        final var owner = service.characterIds().stream().map(id -> World.getInstance().getPlayer(id)).filter(p -> p.getPrivateStoreType() == PrivateStoreType.SELL).findFirst().orElseThrow(); place(customer, owner);
        final var line = owner.getSellList().getItems().iterator().next(); final long funds = customer.getAdena() + owner.getAdena(), stock = count(owner, line.getItem().getId());
        check(PrivateStoreService.getInstance().buy(customer, owner.getObjectId(), Set.of(new RequestTrade(line.getObjectId(), line.getItem().getId(), 1, line.getPrice()))) == PrivateStoreService.Result.REJECTED, "Late transaction passed STOP.");
        check(funds == customer.getAdena() + owner.getAdena() && stock == count(owner, line.getItem().getId()), "STOP rejection changed funds/stock."); receipt.append("STOP_TRANSACTION\trejected=true;conserved=true\n");
    }
    private static void sample(PhantomOfflineMerchantService service, Path receipts, StringBuilder receipt) throws Exception
    {
        final var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        final long cpu = os.getProcessCpuTime(), wall = System.nanoTime(), sql = scalar("SELECT VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS WHERE VARIABLE_NAME='QUESTIONS'");
        final long gc = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
        for (int i = 0; i < 20; i++) { service.onPulse(); Thread.sleep(1000); }
        final long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        final String data = "seconds\tcpuNs\twallNs\theapBytes\tgcMillis\tglobalSqlQuestionsDelta\n20\t" + (os.getProcessCpuTime() - cpu) + '\t' + (System.nanoTime() - wall) + '\t' + heap + '\t' + (ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum() - gc) + '\t' + (scalar("SELECT VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS WHERE VARIABLE_NAME='QUESTIONS'") - sql) + '\n';
        Files.writeString(receipts.resolve("runtime-metrics.tsv"), data, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW); receipt.append("sample\t20s;global SQL includes own probes;latency/p95=NOT_MEASURED\n");
    }
    /** Diagnostic replay of the exact current source filters, compared to service admission, never used to grant slots. */
    private static void layout(PhantomOfflineMerchantService service, PhantomMerchantPolicy policy, List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> arrivals, Path receipts, String generation) throws Exception
    {
        final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
        final var npcs = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof org.l2jmobius.gameserver.model.actor.Npc).map(value -> (org.l2jmobius.gameserver.model.actor.Npc) value).filter(npc -> npc.getInstanceId() == 0).toList();
        final var doors = List.copyOf(DoorData.getInstance().getDoors());
        final var slots = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint>();
        final var output = new StringBuilder("site\traw\tlegal\trejectedByFirstSourceFilter\n");
        for (int i = 0; i < policy.sites().size(); i++)
        {
            final var site = policy.sites().get(i); final var rejected = new java.util.TreeMap<String, Integer>(); int legal = 0;
            for (int row = 0; row < site.rows(); row++) { for (int column = 0; column < site.columns(); column++)
            {
                final int x = site.x() + column * site.spacing(), y = site.y() + row * site.spacing();
                if (!geo.hasGeo(x, y)) { rejected.merge("GEO_MISSING", 1, Integer::sum); continue; }
                final int z = geo.getHeight(x, y, site.z());
                if (Math.abs((long) z - site.z()) > 200) { rejected.merge("Z", 1, Integer::sum); continue; }
                if (org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZones(x, y, z).stream().noneMatch(zone -> zone instanceof org.l2jmobius.gameserver.model.zone.type.TownZone || zone instanceof org.l2jmobius.gameserver.model.zone.type.PeaceZone)) { rejected.merge("TOWN_PEACE", 1, Integer::sum); continue; }
                final var point = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, 0);
                if (arrivals.stream().anyMatch(anchor -> anchor.distanceTo(point) < 300)) { rejected.merge("ARRIVAL", 1, Integer::sum); continue; }
                if (slots.stream().anyMatch(old -> old.distanceTo(point) < 60)) { rejected.merge("SPACING", 1, Integer::sum); continue; }
                if (npcs.stream().anyMatch(npc -> Math.abs((long) npc.getZ() - z) < 200 && Math.hypot((long) npc.getX() - x, (long) npc.getY() - y) < Math.max(160, npc.getTemplate().getCollisionRadius() + 60))) { rejected.merge("NPC", 1, Integer::sum); continue; }
                if (doors.stream().anyMatch(door -> Math.abs((long) door.getZ() - z) < 200 && Math.hypot((long) door.getX() - x, (long) door.getY() - y) < 160)) { rejected.merge("DOOR", 1, Integer::sum); continue; }
                if (!geo.canMoveToTarget(x, y, z, x + 16, y, geo.getHeight(x + 16, y, z), 0) || !geo.canMoveToTarget(x, y, z, x, y + 16, geo.getHeight(x, y + 16, z), 0)) { rejected.merge("GEO_MOVE", 1, Integer::sum); continue; }
                slots.add(point); legal++;
            } }
            output.append(i).append('\t').append(site.columns() * site.rows()).append('\t').append(legal).append('\t').append(rejected).append('\n');
        }
        check(slots.size() == service.snapshot().legalSlots(), "Diagnostic source filters differ from actual merchant service.");
        check(org.l2jmobius.gameserver.phantoms.PhantomSystem.onlineHumanPoints().isEmpty(), "Unowned human observer present; do not spawn into observed space.");
        Files.writeString(receipts.resolve("layout-start" + generation + ".tsv"), output.append("observerPoints\t0\tSOURCE_GUARD_RETAINED\n"), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    private static String durableHash() throws Exception
    {
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (String sql : new String[] {"SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components WHERE component_type='merchant.server.v1' OR component_type LIKE 'merchant.owner.%' ORDER BY profile_id,component_type", "SELECT owner_id,object_id,item_id,count,loc FROM items WHERE owner_id IN (SELECT charId FROM characters WHERE account_name LIKE 'phm_%') ORDER BY object_id"})
        {
            try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql))
            {
                while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = rows.getString(column); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static String offlineHash() throws Exception
    {
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (String sql : new String[] {"SELECT charId,type,title FROM character_offline_trade ORDER BY charId", "SELECT charId,item,count,price FROM character_offline_trade_items ORDER BY charId,item"})
        {
            try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql))
            { while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = rows.getString(column); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static long scalar(String sql) throws Exception { try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { check(rows.next(), "Missing SQL scalar."); return rows.getLong(1); } }
    private static long count(Player player, int id) { return player.getInventory().getInventoryItemCount(id, -1); }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
