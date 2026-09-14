/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.config.custom.PersonalProgressionQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalProgressionQoLConfig.Settings;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.player.SubClassHolder;

/**
 * Bounded quest upper-level relief and canonical auto-Noblesse composition.
 */
public final class PersonalProgressionQoLService
{
	public static final int NOBLESSE_SUBCLASS_LEVEL = 75;

	private volatile Settings _testSettings;

	private PersonalProgressionQoLService()
	{
	}

	public boolean isQuestOverLevelReliefEnabled(Player player)
	{
		return settings().questOverLevelReliefEnabled() && PersonalCharacterQoLService.getInstance().isPersonalUser(player);
	}

	public boolean isAutoNoblesseEnabled()
	{
		return settings().autoNoblesseEnabled();
	}

	public boolean hasEligibleStoredSubclass(Player player)
	{
		if (player == null)
		{
			return false;
		}
		for (SubClassHolder subClass : player.getSubClasses().values())
		{
			if (subClass.getLevel() >= NOBLESSE_SUBCLASS_LEVEL)
			{
				return true;
			}
		}
		return false;
	}

	public boolean tryGrantAutoNoblesse(Player player)
	{
		if (!isAutoNoblesseEnabled() || (player == null) || player.isNoble() || !hasEligibleStoredSubclass(player))
		{
			return false;
		}

		synchronized (player)
		{
			if (player.isNoble() || !hasEligibleStoredSubclass(player))
			{
				return false;
			}
			player.setNoble(true);
			player.store(false);
			return true;
		}
	}

	Settings installForTests(Settings settings)
	{
		final Settings previous = _testSettings;
		_testSettings = settings;
		return previous;
	}

	Settings settings()
	{
		final Settings testSettings = _testSettings;
		return testSettings != null ? testSettings : PersonalProgressionQoLConfig.settings();
	}

	public static PersonalProgressionQoLService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalProgressionQoLService INSTANCE = new PersonalProgressionQoLService();
	}
}
