/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.OwnedWalk;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;

/** The scheduler is the sole intent writer. The route-learning writer persists completed native receipts. */
public final class PhantomPhysicalGeoScout implements PhantomSchedulerControlPort
{
	public record Settings(int count, int sessionHours)
	{
		public Settings
		{
			if (count < 1 || count > 16 || sessionHours < 1 || sessionHours > 24) { throw new IllegalArgumentException("Invalid physical scout settings"); }
		}
	}

	private static final String SOURCE = "travel.physical_scout";
	private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
	private static final long SIGNAL_MILLIS = 15_000;
	private static final long ADMISSION_NANOS = 45 * SECOND;
	private static final long FRONTIER_NANOS = 120 * SECOND;
	private static final long STUCK_NANOS = 6 * SECOND;
	private static final int MAX_LEG = 3000;
	private static final int[][] DIRECTIONS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
	private final String _serverSession = UUID.randomUUID().toString();
	private final Settings _settings;
	private final PhantomScheduler _scheduler;
	private final PhantomMaterializationService _materialization;
	private final PhantomExecutionAdmission _execution;
	private final PhantomNavigationService _navigation;
	private final PhantomPresenceRegistry _presence;
	private final LongFunction<List<Long>> _page;
	private final LongPredicate _eligible;
	private final IntSupplier _spareSlots;
	private final L2jNavigationBackend _geo = new L2jNavigationBackend();
	private final ConcurrentHashMap<Long, Scout> _scouts = new ConcurrentHashMap<>();
	private final java.util.Map<Long, Long> _retry = new java.util.LinkedHashMap<>();
	private final java.util.Map<Long, PhantomNavigationPoint> _resume = new java.util.LinkedHashMap<>();
	private final long _firstSession = System.nanoTime();
	private final long _sessionNanos;
	private volatile boolean _stopped;
	private volatile int _occupied;
	private volatile String _status = "state=WAITING_FOR_SCHEDULER\n";
	private long _nextPulse, _nextAdmission, _sequence, _cursor;
	private long _started, _completed, _verified, _rejected, _stuck, _dead, _replans, _vertical, _crossRegion, _instanceRejected, _journeys, _failures, _lastProgress;
	private double _walked;
	private String _reason = "WAITING_FOR_PROFILES";

	public PhantomPhysicalGeoScout(Settings settings, PhantomScheduler scheduler, PhantomMaterializationService materialization,
		PhantomExecutionAdmission execution, PhantomNavigationService navigation, PhantomPresenceRegistry presence, LongFunction<List<Long>> page,
		LongPredicate eligible, IntSupplier spareSlots)
	{
		_settings = settings;
		_scheduler = scheduler;
		_materialization = materialization;
		_execution = execution;
		_navigation = navigation;
		_presence = presence;
		_page = page;
		_eligible = eligible;
		_spareSlots = spareSlots;
		_sessionNanos = TimeUnit.HOURS.toNanos(settings.sessionHours());
	}

	boolean sessionOpen() { return !_stopped && (System.nanoTime() - _firstSession) % TimeUnit.DAYS.toNanos(1) < _sessionNanos; }
	String status() { return _status; }
	public boolean reserved(long id) { return _scouts.containsKey(id); }
	public Set<Long> reservedProfiles() { return Set.copyOf(_scouts.keySet()); }
	public int occupied() { return _occupied; }
	public boolean releasingCorpse(long id, int objectId, long epoch)
	{
		final var scout = _scouts.get(id);
		return scout != null && scout.storeCorpse && scout.objectId == objectId && scout.epoch == epoch;
	}

	public PhantomPhysicalDemand demand(long id, long now)
	{
		final var scout = _scouts.get(id);
		final long until = scout == null ? 0 : scout.signalUntil;
		return sessionOpen() && until > now && until - now <= SIGNAL_MILLIS
			? new PhantomPhysicalDemand(PhantomPhysicalDemand.Kind.PHYSICAL_SCOUT, id, 0, SOURCE, until - SIGNAL_MILLIS, until) : null;
	}

