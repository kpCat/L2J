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
	private static org.l2jmobius.gameserver.model.script.Quest quest(PhantomTestContext context) throws Exception
	{
		if (org.l2jmobius.gameserver.managers.ScriptManager.getInstance().getScript("M1TimerBootstrap") == null)
		{
			org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(context.moduleRoot().resolve("test/resources/phantoms/M1TimerBootstrap.java"));
		}
		final var quest = org.l2jmobius.gameserver.managers.ScriptManager.getInstance().getScript("M1TimerBootstrap");
		PhantomAssertions.assertTrue(quest != null, "INVALID: actual ScriptEngine Quest bootstrap is missing.");
		return quest;
	}
	@Override public void register(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.m1.native.focus", "all");
		final boolean acceptance = focus.equals("acceptance");
		if (acceptance || focus.equals("all") || focus.equals("callback")) { registry.add("S02-native-delayed-kill-child-before-system-store", this::callback); }
		if (acceptance || focus.equals("all") || focus.equals("death")) { registry.add("D05-accepted-queued-native-return-survives-close", this::queuedDeath); }
		if (acceptance || focus.equals("all") || focus.equals("cold")) { registry.add("D03-cold-canonical-dead-native-recovery", this::coldDead); }
		if (acceptance || focus.equals("dispatch")) { registry.add("E01-executor-entry-before-owner-monitor", this::dispatch); }
		if (acceptance || focus.equals("timeout")) { registry.add("S05-single-deadline-retains-original-running-native-callback", this::timeout); }
		if (acceptance || focus.equals("recovery")) { registry.add("S03-native-recovery-control-does-not-drain-own-callback", this::recoveryControl); }
		if (acceptance || focus.equals("preflight")) { registry.add("S03-cold-stale-native-preflight-is-control-continuation", this::recoveryPreflight); }
		if (acceptance || focus.equals("handoff-death")) { registry.add("D04-exact-pending-handoff-survives-native-death-return", this::handoffDeath); }
		if (acceptance || focus.equals("cold-handoff")) { registry.add("D04-cold-dead-keeps-exact-pending-handoff", this::coldHandoff); }
		if (acceptance || focus.equals("real-death"))
		{
			registry.add("D01-real-45s-native-death-return-and-new-epoch", this::realDeathReturn);
			registry.add("D02-secondary-native-revive-does-not-return-twice", this::secondaryRevive);
		}
	}
	private void recoveryPreflight(PhantomTestContext context) throws Exception
	{
		final var productionField = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); productionField.setAccessible(true);
		final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) productionField.get(_handoff);
		try (var f = _handoff.new Fixture(true, true))
		{
			PhantomVisibleIntentRecoverySuite.complete(f);
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var claim = f.catchups.load(f.id).orElseThrow();
			final var navigation = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend(), worker -> { ThreadPool.execute(worker); return true; }, System::nanoTime, new org.l2jmobius.gameserver.phantoms.PhantomMetrics());
			final var signalsField = PhantomNativeContextHandoffSuite.class.getDeclaredMethod("signals"); signalsField.setAccessible(true);
			final var signals = (org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort) signalsField.invoke(null);
			final var travel = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel(f.materialization, f.background, production.authority().travelQuery(production.topology()), navigation, f.historical::permitsDecision, signals);
			final var autoPlay = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay(f.materialization, () -> null, f.historical::permitsDecision);
			final var adapter = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision.bindVisibleLife(f.background, travel, autoPlay, f.historical, () -> null);
			final var handlers = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry(); adapter.registerHandlers(handlers); handlers.seal();
			final var spec = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal);
			final var source = goal.validSources().stream().filter(value -> value.namespace().equals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.SOURCE_NAMESPACE) && value.key().equals(spec.npcId() + "@" + spec.anchorId())).findFirst().orElseThrow();
			final var step = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep(0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.RECOVER_ACTION, source, java.util.Map.of("npc", (long) spec.npcId()), 5000, 2, "background.recover.explicit");
			final var plan = new org.l2jmobius.gameserver.phantoms.decision.PhantomPlan(1, goal.goalId(), org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.CANDIDATE_KEY, List.of(step), 5000, System.nanoTime());
			final var request = new org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext(f.id, goal, plan, step, PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
			final var entered = new java.util.concurrent.CountDownLatch(1); final var release = new java.util.concurrent.CountDownLatch(1); final var done = new java.util.concurrent.CountDownLatch(1);
			final var elapsed = new AtomicLong(); final var result = new AtomicReference<org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult>(); final var error = new AtomicReference<Throwable>();
			f.afterHistoricalLoad = () ->
			{
				entered.countDown(); ThreadPool.schedule(release::countDown, 350);
				try { if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) { throw new IllegalStateException("TEST027_PREFLIGHT_RELEASE"); } }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
			};
			ThreadPool.schedule(() ->
			{
				final long started = System.nanoTime();
				try { result.set(handlers.snapshot().get(step.actionKey()).execute(request)); }
				catch (Throwable failure) { error.set(failure); }
				finally { elapsed.set(System.nanoTime() - started); done.countDown(); }
			}, 10);
			try
			{
				PhantomAssertions.assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS) && error.get() == null && entered.await(4, java.util.concurrent.TimeUnit.SECONDS), "INVALID S03: real cold native baseline load must be accepted: " + error);
				context.record("S03.preflight", "elapsed=" + elapsed + ";result=" + result + ";loadedEpoch=" + f.loadedEpoch);
				PhantomAssertions.assertTrue(elapsed.get() < 100_000_000L, "RED S03 preflight: scheduled decision caller waited for accepted native baseline load/drain.");
				final long deadline = System.nanoTime() + 8_000_000_000L;
				while (System.nanoTime() < deadline && (f.background.materializationQuiescence().operations() != 0 || f.materialization.find(f.id).isPresent())) { Thread.sleep(10); }
				PhantomAssertions.assertTrue(f.background.firstRecoveryFailure().isEmpty() && f.materialization.find(f.id).isEmpty() && f.transactions.load(f.id).state().vitals().currentHp() > 0, "Cold baseline control must drain and finish the actual native return.");
				PhantomAssertions.assertEquals(claim, f.catchups.load(f.id).orElseThrow(), "Native preflight cannot replace or complete the exact catch-up claim.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow().goal(), "Native preflight cannot replace the goal.");
			}
			finally
			{
				release.countDown();
				final long cleanupDeadline = System.nanoTime() + 10_000_000_000L;
				while (System.nanoTime() < cleanupDeadline && f.background.materializationQuiescence().operations() != 0) { Thread.sleep(10); }
			}
		}
	}
	private void handoffDeath(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.background.installPresencePolicy(id -> id == f.id); f.handoff();
			final var original = World.getInstance().getPlayer(f.objectId); final var owner = original.getNativeWorkOwner();
			final var clock = new AtomicLong(System.nanoTime());
			try (var death = new PhantomOrdinaryDeathRecovery(f.materialization, f.goals, f.background, clock::get))
			{
				PhantomAssertions.assertTrue(death.install(), "Exact native handoff death observation.");
				PlayerNativeWork.run(original, "TEST027_PENDING_HANDOFF_NATIVE_DEATH", () -> original.doDie(null));
				clock.addAndGet(46_000_000_000L); death.pulse();
				final long deadline = System.nanoTime() + 5_000_000_000L;
				while (System.nanoTime() < deadline && !death.drained()) { Thread.sleep(10); }
				final var recovered = World.getInstance().getPlayer(f.objectId);
				context.record("D04.return", "death=" + death.snapshot() + ";old=" + owner.epoch() + ";new=" + (recovered == null ? 0 : recovered.getNativeWorkOwner().epoch()) + ";catchup=" + f.catchups.load(f.id));
				PhantomAssertions.assertTrue(death.drained() && death.snapshot().firstFailure().isEmpty(), "RED D04: exact pending catch-up must complete the accepted native recovery/store.");
				PhantomAssertions.assertTrue(recovered != null && recovered != original && !recovered.isDead() && recovered.getNativeWorkOwner().epoch() != owner.epoch(), "New live epoch retains the exact native handoff.");
				PhantomAssertions.assertTrue(f.catchups.load(f.id).orElseThrow().state().requestId().equals(f.claim), "Original catch-up claim cannot be replaced or completed to bypass admission.");
			}
		}
	}
	private void coldHandoff(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.background.installPresencePolicy(id -> id == f.id); f.handoff();
			final var original = World.getInstance().getPlayer(f.objectId); final long oldEpoch = original.getNativeWorkOwner().epoch();
			PlayerNativeWork.run(original, "TEST027_COLD_PENDING_NATIVE_DEATH", () -> original.doDie(null));
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Original native corpse store.");
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.DEAD, f.transactions.load(f.id).state().state(), "Cold native canonical DEAD.");
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(f.objectId), "No original death memory or Player.");
			final var claim = f.catchups.load(f.id).orElseThrow(); final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var result = f.background.recover(f.id, goal, PhantomActivityState.ACTIVE);
			context.record("D04.cold", "result=" + result + ";claim=" + f.catchups.load(f.id) + ";oldEpoch=" + oldEpoch);
			PhantomAssertions.assertTrue(result.successful(), "RED D04 cold: native recovery must use the exact pending claim, not bypass NORMAL: " + result);
			PhantomAssertions.assertEquals(claim, f.catchups.load(f.id).orElseThrow(), "Cold recovery cannot complete or replace the claim.");
			PhantomAssertions.assertTrue(f.transactions.load(f.id).state().vitals().currentHp() > 0, "Native stock resurrection captured.");
			f.handoff(); final var recovered = World.getInstance().getPlayer(f.objectId);
			PhantomAssertions.assertTrue(recovered != null && !recovered.isDead() && recovered.getNativeWorkOwner().epoch() != oldEpoch && f.historical.permitsDecision(f.id), "Exact cold handoff creates a fresh live decision owner.");
		}
	}
	private void timeout(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native timeout lifetime.");
			final var player = World.getInstance().getPlayer(f.objectId); final var owner = (PhantomNativeWorkScope) player.getNativeWorkOwner(); final var system = configure(f);
			final var npc = new Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(20545));
			final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(npc.getTemplate()); spawn.setXYZ(player.getX() + 40, player.getY(), player.getZ()); npc.setSpawn(spawn); npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			final var entered = new java.util.concurrent.CountDownLatch(1); final var release = new java.util.concurrent.CountDownLatch(1); final var exited = new java.util.concurrent.CountDownLatch(1);
			final var listener = new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(npc, org.l2jmobius.gameserver.model.events.EventType.ON_ATTACKABLE_KILL,
				(org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill event) ->
				{
					entered.countDown();
					try { if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS)) { throw new IllegalStateException("TEST027_ORIGINAL_RELEASE_MISSING"); } player.setHeading((player.getHeading() + 16) % 65536); }
					catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
					finally { exited.countDown(); }
				}, owner);
			npc.addListener(listener);
			try
			{
				PlayerNativeWork.run(player, List.of(npc), "TEST027_ORIGINAL_KILL_TIMEOUT", () -> npc.reduceCurrentHp(npc.getMaxHp() * 2, player, null));
				PhantomAssertions.assertTrue(entered.await(4, java.util.concurrent.TimeUnit.SECONDS), "Original event body entered.");
				final long firstStarted = System.nanoTime();
				PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Genuine deadline cannot be healthy.");
				final var failed = system.shutdownProgress();
				PhantomAssertions.assertTrue(failed.outcome() == PhantomSystem.StopOutcome.FAILED && failed.blocker().startsWith("deadline:") && owner.outstanding() > 0 && owner.isCurrent(), "Original accepted ticket remains retained after bounded failure.");
				ThreadPool.schedule(release::countDown, 500);
				final long repeated = System.nanoTime(); PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "FAILED attempt never becomes healthy."); final long repeatElapsed = System.nanoTime() - repeated;
				context.record("S05.deadline", "firstElapsed=" + (repeated - firstStarted) + ";repeatElapsed=" + repeatElapsed + ";first=" + failed + ";after=" + system.shutdownProgress() + ";owner=" + owner.snapshot());
				PhantomAssertions.assertTrue(repeatElapsed < 100_000_000L && failed.deadlineNanos() == system.shutdownProgress().deadlineNanos(), "RED S05: a repeated failed hook must not start another callback wait window.");
				PhantomAssertions.assertTrue(exited.await(3, java.util.concurrent.TimeUnit.SECONDS), "Original body exits naturally after release.");
			}
			finally { release.countDown(); exited.await(3, java.util.concurrent.TimeUnit.SECONDS); npc.removeListener(listener); npc.deleteMe(); if (PhantomSystem.hasConfiguredInstance()) { PhantomSystem.shutdownIfStarted(); } }
		}
	}
	private void realDeathReturn(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); f.background.installPresencePolicy(id -> id == f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native live actor.");
			final var original = World.getInstance().getPlayer(f.objectId); final var owner = original.getNativeWorkOwner();
			try (var death = new PhantomOrdinaryDeathRecovery(f.materialization, f.goals, f.background))
			{
				PhantomAssertions.assertTrue(death.install(), "Actual priority death pulse.");
				final long started = System.nanoTime();
				PlayerNativeWork.run(original, "TEST027_ACTUAL_NATIVE_DEATH_WINDOW", () -> original.doDie(null));
				Thread.sleep(1000); death.pulse();
				PhantomAssertions.assertTrue(original.isDead() && death.snapshot().observedDeaths() == 1, "Native corpse window preserved.");
				final long deadline = started + 55_000_000_000L;
				while (System.nanoTime() < deadline && (World.getInstance().getPlayer(f.objectId) == original || !death.drained())) { Thread.sleep(100); }
				final var recovered = World.getInstance().getPlayer(f.objectId);
				context.record("D01.return", "elapsed=" + (System.nanoTime() - started) + ";death=" + death.snapshot() + ";old=" + owner.epoch() + ";new=" + (recovered == null ? 0 : recovered.getNativeWorkOwner().epoch()));
				PhantomAssertions.assertTrue(System.nanoTime() - started >= 45_000_000_000L, "Original 45s window cannot be shortened.");
				PhantomAssertions.assertTrue(recovered != null && recovered != original && !recovered.isDead() && recovered.getNativeWorkOwner().epoch() != owner.epoch(), "Exact native recovery/store/rematerialization creates a new live epoch.");
				PhantomAssertions.assertTrue(death.drained() && death.snapshot().firstFailure().isEmpty() && !owner.isCurrent(), "Accepted death completion has no retained original owner or failure.");
			}
		}
	}
	private void secondaryRevive(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); f.background.installPresencePolicy(id -> id == f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Native secondary revival actor.");
			final var player = World.getInstance().getPlayer(f.objectId); final long epoch = player.getNativeWorkOwner().epoch();
			final int x = player.getX(), y = player.getY(); final var clock = new AtomicLong(System.nanoTime());
			try (var death = new PhantomOrdinaryDeathRecovery(f.materialization, f.goals, f.background, clock::get))
			{
				PhantomAssertions.assertTrue(death.install(), "Existing death observation.");
				PlayerNativeWork.run(player, "TEST027_SECONDARY_NATIVE_DEATH", () -> player.doDie(null));
				PlayerNativeWork.run(player, "TEST027_SECONDARY_NATIVE_REVIVE", player::doRevive);
				clock.addAndGet(46_000_000_000L); death.pulse();
				PhantomAssertions.assertTrue(death.snapshot().observedDeaths() == 0 && death.drained() && death.snapshot().firstFailure().isEmpty(), "Secondary revival retires the original return, not another reconcile.");
				PhantomAssertions.assertTrue(!player.isDead() && player.getX() == x && player.getY() == y && player.getNativeWorkOwner().epoch() == epoch, "No second revive/teleport/epoch replacement.");
				context.record("D02.secondary", death.snapshot());
			}
		}
	}
	private void recoveryControl(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup(); f.background.installPresencePolicy(id -> id == f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Original recovery lifetime.");
			final var player = World.getInstance().getPlayer(f.objectId);
			final var original = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final var quest = quest(context);
			final var bodyField = PhantomM1TimerChecks.class.getDeclaredField("QUEST_BODY"); bodyField.setAccessible(true);
			@SuppressWarnings("unchecked") final var body = (AtomicReference<java.util.function.Consumer<Player>>) bodyField.get(null);
			final var result = new AtomicReference<PhantomBackgroundService.OperationResult>();
			final var elapsed = new AtomicLong(); final var done = new java.util.concurrent.CountDownLatch(1);
			PhantomAssertions.assertTrue(body.compareAndSet(null, actor ->
			{
				try { final long now = System.nanoTime(); result.set(f.background.recover(f.id, goal, PhantomActivityState.ACTIVE)); elapsed.set(System.nanoTime() - now); }
				finally { done.countDown(); }
			}), "Exclusive original QuestTimer body.");
			try
			{
				PlayerNativeWork.run(player, "TEST027_NATIVE_DEATH_AND_STOCK_TIMER", () -> { player.doDie(null); quest.startQuestTimer("TASK027_RECOVERY_CONTROL", 150, null, player); });
				PhantomAssertions.assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS), "Original stock callback did not exit.");
				context.record("S03.request", "result=" + result + ";elapsed=" + elapsed + ";owner=" + original.snapshot());
				PhantomAssertions.assertTrue(result.get() != null && result.get().status() == PhantomBackgroundService.OperationStatus.RETRY && elapsed.get() < 100_000_000L, "RED S03: native callback must publish recovery control and return without draining itself.");
				final long deadline = System.nanoTime() + 5_000_000_000L;
				while (System.nanoTime() < deadline && (World.getInstance().getPlayer(f.objectId) == player || !f.background.materializationQuiescence().ready())) { Thread.sleep(10); }
				PhantomAssertions.assertTrue(World.getInstance().getPlayer(f.objectId) != player && original.outstanding() == 0 && original.firstNativeIncident() == null, "Exact original owner released only after callback exit, without incident.");
				PhantomAssertions.assertTrue(f.background.materializationQuiescence().ready() && f.background.firstRecoveryFailure().isEmpty(), "Accepted recovery control must publish its terminal result before fixture teardown.");
				PhantomAssertions.assertTrue(f.transactions.load(f.id).state().vitals().currentHp() > 0, "Native return captured durably.");
			}
			finally { body.set(null); }
		}
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
			final var quest = quest(context);
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
					final long deadline = System.nanoTime() + 5_000_000_000L;
					while (System.nanoTime() < deadline && !death.drained()) { final var continuation = queued.getAndSet(null); if (continuation != null) { continuation.run(); } Thread.sleep(10); }
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
