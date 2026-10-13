/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Actual repository goal commits, reusing the guarded current native fixture. */
public final class PhantomM1Closure041Suite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("m1-closure041", new PhantomM1Closure041Suite(), new PhantomTestContext(41004101, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "m1-closure041"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("idle041".equals(System.getProperty("phantom.m1.native.focus"))) { registry.add("A01-no-target-replans-before-useful-debt-without-renewing-it", this::noTargetRecovery); return; }
		registry.add("G01-owning-goal-commit-links-exact-payload-and-versions", this::goalCommit);
		registry.add("G02-losing-cas-emits-no-successful-goal-witness", this::losingCas);
		registry.add("G03-diagnostic-failure-cannot-undo-goal-commit", this::observerFailure);
		registry.add("A01-no-target-replans-before-useful-debt-without-renewing-it", this::noTargetRecovery);
	}
	private void noTargetRecovery(PhantomTestContext context) throws Exception
	{
		try (var fixture = _intent.handoff.new Fixture(true))
		{
			fixture.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(fixture);
			final var clock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
			final var roots = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var adapter = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay(fixture.materialization, () -> engine, fixture.historical::permitsDecision, _ -> roots.get(), clock::get);
			final var player = fixture.loadedPlayer; final var goal = fixture.goals.load(fixture.id).orElseThrow().goal();
			final int isolated = org.l2jmobius.gameserver.managers.InstanceManager.getInstance().createDynamicInstance(0).getId();
			player.setInstanceId(isolated);
			try
			{
				PhantomAssertions.assertTrue(adapter.start(fixture.id, goal), "Current native session in an empty same-instance target set.");
				PhantomAssertions.assertFalse(adapter.noTargetExpired(fixture.id, goal), "Transient no-target state is tolerated.");
				final var before = adapter.snapshotContinuation(fixture.id).scalarMap();
				PhantomAssertions.assertTrue(Long.parseLong(before.get("liveNoTargetSinceNanos")) > 0, "Actual no-target observation starts its clock.");
				clock.addAndGet(29_000_000_000L);
				PhantomAssertions.assertFalse(adapter.noTargetExpired(fixture.id, goal), "Existing thirty-second recovery delay is respected.");
				clock.addAndGet(2_000_000_000L);
				PhantomAssertions.assertTrue(adapter.noTargetExpired(fixture.id, goal), "RED: a depleted local target set requests existing replan before the ninety-second useful-debt limit.");
				final var after = adapter.snapshotContinuation(fixture.id).scalarMap();
				PhantomAssertions.assertEquals(before.get("liveUsefulSinceNanos"), after.get("liveUsefulSinceNanos"), "No-target recovery never manufactures earned progress.");
				PhantomAssertions.assertEquals(before.get("liveSessionEpoch"), after.get("liveSessionEpoch"), "The original native lifetime is retained.");
				roots.set(false);
				PhantomAssertions.assertFalse(adapter.noTargetExpired(fixture.id, goal), "Soft retirement cannot launch offscreen replan or repair.");
				context.record("A01.before", before); context.record("A01.after", after);
			}
			finally
			{
				adapter.beforeStore(fixture.id, player); player.setInstanceId(0);
				org.l2jmobius.gameserver.managers.InstanceManager.getInstance().destroyInstance(isolated);
				PhantomVisibleIntentRecoverySuite.stop(engine);
			}
		}
	}
	private static java.lang.reflect.Field observer() throws Exception
	{
		// Existing optional observer pattern; the RED runs the real mutation even before this producer exists.
		try { final var field = PhantomProfileRepository.class.getDeclaredField("_commitWitnessObserver"); field.setAccessible(true); return field; }
		catch (NoSuchFieldException missing) { final var field = PhantomBackgroundTransaction.class.getDeclaredField("_commitWitnessObserver"); field.setAccessible(true); return field; }
	}
	private static void subscribe(long id) throws Exception
	{
		try { final var field = PhantomProfileRepository.class.getDeclaredField("_commitWitnessProfiles"); field.setAccessible(true); field.set(null, (LongPredicate) value -> value == id); }
		catch (NoSuchFieldException missing) { /* RED: existing transaction producer cannot see this repository commit. */ }
	}
	private static void unsubscribe() throws Exception
	{
		try { final var field = PhantomProfileRepository.class.getDeclaredField("_commitWitnessProfiles"); field.setAccessible(true); field.set(null, null); }
		catch (NoSuchFieldException missing) { }
	}
	private void goalCommit(PhantomTestContext context) throws Exception
	{
		try (var fixture = _intent.handoff.new Fixture(true))
		{
			final var records = new ArrayList<Map<String, String>>(); final var hook = observer(); subscribe(fixture.id);
			hook.set(null, (Consumer<Map<String, String>>) records::add);
			try
			{
				final var before = fixture.goals.load(fixture.id).orElseThrow();
				final var changed = revision(before.goal()); final var after = fixture.goals.replace(fixture.id, before.rowVersion(), changed);
				PhantomAssertions.assertEquals(1, records.size(), "RED: real goal owner must publish its own successful commit.");
				final var row = records.getFirst(); final var codec = new PhantomGoalStateCodec();
				PhantomAssertions.assertEquals("goal.runtime", row.get("componentName"), "Exact component.");
				PhantomAssertions.assertEquals("SUCCESS", row.get("commitOutcome"), "Only actual successful JDBC commit.");
				PhantomAssertions.assertEquals(Long.toString(before.rowVersion()), row.get("beforeRowVersion"), "Exact predecessor version.");
				PhantomAssertions.assertEquals(Long.toString(after.rowVersion()), row.get("afterRowVersion"), "Exact successor version.");
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.payloadDigest(codec.encode(before.goal())), row.get("beforePayloadSha256"), "Predecessor came from owning SQL transaction.");
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.payloadDigest(codec.encode(changed)), row.get("afterPayloadSha256"), "Successor equals actual encoded mutation.");
				PhantomAssertions.assertEquals(changed, fixture.goals.load(fixture.id).orElseThrow().goal(), "Separate connection sees committed goal.");
				PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> row.put("commitOutcome", "forged"), "Witness is immutable.");
				context.record("G01.actualCommit", row);
			}
			finally { hook.set(null, null); unsubscribe(); }
		}
	}
	private void losingCas(PhantomTestContext context) throws Exception
	{
		try (var fixture = _intent.handoff.new Fixture(true))
		{
			final var before = fixture.goals.load(fixture.id).orElseThrow(); final var hook = observer(); subscribe(fixture.id);
			final var records = new ArrayList<Map<String, String>>(); hook.set(null, (Consumer<Map<String, String>>) records::add);
			try
			{
				PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> fixture.goals.replace(fixture.id, before.rowVersion() + 1, revision(before.goal())), "Actual losing CAS rolls back.");
				PhantomAssertions.assertEquals(0, records.size(), "Rollback is silent.");
				PhantomAssertions.assertEquals(before, fixture.goals.load(fixture.id).orElseThrow(), "Durable predecessor retained.");
				context.record("G02.rollbackRecords", records.size());
			}
			finally { hook.set(null, null); unsubscribe(); }
		}
	}
	private void observerFailure(PhantomTestContext context) throws Exception
	{
		try (var fixture = _intent.handoff.new Fixture(true))
		{
			final var before = fixture.goals.load(fixture.id).orElseThrow(); final var changed = revision(before.goal()); final var hook = observer(); subscribe(fixture.id);
			final var reached = new java.util.concurrent.atomic.AtomicBoolean();
			hook.set(null, (Consumer<Map<String, String>>) row -> { reached.set(true); throw new IllegalStateException("TEST041 diagnostic rejection"); });
			try
			{
				fixture.goals.replace(fixture.id, before.rowVersion(), changed);
				PhantomAssertions.assertTrue(reached.get(), "Actual owner called the diagnostic after commit.");
				PhantomAssertions.assertEquals(changed, fixture.goals.load(fixture.id).orElseThrow().goal(), "Exporter rejection never undoes committed product data.");
				context.record("G03.committedAfterExporterFailure", true);
			}
			finally { hook.set(null, null); unsubscribe(); }
		}
	}
	private static PhantomGoal revision(PhantomGoal goal)
	{
		return new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision() + 1, goal.payloadText());
	}
}
