/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** Continuity contracts composed from the existing owned native fixture. */
public final class PhantomVisibleContinuity032Suite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("visible-continuity032", new PhantomVisibleContinuity032Suite(), new PhantomTestContext(32003201, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "visible-continuity032"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("retirement032".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("C04-locality-pause-stops-next-intention-but-completes-earned-hit", this::retirement); return;
		}
		registry.add("C03-first-decline-recording-preserves-short-circuit-and-SQL-failure", this::readiness);
		if ("readiness032".equals(System.getProperty("phantom.m1.native.focus"))) { return; }
		registry.add("C01-old-native-target-failure-does-not-fence-new-epoch", context ->
		{
			try (var f = _intent.handoff.new Fixture(true))
			{
				f.handoff(); final var goal = f.goals.load(f.id).orElseThrow().goal(); final var player = f.loadedPlayer;
				final long oldEpoch = f.loadedEpoch;
				final var failure = new PhantomVisibleFarmTravel.Failure(f.id, player, oldEpoch, goal, "", "travel.native_action_rejected", PhantomVisibleFarmTravel.Disposition.NATIVE_ACTION_REJECTED, 1, 1);
				PhantomAssertions.assertTrue(f.historical.recordVisibleTravelFailure(f.id, failure), "Actual current failure producer accepted.");
				PhantomAssertions.assertFalse(f.historical.visibleFarmReady(f.id, goal), "Current epoch remains fenced by its own target failure.");
				PhantomVisibleIntentRecoverySuite.complete(f);
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Original whole owned native store/drain.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Completed claim permits ordinary native reload.");
				PhantomAssertions.assertTrue(f.loadedEpoch != oldEpoch && f.loadedPlayer != player, "Actual new owner incarnation required.");
				PhantomAssertions.assertFalse(f.historical.recordVisibleTravelFailure(f.id, failure), "Delayed old producer remains rejected.");
				PhantomAssertions.assertTrue(f.historical.visibleFarmReady(f.id, goal), "RED: old target exclusion cannot fence the actual new epoch.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow().goal(), "Epoch recovery changes no durable goal or revision.");
				context.record("C01.epochs", oldEpoch + "->" + f.loadedEpoch);
			}
		});
		registry.add("C02-stock-autoplay-can-damage-lawful-shared-target", this::cooperative);
		registry.add("C04-locality-pause-stops-next-intention-but-completes-earned-hit", this::retirement);
	}
	private void retirement(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var permitsRoots = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision, _ -> permitsRoots.get(), System::nanoTime);
			final Player player = f.loadedPlayer;
			final int instance = InstanceManager.getInstance().createDynamicInstance(0).getId();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final Monster target = new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
			try
			{
				player.setInstanceId(instance); target.setInstanceId(instance);
				final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX() + 32, player.getY(), player.getZ()); target.setSpawn(spawn);
				target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				PhantomAssertions.assertTrue(autoPlay.start(f.id, goal), "Current native AutoPlay starts before locality withdrawal.");
				final var policies = org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.class.getDeclaredField("PHANTOM_POLICIES"); policies.setAccessible(true);
				final var policy = (org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy) ((java.util.Map<?, ?>) policies.get(null)).get(player);
				PhantomAssertions.assertTrue(policy != null, "Actual registered native policy required.");
				player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.ATTACK, target);
				final long deadline = System.nanoTime() + 5_000_000_000L;
				while (!player.isAttackingNow() && System.nanoTime() < deadline) { Thread.sleep(10); }
				PhantomAssertions.assertTrue(player.isAttackingNow(), "Real stock attack must already have captured its native hit.");
				final long damage = player.getNativeWorkOwner().evidence().snapshot().damageSequence();
				permitsRoots.set(false);
				final var admission = policy.acquireTick(player, "AutoPlay");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickStatus.PAUSED, admission.status(), "Withdrawal pauses ordinary pool roots.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.ai.Intention.IDLE, player.getAI().getIntention(), "RED: paused locality must stop the next stock attack intention.");
				while (player.getNativeWorkOwner().evidence().snapshot().damageSequence() == damage && System.nanoTime() < deadline) { Thread.sleep(10); }
				PhantomAssertions.assertTrue(player.getNativeWorkOwner().evidence().snapshot().damageSequence() > damage, "Captured earned native hit still completes after IDLE.");
				PhantomAssertions.assertEquals(f.loadedEpoch, player.getNativeWorkOwner().epoch(), "No owner replacement or debt reset.");
				context.record("C04.native", autoPlay.snapshotContinuation(f.id).scalarMap());
			}
			finally
			{
				autoPlay.stop(f.id); target.abortAttack(); target.abortCast(); target.deleteMe();
				player.setInstanceId(0); InstanceManager.getInstance().destroyInstance(instance); PhantomVisibleIntentRecoverySuite.stop(engine);
			}
		}
	}
	private void readiness(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
			final String consent = "contract-readiness032";
			PhantomAssertions.assertTrue(recorder.begin(consent), "Existing bounded recorder enabled."); recorder.watch(f.id);
			final var poolField = org.l2jmobius.commons.database.DatabaseFactory.class.getDeclaredField("DATABASE_POOL"); poolField.setAccessible(true);
			final var original = (com.zaxxer.hikari.HikariDataSource) poolField.get(null);
			final var loads = new java.util.concurrent.atomic.AtomicInteger(); final var fail = new java.util.concurrent.atomic.AtomicBoolean();
			final Thread caller = Thread.currentThread();
			poolField.set(null, new com.zaxxer.hikari.HikariDataSource()
			{
				@Override public java.sql.Connection getConnection() throws java.sql.SQLException
				{
					if (Thread.currentThread() == caller) { loads.incrementAndGet(); if (fail.get()) { throw new java.sql.SQLException("TEST032_GOAL_READ_FAILURE"); } }
					return original.getConnection();
				}
			});
			try
			{
				PhantomAssertions.assertTrue(f.historical.visibleFarmReady(f.id, goal), "Exact persisted goal remains ready.");
				PhantomAssertions.assertEquals(1, loads.get(), "Ready evaluation reads the real goal once.");
				fail.set(true); PhantomAssertions.assertThrows(org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException.class, () -> f.historical.visibleFarmReady(f.id, goal), "Original persistence exception propagates.");
				fail.set(false); f.historical.recordVisibleFailure(f.id, goal, ""); loads.set(0); fail.set(true);
				PhantomAssertions.assertFalse(f.historical.visibleFarmReady(f.id, goal), "Current target exclusion short-circuits before failing SQL.");
				PhantomAssertions.assertEquals(0, loads.get(), "Decline records no duplicate or late goal read.");
				final var events = recorder.snapshot(consent).events().stream().filter(event -> event.event().equals("VISIBLE_FARM_READINESS")).toList();
				PhantomAssertions.assertEquals(2, events.size(), "RED: actual readiness calls must record one result each, and no fabricated result on SQL failure.");
				PhantomAssertions.assertEquals("visible.ready", events.get(0).reason(), "Exact ready branch.");
				PhantomAssertions.assertEquals("visible.target_excluded", events.get(1).reason(), "First false branch captured before any consumer stop.");
				PhantomAssertions.assertEquals(f.loadedEpoch, events.get(1).value1(), "Exact native epoch.");
				PhantomAssertions.assertEquals(goal.goalId(), events.get(1).value2(), "Exact goal identity.");
				PhantomAssertions.assertEquals(goal.revision(), events.get(1).value3(), "Exact goal revision.");
				context.record("C03.actual", events);
			}
			finally { poolField.set(null, original); recorder.end(consent); }
		}
	}
	private void cooperative(PhantomTestContext context) throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) field.get(_intent.handoff);
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var autoPlay = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
			final Player player = f.loadedPlayer, ordinary = Player.load(environment.observer().objectId());
			final int instance = InstanceManager.getInstance().createDynamicInstance(0).getId();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final Monster target = new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
			try (var output = ordinary.attachOutboundSession(new org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession(8, 128)))
			{
				player.setInstanceId(instance); ordinary.setInstanceId(instance); ordinary.stopAllTasks(); ordinary.setOnlineStatus(true, false);
				ordinary.spawnMe(player.getX() + 30, player.getY(), player.getZ());
				target.setInstanceId(instance); final var spawn = new Spawn(target.getTemplate());
				spawn.setXYZ(player.getX() + 48, player.getY(), player.getZ()); target.setSpawn(spawn);
				target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				target.setTarget(ordinary);
				final long damage = player.getNativeWorkOwner().evidence().snapshot().damageSequence();
				PhantomAssertions.assertTrue(autoPlay.start(f.id, goal), "Current stock pools start with one lawful occupied target.");
				final long deadline = System.nanoTime() + 8_000_000_000L;
				while (player.getNativeWorkOwner().evidence().snapshot().damageSequence() == damage && System.nanoTime() < deadline) { Thread.sleep(20); }
				PhantomAssertions.assertTrue(player.getNativeWorkOwner().evidence().snapshot().damageSequence() > damage, "RED: cooperative target preference cannot become exclusive native admission.");
				context.record("C02.native", autoPlay.snapshotContinuation(f.id).scalarMap());
			}
			finally
			{
				autoPlay.stop(f.id); target.abortAttack(); target.abortCast(); target.deleteMe(); environment.cleanupLoadedPlayer(ordinary);
				player.setInstanceId(0); InstanceManager.getInstance().destroyInstance(instance); PhantomVisibleIntentRecoverySuite.stop(engine);
			}
		}
	}
}
