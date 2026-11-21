/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

/** Read-only native abnormal authority used to prevent support recast spam. */
public final class PhantomSupportEffectAuthority
{
	/** Implemented by stock removal handlers so planning can inspect their actual parameters without RNG. */
	public interface DebuffRemoval
	{
		boolean supportsDebuffRemoval();

		List<BuffInfo> possibleRemovals(Player recipient);
	}

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
		if (!ordinarySupport(skill) || nativeScope(skill) == null) { return null; }
		if (supportsCapability(skill, "combat.heal")) { return "combat.heal"; }
		if (supportsCapability(skill, "combat.recharge")) { return "combat.recharge"; }
		if (supportsCapability(skill, "combat.resurrection")) { return "combat.resurrection"; }
		if (supportsCapability(skill, "combat.cleanse")) { return "combat.cleanse"; }
		return skill.isContinuous() && !skill.getAbnormalType().isNone() && supportsCapability(skill, "combat.buff") ? "combat.buff" : null;
	}

	private static boolean ordinarySupport(Skill skill)
	{
		return skill != null && skill.isActive() && !skill.isPassive() && !skill.isToggle() && !skill.hasNegativeEffect() && !skill.isDebuff()
			&& !skill.isPvPOnly() && !skill.isSuicideAttack() && !skill.isHeroSkill() && !skill.isGMSkill() && !skill.is7Signs()
			&& !skill.isTransformation() && !skill.isChanneling() && !skill.isSimultaneousCast();
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
			case "combat.cleanse" -> !cleanseEffects(skill).isEmpty();
			// A native self-effect may disable its caster even when Skill.hasNegativeEffect() is false.
			case "combat.buff", "combat.song", "combat.dance" -> !skill.hasEffectType(EffectType.PARALYZE, EffectType.ROOT, EffectType.SLEEP, EffectType.STUN, EffectType.MUTE, EffectType.FEAR, EffectType.FAKE_DEATH);
			default -> true;
		};
	}

	private static List<AbstractEffect> cleanseEffects(Skill skill)
	{
		if (!ordinarySupport(skill) || skill.getFlyType() != null || skill.getAffectRange() > 0
			|| skill.getTargetType() != TargetType.ONE && skill.getTargetType() != TargetType.SELF)
		{
			return List.of();
		}
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (effects != null && !effects.isEmpty() && (scope != EffectScope.GENERAL
				|| effects.stream().anyMatch(effect -> effect instanceof DebuffRemoval removal
					? !removal.supportsDebuffRemoval() : effect.getEffectType() != EffectType.HEAL)))
			{
				return List.of();
			}
		}
		final var general = skill.getEffects(EffectScope.GENERAL);
		return general == null ? List.of() : general.stream().filter(DebuffRemoval.class::isInstance).toList();
	}

	/** 0 when no harmful effect is eligible, 1 for a debuff, 2 for a disabling effect. */
	public static int cleanseUrgency(Player caster, Player recipient, Skill skill)
	{
		if (caster == null || recipient == null || recipient.isAlikeDead()) { return 0; }
		int urgency = 0;
		for (AbstractEffect effect : cleanseEffects(skill))
		{
			if (!effect.canStart(caster, recipient, skill)) { continue; }
			for (BuffInfo candidate : ((DebuffRemoval) effect).possibleRemovals(recipient))
			{
				if (candidate.isRemoved()) { continue; }
				final Skill active = candidate.getSkill();
				// A slot handler can match a beneficial or protected effect. It cannot be offered as Cleanse in that state.
				if (!active.isDebuff() || !active.canBeDispeled()) { return 0; }
				final boolean disabling = active.hasEffectType(EffectType.STUN, EffectType.SLEEP, EffectType.PARALYZE, EffectType.ROOT, EffectType.MUTE, EffectType.FEAR);
				urgency = Math.max(urgency, disabling ? 2 : 1);
			}
		}
		return urgency;
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
