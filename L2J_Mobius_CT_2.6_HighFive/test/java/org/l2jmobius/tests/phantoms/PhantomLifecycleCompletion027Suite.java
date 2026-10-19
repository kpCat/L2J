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
		if (focus.equals("career061"))
		{
			registry.add("C60-transferred-native-actor-lease-context", this::transferredLease061);
			registry.add("C61-owned-native-combat-stock-stop", this::careerOwnedStop061);
			return;
		}
		if (focus.equals("capacity049"))
		{
			registry.add("S06-post-store-capacity-retains-recovery-before-stop", this::capacityRecovery);
			registry.add("S07-accepted-capacity-recovery-drains-before-pools", this::capacityControlStop);
			return;
		}
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
	private void capacityRecovery(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true); var other = _handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
		{
			f.removeCatchup(); other.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Original native owner.");
			final long epoch = f.materialization.find(f.id).orElseThrow().materializedAtNanos();
			final var player = World.getInstance().getPlayer(f.objectId);
			player.setHeading(3210);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Recovery's original native store completes before rematerialization.");
			final var saved = f.transactions.load(f.id);
			PhantomAssertions.assertTrue(saved.successful() && saved.state().state() == PhantomBackgroundState.State.READY, "Original checkpoint must be verified READY.");
			PhantomAssertions.assertEquals(3210, saved.state().position().heading(), "Actual native change survives store.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(other.id).status(), "Another ordinary owner takes the sole slot after original store.");
			final var boundary = PhantomBackgroundService.class.getDeclaredMethod("finishRecoveryStore", long.class, int.class, boolean.class, boolean.class, boolean.class,
				Class.forName("org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService$NativeRecoveryHandoff"), long.class);
			boundary.setAccessible(true);
			try
			{
				final var pending = (PhantomBackgroundService.OperationResult) boundary.invoke(f.background, f.id, f.objectId, true, false, false, null, epoch);
				context.record("S06.capacity", pending);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.RETRY, pending.status(), "RED049: verified store plus temporary capacity is pending recovery, not permanent INCONSISTENT.");
				PhantomAssertions.assertEquals(saved, f.transactions.load(f.id), "Capacity cannot change earned checkpoint or receipt.");
				PhantomAssertions.assertTrue(f.materialization.find(f.id).isEmpty() && f.materialization.find(other.id).isPresent(), "Capacity cannot displace another owner or invent a successful rematerialization.");
				final var invalid = (PhantomBackgroundService.OperationResult) boundary.invoke(f.background, f.id, other.objectId, true, false, false, null, epoch);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.INCONSISTENT, invalid.status(), "Negative049: failed exact-owner verification remains terminal, even while capacity is occupied.");
				PhantomAssertions.assertEquals(saved, f.transactions.load(f.id), "Failed verification cannot rewrite the original save.");
				final var stopping = (PhantomBackgroundService.OperationResult) boundary.invoke(f.background, f.id, f.objectId, true, false, true, null, epoch);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, stopping.status(), "Accepted post-store recovery finishes during stop without opening a new lifetime.");
				PhantomAssertions.assertEquals(saved, f.transactions.load(f.id), "Stop cannot erase or rewrite original earned save.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(other.id).status(), "Competing owner releases normally.");
				final var resumed = (PhantomBackgroundService.OperationResult) boundary.invoke(f.background, f.id, f.objectId, true, false, false, null, epoch);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, resumed.status(), "Same verified recovery resumes when capacity becomes available.");
				PhantomAssertions.assertTrue(f.materialization.find(f.id).orElseThrow().materializedAtNanos() != epoch, "Success requires a real new native lifetime.");
				PhantomAssertions.assertEquals(3210, World.getInstance().getPlayer(f.objectId).getHeading(), "Resumed native actor retains the earned checkpoint.");
			}
			finally { if (f.materialization.find(other.id).isPresent()) { f.materialization.dematerialize(other.id); } }
		}
	}
	private void capacityControlStop(PhantomTestContext context) throws Exception
	{
		try (var f = _handoff.new Fixture(true); var other = _handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
		{
			f.removeCatchup(); other.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Original accepted recovery lifetime.");
			final var original = f.materialization.find(f.id).orElseThrow();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			World.getInstance().getPlayer(f.objectId).setHeading(4321);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Real accepted recovery store.");
			final var saved = f.transactions.load(f.id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(other.id).status(), "Ordinary admission occupies the released slot.");
			// Reuse the existing reflection fixture pattern at the captured post-store boundary.
			final var controlType = Class.forName("org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService$RecoveryControl");
			final var constructor = controlType.getDeclaredConstructor(long.class, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal.class, PhantomActivityState.class,
				java.util.function.BooleanSupplier.class, PhantomMaterializationService.MaterializationSnapshot.class);
			constructor.setAccessible(true);
			final var control = constructor.newInstance(f.id, goal, PhantomActivityState.ACTIVE, (java.util.function.BooleanSupplier) () -> false, original);
			for (String name : List.of("storeRequested", "restoreExisting")) { final var field = controlType.getDeclaredField(name); field.setAccessible(true); field.setBoolean(control, true); }
			final var controlsField = PhantomBackgroundService.class.getDeclaredField("_recoveryControls"); controlsField.setAccessible(true);
			@SuppressWarnings("unchecked") final var controls = (java.util.Map<Long, Object>) controlsField.get(f.background);
			final var recoveriesField = PhantomBackgroundService.class.getDeclaredField("_recoveries"); recoveriesField.setAccessible(true);
			@SuppressWarnings("unchecked") final var recoveries = (java.util.Map<Long, Boolean>) recoveriesField.get(f.background);
			final var operationsField = PhantomBackgroundService.class.getDeclaredField("_currentOperations"); operationsField.setAccessible(true);
			controls.put(f.id, control); recoveries.put(f.id, true); ((AtomicInteger) operationsField.get(f.background)).incrementAndGet();
			final var advance = PhantomBackgroundService.class.getDeclaredMethod("advanceRecovery", controlType); advance.setAccessible(true); advance.invoke(f.background, control);
			context.record("S07.pending", f.background.materializationQuiescence());
			PhantomAssertions.assertTrue(f.background.firstRecoveryFailure().isEmpty() && !f.background.materializationQuiescence().ready() && f.background.snapshot().currentOperations() == 1,
				"RED049: actual accepted control must remain pending after capacity rejection, with no permanent failure.");
			PhantomAssertions.assertEquals(saved, f.transactions.load(f.id), "Pending control retains original checkpoint and receipt.");
			final var system = configure(f);
			PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Actual PhantomSystem shutdown settles accepted recovery before draining players.");
			PhantomAssertions.assertEquals(PhantomSystem.StopPhase.DONE, system.shutdownProgress().phase(), "Typed DONE is required.");
			PhantomAssertions.assertEquals(PhantomSystem.StopOutcome.COMPLETE, system.shutdownProgress().outcome(), "Typed COMPLETE is required.");
			PhantomAssertions.assertTrue(f.background.firstRecoveryFailure().isEmpty() && f.background.snapshot().currentOperations() == 0 && f.materialization.snapshot().materializations().isEmpty(), "Completed stop has no accepted recovery or retained native owner.");
			PhantomAssertions.assertEquals(saved, f.transactions.load(f.id), "Completed stop cannot erase the original earned store.");
			final var poolField = ThreadPool.class.getDeclaredField("SCHEDULED_POOL"); poolField.setAccessible(true);
			PhantomAssertions.assertFalse(((java.util.concurrent.ScheduledThreadPoolExecutor) poolField.get(null)).isShutdown(), "Typed completion precedes pool shutdown.");
			context.record("S07.done", system.shutdownProgress());
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

	private void transferredLease061(PhantomTestContext context) throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(_handoff);
		try (var f = _handoff.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Transferred lease fixture did not materialize.");
			final var player = World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var before = PlayerNativeWork.current(scope);
			final var backend = new org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend(f.materialization, production::knowledge, production::progression);
			final var lease = backend.tryAcquireActor(f.id);
			PhantomAssertions.assertTrue(lease != null, "Transferred native lease is absent.");
			try
			{
				context.record("career061.transferredLease", "callerContextRetained=" + (PlayerNativeWork.current(scope) != before) + ";owner=" + scope.snapshot());
				PhantomAssertions.assertTrue(PlayerNativeWork.current(scope) == before, "A transferred Combat lease retained a thread-local native ACTION on its caller.");
				final var failure = new AtomicReference<Throwable>(); final var done = new java.util.concurrent.CountDownLatch(1);
				ThreadPool.executeOrThrow(() -> { try { lease.close(); } catch (Throwable thrown) { failure.set(thrown); } finally { done.countDown(); } });
				PhantomAssertions.assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS) && failure.get() == null, "Transferred Combat lease failed to close on its actual worker: " + failure.get());
				PhantomAssertions.assertTrue(PlayerNativeWork.current(scope) == before && scope.outstanding() == 0, "Transferred lease leaked its caller context or native ticket.");
			}
			finally { lease.close(); }
		}
	}

	private void careerOwnedStop061(PhantomTestContext context) throws Exception
	{
		final var environmentField = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); environmentField.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) environmentField.get(_handoff);
		final var productionField = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); productionField.setAccessible(true);
		final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) productionField.get(_handoff);
		final Player setup = Player.load(environment.primary().objectId());
		try
		{
			// Independent finite TEST input before any measured combat/Stop, never ordinary058a progress.
			setup.getStat().setExp(setup.getStat().getExpForLevel(20)); setup.getStat().setLevel((byte) 20); setup.setSp(0); setup.rewardSkills();
			for (int item : new int[] {129, 352, 380, 2412, 61, 2428}) { setup.getInventory().equipItem(setup.getInventory().addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD, item, 1, null, this)); }
			setup.getInventory().addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD, 1463, 200, null, this);
			setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.storeMe();
		}
		finally { environment.cleanupLoadedPlayer(setup); }
		try (var f = _handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), false, "generated.farm.187426bb30110951b41e0ba9.anchor", 21208))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Owned native stop input did not materialize.");
			final var player = World.getInstance().getPlayer(f.objectId); player.setInvul(true);
			PhantomAssertions.assertTrue(player.hasOwnedStoreBoundary(), "This composed stop must use the actual background owned-store boundary.");
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var combat = new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService(new org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend(f.materialization, () -> production.knowledge(), () -> production.progression()),
				org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.fromProgression(production::progression), org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.productionDefaults(1));
			combat.start();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ServiceState.RUNNING, combat.snapshot().state(), "Owned stop Combat did not start.");
			final var system = configure(f);
			final var combatField = PhantomSystem.class.getDeclaredField("_combatService"); combatField.setAccessible(true); combatField.set(system, combat);
			final var monster = new Monster(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(20042));
			monster.spawnMe(player.getX() + 40, player.getY(), player.getZ());
			try
			{
				final long exp = player.getExp();
				final var started = combat.startContentSession(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest(f.id, monster.getObjectId(), org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, true, false, 120_000, () -> false), "career061.owned_stop");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus.ACCEPTED, started.status(), "Owned native Combat was not admitted.");
				final long deadline = System.nanoTime() + 30_000_000_000L;
				while ((!monster.isDead() || player.getExp() <= exp) && System.nanoTime() < deadline) { Thread.sleep(20); }
				PhantomAssertions.assertTrue(monster.isDead() && player.getExp() > exp, "Owned stop input has no genuine native kill/reward.");
				context.record("career061.ownedStopBefore", "exp=" + exp + "->" + player.getExp() + ";owner=" + scope.snapshot());
				boolean stopped = PhantomSystem.shutdownIfStarted();
				if (!stopped)
				{
					final long controlDeadline = System.nanoTime() + 12_000_000_000L; // Observe the existing10s attempt, never renew it.
					while (System.nanoTime() < controlDeadline)
					{
						final var progress = system.shutdownProgress();
						if (progress != null && progress.outcome() != PhantomSystem.StopOutcome.PENDING) { stopped = progress.outcome() == PhantomSystem.StopOutcome.COMPLETE; break; }
						Thread.sleep(10);
					}
				}
				context.record("career061.ownedStop", "result=" + stopped + ";progress=" + system.shutdownProgress() + ";blockers=" + f.materialization.cleanupBlockers() + ";owner=" + scope.snapshot());
				PhantomAssertions.assertTrue(stopped, "Actual owned background/native combat Stop did not complete.");
				PhantomAssertions.assertEquals(player.getExp(), f.transactions.load(f.id).state().progress().experience(), "Owned normal Stop lost real earned EXP.");
			}
			finally { if (monster.isSpawned()) { monster.deleteMe(); } }
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
