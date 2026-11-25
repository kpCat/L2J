/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.OwnedWalk;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomDedicatedScoutRoster;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.taskmanagers.MovementTaskManager;

/** The scheduler is the sole intent writer. The route-learning writer persists completed native receipts. */
public final class PhantomPhysicalGeoScout implements PhantomSchedulerControlPort
{
	public enum Mode { LEGACY, DEDICATED }
	public enum Schedule { NIGHT, ALWAYS }

	public record Settings(int count, int sessionHours, java.time.ZoneId zone, LocalTime start, int commissioningMinutes, Mode mode, Schedule schedule)
	{
		public Settings(int count, int sessionHours, java.time.ZoneId zone, LocalTime start, int commissioningMinutes)
		{
			this(count, sessionHours, zone, start, commissioningMinutes, Mode.LEGACY, Schedule.NIGHT);
		}

		public Settings
		{
			if (count < 1 || count > (mode == Mode.DEDICATED ? 32 : 16) || sessionHours < 1 || sessionHours > 24
				|| zone == null || start == null || mode == null || schedule == null || commissioningMinutes < 0 || commissioningMinutes > 10
				|| mode == Mode.LEGACY && schedule != Schedule.NIGHT) { throw new IllegalArgumentException("Invalid physical scout settings"); }
		}
	}

	static final class SessionSchedule
	{
		private final Settings _settings;
		private volatile Instant _commissioningUntil;

		SessionSchedule(Settings settings) { _settings = settings; }

		void startCommissioning(Instant now)
		{
			if (_commissioningUntil == null && _settings.commissioningMinutes() > 0)
			{
				_commissioningUntil = now.plus(_settings.commissioningMinutes(), ChronoUnit.MINUTES);
			}
		}

