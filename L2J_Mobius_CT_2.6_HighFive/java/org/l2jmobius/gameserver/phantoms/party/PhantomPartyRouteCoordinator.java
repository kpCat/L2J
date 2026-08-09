/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.Submission;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/**
 * Owns exactly one navigation request and route manifest per group.
 */
public final class PhantomPartyRouteCoordinator
{
	private final PhantomNavigationService _navigation;
	private final PhantomCombatService _combat;
	private final Object _stateLock = new Object();
	private final Map<String, PendingRoute> _pending = new HashMap<>();
	private final Map<String, String> _routeByGroup = new HashMap<>();
	private final Map<String, Long> _routeDeadlines = new HashMap<>();
	private final Map<Long, MovementLease> _movement = new HashMap<>();
	private final Set<Long> _movementReservations = new HashSet<>();

	public PhantomPartyRouteCoordinator(PhantomNavigationService navigation, PhantomCombatService combat)
	{
		_navigation = navigation;
		_combat = combat;
	}

	public RouteActivity observe(String groupId, RouteManifest persisted, List<MemberRef> roster)
	{
		synchronized (_stateLock)
		{
			final PendingRoute pending = _pending.get(groupId);
			if (pending != null)
			{
				return new RouteActivity(ActivityStatus.PLANNING, pending._routeId, pending._generation, pending._destination, true, false, false);
			}
			if (persisted == null)
			{
				return RouteActivity.none();
			}
			final boolean routeOwned = persisted.routeId().equals(_routeByGroup.get(groupId));
			final boolean movementOwned = roster.stream().filter(member -> member.kind() == MemberKind.PHANTOM).map(member -> _movement.get(member.profileId())).anyMatch(movement -> (movement != null) && persisted.routeId().equals(movement._routeId));
			return new RouteActivity(ActivityStatus.valueOf(persisted.status().name()), persisted.routeId(), persisted.generation(), persisted.destination(), false, routeOwned, movementOwned);
		}
	}

	public Optional<RouteManifest> request(String groupId, long generation, MemberSnapshot leader, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destinationRef, PhantomNavigationPoint destination, String topologyHash, long now, long deadline)
	{
		if ((leader.ref().kind() != MemberKind.PHANTOM) || (destination.instanceId() != leader.instanceId()))
		{
			return Optional.empty();
		}
		final String routeId = PhantomPartyModel.sha256(groupId + '|' + generation + '|' + destinationRef.namespace() + ':' + destinationRef.key() + '|' + topologyHash);
		final PendingRoute pending = new PendingRoute(leader.ref().profileId(), routeId, generation, destinationRef, topologyHash, deadline);
		synchronized (_stateLock)
		{
			if (_pending.containsKey(groupId) || _routeByGroup.containsKey(groupId))
			{
				return Optional.empty();
			}
			_pending.put(groupId, pending);
		}
		final PhantomNavigationPoint origin = new PhantomNavigationPoint(leader.x(), leader.y(), leader.z(), leader.instanceId());
		final Submission submission = _navigation.submit(new PhantomNavigationRequest(leader.ref().profileId(), origin, destination, now, deadline, 100000));
		final boolean stillOwned;
		synchronized (_stateLock)
		{
			stillOwned = _pending.get(groupId) == pending;
			if (stillOwned && (submission.status() == SubmissionStatus.ACCEPTED))
			{
				pending._requestId = submission.requestId();
			}
			else if (stillOwned)
			{
				_pending.remove(groupId);
			}
		}
		if (!stillOwned)
		{
			if (submission.status() == SubmissionStatus.ACCEPTED)
			{
				_navigation.cancel(leader.ref().profileId(), submission.requestId());
			}
			return Optional.empty();
		}
		if (submission.status() == SubmissionStatus.REJECTED)
		{
			return Optional.empty();
		}
		if (submission.status() == SubmissionStatus.COMPLETED)
		{
			final Optional<RouteManifest> result = manifest(routeId, generation, destinationRef, submission.immediateResult(), topologyHash);
			result.ifPresent(_ -> rememberRoute(groupId, routeId, deadline));
			return result;
		}
		return Optional.empty();
	}

