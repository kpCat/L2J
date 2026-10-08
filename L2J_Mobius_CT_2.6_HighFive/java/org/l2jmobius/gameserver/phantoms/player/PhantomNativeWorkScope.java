package org.l2jmobius.gameserver.phantoms.player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Cancellation;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Owner;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Ticket;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.TimerRegistration;

/** One monitor linearizes roots, transitive children, completion and checkpoint sealing. */
public final class PhantomNativeWorkScope implements Owner
{
	public enum State { OPEN, DRAINING, SEALED, DETACHED }
	private enum WorkState { RESERVED, RUNNING, COMPLETED, CANCELLED }
	public static final int MAX_OUTSTANDING = 1024;
	private final Object _monitor;
	private final Player _player;
	private final PhantomIdentityLeaseRegistry.Lease _identity;
	private final long _epoch;
	private final PlayerNativeEvidence _evidence;
	private final Map<Long, WorkTicket> _outstanding = new LinkedHashMap<>();
	private final Map<Long, PendingTimer> _pendingTimers = new LinkedHashMap<>();
	private volatile State _state = State.OPEN;
	private long _sequence;
	private int _peak;
	private volatile String _failure = "";
	private volatile Thread _checkpointThread;
	private volatile boolean _permanentSeal;
	private boolean _pendingResumeEligible;
	private long _incidentSequence;
	private volatile PhantomCleanupIncident _firstNativeIncident;
	private PhantomCleanupIncident _latestNativeIncident;
	private Runnable _quiescentEnqueue;
	private volatile Map<String, String> _publishedWork = Map.of();
	private volatile java.util.List<WorkTicket> _publishedEventTickets = java.util.List.of();
	private volatile String _lastCompletedEvent = "";
	private volatile String _checkpointStage = "";
	private volatile CheckpointFailure _checkpointFirstFailure;
	public enum CheckpointOutcome { WAIT_EARNED, RETRY_BEFORE_WRITE, RESOLVE_RECEIPT, VERIFY_WRITE_OUTCOME, PUBLISH_COMMITTED, RESUME, TERMINAL_RETAIN }
	public record CheckpointKey(long profileId, int objectId, long epoch, long goalId, long goalRevision, String requestId) { }
	public record CheckpointResult(CheckpointOutcome outcome, String phase, String reason) { }
	private static final class OwnedCheckpoint
	{
		final CheckpointKey key;
		boolean writeAttempted;
		boolean finalized;
		boolean published;
		boolean unsafe;
		String reason = "";
		OwnedCheckpoint(CheckpointKey key) { this.key = key; }
	}
	private OwnedCheckpoint _ownedCheckpoint;
	private volatile CheckpointResult _ownedCheckpointResult;
	/** Task-local observer installs this optional scalar hook; default production path has no observer. */
	private static volatile java.util.function.BiConsumer<Player, String> _checkpointObserver;
	private volatile String _checkpointObserverFailure = "";
	private record CheckpointFailure(String stage, String exceptionClass, String message, String stack) { }

	/** Scalar observation only, tied to this exact checkpoint thread; no other actor locks or I/O. */
	public void checkpointStage(String stage)
	{
		if ((_checkpointThread == Thread.currentThread()) && isCurrent())
		{
			_checkpointStage = stage;
			final var owned = _ownedCheckpoint;
			if (owned != null)
			{
				if (stage.equals("INVENTORY_FLUSH") || stage.equals("PREPARE_ATTEMPTED") || stage.equals("PREPARED") || stage.equals("NATIVE_ATTEMPTED")) { owned.writeAttempted = true; }
				if (stage.equals("FINALIZED")) { owned.finalized = true; }
				if (stage.equals("COMPLETED")) { owned.published = true; }
			}
			final var observer = _checkpointObserver;
			if (observer != null)
			{
				try { observer.accept(_player, stage); }
				catch (Throwable failure) { if (_checkpointObserverFailure.isEmpty()) { _checkpointObserverFailure = PhantomCleanupIncident.bounded(failure.getClass().getName() + ":" + failure.getMessage(), 256); } }
			}
		}
	}
	public void recordCheckpointFailure(Throwable failure)
	{
		synchronized (_monitor)
		{
			if ((_checkpointFirstFailure == null) && isCurrent())
			{
				_checkpointFirstFailure = new CheckpointFailure(_checkpointStage, failure.getClass().getName(), PhantomCleanupIncident.bounded(failure.getMessage(), 256), PhantomCleanupIncident.bounded(java.util.Arrays.toString(java.util.Arrays.copyOf(failure.getStackTrace(), Math.min(6, failure.getStackTrace().length))), 1600));
			}
		}
	}

