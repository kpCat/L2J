/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.threads.ThreadPool;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog.PresetDefinition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;

/**
 * Bounded calendar drain above Goal033A. Population pulses dispatch at most one
 * batch onto the shared pool; no historical I/O runs on the scheduler thread.
 */
public final class PhantomPopulationEcologyService
{
	private static final long MINUTE_MILLIS = 60_000L;
	private static final int MAXIMUM_CALENDAR_STEPS_PER_PROFILE = 4096;
	private static final int OWNERSHIP_RECOVERY_TURN = -1;
	private final Object _monitor = new Object();
	private final PhantomPopulationEcologyCatalog _catalog;
	private final PhantomPopulationCatalog _populationCatalog;
	private final PersistencePort _store;
	private final HistoricalPort _historical;
	private final LongPredicate _materialized;
	private final SafeBoundary _safeBoundary;
	private final Clock _clock;
	private final ZoneId _zoneId;
	private final Preset _preset;
	private final int _worldAgeDaysOverride;
	private final int _archiveLimit;
	private final Dispatcher _dispatcher;
	private boolean _workerInFlight;
	private boolean _workerStarted;
	private long _workerGeneration;
	private long _workerQueuedPulse;
	private WakeScheduler _wakeScheduler;
	private LongSupplier _monotonicMillis;
	private long _quantumMillis;
	private Runnable _cancelWake;
	private long _wakeGeneration;
	private long _nextWakeMillis;
	private long _nextBatchMillis;
	private long _workerQueuedMillis;
	private long _activeProfile;
	private String _wakeFailure;
	private boolean _stopping;
	private LongPredicate _currentDemand = _ -> true;
	private final Map<Long, Entry> _entries = new LinkedHashMap<>();
	private final ArrayDeque<Long> _due = new ArrayDeque<>();
	private final Set<Long> _queued = new HashSet<>();
	private final ArrayDeque<Long> _metadataDue = new ArrayDeque<>();
	private final Set<Long> _metadataQueued = new HashSet<>();
	private final ArrayDeque<Long> _materializationDue = new ArrayDeque<>();
	private final Set<Long> _materializationQueued = new HashSet<>();
	private final Map<Long, DemandFact> _demandFacts = new HashMap<>();
	private final Set<Long> _admittedPreparation = new HashSet<>();
	private final TreeMap<Long, Set<Long>> _delayed = new TreeMap<>();
	private final Map<Long, Long> _delayedAt = new HashMap<>();
	private boolean _batchAdmission;
	private int _preparationSlots = 8;
	private long _focusId;
	private int _focusBatches;
	private long _focusStartedMillis;
	private long _lastBatchElapsedMillis;
	private long _refreshedMinute = -1;
	private int _pausedEntries;
	private boolean _populationPlanApplied = true;
	private final ArrayDeque<Long> _replacementQueue = new ArrayDeque<>();
	private final Map<Pace, Integer> _paceHistogram = new EnumMap<>(Pace.class);
	private final Map<Personality, Integer> _personalityHistogram = new EnumMap<>(Personality.class);
	private final Map<String, Integer> _scheduleHistogram = new TreeMap<>();
	private PopulationView _populationView;
	private PopulationEvents _populationEvents;
	private boolean _periodicDueMode;
	private boolean _inventoryReady = true;
	private boolean _replacementInventoryBuilt;
	private int _unloadedEntries;
	private int _metadataProfileBudget;
	private boolean _metadataBudgetInstalled;
	private boolean _metadataDraining;
	private Consumer<MetadataObservation> _metadataObserver;
	private long _metadataProfileOperations;
	private long _metadataPublications;
	private long _metadataFailures;
	private int _lastMetadataBatch;
	private int _maximumMetadataBatch;
	private String _metadataObserverFailure;
	private long _archiveGeneration;
	private long _pulses;
	private long _profileOperations;
	private long _historicalIntervals;
	private long _productiveMinutes;
	private long _calendarMinutes;
	private long _persistenceWrites;
	private long _failures;
	private int _managed;
	private int _archived;
	private int _lastPulseProfiles;
	private int _lastPulseIntervals;
	private int _maximumPulseProfiles;
	private int _maximumPulseIntervals;
	private long _periodicDueCalls;
	private long _periodicOverdueCalls;
	private int _periodicRunning;
	private long _periodicBlockedCalls;
	private String _lastFailure = "";

	public PhantomPopulationEcologyService(PhantomPopulationEcologyCatalog catalog, PhantomPopulationCatalog populationCatalog, PhantomPopulationEcologyStore store, PhantomHistoricalBackgroundService historical, LongPredicate materialized, SafeBoundary safeBoundary, Clock clock, ZoneId zoneId, Preset preset, int worldAgeDaysOverride, int archiveLimit)
	{
		this(catalog, populationCatalog, store, historicalPort(historical), materialized, safeBoundary, clock, zoneId, preset, worldAgeDaysOverride, archiveLimit);
	}

	private static HistoricalPort historicalPort(PhantomHistoricalBackgroundService historical)
	{
		final PhantomHistoricalBackgroundService delegate = Objects.requireNonNull(historical, "Historical Background service must not be null.");
		return new HistoricalPort()
		{
			@Override
			public Optional<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot> status(long profileId)
			{
				return delegate.status(profileId);
			}

			@Override
			public PhantomHistoricalBackgroundService.Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
			{
				return delegate.begin(profileId, fromEpochMinute, targetEpochMinute, deterministicSeed);
			}

			@Override
			public PhantomHistoricalBackgroundService.Result advance(long profileId, int maximumIntervals, int maximumMinutes)
			{
				return delegate.advance(profileId, maximumIntervals, maximumMinutes);
			}
		};
	}

	public PhantomPopulationEcologyService(PhantomPopulationEcologyCatalog catalog, PhantomPopulationCatalog populationCatalog, PersistencePort store, HistoricalPort historical, LongPredicate materialized, SafeBoundary safeBoundary, Clock clock, ZoneId zoneId, Preset preset, int worldAgeDaysOverride, int archiveLimit)
	{
		this(catalog, populationCatalog, store, historical, materialized, safeBoundary, clock, zoneId, preset, worldAgeDaysOverride, archiveLimit, worker -> { ThreadPool.execute(worker); return true; });
	}

	public PhantomPopulationEcologyService(PhantomPopulationEcologyCatalog catalog, PhantomPopulationCatalog populationCatalog, PersistencePort store, HistoricalPort historical, LongPredicate materialized, SafeBoundary safeBoundary, Clock clock, ZoneId zoneId, Preset preset, int worldAgeDaysOverride, int archiveLimit, Dispatcher dispatcher)
	{
		_dispatcher = Objects.requireNonNull(dispatcher);
		_catalog = Objects.requireNonNull(catalog, "Ecology catalog must not be null.");
		_metadataProfileBudget = _catalog.limits().maximumProfilesPerPulse();
		_populationCatalog = Objects.requireNonNull(populationCatalog, "Population catalog must not be null.");
		_store = Objects.requireNonNull(store, "Ecology store must not be null.");
		_historical = Objects.requireNonNull(historical, "Historical Background service must not be null.");
		_materialized = Objects.requireNonNull(materialized, "Materialization lookup must not be null.");
		_safeBoundary = Objects.requireNonNull(safeBoundary, "Ecology safe boundary must not be null.");
		_clock = Objects.requireNonNull(clock, "Ecology clock must not be null.");
		_zoneId = Objects.requireNonNull(zoneId, "Ecology time zone must not be null.");
		_preset = Objects.requireNonNull(preset, "Ecology preset must not be null.");
		_catalog.requirePreset(preset);
		if ((worldAgeDaysOverride < -1) || (worldAgeDaysOverride > 3650) || (archiveLimit < 1) || (archiveLimit > 1_000_000))
		{
			throw new IllegalArgumentException("Ecology operator settings are outside bounds.");
		}
		_worldAgeDaysOverride = worldAgeDaysOverride;
		_archiveLimit = archiveLimit;
	}

	public void installRuntime(PopulationView populationView, PopulationEvents populationEvents)
	{
		synchronized (_monitor)
		{
			if ((_populationView != null) || (_populationEvents != null) || !_entries.isEmpty())
			{
				throw new IllegalStateException("Ecology runtime can only be installed once before population restore.");
			}
			_populationView = Objects.requireNonNull(populationView, "Population view must not be null.");
			_populationEvents = Objects.requireNonNull(populationEvents, "Population events must not be null.");
		}
	}

	public void enablePeriodicDueMode()
	{
		synchronized (_monitor)
		{
			if (_periodicDueMode || !_entries.isEmpty())
			{
				throw new IllegalStateException("Periodic due mode must be enabled once before population restore.");
			}
			_periodicDueMode = true;
		}
	}

	/** Metadata uses the configured existing profile allowance, independently of history limits. */
	public void installMetadataBudget(int maximumProfiles)
	{
		if ((maximumProfiles < 1) || (maximumProfiles > 256)) { throw new IllegalArgumentException("Ecology metadata allowance is outside 1..256."); }
		synchronized (_monitor)
		{
			if (_metadataBudgetInstalled || !_entries.isEmpty()) { throw new IllegalStateException("Metadata allowance must be installed once before restore."); }
			_metadataProfileBudget = maximumProfiles;
			_metadataBudgetInstalled = true;
		}
	}

