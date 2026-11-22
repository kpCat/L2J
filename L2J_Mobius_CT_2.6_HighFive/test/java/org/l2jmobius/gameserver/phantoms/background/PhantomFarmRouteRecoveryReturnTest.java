/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape.Arrival;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape.MpDebit;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape.Receipt;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape.Status;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** A terminal native Return supplies exactly one landing for the same physical actor. */
public final class PhantomFarmRouteRecoveryReturnTest
{
	public enum Progress { NONE, HOLD, LANDED }

	public static boolean queue(PhantomVisibleFarmTravel travel, long profileId, PhantomGoal goal, Player player, long epoch,
		PhantomBackgroundState baseline, BooleanSupplier currentRoot)
	{
		final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		return travel.queueRecoveryReturn(profileId, goal, player, epoch, live, baseline, currentRoot);
	}

	public static Progress poll(PhantomVisibleFarmTravel travel, long profileId, PhantomGoal goal)
	{
		return Progress.valueOf(travel.continueRecoveryReturn(profileId, goal).name());
	}

	public static boolean finishEarnedRecovery(PhantomVisibleFarmTravel travel, long profileId, PhantomGoal goal, Player player, long epoch)
	{
		return travel.finishEarnedRecovery(profileId, goal, player, epoch);
	}

	public static void main(String[] args)
	{
		final var origin = new PhantomNavigationPoint(100, 200, 300, 0);
		final var landing = new PhantomNavigationPoint(500, 600, 700, 0);
		final var before = new MpDebit(2, 50, 48);
		final var hit = new MpDebit(4, 48, 44);
		final var arrival = new Arrival(41, 7, origin, landing, 23);
		final var paid = new Receipt(1050, 1, 41, 7, 800, 10, 25, before, hit, List.of(arrival), Status.ARRIVED);
		final var interrupted = new Receipt(1050, 1, 41, 7, 800, 10, 25, before, hit, List.of(arrival), Status.INTERRUPTED);
		final var otherSkill = new Receipt(2099, 1, 41, 7, 800, 10, 25, before, hit, List.of(arrival), Status.ARRIVED);
		PhantomAssertions.assertEquals(landing, PhantomVisibleFarmTravel.verifiedReturnLanding(paid, 41, 7, origin, landing), "Native learned Return supplies the factual landing.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.verifiedReturnLanding(interrupted, 41, 7, origin, landing), "An interrupted paid cast must not release a subsequent itinerary.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.verifiedReturnLanding(paid, 41, 8, origin, landing), "A prior materialization epoch does not own the landing.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.verifiedReturnLanding(paid, 41, 7, origin, origin), "A missing factual landing does not authorize a replan.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.verifiedReturnLanding(otherSkill, 41, 7, origin, landing), "Another escape skill cannot stand in for learned Return.");
		System.out.println("PASS ordinary farm Return accepts only an exact ARRIVED receipt and factual landing");
	}
}
