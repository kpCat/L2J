/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.timers.TimerExecutor;
import org.l2jmobius.gameserver.model.script.timers.TimerHolder;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.DematerializeResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Q06 actual TimerHolder callback remains a writer after stock post/cancel removes it. */
public final class PhantomM1TimerChecks
{
	private static final int ITEM_ID = 1334;
	private static final AtomicReference<Consumer<Player>> QUEST_BODY = new AtomicReference<>();

	private PhantomM1TimerChecks()
	{
	}

	/** Caller owns the RuntimeFixture and a BEFORE_STORE_OPERATION latch/await failure hook. */
	public static void run(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Q06 requires the initialized allowlisted headless TEST environment.");
		PhantomAssertions.assertTrue(player.isNativeWorkManaged(), "Q06 requires the exact managed materialized Player.");
		PhantomAssertions.assertEquals(1.0f, RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, "Q06 requires the existing stock TEST quest item amount multiplier.");
		PhantomAssertions.assertEquals(1L, beforeStore.getCount(), "Q06 BEFORE_STORE barrier must start closed.");
		PhantomAssertions.assertEquals(1L, releaseBeforeStore.getCount(), "Q06 BEFORE_STORE release barrier must start closed.");
		final String event = "Q06-owned-timer-" + profileId;
		final long beforeItems = player.getInventory().getInventoryItemCount(ITEM_ID, -1);
		final CountDownLatch inside = new CountDownLatch(1);
		final CountDownLatch releaseBody = new CountDownLatch(1);
		final CountDownLatch finished = new CountDownLatch(1);
		final AtomicInteger eventCalls = new AtomicInteger();
		final AtomicInteger cancelCalls = new AtomicInteger();
		final AtomicLong grantedCount = new AtomicLong(-1);
		final AtomicReference<TimerHolder<String>> executing = new AtomicReference<>();
		final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
		final AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
		final AtomicReference<DematerializeResult> cleanupResult = new AtomicReference<>();
		final TimerExecutor<String> timers = new TimerExecutor<>(holder ->
		{
			eventCalls.incrementAndGet();
			executing.set(holder);
			inside.countDown();
			try
			{
				PhantomAssertions.assertTrue(holder.getPlayer() == player, "Q06 native timer callback changed its exact Player.");
				await(releaseBody, "Q06 native timer callback barrier timed out.");
				Quest.giveItems(player, ITEM_ID, 1);
				grantedCount.set(player.getInventory().getInventoryItemCount(ITEM_ID, -1));
			}
			catch (RuntimeException | Error failure) { callbackFailure.set(failure); throw failure; }
			finally { finished.countDown(); }
		}, holder -> cancelCalls.incrementAndGet());
		final Thread cleanup = new Thread(() ->
		{
			try { cleanupResult.set(materialization.dematerialize(profileId)); }
			catch (Throwable failure) { cleanupFailure.set(failure); }
		}, "m1-native-timer-cleanup");
		Throwable primary = null;
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "Q06 ActionLease did not admit the exact managed Player.");
				PhantomAssertions.assertTrue(timers.addTimer(event, null, 250, null, player), "Q06 actual native TimerExecutor did not publish its timer.");
			}
			PhantomAssertions.assertTrue(inside.await(5, TimeUnit.SECONDS), "Q06 actual TimerHolder did not enter its native event callback.");
			PhantomAssertions.assertFalse(timers.hasTimer(event, null, player), "Q06 stock post executor did not remove the one-shot before event body.");
			PhantomAssertions.assertEquals(1, cancelCalls.get(), "Q06 stock post executor did not invoke native cancel before event body.");
			PhantomAssertions.assertEquals(-1L, executing.get().getRemainingTime(), "Q06 native future was not terminal while its callback still runs.");
			cleanup.start();
			final boolean crossed = beforeStore.await(2, TimeUnit.SECONDS);
			context.record("Q06.timer.beforeStoreWhileBodyRunning", crossed);
			context.record("Q06.timer.pendingRegistration", timers.hasTimer(event, null, player));
			context.record("Q06.timer.remainingWhileBodyRunning", executing.get().getRemainingTime());
			context.record("Q06.timer.ownerWhileBodyRunning", materialization.find(profileId).map(Object::toString).orElse("removed"));
			releaseBody.countDown();
			PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "Q06 native timer callback did not finish after release.");
			releaseBeforeStore.countDown();
			cleanup.join(10000);
			context.record("Q06.timer.cleanup", cleanupResult.get() == null ? String.valueOf(cleanupFailure.get()) : cleanupResult.get().status());
			context.record("Q06.timer.rewardCountBeforeDelete", grantedCount.get());
			context.record("Q06.timer.eventCalls", eventCalls.get());
			context.record("Q06.timer.cancelCalls", cancelCalls.get());
			PhantomAssertions.assertFalse(crossed, "Owned BEFORE_STORE crossed a running native TimerHolder event after post/cancel removed its registration.");
			PhantomAssertions.assertEquals(null, callbackFailure.get(), "Q06 native timer reward callback failed.");
			PhantomAssertions.assertTrue(!cleanup.isAlive() && (cleanupFailure.get() == null) && (cleanupResult.get() != null), "Q06 native timer cleanup did not terminate safely: " + cleanupFailure.get());
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanupResult.get().status(), "Q06 native timer cleanup retained a failure.");
			PhantomAssertions.assertEquals(1, eventCalls.get(), "Q06 one-shot event callback did not execute exactly once.");
			PhantomAssertions.assertEquals(beforeItems + 1, grantedCount.get(), "Q06 native quest writer did not grant exactly one item before delete.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			releaseBody.countDown();
			releaseBeforeStore.countDown();
			try
			{
				timers.cancelAllTimers();
				if (inside.getCount() == 0) { PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "Q06 fixture retained a running timer callback after release."); }
				if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				PhantomAssertions.assertFalse(cleanup.isAlive(), "Q06 fixture retained its cleanup thread.");
			}
			catch (Exception | Error failure)
			{
				if (primary == null) { throw failure; }
				primary.addSuppressed(failure);
			}
		}
	}

	/** Called only by the Java8 TEST script loaded through the actual ScriptEngine. */
	public static void onQuestEvent(Player player)
	{
		final Consumer<Player> body = QUEST_BODY.get();
		if (body == null) { throw new IllegalStateException("Q04_TEST_QUEST_BODY_MISSING"); }
		body.accept(player);
	}

	public static void runQuest(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		runHeld(context, player, materialization, profileId, beforeStore, releaseBeforeStore, "Q04");
	}
	public static void runCancel(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		runHeld(context, player, materialization, profileId, beforeStore, releaseBeforeStore, "Q05");
	}
	public static void runRepeating(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		runHeld(context, player, materialization, profileId, beforeStore, releaseBeforeStore, "Q06-repeat");
	}

	private static void runHeld(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore, String scenario) throws Exception
	{
		guard(context, player);
		final String event = scenario + "-timer-" + profileId;
		final String pending = event + "-long";
		final long baseline = player.getInventory().getInventoryItemCount(ITEM_ID, -1);
		final var inside = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var cancels = new AtomicInteger();
		final var longCalls = new AtomicInteger();
		final var granted = new AtomicLong(-1);
		final var failure = new AtomicReference<Throwable>();
		final var result = new AtomicReference<DematerializeResult>();
		final Consumer<Player> body = exact ->
		{
			try
			{
				PhantomAssertions.assertTrue(exact == player, scenario + " callback changed Player lifetime.");
				final var ticket = PlayerNativeWork.current(player.getNativeWorkOwner());
				PhantomAssertions.assertTrue((ticket != null) && ticket.isRunning(), scenario + " actual callback lacks owned running scope.");
				calls.incrementAndGet(); inside.countDown();
				await(release, scenario + " writer release timeout.");
				Quest.giveItems(player, ITEM_ID, 1);
				granted.set(player.getInventory().getInventoryItemCount(ITEM_ID, -1));
			}
			catch (RuntimeException | Error thrown) { failure.set(thrown); throw thrown; }
			finally { finished.countDown(); }
		};
		final var timers = new TimerExecutor<String>(holder ->
		{
			if (holder.getEvent().equals(pending)) { longCalls.incrementAndGet(); return; }
			if (scenario.equals("Q05")) { throw new AssertionError("Q05 cancelled pending event executed."); }
			body.accept(holder.getPlayer());
		}, holder -> { cancels.incrementAndGet(); if (scenario.equals("Q05")) { body.accept(holder.getPlayer()); } });
		final Thread cancel = new Thread(() -> timers.cancelTimers(event), "m1-native-cancel");
		final Thread cleanup = new Thread(() -> result.set(materialization.dematerialize(profileId)), "m1-native-" + scenario + "-cleanup");
		Quest quest = null;
		Throwable primary = null;
		try
		{
			if (scenario.equals("Q04"))
			{
				PhantomAssertions.assertEquals(null, ScriptManager.getInstance().getScript("M1TimerBootstrap"), "Q04 test script already loaded.");
				ScriptEngine.getInstance().executeScript(context.moduleRoot().resolve("test/resources/phantoms/M1TimerBootstrap.java"));
				quest = ScriptManager.getInstance().getScript("M1TimerBootstrap");
				PhantomAssertions.assertTrue(quest != null, "Q04 actual ScriptEngine did not create its Quest.");
				PhantomAssertions.assertTrue(QUEST_BODY.compareAndSet(null, body), "Q04 static TEST body already assigned.");
			}
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, scenario + " fixture did not admit exact Player.");
				if (quest != null) { quest.startQuestTimer(event, 250, null, player); }
				else if (scenario.equals("Q06-repeat"))
				{
					final var owner = (PhantomNativeWorkScope) player.getNativeWorkOwner();
					final int outstanding = owner.outstanding();
					PhantomAssertions.assertTrue(timers.addTimer(pending, 60000, null, player), "Long ambient timer rejected.");
					PhantomAssertions.assertEquals(outstanding, owner.outstanding(), "Long ambient delay held a lifetime ticket.");
					PhantomAssertions.assertTrue(timers.addRepeatingTimer(event, 250, null, player), "Repeating timer rejected.");
				}
				else { PhantomAssertions.assertTrue(timers.addTimer(event, 60000, null, player), "Q05 native timer registration rejected."); }
			}
			if (scenario.equals("Q05")) { cancel.start(); }
			PhantomAssertions.assertTrue(inside.await(5, TimeUnit.SECONDS), scenario + " actual callback did not enter.");
			if (quest != null) { PhantomAssertions.assertEquals(null, quest.getQuestTimer(event, null, player), "Q04 one-shot still registered inside body."); }
			cleanup.start();
			final boolean crossed = beforeStore.await(2, TimeUnit.SECONDS);
			context.record(scenario + ".beforeStoreWhileWriter", crossed);
			release.countDown();
			PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), scenario + " writer did not finish.");
			releaseBeforeStore.countDown(); cleanup.join(10000);
			if (cancel.getState() != Thread.State.NEW) { cancel.join(5000); }
			PhantomAssertions.assertFalse(crossed, scenario + " BEFORE_STORE crossed the actual active native callback.");
			PhantomAssertions.assertTrue(!cleanup.isAlive() && (result.get() != null), scenario + " cleanup did not complete.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), scenario + " cleanup retained failure.");
			PhantomAssertions.assertEquals(null, failure.get(), scenario + " native callback failed.");
			PhantomAssertions.assertEquals(1, calls.get(), scenario + " native callback repeated.");
			PhantomAssertions.assertEquals(baseline + 1, granted.get(), scenario + " native grant count mismatch.");
			PhantomAssertions.assertEquals(scenario.equals("Q05") ? 1 : 0, cancels.get(), scenario + " cleanup invoked an unauthorized cancel callback.");
			PhantomAssertions.assertEquals(0, longCalls.get(), "Long pending ambient callback ran during cleanup.");
			PhantomAssertions.assertFalse(timers.hasTimer(pending, null, player), "Cleanup retained the long ambient registration.");
			context.record(scenario + ".nativeGrantBeforeDelete", granted.get());
		}
		catch (Exception | Error thrown) { primary = thrown; throw thrown; }
		finally
		{
			release.countDown(); releaseBeforeStore.countDown();
			try
			{
				if (inside.getCount() == 0) { PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), scenario + " fixture writer remained active."); }
				if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				if (cancel.getState() != Thread.State.NEW) { cancel.join(5000); }
				timers.cancelAllTimers();
				if (quest != null) { quest.cancelQuestTimer(event, null, player); quest.unload(); }
				PhantomAssertions.assertTrue(!cleanup.isAlive() && !cancel.isAlive(), scenario + " fixture retained a helper thread.");
			}
			catch (Exception | Error thrown) { if (primary == null) { throw thrown; } primary.addSuppressed(thrown); }
			finally { QUEST_BODY.compareAndSet(body, null); }
		}
	}

	/** EARNED is seeded through the existing owned executor; every timer/child is native. */
	public static void runEarnedChain(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		guard(context, player);
		final String parentEvent = "Q13-parent-" + profileId;
		final String childEvent = "Q13-child-" + profileId;
		final String leafEvent = "Q13-leaf-" + profileId;
		final long baseline = player.getInventory().getInventoryItemCount(ITEM_ID, -1);
		final var parentInside = new CountDownLatch(1);
		final var releaseParent = new CountDownLatch(1);
		final var leafInside = new CountDownLatch(1);
		final var releaseLeaf = new CountDownLatch(1);
		final var leafFinished = new CountDownLatch(1);
		final var events = new AtomicInteger();
		final var cancels = new AtomicInteger();
		final var granted = new AtomicLong(-1);
		final var failure = new AtomicReference<Throwable>();
		final var result = new AtomicReference<DematerializeResult>();
		final var reference = new AtomicReference<TimerExecutor<String>>();
		final var timers = new TimerExecutor<String>(holder ->
		{
			try
			{
				events.incrementAndGet();
				final var ticket = PlayerNativeWork.current(player.getNativeWorkOwner());
				PhantomAssertions.assertTrue((holder.getPlayer() == player) && (ticket != null) && (ticket.semantics() == PlayerNativeWork.Semantics.EARNED), "Q13 timer lost exact earned lineage.");
				if (holder.getEvent().equals(parentEvent))
				{
					parentInside.countDown(); await(releaseParent, "Q13 parent release timeout.");
					PhantomAssertions.assertTrue(reference.get().addTimer(childEvent, 150, null, player), "Q13 earned child during drain was rejected.");
					reference.get().cancelTimers(childEvent);
					PhantomAssertions.assertTrue(reference.get().hasTimer(childEvent, null, player), "Q13 pending earned cancellation removed child registration.");
				}
				else if (holder.getEvent().equals(childEvent))
				{
					PhantomAssertions.assertTrue(reference.get().addTimer(leafEvent, 0, null, player), "Q13 zero-delay grandchild was rejected.");
				}
				else
				{
					PhantomAssertions.assertEquals(leafEvent, holder.getEvent(), "Q13 unexpected event.");
					leafInside.countDown(); await(releaseLeaf, "Q13 leaf release timeout.");
					Quest.giveItems(player, ITEM_ID, 1);
					granted.set(player.getInventory().getInventoryItemCount(ITEM_ID, -1));
					leafFinished.countDown();
				}
			}
			catch (RuntimeException | Error thrown) { failure.set(thrown); throw thrown; }
		}, holder -> cancels.incrementAndGet());
		reference.set(timers);
		final Thread cleanup = new Thread(() -> result.set(materialization.dematerialize(profileId)), "m1-native-earned-chain-cleanup");
		Throwable primary = null;
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "Q13 fixture did not admit exact Player.");
				PlayerNativeWork.execute(player, "TEST_Q13_EARNED_SEED", PlayerNativeWork.Semantics.EARNED,
					() -> PhantomAssertions.assertTrue(timers.addTimer(parentEvent, 0, null, player), "Q13 native zero timer rejected."));
			}
			PhantomAssertions.assertTrue(parentInside.await(5, TimeUnit.SECONDS), "Q13 actual parent timer did not execute.");
			cleanup.start();
			final boolean parentCrossed = beforeStore.await(2, TimeUnit.SECONDS);
			releaseParent.countDown();
			PhantomAssertions.assertTrue(leafInside.await(5, TimeUnit.SECONDS), "Q13 actual grandchild did not execute: " + failure.get());
			final boolean leafCrossed = beforeStore.await(100, TimeUnit.MILLISECONDS);
			releaseLeaf.countDown();
			PhantomAssertions.assertTrue(leafFinished.await(5, TimeUnit.SECONDS), "Q13 leaf native writer did not finish.");
			releaseBeforeStore.countDown(); cleanup.join(10000);
			context.record("Q13.timer.beforeStoreWhileParent", parentCrossed);
			context.record("Q13.timer.beforeStoreWhileGrandchild", leafCrossed);
			context.record("Q13.timer.nativeGrantBeforeDelete", granted.get());
			PhantomAssertions.assertFalse(parentCrossed || leafCrossed, "Q13 cleanup crossed an earned native timer generation.");
			PhantomAssertions.assertEquals(null, failure.get(), "Q13 native chain callback failed.");
			PhantomAssertions.assertTrue(!cleanup.isAlive() && (result.get() != null), "Q13 cleanup did not finish.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), "Q13 earned timer cleanup retained failure.");
			PhantomAssertions.assertEquals(3, events.get(), "Q13 native timer chain did not run each generation once.");
			PhantomAssertions.assertEquals(3, cancels.get(), "Q13 automatic native post/cancel sequence changed.");
			PhantomAssertions.assertEquals(baseline + 1, granted.get(), "Q13 native grandchild writer grant mismatch.");
		}
		catch (Exception | Error thrown) { primary = thrown; throw thrown; }
		finally
		{
			releaseParent.countDown(); releaseLeaf.countDown(); releaseBeforeStore.countDown();
			try
			{
				if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				timers.cancelAllTimers();
				PhantomAssertions.assertFalse(cleanup.isAlive(), "Q13 fixture retained cleanup thread.");
			}
			catch (Exception | Error thrown) { if (primary == null) { throw thrown; } primary.addSuppressed(thrown); }
		}
	}

	private static void guard(PhantomTestContext context, Player player)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native timer checks require guarded TEST bootstrap.");
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && (player.getNativeWorkOwner() != null), "Native timer checks require exact owned Player.");
		PhantomAssertions.assertEquals(1.0f, RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, "Native timer checks require stock amount rate.");
	}

	private static void await(CountDownLatch latch, String message)
	{
		try { PhantomAssertions.assertTrue(latch.await(10, TimeUnit.SECONDS), message); }
		catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
	}
}
