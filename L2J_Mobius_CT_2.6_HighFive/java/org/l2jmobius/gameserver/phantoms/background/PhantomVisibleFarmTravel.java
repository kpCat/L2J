/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker.ProgressStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Native movement/GK continuation of the same ordinary farm goal, before AutoPlay. */
public final class PhantomVisibleFarmTravel implements PhantomMaterializationLifecyclePort
{
	private final PhantomMaterializationService _materialization;
	private final PhantomBackgroundService _background;
	private final PhantomNormalGatekeeperTravel _travel;
	private final PhantomNavigationService _navigation;
	private final LongPredicate _permitsOrdinary;
	private final PhantomRelevanceSignalPort _signals;
	private final Map<Long, Journey> _journeys = new ConcurrentHashMap<>();
	private final Map<Long, TerminalFailure> _terminalReasons = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicLong _failureSequence = new java.util.concurrent.atomic.AtomicLong();
	private final BiConsumer<Long, Failure> _failure;
	private final LongSupplier _clock;
	private final java.util.concurrent.atomic.AtomicLong _signalSequence = new java.util.concurrent.atomic.AtomicLong();

	public PhantomVisibleFarmTravel(PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel travel, PhantomNavigationService navigation, LongPredicate permitsOrdinary, PhantomRelevanceSignalPort signals)
	{
		this(materialization, background, travel, navigation, permitsOrdinary, signals, (_id, _failure) -> {}, System::nanoTime);
	}

	public PhantomVisibleFarmTravel(PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel travel, PhantomNavigationService navigation, LongPredicate permitsOrdinary, PhantomRelevanceSignalPort signals, BiConsumer<Long, Failure> failure, LongSupplier clock)
	{
		_materialization = Objects.requireNonNull(materialization);
		_background = Objects.requireNonNull(background);
		_travel = Objects.requireNonNull(travel);
		_navigation = Objects.requireNonNull(navigation);
		_permitsOrdinary = Objects.requireNonNull(permitsOrdinary);
		_signals = Objects.requireNonNull(signals);
		_failure = Objects.requireNonNull(failure);
		_clock = Objects.requireNonNull(clock);
	}

