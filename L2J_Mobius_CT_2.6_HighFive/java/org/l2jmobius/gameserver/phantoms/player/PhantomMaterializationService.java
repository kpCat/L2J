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
import org.l2jmobius.gameserver.model.actor.Player;
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

	public enum MaterializationPurpose
	{
		NORMAL,
		HISTORICAL_BASELINE,
		NATIVE_CONTEXT_HANDOFF
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
		CLEANUP_PENDING,
		BACKGROUND_RECONCILIATION_BLOCKED,
		CATCHUP_FENCED,
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
	private final Object _incidentMonitor = new Object();
	private final java.util.LinkedHashMap<String, CleanupIncidentArchiveEntry> _cleanupIncidents = new java.util.LinkedHashMap<>();
	private long _cleanupIncidentEvictions;
	private boolean _cleanupEvidenceIncomplete;
	private final FailureInjector _failureInjector;
	private final PhantomMaterializationLifecyclePort _lifecyclePort;
	private final long _actionDrainTimeoutMillis;
	private final long _shutdownTimeoutMillis;
	private volatile ServiceState _state = ServiceState.NEW;
	private DrainAttempt _drainAttempt;

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized)
	{
		this(profileRepository, identityRegistry, metrics, trace, maximumMaterialized, FailureInjector.none(), PhantomMaterializationLifecyclePort.none(), PhantomMaterializedPlayer.DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS, MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS);
	}

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized, PhantomMaterializationLifecyclePort lifecyclePort)
	{
		this(profileRepository, identityRegistry, metrics, trace, maximumMaterialized, FailureInjector.none(), lifecyclePort, PhantomMaterializedPlayer.DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS, MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS);
	}

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized, FailureInjector failureInjector, long actionDrainTimeoutMillis, long shutdownTimeoutMillis)
	{
		this(profileRepository, identityRegistry, metrics, trace, maximumMaterialized, failureInjector, PhantomMaterializationLifecyclePort.none(), actionDrainTimeoutMillis, shutdownTimeoutMillis);
	}

	public PhantomMaterializationService(PhantomProfileRepository profileRepository, PhantomIdentityLeaseRegistry identityRegistry, PhantomMetrics metrics, PhantomDiagnosticTrace trace, int maximumMaterialized, FailureInjector failureInjector, PhantomMaterializationLifecyclePort lifecyclePort, long actionDrainTimeoutMillis, long shutdownTimeoutMillis)
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
		_lifecyclePort = Objects.requireNonNull(lifecyclePort, "lifecyclePort");
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
		return materialize(profileId, MaterializationPurpose.NORMAL, "");
	}

	public MaterializeResult materialize(long profileId, MaterializationPurpose purpose, String ownerClaim)
	{
		Objects.requireNonNull(purpose, "purpose");
		ownerClaim = Objects.requireNonNullElse(ownerClaim, "");
		if (((purpose == MaterializationPurpose.NORMAL) && !ownerClaim.isEmpty()) || ((purpose != MaterializationPurpose.NORMAL) && ownerClaim.isBlank()))
		{
			throw new IllegalArgumentException("Invalid materialization purpose claim.");
		}
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
		final MaterializationLifecycleAttempt lifecycleAttempt = new MaterializationLifecycleAttempt(_lifecyclePort, profileId, characterObjectId);
		try
		{
			_lifecyclePort.beforeMaterialize(profileId, characterObjectId, purpose, ownerClaim);
		}
		catch (PhantomMaterializationLifecyclePort.AdmissionRejectedException exception)
		{
			final String reason = Objects.requireNonNullElse(exception.getMessage(), "admission.rejected");
			org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance().record(profileId, "MATERIALIZE_ADMISSION_REJECT", purpose.name(), "CATCHUP_FENCED", reason.substring(0, Math.min(reason.length(), 192)), characterObjectId, 0, 0);
			abortPreserving(lifecycleAttempt, exception);
			return rejectMaterialization(ResultStatus.CATCHUP_FENCED);
		}
		catch (RuntimeException exception)
		{
			abortPreserving(lifecycleAttempt, exception);
			return rejectMaterialization(ResultStatus.BACKGROUND_RECONCILIATION_BLOCKED);
		}
		catch (Error error)
		{
			abortPreserving(lifecycleAttempt, error);
			throw error;
		}
		Entry ownedEntry = null;
		Throwable primaryFailure = null;
		try
		{
		final PhantomMaterializedPlayer.LifecycleSupport lifecycleSupport = new PhantomMaterializedPlayer.LifecycleSupport()
		{
			@Override
			public void afterPlayerLoad(Player player)
			{
				_lifecyclePort.afterPlayerLoad(profileId, player);
			}

			@Override
			public void beforeStore(Player player)
			{
				_lifecyclePort.beforeStore(profileId, player);
			}

			@Override
			public void afterStore(Player player)
			{
				_lifecyclePort.afterStore(profileId, player);
			}
		};
		final Entry entry = new Entry(profileId, characterObjectId, new PhantomMaterializedPlayer(
			characterObjectId,
			_identityRegistry,
			new HeadlessPlayerOutboundSession(16, 128),
			_failureInjector,
			lifecycleSupport,
			_actionDrainTimeoutMillis));
		ownedEntry = entry;

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

		final boolean shutdownRequested;
		synchronized (entry) { shutdownRequested = (_state != ServiceState.RUNNING) || entry._shutdownRequested; }
		if (shutdownRequested)
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
			synchronized (entry) { entry._countedActive = true; }
			_metrics.recordMaterializationSucceeded();
			_trace.record("mat.success." + profileId);
			lifecycleAttempt.succeed();
			return new MaterializeResult(ResultStatus.SUCCESS, snapshot(entry));
		}
		catch (RuntimeException | Error e)
		{
			primaryFailure = e;
			archiveCleanupIncident(entry);
			final boolean retained = entry._materializedPlayer.snapshot().state() != State.STORED;
			if (!retained) { releaseStoredEntry(entry); }
			else { _metrics.recordMaterializationFailureRetained(); }
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
				if (status != null) { return rejectMaterialization(status, retained ? snapshot(entry) : null); }
			}
			_metrics.recordMaterializationRejected();
			if (e instanceof Error error) { throw error; }
			return new MaterializeResult(retained ? ResultStatus.MATERIALIZATION_FAILED_RETAINED : ResultStatus.MATERIALIZATION_FAILED_CLEAN, retained ? snapshot(entry) : null);
		}
		}
		catch (RuntimeException | Error failure)
		{
			primaryFailure = failure;
			throw failure;
		}
		finally
		{
			Throwable terminalFailure = primaryFailure;
			try
			{
				if (primaryFailure == null) { lifecycleAttempt.abortUnlessCompleted(); }
				else { abortPreserving(lifecycleAttempt, primaryFailure); }
			}
			catch (RuntimeException | Error failure)
			{
				terminalFailure = failure;
				throw failure;
			}
			finally
			{
				if (ownedEntry != null)
				{
					// Terminal native abort and lifecycle hooks precede cleanup admission and permit reuse.
					synchronized (ownedEntry) { ownedEntry._materializationThread = null; }
					ownedEntry._materializationCompletion.countDown();
					try { releaseStoredEntry(ownedEntry); }
					catch (RuntimeException | Error failure)
					{
						if (terminalFailure == null) { throw failure; }
						if (terminalFailure != failure) { terminalFailure.addSuppressed(failure); }
					}
				}
			}
		}
	}

	private static void abortPreserving(MaterializationLifecycleAttempt attempt, Throwable primary)
	{
		try { attempt.abortUnlessCompleted(); }
		catch (RuntimeException | Error secondary)
		{
			if (primary != secondary) { primary.addSuppressed(secondary); }
		}
	}

	public DematerializeResult dematerialize(long profileId)
	{
		return cleanup(profileId, false);
	}

	/** One exact Entry control continuation; polling never waits for native work. */
	public DematerializeResult requestDematerialize(long profileId)
	{
		final Entry entry = _activeByProfile.get(profileId);
		if (entry == null) { return new DematerializeResult(ResultStatus.NOT_ACTIVE, null); }
		return requestCleanup(entry, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(_actionDrainTimeoutMillis), false);
	}

	private DematerializeResult requestCleanup(Entry entry, long deadlineNanos, boolean shutdown)
	{
		synchronized (entry)
		{
			if (entry._released) { return new DematerializeResult(ResultStatus.NOT_ACTIVE, null); }
			if (shutdown) { entry._shutdownRequested = true; }
			if (entry._controlCleanupResult != null) { return entry._controlCleanupResult; }
			if (entry._controlCleanupQueued || entry._cleanupInProgress || (entry._materializationCompletion.getCount() != 0)) { return new DematerializeResult(ResultStatus.CLEANUP_PENDING, snapshot(entry)); }
		}
		try
		{
			if (!entry._materializedPlayer.prepareCleanupDrain()) { return new DematerializeResult(ResultStatus.CLEANUP_PENDING, snapshot(entry)); }
		}
		catch (RuntimeException failure)
		{
			archiveCleanupIncident(entry);
			return new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry));
		}
		synchronized (entry)
		{
			if (entry._released) { return new DematerializeResult(ResultStatus.NOT_ACTIVE, null); }
			if (entry._controlCleanupQueued || entry._cleanupInProgress) { return new DematerializeResult(ResultStatus.CLEANUP_PENDING, snapshot(entry)); }
			entry._controlCleanupQueued = true;
		}
		try
		{
			ThreadPool.executeOrThrow(() ->
			{
				DematerializeResult result = null;
				try
				{
					if (_activeByProfile.get(entry._profileId) == entry) { result = cleanupEntry(entry, deadlineNanos, entry._shutdownRequested); }
				}
				finally { synchronized (entry) { entry._controlCleanupResult = result; entry._controlCleanupQueued = false; } }
			});
		}
		catch (RuntimeException failure)
		{
			synchronized (entry) { entry._controlCleanupQueued = false; }
			return new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry));
		}
		return new DematerializeResult(ResultStatus.CLEANUP_PENDING, snapshot(entry));
	}

	/** Uses the caller's single monotonic lifecycle deadline; no executor wait here. */
	public ShutdownResult requestShutdown(long deadlineNanos)
	{
		synchronized (_stateMonitor)
		{
			if ((_state == ServiceState.STOPPED) || (_state == ServiceState.NEW)) { _state = ServiceState.STOPPED; return new ShutdownResult(_state, List.of()); }
			if (_state == ServiceState.FAILED) { return new ShutdownResult(_state, failedProfileIds()); }
			_state = ServiceState.STOPPING;
		}
		boolean failed = System.nanoTime() >= deadlineNanos;
		for (Entry entry : sortedEntries())
		{
			final var result = requestCleanup(entry, deadlineNanos, true);
			failed |= result.status() == ResultStatus.CLEANUP_FAILED_RETAINED;
		}
		synchronized (_stateMonitor)
		{
			_state = failed ? ServiceState.FAILED : _activeByProfile.isEmpty() ? ServiceState.STOPPED : ServiceState.STOPPING;
			return new ShutdownResult(_state, failedProfileIds());
		}
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
			if (shutdown) { entry._shutdownRequested = true; }
			if (entry._released) { return new DematerializeResult(ResultStatus.NOT_ACTIVE, null); }
			if (entry._cleanupInProgress) { return new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry)); }
			entry._cleanupInProgress = true;
		}
		final DematerializeResult result;
		try
		{
			result = awaitMaterialization(entry, deadlineNanos) ? performCleanupEntry(entry, deadlineNanos) : new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry));
		}
		finally { synchronized (entry) { entry._cleanupInProgress = false; } }
		if (result.status() == ResultStatus.CLEANUP_FAILED_RETAINED) { registerDrainRetry(entry); }
		return result;
	}

	private static boolean awaitMaterialization(Entry entry, long deadlineNanos)
	{
		if (entry._materializationCompletion.getCount() == 0) { return true; }
		if (entry._materializationThread == Thread.currentThread()) { return false; }
		final long remainingNanos = deadlineNanos - System.nanoTime();
		if (remainingNanos <= 0) { return false; }
		try { return entry._materializationCompletion.await(remainingNanos, TimeUnit.NANOSECONDS); }
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); return false; }
	}

	private DematerializeResult performCleanupEntry(Entry entry, long deadlineNanos)
	{
			try
			{
				entry._materializedPlayer.cleanup(deadlineNanos);
			}
			catch (RuntimeException | Error e)
			{
				archiveCleanupIncident(entry);
				if (entry._materializedPlayer.snapshot().state() == State.STORED)
				{
					releaseStoredEntry(entry);
					if (e instanceof Error error) { throw error; }
					return new DematerializeResult(ResultStatus.SUCCESS, null);
				}
				_metrics.recordCleanupFailureRetained();
				_trace.record("cleanup.failed." + entry._profileId);
				if (e instanceof Error error) { throw error; }
				return new DematerializeResult(ResultStatus.CLEANUP_FAILED_RETAINED, snapshot(entry));
			}

			releaseStoredEntry(entry);
			_trace.record("cleanup.success." + entry._profileId);
			return new DematerializeResult(ResultStatus.SUCCESS, null);
	}

	private void registerDrainRetry(Entry entry)
	{
		final PhantomNativeWorkScope scope = entry._materializedPlayer.retryableDrainScope();
		if (scope == null) { return; }
		synchronized (entry)
		{
			if (entry._released || entry._retryRegistered || (entry._automaticCleanupAttempts >= 2) || (_activeByProfile.get(entry._profileId) != entry)) { return; }
			entry._retryRegistered = true;
		}
		scope.onQuiescent(() ->
		{
			synchronized (entry)
			{
				if (entry._released || (entry._automaticCleanupAttempts >= 2)) { entry._retryRegistered = false; return; }
				entry._automaticCleanupAttempts++;
			}
			// The ordinary scheduler publishes the control body. Never use strict inline fallback here.
			final var future = ThreadPool.schedule(() ->
			{
				synchronized (entry) { entry._retryRegistered = false; }
				if ((_activeByProfile.get(entry._profileId) != entry) || (entry._materializedPlayer.retryableDrainScope() != scope) || (scope.outstanding() != 0)) { return; }
				cleanupEntry(entry, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(_actionDrainTimeoutMillis), entry._shutdownRequested);
			}, 0);
			if (future == null)
			{
				synchronized (entry) { entry._retryRegistered = false; }
				_trace.record("cleanup.retry.submit.failed." + entry._profileId);
			}
		});
	}

	private void releaseStoredEntry(Entry entry)
	{
		archiveCleanupIncident(entry);
		synchronized (entry)
		{
			if (entry._released || (entry._materializationCompletion.getCount() != 0) || (entry._materializedPlayer.snapshot().state() != State.STORED))
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

	/** Direct immutable lookup used by actual-delivery observers; never scans profiles. */
	public Optional<MaterializationSnapshot> findByCharacterObjectId(int characterObjectId)
	{
		final Entry entry = _activeByCharacter.get(characterObjectId);
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
		final var materializations = list();
		synchronized (_incidentMonitor)
		{
			return new ServiceSnapshot(_state, _maximumMaterialized, _permits.availablePermits(), _activeByProfile.size(), materializations,
				List.copyOf(_cleanupIncidents.values()), _cleanupIncidentEvictions, _cleanupEvidenceIncomplete);
		}
	}

	private void archiveCleanupIncident(Entry entry)
	{
		final var actor = entry._materializedPlayer.snapshot();
		if (actor.firstCleanupIncident() == null) { return; }
		final String key = actor.firstCleanupIncident().id(entry._profileId);
		final var record = new CleanupIncidentArchiveEntry(entry._profileId, key, actor.firstCleanupIncident(), actor.latestCleanupIncident(), actor.state() == State.STORED);
		synchronized (_incidentMonitor)
		{
			if (!_cleanupIncidents.containsKey(key) && (_cleanupIncidents.size() >= 256))
			{
				final String finished = _cleanupIncidents.entrySet().stream().filter(value -> value.getValue().finished()).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
				final String evicted = finished == null ? _cleanupIncidents.keySet().iterator().next() : finished;
				_cleanupIncidents.remove(evicted);
				_cleanupIncidentEvictions++;
				if (finished == null) { _cleanupEvidenceIncomplete = true; }
			}
			_cleanupIncidents.put(key, record);
		}
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
			actor.dematerializedAtNanos(),
			actor.cleanupPhase(),
			actor.cleanupFailurePhase(),
			actor.cleanupFailureClass(),
			actor.cleanupFailureMessage(),
			actor.cleanupFailureSequence(),
			actor.cleanupFailureAdmittedActionCount(),
			actor.cleanupFailureCause(), actor.firstCleanupIncident(), actor.latestCleanupIncident());
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

	public record MaterializationSnapshot(long profileId, int characterObjectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos,
		PhantomMaterializedPlayer.CleanupPhase cleanupPhase, PhantomMaterializedPlayer.CleanupPhase cleanupFailurePhase, String cleanupFailureClass, String cleanupFailureMessage, long cleanupFailureSequence, int cleanupFailureAdmittedActionCount, String cleanupFailureCause, PhantomCleanupIncident firstCleanupIncident, PhantomCleanupIncident latestCleanupIncident)
	{
	}

	public record ShutdownResult(ServiceState state, List<Long> failedProfileIds)
	{
		public ShutdownResult
		{
			failedProfileIds = List.copyOf(failedProfileIds);
		}
	}

	public record CleanupIncidentArchiveEntry(long profileId, String incidentId, PhantomCleanupIncident first, PhantomCleanupIncident latest, boolean finished) { }

	public record ServiceSnapshot(ServiceState state, int maximumMaterialized, int availablePermits, int retainedEntries, List<MaterializationSnapshot> materializations, List<CleanupIncidentArchiveEntry> cleanupIncidents, long cleanupIncidentEvictions, boolean cleanupEvidenceIncomplete)
	{
		public ServiceSnapshot(ServiceState state, int maximumMaterialized, int availablePermits, int retainedEntries, List<MaterializationSnapshot> materializations)
		{
			this(state, maximumMaterialized, availablePermits, retainedEntries, materializations, List.of(), 0, false);
		}

		public ServiceSnapshot
		{
			materializations = List.copyOf(materializations);
			cleanupIncidents = List.copyOf(cleanupIncidents);
		}
	}

	public record ShutdownSnapshot(ServiceState state, int retainedEntries)
	{
	}

	private static final class MaterializationLifecycleAttempt
	{
		private final PhantomMaterializationLifecyclePort _port;
		private final long _profileId;
		private final int _characterObjectId;
		private boolean _terminal;

		private MaterializationLifecycleAttempt(PhantomMaterializationLifecyclePort port, long profileId, int characterObjectId)
		{
			_port = port;
			_profileId = profileId;
			_characterObjectId = characterObjectId;
		}

		private void succeed()
		{
			if (_terminal)
			{
				throw new IllegalStateException("Materialization lifecycle attempt already completed.");
			}
			_terminal = true;
			_port.materializeSucceeded(_profileId, _characterObjectId);
		}

		private void abortUnlessCompleted()
		{
			if (!_terminal)
			{
				_terminal = true;
				_port.materializeAborted(_profileId, _characterObjectId);
			}
		}
	}

	private static final class Entry
	{
		private final long _profileId;
		private final int _characterObjectId;
		private final PhantomMaterializedPlayer _materializedPlayer;
		private final CountDownLatch _materializationCompletion = new CountDownLatch(1);
		private volatile Thread _materializationThread = Thread.currentThread();
		private boolean _permitHeld;
		private boolean _countedActive;
		private boolean _released;
		private volatile boolean _shutdownRequested;
		private boolean _cleanupInProgress;
		private boolean _controlCleanupQueued;
		private DematerializeResult _controlCleanupResult;
		private boolean _retryRegistered;
		private int _automaticCleanupAttempts;

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
