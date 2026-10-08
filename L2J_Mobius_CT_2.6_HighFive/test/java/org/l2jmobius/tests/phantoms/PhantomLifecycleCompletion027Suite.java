/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Genuine native lifecycle composition, reusing the existing guarded TEST fixture. */
public final class PhantomLifecycleCompletion027Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("lifecycle-completion027", new PhantomLifecycleCompletion027Suite(), new PhantomTestContext(27002701, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "lifecycle-completion027"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_handoff.beforeAll(context);
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.m1.native.focus", "all");
		if (focus.equals("all") || focus.equals("callback")) { registry.add("S02-native-delayed-kill-child-before-system-store", this::callback); }
		if (focus.equals("all") || focus.equals("death")) { registry.add("D05-accepted-queued-native-return-survives-close", this::queuedDeath); }
		if (focus.equals("all") || focus.equals("cold")) { registry.add("D03-cold-canonical-dead-native-recovery", this::coldDead); }
		if (focus.equals("dispatch")) { registry.add("E01-executor-entry-before-owner-monitor", this::dispatch); }
	}

	private void dispatch(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native diagnostic lifetime.");
			org.l2jmobius.gameserver.phantoms.player.PhantomLifecycleDispatch027Checks.enteredBeforeOwnerMonitor(context, World.getInstance().getPlayer(f.objectId));
		}
	}

	private static PhantomSystem configure(PhantomNativeContextHandoffSuite.Fixture fixture) throws Exception
	{
		final var method = PhantomSystem.class.getDeclaredMethod("configureForTesting", PhantomMaterializationService.class);
		method.setAccessible(true); method.invoke(null, fixture.materialization);
		final var instance = PhantomSystem.class.getDeclaredField("_configuredInstance"); instance.setAccessible(true);
		final var configured = (PhantomSystem) instance.get(null);
		final var background = PhantomSystem.class.getDeclaredField("_backgroundService"); background.setAccessible(true); background.set(configured, fixture.background);
		return configured;
	}

	private void callback(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Actual managed native actor.");
			final var player = World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var system = configure(f);
			final var npc = new Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(20545));
			final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn);
			npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final var calls = new AtomicInteger(); final var children = new AtomicInteger(); final var selfWait = new AtomicLong();
			org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(context.moduleRoot().resolve("test/resources/phantoms/M1TimerBootstrap.java"));
			final var quest = org.l2jmobius.gameserver.managers.ScriptManager.getInstance().getScript("M1TimerBootstrap");
			final var bodyField = PhantomM1TimerChecks.class.getDeclaredField("QUEST_BODY"); bodyField.setAccessible(true);
			@SuppressWarnings("unchecked") final var body = (AtomicReference<java.util.function.Consumer<Player>>) bodyField.get(null);
			PhantomAssertions.assertTrue(body.compareAndSet(null, actor -> { children.incrementAndGet(); actor.setHeading((actor.getHeading() + 32) % 65536); }), "Exclusive existing native QuestTimer fixture.");
			final var listener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(npc, org.l2jmobius.gameserver.model.events.EventType.ON_ATTACKABLE_KILL,
				(org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill event) ->
				{
					PhantomAssertions.assertTrue(event.getAttacker() == player && PlayerNativeWork.current(scope) != null, "Original earned owner is inherited.");
					calls.incrementAndGet();
					final long started = System.nanoTime();
					PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Native worker must publish control without self-wait.");
					selfWait.set(System.nanoTime() - started);
					quest.startQuestTimer("TASK027_ACTUAL_KILL_CHILD", 150, npc, player);
				}, scope);
			npc.addListener(listener);
			try
			{
				final long exp = player.getExp();
				PlayerNativeWork.run(player, List.of(npc), "TEST027_ORIGINAL_NATIVE_DAMAGE", () -> npc.reduceCurrentHp(npc.getMaxHp() * 2, player, null));
				PhantomAssertions.assertTrue(npc.isDead() && player.getExp() > exp && calls.get() == 0, "Native HP/death/reward precedes the real delayed kill callback.");
				context.record("S02.published", scope.snapshot());
				final boolean stopped = PhantomSystem.shutdownIfStarted();
				context.record("S02.stopResult", "stopped=" + stopped + ";progress=" + system.shutdownProgress() + ";owner=" + scope.snapshot());
				PhantomAssertions.assertTrue(stopped, "Actual callback and child must complete before system store/dependency stop.");
				PhantomAssertions.assertEquals(1, calls.get(), "Exactly one original native kill callback.");
				PhantomAssertions.assertEquals(1, children.get(), "Exactly one stock QuestTimer child.");
				PhantomAssertions.assertTrue(selfWait.get() < 50_000_000L, "Native callback cannot wait on its own stop attempt.");
				PhantomAssertions.assertEquals(PhantomSystem.StopOutcome.COMPLETE, system.shutdownProgress().outcome(), "Typed completion.");
				PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.pendingTimers() == 0 && scope.firstNativeIncident() == null, "Earned native work completed, no incident.");
				context.record("S02.complete", "calls=" + calls + ";children=" + children + ";selfWait=" + selfWait + ";progress=" + system.shutdownProgress() + ";owner=" + scope.snapshot());
			}
			finally { npc.removeListener(listener); body.set(null); npc.deleteMe(); if (PhantomSystem.hasConfiguredInstance()) { PhantomSystem.shutdownIfStarted(); } }
		}
	}

	private void queuedDeath(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); f.background.installPresencePolicy(id -> id == f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native actor before death.");
			final var player = World.getInstance().getPlayer(f.objectId);
			final var clock = new AtomicLong(System.nanoTime()); final var queued = new AtomicReference<Runnable>();
			try (var death = new PhantomOrdinaryDeathRecovery(f.materialization, f.goals, f.background, clock::get, queued::set))
			{
				PhantomAssertions.assertTrue(death.install(), "Existing death listener installed.");
				PlayerNativeWork.run(player, "TEST027_NATIVE_DEATH", () -> player.doDie(null));
				PhantomAssertions.assertTrue(player.isDead(), "Original native Player death.");
				clock.addAndGet(46_000_000_000L); death.pulse();
				try
				{
					PhantomAssertions.assertTrue(queued.get() != null && !player.isDead(), "Original native corpse return accepted one control reconciliation.");
					context.record("D05.beforeClose", "dead=" + player.isDead() + ";drained=" + death.drained() + ";background=" + f.background.snapshot());
					PhantomAssertions.assertFalse(death.drained(), "RED D05: queued accepted reconciliation is part of lifecycle quiescence.");
					death.close(); queued.getAndSet(null).run();
					PhantomAssertions.assertTrue(death.drained(), "Closed death control finishes the exact accepted store.");
					PhantomAssertions.assertTrue(f.transactions.load(f.id).state().vitals().currentHp() > 0, "Native revived HP was durably captured.");
				}
				finally { death.close(); final var task = queued.getAndSet(null); if (task != null) { task.run(); } }
			}
		}
	}

	private void coldDead(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); f.background.installPresencePolicy(id -> id == f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native first lifetime.");
			final var original = World.getInstance().getPlayer(f.objectId);
			PlayerNativeWork.run(original, "TEST027_COLD_NATIVE_DEATH", () -> original.doDie(null));
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Actual native corpse store/release.");
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.DEAD, f.transactions.load(f.id).state().state(), "Canonical cold DEAD is from native death, not DML.");
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(f.objectId), "No old Player/death memory.");
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var result = f.background.recover(f.id, goal, PhantomActivityState.ACTIVE);
			context.record("D03.result", result);
			PhantomAssertions.assertTrue(result.successful(), "Cold canonical DEAD must use actual native recovery: " + result);
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.READY, f.transactions.load(f.id).state().state(), "Cold recovery/store READY.");
			PhantomAssertions.assertTrue(f.transactions.load(f.id).state().vitals().currentHp() > 0, "Stock native revive retained actual HP.");
		}
	}
}
