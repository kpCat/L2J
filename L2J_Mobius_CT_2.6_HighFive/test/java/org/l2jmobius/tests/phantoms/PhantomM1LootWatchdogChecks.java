/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemAdd;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Actual stock producer checks; each public method requires a fresh guarded native fixture. */
public final class PhantomM1LootWatchdogChecks
{
	private PhantomM1LootWatchdogChecks() { }

	public static void pickupAndReload(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, PhantomBackgroundTransaction transaction, int distance, int itemId) throws Exception
	{
		final Player player = player(materialization, profileId);
		PhantomAssertions.assertTrue((distance > 0) && (distance < 200), "Pickup fixture distance is outside stock range.");
		final var template = ItemData.getInstance().getTemplate(itemId);
		PhantomAssertions.assertTrue((template != null) && template.isStackable() && !template.hasExImmediateEffect(), "Pickup persistence fixture requires an actual ordinary stackable item.");
		final Item dropped = ground(player, itemId, 3, distance);
		final long beforeCount = count(player, itemId);
		final var before = evidence(materialization, profileId, player);
		final int startX = player.getX(); final int startY = player.getY();
		try
		{
			final double actualDistance = player.calculateDistance2D(dropped);
			PhantomAssertions.assertTrue(reachable(player, dropped) && (actualDistance < 200) && ((distance <= 70) ? actualDistance <= 70 : actualDistance > 70), "Native dropMe did not preserve the requested reachable distance class.");
			PhantomAssertions.assertTrue(player.isInventoryUnder90(false) && player.getInventory().validateCapacity(dropped) && player.getInventory().validateWeightByItemId(itemId, 3), "Pickup fixture native inventory gates reject this item.");
			PhantomAssertions.assertFalse(AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(itemId), "Lawful pickup fixture item is ignored by actual config.");
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Actual stock Pickup registration did not start.");
			await(() -> !dropped.isSpawned() && (count(player, itemId) == beforeCount + 3), 8000, "Stock scheduled Pickup did not acquire the actual ground item exactly once.");
			final var after = evidence(materialization, profileId, player);
			context.record("m1.loot.pickup." + distance, "groundRemoved=" + !dropped.isSpawned() + " nativeGain=" + (count(player, itemId) - beforeCount) + " sequence=" + after.lootSequence());
			PhantomAssertions.assertTrue((after.lootSequence() > before.lootSequence()) && (after.lootCount() - before.lootCount() == 3), "Actual successful native pickup lacks exact lifetime scalar loot evidence.");
			if (distance > 70) { PhantomAssertions.assertTrue(Math.hypot((long) player.getX() - startX, (long) player.getY() - startY) > 20, "Far native pickup succeeded without actual stock movement."); }
			quiesce(materialization, profileId, visible);
			persistAndReload(context, profileId, materialization, transaction, player, itemId, beforeCount + 3, PlayerNativeWork.checkpoint(player, () -> fullHash(player)));
		}
		finally { visible.stop(profileId); destroyGround(dropped); }
	}

	public static void autoLootAndReload(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomGoal goal, PhantomBackgroundTransaction transaction, Monster nativeTarget, int itemId) throws Exception
	{
		final Player player = player(materialization, profileId);
		validTarget(player, goal, nativeTarget);
		PhantomAssertions.assertFalse(player.isAutoPlaying() || player.isMoving(), "AutoLoot check requires a quiescent fresh native actor.");
		final var item = ItemData.getInstance().getTemplate(itemId);
		PhantomAssertions.assertTrue((item != null) && !item.hasExImmediateEffect() && item.isStackable(), "AutoLoot fixture requires an actual ordinary stackable item template.");
		final var source = nativeTarget.getTemplate();
		final StatSet facts = new StatSet();
		facts.set("id", source.getId()); facts.set("displayId", source.getDisplayId()); facts.set("level", source.getLevel()); facts.set("type", "Monster"); facts.set("name", source.getName());
		facts.set("baseHpMax", nativeTarget.getMaxHp()); facts.set("baseMpMax", nativeTarget.getMaxMp()); facts.set("collisionRadius", source.getCollisionRadius()); facts.set("collisionHeight", source.getCollisionHeight());
		final NpcTemplate isolatedDrop = new NpcTemplate(facts);
		isolatedDrop.setSkills(Map.of());
		isolatedDrop.addDrop(new DropHolder(DropType.DROP, itemId, 3, 3, 100));
		final long beforeCount = count(player, itemId);
		final var before = evidence(materialization, profileId, player);
		final int startX = player.getX(); final int startY = player.getY();
		final boolean previousAutoLoot = PlayerConfig.AUTO_LOOT;
		try
		{
			PlayerConfig.AUTO_LOOT = true;
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { nativeTarget.doItemDrop(isolatedDrop, action.player()); }
			final long gained = count(player, itemId) - beforeCount;
			final var after = evidence(materialization, profileId, player);
			context.record("m1.loot.autoLoot", "nativeGain=" + gained + " sequence=" + after.lootSequence() + " move=" + player.isMoving());
			PhantomAssertions.assertTrue(gained > 0, "Actual native AUTO_LOOT branch did not acquire an eligible stock reward.");
			PhantomAssertions.assertTrue((after.lootSequence() > before.lootSequence()) && (after.lootCount() - before.lootCount() == gained), "Actual native AUTO_LOOT gain lacks exact scalar evidence.");
			PhantomAssertions.assertTrue(!player.isMoving() && (player.getX() == startX) && (player.getY() == startY) && (player.getAI().getIntention() != Intention.MOVE_TO), "Native AUTO_LOOT moved toward a nonexistent ground drop.");
			persistAndReload(context, profileId, materialization, transaction, player, itemId, beforeCount + gained, PlayerNativeWork.checkpoint(player, () -> fullHash(player)));
		}
		finally { PlayerConfig.AUTO_LOOT = previousAutoLoot; }
	}

