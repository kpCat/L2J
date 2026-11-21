/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.Offense;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.OffenseContext;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.tests.phantoms.PhantomCombat131Fixture.DuelIdentity;

public final class PhantomCombat131Checks
{
	public static void main(String[] args)
	{
		PhantomCombatCoreSuite.testReadySkillBeyondLoadoutBound();
		System.out.println("TASK131_D1_READY_SKILL_BOUND_PASS");
		PhantomCombatCoreSuite.testUnlistedAcquiredPveSkill();
		System.out.println("TASK131_D1_ACQUIRED_PVE_PASS");
		PhantomCombatCoreSuite.testPartySupportYield();
		System.out.println("TASK131_D2_PARTY_SUPPORT_YIELD_PASS");
		PhantomCombatCoreSuite.testRetreatDrainsNativeCast();
		System.out.println("TASK131_D9_RETREAT_DRAIN_PASS");
		testSharedOffensePreference();
		System.out.println("TASK131_D1_CONTEXTUAL_OFFENSE_PASS");
		PhantomCombatCoreSuite.testPartyAreaOwner();
		System.out.println("TASK131_D3_PARTY_AREA_OWNER_PASS");
		testDuelSetupRoster();
		System.out.println("TASK131_N06_SETUP_ROSTER_CONTRACT_PASS");
	}

	private static void testDuelSetupRoster()
	{
		final var mage = List.of(new SelectedSkill(10, 12), new SelectedSkill(11, 18));
		final var physical = List.of(new SelectedSkill(20, 9), new SelectedSkill(21, 14));
		final var first = new DuelIdentity(1, "A", 1, 110, "owned_a", "MAGE", 10, mage);
		final var second = new DuelIdentity(1, "B", 2, 120, "owned_b", "PHYSICAL", 88, physical);
		final var third = new DuelIdentity(2, "A", 3, 130, "owned_c", "PHYSICAL", 88, physical);
		final var fourth = new DuelIdentity(2, "B", 4, 140, "owned_d", "MAGE", 10, mage);
		PhantomCombat131Fixture.validateDuelRoster(List.of(first, second, third, fourth));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 110, "owned_d", "MAGE", 10, mage)));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 140, "owned_a", "MAGE", 10, mage)));
		rejectDuelRoster(List.of(first, second, third, new DuelIdentity(2, "B", 4, 140, "owned_d", "MAGE", 10,
			List.of(new SelectedSkill(10, 11), mage.getLast()))));
	}

	private static void rejectDuelRoster(List<DuelIdentity> roster)
	{
		try { PhantomCombat131Fixture.validateDuelRoster(roster); }
		catch (IllegalStateException expected) { return; }
		throw new AssertionError("Inconsistent N06 setup roster was accepted.");
	}

	private static void testSharedOffensePreference()
	{
		final var strike = new Offense(new SelectedSkill(31, 4), true, false, false, 200, 500, 900, 70, 100, 21);
		final var interrupt = new Offense(new SelectedSkill(67, 7), false, true, false, 300, 500, 400, 70, 0, 12);
		final var activeControl = new Offense(interrupt.skill(), false, true, true, 300, 500, 400, 70, 0, 12);
		final var ordinary = new OffenseContext(false, false, true, 0.8, 0.8, 300);
		final var hostileCast = new OffenseContext(false, true, true, 0.8, 0.8, 300);
		final var distantCast = new OffenseContext(false, true, true, 0.8, 0.8, 550);
		final var occludedCast = new OffenseContext(false, true, false, 0.8, 0.8, 300);
		final var pvpFinisher = new OffenseContext(true, false, true, 0.12, 0.8, 300);
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(ordinary, List.of(interrupt, strike)).getFirst(), "PvE without a cast to interrupt should prefer damage.");
		PhantomAssertions.assertEquals(interrupt, CombatDecisionPolicy131.rankOffense(hostileCast, List.of(strike, interrupt)).getFirst(), "An in-range interruption should protect both combat paths.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(distantCast, List.of(interrupt, strike)).getFirst(), "An out-of-range control is not an available interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(occludedCast, List.of(interrupt, strike)).getFirst(), "A hidden target is not an available interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(hostileCast, List.of(activeControl, strike)).getFirst(), "Already active control must not be treated as a new interruption.");
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(pvpFinisher, List.of(strike, interrupt)).getFirst(), "A low-health opponent should be finished rather than controlled again.");
		final var area = new Offense(new SelectedSkill(68, 2), true, false, false, 200, 500, 1100, 70, 80, 40, 3);
		PhantomAssertions.assertEquals(area, CombatDecisionPolicy131.rankOffense(ordinary, List.of(strike, area)).getFirst(), "Coverage of the admitted group of enemies did not affect the common chooser.");
		final var lowMp = new OffenseContext(false, false, true, 0.8, 0.30, 300);
		PhantomAssertions.assertEquals(strike, CombatDecisionPolicy131.rankOffense(lowMp, List.of(strike, area)).getFirst(), "Area coverage permanently displaced resource conservation.");
	}
}
