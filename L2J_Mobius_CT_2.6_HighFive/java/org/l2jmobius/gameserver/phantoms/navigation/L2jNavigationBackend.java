/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;

/**
 * Lazy factual adapter over the current High Five geo/pathfinding APIs.
 */
public final class L2jNavigationBackend implements PhantomNavigationBackend
{
	@Override
	public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
	{
		final GeoEngine geoEngine = GeoEngine.getInstance();
		final boolean startGeo = geoEngine.hasGeo(origin.x(), origin.y());
		final boolean targetGeo = geoEngine.hasGeo(destination.x(), destination.y());
		final PhantomNavigationCapability mode;
		if (!startGeo && !targetGeo)
		{
			mode = PhantomNavigationCapability.NO_GEODATA;
		}
		else if (startGeo != targetGeo)
		{
			mode = PhantomNavigationCapability.PARTIAL_GEODATA;
		}
		else if (GeoEngineConfig.PATHFINDING > 0)
		{
			mode = PhantomNavigationCapability.GEODATA_PATHFINDING;
		}
		else
		{
			mode = PhantomNavigationCapability.GEODATA_DIRECT_ONLY;
		}
		return new CapabilitySnapshot(mode, mode.ordinal());
	}

	@Override
	public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
	{
		return GeoEngine.getInstance().canMoveToTarget(origin.x(), origin.y(), origin.z(), destination.x(), destination.y(), destination.z(), origin.instanceId());
	}

