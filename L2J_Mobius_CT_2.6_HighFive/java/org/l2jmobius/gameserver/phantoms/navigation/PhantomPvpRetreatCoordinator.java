/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/**
 * Navigation-owned Goal025 retreat adapter. It plans through
 * {@link PhantomNavigationService} and moves through a Goal012 external lease;
 * it never mutates coordinates or teleports.
 */
public final class PhantomPvpRetreatCoordinator
{
	public enum RetreatStatus
	{
		PLANNING,
		MOVING,
		ARRIVED,
		FAILED,
		STALE,
		BUSY
	}

	public record RetreatResult(RetreatStatus status, String reasonKey)
	{
		public RetreatResult
		{
			Objects.requireNonNull(status);
			reasonKey = Objects.requireNonNull(reasonKey);
		}

		public boolean terminal()
		{
			return (status == RetreatStatus.ARRIVED) || (status == RetreatStatus.FAILED) || (status == RetreatStatus.STALE);
		}
	}

	private static final int MAXIMUM_DISTANCE = 100_000;
	public static final int MINIMUM_TACTICAL_SEPARATION_GAIN = 200;
	private final Object _monitor = new Object();
	private final PhantomNavigationService _navigation;
	private final PhantomTopologyService _topology;
	private final PhantomCombatService _combat;
	private final LongSupplier _clock;
	private final java.util.function.BiFunction<PhantomNavigationPoint, Integer, java.util.Optional<PhantomNavigationPoint>> _walkSurface;
	private final Map<Long, Pending> _pending = new HashMap<>();

