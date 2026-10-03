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
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Player.OutboundSessionAttachment;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.qol.PersonalProgressionQoLService;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Single canonical per-actor lifecycle used by production materialization and
 * the bounded Task 004 compatibility wrapper.
 */
public final class PhantomMaterializedPlayer implements AutoCloseable
{
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
	private boolean _identityAttached;
	private boolean _actionAdmissionOpen;
	private int _admittedActionCount;
	private long _actionGeneration;
	private long _materializedAtNanos;
	private long _dematerializedAtNanos;
	private boolean _cleanupStarted;
	private boolean _cleanupFinished;
	private volatile CleanupPhase _cleanupPhase = CleanupPhase.NONE;
	private volatile CleanupFailure _cleanupFailure = new CleanupFailure(0, CleanupPhase.NONE, 0, "", "", "");

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

	public synchronized void materialize()
	{
		if (_state != State.STORED)
		{
			throw new IllegalStateException("Materialization can only start from STORED");
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

			requireIdentityRegistriesFree("after identity claim");

			_state = State.LOADING;
			_player = Player.load(_objectId);
			if (_player == null)
			{
				throw new MaterializationException(MaterializationFailure.PLAYER_LOAD_FAILED, "Could not load canonical Player");
			}
			if (_player.getObjectId() != _objectId)
			{
				throw new MaterializationException(MaterializationFailure.OBJECT_ID_MISMATCH, "Loaded Player object ID does not match the claimed character");
			}
			requireWorldIdentityFree("during Player load");
			final PlayerAutoSaveTaskManager autoSaveManager = PlayerAutoSaveTaskManager.getInstance();
			if (!autoSaveManager.contains(_player) || autoSaveManager.containsOtherObjectId(_objectId, _player))
			{
				throw new MaterializationException(MaterializationFailure.AUTOSAVE_IDENTITY_BUSY, "Loaded Player is not the only autosave owner for the claimed character");
			}
			_lifecycleSupport.afterPlayerLoad(_player);
			PersonalProgressionQoLService.getInstance().tryGrantAutoNoblesse(_player);
			failAfter(FailurePoint.AFTER_PLAYER_LOAD);

			_identityAttached = true;
			_state = State.MATERIALIZING;
			failAfter(FailurePoint.AFTER_IDENTITY_ATTACHMENT);

			_outboundAttachment = _player.attachOutboundSession(_outboundSession);
			failAfter(FailurePoint.AFTER_HEADLESS_OUTPUT_ATTACHMENT);

			_player.setRunning();
			_player.standUp();
			_player.refreshOverloaded();
			_player.refreshExpertisePenalty();
			failAfter(FailurePoint.AFTER_DOMAIN_INITIALIZATION);

			_player.setOnlineStatus(true, true);
			failAfter(FailurePoint.AFTER_ONLINE_ACTIVATION);

			requireWorldIdentityFree("immediately before World spawn");
			_player.spawnMe();
			final World world = World.getInstance();
			if ((world.getPlayer(_objectId) != _player) || (world.findObject(_objectId) != _player))
			{
				throw new MaterializationException(MaterializationFailure.WORLD_REGISTRATION_MISMATCH, "World did not register the exact materialized Player in both identity maps");
			}
			failAfter(FailurePoint.AFTER_WORLD_SPAWN);

			synchronized (_actionMonitor)
			{
				_actionGeneration++;
				_actionAdmissionOpen = true;
			}
			_state = State.ACTIVE;
			_materializedAtNanos = System.nanoTime();
			failAfter(FailurePoint.AFTER_ACTION_ADMISSION);
		}
		catch (RuntimeException | Error e)
		{
			_state = State.FAILED;
			try
			{
				cleanup();
			}
			catch (RuntimeException cleanupFailure)
			{
				e.addSuppressed(cleanupFailure);
			}
			throw e;
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
			_admittedActionCount++;
			return new ActionLease(this, _player, _actionGeneration);
		}
	}

	private void releaseAction(long generation)
	{
		synchronized (_actionMonitor)
		{
			if ((generation == _actionGeneration) && (_admittedActionCount > 0))
			{
				_admittedActionCount--;
				_actionMonitor.notifyAll();
			}
		}
	}

	@Override
	public synchronized void close()
	{
		cleanup();
	}

	public synchronized void cleanup()
	{
		cleanup(System.nanoTime() + (_actionDrainTimeoutMillis * 1_000_000L));
	}

