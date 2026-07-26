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
package org.l2jmobius.gameserver.phantoms.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.MaterializationException;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Explicit, bounded production owner of profile-to-canonical-Player
 * materialization.
 */
public final class PhantomMaterializationService
{
	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public enum ResultStatus
	{
		SUCCESS,
		SERVICE_NOT_RUNNING,
		PROFILE_NOT_FOUND,
		PROFILE_UNLINKED,
		PROFILE_READ_FAILED,
		ALREADY_ACTIVE,
		CHARACTER_ALREADY_ACTIVE,
		CAPACITY_REACHED,
		IDENTITY_BUSY,
		WORLD_PLAYER_IDENTITY_BUSY,
		WORLD_OBJECT_IDENTITY_BUSY,
		AUTOSAVE_IDENTITY_BUSY,
		WORLD_REGISTRATION_MISMATCH,
		RETAINED_IDENTITY_NOT_RECOVERABLE,
		MATERIALIZATION_FAILED_CLEAN,
		MATERIALIZATION_FAILED_RETAINED,
		CLEANUP_FAILED_RETAINED,
		NOT_ACTIVE
	}

	private static final long MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS = 10000;

	private final Object _stateMonitor = new Object();
	private final PhantomProfileRepository _profileRepository;
	private final PhantomIdentityLeaseRegistry _identityRegistry;
	private final PhantomRetainedIdentityRecovery _retainedIdentityRecovery;
	private final PhantomMetrics _metrics;
	private final PhantomDiagnosticTrace _trace;
	private final int _maximumMaterialized;
	private final Semaphore _permits;
	private final ConcurrentHashMap<Long, Entry> _activeByProfile = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Entry> _activeByCharacter = new ConcurrentHashMap<>();
	private final FailureInjector _failureInjector;
	private final long _actionDrainTimeoutMillis;
	private final long _shutdownTimeoutMillis;
	private volatile ServiceState _state = ServiceState.NEW;
	private DrainAttempt _drainAttempt;

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized)
	{
		this(profileRepository, identityRegistry, metrics, trace, maximumMaterialized, FailureInjector.none(), PhantomMaterializedPlayer.DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS, MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS);
	}

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized, FailureInjector failureInjector, long actionDrainTimeoutMillis, long shutdownTimeoutMillis)
	{
		if ((maximumMaterialized < 1) || (maximumMaterialized > 10000))
		{
			throw new IllegalArgumentException("maximumMaterialized must be between 1 and 10000");
		}
		if (actionDrainTimeoutMillis <= 0)
		{
			throw new IllegalArgumentException("actionDrainTimeoutMillis must be positive");
		}
		if ((shutdownTimeoutMillis <= 0) || (shutdownTimeoutMillis > MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS))
		{
			throw new IllegalArgumentException("shutdownTimeoutMillis must be between 1 and 10000");
		}
		_profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
		_identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
		_retainedIdentityRecovery = new PhantomRetainedIdentityRecovery(identityRegistry);
		_metrics = Objects.requireNonNull(metrics, "metrics");
		_trace = Objects.requireNonNull(trace, "trace");
		_maximumMaterialized = maximumMaterialized;
		_permits = new Semaphore(maximumMaterialized, true);
		_failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
		_actionDrainTimeoutMillis = actionDrainTimeoutMillis;
		_shutdownTimeoutMillis = shutdownTimeoutMillis;
	}

	public boolean start()
	{
		synchronized (_stateMonitor)
		{
			if (_state == ServiceState.RUNNING)
			{
				return true;
			}
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	public MaterializeResult materialize(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("profileId must be positive");
		}
		_metrics.recordMaterializationRequested();
		_trace.record("mat.request." + profileId);
		if (_state != ServiceState.RUNNING)
		{
			return rejectMaterialization(ResultStatus.SERVICE_NOT_RUNNING);
		}

		final PhantomProfile profile;
		try
		{
			final Optional<PhantomProfile> found = _profileRepository.find(profileId);
			if (found.isEmpty())
			{
				return rejectMaterialization(ResultStatus.PROFILE_NOT_FOUND);
			}
			profile = found.get();
		}
		catch (RuntimeException e)
		{
			return rejectMaterialization(ResultStatus.PROFILE_READ_FAILED);
		}
		if (profile.characterObjectId() == null)
		{
			return rejectMaterialization(ResultStatus.PROFILE_UNLINKED);
		}

		final int characterObjectId = profile.characterObjectId();
		final Entry entry = new Entry(profileId, characterObjectId, new PhantomMaterializedPlayer(
			characterObjectId,
			_identityRegistry,
			new HeadlessPlayerOutboundSession(16, 128),
			_failureInjector,
			PhantomMaterializedPlayer.LifecycleSupport.none(),
			_actionDrainTimeoutMillis));

		synchronized (_stateMonitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				return rejectMaterialization(ResultStatus.SERVICE_NOT_RUNNING);
			}
			if (_activeByProfile.putIfAbsent(profileId, entry) != null)
			{
				return rejectMaterialization(ResultStatus.ALREADY_ACTIVE);
			}
			if (_activeByCharacter.putIfAbsent(characterObjectId, entry) != null)
			{
				_activeByProfile.remove(profileId, entry);
				return rejectMaterialization(ResultStatus.CHARACTER_ALREADY_ACTIVE);
			}
			if (!_permits.tryAcquire())
			{
				_activeByCharacter.remove(characterObjectId, entry);
				_activeByProfile.remove(profileId, entry);
				return rejectMaterialization(ResultStatus.CAPACITY_REACHED);
			}
			entry._permitHeld = true;
		}

		synchronized (entry)
		{
			if ((_state != ServiceState.RUNNING) || entry._shutdownRequested)
			{
				releaseStoredEntry(entry);
				return rejectMaterialization(ResultStatus.SERVICE_NOT_RUNNING);
			}

			final OwnerSnapshot owner = _identityRegistry.getOwnerSnapshot(characterObjectId);
			if ((owner != null) && (owner.ownerKind() == OwnerKind.REAL_LOGIN) && (owner.state() == OwnerState.RETAINED))
			{
				final PhantomRetainedIdentityRecovery.Result recovery = recoverRetainedIdentityInternal(characterObjectId);
				if (!recovery.recovered())
				{
					releaseStoredEntry(entry);
					return rejectMaterialization(ResultStatus.RETAINED_IDENTITY_NOT_RECOVERABLE);
				}
			}
			else if (owner != null)
			{
				releaseStoredEntry(entry);
				return rejectMaterialization(ResultStatus.IDENTITY_BUSY);
			}

			try
			{
				entry._materializedPlayer.materialize();
				entry._countedActive = true;
				_metrics.recordMaterializationSucceeded();
				_trace.record("mat.success." + profileId);
				return new MaterializeResult(ResultStatus.SUCCESS, snapshot(entry));
			}
			catch (RuntimeException | Error e)
			{
				final boolean retained = entry._materializedPlayer.snapshot().state() != State.STORED;
				if (!retained)
				{
					releaseStoredEntry(entry);
				}
				else
				{
					_metrics.recordMaterializationFailureRetained();
				}
				if (e instanceof MaterializationException materializationFailure)
				{
					final ResultStatus status = switch (materializationFailure.failure())
					{
						case IDENTITY_BUSY -> ResultStatus.IDENTITY_BUSY;
						case WORLD_PLAYER_IDENTITY_BUSY -> ResultStatus.WORLD_PLAYER_IDENTITY_BUSY;
						case WORLD_OBJECT_IDENTITY_BUSY -> ResultStatus.WORLD_OBJECT_IDENTITY_BUSY;
						case AUTOSAVE_IDENTITY_BUSY -> ResultStatus.AUTOSAVE_IDENTITY_BUSY;
						case WORLD_REGISTRATION_MISMATCH -> ResultStatus.WORLD_REGISTRATION_MISMATCH;
						default -> null;
					};
					if (status != null)
					{
						return rejectMaterialization(status, retained ? snapshot(entry) : null);
					}
				}
				_metrics.recordMaterializationRejected();
				return new MaterializeResult(retained ? ResultStatus.MATERIALIZATION_FAILED_RETAINED : ResultStatus.MATERIALIZATION_FAILED_CLEAN, retained ? snapshot(entry) : null);
			}
		}
	}

	public DematerializeResult dematerialize(long profileId)
	{
		return cleanup(profileId, false);
	}

	public DematerializeResult retryCleanup(long profileId)
	{
		return cleanup(profileId, false);
	}

	private DematerializeResult cleanup(long profileId, boolean shutdown)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("profileId must be positive");
		}
		if (!shutdown && (_state != ServiceState.RUNNING))
		{
			return new DematerializeResult(ResultStatus.SERVICE_NOT_RUNNING, null);
		}
		final Entry entry = _activeByProfile.get(profileId);
		if (entry == null)
		{
			return new DematerializeResult(ResultStatus.NOT_ACTIVE, null);
		}
		return cleanupEntry(entry, System.nanoTime() + (_actionDrainTimeoutMillis * 1_000_000L), shutdown);
	}

	private DematerializeResult cleanupEntry(Entry entry, long deadlineNanos, boolean shutdown)
	{
		synchronized (entry)
		{
			if (shutdown)
			{
				entry._shutdownRequested = true;
			}
			try
			{
				entry._materializedPlayer.cleanup(deadlineNanos);
			}
			catch (RuntimeException | Error e)
			{
				if (entry._materializedPlayer.snapshot().state() == State.STORED)
				{
					releaseStoredEntry(entry);
					return new DematerializeResult(ResultStatus.SUCCESS, null);
				}
				_metrics.recordCleanupFailureRetained();
				_trace.record("cleanup.failed." + entry._profileId);
				return new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry));
			}

			releaseStoredEntry(entry);
			_trace.record("cleanup.success." + entry._profileId);
			return new DematerializeResult(ResultStatus.SUCCESS, null);
		}
	}

	private void releaseStoredEntry(Entry entry)
	{
		synchronized (entry)
		{
			if (entry._released || (entry._materializedPlayer.snapshot().state() != State.STORED))
			{
				return;
			}
			_activeByProfile.remove(entry._profileId, entry);
			_activeByCharacter.remove(entry._characterObjectId, entry);
			if (entry._permitHeld)
			{
				entry._permitHeld = false;
				_permits.release();
			}
			if (entry._countedActive)
			{
				entry._countedActive = false;
				_metrics.recordDematerializationSucceeded();
			}
			entry._released = true;
		}
	}

	public Optional<ActionLease> tryAcquireAction(long profileId)
	{
		synchronized (_stateMonitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				return Optional.empty();
			}
			final Entry entry = _activeByProfile.get(profileId);
			return entry == null ? Optional.empty() : Optional.ofNullable(entry._materializedPlayer.tryAcquireAction());
		}
	}

	public Optional<MaterializationSnapshot> find(long profileId)
	{
		final Entry entry = _activeByProfile.get(profileId);
		return entry == null ? Optional.empty() : Optional.of(snapshot(entry));
	}

	public boolean ownsCharacterObjectId(int objectId)
	{
		return _activeByCharacter.containsKey(objectId);
	}

	public List<MaterializationSnapshot> list()
	{
		final List<MaterializationSnapshot> snapshots = new ArrayList<>();
		for (Entry entry : _activeByProfile.values())
		{
			snapshots.add(snapshot(entry));
		}
		snapshots.sort(Comparator.comparingLong(MaterializationSnapshot::profileId));
		return List.copyOf(snapshots);
	}

	public RecoveryResult recoverRetainedIdentity(int characterObjectId)
	{
		if (_state != ServiceState.RUNNING)
		{
			return new RecoveryResult(ResultStatus.SERVICE_NOT_RUNNING, null);
		}
		final PhantomRetainedIdentityRecovery.Result recovery = recoverRetainedIdentityInternal(characterObjectId);
		return new RecoveryResult(recovery.recovered() ? ResultStatus.SUCCESS : ResultStatus.RETAINED_IDENTITY_NOT_RECOVERABLE, recovery);
	}

	private PhantomRetainedIdentityRecovery.Result recoverRetainedIdentityInternal(int characterObjectId)
	{
		final PhantomRetainedIdentityRecovery.Result recovery = _retainedIdentityRecovery.recover(characterObjectId);
		if (recovery.recovered())
		{
			_metrics.recordRetainedRecoverySucceeded();
			_trace.record("recovery.success." + characterObjectId);
		}
		else
		{
			_metrics.recordRetainedRecoveryRejected();
			_trace.record("recovery.reject." + characterObjectId);
		}
		return recovery;
	}

	public ShutdownResult shutdown()
	{
		final long callerDeadlineNanos = System.nanoTime() + (Math.min(_shutdownTimeoutMillis, MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS) * 1_000_000L);
		final DrainAttempt attempt;
		synchronized (_stateMonitor)
		{
			if (_state == ServiceState.STOPPED)
			{
				return new ShutdownResult(ServiceState.STOPPED, List.of());
			}
			if (_state == ServiceState.NEW)
			{
				_state = ServiceState.STOPPED;
				return new ShutdownResult(ServiceState.STOPPED, List.of());
			}
			if ((_drainAttempt != null) && !_drainAttempt.isCompleted())
			{
				attempt = _drainAttempt;
			}
			else if ((_state == ServiceState.RUNNING) || (_state == ServiceState.FAILED))
			{
				if (_activeByProfile.isEmpty())
				{
					_state = ServiceState.STOPPED;
					return new ShutdownResult(ServiceState.STOPPED, List.of());
				}
				_state = ServiceState.STOPPING;
				attempt = new DrainAttempt(System.nanoTime() + (Math.min(_shutdownTimeoutMillis, MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS) * 1_000_000L));
				_drainAttempt = attempt;
				attempt._future = ThreadPool.schedule(() -> runDrainAttempt(attempt), 0);
				if (attempt._future == null)
				{
					completeDrainAttemptLocked(attempt, ServiceState.FAILED, failedProfileIds());
				}
			}
			else
			{
				return new ShutdownResult(_state, failedProfileIds());
			}
		}

		boolean completed = attempt.isCompleted();
		if (!completed)
		{
			final long remainingNanos = callerDeadlineNanos - System.nanoTime();
			if (remainingNanos > 0)
			{
				try
				{
					completed = attempt._completion.await(remainingNanos, TimeUnit.NANOSECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
		}

		synchronized (_stateMonitor)
		{
			if (completed || attempt.isCompleted())
			{
				return new ShutdownResult(attempt._completedState, attempt._failedProfileIds);
			}
			_state = ServiceState.FAILED;
			recordShutdownFailureLocked(attempt);
			return new ShutdownResult(ServiceState.FAILED, failedProfileIds());
		}
	}

	private void runDrainAttempt(DrainAttempt attempt)
	{
		List<Long> failed;
		try
		{
			failed = shutdownPass(sortedEntries(), attempt._deadlineNanos);
			if (!failed.isEmpty() && (System.nanoTime() < attempt._deadlineNanos))
			{
				final List<Entry> retryEntries = new ArrayList<>();
				for (long profileId : failed)
				{
					final Entry entry = _activeByProfile.get(profileId);
					if (entry != null)
					{
						retryEntries.add(entry);
					}
				}
				failed = shutdownPass(retryEntries, attempt._deadlineNanos);
			}
		}
		catch (Throwable throwable)
		{
			failed = failedProfileIds();
		}

		synchronized (_stateMonitor)
		{
			final List<Long> retainedProfileIds = failedProfileIds();
			completeDrainAttemptLocked(attempt, retainedProfileIds.isEmpty() ? ServiceState.STOPPED : ServiceState.FAILED, retainedProfileIds);
		}
	}

	private void completeDrainAttemptLocked(DrainAttempt attempt, ServiceState completedState, List<Long> failedProfileIds)
	{
		attempt._completedState = completedState;
		attempt._failedProfileIds = List.copyOf(failedProfileIds);
		attempt._future = null;
		_state = completedState;
		if (completedState == ServiceState.FAILED)
		{
			recordShutdownFailureLocked(attempt);
		}
		if (_drainAttempt == attempt)
		{
			_drainAttempt = null;
		}
		attempt._completion.countDown();
	}

	private void recordShutdownFailureLocked(DrainAttempt attempt)
	{
		if (!attempt._failureRecorded)
		{
			attempt._failureRecorded = true;
			_metrics.recordShutdownFailure();
		}
	}

	private List<Long> shutdownPass(List<Entry> entries, long deadlineNanos)
	{
		final List<Long> failed = new ArrayList<>();
		for (Entry entry : entries)
		{
			if (System.nanoTime() >= deadlineNanos)
			{
				failed.add(entry._profileId);
				continue;
			}
			final DematerializeResult result = cleanupEntry(entry, deadlineNanos, true);
			if (result.status() != ResultStatus.SUCCESS)
			{
				failed.add(entry._profileId);
			}
		}
		failed.sort(Long::compareTo);
		return List.copyOf(failed);
	}

	private List<Entry> sortedEntries()
	{
		final List<Entry> entries = new ArrayList<>(_activeByProfile.values());
		entries.sort(Comparator.comparingLong(entry -> entry._profileId));
		return entries;
	}

	private List<Long> failedProfileIds()
	{
		return _activeByProfile.keySet().stream().sorted().toList();
	}

	public ServiceSnapshot snapshot()
	{
		return new ServiceSnapshot(_state, _maximumMaterialized, _permits.availablePermits(), _activeByProfile.size(), list());
	}

	public ShutdownSnapshot shutdownSnapshot()
	{
		return new ShutdownSnapshot(_state, _activeByProfile.size());
	}

	private MaterializeResult rejectMaterialization(ResultStatus status)
	{
		return rejectMaterialization(status, null);
	}

	private MaterializeResult rejectMaterialization(ResultStatus status, MaterializationSnapshot snapshot)
	{
		_metrics.recordMaterializationRejected();
		return new MaterializeResult(status, snapshot);
	}

	private static MaterializationSnapshot snapshot(Entry entry)
	{
		final PhantomMaterializedPlayer.Snapshot actor = entry._materializedPlayer.snapshot();
		return new MaterializationSnapshot(
			entry._profileId,
			entry._characterObjectId,
			actor.state(),
			actor.playerRetained(),
			actor.identityLeaseRetained(),
			actor.outboundAttached(),
			actor.actionAdmissionOpen(),
			actor.admittedActionCount(),
			actor.worldPresent(),
			actor.materializedAtNanos(),
			actor.dematerializedAtNanos());
	}

	public record MaterializeResult(ResultStatus status, MaterializationSnapshot snapshot)
	{
	}

	public record DematerializeResult(ResultStatus status, MaterializationSnapshot snapshot)
	{
	}

	public record RecoveryResult(ResultStatus status, PhantomRetainedIdentityRecovery.Result evidence)
	{
	}

	public record MaterializationSnapshot(long profileId, int characterObjectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos)
	{
	}

	public record ShutdownResult(ServiceState state, List<Long> failedProfileIds)
	{
		public ShutdownResult
		{
			failedProfileIds = List.copyOf(failedProfileIds);
		}
	}

	public record ServiceSnapshot(ServiceState state, int maximumMaterialized, int availablePermits, int retainedEntries, List<MaterializationSnapshot> materializations)
	{
		public ServiceSnapshot
		{
			materializations = List.copyOf(materializations);
		}
	}

	public record ShutdownSnapshot(ServiceState state, int retainedEntries)
	{
	}

	private static final class Entry
	{
		private final long _profileId;
		private final int _characterObjectId;
		private final PhantomMaterializedPlayer _materializedPlayer;
		private boolean _permitHeld;
		private boolean _countedActive;
		private boolean _released;
		private boolean _shutdownRequested;

		private Entry(long profileId, int characterObjectId, PhantomMaterializedPlayer materializedPlayer)
		{
			_profileId = profileId;
			_characterObjectId = characterObjectId;
			_materializedPlayer = materializedPlayer;
		}
	}

	private static final class DrainAttempt
	{
		private final CountDownLatch _completion = new CountDownLatch(1);
		private final long _deadlineNanos;
		private ScheduledFuture<?> _future;
		private volatile ServiceState _completedState;
		private volatile List<Long> _failedProfileIds = List.of();
		private boolean _failureRecorded;

		private DrainAttempt(long deadlineNanos)
		{
			_deadlineNanos = deadlineNanos;
		}

		private boolean isCompleted()
		{
			return _completion.getCount() == 0;
		}
	}
}