		String mode(Instant now)
		{
			if (_settings.schedule() == Schedule.ALWAYS) { return "ALWAYS"; }
			final var commissioningUntil = _commissioningUntil;
			if (commissioningUntil != null && now.isBefore(commissioningUntil)) { return "COMMISSIONING"; }
			final var today = now.atZone(_settings.zone()).toLocalDate().atTime(_settings.start());
			final var start = now.isBefore(today.atZone(_settings.zone()).toInstant()) ? today.minusDays(1) : today;
			return now.isBefore(start.plusHours(_settings.sessionHours()).atZone(_settings.zone()).toInstant()) ? "NIGHT" : "CLOSED";
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
	private final PhantomPopulationManager _population;
	private final LongPredicate _eligible;
	private final LongPredicate _preparationEligible;
	private final IntSupplier _spareSlots;
	private final PhantomPopulationEcologyService _ecology;
	private final PhantomDedicatedScoutRoster _dedicatedRoster;
	private final IntSupplier _ordinaryTarget;
	private final Set<Integer> _bootstrapped = new java.util.HashSet<>();
	private final long[] _slotRetry = new long[33];
	private volatile PhantomDedicatedScoutRegions _regions;
	private FutureTask<DedicatedAttempt> _dedicatedAdmission;
	private Scout _admitting;
	private int _nextSlot = 1;
	private int _walkCursor;
	private long _nextShed;
	private int _pressureSamples;
	private long _stored;
	private boolean _pauseNewWalks;
	private final L2jNavigationBackend _geo = new L2jNavigationBackend();
	private final ConcurrentHashMap<Long, Scout> _scouts = new ConcurrentHashMap<>();
	private final java.util.Map<Long, Long> _retry = new java.util.LinkedHashMap<>();
	private final java.util.Map<Long, PhantomNavigationPoint> _resume = new java.util.LinkedHashMap<>();
	private final SessionSchedule _session;
	private volatile boolean _stopped;
	private volatile int _occupied;
	private volatile String _status = "state=WAITING_FOR_SCHEDULER\n";
	private long _nextPulse, _nextAdmission, _sequence, _cursor;
	private long _selected, _materialized, _started, _completed, _verified, _rejected, _stuck, _dead, _replans, _vertical, _crossRegion, _instanceRejected, _journeys, _failures, _lastProgress;
	private double _walked;
	private String _reason = "WAITING_FOR_PROFILES";
	private String _lastAdmissionWait = "-";

	public PhantomPhysicalGeoScout(Settings settings, PhantomScheduler scheduler, PhantomMaterializationService materialization,
		PhantomExecutionAdmission execution, PhantomNavigationService navigation, PhantomPopulationManager population,
		LongPredicate eligible, LongPredicate preparationEligible, IntSupplier spareSlots, PhantomPopulationEcologyService ecology)
	{
		this(settings, scheduler, materialization, execution, navigation, population, eligible, preparationEligible, spareSlots, ecology, () -> 0);
	}

	public PhantomPhysicalGeoScout(Settings settings, PhantomScheduler scheduler, PhantomMaterializationService materialization,
		PhantomExecutionAdmission execution, PhantomNavigationService navigation, PhantomPopulationManager population,
		LongPredicate eligible, LongPredicate preparationEligible, IntSupplier spareSlots, PhantomPopulationEcologyService ecology, IntSupplier ordinaryTarget)
	{
		_settings = settings;
		_scheduler = scheduler;
		_materialization = materialization;
		_execution = execution;
		_navigation = navigation;
		_population = population;
		_eligible = eligible;
		_preparationEligible = preparationEligible;
		_spareSlots = spareSlots;
		_ecology = ecology;
		_ordinaryTarget = ordinaryTarget;
		_dedicatedRoster = settings.mode() == Mode.DEDICATED ? new PhantomDedicatedScoutRoster() : null;
		_session = new SessionSchedule(settings);
	}

	boolean sessionOpen() { return !_stopped && !"CLOSED".equals(_session.mode(Instant.now())); }
	String status() { return _status; }
	public boolean dedicated() { return _dedicatedRoster != null; }
	void installRegions(PhantomDedicatedScoutRegions regions) { _regions = regions; }
	public Set<Long> reservedProfiles() { return dedicated() ? Set.of() : Set.copyOf(_scouts.keySet()); }
	public int occupied() { return dedicated() ? _materialization.occupiedDedicatedSlots() : _occupied; }
	public boolean releasingCorpse(long id, int objectId, long epoch)
	{
		final var scout = _scouts.get(id);
		return scout != null && scout.storeCorpse && scout.objectId == objectId && scout.epoch == epoch;
	}

	public PhantomPhysicalDemand demand(long id, long now)
	{
		if (dedicated()) { return null; }
		final var scout = _scouts.get(id);
		final long until = scout == null ? 0 : scout.signalUntil;
		return scout != null && sessionOpen() && until > now && until - now <= SIGNAL_MILLIS && _population.scoutReservation(id) == scout.intent
			? new PhantomPhysicalDemand(PhantomPhysicalDemand.Kind.PHYSICAL_SCOUT, id, scout.intent, SOURCE, until - SIGNAL_MILLIS, until) : null;
	}

	@Override
	public synchronized void onPulse()
	{
		final long now = System.nanoTime();
		if (_stopped || now - _nextPulse < 0) { return; }
		_nextPulse = now + SECOND / 2;
		final var memory = _navigation.routeLearning();
		if (dedicated())
		{
			pulseDedicated(memory, now);
			report(memory);
			return;
		}
		if (memory != null && memory.physicalReady()) { _session.startCommissioning(Instant.now()); }
		final boolean open = sessionOpen();
		final var overload = _scheduler.snapshot().overloadLevel();
		final int spare = _spareSlots.getAsInt();
		final boolean capacity = spare >= _settings.count();
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
		if (working && now - _nextAdmission >= 0 && _scouts.size() < _settings.count())
		{
			_nextAdmission = now + 3 * SECOND;
			_reason = "WAITING_FOR_RESERVABLE_LINKED_PROFILE";
			final var page = _population.readyProfileIdsAfter(_cursor, 16);
			if (page.isEmpty()) { _cursor = 0; }
			for (long id : page)
			{
				_cursor = id;
				if (_retry.getOrDefault(id, 0L) > now || _scouts.containsKey(id) || _materialization.ownsProfile(id)
					|| !"none".equals(_population.presence().busyReason(id)) || !_preparationEligible.test(id)) { continue; }
				final long reservation = _population.tryReserveScout(id, _settings.count(), spare);
				if (reservation == 0) { continue; }
				final var scout = new Scout(id, reservation, now);
				scout.frontier = _resume.remove(id);
				_scouts.put(id, scout);
				_selected++;
				if (renew(scout)) { _reason = "ADMISSION_REQUESTED"; }
				else { release(scout, memory, now, "SCHEDULER_ADMISSION_REJECTED"); }
				if (_scouts.size() >= _settings.count()) { break; }
			}
		}
		if (!open) { _reason = "WAITING_FOR_NEXT_SESSION"; }
		else if (memory == null || !memory.physicalReady()) { _reason = "LEARNED_WRITER_UNAVAILABLE"; }
		else if (!capacity) { _reason = "NO_SPARE_PHYSICAL_CAPACITY"; }
		else if (!working) { _reason = "GAMEPLAY_LOAD_HIGH"; }
		report(memory);
	}

	private record DedicatedAttempt(boolean bootstrapped, PhantomMaterializationService.ResultStatus status, String failure) { }

	private void pulseDedicated(PhantomRouteLearning memory, long now)
	{
		finishDedicatedAdmission(memory, now);
		final var regions = _regions;
		final boolean ready = regions != null && memory != null && memory.physicalReady();
		final boolean open = sessionOpen();
		final boolean pressure = dedicatedPressure();
		final boolean ordinaryWaiting = _materialization.ordinaryCapacityPressure();
		final boolean worldFull = _materialization.dedicatedWorldPressure();
		final int count = _scouts.size();
		final int target = Math.min(_settings.count(), _materialization.dedicatedCapacity(_ordinaryTarget.getAsInt()));
		_pauseNewWalks = pressure || ordinaryWaiting || worldFull;
		if (!open) { _reason = "WAITING_FOR_NEXT_SESSION"; }
		else if (!ready) { _reason = "LEARNED_OR_REGIONS_UNAVAILABLE"; }
		else if (pressure) { _reason = "GAMEPLAY_LOAD_HIGH"; }
		else if (ordinaryWaiting) { _reason = "ORDINARY_CAPACITY_PRIORITY"; }
		else if (worldFull) { _reason = "WORLD_CAPACITY"; }
		else if (target < _settings.count()) { _reason = "NO_SPARE_PHYSICAL_CAPACITY"; }

		final boolean shed = (count > target || _pauseNewWalks && count != 0) && now >= _nextShed;
		final var victim = shed ? _scouts.values().stream().filter(scout -> !scout.releasing && scout != _admitting)
			.max(java.util.Comparator.comparingInt(scout -> scout.slot)).orElse(null) : null;
		for (var scout : _scouts.values().stream().sorted(java.util.Comparator.comparingInt(candidate -> Math.floorMod(candidate.slot - _walkCursor, 32))).toList())
		{
			if (scout == _admitting) { continue; }
			try
			{
				if (scout.releasing || !open || !ready || scout == victim)
				{
					release(scout, memory, now, scout.releasing ? scout.releaseReason : _reason);
					if (scout == victim) { _nextShed = now + 5 * SECOND; }
				}
				else if (scout.leg == null && now - scout.assignmentStarted >= 8 * 60 * SECOND)
				{
					release(scout, memory, now, "REGION_ROTATION");
				}
				else { advance(scout, memory, now); }
			}
			catch (RuntimeException failure)
			{
				release(scout, memory, now, "SCOUT_ERROR_" + failure.getClass().getSimpleName());
			}
		}
		_walkCursor = (_walkCursor + 1) % 32;
		if (open && ready && !_pauseNewWalks && _admitting == null && _scouts.size() < target && now >= _nextAdmission)
		{
			_nextAdmission = now + SECOND;
			regions.beginPulse();
			startDedicatedAdmission(regions, now);
		}
	}

	private boolean dedicatedPressure()
	{
		final var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		final var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
		final boolean high = _scheduler.snapshot().overloadLevel().ordinal() >= PhantomActivityOverloadLevel.HIGH.ordinal()
			|| heap.getMax() > 0 && heap.getUsed() > heap.getMax() * 0.90
			|| os instanceof com.sun.management.OperatingSystemMXBean extended && extended.getProcessCpuLoad() > 0.80;
		_pressureSamples = high ? Math.min(6, _pressureSamples + 1) : Math.max(0, _pressureSamples - 1);
		return _pressureSamples >= 4;
	}

	private void startDedicatedAdmission(PhantomDedicatedScoutRegions regions, long now)
	{
		for (int attempt = 0; attempt < _settings.count(); attempt++)
		{
			final int slot = _nextSlot;
			_nextSlot = slot == _settings.count() ? 1 : slot + 1;
			final long key = PhantomMaterializationService.dedicatedScoutKey(slot);
			if (_scouts.containsKey(key) || (_slotRetry[slot] != 0 && now - _slotRetry[slot] < 0)) { continue; }
			final var assignment = regions.claim(key, System.currentTimeMillis());
			if (assignment == null) { _reason = "WAITING_FOR_SAFE_REGION"; return; }
			final var seed = assignment.seed();
			final var scout = new Scout(key, ++_sequence, now);
			scout.slot = slot;
			scout.assignment = assignment;
			scout.assignmentStarted = now;
			scout.admissionStatus = "MATERIALIZING";
			_scouts.put(key, scout);
			_admitting = scout;
			_selected++;
			_dedicatedAdmission = new FutureTask<>(() ->
			{
				boolean bootstrapped = false;
				try
				{
					if (!sessionOpen()) { return new DedicatedAttempt(false, null, "SESSION_EXPIRED"); }
					final var identity = _dedicatedRoster.ensure(slot);
					bootstrapped = true;
					final var result = _materialization.materializeDedicatedScout(_dedicatedRoster, identity, new Location(seed.x(), seed.y(), seed.z()),
						() -> sessionOpen() && _regions == regions && regions.validateSeed(assignment), _ordinaryTarget.getAsInt(), scout.intent);
					return new DedicatedAttempt(true, result.status(), null);
				}
				catch (java.sql.SQLException | RuntimeException failure)
				{
					return new DedicatedAttempt(bootstrapped, null, "ROSTER_" + failure.getClass().getSimpleName());
				}
			});
			try { org.l2jmobius.commons.threads.ThreadPool.executeOrThrow(_dedicatedAdmission); }
			catch (RuntimeException failure) { _dedicatedAdmission.cancel(false); }
			_reason = "ADMISSION_REQUESTED";
			return;
		}
	}

	private void finishDedicatedAdmission(PhantomRouteLearning memory, long now)
	{
		if (_dedicatedAdmission == null || !_dedicatedAdmission.isDone()) { return; }
		final var scout = _admitting;
		try
		{
			final var done = _dedicatedAdmission.get();
			if (done.bootstrapped()) { _bootstrapped.add(scout.slot); }
			if (done.status() == PhantomMaterializationService.ResultStatus.SUCCESS) { return; }
			final String reason = done.status() == null ? done.failure() : done.status().name();
			scout.storeRetained = done.status() == PhantomMaterializationService.ResultStatus.MATERIALIZATION_FAILED_RETAINED;
			release(scout, memory, now, reason);
		}
		catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException failure)
		{
			release(scout, memory, now, "ADMISSION_WORKER_FAILED");
		}
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
		finally { _dedicatedAdmission = null; _admitting = null; }
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
		final boolean scoutLifetime = _materialization.ownsScoutMaterialization(scout.id, scout.intent);
		final var player = !scoutLifetime || entry == null || !entry.worldPresent() ? null : World.getInstance().getPlayer(entry.characterObjectId());
		final var activeLeg = scout.leg;
		final boolean recheck = now >= scout.recheckAt;
		final var activity = !dedicated() && scout.epoch == 0 ? _scheduler.find(scout.id).orElse(null) : null;
		// A temporary historical baseline can also enter World. Only a scheduler promotion starts the scout lifetime.
		final boolean preparing = !dedicated() && scout.epoch == 0 && (player == null || !entry.actionAdmissionOpen() || activity == null
			|| activity.effectiveState() != PhantomActivityState.ACTIVE || activity.transitionStatus() != PhantomActivityTransitionStatus.STABLE);
		if (!dedicated() && (_population.scoutReservation(scout.id) != scout.intent || !("internal." + SOURCE).equals(_population.presence().busyReason(scout.id))
			|| activeLeg == null && (recheck || scout.epoch == 0 && !preparing) && !(preparing ? _preparationEligible : _eligible).test(scout.id)))
		{
			release(scout, memory, now, "OWNERSHIP_OR_INTENT_CHANGED");
			return;
		}
		if (activeLeg == null && recheck) { scout.recheckAt = now + 3 * SECOND; }
		if (!dedicated() && scout.signalUntil - System.currentTimeMillis() < SIGNAL_MILLIS / 2 && !renew(scout))
		{
			release(scout, memory, now, "SIGNAL_REJECTED");
			return;
		}
		if (preparing)
		{
			if (activity != null && "population.scout_yield_native_context".equals(activity.lastTransitionReason()))
			{
				release(scout, memory, now, "HISTORICAL_NATIVE_CONTEXT_REQUIRED");
				return;
			}
			final var due = _ecology == null ? null : _ecology.dueSnapshot(scout.id);
			final var progress = _ecology == null ? null : _ecology.progressSnapshot(scout.id);
			final long cursor = due == null ? 0 : due.committedCursorMinute();
			final long innerCursor = due != null && due.requestPending() ? progress.innerCursorMinute() : 0;
			scout.admissionStatus = "native_owner=" + (entry == null ? "NONE" : scoutLifetime ? "SCOUT" : "HISTORICAL_PREPARATION")
				+ " scheduler=" + (activity == null ? "UNREGISTERED" : activity.requestedState() + "/" + activity.transitionStatus())
				+ " scheduler_reason=" + (activity == null || activity.lastTransitionReason().isEmpty() ? "-" : activity.lastTransitionReason())
				+ " ecology_reason=" + (due == null ? "disabled" : due.reason()) + " committed_cursor=" + cursor
				+ " inner_cursor=" + innerCursor + " requested_horizon=" + (due == null ? 0 : due.requestedHorizonMinute())
				+ " queued=" + (due != null && due.queued()) + " running=" + (due != null && due.running()) + " complete=" + (due == null || due.complete())
				+ " stage=" + (progress == null ? "-" : progress.currentStage()) + " worker=" + (progress == null ? "-" : progress.workerState())
				+ " worker_profile=" + (progress == null ? 0 : progress.activeProfile());
			if (scout.admissionWait.expired(now, Math.max(cursor, innerCursor)))
			{
				release(scout, memory, now, "ADMISSION_NO_PROGRESS");
			}
			return;
		}
		if (player == null)
		{
			release(scout, memory, now, "NATIVE_ACTOR_UNAVAILABLE");
			return;
		}
		if (scout.epoch == 0)
		{
			if (dedicated() && (!scout.assignment.seed().equals(PhantomNativeTravelActions.point(player)) || !player.isDedicatedGeoScout()))
			{
				release(scout, memory, now, "SEED_PLACEMENT_REJECTED");
				return;
			}
			scout.epoch = entry.materializedAtNanos();
			scout.objectId = entry.characterObjectId();
			scout.actionAdmissionStarted = now;
			scout.admissionStatus = "native_owner=SCOUT";
			_materialized++;
			if (dedicated()) { _regions.seeded(scout.assignment); }
		}
		if (player.isAlikeDead()) { _dead++; release(scout, memory, now, "DIED"); return; }
		if (player.getInstanceId() != 0) { _instanceRejected++; release(scout, memory, now, "INSTANCE_ENTRY_UNSUPPORTED"); return; }
		if (scout.epoch != entry.materializedAtNanos() || !actorEligible(player))
		{
			release(scout, memory, now, "NATIVE_ACTOR_NOT_ELIGIBLE");
			return;
		}
		if (dedicated() && !inAssignment(scout, PhantomNativeTravelActions.point(player)))
		{
			release(scout, memory, now, "REGION_ASSIGNMENT_CHANGED");
			return;
		}
		if (now < scout.retryAt || dedicated() && _pauseNewWalks && activeLeg == null) { return; }
		if (scout.lastPoint == null && now - scout.actionAdmissionStarted >= ADMISSION_NANOS)
		{
			release(scout, memory, now, "NATIVE_ACTION_ADMISSION_TIMEOUT");
			return;
		}
		final var execution = activeLeg != null ? activeLeg.execution : dedicated()
			? _materialization.tryAcquireDedicatedScoutExecution(scout.id) : _execution.tryAcquireExclusiveIdle(scout.id, PhantomExecutionHandoff.solo(scout.id));
		if (execution == null) { return; }
		try
		{
			if (activeLeg != null && !execution.valid())
			{
				release(scout, memory, now, "OWNERSHIP_OR_INTENT_CHANGED");
				return;
			}
			try (var lease = _materialization.tryAcquireAction(scout.id, execution.stamp()).orElse(null))
			{
				if (lease == null || lease.player() != player || !lease.executionValid())
				{
					if (activeLeg != null && now >= activeLeg.deadline) { release(scout, memory, now, "MOVE_ACTION_ADMISSION_TIMEOUT"); }
					return;
				}
				final var current = PhantomNativeTravelActions.point(player);
				scout.lastPoint = current;
				if (!owned(scout, player)) { release(scout, memory, now, "STALE_NATIVE_ACTOR"); return; }
				if (activeLeg != null)
				{
					if (recheck && !eligible(scout)) { release(scout, memory, now, "OWNERSHIP_OR_INTENT_CHANGED"); return; }
					if (recheck) { scout.recheckAt = now + 3 * SECOND; }
					observe(scout, memory, player, lease, current, now);
					return;
				}
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
				issue(scout, memory, player, lease, execution, current, now);
			}
		}
		finally
		{
			if (scout.leg == null || scout.leg.execution != execution) { execution.close(); }
		}
	}

