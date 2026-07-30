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
package org.l2jmobius.tests.phantoms;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.UnregisterStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationOwnershipPort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationPersistencePort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.CreationStage;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PopulationInitializationContract;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

public final class PhantomPopulationTestDoubles
{
	private PhantomPopulationTestDoubles()
	{
	}

	public static final class MemoryStore implements PhantomPopulationPersistencePort
	{
		private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
		private final NavigableMap<Long, ManagedSnapshot> _rows = new TreeMap<>();
		private final String _catalogHash;
		private final AtomicLong _writes = new AtomicLong();
		private long _nextProfileId = 1;
		private Runnable _afterCreate = () ->
		{
		};

		public MemoryStore(String catalogHash)
		{
			_catalogHash = catalogHash;
		}

		public synchronized ManagedSnapshot seedReady(long profileId, int regionId)
		{
			return seed(profileId, State.READY, CreationStage.LINKED, regionId);
		}

		public synchronized ManagedSnapshot seedRetired(long profileId, CreationStage stage, int regionId)
		{
			return seed(profileId, State.RETIRED, stage, regionId);
		}

		public synchronized ManagedSnapshot seed(long profileId, State state, CreationStage stage, int regionId)
		{
			final boolean characterExpected = (stage == CreationStage.CHARACTER_CREATED) || (stage == CreationStage.INITIALIZATION_INTENT) || (stage == CreationStage.VERIFIED) || (stage == CreationStage.LINKED);
			final boolean characterActual = (stage == CreationStage.VERIFIED) || (stage == CreationStage.LINKED);
			final int objectId = Math.toIntExact(1_000_000L + profileId);
			final Integer expected = characterExpected ? objectId : null;
			final Integer actual = characterActual ? objectId : null;
			final String initializationHash = characterActual ? "2".repeat(64) : "";
			final PhantomPopulationState population = new PhantomPopulationState(
				state,
				1,
				profileId,
				_catalogHash,
				"1".repeat(64),
				16_001_601L,
				0,
				"p" + Long.toString(profileId, 36),
				"A".repeat(43),
				"P" + Long.toString(profileId, 36),
				0,
				false,
				0,
				0,
				0,
				"evening",
				0,
				regionId,
				-71338,
				258271,
				-3104,
				expected,
				actual,
				stage,
				initializationHash,
				"");
			final Integer linkedObjectId = stage == CreationStage.LINKED ? objectId : null;
			final PhantomProfile profile = new PhantomProfile(profileId, linkedObjectId, 1, 0, CREATED, CREATED);
			final PhantomProfileComponent component = new PhantomProfileComponent(profileId, PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, 0, new byte[0], CREATED, CREATED);
			final ManagedSnapshot snapshot = new ManagedSnapshot(profile, component, population);
			_rows.put(profileId, snapshot);
			_nextProfileId = Math.max(_nextProfileId, profileId + 1);
			return snapshot;
		}

		public synchronized void afterCreate(Runnable afterCreate)
		{
			_afterCreate = afterCreate;
		}

		public long writes()
		{
			return _writes.get();
		}

		public void resetWrites()
		{
			_writes.set(0);
		}

		public synchronized int size()
		{
			return _rows.size();
		}

		@Override
		public synchronized List<ManagedSnapshot> loadManagedAfter(long exclusiveProfileId, int pageSize)
		{
			return _rows.tailMap(exclusiveProfileId, false).values().stream().limit(pageSize).toList();
		}

		@Override
		public synchronized ManagedSnapshot createShell(long generation, long creationOrdinal, long deterministicSeed)
		{
			final long profileId = _nextProfileId++;
			final ManagedSnapshot snapshot = seed(profileId, State.SHELL, CreationStage.SHELL_DURABLE, (int) (profileId % 20));
			_writes.incrementAndGet();
			_afterCreate.run();
			return snapshot;
		}

		@Override
		public synchronized ManagedSnapshot reload(long profileId)
		{
			final ManagedSnapshot snapshot = _rows.get(profileId);
			if (snapshot == null)
			{
				throw new IllegalStateException("Synthetic managed population row is absent.");
			}
			return snapshot;
		}

