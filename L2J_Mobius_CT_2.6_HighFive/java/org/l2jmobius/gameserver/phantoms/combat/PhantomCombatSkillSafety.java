/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Objects;

import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

public final class PhantomCombatSkillSafety
{
	private PhantomCombatSkillSafety()
	{
	}

	public static boolean supports(Skill skill, PhantomCombatMode mode, boolean actorTransformed)
	{
		Objects.requireNonNull(skill, "skill");
		return supports(facts(skill, actorTransformed), mode);
	}

	public static boolean supportsNpcAggro(Skill skill, boolean actorTransformed)
	{
		Objects.requireNonNull(skill, "skill");
		return supportsNpcAggro(facts(skill, actorTransformed), skill.hasEffectType(EffectType.AGGRESSION));
	}

	/** SELF-only stationary native defense; never admits an area recipient or a foreign effect. */
	public static boolean supportsRaidSelfDefense(Skill skill, boolean actorTransformed)
	{
		if (skill == null || actorTransformed || !skill.isActive() || skill.isPassive() || skill.isToggle() || skill.getTargetType() != TargetType.SELF || skill.getAbnormalType() != org.l2jmobius.gameserver.model.skill.AbnormalType.PD_UP_SPECIAL || skill.hasNegativeEffect() || skill.isPvPOnly() || skill.isSuicideAttack() || skill.isHeroSkill() || skill.isGMSkill() || skill.is7Signs() || skill.isTransformation() || skill.isChanneling() || skill.isSimultaneousCast()) { return false; }
		boolean defense = false;
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (effects == null || effects.isEmpty()) { continue; }
			if (scope != EffectScope.GENERAL) { return false; }
			for (var effect : effects)
			{
				if (!"handlers.skill.effects.ImmobileBuff".equals(effect.getClass().getName())) { return false; }
				defense = true;
			}
		}
		return defense;
	}

	public static boolean supportsNpcAreaAggro(Skill skill, boolean actorTransformed)
	{
		Objects.requireNonNull(skill, "skill");
		if (!ordinaryAction(facts(skill, actorTransformed)) || (skill.getTargetType() != TargetType.AURA) || (skill.getAffectRange() <= 0) || (skill.getAffectRange() > 600) || skill.isChanneling() || skill.isSimultaneousCast())
		{
			return false;
		}
		boolean aggression = false;
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if ((effects == null) || effects.isEmpty()) { continue; }
			if (scope != EffectScope.GENERAL) { return false; }
			for (var effect : effects)
			{
				final String handler = effect.getClass().getName();
				if ("handlers.skill.effects.GetAgro".equals(handler)) { aggression = true; }
				else if (!"handlers.skill.effects.TargetMe".equals(handler)) { return false; }
			}
		}
		return aggression;
	}

	public static boolean supportsNpcAggro(Facts facts, boolean aggressionEffect)
	{
		Objects.requireNonNull(facts, "facts");
		return aggressionEffect && ordinaryOneTargetAction(facts);
	}

	private static Facts facts(Skill skill, boolean actorTransformed)
	{
		return new Facts(skill.isActive(), skill.isPassive(), skill.isToggle(), skill.getTargetType() == TargetType.ONE, skill.hasNegativeEffect(), skill.isPhysical(), skill.isMagic(), skill.isPvPOnly(), skill.isSuicideAttack(), skill.isHeroSkill(), skill.isGMSkill(), skill.is7Signs(), skill.isTransformation(), actorTransformed);
	}

	public static boolean supports(Facts facts, PhantomCombatMode mode)
	{
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(mode, "mode");
		if (!ordinaryOneTargetAction(facts))
		{
			return false;
		}
		return mode.magic() ? facts.magic() : facts.physical();
	}

	private static boolean ordinaryOneTargetAction(Facts facts)
	{
		return facts.oneTarget() && ordinaryAction(facts);
	}

	private static boolean ordinaryAction(Facts facts)
	{
		return facts.active() && !facts.passive() && !facts.toggle() && facts.negative() && !facts.pvpOnly() && !facts.suicide() && !facts.hero() && !facts.gameMaster() && !facts.sevenSigns() && !facts.transformationSkill() && !facts.actorTransformed();
	}

	public record Facts(boolean active, boolean passive, boolean toggle, boolean oneTarget, boolean negative, boolean physical, boolean magic, boolean pvpOnly, boolean suicide, boolean hero, boolean gameMaster, boolean sevenSigns, boolean transformationSkill, boolean actorTransformed)
	{
	}
}
