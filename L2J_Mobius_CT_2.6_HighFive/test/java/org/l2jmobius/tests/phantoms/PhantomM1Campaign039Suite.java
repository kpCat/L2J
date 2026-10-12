/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;

/** Causal native campaign checks, reusing the current owned handoff fixture. */
public final class PhantomM1Campaign039Suite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("m1-campaign039", new PhantomM1Campaign039Suite(), new PhantomTestContext(39003901, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "m1-campaign039"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _intent.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		if ("snapshot039".equals(System.getProperty("phantom.m1.native.focus"))) { registry.add("P05-resource-diagnostics-use-one-existing-session-monitor", this::coherentSnapshot); return; }
		if (!"boundary039".equals(System.getProperty("phantom.m1.native.focus"))) { registry.add("A02-earned-native-damage-is-observed-before-resource-return", this::progressBeforePause); }
		if ("boundary039".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("P01-real-finalize-commit-is-linked-to-later-state-increment", this::committedSuccessor);
			registry.add("P04-rollback-is-silent-and-observer-failure-does-not-undo-commit", this::rollbackAndObserverFailure);
		}
	}
	private void coherentSnapshot(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision);
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Actual native policy provides the existing session.");
			final var sessions = PhantomVisibleAutoPlay.class.getDeclaredField("_sessions"); sessions.setAccessible(true);
			final Object session = ((Map<?, ?>) sessions.get(adapter)).get(f.id);
			final var entered = new java.util.concurrent.CountDownLatch(1);
			final var done = new java.util.concurrent.CountDownLatch(1);
			final var observed = new java.util.concurrent.atomic.AtomicReference<PhantomVisibleAutoPlay.ContinuationSnapshot>();
			final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
			final var reader = new Thread(() ->
			{
				entered.countDown();
				try { observed.set(adapter.snapshotContinuation(f.id)); }
				catch (Throwable error) { failure.set(error); }
				finally { done.countDown(); }
			}, "TEST039-coherent-resource-snapshot");
			try
			{
				synchronized (session)
				{
					reader.start();
					PhantomAssertions.assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS), "Controlled reader reaches the original session.");
					PhantomAssertions.assertFalse(done.await(150, java.util.concurrent.TimeUnit.MILLISECONDS), "RED: resource tuple must wait for the same monitor used by its real writer.");
				}
				PhantomAssertions.assertTrue(done.await(2, java.util.concurrent.TimeUnit.SECONDS) && failure.get() == null && observed.get() != null, "Coherent observation completes after the existing writer monitor releases.");
				context.record("P05.snapshot", observed.get().scalarMap());
			}
			finally { reader.join(2_000); adapter.beforeStore(f.id, f.loadedPlayer); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
	private void committedSuccessor(PhantomTestContext context) throws Exception
	{
		final var observer = PhantomBackgroundTransaction.class.getDeclaredField("_commitWitnessObserver"); observer.setAccessible(true);
		final var records = new ArrayList<Map<String, String>>();
		try (var f = _intent.handoff.new Fixture(true))
		{
			observer.set(null, (Consumer<Map<String, String>>) records::add);
			try
			{
				f.handoff();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Real owning native store completes.");
				final var finalized = records.stream().filter(row -> "finalizeOwnedStore".equals(row.get("producer")) && PhantomBackgroundState.COMPONENT_TYPE.equals(row.get("componentName"))).reduce((a,b) -> b).orElseThrow();
				PhantomAssertions.assertEquals("true", finalized.get("canonicalChecked"), "Exact canonical comparison occurs under owning transaction.");
				PhantomAssertions.assertEquals("FULL", finalized.get("inventoryScope"), "All inventory rows are represented by the existing canonical hash.");
				PhantomAssertions.assertEquals(Long.toString(f.loadedEpoch), finalized.get("nativeEpoch"), "Actual receipt epoch is retained.");
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, f.transactions.markMaterialized(f.id, f.objectId).status(), "A real later state commit.");
				final var successor = records.stream().filter(row -> "markMaterialized".equals(row.get("producer")) && PhantomBackgroundState.COMPONENT_TYPE.equals(row.get("componentName"))).reduce((a,b) -> b).orElseThrow();
				PhantomAssertions.assertEquals(finalized.get("afterRowVersion"), successor.get("beforeRowVersion"), "Successor follows exact finalized row version.");
				PhantomAssertions.assertEquals(finalized.get("afterPayloadSha256"), successor.get("beforePayloadSha256"), "Successor reads exact committed predecessor.");
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT row_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
				{
					statement.setLong(1, f.id); statement.setString(2, PhantomBackgroundState.COMPONENT_TYPE);
					try (var result = statement.executeQuery()) { PhantomAssertions.assertTrue(result.next(), "Late SQL exists."); PhantomAssertions.assertEquals(successor.get("afterRowVersion"), Long.toString(result.getLong(1)), "Late SQL is a committed successor, not the earlier finalized row."); PhantomAssertions.assertEquals(successor.get("afterPayloadSha256"), PhantomBackgroundTransaction.payloadDigest(result.getBytes(2)), "Late payload matches actual successor."); }
				}
				PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> finalized.put("afterPayloadSha256", "tampered"), "Published map is immutable.");
				context.record("P01.finalized", finalized); context.record("P01.successor", successor); context.record("P01.records", records.size());
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, f.transactions.abortMaterialization(f.id, f.objectId).status(), "Controlled TEST state returns through existing recovery.");
			}
			finally { observer.set(null, null); }
		}
	}
	private void rollbackAndObserverFailure(PhantomTestContext context) throws Exception
	{
		final var observer = PhantomBackgroundTransaction.class.getDeclaredField("_commitWitnessObserver"); observer.setAccessible(true);
		final List<Map<String, String>> records = new ArrayList<>();
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var current = f.transactions.load(f.id).state(); final var goal = f.goals.load(f.id).orElseThrow().goal();
			observer.set(null, (Consumer<Map<String, String>>) records::add);
			try
			{
				final var faultReached = new java.util.concurrent.atomic.AtomicBoolean();
				final var rollback = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, PhantomBackgroundTransaction.ObjectIdAllocator.production(), point -> { if (point == PhantomBackgroundTransaction.FaultPoint.BEFORE_OWNED_PREPARE_COMMIT) { faultReached.set(true); throw new IllegalStateException("TEST039 bounded rollback"); } });
				PhantomAssertions.assertTrue(rollback.prepareOwnedStore(current, goal, f.loadedEpoch, PhantomBackgroundState.State.READY).status() != PhantomBackgroundTransaction.Status.SUCCESS, "Real precommit rollback is rejected.");
				PhantomAssertions.assertTrue(faultReached.get(), "The rollback reached the actual precommit fault point.");
				PhantomAssertions.assertEquals(0, records.size(), "Rollback emits no committed witness.");
				PhantomAssertions.assertEquals(current, f.transactions.load(f.id).state(), "Rollback preserved prior durable state.");
				observer.set(null, (Consumer<Map<String, String>>) row -> { throw new IllegalStateException("TEST039 exporter rejection"); });
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.dematerialize(f.id).status(), "Observer failure cannot undo the actual store commit.");
				PhantomAssertions.assertEquals(PhantomBackgroundState.State.READY, f.transactions.load(f.id).state().state(), "Successful canonical state remains committed.");
				context.record("P04.rollbackRecords", records.size());
			}
			finally { observer.set(null, null); }
		}
	}
	private void progressBeforePause(PhantomTestContext context) throws Exception
	{
		// Reuse the existing F05 pre-publication TEST class/skill setup, without runtime stat repair.
		final var field = PhantomNativeContextHandoffSuite.class.getDeclaredField("_environment"); field.setAccessible(true);
		final var environment = (PhantomHeadlessPlayerTestEnvironment) field.get(_intent.handoff);
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=25,base_class=25,race=1,level=7,exp=?,online=0 WHERE charId=?"))
		{
			statement.setLong(1, ExperienceData.getInstance().getExpForLevel(7)); statement.setInt(2, environment.primary().objectId());
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned TEST pre-World caster setup.");
		}
		final Player setup = Player.load(environment.primary().objectId());
		try
		{
			final var learn = SkillTreeData.getInstance().getCompleteClassSkillTree(setup.getPlayerClass()).values().stream().filter(entry -> entry.getSkillId() == 1177 && entry.getSkillLevel() == 1 && entry.getGetLevel() <= setup.getLevel()).findFirst().orElseThrow();
			setup.addSkill(SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true);
			setup.storeMe();
		}
		finally { environment.cleanupLoadedPlayer(setup); }
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var engine = PhantomVisibleIntentRecoverySuite.engine(f);
			final var clock = new AtomicLong(System.nanoTime());
			final var adapter = new PhantomVisibleAutoPlay(f.materialization, () -> engine, f.historical::permitsDecision, clock::get);
			final var player = f.loadedPlayer; final var goal = f.goals.load(f.id).orElseThrow().goal();
			final int isolated = InstanceManager.getInstance().createDynamicInstance(0).getId();
			final var target = new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
			player.setInstanceId(isolated);
			try
			{
				PhantomAssertions.assertTrue(adapter.start(f.id, goal), "Current native policy starts before the damage.");
				final var before = adapter.snapshotContinuation(f.id).scalarMap();
				final long damage = player.getNativeWorkOwner().evidence().snapshot().damageSequence();
				final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX(), player.getY(), player.getZ()); target.setSpawn(spawn);
				target.setInstanceId(isolated); target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				context.record("A02.input", "mp=" + player.getCurrentMp() + ";cost=" + (player.getKnownSkill(1177).getMpInitialConsume() + player.getKnownSkill(1177).getMpConsume()) + ";targetHp=" + target.getCurrentHp() + ";alive=" + !target.isAlikeDead() + ";native=" + player.getNativeWorkOwner().evidence().snapshot());
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.setTarget(target); player.doCast(player.getKnownSkill(1177)); }
				try { await(8_000, () -> player.getNativeWorkOwner().evidence().snapshot().damageSequence() > damage, "INPUT: actual stock cast must produce native damage evidence."); }
				finally { context.record("A02.castBoundary", "mp=" + player.getCurrentMp() + ";targetHp=" + target.getCurrentHp() + ";casting=" + player.isCastingNow() + ";native=" + player.getNativeWorkOwner().evidence().snapshot() + ";policy=" + adapter.snapshotContinuation(f.id).scalarMap()); }
				await(8_000, () -> !player.isCastingNow() && !player.isAttackingNow(), "Original native action completes before rest.");
				target.deleteMe();
				try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow()) { player.sitDown(); }
				PhantomAssertions.assertTrue(player.isSitting(), "The native resource branch sees actual stock rest.");
				clock.addAndGet(1_000_000_000L);
				// Pool ticks share the existing command gate. A busy tick is PAUSED, so wait for this boundary to run.
				await(2_000, () -> { adapter.noTargetExpired(f.id, goal); return Long.parseLong(adapter.snapshotContinuation(f.id).scalarMap().get("liveNativeUsefulNanos")) > 0; }, "Native progress reaches the owned no-target boundary after concurrent pool work.");
				final var after = adapter.snapshotContinuation(f.id).scalarMap();
				context.record("A02.before", before); context.record("A02.after", after); context.record("A02.native", player.getNativeWorkOwner().evidence().snapshot());
				PhantomAssertions.assertEquals(Long.toString(clock.get()), after.get("liveUsefulSinceNanos"), "RED: already-earned native damage must be observed before the resource early return.");
				clock.addAndGet(1_000_000_000L); adapter.noTargetExpired(f.id, goal);
				PhantomAssertions.assertEquals(after.get("liveUsefulSinceNanos"), adapter.snapshotContinuation(f.id).scalarMap().get("liveUsefulSinceNanos"), "Rest without new native damage does not renew useful debt.");
				PhantomAssertions.assertEquals(before.get("liveSessionEpoch"), after.get("liveSessionEpoch"), "The exact original lifetime is retained.");
			}
			finally { adapter.beforeStore(f.id, player); target.deleteMe(); player.setInstanceId(0); InstanceManager.getInstance().destroyInstance(isolated); PhantomVisibleIntentRecoverySuite.stop(engine); }
		}
	}
	private static void await(long millis, java.util.function.BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + millis * 1_000_000L;
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(20); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}
}
