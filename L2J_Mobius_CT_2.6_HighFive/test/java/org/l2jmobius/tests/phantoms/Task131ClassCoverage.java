/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.handler.EffectHandler;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPvpSkillSafety;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.qol.PersonalEffectMusicClassifier;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Offline inspection of stock loaders and route predicates. This probe never creates an actor. */
public final class Task131ClassCoverage
{
	private enum Family
	{
		NORMAL_ATTACK, MELEE_OR_UNTYPED_PHYSICAL, RANGED_PHYSICAL, BOW_DECLARED, CROSSBOW_DECLARED,
		CASTER_DAMAGE, BLOW_MECHANIC, POSITIONAL_DAGGER,
		CHARGES_OR_SOULS, AOE_DAMAGE, CONTROL, DEBUFF, NPC_AGGRO, PLAYER_PEEL, HEAL, RECHARGE,
		BUFF, SONG, DANCE, CLEANSE, RESURRECTION, SUMMON_OWNER, SPOIL, SWEEP, LEARNED_RETURN,
		LEARNED_MASS_RETURN, OTHER_MOBILITY, ITEM_ACTION, OTHER_ACTIVE, PASSIVE, NO_EXACT_TEMPLATE
	}

	private record Route(String select, String execute, String train) { }
	private record Facts(EnumSet<Family> families, String effects, String support, boolean pveSingle, boolean pvpSingle, boolean plainDamage, boolean npcAggro) { }
	private record Member(SkillLearn learn, Skill skill, Facts facts, Set<String> weapons) { }

	public static void main(String[] args) throws Exception
	{
		if (args.length != 3) { throw new IllegalArgumentException("Expected datapack path, base jar path, output file"); }
		final Path root = Path.of(args[0]).toRealPath();
		if (!Path.of(".").toRealPath().equals(root)) { throw new IllegalArgumentException("Stock XML readers require datapack as JVM working directory"); }
		final Path jar = Path.of(args[1]).toRealPath();
		final Path output = Path.of(args[2]).toAbsolutePath().normalize();
		if (output.getParent().toRealPath().startsWith(root) || Files.isSymbolicLink(output))
		{ throw new IllegalArgumentException("Output must be a physical file outside the datapack"); }
		final Path temp = Files.createTempFile(output.getParent(), ".task131-coverage-", ".tmp");
		try
		{
			write(root, jar, temp);
			Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		finally { Files.deleteIfExists(temp); }
	}

	private static void write(Path root, Path jar, Path output) throws Exception
	{
		final Properties general = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("config/General.ini"))) { general.load(reader); }
		final String custom = general.getProperty("CustomSkillsLoad", "false");
		if (!"true".equalsIgnoreCase(custom) && !"false".equalsIgnoreCase(custom))
		{ throw new IllegalArgumentException("Unexpected CustomSkillsLoad value " + custom); }
		ServerConfig.DATAPACK_ROOT = root.toFile();
		ServerConfig.SCRIPT_ROOT = root.resolve("data/scripts").toFile();
		GeneralConfig.CUSTOM_SKILLS_LOAD = Boolean.parseBoolean(custom);
		ThreadConfig.THREADS_FOR_LOADING = false;
		PlayerConfig.load();
		final Path skillRoot = root.resolve("data/stats/skills");
		final List<Path> skillXml = new ArrayList<>(xmlFiles(skillRoot, 1));
		if (GeneralConfig.CUSTOM_SKILLS_LOAD) { skillXml.addAll(xmlFiles(skillRoot.resolve("custom"), 1)); }
		final List<Path> assets = new ArrayList<>(skillXml);
		assets.addAll(xmlFiles(root.resolve("data/stats/players/skillTrees"), Integer.MAX_VALUE));
		assets.add(root.resolve("data/stats/players/classList.xml"));
		assets.add(root.resolve("data/EnchantSkillGroups.xml"));
		assets.add(root.resolve("config/General.ini"));
		assets.add(root.resolve("config/Player.ini"));
		assets.add(root.resolve("config/Scripts.xml"));
		final Path handlers = root.resolve("data/scripts/handlers");
		final List<Path> handlerSources = new ArrayList<>();
		handlerSources.add(handlers.resolve("EffectMasterHandler.java"));
		try (var files = Files.list(handlers.resolve("skill/effects")))
		{
			handlerSources.addAll(files.filter(file -> file.getFileName().toString().endsWith(".java")).toList());
		}
		final String handlersSha = digestAssets(root, handlerSources);
		final Map<Integer, Set<String>> weapons = weaponDeclarations(skillXml);
		final String before = digestAssets(root, assets);
		ScriptEngine.getInstance().executeScript(ScriptEngine.EFFECT_MASTER_HANDLER_FILE);
		if (EffectHandler.getInstance().getHandler("PhysicalDamage") == null || EffectHandler.getInstance().getHandler("Escape") == null)
		{ throw new IllegalStateException("Stock effect handlers were not registered"); }
		final var classes = ClassListData.getInstance().getClassList();
		final var trees = SkillTreeData.getInstance();
		final var skills = SkillData.getInstance();
		final var classIds = PhantomSkillInventory.classIds();
		if (!classes.keySet().equals(EnumSet.allOf(PlayerClass.class)) || classes.size() != classIds.size())
		{ throw new IllegalStateException("ClassListData and PlayerClass disagree"); }

