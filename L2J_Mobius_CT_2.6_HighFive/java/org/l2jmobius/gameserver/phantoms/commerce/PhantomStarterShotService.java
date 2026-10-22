/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Materialized native writer for a birth-only subsidy. No background or ordinary-player minting. */
public final class PhantomStarterShotService
{
	private static final String AVAILABLE = "phantom.starter.shots.available";
	private static final String PROTECTED = "phantom.starter.shots.protected";
	private PhantomStarterShotService() { }

	public static boolean protectedStock(Player player, int itemId)
	{
		return player != null && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope && PhantomStarterShotBudget.ITEMS.contains(itemId) && player.getVariables().getBoolean(PROTECTED, false);
	}

	public static void afterLoad(PhantomProfileRepository profiles, long profileId, Player player)
	{
		if (profiles == null || !(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) || !owner.isCurrent()) { return; }
		final var profile = profiles.find(profileId).orElseThrow();
		if (profile.characterObjectId() == null || profile.characterObjectId() != player.getObjectId()) { return; }
		player.getVariables().set(AVAILABLE, false);
		final var component = profiles.findComponent(profileId, PhantomStarterShotBudget.COMPONENT).orElse(null);
		if (component == null) { return; }
		if (component.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown starter entitlement version."); }
		final var budget = PhantomStarterShotBudget.decode(component.payload());
		// Protect all three fungible No-grade pools conservatively, including previously issued stock after restart.
		player.getVariables().set(PROTECTED, true);
		if (budget.state() == PhantomStarterShotBudget.State.PENDING)
		{
			profiles.updateComponent(profileId, PhantomStarterShotBudget.COMPONENT, component.rowVersion(), 1, budget.inconsistent().encode());
			return;
		}
		player.getVariables().set(AVAILABLE, budget.state() == PhantomStarterShotBudget.State.READY && budget.issued() < budget.limit()
			&& (budget.issued() > 0 || player.getLevel() < 20 && player.getPlayerClass().level() == 0));
	}

	public static void grantIfNeeded(PhantomProfileRepository profiles, long profileId, Player player)
	{
		if (profiles == null || PhantomPlayersConfig.settings().starterShotLifetimeLimit() == 0 || !player.getVariables().getBoolean(AVAILABLE, false)
			|| !(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) || !owner.isCurrent() || !owner.open() || !owner.nativeObservationHealthy()
			|| !player.hasHeadlessOutboundSession() || player.getClient() != null || player.isGM() || player.isAlikeDead() || player.hasPendingOwnedStore()
			|| player.isInStoreMode() || player.isProcessingTransaction() || player.isAttackingNow() || player.isCastingNow()
			|| player.isInCombat() && player.getTarget() instanceof org.l2jmobius.gameserver.model.actor.Creature target && !target.isAlikeDead()
			|| PlayerNativeWork.current(owner) != null) { return; }
		final int candidateItem = compatibleItemId(player);
		if (candidateItem == 0 || player.getInventory().getInventoryItemCount(candidateItem, -1) >= 100) { return; }
		owner.checkpoint(() ->
		{
			final var profile = profiles.find(profileId).orElseThrow();
			if (profile.characterObjectId() == null || profile.characterObjectId() != player.getObjectId() || !owner.isCurrent() || !owner.nativeObservationHealthy()) { return false; }
			// Already admitted actions may finish while the checkpoint drains. Select from the sealed final loadout.
			if (!player.hasHeadlessOutboundSession() || player.getClient() != null || player.isGM() || player.isAlikeDead() || player.hasPendingOwnedStore()
				|| player.isInStoreMode() || player.isProcessingTransaction() || player.isAttackingNow() || player.isCastingNow()
				|| player.isInCombat() && player.getTarget() instanceof org.l2jmobius.gameserver.model.actor.Creature target && !target.isAlikeDead()) { return false; }
			final int itemId = compatibleItemId(player);
			if (itemId == 0) { return false; }
			final boolean magic = itemId == 3947;
			final var template = ItemData.getInstance().getTemplate(itemId);
			if (player.getInventory().getInventoryItemCount(itemId, -1) >= 100) { return false; }
			final var catchup = profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
			if (catchup != null)
			{
				if (catchup.componentSchemaVersion() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.SCHEMA_VERSION) { throw new IllegalStateException("Unknown historical catch-up version."); }
				// Historical baseline/native-context handoff owns its exact preimage until normal operation is permitted.
				if (new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().decode(catchup.payload()).blocksNormalOperation()) { return false; }
			}
			final var component = profiles.findComponent(profileId, PhantomStarterShotBudget.COMPONENT).orElseThrow();
			if (component.componentSchemaVersion() != 1) { throw new IllegalStateException("Unknown starter entitlement version."); }
			final var budget = PhantomStarterShotBudget.decode(component.payload());
			if (budget.issued() == 0 && (player.getLevel() >= 20 || player.getPlayerClass().level() != 0)) { player.getVariables().set(AVAILABLE, false); return false; }
			final long before = player.getInventory().getInventoryItemCount(itemId, -1);
			final long compatibleStock = magic ? Math.addExact(before, player.getInventory().getInventoryItemCount(2509, -1)) : before;
			final var pending = budget.reserve(itemId, compatibleStock);
			if (pending.equals(budget)) { return false; }
			final long weight = Math.multiplyExact((long) template.getWeight(), pending.pending());
			if (!player.getInventory().validateWeight(weight) || player.getInventory().getItemByItemId(itemId) == null && !player.getInventory().validateCapacity(1)
				|| (long) player.getCurrentLoad() + weight >= (long) (player.getMaxLoad() * .50)) { return false; }
			try (var claim = PhantomEconomyConflictPort.claim(profileId, null, List.of(
				new Reservation(profileId, player.getObjectId(), player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, itemId, pending.pending(), before, 0, "INVENTORY"),
				new Reservation(profileId, player.getObjectId(), player.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 1, player.getInventory().getSize(), 0, "INVENTORY"))))
			{
				if (!claim.acquired()) { return false; }
				// CAS commits the debit first. Any later uncertainty retains PENDING/INCONSISTENT, without compensation or replay.
				final var debit = profiles.updateComponent(profileId, PhantomStarterShotBudget.COMPONENT, component.rowVersion(), 1, pending.encode());
				try
				{
					final var item = player.getInventory().addItem(ItemProcessType.REWARD, itemId, pending.pending(), player, "STARTER_SUBSIDY");
					player.getInventory().updateDatabase();
					final long expected = Math.addExact(before, pending.pending());
					if (item == null || player.getInventory().getInventoryItemCount(itemId, -1) != expected) { throw new IllegalStateException("Starter native inventory delta differs."); }
					try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY'"))
					{
						query.setInt(1, player.getObjectId()); query.setInt(2, itemId);
						try (var rows = query.executeQuery()) { if (!rows.next() || rows.getLong(1) != expected) { throw new IllegalStateException("Starter durable inventory delta differs."); } }
					}
					profiles.updateComponent(profileId, PhantomStarterShotBudget.COMPONENT, debit.rowVersion(), 1, pending.committed().encode());
					player.getVariables().set(AVAILABLE, pending.issued() < pending.limit());
					player.refreshOverloaded();
					return true;
				}
				catch (java.sql.SQLException | RuntimeException failure)
				{
					player.getVariables().set(AVAILABLE, false);
					owner.recordCheckpointFailure(failure); owner.recordFailure(failure);
					throw new IllegalStateException("STARTER_SUBSIDY unresolved; no credit replay.", failure);
				}
			}
		});
	}

	private static int compatibleItemId(Player player)
	{
		final var weapon = player.getActiveWeaponItem();
		if (weapon == null || player.getActiveWeaponInstance() == null || player.getActiveWeaponInstance().getEnchantLevel() > 0 || weapon == player.getFistsWeaponItem() || weapon.getCrystalTypePlus().getLevel() != 0) { return 0; }
		final boolean magic = player.getAllSkills().stream().anyMatch(skill -> !skill.isPassive() && skill.isMagic() && skill.isDamage()) && weapon.getSpiritShotCount() > 0;
		final int itemId = magic ? 3947 : 1835;
		return L2jPhantomSupplyJourney.compatible(player, ItemData.getInstance().getTemplate(itemId)) && (magic ? weapon.getSpiritShotCount() : weapon.getSoulShotCount()) > 0 ? itemId : 0;
	}
}