	public static void protectedAndIgnored(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, Player foreignOwner, Monster lawfulTarget, int ignoredItemId) throws Exception
	{
		final Player player = player(materialization, profileId);
		validTarget(player, goal, lawfulTarget);
		PhantomAssertions.assertTrue((foreignOwner != null) && (foreignOwner != player) && !player.isInParty() && !foreignOwner.isInParty(), "Protection fixture has no distinct unshared native owner.");
		PhantomAssertions.assertTrue(AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(ignoredItemId), "Ignored fixture is absent from actual stock ignored config.");
		final Item ignored = ground(player, ignoredItemId, 1, 20);
		// The protected control must have a non-ignored item ID to reach the stock owner gate.
		final int protectedId = player.getInventory().getItems().stream().mapToInt(Item::getId).filter(id -> !AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(id)).filter(id -> { final var template = ItemData.getInstance().getTemplate(id); return (template != null) && !template.hasExImmediateEffect(); }).findFirst().orElseThrow(() -> new AssertionError("No actual non-ignored protection fixture item template."));
		final Item foreign = ground(player, protectedId, 1, 150);
		foreign.setOwnerId(foreignOwner.getObjectId()); foreign.setProtected(true); foreign.getDropProtection().protect(foreignOwner);
		final long protectedCount = count(player, protectedId); final long ignoredCount = count(player, ignoredItemId);
		final int startX = player.getX(); final int startY = player.getY();
		try
		{
			PhantomAssertions.assertTrue(reachable(player, foreign) && (player.calculateDistance2D(foreign) > 70) && (player.calculateDistance2D(foreign) < 200) && foreign.getDropProtection().isProtected() && !foreign.getDropProtection().tryPickUp(player), "Foreign protected item lacks actual native protection/reachability.");
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Protection/ignored stock pool did not start.");
			await(() -> player.isMoving() || (player.getAI().getIntention() == Intention.MOVE_TO) || (player.getTarget() == lawfulTarget), 4000, "Stock worker neither inspected loot nor selected the actual lawful target.");
			context.record("m1.loot.protection", "intention=" + player.getAI().getIntention() + " moving=" + player.isMoving() + " target=" + player.getTarget() + " follow=" + player.getAI().getFollowTarget() + " destination=" + player.getXdestination() + "," + player.getYdestination() + " displacement=" + Math.hypot((long) player.getX() - startX, (long) player.getY() - startY));
			PhantomAssertions.assertTrue((player.getAI().getIntention() != Intention.MOVE_TO) && (player.getTarget() == lawfulTarget) && (!player.isMoving() || (player.getAI().getFollowTarget() == lawfulTarget) || ((player.getAI().getIntention() == Intention.ATTACK) && (Math.hypot((long) player.getXdestination() - lawfulTarget.getX(), (long) player.getYdestination() - lawfulTarget.getY()) <= 30))), "Stock Pickup pursued an ineligible foreign item instead of the exact lawful native target.");
			await(() -> { PhantomAssertions.assertFalse(player.getAI().getIntention() == Intention.MOVE_TO, "Stock Pickup resumed forbidden loot pursuit during lawful native farm."); return evidence(materialization, profileId, player).damageSequence() > 0; }, 8000, "Rejected loot blocked actual lawful farm damage.");
			PhantomAssertions.assertTrue(foreign.isSpawned() && ignored.isSpawned() && (count(player, protectedId) == protectedCount) && (count(player, ignoredItemId) == ignoredCount), "Protected/ignored loot was stolen or duplicated.");
		}
		finally { quiesce(materialization, profileId, visible); destroyGround(foreign); destroyGround(ignored); }
	}

