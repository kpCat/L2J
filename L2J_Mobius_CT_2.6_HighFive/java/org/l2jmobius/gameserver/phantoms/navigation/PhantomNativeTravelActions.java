/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Future;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;

/** Actual native fees/actions only. Unsupported return paths remain explicitly unavailable. */
public final class PhantomNativeTravelActions
{
	public static final int LOCAL_WALK_DISTANCE = 3000;
	private static final ThreadLocal<ReturnOperation> RETURN_GUARD = new ThreadLocal<>();
	public enum Status { ARRIVED, UNAVAILABLE, INSUFFICIENT_RESOURCES, NATIVE_REJECTED, PAID_WITHOUT_ARRIVAL }
	public record GatekeeperResult(Status status, long fee, long adenaBefore, long adenaAfter, PhantomNavigationPoint before, PhantomNavigationPoint after) { }
	public record OwnedWalk(Player player, PlayerNativeWork.Owner owner, PhantomNavigationPoint destination) { }
	private PhantomNativeTravelActions() { }
	/** Native physical AI keeps its attack target independently of Creature's cast/selection target. */
	public static boolean targetsForCombat(Creature attacker, Creature target)
	{
		return attacker != null && target != null && (attacker.getTarget() == target || attacker.hasAI() && attacker.getAI().getAttackTarget() == target);
	}
	/** Physical ownership witness only; the journey keeps its existing Goal and scheduler. */
	public static OwnedWalk captureWalk(Player player)
	{
		final var owner = player.getNativeWorkOwner();
		return owner != null && owner.isCurrent() && World.getInstance().getPlayer(player.getObjectId()) == player && player.isMoving() && player.hasAI() && player.getAI().getIntention() == org.l2jmobius.gameserver.ai.Intention.MOVE_TO
			? new OwnedWalk(player, owner, new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), player.getInstanceId())) : null;
	}
	public static boolean cancelWalk(OwnedWalk issued)
	{
		if (issued == null) { return false; }
		final var player = issued.player(); final var destination = issued.destination();
		if (issued.owner() == null || player.getNativeWorkOwner() != issued.owner() || !issued.owner().isCurrent() || World.getInstance().getPlayer(player.getObjectId()) != player
			|| !player.isMoving() || !player.hasAI() || player.getAI().getIntention() != org.l2jmobius.gameserver.ai.Intention.MOVE_TO || player.getInstanceId() != destination.instanceId()
			|| player.getXdestination() != destination.x() || player.getYdestination() != destination.y() || player.getZdestination() != destination.z()) { return false; }
		player.stopMove(null); player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE);
		return true;
	}
	/** A native town can prepare a journey even when its certified farm topology has no local target. */
	public static boolean safePreparationTown(Player player)
	{
		return player.getInstanceId() == 0 && player.isInsideZone(ZoneId.PEACE) && !player.isInsideZone(ZoneId.WATER)
			&& GeoEngine.getInstance().hasGeo(player.getX(), player.getY());
	}
	/** Geo proves ground; this separately excludes crossing a new native PvE threat. */
	public static boolean safePveWalkSegment(Player player, int x, int y, int z, List<org.l2jmobius.gameserver.model.actor.instance.Monster> visible, java.util.Set<Integer> engaged)
	{
		final double dx = (double) x - player.getX(), dy = (double) y - player.getY(), lengthSquared = dx * dx + dy * dy;
		if (lengthSquared == 0) { return false; }
		for (var monster : visible)
		{
			if (engaged.contains(monster.getObjectId()) || monster.isAlikeDead() || monster.getInstanceId() != player.getInstanceId() || Math.abs((long) monster.getZ() - player.getZ()) > 256
				|| !monster.isAggressive() && monster.getLevel() <= player.getLevel() + 3) { continue; }
			final double mx = (double) monster.getX() - player.getX(), my = (double) monster.getY() - player.getY();
			final double projection = Math.clamp((mx * dx + my * dy) / lengthSquared, 0, 1);
			final double closest = Math.hypot(mx - projection * dx, my - projection * dy);
			final double before = Math.hypot(mx, my), after = Math.hypot((double) x - monster.getX(), (double) y - monster.getY());
			final double radius = Math.max(300, monster.getAggroRange() + 100);
			// An actor already inside danger may leave it, but must not move closer.
			if (closest < Math.min(radius, before) - 16 || before < radius && after < before + 32) { return false; }
		}
		return true;
	}
	/** Existing finite journey owners prove these local checkpoints through Navigation before moving. */
	public static List<PhantomNavigationPoint> localWalkDestinations(PhantomNavigationPoint from, PhantomNavigationPoint target)
	{
		if (from.instanceId() != target.instanceId()) { return List.of(); }
		final double distance = from.distanceTo(target);
		if (distance <= 187) { return List.of(target); }
		final double bearing = Math.atan2((double) target.y() - from.y(), (double) target.x() - from.x());
		final List<PhantomNavigationPoint> points = new java.util.ArrayList<>(); final var geo = GeoEngine.getInstance();
		if (distance <= LOCAL_WALK_DISTANCE) { points.add(target); }
		for (int stride : new int[] {LOCAL_WALK_DISTANCE, 1500, 750, 375, 187})
		{
			for (double turn : stride >= 750 ? new double[] {0, -Math.PI / 6, Math.PI / 6, -Math.PI / 3, Math.PI / 3} : new double[] {0})
			{
				final double step = Math.min(stride, distance - 100);
				final int x = from.x() + (int) Math.round(step * Math.cos(bearing + turn)), y = from.y() + (int) Math.round(step * Math.sin(bearing + turn));
				if (!geo.hasGeo(x, y)) { continue; }
				final var point = new PhantomNavigationPoint(x, y, geo.getHeight(x, y, from.z()), from.instanceId());
				if (from.distanceTo(point) > 3200 || point.distanceTo(target) >= distance) { continue; }
				if (!points.contains(point)) { points.add(point); }
				if (points.size() == 17) { return List.copyOf(points); }
			}
		}
		return List.copyOf(points);
	}

	public static boolean ordinary(Player player)
	{
		final var owner = player.getNativeWorkOwner();
		return player.isNativeWorkManaged() && owner != null && owner.isCurrent() && !owner.sealed() && World.getInstance().getPlayer(player.getObjectId()) == player
			&& player.getInstanceId() == 0 && !player.isGM() && !player.isFlying() && !player.isFlyingMounted() && !player.isAlikeDead() && !player.isJailed() && !player.inObserverMode() && !player.isOnEvent() && !player.isInOlympiadMode()
			&& !player.isCastingNow() && !player.isCastingSimultaneouslyNow() && !player.isSitting() && !player.isInStoreMode() && !player.isInCombat() && !player.isCombatFlagEquipped() && player.getParty() == null && !player.isInsideZone(ZoneId.SIEGE);
	}
	public static PhantomTravelStrategy.Options snapshot(Player player, List<PhantomNormalGatekeeperTravel.Leg> legs, long remainingMillis)
	{
		return snapshot(player, legs, remainingMillis, java.util.Set.of());
	}
	public static PhantomTravelStrategy.Options snapshot(Player player, List<PhantomNormalGatekeeperTravel.Leg> legs, long remainingMillis, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges)
	{
		final long scrolls = player.getInventory().getInventoryItemCount(736, -1);
		final var landing = returnLanding(player);
		return new PhantomTravelStrategy.Options(legs, player.getMoveSpeed(), player.getAdena(), (int)Math.min(Integer.MAX_VALUE, Math.max(0, scrolls)), player.getClassIndex(), player.getLevel(), Instant.now().toEpochMilli() / 60000,
			ordinary(player) && (PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_GK || player.getKarma() == 0), landing == null ? "travel.return_random_landing_or_native_branch_unavailable" : "", landing, Math.multiplyExact((long)PlayerConfig.UNSTUCK_INTERVAL, 1000), remainingMillis, blockedEdges);
	}
	public static Npc gatekeeperAt(PhantomNormalGatekeeperTravel.Leg leg)
	{
		final var region = World.getInstance().getRegion(leg.sourceX(), leg.sourceY(), leg.sourceZ());
		if (region == null) { return null; }
		for (var object : region.getVisibleObjects())
		{
			if (object instanceof Npc npc && npc.getId() == leg.teleporterNpcId() && npc.getInstanceId() == 0 && !npc.isAlikeDead() && Math.hypot((long)npc.getX() - leg.sourceX(), (long)npc.getY() - leg.sourceY()) <= 200) { return npc; }
		}
		return null;
	}
	public static boolean available(PhantomNormalGatekeeperTravel.Leg leg)
	{
		if (!PhantomNormalGatekeeperTravel.matchesNative(leg)) { return false; }
		final var npc = gatekeeperAt(leg);
		if (npc == null) { return false; }
		return PlayerConfig.TELEPORT_WHILE_SIEGE_IN_PROGRESS || (!npc.getCastle().getSiege().isInProgress() && PhantomNormalGatekeeperTravel.destinationCastlesAvailable(leg.destinationCastleIds(), id -> !org.l2jmobius.gameserver.managers.CastleManager.getInstance().getCastleById(id).getSiege().isInProgress()));
	}
	public static GatekeeperResult gatekeeper(Player player, PhantomNormalGatekeeperTravel.Leg leg)
	{
		final var before = point(player); final long adena = player.getAdena();
		final long fee = PhantomNormalGatekeeperTravel.fee(leg, player.getClassIndex(), player.getLevel(), Instant.now().toEpochMilli() / 60000);
		final var npc = gatekeeperAt(leg);
		if (!ordinary(player) || !available(leg) || npc == null || !npc.canInteract(player)
			|| !new L2jNavigationBackend().canWalkDirect(before,new PhantomNavigationPoint(npc.getX(),npc.getY(),GeoEngine.getInstance().getHeight(npc.getX(),npc.getY(),npc.getZ()),npc.getInstanceId()))) { return new GatekeeperResult(Status.UNAVAILABLE, fee, adena, adena, before, before); }
		if (adena < fee) { return new GatekeeperResult(Status.INSUFFICIENT_RESOURCES, fee, adena, adena, before, before); }
		player.stopMove(null);
		npc.onBypassFeedback(player,"teleport "+leg.teleportListName()+" "+leg.destinationIndex());
		if (player.hasHeadlessOutboundSession() && player.isTeleporting()) { player.onTeleported(); }
		final var after = point(player); final long afterAdena = player.getAdena();
		final var expected = new PhantomNavigationPoint(leg.destinationX(),leg.destinationY(),GeoEngine.getInstance().getHeight(leg.destinationX(),leg.destinationY(),leg.destinationZ())+5,0);
		final boolean arrived = after.equals(expected) && !after.equals(before);
		final boolean charged = adena - afterAdena == fee;
		return new GatekeeperResult(arrived && charged ? Status.ARRIVED : afterAdena != adena ? Status.PAID_WITHOUT_ARRIVAL : Status.NATIVE_REJECTED, fee, adena, afterAdena, before, after);
	}
	public static PhantomNavigationPoint point(Player player) { return new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()); }
	/** Captured by the real handler before scheduling; no delayed lookup of a replacement owner. */
	public static BooleanSupplier captureReturnGuard(Player player)
	{
		final var owner = player.getNativeWorkOwner(); final long epoch = owner == null ? -1 : owner.epoch();
		final int instance = player.getInstanceId(); final ReturnOperation journey = RETURN_GUARD.get();
		return () -> owner != null && player.getNativeWorkOwner() == owner && owner.player() == player && owner.epoch() == epoch && owner.isCurrent() && !owner.sealed()
			&& World.getInstance().getPlayer(player.getObjectId()) == player && player.getInstanceId() == instance && !player.isAlikeDead() && player.isCastingNow()
			&& (journey == null || journey.current());
	}
	/** A single deterministic native landing only. Random towns/offsets need an envelope proof. */
	public static PhantomNavigationPoint returnLanding(Player player)
	{
		if (!ordinary(player) || player.isMovementDisabled() || player.isMuted() || player.isRegisteredOnEvent() || PlayerConfig.UNSTUCK_INTERVAL <= 0 || PlayerConfig.UNSTUCK_INTERVAL == 300
			|| PlayerConfig.RANDOM_RESPAWN_IN_TOWN_ENABLED || PlayerConfig.MAX_OFFSET_ON_TELEPORT != 0 || org.l2jmobius.gameserver.handler.UserCommandHandler.getInstance().getHandler(52) == null) { return null; }
		final var location = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
		return location == null ? null : new PhantomNavigationPoint(location.getX(), location.getY(), GeoEngine.getInstance().getHeight(location.getX(), location.getY(), location.getZ()) + 5, 0);
	}
	public static final class ReturnOperation
	{
		private final Player player; private final PlayerNativeWork.Owner owner; private final PhantomNavigationPoint origin, landing; private final BooleanSupplier journey;
		private volatile Future<?> future; private volatile boolean cancelled;
		private ReturnOperation(Player value, PhantomNavigationPoint destination, BooleanSupplier predicate) { player=value; owner=value.getNativeWorkOwner(); origin=point(value); landing=destination; journey=predicate; }
		private boolean current() { return !cancelled && (future == null || !future.isCancelled()) && player.getNativeWorkOwner() == owner && owner.isCurrent() && !owner.sealed() && journey.getAsBoolean() && point(player).equals(origin); }
		public boolean arrived() { return !cancelled && future != null && future.isDone() && !future.isCancelled() && player.getNativeWorkOwner() == owner && owner.isCurrent() && !player.isCastingNow() && !player.isTeleporting() && point(player).equals(landing); }
		public void cancel()
		{
			cancelled=true;
			final Future<?> captured=future;
			if (captured != null && !captured.isDone())
			{
				if (player.getNativeWorkOwner() == owner && owner.isCurrent() && World.getInstance().getPlayer(player.getObjectId()) == player) { PlayerNativeWork.run(player, "CancelUnstuck", () -> { if (!captured.isDone()) { player.abortCast(); } }); }
				captured.cancel(false);
			}
		}
	}
	/** The native handler publishes its real timer under the existing cancellable owner. */
	public static ReturnOperation returnToTown(Player player, PhantomNavigationPoint expected, BooleanSupplier journey)
	{
		if (!expected.equals(returnLanding(player))) { return null; }
		final var handler = org.l2jmobius.gameserver.handler.UserCommandHandler.getInstance().getHandler(52);
		if (handler == null || RETURN_GUARD.get() != null || !journey.getAsBoolean()) { return null; }
		final var operation=new ReturnOperation(player, expected, journey); RETURN_GUARD.set(operation);
		try { return handler.onCommand(52, player) && operation.future != null ? operation : null; }
		finally { RETURN_GUARD.remove(); }
	}
	public static void bindReturnTimer(Player player, Future<?> future)
	{
		final var operation=RETURN_GUARD.get(); if (operation != null && operation.player == player) { operation.future=future; }
	}
}
