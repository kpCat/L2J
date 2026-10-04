/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.CreatureAI;
import org.l2jmobius.gameserver.ai.PlayerAI;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.holders.creature.EffectList;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.BuffFinishTask;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Actual stock publishers/writers. Entry gates supply no movement, penalty or effect writer. */
public final class PhantomM1RawProducerChecks
{
	private PhantomM1RawProducerChecks() { }

	/** Exact caller-owned production fixture, or ordinary native control when service is null. */
	public static void follow(PhantomTestContext context, Player player, Creature target, PhantomMaterializationService service, long profileId) throws Exception
	{
		final var scope = require(context, player, service);
		PhantomAssertions.assertTrue(target != player && !player.getAI().isFollowing() && !player.isMovementDisabled() && !player.isMoving()
			&& !player.isAttackingNow() && !player.isCastingNow() && player.isInsideRadius3D(target, 2500)
			&& !player.isInsideRadius3D(target, 128), "INVALID raw follow: fresh movable native Player and actual nearby target required.");
		final CreatureAI previous = player.getAI();
		final var ai = new HeldFollow(player);
		final var checkpoint = new Checkpoint(player);
		Throwable primary = null;
		try
		{
			player.setAI(ai);
			publish(player, service, profileId, () ->
			{
				ai.startFollow(target, 64); // Original immediate native body supplies a writer control.
				PhantomAssertions.assertTrue(player.isMoving(), "INVALID raw follow: original moveToPawn did not publish actual movement.");
				player.stopMove(null);
			});
			ai.armed.set(true);
			PhantomAssertions.assertTrue(ai.entered.await(4, TimeUnit.SECONDS), "INVALID raw follow: actual shared scheduled follow body did not enter.");
			PhantomAssertions.assertTrue(ai.nativeStack.get(), "INVALID raw follow: shared CreatureFollowTaskManager body missing.");
			final int running = scope == null ? 0 : scope.outstanding();
			checkpoint.start();
			checkpoint.observeAdmission(scope);
			final boolean crossed = checkpoint.finished.await(200, TimeUnit.MILLISECONDS);
			ai.release.countDown();
			PhantomAssertions.assertTrue(ai.finished.await(3, TimeUnit.SECONDS), "Raw follow did not complete original stock writer.");
			checkpoint.join();
			PhantomAssertions.assertEquals(null, ai.failure.get(), "Original follow body failed.");
			PhantomAssertions.assertTrue(ai.moving.get(), "INVALID raw follow: actual stock movement writer was not reached after release.");
			context.record("Q12.rawFollow." + (scope != null), "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " sharedTick=true running=" + running + " owned=" + ai.owned.get() + " checkpointCrossed=" + crossed + " nativeMove=true");
			if (scope != null)
			{
				PhantomAssertions.assertTrue(running > 0 && ai.owned.get(), "Raw shared follow body has no exact native accounting before moveToPawn.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint crossed actual running follow before its stock writer.");
			}
			PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Raw follow native checkpoint failed.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			ai.armed.set(false); ai.release.countDown();
			try
			{
				if (ai.entered.getCount() == 0) { PhantomAssertions.assertTrue(ai.finished.await(3, TimeUnit.SECONDS), "Follow disposal retained native body."); }
				checkpoint.join(); ai.stopFollow(); player.stopMove(null); player.setAI(previous);
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** Native inventory weight is the cause; stock refreshOverloaded owns skill4270 and overload writes. */
	public static void status50(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId) throws Exception
	{
		final var scope = require(context, player, service);
		final var arrows = ItemData.getInstance().getTemplate(17);
		final var weightBuff = SkillData.getInstance().getSkill(4364, 1);
		PhantomAssertions.assertTrue(arrows != null && arrows.isStackable() && arrows.getWeight() > 0 && player.getMaxLoad() > 0
			&& player.getWeightPenalty() == 0 && player.getKnownSkill(4270) == null && weightBuff != null && !weightBuff.isPassive() && !weightBuff.isStayAfterDeath()
			&& player.getEffectList().getBuffInfoBySkillId(4364) == null, "INVALID raw status50: fresh stock arrow and removable stock weight buff required.");
		final int baseMaxLoad = player.getMaxLoad();
		final long added = (((long) baseMaxLoad + player.getBonusWeightPenalty() - player.getCurrentLoad()) / arrows.getWeight()) + 2;
		PhantomAssertions.assertTrue(added > 0 && added <= 1000000, "INVALID raw status50: stock inventory fixture exceeds bounded count.");
		final Field status = Player.class.getDeclaredField("_updateAndBroadcastStatusTask"); status.setAccessible(true);
		PhantomAssertions.assertEquals(null, status.get(player), "INVALID raw status50: previous status callback pending.");
		final var checkpoint = new Checkpoint(player);
		boolean seeded = false;
		Throwable primary = null;
		try (var workers = new WorkerGate())
		{
			workers.acquire();
			publish(player, service, profileId, () ->
			{
				weightBuff.applyEffects(player, player);
				PhantomAssertions.assertTrue(player.getMaxLoad() > baseMaxLoad, "INVALID raw status50: original4364 weight stat did not change maximum.");
				PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, 17, added, player, null) != null, "INVALID raw status50: native inventory addition failed.");
			});
			seeded = true;
			final int beforePenalty = player.getWeightPenalty();
			PhantomAssertions.assertTrue(player.getCurrentLoad() > baseMaxLoad && player.getCurrentLoad() < player.getMaxLoad()
				&& beforePenalty > 0 && beforePenalty < 4 && player.getSkillLevel(4270) == beforePenalty && !player.isOverloaded(),
				"INVALID raw status50: synchronous inventory writer must use the genuine buffed weight threshold.");
			publish(player, service, profileId, player::stopAllEffectsExceptThoseThatLastThroughDeath);
			PhantomAssertions.assertTrue(player.getMaxLoad() == baseMaxLoad && player.getEffectList().getBuffInfoBySkillId(4364) == null
				&& player.getWeightPenalty() == beforePenalty && !player.isOverloaded(), "INVALID raw status50: original buff removal must change actual maximum before delayed penalty writer.");
			final var nativeFuture = (ScheduledFuture<?>) status.get(player);
			PhantomAssertions.assertTrue(nativeFuture != null && !nativeFuture.isDone(), "INVALID raw status50: original50ms publisher did not enqueue.");
			final int queued = scope == null ? 0 : scope.outstanding();
			checkpoint.start(); checkpoint.observeAdmission(scope);
			final boolean crossed = checkpoint.finished.await(200, TimeUnit.MILLISECONDS);
			workers.release(); nativeFuture.get(4, TimeUnit.SECONDS); checkpoint.join();
			PhantomAssertions.assertTrue(player.getWeightPenalty() == 4 && player.getSkillLevel(4270) == 4 && player.isOverloaded(),
				"INVALID raw status50: original refreshOverloaded native penalty/skill writer was not established.");
			context.record("Q12.rawStatus50." + (scope != null), "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " queued=" + queued + " checkpointCrossed=" + crossed + " stockWeightBuff=4364 baseMaxLoad=" + baseMaxLoad
				+ " penaltyBefore=" + beforePenalty + " actual4270=4 overloaded=true workers=" + workers.count);
			if (scope != null)
			{
				PhantomAssertions.assertTrue(queued > 0, "Raw status50 publisher lost exact native work after stopEffects ActionLease closed.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint crossed queued raw status50 before actual penalty writer.");
			}
			PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Raw status50 native checkpoint failed.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				checkpoint.join();
				if (seeded || player.getEffectList().getBuffInfoBySkillId(4364) != null)
				{
					final boolean removeAdded = seeded;
					publish(player, service, profileId, () ->
					{
						player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, 4364);
						final var item = player.getInventory().getItemByItemId(17);
						if (removeAdded)
						{
							PhantomAssertions.assertTrue(item != null && item.getCount() >= added, "Raw status50 disposal lost exact added native quantity.");
							player.getInventory().destroyItem(ItemProcessType.DESTROY, item, added, player, null);
						}
						player.refreshOverloaded();
					});
				}
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** Original finite stock stat writer and natural expiry, before the separate NULL rejection case. */
	public static void buffFinishControl(PhantomTestContext context, Player player, boolean managed) throws Exception
	{
		require(context, player, null);
		final var skill = SkillData.getInstance().getSkill(3125, 1);
		PhantomAssertions.assertTrue(skill != null && player.getEffectList().getBuffInfoBySkillId(3125) == null, "INVALID finite buff control: fresh stock3125 required.");
		final NativeLifetime lifetime = managed ? new NativeLifetime(player) : null;
		final double base = player.getMaxHp();
		try
		{
			PlayerNativeWork.run(player, "TEST_FINITE_BUFF_CONTROL", () -> skill.applyEffects(player, player, false, 3));
			final BuffInfo info = player.getEffectList().getBuffInfoBySkillId(3125);
			PhantomAssertions.assertTrue(info != null && info.getAbnormalTime() == 3 && player.getMaxHp() > base, "INVALID finite buff control: actual stock stat writer missing.");
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
			while ((player.getEffectList().getBuffInfoBySkillId(3125) != null || player.getMaxHp() != base) && System.nanoTime() < deadline) { Thread.sleep(20); }
			PhantomAssertions.assertTrue(player.getEffectList().getBuffInfoBySkillId(3125) == null && player.getMaxHp() == base,
				"INVALID finite buff control: actual stock natural expiry/stat removal missing.");
			context.record("Q12.rawBuffFinishControl." + managed, "object=" + player.getObjectId() + " stock3125=true finite3s=true nativeStatIncrease=true naturalExpiry=true");
		}
		finally
		{
			if (lifetime != null) { lifetime.close(); }
			else { player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, 3125); player.stopAllTasks(); }
		}
	}

	/**
	 * Caller supplies a fresh native loaded Player. Managed mode owns/discards its TEST scope and lease,
	 * avoiding a retained production materialization entry. No successful store of a rejected lifetime.
	 */
	public static Receipt buffFinishNull(PhantomTestContext context, Player player, boolean managed) throws Exception
	{
		require(context, player, null);
		final var skill = SkillData.getInstance().getSkill(3125, 1);
		PhantomAssertions.assertTrue(skill != null && player.getEffectList().getBuffInfoBySkillId(3125) == null, "INVALID BuffFinish NULL: original stock3125 required.");
		player.stopAllTasks(); player.getStatus().stopHpMpRegeneration();
		final double base = player.getMaxHp();
		final Field poolField = ThreadPool.class.getDeclaredField("SCHEDULED_POOL"); poolField.setAccessible(true);
		final Object previousPool = poolField.get(null);
		// Reuse the verified NativeScope scheduler injector; do not introduce another scheduler.
		final var constructor = Class.forName(PhantomM1NativeScopeChecks.class.getName() + "$FaultPool").getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		final var nullPool = (ScheduledThreadPoolExecutor) constructor.newInstance("NULL");
		final var faultPool = new TargetedBuffNullPool((ScheduledThreadPoolExecutor) previousPool, nullPool);
		final Field finishField = Creature.class.getDeclaredField("_buffFinishTask"); finishField.setAccessible(true);
		final BuffFinishTask finish = (BuffFinishTask) finishField.get(player);
		final Field futureField = BuffFinishTask.class.getDeclaredField("_task"); futureField.setAccessible(true);
		PhantomAssertions.assertEquals(null, futureField.get(finish), "INVALID BuffFinish NULL: original expiry producer must be fresh.");
		final NativeLifetime lifetime = managed ? new NativeLifetime(player) : null;
		final PhantomNativeWorkScope scope = lifetime == null ? null : lifetime.scope;
		Throwable publication = null;
		Throwable primary = null;
		boolean successfulStore = false;
		try
		{
			poolField.set(null, faultPool);
			try { PlayerNativeWork.run(player, "TEST_FINITE_BUFF_PUBLICATION", () -> skill.applyEffects(player, player, false, 3)); }
			catch (RuntimeException | Error failure) { publication = failure; }
			finally { poolField.set(null, previousPool); }
			final BuffInfo info = player.getEffectList().getBuffInfoBySkillId(3125);
			final boolean stockWriter = player.getMaxHp() > base;
			final boolean futureMissing = futureField.get(finish) == null;
			final boolean incident = scope != null && scope.firstNativeIncident() != null;
			final String publicationClass = publication == null ? "NONE" : publication.getClass().getName();
			final String firstFrame = publication == null ? "NONE" : java.util.Arrays.stream(publication.getStackTrace())
				.filter(frame -> frame.getClassName().startsWith("org.l2jmobius.gameserver.") || frame.getClassName().startsWith("org.l2jmobius.commons."))
				.findFirst().map(Object::toString).orElse("NONE");
			context.record("Q12.rawBuffFinishNull." + managed, "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " finite3s=" + (info != null && info.getAbnormalTime() == 3) + " nativeStatWriter=" + stockWriter + " futureMissing=" + futureMissing
				+ " rejected=" + (publication != null) + " incident=" + incident + " outstanding=" + (scope == null ? 0 : scope.outstanding())
				+ " targetedPublications=" + faultPool.publications.get() + " originalSubmission=" + faultPool.nativeFrame.get() + " task=" + faultPool.taskClass.get()
				+ " publicationClass=" + publicationClass + " topNativeFrame=" + firstFrame
				+ " nativeIncident=" + (incident ? scope.firstNativeIncident().exceptionClass() + ":" + scope.firstNativeIncident().message() : "NONE"));
			PhantomAssertions.assertTrue(faultPool.publications.get() == 1 && faultPool.nativeFrame.get() != null,
				"INVALID BuffFinish NULL: exact original finite BuffFinish submission was not targeted once.");
			if (managed)
			{
				PhantomAssertions.assertTrue(publication != null && incident && !scope.open(),
					"Finite managed stock BuffFinish NULL silently accepted missing expiry producer without native incident.");
				PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.pendingTimers() == 0, "Rejected finite buff leaked exact native accounting.");
				final var first = scope.firstNativeIncident();
				try { player.store(false); successfulStore = true; } catch (IllegalStateException expected) { }
				PhantomAssertions.assertFalse(successfulStore, "Rejected finite buff lifetime reported successful native store.");
				PhantomAssertions.assertTrue(scope.firstNativeIncident() == first, "Rejected finite buff native incident was cleared/replaced.");
			}
			else
			{
				PhantomAssertions.assertTrue(publication == null && info != null && info.getAbnormalTime() == 3 && stockWriter && futureMissing,
					"INVALID ordinary NULL control: original finite stock stat writer/raw null behavior changed.");
			}
			return new Receipt(player.getObjectId(), scope == null ? 0 : scope.epoch(), incident, scope == null ? 0 : scope.outstanding(), scope == null ? 0 : scope.pendingTimers(), successfulStore, managed);
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				poolField.set(null, previousPool); faultPool.shutdownNow(); nullPool.shutdownNow();
				faultPool.awaitTermination(3, TimeUnit.SECONDS); nullPool.awaitTermination(3, TimeUnit.SECONDS);
				if (lifetime != null)
				{
					lifetime.close();
					context.record("Q12.rawBuffFinishNull.disposal", "object=" + player.getObjectId() + " epoch=" + scope.epoch()
						+ " outstanding=" + scope.outstanding() + " pending=" + scope.pendingTimers() + " firstIncidentRetained=" + (scope.firstNativeIncident() != null)
						+ " exactLeaseClosed=" + lifetime.identity.isClosed() + " nativeOwnerDetached=" + (player.getNativeWorkOwner() == null) + " successfulStore=" + successfulStore);
				}
				else { player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, 3125); player.stopAllTasks(); }
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	public record Receipt(int objectId, long epoch, boolean nativeIncident, int outstanding, int pendingTimers, boolean successfulStore, boolean discarded) { }

	/** Fresh ordinary loaded TEST Player; this helper owns and discards its exact managed lifetime. */
	public static Receipt status50Submission(PhantomTestContext context, Player player, String mode) throws Exception
	{
		return coalescingSubmission(context, player, mode, true);
	}

	/** Actual original pulse, then original stop before the submit result can be retained by publishPulse. */
	public static void buffFinishInlineStop(PhantomTestContext context, Player player) throws Exception
	{
		require(context, player, null); player.stopAllTasks(); player.getStatus().stopHpMpRegeneration();
		final var skill = SkillData.getInstance().getSkill(3125, 1);
		PhantomAssertions.assertTrue(skill != null && player.getEffectList().getBuffInfoBySkillId(3125) == null, "INVALID BuffFinish INLINE: fresh stock3125 required.");
		final Field poolField = ThreadPool.class.getDeclaredField("SCHEDULED_POOL"); poolField.setAccessible(true);
		final var original = (ScheduledThreadPoolExecutor) poolField.get(null);
		final Field finishField = Creature.class.getDeclaredField("_buffFinishTask"); finishField.setAccessible(true);
		final var finish = (BuffFinishTask) finishField.get(player);
		final Field futureField = BuffFinishTask.class.getDeclaredField("_task"); futureField.setAccessible(true);
		final Field publicationField = BuffFinishTask.class.getDeclaredField("_publication"); publicationField.setAccessible(true);
		final Field stoppedField = BuffFinishTask.class.getDeclaredField("_stopped"); stoppedField.setAccessible(true);
		final Field infosField = BuffFinishTask.class.getDeclaredField("_buffInfos"); infosField.setAccessible(true);
		PhantomAssertions.assertTrue(futureField.get(finish) == null && publicationField.get(finish) == null, "INVALID BuffFinish INLINE: original publisher is not fresh.");
		final var constructor = Class.forName(PhantomM1NativeScopeChecks.class.getName() + "$FaultPool").getDeclaredConstructor(String.class); constructor.setAccessible(true);
		final var inline = (ScheduledThreadPoolExecutor) constructor.newInstance("INLINE");
		final var returned = new AtomicReference<ScheduledFuture<?>>(); final var pulse = new AtomicBoolean();
		final var lifetime = new NativeLifetime(player); final var scope = lifetime.scope; final double base = player.getMaxHp();
		final var stopper = new ScheduledThreadPoolExecutor(1)
		{
			@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit)
			{
				PhantomAssertions.assertTrue(unit.toMillis(delay) == 0 && unit.toMillis(period) == 1000, "INVALID BuffFinish INLINE: native period changed.");
				final var future = inline.scheduleAtFixedRate(task, delay, period, unit); returned.set(future);
				try
				{
					final Object values = infosField.get(finish);
					PhantomAssertions.assertTrue(values instanceof java.util.Map<?, ?> map && map.size() == 1
						&& map.values().iterator().next() instanceof AtomicInteger count && count.get() == 1
						&& publicationField.get(finish) != null && futureField.get(finish) == null
						&& PlayerNativeWork.current(scope) != null && scope.isCurrent(), "INVALID BuffFinish INLINE: actual original owned pulse/publication window missing.");
					pulse.set(true); finish.stop(); return future;
				}
				catch (ReflectiveOperationException failure) { throw new IllegalStateException("TEST BuffFinish INLINE observation failed", failure); }
			}
		};
		final var targeted = new TargetedBuffNullPool(original, stopper); Throwable primary = null;
		try
		{
			poolField.set(null, targeted);
			try { PlayerNativeWork.run(player, "TEST_BUFF_INLINE_STOP", () -> skill.applyEffects(player, player, false, 3)); }
			finally { PhantomAssertions.assertTrue(poolField.get(null) == targeted, "BuffFinish INLINE TEST pool slot changed."); poolField.set(null, original); }
			PhantomAssertions.assertTrue(pulse.get() && targeted.publications.get() == 1 && targeted.nativeFrame.get() != null && player.getMaxHp() > base, "INVALID BuffFinish INLINE: original stock writer/pulse missing.");
			context.record("Q07.BuffFinish.inlineBeforeDrain", "stopped=" + stoppedField.get(finish) + " publication=" + (publicationField.get(finish) != null)
				+ " task=" + (futureField.get(finish) != null) + " returnedCancelled=" + (returned.get() != null && returned.get().isCancelled()) + " native=" + scope.snapshot());
			PhantomAssertions.assertTrue((boolean) stoppedField.get(finish) && publicationField.get(finish) == null && futureField.get(finish) == null
				&& returned.get() != null && returned.get().isCancelled(), "Actual inline stop retained a phantom BuffFinish Future/publication.");
			scope.checkpoint(() -> null); // Original EffectList may have lawful queued icons after registration returns.
			PhantomAssertions.assertTrue((boolean) stoppedField.get(finish) && publicationField.get(finish) == null && futureField.get(finish) == null
				&& returned.get() != null && returned.get().isCancelled() && scope.outstanding() == 0 && scope.pendingTimers() == 0 && scope.firstNativeIncident() == null,
				"Actual inline stop retained a phantom BuffFinish Future/publication/native obligation.");
			PlayerNativeWork.run(player, "TEST_BUFF_INLINE_RESTART", finish::start);
			PhantomAssertions.assertTrue(!(boolean) stoppedField.get(finish) && futureField.get(finish) instanceof ScheduledFuture<?> restarted && !restarted.isCancelled()
				&& publicationField.get(finish) == null && scope.isCurrent(), "Original BuffFinish restart did not publish a new native producer.");
			await(() -> player.getEffectList().getBuffInfoBySkillId(3125) == null && player.getMaxHp() == base && scope.outstanding() == 0, "Original restarted BuffFinish did not naturally expire/roll back its stock stat.");
			PhantomAssertions.assertTrue(scope.firstNativeIncident() == null && scope.open(), "Successful BuffFinish INLINE/restart retained a native failure.");
			context.record("Q12.rawBuffFinishInlineStop", "object=" + player.getObjectId() + " epoch=" + scope.epoch() + " actualOriginalPulse=true counter=1 stopBeforeFutureInstall=true cancelledReturnedFuture=true publicationCleared=true originalRestart=true naturalStockExpiry=true");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { if (poolField.get(null) == targeted) { poolField.set(null, original); } lifetime.close(); }
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } if (cleanup != primary) { primary.addSuppressed(cleanup); } }
			finally { targeted.shutdownNow(); stopper.shutdownNow(); inline.shutdownNow(); }
		}
	}

	/** Original EffectList.add and stock2031 effects, with NULL/INLINE only at the icons300 submission. */
	public static Receipt icons300Submission(PhantomTestContext context, Player player, String mode) throws Exception
	{
		return coalescingSubmission(context, player, mode, false);
	}

	private static Receipt coalescingSubmission(PhantomTestContext context, Player player, String mode, boolean status) throws Exception
	{
		require(context, player, null);
		PhantomAssertions.assertTrue(mode.equals("NULL") || mode.equals("INLINE"), "INVALID native coalescing control: NULL/INLINE required.");
		player.stopAllTasks(); player.getStatus().stopHpMpRegeneration();
		final EffectList list = player.getEffectList();
		final Field futureField = accessible(status ? Player.class : EffectList.class, status ? "_updateAndBroadcastStatusTask" : "_updateEffectIconTask");
		final Field tokenField = accessible(status ? Player.class : EffectList.class, status ? "_nativeStatusPublication" : "_nativeIconPublication");
		final Object holder = status ? player : list;
		PhantomAssertions.assertTrue(futureField.get(holder) == null && tokenField.get(holder) == null && list.getBuffs().isEmpty()
			&& list.getShortBuff() == null && player.getWeightPenalty() == 0 && player.getKnownSkill(4270) == null, "INVALID native coalescing control: fresh stock state required.");
		final Skill skill = SkillData.getInstance().getSkill(status ? 4364 : 2031, 1);
		PhantomAssertions.assertTrue(skill != null && !skill.isPassive() && (status ? !skill.isStayAfterDeath() : skill.isHealingPotionSkill()), "INVALID native coalescing control: exact stock skill missing.");
		final int baseMaxLoad = player.getMaxLoad();
		final var arrows = ItemData.getInstance().getTemplate(17);
		final long added = status && arrows != null && arrows.getWeight() > 0 ? (((long) baseMaxLoad + player.getBonusWeightPenalty() - player.getCurrentLoad()) / arrows.getWeight()) + 2 : 0;
		PhantomAssertions.assertTrue(!status || (arrows != null && arrows.isStackable() && baseMaxLoad > 0 && added > 0 && added <= 1000000), "INVALID status submission: bounded original arrow fixture required.");
		final Field poolField = accessible(ThreadPool.class, "SCHEDULED_POOL");
		final var original = (ScheduledThreadPoolExecutor) poolField.get(null);
		final var constructor = Class.forName(PhantomM1NativeScopeChecks.class.getName() + "$FaultPool").getDeclaredConstructor(String.class); constructor.setAccessible(true);
		final var control = (ScheduledThreadPoolExecutor) constructor.newInstance(mode);
		final var targeted = new TargetedCoalescingPool(original, control, status);
		final var lifetime = new NativeLifetime(player);
		final var scope = lifetime.scope;
		Throwable primary = null;
		Throwable publication = null;
		boolean successfulStore = false;
		try
		{
			if (status)
			{
				PlayerNativeWork.run(player, "TEST_STATUS_STOCK_SETUP", () ->
				{
					skill.applyEffects(player, player);
					PhantomAssertions.assertTrue(player.getMaxLoad() > baseMaxLoad && player.getInventory().addItem(ItemProcessType.REWARD, 17, added, player, null) != null, "INVALID status submission: actual stock weight setup failed.");
				});
				await(() -> scope.outstanding() == 0 && fieldValue(futureField, holder) == null && fieldValue(tokenField, holder) == null, "Status setup callbacks did not finish.");
				PhantomAssertions.assertTrue(player.getCurrentLoad() > baseMaxLoad && player.getCurrentLoad() < player.getMaxLoad()
					&& player.getWeightPenalty() > 0 && player.getWeightPenalty() < 4 && !player.isOverloaded(), "INVALID status submission: genuine buffed penalty threshold missing.");
			}
			final BuffInfo first = status ? null : stockInfo(player, skill);
			poolField.set(null, targeted);
			try { PlayerNativeWork.run(player, "TEST_COALESCING_ORIGINAL_PUBLICATION", status ? player::stopAllEffectsExceptThoseThatLastThroughDeath : () -> list.add(first)); }
			catch (RuntimeException | Error failure) { publication = failure; }
			finally { poolField.set(null, original); }
			final boolean firstWriter = status ? player.getMaxLoad() == baseMaxLoad && player.getEffectList().getBuffInfoBySkillId(4364) == null : list.getBuffInfoBySkillId(2031) == first;
			PhantomAssertions.assertTrue(firstWriter && targeted.publications.get() == 1 && targeted.nativeFrame.get() != null, "INVALID coalescing submission: exact original producer and preceding native writer missing.");
			PhantomAssertions.assertTrue(futureField.get(holder) == null && tokenField.get(holder) == null, "Completed/rejected native publication left its token or future assigned.");
			if (mode.equals("INLINE"))
			{
				PhantomAssertions.assertEquals(null, publication, "Original INLINE native publication failed.");
				assertCoalescingWriter(player, first, status);
				await(() -> scope.outstanding() == 0, "INLINE first publication retained work.");
				PlayerNativeWork.run(player, "TEST_COALESCING_SECOND_SETUP", () ->
				{
					if (status) { skill.applyEffects(player, player); player.refreshOverloaded(); }
					else { list.remove(SkillFinishType.REMOVED, first); }
				});
				await(() -> scope.outstanding() == 0 && fieldValue(futureField, holder) == null && fieldValue(tokenField, holder) == null, "Second native publication setup did not retire old callbacks.");
				PhantomAssertions.assertTrue(status ? player.getWeightPenalty() < 4 && !player.isOverloaded() && player.getMaxLoad() > baseMaxLoad : list.getShortBuff() == null && list.getBuffInfoBySkillId(2031) == null, "INVALID coalescing second setup: original native reversal missing.");
				final BuffInfo second = status ? null : stockInfo(player, skill);
				poolField.set(null, targeted);
				try { PlayerNativeWork.run(player, "TEST_COALESCING_SECOND_PUBLICATION", status ? player::stopAllEffectsExceptThoseThatLastThroughDeath : () -> list.add(second)); }
				finally { poolField.set(null, original); }
				assertCoalescingWriter(player, second, status);
				PhantomAssertions.assertTrue(targeted.publications.get() == 2 && futureField.get(holder) == null && tokenField.get(holder) == null && scope.open(), "Fresh second native callback was coalesced away or retained completed publication state.");
				await(() -> scope.outstanding() == 0, "INLINE second publication retained work.");
				PhantomAssertions.assertEquals(null, scope.firstNativeIncident(), "Successful original INLINE callbacks retained a native failure.");
			}
			else
			{
				PhantomAssertions.assertTrue(publication != null && scope.firstNativeIncident() != null && !scope.open(), "Original managed NULL publication silently accepted missing native work.");
				final var incident = scope.firstNativeIncident();
				PhantomAssertions.assertEquals(publication.getClass().getName(), incident.exceptionClass(), "NULL native incident replaced original primary class.");
				PhantomAssertions.assertEquals(PhantomCleanupIncident.bounded(publication.getMessage(), 160), incident.message(), "NULL native incident replaced original primary message.");
				await(() -> scope.outstanding() == 0, "NULL native publication retained queued/running work.");
				try { player.store(false); successfulStore = true; } catch (IllegalStateException expected) { }
				PhantomAssertions.assertFalse(successfulStore, "Failed native NULL lifetime reported STORE success.");
				PhantomAssertions.assertTrue(scope.firstNativeIncident() == incident, "Failed native NULL incident was cleared or replaced.");
			}
			context.record("Q07.raw" + (status ? "Status50" : "Icons300") + "." + mode, "object=" + player.getObjectId() + " epoch=" + scope.epoch()
				+ " precedingStockWriter=true targetedPublications=" + targeted.publications.get() + " nativeFrame=" + targeted.nativeFrame.get()
				+ " future=null token=null actualInlineWriter=" + mode.equals("INLINE") + " primary=" + (publication == null ? "NONE" : publication.getClass().getName())
				+ " nativeIncident=" + (scope.firstNativeIncident() != null) + " successfulStore=" + successfulStore);
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				poolField.set(null, original); targeted.shutdownNow(); control.shutdownNow();
				targeted.awaitTermination(3, TimeUnit.SECONDS); control.awaitTermination(3, TimeUnit.SECONDS); lifetime.close();
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
		return new Receipt(player.getObjectId(), scope.epoch(), scope.firstNativeIncident() != null, scope.outstanding(), scope.pendingTimers(), successfulStore, true);
	}

	/** Stock max-buff boundary, real oldest queue eviction and onExit stat removal; no cap/stat changes. */
	public static void buffEviction(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId) throws Exception
	{
		final var scope = require(context, player, service);
		final EffectList list = player.getEffectList();
		final int maximum = player.getStat().getMaxBuffCount();
		PhantomAssertions.assertTrue(maximum >= 20 && maximum <= 25 && list.getBuffs().isEmpty() && list.getShortBuff() == null,
			"INVALID stock eviction: fresh original20..25 buff cap required.");
		// Each factual stock level1 buff has a distinct abnormal type and unchanged1200s lifetime.
		final int[] ids = { 1040, 1043, 1044, 1045, 1048, 1062, 1068, 1077, 1078, 1085, 1086, 1087, 1182, 1189, 1191, 1204, 1240, 1242, 1243, 1257, 1303, 1352, 1353, 1354, 1392, 1393 };
		final List<Skill> skills = new ArrayList<>();
		final var abnormalTypes = new java.util.HashSet<>();
		for (int i = 0; i <= maximum; i++)
		{
			final Skill skill = SkillData.getInstance().getSkill(ids[i], 1);
			PhantomAssertions.assertTrue(skill != null && !skill.isPassive() && !skill.isDebuff() && !skill.isToggle() && !skill.isDance() && !skill.is7Signs()
				&& !skill.isTriggeredSkill() && !skill.isHealingPotionSkill() && !skill.isAbnormalInstant() && !skill.getAbnormalType().isNone()
				&& abnormalTypes.add(skill.getAbnormalType()), "INVALID stock eviction: distinct ordinary stock buff missing at" + ids[i]);
			skills.add(skill);
		}
		final Skill potion = SkillData.getInstance().getSkill(2031, 1);
		PhantomAssertions.assertTrue(potion != null && potion.isHealingPotionSkill(), "INVALID stock eviction: original2031 required.");
		final Field futureField = accessible(EffectList.class, "_updateEffectIconTask");
		final Field poolField = accessible(ThreadPool.class, "SCHEDULED_POOL");
		final var original = (ScheduledThreadPoolExecutor) poolField.get(null);
		final var constructor = Class.forName(PhantomM1NativeScopeChecks.class.getName() + "$FaultPool").getDeclaredConstructor(String.class); constructor.setAccessible(true);
		final var control = (ScheduledThreadPoolExecutor) constructor.newInstance("INLINE");
		final var targeted = new TargetedCoalescingPool(original, control, false);
		Throwable primary = null;
		try
		{
			publish(player, service, profileId, () -> { for (int i = 0; i < maximum - 1; i++) { skills.get(i).applyEffects(player, player); } });
			await(() -> fieldValue(futureField, list) == null && (scope == null || scope.outstanding() == 0), "Stock eviction setup icons did not finish.");
			PhantomAssertions.assertTrue(list.getBuffCount() == maximum - 1 && list.getBuffs().size() == maximum - 1 && player.getStat().getMaxBuffCount() == maximum,
				"INVALID stock eviction: actual native buffs did not fill the unchanged cap boundary.");
			final BuffInfo oldest = list.getBuffs().peek();
			PhantomAssertions.assertTrue(oldest != null && oldest.getSkill().getId() == 1040, "INVALID stock eviction: original insertion-order shield missing.");
			final var beforeShortWriter = new AtomicInteger(-1);
			final BuffInfo shortInfo = new BuffInfo(player, player, potion)
			{
				@Override public Skill getSkill()
				{
					if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(EffectList.class.getName()) && frame.getMethodName().startsWith("lambda$updateEffectIcons"))))
					{
						beforeShortWriter.compareAndSet(-1, list.getBuffCount());
					}
					return super.getSkill();
				}
			};
			for (AbstractEffect effect : potion.getEffects(EffectScope.GENERAL)) { shortInfo.addEffect(effect); }
			if (scope != null) { poolField.set(null, targeted); }
			try { publish(player, service, profileId, () -> list.add(shortInfo)); }
			finally { poolField.set(null, original); }
			if (scope == null) { await(() -> list.getShortBuff() == shortInfo, "Ordinary original icons writer did not assign potion."); }
			PhantomAssertions.assertTrue(beforeShortWriter.get() == maximum && list.getShortBuff() == shortInfo && list.getBuffCount() == maximum - 1 && list.getBuffs().contains(oldest),
				"INVALID stock eviction: original shortBuff writer/count exclusion not established.");
			publish(player, service, profileId, () -> skills.get(maximum - 1).applyEffects(player, player));
			PhantomAssertions.assertTrue(list.getBuffs().contains(oldest) && list.getBuffs().contains(shortInfo) && list.getBuffCount() == maximum && list.getBuffs().size() == maximum + 1,
				"Stock short potion consumed a normal buff slot and prematurely evicted the oldest buff.");
			final double defenceBefore = player.getPDef(null);
			publish(player, service, profileId, () -> skills.get(maximum).applyEffects(player, player));
			final List<Integer> actualOrder = list.getBuffs().stream().map(info -> info.getSkill().getId()).toList();
			final List<Integer> expectedOrder = new ArrayList<>();
			for (int i = 1; i < maximum - 1; i++) { expectedOrder.add(skills.get(i).getId()); }
			expectedOrder.add(2031); expectedOrder.add(skills.get(maximum - 1).getId()); expectedOrder.add(skills.get(maximum).getId());
			PhantomAssertions.assertTrue(oldest.isRemoved() && list.getBuffInfoBySkillId(1040) == null && !list.getBuffs().contains(oldest)
				&& list.getShortBuff() == shortInfo && list.getBuffCount() == maximum && list.getBuffs().size() == maximum + 1 && player.getPDef(null) < defenceBefore,
				"Actual original over-cap stopAndRemove did not evict shield and remove its native defence stat while retaining potion.");
			PhantomAssertions.assertEquals(expectedOrder, actualOrder, "Native buff eviction changed original insertion order or removed another stock buff.");
			context.record("Q12.rawBuffEviction." + (scope != null), "object=" + player.getObjectId() + " cap=" + maximum + " stockPotion=2031 beforeShortCount=" + beforeShortWriter.get()
				+ " shortExcluded=true prematureEviction=false actualRemoved=1040 originalQueueOrder=" + actualOrder + " nativeDefenceBefore=" + defenceBefore + " nativeDefenceAfter=" + player.getPDef(null));
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				poolField.set(null, original); targeted.shutdownNow(); control.shutdownNow();
				targeted.awaitTermination(3, TimeUnit.SECONDS); control.awaitTermination(3, TimeUnit.SECONDS);
				publish(player, service, profileId, () -> { for (Skill skill : skills) { list.stopSkillEffects(SkillFinishType.REMOVED, skill.getId()); } list.stopSkillEffects(SkillFinishType.REMOVED, 2031); });
				await(() -> fieldValue(futureField, list) == null && (scope == null || scope.outstanding() == 0), "Stock eviction cleanup still has native callbacks.");
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	private static void assertCoalescingWriter(Player player, BuffInfo info, boolean status)
	{
		PhantomAssertions.assertTrue(status ? player.getWeightPenalty() == 4 && player.getSkillLevel(4270) == 4 && player.isOverloaded()
			: player.getEffectList().getShortBuff() == info && player.getEffectList().getBuffCount() == 0, "Original INLINE callback did not finish its actual native writer before publication returned.");
	}

	private static BuffInfo stockInfo(Player player, Skill skill)
	{
		final var info = new BuffInfo(player, player, skill);
		for (AbstractEffect effect : skill.getEffects(EffectScope.GENERAL)) { info.addEffect(effect); }
		return info;
	}

	private static Field accessible(Class<?> type, String name) throws ReflectiveOperationException
	{
		final Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
	}

	private static Object fieldValue(Field field, Object holder)
	{
		try { return field.get(holder); } catch (IllegalAccessException failure) { throw new IllegalStateException("TEST native publication observation failed", failure); }
	}

	/** Only the nearest exact original publisher frame and its confirmed delay are injected. */
	private static final class TargetedCoalescingPool extends ScheduledThreadPoolExecutor
	{
		final ScheduledThreadPoolExecutor original, control;
		final boolean status;
		final AtomicInteger publications = new AtomicInteger();
		final AtomicReference<String> nativeFrame = new AtomicReference<>();
		TargetedCoalescingPool(ScheduledThreadPoolExecutor original, ScheduledThreadPoolExecutor control, boolean status)
		{
			super(1, body -> { final var thread = new Thread(body, "TEST-targeted-native-publication"); thread.setDaemon(true); return thread; });
			this.original = original; this.control = control; this.status = status;
		}
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit)
		{
			final var frame = StackWalker.getInstance().walk(frames -> frames.filter(value -> value.getClassName().equals(Player.class.getName())
				|| value.getClassName().equals(EffectList.class.getName())).findFirst().orElse(null));
			final boolean exact = unit.toMillis(delay) == (status ? 50 : 300) && frame != null && frame.getClassName().equals((status ? Player.class : EffectList.class).getName())
				&& frame.getMethodName().equals(status ? "publishNativeStatus" : "updateEffectIcons");
			if (!exact) { return original.schedule(task, delay, unit); }
			publications.incrementAndGet(); nativeFrame.compareAndSet(null, frame.toString()); return control.schedule(task, delay, unit);
		}
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit)
		{
			return original.scheduleAtFixedRate(task, delay, period, unit);
		}
	}

	/** Forward every unrelated original submission unchanged; NULL only the native BuffFinish add/start call. */
	private static final class TargetedBuffNullPool extends ScheduledThreadPoolExecutor
	{
		final ScheduledThreadPoolExecutor original, rejector;
		final AtomicInteger publications = new AtomicInteger();
		final AtomicReference<String> nativeFrame = new AtomicReference<>(), taskClass = new AtomicReference<>();
		TargetedBuffNullPool(ScheduledThreadPoolExecutor original, ScheduledThreadPoolExecutor rejector)
		{
			super(1, body -> { final var thread = new Thread(body, "TEST-targeted-BuffFinish-NULL"); thread.setDaemon(true); return thread; });
			this.original = original; this.rejector = rejector;
		}
		private boolean exactSubmission(Runnable task)
		{
			final var frame = StackWalker.getInstance().walk(frames -> frames.filter(value -> value.getClassName().equals(BuffFinishTask.class.getName())
				&& (value.getMethodName().equals("addBuffInfo") || value.getMethodName().equals("start"))).findFirst().orElse(null));
			if (frame == null) { return false; }
			publications.incrementAndGet(); nativeFrame.compareAndSet(null, frame.toString());
			// Observe the exact original ThreadPool wrapper, rather than construct a replacement publisher.
			Runnable observed = task;
			try
			{
				Class<?> type = task.getClass();
				while (type != null && !type.getName().equals(ThreadPool.class.getName() + "$RunnableWrapper")) { type = type.getSuperclass(); }
				if (type != null)
				{
					final Field wrapped = type.getDeclaredField("_wrappedRunnable"); wrapped.setAccessible(true);
					observed = (Runnable) wrapped.get(task);
				}
			}
			catch (ReflectiveOperationException failure) { throw new IllegalStateException("TEST exact scheduler observation failed", failure); }
			taskClass.compareAndSet(null, observed.getClass().getName()); return true;
		}
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit)
		{
			return exactSubmission(task) ? rejector.schedule(task, delay, unit) : original.schedule(task, delay, unit);
		}
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit)
		{
			return exactSubmission(task) ? rejector.scheduleAtFixedRate(task, delay, period, unit) : original.scheduleAtFixedRate(task, delay, period, unit);
		}
	}

	private static PhantomNativeWorkScope require(PhantomTestContext context, Player player, PhantomMaterializationService service)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Raw native probes require guarded TEST.");
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "Raw native probe cannot borrow managed execution context.");
		if (service == null) { PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "INVALID ordinary native control: managed lifetime supplied."); return null; }
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID raw native probe: exact production owner required.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(scope.player() == player && scope.isCurrent() && scope.open() && scope.outstanding() == 0, "INVALID raw native probe: fresh current quiescent lifetime required.");
		return scope;
	}

	private static void publish(Player player, PhantomMaterializationService service, long profileId, Runnable body)
	{
		if (service == null) { body.run(); return; }
		try (var action = service.tryAcquireAction(profileId).orElseThrow())
		{
			PhantomAssertions.assertTrue(action.player() == player, "Raw native probe changed exact Player.");
			body.run();
		}
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static final class Checkpoint
	{
		final CountDownLatch finished = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread thread;
		Checkpoint(Player player) { thread = new Thread(() -> { try { player.store(false); } catch (Throwable thrown) { failure.set(thrown); } finally { finished.countDown(); } }, "TEST-raw-native-checkpoint"); }
		void start() { thread.start(); }
		void observeAdmission(PhantomNativeWorkScope scope) throws InterruptedException { if (scope != null) { await(() -> finished.getCount() == 0 || !scope.open(), "Native checkpoint never reached exact owner admission fence."); } }
		void join() throws InterruptedException { if (thread.getState() != Thread.State.NEW) { thread.join(6000); } PhantomAssertions.assertFalse(thread.isAlive(), "Raw native checkpoint thread remains active."); }
	}

	private static final class HeldFollow extends PlayerAI
	{
		final Player player;
		final AtomicBoolean armed = new AtomicBoolean(), nativeStack = new AtomicBoolean(), owned = new AtomicBoolean(), moving = new AtomicBoolean(), first = new AtomicBoolean();
		final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		HeldFollow(Player player) { super(player); this.player = player; }
		@Override public void moveToPawn(WorldObject pawn, int offset)
		{
			if (!armed.get() || !first.compareAndSet(false, true)) { super.moveToPawn(pawn, offset); return; }
			nativeStack.set(StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith("org.l2jmobius.gameserver.taskmanagers.CreatureFollowTaskManager$") && frame.getMethodName().equals("run"))));
			owned.set(player.getNativeWorkOwner() != null && PlayerNativeWork.current(player.getNativeWorkOwner()) != null);
			entered.countDown();
			try
			{
				if (!release.await(4, TimeUnit.SECONDS)) { failure.set(new IllegalStateException("Raw follow entry gate expired.")); return; }
				super.moveToPawn(pawn, offset); moving.set(player.isMoving());
			}
			catch (InterruptedException thrown) { Thread.currentThread().interrupt(); failure.set(thrown); }
			catch (RuntimeException | Error thrown) { failure.set(thrown); throw thrown; }
			finally { finished.countDown(); }
		}
	}

	private static final class NativeLifetime implements AutoCloseable
	{
		final Player player; final PhantomNativeWorkScope scope; final PhantomIdentityLeaseRegistry.Lease identity;
		NativeLifetime(Player player)
		{
			this.player = player; identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(player.getObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(identity != null, "INVALID finite buff: exact TEST lease unavailable.");
			scope = new PhantomNativeWorkScope(new Object(), player, identity, System.nanoTime());
			try { player.attachNativeWorkOwner(scope); } catch (RuntimeException | Error failure) { identity.close(); throw failure; }
		}
		@Override public void close() throws Exception
		{
			await(() -> scope.outstanding() == 0, "Finite buff TEST disposal still has running work.");
			player.stopAllTasks();
			if (scope.pendingTimers() > 0)
			{
				try { scope.drainAndSeal(System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); }
				catch (IllegalStateException failure) { PhantomAssertions.assertTrue(scope.firstNativeIncident() != null && scope.outstanding() == 0 && scope.pendingTimers() == 0, "Finite buff failed TEST disposal is not quiescent."); }
			}
			PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.pendingTimers() == 0, "Finite buff TEST discard requires exact zero accounting.");
			player.deleteMe(); player.detachNativeWorkOwner(scope); identity.close();
		}
	}

	/** Same bounded real worker gate as QueuedWork/DynamicRecipient. No pool replacement. */
	private static final class WorkerGate implements AutoCloseable
	{
		final int count = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		final CountDownLatch started, released = new CountDownLatch(1);
		final List<ScheduledFuture<?>> workers = new ArrayList<>();
		final AtomicReference<String> failure = new AtomicReference<>();
		WorkerGate() { PhantomAssertions.assertTrue(count > 0 && count <= 128, "INVALID raw status50: scheduled pool outside1..128 bound."); started = new CountDownLatch(count); }
		void acquire() throws Exception
		{
			for (int i = 0; i < count; i++)
			{
				final var future = ThreadPool.schedule(() ->
				{
					started.countDown();
					try { if (!released.await(10, TimeUnit.SECONDS)) { failure.compareAndSet(null, "Worker gate expired."); } }
					catch (InterruptedException thrown) { Thread.currentThread().interrupt(); failure.compareAndSet(null, "Worker gate interrupted."); }
				}, 0);
				PhantomAssertions.assertTrue(future != null, "INVALID raw status50: real worker hold rejected."); workers.add(future);
			}
			PhantomAssertions.assertTrue(started.await(5, TimeUnit.SECONDS), "INVALID raw status50: real workers did not all enter.");
		}
		void release() { released.countDown(); }
		@Override public void close() throws Exception
		{
			release();
			try { for (var worker : workers) { worker.get(5, TimeUnit.SECONDS); } PhantomAssertions.assertEquals(null, failure.get(), "Raw status50 worker hold failed."); }
			finally { workers.forEach(worker -> worker.cancel(false)); }
		}
	}
}
