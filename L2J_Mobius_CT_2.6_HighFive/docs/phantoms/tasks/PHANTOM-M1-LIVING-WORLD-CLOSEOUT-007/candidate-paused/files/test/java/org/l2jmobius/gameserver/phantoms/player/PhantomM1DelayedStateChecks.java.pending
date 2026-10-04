/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.tasks.player.TeleportWatchdogTask;
import org.l2jmobius.gameserver.model.actor.holders.creature.EffectList;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Original native publishers and state writers; no constructed delayed task or manual callback. */
public final class PhantomM1DelayedStateChecks
{
	private PhantomM1DelayedStateChecks() { }

	/** Stand variant first waits for a real stock sit transition; no native state flags are fabricated. */
	public static void sitStand(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, boolean stand) throws Exception
	{
		final var scope = require(context, player, service);
		final Field progress = Player.class.getDeclaredField("_sittingInProgress"); progress.setAccessible(true);
		PhantomAssertions.assertTrue(!player.isSitting() && !player.isParalyzed() && !progress.getBoolean(player) && !player.isOutOfControl()
			&& !player.isAttackDisabled() && !player.isImmobilized() && !player.isCastingNow(), "INVALID sit/stand: fresh native state required.");
		final var checkpoint = new Checkpoint(player);
		Throwable primary = null;
		try
		{
			if (stand)
			{
				publish(player, service, profileId, player::sitDown);
				await(4000, () -> player.isSitting() && player.isParalyzed() && !readBoolean(progress, player) && (scope == null || scope.outstanding() == 0), "INVALID stand control: real stock sit writer did not complete.");
			}
			int queued;
			boolean crossed;
			try (var workers = new WorkerGate())
			{
				workers.acquire();
				publish(player, service, profileId, stand ? player::standUp : player::sitDown);
				PhantomAssertions.assertTrue(progress.getBoolean(player) && player.isSitting() && player.isParalyzed() == stand,
					"INVALID sit/stand: original2500 publisher did not leave the native animation in progress.");
				queued = scope == null ? 0 : scope.outstanding();
				checkpoint.start(); checkpoint.observeAdmission(scope);
				crossed = checkpoint.finished.await(150, TimeUnit.MILLISECONDS);
				workers.release();
			}
			await(4000, () -> !readBoolean(progress, player) && (stand ? !player.isSitting() && !player.isParalyzed() && player.getAI().getIntention() == Intention.IDLE : player.isSitting() && player.isParalyzed()),
				"INVALID sit/stand: original delayed native state writer did not complete.");
			checkpoint.join();
			context.record("Q12.native" + (stand ? "Stand" : "Sit") + "." + (scope != null), "object=" + player.getObjectId()
				+ " epoch=" + (scope == null ? 0 : scope.epoch()) + " delayMs=2500 queued=" + queued + " checkpointCrossed=" + crossed
				+ " actualSitting=" + player.isSitting() + " actualParalyzed=" + player.isParalyzed() + " progress=false");
			if (scope != null)
			{
				PhantomAssertions.assertTrue(queued > 0, "Actual sit/stand2500 publisher lost exact lifetime after its ActionLease closed.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint crossed actual queued sit/stand before native state writer.");
			}
			PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Sit/stand native checkpoint failed.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				checkpoint.join();
				await(4000, () -> !readBoolean(progress, player), "Sit/stand cleanup retained actual animation callback.");
				if (player.isSitting())
				{
					publish(player, service, profileId, player::standUp);
					await(4000, () -> !player.isSitting() && !player.isParalyzed() && !readBoolean(progress, player), "Sit/stand cleanup did not complete actual stock stand transition.");
				}
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** BuffInfo getter barrier precedes original icons300 shortBuff/count writer and returns the unchanged stock Skill. */
	public static void icons(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId) throws Exception
	{
		final var scope = require(context, player, service);
		final Skill potion = SkillData.getInstance().getSkill(2031, 1);
		final EffectList list = player.getEffectList();
		PhantomAssertions.assertTrue(potion != null && potion.isHealingPotionSkill() && list.getShortBuff() == null && list.getBuffInfoBySkillId(2031) == null,
			"INVALID icons300: fresh stock healing potion and shortBuff state required.");
		final Field futureField = EffectList.class.getDeclaredField("_updateEffectIconTask"); futureField.setAccessible(true);
		PhantomAssertions.assertEquals(null, futureField.get(list), "INVALID icons300: older icon callback pending.");
		final var gate = new IconGate(player);
		final BuffInfo info = new BuffInfo(player, player, potion)
		{
			@Override public Skill getSkill()
			{
				if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(EffectList.class.getName())
					&& frame.getMethodName().startsWith("lambda$updateEffectIcons")))) { gate.enter(); }
				return super.getSkill();
			}
		};
		final var checkpoint = new Checkpoint(player);
		final int originalCount = list.getBuffCount();
		Throwable primary = null;
		try
		{
			ScheduledFuture<?> nativeFuture;
			int queued;
			int beforeWriter;
			try (var workers = new WorkerGate())
			{
				workers.acquire();
				publish(player, service, profileId, () ->
				{
					for (AbstractEffect effect : potion.getEffects(EffectScope.GENERAL)) { info.addEffect(effect); }
					list.add(info); // Original EffectList -> raw icons300 publisher, original stock effects.
				});
				nativeFuture = (ScheduledFuture<?>) futureField.get(list);
				PhantomAssertions.assertTrue(nativeFuture != null && !nativeFuture.isDone() && list.getShortBuff() == null,
					"INVALID icons300: original publisher did not queue before shortBuff writer.");
				queued = scope == null ? 0 : scope.outstanding(); beforeWriter = list.getBuffCount();
				PhantomAssertions.assertTrue(beforeWriter == originalCount + 1, "INVALID icons300: stock short potion must affect native count before its icon callback.");
				workers.release();
			}
			PhantomAssertions.assertTrue(gate.entered.await(3, TimeUnit.SECONDS) && list.getShortBuff() == null, "INVALID icons300: original icon body did not hold before native shortBuff writer.");
			final int running = scope == null ? 0 : scope.outstanding();
			checkpoint.start(); checkpoint.observeAdmission(scope);
			final boolean crossed = checkpoint.finished.await(150, TimeUnit.MILLISECONDS);
			gate.release.countDown();
			nativeFuture.get(4, TimeUnit.SECONDS); checkpoint.join();
			PhantomAssertions.assertEquals(null, gate.failure.get(), "Icons native getter barrier failed.");
			PhantomAssertions.assertTrue(list.getShortBuff() == info && list.getBuffCount() == beforeWriter - 1,
				"INVALID icons300: original shortBuff assignment/native count change was not established.");
			context.record("Q12.nativeIcons300." + (scope != null), "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " queued=" + queued + " running=" + running + " owned=" + gate.owned.get() + " checkpointCrossed=" + crossed
				+ " nativeShortBuff=2031 countBefore=" + beforeWriter + " countAfter=" + list.getBuffCount());
			if (scope != null)
			{
				PhantomAssertions.assertTrue(queued > 0 && running > 0 && gate.owned.get(), "Actual icons300 callback lost exact native publication/body ownership.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint crossed actual running icons300 before shortBuff/count writer.");
			}
			PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Icons native checkpoint failed.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			gate.release.countDown();
			try
			{
				checkpoint.join();
				if (gate.entered.getCount() == 0) { await(3000, () -> futureFieldValue(futureField, list) == null, "Icons cleanup retained native callback."); }
				publish(player, service, profileId, () -> { list.stopSkillEffects(SkillFinishType.REMOVED, 2031); list.shortBuffStatusUpdate(null); });
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** Root alone sets/restores optional TEST config branch; this API never changes config or task delay. */
	public static void watchdog(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination) throws Exception
	{
		final var scope = require(context, player, service);
		final int seconds = PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT;
		PhantomAssertions.assertTrue(seconds > 0 && seconds <= 3, "INVALID enabled watchdog: caller must enable bounded existing TEST config branch.");
		PhantomAssertions.assertTrue(player.getClient() == null || !player.getClient().isDetached(), "INVALID watchdog: detached client completes teleport inline before optional watchdog.");
		final var geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(destination != null && geo.hasGeo(destination.getX(), destination.getY()) && destination.getInstanceId() == player.getInstanceId(),
			"INVALID watchdog: actual loaded same-instance native destination required.");
		final Location original = new Location(player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getInstanceId());
		final Field futureField = Player.class.getDeclaredField("_teleportWatchdog"); futureField.setAccessible(true);
		PhantomAssertions.assertEquals(null, futureField.get(player), "INVALID enabled watchdog: older native task pending.");
		final var checkpoint = new WatchdogCheckpoint(player);
		Throwable primary = null;
		boolean teleported = false;
		try
		{
			int running;
			boolean crossed;
			ScheduledFuture<?> actualFuture;
			ThreadInfo blocked;
			try (var workers = new WorkerGate())
			{
				workers.acquire();
				publish(player, service, profileId, () -> player.teleToLocation(destination, false));
				teleported = true;
				actualFuture = (ScheduledFuture<?>) futureField.get(player);
				PhantomAssertions.assertTrue(player.isTeleporting() && actualFuture != null && !actualFuture.isDone(),
					"INVALID watchdog: original teleport did not publish enabled native watchdog.");
				// Hold before the actual synchronized entry; never replace the task or its state writer.
				synchronized (player)
				{
					workers.release();
					await((seconds + 2) * 1000, () -> blockedWatchdog(player) != null, "INVALID watchdog: original task did not block entering exact native onTeleported monitor.");
					blocked = blockedWatchdog(player);
					PhantomAssertions.assertTrue(blocked != null && player.isTeleporting(), "INVALID watchdog: original teleport was confirmed before actual task entry.");
					running = scope == null ? 0 : scope.outstanding();
					checkpoint.start(); checkpoint.observeAdmission(scope);
					// The marker precedes nested store, so the Player monitor cannot hide an unowned checkpoint.
					crossed = checkpoint.supplierEntered.await(150, TimeUnit.MILLISECONDS);
				}
			}
			await((seconds + 3) * 1000, () -> !player.isTeleporting() && actualFuture.isDone() && player.isSpawned()
				&& World.getInstance().getPlayer(player.getObjectId()) == player, "INVALID watchdog: original scheduled native onTeleported/spawn writer did not complete.");
			checkpoint.join();
			PhantomAssertions.assertTrue(Math.abs(player.getX() - destination.getX()) <= 1 && Math.abs(player.getY() - destination.getY()) <= 1
				&& player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY(),
				"INVALID watchdog: actual native position/last-position writer missing.");
			context.record("Q12.enabledNativeWatchdog." + (scope != null), "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " configSeconds=" + seconds + " originalTeleportPublisher=true actualTaskThread=" + blocked.getThreadId() + " exactPlayerMonitor=true running=" + running + " checkpointSupplierCrossed=" + crossed
				+ " nativeOnTeleported=true spawned=true lastPositionWriter=true");
			if (scope != null)
			{
				PhantomAssertions.assertTrue(running > 0, "Actual running stock teleport watchdog has no exact lifetime before native onTeleported.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint supplier crossed actual running watchdog before its native spawn/position writer.");
			}
			else { PhantomAssertions.assertTrue(crossed, "INVALID ordinary watchdog control: generic checkpoint marker was masked by Player monitor."); }
			PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Watchdog native checkpoint failed.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				checkpoint.join();
				if (teleported)
				{
					await((seconds + 3) * 1000, () -> !player.isTeleporting(), "Watchdog cleanup retained actual teleport continuation.");
					publish(player, service, profileId, () -> player.teleToLocation(original, false));
					await((seconds + 3) * 1000, () -> !player.isTeleporting() && player.isSpawned(), "Watchdog cleanup did not finish original native return teleport.");
				}
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** Actual confirmation cancels only the idle optional producer; a second teleport must publish again. */
	public static void watchdogPendingCancellation(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination) throws Exception
	{
		final var scope = require(context, player, service);
		final int seconds = PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT;
		PhantomAssertions.assertTrue(seconds > 5 && seconds <= 15 && (player.getClient() == null || !player.getClient().isDetached()), "INVALID watchdog cancellation: caller must enable original bounded TEST timeout>5 without detached inline confirmation.");
		PhantomAssertions.assertTrue(destination != null && GeoEngine.getInstance().hasGeo(destination.getX(), destination.getY()) && destination.getInstanceId() == player.getInstanceId(), "INVALID watchdog cancellation: loaded same-instance native destination required.");
		final Location original = new Location(player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getInstanceId());
		final Field futureField = Player.class.getDeclaredField("_teleportWatchdog"); futureField.setAccessible(true);
		final Field tokenField = scope == null ? null : Player.class.getDeclaredField("_nativeTeleportPublication");
		final Field timerField = scope == null ? null : Player.class.getDeclaredField("_nativeTeleportTimer");
		if (scope != null) { tokenField.setAccessible(true); timerField.setAccessible(true); }
		PhantomAssertions.assertEquals(null, futureField.get(player), "INVALID watchdog cancellation: old optional future pending.");
		final int pendingBefore = scope == null ? 0 : scope.pendingTimers();
		Throwable primary = null;
		boolean moved = false;
		try
		{
			publish(player, service, profileId, () -> player.teleToLocation(destination, false)); moved = true;
			final var first = (ScheduledFuture<?>) futureField.get(player);
			PhantomAssertions.assertTrue(player.isTeleporting() && first != null && !first.isDone(), "INVALID cancellation: original teleport did not publish pending watchdog.");
			await(2000, () -> scope == null || scope.outstanding() == 0, "Pending watchdog control still has unrelated committed teleport callbacks.");
			final int queued = scope == null ? 0 : scope.outstanding();
			final int pending = scope == null ? 0 : scope.pendingTimers();
			publish(player, service, profileId, player::onTeleported);
			await(3000, () -> !player.isTeleporting() && player.isSpawned() && futureFieldValue(futureField, player) == null && (scope == null || scope.outstanding() == 0 && scope.pendingTimers() == pendingBefore), "Original native confirmation did not retire idle watchdog accounting.");
			PhantomAssertions.assertTrue(first.isCancelled() && World.getInstance().getPlayer(player.getObjectId()) == player
				&& player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY(), "INVALID cancellation: real native onTeleported/last-position writer or original future cancellation missing.");
			if (scope != null)
			{
				PhantomAssertions.assertTrue(queued == 0 && pending == pendingBefore + 1 && tokenField.get(player) == null && timerField.get(player) == null, "Confirmed optional watchdog retained a delay-long ticket, pending registration or stale coalescing metadata.");
			}
			publish(player, service, profileId, () -> player.teleToLocation(original, false));
			final var second = (ScheduledFuture<?>) futureField.get(player);
			PhantomAssertions.assertTrue(second != null && second != first && !second.isDone() && player.isTeleporting(), "Native second teleport was coalesced away by cancelled watchdog state.");
			publish(player, service, profileId, player::onTeleported);
			await(3000, () -> !player.isTeleporting() && player.isSpawned() && futureFieldValue(futureField, player) == null && (scope == null || scope.outstanding() == 0 && scope.pendingTimers() == pendingBefore), "Second original native confirmation did not finish.");
			PhantomAssertions.assertTrue(second.isCancelled() && (scope == null || tokenField.get(player) == null && timerField.get(player) == null), "Second confirmed original watchdog retained native metadata.");
			moved = false;
			context.record("Q07.nativeWatchdogPendingCancellation." + (scope != null), "object=" + player.getObjectId() + " configSeconds=" + seconds + " originalPublisher=true pendingBefore=" + pendingBefore + " pendingDuring=" + pending + " queued=" + queued + " actualOnTeleported=true firstCancelled=true secondOriginalProducer=true secondCancelled=true metadataClear=true");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				if (player.isTeleporting()) { publish(player, service, profileId, player::onTeleported); }
				if (moved) { publish(player, service, profileId, () -> { player.teleToLocation(original, false); player.onTeleported(); }); }
				await(3000, () -> !player.isTeleporting() && player.isSpawned() && (scope == null || scope.outstanding() == 0), "Watchdog cancellation cleanup retained native continuation.");
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	/** Explicit TEST EARNED parent seam; the optional task and both teleport writers remain stock. */
	public static void watchdogEarnedConfirmation(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination) throws Exception
	{
		final var scope = require(context, player, service);
		PhantomAssertions.assertTrue(scope != null && PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT == 10 && (player.getClient() == null || !player.getClient().isDetached()), "INVALID earned watchdog: exact managed fixture and caller TEST timeout10 required.");
		PhantomAssertions.assertTrue(destination != null && GeoEngine.getInstance().hasGeo(destination.getX(), destination.getY()) && destination.getInstanceId() == player.getInstanceId(), "INVALID earned watchdog: loaded same-instance destination required.");
		final Location original = new Location(player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getInstanceId());
		final Field future = Player.class.getDeclaredField("_teleportWatchdog"); future.setAccessible(true);
		final Field token = Player.class.getDeclaredField("_nativeTeleportPublication"); token.setAccessible(true);
		final Field timer = Player.class.getDeclaredField("_nativeTeleportTimer"); timer.setAccessible(true);
		PhantomAssertions.assertEquals(null, future.get(player), "INVALID earned watchdog: older optional producer pending.");
		final int pendingBefore = scope.pendingTimers();
		Throwable primary = null;
		boolean moved = false;
		try
		{
			moved = true;
			final EarnedConfirmation first = earnedConfirmation(player, scope, destination, future, token, timer);
			final var checkpointEntered = new CountDownLatch(1);
			final var checkpointFailure = new AtomicReference<Throwable>();
			final Thread checkpoint = new Thread(() ->
			{
				try { PlayerNativeWork.checkpoint(player, () -> { checkpointEntered.countDown(); return null; }); }
				catch (Throwable failure) { checkpointFailure.set(failure); }
			}, "TEST-earned-watchdog-confirmation-checkpoint");
			checkpoint.start();
			final boolean entered;
			try { entered = checkpointEntered.await(2, TimeUnit.SECONDS); }
			finally { checkpoint.join(6500); }
			context.record("Q07.nativeWatchdogEarnedConfirmation.first", "object=" + player.getObjectId() + " epoch=" + scope.epoch()
				+ " explicitTESTEarnedParent=" + first.earnedParent() + " originalTeleportPublisher=" + first.pending() + " actualNativeConfirmation=" + first.confirmed()
				+ " immediatelyCancelled=" + first.cancelled() + " immediateMetadataClear=" + first.metadataClear() + " pendingBefore=" + pendingBefore
				+ " pendingImmediatelyAfter=" + first.pendingAfter() + " checkpointSupplierWithin2s=" + entered + " outstanding=" + scope.outstanding());
			PhantomAssertions.assertFalse(checkpoint.isAlive(), "Earned optional-watchdog checkpoint remains active.");
			PhantomAssertions.assertTrue(first.earnedParent() && first.pending() && first.confirmed(), "INVALID earned watchdog: TEST context or original native teleport/confirmation writer missing.");
			PhantomAssertions.assertTrue(first.cancelled() && first.metadataClear() && first.pendingAfter() == pendingBefore && entered,
				"Optional native watchdog inherited earned lifetime and retained its10s reservation after actual confirmation.");
			PhantomAssertions.assertEquals(null, checkpointFailure.get(), "Confirmed earned-context optional watchdog prevented native checkpoint.");
			final EarnedConfirmation second = earnedConfirmation(player, scope, original, future, token, timer);
			PhantomAssertions.assertTrue(second.earnedParent() && second.pending() && second.confirmed() && second.future() != first.future(), "Actual second earned-context teleport did not publish a distinct original optional producer and confirm successfully.");
			PhantomAssertions.assertTrue(second.cancelled() && second.metadataClear() && second.pendingAfter() == pendingBefore, "Second earned-context native confirmation retained idle watchdog metadata or registration.");
			await(3000, () -> scope.outstanding() == 0 && scope.pendingTimers() == pendingBefore, "Earned-context watchdog confirmation leaked native tickets or pending registration.");
			PhantomAssertions.assertEquals(null, scope.firstNativeIncident(), "Successful earned-context original confirmation retained a native failure.");
			moved = false;
			context.record("Q07.nativeWatchdogEarnedConfirmation.second", "object=" + player.getObjectId() + " explicitTESTEarnedParent=true secondOriginalProducer=true distinctFuture=true actualNativeConfirmation=true immediatelyCancelled=true metadataClear=true outstanding=0 pending=" + scope.pendingTimers());
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try
			{
				if (scope.open())
				{
					if (player.isTeleporting()) { publish(player, service, profileId, player::onTeleported); }
					if (moved) { publish(player, service, profileId, () -> { player.teleToLocation(original, false); player.onTeleported(); }); }
				}
				// A failed native scope is retained for the caller's exact negative-fixture disposal.
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	private record EarnedConfirmation(ScheduledFuture<?> future, boolean earnedParent, boolean pending, boolean confirmed, boolean cancelled, boolean metadataClear, int pendingAfter) { }

	private static EarnedConfirmation earnedConfirmation(Player player, PhantomNativeWorkScope scope, Location destination, Field future, Field token, Field timer) throws Exception
	{
		final var result = new AtomicReference<EarnedConfirmation>();
		final var parent = PlayerNativeWork.schedule(player, List.of(), "TEST_EARNED_OPTIONAL_WATCHDOG", PlayerNativeWork.Semantics.EARNED, () ->
		{
			final var ticket = PlayerNativeWork.current(scope);
			final boolean earned = ticket != null && ticket.semantics() == PlayerNativeWork.Semantics.EARNED;
			player.teleToLocation(destination, false);
			final var actual = (ScheduledFuture<?>) futureFieldValue(future, player);
			final boolean pending = player.isTeleporting() && actual != null && !actual.isDone();
			player.onTeleported();
			final boolean confirmed = !player.isTeleporting() && player.isSpawned() && World.getInstance().getPlayer(player.getObjectId()) == player
				&& player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY()
				&& Math.abs(player.getX() - destination.getX()) <= 1 && Math.abs(player.getY() - destination.getY()) <= 1;
			result.set(new EarnedConfirmation(actual, earned, pending, confirmed, actual != null && actual.isCancelled(), futureFieldValue(future, player) == null
				&& futureFieldValue(token, player) == null && futureFieldValue(timer, player) == null, scope.pendingTimers()));
		}, 0);
		PhantomAssertions.assertTrue(parent != null, "INVALID earned watchdog: explicit TEST native parent submission refused.");
		parent.get(3, TimeUnit.SECONDS);
		PhantomAssertions.assertTrue(result.get() != null, "INVALID earned watchdog: explicit TEST native parent did not execute.");
		return result.get();
	}

	/** Token installed, but native timer constructor is blocked before registerTimer returns. */
	public static void watchdogRegistrationRace(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination, boolean stopTasks) throws Exception
	{
		watchdogPublicationRace(context, player, service, profileId, destination, false, stopTasks);
	}

	/** Native confirmation precedes the original publisher's token claim. */
	public static void watchdogClaimRace(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination) throws Exception
	{
		watchdogPublicationRace(context, player, service, profileId, destination, true, false);
	}

	private static void watchdogPublicationRace(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Location destination, boolean beforeClaim, boolean stopTasks) throws Exception
	{
		final var scope = require(context, player, service);
		PhantomAssertions.assertTrue(scope != null && PlayerConfig.TELEPORT_WATCHDOG_TIMEOUT == 10 && (player.getClient() == null || !player.getClient().isDetached()), "INVALID watchdog race: exact managed native fixture and caller TEST timeout10 required.");
		PhantomAssertions.assertTrue(destination != null && GeoEngine.getInstance().hasGeo(destination.getX(), destination.getY()) && destination.getInstanceId() == player.getInstanceId(), "INVALID watchdog race: loaded same-instance native destination required.");
		final Location original = new Location(player.getX(), player.getY(), player.getZ(), player.getHeading(), player.getInstanceId());
		final Field publicationLock = Player.class.getDeclaredField("_nativeTeleportPublicationLock"); publicationLock.setAccessible(true);
		final Field ownerLock = PhantomNativeWorkScope.class.getDeclaredField("_monitor"); ownerLock.setAccessible(true);
		final Field token = Player.class.getDeclaredField("_nativeTeleportPublication"); token.setAccessible(true);
		final Field timer = Player.class.getDeclaredField("_nativeTeleportTimer"); timer.setAccessible(true);
		final Field future = Player.class.getDeclaredField("_teleportWatchdog"); future.setAccessible(true);
		final Object publicationMonitor = publicationLock.get(player), ownerMonitor = ownerLock.get(scope);
		PhantomAssertions.assertTrue(token.get(player) == null && timer.get(player) == null && future.get(player) == null && pendingWatchdogs(scope, ownerMonitor) == 0, "INVALID watchdog race: stale native producer already present.");
		final CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		final AtomicReference<Throwable> publisherFailure = new AtomicReference<>(), holderFailure = new AtomicReference<>();
		final Thread holder = new Thread(() ->
		{
			synchronized (publicationMonitor)
			{
				held.countDown();
				try { if (!release.await(8, TimeUnit.SECONDS)) { holderFailure.set(new IllegalStateException("TEST watchdog publication gate expired.")); } }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); holderFailure.set(failure); }
			}
		}, "TEST-watchdog-publication-lock");
		final Thread publisher = new Thread(() -> { try { player.setTeleporting(true); } catch (Throwable failure) { publisherFailure.set(failure); } }, "TEST-original-watchdog-publisher");
		Throwable primary = null;
		boolean moved = false;
		try
		{
			final boolean invalidated;
			final boolean confirmed;
			if (beforeClaim)
			{
				synchronized (publicationMonitor)
				{
					publisher.start();
					await(3000, () -> blockedPublisher(publisher, publicationMonitor, Thread.currentThread().threadId(), Player.class.getName(), "publishNativeTeleportWatchdog"), "INVALID claim race: original publisher did not block on exact publication monitor.");
					PhantomAssertions.assertTrue(player.isTeleporting() && token.get(player) == null && timer.get(player) == null, "INVALID claim race: original super state/token gap not established.");
					publish(player, service, profileId, player::onTeleported);
					confirmed = !player.isTeleporting() && player.isSpawned() && player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY();
					invalidated = token.get(player) == null;
				}
			}
			else
			{
				holder.start(); PhantomAssertions.assertTrue(held.await(2, TimeUnit.SECONDS), "INVALID registration race: original publication monitor gate did not enter.");
				publisher.start();
				await(3000, () -> blockedPublisher(publisher, publicationMonitor, holder.threadId(), Player.class.getName(), "publishNativeTeleportWatchdog"), "INVALID registration race: original publisher did not reach token claim lock.");
				synchronized (ownerMonitor)
				{
					release.countDown();
					await(3000, () -> blockedPublisher(publisher, ownerMonitor, Thread.currentThread().threadId(), PhantomNativeWorkScope.class.getName(), "registerTimer"), "INVALID registration race: actual ambient constructor did not block at exact owner registerTimer monitor.");
					synchronized (publicationMonitor)
					{
						PhantomAssertions.assertTrue(player.isTeleporting() && token.get(player) != null && timer.get(player) == null && future.get(player) == null, "INVALID registration race: installed token/pre-assignment timer gap missing.");
					}
					publish(player, service, profileId, stopTasks ? player::stopAllTasks : player::onTeleported);
					synchronized (publicationMonitor) { invalidated = token.get(player) == null; }
					if (stopTasks) { publish(player, service, profileId, player::onTeleported); }
					confirmed = !player.isTeleporting() && player.isSpawned() && player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY();
				}
			}
			publisher.join(3000); if (holder.getState() != Thread.State.NEW) { holder.join(3000); }
			PhantomAssertions.assertTrue(!publisher.isAlive() && !holder.isAlive(), "Native watchdog publisher/gate remains active.");
			PhantomAssertions.assertEquals(null, publisherFailure.get(), "Original native watchdog publication failed.");
			PhantomAssertions.assertEquals(null, holderFailure.get(), "TEST native publication monitor gate failed.");
			await(3000, () -> scope.outstanding() == 0, "Native watchdog race retained actual executing work.");
			final var obsolete = (ScheduledFuture<?>) future.get(player);
			final boolean noObsoleteMetadata = token.get(player) == null && timer.get(player) == null && obsolete == null;
			final int obsoleteRegistrations = pendingWatchdogs(scope, ownerMonitor);
			publish(player, service, profileId, () -> player.teleToLocation(destination, false)); moved = true;
			final var second = (ScheduledFuture<?>) future.get(player);
			final boolean nextPublished = player.isTeleporting() && second != null && !second.isDone() && second != obsolete;
			publish(player, service, profileId, player::onTeleported);
			await(3000, () -> !player.isTeleporting() && player.isSpawned() && scope.outstanding() == 0, "Actual next teleport/confirmation did not finish native writers.");
			PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && Math.abs(player.getX() - destination.getX()) <= 1 && Math.abs(player.getY() - destination.getY()) <= 1
				&& player.getLastServerPosition().getX() == player.getX() && player.getLastServerPosition().getY() == player.getY(), "INVALID watchdog race: actual next native position/spawn/lastPosition writer missing.");
			context.record("Q07.nativeWatchdogPublicationRace." + (beforeClaim ? "preclaim" : stopTasks ? "stop" : "confirmation"), "object=" + player.getObjectId() + " originalSetTeleporting=true exactOriginalMonitors=true nativeConfirmed=" + confirmed
				+ " tokenInvalidatedAtAction=" + invalidated + " obsoleteMetadataAbsent=" + noObsoleteMetadata + " obsoleteWatchdogRegistrations=" + obsoleteRegistrations + " nextDistinctProducer=" + nextPublished + " actualNextNativeTeleport=true lastPositionWriter=true");
			PhantomAssertions.assertTrue(confirmed && invalidated && noObsoleteMetadata && obsoleteRegistrations == 0 && nextPublished,
				"Original watchdog confirmation/stop raced token claim or timer assignment and retained/coalesced an obsolete optional producer.");
			PhantomAssertions.assertTrue(second.isCancelled() && token.get(player) == null && timer.get(player) == null && future.get(player) == null && pendingWatchdogs(scope, ownerMonitor) == 0, "Next original confirmation leaked exact watchdog registry or native metadata.");
			PhantomAssertions.assertEquals(null, scope.firstNativeIncident(), "Successful watchdog publication race retained a native failure.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			release.countDown();
			try
			{
				if (publisher.getState() != Thread.State.NEW) { publisher.join(3000); }
				if (holder.getState() != Thread.State.NEW) { holder.join(3000); }
				PhantomAssertions.assertTrue(!publisher.isAlive() && !holder.isAlive(), "Watchdog race disposal retains original publisher or monitor gate.");
				if (scope.open())
				{
					if (player.isTeleporting()) { publish(player, service, profileId, player::onTeleported); }
					publish(player, service, profileId, () -> player.setTeleporting(false));
					if (moved) { publish(player, service, profileId, () -> { player.teleToLocation(original, false); player.onTeleported(); }); }
				}
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
		}
	}

	private static boolean blockedPublisher(Thread publisher, Object lock, long ownerId, String nativeClass, String nativeMethod)
	{
		final ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(publisher.threadId(), 64);
		return info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null && info.getLockOwnerId() == ownerId
			&& info.getLockInfo().getIdentityHashCode() == System.identityHashCode(lock) && java.util.Arrays.stream(info.getStackTrace()).anyMatch(frame -> frame.getClassName().equals(nativeClass) && frame.getMethodName().equals(nativeMethod));
	}

	private static int pendingWatchdogs(PhantomNativeWorkScope scope, Object monitor) throws ReflectiveOperationException
	{
		final Field registrations = PhantomNativeWorkScope.class.getDeclaredField("_pendingTimers"); registrations.setAccessible(true);
		int count = 0;
		synchronized (monitor)
		{
			for (Object registration : ((java.util.Map<?, ?>) registrations.get(scope)).values())
			{
				final Field kind = registration.getClass().getDeclaredField("_kind"); kind.setAccessible(true);
				if ("PLAYER_TELEPORT_WATCHDOG".equals(kind.get(registration))) { count++; }
			}
		}
		return count;
	}

	private static ThreadInfo blockedWatchdog(Player player)
	{
		final var bean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo info : bean.getThreadInfo(bean.getAllThreadIds(), 64))
		{
			if (info == null || info.getThreadState() != Thread.State.BLOCKED || info.getLockInfo() == null
				|| info.getLockOwnerId() != Thread.currentThread().threadId() || info.getLockInfo().getIdentityHashCode() != System.identityHashCode(player)) { continue; }
			final boolean task = java.util.Arrays.stream(info.getStackTrace()).anyMatch(frame -> frame.getClassName().equals(TeleportWatchdogTask.class.getName()) && frame.getMethodName().equals("run"));
			final boolean entry = java.util.Arrays.stream(info.getStackTrace()).anyMatch(frame -> (frame.getClassName().equals(Player.class.getName()) || frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.Creature")) && frame.getMethodName().equals("onTeleported"));
			if (task && entry) { return info; }
		}
		return null;
	}

	private static final class WatchdogCheckpoint
	{
		final CountDownLatch supplierEntered = new CountDownLatch(1), finished = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread thread;
		WatchdogCheckpoint(Player player)
		{
			thread = new Thread(() ->
			{
				try { PlayerNativeWork.checkpoint(player, () -> { supplierEntered.countDown(); player.store(false); return null; }); }
				catch (Throwable thrown) { failure.set(thrown); }
				finally { finished.countDown(); }
			}, "TEST-native-watchdog-checkpoint");
		}
		void start() { thread.start(); }
		void observeAdmission(PhantomNativeWorkScope scope) throws InterruptedException { if (scope != null) { await(2000, () -> finished.getCount() == 0 || !scope.open(), "Watchdog checkpoint never reached exact owner fence."); } }
		void join() throws InterruptedException { if (thread.getState() != Thread.State.NEW) { thread.join(6500); } PhantomAssertions.assertFalse(thread.isAlive(), "Watchdog generic checkpoint remains active."); }
	}

	private static PhantomNativeWorkScope require(PhantomTestContext context, Player player, PhantomMaterializationService service)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Delayed native state probes require guarded TEST.");
		PhantomAssertions.assertTrue(player != null && World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline()
			&& !player.isAlikeDead() && !player.isMoving() && !player.isTeleporting(), "INVALID delayed native state probe: exact live stationary Player required.");
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "Delayed native state probe retained borrowed owner context.");
		if (service == null) { PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "INVALID ordinary control: managed Player supplied."); return null; }
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID delayed native state probe: production owner required.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(scope.isCurrent() && scope.player() == player && scope.open() && scope.outstanding() == 0, "INVALID delayed native state probe: exact quiescent lifetime required.");
		return scope;
	}

	private static void publish(Player player, PhantomMaterializationService service, long profileId, Runnable body)
	{
		if (service == null) { body.run(); return; }
		try (var action = service.tryAcquireAction(profileId).orElseThrow()) { PhantomAssertions.assertTrue(action.player() == player, "Delayed native state probe changed exact Player."); body.run(); }
	}

	private static boolean readBoolean(Field field, Object owner) { try { return field.getBoolean(owner); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); } }
	private static Object futureFieldValue(Field field, Object owner) { try { return field.get(owner); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); } }
	private static void await(long milliseconds, BooleanSupplier predicate, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
		while (!predicate.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(predicate.getAsBoolean(), message);
	}

	private static final class Checkpoint
	{
		final CountDownLatch finished = new CountDownLatch(1); final AtomicReference<Throwable> failure = new AtomicReference<>(); final Thread thread;
		Checkpoint(Player player) { thread = new Thread(() -> { try { player.store(false); } catch (Throwable thrown) { failure.set(thrown); } finally { finished.countDown(); } }, "TEST-native-delayed-state-checkpoint"); }
		void start() { thread.start(); }
		void observeAdmission(PhantomNativeWorkScope scope) throws InterruptedException { if (scope != null) { await(2000, () -> finished.getCount() == 0 || !scope.open(), "Native checkpoint did not reach owner fence."); } }
		void join() throws InterruptedException { if (thread.getState() != Thread.State.NEW) { thread.join(6500); } PhantomAssertions.assertFalse(thread.isAlive(), "Delayed native state checkpoint remains active."); }
	}

	private static final class IconGate
	{
		final Player player; final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); final AtomicBoolean first = new AtomicBoolean(), owned = new AtomicBoolean(); final AtomicReference<Throwable> failure = new AtomicReference<>();
		IconGate(Player value) { player = value; }
		void enter()
		{
			if (!first.compareAndSet(false, true)) { return; }
			owned.set(player.getNativeWorkOwner() != null && PlayerNativeWork.current(player.getNativeWorkOwner()) != null); entered.countDown();
			try { if (!release.await(4, TimeUnit.SECONDS)) { failure.set(new IllegalStateException("Icons300 native getter gate expired.")); } }
			catch (InterruptedException thrown) { Thread.currentThread().interrupt(); failure.set(thrown); }
		}
	}

	/** Existing real scheduled worker pre-entry gate, no executor replacement. */
	private static final class WorkerGate implements AutoCloseable
	{
		final int count = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE; final CountDownLatch started, released = new CountDownLatch(1);
		final List<ScheduledFuture<?>> workers = new ArrayList<>(); final AtomicReference<String> failure = new AtomicReference<>();
		WorkerGate() { PhantomAssertions.assertTrue(count > 0 && count <= 128, "INVALID delayed native gate: pool outside existing1..128 bound."); started = new CountDownLatch(count); }
		void acquire() throws Exception
		{
			for (int i = 0; i < count; i++)
			{
				final var future = ThreadPool.schedule(() ->
				{
					started.countDown();
					try { if (!released.await(10, TimeUnit.SECONDS)) { failure.compareAndSet(null, "Native delayed worker hold expired."); } }
					catch (InterruptedException thrown) { Thread.currentThread().interrupt(); failure.compareAndSet(null, "Native delayed worker hold interrupted."); }
				}, 0);
				PhantomAssertions.assertTrue(future != null, "INVALID delayed native gate: worker rejected."); workers.add(future);
			}
			PhantomAssertions.assertTrue(started.await(5, TimeUnit.SECONDS), "INVALID delayed native gate: workers did not enter.");
		}
		void release() { released.countDown(); }
		@Override public void close() throws Exception
		{
			release();
			try { for (var future : workers) { future.get(5, TimeUnit.SECONDS); } PhantomAssertions.assertEquals(null, failure.get(), "Delayed native worker gate failed."); }
			finally { workers.forEach(future -> future.cancel(false)); }
		}
	}
}
