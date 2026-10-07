/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomM1DynamicRecipientChecks;

/** Task023 guarded native contracts; full natural acceptance remains a separate server gate. */
public final class PhantomLivingWorld023Suite implements PhantomTestSuite
{

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("living-world023", new PhantomLivingWorld023Suite(),
			new PhantomTestContext(23002301, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "living-world023"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception { _environment.shutdown(); }
	@Override public void register(PhantomTestRegistry registry)
	{
		final String focus = System.getProperty("phantom.m1.native.focus", "all");
		if (focus.equals("retaliation024"))
		{
			for (String variant : java.util.List.of("retaliation-open", "retaliation-sealed")) { registry.add("A01-A02-actual-HitTask-NPC-" + variant, context -> cooperative(context, variant)); }
			registry.add("A03-original-entry-cannot-relabel-replaced-epoch", context -> admission(context, "original-replaced-epoch"));
			return;
		}
		if (java.util.Set.of("cooperative", "stale").contains(focus))
		{
			registry.add("N02-N06-lawful-native-" + focus, context -> cooperative(context, focus)); return;
		}
		registry.add("N02-late-real-native-damage-open-owner-completes-cast", this::cooperative);
		registry.add("N06-old-published-callback-cannot-abort-new-native-cast", context -> cooperative(context, "stale"));
		registry.add("N04-real-cast-after-native-writer-exception-is-retained", context -> cooperative(context, "cast-body-failure"));
		for (String variant : java.util.List.of("physical-open", "physical-sealed", "physical-ordinary")) { registry.add("N03-N04-" + variant, context -> cooperative(context, variant)); }
		for (String variant : java.util.List.of("open", "sealed", "replaced-epoch", "strict-delayed", "body-failure", "earned-failure-drain"))
		{
			registry.add("N03-N04-native-boundary-" + variant, context -> admission(context, variant));
		}
	}
	private void admission(PhantomTestContext context, String variant) throws Exception
	{
		final Player first = Player.load(_environment.primary().objectId()), second = Player.load(_environment.observer().objectId());
		try (var firstOutput = first.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)); var secondOutput = second.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
		{
			for (Player player : java.util.List.of(first, second)) { player.stopAllTasks(); player.setCurrentHp(player.getMaxHp()); player.setOnlineStatus(true, false); player.spawnMe(); }
			PhantomM1DynamicRecipientChecks.admissionContract(context, first, second, variant);
		}
		finally { for (Player player : java.util.List.of(first, second)) { if (!player.isNativeWorkManaged() && org.l2jmobius.gameserver.model.World.getInstance().getPlayer(player.getObjectId()) == player) { _environment.cleanupLoadedPlayer(player); } } }
	}
	private void cooperative(PhantomTestContext context) throws Exception
	{
		cooperative(context, "cooperative");
	}
	private void cooperative(PhantomTestContext context, String variant) throws Exception
	{
		if (!variant.startsWith("physical-") && !variant.startsWith("retaliation-"))
		{
			for (int objectId : java.util.List.of(_environment.primary().objectId(), _environment.observer().objectId()))
			{
				try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var statement = connection.prepareStatement("UPDATE characters SET classid=10,base_class=10,race=0,level=?,exp=?,online=0 WHERE charId=?"))
				{
					final int level = variant.equals("stale") ? 7 : 1;
					statement.setInt(1, level); statement.setLong(2, org.l2jmobius.gameserver.data.xml.ExperienceData.getInstance().getExpForLevel(level)); statement.setInt(3, objectId);
					PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Exact owned ordinary TEST Human Mystic setup before native lifetime.");
				}
			}
		}
		final Player first = Player.load(_environment.primary().objectId());
		final Player second = Player.load(_environment.observer().objectId());
		final Player ordinary = Player.create(org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(0), _environment.primary().accountName(), "Ph023Ord" + Long.toUnsignedString(System.nanoTime(), 36), new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		PhantomAssertions.assertTrue(ordinary != null, "N02 canonical ordinary third fixture created.");
		final Monster npc = variant.startsWith("retaliation-") ? new PhantomM1DynamicRecipientChecks.RetaliationMonster024() : variant.equals("cast-body-failure") ? new AfterNativeWriterFailure() : new Monster(NpcData.getInstance().getTemplate(variant.startsWith("physical-") ? 20667 : variant.equals("cooperative") ? 20121 : 20534));
		try (var firstOutput = first.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128));
			var secondOutput = second.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128));
			var thirdOutput = ordinary.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128)))
		{
			for (Player player : java.util.List.of(first, second, ordinary))
			{
				player.stopAllTasks();
				if (player != ordinary && !variant.startsWith("physical-") && !variant.startsWith("retaliation-"))
				{
					final var learn = org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(player.getPlayerClass()).values().stream().filter(entry -> entry.getSkillId() == 1177 && entry.getSkillLevel() == 1 && entry.getGetLevel() <= player.getLevel()).findFirst().orElseThrow();
					player.addSkill(SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel()), true);
				}
				player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				player.setOnlineStatus(true, false);
			}
			first.spawnMe(); second.spawnMe(first.getX() + 30, first.getY(), first.getZ()); ordinary.spawnMe(first.getX() + 35, first.getY(), first.getZ());
			final Spawn spawn = new Spawn(npc.getTemplate()); spawn.setXYZ(first.getX() + 40, first.getY(), first.getZ()); npc.setSpawn(spawn);
			npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp()); npc.spawnMe(spawn.getX(), spawn.getY(), spawn.getZ());
			PhantomAssertions.assertFalse(npc.isCoreAIDisabled(), "N02 stock NPC AI must remain enabled.");
			if (variant.startsWith("retaliation-")) { PhantomM1DynamicRecipientChecks.retaliation024(context, first, second, (PhantomM1DynamicRecipientChecks.RetaliationMonster024) npc, variant.equals("retaliation-sealed")); }
			else if (variant.equals("stale")) { PhantomM1DynamicRecipientChecks.staleCast(context, first, npc, first.getKnownSkill(1177)); }
			else if (variant.equals("cast-body-failure")) { PhantomM1DynamicRecipientChecks.failedCastBody(context, first, npc, first.getKnownSkill(1177)); }
			else if (variant.startsWith("physical-")) { PhantomM1DynamicRecipientChecks.physicalTransfer(context, first, second, ordinary, npc, !variant.equals("physical-ordinary"), variant.equals("physical-sealed")); }
			else { PhantomM1DynamicRecipientChecks.lateNativeDamage(context, first, second, ordinary, npc, first.getKnownSkill(1177)); }
		}
		finally
		{
			npc.abortAttack(); npc.abortCast(); npc.deleteMe();
			for (Player player : java.util.List.of(second, first))
			{
				if (!player.isNativeWorkManaged() && org.l2jmobius.gameserver.model.World.getInstance().getPlayer(player.getObjectId()) == player) { _environment.cleanupLoadedPlayer(player); }
			}
			_environment.cleanupLoadedPlayer(ordinary); org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(ordinary.getObjectId());
		}
	}

	private static final class AfterNativeWriterFailure extends Monster
	{
		private boolean _failed;
		AfterNativeWriterFailure() { super(NpcData.getInstance().getTemplate(20534)); }
		@Override public void reduceCurrentHp(double amount, org.l2jmobius.gameserver.model.actor.Creature attacker, boolean awake, boolean dot, org.l2jmobius.gameserver.model.skill.Skill skill)
		{
			super.reduceCurrentHp(amount, attacker, awake, dot, skill);
			if (!_failed && amount > 0) { _failed = true; throw new IllegalStateException("TEST023_AFTER_NATIVE_HP_WRITER"); }
		}
	}
}