	public Optional<RouteManifest> poll(String groupId)
	{
		final PendingRoute pending;
		synchronized (_stateLock)
		{
			pending = _pending.get(groupId);
		}
		if ((pending == null) || (pending._requestId == 0))
		{
			return Optional.empty();
		}
		final Optional<PhantomNavigationResult> result = _navigation.consume(pending._requestId);
		if (result.isEmpty())
		{
			return Optional.empty();
		}
		synchronized (_stateLock)
		{
			if (!_pending.remove(groupId, pending))
			{
				return Optional.empty();
			}
			_routeByGroup.put(groupId, pending._routeId);
			_routeDeadlines.put(groupId, pending._deadline);
		}
		return manifest(pending._routeId, pending._generation, pending._destination, result.get(), pending._topologyHash);
	}

	public AdvanceResult advance(String groupId, RouteManifest route, MemberRef leader, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, int operationBudget, long logicalNow, String currentTopologyHash, PhantomCancellationToken token)
	{
		final long deadline;
		synchronized (_stateLock)
		{
			if (!route.routeId().equals(_routeByGroup.get(groupId)))
			{
				return new AdvanceResult(route.withProgress(route.currentWaypoint(), RouteStatus.FAILED), 1);
			}
			deadline = _routeDeadlines.getOrDefault(groupId, 0L);
		}
		if (token.isCancelled() || (deadline <= 0) || (logicalNow >= deadline) || !route.topologyHash().equals(currentTopologyHash))
		{
			cancel(groupId);
			return new AdvanceResult(route.withProgress(route.currentWaypoint(), RouteStatus.FAILED), 1);
		}
		releaseCompletedMovement(route.routeId(), roster, snapshots, logicalNow);
		final MemberSnapshot leaderSnapshot = snapshots.get(leader);
		if ((leaderSnapshot == null) || (leaderSnapshot.instanceId() != route.waypoints().getFirst().instanceId()) || (operationBudget < 1))
		{
			return new AdvanceResult(route.withProgress(route.currentWaypoint(), RouteStatus.FAILED), 1);
		}
		boolean separated = false;
		boolean allAtWaypoint = true;
		boolean movementBlocked = false;
		int examined = 1;
		final PhantomNavigationPoint waypoint = route.waypoints().get(route.currentWaypoint());
		for (MemberRef member : roster)
		{
			if (examined >= operationBudget)
			{
				return new AdvanceResult(route, examined);
			}
			examined++;
			final MemberSnapshot snapshot = snapshots.get(member);
			if ((snapshot == null) || (snapshot.instanceId() != leaderSnapshot.instanceId()))
			{
				return new AdvanceResult(route.withProgress(route.currentWaypoint(), RouteStatus.REGROUPING), examined);
			}
			movementBlocked |= snapshot.dead() || snapshot.casting() || snapshot.attacking();
			final double leaderDistance = distance(snapshot, leaderSnapshot.x(), leaderSnapshot.y(), leaderSnapshot.z());
			separated |= leaderDistance > route.maximumSeparation();
			allAtWaypoint &= distance(snapshot, waypoint.x(), waypoint.y(), waypoint.z()) <= route.regroupRadius();
		}
		if (movementBlocked)
		{
			return new AdvanceResult(route.withProgress(route.currentWaypoint(), RouteStatus.REGROUPING), examined);
		}
		int waypointIndex = route.currentWaypoint();
		if (allAtWaypoint && (waypointIndex + 1 < route.waypoints().size()))
		{
			waypointIndex++;
		}
		else if (allAtWaypoint)
		{
			return new AdvanceResult(route.withProgress(waypointIndex, RouteStatus.ARRIVED), examined);
		}
		final PhantomNavigationPoint sharedWaypoint = route.waypoints().get(waypointIndex);
		int issued = 0;
		for (MemberRef member : roster)
		{
			if ((examined >= operationBudget) || (member.kind() != MemberKind.PHANTOM))
			{
				continue;
			}
			examined++;
			if (!reserveMovement(member.profileId()))
			{
				continue;
			}
			final MemberSnapshot snapshot = snapshots.get(member);
			if ((snapshot == null) || snapshot.dead() || snapshot.casting() || snapshot.attacking() || (snapshot.instanceId() != sharedWaypoint.instanceId()))
			{
				releaseMovementReservation(member.profileId());
				continue;
			}
			final PhantomNavigationPoint target = separated && !member.equals(leader) ? new PhantomNavigationPoint(leaderSnapshot.x(), leaderSnapshot.y(), leaderSnapshot.z(), leaderSnapshot.instanceId()) : sharedWaypoint;
			final String operationKey = "party.route." + route.routeId().substring(0, 20) + '.' + route.generation() + '.' + waypointIndex;
			final PhantomCombatService.ExternalActionResult acquisition = _combat.acquireExternalAction(new ExternalActionRequest(member.profileId(), ExternalActionKind.PARTY_ROUTE, operationKey, deadline, token));
			final ExternalActionLease lease = acquisition.lease();
			if (lease == null)
			{
				releaseMovementReservation(member.profileId());
				continue;
			}
			final ActionOutcome outcome = lease.moveTo(target.x(), target.y(), target.z(), target.instanceId());
			if ((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED))
			{
				if (rememberMovement(member.profileId(), route.routeId(), lease))
				{
					issued++;
				}
				else
				{
					lease.close();
				}
			}
			else
			{
				releaseMovementReservation(member.profileId());
				lease.close();
			}
		}
		return new AdvanceResult(route.withProgress(waypointIndex, separated ? RouteStatus.REGROUPING : RouteStatus.MOVING), examined);
	}

