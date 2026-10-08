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

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.Player.OutboundSessionAttachment;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.qol.PersonalProgressionQoLService;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Single canonical per-actor lifecycle used by production materialization and
 * the bounded Task 004 compatibility wrapper.
 */
public final class PhantomMaterializedPlayer implements AutoCloseable
{
	private static final Logger LOGGER = Logger.getLogger(PhantomMaterializedPlayer.class.getName());
	public enum State
	{
		STORED,
		CLAIMED,
		LOADING,
		MATERIALIZING,
		ACTIVE,
		DEMATERIALIZING,
		FAILED
	}

	public enum FailurePoint
	{
		AFTER_IDENTITY_CLAIM,
		AFTER_PLAYER_LOAD,
		AFTER_IDENTITY_ATTACHMENT,
		AFTER_HEADLESS_OUTPUT_ATTACHMENT,
		AFTER_DOMAIN_INITIALIZATION,
		AFTER_ONLINE_ACTIVATION,
		AFTER_WORLD_SPAWN,
		AFTER_ACTION_ADMISSION,
		BEFORE_STORE_OPERATION,
		AFTER_NATIVE_STORE,
		AFTER_STORE_BEFORE_DELETE,
		BEFORE_DELETE_OPERATION,
		AFTER_DELETE_BEFORE_IDENTITY_RELEASE
	}

	public enum CleanupPhase
	{
		NONE, ACTION_DRAIN, PRE_STORE, NATIVE_STORE, POST_STORE, PRE_DELETE, DELETE, POST_DELETE,
		RELEASE_OUTBOUND, RELEASE_IDENTITY, POSTCONDITION, COMPLETE
	}

	public enum MaterializationFailure
	{
		WORLD_PLAYER_IDENTITY_BUSY,
		WORLD_OBJECT_IDENTITY_BUSY,
		AUTOSAVE_IDENTITY_BUSY,
		WORLD_REGISTRATION_MISMATCH,
		IDENTITY_BUSY,
		PLAYER_LOAD_FAILED,
		OBJECT_ID_MISMATCH
	}

	@FunctionalInterface
	public interface FailureInjector
	{
		void after(FailurePoint point);

		static FailureInjector none()
		{
			return point ->
			{
			};
		}
	}

	interface LifecycleSupport
	{
		void afterPlayerLoad(Player player);

		void beforeStore(Player player);

		default void afterStore(Player player)
		{
		}

		static LifecycleSupport none()
		{
			return new LifecycleSupport()
			{
				@Override
				public void afterPlayerLoad(Player player)
				{
				}

				@Override
				public void beforeStore(Player player)
				{
				}

				@Override
				public void afterStore(Player player)
				{
				}
			};
		}
	}

	public static final long DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS = 5000;

	private final Object _actionMonitor = new Object();
	private final CountDownLatch _materializationCompletion = new CountDownLatch(1);
	private Thread _materializationThread;
	private final int _objectId;
	private final PhantomIdentityLeaseRegistry _identityRegistry;
	private final HeadlessPlayerOutboundSession _outboundSession;
	private final FailureInjector _failureInjector;
	private final LifecycleSupport _lifecycleSupport;
	private final long _actionDrainTimeoutMillis;
	private volatile State _state = State.STORED;
	private volatile Player _player;
	private Lease _identityLease;
	private OutboundSessionAttachment _outboundAttachment;
	private java.util.function.Supplier<org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease> _nativeActionAdmission;
	private boolean _identityAttached;
	private boolean _actionAdmissionOpen;
	private PhantomNativeWorkScope _nativeWork;
	private long _actionGeneration;
	private long _materializedAtNanos;
	private long _dematerializedAtNanos;
	private boolean _cleanupStarted;
	private boolean _cleanupFinished;
	private volatile CleanupPhase _cleanupPhase = CleanupPhase.NONE;
	private volatile CleanupFailure _cleanupFailure = new CleanupFailure(0, CleanupPhase.NONE, 0, "", "", "");
	private volatile PhantomCleanupIncident _firstCleanupIncident;
	private volatile PhantomCleanupIncident _latestCleanupIncident;
	private long _materializationAttemptAtNanos;
	private String _cleanupHook = "NONE";

	private record CleanupFailure(long sequence, CleanupPhase phase, int admittedActions, String className, String message, String cause)
	{
	}

