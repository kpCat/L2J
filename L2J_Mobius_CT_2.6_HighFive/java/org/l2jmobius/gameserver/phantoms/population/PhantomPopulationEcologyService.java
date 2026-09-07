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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog.PresetDefinition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;

/**
 * Bounded calendar policy above Goal033A. This class owns no timer, thread,
 * executor or future; {@link #onPopulationPulse()} is called by PopulationManager.
 */
public final class PhantomPopulationEcologyService
{
	private static final long MINUTE_MILLIS = 60_000L;
	private static final int MAXIMUM_CALENDAR_STEPS_PER_PROFILE = 4096;
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
	private final Map<Long, Entry> _entries = new LinkedHashMap<>();
	private final ArrayDeque<Long> _due = new ArrayDeque<>();
	private final Set<Long> _queued = new HashSet<>();
	private final ArrayDeque<Long> _replacementQueue = new ArrayDeque<>();
	private final Map<Pace, Integer> _paceHistogram = new EnumMap<>(Pace.class);
	private final Map<Personality, Integer> _personalityHistogram = new EnumMap<>(Personality.class);
	private final Map<String, Integer> _scheduleHistogram = new TreeMap<>();
	private PopulationView _populationView;
	private PopulationEvents _populationEvents;
	private boolean _inventoryReady = true;
	private boolean _replacementInventoryBuilt;
	private int _unloadedEntries;
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
		_catalog = Objects.requireNonNull(catalog, "Ecology catalog must not be null.");
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

	public void register(ManagedSnapshot population)
	{
		Objects.requireNonNull(population, "Population snapshot must not be null.");
		synchronized (_monitor)
		{
			requireRuntime();
			final long profileId = population.profile().profileId();
			if (_entries.putIfAbsent(profileId, new Entry()) != null)
			{
				return;
			}
			_unloadedEntries++;
			_inventoryReady = false;
			_replacementInventoryBuilt = false;
			queueLocked(profileId);
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
		final List<Long> profiles = new ArrayList<>();
		synchronized (_monitor)
		{
			requireRuntime();
			_pulses++;
			for (int count = 0; (count < _catalog.limits().maximumProfilesPerPulse()) && !_due.isEmpty(); count++)
			{
				final long profileId = _due.removeFirst();
				_queued.remove(profileId);
				final Entry entry = _entries.get(profileId);
				if ((entry != null) && !entry._claimed)
				{
					entry._claimed = true;
					profiles.add(profileId);
				}
			}
		}
		int intervalsRemaining = _catalog.limits().maximumIntervalsPerPulse();
		int intervals = 0;
		for (long profileId : profiles)
		{
			try
			{
				final int used = process(profileId, intervalsRemaining);
				intervals += used;
				intervalsRemaining -= used;
			}
			catch (RuntimeException exception)
			{
				recordFailure(typedFailure(exception));
			}
			finally
			{
				synchronized (_monitor)
				{
					final Entry entry = _entries.get(profileId);
					if (entry != null)
					{
						entry._claimed = false;
						queueLocked(profileId);
					}
				}
			}
		}
		boolean inventoryBecameReady = false;
		synchronized (_monitor)
		{
			_lastPulseProfiles = profiles.size();
			_lastPulseIntervals = intervals;
			_maximumPulseProfiles = Math.max(_maximumPulseProfiles, _lastPulseProfiles);
			_maximumPulseIntervals = Math.max(_maximumPulseIntervals, _lastPulseIntervals);
			_profileOperations += profiles.size();
			_historicalIntervals += intervals;
			final boolean before = _inventoryReady;
			refreshInventoryLocked();
			inventoryBecameReady = !before && _inventoryReady;
		}
		if (inventoryBecameReady)
		{
			_populationEvents.reconcilePopulation();
		}
	}

	private int process(long profileId, int intervalBudget)
	{
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
		}
		final PhantomPopulationEcologyState state = stored.state();
		if ((state.disposition() == Disposition.ARCHIVED) || (population.state().state() != PhantomPopulationState.State.READY))
		{
			return 0;
		}
		if (state.requestPending())
		{
			return advanceRequest(profileId, stored, intervalBudget);
		}
		final long now = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		if (_materialized.test(profileId))
		{
			if (now > state.calendarCursorEpochMinute())
			{
				persist(profileId, stored, state.advanceCalendar(now));
			}
			return 0;
		}
		final long reconciliationTarget = state.initialCatchupComplete() ? now : Math.min(now, state.initialTargetEpochMinute());
		if (state.calendarCursorEpochMinute() < reconciliationTarget)
		{
			final int advanced = beginNextWindow(profileId, population, stored, reconciliationTarget);
			if (state.initialCatchupComplete() && permitsScheduling(profileId))
			{
				_populationEvents.ecologyFenceChanged(profileId);
			}
			return advanced;
		}
		if ((now >= state.turnoverEligibleEpochMinute()) && !_materialized.test(profileId))
		{
			requestArchive(profileId);
		}
		return 0;
	}

