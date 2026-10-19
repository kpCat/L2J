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
			Item selected = null;
			// One linear inventory pass; no per-pulse sort or second supply catalog.
			for (var item : _player.getInventory().getItems())
			{
				if (item.getEtcItem() == null || item.getCount() < perHit || item.getTemplate().getDefaultAction() != action
					|| item.getTemplate().getCrystalType() != weapon.getCrystalTypePlus() || !_player.getInventory().canManipulateWithItemId(item.getId())
					|| ItemHandler.getInstance().getHandler(item.getEtcItem()) == null) { continue; }
				if (selected == null || preferred(item, selected, magic)) { selected = item; }
			}
			if (selected == null) { return charged(magic) ? ShotOutcome.ACTIVATED : ShotOutcome.UNAVAILABLE; }
			final boolean preferredCharged = magic && selected.getEtcItem().isBlessed() ? _player.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) : charged(magic);
			if (!preferredCharged && !(ItemHandler.getInstance().getHandler(selected.getEtcItem()).onItemUse(_player, selected, false) && charged(magic)))
			{
				return charged(magic) ? ShotOutcome.ACTIVATED : ShotOutcome.UNAVAILABLE;
			}
			for (int active : List.copyOf(_player.getAutoSoulShot()))
			{
				if (active == selected.getId()) { continue; }
				final var template = ItemData.getInstance().getTemplate(active);
				if (template != null && (template.getDefaultAction() == ActionType.SOULSHOT || template.getDefaultAction() == ActionType.SPIRITSHOT) && _player.removeAutoSoulShot(active))
				{
					if (!_added.remove(active)) { _suspended.add(active); }
				}
			}
			if (!_player.getAutoSoulShot().contains(selected.getId())) { _player.addAutoSoulShot(selected.getId()); _added.add(selected.getId()); }
			return ShotOutcome.ACTIVATED;
		}
		catch (RuntimeException failure) { return ShotOutcome.FAILED; }
	}

	private static boolean preferred(Item candidate, Item selected, boolean magic)
	{
		if (magic && candidate.getEtcItem().isBlessed() != selected.getEtcItem().isBlessed()) { return candidate.getEtcItem().isBlessed(); }
		return candidate.getId() < selected.getId() || candidate.getId() == selected.getId() && candidate.getObjectId() < selected.getObjectId();
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