	public PhantomMaterializedPlayer(int objectId, PhantomIdentityLeaseRegistry identityRegistry, HeadlessPlayerOutboundSession outboundSession)
	{
		this(objectId, identityRegistry, outboundSession, FailureInjector.none(), LifecycleSupport.none(), DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS);
	}

	PhantomMaterializedPlayer(int objectId, PhantomIdentityLeaseRegistry identityRegistry, HeadlessPlayerOutboundSession outboundSession, FailureInjector failureInjector, LifecycleSupport lifecycleSupport, long actionDrainTimeoutMillis)
	{
		if (objectId <= 0)
		{
			throw new IllegalArgumentException("objectId must be positive");
		}
		if (actionDrainTimeoutMillis <= 0)
		{
			throw new IllegalArgumentException("actionDrainTimeoutMillis must be positive");
		}
		_objectId = objectId;
		_identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
		_outboundSession = Objects.requireNonNull(outboundSession, "outboundSession");
		_failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
		_lifecycleSupport = Objects.requireNonNull(lifecycleSupport, "lifecycleSupport");
		_actionDrainTimeoutMillis = actionDrainTimeoutMillis;
	}

	public void materialize()
	{
		synchronized (this)
		{
			if ((_state != State.STORED) || _cleanupStarted || _cleanupFinished || (_materializationThread != null))
			{
				throw new IllegalStateException("Materialization can only start from an unused STORED actor");
			}
			_materializationThread = Thread.currentThread();
			_materializationAttemptAtNanos = System.nanoTime();
			_state = State.CLAIMED;
		}

		try
		{
			requireIdentityRegistriesFree("before identity claim");

			_identityLease = _identityRegistry.tryAcquire(_objectId, OwnerKind.PHANTOM);
			if (_identityLease == null)
			{
				throw new MaterializationException(MaterializationFailure.IDENTITY_BUSY, "Character identity is already owned");
			}
			_state = State.CLAIMED;
			failAfter(FailurePoint.AFTER_IDENTITY_CLAIM);
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_IDENTITY_CLAIMED", _state.name(), "PHANTOM", _objectId);

			requireIdentityRegistriesFree("after identity claim");

			_state = State.LOADING;
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_PLAYER_LOAD_BEGIN", _state.name(), "", _objectId);
			_player = Player.load(_objectId, loaded ->
			{
				// Retain this exact instance even if constructor/restore aborts later.
				_player = loaded;
				_materializedAtNanos = System.nanoTime();
				_nativeWork = new PhantomNativeWorkScope(_actionMonitor, loaded, _identityLease, _materializedAtNanos);
				loaded.attachNativeWorkOwner(_nativeWork);
			});
			if (_player == null)
			{
				throw new MaterializationException(MaterializationFailure.PLAYER_LOAD_FAILED, "Could not load canonical Player");
			}
			if (_player.getObjectId() != _objectId)
			{
				throw new MaterializationException(MaterializationFailure.OBJECT_ID_MISMATCH, "Loaded Player object ID does not match the claimed character");
			}
			requireWorldIdentityFree("during Player load");
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_PLAYER_LOAD_OK", _state.name(), "", _objectId);
			final PlayerAutoSaveTaskManager autoSaveManager = PlayerAutoSaveTaskManager.getInstance();
			if (!autoSaveManager.contains(_player) || autoSaveManager.containsOtherObjectId(_objectId, _player))
			{
				throw new MaterializationException(MaterializationFailure.AUTOSAVE_IDENTITY_BUSY, "Loaded Player is not the only autosave owner for the claimed character");
			}
			PlayerNativeWork.run(_player, "PLAYER_RESTORE_EFFECTS", _player::restoreEffects);
			_lifecycleSupport.afterPlayerLoad(_player);
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_AFTER_PLAYER_LOAD_OK", _state.name(), "", _objectId);
			PlayerNativeWork.run(_player, "PLAYER_LOGIN_DOMAIN", () ->
			{
				org.l2jmobius.gameserver.model.script.Quest.playerEnter(_player);
				PersonalProgressionQoLService.getInstance().tryGrantAutoNoblesse(_player);
			});
			failAfter(FailurePoint.AFTER_PLAYER_LOAD);

			_identityAttached = true;
			_state = State.MATERIALIZING;
			failAfter(FailurePoint.AFTER_IDENTITY_ATTACHMENT);

			_outboundAttachment = _player.attachOutboundSession(_outboundSession);
			final Player ownedPlayer = _player;
			_nativeActionAdmission = () ->
			{
				final ActionLease lease = tryAcquireAction();
				if (lease == null) { return null; }
				if (lease.player() != ownedPlayer) { lease.close(); return null; }
				return lease::close;
			};
			_player.attachPhantomNativeActionAdmission(_nativeActionAdmission);
			failAfter(FailurePoint.AFTER_HEADLESS_OUTPUT_ATTACHMENT);

			_player.setRunning();
			_player.standUp();
			_player.refreshOverloaded();
			_player.refreshExpertisePenalty();
			failAfter(FailurePoint.AFTER_DOMAIN_INITIALIZATION);

			_player.setOnlineStatus(true, true);
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_ONLINE", _state.name(), "", _objectId);
			failAfter(FailurePoint.AFTER_ONLINE_ACTIVATION);

			requireWorldIdentityFree("immediately before World spawn");
			_player.spawnMe();
			final World world = World.getInstance();
			if ((world.getPlayer(_objectId) != _player) || (world.findObject(_objectId) != _player))
			{
				throw new MaterializationException(MaterializationFailure.WORLD_REGISTRATION_MISMATCH, "World did not register the exact materialized Player in both identity maps");
			}
			failAfter(FailurePoint.AFTER_WORLD_SPAWN);
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_WORLD_SPAWN", _state.name(), "exact.World.Player", _objectId);

			synchronized (_actionMonitor)
			{
				_actionGeneration++;
				_actionAdmissionOpen = true;
			}
			_state = State.ACTIVE;
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_ACTION_ADMISSION_OPEN", _state.name(), "", _objectId);
			failAfter(FailurePoint.AFTER_ACTION_ADMISSION);
		}
		catch (RuntimeException | Error e)
		{
			_state = State.FAILED;
			PhantomRuntimeFlightRecorder.getInstance().recordCurrent("MAT_ABORT", _state.name(), e.getClass().getName(), _objectId);
			recordCleanupFailure(e);
			try
			{
				cleanup();
			}
			catch (RuntimeException | Error cleanupFailure)
			{
				if (cleanupFailure != e) { e.addSuppressed(cleanupFailure); }
			}
			throw e;
		}
		finally
		{
			synchronized (this) { _materializationThread = null; }
			_materializationCompletion.countDown();
		}
	}

