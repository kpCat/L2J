/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmFailureBinding;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.Disposition;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Focused checks over the caller's existing guarded native travel fixture. */
public final class PhantomM1TravelChecks
{
	private PhantomM1TravelChecks() { }

	public static void run(PhantomTestContext context, long profileId, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomHistoricalBackgroundPlanner planner, PhantomRelevanceSignalPort signals) throws Exception
	{
		deadlineBudget(context, profileId, profiles, materialization, background, route, goal, planner, signals);
		transientBudget(context, profileId, profiles, materialization, background, route, goal, planner, signals);
		protocolAndShutdown(context, profileId, profiles, materialization, background, route, goal, planner, signals, false);
		protocolAndShutdown(context, profileId, profiles, materialization, background, route, goal, planner, signals, true);
	}

	/** The caller reuses its native fixture factory at a factual GK source, before catch-up. */
	public static void missingGatekeeper(PhantomTestContext context, long profileId, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomHistoricalBackgroundPlanner planner, PhantomRelevanceSignalPort signals)
	{
		final var state = background.acquisitionSnapshot(profileId).orElseThrow();
		final var step = route.route(state.position().committedAnchorId(), PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElseThrow().getFirst();
		PhantomAssertions.assertEquals(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, step.type(), "Missing-GK check requires the actual first production route step.");
		PhantomAssertions.assertTrue(PhantomNormalGatekeeperTravel.matchesNative(step.gatekeeper()), "Missing-GK source is not a factual native teleport leg.");
		final var removed = new ArrayList<Npc>();
		final var clock = new AtomicLong(System.nanoTime());
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
		final var history = new PhantomHistoricalBackgroundService(profiles, new PhantomGoalStateStore(profiles), planner, background, materialization, clock::get);
		final var observed = new AtomicReference<PhantomVisibleFarmTravel.Failure>();
		final var binding = PhantomVisibleFarmFailureBinding.bind(history);
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals, (id, failure) -> { binding.accept(id, failure); observed.set(failure); }, clock::get);
		try
		{
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
			{
				final var player = action.player();
				final var leg = step.gatekeeper();
				PhantomAssertions.assertTrue((Math.hypot((long) player.getX() - leg.sourceX(), (long) player.getY() - leg.sourceY()) <= 100) && (Math.abs((long) player.getZ() - leg.sourceZ()) <= 100), "Missing-GK fixture is not naturally at the native source.");
				final var present = new ArrayList<Npc>();
				World.getInstance().forEachVisibleObjectInRange(player, Npc.class, 200, npc -> { if ((npc.getId() == leg.teleporterNpcId()) && (npc.getInstanceId() == player.getInstanceId())) { present.add(npc); } });
				for (var npc : present)
				{
					PhantomAssertions.assertTrue(npc.isSpawned() && !npc.isMoving(), "Missing-GK native teleporter is not a stationary spawned NPC.");
					removed.add(npc);
					PhantomAssertions.assertTrue(npc.decayMe(), "Missing-GK native temporary decay failed.");
					PhantomAssertions.assertTrue(!npc.isSpawned() && (World.getInstance().findObject(npc.getObjectId()) == null), "Missing-GK NPC remained in native World after decay.");
				}
				final var remaining = new AtomicReference<Npc>();
				World.getInstance().forEachVisibleObjectInRange(player, Npc.class, 200, npc -> { if ((npc.getId() == leg.teleporterNpcId()) && (npc.getInstanceId() == player.getInstanceId())) { remaining.set(npc); } });
				PhantomAssertions.assertEquals(null, remaining.get(), "Missing-GK native teleporter was not actually absent.");
				context.record("m1.travel.missingGatekeeper.absent", "npc=" + leg.teleporterNpcId() + " removedObjectIds=" + removed.stream().map(Npc::getObjectId).toList());
			}
			PhantomAssertions.assertTrue(navigation.start(), "Missing-GK stock navigation did not start.");
			travel.arrive(profileId, goal);
			travel.arrive(profileId, goal);
			clock.addAndGet(999_999_999L);
			travel.arrive(profileId, goal);
			context.record("m1.travel.missingGatekeeper.beforeCooldown", observed.get() == null ? travel.reason(profileId) : observed.get());
			PhantomAssertions.assertEquals(null, observed.get(), "Missing native GK exhausted attempts before the existing one-second cooldown.");
			clock.addAndGet(2L);
			travel.arrive(profileId, goal);
			PhantomAssertions.assertEquals(null, observed.get(), "Second due GK attempt prematurely terminated.");
			clock.addAndGet(1_000_000_001L);
			travel.arrive(profileId, goal);
			PhantomAssertions.assertTrue((observed.get() != null) && observed.get().reason().equals("travel.native_gatekeeper_absent") && (observed.get().attempts() == 3), "Three due native GK attempts did not resolve the same bounded attempt.");
			PhantomAssertions.assertEquals(Disposition.NATIVE_ACTION_REJECTED, observed.get().disposition(), "Missing native GK was falsely classified as bad geometry.");
			PhantomAssertions.assertFalse(observed.get().routeFailure(), "Missing native GK permanently invalidated geometry.");
			PhantomAssertions.assertFalse(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Missing GK terminal did not enter actual production feedback.");
			context.record("m1.travel.missingGatekeeper", "cooldown=true attempts=" + observed.get().attempts() + " step=" + step.id());
		}
		finally
		{
			try
			{
				travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
				navigation.beginStop(); navigation.finishStop();
			}
			finally
			{
				for (var npc : removed)
				{
					if (!npc.isSpawned()) { PhantomAssertions.assertTrue(npc.spawnMe(), "Missing-GK native teleporter restore failed."); }
					PhantomAssertions.assertTrue(npc.isSpawned() && (World.getInstance().findObject(npc.getObjectId()) == npc), "Missing-GK restore changed native object identity.");
				}
				context.record("m1.travel.missingGatekeeper.restored", "sameNativeObjectIds=" + removed.stream().map(Npc::getObjectId).toList());
			}
		}
	}

	private static void protocolAndShutdown(PhantomTestContext context, long profileId, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomHistoricalBackgroundPlanner planner, PhantomRelevanceSignalPort signals, boolean shutdown)
	{
		final var clock = new AtomicLong(System.nanoTime());
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
		final var history = new PhantomHistoricalBackgroundService(profiles, new PhantomGoalStateStore(profiles), planner, background, materialization, clock::get);
		final var observed = new AtomicReference<PhantomVisibleFarmTravel.Failure>();
		final var binding = PhantomVisibleFarmFailureBinding.bind(history);
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals, (id, failure) -> { binding.accept(id, failure); observed.set(failure); }, clock::get);
		try
		{
			PhantomAssertions.assertTrue(navigation.start(), "Protocol native navigation did not start.");
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Protocol fixture arrived without native continuation.");
			PhantomAssertions.assertEquals(1, navigation.progressTracker().activeAttempts(), "Protocol check did not acquire an actual stock progress tracker.");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { action.player().stopMove(null); }
			if (shutdown) { navigation.beginStop(); navigation.finishStop(); }
			else { navigation.progressTracker().cancelAll(); }
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Missing native tracker was reported as arrival.");
			context.record(shutdown ? "m1.travel.shutdownDisposition" : "m1.travel.protocol", observed.get() == null ? travel.reason(profileId) : observed.get());
			if (shutdown)
			{
				PhantomAssertions.assertEquals(null, observed.get(), "Ordinary navigation shutdown falsely created a terminal protocol incident.");
				PhantomAssertions.assertTrue(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Ordinary navigation shutdown poisoned the current native goal.");
			}
			else
			{
				PhantomAssertions.assertTrue(observed.get() != null, "Unexpected RUNNING tracker loss silently restarted native travel.");
				PhantomAssertions.assertEquals(Disposition.PROTOCOL_VIOLATION, observed.get().disposition(), "Unexpected tracker loss was treated as transient or geometry.");
				PhantomAssertions.assertFalse(observed.get().routeFailure(), "Protocol loss became a geometric exclusion.");
				PhantomAssertions.assertFalse(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Protocol loss silently repaired the same goal/lifetime.");
			}
		}
		finally
		{
			travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
			navigation.beginStop(); navigation.finishStop();
		}
	}

	private static void deadlineBudget(PhantomTestContext context, long profileId, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomHistoricalBackgroundPlanner planner, PhantomRelevanceSignalPort signals) throws Exception
	{
		final var clock = new AtomicLong(System.nanoTime());
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
		final var history = new PhantomHistoricalBackgroundService(profiles, new PhantomGoalStateStore(profiles), planner, background, materialization, clock::get);
		PhantomAssertions.assertTrue(history.status(profileId).isEmpty(), "Deadline check requires the caller's pre-catchup native fixture.");
		final var productionFeedback = PhantomVisibleFarmFailureBinding.bind(history);
		final var observed = new AtomicReference<PhantomVisibleFarmTravel.Failure>();
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals, (id, failure) -> { productionFeedback.accept(id, failure); observed.set(failure); }, clock::get);
		final Player player;
		final boolean previousCasting;
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow())
		{
			player = action.player();
			previousCasting = player.isCastingNow();
			player.setCastingNow(true);
		}
		try
		{
			PhantomAssertions.assertTrue(navigation.start(), "Native deadline navigation did not start.");
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Busy native actor arrived before route completion.");
			clock.addAndGet(navigation.policy().maximumAttemptDurationNanos() + 1);
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Expired native journey was reported as arrived.");
			PhantomAssertions.assertTrue(observed.get() != null && observed.get().reason().equals("travel.journey_deadline"), "Native busy did not expose its deadline terminal.");
			PhantomAssertions.assertEquals(Disposition.NATIVE_ACTION_REJECTED, observed.get().disposition(), "Busy deadline falsely classified healthy geometry.");
			PhantomAssertions.assertTrue((observed.get().player() == player) && (observed.get().profileId() == profileId) && (observed.get().epoch() == materialization.find(profileId).orElseThrow().materializedAtNanos()), "Terminal lost its exact native lifetime.");
			PhantomAssertions.assertFalse(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Actual shared production binding discarded native deadline replan feedback.");
			final var terminal = observed.get();
			final var stale = new PhantomVisibleFarmTravel.Failure(profileId, player, terminal.epoch() + 1, goal, terminal.stepId(), terminal.reason(), terminal.disposition(), terminal.startedNanos(), terminal.attempts());
			PhantomAssertions.assertFalse(history.recordVisibleTravelFailure(profileId, stale), "Old epoch terminal captured the current native owner.");
			final var revision = revised(goal);
			final var wrongGoal = new PhantomVisibleFarmTravel.Failure(profileId, player, terminal.epoch(), revision, terminal.stepId(), terminal.reason(), terminal.disposition(), terminal.startedNanos(), terminal.attempts());
			PhantomAssertions.assertFalse(history.recordVisibleTravelFailure(profileId, wrongGoal), "Unpublished revision terminal changed current replan ownership.");
			final long terminalSequence = travel.lastFailure(profileId).sequence();
			for (int retry = 0; retry < 4; retry++)
			{
				clock.addAndGet(TimeUnit.SECONDS.toNanos(121));
				PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Same revision restarted a completed travel attempt.");
			}
			PhantomAssertions.assertEquals(terminalSequence, travel.lastFailure(profileId).sequence(), "Journey replacement reset the same-revision terminal budget.");
			context.record("m1.travel.sharedDeadline", "samePlayer=" + player.getObjectId() + " terminalSequence=" + terminalSequence);
		}
		finally
		{
			player.setCastingNow(previousCasting);
			travel.beforeMaterialize(profileId, player.getObjectId());
			navigation.beginStop(); navigation.finishStop();
		}
	}

	private static void transientBudget(PhantomTestContext context, long profileId, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel route, PhantomGoal goal, PhantomHistoricalBackgroundPlanner planner, PhantomRelevanceSignalPort signals)
	{
		final var clock = new AtomicLong(System.nanoTime());
		final long started = clock.get();
		// This native service is deliberately not RUNNING; no alternate geometry backend is used.
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new L2jNavigationBackend(), worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
		final var history = new PhantomHistoricalBackgroundService(profiles, new PhantomGoalStateStore(profiles), planner, background, materialization, clock::get);
		final var observed = new AtomicReference<PhantomVisibleFarmTravel.Failure>();
		final var productionFeedback = PhantomVisibleFarmFailureBinding.bind(history);
		final var travel = new PhantomVisibleFarmTravel(materialization, background, route, navigation, _ -> true, signals, (id, failure) -> { productionFeedback.accept(id, failure); observed.set(failure); }, clock::get);
		try
		{
			for (int attempt = 0; attempt < 3; attempt++)
			{
				PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Stopped native navigation was reported as route success.");
				clock.addAndGet(TimeUnit.SECONDS.toNanos(1) + 1);
			}
			PhantomAssertions.assertEquals(null, observed.get(), "Transient service failure immediately banned a healthy route.");
			PhantomAssertions.assertTrue(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Transient retry polluted route exclusions.");
			clock.set(started + navigation.policy().maximumAttemptDurationNanos() + 1);
			PhantomAssertions.assertFalse(travel.arrive(profileId, goal), "Expired service retry budget restarted a native journey.");
			PhantomAssertions.assertTrue(observed.get() != null && observed.get().reason().equals("travel.journey_deadline") && (observed.get().startedNanos() == started), "Exhausted transient retry did not surface its original bounded deadline.");
			PhantomAssertions.assertEquals(Disposition.NATIVE_ACTION_REJECTED, observed.get().disposition(), "Exhausted service deadline was treated as geometry proof.");
			PhantomAssertions.assertFalse(history.replanVisibleFarmIfOutgrown(profileId, goal, null), "Exhausted service budget did not enter actual bounded production resolution.");
			context.record("m1.travel.transientBudget", "attempts=" + observed.get().attempts() + " originalDeadline=true routeFailure=" + observed.get().routeFailure());
		}
		finally
		{
			travel.beforeMaterialize(profileId, materialization.find(profileId).orElseThrow().characterObjectId());
			navigation.beginStop(); navigation.finishStop();
		}
	}

	private static PhantomGoal revised(PhantomGoal goal)
	{
		return new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision() + 1);
	}
}
