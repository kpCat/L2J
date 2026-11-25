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

	/** Resolve only the authored XY/instance within its existing vertical validation tolerance. */
	public java.util.Optional<PhantomNavigationPoint> walkSurface(PhantomNavigationPoint hint, int verticalTolerance)
	{
		if (hint == null || verticalTolerance < 0 || verticalTolerance > 500) { return java.util.Optional.empty(); }
		final var geo = GeoEngine.getInstance();
		// Preserve Navigation's existing NO_GEODATA policy when no surface is known.
		if (!geo.hasGeo(hint.x(), hint.y())) { return java.util.Optional.of(hint); }
		final int z = geo.getHeight(hint.x(), hint.y(), hint.z());
		if (z < org.l2jmobius.gameserver.model.World.WORLD_Z_MIN || z > org.l2jmobius.gameserver.model.World.WORLD_Z_MAX || Math.abs((long) z - hint.z()) > verticalTolerance || geo.getHeight(hint.x(), hint.y(), z) != z || ZoneManager.getInstance().getZone(hint.x(), hint.y(), z, WaterZone.class) != null) { return java.util.Optional.empty(); }
		return java.util.Optional.of(new PhantomNavigationPoint(hint.x(), hint.y(), z, hint.instanceId()));
	}

	PhantomNavigationPoint learnedSurface(PhantomNavigationPoint hint, int tolerance)
	{
		return hint == null || !GeoEngine.getInstance().hasGeo(hint.x(), hint.y()) ? null : walkSurface(hint, tolerance).orElse(null);
	}

	boolean canWalkLearned(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final var zones = ZoneManager.getInstance();
		final long generation = zones.loadedGeneration();
		return generation >= 0 && learnedSurface(from, 0) != null && learnedSurface(to, 0) != null
			&& canWalkDirect(from, to) && zones.loadedGeneration() == generation;
	}

	boolean canWalkLearnedFromStage(PhantomNavigationPoint from, PhantomNavigationPoint to, PhantomNavigationPoint origin)
	{
		if (canWalkLearned(from, to)) { return true; }
		if (!from.equals(origin)) { return false; }
		// The native GK/Return receipt can place the actor exactly five units over the floor.
		final var floor = learnedSurface(from, 5);
		return floor != null && from.z() == floor.z() + 5 && canWalkLearned(floor, to) && canWalkDirect(from, to);
	}

	/** The existing visible-travel dry-cell/height contract, plus factual doors/NSWE. */
	@Override
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
	public PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths)
	{
		return findWalkCorridor(request,cancellation,policy,clock,paths,java.util.Set.of());
	}
	/** One proven physical checkpoint; never a certificate for the unsearched tail. */
	public PhantomWalkCorridorPlanner.Result findWalkEpisode(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths)
	{
		return findWalkEpisode(request, cancellation, policy, clock, paths, java.util.Set.of());
	}
	public PhantomWalkCorridorPlanner.Result findWalkEpisode(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomNavigationPoint> visited)
	{
		return findWalkCorridor(request, cancellation, policy, clock, paths, java.util.Set.of(), new PhantomWalkCorridorPlanner.Budget(), true, visited);
	}
	PhantomWalkCorridorPlanner.Result findSoloWalk(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blocked, boolean episode, java.util.Set<PhantomNavigationPoint> visited)
	{
		return findWalkCorridor(request, cancellation, policy, clock, paths, blocked, new PhantomWalkCorridorPlanner.Budget(), episode, visited);
	}
	private PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges)
	{
		return findWalkCorridor(request, cancellation, policy, clock, paths, blockedEdges, new PhantomWalkCorridorPlanner.Budget());
	}
	private PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges, PhantomWalkCorridorPlanner.Budget budget)
	{
		return findWalkCorridor(request, cancellation, policy, clock, paths, blockedEdges, budget, false);
	}
	private PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges, PhantomWalkCorridorPlanner.Budget budget, boolean episode)
	{
		return findWalkCorridor(request, cancellation, policy, clock, paths, blockedEdges, budget, episode, java.util.Set.of());
	}
	private PhantomWalkCorridorPlanner.Result findWalkCorridor(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomNavigationBackend paths, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges, PhantomWalkCorridorPlanner.Budget budget, boolean episode, java.util.Set<PhantomNavigationPoint> visited)
	{
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				if (z < org.l2jmobius.gameserver.model.World.WORLD_Z_MIN || z > org.l2jmobius.gameserver.model.World.WORLD_Z_MAX || z != geo.getHeight(x, y, z) || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { return null; }
				return new PhantomNavigationPoint(x, y, z, surface.instanceId());
			}
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return request.permitsWalkSegment(from, to) && !blockedEdges.contains(new PhantomWalkCorridorPlanner.Edge(from, to)) && canWalkDirect(from, to); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return paths.findPath(new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(), request.deadlineLogicalNanos(), request.maximumRouteDistance(), request.pveThreats()), cancellation);
			}
		}, clock);
		return episode ? planner.planEpisode(request, cancellation, policy, visited) : planner.plan(request, cancellation, policy, budget);
	}

	public record TravelPlan(PhantomNavigationResult.Status status, PhantomTravelStrategy.Candidate candidate) { }

	List<PhantomTravelStrategy.Stage> learnedSections(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation,
		PhantomNavigationPolicy policy, LongSupplier clock, PhantomRouteLearning memory, java.util.Set<PhantomWalkCorridorPlanner.Edge> blocked, double speed)
	{
		if (memory == null || request.origin().instanceId() != 0) { return List.of(); }
		final long geo = GeoEngine.getInstance().generation(), zones = ZoneManager.getInstance().loadedGeneration();
		final long sliceEnd = Math.min(request.deadlineLogicalNanos(), clock.getAsLong() + 250_000_000L);
		final var nativeQueries = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.function.BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> walk =
			(from, to) -> request.permitsWalkSegment(from, to) && canWalkLearnedFromStage(from, to, request.origin());
		final java.util.function.BooleanSupplier interrupted = () -> cancellation.changedSince(0) || clock.getAsLong() >= sliceEnd
			|| geo != GeoEngine.getInstance().generation() || zones != ZoneManager.getInstance().loadedGeneration();
		final List<PhantomNavigationPoint> path = memory.itinerary(request, blocked, System.currentTimeMillis(),
			walk, interrupted, (from, to) ->
			{
				if (walk.test(from, to)) { return List.of(to); }
				if (interrupted.getAsBoolean() || nativeQueries.incrementAndGet() > 4
					|| learnedSurface(to, 0) == null || Math.abs((long) from.z() - to.z()) > 384) { return List.of(); }
				final var connector = new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(),
					sliceEnd, request.maximumRouteDistance(), request.pveThreats());
				final var proof = PhantomNativeRouteContract.normalize(from, to, findPath(connector, cancellation), policy,
					PhantomNativeTravelActions.LOCAL_WALK_DISTANCE, (a, b) -> !blocked.contains(new PhantomWalkCorridorPlanner.Edge(a, b)) && walk.test(a, b),
					() -> interrupted.getAsBoolean() ? PhantomNavigationResult.Status.DEADLINE_EXPIRED : PhantomNavigationResult.Status.PATH_FOUND);
				return proof.waypoints();
			}, true);
		return PhantomTravelStrategy.walkSections(request.origin(), path, speed, policy.maximumWaypoints());
	}

	/** The bounded legacy planner still owns its callers. Ordinary travel also accepts a segmented itinerary. */
	PhantomTravelStrategy.Candidate planItinerary(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation,
		PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options, PhantomRouteLearning memory)
	{
		if (memory == null || request.origin().instanceId() != 0) { return null; }
		final long sliceDeadline = Math.min(request.deadlineLogicalNanos(), clock.getAsLong() + 400_000_000L);
		if (sliceDeadline <= request.submittedLogicalNanos() || clock.getAsLong() >= sliceDeadline) { return null; }
		final var sliced = new PhantomNavigationRequest(request.profileId(), request.origin(), request.destination(), request.submittedLogicalNanos(),
			sliceDeadline, request.maximumRouteDistance(), request.pveThreats());
		final var walk = itineraryWalk(sliced, sliced.origin(), sliced.destination(), cancellation, policy, clock, memory, options, false);
		PhantomTravelStrategy.Candidate best = walk.isEmpty() ? null : new PhantomTravelStrategy.Candidate("learned.walk", walk, true);
		if (!options.gatekeeperAllowed() || cancellation.changedSince(0) || clock.getAsLong() >= sliceDeadline) { return best; }
		final var transfers = new ArrayList<PhantomTravelStrategy.Stage>();
		if (options.returnLanding() != null)
		{
			transfers.add(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK, request.origin(), options.returnLanding(),
				List.of(), null, options.returnMillis(), 0, 0));
		}
		for (var leg : options.legs())
		{
			final long reserve = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.phantomMaximumFee(leg, options.classIndex(), options.level());
			if (reserve > options.adena() || !PhantomNativeTravelActions.available(leg)) { continue; }
			final var endpoints = PhantomNativeTravelActions.gatekeeperEndpoints(leg);
			transfers.add(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, endpoints.source(), endpoints.landing(), List.of(), leg, 0, reserve, 0));
		}
		final var ranked = transfers.stream().sorted(Comparator.comparingDouble(stage -> stage.millis()
			+ 1000 * (request.origin().distanceTo(stage.from()) + stage.to().distanceTo(request.destination())) / options.speed())).limit(3).toList();
		for (var transfer : ranked)
		{
			if (cancellation.changedSince(0) || clock.getAsLong() >= sliceDeadline) { break; }
			final double lowerMillis = transfer.millis() + 1000 * (request.origin().distanceTo(transfer.from()) + transfer.to().distanceTo(request.destination())) / options.speed();
			if (best != null && lowerMillis >= best.millis()) { continue; }
			final var ingress = itineraryWalk(sliced, sliced.origin(), transfer.from(), cancellation, policy, clock, memory, options, true);
			if (ingress.isEmpty()) { continue; }
			final var egress = itineraryWalk(sliced, transfer.to(), sliced.destination(), cancellation, policy, clock, memory, options, true);
			if (egress.isEmpty()) { continue; }
			final var stages = new ArrayList<>(ingress);
			stages.add(transfer);
			stages.addAll(egress);
			if (stages.size() > PhantomTravelStrategy.MAX_ITINERARY_SECTIONS) { continue; }
			final var candidate = new PhantomTravelStrategy.Candidate(transfer.mode() == PhantomTravelStrategy.Mode.GK ? "learned.gk." + transfer.gatekeeper().id()
				: "learned.return", stages, true);
			if (best == null || candidate.millis() < best.millis()) { best = candidate; }
		}
		return best;
	}

	private List<PhantomTravelStrategy.Stage> itineraryWalk(PhantomNavigationRequest request, PhantomNavigationPoint from, PhantomNavigationPoint to,
		PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomRouteLearning memory,
		PhantomTravelStrategy.Options options, boolean localFallback)
	{
		if (from.equals(to)) { return List.of(walkStage(from, to, List.of(to), options.speed())); }
		final var stage = new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(),
			request.deadlineLogicalNanos(), request.maximumRouteDistance(), request.pveThreats());
		if (from.distanceTo(to) <= PhantomNativeTravelActions.LOCAL_WALK_DISTANCE && !options.blockedEdges().contains(new PhantomWalkCorridorPlanner.Edge(from, to))
			&& stage.permitsWalkSegment(from, to) && canWalkDirect(from, to))
		{
			return List.of(walkStage(from, to, List.of(to), options.speed()));
		}
		final var learned = learnedSections(stage, cancellation, policy, clock, memory, options.blockedEdges(), options.speed());
		if (!learned.isEmpty() || !localFallback || from.distanceTo(to) > PhantomNativeTravelActions.LOCAL_WALK_DISTANCE
			|| cancellation.changedSince(0) || clock.getAsLong() >= request.deadlineLogicalNanos()) { return learned; }
		final var corridor = findSoloWalk(stage, cancellation, policy, clock, this, options.blockedEdges(), false, java.util.Set.of());
		return corridor.status() == PhantomWalkCorridorPlanner.Status.FOUND
			? PhantomTravelStrategy.walkSections(from, corridor.points(), options.speed(), policy.maximumWaypoints()) : List.of();
	}

	/** WALK overlay plus at most seven complete GK candidates sharing bounded native corridor proofs. */
	public TravelPlan planTravel(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options)
	{
		return planTravel(request, cancellation, policy, clock, options, this);
	}
	/** A decorated native backend still sees each actual bounded stock query. */
	public TravelPlan planTravel(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options, PhantomNavigationBackend paths)
	{
		return planTravel(request, cancellation, policy, clock, options, paths, null, new PhantomWalkCorridorPlanner.Budget());
	}

	TravelPlan planTravel(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellation, PhantomNavigationPolicy policy, LongSupplier clock, PhantomTravelStrategy.Options options, PhantomNavigationBackend paths,
		PhantomRouteLearning memory, PhantomWalkCorridorPlanner.Budget budget)
	{
		if (cancellation.changedSince(0)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
		final long generation = cancellation.generation();
		final List<PhantomTravelStrategy.Candidate> candidates = new ArrayList<>();
		final var corridors = new java.util.HashMap<PhantomWalkCorridorPlanner.Edge, PhantomWalkCorridorPlanner.Result>();
		final java.util.function.BiFunction<PhantomNavigationPoint, PhantomNavigationPoint, PhantomWalkCorridorPlanner.Result> walk = (from, to) -> corridors.computeIfAbsent(new PhantomWalkCorridorPlanner.Edge(from, to), edge ->
		{
			final var stage = new PhantomNavigationRequest(request.profileId(), from, to, request.submittedLogicalNanos(), request.deadlineLogicalNanos(), request.maximumRouteDistance(), request.pveThreats());
			final var blocked = options.blockedEdges();
			List<PhantomNavigationPoint> learned = List.of();
			if (memory != null && from.instanceId() == 0 && to.instanceId() == 0 && !from.equals(to)
				&& from.distanceTo(to) <= policy.maximumLocalStraightDistance() && !budget.learnedChecksExhausted() && !cancellation.changedSince(generation)
				&& clock.getAsLong() < request.deadlineLogicalNanos()
				&& (blocked.contains(edge) || !stage.permitsWalkSegment(from, to) || !canWalkDirect(from, to))
				&& budget.reserveLearnedProbe())
			{
				final long geoGeneration = GeoEngine.getInstance().generation();
				final long zoneGeneration = ZoneManager.getInstance().loadedGeneration();
				learned = memory.provenTravelWalk(stage, policy, blocked, System.currentTimeMillis(),
					(start, end) -> stage.permitsWalkSegment(start, end) && budget.reserveLearnedCheck() && canWalkLearnedFromStage(start, end, from),
					() -> cancellation.changedSince(generation) || clock.getAsLong() >= request.deadlineLogicalNanos() || budget.learnedChecksExhausted());
				if (geoGeneration != GeoEngine.getInstance().generation() || zoneGeneration != ZoneManager.getInstance().loadedGeneration()) { learned = List.of(); }
			}
			final var stock = findWalkCorridor(stage, cancellation, policy, clock, paths, blocked, budget);
			// Preserve the stock itinerary whenever the learned option needs more time or waypoints.
			if (!learned.isEmpty() && (stock.status() == PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET
				|| stock.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT
				|| stock.status() == PhantomWalkCorridorPlanner.Status.FOUND && learned.size() <= stock.points().size()
					&& PhantomTravelStrategy.distance(from, learned) <= PhantomTravelStrategy.distance(from, stock.points())))
			{
				return new PhantomWalkCorridorPlanner.Result(PhantomWalkCorridorPlanner.Status.FOUND, learned, stock.nodes(), stock.edges(), stock.queries(), stock.labels());
			}
			return stock;
		});
		PhantomNavigationResult.Status absent = PhantomNavigationResult.Status.NO_PATH;
		if (request.origin().distanceTo(request.destination()) <= policy.maximumLocalStraightDistance())
		{
			final var corridor = walk.apply(request.origin(), request.destination());
			budget.recordDirectWalk(corridor);
			if (corridor.status() == PhantomWalkCorridorPlanner.Status.FOUND) { candidates.add(new PhantomTravelStrategy.Candidate("walk", List.of(walkStage(request.origin(), request.destination(), corridor.points(), options.speed())))); }
			else if (corridor.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; }
		}
		if (options.gatekeeperAllowed())
		{
			final var returning = options.returnLanding();
			final var proposed = options.legs().stream().filter(leg -> (Math.hypot((long)leg.sourceX() - request.origin().x(), (long)leg.sourceY() - request.origin().y()) <= policy.maximumLocalStraightDistance()
				|| (returning != null && Math.hypot((long)leg.sourceX() - returning.x(), (long)leg.sourceY() - returning.y()) <= policy.maximumLocalStraightDistance()))
				&& Math.hypot((long)leg.destinationX() - request.destination().x(), (long)leg.destinationY() - request.destination().y()) <= policy.maximumLocalStraightDistance())
				.sorted(Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg leg) -> Math.hypot((long)leg.sourceX() - request.origin().x(), (long)leg.sourceY() - request.origin().y()) + Math.hypot((long)leg.destinationX() - request.destination().x(), (long)leg.destinationY() - request.destination().y())).thenComparing(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg::id)).limit(7).toList();
			for (var leg : proposed)
			{
				if (cancellation.changedSince(generation)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
				if (clock.getAsLong() >= request.deadlineLogicalNanos()) { return new TravelPlan(PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
				if (!budget.reserve(2, 3)) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; break; }
				final long fee = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.phantomFee(leg, options.classIndex(), options.level(), options.epochMinute());
				if (fee > options.adena() || !PhantomNativeTravelActions.available(leg)) { continue; }
				final var endpoints = PhantomNativeTravelActions.gatekeeperEndpoints(leg);
				final var source = endpoints.source();
				final var landing = endpoints.landing();
				final var egress = walk.apply(landing, request.destination());
				if (egress.status() != PhantomWalkCorridorPlanner.Status.FOUND) { if (egress.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; } continue; }
				final var teleport = new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, source, landing, List.of(), leg, 0, fee, 0);
				final var egressStage = walkStage(landing, request.destination(), egress.points(), options.speed());
				if (request.origin().distanceTo(source) <= policy.maximumLocalStraightDistance() && candidates.size() < 8)
				{
					final var ingress = walk.apply(request.origin(), source);
					if (ingress.status() == PhantomWalkCorridorPlanner.Status.FOUND) { addCandidate(candidates, "gk." + leg.id(), List.of(walkStage(request.origin(), source, ingress.points(), options.speed()), teleport, egressStage), request, policy); }
					else if (ingress.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; }
				}
				if (returning != null && candidates.size() < 8 && budget.reserve(1, 4))
				{
					final var ingress = walk.apply(returning, source);
					if (ingress.status() == PhantomWalkCorridorPlanner.Status.FOUND) { addCandidate(candidates, "return-gk." + leg.id(), List.of(walkStage(request.origin(), request.origin(), List.of(request.origin()), options.speed()),
						new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.RETURN_GK, request.origin(), returning, List.of(), null, options.returnMillis(), 0, 0), walkStage(returning, source, ingress.points(), options.speed()), teleport, egressStage), request, policy); }
					else if (ingress.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT) { absent = PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED; }
				}
			}
		}
		if (cancellation.changedSince(generation)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
		if (clock.getAsLong() >= request.deadlineLogicalNanos()) { return new TravelPlan(PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
		final var selected = selectTravel(candidates, options);
		if (selected.candidate() == null)
		{
			final var composite = PhantomCompositeTravelPlanner.plan(request, policy, options, walk, (x, y, hint) ->
			{
				final var geo = GeoEngine.getInstance();
				return geo.hasGeo(x, y) ? new PhantomNavigationPoint(x, y, geo.getHeight(x, y, hint.z()), hint.instanceId()) : null;
			}, leg ->
			{
				if (!PhantomNativeTravelActions.available(leg)) { return null; }
				final var endpoints = PhantomNativeTravelActions.gatekeeperEndpoints(leg);
				return new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, endpoints.source(), endpoints.landing(), List.of(), leg, 0, org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.phantomFee(leg, options.classIndex(), options.level(), options.epochMinute()), 0);
			}, () -> cancellation.changedSince(generation) || clock.getAsLong() >= request.deadlineLogicalNanos());
			if (cancellation.changedSince(generation)) { return new TravelPlan(PhantomNavigationResult.Status.CANCELLED, null); }
			if (clock.getAsLong() >= request.deadlineLogicalNanos()) { return new TravelPlan(PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
			return new TravelPlan(composite.status() == PhantomNavigationResult.Status.NO_PATH ? absent : composite.status(), composite.candidate());
		}
		return new TravelPlan(selected.status() == PhantomTravelStrategy.Status.FOUND ? PhantomNavigationResult.Status.PATH_FOUND : absent, selected.candidate());
	}
	static PhantomTravelStrategy.Selection selectTravel(List<PhantomTravelStrategy.Candidate> candidates, PhantomTravelStrategy.Options options)
	{
		// Fast-path candidates must reserve the same full tariff as the composite fallback.
		// A current discount is a debit quote, not funding for a later mandatory transfer.
		final var reserved = candidates.stream().map(candidate -> new PhantomTravelStrategy.Candidate(candidate.id(), candidate.stages().stream().map(stage ->
			stage.mode() != PhantomTravelStrategy.Mode.GK ? stage : new PhantomTravelStrategy.Stage(stage.mode(), stage.from(), stage.to(), stage.points(), stage.gatekeeper(), stage.millis(),
				org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.phantomMaximumFee(stage.gatekeeper(), options.classIndex(), options.level()), stage.scrolls())).toList())).toList();
		return PhantomTravelStrategy.select(reserved, options.adena(), options.scrolls(), options.remainingMillis());
	}
	private static PhantomTravelStrategy.Stage walkStage(PhantomNavigationPoint from, PhantomNavigationPoint to, List<PhantomNavigationPoint> points, double speed)
	{
		return new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, from, to, points, null, (long)Math.ceil(1000 * PhantomTravelStrategy.distance(from, points) / speed), 0, 0);
	}
	private static void addCandidate(List<PhantomTravelStrategy.Candidate> candidates, String id, List<PhantomTravelStrategy.Stage> stages, PhantomNavigationRequest request, PhantomNavigationPolicy policy)
	{
		if (stages.stream().mapToInt(s -> s.points().size()).sum() <= policy.maximumWaypoints() && stages.stream().filter(s -> s.mode() == PhantomTravelStrategy.Mode.WALK).mapToDouble(s -> PhantomTravelStrategy.distance(s.from(), s.points())).sum() <= Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance())) { candidates.add(new PhantomTravelStrategy.Candidate(id, stages)); }
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
