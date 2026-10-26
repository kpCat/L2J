/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.l2jmobius.gameserver.config.custom.ClassBalanceConfig;
import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.Stat;

/** Immutable ordinary-PvE action facts. No execution, resource grant or catalog initialization. */
public final class PhantomNativeCombatCapability
{
	public static final int MAXIMUM_SKILLS = 4096;
	public enum DamageKind { PHYSICAL, MAGIC }
	public record Mechanics(DamageKind kind, double power, int castMillis, int reuseMillis, boolean staticCast, double drainRatio, int magicLevel, byte element, int elementPower, double damageMultiplier)
	{
		public Mechanics(DamageKind kind, double power, int castMillis, int reuseMillis, boolean staticCast, double drainRatio)
		{ this(kind, power, castMillis, reuseMillis, staticCast, drainRatio, 0, (byte) -1, 0, 1); }
		public Mechanics
		{
			if (kind == null || !Double.isFinite(power) || power <= 0 || castMillis < 0 || reuseMillis < 0 || !Double.isFinite(drainRatio) || drainRatio < 0 || drainRatio > 1 || kind != DamageKind.MAGIC && drainRatio != 0 || magicLevel < 0 || element < -1 || element > 5 || elementPower < 0 || !Double.isFinite(damageMultiplier) || damageMultiplier <= 0 || damageMultiplier > 1)
			{ throw new IllegalArgumentException("Unsupported ordinary skill mechanics."); }
		}
		public Mechanics scaled(double scale)
		{ return new Mechanics(kind, power, castMillis, reuseMillis, staticCast, drainRatio, magicLevel, element, elementPower, damageMultiplier * scale); }
	}
	public record Readiness(boolean supported, boolean disabled, boolean conditions, double currentMp, double mpCost, int hpCost, int itemCost, int chargeCost, int soulCost)
	{
		public boolean ready()
		{
			// The persisted loadout can attest MP; these other repeatable costs have no ordinary cast ledger.
			return supported && !disabled && conditions && Double.isFinite(currentMp) && Double.isFinite(mpCost) && mpCost >= 0 && currentMp >= mpCost && hpCost == 0 && itemCost == 0 && chargeCost == 0 && soulCost == 0;
		}
	}
	public record Candidate(int skillId, int skillLevel, Mechanics mechanics, Readiness readiness)
	{
		public Candidate
		{
			if (skillId < 1 || skillLevel < 1 || readiness == null) { throw new IllegalArgumentException("Invalid learned action."); }
		}
	}

	/** Authored IDs are preferences over learned ranks, never grants. */
	public static Optional<Candidate> select(List<Candidate> learned, List<Integer> preferred)
	{ return select(learned, preferred, true); }

	private static Optional<Candidate> select(List<Candidate> learned, List<Integer> preferred, boolean readyNow)
	{
		if (learned.size() > MAXIMUM_SKILLS || preferred.size() > MAXIMUM_SKILLS) { return Optional.empty(); }
		if (!readyNow)
		{
			final var ready = select(learned, preferred, true);
			if (ready.isPresent()) { return ready; }
		}
		return Stream.concat(preferred.stream().flatMap(id -> learned.stream().filter(candidate -> candidate.skillId() == id)),
			learned.stream().sorted(Comparator.comparingInt((Candidate value) -> value.mechanics() == null ? -1 : value.mechanics().magicLevel()).reversed()
				.thenComparing(Comparator.comparingDouble((Candidate value) -> value.mechanics() == null ? 0 : value.mechanics().power()).reversed()).thenComparingInt(Candidate::skillId)))
			.filter(candidate -> candidate.mechanics() != null && (readyNow ? candidate.readiness().ready() : preparable(candidate.readiness()))).findFirst();
	}

	private static boolean preparable(Readiness value)
	{ return value.supported() && value.conditions() && value.hpCost() == 0 && value.itemCost() == 0 && value.chargeCost() == 0 && value.soulCost() == 0; }

	/** A single shared native choice for Phantom AutoUse preparation, admission and capture. */
	public static Skill offensiveSkill(Player player, Creature target)
	{ return resolve(player, target, true); }

	/** Configuration intent only; native AutoUse and resource recovery still enforce MP/reuse. */
	public static Skill preparationSkill(Player player, Creature target)
	{ return resolve(player, target, false); }