	/** Optional passive evidence hook; installation cannot affect metadata/history admission. */
	public void installMetadataObserver(Consumer<MetadataObservation> observer)
	{
		synchronized (_monitor)
		{
			if ((_metadataObserver != null) || !_entries.isEmpty()) { throw new IllegalStateException("Metadata observer must be installed once before restore."); }
			_metadataObserver = Objects.requireNonNull(observer);
		}
	}

	public void installMaterializationDemand(LongPredicate currentDemand)
	{
		synchronized (_monitor) { _currentDemand = Objects.requireNonNull(currentDemand); }
	}

	public record DemandFact(long profileId, long positionRevision, boolean couldKnow, long distanceSquared, long firstDemandNanos) {}
	private static final Comparator<DemandFact> DEMAND_ORDER = Comparator.comparingInt((DemandFact fact) -> fact.couldKnow() ? 0 : 1).thenComparingLong(DemandFact::distanceSquared).thenComparingLong(DemandFact::firstDemandNanos).thenComparingLong(DemandFact::profileId);

	/** Physical facts do not imply an expensive preparation lease. */
	public void updateMaterializationDemand(List<DemandFact> facts, int availablePreparationSlots)
	{
		final Set<Long> live = new HashSet<>();
		for (DemandFact fact : facts) { if (_materialized.test(fact.profileId())) { live.add(fact.profileId()); } }
		synchronized (_monitor)
		{
			_batchAdmission = true;
			_preparationSlots = Math.max(0, Math.min(8, availablePreparationSlots));
			final Map<Long, DemandFact> previous = new HashMap<>(_demandFacts);
			_demandFacts.clear();
			for (DemandFact fact : facts)
			{
				final Entry entry = _entries.get(fact.profileId());
				if ((entry == null) || !entry._participating) { continue; }
				final DemandFact old = previous.get(fact.profileId());
				_demandFacts.put(fact.profileId(), old == null ? fact : new DemandFact(fact.profileId(), fact.positionRevision(), fact.couldKnow(), fact.distanceSquared(), old.firstDemandNanos()));
				entry._liveOwner = live.contains(fact.profileId());
				entry._materializationDemand = true;
				entry._requestedMinute = Math.max(entry._requestedMinute, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
			}
			for (long id : previous.keySet()) { if (!_demandFacts.containsKey(id)) { final Entry entry = _entries.get(id); if (entry != null) { entry._materializationDemand = false; } } }
			rebuildAdmissionLocked();
		}
	}

	private void rebuildAdmissionLocked()
	{
		_admittedPreparation.clear();
		_demandFacts.values().stream().filter(fact -> { final Entry entry = _entries.get(fact.profileId()); return entry != null && entry._participating && !entry._liveOwner && !entry._terminal && needsWorkLocked(entry) && progressClock() >= entry._nextRetryPulse; })
			.sorted(DEMAND_ORDER).limit(_preparationSlots).forEach(fact -> _admittedPreparation.add(fact.profileId()));
		for (long id : List.copyOf(_materializationQueued))
		{
			if (!_admittedPreparation.contains(id)) { _materializationQueued.remove(id); _materializationDue.remove(id); queueLocked(id); }
		}
		for (long id : _admittedPreparation) { queueMaterializationLocked(id, false); }
	}

	/** One service timer, sharing the population budget; the wake itself never performs I/O. */
	public void installDemandPump(long quantumMillis, LongSupplier monotonicMillis, WakeScheduler scheduler)
	{
		synchronized (_monitor)
		{
			if ((_wakeScheduler != null) || !_entries.isEmpty() || (quantumMillis < 1)) { throw new IllegalStateException("Ecology pump must be installed before restore."); }
			_quantumMillis = quantumMillis;
			_monotonicMillis = Objects.requireNonNull(monotonicMillis);
			_wakeScheduler = Objects.requireNonNull(scheduler);
		}
	}

	private void requestWakeLocked()
	{
		if ((_wakeScheduler == null) || _stopping || (_wakeFailure != null) || (_cancelWake != null)) { return; }
		final long now = _monotonicMillis.getAsLong();
		final boolean runnable = !_due.isEmpty() || !_materializationDue.isEmpty();
		if (!runnable && _delayed.isEmpty() && !_periodicDueMode) { return; }
		final long upkeep = now + Math.max(1, MINUTE_MILLIS - Math.floorMod(_clock.millis(), MINUTE_MILLIS));
		final long idleDue = _delayed.isEmpty() ? upkeep : Math.min(upkeep, _delayed.firstKey());
		final long due = _workerInFlight ? _workerQueuedMillis + 256 * _quantumMillis : runnable ? Math.max(now, _nextBatchMillis) : Math.max(_nextBatchMillis, idleDue);
		final long generation = ++_wakeGeneration;
		_nextWakeMillis = Math.max(now + (_workerStarted ? _quantumMillis : 0), due);
		try { _cancelWake = Objects.requireNonNull(_wakeScheduler.schedule(() -> pumpWake(generation), Math.max(1, _nextWakeMillis - now))); }
		catch (RuntimeException exception)
		{
			_nextWakeMillis = 0;
			_wakeFailure = "ecology.wake_rejected";
			recordFailure(_wakeFailure);
		}
	}

	private void pumpWake(long generation)
	{
		synchronized (_monitor)
		{
			if (_stopping || (generation != _wakeGeneration)) { return; }
			_cancelWake = null;
			_nextWakeMillis = 0;
		}
		dispatchBatch();
	}

	public void register(ManagedSnapshot population)
	{
		Objects.requireNonNull(population, "Population snapshot must not be null.");
		synchronized (_monitor)
		{
			requireRuntime();
			final long profileId = population.profile().profileId();
			final Entry entry = new Entry();
			entry._participating = population.state().state() == PhantomPopulationState.State.READY;
			if (_entries.putIfAbsent(profileId, entry) != null)
			{
				return;
			}
			_unloadedEntries++;
			if (!entry._participating) { _pausedEntries++; }
			_inventoryReady = false;
			_replacementInventoryBuilt = false;
			queueLocked(profileId);
		}
	}

	/** Memory-only publication; never calls back into population while holding this monitor. */
	public void updateParticipation(long profileId, boolean participating)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry == null) { return; }
			if (entry._participating != participating) { _pausedEntries += participating ? -1 : 1; }
			entry._participating = participating;
			entry._readinessRevision++;
			if (!participating)
			{
			entry._materializationDemand = false;
			_demandFacts.remove(profileId); _admittedPreparation.remove(profileId);
				_queued.remove(profileId); _due.remove(profileId);
				_materializationQueued.remove(profileId); _materializationDue.remove(profileId);
				_demandFacts.remove(profileId); _admittedPreparation.remove(profileId); removeDelayLocked(profileId);
			}
			queueLocked(profileId);
		}
	}

	/** The executing claim retains ownership until its finally; saved pending is pausable. */
	public void holdStartupPopulationPlan() { synchronized (_monitor) { _populationPlanApplied = false; } }
	public void startupPopulationPlanApplied() { synchronized (_monitor) { _populationPlanApplied = true; requestWakeLocked(); } }

	public boolean pauseForRetirement(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && entry._claimed) { return false; }
			updateParticipation(profileId, false);
			return true;
		}
	}

	public PhantomPopulationEcologyState planNew(long generation, long ordinal, long deterministicSeed, long nowEpochMinute)
	{
		final long replacement;
		synchronized (_monitor)
		{
			replacement = _replacementQueue.isEmpty() ? 0 : _replacementQueue.peekFirst();
		}
		return _catalog.assign(_preset, generation, ordinal, deterministicSeed, nowEpochMinute, _worldAgeDaysOverride, replacement, null);
	}

	public StoredState attachNew(ManagedSnapshot population, PhantomPopulationEcologyState planned)
	{
		if (!population.state().scheduleTemplate().equals(planned.scheduleTemplate()))
		{
			throw new IllegalArgumentException("New population shell and ecology schedule differ.");
		}
		final StoredState stored = _store.insert(population.profile().profileId(), planned);
		synchronized (_monitor)
		{
			Entry entry = _entries.get(population.profile().profileId());
			if (entry == null)
			{
				entry = new Entry();
				_entries.put(population.profile().profileId(), entry);
				_unloadedEntries++;
			}
			publishLocked(entry, stored);
			if ((stored.state().replacesProfileId() > 0) && !_replacementQueue.isEmpty() && (_replacementQueue.peekFirst() == stored.state().replacesProfileId()))
			{
				_replacementQueue.removeFirst();
			}
			queueLocked(population.profile().profileId());
			refreshInventoryLocked();
		}
		return stored;
	}

	public void onPopulationPulse()
	{
		synchronized (_monitor)
		{
			if (_wakeScheduler != null) { requestWakeLocked(); return; }
		}
		dispatchBatch();
	}

	private void dispatchBatch()
	{
		final long generation;
		synchronized (_monitor)
		{
			requireRuntime();
			if (_stopping) { return; }
			_pulses++;
			refreshRunnableLocked();
			if (_workerInFlight && !_workerStarted && ((_wakeScheduler == null) ? ((_pulses - _workerQueuedPulse) >= 256) : (_monotonicMillis.getAsLong() >= _workerQueuedMillis + 256 * _quantumMillis)))
			{
				_workerInFlight = false;
				_workerGeneration++;
				_lastFailure = "ecology.worker_dispatch_timeout";
			}
			if (_workerInFlight || (_due.isEmpty() && _materializationDue.isEmpty() && _metadataDue.isEmpty())) { requestWakeLocked(); return; }
			if ((_wakeScheduler != null) && (_monotonicMillis.getAsLong() < _nextBatchMillis)) { requestWakeLocked(); return; }
			_workerInFlight = true;
			_workerStarted = false;
			_workerQueuedPulse = _pulses;
			if (_wakeScheduler != null) { _workerQueuedMillis = _monotonicMillis.getAsLong(); _nextBatchMillis = _workerQueuedMillis + _quantumMillis; }
			generation = ++_workerGeneration;
		}
		boolean accepted = false;
		try
		{
			accepted = _dispatcher.dispatch(() ->
			{
				synchronized (_monitor)
				{
					if (_stopping || ! _workerInFlight || (generation != _workerGeneration)) { return; }
					_workerStarted = true;
				}
				try { drainBatch(); }
				finally
				{
					synchronized (_monitor)
					{
						if (generation == _workerGeneration)
						{
							_workerInFlight = false; _workerStarted = false; _activeProfile = 0; _metadataDraining = false;
							if (_wakeScheduler != null) { _nextBatchMillis = _monotonicMillis.getAsLong() + _quantumMillis; }
							cancelWakeLocked(); requestWakeLocked();
						}
						_monitor.notifyAll();
					}
				}
			});
		}
		catch (RuntimeException exception) { recordFailure("ecology.worker_rejected"); }
		if (!accepted)
		{
			synchronized (_monitor) { if ((generation == _workerGeneration) && !_workerStarted) { _workerInFlight = false; _workerGeneration++; } _lastFailure = "ecology.worker_rejected"; requestWakeLocked(); }
		}
		else { synchronized (_monitor) { requestWakeLocked(); } }
	}

	private void cancelWakeLocked()
	{
		if (_cancelWake != null) { _cancelWake.run(); _cancelWake = null; }
		_wakeGeneration++; _nextWakeMillis = 0;
	}

	public void beginStop()
	{
		synchronized (_monitor)
		{
			_stopping = true;
			cancelWakeLocked();
			if (!_workerStarted) { _workerInFlight = false; _workerGeneration++; }
		}
	}

	/** Pollable drain fence: a running canonical commit retains ownership until finally. */
	public boolean finishStop()
	{
		synchronized (_monitor) { return _stopping && !_workerInFlight; }
	}

	private void drainBatch()
	{
		final Set<Long> profiles = new HashSet<>();
		final boolean inventoryReadyBefore;
		final boolean metadataBatch;
		final int profileBudget;
		final long started = System.nanoTime();
		long focus = 0;
		synchronized (_monitor)
		{
			requireRuntime();
			inventoryReadyBefore = _inventoryReady;
			metadataBatch = !_inventoryReady;
			_metadataDraining = metadataBatch;
			profileBudget = metadataBatch ? _metadataProfileBudget : (_populationPlanApplied ? _catalog.limits().maximumProfilesPerPulse() : 0);
			if (!metadataBatch && _populationPlanApplied)
			{
				if (_batchAdmission) { rebuildAdmissionLocked(); }
				focus = selectFocusLocked();
			}
		}
		int intervalsRemaining = _catalog.limits().maximumIntervalsPerPulse();
		int intervals = 0;
		boolean ordinaryServed = false;
		while ((profiles.size() < profileBudget) && (intervalsRemaining > 0))
		{
			final long profileId;
			final int slice;
			synchronized (_monitor)
			{
				if (_stopping) { break; }
				if (metadataBatch)
				{
					profileId = pollMetadataClaimLocked(profiles);
					slice = 0;
				}
				else if (profiles.isEmpty() && (focus > 0))
				{
					profileId = claimLocked(focus, profiles) ? focus : 0;
					slice = Math.min(intervalsRemaining, _due.isEmpty() ? intervalsRemaining : Math.max(1, _catalog.limits().maximumIntervalsPerPulse() - 4));
				}
				else
				{
					final long ordinary = !ordinaryServed ? pollClaimLocked(_due, _queued, profiles) : 0;
					ordinaryServed |= ordinary > 0;
					final long urgent = ordinary > 0 ? ordinary : pollClaimLocked(_materializationDue, _materializationQueued, profiles);
					profileId = urgent == 0 ? pollClaimLocked(_due, _queued, profiles) : urgent;
					slice = (focus > 0 && ordinary > 0) ? Math.min(4, intervalsRemaining) : intervalsRemaining;
				}
				if (profileId == 0) { if (focus > 0 && profiles.isEmpty()) { focus = 0; continue; } break; }
				profiles.add(profileId);
				_activeProfile = profileId;
			}
			try
			{
				if (!metadataBatch) { publishSchedulingPermissionEdge(profileId); }
				if (!metadataBatch && (profileId == focus) && !_currentDemand.test(profileId)) { withdrawMaterializationDue(profileId); }
				final int used = metadataBatch ? process(profileId, 0, false, true) : processRequested(profileId, slice);
				intervals += used;
				intervalsRemaining -= used;
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null) { entry._advancedReceipt += used; }
				}
			}
			catch (RuntimeException exception)
			{
				// A durable inner commit can precede a failed outer save. Keep its receipt;
				// reserve the rest of this slice when an exception leaves progress uncertain.
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					final int committed = entry == null ? 0 : entry._claimCommittedIntervals;
					if (metadataBatch) { _metadataFailures++; }
					else
					{
						intervals += committed;
						intervalsRemaining -= Math.max(slice, committed);
						if (entry != null) { entry._advancedReceipt += committed; }
					}
				}
				recordFailure(typedFailure(exception));
				deferRetry(profileId);
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null) { entry._lastReportedFailure = typedFailure(exception); }
				}
			}
			finally
			{
				try { if (!metadataBatch) { publishSchedulingPermissionEdge(profileId); } }
				catch (RuntimeException exception) { recordFailure("ecology.readiness_callback_failed"); }
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null)
					{
						entry._claimed = false;
						_periodicRunning--;
						if (!entry._terminal && entry._materializationDemand && !_batchAdmission && !dueSnapshotLocked(profileId, entry).complete())
						{
							queueMaterializationLocked(profileId, false);
						}
						else if (!entry._terminal && ((entry._stored == null) || (entry._stored.state().disposition() == Disposition.MANAGED)))
						{
							queueLocked(profileId);
						}
					}
				}
			}
		}
		boolean inventoryBecameReady = false;
		synchronized (_monitor)
		{
			_lastPulseProfiles = metadataBatch ? 0 : profiles.size();
			_lastPulseIntervals = intervals;
			_maximumPulseProfiles = Math.max(_maximumPulseProfiles, _lastPulseProfiles);
			_maximumPulseIntervals = Math.max(_maximumPulseIntervals, _lastPulseIntervals);
			_profileOperations += _lastPulseProfiles;
			_lastBatchElapsedMillis = (System.nanoTime() - started) / 1_000_000L;
			_historicalIntervals += intervals;
			refreshInventoryLocked();
			inventoryBecameReady = !inventoryReadyBefore && _inventoryReady;
			_lastMetadataBatch = metadataBatch ? profiles.size() : 0;
			_metadataProfileOperations += _lastMetadataBatch;
			_maximumMetadataBatch = Math.max(_maximumMetadataBatch, _lastMetadataBatch);
		}
		publishMetadataObservation();
		synchronized (_monitor) { _metadataDraining = false; }
		if (inventoryBecameReady)
		{
			_populationEvents.reconcilePopulation();
		}
	}

	/** Immutable scalar copy under the monitor; the optional consumer runs after its release. */
	private void publishMetadataObservation()
	{
		final Consumer<MetadataObservation> observer;
		final MetadataObservation observation;
		synchronized (_monitor)
		{
			observer = _metadataObserver;
			if (observer == null) { return; }
			observation = new MetadataObservation(_entries.size(), _unloadedEntries, _metadataPublications, _lastMetadataBatch, _maximumMetadataBatch, _populationPlanApplied, _stopping, _inventoryReady, _historicalIntervals, _productiveMinutes, _calendarMinutes, _persistenceWrites, _maximumPulseProfiles, _maximumPulseIntervals, _metadataObserverFailure);
		}
		try { observer.accept(observation); }
		catch (RuntimeException | Error failure)
		{
			// An evidence consumer must not change an original worker outcome or readiness.
			synchronized (_monitor) { _metadataObserverFailure = failure.getClass().getName(); }
		}
	}

	private int processRequested(long profileId, int intervalLimit)
	{
		if (retryDeferred(profileId)) { return 0; }
		int advanced = 0;
		for (int steps = 0; steps < (intervalLimit * 4) + 8; steps++)
		{
			final StoredState before = stored(profileId);
			final long revision = dueSnapshot(profileId).revision();
			final boolean requested;
			synchronized (_monitor)
			{
				final Entry entry = _entries.get(profileId);
				requested = (entry != null) && (entry._requestedMinute > 0);
			}
			if (requested && (before != null) && before.state().initialCatchupComplete() && !before.state().requestPending() && dueSnapshot(profileId).complete()) { process(profileId, 0, false); break; }
			final int used = process(profileId, Math.max(0, intervalLimit - advanced), requested);
			// Ownership recovery is one worker turn; historical advance belongs to the next batch.
			if (used == OWNERSHIP_RECOVERY_TURN) { break; }
			advanced += used;
			if (((before == stored(profileId)) && (revision == dueSnapshot(profileId).revision())) || (advanced >= intervalLimit)) { break; }
		}
		return advanced;
	}

	private long selectFocusLocked()
	{
		final List<DemandFact> ranked = _demandFacts.values().stream().filter(fact -> _admittedPreparation.contains(fact.profileId())).sorted(DEMAND_ORDER).toList();
		long selected = ranked.isEmpty() ? (_materializationDue.isEmpty() ? 0 : _materializationDue.peekFirst()) : ranked.getFirst().profileId();
		if (!ranked.isEmpty() && (_focusBatches >= 8) && (_focusId == selected))
		{
			final DemandFact current = ranked.getFirst();
			selected = ranked.stream().filter(fact -> fact.profileId() != _focusId && fact.couldKnow() == current.couldKnow()).min(Comparator.comparingLong(DemandFact::firstDemandNanos).thenComparingLong(DemandFact::profileId)).map(DemandFact::profileId).orElse(selected);
		}
		if (selected != _focusId) { _focusId = selected; _focusBatches = 0; _focusStartedMillis = progressClock(); }
		if (selected > 0) { _focusBatches++; }
		return selected;
	}

	private boolean needsWorkLocked(Entry entry)
	{
		if (entry._stored == null) { return true; }
		if (!entry._participating || entry._liveOwner || entry._terminal || entry._stored.state().disposition() != Disposition.MANAGED) { return false; }
		if (entry._schedulingPublicationPending) { return true; }
		final var state = entry._stored.state();
		final long horizon = _periodicDueMode ? entry._requestedMinute : Math.max(0, _clock.millis() / MINUTE_MILLIS);
		final long now = Math.max(0, _clock.millis() / MINUTE_MILLIS);
		return state.requestPending() || !state.initialCatchupComplete() || state.calendarCursorEpochMinute() < horizon || (_populationPlanApplied && _inventoryReady && _archived < _archiveLimit && !entry._archiveRequested && now >= state.turnoverEligibleEpochMinute() && entry._lastArchiveProbeMinute != now);
	}

	private boolean claimLocked(long profileId, Set<Long> selected)
	{
		final Entry entry = _entries.get(profileId);
		if ((entry == null) || entry._claimed || selected.contains(profileId) || !needsWorkLocked(entry)) { return false; }
		if (progressClock() < entry._nextRetryPulse) { delayLocked(profileId, entry._nextRetryPulse); return false; }
		_queued.remove(profileId); _due.remove(profileId);
		_metadataQueued.remove(profileId); _metadataDue.remove(profileId);
		_materializationQueued.remove(profileId); _materializationDue.remove(profileId);
		entry._claimed = true; entry._claimCommittedIntervals = 0; _periodicRunning++;
		return true;
	}

	private long pollMetadataClaimLocked(Set<Long> selected)
	{
		final int count = _metadataDue.size();
		for (int index = 0; index < count && !_metadataDue.isEmpty(); index++)
		{
			final long id = _metadataDue.removeFirst(); _metadataQueued.remove(id);
			final Entry entry = _entries.get(id);
			if ((entry == null) || (entry._stored != null)) { continue; }
			if (selected.contains(id)) { _metadataDue.addLast(id); _metadataQueued.add(id); continue; }
			if (claimLocked(id, selected)) { return id; }
		}
		return 0;
	}

	private long pollClaimLocked(ArrayDeque<Long> queue, Set<Long> queued, Set<Long> selected)
	{
		final int count = queue.size();
		for (int index = 0; index < count && !queue.isEmpty(); index++)
		{
			final long id = queue.removeFirst(); queued.remove(id);
			if (selected.contains(id)) { queue.addLast(id); queued.add(id); continue; }
			if (claimLocked(id, selected)) { return id; }
		}
		return 0;
	}

	private void removeDelayLocked(long profileId)
	{
		final Long due = _delayedAt.remove(profileId);
		if (due != null) { final Set<Long> ids = _delayed.get(due); if (ids != null) { ids.remove(profileId); if (ids.isEmpty()) { _delayed.remove(due); } } }
	}

	private void delayLocked(long profileId, long due)
	{
		removeDelayLocked(profileId); _delayedAt.put(profileId, due); _delayed.computeIfAbsent(due, _ -> new HashSet<>()).add(profileId);
		_queued.remove(profileId); _due.remove(profileId); _materializationQueued.remove(profileId); _materializationDue.remove(profileId);
		_metadataQueued.remove(profileId); _metadataDue.remove(profileId);
		requestWakeLocked();
	}

	private void refreshRunnableLocked()
	{
		final long now = Math.max(0, _clock.millis() / MINUTE_MILLIS);
		if (now != _refreshedMinute)
		{
			_refreshedMinute = now;
			for (var pair : _entries.entrySet())
			{
				final Entry entry = pair.getValue();
				if (entry._participating && _periodicDueMode) { if (now > entry._requestedMinute) { entry._requestedMinute = now; entry._readinessRevision++; } }
				if (entry._participating && (entry._stored != null)) { entry._liveOwner = false; }
				queueLocked(pair.getKey());
			}
		}
		while (!_delayed.isEmpty() && _delayed.firstKey() <= progressClock())
		{
			final var due = _delayed.pollFirstEntry();
			for (long id : due.getValue()) { _delayedAt.remove(id); queueLocked(id); }
		}
		if (_batchAdmission) { rebuildAdmissionLocked(); }
	}

	/** Compatibility entry point; it only requests work and never drains inline. */
	public DueReconciliation reconcileBackgroundDue(long profileId)
	{
		return requestBackgroundDue(profileId);
	}

	/** Continues an incomplete local materialization due inside the existing pulse budgets. */
	public DueReconciliation reconcileMaterializationDue(long profileId)
	{
		return requestMaterializationDue(profileId);
	}

	public DueReconciliation requestBackgroundDue(long profileId)
	{
		return requestDue(profileId, false, true);
	}

	/** Admission gate must not consume the receipt owned by the periodic farm handler. */
	public DueReconciliation requestBackgroundReadiness(long profileId)
	{
		return requestDue(profileId, false, false);
	}

	public DueReconciliation requestMaterializationDue(long profileId)
	{
		return requestDue(profileId, true, false);
	}

	public void withdrawMaterializationDue(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._materializationDemand = false;
				_materializationQueued.remove(profileId);
				_materializationDue.remove(profileId);
				queueLocked(profileId);
			}
		}
	}

	private DueReconciliation requestDue(long profileId, boolean materializationDue, boolean consumeReceipt)
	{
		final var due = registerDue(profileId, materializationDue, consumeReceipt);
		publishSchedulingPermissionEdge(profileId);
		return due;
	}

	private DueReconciliation registerDue(long profileId, boolean materializationDue, boolean consumeReceipt)
	{
		final long targetMinute = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		synchronized (_monitor)
		{
			requireRuntime();
			_periodicDueCalls++;
			final Entry entry = _entries.get(profileId);
			if (entry == null)
			{
				_periodicBlockedCalls++;
				return new DueReconciliation(false, 0, "ecology.profile_unknown");
			}
			if (_stopping) { return new DueReconciliation(false, 0, "ecology.stopping"); }
			if (!entry._participating) { return new DueReconciliation(false, 0, "ecology.population_paused"); }
			if ((entry._stored != null) && entry._stored.state().initialCatchupComplete() && (targetMinute - entry._stored.state().calendarCursorEpochMinute() > 15))
			{
				_periodicOverdueCalls++;
			}
			if (targetMinute > entry._requestedMinute) { entry._requestedMinute = targetMinute; entry._readinessRevision++; }
			final var state = entry._stored == null ? null : entry._stored.state();
			final var historical = entry._historicalSnapshot == null ? null : entry._historicalSnapshot.state();
			// Foreground supplies the missing native context; the pending history remains fenced.
			if (materializationDue && _currentDemand.test(profileId) && !entry._terminal && (_wakeFailure == null) && _populationPlanApplied && _inventoryReady && !_metadataDraining
				&& (state != null) && (state.disposition() == Disposition.MANAGED) && state.requestPending() && (historical != null)
				&& historical.requestId().equals(state.currentRequestId()) && (historical.fromEpochMinute() == state.calendarCursorEpochMinute()) && (historical.targetEpochMinute() == state.currentWindowTargetEpochMinute())
				&& (historical.status() != Status.COMPLETE) && PhantomHistoricalBackgroundService.requiresNativeMaterialization(entry._lastReportedFailure))
			{
				entry._materializationDemand = true;
				return new DueReconciliation(true, 0, "ecology.native_materialization_required", org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest.nativeContextHandoff(historical.requestId()));
			}
			final DueSnapshot snapshot = dueSnapshotLocked(profileId, entry);
			if (!snapshot.complete() && !entry._terminal)
			{
				_periodicBlockedCalls++;
				entry._materializationDemand |= materializationDue;
				if (entry._materializationDemand) { queueMaterializationLocked(profileId, false); }
				else { queueLocked(profileId); }
			}
			final int receipt = snapshot.complete() && consumeReceipt ? entry._advancedReceipt : 0;
			if (snapshot.complete() && consumeReceipt) { entry._advancedReceipt = 0; }
			return new DueReconciliation(snapshot.complete(), receipt, snapshot.reason());
		}
	}

	public DueSnapshot dueSnapshot(long profileId)
	{
		synchronized (_monitor) { return dueSnapshotLocked(profileId, _entries.get(profileId)); }
	}

	private DueSnapshot dueSnapshotLocked(long profileId, Entry entry)
	{
		final var stored = entry == null ? null : entry._stored;
		final var state = stored == null ? null : stored.state();
		final long horizon = entry == null ? 0 : entry._requestedMinute;
		final String stateReason = _stopping ? "ecology.stopping" : _wakeFailure != null ? _wakeFailure : entry == null ? "ecology.profile_unknown" : !entry._participating ? "ecology.population_paused" : (state == null) || !_populationPlanApplied || !_inventoryReady || _metadataDraining ? "ecology.inventory_pending" : state.disposition() != Disposition.MANAGED ? "ecology.archived" : entry._lastReportedFailure != null ? entry._lastReportedFailure : state.requestPending() ? "ecology.commit_pending" : !state.initialCatchupComplete() ? "ecology.initial_catchup_pending" : state.calendarCursorEpochMinute() < horizon ? "ecology.cursor_pending" : "ecology.cursor_current";
		final String reason = (entry != null) && !entry._terminal && entry._participating && entry._materializationDemand && _batchAdmission && !_admittedPreparation.contains(profileId) && !"ecology.cursor_current".equals(stateReason) ? "ecology.preparation_capacity" : stateReason;
		return new DueSnapshot(profileId, horizon, state == null ? 0 : state.calendarCursorEpochMinute(), (state != null) && state.initialCatchupComplete(), (state != null) && state.requestPending(), _queued.contains(profileId) || _materializationQueued.contains(profileId), (entry != null) && entry._claimed, entry == null ? 0 : entry._readinessRevision, "ecology.cursor_current".equals(reason), reason);
	}

	private int process(long profileId, int intervalBudget)
	{
		return process(profileId, intervalBudget, false);
	}

	private int process(long profileId, int intervalBudget, boolean periodicDue)
	{
		return process(profileId, intervalBudget, periodicDue, false);
	}

	private int process(long profileId, int intervalBudget, boolean periodicDue, boolean forceMetadata)
	{
		final boolean metadataOnly;
		synchronized (_monitor) { metadataOnly = forceMetadata || !_populationPlanApplied || !_inventoryReady || _metadataDraining; }
		final ManagedSnapshot population = _populationView.find(profileId).orElse(null);
		if (population == null)
		{
			synchronized (_monitor)
			{
				removeLocked(profileId);
			}
			return 0;
		}
		StoredState stored = stored(profileId);
		if (stored == null)
		{
			stored = _store.load(profileId).orElse(null);
			if (stored == null)
			{
				final long assignedAt = Math.max(0, population.profile().createdAt().toEpochMilli() / MINUTE_MILLIS);
				final PhantomPopulationEcologyState assignment = _catalog.assign(_preset, population.state().populationGeneration(), population.state().creationOrdinal(), population.state().deterministicSeed(), assignedAt, _worldAgeDaysOverride, 0, population.state().scheduleTemplate());
				stored = _store.insert(profileId, assignment);
				recordWrite();
			}
			if (!stored.state().scheduleTemplate().equals(population.state().scheduleTemplate()))
			{
				throw new IllegalStateException("ecology.schedule_assignment_conflict");
			}
			publish(profileId, stored);
			if (forceMetadata) { clearReportedFailure(profileId); }
		}
		final PhantomPopulationEcologyState state = stored.state();
		// The final metadata publication cannot fall through to history in this batch.
		synchronized (_monitor) { if (metadataOnly || !_populationPlanApplied || !_inventoryReady || _metadataDraining || !_entries.get(profileId)._participating) { return 0; } }
		if ((state.disposition() == Disposition.ARCHIVED) || (population.state().state() != PhantomPopulationState.State.READY))
		{
			return 0;
		}
		if (state.requestPending())
		{
			if (_materialized.test(profileId)) { synchronized (_monitor) { _entries.get(profileId)._liveOwner = true; } recordFailureOnce(profileId, "ecology.live_owner"); return 0; }
			return advanceRequest(profileId, stored, intervalBudget);
		}
		if (recoverOrphanHistoricalOwnership(profileId, stored)) { return OWNERSHIP_RECOVERY_TURN; }
		final long now = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		if (_materialized.test(profileId))
		{
			synchronized (_monitor) { _entries.get(profileId)._liveOwner = true; }
			if (now > state.calendarCursorEpochMinute())
			{
				persist(profileId, stored, state.advanceCalendar(now));
			}
			return 0;
		}
		if (_periodicDueMode && state.initialCatchupComplete() && !periodicDue)
		{
			if (now >= state.turnoverEligibleEpochMinute()) { requestArchive(profileId); }
			return 0;
		}
		final long requested;
		synchronized (_monitor) { requested = _entries.get(profileId)._requestedMinute; }
		final long horizon = periodicDue ? Math.min(now, requested) : now;
		final long reconciliationTarget = state.initialCatchupComplete() ? horizon : Math.min(now, state.initialTargetEpochMinute());
		if (state.calendarCursorEpochMinute() < reconciliationTarget)
		{
			return beginNextWindow(profileId, population, stored, reconciliationTarget);
		}
		if ((now >= state.turnoverEligibleEpochMinute()) && !_materialized.test(profileId))
		{
			requestArchive(profileId);
		}
		return 0;
	}

	/** Reattach only the exact outer owner; the historical component remains untouched. */
	private boolean recoverOrphanHistoricalOwnership(long profileId, StoredState ecology)
	{
		stage(profileId, "historical.status");
		final var existing = _historical.status(profileId).orElse(null);
		cacheHistorical(profileId, existing);
		if ((existing == null) || (existing.state().status() == Status.COMPLETE)) { return false; }
		final var state = ecology.state();
		final var historical = existing.state();
		final var recorder = PhantomRuntimeFlightRecorder.getInstance();
		recorder.record(profileId, "ECOLOGY_ORPHAN_FOUND", historical.status().name(), "", "", state.calendarCursorEpochMinute(), historical.targetEpochMinute(), ecology.rowVersion());
		if ((historical.fromEpochMinute() != state.calendarCursorEpochMinute())
			|| (historical.targetEpochMinute() <= state.calendarCursorEpochMinute())
			|| (historical.deterministicSeed() != historicalSeed(state)))
		{
			recordFailureOnce(profileId, "ecology.orphan_historical_conflict");
			synchronized (_monitor) { final Entry entry = _entries.get(profileId); if (entry != null) { entry._terminal = true; } }
			recorder.record(profileId, "ECOLOGY_ORPHAN_CONFLICT", historical.status().name(), "", "ecology.orphan_historical_conflict", historical.fromEpochMinute(), historical.targetEpochMinute(), state.calendarCursorEpochMinute());
			return true;
		}
		if (_materialized.test(profileId) || !_safeBoundary.blockingReason(profileId).isEmpty())
		{
			deferRetry(profileId);
			return true;
		}
		// A previous failed CAS may have left our cached outer snapshot behind the durable row.
		final StoredState current = _store.load(profileId).orElse(null);
		if (!ecology.equals(current))
		{
			if (current != null) { publish(profileId, current); }
			deferRetry(profileId);
			return true;
		}
		final ManagedSnapshot population = _populationView.find(profileId).orElse(null);
		if ((population == null) || (population.state().state() != PhantomPopulationState.State.READY)
			|| _materialized.test(profileId) || !_safeBoundary.blockingReason(profileId).isEmpty())
		{
			deferRetry(profileId);
			return true;
		}
		final StoredState adopted = persist(profileId, ecology, state.beginRequest(historical.requestId(), historical.targetEpochMinute()));
		recorder.record(profileId, "ECOLOGY_ORPHAN_ADOPTED", historical.status().name(), "MANAGED", "", state.calendarCursorEpochMinute(), historical.targetEpochMinute(), adopted.rowVersion());
		return true;
	}

	private int advanceRequest(long profileId, StoredState ecology, int intervalBudget)
	{
		final PhantomPopulationEcologyState state = ecology.state();
		stage(profileId, "historical.status");
		var catchup = _historical.status(profileId).orElse(null);
		cacheHistorical(profileId, catchup);
		if ((catchup == null) || !catchup.state().requestId().equals(state.currentRequestId()))
		{
			final var begun = _historical.begin(profileId, state.calendarCursorEpochMinute(), state.currentWindowTargetEpochMinute(), historicalSeed(state));
			cacheHistorical(profileId, begun.snapshot());
			if (!begun.successful() || (begun.snapshot() == null) || !begun.snapshot().state().requestId().equals(state.currentRequestId()))
			{
				historicalFailure(profileId, begun, "ecology.request_identity_conflict");
				return 0;
			}
			catchup = begun.snapshot();
		}
		if ((catchup.state().fromEpochMinute() != state.calendarCursorEpochMinute()) || (catchup.state().targetEpochMinute() != state.currentWindowTargetEpochMinute()))
		{
			recordFailureOnce(profileId, "ecology.request_window_conflict");
			synchronized (_monitor) { _entries.get(profileId)._terminal = true; }
			return 0;
		}
		if (catchup.state().status() == Status.COMPLETE)
		{
			persist(profileId, ecology, state.completeRequest());
			return 0;
		}
		if (retryDeferred(profileId))
		{
			return 0;
		}
		if (intervalBudget <= 0)
		{
			return 0;
		}
		if (catchup.state().status() == Status.FAILED_REPLAN_REQUIRED)
		{
			recordFailureOnce(profileId, catchup.state().failureReason());
			if (!failedProbeDue(profileId))
			{
				return 0;
			}
		}
		final int bounded = Math.min(intervalBudget, _catalog.limits().maximumIntervalsPerPulse());
		stage(profileId, "historical.advance");
		final var advanced = _historical.advance(profileId, bounded, bounded);
		cacheHistorical(profileId, advanced.snapshot());
		// Publish committed accounting before any subsequent callback or ecology save.
		final int committed = (advanced.snapshot() != null) && advanced.snapshot().state().requestId().equals(catchup.state().requestId())
			? Math.toIntExact(Math.max(advanced.advancedIntervals(), advanced.snapshot().state().intervalOrdinal() - catchup.state().intervalOrdinal())) : advanced.advancedIntervals();
		if (committed > 0)
		{
			synchronized (_monitor) { _productiveMinutes += committed; final Entry entry = _entries.get(profileId); if (entry != null) { entry._claimCommittedIntervals += committed; } }
		}
		if ((advanced.status() != ResultStatusCode.SUCCESS) && (advanced.status() != ResultStatusCode.RETRY))
		{
			historicalFailure(profileId, advanced, "ecology.historical_failure");
		}
		else if (advanced.status() == ResultStatusCode.SUCCESS)
		{
			clearReportedFailure(profileId);
		}
		if (advanced.status() == ResultStatusCode.RETRY) { historicalFailure(profileId, advanced, "ecology.historical_retry"); }
		if ((advanced.snapshot() != null) && (advanced.snapshot().state().status() == Status.COMPLETE))
		{
			persist(profileId, ecology, state.completeRequest());
		}
		return committed;
	}

	private int beginNextWindow(long profileId, ManagedSnapshot population, StoredState stored, long reconciliationTarget)
	{
		PhantomPopulationEcologyState state = stored.state();
		final long scanLimit = Math.min(reconciliationTarget, Math.addExact(state.calendarCursorEpochMinute(), _catalog.limits().maximumCalendarDaysPerPulse() * 1440L));
		final Window window = nextProductiveWindow(state, population.state().schedulePhaseMinutes(), scanLimit);
		if (window.startEpochMinute() > state.calendarCursorEpochMinute())
		{
			final long skipped = window.startEpochMinute() - state.calendarCursorEpochMinute();
			stored = persist(profileId, stored, state.advanceCalendar(window.startEpochMinute()));
			state = stored.state();
			synchronized (_monitor)
			{
				_calendarMinutes += skipped;
			}
		}
		if (!window.productive())
		{
			return 0;
		}
		final var begun = _historical.begin(profileId, window.startEpochMinute(), window.endEpochMinute(), historicalSeed(state));
		cacheHistorical(profileId, begun.snapshot());
		if (!begun.successful() || (begun.snapshot() == null))
		{
			if (begun.status() != ResultStatusCode.NORMAL_MATERIALIZED)
			{
				historicalFailure(profileId, begun, "ecology.historical_begin_failed");
			}
			return 0;
		}
		persist(profileId, stored, state.beginRequest(begun.snapshot().state().requestId(), window.endEpochMinute()));
		return 0;
	}

	public Window nextProductiveWindow(PhantomPopulationEcologyState state, int schedulePhaseMinutes, long targetEpochMinute)
	{
		long cursor = state.calendarCursorEpochMinute();
		int steps = 0;
		while ((cursor < targetEpochMinute) && (steps++ < MAXIMUM_CALENDAR_STEPS_PER_PROFILE))
		{
			final Instant instant = Instant.ofEpochSecond(Math.multiplyExact(cursor, 60L));
			final var evaluation = _populationCatalog.evaluate(state.scheduleTemplate(), instant, _zoneId, schedulePhaseMinutes);
			final long scheduleBoundary = Math.max(cursor + 1, Math.floorDiv(evaluation.nextBoundary().toEpochMilli() + MINUTE_MILLIS - 1, MINUTE_MILLIS));
			final long segmentEnd = Math.min(targetEpochMinute, scheduleBoundary);
			if ((evaluation.state() != PhantomActivityState.ACTIVE) && (evaluation.state() != PhantomActivityState.BACKGROUND))
			{
				cursor = segmentEnd;
				continue;
			}
			final long block = Math.floorDiv(cursor, state.productiveBlockMinutes());
			final long blockEnd = Math.min(segmentEnd, Math.multiplyExact(block + 1, state.productiveBlockMinutes()));
			if (_catalog.productive(state, block))
			{
				return new Window(cursor, blockEnd, true);
			}
			cursor = blockEnd;
		}
		return new Window(cursor, cursor, false);
	}

	private void requestArchive(long profileId)
	{
		synchronized (_monitor) { final Entry entry = _entries.get(profileId); if (entry != null) { entry._lastArchiveProbeMinute = Math.max(0, _clock.millis() / MINUTE_MILLIS); } }
		if (!_safeBoundary.blockingReason(profileId).isEmpty()) { return; }
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._stored == null) || entry._archiveRequested || !_populationPlanApplied || !_inventoryReady || _metadataDraining || (_archived >= _archiveLimit) || entry._stored.state().requestPending())
			{
				return;
			}
			entry._archiveRequested = true;
		}
		_populationEvents.requestArchive(profileId);
	}

	public Optional<ArchivedResult> archive(ManagedSnapshot population)
	{
		final long profileId = population.profile().profileId();
		final boolean blocked = _materialized.test(profileId) || !_safeBoundary.blockingReason(profileId).isEmpty();
		final StoredState ecology;
		final long generation;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || !entry._archiveRequested || (entry._stored == null) || (entry._stored.state().disposition() != Disposition.MANAGED) || entry._stored.state().requestPending() || !_populationPlanApplied || !_inventoryReady || _metadataDraining || (_archived >= _archiveLimit) || blocked)
			{
				if (entry != null)
				{
					entry._archiveRequested = false;
				}
				return Optional.empty();
			}
			ecology = entry._stored;
			generation = ++_archiveGeneration;
		}
		final long now = Math.max(ecology.state().assignedAtEpochMinute(), _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		final ArchivedResult archived = _store.archive(population, ecology, generation, now);
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._archiveRequested = false;
				publishLocked(entry, archived.ecology());
				_replacementQueue.addLast(profileId);
				_persistenceWrites++;
			}
		}
		return Optional.of(archived);
	}

	public void archiveCancelled(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._archiveRequested = false;
			}
		}
	}

	public boolean permitsScheduling(long profileId)
	{
		return permitsScheduling(profileId, _clock.instant());
	}

	public boolean permitsScheduling(long profileId, Instant instant)
	{
		final long now = Math.max(0, Objects.requireNonNull(instant, "Scheduling instant must not be null.").toEpochMilli() / MINUTE_MILLIS);
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry != null) && permitsSchedulingLocked(entry, now);
		}
	}

	private void publishSchedulingPermissionEdge(long profileId)
	{
		final boolean changed;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry == null)
			{
				return;
			}
			final long now = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
			final boolean permitted = permitsSchedulingLocked(entry, now);
			changed = permitted != entry._publishedSchedulingPermission;
			entry._publishedSchedulingPermission = permitted;
			if (_populationPlanApplied && _inventoryReady && !_metadataDraining) { entry._schedulingPublicationPending = false; }
		}
		if (changed)
		{
			_populationEvents.ecologyFenceChanged(profileId);
		}
	}

	private boolean permitsSchedulingLocked(Entry entry, long now)
	{
		return !_stopping && _populationPlanApplied && _inventoryReady && !_metadataDraining && entry._participating && (entry._stored != null) && (entry._stored.state().disposition() == Disposition.MANAGED) && entry._stored.state().initialCatchupComplete() && !entry._stored.state().requestPending() && (entry._stored.state().calendarCursorEpochMinute() >= entry._requestedMinute) && (_periodicDueMode || (entry._stored.state().calendarCursorEpochMinute() >= now));
	}

	public boolean managed(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry == null) || (entry._stored == null) || (entry._stored.state().disposition() == Disposition.MANAGED);
		}
	}

	public boolean returnable(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return _inventoryReady && (entry != null) && (entry._stored != null) && (entry._stored.state().disposition() == Disposition.MANAGED);
		}
	}

	public boolean inventoryReady()
	{
		synchronized (_monitor)
		{
			return _inventoryReady;
		}
	}

	public int archiveLimit()
	{
		return _archiveLimit;
	}

	public Optional<PhantomPopulationEcologyState> find(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry == null) || (entry._stored == null) ? Optional.empty() : Optional.of(entry._stored.state());
		}
	}

	public Snapshot snapshot()
	{
		final long now = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		synchronized (_monitor)
		{
			final PresetDefinition preset = _catalog.requirePreset(_preset);
			int newcomers = 0;
			int pending = 0;
			for (Entry entry : _entries.values())
			{
				if ((entry._stored != null) && (entry._stored.state().disposition() == Disposition.MANAGED))
				{
					newcomers += entry._stored.state().newcomer(now, preset.newcomerDays()) ? 1 : 0;
					pending += (!entry._stored.state().initialCatchupComplete() || entry._stored.state().requestPending()) ? 1 : 0;
				}
			}
			final String pause = _archived >= _archiveLimit ? "archive_limit" : (!_inventoryReady ? "inventory_loading" : "none");
			return new Snapshot(true, _preset, _catalog.hash(), _inventoryReady, _managed, _archived, newcomers, pending, pause, Map.copyOf(_paceHistogram), Map.copyOf(_personalityHistogram), Map.copyOf(_scheduleHistogram), _pulses, _profileOperations, _historicalIntervals, _productiveMinutes, _calendarMinutes, _persistenceWrites, _failures, _lastPulseProfiles, _lastPulseIntervals, _maximumPulseProfiles, _maximumPulseIntervals, _lastFailure, _periodicDueCalls, _periodicOverdueCalls, _periodicRunning, _periodicBlockedCalls, _entries.size(), _unloadedEntries, _metadataProfileBudget, _metadataProfileOperations, _metadataPublications, _metadataFailures, _lastMetadataBatch, _maximumMetadataBatch);
		}
	}

	public Map<Integer, Integer> initialPersonalityTraits(long profileId)
	{
		final PhantomPopulationEcologyState state = find(profileId).orElse(null);
		return (state == null) || (state.disposition() != Disposition.MANAGED) ? Map.of() : _catalog.personalityTraits(state, profileId);
	}

	private StoredState persist(long profileId, StoredState expected, PhantomPopulationEcologyState replacement)
	{
		stage(profileId, "ecology.persist");
		final StoredState saved = _store.save(profileId, expected, replacement);
		publish(profileId, saved);
		clearReportedFailure(profileId);
		recordWrite();
		return saved;
	}

	private StoredState stored(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return entry == null ? null : entry._stored;
		}
	}

	private void publish(long profileId, StoredState stored)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				publishLocked(entry, stored);
				refreshInventoryLocked();
			}
		}
	}

	private void publishLocked(Entry entry, StoredState stored)
	{
		if (entry._stored != null)
		{
			removeHistogramsLocked(entry._stored.state());
		}
		else
		{
			_unloadedEntries--;
			_metadataPublications++;
			entry._schedulingPublicationPending = true;
			if (_unloadedEntries < 0)
			{
				throw new IllegalStateException("Ecology unloaded-entry count became negative.");
			}
		}
		entry._stored = stored;
		entry._readinessRevision++;
		entry._lastProgressMillis = progressClock();
		addHistogramsLocked(stored.state());
		_archiveGeneration = Math.max(_archiveGeneration, stored.state().archiveGeneration());
	}

	private void addHistogramsLocked(PhantomPopulationEcologyState state)
	{
		if (state.disposition() == Disposition.ARCHIVED)
		{
			_archived++;
			return;
		}
		_managed++;
		_paceHistogram.merge(state.pace(), 1, Integer::sum);
		_personalityHistogram.merge(state.personality(), 1, Integer::sum);
		_scheduleHistogram.merge(state.scheduleTemplate(), 1, Integer::sum);
	}

	private void removeHistogramsLocked(PhantomPopulationEcologyState state)
	{
		if (state.disposition() == Disposition.ARCHIVED)
		{
			_archived--;
			return;
		}
		_managed--;
		decrement(_paceHistogram, state.pace());
		decrement(_personalityHistogram, state.personality());
		decrement(_scheduleHistogram, state.scheduleTemplate());
	}

	private static <T> void decrement(Map<T, Integer> values, T key)
	{
		values.computeIfPresent(key, (_, count) -> count == 1 ? null : count - 1);
	}

	private void removeLocked(long profileId)
	{
		final Entry removed = _entries.remove(profileId);
		if ((removed != null) && !removed._participating) { _pausedEntries--; }
		_demandFacts.remove(profileId); _admittedPreparation.remove(profileId); removeDelayLocked(profileId);
		if ((removed != null) && (removed._stored != null))
		{
			removeHistogramsLocked(removed._stored.state());
		}
		else if (removed != null)
		{
			_unloadedEntries--;
		}
		_queued.remove(profileId);
		_due.remove(profileId);
		_metadataQueued.remove(profileId);
		_metadataDue.remove(profileId);
		_materializationQueued.remove(profileId);
		_materializationDue.remove(profileId);
		refreshInventoryLocked();
	}

	private void queueLocked(long profileId)
	{
		final Entry candidate = _entries.get(profileId);
		if ((candidate == null) || !needsWorkLocked(candidate)) { return; }
		if (progressClock() < candidate._nextRetryPulse) { delayLocked(profileId, candidate._nextRetryPulse); return; }
		if ((candidate._stored == null) && _metadataQueued.add(profileId)) { _metadataDue.addLast(profileId); }
		if (!_materializationQueued.contains(profileId) && _queued.add(profileId))
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null) { entry._enqueuedMillis = progressClock(); }
			_due.addLast(profileId);
			requestWakeLocked();
		}
	}

	private void queueMaterializationLocked(long profileId, boolean continuation)
	{
		final Entry candidate = _entries.get(profileId);
		if ((candidate == null) || !candidate._participating || !needsWorkLocked(candidate) || (_batchAdmission && !_admittedPreparation.contains(profileId))) { return; }
		if (progressClock() < candidate._nextRetryPulse) { delayLocked(profileId, candidate._nextRetryPulse); return; }
		if ((candidate._stored == null) && _metadataQueued.add(profileId)) { _metadataDue.addLast(profileId); }
		if (_materializationQueued.add(profileId))
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null) { entry._enqueuedMillis = progressClock(); }
			_queued.remove(profileId);
			_due.remove(profileId);
			if (continuation)
			{
				_materializationDue.addFirst(profileId);
			}
			else
			{
				_materializationDue.addLast(profileId);
			}
			requestWakeLocked();
		}
	}

	@FunctionalInterface
	public interface WakeScheduler
	{
		/** Returns cancellation for the single outstanding wake. */
		Runnable schedule(Runnable wake, long delayMillis);
	}

	private void refreshInventoryLocked()
	{
		_inventoryReady = _unloadedEntries == 0;
		if (_inventoryReady && !_replacementInventoryBuilt)
		{
			final Set<Long> replaced = new HashSet<>();
			final List<Long> archived = new ArrayList<>();
			for (Map.Entry<Long, Entry> entry : _entries.entrySet())
			{
				final PhantomPopulationEcologyState state = entry.getValue()._stored.state();
				if (state.replacesProfileId() > 0)
				{
					replaced.add(state.replacesProfileId());
				}
				if (state.disposition() == Disposition.ARCHIVED)
				{
					archived.add(entry.getKey());
				}
			}
			archived.stream().filter(profileId -> !replaced.contains(profileId)).sorted().forEach(_replacementQueue::addLast);
			_replacementInventoryBuilt = true;
		}
	}

	private void recordWrite()
	{
		synchronized (_monitor)
		{
			_persistenceWrites++;
		}
	}

	private void recordFailure(String reason)
	{
		synchronized (_monitor)
		{
			_failures++;
			_lastFailure = reason == null ? "ecology.unknown_failure" : reason;
		}
	}

	private void recordFailureOnce(long profileId, String reason)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && !Objects.equals(entry._lastReportedFailure, reason))
			{
				entry._lastReportedFailure = reason;
				recordFailure(reason);
			}
		}
	}

	private void clearReportedFailure(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._lastReportedFailure = null;
				entry._terminal = false;
				entry._retryDelayPulses = 0;
				entry._nextRetryPulse = 0;
				entry._nextFailedProbePulse = 0;
			}
		}
	}

	private boolean failedProbeDue(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (progressClock() < entry._nextFailedProbePulse))
			{
				return false;
			}
			entry._nextFailedProbePulse = progressClock() + 256 * retryQuantum();
			return true;
		}
	}

	private boolean retryDeferred(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry != null) && (entry._terminal || (progressClock() < entry._nextRetryPulse));
		}
	}

	private void deferRetry(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if (entry != null)
			{
				entry._retryDelayPulses = Math.min(256, Math.max(1, entry._retryDelayPulses * 2));
				entry._nextRetryPulse = progressClock() + entry._retryDelayPulses * retryQuantum();
			}
		}
	}

	private void requireRuntime()
	{
		if ((_populationView == null) || (_populationEvents == null))
		{
			throw new IllegalStateException("Ecology runtime is not installed.");
		}
	}

	private long progressClock() { return _monotonicMillis == null ? _pulses : _monotonicMillis.getAsLong(); }
	private long retryQuantum() { return _wakeScheduler == null ? 1 : _quantumMillis; }

	private void stage(long profileId, String stage)
	{
		synchronized (_monitor) { final Entry entry = _entries.get(profileId); if (entry != null) { entry._stage = stage; } }
	}

	private void cacheHistorical(long profileId, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot value)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry != null) && (value != null))
			{
				if (!value.equals(entry._historicalSnapshot)) { entry._lastProgressMillis = progressClock(); entry._readinessRevision++; }
				entry._historicalSnapshot = value;
			}
		}
	}

	private void historicalFailure(long profileId, PhantomHistoricalBackgroundService.Result result, String fallback)
	{
		final String reason = result.successful() ? fallback : result.reason();
		recordFailureOnce(profileId, reason);
		if ((result.status() == ResultStatusCode.REPLAN_REQUIRED) && PhantomHistoricalBackgroundService.requiresNativeMaterialization(reason)) { deferRetry(profileId); return; }
		if ((result.status() == ResultStatusCode.RETRY) || (result.status() == ResultStatusCode.NORMAL_MATERIALIZED) || ((result.status() == ResultStatusCode.REPLAN_REQUIRED) && PhantomHistoricalBackgroundService.isRecoverableFailure(reason))) { deferRetry(profileId); }
		else { synchronized (_monitor) { final Entry entry = _entries.get(profileId); if (entry != null) { entry._terminal = true; entry._stage = "blocked"; } } }
	}

	/** Worker-cached diagnostics: no historical/status SQL in this read path. */
	public ProgressSnapshot progressSnapshot(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			final var historical = entry == null ? null : entry._historicalSnapshot;
			final var state = historical == null ? null : historical.state();
			final long now = progressClock();
			return new ProgressSnapshot(_due.size(), _materializationDue.size(), _workerStarted ? "RUNNING" : _workerInFlight ? "DISPATCHED" : _cancelWake != null ? "WAKE_SCHEDULED" : _stopping ? "STOPPED" : _wakeFailure != null ? "BLOCKED" : "IDLE", _activeProfile, entry == null ? "unknown" : entry._stage, entry == null ? 0 : Math.max(0, now - entry._enqueuedMillis), entry == null ? 0 : Math.max(0, now - entry._lastProgressMillis), _nextWakeMillis, entry == null ? 0 : entry._nextRetryPulse, state == null ? "UNKNOWN" : state.status().name(), state == null ? "" : state.requestId(), state == null ? 0 : state.cursorEpochMinute(), state == null ? 0 : state.targetEpochMinute(), historical == null ? 0 : historical.rowVersion());
		}
	}

	public record ProgressSnapshot(int ordinaryQueued, int urgentQueued, String workerState, long activeProfile, String currentStage, long enqueueAgeMillis, long lastProgressAgeMillis, long nextWakeMillis, long nextRetryMillis, String historicalStatus, String requestId, long innerCursorMinute, long targetMinute, long innerRevision) {}

	public record PreparationSnapshot(int physicalCount, int admittedPreparationCount, int waitingPreparationCount, long focusId, long focusAgeMillis, long oldestWaitMillis, int runnableOrdinary, int reservedPaused, int committedIntervals, long elapsedBatchMillis) {}
	public PreparationSnapshot preparationSnapshot()
	{
		synchronized (_monitor)
		{
			int waiting = 0;
			long oldest = 0;
			for (DemandFact fact : _demandFacts.values())
			{
				final Entry entry = _entries.get(fact.profileId());
				if (entry != null && needsWorkLocked(entry) && !_admittedPreparation.contains(fact.profileId()))
				{
					waiting++;
					oldest = Math.max(oldest, Math.max(0, (System.nanoTime() - fact.firstDemandNanos()) / 1_000_000L));
				}
			}
			return new PreparationSnapshot(_demandFacts.size(), _admittedPreparation.size(), waiting, _focusId, _focusId == 0 ? 0 : Math.max(0, progressClock() - _focusStartedMillis), oldest, _due.size(), _pausedEntries, _lastPulseIntervals, _lastBatchElapsedMillis);
		}
	}

	private static long historicalSeed(PhantomPopulationEcologyState state)
	{
		final long seed = mix(state.ecologyGeneration() ^ state.assignmentOrdinal(), state.virtualJoinEpochMinute());
		return (seed & Long.MAX_VALUE) == 0 ? 1 : seed & Long.MAX_VALUE;
	}

	private static long mix(long seed, long value)
	{
		long mixed = seed ^ (value + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	private static String typedFailure(RuntimeException exception)
	{
		final String message = exception.getMessage();
		return (message != null) && message.matches("[a-z0-9_.-]{1,96}") ? message : "ecology.persistence_or_runtime_failure";
	}

	private static final class Entry
	{
		private boolean _participating = true;
		private boolean _liveOwner;
		private boolean _terminal;
		private org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot _historicalSnapshot;
		private String _stage = "queued";
		private long _lastProgressMillis;
		private long _enqueuedMillis;
		private StoredState _stored;
		private String _lastReportedFailure;
		private int _retryDelayPulses;
		private long _nextRetryPulse;
		private long _nextFailedProbePulse;
		private boolean _claimed;
		private int _claimCommittedIntervals;
		private boolean _archiveRequested;
		private long _lastArchiveProbeMinute = -1;
		private boolean _publishedSchedulingPermission;
		private boolean _schedulingPublicationPending;
		private long _requestedMinute;
		private boolean _materializationDemand;
		private int _advancedReceipt;
		private long _readinessRevision;
	}

	@FunctionalInterface
	public interface Dispatcher
	{
		boolean dispatch(Runnable worker);
	}

	public record DueSnapshot(long profileId, long requestedHorizonMinute, long committedCursorMinute, boolean initialCatchupComplete, boolean requestPending, boolean queued, boolean running, long revision, boolean complete, String reason)
	{
	}

	@FunctionalInterface
	public interface PopulationView
	{
		Optional<ManagedSnapshot> find(long profileId);
	}

	public interface PopulationEvents
	{
		void requestArchive(long profileId);

		void reconcilePopulation();

		void ecologyFenceChanged(long profileId);
	}

	@FunctionalInterface
	public interface SafeBoundary
	{
		String blockingReason(long profileId);
	}

	public interface PersistencePort
	{
		Optional<StoredState> load(long profileId);

		StoredState insert(long profileId, PhantomPopulationEcologyState state);

		StoredState save(long profileId, StoredState expected, PhantomPopulationEcologyState replacement);

		ArchivedResult archive(ManagedSnapshot population, StoredState ecology, long archiveGeneration, long nowEpochMinute);
	}

	public interface HistoricalPort
	{
		Optional<org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot> status(long profileId);

		PhantomHistoricalBackgroundService.Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed);

		PhantomHistoricalBackgroundService.Result advance(long profileId, int maximumIntervals, int maximumMinutes);
	}

	public record Window(long startEpochMinute, long endEpochMinute, boolean productive)
	{
		public Window
		{
			if ((startEpochMinute < 0) || (endEpochMinute < startEpochMinute) || (productive && (endEpochMinute <= startEpochMinute)))
			{
				throw new IllegalArgumentException("Ecology productive window is invalid.");
			}
		}
	}

	public record DueReconciliation(boolean complete, int advancedIntervals, String reason, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest request)
	{
		public DueReconciliation(boolean complete, int advancedIntervals, String reason)
		{
			this(complete, advancedIntervals, reason, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest.normal());
		}
	}

	/** Batch counts are metadata attempts; publishedProfiles counts only first successful publications. */
	public record MetadataObservation(int registeredProfiles, int unloadedProfiles, long publishedProfiles, int lastMetadataBatch, int maximumMetadataBatch, boolean populationPlanApplied, boolean stopping, boolean inventoryReady, long historicalIntervals, long productiveMinutes, long calendarMinutes, long persistenceWrites, int maximumOrdinaryProfiles, int maximumOrdinaryIntervals, String observerFailure)
	{
	}

	/** Ordinary profile/interval counters exclude the separately counted metadata attempts. */
	public record Snapshot(boolean enabled, Preset preset, String catalogHash, boolean inventoryReady, int managed, int archived, int newcomers, int pendingCatchup, String turnoverPaused, Map<Pace, Integer> paceHistogram, Map<Personality, Integer> personalityHistogram, Map<String, Integer> scheduleHistogram, long pulses, long profileOperations, long historicalIntervals, long productiveMinutes, long calendarMinutes, long persistenceWrites, long failures, int lastPulseProfiles, int lastPulseIntervals, int maximumPulseProfiles, int maximumPulseIntervals, String lastFailure, long periodicDueCalls, long periodicOverdueCalls, int periodicRunning, long periodicBlockedCalls, int registeredProfiles, int unloadedProfiles, int metadataAllowance, long metadataProfileOperations, long metadataPublications, long metadataFailures, int lastMetadataBatch, int maximumMetadataBatch)
	{
		public static Snapshot disabled()
		{
			return new Snapshot(false, Preset.LIVING, "none", true, 0, 0, 0, 0, "disabled", Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}
}