		@Override
		public synchronized CreationResult advanceCreation(ManagedSnapshot current)
		{
			final PhantomPopulationState state = current.state();
			final PhantomPopulationState next = switch (state.state())
			{
				case SHELL -> copy(state, State.ACCOUNT_PREPARED, CreationStage.ACCOUNT_VERIFIED, null, null, "");
				case ACCOUNT_PREPARED -> copy(state, State.CHARACTER_PRESENT, CreationStage.CHARACTER_CREATED, Math.toIntExact(1_000_000L + current.profile().profileId()), null, "");
				case CHARACTER_PRESENT -> copy(state, State.INITIALIZING, CreationStage.VERIFIED, Math.toIntExact(1_000_000L + current.profile().profileId()), Math.toIntExact(1_000_000L + current.profile().profileId()), "2".repeat(64));
				case INITIALIZING -> copy(state, State.READY, CreationStage.LINKED, state.expectedCharacterObjectId(), state.actualCharacterObjectId(), state.initializationHash());
				case READY -> state;
				case RETIRE_REQUESTED, RETIRED, INCONSISTENT -> null;
			};
			if (next == null)
			{
				return new CreationResult(state.state() == State.INCONSISTENT ? CreationOutcome.INCONSISTENT : CreationOutcome.NOT_PENDING, current);
			}
			if (next == state)
			{
				return new CreationResult(CreationOutcome.READY, current);
			}
			final ManagedSnapshot updated = updateState(current, next);
			return new CreationResult(next.state() == State.READY ? CreationOutcome.READY : CreationOutcome.PROGRESSED, updated);
		}

		@Override
		public synchronized ManagedSnapshot updateState(ManagedSnapshot current, PhantomPopulationState next)
		{
			final long version = current.component().rowVersion() + 1;
			final PhantomProfileComponent component = new PhantomProfileComponent(current.profile().profileId(), PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, version, new byte[0], CREATED, CREATED);
			final Integer characterObjectId = next.creationStage() == CreationStage.LINKED ? next.actualCharacterObjectId() : current.profile().characterObjectId();
			final PhantomProfile profile = new PhantomProfile(current.profile().profileId(), characterObjectId, 1, current.profile().rowVersion() + (characterObjectId != current.profile().characterObjectId() ? 1 : 0), CREATED, CREATED);
			final ManagedSnapshot updated = new ManagedSnapshot(profile, component, next);
			_rows.put(profile.profileId(), updated);
			_writes.incrementAndGet();
			return updated;
		}

		@Override
		public PopulationInitializationContract validateAuthority(ManagedSnapshot snapshot)
		{
			return null;
		}

		private static PhantomPopulationState copy(PhantomPopulationState source, State state, CreationStage stage, Integer expected, Integer actual, String initializationHash)
		{
			return new PhantomPopulationState(
				state,
				source.populationGeneration(),
				source.creationOrdinal(),
				source.catalogHash(),
				source.initializationAuthorityHash(),
				source.deterministicSeed(),
				source.nameAttempt(),
				source.reservedAccount(),
				source.ownershipToken(),
				source.characterName(),
				source.classId(),
				source.female(),
				source.face(),
				source.hairColor(),
				source.hairStyle(),
				source.scheduleTemplate(),
				source.schedulePhaseMinutes(),
				source.homeMapRegionId(),
				source.creationX(),
				source.creationY(),
				source.creationZ(),
				expected,
				actual,
				stage,
				initializationHash,
				"");
		}
	}

	public static final class Ownership implements PhantomPopulationOwnershipPort
	{
		private final Set<Long> _registered = new HashSet<>();
		private final Set<Long> _attached = new HashSet<>();
		private final Map<Long, Long> _lastSequence = new TreeMap<>();
		private final Map<Long, PhantomActivityState> _states = new TreeMap<>();
		private final ArrayDeque<RegistrationStatus> _register = new ArrayDeque<>();
		private final ArrayDeque<AttachResult> _attach = new ArrayDeque<>();
		private final ArrayDeque<SignalStatus> _submit = new ArrayDeque<>();
		private final ArrayDeque<SignalStatus> _withdraw = new ArrayDeque<>();
		private final ArrayDeque<UnregisterStatus> _unregister = new ArrayDeque<>();
		private final ArrayDeque<DetachResult> _detach = new ArrayDeque<>();
		private boolean _throwRegister;
		private long _calls;

		public void registerOutcomes(RegistrationStatus... outcomes)
		{
			add(_register, outcomes);
		}

		public void attachOutcomes(AttachResult... outcomes)
		{
			add(_attach, outcomes);
		}

		public void submitOutcomes(SignalStatus... outcomes)
		{
			add(_submit, outcomes);
		}

		public void withdrawOutcomes(SignalStatus... outcomes)
		{
			add(_withdraw, outcomes);
		}

		public void unregisterOutcomes(UnregisterStatus... outcomes)
		{
			add(_unregister, outcomes);
		}

		public void detachOutcomes(DetachResult... outcomes)
		{
			add(_detach, outcomes);
		}

		public void throwNextRegister()
		{
			_throwRegister = true;
		}

		public long calls()
		{
			return _calls;
		}

		public long lastSequence(long profileId)
		{
			return _lastSequence.getOrDefault(profileId, 0L);
		}