	@Override
	public synchronized void onPulse()
	{
		final long now = System.nanoTime();
		if (_stopped || now - _nextPulse < 0) { return; }
		_nextPulse = now + SECOND / 2;
		final var memory = _navigation.routeLearning();
		final boolean open = sessionOpen();
		final var overload = _scheduler.snapshot().overloadLevel();
		final boolean capacity = _spareSlots.getAsInt() >= _scouts.size();
		final boolean working = open && overload.ordinal() < PhantomActivityOverloadLevel.HIGH.ordinal() && capacity && memory != null && memory.physicalReady();
		for (var scout : _scouts.values())
		{
			try
			{
				if (working && !scout.releasing) { advance(scout, memory, now); }
				else { release(scout, memory, now, open ? "PAUSED" : "SESSION_EXPIRED"); }
			}
			catch (RuntimeException ex)
			{
				release(scout, memory, now, "SCOUT_ERROR_" + ex.getClass().getSimpleName());
			}
		}
		if (working && now - _nextAdmission >= 0 && _scouts.size() < Math.min(_settings.count(), _spareSlots.getAsInt()))
		{
			_nextAdmission = now + 3 * SECOND;
			_reason = "NO_ELIGIBLE_FREE_LINKED_PROFILE";
			final var page = _page.apply(_cursor);
			if (page.isEmpty()) { _cursor = 0; }
			for (long id : page)
			{
				_cursor = id;
				if (_retry.getOrDefault(id, 0L) > now || reserved(id) || _materialization.find(id).isPresent()
					|| !"none".equals(_presence.busyReason(id)) || !_eligible.test(id) || !_presence.claimBusy(id, SOURCE)) { continue; }
				final var scout = new Scout(id, ++_sequence, now);
				scout.frontier = _resume.remove(id);
				_scouts.put(id, scout);
				if (renew(scout)) { _reason = "ADMISSION_REQUESTED"; }
				else { release(scout, memory, now, "SCHEDULER_ADMISSION_REJECTED"); }
				break;
			}
		}
		if (!open) { _reason = "WAITING_FOR_NEXT_SESSION"; }
		else if (memory == null || !memory.physicalReady()) { _reason = "LEARNED_WRITER_UNAVAILABLE"; }
		else if (!capacity || _spareSlots.getAsInt() == 0) { _reason = "NO_SPARE_PHYSICAL_CAPACITY"; }
		else if (!working) { _reason = "GAMEPLAY_LOAD_HIGH"; }
		report(memory);
	}

	private boolean renew(Scout scout)
	{
		scout.signalUntil = System.currentTimeMillis() + SIGNAL_MILLIS;
		final var result = _scheduler.submitSignal(scout.id, new PhantomRelevanceSignal(SOURCE, ++_sequence, PhantomActivityState.ACTIVE, SIGNAL_MILLIS));
		return result.status() == SignalStatus.ACCEPTED || result.status() == SignalStatus.COALESCED;
	}

