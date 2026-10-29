/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger;
import org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.tests.phantoms.PhantomPopulation102Native.Merchant105;

/** One107 lane: immutable live price attestation, actual native inventory and read-only SQL proof. */
final class PhantomMerchant107Native
{
    private record Frozen(Player player, int ordinal, long token, PrivateStoreType type, List<String> listing,
        long wallet, List<String> inventory, boolean priceSafe, String idle) { }
    private static final Map<Player, Frozen> FROZEN = new java.util.IdentityHashMap<>();
    private static final Set<Integer> EVER = new HashSet<>();
    private static final Class<PhantomOfflineMerchantService> TYPE = PhantomOfflineMerchantService.class;

    private static Object call(Object target, Class<?> type, String name, Class<?>[] signature, Object... arguments) throws Exception
    {
        final var method = type.getDeclaredMethod(name, signature); method.setAccessible(true);
        try { return method.invoke(target, arguments); }
        catch (java.lang.reflect.InvocationTargetException failed)
        {
            if (failed.getCause() instanceof Exception exception) { throw exception; }
            if (failed.getCause() instanceof Error error) { throw error; }
            throw failed;
        }
    }
    private static List<String> inventory(Player player)
    {
        return player.getInventory().getItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.getEnchantLevel()).sorted().toList();
    }
    private static Frozen frozen(Merchant105 merchant)
    {
        final var player = merchant.player(); final var value = FROZEN.get(player);
        final var lines = player.getPrivateStoreType() == PrivateStoreType.NONE ? List.<String>of() : PhantomPopulation102Native.listing105(player);
        require(value != null && value.ordinal() == merchant.role().ordinal()
            && PhantomMerchant106Native.sameListing(value.player(), player, value.token(), merchant.lease().token(), value.type(), player.getPrivateStoreType(), value.listing(), lines)
            && PhantomMerchant106Native.sameEconomy(value.wallet(), player.getAdena(), value.inventory(), inventory(player)), "OWN107 frozen owner/listing/token/economy changed");
        return value;
    }
    static void freezeForStop(List<Merchant105> merchants, Path receipts, int phase) throws Exception
    {
        require(!merchants.isEmpty(), "OWN107 no merchant service to freeze");
        final var service = merchants.getFirst().service();
        final long deadline = System.nanoTime() + 30_000_000_000L;
        while (!service.beginStop())
        {
            require(System.nanoTime() < deadline, "OWN107 market native work/uncertainty prevents STOP freeze"); Thread.sleep(100);
        }
        // Pricing is still alive here; the shared merchant producer and transaction admission are frozen.
        capture(merchants, receipts, "prestop" + phase, false);
    }
    @SuppressWarnings("unchecked")
    static void capture(List<Merchant105> merchants, Path receipts, String cut, boolean stopped) throws Exception
    {
        require(merchants.size() == 250 && merchants.stream().allMatch(value -> value.service() == merchants.getFirst().service()), "OWN107 original250 merchant owners required");
        final var service = merchants.getFirst().service();
        final var rows = new ArrayList<String>(); final Map<String, Integer> types = new TreeMap<>();
        final Set<Integer> stock = new HashSet<>(), offered = new HashSet<>(), funded = new HashSet<>();
        int open = 0, enchanted = 0, zeroGear = 0;
        synchronized (service)
        {
            final var catalog = (PhantomMerchantCatalog) PhantomPopulation102Native.field105(service, TYPE, "_catalog");
            final var pricing = stopped ? null : ((Supplier<PhantomPrivateMarketPricingAuthority>) PhantomPopulation102Native.field105(service, TYPE, "_pricing")).get();
            final var market = PhantomPopulation102Native.field105(service, TYPE, "_marketProvision");
            final var ledger = (PhantomMerchantMarketLedger.Ledger) PhantomPopulation102Native.field105(market, market.getClass(), "_ledger");
            final var grants = (Map<Long, PhantomMerchantMarketLedger.Receipt>) PhantomPopulation102Native.field105(market, market.getClass(), "_receipts");
            require(((Set<?>) PhantomPopulation102Native.field105(market, market.getClass(), "_quarantined")).isEmpty(), "OWN107 uncertain market owner");
            require(grants.size() == ledger.sequence() && grants.values().stream().noneMatch(PhantomMerchantMarketLedger.Receipt::quarantined), "OWN107 pending grant is not native acceptance");
            try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
            {
                final var server = (org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent) PhantomPopulation102Native.field105(service, TYPE, "_server");
                query.setLong(1, server.profileId()); query.setString(2, PhantomMerchantMarketLedger.COMPONENT);
                try (var sql = query.executeQuery()) { require(sql.next() && java.util.Arrays.equals(sql.getBytes(1), PhantomMerchantMarketLedger.encode(ledger)) && !sql.next(), "OWN107 quota ledger SQL/cache differs"); }
            }
            for (var grant : grants.values())
            {
                rows.add("GRANT\t" + grant);
                try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
                {
                    final var server = (org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent) PhantomPopulation102Native.field105(service, TYPE, "_server");
                    query.setLong(1, server.profileId()); query.setString(2, grant.component());
                    try (var sql = query.executeQuery()) { require(sql.next() && java.util.Arrays.equals(sql.getBytes(1), PhantomMerchantMarketLedger.encode(grant)) && !sql.next(), "OWN107 grant SQL/cache differs"); }
                }
            }
            for (var merchant : merchants)
            {
                final var player = merchant.player();
                synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
                {
                    require(PhantomPopulation102Native.quiescent105(merchant), "OWN107 pending native owner");
                    final var owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId());
                    require(owner != null && owner.token() == merchant.lease().token() && World.getInstance().getPlayer(player.getObjectId()) == player, "OWN107 World/lease differs");
                    final var type = player.getPrivateStoreType();
                    if (!stopped)
                    {
                        String idle = ""; boolean safe = true;
                        if (type == PrivateStoreType.NONE)
                        {
                            final var point = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
                            final boolean observed = (boolean) call(null, TYPE, "observed", new Class<?>[] {point.getClass()}, point);
                            final var sell = call(service, TYPE, "sellPlan", new Class<?>[] {Player.class}, player);
                            final var buy = merchant.role().storeKind() == 2 ? null : call(service, TYPE, "buyPlan", new Class<?>[] {merchant.role().getClass(), Player.class}, merchant.role(), player);
                            final var manufacture = merchant.role().storeKind() == 2 ? call(service, TYPE, "manufacturePlan", new Class<?>[] {Player.class}, player) : null;
                            idle = observed ? "OBSERVED" : sell == null && buy == null && manufacture == null ? "NO_NATIVE_PLAN" : "PLAN_AVAILABLE";
                            safe = !idle.equals("PLAN_AVAILABLE");
                        }
                        else if (type == PrivateStoreType.MANUFACTURE)
                        {
                            safe = !player.getManufactureItems().isEmpty();
                            for (var line : player.getManufactureItems().values())
                            {
                                final var recipe = org.l2jmobius.gameserver.data.xml.RecipeData.getInstance().getRecipeList(line.getRecipeId());
                                safe &= recipe != null && line.getCost() == pricing.manufactureFee(recipe.getId(), recipe.getItemId());
                            }
                        }
                        else { safe = (boolean) call(service, TYPE, "safeTradePrices", new Class<?>[] {Player.class, org.l2jmobius.gameserver.network.holders.TradeList.class}, player, type == PrivateStoreType.BUY ? player.getBuyList() : player.getSellList()); }
                        FROZEN.put(player, new Frozen(player, merchant.role().ordinal(), merchant.lease().token(), type, type == PrivateStoreType.NONE ? List.of() : PhantomPopulation102Native.listing105(player), player.getAdena(), inventory(player), safe, idle));
                    }
                    final var proof = frozen(merchant);
                    types.merge(type.name(), 1, Integer::sum); if (type != PrivateStoreType.NONE) { open++; }
                    rows.add("OWNER\t" + player.getObjectId() + "\tordinal=" + merchant.role().ordinal() + ";birthRole=" + merchant.role().storeKind() + ";type=" + type + ";wallet=" + player.getAdena() + ";token=" + merchant.lease().token() + ";priceSafe=" + proof.priceSafe() + ";idle=" + proof.idle() + ";idleState=" + ((Map<?, ?>) PhantomPopulation102Native.field105(service, TYPE, "_idle")).get(merchant.role().ordinal()));
                    for (var item : player.getInventory().getItems()) { stock.add(item.getId()); rows.add("INVENTORY\t" + player.getObjectId() + '\t' + item.getObjectId() + '\t' + item.getId() + '\t' + item.getCount() + '\t' + item.getEnchantLevel()); }
                    if (type == PrivateStoreType.BUY)
                    {
                        long reserve = 0;
                        for (var line : player.getBuyList().getItems()) { offered.add(line.getItem().getId()); funded.add(line.getItem().getId()); reserve = Math.addExact(reserve, Math.multiplyExact(line.getCount(), line.getPrice())); rows.add("BUY\t" + player.getObjectId() + '\t' + line.getItem().getId() + '\t' + line.getCount() + '\t' + line.getPrice()); }
                        require(reserve <= player.getAdena(), "OWN107 BUY reserve exceeds physical wallet");
                    }
                    if (type == PrivateStoreType.SELL || type == PrivateStoreType.PACKAGE_SELL)
                    {
                        for (var line : player.getSellList().getItems())
                        {
                            final var item = player.getInventory().getItemByObjectId(line.getObjectId()); require(item != null && catalog.contains(item.getId()), "OWN107 listed item missing from eligible source catalog"); offered.add(item.getId());
                            if (catalog.classification(item.getId()).category().equals("FINISHED_GEAR")) { if (item.getEnchantLevel() > 0) { enchanted++; } else { zeroGear++; } }
                            rows.add("SELL\t" + player.getObjectId() + '\t' + item.getObjectId() + '\t' + item.getId() + '\t' + line.getCount() + '\t' + line.getPrice() + '\t' + item.getEnchantLevel() + (stopped ? "\tFROZEN_PRICE" : "\t" + pricing.quoteStock(item.getId(), item.getEnchantLevel())));
                        }
                    }
                    require(persisted(merchant), "OWN107 native SQL persistence failed");
                } } }
            }
            EVER.addAll(offered);
            if (!stopped)
            {
                rows.add("QUOTE_METRICS\t" + pricing.quoteMetrics());
                writeCatalog(catalog, receipts, cut, stock, offered, funded);
            }
            rows.add("LEDGER\t" + ledger);
            final String summary = "owners=" + merchants.size() + ";open=" + open + ";types=" + types + ";distinctOffered=" + offered.size() + ";gear0=" + zeroGear + ";enchanted=" + enchanted + ";fundingIssued=" + ledger.fundingIssued() + ";stockIssued=" + ledger.stockIssued() + ";grants=" + ledger.sequence() + ";stopped=" + stopped + ";market=" + (open >= 200 && types.getOrDefault("BUY", 0) > 0 && types.getOrDefault("SELL", 0) > 0 && types.getOrDefault("MANUFACTURE", 0) == 25 ? "PASS" : "PARTIAL");
            Files.write(receipts.resolve("merchants107-" + cut + ".private.tsv"), rows, StandardOpenOption.CREATE_NEW);
            Files.writeString(receipts.resolve("merchants107-" + cut + ".txt"), summary + "\n", StandardOpenOption.CREATE_NEW);
            System.out.println("MERCHANT107 " + summary);
        }
    }
    private static void writeCatalog(PhantomMerchantCatalog catalog, Path receipts, String cut, Set<Integer> stock, Set<Integer> offered, Set<Integer> funded) throws Exception
    {
        final var rows = new StringBuilder("itemId\tcategory\ttargetGrade\tprovenance\trecipeId\tchance\tsourceQuote\tstatus\texclusionReason\n");
        final Map<String, long[]> counts = new TreeMap<>();
        for (var row : catalog.audit())
        {
            final var c = row.classification(); final var q = row.quote(); final int id = row.item().getId();
            rows.append(id).append('\t').append(c.category()).append('\t').append(c.targetGrade()).append('\t').append(c.provenance()).append('\t').append(c.recipes().toString().replace(" ", "")).append('\t').append(c.category().equals("RECIPE_SCROLL60") && c.eligible() ? "60" : "").append('\t').append(q).append('\t').append(c.eligible() ? q.priced() ? "SOURCE_PRICED" : "UNPRICED" : "DENIED").append('\t').append(c.exclusionReason().isEmpty() && !q.priced() ? q.sourceReason() : c.exclusionReason()).append('\n');
            final var count = counts.computeIfAbsent(c.category() + '\t' + c.targetGrade(), _ -> new long[7]);
            count[0]++; if (c.eligible()) { count[1]++; } if (q.priced()) { count[2]++; } if (funded.contains(id)) { count[3]++; } if (stock.contains(id)) { count[4]++; } if (EVER.contains(id)) { count[5]++; } if (offered.contains(id)) { count[6]++; }
        }
        Files.writeString(receipts.resolve("market107-universe-" + cut + ".tsv"), rows, StandardOpenOption.CREATE_NEW);
        final var coverage = new StringBuilder("category\tgrade\tsourceCandidates\tclassEligible\tpriced\tfundedListed\tinStock\teverOfferedObserved\tcurrentlyOpen\n");
        counts.forEach((key, count) -> { coverage.append(key); for (long value : count) { coverage.append('\t').append(value); } coverage.append('\n'); });
        Files.writeString(receipts.resolve("market107-coverage-" + cut + ".tsv"), coverage, StandardOpenOption.CREATE_NEW);
    }
    static boolean persisted(Merchant105 merchant) throws Exception
    {
        final var player = merchant.player(); final var proof = PhantomMerchant106Native.priceEvidence(true, frozen(merchant), () -> { throw new AssertionError("OWN107 STOP queried live pricing supplier"); });
        require(proof.priceSafe(), "OWN107 live price/IDLE attestation failed");
        try (var connection = DatabaseFactory.getConnection())
        {
            final var actual = new ArrayList<String>();
            try (var query = connection.prepareStatement("SELECT object_id,item_id,count,enchant_level FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
            {
                query.setInt(1, player.getObjectId()); try (var sql = query.executeQuery()) { while (sql.next()) { actual.add(sql.getInt(1) + ":" + sql.getInt(2) + ":" + sql.getLong(3) + ":" + sql.getInt(4)); } }
            }
            require(inventory(player).equals(actual.stream().sorted().toList()), "OWN107 full inventory/enchant SQL differs");
            try (var query = connection.prepareStatement("SELECT c.account_name,c.accesslevel,a.accessLevel,a.password,c.exp,c.sp,c.level,c.x,c.y,c.z FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.charId=?"))
            {
                query.setInt(1, player.getObjectId());
                try (var sql = query.executeQuery()) { require(sql.next() && merchant.role().account().equals(sql.getString(1)) && sql.getInt(2) == 0 && sql.getInt(3) == -1 && merchant.role().token().equals(sql.getString(4)) && sql.getLong(5) == player.getExp() && sql.getLong(6) == player.getSp() && sql.getInt(7) == player.getLevel() && sql.getInt(8) == player.getX() && sql.getInt(9) == player.getY() && sql.getInt(10) == player.getZ() && !sql.next(), "OWN107 character/disabled-account readback differs"); }
            }
            if (player.getPrivateStoreType() == PrivateStoreType.NONE)
            {
                try (var query = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM character_offline_trade WHERE charId=?),(SELECT COUNT(*) FROM character_offline_trade_items WHERE charId=?)"))
                {
                    query.setInt(1, player.getObjectId()); query.setInt(2, player.getObjectId());
                    try (var sql = query.executeQuery()) { require(sql.next() && PhantomMerchant106Native.legalIdle(PrivateStoreType.NONE, true, PhantomPopulation102Native.quiescent105(merchant), true, sql.getInt(1), sql.getInt(2), proof.idle()), "OWN107 contradictory/unexplained IDLE"); }
                }
                return true;
            }
        }
        return PhantomPopulation102Native.persisted105(merchant);
    }
    private static void require(boolean condition, String reason) { if (!condition) { throw new AssertionError(reason); } }
}
