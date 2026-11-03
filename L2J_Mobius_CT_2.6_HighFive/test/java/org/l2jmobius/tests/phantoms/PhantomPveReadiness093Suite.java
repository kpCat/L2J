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

	private static final long SECOND = 1_000_000_000L;

	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("117-canonical-idle-rests-before-farm-admission", _ ->
		{
			final double hp = 8.912675;
			PhantomAssertions.assertTrue(PhantomCombatPolicy.pveIdleRestRequired(true, true, false, hp, 176), "Low-HP historical idle waited for farm viability before native REST.");
			PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(false, true, false, hp, 176), "A noncanonical or foreign root acquired idle REST.");
			PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(true, false, false, hp, 176), "Idle REST interrupted another native activity.");
			PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(true, true, true, hp, 176), "Idle REST ignored a native threat.");
			PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(true, true, false, 140.8, 176), "Recovered idle kept requesting REST at the existing 80 percent threshold.");
			for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 177})
			{
				PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(true, true, false, invalid, 176), "Unproven or dead native HP acquired idle REST.");
			}
			PhantomAssertions.assertFalse(PhantomCombatPolicy.pveIdleRestRequired(true, true, false, hp, Double.NaN), "Unknown native max HP acquired idle REST.");
			PhantomAssertions.assertFalse(org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "Idle recovery policy initialized SQL.");
		});
		registry.add("117-commerce-target-does-not-poison-pve-resource-recovery", _ ->
		{
			final boolean ready = PhantomCombatPolicy.pveResourcesReady(129, 129, 138, 0, true);
			PhantomAssertions.assertTrue(ready, "Observed fully recovered native resources were not ready.");
			final boolean[][] facts = {{false, false, false, false}, {true, false, true, false}, {true, true, false, false}, {true, true, true, true}, {true, true, true, false}};
			final String[] names = {"no target", "live merchant", "dead monster", "viable live monster", "nonviable live monster"};
			final var allow = PhantomCombatPolicy.PveRecoveryAction.ALLOW;
			final var expected = new PhantomCombatPolicy.PveRecoveryAction[] {allow, allow, allow, allow, PhantomCombatPolicy.PveRecoveryAction.EXPIRED};
			for (int index = 0; index < facts.length; index++)
			{
				final var input = facts[index];
				final boolean viable = !PhantomCombatPolicy.pveResourceTargetRequiresViability(input[0], input[1], input[2]) || input[3];
				PhantomAssertions.assertEquals(expected[index], PhantomCombatPolicy.pveRecoveryAction(ready, viable, false, false, true), names[index] + " changed recovery admission after commerce.");
			}
			PhantomAssertions.assertEquals(PhantomCombatPolicy.PveRecoveryAction.REST, PhantomCombatPolicy.pveRecoveryAction(false, !PhantomCombatPolicy.pveResourceTargetRequiresViability(true, false, true), false, false, false), "A merchant target bypassed actual low-resource recovery.");
			PhantomAssertions.assertFalse(org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "Primitive recovery facts initialized SQL.");
		});
		registry.add("115-native-low-hp-rest-gets-one-finite-regen-budget", _ ->
		{
			final long budget = PhantomCombatPolicy.pveRecoveryBudgetNanos(183 * 0.8 - 72, 1.16, 0, 0, 45 * SECOND, 90 * SECOND);
			PhantomAssertions.assertTrue(budget >= 65 * SECOND && budget <= 90 * SECOND, "Native9 Fighter recovery expired at45s just before native80% HP readiness.");
			PhantomAssertions.assertEquals(45 * SECOND, PhantomCombatPolicy.pveRecoveryBudgetNanos(0, 1.16, 0, 0, 45 * SECOND, 90 * SECOND), "Healthy/basic-melee input received an extra recovery window.");
			PhantomAssertions.assertEquals(90 * SECOND, PhantomCombatPolicy.pveRecoveryBudgetNanos(1000, 1, 1000, 1, 45 * SECOND, 90 * SECOND), "Recovery prediction escaped the existing useful-stall bound.");
			PhantomAssertions.assertEquals(45 * SECOND, PhantomCombatPolicy.pveRecoveryBudgetNanos(72, 0, 0, 0, 45 * SECOND, 90 * SECOND), "Unknown/zero native regen extended recovery.");
			PhantomAssertions.assertEquals(45 * SECOND, PhantomCombatPolicy.pveRecoveryBudgetNanos(Double.NaN, 1, 0, 0, 45 * SECOND, 90 * SECOND), "Unproven native deficit extended recovery.");
			PhantomAssertions.assertTrue(!PhantomCombatPolicy.pveResourcesReady(133, 183, 59, 0, true) && PhantomCombatPolicy.pveResourcesReady(147, 183, 59, 0, true), "Native recovery hysteresis changed instead of allowing its actual elapsed time.");
		});
		registry.add("094-maintenance-keeps-native-support-mp-reserve", _ ->
		{
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.maintenanceAffordable(400, 1000, 60), "Maintenance spent the native support recovery reserve.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.maintenanceAffordable(410, 1000, 60), "Exact reserve boundary rejected an affordable native buff.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.maintenanceAffordable(Double.NaN, 1000, 10), "Unknown native resources authorized maintenance.");
		});
		registry.add("094-target-race-requires-an-observed-target", _ ->
		{
			final var condition = new org.l2jmobius.gameserver.model.conditions.ConditionTargetRace(org.l2jmobius.gameserver.model.actor.enums.creature.Race.UNDEAD);
			PhantomAssertions.assertFalse(condition.testImpl(null, null, null, null), "Unresolved planning target cannot satisfy a race-restricted native skill.");
		});
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
