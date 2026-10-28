/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Native recipe writer with intent-before-effects and SQL readback, shared by explicit grant owners. */
public final class PhantomNativeCraftRecipes
{
    private static final String NATIVE_CODE = codeFingerprint();
    private final List<RecipeList> _catalog;
    public PhantomNativeCraftRecipes(PhantomPrivateMarketPricingAuthority pricing)
    {
        final var data = RecipeData.getInstance();
        _catalog = java.util.Arrays.stream(data.getAllItemIds()).mapToObj(data::getRecipeByItemId).filter(java.util.Objects::nonNull)
            .filter(recipe -> recipe.isDwarvenRecipe() && recipe.getLevel() >= 1 && recipe.getLevel() <= 9 && recipe.getRecipes().length > 0 && recipe.getSuccessRate() == 100)
            .filter(recipe -> { try { return pricing.manufactureFee(recipe.getId(), recipe.getItemId()) > 0; } catch (IllegalArgumentException unpriced) { return false; } })
            .sorted(Comparator.comparingInt(RecipeList::getId)).toList();
        if (_catalog.size() > 4096) { throw new IllegalArgumentException("Native craft catalog exceeds bound."); }
    }
    public List<Integer> select(Player player, int rank, long offset, int quota)
    {
        return PhantomCraftRecipePolicy.select(_catalog.stream().map(recipe -> new PhantomCraftRecipePolicy.Recipe(recipe.getId(), recipe.getLevel())).toList(), rank, offset, quota,
            java.util.stream.Stream.concat(player.getDwarvenRecipeBook().stream(), player.getCommonRecipeBook().stream()).map(RecipeList::getId).collect(java.util.stream.Collectors.toSet()));
    }
    public static int sourceRank(int classId, int level, int skillId)
    {
        return SkillTreeData.getInstance().getCompleteClassSkillTree(PlayerClass.getPlayerClass(classId)).values().stream()
            .filter(learn -> learn.getSkillId() == skillId && learn.getGetLevel() <= level).mapToInt(learn -> learn.getSkillLevel()).max().orElse(0);
    }
    public record Receipt(String state, String source, int objectId, String account, int classId, int classIndex, int level, int dwarvenRank, int commonRank, List<Integer> recipes, String fingerprint)
    {
        public Receipt
        {
            recipes = List.copyOf(recipes);
            if (!Set.of("PLANNED", "READY", "INCONSISTENT").contains(state) || !Set.of("OWNER_PROVISIONED_CRAFT", "PROGRESSION_RECIPE_BONUS").contains(source)
                || objectId <= 0 || !account.matches("[a-zA-Z0-9_]{1,45}") || !PhantomCraftRecipePolicy.creator(classId) || classIndex < 0 || classIndex > 3 || level < 20 || level > 85
                || dwarvenRank < 1 || dwarvenRank > 10 || commonRank < 0 || commonRank > 9 || recipes.isEmpty() || recipes.size() > 12 || recipes.stream().anyMatch(id -> id <= 0) || recipes.stream().distinct().count() != recipes.size() || !fingerprint.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid native craft receipt."); }
        }
        public Receipt state(String value) { return new Receipt(value, source, objectId, account, classId, classIndex, level, dwarvenRank, commonRank, recipes, fingerprint); }
        public byte[] encode()
        {
            return (String.join("|", state, source, Integer.toString(objectId), account, Integer.toString(classId), Integer.toString(classIndex), Integer.toString(level), Integer.toString(dwarvenRank), Integer.toString(commonRank), recipes.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")), fingerprint)).getBytes(StandardCharsets.UTF_8);
        }
        public static Receipt decode(PhantomProfileComponent component)
        {
            if (component.componentSchemaVersion() != 1 || component.payload().length > 4096) { throw new IllegalStateException("Unknown native craft receipt schema."); }
            final var fields = new String(component.payload(), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 11) { throw new IllegalStateException("Invalid native craft receipt fields."); }
            return new Receipt(fields[0], fields[1], Integer.parseInt(fields[2]), fields[3], Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]), java.util.Arrays.stream(fields[9].split(",")).map(Integer::parseInt).toList(), fields[10]);
        }
    }
    public Receipt plan(String source, Player player, int classId, int level, int dwarven, int common, List<Integer> ids)
    {
        return new Receipt("PLANNED", source, player.getObjectId(), player.getAccountName(), classId, player.getClassIndex(), level, dwarven, common, ids, fingerprint(classId, level, dwarven, common, ids));
    }
    private String fingerprint(int classId, int level, int dwarven, int common, List<Integer> ids)
    {
        final var text = new StringBuilder("native-craft101-v1|").append(NATIVE_CODE).append('|').append(classId).append('|').append(level).append('|').append(dwarven).append('|').append(common);
        if (sourceRank(classId, level, CommonSkill.CREATE_DWARVEN.getId()) < dwarven || sourceRank(classId, level, CommonSkill.CREATE_COMMON.getId()) < common) { throw new IllegalStateException("Craft rank absent from current native class tree."); }
        for (int id : ids)
        {
            final var recipe = _catalog.stream().filter(value -> value.getId() == id).findFirst().orElseThrow(() -> new IllegalStateException("Receipt recipe source changed."));
            if (recipe.getLevel() > dwarven) { throw new IllegalStateException("Receipt exceeds native craft rank."); }
            text.append('|').append(id).append(':').append(recipe.getRecipeId()).append(':').append(recipe.getItemId()).append(':').append(recipe.getLevel()).append(':').append(recipe.getCount()).append(':').append(recipe.getSuccessRate());
            for (var input : recipe.getRecipes()) { text.append(':').append(input.getItemId()).append('/').append(input.getQuantity()); }
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String codeFingerprint()
    {
        try
        {
            final var digest = MessageDigest.getInstance("SHA-256");
            for (var type : List.of(Player.class, RecipeData.class, SkillTreeData.class, PhantomNativeCraftRecipes.class))
            {
                try (var input = type.getResourceAsStream('/' + type.getName().replace('.', '/') + ".class"))
                { if (input == null) { throw new IllegalStateException("Native crafting code fingerprint unavailable."); } digest.update(input.readAllBytes()); }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("Native crafting code fingerprint unavailable.", failure); }
    }
    public void verify(Player player, Receipt receipt)
    {
        if (!receipt.state().equals("READY") || player.getObjectId() != receipt.objectId() || !player.getAccountName().equals(receipt.account()) || player.getActiveClass() != receipt.classId() || player.getClassIndex() != receipt.classIndex()
            || player.getLevel() < receipt.level() || player.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < receipt.dwarvenRank() || player.getSkillLevel(CommonSkill.CREATE_COMMON.getId()) < receipt.commonRank()
            || !fingerprint(receipt.classId(), receipt.level(), receipt.dwarvenRank(), receipt.commonRank(), receipt.recipes()).equals(receipt.fingerprint())) { throw new IllegalStateException("INCONSISTENT native craft owner/source; grant will not replay."); }
        verifyQualification(player, receipt);
        try (var connection = DatabaseFactory.getConnection())
        {
            for (int id : receipt.recipes())
            {
                if (!player.hasRecipeList(id)) { throw new IllegalStateException("INCONSISTENT native recipe missing in Player; no replay."); }
                try (var query = connection.prepareStatement("SELECT type FROM character_recipebook WHERE charId=? AND id=? AND classIndex=?"))
                {
                    query.setInt(1, player.getObjectId()); query.setInt(2, id); query.setInt(3, receipt.classIndex());
                    try (var rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) != 1 || rows.next()) { throw new IllegalStateException("INCONSISTENT persisted recipe type/classIndex; no replay."); } }
                }
            }
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("Native recipe readback unavailable; no replay.", failure); }
    }
    public void verifyQualification(Player player, Receipt receipt)
    {
        try (var connection = DatabaseFactory.getConnection())
        {
            final String sql = player.getClassIndex() == 0 ? "SELECT classid,level FROM characters WHERE charId=? AND account_name=? AND accesslevel=0" : "SELECT s.class_id,s.level FROM character_subclasses s JOIN characters c ON c.charId=s.charId WHERE s.charId=? AND c.account_name=? AND c.accesslevel=0 AND s.class_index=?";
            try (var query = connection.prepareStatement(sql))
            {
                query.setInt(1, player.getObjectId()); query.setString(2, player.getAccountName());
                if (player.getClassIndex() != 0) { query.setInt(3, player.getClassIndex()); }
                try (var rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) != player.getActiveClass() || rows.getInt(2) != player.getLevel() || rows.next()) { throw new IllegalStateException("INCONSISTENT persisted craft character."); } }
            }
            try (var query = connection.prepareStatement("SELECT skill_level FROM character_skills WHERE charId=? AND skill_id=? AND class_index=?"))
            {
                for (var skill : List.of(CommonSkill.CREATE_DWARVEN, CommonSkill.CREATE_COMMON))
                {
                    final int rank = skill == CommonSkill.CREATE_DWARVEN ? receipt.dwarvenRank() : receipt.commonRank();
                    if (rank == 0) { continue; }
                    query.setInt(1, player.getObjectId()); query.setInt(2, skill.getId()); query.setInt(3, receipt.classIndex());
                    try (var rows = query.executeQuery()) { if (!rows.next() || rows.getInt(1) < rank || rows.next()) { throw new IllegalStateException("INCONSISTENT persisted craft skill."); } }
                }
            }
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("Native craft readback unavailable; no replay.", failure); }
    }
    public void learn(Player player, Receipt receipt)
    {
        // Called only after the owning service durably inserted PLANNED and fenced native work.
        for (int id : receipt.recipes())
        {
            if (player.hasRecipeList(id)) { throw new IllegalStateException("Craft planned preimage changed; no replay."); }
            player.registerDwarvenRecipeList(RecipeData.getInstance().getRecipeList(id), true);
        }
        // Ordinary recipe INSERT is already durable. Its caller owns an ACTION ticket;
        // storeMe would self-drain that ticket. Dedicated merchants have no native work owner.
        if (receipt.source().equals("OWNER_PROVISIONED_CRAFT")) { player.storeMe(); }
        verify(player, receipt.state("READY"));
    }
    public static void inconsistent(PhantomProfileRepository profiles, PhantomProfileComponent intent, Receipt receipt)
    {
        profiles.updateComponent(intent.profileId(), intent.componentType(), intent.rowVersion(), 1, receipt.state("INCONSISTENT").encode());
    }
}