		int ranks = 0;
		int familyRows = 0;
		int unresolved = 0;
		final var allPairs = new TreeSet<String>();
		try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8))
		{
			row(writer, "#TASK131_CLASS_COVERAGE_V1", "OFFLINE_NATIVE_CATALOG");
			row(writer, "#ASSET_SHA256", before, assets.size(), "SHA256(sorted relative path + tab + lowercase SHA256 + LF)");
			row(writer, "#EFFECT_SOURCE_SHA256", handlersSha, handlerSources.size(), EffectHandler.getInstance().size());
			row(writer, "#BASE_JAR_SHA256", digest(jar));
			row(writer, "#CUSTOM_SKILLS_LOAD", GeneralConfig.CUSTOM_SKILLS_LOAD);
			row(writer, "#SCOPE", "Native ClassListData/PlayerClass/SkillTreeData/SkillData and stock effect registration. Uses datapack root, CustomSkillsLoad and stock skill-duration/reuse flags. Player-owned acquired ranks, legal candidates, native effects, item/servitor inventories, transferred skills and TRAIN receipts are unobserved.");
			row(writer, "#FAMILY_RULES", "Skills can belong to multiple families. PLAYER_PEEL indicates control-effect candidates only. RANGED_PHYSICAL uses declared BOW/CROSSBOW or physical native castRange >=350; declared weapon metadata is XML skill-wide across normal/enchant cast conditions. POSITIONAL_DAGGER requires native rear gate and an observed DAGGER/DUALDAGGER declaration. OTHER_ACTIVE and unsupported families are retained as gaps. Passive N/A applies only to active dispatch. An absent class-tree rank does not decide item/servitor availability.");
			row(writer, "#STATUS", "DISCOVERED_OFFLINE_* = exact stock data; SOURCE_* / STATIC_* = a current source path or accepted offline shape, readiness/authority untested; PARTIAL = only some ranks meet a route; GAP = no positive route evidenced; UNKNOWN = insufficient static evidence; NOT_RUN = no native execute or FIT; N/A = passive or absent class-tree route.");
			row(writer, "row", "classId", "playerClass", "family", "rankCount", "skillId", "skillLevel", "skillName", "minCharacterLevel", "learnModes", "baseSp", "requiredItems", "prerequisiteSkills", "previousRank", "races", "nativeTarget", "castRange", "areaRadius", "castPreconditions", "declaredWeaponsAcrossLevels", "mpInitial", "mp", "hp", "itemId", "itemCount", "charges", "soulsMax", "reuseMs", "castMs", "effects", "discover", "select", "execute", "train", "runtimeEvidence");
			for (int classId : classIds)
			{
				final PlayerClass clazz = PlayerClass.getPlayerClass(classId);
				final Map<Family, List<Member>> byFamily = new EnumMap<>(Family.class);
				for (Family family : Family.values()) { byFamily.put(family, new ArrayList<>()); }
				final var complete = trees.getCompleteClassSkillTree(clazz);
				if (complete.isEmpty() || complete.size() != PhantomSkillInventory.theoretical(classId).size())
				{ throw new IllegalStateException("Empty or mismatched acquired theoretical tree for " + clazz); }
				for (SkillLearn learn : complete.values().stream().sorted(Comparator.comparingInt(SkillLearn::getSkillId).thenComparingInt(SkillLearn::getSkillLevel)).toList())
				{
					final Skill candidate = skills.getSkill(learn.getSkillId(), learn.getSkillLevel());
					final Skill skill = candidate != null && candidate.getId() == learn.getSkillId() && candidate.getLevel() == learn.getSkillLevel() ? candidate : null;
					final Set<String> declared = weapons.getOrDefault(learn.getSkillId(), Set.of());
					final Facts facts = skill == null ? new Facts(EnumSet.of(Family.NO_EXACT_TEMPLATE), "", "", false, false, false, false) : facts(skill, declared);
					final var member = new Member(learn, skill, facts, declared);
					for (Family family : facts.families()) { byFamily.get(family).add(member); }
					detail(writer, clazz, member);
					ranks++;
					allPairs.add(learn.getSkillId() + ":" + learn.getSkillLevel());
					if (skill == null) { unresolved++; }
				}

				for (Family family : Family.values())
				{
					final List<Member> members = byFamily.get(family);
					final Route route = aggregate(family, members);
					final String discover = switch (family)
					{
						case NORMAL_ATTACK -> "SOURCE_NATIVE_PLAYER";
						case ITEM_ACTION -> "UNKNOWN_ITEM_AVAILABILITY_REQUIRES_ACQUIRED_INVENTORY";
						case NO_EXACT_TEMPLATE -> members.isEmpty() ? "N/A_NO_MISSING_TEMPLATE" : "GAP_TREE_RANK_WITHOUT_EXACT_TEMPLATE";
						default -> members.isEmpty() ? "N/A_ABSENT_FROM_CLASS_TREE" : "DISCOVERED_OFFLINE_NATIVE_LOADER";
					};
					row(writer, "FAMILY", classId, clazz, family, members.size(), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", discover, route.select(), route.execute(), route.train(), "NOT_RUN");
					familyRows++;
				}
			}
			row(writer, "#COUNTS", "classes=" + classIds.size(), "classRanks=" + ranks, "distinctRanks=" + allPairs.size(), "familyRows=" + familyRows, "unresolvedClassRanks=" + unresolved);
			final String after = digestAssets(root, assets);
			if (!before.equals(after) || !handlersSha.equals(digestAssets(root, handlerSources))) { throw new IllegalStateException("Datapack inputs changed during inspection"); }
			System.out.println("TASK131_CLASS_COVERAGE_OFFLINE classes=" + classIds.size() + " classRanks=" + ranks + " distinctRanks=" + allPairs.size()
				+ " familyRows=" + familyRows + " unresolvedClassRanks=" + unresolved + " assetSha256=" + before + " assets=" + assets.size());
		}
	}

	private static Facts facts(Skill skill, Set<String> weapons)
	{
		final var families = EnumSet.noneOf(Family.class);
		final List<String> effects = new ArrayList<>();
		final Set<String> handlers = new TreeSet<>();
		for (EffectScope scope : EffectScope.values())
		{
			final var values = skill.getEffects(scope);
			if (values == null) { continue; }
			for (var effect : values)
			{
				final String name = effect.getClass().getSimpleName();
				handlers.add(name);
				effects.add(scope + ":" + name + ":" + effect.getEffectType());
			}
		}
		effects.sort(String::compareTo);
		if (skill.isPassive()) { return new Facts(EnumSet.of(Family.PASSIVE), String.join(",", effects), "", false, false, false, false); }
		final String support = PhantomSupportEffectAuthority.nativeCapability(skill);
		final var mode = skill.isMagic() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL;
		final boolean pve = skill.getAffectRange() == 0 && PhantomCombatSkillSafety.supports(skill, mode, false)
			&& PhantomPvpSkillSafety.supports(skill, mode, false) && !skill.hasEffectType(EffectType.AGGRESSION)
			&& (skill.isDamage() || skill.isDebuff() || PhantomPvpSkillSafety.priority(skill) >= 300);
		final boolean pvp = PhantomPvpSkillSafety.supports(skill, mode, false);
		final boolean simple = PhantomNativeCombatCapability.mechanics(skill) != null && skill.getHpConsume() == 0
			&& skill.getItemConsumeCount() == 0 && skill.getChargeConsumeCount() == 0 && skill.getMaxSoulConsumeCount() == 0;
		final boolean aggro = PhantomCombatSkillSafety.supportsNpcAggro(skill, false) || PhantomCombatSkillSafety.supportsNpcAreaAggro(skill, false);
		if (skill.isDamage())
		{
			if (skill.getAffectRange() > 0 || skill.isAOE()) { families.add(Family.AOE_DAMAGE); }
			if (skill.isMagic()) { families.add(Family.CASTER_DAMAGE); }
			if (skill.isPhysical())
			{
				families.add(weapons.contains("BOW") || weapons.contains("CROSSBOW") || skill.getCastRange() >= 350
					? Family.RANGED_PHYSICAL : Family.MELEE_OR_UNTYPED_PHYSICAL);
			}
		}
		if (skill.isPhysical() && weapons.contains("BOW")) { families.add(Family.BOW_DECLARED); }
		if (skill.isPhysical() && weapons.contains("CROSSBOW")) { families.add(Family.CROSSBOW_DECLARED); }
		if (skill.getBlowChance() > 0 || handlers.contains("Backstab") || handlers.contains("Blow") || handlers.contains("SoulBlow")) { families.add(Family.BLOW_MECHANIC); }
		if (PhantomPvpSkillSafety.requiresRear(skill) && (weapons.contains("DAGGER") || weapons.contains("DUALDAGGER"))) { families.add(Family.POSITIONAL_DAGGER); }
		if (skill.getChargeConsumeCount() > 0 || skill.getMaxSoulConsumeCount() > 0
			|| handlers.contains("FocusEnergy") || handlers.contains("FocusSouls") || handlers.contains("SoulEating")) { families.add(Family.CHARGES_OR_SOULS); }
		if (skill.hasEffectType(EffectType.STUN, EffectType.ROOT, EffectType.PARALYZE, EffectType.SLEEP, EffectType.MUTE, EffectType.FEAR))
		{ families.add(Family.CONTROL); families.add(Family.PLAYER_PEEL); }
		if (skill.isDebuff() || skill.hasEffectType(EffectType.DEBUFF)) { families.add(Family.DEBUFF); }
		if (skill.hasEffectType(EffectType.AGGRESSION)) { families.add(Family.NPC_AGGRO); }
		if (skill.hasEffectType(EffectType.HEAL, EffectType.REBALANCE_HP)) { families.add(Family.HEAL); }
		if (skill.hasEffectType(EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT)) { families.add(Family.RECHARGE); }
		if (skill.isDance())
		{
			switch (PersonalEffectMusicClassifier.getInstance().classify(skill))
			{
				case SONG -> families.add(Family.SONG);
				case DANCE -> families.add(Family.DANCE);
				default -> { }
			}
		}
		if (skill.hasEffectType(EffectType.BUFF) || "combat.buff".equals(support)) { families.add(Family.BUFF); }
		if (!skill.hasNegativeEffect() && (skill.hasEffectType(EffectType.DISPEL, EffectType.DISPEL_BY_SLOT)
			|| handlers.contains("DispelByCategory") || handlers.contains("DispelBySlot"))) { families.add(Family.CLEANSE); }
		if (skill.hasEffectType(EffectType.RESURRECTION, EffectType.RESURRECTION_SPECIAL)) { families.add(Family.RESURRECTION); }
		if (skill.hasEffectType(EffectType.SUMMON, EffectType.SUMMON_PET, EffectType.SUMMON_NPC)
			|| handlers.contains("SummonCubic") || handlers.contains("SummonTrap")) { families.add(Family.SUMMON_OWNER); }
		if (handlers.contains("Spoil")) { families.add(Family.SPOIL); }
		if (handlers.contains("Sweeper")) { families.add(Family.SWEEP); }
		if (skill.getId() == 1050 && handlers.contains("Escape")) { families.add(Family.LEARNED_RETURN); }
		else if (skill.getId() == 1255 && handlers.contains("Escape")) { families.add(Family.LEARNED_MASS_RETURN); }
		else if (skill.getFlyType() != null || skill.hasEffectType(EffectType.TELEPORT, EffectType.TELEPORT_TO_TARGET)) { families.add(Family.OTHER_MOBILITY); }
		if (families.isEmpty()) { families.add(Family.OTHER_ACTIVE); }
		return new Facts(families, String.join(",", effects), support == null ? "" : support, pve, pvp, simple, aggro);
	}

	private static Route route(Family family, Member member)
	{
		final Facts facts = member.facts();
		return switch (family)
		{
			case MELEE_OR_UNTYPED_PHYSICAL, RANGED_PHYSICAL, BOW_DECLARED, CROSSBOW_DECLARED, CASTER_DAMAGE, BLOW_MECHANIC, POSITIONAL_DAGGER, CHARGES_OR_SOULS, CONTROL, DEBUFF ->
				facts.pveSingle() || facts.pvpSingle()
					? new Route("STATIC_SINGLE_TARGET_" + (facts.pveSingle() ? "PVE" : "") + (facts.pveSingle() && facts.pvpSingle() ? "_" : "") + (facts.pvpSingle() ? "PVP" : ""),
						"SOURCE_LEARNED_SINGLE_TARGET_WITH_NATIVE_GATES", facts.plainDamage() ? "SOURCE_V4_DAMAGE_EXPERIMENT_REQUIRES_FROZEN_ALLOWLIST_AND_RECEIPT" : "GAP_NO_MATCHING_TRAIN_ROUTE_PROVEN")
					: gap();
			case NPC_AGGRO -> facts.npcAggro() ? new Route("STATIC_NPC_AGGRO", "SOURCE_NPC_AGGRO_NATIVE_GATES", "GAP_NO_TANK_TRAIN_ROUTE") : gap();
			case HEAL -> support(facts, "combat.heal");
			case RECHARGE -> support(facts, "combat.recharge");
			case BUFF, SONG, DANCE -> support(facts, "combat.buff");
			case RESURRECTION -> support(facts, "combat.resurrection");
			case SPOIL, SWEEP -> new Route("SOURCE_ACQUISITION_EXACT_CAPABILITY_REQUIRED", "SOURCE_NATIVE_SPOIL_SWEEP_WITH_ELIGIBLE_NPC", "GAP_NO_SPOIL_TRAIN_ROUTE");
			case LEARNED_RETURN, LEARNED_MASS_RETURN -> new Route("SOURCE_RETREAT_WITH_AUTHORITY", "SOURCE_STOCK_ESCAPE_EXACT_SHAPE_AND_ROSTER", "GAP_NO_ESCAPE_TRAIN_ROUTE");
			case SUMMON_OWNER -> new Route("UNKNOWN_EXACT_SUMMON_CATALOG_AND_OWNER", "SOURCE_OWNED_SERVITOR_ADAPTER_ONLY", "GAP_NO_SUMMON_TRAIN_ROUTE");
			case PLAYER_PEEL -> new Route("GAP_NO_TACTICAL_PEEL_ROUTE_PROVEN", "UNKNOWN_CONTROL_EFFECT_REQUIRES_PVP_AUTHORITY", "GAP_NO_PEEL_TRAIN_ROUTE");
			case PASSIVE -> new Route("N/A_PASSIVE", "N/A_PASSIVE", "N/A_PASSIVE");
			default -> gap();
		};
	}

	private static Route support(Facts facts, String capability)
	{
		return capability.equals(facts.support())
			? new Route("STATIC_PARTY_" + capability.substring(7).toUpperCase(java.util.Locale.ROOT), "SOURCE_PARTY_SUPPORT_WITH_TARGET_AND_OWNER_GATES", "GAP_NO_V4_SUPPORT_CHOICE_ROUTE")
			: gap();
	}

	private static Route gap() { return new Route("GAP_UNPROVEN_SELECTION", "GAP_UNPROVEN_EXECUTION", "GAP_UNPROVEN_TRAIN"); }

	private static Route aggregate(Family family, List<Member> members)
	{
		if (family == Family.NORMAL_ATTACK) { return new Route("SOURCE_STOCK_AUTOATTACK_ELIGIBILITY", "SOURCE_ATTACK_WITH_OWNER_RANGE_AND_WEAPON", "GAP_NO_AUTOATTACK_TRAIN_ROUTE"); }
		if (family == Family.ITEM_ACTION) { return new Route("SOURCE_PAID_SHOTS_OR_SUPPLY_ONLY", "UNKNOWN_ACQUIRED_ITEM_AND_STAGE_REQUIRED", "GAP_NO_ITEM_ACTION_TRAIN_ROUTE"); }
		if (members.isEmpty()) { return new Route("N/A_NO_CLASS_TREE_MATCH", "N/A_NO_CLASS_TREE_MATCH", "N/A_NO_CLASS_TREE_MATCH"); }
		final List<Route> routes = members.stream().map(member -> route(family, member)).toList();
		return new Route(rollup(routes.stream().map(Route::select).toList()), rollup(routes.stream().map(Route::execute).toList()), rollup(routes.stream().map(Route::train).toList()));
	}

	private static String rollup(List<String> values)
	{
		final long routes = values.stream().filter(s -> s.startsWith("SOURCE") || s.startsWith("STATIC")).count();
		final String detail = String.join("|", new TreeSet<>(values));
		return routes > 0 && routes < values.size() ? "PARTIAL(" + routes + "/" + values.size() + "):" + detail : detail;
	}

	private static void detail(BufferedWriter writer, PlayerClass clazz, Member member) throws Exception
	{
		final SkillLearn learn = member.learn();
		final Skill skill = member.skill();
		final String modes = (learn.isAutoGet() ? "AUTO;" : "") + (learn.isLearnedByNpc() ? "NPC;" : "") + (learn.isLearnedByFS() ? "FS;" : "");
		final String items = String.join(",", learn.getRequiredItems().stream().map(item -> item.getId() + ":" + item.getCount()).sorted().toList());
		final String requires = String.join(",", learn.getPreReqSkills().stream().map(req -> req.getSkillId() + ":" + req.getSkillLevel()).sorted().toList());
		final String races = String.join(",", learn.getRaces().stream().map(Enum::name).sorted().toList());
		final String previous = learn.getSkillLevel() > 1 ? learn.getSkillId() + ":" + (learn.getSkillLevel() - 1) : "";
		final String declared = String.join(",", new TreeSet<>(member.weapons()));
		final List<Route> routes = member.facts().families().stream().map(family -> route(family, member)).toList();
		row(writer, "SKILL", clazz.getId(), clazz, member.facts().families(), 1, learn.getSkillId(), learn.getSkillLevel(),
			skill == null ? learn.getName() : skill.getName(), learn.getGetLevel(), modes, learn.getLevelUpSp(), items, requires, previous, races,
			skill == null ? "" : skill.getTargetType(), skill == null ? "" : skill.getCastRange(), skill == null ? "" : skill.getAffectRange(),
			skill == null ? "" : skill.hasCastPreconditions(), declared, skill == null ? "" : skill.getMpInitialConsume(),
			skill == null ? "" : skill.getMpConsume(), skill == null ? "" : skill.getHpConsume(), skill == null ? "" : skill.getItemConsumeId(),
			skill == null ? "" : skill.getItemConsumeCount(), skill == null ? "" : skill.getChargeConsumeCount(),
			skill == null ? "" : skill.getMaxSoulConsumeCount(), skill == null ? "" : skill.getReuseDelay(), skill == null ? "" : skill.getHitTime(),
			member.facts().effects(), skill == null ? "GAP_TREE_RANK_WITHOUT_EXACT_TEMPLATE" : "DISCOVERED_OFFLINE_EXACT_RANK_REQUIRES_ACQUIRED",
			rollup(routes.stream().map(Route::select).toList()), rollup(routes.stream().map(Route::execute).toList()), rollup(routes.stream().map(Route::train).toList()), "NOT_RUN");
	}

	private static Map<Integer, Set<String>> weaponDeclarations(List<Path> files) throws Exception
	{
		final var result = new TreeMap<Integer, Set<String>>();
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		final var builder = factory.newDocumentBuilder();
		for (Path file : files)
		{
			final var nodes = builder.parse(file.toFile()).getDocumentElement().getElementsByTagName("skill");
			for (int i = 0; i < nodes.getLength(); i++)
			{
				final var skill = (org.w3c.dom.Element) nodes.item(i);
				final var using = skill.getElementsByTagName("using");
				if (using.getLength() == 0) { continue; }
				final var kinds = result.computeIfAbsent(Integer.valueOf(skill.getAttribute("id")), _ -> new TreeSet<>());
				for (int j = 0; j < using.getLength(); j++)
				{
					final var condition = (org.w3c.dom.Element) using.item(j);
					for (var ancestor = condition.getParentNode(); ancestor != skill; ancestor = ancestor.getParentNode())
					{
						if ("conditions".equals(ancestor.getNodeName()) && ancestor.getParentNode() == skill && condition.hasAttribute("kind"))
						{ kinds.addAll(Arrays.asList(condition.getAttribute("kind").split("[, ]+"))); }
					}
				}
			}
		}
		return result;
	}

	private static List<Path> xmlFiles(Path directory, int depth) throws Exception
	{
		try (var stream = Files.walk(directory, depth))
		{ return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList(); }
	}

	private static String digestAssets(Path root, List<Path> assets) throws Exception
	{
		final var fingerprint = MessageDigest.getInstance("SHA-256");
		for (Path asset : assets.stream().sorted().toList())
		{
			final String name = root.relativize(asset).toString().replace('\\', '/');
			fingerprint.update((name + "\t" + digest(asset) + "\n").getBytes(StandardCharsets.UTF_8));
		}
		return HexFormat.of().formatHex(fingerprint.digest());
	}

	private static String digest(Path file) throws Exception
	{
		final var hash = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(file))
		{
			final byte[] buf = new byte[65536];
			for (int count; (count = input.read(buf)) != -1;) { hash.update(buf, 0, count); }
		}
		return HexFormat.of().formatHex(hash.digest());
	}

	private static void row(BufferedWriter writer, Object... values) throws Exception
	{
		for (int i = 0; i < values.length; i++)
		{
			if (i > 0) { writer.write('\t'); }
			writer.write(String.valueOf(values[i]).replace('\t', ' ').replace('\r', ' ').replace('\n', ' '));
		}
		writer.write('\n');
	}
}
