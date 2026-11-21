/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt;

/** Merchant-only qualification contract. The component key remains the same one-shot intent. */
final class PhantomMerchantCraftReceipt
{
    static final int VERSION = 2;
    static final String SOURCE = "OWNER_PROVISIONED_CRAFT";
    private static final int DWARVEN = CommonSkill.CREATE_DWARVEN.getId();
    private static final int COMMON = CommonSkill.CREATE_COMMON.getId();

    private PhantomMerchantCraftReceipt() { }

    static Receipt decode(PhantomProfileComponent row)
    {
        final var payload = row.payload();
        final Receipt receipt = switch (row.componentSchemaVersion())
        {
            case 1 -> Receipt.decode(row);
            case VERSION -> Receipt.decode(new PhantomProfileComponent(row.profileId(), row.componentType(), 1,
                row.rowVersion(), payload, row.createdAt(), row.updatedAt()));
            default -> throw new IllegalStateException("Unknown merchant craft receipt version.");
        };
        if (!SOURCE.equals(receipt.source()) || receipt.classIndex() != 0 || !Arrays.equals(payload, receipt.encode()))
        {
            throw new IllegalStateException("Invalid merchant craft receipt source or encoding.");
        }
        return receipt;
    }

    static Receipt withFingerprint(Receipt receipt, String fingerprint)
    {
        return new Receipt(receipt.state(), receipt.source(), receipt.objectId(), receipt.account(), receipt.classId(),
            receipt.classIndex(), receipt.level(), receipt.dwarvenRank(), receipt.commonRank(), receipt.recipes(), fingerprint);
    }

    static String fingerprint(Receipt receipt)
    {
        return fingerprint(receipt, RecipeData.getInstance()::getRecipeList, skill ->
        {
            final int rank = PhantomNativeCraftRecipes.sourceRank(receipt.classId(), receipt.level(), skill);
            if (rank > 0 && SkillData.getInstance().getSkill(skill, rank) == null)
            {
                throw new IllegalStateException("Native craft skill definition is missing.");
            }
            return rank;
        }, item -> ItemData.getInstance().getTemplate(item) != null);
    }

    static String fingerprint(Receipt receipt, IntFunction<RecipeList> recipes, IntUnaryOperator rank, IntPredicate itemExists)
    {
        if (!SOURCE.equals(receipt.source()) || receipt.classIndex() != 0 || rank.applyAsInt(DWARVEN) != receipt.dwarvenRank()
            || rank.applyAsInt(COMMON) != receipt.commonRank())
        {
            throw new IllegalStateException("Merchant craft class or native skill rank changed.");
        }
        final var facts = new StringBuilder("merchant-native-craft-v2|")
            .append(receipt.source()).append('|').append(receipt.classId()).append('|').append(receipt.classIndex())
            .append('|').append(receipt.level()).append('|').append(DWARVEN).append('/').append(receipt.dwarvenRank())
            .append('|').append(COMMON).append('/').append(receipt.commonRank());
        for (PlayerClass type = PlayerClass.getPlayerClass(receipt.classId()); type != null; type = type.getParent())
        {
            facts.append('|').append(type.getId());
        }
        for (int id : receipt.recipes())
        {
            final var recipe = recipes.apply(id);
            if (recipe == null || recipe.getId() != id || !recipe.isDwarvenRecipe() || recipe.getLevel() < 1
                || recipe.getLevel() > Math.min(9, receipt.dwarvenRank()) || recipe.getSuccessRate() != 100
                || recipe.getRecipes().length == 0 || recipe.getCount() <= 0)
            {
                throw new IllegalStateException("Merchant craft recipe no longer qualifies.");
            }
            requireItem(itemExists, recipe.getRecipeId());
            requireItem(itemExists, recipe.getItemId());
            appendRecipe(facts, recipe);
            if (recipe.getRareItemId() != 0) { requireItem(itemExists, recipe.getRareItemId()); }
            facts.append(":rare=").append(recipe.getRareItemId()).append('/').append(recipe.getRareCount()).append('/').append(recipe.getRarity());
            for (var input : recipe.getRecipes()) { requireItem(itemExists, input.getItemId()); }
            for (var stat : recipe.getStatUse()) { facts.append(":use=").append(stat.getType()).append('/').append(stat.getValue()); }
            for (var stat : recipe.getAltStatChange()) { facts.append(":alt=").append(stat.getType()).append('/').append(stat.getValue()); }
        }
        return digest(facts.toString().getBytes(StandardCharsets.UTF_8));
    }

    // Identical field order to the archived v1 verifier. Its code fingerprint is provided by the offline operator tool.
    static String legacyFingerprint(Receipt receipt, String nativeCode)
    {
        final var facts = new StringBuilder("native-craft101-v1|").append(nativeCode)
            .append('|').append(receipt.classId()).append('|').append(receipt.level())
            .append('|').append(receipt.dwarvenRank()).append('|').append(receipt.commonRank());
        for (int id : receipt.recipes())
        {
            final var recipe = RecipeData.getInstance().getRecipeList(id);
            if (recipe == null) { throw new IllegalStateException("Legacy recipe source is missing."); }
            appendRecipe(facts, recipe);
        }
        return digest(facts.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendRecipe(StringBuilder facts, RecipeList recipe)
    {
        facts.append('|').append(recipe.getId()).append(':').append(recipe.getRecipeId()).append(':').append(recipe.getItemId())
            .append(':').append(recipe.getLevel()).append(':').append(recipe.getCount()).append(':').append(recipe.getSuccessRate());
        for (var input : recipe.getRecipes()) { facts.append(':').append(input.getItemId()).append('/').append(input.getQuantity()); }
    }

    private static void requireItem(IntPredicate itemExists, int id)
    {
        if (id <= 0 || !itemExists.test(id)) { throw new IllegalStateException("Merchant craft item source is missing."); }
    }

    static String digest(byte[] data)
    {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
