/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.time.Instant;
import java.util.List;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt;

public final class PhantomMerchantCraftCompatChecks
{
    public static void main(String[] args)
    {
        final var old = receipt("1".repeat(64));
        final var changedClassBytes = receipt("2".repeat(64));
        final String semantic = fingerprint(old, 3);
        check(semantic.equals(fingerprint(changedClassBytes, 3)), "Unrelated class bytes invalidated qualification.");
        check(!semantic.equals(fingerprint(old, 4)), "A different ingredient amount retained qualification.");
        refuse(() -> PhantomMerchantCraftReceipt.fingerprint(old, id -> recipe(3), id -> 2, id -> true));
        refuse(() -> PhantomMerchantCraftReceipt.fingerprint(old, id -> recipe(3), id -> 1, id -> id != 1864));

        final var migrated = PhantomMerchantCraftReceipt.withFingerprint(old, semantic);
        final var row = new PhantomProfileComponent(1, "merchant.craft.qualified.v1.9", 2, 2,
            migrated.encode(), Instant.EPOCH, Instant.EPOCH);
        check(PhantomMerchantCraftReceipt.decode(row).equals(migrated), "V2 changed receipt identity or grant facts.");
        refuse(() -> PhantomMerchantCraftReceipt.decode(new PhantomProfileComponent(1, row.componentType(), 3, 2,
            migrated.encode(), Instant.EPOCH, Instant.EPOCH)));
        System.out.println("MERCHANT_CRAFT_COMPAT_PASS fingerprint=stable source_changes=refused schema=2");
    }

    private static Receipt receipt(String fingerprint)
    {
        return new Receipt("READY", "OWNER_PROVISIONED_CRAFT", 17, "phm_test", 56, 0, 20, 1, 1, List.of(1), fingerprint);
    }

    private static String fingerprint(Receipt receipt, int quantity)
    {
        return PhantomMerchantCraftReceipt.fingerprint(receipt, id -> recipe(quantity), id -> 1, id -> true);
    }

    private static RecipeList recipe(int quantity)
    {
        final var data = new StatSet();
        data.set("id", 1);
        data.set("recipeId", 1666);
        data.set("recipeName", "test_arrow");
        data.set("craftLevel", 1);
        data.set("successRate", 100);
        data.set("itemId", 17);
        data.set("count", 500);
        data.set("isDwarvenRecipe", true);
        final var recipe = new RecipeList(data, false);
        recipe.addRecipe(new RecipeHolder(1864, quantity));
        return recipe;
    }

    private static void refuse(Runnable action)
    {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Invalid receipt or source was accepted.");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) { throw new AssertionError(message); }
    }
}
