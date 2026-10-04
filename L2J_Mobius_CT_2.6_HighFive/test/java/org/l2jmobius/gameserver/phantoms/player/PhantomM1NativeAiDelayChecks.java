/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.CreatureAI;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.ai.PlayerAI;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureSkillUse;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Observe original scheduled tasks and native AI super bodies; never manufacture an AI notification. */
public final class PhantomM1NativeAiDelayChecks
{
	private static final ThreadLocal<Probe> PUBLISHING = new ThreadLocal<>(), EXECUTING = new ThreadLocal<>();
	private PhantomM1NativeAiDelayChecks() { }

	/** Caller equips native stock13/17 and supplies a nearby loaded destination before canonical materialization. */
	public static void bowReady(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Creature target, Location destination) throws Exception
	{
		requireBow(player, target);
		final var geo = GeoEngine.getInstance();
		final double distance = destination == null ? 0 : Math.hypot(destination.getX() - player.getX(), destination.getY() - player.getY());
		PhantomAssertions.assertTrue(destination != null && destination.getInstanceId() == player.getInstanceId() && distance >= 128 && distance <= 320
			&& distance / player.getStat().getMoveSpeed() < 2 && geo.hasGeo(destination.getX(), destination.getY())
			&& geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), destination.getX(), destination.getY(), destination.getZ(), player.getInstanceId()),
			"INVALID bow READY: actual nearby loaded native MOVE_TO destination required.");
		final int total = player.calculateTimeBetweenAttacks() + player.calculateReuseTime(player.getActiveWeaponItem());
		PhantomAssertions.assertTrue(total > 2400 && total < 10000, "INVALID bow READY: natural stock attack/reuse interval must leave more than1000ms after attack end.");
		probe(context, player, service, profileId, target, null, Kind.READY, destination, total);
	}

	/** Original doAttack supplies factual bow cooldown; original CastTask later casts the exact known1177. */
	public static void bowCast(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Creature target, Skill skill) throws Exception
	{
		requireBow(player, target); requireSkill(player, target, skill);
		probe(context, player, service, profileId, target, skill, Kind.CAST, null, 0);
	}

	/** Caller supplies a stationary native target on a loaded direct corridor, under the stock3000 follow limit. */
	public static void longMove(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Creature target, Skill skill) throws Exception
	{
		requireSkill(player, target, skill);
		final double distance = Math.hypot(target.getX() - player.getX(), target.getY() - player.getY());
		final double approach = (distance - player.getMagicalAttackRange(skill)) / player.getStat().getMoveSpeed();
		final var geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(distance < 2500 && approach >= 5 && approach <= 8 && !target.isMoving()
			&& geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId()),
			"INVALID ARRIVED_REVALIDATE: actual stationary target and native5..8sec loaded direct cast approach required.");
		probe(context, player, service, profileId, target, skill, Kind.ARRIVED, null, 0);
	}

	private static void probe(PhantomTestContext context, Player player, PhantomMaterializationService service, long profileId, Creature target, Skill skill, Kind kind, Location destination, int attackTotal) throws Exception
	{
		final var scope = require(context, player, service);
		PhantomAssertions.assertTrue(target != player && !target.isAlikeDead() && !target.isInvul() && target.isSpawned()
			&& target.getInstanceId() == player.getInstanceId() && player.isInSurroundingRegion(target)
			&& GeoEngine.getInstance().hasGeo(target.getX(), target.getY()), "INVALID delayed AI: exact live vulnerable loaded native target required.");
		final var selected = new Probe(player, target, skill, kind);
		final var ai = new HeldAI(player, selected);
		final CreatureAI previous = player.getAI();
		final var checkpoint = new Checkpoint(player);
		final var damage = new CountDownLatch(1);
		final var damageStack = new AtomicBoolean();
		final var damageWrites = new AtomicInteger();
		final var nativeCastEntry = new AtomicBoolean();
		final double hpCp = target.getCurrentHp() + target.getCurrentCp();
		final var listener = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if (skill != null && event.getAttacker() == player && event.getTarget() == target && event.getSkill() == skill && !event.isDamageOverTime())
			{
				damageStack.set(nativeFrame("org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask", "run"));
				if (event.getDamage() > 0 && hpCp > target.getCurrentHp() + target.getCurrentCp()) { damageWrites.incrementAndGet(); damage.countDown(); }
			}
		}, damage);
		final var castListener = new ConsumerEventListener(player, EventType.ON_CREATURE_SKILL_USE, (OnCreatureSkillUse event) ->
		{
			if (event.getCaster() == player && event.getSkill() == skill && event.getTarget() == target
				&& nativeFrame(Creature.class.getName(), "beginCastNative")) { nativeCastEntry.set(true); }
		}, nativeCastEntry);
		Throwable primary = null;
		boolean quiesced = false, terminalCleanup = false;
		player.addListener(listener);
		player.addListener(castListener);
		try
		{
			player.setAI(ai);
			try (var observer = new ObservationPool(selected))
			{
				observer.install();
				publish(player, service, profileId, () ->
				{
					player.setTarget(target);
					if (kind == Kind.ARRIVED)
					{
						ai.setIntention(Intention.CAST, skill, target);
						PhantomAssertions.assertTrue(ai.getIntention() == Intention.CAST && ai.isFollowing(), "INVALID long cast: original native approach did not start follow.");
					}
					else
					{
						player.doAttack(target);
						PhantomAssertions.assertTrue(player.isAttackingNow() && player.isRangeAttackingNow(), "INVALID bow: original stock attack did not establish actual cooldown.");
						if (kind == Kind.READY)
						{
							ai.setIntention(Intention.MOVE_TO, destination);
							PhantomAssertions.assertTrue(ai.getNextIntention() != null && !player.isMoving(), "INVALID READY: original PlayerAI did not save actual MOVE_TO during attack.");
						}
						else { ai.setIntention(Intention.CAST, skill, target); }
					}
				});
				if (kind == Kind.READY)
				{
					await(6000, () -> !player.isAttackingNow(), "INVALID READY: original bow attack did not end naturally.");
					final int remaining = (player.getBowAttackEndTime() - GameTimeTaskManager.getInstance().getGameTicks()) * GameTimeTaskManager.MILLIS_IN_TICK;
					PhantomAssertions.assertTrue(remaining > 1200 && ai.getNextIntention() != null, "INVALID READY: original cooldown no longer leaves enough time for raw1000 callback.");
					publish(player, service, profileId, () -> player.doAttack(target));
				}
				PhantomAssertions.assertTrue(selected.published.await(3, TimeUnit.SECONDS), "INVALID delayed AI: exact original scheduled publisher was not observed.");
			}
			if (kind == Kind.ARRIVED)
			{
				final Object originalMove = selected.moveData();
				publish(player, service, profileId, ai::stopFollow);
				PhantomAssertions.assertTrue(originalMove != null && selected.moveData() == originalMove && player.isMoving()
					&& !ai.isFollowing() && ai.getIntention() == Intention.CAST,
					"INVALID ARRIVED: native stopFollow fixture seam changed actual long movement or cast intention.");
				selected.nativeFollowStopped.set(true);
			}
			PhantomAssertions.assertTrue(selected.entered.await(10, TimeUnit.SECONDS), "INVALID delayed AI: original native task did not enter selected PlayerAI body.");
			PhantomAssertions.assertTrue(selected.nativeStack.get(), "INVALID delayed AI: original NotifyAITask/CastTask.run frame missing.");
			final int running = scope == null ? 0 : scope.outstanding();
			checkpoint.start(); checkpoint.observe(scope);
			final boolean crossed = checkpoint.supplierEntered.await(150, TimeUnit.MILLISECONDS);
			selected.release.countDown();
			selected.future.get().get(8, TimeUnit.SECONDS);
			checkpoint.join();
			PhantomAssertions.assertEquals(null, selected.failure.get(), "Original delayed native AI body failed.");
			context.record("Q12.nativeAiDelay." + kind + "." + (scope != null) + ".beforeDamage",
				"exactBodyOwned=" + selected.owned.get() + " scopeBeforeWriter=" + selected.beforeWriterScope.get() + " scopeAfterBody=" + (scope == null ? "ORDINARY" : scope.snapshot())
				+ " nativeThinkCastAttempt=" + ai.thinkCastAttempt.get() + " originalBeginCastNative=" + nativeCastEntry.get() + " nativeCastingStarted=" + ai.nativeCast.get()
				+ " targetAliveBeforeWriter=" + selected.targetAlive.get() + " targetHpBeforeWriter=" + selected.targetHp
				+ " stockSkillAndMpReady=" + selected.skillAndMpReady.get() + " targetVisibleBeforeWriter=" + selected.targetVisible.get()
				+ " checkpointCrossed=" + crossed + " nativeFollowStoppedSetup=" + selected.nativeFollowStopped.get()
				+ " originalFollowAdded=" + ai.followAdded.get() + " nativeMoveDataContinuation=" + ai.moveDataContinued.get()
				+ " genericSupplierEntered=" + (checkpoint.supplierEntered.getCount() == 0) + " checkpointFailure=" + checkpoint.failure.get());
			final boolean writer;
			if (kind == Kind.READY)
			{
				writer = ai.nativeMove.get();
				PhantomAssertions.assertTrue(writer, "INVALID raw READY: original saved intention did not publish native MOVE_TO.");
			}
			else
			{
				if (kind == Kind.ARRIVED)
				{
					PhantomAssertions.assertTrue(selected.nativeFollowStopped.get() && ai.followAdded.get(),
						"INVALID ARRIVED_REVALIDATE: original AI super body did not restore actual native follow registration after unchanged long movement.");
					PhantomAssertions.assertTrue(ai.continuedMove.await(4, TimeUnit.SECONDS) && ai.moveDataContinued.get(),
						"INVALID ARRIVED_REVALIDATE: original shared follow continuation did not update actual native MoveData.");
				}
				final boolean hpWriter = damage.await(12, TimeUnit.SECONDS) && damageStack.get();
				context.record("Q12.nativeAiDelay." + kind + "." + (scope != null) + ".afterDamage",
					"stock1177HpWriter=" + hpWriter + " originalBeginCastNative=" + nativeCastEntry.get() + " exactBodyOwned=" + selected.owned.get()
					+ " nativeThinkCastAttempt=" + ai.thinkCastAttempt.get() + " scopeBeforeWriter=" + selected.beforeWriterScope.get());
				if (kind == Kind.CAST && scope != null && !selected.owned.get() && ai.thinkCastAttempt.get() && !nativeCastEntry.get()
					&& selected.targetAlive.get() && selected.skillAndMpReady.get() && selected.targetVisible.get()
					&& (selected.beforeWriterScope.get().contains("state=DRAINING") || selected.beforeWriterScope.get().contains("state=SEALED")) && !hpWriter)
				{
					throw new AssertionError("Original unowned CastTask reached stock thinkCast but lost native cast-frontend descendant admission during checkpoint DRAINING/SEALED.");
				}
				PhantomAssertions.assertTrue(hpWriter, "INVALID delayed cast: actual stock1177 MagicUseTask HP/CP writer was not established.");
				writer = kind == Kind.CAST ? ai.nativeCast.get() && nativeCastEntry.get() : ai.followAdded.get() && ai.moveDataContinued.get();
			}
			context.record("Q12.nativeAiDelay." + kind + "." + (scope != null), "object=" + player.getObjectId() + " epoch=" + (scope == null ? 0 : scope.epoch())
				+ " source=" + selected.source.get() + " originalTask=" + selected.taskClass.get() + " delayMs=" + selected.delayMs
				+ " naturalAttackTotal=" + attackTotal + " aggregateRunning=" + running + " exactBodyOwned=" + selected.owned.get()
				+ " checkpointIncludesInitialBow=" + (kind != Kind.ARRIVED) + " checkpointCrossed=" + crossed + " originalSuperWriter=" + writer + " stock1177HpWriter=" + (skill != null && damage.getCount() == 0)
				+ " nativeFollowRemoved=" + ai.followRemoved.get() + " nativeFollowAdded=" + ai.followAdded.get());
			if (scope != null)
			{
				PhantomAssertions.assertTrue(selected.owned.get(), "Original delayed native AI body has no exact Player lifetime execution before stock writer.");
				PhantomAssertions.assertFalse(crossed, "Native checkpoint crossed selected original delayed AI body before its stock writer.");
			}
			// A stock cast can legitimately outlast the unchanged five-second checkpoint deadline.
			// The marker never requests STORE/PREPARE, and an empty receipt cannot authorize generic reopen.
			await(12000, () -> !player.isAttackingNow() && !player.isCastingNow() && (scope == null || scope.outstanding() == 0), "Original delayed AI descendants did not quiesce after their actual writers.");
			if (skill != null) { PhantomAssertions.assertEquals(1, damageWrites.get(), "Original stock1177 HP writer did not complete exactly once."); }
			if (scope != null && kind == Kind.CAST && checkpoint.failure.get() instanceof PhantomNativeWorkScope.DrainTimeoutException timeout)
			{
				final var retained = service.find(profileId).orElseThrow();
				PhantomAssertions.assertTrue(checkpoint.supplierEntered.getCount() == 1 && !checkpoint.pendingAtFailure.get() && !player.hasPendingOwnedStore()
					&& timeout.getMessage().contains("cast-launch:RESERVED") && checkpoint.scopeAtFailure.get().contains("state=DRAINING"),
					"CAST deferred outcome lacks an actual earned cast-launch timeout before supplier/PREPARE.");
				PhantomAssertions.assertTrue(player.getNativeWorkOwner() == scope && scope.player() == player && scope.isCurrent() && !scope.open()
					&& scope.epoch() == retained.materializedAtNanos() && retained.characterObjectId() == player.getObjectId()
					&& retained.playerRetained() && retained.identityLeaseRetained() && retained.worldPresent()
					&& World.getInstance().getPlayer(player.getObjectId()) == player && scope.firstNativeIncident() == null && retained.firstCleanupIncident() == null,
					"CAST deferred checkpoint changed its exact retained Player/owner/epoch or created an incident.");
				context.record("Q12.nativeAiDelay.CAST.true.checkpoint", "DEFERRED_DRAIN_TIMEOUT_BEFORE_SUPPLIER epoch=" + scope.epoch()
					+ " beforePrepare=true pendingReceipt=false genericReopen=false actual1177Writes=" + damageWrites.get() + " scopeAtTimeout=" + checkpoint.scopeAtFailure.get());
				// Existing cleanup is the terminal continuation on this exact lifetime; it calls real Player.storeMe.
				// No fresh generic checkpoint or pending-receipt resume is permitted here.
				final var cleanup = service.retryCleanup(profileId);
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, cleanup.status(), "Completed stock cast could not reach same-lifetime terminal native store.");
				terminalCleanup = true;
				PhantomAssertions.assertTrue(service.find(profileId).isEmpty() && World.getInstance().getPlayer(player.getObjectId()) == null
					&& player.getNativeWorkOwner() == null && scope.outstanding() == 0 && scope.pendingTimers() == 0 && scope.firstNativeIncident() == null,
					"CAST terminal native store retained ownership/accounting or lost its clean outcome.");
				PhantomAssertions.assertEquals(1, damageWrites.get(), "Terminal cleanup replayed the actual stock1177 writer.");
				context.record("Q12.nativeAiDelay.CAST.true.continuation", "TERMINAL_CLEANUP_SAME_LIFETIME epoch=" + scope.epoch()
					+ " status=" + cleanup.status() + " actual1177Writes=" + damageWrites.get() + " outstanding=0 pendingTimers=0 genericReopen=false");
			}
			else
			{
				PhantomAssertions.assertEquals(null, checkpoint.failure.get(), "Delayed AI generic checkpoint failed.");
				PhantomAssertions.assertEquals(0L, checkpoint.supplierEntered.getCount(), "Generic checkpoint never reached its genuine supplier marker.");
				quiesce(player, ai, service, profileId, scope);
				quiesced = true;
				PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "Native STORE cannot borrow ActionLease execution.");
				player.store(false); // Stock native STORE after the exact ActionLease above has closed.
				if (scope != null)
				{
					PhantomAssertions.assertTrue(player.getNativeWorkOwner() == scope && scope.player() == player && scope.isCurrent() && scope.open()
						&& service.find(profileId).orElseThrow().materializedAtNanos() == scope.epoch() && !player.hasPendingOwnedStore() && scope.firstNativeIncident() == null,
						"Quiescent active native STORE changed exact lifetime or retained a failure/receipt.");
				}
				context.record("Q12.nativeAiDelay." + kind + "." + (scope != null) + ".checkpoint", "GENERIC_MARKER_THEN_QUIESCENT_NATIVE_STORE epoch="
					+ (scope == null ? 0 : scope.epoch()) + " supplierEntered=true outsideActionLease=true sameLifetime=true");
			}
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			selected.release.countDown();
			try
			{
				final var future = selected.future.get();
				if (future != null) { future.get(12, TimeUnit.SECONDS); }
				checkpoint.join();
				if (!terminalCleanup && (!quiesced || (scope != null && !scope.open())))
				{
					if (scope == null || scope.open()) { quiesce(player, ai, service, profileId, scope); }
					else
					{
						final var cleanup = service.retryCleanup(profileId); // Preserve a fenced lifetime; never manufacture active admission.
						PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, cleanup.status(), "Fenced delayed AI TEST cleanup retained its exact lifetime.");
					}
				}
				player.setAI(previous);
			}
			catch (Exception | Error cleanup) { if (primary == null) { throw cleanup; } primary.addSuppressed(cleanup); }
			finally { player.removeListener(listener); player.removeListener(castListener); }
		}
	}

	private static void quiesce(Player player, HeldAI ai, PhantomMaterializationService service, long profileId, PhantomNativeWorkScope scope) throws Exception
	{
		await(12000, () -> !player.isAttackingNow() && !player.isCastingNow() && (scope == null || scope.outstanding() == 0), "Delayed AI cleanup retained actual native work.");
		publish(player, service, profileId, () ->
		{
			ai.stopFollow(); player.stopMove(null); ai.setIntention(Intention.IDLE);
			ai.clientStopAutoAttack();
			org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(player);
			ai.setAutoAttacking(false); // Same original public combat cleanup as Q10 and native materialization teardown.
		});
		await(12000, () -> !player.isMoving() && !ai.isFollowing() && ai.getIntention() == Intention.IDLE && !player.isInCombat()
			&& !player.isAttackingNow() && !player.isCastingNow() && (scope == null || scope.outstanding() == 0), "Native public cleanup did not reach a supported quiescent STORE context.");
	}

	private static void requireBow(Player player, Creature target)
	{
		final var bow = player.getActiveWeaponItem();
		final var arrows = player.getInventory().getItemByItemId(17);
		PhantomAssertions.assertTrue(bow != null && bow.getId() == 13 && arrows != null && arrows.getCount() >= 2
			&& player.getCurrentMp() > 10 && player.isInsideRadius3D(target, 450), "INVALID bow: actual stock13/17, MP and nearby native target required.");
	}

	private static void requireSkill(Player player, Creature target, Skill skill)
	{
		PhantomAssertions.assertTrue(skill != null && skill.getId() == 1177 && player.getKnownSkill(1177) == skill
			&& GeoEngine.getInstance().canSeeTarget(player, target), "INVALID delayed cast: exact known stock1177 and actual native target visibility required.");
	}

	private static PhantomNativeWorkScope require(PhantomTestContext context, Player player, PhantomMaterializationService service)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native delayed AI requires guarded TEST.");
		PhantomAssertions.assertEquals(null, PlayerNativeWork.inheritedPlayer(), "Native delayed AI caller cannot borrow native execution.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && player.isOnline() && player.isSpawned()
			&& !player.isAlikeDead() && !player.isMoving() && !player.isMovementDisabled() && !player.isAttackingNow() && !player.isCastingNow()
			&& GeoEngine.getInstance().hasGeo(player.getX(), player.getY()), "INVALID delayed AI: fresh actual movable loaded World Player required.");
		if (service == null) { PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "INVALID ordinary delayed AI: managed Player supplied."); return null; }
		PhantomAssertions.assertTrue(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID delayed AI: production native owner required.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		PhantomAssertions.assertTrue(scope.player() == player && scope.isCurrent() && scope.open() && scope.outstanding() == 0 && scope.firstNativeIncident() == null,
			"INVALID delayed AI: exact fresh current production lifetime required.");
		return scope;
	}

	private static void publish(Player player, PhantomMaterializationService service, long profileId, Runnable body)
	{
		if (service == null) { body.run(); return; }
		try (var action = service.tryAcquireAction(profileId).orElseThrow())
		{
			PhantomAssertions.assertTrue(action.player() == player, "Delayed AI ActionLease changed exact Player."); body.run();
		}
	}

	private static boolean nativeFrame(String type, String method)
	{
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(type) && frame.getMethodName().equals(method)));
	}

	private static void await(int millis, BooleanSupplier condition, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private enum Kind { READY, CAST, ARRIVED }

	private static final class Probe
	{
		final Player player; final Creature target; final Skill skill; final Kind kind; final Field move;
		final CountDownLatch published = new CountDownLatch(1), entered = new CountDownLatch(1), release = new CountDownLatch(1);
		final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
		final AtomicReference<String> source = new AtomicReference<>(), taskClass = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final AtomicReference<String> beforeWriterScope = new AtomicReference<>("NOT_REACHED");
		final AtomicBoolean selected = new AtomicBoolean(), nativeStack = new AtomicBoolean(), owned = new AtomicBoolean();
		final AtomicBoolean nativeFollowStopped = new AtomicBoolean(), targetAlive = new AtomicBoolean(), skillAndMpReady = new AtomicBoolean(), targetVisible = new AtomicBoolean();
		volatile long delayMs;
		volatile double targetHp;
		Probe(Player player, Creature target, Skill skill, Kind kind) throws Exception
		{
			this.player = player; this.target = target; this.skill = skill; this.kind = kind;
			move = Creature.class.getDeclaredField("_move"); move.setAccessible(true);
		}
		Object moveData()
		{
			try { return move.get(player); } catch (IllegalAccessException failure) { throw new IllegalStateException("TEST native movement observation failed.", failure); }
		}
		void enter()
		{
			nativeStack.set(nativeFrame(kind == Kind.CAST ? CreatureAI.class.getName() + "$CastTask" : "org.l2jmobius.gameserver.model.actor.tasks.creature.NotifyAITask", "run"));
			owned.set(player.getNativeWorkOwner() != null && PlayerNativeWork.current(player.getNativeWorkOwner()) != null);
			entered.countDown();
			try { if (!release.await(4, TimeUnit.SECONDS)) { throw new IllegalStateException("TEST original delayed AI entry gate expired."); } }
			catch (InterruptedException thrown) { Thread.currentThread().interrupt(); failure.set(thrown); throw new IllegalStateException(thrown); }
			beforeWriterScope.set(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope scope ? scope.snapshot() : "ORDINARY");
			targetHp = target.getCurrentHp(); targetAlive.set(!target.isAlikeDead() && !target.isInvul());
			targetVisible.set(GeoEngine.getInstance().canSeeTarget(player, target));
			skillAndMpReady.set(skill == null || player.getKnownSkill(skill.getId()) == skill
				&& player.getCurrentMp() >= player.getStat().getMpConsume(skill) + player.getStat().getMpInitialConsume(skill));
		}
	}

	private static final class HeldAI extends PlayerAI
	{
		final Player player; final Probe probe;
		final AtomicBoolean nativeMove = new AtomicBoolean(), nativeCast = new AtomicBoolean(), followRemoved = new AtomicBoolean(), followAdded = new AtomicBoolean();
		final AtomicBoolean thinkCastAttempt = new AtomicBoolean(), moveDataContinued = new AtomicBoolean();
		final CountDownLatch continuedMove = new CountDownLatch(1);
		HeldAI(Player player, Probe probe) { super(player); this.player = player; this.probe = probe; }
		@Override protected void onActionReadyToAct()
		{
			if (EXECUTING.get() != probe || probe.kind != Kind.READY) { super.onActionReadyToAct(); return; }
			PhantomAssertions.assertTrue(getNextIntention() != null && !player.isAttackingNow(), "INVALID raw READY: factual saved native intention missing at entry.");
			probe.enter(); super.onActionReadyToAct(); nativeMove.set(player.isMoving() && getIntention() == Intention.MOVE_TO && getNextIntention() == null);
		}
		@Override protected void changeIntentionToCast(Skill skill, WorldObject target)
		{
			if (EXECUTING.get() != probe || probe.kind != Kind.CAST) { super.changeIntentionToCast(skill, target); return; }
			probe.enter(); super.changeIntentionToCast(skill, target); nativeCast.set(player.isCastingNow() && getIntention() == Intention.CAST);
		}
		@Override protected void onActionArrivedRevalidate()
		{
			if (EXECUTING.get() != probe || probe.kind != Kind.ARRIVED) { super.onActionArrivedRevalidate(); return; }
			PhantomAssertions.assertTrue(player.isMoving() && !isFollowing() && getIntention() == Intention.CAST && probe.nativeFollowStopped.get(),
				"INVALID raw ARRIVED: factual continued long move with native follow stopped missing at entry.");
			probe.enter(); super.onActionArrivedRevalidate();
		}
		@Override public void clientStopMoving(Location location)
		{
			if (EXECUTING.get() == probe && probe.kind == Kind.CAST && nativeFrame(PlayerAI.class.getName(), "thinkCast")) { thinkCastAttempt.set(true); }
			super.clientStopMoving(location);
		}
		@Override public void stopFollow()
		{
			final boolean selected = EXECUTING.get() == probe && probe.kind == Kind.ARRIVED && isFollowing();
			super.stopFollow();
			if (selected && !isFollowing() && getFollowTarget() == null) { followRemoved.set(true); }
		}
		@Override public void startFollow(Creature target, int range)
		{
			super.startFollow(target, range);
			if (EXECUTING.get() == probe && probe.kind == Kind.ARRIVED && isFollowing() && getFollowTarget() == target) { followAdded.set(true); }
		}
		@Override public void moveToPawn(WorldObject target, int offset)
		{
			if (probe.kind != Kind.ARRIVED) { super.moveToPawn(target, offset); return; }
			final Object before = probe.moveData();
			final boolean continuation = followAdded.get() && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
				frame.getClassName().startsWith("org.l2jmobius.gameserver.taskmanagers.CreatureFollowTaskManager$") && frame.getMethodName().equals("run")));
			PUBLISHING.set(probe);
			try
			{
				super.moveToPawn(target, offset);
				if (continuation && probe.moveData() != null && probe.moveData() != before) { moveDataContinued.set(true); continuedMove.countDown(); }
			}
			finally { PUBLISHING.remove(); }
		}
	}

	/** Forward original Runnable/delay to the original pool; marker selects a body but creates no ownership. */
	private static final class ObservationPool extends ScheduledThreadPoolExecutor implements AutoCloseable
	{
		final Probe probe; final Field field; final ScheduledThreadPoolExecutor original;
		final Thread caller = Thread.currentThread();
		ObservationPool(Probe probe) throws Exception
		{
			super(1, body -> { final var thread = new Thread(body, "TEST-original-AI-observer-unused"); thread.setDaemon(true); return thread; });
			this.probe = probe; field = ThreadPool.class.getDeclaredField("SCHEDULED_POOL"); field.setAccessible(true);
			original = (ScheduledThreadPoolExecutor) field.get(null);
		}
		void install() throws Exception { PhantomAssertions.assertTrue(field.get(null) == original, "TEST native pool changed before observation."); field.set(null, this); }
		@Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit)
		{
			final boolean exactThread = probe.kind == Kind.ARRIVED ? PUBLISHING.get() == probe : Thread.currentThread() == caller;
			final String sourceType = probe.kind == Kind.CAST ? CreatureAI.class.getName() : Creature.class.getName();
			final String method = probe.kind == Kind.CAST ? "onIntentionCast" : probe.kind == Kind.READY ? "canUseRangeWeapon" : "moveToLocation";
			final var source = exactThread ? StackWalker.getInstance().walk(frames -> frames.filter(frame -> frame.getClassName().equals(sourceType) && frame.getMethodName().equals(method)).findFirst().orElse(null)) : null;
			if (source == null || !probe.selected.compareAndSet(false, true)) { return original.schedule(task, delay, unit); }
			probe.source.set(source.toString()); probe.delayMs = unit.toMillis(delay); probe.taskClass.set(unwrap(task).getClass().getName());
			final var future = original.schedule(() ->
			{
				EXECUTING.set(probe);
				try { task.run(); } catch (RuntimeException | Error failure) { probe.failure.compareAndSet(null, failure); throw failure; }
				finally { EXECUTING.remove(); }
			}, delay, unit);
			probe.future.set(future); probe.published.countDown(); return future;
		}
		private Runnable unwrap(Runnable task)
		{
			Class<?> type = task.getClass();
			while (type != null && !type.getName().equals(ThreadPool.class.getName() + "$RunnableWrapper")) { type = type.getSuperclass(); }
			if (type == null) { return task; }
			try { final var wrapped = type.getDeclaredField("_wrappedRunnable"); wrapped.setAccessible(true); return (Runnable) wrapped.get(task); }
			catch (ReflectiveOperationException failure) { throw new IllegalStateException("TEST original native task observation failed.", failure); }
		}
		@Override public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) { return original.schedule(task, delay, unit); }
		@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit) { return original.scheduleAtFixedRate(task, delay, period, unit); }
		@Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, long period, TimeUnit unit) { return original.scheduleWithFixedDelay(task, delay, period, unit); }
		@Override public void close()
		{
			try
			{
				PhantomAssertions.assertTrue(field.get(null) == this, "TEST original native pool changed during observation.");
				field.set(null, original); shutdownNow();
				if (!awaitTermination(3, TimeUnit.SECONDS)) { throw new IllegalStateException("TEST unused observer pool retained work."); }
			}
			catch (ReflectiveOperationException failure) { throw new IllegalStateException("TEST original native pool restoration failed.", failure); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
		}
	}

	private static final class Checkpoint
	{
		final CountDownLatch supplierEntered = new CountDownLatch(1), finished = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final AtomicReference<String> scopeAtFailure = new AtomicReference<>("NONE");
		final AtomicBoolean pendingAtFailure = new AtomicBoolean();
		final Thread thread;
		Checkpoint(Player player)
		{
			thread = new Thread(() ->
			{
				try { PlayerNativeWork.checkpoint(player, () -> { supplierEntered.countDown(); return null; }); }
				catch (Throwable thrown)
				{
					pendingAtFailure.set(player.hasPendingOwnedStore());
					scopeAtFailure.set(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope scope ? scope.snapshot() : "ORDINARY");
					failure.set(thrown);
				}
				finally { finished.countDown(); }
			}, "TEST-native-AI-checkpoint");
		}
		void start() { thread.start(); }
		void observe(PhantomNativeWorkScope scope) throws InterruptedException { if (scope != null) { await(2000, () -> finished.getCount() == 0 || !scope.open(), "Native AI checkpoint never reached exact admission fence."); } }
		void join() throws InterruptedException { if (thread.getState() != Thread.State.NEW) { thread.join(6000); } PhantomAssertions.assertFalse(thread.isAlive(), "Native AI generic checkpoint retained its active thread."); }
	}
}
