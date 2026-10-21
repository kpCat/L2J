/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;

/** Ephemeral preferences owned by an existing native action/session; Creature owns all recharge timers. */
public final class PhantomNativeShotControl implements AutoCloseable
{
	private final Player _player;
	private final Set<Integer> _added = new HashSet<>();
	private final Set<Integer> _suspended = new HashSet<>();
	private int _weaponObjectId;
	private boolean _closed;

	public PhantomNativeShotControl(Player player) { _player = java.util.Objects.requireNonNull(player); }

	public synchronized ShotOutcome activate(boolean magic)
	{
		try
		{
			final var weapon = _player.getActiveWeaponItem(); final var equipped = _player.getActiveWeaponInstance();
			if (_closed || weapon == null || equipped == null || weapon == _player.getFistsWeaponItem()) { return ShotOutcome.UNAVAILABLE; }
			if (_weaponObjectId != equipped.getObjectId()) { release(); _weaponObjectId = equipped.getObjectId(); }
			final int perHit = magic ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
			if (perHit <= 0) { return ShotOutcome.UNAVAILABLE; }
			final ActionType action = magic ? ActionType.SPIRITSHOT : ActionType.SOULSHOT;
			final Item selected = select(_player, weapon, magic);
			if (selected == null) { return charged(magic) ? ShotOutcome.ACTIVATED : ShotOutcome.UNAVAILABLE; }
			final boolean preferredCharged = magic && selected.getEtcItem().isBlessed() ? _player.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) : charged(magic);
			if (!preferredCharged && !(ItemHandler.getInstance().getHandler(selected.getEtcItem()).onItemUse(_player, selected, false) && charged(magic)))
			{
				return charged(magic) ? ShotOutcome.ACTIVATED : ShotOutcome.FAILED;
			}
			for (int active : List.copyOf(_player.getAutoSoulShot()))
			{
				if (active == selected.getId()) { continue; }
				final var template = ItemData.getInstance().getTemplate(active);
				if (template != null && template.getDefaultAction() == action && _player.removeAutoSoulShot(active))
				{
					if (!_added.remove(active)) { _suspended.add(active); }
				}
			}
			if (!_player.getAutoSoulShot().contains(selected.getId())) { _player.addAutoSoulShot(selected.getId()); _added.add(selected.getId()); }
			return ShotOutcome.ACTIVATED;
		}
		catch (RuntimeException failure)
		{
			final var owner = _player.getNativeWorkOwner();
			if (owner != null && owner.isCurrent()) { owner.recordFailure(failure); }
			return ShotOutcome.FAILED;
		}
	}

	private static boolean preferred(Item candidate, Item selected, boolean magic)
	{
		if (magic && candidate.getEtcItem().isBlessed() != selected.getEtcItem().isBlessed()) { return candidate.getEtcItem().isBlessed(); }
		return candidate.getId() < selected.getId() || candidate.getId() == selected.getId() && candidate.getObjectId() < selected.getObjectId();
	}

	private static Item select(Player player, org.l2jmobius.gameserver.model.item.Weapon weapon, boolean magic)
	{
		final int count = magic ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		final ActionType action = magic ? ActionType.SPIRITSHOT : ActionType.SOULSHOT;
		Item selected = null;
		// Shared linear selection, without sorting or a parallel item catalog.
		for (var item : player.getInventory().getItems())
		{
			if (count <= 0 || item.getCount() < count || item.getEtcItem() == null || item.getTemplate().getDefaultAction() != action || item.getTemplate().getCrystalType() != weapon.getCrystalTypePlus() || !player.getInventory().canManipulateWithItemId(item.getId()) || ItemHandler.getInstance().getHandler(item.getEtcItem()) == null) { continue; }
			if (selected == null || preferred(item, selected, magic)) { selected = item; }
		}
		return selected;
	}

	/** Optional paid charge at the native boundary. Missing stock differs from a rejected/failed mutation. */
	public static ShotOutcome chargeOffensive(org.l2jmobius.gameserver.model.actor.Creature actor, Player owner, boolean magic)
	{
		if (actor instanceof org.l2jmobius.gameserver.model.actor.Summon summon && (owner.getSummon() != summon || summon.isAlikeDead())) { return ShotOutcome.FAILED; }
		if (charged(actor, magic)) { return ShotOutcome.ACTIVATED; }
		try
		{
			if (actor instanceof org.l2jmobius.gameserver.model.actor.Summon summon)
			{
				if (owner.getSummon() != summon || summon.isAlikeDead()) { return ShotOutcome.FAILED; }
				// Native H5 handlers consume existing owner stock for pets and servitors alike.
				final int id = magic ? 6647 : 6645;
				final int perHit = magic ? summon.getSpiritShotsPerHit() : summon.getSoulShotsPerHit();
				if (perHit <= 0) { return ShotOutcome.UNAVAILABLE; }
				if (perHit > 100) { return ShotOutcome.FAILED; }
				final var template = ItemData.getInstance().getTemplate(id);
				if (template == null || template.getDefaultAction() != (magic ? ActionType.SUMMON_SPIRITSHOT : ActionType.SUMMON_SOULSHOT)) { return ShotOutcome.FAILED; }
				final var item = owner.getInventory().getItemByItemId(id);
				if (item == null || item.getCount() < perHit) { return ShotOutcome.UNAVAILABLE; }
				final var handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
				return handler != null && handler.onItemUse(owner, item, false) && charged(actor, magic) ? ShotOutcome.ACTIVATED : ShotOutcome.FAILED;
			}
			final var weapon = owner.getActiveWeaponItem();
			if (actor != owner) { return ShotOutcome.FAILED; }
			if (weapon == null || owner.getActiveWeaponInstance() == null || weapon == owner.getFistsWeaponItem()) { return ShotOutcome.UNAVAILABLE; }
			final Item selected = select(owner, weapon, magic);
			if (selected == null) { return ShotOutcome.UNAVAILABLE; }
			return ItemHandler.getInstance().getHandler(selected.getEtcItem()).onItemUse(owner, selected, false) && charged(actor, magic) ? ShotOutcome.ACTIVATED : ShotOutcome.FAILED;
		}
		catch (RuntimeException failure)
		{
			final var nativeOwner = owner.getNativeWorkOwner();
			if (nativeOwner != null && nativeOwner.isCurrent()) { nativeOwner.recordFailure(failure); }
			return ShotOutcome.FAILED;
		}
	}

	private static boolean charged(org.l2jmobius.gameserver.model.actor.Creature actor, boolean magic)
	{
		return magic ? actor.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) || actor.isChargedShot(ShotType.SPIRITSHOTS) : actor.isChargedShot(ShotType.SOULSHOTS);
	}

	private boolean charged(boolean magic)
	{
		return magic ? _player.isChargedShot(ShotType.SPIRITSHOTS) || _player.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) : _player.isChargedShot(ShotType.SOULSHOTS);
	}

	private void release()
	{
		for (int itemId : _added) { _player.removeAutoSoulShot(itemId); }
		for (int itemId : _suspended) { if (_player.getInventory().getInventoryItemCount(itemId, -1) > 0) { _player.addAutoSoulShot(itemId); } }
		_added.clear(); _suspended.clear();
	}

	@Override public synchronized void close() { if (!_closed) { _closed = true; release(); } }
}
