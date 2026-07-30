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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
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
	private final PhantomPopulationStore _store;
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
	private final ArrayDeque<Long> _signals = new ArrayDeque<>();
	private final ArrayDeque<Long> _retirements = new ArrayDeque<>();
	private final ArrayDeque<Long> _readyTransitions = new ArrayDeque<>();
	private PhantomDecisionEngine _decisionEngine;
	private LifecycleState _lifecycle = LifecycleState.NEW;
	private int _target;
	private int _activeTarget;
	private boolean _inconsistentDeficit;
	private boolean _admissionDirty;
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

	public PhantomPopulationManager(PhantomPopulationStore store, PhantomPopulationCatalog catalog, PhantomGoalStateStore goals, PhantomScheduler scheduler, LongPredicate materialized, Clock clock, ZoneId zoneId, int target, int activeTarget, int maximumScheduled, int maximumMaterialized, int creationLimit, int boundaryBudget)
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

	public void installDecisionEngine(PhantomDecisionEngine decisionEngine)
	{
		synchronized (_monitor)
		{
			if ((_lifecycle != LifecycleState.NEW) || (_decisionEngine != null))
			{
				throw new IllegalStateException("Population decision engine can only be installed once before start.");
			}
			_decisionEngine = Objects.requireNonNull(decisionEngine, "Decision engine must not be null.");
		}
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if ((_lifecycle != LifecycleState.NEW) || (_decisionEngine == null))
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
				final List<ManagedSnapshot> page = _store.loadManagedAfter(cursor, Math.min(256, (_maximumScheduled - restoreIds.size()) + 1));
				if ((restoreIds.size() + page.size()) > _maximumScheduled)
				{
					throw new IllegalStateException("Managed population exceeds configured scheduler capacity.");
				}
				synchronized (_monitor)
				{
					for (ManagedSnapshot snapshot : page)
					{
						final long profileId = snapshot.profile().profileId();
						_entries.put(profileId, new Entry(snapshot));
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
				if (page.size() < Math.min(256, (_maximumScheduled - restoreIds.size()) + 1))
				{
					break;
				}
			}
			synchronized (_monitor)
			{
				_lifecycle = LifecycleState.RUNNING;
			}
			for (long profileId : restoreIds)
			{
				restore(profileId);
			}
			reconcileTarget(_target, _activeTarget);
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
					final int schedulerCapacity = Math.max(0, _maximumScheduled - _scheduler.snapshot().registered());
					shellsToCreate = _inconsistentDeficit ? 0 : Math.min(deficit, Math.min(creationCapacity, schedulerCapacity));
				}
			}
			else if (deficit < 0)
			{
				final List<Entry> readyDescending = counted.stream().filter(entry -> entry._snapshot.state().state() == State.READY).sorted(Comparator.comparingLong((Entry entry) -> entry._snapshot.profile().profileId()).reversed()).toList();
				for (Entry entry : readyDescending)
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

		CreationResult result;
		try
		{
			result = _store.advanceCreation(current);
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
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (result.snapshot() != null))
			{
				entry._snapshot = result.snapshot();
				if (result.outcome() == CreationOutcome.INCONSISTENT)
				{
					_inconsistentDeficit = true;
				}
				if (result.outcome() == CreationOutcome.READY)
				{
					_admissionDirty = true;
					_readyTransitions.offerLast(profileId);
				}
			}
		}
		if (result.outcome() == CreationOutcome.READY)
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
		while (remaining > 0)
		{
			final Long profileId;
			synchronized (_monitor)
			{
				profileId = _readyTransitions.pollFirst();
			}
			if (profileId == null)
			{
				break;
			}
			installReady(profileId);
			remaining--;
		}
		synchronized (_monitor)
		{
			if (now.isBefore(_lastControlInstant))
			{
				_lastControlInstant = now;
				return;
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
				evaluateScheduleLocked(entry, now);
				remaining--;
			}
			if (_admissionDirty)
			{
				rebalanceAdmissionLocked(now);
				_admissionDirty = false;
			}
		}
		while (remaining > 0)
		{
			final Long profileId;
			synchronized (_monitor)
			{
				profileId = _signals.pollFirst();
				if (profileId != null)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null)
					{
						entry._signalQueued = false;
					}
				}
			}
			if (profileId == null)
			{
				break;
			}
			applyScheduleSignal(profileId, now);
			remaining--;
		}
		while (remaining > 0)
		{
			final Long profileId;
			synchronized (_monitor)
			{
				profileId = _retirements.pollFirst();
			}
			if (profileId == null)
			{
				break;
			}
			completeRetirement(profileId);
			remaining--;
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
			case READY -> installReady(profileId);
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
		finally
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
		}
		synchronized (_monitor)
		{
			_entries.put(snapshot.profile().profileId(), new Entry(snapshot));
		}
		bootstrap(snapshot.profile().profileId());
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
		final PhantomScheduler.RegistrationResult registration = _scheduler.register(profileId);
		if ((registration.status() != RegistrationStatus.REGISTERED) && (registration.status() != RegistrationStatus.ALREADY_REGISTERED))
		{
			return;
		}
		final AttachResult attached = _decisionEngine.attach(profileId);
		if ((attached != AttachResult.ATTACHED) && (attached != AttachResult.ALREADY_ATTACHED))
		{
			return;
		}
		final long sequence;
		synchronized (_monitor)
		{
			sequence = ++entry._signalSequence;
		}
		_scheduler.submitSignal(profileId, new PhantomRelevanceSignal(BOOTSTRAP_SIGNAL_SOURCE, sequence, PhantomActivityState.WARM, SIGNAL_HEARTBEAT_MILLIS));
	}

	private boolean repairBootstrapGoal(Entry entry)
	{
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
		final PhantomScheduler.RegistrationResult registration = _scheduler.register(profileId);
		if ((registration.status() != RegistrationStatus.REGISTERED) && (registration.status() != RegistrationStatus.ALREADY_REGISTERED))
		{
			return;
		}
		final AttachResult attach = _decisionEngine.attach(profileId);
		if ((attach != AttachResult.ATTACHED) && (attach != AttachResult.ALREADY_ATTACHED))
		{
			return;
		}
		final Instant now = _clock.instant();
		synchronized (_monitor)
		{
			evaluateScheduleLocked(entry, now);
			_admissionDirty = true;
		}
		cleanupBootstrap(profileId);
	}

	private void cleanupBootstrap(long profileId)
	{
		final Optional<PhantomDecisionEngine.RuntimeSnapshot> runtime = _decisionEngine.find(profileId);
		if (runtime.isPresent() && BOOTSTRAP_GOAL_TYPE.equals(runtime.get().goalType()))
		{
			final MutationResult result = _decisionEngine.clearGoal(profileId);
			if ((result != MutationResult.APPLIED) && (result != MutationResult.GOAL_NOT_PRESENT))
			{
				return;
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
			_scheduler.withdrawSignal(profileId, BOOTSTRAP_SIGNAL_SOURCE, sequence);
		}
	}

	private void evaluateScheduleLocked(Entry entry, Instant now)
	{
		final ScheduleEvaluation evaluation = _catalog.evaluate(entry._snapshot.state().scheduleTemplate(), now, _zoneId, entry._snapshot.state().schedulePhaseMinutes());
		entry._desiredState = evaluation.state();
		entry._nextBoundary = evaluation.nextBoundary();
		entry._heartbeatDue = now.plusMillis(SIGNAL_HEARTBEAT_MILLIS);
		entry._scheduleGeneration++;
		_due.add(new DueEntry(earlier(entry._nextBoundary, entry._heartbeatDue), entry._snapshot.profile().profileId(), entry._scheduleGeneration));
		queueSignalLocked(entry);
		_admissionDirty = true;
	}

	private void rebalanceAdmissionLocked(Instant now)
	{
		final List<Entry> ready = _entries.values().stream().filter(entry -> entry._snapshot.state().state() == State.READY).toList();
		final List<AdmissionProfile> profiles = new ArrayList<>(ready.size());
		for (Entry entry : ready)
		{
			profiles.add(new AdmissionProfile(entry._snapshot.profile().profileId(), entry._snapshot.state().homeMapRegionId(), entry._snapshot.state().deterministicSeed(), entry._desiredState));
		}
		final long epochDay = now.atZone(_zoneId).toLocalDate().toEpochDay();
		final Set<Long> admitted = selectActiveProfiles(profiles, Math.min(_activeTarget, _maximumMaterialized), epochDay);
		for (Entry entry : ready)
		{
			final PhantomActivityState effective = (entry._desiredState == PhantomActivityState.ACTIVE) && !admitted.contains(entry._snapshot.profile().profileId()) ? PhantomActivityState.WARM : entry._desiredState;
			if (entry._effectiveState != effective)
			{
				entry._effectiveState = effective;
				queueSignalLocked(entry);
			}
		}
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

	private void applyScheduleSignal(long profileId, Instant now)
	{
		final Entry entry;
		final PhantomActivityState effective;
		final long sequence;
		final long ttl;
		synchronized (_monitor)
		{
			entry = _entries.get(profileId);
			if ((_lifecycle != LifecycleState.RUNNING) || (entry == null) || (entry._snapshot.state().state() != State.READY))
			{
				return;
			}
			effective = entry._effectiveState;
			sequence = ++entry._signalSequence;
			final long untilBoundary = Math.max(1, ChronoUnit.MILLIS.between(now, entry._nextBoundary));
			ttl = Math.min(PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS, Math.min(SIGNAL_HEARTBEAT_MILLIS, untilBoundary));
		}
		final PhantomScheduler.SignalResult result = effective == PhantomActivityState.SLEEPING ? _scheduler.withdrawSignal(profileId, SCHEDULE_SIGNAL_SOURCE, sequence) : _scheduler.submitSignal(profileId, new PhantomRelevanceSignal(SCHEDULE_SIGNAL_SOURCE, sequence, effective, ttl));
		if ((result.status() == SignalStatus.BACKPRESSURE) || (result.status() == SignalStatus.NOT_REGISTERED))
		{
			synchronized (_monitor)
			{
				queueSignalLocked(entry);
			}
		}
	}

	private void requestRetirement(long profileId)
	{
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._snapshot.state().state() != State.READY))
			{
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
		finally
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._snapshot = updated;
				entry._scheduleGeneration++;
				_admissionDirty = true;
			}
		}
		resumeRetirement(profileId);
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
		final long scheduleSequence;
		synchronized (_monitor)
		{
			scheduleSequence = ++entry._signalSequence;
		}
		_scheduler.withdrawSignal(profileId, SCHEDULE_SIGNAL_SOURCE, scheduleSequence);
		_scheduler.unregister(profileId);
		synchronized (_monitor)
		{
			_retirements.offerLast(profileId);
		}
	}

	private void completeRetirement(long profileId)
	{
		final ManagedSnapshot current;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._snapshot.state().state() != State.RETIRE_REQUESTED))
			{
				return;
			}
			if (_scheduler.find(profileId).isPresent() || _materialized.test(profileId))
			{
				_retirements.offerLast(profileId);
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
		finally
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
		}
		_decisionEngine.detach(profileId);
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._snapshot = retired;
			}
		}
	}

	private void returnRetired(long profileId)
	{
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
		finally
		{
			synchronized (_monitor)
			{
				_persistenceClaims--;
			}
		}
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._snapshot = returned;
			}
		}
		installReady(profileId);
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
			current = entry._snapshot;
		}
		final ManagedSnapshot failed = _store.updateState(current, current.state().fail(failure));
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._snapshot = failed;
			}
			_inconsistentDeficit = true;
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
			_signals.clear();
			_retirements.clear();
			_readyTransitions.clear();
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
			_lifecycle = LifecycleState.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			int ready = 0;
			int retired = 0;
			int inconsistent = 0;
			final Map<Integer, Integer> classHistogram = new LinkedHashMap<>();
			final Map<Integer, Integer> levelHistogram = new LinkedHashMap<>();
			final Map<Integer, Integer> regionHistogram = new LinkedHashMap<>();
			for (Entry entry : _entries.values())
			{
				switch (entry._snapshot.state().state())
				{
					case READY -> ready++;
					case RETIRED -> retired++;
					case INCONSISTENT -> inconsistent++;
					default ->
					{
					}
				}
				classHistogram.merge(entry._snapshot.state().classId(), 1, Integer::sum);
				levelHistogram.merge(1, 1, Integer::sum);
				regionHistogram.merge(entry._snapshot.state().homeMapRegionId(), 1, Integer::sum);
			}
			return new Snapshot(_lifecycle, _target, _activeTarget, _entries.size(), ready, retired, inconsistent, _inconsistentDeficit, _due.size(), _signals.size(), _controlCalls, _controlClaims, _creationClaims, _persistenceClaims, _peakOperations, _peakCreationClaims, _peakPersistenceClaims, Map.copyOf(classHistogram), Map.copyOf(levelHistogram), Map.copyOf(regionHistogram));
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

	private void queueSignalLocked(Entry entry)
	{
		if (!entry._signalQueued)
		{
			entry._signalQueued = true;
			_signals.offerLast(entry._snapshot.profile().profileId());
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

	public record Snapshot(LifecycleState state, int target, int activeTarget, int managed, int ready, int retired, int inconsistent, boolean deficitBlocked, int dueBoundaries, int queuedSignals, long controlCalls, long controlClaims, long creationClaims, long persistenceClaims, long peakOperations, long peakCreationClaims, long peakPersistenceClaims, Map<Integer, Integer> classHistogram, Map<Integer, Integer> levelHistogram, Map<Integer, Integer> regionHistogram)
	{
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
		private boolean _signalQueued;
		private boolean _creationClaimed;

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
}