	public static void blockedInventory(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, int itemId, boolean weightOnly, Monster lawfulTarget) throws Exception
	{
		final Player player = player(materialization, profileId);
		validTarget(player, goal, lawfulTarget);
		final Item dropped = ground(player, itemId, weightOnly ? 3 : 1, 20);
		final long before = count(player, itemId);
		try
		{
			if (weightOnly) { PhantomAssertions.assertTrue(player.isInventoryUnder90(false) && player.getInventory().validateCapacity(dropped) && !player.getInventory().validateWeightByItemId(itemId, 3), "Weight-only fixture lacks actual native rejected weight with available slots."); }
			else { PhantomAssertions.assertTrue(!player.isInventoryUnder90(false) && !player.getInventory().validateCapacity(dropped), "Full inventory fixture lacks actual native capacity rejection."); }
			PhantomAssertions.assertFalse(AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(itemId), "Blocked inventory item is independently ignored by config.");
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Blocked inventory stock worker did not start.");
			await(() -> !dropped.isSpawned() || (player.getTarget() == lawfulTarget), 4000, "Stock worker did not reach blocked pickup or native target selection.");
			context.record(weightOnly ? "m1.loot.weight" : "m1.loot.capacity", "ground=" + dropped.isSpawned() + " gain=" + (count(player, itemId) - before) + " load=" + player.getCurrentLoad() + "/" + player.getMaxLoad());
			PhantomAssertions.assertTrue(dropped.isSpawned() && (count(player, itemId) == before), "Stock Pickup acquired loot rejected by the native inventory gate.");
			await(() -> evidence(materialization, profileId, player).damageSequence() > 0, 8000, "Inventory rejection caused an endless loot block of native farm.");
		}
		finally { quiesce(materialization, profileId, visible); destroyGround(dropped); }
	}

	/** Read-only native geometry probe; an empty result requires a different factual TEST fixture. */
	public static Optional<Location> excludedGroundPoint(Player player, boolean blocked)
	{
		return excludedGroundPoint(new Location(player), blocked);
	}

	public static Optional<Location> excludedGroundPoint(PhantomTopologyPoint source, boolean blocked)
	{
		return excludedGroundPoint(new Location(source.x(), source.y(), source.z(), 0, source.instanceId()), blocked);
	}

	public static Optional<Location> excludedGroundPoint(Location source, boolean blocked)
	{
		final var points = excludedGroundPoints(source);
		return Optional.ofNullable(blocked ? points.blocked() : points.reachable());
	}

	/** Select before TEST seed/baseline; never relocate an existing Player or enlarge original anchor tolerance. */
	public static Optional<Location> excludedGroundSource(PhantomTopologyAnchor anchor)
	{
		final var point = anchor.point();
		return excludedGroundSource(new Location(point.x(), point.y(), point.z(), 0, point.instanceId()), anchor.validationTolerance());
	}

