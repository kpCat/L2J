/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;

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

	public PhantomVisibleFarmTravel(PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel travel, PhantomNavigationService navigation, LongPredicate permitsOrdinary, PhantomRelevanceSignalPort signals)
	{
		_materialization = Objects.requireNonNull(materialization);
		_background = Objects.requireNonNull(background);
		_travel = Objects.requireNonNull(travel);
		_navigation = Objects.requireNonNull(navigation);
		_permitsOrdinary = Objects.requireNonNull(permitsOrdinary);
		_signals = Objects.requireNonNull(signals);
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
					return false;
				}
				journey = new Journey(goal, player, route.getFirst());
				_journeys.put(profileId, journey);
			}
			_signals.submit(profileId, new PhantomRelevanceSignal("visible.farm.travel", Math.max(1, System.nanoTime()), PhantomActivityState.NEARBY_PERCEPTIBLE, 60_000));
			final var arrival = _travel.topology().findAnchor(journey.step.toAnchorId()).orElseThrow();
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(arrival, player.getHeading()).orElse(null);
			if (canonical == null)
			{
				remove(profileId);
				return false;
			}
			if (journey.step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER)
			{
				final var leg = journey.step.gatekeeper();
				if (!PhantomNormalGatekeeperTravel.matchesNative(leg))
				{
					remove(profileId);
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
		final long now = Math.max(1, System.nanoTime());
		if ((journey.requestId != 0) && (now >= journey.deadline))
		{
			clearRoute(profileId, journey);
		}
		if (journey.requestId == 0)
		{
			journey.deadline = now + 60_000_000_000L;
			final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, current, destination, now, journey.deadline, 100_000));
			journey.requestId = submission.requestId();
			if (journey.requestId == 0)
			{
				journey.reason = "travel.navigation_admission_rejected";
				return false;
			}
		}
		if (journey.waypoints == null)
		{
			final var result = _navigation.consume(journey.requestId).orElse(null);
			if (result == null)
			{
				journey.reason = "travel.navigation_pending";
				return false;
			}
			if ((result.route() == null) || (result.route().mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA))
			{
				journey.reason = "travel.navigation_unproven";
				clearRoute(profileId, journey);
				return false;
			}
			journey.waypoints = result.route().waypoints();
		}
		while ((journey.index < journey.waypoints.size() - 1) && (current.distanceTo(journey.waypoints.get(journey.index)) <= _navigation.arrivalRadius()))
		{
			journey.index++;
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
		return false;
	}

	public String reason(long profileId)
	{
		final Journey journey = _journeys.get(profileId);
		return journey == null ? "" : journey.reason;
	}

	private void clearRoute(long profileId, Journey journey)
	{
		if (journey.requestId != 0)
		{
			_navigation.cancel(profileId, journey.requestId);
			_navigation.consume(journey.requestId);
		}
		journey.requestId = 0;
		journey.waypoints = null;
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
			_signals.withdraw(profileId, "visible.farm.travel", Math.max(1, System.nanoTime()));
		}
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
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		stopOwnedMove(_journeys.get(profileId), player);
		remove(profileId);
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
		private boolean teleported;
		private boolean moveIssued;
		private int moveX;
		private int moveY;
		private long requestId;
		private long deadline;
		private List<PhantomNavigationPoint> waypoints;
		private int index;
		private volatile String reason = "travel.started";

		private Journey(PhantomGoal goal, Player player, PhantomNormalGatekeeperTravel.Step step)
		{
			goalId = goal.goalId();
			revision = goal.revision();
			objectId = player.getObjectId();
			this.step = step;
		}
	}
}
