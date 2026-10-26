/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.PvePreparation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.PvePreparationAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativePetRolePolicy;

/** DB-free consumer contract: supportive pets must not receive stock automatic attack roots. */
public final class PhantomPveReadiness093Suite implements PhantomTestSuite
{
	@Override public String id() { return "pve-readiness093"; }

	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("093-continuation-and-readiness-debt-retain-exact-owner", _ ->
		{
			final String visible = Files.readString(Path.of(System.getProperty("phantom.module.root")).resolve("java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java"));
			PhantomAssertions.assertFalse(visible.contains("target.isAlikeDead() || nativeBusy(_player)) { return true; }"), "Busy continuation grants an unrelated new target.");
			final int carry = visible.indexOf("_readinessSince = previous._readinessSince");
			PhantomAssertions.assertTrue(carry > 0 && visible.substring(Math.max(0, carry - 160), carry).contains("previous._goalId == goalId && previous._revision == revision"), "A new goal inherits expired readiness debt.");
		});
		registry.add("093-native-wolf-family-only-pet-dd", _ ->
		{
			for (int id : new int[] {12077, 16025, 16030, 16037, 16041, 16042}) { PhantomAssertions.assertTrue(PhantomNativePetRolePolicy.combatPet(id), "Stock wolf family lost attack eligibility."); }
			for (int id : new int[] {0, -1, 12780, 12781, 12782, 16034, 16035, 16036, 12311, 12526, 12621, 99999}) { PhantomAssertions.assertFalse(PhantomNativePetRolePolicy.combatPet(id), "Support/unknown Pet became DD."); }
		});
		registry.add("093-level-tiers-preserve-safe-low-resource-combat", _ ->
		{
			for (int level : new int[] {1, 20, 39, 40, 60, 75, 76, 80, 85})
			{
				final var missing = input(level, 100, 100, 0, false, false);
				PhantomAssertions.assertEquals(PvePreparationAction.ALLOW, PhantomCombatPolicy.pvePreparation(missing, true, false, false).action(), "Safe native target was blanket-blocked by missing shots.");
				final var risky = PhantomCombatPolicy.pvePreparation(missing, true, true, false);
				PhantomAssertions.assertEquals(level < 40 ? PvePreparationAction.ALLOW : PvePreparationAction.MAINTAIN_SUPPLIES, risky.action(), "Risky solo shortage ignored the level tier.");
				PhantomAssertions.assertEquals(PvePreparationAction.RECOVER, PhantomCombatPolicy.pvePreparation(input(level, 20, 100, 30, true, true), true, false, false).action(), "Low HP did not defer a new pull.");
			}
		});
		registry.add("093-high-tier-has-no-pet-or-future-buff-credit", _ ->
		{
			PhantomAssertions.assertEquals(PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, PhantomCombatPolicy.pvePreparation(input(80, 100, 100, 20, false, false), true, true, false).action(), "Missing native buffs were credited as future effects.");
			PhantomAssertions.assertEquals(PvePreparationAction.ALLOW, PhantomCombatPolicy.pvePreparation(input(80, 100, 100, 20, true, true), true, true, false).action(), "Actual prepared native input rejected.");
			PhantomAssertions.assertEquals(PvePreparationAction.WAIT_NATIVE, PhantomCombatPolicy.pvePreparation(input(80, 20, 0, 0, false, false), false, true, true).action(), "New preparation replaced an in-flight owner.");
			PhantomAssertions.assertEquals(PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, PhantomCombatPolicy.pvePreparation(input(80, 100, 100, 20, true, true), false, true, false).action(), "Nonviable target was called safe.");
		});
		registry.add("093-magic-shortage-gear-funds-and-human-default", _ ->
		{
			final var magic = new PvePreparation(80, 100, 0, true, true, false, 100, 2, true, false, false, true);
			PhantomAssertions.assertEquals(PvePreparationAction.RECOVER, PhantomCombatPolicy.pvePreparation(magic, true, true, false).action(), "MAGIC primary ignored its actual MP cost.");
			final var poor = new PvePreparation(80, 100, 100, false, false, true, 0, 0, false, false, false, false);
			PhantomAssertions.assertEquals(PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, PhantomCombatPolicy.pvePreparation(poor, true, true, false).action(), "No funds invented an executable purchase.");
			final var human = new org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy()
			{
				@Override public org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease acquire(org.l2jmobius.gameserver.model.actor.Player player) { return null; }
				@Override public boolean permitsTarget(org.l2jmobius.gameserver.model.actor.Creature target) { return true; }
			};
			PhantomAssertions.assertTrue(human.allowSummonAttack(null, null), "Default summon semantics changed for existing policies.");
			PhantomAssertions.assertFalse(PhantomNativePetRolePolicy.allowAttack(null, null), "Unknown Phantom ownership granted summon attack.");
		});
		registry.add("093-stock-summon-attack-is-owned-by-phantom-role-policy", _ ->
		{
			final Path root = Path.of(System.getProperty("phantom.module.root"));
			final String autoPlay = Files.readString(root.resolve("java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java"));
			final int attack = autoPlay.indexOf("summon.getAI().setIntention(Intention.ATTACK, creature)");
			PhantomAssertions.assertTrue(attack > 0, "Stock summon attack consumer disappeared; audit its replacement.");
			final int condition = autoPlay.lastIndexOf("if (", attack);
			PhantomAssertions.assertTrue(autoPlay.substring(condition, attack).contains("phantomPolicy.allowSummonAttack(player, summon)"), "Support Pet still receives an unconditional stock ATTACK root.");
			PhantomAssertions.assertTrue(autoPlay.substring(condition, attack).contains("phantomPolicy == null"), "Human stock summon attack must retain its ordinary branch.");
		});
	}

	private static PvePreparation input(int level, double hp, double mp, long shots, boolean buff, boolean potion)
	{
		return new PvePreparation(level, hp, mp, true, false, true, shots, potion ? 1 : 0, buff, true, false, true);
	}
}