	private void selectFrontier(Scout scout, PhantomRouteLearning memory, PhantomNavigationPoint from, long now)
	{
		scout.candidate = memory.scoutFrontier(from, System.currentTimeMillis(), edge -> !scout.recent.contains(edge.to())
			&& inAssignment(scout, edge.from()) && (inAssignment(scout, edge.to()) || dedicated() && _regions.canCross(scout.assignment, edge.from(), edge.to()))
			&& _scouts.values().stream().noneMatch(other -> other != scout && edge.equals(other.candidate)));
		if (dedicated() && scout.candidate != null && !inAssignment(scout, scout.candidate.to())
			&& !_regions.admitCrossing(scout.assignment, scout.candidate.from(), scout.candidate.to())) { scout.candidate = null; }
		if (dedicated() && scout.candidate == null)
		{
			for (var direction : DIRECTIONS)
			{
				final var hint = new PhantomNavigationPoint(from.x() + direction[0] * 1250, from.y() + direction[1] * 1250, from.z(), 0);
				if (!_regions.canCross(scout.assignment, from, hint) || PhantomWalkCorridorPlanner.nearVisited(hint, scout.recent)) { continue; }
				final var target = _geo.learnedSurface(hint, 384);
				if (target == null) { continue; }
				final var edge = new Edge(from, target);
				final var previous = memory.find(edge);
				if (previous != null && (previous.successes() > 0 || previous.retryAfter() > System.currentTimeMillis())) { continue; }
				if (_regions.admitCrossing(scout.assignment, from, target))
				{
					memory.propose(edge);
					scout.candidate = edge;
					break;
				}
			}
		}
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
				if (target != null && inAssignment(scout, target) && !PhantomWalkCorridorPlanner.nearVisited(target, scout.recent))
				{
					scout.frontier = target;
					break;
				}
			}
			if (scout.frontier == null) { useBacktrack(scout, from); }
			if (dedicated() && scout.frontier == null)
			{
				rejectFrontier(scout, memory, now, "REGION_FRONTIER_UNAVAILABLE");
				return;
			}
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
		PhantomExecutionAdmission.Permit execution, PhantomNavigationPoint from, long now)
	{
		final var waypoint = scout.waypoints.peek();
		final var to = waypointWithinLeg(from, waypoint);
		if (to == null || !inAssignment(scout, from) || !inAssignment(scout, to) || from.x() == to.x() && from.y() == to.y()
			|| !_geo.canWalkDirect(from, to) || !safe(player, to))
		{
			remember(scout.blocked, new Edge(from, waypoint), 16);
			witness(scout, memory, player, null, from, waypoint, from, System.currentTimeMillis(), now, now, ++_sequence, 0, "BLOCKED_BEFORE_ISSUE");
			failPlan(scout, memory, now, "BLOCKED_BEFORE_ISSUE");
			return;
		}
		if (!eligible(scout) || !lease.executionValid()) { release(scout, memory, now, "INTENT_CHANGED_BEFORE_WALK"); return; }
		player.setWalking();
		RuntimeException nativeFailure = null;
		try { player.getAI().setIntention(Intention.MOVE_TO, new Location(to.x(), to.y(), to.z())); }
		catch (RuntimeException failure) { nativeFailure = failure; }
		final var movement = PhantomNativeTravelActions.captureWalk(player);
		final var receipt = movement == null ? null : player.nativeStraightMoveArrival(to.x(), to.y(), to.z());
		final long legId = ++_sequence;
		final long wallClock = System.currentTimeMillis();
		if (movement == null)
		{
			witness(scout, memory, player, null, from, null, from, wallClock, now, now, legId, 0, "NATIVE_MOVE_REJECTED");
			if (nativeFailure != null) { throw nativeFailure; }
			failPlan(scout, memory, now, "NATIVE_MOVE_REJECTED");
			return;
		}
		final long deadline = now + TimeUnit.SECONDS.toNanos(Math.clamp((long) Math.ceil(from.distanceTo(to) / Math.max(1, player.getMoveSpeed())) + 8, 10, 65));
		scout.leg = new Leg(movement, new Arrival(new Edge(from, movement.destination()), receipt == null ? () -> false : receipt),
			execution, execution.stamp(), legId, wallClock, now, deadline, from);
		if (nativeFailure != null || !movement.destination().equals(to) || receipt == null)
		{
			cancelLeg(scout, memory, now, "NATIVE_MOVE_REJECTED");
			if (nativeFailure != null) { throw nativeFailure; }
			failPlan(scout, memory, now, "NATIVE_MOVE_REJECTED");
			return;
		}
		_started++;
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
		if (leg.cancelReason != null) { cancelLeg(scout, memory, now, leg.cancelReason); return; }
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
		// Native ARRIVED runs after the position receipt, under the queued movement ticket.
		final boolean endpoint = sameOwner && current.equals(edge.to());
		final boolean settled = endpoint && walkSettled(leg.walk);
		if (endpoint && !settled && now < leg.deadline) { return; }
		if (settled && leg.arrival.consume(sessionOpen() && !scout.releasing && !player.isRunning(), leg.samples, current))
		{
			scout.leg = null;
			if (!eligible(scout) || !inAssignment(scout, edge.from()) || !inAssignment(scout, edge.to()) || !_geo.canWalkDirect(edge.from(), edge.to()))
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
			if (dedicated()) { _regions.arrived(scout.assignment); }
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
		return _scouts.get(scout.id) == scout && !scout.releasing
			&& (dedicated() ? player.isDedicatedGeoScout() : _population.scoutReservation(scout.id) == scout.intent)
			&& _materialization.ownsScoutMaterialization(scout.id, scout.intent) && owner != null && owner.player() == player && owner.epoch() == scout.epoch && owner.isCurrent()
			&& !owner.sealed() && World.getInstance().getPlayer(player.getObjectId()) == player && actorEligible(player);
	}

	private boolean eligible(Scout scout)
	{
		return dedicated() ? !scout.releasing && _materialization.ownsScoutMaterialization(scout.id, scout.intent) : _eligible.test(scout.id);
	}

	private boolean inAssignment(Scout scout, PhantomNavigationPoint point)
	{
		return !dedicated() || _regions.tracks(scout.assignment, point);
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
		if (leg.cancelReason == null) { leg.cancelReason = reason; }
		final var player = leg.walk.player();
		if (leg.walk.equals(PhantomNativeTravelActions.captureWalk(player)))
		{
			// Exact cancellation of previously issued native work also applies while durable handoff resolves.
			PlayerNativeWork.run(player, "SCOUT_WALK_CANCEL", () -> PhantomNativeTravelActions.cancelWalk(leg.walk));
		}
		if (!walkSettled(leg.walk)) { return; }
		scout.leg = null;
		leg.execution.close();
		witness(scout, memory, player, null, leg.arrival.edge.from(), leg.walk.destination(), PhantomNativeTravelActions.point(player), leg.wallClock, leg.started, now, leg.id, leg.samples, leg.cancelReason);
	}

	private static boolean walkSettled(OwnedWalk walk)
	{
		return !walk.owner().isCurrent() || walk.owner().sealed()
			|| !walk.equals(PhantomNativeTravelActions.captureWalk(walk.player()))
				&& !MovementTaskManager.getInstance().isPlayerMovementScheduled(walk.player())
				&& walk.owner() instanceof PhantomNativeWorkScope scope && !scope.hasMovementWork();
	}

	private void release(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		if (dedicated()) { releaseDedicated(scout, memory, now, reason); return; }
		if (!scout.releasing)
		{
			scout.releasing = true;
			scout.died = "DIED".equals(reason);
			scout.releaseReason = reason;
			scout.signalUntil = 0;
			_population.closeScoutAdmission(scout.id, scout.intent);
			_reason = reason;
			if (scout.epoch == 0)
			{
				_lastAdmissionWait = "profile=" + scout.id + " release_reason=" + reason + ' ' + scout.admissionStatus;
			}
			if ("SESSION_EXPIRED".equals(reason) && scout.frontier != null)
			{
				if (_resume.size() >= 16) { _resume.remove(_resume.keySet().iterator().next()); }
				_resume.put(scout.id, scout.frontier);
			}
			cancelRequest(scout);
			_scheduler.withdrawSignal(scout.id, SOURCE, ++_sequence);
		}
		cancelLeg(scout, memory, now, scout.releaseReason);
		if (scout.leg != null || _stopped) { return; }
		final var entry = _materialization.find(scout.id).orElse(null);
		if (entry != null && !scout.storeRetained && _materialization.ownsScoutMaterialization(scout.id, scout.intent)
			&& entry.materializedAtNanos() != 0 && now >= scout.storeRetryAt)
		{
			scout.storeRetryAt = now + 3 * SECOND;
			scout.epoch = entry.materializedAtNanos();
			scout.objectId = entry.characterObjectId();
			final var actor = World.getInstance().getPlayer(entry.characterObjectId());
			if (actor != null && actor.isDead())
			{
				if (!scout.died) { _dead++; }
				scout.died = true;
				scout.storeCorpse = true;
			}
			final var stored = _materialization.requestDematerialize(scout.id, entry.characterObjectId(), entry.materializedAtNanos(), now + 5 * SECOND);
			scout.storeRetained = stored.status() == PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED;
			scout.admissionStatus = "native_owner=SCOUT store=" + stored.status();
		}
		// The exact owner remains fenced until STORE or historical preparation releases the entry.
		if (entry != null || !_scheduler.acknowledgeScoutStore(scout.id) || !_population.releaseScout(scout.id, scout.intent)) { return; }
		_scouts.remove(scout.id, scout);
		if (_retry.size() >= 128) { _retry.remove(_retry.keySet().iterator().next()); }
		_retry.put(scout.id, now + (scout.died ? TimeUnit.HOURS.toNanos(1) : 180 * SECOND));
	}

	private void releaseDedicated(Scout scout, PhantomRouteLearning memory, long now, String reason)
	{
		if (!scout.releasing)
		{
			scout.releasing = true;
			scout.releaseReason = reason;
			scout.admissionStatus = scout.storeRetained ? "FAILED_RETAINED" : "DRAINING";
			_reason = reason;
			cancelRequest(scout);
		}
		cancelLeg(scout, memory, now, scout.releaseReason);
		if (scout.leg != null) { return; }
		final var entry = _materialization.find(scout.id).orElse(null);
		if (entry != null)
		{
			if (!_materialization.ownsScoutMaterialization(scout.id, scout.intent))
			{
				scout.storeRetained = true;
				scout.admissionStatus = "FAILED_RETAINED";
				return;
			}
			if (!scout.storeRetained && entry.materializedAtNanos() != 0 && now >= scout.storeRetryAt)
			{
				scout.storeRetryAt = now + (_stopped ? SECOND / 10 : SECOND);
				final var receipt = _materialization.requestDematerialize(scout.id, entry.characterObjectId(), entry.materializedAtNanos(), now + 8 * SECOND);
				scout.storeRetained = receipt.status() == PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED;
				if (scout.storeRetained) { scout.admissionStatus = "FAILED_RETAINED"; }
			}
			return;
		}
		final boolean cleanRotation = "REGION_ROTATION".equals(scout.releaseReason);
		final boolean administrative = _stopped || cleanRotation || "WAITING_FOR_NEXT_SESSION".equals(scout.releaseReason)
			|| "NO_SPARE_PHYSICAL_CAPACITY".equals(scout.releaseReason) || "ORDINARY_CAPACITY_PRIORITY".equals(scout.releaseReason)
			|| "WORLD_CAPACITY".equals(scout.releaseReason) || "GAMEPLAY_LOAD_HIGH".equals(scout.releaseReason);
		_regions.release(scout.assignment, System.currentTimeMillis(), scout.releaseReason, !administrative);
		if (scout.epoch != 0) { _stored++; }
		_scouts.remove(scout.id, scout);
		_slotRetry[scout.slot] = now + (administrative ? 2 : 25) * SECOND;
	}

	public synchronized boolean beginStop()
	{
		_stopped = true;
		final var memory = _navigation.routeLearning();
		final long now = System.nanoTime();
		if (dedicated())
		{
			finishDedicatedAdmission(memory, now);
			for (var scout : _scouts.values())
			{
				if (scout != _admitting) { releaseDedicated(scout, memory, now, "SERVER_STOP"); }
			}
			_reason = "SERVER_STOP";
			report(memory);
			return _admitting == null && _scouts.isEmpty();
		}
		for (var scout : _scouts.values()) { release(scout, memory, now, "SERVER_STOP"); }
		_reason = "SERVER_STOP";
		report(memory);
		// The server shutdown coordinator drains native entries after all producers stop.
		return _scouts.values().stream().noneMatch(scout -> scout.leg != null);
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
		_occupied = (int) _scouts.values().stream().filter(scout -> _materialization.ownsScoutMaterialization(scout.id, scout.intent)).count();
		final long worldPresent = _scouts.values().stream().filter(scout -> _materialization.ownsScoutMaterialization(scout.id, scout.intent)
			&& _materialization.find(scout.id).map(entry -> entry.worldPresent()).orElse(false)).count();
		final boolean proven = memory != null && memory.physicalNewProven() > 0;
		final String state = _stopped ? dedicated() && !_scouts.isEmpty() ? "DRAINING" : "STOPPED" : !sessionOpen() ? "SESSION_CLOSED" : moving > 0 && proven ? "PHYSICAL_SCOUTS_RUNNING"
			: moving > 0 ? "MOVING_AWAITING_PHYSICAL_PROOF" : active > 0 ? "PHYSICAL_SCOUTS_IDLE" : "WAITING_FOR_ADMISSION";
		final var output = new StringBuilder().append("state=").append(state).append("\nreason=").append(_reason)
			.append("\nrequested=").append(_settings.count()).append("\nreserved=").append(_scouts.size()).append("\nselected_total=").append(_selected)
			.append("\nmissing_physical_slots=").append(Math.max(0, _settings.count() - (dedicated() ? _materialization.dedicatedCapacity(_ordinaryTarget.getAsInt()) : _spareSlots.getAsInt())))
			.append("\nactive=").append(active).append("\nmaterialized=").append(worldPresent).append("\nmaterialized_total=").append(_materialized).append("\nmoving=").append(moving)
			.append("\nidle=").append(active - moving).append("\nstuck=").append(_stuck).append("\ndead=").append(_dead).append("\nwalked_units=").append(Math.round(_walked))
			.append("\nnative_legs_started=").append(_started).append("\nnative_legs_completed=").append(_completed)
			.append("\nnew_proven=").append(memory == null ? 0 : memory.physicalNewProven()).append("\ncandidates_verified=").append(_verified).append("\ncandidates_rejected=").append(_rejected)
			.append("\nreplans=").append(_replans).append("\nvertical_legs=").append(_vertical).append("\nregion_transitions=").append(_crossRegion)
			.append("\nphysical_frontiers_completed=").append(_journeys).append("\nfailures=").append(_failures)
			.append("\nbridge_status=UNCONFIRMED")
			.append("\ninstance_scope=world0;INSTANCE_ENTRY_UNSUPPORTED\ninstance_rejected=").append(_instanceRejected)
			.append("\nlast_admission_wait=").append(_lastAdmissionWait)
			.append("\nsession_mode=").append(_session.mode(Instant.now())).append("\nsession_zone=").append(_settings.zone())
			.append("\nsession_start=").append(_settings.start()).append("\nsession_hours=").append(_settings.sessionHours())
			.append("\nserver_session=").append(_serverSession).append("\nlast_progress=").append(_lastProgress == 0 ? "-" : Instant.ofEpochMilli(_lastProgress)).append('\n');
		if (dedicated())
		{
			final var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
			final var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			final var navigation = _navigation.snapshot();
			output.append("mode=DEDICATED\nbootstrapped=").append(_bootstrapped.size()).append("\nstored_total=").append(_stored)
				.append("\nordinary_occupied=").append(_materialization.occupiedPhysicalSlots() - _materialization.occupiedDedicatedSlots())
				.append("\nordinary_target=").append(_ordinaryTarget.getAsInt()).append("\nphysical_occupied=").append(_materialization.occupiedPhysicalSlots())
				.append("\nworld_players=").append(World.getInstance().getPlayers().size())
				.append("\nphysical_ceiling=").append(_materialization.physicalCeiling())
				.append("\nheap_used_bytes=").append(heap.getUsed()).append("\nheap_committed_bytes=").append(heap.getCommitted()).append("\nheap_max_bytes=").append(heap.getMax())
				.append("\nprocess_cpu=").append(os instanceof com.sun.management.OperatingSystemMXBean extended ? extended.getProcessCpuLoad() : -1)
				.append("\ngc_count=").append(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionCount).sum())
				.append("\npath_workers=").append(navigation.currentWorkers()).append("\npath_workers_max=").append(navigation.maximumWorkers())
				.append("\npath_queue=").append(navigation.queuedRequests()).append("\npath_queue_peak=").append(navigation.peakQueuedRequests())
				.append("\npressure_samples=").append(_pressureSamples).append('\n');
		}
		for (var scout : _scouts.values())
		{
			output.append("scout=").append(scout.id).append(" reservation=").append(scout.intent).append(" object=").append(scout.objectId)
				.append(" epoch=").append(scout.epoch).append(" position=").append(encode(scout.lastPoint))
				.append(" frontier=").append(encode(scout.frontier)).append(" releasing=").append(scout.releasing)
				.append(" admission=").append(scout.admissionStatus);
			if (dedicated()) { output.append(" slot=").append(scout.slot).append(" region=").append(scout.assignment.tileX()).append('_').append(scout.assignment.tileY()); }
			output.append('\n');
		}
		_status = output.toString();
	}

