/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantCatalog;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.tests.phantoms.PhantomPopulation102Native.Merchant105;

/** Read-only native evidence. Never installs listings, invokes refresh, grants stock or releases leases. */
final class PhantomMerchant106Native
{
    private record Diagnosis(String reason, int cachedBuy, int freshBuy, int affordable, int oldPositive, boolean observed, PhantomStorePlan plan,
        PhantomPrivateMarketPricingAuthority generation) { }
    private record Frozen(Player player, int ordinal, long token, PrivateStoreType type, List<String> listing, Diagnosis diagnosis, boolean priceSafe, long wallet, List<String> inventory) { }
    private static final Map<Player, Frozen> FROZEN = new java.util.IdentityHashMap<>();

    static boolean sameListing(Object before, Object after, long token, long currentToken, PrivateStoreType type, PrivateStoreType currentType, List<String> listing, List<String> currentListing)
    {
        return before == after && token == currentToken && type == currentType && listing.equals(currentListing);
    }

    static <T> T priceEvidence(boolean stopped, T frozen, Supplier<T> live)
    {
        // A stopped knowledge service is no longer a live pricing authority. Never query it for STOP proof.
        if (!stopped) { return live.get(); }
        if (frozen == null) { throw new IllegalStateException("OWN106 pre-drain pricing evidence absent"); }
        return frozen;
    }

    static boolean sameEconomy(long wallet, long currentWallet, List<String> inventory, List<String> currentInventory)
    {
        return wallet == currentWallet && inventory.equals(currentInventory);
    }

