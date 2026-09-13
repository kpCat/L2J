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

/**
 * Pure per-recipient effect duration policy.
 */
public final class PersonalEffectDurationPolicy
{
	public enum Category
	{
		BUFF,
		DANCE,
		SONG,
		UNKNOWN
	}

	public enum Decision
	{
		ADJUSTED_BUFF,
		ADJUSTED_DANCE,
		ADJUSTED_SONG,
		DISABLED,
		INELIGIBLE_RECIPIENT,
		NON_POSITIVE_STOCK,
		MISSING_SKILL,
		PASSIVE,
		TOGGLE,
		TRIGGERED,
		ABNORMAL_INSTANT,
		DEBUFF,
		NEGATIVE_EFFECT,
		NON_CONTINUOUS,
		UNKNOWN_MUSIC
	}

	public record SkillTraits(int skillId, boolean present, boolean passive, boolean toggle, boolean triggered, boolean abnormalInstant, boolean debuff, boolean negativeEffect, boolean continuous, boolean music, Category category)
	{
	}

	public record Result(int abnormalTime, Decision decision)
	{
	}

	private PersonalEffectDurationPolicy()
	{
	}

	public static Result adjust(boolean recipientEligible, int stockSeconds, SkillTraits skill, EffectDurationSettings settings)
	{
		if ((settings == null) || !settings.valid() || !settings.enabled())
		{
			return unchanged(stockSeconds, Decision.DISABLED);
		}
		if (!recipientEligible)
		{
			return unchanged(stockSeconds, Decision.INELIGIBLE_RECIPIENT);
		}
		if (stockSeconds <= 0)
		{
			return unchanged(stockSeconds, Decision.NON_POSITIVE_STOCK);
		}
		if ((skill == null) || !skill.present())
		{
			return unchanged(stockSeconds, Decision.MISSING_SKILL);
		}
		if (skill.passive())
		{
			return unchanged(stockSeconds, Decision.PASSIVE);
		}
		if (skill.toggle())
		{
			return unchanged(stockSeconds, Decision.TOGGLE);
		}
		if (skill.triggered())
		{
			return unchanged(stockSeconds, Decision.TRIGGERED);
		}
		if (skill.abnormalInstant())
		{
			return unchanged(stockSeconds, Decision.ABNORMAL_INSTANT);
		}
		if (skill.debuff())
		{
			return unchanged(stockSeconds, Decision.DEBUFF);
		}
		if (skill.negativeEffect())
		{
			return unchanged(stockSeconds, Decision.NEGATIVE_EFFECT);
		}
		if (!skill.continuous())
		{
			return unchanged(stockSeconds, Decision.NON_CONTINUOUS);
		}
		if (skill.music() && (skill.category() == Category.UNKNOWN))
		{
			return unchanged(stockSeconds, Decision.UNKNOWN_MUSIC);
		}

		final double categoryMultiplier;
		final Decision decision;
		switch (skill.category())
		{
			case SONG:
			{
				categoryMultiplier = settings.songMultiplier();
				decision = Decision.ADJUSTED_SONG;
				break;
			}
			case DANCE:
			{
				categoryMultiplier = settings.danceMultiplier();
				decision = Decision.ADJUSTED_DANCE;
				break;
			}
			case BUFF:
			{
				categoryMultiplier = settings.buffMultiplier();
				decision = Decision.ADJUSTED_BUFF;
				break;
			}
			default:
			{
				return unchanged(stockSeconds, Decision.UNKNOWN_MUSIC);
			}
		}

		final double multiplier = settings.overrides().getOrDefault(skill.skillId(), categoryMultiplier);
		final double product = stockSeconds * multiplier;
		final int adjusted = product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) Math.round(product));
		return new Result(adjusted, decision);
	}

	private static Result unchanged(int stockSeconds, Decision decision)
	{
		return new Result(stockSeconds, decision);
	}
}
