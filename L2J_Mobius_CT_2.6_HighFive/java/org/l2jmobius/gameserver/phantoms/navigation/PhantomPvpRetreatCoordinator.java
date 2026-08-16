/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

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
	private final Object _monitor = new Object();
	private final PhantomNavigationService _navigation;
	private final PhantomTopologyService _topology;
	private final PhantomCombatService _combat;
	private final LongSupplier _clock;
	private final Map<Long, Pending> _pending = new HashMap<>();

	public PhantomPvpRetreatCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat)
	{
		this(navigation, topology, combat, System::nanoTime);
	}

	public PhantomPvpRetreatCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat, LongSupplier clock)
	{
		_navigation = Objects.requireNonNull(navigation);
		_topology = Objects.requireNonNull(topology);
		_combat = Objects.requireNonNull(combat);
		_clock = Objects.requireNonNull(clock);
	}

	public RetreatResult start(long profileId, String authorityHash, long maximumDurationNanos)
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
		if ((profile == null) || !profile.resolved() || (profile.topologyGeneration() != query.snapshot().generation()))
		{
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.position_stale");
		}
		final PhantomTopologyAnchor destination = safeAnchor(query.nearestAnchors(profile.point(), PhantomTopologyAnchorRole.RESPAWN, 1, MAXIMUM_DISTANCE), query.nearestAnchors(profile.point(), PhantomTopologyAnchorRole.CITY_CENTER, 1, MAXIMUM_DISTANCE));
		if ((destination == null) || (destination.point().instanceId() != profile.point().instanceId()))
		{
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.safe_anchor_missing");
		}
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
		final Pending pending = new Pending(profileId, authorityHash, destination.point(), query.snapshot().generation(), deadline);
		synchronized (_monitor)
		{
			if (_pending.putIfAbsent(profileId, pending) != null)
			{
				return new RetreatResult(RetreatStatus.BUSY, "pvp.retreat.busy");
			}
		}
		final PhantomNavigationPoint origin = point(profile.point());
		final PhantomNavigationPoint target = point(destination.point());
		final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, origin, target, now, deadline, MAXIMUM_DISTANCE));
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
			remove(profileId, pending, true);
			return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.route_rejected");
		}
		if (submission.status() == SubmissionStatus.COMPLETED)
		{
			_navigation.consume(submission.requestId());
			if (!installRoute(pending, submission.immediateResult()))
			{
				remove(profileId, pending, true);
				return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.route_failed");
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
				return new RetreatResult(RetreatStatus.PLANNING, "pvp.retreat.planning");
			}
			final PhantomNavigationResult result = _navigation.consume(pending._requestId).orElse(null);
			if (result == null)
			{
				return new RetreatResult(RetreatStatus.PLANNING, "pvp.retreat.planning");
			}
			if (!installRoute(pending, result))
			{
				remove(profileId, pending, true);
				return new RetreatResult(RetreatStatus.FAILED, "pvp.retreat.route_failed");
			}
		}
		final ProfileTopologySnapshot profile = _topology.findProfile(profileId).orElse(null);
		if ((profile == null) || !profile.resolved() || (profile.topologyGeneration() != pending._topologyGeneration) || (profile.point().instanceId() != pending._destination.instanceId()))
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
				remove(profileId, pending, false);
				return new RetreatResult(RetreatStatus.ARRIVED, "pvp.retreat.arrived");
			}
		}
		if (pending._movement != null)
		{
			return new RetreatResult(RetreatStatus.MOVING, "pvp.retreat.moving");
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
		if ((result == null) || (result.route() == null) || !List.of(Status.DIRECT_VALIDATED, Status.DIRECT_UNVERIFIED_NO_GEODATA, Status.PATH_FOUND).contains(result.status()) || !result.route().destination().equals(point(pending._destination)))
		{
			return false;
		}
		pending._route = result.route();
		return true;
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

	private static PhantomTopologyAnchor safeAnchor(List<PhantomTopologyAnchor> respawns, List<PhantomTopologyAnchor> cities)
	{
		return !respawns.isEmpty() ? respawns.getFirst() : cities.isEmpty() ? null : cities.getFirst();
	}

	private static PhantomNavigationPoint point(PhantomTopologyPoint point)
	{
		return new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId());
	}

	private static RetreatResult state(Pending pending)
	{
		return new RetreatResult(pending._route == null ? RetreatStatus.PLANNING : RetreatStatus.MOVING, pending._route == null ? "pvp.retreat.planning" : "pvp.retreat.moving");
	}

	private static final class Pending
	{
		private final long _profileId;
		private final String _authorityHash;
		private final PhantomTopologyPoint _destination;
		private final long _topologyGeneration;
		private final long _deadline;
		private final AtomicBoolean _cancelled = new AtomicBoolean();
		private volatile long _requestId;
		private volatile PhantomNavigationRoute _route;
		private volatile int _waypointIndex;
		private volatile ExternalActionLease _movement;

		private Pending(long profileId, String authorityHash, PhantomTopologyPoint destination, long topologyGeneration, long deadline)
		{
			_profileId = profileId;
			_authorityHash = authorityHash;
			_destination = destination;
			_topologyGeneration = topologyGeneration;
			_deadline = deadline;
		}
	}
}