	public boolean arrive(long profileId, PhantomGoal goal)
	{
		if (!_permitsOrdinary.test(profileId))
		{
			remove(profileId);
			return false;
		}
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || action.player().isDead() || action.player().isInParty())
			{
				remove(profileId);
				return false;
			}
			final Player player = action.player();
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			final var state = _background.acquisitionSnapshot(profileId).orElse(null);
			if ((state == null) || (state.state() != PhantomBackgroundState.State.MATERIALIZED) || (state.identity().characterObjectId() != player.getObjectId()))
			{
				return false;
			}
			if (state.position().committedAnchorId().equals(spec.anchorId()))
			{
				remove(profileId);
				_terminalReasons.remove(profileId);
				return true;
			}
			Journey journey = _journeys.get(profileId);
			if ((journey != null) && ((journey.goalId != goal.goalId()) || (journey.revision != goal.revision()) || (journey.objectId != player.getObjectId())))
			{
				remove(profileId);
				journey = null;
			}
			if (journey == null)
			{
				final var route = _travel.route(state.position().committedAnchorId(), spec.anchorId()).orElse(List.of());
				if (route.isEmpty())
				{
					rememberFailure(profileId, "travel.route_absent");
					_failure.accept(profileId, new Failure(goal, "", "travel.route_absent"));
					return false;
				}
				journey = new Journey(goal, player, route.getFirst(), _clock.getAsLong());
				_journeys.put(profileId, journey);
			}
			if ((_clock.getAsLong() - journey.startedNanos) >= _navigation.policy().maximumAttemptDurationNanos())
			{
				fail(profileId, journey, "travel.journey_deadline");
				return false;
			}
			final var arrival = _travel.topology().findAnchor(journey.step.toAnchorId()).orElseThrow();
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(arrival, player.getHeading()).orElse(null);
			if (canonical == null)
			{
				fail(profileId, journey, "travel.destination_unproven");
				return false;
			}
			if (journey.step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER)
			{
				final var leg = journey.step.gatekeeper();
				if (!PhantomNormalGatekeeperTravel.matchesNative(leg))
				{
					fail(profileId, journey, "travel.gatekeeper_unproven");
					return false;
				}
				if (!journey.teleported)
				{
					if (!walk(profileId, player, journey, new PhantomNavigationPoint(leg.sourceX(), leg.sourceY(), leg.sourceZ(), 0), 100))
					{
						return false;
					}
					final Npc[] teleporter = {null};
					World.getInstance().forEachVisibleObjectInRange(player, Npc.class, 200, npc ->
					{
						if ((npc.getId() == leg.teleporterNpcId()) && (npc.getInstanceId() == player.getInstanceId()))
						{
							teleporter[0] = npc;
						}
					});
					if (teleporter[0] == null)
					{
						journey.reason = "travel.native_gatekeeper_absent";
						retryOrFail(profileId, journey);
						return false;
					}
					player.stopMove(null);
					TeleporterData.getInstance().getHolder(leg.teleporterNpcId(), leg.teleportListName()).doTeleport(player, teleporter[0], leg.destinationIndex());
					if (player.hasHeadlessOutboundSession() && player.isTeleporting())
					{
						player.onTeleported();
					}
					journey.teleported = (player.getInstanceId() == 0) && (Math.hypot((long) player.getX() - leg.destinationX(), (long) player.getY() - leg.destinationY()) < 128);
					journey.reason = journey.teleported ? "travel.native_teleport_arrived" : "travel.native_teleport_rejected";
					clearRoute(profileId, journey);
					if (!journey.teleported) { retryOrFail(profileId, journey); }
					else { hold(profileId); }
					return false;
				}
			}
			if (!walk(profileId, player, journey, new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId()), 0))
			{
				return false;
			}
			if (!_background.captureVisibleArrival(profileId, player, goal, arrival.id()))
			{
				journey.reason = "travel.arrival_capture_pending";
				retryOrFail(profileId, journey);
				return false;
			}
			remove(profileId);
			return arrival.id().equals(spec.anchorId());
		}
	}

	private boolean walk(long profileId, Player player, Journey journey, PhantomNavigationPoint destination, int radius)
	{
		final var current = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		if ((current.instanceId() == destination.instanceId()) && (Math.hypot((long) current.x() - destination.x(), (long) current.y() - destination.y()) <= radius) && (Math.abs((long) current.z() - destination.z()) <= 100))
		{
			clearRoute(profileId, journey);
			return true;
		}
		if (player.isTeleporting() || player.isInCombat() || player.isCastingNow())
		{
			journey.reason = "travel.native_action_busy";
			return false;
		}
		final long now = Math.max(1, _clock.getAsLong());
		if (now < journey.retryAtNanos)
		{
			return false;
		}
		if (journey.requestId == 0)
		{
			journey.deadline = now + _navigation.policy().defaultRequestDeadlineNanos();
			final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, current, destination, now, journey.deadline, 100_000));
			journey.requestId = submission.requestId();
			if (submission.immediateResult() != null)
			{
				_navigation.consume(journey.requestId);
				if (!terminalResult(profileId, journey, submission.immediateResult())) { return false; }
			}
			else if ((submission.status() != PhantomNavigationService.SubmissionStatus.ACCEPTED) || (journey.requestId <= 0))
			{
				journey.reason = "travel.navigation_protocol_failure";
				retryOrFail(profileId, journey);
				return false;
			}
		}
		if (journey.waypoints == null)
		{
			final var result = _navigation.consume(journey.requestId).orElse(null);
			if (result == null)
			{
				if ((now >= journey.deadline) || _navigation.find(journey.requestId).isEmpty())
				{
					journey.reason = now >= journey.deadline ? "travel.navigation_deadline_expired" : "travel.navigation_terminal_missing";
					retryOrFail(profileId, journey);
					return false;
				}
				journey.reason = "travel.navigation_pending";
				hold(profileId);
				return false;
			}
			if (!terminalResult(profileId, journey, result)) { return false; }
		}
		while ((journey.index < journey.waypoints.size() - 1) && (current.distanceTo(journey.waypoints.get(journey.index)) <= _navigation.arrivalRadius()))
		{
			journey.index++;
		}
		if (journey.trackedIndex != journey.index)
		{
			_navigation.progressTracker().cancel(profileId, journey.requestId);
			final var waypoint = journey.waypoints.get(journey.index);
			final var leg = new PhantomNavigationRoute(journey.route.mode(), current, waypoint, List.of(waypoint), journey.route.geodataCapability(), now, false, 1, _navigation.policy().maximumRouteDistance());
			final var begun = _navigation.progressTracker().begin(profileId, journey.requestId, leg, now);
			if (begun.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker.BeginStatus.TRACKING)
			{
				journey.reason = "travel.navigation_progress_busy";
				retryOrFail(profileId, journey);
				return false;
			}
			journey.trackedIndex = journey.index;
		}
		final var progress = _navigation.progressTracker().observe(profileId, journey.requestId, current, now).status();
		if ((progress == ProgressStatus.PROGRESS) || (progress == ProgressStatus.ARRIVED)) { _terminalReasons.remove(profileId); }
		if (progress == ProgressStatus.ARRIVED) { journey.trackedIndex = -1; }
		if ((progress == ProgressStatus.STUCK) || (progress == ProgressStatus.TIMEOUT) || (progress == ProgressStatus.STALE))
		{
			journey.reason = "travel.native_progress_" + progress.name().toLowerCase(java.util.Locale.ROOT);
			retryOrFail(profileId, journey);
			return false;
		}
		if (!player.isMoving())
		{
			final var waypoint = journey.waypoints.get(journey.index);
			player.getAI().setIntention(Intention.MOVE_TO, new Location(waypoint.x(), waypoint.y(), waypoint.z()));
			journey.moveIssued = player.isMoving();
			journey.moveX = player.getXdestination();
			journey.moveY = player.getYdestination();
		}
		journey.reason = player.isMoving() ? "travel.native_walking" : "travel.native_move_rejected";
		if (player.isMoving()) { hold(profileId); }
		else { retryOrFail(profileId, journey); }
		return false;
	}

	private boolean terminalResult(long profileId, Journey journey, PhantomNavigationResult result)
	{
		if ((result.profileId() != profileId) || (result.requestId() != journey.requestId))
		{
			journey.reason = "travel.navigation_protocol_failure";
			retryOrFail(profileId, journey);
			return false;
		}
		if ((result.route() == null) || (result.route().mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA))
		{
			journey.reason = "travel.navigation_" + result.status().name().toLowerCase(java.util.Locale.ROOT);
			clearRoute(profileId, journey);
			switch (result.status())
			{
				case COOLDOWN, QUEUE_BACKPRESSURE, PROFILE_BUSY, BACKEND_FAILURE, SERVICE_NOT_RUNNING, CANCELLED, DEADLINE_EXPIRED -> retryOrFail(profileId, journey);
				default -> fail(profileId, journey, journey.reason);
			}
			return false;
		}
		journey.route = result.route();
		journey.waypoints = result.route().waypoints();
		return true;
	}

	private void hold(long profileId)
	{
		_signals.submit(profileId, new PhantomRelevanceSignal("visible.farm.travel", signalSequence(), PhantomActivityState.NEARBY_PERCEPTIBLE, 5_000));
	}

	private void retryOrFail(long profileId, Journey journey)
	{
		clearRoute(profileId, journey);
		_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		if (++journey.failures >= 3)
		{
			fail(profileId, journey, journey.reason);
		}
		else
		{
			journey.retryAtNanos = _clock.getAsLong() + 1_000_000_000L;
		}
	}

	private void fail(long profileId, Journey journey, String reason)
	{
		remove(profileId);
		rememberFailure(profileId, reason);
		_failure.accept(profileId, new Failure(journey.goal, journey.step.id(), reason));
	}

	private synchronized void rememberFailure(long profileId, String reason)
	{
		if (!_terminalReasons.containsKey(profileId) && (_terminalReasons.size() >= _navigation.policy().maximumTrackedProfiles()))
		{
			final var iterator = _terminalReasons.keySet().iterator();
			if (iterator.hasNext()) { _terminalReasons.remove(iterator.next()); }
		}
		_terminalReasons.put(profileId, new TerminalFailure(reason, _failureSequence.incrementAndGet()));
	}

	public record TerminalFailure(String reason, long sequence) { }

	public TerminalFailure lastFailure(long profileId) { return _terminalReasons.get(profileId); }

	public record Failure(PhantomGoal goal, String stepId, String reason)
	{
		public boolean routeFailure()
		{
			return reason.equals("travel.route_absent") || reason.equals("travel.destination_unproven") || reason.equals("travel.gatekeeper_unproven") || reason.equals("travel.navigation_no_path") || reason.equals("travel.navigation_route_obstructed") || reason.equals("travel.navigation_route_budget_exceeded");
		}
	}

	public String reason(long profileId)
	{
		final Journey journey = _journeys.get(profileId);
		final TerminalFailure failure = _terminalReasons.get(profileId);
		return journey == null ? failure == null ? "" : failure.reason() : journey.reason;
	}

	private void clearRoute(long profileId, Journey journey)
	{
		if (journey.requestId != 0)
		{
			_navigation.progressTracker().cancel(profileId, journey.requestId);
			_navigation.cancel(profileId, journey.requestId);
			_navigation.consume(journey.requestId);
		}
		journey.requestId = 0;
		journey.waypoints = null;
		journey.route = null;
		journey.trackedIndex = -1;
		journey.index = 0;
	}

	private void remove(long profileId)
	{
		final Journey journey = _journeys.remove(profileId);
		if (journey != null)
		{
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (action != null)
				{
					stopOwnedMove(journey, action.player());
				}
			}
			clearRoute(profileId, journey);
			_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		}
	}

	private long signalSequence()
	{
		return _signalSequence.updateAndGet(previous -> Math.max(previous + 1, Math.max(1, _clock.getAsLong())));
	}

	private static void stopOwnedMove(Journey journey, Player player)
	{
		if ((journey != null) && journey.moveIssued && (player.getObjectId() == journey.objectId) && player.hasAI() && (player.getAI().getIntention() == Intention.MOVE_TO) && player.isMoving() && (player.getXdestination() == journey.moveX) && (player.getYdestination() == journey.moveY))
		{
			player.stopMove(null);
			player.getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
		remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		stopOwnedMove(_journeys.get(profileId), player);
		remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}

	private static final class Journey
	{
		private final long goalId;
		private final long revision;
		private final int objectId;
		private final PhantomNormalGatekeeperTravel.Step step;
		private final PhantomGoal goal;
		private final long startedNanos;
		private int failures;
		private long retryAtNanos;
		private boolean teleported;
		private boolean moveIssued;
		private int moveX;
		private int moveY;
		private long requestId;
		private long deadline;
		private List<PhantomNavigationPoint> waypoints;
		private PhantomNavigationRoute route;
		private int trackedIndex = -1;
		private int index;
		private volatile String reason = "travel.started";

		private Journey(PhantomGoal goal, Player player, PhantomNormalGatekeeperTravel.Step step, long startedNanos)
		{
			goalId = goal.goalId();
			revision = goal.revision();
			objectId = player.getObjectId();
			this.step = step;
			this.goal = goal;
			this.startedNanos = startedNanos;
		}
	}
}
