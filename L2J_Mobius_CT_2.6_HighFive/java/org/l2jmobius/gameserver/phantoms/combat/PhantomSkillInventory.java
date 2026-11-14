/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import org.l2jmobius.gameserver.data.xml.EnchantSkillGroupsData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.EnchantSkillLearn;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/** Full acquired facts, never a cast list, grant, second skill loader or cached readiness. */
public final class PhantomSkillInventory
{
	public static final String VERSION = "ACQUIRED_SKILL_INVENTORY_V1";
	public enum Category { SINGLE_TARGET_DAMAGE, HEAL, SELF_BUFF, PARTY_BUFF, DEBUFF, CONTROL, AOE_DAMAGE, SUMMON, RESURRECTION, MOBILITY_ESCAPE, OTHER_ACTIVE, PASSIVE, UNKNOWN }
	public enum Support { DISCOVERED, SUPPORTED_FOR_SELECTION, SUPPORTED_FOR_TRAIN, SUPPORTED_FOR_NATIVE_EXECUTION, NOT_SUPPORTED, TEMPORARILY_UNAVAILABLE }
	public enum Reason { OWNER, DEAD, NON_ACTIVE, LEARNED_VERSION, DISABLED, COOLDOWN, MP, HP, ITEM, CHARGE, SOUL, WRONG_TARGET, RANGE, LOS, GEO, WEAPON, NATIVE_CONDITION, UNASSESSED }
	public record Fact<T>(T value, boolean known)
	{
		public Fact { if (known == (value == null)) { throw new IllegalArgumentException("Неизвестный факт должен иметь пустое значение."); } }
		public static <T> Fact<T> known(T value) { return new Fact<>(Objects.requireNonNull(value), true); }
		public static <T> Fact<T> unknown() { return new Fact<>(null, false); }
	}
	public record Ability(SkillRef skill, Fact<Integer> enchantRoute, Fact<Integer> enchantLevel, Category category,
		List<Support> support, String targetType, Fact<Integer> castRange, Fact<Integer> effectRange, int areaRadius,
		Fact<Integer> maximumTargets, double mpCost, int hpCost, int itemId, int itemCount, int charges, int souls,
		int castMillis, int reuseMillis, boolean magic, boolean physical, byte element, int elementPower, double power,
		int abnormalSeconds, String abnormalType, int abnormalLevel, boolean conditionsPresent, List<String> effects,
		Fact<List<String>> definitionSources)
	{
		public Ability
		{
			Objects.requireNonNull(skill); Objects.requireNonNull(category);
			support = List.copyOf(support); effects = List.copyOf(effects);
		}
	}
	public record Snapshot(int classId, int classIndex, String catalogHash, List<Ability> abilities, String fingerprint)
	{
		public Snapshot { abilities = List.copyOf(abilities); }
	}
	public record TheoreticalSkill(int classId, SkillRef skill, int minimumCharacterLevel, boolean autoGet, boolean learnedByNpc) { }
	/** Values are current native observations, not a resource simulator or permission to spend them. */
	public record Resources(double mp, double mpCost, double hp, int hpCost, long items, int itemCost, int charges, int chargeCost, int souls, int soulCost)
	{
		public Resources
		{
			if (!Double.isFinite(mp) || !Double.isFinite(mpCost) || !Double.isFinite(hp) || mp < 0 || mpCost < 0 || hp < 0 || hpCost < 0
				|| items < 0 || itemCost < 0 || charges < 0 || chargeCost < 0 || souls < 0 || soulCost < 0)
			{ throw new IllegalArgumentException("Текущие ресурсы навыка должны быть известными конечными значениями."); }
		}
	}
	public record NativeFlags(boolean owner, boolean alive, boolean active, boolean exactLearned, boolean allDisabled,
		boolean skillDisabled, boolean cooldown, boolean target, boolean inRange, boolean lineOfSight, boolean weapon, boolean conditions) { }
	public record Availability(List<Reason> reasons, boolean known)
	{
		public Availability { reasons = List.copyOf(reasons); }
		public boolean ready() { return known && reasons.isEmpty(); }
	}