	public ActionLease tryAcquireAction()
	{
		synchronized (_actionMonitor)
		{
			if ((_state != State.ACTIVE) || !_actionAdmissionOpen || (_player == null))
			{
				return null;
			}
			final var ticket = _nativeWork.reserve(PlayerNativeWork.current(_nativeWork), "ACTION", PlayerNativeWork.Semantics.CANCELLABLE);
			if ((ticket == null) || !ticket.tryStart()) { return null; }
			return new ActionLease(this, _player, _actionGeneration, ticket);
		}
	}

	private int outstandingWork() { return _nativeWork == null ? 0 : _nativeWork.outstanding(); }

	@Override
	public void close()
	{
		cleanup();
	}

	public void cleanup()
	{
		cleanup(System.nanoTime() + (_actionDrainTimeoutMillis * 1_000_000L));
	}

	void cleanup(long deadlineNanos)
	{
		if (!claimCleanup(deadlineNanos)) { return; }

		RuntimeException afterStepFailure = null;
		final long failureSequenceBeforeCleanup = _cleanupFailure.sequence();
		try
		{
			closeActionAdmissionAndDrain(Math.min(deadlineNanos, System.nanoTime() + (_actionDrainTimeoutMillis * 1_000_000L)));

			final Player cleanupPlayer = _player;
			if ((cleanupPlayer != null) && !PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer))
			{
				_cleanupPhase = CleanupPhase.PRE_STORE;
				_cleanupHook = "NATIVE_TEARDOWN";
				requireNoForeignWorldIdentity(cleanupPlayer);
				if (!cleanupPlayer.isNativeCleanupPrepared())
				{
					_nativeWork.teardown(() ->
					{
						cleanupPlayer.abortAttack(); cleanupPlayer.abortCast(); cleanupPlayer.stopMove(null);
						if (cleanupPlayer.hasAI())
						{
							cleanupPlayer.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE);
							cleanupPlayer.getAI().clientStopAutoAttack();
							org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(cleanupPlayer);
							cleanupPlayer.getAI().setAutoAttacking(false);
						}
						cleanupPlayer.stopAllTasks();
						cleanupPlayer.prepareNativeCleanup();
					}, deadlineNanos);
				}
				// A legitimate descendant may add a fresh effect; dispose each new native body once.
				for (int pass = 0; !cleanupPlayer.getEffectList().getEffects().isEmpty() && (pass < 3); pass++) { _nativeWork.teardown(cleanupPlayer::completeNativeCleanupEffects, deadlineNanos); }
				if (!cleanupPlayer.getEffectList().getEffects().isEmpty()) { throw new IllegalStateException("NATIVE_TEARDOWN_EFFECTS_NOT_QUIESCENT"); }
				_nativeWork.terminalStore(() ->
				{
					Throwable storeFailure = null;
					try
					{
						_cleanupHook = "LIFECYCLE_BEFORE_STORE";
						_lifecycleSupport.beforeStore(cleanupPlayer);
						failAfter(FailurePoint.BEFORE_STORE_OPERATION);
						_cleanupPhase = CleanupPhase.NATIVE_STORE;
						_cleanupHook = "PLAYER_STORE_ME";
						cleanupPlayer.storeMe();
						failAfter(FailurePoint.AFTER_NATIVE_STORE);
						_cleanupPhase = CleanupPhase.POST_STORE;
						_cleanupHook = "LIFECYCLE_AFTER_STORE";
						_lifecycleSupport.afterStore(cleanupPlayer);
					}
					catch (RuntimeException | Error failure)
					{
						storeFailure = failure;
						throw failure;
					}
					finally
					{
						try { cleanupPlayer.stopAllTasks(); }
						catch (RuntimeException | Error finalizerFailure)
						{
							if (storeFailure == null) { throw finalizerFailure; }
							if (storeFailure != finalizerFailure) { storeFailure.addSuppressed(finalizerFailure); }
						}
					}
				});

				_cleanupPhase = CleanupPhase.PRE_DELETE;
				try
				{
					failAfter(FailurePoint.AFTER_STORE_BEFORE_DELETE);
				}
				catch (RuntimeException e)
				{
					recordCleanupFailure(e);
					afterStepFailure = remember(afterStepFailure, e);
				}

				failAfter(FailurePoint.BEFORE_DELETE_OPERATION);
				requireNoForeignWorldIdentity(cleanupPlayer);
				_cleanupPhase = CleanupPhase.DELETE;
				cleanupPlayer.deleteMe();
				cleanupPlayer.stopAllTasks();

				_cleanupPhase = CleanupPhase.POST_DELETE;
				try
				{
					failAfter(FailurePoint.AFTER_DELETE_BEFORE_IDENTITY_RELEASE);
				}
				catch (RuntimeException e)
				{
					recordCleanupFailure(e);
					afterStepFailure = remember(afterStepFailure, e);
				}
			}

