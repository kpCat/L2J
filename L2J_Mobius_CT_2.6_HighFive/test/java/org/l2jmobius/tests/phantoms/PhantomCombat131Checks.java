/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.Offense;
import org.l2jmobius.gameserver.phantoms.combat.CombatDecisionPolicy131.OffenseContext;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

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
