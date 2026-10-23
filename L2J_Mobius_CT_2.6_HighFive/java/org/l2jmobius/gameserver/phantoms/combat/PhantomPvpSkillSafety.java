/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

public final class PhantomPvpSkillSafety
{
	private PhantomPvpSkillSafety()
	{
	}

	public static boolean supports(Skill skill, PhantomCombatMode mode, boolean actorTransformed)
	{
		Objects.requireNonNull(skill, "skill");
		return !skill.isChanneling() && !skill.isSimultaneousCast() && !skill.hasEffectType(EffectType.TELEPORT, EffectType.TELEPORT_TO_TARGET, EffectType.SUMMON, EffectType.SUMMON_NPC, EffectType.SUMMON_PET)
			&& supports(new Facts(skill.isActive(), skill.isPassive(), skill.isToggle(), skill.getTargetType() == TargetType.ONE, skill.hasNegativeEffect(), skill.isPhysical(), skill.isMagic(), skill.isSuicideAttack(), skill.isHeroSkill(), skill.isGMSkill(), skill.is7Signs(), skill.isTransformation(), actorTransformed), mode);
	}

	/** Native effect metadata distinguishes a positional blow without a class/skill-ID table. */
	public static boolean requiresRear(Skill skill)
	{
		for (EffectScope scope : new EffectScope[] { EffectScope.GENERAL, EffectScope.PVP })
		{
			final var effects = skill.getEffects(scope);
			if (effects != null && effects.stream().anyMatch(effect -> "handlers.skill.effects.Backstab".equals(effect.getClass().getName()))) { return true; }
		}
		return false;
	}

	/** Small stable utility: control, native damage/positional blows, then other debuffs. */
	public static int priority(Skill skill)
	{
		if (skill.hasEffectType(EffectType.STUN, EffectType.PARALYZE, EffectType.ROOT, EffectType.MUTE)) { return 300; }
		if (requiresRear(skill)) { return 250; }
		return skill.isDamage() ? 200 : 100;
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
