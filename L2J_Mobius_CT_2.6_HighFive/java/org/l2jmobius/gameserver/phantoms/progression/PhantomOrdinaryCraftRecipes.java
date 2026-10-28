/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt;

/** Recipes only: no EXP/SP/class/skill/item writer. Invoked at native admission or paid learning. */
public final class PhantomOrdinaryCraftRecipes
{
    public static final String FILE = "config/Custom/PhantomOrdinaryCraftRecipes.ini";
    private static final String PREFIX = "ordinary.craft.recipe.v1.";
    private static final Logger LOGGER = Logger.getLogger(PhantomOrdinaryCraftRecipes.class.getName());
    private final PhantomProfileRepository _profiles;
    private final PhantomMaterializationService _materialization;
    private final PhantomNativeCraftRecipes _recipes;
    private final int _perRank, _lifetime;
    private final Set<String> _seen = new HashSet<>();
    public static PhantomOrdinaryCraftRecipes load(Path file, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomPrivateMarketPricingAuthority pricing)
    {
        if (!Files.isRegularFile(file)) { return null; }
        final var config = new ConfigReader(file.toString());
        if (!"True".equals(config.getValue("EnableOrdinaryCraftRecipeReward"))) { return null; }
        final int perRank = Integer.parseInt(config.getValue("OrdinaryCraftRecipesPerRank")), lifetime = Integer.parseInt(config.getValue("OrdinaryCraftRecipesLifetime"));
        if (perRank < 2 || perRank > 4 || lifetime < perRank || lifetime > 20) { throw new IllegalArgumentException("Ordinary craft recipe quota outside bounds."); }
        return new PhantomOrdinaryCraftRecipes(profiles, materialization, pricing, perRank, lifetime);
    }
    private PhantomOrdinaryCraftRecipes(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomPrivateMarketPricingAuthority pricing, int perRank, int lifetime)
    { _profiles = profiles; _materialization = materialization; _recipes = new PhantomNativeCraftRecipes(pricing); _perRank = perRank; _lifetime = lifetime; }

    public synchronized void catchUp(long profileId)
    {
        try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
        {
            if (action == null) { return; }
            final var player = action.player();
            if (!PhantomCraftRecipePolicy.creator(player.getActiveClass()) || player.getAccessLevel().getLevel() != 0 || player.getClient() != null || !player.hasHeadlessOutboundSession() || player.getAccountName().startsWith("phm_")
                || PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) != PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM || player.isAlikeDead() || player.isInCombat() || player.isInParty() || player.isTeleporting() || player.isChangingClass() || player.isTransformed()
                || player.getPrivateStoreType() != PrivateStoreType.NONE || player.isCrafting() || RecipeManager.getInstance().isMakingItem(player) || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { return; }
            final int rank = player.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()), common = Math.max(0, player.getSkillLevel(CommonSkill.CREATE_COMMON.getId()));
            if (rank < 1 || rank > 10 || player.getLevel() < 20 || PhantomNativeCraftRecipes.sourceRank(player.getActiveClass(), player.getLevel(), CommonSkill.CREATE_DWARVEN.getId()) < rank) { return; }
            final String key = profileId + ":" + player.getObjectId() + ':' + player.getClassIndex() + ':' + player.getActiveClass() + ':' + player.getLevel() + ':' + rank + ':' + common;
            if (_seen.contains(key)) { return; }
            final var profile = _profiles.findByCharacterObjectId(player.getObjectId()).orElse(null);
            if (profile == null || profile.profileId() != profileId) { return; }
            final String component = PREFIX + player.getClassIndex() + '.' + rank;
            final var previous = _profiles.findComponent(profileId, component).orElse(null);
            if (previous != null)
            {
                final var receipt = Receipt.decode(previous);
                if (!receipt.source().equals("PROGRESSION_RECIPE_BONUS")) { throw new IllegalStateException("Ordinary recipe source differs."); }
                _recipes.verify(player, receipt); _seen.add(key); return;
            }
            // Every previous intent, including an uncertain one, consumes its lifetime reservation.
            // A classIndex switch never creates another lifetime budget.
            int issued = 0;
            for (var saved : _profiles.listComponents(profileId))
            {
                if (!saved.componentType().startsWith(PREFIX)) { continue; }
                final var receipt = Receipt.decode(saved);
                if (!receipt.state().equals("READY")) { throw new IllegalStateException("INCONSISTENT ordinary recipe intent; no replay."); }
                issued += receipt.recipes().size();
            }
            final int quota = Math.min(_perRank, _lifetime - issued);
            if (quota <= 0) { _seen.add(key); return; }
            final var ids = _recipes.select(player, rank, profileId * _perRank, quota);
            if (ids.isEmpty()) { _seen.add(key); return; }
            final var receipt = _recipes.plan("PROGRESSION_RECIPE_BONUS", player, player.getActiveClass(), player.getLevel(), rank, common, ids);
            // A just-earned level/skill may not yet have reached its existing checkpoint.
            // Defer that preimage to the next admission instead of sealing a failed reward.
            try { _recipes.verifyQualification(player, receipt); }
            catch (IllegalStateException notPersisted) { return; }
            final var intent = _profiles.insertComponent(profileId, component, 1, receipt.encode());
            try
            {
                _recipes.learn(player, receipt);
                _profiles.updateComponent(profileId, component, intent.rowVersion(), 1, receipt.state("READY").encode());
                _seen.add(key);
                LOGGER.info("PHANTOM_RECIPE_PROGRESS_REWARD profile=" + profileId + ";source=PROGRESSION_RECIPE_BONUS;recipes=" + ids);
            }
            catch (RuntimeException uncertain) { PhantomNativeCraftRecipes.inconsistent(_profiles, intent, receipt); throw uncertain; }
        }
        catch (RuntimeException denied)
        {
            // Admission/progression continues, but an uncertain reward is never retried as a new grant.
            LOGGER.log(Level.WARNING, "Ordinary native recipe reward held profile=" + profileId, denied);
        }
    }
}