	private void advance(Scout scout, PhantomRouteLearning memory, long now)
	{
		final var entry = _materialization.find(scout.id).orElse(null);
		final var player = entry == null || !entry.worldPresent() ? null : World.getInstance().getPlayer(entry.characterObjectId());
		if (!("internal." + SOURCE).equals(_presence.busyReason(scout.id))
			|| now >= scout.recheckAt && !_eligible.test(scout.id))
		{
			release(scout, memory, now, "OWNERSHIP_OR_INTENT_CHANGED");
			return;
		}
		if (now >= scout.recheckAt) { scout.recheckAt = now + 3 * SECOND; }
		if (scout.signalUntil - System.currentTimeMillis() < SIGNAL_MILLIS / 2 && !renew(scout))
		{
			release(scout, memory, now, "SIGNAL_REJECTED");
			return;
		}
		if (player == null)
		{
			if (scout.epoch != 0 || now >= scout.admittedAt + ADMISSION_NANOS) { release(scout, memory, now, "MATERIALIZATION_UNAVAILABLE"); }
			return;
		}
		if (scout.epoch == 0)
		{
			scout.epoch = entry.materializedAtNanos();
			scout.objectId = entry.characterObjectId();
		}
		if (player.isAlikeDead()) { _dead++; release(scout, memory, now, "DIED"); return; }
		if (player.getInstanceId() != 0) { _instanceRejected++; release(scout, memory, now, "INSTANCE_ENTRY_UNSUPPORTED"); return; }
		if (scout.epoch != entry.materializedAtNanos() || !actorEligible(player))
		{
			release(scout, memory, now, "NATIVE_ACTOR_NOT_ELIGIBLE");
			return;
		}
		if (now < scout.retryAt) { return; }
		if (scout.lastPoint == null && now >= scout.admittedAt + ADMISSION_NANOS)
		{
			release(scout, memory, now, "NATIVE_ACTION_ADMISSION_TIMEOUT");
			return;
		}
		try (var execution = _execution.tryAcquireExclusiveIdle(scout.id, PhantomExecutionHandoff.solo(scout.id)))
		{
			if (execution == null) { return; }
			try (var lease = _materialization.tryAcquireAction(scout.id, execution.stamp()).orElse(null))
			{
				if (lease == null || lease.player() != player || !lease.executionValid()) { return; }
				final var current = PhantomNativeTravelActions.point(player);
				scout.lastPoint = current;
				if (!owned(scout, player)) { release(scout, memory, now, "STALE_NATIVE_ACTOR"); return; }
				if (scout.leg != null) { observe(scout, memory, player, lease, current, now); return; }
				if (player.isMoving() || player.hasAI() && player.getAI().getIntention() != Intention.IDLE
					&& player.getAI().getIntention() != Intention.ACTIVE && player.getAI().getIntention() != Intention.MOVE_TO)
				{
					release(scout, memory, now, "OTHER_NATIVE_ACTION");
					return;
				}
				if (scout.frontier != null && now >= scout.frontierDeadline && scout.frontierDeadline != 0)
				{
					rejectFrontier(scout, memory, now, "FRONTIER_DEADLINE");
					return;
				}
				if (scout.request != 0)
				{
					final var result = _navigation.consume(scout.request).orElse(null);
					if (result == null && _navigation.find(scout.request).isPresent()) { return; }
					scout.request = 0;
					if (result != null && result.route() != null && result.route().origin().equals(current)
						&& result.status() != PhantomNavigationResult.Status.DIRECT_UNVERIFIED_NO_GEODATA)
					{
						scout.waypoints.addAll(result.route().waypoints());
					}
					else if (result != null && result.status() != PhantomNavigationResult.Status.CANCELLED)
					{
						failPlan(scout, memory, now, result.status().name());
						return;
					}
				}
				if (scout.frontier == null) { selectFrontier(scout, memory, current, now); }
				else if (scout.frontierDeadline == 0) { scout.frontierDeadline = now + FRONTIER_NANOS; }
				if (scout.frontier == null) { scout.retryAt = now + 2 * SECOND; return; }
				if (near(current, scout.frontier))
				{
					scout.waypoints.clear();
					if (scout.candidate != null && scout.approaching)
					{
						scout.approaching = false;
						scout.frontier = scout.candidate.to();
					}
					else
					{
						_journeys++;
						if (scout.candidate != null && !scout.candidateWitnessed) { rejectFrontier(scout, memory, now, "DETOUR_ARRIVED_CANDIDATE_UNCONFIRMED"); }
						else { clearFrontier(scout); }
						return;
					}
				}
				while (!scout.waypoints.isEmpty() && scout.waypoints.peek().equals(current)) { scout.waypoints.removeFirst(); }
				if (scout.waypoints.isEmpty())
				{
					plan(scout, player, current, now);
					return;
				}
				issue(scout, memory, player, lease, current, now);
			}
		}
	}

