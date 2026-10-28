/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** One-shot owner-authorized qualification; never changes stock, capital or Role v1. */
final class PhantomMerchantCraftProvision
{
    static final String FILE = "config/Custom/PhantomMerchantCrafting.ini";
    private final PhantomProfileRepository _profiles;
    private final PhantomNativeCraftRecipes _recipes;
    private final int _quota;
    private final java.util.Set<Integer> _verified = new java.util.HashSet<>();
    static PhantomMerchantCraftProvision load(Path file, PhantomProfileRepository profiles, PhantomPrivateMarketPricingAuthority pricing)
    {
        if (!Files.isRegularFile(file)) { return null; }
        final var config = new ConfigReader(file.toString());
        if (!"True".equals(config.getValue("EnableMerchantCraftProvision"))) { return null; }
        final int quota = Integer.parseInt(config.getValue("MerchantCraftLearnedRecipes"));
        if (quota < 4 || quota > 12) { throw new IllegalArgumentException("Merchant craft quota outside4..12."); }
        return new PhantomMerchantCraftProvision(profiles, pricing, quota);
    }
    private PhantomMerchantCraftProvision(PhantomProfileRepository profiles, PhantomPrivateMarketPricingAuthority pricing, int quota) { _profiles = profiles; _recipes = new PhantomNativeCraftRecipes(pricing); _quota = quota; }

    void qualify(long serverProfile, PhantomMerchantBootstrap.Role role, Player player, Lease lease)
    {
        if (role.storeKind() != 2 || role.classId() != 53 || !role.account().startsWith("phm_")) { return; }
        if (_verified.contains(player.getObjectId())) { return; }
        if (role.stage() != PhantomMerchantBootstrap.Stage.READY || !role.account().equals(player.getAccountName()) || !role.name().equals(player.getName()) || role.characterId() != player.getObjectId()
            || lease.objectId() != player.getObjectId() || lease.ownerKind() != PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM
            || PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId()) == null || PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId()).token() != lease.token()
            || player.getAccessLevel().getLevel() != 0 || player.getClient() != null || player.hasHeadlessOutboundSession() || player.getNativeWorkOwner() != null || player.isInParty() || player.getClanId() != 0 || player.getClassIndex() != 0
            || _profiles.findByCharacterObjectId(player.getObjectId()).isPresent()) { throw new IllegalStateException("Merchant craft owner differs."); }
        try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT a.password,a.accessLevel,c.charId FROM accounts a JOIN characters c ON c.account_name=a.login WHERE a.login=?"))
        {
            query.setString(1, role.account());
            try (var rows = query.executeQuery()) { if (!rows.next() || !role.token().equals(rows.getString(1)) || rows.getInt(2) != -1 || rows.getInt(3) != role.characterId() || rows.next()) { throw new IllegalStateException("Merchant craft exact disabled account/character differs."); } }
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("Merchant craft authority unavailable.", failure); }
        final String component = "merchant.craft.qualified.v1." + role.ordinal();
        final var previous = _profiles.findComponent(serverProfile, component).orElse(null);
        if (previous != null)
        {
            final var receipt = Receipt.decode(previous);
            if (!receipt.source().equals("OWNER_PROVISIONED_CRAFT")) { throw new IllegalStateException("Merchant craft receipt source differs."); }
            _recipes.verify(player, receipt); _verified.add(player.getObjectId()); return;
        }
        // Existing Human/Elf MFG intents return above. Even a dwarf is promoted only from an exact pristine class/skill preimage.
        // Player.create sets the dwarf template/base, but activeClass is initialized only by load/class change.
        if (player.getPlayerClass() != PlayerClass.DWARVEN_FIGHTER || player.getBaseClass() != 53 || player.getLevel() != 1 || !player.getDwarvenRecipeBook().isEmpty() || !player.getCommonRecipeBook().isEmpty()
            || player.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) > 0 || player.getSkillLevel(CommonSkill.CREATE_COMMON.getId()) > 0) { throw new IllegalStateException("Merchant craft preimage ambiguous; no grant."); }
        final int tier = Math.floorMod(role.ordinal(), 4);
        final int level = new int[] {20, 43, 55, 76}[tier], classId = tier == 0 ? 56 : tier == 3 ? 118 : 57;
        if (PlayerClass.ARTISAN.getParent() != PlayerClass.DWARVEN_FIGHTER || PlayerClass.WARSMITH.getParent() != PlayerClass.ARTISAN || PlayerClass.MAESTRO.getParent() != PlayerClass.WARSMITH) { throw new IllegalStateException("Native dwarf lineage changed."); }
        final int dwarven = PhantomNativeCraftRecipes.sourceRank(classId, level, CommonSkill.CREATE_DWARVEN.getId()), common = PhantomNativeCraftRecipes.sourceRank(classId, level, CommonSkill.CREATE_COMMON.getId());
        final List<Integer> ids = _recipes.select(player, dwarven, role.ordinal() * (long) _quota, _quota);
        if (ids.size() < 4) { throw new IllegalStateException("Insufficient source-priced recipes; no qualification."); }
        final var receipt = _recipes.plan("OWNER_PROVISIONED_CRAFT", player, classId, level, dwarven, common, ids);
        synchronized (player.getSellList())
        {
            synchronized (player.getBuyList())
            {
                synchronized (player.getManufactureItems())
                {
                    if (player.isAlikeDead() || player.isCrafting() || RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore() || player.isChangingClass()) { return; }
                    final var intent = _profiles.insertComponent(serverProfile, component, 1, receipt.encode());
                    try
                    {
                        player.getSellList().clear(); player.getBuyList().clear(); player.getManufactureItems().clear(); player.setPrivateStoreType(PrivateStoreType.NONE);
                        if (player.isSpawned()) { OfflineTraderTable.getInstance().onTransaction(player, true, false); }
                        // At level1 the native class writer cannot auto-learn high-rank combat skills.
                        player.setPlayerClass(56);
                        if (classId != 56) { player.setPlayerClass(57); }
                        if (classId == 118) { player.setPlayerClass(118); }
                        player.setBaseClass(classId); player.getStat().setLevel((byte) level); player.setExp(player.getStat().getExpForLevel(level));
                        for (var skill : List.of(CommonSkill.CREATE_DWARVEN, CommonSkill.CREATE_COMMON))
                        {
                            final int rank = skill == CommonSkill.CREATE_DWARVEN ? dwarven : common;
                            if (rank > 0) { final var nativeSkill = SkillData.getInstance().getSkill(skill.getId(), rank); if (nativeSkill == null) { throw new IllegalStateException("Native craft skill absent."); } player.addSkill(nativeSkill, true); }
                        }
                        player.getStatus().setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
                        _recipes.learn(player, receipt);
                        _profiles.updateComponent(serverProfile, component, intent.rowVersion(), 1, receipt.state("READY").encode());
                        _verified.add(player.getObjectId());
                    }
                    catch (RuntimeException uncertain) { PhantomNativeCraftRecipes.inconsistent(_profiles, intent, receipt); throw uncertain; }
                }
            }
        }
    }
}
