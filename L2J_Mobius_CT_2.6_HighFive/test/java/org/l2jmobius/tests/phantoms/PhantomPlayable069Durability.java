/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.script.State;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Same ordinary buyer, real purchased items, native save and a stable before/after DB image. */
public final class PhantomPlayable069Durability
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !List.of("prepare", "verify").contains(args[0]) || !PhantomTask069Lane.enabled()) { throw new IllegalArgumentException("Own069 prepare|verify required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task069").toRealPath();
		final Path baseline = task.resolve("private/normal-player-baseline069.properties");
		Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006906L, module, task.resolve("reports/durability")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("quests/Q00102_SeaOfSporesFever/Q00102_SeaOfSporesFever.java"));
			final int objectId;
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT c.charId FROM characters c JOIN accounts a ON c.account_name=a.login WHERE c.account_name='task069buyer' AND c.char_name='Buyer069' AND c.accesslevel=0 AND a.accessLevel=-1"); var rows = query.executeQuery()) { check(rows.next(), "Actual ordinary native buyer missing."); objectId = rows.getInt(1); check(!rows.next(), "Ambiguous buyer."); }
			player = Player.load(objectId);
			check(player != null && player.getClient() == null && !player.hasHeadlessOutboundSession() && !player.isGM() && player.getNativeWorkOwner() == null, "Ordinary native buyer authority differs.");
			if (args[0].equals("prepare"))
			{
				check(!Files.exists(baseline), "Preserve existing before image; no reseed.");
				final Properties input = read(task.resolve("reports/ordinary069-input.properties"));
				// Declared QA state, not earned levels/skills or quest completion. Commerce receipts are retained.
				player.getStat().setExp(player.getStat().getExpForLevel(5)); player.getStat().setLevel((byte) 5);
				player.addSkill(SkillData.getInstance().getSkill(3, 1), true);
				player.getVariables().set("task069.persist", "Проверка сохранения 069"); player.getVariables().storeMe();
				final var quest = ScriptManager.getInstance().getQuest(102).getQuestState(player, true); quest.setState(State.STARTED); quest.setCond(1, false); quest.set("task069", "retained");
				player.setXYZInvisible(Integer.parseInt(input.getProperty("x")) + 200, Integer.parseInt(input.getProperty("y")), Integer.parseInt(input.getProperty("z")));
				player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				player.getInventory().updateDatabase(); player.storeMe(); Thread.sleep(500);
				final Properties image = snapshot(objectId); image.setProperty("objectId", Integer.toString(objectId)); image.setProperty("characterName", "Buyer069"); image.setProperty("x", Integer.toString(player.getX())); image.setProperty("y", Integer.toString(player.getY())); image.setProperty("z", Integer.toString(player.getZ()));
				try (var writer = Files.newBufferedWriter(baseline, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)) { image.store(writer, "Own069 normal access0 QA only; no owner account provisioned"); }
				System.out.println("OWN069_NORMAL_PREPARED objectId=" + objectId + ";native buyer inventory preserved; fixture level5/skill3:1/quest-state declared; " + baseline);
			}
			else
			{
				final var expected = read(baseline); final var actual = snapshot(objectId);
				for (String key : actual.stringPropertyNames()) { check(actual.getProperty(key).equals(expected.getProperty(key)), "Normal-player persistence differs: " + key); }
				check(player.getLevel() == 5 && player.getActiveClass() == 0 && player.getSkillLevel(3) == 1 && "Проверка сохранения 069".equals(player.getVariables().getString("task069.persist", "")), "Native reloaded class/skill/variable differs.");
				check(player.getQuestState("Q00102_SeaOfSporesFever") != null && "retained".equals(player.getQuestState("Q00102_SeaOfSporesFever").get("task069")), "Native quest state did not reload.");
				check(player.getInventory().getInventoryItemCount(728, -1) == 1 && player.getInventory().getInventoryItemCount(5575, -1) == 54, "Actual CB purchase/exchange products were lost.");
				System.out.println("OWN069_NORMAL_DURABILITY_PASS objectId=" + objectId + "; inventory/equipment/skills/class/quests/variables exact; native reload PASS");
			}
		}
		finally
		{
			if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static Properties snapshot(int id) throws Exception
	{
		final String[][] queries = {
			{"character", "SELECT char_name,account_name,classid,base_class,level,exp,sp,accesslevel FROM characters WHERE charId=?"},
			{"items", "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=? ORDER BY object_id"},
			{"skills", "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? ORDER BY class_index,skill_id"},
			{"quests", "SELECT name,var,value FROM character_quests WHERE charId=? ORDER BY name,var"},
			{"variables", "SELECT var,val FROM character_variables WHERE charId=? ORDER BY var,val"}
		};
		final Properties result = new Properties();
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true);
			for (var query : queries)
			{
				final var digest = MessageDigest.getInstance("SHA-256"); long count = 0;
				try (var statement = connection.prepareStatement(query[1]))
				{
					statement.setInt(1, id);
					try (var rows = statement.executeQuery())
					{
						while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = rows.getString(column); digest.update((value == null ? "-1:" : value.length() + ":" + value).getBytes(StandardCharsets.UTF_8)); digest.update((byte) '\n'); } count++; }
					}
				}
				result.setProperty(query[0], count + ":" + HexFormat.of().formatHex(digest.digest()));
			}
		}
		return result;
	}
	private static Properties read(Path path) throws Exception { final var properties = new Properties(); try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); } return properties; }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