	public static Optional<Location> excludedGroundSource(Location anchor, int validationTolerance)
	{
		PhantomAssertions.assertTrue(validationTolerance >= 0 && validationTolerance <= 500, "Invalid original native fixture anchor tolerance.");
		final GeoEngine geo = GeoEngine.getInstance();
		if (anchor.getInstanceId() != 0 || !geo.hasGeo(anchor.getX(), anchor.getY())) { return Optional.empty(); }
		final int tolerance = Math.min(validationTolerance, 400);
		final int anchorZ = geo.getHeight(anchor.getX(), anchor.getY(), anchor.getZ());
		if (anchorZ != geo.getHeight(anchor.getX(), anchor.getY(), anchor.getZ()) || anchorZ != geo.getHeight(anchor.getX(), anchor.getY(), anchorZ) || Math.abs((long) anchorZ - anchor.getZ()) > validationTolerance) { return Optional.empty(); }
		for (int ring = 0; ring <= 4; ring++)
		{
			for (int dx = -ring; dx <= ring; dx++)
			{
				for (int dy = -ring; dy <= ring; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != ring || ((long) dx * dx + (long) dy * dy) * 64 * 64 > (long) tolerance * tolerance) { continue; }
					final int x = anchor.getX() + dx * 64; final int y = anchor.getY() + dy * 64;
					if (!geo.hasGeo(x, y) || !geo.hasGeo(x + 20, y)) { continue; }
					final int z = geo.getHeight(x, y, anchor.getZ());
					if (z != geo.getHeight(x, y, anchor.getZ()) || z != geo.getHeight(x, y, z) || Math.abs((long) z - anchorZ) > tolerance || Math.abs((long) z - anchor.getZ()) > validationTolerance
						|| ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null || ZoneManager.getInstance().getZone(x + 20, y, z, WaterZone.class) != null
						|| !geo.canMoveToTarget(anchor.getX(), anchor.getY(), anchorZ, x, y, z, 0) || !geo.canMoveToTarget(x, y, z, x + 20, y, z, 0)) { continue; }
					final var source = new Location(x, y, z, 0, 0);
					final var points = excludedGroundPoints(source);
					if (points.blocked() != null && points.reachable() != null) { return Optional.of(source); }
				}
			}
		}
		return Optional.empty();
	}

	private record GroundPoints(Location blocked, Location reachable) { }

	private static GroundPoints excludedGroundPoints(Location source)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		final int sourceX = source.getX(); final int sourceY = source.getY(); final int sourceZ = source.getZ(); final int instanceId = source.getInstanceId();
		if (!geo.hasGeo(sourceX, sourceY)) { return new GroundPoints(null, null); }
		Location blocked = null; Location reachable = null;
		for (int radius = 80; radius <= 190; radius += 10)
		{
			for (int ray = 0; ray < 32; ray++)
			{
				final double angle = (2 * Math.PI * ray) / 32;
				final int x = sourceX + (int) Math.round(radius * Math.cos(angle)); final int y = sourceY + (int) Math.round(radius * Math.sin(angle));
				if (!geo.hasGeo(x, y)) { continue; }
				final int z = geo.getHeight(x, y, sourceZ);
				final long dx = (long) x - sourceX; final long dy = (long) y - sourceY; final long dz = (long) z - sourceZ;
				final long horizontalSquared = (dx * dx) + (dy * dy);
				// World.forEachVisibleObjectInRange uses 3D distance before stock Pickup examines geo.
				if ((horizontalSquared <= 70 * 70) || (horizontalSquared >= 200 * 200) || ((horizontalSquared + (dz * dz)) >= 200 * 200)) { continue; }
				final boolean canMove = geo.canMoveToTarget(sourceX, sourceY, sourceZ, x, y, z, instanceId);
				if (!canMove && blocked == null) { blocked = new Location(x, y, z, 0, instanceId); }
				if (canMove && reachable == null) { reachable = new Location(x, y, z, 0, instanceId); }
				if (blocked != null && reachable != null) { return new GroundPoints(blocked, reachable); }
			}
		}
		return new GroundPoints(blocked, reachable);
	}

	public static void excludedGroundCreated(PhantomTestContext context, Player exactManaged, PhantomMaterializationService materialization, long profileId, PhantomVisibleAutoPlay visible, PhantomGoal goal, Monster lawfulTarget, int stockItemId) throws Exception
	{
		final Player player = player(materialization, profileId);
		PhantomAssertions.assertTrue(player == exactManaged, "Excluded-ground caller supplied a different native Player lifetime.");
		validTarget(player, goal, lawfulTarget);
		final var template = ItemData.getInstance().getTemplate(stockItemId);
		PhantomAssertions.assertTrue((template != null) && template.isStackable() && !template.hasExImmediateEffect() && !AutoPlayConfig.IGNORED_AUTO_PICK_ITEMS.contains(stockItemId) && player.getAutoPlaySettings().doPickup() && player.isInventoryUnder90(false), "Excluded-ground fixture has an independent template/settings/inventory pickup exclusion.");
		final var nativeDrops = lawfulTarget.getTemplate().getDropList(); final var nativeGroups = lawfulTarget.getTemplate().getDropGroups();
		PhantomAssertions.assertTrue(((nativeDrops == null) || nativeDrops.stream().noneMatch(drop -> drop.getItemId() == stockItemId)) && ((nativeGroups == null) || nativeGroups.stream().allMatch(group -> group.getDropList().stream().noneMatch(drop -> drop.getItemId() == stockItemId))), "Excluded-ground count oracle overlaps the lawful Monster's actual drop table.");
		final var points = excludedGroundPoints(new Location(player));
		final Location blocked = Optional.ofNullable(points.blocked()).orElseThrow(() -> new IllegalStateException("Missing actual loaded blocked TEST ground point within 80..190 rays at " + player.getX() + "," + player.getY() + "," + player.getZ() + " instance=" + player.getInstanceId()));
		final Location reachable = Optional.ofNullable(points.reachable()).orElseThrow(() -> new IllegalStateException("Missing actual loaded reachable TEST ground point within 80..190 rays."));
		final int otherInstanceId = InstanceManager.getInstance().createDynamicInstance(0).getId();
		Item unreachable = null; Item otherInstance = null;
		try
		{
			unreachable = excludedItem(player, stockItemId, blocked, player.getInstanceId());
			otherInstance = excludedItem(player, stockItemId, reachable, otherInstanceId);
			PhantomAssertions.assertTrue(player.getInventory().validateCapacity(unreachable) && player.getInventory().validateCapacity(otherInstance) && player.getInventory().validateWeightByItemId(stockItemId, 2), "Excluded-ground fixture has an independent native capacity/weight exclusion.");
			PhantomAssertions.assertTrue(World.getInstance().getVisibleObjectsInRange(player, Item.class, 200).contains(unreachable) && !World.getInstance().getVisibleObjectsInRange(player, Item.class, 200).contains(otherInstance) && reachable(player, otherInstance), "Native World/geo preflight does not isolate geo exclusion from actual other-instance exclusion.");
			context.record("m1.loot.exclusionFixture", "item=" + stockItemId + " blocked=" + blocked.getX() + "," + blocked.getY() + "," + blocked.getZ() + " distance=" + player.calculateDistance2D(unreachable) + " instance=" + player.getInstanceId() + " other=" + reachable.getX() + "," + reachable.getY() + "," + reachable.getZ() + " distance=" + player.calculateDistance2D(otherInstance) + " instance=" + otherInstanceId + " nativeGeo=false/true boundedRays=384");
			excludedGround(context, profileId, materialization, visible, goal, unreachable, otherInstance, lawfulTarget);
		}
		finally
		{
			try { destroyGround(unreachable); }
			finally { try { destroyGround(otherInstance); } finally { InstanceManager.getInstance().destroyInstance(otherInstanceId); } }
		}
	}

	private static Item excludedItem(Player player, int itemId, Location point, int instanceId)
	{
		final Item item = ItemManager.createItem(ItemProcessType.LOOT, itemId, 1, player, PhantomM1LootWatchdogChecks.class);
		PhantomAssertions.assertTrue(item != null, "Excluded-ground native Item was not created.");
		try
		{
			item.resetOwnerTimer(); item.setOwnerId(0); item.setProtected(false); item.getDropProtection().unprotect();
			item.dropMe(null, point.getX(), point.getY(), point.getZ());
			item.setInstanceId(instanceId);
			PhantomAssertions.assertTrue(item.isSpawned() && (item.getInstanceId() == instanceId) && (item.getX() == point.getX()) && (item.getY() == point.getY()) && (item.getZ() == point.getZ()), "Native dropMe(null) did not retain the measured point/exact instance.");
			return item;
		}
		catch (RuntimeException | Error failure)
		{
			item.resetOwnerTimer(); if (item.isSpawned()) { item.decayMe(); }
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomM1LootWatchdogChecks.class);
			throw failure;
		}
	}

	public static void excludedGround(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, Item unreachable, Item otherInstance, Monster lawfulTarget) throws Exception
	{
		final Player player = player(materialization, profileId);
		validTarget(player, goal, lawfulTarget);
		PhantomAssertions.assertTrue(unreachable.isSpawned() && (unreachable.getInstanceId() == player.getInstanceId()) && (player.calculateDistance2D(unreachable) > 70) && (player.calculateDistance2D(unreachable) < 200) && !reachable(player, unreachable), "Unreachable fixture has no factual native geo exclusion at 70<distance<200.");
		PhantomAssertions.assertTrue(otherInstance.isSpawned() && (otherInstance.getInstanceId() != player.getInstanceId()) && (player.calculateDistance2D(otherInstance) > 70) && (player.calculateDistance2D(otherInstance) < 200), "Other-instance fixture lacks actual instance separation at 70<distance<200.");
		final long unreachableCount = count(player, unreachable.getId()); final long otherCount = count(player, otherInstance.getId());
		try
		{
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Exclusion stock pool did not start.");
			await(() -> (player.getTarget() == lawfulTarget) && (evidence(materialization, profileId, player).damageSequence() > 0), 8000, "Excluded ground blocked actual lawful native farm selection/damage.");
			PhantomAssertions.assertTrue(unreachable.isSpawned() && otherInstance.isSpawned() && (count(player, unreachable.getId()) == unreachableCount) && (count(player, otherInstance.getId()) == otherCount), "Stock Pickup acquired geo-excluded or other-instance loot.");
			context.record("m1.loot.exclusions", "actualGeo=false otherInstance=" + otherInstance.getInstanceId() + " nativeFarmDamage=true");
		}
		finally { quiesce(materialization, profileId, visible); destroyGround(unreachable); destroyGround(otherInstance); }
	}

	public static void pickupDuringCleanup(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, PhantomBackgroundTransaction transaction, int questItemId) throws Exception
	{
		final Player player = player(materialization, profileId);
		final var baseline = transaction.load(profileId);
		PhantomAssertions.assertTrue(baseline.successful() && !baseline.state().inventory().mutableItemIds().contains(questItemId), "Cleanup quest fixture is already a background mutable item.");
		final Item first = ground(player, questItemId, 1, 20);
		final var second = new AtomicReference<Item>();
		PhantomAssertions.assertTrue(first.isQuestItem() && reachable(player, first), "Cleanup fixture lacks an actual reachable native quest item.");
		final long expectedCount = count(player, questItemId) + 2;
		final var inside = new CountDownLatch(1); final var release = new CountDownLatch(1); final var finished = new CountDownLatch(1);
		final var consumed = new AtomicBoolean(); final var failure = new AtomicReference<Throwable>(); final var hash = new AtomicReference<String>();
		final var result = new AtomicReference<PhantomMaterializationService.DematerializeResult>();
		final var cleanupFailure = new AtomicReference<Throwable>();
		final var listener = new ConsumerEventListener(player, EventType.ON_PLAYER_ITEM_ADD, (OnPlayerItemAdd event) ->
		{
			if ((event.getPlayer() != player) || (event.getItem().getId() != questItemId) || !consumed.compareAndSet(false, true)) { return; }
			inside.countDown();
			try
			{
				if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Actual inventory callback barrier timed out."); }
				final Item remaining = second.get();
				PhantomAssertions.assertTrue((remaining != null) && remaining.isSpawned(), "Second lawful ground item was not prepared behind the callback barrier.");
				player.doPickupItem(remaining);
				PhantomAssertions.assertEquals(expectedCount, count(player, questItemId), "Inherited native callback lost the second lawful pickup.");
				hash.set(fullHash(player));
			}
			catch (Throwable thrown) { failure.set(thrown); }
			finally { finished.countDown(); }
		}, PhantomM1LootWatchdogChecks.class);
		final Thread cleanup = new Thread(() -> { try { result.set(materialization.dematerialize(profileId)); } catch (Throwable thrown) { cleanupFailure.set(thrown); } }, "m1-native-pickup-cleanup");
		player.addListener(listener);
		try
		{
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Concurrent cleanup stock Pickup did not start.");
			PhantomAssertions.assertTrue(inside.await(5, TimeUnit.SECONDS), "Actual stock pickup never entered its native asynchronous inventory listener.");
			visible.stop(profileId);
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { second.set(ground(action.player(), questItemId, 1, 20)); }
			PhantomAssertions.assertTrue(second.get().isQuestItem() && reachable(player, second.get()) && (player.calculateDistance2D(second.get()) <= 70), "Second callback pickup lacks factual native quest/reachability preconditions.");
			cleanup.start();
			await(() -> materialization.find(profileId).map(snapshot -> !snapshot.actionAdmissionOpen()).orElse(true), 2000, "Native cleanup did not close actor admission during held earned callback.");
			final boolean retained = materialization.find(profileId).isPresent() && (World.getInstance().getPlayer(player.getObjectId()) == player) && (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM) && player.hasHeadlessOutboundSession() && cleanup.isAlive();
			context.record("m1.loot.cleanup.retainedBeforeRelease", retained);
			PhantomAssertions.assertTrue(retained, "Native cleanup crossed an active earned inventory callback.");
			release.countDown();
			PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "Held native pickup continuation did not complete.");
			cleanup.join(8000);
			PhantomAssertions.assertTrue(!cleanup.isAlive() && (failure.get() == null) && (cleanupFailure.get() == null), "Native pickup/cleanup failed: " + failure.get() + " / " + cleanupFailure.get());
			PhantomAssertions.assertTrue((result.get() != null) && (result.get().status() == PhantomMaterializationService.ResultStatus.SUCCESS) && (hash.get() != null), "Native pickup/cleanup did not reach successful store.");
			assertStoredAndReload(context, profileId, materialization, transaction, questItemId, expectedCount, hash.get());
		}
		finally
		{
			release.countDown(); visible.stop(profileId); player.removeListener(listener);
			if (cleanup.isAlive()) { cleanup.join(10000); }
			destroyGround(first); destroyGround(second.get());
		}
	}

	public static void watchdog(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomVisibleAutoPlay visible, PhantomGoal goal, AtomicLong clock, Monster target, Skill learnedHeal) throws Exception
	{
		final Player player = player(materialization, profileId);
		PhantomAssertions.assertTrue((learnedHeal != null) && learnedHeal.hasEffectType(EffectType.HEAL) && (player.getKnownSkill(learnedHeal.getId()) != null) && (player.getKnownSkill(learnedHeal.getId()).getLevel() == learnedHeal.getLevel()), "Watchdog fixture lacks an actually learned native HEAL.");
		PhantomAssertions.assertTrue((clock.get() > 0) && (Math.abs(System.nanoTime() - clock.get()) < TimeUnit.SECONDS.toNanos(10)), "Watchdog clock did not originate from the native monotonic timeline.");
		try
		{
			PhantomAssertions.assertTrue(visible.start(profileId, goal), "Watchdog actual stock pools did not start.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				player.getAutoUseSettings().getAutoActions().clear(); player.getAutoUseSettings().getAutoSkills().clear(); player.getAutoUseSettings().getAutoBuffs().clear(); player.getAutoUseSettings().getAutoSupplyItems().clear(); player.getAutoUseSettings().setAutoPotionItem(0);
				player.setCurrentHp(player.getMaxHp() / 2); player.getStatus().stopHpMpRegeneration();
				target.disableCoreAI(true);
				if (!target.isSpawned()) { target.setInstanceId(player.getInstanceId()); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(player.getX() + 20, player.getY(), player.getZ()); }
				// Npc.onSpawn restores template random walking even when core attacks are disabled.
				target.setRandomWalking(false); target.stopMove(null);
			}
			final int targetRange = player.getAutoPlaySettings().isShortRange() ? AutoPlayConfig.AUTO_PLAY_SHORT_RANGE : AutoPlayConfig.AUTO_PLAY_LONG_RANGE;
			context.record("m1.watchdog.target.beforeHeal", targetState(player, goal, target, targetRange));
			validTarget(player, goal, target, targetRange);
			await(() -> player.getTarget() == target, 4000, "Actual stock AutoPlay did not select the valid native watchdog target.");
			PhantomAssertions.assertEquals(0L, evidence(materialization, profileId, player).damageSequence(), "Watchdog setup already made useful damage; no zero-progress RED is valid.");
			final long started = clock.get();
			PhantomAssertions.assertFalse(visible.noTargetExpired(profileId, goal), "Fresh valid native watchdog session was already expired.");
			final double beforeHp = player.getCurrentHp();
			final double beforeMp = player.getCurrentMp();
			final int baseCastMillis = learnedHeal.getHitTime() + learnedHeal.getCoolTime();
			// Uncharged stock duration is a conservative upper bound for charged native casts.
			final int nativeCastMillis = Math.max(learnedHeal.isMagic() && (baseCastMillis > 550) ? 550 : 0, learnedHeal.isStatic() ? baseCastMillis : Formulas.calcAtkSpd(player, learnedHeal, baseCastMillis));
			final long castHorizonMillis = (long) nativeCastMillis + 2000;
			PhantomAssertions.assertTrue(!learnedHeal.isChanneling() && (nativeCastMillis >= 0) && (castHorizonMillis <= 20000), "Actual learned HEAL native duration exceeds the bounded ordinary cast fixture.");
			context.record("m1.watchdog.heal.horizon", "nativeCastMillis=" + nativeCastMillis + " schedulerAllowanceMillis=2000 horizonMillis=" + castHorizonMillis);
			context.record("m1.watchdog.heal.before", "skill=" + learnedHeal.getId() + ":" + learnedHeal.getLevel() + " hp=" + beforeHp + "/" + player.getMaxHp() + " recoverable=" + player.getMaxRecoverableHp() + " mp=" + beforeMp + " need=" + (player.getStat().getMpConsume(learnedHeal) + player.getStat().getMpInitialConsume(learnedHeal)) + " invul=" + player.isInvul() + " teleport=" + player.isTeleporting() + " muted=" + player.isMuted() + " disabled=" + player.isSkillDisabled(learnedHeal) + " mAtkSpd=" + player.getMAtkSpd() + " baseHitMillis=" + learnedHeal.getHitTime());
			PhantomAssertions.assertTrue(!player.isInvul() && !player.isDead() && (player.getMaxRecoverableHp() > beforeHp) && player.checkDoCastConditions(learnedHeal), "Native SELF HEAL fixture is rejected by actual cast or effect eligibility.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { player.doCast(learnedHeal, player, List.of(player)); }
			try { await(() -> (player.getCurrentHp() > beforeHp) && (player.getCurrentMp() < beforeMp) && !player.isCastingNow() && (player.getLastSkillCast() == learnedHeal), castHorizonMillis, "Actual learned native SELF HEAL did not complete with positive HP delta and native MP consumption within its derived horizon."); }
			finally { context.record("m1.watchdog.heal.after", "hp=" + player.getCurrentHp() + " delta=" + (player.getCurrentHp() - beforeHp) + " mp=" + player.getCurrentMp() + " mpDelta=" + (beforeMp - player.getCurrentMp()) + " casting=" + player.isCastingNow() + " lastSkill=" + (player.getLastSkillCast() == null ? 0 : player.getLastSkillCast().getId()) + " disabled=" + player.isSkillDisabled(learnedHeal) + " invul=" + player.isInvul() + " nativeOverflow=" + evidence(materialization, profileId, player).overflow()); }
			context.record("m1.watchdog.target.afterHeal", targetState(player, goal, target, targetRange));
			PhantomAssertions.assertEquals(0L, evidence(materialization, profileId, player).damageSequence(), "Native selfheal control unexpectedly produced own offensive damage.");
			clock.set(started + TimeUnit.SECONDS.toNanos(31));
			PhantomAssertions.assertFalse(visible.noTargetExpired(profileId, goal), "First native no-progress recovery prematurely terminated the goal.");
			clock.set(started + TimeUnit.SECONDS.toNanos(91));
			validTarget(player, goal, target, targetRange);
			PhantomAssertions.assertTrue(visible.running(profileId, goal) && player.isAutoPlaying() && (player.getTarget() == target), "Watchdog lost its actual running target/session before the debt assertion.");
			final boolean expired = visible.noTargetExpired(profileId, goal);
			context.record("m1.watchdog.targetWithoutProgress", "target=" + target.getObjectId() + " autoPlay=" + player.isAutoPlaying() + " healDelta=" + (player.getCurrentHp() - beforeHp) + " damage=0 expired=" + expired);
			PhantomAssertions.assertTrue(expired, "Existing native watchdog erases 90s farm progress debt merely because a target/selfheal exists.");
		}
		finally { quiesce(materialization, profileId, visible); }
	}

	private static Player player(PhantomMaterializationService materialization, long profileId)
	{
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
		{
			final Player player = action.player();
			PhantomAssertions.assertTrue(!player.isGM() && player.isOnline() && player.isSpawned() && player.hasHeadlessOutboundSession() && !player.isInParty() && !player.isMoving() && !player.isAttackingNow() && !player.isCastingNow() && (player.getTarget() == null), "Fixture is not a fresh ordinary guarded native actor.");
			return player;
		}
	}

	private static PlayerNativeEvidence.Snapshot evidence(PhantomMaterializationService materialization, long profileId, Player player)
	{
		final var owner = player.getNativeWorkOwner();
		final long epoch = materialization.find(profileId).orElseThrow().materializedAtNanos();
		PhantomAssertions.assertTrue((owner != null) && (owner.player() == player) && owner.isCurrent() && (owner.epoch() == epoch) && (owner.evidence() != null) && owner.evidence().matches(player.getObjectId(), epoch), "Actual native fixture lacks its exact current lifetime evidence owner.");
		final var snapshot = owner.evidence().snapshot();
		PhantomAssertions.assertFalse(snapshot.overflow(), "Native scalar evidence is unproven/overflowed.");
		return snapshot;
	}

	private static void validTarget(Player player, PhantomGoal goal, Monster target)
	{
		validTarget(player, goal, target, 30);
	}

	private static void validTarget(Player player, PhantomGoal goal, Monster target, int range)
	{
		PhantomAssertions.assertTrue(target.isSpawned() && !target.isAlikeDead() && !target.isRaid() && !target.isInvul() && target.isTargetable() && target.isShowName() && target.isAutoAttackable(player) && (target.getId() == PhantomBackgroundGoalSpec.parse(goal).npcId()) && (target.getInstanceId() == player.getInstanceId()) && (player.calculateDistance2D(target) <= range) && (Math.abs((long) player.getZ() - target.getZ()) < 800) && (!player.getAutoPlaySettings().isRespectfulHunting() || (target.getTarget() == null) || (target.getTarget() == player) || (player.hasSummon() && (player.getSummon().getObjectId() == target.getTarget().getObjectId()))) && GeoEngine.getInstance().canSeeTarget(player, target) && GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId()), "Fixture lacks a factual selectable exact-goal native Monster: " + targetState(player, goal, target, range));
	}

	private static String targetState(Player player, PhantomGoal goal, Monster target, int range)
	{
		return "object=" + target.getObjectId() + " npc=" + target.getId() + "/" + PhantomBackgroundGoalSpec.parse(goal).npcId() + " spawned=" + target.isSpawned() + " dead=" + target.isAlikeDead() + " raid=" + target.isRaid() + " invul=" + target.isInvul() + " targetable=" + target.isTargetable() + " showName=" + target.isShowName() + " autoAttackable=" + target.isAutoAttackable(player) + " instance=" + target.getInstanceId() + "/" + player.getInstanceId() + " distance=" + player.calculateDistance2D(target) + "/" + range + " zDelta=" + Math.abs((long) player.getZ() - target.getZ()) + " see=" + GeoEngine.getInstance().canSeeTarget(player, target) + " move=" + GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId()) + " randomWalk=" + target.isRandomWalkingEnabled() + " moving=" + target.isMoving() + " respectful=" + player.getAutoPlaySettings().isRespectfulHunting() + " npcTarget=" + target.getTarget() + " playerTarget=" + player.getTarget() + " playerPose=" + player.getX() + "," + player.getY() + "," + player.getZ() + " targetPose=" + target.getX() + "," + target.getY() + "," + target.getZ();
	}

	private static Item ground(Player player, int itemId, long count, int distance)
	{
		PhantomAssertions.assertTrue(ItemData.getInstance().getTemplate(itemId) != null, "Native fixture item template is missing.");
		final Item item = ItemManager.createItem(ItemProcessType.LOOT, itemId, count, player, PhantomM1LootWatchdogChecks.class);
		PhantomAssertions.assertTrue(item != null, "Native fixture Item was not created.");
		item.dropMe(player, player.getX() + distance, player.getY(), player.getZ());
		item.setOwnerId(0); item.setProtected(false); item.getDropProtection().unprotect();
		return item;
	}

	private static boolean reachable(Player player, Item item)
	{
		return GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), item.getX(), item.getY(), item.getZ(), player.getInstanceId());
	}

	private static long count(Player player, int itemId) { return player.getInventory().getInventoryItemCount(itemId, -1); }

	private static String fullHash(Player player)
	{
		return PhantomBackgroundInventoryHash.compute(player.getInventory().getItems().stream().filter(item -> (item.getCount() > 0) && (item.getItemLocation().name().equals("INVENTORY") || item.getItemLocation().name().equals("PAPERDOLL"))).map(item -> new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name()))).toList());
	}

	private static void quiesce(PhantomMaterializationService materialization, long profileId, PhantomVisibleAutoPlay visible)
	{
		visible.stop(profileId);
		try (var action = materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action == null) { return; }
			final Player player = action.player(); player.abortAttack(); player.abortCast(); player.stopMove(null); player.setTarget(null); player.getAI().setIntention(Intention.IDLE); player.getAI().clientStopAutoAttack(); player.getAI().setAutoAttacking(false);
		}
	}

	private static void persistAndReload(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundTransaction transaction, Player player, int itemId, long expectedCount, String expectedHash)
	{
		PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(profileId).status(), "Native acquired-loot cleanup failed.");
		PhantomAssertions.assertFalse(World.getInstance().getPlayer(player.getObjectId()) == player, "Native cleanup retained the stored Player in World.");
		assertStoredAndReload(context, profileId, materialization, transaction, itemId, expectedCount, expectedHash);
	}

	private static void assertStoredAndReload(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundTransaction transaction, int itemId, long expectedCount, String expectedHash)
	{
		final var stored = transaction.load(profileId);
		PhantomAssertions.assertTrue(stored.successful() && (stored.state() != null), "Acquired-loot background store cannot be read.");
		PhantomAssertions.assertEquals(expectedHash, stored.state().inventory().canonicalHash(), "Native acquired-loot full canonical digest disagrees with persisted rows.");
		PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(profileId).status(), "Stored acquired-loot Player did not rematerialize.");
		final Player restored;
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { restored = action.player(); }
		PhantomAssertions.assertEquals(expectedCount, count(restored, itemId), "Native acquired item count changed after rematerialization.");
		PhantomAssertions.assertEquals(expectedHash, PlayerNativeWork.checkpoint(restored, () -> fullHash(restored)), "Complete native inventory digest changed after rematerialization.");
		context.record("m1.loot.persisted." + itemId, "count=" + expectedCount + " fullInventoryHash=" + expectedHash);
	}

	private static void await(BooleanSupplier condition, long milliseconds, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline)) { Thread.sleep(20); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static void destroyGround(Item item)
	{
		if ((item != null) && item.isSpawned())
		{
			item.getDropProtection().unprotect(); item.resetOwnerTimer(); item.decayMe();
			ItemManager.destroyItem(ItemProcessType.DESTROY, item, null, PhantomM1LootWatchdogChecks.class);
		}
	}
}
