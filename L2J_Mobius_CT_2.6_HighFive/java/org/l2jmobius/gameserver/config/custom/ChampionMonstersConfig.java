/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.config.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.enums.npc.ChampionType;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;

/**
 * This class loads all the custom champion monster related configurations.
 * @author Mobius
 */
public class ChampionMonstersConfig
{
	private static final Logger LOGGER = Logger.getLogger(ChampionMonstersConfig.class.getName());
	// File
	private static final String CHAMPION_MONSTERS_CONFIG_FILE = "./config/Custom/ChampionMonsters.ini";
	
	// Constants
	public static boolean CHAMPION_ENABLE;
	public static boolean CHAMPION_PASSIVE;
	public static int CHAMPION_FREQUENCY;
	public static String CHAMP_TITLE;
	public static boolean SHOW_CHAMPION_AURA;
	public static int CHAMP_MIN_LEVEL;
	public static int CHAMP_MAX_LEVEL;
	public static int CHAMPION_HP;
	public static float CHAMPION_HP_REGEN;
	public static float CHAMPION_REWARDS_EXP_SP;
	public static float CHAMPION_REWARDS_CHANCE;
	public static float CHAMPION_REWARDS_AMOUNT;
	public static float CHAMPION_ADENAS_REWARDS_CHANCE;
	public static float CHAMPION_ADENAS_REWARDS_AMOUNT;
	public static float CHAMPION_ATK;
	public static float CHAMPION_SPD_ATK;
	public static int CHAMPION_REWARD_LOWER_LEVEL_ITEM_CHANCE;
	public static int CHAMPION_REWARD_HIGHER_LEVEL_ITEM_CHANCE;
	public static List<ItemHolder> CHAMPION_REWARD_ITEMS = new ArrayList<>();
	public static boolean CHAMPION_ENABLE_VITALITY;
	public static boolean CHAMPION_ENABLE_IN_INSTANCES;
	public static boolean BLUE_CHAMPION_ENABLE;
	public static int BLUE_CHAMPION_FREQUENCY;
	public static Settings RED;
	public static Settings BLUE;
	public static float CHAMPION_SPOIL_CHANCE = 1;
	public static float CHAMPION_SPOIL_AMOUNT = 1;

	/** Immutable tier values; HP follows the stock damage-divisor contract. */
	public record Settings(boolean passive, String title, boolean aura, int minimumLevel, int maximumLevel, float hp, float hpRegen, float expSp, float dropChance, float dropAmount, float adenaChance, float adenaAmount, float sealChance, float sealAmount, float attack, float attackSpeed, float spoilChance, float spoilAmount, List<ItemHolder> rewards, int lowerRewardChance, int higherRewardChance, boolean vitality, boolean instances)
	{
	}

	public static Settings profile(Creature creature)
	{
		return creature.isAttackable() && (creature.asAttackable().getChampionType() == ChampionType.BLUE) ? BLUE : RED;
	}

	public static ChampionType select(Npc npc, int instanceId)
	{
		if (!CHAMPION_ENABLE && !BLUE_CHAMPION_ENABLE)
		{
			return ChampionType.NORMAL;
		}
		final boolean red = eligible(npc, instanceId, CHAMPION_ENABLE, RED);
		final boolean blue = eligible(npc, instanceId, BLUE_CHAMPION_ENABLE, BLUE);
		if (!red && !blue)
		{
			return ChampionType.NORMAL;
		}
		return ChampionSelectionPolicy.select(CHAMPION_ENABLE ? CHAMPION_FREQUENCY : 0, BLUE_CHAMPION_ENABLE ? BLUE_CHAMPION_FREQUENCY : 0, red, blue, Rnd.get(100));
	}

	private static boolean eligible(Npc npc, int instanceId, boolean enabled, Settings settings)
	{
		return enabled && (settings != null) && ChampionSelectionPolicy.eligible(npc.isMonster(), npc.isQuestMonster(), npc.getTemplate().isUndying(), npc.isRaid(), npc.isMinion() || npc.isRaidMinion(), npc.getLevel(), instanceId, enabled, settings.minimumLevel(), settings.maximumLevel(), settings.instances());
	}

	public static boolean isSealStone(int itemId)
	{
		return (itemId == 6360) || (itemId == 6361) || (itemId == 6362);
	}

