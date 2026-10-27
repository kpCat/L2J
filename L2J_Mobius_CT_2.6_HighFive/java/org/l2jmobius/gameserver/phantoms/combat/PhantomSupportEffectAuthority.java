/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
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

	/** Native maintenance uses the same low-MP boundary as party recharge planning. */
	public static boolean maintenanceAffordable(double currentMp, double maximumMp, double cost)
	{
		return Double.isFinite(currentMp) && Double.isFinite(maximumMp) && Double.isFinite(cost) && maximumMp > 0 && cost >= 0 && currentMp - cost >= maximumMp * 0.35;
	}

	public static boolean maintenanceAffordable(Player caster, Skill skill)
	{
		return caster != null && skill != null && maintenanceAffordable(caster.getCurrentMp(), caster.getMaxMp(), Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), caster.getStat().getMpInitialConsume(skill) + caster.getStat().getMpConsume(skill)));
	}

	/** Learned native support outside the sparse authored progression catalog. No skill is granted. */
	public static String nativeCapability(Skill skill)
	{
		if (skill == null || !skill.isActive() || skill.isPassive() || skill.isToggle() || skill.hasNegativeEffect() || skill.isDebuff() || skill.isPvPOnly() || skill.isSuicideAttack() || skill.isHeroSkill() || skill.isGMSkill() || skill.is7Signs() || skill.isTransformation() || skill.isChanneling() || skill.isSimultaneousCast() || nativeScope(skill) == null) { return null; }
		if (supportsCapability(skill, "combat.heal")) { return "combat.heal"; }
		if (supportsCapability(skill, "combat.recharge")) { return "combat.recharge"; }
		if (supportsCapability(skill, "combat.resurrection")) { return "combat.resurrection"; }
		return skill.isContinuous() && !skill.getAbnormalType().isNone() ? "combat.buff" : null;
	}

	public static String nativeScope(Skill skill)
	{
		return switch (skill.getTargetType())
		{
			case SELF -> "SELF";
			case ONE, PC_BODY -> "SINGLE_TARGET";
			case PARTY, PARTY_MEMBER, PARTY_NOTME, PARTY_OTHER, TARGET_PARTY -> "PARTY";
			default -> null;
		};
	}

	public static String nativeVariant(Skill skill)
	{
		return "native.skill." + skill.getId();
	}

	/** Descriptive class evidence never substitutes for the learned native action's actual effect. */
	public static boolean supportsCapability(Skill skill, String capabilityKey)
	{
		if (skill == null || capabilityKey == null) { return false; }
		return switch (capabilityKey)
		{
			case "combat.heal" -> skill.hasEffectType(EffectType.HEAL) || skill.hasEffectType(EffectType.REBALANCE_HP);
			case "combat.recharge" -> skill.hasEffectType(EffectType.MANAHEAL_BY_LEVEL) || skill.hasEffectType(EffectType.MANAHEAL_PERCENT);
			case "combat.resurrection" -> skill.hasEffectType(EffectType.RESURRECTION) || skill.hasEffectType(EffectType.RESURRECTION_SPECIAL);
			default -> true;
		};
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
