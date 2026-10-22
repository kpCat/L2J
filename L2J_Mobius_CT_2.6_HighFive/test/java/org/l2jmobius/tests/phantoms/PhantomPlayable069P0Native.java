/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropGroupHolder;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Deterministic native P0 boundaries, without a client or probabilistic boss farming. */
public final class PhantomPlayable069P0Native
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask069Lane.enabled()) { throw new IllegalArgumentException("Explicit own069 native lane required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path output = module.getParent().resolve(".phantom-local/task069/reports/p0-native-" + System.currentTimeMillis() + ".tsv");
		final StringBuilder report = new StringBuilder("stage\tvalue\n");
		Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006902L, module, output.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			player = fixture();
			mana(player, report);
			drops(player, report);
			report.append("scope\tnative potion/inventory + actual RaidBoss/GrandBoss/Monster drop owners; direct combat/QueenAnt separate\n");
			System.out.println("OWN069_P0_NATIVE_PASS " + output);
		}
		finally
		{
			Files.writeString(output, report);
			if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}

	private static Player fixture() throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task069p0','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
		final Player player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task069p0", "Rules069", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(player != null && player.getClient() == null && !player.hasHeadlessOutboundSession() && !player.isGM(), "Ordinary actor required.");
		player.getStat().setLevel((byte) 80);
		player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		player.addItem(ItemProcessType.REWARD, 728, 4, player, false);
		player.setOnlineStatus(true, false); player.spawnMe(81700, 148500, -3470);
		return player;
	}

	private static void mana(Player player, StringBuilder report) throws Exception
	{
		final var potion = player.getInventory().getItemByItemId(728);
		final var handler = ItemHandler.getInstance().getHandler(potion.getEtcItem());
		final var skill = SkillData.getInstance().getSkill(10001, 1);
		check(handler != null && skill != null && skill.getReuseDelay() == 500, "Native mana handler/reuse missing.");
		player.setPvpFlag(1); player.setTarget(player); player.setCurrentMp(1);
		check(potion.getTemplate().checkCondition(player, player, false), "Item conditions still refuse PvP.");
		final long before = potion.getCount();
		check(handler.onItemUse(player, potion, false), "Native PvP mana use refused.");
		Thread.sleep(30);
		check(potion.getCount() == before - 1 && player.getCurrentMp() >= 100 && player.getCurrentMp() <= 102, "Native mana effect/consume differs from100MP/1item.");
		check(!handler.onItemUse(player, potion, false) && potion.getCount() == before - 1, "Immediate reuse consumed another potion.");
		report.append("pvpMana\tMP1->").append(player.getCurrentMp()).append(";count=").append(before).append("->").append(potion.getCount()).append(";reuse500ms repeatRefused\n");
		Thread.sleep(600); player.setPvpFlag(0); player.setKarma(100); player.setCurrentMp(1);
		check(handler.onItemUse(player, potion, false), "Karma-only mana use refused.");
		Thread.sleep(600); player.setKarma(0); player.setDuelState(1); player.setCurrentMp(1);
		check(handler.onItemUse(player, potion, false), "Duel mana use refused.");
		player.setDuelState(0);
		report.append("pkDuel\tnative uses accepted; setup flags are explicit fixture input\n");
	}

	private static void drops(Player player, StringBuilder report)
	{
		// Deterministic boundary: force the configurable level filter to0, never the base drop table.
		RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE = 1; RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE = 2; RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE = 0;
		RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE = 1; RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE = 2; RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE = 0;
		RatesConfig.DROP_MAX_OCCURRENCES_NORMAL = 8; RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS = 8;
		final List<Monster> victims = List.of(new Monster(template(910691)), new RaidBoss(template(910692)), new GrandBoss(template(910693)));
		for (Monster victim : victims)
		{
			try
			{
				final var group = new DropGroupHolder(100); group.addDrop(new DropHolder(DropType.DROP, 1864, 3, 3, 100));
				victim.getTemplate().setDropGroups(List.of(group));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 1865, 4, 4, 100));
				victim.getTemplate().addDrop(new DropHolder(DropType.DROP, 57, 17, 17, 100));
				final var result = victim.getTemplate().calculateDrops(DropType.DROP, victim, player);
				for (int id : new int[] {1864, 1865, 57})
				{
					final long count = result == null ? 0 : result.stream().filter(item -> item.getId() == id).mapToLong(item -> item.getCount()).sum();
					check(count == (victim.isRaid() ? id == 1864 ? 3 : id == 1865 ? 4 : 17 : 0), "Native drop role boundary failed: " + victim.getClass().getSimpleName() + "/" + id + "=" + count);
				}
				report.append("drops\t").append(victim.getClass().getSimpleName()).append(";actualIsRaid=").append(victim.isRaid()).append(";victim40/killer80;grouped+ungrouped+adena=").append(result).append('\n');
			}
			finally { victim.deleteMe(); }
		}
	}

	private static NpcTemplate template(int id)
	{
		final StatSet set = new StatSet(); set.set("id", id); set.set("displayId", 18342); set.set("level", 40); set.set("type", "Monster"); set.set("name", "Drop069"); set.set("baseHpMax", 1000); set.set("baseMpMax", 100); set.set("collisionRadius", 8); set.set("collisionHeight", 20);
		final var template = new NpcTemplate(set); template.setSkills(Map.of()); return template;
	}

	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
