/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.DamageReceipt;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Action;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Effect;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Kind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Scene;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalCombat.Source;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalDecision;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalDecision.Calibration;
import org.l2jmobius.gameserver.phantoms.combat.PhantomTacticalDecision.Encounter;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomTacticalCombat023Checks
{
	public static void main(String[] args) throws Exception
	{
		final var hit = action(0, true, 0.8, 0, 0, new Effect(Kind.PHYSICAL, 35, 0, 1));
		final var spell = action(10, true, 1.4, 22, 0, new Effect(Kind.MAGIC, 120, 0, 1));
		final var producer = action(11, true, 0.4, 0, 0, new Effect(Kind.CHARGE, 2, 0, 1));
		final var spender = action(12, false, 0.7, 0, 2, new Effect(Kind.PHYSICAL, 220, 0, 1));
		final var interrupt = action(13, true, 0.3, 5, 0, new Effect(Kind.CONTROL, 1, 2, 1));
		final var heal = action(14, true, 0.3, 5, 0, new Effect(Kind.HEAL, 60, 0, 1));
		check(plan(100, 0, 30, false, List.of(), hit, spell) == 0, "Overkill should conserve MP");
		check(plan(100, 0, 190, false, List.of(), hit, producer, spender) == 11, "A ready producer should unlock a better continuation");
		final var casting = new Source(12, 55, true, true, true, true, 1, 0.8);
		check(plan(48, 0, 220, false, List.of(casting), hit, spell, interrupt) == 13, "Interrupt should precede a lethal burst");
		final var root = action(15, true, 0.3, 5, 0, new Effect(Kind.ROOT, 1, 3, 1));
		check(plan(48, 0, 220, false, List.of(casting), hit, spell, root, interrupt) == 13, "Root must not replace a cast interrupt");
		final var pressure = new Source(24, 0, true, true, false, true, 1, 1);
		check(plan(18, 0, 260, false, List.of(pressure), hit, spell, heal) == 14, "Healing should beat dying in a long cast");
		final var neutral = new Source(200, 0, false, true, false, false, 0, 1);
		final var blocked = new Source(200, 0, true, false, false, false, 4, 1);
		final Scene pve = scene(50, 50, 100, false, List.of(pressure, neutral, blocked));
		final var threat = PhantomTacticalCombat.threat(pve);
		check(threat.incomingDps() == 24 && threat.attackers() == 2, "A neutral and a blocked attacker do not contribute immediate DPS");
		check(PhantomTacticalCombat.threat(scene(50, 50, 100, true, List.of(pressure))).dangerSeconds() > threat.dangerSeconds(), "Only PvP pressure should spend CP");
		final var pveInPvp = new Source(24, 0, true, true, false, true, 1, 1, false);
		check(PhantomTacticalCombat.threat(scene(50, 50, 100, true, List.of(pveInPvp))).dangerSeconds() == threat.dangerSeconds(), "NPC pressure must bypass PvP CP");
		final var timed = PhantomTacticalCombat.choose(pve, List.of(hit, spell, interrupt, heal), () -> 10, 10);
		check(timed.expansions() == 0 && timed.budgetLimited(), "Expired clock must return a bounded decision");
		checkCalibration();
		System.out.println("TACTICAL_COMBAT_023 MODEL seed=23002301 PASS");
		if (args.length == 1) { checkCatalog(Path.of(args[0])); }
	}

	private static void checkCalibration()
	{
		final var cache = new Calibration(1);
		final var target = new Target(101, 0, 12);
		final var encounter = new Encounter(5, 9, 2, 0, target);
		final var first = new DamageReceipt(5, 9, target, 4, 1_000_000_000L, 18);
		final var next = new DamageReceipt(5, 9, target, 5, 2_000_000_000L, 56);
		check(cache.estimate(encounter, first, 20, first.observedNanos()) == 20, "First sample must retain the prior");
		final double corrected = cache.estimate(encounter, next, 20, next.observedNanos());
		check(corrected > 20 && corrected < 40, "Contiguous own damage should only partially correct the prior");
		check(cache.estimate(encounter, next, 20, next.observedNanos()) == corrected, "A duplicate must not increase confidence");
		final var foreign = new DamageReceipt(6, 9, target, 6, 2_200_000_000L, 1000);
		check(cache.estimate(encounter, foreign, 20, foreign.observedNanos()) == corrected, "Foreign damage must not influence the estimate");
		final var newer = new Encounter(5, 10, 2, 0, target);
		check(cache.estimate(newer, next, 20, next.observedNanos()) == 20, "Prior owner evidence must not cross an epoch");
	}

	private static void checkCatalog(Path game) throws Exception
	{
		ServerConfig.DATAPACK_ROOT = game.toFile();
		ServerConfig.SCRIPT_ROOT = game.resolve("data/scripts").toFile();
		ScriptEngine.getInstance().executeScript(ScriptEngine.EFFECT_MASTER_HANDLER_FILE);
		final var catalog = SkillData.getInstance();
		final var shadowStep = catalog.getSkill(821, 1);
		check(shadowStep != null && PhantomSkillInventory.composition(shadowStep).components().stream().anyMatch(effect -> effect.kind() == Kind.MOVE)
			&& !PhantomTacticalDecision.hasSupportedProjection(shadowStep), "Mixed control must not admit an unaudited native teleport");
		final var ranks = new HashSet<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef>();
		final var coverage = new EnumMap<Kind, Integer>(Kind.class);
		int exact = 0, mixed = 0, passive = 0;
		for (int id : PhantomSkillInventory.classIds())
		{
			for (var entry : PhantomSkillInventory.theoretical(id))
			{
				if (!ranks.add(entry.skill())) { continue; }
				final var skill = catalog.getSkill(entry.skill().skillId(), entry.skill().skillLevel());
				if (skill == null || skill.getId() != entry.skill().skillId() || skill.getLevel() != entry.skill().skillLevel()) { continue; }
				final var composition = PhantomSkillInventory.composition(skill);
				check(composition.rank().equals(entry.skill()), "The descriptor lost the exact native rank");
				check(composition == PhantomSkillInventory.composition(skill), "One native definition should reuse one descriptor");
				exact++;
				if (composition.components().size() > 1) { mixed++; }
				if (composition.passive()) { passive++; }
				composition.components().forEach(effect -> coverage.merge(effect.kind(), 1, Integer::sum));
			}
		}
		check(exact > 500 && mixed > 0 && passive > 0, "Missing loaded stock effects or class ranks");
		check(coverage.containsKey(Kind.DRAIN) && coverage.containsKey(Kind.CONTROL) && coverage.containsKey(Kind.UNKNOWN), "Mixed and unsupported facts must be visible");
		System.out.println("TACTICAL_COMBAT_023 NATIVE_CATALOG exact=" + exact + " mixed=" + mixed + " passive=" + passive + " effects=" + coverage);
	}

	private static Scene scene(double hp, double cp, double targetHp, boolean pvp, List<Source> sources)
	{
		return new Scene(hp, 100, cp, 50, 50, 0, 0, targetHp, targetHp, 0, pvp, false, sources);
	}

	private static int plan(double hp, double cp, double targetHp, boolean pvp, List<Source> sources, Action... actions)
	{
		final var choice = PhantomTacticalCombat.choose(scene(hp, cp, targetHp, pvp, sources), List.of(actions), () -> 0, 1);
		check(choice.first() != null && choice.expansions() <= PhantomTacticalCombat.MAX_EXPANSIONS, "Missing first action or excessive search");
		return choice.first().id();
	}

	private static Action action(int id, boolean ready, double seconds, double mp, int charges, Effect effect)
	{
		return new Action(id, id, ready, true, seconds, 1, mp, 0, 0, charges, 0, false, List.of(effect));
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
