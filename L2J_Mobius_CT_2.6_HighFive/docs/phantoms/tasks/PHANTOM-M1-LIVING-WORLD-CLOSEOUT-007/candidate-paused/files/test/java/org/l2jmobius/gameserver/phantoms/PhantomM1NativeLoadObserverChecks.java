/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureAttackAvoid;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Original bow writer and lawful STORE control; the caller retains exact native fixture cleanup. */
public final class PhantomM1NativeLoadObserverChecks
{
	private PhantomM1NativeLoadObserverChecks() { }

	/** Seed bow13 and equipped arrow17 count8 before baseline/load; do not start visible AutoPlay. */
	public static void run(PhantomTestContext context, long profileId, PhantomMaterializationService materialization, PhantomM1NativeLoadObserver observer, Monster target) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Native-load control requires guarded headless TEST.");
		final var entry = materialization.find(profileId).orElseThrow();
		final Player player = World.getInstance().getPlayer(entry.characterObjectId());
		PhantomAssertions.assertTrue(player != null && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "INVALID load control: exact managed native Player is absent.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		final long epoch = scope.epoch();
		PhantomAssertions.assertTrue(!player.isAutoPlaying() && !player.isInParty() && !player.hasSummon() && !player.isAlikeDead() && !player.isAttackingNow() && !player.isCastingNow(), "INVALID load control: fresh idle native fixture required.");
		PhantomAssertions.assertTrue(player.hasOwnedStoreBoundary() && player.getStat().getVitalityMultiplier() == 1 && player.getNevitHourglassMultiplier() == 1, "INVALID load control: original lawful owned STORE context required; vitality/nevit guards are unchanged.");
		final var bow = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		final var arrows = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		PhantomAssertions.assertTrue(bow != null && bow.getId() == 13 && arrows != null && arrows.getId() == 17 && arrows.getCount() == 8, "INVALID load control: original loaded Short Bow13 and equipped Wooden Arrow17 count8 required.");
		PhantomAssertions.assertTrue(target != null && target.getId() == 20534 && !target.isDead() && !target.isInvul() && target.getInstanceId() == player.getInstanceId() && player.isInSurroundingRegion(target) && player.calculateDistance3D(target) < 500, "INVALID load control: fresh actual NPC20534 within native bow range required.");
		final var initial = sample(player, scope, epoch);
		final Progress loaded = observer.assertRestored(context, "loadControl.initial", profileId, player, initial);
		PhantomAssertions.assertTrue(initial.expGained() == 0 && initial.spGained() == 0, "INVALID load control: gameplay preceded the frozen load witness.");
		final var loadedRows = PlayerNativeWork.checkpoint(player, () -> nativeRows(player.getObjectId()));
		PhantomAssertions.assertEquals(liveRows(player), loadedRows, "INVALID load control: initial full native inventory/equipment differs from loaded items.");
		final long loadedCount = count(loadedRows, arrows.getObjectId());
		PhantomAssertions.assertEquals(8L, loadedCount, "INVALID load control: native DB arrow baseline differs from the loaded stack.");
		context.record("loadControl.fixture", "INVALID_UNTIL_ORIGINAL_HIT_REWARD_STORE");
		final var callbacks = new AtomicInteger();
		final var successfulDamage = new AtomicInteger();
		final var taskStack = new AtomicReference<String>();
		final var callbackFailure = new AtomicReference<String>();
		final var dealt = new ConsumerEventListener(player, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) ->
		{
			if (event.getAttacker() != player || event.getTarget() != target || event.getSkill() != null || event.isDamageOverTime()) { return; }
			observeHit(callbackFailure, taskStack);
			if (event.getDamage() > 0) { successfulDamage.incrementAndGet(); }
			callbacks.incrementAndGet();
		}, callbacks);
		final var avoided = new ConsumerEventListener(target, EventType.ON_CREATURE_ATTACK_AVOID, (OnCreatureAttackAvoid event) ->
		{
			if (event.getAttacker() != player || event.getTarget() != target || event.isDamageOverTime()) { return; }
			observeHit(callbackFailure, taskStack); callbacks.incrementAndGet();
		}, callbacks);
		player.addListener(dealt); target.addListener(avoided);
		Throwable primary = null;
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "INVALID load control: setup lease changed native identity.");
				// Reuse the focused SharedRecipients HP1 premise; stock damage, kill and rewards remain original.
				target.setCurrentHp(1); player.setTarget(target);
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			long nextShot = 0;
			int shots = 0;
			while (!target.isDead() && shots < 7)
			{
				final long ready = nextShot;
				await(() -> System.nanoTime() >= ready && !player.isAttackingNow(), deadline, "Original bow readiness exceeded the bounded TEST window.");
				final int beforeCallbacks = callbacks.get();
				final long beforeCount = arrows.getCount();
				final int nativeReuseMillis = player.calculateTimeBetweenAttacks() + player.calculateReuseTime(player.getActiveWeaponItem()) + 200;
				PhantomAssertions.assertTrue(nativeReuseMillis > 0 && nativeReuseMillis <= 6000, "INVALID load control: original bow reuse exceeds the bounded fixture budget.");
				if (shots == 0)
				{
					try (var gate = new ScheduledWorkerGate())
					{
						gate.acquire(); publish(materialization, profileId, player, target);
						PhantomAssertions.assertTrue(arrows.getCount() > 0 && arrows.getCount() < loadedCount && arrows.getCount() == beforeCount - 1, "INVALID dirty-arrow control: original native bow did not partially consume the stack.");
						final var dirtyDb = nativeRows(player.getObjectId());
						PhantomAssertions.assertEquals(loadedCount, count(dirtyDb, arrows.getObjectId()), "INVALID dirty-arrow control: native DB was already flushed before observation.");
						PhantomAssertions.assertEquals(beforeCallbacks, callbacks.get(), "INVALID dirty-arrow control: HitTask entered before native scheduled-worker release.");
						PhantomAssertions.assertEquals(loaded, observer.assertRestored(context, "loadControl.dirtyArrow", profileId, player, sample(player, scope, epoch)), "Original load witness changed after a real dirty decrement.");
						context.record("loadControl.dirtyArrow", "object=" + arrows.getObjectId() + " loaded=" + loadedCount + " current=" + arrows.getCount() + " nativeDb=" + count(dirtyDb, arrows.getObjectId()) + " stockPartialWriterBeforeHit=true nativeFullHash=" + hash(dirtyDb));
					}
				}
				else { publish(materialization, profileId, player, target); }
				PhantomAssertions.assertEquals(beforeCount - 1, arrows.getCount(), "INVALID load control: original bow frontend rejected a ready shot.");
				nextShot = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(nativeReuseMillis);
				shots++;
				await(() -> callbacks.get() > beforeCallbacks, deadline, "INVALID load control: original HitTask did not produce damage or avoidance.");
				PhantomAssertions.assertEquals(null, callbackFailure.get(), "INVALID load control: native event did not originate in HitTask.run.");
			}
			await(() -> target.isDead() && scope.outstanding() == 0, deadline, "INVALID load control: no completed original native kill/reward within seven stock shots.");
			final var earned = sample(player, scope, epoch);
			PhantomAssertions.assertTrue(successfulDamage.get() > 0 && earned.damageSequence() > initial.damageSequence() && earned.killSequence() > initial.killSequence() && earned.rewardSequence() > initial.rewardSequence() && earned.expGained() > 0 && earned.spGained() > 0, "INVALID load control: actual owned damage/kill/reward EXP/SP evidence is incomplete.");
			assertProgress(player, loaded, earned);
			await(() -> !player.isInCombat(), deadline, "INVALID load control: original combat state did not leave the lawful STORE guard.");
			PhantomAssertions.assertEquals(null, PlayerNativeWork.current(scope), "Lawful STORE must run outside the original ActionLease.");
			// Public native STORE owns its checkpoint; this caller holds no Player/status/actor monitor.
			player.storeMe();
			final var storedEvidence = PlayerNativeWork.checkpoint(player, () ->
			{
				final var observed = sample(player, scope, epoch);
				assertProgress(player, loaded, observed);
				final var canonical = new PhantomBackgroundTransaction().load(profileId);
				PhantomAssertions.assertTrue(canonical.successful() && canonical.state() != null && canonical.state().identity().characterObjectId() == player.getObjectId(), "Lawful STORE canonical projection is missing/wrong.");
				final var state = canonical.state();
				PhantomAssertions.assertEquals(new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath()), state.progress(), "Lawful STORE lost original native progress.");
				final var rows = nativeRows(player.getObjectId());
				PhantomAssertions.assertEquals(liveRows(player), rows, "Lawful STORE full native items/slots/enchant differ from the live inventory.");
				PhantomAssertions.assertEquals(hash(rows), state.inventory().canonicalHash(), "Lawful STORE full native item hash differs from its canonical projection.");
				PhantomAssertions.assertTrue(state.position().instanceId() == player.getInstanceId() && state.position().x() == player.getX() && state.position().y() == player.getY() && state.position().z() == player.getZ() && state.position().heading() == player.getHeading(), "Lawful STORE native pose was not preserved.");
				PhantomAssertions.assertEquals(arrows.getCount(), count(rows, arrows.getObjectId()), "Lawful STORE did not flush the original partial arrow writer.");
				context.record("loadControl.afterStore", "loadedProgress=" + loaded + " currentCanonical=" + state.progress() + " actualLifetimeExp=" + observed.expGained() + " actualLifetimeSp=" + observed.spGained() + " nativeFullHash=" + hash(rows) + " fullSixFieldRows=" + rows + " sameObjectEpoch=" + epoch);
				return observed;
			});
			PhantomAssertions.assertEquals(loaded, observer.assertRestored(context, "loadControl.afterLawfulStore", profileId, player, storedEvidence), "Lawful STORE replaced the immutable original load baseline.");
			PhantomAssertions.assertTrue(storedEvidence.expGained() == earned.expGained() && storedEvidence.spGained() == earned.spGained(), "Lawful STORE changed cumulative native reward evidence.");
			observer.assertContractControls(context, profileId, player, storedEvidence);
			context.record("loadControl.nativeHitTaskStack", taskStack.get()); context.record("loadControl.shots", shots);
			context.record("loadControl.fixture", "VALID_ORIGINAL_DIRTY_ARROW_HIT_KILL_REWARD_LAWFUL_STORE; caller native cleanup remains required");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { player.removeListener(dealt); target.removeListener(avoided); }
			catch (RuntimeException | Error secondary) { if (primary == null) { throw secondary; } primary.addSuppressed(secondary); }
		}
	}

	private static void publish(PhantomMaterializationService service, long profileId, Player player, Monster target)
	{
		try (var action = service.tryAcquireAction(profileId).orElseThrow())
		{
			PhantomAssertions.assertTrue(action.player() == player, "Native bow lease changed exact Player."); player.doAttack(target);
		}
	}

	private static PlayerNativeEvidence.Snapshot sample(Player player, PhantomNativeWorkScope scope, long epoch)
	{
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && player.getNativeWorkOwner() == scope && scope.player() == player && scope.isCurrent() && scope.epoch() == epoch && scope.firstNativeIncident() == null, "Native load control lost exact current owner/epoch or recorded an incident.");
		final var evidence = scope.evidence().snapshot();
		PhantomAssertions.assertTrue(!evidence.overflow() && evidence.objectId() == player.getObjectId() && evidence.epoch() == epoch, "Native load control evidence is incomplete/stale."); return evidence;
	}
	private static void assertProgress(Player player, Progress loaded, PlayerNativeEvidence.Snapshot evidence)
	{
		PhantomAssertions.assertTrue(player.getExp() == Math.addExact(loaded.experience(), evidence.expGained()) && player.getSp() == Math.addExact(loaded.skillPoints(), evidence.spGained()), "Native progress differs from immutable loaded progress plus actual lifetime rewards.");
	}
	private static void observeHit(AtomicReference<String> failure, AtomicReference<String> stack)
	{
		final var frames = Thread.currentThread().getStackTrace();
		if (!Arrays.stream(frames).anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.tasks.creature.HitTask") && frame.getMethodName().equals("run"))) { failure.compareAndSet(null, "Original HitTask.run absent from native event stack."); }
		stack.compareAndSet(null, Arrays.stream(frames).limit(28).map(frame -> frame.getClassName() + "." + frame.getMethodName()).reduce((left, right) -> left + ">" + right).orElse(""));
	}
	private static void await(BooleanSupplier condition, long deadline, String message) throws InterruptedException
	{
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}
	private record Row(CanonicalItem item, int slot, int enchant) { }
	private static List<Row> liveRows(Player player)
	{
		return player.getInventory().getItems().stream().filter(item -> item.getCount() > 0 && Set.of("INVENTORY", "PAPERDOLL").contains(item.getItemLocation().name())).map(item -> new Row(new CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), ItemLocation.valueOf(item.getItemLocation().name())), item.getLocationSlot(), item.getEnchantLevel())).sorted(Comparator.comparingInt(row -> row.item().objectId())).toList();
	}
	private static List<Row> nativeRows(int objectId)
	{
		final var items = new ArrayList<Row>();
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT object_id, item_id, count, loc, loc_data, enchant_level FROM items WHERE owner_id=? AND count>0 AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
		{
			query.setInt(1, objectId); query.setQueryTimeout(5);
			try (var rows = query.executeQuery()) { while (rows.next()) { items.add(new Row(new CanonicalItem(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("count"), ItemLocation.valueOf(rows.getString("loc"))), rows.getInt("loc_data"), rows.getInt("enchant_level"))); } }
			return List.copyOf(items);
		}
		catch (SQLException failure) { throw new IllegalStateException("Guarded TEST native item read failed.", failure); }
	}
	private static long count(List<Row> rows, int objectId) { return rows.stream().filter(row -> row.item().objectId() == objectId).mapToLong(row -> row.item().count()).sum(); }
	private static String hash(List<Row> rows) { return PhantomBackgroundInventoryHash.compute(rows.stream().map(Row::item).toList()); }

	/** Same original scheduled-pool hold as QueuedWork; no scheduler configuration or writer replacement. */
	private static final class ScheduledWorkerGate implements AutoCloseable
	{
		private final int _count = ThreadConfig.SCHEDULED_THREAD_POOL_SIZE;
		private final CountDownLatch _started = new CountDownLatch(_count);
		private final CountDownLatch _release = new CountDownLatch(1);
		private final List<ScheduledFuture<?>> _workers = new ArrayList<>();
		private final AtomicReference<String> _failure = new AtomicReference<>();
		private void acquire() throws InterruptedException
		{
			PhantomAssertions.assertTrue(_count > 0 && _count <= 128, "INVALID dirty-arrow gate: bounded actual scheduled pool required.");
			for (int index = 0; index < _count; index++)
			{
				final var future = ThreadPool.schedule(() -> { _started.countDown(); try { if (!_release.await(10, TimeUnit.SECONDS)) { _failure.compareAndSet(null, "Native worker hold timed out."); } } catch (InterruptedException failure) { Thread.currentThread().interrupt(); _failure.compareAndSet(null, "Native worker hold interrupted."); } }, 0);
				PhantomAssertions.assertTrue(future != null, "INVALID dirty-arrow gate: original worker submission rejected."); _workers.add(future);
			}
			PhantomAssertions.assertTrue(_started.await(5, TimeUnit.SECONDS), "INVALID dirty-arrow gate: actual scheduled workers did not enter.");
		}
		@Override public void close() throws Exception
		{
			_release.countDown(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			try { for (var worker : _workers) { worker.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); } PhantomAssertions.assertEquals(null, _failure.get(), "INVALID dirty-arrow gate: actual worker hold failed."); }
			finally { _workers.forEach(worker -> worker.cancel(false)); }
		}
	}
}
