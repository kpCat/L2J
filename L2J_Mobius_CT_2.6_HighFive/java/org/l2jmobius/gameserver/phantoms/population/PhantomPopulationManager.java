/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.UnregisterStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.MutationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.ScheduleEvaluation;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;

/**
 * Target-driven owner for managed shells, creation admission, schedules and
 * deterministic retirement. Pulse control is in-memory except a bounded
 * retirement completion write.
 */
public final class PhantomPopulationManager implements PhantomSchedulerControlPort
{
	public static final String BOOTSTRAP_GOAL_TYPE = "population.bootstrap";
	public static final String BOOTSTRAP_SIGNAL_SOURCE = "population.bootstrap";
	public static final String SCHEDULE_SIGNAL_SOURCE = "population.schedule";
	public static final long DETERMINISTIC_SEED = 16_001_601L;
	private static final long SIGNAL_HEARTBEAT_MILLIS = 3_600_000;

	private final Object _monitor = new Object();
	private final PhantomPopulationPersistencePort _store;
	private final PhantomPopulationCatalog _catalog;
	private final PhantomGoalStateStore _goals;
	private final PhantomScheduler _scheduler;
	private final LongPredicate _materialized;
	private final Clock _clock;
	private final ZoneId _zoneId;
	private final int _maximumScheduled;
	private final int _maximumMaterialized;
	private final int _creationLimit;
	private final int _boundaryBudget;
	private final Map<Long, Entry> _entries = new HashMap<>();
	private final PriorityQueue<DueEntry> _due = new PriorityQueue<>();
	private final PriorityQueue<RetryAction> _retryActions = new PriorityQueue<>();
	private final TreeSet<Long> _readyIds = new TreeSet<>();
	private final Map<Integer, TreeSet<Long>> _readyIdsByRegion = new HashMap<>();
	private final Map<Integer, TreeSet<Long>> _desiredActiveIdsByRegion = new HashMap<>();
	private final Set<Long> _admittedIds = new HashSet<>();
	private final Map<Integer, Integer> _classHistogram = new HashMap<>();
	private final Map<Integer, Integer> _levelHistogram = new HashMap<>();
	private final Map<Integer, Integer> _regionHistogram = new HashMap<>();
	private PhantomDecisionEngine _decisionEngine;
	private PhantomPopulationOwnershipPort _ownership;
	private LifecycleState _lifecycle = LifecycleState.NEW;
	private int _target;
	private int _activeTarget;
	private boolean _inconsistentDeficit;
	private boolean _admissionDirty;
	private boolean _clockRecompute;
	private long _clockRecomputeCursor;
	private long _lastEpochDay = Long.MIN_VALUE;
	private Instant _lastControlInstant = Instant.MIN;
	private long _populationGeneration = 1;
	private long _creationOrdinal;
	private long _controlCalls;
	private long _controlClaims;
	private long _creationClaims;
	private long _persistenceClaims;
	private long _peakOperations;
	private long _peakCreationClaims;
	private long _peakPersistenceClaims;
	private long _lastPulseOperations;
	private long _retrySequence;
	private int _readyCount;
	private int _retiredCount;
	private int _inconsistentCount;

	public PhantomPopulationManager(PhantomPopulationPersistencePort store, PhantomPopulationCatalog catalog, PhantomGoalStateStore goals, PhantomScheduler scheduler, LongPredicate materialized, Clock clock, ZoneId zoneId, int target, int activeTarget, int maximumScheduled, int maximumMaterialized, int creationLimit, int boundaryBudget)
	{
		_store = Objects.requireNonNull(store, "Population store must not be null.");
		_catalog = Objects.requireNonNull(catalog, "Population catalog must not be null.");
		_goals = Objects.requireNonNull(goals, "Goal store must not be null.");
		_scheduler = Objects.requireNonNull(scheduler, "Scheduler must not be null.");
		_materialized = Objects.requireNonNull(materialized, "Materialization lookup must not be null.");
		_clock = Objects.requireNonNull(clock, "Population clock must not be null.");
		_zoneId = Objects.requireNonNull(zoneId, "Population time zone must not be null.");
		if ((maximumScheduled < 1) || (maximumMaterialized < 1) || (creationLimit < 1) || (creationLimit > 64) || (boundaryBudget < 1) || (boundaryBudget > 10000))
		{
			throw new IllegalArgumentException("Population manager capacities are invalid.");
		}
		_maximumScheduled = maximumScheduled;
		_maximumMaterialized = maximumMaterialized;
		_creationLimit = creationLimit;
		_boundaryBudget = boundaryBudget;
		validateTargets(target, activeTarget);
		_target = target;
		_activeTarget = activeTarget;
	}

	public PhantomPopulationManager(PhantomPopulationPersistencePort store, PhantomPopulationCatalog catalog, PhantomGoalStateStore goals, PhantomPopulationOwnershipPort ownership, Clock clock, ZoneId zoneId, int target, int activeTarget, int maximumScheduled, int maximumMaterialized, int creationLimit, int boundaryBudget)
	{
		_store = Objects.requireNonNull(store, "Population store must not be null.");
		_catalog = Objects.requireNonNull(catalog, "Population catalog must not be null.");
		_goals = goals;
		_scheduler = null;
		_materialized = profileId -> ownership.materialized(profileId);
		_ownership = Objects.requireNonNull(ownership, "Population ownership port must not be null.");
		_clock = Objects.requireNonNull(clock, "Population clock must not be null.");
		_zoneId = Objects.requireNonNull(zoneId, "Population time zone must not be null.");
		if ((maximumScheduled < 1) || (maximumMaterialized < 1) || (creationLimit < 1) || (creationLimit > 64) || (boundaryBudget < 1) || (boundaryBudget > 10000))
		{
			throw new IllegalArgumentException("Population manager capacities are invalid.");
		}
		_maximumScheduled = maximumScheduled;
		_maximumMaterialized = maximumMaterialized;
		_creationLimit = creationLimit;
		_boundaryBudget = boundaryBudget;
		validateTargets(target, activeTarget);
		_target = target;
		_activeTarget = activeTarget;
	}

