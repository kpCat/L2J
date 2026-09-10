/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.Lifecycle;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetCode;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService.ResetPreview;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorMode;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivitySnapshot;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomSupportedContentScriptBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Guarded production-composed Goal033 LIVING, reset/reseed and restart acceptance. */
public final class PhantomPopulationEcologyProductionGoal033Suite implements PhantomTestSuite
{
	private static final long SEED = 33003301L;
	private static final long CONVERGENCE_TIMEOUT_MILLIS = 720_000L;
	private static final long IRRELEVANT_WORLD_TIMER_START_MILLIS = CONVERGENCE_TIMEOUT_MILLIS + 300_000L;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _populationCodec = new PhantomPopulationStateCodec();
	private final PhantomPopulationEcologyStateCodec _ecologyCodec = new PhantomPopulationEcologyStateCodec();
	private final PhantomBackgroundCatchupStateCodec _catchupCodec = new PhantomBackgroundCatchupStateCodec();
	private PhantomPlayersConfig.Settings _settings;
	private PhantomProfileRepository _profiles;
	private boolean _environmentInitialized;
	private List<ManagedProfile> _population = List.of();
	private Set<Identity> _reseeded = Set.of();
	private Map<Long, Assignment> _assignments = Map.of();
	private PhantomPopulationEcologyService.Snapshot _steady;
	private long _reseedControlMillis;
	private long _convergenceMillis;
	private int _maximumPending;
	private boolean _partialReadyObserved;

