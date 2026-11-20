/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.Collection;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.holders.creature.EffectList;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.stats.Stat;

/** Transfers a committed owner's active effect to the current summon. */
public final class PersonalSummonerBuffService
{
	// Admit audited handlers. EffectType.BUFF also includes
	// player-only handlers and callbacks that can grant unrelated capabilities.
	private static final Set<String> MIRRORABLE_HANDLERS = Set.of(
		"handlers.skill.effects.AttackTrait",
		"handlers.skill.effects.BlockAbnormalSlot",
		"handlers.skill.effects.Buff",
		"handlers.skill.effects.DefenceTrait",
		"handlers.skill.effects.Grow",
		"handlers.skill.effects.Hide",
		"handlers.skill.effects.ImmobileBuff",
		"handlers.skill.effects.Invincible",
		"handlers.skill.effects.MaxHp",
		"handlers.skill.effects.NoblesseBless",
		"handlers.skill.effects.PolearmSingleTarget",
		"handlers.skill.effects.ResistSkill",
		"handlers.skill.effects.ResurrectionSpecial",
		"handlers.skill.effects.SpiritSharing",
		"handlers.skill.effects.TransferDamage",
		"handlers.skill.effects.TriggerSkillByDamageDealt",
		"handlers.skill.effects.TriggerSkillByDamageReceived");
	private static final Set<String> SOURCE_TICKED_HANDLERS = Set.of(
		"handlers.skill.effects.HealOverTime",
		"handlers.skill.effects.ManaHealOverTime");
	private static final Set<String> TOGGLE_STAT_HANDLERS = Set.of(
		"handlers.skill.effects.DamOverTime",
		"handlers.skill.effects.ManaDamOverTime");
	private static final Set<Integer> NATIVE_ONLY_SKILLS = Set.of(
		1557, // Servitor Share already transfers the owner's stats.
		2232, // Valakas amulet consumes a scroll and supplies a signed raid trait.
		5098); // Capture Penalty lowers bow defence despite its positive effect point.

	private PersonalSummonerBuffService()
	{
	}

	public static void ownerBuffAdded(BuffInfo source, Collection<WorldObject> nativeTargets)
	{
		if ((source.getEffected() instanceof Player owner) && isEligibleOwner(owner))
		{
			final Summon summon = owner.getSummon();
			if ((summon != null) && ((nativeTargets == null) || !nativeTargets.contains(summon)))
			{
				mirrorActiveBuff(source, summon);
			}
		}
	}

	public static void synchronizeOnSpawn(Summon summon)
	{
		final Player owner = summon.getOwner();
		if (isEligibleOwner(owner) && (owner.getSummon() == summon))
		{
			for (BuffInfo source : owner.getEffectList().getEffects())
			{
				mirrorActiveBuff(source, summon);
			}
		}
	}

	public static void ownerBuffRemoved(BuffInfo source)
	{
		if (source.hadSummonMirror() && (source.getEffected() instanceof Player owner))
		{
			final Summon summon = owner.getSummon();
			if ((summon == null) || (summon.getOwner() != owner))
			{
				return;
			}
			final EffectList effects = summon.getEffectList();
			for (BuffInfo mirror : effects.getEffects())
			{
				if (mirror.getMirrorSource() == source)
				{
					effects.remove(SkillFinishType.REMOVED, mirror);
					return;
				}
			}
		}
	}

	/** Recheck after publishing the copy, so a concurrent source removal cannot miss it. */
	public static boolean sourceIsCurrent(BuffInfo source, Summon summon)
	{
		return ownerSourceIsCurrent(source, summon) && !source.isSummonMirrorConsumed() && summon.isSpawned() && !summon.isDead() && (source.getEffected().getInstanceId() == summon.getInstanceId());
	}

	public static boolean isCurrentMirror(BuffInfo mirror)
	{
		return (mirror.getMirrorSource() != null) && (mirror.getEffected() instanceof Summon summon) && mirror.isInUse()
			&& summon.getEffectList().contains(mirror) && sourceIsCurrent(mirror.getMirrorSource(), summon);
	}

	public static boolean ownerSourceIsCurrent(BuffInfo source, Summon summon)
	{
		final Player owner = summon.getOwner();
		return (source.getEffected() == owner) && isEligibleOwner(owner) && (owner.getSummon() == summon) && !owner.isDead()
			&& source.isInUse() && (source.getSkill().isToggle() || (source.getTime() > 0)) && owner.getEffectList().contains(source);
	}