	private int advanceRequest(long profileId, StoredState ecology, int intervalBudget)
	{
		final PhantomPopulationEcologyState state = ecology.state();
		var catchup = _historical.status(profileId).orElse(null);
		if ((catchup == null) || !catchup.state().requestId().equals(state.currentRequestId()))
		{
			final var begun = _historical.begin(profileId, state.calendarCursorEpochMinute(), state.currentWindowTargetEpochMinute(), historicalSeed(state));
			if (!begun.successful() || (begun.snapshot() == null) || !begun.snapshot().state().requestId().equals(state.currentRequestId()))
			{
				recordFailure(begun.reason());
				return 0;
			}
			catchup = begun.snapshot();
		}
		if (catchup.state().status() == Status.COMPLETE)
		{
			persist(profileId, ecology, state.completeRequest());
			_populationEvents.ecologyFenceChanged(profileId);
			return 0;
		}
		if ((catchup.state().status() == Status.FAILED_REPLAN_REQUIRED) || (intervalBudget <= 0))
		{
			if (catchup.state().status() == Status.FAILED_REPLAN_REQUIRED)
			{
				recordFailure(catchup.state().failureReason());
			}
			return 0;
		}
		final int bounded = Math.min(intervalBudget, _catalog.limits().maximumIntervalsPerPulse());
		final var advanced = _historical.advance(profileId, bounded, bounded);
		if ((advanced.status() != ResultStatusCode.SUCCESS) && (advanced.status() != ResultStatusCode.RETRY))
		{
			recordFailure(advanced.reason());
		}
		if ((advanced.snapshot() != null) && (advanced.snapshot().state().status() == Status.COMPLETE))
		{
			persist(profileId, ecology, state.completeRequest());
			_populationEvents.ecologyFenceChanged(profileId);
		}
		if (advanced.advancedIntervals() > 0)
		{
			synchronized (_monitor)
			{
				_productiveMinutes += advanced.advancedIntervals();
			}
		}
		return advanced.advancedIntervals();
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
		if (!begun.successful() || (begun.snapshot() == null))
		{
			if (begun.status() != ResultStatusCode.NORMAL_MATERIALIZED)
			{
				recordFailure(begun.reason());
			}
			return 0;
		}
		persist(profileId, stored, state.beginRequest(begun.snapshot().state().requestId(), window.endEpochMinute()));
		_populationEvents.ecologyFenceChanged(profileId);
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
		final String blocked;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || (entry._stored == null) || entry._archiveRequested || !_inventoryReady || (_archived >= _archiveLimit) || entry._stored.state().requestPending())
			{
				return;
			}
			blocked = _safeBoundary.blockingReason(profileId);
			if (!blocked.isEmpty())
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
		final StoredState ecology;
		final long generation;
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			if ((entry == null) || !entry._archiveRequested || (entry._stored == null) || (entry._stored.state().disposition() != Disposition.MANAGED) || entry._stored.state().requestPending() || (_archived >= _archiveLimit) || _materialized.test(profileId) || !_safeBoundary.blockingReason(profileId).isEmpty())
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
		final long now = Math.max(0, _clock.instant().toEpochMilli() / MINUTE_MILLIS);
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry != null) && (entry._stored != null) && (entry._stored.state().disposition() == Disposition.MANAGED) && entry._stored.state().initialCatchupComplete() && !entry._stored.state().requestPending() && (entry._stored.state().calendarCursorEpochMinute() >= now);
		}
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
			return new Snapshot(true, _preset, _catalog.hash(), _inventoryReady, _managed, _archived, newcomers, pending, pause, Map.copyOf(_paceHistogram), Map.copyOf(_personalityHistogram), Map.copyOf(_scheduleHistogram), _pulses, _profileOperations, _historicalIntervals, _productiveMinutes, _calendarMinutes, _persistenceWrites, _failures, _lastPulseProfiles, _lastPulseIntervals, _maximumPulseProfiles, _maximumPulseIntervals, _lastFailure);
		}
	}

	public Map<Integer, Integer> initialPersonalityTraits(long profileId)
	{
		final PhantomPopulationEcologyState state = find(profileId).orElse(null);
		return (state == null) || (state.disposition() != Disposition.MANAGED) ? Map.of() : _catalog.personalityTraits(state, profileId);
	}

	private StoredState persist(long profileId, StoredState expected, PhantomPopulationEcologyState replacement)
	{
		final StoredState saved = _store.save(profileId, expected, replacement);
		publish(profileId, saved);
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
			if (_unloadedEntries < 0)
			{
				throw new IllegalStateException("Ecology unloaded-entry count became negative.");
			}
		}
		entry._stored = stored;
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
		refreshInventoryLocked();
	}

	private void queueLocked(long profileId)
	{
		if (_queued.add(profileId))
		{
			_due.addLast(profileId);
		}
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

	private void requireRuntime()
	{
		if ((_populationView == null) || (_populationEvents == null))
		{
			throw new IllegalStateException("Ecology runtime is not installed.");
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
		private StoredState _stored;
		private boolean _claimed;
		private boolean _archiveRequested;
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

	public record Snapshot(boolean enabled, Preset preset, String catalogHash, boolean inventoryReady, int managed, int archived, int newcomers, int pendingCatchup, String turnoverPaused, Map<Pace, Integer> paceHistogram, Map<Personality, Integer> personalityHistogram, Map<String, Integer> scheduleHistogram, long pulses, long profileOperations, long historicalIntervals, long productiveMinutes, long calendarMinutes, long persistenceWrites, long failures, int lastPulseProfiles, int lastPulseIntervals, int maximumPulseProfiles, int maximumPulseIntervals, String lastFailure)
	{
		public static Snapshot disabled()
		{
			return new Snapshot(false, Preset.LIVING, "none", true, 0, 0, 0, 0, "disabled", Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
		}
	}
}