	public static Availability availability(Resources resources, NativeFlags flags)
	{
		Objects.requireNonNull(resources); Objects.requireNonNull(flags);
		final var reasons = new ArrayList<Reason>();
		if (!flags.owner()) { reasons.add(Reason.OWNER); }
		if (!flags.alive()) { reasons.add(Reason.DEAD); }
		if (!flags.active()) { reasons.add(Reason.NON_ACTIVE); }
		if (!flags.exactLearned()) { reasons.add(Reason.LEARNED_VERSION); }
		if (flags.allDisabled() || flags.skillDisabled()) { reasons.add(Reason.DISABLED); }
		if (flags.cooldown()) { reasons.add(Reason.COOLDOWN); }
		if (resources.mp() < resources.mpCost()) { reasons.add(Reason.MP); }
		if (resources.hp() <= resources.hpCost()) { reasons.add(Reason.HP); }
		if (resources.items() < resources.itemCost()) { reasons.add(Reason.ITEM); }
		if (resources.charges() < resources.chargeCost()) { reasons.add(Reason.CHARGE); }
		if (resources.souls() < resources.soulCost()) { reasons.add(Reason.SOUL); }
		if (!flags.target()) { reasons.add(Reason.WRONG_TARGET); }
		if (!flags.inRange()) { reasons.add(Reason.RANGE); }
		if (!flags.lineOfSight()) { reasons.add(Reason.LOS); }
		if (!flags.weapon()) { reasons.add(Reason.WEAPON); }
		if (!flags.conditions()) { reasons.add(Reason.NATIVE_CONDITION); }
		return new Availability(reasons, true);
	}

