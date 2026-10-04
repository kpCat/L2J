/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.Comparator;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Measurements choose a real existing edge for the caller's native fixture; they do not prove execution. */
public final class PhantomM1GeometryChecks
{
	private PhantomM1GeometryChecks() { }

	public record Measurement(PhantomTopologyEdge unsafeEdge, PhantomNavigationPoint waterPoint) { }
	private record Corridor(int sampled, int missing, int wet, PhantomNavigationPoint firstWater, boolean waterReentry) { }
	private enum GeometryFault { WRONG_HEIGHT }

	/** The caller creates a fresh native fixture already committed at this farming anchor. */
	public static PhantomNavigationPoint canonicalFarmStandpoint(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomRelevanceSignalPort signals)
	{
		final var anchor = route.topology().findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElseThrow();
		final var area = route.topology().findNode(anchor.nodeId()).orElseThrow().area();
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).orElseThrow();
		PhantomAssertions.assertEquals(PhantomTopologyAnchorRole.FARMING, anchor.role(), "Canonical-standpoint fixture is not a farming anchor.");
		PhantomAssertions.assertEquals(anchor.id(), background.acquisitionSnapshot(profileId).orElseThrow().position().committedAnchorId(), "Canonical-standpoint fixture requires the same committed farming anchor.");
		final PhantomNavigationPoint origin;
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
		{
			final var player = action.player();
			origin = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			PhantomAssertions.assertEquals(new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId()), origin, "Canonical-standpoint fixture did not naturally materialize at the actual canonical farming point.");
			PhantomAssertions.assertFalse(player.isMoving() || player.isCastingNow() || player.isInCombat(), "Canonical-standpoint fixture contains an unrelated native action.");
			PhantomAssertions.assertEquals(null, ZoneManager.getInstance().getZone(origin.x(), origin.y(), origin.z(), WaterZone.class), "Canonical-standpoint source is not dry land.");
		}
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, System::nanoTime, new PhantomMetrics());
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals);
		try
		{
			PhantomAssertions.assertTrue(navigation.start(), "Canonical-standpoint stock navigation did not start.");
			final boolean arrived = travel.arrive(profileId, goal);
			context.record("m1.geometry.canonicalStandpoint." + profileId, "controlledNativeFixture=true anchor=" + anchor.id() + " origin=" + origin + " arrived=" + arrived + " reason=" + travel.reason(profileId));
			PhantomAssertions.assertFalse(arrived, "Canonical farm pile was accepted without native standpoint continuation.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				final var issued = new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), player.getInstanceId());
				context.record("m1.geometry.canonicalStandpoint.target." + profileId, "actualDestination=" + issued + " moving=" + player.isMoving() + " reason=" + travel.reason(profileId));
				PhantomAssertions.assertTrue(player.isMoving() && ((issued.x() != origin.x()) || (issued.y() != origin.y())), "Canonical farm did not issue a distinct actual native standpoint.");
				PhantomAssertions.assertTrue(area.contains(new PhantomTopologyPoint(issued.x(), issued.y(), issued.z(), issued.instanceId())) && GeoEngine.getInstance().hasGeo(issued.x(), issued.y()) && (GeoEngine.getInstance().getHeight(issued.x(), issued.y(), issued.z()) == issued.z()) && (ZoneManager.getInstance().getZone(issued.x(), issued.y(), issued.z(), WaterZone.class) == null), "Actual native standpoint is not loaded, fixed-height dry land inside the factual farming area.");
				final var local = corridor(origin, issued);
				PhantomAssertions.assertTrue(new L2jNavigationBackend().canMoveDirect(origin, issued) && (local.wet == 0) && (local.missing == 0), "Actual native standpoint has no lawful stock native-direct local continuation.");
				PhantomAssertions.assertEquals(anchor.id(), background.acquisitionSnapshot(profileId).orElseThrow().position().committedAnchorId(), "Native standpoint movement changed its semantic farming anchor before capture.");
				return issued;
			}
		}
		finally
		{
			travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
			navigation.beginStop(); navigation.finishStop();
		}
	}

	/** Separate controlled wrong-height fault, always restoring the actual native pose. */
	public static void wrongHeightCanonicalFarm(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomRelevanceSignalPort signals)
	{
		final var anchor = route.topology().findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElseThrow();
		final var area = route.topology().findNode(anchor.nodeId()).orElseThrow().area();
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).orElseThrow();
		PhantomAssertions.assertEquals(PhantomTopologyAnchorRole.FARMING, anchor.role(), "Wrong-height fixture is not a farming anchor.");
		PhantomAssertions.assertEquals(anchor.id(), background.acquisitionSnapshot(profileId).orElseThrow().position().committedAnchorId(), "Wrong-height fixture requires the same committed farming anchor.");
		final var origin = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
		final int faultZ = area.contains(new PhantomTopologyPoint(origin.x(), origin.y(), origin.z() + 16, origin.instanceId())) ? origin.z() + 16 : origin.z() - 16;
		PhantomAssertions.assertTrue(area.contains(new PhantomTopologyPoint(origin.x(), origin.y(), faultZ, origin.instanceId())) && (GeoEngine.getInstance().getHeight(origin.x(), origin.y(), faultZ) != faultZ), "Wrong-height fixture lacks a factual in-area height fault.");
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, System::nanoTime, new PhantomMetrics());
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals);
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				PhantomAssertions.assertEquals(origin, new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()), "Wrong-height fixture did not naturally start at canonical farming position.");
				PhantomAssertions.assertFalse(player.isMoving() || player.isCastingNow() || player.isInCombat(), "Wrong-height fixture contains an unrelated native action.");
				player.setXYZ(origin.x(), origin.y(), faultZ);
				player.revalidateZone(true);
			}
			PhantomAssertions.assertTrue(navigation.start(), "Wrong-height stock navigation did not start.");
			final boolean arrived = travel.arrive(profileId, goal);
			context.record("m1.geometry.wrongHeight." + profileId, "fault=" + GeometryFault.WRONG_HEIGHT + " controlledTestPose=true anchor=" + anchor.id() + " canonical=" + origin + " faultZ=" + faultZ + " arrived=" + arrived + " reason=" + travel.reason(profileId));
			PhantomAssertions.assertFalse(arrived, "Wrong-height farming pose falsely accepted as successful native arrival.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				final var failure = travel.lastFailure(profileId);
				final var issued = new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), player.getInstanceId());
				final boolean corrected = player.isMoving() && GeoEngine.getInstance().hasGeo(issued.x(), issued.y()) && (GeoEngine.getInstance().getHeight(issued.x(), issued.y(), issued.z()) == issued.z()) && area.contains(new PhantomTopologyPoint(issued.x(), issued.y(), issued.z(), issued.instanceId())) && (ZoneManager.getInstance().getZone(issued.x(), issued.y(), issued.z(), WaterZone.class) == null);
				final boolean rejected = (failure != null) && (failure.disposition() == PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE) && failure.reason().contains("height") && (failure.objectId() == player.getObjectId()) && (failure.epoch() == materialization.find(profileId).orElseThrow().materializedAtNanos()) && (failure.goalId() == goal.goalId()) && (failure.revision() == goal.revision());
				PhantomAssertions.assertTrue(corrected || rejected, "Wrong-height pose produced neither lawful native correction nor exact typed height rejection.");
			}
		}
		finally
		{
			try
			{
				travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
				navigation.beginStop(); navigation.finishStop();
			}
			finally
			{
				try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
				{
					action.player().stopMove(null);
					action.player().setXYZ(origin.x(), origin.y(), origin.z());
					action.player().revalidateZone(true);
				}
			}
		}
	}

	/** Called with the caller's second existing native fixture, after its exact goal is stored. */
	public static void unsafeWaterRoute(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomRelevanceSignalPort signals, String expectedEdgeId)
	{
		final var state = background.acquisitionSnapshot(profileId).orElseThrow();
		final var step = route.route(state.position().committedAnchorId(), PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElseThrow().getFirst();
		PhantomAssertions.assertEquals(expectedEdgeId, step.id(), "Unsafe-water fixture selected a different actual production route.");
		PhantomAssertions.assertEquals(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, step.type(), "Unsafe-water fixture is not a native walking step.");
		final var target = route.topology().findAnchor(step.toAnchorId()).orElseThrow();
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(target, 0).orElseThrow();
		final PhantomNavigationPoint origin;
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
		{
			final var player = action.player();
			origin = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			PhantomAssertions.assertFalse(player.isMoving() || player.isCastingNow() || player.isInCombat(), "Unsafe-water fixture contains an unrelated native action.");
			PhantomAssertions.assertEquals(null, ZoneManager.getInstance().getZone(origin.x(), origin.y(), origin.z(), WaterZone.class), "Unsafe-water fixture did not start on dry land.");
		}
		final var destination = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
		final var measured = corridor(origin, destination);
		PhantomAssertions.assertTrue((measured.missing == 0) && (measured.wet > 0) && new L2jNavigationBackend().canMoveDirect(origin, destination), "Unsafe-water fixture lacks the measured native-direct covered water corridor.");
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, System::nanoTime, new PhantomMetrics());
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals);
		try
		{
			PhantomAssertions.assertTrue(navigation.start(), "Unsafe-water native navigation did not start.");
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Unsafe-water fixture reported an unstored arrival.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				final var issued = new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), player.getInstanceId());
				final var actual = player.isMoving() ? corridor(origin, issued) : new Corridor(0, 0, 0, null, false);
				context.record("m1.geometry.nativeUnsafeMove", "profile=" + profileId + " epoch=" + materialization.find(profileId).orElseThrow().materializedAtNanos() + " edge=" + step.id() + " origin=" + origin + " destination=" + issued + " moving=" + player.isMoving() + " cells=" + actual.sampled + " missing=" + actual.missing + " wet=" + actual.wet + " reason=" + travel.reason(profileId));
				PhantomAssertions.assertFalse(player.isMoving() && ((actual.wet > 0) || (actual.missing > 0)), "Dry farm issued actual native MOVE_TO through a measured WaterZone or missing geodata corridor.");
				PhantomAssertions.assertEquals("travel.native_segment_water_entry", travel.reason(profileId), "Unsafe-water check passed because an unrelated native action failed.");
			}
		}
		finally
		{
			travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
			navigation.beginStop(); navigation.finishStop();
		}
		lawfulWaterExit(context, profileId, materialization, background, route, goal, signals, origin, destination);
	}

	/** Controlled TEST pose on the same factual corridor; it is not natural-cohort evidence. */
	private static void lawfulWaterExit(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomRelevanceSignalPort signals, PhantomNavigationPoint dryOrigin, PhantomNavigationPoint destination)
	{
		final var geo = GeoEngine.getInstance();
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(dryOrigin.x()), GeoEngine.getGeoY(dryOrigin.y()), GeoEngine.getGeoX(destination.x()), GeoEngine.getGeoY(destination.y()));
		PhantomNavigationPoint lastWater = null;
		int z = dryOrigin.z();
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x());
			final int y = GeoEngine.getWorldY(cells.y());
			z = geo.getHeight(x, y, z);
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { lastWater = new PhantomNavigationPoint(x, y, z, dryOrigin.instanceId()); }
		}
		PhantomAssertions.assertTrue((lastWater != null) && new L2jNavigationBackend().canMoveDirect(lastWater, destination), "Lawful-exit fixture has no actual stock native-direct shoreline continuation.");
		final var shoreline = corridor(lastWater, destination);
		PhantomAssertions.assertTrue((shoreline.missing == 0) && (shoreline.wet > 0) && !shoreline.waterReentry, "Lawful-exit fixture does not contain a loaded initial wet prefix followed by dry land.");
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, System::nanoTime, new PhantomMetrics());
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals);
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				player.stopMove(null);
				player.setXYZ(lastWater.x(), lastWater.y(), lastWater.z());
				player.revalidateZone(true);
				PhantomAssertions.assertTrue(player.isInsideZone(ZoneId.WATER), "Lawful-exit controlled native pose did not enter WaterZone.");
			}
			PhantomAssertions.assertTrue(navigation.start(), "Lawful-exit stock navigation did not start.");
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Lawful-exit fixture reported an unstored arrival.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				final var issued = new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), player.getInstanceId());
				final var actual = corridor(lastWater, issued);
				context.record("m1.geometry.lawfulWaterExit", "controlledTestPose=true profile=" + profileId + " origin=" + lastWater + " actualDestination=" + issued + " moving=" + player.isMoving() + " cells=" + actual.sampled + " missing=" + actual.missing + " wet=" + actual.wet + " reentry=" + actual.waterReentry + " reason=" + travel.reason(profileId));
				PhantomAssertions.assertTrue(player.isMoving() && travel.reason(profileId).equals("travel.native_walking") && (lastWater.distanceTo(issued) > 0), "Lawful native initial-water exit was blocked by the dry-land guard.");
				PhantomAssertions.assertTrue((actual.missing == 0) && !actual.waterReentry, "Lawful exit issued a different unsafe native segment.");
			}
		}
		finally
		{
			try
			{
				travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
				navigation.beginStop(); navigation.finishStop();
			}
			finally
			{
				try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
				{
					action.player().stopMove(null);
					action.player().setXYZ(dryOrigin.x(), dryOrigin.y(), dryOrigin.z());
					action.player().revalidateZone(true);
				}
			}
		}
	}

	private static Corridor corridor(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final var geo = GeoEngine.getInstance();
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		int sampled = 0;
		int missing = 0;
		int wet = 0;
		int z = from.z();
		boolean dry = ZoneManager.getInstance().getZone(from.x(), from.y(), from.z(), WaterZone.class) == null;
		boolean waterReentry = false;
		PhantomNavigationPoint first = null;
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x());
			final int y = GeoEngine.getWorldY(cells.y());
			if (!geo.hasGeo(x, y)) { missing++; }
			z = geo.getHeight(x, y, z);
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null)
			{
				if (dry) { waterReentry = true; }
				wet++;
				if (first == null) { first = new PhantomNavigationPoint(x, y, z, from.instanceId()); }
			}
			else { dry = true; }
			sampled++;
		}
		return new Corridor(sampled, missing, wet, first, waterReentry);
	}

	public static Measurement measure(PhantomTestContext context, PhantomTopologyQuery topology)
	{
		final var geo = GeoEngine.getInstance();
		final var zones = ZoneManager.getInstance();
		final var backend = new L2jNavigationBackend();
		for (var anchor : topology.snapshot().anchors().stream().filter(value -> value.sourceRefs().contains("data/spawns/ElvenTerritory/ElvenStarting.xml") || value.id().equals("population.ingress.elf.01")).sorted(Comparator.comparing(value -> value.id())).limit(32).toList())
		{
			final var p = anchor.point();
			final int z = geo.getHeight(p.x(), p.y(), p.z());
			final var water = zones.getZone(p.x(), p.y(), z, WaterZone.class);
			context.record("m1.geometry.anchor." + anchor.id(), "id=" + anchor.id() + " xyz=" + p.x() + "," + p.y() + "," + z + " seedZ=" + p.z() + " geo=" + geo.hasGeo(p.x(), p.y()) + " fixed=" + (z == geo.getHeight(p.x(), p.y(), z)) + " delta=" + Math.abs((long) z - p.z()) + " tolerance=" + anchor.validationTolerance() + " nsweAll=" + geo.checkNearestNswe(GeoEngine.getGeoX(p.x()), GeoEngine.getGeoY(p.y()), z, 15) + " water=" + (water == null ? "none" : water.getId() + ":" + water.getWaterZ()));
		}
		Measurement firstUnsafe = null;
		for (var edge : topology.snapshot().edges().stream().filter(value -> value.backgroundEligible() && (value.fromAnchorId() != null) && (value.toAnchorId() != null)).filter(value -> value.sourceRefs().contains("data/spawns/ElvenTerritory/ElvenStarting.xml") || value.fromAnchorId().equals("population.ingress.elf.01")).sorted(Comparator.comparing(PhantomTopologyEdge::id)).limit(128).toList())
		{
			final var fromAnchor = topology.findAnchor(edge.fromAnchorId()).orElseThrow();
			final var toAnchor = topology.findAnchor(edge.toAnchorId()).orElseThrow();
			final var from = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(fromAnchor, 0).orElse(null);
			final var to = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(toAnchor, 0).orElse(null);
			if ((from == null) || (to == null))
			{
					context.record("m1.geometry.edge." + edge.id(), "id=" + edge.id() + " canonical=UNPROVEN");
				continue;
			}
			final var origin = new PhantomNavigationPoint(from.x(), from.y(), from.z(), from.instanceId());
			final var destination = new PhantomNavigationPoint(to.x(), to.y(), to.z(), to.instanceId());
			final boolean fromWater = zones.getZone(from.x(), from.y(), from.z(), WaterZone.class) != null;
			final boolean toWater = zones.getZone(to.x(), to.y(), to.z(), WaterZone.class) != null;
			final boolean direct = backend.canMoveDirect(origin, destination);
			final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
			int missing = 0;
			int wet = 0;
			int sampled = 0;
			int z = from.z();
			PhantomNavigationPoint waterPoint = null;
			while (cells.next())
			{
				final int x = GeoEngine.getWorldX(cells.x());
				final int y = GeoEngine.getWorldY(cells.y());
				if (!geo.hasGeo(x, y)) { missing++; }
				z = geo.getHeight(x, y, z);
				if (zones.getZone(x, y, z, WaterZone.class) != null)
				{
					wet++;
					if (waterPoint == null) { waterPoint = new PhantomNavigationPoint(x, y, z, 0); }
				}
				sampled++;
			}
			context.record("m1.geometry.edge." + edge.id(), "id=" + edge.id() + " from=" + from.x() + "," + from.y() + "," + from.z() + " to=" + to.x() + "," + to.y() + "," + to.z() + " nativeDirect=" + direct + " fromWater=" + fromWater + " toWater=" + toWater + " cells=" + sampled + " missing=" + missing + " wet=" + wet);
			if ((firstUnsafe == null) && direct && !fromWater && (wet > 0) && (missing == 0)) { firstUnsafe = new Measurement(edge, waterPoint); }
		}
		context.record("m1.geometry.measuredUnsafe", firstUnsafe == null ? "NONE; native unsafe execution remains UNPROVEN" : "edge=" + firstUnsafe.unsafeEdge().id() + " water=" + firstUnsafe.waterPoint());
		return firstUnsafe;
	}
}
