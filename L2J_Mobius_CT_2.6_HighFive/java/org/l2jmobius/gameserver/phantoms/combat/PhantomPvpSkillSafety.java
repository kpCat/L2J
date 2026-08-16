/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

public final class PhantomPvpSkillSafety
{
	private PhantomPvpSkillSafety()
	{
	}

	public static boolean supports(Skill skill, PhantomCombatMode mode, boolean actorTransformed)
	{
		Objects.requireNonNull(skill, "skill");
		return supports(new Facts(skill.isActive(), skill.isPassive(), skill.isToggle(), skill.getTargetType() == TargetType.ONE, skill.hasNegativeEffect(), skill.isPhysical(), skill.isMagic(), skill.isSuicideAttack(), skill.isHeroSkill(), skill.isGMSkill(), skill.is7Signs(), skill.isTransformation(), actorTransformed), mode);
	}

	public static boolean supports(Facts facts, PhantomCombatMode mode)
	{
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(mode, "mode");
		if (!facts.active() || facts.passive() || facts.toggle() || !facts.oneTarget() || !facts.negative() || facts.suicide() || facts.hero() || facts.gameMaster() || facts.sevenSigns() || facts.transformationSkill() || facts.actorTransformed())
		{
			return false;
		}
		return mode.magic() ? facts.magic() : facts.physical();
	}

	public record Facts(boolean active, boolean passive, boolean toggle, boolean oneTarget, boolean negative, boolean physical, boolean magic, boolean suicide, boolean hero, boolean gameMaster, boolean sevenSigns, boolean transformationSkill, boolean actorTransformed)
	{
	}
}
