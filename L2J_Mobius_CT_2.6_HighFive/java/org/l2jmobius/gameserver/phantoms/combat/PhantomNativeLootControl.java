/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;

/** Bounded native collection within the existing AutoPlay command lease, never an independent loop. */
public final class PhantomNativeLootControl
{
	private final Map<Integer, Long> _refusedUntil = new LinkedHashMap<>();
	private int _objectId, _itemId, _attempts;
	private long _count, _baseline, _deadline, _nextSeries;
	private boolean _sweeping;
	private long _acquired, _consumed, _swept;

	public static boolean eligible(Player player, Item item, int range)
	{
		return item.isSpawned() && item.getItemLocation() == ItemLocation.VOID && item.getInstanceId() == player.getInstanceId()
			&& player.calculateDistance2D(item) <= range && Math.abs(player.getZ() - item.getZ()) <= 128
			&& !AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(item.getId()) && item.getDropProtection().tryPickUp(player)
			&& (item.getOwnerId() == 0 || item.getOwnerId() == player.getObjectId() || player.isInLooterParty(item.getOwnerId()))
			&& player.getInventory().validateCapacity(item) && player.getInventory().validateWeightByItemId(item.getId(), item.getCount());
	}

	/** Selection only; stock PlayerAI owns the actual path, final protection and inventory mutation. */
	public boolean pending(Player player, long now)
	{
		if (threatened(player)) { return false; }
		if (_objectId != 0) { return true; }
		if (now < _nextSeries) { return false; }
		return corpse(player, now) != null || loot(player, now) != null;
	}

	public boolean tick(Player player, long now)
	{
		final var owner = player.getNativeWorkOwner();
		if (owner == null || !owner.isCurrent() || owner.player() != player || !owner.nativeObservationHealthy() || owner.evidence() == null) { return false; }
		if (threatened(player))
		{
			if (_objectId != 0 && player.getTarget() != null && player.getTarget().getObjectId() == _objectId && (player.getAI().getIntention() == Intention.PICK_UP || _sweeping && player.getAI().getIntention() == Intention.MOVE_TO)) { player.getAI().setIntention(Intention.IDLE); }
			_objectId = 0;
			return false;
		}
		if (player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isAttackingNow()) { return false; }
		if (player.getTarget() instanceof Creature target && !target.isAlikeDead() && (player.getAI().getIntention() == Intention.ATTACK || player.getAI().getIntention() == Intention.CAST)) { return false; }
		final var sensor = owner.evidence();
		if (_objectId != 0)
		{
			final WorldObject current = World.getInstance().findObject(_objectId);
			final var receipt = sensor.pickupReceipt(_objectId);
			if (!_sweeping && receipt != null && receipt.sequence() > _baseline && receipt.itemId() == _itemId && receipt.count() == _count)
			{
				if (receipt.nativeConsumed()) { _consumed++; } else { _acquired++; } _objectId = 0;
			}
			else if (_sweeping && sensor.sweepSequence(_objectId) > _baseline) { _swept++; _objectId = 0; }
			else if (now >= _deadline || current == null || !_sweeping && (!(current instanceof Item item) || !item.isSpawned()) || _sweeping && (!(current instanceof Monster body) || !body.isSweepActive()))
			{
				_refusedUntil.put(_objectId, now + 5_000_000_000L);
				if (player.getTarget() != null && player.getTarget().getObjectId() == _objectId && (player.getAI().getIntention() == Intention.PICK_UP || _sweeping && player.getAI().getIntention() == Intention.MOVE_TO)) { player.getAI().setIntention(Intention.IDLE); }
				_objectId = 0;
			}
			else
			{
				if (_sweeping && current instanceof Monster body && player.calculateDistance2D(body) <= 40 && player.getKnownSkill(42) != null)
				{
					player.setTarget(body); player.useMagic(player.getKnownSkill(42), false, false);
				}
				return true;
			}
		}
		if (_attempts >= AutoPlayConfig.PHANTOM_PICKUP_BATCH_SIZE) { _attempts = 0; _nextSeries = now + 1_000_000_000L; return false; }
		if (now < _nextSeries) { return false; }
		_refusedUntil.entrySet().removeIf(entry -> now >= entry.getValue());
		while (_refusedUntil.size() > 32) { final var oldest = _refusedUntil.keySet().iterator(); oldest.next(); oldest.remove(); }
		final Monster body = corpse(player, now);
		if (body != null)
		{
			if (player.isSitting()) { player.standUp(); return true; }
			_objectId = body.getObjectId(); _sweeping = true; _baseline = sensor.sweepSequence(_objectId); _deadline = now + 8_000_000_000L; _attempts++;
			player.setTarget(body);
			if (player.calculateDistance2D(body) > 40) { player.getAI().setIntention(Intention.MOVE_TO, body); }
			else { player.useMagic(player.getKnownSkill(42), false, false); }
			return true;
		}
		final Item item = loot(player, now);
		if (item == null) { _attempts = 0; return false; }
		if (player.isSitting()) { player.standUp(); return true; }
		_objectId = item.getObjectId(); _itemId = item.getId(); _count = item.getCount(); _sweeping = false;
		_baseline = sensor.pickupSequence(); _deadline = now + 8_000_000_000L; _attempts++;
		player.setTarget(item); player.getAI().setIntention(Intention.PICK_UP, item);
		return true;
	}