	public void cancel(String groupId)
	{
		final PendingRoute pending;
		final List<ExternalActionLease> movement;
		synchronized (_stateLock)
		{
			pending = _pending.remove(groupId);
			final String routeId = _routeByGroup.remove(groupId);
			_routeDeadlines.remove(groupId);
			movement = _movement.entrySet().stream().filter(entry -> (routeId != null) && routeId.equals(entry.getValue()._routeId)).map(Map.Entry::getValue).map(value -> value._lease).toList();
			_movement.entrySet().removeIf(entry -> (routeId != null) && routeId.equals(entry.getValue()._routeId));
		}
		if (pending != null)
		{
			final long requestId = pending._requestId;
			if (requestId > 0)
			{
				_navigation.cancel(pending._leaderProfileId, requestId);
			}
		}
		for (ExternalActionLease lease : movement)
		{
			lease.close();
		}
	}

	public void beginStop()
	{
		final List<String> groups;
		synchronized (_stateLock)
		{
			groups = java.util.stream.Stream.concat(_pending.keySet().stream(), _routeByGroup.keySet().stream()).distinct().toList();
		}
		for (String groupId : groups)
		{
			cancel(groupId);
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_stateLock)
		{
			return new Snapshot(_pending.size(), _movement.size());
		}
	}

	private void releaseCompletedMovement(String routeId, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, long logicalNow)
	{
		final List<ExternalActionLease> completed = new ArrayList<>();
		final List<ExternalActionLease> expired = new ArrayList<>();
		synchronized (_stateLock)
		{
			for (MemberRef member : roster)
			{
				if (member.kind() != MemberKind.PHANTOM)
				{
					continue;
				}
				final MovementLease movement = _movement.get(member.profileId());
				if ((movement == null) || !routeId.equals(movement._routeId))
				{
					continue;
				}
				final MemberSnapshot snapshot = snapshots.get(member);
				if (movement._lease.expired(logicalNow))
				{
					expired.add(movement._lease);
					_movement.remove(member.profileId());
				}
				else if ((snapshot == null) || !snapshot.moving())
				{
					completed.add(movement._lease);
					_movement.remove(member.profileId());
				}
			}
		}
		completed.forEach(ExternalActionLease::complete);
		expired.forEach(ExternalActionLease::close);
	}

