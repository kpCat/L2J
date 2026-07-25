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

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Player.OutboundSessionAttachment;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;

/**
 * Bounded Task 004 materialization proof. It is deliberately not wired into
 * GameServer or the Task 003 Phantom lifecycle.
 */
public final class PhantomPlayerMaterializationSpike implements AutoCloseable
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
		AFTER_ACTION_MUTATION,
		BEFORE_STORE_OPERATION,
		AFTER_STORE_BEFORE_DELETE,
		BEFORE_DELETE_OPERATION,
		AFTER_DELETE_BEFORE_IDENTITY_RELEASE
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

	private static final long ACTION_DRAIN_TIMEOUT_MILLIS = 5000;

	private final Object _actionMonitor = new Object();
	private final int _objectId;
	private final PhantomIdentityLeaseRegistry _identityRegistry;
	private final HeadlessPlayerOutboundSession _outboundSession;
	private final PhantomActionFacade _actionFacade;
	private final FailureInjector _failureInjector;
	private volatile State _state = State.STORED;
	private volatile Player _player;
	private Lease _identityLease;
	private OutboundSessionAttachment _outboundAttachment;
	private boolean _identityAttached;
	private boolean _actionAdmissionOpen;
	private int _admittedActionCount;
	private long _fixtureItemBaseline;
	private long _materializedAtNanos;
	private long _dematerializedAtNanos;
	private boolean _cleanupStarted;
	private boolean _cleanupFinished;

	public PhantomPlayerMaterializationSpike(int objectId, PhantomIdentityLeaseRegistry identityRegistry, HeadlessPlayerOutboundSession outboundSession, PhantomActionFacade actionFacade, FailureInjector failureInjector)
	{
		if (objectId <= 0)
		{
			throw new IllegalArgumentException("objectId must be positive");
		}
		_objectId = objectId;
		_identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
		_outboundSession = Objects.requireNonNull(outboundSession, "outboundSession");
		_actionFacade = Objects.requireNonNull(actionFacade, "actionFacade");
		_failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
	}

	public void materialize()
	{
		synchronized (this)
		{
			if (_state != State.STORED)
			{
				throw new IllegalStateException("Materialization can only start from STORED");
			}

			try
			{
				if (World.getInstance().getPlayer(_objectId) != null)
				{
					throw new IllegalStateException("Character is already present in World");
				}

				_identityLease = _identityRegistry.tryAcquire(_objectId, OwnerKind.PHANTOM);
				if (_identityLease == null)
				{
					throw new IllegalStateException("Character identity is already owned");
				}
				_state = State.CLAIMED;
				failAfter(FailurePoint.AFTER_IDENTITY_CLAIM);

				if (World.getInstance().getPlayer(_objectId) != null)
				{
					throw new IllegalStateException("Character entered World after identity claim");
				}

				_state = State.LOADING;
				_player = Player.load(_objectId);
				if (_player == null)
				{
					throw new IllegalStateException("Could not load canonical Player");
				}
				_fixtureItemBaseline = _actionFacade.getFixtureCount(_player);
				failAfter(FailurePoint.AFTER_PLAYER_LOAD);

				if (World.getInstance().getPlayer(_objectId) != null)
				{
					throw new IllegalStateException("Character entered World during Player load");
				}

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

				_player.spawnMe();
				failAfter(FailurePoint.AFTER_WORLD_SPAWN);

				synchronized (_actionMonitor)
				{
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
	}

	public PhantomActionFacade.ActionResult performReversibleInventoryAction()
	{
		final Player actionPlayer = beginAction();
		RuntimeException failure = null;
		try
		{
			return _actionFacade.performReversibleInventoryFixture(actionPlayer, () -> failAfter(FailurePoint.AFTER_ACTION_MUTATION));
		}
		catch (RuntimeException e)
		{
			failure = e;
			throw e;
		}
		finally
		{
			endAction();
			if (failure != null)
			{
				_state = State.FAILED;
				try
				{
					cleanup();
				}
				catch (RuntimeException cleanupFailure)
				{
					failure.addSuppressed(cleanupFailure);
				}
			}
		}
	}

	private Player beginAction()
	{
		synchronized (_actionMonitor)
		{
			if ((_state != State.ACTIVE) || !_actionAdmissionOpen || (_player == null))
			{
				throw new IllegalStateException("Phantom action admission is closed");
			}
			_admittedActionCount++;
			return _player;
		}
	}

	private void endAction()
	{
		synchronized (_actionMonitor)
		{
			_admittedActionCount--;
			_actionMonitor.notifyAll();
		}
	}

	@Override
	public synchronized void close()
	{
		cleanup();
	}

	public synchronized void cleanup()
	{
		if (_cleanupFinished || _cleanupStarted)
		{
			return;
		}
		_cleanupStarted = true;
		_state = State.DEMATERIALIZING;

		RuntimeException afterStepFailure = null;
		try
		{
			closeActionAdmissionAndDrain();

			final Player cleanupPlayer = _player;
			if ((cleanupPlayer != null) && !PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer))
			{
				cleanupPlayer.stopAllTasks();
				_actionFacade.restoreFixtureBaseline(cleanupPlayer, _fixtureItemBaseline);
				failAfter(FailurePoint.BEFORE_STORE_OPERATION);
				cleanupPlayer.storeMe();

				try
				{
					failAfter(FailurePoint.AFTER_STORE_BEFORE_DELETE);
				}
				catch (RuntimeException e)
				{
					afterStepFailure = remember(afterStepFailure, e);
				}

				failAfter(FailurePoint.BEFORE_DELETE_OPERATION);
				cleanupPlayer.deleteMe();

				try
				{
					failAfter(FailurePoint.AFTER_DELETE_BEFORE_IDENTITY_RELEASE);
				}
				catch (RuntimeException e)
				{
					afterStepFailure = remember(afterStepFailure, e);
				}
			}

			if ((cleanupPlayer != null) && !PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer))
			{
				throw new IllegalStateException("Canonical Player cleanup postconditions are incomplete");
			}

			if (_outboundAttachment != null)
			{
				_outboundAttachment.close();
				_outboundAttachment = null;
			}
			_identityAttached = false;

			if (_identityLease != null)
			{
				_identityLease.close();
				_identityLease = null;
			}

			_player = null;
			_dematerializedAtNanos = System.nanoTime();
			_state = State.STORED;
			_cleanupFinished = true;
		}
		catch (RuntimeException | Error e)
		{
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

	private void closeActionAdmissionAndDrain()
	{
		synchronized (_actionMonitor)
		{
			_actionAdmissionOpen = false;
			final long deadline = System.nanoTime() + (ACTION_DRAIN_TIMEOUT_MILLIS * 1_000_000L);
			while (_admittedActionCount > 0)
			{
				final long remainingNanos = deadline - System.nanoTime();
				if (remainingNanos <= 0)
				{
					throw new IllegalStateException("Timed out waiting for admitted Phantom actions");
				}

				try
				{
					final long waitMillis = Math.max(1, remainingNanos / 1_000_000L);
					_actionMonitor.wait(waitMillis);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while draining Phantom actions", e);
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
			return new Snapshot(_objectId, _state, snapshotPlayer != null, _identityLease != null, _identityAttached, _outboundAttachment != null, _actionAdmissionOpen, _admittedActionCount, (snapshotPlayer != null) && (World.getInstance().getPlayer(_objectId) == snapshotPlayer), _materializedAtNanos, _dematerializedAtNanos);
		}
	}

	public Player getPlayer()
	{
		return _player;
	}

	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos)
	{
	}
}
