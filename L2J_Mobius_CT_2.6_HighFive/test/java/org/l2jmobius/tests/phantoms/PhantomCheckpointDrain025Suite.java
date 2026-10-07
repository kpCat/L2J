/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Stock delayed EventDispatcher traversal completes before the exact arrival store. No rewards are injected. */
public final class PhantomCheckpointDrain025Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _native = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("checkpoint-drain025", new PhantomCheckpointDrain025Suite(), new PhantomTestContext(25002502, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "checkpoint-drain025"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _native.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _native.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("R21-stock-earned-callback-before-store", this::delayed);
		registry.add("R22-two-exact-control-waits-release-workers", this::pair);
	}
	private void pair(PhantomTestContext context) throws Exception
	{
		try (var first = _native.new Fixture(true); var second = _native.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
		{
			final var fixtures = java.util.List.of(first, second);
			final var finished = new CountDownLatch(2);
			for (var f : fixtures)
			{
				f.removeCatchup();
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Distinct native lifetime.");
				final var player = World.getInstance().getPlayer(f.objectId);
				final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
				player.addListener(new ConsumerEventListener(player, EventType.ON_ATTACKABLE_KILL, (OnAttackableKill event) ->
				{
					PhantomAssertions.assertTrue(event.getAttacker() == player && PlayerNativeWork.current(scope) != null, "Exact participant under pending control.");
					finished.countDown();
				}, this));
				EventDispatcher.getInstance().notifyEventAsyncDelayed(new OnAttackableKill(player, null, false), player, 800);
			}
			final long started = System.nanoTime();
			for (var f : fixtures)
			{
				final var goal = f.goals.load(f.id).orElseThrow().goal();
				PhantomAssertions.assertFalse(f.background.captureVisibleArrival(f.id, World.getInstance().getPlayer(f.objectId), goal, goal.selectedAnchor().key()), "Each earned control defers.");
			}
			PhantomAssertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 350, "Two controls release their workers before callbacks are due.");
			PhantomAssertions.assertTrue(finished.await(3, TimeUnit.SECONDS), "Both stock callback traversals execute normally.");
			for (var f : fixtures)
			{
				final var player = World.getInstance().getPlayer(f.objectId);
				final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
				final var goal = f.goals.load(f.id).orElseThrow().goal();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
				while (!scope.open() && System.nanoTime() < deadline) { f.background.continueVisibleCheckpoint(f.id, goal); Thread.sleep(10); }
				PhantomAssertions.assertTrue(scope.open() && !player.hasPendingOwnedStore(), "Both exact continuations finalized.");
			}
			context.record("checkpoint025.pair", "controls=2;stockCallbacks=2;nativeWorkersBlocked=false");
		}
	}
	private void delayed(PhantomTestContext context) throws Exception
	{
		try (var f = _native.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Exact materialized native lifetime.");
			final var player = World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var finished = new CountDownLatch(1);
			final var calls = new AtomicInteger();
			final var listener = new ConsumerEventListener(player, EventType.ON_ATTACKABLE_KILL, (OnAttackableKill event) ->
			{
				PhantomAssertions.assertTrue(event.getAttacker() == player && PlayerNativeWork.current(scope) != null, "Stock event retains exact earned owner.");
				calls.incrementAndGet(); finished.countDown();
			}, this);
			player.addListener(listener);
			try
			{
				EventDispatcher.getInstance().notifyEventAsyncDelayed(new OnAttackableKill(player, null, false), player, 500);
				PhantomAssertions.assertTrue(scope.outstanding() > 0, "Stock delayed event reserved before publication.");
				final long started = System.nanoTime();
				PhantomAssertions.assertFalse(f.background.captureVisibleArrival(f.id, player, goal, goal.selectedAnchor().key()), "Pending earned work must defer the checkpoint.");
				PhantomAssertions.assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 250, "Control released its worker before earned due.");
				PhantomAssertions.assertFalse(scope.open() || player.hasPendingOwnedStore(), "Wait closes roots without premature store.");
				PhantomAssertions.assertTrue(finished.await(3, TimeUnit.SECONDS), "Stock earned callback executes without control blocking its executor.");
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
				while (!scope.open() && System.nanoTime() < deadline) { f.background.continueVisibleCheckpoint(f.id, goal); Thread.sleep(10); }
				PhantomAssertions.assertTrue(scope.open() && scope.isCurrent(), "Exact quiescent continuation resumes original owner.");
				PhantomAssertions.assertEquals(1, calls.get(), "Earned callback runs once.");
				PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Receipt finalized before native roots reopen.");
				PhantomAssertions.assertEquals(f.baseline.progress(), f.transactions.load(f.id).state().progress(), "Control supplies no EXP/SP.");
				context.record("checkpoint025.earned", "stock=ON_ATTACKABLE_KILL;delayMs=500;calls=1;ownerOpen=true;pending=0");
			}
			finally { player.removeListener(listener); }
		}
	}
}