	/** Safe action adapters attest target semantics; unsupported mechanics remain explicitly unassessed. */
	public static Availability eligibility(Player player, Creature target, Ability ability, BooleanSupplier ownerCurrent, boolean targetVerified)
	{
		Objects.requireNonNull(player); Objects.requireNonNull(ability); Objects.requireNonNull(ownerCurrent);
		if (!ownerCurrent.getAsBoolean()) { return new Availability(List.of(Reason.OWNER), false); }
		final Skill skill = player.getKnownSkill(ability.skill().skillId());
		if (skill == null || skill.getLevel() != ability.skill().skillLevel()) { return new Availability(List.of(Reason.LEARNED_VERSION), true); }
		if (skill.isPassive()) { return new Availability(List.of(Reason.NON_ACTIVE), true); }
		if (!ability.support().contains(Support.SUPPORTED_FOR_NATIVE_EXECUTION) || SkillData.getInstance().catalogReloaded())
		{ return new Availability(List.of(Reason.UNASSESSED), false); }
		if (!targetVerified || target == null || player.getInstanceId() != target.getInstanceId())
		{ return new Availability(List.of(Reason.WRONG_TARGET), true); }
		final var geo = GeoEngine.getInstance();
		if (!geo.hasGeo(player.getX(), player.getY()) || !geo.hasGeo(target.getX(), target.getY())) { return new Availability(List.of(Reason.GEO), true); }
		final double mpCost = player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill);
		final var resources = new Resources(player.getCurrentMp(), mpCost, player.getCurrentHp(), skill.getHpConsume(),
			skill.getItemConsumeId() > 0 ? player.getInventory().getInventoryItemCount(skill.getItemConsumeId(), -1) : 0, skill.getItemConsumeCount(),
			player.getCharges(), skill.getChargeConsumeCount(), player.getChargedSouls(), skill.getMaxSoulConsumeCount());
		final var weapon = player.getActiveWeaponItem();
		final boolean weaponSkill = weapon == null || !weapon.useWeaponSkillsOnly() || !weapon.hasSkills()
			|| Arrays.stream(weapon.getSkills()).anyMatch(holder -> holder.getSkillId() == skill.getId());
		final double dx = (double) player.getX() - target.getX(), dy = (double) player.getY() - target.getY(), dz = (double) player.getZ() - target.getZ();
		final double range = Math.max(40, player.getMagicalAttackRange(skill));
		final boolean nativeConditions = player.checkDoCastConditions(skill) && skill.checkCondition(player, target, false) && skill.checkCondition(player, target, true);
		final var flags = new NativeFlags(ownerCurrent.getAsBoolean(), !player.isAlikeDead() && !target.isAlikeDead(), skill.isActive() && !skill.isPassive(),
			player.getKnownSkill(skill.getId()) == skill, player.isAllSkillsDisabled(), player.isSkillDisabled(skill), player.getSkillRemainingReuseTime(skill.getReuseHashCode()) > 0,
			targetVerified, dx * dx + dy * dy + dz * dz <= range * range, geo.canSeeTarget(player, target), weaponSkill, nativeConditions);
		return availability(resources, flags);
	}

	/** Enumeration is deliberately independent of the sparse authored capability rules. */
	public static List<Integer> classIds()
	{ return Arrays.stream(PlayerClass.values()).map(PlayerClass::getId).sorted().toList(); }

	/** The server already resolves common and parent trees. These facts do not grant acquired ranks. */
	public static List<TheoreticalSkill> theoretical(int classId)
	{
		final var playerClass = PlayerClass.getPlayerClass(classId);
		if (playerClass == null) { throw new IllegalArgumentException("Неизвестная профессия для теоретического каталога."); }
		return SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream()
			.map(learn -> new TheoreticalSkill(classId, new SkillRef(learn.getSkillId(), learn.getSkillLevel()), learn.getGetLevel(), learn.isAutoGet(), learn.isLearnedByNpc()))
			.sorted(Comparator.comparingInt((TheoreticalSkill value) -> value.skill().skillId()).thenComparingInt(value -> value.skill().skillLevel())).toList();
	}

	public static Snapshot capture(int classId, int classIndex, String catalogHash, Collection<Skill> learned,
		IntFunction<EnchantSkillLearn> enchantMetadata)
	{ return capture(classId, classIndex, catalogHash, learned, enchantMetadata, null); }

	private static Snapshot capture(int classId, int classIndex, String catalogHash, Collection<Skill> learned,
		IntFunction<EnchantSkillLearn> enchantMetadata, PhantomProgressionCatalog catalog)
	{
		if (PlayerClass.getPlayerClass(classId) == null || classIndex < 0 || catalogHash == null || !catalogHash.matches("[a-fA-F0-9]{64}")
			|| learned == null || learned.size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS || enchantMetadata == null)
		{ throw new IllegalArgumentException("Недопустимый фактически изученный набор навыков."); }
		final var values = List.copyOf(learned);
		if (values.stream().map(Skill::getId).distinct().count() != values.size())
		{ throw new IllegalArgumentException("В изученном наборе повторяется идентичность навыка."); }
		final var abilities = values.stream().sorted(Comparator.comparingInt(Skill::getId))
			.map(skill -> describe(skill, enchantMetadata.apply(skill.getId()), catalog)).toList();
		return new Snapshot(classId, classIndex, catalogHash, abilities, hash(VERSION + "/" + classId + "/" + classIndex + "/" + catalogHash + "/" + abilities));
	}

	/** Caller retains the admitted native owner; no class-tree skills are filled into the player. */
	public static Snapshot acquired(Player player, PhantomProgressionCatalog catalog)
	{
		Objects.requireNonNull(player); Objects.requireNonNull(catalog);
		final int classId = player.getActiveClass(), classIndex = player.getClassIndex();
		final var learned = List.copyOf(player.getAllSkills());
		final var result = capture(classId, classIndex, catalog.combinedHash(), learned,
			id -> EnchantSkillGroupsData.getInstance().getSkillEnchantmentBySkillId(id), catalog);
		if (player.getActiveClass() != classId || player.getClassIndex() != classIndex || player.getAllSkills().size() != learned.size()
			|| learned.stream().anyMatch(skill -> player.getKnownSkill(skill.getId()) != skill))
		{ throw new IllegalStateException("Изученные навыки или подкласс изменились во время чтения."); }
		return result;
	}

	private static Ability describe(Skill skill, EnchantSkillLearn enchant, PhantomProgressionCatalog catalog)
	{
		final int level = skill.getLevel();
		Fact<Integer> route = Fact.unknown(), rank = Fact.unknown();
		if (enchant != null && level > enchant.getBaseLevel() && enchant.getAllRoutes().contains(EnchantSkillLearn.getEnchantRoute(level)))
		{ route = Fact.known(EnchantSkillLearn.getEnchantRoute(level)); rank = Fact.known(EnchantSkillLearn.getEnchantIndex(level) + 1); }
		else if (enchant != null && level <= enchant.getBaseLevel()) { route = Fact.known(0); rank = Fact.known(0); }
		final var category = category(skill);
		final boolean simpleCosts = skill.getHpConsume() == 0 && skill.getItemConsumeCount() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0;
		final boolean damage = category == Category.SINGLE_TARGET_DAMAGE && PhantomNativeCombatCapability.mechanics(skill) != null;
		final boolean heal = category == Category.HEAL && "combat.heal".equals(PhantomSupportEffectAuthority.nativeCapability(skill))
			&& "SINGLE_TARGET".equals(PhantomSupportEffectAuthority.nativeScope(skill)) && skill.getAffectRange() == 0;
		// Existing native Party HEAL already handles exact item consumption (e.g. Major Heal).
		// Item availability remains a live eligibility condition; complete economy is still UNKNOWN in terminal metrics.
		final boolean healCosts = skill.getHpConsume() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0
			&& (skill.getItemConsumeCount() == 0 || skill.getItemConsumeId() > 0);
		final List<Support> support = simpleCosts && damage || healCosts && heal
			? List.of(Support.DISCOVERED, Support.SUPPORTED_FOR_SELECTION, Support.SUPPORTED_FOR_TRAIN, Support.SUPPORTED_FOR_NATIVE_EXECUTION)
			: List.of(Support.DISCOVERED, Support.NOT_SUPPORTED);
		final var effects = new ArrayList<String>();
		for (EffectScope scope : EffectScope.values())
		{
			final var values = skill.getEffects(scope);
			if (values != null) { for (var effect : values) { effects.add(scope + ":" + effect.getClass().getName() + ":" + effect.getEffectType()); } }
		}
		effects.sort(String::compareTo);
		Fact<List<String>> sources = Fact.unknown();
		if (catalog != null)
		{
			final var fact = catalog.skill(new SkillRef(skill.getId(), enchant != null && level > enchant.getBaseLevel() ? enchant.getBaseLevel() : level));
			if (fact != null && !fact.sourcePaths().isEmpty()) { sources = Fact.known(fact.sourcePaths().stream().sorted().toList()); }
		}
		// getAffectLimit samples RNG; no deterministic public min/max accessor exists here.
		return new Ability(new SkillRef(skill.getId(), level), route, rank, category, support, skill.getTargetType().name(),
			nonnegative(skill.getCastRange()), nonnegative(skill.getEffectRange()), skill.getAffectRange(), Fact.unknown(),
			skill.getMpInitialConsume() + (double) skill.getMpConsume(), skill.getHpConsume(), skill.getItemConsumeId(), skill.getItemConsumeCount(),
			skill.getChargeConsumeCount(), skill.getMaxSoulConsumeCount(), Math.addExact(skill.getHitTime(), skill.getCoolTime()), skill.getReuseDelay(),
			skill.isMagic(), skill.isPhysical(), skill.getElement(), skill.getElementPower(), skill.getPower(false, true), skill.getAbnormalTime(),
			skill.getAbnormalType().name(), skill.getAbnormalLevel(), skill.hasCastPreconditions(), effects, sources);
	}

	private static Category category(Skill skill)
	{
		if (skill.isPassive()) { return Category.PASSIVE; }
		if (skill.hasEffectType(EffectType.SUMMON, EffectType.SUMMON_PET, EffectType.SUMMON_NPC)) { return Category.SUMMON; }
		if (skill.hasEffectType(EffectType.RESURRECTION, EffectType.RESURRECTION_SPECIAL)) { return Category.RESURRECTION; }
		if (skill.getFlyType() != null || skill.hasEffectType(EffectType.TELEPORT, EffectType.TELEPORT_TO_TARGET)) { return Category.MOBILITY_ESCAPE; }
		if (skill.hasEffectType(EffectType.STUN, EffectType.ROOT, EffectType.PARALYZE, EffectType.SLEEP, EffectType.MUTE, EffectType.FEAR, EffectType.FAKE_DEATH)) { return Category.CONTROL; }
		if (skill.hasEffectType(EffectType.HEAL, EffectType.REBALANCE_HP)) { return Category.HEAL; }
		if (skill.isDamage()) { return skill.getAffectRange() > 0 || skill.isAOE() ? Category.AOE_DAMAGE : Category.SINGLE_TARGET_DAMAGE; }
		if (skill.isDebuff() || skill.hasEffectType(EffectType.DEBUFF)) { return Category.DEBUFF; }
		if ("combat.buff".equals(PhantomSupportEffectAuthority.nativeCapability(skill)))
		{ return "SELF".equals(PhantomSupportEffectAuthority.nativeScope(skill)) ? Category.SELF_BUFF : Category.PARTY_BUFF; }
		for (EffectType type : EffectType.values()) { if (type != EffectType.NONE && skill.hasEffectType(type)) { return Category.OTHER_ACTIVE; } }
		return Category.UNKNOWN;
	}

	private static Fact<Integer> nonnegative(int value) { return value < 0 ? Fact.unknown() : Fact.known(value); }
	private static String hash(String value)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
	private PhantomSkillInventory() { }
}
