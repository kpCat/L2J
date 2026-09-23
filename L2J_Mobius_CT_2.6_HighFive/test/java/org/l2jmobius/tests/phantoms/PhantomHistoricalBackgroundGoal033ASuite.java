/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.HistoricalIdentity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Identity;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Kind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;

/** Focused Goal033A causal historical Background integration gate. */
public final class PhantomHistoricalBackgroundGoal033ASuite implements PhantomTestSuite
{
	private static final long SEED = 33003312L;
	private static final long FROM_MINUTE = 1_000_000L;
	private static final Path POPULATION_CATALOG = Path.of("data/phantoms/population/high-five-population-v1.xml");

	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomProfileRepository _profiles;
	private PhantomPopulationCatalog _catalog;
	private final List<ManagedSnapshot> _managed = new ArrayList<>();
	private long _creationOrdinal;

	@Override
	public String id()
	{
		return "historical-background-goal033a";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal033A deterministic seed changed.");
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
		_profiles = PhantomProfileRepository.open();
		_catalog = PhantomPopulationCatalog.load(POPULATION_CATALOG, ZoneId.of("UTC"));
		context.record("goal033a.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("goal033a.knowledgeHash", _production.knowledge().snapshot().combinedHash());
		context.record("goal033a.topologyHash", _production.topology().snapshot().canonicalHash());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			cleanupManaged();
		}
		finally
		{
			try
			{
				if (_production != null)
				{
					_production.close();
				}
			}
			finally
			{
				if (_environment != null)
				{
					_environment.shutdown();
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-strict-codec-and-disjoint-operation-identity", this::testCodecAndIdentity);
		registry.add("02-canonical-planner-baseline-and-fences", this::testPlannerBaselineAndFences);
		registry.add("03-atomic-fault-replay-and-restart", this::testAtomicFaultReplayAndRestart);
		registry.add("04-stale-hash-and-reset-cascade", this::testStaleHashAndResetCascade);
		registry.add("05-stale-after-commit-recovers-across-restart", this::testStaleAfterCommitRecovers);
		registry.add("06-indivisible-object-cap-is-bounded", this::testIndivisibleObjectCap);
		registry.add("07-item-reservation-retry-and-canonical-mismatch", this::testItemReservationAndMismatch);
		registry.add("08-current-authority-recaptures-canonical-baseline", this::testCurrentAuthorityRecapturesCanonicalBaseline);
		registry.add("09-unplanned-topology-block-reopens-on-generation", this::testUnplannedTopologyBlockReopens);
		registry.add("10-legacy-item-conflict-retries-from-cursor", this::testLegacyItemConflictRetries);
	}
	private void testCodecAndIdentity(PhantomTestContext context)
	{
		final var generation = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), _production.authority()).generation();
		final String requestId = "a".repeat(64);
		final String planIdentity = "b".repeat(64);
		final PhantomBackgroundCatchupState pending = new PhantomBackgroundCatchupState(Status.PENDING, requestId, SEED, FROM_MINUTE, FROM_MINUTE + 2, FROM_MINUTE, 0, 0, 1, generation.knowledgeGeneration(), generation.topologyGeneration(), 0, 0, "", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
		final PhantomBackgroundCatchupState running = pending.withPlan(101, 0, 0, planIdentity, generation.knowledgeGeneration(), generation.topologyGeneration()).running();
		final PhantomBackgroundCatchupState complete = running.advanceTo(FROM_MINUTE + 1).advanceTo(FROM_MINUTE + 2);
		final PhantomBackgroundCatchupStateCodec codec = new PhantomBackgroundCatchupStateCodec();
		PhantomAssertions.assertEquals(complete, codec.decode(codec.encode(complete)), "background.catchup v1 codec changed state.");
		final byte[] trailing = Arrays.copyOf(codec.encode(complete), codec.encode(complete).length + 1);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(trailing), "Catch-up codec accepted trailing bytes.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> running.advanceTo(running.cursorEpochMinute()), "Catch-up cursor accepted a non-positive interval.");

		final PhantomBackgroundOperationKey live = new PhantomBackgroundOperationKey(1, 2, 101, 0, 1, 1, ActionKind.FARM, 10, "farm.anchor", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes());
		final HistoricalIdentity historical = new HistoricalIdentity(requestId, 1, 0, FROM_MINUTE, FROM_MINUTE + 1, planIdentity);
		final PhantomBackgroundOperationKey catchup = new PhantomBackgroundOperationKey(1, 2, 101, 0, 0, 0, ActionKind.HISTORICAL_FARM, 10, "farm.anchor", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), null, historical);
		PhantomAssertions.assertFalse(live.digest().equals(catchup.digest()), "Live and historical operation identities collided.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomBackgroundOperationKey(1, 2, 101, 0, 0, 0, ActionKind.FARM, 10, "farm.anchor", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), null, historical), "Historical identity was accepted by a live action kind.");
		context.record("goal033a.catchupCodecBytes", codec.encode(complete).length);
		context.record("goal033a.historicalOperationDigest", catchup.digest());
	}

	private void testPlannerBaselineAndFences(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed());
		try (RuntimeHarness runtime = openRuntime(managed.profile().profileId(), new PhantomBackgroundTransaction()))
		{
			final long profileId = managed.profile().profileId();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().materialize(profileId).status(), "Ordinary materialization preflight failed before catch-up claim.");
			PhantomAssertions.assertEquals(ResultStatusCode.NORMAL_MATERIALIZED, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Catch-up began while the profile was normally materialized.");
			try (var action = runtime.materialization().tryAcquireAction(profileId).orElseThrow())
			{
				final var directPlan = runtime.planner().planInitial(profileId, action.player(), context.seed(), 0);
				PhantomAssertions.assertTrue(directPlan.ready(), "Direct canonical planner failed before baseline persist: " + directPlan.reasonKey());
			}
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().dematerialize(profileId).status(), "Ordinary preflight dematerialization failed.");

			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Canonical historical baseline did not initialize: " + begun.reason());
			PhantomAssertions.assertEquals(Status.RUNNING, begun.snapshot().state().status(), "Initialized catch-up did not enter RUNNING.");
			PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty(), "Historical baseline left a runtime Player materialized.");
			final PhantomBackgroundState baseline = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertTrue((baseline.state() == PhantomBackgroundState.State.READY) || (baseline.state() == PhantomBackgroundState.State.DEAD), "afterStore did not capture a canonical READY/DEAD baseline.");
			final PhantomGoal goal = runtime.goals().load(profileId).orElseThrow().goal();
			final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
			PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goal.status(), "Planner did not persist an ACTIVE farm.background goal.");
			assertPlannerEvidence(baseline, spec);