	private boolean reserveMovement(long profileId)
	{
		synchronized (_stateLock)
		{
			if (_movement.containsKey(profileId) || !_movementReservations.add(profileId))
			{
				return false;
			}
			return true;
		}
	}

	private void releaseMovementReservation(long profileId)
	{
		synchronized (_stateLock)
		{
			_movementReservations.remove(profileId);
		}
	}

	private boolean rememberMovement(long profileId, String routeId, ExternalActionLease lease)
	{
		synchronized (_stateLock)
		{
			_movementReservations.remove(profileId);
			if (!_routeByGroup.containsValue(routeId))
			{
				return false;
			}
			_movement.put(profileId, new MovementLease(routeId, lease));
			return true;
		}
	}

	private void rememberRoute(String groupId, String routeId, long deadline)
	{
		synchronized (_stateLock)
		{
			_routeByGroup.put(groupId, routeId);
			_routeDeadlines.put(groupId, deadline);
		}
	}

	private static Optional<RouteManifest> manifest(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, PhantomNavigationResult result, String topologyHash)
	{
		if (result.route() == null)
		{
			return Optional.empty();
		}
		final String navigationHash = PhantomPartyModel.sha256(result.route().mode() + "|" + result.route().waypoints());
		return Optional.of(new RouteManifest(routeId, generation, destination, result.route().waypoints(), 0, 250, 1500, RouteStatus.MOVING, topologyHash, navigationHash));
	}

	private static double distance(MemberSnapshot snapshot, int x, int y, int z)
	{
		final long dx = (long) snapshot.x() - x;
		final long dy = (long) snapshot.y() - y;
		final long dz = (long) snapshot.z() - z;
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	public enum ActivityStatus
	{
		NONE,
		PLANNING,
		MOVING,
		REGROUPING,
		ARRIVED,
		FAILED
	}

	public record RouteActivity(ActivityStatus status, String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, boolean plannerOwned, boolean routeOwned, boolean movementOwned)
	{
		public static RouteActivity none()
		{
			return new RouteActivity(ActivityStatus.NONE, "0".repeat(64), 0, null, false, false, false);
		}

		public boolean nonTerminal()
		{
			return Set.of(ActivityStatus.PLANNING, ActivityStatus.MOVING, ActivityStatus.REGROUPING).contains(status);
		}

		public boolean terminal()
		{
			return (status == ActivityStatus.ARRIVED) || (status == ActivityStatus.FAILED);
		}
	}

	public record Snapshot(int navigationClaims, int movementClaims)
	{
	}

	public record AdvanceResult(RouteManifest route, int examinedOperations)
	{
		public AdvanceResult
		{
			if ((route == null) || (examinedOperations < 1))
			{
				throw new IllegalArgumentException("Invalid bounded route advance result.");
			}
		}
	}

	private static final class PendingRoute
	{
		private final long _leaderProfileId;
		private final String _routeId;
		private final long _generation;
		private final org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef _destination;
		private final String _topologyHash;
		private final long _deadline;
		private volatile long _requestId;

		private PendingRoute(long leaderProfileId, String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, String topologyHash, long deadline)
		{
			_leaderProfileId = leaderProfileId;
			_routeId = routeId;
			_generation = generation;
			_destination = destination;
			_topologyHash = topologyHash;
			_deadline = deadline;
		}
	}

	private static final class MovementLease
	{
		private final String _routeId;
		private final ExternalActionLease _lease;

		private MovementLease(String routeId, ExternalActionLease lease)
		{
			_routeId = routeId;
			_lease = lease;
		}
	}
}
