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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.FactionSystemConfig;
import org.l2jmobius.gameserver.config.custom.StartingLocationConfig;
import org.l2jmobius.gameserver.config.custom.StartingTitleConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.DeliveryMode;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.InitialPlan;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData.RegistrationKind;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.stat.PlayerStat;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.holders.InitialEquipment;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.network.PacketLogger;

/**
 * Canonical, transport-neutral initialization shared by client and managed
 * population character creation.
 */
public final class PlayerCreationInitializer
{
	public static final String VERSION = "PLAYER_CREATION_INITIALIZER_V1";

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

		apply(player, mode, resolvePlan(player.getPlayerClass(), mode, creationLocation), Set.of(), PopulationInitializationObserver.noop());
	}

	public static void initializePopulation(Player player, CreationPlan plan, PopulationInitializationObserver observer)
	{
		initializePopulation(player, plan, Set.of(), observer);
	}

	public static void initializePopulation(Player player, CreationPlan plan, Set<Integer> existingMacroIds, PopulationInitializationObserver observer)
	{
		if ((player == null) || (plan == null) || (existingMacroIds == null) || (observer == null) || (plan.mode() != Mode.POPULATION))
		{
			throw new IllegalArgumentException("Population player, plan and observer must be valid.");
		}
		apply(player, Mode.POPULATION, plan, existingMacroIds, observer);
	}

	public static void preparePopulationCharacterRow(int objectId, CreationPlan plan)
	{
		if ((objectId <= 0) || (plan == null) || (plan.mode() != Mode.POPULATION))
		{
			throw new IllegalArgumentException("Population character row preparation requires a positive object ID and POPULATION plan.");
		}
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=maxHp,curMp=maxMp,curCp=0,x=?,y=?,z=?,title=?,vitality_points=? WHERE charId=? AND curHp=0 AND curMp=0 AND curCp=0 AND (title='' OR title IS NULL)"))
		{
			statement.setInt(1, plan.creationLocation().getX());
			statement.setInt(2, plan.creationLocation().getY());
			statement.setInt(3, plan.creationLocation().getZ());
			statement.setString(4, plan.title());
			statement.setInt(5, plan.vitalityPoints());
			statement.setInt(6, objectId);
			statement.executeUpdate();
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Could not prepare the transport-neutral population character row.", e);
		}
	}

	public static CreationPlan resolvePlan(PlayerClass playerClass, Mode mode, Location creationLocation)
	{
		if ((playerClass == null) || (mode == null) || (creationLocation == null))
		{
			throw new IllegalArgumentException("Player class, creation mode and creation location must not be null.");
		}
		final int level = mode == Mode.CLIENT ? PlayerConfig.STARTING_LEVEL : 1;
		final long sp = mode == Mode.CLIENT ? PlayerConfig.STARTING_SP : 0;
		final long adena = Math.max(0, PlayerConfig.STARTING_ADENA);
		final String title = StartingTitleConfig.ENABLE_CUSTOM_STARTING_TITLE ? StartingTitleConfig.CUSTOM_STARTING_TITLE : "";
		final boolean vitalityEnabled = PlayerConfig.ENABLE_VITALITY;
		final int vitalityPoints = mode == Mode.POPULATION ? PlayerStat.MIN_VITALITY_POINTS : Math.min(PlayerConfig.STARTING_VITALITY_POINTS, PlayerStat.MAX_VITALITY_POINTS);
		final Collection<InitialEquipment> classEquipment = InitialEquipmentData.getInstance().getClassEquipment(playerClass);
		final List<InitialItemPlan> items = classEquipment == null ? List.of() : classEquipment.stream().map(equipment -> new InitialItemPlan(equipment.getId(), equipment.getCount(), equipment.isEquipped())).toList();
		final List<InitialSkillPlan> skills = SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream()
			.filter(skill -> (skill.getGetLevel() <= level) && (skill.getSkillLevel() == 1))
			.filter(skill -> skill.isAutoGet() || skill.isLearnedByNpc())
			.filter(skill -> (skill.getSkillId() != CommonSkill.DIVINE_INSPIRATION.getId()) || PlayerConfig.AUTO_LEARN_DIVINE_INSPIRATION)
			.map(skill -> new InitialSkillPlan(skill.getSkillId(), skill.getSkillLevel()))
			.toList();
		final Set<Integer> itemIds = new LinkedHashSet<>();
		items.forEach(item -> itemIds.add(item.itemId()));
		if (adena > 0)
		{
			itemIds.add(Inventory.ADENA_ID);
		}
		final Set<Integer> skillIds = new LinkedHashSet<>();
		skills.forEach(skill -> skillIds.add(skill.skillId()));
		final InitialPlan shortcuts = InitialShortcutData.getInstance().resolvePlan(playerClass, itemIds::contains, skillIds::contains);
		return new CreationPlan(
			mode,
			level,
			sp,
			adena,
			creationLocation,
			title,
			vitalityEnabled,
			vitalityPoints,
			PlayerConfig.STARTING_LEVEL,
			PlayerConfig.STARTING_SP,
			items,
			skills,
			shortcuts);
	}

	private static void apply(Player player, Mode mode, CreationPlan plan, Set<Integer> existingMacroIds, PopulationInitializationObserver observer)
	{
		// A pristine Player.create row has zero current HP. If population
		// creation resumes after that durable boundary, Player.load correctly
		// marks it dead; canonical creation must restore the not-yet-played
		// creation state before status setters can initialize its vitals.
		player.setDead(false);
		setCanonicalVitals(player, mode);

		if (plan.adena() > 0)
		{
			if (mode == Mode.CLIENT)
			{
				player.addAdena(ItemProcessType.REWARD, plan.adena(), null, false);
			}
			else
			{
				player.getInventory().addItem(ItemProcessType.REWARD, Inventory.ADENA_ID, plan.adena(), null, null);
			}
			observer.afterAdena();
		}

		player.setXYZInvisible(plan.creationLocation().getX(), plan.creationLocation().getY(), plan.creationLocation().getZ());
		player.setTitle(plan.title());

		if (((mode == Mode.POPULATION) && (player.getVitalityPoints() != plan.vitalityPoints())) || (plan.vitalityEnabled() && ((mode == Mode.CLIENT) || (player.getVitalityPoints() != plan.vitalityPoints()))))
		{
			player.setVitalityPoints(plan.vitalityPoints(), true);
		}

		if ((mode == Mode.CLIENT) && (plan.level() > 1))
		{
			player.getStat().addLevel((byte) (plan.level() - 1));
		}
		if ((mode == Mode.CLIENT) && (plan.sp() > 0))
		{
			player.getStat().addSp(plan.sp());
		}

		int itemOrdinal = 0;
		for (InitialItemPlan equipment : plan.items())
		{
			final Item item = player.getInventory().addItem(ItemProcessType.REWARD, equipment.itemId(), equipment.count(), mode == Mode.CLIENT ? player : null, null);
			if (item == null)
			{
				PacketLogger.warning("Could not create item during player creation: itemId " + equipment.itemId() + ", amount " + equipment.count() + ".");
				continue;
			}
			if (item.isEquipable() && equipment.equipped())
			{
				if (mode == Mode.CLIENT)
				{
					player.getInventory().equipItem(item);
				}
				else
				{
					final int paperdollSlot = BodyPart.getPaperdollIndex(item.getTemplate().getBodyPart());
					if (paperdollSlot < 0)
					{
						throw new IllegalStateException("Population initial equipment has no deterministic paperdoll slot.");
					}
					item.setItemLocation(ItemLocation.PAPERDOLL, paperdollSlot, false);
					item.updateDatabase();
				}
			}
			observer.afterItem(itemOrdinal++);
		}

		for (InitialSkillPlan skill : plan.skills())
		{
			player.addSkill(SkillData.getInstance().getSkill(skill.skillId(), skill.skillLevel()), true);
		}
		if (!plan.skills().isEmpty())
		{
			observer.afterSkills();
		}

		InitialShortcutData.getInstance().registerResolvedPlan(player, plan.shortcuts(), mode == Mode.CLIENT ? DeliveryMode.CLIENT : DeliveryMode.POPULATION, existingMacroIds, (kind, ordinal) ->
		{
			if (kind == RegistrationKind.SHORTCUT)
			{
				observer.afterShortcut(ordinal);
			}
			else
			{
				observer.afterMacro(ordinal);
			}
		});

		// Equipment can change maximum vitals, so creation must finish at the
		// canonical full-health boundary required by both creation paths.
		setCanonicalVitals(player, mode);
	}

	private static void setCanonicalVitals(Player player, Mode mode)
	{
		if (mode == Mode.CLIENT)
		{
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			player.setCurrentCp(0);
		}
		else
		{
			player.getStatus().setCurrentHp(player.getMaxHp(), false);
			player.getStatus().setCurrentMp(player.getMaxMp(), false);
			player.getStatus().setCurrentCp(0, false);
		}
	}

	public record CreationPlan(Mode mode, int level, long sp, long adena, Location creationLocation, String title, boolean vitalityEnabled, int vitalityPoints, int configuredStartingLevel, long configuredStartingSp, List<InitialItemPlan> items, List<InitialSkillPlan> skills, InitialPlan shortcuts)
	{
		public CreationPlan
		{
			Objects.requireNonNull(mode, "Creation mode must not be null.");
			Objects.requireNonNull(creationLocation, "Creation location must not be null.");
			title = Objects.requireNonNull(title, "Creation title must not be null.");
			items = List.copyOf(items);
			skills = List.copyOf(skills);
			Objects.requireNonNull(shortcuts, "Initial shortcut plan must not be null.");
		}
	}

	public record InitialItemPlan(int itemId, long count, boolean equipped)
	{
	}

	public record InitialSkillPlan(int skillId, int skillLevel)
	{
	}

	public interface PopulationInitializationObserver
	{
		void afterAdena();

		void afterItem(int ordinal);

		void afterSkills();

		void afterShortcut(int ordinal);

		void afterMacro(int ordinal);

		static PopulationInitializationObserver noop()
		{
			return new PopulationInitializationObserver()
			{
				@Override
				public void afterAdena()
				{
				}

				@Override
				public void afterItem(int ordinal)
				{
				}

				@Override
				public void afterSkills()
				{
				}

				@Override
				public void afterShortcut(int ordinal)
				{
				}

				@Override
				public void afterMacro(int ordinal)
				{
				}
			};
		}
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