    private static List<String> inventory(Player player)
    {
        return player.getInventory().getItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.getEnchantLevel()).sorted().toList();
    }

    private static Frozen frozen(Merchant105 merchant)
    {
        final var player = merchant.player();
        final var value = FROZEN.get(player);
        final var lines = player.getPrivateStoreType() == PrivateStoreType.NONE ? List.<String>of() : PhantomPopulation102Native.listing105(player);
        require(value != null && value.ordinal() == merchant.role().ordinal()
            && sameListing(value.player(), player, value.token(), merchant.lease().token(), value.type(), player.getPrivateStoreType(), value.listing(), lines)
            && sameEconomy(value.wallet(), player.getAdena(), value.inventory(), inventory(player)), "OWN106 listing/token/Player/economy changed after price attestation");
        return value;
    }

    private static Object call(PhantomOfflineMerchantService service, String name, Class<?>[] signature, Object... arguments) throws Exception
    {
        final var method = PhantomOfflineMerchantService.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        try { return method.invoke(service, arguments); }
        catch (java.lang.reflect.InvocationTargetException failure)
        {
            if (failure.getCause() instanceof Exception exception) { throw exception; }
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    private static Diagnosis diagnose(Merchant105 merchant, List<String> output) throws Exception
    {
        final var service = merchant.service();
        final Player player = merchant.player();
        final Class<?> type = PhantomOfflineMerchantService.class;
        final var catalog = (PhantomMerchantCatalog) PhantomPopulation102Native.field105(service, type, "_catalog");
        final var policy = (PhantomMerchantPolicy) PhantomPopulation102Native.field105(service, type, "_policy");
        final var pricing = ((Supplier<PhantomPrivateMarketPricingAuthority>) PhantomPopulation102Native.field105(service, type, "_pricing")).get();
        int cached = 0, fresh = 0, affordable = 0, oldPositive = 0;
        for (var entry : catalog.entries())
        {
            final var quote = pricing.quoteItem(entry.item().getId());
            final boolean initial = entry.item().isStackable() && entry.quote().bidAllowed();
            final boolean eligible = entry.item().isStackable() && quote.priced() && quote.bidAllowed() && quote.bid() > 0;
            final long oldCount = eligible ? Math.min(policy.stackCount(), player.getAdena() / policy.linesPerStore() / quote.bid()) : 0;
            if (initial) { cached++; }
            if (eligible) { fresh++; }
            if (initial && eligible && quote.bid() <= player.getAdena()) { affordable++; }
            if (initial && oldCount > 0) { oldPositive++; }
            output.add("QUOTE\t" + player.getObjectId() + "\t" + entry.item().getId() + "\tstackable=" + entry.item().isStackable()
                + "\tcachedBidAllowed=" + entry.quote().bidAllowed() + "\tpriced=" + quote.priced() + "\tbidAllowed=" + quote.bidAllowed()
                + "\tbid=" + quote.bid() + "\task=" + quote.ask() + "\tfair=" + quote.fair() + "\toldCount=" + oldCount + "\treason=" + quote.sourceReason());
        }
        final var point = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
        final boolean observed = (boolean) call(service, "observed", new Class<?>[] {point.getClass()}, point);
        final var sell = (PhantomStorePlan) call(service, "sellPlan", new Class<?>[] {Player.class}, player);
        final var buy = merchant.role().storeKind() == 1 ? (PhantomStorePlan) call(service, "buyPlan", new Class<?>[] {merchant.role().getClass(), Player.class}, merchant.role(), player) : null;
        final var manufacture = merchant.role().storeKind() == 2 ? (PhantomStorePlan) call(service, "manufacturePlan", new Class<?>[] {Player.class}, player) : null;
        final var plan = manufacture != null ? manufacture : sell != null ? sell : buy;
        final String reason = observed ? "OBSERVED" : plan != null ? "PLAN_AVAILABLE" : merchant.role().storeKind() != 1 ? "NO_NATIVE_PLAN"
            : cached == 0 ? "NO_CACHED_BUY_CANDIDATE" : fresh == 0 ? "NO_SAFE_FRESH_QUOTE" : affordable == 0 ? "NO_AFFORDABLE_UNIT"
            : oldPositive == 0 ? "ALLOCATION_STARVATION" : "UNKNOWN_PLAN_REFUSAL";
        output.add("PLAN\t" + player.getObjectId() + "\tsell=" + sell + "\tbuy=" + buy + "\tmanufacture=" + manufacture);
        return new Diagnosis(reason, cached, fresh, affordable, oldPositive, observed, plan, pricing);
    }

    static boolean legalIdle(PrivateStoreType type, boolean identity, boolean quiescent, boolean durable, int metadata, int items, String reason)
    {
        return type == PrivateStoreType.NONE && identity && quiescent && durable && metadata == 0 && items == 0
            && java.util.Set.of("OBSERVED", "NO_NATIVE_PLAN", "NO_CACHED_BUY_CANDIDATE", "NO_SAFE_FRESH_QUOTE", "NO_AFFORDABLE_UNIT", "ALLOCATION_STARVATION").contains(reason);
    }

    static void capture(List<Merchant105> merchants, Path receipts, String cut, boolean stopped) throws Exception
    {
        final var output = new ArrayList<String>();
        final Map<String, Integer> cross = new TreeMap<>(), idle = new TreeMap<>();
        int open = 0;
        require(!merchants.isEmpty() && merchants.stream().allMatch(merchant -> merchant.service() == merchants.getFirst().service()), "OWN106 exact single native merchant service required");
        synchronized (merchants.getFirst().service())
        {
        for (var merchant : merchants)
        {
            synchronized (merchant.service())
            {
                final var player = merchant.player();
                synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
                {
                    final var diagnosis = stopped ? frozen(merchant).diagnosis() : diagnose(merchant, output);
                    final var owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId());
                    final var nativeType = player.getPrivateStoreType();
                    if (!stopped)
                    {
                        boolean safe = nativeType == PrivateStoreType.NONE;
                        if (nativeType == PrivateStoreType.MANUFACTURE)
                        {
                            safe = !player.getManufactureItems().isEmpty();
                            for (var line : player.getManufactureItems().values())
                            {
                                final var recipe = org.l2jmobius.gameserver.data.xml.RecipeData.getInstance().getRecipeList(line.getRecipeId());
                                safe &= recipe != null && line.getCost() > 0 && line.getCost() == diagnosis.generation().manufactureFee(recipe.getId(), recipe.getItemId());
                            }
                        }
                        else if (nativeType != PrivateStoreType.NONE)
                        {
                            safe = (boolean) call(merchant.service(), "safeTradePrices", new Class<?>[] {Player.class, org.l2jmobius.gameserver.network.holders.TradeList.class}, player, nativeType == PrivateStoreType.BUY ? player.getBuyList() : player.getSellList());
                        }
                        FROZEN.put(player, new Frozen(player, merchant.role().ordinal(), merchant.lease().token(), nativeType,
                            nativeType == PrivateStoreType.NONE ? List.of() : PhantomPopulation102Native.listing105(player), diagnosis, safe, player.getAdena(), inventory(player)));
                    }
                    cross.merge("role=" + merchant.role().storeKind() + ";type=" + nativeType, 1, Integer::sum);
                    if (nativeType == PrivateStoreType.NONE) { idle.merge(diagnosis.reason(), 1, Integer::sum); } else { open++; }
                    output.add("OWNER\t" + player.getObjectId() + "\tordinal=" + merchant.role().ordinal() + "\tclass=" + player.getActiveClass()
                        + "\trole=" + merchant.role().storeKind() + "\tstage=" + merchant.role().stage() + "\ttype=" + nativeType
                        + "\tworld=" + System.identityHashCode(org.l2jmobius.gameserver.model.World.getInstance().getPlayer(player.getObjectId()))
                        + "\tplayer=" + System.identityHashCode(player) + "\tlease=" + owner + "\tserviceToken=" + merchant.lease().token()
                        + "\twallet=" + player.getAdena() + "\tcachedBuy=" + diagnosis.cachedBuy() + "\tfreshBuy=" + diagnosis.freshBuy()
                        + "\taffordable=" + diagnosis.affordable() + "\toldPositive=" + diagnosis.oldPositive() + "\tobserved=" + diagnosis.observed()
                        + "\tbuyLimit=" + player.getPrivateBuyStoreLimit() + "\tbuyLines=" + player.getBuyList().getItems().size()
                        + "\treason=" + diagnosis.reason() + "\tquiescent=" + PhantomPopulation102Native.quiescent105(merchant)
                        + "\tpriceGeneration=" + System.identityHashCode(diagnosis.generation()) + "\tlivePriceSafe=" + frozen(merchant).priceSafe());
                    for (var item : player.getInventory().getItems()) { output.add("INVENTORY\t" + player.getObjectId() + "\t" + item.getObjectId() + "\t" + item.getId() + "\t" + item.getCount() + "\t" + item.getEnchantLevel()); }
                    try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT time,type,title FROM character_offline_trade WHERE charId=?"))
                    {
                        query.setInt(1, player.getObjectId());
                        try (var sql = query.executeQuery()) { while (sql.next()) { output.add("SQL_META\t" + player.getObjectId() + "\t" + sql.getLong(1) + "\t" + sql.getInt(2) + "\t" + sql.getString(3)); } }
                        try (var lines = connection.prepareStatement("SELECT item,count,price FROM character_offline_trade_items WHERE charId=? ORDER BY item"))
                        {
                            lines.setInt(1, player.getObjectId());
                            try (var sql = lines.executeQuery()) { while (sql.next()) { output.add("SQL_LINE\t" + player.getObjectId() + "\t" + sql.getInt(1) + "\t" + sql.getLong(2) + "\t" + sql.getLong(3)); } }
                        }
                    }
                } } }
            }
        }
        }
        Files.write(receipts.resolve("merchants106-" + cut + ".private.tsv"), output, StandardOpenOption.CREATE_NEW);
        final String summary = "owners=" + merchants.size() + ";open=" + open + ";idle=" + idle + ";cross=" + cross + ";stopped=" + stopped;
        Files.writeString(receipts.resolve("merchants106-" + cut + ".txt"), summary + "\n", StandardOpenOption.CREATE_NEW);
        System.out.println("MERCHANT106 " + summary);
    }

    static boolean persisted(Merchant105 merchant) throws Exception
    {
        final Player player = merchant.player();
        synchronized (merchant.service()) { synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
        {
            require(PhantomPopulation102Native.quiescent105(merchant), "OWN106 pending native owner");
            final var attestation = priceEvidence(true, frozen(merchant), () -> { throw new AssertionError("Stopped native price proof queried live supplier"); });
            // Validate durable character, account and all physical inventory even when there is no store row.
            try (var connection = DatabaseFactory.getConnection())
            {
                try (var query = connection.prepareStatement("SELECT c.account_name,c.accesslevel,a.accessLevel,a.password,c.exp,c.sp,c.level,c.x,c.y,c.z FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.charId=?"))
                {
                    query.setInt(1, player.getObjectId());
                    try (var sql = query.executeQuery())
                    {
                        require(sql.next() && merchant.role().account().equals(sql.getString(1)) && sql.getInt(2) == 0 && sql.getInt(3) == -1
                            && merchant.role().token().equals(sql.getString(4)) && sql.getLong(5) == player.getExp() && sql.getLong(6) == player.getSp()
                            && sql.getInt(7) == player.getLevel() && sql.getInt(8) == player.getX() && sql.getInt(9) == player.getY() && sql.getInt(10) == player.getZ() && !sql.next(), "OWN106 character/account durable mismatch");
                    }
                }
                final var expected = new TreeMap<Integer, String>();
                for (var item : player.getInventory().getItems()) { require(expected.put(item.getObjectId(), item.getId() + ":" + item.getCount() + ":" + item.getEnchantLevel()) == null, "OWN106 duplicate inventory"); }
                final var actual = new TreeMap<Integer, String>();
                try (var query = connection.prepareStatement("SELECT object_id,item_id,count,enchant_level FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
                {
                    query.setInt(1, player.getObjectId());
                    try (var sql = query.executeQuery()) { while (sql.next()) { require(actual.put(sql.getInt(1), sql.getInt(2) + ":" + sql.getLong(3) + ":" + sql.getInt(4)) == null, "OWN106 duplicate SQL inventory"); } }
                }
                require(expected.equals(actual), "OWN106 inventory not persisted exactly");
                if (player.getPrivateStoreType() == PrivateStoreType.NONE)
                {
                    final var diagnosis = attestation.diagnosis();
                    int metadata, items;
                    try (var query = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM character_offline_trade WHERE charId=?),(SELECT COUNT(*) FROM character_offline_trade_items WHERE charId=?)"))
                    {
                        query.setInt(1, player.getObjectId()); query.setInt(2, player.getObjectId());
                        try (var sql = query.executeQuery()) { require(sql.next(), "OWN106 idle SQL absent"); metadata = sql.getInt(1); items = sql.getInt(2); }
                    }
                    require(legalIdle(player.getPrivateStoreType(), true, true, true, metadata, items, diagnosis.reason()), "OWN106 unexplained or contradictory IDLE");
                    return true;
                }
            }
            if (player.getPrivateStoreType() == PrivateStoreType.BUY)
            {
                long total = 0;
                final var ids = new HashSet<Integer>();
                for (var line : player.getBuyList().getItems()) { require(line.getCount() > 0 && line.getPrice() > 0 && ids.add(line.getItem().getId()), "OWN106 invalid BUY line"); total = Math.addExact(total, Math.multiplyExact(line.getCount(), line.getPrice())); }
                require(total <= player.getAdena(), "OWN106 BUY overcommitted wallet");
            }
            if (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE) { call(merchant.service(), "validateDurableEligibility", new Class<?>[] {Player.class}, player); }
            require(attestation.priceSafe(), "OWN106 pre-drain source price attestation failed");
            return PhantomPopulation102Native.persisted105(merchant);
        } } } }
    }

    public static void main(String[] args)
    {
        require(legalIdle(PrivateStoreType.NONE, true, true, true, 0, 0, "ALLOCATION_STARVATION"), "Legal idle rejected");
        for (var type : List.of(PrivateStoreType.BUY, PrivateStoreType.SELL, PrivateStoreType.MANUFACTURE)) { require(!legalIdle(type, true, true, true, 0, 0, "ALLOCATION_STARVATION"), "Empty store accepted as IDLE"); }
        require(!legalIdle(PrivateStoreType.NONE, true, true, true, 0, 1, "NO_AFFORDABLE_UNIT"), "Orphan offline item accepted");
        require(!legalIdle(PrivateStoreType.NONE, true, true, true, 1, 0, "NO_AFFORDABLE_UNIT"), "Contradictory metadata accepted");
        require(!legalIdle(PrivateStoreType.NONE, false, true, true, 0, 0, "OBSERVED"), "Wrong identity accepted");
        require(!legalIdle(PrivateStoreType.NONE, true, false, true, 0, 0, "OBSERVED"), "Pending mutation accepted");
        require(!legalIdle(PrivateStoreType.NONE, true, true, false, 0, 0, "OBSERVED"), "Unpersisted character accepted");
        require(!legalIdle(PrivateStoreType.NONE, true, true, true, 0, 0, "PLAN_AVAILABLE"), "Unexplained refusal accepted");
        final var identity = new Object();
        require(sameListing(identity, identity, 1, 1, PrivateStoreType.BUY, PrivateStoreType.BUY, List.of("item:1:12500"), List.of("item:1:12500")), "Frozen listing rejected");
        require(!sameListing(identity, new Object(), 1, 1, PrivateStoreType.BUY, PrivateStoreType.BUY, List.of(), List.of()), "Replaced Player accepted");
        require(!sameListing(identity, identity, 1, 2, PrivateStoreType.BUY, PrivateStoreType.BUY, List.of(), List.of()), "Stale token accepted");
        require(!sameListing(identity, identity, 1, 1, PrivateStoreType.BUY, PrivateStoreType.NONE, List.of(), List.of()), "Changed subtype accepted");
        require(!sameListing(identity, identity, 1, 1, PrivateStoreType.BUY, PrivateStoreType.BUY, List.of("item:1:12500"), List.of("item:2:12500")), "Changed listing accepted");
        final Object generation = new Object();
        require(priceEvidence(true, generation, () -> { throw new IllegalStateException("knowledge STOPPED"); }) == generation, "STOP queried dead pricing supplier");
        try { priceEvidence(true, null, () -> generation); throw new AssertionError("Missing attestation accepted"); }
        catch (IllegalStateException expected) { }
        require(sameEconomy(100000, 100000, List.of("stock:1"), List.of("stock:1")), "Frozen economy rejected");
        require(!sameEconomy(100000, 99999, List.of("stock:1"), List.of("stock:1")), "Changed wallet accepted");
        require(!sameEconomy(100000, 100000, List.of("stock:1"), List.of("stock:2")), "Changed stock accepted");
        require(!DatabaseFactory.isInitialized(), "Focused check initialized JDBC");
        System.out.println("MERCHANT106_IDLE_CHECK_PASS legalIdle=true;emptyStoreOrphansUnknownAndUnsafe=REJECTED;SQL=0;World=NOT_INITIALIZED");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
