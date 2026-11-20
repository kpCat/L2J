/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

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
	}
}
