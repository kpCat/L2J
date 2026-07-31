/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException.Category;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.DimensionDefinition;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.DimensionGroup;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.EventDefinition;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.ModifierDefinition;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.ModifierWeight;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.SourceGroup;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Result;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.Contribution;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemoryRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemorySnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.ModifierSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.PersonalitySnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.RelationshipRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.RelationshipSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/**
 * Single-writer social-state owner with fixed stripes, bounded cache and no
 * worker, timer, executor or scheduled task.
 */
public final class PhantomSocialService implements PhantomSocialEventSink
{
	public interface PersistencePort
	{
		boolean profileExists(long profileId);

		Optional<StoredState> load(long profileId);

		StoredState save(long profileId, long expectedRowVersion, SocialState state);
	}

	public record StoredState(long profileId, long rowVersion, SocialState state)
	{
		public StoredState
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Stored social state metadata is invalid.");
			}
		}
	}

	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public record QueryResult<T>(Status status, T value, String detail)
	{
		public QueryResult
		{
			Objects.requireNonNull(status);
			detail = detail == null ? "" : detail;
			if (detail.length() > 128)
			{
				throw new IllegalArgumentException("Social query detail exceeds 128 characters.");
			}
		}

		public boolean available()
		{
			return (status == Status.READY) || (status == Status.INITIALIZED);
		}
	}

	public record Snapshot(ServiceState state, String catalogHash, int cacheEntries, int cacheLimit, int operationClaims, int writeClaims, long durableWrites, long recordedEvents, long idempotentEvents, long optimisticConflicts, long capacityFailures, long authorityStale, long failures)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(ServiceState.STOPPED, "none", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final int STRIPES = 64;
	private static final int MAX_ATTEMPTS = 3;
	private final PhantomSocialCatalog _catalog;
	private final PersistencePort _store;
	private final long _personalitySeed;
	private final int _cacheLimit;
	private final Object[] _stripes = new Object[STRIPES];
	private final Object _lifecycleLock = new Object();
	private final Map<Long, StoredState> _cache;
	private final AtomicInteger _operationClaims = new AtomicInteger();
	private final AtomicInteger _writeClaims = new AtomicInteger();
	private final LongAdder _durableWrites = new LongAdder();
	private final LongAdder _recordedEvents = new LongAdder();
	private final LongAdder _idempotentEvents = new LongAdder();
	private final LongAdder _optimisticConflicts = new LongAdder();
	private final LongAdder _capacityFailures = new LongAdder();
	private final LongAdder _authorityStale = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private volatile ServiceState _state = ServiceState.NEW;

	public PhantomSocialService(PhantomSocialCatalog catalog, PersistencePort store, long personalitySeed, int cacheLimit)
	{
		_catalog = Objects.requireNonNull(catalog);
		_store = Objects.requireNonNull(store);
		if (personalitySeed <= 0)
		{
			throw new IllegalArgumentException("Social personality seed must be positive.");
		}
		if ((cacheLimit < 16) || (cacheLimit > 10000))
		{
			throw new IllegalArgumentException("Social cache limit must be between 16 and 10000.");
		}
		_personalitySeed = personalitySeed;
		_cacheLimit = cacheLimit;
		for (int index = 0; index < _stripes.length; index++)
		{
			_stripes[index] = new Object();
		}
		_cache = new LinkedHashMap<>(Math.min(cacheLimit, 256), 0.75f, true)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, StoredState> eldest)
			{
				return size() > _cacheLimit;
			}
		};
	}

	public boolean start()
	{
		synchronized (_lifecycleLock)
		{
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	public QueryResult<PersonalitySnapshot> ensurePersonality(long profileId)
	{
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return queryFailure(Status.NOT_RUNNING, "social.not_running");
		}
		try (claim)
		{
			synchronized (stripe(profileId))
			{
				final EnsureResult ensured = ensureStored(profileId);
				if (!ensured.available())
				{
					return queryFailure(ensured.status(), ensured.detail());
				}
				return new QueryResult<>(ensured.initialized() ? Status.INITIALIZED : Status.READY, personality(profileId, ensured.stored().state()), ensured.initialized() ? "social.personality.initialized" : "social.personality.ready");
			}
		}
	}

	@Override
	public Result record(SocialEvent event)
	{
		Objects.requireNonNull(event);
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return new Result(Status.NOT_RUNNING, "social.not_running");
		}
		try (claim)
		{
			synchronized (stripe(event.ownerProfileId()))
			{
				StoredState current = cached(event.ownerProfileId());
				for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
				{
					try
					{
						if (current == null)
						{
							current = exact(event.ownerProfileId());
						}
						if ((current == null) && !_store.profileExists(event.ownerProfileId()))
						{
							return new Result(Status.PROFILE_NOT_FOUND, "social.profile_not_found");
						}
						final SocialState state = current == null ? createState(event.ownerProfileId()) : current.state();
						if (!authorityCurrent(state))
						{
							_authorityStale.increment();
							return new Result(Status.AUTHORITY_STALE, "social.authority_stale");
						}
						if (state.containsEvent(event.eventId()))
						{
							_idempotentEvents.increment();
							return new Result(Status.IDEMPOTENT, "social.event_idempotent");
						}
						final SocialState mutated = apply(state, event);
						final StoredState saved = durableSave(event.ownerProfileId(), current == null ? -1 : current.rowVersion(), mutated);
						cache(saved);
						_recordedEvents.increment();
						return new Result(Status.RECORDED, "social.event_recorded");
					}
					catch (CapacityFailure e)
					{
						_capacityFailures.increment();
						return new Result(Status.CAPACITY_REACHED, "social.capacity_reached");
					}
					catch (ConcurrentModificationException e)
					{
						_optimisticConflicts.increment();
						current = reload(event.ownerProfileId());
					}
					catch (PhantomProfilePersistenceException e)
					{
						if ((current == null) && (e.category() == Category.CONSTRAINT_VIOLATION))
						{
							_optimisticConflicts.increment();
							current = reload(event.ownerProfileId());
							continue;
						}
						_failures.increment();
						return new Result(Status.INCONSISTENT, "social.persistence_failure");
					}
					catch (IllegalArgumentException e)
					{
						_failures.increment();
						return new Result(Status.INCONSISTENT, "social.state_inconsistent");
					}
					catch (RuntimeException e)
					{
						_failures.increment();
						return new Result(Status.INCONSISTENT, "social.operation_failure");
					}
				}
				return new Result(Status.CONFLICT, "social.optimistic_conflict");
			}
		}
	}

	public QueryResult<SocialSnapshot> snapshot(long ownerProfileId, SubjectRef subject, int memoryLimit, long nowEpochMinute)
	{
		if ((ownerProfileId <= 0) || (subject == null) || (memoryLimit < 0) || (memoryLimit > PhantomSocialModel.MAX_MEMORIES) || (nowEpochMinute < 0))
		{
			return queryFailure(Status.INCONSISTENT, "social.query_invalid");
		}
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return queryFailure(Status.NOT_RUNNING, "social.not_running");
		}
		try (claim)
		{
			synchronized (stripe(ownerProfileId))
			{
				final EnsureResult ensured = ensureStored(ownerProfileId);
				if (!ensured.available())
				{
					return queryFailure(ensured.status(), ensured.detail());
				}
				final SocialState state = ensured.stored().state();
				if (!authorityCurrent(state))
				{
					_authorityStale.increment();
					return queryFailure(Status.AUTHORITY_STALE, "social.authority_stale");
				}
				try
				{
					final SocialState projected = project(state, nowEpochMinute);
					return new QueryResult<>(ensured.initialized() ? Status.INITIALIZED : Status.READY, socialSnapshot(ownerProfileId, subject, memoryLimit, projected), "social.snapshot.ready");
				}
				catch (RuntimeException e)
				{
					_failures.increment();
					return queryFailure(Status.INCONSISTENT, "social.state_inconsistent");
				}
			}
		}
	}

	public QueryResult<ModifierSnapshot> modifier(long ownerProfileId, SubjectRef subject, String modifierKey, long nowEpochMinute)
	{
		if ((ownerProfileId <= 0) || (subject == null) || (nowEpochMinute < 0))
		{
			return queryFailure(Status.INCONSISTENT, "social.modifier_invalid");
		}
		final ModifierDefinition modifier;
		try
		{
			modifier = _catalog.requireModifier(modifierKey);
		}
		catch (IllegalArgumentException e)
		{
			return queryFailure(Status.INCONSISTENT, "social.modifier_unknown");
		}
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			return queryFailure(Status.NOT_RUNNING, "social.not_running");
		}
		try (claim)
		{
			synchronized (stripe(ownerProfileId))
			{
				final EnsureResult ensured = ensureStored(ownerProfileId);
				if (!ensured.available())
				{
					return queryFailure(ensured.status(), ensured.detail());
				}
				final SocialState state = ensured.stored().state();
				if (!authorityCurrent(state))
				{
					_authorityStale.increment();
					return queryFailure(Status.AUTHORITY_STALE, "social.authority_stale");
				}
				try
				{
					final SocialState projected = project(state, nowEpochMinute);
					return new QueryResult<>(ensured.initialized() ? Status.INITIALIZED : Status.READY, evaluateModifier(projected, subject, modifier), "social.modifier.ready");
				}
				catch (RuntimeException e)
				{
					_failures.increment();
					return queryFailure(Status.INCONSISTENT, "social.state_inconsistent");
				}
			}
		}
	}

	public void beginStop()
	{
		synchronized (_lifecycleLock)
		{
			if (_state == ServiceState.NEW)
			{
				_state = ServiceState.STOPPED;
			}
			else if (_state == ServiceState.RUNNING)
			{
				_state = ServiceState.STOPPING;
			}
		}
	}

	public boolean finishStop()
	{
		if (_state == ServiceState.NEW)
		{
			beginStop();
			return true;
		}
		if (_state == ServiceState.RUNNING)
		{
			beginStop();
		}
		if ((_operationClaims.get() != 0) || (_writeClaims.get() != 0))
		{
			return false;
		}
		synchronized (_lifecycleLock)
		{
			if ((_operationClaims.get() != 0) || (_writeClaims.get() != 0))
			{
				return false;
			}
			_state = ServiceState.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_state, _catalog.hash(), cacheSize(), _cacheLimit, _operationClaims.get(), _writeClaims.get(), _durableWrites.sum(), _recordedEvents.sum(), _idempotentEvents.sum(), _optimisticConflicts.sum(), _capacityFailures.sum(), _authorityStale.sum(), _failures.sum());
	}

	private EnsureResult ensureStored(long profileId)
	{
		if (profileId <= 0)
		{
			return new EnsureResult(Status.PROFILE_NOT_FOUND, null, false, "social.profile_not_found");
		}
		StoredState current = cached(profileId);
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
		{
			try
			{
				if (current == null)
				{
					current = exact(profileId);
				}
				if (current != null)
				{
					if (!authorityCurrent(current.state()))
					{
						_authorityStale.increment();
						return new EnsureResult(Status.AUTHORITY_STALE, null, false, "social.authority_stale");
					}
					cache(current);
					return new EnsureResult(Status.READY, current, false, "social.ready");
				}
				if (!_store.profileExists(profileId))
				{
					return new EnsureResult(Status.PROFILE_NOT_FOUND, null, false, "social.profile_not_found");
				}
				final StoredState inserted = durableSave(profileId, -1, createState(profileId));
				cache(inserted);
				return new EnsureResult(Status.INITIALIZED, inserted, true, "social.initialized");
			}
			catch (ConcurrentModificationException e)
			{
				_optimisticConflicts.increment();
				current = reload(profileId);
			}
			catch (PhantomProfilePersistenceException e)
			{
				if ((current == null) && (e.category() == Category.CONSTRAINT_VIOLATION))
				{
					_optimisticConflicts.increment();
					current = reload(profileId);
					continue;
				}
				_failures.increment();
				return new EnsureResult(Status.INCONSISTENT, null, false, "social.persistence_failure");
			}
			catch (RuntimeException e)
			{
				_failures.increment();
				return new EnsureResult(Status.INCONSISTENT, null, false, "social.state_inconsistent");
			}
		}
		return new EnsureResult(Status.CONFLICT, null, false, "social.optimistic_conflict");
	}

	private SocialState createState(long profileId)
	{
		final NavigableMap<Integer, Integer> traits = new TreeMap<>();
		for (var trait : _catalog.traits())
		{
			final String hash = PhantomSocialModel.sha256(_catalog.hash() + '|' + _personalitySeed + '|' + profileId + '|' + trait.code());
			final long value = Long.parseUnsignedLong(hash.substring(0, 8), 16);
			traits.put(trait.code(), (int) (value % 20001L) - 10000);
		}
		return new SocialState(_catalog.hash(), _personalitySeed, 0, traits, List.of(), List.of());
	}

	private SocialState apply(SocialState state, SocialEvent event)
	{
		final EventDefinition definition = _catalog.requireEvent(event.eventKey());
		final SocialState projected = project(state, event.happenedEpochMinute());
		final long effectiveMinute = projected.logicalMinute();
		final List<RelationshipRecord> relationships = new ArrayList<>(projected.relationships());
		int targetIndex = -1;
		for (int index = 0; index < relationships.size(); index++)
		{
			if (relationships.get(index).subject().equals(event.subject()))
			{
				targetIndex = index;
				break;
			}
		}
		if (targetIndex < 0)
		{
			if (relationships.size() >= _catalog.limits().relationships())
			{
				final RelationshipRecord evictable = relationships.stream()
					.filter(RelationshipRecord::neutral)
					.filter(value -> !value.hasUnresolvedAgreement())
					.filter(value -> projected.memories().stream().noneMatch(memory -> memory.subject().equals(value.subject())))
					.min(Comparator.comparingLong(RelationshipRecord::lastInteractionMinute).thenComparing(RelationshipRecord::subject))
					.orElseThrow(CapacityFailure::new);
				relationships.remove(evictable);
			}
			relationships.add(RelationshipRecord.neutral(event.subject(), effectiveMinute));
			relationships.sort(Comparator.comparing(RelationshipRecord::subject));
			for (int index = 0; index < relationships.size(); index++)
			{
				if (relationships.get(index).subject().equals(event.subject()))
				{
					targetIndex = index;
					break;
				}
			}
		}

		final RelationshipRecord current = relationships.get(targetIndex);
		final List<Integer> values = new ArrayList<>(current.values());
		for (Map.Entry<Integer, Integer> delta : definition.dimensionDeltas().entrySet())
		{
			final long scaled = ((long) delta.getValue() * event.magnitude()) / 1000L;
			values.set(delta.getKey(), PhantomSocialModel.clamp((long) values.get(delta.getKey()) + scaled));
		}
		final List<Integer> agreements = new ArrayList<>(current.agreements());
		for (int index = 0; index < agreements.size(); index++)
		{
			agreements.set(index, (int) Math.max(0, Math.min(PhantomSocialModel.MAX_COUNTER, (long) agreements.get(index) + definition.agreementDeltas().get(index))));
		}
		relationships.set(targetIndex, current.withValues(values, agreements, effectiveMinute, effectiveMinute));

		final List<MemoryRecord> memories = new ArrayList<>(projected.memories());
		final int salience = (int) Math.min(PhantomSocialModel.MAX_VALUE, ((long) definition.salience() * event.magnitude()) / 1000L);
		if ((salience >= _catalog.limits().memorySalienceThreshold()) && (event.happenedEpochMinute() <= (Long.MAX_VALUE - definition.ttlMinutes())))
		{
			final long expiry = event.happenedEpochMinute() + definition.ttlMinutes();
			if (effectiveMinute < expiry)
			{
				memories.add(new MemoryRecord(event.eventId(), definition.code(), event.subject(), event.happenedEpochMinute(), expiry, salience, event.magnitude(), event.evidenceHash()));
			}
		}
		while (memories.size() > _catalog.limits().memories())
		{
			final MemoryRecord evicted = memories.stream().min(Comparator.comparingInt((MemoryRecord memory) -> effectiveSalience(memory, effectiveMinute)).thenComparingLong(MemoryRecord::happenedMinute).thenComparing(MemoryRecord::eventId)).orElseThrow();
			memories.remove(evicted);
		}
		return new SocialState(projected.authorityHash(), projected.personalitySeed(), effectiveMinute, projected.traits(), relationships, memories);
	}

	private SocialState project(SocialState state, long requestedMinute)
	{
		final long effectiveMinute = Math.max(requestedMinute, state.logicalMinute());
		final List<RelationshipRecord> relationships = new ArrayList<>(state.relationships().size());
		for (RelationshipRecord relationship : state.relationships())
		{
			final long elapsed = effectiveMinute - relationship.lastDecayMinute();
			final List<Integer> values = new ArrayList<>(relationship.values());
			for (DimensionDefinition dimension : _catalog.dimensions())
			{
				values.set(dimension.index(), decay(values.get(dimension.index()), elapsed, dimension.decayPerDay()));
			}
			relationships.add(relationship.withValues(values, relationship.agreements(), effectiveMinute, relationship.lastInteractionMinute()));
		}
		final List<MemoryRecord> memories = state.memories().stream().filter(memory -> effectiveMinute < memory.expiryMinute()).toList();
		return new SocialState(state.authorityHash(), state.personalitySeed(), effectiveMinute, state.traits(), relationships, memories);
	}

	private int decay(int value, long elapsedMinutes, int unitsPerDay)
	{
		if ((value == 0) || (elapsedMinutes <= 0) || (unitsPerDay == 0))
		{
			return value;
		}
		final long magnitude = Math.abs((long) value);
		final long minutesToZero = ((magnitude * 1440L) + unitsPerDay - 1L) / unitsPerDay;
		if (elapsedMinutes >= minutesToZero)
		{
			return 0;
		}
		final long reduction = (elapsedMinutes * unitsPerDay) / 1440L;
		final long decayed = Math.max(0, magnitude - reduction);
		return (int) (value < 0 ? -decayed : decayed);
	}

	private int effectiveSalience(MemoryRecord memory, long now)
	{
		return Math.max(0, decay(memory.salience(), Math.max(0, now - memory.happenedMinute()), _catalog.limits().memoryDecayPerDay()));
	}

	private PersonalitySnapshot personality(long profileId, SocialState state)
	{
		final Map<String, Integer> traits = new TreeMap<>();
		for (var trait : _catalog.traits())
		{
			traits.put(trait.key(), state.traits().get(trait.code()));
		}
		return new PersonalitySnapshot(profileId, state.personalitySeed(), traits, state.authorityHash());
	}

	private SocialSnapshot socialSnapshot(long profileId, SubjectRef subject, int memoryLimit, SocialState state)
	{
		final RelationshipRecord relationship = state.relationship(subject);
		final Map<String, Integer> relationshipValues = new TreeMap<>();
		final Map<String, Integer> reputationValues = new TreeMap<>();
		for (DimensionDefinition dimension : _catalog.dimensions())
		{
			final int value = relationship == null ? 0 : relationship.values().get(dimension.index());
			(dimension.group() == DimensionGroup.RELATIONSHIP ? relationshipValues : reputationValues).put(dimension.key(), value);
		}
		final Map<String, Integer> agreements = new TreeMap<>();
		for (int index = 0; index < PhantomSocialModel.AGREEMENT_COUNT; index++)
		{
			agreements.put(_catalog.agreementKey(index), relationship == null ? 0 : relationship.agreements().get(index));
		}
		final RelationshipSnapshot relationshipSnapshot = new RelationshipSnapshot(subject, relationshipValues, reputationValues, agreements, relationship == null ? state.logicalMinute() : relationship.lastDecayMinute(), relationship == null ? 0 : relationship.lastInteractionMinute());
		final List<MemorySnapshot> memories = state.memories().stream()
			.filter(memory -> memory.subject().equals(subject))
			.sorted(Comparator.comparingInt((MemoryRecord memory) -> effectiveSalience(memory, state.logicalMinute())).reversed().thenComparing(Comparator.comparingLong(MemoryRecord::happenedMinute).reversed()).thenComparing(MemoryRecord::eventId))
			.limit(memoryLimit)
			.map(memory -> new MemorySnapshot(memory.eventId(), _catalog.requireEvent(memory.eventCode()).key(), memory.subject(), memory.happenedMinute(), memory.expiryMinute(), effectiveSalience(memory, state.logicalMinute()), memory.magnitude(), memory.evidenceHash()))
			.toList();
		return new SocialSnapshot(profileId, personality(profileId, state), relationshipSnapshot, memories, state.logicalMinute(), state.authorityHash());
	}

	private ModifierSnapshot evaluateModifier(SocialState state, SubjectRef subject, ModifierDefinition definition)
	{
		final RelationshipRecord relationship = state.relationship(subject);
		final List<Contribution> traits = new ArrayList<>();
		final List<Contribution> relationships = new ArrayList<>();
		final List<Contribution> agreements = new ArrayList<>();
		final List<String> evidence = new ArrayList<>();
		long total = 0;
		for (ModifierWeight weight : definition.weights())
		{
			final int input = switch (weight.sourceGroup())
			{
				case TRAIT -> state.traits().getOrDefault(weight.sourceIndex(), 0);
				case DIMENSION -> relationship == null ? 0 : relationship.values().get(weight.sourceIndex());
				case AGREEMENT -> relationship == null ? 0 : (int) Math.min(PhantomSocialModel.MAX_VALUE, (long) relationship.agreements().get(weight.sourceIndex()) * 1000L);
			};
			final int delta = (int) Math.max(-3000, Math.min(3000, ((long) input * weight.weight()) / 10000L));
			final Contribution contribution = new Contribution(weight.sourceKey(), input, weight.weight(), delta);
			switch (weight.sourceGroup())
			{
				case TRAIT -> traits.add(contribution);
				case DIMENSION -> relationships.add(contribution);
				case AGREEMENT -> agreements.add(contribution);
			}
			if (delta != 0)
			{
				evidence.add(weight.sourceKey());
			}
			total += delta;
		}
		final int result = (int) Math.max(definition.minimum(), Math.min(definition.maximum(), total));
		final List<String> boundedEvidence = evidence.stream().distinct().sorted().limit(8).toList();
		return new ModifierSnapshot(definition.key(), result, traits, relationships, agreements, boundedEvidence, state.authorityHash());
	}

	private boolean authorityCurrent(SocialState state)
	{
		return state.authorityHash().equals(_catalog.hash()) && (state.personalitySeed() == _personalitySeed);
	}

	private StoredState exact(long profileId)
	{
		return _store.load(profileId).orElse(null);
	}

	private StoredState reload(long profileId)
	{
		final StoredState result = exact(profileId);
		if (result == null)
		{
			removeCached(profileId);
		}
		else
		{
			cache(result);
		}
		return result;
	}

	private StoredState durableSave(long profileId, long expectedRowVersion, SocialState state)
	{
		_writeClaims.incrementAndGet();
		try
		{
			final StoredState result = _store.save(profileId, expectedRowVersion, state);
			_durableWrites.increment();
			return result;
		}
		finally
		{
			_writeClaims.decrementAndGet();
		}
	}

	private Object stripe(long profileId)
	{
		return _stripes[Math.floorMod(Long.hashCode(profileId), _stripes.length)];
	}

	private OperationClaim beginOperation()
	{
		synchronized (_lifecycleLock)
		{
			if (_state != ServiceState.RUNNING)
			{
				return null;
			}
			_operationClaims.incrementAndGet();
			return new OperationClaim();
		}
	}

	private StoredState cached(long profileId)
	{
		synchronized (_cache)
		{
			return _cache.get(profileId);
		}
	}

	private void cache(StoredState state)
	{
		synchronized (_cache)
		{
			_cache.put(state.profileId(), state);
		}
	}

	private void removeCached(long profileId)
	{
		synchronized (_cache)
		{
			_cache.remove(profileId);
		}
	}

	private int cacheSize()
	{
		synchronized (_cache)
		{
			return _cache.size();
		}
	}

	private static <T> QueryResult<T> queryFailure(Status status, String detail)
	{
		return new QueryResult<>(status, null, detail);
	}

	private record EnsureResult(Status status, StoredState stored, boolean initialized, String detail)
	{
		private boolean available()
		{
			return (status == Status.READY) || (status == Status.INITIALIZED);
		}
	}

	private static final class CapacityFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}

	private final class OperationClaim implements AutoCloseable
	{
		private final AtomicBoolean _closed = new AtomicBoolean();

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_operationClaims.decrementAndGet();
			}
		}
	}
}
