package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeTimer;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Actual native Player/executor and production scope; faults are confined to TEST. */
public final class PhantomM1NativeScopeChecks
{
	private PhantomM1NativeScopeChecks() { }

	public static void timerPrimary(PhantomTestContext context, int objectId)
	{
		for (boolean primaryError : List.of(false, true))
		{
			final Player player = Player.load(objectId); player.stopAllTasks(); player.setCurrentHp(Math.max(1, player.getMaxHp() - 10)); player.getStatus().stopHpMpRegeneration();
			final var identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(identity != null, "Q11 exact fixture identity unavailable.");
			final var scope = new PhantomNativeWorkScope(new Object(), player, identity, System.nanoTime()); player.attachNativeWorkOwner(scope);
			final Throwable primary = primaryError ? new AssertionError("TEST_Q11_BODY_A") : new IllegalStateException("TEST_Q11_BODY_A");
			final Throwable secondary = primaryError ? new IllegalStateException("TEST_Q11_REMOVE_B") : new AssertionError("TEST_Q11_REMOVE_B");
			final double hp = player.getCurrentHp();
			try
			{
				final var timer = new PlayerNativeTimer(player, "TEST_Q11_NATIVE", false, () -> rethrow(secondary));
				Throwable observed = null;
				try { timer.invoke(() -> { player.setCurrentHp(player.getMaxHp()); rethrow(primary); }); }
				catch (RuntimeException | Error failure) { observed = failure; }
				PhantomAssertions.assertTrue(player.getCurrentHp() != hp, "Q11 actual native status writer was not reached.");
				PhantomAssertions.assertEquals(0, scope.outstanding(), "Q11 body completion leaked accounting.");
				PhantomAssertions.assertEquals(0, scope.pendingTimers(), "Q11 removal leaked pending timer.");
				PhantomAssertions.assertTrue(observed == primary, "Q11 secondary removal masked native callback primary.");
				PhantomAssertions.assertTrue(java.util.Arrays.asList(primary.getSuppressed()).contains(secondary), "Q11 secondary was not suppressed on primary.");
				PhantomAssertions.assertEquals("TEST_Q11_BODY_A", scope.firstNativeIncident().message(), "Q11 first scalar incident changed.");
				PhantomAssertions.assertEquals("TEST_Q11_REMOVE_B", scope.latestNativeIncident().message(), "Q11 latest scalar incident lost secondary.");
				PhantomAssertions.assertFalse(scope.open(), "Q11 failed native lifetime admits new roots.");
				context.record("m1.Q11." + primaryError, "nativeWriter=true primary=" + observed.getClass().getSimpleName() + " secondary=" + secondary.getClass().getSimpleName() + " outstanding=0 pending=0");
			}
			finally
			{
				PhantomAssertions.assertEquals(0, scope.outstanding(), "Q11 failed TEST discard requires zero work.");
				player.stopAllTasks(); player.deleteMe(); player.detachNativeWorkOwner(scope); identity.close();
			}
		}
	}

	private static void rethrow(Throwable failure)
	{
		if (failure instanceof RuntimeException runtime) { throw runtime; }
		throw (Error) failure;
	}

