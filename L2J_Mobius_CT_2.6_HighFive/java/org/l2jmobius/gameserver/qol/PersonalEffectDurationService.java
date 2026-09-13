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
import org.l2jmobius.gameserver.model.skill.Skill;
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

	public int adjustAbnormalTime(Creature recipient, Skill skill, int stockSeconds)
	{
		final PersonalCharacterQoLService personalQoL = PersonalCharacterQoLService.getInstance();
		final Settings settings = personalQoL.settings();
		final EffectDurationSettings durationSettings = settings.effectDurationSettings();
		if (!durationSettings.valid() || !durationSettings.enabled() || (recipient == null) || !recipient.isPlayer() || !personalQoL.isPersonalUser(recipient.asPlayer(), settings) || (skill == null))
		{
			return stockSeconds;
		}

		final Category category = PersonalEffectMusicClassifier.getInstance().classify(skill);
		final SkillTraits traits = new SkillTraits(skill.getId(), true, skill.isPassive(), skill.isToggle(), skill.isTriggeredSkill(), skill.isAbnormalInstant(), skill.isDebuff(), skill.hasNegativeEffect(), skill.isContinuous() || skill.isSelfContinuous(), skill.isDance(), category);
		return PersonalEffectDurationPolicy.adjust(true, stockSeconds, traits, durationSettings).abnormalTime();
	}

	public static PersonalEffectDurationService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalEffectDurationService INSTANCE = new PersonalEffectDurationService();
	}
}
