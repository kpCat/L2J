/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureAttackAvoid;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Actual native publishers are observed while every scheduled worker is held before task entry. */
public final class PhantomM1QueuedWorkChecks
{
	private PhantomM1QueuedWorkChecks()
	{
	}

	/** Caller supplies a fresh factual native target, already spawned beside the exact managed Player. */
	public static void runAttack(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, Creature target) throws Exception
	{
		final var scope = scope(context, player, materialization, profileId);
		final var observed = probe(context, "Q03.queuedAttack", scope, player, target, null, () ->
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "Q03 attack ActionLease changed the exact Player.");
				player.setTarget(target);
				player.doAttack(target);
			}
		});
		assertQueued(observed, "Q03 actual HitTask was uncounted after ActionLease close and before native callback entry.");
	}

	/** Caller learns the real stock Wind Strike on its seed Player before canonical materialization. */
	public static void runCast(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId, Creature target, Skill skill) throws Exception
	{
		final var scope = scope(context, player, materialization, profileId);
		PhantomAssertions.assertTrue((skill != null) && (skill.getId() == 1177) && (player.getKnownSkill(1177) == skill), "INVALID Q03: exact managed Player must know the supplied stock Wind Strike.");
		final var observed = probe(context, "Q03.queuedCast", scope, player, target, skill, () ->
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "Q03 cast ActionLease changed the exact Player.");
				player.setTarget(target);
				PhantomAssertions.assertTrue(skill.getTargetList(player).contains(target), "INVALID Q03: stock Wind Strike target handler rejected its native target.");
				player.doCast(skill);
			}
		});
		PhantomAssertions.assertTrue(!observed.miss() && (observed.hpCpLoss() > 0), "INVALID Q03: stock MagicalDamage did not produce a native HP/CP write.");
		assertQueued(observed, "Q03 actual MagicUseTask was uncounted after ActionLease close and before native callback entry.");
	}

	/** The NPC publisher runs without borrowing the managed receiver's ActionLease or execution context. */
	public static void runIncomingAttack(PhantomTestContext context, Player receiver, PhantomMaterializationService materialization, long profileId, Creature npc) throws Exception
	{
		final var scope = scope(context, receiver, materialization, profileId);
		PhantomAssertions.assertTrue(npc.isNpc() && !npc.isPlayable(), "INVALID Q12: incoming publisher must be an ordinary native NPC.");
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "INVALID Q12: incoming NPC publisher must have no borrowed managed context.");
		for (int attempt = 1; attempt <= 3; attempt++)
		{
			final var observed = probe(context, "Q12.incomingAttack." + attempt, scope, npc, receiver, null, () ->
			{
				npc.setTarget(receiver);
				npc.disableCoreAI(false);
				try { npc.doAttack(receiver); }
				finally { npc.disableCoreAI(true); }
			});
			if (!observed.miss())
			{
				PhantomAssertions.assertTrue(observed.hpCpLoss() > 0, "INVALID Q12: actual NPC HitTask damage event had no managed receiver HP/CP write.");
				assertQueued(observed, "Q12 ordinary NPC HitTask did not retain its exact managed receiver before native HP/CP mutation.");
				return;
			}
			// Stock physical misses are not changed; a bounded next real attack may establish the writer control.
			awaitAttackReady(npc);
		}
		throw new AssertionError("INVALID Q12: three actual stock NPC attacks missed; no native receiver writer was established.");
	}

	/** Optional non-physical incoming writer control using the same stock Wind Strike and actual target handler. */
	public static void runIncomingCast(PhantomTestContext context, Player receiver, PhantomMaterializationService materialization, long profileId, Creature npc, Skill skill) throws Exception
	{
		final var scope = scope(context, receiver, materialization, profileId);
		PhantomAssertions.assertTrue(npc.isNpc() && !npc.isPlayable() && (skill != null) && (skill.getId() == 1177), "INVALID Q12: incoming cast requires an ordinary NPC and real stock Wind Strike.");
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "INVALID Q12: incoming NPC cast must have no borrowed managed context.");
		final var observed = probe(context, "Q12.incomingCast", scope, npc, receiver, skill, () ->
		{
			npc.setTarget(receiver);
			PhantomAssertions.assertTrue(skill.getTargetList(npc).contains(receiver), "INVALID Q12: stock NPC Wind Strike target handler rejected its managed receiver.");
			npc.doCast(skill);
		});
		PhantomAssertions.assertTrue(!observed.miss() && (observed.hpCpLoss() > 0), "INVALID Q12: stock NPC MagicalDamage did not write managed receiver HP/CP.");
		assertQueued(observed, "Q12 ordinary NPC MagicUseTask did not retain its exact managed receiver before native HP/CP mutation.");
	}

	private static PhantomNativeWorkScope scope(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Queued native tests require the initialized allowlisted headless TEST environment.");
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && (player.getNativeWorkOwner() instanceof PhantomNativeWorkScope), "Queued native test requires the exact managed materialized Player scope.");
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
		{
			PhantomAssertions.assertTrue(action.player() == player, "Queued native fixture materialization identity changed.");
			player.getStatus().stopHpMpRegeneration();
		}
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(scope.isCurrent() && (scope.player() == player), "Queued native fixture owner is stale.");
		return scope;
	}

	private static Observation probe(PhantomTestContext context, String key, PhantomNativeWorkScope scope, Creature attacker, Creature target, Skill skill, Runnable publish) throws Exception
	{
		context.record(key + ".fixture", "INVALID_UNTIL_NATIVE_COMPLETION");
		PhantomAssertions.assertTrue(!attacker.isAlikeDead() && !target.isAlikeDead() && !target.isInvul(), "INVALID queued native fixture: actor/receiver dead or invulnerable.");
		PhantomAssertions.assertTrue((attacker.getInstanceId() == target.getInstanceId()) && attacker.isInSurroundingRegion(target), "INVALID queued native fixture: actors are not in the same actual surrounding region.");
		PhantomAssertions.assertFalse(attacker.isCastingNow() || attacker.isAttackingNow(), "INVALID queued native fixture: publisher already has a native cast/attack in progress.");
		final double beforeHpCp = target.getCurrentHp() + target.getCurrentCp();
		final var completed = new CountDownLatch(1);
		final var result = new AtomicReference<NativeCompletion>();
		final var listenerFailure = new AtomicReference<String>();
		final var dealt = new ConsumerEventListener(attacker, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if ((event.getAttacker() != attacker) || (event.getTarget() != target) || (event.getSkill() != skill) || event.isDamageOverTime()) { return; }
			complete(result, completed, listenerFailure, skill, false, event.getDamage(), beforeHpCp - target.getCurrentHp() - target.getCurrentCp());
		}, completed);
		final var avoided = new ConsumerEventListener(target, EventType.ON_CREATURE_ATTACK_AVOID, (OnCreatureAttackAvoid event) ->
		{
			if ((skill != null) || (event.getAttacker() != attacker) || (event.getTarget() != target) || event.isDamageOverTime()) { return; }
			complete(result, completed, listenerFailure, null, true, 0, 0);
		}, completed);
		attacker.addListener(dealt);
		target.addListener(avoided);
		Throwable primary = null;
		try
		{
			final int baseline;
			final int queued;
			final String queuedSnapshot;
			try (var gate = new ScheduledWorkerGate())
			{
				gate.acquire();
				baseline = scope.outstanding();
				PhantomAssertions.assertEquals(0, baseline, "INVALID queued native fixture: pre-existing native tickets hide publisher accounting.");
				publish.run();
				PhantomAssertions.assertTrue(skill == null ? attacker.isAttackingNow() : attacker.isCastingNow(), "INVALID queued native fixture: stock attack/cast frontend rejected publication.");
				queued = scope.outstanding();
				queuedSnapshot = scope.snapshot();
				context.record(key + ".scheduledWorkersHeld", gate.workerCount());
				context.record(key + ".baselineOutstanding", baseline);
				context.record(key + ".outstandingAfterLeaseBeforeEntry", queued);
				context.record(key + ".exactEpoch", scope.epoch());
				context.record(key + ".queuedSnapshot", queuedSnapshot);
				PhantomAssertions.assertEquals(1L, completed.getCount(), "INVALID queued native fixture: callback entered before all scheduled workers were released.");
				PhantomAssertions.assertEquals(null, PlayerNativeWork.current(scope), "INVALID queued native fixture: root ActionLease still owns the caller after native publication.");
			}
			PhantomAssertions.assertTrue(completed.await(10, TimeUnit.SECONDS), "INVALID queued native fixture: stock native task did not complete after worker release.");
			PhantomAssertions.assertEquals(null, listenerFailure.get(), "INVALID queued native fixture: native event observation failed.");
			final var nativeResult = result.get();
			PhantomAssertions.assertTrue(nativeResult != null, "INVALID queued native fixture: no exact native callback observation.");
			context.record(key + ".nativeTaskStack", nativeResult.stack());
			context.record(key + ".nativeDamage", nativeResult.damage());
			context.record(key + ".nativeHpCpLoss", nativeResult.hpCpLoss());
			context.record(key + ".nativeMiss", nativeResult.miss());
			context.record(key + ".fixture", "VALID_ACTUAL_NATIVE_COMPLETION");
			return new Observation(baseline, queued, nativeResult.miss(), nativeResult.hpCpLoss());
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { attacker.removeListener(dealt); target.removeListener(avoided); }
			catch (RuntimeException | Error failure)
			{
				if (primary == null) { throw failure; }
				primary.addSuppressed(failure);
			}
		}
	}

	private static void complete(AtomicReference<NativeCompletion> result, CountDownLatch completed, AtomicReference<String> failure, Skill skill, boolean miss, double damage, double hpCpLoss)
	{
		final String nativeTask = skill == null ? "org.l2jmobius.gameserver.model.actor.tasks.creature.HitTask" : "org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask";
		final var frames = Thread.currentThread().getStackTrace();
		if (!Arrays.stream(frames).anyMatch(frame -> nativeTask.equals(frame.getClassName()) && "run".equals(frame.getMethodName())))
		{
			failure.compareAndSet(null, "Expected actual scheduled " + nativeTask + ".run on native event stack.");
		}
		final String stack = Arrays.stream(frames).limit(24).map(frame -> frame.getClassName() + "." + frame.getMethodName()).reduce((left, right) -> left + ">" + right).orElse("");
		result.compareAndSet(null, new NativeCompletion(miss, damage, hpCpLoss, stack));
		completed.countDown();
	}

	private static void assertQueued(Observation observed, String message)
	{
		PhantomAssertions.assertTrue(observed.queued() > observed.baseline(), message);
	}

	private static void awaitAttackReady(Creature attacker) throws Exception
	{
		final var ready = new CountDownLatch(1);
		final long remaining = Math.max(0, TimeUnit.NANOSECONDS.toMillis(attacker.getAttackEndTime() - System.nanoTime())) + 10;
		PhantomAssertions.assertTrue(remaining <= 5000, "INVALID Q12: native attack reuse exceeds the bounded fixture budget.");
		final var future = ThreadPool.schedule(ready::countDown, remaining);
		PhantomAssertions.assertTrue(future != null, "INVALID Q12: native attack reuse marker could not be scheduled.");
		try { PhantomAssertions.assertTrue(ready.await(6, TimeUnit.SECONDS), "INVALID Q12: native attack reuse marker did not finish."); }
		finally { future.cancel(false); }
	}

	private record Observation(int baseline, int queued, boolean miss, double hpCpLoss) { }
	private record NativeCompletion(boolean miss, double damage, double hpCpLoss, String stack) { }

	/** Reuses the existing headless warm-up pattern; it never changes scheduler configuration or reflection state. */
	private static final class ScheduledWorkerGate implements AutoCloseable
	{
		private final int _workerCount = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		private final CountDownLatch _started;
		private final CountDownLatch _release = new CountDownLatch(1);
		private final List<ScheduledFuture<?>> _workers = new ArrayList<>();
		private final AtomicReference<String> _failure = new AtomicReference<>();

		private ScheduledWorkerGate()
		{
			PhantomAssertions.assertTrue((_workerCount > 0) && (_workerCount <= 128), "INVALID queued native fixture: scheduled pool size must be within the bounded 1..128 TEST worker budget.");
			_started = new CountDownLatch(_workerCount);
		}

		private int workerCount() { return _workerCount; }

		private void acquire() throws Exception
		{
			for (int index = 0; index < _workerCount; index++)
			{
				final var future = ThreadPool.schedule(() ->
				{
					_started.countDown();
					try
					{
						if (!_release.await(10, TimeUnit.SECONDS)) { _failure.compareAndSet(null, "Scheduled TEST worker release timed out."); }
					}
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); _failure.compareAndSet(null, "Scheduled TEST worker was interrupted."); }
				}, 0);
				PhantomAssertions.assertTrue(future != null, "INVALID queued native fixture: scheduled TEST worker was not accepted.");
				_workers.add(future);
			}
			PhantomAssertions.assertTrue(_started.await(5, TimeUnit.SECONDS), "INVALID queued native fixture: not every actual scheduled worker reached its pre-entry barrier.");
			PhantomAssertions.assertEquals(null, _failure.get(), "INVALID queued native fixture: worker barrier expired before publication.");
		}

		@Override public void close() throws Exception
		{
			_release.countDown();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			try
			{
				for (var worker : _workers)
				{
					worker.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
				}
				PhantomAssertions.assertEquals(null, _failure.get(), "INVALID queued native fixture: scheduled TEST worker failed.");
			}
			finally { _workers.forEach(worker -> worker.cancel(false)); }
		}
	}
}
