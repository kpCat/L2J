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
package org.l2jmobius.gameserver.model.actor;

import java.util.Collection;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.FactionSystemConfig;
import org.l2jmobius.gameserver.config.custom.StartingLocationConfig;
import org.l2jmobius.gameserver.config.custom.StartingTitleConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.stat.PlayerStat;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.holders.InitialEquipment;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.network.PacketLogger;

/**
 * Canonical, transport-neutral initialization shared by client and managed
 * population character creation.
 */
public final class PlayerCreationInitializer
{
	public enum Mode
	{
		CLIENT,
		POPULATION
	}

	private PlayerCreationInitializer()
	{
	}

	public static void initialize(Player player, Mode mode)
	{
		if (player == null)
		{
			throw new IllegalArgumentException("Player must not be null.");
		}
		initialize(player, mode, resolveCreationLocation(player.getTemplate()));
	}

	public static void initialize(Player player, Mode mode, Location creationLocation)
	{
		if ((player == null) || (mode == null) || (creationLocation == null))
		{
			throw new IllegalArgumentException("Player, creation mode and creation location must not be null.");
		}

		// A pristine Player.create row has zero current HP. If population
		// creation resumes after that durable boundary, Player.load correctly
		// marks it dead; canonical creation must restore the not-yet-played
		// creation state before status setters can initialize its vitals.
		player.setDead(false);
		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
		player.setCurrentCp(0);

		if (PlayerConfig.STARTING_ADENA > 0)
		{
			player.addAdena(ItemProcessType.REWARD, PlayerConfig.STARTING_ADENA, null, false);
		}

		player.setXYZInvisible(creationLocation.getX(), creationLocation.getY(), creationLocation.getZ());
		player.setTitle(StartingTitleConfig.ENABLE_CUSTOM_STARTING_TITLE ? StartingTitleConfig.CUSTOM_STARTING_TITLE : "");

		if (PlayerConfig.ENABLE_VITALITY)
		{
			player.setVitalityPoints(Math.min(PlayerConfig.STARTING_VITALITY_POINTS, PlayerStat.MAX_VITALITY_POINTS), true);
		}

		if ((mode == Mode.CLIENT) && (PlayerConfig.STARTING_LEVEL > 1))
		{
			player.getStat().addLevel((byte) (PlayerConfig.STARTING_LEVEL - 1));
		}
		if ((mode == Mode.CLIENT) && (PlayerConfig.STARTING_SP > 0))
		{
			player.getStat().addSp(PlayerConfig.STARTING_SP);
		}

		final Collection<InitialEquipment> classEquipment = InitialEquipmentData.getInstance().getClassEquipment(player.getPlayerClass());
		if (classEquipment != null)
		{
			for (InitialEquipment equipment : classEquipment)
			{
				final Item item = player.getInventory().addItem(ItemProcessType.REWARD, equipment.getId(), equipment.getCount(), player, null);
				if (item == null)
				{
					PacketLogger.warning("Could not create item during player creation: itemId " + equipment.getId() + ", amount " + equipment.getCount() + ".");
					continue;
				}
				if (item.isEquipable() && equipment.isEquipped())
				{
					player.getInventory().equipItem(item);
				}
			}
		}

		for (SkillLearn skill : SkillTreeData.getInstance().getAvailableSkills(player, player.getPlayerClass(), false, true))
		{
			player.addSkill(SkillData.getInstance().getSkill(skill.getSkillId(), skill.getSkillLevel()), true);
		}

		InitialShortcutData.getInstance().registerAllShortcuts(player);

		// Equipment can change maximum vitals, so creation must finish at the
		// canonical full-health boundary required by both creation paths.
		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
		player.setCurrentCp(0);
	}

	public static Location resolveCreationLocation(PlayerTemplate template)
	{
		if (template == null)
		{
			throw new IllegalArgumentException("Player template must not be null.");
		}
		final Location configured;
		if (StartingLocationConfig.CUSTOM_STARTING_LOC)
		{
			configured = new Location(StartingLocationConfig.CUSTOM_STARTING_LOC_X, StartingLocationConfig.CUSTOM_STARTING_LOC_Y, StartingLocationConfig.CUSTOM_STARTING_LOC_Z);
		}
		else if (FactionSystemConfig.FACTION_SYSTEM_ENABLED)
		{
			configured = FactionSystemConfig.FACTION_STARTING_LOCATION;
		}
		else
		{
			configured = template.getCreationPoint();
		}

		final int x = Math.max(World.WORLD_X_MIN + 5000, Math.min(World.WORLD_X_MAX - 5000, configured.getX()));
		final int y = Math.max(World.WORLD_Y_MIN + 5000, Math.min(World.WORLD_Y_MAX - 5000, configured.getY()));
		return new Location(x, y, GeoEngine.getInstance().getHeight(x, y, configured.getZ()));
	}
}
