/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Pure selection contracts; real source prices, ownership and transfers require the native lane. */
public final class PhantomMerchant100Checks
{
    public static void main(String[] args)
    {
        if (args.length != 1) { throw new IllegalArgumentException("catalog or circulation required"); }
        if (args[0].equals("catalog")) { catalog(); }
        else if (args[0].equals("circulation")) { circulation(); }
        else { throw new IllegalArgumentException("Unknown focused group"); }
        System.out.println("OWN100_DB_FREE_PASS group=" + args[0] + ";NATIVE=NOT_RUN");
    }
    private static void catalog()
    {
        final var first = new ArrayList<PhantomMerchantCatalog.Entry>();
        final var second = new ArrayList<PhantomMerchantCatalog.Entry>();
        for (int id = 1; id <= 80; id++)
        {
            first.add(new PhantomMerchantCatalog.Entry(null, new PhantomPrivateMarketPricingAuthority.ItemQuote(id, true, true, 9, 11, 10, "TEST_PRICED_BOUNDARY"), "material.0"));
            second.add(new PhantomMerchantCatalog.Entry(null, new PhantomPrivateMarketPricingAuthority.ItemQuote(1000 + id, true, true, 9, 11, 10, "TEST_PRICED_BOUNDARY"), "recipe.0"));
        }
        final var groups = List.of(first, second);
        final var selected = PhantomMerchantCatalog.selectEntries(groups);
        check(selected.size() == 128, "Priced candidates were truncated by the old three-per-group shortlist");
        check(selected.get(0).quote().itemId() == 1 && selected.get(1).quote().itemId() == 1001 && selected.get(127).quote().itemId() == 1064, "Deterministic group interleave lost category coverage");
        check(selected.equals(PhantomMerchantCatalog.selectEntries(groups)), "Selection changed on identical inputs");
        check(selected.stream().map(e -> e.quote().itemId()).distinct().count() == 128, "Duplicate catalog entries");
        check(PhantomMerchantCatalog.selectEntries(List.of(List.of(), first)).size() == 80, "Empty group hid eligible stock");
        final var stock = Map.of(1835, 1L, 1864, 1L, 1865, 1L, 1866, 1L, 1867, 1L, 1868, 1L, 1869, 1L, 1870, 1L);
        final var role = new PhantomMerchantBootstrap.Role(0, 42, "merchant100", "A".repeat(43), "Trader100", 53, 0, 81800, 148700, -3400, 100000, stock, PhantomMerchantBootstrap.Stage.READY);
        check(role.equals(PhantomMerchantBootstrap.decodeRole(PhantomMerchantBootstrap.encode(role))), "Eight-stock v1 recovery changed");
    }
    private static void circulation()
    {
        final var physical = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Supplies", List.of(new PhantomStorePlan.Line(345, 1835, 2, 11)), Long.MAX_VALUE);
        final var demand = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Buying", List.of(new PhantomStorePlan.Line(1835, 1835, 1, 9)), Long.MAX_VALUE);
        final Supplier<PhantomStorePlan> noMaker = () -> null;
        check(PhantomOfflineMerchantService.selectPlan(1, noMaker, () -> physical, () -> demand) == physical, "BUY purchased inventory was stranded behind fresh BUY demand");
        check(PhantomOfflineMerchantService.selectPlan(1, noMaker, () -> null, () -> demand) == demand, "Sold-out buyer did not return to native BUY");
        check(PhantomOfflineMerchantService.selectPlan(0, noMaker, () -> null, () -> demand) == null, "SELL role became a subsidized buyer");
        check(PhantomOfflineMerchantService.selectPlan(2, () -> physical, () -> { throw new AssertionError("Active MFG displaced"); }, () -> demand) == physical, "Qualified MFG did not retain priority");
        check(physical.lines().getFirst().objectOrRecipeId() == 345 && physical.lines().getFirst().count() == 2, "Stock identity/count changed");
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