	/** Exact visible-store caller only. Waiting releases its worker; earned completion only publishes control. */
	public CheckpointResult ownedCheckpoint(CheckpointKey key, BooleanSupplier action, Runnable enqueue)
	{
		if (PlayerNativeWork.current(this) != null) { throw new IllegalStateException("NATIVE_WORK_SELF_DRAIN"); }
		final OwnedCheckpoint owned;
		synchronized (_monitor)
		{
			if (!isCurrent() || _permanentSeal || !_failure.isEmpty() || key.objectId() != _player.getObjectId() || key.epoch() != _epoch)
			{
				return new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, _checkpointStage, "owned_checkpoint.owner_or_cleanup_changed:" + _failure);
			}
			if (_checkpointThread != null) { return new CheckpointResult(CheckpointOutcome.WAIT_EARNED, _checkpointStage, "owned_checkpoint.executing"); }
			if (_ownedCheckpoint == null)
			{
				if (_state != State.OPEN) { return new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, _checkpointStage, "owned_checkpoint.unproven_seal"); }
				_ownedCheckpoint = new OwnedCheckpoint(key);
				_ownedCheckpointResult = null;
				_checkpointStage = "ADMITTED";
				_state = State.DRAINING;
			}
			owned = _ownedCheckpoint;
			if (!owned.key.equals(key)) { return new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, _checkpointStage, "owned_checkpoint.request_changed"); }
			if ((_ownedCheckpointResult != null) && (_ownedCheckpointResult.outcome() == CheckpointOutcome.VERIFY_WRITE_OUTCOME || _ownedCheckpointResult.outcome() == CheckpointOutcome.TERMINAL_RETAIN)) { return _ownedCheckpointResult; }
			if (!_outstanding.isEmpty())
			{
				_checkpointStage = "WAIT_EARNED";
				if (_quiescentEnqueue == null) { _quiescentEnqueue = enqueue; }
				return _ownedCheckpointResult = new CheckpointResult(CheckpointOutcome.WAIT_EARNED, _checkpointStage, "owned_checkpoint.earned_pending");
			}
			_quiescentEnqueue = null;
			_state = State.SEALED;
			_checkpointThread = Thread.currentThread();
			if (!owned.writeAttempted) { _checkpointStage = "SEALED"; }
		}
		boolean completed = false;
		try { completed = action.getAsBoolean(); }
		catch (RuntimeException | Error failure)
		{
			recordCheckpointFailure(failure);
			if (owned.reason.isEmpty()) { owned.reason = failure.getClass().getName() + ":" + PhantomCleanupIncident.bounded(failure.getMessage(), 256); }
		}
		finally
		{
			synchronized (_monitor)
			{
				final boolean exact = isCurrent() && !_permanentSeal && _failure.isEmpty();
				final CheckpointOutcome outcome = !exact || owned.unsafe ? CheckpointOutcome.TERMINAL_RETAIN
					: completed && owned.finalized && owned.published && !_player.hasPendingOwnedStore() ? CheckpointOutcome.RESUME
					: !owned.writeAttempted ? CheckpointOutcome.RETRY_BEFORE_WRITE
					: _player.hasPendingOwnedStore() ? CheckpointOutcome.RESOLVE_RECEIPT
					: owned.finalized ? CheckpointOutcome.PUBLISH_COMMITTED : CheckpointOutcome.VERIFY_WRITE_OUTCOME;
				_ownedCheckpointResult = new CheckpointResult(outcome, _checkpointStage, owned.reason);
				if (owned.reason.isEmpty() && !completed) { _ownedCheckpointResult = new CheckpointResult(outcome, _checkpointStage, "owned_checkpoint.business_rejected:" + _checkpointStage); }
				if (outcome == CheckpointOutcome.RESUME || outcome == CheckpointOutcome.RETRY_BEFORE_WRITE)
				{
					_state = State.OPEN;
					_ownedCheckpoint = null;
				}
				_pendingResumeEligible = exact && _player.hasPendingOwnedStore();
				_checkpointThread = null;
				_monitor.notifyAll();
			}
		}
		return _ownedCheckpointResult;
	}
	public boolean ownedCheckpointNeedsPublication(CheckpointKey key)
	{
		synchronized (_monitor) { return _ownedCheckpoint != null && _ownedCheckpoint.key.equals(key) && _ownedCheckpoint.finalized; }
	}
	/** Negative executing-boundary proof is terminal; it never clears a native incident or ownership. */
	public void retainOwnedCheckpoint(CheckpointKey key, String reason)
	{
		synchronized (_monitor)
		{
			if (_checkpointThread == Thread.currentThread() && _ownedCheckpoint != null && _ownedCheckpoint.key.equals(key))
			{
				_ownedCheckpoint.unsafe = true;
				if (_ownedCheckpoint.reason.isEmpty()) { _ownedCheckpoint.reason = reason; }
			}
		}
	}

	PhantomNativeWorkScope(Object monitor, Player player, PhantomIdentityLeaseRegistry.Lease identity, long epoch)
	{
		_monitor = monitor; _player = player; _identity = identity; _epoch = epoch;
		_evidence = new PlayerNativeEvidence(player.getObjectId(), epoch);
	}

	@Override public boolean isCurrent()
	{
		return !_identity.isClosed() && (_player.getObjectId() == _identity.objectId()) && (_player.getNativeWorkOwner() == this);
	}
	@Override public Player player() { return _player; }
	@Override public PlayerNativeEvidence evidence() { return _evidence; }
	@Override public boolean nativeObservationHealthy() { return _failure.isEmpty() && !_player.hasPendingOwnedStore(); }
	/** Only an exact healthy control checkpoint is temporary; shutdown and native failure never are. */
	public boolean temporaryCheckpoint()
	{
		synchronized (_monitor)
		{
			return isCurrent() && ((_checkpointThread != null) || (_ownedCheckpoint != null)) && !_permanentSeal && _failure.isEmpty() && !_player.hasPendingOwnedStore() && ((_state == State.DRAINING) || (_state == State.SEALED));
		}
	}
	public boolean checkpointPauseAllowed(boolean witnessed)
	{
		synchronized (_monitor)
		{
			return isCurrent() && !_permanentSeal && _failure.isEmpty() && !_player.hasPendingOwnedStore() && (temporaryCheckpoint() || (witnessed && (_state == State.OPEN)));
		}
	}

	@Override public Ticket reserve(Ticket parent, String kind, Semantics semantics)
	{
		synchronized (_monitor)
		{
			final boolean child = (parent instanceof WorkTicket ticket) && (ticket.owner() == this) && (ticket._state == WorkState.RUNNING);
			if (!isCurrent() || (_permanentSeal && (_state == State.OPEN)) || ((_state != State.OPEN) && !((_state == State.DRAINING) && child))) { return null; }
			if ((parent != null) && !child) { throw new IllegalStateException("NATIVE_WORK_PARENT_EXPIRED"); }
			if (_outstanding.size() >= MAX_OUTSTANDING)
			{
				if (child && (semantics == Semantics.EARNED)) { _failure = "UNREGISTERED_EARNED_WORK_CAP"; }
				throw new IllegalStateException("NATIVE_WORK_ACCOUNTING_CAP");
			}
			final var ticket = new WorkTicket(++_sequence, kind, semantics, child ? ((WorkTicket) parent)._depth + 1 : 0);
			_outstanding.put(ticket._id, ticket);
			_peak = Math.max(_peak, _outstanding.size());
			publishWork();
			return ticket;
		}
	}

	@Override public void recordFailure(Throwable failure)
	{
		synchronized (_monitor)
		{
			if (_failure.isEmpty()) { _failure = "NATIVE_WORK_FAILED:" + failure.getClass().getName(); }
			if (_state == State.OPEN) { _state = State.DRAINING; }
			_quiescentEnqueue = null;
			_latestNativeIncident = PhantomCleanupIncident.capture(_player.getObjectId(), _epoch, ++_incidentSequence,
				PhantomMaterializedPlayer.CleanupPhase.ACTION_DRAIN, "native-work", _outstanding.size(), failure);
			if (_firstNativeIncident == null) { _firstNativeIncident = _latestNativeIncident; }
		}
	}
	public PhantomCleanupIncident firstNativeIncident() { synchronized (_monitor) { return _firstNativeIncident; } }
	public PhantomCleanupIncident latestNativeIncident() { synchronized (_monitor) { return _latestNativeIncident; } }
	public int pendingTimers() { synchronized (_monitor) { return _pendingTimers.size(); } }

	@Override public TimerRegistration registerTimer(Ticket parent, String kind, boolean repeating, Runnable stop)
	{
		synchronized (_monitor)
		{
			final boolean child = (parent instanceof WorkTicket ticket) && (ticket.owner() == this) && (ticket._state == WorkState.RUNNING);
			final boolean earned = child && !repeating && (parent.semantics() == Semantics.EARNED);
			if (!isCurrent() || ((_state != State.OPEN) && !((_state == State.DRAINING) && earned))) { return null; }
			if ((parent != null) && !child) { throw new IllegalStateException("NATIVE_WORK_PARENT_EXPIRED"); }
			if (_pendingTimers.size() >= MAX_OUTSTANDING)
			{
				final var failure = new IllegalStateException("NATIVE_TIMER_ACCOUNTING_CAP");
				recordFailure(failure);
				throw failure;
			}
			final Ticket obligation = earned ? reserve(parent, kind, Semantics.EARNED) : null;
			final var registration = new PendingTimer(++_sequence, kind, repeating, obligation, stop);
			_pendingTimers.put(registration._id, registration);
			publishWork();
			return registration;
		}
	}

	public int outstanding() { synchronized (_monitor) { return _outstanding.size(); } }
	/** The hook only publishes a service control task; it never performs cleanup inline. */
	public void onQuiescent(Runnable enqueue)
	{
		final Runnable ready;
		synchronized (_monitor)
		{
			if (!isCurrent() || !_permanentSeal || (_state != State.DRAINING) || !_failure.isEmpty()) { return; }
			if (_quiescentEnqueue != null) { return; }
			_quiescentEnqueue = enqueue;
			ready = takeQuiescentEnqueue();
		}
		if (ready != null) { ready.run(); }
	}

	private Runnable takeQuiescentEnqueue()
	{
		if (!_outstanding.isEmpty() || !_failure.isEmpty() || !isCurrent()) { return null; }
		final Runnable ready = _quiescentEnqueue;
		_quiescentEnqueue = null;
		return ready;
	}

	public boolean retryableDrain() { synchronized (_monitor) { return isCurrent() && _permanentSeal && (_state == State.DRAINING) && _failure.isEmpty(); } }
	public boolean open() { synchronized (_monitor) { return (_state == State.OPEN) && isCurrent(); } }
	public long epoch() { return _epoch; }
	@Override public boolean sealed() { synchronized (_monitor) { return (_state == State.SEALED) && _outstanding.isEmpty() && isCurrent(); } }

	@Override public <T> T checkpoint(Supplier<T> action)
	{
		return checkpoint(action, null, false);
	}

	@Override public <T> T pendingStoreCheckpoint(Object ownerKey, Supplier<T> action)
	{
		return checkpoint(action, ownerKey, false);
	}

	/** Only the materialization cleanup owner invokes this terminal capture/store stage. */
	void terminalStore(Runnable action)
	{
		checkpoint(() -> { action.run(); return null; }, null, true);
	}

	private <T> T checkpoint(Supplier<T> action, Object pendingOwner, boolean terminal)
	{
		if (PlayerNativeWork.current(this) != null) { throw new IllegalStateException("NATIVE_WORK_SELF_DRAIN"); }
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5000);
		final boolean reopen;
		final boolean nested;
		synchronized (_monitor)
		{
			nested = _checkpointThread == Thread.currentThread();
			while (!nested && (_checkpointThread != null)) { await(deadline); }
			if (!isCurrent() || (_state == State.DETACHED)) { throw new IllegalStateException("NATIVE_CHECKPOINT_OWNER_CHANGED"); }
			final boolean pendingResume = (pendingOwner != null) && _pendingResumeEligible && !_permanentSeal && _failure.isEmpty() && _player.hasOwnedStoreBoundary(pendingOwner) && _player.hasPendingOwnedStore();
			if (!nested && terminal && (!_permanentSeal || (_state != State.SEALED) || !_outstanding.isEmpty() || !_pendingTimers.isEmpty() || !_failure.isEmpty())) { throw new IllegalStateException("NATIVE_TERMINAL_STORE_NOT_QUIESCENT"); }
			if (!nested && !terminal && (pendingOwner == null) && ((_state != State.OPEN) || _permanentSeal)) { throw new IllegalStateException("NATIVE_CHECKPOINT_ADMISSION_CLOSED"); }
			if ((pendingOwner != null) && !nested && !pendingResume && (_state != State.OPEN)) { throw new IllegalStateException("NATIVE_PENDING_RESUME_NOT_AUTHORIZED"); }
			reopen = (_state == State.OPEN) || pendingResume;
			if (!nested) { _checkpointThread = Thread.currentThread(); _checkpointStage = "WAIT_EARNED"; }
		}
		if (nested) { return action.get(); }
		boolean successful = false;
		try
		{
			drain(deadline);
			_checkpointStage = "SEALED";
			final T result = action.get();
			successful = true;
			return result;
		}
		finally
		{
			synchronized (_monitor)
			{
				// A failed/unknown store stays fenced. No automatic replay or reopen.
				if (successful && reopen && !_permanentSeal && !_player.hasPendingOwnedStore() && _failure.isEmpty() && isCurrent()) { _state = State.OPEN; }
				if (reopen && !_permanentSeal) { _pendingResumeEligible = (_state != State.OPEN) && _player.hasPendingOwnedStore() && _failure.isEmpty() && isCurrent(); }
				_checkpointThread = null;
				_monitor.notifyAll();
			}
		}
	}

	/** Close roots without parking a shared worker needed by an earned descendant. */
	boolean beginTerminalDrain()
	{
		final var stops = new ArrayList<Runnable>();
		synchronized (_monitor)
		{
			if (!isCurrent() || !_failure.isEmpty()) { throw new IllegalStateException("NATIVE_DRAIN_OWNER_OR_FAILURE:" + _failure); }
			if ((_checkpointThread != null) || (_ownedCheckpoint != null) || _player.hasPendingOwnedStore()) { return false; }
			_permanentSeal = true;
			if (_state != State.SEALED) { _state = State.DRAINING; }
			for (PendingTimer timer : new ArrayList<>(_pendingTimers.values()))
			{
				if (timer._obligation == null) { timer.stopLocked(); stops.add(timer._stop); }
			}
		}
		for (Runnable stop : stops) { stop.run(); }
		synchronized (_monitor) { return (_checkpointThread == null) && _outstanding.isEmpty() && _pendingTimers.isEmpty(); }
	}

	public void drainAndSeal(long deadlineNanos)
	{
		if (PlayerNativeWork.current(this) != null) { throw new IllegalStateException("NATIVE_WORK_SELF_DRAIN"); }
		synchronized (_monitor)
		{
			_permanentSeal = true;
			_quiescentEnqueue = null;
			while ((_checkpointThread != null) && (_checkpointThread != Thread.currentThread())) { await(deadlineNanos); }
		}
		drain(deadlineNanos);
		synchronized (_monitor)
		{
			if (isCurrent() && _permanentSeal && (_state == State.SEALED) && _outstanding.isEmpty() && _pendingTimers.isEmpty() && _failure.isEmpty() && !_player.hasPendingOwnedStore()) { _evidence.retireNativeEpisodes(); }
		}
	}

	/** An internal terminal stage may fork earned work without reopening ordinary admission. */
	public void teardown(Runnable action, long deadlineNanos)
	{
		if (PlayerNativeWork.current(this) != null) { throw new IllegalStateException("NATIVE_WORK_SELF_DRAIN"); }
		final WorkTicket ticket;
		synchronized (_monitor)
		{
			if (!isCurrent() || !_permanentSeal || (_checkpointThread != null) || (_state != State.SEALED) || !_outstanding.isEmpty() || !_failure.isEmpty()) { throw new IllegalStateException("NATIVE_TEARDOWN_NOT_QUIESCENT"); }
			_state = State.DRAINING;
			ticket = new WorkTicket(++_sequence, "NATIVE_TEARDOWN", Semantics.EARNED, 0);
			_outstanding.put(ticket._id, ticket); _peak = Math.max(_peak, _outstanding.size());
		}
		if (!ticket.tryStart()) { throw new IllegalStateException("NATIVE_TEARDOWN_OWNER_CHANGED"); }
		Throwable failure = null;
		try (var context = PlayerNativeWork.enter(ticket)) { action.run(); }
		catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
		finally { ticket.complete(failure); }
		drainAndSeal(deadlineNanos);
	}

	private void await(long deadlineNanos)
	{
		final long remaining = deadlineNanos - System.nanoTime();
		if (remaining <= 0) { throw new DrainTimeoutException(snapshot()); }
		try { _monitor.wait(Math.max(1, remaining / 1_000_000)); }
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("NATIVE_WORK_DRAIN_INTERRUPTED", failure); }
	}

	private void drain(long deadlineNanos)
	{
		final var stops = new ArrayList<Runnable>();
		synchronized (_monitor)
		{
			if (_state == State.DETACHED) { return; }
			if ((_state == State.SEALED) && _outstanding.isEmpty() && (!_permanentSeal || _pendingTimers.isEmpty())) { return; }
			_state = State.DRAINING;
			if (_permanentSeal)
			{
				for (PendingTimer timer : new ArrayList<>(_pendingTimers.values()))
				{
					if (timer._obligation == null) { timer.stopLocked(); stops.add(timer._stop); }
				}
			}
		}
		// Native registry locks and callbacks are never entered under the owner monitor.
		for (Runnable stop : stops) { stop.run(); }
		synchronized (_monitor)
		{
			while (!_outstanding.isEmpty())
			{
				await(deadlineNanos);
			}
			if (!_failure.isEmpty()) { throw new IllegalStateException(_failure); }
			if (!isCurrent()) { throw new IllegalStateException("NATIVE_WORK_OWNER_CHANGED"); }
			_state = State.SEALED;
		}
	}

	public void detach()
	{
		synchronized (_monitor)
		{
			if ((_state != State.SEALED) || !_outstanding.isEmpty() || !_pendingTimers.isEmpty()) { throw new IllegalStateException("NATIVE_WORK_DETACH_NOT_QUIESCENT"); }
			_state = State.DETACHED;
			_quiescentEnqueue = null;
		}
	}

	public String snapshot()
	{
		synchronized (_monitor)
		{
			return "epoch=" + _epoch + " state=" + _state + " outstanding=" + _outstanding.size() + " pendingTimers=" + _pendingTimers.size() + " peak=" + _peak + " failure=" + _failure
				+ " eventDispatch=" + _publishedEventTickets.stream().map(WorkTicket::dispatchSnapshot).toList()
				+ " work=" + _outstanding.values().stream().limit(8).map(ticket -> ticket._id + ":" + ticket._kind + ":" + ticket._state + ":depth=" + ticket._depth + ":ageMs=" + ((System.nanoTime() - ticket._created) / 1_000_000)).toList();
		}
	}
	/** Bounded scalar publication under the existing monitor; diagnostic readers never wait on it. */
	private void publishWork()
	{
		final long now = System.nanoTime();
		_publishedWork = Map.of("nativeOwnerWorkSampleNanos", Long.toString(now), "nativeOwnerOutstanding", Integer.toString(_outstanding.size()), "nativeOwnerPendingTimers", Integer.toString(_pendingTimers.size()),
			"nativeOwnerWork", _outstanding.values().stream().limit(8).map(ticket -> ticket._id + ":" + ticket._kind + ":" + ticket._state + ":" + ticket._semantics + ":depth=" + ticket._depth + ":ageMs=" + ((now - ticket._created) / 1_000_000)).toList().toString());
		if (org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled())
		{
			_publishedEventTickets = _outstanding.values().stream().filter(ticket -> ticket._traceDispatch).limit(8).toList();
		}
	}
	public Map<String, String> diagnosticScalars()
	{
		final Map<String, String> fields = new LinkedHashMap<>(_publishedWork);
		fields.put("nativeEventDispatch", _publishedEventTickets.stream().map(WorkTicket::dispatchSnapshot).toList().toString());
		fields.put("nativeLastCompletedEvent", _lastCompletedEvent);
		fields.put("nativeOwnerRead", "NONATOMIC_VOLATILE");
		fields.put("nativeOwnerState", _state.name()); fields.put("nativeOwnerCurrent", Boolean.toString(isCurrent()));
		fields.put("nativeOwnerPermanentSeal", Boolean.toString(_permanentSeal)); fields.put("nativeOwnerCheckpoint", Boolean.toString(_checkpointThread != null));
		fields.put("nativeOwnerFailure", _failure);
		fields.put("nativeCheckpointStage", _checkpointStage);
		fields.put("nativeCheckpointObserverFailure", _checkpointObserverFailure);
		final var ownedResult = _ownedCheckpointResult;
		fields.put("nativeCheckpointOutcome", ownedResult == null ? "" : ownedResult.outcome().name());
		fields.put("nativeCheckpointReason", ownedResult == null ? "" : ownedResult.reason());
		final var checkpoint = _checkpointFirstFailure;
		fields.put("nativeCheckpointFirstStage", checkpoint == null ? "" : checkpoint.stage());
		fields.put("nativeCheckpointFirstClass", checkpoint == null ? "" : checkpoint.exceptionClass());
		fields.put("nativeCheckpointFirstMessage", checkpoint == null ? "" : checkpoint.message());
		fields.put("nativeCheckpointFirstStack", checkpoint == null ? "" : checkpoint.stack());
		final var first = _firstNativeIncident;
		fields.put("nativeFirstIncident", first == null ? "" : first.exceptionClass() + ":" + first.message());
		fields.put("nativeFirstIncidentUtc", first == null ? "" : first.utc());
		fields.put("nativeFirstIncidentDetail", first == null ? "" : first.detail());
		return java.util.Collections.unmodifiableMap(fields);
	}

	public static final class DrainTimeoutException extends IllegalStateException
	{
		private static final long serialVersionUID = 1L;
		private DrainTimeoutException(String detail) { super("NATIVE_WORK_DRAIN_TIMEOUT " + detail); }
	}

	private final class WorkTicket implements Ticket
	{
		private final long _id;
		private final String _kind;
		private final Semantics _semantics;
		private final int _depth;
		private final long _created = System.nanoTime();
		private final PlayerNativeEvidence.CombatEpisode _combatEpisode;
		private WorkState _state = WorkState.RESERVED;
		private final boolean _traceDispatch;
		private volatile long _submitNanos, _executorEnteredNanos, _startNanos, _bodyExitNanos, _endNanos, _delayMillis;
		private volatile String _bodyOutcome = "NOT_ENTERED";
		private volatile String _dispatchStatus = "RESERVED";
		private volatile java.util.concurrent.ScheduledFuture<?> _dispatchFuture;
		private WorkTicket(long id, String kind, Semantics semantics, int depth)
		{
			_id = id; _kind = PhantomCleanupIncident.bounded(kind, 80); _semantics = semantics; _depth = depth;
			_combatEpisode = PlayerNativeWork.captureCombatEpisode(PhantomNativeWorkScope.this);
			_traceDispatch = kind.startsWith("EVENT:") && org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().diagnosticsEnabled();
		}
		@Override public Owner owner() { return PhantomNativeWorkScope.this; }
		@Override public Semantics semantics() { return _semantics; }
		@Override public PlayerNativeEvidence.CombatEpisode combatEpisode() { return _combatEpisode; }
		@Override public boolean isRunning() { synchronized (_monitor) { return _state == WorkState.RUNNING; } }
		@Override public void executorEntered()
		{
			if (_traceDispatch && (_executorEnteredNanos == 0)) { _executorEnteredNanos = System.nanoTime(); }
		}
		@Override public void bodyExited(Throwable failure)
		{
			if (_traceDispatch) { _bodyOutcome = failure == null ? "SUCCESS" : failure.getClass().getName(); _bodyExitNanos = System.nanoTime(); }
		}
		@Override public void dispatchStage(String stage, long delayMillis, java.util.concurrent.ScheduledFuture<?> future)
		{
			if (!_traceDispatch) { return; }
			_delayMillis = delayMillis;
			if (future != null) { _dispatchFuture = future; }
			if (stage.equals("SUBMITTED")) { _submitNanos = System.nanoTime(); }
			if (_startNanos == 0) { _dispatchStatus = stage; }
		}
		private String dispatchSnapshot()
		{
			final var future = _dispatchFuture;
			return _id + ":" + _kind + ":" + _dispatchStatus + ":epoch=" + _epoch + ":reserve=" + _created + ":submit=" + _submitNanos + ":executorEntered=" + _executorEnteredNanos + ":start=" + _startNanos + ":bodyExit=" + _bodyExitNanos + ":bodyOutcome=" + _bodyOutcome + ":end=" + _endNanos
				+ ":delayMs=" + _delayMillis + ":future=" + (future == null ? "NONE" : "done=" + future.isDone() + ",cancelled=" + future.isCancelled() + ",dueMs=" + future.getDelay(TimeUnit.MILLISECONDS));
		}
		@Override public boolean tryStart()
		{
			synchronized (_monitor)
			{
				if (_state != WorkState.RESERVED) { return false; }
				if (!isCurrent()) { finish(WorkState.CANCELLED); return false; }
				_state = WorkState.RUNNING;
				if (_traceDispatch) { _startNanos = System.nanoTime(); _dispatchStatus = "RUNNING"; }
				publishWork();
				return true;
			}
		}
		@Override public boolean cancelBeforeStart()
		{
			final Runnable ready;
			synchronized (_monitor)
			{
				if ((_state != WorkState.RESERVED) || (_semantics == Semantics.EARNED)) { return false; }
				finish(WorkState.CANCELLED);
				ready = takeQuiescentEnqueue();
			}
			if (ready != null) { ready.run(); }
			if ((_combatEpisode != null) && _failure.isEmpty() && !Thread.holdsLock(_monitor)) { PlayerNativeWork.probeCombat(_player); }
			return true;
		}
		@Override public void complete(Throwable failure)
		{
			final Runnable ready;
			synchronized (_monitor)
			{
				if (_state != WorkState.RUNNING) { return; }
				if (failure != null) { recordFailure(failure); }
				finish(WorkState.COMPLETED);
				ready = takeQuiescentEnqueue();
			}
			if (ready != null) { ready.run(); }
			if ((_combatEpisode != null) && _failure.isEmpty() && !Thread.holdsLock(_monitor)) { PlayerNativeWork.probeCombat(_player); }
		}
		@Override public void rejected(Throwable failure)
		{
			synchronized (_monitor)
			{
				if (_state != WorkState.RESERVED) { return; }
				if (_semantics == Semantics.EARNED) { _failure = "UNSUBMITTED_EARNED_WORK:" + failure.getClass().getName(); }
				recordFailure(failure);
				finish(WorkState.CANCELLED);
			}
		}
		private void finish(WorkState state)
		{
			_state = state;
			if (_traceDispatch) { _endNanos = System.nanoTime(); _dispatchStatus = state.name(); _lastCompletedEvent = dispatchSnapshot(); _dispatchFuture = null; }
			// Scalar bookkeeping only under owner monitor; native passive getters run afterwards.
			if (_combatEpisode != null) { _evidence.completeCombat(_combatEpisode, false); }
			_outstanding.remove(_id); publishWork(); _monitor.notifyAll();
		}
	}

	private final class PendingTimer implements TimerRegistration
	{
		private final long _id;
		private final String _kind;
		private final boolean _repeating;
		private final Ticket _obligation;
		private final Runnable _stop;
		private boolean _stopped;
		private boolean _started;
		private PendingTimer(long id, String kind, boolean repeating, Ticket obligation, Runnable stop)
		{
			_id = id; _kind = PhantomCleanupIncident.bounded(kind, 80); _repeating = repeating; _obligation = obligation; _stop = stop;
		}
		@Override public Ticket start()
		{
			final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5000);
			synchronized (_monitor)
			{
				// An earned child must run to let checkpoint drain; ambient work waits for reopen.
				while (!_stopped && (_obligation == null) && !_permanentSeal && (_checkpointThread != null))
				{
					if (_repeating) { return null; }
					final long remaining = deadline - System.nanoTime();
					if (remaining <= 0) { stopLocked(); return null; }
					try { _monitor.wait(Math.max(1, remaining / 1_000_000)); }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); stopLocked(); return null; }
				}
				if (_stopped || !isCurrent() || (!_repeating && _started)) { return null; }
				if ((_obligation == null) && (_state != State.OPEN)) { stopLocked(); return null; }
				final Ticket ticket = _obligation == null ? reserve(null, _kind, Semantics.CANCELLABLE) : _obligation;
				if ((ticket == null) || !ticket.tryStart()) { return null; }
				_started = true;
				return ticket;
			}
		}
		@Override public boolean stopped() { synchronized (_monitor) { return _stopped; } }
		@Override public Cancellation cancel(boolean callback)
		{
			synchronized (_monitor)
			{
				if ((_obligation != null) && !_started && !_stopped) { return new Cancellation(false, null, false); }
				if (_stopped) { return new Cancellation(true, null, false); }
				final Ticket current = PlayerNativeWork.current(PhantomNativeWorkScope.this);
				final boolean invoke = callback && isCurrent() && ((current != null) || (_state == State.OPEN));
				final Ticket ticket = invoke && (current == null) ? reserve(null, _kind + ":cancel", Semantics.CANCELLABLE) : null;
				stopLocked();
				return new Cancellation(true, ticket, invoke);
			}
		}
		@Override public void completed(Ticket ticket, Throwable failure)
		{
			boolean stop = false;
			synchronized (_monitor)
			{
				if (failure != null) { recordFailure(failure); }
				if (!_repeating || (failure != null)) { stopLocked(); stop = true; }
			}
			try { if (stop) { _stop.run(); } }
			catch (RuntimeException | Error secondary)
			{
				recordFailure(secondary);
				if (failure == null) { throw secondary; }
				if (failure != secondary) { failure.addSuppressed(secondary); }
			}
			finally { ticket.complete(null); }
		}
		@Override public void rejected(Throwable failure)
		{
			synchronized (_monitor)
			{
				if (_obligation != null) { _obligation.rejected(failure); }
				else { recordFailure(failure); }
				stopLocked();
			}
		}
		private void stopLocked() { _stopped = true; _pendingTimers.remove(_id); publishWork(); _monitor.notifyAll(); }
	}
}