			final var replanned = runtime.planner().replan(profileId, baseline, goal, context.seed(), 1);
			final var restartedPlan = runtime.planner().replan(profileId, baseline, goal, context.seed(), 1);
			PhantomAssertions.assertTrue(replanned.ready(), "Own-state replan did not find current real data: " + replanned.reasonKey());
			PhantomAssertions.assertEquals(replanned, restartedPlan, "Planner result changed across a deterministic restart.");
			PhantomAssertions.assertEquals(goal.goalId(), replanned.goal().goalId(), "Replan replaced the durable goal identity.");
			PhantomAssertions.assertEquals(goal.revision() + 1, replanned.goal().revision(), "Replan did not advance the goal revision once.");

			PhantomAssertions.assertFalse(runtime.historical().permitsNormalOperation(profileId), "PENDING/RUNNING catch-up admitted normal Decision work.");
			PhantomAssertions.assertEquals(ResultStatus.CATCHUP_FENCED, runtime.materialization().materialize(profileId).status(), "RUNNING catch-up admitted NORMAL materialization.");
			PhantomAssertions.assertEquals(ResultStatus.CATCHUP_FENCED, runtime.materialization().materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, "0".repeat(64)).status(), "Historical maintenance accepted the wrong owner claim.");

			final var advanced = runtime.historical().advance(profileId, 4, 4);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, advanced.status(), "Bounded historical intervals did not complete: " + advanced.reason());
			PhantomAssertions.assertEquals(4, advanced.advancedIntervals(), "Catch-up did not execute exactly one interval per simulated minute.");
			PhantomAssertions.assertEquals(Status.COMPLETE, advanced.snapshot().state().status(), "Catch-up did not stop exactly at its target cursor.");
			PhantomAssertions.assertTrue(runtime.historical().permitsNormalOperation(profileId), "COMPLETE catch-up did not reopen normal Decision work.");
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, runtime.transaction().reconcileVerifyPending(profileId, baseline.identity().characterObjectId()).status(), "COMPLETE catch-up left an unreconciled canonical Background state.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().materialize(profileId).status(), "COMPLETE catch-up did not reopen NORMAL materialization.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().dematerialize(profileId).status(), "Post-catch-up ordinary dematerialization failed.");
			context.record("goal033a.selectedNpcId", spec.npcId());
			context.record("goal033a.selectedFarmAnchor", spec.anchorId());
			context.record("goal033a.initialIngressAnchor", baseline.position().committedAnchorId());
		}
	}
	private void testAtomicFaultReplayAndRestart(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 1);
		final long profileId = managed.profile().profileId();
		final AtomicReference<FaultPoint> fault = new AtomicReference<>();
		final PhantomBackgroundTransaction faulting = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ObjectIdAllocator.production(), point ->
		{
			if (fault.compareAndSet(point, null))
			{
				throw new IllegalStateException("goal033a." + point.name().toLowerCase());
			}
		});
		RuntimeHarness runtime = openRuntime(profileId, faulting);
		try
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Atomic fixture baseline failed: " + begun.reason());
			final PhantomBackgroundCatchupStore catchupStore = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot initialCatchup = catchupStore.load(profileId).orElseThrow();
			final var initialGoal = runtime.goals().load(profileId).orElseThrow();
			final PhantomBackgroundState initialBackground = runtime.transaction().load(profileId).state();
			boolean branchSwitchPlanSelected = false;
			branchSwitchSearch:
			for (var departureAnchor : _production.topology().snapshot().anchorById().values().stream().sorted(Comparator.comparing(value -> value.id())).toList())
			{
				final Position departurePosition = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(departureAnchor, initialBackground.position().heading()).orElse(null);
				if (departurePosition == null)
				{
					continue;
				}
				final Clock initialClock = initialBackground.clock();
				final PhantomBackgroundState oneIntervalTravel = copyWithPositionAndClock(initialBackground, departurePosition, new Clock(initialClock.rngState(), 1, initialClock.residualEncounterMillis()));
				for (long planOrdinal = 1; planOrdinal <= 64; planOrdinal++)
				{
					final var candidate = runtime.planner().replan(profileId, oneIntervalTravel, initialGoal.goal(), context.seed(), planOrdinal);
					if (!candidate.ready() || (candidate.routeEdgeIds().size() != 1))
					{
						continue;
					}
					final var travel = _production.authority().advanceTravel(oneIntervalTravel, candidate.spec(), PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
					if (!oneIntervalTravel.position().committedAnchorId().equals(candidate.spec().anchorId()) && travel.mutated() && travel.position().committedAnchorId().equals(candidate.spec().anchorId()))
					{
						final PhantomBackgroundCatchupState replanned = initialCatchup.state().withPlan(candidate.goal().goalId(), candidate.goal().revision(), planOrdinal, candidate.planIdentity(), candidate.generation().knowledgeGeneration(), candidate.generation().topologyGeneration());
						catchupStore.replacePlan(profileId, initialCatchup, replanned, initialGoal, candidate.goal());
						moveCanonicalFixture(oneIntervalTravel.identity().characterObjectId(), departurePosition);
						final var backgroundComponent = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
						_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, backgroundComponent.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(oneIntervalTravel));
						branchSwitchPlanSelected = true;
						break branchSwitchSearch;
					}
				}
			}
			PhantomAssertions.assertTrue(branchSwitchPlanSelected, "Atomic replay fixture found no one-interval travel-to-farm branch switch.");
			final byte[] catchupBeforeFault = componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE);
			final byte[] backgroundBeforeFault = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);

			fault.set(FaultPoint.AFTER_CATCHUP_STATE_WRITE);
			final var rolledBack = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertFalse(rolledBack.successful(), "Pre-commit catch-up fault was reported as success.");
			final byte[] catchupAfterFault = componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE);
			PhantomAssertions.assertTrue(Arrays.equals(catchupBeforeFault, catchupAfterFault), "Pre-commit failure changed catch-up state. before=" + new PhantomBackgroundCatchupStateCodec().decode(catchupBeforeFault) + ", after=" + new PhantomBackgroundCatchupStateCodec().decode(catchupAfterFault));
			PhantomAssertions.assertTrue(Arrays.equals(backgroundBeforeFault, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Pre-commit failure changed canonical Background/player state.");

			final Snapshot expected = catchupStore.load(profileId).orElseThrow();
			final PhantomBackgroundCatchupState next = expected.state().advanceTo(expected.state().cursorEpochMinute() + 1);
			final PhantomGoal goal = runtime.goals().load(profileId).orElseThrow().goal();
			fault.set(FaultPoint.AFTER_OPERATION_COMMIT);
			final var ambiguous = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, ambiguous.status(), "Committed ambiguous outcome was not reconciled: " + ambiguous.reason());
			PhantomAssertions.assertEquals(expected.state().cursorEpochMinute() + 1, ambiguous.snapshot().state().cursorEpochMinute(), "Ambiguous outcome did not observe exactly one cursor advance.");
			final byte[] catchupAfterCommit = componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE);
			final byte[] backgroundAfterCommit = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);

			final var duplicate = runtime.background().advanceHistorical(profileId, goal, expected, next);
			PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.IDEMPOTENT, duplicate.status(), "Duplicate historical identity was not idempotent: " + duplicate.reason());
			PhantomAssertions.assertTrue(Arrays.equals(catchupAfterCommit, componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE)), "Duplicate operation advanced the cursor twice.");
			PhantomAssertions.assertTrue(Arrays.equals(backgroundAfterCommit, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Duplicate operation changed EXP/items/resources twice.");

			runtime.close();
			runtime = openRuntime(profileId, new PhantomBackgroundTransaction());
			PhantomAssertions.assertTrue(Arrays.equals(catchupAfterCommit, componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE)), "Restart changed the durable catch-up state.");
			PhantomAssertions.assertTrue(Arrays.equals(backgroundAfterCommit, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Restart changed the canonical Background state.");
			PhantomAssertions.assertEquals(goal, runtime.goals().load(profileId).orElseThrow().goal(), "Restart changed the durable goal identity/revision.");
			var completed = runtime.historical().status(profileId).orElseThrow();
			while (completed.state().status() != Status.COMPLETE)
			{
				final Snapshot beforeCatchup = completed;
				final PhantomBackgroundState beforeBackground = runtime.transaction().load(profileId).state();
				final PhantomGoal beforeGoal = runtime.goals().load(profileId).orElseThrow().goal();
				final var advanced = runtime.historical().advance(profileId, 1, 1);
				PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, advanced.status(), "Split/restart continuation failed: " + advanced.reason());
				PhantomAssertions.assertEquals(1, advanced.advancedIntervals(), "Restart continuation did not commit exactly one interval.");
				completed = advanced.snapshot();
				final PhantomGoal afterGoal = runtime.goals().load(profileId).orElseThrow().goal();
				PhantomAssertions.assertEquals(beforeGoal.goalId(), afterGoal.goalId(), "Own-progression replan replaced the durable goal identity.");
				PhantomAssertions.assertTrue((afterGoal.revision() == beforeGoal.revision()) || (afterGoal.revision() == (beforeGoal.revision() + 1)), "One interval changed the goal revision by more than one.");
				assertContinuousInterval(profileId, beforeCatchup.state(), completed.state(), beforeBackground, runtime.transaction().load(profileId).state(), afterGoal);
			}
			PhantomAssertions.assertEquals(FROM_MINUTE + 4, completed.state().cursorEpochMinute(), "Restart continuation overshot or undershot target cursor.");
			context.record("goal033a.preCommitFault", FaultPoint.AFTER_CATCHUP_STATE_WRITE);
			context.record("goal033a.ambiguousFault", FaultPoint.AFTER_OPERATION_COMMIT);
			context.record("goal033a.restartFinalReceipt", runtime.transaction().load(profileId).state().receipt().operationKey());
		}
		finally
		{
			runtime.close();
		}
	}

	private void assertContinuousInterval(long profileId, PhantomBackgroundCatchupState beforeCatchup, PhantomBackgroundCatchupState afterCatchup, PhantomBackgroundState before, PhantomBackgroundState after, PhantomGoal goal)
	{
		PhantomAssertions.assertEquals(beforeCatchup.cursorEpochMinute() + 1, afterCatchup.cursorEpochMinute(), "Restart continuation cursor is not one monotonic interval.");
		PhantomAssertions.assertEquals(beforeCatchup.intervalOrdinal() + 1, afterCatchup.intervalOrdinal(), "Restart continuation interval ordinal is not monotonic.");
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		final boolean deadIdle = before.state() == PhantomBackgroundState.State.DEAD;
		final boolean travel = !deadIdle && !before.position().committedAnchorId().equals(spec.anchorId());
		Map<Integer, Long> expectedItemDeltas = Map.of();
		if (deadIdle)
		{
			PhantomAssertions.assertEquals(before.progress(), after.progress(), "DEAD idle interval changed level/EXP/SP across restart.");
			PhantomAssertions.assertEquals(before.vitals(), after.vitals(), "DEAD idle interval changed vitals/death across restart.");
			PhantomAssertions.assertEquals(before.position(), after.position(), "DEAD idle interval changed position across restart.");
			PhantomAssertions.assertEquals(before.clock(), after.clock(), "DEAD idle interval consumed RNG/time across restart.");
			PhantomAssertions.assertEquals(before.autoGetSkills(), after.autoGetSkills(), "DEAD idle interval changed auto-get skills across restart.");
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.DEAD, after.state(), "DEAD idle interval revived the Phantom.");
		}
		else if (travel)
		{
			final var expected = _production.authority().advanceTravel(before, spec, PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS);
			PhantomAssertions.assertTrue(expected.mutated(), "Continuous restart oracle could not advance factual travel.");
			PhantomAssertions.assertEquals(before.progress(), after.progress(), "Travel interval changed level/EXP/SP across restart.");
			PhantomAssertions.assertEquals(before.vitals(), after.vitals(), "Travel interval changed vitals/death across restart.");
			PhantomAssertions.assertEquals(expected.position(), after.position(), "Travel position differs from continuous deterministic transition.");
			PhantomAssertions.assertEquals(expected.clock(), after.clock(), "Travel RNG/time differs from continuous deterministic transition.");
			PhantomAssertions.assertEquals(before.autoGetSkills(), after.autoGetSkills(), "Travel interval changed auto-get skills across restart.");
		}
		else
		{
			final var input = _production.authority().farmInput(before, spec);
			final var expected = new PhantomBackgroundModel().evaluate(new BatchRequest(before, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			PhantomAssertions.assertTrue(expected.mutated(), "Continuous restart oracle produced no canonical farm transition.");
			PhantomAssertions.assertEquals(expected.progress(), after.progress(), "Farm level/EXP/SP differs from continuous deterministic transition.");
			assertCanonicalVitals(expected.vitals(), after.vitals());
			PhantomAssertions.assertEquals(before.position(), after.position(), "Farm interval changed canonical position across restart.");
			PhantomAssertions.assertEquals(new Clock(expected.nextRngState(), 0, 0), after.clock(), "Farm RNG differs from continuous deterministic transition.");
			PhantomAssertions.assertEquals(_production.authority().autoGetSkills(before.identity(), expected.progress().level()), after.autoGetSkills(), "Farm auto-get skills differ from continuous deterministic transition.");
			PhantomAssertions.assertEquals(expected.dead() ? PhantomBackgroundState.State.DEAD : PhantomBackgroundState.State.READY, after.state(), "Farm death state differs from continuous deterministic transition.");
			expectedItemDeltas = expected.inventoryDelta().itemDeltas();
		}
		final TreeSet<Integer> tracked = new TreeSet<>(before.inventory().mutableItemIds());
		tracked.addAll(after.inventory().mutableItemIds());
		for (int itemId : tracked)
		{
			PhantomAssertions.assertEquals(before.inventory().itemCount(itemId) + expectedItemDeltas.getOrDefault(itemId, 0L), after.inventory().itemCount(itemId), "Tracked inventory/resource delta differs from continuous deterministic transition for item " + itemId + ".");
		}
		final HistoricalIdentity historical = new HistoricalIdentity(afterCatchup.requestId(), afterCatchup.generation(), afterCatchup.intervalOrdinal() - 1, afterCatchup.cursorEpochMinute() - 1, afterCatchup.cursorEpochMinute(), afterCatchup.planIdentity());
		final ActionKind kind = deadIdle ? ActionKind.HISTORICAL_DEAD_IDLE : travel ? ActionKind.HISTORICAL_TRAVEL : ActionKind.HISTORICAL_FARM;
		final PhantomBackgroundOperationKey expectedKey = new PhantomBackgroundOperationKey(profileId, before.identity().characterObjectId(), goal.goalId(), goal.revision(), 0, 0, kind, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _production.authority().hashes(), null, historical);
		PhantomAssertions.assertEquals(expectedKey.digest(), after.receipt().operationKey(), "Historical receipt identity differs from continuous deterministic transition.");
		PhantomAssertions.assertFalse(after.receipt().expectedAfterHash().isBlank(), "Historical restart receipt lost its canonical after-hash.");
		PhantomAssertions.assertEquals(goal.goalId(), afterCatchup.goalId(), "Catch-up cursor and goal identity diverged.");
		PhantomAssertions.assertEquals(goal.revision(), afterCatchup.goalRevision(), "Catch-up cursor and goal revision diverged.");
	}

	private static void assertCanonicalVitals(PhantomBackgroundState.Vitals expected, PhantomBackgroundState.Vitals actual)
	{
		PhantomAssertions.assertEquals(Math.round(expected.currentHp()), (long) actual.currentHp(), "Farm HP differs from continuous deterministic transition.");
		PhantomAssertions.assertEquals(expected.maximumHp(), actual.maximumHp(), "Farm maximum HP changed across restart.");
		PhantomAssertions.assertEquals(Math.round(expected.currentMp()), (long) actual.currentMp(), "Farm MP differs from continuous deterministic transition.");
		PhantomAssertions.assertEquals(expected.maximumMp(), actual.maximumMp(), "Farm maximum MP changed across restart.");
		PhantomAssertions.assertEquals(Math.round(expected.currentCp()), (long) actual.currentCp(), "Farm CP differs from continuous deterministic transition.");
		PhantomAssertions.assertEquals(expected.maximumCp(), actual.maximumCp(), "Farm maximum CP changed across restart.");
	}
	private void testStaleHashAndResetCascade(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 2);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 2, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Stale-hash fixture baseline failed: " + begun.reason());
			final PhantomBackgroundCatchupStore store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot current = store.load(profileId).orElseThrow();
			final Hashes hashes = current.state().authorityHashes();
			final Hashes staleHashes = new Hashes("stale-" + hashes.knowledge(), hashes.topology(), hashes.progression(), hashes.commerce());
			store.replace(profileId, current, copyWithHashes(current.state(), staleHashes));
			final var recovered = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, recovered.status(), "Stale authority hash did not recover from canonical state: " + recovered.reason());
			PhantomAssertions.assertEquals(current.state().cursorEpochMinute() + 1, recovered.snapshot().state().cursorEpochMinute(), "Stale recovery did not advance exactly one interval.");
			PhantomAssertions.assertEquals(hashes, recovered.snapshot().state().authorityHashes(), "Stale recovery did not install current authority hashes.");
			PhantomAssertions.assertFalse(runtime.historical().permitsNormalOperation(profileId), "Incomplete catch-up silently reopened normal work.");
		}

		deleteProfileOnly(profileId);
		PhantomAssertions.assertTrue(_profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).isEmpty(), "Profile reset did not cascade-delete pending catch-up state.");
		context.record("goal033a.staleHashReason", "catchup.authority_hash_or_generation_stale");
		context.record("goal033a.resetCascadeProfile", profileId);
	}

	private void testStaleAfterCommitRecovers(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 3);
		final long profileId = managed.profile().profileId();
		RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction());
		try
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Recovery fixture baseline failed.");
			final var first = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, first.status(), "Recovery fixture did not commit its first interval.");
			final PhantomBackgroundCatchupStore store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot committed = store.load(profileId).orElseThrow();
			final byte[] canonicalAfterCommit = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final Hashes hashes = committed.state().authorityHashes();
			final Hashes stale = new Hashes("stale-" + hashes.knowledge(), hashes.topology(), hashes.progression(), hashes.commerce());
			final Snapshot failed = store.replace(profileId, committed, copyWithHashes(committed.state(), stale).failed("catchup.authority_hash_or_generation_stale"));
			PhantomAssertions.assertEquals(committed.state().cursorEpochMinute(), failed.state().cursorEpochMinute(), "Stale fixture advanced the cursor.");
			PhantomAssertions.assertTrue(Arrays.equals(canonicalAfterCommit, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Stale fixture changed committed canonical rewards.");
			runtime.close();
			runtime = openRuntime(profileId, new PhantomBackgroundTransaction());
			final var recovered = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, recovered.status(), "Failed stale request did not recover after restart: " + recovered.reason());
			PhantomAssertions.assertEquals(committed.state().cursorEpochMinute() + 1, recovered.snapshot().state().cursorEpochMinute(), "Recovery did not commit exactly the next interval.");
			PhantomAssertions.assertEquals(committed.state().intervalOrdinal() + 1, recovered.snapshot().state().intervalOrdinal(), "Recovery replayed an interval ordinal.");
			PhantomAssertions.assertEquals(committed.state().deterministicSeed(), recovered.snapshot().state().deterministicSeed(), "Recovery changed request lineage seed.");
			final byte[] backgroundAfterRecovery = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			runtime.close();
			runtime = openRuntime(profileId, new PhantomBackgroundTransaction());
			PhantomAssertions.assertTrue(Arrays.equals(backgroundAfterRecovery, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Restart replayed canonical rewards after recovery.");
			PhantomAssertions.assertEquals(recovered.snapshot().state().cursorEpochMinute(), runtime.historical().status(profileId).orElseThrow().state().cursorEpochMinute(), "Restart changed the recovered cursor.");
			final var continued = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, continued.status(), "Recovery continuation failed after second restart: " + continued.reason());
			PhantomAssertions.assertEquals(recovered.snapshot().state().cursorEpochMinute() + 1, continued.snapshot().state().cursorEpochMinute(), "Second restart replayed the recovered interval.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testIndivisibleObjectCap(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 4);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 16, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Object-cap fixture baseline failed: " + begun.reason());
			final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(runtime.goals().load(profileId).orElseThrow().goal());
			for (int step = 0; (step < 16) && !runtime.transaction().load(profileId).state().position().committedAnchorId().equals(spec.anchorId()); step++)
			{
				PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Object-cap fixture could not reach its factual farm anchor.");
			}
			final PhantomBackgroundState baseline = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertEquals(spec.anchorId(), baseline.position().committedAnchorId(), "Object-cap fixture did not reach its factual farm anchor.");
			final var input = _production.authority().farmInput(baseline, spec);
			final Target original = input.target();
			final List<Drop> drops = new ArrayList<>();
			for (int ordinal = 0; ordinal < 17; ordinal++)
			{
				drops.add(new Drop(57 + ordinal, ordinal, 0, 100, 100, 1, 1, 1, null, 1, 100, true, 0));
			}
			final Target oversized = new Target(original.npcId(), original.level(), original.normalMonster(), original.maximumHp(), original.maximumMp(), original.physicalOffense(), original.magicOffense(), original.physicalDefense(), original.magicDefense(), original.attackSpeed(), original.castSpeed(), original.baseExperience(), original.baseSkillPoints(), drops, 17);
			final var batch = new PhantomBackgroundModel().evaluate(new BatchRequest(baseline, oversized, input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			PhantomAssertions.assertTrue(batch.indivisibleObjectCap(), "A single oversized encounter was not classified as bounded unsupported input.");
			PhantomAssertions.assertFalse(batch.mutated(), "An indivisible object-cap encounter partially mutated rewards.");
			final Drop oneObject = new Drop(500000, 0, 0, 100, 100, 1, 1, 1, null, 1, 100, false, 0);
			final Target cumulative = new Target(original.npcId(), original.level(), true, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, List.of(oneObject), 1);
			final var bounded = new PhantomBackgroundModel().evaluate(new BatchRequest(baseline, cumulative, input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			PhantomAssertions.assertEquals(PhantomBackgroundModel.ResultReason.OBJECT_CAP, bounded.reason(), "Cumulative object safety cap did not stop the bounded batch.");
			PhantomAssertions.assertTrue(bounded.mutated(), "A completed prefix was discarded at the object safety cap.");
			PhantomAssertions.assertFalse(bounded.indivisibleObjectCap(), "A completed prefix was mistaken for an indivisible encounter.");
			PhantomAssertions.assertEquals(baseline, runtime.transaction().load(profileId).state(), "An uncommitted object-cap encounter changed canonical state.");
		}
	}

	private void testItemReservationAndMismatch(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 5);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 2, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Item conflict fixture baseline failed: " + begun.reason());
			final PhantomBackgroundState baseline = runtime.transaction().load(profileId).state();
			final PhantomGoal goal = runtime.goals().load(profileId).orElseThrow().goal();
			final int objectId = baseline.identity().characterObjectId();
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, objectId, goal.goalId(), goal.revision(), 1, 1, ActionKind.FARM, PhantomBackgroundGoalSpec.parse(goal).npcId(), PhantomBackgroundGoalSpec.parse(goal).anchorId(), PhantomBackgroundState.MODEL_VERSION, baseline.hashes());
			final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(baseline, goal, key, baseline.progress(), baseline.vitals(), baseline.position(), baseline.clock(), Map.of(57, 1L), baseline.autoGetSkills());
			final PhantomEconomyPolicy policy = PhantomEconomyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/economy/high-five-economy-v1.xml"));
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(policy);
			final long now = System.currentTimeMillis();
			final PhantomEconomyOperation operation = new PhantomEconomyOperation(new Identity(profileId, objectId, goal.goalId(), goal.revision(), 1, "economy.live0030c", 1, 1), Kind.SELF_CRAFT, State.PREPARED, PhantomEconomyOperation.sha256("authority:live0030c"), PhantomEconomyOperation.sha256("intent:live0030c"), PhantomEconomyOperation.utf8Payload("before"), PhantomEconomyOperation.utf8Payload("intent"), now, now, now + 120000, 0);
			final Reservation held = new Reservation(profileId, objectId, baseline.identity().classIndex(), ResourceKind.ITEM_COUNT, 0, 57, 1, baseline.inventory().itemCount(57), 0, "INVENTORY");
			try
			{
				PhantomAssertions.assertTrue(reservations.start(), "Item conflict reservation service did not start.");
				PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, List.of(held)).status(), "Item conflict reservation was not acquired.");
				PhantomEconomyConflictPort.install(reservations);
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.ITEM_BUSY, runtime.transaction().execute(command).status(), "Temporary item reservation was classified as a canonical mismatch.");
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.RETRY, runtime.background().mapTransactionFailure(PhantomBackgroundTransaction.Status.ITEM_BUSY).status(), "Temporary item reservation did not preserve retry semantics.");
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.RETRY, runtime.background().mapTransactionFailure(PhantomBackgroundTransaction.Status.ITEM_EXPECTED_COUNT_STALE).status(), "Stale acquisition expected count did not preserve retry semantics.");
				PhantomAssertions.assertEquals(PhantomBackgroundService.ServiceState.RUNNING, runtime.background().snapshot().state(), "Temporary item reservation failed the whole Background service.");
				PhantomAssertions.assertEquals(baseline, runtime.transaction().load(profileId).state(), "Blocked item claim changed canonical rewards.");
				reservations.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), new PhantomEconomyOperation.Audit(PhantomEconomyOperation.Result.ERROR, "operation.conflict", new byte[0]));
				final PhantomBackgroundTransaction.Command mismatch = new PhantomBackgroundTransaction.Command(baseline, goal, key, baseline.progress(), baseline.vitals(), baseline.position(), baseline.clock(), Map.of(999999, -1L), baseline.autoGetSkills());
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.ITEM_CONFLICT, runtime.transaction().execute(mismatch).status(), "Genuine nonmutable inventory mismatch did not fail closed.");
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.INCONSISTENT, runtime.background().mapTransactionFailure(PhantomBackgroundTransaction.Status.ITEM_CONFLICT).status(), "Canonical inventory mismatch was classified as retryable.");
			}
			finally
			{
				PhantomEconomyConflictPort.uninstall(reservations);
				reservations.shutdown(System.currentTimeMillis());
			}
		}
	}

	private void testCurrentAuthorityRecapturesCanonicalBaseline(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 6);
		final long profileId = managed.profile().profileId();
		RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction());
		try
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Authority refresh fixture baseline failed.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Authority refresh fixture did not commit its first interval.");
			final Snapshot committed = runtime.historical().status(profileId).orElseThrow();
			final PhantomBackgroundState canonical = runtime.transaction().load(profileId).state();
			final Hashes oldHashes = canonical.hashes();
			final Hashes currentHashes = new Hashes("new-" + oldHashes.knowledge(), oldHashes.topology(), oldHashes.progression(), oldHashes.commerce());
			runtime.close();
			runtime = openRuntime(profileId, new PhantomBackgroundTransaction(), authorityWithHashes(currentHashes));
			final var recovered = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, recovered.status(), "Stale canonical baseline was not recaptured: " + recovered.reason());
			PhantomAssertions.assertEquals(committed.state().cursorEpochMinute() + 1, recovered.snapshot().state().cursorEpochMinute(), "Canonical refresh replayed or skipped an interval.");
			PhantomAssertions.assertEquals(committed.state().deterministicSeed(), recovered.snapshot().state().deterministicSeed(), "Canonical refresh changed lineage seed.");
			PhantomAssertions.assertEquals(currentHashes, runtime.transaction().load(profileId).state().hashes(), "Canonical refresh did not install current authority hashes.");
			PhantomAssertions.assertTrue(runtime.transaction().load(profileId).state().progress().experience() >= canonical.progress().experience(), "Canonical refresh rewound committed EXP.");
			PhantomAssertions.assertTrue(runtime.transaction().load(profileId).state().progress().skillPoints() >= canonical.progress().skillPoints(), "Canonical refresh rewound committed SP.");
		}
		finally
		{
			runtime.close();
		}
	}

	private void testUnplannedTopologyBlockReopens(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 7);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final var current = runtime.planner().generation();
			final PhantomBackgroundCatchupState blocked = new PhantomBackgroundCatchupState(Status.FAILED_REPLAN_REQUIRED, "f".repeat(64), context.seed(), FROM_MINUTE, FROM_MINUTE + 4, FROM_MINUTE, 0, 0, 1, current.knowledgeGeneration(), current.topologyGeneration() + 1, 0, 0, "", PhantomBackgroundState.MODEL_VERSION, current.authorityHashes(), "planner.target_or_route.absent");
			final PhantomBackgroundCatchupStore store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			store.claim(profileId, blocked);
			PhantomAssertions.assertTrue(runtime.historical().status(profileId).orElseThrow().state().blocksNormalOperation(), "Unplanned topology block admitted normal work.");
			final var recovered = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, recovered.status(), "Factual topology generation did not resume the same request: " + recovered.reason());
			PhantomAssertions.assertEquals(blocked.requestId(), recovered.snapshot().state().requestId(), "Topology recovery replaced the durable request identity.");
			PhantomAssertions.assertEquals(blocked.deterministicSeed(), recovered.snapshot().state().deterministicSeed(), "Topology recovery changed the lineage seed.");
			PhantomAssertions.assertEquals(FROM_MINUTE + 1, recovered.snapshot().state().cursorEpochMinute(), "Topology recovery replayed or skipped a minute.");
		}
	}

	private void testLegacyItemConflictRetries(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 8);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Legacy item-conflict fixture baseline failed.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Legacy item-conflict fixture did not commit its first interval.");
			final PhantomBackgroundCatchupStore store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot committed = store.load(profileId).orElseThrow();
			final byte[] canonicalBefore = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			store.replace(profileId, committed, committed.state().failed("transaction.item_conflict"));
			PhantomAssertions.assertTrue(Arrays.equals(canonicalBefore, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Legacy item-conflict fixture changed canonical rewards.");
			final var retried = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, retried.status(), "Legacy transient item conflict did not retry: " + retried.reason());
			PhantomAssertions.assertEquals(committed.state().cursorEpochMinute() + 1, retried.snapshot().state().cursorEpochMinute(), "Legacy item-conflict retry replayed the committed minute.");
			PhantomAssertions.assertEquals(committed.state().deterministicSeed(), retried.snapshot().state().deterministicSeed(), "Legacy item-conflict retry changed lineage seed.");
		}
	}

	private PhantomBackgroundAuthority authorityWithHashes(Hashes currentHashes)
	{
		final PhantomBackgroundAuthority delegate = _production.authority();
		return new PhantomBackgroundAuthority()
		{
			@Override
			public Hashes hashes()
			{
				return currentHashes;
			}

			@Override
			public PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
			{
				return copyBackgroundWithHashes(delegate.capture(profileId, player, goal, previous), currentHashes);
			}

			@Override
			public PlanningSnapshot planningSnapshot(Player player)
			{
				return delegate.planningSnapshot(player);
			}

			@Override
			public boolean matchesRuntime(Player player, PhantomBackgroundState state)
			{
				return delegate.matchesRuntime(player, state);
			}

			@Override
			public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal)
			{
				return delegate.farmInput(copyBackgroundWithHashes(state, delegate.hashes()), goal);
			}

			@Override
			public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis)
			{
				return delegate.advanceTravel(copyBackgroundWithHashes(state, delegate.hashes()), goal, elapsedBudgetMillis);
			}

			@Override
			public Optional<Position> canonicalRecoveryPosition(int x, int y, int z, int instanceId, int heading)
			{
				return delegate.canonicalRecoveryPosition(x, y, z, instanceId, heading);
			}

			@Override
			public List<PhantomBackgroundState.AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level)
			{
				return delegate.autoGetSkills(identity, level);
			}
		};
	}

	private static PhantomBackgroundState copyBackgroundWithHashes(PhantomBackgroundState state, Hashes hashes)
	{
		return new PhantomBackgroundState(state.state(), state.identity(), state.progress(), state.vitals(), state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), hashes);
	}
	private void assertPlannerEvidence(PhantomBackgroundState baseline, PhantomBackgroundGoalSpec spec)
	{
		final var npc = _production.knowledge().snapshot().npcById().get(spec.npcId());
		PhantomAssertions.assertTrue((npc != null) && npc.attackable() && npc.targetable(), "Planner selected a non-authoritative monster.");
		PhantomAssertions.assertTrue(Math.abs(npc.level() - baseline.progress().level()) <= 2, "Planner selected a target outside the Phantom's own level window.");
		final var anchor = _production.topology().findAnchor(spec.anchorId()).orElseThrow();
		PhantomAssertions.assertEquals(PhantomTopologyAnchorRole.FARMING, anchor.role(), "Planner selected a non-FARMING anchor.");
		PhantomAssertions.assertTrue(_production.knowledge().snapshot().spawnAreasByNpc().getOrDefault(spec.npcId(), List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId())), "Selected FARMING anchor has no real spawn evidence.");
		final var route = _production.topology().routeHint(baseline.position().committedAnchorId(), spec.anchorId()).orElseThrow();
		String currentAnchor = baseline.position().committedAnchorId();
		for (String edgeId : route.edgeIds())
		{
			final var edge = _production.topology().snapshot().edgeById().get(edgeId);
			PhantomAssertions.assertTrue((edge != null) && edge.backgroundEligible() && (edge.mode() == PhantomTopologyEdgeMode.BACKGROUND) && edge.fromAnchorId().equals(currentAnchor) && _production.topology().isTraversable(edgeId), "Planner route contains a non-factual BACKGROUND segment.");
			currentAnchor = edge.toAnchorId();
		}
		PhantomAssertions.assertEquals(spec.anchorId(), currentAnchor, "Planner route does not terminate at the selected FARMING anchor.");
	}

	private ManagedSnapshot createManaged(long seed)
	{
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		ManagedSnapshot snapshot = store.createShell(1, ++_creationOrdinal, seed);
		final int ownerIndex = _managed.size();
		_managed.add(snapshot);
		for (int step = 0; (step < 20) && (snapshot.state().state() != PhantomPopulationState.State.READY) && (snapshot.state().state() != PhantomPopulationState.State.INCONSISTENT); step++)
		{
			final var result = store.advanceCreation(snapshot);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Goal033A population creation became inconsistent: " + result.snapshot().state().lastFailure());
			snapshot = result.snapshot();
			_managed.set(ownerIndex, snapshot);
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Goal033A population creation did not reach READY.");
		PhantomAssertions.assertEquals(snapshot.profile().characterObjectId(), snapshot.state().actualCharacterObjectId(), "Goal033A managed profile link differs from the created character.");
		return snapshot;
	}

	private RuntimeHarness openRuntime(long profileId, PhantomBackgroundTransaction transaction)
	{
		return openRuntime(profileId, transaction, _production.authority());
	}

	private RuntimeHarness openRuntime(long profileId, PhantomBackgroundTransaction transaction, PhantomBackgroundAuthority authority)
	{
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		final PhantomMaterializationLifecycleBridge lifecycle = new PhantomMaterializationLifecycleBridge();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point ->
		{
		}, lifecycle, 5_000, 10_000);
		final AtomicReference<PhantomMaterializationService> materializationRef = new AtomicReference<>(materialization);
		final PhantomBackgroundService background = new PhantomBackgroundService(_profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
		PhantomAssertions.assertTrue(background.start(), "Goal033A Background service did not start.");
		final PhantomHistoricalBackgroundPlanner planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), authority);
		final PhantomHistoricalBackgroundService historical = new PhantomHistoricalBackgroundService(_profiles, goals, planner, background, materialization);
		lifecycle.install(PhantomMaterializationLifecyclePort.chain(historical, background));
		PhantomAssertions.assertTrue(materialization.start(), "Goal033A materialization service did not start.");
		return new RuntimeHarness(profileId, goals, transaction, background, materialization, planner, historical);
	}

	private byte[] componentPayload(long profileId, String componentType)
	{
		return _profiles.findComponent(profileId, componentType).orElseThrow().payload();
	}

	private static PhantomBackgroundCatchupState copyWithHashes(PhantomBackgroundCatchupState state, Hashes hashes)
	{
		return new PhantomBackgroundCatchupState(state.status(), state.requestId(), state.deterministicSeed(), state.fromEpochMinute(), state.targetEpochMinute(), state.cursorEpochMinute(), state.planOrdinal(), state.intervalOrdinal(), state.generation(), state.knowledgeGeneration(), state.topologyGeneration(), state.goalId(), state.goalRevision(), state.planIdentity(), state.modelVersion(), hashes, state.failureReason());
	}

	private static PhantomBackgroundState copyWithPositionAndClock(PhantomBackgroundState state, Position position, Clock clock)
	{
		return new PhantomBackgroundState(state.state(), state.identity(), state.progress(), state.vitals(), position, state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), clock, state.receipt(), state.hashes());
	}

	private static void moveCanonicalFixture(int characterObjectId, Position position) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE characters SET x=?,y=?,z=?,heading=? WHERE charId=?"))
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Goal033A position fixture touched a non-test database.");
			statement.setInt(1, position.x());
			statement.setInt(2, position.y());
			statement.setInt(3, position.z());
			statement.setInt(4, position.heading());
			statement.setInt(5, characterObjectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Goal033A position fixture character is absent.");
		}
	}

	private void deleteProfileOnly(long profileId)
	{
		PhantomProfile current = _profiles.find(profileId).orElse(null);
		if (current == null)
		{
			return;
		}
		if (current.characterObjectId() != null)
		{
			current = _profiles.updateCharacterLink(profileId, current.rowVersion(), null);
		}
		_profiles.delete(profileId, current.rowVersion());
	}

	private void cleanupManaged() throws Exception
	{
		for (int index = _managed.size() - 1; index >= 0; index--)
		{
			final ManagedSnapshot saved = _managed.get(index);
			final long profileId = saved.profile().profileId();
			PhantomPopulationState state = saved.state();
			PhantomProfile profile = _profiles.find(profileId).orElse(null);
			if (profile != null)
			{
				final var component = _profiles.findComponent(profileId, PhantomPopulationState.COMPONENT_TYPE).orElse(null);
				if (component != null)
				{
					state = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec().decode(component.payload());
				}
				if (profile.characterObjectId() != null)
				{
					profile = _profiles.updateCharacterLink(profileId, profile.rowVersion(), null);
				}
				_profiles.delete(profileId, profile.rowVersion());
			}
			final Integer objectId = state.actualCharacterObjectId() != null ? state.actualCharacterObjectId() : state.expectedCharacterObjectId();
			if (objectId != null)
			{
				final Player worldPlayer = World.getInstance().getPlayer(objectId);
				if (worldPlayer != null)
				{
					_environment.cleanupLoadedPlayer(worldPlayer);
				}
				GameClient.deleteCharByObjId(objectId);
			}
			try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				statement.setString(1, state.reservedAccount());
				statement.executeUpdate();
			}
		}
		_managed.clear();
	}

	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override
			public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal)
			{
				return SignalDelivery.ACCEPTED;
			}

			@Override
			public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
			{
				return SignalDelivery.ACCEPTED;
			}
		};
	}

	private static final class RuntimeHarness implements AutoCloseable
	{
		private final long _profileId;
		private final PhantomGoalStateStore _goals;
		private final PhantomBackgroundTransaction _transaction;
		private final PhantomBackgroundService _background;
		private final PhantomMaterializationService _materialization;
		private final PhantomHistoricalBackgroundPlanner _planner;
		private final PhantomHistoricalBackgroundService _historical;
		private boolean _closed;

		private RuntimeHarness(long profileId, PhantomGoalStateStore goals, PhantomBackgroundTransaction transaction, PhantomBackgroundService background, PhantomMaterializationService materialization, PhantomHistoricalBackgroundPlanner planner, PhantomHistoricalBackgroundService historical)
		{
			_profileId = profileId;
			_goals = goals;
			_transaction = transaction;
			_background = background;
			_materialization = materialization;
			_planner = planner;
			_historical = historical;
		}

		private PhantomGoalStateStore goals()
		{
			return _goals;
		}

		private PhantomBackgroundTransaction transaction()
		{
			return _transaction;
		}

		private PhantomBackgroundService background()
		{
			return _background;
		}

		private PhantomMaterializationService materialization()
		{
			return _materialization;
		}

		private PhantomHistoricalBackgroundPlanner planner()
		{
			return _planner;
		}

		private PhantomHistoricalBackgroundService historical()
		{
			return _historical;
		}

		@Override
		public void close()
		{
			if (_closed)
			{
				return;
			}
			_closed = true;
			if (_materialization.find(_profileId).isPresent())
			{
				_materialization.dematerialize(_profileId);
			}
			_materialization.shutdown();
			_background.beginStop();
			if (!_background.finishStop())
			{
				throw new IllegalStateException("Goal033A Background service did not stop cleanly.");
			}
		}
	}
}