	private Monster corpse(Player player, long now)
	{
		final var skill = player.getKnownSkill(42);
		if (skill == null || player.isSkillDisabled(skill) || player.getCurrentMp() < player.getStat().getMpConsume(skill) + player.getStat().getMpInitialConsume(skill)) { return null; }
		return World.getInstance().getVisibleObjectsInRange(player, Monster.class, AutoPlayConfig.PHANTOM_PICKUP_RANGE).stream().limit(64)
			.filter(body -> body.isDead() && !body.isRaid() && body.getInstanceId() == player.getInstanceId() && Math.abs(player.getZ() - body.getZ()) <= 128 && body.isSweepActive() && body.checkSpoilOwner(player, false) && now >= _refusedUntil.getOrDefault(body.getObjectId(), 0L))
			.min(Comparator.comparingDouble((Monster body) -> player.calculateDistance2D(body)).thenComparingInt(Monster::getObjectId)).orElse(null);
	}

	private Item loot(Player player, long now)
	{
		return World.getInstance().getVisibleObjectsInRange(player, Item.class, AutoPlayConfig.PHANTOM_PICKUP_RANGE).stream().limit(64)
			.filter(item -> now >= _refusedUntil.getOrDefault(item.getObjectId(), 0L) && eligible(player, item, AutoPlayConfig.PHANTOM_PICKUP_RANGE))
			.max(Comparator.comparingDouble((Item item) -> Math.max(1, Math.min(1_000_000, item.getReferencePrice())) * Math.min(1000, item.getCount()) / (50 + player.calculateDistance2D(item))).thenComparingInt(item -> -item.getObjectId())).orElse(null);
	}

	public static Creature threat(Player player)
	{
		final Creature known = player.getAttackByList().stream().limit(32).filter(creature -> !creature.isAlikeDead() && creature.getInstanceId() == player.getInstanceId() && org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.targetsForCombat(creature, player)).findFirst().orElse(null);
		if (known != null) { return known; }
		return World.getInstance().getVisibleObjectsInRange(player, Creature.class, AutoPlayConfig.PHANTOM_PICKUP_RANGE).stream()
			.filter(creature -> creature.isAttackable() && !creature.isAlikeDead() && creature.getInstanceId() == player.getInstanceId() && org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.targetsForCombat(creature, player)).findFirst().orElse(null);
	}
	private static boolean threatened(Player player) { return threat(player) != null; }

	public Map<String, String> snapshot() { return Map.of("loot.pending", Integer.toString(_objectId), "loot.acquired", Long.toString(_acquired), "loot.consumed", Long.toString(_consumed), "loot.swept", Long.toString(_swept), "loot.refusals", Integer.toString(_refusedUntil.size())); }
}
