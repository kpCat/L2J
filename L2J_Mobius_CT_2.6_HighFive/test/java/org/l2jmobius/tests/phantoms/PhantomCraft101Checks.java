/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Set;
import org.l2jmobius.gameserver.phantoms.progression.PhantomCraftRecipePolicy;

/** DB-free trust-zone and finite-selection contracts; no native progression claim. */
public final class PhantomCraft101Checks
{
    public static void main(String[] args)
    {
        check(PhantomCraftRecipePolicy.startingClass(2, List.of(0, 10, 53), 0) == 53, "MFG must be born dwarf, never converted from Human.");
        check(PhantomCraftRecipePolicy.startingClass(2, List.of(0, 10), 10) == 10, "No allowlisted dwarf must keep fallback identity.");
        check(PhantomCraftRecipePolicy.startingClass(1, List.of(0, 10, 53), 10) == 10, "BUY identity changed.");
        check(PhantomCraftRecipePolicy.creator(56) && PhantomCraftRecipePolicy.creator(57) && PhantomCraftRecipePolicy.creator(118), "Native creator lineage missing.");
        check(!PhantomCraftRecipePolicy.creator(53) && !PhantomCraftRecipePolicy.creator(0) && !PhantomCraftRecipePolicy.creator(55), "Common Craft or spoil lineage admitted.");
        final var catalog = List.of(new PhantomCraftRecipePolicy.Recipe(1, 1), new PhantomCraftRecipePolicy.Recipe(2, 2), new PhantomCraftRecipePolicy.Recipe(3, 2), new PhantomCraftRecipePolicy.Recipe(4, 3));
        final var selected = PhantomCraftRecipePolicy.select(catalog, 2, 0, 2, Set.of(2));
        check(selected.equals(List.of(1, 3)), "Recipe selection ignored rank, quota or already learned recipes: " + selected);
        check(selected.equals(PhantomCraftRecipePolicy.select(catalog, 2, 0, 2, Set.of(2))), "Selection is not deterministic.");
        check(PhantomCraftRecipePolicy.select(catalog, 0, 0, 2, Set.of()).isEmpty(), "Missing native skill received recipes.");
        System.out.println("TASK101_DB_FREE_PASS contracts=3 native=NOT_RUN");
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