	/** Blue currencies use their explicit rates even when a server item-id override exists. */
	public static float dropChance(Creature creature, int itemId, boolean itemSpecific)
	{
		final Settings settings = profile(creature);
		if (creature.asAttackable().getChampionType() == ChampionType.BLUE)
		{
			return itemId == 57 ? settings.adenaChance() : isSealStone(itemId) ? settings.sealChance() : settings.dropChance();
		}
		return itemSpecific ? itemId == 57 ? settings.adenaChance() : 1 : settings.dropChance();
	}

	public static float dropAmount(Creature creature, int itemId, boolean itemSpecific)
	{
		final Settings settings = profile(creature);
		if (creature.asAttackable().getChampionType() == ChampionType.BLUE)
		{
			return itemId == 57 ? settings.adenaAmount() : isSealStone(itemId) ? settings.sealAmount() : settings.dropAmount();
		}
		return itemSpecific ? itemId == 57 ? settings.adenaAmount() : 1 : settings.dropAmount();
	}
	
	public static void load()
	{
		final ConfigReader config = new ConfigReader(CHAMPION_MONSTERS_CONFIG_FILE);
		CHAMPION_ENABLE = config.getBoolean("ChampionEnable", false);
		CHAMPION_PASSIVE = config.getBoolean("ChampionPassive", false);
		CHAMPION_FREQUENCY = config.getInt("ChampionFrequency", 0);
		CHAMP_TITLE = config.getString("ChampionTitle", "Champion");
		SHOW_CHAMPION_AURA = config.getBoolean("ChampionAura", true);
		CHAMP_MIN_LEVEL = config.getInt("ChampionMinLevel", 20);
		CHAMP_MAX_LEVEL = config.getInt("ChampionMaxLevel", 60);
		CHAMPION_HP = config.getInt("ChampionHp", 7);
		CHAMPION_HP_REGEN = config.getFloat("ChampionHpRegen", 1);
		CHAMPION_REWARDS_EXP_SP = config.getFloat("ChampionRewardsExpSp", 8);
		CHAMPION_REWARDS_CHANCE = config.getFloat("ChampionRewardsChance", 8);
		CHAMPION_REWARDS_AMOUNT = config.getFloat("ChampionRewardsAmount", 1);
		CHAMPION_ADENAS_REWARDS_CHANCE = config.getFloat("ChampionAdenasRewardsChance", 1);
		CHAMPION_ADENAS_REWARDS_AMOUNT = config.getFloat("ChampionAdenasRewardsAmount", 1);
		CHAMPION_ATK = config.getFloat("ChampionAtk", 1);
		CHAMPION_SPD_ATK = config.getFloat("ChampionSpdAtk", 1);
		CHAMPION_REWARD_LOWER_LEVEL_ITEM_CHANCE = config.getInt("ChampionRewardLowerLvlItemChance", 0);
		CHAMPION_REWARD_HIGHER_LEVEL_ITEM_CHANCE = config.getInt("ChampionRewardHigherLvlItemChance", 0);
		
		CHAMPION_REWARD_ITEMS.clear();
		for (String s : config.getString("ChampionRewardItems", "4356,10").split(";"))
		{
			if (s.isEmpty())
			{
				continue;
			}
			CHAMPION_REWARD_ITEMS.add(new ItemHolder(Integer.parseInt(s.split(",")[0]), Integer.parseInt(s.split(",")[1])));
		}
		
		CHAMPION_ENABLE_VITALITY = config.getBoolean("ChampionEnableVitality", false);
		CHAMPION_ENABLE_IN_INSTANCES = config.getBoolean("ChampionEnableInInstances", false);
		// Stock red never multiplied spoil table chance/amount; missing new keys preserve that.
		CHAMPION_SPOIL_CHANCE = redSpoil(config.getFloat("ChampionSpoilChance", 1));
		CHAMPION_SPOIL_AMOUNT = redSpoil(config.getFloat("ChampionSpoilAmount", 1));
		RED = new Settings(CHAMPION_PASSIVE, CHAMP_TITLE, SHOW_CHAMPION_AURA, CHAMP_MIN_LEVEL, CHAMP_MAX_LEVEL, CHAMPION_HP, CHAMPION_HP_REGEN, CHAMPION_REWARDS_EXP_SP, CHAMPION_REWARDS_CHANCE, CHAMPION_REWARDS_AMOUNT, CHAMPION_ADENAS_REWARDS_CHANCE, CHAMPION_ADENAS_REWARDS_AMOUNT, CHAMPION_REWARDS_CHANCE, CHAMPION_REWARDS_AMOUNT, CHAMPION_ATK, CHAMPION_SPD_ATK, CHAMPION_SPOIL_CHANCE, CHAMPION_SPOIL_AMOUNT, List.copyOf(CHAMPION_REWARD_ITEMS), CHAMPION_REWARD_LOWER_LEVEL_ITEM_CHANCE, CHAMPION_REWARD_HIGHER_LEVEL_ITEM_CHANCE, CHAMPION_ENABLE_VITALITY, CHAMPION_ENABLE_IN_INSTANCES);
		BLUE_CHAMPION_ENABLE = config.getBoolean("BlueChampionEnable", false);
		BLUE_CHAMPION_FREQUENCY = config.getInt("BlueChampionFrequency", 2);
		try
		{
			final List<ItemHolder> blueRewards = new ArrayList<>();
			for (String reward : config.getString("BlueChampionRewardItems", "").split(";"))
			{
				if (!reward.isBlank())
				{
					final String[] parts = reward.trim().split(",");
					final int id = Integer.parseInt(parts[0].trim());
					final long amount = Long.parseLong(parts[1].trim());
					if ((parts.length != 2) || (id <= 0) || (amount <= 0))
					{
						throw new IllegalArgumentException("Invalid BlueChampionRewardItems");
					}
					blueRewards.add(new ItemHolder(id, amount));
				}
			}
			BLUE = new Settings(config.getBoolean("BlueChampionPassive", false), config.getString("BlueChampionTitle", "Blue Champion"), config.getBoolean("BlueChampionAura", true), config.getInt("BlueChampionMinLevel", 20), config.getInt("BlueChampionMaxLevel", 70), positive(config.getFloat("BlueChampionHp", 2)), positive(config.getFloat("BlueChampionHpRegen", 1)), positive(config.getFloat("BlueChampionRewardsExpSp", 2)), positive(config.getFloat("BlueChampionRewardsChance", 2)), positive(config.getFloat("BlueChampionRewardsAmount", 1)), positive(config.getFloat("BlueChampionAdenasRewardsChance", 1)), positive(config.getFloat("BlueChampionAdenasRewardsAmount", 1)), positive(config.getFloat("BlueChampionSealStonesRewardsChance", 1)), positive(config.getFloat("BlueChampionSealStonesRewardsAmount", 1)), positive(config.getFloat("BlueChampionAtk", 1)), positive(config.getFloat("BlueChampionSpdAtk", 1)), positive(config.getFloat("BlueChampionSpoilChance", 1)), positive(config.getFloat("BlueChampionSpoilAmount", 1)), List.copyOf(blueRewards), probability(config.getInt("BlueChampionRewardLowerLvlItemChance", 0)), probability(config.getInt("BlueChampionRewardHigherLvlItemChance", 0)), config.getBoolean("BlueChampionEnableVitality", false), config.getBoolean("BlueChampionEnableInInstances", false));
			if ((BLUE.minimumLevel() < 1) || (BLUE.maximumLevel() < BLUE.minimumLevel()) || (BLUE.hp() <= 0) || !ChampionSelectionPolicy.validFrequencies(CHAMPION_ENABLE ? CHAMPION_FREQUENCY : 0, BLUE_CHAMPION_ENABLE ? BLUE_CHAMPION_FREQUENCY : 0))
			{
				throw new IllegalArgumentException("Invalid levels, HP or final champion frequencies (sum must be <= 100)");
			}
		}
		catch (RuntimeException e)
		{
			BLUE_CHAMPION_ENABLE = false;
			BLUE = RED;
			LOGGER.warning("Blue champions disabled: " + e.getMessage());
		}
	}

	private static float positive(float value)
	{
		if (!Float.isFinite(value) || (value < 0))
		{
			throw new IllegalArgumentException("Champion multiplier must be finite and nonnegative");
		}
		return value;
	}

	private static float redSpoil(float value)
	{
		if (!Float.isFinite(value) || (value < 0))
		{
			LOGGER.warning("Invalid red champion spoil multiplier; preserving the stock 1.0 multiplier.");
			return 1;
		}
		return value;
	}

	private static int probability(int value)
	{
		if ((value < 0) || (value > 100))
		{
			throw new IllegalArgumentException("Champion probability must be in 0..100");
		}
		return value;
	}
}
