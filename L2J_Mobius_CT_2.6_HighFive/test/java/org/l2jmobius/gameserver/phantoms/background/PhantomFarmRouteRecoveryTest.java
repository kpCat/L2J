/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.BudgetProof;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Ordinary WALK release over a dry corner captured from the profile 32 GeoData route. */
public final class PhantomFarmRouteRecoveryTest
{
	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && "--offscreen-refusal011".equals(args[0]))
		{
			verifyOffscreenRefusal();
			return;
		}
		if (args.length == 1 && "--alternative011".equals(args[0]))
		{
			verifyAlternativeFarm();
			return;
		}
		if (args.length == 2 && "--offscreen-origin011".equals(args[0]))
		{
			verifyOffscreenOrigin(Path.of(args[1]));
			return;
		}
		final var from = new PhantomNavigationPoint(115016, -164632, -528, 0);
		final var corner = new PhantomNavigationPoint(115048, -164664, -528, 0);
		final var checkpoint = new PhantomNavigationPoint(115128, -165160, -384, 0);
		final var target = new PhantomNavigationPoint(116056, -175091, -1016, 0);
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var route = new PhantomNavigationRoute(PhantomNavigationRoute.Mode.COMPUTED, from, checkpoint, List.of(corner, checkpoint),
			new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1), 1, false, policy.maximumWaypoints(), policy.maximumRouteDistance());
		final var budget = new NativeWalkBudget(from, target, 1, 80, policy);
		PhantomAssertions.assertTrue(budget.admit(route, 2), "Proven nonfinal WALK must fit the existing budget.");
		PhantomAssertions.assertEquals(0, PhantomVisibleFarmTravel.nextWalkWaypoint(from, route.waypoints(), 0, policy.arrivalRadius(), (_, _) -> false),
			"Dispatch the captured corner when the shorter chord is blocked.");
		PhantomAssertions.assertFalse(budget.checkpoint(corner, 3), "An interior corner does not publish checkpoint arrival.");
		PhantomAssertions.assertEquals(1, PhantomVisibleFarmTravel.nextWalkWaypoint(corner, route.waypoints(), 0, policy.arrivalRadius(),
			(start, end) -> start.equals(corner) && end.equals(checkpoint)), "After native arrival at the corner the proven outgoing leg is usable.");
		PhantomAssertions.assertTrue(budget.checkpoint(checkpoint, 4), "The admitted checkpoint can be consumed once.");
		PhantomAssertions.assertFalse(budget.checkpoint(checkpoint, 5), "Duplicate checkpoint cannot renew time or budget.");
		final var returnToOrigin = new PhantomNavigationRoute(route.mode(), checkpoint, from, List.of(corner, from),
			route.geodataCapability(), 5, false, policy.maximumWaypoints(), policy.maximumRouteDistance());
		final var beforeLoop = budget.suspend(5);
		PhantomAssertions.assertFalse(budget.admit(returnToOrigin, 5), "Returning to the prior checkpoint must not reopen an episode.");
		PhantomAssertions.assertEquals(beforeLoop, budget.suspend(5), "A rejected loop must preserve the charged route, time and visited set.");
		PhantomAssertions.assertEquals(1, PhantomVisibleFarmTravel.nextWalkWaypoint(from, route.waypoints(), 0, policy.arrivalRadius(), (_, _) -> true),
			"A geometrically revalidated nearby-waypoint shortcut remains allowed.");
		verifyCheckpointRefusal();
		verifyFarmSettlement();
		verifyPassiveEvidence();
		System.out.println("PASS ordinary farm WALK keeps a mandatory corner and consumes the bounded checkpoint once");
	}

	private static void verifyOffscreenRefusal()
	{
		PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.isRecoverableFailure("catchup.offscreen.proof_or_residual_stale"),
			"A refused offscreen approach must return to the durable recovery owner.");
		PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.isRecoverableFailure("transaction.commit_outcome_unknown"),
			"An ambiguous transaction may not enter ordinary replanning.");
		System.out.println("PASS the historical owner classifies an offscreen refusal as recoverable");
	}

	private static void verifyAlternativeFarm()
	{
		final var planner = PhantomFarm074Checks.planner(true, true);
		final var state = PhantomFarm074Checks.state();
		final var predecessor = PhantomFarm074Checks.goal();
		final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(100, 100, 0, 0);
		final var examined = new java.util.HashSet<String>();
		final var alternate = planner.proposeVisibleRecovery(74, state, predecessor, 740074, 1, point, java.util.Set.of(), java.util.Set.of(), anchor ->
		{
			examined.add(anchor.id());
			return anchor.id().equals("farm.alternative");
		});
		PhantomAssertions.assertTrue(examined.contains("farm.far") && alternate.ready(),
			"The rejected near approach must leave the distinct farm within the existing native route range eligible.");
		PhantomAssertions.assertEquals("farm.alternative", alternate.spec().anchorId(), "A refused farm was selected again.");
		PhantomAssertions.assertEquals(predecessor.goalId(), alternate.goal().goalId(), "Recovery lost its root identity.");
		PhantomAssertions.assertEquals(predecessor.revision() + 1, alternate.goal().revision(), "Recovery must propose exactly one next revision.");
		final var unavailable = planner.proposeVisibleRecovery(74, state, predecessor, 740074, 1, point, java.util.Set.of(), java.util.Set.of(), _ -> false);
		PhantomAssertions.assertFalse(unavailable.ready(), "Refusal of every approach cannot grant a goal.");
		System.out.println("PASS the route owner proposes a distinct farm beyond the local straight-line limit");
	}

	private static void verifyOffscreenOrigin(Path module) throws Exception
	{
		final var witness = PhantomOffscreen092Checks.witness(module);
		final var state = PhantomOffscreen092Checks.state(witness.origin(), List.of());
		final var planner = PhantomOffscreen092Checks.planner(witness, false, true);
		final var previous = PhantomOffscreen092Checks.goal(witness, 1, 1);
		final var eligible = planner.replaceFromState(92, state, previous, 920092, 1);
		PhantomAssertions.assertTrue(eligible.ready() && PhantomOffscreenOutdoorTravel.pending(eligible.goal()),
			"A valid departure still supports offscreen travel.");
		final var at = state.position();
		final var offset = new PhantomBackgroundState.Position(at.x() + witness.origin().validationTolerance() + 1, at.y(), at.z(),
			at.instanceId(), at.heading(), at.committedAnchorId());
		final var displaced = state.after(state.progress(), state.vitals(), offset, state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
		PhantomAssertions.assertFalse(planner.remainsSuitable(displaced, eligible.goal()), "The saved anchor ID alone cannot retain a refused offscreen goal.");
		final var replacement = planner.replaceFromState(92, displaced, eligible.goal(), 920092, 2);
		PhantomAssertions.assertEquals("planner.target_or_route.absent", replacement.reasonKey(), "Reconsideration must leave the historical idle path available.");
		final var certified = PhantomOffscreen092Checks.planner(witness, true, true).replaceFromState(92, displaced, eligible.goal(), 920092, 2);
		PhantomAssertions.assertEquals(List.of("fixture092.certified"), certified.routeEdgeIds(), "Ordinary route planning must still work.");
		System.out.println("PASS offscreen selection and reconsideration use the saved departure");
	}

	private static void verifyCheckpointRefusal()
	{
		final var origin = new PhantomNavigationPoint(12207, 15907, -4552, 0);
		final var checkpoint = new PhantomNavigationPoint(15147, 15312, -4552, 0);
		final var arrival = new PhantomNavigationPoint(25304, 13256, -3648, 0);
		final var site = new PhantomFarmRouteBackoff.Site(7, 268484815, 38561671478300L, 1, 1, 1);
		final var rejected = new PhantomFarmRouteBackoff.Probe(site, origin, checkpoint);
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var request = new PhantomNavigationRequest(site.profileId(), origin, checkpoint, 1, 2, policy.maximumRouteDistance());
		final var refusal = BudgetProof.context(LimitKind.SEARCH_LIMIT, "CORRIDOR", request, policy);
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.dryRecoveryRequestMatches(rejected, refusal, arrival),
			"A search limit at the current checkpoint can trigger a bounded dry WALK to the original arrival.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.dryRecoveryRequestMatches(rejected, refusal.submission(
			new PhantomNavigationRequest(site.profileId(), origin, arrival, 1, 2, policy.maximumRouteDistance()), "CORRIDOR"), arrival),
			"A refusal for a different submitted destination cannot trigger dry recovery.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.dryRecoveryRequestMatches(rejected, refusal.submission(request, "EPISODE"), arrival),
			"An episode refusal cannot start another intermediate recovery.");
	}

	private static void verifyFarmSettlement()
	{
		final var evidence = new PlayerNativeEvidence(41, 7);
		final var attackTarget = new PlayerNativeEvidence.Target(42, 0, 1);
		final var foreignTarget = new PlayerNativeEvidence.Target(43, 0, 1);
		final var arrival = evidence.snapshot();
		evidence.damage(attackTarget, 10);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Damage leaves the incident open.");
		evidence.killed(attackTarget);
		evidence.reward(foreignTarget, 10, 1);
		evidence.rewardSettled(foreignTarget);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Unrelated reward leaves the incident open.");
		evidence.reward(attackTarget, 10, 1);
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "The reward writer must finish before incident release.");
		evidence.rewardSettled(attackTarget);
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 0), "Exact fresh kill and settled EXP/SP close the incident.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, evidence.snapshot(), 0), "A prior reward cannot close a later incident.");
		PhantomAssertions.assertFalse(PhantomVisibleFarmTravel.settledFarmAfter(evidence, arrival, 1), "A different instance cannot consume the farm receipt.");
	}

	private static void verifyPassiveEvidence()
	{
		final var evidence = new PlayerNativeEvidence(41, 7);
		evidence.phase(PlayerNativeEvidence.Phase.ROUTE, 10, 20);
		final var committed = evidence.currentSnapshot();
		PhantomAssertions.assertEquals(committed, evidence.currentSnapshot(), "A private status read cannot advance the evidence clock.");
		PhantomAssertions.assertFalse(committed.overflow(), "The status read must preserve the producer's deadline decision.");
		PhantomAssertions.assertTrue(evidence.snapshot(20).overflow(), "Native producer polling still checks the deadline.");
	}
}
