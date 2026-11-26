/*
 * This file is part of the L2J Mobius project.
 * Distributed under the GNU General Public License, version 3 or later.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.actor.instance.DungeonGatekeeper;
import org.l2jmobius.gameserver.model.teleporter.TeleportLocation;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

/** Foreground-only SSQ connectors rooted at the first directed LIVE-002 farm edge. */
final class PhantomSsqFarmAccess
{
	private static final String PROOF_PREFIX = "data/phantoms/evidence/live002-content-ssq_catacombs_necropolis-";
	private static final int INTERIOR_DISTANCE = 4500;
	private static final int APPROACH_DISTANCE = 16000;
	private static final int FLOOR_TOLERANCE = 96;
	private final PhantomTopologyQuery _topology;
	private volatile Catalog _catalog;

	PhantomSsqFarmAccess(PhantomTopologyQuery topology)
	{
		_topology = topology;
	}

	static boolean closedAnchor(PhantomTopologyAnchor anchor)
	{
		return anchor.tags().contains("content-farming")
			&& anchor.sourceRefs().stream().anyMatch(path -> path.startsWith("data/spawns/Catacombs/"));
	}

	record Site(int entryNpcId, int exitNpcId, boolean necropolis, PhantomTopologyAnchor landing, PhantomTopologyAnchor farm)
	{
		boolean permits(Player player, int objectId)
		{
			return DungeonGatekeeper.mayEnter(player, objectId, necropolis);
		}
	}

	private record Catalog(PhantomTopologySnapshot snapshot, Map<String, Site> sites) { }

	Map<String, Site> sites()
	{
		final var snapshot = _topology.snapshot();
		final var current = _catalog;
		if (current != null && current.snapshot() == snapshot) { return current.sites(); }
		final Map<String, Site> result = new HashMap<>();
		for (var edge : snapshot.edges())
		{
			if (edge.backgroundEligible() || edge.bidirectional() || edge.mode() != PhantomTopologyEdgeMode.BACKGROUND
				|| !edge.fromNodeId().startsWith("entry.") || edge.sourceRefs().stream().noneMatch(path -> path.startsWith(PROOF_PREFIX))) { continue; }
			final var from = snapshot.anchorById().get(edge.fromAnchorId());
			final var farm = snapshot.anchorById().get(edge.toAnchorId());
			if (from == null || farm == null || farm.role() != PhantomTopologyAnchorRole.FARMING || !closedAnchor(farm)
				|| from.point().instanceId() != 0 || farm.point().instanceId() != 0) { continue; }
			for (int id = 31095; id <= 31119; id++)
			{
				final boolean necropolis = id <= 31102;
				if (!necropolis && id < 31114) { continue; }
				if (from.sourceRefs().contains("data/teleporters/dungeon/" + id + ".xml"))
				{
					result.putIfAbsent(farm.id(), new Site(id, id + (necropolis ? 8 : 6), necropolis, from, farm));
				}
			}
		}
		final var loaded = new Catalog(snapshot, Map.copyOf(result));
		_catalog = loaded;
		return loaded.sites();
	}

	Site inside(PhantomNavigationPoint point)
	{
		if (point.instanceId() != 0) { return null; }
		return sites().values().stream().filter(site -> sameRoom(point, site.landing().point()))
			.min(java.util.Comparator.comparingDouble((Site site) -> point.distanceTo(point(site.landing().point())))
				.thenComparingInt(Site::entryNpcId)).orElse(null);
	}

	Gate entryFrom(Site site, PhantomNavigationPoint origin)
	{
		final var entry = gate(site, true);
		return entry != null && origin.instanceId() == 0 && origin.distanceTo(entry.approach()) <= APPROACH_DISTANCE
			&& gate(site, false) != null ? entry : null;
	}

	Gate gate(Site site, boolean entering)
	{
		final var inside = destination(site.entryNpcId());
		final var outside = destination(site.exitNpcId());
		if (!matches(site.landing().point(), inside) || outside == null || !sameRoom(point(site.farm().point()), site.landing().point()))
		{
			return null;
		}
		final var npc = npc(entering ? site.entryNpcId() : site.exitNpcId());
		if (npc == null) { return null; }
		final var approach = entering ? ground(outside) : ground(npc);
		final var landing = nativeLanding(entering ? inside : outside);
		final var npcPoint = ground(npc);
		if (approach == null || landing == null || npcPoint == null || (entering ? approach.distanceTo(npcPoint) > Npc.INTERACTION_DISTANCE
			: !sameRoom(approach, site.landing().point()))) { return null; }
		return new Gate(site, entering, npc, approach, landing);
	}