	public void installDecisionEngine(PhantomDecisionEngine decisionEngine)
	{
		synchronized (_monitor)
		{
			if ((_lifecycle != LifecycleState.NEW) || (_decisionEngine != null) || (_scheduler == null))
			{
				throw new IllegalStateException("Population decision engine can only be installed once before start.");
			}
			_decisionEngine = Objects.requireNonNull(decisionEngine, "Decision engine must not be null.");
			_ownership = new ProductionOwnershipPort(_scheduler, _decisionEngine, _materialized);
		}
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if ((_lifecycle != LifecycleState.NEW) || (_ownership == null))
			{
				return false;
			}
			_lifecycle = LifecycleState.STARTING;
		}
		try
		{
			final List<Long> restoreIds = new ArrayList<>(Math.min(_maximumScheduled, 256));
			long cursor = 0;
			while (true)
			{
				final int pageSize = Math.min(256, (_maximumScheduled - restoreIds.size()) + 1);
				final List<ManagedSnapshot> page = _store.loadManagedAfter(cursor, pageSize);
				if ((restoreIds.size() + page.size()) > _maximumScheduled)
				{
					throw new IllegalStateException("Managed population exceeds configured scheduler capacity.");
				}
				synchronized (_monitor)
				{
					for (ManagedSnapshot snapshot : page)
					{
						final long profileId = snapshot.profile().profileId();
						publishEntryLocked(new Entry(snapshot));
						restoreIds.add(profileId);
						cursor = profileId;
						_creationOrdinal = Math.max(_creationOrdinal, snapshot.state().creationOrdinal());
						_populationGeneration = Math.max(_populationGeneration, snapshot.state().populationGeneration());
						if (snapshot.state().state() == State.INCONSISTENT)
						{
							_inconsistentDeficit = true;
						}
					}
				}
				if (page.size() < pageSize)
				{
					break;
				}
			}
			synchronized (_monitor)
			{
				_lifecycle = LifecycleState.RUNNING;
			}
			reconcileTarget(_target, _activeTarget);
			for (long profileId : restoreIds)
			{
				restore(profileId);
			}
			return true;
		}
		catch (RuntimeException e)
		{
			beginStop();
			finishStop();
			throw e;
		}
	}

	public void reconcileTarget(int target, int activeTarget)
	{
		validateTargets(target, activeTarget);
		final List<Long> returnIds = new ArrayList<>();
		final List<Long> retireIds = new ArrayList<>();
		int shellsToCreate = 0;
		synchronized (_monitor)
		{
			if (_lifecycle != LifecycleState.RUNNING)
			{
				return;
			}
			_target = target;
			_activeTarget = activeTarget;
			final List<Entry> counted = _entries.values().stream().filter(entry -> entry._snapshot.state().state() != State.RETIRED).sorted(Comparator.comparingLong(entry -> entry._snapshot.profile().profileId())).toList();
			int deficit = target - counted.size();
			if (deficit > 0)
			{
				final List<Entry> retired = _entries.values().stream().filter(entry -> entry._snapshot.state().state() == State.RETIRED).sorted(Comparator.comparingLong(entry -> entry._snapshot.profile().profileId())).toList();
				for (Entry entry : retired)
				{
					if (deficit-- <= 0)
					{
						break;
					}
					returnIds.add(entry._snapshot.profile().profileId());
				}
				if (deficit >= 0)
				{
					final long creationPending = counted.stream().filter(entry -> entry._snapshot.state().creationPending()).count();
					final int creationCapacity = Math.max(0, _creationLimit - (int) creationPending);
					final int schedulerCapacity = Math.max(0, _maximumScheduled - _ownership.registeredCount());
					shellsToCreate = _inconsistentDeficit ? 0 : Math.min(deficit, Math.min(creationCapacity, schedulerCapacity));
				}
			}
			else if (deficit < 0)
			{
				final List<Entry> managedDescending = counted.stream().filter(entry -> entry._snapshot.state().creationPending() || (entry._snapshot.state().state() == State.READY)).sorted(Comparator.comparingLong((Entry entry) -> entry._snapshot.profile().profileId()).reversed()).toList();
				for (Entry entry : managedDescending)
				{
					if (deficit++ >= 0)
					{
						break;
					}
					retireIds.add(entry._snapshot.profile().profileId());
				}
			}
			_admissionDirty = true;
		}

		for (long profileId : returnIds)
		{
			returnRetired(profileId);
		}
		for (long profileId : retireIds)
		{
			requestRetirement(profileId);
		}
		for (int index = 0; index < shellsToCreate; index++)
		{
			createAndBootstrapShell();
		}
	}

	public CreationResult advanceCreation(long profileId)
	{
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((_lifecycle != LifecycleState.RUNNING) || (entry == null) || !entry._snapshot.state().creationPending() || entry._creationClaimed)
			{
				return new CreationResult(CreationOutcome.NOT_PENDING, entry != null ? entry._snapshot : null);
			}
			entry._creationClaimed = true;
			_creationClaims++;
			updatePeaksLocked();
			current = entry._snapshot;
		}

		CreationResult result = null;
		boolean requestRetirement = false;
		try
		{
			result = _store.advanceCreation(current);
			synchronized (_monitor)
			{
				final Entry entry = _entries.get(profileId);
				if ((entry != null) && (result.snapshot() != null) && (_lifecycle == LifecycleState.RUNNING))
				{
					transitionSnapshotLocked(entry, result.snapshot());
					if (result.outcome() == CreationOutcome.INCONSISTENT)
					{
						_inconsistentDeficit = true;
					}
					if (result.outcome() == CreationOutcome.READY)
					{
						queueActionLocked(entry, RetryActionType.READY_REGISTER, 0, _controlCalls);
					}
					requestRetirement = entry._retirementPending;
					entry._retirementPending = false;
				}
			}
		}
		finally
		{
			synchronized (_monitor)
			{
				final Entry entry = _entries.get(profileId);
				if (entry != null)
				{
					entry._creationClaimed = false;
				}
				_creationClaims--;
			}
		}
		if (requestRetirement)
		{
			requestRetirement(profileId);
		}
		if ((result != null) && (result.outcome() == CreationOutcome.READY))
		{
			reconcileTarget(_target, _activeTarget);
		}
		return result;
	}

	@Override
	public void onPulse()
	{
		synchronized (_monitor)
		{
			if (_lifecycle != LifecycleState.RUNNING)
			{
				return;
			}
			_controlCalls++;
			_controlClaims++;
			updatePeaksLocked();
		}
		try
		{
			controlPulse();
		}
		finally
		{
			synchronized (_monitor)
			{
				_controlClaims--;
			}
		}
	}

	private void controlPulse()
	{
		final Instant now = _clock.instant();
		int remaining = _boundaryBudget;
		int processed = 0;
		while (remaining > 0)
		{
			final RetryAction action;
			synchronized (_monitor)
			{
				action = !_retryActions.isEmpty() && (_retryActions.peek().duePulse() <= _controlCalls) ? _retryActions.poll() : null;
				if (action != null)
				{
					final Entry entry = _entries.get(action.profileId());
					if (entry != null)
					{
						entry._queuedActions.remove(action.type());
					}
				}
			}
			if (action == null)
			{
				break;
			}
			processRetryAction(action, now);
			remaining--;
			processed++;
		}
		synchronized (_monitor)
		{
			if (now.isBefore(_lastControlInstant))
			{
				_clockRecompute = true;
				_clockRecomputeCursor = 0;
			}
			_lastControlInstant = now;
			while ((remaining > 0) && !_due.isEmpty() && !_due.peek().due().isAfter(now))
			{
				final DueEntry due = _due.poll();
				final Entry entry = _entries.get(due.profileId());
				if ((entry == null) || (entry._scheduleGeneration != due.generation()) || (entry._snapshot.state().state() != State.READY))
				{
					continue;
				}
				entry._forceScheduleEvaluation = true;
				queueActionLocked(entry, RetryActionType.READY_SCHEDULE, 0, _controlCalls);
				remaining--;
				processed++;
			}
			while ((remaining > 0) && _clockRecompute)
			{
				final Long profileId = _readyIds.higher(_clockRecomputeCursor);
				if (profileId == null)
				{
					_clockRecompute = false;
					_clockRecomputeCursor = 0;
					break;
				}
				_clockRecomputeCursor = profileId;
				final Entry entry = _entries.get(profileId);
				if (entry != null)
				{
					entry._forceScheduleEvaluation = true;
					queueActionLocked(entry, RetryActionType.READY_SCHEDULE, 0, _controlCalls);
				}
				remaining--;
				processed++;
			}
			final long epochDay = now.atZone(_zoneId).toLocalDate().toEpochDay();
			if (_admissionDirty || (_lastEpochDay != epochDay))
			{
				rebalanceAdmissionLocked(now);
				_admissionDirty = false;
				_lastEpochDay = epochDay;
			}
			_lastPulseOperations = processed;
		}
	}

	private void restore(long profileId)
	{
		final ManagedSnapshot snapshot;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry == null)
			{
				return;
			}
			snapshot = entry._snapshot;
		}
		switch (snapshot.state().state())
		{
			case SHELL, ACCOUNT_PREPARED, CHARACTER_PRESENT, INITIALIZING -> bootstrap(profileId);
			case READY ->
			{
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null)
					{
						queueActionLocked(entry, RetryActionType.READY_REGISTER, 0, _controlCalls);
					}
				}
			}
			case RETIRE_REQUESTED -> resumeRetirement(profileId);
			case RETIRED, INCONSISTENT ->
			{
			}
		}
	}

	private void createAndBootstrapShell()
	{
		final long ordinal;
		synchronized (_monitor)
		{
			if ((_lifecycle != LifecycleState.RUNNING) || _inconsistentDeficit)
			{
				return;
			}
			ordinal = ++_creationOrdinal;
			_persistenceClaims++;
			updatePeaksLocked();
		}
		final ManagedSnapshot snapshot;
		try
		{
			snapshot = _store.createShell(_populationGeneration, ordinal, DETERMINISTIC_SEED);
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
			throw e;
		}
		boolean published = false;
		synchronized (_monitor)
		{
			if (_lifecycle == LifecycleState.RUNNING)
			{
				publishEntryLocked(new Entry(snapshot));
				published = true;
			}
			_persistenceClaims--;
		}
		if (published)
		{
			bootstrap(snapshot.profile().profileId());
		}
	}

	private void bootstrap(long profileId)
	{
		final Entry entry;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
			if ((_lifecycle != LifecycleState.RUNNING) || (entry == null))
			{
				return;
			}
		}
		if (!repairBootstrapGoal(entry))
		{
			markInconsistent(profileId, "bootstrap.goal_conflict");
			return;
		}
		synchronized (_monitor)
		{
			queueActionLocked(entry, RetryActionType.BOOTSTRAP_REGISTER, 0, _controlCalls);
		}
	}

	private boolean repairBootstrapGoal(Entry entry)
	{
		if (_goals == null)
		{
			return true;
		}
		final long profileId = entry._snapshot.profile().profileId();
		final Optional<StoredGoal> stored = _goals.load(profileId);
		if (stored.isPresent())
		{
			final PhantomGoal goal = stored.get().goal();
			if (BOOTSTRAP_GOAL_TYPE.equals(goal.goalType()) && (goal.status() == PhantomGoalStatus.ACTIVE))
			{
				return true;
			}
			if (BOOTSTRAP_GOAL_TYPE.equals(goal.goalType()))
			{
				_goals.delete(profileId, stored.get().rowVersion());
			}
			else
			{
				return false;
			}
		}
		_goals.insert(profileId, bootstrapGoal(entry._snapshot));
		return true;
	}

	private static PhantomGoal bootstrapGoal(ManagedSnapshot snapshot)
	{
		final long profileId = snapshot.profile().profileId();
		final PhantomDomainRef ref = new PhantomDomainRef("population.profile", Long.toString(profileId));
		return new PhantomGoal(profileId, BOOTSTRAP_GOAL_TYPE, PhantomGoalStatus.ACTIVE, ref, ref, 1, 0, "population.create", List.of(ref), ref, "population.bootstrap", 1000, 0, 0, 0, Map.of("generation", snapshot.state().populationGeneration()), "population.bootstrap.required", 0);
	}

	private void installReady(long profileId)
	{
		final Entry entry;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
			if ((_lifecycle != LifecycleState.RUNNING) || (entry == null) || (entry._snapshot.state().state() != State.READY))
			{
				return;
			}
		}
		synchronized (_monitor)
		{
			queueActionLocked(entry, RetryActionType.READY_REGISTER, 0, _controlCalls);
		}
	}

	private boolean cleanupBootstrap(long profileId)
	{
		if (_decisionEngine == null)
		{
			return true;
		}
		final Optional<PhantomDecisionEngine.RuntimeSnapshot> runtime = _decisionEngine.find(profileId);
		if (runtime.isPresent() && BOOTSTRAP_GOAL_TYPE.equals(runtime.get().goalType()))
		{
			final MutationResult result = _decisionEngine.clearGoal(profileId);
			if ((result != MutationResult.APPLIED) && (result != MutationResult.GOAL_NOT_PRESENT))
			{
				return false;
			}
		}
		final Entry entry;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
		}
		if (entry != null)
		{
			final long sequence;
			synchronized (_monitor)
			{
				sequence = ++entry._signalSequence;
			}
			final SignalStatus status = _ownership.withdraw(profileId, BOOTSTRAP_SIGNAL_SOURCE, sequence);
			return (status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED) || (status == SignalStatus.STALE) || (status == SignalStatus.NOT_REGISTERED);
		}
		return true;
	}

	private void evaluateScheduleLocked(Entry entry, Instant now)
	{
		final ScheduleEvaluation evaluation = _catalog.evaluate(entry._snapshot.state().scheduleTemplate(), now, _zoneId, entry._snapshot.state().schedulePhaseMinutes());
		removeDesiredActiveLocked(entry);
		entry._desiredState = evaluation.state();
		addDesiredActiveLocked(entry);
		entry._nextBoundary = evaluation.nextBoundary();
		entry._heartbeatDue = now.plusMillis(SIGNAL_HEARTBEAT_MILLIS);
		entry._scheduleGeneration++;
		_due.add(new DueEntry(earlier(entry._nextBoundary, entry._heartbeatDue), entry._snapshot.profile().profileId(), entry._scheduleGeneration));
		_admissionDirty = true;
	}

	private void rebalanceAdmissionLocked(Instant now)
	{
		final long epochDay = now.atZone(_zoneId).toLocalDate().toEpochDay();
		final Map<Integer, Integer> population = new HashMap<>();
		_readyIdsByRegion.forEach((region, ids) -> population.put(region, ids.size()));
		final Map<Integer, Integer> desired = new HashMap<>();
		_desiredActiveIdsByRegion.forEach((region, ids) -> desired.put(region, ids.size()));
		final int limit = Math.min(Math.min(_activeTarget, _maximumMaterialized), desired.values().stream().mapToInt(Integer::intValue).sum());
		final Map<Integer, Integer> quotas = largestRemainderCounts(population, desired, limit);
		final Set<Long> admitted = new HashSet<>();
		for (Map.Entry<Integer, TreeSet<Long>> regional : _desiredActiveIdsByRegion.entrySet())
		{
			final TreeSet<Long> ids = regional.getValue();
			final int quota = Math.min(quotas.getOrDefault(regional.getKey(), 0), ids.size());
			if ((quota == 0) || ids.isEmpty())
			{
				continue;
			}
			final long first = ids.first();
			final long last = ids.last();
			final long span = (last - first) + 1;
			final long selectedKey = span > 0 ? first + Math.floorMod(mix(epochDay, regional.getKey()), span) : first;
			Long cursor = ids.ceiling(selectedKey);
			if (cursor == null)
			{
				cursor = first;
			}
			for (int selected = 0; selected < quota; selected++)
			{
				admitted.add(cursor);
				cursor = ids.higher(cursor);
				if (cursor == null)
				{
					cursor = ids.first();
				}
			}
		}
		final Set<Long> changed = new HashSet<>(_admittedIds);
		changed.addAll(admitted);
		changed.removeIf(profileId -> _admittedIds.contains(profileId) == admitted.contains(profileId));
		_admittedIds.clear();
		_admittedIds.addAll(admitted);
		for (long profileId : changed)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (entry._snapshot.state().state() == State.READY))
			{
				entry._effectiveState = effectiveStateLocked(entry);
				queueActionLocked(entry, RetryActionType.READY_SCHEDULE, 0, _controlCalls);
			}
		}
	}

	private static Map<Integer, Integer> largestRemainderCounts(Map<Integer, Integer> population, Map<Integer, Integer> desired, int limit)
	{
		final Map<Integer, Integer> quotas = new HashMap<>();
		if ((limit == 0) || population.isEmpty())
		{
			return quotas;
		}
		final int total = population.values().stream().mapToInt(Integer::intValue).sum();
		final List<RegionRemainder> remainders = new ArrayList<>(population.size());
		int allocated = 0;
		for (Map.Entry<Integer, Integer> region : population.entrySet())
		{
			final long scaled = (long) limit * region.getValue();
			final int floor = Math.min((int) (scaled / total), desired.getOrDefault(region.getKey(), 0));
			quotas.put(region.getKey(), floor);
			allocated += floor;
			remainders.add(new RegionRemainder(region.getKey(), scaled % total));
		}
		remainders.sort(Comparator.comparingLong(RegionRemainder::remainder).reversed().thenComparingInt(RegionRemainder::region));
		while (allocated < limit)
		{
			boolean changed = false;
			for (RegionRemainder remainder : remainders)
			{
				final int capacity = desired.getOrDefault(remainder.region(), 0);
				final int current = quotas.getOrDefault(remainder.region(), 0);
				if ((current < capacity) && (allocated < limit))
				{
					quotas.put(remainder.region(), current + 1);
					allocated++;
					changed = true;
				}
			}
			if (!changed)
			{
				break;
			}
		}
		return quotas;
	}

	public static Set<Long> selectActiveProfiles(List<AdmissionProfile> profiles, int limit, long epochDay)
	{
		Objects.requireNonNull(profiles, "Admission profiles must not be null.");
		if ((profiles.size() > 1_000_000) || (limit < 0))
		{
			throw new IllegalArgumentException("Admission input exceeds bounded limits.");
		}
		final Map<Integer, Integer> populationByRegion = new HashMap<>();
		final Map<Integer, List<AdmissionProfile>> desiredByRegion = new HashMap<>();
		final Set<Long> ids = new HashSet<>();
		for (AdmissionProfile profile : profiles)
		{
			if ((profile == null) || (profile.profileId() <= 0) || (profile.regionId() < 0) || !ids.add(profile.profileId()))
			{
				throw new IllegalArgumentException("Admission profiles must contain unique positive identities and valid regions.");
			}
			populationByRegion.merge(profile.regionId(), 1, Integer::sum);
			if (profile.desiredState() == PhantomActivityState.ACTIVE)
			{
				desiredByRegion.computeIfAbsent(profile.regionId(), _ -> new ArrayList<>()).add(profile);
			}
		}
		final int boundedLimit = Math.min(limit, desiredByRegion.values().stream().mapToInt(List::size).sum());
		final Map<Integer, Integer> quotas = largestRemainder(populationByRegion, desiredByRegion, boundedLimit);
		final Set<Long> admitted = new HashSet<>();
		for (Map.Entry<Integer, List<AdmissionProfile>> regional : desiredByRegion.entrySet())
		{
			regional.getValue().sort(Comparator.comparingLong(profile -> mix(profile.seed() ^ epochDay, profile.profileId())));
			final int quota = quotas.getOrDefault(regional.getKey(), 0);
			for (int index = 0; index < Math.min(quota, regional.getValue().size()); index++)
			{
				admitted.add(regional.getValue().get(index).profileId());
			}
		}
		return Set.copyOf(admitted);
	}

	private static Map<Integer, Integer> largestRemainder(Map<Integer, Integer> population, Map<Integer, List<AdmissionProfile>> desired, int limit)
	{
		final Map<Integer, Integer> quotas = new HashMap<>();
		if ((limit == 0) || population.isEmpty())
		{
			return quotas;
		}
		final int total = population.values().stream().mapToInt(Integer::intValue).sum();
		final List<RegionRemainder> remainders = new ArrayList<>();
		int allocated = 0;
		for (Map.Entry<Integer, Integer> region : population.entrySet())
		{
			final long scaled = (long) limit * region.getValue();
			final int floor = Math.min((int) (scaled / total), desired.getOrDefault(region.getKey(), List.of()).size());
			quotas.put(region.getKey(), floor);
			allocated += floor;
			remainders.add(new RegionRemainder(region.getKey(), scaled % total));
		}
		remainders.sort(Comparator.comparingLong(RegionRemainder::remainder).reversed().thenComparingInt(RegionRemainder::region));
		while (allocated < limit)
		{
			boolean changed = false;
			for (RegionRemainder remainder : remainders)
			{
				final int capacity = desired.getOrDefault(remainder.region(), List.of()).size();
				final int current = quotas.getOrDefault(remainder.region(), 0);
				if ((current < capacity) && (allocated < limit))
				{
					quotas.put(remainder.region(), current + 1);
					allocated++;
					changed = true;
				}
			}
			if (!changed)
			{
				break;
			}
		}
		return quotas;
	}

	private void processRetryAction(RetryAction action, Instant now)
	{
		try
		{
			switch (action.type())
			{
				case BOOTSTRAP_REGISTER -> processRegister(action, RetryActionType.BOOTSTRAP_ATTACH);
				case BOOTSTRAP_ATTACH -> processAttach(action, RetryActionType.BOOTSTRAP_SIGNAL);
				case BOOTSTRAP_SIGNAL -> processSignal(action, BOOTSTRAP_SIGNAL_SOURCE, PhantomActivityState.WARM, RetryActionType.BOOTSTRAP_REGISTER);
				case READY_REGISTER -> processRegister(action, RetryActionType.READY_ATTACH);
				case READY_ATTACH -> processAttach(action, RetryActionType.READY_SCHEDULE);
				case READY_SCHEDULE -> processReadySchedule(action, now);
				case RETIRE_WITHDRAW -> processRetireWithdraw(action);
				case RETIRE_UNREGISTER -> processRetireUnregister(action);
				case RETIRE_COMPLETE -> processRetireComplete(action);
				case RETURN_REGISTER -> processRegister(action, RetryActionType.RETURN_ATTACH);
				case RETURN_ATTACH -> processAttach(action, RetryActionType.RETURN_SCHEDULE);
				case RETURN_SCHEDULE -> processReturnSchedule(action);
			}
		}
		catch (RuntimeException e)
		{
			retryOrFail(action, "ownership.action_exception");
		}
	}

	private void processRegister(RetryAction action, RetryActionType next)
	{
		if (!actionCurrent(action))
		{
			return;
		}
		final RegistrationStatus status = _ownership.register(action.profileId());
		switch (status)
		{
			case REGISTERED, ALREADY_REGISTERED -> queueNext(action, next);
			case CAPACITY_REACHED, NOT_RUNNING -> retryOrFail(action, "ownership.register_exhausted");
			case INVALID_PROFILE_ID -> failAction(action, "ownership.register_invalid");
		}
	}

	private void processAttach(RetryAction action, RetryActionType next)
	{
		if (!actionCurrent(action))
		{
			return;
		}
		final AttachResult status = _ownership.attach(action.profileId());
		switch (status)
		{
			case ATTACHED, ALREADY_ATTACHED -> queueNext(action, next);
			case CAPACITY_REJECTED, NOT_RUNNING, CANCELLED_BY_STOP, PERSISTENCE_FAILED -> retryOrFail(action, "ownership.attach_exhausted");
			case INVALID_PROFILE_ID, PROFILE_NOT_FOUND -> failAction(action, "ownership.attach_invalid");
		}
	}

	private void processSignal(RetryAction action, String source, PhantomActivityState state, RetryActionType registerAction)
	{
		final Entry entry = currentEntry(action);
		if (entry == null)
		{
			return;
		}
		final long sequence;
		synchronized (_monitor)
		{
			sequence = ++entry._signalSequence;
		}
		final SignalStatus status = _ownership.submit(action.profileId(), source, sequence, state, SIGNAL_HEARTBEAT_MILLIS);
		switch (status)
		{
			case ACCEPTED, COALESCED, STALE ->
			{
			}
			case BACKPRESSURE, NOT_RUNNING -> retryOrFail(action, "ownership.signal_exhausted");
			case NOT_REGISTERED -> queueNext(action, registerAction);
			case REJECTED -> failAction(action, "ownership.signal_rejected");
		}
	}

	private void processReadySchedule(RetryAction action, Instant now)
	{
		final Entry entry = currentEntry(action);
		if ((entry == null) || (entry._snapshot.state().state() != State.READY))
		{
			return;
		}
		final PhantomActivityState effective;
		final long sequence;
		final long ttl;
		synchronized (_monitor)
		{
			if (entry._forceScheduleEvaluation || entry._nextBoundary.equals(Instant.MAX) || !entry._nextBoundary.isAfter(now) || !entry._heartbeatDue.isAfter(now))
			{
				entry._forceScheduleEvaluation = false;
				evaluateScheduleLocked(entry, now);
			}
			entry._effectiveState = effectiveStateLocked(entry);
			effective = entry._effectiveState;
			sequence = ++entry._signalSequence;
			final long untilBoundary = Math.max(1, ChronoUnit.MILLIS.between(now, entry._nextBoundary));
			ttl = Math.min(PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS, Math.min(SIGNAL_HEARTBEAT_MILLIS, untilBoundary));
		}
		final SignalStatus status = effective == PhantomActivityState.SLEEPING ? _ownership.withdraw(action.profileId(), SCHEDULE_SIGNAL_SOURCE, sequence) : _ownership.submit(action.profileId(), SCHEDULE_SIGNAL_SOURCE, sequence, effective, ttl);
		switch (status)
		{
			case ACCEPTED, COALESCED, STALE ->
			{
				if (!cleanupBootstrap(action.profileId()))
				{
					retryOrFail(action, "ownership.bootstrap_cleanup_exhausted");
				}
			}
			case BACKPRESSURE, NOT_RUNNING -> retryOrFail(action, "ownership.schedule_exhausted");
			case NOT_REGISTERED -> queueNext(action, RetryActionType.READY_REGISTER);
			case REJECTED -> failAction(action, "ownership.schedule_rejected");
		}
	}

	private void processRetireWithdraw(RetryAction action)
	{
		final Entry entry = currentEntry(action);
		if ((entry == null) || (entry._snapshot.state().state() != State.RETIRE_REQUESTED))
		{
			return;
		}
		final long scheduleSequence;
		final long bootstrapSequence;
		synchronized (_monitor)
		{
			scheduleSequence = ++entry._signalSequence;
			bootstrapSequence = ++entry._signalSequence;
		}
		final SignalStatus schedule = _ownership.withdraw(action.profileId(), SCHEDULE_SIGNAL_SOURCE, scheduleSequence);
		final SignalStatus bootstrap = _ownership.withdraw(action.profileId(), BOOTSTRAP_SIGNAL_SOURCE, bootstrapSequence);
		if (permanentSignalFailure(schedule) || permanentSignalFailure(bootstrap))
		{
			failAction(action, "ownership.retire_withdraw_rejected");
		}
		else if (transientSignalFailure(schedule) || transientSignalFailure(bootstrap))
		{
			retryOrFail(action, "ownership.retire_withdraw_exhausted");
		}
		else
		{
			queueNext(action, RetryActionType.RETIRE_UNREGISTER);
		}
	}

	private void processRetireUnregister(RetryAction action)
	{
		if (!actionCurrent(action))
		{
			return;
		}
		final UnregisterStatus status = _ownership.unregister(action.profileId());
		switch (status)
		{
			case UNREGISTERED, NOT_REGISTERED -> queueNext(action, RetryActionType.RETIRE_COMPLETE);
			case PENDING, BACKPRESSURE, NOT_RUNNING -> retryOrFail(action, "ownership.unregister_exhausted");
			case INVALID_PROFILE_ID -> failAction(action, "ownership.unregister_invalid");
		}
	}

	private void processRetireComplete(RetryAction action)
	{
		final Entry entry = currentEntry(action);
		if ((entry == null) || (entry._snapshot.state().state() != State.RETIRE_REQUESTED))
		{
			return;
		}
		if (_ownership.registered(action.profileId()) || _ownership.materialized(action.profileId()))
		{
			retryOrFail(action, "ownership.retire_presence_exhausted");
			return;
		}
		final DetachResult detached = _ownership.detach(action.profileId());
		switch (detached)
		{
			case PENDING ->
			{
				retryOrFail(action, "ownership.detach_exhausted");
				return;
			}
			case DETACHED, NOT_ATTACHED ->
			{
			}
		}
		persistRetired(entry);
	}

	private void processReturnSchedule(RetryAction action)
	{
		final Entry entry = currentEntry(action);
		if ((entry == null) || (entry._snapshot.state().state() != State.RETIRED))
		{
			return;
		}
		final boolean linked = entry._snapshot.state().creationStage() == PhantomPopulationState.CreationStage.LINKED;
		final String source = linked ? SCHEDULE_SIGNAL_SOURCE : BOOTSTRAP_SIGNAL_SOURCE;
		final long sequence;
		synchronized (_monitor)
		{
			sequence = ++entry._signalSequence;
		}
		final SignalStatus status = _ownership.submit(action.profileId(), source, sequence, PhantomActivityState.WARM, SIGNAL_HEARTBEAT_MILLIS);
		switch (status)
		{
			case ACCEPTED, COALESCED, STALE -> persistReturned(entry);
			case BACKPRESSURE, NOT_RUNNING -> retryOrFail(action, "ownership.return_signal_exhausted");
			case NOT_REGISTERED -> queueNext(action, RetryActionType.RETURN_REGISTER);
			case REJECTED -> failAction(action, "ownership.return_signal_rejected");
		}
	}

	private boolean actionCurrent(RetryAction action)
	{
		return currentEntry(action) != null;
	}

	private Entry currentEntry(RetryAction action)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(action.profileId());
			return (entry != null) && (entry._ownershipGeneration == action.generation()) && (_lifecycle == LifecycleState.RUNNING) ? entry : null;
		}
	}

	private void queueNext(RetryAction current, RetryActionType next)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(current.profileId());
			if ((entry != null) && (entry._ownershipGeneration == current.generation()) && (_lifecycle == LifecycleState.RUNNING))
			{
				queueActionLocked(entry, next, 0, _controlCalls);
			}
		}
	}

	private void retryOrFail(RetryAction action, String exhaustedFailure)
	{
		if (action.attempt() >= 15)
		{
			failAction(action, exhaustedFailure);
			return;
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(action.profileId());
			if ((entry != null) && (entry._ownershipGeneration == action.generation()) && (_lifecycle == LifecycleState.RUNNING))
			{
				final long backoff = 1L << Math.min(action.attempt(), 10);
				queueActionLocked(entry, action.type(), action.attempt() + 1, _controlCalls + backoff);
			}
		}
	}

	private void failAction(RetryAction action, String failure)
	{
		if (actionCurrent(action))
		{
			markInconsistent(action.profileId(), failure);
		}
	}

	private static boolean transientSignalFailure(SignalStatus status)
	{
		return (status == SignalStatus.BACKPRESSURE) || (status == SignalStatus.NOT_RUNNING);
	}

	private static boolean permanentSignalFailure(SignalStatus status)
	{
		return status == SignalStatus.REJECTED;
	}

	private void requestRetirement(long profileId)
	{
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (!entry._snapshot.state().creationPending() && (entry._snapshot.state().state() != State.READY)))
			{
				return;
			}
			if (entry._creationClaimed)
			{
				entry._retirementPending = true;
				return;
			}
			current = entry._snapshot;
			_persistenceClaims++;
			updatePeaksLocked();
		}
		final ManagedSnapshot updated;
		try
		{
			updated = _store.updateState(current, current.state().retireRequested());
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
			throw e;
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (_lifecycle == LifecycleState.RUNNING))
			{
				transitionSnapshotLocked(entry, updated);
				queueActionLocked(entry, RetryActionType.RETIRE_WITHDRAW, 0, _controlCalls);
			}
			_persistenceClaims--;
		}
	}

	private void resumeRetirement(long profileId)
	{
		final Entry entry;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
		}
		if (entry == null)
		{
			return;
		}
		synchronized (_monitor)
		{
			queueActionLocked(entry, RetryActionType.RETIRE_WITHDRAW, 0, _controlCalls);
		}
	}

	private void persistRetired(Entry claimedEntry)
	{
		final long profileId = claimedEntry._snapshot.profile().profileId();
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._snapshot.state().state() != State.RETIRE_REQUESTED))
			{
				return;
			}
			current = entry._snapshot;
			_persistenceClaims++;
			updatePeaksLocked();
		}
		final ManagedSnapshot retired;
		try
		{
			retired = _store.updateState(current, current.state().retired());
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
			throw e;
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (_lifecycle == LifecycleState.RUNNING))
			{
				transitionSnapshotLocked(entry, retired);
			}
			_persistenceClaims--;
		}
	}

	private void returnRetired(long profileId)
	{
		final Entry entry;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
			if ((entry == null) || (entry._snapshot.state().state() != State.RETIRED))
			{
				return;
			}
		}
		if ((entry._snapshot.state().creationStage() != PhantomPopulationState.CreationStage.LINKED) && !repairBootstrapGoal(entry))
		{
			markInconsistent(profileId, "bootstrap.goal_conflict");
			return;
		}
		synchronized (_monitor)
		{
			final Entry current = _entries.get(profileId);
			if ((current != null) && (current._snapshot.state().state() == State.RETIRED))
			{
				current._ownershipGeneration++;
				current._queuedActions.clear();
				queueActionLocked(current, RetryActionType.RETURN_REGISTER, 0, _controlCalls);
			}
		}
	}

	private void persistReturned(Entry claimedEntry)
	{
		final long profileId = claimedEntry._snapshot.profile().profileId();
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._snapshot.state().state() != State.RETIRED))
			{
				return;
			}
			current = entry._snapshot;
			_persistenceClaims++;
			updatePeaksLocked();
		}
		final ManagedSnapshot returned;
		try
		{
			returned = _store.updateState(current, current.state().returned());
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
			throw e;
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (_lifecycle == LifecycleState.RUNNING))
			{
				transitionSnapshotLocked(entry, returned);
				if (returned.state().state() == State.READY)
				{
					queueActionLocked(entry, RetryActionType.READY_SCHEDULE, 0, _controlCalls);
				}
			}
			_persistenceClaims--;
		}
		if (returned.state().creationPending())
		{
			bootstrap(profileId);
		}
	}

	private void markInconsistent(long profileId, String failure)
	{
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry == null)
			{
				return;
			}
			if (entry._snapshot.state().state() == State.INCONSISTENT)
			{
				return;
			}
			current = entry._snapshot;
			_persistenceClaims++;
			updatePeaksLocked();
		}
		final ManagedSnapshot failed;
		try
		{
			failed = _store.updateState(current, current.state().fail(failure));
		}
		catch (RuntimeException e)
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
			throw e;
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (_lifecycle == LifecycleState.RUNNING))
			{
				transitionSnapshotLocked(entry, failed);
			}
			_inconsistentDeficit = true;
			_persistenceClaims--;
		}
	}

	public BeginStopResult beginStop()
	{
		synchronized (_monitor)
		{
			if (_lifecycle == LifecycleState.STOPPED)
			{
				return BeginStopResult.ALREADY_STOPPED;
			}
			if (_lifecycle == LifecycleState.STOPPING)
			{
				return BeginStopResult.ALREADY_STOPPING;
			}
			_lifecycle = LifecycleState.STOPPING;
			_due.clear();
			_retryActions.clear();
			return BeginStopResult.STARTED;
		}
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_lifecycle == LifecycleState.STOPPED)
			{
				return true;
			}
			if ((_lifecycle != LifecycleState.STOPPING) || (_controlClaims != 0) || (_creationClaims != 0) || (_persistenceClaims != 0))
			{
				return false;
			}
			_entries.clear();
			_readyIds.clear();
			_readyIdsByRegion.clear();
			_desiredActiveIdsByRegion.clear();
			_admittedIds.clear();
			_classHistogram.clear();
			_levelHistogram.clear();
			_regionHistogram.clear();
			_readyCount = 0;
			_retiredCount = 0;
			_inconsistentCount = 0;
			_inconsistentDeficit = false;
			_lastPulseOperations = 0;
			_lifecycle = LifecycleState.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new Snapshot(_lifecycle, _target, _activeTarget, _entries.size(), _readyCount, _retiredCount, _inconsistentCount, _inconsistentDeficit, _due.size(), 0, _retryActions.size(), _lastPulseOperations, _controlCalls, _controlClaims, _creationClaims, _persistenceClaims, _peakOperations, _peakCreationClaims, _peakPersistenceClaims, Map.copyOf(_classHistogram), Map.copyOf(_levelHistogram), Map.copyOf(_regionHistogram));
		}
	}

	public Optional<ManagedSnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return entry == null ? Optional.empty() : Optional.of(entry._snapshot);
		}
	}

	private void publishEntryLocked(Entry entry)
	{
		final long profileId = entry._snapshot.profile().profileId();
		if (_entries.putIfAbsent(profileId, entry) != null)
		{
			throw new IllegalStateException("Managed population entry was published twice.");
		}
		_classHistogram.merge(entry._snapshot.state().classId(), 1, Integer::sum);
		_levelHistogram.merge(1, 1, Integer::sum);
		_regionHistogram.merge(entry._snapshot.state().homeMapRegionId(), 1, Integer::sum);
		addStateIndexesLocked(entry);
	}

	private void transitionSnapshotLocked(Entry entry, ManagedSnapshot snapshot)
	{
		removeStateIndexesLocked(entry);
		entry._snapshot = snapshot;
		entry._ownershipGeneration++;
		entry._queuedActions.clear();
		entry._scheduleGeneration++;
		addStateIndexesLocked(entry);
	}

	private void addStateIndexesLocked(Entry entry)
	{
		final long profileId = entry._snapshot.profile().profileId();
		switch (entry._snapshot.state().state())
		{
			case READY ->
			{
				_readyCount++;
				_readyIds.add(profileId);
				_readyIdsByRegion.computeIfAbsent(entry._snapshot.state().homeMapRegionId(), _ -> new TreeSet<>()).add(profileId);
				addDesiredActiveLocked(entry);
				_admissionDirty = true;
			}
			case RETIRED -> _retiredCount++;
			case INCONSISTENT -> _inconsistentCount++;
			default ->
			{
			}
		}
	}

	private void removeStateIndexesLocked(Entry entry)
	{
		final long profileId = entry._snapshot.profile().profileId();
		switch (entry._snapshot.state().state())
		{
			case READY ->
			{
				_readyCount--;
				_readyIds.remove(profileId);
				final int region = entry._snapshot.state().homeMapRegionId();
				final TreeSet<Long> regional = _readyIdsByRegion.get(region);
				if (regional != null)
				{
					regional.remove(profileId);
					if (regional.isEmpty())
					{
						_readyIdsByRegion.remove(region);
					}
				}
				removeDesiredActiveLocked(entry);
				_admittedIds.remove(profileId);
				_admissionDirty = true;
			}
			case RETIRED -> _retiredCount--;
			case INCONSISTENT -> _inconsistentCount--;
			default ->
			{
			}
		}
	}

	private void addDesiredActiveLocked(Entry entry)
	{
		if ((entry._snapshot.state().state() == State.READY) && (entry._desiredState == PhantomActivityState.ACTIVE))
		{
			_desiredActiveIdsByRegion.computeIfAbsent(entry._snapshot.state().homeMapRegionId(), _ -> new TreeSet<>()).add(entry._snapshot.profile().profileId());
		}
	}

	private void removeDesiredActiveLocked(Entry entry)
	{
		if (entry._desiredState != PhantomActivityState.ACTIVE)
		{
			return;
		}
		final int region = entry._snapshot.state().homeMapRegionId();
		final TreeSet<Long> regional = _desiredActiveIdsByRegion.get(region);
		if (regional != null)
		{
			regional.remove(entry._snapshot.profile().profileId());
			if (regional.isEmpty())
			{
				_desiredActiveIdsByRegion.remove(region);
			}
		}
	}

	private PhantomActivityState effectiveStateLocked(Entry entry)
	{
		return (entry._desiredState == PhantomActivityState.ACTIVE) && !_admittedIds.contains(entry._snapshot.profile().profileId()) ? PhantomActivityState.WARM : entry._desiredState;
	}

	private void queueActionLocked(Entry entry, RetryActionType type, int attempt, long duePulse)
	{
		if ((_lifecycle == LifecycleState.RUNNING) && entry._queuedActions.add(type))
		{
			_retryActions.add(new RetryAction(type, entry._snapshot.profile().profileId(), entry._ownershipGeneration, attempt, duePulse, ++_retrySequence));
		}
	}

	private void updatePeaksLocked()
	{
		_peakCreationClaims = Math.max(_peakCreationClaims, _creationClaims);
		_peakPersistenceClaims = Math.max(_peakPersistenceClaims, _persistenceClaims);
		_peakOperations = Math.max(_peakOperations, _controlClaims + _creationClaims + _persistenceClaims);
	}

	private void validateTargets(int target, int activeTarget)
	{
		if ((target < 0) || (target > _maximumScheduled) || (activeTarget < 0) || (activeTarget > Math.min(target, _maximumMaterialized)))
		{
			throw new IllegalArgumentException("Population targets exceed manager capacity.");
		}
	}

	private static Instant earlier(Instant first, Instant second)
	{
		return first.isBefore(second) ? first : second;
	}

	private static long mix(long seed, long value)
	{
		long mixed = seed ^ (value + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	public enum LifecycleState
	{
		NEW,
		STARTING,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum BeginStopResult
	{
		STARTED,
		ALREADY_STOPPING,
		ALREADY_STOPPED
	}

	public record Snapshot(LifecycleState state, int target, int activeTarget, int managed, int ready, int retired, int inconsistent, boolean deficitBlocked, int dueBoundaries, int queuedSignals, int retryActions, long lastPulseOperations, long controlCalls, long controlClaims, long creationClaims, long persistenceClaims, long peakOperations, long peakCreationClaims, long peakPersistenceClaims, Map<Integer, Integer> classHistogram, Map<Integer, Integer> levelHistogram, Map<Integer, Integer> regionHistogram)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(LifecycleState.STOPPED, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of());
		}
	}

	public record AdmissionProfile(long profileId, int regionId, long seed, PhantomActivityState desiredState)
	{
		public AdmissionProfile
		{
			Objects.requireNonNull(desiredState, "Desired admission state must not be null.");
		}
	}

	private static final class Entry
	{
		private ManagedSnapshot _snapshot;
		private PhantomActivityState _desiredState = PhantomActivityState.SLEEPING;
		private PhantomActivityState _effectiveState = PhantomActivityState.SLEEPING;
		private Instant _nextBoundary = Instant.MAX;
		private Instant _heartbeatDue = Instant.MAX;
		private long _scheduleGeneration;
		private long _signalSequence;
		private boolean _creationClaimed;
		private boolean _retirementPending;
		private boolean _forceScheduleEvaluation;
		private long _ownershipGeneration;
		private final EnumSet<RetryActionType> _queuedActions = EnumSet.noneOf(RetryActionType.class);

		private Entry(ManagedSnapshot snapshot)
		{
			_snapshot = snapshot;
		}
	}

	private record DueEntry(Instant due, long profileId, long generation) implements Comparable<DueEntry>
	{
		@Override
		public int compareTo(DueEntry other)
		{
			final int time = due.compareTo(other.due);
			return time != 0 ? time : Long.compare(profileId, other.profileId);
		}
	}

	private record RegionRemainder(int region, long remainder)
	{
	}

	public enum RetryActionType
	{
		BOOTSTRAP_REGISTER,
		BOOTSTRAP_ATTACH,
		BOOTSTRAP_SIGNAL,
		READY_REGISTER,
		READY_ATTACH,
		READY_SCHEDULE,
		RETIRE_WITHDRAW,
		RETIRE_UNREGISTER,
		RETIRE_COMPLETE,
		RETURN_REGISTER,
		RETURN_ATTACH,
		RETURN_SCHEDULE
	}

	private record RetryAction(RetryActionType type, long profileId, long generation, int attempt, long duePulse, long sequence) implements Comparable<RetryAction>
	{
		@Override
		public int compareTo(RetryAction other)
		{
			final int due = Long.compare(duePulse, other.duePulse);
			return due != 0 ? due : Long.compare(sequence, other.sequence);
		}
	}

	private static final class ProductionOwnershipPort implements PhantomPopulationOwnershipPort
	{
		private final PhantomScheduler _scheduler;
		private final PhantomDecisionEngine _decision;
		private final LongPredicate _materialized;

		private ProductionOwnershipPort(PhantomScheduler scheduler, PhantomDecisionEngine decision, LongPredicate materialized)
		{
			_scheduler = scheduler;
			_decision = decision;
			_materialized = materialized;
		}

		@Override
		public RegistrationStatus register(long profileId)
		{
			return _scheduler.register(profileId).status();
		}

		@Override
		public AttachResult attach(long profileId)
		{
			return _decision.attach(profileId);
		}

		@Override
		public SignalStatus submit(long profileId, String source, long sequence, PhantomActivityState state, long ttlMillis)
		{
			return _scheduler.submitSignal(profileId, new PhantomRelevanceSignal(source, sequence, state, ttlMillis)).status();
		}

		@Override
		public SignalStatus withdraw(long profileId, String source, long sequence)
		{
			return _scheduler.withdrawSignal(profileId, source, sequence).status();
		}

		@Override
		public UnregisterStatus unregister(long profileId)
		{
			return _scheduler.unregister(profileId).status();
		}

		@Override
		public DetachResult detach(long profileId)
		{
			return _decision.detach(profileId);
		}

		@Override
		public boolean registered(long profileId)
		{
			return _scheduler.find(profileId).isPresent();
		}

		@Override
		public boolean materialized(long profileId)
		{
			return _materialized.test(profileId);
		}

		@Override
		public int registeredCount()
		{
			return _scheduler.snapshot().registered();
		}
	}
}
