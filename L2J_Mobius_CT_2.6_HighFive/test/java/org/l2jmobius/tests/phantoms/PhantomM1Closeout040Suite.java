/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;

/** Native capability fixtures; no natural cohort or actual REAL claim. */
public final class PhantomM1Closeout040Suite implements PhantomTestSuite
{
	private final PhantomVisibleIntentRecoverySuite _intent = new PhantomVisibleIntentRecoverySuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("m1-closeout040", new PhantomM1Closeout040Suite(), new PhantomTestContext(40004002, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "m1-closeout040"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_intent.beforeAll(context);
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { _intent.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("A01-known-native-capability-and-actual-skill-order", context -> capability(context, false));
		registry.add("A02-depleted-native-resource-does-not-plan-unavailable-cast", context -> capability(context, true));
		registry.add("A03-changed-capability-invalidates-old-action-positions", this::changedApproach);
	}
	private void changedApproach(PhantomTestContext context) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true, false, 0, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultInjector.none(), true))
		{
			f.handoff(); final var player = f.loadedPlayer; final var goal = f.goals.load(f.id).orElseThrow().goal();
			player.addSkill(SkillData.getInstance().getSkill(1177, 1), false);
			player.getAutoUseSettings().getAutoSkills().clear(); player.getAutoUseSettings().getAutoSkills().add(1177); player.getAutoUseSettings().resetSkillOrder();
			final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
			final var target = new Monster(NpcData.getInstance().getTemplate(npcId));
			final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
			org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination = null;
			for (int[] direction : new int[][] {{1,0},{0,1},{-1,0},{0,-1}})
			{
				final int x = player.getX() + 900 * direction[0], y = player.getY() + 900 * direction[1], z = geo.getHeight(x, y, player.getZ());
				if (geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId()) && geo.canSeeTarget(player.getX(), player.getY(), player.getZ(), player.getInstanceId(), x, y, z, player.getInstanceId()))
				{ destination = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, z, player.getInstanceId()); break; }
			}
			PhantomAssertions.assertTrue(destination != null, "INPUT: actual direct native approach outside spell range.");
			final var navigation = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService(new org.l2jmobius.gameserver.phantoms.PhantomMetrics()); navigation.start();
			final var controller = new org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController(navigation, () -> true);
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(destination.x(), destination.y(), destination.z()); target.setSpawn(spawn);
			try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
			{
				target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ()); player.setTarget(target);
				PhantomAssertions.assertEquals(1177, new PhantomEngagementPlanner().inspect(player, target, npcId).skillId(), "INPUT: first approach has an affordable known spell.");
				final long now = System.nanoTime(); controller.advance(f.id, player, goal, npcId, _ -> 0, now);
				player.setCurrentMp(0);
				controller.advance(f.id, player, goal, npcId, _ -> 0, now + navigation.policy().stuckWindowNanos() + 1);
				final var opportunity = new PhantomEngagementPlanner().inspect(player, target, npcId);
				final var positionsField = controller.getClass().getDeclaredField("_positions"); positionsField.setAccessible(true);
				@SuppressWarnings("unchecked") final var positions = (java.util.List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint>) positionsField.get(controller);
				PhantomAssertions.assertFalse(positions.isEmpty(), "Current native action has candidates.");
				PhantomAssertions.assertTrue(positions.stream().allMatch(point -> point.distanceTo(opportunity.targetPosition()) <= opportunity.range()), "RED: old spell standpoints cannot survive a switch to the actual short-range action.");
				context.record("approach.current", controller.snapshot());
			}
			finally { controller.stopMovement(player, player.getNativeWorkOwner().epoch()); controller.revoke(); target.deleteMe(); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Approach navigation drains."); }
		}
	}
	private void capability(PhantomTestContext context, boolean depleted) throws Exception
	{
		try (var f = _intent.handoff.new Fixture(true))
		{
			f.handoff(); final var player = f.loadedPlayer;
			PhantomAssertions.assertFalse(player.getPlayerClass().isMage(), "TEST native fighter with explicit known capabilities.");
			final var wind = SkillData.getInstance().getSkill(1177, 1);
			final var twister = SkillData.getInstance().getSkill(1178, 1);
			PhantomAssertions.assertTrue(wind != null && twister != null, "Stock High Five skill data.");
			player.addSkill(wind, false); player.addSkill(twister, false);
			player.getAutoUseSettings().getAutoSkills().clear();
			player.getAutoUseSettings().getAutoSkills().add(1178); player.getAutoUseSettings().getAutoSkills().add(1177);
			player.getAutoUseSettings().resetSkillOrder();
			final int npcId = PhantomBackgroundGoalSpec.parse(f.goals.load(f.id).orElseThrow().goal()).npcId();
			final var target = new Monster(NpcData.getInstance().getTemplate(npcId));
			final var spawn = new Spawn(target.getTemplate()); spawn.setXYZ(player.getX(), player.getY(), player.getZ()); target.setSpawn(spawn);
			try (var action = f.materialization.tryAcquireAction(f.id).orElseThrow())
			{
				target.disableCoreAI(true); target.setImmobilized(true); target.setCurrentHpMp(target.getMaxHp(), target.getMaxMp()); target.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
				player.setTarget(target);
				if (depleted)
				{
					player.setCurrentMp(0);
					PhantomAssertions.assertEquals(0, new PhantomEngagementPlanner().inspect(player, target, npcId).skillId(), "No route/range claim for an unaffordable native cast.");
					return;
				}
				PhantomAssertions.assertTrue(twister.checkCondition(player, target, false) && wind.checkCondition(player, target, false), "INPUT: both actual stock actions are applicable.");
				PhantomAssertions.assertFalse(wind.getTargetList(player).isEmpty(), "INPUT: stock target handlers are loaded and native target resolves.");
				final var planner = new PhantomEngagementPlanner();
				PhantomAssertions.assertEquals(1178, planner.inspect(player, target, npcId).skillId(), "RED: planner uses actual configured capability and next AutoUse skill, independent of mage class flag.");
				player.getAutoUseSettings().incrementSkillOrder();
				PhantomAssertions.assertEquals(1177, planner.inspect(player, target, npcId).skillId(), "Current native skill cursor changes the planned action.");
				final var owner = player.getNativeWorkOwner(); final long damage = owner.evidence().snapshot().damageSequence();
				context.record("cast.input", "mp=" + player.getCurrentMp() + ";hp=" + target.getCurrentHp() + ";target=" + player.getTarget() + ";owner=" + owner + ";evidence=" + owner.evidence().snapshot());
				// This existing headless bootstrap loads effect handlers; reuse039's direct stock cast fixture.
				player.doCast(wind);
				final long deadline = System.nanoTime() + 10_000_000_000L;
				while (owner.evidence().snapshot().damageSequence() == damage && System.nanoTime() < deadline) { Thread.sleep(50); }
				context.record("cast.output", "mp=" + player.getCurrentMp() + ";hp=" + target.getCurrentHp() + ";casting=" + player.isCastingNow() + ";intention=" + player.getAI().getIntention() + ";lastCast=" + player.getLastSkillCast() + ";evidence=" + owner.evidence().snapshot());
				PhantomAssertions.assertTrue(owner.evidence().snapshot().damageSequence() > damage, "Actual stock cast earns own native damage.");
				while (player.isCastingNow() && System.nanoTime() < deadline) { Thread.sleep(50); }
				PhantomAssertions.assertFalse(player.isCastingNow(), "Native cast completes before fixture cleanup.");
				context.record("capability.native", owner.evidence().snapshot());
			}
			finally { target.deleteMe(); }
		}
	}
}