	private static boolean sameRoom(PhantomNavigationPoint point, PhantomTopologyPoint anchor)
	{
		return point.instanceId() == anchor.instanceId() && Math.abs((long) point.z() - anchor.z()) <= FLOOR_TOLERANCE
			&& point.distanceTo(point(anchor)) <= INTERIOR_DISTANCE;
	}

	private static boolean matches(PhantomTopologyPoint point, TeleportLocation location)
	{
		return location != null && point.x() == location.getX() && point.y() == location.getY() && point.z() == location.getZ()
			&& point.instanceId() == location.getInstanceId();
	}

	private static PhantomNavigationPoint point(PhantomTopologyPoint point)
	{
		return new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId());
	}

	private static TeleportLocation destination(int id)
	{
		final var holder = TeleporterData.getInstance().getHolder(id, TeleportType.OTHER.name());
		if (holder == null || holder.getLocations().size() != 1) { return null; }
		final var location = holder.getLocation(0);
		// These stock Ziggurats are free. Any changed fee or siege contract needs explicit support.
		return location.getFeeCount() == 0 && location.getCastleId().isEmpty() && location.getInstanceId() == 0 ? location : null;
	}

	private static DungeonGatekeeper npc(int id)
	{
		final List<DungeonGatekeeper> active = SpawnTable.getInstance().getSpawns(id).stream().flatMap(spawn -> spawn.getSpawnedNpcs().stream())
			.filter(DungeonGatekeeper.class::isInstance).map(DungeonGatekeeper.class::cast)
			.filter(npc -> npc.getInstanceId() == 0 && npc.isSpawned() && !npc.isAlikeDead() && World.getInstance().findObject(npc.getObjectId()) == npc)
			.limit(2).toList();
		return active.size() == 1 ? active.getFirst() : null;
	}

	private static PhantomNavigationPoint ground(org.l2jmobius.gameserver.model.interfaces.ILocational location)
	{
		final var geo = GeoEngine.getInstance();
		if (!geo.hasGeo(location.getX(), location.getY())) { return null; }
		final int floor = geo.getHeight(location.getX(), location.getY(), location.getZ());
		return Math.abs((long) floor - location.getZ()) <= FLOOR_TOLERANCE
			? new PhantomNavigationPoint(location.getX(), location.getY(), floor, location.getInstanceId()) : null;
	}

	private static PhantomNavigationPoint nativeLanding(TeleportLocation location)
	{
		final var ground = ground(location);
		return ground == null ? null : new PhantomNavigationPoint(ground.x(), ground.y(), ground.z() + 5, ground.instanceId());
	}

	record Gate(Site site, boolean entering, DungeonGatekeeper npc, PhantomNavigationPoint approach, PhantomNavigationPoint landing)
	{
		boolean interactable(Player player)
		{
			final var point = PhantomNativeTravelActions.point(player);
			final var floor = ground(npc);
			return floor != null && npc.canInteract(player) && player.getInstanceId() == npc.getInstanceId()
				&& point.distanceTo(approach) <= Npc.INTERACTION_DISTANCE && new L2jNavigationBackend().canWalkDirect(point, floor);
		}

		boolean execute(Player player)
		{
			if (!PhantomNativeTravelActions.ordinary(player) || !player.hasHeadlessOutboundSession()
				|| player.isTeleporting() || player.hasPendingOwnedStore() || player.isIn7sDungeon() == entering
				|| PhantomSsqFarmAccess.npc(npc.getId()) != npc || !interactable(player)
				|| entering && !site.permits(player, player.getObjectId())) { return false; }
			final var expected = destination(entering ? site.entryNpcId() : site.exitNpcId());
			if (expected == null || !landing.equals(nativeLanding(expected))) { return false; }
			final var before = PhantomNativeTravelActions.point(player);
			npc.onBypassFeedback(player, (entering ? site.necropolis() ? "necro" : "cata" : "exit") + " 0");
			if (player.isTeleporting()) { player.onTeleported(); }
			final var after = PhantomNativeTravelActions.point(player);
			return landing.equals(after) && !after.equals(before) && player.isIn7sDungeon() == entering;
		}
	}

}
