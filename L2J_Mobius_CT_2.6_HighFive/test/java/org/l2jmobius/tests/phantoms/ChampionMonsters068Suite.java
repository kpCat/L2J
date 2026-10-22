/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.network.ResourcePool;
import org.l2jmobius.commons.network.internal.ArrayPacketBuffer;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.ChampionMonstersConfig;
import org.l2jmobius.gameserver.config.custom.ChampionMonstersConfig.Settings;
import org.l2jmobius.gameserver.config.custom.MultilingualSupportConfig;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.enums.creature.Team;
import org.l2jmobius.gameserver.model.actor.enums.npc.ChampionType;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo;

/** Native champion reset regression; uses the existing guarded test environment. */
public final class ChampionMonsters068Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "champion068";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		if (!PhantomTask068Lane.enabled())
		{
			throw new IllegalArgumentException("Champion native proof requires the exact own068 lane.");
		}
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(context);
	}

	@Override
	public void afterAll(PhantomTestContext context)
	{
		ThreadPool.shutdown();
		DatabaseFactory.close();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-respawn-clears-aura-after-config-disable", this::auraReset);
		registry.add("02-native-tiers-hp-drop-spoil-packet-and-respawn", this::nativeTiers);
	}

	private void nativeTiers(PhantomTestContext context) throws Exception
	{
		final Settings previousBlue = ChampionMonstersConfig.BLUE;
		final boolean previousRedEnabled = ChampionMonstersConfig.CHAMPION_ENABLE;
		final boolean previousBlueEnabled = ChampionMonstersConfig.BLUE_CHAMPION_ENABLE;
		final int previousRedFrequency = ChampionMonstersConfig.CHAMPION_FREQUENCY;
		final int previousBlueFrequency = ChampionMonstersConfig.BLUE_CHAMPION_FREQUENCY;
		final boolean previousMultilang = MultilingualSupportConfig.MULTILANG_ENABLE;
		final float dropChance = RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER;
		final float dropAmount = RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER;
		final float spoilChance = RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;
		final float spoilAmount = RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER;
		// Declared component TEST inputs, installed before the measured tier rows.
		ChampionMonstersConfig.BLUE = new Settings(false, "Blue068", true, 1, 85, 2, 2, 3, 2, 3, 4, 5, 6, 7, 1.2f, 1.1f, 2, 4, List.of(), 0, 0, true, false);
		MultilingualSupportConfig.MULTILANG_ENABLE = false;
		RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER = 1;
		RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER = 1;
		RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = 1;
		RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER = 1;
		final NpcTemplate template = template();
		template.addDrop(new DropHolder(DropType.DROP, 1864, 10, 10, 100));
		template.addSpoil(new DropHolder(DropType.SPOIL, 1866, 10, 10, 100));
		final Spawn spawn = new Spawn(template);
		spawn.setXYZ(-71350, 258050, -3104);
		Monster monster = null;
		final Monster attacker = new Monster(template());
		try
		{
			for (ChampionType tier : ChampionType.values())
			{
				// Force each categorical outcome before its spawn, rather than wait for RNG.
				ChampionMonstersConfig.CHAMPION_ENABLE = tier == ChampionType.RED;
				ChampionMonstersConfig.BLUE_CHAMPION_ENABLE = tier == ChampionType.BLUE;
				ChampionMonstersConfig.CHAMPION_FREQUENCY = tier == ChampionType.RED ? 100 : 0;
				ChampionMonstersConfig.BLUE_CHAMPION_FREQUENCY = tier == ChampionType.BLUE ? 100 : 0;
				if (monster == null)
				{
					monster = (Monster) spawn.doSpawn(false);
					PhantomAssertions.assertTrue(monster != null, "Native champion fixture did not spawn.");
				}
				else
				{
					monster.decayMe();
					spawn.respawnNpc(monster);
				}
				PhantomAssertions.assertEquals(tier, monster.getChampionType(), "Native spawn selection chose the wrong tier.");
				final double hpBefore = monster.getCurrentHp();
				final double divisor = tier == ChampionType.NORMAL ? 1 : ChampionMonstersConfig.profile(monster).hp();
				monster.reduceCurrentHp(80, attacker, null);
				PhantomAssertions.assertTrue(Math.abs((hpBefore - monster.getCurrentHp()) - (80 / divisor)) < 0.001, "Native HP damage divisor differed for " + tier);
				final long expectedDrop = tier == ChampionType.NORMAL ? 10 : (long) (10 * ChampionMonstersConfig.profile(monster).dropAmount());
				final long expectedSpoil = tier == ChampionType.NORMAL ? 10 : (long) (10 * ChampionMonstersConfig.profile(monster).spoilAmount());
				PhantomAssertions.assertEquals(expectedDrop, count(template.calculateDrops(DropType.DROP, monster, attacker), 1864), "Native death reward amount differed for " + tier);
				PhantomAssertions.assertEquals(expectedSpoil, count(template.calculateDrops(DropType.SPOIL, monster, attacker), 1866), "Native spoil reward amount differed for " + tier);
				final int expectedTeam = tier == ChampionType.NORMAL ? 0 : tier == ChampionType.BLUE ? 1 : ChampionMonstersConfig.SHOW_CHAMPION_AURA ? 2 : 0;
				final ArrayPacketBuffer buffer = new ArrayPacketBuffer(new ResourcePool(), NpcInfo.class);
				new NpcInfo(monster, attacker).writeImpl(null, buffer);
				buffer.limit(buffer.position());
				buffer.position(126); // Stock H5 fixed fields through NPCString name id.
				readString(buffer);
				buffer.readInt();
				final String title = readString(buffer);
				buffer.position(buffer.position() + 33); // Eight ints and water movement byte.
				PhantomAssertions.assertEquals(expectedTeam, Byte.toUnsignedInt(buffer.readByte()), "Native NPC_INFO team byte differed for " + tier);
				if (tier != ChampionType.NORMAL)
				{
					PhantomAssertions.assertTrue(title.contains(ChampionMonstersConfig.profile(monster).title()), "Native NPC_INFO omitted tier title.");
				}
				context.record("champion068.native." + tier, "damage=" + (hpBefore - monster.getCurrentHp()) + ";drop=" + expectedDrop + ";spoil=" + expectedSpoil + ";packetTeam=" + expectedTeam + ";title=" + title);
			}
			ChampionMonstersConfig.CHAMPION_ENABLE = false;
			ChampionMonstersConfig.BLUE_CHAMPION_ENABLE = false;
			monster.decayMe();
			spawn.respawnNpc(monster);
			PhantomAssertions.assertEquals(ChampionType.NORMAL, monster.getChampionType(), "Blue respawn retained tier.");
			PhantomAssertions.assertEquals(Team.NONE, monster.getTeam(), "Blue respawn retained aura.");
			PhantomAssertions.assertTrue(Math.abs(monster.getMaxHp() - monster.getCurrentHp()) < 0.001, "Respawn did not restore native HP.");
			context.record("champion068.native.reset", "BLUE->NORMAL;team=0;HP=max;CLIENT_RENDERING=DEFERRED");
		}
		finally
		{
			spawn.stopRespawn();
			if (monster != null) { monster.deleteMe(); }
			attacker.deleteMe();
			ChampionMonstersConfig.BLUE = previousBlue;
			ChampionMonstersConfig.CHAMPION_ENABLE = previousRedEnabled;
			ChampionMonstersConfig.BLUE_CHAMPION_ENABLE = previousBlueEnabled;
			ChampionMonstersConfig.CHAMPION_FREQUENCY = previousRedFrequency;
			ChampionMonstersConfig.BLUE_CHAMPION_FREQUENCY = previousBlueFrequency;
			MultilingualSupportConfig.MULTILANG_ENABLE = previousMultilang;
			RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER = dropChance;
			RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER = dropAmount;
			RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER = spoilChance;
			RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER = spoilAmount;
		}
	}

	private static long count(List<ItemHolder> items, int id)
	{
		return items == null ? 0 : items.stream().filter(item -> item.getId() == id).mapToLong(ItemHolder::getCount).sum();
	}

	private static String readString(ArrayPacketBuffer buffer)
	{
		final StringBuilder result = new StringBuilder();
		char value;
		while ((value = buffer.readChar()) != 0)
		{
			result.append(value);
		}
		return result.toString();
	}

	private void auraReset(PhantomTestContext context)
	{
		final boolean original = ChampionMonstersConfig.SHOW_CHAMPION_AURA;
		final Monster monster = new Monster(template());
		try
		{
			ChampionMonstersConfig.SHOW_CHAMPION_AURA = true;
			monster.setChampion(true);
			PhantomAssertions.assertEquals(Team.RED, monster.getTeam(), "Red champion aura was not assigned.");
			ChampionMonstersConfig.SHOW_CHAMPION_AURA = false;
			monster.setChampion(false);
			PhantomAssertions.assertFalse(monster.isChampion(), "Respawn retained champion state.");
			PhantomAssertions.assertEquals(Team.NONE, monster.getTeam(), "Disabled aura retained the previous spawn's red team.");
			context.record("champion068.auraReset", monster.getTeam());
		}
		finally
		{
			ChampionMonstersConfig.SHOW_CHAMPION_AURA = original;
			monster.deleteMe();
		}
	}

	private static NpcTemplate template()
	{
		final StatSet set = new StatSet();
		set.set("id", 18342);
		set.set("displayId", 18342);
		set.set("level", 40);
		set.set("type", "Monster");
		set.set("name", "Champion068Fixture");
		set.set("baseHpMax", 1000);
		set.set("baseMpMax", 100);
		set.set("collisionRadius", 8);
		set.set("collisionHeight", 20);
		final NpcTemplate template = new NpcTemplate(set);
		template.setSkills(Map.of());
		return template;
	}
}
