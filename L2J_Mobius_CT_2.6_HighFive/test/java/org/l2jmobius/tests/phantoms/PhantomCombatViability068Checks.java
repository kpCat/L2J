/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.config.custom.ChampionMonstersConfig;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;

/** Pure scalar controls; native stat projection is exercised separately in the owned scene. */
public final class PhantomCombatViability068Checks implements PhantomTestSuite
{
	@Override public String id() { return "combat-viability068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-current-loadout-hp-mp", _ -> currentLoadout());
		registry.add("02-target-strength-and-unclamped-budget", _ -> targetStrength());
	}

	public static void main(String[] args)
	{
		currentLoadout(); targetStrength();
		System.out.println("OWN068_COMBAT_VIABILITY_PASS cases=2; DB_WRITES=0");
	}

	private static void currentLoadout()
	{
		final Target target = target(500, 100);
		for (ModelKind kind : List.of(ModelKind.MELEE, ModelKind.RANGED, ModelKind.MAGIC))
		{
			PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat(kind, 10, 10), vitals(1000, 100), 0, target), "Weak current equipment was treated as level capability.");
			PhantomAssertions.assertTrue(PhantomBackgroundModel.viableEncounter(combat(kind, 1000, 1000), vitals(1000, 100), 20, target), "Actual stronger loadout did not admit the same target.");
			PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat(kind, 1000, 1000), vitals(1, 100), 20, target), "Current HP was replaced by maximum HP.");
			PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat(kind, 1000, 1000), vitals(1000, 19), 20, target), "Unaffordable selected native skill was admitted.");
			PhantomAssertions.assertTrue(PhantomBackgroundModel.viableEncounter(combat(kind, 1000, 1000), vitals(1000, 0), 0, target), "MP depletion disabled an affordable ordinary attack.");
		}
	}

	private static void targetStrength()
	{
		final CombatFacts combat = combat(ModelKind.MELEE, 1000, 1000);
		final Vitals vitals = vitals(1000, 100);
		for (double hpMultiplier : new double[] {1, 2, 4})
		{
			PhantomAssertions.assertTrue(PhantomBackgroundModel.viableEncounter(combat, vitals, 0, target(500 * hpMultiplier, 100)), "Manageable stronger target was blanket-excluded.");
		}
		PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat, vitals, 0, target(4000, 3000)), "Stronger native target offense/HP did not affect admission.");
		final Target huge = target(100_000, 1);
		PhantomAssertions.assertTrue(PhantomBackgroundModel.estimateEncounter(combat, huge).durationMillis() > 120_000, "Simulation's 20s clamp hid a long real encounter.");
		PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat, vitals, 0, huge), "Unproductive encounter exceeded existing combat budget.");
		final Target alone = target(4000, 1000);
		PhantomAssertions.assertTrue(PhantomBackgroundModel.viableEncounter(combat, vitals, 0, alone), "Manageable isolated target was denied.");
		PhantomAssertions.assertFalse(PhantomBackgroundModel.viableEncounter(combat, vitals, 0, alone, List.of(target(500, 3000))), "Actual additional attacker was ignored.");
	}

	/** Call from the owned native A scene before/after a real gear change and for each actual tier. */
	public static Map<String, String> nativeProjection(Player player, Creature target)
	{
		PhantomAssertions.assertTrue(PhantomTask068Lane.enabled() && player.getAccessLevel().getLevel() == 0 && !player.isInvul() && !target.isInvul(), "Native viability controls require an ordinary own068 actor and target.");
		final var skill = PhantomEngagementPlanner.offensiveSkill(player, target);
		final var actor = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, target, skill);
		final var projected = L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true);
		PhantomAssertions.assertTrue(actor.combat().physicalOffense() == player.getPAtk(target) && actor.combat().physicalDefense() == player.getPDef(target) && actor.combat().magicOffense() == player.getMAtk(target, skill) && actor.combat().magicDefense() == player.getMDef(target, skill), "Planning replaced native current gear/buff stats.");
		PhantomAssertions.assertTrue(actor.vitals().currentHp() == player.getCurrentHp() && actor.vitals().currentMp() == Math.min(player.getCurrentMp(), player.getMaxMp()), "Planning replaced current native resources.");
		final double hpMultiplier = target.isChampion() ? Math.max(1, ChampionMonstersConfig.profile(target).hp()) : 1;
		PhantomAssertions.assertTrue(projected.maximumHp() == Math.max(1, target.getCurrentHp()) * hpMultiplier && projected.physicalOffense() == target.getPAtk(null), "Planning lost native target/champion strength.");
		final double cost = skill == null ? 0 : Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill));
		PhantomAssertions.assertTrue(actor.requiredMp() == cost, "Planning replaced the selected known native skill cost.");
		return Map.of("gear", player.getInventory().getPaperdollItems().stream().map(item -> item.getId()).sorted().toList().toString(), "hp", Double.toString(actor.vitals().currentHp()), "mp", Double.toString(actor.vitals().currentMp()), "skill", Integer.toString(skill == null ? 0 : skill.getId()), "physicalOffense", Double.toString(actor.combat().physicalOffense()), "targetHp", Double.toString(projected.maximumHp()), "champion", Boolean.toString(target.isChampion()), "viable", Boolean.toString(PhantomEngagementPlanner.viable(player, target)));
	}

	private static CombatFacts combat(ModelKind kind, double offense, double defense)
	{
		return new CombatFacts(kind, offense, offense, defense, defense, 500, 500, 0, 0, 1, 1, 0, 1, 1, 1, 1);
	}

	private static Vitals vitals(double hp, double mp) { return new Vitals(hp, 1000, mp, 100, 0, 0); }
	private static Target target(double hp, double offense)
	{
		return new Target(1, 1, true, hp, 100, offense, offense, 100, 100, 500, 500, 0, 0, List.of(), 1, (float) hp);
	}
}
