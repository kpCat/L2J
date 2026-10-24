/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Set;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Read-only access to the existing package-private native itinerary admission. */
public final class PhantomFarm074PlanningDiagnostic
{
	private PhantomFarm074PlanningDiagnostic() { }

	public static void append(long profileId, Player player, PhantomBackgroundState baseline, PhantomGoal predecessor,
		PhantomBackgroundCatchupState catchup, PhantomHistoricalBackgroundPlanner planner, PhantomVisibleFarmTravel travel, StringBuilder report)
	{
		// Match prepareVisibleDecision's current native projection; the durable baseline stays unchanged.
		final var skill = org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.offensiveSkill(player, null);
		final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, null, skill);
		final var oldLoadout = baseline.loadout();
		final var loadout = new PhantomBackgroundState.Loadout(skill == null ? 0 : skill.getId(), skill == null ? 0 : skill.getLevel(), oldLoadout.summonNpcId(), (int) Math.ceil(combat.requiredMp()), oldLoadout.shotItemId(), oldLoadout.shotsPerEncounter(), oldLoadout.summonResourceItemId(), oldLoadout.summonResourcesPerEncounter());
		final var progress = new PhantomBackgroundState.Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
		final var projected = new PhantomBackgroundState(baseline.state(), baseline.identity(), progress, combat.vitals(), baseline.position(), combat.combat(), loadout, baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
		final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		final var previous = PhantomBackgroundGoalSpec.parse(predecessor);
		final var excluded = Set.of(previous.npcId() + "@" + previous.anchorId());
		final int[] proposals = { 0 };
		report.append("B_OWNED_PLANNING_INPUT\tactiveWeapon=").append(player.getActiveWeaponInstance()).append(";nativeOrdinary=").append(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.ordinary(player)).append(";combat=").append(combat).append(";oldLocal=").append(planner.isVisibleLocal(predecessor, live)).append(";oldSuitable=").append(planner.remainsVisibleSuitable(projected, predecessor, live)).append(";diagnosticExcluded=").append(excluded).append(";noPublicationOrNativeActions=true\n");
		final var result = planner.replanVisibleLocal(profileId, projected, predecessor, catchup.deterministicSeed(), Math.addExact(catchup.planOrdinal(), 1), live, excluded, Set.of(), candidate ->
		{
			proposals[0]++;
			final boolean local = planner.isVisibleLocal(candidate, live);
			final boolean admitted = local || travel.permitsReplannedFarmTravel(predecessor, candidate, live, player);
			report.append("B_OWNED_PLANNING_PROPOSAL\tindex=").append(proposals[0]).append(";local=").append(local).append(";nativeItineraryAdmitted=").append(admitted).append(";candidate=").append(candidate).append('\n');
			return admitted;
		});
		report.append("B_OWNED_PLANNING_RESULT\tproposalCount=").append(proposals[0]).append(";ready=").append(result.ready()).append(";reason=").append(result.reasonKey()).append(";routeEdges=").append(result.routeEdgeIds()).append(";classification=").append(result.ready() ? "ADMITTED_PROPOSAL" : proposals[0] == 0 ? "NO_VIABLE_TARGET_OR_TOPOLOGY_ROUTE" : "NATIVE_ITINERARY_REJECTED").append(";noCAS=true\n");
	}
}