	private void selectFrontier(Scout scout, PhantomRouteLearning memory, PhantomNavigationPoint from, long now)
	{
		scout.candidate = memory.scoutFrontier(from, System.currentTimeMillis(), edge -> !scout.recent.contains(edge.to())
			&& _scouts.values().stream().noneMatch(other -> other != scout && edge.equals(other.candidate)));
		if (scout.candidate != null)
		{
			scout.approaching = !near(from, scout.candidate.from());
			scout.frontier = scout.approaching ? scout.candidate.from() : scout.candidate.to();
		}
		else
		{
			// New frontiers are local, include side/back directions and project on the current floor first.
			for (int attempt = 0; attempt < DIRECTIONS.length; attempt++)
			{
				final int index = scout.direction++;
				final var direction = DIRECTIONS[Math.floorMod(index, DIRECTIONS.length)];
				final int span = 700 + 200 * Math.floorMod(index / DIRECTIONS.length, 4);
				final int x = from.x() + direction[0] * span, y = from.y() + direction[1] * span;
				if (x < World.WORLD_X_MIN || x > World.WORLD_X_MAX || y < World.WORLD_Y_MIN || y > World.WORLD_Y_MAX) { continue; }
				final var target = _geo.learnedSurface(new PhantomNavigationPoint(x, y, from.z(), 0), 384);
				if (target != null && !PhantomWalkCorridorPlanner.nearVisited(target, scout.recent)) { scout.frontier = target; break; }
			}
			if (scout.frontier == null) { useBacktrack(scout, from); }
		}
		scout.frontierDeadline = now + FRONTIER_NANOS;
	}

	private void plan(Scout scout, Player player, PhantomNavigationPoint from, long now)
	{
		if (!_navigation.canProbeGeo()) { return; }
		final var monsters = monsters(player);
		if (monsters.size() > 64) { scout.retryAt = now + 5 * SECOND; _reason = "LOCAL_THREAT_BUDGET"; return; }
		final var request = new PhantomNavigationRequest(scout.id, from, scout.frontier, now, now + _navigation.policy().defaultRequestDeadlineNanos(), 9000,
			PhantomVisibleFarmTravel.pveThreats(from, player.getLevel(), monsters));
		if (scout.planAttempts++ >= 10) { scout.frontierDeadline = now; return; }
		final var result = _navigation.submitScoutWalk(request, scout.backtracking ? Set.of() : Set.copyOf(scout.recent), Set.copyOf(scout.blocked));
		if (result.status() == PhantomNavigationService.SubmissionStatus.ACCEPTED) { scout.request = result.requestId(); }
		else if (result.immediateResult() != null && result.immediateResult().route() != null
			&& result.immediateResult().status() == PhantomNavigationResult.Status.DIRECT_VALIDATED)
		{
			scout.waypoints.addAll(result.immediateResult().route().waypoints());
			_navigation.consume(result.requestId());
		}
		else { scout.retryAt = now + SECOND; }
	}

	private void issue(Scout scout, PhantomRouteLearning memory, Player player, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease lease,
		PhantomNavigationPoint from, long now)
	{
		final var waypoint = scout.waypoints.peek();
		final var to = waypointWithinLeg(from, waypoint);
		if (to == null || from.x() == to.x() && from.y() == to.y() || !_geo.canWalkDirect(from, to) || !safe(player, to))
		{
			remember(scout.blocked, new Edge(from, waypoint), 16);
			witness(scout, memory, player, null, from, waypoint, from, System.currentTimeMillis(), now, now, ++_sequence, 0, "BLOCKED_BEFORE_ISSUE");
			failPlan(scout, memory, now, "BLOCKED_BEFORE_ISSUE");
			return;
		}
		if (!_eligible.test(scout.id) || !lease.executionValid()) { release(scout, memory, now, "INTENT_CHANGED_BEFORE_WALK"); return; }
		player.setWalking();
		player.getAI().setIntention(Intention.MOVE_TO, new Location(to.x(), to.y(), to.z()));
		final var movement = PhantomNativeTravelActions.captureWalk(player);
		final var stamp = lease.executionStamp();
		final var receipt = movement == null ? null : player.nativeStraightMoveArrival(to.x(), to.y(), to.z());
		final long legId = ++_sequence;
		final long wallClock = System.currentTimeMillis();
		if (movement == null || !movement.destination().equals(to) || receipt == null || stamp == null)
		{
			PhantomNativeTravelActions.cancelWalk(movement);
			witness(scout, memory, player, null, from, movement == null ? null : movement.destination(), from, wallClock, now, now, legId, 0, "NATIVE_MOVE_REJECTED");
			failPlan(scout, memory, now, "NATIVE_MOVE_REJECTED");
			return;
		}
		_started++;
		scout.leg = new Leg(movement, new Arrival(new Edge(from, to), receipt), stamp, legId, wallClock, now,
			now + TimeUnit.SECONDS.toNanos(Math.clamp((long) Math.ceil(from.distanceTo(to) / Math.max(1, player.getMoveSpeed())) + 8, 10, 65)), from);
		if (to.equals(waypoint)) { scout.waypoints.removeFirst(); }
	}

