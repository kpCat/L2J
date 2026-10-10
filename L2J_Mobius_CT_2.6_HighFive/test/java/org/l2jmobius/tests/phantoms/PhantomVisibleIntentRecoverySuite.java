/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;

/** Current-intent contracts on the existing composed native fixture. */
public final class PhantomVisibleIntentRecoverySuite implements PhantomTestSuite
{
	final PhantomNativeContextHandoffSuite handoff = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("visible-intent-recovery", new PhantomVisibleIntentRecoverySuite(), new PhantomTestContext(21002101, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "visible-intent-recovery"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { handoff.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { handoff.afterAll(context); }

	@Override public void register(PhantomTestRegistry registry)
	{
		if ("completedAuthority031".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("R11-complete-old-authority-renews-only-after-current-owned-checkpoint", context -> completedAuthority(context, true));
			registry.add("R12-unfinished-handoff-never-renews-authority", context -> completedAuthority(context, false)); return;
		}
		registry.add("R01-attach-before-current-publication-reloads-before-work", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				complete(f);
				final var initial = f.goals.load(f.id).orElseThrow(); f.goals.replace(f.id, initial.rowVersion(), revision(initial.goal(), 28));
				final var initialCatchup = f.catchups.load(f.id).orElseThrow(); f.catchups.replace(f.id, initialCatchup, initialCatchup.state().withPlan(initial.goal().goalId(), 28, 28, initialCatchup.state().planIdentity(), 1, 1));
				final var engine = engine(f);
				try
				{
					final var old = f.goals.load(f.id).orElseThrow();
					final var current = revision(old.goal(), 29);
					final var catchup = f.catchups.load(f.id).orElseThrow(); f.catchups.replacePlan(f.id, catchup, catchup.state().withPlan(current.goalId(), 29, 29, catchup.state().planIdentity(), 1, 1), old, current);
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Ordinary current native load.");
					PhantomAssertions.assertTrue(prepare(f, engine), "Pre-work sync admitted.");
					PhantomAssertions.assertEquals(29L, engine.find(f.id).orElseThrow().goalRevision(), "RED: current persisted revision must replace attach-time runtime before work.");
					PhantomAssertions.assertEquals(current, f.goals.load(f.id).orElseThrow().goal(), "No durable rollback.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("R02-busy-sync-defers-stale-action", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				complete(f); final var engine = engine(f);
				try
				{
					final var old = f.goals.load(f.id).orElseThrow(); f.goals.replace(f.id, old.rowVersion(), revision(old.goal(), 29));
					f.materialization.materialize(f.id);
					busy(engine, f.id, true);
					try { PhantomAssertions.assertFalse(prepare(f, engine), "Busy pre-work stays closed."); PhantomAssertions.assertEquals(0L, engine.find(f.id).orElseThrow().goalRevision(), "No stale work or forced reload."); }
					finally { busy(engine, f.id, false); }
					PhantomAssertions.assertTrue(prepare(f, engine), "Next free boundary synchronizes."); PhantomAssertions.assertEquals(29L, engine.find(f.id).orElseThrow().goalRevision(), "Current revision after busy.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("R04-stale-epoch-and-goal-failure-rejected", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); final var goal = f.goals.load(f.id).orElseThrow().goal(); final var player = World.getInstance().getPlayer(f.objectId);
				for (var failure : java.util.List.of(
					new PhantomVisibleFarmTravel.Failure(f.id, player, f.loadedEpoch + 1, goal, "", "travel.native_segment_water_entry", PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE, 1, 1),
					new PhantomVisibleFarmTravel.Failure(f.id, player, f.loadedEpoch, revision(goal, goal.revision() + 1), "", "travel.native_segment_water_entry", PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE, 1, 1)))
				{ PhantomAssertions.assertFalse(f.historical.recordVisibleTravelFailure(f.id, failure), "Foreign failure cannot exclude current target."); }
			}
		});
		registry.add("R06-pending-native-owner-local-replacement-is-atomic", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); placeAtAlternativeFactualAnchor(f, context); final var engine = engine(f);
				try
				{
					final var before = f.catchups.load(f.id).orElseThrow(); final var goal = f.goals.load(f.id).orElseThrow().goal();
					f.historical.recordVisibleFailure(f.id, goal, "");
					prepare(f, engine);
					final var after = f.catchups.load(f.id).orElseThrow(); final var next = f.goals.load(f.id).orElseThrow();
					PhantomAssertions.assertEquals(goal.revision() + 1, next.goal().revision(), "RED: exact foreground owner must atomically replace failed local intent.");
					PhantomAssertions.assertEquals(next.goal().revision(), after.state().goalRevision(), "Catchup/goal coherence.");
					immutable(before.state(), after.state());
					PhantomAssertions.assertTrue(f.historical.permitsDecision(f.id), "Exact permit rebind.");
					prepare(f, engine);
					PhantomAssertions.assertEquals(next.rowVersion(), engine.find(f.id).orElseThrow().componentRowVersion(), "Runtime projection.");
					PhantomAssertions.assertEquals(next, f.goals.load(f.id).orElseThrow(), "Receipt idempotence.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("R07-historical-and-unowned-pending-stay-fenced", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				final var engine = engine(f);
				try { PhantomAssertions.assertFalse(prepare(f, engine), "Pending without native handoff."); }
				finally { stop(engine); }
			}
		});
		registry.add("R08-foreign-goal-write-never-rebinds-permit", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); final var engine = engine(f);
				try
				{
					final var goal = f.goals.load(f.id).orElseThrow(); f.goals.replace(f.id, goal.rowVersion(), revision(goal.goal(), goal.goal().revision() + 1));
					PhantomAssertions.assertFalse(prepare(f, engine), "Foreign write fenced.");
					PhantomAssertions.assertFalse(f.historical.permitsDecision(f.id), "No implicit grant.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("R11-failed-catchup-local-plan-keeps-status-and-cursor", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				final var original = f.catchups.load(f.id).orElseThrow(); f.catchups.replace(f.id, original, original.state().failed("retained.history.failure"));
				f.handoff(); placeAtAlternativeFactualAnchor(f, context); final var engine = engine(f);
				try
				{
					final var before = f.catchups.load(f.id).orElseThrow().state(); f.historical.recordVisibleFailure(f.id, f.goals.load(f.id).orElseThrow().goal(), ""); prepare(f, engine);
					final var after = f.catchups.load(f.id).orElseThrow().state();
					PhantomAssertions.assertEquals(before.planOrdinal() + 1, after.planOrdinal(), "RED: foreground failed plan replaced once."); immutable(before, after);
				}
				finally { stop(engine); }
			}
		});
		registry.add("R08-atomic-CAS-conflict-has-no-partial-goal", _ ->
		{
			try (var f = handoff.new Fixture(true))
			{
				final var before = f.catchups.load(f.id).orElseThrow(); final var goal = f.goals.load(f.id).orElseThrow();
				final var foreign = f.catchups.replace(f.id, before, before.state().running());
				PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> f.catchups.replacePlan(f.id, before, before.state().withPlan(goal.goal().goalId(), goal.goal().revision() + 1, 1, "b".repeat(64), 1, 1), goal, revision(goal.goal(), goal.goal().revision() + 1)), "Stale catchup CAS rejected.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id).orElseThrow(), "Goal unchanged despite valid goal CAS.");
				PhantomAssertions.assertEquals(foreign, f.catchups.load(f.id).orElseThrow(), "Claim unchanged by failed atomic transaction.");
			}
		});
		registry.add("R09-commit-then-load-failure-is-exactly-retryable", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); placeAtAlternativeFactualAnchor(f, context); final var store = new ReloadStore(f); final var engine = engine(f, store);
				try
				{
					final var old = f.goals.load(f.id).orElseThrow(); f.historical.recordVisibleFailure(f.id, old.goal(), ""); store.fail = true;
					PhantomAssertions.assertFalse(prepare(f, engine), "Commit with failed runtime projection stays closed.");
					final var committed = f.goals.load(f.id).orElseThrow(); PhantomAssertions.assertEquals(old.goal().revision() + 1, committed.goal().revision(), "Durable CAS committed before reload.");
					PhantomAssertions.assertFalse(f.historical.permitsDecision(f.id), "Completion receipt closes native decisions.");
					store.fail = false; PhantomAssertions.assertTrue(prepare(f, engine), "Exact receipt resumes publication.");
					PhantomAssertions.assertEquals(committed, f.goals.load(f.id).orElseThrow(), "No second goal mutation on retry.");
					PhantomAssertions.assertEquals(committed.goal().revision(), engine.find(f.id).orElseThrow().goalRevision(), "Current runtime projection.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("R10-cleanup-between-CAS-and-projection-never-grants", context ->
		{
			try (var f = handoff.new Fixture(true))
			{
				f.handoff(); placeAtAlternativeFactualAnchor(f, context); final var store = new ReloadStore(f); final var engine = engine(f, store);
				try
				{
					f.historical.recordVisibleFailure(f.id, f.goals.load(f.id).orElseThrow().goal(), "");
					store.onLoad = () -> f.historical.beforeStore(f.id, World.getInstance().getPlayer(f.objectId));
					PhantomAssertions.assertFalse(prepare(f, engine), "Cleanup invalidates receipt."); PhantomAssertions.assertFalse(f.historical.permitsDecision(f.id), "No handoff resurrection after cleanup.");
				}
				finally { stop(engine); }
			}
		});
	}

	private void completedAuthority(PhantomTestContext context, boolean completed) throws Exception
	{
		final boolean priorLootSlot = org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_SLOT_LIMIT;
		try (var f = handoff.new Fixture(true))
		{
			if (completed) { complete(f); }
			final var before = f.catchups.load(f.id).orElseThrow();
			final var oldGoal = f.goals.load(f.id).orElseThrow();
			if (completed) { PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Ordinary native load for completed catchup."); }
			else { f.handoff(); }
			final var player = World.getInstance().getPlayer(f.objectId);
			final var progress = new PhantomBackgroundState.Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
			final long epoch = f.materialization.find(f.id).orElseThrow().materializedAtNanos();
			final long nativeVersion = f.transactions.nativeContext(f.id, f.objectId).context().stateRowVersion();
			final var engine = engine(f);
			try
			{
				// One isolated TEST JVM changes actual authority policy, without altering persisted native facts.
				org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_SLOT_LIMIT = !priorLootSlot;
				boolean admitted = false;
				final long deadline = System.nanoTime() + 15_000_000_000L;
				do { admitted = prepare(f, engine); if (!admitted) { Thread.sleep(25); } }
				while (!admitted && completed && System.nanoTime() < deadline);
				final var after = f.catchups.load(f.id).orElseThrow();
				if (!completed)
				{
					PhantomAssertions.assertFalse(admitted, "Unfinished old-authority handoff remains fenced.");
					PhantomAssertions.assertEquals(before, after, "No unfinished cursor or authority laundering.");
					PhantomAssertions.assertEquals(oldGoal, f.goals.load(f.id).orElseThrow(), "No unfinished plan publication."); return;
				}
				PhantomAssertions.assertTrue(admitted, "RED: completed old authority must renew via actual current owned checkpoint.");
				final var proof = f.transactions.nativeContext(f.id, f.objectId);
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, proof.status(), "Canonical native/state/context proof.");
				PhantomAssertions.assertEquals(PhantomNativeContext.Phase.COMPLETED, proof.context().phase(), "Completed owned native receipt.");
				PhantomAssertions.assertTrue(proof.context().stateRowVersion() > nativeVersion, "Actual owned checkpoint advances native/state/context binding.");
				PhantomAssertions.assertEquals(proof.state().hashes(), after.state().authorityHashes(), "Authority renewal follows native checkpoint hashes.");
				PhantomAssertions.assertFalse(before.state().authorityHashes().equals(after.state().authorityHashes()), "Actual authority change observed.");
				PhantomAssertions.assertEquals(java.util.List.of(before.state().status(), before.state().requestId(), before.state().fromEpochMinute(), before.state().targetEpochMinute(), before.state().cursorEpochMinute(), before.state().intervalOrdinal(), before.state().generation()), java.util.List.of(after.state().status(), after.state().requestId(), after.state().fromEpochMinute(), after.state().targetEpochMinute(), after.state().cursorEpochMinute(), after.state().intervalOrdinal(), after.state().generation()), "Completed interval and reward cursor unchanged.");
				PhantomAssertions.assertEquals(progress, proof.state().progress(), "No synthetic rewards during authority renewal.");
				PhantomAssertions.assertEquals(f.baseline.inventory(), proof.state().inventory(), "Whole native inventory preserved.");
				PhantomAssertions.assertEquals(epoch, f.materialization.find(f.id).orElseThrow().materializedAtNanos(), "No lifetime replacement.");
				PhantomAssertions.assertEquals(oldGoal.goal().revision() + 1, after.state().goalRevision(), "One atomic current plan revision.");
				context.record("completedAuthority.currentEpoch", epoch);
			}
			finally { org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_SLOT_LIMIT = priorLootSlot; stop(engine); }
		}
		finally { org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_SLOT_LIMIT = priorLootSlot; }
	}
	static void busy(PhantomDecisionEngine engine, long id, boolean value) throws Exception
	{
		final var slots = PhantomDecisionEngine.class.getDeclaredField("_slots"); slots.setAccessible(true); final Object slot = ((Map<?, ?>) slots.get(engine)).get(id);
		final var inFlight = slot.getClass().getDeclaredField("_inFlight"); inFlight.setAccessible(true); inFlight.setBoolean(slot, value);
	}
	static final class ReloadStore implements PhantomGoalStore
	{
		final PhantomNativeContextHandoffSuite.Fixture f; boolean fail; Runnable onLoad = () -> {};
		ReloadStore(PhantomNativeContextHandoffSuite.Fixture f) { this.f = f; }
		@Override public boolean profileExists(long id) { return f.goals.profileExists(id); }
		@Override public java.util.Optional<StoredGoal> load(long id) { onLoad.run(); if (fail) { throw new IllegalStateException("Injected reload IO failure"); } return f.goals.load(id); }
		@Override public StoredGoal insert(long id, PhantomGoal goal) { return f.goals.insert(id, goal); }
		@Override public StoredGoal replace(long id, long version, PhantomGoal goal) { return f.goals.replace(id, version, goal); }
		@Override public void delete(long id, long version) { f.goals.delete(id, version); }
	}

	static boolean prepare(PhantomNativeContextHandoffSuite.Fixture f, PhantomDecisionEngine engine) throws Exception
	{
		try { return (boolean) f.historical.getClass().getMethod("prepareVisibleDecision", long.class, PhantomDecisionEngine.class).invoke(f.historical, f.id, engine); }
		catch (NoSuchMethodException beforeFix) { return f.historical.permitsDecision(f.id); }
	}
	private void placeAtAlternativeFactualAnchor(PhantomNativeContextHandoffSuite.Fixture f, PhantomTestContext context) throws Exception
	{
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_production"); field.setAccessible(true);
		final var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) field.get(handoff);
		final var goal = f.goals.load(f.id).orElseThrow().goal(); final var old = PhantomBackgroundGoalSpec.parse(goal);
		final var query = new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery(1, 3, 1, null, null, Set.of(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER), true, true, null, null, null, org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest.first(64));
		for (var target : production.knowledge().suitableTargets(query).values())
		{
			for (var area : target.representativeAreas())
			{
				if (area.topologyNodeId() == null) { continue; }
				for (var anchor : production.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), java.util.List.of()))
				{
					if (anchor.role() != org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING || anchor.point().instanceId() != 0 || (anchor.npcId() != null && anchor.npcId() != target.npc().npcId()) || (target.npc().npcId() == old.npcId() && anchor.id().equals(old.anchorId()))) { continue; }
					// Controlled TEST fixture position only; connected actors are never moved by TASK021.
					final var point = anchor.point(); World.getInstance().getPlayer(f.objectId).setXYZ(point.x(), point.y(), point.z());
					context.record("atomic.fixture.live", anchor.id()); return;
				}
			}
		}
		throw new AssertionError("No factual alternative TEST anchor.");
	}
	static PhantomDecisionEngine engine(PhantomNativeContextHandoffSuite.Fixture f)
	{
		return engine(f, f.goals);
	}
	static PhantomDecisionEngine engine(PhantomNativeContextHandoffSuite.Fixture f, PhantomGoalStore store)
	{
		final var adapter = new PhantomBackgroundDecision(f.background);
		final var candidates = new PhantomCandidateRegistry(); adapter.registerCandidates(candidates); candidates.seal();
		final var handlers = new PhantomStepHandlerRegistry(); adapter.registerHandlers(handlers); handlers.seal();
		final var engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 1, null, f.historical::permitsDecision);
		engine.start(); engine.attach(f.id); return engine;
	}
	static void stop(PhantomDecisionEngine engine) { engine.beginStop(); PhantomAssertions.assertTrue(engine.finishStop(), "Decision stopped."); }
	static void complete(PhantomNativeContextHandoffSuite.Fixture f)
	{
		final var before = f.catchups.load(f.id).orElseThrow(); f.catchups.replace(f.id, before, before.state().running().advanceTo(f.target));
	}
	static PhantomGoal revision(PhantomGoal g, long revision)
	{
		return new PhantomGoal(g.goalId(), g.goalType(), g.status(), g.subject(), g.target(), g.requiredAmount(), g.currentAmount(), g.acquisitionMethod(), g.validSources(), g.selectedAnchor(), g.purposeKey(), g.priority(), g.riskBudget(), g.expenseBudget(), g.deadlineEpochMillis(), g.constraints(), g.reasonKey(), revision, g.payloadText());
	}
	static void immutable(PhantomBackgroundCatchupState a, PhantomBackgroundCatchupState b)
	{
		PhantomAssertions.assertEquals(java.util.List.of(a.status(), a.requestId(), a.deterministicSeed(), a.fromEpochMinute(), a.targetEpochMinute(), a.cursorEpochMinute(), a.intervalOrdinal(), a.generation(), a.knowledgeGeneration(), a.topologyGeneration(), a.modelVersion(), a.authorityHashes(), a.failureReason()), java.util.List.of(b.status(), b.requestId(), b.deterministicSeed(), b.fromEpochMinute(), b.targetEpochMinute(), b.cursorEpochMinute(), b.intervalOrdinal(), b.generation(), b.knowledgeGeneration(), b.topologyGeneration(), b.modelVersion(), b.authorityHashes(), b.failureReason()), "No historical progression/rewards/status mutation.");
	}
}
