/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.EffectDurationSettings;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.Category;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.SkillTraits;

/**
 * Runtime adapter between a concrete effect recipient and the pure duration policy.
 */
public final class PersonalEffectDurationService
{
	private PersonalEffectDurationService()
	{
	}

	public int adjustAbnormalTime(Creature effector, Creature recipient, Skill skill, int stockSeconds, boolean cast)
	{
		final PersonalCharacterQoLService personalQoL = PersonalCharacterQoLService.getInstance();
		final Settings settings = personalQoL.settings();
		final EffectDurationSettings durationSettings = settings.effectDurationSettings();
		if (!durationSettings.valid() || !durationSettings.enabled() || (skill == null))
		{
			return stockSeconds;
		}

		final boolean personalRecipient = (recipient instanceof Player player) && personalQoL.isPersonalUser(player, settings);
		final boolean personalCast = !personalRecipient && cast && isPersonalCaster(effector, personalQoL, settings) && isLiveRecipient(recipient);
		if (!personalRecipient && !personalCast)
		{
			return stockSeconds;
		}

		return PersonalEffectDurationPolicy.adjust(true, stockSeconds, traitsOf(skill), durationSettings).abnormalTime();
	}

	public CastDuration beginCast(Creature effector, Skill skill)
	{
		final PersonalCharacterQoLService personalQoL = PersonalCharacterQoLService.getInstance();
		final Settings settings = personalQoL.settings();
		final EffectDurationSettings duration = settings.effectDurationSettings();
		if (!duration.valid() || !duration.enabled() || !isPersonalCaster(effector, personalQoL, settings) || (skill.getAbnormalTime() <= 0))
		{
			return null;
		}
		final SkillTraits traits = traitsOf(skill);
		if (traits.passive() || traits.toggle() || traits.triggered() || traits.abnormalInstant() || traits.debuff() || traits.negativeEffect() || !traits.continuous() || (traits.category() == Category.UNKNOWN))
		{
			return null;
		}
		return new CastDuration(effector, skill, traits, duration);
	}

	private static SkillTraits traitsOf(Skill skill)
	{
		final Category category = PersonalEffectMusicClassifier.getInstance().classify(skill);
		return new SkillTraits(skill.getId(), true, skill.isPassive(), skill.isToggle(), skill.isTriggeredSkill(), skill.isAbnormalInstant(), skill.isDebuff(), skill.hasNegativeEffect(), skill.isContinuous() || skill.isSelfContinuous(), skill.isDance(), category);
	}

	private static boolean isPersonalCaster(Creature effector, PersonalCharacterQoLService personalQoL, Settings settings)
	{
		return settings.casterDurationsForRecipients() && (effector instanceof Player caster) && personalQoL.isPersonalUser(caster, settings) && isOrdinaryLivePlayer(caster);
	}

	static boolean isOrdinaryLivePlayer(Player player)
	{
		return (player != null) && player.isSpawned() && player.hasEnteredWorld() && isOrdinaryConnectedPlayer(player);
	}

	static boolean isOrdinaryConnectedPlayer(Player player)
	{
		if (!player.isOnline() || player.hasHeadlessOutboundSession() || player.isInOlympiadMode() || !PersonalCharacterQoLService.isOrdinaryAdventureAccess(player.getAccessLevel()))
		{
			return false;
		}
		final GameClient client = player.getClient();
		if ((client == null) || client.isDetached())
		{
			return false;
		}
		final OwnerKind owner = PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId());
		return (owner != OwnerKind.PHANTOM) && (owner != OwnerKind.BACKGROUND);
	}

	private static boolean isLiveRecipient(Creature recipient)
	{
		if (recipient instanceof Player player)
		{
			return isOrdinaryLivePlayer(player);
		}
		if (recipient instanceof Summon summon)
		{
			final Player owner = summon.getOwner();
			return isOrdinaryLivePlayer(owner) && (owner.getSummon() == summon) && summon.isSpawned();
		}
		return false;
	}

	public static PersonalEffectDurationService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	public static final class CastDuration
	{
		private final Creature _effector;
		private final Skill _skill;
		private final SkillTraits _traits;
		private final EffectDurationSettings _settings;
		private Integer _stockSeconds;

		private CastDuration(Creature effector, Skill skill, SkillTraits traits, EffectDurationSettings settings)
		{
			_effector = effector;
			_skill = skill;
			_traits = traits;
			_settings = settings;
		}

		public int forRecipient(Creature recipient)
		{
			if (!isLiveRecipient(recipient))
			{
				return 0;
			}
			// Positive continuous effects have no target-dependent term in the stock
			// formula. Take its Skill Mastery result once on the first accepted target.
			if (_stockSeconds == null)
			{
				_stockSeconds = Formulas.calcEffectAbnormalTime(_effector, recipient, _skill);
			}
			return PersonalEffectDurationPolicy.adjust(true, _stockSeconds, _traits, _settings).abnormalTime();
		}
	}

	private static class SingletonHolder
	{
		private static final PersonalEffectDurationService INSTANCE = new PersonalEffectDurationService();
	}
}