	public PhantomPvpRetreatCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat)
	{
		this(navigation, topology, combat, System::nanoTime);
	}

	public PhantomPvpRetreatCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat, LongSupplier clock)
	{
		this(navigation, topology, combat, clock, new L2jNavigationBackend()::walkSurface);
	}

	public PhantomPvpRetreatCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat, LongSupplier clock, java.util.function.BiFunction<PhantomNavigationPoint, Integer, java.util.Optional<PhantomNavigationPoint>> walkSurface)
	{
		_navigation = Objects.requireNonNull(navigation);
		_topology = Objects.requireNonNull(topology);
		_combat = Objects.requireNonNull(combat);
		_clock = Objects.requireNonNull(clock);
		_walkSurface = Objects.requireNonNull(walkSurface);
	}

	public RetreatResult start(long profileId, String authorityHash, long maximumDurationNanos)
	{
		return start(profileId, authorityHash, maximumDurationNanos, null, _ -> true);
	}

	/** An exact encounter may first break distance locally; this does not confer peace or immunity. */
	public RetreatResult start(long profileId, String authorityHash, long maximumDurationNanos, Supplier<Optional<PhantomNavigationPoint>> threat, Predicate<PhantomNavigationPoint> safeWaypoint)
	{
		if ((profileId <= 0) || (authorityHash == null) || !authorityHash.matches("[A-F0-9]{64}") || (maximumDurationNanos < 1))
		{
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.invalid");
		}
		synchronized (_monitor)
		{
			final Pending current = _pending.get(profileId);
			if (current != null)
			{
				return current._authorityHash.equals(authorityHash) ? state(current) : new RetreatResult(RetreatStatus.BUSY, "pvp.retreat.busy");
			}
		}
		final ProfileTopologySnapshot profile = _topology.findProfile(profileId).orElse(null);
		final var query = _topology.query();
		// Wilderness coordinates need no authored node; native navigation still proves every leg.
		if ((profile == null) || (profile.point() == null) || (profile.topologyGeneration() != query.snapshot().generation()))
		{
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.position_stale");
		}
		final PhantomTopologyAnchor destination = safeAnchor(profile.point(), query.nearestAnchors(profile.point(), PhantomTopologyAnchorRole.RESPAWN, 1, MAXIMUM_DISTANCE), query.nearestAnchors(profile.point(), PhantomTopologyAnchorRole.CITY_CENTER, 1, MAXIMUM_DISTANCE));
		final long now = _clock.getAsLong();
		final long deadline;
		try
		{
			deadline = Math.addExact(now, maximumDurationNanos);
		}
		catch (ArithmeticException exception)
		{
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.deadline_invalid");
		}
		final var origin = point(profile.point());
		final var initialThreat = threat == null ? null : threat.get().orElse(null);
		final List<Destination> destinations = new ArrayList<>();
		if (initialThreat != null && initialThreat.instanceId() == origin.instanceId())
		{
			for (var candidate : tacticalDestinations(origin, initialThreat))
			{
				if (safeWaypoint.test(candidate)) { destinations.add(new Destination(candidate, true)); }
			}
		}
		if (destination != null && destination.point().instanceId() == origin.instanceId())
		{
			final var surface = _walkSurface.apply(point(destination.point()), destination.validationTolerance());
			final var target = surface == null ? null : surface.orElse(null);
			if (target != null && target.x() == destination.point().x() && target.y() == destination.point().y() && target.instanceId() == destination.point().instanceId() && Math.abs((long) target.z() - destination.point().z()) <= destination.validationTolerance()) { destinations.add(new Destination(target, false)); }
		}
		if (destinations.isEmpty()) { return new RetreatResult(RetreatStatus.FAILED, destination == null ? "pvp.retreat.safe_anchor_missing" : "pvp.retreat.safe_surface_unavailable"); }
		final Pending pending = new Pending(profileId, authorityHash, destinations, query.snapshot().generation(), deadline, threat, safeWaypoint, initialThreat == null ? 0 : origin.distanceTo(initialThreat));
		synchronized (_monitor)
		{
			if (_pending.putIfAbsent(profileId, pending) != null)
			{
				return new RetreatResult(RetreatStatus.BUSY, "pvp.retreat.busy");
			}
		}
		return requestRoute(profileId, pending, origin, now);
	}

	private RetreatResult requestRoute(long profileId, Pending pending, PhantomNavigationPoint origin, long now)
	{
		pending._origin = origin;
		final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, origin, pending.destination().point(), now, Math.min(pending._deadline, now + _navigation.policy().defaultRequestDeadlineNanos()), MAXIMUM_DISTANCE));
		final boolean owned;
		synchronized (_monitor)
		{
			owned = _pending.get(profileId) == pending;
			if (owned)
			{
				pending._requestId = submission.requestId();
			}
		}
		if (!owned)
		{
			if (submission.status() == SubmissionStatus.ACCEPTED)
			{
				_navigation.cancel(profileId, submission.requestId());
			}
			return new RetreatResult(RetreatStatus.STALE, "pvp.retreat.ownership_changed");
		}
		if (submission.status() == SubmissionStatus.REJECTED)
		{
			return routeFailure(profileId, pending, submission.immediateResult(), now);
		}
		if (submission.status() == SubmissionStatus.COMPLETED)
		{
			_navigation.consume(submission.requestId());
			if (!installRoute(pending, submission.immediateResult()))
			{
				return routeFailure(profileId, pending, submission.immediateResult(), now);
			}
		}
		return state(pending);
	}

	public RetreatResult advance(long profileId, String authorityHash)
	{
		final Pending pending;
		synchronized (_monitor)
		{
			pending = _pending.get(profileId);
		}
		if (pending == null)
		{
			return new RetreatResult(RetreatStatus.STALE, "pvp.retreat.absent");
		}
		if (!pending._authorityHash.equals(authorityHash))
		{
			return new RetreatResult(RetreatStatus.STALE, "pvp.retreat.authority_stale");
		}
		final long now = _clock.getAsLong();
		if (pending._cancelled.get() || (now >= pending._deadline))
		{
			remove(profileId, pending, true);
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.expired");
		}
		if (pending._route == null)
		{
			if (pending._requestId == 0)
			{
				if (now < pending._retryAt) { return new RetreatResult(RetreatStatus.PLANNING, "pvp.retreat.route_retry." + pending._lastRefusal); }
				final var current = _topology.findProfile(profileId).orElse(null);
				if (current == null || current.point() == null || current.topologyGeneration() != pending._topologyGeneration || current.point().instanceId() != pending.destination().point().instanceId()) { remove(profileId, pending, true); return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.position_stale"); }
				return requestRoute(profileId, pending, point(current.point()), now);
			}
			final PhantomNavigationResult result = _navigation.consume(pending._requestId).orElse(null);
			if (result == null)
			{
				return new RetreatResult(RetreatStatus.PLANNING, "pvp.retreat.planning");
			}
			if (!installRoute(pending, result))
			{
				return routeFailure(profileId, pending, result, now);
			}
		}
		final ProfileTopologySnapshot profile = _topology.findProfile(profileId).orElse(null);
		if ((profile == null) || (profile.point() == null) || (profile.topologyGeneration() != pending._topologyGeneration) || (profile.point().instanceId() != pending.destination().point().instanceId()))
		{
			remove(profileId, pending, true);
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.position_stale");
		}
		final PhantomNavigationPoint current = point(profile.point());
		final PhantomNavigationPoint waypoint = pending._route.waypoints().get(pending._waypointIndex);
		if (current.distanceTo(waypoint) <= _navigation.arrivalRadius())
		{
			releaseMovement(pending, true);
			if (++pending._waypointIndex >= pending._route.waypoints().size())
			{
				final var threat = pending.destination().tactical() ? pending._threat.get().orElse(null) : null;
				if (pending.destination().tactical() && (threat == null || threat.instanceId() != current.instanceId() || current.distanceTo(threat) < pending._initialThreatDistance + MINIMUM_TACTICAL_SEPARATION_GAIN))
				{
					remove(profileId, pending, false);
					return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.tactical_pressure_persists");
				}
				remove(profileId, pending, false);
				return new RetreatResult(RetreatStatus.ARRIVED, pending.destination().tactical() ? "pvp.retreat.tactical_arrived" : "pvp.retreat.arrived");
			}
		}
		if (pending._movement != null)
		{
			return new RetreatResult(RetreatStatus.MOVING, "pvp.retreat.moving");
		}
		if (pending.destination().tactical())
		{
			final var threat = pending._threat.get().orElse(null);
			if (threat == null || !awaySegment(current, pending._route.waypoints().get(pending._waypointIndex), threat) || !pending._safeWaypoint.test(pending._route.waypoints().get(pending._waypointIndex)))
			{
				remove(profileId, pending, true);
				return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.tactical_segment_changed");
			}
		}
		final String operationKey = "pvp.retreat." + authorityHash.substring(0, 20) + '.' + pending._waypointIndex;
		final var acquisition = _combat.acquireExternalAction(new ExternalActionRequest(profileId, ExternalActionKind.PVP_RETREAT, operationKey, pending._deadline, pending._cancelled::get));
		if (acquisition.lease() == null)
		{
			return new RetreatResult(RetreatStatus.BUSY, "pvp.retreat.action_busy");
		}
		final PhantomNavigationPoint next = pending._route.waypoints().get(pending._waypointIndex);
		final ActionOutcome outcome = acquisition.lease().moveTo(next.x(), next.y(), next.z(), next.instanceId());
		if ((outcome != ActionOutcome.ISSUED) && (outcome != ActionOutcome.ALREADY_OWNED))
		{
			acquisition.lease().close();
			remove(profileId, pending, true);
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.movement_rejected");
		}
		synchronized (_monitor)
		{
			if (_pending.get(profileId) == pending)
			{
				pending._movement = acquisition.lease();
				return new RetreatResult(RetreatStatus.MOVING, "pvp.retreat.moving");
			}
		}
		acquisition.lease().close();
		return new RetreatResult(RetreatStatus.STALE, "pvp.retreat.ownership_changed");
	}

	public boolean cancel(long profileId, String authorityHash)
	{
		final Pending pending;
		synchronized (_monitor)
		{
			pending = _pending.get(profileId);
			if ((pending == null) || !pending._authorityHash.equals(authorityHash))
			{
				return false;
			}
		}
		remove(profileId, pending, true);
		return true;
	}

	public void finishStop()
	{
		final List<Pending> copy;
		synchronized (_monitor)
		{
			copy = new ArrayList<>(_pending.values());
		}
		copy.forEach(pending -> remove(pending._profileId, pending, true));
	}

	public int activeCount()
	{
		synchronized (_monitor)
		{
			return _pending.size();
		}
	}

	private boolean installRoute(Pending pending, PhantomNavigationResult result)
	{
		if ((result == null) || (result.route() == null) || !List.of(Status.DIRECT_VALIDATED, Status.PATH_FOUND).contains(result.status()) || result.profileId() != pending._profileId || result.requestId() != pending._requestId || !result.route().origin().equals(pending._origin) || !result.route().destination().equals(pending.destination().point()))
		{
			return false;
		}
		if (pending.destination().tactical())
		{
			final var threat = pending._threat.get().orElse(null);
			var previous = result.route().origin();
			for (var waypoint : result.route().waypoints())
			{
				if (threat == null || !awaySegment(previous, waypoint, threat)) { return false; }
				previous = waypoint;
			}
		}
		pending._route = result.route();
		return true;
	}

	private RetreatResult routeFailure(long profileId, Pending pending, PhantomNavigationResult result, long now)
	{
		final var status = result == null ? Status.BACKEND_FAILURE : result.status();
		pending._requestId = 0; pending._lastRefusal = status.name().toLowerCase(java.util.Locale.ROOT);
		if (List.of(Status.COOLDOWN, Status.PROFILE_BUSY, Status.QUEUE_BACKPRESSURE).contains(status))
		{
			pending._retryAt = now + _navigation.policy().pathfindingCooldownNanos(); return state(pending);
		}
		if (++pending._destinationIndex < pending._destinations.size())
		{
			pending._retryAt = now + _navigation.policy().pathfindingCooldownNanos(); return new RetreatResult(RetreatStatus.PLANNING, "pvp.retreat.route_retry." + pending._lastRefusal);
		}
		remove(profileId, pending, true);
		return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.route_failed." + pending._lastRefusal);
	}

	private List<PhantomNavigationPoint> tacticalDestinations(PhantomNavigationPoint origin, PhantomNavigationPoint threat)
	{
		if (origin.instanceId() != threat.instanceId() || origin.distanceTo(threat) < 1) { return List.of(); }
		final var result = new ArrayList<PhantomNavigationPoint>(); final double bearing = Math.atan2((double) origin.y() - threat.y(), (double) origin.x() - threat.x());
		for (int distance : new int[] {600, 375}) { for (double turn : new double[] {0, -Math.PI / 6, Math.PI / 6})
		{
			final int x = origin.x() + (int) Math.round(distance * Math.cos(bearing + turn)), y = origin.y() + (int) Math.round(distance * Math.sin(bearing + turn));
			try
			{
				final var hint = new PhantomNavigationPoint(x, y, origin.z(), origin.instanceId()); final var surface = _walkSurface.apply(hint, 256); final var point = surface == null ? null : surface.orElse(null);
				if (point != null && point.x() == x && point.y() == y && point.instanceId() == origin.instanceId() && Math.abs((long) point.z() - origin.z()) <= 256 && point.distanceTo(threat) >= origin.distanceTo(threat) + MINIMUM_TACTICAL_SEPARATION_GAIN && awaySegment(origin, point, threat)) { result.add(point); }
			}
			catch (IllegalArgumentException ignored) { /* A world boundary is not an escape corridor. */ }
		} }
		return List.copyOf(result);
	}

	/** A detour may not cut toward the exact threat even if its endpoint is farther away. */
	private static boolean awaySegment(PhantomNavigationPoint from, PhantomNavigationPoint to, PhantomNavigationPoint threat)
	{
		if (from.instanceId() != threat.instanceId() || to.instanceId() != threat.instanceId()) { return false; }
		final double dx = (double) to.x() - from.x(), dy = (double) to.y() - from.y(), length = dx * dx + dy * dy;
		if (length == 0) { return false; }
		final double projection = Math.clamp((((double) threat.x() - from.x()) * dx + ((double) threat.y() - from.y()) * dy) / length, 0, 1);
		final double nearest = Math.hypot(from.x() + projection * dx - threat.x(), from.y() + projection * dy - threat.y());
		return nearest + 16 >= Math.hypot((double) from.x() - threat.x(), (double) from.y() - threat.y()) && to.distanceTo(threat) > from.distanceTo(threat);
	}

	private void remove(long profileId, Pending pending, boolean cancel)
	{
		synchronized (_monitor)
		{
			if (!_pending.remove(profileId, pending))
			{
				return;
			}
			pending._cancelled.set(cancel);
		}
		if (cancel && (pending._requestId > 0) && (pending._route == null))
		{
			_navigation.cancel(profileId, pending._requestId);
			_navigation.consume(pending._requestId);
		}
		releaseMovement(pending, !cancel);
	}

	private static void releaseMovement(Pending pending, boolean complete)
	{
		final ExternalActionLease lease = pending._movement;
		pending._movement = null;
		if (lease != null)
		{
			if (complete)
			{
				lease.complete();
			}
			else
			{
				lease.close();
			}
		}
	}

	private static PhantomTopologyAnchor safeAnchor(PhantomTopologyPoint origin, List<PhantomTopologyAnchor> respawns, List<PhantomTopologyAnchor> cities)
	{
		if (respawns.isEmpty()) { return cities.isEmpty() ? null : cities.getFirst(); }
		if (cities.isEmpty()) { return respawns.getFirst(); }
		return origin.distanceSquared2D(cities.getFirst().point()) < origin.distanceSquared2D(respawns.getFirst().point()) ? cities.getFirst() : respawns.getFirst();
	}

	private static PhantomNavigationPoint point(PhantomTopologyPoint point)
	{
		return new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId());
	}

	private static RetreatResult state(Pending pending)
	{
		return new RetreatResult(pending._route == null ? RetreatStatus.PLANNING : RetreatStatus.MOVING, pending._route == null ? "pvp.retreat.planning" : "pvp.retreat.moving");
	}
	private record Destination(PhantomNavigationPoint point, boolean tactical) { }

	private static final class Pending
	{
		private final long _profileId;
		private final String _authorityHash;
		private final List<Destination> _destinations;
		private final Supplier<Optional<PhantomNavigationPoint>> _threat;
		private final Predicate<PhantomNavigationPoint> _safeWaypoint;
		private final double _initialThreatDistance;
		private final long _topologyGeneration;
		private final long _deadline;
		private final AtomicBoolean _cancelled = new AtomicBoolean();
		private volatile long _requestId;
		private long _retryAt;
		private String _lastRefusal = "";
		private PhantomNavigationPoint _origin;
		private volatile PhantomNavigationRoute _route;
		private volatile int _waypointIndex;
		private int _destinationIndex;
		private volatile ExternalActionLease _movement;

		private Pending(long profileId, String authorityHash, List<Destination> destinations, long topologyGeneration, long deadline, Supplier<Optional<PhantomNavigationPoint>> threat, Predicate<PhantomNavigationPoint> safeWaypoint, double initialThreatDistance)
		{
			_profileId = profileId;
			_authorityHash = authorityHash;
			_destinations = List.copyOf(destinations);
			_threat = threat;
			_safeWaypoint = Objects.requireNonNull(safeWaypoint);
			_initialThreatDistance = initialThreatDistance;
			_topologyGeneration = topologyGeneration;
			_deadline = deadline;
		}
		private Destination destination() { return _destinations.get(_destinationIndex); }
	}
}
