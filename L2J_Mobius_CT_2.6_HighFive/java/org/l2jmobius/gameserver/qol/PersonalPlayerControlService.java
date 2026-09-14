/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;

/** Authoritative per-character state for the Personal Board EXP and herb controls. */
public final class PersonalPlayerControlService
{
	public static final String EXPERIENCE_DISABLED_VARIABLE = "EXPOFF";
	private static final Map<HerbCategory, String> HERB_VARIABLES = Map.of(
		HerbCategory.RECOVERY, "PERSONAL_QOL_HERB_RECOVERY_DISABLED",
		HerbCategory.COMBAT, "PERSONAL_QOL_HERB_COMBAT_DISABLED",
		HerbCategory.VITALITY, "PERSONAL_QOL_HERB_VITALITY_DISABLED");
	private static final Set<Integer> RECOVERY_HERBS = Set.of(8154, 8155, 8600, 8601, 8602, 8603, 8604, 8605, 8614, 8952, 8953, 10432, 10433, 14777, 14779);
	private static final Set<Integer> VITALITY_HERBS = Set.of(13028, 13029, 13030, 13031, 20273, 20926);
	private static final Set<Integer> COMBAT_HERBS = Set.of(8156, 8157, 8606, 8607, 8608, 8609, 8610, 8611, 8612, 8613, 10655, 10656, 10657, 14778, 14824, 14825, 14826, 14827, 20272, 20274, 20770, 20771, 20772, 20927, 20928);

	private PersonalPlayerControlService()
	{
	}

	public boolean isExperienceGainEnabled(Player player)
	{
		return (player != null) && !player.getVariables().getBoolean(EXPERIENCE_DISABLED_VARIABLE, false);
	}

	public boolean setExperienceGainEnabled(Player player, boolean enabled)
	{
		if ((player == null) || (isExperienceGainEnabled(player) == enabled))
		{
			return false;
		}
		if (enabled)
		{
			player.enableExpGain();
		}
		else
		{
			player.disableExpGain();
		}
		player.getVariables().set(EXPERIENCE_DISABLED_VARIABLE, !enabled);
		player.getVariables().storeMe();
		player.sendMessage(enabled ? "Experience gain is enabled." : "Experience gain is disabled.");
		return true;
	}

	public void restoreExperienceGainState(Player player)
	{
		if ((player != null) && !isExperienceGainEnabled(player))
		{
			player.disableExpGain();
			player.sendMessage("Experience gain is disabled.");
		}
	}

	public boolean isHerbEnabled(Player player, HerbCategory category)
	{
		return (player != null) && (category != null) && !player.getVariables().getBoolean(HERB_VARIABLES.get(category), false);
	}

	public boolean setHerbEnabled(Player player, HerbCategory category, boolean enabled)
	{
		if ((player == null) || (category == null) || (isHerbEnabled(player, category) == enabled))
		{
			return false;
		}
		player.getVariables().set(HERB_VARIABLES.get(category), !enabled);
		player.getVariables().storeMe();
		return true;
	}

	/** Unknown immediate-effect items retain vanilla behavior. */
	public boolean shouldApplyImmediateEffect(Player player, ItemTemplate item)
	{
		if ((player == null) || (item == null) || player.hasHeadlessOutboundSession())
		{
			return true;
		}
		final HerbCategory category = categoryOf(item.getId());
		return (category == null) || isHerbEnabled(player, category);
	}

	public HerbCategory categoryOf(int itemId)
	{
		if (RECOVERY_HERBS.contains(itemId))
		{
			return HerbCategory.RECOVERY;
		}
		if (VITALITY_HERBS.contains(itemId))
		{
			return HerbCategory.VITALITY;
		}
		return COMBAT_HERBS.contains(itemId) ? HerbCategory.COMBAT : null;
	}

	public enum HerbCategory
	{
		RECOVERY("Восстановление HP/MP"),
		COMBAT("Боевые усиления"),
		VITALITY("Vitality");

		private final String _displayName;

		HerbCategory(String displayName)
		{
			_displayName = displayName;
		}

		public String displayName()
		{
			return _displayName;
		}
	}

	public static PersonalPlayerControlService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalPlayerControlService INSTANCE = new PersonalPlayerControlService();
	}
}