	/** The existing visible-travel dry-cell/height contract, plus factual doors/NSWE. */
	public boolean canWalkDirect(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final var geo = GeoEngine.getInstance();
		if (from.instanceId() != to.instanceId() || !geo.hasGeo(from.x(), from.y()) || !geo.hasGeo(to.x(), to.y()) || geo.getHeight(to.x(), to.y(), to.z()) != to.z()) { return false; }
		boolean dry = ZoneManager.getInstance().getZone(from.x(), from.y(), from.z(), WaterZone.class) == null;
		int z = from.z();
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x()), y = GeoEngine.getWorldY(cells.y());
			if (!geo.hasGeo(x, y)) { return false; }
			z = geo.getHeight(x, y, z);
			if (z != geo.getHeight(x, y, z)) { return false; }
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { if (dry) { return false; } }
			else { dry = true; }
		}
		return ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), WaterZone.class) == null && canMoveDirect(from, to);
	}

	public PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock)
	{
		return findWalkCorridor(request, cancellation, policy, clock, this, java.util.Set.of());
	}
	public PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges)
	{
		return findWalkCorridor(request, cancellation, policy, clock, this, java.util.Set.copyOf(blockedEdges));
	}
	private PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges)
	{
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				if (z != geo.getHeight(x, y, z) || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { return null; }
				return new PhantomNavigationPoint(x, y, z, surface.instanceId());
			}
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return !blockedEdges.contains(new PhantomWalkCorridorPlanner.Edge(from, to)) && canWalkDirect(from, to); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return paths.findPath(new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(), request.deadlineLogicalNanos(), request.maximumRouteDistance()), cancellation);
			}
		}, clock);
		return planner.plan(request, cancellation, policy);
	}

	public record TravelPlan(PhantomNavigationResult.Status status, PhantomTravelStrategy.Candidate candidate) { }
	/** WALK overlay plus at most seven complete GK candidates with directly proven ingress/egress. */
	public TravelPlan planTravel(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options)
	{
		return planTravel(request, cancellation, policy, clock, options, this);
	}
	/** A decorated native backend still sees each actual bounded stock query. */
	public TravelPlan planTravel(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options, PhantomNavigationBackend paths)
	{
		if (cancellation.changedSince(0)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
		final long generation = cancellation.generation();
		final List<PhantomTravelStrategy.Candidate> candidates = new ArrayList<>();
		int nodes = 0, edges = 0;
		PhantomNavigationResult.Status absent = PhantomNavigationResult.Status.NO_PATH;
		if (request.origin().distanceTo(request.destination()) <= policy.maximumLocalStraightDistance())
		{
			final var walk = findWalkCorridor(request, cancellation, policy, clock, paths, options.blockedEdges());
			nodes = walk.nodes(); edges = walk.edges();
			if (walk.status() == PhantomWalkCorridorPlanner.Status.FOUND) { candidates.add(new PhantomTravelStrategy.Candidate("walk", List.of(walkStage(request.origin(), request.destination(), walk.points(), options.speed())))); }
			else if (walk.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; }
		}
		if (options.gatekeeperAllowed())
		{
			final var geo = GeoEngine.getInstance();
			final var returning = options.returnLanding();
			final var proposed = options.legs().stream().filter(leg -> (Math.hypot((long)leg.sourceX() - request.origin().x(), (long)leg.sourceY() - request.origin().y()) <= policy.maximumLocalStraightDistance()
				|| (returning != null && Math.hypot((long)leg.sourceX() - returning.x(), (long)leg.sourceY() - returning.y()) <= policy.maximumLocalStraightDistance()))
				&& Math.hypot((long)leg.destinationX() - request.destination().x(), (long)leg.destinationY() - request.destination().y()) <= policy.maximumLocalStraightDistance())
				.sorted(Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg leg) -> Math.hypot((long)leg.sourceX() - request.origin().x(), (long)leg.sourceY() - request.origin().y()) + Math.hypot((long)leg.destinationX() - request.destination().x(), (long)leg.destinationY() - request.destination().y())).thenComparing(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg::id)).limit(7).toList();
			for (var leg : proposed)
			{
				if (cancellation.changedSince(generation)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
				if (clock.getAsLong() >= request.deadlineLogicalNanos()) { return new TravelPlan(PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
				if (nodes + 2 > 128 || edges + 3 > 512) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; break; }
				nodes += 2; edges += 3;
				final long fee = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(leg, options.classIndex(), options.level(), options.epochMinute());
				if (fee > options.adena() || !PhantomNativeTravelActions.available(leg)) { continue; }
				final var source = new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), geo.getHeight(leg.sourceX(), leg.sourceY(), leg.sourceZ()), 0);
				// Stock NORMAL teleport is synchronous; native Creature adds five to its factual geo landing Z.
				final var landing = new PhantomNavigationPoint(leg.destinationX(), leg.destinationY(), geo.getHeight(leg.destinationX(), leg.destinationY(), leg.destinationZ()) + 5, 0);
				if (!canTravelWalk(options, landing, request.destination())) { continue; }
				final var teleport = new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, source, landing, List.of(), leg, 0, fee, 0);
				final var egress = walkStage(landing, request.destination(), List.of(request.destination()), options.speed());
				if (canTravelWalk(options, request.origin(), source) && candidates.size() < 8)
				{
					addCandidate(candidates, "gk." + leg.id(), List.of(walkStage(request.origin(), source, List.of(source), options.speed()), teleport, egress), request, policy);
				}
				if (returning != null && candidates.size() < 8 && nodes + 1 <= 128 && edges + 4 <= 512 && canTravelWalk(options, returning, source))
				{
					nodes++; edges += 4;
					addCandidate(candidates, "return-gk." + leg.id(), List.of(walkStage(request.origin(), request.origin(), List.of(request.origin()), options.speed()),
						new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK, request.origin(), returning, List.of(), null, options.returnMillis(), 0, 0), walkStage(returning, source, List.of(source), options.speed()), teleport, egress), request, policy);
				}
			}
		}
		if (cancellation.changedSince(generation)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
		if (clock.getAsLong() >= request.deadlineLogicalNanos()) { return new TravelPlan(PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
		final var selected = PhantomTravelStrategy.select(candidates, options.adena(), options.scrolls(), options.remainingMillis());
		return new TravelPlan(selected.status() == PhantomTravelStrategy.Status.FOUND ? PhantomNavigationResult.Status.PATH_FOUND : absent, selected.candidate());
	}
	private static PhantomTravelStrategy.Stage walkStage(PhantomNavigationPoint from, PhantomNavigationPoint to, List<PhantomNavigationPoint> points, double speed)
	{
		return new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, from, to, points, null, (long)Math.ceil(1000 * PhantomTravelStrategy.distance(from, points) / speed), 0, 0);
	}
	private static void addCandidate(List<PhantomTravelStrategy.Candidate> candidates, String id, List<PhantomTravelStrategy.Stage> stages, PhantomNavigationRequest request, PhantomNavigationPolicy policy)
	{
		if (stages.stream().filter(s -> s.mode() == PhantomTravelStrategy.Mode.WALK).mapToDouble(s -> PhantomTravelStrategy.distance(s.from(), s.points())).sum() <= Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance())) { candidates.add(new PhantomTravelStrategy.Candidate(id, stages)); }
	}
	private boolean canTravelWalk(PhantomTravelStrategy.Options options, PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return !options.blockedEdges().contains(new PhantomWalkCorridorPlanner.Edge(from,to)) && canWalkDirect(from,to);
	}

	@Override
	public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
	{
		final List<GeoLocation> path = PathFinding.getInstance().findPath(
			request.origin().x(),
			request.origin().y(),
			request.origin().z(),
			request.destination().x(),
			request.destination().y(),
			request.destination().z(),
			request.origin().instanceId(),
			true);
		if (path == null)
		{
			return null;
		}
		final List<PhantomNavigationPoint> copy = new ArrayList<>(path.size());
		for (GeoLocation point : path)
		{
			copy.add(new PhantomNavigationPoint(point.getX(), point.getY(), point.getZ(), request.origin().instanceId()));
		}
		return List.copyOf(copy);
	}
}
