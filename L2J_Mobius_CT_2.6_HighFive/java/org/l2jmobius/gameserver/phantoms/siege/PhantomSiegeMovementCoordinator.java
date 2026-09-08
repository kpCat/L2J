/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/** Bounded siege movement orchestration over the existing Navigation and Combat owners. */
public final class PhantomSiegeMovementCoordinator implements PhantomSiegeMovementPort
{
	public enum Status
	{
		PLANNING,
		MOVING,
		ARRIVED,
		FAILED,
		BUSY,
		STALE
	}

	public record Result(Status status, String reasonKey)
	{
		public Result
		{
			Objects.requireNonNull(status);
			Objects.requireNonNull(reasonKey);
		}
	}

	private static final int MAXIMUM_ACTIVE = 128;
	private static final int MAXIMUM_DISTANCE = 100_000;
	private final Object _monitor = new Object();
	private final PhantomNavigationService _navigation;
	private final PhantomTopologyService _topology;
	private final PhantomCombatService _combat;
	private final LongSupplier _clock;
	private final Map<Long, Pending> _pending = new HashMap<>();

	public PhantomSiegeMovementCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat)
	{
		this(navigation, topology, combat, System::nanoTime);
	}

	public PhantomSiegeMovementCoordinator(PhantomNavigationService navigation, PhantomTopologyService topology, PhantomCombatService combat, LongSupplier clock)
	{
		_navigation = Objects.requireNonNull(navigation);
		_topology = Objects.requireNonNull(topology);
		_combat = Objects.requireNonNull(combat);
		_clock = Objects.requireNonNull(clock);
	}

	@Override
	public Result advance(long profileId, String anchorId, String authorityHash, long maximumDurationMillis)
	{
		if ((profileId <= 0) || (anchorId == null) || anchorId.isBlank() || (authorityHash == null) || !authorityHash.matches("[A-F0-9]{64}") || (maximumDurationMillis < 1000) || (maximumDurationMillis > TimeUnit.MINUTES.toMillis(10)))
		{
			return new Result(Status.FAILED, "siege.route.invalid");
		}
		final Pending current;
		synchronized (_monitor)
		{
			current = _pending.get(profileId);
		}
		if (current != null)
		{
			if (!current._anchorId.equals(anchorId) || !current._authorityHash.equals(authorityHash))
			{
				return new Result(Status.BUSY, "siege.route.busy");
			}
			return progress(current);
		}

		final ProfileTopologySnapshot profile = _topology.findProfile(profileId).orElse(null);
		final var query = _topology.query();
		final PhantomTopologyAnchor anchor = query.findAnchor(anchorId).orElse(null);
		if ((profile == null) || !profile.resolved() || (profile.topologyGeneration() != query.snapshot().generation()) || (anchor == null) || (profile.point().instanceId() != anchor.point().instanceId()))
		{
			return new Result(Status.FAILED, "siege.route.topology_stale");
		}
		if (profile.point().distanceSquared2D(anchor.point()) <= ((long) _navigation.arrivalRadius() * _navigation.arrivalRadius()))
		{
			return new Result(Status.ARRIVED, "siege.route.arrived");
		}
		final long now = _clock.getAsLong();
		final long deadline;
		try
		{
			deadline = Math.addExact(now, TimeUnit.MILLISECONDS.toNanos(maximumDurationMillis));
		}
		catch (ArithmeticException exception)
		{
			return new Result(Status.FAILED, "siege.route.deadline_invalid");
		}
		final Pending pending = new Pending(profileId, anchorId, authorityHash, anchor.point(), query.snapshot().generation(), deadline);
		synchronized (_monitor)
		{
			if ((_pending.size() >= MAXIMUM_ACTIVE) || (_pending.putIfAbsent(profileId, pending) != null))
			{
				return new Result(Status.BUSY, "siege.route.capacity");
			}
		}
		final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, point(profile.point()), point(anchor.point()), now, deadline, MAXIMUM_DISTANCE));
		if (submission.status() == SubmissionStatus.REJECTED)
		{
			remove(pending, true);
			return new Result(Status.FAILED, "siege.route.rejected");
		}
		pending._requestId = submission.requestId();
		if (submission.status() == SubmissionStatus.COMPLETED)
		{
			_navigation.consume(submission.requestId());
			if (!install(pending, submission.immediateResult()))
			{
				remove(pending, true);
				return new Result(Status.FAILED, "siege.route.no_path");
			}
		}
		return progress(pending);
	}

	@Override
	public boolean cancel(long profileId, String authorityHash)
	{
		final Pending pending;
		synchronized (_monitor)
		{
			pending = _pending.get(profileId);
		}
		if ((pending == null) || !pending._authorityHash.equals(authorityHash))
		{
			return false;
		}
		remove(pending, true);
		return true;
	}

	@Override
	public void finishStop()
	{
		final List<Pending> pending;
		synchronized (_monitor)
		{
			pending = new ArrayList<>(_pending.values());
		}
		pending.forEach(value -> remove(value, true));
	}

	@Override
	public int activeCount()
	{
		synchronized (_monitor)
		{
			return _pending.size();
		}
	}

	private Result progress(Pending pending)
	{
		if (pending._cancelled.get() || (_clock.getAsLong() >= pending._deadline))
		{
			remove(pending, true);
			return new Result(Status.FAILED, "siege.route.expired");
		}
		if (pending._route == null)
		{
			final PhantomNavigationResult result = _navigation.consume(pending._requestId).orElse(null);
			if (result == null)
			{
				return new Result(Status.PLANNING, "siege.route.planning");
			}
			if (!install(pending, result))
			{
				remove(pending, true);
				return new Result(Status.FAILED, "siege.route.no_path");
			}
		}
		final ProfileTopologySnapshot profile = _topology.findProfile(pending._profileId).orElse(null);
		if ((profile == null) || !profile.resolved() || (profile.topologyGeneration() != pending._topologyGeneration))
		{
			remove(pending, true);
			return new Result(Status.STALE, "siege.route.position_stale");
		}
		final PhantomNavigationPoint waypoint = pending._route.waypoints().get(pending._waypointIndex);
		if (point(profile.point()).distanceTo(waypoint) <= _navigation.arrivalRadius())
		{
			releaseMovement(pending, true);
			if (++pending._waypointIndex >= pending._route.waypoints().size())
			{
				remove(pending, false);
				return new Result(Status.ARRIVED, "siege.route.arrived");
			}
		}
		if (pending._movement != null)
		{
			return new Result(Status.MOVING, "siege.route.moving");
		}
		final String operationKey = "siege.route." + pending._authorityHash.substring(0, 20) + '.' + pending._waypointIndex;
		final var acquired = _combat.acquireExternalAction(new ExternalActionRequest(pending._profileId, ExternalActionKind.SIEGE_ROUTE, operationKey, pending._deadline, pending._cancelled::get));
		if (acquired.lease() == null)
		{
			return new Result(Status.BUSY, "siege.route.action_busy");
		}
		final PhantomNavigationPoint next = pending._route.waypoints().get(pending._waypointIndex);
		final ActionOutcome outcome = acquired.lease().moveTo(next.x(), next.y(), next.z(), next.instanceId());
		if ((outcome != ActionOutcome.ISSUED) && (outcome != ActionOutcome.ALREADY_OWNED))
		{
			acquired.lease().close();
			remove(pending, true);
			return new Result(Status.FAILED, "siege.route.movement_rejected");
		}
		pending._movement = acquired.lease();
		return new Result(Status.MOVING, "siege.route.moving");
	}

	private static boolean install(Pending pending, PhantomNavigationResult result)
	{
		if ((result == null) || (result.route() == null) || !List.of(PhantomNavigationResult.Status.DIRECT_VALIDATED, PhantomNavigationResult.Status.DIRECT_UNVERIFIED_NO_GEODATA, PhantomNavigationResult.Status.PATH_FOUND).contains(result.status()) || !result.route().destination().equals(point(pending._destination)))
		{
			return false;
		}
		pending._route = result.route();
		return true;
	}

	private void remove(Pending pending, boolean cancel)
	{
		synchronized (_monitor)
		{
			if (!_pending.remove(pending._profileId, pending))
			{
				return;
			}
			pending._cancelled.set(cancel);
		}
		if (cancel && (pending._route == null) && (pending._requestId > 0))
		{
			_navigation.cancel(pending._profileId, pending._requestId);
			_navigation.consume(pending._requestId);
		}
		releaseMovement(pending, !cancel);
	}

	private static void releaseMovement(Pending pending, boolean complete)
	{
		final ExternalActionLease movement = pending._movement;
		pending._movement = null;
		if (movement != null)
		{
			if (complete)
			{
				movement.complete();
			}
			else
			{
				movement.close();
			}
		}
	}

	private static PhantomNavigationPoint point(PhantomTopologyPoint point)
	{
		return new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId());
	}

	private static final class Pending
	{
		private final long _profileId;
		private final String _anchorId;
		private final String _authorityHash;
		private final PhantomTopologyPoint _destination;
		private final long _topologyGeneration;
		private final long _deadline;
		private final AtomicBoolean _cancelled = new AtomicBoolean();
		private volatile long _requestId;
		private volatile PhantomNavigationRoute _route;
		private volatile int _waypointIndex;
		private volatile ExternalActionLease _movement;

		private Pending(long profileId, String anchorId, String authorityHash, PhantomTopologyPoint destination, long topologyGeneration, long deadline)
		{
			_profileId = profileId;
			_anchorId = anchorId;
			_authorityHash = authorityHash;
			_destination = destination;
			_topologyGeneration = topologyGeneration;
			_deadline = deadline;
		}
	}
}
