/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.Locale;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.HennaNegativeMode;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.data.holders.AccessLevel;
import org.l2jmobius.gameserver.model.sevensigns.SevenSigns;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;

/**
 * Per-character admission policy for personal QoL features.
 */
public final class PersonalCharacterQoLService
{
	private volatile Settings _testSettings;

	private PersonalCharacterQoLService()
	{
	}

	public boolean isPersonalUser(Player player)
	{
		return isPersonalUser(player, settings());
	}

	boolean isPersonalUser(Player player, Settings settings)
	{
		if (!settings.enabled() || (player == null) || player.hasHeadlessOutboundSession())
		{
			return false;
		}
		final String accountName = player.getAccountName();
		return settings.allowedCharacterIds().contains(player.getObjectId()) || ((accountName != null) && settings.allowedAccounts().contains(accountName.trim().toLowerCase(Locale.ROOT)));
	}

	public boolean isCrossClassSkillsEnabled(Player player)
	{
		return settings().crossClassSkillsEnabled() && isPersonalUser(player) && !player.isSubClassActive();
	}

	private boolean isAdventureUser(Player player, Settings settings)
	{
		if (!isPersonalUser(player, settings) || !isOrdinaryAdventureAccess(player.getAccessLevel())) { return false; }
		final OwnerKind owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId());
		return (owner != OwnerKind.PHANTOM) && (owner != OwnerKind.BACKGROUND);
	}

	static boolean isOrdinaryAdventureAccess(AccessLevel access)
	{
		return (access != null) && (access.getLevel() == 0) && !access.isGm();
	}

	public boolean isSoloInstanceEntryEnabled(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().soloInstanceEntry() && isAdventureUser(player, settings);
	}

	public boolean isSoloRiftEnabled(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().soloRift() && isAdventureUser(player, settings);
	}

	public boolean isWondrousCubicCooldownEnabled(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().wondrousCubicCooldown() && isAdventureUser(player, settings);
	}

	public long getWondrousCubicCooldownMillis(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().wondrousCubicCooldown() && isAdventureUser(player, settings) ? settings.adventureSettings().wondrousCubicCooldownMillis() : 86400000L;
	}

	public boolean isFishingAssistEnabled(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().fishingAssist() && isAdventureUser(player, settings);
	}

	public HennaNegativeMode getHennaNegativeMode(Player player)
	{
		final Settings settings = settings();
		return isAdventureUser(player, settings) ? settings.adventureSettings().hennaNegativeMode() : HennaNegativeMode.OFF;
	}

	public double getPositiveAugmentationMultiplier(Player player)
	{
		final Settings settings = settings();
		return settings.adventureSettings().positiveAugmentation() && isAdventureUser(player, settings) ? settings.adventureSettings().positiveAugmentationMultiplier() : 1.0;
	}

	public boolean isCrystallizationEnabled(Player player)
	{
		return settings().crystallizationEnabled() && isPersonalUser(player);
	}

	public boolean isSevenSignsAccessEnabled(Player player)
	{
		return settings().sevenSignsAccessEnabled() && isPersonalUser(player);
	}

	public boolean isPartySupportEnabled(Player player)
	{
		return settings().partySupportEnabled() && isPersonalUser(player);
	}

	public boolean isSevenSignsRegistered(Player player, int playerCabal)
	{
		return (playerCabal != SevenSigns.CABAL_NULL) || isSevenSignsAccessEnabled(player);
	}

	public boolean isSevenSignsCabalEligible(Player player, int playerCabal, int requiredCabal)
	{
		return (playerCabal == requiredCabal) || isSevenSignsAccessEnabled(player);
	}

	public boolean isSevenSignsWinningSealEligible(Player player, int playerCabal, int competitionWinner, int sealOwner)
	{
		return ((competitionWinner == SevenSigns.CABAL_DAWN) || (competitionWinner == SevenSigns.CABAL_DUSK)) && (sealOwner == competitionWinner) && isSevenSignsCabalEligible(player, playerCabal, competitionWinner);
	}

	public boolean isAnyFeatureEnabled(Player player)
	{
		final Settings settings = settings();
		return isPersonalUser(player) && (settings.crossClassSkillsEnabled() || settings.crystallizationEnabled() || settings.sevenSignsAccessEnabled() || settings.partySupportEnabled() || (isAdventureUser(player, settings) && (settings.adventureSettings().soloInstanceEntry() || settings.adventureSettings().soloRift() || settings.adventureSettings().wondrousCubicCooldown() || settings.adventureSettings().fishingAssist() || settings.adventureSettings().hennaNegativeMode() != HennaNegativeMode.OFF || settings.adventureSettings().positiveAugmentation())));
	}

	public boolean canOpenAlternativeSkillList(Player player, Npc trainer)
	{
		return PlayerConfig.ALT_GAME_SKILL_LEARN || ((trainer instanceof Folk) && isCrossClassSkillsEnabled(player));
	}

	public boolean canAcquireClassSkill(Player player, Npc trainer, PlayerClass learningClass)
	{
		if ((player == null) || (learningClass == null))
		{
			return false;
		}
		if (learningClass == player.getPlayerClass())
		{
			return true;
		}
		if (PlayerConfig.ALT_GAME_SKILL_LEARN)
		{
			return true;
		}
		return isCrossClassSkillsEnabled(player) && (trainer instanceof Folk) && trainer.getTemplate().canTeach(learningClass) && (learningClass.level() <= player.getPlayerClass().level());
	}

	public boolean usesAlternativeSkillPrice(Player player, Npc trainer, PlayerClass learningClass)
	{
		return (player != null) && (learningClass != null) && (learningClass != player.getPlayerClass()) && canAcquireClassSkill(player, trainer, learningClass);
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
		return testSettings != null ? testSettings : PersonalCharacterQoLConfig.settings();
	}

	public static PersonalCharacterQoLService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalCharacterQoLService INSTANCE = new PersonalCharacterQoLService();
	}
}
