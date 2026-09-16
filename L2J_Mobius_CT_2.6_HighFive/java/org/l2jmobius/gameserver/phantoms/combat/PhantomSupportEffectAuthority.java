/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Read-only native abnormal authority used to prevent support recast spam. */
public final class PhantomSupportEffectAuthority
{
	public enum Status
	{
		MISSING,
		NEAR_EXPIRY,
		HEALTHY
	}

	private PhantomSupportEffectAuthority()
	{
	}

	public static Status status(Player target, Skill incoming, int rebuffRemainingSeconds)
	{
		if ((target == null) || (incoming == null) || (rebuffRemainingSeconds < 0))
		{
			throw new IllegalArgumentException("Support effect inspection input is invalid.");
		}
		final BuffInfo exact = target.getEffectList().getBuffInfoBySkillId(incoming.getId());
		final BuffInfo current = incoming.getAbnormalType().isNone() ? exact : target.getEffectList().getBuffInfoByAbnormalType(incoming.getAbnormalType());
		if (current == null)
		{
			return Status.MISSING;
		}
		final Skill active = current.getSkill();
		if ((active.getId() == incoming.getId()) && (active.getLevel() < incoming.getLevel()))
		{
			return Status.MISSING;
		}
		if (active.getAbnormalLevel() < incoming.getAbnormalLevel())
		{
			return Status.MISSING;
		}
		final int remaining = current.getTime();
		return (remaining > 0) && (remaining <= rebuffRemainingSeconds) ? Status.NEAR_EXPIRY : Status.HEALTHY;
	}
}