	public static void scheduler(PhantomTestContext context, int objectId) throws Exception
	{
		final Field field = ThreadPool.class.getDeclaredField("SCHEDULED_POOL"); field.setAccessible(true);
		final ScheduledThreadPoolExecutor original = (ScheduledThreadPoolExecutor) field.get(null);
		for (String mode : List.of("NULL", "THROW", "INLINE", "SHUTDOWN", "NORMAL"))
		{
			for (String producer : List.of("single", "group", "periodic"))
			{
				final boolean group = producer.equals("group"), periodic = producer.equals("periodic");
				final Player player = Player.load(objectId);
				PhantomAssertions.assertTrue(player != null && !player.isNativeWorkManaged(), "Q07 requires ordinary native Player load.");
				player.stopAllTasks(); player.setCurrentHp(Math.max(1, player.getMaxHp() - 1)); player.getStatus().stopHpMpRegeneration();
				final double hp = player.getCurrentHp();
				final var identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
				PhantomAssertions.assertTrue(identity != null, "Q07 exact fixture identity unavailable.");
				final var scope = new PhantomNativeWorkScope(new Object(), player, identity, System.nanoTime()); player.attachNativeWorkOwner(scope);
				final var writes = new AtomicInteger(); final var completed = new CountDownLatch(1); final var pool = new FaultPool(mode);
				pool.setRejectedExecutionHandler(original.getRejectedExecutionHandler());
				if (mode.equals("SHUTDOWN")) { pool.shutdown(); }
				Throwable submission = null;
				try
				{
					field.set(null, pool);
					final Runnable writer = () ->
					{
						PhantomAssertions.assertTrue(PlayerNativeWork.current(scope) != null && scope.outstanding() > 0, "Q07 body lacks running ticket.");
						player.setCurrentHp(hp + 1); writes.incrementAndGet(); completed.countDown();
					};
					try
					{
						final ScheduledFuture<?> future = periodic ? PlayerNativeWork.scheduleAtFixedRate(player, "TEST_Q07_PERIODIC", writer, 0, TimeUnit.DAYS.toMillis(1)) : group
							? PlayerNativeWork.schedule(null, List.of(player), "TEST_Q07", Semantics.EARNED, writer, 0)
							: PlayerNativeWork.schedule(player, "TEST_Q07", Semantics.EARNED, writer, 0);
						PhantomAssertions.assertTrue(future != null, "Q07 falsely accepted null native " + producer + " submission.");
						if (periodic)
						{
							PhantomAssertions.assertTrue(completed.await(3, TimeUnit.SECONDS), "Q07 periodic native body timeout."); future.cancel(false);
							final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
							while ((scope.outstanding() > 0) && (System.nanoTime() < deadline)) { Thread.onSpinWait(); }
						}
						else { future.get(3, TimeUnit.SECONDS); }
					}
					catch (RuntimeException failure) { submission = failure; }
					final boolean accepted = mode.equals("INLINE") || mode.equals("NORMAL");
					PhantomAssertions.assertEquals(accepted ? 1 : 0, writes.get(), "Q07 incorrect native execution count.");
					PhantomAssertions.assertEquals(0, scope.outstanding(), "Q07 submission leaked accounting.");
					PhantomAssertions.assertEquals(0, scope.pendingTimers(), "Q07 submission leaked pending native timer.");
					PhantomAssertions.assertEquals(accepted, submission == null, "Q07 untruthful submission outcome.");
					if (accepted)
					{
						PhantomAssertions.assertTrue(player.getCurrentHp() == hp + 1, "Q07 status writer did not complete.");
						scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); scope.detach();
					}
					else { PhantomAssertions.assertTrue(scope.firstNativeIncident() != null, "Q07 lost failure evidence."); }
					context.record("m1.Q07." + mode + "." + producer, "writes=" + writes.get() + " outstanding=" + scope.outstanding() + " failure=" + (submission == null ? "NONE" : submission.getClass().getSimpleName()));
				}
				finally
				{
					field.set(null, original); pool.shutdownNow(); pool.awaitTermination(3, TimeUnit.SECONDS);
					PhantomAssertions.assertEquals(0, scope.outstanding(), "Q07 TEST discard requires zero work.");
					if (scope.pendingTimers() > 0) { scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); }
					// Exact failed TEST lifetime is discarded without recovery/reopen or store SUCCESS.
					player.stopAllTasks(); player.deleteMe(); player.detachNativeWorkOwner(scope); identity.close();
				}
			}
		}
		final Player ordinary = Player.load(objectId);
		try
		{
			ordinary.stopAllTasks(); final var count = new AtomicInteger(); final var future = ThreadPool.schedule(count::incrementAndGet, 0);
			PhantomAssertions.assertTrue(future != null, "Q07 ordinary scheduler rejected work."); future.get(3, TimeUnit.SECONDS);
			PhantomAssertions.assertEquals(1, count.get(), "Q07 ordinary native execution count.");
		}
		finally { ordinary.stopAllTasks(); ordinary.deleteMe(); }
	}

	public static void interleavings(PhantomTestContext context, int objectId) throws Exception
	{
		final Player player = Player.load(objectId);
		PhantomAssertions.assertTrue(player != null && !player.isNativeWorkManaged(), "Q05 requires native Player.load."); player.stopAllTasks();
		final long started = System.nanoTime();
		final var identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
		PhantomAssertions.assertTrue(identity != null, "Q05 identity unavailable.");
		final var failure = new AtomicReference<Throwable>(); int bodies = 0;
		try
		{
			for (int i = 0; i < 1000; i++)
			{
				final var scope = new PhantomNativeWorkScope(new Object(), player, identity, System.nanoTime()); player.attachNativeWorkOwner(scope);
				final var parent = scope.reserve(null, "TEST_Q05_PARENT", Semantics.EARNED);
				PhantomAssertions.assertTrue(parent.tryStart(), "Q05 parent did not start.");
				final var child = scope.reserve(parent, "TEST_Q05_CHILD", Semantics.CANCELLABLE);
				final var ready = new CountDownLatch(1); final var finished = new CountDownLatch(1); final var count = new AtomicInteger();
				ThreadPool.executeOrThrow(() ->
				{
					ready.countDown();
					try
					{
						if (child.tryStart())
						{
							try (var contextTicket = PlayerNativeWork.enter(child))
							{
								player.setCurrentHp(player.getCurrentHp()); // Native status writer; no farm progress claim.
								count.incrementAndGet();
								final var grandchild = scope.reserve(child, "TEST_Q05_EARNED", Semantics.EARNED);
								PhantomAssertions.assertFalse(grandchild.cancelBeforeStart(), "Q05 cancelled earned child.");
								PhantomAssertions.assertTrue(grandchild.tryStart(), "Q05 earned child did not start."); grandchild.complete(null); grandchild.complete(null);
							}
							finally { child.complete(null); child.complete(null); }
						}
					}
					catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
					finally { finished.countDown(); }
				});
				if ((i & 1) == 0) { PhantomAssertions.assertTrue(ready.await(3, TimeUnit.SECONDS), "Q05 callback CAS timeout."); }
				child.cancelBeforeStart(); child.cancelBeforeStart(); parent.complete(null); parent.complete(null);
				PhantomAssertions.assertTrue(finished.await(3, TimeUnit.SECONDS), "Q05 callback finish timeout.");
				PhantomAssertions.assertEquals(null, failure.get(), "Q05 native interleaving failure.");
				PhantomAssertions.assertTrue(count.get() <= 1, "Q05 duplicate callback."); bodies += count.get();
				scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3));
				PhantomAssertions.assertEquals(0, scope.outstanding(), "Q05 outstanding counter did not return to zero.");
				scope.detach(); player.detachNativeWorkOwner(scope);
				PhantomAssertions.assertEquals(null, PlayerNativeWork.current(scope), "Q05 caller retained context.");
			}
			context.record("m1.Q05.interleavings", "iterations=1000 nativeBodies=" + bodies + " elapsedNanos=" + (System.nanoTime() - started));
		}
		finally { player.stopAllTasks(); player.deleteMe(); identity.close(); }
	}

	public static void identityReuse(PhantomTestContext context, int objectId) throws Exception
	{
		final Player old = Player.load(objectId); old.stopAllTasks();
		final var registry = PhantomIdentityLeaseRegistry.getInstance();
		final var oldIdentity = registry.tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
		PhantomAssertions.assertTrue(oldIdentity != null, "Q08 old identity unavailable.");
		final var scope = new PhantomNativeWorkScope(new Object(), old, oldIdentity, System.nanoTime()); old.attachNativeWorkOwner(scope);
		final double oldHp = old.getCurrentHp(); final var writes = new AtomicInteger();
		final var future = PlayerNativeWork.schedule(old, "TEST_Q08_OLD_QUEUED", Semantics.EARNED, () -> { old.setCurrentHp(1); writes.incrementAndGet(); }, 250);
		oldIdentity.close();
		final var realIdentity = registry.tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN);
		PhantomAssertions.assertTrue(realIdentity != null, "Q08 replacement REAL identity unavailable.");
		Player real = null;
		try
		{
			real = Player.load(objectId); real.stopAllTasks();
			PhantomAssertions.assertTrue((real != old) && !real.isNativeWorkManaged(), "Q08 REAL replacement must be a different ordinary native Player.");
			final double realHp = real.getCurrentHp();
			future.get(3, TimeUnit.SECONDS);
			PhantomAssertions.assertEquals(0, writes.get(), "Q08 old queued native callback wrote after identity replacement.");
			PhantomAssertions.assertTrue(old.getCurrentHp() == oldHp && real.getCurrentHp() == realHp, "Q08 stale callback touched old/new native status.");
			PhantomAssertions.assertEquals(0, scope.outstanding(), "Q08 stale native callback retained accounting.");
			final Player replacement = real;
			PlayerNativeWork.run(replacement, "TEST_Q08_REAL_POSITIVE", () -> { replacement.setCurrentHp(Math.max(1, realHp - 1)); writes.incrementAndGet(); });
			replacement.storeMe();
			PhantomAssertions.assertEquals(1, writes.get(), "Q08 ordinary REAL native writer was blocked.");
			context.record("m1.Q08.native", "sameObjectId=" + objectId + " distinctPlayer=true oldWrites=0 realWrites=1 oldOutstanding=0 realOwner=" + registry.getOwnerKind(objectId));
		}
		finally
		{
			if (!future.isDone()) { future.get(3, TimeUnit.SECONDS); }
			old.stopAllTasks(); old.deleteMe(); old.detachNativeWorkOwner(scope);
			if (real != null) { real.stopAllTasks(); real.deleteMe(); }
			realIdentity.close();
		}
	}

	private static final class FaultPool extends ScheduledThreadPoolExecutor
	{
		private final String _mode;
		FaultPool(String mode) { super(1, task -> { final var thread = new Thread(task, "TEST-Q07-native-pool"); thread.setDaemon(true); return thread; }); _mode = mode; }
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit)
		{
			if (_mode.equals("NULL")) { return null; }
			if (_mode.equals("THROW")) { throw new IllegalStateException("TEST_Q07_SUBMIT_PRIMARY"); }
			if (_mode.equals("INLINE")) { task.run(); return super.schedule(() -> {}, 0, TimeUnit.MILLISECONDS); }
			return super.schedule(task, delay, unit);
		}
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit)
		{
			if (_mode.equals("NULL")) { return null; }
			if (_mode.equals("THROW")) { throw new IllegalStateException("TEST_Q07_SUBMIT_PRIMARY"); }
			if (_mode.equals("INLINE")) { task.run(); return super.scheduleAtFixedRate(() -> {}, TimeUnit.DAYS.toMillis(1), period, TimeUnit.MILLISECONDS); }
			return super.scheduleAtFixedRate(task, delay, period, unit);
		}
	}
}
