/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.HennaData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.fishing.Fishing;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Native component proof on the exact own067 TEST lane; no synthetic network session. */
public final class QoLAdventure067Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !List.of("red-cubic", "red-fishing", "red-henna", "red-kamaloka", "red-augmentation", "solo-exit", "core", "fishing").contains(args[0]) || !Boolean.getBoolean("phantom.task067.native")) { throw new IllegalArgumentException("Explicit own067 native scenario required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task067").toRealPath();
		Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(67006701L, module, task.resolve("reports/" + args[0])));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task067native','DISABLED_NATIVE_TEST_ONLY',0,1)")) { statement.executeUpdate(); }
			Integer objectId = null;
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task067native' AND char_name='Native067A' AND accesslevel=0"))
			{ try (var rows = query.executeQuery()) { if (rows.next()) { objectId = rows.getInt(1); if (rows.next()) { throw new IllegalStateException("Own067 fixture ambiguous."); } } } }
			player = objectId == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task067native", "Native067A", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(objectId);
			PhantomAssertions.assertTrue(player != null && !player.isGM() && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Own067 requires an ordinary access0 native fixture, without a fake session.");
			player.setOnlineStatus(true, false); player.stopAllTasks();
			if (player.isDead()) { player.doRevive(); }
			player.getStatus().setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
			player.spawnMe(-71350, 258050, -3104);
			final Path policy = task.resolve("private/native-policy.ini");
			Files.writeString(policy, "EnablePersonalCharacterQoL=True\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nAllowedAccounts=task067native\nAllowedCharacterIds=\nEnablePersonalWondrousCubicCooldown=True\nPersonalWondrousCubicCooldownMinutes=60\nEnablePersonalFishingAssist=True\nEnablePersonalSoloInstanceEntry=True\nPersonalHennaNegativeMode=IGNORE_NEGATIVE\n", StandardCharsets.UTF_8);
			Files.writeString(policy, "EnablePersonalPositiveAugmentationMultiplier=True\nPersonalPositiveAugmentationMultiplier=2.0\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
			PersonalCharacterQoLService.getInstance().installForTests(PersonalCharacterQoLConfig.read(policy));
			PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().isPersonalUser(player), "Own067 real-player service seam was not admitted.");
			System.out.println("OWN067_INPUT alive=" + !player.isAlikeDead() + "; cubic=" + PersonalCharacterQoLService.getInstance().isWondrousCubicCooldownEnabled(player) + "; settings=" + PersonalCharacterQoLConfig.read(policy).adventureSettings() + "; production=" + Player.class.getProtectionDomain().getCodeSource().getLocation());
			if (args[0].equals("core"))
			{
				QoLAdventure067CoreChecks.run(player, task);
			}
			else if (args[0].equals("fishing"))
			{
				QoLAdventure067FishingScenario.run(player, task);
			}
			else if (args[0].equals("solo-exit"))
			{
				QoLAdventure067CoreChecks.soloExitRegression(player, task);
			}
			else if (args[0].equals("red-cubic"))
			{
				final var cubic = player.addItem(ItemProcessType.QUEST, 10632, 1, null, false);
				QoLAdventure067CubicChecks.legacyTimestampUsesPersonalHour(player, cubic);
			}
			else if (args[0].equals("red-augmentation"))
			{
				QoLAdventure067AugmentationChecks.nativePositiveBonuses(player, 2.0);
			}
			else if (args[0].equals("red-henna"))
			{
				PhantomAssertions.assertTrue(player.addHenna(HennaData.getInstance().getHenna(1)), "Own067 native dye input failed.");
				try { PhantomAssertions.assertEquals(0, player.getHennaStatCON(), "Personal IGNORE_NEGATIVE retained the native negative dye modifier."); }
				finally { player.removeHenna(1); }
			}
			else if (args[0].equals("red-kamaloka"))
			{
				player.getStat().setLevel((byte) 23);
				ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"));
				final Npc captain = new Npc(NpcData.getInstance().getTemplate(30332));
				ScriptManager.getInstance().getScript("Kamaloka").onEvent("0", captain, player);
				final var world = InstanceManager.getInstance().getPlayerWorld(player);
				PhantomAssertions.assertTrue(world != null && world.getTemplateId() == 57, "An allowlisted ordinary solo entrant did not receive a native Kamaloka world.");
			}
			else
			{
				player.addSkill(SkillData.getInstance().getSkill(1315, 1), false);
				player.addSkill(SkillData.getInstance().getSkill(1313, 1), false);
				player.addSkill(SkillData.getInstance().getSkill(1314, 1), false);
				final var rod = player.addItem(ItemProcessType.QUEST, 6529, 1, null, false);
				final var lure = player.addItem(ItemProcessType.QUEST, 7807, 2, null, false);
				player.getInventory().equipItem(rod); player.getInventory().equipItem(lure); player.setLure(lure);
				player.setCurrentMp(player.getMaxMp());
				// Declared premeasurement native combat input; the final proof must also exercise START effect/fee.
				player.startFishing(player.getX() + 120, player.getY(), player.getZ()); player.stopLookingForFishTask(); player.startFishCombat(true, false);
				for (String fieldName : List.of("_mode", "_deceptiveMode", "_stop"))
				{
					final var field = Fishing.class.getDeclaredField(fieldName); field.setAccessible(true); field.setInt(player.getFishCombat(), fieldName.equals("_stop") ? 1 : 0);
				}
				QoLAdventure067FishingChecks.assertAssistedTick(player, 1313);
			}
			System.out.println("OWN067_NATIVE scenario=" + args[0] + "; PASS; actualNetworkSession=false; CLIENT_QA=DEFERRED_BY_OWNER");
		}
		finally
		{
			PersonalCharacterQoLService.getInstance().installForTests(null);
			try { if (player != null) { if (player.getFishCombat() != null) { player.getFishCombat().doDie(false); } player.stopAllTasks(); player.storeMe(); player.deleteMe(); } }
			finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
		}
	}
}