	private static Skill resolve(Player player, Creature target, boolean readyNow)
	{
		if (!readyNow)
		{
			final Skill ready = resolve(player, target, true);
			if (ready != null) { return ready; }
		}
		if (!AutoPlayConfig.ENABLE_AUTO_SKILL || player.isAlikeDead() || player.isMounted() || player.isTransformed() || readyNow && player.isAllSkillsDisabled()) { return null; }
		final var learned = player.getAllSkills();
		if (learned.size() > MAXIMUM_SKILLS) { return null; }
		final var configured = List.copyOf(player.getAutoUseSettings().getAutoSkills());
		final var facts = learned.stream().map(skill -> candidate(player, target, skill, readyNow)).toList();
		final var selected = select(facts, configured, readyNow);
		return selected.map(value -> player.getKnownSkill(value.skillId())).orElse(null);
	}

	public static boolean magicShots(Player player)
	{
		final var configured = player.getAutoUseSettings().getAutoSkills();
		final Skill primary = configured.stream().map(player::getKnownSkill).filter(skill -> mechanics(skill) != null).findFirst().orElseGet(() -> offensiveSkill(player, null));
		return primary != null && primary.isMagic() && mechanics(primary) != null;
	}

	/** One ephemeral sample under the visible session's admitted native writer, never a durable combat bonus. */
	public static PhantomCombatPolicy.PvePreparation pvePreparation(Player player)
	{
		// alignOffensiveSkill already selected the actual learned primary under this writer.
		final Skill primary = player.getAutoUseSettings().getAutoSkills().stream().map(player::getKnownSkill).filter(skill -> mechanics(skill) != null).findFirst().orElse(null);
		final boolean magic = primary != null && primary.isMagic();
		final var weapon = player.getActiveWeaponItem();
		final boolean gear = weapon != null && weapon != player.getFistsWeaponItem() && player.getActiveWeaponInstance() != null
			&& weapon.getCrystalType().getLevel() <= player.getExpertiseLevel();
		final var action = magic ? org.l2jmobius.gameserver.model.item.type.ActionType.SPIRITSHOT : org.l2jmobius.gameserver.model.item.type.ActionType.SOULSHOT;
		final int perHit = weapon == null ? 0 : magic ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		long shots = 0;
		for (var item : player.getInventory().getItems().stream().limit(256).toList())
		{
			if (gear && item.getOwnerId() == player.getObjectId() && item.getCount() > 0 && item.getEtcItem() != null
				&& item.getTemplate().getDefaultAction() == action && item.getTemplate().getCrystalType() == weapon.getCrystalTypePlus()
				&& org.l2jmobius.gameserver.handler.ItemHandler.getInstance().getHandler(item.getEtcItem()) != null)
			{ shots += Math.min(100_000, item.getCount()); }
		}
		final boolean buff = player.getEffectList().getBuffs().stream().anyMatch(info -> !info.getSkill().hasNegativeEffect()
			&& info.getSkill().isContinuous() && !info.getSkill().isHealingPotionSkill() && (info.getTime() > 10 || info.getTime() < 0));
		final var summon = player.getSummon();
		final boolean support = summon instanceof org.l2jmobius.gameserver.model.actor.instance.BabyPet pet && pet.getOwner() == player
			&& !pet.isAlikeDead() && pet.getCurrentFed() > 0 && pet.getCurrentMp() > 0;
		final double mpCost = primary == null ? 0 : Math.max(primary.getMpInitialConsume() + primary.getMpConsume(), player.getStat().getMpInitialConsume(primary) + player.getStat().getMpConsume(primary));
		return new PhantomCombatPolicy.PvePreparation(player.getLevel(), player.getCurrentHpPercent(), player.getMaxMp() > 0 ? player.getCurrentMp() * 100 / player.getMaxMp() : 0,
			gear, magic, !magic || player.getCurrentMp() >= mpCost, perHit > 0 ? shots / perHit : 0,
			L2jCombatBackend.ownedHealingStock(player), buff, support, player.isInParty(), player.getAdena() > 0);
	}