		public Set<Long> activeIds()
		{
			final Set<Long> active = new HashSet<>();
			_states.forEach((profileId, state) ->
			{
				if (state == PhantomActivityState.ACTIVE)
				{
					active.add(profileId);
				}
			});
			return Set.copyOf(active);
		}

		@Override
		public RegistrationStatus register(long profileId)
		{
			_calls++;
			if (_throwRegister)
			{
				_throwRegister = false;
				throw new IllegalStateException("synthetic register fault");
			}
			final RegistrationStatus outcome = _register.isEmpty() ? (_registered.contains(profileId) ? RegistrationStatus.ALREADY_REGISTERED : RegistrationStatus.REGISTERED) : _register.removeFirst();
			if ((outcome == RegistrationStatus.REGISTERED) || (outcome == RegistrationStatus.ALREADY_REGISTERED))
			{
				_registered.add(profileId);
			}
			return outcome;
		}

		@Override
		public AttachResult attach(long profileId)
		{
			_calls++;
			final AttachResult outcome = _attach.isEmpty() ? (_attached.contains(profileId) ? AttachResult.ALREADY_ATTACHED : AttachResult.ATTACHED) : _attach.removeFirst();
			if ((outcome == AttachResult.ATTACHED) || (outcome == AttachResult.ALREADY_ATTACHED))
			{
				_attached.add(profileId);
			}
			return outcome;
		}

		@Override
		public SignalStatus submit(long profileId, String source, long sequence, PhantomActivityState state, long ttlMillis)
		{
			_calls++;
			_lastSequence.merge(profileId, sequence, Math::max);
			final SignalStatus outcome = _submit.isEmpty() ? (_registered.contains(profileId) ? SignalStatus.ACCEPTED : SignalStatus.NOT_REGISTERED) : _submit.removeFirst();
			if ((outcome == SignalStatus.ACCEPTED) || (outcome == SignalStatus.COALESCED) || (outcome == SignalStatus.STALE))
			{
				_states.put(profileId, state);
			}
			return outcome;
		}

		@Override
		public SignalStatus withdraw(long profileId, String source, long sequence)
		{
			_calls++;
			_lastSequence.merge(profileId, sequence, Math::max);
			final SignalStatus outcome = _withdraw.isEmpty() ? (_registered.contains(profileId) ? SignalStatus.ACCEPTED : SignalStatus.NOT_REGISTERED) : _withdraw.removeFirst();
			if ((outcome == SignalStatus.ACCEPTED) || (outcome == SignalStatus.COALESCED) || (outcome == SignalStatus.STALE) || (outcome == SignalStatus.NOT_REGISTERED))
			{
				_states.put(profileId, PhantomActivityState.SLEEPING);
			}
			return outcome;
		}

		@Override
		public UnregisterStatus unregister(long profileId)
		{
			_calls++;
			final UnregisterStatus outcome = _unregister.isEmpty() ? (_registered.contains(profileId) ? UnregisterStatus.UNREGISTERED : UnregisterStatus.NOT_REGISTERED) : _unregister.removeFirst();
			if ((outcome == UnregisterStatus.UNREGISTERED) || (outcome == UnregisterStatus.NOT_REGISTERED))
			{
				_registered.remove(profileId);
			}
			return outcome;
		}

		@Override
		public DetachResult detach(long profileId)
		{
			_calls++;
			final DetachResult outcome = _detach.isEmpty() ? (_attached.contains(profileId) ? DetachResult.DETACHED : DetachResult.NOT_ATTACHED) : _detach.removeFirst();
			if ((outcome == DetachResult.DETACHED) || (outcome == DetachResult.NOT_ATTACHED))
			{
				_attached.remove(profileId);
			}
			return outcome;
		}

		@Override
		public boolean registered(long profileId)
		{
			return _registered.contains(profileId);
		}

		@Override
		public boolean materialized(long profileId)
		{
			return false;
		}

		@Override
		public int registeredCount()
		{
			return _registered.size();
		}

		private static <T> void add(ArrayDeque<T> queue, T[] outcomes)
		{
			for (T outcome : outcomes)
			{
				queue.addLast(outcome);
			}
		}
	}

	public static final class MutableClock extends Clock
	{
		private Instant _instant;
		private final ZoneId _zone;

		public MutableClock(Instant instant)
		{
			this(instant, ZoneOffset.UTC);
		}

		private MutableClock(Instant instant, ZoneId zone)
		{
			_instant = instant;
			_zone = zone;
		}

		public void set(Instant instant)
		{
			_instant = instant;
		}

		@Override
		public ZoneId getZone()
		{
			return _zone;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return new MutableClock(_instant, zone);
		}

		@Override
		public Instant instant()
		{
			return _instant;
		}
	}
}
