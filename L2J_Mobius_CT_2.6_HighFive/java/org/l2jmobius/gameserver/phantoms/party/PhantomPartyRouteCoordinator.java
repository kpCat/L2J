/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
	private static final int MAX_TERMINAL_RECEIPTS = 4096;
	private final PhantomNavigationService _navigation;
	private final PhantomCombatService _combat;
	private final PhantomPartyFollowPolicy _followPolicy;
	private final Object _stateLock = new Object();
	private final Map<String, PendingRoute> _pending = new HashMap<>();
	private final Map<String, String> _routeByGroup = new HashMap<>();
	private final Map<String, Long> _routeDeadlines = new HashMap<>();
	private final Map<String, Long> _routeGenerations = new HashMap<>();
	private final Map<String, FollowState> _followStates = new HashMap<>();
	private final LinkedHashMap<String, RouteAttempt> _terminalByGroup = new LinkedHashMap<>();
	private final Map<Long, MovementLease> _movement = new HashMap<>();
	private final Set<Long> _movementReservations = new HashSet<>();
	private long _followSequence;

	public PhantomPartyRouteCoordinator(PhantomNavigationService navigation, PhantomCombatService combat)
	{
		this(navigation, combat, PhantomPartyFollowPolicy.defaults());
	}

	public PhantomPartyRouteCoordinator(PhantomNavigationService navigation, PhantomCombatService combat, PhantomPartyFollowPolicy followPolicy)
	{
		_navigation = navigation;
		_combat = combat;
		_followPolicy = java.util.Objects.requireNonNull(followPolicy);
	}

	public RouteActivity observe(String groupId, RouteManifest persisted, List<MemberRef> roster)
	{
		synchronized (_stateLock)
		{
			final PendingRoute pending = _pending.get(groupId);
			if (pending != null)
			{
				return new RouteActivity(ActivityStatus.PLANNING, pending._routeId, pending._generation, pending._destination, true, false, false, null, "party.route.pending");
			}
			if (persisted == null)
			{
				final RouteAttempt terminal = _terminalByGroup.get(groupId);
				return terminal == null ? RouteActivity.none() : RouteActivity.terminal(terminal);
			}
			final boolean routeOwned = persisted.routeId().equals(_routeByGroup.get(groupId));
			final boolean movementOwned = roster.stream().filter(member -> member.kind() == MemberKind.PHANTOM).map(member -> _movement.get(member.profileId())).anyMatch(movement -> (movement != null) && persisted.routeId().equals(movement._routeId));
			return new RouteActivity(ActivityStatus.valueOf(persisted.status().name()), persisted.routeId(), persisted.generation(), persisted.destination(), false, routeOwned, movementOwned, null, "party.route." + persisted.status().name().toLowerCase(Locale.ROOT));
		}
	}

	public RouteAttempt request(String groupId, long generation, MemberSnapshot leader, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destinationRef, PhantomNavigationPoint destination, String topologyHash, long now, long deadline)
	{
		final String routeId = PhantomPartyModel.sha256(groupId + '|' + generation + '|' + destinationRef.namespace() + ':' + destinationRef.key() + '|' + topologyHash);
		if ((_navigation == null) || (leader.ref().kind() != MemberKind.PHANTOM) || (destination.instanceId() != leader.instanceId()))
		{
			return RouteAttempt.unavailable(routeId, generation, destinationRef, "party.route.unavailable");
		}
		final PendingRoute pending = new PendingRoute(leader.ref().profileId(), routeId, generation, destinationRef, topologyHash, deadline);
		synchronized (_stateLock)
		{
			final PendingRoute current = _pending.get(groupId);
			if (current != null)
			{
				return RouteAttempt.pending(current._routeId, current._generation, current._destination);
			}
			if (_routeByGroup.containsKey(groupId))
			{
				return RouteAttempt.unavailable(routeId, generation, destinationRef, "party.route.already_owned");
			}
			_terminalByGroup.remove(groupId);
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
			return RouteAttempt.unavailable(routeId, generation, destinationRef, "party.route.ownership_changed");
		}
		if (submission.status() == SubmissionStatus.REJECTED)
		{
			return RouteAttempt.rejected(routeId, generation, destinationRef, submission.immediateResult());
		}
		if (submission.status() == SubmissionStatus.COMPLETED)
		{
			_navigation.consume(submission.requestId());
			final RouteAttempt result = completed(routeId, generation, destinationRef, submission.immediateResult(), topologyHash);
			if (result.status() == AttemptStatus.READY)
			{
				rememberRoute(groupId, routeId, deadline, generation);
			}
			return result;
		}
		return RouteAttempt.pending(routeId, generation, destinationRef);
	}

	public RouteAttempt poll(String groupId)
	{
		final PendingRoute pending;
		synchronized (_stateLock)
		{
			pending = _pending.get(groupId);
		}
		if (pending == null)
		{
			return RouteAttempt.none();
		}
		if (pending._requestId == 0)
		{
			return RouteAttempt.pending(pending._routeId, pending._generation, pending._destination);
		}
		final Optional<PhantomNavigationResult> result = _navigation.consume(pending._requestId);
		if (result.isEmpty())
		{
			return RouteAttempt.pending(pending._routeId, pending._generation, pending._destination);
		}
		final RouteAttempt attempt = completed(pending._routeId, pending._generation, pending._destination, result.get(), pending._topologyHash);
		synchronized (_stateLock)
		{
			if (!_pending.remove(groupId, pending))
			{
				return RouteAttempt.none();
			}
			if (attempt.status() == AttemptStatus.READY)
			{
				_routeByGroup.put(groupId, pending._routeId);
				_routeDeadlines.put(groupId, pending._deadline);
				_routeGenerations.put(groupId, pending._generation);
			}
			else
			{
				rememberTerminalLocked(groupId, attempt);
			}
		}
		return attempt;
	}

	public AdvanceResult advance(String groupId, RouteManifest route, MemberRef leader, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, int operationBudget, long logicalNow, String currentTopologyHash, PhantomCancellationToken token)
	{
		return advance(groupId, route, leader, roster, snapshots, operationBudget, logicalNow, currentTopologyHash, token, false);
	}

	public AdvanceResult advance(String groupId, RouteManifest route, MemberRef leader, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, int operationBudget, long logicalNow, String currentTopologyHash, PhantomCancellationToken token, boolean movementPaused)
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
		boolean movementBlocked = movementPaused;
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

	/** One follower owns its own path; neither the human nor another follower visits its old waypoints. */
	public int follow(String groupId, long generation, MemberSnapshot actor, MemberSnapshot leader, String topologyHash, long logicalNow, int operationBudget, PhantomCancellationToken token)
	{
		if (!_followPolicy.enabled() || (_navigation == null) || (_combat == null) || (operationBudget < 3) || (actor == null) || (leader == null) || (actor.ref().kind() != MemberKind.PHANTOM) || (leader.ref().kind() != MemberKind.REAL))
		{
			return 0;
		}
		final String memberKey = followMemberKey(groupId, generation, actor.ref().profileId());
		if (token.isCancelled() || actor.dead() || (actor.instanceId() != leader.instanceId()))
		{
			cancelFollow(groupId, generation, actor.ref().profileId());
			return 1;
		}
		final PhantomNavigationPoint origin = new PhantomNavigationPoint(actor.x(), actor.y(), actor.z(), actor.instanceId());
		final PhantomNavigationPoint destination = new PhantomNavigationPoint(leader.x(), leader.y(), leader.z(), leader.instanceId());
		final double separation = origin.distanceTo(destination);
		FollowState follow;
		synchronized (_stateLock)
		{
			follow = _followStates.get(memberKey);
		}
		if (follow != null)
		{
			final boolean changed = !follow.leader().equals(leader.ref()) || !follow.topologyHash().equals(topologyHash) || (follow.destination().instanceId() != destination.instanceId()) || (follow.destination().distanceTo(destination) >= _followPolicy.replanDistance()) || (follow.settled() && (separation > _followPolicy.startDistance())) || (follow.blocked() && (follow.origin().distanceTo(origin) >= _followPolicy.waypointRadius()));
			if (changed)
			{
				if (!replaceFollow(memberKey, follow, null))
				{
					return 1;
				}
				cancelOwnedRoute(follow.routeKey(), generation);
				follow = null;
			}
		}
		if (separation > _followPolicy.maximumDistance())
		{
			cancelFollow(groupId, generation, actor.ref().profileId());
			return 1;
		}
		if (actor.casting() || actor.attacking())
		{
			if ((follow != null) && (follow.route() != null))
			{
				releaseCompletedMovement(follow.route().routeId(), List.of(actor.ref()), Map.of(actor.ref(), actor), logicalNow);
			}
			return 1;
		}
		if (follow == null)
		{
			if (separation <= _followPolicy.startDistance())
			{
				return 1;
			}
			final long timeout = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(_followPolicy.routeTimeoutMillis());
			final long deadline = logicalNow > Long.MAX_VALUE - timeout ? Long.MAX_VALUE : logicalNow + timeout;
			synchronized (_stateLock)
			{
				if (_followStates.containsKey(memberKey) || (_followStates.size() >= MAX_TERMINAL_RECEIPTS))
				{
					return 1;
				}
				_followSequence = Math.incrementExact(_followSequence);
				final String routeKey = memberKey + '.' + PhantomPartyModel.sha256(destination + "|" + topologyHash + '|' + _followSequence);
				follow = new FollowState(groupId, generation, actor.ref().profileId(), leader.ref(), origin, destination, topologyHash, routeKey, deadline, 0, null, false, false, false);
				_followStates.put(memberKey, follow);
			}
		}
		if (follow.settled() || follow.blocked())
		{
			return 1;
		}
		final String routeKey = follow.routeKey();
		final PhantomCancellationToken owned = () -> token.isCancelled() || !ownsFollow(memberKey, routeKey);
		if (logicalNow >= follow.deadline())
		{
			// A spent capacity/cooldown wait is not geometric evidence. Release this
			// attempt so the next pulse can obtain a fresh bounded route budget.
			final boolean transientWait = (follow.route() == null) && !follow.requested() && (follow.retryAt() > 0);
			replaceFollow(memberKey, follow, transientWait ? null : follow.terminal(origin, false));
			cancelOwnedRoute(routeKey, generation);
			return 1;
		}
		if (logicalNow < follow.retryAt())
		{
			return 1;
		}
		if (follow.route() == null)
		{
			final RouteAttempt attempt;
			if (!follow.requested())
			{
				final FollowState requested = follow.requestedState();
				if (!replaceFollow(memberKey, follow, requested))
				{
					return 1;
				}
				follow = requested;
				attempt = request(routeKey, generation, actor, new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("character.object", Integer.toString(leader.ref().characterObjectId())), destination, topologyHash, logicalNow, follow.deadline());
			}
			else
			{
				attempt = poll(routeKey);
			}
			if (attempt.status() == AttemptStatus.PENDING)
			{
				return 2;
			}
			if (attempt.status() != AttemptStatus.READY)
			{
				// Capacity and cooldown are ordinary waits; a geometric failure needs changed evidence.
				final boolean transientWait = (attempt.navigationStatus() != null) && Set.of(PhantomNavigationResult.Status.PROFILE_BUSY, PhantomNavigationResult.Status.QUEUE_BACKPRESSURE, PhantomNavigationResult.Status.COOLDOWN).contains(attempt.navigationStatus());
				final long retryAt = Math.min(follow.deadline(), logicalNow > Long.MAX_VALUE - 1_000_000_000L ? Long.MAX_VALUE : logicalNow + 1_000_000_000L);
				replaceFollow(memberKey, follow, transientWait ? follow.retry(retryAt) : follow.terminal(origin, false));
				cancelOwnedRoute(routeKey, generation);
				return 2;
			}
			final FollowState ready = follow.withRoute(attempt.route());
			if (!replaceFollow(memberKey, follow, ready))
			{
				cancelOwnedRoute(routeKey, generation);
				return 2;
			}
			follow = ready;
		}
		final RouteManifest route = follow.route();
		releaseCompletedMovement(route.routeId(), List.of(actor.ref()), Map.of(actor.ref(), actor), logicalNow);
		int waypoint = route.currentWaypoint();
		while ((waypoint + 1 < route.waypoints().size()) && (origin.distanceTo(route.waypoints().get(waypoint)) <= _followPolicy.waypointRadius()))
		{
			waypoint++;
		}
		if ((waypoint + 1 == route.waypoints().size()) && (separation <= _followPolicy.stopDistance()))
		{
			replaceFollow(memberKey, follow, follow.terminal(origin, true));
			cancelOwnedRoute(routeKey, generation);
			return 2;
		}
		if (waypoint != route.currentWaypoint())
		{
			final FollowState advanced = follow.withRoute(route.withProgress(waypoint, RouteStatus.MOVING));
			if (!replaceFollow(memberKey, follow, advanced))
			{
				return 1;
			}
			follow = advanced;
		}
		if (actor.moving() || !reserveMovement(actor.ref().profileId()))
		{
			return 2;
		}
		final PhantomNavigationPoint target = route.waypoints().get(waypoint);
		final String operationKey = "party.follow." + route.routeId().substring(0, 20) + '.' + generation + '.' + waypoint;
		final ExternalActionLease lease = _combat.acquireExternalAction(new ExternalActionRequest(actor.ref().profileId(), ExternalActionKind.PARTY_ROUTE, operationKey, follow.deadline(), owned)).lease();
		if (lease == null)
		{
			releaseMovementReservation(actor.ref().profileId());
			return 3;
		}
		final ActionOutcome outcome = lease.moveTo(target.x(), target.y(), target.z(), target.instanceId());
		if (((outcome == ActionOutcome.ISSUED) || (outcome == ActionOutcome.ALREADY_OWNED)) && rememberMovement(actor.ref().profileId(), route.routeId(), lease))
		{
			return 3;
		}
		releaseMovementReservation(actor.ref().profileId());
		lease.close();
		return 3;
	}

	private boolean ownsFollow(String memberKey, String routeKey)
	{
		synchronized (_stateLock)
		{
			final FollowState current = _followStates.get(memberKey);
			return (current != null) && current.routeKey().equals(routeKey) && !current.settled() && !current.blocked();
		}
	}

	private boolean replaceFollow(String memberKey, FollowState expected, FollowState replacement)
	{
		synchronized (_stateLock)
		{
			if (_followStates.get(memberKey) != expected)
			{
				return false;
			}
			if (replacement == null)
			{
				_followStates.remove(memberKey);
			}
			else
			{
				_followStates.put(memberKey, replacement);
			}
			return true;
		}
	}

	public void cancel(String groupId)
	{
		cancel(groupId, 0);
	}

	/** A stale membership generation may release only its own navigation and movement. */
	public void cancel(String groupId, long expectedGeneration)
	{
		final Set<String> routes = new HashSet<>();
		routes.add(groupId);
		final String prefix = groupId + ".follow." + (expectedGeneration == 0 ? "" : expectedGeneration + ".");
		synchronized (_stateLock)
		{
			_followStates.entrySet().removeIf(entry ->
			{
				final FollowState follow = entry.getValue();
				if ((follow.parentGroupId().equals(groupId) || follow.routeKey().equals(groupId)) && ((expectedGeneration == 0) || (follow.groupGeneration() == expectedGeneration)))
				{
					routes.add(follow.routeKey());
					return true;
				}
				return false;
			});
			_pending.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(routes::add);
			_routeByGroup.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(routes::add);
		}
		routes.forEach(key -> cancelOwnedRoute(key, expectedGeneration));
	}

	public void cancelFollow(String groupId, long generation, long profileId)
	{
		final String memberKey = followMemberKey(groupId, generation, profileId);
		final FollowState follow;
		synchronized (_stateLock)
		{
			follow = _followStates.remove(memberKey);
		}
		if (follow != null)
		{
			cancelOwnedRoute(follow.routeKey(), generation);
		}
	}

	public int holdRadius()
	{
		return _followPolicy.holdRadius();
	}

	/** A member may release only movement leased by this exact group route. */
	public void cancelMemberMovement(String groupId, long generation, long profileId)
	{
		cancelFollow(groupId, generation, profileId);
		final MovementLease movement;
		synchronized (_stateLock)
		{
			final MovementLease current = _movement.get(profileId);
			movement = (current != null) && (_routeGenerations.getOrDefault(groupId, 0L) == generation) && current._routeId.equals(_routeByGroup.get(groupId)) ? _movement.remove(profileId) : null;
		}
		if (movement != null)
		{
			movement._lease.close();
		}
	}

	private void cancelOwnedRoute(String groupId, long expectedGeneration)
	{
		final PendingRoute pending;
		final List<ExternalActionLease> movement;
		synchronized (_stateLock)
		{
			final PendingRoute currentPending = _pending.get(groupId);
			pending = (currentPending != null) && ((expectedGeneration == 0) || (currentPending._generation == expectedGeneration)) ? _pending.remove(groupId) : null;
			final String routeId;
			if ((expectedGeneration == 0) || (_routeGenerations.getOrDefault(groupId, 0L) == expectedGeneration))
			{
				routeId = _routeByGroup.remove(groupId);
				_routeDeadlines.remove(groupId);
				_routeGenerations.remove(groupId);
			}
			else
			{
				routeId = null;
			}
			final RouteAttempt terminal = _terminalByGroup.get(groupId);
			if ((terminal != null) && ((expectedGeneration == 0) || (terminal.generation() == expectedGeneration)))
			{
				_terminalByGroup.remove(groupId);
			}
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
			final Set<String> owned = new HashSet<>(_pending.keySet());
			owned.addAll(_routeByGroup.keySet());
			owned.addAll(_terminalByGroup.keySet());
			_followStates.values().forEach(follow -> owned.add(follow.parentGroupId()));
			groups = List.copyOf(owned);
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
			return new Snapshot(_pending.size(), _routeByGroup.size(), _routeDeadlines.size(), _movement.size(), _terminalByGroup.size());
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

	private void rememberRoute(String groupId, String routeId, long deadline, long generation)
	{
		synchronized (_stateLock)
		{
			_routeByGroup.put(groupId, routeId);
			_routeDeadlines.put(groupId, deadline);
			_routeGenerations.put(groupId, generation);
		}
	}

	private void rememberTerminalLocked(String groupId, RouteAttempt attempt)
	{
		_terminalByGroup.remove(groupId);
		_terminalByGroup.put(groupId, attempt);
		while (_terminalByGroup.size() > MAX_TERMINAL_RECEIPTS)
		{
			_terminalByGroup.remove(_terminalByGroup.keySet().iterator().next());
		}
	}

	private static RouteAttempt completed(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, PhantomNavigationResult result, String topologyHash)
	{
		if ((result == null) || (result.route() == null))
		{
			return RouteAttempt.failed(routeId, generation, destination, result);
		}
		return RouteAttempt.ready(routeId, generation, destination, manifest(routeId, generation, destination, result, topologyHash), result.status());
	}

	private static RouteManifest manifest(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, PhantomNavigationResult result, String topologyHash)
	{
		final String navigationHash = PhantomPartyModel.sha256(result.route().mode() + "|" + result.route().waypoints());
		return new RouteManifest(routeId, generation, destination, result.route().waypoints(), 0, 250, 1500, RouteStatus.MOVING, topologyHash, navigationHash);
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
		FAILED,
		REJECTED,
		UNAVAILABLE
	}

	public record RouteActivity(ActivityStatus status, String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, boolean plannerOwned, boolean routeOwned, boolean movementOwned, PhantomNavigationResult.Status navigationStatus, String reasonKey)
	{
		public static RouteActivity none()
		{
			return new RouteActivity(ActivityStatus.NONE, "0".repeat(64), 0, null, false, false, false, null, "party.route.none");
		}

		private static RouteActivity terminal(RouteAttempt attempt)
		{
			return new RouteActivity(ActivityStatus.valueOf(attempt.status().name()), attempt.routeId(), attempt.generation(), attempt.destination(), false, false, false, attempt.navigationStatus(), attempt.reasonKey());
		}

		public boolean nonTerminal()
		{
			return Set.of(ActivityStatus.PLANNING, ActivityStatus.MOVING, ActivityStatus.REGROUPING).contains(status);
		}

		public boolean terminal()
		{
			return Set.of(ActivityStatus.ARRIVED, ActivityStatus.FAILED, ActivityStatus.REJECTED, ActivityStatus.UNAVAILABLE).contains(status);
		}
	}

	public enum AttemptStatus
	{
		NONE,
		PENDING,
		READY,
		FAILED,
		REJECTED,
		UNAVAILABLE
	}

	public record RouteAttempt(AttemptStatus status, String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, RouteManifest route, PhantomNavigationResult.Status navigationStatus, String reasonKey)
	{
		private static RouteAttempt none()
		{
			return new RouteAttempt(AttemptStatus.NONE, "0".repeat(64), 0, null, null, null, "party.route.none");
		}

		private static RouteAttempt pending(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination)
		{
			return new RouteAttempt(AttemptStatus.PENDING, routeId, generation, destination, null, null, "party.route.pending");
		}

		private static RouteAttempt ready(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, RouteManifest route, PhantomNavigationResult.Status navigationStatus)
		{
			return new RouteAttempt(AttemptStatus.READY, routeId, generation, destination, route, navigationStatus, navigationReason(navigationStatus));
		}

		private static RouteAttempt failed(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, PhantomNavigationResult result)
		{
			final PhantomNavigationResult.Status status = result == null ? PhantomNavigationResult.Status.BACKEND_FAILURE : result.status();
			return new RouteAttempt(AttemptStatus.FAILED, routeId, generation, destination, null, status, navigationReason(status));
		}

		private static RouteAttempt rejected(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, PhantomNavigationResult result)
		{
			final PhantomNavigationResult.Status status = result == null ? PhantomNavigationResult.Status.BACKEND_FAILURE : result.status();
			return new RouteAttempt(AttemptStatus.REJECTED, routeId, generation, destination, null, status, navigationReason(status));
		}

		private static RouteAttempt unavailable(String routeId, long generation, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef destination, String reasonKey)
		{
			return new RouteAttempt(AttemptStatus.UNAVAILABLE, routeId, generation, destination, null, null, reasonKey);
		}

		private static String navigationReason(PhantomNavigationResult.Status status)
		{
			return "party.route.navigation." + status.name().toLowerCase(Locale.ROOT);
		}
	}

	public record Snapshot(int navigationClaims, int routeClaims, int deadlineClaims, int movementClaims, int terminalReceipts)
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

	private static String followMemberKey(String groupId, long generation, long profileId)
	{
		return groupId + ".follow." + generation + '.' + profileId;
	}

	private record FollowState(String parentGroupId, long groupGeneration, long profileId, MemberRef leader, PhantomNavigationPoint origin, PhantomNavigationPoint destination, String topologyHash, String routeKey, long deadline, long retryAt, RouteManifest route, boolean requested, boolean settled, boolean blocked)
	{
		private FollowState requestedState()
		{
			return new FollowState(parentGroupId, groupGeneration, profileId, leader, origin, destination, topologyHash, routeKey, deadline, retryAt, route, true, false, false);
		}

		private FollowState withRoute(RouteManifest next)
		{
			return new FollowState(parentGroupId, groupGeneration, profileId, leader, origin, destination, topologyHash, routeKey, deadline, retryAt, next, true, false, false);
		}

		private FollowState terminal(PhantomNavigationPoint current, boolean arrived)
		{
			return new FollowState(parentGroupId, groupGeneration, profileId, leader, current, destination, topologyHash, routeKey, deadline, retryAt, route, true, arrived, !arrived);
		}

		private FollowState retry(long nextEligible)
		{
			return new FollowState(parentGroupId, groupGeneration, profileId, leader, origin, destination, topologyHash, routeKey, deadline, nextEligible, null, false, false, false);
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
