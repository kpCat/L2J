/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Diagnostic boundary only: distinguish executor entry from a blocked exact-ticket start. */
public final class PhantomLifecycleDispatch027Checks
{
	private PhantomLifecycleDispatch027Checks() { }
	public static void enteredBeforeOwnerMonitor(PhantomTestContext context, Player player) throws Exception
	{
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		final var monitorField = PhantomNativeWorkScope.class.getDeclaredField("_monitor"); monitorField.setAccessible(true);
		final var body = new CountDownLatch(1);
		synchronized (monitorField.get(scope))
		{
			PlayerNativeWork.execute(player, List.of(), "EVENT:TASK027_OBSERVATION", PlayerNativeWork.Semantics.EARNED, body::countDown);
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			String snapshot;
			do { snapshot = scope.diagnosticScalars().get("nativeEventDispatch"); if (!snapshot.contains(":executorEntered=0:")) { break; } Thread.sleep(5); } while (System.nanoTime() < deadline);
			context.record("E01.ownerHeld", snapshot);
			PhantomAssertions.assertTrue(snapshot.contains("EVENT:TASK027_OBSERVATION") && !snapshot.contains(":executorEntered=0:") && snapshot.contains(":start=0:"), "Executor entry must be observable before owner lock / tryStart.");
			PhantomAssertions.assertEquals(1L, body.getCount(), "Diagnostic entry cannot run the native body through its owner lock.");
		}
		PhantomAssertions.assertTrue(body.await(2, TimeUnit.SECONDS), "Exact original published observation body resumes after lock release.");
		scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
		PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.firstNativeIncident() == null, "Observation preserves native ownership/accounting.");
	}
}