			_cleanupPhase = CleanupPhase.POSTCONDITION;
			if ((cleanupPlayer != null) && !PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer))
			{
				throw new IllegalStateException("Canonical Player cleanup postconditions are incomplete");
			}

			if (_outboundAttachment != null)
			{
				_cleanupPhase = CleanupPhase.RELEASE_OUTBOUND;
				if ((cleanupPlayer != null) && (_nativeActionAdmission != null))
				{
					cleanupPlayer.detachPhantomNativeActionAdmission(_nativeActionAdmission);
					_nativeActionAdmission = null;
				}
				_outboundAttachment.close();
				_outboundAttachment = null;
			}
			if ((cleanupPlayer != null) && (_nativeWork != null))
			{
				_nativeWork.detach();
				cleanupPlayer.detachNativeWorkOwner(_nativeWork);
			}
			_identityAttached = false;

			if (_identityLease != null)
			{
				_cleanupPhase = CleanupPhase.RELEASE_IDENTITY;
				_identityLease.close();
				_identityLease = null;
			}

			_player = null;
			_dematerializedAtNanos = System.nanoTime();
			_state = State.STORED;
			_cleanupFinished = true;
			_cleanupPhase = CleanupPhase.COMPLETE;
		}
		catch (RuntimeException | Error e)
		{
			if ((_cleanupPhase != CleanupPhase.ACTION_DRAIN) || (_cleanupFailure.sequence() == failureSequenceBeforeCleanup)) { recordCleanupFailure(e); }
			_state = State.FAILED;
			if ((afterStepFailure != null) && (afterStepFailure != e))
			{
				e.addSuppressed(afterStepFailure);
			}
			throw e;
		}
		finally
		{
			synchronized (this) { _cleanupStarted = false; }
		}

		if (afterStepFailure != null)
		{
			throw afterStepFailure;
		}
	}

	private boolean claimCleanup(long deadlineNanos)
	{
		while (true)
		{
			synchronized (this)
			{
				if (_cleanupFinished) { return false; }
				if (_materializationThread == Thread.currentThread())
				{
					// Only the failed-load owner may abort its own partial native body.
					if (_state != State.FAILED) { throw new IllegalStateException("NATIVE_MATERIALIZATION_SELF_CLEANUP"); }
				}
				if ((_materializationThread == null) || (_materializationThread == Thread.currentThread()))
				{
					if (_cleanupStarted) { throw new IllegalStateException("NATIVE_CLEANUP_ALREADY_RUNNING"); }
					_cleanupStarted = true;
					_state = State.DEMATERIALIZING;
					return true;
				}
			}
			final long remainingNanos = deadlineNanos - System.nanoTime();
			try
			{
				if ((remainingNanos <= 0) || !_materializationCompletion.await(remainingNanos, TimeUnit.NANOSECONDS))
				{
					throw new IllegalStateException("NATIVE_MATERIALIZATION_COMPLETION_TIMEOUT");
				}
			}
			catch (InterruptedException failure)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException("NATIVE_MATERIALIZATION_COMPLETION_INTERRUPTED", failure);
			}
		}
	}

	private void recordCleanupFailure(Throwable failure)
	{
		final boolean first;
		final PhantomCleanupIncident incident;
		synchronized (_actionMonitor)
		{
			final PhantomCleanupIncident nativeFirst = _nativeWork == null ? null : _nativeWork.firstNativeIncident();
			final PhantomCleanupIncident nativeLatest = _nativeWork == null ? null : _nativeWork.latestNativeIncident();
			if ((_firstCleanupIncident == null) && (nativeFirst != null)) { _firstCleanupIncident = nativeFirst; }
			incident = PhantomCleanupIncident.capture(_objectId, _materializedAtNanos == 0 ? _materializationAttemptAtNanos : _materializedAtNanos,
				Math.max(_cleanupFailure.sequence(), nativeLatest == null ? 0 : nativeLatest.sequence()) + 1, _cleanupPhase, _cleanupHook, outstandingWork(), failure);
			_cleanupFailure = new CleanupFailure(incident.sequence(), incident.phase(), incident.admittedActions(), incident.exceptionClass(), incident.message(),
				PhantomCleanupIncident.bounded(incident.detail().lines().filter(line -> line.startsWith("cause ")).findFirst().orElse(""), 160));
			_latestCleanupIncident = incident;
			first = _firstCleanupIncident == null;
			if (first) { _firstCleanupIncident = incident; }
		}
		if (first) { LOGGER.warning("Phantom cleanup first incident " + incident); }
	}

	PhantomNativeWorkScope retryableDrainScope()
	{
		synchronized (_actionMonitor)
		{
			if ((_state != State.FAILED) || ((_cleanupPhase != CleanupPhase.ACTION_DRAIN) && (_cleanupPhase != CleanupPhase.PRE_STORE)) || (_player == null) || _player.hasPendingOwnedStore()
				|| !PhantomNativeWorkScope.DrainTimeoutException.class.getName().equals(_cleanupFailure.className())
				|| (_nativeWork == null) || !_nativeWork.retryableDrain()) { return null; }
			return _nativeWork;
		}
	}

	private void requireIdentityRegistriesFree(String phase)
	{
		requireWorldIdentityFree(phase);
		if (PlayerAutoSaveTaskManager.getInstance().containsObjectId(_objectId))
		{
			throw new MaterializationException(MaterializationFailure.AUTOSAVE_IDENTITY_BUSY, "Autosave already owns the claimed character " + phase);
		}
	}

	private void requireWorldIdentityFree(String phase)
	{
		final World world = World.getInstance();
		if (world.getPlayer(_objectId) != null)
		{
			throw new MaterializationException(MaterializationFailure.WORLD_PLAYER_IDENTITY_BUSY, "World Player identity is busy " + phase);
		}
		if (world.findObject(_objectId) != null)
		{
			throw new MaterializationException(MaterializationFailure.WORLD_OBJECT_IDENTITY_BUSY, "World object identity is busy " + phase);
		}
	}

	private static void requireNoForeignWorldIdentity(Player cleanupPlayer)
	{
		final World world = World.getInstance();
		final int objectId = cleanupPlayer.getObjectId();
		final Player worldPlayer = world.getPlayer(objectId);
		if ((worldPlayer != null) && (worldPlayer != cleanupPlayer))
		{
			throw new IllegalStateException("Another World Player owns the cleanup object ID");
		}
		final Object worldObject = world.findObject(objectId);
		if ((worldObject != null) && (worldObject != cleanupPlayer))
		{
			throw new IllegalStateException("Another World object owns the cleanup object ID");
		}
	}

	boolean prepareCleanupDrain()
	{
		synchronized (_actionMonitor) { _actionAdmissionOpen = false; }
		return (_nativeWork == null) || _nativeWork.beginTerminalDrain();
	}

	boolean terminalCleanupReady() { return (_nativeWork == null) || _nativeWork.terminalCleanupReady(); }

	private void closeActionAdmissionAndDrain(long deadlineNanos)
	{
		synchronized (_actionMonitor)
		{
			_cleanupPhase = CleanupPhase.ACTION_DRAIN;
			_cleanupHook = "NATIVE_WORK_DRAIN";
			_actionAdmissionOpen = false;
		}
		if (_nativeWork != null) { _nativeWork.drainAndSeal(deadlineNanos); }
	}

	private void failAfter(FailurePoint point)
	{
		_cleanupHook = point.name();
		_failureInjector.after(point);
	}

	private static RuntimeException remember(RuntimeException first, RuntimeException next)
	{
		if (first == null)
		{
			return next;
		}
		first.addSuppressed(next);
		return first;
	}

	public Snapshot snapshot()
	{
		synchronized (_actionMonitor)
		{
			final Player snapshotPlayer = _player;
			final CleanupFailure failure = _cleanupFailure;
			return new Snapshot(_objectId, _state, snapshotPlayer != null, _identityLease != null, _identityAttached, _outboundAttachment != null, _actionAdmissionOpen, outstandingWork(), (snapshotPlayer != null) && (World.getInstance().getPlayer(_objectId) == snapshotPlayer), _materializedAtNanos, _dematerializedAtNanos,
				_cleanupPhase, failure.phase(), failure.className(), failure.message(), failure.sequence(), failure.admittedActions(), failure.cause(), _firstCleanupIncident, _latestCleanupIncident);
		}
	}

	public Player getPlayer()
	{
		return _player;
	}

	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos,
		CleanupPhase cleanupPhase, CleanupPhase cleanupFailurePhase, String cleanupFailureClass, String cleanupFailureMessage, long cleanupFailureSequence, int cleanupFailureAdmittedActionCount, String cleanupFailureCause, PhantomCleanupIncident firstCleanupIncident, PhantomCleanupIncident latestCleanupIncident)
	{
	}

	public static final class ActionLease implements AutoCloseable
	{
		private final PhantomMaterializedPlayer _owner;
		private final Player _player;
		private final long _generation;
		private final AtomicBoolean _closed = new AtomicBoolean();
		private final PlayerNativeWork.Ticket _ticket;
		private final PlayerNativeWork.Context _context;

		private ActionLease(PhantomMaterializedPlayer owner, Player player, long generation, PlayerNativeWork.Ticket ticket)
		{
			_owner = owner;
			_player = player;
			_generation = generation;
			_ticket = ticket;
			_context = PlayerNativeWork.enter(ticket);
		}

		public Player player()
		{
			return _player;
		}

		public boolean isClosed()
		{
			return _closed.get();
		}

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				try { _context.close(); }
				finally { _ticket.complete(null); }
			}
		}
	}

	public static final class MaterializationException extends IllegalStateException
	{
		private static final long serialVersionUID = 1L;

		private final MaterializationFailure _failure;

		private MaterializationException(MaterializationFailure failure, String message)
		{
			super(message);
			_failure = failure;
		}

		public MaterializationFailure failure()
		{
			return _failure;
		}
	}
}