	public static boolean consumeMirror(Summon summon, BuffInfo mirror)
	{
		final BuffInfo source = mirror.getMirrorSource();
		synchronized (summon)
		{
			if ((source == null) || (mirror.getEffected() != summon) || !mirror.isInUse() || !summon.getEffectList().contains(mirror)
				|| !sourceIsCurrent(source, summon) || !source.consumeSummonMirror())
			{
				return false;
			}
			summon.getEffectList().remove(SkillFinishType.REMOVED, mirror);
			return true;
		}
	}

	public static void ownerBuffTick(BuffInfo source, AbstractEffect effect)
	{
		if (!SOURCE_TICKED_HANDLERS.contains(effect.getClass().getName()) || !(source.getEffected() instanceof Player owner))
		{
			return;
		}
		final Summon summon = owner.getSummon();
		if (summon == null)
		{
			return;
		}
		synchronized (summon)
		{
			if (!sourceIsCurrent(source, summon))
			{
				return;
			}
			final BuffInfo mirror = summon.getEffectList().getBuffInfoBySkillId(source.getSkill().getId());
			if ((mirror != null) && mirror.isInUse() && (mirror.getMirrorSource() == source) && mirror.getEffects().contains(effect))
			{
				effect.onActionTime(mirror.getEffector(), summon, mirror.getSkill());
			}
		}
	}

	private static boolean isEligibleOwner(Player owner)
	{
		return hasMirroringPermission(owner) && PersonalEffectDurationService.isOrdinaryLivePlayer(owner);
	}

	private static boolean hasMirroringPermission(Player owner)
	{
		final PersonalCharacterQoLService qol = PersonalCharacterQoLService.getInstance();
		final Settings settings = qol.settings();
		return settings.summonerBuffMirroring() && qol.isPersonalUser(owner, settings);
	}

	public static void mirrorActiveBuff(BuffInfo source, Summon summon)
	{
		final Skill skill = source.getSkill();
		if (!isMirrorCandidate(source) || source.isSummonMirrorConsumed() || !ownerSourceIsCurrent(source, summon))
		{
			return;
		}
		final BuffInfo copy = BuffInfo.forSummonMirror(source, summon);
		for (AbstractEffect effect : source.getEffects())
		{
			if (isMirrorableHandler(effect, skill) && effect.canStart(copy.getEffector(), summon, skill))
			{
				copy.addEffect(effect);
			}
		}
		if (!copy.getEffects().isEmpty())
		{
			summon.getEffectList().add(copy);
		}
	}

	public static boolean isPersonalSummonerSource(BuffInfo source)
	{
		// EnterWorld restores effects before the connected character enters the world.
		// Track their expiry identity immediately while keeping live admission for copying.
		return (source.getEffected() instanceof Player owner) && hasMirroringPermission(owner)
			&& PersonalEffectDurationService.isOrdinaryConnectedPlayer(owner) && isMirrorCandidate(source)
			&& source.getEffects().stream().anyMatch(effect -> isMirrorableHandler(effect, source.getSkill()));
	}

	private static boolean isMirrorCandidate(BuffInfo source)
	{
		final Skill skill = source.getSkill();
		return (source.getMirrorSource() == null) && !NATIVE_ONLY_SKILLS.contains(skill.getId())
			&& !skill.isPassive() && !skill.isDebuff()
			&& !skill.hasNegativeEffect() && !skill.hasEffects(EffectScope.END) && (skill.isContinuous() || skill.isSelfContinuous() || skill.isToggle()) && !source.getEffects().isEmpty();
	}

	private static boolean isMirrorableHandler(AbstractEffect effect, Skill skill)
	{
		if (effect.isInstant())
		{
			return false;
		}
		final String handler = effect.getClass().getName();
		if (SOURCE_TICKED_HANDLERS.contains(handler) || handler.equals("handlers.skill.effects.SilentMove"))
		{
			return true;
		}
		final var statFunctions = effect.getFuncTemplates();
		if (skill.isToggle())
		{
			if (handler.equals("handlers.skill.effects.Relax") || handler.equals("handlers.skill.effects.ChameleonRest"))
			{
				return true;
			}
			if (TOGGLE_STAT_HANDLERS.contains(handler))
			{
				return (statFunctions != null) && !statFunctions.isEmpty();
			}
		}
		if ((effect.getTicks() != 0) || !MIRRORABLE_HANDLERS.contains(handler))
		{
			return false;
		}
		// Only PlayerStat consumes vitality. A pure vitality change has no summon effect.
		if (handler.equals("handlers.skill.effects.Grow"))
		{
			return (statFunctions != null) && !statFunctions.isEmpty();
		}
		return !handler.equals("handlers.skill.effects.Buff") || ((statFunctions != null)
			&& statFunctions.stream().anyMatch(function -> function.getStat() != Stat.VITALITY_CONSUME_RATE));
	}
}
