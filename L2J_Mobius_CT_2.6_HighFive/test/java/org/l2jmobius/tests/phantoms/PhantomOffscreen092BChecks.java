/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.*;

/** The existing stock graph0 witness, with an explicit geometry fixture boundary. No native claim. */
public final class PhantomOffscreen092BChecks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length >= 2 && args.length <= 3 && args[0].equals("combat")) { combatCapability(Path.of(args[1]), args.length == 3 ? Path.of(args[2]) : null); return; }
		if (args.length == 1) { focused(Path.of(args[0])); return; }
		int exit = 1;
		try { nativeAction(args); exit = 0; }
		catch (Throwable failure)
		{
			System.out.println("092B_NATIVE_FAIL " + failure.getClass().getSimpleName());
			failure.printStackTrace(System.out);
			if (failure instanceof AssertionError || failure instanceof IllegalArgumentException) { System.out.println("092B_KEY " + failure.getMessage()); }
			for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.tests.phantoms.")) { System.out.println(frame); } }
		}
		finally
		{
			if (List.of("native", "continue", "verified", "restart", "final-live-authority-diagnostic").contains(args[0])) { org.l2jmobius.commons.threads.ThreadPool.shutdown(); }
			if (DatabaseFactory.isInitialized()) { DatabaseFactory.close(); }
		}
		System.exit(exit);
	}

	private static void combatCapability(Path module, Path baseline) throws Exception
	{
		final var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(module.resolve("dist/game/data/stats/skills/01200-01299.xml").toFile());
		final var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
		final var skill = (org.w3c.dom.Node) xpath.evaluate("//skill[@id='1245']", xml, javax.xml.xpath.XPathConstants.NODE);
		final var mechanics = new Mechanics(DamageKind.MAGIC, skillScalar(xpath, skill, "power", 14), (int) skillScalar(xpath, skill, "hitTime", 14), (int) skillScalar(xpath, skill, "reuseDelay", 14), false,
			Double.parseDouble(xpath.evaluate("effects/effect[@name='HpDrain']/power", skill)), (int) skillScalar(xpath, skill, "magicLevel", 14), (byte) -1, 0, 1);
		final double mp = skillScalar(xpath, skill, "mpConsume", 14) + skillScalar(xpath, skill, "mpInitialConsume", 14);
		final var nobleXml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(module.resolve("dist/game/data/stats/skills/00300-00399.xml").toFile());
		final var noble = (org.w3c.dom.Node) xpath.evaluate("//skill[@id='325']", nobleXml, javax.xml.xpath.XPathConstants.NODE);
		final var nativeScalars = PhantomNativeCombatCapability.class.getDeclaredMethod("nativeMechanics", boolean.class, double.class, int.class, int.class, boolean.class, double.class, int.class, byte.class, int.class);
		nativeScalars.setAccessible(true);
		PhantomAssertions.assertEquals(-1d, skillScalar(xpath, noble, "magicLevel", 1), "The real Noble325 source owns a negative native sentinel.");
		PhantomAssertions.assertEquals(null, nativeScalars.invoke(null, false, skillScalar(xpath, noble, "power", 1),
			(int) (skillScalar(xpath, noble, "hitTime", 1) + skillScalar(xpath, noble, "coolTime", 1)), (int) skillScalar(xpath, noble, "reuseDelay", 1), false, 0d, -1, (byte) -1, 0),
			"Unsupported native325 metadata must not abort the entire progression catalog.");
		PhantomAssertions.assertTrue(nativeScalars.invoke(null, false, 100d, 1000, 2000, false, 0d, 0, (byte) -1, 0) != null, "Physical magicLevel0 remains supported.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new Mechanics(DamageKind.PHYSICAL, 100, 1000, 2000, false, 0, -1, (byte) -1, 0, 1), "The strict simulation validator still rejects negative magicLevel.");
		PhantomAssertions.assertEquals(mechanics, nativeScalars.invoke(null, true, mechanics.power(), mechanics.castMillis(), mechanics.reuseMillis(), false, mechanics.drainRatio(), mechanics.magicLevel(), mechanics.element(), mechanics.elementPower()), "The native1245 MAGIC mechanics remain unchanged.");
		final var readiness = new Readiness(true, false, true, 3785, mp, 0, 0, 0, 0);
		final var learned = new Candidate(1245, 14, mechanics, readiness);
		final var selected = PhantomNativeCombatCapability.select(List.of(learned), List.of());
		PhantomAssertions.assertTrue(selected.isPresent(), "A learned supported native attack must survive an absent curated damage rule.");
		PhantomAssertions.assertEquals(14, selected.orElseThrow().skillLevel(), "Use the actual learned rank.");
		final var physical = new Candidate(3, 1, new Mechanics(DamageKind.PHYSICAL, 100, 1000, 2000, false, 0), readiness);
		for (var rejected : List.of(new Readiness(false, false, true, 3785, mp, 0, 0, 0, 0), new Readiness(true, true, true, 3785, mp, 0, 0, 0, 0),
			new Readiness(true, false, false, 3785, mp, 0, 0, 0, 0), new Readiness(true, false, true, 1, mp, 0, 0, 0, 0),
			new Readiness(true, false, true, 3785, mp, 1, 0, 0, 0), new Readiness(true, false, true, 3785, mp, 0, 1, 0, 0),
			new Readiness(true, false, true, 3785, mp, 0, 0, 1, 0), new Readiness(true, false, true, 3785, mp, 0, 0, 0, 1)))
		{
			final var result = PhantomNativeCombatCapability.select(List.of(new Candidate(3, 1, physical.mechanics(), rejected), learned), List.of(3));
			PhantomAssertions.assertEquals(1245, result.orElseThrow().skillId(), "A rejected preferred action must permit the next supported action.");
		}
		PhantomAssertions.assertEquals(physical, PhantomNativeCombatCapability.select(List.of(learned, physical), List.of(3)).orElseThrow(), "An eligible preferred physical action retains priority.");
		PhantomAssertions.assertTrue(PhantomNativeCombatCapability.select(java.util.Collections.nCopies(4097, learned), List.of()).isEmpty(), "Learned discovery has a hard bound.");
		PhantomAssertions.assertTrue(PhantomNativeCombatCapability.select(List.of(new Candidate(1, 1, null, readiness)), List.of()).isEmpty(), "Unsupported effects cannot become MAGIC.");
		PhantomAssertions.assertEquals(101, PhantomNativeCombatCapability.select(List.of(new Candidate(1245, 101, mechanics, readiness)), List.of()).orElseThrow().skillLevel(), "An actual enchanted rank cannot be downcast to its base rank.");
		PhantomAssertions.assertFalse(PhantomNativeCombatCapability.castingAllowed(false, true, true, false, true), "Native magic mute is a casting refusal.");
		PhantomAssertions.assertFalse(PhantomNativeCombatCapability.castingAllowed(false, false, false, true, true), "Native physical mute is a casting refusal.");
		PhantomAssertions.assertFalse(PhantomNativeCombatCapability.castingAllowed(false, true, false, false, false), "Weapon-only skill restrictions remain binding.");
		final var autoUse = new org.l2jmobius.gameserver.model.actor.holders.player.AutoUseSettingsHolder();
		final var preparationSelector = PhantomNativeCombatCapability.class.getDeclaredMethod("select", List.class, List.class, boolean.class);
		preparationSelector.setAccessible(true);
		final var reused = new Candidate(1245, 14, mechanics, new Readiness(true, true, true, 3785, mp, 0, 0, 0, 0));
		final var preparation = (java.util.Optional<?>) preparationSelector.invoke(null, List.of(reused, physical), List.of(1245), false);
		PhantomAssertions.assertEquals(physical, preparation.orElseThrow(), "AutoUse preparation must select the same ready fallback as capture before retaining a recovery intent.");
		PhantomNativeCombatCapability.alignAutoSkills(autoUse, List.of(254), 1245);
		autoUse.incrementSkillOrder();
		PhantomNativeCombatCapability.alignAutoSkills(autoUse, List.of(254), 1245);
		PhantomAssertions.assertEquals(1245, autoUse.getNextSkillId(), "Unchanged primary/profession intent must preserve the native cursor.");
		PhantomAssertions.assertEquals(List.of(254, 1245), autoUse.getAutoSkills(), "Native profession utility survives damage alignment.");
		PhantomNativeCombatCapability.alignAutoSkills(autoUse, List.of(254), 0);
		PhantomAssertions.assertEquals(List.of(254), autoUse.getAutoSkills(), "Loss of supported damage must clear the stale primary and retain utility.");

		final var witness = PhantomOffscreen092Checks.witness(module);
		final var saved = baseline == null ? PhantomOffscreen092Checks.state(witness.origin(), List.of()) : new PhantomBackgroundStateCodec().decode(Files.readAllBytes(baseline));
		final var combat = baseline == null ? new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MAGIC, 288.99, 509.86383175, 613.47, 103.2421, 394, 273, 6.407353333333333, 2.76991, 1, 1, 0, 1, 1, 1, 1) : saved.combat();
		final var vitals = baseline == null ? new PhantomBackgroundState.Vitals(3998, 3998, 3785, 3785, 3598, 3598) : saved.vitals();
		final var target = new PhantomBackgroundModel.Target(21513, 70, true, 3862, 100, 1312.704, 779.5826, 398.6925, 611.735328, 278.3, 333, 9866, 978, List.of(), 1);
		final var encounter = PhantomBackgroundModel.castEncounter(combat, vitals, mp, target, mechanics, List.of());
		final var oldRank = new Mechanics(DamageKind.MAGIC, 21, 3000, 0, false, 0, 14, (byte) -1, 0, 1);
		PhantomAssertions.assertFalse(PhantomNativeCombatCapability.magicLevelReady(oldRank, 80, 0), "An obsolete retained spell cannot starve discovery at high level.");
		PhantomAssertions.assertTrue(PhantomNativeCombatCapability.magicLevelReady(mechanics, 80, 0), "The actual learned drain remains preparable at level80.");
		final var weakHighTarget = new PhantomBackgroundModel.Target(1, 70, true, 1000, 100, 1, 1, 100, 100, 100, 100, 1, 1, List.of(), 1);
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, vitals, 1, weakHighTarget, oldRank, List.of()).viable(), "An old native magicLevel cannot be modeled as a guaranteed full-power hit against a high-level target.");
		final var resisted = new PhantomBackgroundModel.Target(target.npcId(), target.level(), true, target.maximumHp(), target.maximumMp(), target.physicalOffense(), target.magicOffense(), target.physicalDefense(), target.magicDefense(), target.attackSpeed(), target.castSpeed(), target.baseExperience(), target.baseSkillPoints(), List.of(), 1, target.vitalityMaximumHp(), new PhantomBackgroundModel.CastDefense(List.of(0, 0, 0, 0, 0, 0), 3));
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, vitals, mp, resisted, mechanics, List.of()).viable(), "Unmodeled native magic resistance cannot grant drain or rewards.");
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, vitals, mp, target, mechanics.scaled(0.1), List.of()).viable(), "A native damage reduction must reduce viability.");
		PhantomAssertions.assertTrue(encounter.viable(), "Paid native drain must admit this source-backed ordinary target without shots or buffs.");
		PhantomAssertions.assertEquals(11, encounter.casts(), "Full native power/cast cycle, no twenty-second compression.");
		PhantomAssertions.assertEquals(1947d, encounter.requiredMp(), "Every one of eleven casts must be reserved.");
		PhantomAssertions.assertTrue(encounter.restoredHp() <= target.maximumHp() * mechanics.drainRatio() && encounter.remainingHp() < vitals.currentHp(), "Drain is bounded by actual target HP and cannot create a net free heal here.");
		final var noDrain = new Mechanics(DamageKind.MAGIC, mechanics.power(), mechanics.castMillis(), mechanics.reuseMillis(), false, 0, mechanics.magicLevel(), mechanics.element(), mechanics.elementPower(), 1);
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, vitals, mp, target, noDrain, List.of()).viable(), "The same unpaid-heal-free encounter must still predict death.");
		final var weak = new PhantomBackgroundState.Vitals(100, vitals.maximumHp(), vitals.currentMp(), vitals.maximumMp(), 0, vitals.maximumCp());
		final var firstDeath = PhantomBackgroundModel.castEncounter(combat, weak, mp, target, mechanics, List.of());
		PhantomAssertions.assertTrue(!firstDeath.viable() && firstDeath.restoredHp() == 0, "Future drain cannot heal a caster killed before the first hit.");
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, new PhantomBackgroundState.Vitals(vitals.currentHp(), vitals.maximumHp(), mp, vitals.maximumMp(), 0, vitals.maximumCp()), mp, target, mechanics, List.of()).viable(), "One cast's MP cannot fund an encounter.");
		final var lethal = new PhantomBackgroundModel.Target(22196, 80, true, 5198, 100, 2163.876, 100, 1794.3077295, 513.3882, 278.3, 333, 100, 10, List.of(), 1);
		PhantomAssertions.assertFalse(PhantomBackgroundModel.castEncounter(combat, vitals, mp, lethal, mechanics, List.of(lethal, lethal)).viable(), "Additional native threats remain a refusal.");
		for (int level : List.of(1, 20, 40, 60, 80, 85))
		{
			final var small = new PhantomBackgroundModel.Target(1, level, true, 50, 50, 1, 1, 100, 100, 100, 100, 10, 1, List.of(), 1);
			for (var action : List.of(physical.mechanics(), new Mechanics(DamageKind.MAGIC, 108, 4000, 2000, false, 0, level, (byte) -1, 0, 1)))
			{ PhantomAssertions.assertTrue(PhantomBackgroundModel.castEncounter(combat, vitals, 1, small, action, List.of()).viable(), "Plain physical and magic mechanics are independent of class/level labels."); }
		}
		final var prepared = new PhantomBackgroundState(saved.state(), saved.identity(), new PhantomBackgroundState.Progress(80, saved.progress().experience(), saved.progress().skillPoints(), saved.progress().experienceBeforeDeath()), vitals, saved.position(),
			new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MAGIC, combat.physicalOffense(), combat.magicOffense(), combat.physicalDefense(), combat.magicDefense(), combat.attackSpeed(), combat.castSpeed(), 0, 0, 1, 1, 0, 1, 1, 1, 1),
			new PhantomBackgroundState.Loadout(1245, 14, 0, (int) mp, 0, 0, 0, 0), saved.inventory(), saved.autoGetSkills(), new PhantomBackgroundState.Clock(saved.clock().rngState(), 0, 0), saved.receipt(), saved.hashes());
		final var death = new PhantomBackgroundModel.DeathPolicy() { public double lossPercent(int level) { return 1; } public double normalMonsterReductionMultiplier() { return 1; } };
		final var levels = new PhantomBackgroundModel.ExperienceTable() { public long experienceForLevel(int level) { return level * 100L; } public int maximumLevel() { return 85; } };
		final var batch = new PhantomBackgroundModel().evaluate(new PhantomBackgroundModel.BatchRequest(prepared, target, new PhantomBackgroundModel.RewardPolicy(11, 1, 1), death, levels, experience -> 80, false, mechanics));
		PhantomAssertions.assertEquals(1, batch.encounters(), "The real cast duration permits just one encounter per minute.");
		PhantomAssertions.assertEquals(vitals.currentMp() - encounter.requiredMp(), batch.vitals().currentMp(), "The applied batch pays all casts, with no free MP recovery.");
		PhantomAssertions.assertEquals(encounter.remainingHp(), batch.vitals().currentHp(), "Batch and admission use the same chronological drain.");
		PhantomAssertions.assertTrue(batch.inventoryDelta().itemDeltas().isEmpty() && batch.elapsedMillis() <= 60_000, "No invented shots/items or unbounded elapsed work.");
		System.out.println("092B_COMBAT_REAL mode=MAGIC skill=1245/14 power=" + mechanics.power() + " mp/cast=" + mp + " target=21513 " + encounter + "; savedPayload=" + (baseline == null ? "FIXTURE" : "READ_ONLY_DECODE"));
		PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(), "Combat contracts cannot initialize JDBC.");
		System.out.println("PASS092B_COMBAT groups=3 learned-native-fallback/readiness; paid-cast/drain/physical-magic; batch-debits/bounds; DB=0; native=NOT_RUN");
	}

	private static double skillScalar(javax.xml.xpath.XPath xpath, org.w3c.dom.Node skill, String name, int rank) throws Exception
	{
		final String value = xpath.evaluate(name, skill).trim();
		return value.startsWith("#") ? Double.parseDouble(xpath.evaluate("table[@name='" + value + "']", skill).trim().split("\\s+")[rank - 1]) : Double.parseDouble(value);
	}

	private static void focused(Path module) throws Exception
	{
		final var witness = PhantomOffscreen092Checks.witness(module);
		var state = PhantomOffscreen092Checks.state(witness.origin(), List.of());
		state = state.after(state.progress(), state.vitals(), state.position(), state.inventory(), state.autoGetSkills(),
			new PhantomBackgroundState.Clock(state.clock().rngState(), 0, 0), PhantomBackgroundState.Receipt.empty());
		final var previous = PhantomOffscreen092Checks.goal(witness, 1, 1);
		final var planned = PhantomOffscreen092Checks.planner(witness, false, true).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(planned.ready(), "Graph0 must select the real stock outdoor target.");
		PhantomAssertions.assertEquals(PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING, planned.travelMode(), "Pending is a distinct typed intent.");
		PhantomAssertions.assertTrue(planned.routeEdgeIds().isEmpty(), "No synthetic certified edge may be inserted.");
		final var certified = PhantomOffscreen092Checks.planner(witness, true, true).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertEquals(PhantomOffscreenOutdoorTravel.Mode.CERTIFIED, certified.travelMode(), "An existing route keeps priority.");
		PhantomAssertions.assertEquals(List.of("fixture092.certified"), certified.routeEdgeIds(), "Certified route unchanged.");
		final var lowerCertified = PhantomOffscreen092Checks.planner(witness, false, true, true).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertEquals(999997, lowerCertified.spec().npcId(), "A pending tier2 candidate must not hide a certified tier5 candidate.");
		final var closedNodes = witness.nodes().stream().map(node -> node.id().equals(witness.destination().nodeId())
			? new PhantomTopologyNode(node.id(), node.kind(), node.instanceId(), node.area(), node.parentId(), List.of("closed"), node.sourceRefs()) : node).toList();
		PhantomAssertions.assertFalse(PhantomOffscreen092Checks.planner(new PhantomOffscreen092Checks.Witness(witness.origin(), witness.destination(), witness.npc(), closedNodes), false, true)
			.replaceFromState(92, state, previous, 920092, 1).ready(), "Closed/untagged farmland must not be a virtual target.");
		final var coveringNodes = witness.nodes().stream().map(node -> node.id().equals(witness.destination().nodeId())
			? new PhantomTopologyNode(node.id(), node.kind(), 0, PhantomTopologyArea.cuboid(0, 0, 200000, 0, 200000, -5000, 0), null, node.tags(), node.sourceRefs()) : node).toList();
		PhantomAssertions.assertFalse(PhantomOffscreen092Checks.planner(new PhantomOffscreen092Checks.Witness(witness.origin(), witness.destination(), witness.npc(), coveringNodes), false, true)
			.replaceFromState(92, state, previous, 920092, 1).ready(), "A farm already containing the source is not a remote offscreen option.");
		final var unverified = new PhantomTopologyAnchor("generated.farm.fixture.anchor", witness.destination().role(), "generated.farm.fixture",
			witness.destination().point(), witness.destination().npcId(), null, witness.destination().validationTolerance(), witness.destination().tags(), witness.destination().sourceRefs());
		final var generatedNodes = witness.nodes().stream().map(node -> node.id().equals(witness.destination().nodeId())
			? new PhantomTopologyNode(unverified.nodeId(), node.kind(), 0, node.area(), null, node.tags(), node.sourceRefs()) : node).toList();
		PhantomAssertions.assertFalse(PhantomOffscreen092Checks.planner(new PhantomOffscreen092Checks.Witness(witness.origin(), unverified, witness.npc(), generatedNodes), false, true)
			.replaceFromState(92, state, previous, 920092, 1).ready(), "An unverified generated outdoor tag cannot authorize closed-content entry.");
		final var audited = PhantomOffscreen092Checks.auditedRuneWitness(module);
		final var auditedPlan = PhantomOffscreen092Checks.planner(audited, false, true).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(auditedPlan.ready(), "An exactly audited public generated outdoor area must remain eligible on graph0.");
		PhantomAssertions.assertEquals(audited.destination().id(), auditedPlan.spec().anchorId(), "The audited source must retain its real anchor identity.");
		final var altered = new PhantomTopologyAnchor(audited.destination().id(), audited.destination().role(), audited.destination().nodeId(),
			new PhantomTopologyPoint(audited.destination().point().x() + 1, audited.destination().point().y(), audited.destination().point().z(), 0),
			null, audited.destination().mapRegionLocId(), 0, audited.destination().tags(), audited.destination().sourceRefs());
		PhantomAssertions.assertTrue(PhantomOffscreen092Checks.planner(new PhantomOffscreen092Checks.Witness(audited.origin(), altered, audited.npc(), audited.nodes(), audited.spawns()), false, true)
			.replaceFromState(92, state, previous, 920092, 1).ready(), "Stock polygon admission must not depend on one fixed XYZ.");
		stockAlternatives(module, state, previous, audited);
		final var policy = new PhantomBackgroundSimulationPolicy(2, 80, 1, 1, true, false, true, false, false,
			1, 1, 1, 1, 1, 1, 1, 0, 0, 100, 100, "a".repeat(64), 120, 60_000, 0);
		final var destination = new PhantomBackgroundState.Position(0, witness.destination().point().x(), witness.destination().point().y(),
			witness.destination().point().z(), 0, witness.destination().id());
		final var initial = state;
		int intervals = 0;
		for (; intervals < 100; intervals++)
		{
			final var key = key(state, planned.goal().goalId(), planned.goal().revision(), planned.planIdentity(), intervals);
			final var step = PhantomOffscreenOutdoorTravel.advance(state, destination, key, 60_000, policy);
			PhantomAssertions.assertTrue(step.mutated(), "An owned travel minute must progress.");
			PhantomAssertions.assertEquals(0L, step.feeAdena(), "Virtual travel cannot debit GK fees.");
			if (intervals == 0)
			{
				PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL, step.status(), "A distant farm cannot arrive in a single minute.");
				PhantomAssertions.assertEquals(state.position(), step.position(), "A partial minute keeps factual source XYZ.");
			}
			state = state.after(state.progress(), state.vitals(), step.position(), state.inventory(), state.autoGetSkills(), step.clock(),
				new PhantomBackgroundState.Receipt(key.digest(), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, "b".repeat(64)));
			if (step.status() == PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED) { intervals++; break; }
			final var next = key(state, planned.goal().goalId(), planned.goal().revision(), planned.planIdentity(), intervals + 1);
			PhantomAssertions.assertTrue(PhantomOffscreenOutdoorTravel.pendingDebt(state), "Partial travel carries a durable typed receipt marker.");
			PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.ownsResidual(state, key(state, planned.goal().goalId(), planned.goal().revision(), planned.planIdentity(), 0)), "A renewed request ordinal0 cannot inherit old debt; it must cancel/restart.");
			PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.advance(state, destination,
				key(state, planned.goal().goalId(), planned.goal().revision() + 1, planned.planIdentity(), intervals + 1), 60_000, policy).mutated(), "Replaced revision cannot inherit travel debt.");
			PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.advance(state, destination,
				key(state, planned.goal().goalId(), planned.goal().revision(), "c".repeat(64), intervals + 1), 60_000, policy).mutated(), "Another plan cannot inherit travel debt.");
			PhantomAssertions.assertEquals(state.receipt().operationKey(), PhantomOffscreenOutdoorTravel.predecessor(next).digest(), "Continuation requires exact predecessor receipt.");
		}
		PhantomAssertions.assertEquals(destination, state.position(), "Final due minute commits destination, not intermediate imaginary XYZ.");
		PhantomAssertions.assertEquals(initial.inventory(), state.inventory(), "Travel preserves item identities/counts.");
		PhantomAssertions.assertEquals(initial.progress(), state.progress(), "Travel has no rewards.");
		final var beforeSpawn = state.withState(PhantomBackgroundState.State.MATERIALIZED);
		PhantomAssertions.assertEquals(state.receipt(), PhantomOffscreenOutdoorTravel.nativeReceipt(beforeSpawn, false),
			"A MATERIALIZED mark before World spawn must not retire the observer fence during aborted-load cleanup.");
		PhantomAssertions.assertEquals(PhantomBackgroundState.Receipt.empty(), PhantomOffscreenOutdoorTravel.nativeReceipt(beforeSpawn, true),
			"A completed capture of the exact World Player may retire the offscreen fence.");
		final var foreign = PhantomOffscreen092Checks.state(witness.origin(), List.of());
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.advance(foreign, destination,
			key(foreign, 1, 1, "a".repeat(64), 0), 60_000, policy).mutated(), "Unowned legacy residual fails closed.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.advance(initial, destination,
			key(initial, 1, 1, "a".repeat(64), 0), 60_001, policy).mutated(), "Minute budget is bounded.");
		PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(), "Focused contracts cannot initialize JDBC.");
		System.out.println("PASS092B groups=3 graph0/certified; elapsed/arrival/residual-lineage; no-reward/bounds; intervals=" + intervals + "; DB=0; native=NOT_RUN");
	}

	private static PhantomBackgroundOperationKey key(PhantomBackgroundState state, long goal, long revision, String plan, long interval)
	{
		return new PhantomBackgroundOperationKey(92, 920092, goal, revision, 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK,
			PhantomBackgroundOperationKey.ActionKind.HISTORICAL_OFFSCREEN_TRAVEL, 22859, "giran.farming.22859",
			PhantomBackgroundState.MODEL_VERSION, state.hashes(), null,
			new PhantomBackgroundOperationKey.HistoricalIdentity("a".repeat(64), 1, interval, 100 + interval, 101 + interval, plan));
	}

	private static void stockAlternatives(Path module, PhantomBackgroundState state,
		org.l2jmobius.gameserver.phantoms.decision.PhantomGoal previous, PhantomOffscreen092Checks.Witness rune) throws Exception
	{
		final var stockSources = new java.util.ArrayList<PhantomOffscreen092Checks.Witness>();
		for (var sample : List.of(
			Map.entry("generated.farm.0c00c3760a3ec18fa034d798.anchor", 20525),
			Map.entry("generated.farm.013f818c1372afc740e3cbc2.anchor", 21130),
			Map.entry("generated.farm.0b52b3a7154b1b1513731338.anchor", 20797),
			Map.entry("generated.farm.054a33b59b94dcb5d5e8d43a.anchor", 21521),
			Map.entry("generated.farm.08a347f7f13696479813918f.anchor", 22202)))
		{
			final var stock = PhantomOffscreen092Checks.stockGeneratedWitness(module, "high-five-generated-01.xml", sample.getKey(), sample.getValue());
			stockSources.add(stock);
			final var progress = state.progress();
			final var atLevel = state.after(new PhantomBackgroundState.Progress(stock.npc().level(), progress.experience(), progress.skillPoints(), progress.experienceBeforeDeath()),
				state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
			final var result = PhantomOffscreen092Checks.planner(stock, false, true).replaceFromState(92, atLevel, previous, 920092, 1);
			PhantomAssertions.assertTrue(result.ready(), "Matching real stock geometry must not require a region or level whitelist.");
			System.out.println("092B_STOCK_SOURCE level=" + stock.npc().level() + " npc=" + stock.npc().npcId() + " anchor=" + result.spec().anchorId()
				+ "; stock/topology=PASS; native-zones/Geo/combat=NOT_RUN");
		}
		for (int level : List.of(5, 20, 40, 60, 80, 85))
		{
			final var p = state.progress();
			final var atLevel = state.after(new PhantomBackgroundState.Progress(level, p.experience(), p.skillPoints(), p.experienceBeforeDeath()),
				state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
			final var result = PhantomOffscreen092Checks.alternatives(stockSources, java.util.Set.of()).replaceFromState(92, atLevel, previous, 920092, 1);
			if (result.ready())
			{
				final int targetLevel = stockSources.stream().filter(source -> source.npc().npcId() == result.spec().npcId()).findFirst().orElseThrow().npc().level();
				PhantomAssertions.assertTrue(targetLevel >= Math.max(1, level - 10) && targetLevel <= level + 2, "A selected source must match this actor's level band.");
			}
			else { PhantomAssertions.assertEquals("planner.target_or_route.absent", result.reasonKey(), "Unavailable level bands must fail explicitly."); }
			System.out.println("092B_LEVEL level=" + level + " outcome=" + (result.ready() ? result.spec().npcId() : "NO_ELIGIBLE_TARGET") + "; fixture-native-combat=NOT_RUN");
		}
		final var level85 = state.after(new PhantomBackgroundState.Progress(85, 0, 0, 0), state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
		PhantomAssertions.assertFalse(PhantomOffscreen092Checks.planner(rune, false, true).replaceFromState(92, level85, previous, 920092, 1).ready(),
			"The real 72-level stock source must not become an 85-level whitelist exception.");
		final var bad = PhantomOffscreen092Checks.stockGeneratedWitness(module, "high-five-generated-01.xml", "generated.farm.386e6c4606d43aad870b4d6e.anchor", 18581);
		candidateBudget(state, previous, bad, rune);
		final Map<String, List<org.l2jmobius.gameserver.model.zone.ZoneType>> zones = Map.of(bad.destination().id(), List.of(new org.l2jmobius.gameserver.model.zone.type.NoRestartZone(920093)));
		final var alternatives = PhantomOffscreen092Checks.alternatives(List.of(bad, rune), java.util.Set.of(), zones);
		final var fallback = alternatives.replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(fallback.ready() && fallback.spec().npcId() == rune.npc().npcId(), "A rejected first stock candidate must permit the next valid candidate.");
		PhantomAssertions.assertTrue(fallback.candidateRejections().stream().anyMatch(reason -> reason.contains(bad.destination().id())), "Rejected candidate identity/reason must be retained.");
		final var exhausted = PhantomOffscreen092Checks.alternatives(List.of(bad, rune), java.util.Set.of(rune.npc().npcId()), zones).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertFalse(exhausted.ready(), "Unavailable combat alternatives must yield bounded refusal.");
		PhantomAssertions.assertTrue(exhausted.candidateRejections().size() <= 32, "Rejection evidence is bounded and scoped to this plan.");
		PhantomAssertions.assertTrue(alternatives.replaceFromState(92, state, previous, 920092, 2).ready(), "A prior refusal cannot poison the next plan or region.");
		final var reversed = new org.l2jmobius.gameserver.model.zone.form.ZoneCuboid(0, 100, 0, 100, 100, -100);
		PhantomAssertions.assertTrue(reversed.isInsideZone(50, 50, 0), "Ordinary native zones normalize reversed Z bounds.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.zoneRejection(List.of(new org.l2jmobius.gameserver.model.zone.type.ArenaZone(920092)), true).isEmpty(), "Event arena native semantics deny arrival.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.zoneRejection(List.of(new org.l2jmobius.gameserver.model.zone.type.NoRestartZone(920093)), false).isEmpty(), "Restricted actual source denies departure.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.zoneRejection(List.of(new org.l2jmobius.gameserver.model.zone.type.WaterZone(920094)), true).isEmpty(), "Garden/underwater geometry alone never grants outdoor permission.");
		PhantomAssertions.assertTrue(PhantomOffscreenOutdoorTravel.zoneRejection(List.of(new org.l2jmobius.gameserver.model.zone.type.FishingZone(920095)), true).isEmpty(), "Stock fishing catchment has empty enter/exit handlers and does not restrict ordinary land movement.");
		final var generation = new java.util.concurrent.atomic.AtomicLong(1);
		final var proof = new PhantomBackgroundTransaction.OffscreenProof((id, source, target) -> { throw new AssertionError("Generation check cannot evaluate observers."); },
			PhantomOffscreen092Checks.generationAuthority(generation), 1, 60_000, state.position());
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenProofCurrent(proof), "Proof pins a ready zone generation.");
		generation.set(-1);
		PhantomAssertions.assertFalse(PhantomBackgroundTransaction.offscreenProofCurrent(proof), "Reload/incomplete load invalidates a prepared commit proof.");
		generation.set(2);
		PhantomAssertions.assertFalse(PhantomBackgroundTransaction.offscreenProofCurrent(proof), "Completed reload cannot revive a stale generation proof.");
		System.out.println("PASS092B stock-level-bands=5; rejected-first/next/exhaustion/diagnostics; restricted-native-zone-types; full-native=NOT_RUN");
	}

	private static void candidateBudget(PhantomBackgroundState state, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal previous,
		PhantomOffscreen092Checks.Witness room, PhantomOffscreen092Checks.Witness outdoor)
	{
		// Synthetic multiplicity, not native geometry: zero rewards match the stock
		// event-template failure. 64 source areas with four anchors consume 256 slots.
		final var sources = new java.util.ArrayList<PhantomOffscreen092Checks.Witness>();
		final var nativeDenials = new java.util.HashMap<String, List<org.l2jmobius.gameserver.model.zone.ZoneType>>();
		final var zeroReward = new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact(999991, 80,
			org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER, true, true, false, 0, 0,
			org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		final var nativeZone = new org.l2jmobius.gameserver.model.zone.type.NoRestartZone(920099);
		for (int copy = 0; copy < 4; copy++)
		{
			for (int area = 0; area < 64; area++)
			{
				final String nodeId = "fixture092b.budget." + area;
				final var original = room.nodes().getLast();
				final var node = new PhantomTopologyNode(nodeId, original.kind(), 0, original.area(), null, List.of("outdoor-farming"), original.sourceRefs());
				final var anchor = new PhantomTopologyAnchor(nodeId + "." + copy, room.destination().role(), nodeId, room.destination().point(),
					null, null, room.destination().validationTolerance(), room.destination().tags(), room.destination().sourceRefs());
				sources.add(new PhantomOffscreen092Checks.Witness(room.origin(), anchor, zeroReward, List.of(room.nodes().getFirst(), node)));
				nativeDenials.put(anchor.id(), List.of(nativeZone));
			}
		}
		sources.add(outdoor);
		final var cheap = PhantomOffscreen092Checks.alternatives(sources, java.util.Set.of()).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(cheap.ready() && cheap.spec().anchorId().equals(outdoor.destination().id()),
			"256 cheap zero-reward rejections must not starve the next real lower-tier outdoor source.");
		final var rewarded = new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact(zeroReward.npcId(), 80, zeroReward.kind(),
			true, true, false, 1000, 100, zeroReward.authority());
		final var viableSources = sources.stream().map(value -> value.npc().npcId() == zeroReward.npcId()
			? new PhantomOffscreen092Checks.Witness(value.origin(), value.destination(), rewarded, value.nodes(), value.spawns()) : value).toList();
		final var combat = PhantomOffscreen092Checks.alternatives(viableSources, java.util.Set.of(rewarded.npcId()), nativeDenials)
			.replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(combat.ready(), "Combat-ineligible candidates must not spend native proof attempts.");
		final var bounded = PhantomOffscreen092Checks.alternatives(viableSources, java.util.Set.of(), nativeDenials)
			.replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertFalse(bounded.ready(), "256 actual native proof denials must still bound the search.");
		final var diagnosed = PhantomOffscreen092Checks.alternatives(sources, java.util.Set.of(),
			Map.of(outdoor.destination().id(), List.of(nativeZone))).replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(diagnosed.candidateRejections().stream().anyMatch(reason -> reason.contains(outdoor.destination().id() + ":native.zone.NoRestartZone")),
			"Cheap rejection noise must not hide the first actual native proof denial.");
		System.out.println("PASS092B candidate-budget cheap/combat-rejection-fairness; native-attempt-cap=256; synthetic-multiplicity=true");
	}

	private static void nativeAction(String[] args) throws Exception
	{
		PhantomTask091Lane.require(Boolean.getBoolean("phantom.task092b.native") && args.length == 2, "092B_EXPLICIT");
		final String launchName = args[0];
		final boolean authorityDiagnostic = launchName.equals("final-live-authority-diagnostic");
		final boolean nativeBinding = List.of("binding-a5", "binding-restart", "binding-correction-a5", "binding-correction-restart").contains(launchName);
		final boolean productA5 = nativeBinding || List.of("a5-proof", "a5-restart").contains(launchName);
		PhantomTask091Lane.require(!Boolean.getBoolean("phantom.task092b.safeCleanupOverlay"), "092B_NO_EXTERNAL_DIAGNOSTIC_OVERRIDE");
		if (authorityDiagnostic || productA5) { System.setProperty("phantom.task092b.safeCleanupOverlay", "true"); }
		if (productA5)
		{
			args[0] = List.of("a5-proof", "binding-a5", "binding-correction-a5").contains(launchName) ? "verified" : "restart";
			for (Class<?> type : List.of(L2jPhantomBackgroundAuthority.class, PhantomHistoricalBackgroundPlanner.class, PhantomBackgroundTransaction.class, PhantomOffscreenOutdoorTravel.class, org.l2jmobius.gameserver.managers.ZoneManager.class,
				PhantomNativeCombatCapability.class, PhantomBackgroundModel.class, PhantomVisibleAutoPlay.class, org.l2jmobius.gameserver.model.effects.AbstractEffect.class))
			{
				final Path actual = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
				System.out.println("092B_PRODUCTION_LOCATION " + type.getName() + "=" + actual);
				PhantomTask091Lane.require(actual.equals(Path.of(System.getProperty("phantom.task092b.expectedProduction"))), "092B_EXACT_PRODUCTION_CLASSES");
			}
			for (Class<?> type : List.of(PhantomOffscreen092BChecks.class, PhantomTask091Lane.class, PhantomHeadlessPlayerTestEnvironment.class, PhantomBackgroundSuite.class))
			{
				final Path actual = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
				System.out.println("092B_TEST_LOCATION " + type.getName() + "=" + actual);
				PhantomTask091Lane.require(actual.equals(Path.of(System.getProperty("phantom.task092b.expectedTests"))), "092B_EXACT_TEST_CLASSES");
			}
		}
		final boolean secondResume = List.of("resume2-preflight", "verified2", "restart2", "connections2").contains(launchName);
		final boolean finalClosure = List.of("final-preflight", "final-travel", "final-restart", "final-connections").contains(launchName);
		if (finalClosure) { System.out.println("092B_PRODUCTION_LOCATION " + PhantomHistoricalBackgroundPlanner.class.getProtectionDomain().getCodeSource().getLocation()); }
		if (secondResume || finalClosure)
		{
			args[0] = switch (launchName)
			{
				case "resume2-preflight", "final-preflight" -> "resume-preflight";
				case "verified2", "final-travel" -> "verified";
				case "restart2", "final-restart" -> "restart";
				default -> "connections";
			};
		}
		final Path module = Path.of(System.getProperty("phantom.module.root"));
		final Path config = module.resolve("dist/game/config/Database.ini");
		if (args[0].equals("configure"))
		{
			final var ownerPath = PhantomTask091Lane.root().resolve("private/owner.properties");
			final var owner = PhantomTask091Lane.read(ownerPath);
			PhantomTask091Lane.ownerFields(owner, module, System.getProperty("phantom.task092b.uuid"));
			final var settings = PhantomTask091Lane.read(Path.of(args[1]).resolve("dist/game/config/Database.ini"));
			PhantomTask091Lane.require(!Files.exists(config) && settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/[^\\r\\n]+"), "092B_CONFIG_INPUT");
			owner.setProperty("loginSha256", PhantomTask091Lane.hashText(settings.getProperty("Login")));
			settings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + PhantomTask091Lane.SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
			settings.setProperty("Driver", "com.mysql.cj.jdbc.Driver"); settings.setProperty("MaximumDatabaseConnections", "4");
			settings.setProperty("TestDatabaseConnections", "false"); settings.setProperty("BackupDatabase", "false");
			try (var writer = Files.newBufferedWriter(config, StandardOpenOption.CREATE_NEW)) { settings.store(writer, "Private own TEST092B"); }
			try (var writer = Files.newBufferedWriter(ownerPath)) { owner.store(writer, "Own TEST092B"); }
			PhantomTask091Lane.validate(module, config);
			System.out.println("092B_CONFIGURED private=true SQL=0"); return;
		}
		PhantomTask091Lane.validate(module, config);
		if (authorityDiagnostic)
		{
			final Path root = PhantomTask091Lane.root();
			final var saved = new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve("corrected-baseline.bin")));
			PhantomBackgroundState stored;
			try (var connection = PhantomTask091Lane.connect(PhantomTask091Lane.read(config), false))
			{
				connection.setReadOnly(true);
				PhantomTask091Lane.owned(connection, PhantomTask091Lane.read(root.resolve("private/owner.properties")));
				PhantomTask091Lane.quiescent(connection);
				try (var query = connection.prepareStatement("SELECT profile_id,character_object_id FROM phantom_profiles"); var rows = query.executeQuery())
				{ PhantomTask091Lane.require(rows.next() && rows.getLong(1) == saved.identity().profileId() && rows.getInt(2) == saved.identity().characterObjectId() && !rows.next(), "092B_DIAGNOSTIC_EXACT_ACTOR"); }
				try (var query = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE profile_id=? AND component_type='background.state'"))
				{
					query.setLong(1, saved.identity().profileId());
					try (var rows = query.executeQuery())
					{
						PhantomTask091Lane.require(rows.next(), "092B_DIAGNOSTIC_STATE");
						stored = new PhantomBackgroundStateCodec().decode(rows.getBytes(1));
						PhantomTask091Lane.require(!rows.next() && stored.equals(saved), "092B_DIAGNOSTIC_BASELINE");
					}
				}
				try (var query = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM characters WHERE online<>0)+(SELECT COUNT(*) FROM character_instance_time)+(SELECT COUNT(*) FROM character_skills_save)" ); var rows = query.executeQuery())
				{ PhantomTask091Lane.require(rows.next() && rows.getLong(1) == 0, "092B_DIAGNOSTIC_TIMESTAMP_DATA_RISK"); }
			}
			Files.writeString(root.resolve(launchName + ".started"), "One authorized authority diagnostic; no campaign", StandardOpenOption.CREATE_NEW);
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(920092, module, root.resolve("reports")));
			PhantomTask091Lane.require(!org.l2jmobius.gameserver.config.IdManagerConfig.DATABASE_CLEAN_UP, "092B_DIAGNOSTIC_CLEANUP_DISABLED");
			try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				final var liveHashes = fixture.authority().hashes();
				recordCompositeGuard(root.resolve("private/final-live-authority-diagnostic/guard-diagnostic.properties"), saved, stored, liveHashes, fixture, "AFTER_PRODUCTION_AUTHORITY_FIXTURE");
				final var travel = fixture.authority().travelQuery(fixture.topology());
				System.out.println("092B_GUARD_DIAGNOSTIC catalog.travel.hash=" + travel.hash());
				System.out.println("092B_GUARD_DIAGNOSTIC catalog.travel.legs=" + travel.legs().size());
				System.out.println("092B_GUARD_DIAGNOSTIC fixture.explicitTravel=false");
				System.out.println("092B_GUARD_DIAGNOSTIC loot.auto=" + org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT + ";herbs=" + org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_HERBS + ";slots=" + org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_SLOT_LIMIT + ";itemIds=" + org.l2jmobius.gameserver.config.PlayerConfig.AUTO_LOOT_ITEM_IDS.stream().sorted().toList());
				final var goal = new PhantomGoalStateStore(PhantomProfileRepository.open()).load(saved.identity().profileId()).orElseThrow().goal();
				System.out.println("092B_GUARD_DIAGNOSTIC goal.id=" + goal.goalId() + ";revision=" + goal.revision());
				System.out.println("092B_AUTHORITY_DIAGNOSTIC_STOP planner=NOT_INVOKED travel=NOT_EXECUTED rewards=0 restart=NOT_RUN");
			}
			return;
		}
		if (args[0].equals("resume-preflight"))
		{
			final var root = PhantomTask091Lane.root();
			final var saved = new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve("corrected-baseline.bin")));
			PhantomTask091Lane.require(Files.isRegularFile(root.resolve("bootstrap.complete"))
				&& !Files.exists(root.resolve(finalClosure ? "final-travel.started" : secondResume ? "verified2.started" : "verified.started"))
				&& !Files.exists(root.resolve(finalClosure ? "final-restart.started" : secondResume ? "restart2.started" : "restart.started"))
				&& (!secondResume || Files.isRegularFile(root.resolve("verified.started")) && Files.isRegularFile(root.resolve("verified.process.json")))
				&& (!finalClosure || Files.isRegularFile(root.resolve("verified2.process.json")) && !Files.exists(root.resolve("arrival.bin")))
				&& Long.parseLong(Files.readString(root.resolve("profile.txt")).strip()) == saved.identity().profileId(), "092B_RESUME_ONCE");
			try (var connection = PhantomTask091Lane.connect(PhantomTask091Lane.read(config), false))
			{
				connection.setReadOnly(true);
				PhantomTask091Lane.owned(connection, PhantomTask091Lane.read(root.resolve("private/owner.properties")));
				PhantomTask091Lane.quiescent(connection);
				try (var statement = connection.prepareStatement("SELECT profile_id,character_object_id FROM phantom_profiles"); var rows = statement.executeQuery())
				{
					PhantomTask091Lane.require(rows.next() && rows.getLong(1) == saved.identity().profileId()
						&& rows.getInt(2) == saved.identity().characterObjectId() && !rows.next(), "092B_RESUME_EXACT_ACTOR");
				}
				try (var statement = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
				{
					statement.setLong(1, saved.identity().profileId()); statement.setString(2, PhantomBackgroundState.COMPONENT_TYPE);
					try (var rows = statement.executeQuery())
					{ PhantomTask091Lane.require(rows.next() && saved.equals(new PhantomBackgroundStateCodec().decode(rows.getBytes(1))) && !rows.next(), "092B_RESUME_BASELINE_UNCHANGED"); }
				}
			}
			System.out.println("092B_RESUME_PREFLIGHT_PASS sameOwnedDB=true sameActor=true baselineUnchanged=true foreignConnections=0 DDL=0 DML=0");
			return;
		}
		if (List.of("bootstrap", "connections").contains(args[0])) { PhantomTask091Lane.environment(module, config, args[0]); return; }
		PhantomTask091Lane.require(List.of("native", "continue", "verified", "restart").contains(args[0]), "092B_MODE");
		final Path root = PhantomTask091Lane.root();
		final String baselineFile = nativeBinding ? "native-binding-baseline.bin" : "corrected-baseline.bin";
		Files.writeString(root.resolve(launchName + ".started"), "One own campaign; no reseed", StandardOpenOption.CREATE_NEW);
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(920092, module, root.resolve("reports")));
		if (productA5) { PhantomTask091Lane.require(!org.l2jmobius.gameserver.config.IdManagerConfig.DATABASE_CLEAN_UP, "092B_A5_CLEANUP_DISABLED"); }
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			if (productA5)
			{
				final var saved = new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve(args[0].equals("restart") ? "arrival.bin" : "corrected-baseline.bin")));
				final var stored = readStoredState(saved.identity().profileId());
				recordCompositeGuard(root.resolve(launchName + ".after-fixture.guard.properties"), saved, stored, fixture.authority().hashes(), fixture, "AFTER_PRODUCTION_AUTHORITY_FIXTURE");
			}
			final var profiles = PhantomProfileRepository.open();
			final var goals = new PhantomGoalStateStore(profiles);
			final var uncertain = new java.util.concurrent.atomic.AtomicBoolean();
			final var transactions = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, new PhantomBackgroundTransaction.ObjectIdAllocator()
			{
				public int reserve() { throw new AssertionError("Virtual travel cannot allocate items."); }
				public void release(int id) { throw new AssertionError("Virtual travel cannot release items."); }
			}, point -> { if (point == PhantomBackgroundTransaction.FaultPoint.AFTER_OPERATION_COMMIT && uncertain.getAndSet(false)) { throw new IllegalStateException("092B_COMMIT_REPLY_LOST"); } });
			final var metrics = new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
			final var bridge = new PhantomMaterializationLifecycleBridge();
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
				new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false, 64, 16, metrics), 1, bridge);
			final var signals = new PhantomRelevanceSignalPort()
			{
				public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions,
				fixture.authority(), new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
			final var topology = PhantomTopologyService.fromSnapshotForTesting(fixture.topology().snapshot(), fixture.topologyBackend(), PhantomTopologyPolicy.productionDefaults(), signals);
			PhantomTask091Lane.require(topology.start() && materialization.start() && background.start(), "092B_OWNERS_START");
			bridge.install(background);
			final var publisher = new PhantomTopologyPositionPublisher(topology, background::acquisitionSnapshot);
			background.installCommittedPositionPublisher(publisher::committed);
			final var retention = new PhantomMaterializationRetentionPolicy(id -> new PhantomMaterializationRetentionPolicy.Facts(false, false, false, false, false), System::nanoTime, 60_000);
			final var observers = new PhantomOffscreenObserverAdmission(retention, System::nanoTime);
			final var lastObserver = new java.util.concurrent.atomic.AtomicReference<PhantomOffscreenObserverAdmission.Verdict>();
			final var observerCalls = new java.util.concurrent.atomic.AtomicInteger();
			// Documented DI boundary: actual published topology + full live World census +
			// actual QA point and real 60s startup grace; no fixture ALLOW shortcut.
			final PhantomOffscreenOutdoorTravel.ObserverAdmission admission = (id, from, to) ->
			{
				final var source = new PhantomTopologyPoint(from.x(), from.y(), from.z(), from.instanceId());
				final var destination = new PhantomTopologyPoint(to.x(), to.y(), to.z(), to.instanceId());
				final var verdict = !topology.findProfile(id).map(value -> source.equals(value.point())).orElse(false)
					? new PhantomOffscreenObserverAdmission.Verdict(PhantomOffscreenObserverAdmission.Code.UNKNOWN_PROFILE, id, source, destination, 0, System.nanoTime(), 0)
					: observers.evaluate(id, source, destination, 0,
					() -> () -> World.getInstance().getPlayers().stream().filter(player -> player.isOnline() && !player.hasHeadlessOutboundSession())
						.map(player -> new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId())).iterator(),
					() -> org.l2jmobius.gameserver.localplay.LocalPlayPilotService.getInstance().autonomousObservationPoint());
				lastObserver.set(verdict);
				if (observerCalls.incrementAndGet() <= 12 || verdict.code() != PhantomOffscreenObserverAdmission.Code.ALLOW)
				{ System.out.println("092B_OBSERVER " + verdict); }
				return verdict;
			};
			background.installOffscreenObserverAdmission(admission);
			long profile;
			if (args[0].equals("native"))
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement())
				{ statement.executeUpdate("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task092bnative',NULL,0,1)"); }
				final var source = fixture.topology().findAnchor("elven.farming.oren04-2019-02s").orElseThrow();
				final var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(source, 0).orElseThrow();
				final Player player = Player.create(org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(115), "task092bnative", "Trip092bOrc",
					new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				PhantomTask091Lane.require(player != null && !player.isGM(), "092B_FIXTURE");
				try
				{
					player.stopAllTasks(); player.setXYZInvisible(position.x(), position.y(), position.z());
					player.setPlayerClass(115); player.setBaseClass(115); player.getStat().setExp(player.getStat().getExpForLevel(80)); player.getStat().setLevel((byte) 80);
					player.giveAvailableAutoGetSkills(); player.giveAvailableSkills(false, true, false); player.rewardSkills();
					for (int item : new int[] {6579, 6383, 6384, 6385, 6386}) { player.getInventory().equipItem(player.addItem(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD, item, 1, player, false)); }
					player.addAdena(org.l2jmobius.gameserver.model.item.enums.ItemProcessType.REWARD, 200000, player, false);
					player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setRunning();
					player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp());
					player.getInventory().updateDatabase(); player.storeMe(); profile = profiles.create(player.getObjectId()).profileId();
					final var plan = new PhantomHistoricalBackgroundPlanner(fixture.knowledge(), fixture.topology(), fixture.authority()).planInitial(profile, player, 920092, 0);
					PhantomTask091Lane.require(plan.ready() && plan.travelMode() == PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING, "092B_GRAPH0_PLAN");
					goals.insert(profile, plan.goal());
					final var capture = fixture.authority().captureOwnedNative(profile, player, plan.goal(), null);
					final var stored = transactions.captureBaseline(capture.state(), plan.goal(), capture.context());
					PhantomTask091Lane.require(stored.status() == PhantomBackgroundTransaction.Status.SUCCESS, "092B_BASELINE_" + stored.status());
					Files.write(root.resolve("baseline.bin"), new PhantomBackgroundStateCodec().encode(stored.state()), StandardOpenOption.CREATE_NEW);
					Files.writeString(root.resolve("profile.txt"), Long.toString(profile), StandardOpenOption.CREATE_NEW);
					final long from = System.currentTimeMillis() / 60_000 - 120;
					new PhantomBackgroundCatchupStore(profiles, goals).claim(profile, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING,
						"d".repeat(64), 920092, from, from + 1, from, 0, 0, 1, plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration(),
						plan.goal().goalId(), plan.goal().revision(), plan.planIdentity(), PhantomBackgroundState.MODEL_VERSION, stored.state().hashes(), ""));
					System.out.println("092B_FIXTURE profile=" + profile + " object=" + player.getObjectId() + " source=" + stored.state().position() + " target=" + plan.spec().anchorId() + " nativeSpeed=" + capture.context().policy().runSpeed());
				}
				finally { player.stopAllTasks(); player.deleteMe(); }
			}
			else
			{
				profile = Long.parseLong(Files.readString(root.resolve("profile.txt")).strip());
				if (args[0].equals("continue"))
				{
					final var original = new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve("baseline.bin")));
					PhantomTask091Lane.require(original.equals(background.acquisitionSnapshot(profile).orElseThrow()), "092B_UNCHANGED_FAILED_BASELINE");
					PhantomTask091Lane.require(materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "092B_NATIVE_BASELINE_LOAD");
					try (var action = materialization.tryAcquireAction(profile).orElseThrow())
					{
						final var storedGoal = goals.load(profile).orElseThrow();
						final var sourceCapture = fixture.authority().captureOwnedNative(profile, action.player(), storedGoal.goal(), null);
						final var source = sourceCapture.state();
						final var plan = new PhantomHistoricalBackgroundPlanner(fixture.knowledge(), fixture.topology(), fixture.authority()).replan(profile, source, storedGoal.goal(), 920092, 1);
						PhantomTask091Lane.require(plan.ready() && plan.travelMode() == PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING, "092B_CORRECTED_GRAPH0");
						final var target = fixture.topology().findAnchor(plan.spec().anchorId()).orElseThrow().point();
						PhantomTask091Lane.require(target.distanceSquared2D(new PhantomTopologyPoint(source.position().x(), source.position().y(), source.position().z(), 0)) > 100_000_000L, "092B_REMOTE_TARGET");
						System.out.println("092B_REMOTE_PLAN source=" + source.position() + " target=" + target + " anchor=" + plan.spec().anchorId() + " nativeSpeed=" + sourceCapture.context().policy().runSpeed());
						final var store = new PhantomBackgroundCatchupStore(profiles, goals);
						final var history = store.load(profile).orElseThrow();
						store.replacePlan(profile, history, history.state().withPlan(plan.goal().goalId(), plan.goal().revision(), 1, plan.planIdentity(), plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration()), storedGoal, plan.goal());
					}
					PhantomTask091Lane.require(materialization.dematerialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "092B_BASELINE_NATIVE_STORE");
					final var corrected = background.acquisitionSnapshot(profile).orElseThrow();
					PhantomTask091Lane.require(corrected.identity().equals(original.identity()) && corrected.progress().equals(original.progress())
						&& corrected.inventory().equals(original.inventory()) && corrected.position().x() == original.position().x()
						&& corrected.position().y() == original.position().y() && corrected.position().z() == original.position().z(), "092B_NO_RESEED_OR_AWARD");
					Files.write(root.resolve("corrected-baseline.bin"), new PhantomBackgroundStateCodec().encode(corrected), StandardOpenOption.CREATE_NEW);
					System.out.println("092B_BASELINE_REPAIRED same actor/XYZ/level/inventory; anchor=" + corrected.position().committedAnchorId());
				}
			}
			publisher.ready(profile);
			if (nativeBinding && args[0].equals("verified"))
			{
				final var original = background.acquisitionSnapshot(profile).orElseThrow();
				final var currentGoal = goals.load(profile).orElseThrow();
				recordNativeFacts(root.resolve(launchName + ".before-recapture.properties"), original, currentGoal.goal(), "NOT_INVOKED");
				PhantomTask091Lane.require(profile == 1 && original.identity().characterObjectId() == 268480990
					&& original.equals(new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve("corrected-baseline.bin"))))
					&& !Files.exists(root.resolve(baselineFile)), "092B_BINDING_EXACT_BASELINE");
				final var history = new PhantomBackgroundCatchupStore(profiles, goals).load(profile).orElseThrow();
				final var loaded = materialization.materialize(profile, PhantomMaterializationService.MaterializationPurpose.HISTORICAL_BASELINE, history.state().requestId());
				System.out.println("092B_BINDING_LOAD " + loaded);
				PhantomTask091Lane.require(loaded.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "092B_BINDING_NATIVE_LOAD");
				try (var action = materialization.tryAcquireAction(profile).orElseThrow())
				{
					final Player player = action.player();
					final var learned = player.getKnownSkill(1245);
					PhantomTask091Lane.require(player.getActiveClass() == 115 && player.getLevel() == 80 && learned != null && learned.getLevel() == 14, "092B_BINDING_LEARNED_RANK");
					final var selected = PhantomNativeCombatCapability.offensiveSkill(player, null);
					System.out.println("092B_BINDING_NATIVE class=" + player.getActiveClass() + " level=" + player.getLevel()
						+ " learned=1245/" + learned.getLevel() + " selected=" + (selected == null ? "NONE" : selected.getId() + "/" + selected.getLevel())
						+ " HP=" + player.getCurrentHp() + "/" + player.getMaxHp() + " MP=" + player.getCurrentMp() + "/" + player.getMaxMp()
						+ " initialMp=" + player.getStat().getMpInitialConsume(learned) + " castMp=" + player.getStat().getMpConsume(learned)
						+ " mechanics=" + PhantomNativeCombatCapability.mechanics(learned)
						+ " equipment=" + player.getInventory().getPaperdollItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getEnchantLevel()).toList());
					PhantomTask091Lane.require(selected != null && selected.getId() == 1245 && selected.getLevel() == 14, "092B_BINDING_NATIVE_SELECTOR");
					// Exercise the existing foreground configuration without starting any combat pools.
					final var configure = PhantomVisibleAutoPlay.class.getDeclaredMethod("configure", Player.class);
					configure.setAccessible(true); configure.invoke(null, player);
					final var foreground = PhantomNativeCombatCapability.offensiveSkill(player, null);
					final var capture = fixture.authority().captureOwnedNative(profile, player, currentGoal.goal(), original);
					System.out.println("092B_BINDING_CAPTURE AutoUse=" + player.getAutoUseSettings().getAutoSkills() + " state=" + capture.state() + " policy=" + capture.context().policy());
					PhantomTask091Lane.require(foreground == selected && player.getAutoUseSettings().getAutoSkills().contains(selected.getId())
						&& capture.state().combat().modelKind() == PhantomBackgroundState.ModelKind.MAGIC
						&& capture.state().loadout().selectedSkillId() == selected.getId() && capture.state().loadout().selectedSkillLevel() == selected.getLevel(), "092B_BINDING_FOREGROUND_BACKGROUND");
				}
				final var stored = materialization.dematerialize(profile);
				System.out.println("092B_BINDING_STORE " + stored);
				PhantomTask091Lane.require(stored.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "092B_BINDING_CANONICAL_STORE");
				final var recaptured = background.acquisitionSnapshot(profile).orElseThrow();
				recordNativeFacts(root.resolve(launchName + ".after-recapture.properties"), recaptured, goals.load(profile).orElseThrow().goal(), "NOT_INVOKED");
				PhantomTask091Lane.require(recaptured.state() == PhantomBackgroundState.State.READY && recaptured.combat().modelKind() == PhantomBackgroundState.ModelKind.MAGIC
					&& recaptured.loadout().selectedSkillId() == 1245 && recaptured.loadout().selectedSkillLevel() == 14
					&& recaptured.hashes().equals(fixture.authority().hashes()) && recaptured.identity().equals(original.identity())
					&& recaptured.progress().equals(original.progress()) && recaptured.inventory().equals(original.inventory()) && recaptured.position().equals(original.position())
					&& goals.load(profile).orElseThrow().equals(currentGoal) && World.getInstance().findObject(268480990) == null, "092B_BINDING_CANONICAL_INVARIANTS");
				Files.write(root.resolve(baselineFile), new PhantomBackgroundStateCodec().encode(recaptured), StandardOpenOption.CREATE_NEW);
				System.out.println("092B_GATE1_PASS canonicalRecapture=true mode=MAGIC learned=1245/14 nativeCast=NOT_RUN oldBaselineUnchanged=true");
				publisher.ready(profile);
			}
			if (args[0].equals("verified"))
			{
				// One separately authorised proof: retain the same actor and baseline, and
				// let the real planner use final STORED facts rather than pre-store stats.
				final var stored = background.acquisitionSnapshot(profile).orElseThrow();
				final var saved = new PhantomBackgroundStateCodec().decode(Files.readAllBytes(root.resolve(baselineFile)));
				final var liveHashes = fixture.authority().hashes();
				recordCompositeGuard(root.resolve(launchName + ".guard-diagnostic.properties"), saved, stored, liveHashes, fixture);
				PhantomTask091Lane.require(stored.equals(saved) && stored.hashes().equals(fixture.authority().hashes()), "092B_VERIFIED_SOURCE_HASHES");
				final var currentGoal = goals.load(profile).orElseThrow();
				recordNativeFacts(root.resolve(launchName + ".before-planning.properties"), stored, currentGoal.goal(), "NOT_INVOKED");
				final var store = new PhantomBackgroundCatchupStore(profiles, goals);
				final var history = store.load(profile).orElseThrow();
				final long ordinal = Math.addExact(history.state().planOrdinal(), 1);
				final var plan = new PhantomHistoricalBackgroundPlanner(fixture.knowledge(), fixture.topology(), fixture.authority()).replaceFromState(profile, stored, currentGoal.goal(), 920092, ordinal);
				System.out.println("092B_PUBLIC_PLAN ready=" + plan.ready() + " reason=" + plan.reasonKey()
					+ " mode=" + plan.travelMode() + " generation=" + plan.generation() + " rejections=" + plan.candidateRejections());
				PhantomTask091Lane.require(plan.ready() && plan.travelMode() == PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING, "092B_VERIFIED_PUBLIC_PLAN");
				final var target = fixture.topology().findAnchor(plan.spec().anchorId()).orElseThrow().point();
				PhantomTask091Lane.require(target.distanceSquared2D(new PhantomTopologyPoint(stored.position().x(), stored.position().y(), stored.position().z(), 0)) > 100_000_000L, "092B_VERIFIED_REMOTE");
				store.replacePlan(profile, history, history.state().withPlan(plan.goal().goalId(), plan.goal().revision(), ordinal, plan.planIdentity(), plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration(), nativeBinding ? stored.hashes() : history.state().authorityHashes()), currentGoal, plan.goal());
				System.out.println("092B_VERIFIED_PLAN npc=" + plan.spec().npcId() + " target=" + target + " anchor=" + plan.spec().anchorId() + " sameStoredCapture=true");
			}
			final var codec = new PhantomBackgroundStateCodec();
			final var baseline = codec.decode(Files.readAllBytes(root.resolve(Files.exists(root.resolve(baselineFile)) ? baselineFile : "baseline.bin")));
			final var goal = goals.load(profile).orElseThrow().goal();
			recordNativeFacts(root.resolve(launchName + ".before-operation.properties"), background.acquisitionSnapshot(profile).orElseThrow(), goal, "NOT_INVOKED");
			final var catchups = new PhantomBackgroundCatchupStore(profiles, goals);
			if (!args[0].equals("restart"))
			{
				// One warmup only; native public topology and real recent-human history remain authoritative.
				final long ready = System.nanoTime() + 60_100_000_000L;
				while (System.nanoTime() < ready) { Thread.sleep(1000); }
				for (int minute = 0; minute < 120; minute++)
				{
					var before = catchups.load(profile).orElseThrow();
					if (before.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE)
					{
						final var old = before.state();
						final var renewed = catchups.renewCompleted(profile, before, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING,
							"e".repeat(64), 920092, old.targetEpochMinute(), old.targetEpochMinute() + 120, old.targetEpochMinute(), old.planOrdinal(), 0,
							old.generation() + 1, old.knowledgeGeneration(), old.topologyGeneration(), old.goalId(), old.goalRevision(), old.planIdentity(), old.modelVersion(), old.authorityHashes(), ""));
						before = catchups.replace(profile, renewed, renewed.state().running());
					}
					final var next = before.state().advanceTo(before.state().cursorEpochMinute() + 1);
					if (minute == 0)
					{
						try (var held = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(baseline.identity().characterObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND))
						{
							PhantomTask091Lane.require(held != null, "092B_CONTENTION_LEASE");
							final var raced = materialization.materialize(profile);
							final var busy = background.advanceHistorical(profile, goal, before, next);
							PhantomTask091Lane.require(raced.status() == PhantomMaterializationService.ResultStatus.IDENTITY_BUSY
								&& busy.status() == PhantomBackgroundService.OperationStatus.RETRY && busy.reason().equals("identity.busy") && materialization.find(profile).isEmpty()
								&& World.getInstance().findObject(baseline.identity().characterObjectId()) == null
								&& baseline.equals(background.acquisitionSnapshot(profile).orElseThrow()) && before.equals(catchups.load(profile).orElseThrow()), "092B_IDENTITY_MATERIALIZATION_RACE");
							System.out.println("092B_CONTENTION_PASS materialization=" + raced.status() + " background=" + busy.status() + " WorldPlayer=0 noMutation=true");
						}
						final var calls = new java.util.concurrent.atomic.AtomicInteger();
						background.installOffscreenObserverAdmission((id, from, to) ->
						{
							final var actual = admission.evaluate(id, from, to);
							return calls.incrementAndGet() == 3 ? new PhantomOffscreenObserverAdmission.Verdict(PhantomOffscreenObserverAdmission.Code.DENY_DESTINATION_HUMAN,
								id, actual.source(), actual.destination(), 0, System.nanoTime(), actual.inspectedHumans()) : actual;
						});
						final var denied = background.advanceHistorical(profile, goal, before, next);
						System.out.println("092B_DENIAL_PROBE calls=" + calls.get() + " result=" + denied);
						PhantomTask091Lane.require(calls.get() == 3 && denied.status() == PhantomBackgroundService.OperationStatus.RETRY
							&& baseline.equals(background.acquisitionSnapshot(profile).orElseThrow()) && before.equals(catchups.load(profile).orElseThrow()), "092B_COMMIT_DENIAL_ROLLBACK");
						background.installOffscreenObserverAdmission(admission); uncertain.set(true);
						System.out.println("092B_COMMIT_DENIAL_PASS no canonical/state/clock/receipt/cursor mutation");
					}
					System.out.println("092B_INTERVAL_BEFORE minute=" + minute + " goal=" + goal.goalId() + ":" + goal.revision() + " cursor=" + before + " state=" + background.acquisitionSnapshot(profile));
					final var result = background.advanceHistorical(profile, goal, before, next);
					System.out.println("092B_MINUTE " + minute + " " + result);
					final var observedAfter = background.acquisitionSnapshot(profile);
					System.out.println("092B_INTERVAL_AFTER minute=" + minute + " outcome=" + result + " state=" + observedAfter);
					PhantomTask091Lane.require(result.status() == PhantomBackgroundService.OperationStatus.SUCCESS || result.status() == PhantomBackgroundService.OperationStatus.IDEMPOTENT, "092B_TRAVEL_" + result.reason());
					final var state = observedAfter.orElseThrow();
					if (minute == 0) { PhantomTask091Lane.require(result.status() == PhantomBackgroundService.OperationStatus.IDEMPOTENT && PhantomOffscreenOutdoorTravel.pendingDebt(state), "092B_UNCERTAIN_COMMIT"); }
					if (minute == 1) { PhantomTask091Lane.require(state.clock().residualTravelMillis() == 0 && state.position().equals(baseline.position()), "092B_RENEWAL_CANCELLATION"); }
					PhantomTask091Lane.require(World.getInstance().findObject(state.identity().characterObjectId()) == null && materialization.find(profile).isEmpty(), "092B_STORED_ABSENCE");
					if (state.position().committedAnchorId().equals(PhantomBackgroundGoalSpec.parse(goal).anchorId()))
					{
						Files.write(root.resolve("arrival.bin"), codec.encode(state), StandardOpenOption.CREATE_NEW);
						Files.write(root.resolve("last-catchup.bin"), new PhantomBackgroundCatchupStateCodec().encode(before.state()), StandardOpenOption.CREATE_NEW);
						Files.writeString(root.resolve("last-version.txt"), Long.toString(before.rowVersion()), StandardOpenOption.CREATE_NEW); break;
					}
				}
			}
			final var arrival = codec.decode(Files.readAllBytes(root.resolve("arrival.bin")));
			final var verified = transactions.reconcileVerifyPending(profile, baseline.identity().characterObjectId());
			System.out.println("092B_CANONICAL_TRANSACTION outcome=" + verified.status() + " state=" + verified.state());
			PhantomTask091Lane.require(verified.successful() && arrival.equals(verified.state()) && arrival.identity().equals(baseline.identity())
				&& arrival.progress().equals(baseline.progress()) && arrival.inventory().equals(baseline.inventory()), "092B_DURABLE_IDENTITY_INVENTORY");
			final var point = topology.findProfile(profile).orElseThrow().point();
			PhantomTask091Lane.require(point.equals(new PhantomTopologyPoint(arrival.position().x(), arrival.position().y(), arrival.position().z(), 0)), "092B_TOPOLOGY_PUBLICATION");
			final var last = new PhantomBackgroundCatchupStore.Snapshot(new PhantomBackgroundCatchupStateCodec().decode(Files.readAllBytes(root.resolve("last-catchup.bin"))), Long.parseLong(Files.readString(root.resolve("last-version.txt")).strip()));
			final var replay = background.advanceHistorical(profile, goal, last, last.state().advanceTo(last.state().cursorEpochMinute() + 1));
			System.out.println("092B_REPLAY_OUTCOME " + replay + " state=" + background.acquisitionSnapshot(profile));
			PhantomTask091Lane.require(replay.status() == PhantomBackgroundService.OperationStatus.IDEMPOTENT && arrival.equals(background.acquisitionSnapshot(profile).orElseThrow()), "092B_REPLAY");
			recordNativeFacts(root.resolve(launchName + ".after.properties"), arrival, goal, String.valueOf(lastObserver.get()));
			System.out.println("092B_OBSERVER_FINAL " + lastObserver.get());
			System.out.println("092B_" + args[0].toUpperCase() + "_PASS canonical=" + arrival.position() + " sameIdentityInventoryProgress=true WorldPlayer=0 replay=" + replay.status());
			PhantomTask091Lane.require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "092B_STOP");
			topology.beginStop(); PhantomTask091Lane.require(topology.finishStop(), "092B_TOPOLOGY_STOP");
		}
	}

	private static void recordCompositeGuard(Path file, PhantomBackgroundState saved, PhantomBackgroundState stored,
		PhantomBackgroundState.Hashes liveHashes, PhantomBackgroundSuite.ProductionAuthorityFixture fixture) throws Exception
	{
		recordCompositeGuard(file, saved, stored, liveHashes, fixture, "BEFORE_ORIGINAL_COMPOSITE_GUARD");
	}

	private static void recordCompositeGuard(Path file, PhantomBackgroundState saved, PhantomBackgroundState stored,
		PhantomBackgroundState.Hashes liveHashes, PhantomBackgroundSuite.ProductionAuthorityFixture fixture, String phase) throws Exception
	{
		final var facts = new Properties();
		facts.setProperty("stored.equals.saved", Boolean.toString(stored.equals(saved)));
		facts.setProperty("stored.hashes.equals.saved.hashes", Boolean.toString(stored.hashes().equals(saved.hashes())));
		facts.setProperty("stored.hashes.equals.live.hashes", Boolean.toString(stored.hashes().equals(liveHashes)));
		final String[] components = {"knowledge", "topology", "progression", "commerce"};
		final var expected = List.of(saved.hashes().knowledge(), saved.hashes().topology(), saved.hashes().progression(), saved.hashes().commerce());
		final var actual = List.of(stored.hashes().knowledge(), stored.hashes().topology(), stored.hashes().progression(), stored.hashes().commerce());
		final var live = List.of(liveHashes.knowledge(), liveHashes.topology(), liveHashes.progression(), liveHashes.commerce());
		String firstDifference = "NONE";
		for (int index = 0; index < components.length; index++)
		{
			facts.setProperty("saved.hashes." + components[index], expected.get(index));
			facts.setProperty("stored.hashes." + components[index], actual.get(index));
			facts.setProperty("live.hashes." + components[index], live.get(index));
			if (firstDifference.equals("NONE") && !actual.get(index).equals(live.get(index))) { firstDifference = components[index]; }
		}
		facts.setProperty("firstStoredLiveHashDifference", firstDifference);
		recordGuardSection(facts, "state", saved.state(), stored.state());
		recordGuardSection(facts, "identity", saved.identity(), stored.identity());
		recordGuardSection(facts, "progress", saved.progress(), stored.progress());
		recordGuardSection(facts, "vitals", saved.vitals(), stored.vitals());
		recordGuardSection(facts, "position", saved.position(), stored.position());
		recordGuardSection(facts, "combat", saved.combat(), stored.combat());
		recordGuardSection(facts, "loadout", saved.loadout(), stored.loadout());
		recordGuardSection(facts, "inventory", saved.inventory(), stored.inventory());
		recordGuardSection(facts, "autoGetSkills", saved.autoGetSkills(), stored.autoGetSkills());
		recordGuardSection(facts, "clock", saved.clock(), stored.clock());
		recordGuardSection(facts, "receipt", saved.receipt(), stored.receipt());
		recordGuardSection(facts, "hashes", saved.hashes(), stored.hashes());
		facts.setProperty("saved.inventory.objectCount", Integer.toString(saved.inventory().objects().size()));
		facts.setProperty("stored.inventory.objectCount", Integer.toString(stored.inventory().objects().size()));
		facts.setProperty("saved.inventory.canonicalHash", saved.inventory().canonicalHash());
		facts.setProperty("stored.inventory.canonicalHash", stored.inventory().canonicalHash());
		facts.setProperty("saved.autoGetSkills.count", Integer.toString(saved.autoGetSkills().size()));
		facts.setProperty("stored.autoGetSkills.count", Integer.toString(stored.autoGetSkills().size()));
		final var knowledge = fixture.knowledge().snapshot();
		facts.setProperty("catalog.knowledge.generation", Long.toString(knowledge.generation()));
		facts.setProperty("catalog.knowledge.combinedHash", knowledge.combinedHash());
		facts.setProperty("catalog.knowledge.components", knowledge.hashes().toString());
		facts.setProperty("catalog.knowledge.lootPolicyFingerprint", "NOT_EXPOSED_BY_PUBLIC_API");
		facts.setProperty("catalog.topology.generation", Long.toString(fixture.topology().snapshot().generation()));
		facts.setProperty("catalog.topology.canonicalHash", fixture.topology().snapshot().canonicalHash());
		facts.setProperty("catalog.topology.canonicalHash", fixture.topology().snapshot().canonicalHash());
		facts.setProperty("catalog.progression.components", fixture.progression().hashes().toString());
		facts.setProperty("catalog.commerce.components", fixture.commerce().hashes().toString());
		facts.setProperty("catalog.progression.generation", "NOT_EXPOSED_BY_PUBLIC_API");
		facts.setProperty("catalog.commerce.generation", "NOT_EXPOSED_BY_PUBLIC_API");
		facts.setProperty("productionCodeSource", fixture.authority().getClass().getProtectionDomain().getCodeSource().getLocation().toString());
		facts.setProperty("diagnostic.phase", phase);
		try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) { facts.store(writer, "TASK092B composite guard diagnostic; no inventory rows"); }
		facts.stringPropertyNames().stream().sorted().forEach(key -> System.out.println("092B_GUARD_DIAGNOSTIC " + key + "=" + facts.getProperty(key)));
	}

	private static PhantomBackgroundState readStoredState(long profileId) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true);
			PhantomTask091Lane.require(PhantomTask091Lane.SCHEMA.equals(connection.getCatalog()), "092B_HASH_FACTS_OWN_CATALOG");
			try (var query = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE profile_id=? AND component_type='background.state'"))
			{
				query.setLong(1, profileId);
				try (var rows = query.executeQuery())
				{
					PhantomTask091Lane.require(rows.next(), "092B_HASH_FACTS_PRESENT");
					final var state = new PhantomBackgroundStateCodec().decode(rows.getBytes(1));
					PhantomTask091Lane.require(!rows.next(), "092B_HASH_FACTS_UNIQUE");
					return state;
				}
			}
		}
	}

	private static void recordGuardSection(Properties facts, String section, Object saved, Object stored) throws Exception
	{
		facts.setProperty("section." + section + ".equal", Boolean.toString(java.util.Objects.equals(saved, stored)));
		// Structural UTF-8 toString digests are diagnostic summaries, not authority or inventory hashes.
		facts.setProperty("section." + section + ".savedSummarySha256", PhantomTask091Lane.hashText(saved.toString()));
		facts.setProperty("section." + section + ".storedSummarySha256", PhantomTask091Lane.hashText(stored.toString()));
	}

	private static void recordNativeFacts(Path file, PhantomBackgroundState state,
		org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal, String observer) throws Exception
	{
		final var facts = new Properties();
		facts.setProperty("identity", state.identity().toString());
		facts.setProperty("goalId", Long.toString(goal.goalId())); facts.setProperty("goalRevision", Long.toString(goal.revision()));
		facts.setProperty("position", state.position().toString()); facts.setProperty("progress", state.progress().toString());
		facts.setProperty("clock", state.clock().toString()); facts.setProperty("receipt", state.receipt().toString());
		facts.setProperty("inventoryHash", state.inventory().canonicalHash()); facts.setProperty("observer", observer);
		facts.setProperty("hashes", state.hashes().toString()); facts.setProperty("vitals", state.vitals().toString());
		facts.setProperty("combat", state.combat().toString()); facts.setProperty("loadout", state.loadout().toString());
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); PhantomTask091Lane.require(PhantomTask091Lane.SCHEMA.equals(connection.getCatalog()), "092B_FACTS_OWN_CATALOG");
			try (var query = connection.prepareStatement("SELECT component_type,row_version,payload FROM phantom_profile_components WHERE profile_id=? ORDER BY component_type"))
			{
				query.setLong(1, state.identity().profileId());
				try (var rows = query.executeQuery())
				{
					while (rows.next())
					{
						facts.setProperty("component." + rows.getString(1) + ".rowVersion", Long.toString(rows.getLong(2)));
						facts.setProperty("component." + rows.getString(1) + ".sha256", PhantomBackgroundTransaction.payloadDigest(rows.getBytes(3)));
					}
				}
			}
			try (var query = connection.prepareStatement("SELECT x,y,z,level,exp,sp,classid FROM characters WHERE charId=?"))
			{
				query.setInt(1, state.identity().characterObjectId());
				try (var row = query.executeQuery())
				{
					PhantomTask091Lane.require(row.next(), "092B_FACTS_CHARACTER");
					facts.setProperty("nativeXYZ", row.getInt(1) + "," + row.getInt(2) + "," + row.getInt(3));
					PhantomTask091Lane.require(row.getInt(1) == state.position().x() && row.getInt(2) == state.position().y() && row.getInt(3) == state.position().z()
						&& row.getInt(4) == state.progress().level() && row.getLong(5) == state.progress().experience()
						&& row.getLong(6) == state.progress().skillPoints() && row.getInt(7) == state.identity().activeClassId() && !row.next(), "092B_FACTS_CANONICAL_MATCH");
				}
			}
			long adena = 0;
			try (var query = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
			{
				query.setInt(1, state.identity().characterObjectId());
				try (var rows = query.executeQuery())
				{
					while (rows.next())
					{
						facts.setProperty("item." + rows.getInt(1), rows.getInt(2) + ":" + rows.getLong(3) + ":" + rows.getString(4));
						if (rows.getInt(2) == org.l2jmobius.gameserver.model.itemcontainer.Inventory.ADENA_ID) { adena = Math.addExact(adena, rows.getLong(3)); }
					}
				}
			}
			facts.setProperty("Adena", Long.toString(adena));
		}
		try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) { facts.store(writer, "TASK092B native canonical facts"); }
		System.out.println("092B_FACTS " + file.getFileName() + " " + facts);
	}
}
