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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.network.WritableBuffer;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.PlayerOutboundSession;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.enums.HtmlActionScope;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.ItemList;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.ServerPacket;
import org.l2jmobius.gameserver.network.serverpackets.TutorialCloseHtml;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomPlayerMaterializationSpike;
import org.l2jmobius.gameserver.phantoms.player.PhantomPlayerMaterializationSpike.FailurePoint;

public final class PhantomHeadlessPlayerSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();

	@Override
	public String id()
	{
		return "headless-player";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("fixture-create-load-canonical", _ -> testCanonicalCreateLoad());
		registry.add("default-and-owned-outbound-session", _ -> testOutboundOwnership());
		registry.add("headless-basic-effect-contract", _ -> testBasicEffectContract());
		registry.add("actual-html-and-tutorial-effects", _ -> testHtmlAndTutorialEffects());
		registry.add("actual-item-list-recursion", _ -> testItemListRecursion());
		registry.add("bounded-recursion-and-recording", _ -> testBoundedRecursionAndRecording());
		registry.add("identity-token-and-concurrency", _ -> testIdentityTokenAndConcurrency());
		registry.add("identity-materialization-collisions", _ -> testIdentityMaterializationCollisions());
		registry.add("materialize-action-cleanup-reload", _ -> testLifecycleActionAndReload());
		registry.add("observer-visibility-and-creature-say-snoop", _ -> testObserverVisibilityAndSnoop());
		registry.add("action-admission-closes-before-cleanup", _ -> testActionCleanupRace());
		registry.add("failure-matrix-all-eleven-points", context -> testFailureMatrix(context));
		registry.add("final-world-autosave-lease-residue", _ ->
		{
			_environment.assertClean(_environment.primary(), null);
			_environment.assertClean(_environment.observer(), null);
			PhantomAssertions.assertEquals(0, PhantomIdentityLeaseRegistry.getInstance().getActiveLeaseCount(), "Identity registry retained a lease.");
		});
	}

	private void testCanonicalCreateLoad() throws Exception
	{
		final Player player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(player != null, "Canonical Player.load returned null.");
		try
		{
			PhantomAssertions.assertEquals(Player.class, player.getClass(), "Fixture is not the exact canonical Player class.");
			PhantomAssertions.assertEquals(null, player.getClient(), "Canonical fixture unexpectedly has a GameClient.");
			PhantomAssertions.assertEquals(_environment.primary().fixtureItemBaseline(), player.getInventory().getInventoryItemCount(PhantomActionFacade.FIXTURE_ITEM_ID, -1), "Persisted inventory was not restored.");
			PhantomAssertions.assertTrue(player.getKnownSkill(_environment.primary().skillId()) != null, "Persisted skill was not restored.");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(player);
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testOutboundOwnership() throws Exception
	{
		final Player player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(player != null, "Could not load outbound fixture.");
		final AtomicInteger effects = new AtomicInteger();
		final CounterPacket counterPacket = new CounterPacket(effects);
		Player.OutboundSessionAttachment first = null;
		Player.OutboundSessionAttachment second = null;
		try
		{
			player.sendPacket(counterPacket);
			PhantomAssertions.assertEquals(0, effects.get(), "Default null-client path must remain a no-op.");
			PhantomAssertions.assertThrows(NullPointerException.class, () -> player.sendPacket((ServerPacket) null), "Player.sendPacket(null) must fail explicitly.");

			final HeadlessPlayerOutboundSession firstSession = new HeadlessPlayerOutboundSession(8, 32);
			first = player.attachOutboundSession(firstSession);
			player.sendPacket(counterPacket);
			PhantomAssertions.assertEquals(1, effects.get(), "Headless effect did not execute exactly once.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)), "Attachment must not replace another owner.");

			first.close();
			final Player.OutboundSessionAttachment stale = first;
			second = player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32));
			stale.close();
			PhantomAssertions.assertTrue(player.hasHeadlessOutboundSession(), "Stale attachment detached a newer session.");
			second.close();
			PhantomAssertions.assertFalse(player.hasHeadlessOutboundSession(), "Detach did not restore client-bound session.");
		}
		finally
		{
			if (second != null)
			{
				second.close();
			}
			if (first != null)
			{
				first.close();
			}
			_environment.cleanupLoadedPlayer(player);
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testBasicEffectContract() throws Exception
	{
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(8, 32, 8);
		final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), output, PhantomPlayerMaterializationSpike.FailureInjector.none());
		spike.materialize();
		final Player player = spike.getPlayer();
		final AtomicInteger effects = new AtomicInteger();
		try
		{
			player.sendPacket(new NoEffectPacket());
			player.sendPacket(new CounterPacket(effects));
			PhantomAssertions.assertEquals(1, effects.get(), "Counter packet effect was not exactly once.");
			PhantomAssertions.assertThrows(EffectFailure.class, () -> player.sendPacket(new ThrowingPacket()), "Packet effect exception must propagate.");
			PhantomAssertions.assertTrue(output.snapshot().recordedPacketClasses().contains("NoEffectPacket"), "No-effect packet was not safely dispatched.");
			PhantomAssertions.assertEquals(0L, output.snapshot().rejectedCount(), "Basic effect path unexpectedly rejected a packet.");
		}
		finally
		{
			spike.cleanup();
			spike.cleanup();
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testHtmlAndTutorialEffects() throws Exception
	{
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(8, 32);
		final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), output, PhantomPlayerMaterializationSpike.FailureInjector.none());
		spike.materialize();
		final Player player = spike.getPlayer();
		try
		{
			PhantomAssertions.assertEquals(-1, player.validateHtmlAction("t004_html"), "HTML action unexpectedly existed before packet effect.");
			player.sendPacket(new NpcHtmlMessage("<html><body><button action=\"bypass -h t004_html\"></body></html>"));
			PhantomAssertions.assertEquals(0, player.validateHtmlAction("t004_html"), "Actual HTML packet did not build the action cache.");

			player.addHtmlAction(HtmlActionScope.TUTORIAL_HTML, "t004_tutorial");
			PhantomAssertions.assertEquals(0, player.validateHtmlAction("t004_tutorial"), "Tutorial action setup failed.");
			player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
			PhantomAssertions.assertEquals(-1, player.validateHtmlAction("t004_tutorial"), "Actual TutorialCloseHtml did not clear its action scope.");
		}
		finally
		{
			spike.cleanup();
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testItemListRecursion() throws Exception
	{
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(8, 32, 16);
		final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), output, PhantomPlayerMaterializationSpike.FailureInjector.none());
		spike.materialize();
		final Player player = spike.getPlayer();
		try
		{
			final long before = output.snapshot().effectCount();
			player.sendPacket(new ItemList(player, false));
			final HeadlessPlayerOutboundSession.Snapshot snapshot = output.snapshot();
			PhantomAssertions.assertEquals(before + 2, snapshot.effectCount(), "ItemList -> ExQuestItemList must dispatch exactly two effects.");
			PhantomAssertions.assertTrue(snapshot.recordedPacketClasses().contains("ItemList"), "Actual ItemList was not recorded.");
			PhantomAssertions.assertTrue(snapshot.recordedPacketClasses().contains("ExQuestItemList"), "Actual nested ExQuestItemList was not recorded.");
			PhantomAssertions.assertTrue(snapshot.maximumObservedDepth() >= 2, "Actual ItemList recursion was not observed.");
		}
		finally
		{
			spike.cleanup();
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testBoundedRecursionAndRecording() throws Exception
	{
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(4, 8, 3);
		final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), output, PhantomPlayerMaterializationSpike.FailureInjector.none());
		spike.materialize();
		final Player player = spike.getPlayer();
		try
		{
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> player.sendPacket(new RecursivePacket()), "Recursive packet cycle must hit the bounded guard.");
			for (int i = 0; i < 6; i++)
			{
				player.sendPacket(new NoEffectPacket());
			}
			final HeadlessPlayerOutboundSession.Snapshot snapshot = output.snapshot();
			PhantomAssertions.assertEquals(3, snapshot.recordedPacketClasses().size(), "Packet recording exceeded fixed capacity.");
			PhantomAssertions.assertTrue(snapshot.droppedRecordCount() > 0, "Bounded recorder did not count overwritten entries.");
			PhantomAssertions.assertTrue(snapshot.rejectedCount() >= 1, "Recursive guard rejection was not counted.");
			PhantomAssertions.assertEquals(4, snapshot.maximumObservedDepth(), "Recursive guard observed an unexpected depth.");
		}
		finally
		{
			spike.cleanup();
		}
		_environment.assertClean(_environment.primary(), player);
	}

	private void testIdentityTokenAndConcurrency() throws Exception
	{
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		final int staleObjectId = Integer.MAX_VALUE - 100;
		final Lease first = registry.tryAcquire(staleObjectId, OwnerKind.PHANTOM);
		PhantomAssertions.assertTrue(first != null, "Could not acquire first identity lease.");
		first.close();
		final Lease second = registry.tryAcquire(staleObjectId, OwnerKind.REAL_LOGIN);
		PhantomAssertions.assertTrue(second != null, "Could not acquire replacement identity lease.");
		first.close();
		PhantomAssertions.assertEquals(OwnerKind.REAL_LOGIN, registry.getOwnerKind(staleObjectId), "Stale close released a newer identity owner.");
		second.close();

		final int concurrentObjectId = Integer.MAX_VALUE - 101;
		final CountDownLatch start = new CountDownLatch(1);
		final AtomicInteger acquired = new AtomicInteger();
		final Set<Lease> leases = ConcurrentHashMap.newKeySet();
		final Runnable contender = () ->
		{
			try
			{
				start.await();
				final Lease lease = registry.tryAcquire(concurrentObjectId, OwnerKind.PHANTOM);
				if (lease != null)
				{
					leases.add(lease);
					acquired.incrementAndGet();
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		};
		final Thread left = new Thread(contender, "t004-lease-left");
		final Thread right = new Thread(contender, "t004-lease-right");
		left.start();
		right.start();
		start.countDown();
		left.join(2000);
		right.join(2000);
		PhantomAssertions.assertFalse(left.isAlive() || right.isAlive(), "Concurrent lease test threads did not terminate.");
		PhantomAssertions.assertEquals(1, acquired.get(), "Concurrent identity claim allowed more than one owner.");
		leases.forEach(Lease::close);
		PhantomAssertions.assertEquals(null, registry.getOwnerKind(concurrentObjectId), "Concurrent identity lease was not released.");
	}

	private void testIdentityMaterializationCollisions() throws Exception
	{
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		final PhantomHeadlessPlayerFixture fixture = _environment.primary();

		final Lease realLease = registry.tryAcquire(fixture.objectId(), OwnerKind.REAL_LOGIN);
		PhantomAssertions.assertTrue(realLease != null, "Could not create real-login reservation.");
		try
		{
			final PhantomPlayerMaterializationSpike blocked = spike(fixture, new HeadlessPlayerOutboundSession(8, 32), PhantomPlayerMaterializationSpike.FailureInjector.none());
			PhantomAssertions.assertThrows(IllegalStateException.class, blocked::materialize, "Real-login reservation must block Phantom load.");
			blocked.cleanup();
			PhantomAssertions.assertEquals(OwnerKind.REAL_LOGIN, registry.getOwnerKind(fixture.objectId()), "Blocked Phantom released the real-login owner.");
		}
		finally
		{
			realLease.close();
		}

		final PhantomPlayerMaterializationSpike owner = spike(fixture, new HeadlessPlayerOutboundSession(8, 32), PhantomPlayerMaterializationSpike.FailureInjector.none());
		owner.materialize();
		final Player ownerPlayer = owner.getPlayer();
		try
		{
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, registry.getOwnerKind(fixture.objectId()), "Materialized Phantom does not own its identity.");
			PhantomAssertions.assertEquals(null, registry.tryAcquire(fixture.objectId(), OwnerKind.REAL_LOGIN), "Phantom owner did not block real-login reservation.");

			final PhantomPlayerMaterializationSpike duplicate = spike(fixture, new HeadlessPlayerOutboundSession(8, 32), PhantomPlayerMaterializationSpike.FailureInjector.none());
			PhantomAssertions.assertThrows(IllegalStateException.class, duplicate::materialize, "Phantom owner did not block another Phantom.");
			duplicate.cleanup();
			PhantomAssertions.assertEquals(ownerPlayer, World.getInstance().getPlayer(fixture.objectId()), "Collision path disturbed the existing World Player.");
		}
		finally
		{
			owner.cleanup();
		}
		_environment.assertClean(fixture, ownerPlayer);

		final int missingObjectId = Integer.MAX_VALUE - 102;
		final PhantomPlayerMaterializationSpike missing = new PhantomPlayerMaterializationSpike(missingObjectId, registry, new HeadlessPlayerOutboundSession(8, 32), new PhantomActionFacade(), PhantomPlayerMaterializationSpike.FailureInjector.none());
		PhantomAssertions.assertThrows(IllegalStateException.class, missing::materialize, "Missing Player load should fail.");
		missing.cleanup();
		PhantomAssertions.assertEquals(null, registry.getOwnerKind(missingObjectId), "Failed Player load retained identity ownership.");
	}

	private void testLifecycleActionAndReload() throws Exception
	{
		final PhantomHeadlessPlayerFixture fixture = _environment.primary();
		final PhantomPlayerMaterializationSpike first = spike(fixture, new HeadlessPlayerOutboundSession(16, 128, 16), PhantomPlayerMaterializationSpike.FailureInjector.none());
		first.materialize();
		final Player firstPlayer = first.getPlayer();
		try
		{
			PhantomAssertions.assertEquals(2, firstPlayer.isOnlineInt(), "Active headless Player must map to detached/offline value 2.");
			PhantomAssertions.assertEquals(null, firstPlayer.getClient(), "Materialized Player unexpectedly has a client.");
			PhantomAssertions.assertEquals(firstPlayer, World.getInstance().getPlayer(fixture.objectId()), "World does not contain the exact materialized Player.");
			PhantomAssertions.assertTrue(PhantomHeadlessPlayerTestEnvironment.isAutosaveMember(fixture.objectId()), "Player.load did not register autosave membership.");
			final PhantomActionFacade.ActionResult action = first.performReversibleInventoryAction();
			PhantomAssertions.assertEquals(fixture.fixtureItemBaseline(), action.before(), "Action baseline differs from persisted fixture.");
			PhantomAssertions.assertEquals(action.before() + 1, action.afterAdd(), "Action add delta is wrong.");
			PhantomAssertions.assertEquals(action.before(), action.after(), "Action did not restore its baseline.");
		}
		finally
		{
			first.cleanup();
			first.cleanup();
		}
		_environment.assertClean(fixture, firstPlayer);

		final PhantomPlayerMaterializationSpike reload = spike(fixture, new HeadlessPlayerOutboundSession(8, 32), PhantomPlayerMaterializationSpike.FailureInjector.none());
		reload.materialize();
		final Player reloadedPlayer = reload.getPlayer();
		try
		{
			PhantomAssertions.assertEquals(fixture.fixtureItemBaseline(), reloadedPlayer.getInventory().getInventoryItemCount(PhantomActionFacade.FIXTURE_ITEM_ID, -1), "Reload did not restore conserved inventory.");
			PhantomAssertions.assertTrue(reloadedPlayer.getKnownSkill(fixture.skillId()) != null, "Reload did not restore persisted skill.");
		}
		finally
		{
			reload.cleanup();
		}
		_environment.assertClean(fixture, reloadedPlayer);
	}

	private void testObserverVisibilityAndSnoop() throws Exception
	{
		final HeadlessPlayerOutboundSession primaryOutput = new HeadlessPlayerOutboundSession(16, 128, 64);
		final HeadlessPlayerOutboundSession observerOutput = new HeadlessPlayerOutboundSession(16, 128, 64);
		final PhantomPlayerMaterializationSpike primary = spike(_environment.primary(), primaryOutput, PhantomPlayerMaterializationSpike.FailureInjector.none());
		final PhantomPlayerMaterializationSpike observer = spike(_environment.observer(), observerOutput, PhantomPlayerMaterializationSpike.FailureInjector.none());
		primary.materialize();
		final Player primaryPlayer = primary.getPlayer();
		observer.materialize();
		final Player observerPlayer = observer.getPlayer();
		try
		{
			PhantomAssertions.assertTrue(observerOutput.snapshot().recordedPacketClasses().contains("CharInfo"), "Observer did not receive primary Player visibility.");
			PhantomAssertions.assertTrue(primaryOutput.snapshot().recordedPacketClasses().contains("CharInfo"), "Primary did not receive observer Player visibility.");

			primaryPlayer.addSnooper(observerPlayer);
			observerPlayer.addSnooped(primaryPlayer);
			primaryPlayer.sendPacket(new CreatureSay(primaryPlayer, ChatType.GENERAL, primaryPlayer.getName(), "t004"));
			final List<String> observerPackets = observerOutput.snapshot().recordedPacketClasses();
			PhantomAssertions.assertTrue(observerPackets.contains("CreatureSay"), "Actual CreatureSay snoop effect did not forward the source packet.");
			PhantomAssertions.assertTrue(observerPackets.contains("Snoop"), "Actual CreatureSay snoop effect did not send Snoop.");
			primaryPlayer.removeSnooper(observerPlayer);
			observerPlayer.removeSnooped(primaryPlayer);
		}
		finally
		{
			observer.cleanup();
			primary.cleanup();
		}
		_environment.assertClean(_environment.observer(), observerPlayer);
		_environment.assertClean(_environment.primary(), primaryPlayer);
	}

	private void testActionCleanupRace() throws Exception
	{
		final CountDownLatch mutationReached = new CountDownLatch(1);
		final CountDownLatch releaseMutation = new CountDownLatch(1);
		final PhantomPlayerMaterializationSpike.FailureInjector blocker = point ->
		{
			if (point == FailurePoint.AFTER_ACTION_MUTATION)
			{
				mutationReached.countDown();
				try
				{
					if (!releaseMutation.await(3, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to release the action mutation");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
		};
		final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), new HeadlessPlayerOutboundSession(8, 32), blocker);
		spike.materialize();
		final Player player = spike.getPlayer();
		final AtomicReference<Throwable> actionFailure = new AtomicReference<>();
		final AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
		final Thread actionThread = new Thread(() ->
		{
			try
			{
				spike.performReversibleInventoryAction();
			}
			catch (Throwable throwable)
			{
				actionFailure.set(throwable);
			}
		}, "t004-action");
		final Thread cleanupThread = new Thread(() ->
		{
			try
			{
				spike.cleanup();
			}
			catch (Throwable throwable)
			{
				cleanupFailure.set(throwable);
			}
		}, "t004-cleanup");

		actionThread.start();
		PhantomAssertions.assertTrue(mutationReached.await(3, TimeUnit.SECONDS), "Action did not reach the admitted mutation.");
		cleanupThread.start();
		final long deadline = System.nanoTime() + 2_000_000_000L;
		while (spike.snapshot().actionAdmissionOpen() && (System.nanoTime() < deadline))
		{
			Thread.onSpinWait();
		}
		PhantomAssertions.assertFalse(spike.snapshot().actionAdmissionOpen(), "Cleanup did not close action admission first.");
		PhantomAssertions.assertThrows(IllegalStateException.class, spike::performReversibleInventoryAction, "A new action was admitted during cleanup.");
		releaseMutation.countDown();
		actionThread.join(4000);
		cleanupThread.join(4000);
		PhantomAssertions.assertFalse(actionThread.isAlive() || cleanupThread.isAlive(), "Action/cleanup race threads did not terminate.");
		PhantomAssertions.assertEquals(null, actionFailure.get(), "Admitted action failed during bounded cleanup race.");
		PhantomAssertions.assertEquals(null, cleanupFailure.get(), "Cleanup failed during bounded action drain.");
		_environment.assertClean(_environment.primary(), player);
	}

	private void testFailureMatrix(PhantomTestContext context) throws Exception
	{
		int verified = 0;
		for (FailurePoint failurePoint : FailurePoint.values())
		{
			final AtomicInteger injected = new AtomicInteger();
			final PhantomPlayerMaterializationSpike spike = spike(_environment.primary(), new HeadlessPlayerOutboundSession(8, 64, 8), point ->
			{
				if ((point == failurePoint) && (injected.getAndIncrement() == 0))
				{
					throw new InjectedFailure(failurePoint);
				}
			});
			Player retainedPlayer = null;
			try
			{
				if ((failurePoint == FailurePoint.AFTER_STORE_BEFORE_DELETE) || (failurePoint == FailurePoint.AFTER_DELETE_BEFORE_IDENTITY_RELEASE))
				{
					spike.materialize();
					retainedPlayer = spike.getPlayer();
					PhantomAssertions.assertThrows(InjectedFailure.class, spike::cleanup, "Cleanup failure point did not propagate.");
				}
				else if (failurePoint == FailurePoint.AFTER_ACTION_MUTATION)
				{
					spike.materialize();
					retainedPlayer = spike.getPlayer();
					PhantomAssertions.assertThrows(InjectedFailure.class, spike::performReversibleInventoryAction, "Action mutation failure did not propagate.");
				}
				else
				{
					PhantomAssertions.assertThrows(InjectedFailure.class, spike::materialize, "Materialization failure point did not propagate.");
				}
			}
			finally
			{
				spike.cleanup();
				spike.cleanup();
			}

			PhantomAssertions.assertEquals(1, injected.get(), "Failure point was not injected exactly once: " + failurePoint);
			_environment.assertClean(_environment.primary(), retainedPlayer);
			verified++;
		}
		context.record("headless.failurePointsVerified", verified);
		PhantomAssertions.assertEquals(FailurePoint.values().length, verified, "Failure matrix did not cover every point.");
	}

	private static PhantomPlayerMaterializationSpike spike(PhantomHeadlessPlayerFixture fixture, HeadlessPlayerOutboundSession output, PhantomPlayerMaterializationSpike.FailureInjector injector)
	{
		return new PhantomPlayerMaterializationSpike(fixture.objectId(), PhantomIdentityLeaseRegistry.getInstance(), output, new PhantomActionFacade(), injector);
	}

	private static final class NoEffectPacket extends ServerPacket
	{
		@Override
		protected void writeImpl(GameClient client, WritableBuffer buffer)
		{
		}
	}

	private static final class CounterPacket extends ServerPacket
	{
		private final AtomicInteger _counter;

		private CounterPacket(AtomicInteger counter)
		{
			_counter = counter;
		}

		@Override
		public void runImpl(Player player)
		{
			_counter.incrementAndGet();
		}

		@Override
		protected void writeImpl(GameClient client, WritableBuffer buffer)
		{
		}
	}

	private static final class ThrowingPacket extends ServerPacket
	{
		@Override
		public void runImpl(Player player)
		{
			throw new EffectFailure();
		}

		@Override
		protected void writeImpl(GameClient client, WritableBuffer buffer)
		{
		}
	}

	private static final class RecursivePacket extends ServerPacket
	{
		@Override
		public void runImpl(Player player)
		{
			player.sendPacket(this);
		}

		@Override
		protected void writeImpl(GameClient client, WritableBuffer buffer)
		{
		}
	}

	private static final class EffectFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}

	private static final class InjectedFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		private InjectedFailure(FailurePoint point)
		{
			super(point.name());
		}
	}
}