	private static Candidate candidate(Player player, Creature target, Skill skill, boolean readyNow)
	{
		final Mechanics mechanics = mechanics(skill);
		final double mp = Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill));
		final double reuseRate = skill.isStaticReuse() || skill.isStatic() ? 1 : player.calcStat(skill.isMagic() ? Stat.MAGIC_REUSE_RATE : Stat.P_REUSE, 1, null, null);
		final double reuse = skill.getReuseDelay() * reuseRate * ClassBalanceConfig.SKILL_REUSE_MULTIPLIERS[player.getPlayerClass().getId()];
		final var weapon = player.getActiveWeaponItem();
		final boolean weaponSkill = weapon == null || !weapon.useWeaponSkillsOnly() || !weapon.hasSkills() || java.util.Arrays.stream(weapon.getSkills()).anyMatch(holder -> holder.getSkillId() == skill.getId());
		final boolean bow = weapon != null && (weapon.getItemType() == org.l2jmobius.gameserver.model.item.type.WeaponType.BOW || weapon.getItemType() == org.l2jmobius.gameserver.model.item.type.WeaponType.CROSSBOW);
		final boolean scalarEnvelope = player.getAttackElement() < 0 && player.calcStat(skill.isMagic() ? Stat.PVE_MAGICAL_DMG : bow ? Stat.PVE_BOW_SKILL_DMG : Stat.PVE_PHYSICAL_DMG, 1, null, null) >= 1
			&& (skill.isMagic() || player.calcStat(Stat.PHYSICAL_SKILL_POWER, 1, null, null) == 1 && player.calcStat(Stat.PHYSICAL_SKILL_POWER, 1000, null, null) == 1000);
		final boolean supported = mechanics != null && magicLevelReady(mechanics, player.getLevel(), target == null ? 0 : target.getLevel()) && !AutoPlayConfig.DISABLED_AUTO_SKILLS.contains(skill.getId()) && reuse <= skill.getReuseDelay() && scalarEnvelope && classBound(mechanics, player.getPlayerClass().getId()) != null;
		final boolean conditions = supported && castingAllowed(skill.isStatic(), skill.isMagic(), player.isMuted(), player.isPhysicalMuted(), weaponSkill) && skill.checkCondition(player, target, false) && skill.checkCondition(player, target, true)
			&& (!readyNow || player.checkDoCastConditions(skill)); // Read-only canonical checks; native cast remains the sole resource consumer.
		return new Candidate(skill.getId(), skill.getLevel(), mechanics, new Readiness(supported, player.isSkillDisabled(skill), conditions, player.getCurrentMp(), mp, skill.getHpConsume(), skill.getItemConsumeCount(), skill.getChargeConsumeCount(), skill.getMaxSoulConsumeCount()));
	}

	public static boolean castingAllowed(boolean staticSkill, boolean magic, boolean muted, boolean physicalMuted, boolean weaponSkill)
	{ return weaponSkill && (staticSkill || !(magic ? muted : physicalMuted)); }

	public static boolean magicLevelReady(Mechanics mechanics, int actorLevel, int targetLevel)
	{
		if (mechanics.kind() != DamageKind.MAGIC) { return true; }
		if (mechanics.magicLevel() <= 0) { return false; }
		return targetLevel > 0 ? targetLevel <= mechanics.magicLevel() : 100 - Math.round(Math.pow(1.3, actorLevel - mechanics.magicLevel())) > 0;
	}

	public static void alignAutoSkills(org.l2jmobius.gameserver.model.actor.holders.player.AutoUseSettingsHolder settings, List<Integer> utilities, int primary)
	{
		if (primary < 0 || utilities.size() > MAXIMUM_SKILLS) { throw new IllegalArgumentException("Invalid Phantom AutoUse intent."); }
		final var desired = new java.util.ArrayList<>(utilities.stream().filter(id -> id != primary).distinct().sorted().toList());
		if (primary > 0) { desired.add(primary); }
		if (!settings.getAutoSkills().equals(desired))
		{ settings.getAutoSkills().clear(); settings.getAutoSkills().addAll(desired); settings.resetSkillOrder(); }
	}

	public static void alignAutoSkills(org.l2jmobius.gameserver.model.actor.holders.player.AutoUseSettingsHolder settings, List<Integer> utilities, int primary, boolean magicPrimary)
	{
		alignAutoSkills(settings, utilities, primary);
		// Stock AutoPlay uses action2 to distinguish melee roots from AutoUse casting.
		if (magicPrimary) { settings.getAutoActions().remove(2); }
		else { settings.getAutoActions().add(2); }
	}

	/** Only a plain instant single-target native hit (optionally its native HP drain). */
	public static Mechanics mechanics(Skill skill)
	{
		if (skill == null || !skill.isDamage() || skill.isContinuous() || skill.isChanneling() || skill.isSimultaneousCast() || skill.getFlyType() != null || skill.getFlyRadius() != 0
			|| !PhantomCombatSkillSafety.supports(skill, skill.isMagic() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL, false)) { return null; }
		int count = 0;
		double drain = 0;
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (effects == null) { continue; }
			for (var effect : effects)
			{
				if (scope != EffectScope.GENERAL || ++count > 1 || !effect.isInstant() || effect.hasAttachCondition() || effect.getTicks() != 0 || effect.getFuncTemplates() != null && !effect.getFuncTemplates().isEmpty()) { return null; }
				final String handler = effect.getClass().getName();
				if (skill.isMagic() && "handlers.skill.effects.HpDrain".equals(handler)) { drain = effect.getHpDrainRatio(); }
				else if (!(skill.isMagic() && "handlers.skill.effects.MagicalDamage".equals(handler)) && !(skill.isPhysical() && "handlers.skill.effects.PhysicalDamage".equals(handler))) { return null; }
			}
		}
		final double power = skill.getPower(false, true);
		if (count != 1 || !Double.isFinite(power) || power <= 0 || !Double.isFinite(drain) || drain < 0 || drain > 1) { return null; }
		return nativeMechanics(skill.isMagic(), power, Math.addExact(skill.getHitTime(), skill.getCoolTime()), skill.getReuseDelay(), skill.isStatic(), drain, skill.getMagicLevel(), skill.getElement(), skill.getElementPower());
	}

	/** Scalar boundary after the native effect envelope; source sentinels are not simulation facts. */
	private static Mechanics nativeMechanics(boolean magic, double power, int castMillis, int reuseMillis, boolean staticCast, double drain, int magicLevel, byte element, int elementPower)
	{
		if (magicLevel < 0 || magic && magicLevel == 0) { return null; }
		return new Mechanics(magic ? DamageKind.MAGIC : DamageKind.PHYSICAL, power, castMillis, reuseMillis, staticCast, drain, magicLevel, element, elementPower, 1);
	}

	public static Mechanics classBound(Mechanics mechanics, int classId)
	{
		if (mechanics == null) { return null; }
		if (classId < 0) { return mechanics; } // Pure scalar fixtures have no native class configuration.
		final float[] values = mechanics.kind() == DamageKind.MAGIC ? ClassBalanceConfig.PVE_MAGICAL_SKILL_DAMAGE_MULTIPLIERS : ClassBalanceConfig.PVE_PHYSICAL_SKILL_DAMAGE_MULTIPLIERS;
		if (classId >= values.length || !Float.isFinite(values[classId]) || values[classId] <= 0) { return null; }
		return mechanics.scaled(Math.min(1, values[classId]));
	}

	public static Mechanics targetBound(Mechanics mechanics, int actorLevel, int targetLevel)
	{
		if (mechanics == null || actorLevel <= 0 || targetLevel < NpcConfig.MIN_NPC_LEVEL_DMG_PENALTY || targetLevel - actorLevel < 2) { return mechanics; }
		final float[] penalties = NpcConfig.NPC_SKILL_DMG_PENALTY;
		if (penalties == null || penalties.length == 0) { return null; }
		final double scale = penalties[Math.min(targetLevel - actorLevel - 1, penalties.length - 1)];
		return Double.isFinite(scale) && scale > 0 ? mechanics.scaled(Math.min(1, scale)) : null;
	}

	/** Preserve the existing profession executor without admitting arbitrary unmodeled debuffs. */
	public static boolean ordinaryUtility(Skill skill)
	{
		if (skill == null || !PhantomCombatSkillSafety.supports(skill, skill.isMagic() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL, false) || skill.isChanneling() || skill.isSimultaneousCast()) { return false; }
		int count = 0;
		for (EffectScope scope : EffectScope.values())
		{
			final var effects = skill.getEffects(scope);
			if (effects == null) { continue; }
			for (var effect : effects)
			{ if (scope != EffectScope.GENERAL || ++count > 1 || !"handlers.skill.effects.Spoil".equals(effect.getClass().getName())) { return false; } }
		}
		return count == 1;
	}

	private PhantomNativeCombatCapability() { }
}