	private PhantomNavigationPoint waypointWithinLeg(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final double distance = from.distanceTo(to);
		if (distance <= MAX_LEG) { return to; }
		final double ratio = 1800 / distance;
		final int x = from.x() + (int) ((to.x() - from.x()) * ratio);
		final int y = from.y() + (int) ((to.y() - from.y()) * ratio);
		return _geo.learnedSurface(new PhantomNavigationPoint(x, y, from.z(), 0), 384);
	}

	private void observe(Scout scout, PhantomRouteLearning memory, Player player, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease lease,
		PhantomNavigationPoint current, long now)
	{
		final var leg = scout.leg;
		final var edge = leg.arrival.edge;
		final boolean sameOwner = player == leg.walk.player() && player.getNativeWorkOwner() == leg.walk.owner() && owned(scout, player)
			&& leg.stamp.equals(lease.executionStamp()) && lease.executionValid();
		final boolean currentWalk = sameOwner && !player.isRunning() && leg.walk.equals(PhantomNativeTravelActions.captureWalk(player));
		if (currentWalk && !current.equals(edge.to()) && current.distanceTo(leg.previous) >= 8)
		{
			leg.previous = current;
			leg.lastChange = now;
			leg.samples++;
			_lastProgress = System.currentTimeMillis();
		}
		if (sameOwner && leg.arrival.consume(sessionOpen() && !scout.releasing && !player.isRunning(), leg.samples, current))
		{
			scout.leg = null;
			if (!_eligible.test(scout.id) || !_geo.canWalkDirect(edge.from(), edge.to()))
			{
				witness(scout, memory, player, null, edge.from(), edge.to(), current, leg.wallClock, leg.started, now, leg.id, leg.samples, "ARRIVAL_INVALIDATED");
				failPlan(scout, memory, now, "ARRIVAL_INVALIDATED");
				return;
			}
			_completed++;
			scout.deadEnds = 0;
			_walked += edge.from().distanceTo(edge.to());
			_lastProgress = System.currentTimeMillis();
			if (Math.abs(edge.from().z() - edge.to().z()) >= 40) { _vertical++; }
			if (edge.from().x() >> 15 != edge.to().x() >> 15 || edge.from().y() >> 15 != edge.to().y() >> 15) { _crossRegion++; }
			final boolean saved = witness(scout, memory, player, edge, edge.from(), edge.to(), current, leg.wallClock, leg.started, now, leg.id, leg.samples, "ARRIVED");
			if (saved && edge.equals(scout.candidate)) { _verified++; scout.candidateWitnessed = true; }
			if (!saved) { release(scout, memory, now, "WITNESS_UNAVAILABLE"); return; }
			remember(scout.recent, edge.from(), 32);
			if (!scout.backtracking)
			{
				if (scout.trail.size() >= 32) { scout.trail.removeFirst(); }
				scout.trail.addLast(edge.from());
			}
			return;
		}
		final boolean stopped = !currentWalk;
		if (stopped || now >= leg.deadline || now - leg.lastChange >= STUCK_NANOS || !safe(player, edge.to()))
		{
			final String reason = stopped ? "MOVE_INTERRUPTED" : now >= leg.deadline ? "MOVE_DEADLINE" : now - leg.lastChange >= STUCK_NANOS ? "STUCK" : "DANGEROUS_MONSTER";
			if ("STUCK".equals(reason)) { _stuck++; }
			cancelLeg(scout, memory, now, reason);
			remember(scout.blocked, edge, 16);
			if (sameOwner) { memory.failed(edge, PhantomRouteLearning.Failure.TRANSIENT, System.currentTimeMillis()); }
			failPlan(scout, memory, now, reason);
		}
	}

	private boolean owned(Scout scout, Player player)
	{
		final var owner = player.getNativeWorkOwner();
		return _scouts.get(scout.id) == scout && owner != null && owner.player() == player && owner.epoch() == scout.epoch && owner.isCurrent()
			&& !owner.sealed() && World.getInstance().getPlayer(player.getObjectId()) == player && actorEligible(player);
	}

