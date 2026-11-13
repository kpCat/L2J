/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.EnchantSkillLearn;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory.Category;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory.Support;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/** DB-free contracts for actual learned state. Fixture IDs are never algorithm allowlists. */
public final class AutonomousTrainingChecks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) { throw new IllegalArgumentException("Expected inventory, shortlist or eligibility"); }
		switch (args[0])
		{
			case "inventory" -> inventory();
			case "shortlist" -> shortlist();
			case "eligibility" -> eligibility();
			case "catalog" -> { catalog(); return; }
			case "objective" -> objective();
			case "config" -> configuration();
			case "curriculum" -> curriculum();
			default -> throw new IllegalArgumentException("Unknown autonomous contract group");
		}
		System.out.println("AUTONOMOUS_TRAINING_GREEN " + args[0]);
	}

	private static void inventory()
	{
		final Skill hydro = skill(1235, 28, false, EffectType.MAGICAL_ATTACK, 69, 100, 0);
		final Skill passive = skill(1064, 1, true, EffectType.NONE, 0, 0, 0);
		final Skill unknown = skill(7777, 1, false, EffectType.NONE, 0, 0, 0);
		final var first = PhantomSkillInventory.capture(103, 0, "a".repeat(64), List.of(hydro, passive, unknown), _ -> null);
		check(PhantomSkillInventory.theoretical(103).stream().anyMatch(v -> v.skill().equals(new SkillRef(1235, 28))), "Canonical complete parent skill tree lost inherited Hydro Blast");
		check(first.abilities().size() == 3, "Full acquired inventory omitted passive/unknown skills");
		check(first.abilities().stream().anyMatch(v -> v.skill().equals(new SkillRef(1235, 28))), "Third profession lost actually learned inherited Hydro Blast");
		check(first.abilities().stream().anyMatch(v -> v.category() == Category.PASSIVE && !v.support().contains(Support.SUPPORTED_FOR_SELECTION)), "Passive became a cast candidate");
		check(first.abilities().stream().anyMatch(v -> v.skill().skillId() == 7777 && v.category() == Category.UNKNOWN && !v.support().contains(Support.SUPPORTED_FOR_TRAIN)), "Unknown mechanics became trainable DAMAGE");
		final var second = PhantomSkillInventory.capture(103, 0, "a".repeat(64), List.of(passive), _ -> null);
		final var paidHeal = PhantomSkillInventory.capture(105, 0, "a".repeat(64), List.of(skill(1401, 11, false, EffectType.HEAL, 80, 946, 0, 3031, 1)), _ -> null).abilities().getFirst();
		check(paidHeal.support().contains(Support.SUPPORTED_FOR_TRAIN) && paidHeal.itemId() == 3031 && paidHeal.itemCount() == 1, "Native Party item-paid HEAL was hidden or item cost was erased");
		check(second.abilities().size() == 1 && second.abilities().stream().noneMatch(v -> v.skill().skillId() == 1235), "Class theory granted an unlearned action");
		final var enchant = new EnchantSkillLearn(1235, 28); enchant.addNewEnchantRoute(1, 1);
		final var refreshed = PhantomSkillInventory.capture(103, 1, "a".repeat(64), List.of(skill(1235, 101, false, EffectType.MAGICAL_ATTACK, 41, 140, 0)), _ -> enchant);
		final var actual = refreshed.abilities().getFirst();
		check(actual.skill().skillLevel() == 101 && actual.enchantRoute().value() == 1 && actual.enchantLevel().value() == 1, "Current enchant canonical identity was downcast");
		check(actual.mpCost() == 41 && actual.power() == 140 && !first.fingerprint().equals(refreshed.fingerprint()), "Refresh retained stale rank characteristics or subclass identity");
		final var categories = PhantomSkillInventory.capture(88, 0, "a".repeat(64), List.of(
			skill(6001, 1, false, EffectType.SUMMON, 20, 0, 0), skill(6002, 1, false, EffectType.MAGICAL_ATTACK, 20, 20, 300),
			skill(6003, 1, false, EffectType.STUN, 20, 0, 0)), _ -> null);
		check(categories.abilities().stream().map(PhantomSkillInventory.Ability::category).toList().equals(List.of(Category.SUMMON, Category.AOE_DAMAGE, Category.CONTROL)), "Canonical effect categories were relabeled as single-target support");
		check(categories.abilities().stream().noneMatch(v -> v.support().contains(Support.SUPPORTED_FOR_TRAIN)), "Discovered summon/AoE/control was advertised as implemented");
		check(PhantomSkillInventory.classIds().size() == PlayerClass.values().length && PhantomSkillInventory.classIds().contains(88), "Discovery is still a fixed historical class list");
		check(!actual.maximumTargets().known() && actual.maximumTargets().value() == null, "Unknown max-target bound became zero or an RNG draw");
		reject(() -> PhantomSkillInventory.capture(103, 0, "a".repeat(64), List.of(hydro, hydro), _ -> null), "Duplicate learned identity admitted");
		check(PhantomSkillInventory.capture(103, 0, "A".repeat(64), List.of(hydro), _ -> null).catalogHash().equals("A".repeat(64)), "Canonical progression builder uses uppercase SHA; inventory rejected or rewrote its identity");
	}

	private static void shortlist()
	{
		final var all = new ArrayList<SkillRef>();
		for (int id = 200; id < 207; id++) { all.add(new SkillRef(id, 1)); }
		final var one = AutonomousTrainingV1.shortlist(all, 0, 4);
		final var two = AutonomousTrainingV1.shortlist(all, one.nextCursor(), 4);
		check(one.candidates().size() == 4 && two.candidates().size() == 4 && all.size() == 7, "Full inventory and bound4 shortlist were conflated");
		final var covered = new HashSet<>(one.candidates()); covered.addAll(two.candidates());
		check(covered.size() == 7 && two.candidates().contains(new SkillRef(206, 1)), "High IDs are permanently excluded by first-four truncation");
		check(one.equals(AutonomousTrainingV1.shortlist(all.reversed(), 0, 4)), "Shortlist cannot be reproduced from the same cursor");
		check(!one.randomInclusion() && one.nextCursor() == 4 && two.nextCursor() == 8, "Deterministic cursor or sampling provenance changed");
		check(AutonomousTrainingV1.shortlist(List.of(), 8, 4).candidates().isEmpty(), "Empty legal set produced fake alternatives");
		reject(() -> AutonomousTrainingV1.shortlist(List.of(new SkillRef(200, 1), new SkillRef(200, 101)), 0, 4), "Two versions of one acquired skill admitted");
		reject(() -> AutonomousTrainingV1.shortlist(all, -1, 4), "Invalid campaign cursor admitted");
	}

	private static void eligibility()
	{
		final var ready = new PhantomSkillInventory.NativeFlags(true, true, true, true, false, false, false, true, true, true, true, true);
		final var enough = new PhantomSkillInventory.Resources(100, 40, 100, 0, 1, 1, 1, 1, 1, 1);
		check(PhantomSkillInventory.availability(enough, ready).ready(), "All native gates passed but the action was lost");
		final var poor = new PhantomSkillInventory.Resources(10, 40, 5, 6, 0, 1, 0, 1, 0, 1);
		final var blocked = new PhantomSkillInventory.NativeFlags(false, true, true, true, false, true, true, true, false, false, false, true);
		final var reasons = PhantomSkillInventory.availability(poor, blocked).reasons();
		check(reasons.containsAll(List.of(PhantomSkillInventory.Reason.MP, PhantomSkillInventory.Reason.HP,
			PhantomSkillInventory.Reason.ITEM, PhantomSkillInventory.Reason.CHARGE, PhantomSkillInventory.Reason.SOUL,
			PhantomSkillInventory.Reason.OWNER, PhantomSkillInventory.Reason.COOLDOWN, PhantomSkillInventory.Reason.DISABLED,
			PhantomSkillInventory.Reason.RANGE, PhantomSkillInventory.Reason.LOS, PhantomSkillInventory.Reason.WEAPON)), "A real temporary blocker lost its diagnostic reason");
		check(!PhantomSkillInventory.availability(poor, blocked).ready(), "Unavailable skill admitted to selection");
		final var nativeRefusal = new PhantomSkillInventory.NativeFlags(true, true, true, true, false, false, false, true, true, true, true, false);
		check(PhantomSkillInventory.availability(enough, nativeRefusal).reasons().equals(List.of(PhantomSkillInventory.Reason.NATIVE_CONDITION)), "Unexplained native refusal invented a specific resource/target reason");
		reject(() -> new PhantomSkillInventory.Resources(Double.NaN, 40, 100, 0, 1, 1, 1, 1, 1, 1), "Unknown native resource value silently became zero");
	}

	private static void catalog()
	{
		final var rows = new ArrayList<String>();
		final var unique = new HashSet<SkillRef>();
		long learnFacts = 0;
		for (int classId : PhantomSkillInventory.classIds())
		{
			final var type = PlayerClass.getPlayerClass(classId);
			final var theory = PhantomSkillInventory.theoretical(classId);
			theory.forEach(value -> unique.add(value.skill())); learnFacts += theory.size();
			rows.add("{\"classId\":" + classId + ",\"name\":" + TrainingStore.quote(type.name()) + ",\"tier\":" + type.level()
				+ ",\"parentClassId\":" + (type.getParent() == null ? "null" : type.getParent().getId()) + ",\"completeTheoreticalLearnFacts\":" + theory.size()
				+ ",\"actualAcquiredSkills\":null,\"trainReadiness\":\"NOT_ASSESSED\",\"reason\":\"ACTUAL_ACTOR_CENSUS_NOT_RUN\"}");
		}
		System.out.println("{\"schema\":\"AUTONOMOUS_CLASS_DISCOVERY_V1\",\"source\":\"PlayerClass/SkillTreeData.getCompleteClassSkillTree\",\"classesDiscovered\":" + rows.size()
			+ ",\"completeTheoreticalLearnFacts\":" + learnFacts + ",\"uniqueTheoreticalSkillVersions\":" + unique.size()
			+ ",\"actualAcquiredSkillCount\":null,\"nativeReadiness\":\"NOT_RUN\",\"classes\":[" + String.join(",", rows) + "]}");
	}

	private static void curriculum()
	{
		final var a = List.of(new SkillRef(200, 1), new SkillRef(201, 1), new SkillRef(202, 1), new SkillRef(203, 1), new SkillRef(204, 1));
		final var b = List.of(new SkillRef(300, 1), new SkillRef(301, 1));
		final var olderClass = new AutonomousTrainingV1.ActorSeed("a".repeat(64), 94, 77, 0, a, List.of(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, a)));
		final var anotherClass = new AutonomousTrainingV1.ActorSeed("b".repeat(64), 88, 60, 0, b, List.of(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.MELEE_PHYSICAL, b)));
		final var counts = Map.of("class/94", 10L, "class/88", 0L);
		final var selected = AutonomousTrainingV1.nextCandidate(List.of(olderClass, anotherClass), counts, Map.of(), 0, 183).orElseThrow();
		check(selected.actor().classId() == 88, "Curriculum kept repeating a covered historical class");
		final var same = AutonomousTrainingV1.nextCandidate(List.of(anotherClass, olderClass), counts, Map.of(), 0, 183).orElseThrow();
		check(selected.equals(same), "Input enumeration changed the reproducible curriculum choice");
		final var first = AutonomousTrainingV1.nextCandidate(List.of(olderClass), Map.of(), Map.of(), 0, 183).orElseThrow();
		final var second = AutonomousTrainingV1.nextCandidate(List.of(olderClass), Map.of(), Map.of(first.shortlistKey(), first.shortlist().nextCursor()), 10, 183).orElseThrow();
		final var coverage = new HashSet<>(first.shortlist().candidates()); coverage.addAll(second.shortlist().candidates());
		check(coverage.size() == 5 && second.shortlist().candidates().contains(new SkillRef(204, 1)), "Interleaved cohorts reset a pool cursor and starved a learned action");
		reject(() -> new AutonomousTrainingV1.ActorSeed("c".repeat(64), 94, 77, 0, b, List.of(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, a))), "Theoretical/unlearned actions entered a training proposal");
		final var insufficient = new AutonomousTrainingV1.ActorSeed("d".repeat(64), 88, 60, 0, List.of(b.getFirst()), List.of(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.MELEE_PHYSICAL, List.of(b.getFirst()))));
		check(AutonomousTrainingV1.nextCandidate(List.of(insufficient), Map.of(), Map.of(), 0, 183).isEmpty(), "Single DAMAGE action was padded with fake alternatives");
		reject(() -> AutonomousTrainingV1.nextCandidate(List.of(olderClass), Map.of("class/94", -1L), Map.of(), 0, 183), "Corrupt coverage became a fresh zero count");
		final var both = new ArrayList<>(a); both.addAll(b);
		final var hybrid = new AutonomousTrainingV1.ActorSeed("e".repeat(64), 88, 60, 0, both, List.of(
			new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, a),
			new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.MELEE_PHYSICAL, b)));
		final var physical = AutonomousTrainingV1.nextCandidate(List.of(hybrid), Map.of("class/88/family/DAMAGE/mode/RANGED_MAGIC", 10L), Map.of(), 0, 183).orElseThrow();
		check(physical.pool().mode() == PhantomCombatMode.MELEE_PHYSICAL && physical.shortlist().candidates().equals(b), "One combat-mode request mixed physical and magic alternatives or starved a mode");
		reject(() -> new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, null, a), "DAMAGE proposal lost its native Combat mode");
	}

	private static void objective()
	{
		final var samples = new ArrayList<ActionAdvantageV2.Sample>();
		for (int i = 0; i < 48; i++) { samples.add(advantageSample(i, i < 32 ? "TRAIN" : i < 40 ? "TUNING" : "AUDIT", i >= 16 && i < 32, i % 2 == 0 ? 88 : 94)); }
		final var frozen = ActionAdvantageV2.freeze("1".repeat(32), samples);
		check(frozen.sufficient() && frozen.weightedExamples().size() == 16, "Generic objective rejected a lawful independent class-balanced cohort");
		check(frozen.targets().stream().filter(t -> t.sample().selection().experimental()).allMatch(t -> t.baselineCount() == 7 && t.advantage() > 0), "Own control block leaked into exact-cell baseline or paid negative/positive utility was relabeled");
		check(ActionAdvantageV2.restore(frozen.runId(), frozen.bytes(), frozen.datasetSha()).datasetSha().equals(frozen.datasetSha()), "V2 cold targets changed");
		final var withoutA = ActionAdvantageV2.freeze("2".repeat(32), samples.stream().filter(s -> !s.selection().split().equals("TRAIN") || s.selection().experimental()).toList());
		check(!withoutA.sufficient() && withoutA.targets().isEmpty(), "Missing controls became an invented counterfactual");
		final var duplicate = new ArrayList<>(samples); duplicate.add(samples.getFirst());
		reject(() -> ActionAdvantageV2.freeze("3".repeat(32), duplicate), "One native action/episode received duplicate credit");
		for (int i = 48; i < 64; i++) { samples.add(advantageSample(i, "TRAIN", true, 88)); }
		final var balanced = ActionAdvantageV2.freeze("4".repeat(32), samples);
		double one = 0, two = 0;
		for (var target : balanced.targets())
		{
			if (!target.sample().selection().split().equals("TRAIN") || !target.sample().selection().experimental()) { continue; }
			if (target.sample().selection().observation().self().classId() == 88) { one += target.weight(); } else { two += target.weight(); }
		}
		check(Math.abs(one - two) < 1e-9 && balanced.weightedExamples().stream().allMatch(e -> e.weight() > 0 && e.weight() <= 4), "Dominant class overwhelmed the common model or violated bounded weight contract");
		check(Math.abs(frozen.constantLoss() - balanced.constantLoss()) < 1e-9, "Constant benchmark ignored class balancing and moved when only the dominant class was repeated");
		TrainExplorationV1.validateAutonomousPermit(true, true, "COLLECT", "autonomous009-fixture", "autonomous009-fixture", "TRAIN", true, LegalActionV3.Family.DAMAGE, 42, 12, "f".repeat(64));
		reject(() -> TrainExplorationV1.validateAutonomousPermit(false, true, "COLLECT", "autonomous009-fixture", "autonomous009-fixture", "TRAIN", true, LegalActionV3.Family.DAMAGE, 42, 12, "f".repeat(64)), "MAIN/PLAY acquired exploration admitted");
		reject(() -> TrainExplorationV1.validateAutonomousPermit(true, true, "COLLECT", "autonomous009-fixture", "autonomous009-fixture", "AUDIT", true, LegalActionV3.Family.HEAL, 42, 12, "f".repeat(64)), "Experimental draw entered held-out split");
		reject(() -> TrainExplorationV1.validateAutonomousPermit(true, true, "COLLECT", "autonomous009-fixture", "autonomous009-fixture", "TRAIN", true, LegalActionV3.Family.DAMAGE, 0, 12, "f".repeat(64)), "Actor-unbound Party opportunity admitted");
		check(TacticalStoreV3.autonomousSampler(frozen.metrics(), ActionAdvantageV2.REVISION), "V2 cold optimizer sampler rejected");
		check(!TacticalStoreV3.autonomousSampler("{\"objective\":\"" + ActionAdvantageV1.OBJECTIVE + "\"}", ActionAdvantageV1.REVISION), "Historical007 sampler was reinterpreted as V2");
		reject(() -> TacticalStoreV3.autonomousSampler(frozen.metrics(), ActionAdvantageV1.REVISION), "V2 optimizer silently accepted historical sampler lineage");
	}
	/** Same receipt constructors as the local007 fixture; these are contract facts, never native evidence. */
	private static ActionAdvantageV2.Sample advantageSample(int i, String split, boolean experimental, int classId)
	{
		int actor = 100 + i, target = 1000 + i; long profile = 10000 + i, epoch = 100000 + i, spawn = 200000 + i;
		final var facts = Map.of(TacticalObservationV3.Field.HP, TacticalObservationV3.Fact.known(.8), TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(2000), TacticalObservationV3.Field.LEVEL, TacticalObservationV3.Fact.known(77));
		final var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, actor, epoch, 0, classId, facts, List.of(), List.of(), List.of(), List.of());
		final var enemy = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY, target, spawn, 0, 0, facts, List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 150, profile, actor, epoch, "solo:" + actor, List.of(self, enemy));
		final var stock = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", profile, actor, epoch, 1, target, spawn, observation.partyIdentity(), 200, 1, 60, 4000, 900, false, true);
		final var other = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", profile, actor, epoch, 1, target, spawn, observation.partyIdentity(), 201, 2, 70, 5000, 900, false, true);
		final var selected = experimental ? other : stock; double hp = experimental ? (classId == 88 ? 900 : 700) : 500, mp = selected.mpCost();
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(stock, other), selected, stock, 12);
		final var receipt = new TacticalReceiptV3(String.format("%064x", i + 1), attempt, 2000, 2000 - hp, hp, mp, 1000000000L, TacticalReceiptV3.reward(hp, 2000, mp, 1000000000L), new HealEvidenceV3.SourceOwner(1, "Combat/test/" + i));
		final var row = new TacticalStoreV3.Row(split, "autonomous009|" + split + "|fixture|" + i, receipt); String id = String.format("%064x", 1000 + i);
		final var actorResult = new TacticalEpisodeV3.Actor(profile, actor, epoch, classId, 77, 900, 800, false, 50000L, 10000L);
		final var kill = new TacticalEpisodeV3.Kill(target, 21798, spawn, 0, actor, 1);
		final var episode = new TacticalEpisodeV3(id, "b".repeat(32), classId == 88 ? "SOLO" : "PARTY", 100, 3000000100L, "NATIVE_KILL", List.of(actorResult), List.of(kill), List.of(receipt.eventKey()), hp, 0, mp, 0);
		final var boundary = new TerminalRewardReceiptV1.Boundary("a".repeat(64), "b".repeat(32), 1, 0, target, spawn, 100, 2000000100L, 3000000100L, true);
		final var terminal = TerminalRewardReceiptV1.create(boundary, episode, List.of(new TerminalRewardReceiptV1.TimedAction(row, 0, 200, 300, 400, 500, false)), split, ActionAdvantageV2.DATASET_PREFIX + "fixture", TerminalRewardReceiptV1.Config.defaults());
		final String block = split.equals("TRAIN") ? "block" + (i % 16) : split + i;
		final var selection = new TrainExplorationV1.Selection(id, block, (classId == 88 ? "c" : "d").repeat(64), split, experimental, observation, List.of(stock, other), stock, selected, experimental ? .25 : 1, experimental ? .9 : 0, experimental ? "EXPLORATION_ALTERNATIVE" : "CONTROL_STOCK", "", 12, "f".repeat(64));
		return new ActionAdvantageV2.Sample(selection, terminal);
	}

	private static void configuration() throws Exception
	{
		final var path = java.nio.file.Files.createTempFile("goal009-config-", ".ini");
		final String body = "schemaVersion=1\nautonomous009Version=1\nautonomous009Enabled=false\nautonomous009Phase=OFF\nautonomous009Comparator=" + AutonomousTrainingV1.COMPARATOR
			+ "\nclasses=auto\nlevels=auto\nautonomous009MinLevel=20\nautonomous009MaxLevel=21\nautonomous009ClassWeights=88:3\nv3Enabled=true\nmaxHeapMb=4096\nautonomous009MemoryBudgetMb=8192\n";
		try
		{
			java.nio.file.Files.writeString(path, body, java.nio.charset.StandardCharsets.UTF_8);
			final var config = TrainingConfig.load(path);
			check(config.autonomousConfigured() && config.classes().size() == PlayerClass.values().length && config.levels().equals(List.of(20, 21)), "Automatic classes/level interval is still a manual class list");
			check(config.autonomousClassWeights().get(88) == 3 && config.autonomousClassWeights().get(94) == 1, "Class distribution lost requested/default weight");
			for (String invalid : List.of(body.replace("MinLevel=20", "MinLevel=22"), body.replace("MemoryBudgetMb=8192", "MemoryBudgetMb=4096"), body.replace("ClassWeights=88:3", "ClassWeights=999:3")))
			{
				java.nio.file.Files.writeString(path, invalid, java.nio.charset.StandardCharsets.UTF_8);
				boolean rejected = false; try { TrainingConfig.load(path); } catch (IllegalArgumentException expected) { rejected = true; }
				check(rejected, "Invalid interval/memory/class distribution accepted");
			}
		}
		finally { java.nio.file.Files.deleteIfExists(path); }
	}

	private static Skill skill(int id, int level, boolean passive, EffectType type, int mp, int power, int area)
	{ return skill(id, level, passive, type, mp, power, area, 0, 0); }
	private static Skill skill(int id, int level, boolean passive, EffectType type, int mp, int power, int area, int itemId, int itemCount)
	{
		final var values = new StatSet();
		values.set("skill_id", id); values.set("level", level); values.set("operateType", passive ? "P" : "A1");
		values.set("targetType", "ONE"); values.set("isMagic", 1); values.set("effectPoint", type == EffectType.HEAL ? 1 : -1);
		values.set("mpConsume", mp); values.set("power", power); values.set("affectRange", area); values.set("castRange", 900);
		values.set("itemConsumeId", itemId); values.set("itemConsumeCount", itemCount);
		final var result = new Skill(values);
		if (type != EffectType.NONE)
		{
			final var parameters = new StatSet(); parameters.set("name", "ContractEffect");
			result.addEffect(EffectScope.GENERAL, new AbstractEffect(null, null, parameters, new StatSet())
			{
				@Override public EffectType getEffectType() { return type; }
			});
		}
		return result;
	}

	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
	private static void reject(Runnable action, String message)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
}
