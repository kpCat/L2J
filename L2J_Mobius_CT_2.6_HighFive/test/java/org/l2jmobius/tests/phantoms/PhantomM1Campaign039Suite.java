/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
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
		registry.add("A02-earned-native-damage-is-observed-before-resource-return", this::progressBeforePause);
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
				adapter.noTargetExpired(f.id, goal);
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