	synchronized void cleanup(long deadlineNanos)
	{
		if (_cleanupFinished || _cleanupStarted)
		{
			return;
		}
		_cleanupStarted = true;
		_state = State.DEMATERIALIZING;

		RuntimeException afterStepFailure = null;
		final long failureSequenceBeforeCleanup = _cleanupFailure.sequence();
		try
		{
			closeActionAdmissionAndDrain(Math.min(deadlineNanos, System.nanoTime() + (_actionDrainTimeoutMillis * 1_000_000L)));

			final Player cleanupPlayer = _player;
			if ((cleanupPlayer != null) && !PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer))
			{
				_cleanupPhase = CleanupPhase.PRE_STORE;
				requireNoForeignWorldIdentity(cleanupPlayer);
				cleanupPlayer.stopAllTasks();
				try
				{
					_lifecycleSupport.beforeStore(cleanupPlayer);
					failAfter(FailurePoint.BEFORE_STORE_OPERATION);
					_cleanupPhase = CleanupPhase.NATIVE_STORE;
					cleanupPlayer.storeMe();
					failAfter(FailurePoint.AFTER_NATIVE_STORE);
					_cleanupPhase = CleanupPhase.POST_STORE;
					_lifecycleSupport.afterStore(cleanupPlayer);
				}
				finally
				{
					cleanupPlayer.stopAllTasks();
				}

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
				_outboundAttachment.close();
				_outboundAttachment = null;
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
			_cleanupStarted = false;
		}

		if (afterStepFailure != null)
		{
			throw afterStepFailure;
		}
	}

	private void recordCleanupFailure(Throwable failure)
	{
		synchronized (_actionMonitor)
		{
			final Throwable cause = failure.getCause();
			_cleanupFailure = new CleanupFailure(_cleanupFailure.sequence() + 1, _cleanupPhase, _admittedActionCount,
				failure.getClass().getName(), boundedCleanupMessage(failure.getMessage()),
				cause == null ? "" : boundedCleanupMessage(cause.getClass().getName() + ":" + cause.getMessage()));
		}
	}

	private static String boundedCleanupMessage(String value)
	{
		if (value == null) { return ""; }
		final StringBuilder result = new StringBuilder(Math.min(value.length(), 160));
		for (int index = 0; (index < value.length()) && (result.length() < 160);)
		{
			final int character = value.codePointAt(index);
			final boolean validXml = ((character >= 0x20) && (character <= 0xD7FF)) || ((character >= 0xE000) && (character <= 0xFFFD)) || ((character >= 0x10000) && (character <= 0x10FFFF));
			final int sanitized = validXml && !Character.isISOControl(character) ? character : ' ';
			if (result.length() + Character.charCount(sanitized) > 160) { break; }
			result.appendCodePoint(sanitized);
			index += Character.charCount(character);
		}
		return result.toString();
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

	private void closeActionAdmissionAndDrain(long deadlineNanos)
	{
		synchronized (_actionMonitor)
		{
			_cleanupPhase = CleanupPhase.ACTION_DRAIN;
			_actionAdmissionOpen = false;
			while (_admittedActionCount > 0)
			{
				final long remainingNanos = deadlineNanos - System.nanoTime();
				if (remainingNanos <= 0)
				{
					final var failure = new IllegalStateException("Timed out waiting for admitted Phantom actions");
					recordCleanupFailure(failure);
					throw failure;
				}

				try
				{
					final long waitMillis = Math.max(1, remainingNanos / 1_000_000L);
					_actionMonitor.wait(waitMillis);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					final var failure = new IllegalStateException("Interrupted while draining Phantom actions", e);
					recordCleanupFailure(failure);
					throw failure;
				}
			}
		}
	}

	private void failAfter(FailurePoint point)
	{
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
			return new Snapshot(_objectId, _state, snapshotPlayer != null, _identityLease != null, _identityAttached, _outboundAttachment != null, _actionAdmissionOpen, _admittedActionCount, (snapshotPlayer != null) && (World.getInstance().getPlayer(_objectId) == snapshotPlayer), _materializedAtNanos, _dematerializedAtNanos,
				_cleanupPhase, failure.phase(), failure.className(), failure.message(), failure.sequence(), failure.admittedActions(), failure.cause());
		}
	}

	public Player getPlayer()
	{
		return _player;
	}

	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos,
		CleanupPhase cleanupPhase, CleanupPhase cleanupFailurePhase, String cleanupFailureClass, String cleanupFailureMessage, long cleanupFailureSequence, int cleanupFailureAdmittedActionCount, String cleanupFailureCause)
	{
	}

	public static final class ActionLease implements AutoCloseable
	{
		private final PhantomMaterializedPlayer _owner;
		private final Player _player;
		private final long _generation;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private ActionLease(PhantomMaterializedPlayer owner, Player player, long generation)
		{
			_owner = owner;
			_player = player;
			_generation = generation;
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
				_owner.releaseAction(_generation);
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
