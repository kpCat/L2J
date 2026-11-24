/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.BudgetProof;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;
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
		if (args.length == 1 && "--idle-farm011".equals(args[0]))
		{
			verifyIdleFarm();
			return;
		}
		if (args.length == 2 && "--offscreen-origin011".equals(args[0]))
		{
			verifyOffscreenOrigin(Path.of(args[1]));
			return;
		}
		if (args.length == 7 && Set.of("--geo-recovery011", "--geo-episode-required011").contains(args[0]))
		{
			verifyGeoRecovery(point(args, 1), point(args, 4), "--geo-episode-required011".equals(args[0]));
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
		final var running = PhantomBackgroundCatchupState.Status.RUNNING;
		PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.needsRecoveryApproach(false, true, false, running),
			"A terminal native route still needs the verified alternative and Return when ordinary replanning has no candidate.");
		PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.needsRecoveryApproach(false, false, false, running),
			"A target selection failure alone must not start spatial recovery.");
		PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.needsRecoveryApproach(true, true, false, running),
			"An active spatial recovery already owns verification and must not be restarted.");
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

	private static void verifyIdleFarm()
	{
		final var planner = PhantomFarm074Checks.planner(false);
		final var state = PhantomFarm074Checks.state();
		final var idle = planner.idleFromState(74, state, PhantomFarm074Checks.goal(), 740074, 1).goal();
		final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(100, 100, 0, 0);
		final var excluded = Set.of("101@farm.local");
		PhantomAssertions.assertFalse(planner.replanVisibleLocal(74, state, idle, 740074, 2, point, excluded, Set.of()).ready(),
			"A disconnected distant farm cannot be reached through the old local/topology plan.");
		final var walk = planner.proposeVisibleRecovery(74, state, idle, 740074, 2, point, excluded, Set.of(),
			anchor -> anchor.id().equals("farm.far"));
		PhantomAssertions.assertTrue(walk.ready(), "Historical idle must be able to offer a different farm to the native WALK owner.");
		PhantomAssertions.assertEquals("farm.far", walk.spec().anchorId(), "Recovery lost the lawful farming target.");
		PhantomAssertions.assertEquals(idle.revision() + 1, walk.goal().revision(), "Recovery did not prepare the immediate successor.");
		PhantomAssertions.assertEquals(idle.subject(), walk.goal().subject(), "Recovery changed the character owner.");
		PhantomAssertions.assertTrue(walk.routeEdgeIds().isEmpty(), "A proposal must not invent an offscreen edge.");
		System.out.println("PASS historical idle proposes an alternative farm to the native WALK owner");
	}

	private static PhantomNavigationPoint point(String[] args, int offset)
	{
		return new PhantomNavigationPoint(Integer.parseInt(args[offset]), Integer.parseInt(args[offset + 1]), Integer.parseInt(args[offset + 2]), 0);
	}

	/** Stock terrain comparison. There are no World Players or runtime PVE threats in this fixture. */
	private static void verifyGeoRecovery(PhantomNavigationPoint from, PhantomNavigationPoint target, boolean checkpointRequired) throws Exception
	{
		final var game = Path.of(".").toRealPath();
		PhantomAssertions.assertTrue(game.getFileName().toString().startsWith("geo-") && Files.isRegularFile(game.getParent().resolve(".codex-owned.json")),
			"Expected an isolated GeoData fixture.");
		ServerConfig.DATAPACK_ROOT = game.toFile();
		GeoEngineConfig.load();
		final var backend = new L2jNavigationBackend();
		System.out.println("SOURCE_SURFACE " + backend.walkSurface(from, 16));
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var navigation = new PhantomNavigationService(policy, backend, work -> { work.run(); return true; }, System::nanoTime, new PhantomMetrics());
		PhantomAssertions.assertTrue(navigation.start(), "The isolated navigation owner must start.");
		final var rejectedChord = checkpointRequired ? Set.of(new PhantomWalkCorridorPlanner.Edge(from, target)) : Set.<PhantomWalkCorridorPlanner.Edge>of();
		try
		{
			for (boolean episode : List.of(false, true))
			{
				final long now = Math.max(1, System.nanoTime());
				final var request = new PhantomNavigationRequest(episode ? 2 : 1, from, target, now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance());
				final var submission = navigation.submitSoloWalk(request, episode, Set.of(), rejectedChord);
				final var result = submission.immediateResult() != null ? submission.immediateResult() : navigation.consume(submission.requestId()).orElseThrow();
				final var route = result.route();
				System.out.println((episode ? "EPISODE" : "CORRIDOR") + " " + result.status() + " checkpoint=" + (route == null ? "-" : route.destination())
					+ " " + result.proof().diagnostic());
				if (checkpointRequired)
				{
					PhantomAssertions.assertTrue(episode ? route != null && !route.destination().equals(target) : result.proof().kind() == LimitKind.CHORD_DISTANCE,
						"The distant dry farm needs an episode checkpoint beyond the corridor limit.");
				}
				if (episode && route != null)
				{
					final var budget = new NativeWalkBudget(from, target, now, 100, policy);
					PhantomAssertions.assertEquals(target, result.proof().submittedDestination(), "Checkpoint belongs to a different farm.");
					PhantomAssertions.assertTrue(budget.admit(route, System.nanoTime()), "Checkpoint is outside the ordinary WALK budget.");
				}
			}
		}
		finally
		{
			navigation.beginStop();
			PhantomAssertions.assertTrue(navigation.finishStop(), "The isolated navigation owner must stop.");
		}
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
