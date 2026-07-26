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

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.LifecycleSupport;

/**
 * Task 004 compatibility wrapper. Lifecycle ownership is delegated to the
 * production {@link PhantomMaterializedPlayer}; only fixture behavior remains
 * here.
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

	private final PhantomActionFacade _actionFacade;
	private final FailureInjector _failureInjector;
	private final PhantomMaterializedPlayer _materializedPlayer;
	private volatile long _fixtureItemBaseline;

	public PhantomPlayerMaterializationSpike(int objectId, PhantomIdentityLeaseRegistry identityRegistry, HeadlessPlayerOutboundSession outboundSession, PhantomActionFacade actionFacade, FailureInjector failureInjector)
	{
		_actionFacade = Objects.requireNonNull(actionFacade, "actionFacade");
		_failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
		_materializedPlayer = new PhantomMaterializedPlayer(
			objectId,
			Objects.requireNonNull(identityRegistry, "identityRegistry"),
			Objects.requireNonNull(outboundSession, "outboundSession"),
			point -> _failureInjector.after(FailurePoint.valueOf(point.name())),
			new LifecycleSupport()
			{
				@Override
				public void afterPlayerLoad(Player player)
				{
					_fixtureItemBaseline = _actionFacade.getFixtureCount(player);
				}

				@Override
				public void beforeStore(Player player)
				{
					_actionFacade.restoreFixtureBaseline(player, _fixtureItemBaseline);
				}
			},
			PhantomMaterializedPlayer.DEFAULT_ACTION_DRAIN_TIMEOUT_MILLIS);
	}

	public void materialize()
	{
		_materializedPlayer.materialize();
	}

	public PhantomActionFacade.ActionResult performReversibleInventoryAction()
	{
		final ActionLease actionLease = _materializedPlayer.tryAcquireAction();
		if (actionLease == null)
		{
			throw new IllegalStateException("Phantom action admission is closed");
		}

		RuntimeException failure = null;
		try (actionLease)
		{
			return _actionFacade.performReversibleInventoryFixture(actionLease.player(), () -> _failureInjector.after(FailurePoint.AFTER_ACTION_MUTATION));
		}
		catch (RuntimeException e)
		{
			failure = e;
			throw e;
		}
		finally
		{
			if (failure != null)
			{
				try
				{
					_materializedPlayer.cleanup();
				}
				catch (RuntimeException cleanupFailure)
				{
					failure.addSuppressed(cleanupFailure);
				}
			}
		}
	}

	@Override
	public void close()
	{
		cleanup();
	}

	public void cleanup()
	{
		_materializedPlayer.cleanup();
	}

	public Snapshot snapshot()
	{
		final PhantomMaterializedPlayer.Snapshot snapshot = _materializedPlayer.snapshot();
		return new Snapshot(
			snapshot.objectId(),
			State.valueOf(snapshot.state().name()),
			snapshot.playerRetained(),
			snapshot.identityLeaseRetained(),
			snapshot.identityAttached(),
			snapshot.outboundAttached(),
			snapshot.actionAdmissionOpen(),
			snapshot.admittedActionCount(),
			snapshot.worldPresent(),
			snapshot.materializedAtNanos(),
			snapshot.dematerializedAtNanos());
	}

	public Player getPlayer()
	{
		return _materializedPlayer.getPlayer();
	}

	public record Snapshot(int objectId, State state, boolean playerRetained, boolean identityLeaseRetained, boolean identityAttached, boolean outboundAttached, boolean actionAdmissionOpen, int admittedActionCount, boolean worldPresent, long materializedAtNanos, long dematerializedAtNanos)
	{
	}
}
