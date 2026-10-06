/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.localplay.LocalPlayPilotActions;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotProtocol;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Exact foreground permission contracts against the real composed native lifecycle. */
public final class PhantomVisibleDecisionAdmissionSuite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _handoff = new PhantomNativeContextHandoffSuite();
	private PhantomProfileRepository _profiles;

	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("visible-decision-admission", new PhantomVisibleDecisionAdmissionSuite(), new PhantomTestContext(20002001, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "visible-decision-admission"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _handoff.beforeAll(context); _profiles = PhantomProfileRepository.open(); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _handoff.afterAll(context); }

	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("V01-original-admission-retains-sequence-zero", _ ->
		{
			try (var f = _handoff.new Fixture(true))
			{
				f.handoff();
				PhantomAssertions.assertFalse(f.historical.permitsNormalOperation(f.id), "Historical fence.");
				final var engine = engine(f, f.historical::permitsNormalOperation);
				try
				{
					work(engine, f.id);
					final var result = engine.find(f.id).orElseThrow();
					PhantomAssertions.assertEquals(0L, result.decisionSequence(), "Original admission must reproduce TASK019.");
					PhantomAssertions.assertEquals("goal.reloaded", result.reasonKey(), "Original admission must reject before planning.");
				}
				finally { stop(engine); }
			}
		});
		registry.add("V02-exact-handoff-permits-only-decision", _ ->
		{
			try (var f = _handoff.new Fixture()) { f.handoff(); PhantomAssertions.assertFalse(f.historical.permitsNormalOperation(f.id), "NORMAL unchanged."); PhantomAssertions.assertTrue(permit(f), "RED: successful exact foreground handoff must permit decisions."); }
		});
		registry.add("V03-incomplete-without-handoff-fenced", _ -> { try (var f = _handoff.new Fixture()) { PhantomAssertions.assertFalse(permit(f), "Unowned history."); } });
		registry.add("V04-historical-baseline-fenced", _ ->
		{
			try (var f = _handoff.new Fixture()) { f.materialization.materialize(f.id, MaterializationPurpose.HISTORICAL_BASELINE, f.claim); PhantomAssertions.assertFalse(permit(f), "Temporary baseline cannot own decisions."); }
		});
		registry.add("V05-wrong-request-fenced", _ ->
		{
			try (var f = _handoff.new Fixture()) { f.materialization.materialize(f.id, MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, "d".repeat(64)); PhantomAssertions.assertFalse(permit(f), "Wrong request."); }
		});
		registry.add("V06-catchup-row-and-payload-changes-fenced", _ ->
		{
			for (boolean payload : new boolean[] { false, true })
			{
				try (var f = _handoff.new Fixture())
				{
					f.handoff();
					final var component = _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
					final byte[] bytes = payload ? new PhantomBackgroundCatchupStateCodec().encode(f.catchups.load(f.id).orElseThrow().state().running()) : component.payload();
					_profiles.updateComponent(f.id, component.componentType(), component.rowVersion(), component.componentSchemaVersion(), bytes);
					PhantomAssertions.assertFalse(permit(f), "Changed claim row/payload.");
				}
			}
		});
		registry.add("V07-goal-component-change-fenced", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				f.handoff(); final var goal = f.goals.load(f.id).orElseThrow(); f.goals.replace(f.id, goal.rowVersion(), goal.goal());
				PhantomAssertions.assertFalse(permit(f), "Changed goal row even with identical payload.");
			}
		});
		registry.add("V08-absent-stored-failed-materialization-fenced", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				PhantomAssertions.assertFalse(permit(f), "Absent actor."); f.handoff();
				mutateActor(f, "_state", State.STORED); mutateActor(f, "_state", State.FAILED);
			}
		});
		registry.add("V09-world-absence-fenced", _ -> { try (var f = _handoff.new Fixture()) { f.handoff(); mutateActor(f, "_player", null); } });
		registry.add("V10-closed-action-admission-fenced", _ -> { try (var f = _handoff.new Fixture()) { f.handoff(); mutateActor(f, "_actionAdmissionOpen", false); } });
		registry.add("V11-character-and-epoch-mismatch-fenced", _ ->
		{
			try (var f = _handoff.new Fixture()) { f.handoff(); mutateActor(f, "_materializedAtNanos", f.loadedEpoch + 1); mutateActor(f, "_objectId", f.objectId + 1); }
		});
		registry.add("V12-background-state-and-identity-fenced", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				f.handoff(); final var baseline = f.transactions.load(f.id).state();
				final var exact = _profiles.findComponent(f.id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
				for (boolean identity : new boolean[] { false, true })
				{
					final var original = baseline.identity();
					final var changed = identity ? new PhantomBackgroundState.Identity(f.id, f.objectId + 1, original.classIndex(), original.activeClassId(), original.raceOrdinal()) : original;
					final var invalid = new PhantomBackgroundState(identity ? baseline.state() : PhantomBackgroundState.State.READY, changed, baseline.progress(), baseline.vitals(), baseline.position(), baseline.combat(), baseline.loadout(), baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
					writeBackground(f.id, invalid);
					try { PhantomAssertions.assertFalse(permit(f), "Canonical state/identity."); }
					finally { restoreTestComponent(exact); }
				}
			}
		});
		registry.add("V13-before-store-revokes-permit", _ ->
		{
			try (var f = _handoff.new Fixture()) { f.handoff(); f.historical.beforeStore(f.id, World.getInstance().getPlayer(f.objectId)); PhantomAssertions.assertFalse(permit(f), "Permit must be removed before cleanup/store."); }
		});
		registry.add("V14-abort-revokes-permit", _ -> { try (var f = _handoff.new Fixture()) { f.handoff(); f.historical.materializeAborted(f.id, f.objectId); PhantomAssertions.assertFalse(permit(f), "Abort removed active record."); } });
		registry.add("V15-absent-and-complete-history-ordinary", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				final var before = f.catchups.load(f.id).orElseThrow(); f.catchups.replace(f.id, before, before.state().running().advanceTo(f.target));
				PhantomAssertions.assertTrue(permit(f), "COMPLETE ordinary permission."); f.removeCatchup(); PhantomAssertions.assertTrue(permit(f), "Absent history ordinary permission.");
			}
		});
		registry.add("V16-permit-check-has-no-durable-mutation", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				f.handoff(); final var claim = _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE); final var goal = f.goals.load(f.id); final var background = _profiles.findComponent(f.id, PhantomBackgroundState.COMPONENT_TYPE);
				permit(f); permit(f);
				PhantomAssertions.assertEquals(claim, _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE), "Catchup unchanged.");
				PhantomAssertions.assertEquals(goal, f.goals.load(f.id), "Goal unchanged.");
				PhantomAssertions.assertEquals(background, _profiles.findComponent(f.id, PhantomBackgroundState.COMPONENT_TYPE), "Background unchanged.");
			}
		});
		registry.add("V17-snapshot-census-read-only-and-optional", _ ->
		{
			try (var f = _handoff.new Fixture())
			{
				f.handoff(); final var actor = World.getInstance().getPlayer(f.objectId); final var actions = new LocalPlayPilotActions(actor);
				final var before = LocalPlayPilotActions.snapshot(actor);
				final var request = new LocalPlayPilotProtocol.Request("r", "s", "run", 1, Instant.now().plusSeconds(20), LocalPlayPilotProtocol.Operation.SNAPSHOT_PHANTOMS, Map.of("includeCensus", "true", "censusAfterProfileId", "0"));
				final var result = actions.execute(actor, request);
				for (String field : new String[] { "x", "y", "z", "instanceId", "targetId", "moving", "teleporting" }) { PhantomAssertions.assertEquals(before.get(field), LocalPlayPilotActions.snapshot(actor).get(field), "Read-only observer " + field); }
				PhantomAssertions.assertEquals("SUCCEEDED", result.status(), "Snapshot route.");
				PhantomAssertions.assertTrue(result.candidate().containsKey("censusCount"), "RED: optional census must be appended without PREPARE.");
				final var plain = actions.execute(actor, new LocalPlayPilotProtocol.Request("r", "s", "run", 2, Instant.now().plusSeconds(20), LocalPlayPilotProtocol.Operation.SNAPSHOT_PHANTOMS, Map.of()));
				PhantomAssertions.assertFalse(plain.candidate().containsKey("censusCount"), "Default snapshot unchanged.");
			}
		});
		registry.add("V18-active-work-selects-existing-visible-farm", _ ->
		{
			try (var f = _handoff.new Fixture(true))
			{
				f.handoff(); final var engine = engine(f, id -> permit(f));
				try
				{
					work(engine, f.id); final var result = engine.find(f.id).orElseThrow();
					PhantomAssertions.assertTrue(result.decisionSequence() > 0, "RED: exact active handoff work must advance sequence.");
					PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.CANDIDATE_KEY, result.selectedCandidateKey(), "Existing farm candidate.");
					PhantomAssertions.assertFalse("goal.reloaded".equals(result.reasonKey()), "Planning must progress.");
				}
				finally { stop(engine); }
			}
		});
	}

	private static boolean permit(PhantomNativeContextHandoffSuite.Fixture f)
	{
		try { return (boolean) f.historical.getClass().getMethod("permitsDecision", long.class).invoke(f.historical, f.id); }
		catch (NoSuchMethodException missing) { return f.historical.permitsNormalOperation(f.id); } // Pre-fix behavior compiles and fails semantically.
		catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
	}

	private static PhantomDecisionEngine engine(PhantomNativeContextHandoffSuite.Fixture f, LongPredicate permission)
	{
		final var decision = new PhantomBackgroundDecision(f.background, (id, goal) -> true, (id, goal) -> true, id -> {});
		final var candidates = new PhantomCandidateRegistry(); decision.registerCandidates(candidates); candidates.seal();
		final var handlers = new PhantomStepHandlerRegistry(); decision.registerHandlers(handlers); handlers.seal();
		final var engine = new PhantomDecisionEngine(f.goals, candidates, handlers, new PhantomMetrics(), 1, null, permission::test);
		engine.start(); engine.attach(f.id); return engine;
	}
	private static void work(PhantomDecisionEngine engine, long id) { engine.accept(new PhantomActivityWorkItem(id, PhantomActivityState.ACTIVE, 1, 1, 1000, PhantomActivityOverloadLevel.NORMAL)); }
	private static void stop(PhantomDecisionEngine engine) { engine.beginStop(); PhantomAssertions.assertTrue(engine.finishStop(), "Decision stop."); }

	private static void mutateActor(PhantomNativeContextHandoffSuite.Fixture f, String name, Object invalid) throws Exception
	{
		final var entries = f.materialization.getClass().getDeclaredField("_activeByProfile"); entries.setAccessible(true);
		final Object entry = ((Map<?, ?>) entries.get(f.materialization)).get(f.id);
		final var retained = entry.getClass().getDeclaredField("_materializedPlayer"); retained.setAccessible(true);
		final Object actor = retained.get(entry); final var field = actor.getClass().getDeclaredField(name); field.setAccessible(true);
		final Object original = field.get(actor);
		try { field.set(actor, invalid); PhantomAssertions.assertFalse(permit(f), "Invalid lifetime " + name); }
		finally { field.set(actor, original); }
	}
	private void writeBackground(long id, PhantomBackgroundState state)
	{
		final var component = _profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
		_profiles.updateComponent(id, component.componentType(), component.rowVersion(), component.componentSchemaVersion(), new PhantomBackgroundStateCodec().encode(state));
	}

	/** Preserve the exact TEST-only witness; ordinary update would invalidate native-context binding. */
	private void restoreTestComponent(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent exact) throws Exception
	{
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection();
			var statement = connection.prepareStatement("UPDATE phantom_profile_components SET payload=?,row_version=?,updated_at=? WHERE profile_id=? AND component_type=? AND row_version=?"))
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Restore is TEST-only.");
			statement.setBytes(1, exact.payload()); statement.setLong(2, exact.rowVersion()); statement.setTimestamp(3, java.sql.Timestamp.from(exact.updatedAt()));
			statement.setLong(4, exact.profileId()); statement.setString(5, exact.componentType()); statement.setLong(6, exact.rowVersion() + 1);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact test component restore.");
		}
	}
}