	static final class AdmissionWait
	{
		private long _lastProgress;
		private long _cursor = Long.MIN_VALUE;

		AdmissionWait(long now) { _lastProgress = now; }

		boolean expired(long now, long cursor)
		{
			if (cursor > _cursor)
			{
				_cursor = cursor;
				_lastProgress = now;
			}
			return now - _lastProgress >= ADMISSION_NANOS;
		}
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
		final long id, intent;
		final AdmissionWait admissionWait;
		volatile long signalUntil;
		volatile boolean storeCorpse;
		long epoch, recheckAt, retryAt, frontierDeadline, request, actionAdmissionStarted, storeRetryAt;
		int slot;
		long assignmentStarted;
		PhantomDedicatedScoutRegions.Assignment assignment;
		int objectId, direction, planAttempts, planFailures, deadEnds;
		boolean approaching, candidateWitnessed, backtracking, releasing, died, storeRetained;
		String releaseReason, admissionStatus = "requested";
		Edge candidate;
		PhantomNavigationPoint frontier, lastPoint;
		final LinkedHashSet<PhantomNavigationPoint> recent = new LinkedHashSet<>();
		final LinkedHashSet<Edge> blocked = new LinkedHashSet<>();
		final ArrayDeque<PhantomNavigationPoint> waypoints = new ArrayDeque<>(), trail = new ArrayDeque<>();
		Leg leg;
		Scout(long id, long intent, long now) { this.id = id; this.intent = intent; admissionWait = new AdmissionWait(now); direction = (int) (id % DIRECTIONS.length); }
	}

	private static final class Leg
	{
		final OwnedWalk walk;
		final Arrival arrival;
		final PhantomExecutionAdmission.Permit execution;
		final PhantomExecutionAdmission.Stamp stamp;
		final long id, wallClock, started, deadline;
		PhantomNavigationPoint previous;
		long lastChange;
		int samples;
		String cancelReason;
		Leg(OwnedWalk walk, Arrival arrival, PhantomExecutionAdmission.Permit execution, PhantomExecutionAdmission.Stamp stamp, long id, long wallClock, long started,
			long deadline, PhantomNavigationPoint previous)
		{
			this.walk = walk;
			this.arrival = arrival;
			this.execution = execution;
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