	private static boolean actorEligible(Player player)
	{
		return PhantomNativeTravelActions.ordinary(player) && player.hasHeadlessOutboundSession() && player.getClient() == null
			&& player.isOnline() && !player.hasPendingOwnedStore() && !player.isTeleporting() && !player.isMovementDisabled() && !player.isMounted()
			&& !player.isInBoat() && !player.isInAirShip() && !player.isInsideZone(ZoneId.WATER);
	}

	private static List<Monster> monsters(Player player)
	{
		return World.getInstance().getVisibleObjectsInRange(player, Monster.class, 3500).stream().limit(65).toList();
	}

	private static boolean safe(Player player, PhantomNavigationPoint to)
	{
		final var monsters = monsters(player);
		return monsters.size() <= 64 && PhantomNativeTravelActions.safePveWalkSegment(player, to.x(), to.y(), to.z(), monsters, Set.of());
	}

	private static boolean near(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return from.instanceId() == to.instanceId() && from.distanceTo(to) <= 24;
	}

	private void failPlan(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		_replans++;
		_reason = reason;
		scout.waypoints.clear();
		scout.retryAt = now + 2 * SECOND;
		if (++scout.planFailures >= 3) { rejectFrontier(scout, memory, now, reason); }
	}

	private void rejectFrontier(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		if (scout.candidate != null)
		{
			memory.failed(scout.candidate, PhantomRouteLearning.Failure.TRANSIENT, System.currentTimeMillis());
			_rejected++;
		}
		if (scout.frontier != null) { remember(scout.recent, scout.frontier, 32); }
		_failures++;
		_reason = reason;
		cancelRequest(scout);
		clearFrontier(scout);
		if (++scout.deadEnds >= 2 && scout.lastPoint != null) { useBacktrack(scout, scout.lastPoint); }
		scout.retryAt = now + 3 * SECOND;
		if (scout.deadEnds >= 6) { release(scout, memory, now, "FRONTIER_BUDGET_EXHAUSTED"); }
	}

	private static <T> void remember(LinkedHashSet<T> values, T value, int limit)
	{
		if (values.size() >= limit) { values.removeFirst(); }
		values.add(value);
	}

	private static void useBacktrack(Scout scout, PhantomNavigationPoint from)
	{
		while (!scout.trail.isEmpty())
		{
			final var target = scout.trail.removeLast();
			if (near(from, target)) { continue; }
			scout.frontier = target;
			scout.backtracking = true;
			return;
		}
	}

	private static void clearFrontier(Scout scout)
	{
		scout.frontier = null;
		scout.candidate = null;
		scout.candidateWitnessed = false;
		scout.backtracking = false;
		scout.frontierDeadline = 0;
		scout.planAttempts = 0;
		scout.planFailures = 0;
		scout.waypoints.clear();
	}

	private void cancelRequest(Scout scout)
	{
		if (scout.request == 0) { return; }
		_navigation.cancel(scout.id, scout.request);
		_navigation.consume(scout.request);
		scout.request = 0;
	}

	private void cancelLeg(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		final var leg = scout.leg;
		if (leg == null) { return; }
		final var player = leg.walk.player();
		final var existing = org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(leg.walk.owner());
		if (existing != null) { PhantomNativeTravelActions.cancelWalk(leg.walk); }
		else
		{
			try (var completion = _materialization.tryAcquireCompletionAction(scout.id, leg.stamp).orElse(null))
			{
				if (completion == null && player.isMoving() && leg.walk.equals(PhantomNativeTravelActions.captureWalk(player))) { return; }
				if (completion != null && completion.player() == player) { PhantomNativeTravelActions.cancelWalk(leg.walk); }
			}
		}
		scout.leg = null;
		witness(scout, memory, player, null, leg.arrival.edge.from(), leg.walk.destination(), PhantomNativeTravelActions.point(player), leg.wallClock, leg.started, now, leg.id, leg.samples, reason);
	}