	@Override
	public String id()
	{
		return "phantom-population-ecology-production-goal033";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal033 production suite used the wrong seed.");
		final PhantomPlayersConfig.Settings local = PhantomPlayersConfig.read(context.moduleRoot().resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini"));
		PhantomAssertions.assertTrue(local.enabled() && local.ecologyEnabled(), "Local-play production preset does not enable ecology.");
		PhantomAssertions.assertEquals("LIVING", local.ecologyPreset(), "Local-play production preset is not LIVING.");
		PhantomAssertions.assertEquals(10, local.populationTarget(), "Local-play population target changed.");
		PhantomAssertions.assertEquals(5, local.populationActiveTarget(), "Local-play ACTIVE target changed.");
		final ZoneOffset eveningZone = currentEveningZone();
		_settings = new PhantomPlayersConfig.Settings(true, true, local.maxMaterializedPhantoms(), local.maxScheduledPhantomProfiles(), local.schedulerPulseMillis(), local.schedulerProfilesPerPulse(), local.populationTarget(), local.populationActiveTarget(), local.populationCreationInFlight(), local.populationBoundariesPerPulse(), local.partyOperationsPerPulse(), local.socialCacheProfiles(), eveningZone, true, local.ecologyPreset(), local.ecologyWorldAgeDays(), local.ecologyArchiveLimit());
		resetOperatorState();
		_environment.initialize(context, IRRELEVANT_WORLD_TIMER_START_MILLIS);
		_environmentInitialized = true;
		PhantomSupportedContentScriptBootstrap.loadGoal036Owners(context);
		_profiles = PhantomProfileRepository.open();
		final long recoveredProfiles = scalar("SELECT COUNT(*) FROM phantom_profiles");
		if (recoveredProfiles > 0)
		{
			PhantomAssertions.assertEquals(recoveredProfiles, scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='population.ecology'"), "Refusing to clean profiles not proven to belong to the interrupted Goal033 production suite.");
			cleanupOwnedPopulation();
		}
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal033 production suite requires a clean guarded Phantom profile table.");
		context.record("goal033.production.recoveredProfiles", recoveredProfiles);
		context.record("goal033.production.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal033.production.settings", "population=10,active=5,preset=LIVING,worldAgeDays=" + _settings.ecologyWorldAgeDays() + ",zone=" + eveningZone);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-goal032-cold-reseed-living-convergence", this::testColdReseedLiving);
		registry.add("02-production-domains-status-and-restart", this::testProductionRestart);
	}

	private void testColdReseedLiving(PhantomTestContext context) throws Exception
	{
		startRuntime();
		awaitReadyPopulation(60_000L);
		final Set<Identity> old = identities();
		PhantomAssertions.assertEquals(10, old.size(), "Initial production population did not contain ten identities.");
		shutdownRuntime();

		final PhantomPopulationResetService reset = new PhantomPopulationResetService(System::currentTimeMillis, new SecureRandom(), reseedLifecycle(), _ -> { });
		final ResetPreview preview = reset.preview();
		PhantomAssertions.assertTrue(preview.safe(), "Goal032 reset preview blocked the Goal033 cold reseed: " + preview.blockers());
		PhantomAssertions.assertEquals(10, preview.identities(), "Goal032 reset preview did not report the ten initial identities.");
		final long reseedStarted = System.nanoTime();
		final var result = reset.confirm(preview.confirmationToken(), true);
		_reseedControlMillis = elapsedMillis(reseedStarted);
		PhantomAssertions.assertEquals(ResetCode.RESET_RESEEDED, result.code(), "Goal032 reset/reseed did not restart the production ecology runtime.");
		PhantomAssertions.assertTrue(result.resetCommitted() && result.reseeded(), "Goal032 reset/reseed result flags drifted.");
		PhantomAssertions.assertTrue(PhantomSystem.hasConfiguredInstance(), "Goal032 reseed returned without a production runtime.");

		awaitReadyPopulation(60_000L);
		awaitEcologyInventory();
		_reseeded = identities();
		PhantomAssertions.assertEquals(10, _reseeded.size(), "LIVING reseed did not restore ten real identities.");
		PhantomAssertions.assertTrue(disjointProfiles(old, _reseeded), "LIVING reseed reused an old profile ID.");
		PhantomAssertions.assertTrue(disjointCharacters(old, _reseeded), "LIVING reseed reused an old character ID.");
		PhantomAssertions.assertTrue(disjointAccounts(old, _reseeded), "LIVING reseed reused an old reserved account.");
		final PhantomPopulationEcologyService.Snapshot cold = PhantomSystem.operatorStatus().ecology();
		PhantomAssertions.assertTrue(cold.pendingCatchup() > 0, "Production startup completed all LIVING history synchronously instead of exposing bounded pending catch-up.");

		final long convergenceStarted = System.nanoTime();
		awaitEcologySteady();
		_convergenceMillis = elapsedMillis(convergenceStarted);
		_assignments = assignments();
		assertCardinalityAndHistograms();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM (SELECT profile_id FROM phantom_profile_components WHERE component_type='population.ecology' GROUP BY profile_id HAVING COUNT(*) > 1) duplicate_ecology"), "Duplicate durable ecology components were created.");
		PhantomAssertions.assertTrue(scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='background.catchup'") > 0, "Real Goal033A produced no durable catch-up state.");
		PhantomAssertions.assertTrue(_steady.historicalIntervals() > 0, "Production LIVING catch-up executed no real Goal033A intervals.");
		PhantomAssertions.assertEquals(_steady.historicalIntervals(), _steady.productiveMinutes(), "Goal033 production metrics disagree on causal interval/minute work.");
		assertCatchupComplete();
		PhantomAssertions.assertTrue(_steady.failures() <= 10, "Production ecology exceeded the bounded one-conflict-per-profile retry envelope: " + _steady.failures());
		if (_steady.failures() > 0)
		{
			PhantomAssertions.assertEquals("catchup.goal.conflict", _steady.lastFailure(), "Production ecology ended with an unexpected retry reason.");
		}
		PhantomAssertions.assertTrue((_steady.maximumPulseProfiles() <= 4) && (_steady.maximumPulseIntervals() <= 16), "Production ecology exceeded the catalog pulse bounds.");
		context.record("goal033.production.reseedControlMillis", _reseedControlMillis);
		context.record("goal033.production.convergenceMillis", _convergenceMillis);
		context.record("goal033.production.pending", "max=" + _maximumPending + ",partialReadyObserved=" + _partialReadyObserved + ",final=0");
		context.record("goal033.production.work", "productiveMinutes=" + _steady.productiveMinutes() + ",goal033aIntervals=" + _steady.historicalIntervals() + ",maxPulse=" + _steady.maximumPulseProfiles() + "/" + _steady.maximumPulseIntervals());
		context.record("goal033.production.errors", "recoverableConflicts=" + _steady.failures() + ",finalCatchupFailed=0,lastRetry=" + _steady.lastFailure());
		context.record("goal033.production.levelHistogram", PhantomSystem.operatorStatus().levelHistogram());
	}

	private void testProductionRestart(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(10, _reseeded.size(), "Cold LIVING case did not preserve ten reseeded identities.");
		final PhantomScheduler generationOneOwner = Objects.requireNonNull(PhantomSystem.configuredScheduler());
		final Set<Long> generationOneActive = awaitCanonicalActivePopulation("generation one");
		final PhantomSystem.OperatorStatus beforeRestart = PhantomSystem.operatorStatus();
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.RUNNING, beforeRestart.schedulerState(), "Production Scheduler is not RUNNING before restart.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.State.RUNNING, beforeRestart.decisionState(), "Production Decision Engine is not RUNNING before restart.");
		PhantomAssertions.assertEquals(10, Objects.requireNonNull(PhantomSystem.configuredScheduler()).snapshot().registered(), "Production Scheduler did not retain ten LIVING registrations.");
		final var live = PhantomSystem.configuredShutdownSnapshot();
		PhantomAssertions.assertEquals(PhantomSocialService.ServiceState.RUNNING, live.socialState(), "Production Social service is not RUNNING.");
		PhantomAssertions.assertFalse("none".equals(live.socialCatalogHash()), "Production Social service has no loaded catalog.");
		shutdownRuntime();
		assertCanonicalPopulationDrained(generationOneActive);

		startRuntime();
		PhantomAssertions.assertFalse(generationOneOwner == PhantomSystem.configuredScheduler(), "Cold restart reused the generation-one Scheduler owner.");
		awaitReadyPopulation(60_000L);
		awaitEcologyInventory();
		await(60_000L, () -> PhantomSystem.operatorStatus().ecology().pulses() >= 2, "Restarted ecology did not execute two production pulses.");
		awaitEcologySteady();
		PhantomAssertions.assertEquals(_reseeded, identities(), "Restart changed or duplicated the LIVING identity set.");
		PhantomAssertions.assertEquals(_assignments, assignments(), "Restart rerolled an immutable LIVING ecology assignment.");
		assertCardinalityAndHistograms();
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Restart changed Phantom profile cardinality.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(DISTINCT character_object_id) FROM phantom_profiles"), "Restart duplicated a character link.");
		PhantomAssertions.assertEquals(10L, scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='population.ecology'"), "Restart duplicated or lost ecology state.");
		PhantomAssertions.assertTrue(scalar("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='background.catchup'") <= 10L, "Restart duplicated Goal033A catch-up state.");
		final PhantomSystem.OperatorStatus status = PhantomSystem.operatorStatus();
		PhantomAssertions.assertEquals(PhantomSystem.State.RUNNING, status.runtimeState(), "Restarted ecology runtime is not RUNNING.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.RUNNING, status.schedulerState(), "Restarted Scheduler is not RUNNING.");
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.State.RUNNING, status.decisionState(), "Restarted Decision Engine is not RUNNING.");
		PhantomAssertions.assertEquals(Preset.LIVING, status.ecology().preset(), "Restarted production ecology is not LIVING.");
		PhantomAssertions.assertEquals(0, status.ecology().pendingCatchup(), "Restart did not return LIVING population to steady state.");
		final Set<Long> generationTwoActive = awaitCanonicalActivePopulation("generation two");
		PhantomAssertions.assertEquals(generationOneActive, generationTwoActive, "Cold restart changed the schedule-aware ACTIVE population.");
		context.record("goal033.production.domains", "scheduler=RUNNING/registered10,decision=RUNNING,backgroundIntervals=" + _steady.historicalIntervals() + ",social=RUNNING");
		context.record("goal033.production.restart", "profiles=10,ecology=10,assignmentsStable=true,pending=0,active=" + generationTwoActive + ",levels=" + status.levelHistogram());
		shutdownRuntime();
	}

	private Set<Long> awaitCanonicalActivePopulation(String generation) throws Exception
	{
		final long deadline = System.nanoTime() + 60_000_000_000L;
		MaterializationObservation latest = observeMaterialization();
		while (System.nanoTime() < deadline)
		{
			latest = observeMaterialization();
			if (latest.converged())
			{
				return latest.requestedActive();
			}
			Thread.sleep(20L);
		}
		throw new AssertionError("Production 10/5 " + generation + " did not materialize its schedule-aware ACTIVE population: " + latest);
	}

	private MaterializationObservation observeMaterialization()
	{
		final Set<Long> requestedActive = new HashSet<>();
		final Set<Long> effectiveActive = new HashSet<>();
		final Set<Long> canonicalOnline = new HashSet<>();
		final Set<Long> stableActive = new HashSet<>();
		final List<PhantomActivitySnapshot> scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler()).list();
		for (PhantomActivitySnapshot snapshot : scheduler)
		{
			if (snapshot.requestedState() == PhantomActivityState.ACTIVE)
			{
				requestedActive.add(snapshot.profileId());
			}
			if (snapshot.effectiveState() == PhantomActivityState.ACTIVE)
			{
				effectiveActive.add(snapshot.profileId());
				if (snapshot.transitionStatus() == PhantomActivityTransitionStatus.STABLE)
				{
					stableActive.add(snapshot.profileId());
				}
			}
		}
		for (ManagedProfile row : _population)
		{
			final Integer characterObjectId = row.profile().characterObjectId();
			final Player player = characterObjectId == null ? null : World.getInstance().getPlayer(characterObjectId);
			if ((player != null) && player.isOnline())
			{
				canonicalOnline.add(row.profile().profileId());
			}
		}
		return new MaterializationObservation(Set.copyOf(requestedActive), Set.copyOf(effectiveActive), Set.copyOf(canonicalOnline), Set.copyOf(stableActive), List.copyOf(scheduler));
	}

	private void assertCanonicalPopulationDrained(Set<Long> generationOneActive) throws Exception
	{
		for (ManagedProfile row : _population)
		{
			if (generationOneActive.contains(row.profile().profileId()))
			{
				PhantomAssertions.assertTrue(World.getInstance().getPlayer(Objects.requireNonNull(row.profile().characterObjectId())) == null, "Generation-one drain retained a canonical Player for profile " + row.profile().profileId() + ".");
			}
		}
	}

	private void awaitEcologyInventory() throws Exception
	{
		await(60_000L, () ->
		{
			final PhantomPopulationEcologyService.Snapshot ecology = PhantomSystem.operatorStatus().ecology();
			return ecology.enabled() && ecology.inventoryReady() && (ecology.managed() == 10) && (ecology.archived() == 0);
		}, "Production ecology inventory did not load ten managed LIVING assignments.");
	}

	private void awaitEcologySteady() throws Exception
	{
		final long deadline = System.nanoTime() + (CONVERGENCE_TIMEOUT_MILLIS * 1_000_000L);
		PhantomPopulationEcologyService.Snapshot latest = PhantomSystem.operatorStatus().ecology();
		while (System.nanoTime() < deadline)
		{
			latest = PhantomSystem.operatorStatus().ecology();
			final int ready = initiallyReadyCount();
			_maximumPending = Math.max(_maximumPending, latest.pendingCatchup());
			_partialReadyObserved |= (ready > 0) && (ready < 10);
			if (latest.inventoryReady() && (latest.managed() == 10) && (latest.pendingCatchup() == 0) && (ready == 10))
			{
				_steady = latest;
				return;
			}
			Thread.sleep(50L);
		}
		throw new AssertionError("LIVING catch-up did not converge: pending=" + latest.pendingCatchup() + ", failures=" + latest.failures() + ", last=" + latest.lastFailure());
	}

	private int initiallyReadyCount()
	{
		int ready = 0;
		for (ManagedProfile row : ecologyRows())
		{
			final PhantomPopulationEcologyState state = _ecologyCodec.decode(row.component().payload());
			if ((state.disposition() == Disposition.MANAGED) && state.initialCatchupComplete() && !state.requestPending())
			{
				ready++;
			}
		}
		return ready;
	}

	private void assertCardinalityAndHistograms() throws Exception
	{
		final PhantomSystem.OperatorStatus status = PhantomSystem.operatorStatus();
		final PhantomPopulationEcologyService.Snapshot ecology = status.ecology();
		PhantomAssertions.assertEquals(10, ecology.managed(), "Operator ecology status has the wrong managed count.");
		PhantomAssertions.assertEquals(10, ecology.paceHistogram().values().stream().mapToInt(Integer::intValue).sum(), "Pace histogram cardinality is inaccurate.");
		PhantomAssertions.assertEquals(10, ecology.personalityHistogram().values().stream().mapToInt(Integer::intValue).sum(), "Personality histogram cardinality is inaccurate.");
		PhantomAssertions.assertEquals(10, ecology.scheduleHistogram().values().stream().mapToInt(Integer::intValue).sum(), "Schedule histogram cardinality is inaccurate.");
		PhantomAssertions.assertEquals(databaseLevelHistogram(), status.levelHistogram(), "Operator level histogram differs from guarded DB facts.");
		PhantomAssertions.assertEquals(10, status.levelHistogram().values().stream().mapToInt(Integer::intValue).sum(), "Level histogram cardinality is inaccurate.");
	}

	private void assertCatchupComplete()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomBackgroundCatchupState.COMPONENT_TYPE, 0, 16);
		PhantomAssertions.assertTrue(!rows.isEmpty() && (rows.size() <= 10), "Production Goal033A catch-up component cardinality is invalid.");
		for (ManagedProfile row : rows)
		{
			final PhantomBackgroundCatchupState state = _catchupCodec.decode(row.component().payload());
			PhantomAssertions.assertEquals(PhantomBackgroundCatchupState.Status.COMPLETE, state.status(), "Production Goal033A retained a non-terminal catch-up state.");
			PhantomAssertions.assertEquals("", state.failureReason(), "Production Goal033A retained a terminal failure reason.");
		}
	}

	private Map<String, Integer> databaseLevelHistogram() throws Exception
	{
		final Map<String, Integer> histogram = new HashMap<>();
		final String sql = "SELECT CASE WHEN c.level <= 19 THEN '01-19' WHEN c.level <= 39 THEN '20-39' WHEN c.level <= 60 THEN '40-60' WHEN c.level <= 75 THEN '61-75' ELSE '76-85' END AS level_bucket, COUNT(*) AS bucket_count FROM characters c JOIN phantom_profiles p ON p.character_object_id=c.charId GROUP BY level_bucket";
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql);
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				histogram.put(result.getString("level_bucket"), result.getInt("bucket_count"));
			}
		}
		return Map.copyOf(histogram);
	}

	private Map<Long, Assignment> assignments()
	{
		final Map<Long, Assignment> assignments = new HashMap<>();
		for (ManagedProfile row : ecologyRows())
		{
			final PhantomPopulationEcologyState state = _ecologyCodec.decode(row.component().payload());
			assignments.put(row.profile().profileId(), Assignment.capture(state));
		}
		PhantomAssertions.assertEquals(10, assignments.size(), "Production ecology assignment cardinality drifted.");
		return Map.copyOf(assignments);
	}

	private List<ManagedProfile> ecologyRows()
	{
		return _profiles.listManagedAfter(PhantomPopulationEcologyState.COMPONENT_TYPE, 0, 16);
	}

	private void startRuntime()
	{
		resetOperatorState();
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings), "Production-composed Goal033 runtime did not start.");
	}

	private void awaitReadyPopulation(long timeoutMillis) throws Exception
	{
		await(timeoutMillis, () ->
		{
			final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 16);
			if (rows.size() != 10)
			{
				return false;
			}
			for (ManagedProfile row : rows)
			{
				final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
				if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null) || !state.actualCharacterObjectId().equals(row.profile().characterObjectId()))
				{
					return false;
				}
			}
			_population = List.copyOf(rows);
			return true;
		}, "Production 10/5 population did not reach ten READY real identities.");
	}

	private Set<Identity> identities()
	{
		final Set<Identity> identities = new HashSet<>();
		for (ManagedProfile row : _population)
		{
			final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
			identities.add(new Identity(row.profile().profileId(), Objects.requireNonNull(state.actualCharacterObjectId()), state.reservedAccount()));
		}
		return Set.copyOf(identities);
	}

	private Lifecycle reseedLifecycle()
	{
		return new Lifecycle()
		{
			@Override
			public OperatorControlResult drain()
			{
				return PhantomSystem.operatorDrain();
			}

			@Override
			public OperatorControlResult reseed()
			{
				if (PhantomSystem.startConfiguredForTesting(_settings))
				{
					return control(OperatorControlCode.STARTED, true, PhantomSystem.State.RUNNING);
				}
				return control(OperatorControlCode.START_FAILED, PhantomSystem.hasConfiguredInstance(), PhantomSystem.hasConfiguredInstance() ? PhantomSystem.operatorStatus().runtimeState() : null);
			}
		};
	}

	private static OperatorControlResult control(OperatorControlCode code, boolean configured, PhantomSystem.State state)
	{
		return new OperatorControlResult(code, OperatorMode.DRAINED, configured, configured, state);
	}

	private static boolean disjointProfiles(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.profileId() == right.profileId()));
	}

	private static boolean disjointCharacters(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.characterObjectId() == right.characterObjectId()));
	}

	private static boolean disjointAccounts(Set<Identity> first, Set<Identity> second)
	{
		return first.stream().noneMatch(left -> second.stream().anyMatch(right -> left.accountName().equals(right.accountName())));
	}

	private static void shutdownRuntime() throws Exception
	{
		PhantomSystem.shutdownIfStarted();
		final long deadline = System.nanoTime() + 30_000_000_000L;
		while (PhantomSystem.hasConfiguredInstance() && (System.nanoTime() < deadline))
		{
			PhantomSystem.operatorDrain();
			if (PhantomSystem.hasConfiguredInstance())
			{
				Thread.sleep(20L);
			}
		}
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Goal033 shutdown retained a configured runtime.");
		PhantomSystem.resetOperatorModeForTesting();
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (PhantomSystem.hasConfiguredInstance())
			{
				shutdownRuntime();
			}
			if (DatabaseFactory.isInitialized() && (scalar("SELECT COUNT(*) FROM phantom_profiles") > 0))
			{
				cleanupOwnedPopulation();
			}
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal033 cleanup retained a Phantom profile.");
			PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Goal033 cleanup retained a Phantom component.");
			PhantomSystem.resetOperatorModeForTesting();
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			if (_environmentInitialized)
			{
				_environment.shutdown();
				_environmentInitialized = false;
			}
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	private static void cleanupOwnedPopulation()
	{
		final Lifecycle stopped = new Lifecycle()
		{
			@Override
			public OperatorControlResult drain()
			{
				return control(OperatorControlCode.ALREADY_DRAINED, false, null);
			}

			@Override
			public OperatorControlResult reseed()
			{
				return control(OperatorControlCode.CONFIG_DISABLED, false, null);
			}
		};
		final PhantomPopulationResetService cleanup = new PhantomPopulationResetService(System::currentTimeMillis, new SecureRandom(), stopped, _ -> { });
		final ResetPreview preview = cleanup.preview();
		PhantomAssertions.assertTrue(preview.safe(), "Goal033 cleanup preview was blocked: " + preview.blockers());
		final var result = cleanup.confirm(preview.confirmationToken(), false);
		PhantomAssertions.assertEquals(ResetCode.RESET_COMPLETE, result.code(), "Goal033 cleanup did not remove its Phantom-owned population.");
	}

	private static void resetOperatorState()
	{
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Operator reset retained a configured owner.");
		PhantomSystem.resetOperatorModeForTesting();
	}

	private static long scalar(String sql, Object... arguments) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < arguments.length; index++)
			{
				statement.setObject(index + 1, arguments[index]);
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Goal033 scalar query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static void await(long timeoutMillis, BooleanSupplier condition, String failure) throws Exception
	{
		final long deadline = System.nanoTime() + (timeoutMillis * 1_000_000L);
		while (System.nanoTime() < deadline)
		{
			if (condition.getAsBoolean())
			{
				return;
			}
			Thread.sleep(20L);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private static long elapsedMillis(long startedNanos)
	{
		return (System.nanoTime() - startedNanos) / 1_000_000L;
	}

	private static ZoneOffset currentEveningZone()
	{
		final var utc = Instant.now().atOffset(ZoneOffset.UTC);
		int offsetMinutes = ((20 * 60) + 30) - ((utc.getHour() * 60) + utc.getMinute());
		if (offsetMinutes > (12 * 60))
		{
			offsetMinutes -= 24 * 60;
		}
		else if (offsetMinutes < (-12 * 60))
		{
			offsetMinutes += 24 * 60;
		}
		return ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
	}

	private record Identity(long profileId, int characterObjectId, String accountName)
	{
	}

	private record MaterializationObservation(Set<Long> requestedActive, Set<Long> effectiveActive, Set<Long> canonicalOnline, Set<Long> stableActive, List<PhantomActivitySnapshot> scheduler)
	{
		private boolean converged()
		{
			return (requestedActive.size() == 5) && requestedActive.equals(effectiveActive) && requestedActive.equals(canonicalOnline) && requestedActive.equals(stableActive);
		}
	}

	private record Assignment(String catalogHash, Preset preset, long generation, long ordinal, long assignedAt, long virtualJoin, Pace pace, int productiveShare, Personality personality, Map<Integer, Integer> socialTraits, String schedule, long turnoverEligible, long replacesProfileId, Disposition disposition)
	{
		private static Assignment capture(PhantomPopulationEcologyState state)
		{
			return new Assignment(state.catalogHash(), state.preset(), state.ecologyGeneration(), state.assignmentOrdinal(), state.assignedAtEpochMinute(), state.virtualJoinEpochMinute(), state.pace(), state.productiveShareBasisPoints(), state.personality(), state.initialSocialTraits(), state.scheduleTemplate(), state.turnoverEligibleEpochMinute(), state.replacesProfileId(), state.disposition());
		}
	}
}
