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
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
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
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
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
	private final Map<Long, Attempt> _attempts = new ConcurrentHashMap<>();
	private final Map<Long, PendingStore> _pendingStores = new ConcurrentHashMap<>();
	private final Map<Long, TerminalFailure> _terminalReasons = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicLong _failureSequence = new java.util.concurrent.atomic.AtomicLong();
	private final BiConsumer<Long, Failure> _failure;
	private final LongSupplier _clock;
	private final java.util.concurrent.atomic.AtomicLong _signalSequence = new java.util.concurrent.atomic.AtomicLong();
	private volatile java.util.function.LongFunction<java.util.Set<String>> _routeExclusions = _ -> java.util.Set.of();

	public void bindRouteExclusions(java.util.function.LongFunction<java.util.Set<String>> exclusions)
	{
		_routeExclusions = Objects.requireNonNull(exclusions);
	}

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
		if (!currentGoal(profileId, goal)) { discardStale(profileId, goal); return false; }
		PendingStore pending = _pendingStores.get(profileId);
		if (pending == null)
		{
			// A failed checkpoint closes ordinary roots. Preserve its control receipt before advance can discard the Journey.
			final var lifetime = _materialization.find(profileId).orElse(null);
			final Player player = lifetime == null ? null : World.getInstance().getPlayer(lifetime.characterObjectId());
			if (player != null && player.hasPendingOwnedStore() && player.getNativeWorkOwner() != null
				&& player.getNativeWorkOwner().player() == player && player.getNativeWorkOwner().isCurrent()
				&& player.getNativeWorkOwner().epoch() == lifetime.materializedAtNanos())
			{
				_pendingStores.putIfAbsent(profileId, new PendingStore(player, lifetime.materializedAtNanos(), goal, null, _journeys.get(profileId)));
				pending = _pendingStores.get(profileId);
			}
		}
		if (pending == null)
		{
			if (advance(profileId, goal)) { return true; }
			pending = _pendingStores.get(profileId);
		}
		if ((pending == null) || !pending.goal.equals(goal)) { return false; }
		if ((pending.journey != null) && (_journeys.get(profileId) != pending.journey))
		{
			closeRoutePhase(profileId, pending.journey);
			_pendingStores.remove(profileId, pending);
			return false;
		}
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.characterObjectId() != pending.player.getObjectId()) || (lifetime.materializedAtNanos() != pending.epoch))
		{
			if (pending.journey != null) { closeRoutePhase(profileId, pending.journey); }
			_pendingStores.remove(profileId, pending);
			return false;
		}
		final var owner = pending.player.getNativeWorkOwner();
		if (owner == null || owner.player() != pending.player || !owner.isCurrent() || owner.epoch() != pending.epoch
			|| World.getInstance().getPlayer(pending.player.getObjectId()) != pending.player || !_permitsOrdinary.test(profileId)
			|| pending.player.isDead() || pending.player.isInParty() || !pending.player.hasHeadlessOutboundSession())
		{
			if (pending.journey != null) { closeRoutePhase(profileId, pending.journey); }
			return false;
		}
		final PendingStore exactPending = pending;
		if ((pending.anchorId != null) && _travel.topology().findAnchor(pending.anchorId).filter(anchor -> !isUsableLocalFarmPosition(exactPending.player, anchor)).isPresent()) { return false; }
		if (pending.player.hasPendingOwnedStore())
		{
			final var resumed = _background.resumeVisibleOwnedStore(profileId, pending.player, goal);
			if (resumed.status() != PhantomBackgroundService.VisibleStoreStatus.SUCCESS)
			{
				rememberFailure(profileId, resumed.reason());
				return false;
			}
			_pendingStores.remove(profileId, pending);
			if (pending.anchorId == null) { return advance(profileId, goal); }
			remove(profileId, pending.journey);
			return pending.anchorId.equals(PhantomBackgroundGoalSpec.parse(goal).anchorId());
		}
		if (pending.anchorId == null) { _pendingStores.remove(profileId, pending); return false; }
		if (!_background.captureVisibleArrival(profileId, pending.player, goal, pending.anchorId))
		{
			if (pending.journey != null) { pending.journey.reason = "travel.arrival_capture_pending"; }
			return false;
		}
		_pendingStores.remove(profileId, pending);
		remove(profileId, pending.journey);
		return pending.anchorId.equals(PhantomBackgroundGoalSpec.parse(goal).anchorId());
	}

	private boolean advance(long profileId, PhantomGoal goal)
	{
		if (!currentGoal(profileId, goal)) { discardStale(profileId, goal); return false; }
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
			final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			var state = _background.acquisitionSnapshot(profileId).orElse(null);
			if (player.hasPendingOwnedStore())
			{
				_pendingStores.put(profileId, new PendingStore(player, epoch, goal, null, null));
				return false;
			}
			if ((state == null) || (state.state() != PhantomBackgroundState.State.MATERIALIZED) || (state.identity().characterObjectId() != player.getObjectId()))
			{
				return false;
			}
			final var targetAnchor = _travel.topology().findAnchor(spec.anchorId()).orElse(null);
			final var existingAttempt = _attempts.get(profileId);
			if ((existingAttempt != null) && existingAttempt.matches(goal, player, epoch) && existingAttempt.terminal) { return false; }
			final boolean sameAnchor = state.position().committedAnchorId().equals(spec.anchorId());
			Journey journey = _journeys.get(profileId);
			if ((journey != null) && ((journey.goalId != goal.goalId()) || (journey.revision != goal.revision()) || (journey.player != player) || (journey.epoch != epoch)))
			{
				remove(profileId, journey);
				journey = null;
			}
			if ((targetAnchor != null) && L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), player, targetAnchor) && isUsableLocalFarmPosition(player, targetAnchor))
			{
				if ((journey == null) && sameAnchor) { return true; }
				if (journey != null) { clearRoute(profileId, journey); closeRoutePhase(profileId, journey); }
				player.stopMove(null);
				_pendingStores.put(profileId, new PendingStore(player, epoch, goal, targetAnchor.id(), journey));
				return false;
			}
			final Attempt attempt = attempt(profileId, goal, player, epoch);
			if ((attempt == null) || attempt.terminal) { return false; }
			if (journey == null)
			{
				final boolean local = (targetAnchor != null) && (targetAnchor.point().instanceId() == player.getInstanceId()) && (Math.hypot((long) player.getX() - targetAnchor.point().x(), (long) player.getY() - targetAnchor.point().y()) <= 2000);
				final var route = local ? List.of(new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "live.approach." + spec.anchorId(), state.position().committedAnchorId(), spec.anchorId(), 0, null)) : _travel.route(state.position().committedAnchorId(), spec.anchorId()).orElse(List.of());
				if (route.isEmpty())
				{
					fail(profileId, attempt, "", "travel.route_absent", Disposition.ROUTE_UNUSABLE);
					return false;
				}
				journey = new Journey(goal, player, epoch, route.getFirst(), attempt);
				synchronized (_attempts)
				{
					if ((_attempts.get(profileId) != attempt) || attempt.terminal || (_journeys.putIfAbsent(profileId, journey) != null)) { return false; }
				}
			}
			if ((_clock.getAsLong() - attempt.startedNanos) >= _navigation.policy().maximumAttemptDurationNanos())
			{
				fail(profileId, journey, "travel.journey_deadline", Disposition.NATIVE_ACTION_REJECTED);
				return false;
			}
			if (attempt.transientExhausted) { return false; }
			if (_clock.getAsLong() < attempt.retryAtNanos) { return false; }
			final var arrival = _travel.topology().findAnchor(journey.step.toAnchorId()).orElseThrow();
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(arrival, player.getHeading()).orElse(null);
			if (canonical == null)
			{
				fail(profileId, journey, "travel.destination_unproven", Disposition.ROUTE_UNUSABLE);
				return false;
			}
			if (journey.step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER)
			{
				final var leg = journey.step.gatekeeper();
				if (!PhantomNormalGatekeeperTravel.matchesNative(leg))
				{
					fail(profileId, journey, "travel.gatekeeper_unproven", Disposition.ROUTE_UNUSABLE);
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
						retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED);
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
					if (!journey.teleported) { retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED); }
					else { hold(profileId); }
					return false;
				}
			}
			if (journey.arrivalPoint == null)
			{
				final var canonicalPoint = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
				journey.arrivalPoint = arrival.role() == PhantomTopologyAnchorRole.FARMING ? farmingStandpoint(profileId, arrival, canonicalPoint) : canonicalPoint;
				if (journey.arrivalPoint == null)
				{
					fail(profileId, journey, "travel.farming_standpoint_unproven", Disposition.ROUTE_UNUSABLE);
					return false;
				}
			}
			if (!walk(profileId, player, journey, journey.arrivalPoint, 0))
			{
				return false;
			}
			closeRoutePhase(profileId, journey);
			journey.reason = "travel.arrival_capture_pending";
			_pendingStores.put(profileId, new PendingStore(player, epoch, goal, arrival.id(), journey));
			return false;
		}
	}

	private static boolean isUsableLocalFarmPosition(Player player, PhantomTopologyAnchor anchor)
	{
		if (anchor.role() != PhantomTopologyAnchorRole.FARMING) { return true; }
		final var geo = GeoEngine.getInstance();
		return geo.hasGeo(player.getX(), player.getY()) && (geo.getHeight(player.getX(), player.getY(), player.getZ()) == player.getZ()) && (ZoneManager.getInstance().getZone(player.getX(), player.getY(), player.getZ(), WaterZone.class) == null);
	}

	/** A bounded local continuation of the factual farm anchor, stable for this Journey. */
	private PhantomNavigationPoint farmingStandpoint(long profileId, PhantomTopologyAnchor anchor, PhantomNavigationPoint canonical)
	{
		final var area = _travel.topology().findNode(anchor.nodeId()).map(node -> node.area()).orElse(null);
		if (area == null) { return null; }
		final var geo = GeoEngine.getInstance();
		final int[][] directions = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		for (int index = 0; index < 24; index++)
		{
			final int choice = (int) Math.floorMod(profileId + index, 24L);
			final int distance = 32 * (1 + (choice / directions.length));
			final int x = canonical.x() + (distance * directions[choice % directions.length][0]);
			final int y = canonical.y() + (distance * directions[choice % directions.length][1]);
			if (!geo.hasGeo(x, y)) { continue; }
			final int z = geo.getHeight(x, y, canonical.z());
			final var candidate = new PhantomNavigationPoint(x, y, z, canonical.instanceId());
			if (_routeExclusions.apply(profileId).contains(localRouteWitness(anchor.id(), candidate))) { continue; }
			if (area.contains(new PhantomTopologyPoint(x, y, z, candidate.instanceId())) && (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) == null) && (unsafeSegment(canonical, candidate, false, 0) == null) && geo.canMoveToTarget(canonical.x(), canonical.y(), canonical.z(), x, y, z, canonical.instanceId())) { return candidate; }
		}
		return null;
	}

	private boolean walk(long profileId, Player player, Journey journey, PhantomNavigationPoint destination, int radius)
	{
		if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
		final var current = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		if ((current.instanceId() == destination.instanceId()) && (Math.hypot((long) current.x() - destination.x(), (long) current.y() - destination.y()) <= radius) && (Math.abs((long) current.z() - destination.z()) <= (radius == 0 ? 0 : 100)))
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
		if (now < journey.attempt.retryAtNanos)
		{
			return false;
		}
		if (journey.requestId == 0)
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
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
				fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION);
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
					if (now >= journey.deadline) { retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE); }
					else { fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION); }
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
				retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE);
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
			if ((progress == ProgressStatus.STALE) && (_navigation.snapshot().state() != PhantomNavigationService.ServiceState.RUNNING)) { retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE); }
			else if (progress == ProgressStatus.STALE) { fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION); }
			else { retryOrFail(profileId, journey, Disposition.ROUTE_UNUSABLE); }
			return false;
		}
		if (!player.isMoving())
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
			final var waypoint = journey.waypoints.get(journey.index);
			final boolean waterExit = player.isInsideZone(ZoneId.WATER) || (ZoneManager.getInstance().getZone(current.x(), current.y(), current.z(), WaterZone.class) != null);
			final String unsafe = unsafeSegment(current, waypoint, waterExit, radius, witness -> journey.attempt.segmentWitness = witness);
			if (unsafe != null)
			{
				fail(profileId, journey, unsafe, Disposition.ROUTE_UNUSABLE);
				return false;
			}
			player.getAI().setIntention(Intention.MOVE_TO, new Location(waypoint.x(), waypoint.y(), waypoint.z()));
			journey.moveIssued = player.isMoving();
			journey.moveX = player.getXdestination();
			journey.moveY = player.getYdestination();
			if (journey.moveIssued)
			{
				// Native swimming may shorten a leg; validate the actual native destination too.
				final var issued = new PhantomNavigationPoint(journey.moveX, journey.moveY, player.getZdestination(), player.getInstanceId());
				final String actualUnsafe = unsafeSegment(current, issued, waterExit, waterExit ? 100 : radius, witness -> journey.attempt.segmentWitness = witness);
				if (actualUnsafe != null)
				{
					stopOwnedMove(journey, player);
					fail(profileId, journey, actualUnsafe, Disposition.ROUTE_UNUSABLE);
					return false;
				}
			}
		}
		journey.reason = player.isMoving() ? "travel.native_walking" : "travel.native_move_rejected";
		if (player.isMoving())
		{
			publishRoutePhase(profileId, journey, player);
			hold(profileId);
		}
		else { retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED); }
		return false;
	}

	/** Inspect stock native legs; water is allowed only before the first dry cell of an exit. */
	private static String unsafeSegment(PhantomNavigationPoint from, PhantomNavigationPoint to, boolean waterExit, int destinationTolerance)
	{
		return unsafeSegment(from, to, waterExit, destinationTolerance, null);
	}

	private static String unsafeSegment(PhantomNavigationPoint from, PhantomNavigationPoint to, boolean waterExit, int destinationTolerance, java.util.function.Consumer<String> witness)
	{
		final var geo = GeoEngine.getInstance();
		if ((from.instanceId() != to.instanceId()) || !geo.hasGeo(to.x(), to.y())) { return "travel.native_segment_missing_geodata"; }
		final boolean targetWater = ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), WaterZone.class) != null;
		final int targetZ = geo.getHeight(to.x(), to.y(), to.z());
		if ((targetZ != geo.getHeight(to.x(), to.y(), targetZ)) || ((!waterExit || !targetWater) && (Math.abs((long) targetZ - to.z()) > destinationTolerance))) { return "travel.native_segment_height_unproven"; }
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		boolean dry = !waterExit;
		int z = from.z();
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x());
			final int y = GeoEngine.getWorldY(cells.y());
			if (!geo.hasGeo(x, y)) { return "travel.native_segment_missing_geodata"; }
			z = geo.getHeight(x, y, z);
			if (z != geo.getHeight(x, y, z)) { return "travel.native_segment_height_unproven"; }
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null)
			{
				if (dry)
				{
					if (witness != null) { witness.accept("from=" + from + ";to=" + to + ";cell=" + x + "," + y + "," + z + ";waterExit=" + waterExit); }
					return "travel.native_segment_water_entry";
				}
			}
			else { dry = true; }
		}
		return null;
	}

	private boolean terminalResult(long profileId, Journey journey, PhantomNavigationResult result)
	{
		if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
		if ((result.profileId() != profileId) || (result.requestId() != journey.requestId))
		{
			journey.reason = "travel.navigation_protocol_failure";
			fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION);
			return false;
		}
		if ((result.route() == null) || (result.route().mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA))
		{
			journey.reason = "travel.navigation_" + result.status().name().toLowerCase(java.util.Locale.ROOT);
			clearRoute(profileId, journey);
			switch (result.status())
			{
				case COOLDOWN, QUEUE_BACKPRESSURE, PROFILE_BUSY, BACKEND_FAILURE, SERVICE_NOT_RUNNING, CANCELLED, DEADLINE_EXPIRED, ROUTE_BUDGET_EXCEEDED -> retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE);
				default -> fail(profileId, journey, journey.reason, Disposition.ROUTE_UNUSABLE);
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

	private void retryOrFail(long profileId, Journey journey, Disposition disposition)
	{
		if ((_journeys.get(profileId) != journey) || (_attempts.get(profileId) != journey.attempt)) { return; }
		clearRoute(profileId, journey);
		_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		if (++journey.attempt.failures >= 3)
		{
			if (disposition == Disposition.TRANSIENT_SERVICE)
			{
				// Keep the existing three-attempt bound without converting service load into bad geometry.
				journey.attempt.transientExhausted = true;
			}
			else { fail(profileId, journey, journey.reason, disposition); }
		}
		else
		{
			journey.attempt.retryAtNanos = _clock.getAsLong() + 1_000_000_000L;
		}
	}

	private Attempt attempt(long profileId, PhantomGoal goal, Player player, long epoch)
	{
		final Attempt previous = _attempts.get(profileId);
		if ((previous != null) && previous.matches(goal, player, epoch)) { return previous; }
		final var owner = player.getNativeWorkOwner();
		final var evidence = owner == null ? null : owner.evidence();
		synchronized (_attempts)
		{
			final Attempt current = _attempts.get(profileId);
			if ((current != null) && current.matches(goal, player, epoch)) { return current; }
			if ((current == null) && (_attempts.size() >= _navigation.policy().maximumTrackedProfiles()))
			{
				rememberFailure(profileId, "travel.attempt_capacity");
				return null;
			}
			clearRoutePhase(current);
			final var created = new Attempt(goal, player, epoch, _clock.getAsLong(), owner, evidence);
			_attempts.put(profileId, created);
			_terminalReasons.remove(profileId);
			return created;
		}
	}

	private void fail(long profileId, Journey journey, String reason, Disposition disposition)
	{
		if (!remove(profileId, journey)) { return; }
		final String witness = journey.step.id().startsWith("live.approach.") && (journey.arrivalPoint != null) ? localRouteWitness(journey.step.toAnchorId(), journey.arrivalPoint) : journey.step.id();
		fail(profileId, journey.attempt, witness, reason, disposition);
	}

	private static String localRouteWitness(String anchorId, PhantomNavigationPoint point)
	{
		return "live.approach." + anchorId + "@" + point.x() + ":" + point.y() + ":" + point.z();
	}

	private void fail(long profileId, Attempt attempt, String stepId, String reason, Disposition disposition)
	{
		synchronized (_attempts)
		{
			if ((_attempts.get(profileId) != attempt) || attempt.terminal) { return; }
			attempt.terminal = true;
			clearRoutePhase(attempt);
			rememberFailure(profileId, reason, disposition, attempt);
		}
		_failure.accept(profileId, new Failure(profileId, attempt.player, attempt.epoch, attempt.goal, stepId, reason, disposition, attempt.startedNanos, attempt.failures));
	}

	private synchronized void rememberFailure(long profileId, String reason)
	{
		rememberFailure(profileId, reason, Disposition.STORE_PENDING, _attempts.get(profileId));
	}

	private synchronized void rememberFailure(long profileId, String reason, Disposition disposition, Attempt attempt)
	{
		if (!_terminalReasons.containsKey(profileId) && (_terminalReasons.size() >= _navigation.policy().maximumTrackedProfiles()))
		{
			final var iterator = _terminalReasons.keySet().iterator();
			if (iterator.hasNext()) { _terminalReasons.remove(iterator.next()); }
		}
		_terminalReasons.put(profileId, new TerminalFailure(reason, _failureSequence.incrementAndGet(), disposition, attempt == null ? 0 : attempt.goal.goalId(), attempt == null ? 0 : attempt.goal.revision(), attempt == null ? 0 : attempt.player.getObjectId(), attempt == null ? 0 : attempt.epoch, attempt == null ? "" : attempt.segmentWitness));
	}

	public enum Disposition { ROUTE_UNUSABLE, TRANSIENT_SERVICE, NATIVE_ACTION_REJECTED, STORE_PENDING, PROTOCOL_VIOLATION }
	public record TerminalFailure(String reason, long sequence, Disposition disposition, long goalId, long revision, int objectId, long epoch, String segmentWitness)
	{
		public TerminalFailure(String reason, long sequence, Disposition disposition, long goalId, long revision, int objectId, long epoch) { this(reason, sequence, disposition, goalId, revision, objectId, epoch, ""); }
	}

	public TerminalFailure lastFailure(long profileId) { return _terminalReasons.get(profileId); }

	public enum ArrivalKind { ARRIVED, PENDING, STALE_GOAL, TERMINAL }
	public record ArrivalResult(ArrivalKind kind, long goalId, long revision, int objectId, long epoch, long failureSequence, String reason) { }

	/** Observe the existing Attempt without resetting its terminal receipt. */
	public ArrivalResult observeArrival(long profileId, PhantomGoal goal)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		final int objectId = lifetime == null ? 0 : lifetime.characterObjectId();
		final long epoch = lifetime == null ? 0 : lifetime.materializedAtNanos();
		if (!currentGoal(profileId, goal))
		{
			discardStale(profileId, goal);
			return new ArrivalResult(ArrivalKind.STALE_GOAL, goal.goalId(), goal.revision(), objectId, epoch, 0, "travel.stale_goal");
		}
		final var terminalAttempt = _attempts.get(profileId);
		final var terminalFailure = lastFailure(profileId);
		if ((terminalAttempt != null) && terminalAttempt.terminal && (terminalFailure != null) && (terminalFailure.goalId() == goal.goalId()) && (terminalFailure.revision() == goal.revision()) && (terminalFailure.objectId() == objectId) && (terminalFailure.epoch() == epoch))
		{
			return new ArrivalResult(ArrivalKind.TERMINAL, goal.goalId(), goal.revision(), objectId, epoch, terminalFailure.sequence(), terminalFailure.reason());
		}
		final boolean arrived = arrive(profileId, goal);
		if (!currentGoal(profileId, goal))
		{
			discardStale(profileId, goal);
			return new ArrivalResult(ArrivalKind.STALE_GOAL, goal.goalId(), goal.revision(), objectId, epoch, 0, "travel.stale_goal");
		}
		final var failure = lastFailure(profileId);
		final var attempt = _attempts.get(profileId);
		final boolean terminal = (attempt != null) && attempt.terminal && (failure != null) && (failure.goalId() == goal.goalId()) && (failure.revision() == goal.revision()) && (failure.objectId() == objectId) && (failure.epoch() == epoch);
		return new ArrivalResult(arrived ? ArrivalKind.ARRIVED : terminal ? ArrivalKind.TERMINAL : ArrivalKind.PENDING, goal.goalId(), goal.revision(), objectId, epoch, terminal ? failure.sequence() : 0, terminal ? failure.reason() : reason(profileId));
	}

	private boolean currentGoal(long profileId, PhantomGoal goal)
	{
		return Objects.equals(_background.ordinaryGoal(profileId).orElse(null), goal);
	}

	private boolean currentJourney(long profileId, Journey journey)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		return (lifetime != null) && (lifetime.characterObjectId() == journey.objectId) && (lifetime.materializedAtNanos() == journey.epoch) && currentGoal(profileId, journey.attempt.goal);
	}

	private void discardStale(long profileId, PhantomGoal goal)
	{
		final var journey = _journeys.get(profileId);
		if ((journey != null) && journey.attempt.goal.equals(goal) && (_materialization.find(profileId).map(value -> (value.characterObjectId() == journey.objectId) && (value.materializedAtNanos() == journey.epoch)).orElse(false))) { remove(profileId, journey); }
	}

	/** Recovery stops only the captured current goal's movement, never a foreign lifetime. */
	public boolean stopForRecovery(long profileId, PhantomGoal goal, int objectId, long epoch)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.characterObjectId() != objectId) || (lifetime.materializedAtNanos() != epoch) || !currentGoal(profileId, goal)) { return false; }
		final var journey = _journeys.get(profileId);
		if (journey == null) { return true; }
		return journey.attempt.goal.equals(goal) && (journey.objectId == objectId) && (journey.epoch == epoch) && remove(profileId, journey);
	}

	public void cancelSuperseded(long profileId, PhantomGoal current)
	{
		final var journey = _journeys.get(profileId);
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((journey != null) && (lifetime != null) && (lifetime.characterObjectId() == journey.objectId) && (lifetime.materializedAtNanos() == journey.epoch)
			&& !journey.goal.equals(current) && currentGoal(profileId, current)) { remove(profileId, journey); }
	}

	public record Failure(long profileId, Player player, long epoch, PhantomGoal goal, String stepId, String reason, Disposition disposition, long startedNanos, int attempts)
	{
		public Failure(PhantomGoal goal, String stepId, String reason)
		{
			this(0, null, 0, goal, stepId, reason, legacyDisposition(reason), 0, 0);
		}

		public boolean routeFailure()
		{
			return disposition == Disposition.ROUTE_UNUSABLE;
		}

		private static Disposition legacyDisposition(String reason)
		{
			return switch (reason)
			{
				case "travel.route_absent", "travel.destination_unproven", "travel.gatekeeper_unproven", "travel.navigation_no_path", "travel.navigation_route_obstructed", "travel.native_progress_stuck", "travel.native_progress_timeout" -> Disposition.ROUTE_UNUSABLE;
				default -> Disposition.PROTOCOL_VIOLATION;
			};
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
		remove(profileId, _journeys.get(profileId));
	}

	private boolean remove(long profileId, Journey journey)
	{
		synchronized (_attempts)
		{
			if ((journey == null) || !_journeys.remove(profileId, journey)) { return false; }
			closeRoutePhase(profileId, journey);
		}
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action != null) && (action.player() == journey.player))
			{
				stopOwnedMove(journey, action.player());
			}
		}
		clearRoute(profileId, journey);
		_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		return true;
	}

	private void publishRoutePhase(long profileId, Journey journey, Player player)
	{
		final Attempt attempt = journey.attempt;
		final var owner = attempt.nativeOwner;
		final var evidence = attempt.evidence;
		if (!journey.moveIssued || !player.isMoving() || !player.hasAI() || (player.getAI().getIntention() != Intention.MOVE_TO)
			|| (player.getXdestination() != journey.moveX) || (player.getYdestination() != journey.moveY)
			|| (player != attempt.player) || (owner == null) || (evidence == null) || (owner.player() != player)
			|| (player.getNativeWorkOwner() != owner) || !owner.isCurrent() || (owner.epoch() != attempt.epoch)
			|| (owner.evidence() != evidence) || !evidence.matches(player.getObjectId(), attempt.epoch)
			|| (World.getInstance().getPlayer(player.getObjectId()) != player)) { return; }
		synchronized (_attempts)
		{
			if ((_journeys.get(profileId) != journey) || (_attempts.get(profileId) != attempt) || attempt.terminal || journey.phaseClosed) { return; }
			evidence.phase(PlayerNativeEvidence.Phase.ROUTE, attempt.startedNanos, attempt.startedNanos + _navigation.policy().maximumAttemptDurationNanos());
		}
	}

	private void closeRoutePhase(long profileId, Journey journey)
	{
		synchronized (_attempts)
		{
			journey.phaseClosed = true;
			final Journey current = _journeys.get(profileId);
			if ((current == null) || (current == journey) || (current.attempt != journey.attempt)) { clearRoutePhase(journey.attempt); }
		}
	}

	private void clearRoutePhase(Attempt attempt)
	{
		synchronized (_attempts)
		{
			if ((attempt != null) && (attempt.evidence != null) && attempt.evidence.matches(attempt.player.getObjectId(), attempt.epoch))
			{
				attempt.evidence.clearPhase(PlayerNativeEvidence.Phase.ROUTE, attempt.startedNanos);
			}
		}
	}

	private void clearAttempt(long profileId)
	{
		synchronized (_attempts)
		{
			clearRoutePhase(_attempts.remove(profileId));
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
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
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
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		stopOwnedMove(_journeys.get(profileId), player);
		remove(profileId);
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
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
		private final Player player;
		private final long epoch;
		private final PhantomNormalGatekeeperTravel.Step step;
		private final PhantomGoal goal;
		private final Attempt attempt;
		private boolean teleported;
		private boolean phaseClosed;
		private boolean moveIssued;
		private int moveX;
		private int moveY;
		private long requestId;
		private long deadline;
		private PhantomNavigationPoint arrivalPoint;
		private List<PhantomNavigationPoint> waypoints;
		private PhantomNavigationRoute route;
		private int trackedIndex = -1;
		private int index;
		private volatile String reason = "travel.started";

		private Journey(PhantomGoal goal, Player player, long epoch, PhantomNormalGatekeeperTravel.Step step, Attempt attempt)
		{
			goalId = goal.goalId();
			revision = goal.revision();
			objectId = player.getObjectId();
			this.player = player;
			this.epoch = epoch;
			this.step = step;
			this.goal = goal;
			this.attempt = attempt;
		}
	}

	private record PendingStore(Player player, long epoch, PhantomGoal goal, String anchorId, Journey journey) { }

	private static final class Attempt
	{
		private final PhantomGoal goal;
		private final Player player;
		private final long epoch;
		private final long startedNanos;
		private final PlayerNativeWork.Owner nativeOwner;
		private final PlayerNativeEvidence evidence;
		private int failures;
		private long retryAtNanos;
		private volatile boolean transientExhausted;
		private volatile boolean terminal;
		private volatile String segmentWitness = "";

		private Attempt(PhantomGoal goal, Player player, long epoch, long startedNanos, PlayerNativeWork.Owner nativeOwner, PlayerNativeEvidence evidence)
		{
			this.goal = goal; this.player = player; this.epoch = epoch; this.startedNanos = startedNanos;
			this.nativeOwner = nativeOwner; this.evidence = evidence;
		}

		private boolean matches(PhantomGoal goal, Player player, long epoch)
		{
			return (this.player == player) && (this.epoch == epoch) && (this.goal.goalId() == goal.goalId()) && (this.goal.revision() == goal.revision());
		}
	}
}