	private void release(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		if (!scout.releasing)
		{
			scout.releasing = true;
			scout.died = "DIED".equals(reason);
			scout.yieldToOrdinary = switch (reason)
			{
				case "OWNERSHIP_OR_INTENT_CHANGED", "INTENT_CHANGED_BEFORE_WALK", "OTHER_NATIVE_ACTION", "NATIVE_ACTOR_NOT_ELIGIBLE", "INSTANCE_ENTRY_UNSUPPORTED" -> true;
				default -> false;
			};
			scout.releaseReason = reason;
			scout.signalUntil = 0;
			_reason = reason;
			if ("SESSION_EXPIRED".equals(reason) && scout.frontier != null)
			{
				if (_resume.size() >= 16) { _resume.remove(_resume.keySet().iterator().next()); }
				_resume.put(scout.id, scout.frontier);
			}
			cancelRequest(scout);
			_scheduler.withdrawSignal(scout.id, SOURCE, ++_sequence);
		}
		cancelLeg(scout, memory, now, scout.releaseReason);
		final var entry = _materialization.find(scout.id).orElse(null);
		// The scheduler may STORE the exact scout corpse after its cancellable WALK is gone.
		if (scout.leg == null && entry != null && entry.worldPresent() && entry.materializedAtNanos() == scout.epoch
			&& entry.characterObjectId() == scout.objectId)
		{
			final var actor = World.getInstance().getPlayer(scout.objectId);
			if (actor != null && actor.isDead())
			{
				if (!scout.died) { _dead++; }
				scout.died = true;
				scout.storeCorpse = true;
			}
		}
		// Keep ordinary work fenced while this actor's scheduled STORE is pending.
		if (entry != null && !_stopped && (!scout.yieldToOrdinary || scout.died || scout.leg != null)) { return; }
		if (scout.leg != null && !_stopped) { return; }
		_presence.releaseBusy(scout.id, SOURCE);
		_scouts.remove(scout.id, scout);
		if (_retry.size() >= 128) { _retry.remove(_retry.keySet().iterator().next()); }
		_retry.put(scout.id, now + (scout.died ? TimeUnit.HOURS.toNanos(1) : 180 * SECOND));
	}

	public synchronized void beginStop()
	{
		if (_stopped) { return; }
		_stopped = true;
		final var memory = _navigation.routeLearning();
		final long now = System.nanoTime();
		for (var scout : _scouts.values()) { release(scout, memory, now, "SERVER_STOP"); }
		_reason = "SERVER_STOP";
		report(memory);
	}

	private boolean witness(Scout scout, PhantomRouteLearning memory, Player player, Edge arrived,
		PhantomNavigationPoint from, PhantomNavigationPoint issued, PhantomNavigationPoint actual,
		long startedMillis, long started, long now, long legId, int samples, String result)
	{
		return memory != null && memory.recordPhysical(arrived, String.join("\t", _serverSession, Long.toString(scout.id), Integer.toString(player.getObjectId()),
			player.getName().replaceAll("[^A-Za-z0-9_-]", "_"), Long.toString(scout.epoch), Long.toString(scout.intent), Long.toString(legId),
			Long.toString(startedMillis), Long.toString(System.currentTimeMillis()), Long.toString(TimeUnit.NANOSECONDS.toMillis(now - started)),
			encode(from), encode(issued), encode(actual), encode(scout.frontier), Integer.toString(samples), result));
	}

	private static String encode(PhantomNavigationPoint point)
	{
		return point == null ? "-" : point.x() + "," + point.y() + "," + point.z() + "," + point.instanceId();
	}

