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
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
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
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.ResultStatusCode;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
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
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;

/** Focused Goal033A causal historical Background integration gate. */
public class PhantomHistoricalBackgroundGoal033ASuite implements PhantomTestSuite
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
		registry.add("11-d2-managed-dwarf-normal-gk", this::testD2ManagedDwarfNormalGatekeeper);
		registry.add("12-d2-paid-gk-atomic-replay", this::testD2PaidGatekeeperAtomicReplay);
		registry.add("13-complete-stale-authority-renews-next-due", this::testCompleteStaleAuthorityRenewsNextDue);
		registry.add("14-durable-object-cap-resumes-same-cursor", context -> testRecoverableFailure(context, "model.object_cap_indivisible"));
		registry.add("15-legacy-authority-retries-same-request", context -> testRecoverableFailure(context, "catchup.authority.unsupported"));
		registry.add("16-durable-planner-absence-retries", context -> testRecoverableFailure(context, "planner.target_or_route.absent"));
		registry.add("17-unknown-history-stays-failed", context -> testRecoverableFailure(context, "catchup.authority.unknown"));
		registry.add("18-no-route-idle-is-atomic-and-replayable", this::testNoRouteHistoricalIdle);
		registry.add("19-planner-lower-tier-and-exclusion", this::testPlannerLowerTier);
		registry.add("20-durable-authority-hash-refresh", context -> testRecoverableFailure(context, "catchup.authority.authority_stale"));
		registry.add("21-durable-position-refresh", context -> testRecoverableFailure(context, "catchup.authority.position_stale"));
		registry.add("22-durable-target-exclusion", context -> testRecoverableFailure(context, "catchup.authority.target_stale"));
		registry.add("23-durable-loadout-replan", context -> testRecoverableFailure(context, "catchup.authority.resource_stale"));
		registry.add("24-complete-renewal-rebuilds-absent-goal-and-background", this::testRenewalWithoutGoalAndBackground);
		registry.add("25-complete-renewal-rebuilds-background-with-goal", this::testRenewalWithoutBackground);
		registry.add("26-complete-renewal-replans-stale-goal", this::testRenewalWithStaleGoal);
		registry.add("27-complete-renewal-classifies-invalid-background", this::testRenewalWithInvalidBackground);
		registry.add("28-complete-renewal-recovers-orphaned-materialized-background", this::testRenewalWithOrphanedMaterializedBackground);
		registry.add("29-ready-local-demand-rebuilds-history-then-native-player", context -> testReadyLocalDemandRebuild(context, false, false, false));
		registry.add("29a-legacy-recovered-local-demand-native-player", context -> testReadyLocalDemandRebuild(context, true, false, false));
		registry.add("29b-latent-recovered-local-demand-native-player", context -> testReadyLocalDemandRebuild(context, false, true, false));
		registry.add("29c-running-pending-recovery-to-linked-native-player", context -> testReadyLocalDemandRebuild(context, false, false, true));
		registry.add("29d-synthetic-world-human-to-linked-native-player", context -> testReadyLocalDemandRebuild(context, false, false, false, true));
		registry.add("30-complete-renewal-rebuilds-absent-goal-with-background", this::testRenewalWithoutGoalWithBackground);
		registry.add("31-complete-renewal-rejects-unverifiable-goalless-dead-baseline", this::testRenewalWithoutGoalWithDeadBackground);
		registry.add("32-running-verify-pending-reconciles-without-cursor-loss", this::testRunningVerifyPending);
		registry.add("33-failed-recovery-verify-pending-resumes-same-request", this::testFailedRecoveryVerifyPending);
		registry.add("34-failed-recovery-background-missing", context -> testPrerequisiteRecovery(context, "background", true));
		registry.add("35-failed-recovery-goal-missing", context -> testPrerequisiteRecovery(context, "goal", true));
		registry.add("36-failed-recovery-goal-revision-stale", context -> testPrerequisiteRecovery(context, "revision", true));
		registry.add("37-midstream-goal-missing-remains-fail-closed", this::testMidstreamGoalMissing);
		registry.add("38-running-recovery-background-missing", context -> testPrerequisiteRecovery(context, "background", false));
		registry.add("39-running-recovery-goal-missing", context -> testPrerequisiteRecovery(context, "goal", false));
	}

	private void testRunningVerifyPending(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 32).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "VERIFY_PENDING fixture did not begin history.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "VERIFY_PENDING fixture did not commit its first interval.");
			final Snapshot before = runtime.historical().status(profileId).orElseThrow();
			final var component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			final var codec = new PhantomBackgroundStateCodec();
			final PhantomBackgroundState committed = codec.decode(component.payload());
			_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, codec.encode(committed.withState(PhantomBackgroundState.State.VERIFY_PENDING)));
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.VERIFY_PENDING, runtime.transaction().load(profileId).state().state(), "TEST did not expose a committed pending marker.");
			final var resumed = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, resumed.status(), "RUNNING history rejected a verifiable pending commit: " + resumed.reason());
			PhantomAssertions.assertEquals(before.state().requestId(), resumed.snapshot().state().requestId(), "Pending reconciliation replaced request ownership.");
			PhantomAssertions.assertEquals(before.state().cursorEpochMinute() + 1, resumed.snapshot().state().cursorEpochMinute(), "Pending reconciliation did not advance the same cursor once.");
		}
	}

	private void testFailedRecoveryVerifyPending(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 33).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Failed pending fixture did not begin history.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Failed pending fixture did not commit its first interval.");
			final Snapshot before = runtime.historical().status(profileId).orElseThrow();
			final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			store.replace(profileId, before, before.state().failed("transaction.item_conflict"));
			final var component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			final var codec = new PhantomBackgroundStateCodec();
			_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, codec.encode(codec.decode(component.payload()).withState(PhantomBackgroundState.State.VERIFY_PENDING)));
			final var resumed = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, resumed.status(), "Failed recovery rejected a verifiable pending commit: " + resumed.reason());
			PhantomAssertions.assertEquals(before.state().requestId(), resumed.snapshot().state().requestId(), "Failed pending recovery replaced the request.");
			PhantomAssertions.assertEquals(before.state().cursorEpochMinute() + 1, resumed.snapshot().state().cursorEpochMinute(), "Failed pending recovery lost the cursor.");
		}
	}

	private void testPrerequisiteRecovery(PhantomTestContext context, String missing, boolean durableFailure) throws Exception
	{
		final long profileId = createManaged(context.seed() + missing.hashCode() + (durableFailure ? 0 : 40)).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Prerequisite fixture did not begin history.");
			if (!"goal".equals(missing)) { PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Prerequisite fixture did not commit its prefix."); }
			final Snapshot before = runtime.historical().status(profileId).orElseThrow();
			if (durableFailure)
			{
				final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
				store.replace(profileId, before, before.state().failed("transaction.item_conflict"));
			}
			switch (missing)
			{
				case "background" -> deleteComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE);
				case "goal" -> deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
				case "revision" ->
				{
					final var current = runtime.goals().load(profileId).orElseThrow();
					final var state = runtime.background().acquisitionSnapshot(profileId).orElseThrow();
					final var plan = runtime.planner().replaceFromState(profileId, state, current.goal(), context.seed(), before.state().planOrdinal() + 1);
					PhantomAssertions.assertTrue(plan.ready(), "Stale revision fixture had no deterministic replacement.");
					runtime.goals().replace(profileId, current.rowVersion(), plan.goal());
				}
				default -> throw new IllegalArgumentException(missing);
			}
			final var resumed = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, resumed.status(), "Known " + missing + " recovery remained terminal: " + resumed.reason());
			PhantomAssertions.assertEquals(before.state().requestId(), resumed.snapshot().state().requestId(), "Known " + missing + " recovery replaced the request.");
			PhantomAssertions.assertEquals(before.state().cursorEpochMinute() + 1, resumed.snapshot().state().cursorEpochMinute(), "Known " + missing + " recovery lost the cursor.");
		}
	}

	private void testMidstreamGoalMissing(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 37).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Midstream fixture did not begin.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Midstream fixture did not commit a prefix.");
			final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot before = store.load(profileId).orElseThrow();
			final Snapshot failed = store.replace(profileId, before, before.state().failed("transaction.item_conflict"));
			deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
			final var rejected = runtime.historical().advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.REPLAN_REQUIRED, rejected.status(), "Midstream missing goal silently recreated a possibly different plan.");
			PhantomAssertions.assertEquals("catchup.recovery.goal_missing", rejected.reason(), "Midstream missing goal lost its typed boundary.");
			PhantomAssertions.assertEquals(failed, store.load(profileId).orElseThrow(), "Midstream missing goal rewrote the accepted cursor.");
		}
	}

	private void testReadyLocalDemandRebuild(PhantomTestContext context, boolean legacyRecovery, boolean latentRecovery, boolean runningPendingRecovery) throws Exception
	{
		testReadyLocalDemandRebuild(context, legacyRecovery, latentRecovery, runningPendingRecovery, false);
	}

	private void testReadyLocalDemandRebuild(PhantomTestContext context, boolean legacyRecovery, boolean latentRecovery, boolean runningPendingRecovery, boolean syntheticHuman) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 29);
		final long profileId = managed.profile().profileId();
		final var ecologyCatalog = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), _catalog, PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
		final long day = Instant.parse("2026-01-05T00:00:00Z").getEpochSecond() / 60;
		long from = 0;
		for (int offset = 0; offset < 7 * 1440; offset += 5)
		{
			final long minute = day + offset;
			final var schedule = _catalog.evaluate(managed.state().scheduleTemplate(), Instant.ofEpochSecond(minute * 60), ZoneOffset.UTC, managed.state().schedulePhaseMinutes());
			if ((schedule.state() == PhantomActivityState.ACTIVE) && (schedule.nextBoundary().getEpochSecond() >= (minute + 20) * 60)) { from = minute; break; }
		}
		PhantomAssertions.assertTrue(from > 0, "TEST profile has no bounded calendar-online interval.");
		final long completedAt = from;
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()); var humanSession = new org.l2jmobius.gameserver.localplay.LocalPlaySyntheticHumanSession(_environment.observer().objectId(), _environment.observer().characterName()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, completedAt - 4, completedAt, context.seed()).status(), "Local-demand fixture did not begin history.");
			final Snapshot old = runtime.historical().advance(profileId, 4, 4).snapshot();
			PhantomAssertions.assertEquals(Status.COMPLETE, old.state().status(), "Local-demand fixture has no completed window.");
			deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
			deleteComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			if (syntheticHuman)
			{
				final Player human = humanSession.start();
				human.teleToLocation(managed.state().creationX(), managed.state().creationY(), managed.state().creationZ()); human.onTeleported();
				PhantomAssertions.assertTrue(humanSession.valid(), "Linked TEST synthetic human was not an ordinary World Player.");
				context.record("synthetic.linkedHuman", org.l2jmobius.gameserver.localplay.LocalPlayPilotActions.snapshot(human));
			}
			recordLinkedTransition(context, "01-human-demand", profileId, runtime, "READY linked, calendar online", "local demand pending");
			final var assignment = ecologyCatalog.assign(PhantomPopulationEcologyState.Preset.LIVING, managed.state().populationGeneration(), managed.state().creationOrdinal(), context.seed(), completedAt, 0, 0, managed.state().scheduleTemplate());
			final var ecologyState = new PhantomPopulationEcologyState(assignment.catalogHash(), assignment.preset(), assignment.ecologyGeneration(), assignment.assignmentOrdinal(), assignment.assignedAtEpochMinute(), assignment.virtualJoinEpochMinute(), completedAt, completedAt, PhantomPopulationEcologyState.Pace.OUTLIER, 10000, assignment.productiveBlockMinutes(), assignment.personality(), assignment.initialSocialTraits(), assignment.scheduleTemplate(), assignment.disposition(), assignment.turnoverEligibleEpochMinute(), assignment.replacesProfileId(), "", 0, 0, 0, "");
			final var ecologyStore = new PhantomPopulationEcologyStore(_profiles);
			ecologyStore.insert(profileId, ecologyState);
			final boolean[] injectedPending = {false};
			final var historicalPort = new PhantomPopulationEcologyService.HistoricalPort()
			{
				@Override public Optional<Snapshot> status(long id) { return runtime.historical().status(id); }
				@Override public PhantomHistoricalBackgroundService.Result begin(long id, long start, long target, long seed)
				{
					final var begun = runtime.historical().begin(id, start, target, seed);
					if ((id == profileId) && begun.reason().startsWith("catchup.renewal.")) { recordLinkedTransition(context, "03-historical-renewal", id, runtime, begun.status().name(), begun.reason()); }
					if (runningPendingRecovery && (id == profileId) && begun.successful() && !injectedPending[0])
					{
						injectedPending[0] = true;
						final var prefix = runtime.historical().advance(id, 1, 1);
						PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, prefix.status(), "Linked RUNNING fixture did not commit a prefix interval.");
						final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
						store.replace(id, prefix.snapshot(), prefix.snapshot().state().failed("transaction.item_conflict"));
						final var component = _profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
						final var codec = new PhantomBackgroundStateCodec();
						_profiles.updateComponent(id, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, codec.encode(codec.decode(component.payload()).withState(PhantomBackgroundState.State.VERIFY_PENDING)));
						recordLinkedTransition(context, "03a-running-pending", id, runtime, "RUNNING_PREFIX_VERIFY_PENDING", "transaction.item_conflict");
					}
					return begun;
				}
				@Override public PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes) { return runtime.historical().advance(id, intervals, minutes); }
			};
			final var ecology = new PhantomPopulationEcologyService(ecologyCatalog, _catalog, ecologyStore, historicalPort, id -> runtime.materialization().find(id).isPresent(), id -> "", java.time.Clock.fixed(Instant.ofEpochSecond((completedAt + 10) * 60), ZoneOffset.UTC), ZoneOffset.UTC, PhantomPopulationEcologyState.Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
			ecology.installRuntime(id -> id == profileId ? Optional.of(managed) : Optional.empty(), new PhantomPopulationEcologyService.PopulationEvents()
			{
				@Override public void requestArchive(long id) {}
				@Override public void reconcilePopulation() {}
				@Override public void ecologyFenceChanged(long id) {}
			});
			ecology.enablePeriodicDueMode();
			ecology.register(managed);
			try
			{
				if (syntheticHuman)
				{
					final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
					{
						public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
						public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
					};
					final var topology = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.fromSnapshotForTesting(_production.topology().snapshot(), _production.topologyBackend(), org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults(), signals);
					PhantomAssertions.assertTrue(topology.start(), "Linked native human topology did not start.");
					try
					{
						topology.registerProfile(profileId);
						topology.updateProfile(profileId, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(managed.state().creationX(), managed.state().creationY(), managed.state().creationZ(), 0), 1);
						final var locality = new org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl(topology, signals, org.l2jmobius.gameserver.phantoms.PhantomSystem::onlineHumanPoints, System::currentTimeMillis, id -> id == profileId);
						locality.installPreparationDemand(ecology::updateMaterializationDemand, () -> 1);
						locality.onPulse();
						PhantomAssertions.assertTrue(locality.isLocal(profileId), "Production World human supplier did not drive linked ecology demand.");
					}
					finally { topology.beginStop(); topology.finishStop(); }
				}
				else { ecology.updateMaterializationDemand(List.of(new PhantomPopulationEcologyService.DemandFact(profileId, 1, true, 1, 1)), 1); }
				PhantomAssertions.assertFalse(ecology.requestMaterializationDue(profileId).complete(), "Missing renewal crossed readiness before repair.");
				recordLinkedTransition(context, "02-ecology-due", profileId, runtime, ecology.dueSnapshot(profileId).toString(), ecology.dueSnapshot(profileId).reason());
				PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty(), "NORMAL Player appeared before readiness.");
				for (int pulse = 0; (pulse < 512) && !ecology.requestMaterializationDue(profileId).complete(); pulse++) { ecology.onPopulationPulse(); }
				PhantomAssertions.assertTrue(context.measurements().containsKey("m1.linked.03-historical-renewal"), "Ecology did not publish a typed historical renewal result.");
				if (runningPendingRecovery) { PhantomAssertions.assertTrue(injectedPending[0], "Linked fixture did not exercise an already RUNNING request."); }
				PhantomAssertions.assertTrue(ecology.requestMaterializationDue(profileId).complete(), "Local demand did not finish the same renewal: due=" + ecology.dueSnapshot(profileId) + ",progress=" + ecology.progressSnapshot(profileId) + ",preparation=" + ecology.preparationSnapshot() + ",failure=" + ecology.snapshot().lastFailure());
				recordLinkedTransition(context, "04-readiness-complete", profileId, runtime, ecology.dueSnapshot(profileId).toString(), ecology.dueSnapshot(profileId).reason());
				final Snapshot renewed = runtime.historical().status(profileId).orElseThrow();
				PhantomAssertions.assertEquals(Status.COMPLETE, renewed.state().status(), "Demand did not finish historical window.");
				PhantomAssertions.assertTrue(!old.state().requestId().equals(renewed.state().requestId()), "Demand reused prior completed request.");
				PhantomAssertions.assertEquals(renewed.state().targetEpochMinute(), ecologyStore.load(profileId).orElseThrow().state().calendarCursorEpochMinute(), "Ecology lost the requested horizon.");
				PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty(), "Historical baseline Player remained before NORMAL admission.");
				if (legacyRecovery)
				{
					recoverLegacyFixture(profileId, managed.profile().characterObjectId(), runtime, context);
				}
				if (latentRecovery)
				{
					recoverLatentFixture(profileId, managed.profile().characterObjectId(), runtime, context);
				}
				final var normal = runtime.materialization().materialize(profileId);
				recordLinkedTransition(context, "05-normal-materialization", profileId, runtime, normal.status().name(), normal.status().name());
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, normal.status(), "Complete readiness did not admit a real NORMAL Player.");
				if (latentRecovery)
				{
					final var busyWitness = latentWitness(profileId, managed.profile().characterObjectId());
					final long beforeBusy = componentRowVersion(profileId, PhantomBackgroundState.COMPONENT_TYPE);
					final var busy = runtime.background().recoverAttestedLegacyMaterializedDrift(busyWitness);
					PhantomAssertions.assertEquals("recovery.legacy.identity_busy", busy.reason(), "Runtime Player identity lease did not fence latent repair.");
					PhantomAssertions.assertEquals(beforeBusy, componentRowVersion(profileId, PhantomBackgroundState.COMPONENT_TYPE), "Runtime-busy latent repair wrote a component.");
				}
				final Player nativePlayer;
				try (var action = runtime.materialization().tryAcquireAction(profileId).orElseThrow())
				{
					nativePlayer = action.player();
					PhantomAssertions.assertEquals(managed.profile().characterObjectId(), action.player().getObjectId(), "NORMAL Player changed the linked identity.");
					PhantomAssertions.assertTrue(World.getInstance().getPlayer(action.player().getObjectId()) == action.player(), "NORMAL Player is absent from World.");
				}
				final var goal = runtime.goals().load(profileId).orElseThrow().goal();
				PhantomAssertions.assertEquals(PhantomBackgroundGoalSpec.GOAL_TYPE, goal.goalType(), "Rebuilt local goal did not select native farm AutoPlay.");
				if (runtime.materialization().tryAcquireAction(profileId).map(action -> { try (action) { return action.player().isDead(); } }).orElse(false))
				{
					final var recovered = runtime.background().recover(profileId, goal, PhantomActivityState.ACTIVE);
					PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, recovered.status(), "Native death recovery failed before AutoPlay: " + recovered.reason());
				}
				final var candidates = new PhantomCandidateRegistry();
				candidates.seal();
				final var handlers = new PhantomStepHandlerRegistry();
				handlers.seal();
				final var engine = new PhantomDecisionEngine(runtime.goals(), candidates, handlers, new PhantomMetrics(), 1);
				engine.start();
				final var autoPlay = new PhantomVisibleAutoPlay(runtime.materialization(), () -> engine, id -> ecology.requestMaterializationDue(id).complete());
				Monster monster = null;
				try
				{
					PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, engine.attach(profileId), "Rebuilt goal did not attach to Decision.");
					final var decision = engine.find(profileId).orElseThrow();
					recordLinkedTransition(context, "06-decision-runtime", profileId, runtime, decision.runtimeState().name(), decision.reasonKey());
					final boolean current = (decision.goalId() == goal.goalId()) && (decision.goalRevision() == goal.revision()) && (decision.goalStatus() == PhantomGoalStatus.ACTIVE) && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(decision.goalType()) && ecology.requestMaterializationDue(profileId).complete();
					recordLinkedTransition(context, "07-autoplay-current", profileId, runtime, Boolean.toString(current), current ? "all current() guards satisfied" : "decision goal or readiness mismatch");
					PhantomAssertions.assertTrue(current, "AutoPlay.current guard did not match the active Decision goal and readiness.");
					final var nativeSnapshot = runtime.materialization().find(profileId).orElseThrow();
					try (var action = runtime.materialization().tryAcquireAction(profileId).orElseThrow())
					{
						PhantomAssertions.assertTrue(action.player().hasHeadlessOutboundSession() && action.player().isOnline() && !action.player().isDead(), "Rebuilt Player lacks native AutoPlay admission: headless=" + action.player().hasHeadlessOutboundSession() + ",online=" + action.player().isOnline() + ",dead=" + action.player().isDead());
					}
					monster = new Monster(NpcData.getInstance().getTemplate(PhantomBackgroundGoalSpec.parse(goal).npcId()));
					monster.setInstanceId(nativePlayer.getInstanceId());
					final var nativeSpawn = new Spawn(monster.getTemplate());
					nativeSpawn.setXYZ(nativePlayer.getX() + 20, nativePlayer.getY(), nativePlayer.getZ());
					monster.setSpawn(nativeSpawn);
					monster.setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp());
					monster.spawnMe(nativePlayer.getX() + 20, nativePlayer.getY(), nativePlayer.getZ());
					final double initialHp = monster.getCurrentHp();
					final boolean started = autoPlay.start(profileId, goal);
					recordLinkedTransition(context, "08-autoplay-start", profileId, runtime, Boolean.toString(started), started ? "native pools admitted" : "start guard: snapshot=" + nativeSnapshot + ",decision=" + decision + ",headless=" + nativePlayer.hasHeadlessOutboundSession() + ",online=" + nativePlayer.isOnline() + ",dead=" + nativePlayer.isDead());
					PhantomAssertions.assertTrue(started, "Rebuilt goal did not start native AutoPlay: materialization=" + nativeSnapshot + ",decision=" + decision + ",goal=" + goal.goalType() + "/" + goal.revision());
					PhantomAssertions.assertTrue(autoPlay.running(profileId, goal), "Native AutoPlay was not running for rebuilt Player.");
					final long actionDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
					while ((monster.getCurrentHp() >= initialHp) && (System.nanoTime() < actionDeadline)) { Thread.sleep(20); }
					recordLinkedTransition(context, "09-first-native-action", profileId, runtime, "monsterHp=" + monster.getCurrentHp(), monster.getCurrentHp() < initialHp ? "native attack damage=" + (initialHp - monster.getCurrentHp()) : "no attack: online=" + nativePlayer.isOnline() + ",dead=" + nativePlayer.isDead() + ",moving=" + nativePlayer.isMoving() + ",auto=" + nativePlayer.isAutoPlaying() + ",target=" + nativePlayer.getTarget() + ",peace=" + nativePlayer.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE));
					PhantomAssertions.assertTrue(monster.getCurrentHp() < initialHp, "Native AutoPlay did not perform its first attack.");
				}
				finally
				{
					autoPlay.stop(profileId);
					if (monster != null) { monster.deleteMe(); }
					try (var action = runtime.materialization().tryAcquireAction(profileId).orElse(null))
					{
						if (action != null)
						{
							final var player = action.player();
							player.abortAttack(); player.abortCast(); player.stopMove(null); player.setTarget(null);
							player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE);
							player.getAI().setAutoAttacking(false);
							org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(player);
						}
					}
					engine.beginStop(); engine.finishStop();
					final var cleanup = runtime.materialization().dematerialize(profileId);
					recordLinkedTransition(context, "10-cleanup", profileId, runtime, cleanup.status().name(), cleanup.status().name());
					PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanup.status(), "Native action cleanup retained Player: " + cleanup + ", lifecycle=" + runtime.lifecycleFailure());
					PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty() && World.getInstance().getPlayer(managed.profile().characterObjectId()) == null, "Native action cleanup left a runtime Player.");
					PhantomAssertions.assertTrue(runtime.transaction().load(profileId).state().state() != PhantomBackgroundState.State.MATERIALIZED, "Native action cleanup left an orphaned MATERIALIZED background state.");
				}
			}
			finally { ecology.beginStop(); ecology.finishStop(); }
		}
	}

	private static void recoverLegacyFixture(long profileId, int objectId, RuntimeHarness runtime, PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, runtime.transaction().markMaterialized(profileId, objectId).status(), "Linked legacy fixture could not enter MATERIALIZED.");
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET x=x+16,heading=heading+1 WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Linked legacy fixture did not drift canonical position.");
		}
		PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.INCONSISTENT, runtime.transaction().abortMaterialization(profileId, objectId).status(), "Linked legacy fixture did not fail closed before repair.");
		final PhantomBackgroundTransaction.LegacyHeadlessWitness witness;
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT pc.row_version,pc.payload,c.curHp,c.curMp,c.curCp,c.x,c.y,c.z,c.heading FROM phantom_profile_components pc JOIN characters c ON c.charId=? WHERE pc.profile_id=? AND pc.component_type='background.state'"))
		{
			statement.setInt(1, objectId);
			statement.setLong(2, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Linked legacy fixture witness is absent.");
				witness = new PhantomBackgroundTransaction.LegacyHeadlessWitness(profileId, objectId, result.getLong("row_version"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getBytes("payload"))), result.getDouble("curHp"), result.getDouble("curMp"), result.getDouble("curCp"), result.getInt("x"), result.getInt("y"), result.getInt("z"), result.getInt("heading"));
			}
		}
		final var recovered = runtime.background().recoverAttestedLegacyHeadlessDrift(witness);
		recordLinkedTransition(context, "04a-legacy-recovery", profileId, runtime, recovered.status().name(), recovered.reason());
		PhantomAssertions.assertTrue(recovered.successful(), "Attested linked legacy fixture did not recover: " + recovered.reason());
	}

	private static void recoverLatentFixture(long profileId, int objectId, RuntimeHarness runtime, PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, runtime.transaction().markMaterialized(profileId, objectId).status(), "Linked latent fixture could not enter MATERIALIZED.");
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET x=x+16,heading=heading+1 WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Linked latent fixture did not drift canonical position.");
		}
		final var recovered = runtime.background().recoverAttestedLegacyMaterializedDrift(latentWitness(profileId, objectId));
		recordLinkedTransition(context, "04b-latent-recovery", profileId, runtime, recovered.status().name(), recovered.reason());
		PhantomAssertions.assertTrue(recovered.successful(), "Attested linked latent fixture did not recover: " + recovered.reason());
	}

	private static PhantomBackgroundTransaction.LegacyMaterializedWitness latentWitness(long profileId, int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT pc.row_version,pc.payload,c.curHp,c.curMp,c.curCp,c.x,c.y,c.z,c.heading FROM phantom_profile_components pc JOIN characters c ON c.charId=? WHERE pc.profile_id=? AND pc.component_type='background.state'"))
		{
			statement.setInt(1, objectId);
			statement.setLong(2, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Linked latent fixture witness is absent.");
				final byte[] payload = result.getBytes("payload");
				PhantomAssertions.assertEquals(0, (int) payload[8], "Linked latent fixture was not MATERIALIZED.");
				final byte[] marker = payload.clone();
				marker[8] = 4;
				return new PhantomBackgroundTransaction.LegacyMaterializedWitness(profileId, objectId, result.getLong("row_version"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(marker)), result.getDouble("curHp"), result.getDouble("curMp"), result.getDouble("curCp"), result.getInt("x"), result.getInt("y"), result.getInt("z"), result.getInt("heading"));
			}
		}
	}

	private static long componentRowVersion(long profileId, String type) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT row_version FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
		{
			statement.setLong(1, profileId);
			statement.setString(2, type);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Linked component row is absent.");
				return result.getLong(1);
			}
		}
	}

	private static void recordLinkedTransition(PhantomTestContext context, String step, long profileId, RuntimeHarness runtime, String owner, String reason)
	{
		final var goal = runtime.goals().load(profileId).map(stored -> stored.goal()).orElse(null);
		final var background = runtime.transaction().load(profileId).state();
		final var materialization = runtime.materialization().find(profileId).orElse(null);
		context.record("m1.linked." + step, "profile=" + profileId + " owner=" + owner + " goal=" + (goal == null ? "absent" : goal.goalId() + "/" + goal.revision() + "/" + goal.status() + "/" + goal.goalType()) + " background=" + (background == null ? "absent" : background.state()) + " materialization=" + (materialization == null ? "absent/objectId=0" : materialization.state() + "/objectId=" + materialization.characterObjectId() + "/world=" + materialization.worldPresent()) + " reason=" + reason);
	}

	private void testRenewalWithoutGoalAndBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 24).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final Snapshot complete = completeWindow(runtime, profileId, context.seed());
			deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
			deleteComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final var invalid = new PhantomBackgroundCatchupState(Status.PENDING, complete.state().requestId(), context.seed(), FROM_MINUTE + 4, FROM_MINUTE + 8, FROM_MINUTE + 4, complete.state().planOrdinal() + 1, 0, complete.state().generation() + 1, complete.state().knowledgeGeneration(), complete.state().topologyGeneration(), 0, 0, "", complete.state().modelVersion(), complete.state().authorityHashes(), "");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> store.renewCompletedUnplanned(profileId, complete, invalid), "Unplanned renewal reused a completed request ID.");
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Absent goal/background renewal failed: " + begun.reason());
			PhantomAssertions.assertEquals("catchup.renewal.goal_missing.background_missing.recovered", begun.reason(), "Missing goal/background recovery was not typed.");
			PhantomAssertions.assertEquals(Status.RUNNING, begun.snapshot().state().status(), "Rebuilt renewal did not enter RUNNING.");
			PhantomAssertions.assertEquals(FROM_MINUTE + 4, begun.snapshot().state().cursorEpochMinute(), "Rebuild advanced the historical cursor.");
			PhantomAssertions.assertTrue(!complete.state().requestId().equals(begun.snapshot().state().requestId()), "Renewal reused the completed request.");
			PhantomAssertions.assertEquals(begun.snapshot().state().requestId(), runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed()).snapshot().state().requestId(), "Rebuild did not retain the same new request.");
			PhantomAssertions.assertEquals(Status.COMPLETE, runtime.historical().advance(profileId, 4, 4).snapshot().state().status(), "Rebuilt history did not complete.");
			PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty(), "Historical baseline retained a Player.");
		}
	}

	private void testRenewalWithoutGoalWithBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 30).profile().profileId();
		final var fault = new AtomicReference<FaultPoint>();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ObjectIdAllocator.production(), point ->
		{
			if (fault.compareAndSet(point, null)) { throw new IllegalStateException("Injected one-time baseline capture interruption."); }
		});
		try (RuntimeHarness runtime = openRuntime(profileId, transaction))
		{
			completeWindow(runtime, profileId, context.seed());
			PhantomBackgroundState before = runtime.transaction().load(profileId).state();
			if (before.state() == PhantomBackgroundState.State.DEAD)
			{
				final var goal = runtime.goals().load(profileId).orElseThrow().goal();
				final var recovery = runtime.background().recover(profileId, goal, PhantomActivityState.WARM);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, recovery.status(), "Goal-only fixture could not complete native death recovery: " + recovery.reason());
				before = runtime.transaction().load(profileId).state();
			}
			PhantomAssertions.assertEquals(PhantomBackgroundState.State.READY, before.state(), "Goal-only fixture is not a safe READY baseline.");
			deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
			fault.set(FaultPoint.BEFORE_CAPTURE_COMMIT);
			final var interrupted = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.RETRY, interrupted.status(), "One-time baseline capture failure did not retain the same renewal.");
			PhantomAssertions.assertTrue(interrupted.reason().endsWith("catchup.baseline.store_retry"), "Interrupted baseline did not report the store boundary: " + interrupted.reason());
			context.record("m1.goalOnlyRetry", "reason=" + interrupted.reason() + " background=" + runtime.transaction().load(profileId).state().state() + " materialization=" + runtime.materialization().find(profileId) + " lifecycle=" + runtime.lifecycleFailure());
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Absent goal with canonical baseline did not rebuild: " + begun.reason());
			PhantomAssertions.assertEquals("catchup.ready", begun.reason(), "Retried goal-only recovery did not resume the same ready request.");
			final PhantomBackgroundState after = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertEquals(before.progress(), after.progress(), "Goal rebuild fabricated progression.");
			PhantomAssertions.assertEquals(before.inventory().canonicalHash(), after.inventory().canonicalHash(), "Goal rebuild changed canonical item counts.");
			PhantomAssertions.assertTrue(runtime.materialization().find(profileId).isEmpty(), "Goal rebuild retained historical Player.");
			PhantomAssertions.assertEquals(Status.COMPLETE, runtime.historical().advance(profileId, 4, 4).snapshot().state().status(), "Goal-only renewal did not finish.");
		}
	}

	private void testRenewalWithoutGoalWithDeadBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 31).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final Snapshot complete = completeWindow(runtime, profileId, context.seed());
			final var component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			final var before = runtime.transaction().load(profileId).state();
			final var vitals = before.vitals();
			final var deadVitals = new PhantomBackgroundState.Vitals(0, vitals.maximumHp(), vitals.currentMp(), vitals.maximumMp(), vitals.currentCp(), vitals.maximumCp());
			final var dead = before.after(before.progress(), deadVitals, before.position(), before.inventory(), before.autoGetSkills(), before.clock(), before.receipt());
			_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(dead));
			deleteComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE);
			final var rejected = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.REPLAN_REQUIRED, rejected.status(), "Goal-free DEAD baseline entered an unbounded retry.");
			PhantomAssertions.assertEquals("catchup.renewal.background_state_invalid", rejected.reason(), "Unverifiable DEAD baseline was not typed.");
			PhantomAssertions.assertEquals(complete, runtime.historical().status(profileId).orElseThrow(), "Rejected DEAD baseline mutated completed history.");
		}
	}

	private void testRenewalWithoutBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 25).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final Snapshot complete = completeWindow(runtime, profileId, context.seed());
			final var goal = runtime.goals().load(profileId).orElseThrow().goal();
			deleteComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Absent background renewal failed: " + begun.reason());
			PhantomAssertions.assertEquals("catchup.renewal.background_missing.recovered", begun.reason(), "Missing background recovery was not typed.");
			PhantomAssertions.assertEquals(complete.state().goalId(), begun.snapshot().state().goalId(), "Background rebuild replaced goal identity.");
			PhantomAssertions.assertEquals(goal, runtime.goals().load(profileId).orElseThrow().goal(), "Background rebuild changed the valid goal.");
			PhantomAssertions.assertEquals(Status.COMPLETE, runtime.historical().advance(profileId, 4, 4).snapshot().state().status(), "Background rebuild did not finish history.");
		}
	}

	private void testRenewalWithStaleGoal(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 26).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final Snapshot complete = completeWindow(runtime, profileId, context.seed());
			final var storedGoal = runtime.goals().load(profileId).orElseThrow();
			runtime.goals().replace(profileId, storedGoal.rowVersion(), storedGoal.goal().withStatus(PhantomGoalStatus.ACTIVE));
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Safe stale goal did not replan: " + begun.reason());
			PhantomAssertions.assertEquals("catchup.renewal.goal_mismatch.recovered", begun.reason(), "Safe stale goal recovery was not typed.");
			PhantomAssertions.assertEquals(complete.state().goalId(), begun.snapshot().state().goalId(), "Stale renewal replaced goal identity.");
			PhantomAssertions.assertEquals(Status.COMPLETE, runtime.historical().advance(profileId, 4, 4).snapshot().state().status(), "Replanned history did not complete.");
		}
	}

	private void testRenewalWithInvalidBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 27).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			final Snapshot complete = completeWindow(runtime, profileId, context.seed());
			final var component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			final var invalid = runtime.transaction().load(profileId).state().withState(PhantomBackgroundState.State.INCONSISTENT);
			_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(invalid));
			final var rejected = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.REPLAN_REQUIRED, rejected.status(), "Inconsistent background was accepted.");
			PhantomAssertions.assertEquals("catchup.renewal.background_state_invalid", rejected.reason(), "Invalid background was not typed.");
			PhantomAssertions.assertEquals(complete, new PhantomBackgroundCatchupStore(_profiles, runtime.goals()).load(profileId).orElseThrow(), "Invalid background changed completed history.");
		}
	}

	private void testRenewalWithOrphanedMaterializedBackground(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 28).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			completeWindow(runtime, profileId, context.seed());
			final int objectId = _profiles.find(profileId).orElseThrow().characterObjectId();
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, runtime.transaction().markMaterialized(profileId, objectId).status(), "TEST orphan fixture was not created.");
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Verified orphaned MATERIALIZED state was not recovered: " + begun.reason());
			PhantomAssertions.assertEquals("catchup.renewal.background_state_invalid.recovered", begun.reason(), "Orphaned state recovery was not typed.");
			PhantomAssertions.assertEquals(Status.COMPLETE, runtime.historical().advance(profileId, 4, 4).snapshot().state().status(), "Recovered orphan did not finish history.");
		}
	}

	private Snapshot completeWindow(RuntimeHarness runtime, long profileId, long seed)
	{
		PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, seed).status(), "Renewal fixture baseline failed.");
		final var complete = runtime.historical().advance(profileId, 4, 4);
		PhantomAssertions.assertEquals(Status.COMPLETE, complete.snapshot().state().status(), "Renewal fixture did not complete.");
		return complete.snapshot();
	}

	private void deleteComponent(long profileId, String componentType)
	{
		final var component = _profiles.findComponent(profileId, componentType).orElseThrow();
		_profiles.deleteComponent(profileId, componentType, component.rowVersion());
	}

	private void testRecoverableFailure(PhantomTestContext context, String reason) throws Exception
	{
		final long profileId = createManaged(context.seed() + reason.hashCode()).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "Recovery baseline failed.");
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Recovery prefix failed.");
			final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot prefix = store.load(profileId).orElseThrow();
			final byte[] canonical = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final Snapshot failed = store.replace(profileId, prefix, prefix.state().failed(reason));
			final var result = runtime.historical().advance(profileId, 1, 1);
			if (reason.endsWith("unknown"))
			{
				PhantomAssertions.assertEquals(ResultStatusCode.REPLAN_REQUIRED, result.status(), "Unknown authority failure became successful.");
				PhantomAssertions.assertEquals(failed, result.snapshot(), "Unknown failure changed history ownership.");
				PhantomAssertions.assertTrue(Arrays.equals(canonical, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)), "Unknown failure changed canonical character facts.");
				final Hashes currentHashes = prefix.state().authorityHashes();
				final Hashes staleHashes = new Hashes("old-" + currentHashes.knowledge(), currentHashes.topology(), currentHashes.progression(), currentHashes.commerce());
				final Snapshot staleUnknown = store.replace(profileId, failed, copyWithHashes(failed.state(), staleHashes));
				final var staleResult = runtime.historical().advance(profileId, 1, 1);
				PhantomAssertions.assertEquals(ResultStatusCode.REPLAN_REQUIRED, staleResult.status(), "Stale generation erased an unknown failure.");
				PhantomAssertions.assertEquals(staleUnknown, staleResult.snapshot(), "Unknown failure changed history ownership during stale-generation recovery.");
				return;
			}
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, result.status(), "Recoverable durable failure remained terminal: " + reason + ":" + result.reason());
			PhantomAssertions.assertEquals(prefix.state().requestId(), result.snapshot().state().requestId(), "Recovery recreated the history request.");
			PhantomAssertions.assertEquals(prefix.state().cursorEpochMinute() + 1, result.snapshot().state().cursorEpochMinute(), "Recovery replayed or skipped a minute.");
			PhantomAssertions.assertEquals(prefix.state().intervalOrdinal() + 1, result.snapshot().state().intervalOrdinal(), "Recovery reset the interval ordinal.");
			PhantomAssertions.assertEquals(prefix.state().generation(), result.snapshot().state().generation(), "Recovery replaced cursor ownership.");
		}
	}

	private void testNoRouteHistoricalIdle(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 18).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 3, context.seed()).status(), "Idle baseline failed.");
			final var state = runtime.transaction().load(profileId).state();
			final var topology = isolatedTopology(state.position().committedAnchorId());
			final var planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), topology, _production.authority());
			final var historical = new PhantomHistoricalBackgroundService(_profiles, runtime.goals(), planner, runtime.background(), runtime.materialization());
			final var store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot prefix = store.load(profileId).orElseThrow();
			store.replace(profileId, prefix, prefix.state().failed("planner.target_or_route.absent"));
			final var result = historical.advance(profileId, 1, 1);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, result.status(), "No-route historical time remained permanently fenced: " + result.reason());
			final var after = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertEquals(state.progress(), after.progress(), "Idle granted experience or SP.");
			PhantomAssertions.assertEquals(state.vitals(), after.vitals(), "Idle changed vitals.");
			PhantomAssertions.assertEquals(state.inventory(), after.inventory(), "Idle changed inventory or adena.");
			PhantomAssertions.assertEquals(state.position(), after.position(), "Idle moved the character.");
			PhantomAssertions.assertEquals(state.clock(), after.clock(), "Idle consumed RNG or combat time.");
			PhantomAssertions.assertEquals(prefix.state().requestId(), result.snapshot().state().requestId(), "Idle recreated history.");
			PhantomAssertions.assertEquals(FROM_MINUTE + 1, result.snapshot().state().cursorEpochMinute(), "Idle did not advance exactly one minute.");
			final var idleGoal = runtime.goals().load(profileId).orElseThrow().goal();
			final var committed = result.snapshot().state();
			final var identity = new HistoricalIdentity(committed.requestId(), committed.generation(), committed.intervalOrdinal() - 1, committed.cursorEpochMinute() - 1, committed.cursorEpochMinute(), committed.planIdentity());
			final var key = new PhantomBackgroundOperationKey(profileId, state.identity().characterObjectId(), idleGoal.goalId(), idleGoal.revision(), 0, 0, ActionKind.HISTORICAL_IDLE, 0, state.position().committedAnchorId(), PhantomBackgroundState.MODEL_VERSION, committed.authorityHashes(), null, identity);
			PhantomAssertions.assertEquals(key.digest(), after.receipt().operationKey(), "Idle receipt lost its distinct replay identity.");
			final var beforeIdle = prefix.state().withPlan(idleGoal.goalId(), idleGoal.revision(), committed.planOrdinal(), committed.planIdentity(), committed.knowledgeGeneration(), committed.topologyGeneration(), committed.authorityHashes());
			final var replay = runtime.transaction().verifyCommittedHistoricalReplay(profileId, state.identity().characterObjectId(), idleGoal, key, new PhantomBackgroundTransaction.CatchupMutation(beforeIdle, result.snapshot().rowVersion() - 1, committed));
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.IDEMPOTENT, replay.status(), "Historical idle replay was not idempotent.");
			PhantomAssertions.assertEquals(after, replay.state(), "Idle replay changed canonical character facts.");
			PhantomAssertions.assertEquals(result.snapshot(), store.load(profileId).orElseThrow(), "Idle replay changed catch-up ownership.");
		}
	}

	private org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery isolatedTopology(String anchorId)
	{
		final var source = _production.topology().snapshot();
		final var backend = new org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend();
		final var snapshot = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot.create(1, "test-isolated-history", 1, source.generation(), source.nodes(), List.of(_production.topology().findAnchor(anchorId).orElseThrow()), List.of(), backend, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults());
		return new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery(snapshot, backend, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics());
	}

	private void testPlannerLowerTier(PhantomTestContext context) throws Exception
	{
		final long profileId = createManaged(context.seed() + 19).profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 16, context.seed()).status(), "Tier baseline failed.");
			final var goal = runtime.goals().load(profileId).orElseThrow().goal();
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			for (int step = 0; step < 15 && !runtime.transaction().load(profileId).state().position().committedAnchorId().equals(spec.anchorId()); step++)
			{
				PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().advance(profileId, 1, 1).status(), "Tier fixture travel failed.");
			}
			final var state = runtime.transaction().load(profileId).state();
			final var planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), isolatedTopology(spec.anchorId()), _production.authority());
			final int targetLevel = _production.knowledge().findNpc(spec.npcId()).orElseThrow().level();
			for (int difference : List.of(0, 5, 10))
			{
				final var progress = new PhantomBackgroundState.Progress(targetLevel + difference, state.progress().experience(), state.progress().skillPoints(), state.progress().experienceBeforeDeath());
				final var projected = state.after(progress, state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
				final var result = planner.replan(profileId, projected, goal, context.seed(), 1);
				PhantomAssertions.assertTrue(result.ready(), "Reachable lower-tier target was excluded: L-" + difference);
				PhantomAssertions.assertEquals(spec.anchorId(), result.spec().anchorId(), "Isolated tier selected a target outside the only authoritative anchor.");
				PhantomAssertions.assertTrue(planner.remainsSuitable(projected, result.goal()), "Lower-tier target was immediately considered unsuitable.");
			}
			final var excluded = planner.replan(profileId, state, goal, context.seed(), 1, Set.of(spec.npcId() + "@" + spec.anchorId()), Set.of());
			PhantomAssertions.assertTrue(!excluded.ready() || (excluded.spec().npcId() != spec.npcId()) || !excluded.spec().anchorId().equals(spec.anchorId()), "Target-specific exclusion selected the excluded NPC and anchor.");
		}
	}

	private void testCompleteStaleAuthorityRenewsNextDue(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed() + 13);
		final long profileId = managed.profile().profileId();
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction()))
		{
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed()).status(), "First durable window did not begin.");
			final var first = runtime.historical().advance(profileId, 4, 4);
			PhantomAssertions.assertEquals(Status.COMPLETE, first.snapshot().state().status(), "First durable window did not complete.");
			final PhantomBackgroundCatchupStore store = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			final Snapshot complete = store.load(profileId).orElseThrow();
			final Hashes current = runtime.planner().generation().authorityHashes();
			final Hashes stale = new Hashes("old-" + current.knowledge(), current.topology(), current.progression(), current.commerce());
			store.replace(profileId, complete, copyWithHashes(complete.state(), stale));
			final var renewed = runtime.historical().begin(profileId, FROM_MINUTE + 4, FROM_MINUTE + 8, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, renewed.status(), "Completed stale authority blocked the next ecology due: " + renewed.reason());
			PhantomAssertions.assertEquals(Status.RUNNING, renewed.snapshot().state().status(), "Renewed due did not enter RUNNING.");
			PhantomAssertions.assertEquals(FROM_MINUTE + 4, renewed.snapshot().state().cursorEpochMinute(), "Renewal replayed or skipped completed time.");
			PhantomAssertions.assertEquals(current, renewed.snapshot().state().authorityHashes(), "Renewal kept stale authority hashes.");
			PhantomAssertions.assertEquals(complete.state().goalId(), renewed.snapshot().state().goalId(), "Renewal replaced the durable goal identity.");
			PhantomAssertions.assertEquals(complete.state().goalRevision() + 1, renewed.snapshot().state().goalRevision(), "Renewal did not atomically advance the goal revision.");
		}
	}

	private void testD2PaidGatekeeperAtomicReplay(PhantomTestContext context) throws Exception
	{
		ManagedSnapshot dwarf = null;
		for (int attempt = 0; (attempt < 32) && (dwarf == null); attempt++)
		{
			final ManagedSnapshot candidate = createManaged(context.seed() + 200 + attempt);
			if (candidate.state().classId() == 53)
			{
				dwarf = candidate;
			}
		}
		PhantomAssertions.assertTrue(dwarf != null, "Paid D2 fixture did not create a managed Dwarf.");
		final long profileId = dwarf.profile().profileId();
		final int characterId = dwarf.profile().characterObjectId();
		final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _production.topology());
		final var paidLeg = travel.legs().stream().filter(leg -> leg.transitionId().equals("transition.e904d3aed33a9cecd852c15c")).findFirst().orElseThrow();
		final var source = _production.topology().findAnchor(paidLeg.fromAnchorId()).orElseThrow();
		final var sourcePosition = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(source, 0).orElseThrow();
		final int adenaObjectId = IdManager.getInstance().getNextId();
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement character = connection.prepareStatement("UPDATE characters SET level=?,exp=?,expBeforeDeath=0,x=?,y=?,z=?,heading=? WHERE charId=?"); PreparedStatement item = connection.prepareStatement("INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES (?,57,12000,'INVENTORY',0,0,?,0,0,-1,-1)"))
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Paid D2 fixture touched a non-test database.");
			character.setInt(1, 24);
			character.setLong(2, ExperienceData.getInstance().getExpForLevel(24));
			character.setInt(3, sourcePosition.x());
			character.setInt(4, sourcePosition.y());
			character.setInt(5, sourcePosition.z());
			character.setInt(6, sourcePosition.heading());
			character.setInt(7, characterId);
			PhantomAssertions.assertEquals(1, character.executeUpdate(), "Paid D2 fixture character is absent.");
			item.setInt(1, characterId);
			item.setInt(2, adenaObjectId);
			PhantomAssertions.assertEquals(1, item.executeUpdate(), "Paid D2 Adena fixture was not inserted.");
		}
		final var authority = new L2jPhantomBackgroundAuthority(_production::knowledge, _production::topology, _production::progression, _production::commerce, travel);
		final AtomicReference<FaultPoint> fault = new AtomicReference<>();
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, ObjectIdAllocator.production(), point ->
		{
			if (fault.compareAndSet(point, null))
			{
				throw new IllegalStateException("d2.paid." + point.name().toLowerCase());
			}
		});
		try (RuntimeHarness runtime = openRuntime(profileId, transaction, authority))
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 12, 0);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Paid D2 Dwarf baseline failed: " + begun.reason());
			final var goal = runtime.goals().load(profileId).orElseThrow().goal();
			PhantomAssertions.assertEquals(paidLeg.toAnchorId(), PhantomBackgroundGoalSpec.parse(goal).anchorId(), "Paid D2 planner did not select the e904 witness.");
			final var atSource = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertEquals(paidLeg.fromAnchorId(), atSource.position().committedAnchorId(), "Paid D2 fixture did not reach factual GK source.");
			final var paidSkills = authority.autoGetSkills(atSource.identity(), 41);
			final var paidState = atSource.after(new PhantomBackgroundState.Progress(41, ExperienceData.getInstance().getExpForLevel(41), atSource.progress().skillPoints(), atSource.progress().experienceBeforeDeath()), atSource.vitals(), atSource.position(), atSource.inventory(), paidSkills, atSource.clock(), atSource.receipt());
			try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE characters SET level=41,exp=? WHERE charId=?"); PreparedStatement skill = connection.prepareStatement("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE skill_level=VALUES(skill_level)"))
			{
				PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "Paid D2 level fixture touched a non-test database.");
				statement.setLong(1, paidState.progress().experience());
				statement.setInt(2, characterId);
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Paid D2 fixture level did not update.");
				for (var entry : paidSkills)
				{
					skill.setInt(1, characterId);
					skill.setInt(2, entry.skillId());
					skill.setInt(3, entry.skillLevel());
					skill.setInt(4, atSource.identity().classIndex());
					skill.addBatch();
				}
				skill.executeBatch();
			}
			final var component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			_profiles.updateComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE, component.rowVersion(), PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(paidState));
			PhantomAssertions.assertEquals(12000L, runtime.transaction().load(profileId).state().inventory().itemCount(57), "Paid D2 baseline did not track Adena.");
			final PhantomBackgroundCatchupStore catchupStore = new PhantomBackgroundCatchupStore(_profiles, runtime.goals());
			for (int interval = 0; interval < 3; interval++)
			{
				final var current = catchupStore.load(profileId).orElseThrow();
				final var state = runtime.transaction().load(profileId).state();
				if (state.clock().residualTravelMillis() > 0 && state.clock().residualTravelMillis() <= PhantomBackgroundService.FARM_TRAVEL_BUDGET_MILLIS)
				{
					break;
				}
				final var partial = runtime.background().advanceHistorical(profileId, goal, current, current.state().advanceTo(current.state().cursorEpochMinute() + 1));
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, partial.status(), "Paid D2 partial GK interval failed: " + partial.reason());
				PhantomAssertions.assertEquals(12000L, runtime.transaction().load(profileId).state().inventory().itemCount(57), "Partial paid GK charged early.");
			}
			final var expected = catchupStore.load(profileId).orElseThrow();
			final var next = expected.state().advanceTo(expected.state().cursorEpochMinute() + 1);
			final byte[] beforeState = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final byte[] beforeCursor = componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE);
			fault.set(FaultPoint.AFTER_CATCHUP_STATE_WRITE);
			final var failed = runtime.background().advanceHistorical(profileId, goal, expected, next);
			PhantomAssertions.assertFalse(failed.status() == PhantomBackgroundService.OperationStatus.SUCCESS, "Pre-commit paid GK fault reported success.");
			PhantomAssertions.assertTrue(Arrays.equals(beforeState, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)) && Arrays.equals(beforeCursor, componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE)), "Pre-commit paid GK fault changed state or cursor.");
			fault.set(FaultPoint.AFTER_OPERATION_COMMIT);
			final var completed = runtime.background().advanceHistorical(profileId, goal, expected, next);
			PhantomAssertions.assertTrue((completed.status() == PhantomBackgroundService.OperationStatus.SUCCESS) || (completed.status() == PhantomBackgroundService.OperationStatus.IDEMPOTENT), "Ambiguous paid GK completion did not reconcile: " + completed.reason());
			final var after = runtime.transaction().load(profileId).state();
			PhantomAssertions.assertEquals(0L, after.inventory().itemCount(57), "Paid GK did not debit exact Adena once.");
			PhantomAssertions.assertEquals(paidLeg.toAnchorId(), after.position().committedAnchorId(), "Paid GK did not commit factual destination.");
			PhantomAssertions.assertEquals(next.cursorEpochMinute(), catchupStore.load(profileId).orElseThrow().state().cursorEpochMinute(), "Paid GK cursor was not atomic with item and move.");
			PhantomAssertions.assertFalse(after.receipt().operationKey().isBlank(), "Paid GK did not persist a receipt.");
			final byte[] afterState = componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE);
			final byte[] afterCursor = componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE);
			final var duplicate = runtime.background().advanceHistorical(profileId, goal, expected, next);
			PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.IDEMPOTENT, duplicate.status(), "Paid GK replay was not idempotent.");
			PhantomAssertions.assertTrue(Arrays.equals(afterState, componentPayload(profileId, PhantomBackgroundState.COMPONENT_TYPE)) && Arrays.equals(afterCursor, componentPayload(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE)), "Paid GK replay changed state or cursor.");
			context.record("d2.paid.beforeAdena", 12000);
			context.record("d2.paid.afterAdena", after.inventory().itemCount(57));
			context.record("d2.paid.beforeAnchor", paidLeg.fromAnchorId());
			context.record("d2.paid.afterAnchor", after.position().committedAnchorId());
			context.record("d2.paid.beforeCursor", expected.state().cursorEpochMinute());
			context.record("d2.paid.afterCursor", next.cursorEpochMinute());
			context.record("d2.paid.receipt", after.receipt().operationKey());
		}
	}

	private void testD2ManagedDwarfNormalGatekeeper(PhantomTestContext context) throws Exception
	{
		ManagedSnapshot dwarf = null;
		for (int attempt = 0; (attempt < 32) && (dwarf == null); attempt++)
		{
			final ManagedSnapshot candidate = createManaged(context.seed() + 100 + attempt);
			if (candidate.state().classId() == 53)
			{
				dwarf = candidate;
			}
		}
		PhantomAssertions.assertTrue(dwarf != null, "Guarded population fixture did not create a Dwarf class.");
		final long profileId = dwarf.profile().profileId();
		final int characterId = dwarf.profile().characterObjectId();
		final var ingress = _production.topology().findAnchor("population.ingress.dwarf.01").orElseThrow();
		final var ingressPosition = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(ingress, 0).orElseThrow();
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE characters SET level=?,exp=?,expBeforeDeath=0,x=?,y=?,z=?,heading=? WHERE charId=?"))
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, connection.getCatalog(), "D2 Dwarf fixture touched a non-test database.");
			statement.setInt(1, 12);
			statement.setLong(2, ExperienceData.getInstance().getExpForLevel(12));
			statement.setInt(3, ingressPosition.x());
			statement.setInt(4, ingressPosition.y());
			statement.setInt(5, ingressPosition.z());
			statement.setInt(6, ingressPosition.heading());
			statement.setInt(7, characterId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "D2 Dwarf fixture character is absent.");
		}
		final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _production.topology());
		final var authority = new L2jPhantomBackgroundAuthority(_production::knowledge, _production::topology, _production::progression, _production::commerce, travel);
		try (RuntimeHarness runtime = openRuntime(profileId, new PhantomBackgroundTransaction(), authority))
		{
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 8, 0);
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "D2 Dwarf historical baseline failed: " + begun.reason());
			final var goal = runtime.goals().load(profileId).orElseThrow().goal();
			final var spec = PhantomBackgroundGoalSpec.parse(goal);
			PhantomAssertions.assertTrue(authority.travelQuery(_production.topology()).route(ingress.id(), spec.anchorId()).orElseThrow().stream().anyMatch(step -> step.id().startsWith("leg.")), "Managed Dwarf planner did not select factual GK travel.");
			boolean arrived = false;
			for (int interval = 0; interval < 8; interval++)
			{
				final var before = runtime.transaction().load(profileId).state();
				final var cursorBefore = runtime.historical().status(profileId).orElseThrow().state().cursorEpochMinute();
				final var advanced = runtime.historical().advance(profileId, 1, 1);
				PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, advanced.status(), "D2 managed GK interval failed: " + advanced.reason());
				final var after = runtime.transaction().load(profileId).state();
				if (after.receipt().operationKey().isBlank() || !after.position().committedAnchorId().equals(spec.anchorId()))
				{
					continue;
				}
				PhantomAssertions.assertEquals(before.inventory().itemCount(57), after.inventory().itemCount(57), "Free Dwarf NORMAL GK charged Adena.");
				PhantomAssertions.assertEquals(cursorBefore + 1, advanced.snapshot().state().cursorEpochMinute(), "GK completion did not commit cursor with position.");
				PhantomAssertions.assertFalse(before.position().equals(after.position()), "GK completion did not commit destination position.");
				context.record("d2.managed.profileId", profileId);
				context.record("d2.managed.beforeAdena", before.inventory().itemCount(57));
				context.record("d2.managed.afterAdena", after.inventory().itemCount(57));
				context.record("d2.managed.beforeAnchor", before.position().committedAnchorId());
				context.record("d2.managed.afterAnchor", after.position().committedAnchorId());
				context.record("d2.managed.beforeCursor", cursorBefore);
				context.record("d2.managed.afterCursor", advanced.snapshot().state().cursorEpochMinute());
				context.record("d2.managed.receipt", after.receipt().operationKey());
				arrived = true;
				break;
			}
			PhantomAssertions.assertTrue(arrived, "Managed Dwarf did not complete factual NORMAL GK within bounded historical intervals.");
			while (runtime.historical().status(profileId).orElseThrow().state().status() != Status.COMPLETE)
			{
				final var advanced = runtime.historical().advance(profileId, 1, 1);
				PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, advanced.status(), "D2 managed Dwarf catch-up did not complete: " + advanced.reason());
			}
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().materialize(profileId).status(), "D2 managed Dwarf failed canonical materialization parity.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().dematerialize(profileId).status(), "D2 managed Dwarf failed dematerialization parity.");
		}
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
			observeGeneratedPlannerRoute(baseline, runtime.planner(), context);

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
			if (runtime.transaction().load(profileId).state().state() == PhantomBackgroundState.State.DEAD)
			{
				final var recovery = runtime.background().recover(profileId, runtime.goals().load(profileId).orElseThrow().goal(), PhantomActivityState.WARM);
				PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.SUCCESS, recovery.status(), "Post-catch-up native death recovery failed: " + recovery.reason());
			}
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().materialize(profileId).status(), "COMPLETE catch-up did not reopen NORMAL materialization.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().dematerialize(profileId).status(), "Post-catch-up ordinary dematerialization failed.");
			context.record("goal033a.selectedNpcId", spec.npcId());
			context.record("goal033a.selectedFarmAnchor", spec.anchorId());
			context.record("goal033a.initialIngressAnchor", baseline.position().committedAnchorId());
		}
	}

	protected void testGeneratedRoute(PhantomTestContext context) throws Exception
	{
		final ManagedSnapshot managed = createManaged(context.seed());
		try (RuntimeHarness runtime = openRuntime(managed.profile().profileId(), new PhantomBackgroundTransaction()))
		{
			final long profileId = managed.profile().profileId();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().materialize(profileId).status(), "Generated route preflight materialization failed.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, runtime.materialization().dematerialize(profileId).status(), "Generated route preflight dematerialization failed.");
			final var begun = runtime.historical().begin(profileId, FROM_MINUTE, FROM_MINUTE + 4, context.seed());
			PhantomAssertions.assertEquals(ResultStatusCode.SUCCESS, begun.status(), "Generated route canonical baseline failed: " + begun.reason());
			observeGeneratedPlannerRoute(runtime.transaction().load(profileId).state(), runtime.planner(), context);
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
			PhantomAssertions.assertTrue(batch.mutated(), "A single oversized ordinary encounter still blocked history.");
			PhantomAssertions.assertEquals(16, batch.inventoryDelta().itemDeltas().size(), "Ordinary encounter exceeded the canonical mutation envelope.");
			PhantomAssertions.assertEquals((long) batch.encounters(), batch.groundLosses().get(73), "Excess ordinary loot was not recorded as ground loss.");
			final Drop oneObject = new Drop(500000, 0, 0, 100, 100, 1, 1, 1, null, 1, 100, false, 0);
			final Target cumulative = new Target(original.npcId(), original.level(), true, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, List.of(oneObject), 1);
			final var bounded = new PhantomBackgroundModel().evaluate(new BatchRequest(baseline, cumulative, input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
			PhantomAssertions.assertTrue(bounded.encounters() > PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS, "Cumulative ordinary loot overflow stopped historical time.");
			PhantomAssertions.assertTrue(bounded.mutated(), "A completed prefix was discarded at the object safety cap.");
			PhantomAssertions.assertEquals((long) bounded.encounters() - PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS, bounded.groundLosses().get(500000), "Cumulative overflow did not conserve ordinary drops.");
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

	private void observeGeneratedPlannerRoute(PhantomBackgroundState baseline, PhantomHistoricalBackgroundPlanner planner, PhantomTestContext context)
	{
		final int level = baseline.progress().level();
		final var targets = _production.knowledge().suitableTargets(new TargetQuery(Math.max(1, level - 2), level + 2, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(16))).values();
		int inspected = 0;
		for (var target : targets)
		{
			for (var area : target.representativeAreas())
			{
				if ((area.topologyNodeId() == null) || !area.topologyNodeId().startsWith("generated.") || (area.instanceId() != 0) || (area.totalConfiguredAmount() <= 0))
				{
					continue;
				}
				for (var anchor : _production.topology().snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()))
				{
					final var route = _production.topology().routeHint(baseline.position().committedAnchorId(), anchor.id()).orElse(null);
					if ((route == null) || route.edgeIds().isEmpty() || route.edgeIds().stream().noneMatch(edgeId -> edgeId.startsWith("generated.")))
					{
						continue;
					}
					if (++inspected > 8)
					{
						context.record("goal033a.generatedRoute", "not-observed-in-bounded-sample");
						return;
					}
					final int npcId = target.npc().npcId();
					final PhantomGoal goal = new PhantomGoal(1, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "background.catchup.plan", 0);
				if (planner.remainsSuitable(baseline, goal))
				{
					context.record("goal033a.generatedRoute", String.join(",", route.edgeIds()));
					context.record("goal033a.generatedAnchor", anchor.id());
					context.record("goal033a.generatedNpcId", npcId);
					return;
				}
				}
			}
		}
		context.record("goal033a.generatedRoute", "none-for-managed-class-and-ingress");
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
		final var lifecycleFailure = new AtomicReference<String>();
		final var delegated = PhantomMaterializationLifecyclePort.chain(historical, background);
		lifecycle.install(new PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long id, int objectId) { delegated.beforeMaterialize(id, objectId); }
			@Override public void beforeMaterialize(long id, int objectId, MaterializationPurpose purpose, String claim) { delegated.beforeMaterialize(id, objectId, purpose, claim); }
			@Override public void afterPlayerLoad(long id, Player player) { delegated.afterPlayerLoad(id, player); }
			@Override public void materializeSucceeded(long id, int objectId) { delegated.materializeSucceeded(id, objectId); }
			@Override public void materializeAborted(long id, int objectId) { delegated.materializeAborted(id, objectId); }
			@Override public void beforeStore(long id, Player player) { delegated.beforeStore(id, player); }
			@Override public void afterStore(long id, Player player)
			{
				try { delegated.afterStore(id, player); }
				catch (RuntimeException exception) { lifecycleFailure.set(exception.toString()); throw exception; }
			}
		});
		PhantomAssertions.assertTrue(materialization.start(), "Goal033A materialization service did not start.");
		return new RuntimeHarness(profileId, goals, transaction, background, materialization, planner, historical, lifecycleFailure);
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
		private final AtomicReference<String> _lifecycleFailure;
		private boolean _closed;

		private RuntimeHarness(long profileId, PhantomGoalStateStore goals, PhantomBackgroundTransaction transaction, PhantomBackgroundService background, PhantomMaterializationService materialization, PhantomHistoricalBackgroundPlanner planner, PhantomHistoricalBackgroundService historical, AtomicReference<String> lifecycleFailure)
		{
			_profileId = profileId;
			_goals = goals;
			_transaction = transaction;
			_background = background;
			_materialization = materialization;
			_planner = planner;
			_historical = historical;
			_lifecycleFailure = lifecycleFailure;
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

		private String lifecycleFailure()
		{
			return _lifecycleFailure.get();
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
