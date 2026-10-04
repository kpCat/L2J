/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.ZoneRegion;
import org.l2jmobius.gameserver.model.zone.ZoneType;
import org.l2jmobius.gameserver.model.zone.form.ZoneCuboid;
import org.l2jmobius.gameserver.model.zone.type.DamageZone;
import org.l2jmobius.gameserver.model.zone.type.EffectZone;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.DematerializeResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;

/** Actual BuffInfo/EffectTickTask publication, stock HP writer, and native effect reload. */
public final class PhantomM1BuffChecks
{
	private PhantomM1BuffChecks() { }

	/** Caller arms its native production transaction's AFTER_OWNED_PREPARE barrier after materialization. */
	public static void movement(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId,
		CountDownLatch afterOwnedPrepare, CountDownLatch releaseOwnedPrepare, Consumer<Player> cleanupLoaded) throws Exception
	{
		requireFixture(context, player, afterOwnedPrepare);
		PhantomAssertions.assertTrue(PlayerConfig.STORE_SKILL_COOLTIME, "INVALID Q12 movement: native buff persistence required.");
		final ZoneRegion region = ZoneManager.getInstance().getRegion(player);
		final Location destination = movementDestination(player, region);
		final Skill skill = SkillData.getInstance().getSkill(3125, 1), blessing = SkillData.getInstance().getSkill(1045, 1);
		PhantomAssertions.assertTrue(skill != null && blessing != null && player.getEffectList().getBuffInfoBySkillId(3125) == null && player.getEffectList().getBuffInfoBySkillId(1045) == null,
			"INVALID Q12 movement: fresh original stock buffs required.");
		final double originalMaxHp = player.getMaxHp();
		final var gate = new NativeGate();
		final var exitFinished = new CountDownLatch(1);
		final var ownedAtMovementExit = new AtomicBoolean();
		final var exitFailure = new AtomicReference<Throwable>();
		final var storeFailure = new AtomicReference<Throwable>();
		final Thread checkpoint = new Thread(() ->
		{
			try { player.store(false); }
			catch (Throwable failure) { storeFailure.set(failure); }
		}, "TEST-native-movement-checkpoint");
		final var info = new BuffInfo(player, player, skill)
		{
			@Override public Skill getSkill()
			{
				// Instrument the real remove entry expression before its first queue mutation; return the unchanged stock Skill.
				if (StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.holders.creature.EffectList") && frame.getMethodName().equals("remove")))
					&& movementStack() && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.zone.type.EffectZone") && frame.getMethodName().equals("onExit"))))
				{ gate.enter(player, "org.l2jmobius.gameserver.taskmanagers.MovementTaskManager"); }
				return super.getSkill();
			}
		};
		final var zone = new EffectZone(-700014)
		{
			{ _skills = new ConcurrentHashMap<>(); _skills.put(3125, 1); }
			@Override protected void onExit(Creature creature)
			{
				final boolean moving = movementStack();
				if (moving) { ownedAtMovementExit.set(PlayerNativeWork.current(player.getNativeWorkOwner()) != null); }
				try { super.onExit(creature); }
				catch (RuntimeException | Error failure) { if (moving) { exitFailure.set(failure); } throw failure; }
				finally { if (moving) { exitFinished.countDown(); } }
			}
		};
		zone.setParameter("removeEffectsOnExit", "true"); zone.setParameter("chance", "0"); zone.setParameter("showDangerIcon", "false");
		zone.setParameter("initialDelay", Integer.toString((int) TimeUnit.DAYS.toMillis(1))); zone.setParameter("reuse", Integer.toString((int) TimeUnit.DAYS.toMillis(1)));
		zone.setZone(new ZoneCuboid(player.getX() - 64, player.getX() + 64, player.getY() - 64, player.getY() + 64, player.getZ() - 64, player.getZ() + 64));
		PhantomAssertions.assertEquals(null, region.getZones().putIfAbsent(-700014, zone), "INVALID Q12 movement: temporary zone id already occupied.");
		Throwable primary = null;
		try
		{
			final double retainedMaxHp;
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player && !player.isMoving() && !player.isTeleporting(), "INVALID Q12 movement: wrong or moving exact Player.");
				blessing.applyEffects(player, player); retainedMaxHp = player.getMaxHp();
				player.setCurrentHp(retainedMaxHp); player.getStatus().stopHpMpRegeneration();
				for (AbstractEffect effect : skill.getEffects(EffectScope.GENERAL)) { info.addEffect(effect); }
				player.getEffectList().add(info);
				PhantomAssertions.assertTrue(player.getMaxHp() > retainedMaxHp && player.getCurrentHp() > retainedMaxHp && player.getEffectList().getBuffInfoBySkillId(3125) == info,
					"INVALID Q12 movement: original stock MaxHp writer did not install/heal its native buff.");
				zone.revalidateInZone(player);
				player.setRunning(); player.moveToLocation(destination.getX(), destination.getY(), destination.getZ(), 0);
			}
			PhantomAssertions.assertTrue(gate.entered.await(5, TimeUnit.SECONDS) && gate.nativeStack.get(), "INVALID Q12 movement: actual Movement/updatePosition/EffectList.remove did not enter.");
			PhantomAssertions.assertTrue(player.getEffectList().getBuffInfoBySkillId(3125) == info, "INVALID Q12 movement: first buff queue mutation preceded the gate.");
			context.record("Q12.movement.stockBuffPresentBeforeRemoval", true); context.record("Q12.movement.ownedAtRemovalEntry", gate.owned.get());
			context.record("Q12.movement.ownedAtZoneExitEntry", ownedAtMovementExit.get());
			checkpoint.start();
			final boolean crossed = afterOwnedPrepare.await(2, TimeUnit.SECONDS);
			context.record("Q12.movement.prepareWhileNativeRemoval", crossed);
			if (crossed) { releaseOwnedPrepare.countDown(); } // Let the RED stock writer leave even if native store holds the Player monitor.
			gate.release.countDown();
			PhantomAssertions.assertTrue(exitFinished.await(5, TimeUnit.SECONDS) && exitFailure.get() == null && gate.failure.get() == null, "INVALID Q12 movement: native zone exit failed: " + exitFailure.get());
			PhantomAssertions.assertEquals(null, player.getEffectList().getBuffInfoBySkillId(3125), "INVALID Q12 movement: actual native queue removal did not run.");
			context.record("Q12.movement.stockBuffRemovedAfterRelease", true); context.record("Q12.movement.stockHpAfterExit", player.getCurrentHp());
			// A crossed RED checkpoint can refuse the later BuffInfo body; native queue removal remains the actual writer under test.
			if (!crossed) { PhantomAssertions.assertTrue(player.getCurrentHp() <= retainedMaxHp && player.getMaxHp() <= retainedMaxHp, "Q12 movement: original MaxHp.onExit did not finish before snapshot."); }
			PhantomAssertions.assertTrue(afterOwnedPrepare.await(5, TimeUnit.SECONDS), "Q12 movement: checkpoint did not prepare after native exit.");
			releaseOwnedPrepare.countDown(); checkpoint.join(10000);
			PhantomAssertions.assertTrue(!checkpoint.isAlive() && storeFailure.get() == null, "Q12 movement: active native checkpoint failed: " + storeFailure.get());
			PhantomAssertions.assertFalse(crossed, "Owned snapshot crossed actual movement EffectList.remove before the first native buff queue mutation.");
			PhantomAssertions.assertTrue(gate.owned.get(), "Q12 movement: actual movement removal lacked its exact native owner context.");
			PhantomAssertions.assertTrue(ownedAtMovementExit.get(), "Q12 movement: native movement entered zone exit without its exact owner context.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.dematerialize(profileId).status(), "Q12 movement: final native cleanup failed.");
			final Player loaded = Player.load(player.getObjectId());
			PhantomAssertions.assertTrue(loaded != null, "Q12 movement: canonical reload returned null.");
			try
			{
				loaded.getStatus().stopHpMpRegeneration(); loaded.restoreEffects();
				final BuffInfo restored = loaded.getEffectList().getBuffInfoBySkillId(1045);
				PhantomAssertions.assertTrue(restored != null && restored.getTime() > 0 && restored.getTime() <= blessing.getAbnormalTime() && loaded.getMaxHp() > originalMaxHp,
					"Q12 movement: unrelated original stock blessing or remaining time was lost on final reload.");
				PhantomAssertions.assertEquals(null, loaded.getEffectList().getBuffInfoBySkillId(3125), "Q12 movement: removed stock zone buff reappeared on reload.");
				context.record("Q12.movement.reload", "stock1045=" + restored.getTime() + " removed3125=true");
			}
			finally { cleanupLoaded.accept(loaded); }
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			gate.release.countDown(); releaseOwnedPrepare.countDown();
			region.getZones().remove(-700014, zone);
			try
			{
				if (gate.entered.getCount() == 0) { PhantomAssertions.assertTrue(exitFinished.await(5, TimeUnit.SECONDS), "Q12 movement fixture retained native zone exit."); }
				if (checkpoint.getState() != Thread.State.NEW) { checkpoint.join(10000); }
				PhantomAssertions.assertFalse(checkpoint.isAlive(), "Q12 movement fixture retained checkpoint thread.");
				player.stopMove(null); zone.removeCharacter(player);
			}
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
	}

	private static boolean movementStack()
	{
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith("org.l2jmobius.gameserver.taskmanagers.MovementTaskManager") && frame.getMethodName().equals("run")))
			&& StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals("org.l2jmobius.gameserver.model.actor.Creature") && frame.getMethodName().equals("updatePosition")));
	}

	private static Location movementDestination(Player player, ZoneRegion region)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		PhantomAssertions.assertTrue(region != null && geo.hasGeo(player.getX(), player.getY()), "INVALID Q12 movement: actual zone region/geodata unavailable.");
		for (int[] offset : new int[][] { { 256, 0 }, { -256, 0 }, { 0, 256 }, { 0, -256 } })
		{
			final int x = player.getX() + offset[0], y = player.getY() + offset[1], z = geo.getHeight(x, y, player.getZ());
			if (geo.hasGeo(x, y) && ZoneManager.getInstance().getRegion(x, y) == region && geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId())) { return new Location(x, y, z); }
		}
		throw new AssertionError("INVALID Q12 movement: no actual short same-region geodata corridor.");
	}

	/** Actual natural BuffFinishTask continuation; the TEST gate supplies no status writer. */
	public static void expiry(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId,
		CountDownLatch beforeStore, CountDownLatch releaseBeforeStore) throws Exception
	{
		requireFixture(context, player, beforeStore);
		final Skill skill = SkillData.getInstance().getSkill(3125, 1);
		PhantomAssertions.assertTrue(skill != null && player.getEffectList().getBuffInfoBySkillId(3125) == null, "INVALID Q12 expiry: original stock buff required.");
		final AbstractEffect stock = skill.getEffects(EffectScope.GENERAL).stream().filter(effect -> effect.getClass().getName().equals("handlers.skill.effects.MaxHp")).findFirst().orElseThrow();
		final double baseMaxHp = player.getMaxHp();
		final var gate = new NativeGate();
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "INVALID Q12 expiry: wrong exact Player.");
				player.setCurrentHp(baseMaxHp); player.getStatus().stopHpMpRegeneration();
				final var info = new BuffInfo(player, player, skill);
				info.setAbnormalTime(1); // Existing TEST near-expiry seam, stock expiry pulse unchanged.
				final var gateSettings = new StatSet(); gateSettings.set("name", "TEST_NativeExpiryEntry");
				info.addEffect(new AbstractEffect(null, null, gateSettings, new StatSet())
				{
					@Override public void onExit(Creature effector, Creature effected, Skill effectSkill)
					{
						gate.enter(player, "org.l2jmobius.gameserver.model.skill.BuffFinishTask");
					}
				});
				info.addEffect(stock); // Original effect identity owns its original stat functions.
				player.getEffectList().add(info);
				PhantomAssertions.assertTrue(player.getMaxHp() > baseMaxHp && player.getCurrentHp() > baseMaxHp, "INVALID Q12 expiry: stock MaxHp did not increase/heal HP.");
			}
			assertDrain(context, "Q12.buff.expiry", player, materialization, profileId, beforeStore, releaseBeforeStore, gate, () ->
			{
				PhantomAssertions.assertEquals(null, player.getEffectList().getBuffInfoBySkillId(3125), "INVALID Q12 expiry: native list removal did not precede onExit.");
				await(() -> player.getCurrentHp() <= baseMaxHp, "INVALID Q12 expiry: original MaxHp.onExit did not clamp HP.");
				context.record("Q12.buff.expiry.stockClamp", player.getCurrentHp());
			});
		}
		finally { gate.release.countDown(); }
	}

	/** Actual shared native zone producer with a TEST-only entry gate before its stock writer. */
	public static void zone(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId,
		CountDownLatch beforeStore, CountDownLatch releaseBeforeStore, boolean damage) throws Exception
	{
		requireFixture(context, player, beforeStore);
		final var gate = new NativeGate();
		final double hp = player.getMaxHp(), mp = player.getMaxMp();
		final var observedHp = new AtomicReference<Double>();
		final ZoneType zone;
		if (damage)
		{
			zone = new DamageZone(-700012)
			{
				@Override protected int getHPDamagePerSecond() { gate.enter(player, "org.l2jmobius.gameserver.model.zone.type.DamageZone"); return super.getHPDamagePerSecond(); }
				@Override protected int getMPDamagePerSecond() { observedHp.set(player.getCurrentHp()); return super.getMPDamagePerSecond(); }
			};
			zone.setParameter("dmgHPSec", "7"); zone.setParameter("dmgMPSec", "3");
		}
		else
		{
			PhantomAssertions.assertEquals(null, player.getEffectList().getBuffInfoBySkillId(3125), "INVALID Q12 zone: stock max HP buff already active.");
			zone = new HeldEffectZone(player, gate, observedHp);
			zone.setParameter("bypassSkillConditions", "true"); zone.setParameter("showDangerIcon", "false");
		}
		zone.setParameter("initialDelay", "0"); zone.setParameter("reuse", Long.toString(TimeUnit.DAYS.toMillis(1)));
		zone.setZone(new ZoneCuboid(player.getX() - 64, player.getX() + 64, player.getY() - 64, player.getY() + 64, player.getZ() - 64, player.getZ() + 64));
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "INVALID Q12 zone: wrong exact Player.");
				player.setCurrentHp(hp); player.setCurrentMp(mp); player.getStatus().stopHpMpRegeneration();
				zone.revalidateInZone(player);
			}
			assertDrain(context, damage ? "Q12.zone.damage" : "Q12.zone.effect", player, materialization, profileId, beforeStore, releaseBeforeStore, gate, () ->
			{
				await(() -> observedHp.get() != null, "INVALID Q12 zone: actual stock producer did not finish its HP writer.");
				PhantomAssertions.assertTrue(damage ? observedHp.get() < hp : observedHp.get() > hp, "INVALID Q12 zone: no native stock HP mutation.");
				if (damage) { await(() -> player.getCurrentMp() < mp, "INVALID Q12 zone: no native stock MP mutation."); }
				context.record(damage ? "Q12.zone.damage.nativeDelta" : "Q12.zone.effect.nativeDelta", observedHp.get() - hp);
			});
		}
		finally { gate.release.countDown(); zone.removeCharacter(player); }
	}

	private static void requireFixture(PhantomTestContext context, Player player, CountDownLatch beforeStore)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Q12 requires guarded headless TEST.");
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && player.getNativeWorkOwner().isCurrent(), "INVALID Q12: exact managed owner required.");
		PhantomAssertions.assertEquals(1L, beforeStore.getCount(), "INVALID Q12: BEFORE_STORE must begin closed.");
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	@FunctionalInterface
	private interface CheckedAction { void run() throws Exception; }

	private static void assertDrain(PhantomTestContext context, String label, Player player, PhantomMaterializationService materialization, long profileId,
		CountDownLatch beforeStore, CountDownLatch releaseBeforeStore, NativeGate gate, CheckedAction stockMutation) throws Exception
	{
		final var result = new AtomicReference<DematerializeResult>();
		final var failure = new AtomicReference<Throwable>();
		final Thread cleanup = new Thread(() ->
		{
			try { result.set(materialization.dematerialize(profileId)); }
			catch (Throwable thrown) { failure.set(thrown); }
		}, "TEST-native-effect-cleanup");
		Throwable primary = null;
		try
		{
			PhantomAssertions.assertTrue(gate.entered.await(5, TimeUnit.SECONDS) && gate.nativeStack.get(), "INVALID " + label + ": actual native producer did not enter.");
			cleanup.start();
			final boolean crossed = beforeStore.await(2, TimeUnit.SECONDS);
			context.record(label + ".beforeStoreWhileRunning", crossed); context.record(label + ".owned", gate.owned.get());
			gate.release.countDown();
			stockMutation.run();
			PhantomAssertions.assertEquals(null, gate.failure.get(), "INVALID " + label + ": native instrumentation failed.");
			PhantomAssertions.assertFalse(crossed, label + " snapshot crossed the running actual native stock writer.");
			PhantomAssertions.assertTrue(gate.owned.get(), label + " native invocation lacks captured receiver context.");
			PhantomAssertions.assertTrue(beforeStore.await(5, TimeUnit.SECONDS), label + " cleanup did not store after body exit.");
			releaseBeforeStore.countDown(); cleanup.join(10000);
			PhantomAssertions.assertTrue(!cleanup.isAlive() && failure.get() == null && result.get() != null, label + " cleanup failed: " + failure.get());
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.get().status(), label + " cleanup retained unresolved work.");
		}
		catch (Exception | Error thrown) { primary = thrown; throw thrown; }
		finally
		{
			gate.release.countDown(); releaseBeforeStore.countDown();
			try
			{
				if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				PhantomAssertions.assertFalse(cleanup.isAlive(), label + " TEST cleanup thread remained active.");
			}
			catch (Exception | Error thrown) { if (primary == null) { throw thrown; } primary.addSuppressed(thrown); }
		}
	}

	/** Entry instrumentation never supplies a native writer or replaces the stock effect identity. */
	private static final class NativeGate
	{
		final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		final AtomicBoolean nativeStack = new AtomicBoolean(), owned = new AtomicBoolean();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		private final AtomicInteger _calls = new AtomicInteger();
		void enter(Player player, String nativeClass)
		{
			if (_calls.incrementAndGet() != 1) { return; }
			nativeStack.set(StackWalker.getInstance().walk(stream -> stream.anyMatch(frame -> frame.getClassName().startsWith(nativeClass))));
			owned.set(PlayerNativeWork.current(player.getNativeWorkOwner()) != null);
			entered.countDown(); boolean interrupted = false;
			try
			{
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
				while (release.getCount() != 0)
				{
					final long remaining = deadline - System.nanoTime();
					if (remaining <= 0) { throw new AssertionError("Native status TEST gate timed out."); }
					try { release.await(remaining, TimeUnit.NANOSECONDS); }
					catch (InterruptedException ignored) { interrupted = true; }
				}
			}
			catch (RuntimeException | Error thrown) { failure.set(thrown); throw thrown; }
			finally { if (interrupted) { Thread.currentThread().interrupt(); } }
		}
	}

	private static final class HeldEffectZone extends EffectZone
	{
		HeldEffectZone(Player player, NativeGate gate, AtomicReference<Double> observedHp)
		{
			super(-700013);
			_skills = new ConcurrentHashMap<>()
			{
				private static final long serialVersionUID = 1L;
				@Override public Set<Map.Entry<Integer, Integer>> entrySet()
				{
					gate.enter(player, "org.l2jmobius.gameserver.model.zone.type.EffectZone");
					final Set<Map.Entry<Integer, Integer>> entries = super.entrySet();
					return new AbstractSet<>()
					{
						@Override public int size() { return entries.size(); }
						@Override public Iterator<Map.Entry<Integer, Integer>> iterator()
						{
							final var iterator = entries.iterator();
							return new Iterator<>()
							{
								@Override public boolean hasNext()
								{
									final boolean next = iterator.hasNext();
									if (!next) { observedHp.set(player.getCurrentHp()); }
									return next;
								}
								@Override public Map.Entry<Integer, Integer> next() { return iterator.next(); }
							};
						}
					};
				}
			};
			_skills.put(3125, 1);
		}
	}

	/** Caller supplies its existing BEFORE_STORE_OPERATION barrier and loaded-player cleanup. */
	public static void run(PhantomTestContext context, Player player, PhantomMaterializationService materialization, long profileId,
		CountDownLatch beforeStore, CountDownLatch releaseBeforeStore, Consumer<Player> cleanupLoaded) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Q12 Buff requires guarded headless TEST.");
		PhantomAssertions.assertTrue(player.isNativeWorkManaged() && PlayerConfig.STORE_SKILL_COOLTIME, "INVALID Q12 Buff: managed Player and native effect persistence required.");
		PhantomAssertions.assertEquals(1L, beforeStore.getCount(), "INVALID Q12 Buff: BEFORE_STORE must begin closed.");
		final Skill potion = SkillData.getInstance().getSkill(2031, 1);
		final Skill blessing = SkillData.getInstance().getSkill(1045, 1);
		PhantomAssertions.assertTrue((potion != null) && (blessing != null) && potion.hasEffects(EffectScope.GENERAL), "INVALID Q12 Buff: stock skills unavailable.");
		final AbstractEffect stock = potion.getEffects(EffectScope.GENERAL).stream().filter(effect -> effect.getName().equals("HealOverTime")).findFirst().orElseThrow();
		PhantomAssertions.assertTrue(stock.getClass().getName().equals("handlers.skill.effects.HealOverTime") && (stock.getTicks() > 0), "INVALID Q12 Buff: actual stock ticking handler unavailable.");
		PhantomAssertions.assertEquals(null, player.getEffectList().getBuffInfoBySkillId(2031), "INVALID Q12 Buff: potion already active.");
		PhantomAssertions.assertEquals(null, player.getEffectList().getBuffInfoBySkillId(1045), "INVALID Q12 Buff: blessing already active.");
		final double baseMaxHp = player.getMaxHp();
		final var tick = new HeldTick(stock);
		final var cleanupResult = new AtomicReference<DematerializeResult>();
		final var cleanupFailure = new AtomicReference<Throwable>();
		final Thread cleanup = new Thread(() ->
		{
			try { cleanupResult.set(materialization.dematerialize(profileId)); }
			catch (Throwable failure) { cleanupFailure.set(failure); }
		}, "TEST-native-buff-cleanup");
		Throwable primary = null;
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				PhantomAssertions.assertTrue(action.player() == player, "INVALID Q12 Buff: wrong materialized Player.");
				player.setCurrentHp(baseMaxHp / 2);
				player.getStatus().stopHpMpRegeneration();
				blessing.applyEffects(player, player);
				PhantomAssertions.assertTrue(player.getMaxHp() > baseMaxHp, "INVALID Q15 Buff: stock Bless the Body did not increase max HP.");
				final BuffInfo info = new BuffInfo(player, player, potion);
				info.addEffect(tick);
				player.getEffectList().add(info);
				PhantomAssertions.assertTrue(player.getEffectList().getBuffInfoBySkillId(2031) == info, "INVALID Q12 Buff: native BuffInfo not installed.");
			}
			PhantomAssertions.assertTrue(tick.entered.await(5, TimeUnit.SECONDS), "INVALID Q12 Buff: actual scheduled effect did not enter.");
			PhantomAssertions.assertTrue(tick.nativeStack.get(), "INVALID Q12 Buff: callback lacks actual EffectTickTask/BuffInfo stack.");
			cleanup.start();
			final boolean crossed = beforeStore.await(2, TimeUnit.SECONDS);
			context.record("Q12.buff.beforeStoreWhileTick", crossed);
			context.record("Q12.buff.ownedAtTickEntry", tick.owned.get());
			context.record("Q12.buff.nativeStack", tick.nativeStack.get());
			tick.release.countDown();
			PhantomAssertions.assertTrue(tick.finished.await(5, TimeUnit.SECONDS), "INVALID Q12 Buff: stock callback failed to leave after release.");
			PhantomAssertions.assertEquals(null, tick.failure.get(), "INVALID Q12 Buff: stock tick failed.");
			context.record("Q12.buff.stockHpGain", tick.hpAfter.get() - tick.hpBefore.get());
			context.record("Q12.buff.interruptedWhileRunning", tick.interrupted.get());
			PhantomAssertions.assertTrue(tick.hpAfter.get() > tick.hpBefore.get(), "INVALID Q12 Buff: stock HealOverTime produced no native HP gain.");
			PhantomAssertions.assertFalse(crossed, "Owned snapshot crossed a running actual EffectTickTask before stock HealOverTime HP mutation.");
			PhantomAssertions.assertTrue(beforeStore.await(5, TimeUnit.SECONDS), "Q12 Buff cleanup did not reach final store after native callback exit.");
			releaseBeforeStore.countDown();
			cleanup.join(10000);
			PhantomAssertions.assertTrue(!cleanup.isAlive() && (cleanupFailure.get() == null) && (cleanupResult.get() != null), "Q12 Buff cleanup failed: " + cleanupFailure.get());
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanupResult.get().status(), "Q12 Buff cleanup retained an unresolved outcome.");
			final Player loaded = Player.load(player.getObjectId());
			PhantomAssertions.assertTrue(loaded != null, "Q15 Buff canonical reload returned null.");
			try
			{
				loaded.getStatus().stopHpMpRegeneration();
				loaded.restoreEffects();
				final BuffInfo restoredBlessing = loaded.getEffectList().getBuffInfoBySkillId(1045);
				final BuffInfo restoredPotion = loaded.getEffectList().getBuffInfoBySkillId(2031);
				PhantomAssertions.assertTrue((restoredBlessing != null) && (restoredBlessing.getTime() > 0), "Q15 Buff lost the original live stock blessing during pre-store effect disposal.");
				PhantomAssertions.assertTrue(loaded.getMaxHp() > baseMaxHp, "Q15 Buff reload did not restore actual stock max HP stat.");
				PhantomAssertions.assertTrue((restoredPotion != null) && (restoredPotion.getTime() > 0) && (restoredPotion.getTime() <= potion.getAbnormalTime()), "Q15 Buff lost or refreshed native potion remaining time.");
				PhantomAssertions.assertTrue(restoredPotion.getEffects().stream().anyMatch(effect -> effect.getClass().getName().equals("handlers.skill.effects.HealOverTime")), "Q15 Buff reload did not use the original stock effect handler.");
				context.record("Q15.buff.reload", "stock1045=" + restoredBlessing.getTime() + " stock2031=" + restoredPotion.getTime() + " maxHp=" + loaded.getMaxHp());
			}
			finally { cleanupLoaded.accept(loaded); }
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			tick.release.countDown(); releaseBeforeStore.countDown();
			try
			{
				if (tick.entered.getCount() == 0) { PhantomAssertions.assertTrue(tick.finished.await(5, TimeUnit.SECONDS), "Q12 Buff fixture retained running native callback."); }
				if (cleanup.getState() != Thread.State.NEW) { cleanup.join(10000); }
				PhantomAssertions.assertFalse(cleanup.isAlive(), "Q12 Buff fixture retained cleanup thread.");
			}
			catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
		}
	}

	/** TEST entry instrumentation delegates every effect callback to the loaded stock handler. */
	private static final class HeldTick extends AbstractEffect
	{
		private final AbstractEffect _stock;
		final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
		final AtomicInteger calls = new AtomicInteger();
		final AtomicBoolean nativeStack = new AtomicBoolean(), owned = new AtomicBoolean(), interrupted = new AtomicBoolean();
		final AtomicReference<Double> hpBefore = new AtomicReference<>(0.0), hpAfter = new AtomicReference<>(0.0);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		HeldTick(AbstractEffect stock) { super(null, null, settings(stock), new StatSet()); _stock = stock; }
		private static StatSet settings(AbstractEffect stock)
		{
			final var set = new StatSet(); set.set("name", stock.getName()); set.set("ticks", stock.getTicks()); return set;
		}
		@Override public void onStart(Creature effector, Creature effected, Skill skill) { _stock.onStart(effector, effected, skill); }
		@Override public void onExit(Creature effector, Creature effected, Skill skill) { _stock.onExit(effector, effected, skill); }
		@Override public boolean onActionTime(Creature effector, Creature effected, Skill skill)
		{
			if (calls.incrementAndGet() != 1) { return _stock.onActionTime(effector, effected, skill); }
			final var frames = StackWalker.getInstance().walk(stream -> stream.map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList());
			nativeStack.set(frames.contains("org.l2jmobius.gameserver.model.effects.EffectTickTask.run") && frames.contains("org.l2jmobius.gameserver.model.skill.BuffInfo.onTick"));
			owned.set((effected instanceof Player player) && (PlayerNativeWork.current(player.getNativeWorkOwner()) != null));
			entered.countDown();
			try
			{
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
				while (release.getCount() != 0)
				{
					final long remaining = deadline - System.nanoTime();
					if (remaining <= 0) { throw new AssertionError("Q12 Buff native tick barrier timed out."); }
					try { release.await(remaining, TimeUnit.NANOSECONDS); }
					catch (InterruptedException ignored) { interrupted.set(true); }
				}
				hpBefore.set(effected.getCurrentHp());
				final boolean repeat = _stock.onActionTime(effector, effected, skill);
				hpAfter.set(effected.getCurrentHp());
				return repeat;
			}
			catch (RuntimeException | Error thrown) { failure.set(thrown); throw thrown; }
			finally { finished.countDown(); if (interrupted.get()) { Thread.currentThread().interrupt(); } }
		}
	}
}