	private void report(PhantomRouteLearning memory)
	{
		final long active = _scouts.values().stream().filter(scout -> !scout.releasing && scout.epoch != 0 && scout.lastPoint != null).count();
		final long moving = _scouts.values().stream().filter(scout -> !scout.releasing && scout.leg != null).count();
		_occupied = (int) _scouts.keySet().stream().filter(id -> _materialization.find(id).map(entry -> entry.worldPresent()).orElse(false)).count();
		final boolean proven = memory != null && memory.physicalNewProven() > 0;
		final String state = _stopped ? "STOPPED" : !sessionOpen() ? "SESSION_CLOSED" : moving > 0 && proven ? "PHYSICAL_SCOUTS_RUNNING"
			: moving > 0 ? "MOVING_AWAITING_PHYSICAL_PROOF" : active > 0 ? "PHYSICAL_SCOUTS_IDLE" : "WAITING_FOR_ADMISSION";
		final var output = new StringBuilder().append("state=").append(state).append("\nreason=").append(_reason)
			.append("\nrequested=").append(_settings.count()).append("\nactive=").append(active).append("\nmaterialized=").append(occupied()).append("\nmoving=").append(moving)
			.append("\nidle=").append(active - moving).append("\nstuck=").append(_stuck).append("\ndead=").append(_dead).append("\nwalked_units=").append(Math.round(_walked))
			.append("\nnative_legs_started=").append(_started).append("\nnative_legs_completed=").append(_completed)
			.append("\nnew_proven=").append(memory == null ? 0 : memory.physicalNewProven()).append("\ncandidates_verified=").append(_verified).append("\ncandidates_rejected=").append(_rejected)
			.append("\nreplans=").append(_replans).append("\nvertical_legs=").append(_vertical).append("\nregion_transitions=").append(_crossRegion)
			.append("\nphysical_frontiers_completed=").append(_journeys).append("\nfailures=").append(_failures)
			.append("\nbridge_status=UNCONFIRMED")
			.append("\ninstance_scope=world0;INSTANCE_ENTRY_UNSUPPORTED\ninstance_rejected=").append(_instanceRejected)
			.append("\nsession_hours=").append(_settings.sessionHours()).append("\nlast_progress=").append(_lastProgress == 0 ? "-" : Instant.ofEpochMilli(_lastProgress)).append('\n');
		for (var scout : _scouts.values())
		{
			output.append("scout=").append(scout.id).append(" epoch=").append(scout.epoch).append(" position=").append(encode(scout.lastPoint))
				.append(" frontier=").append(encode(scout.frontier)).append(" releasing=").append(scout.releasing).append('\n');
		}
		_status = output.toString();
	}

	static final class Arrival
	{
		final Edge edge;
		private final BooleanSupplier _receipt;
		private boolean _consumed;
		Arrival(Edge edge, BooleanSupplier receipt) { this.edge = edge; _receipt = receipt; }
		boolean consume(boolean currentOwner, int progressSamples, PhantomNavigationPoint actual)
		{
			if (_consumed || !currentOwner || progressSamples == 0 || edge.from().instanceId() != 0 || !edge.to().equals(actual) || actual.instanceId() != 0
				|| edge.from().x() == actual.x() && edge.from().y() == actual.y() || !_receipt.getAsBoolean()) { return false; }
			_consumed = true;
			return true;
		}
	}

	private static final class Scout
	{
		final long id, intent, admittedAt;
		volatile long signalUntil;
		volatile boolean storeCorpse;
		long epoch, recheckAt, retryAt, frontierDeadline, request;
		int objectId, direction, planAttempts, planFailures, deadEnds;
		boolean approaching, candidateWitnessed, backtracking, releasing, died, yieldToOrdinary;
		String releaseReason;
		Edge candidate;
		PhantomNavigationPoint frontier, lastPoint;
		final LinkedHashSet<PhantomNavigationPoint> recent = new LinkedHashSet<>();
		final LinkedHashSet<Edge> blocked = new LinkedHashSet<>();
		final ArrayDeque<PhantomNavigationPoint> waypoints = new ArrayDeque<>(), trail = new ArrayDeque<>();
		Leg leg;
		Scout(long id, long intent, long now) { this.id = id; this.intent = intent; admittedAt = now; direction = (int) (id % DIRECTIONS.length); }
	}

	private static final class Leg
	{
		final OwnedWalk walk;
		final Arrival arrival;
		final PhantomExecutionAdmission.Stamp stamp;
		final long id, wallClock, started, deadline;
		PhantomNavigationPoint previous;
		long lastChange;
		int samples;
		Leg(OwnedWalk walk, Arrival arrival, PhantomExecutionAdmission.Stamp stamp, long id, long wallClock, long started, long deadline, PhantomNavigationPoint previous)
		{
			this.walk = walk;
			this.arrival = arrival;
			this.stamp = stamp;
			this.id = id;
			this.wallClock = wallClock;
			this.started = started;
			this.deadline = deadline;
			this.previous = previous;
			lastChange = started;
		}
	}
}
