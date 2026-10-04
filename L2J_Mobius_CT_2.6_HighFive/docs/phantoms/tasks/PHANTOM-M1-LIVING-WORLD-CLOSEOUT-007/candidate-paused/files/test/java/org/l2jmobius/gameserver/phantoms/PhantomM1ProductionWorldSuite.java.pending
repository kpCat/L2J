/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomM1PopulationFixture;
import org.l2jmobius.tests.phantoms.PhantomSupportedContentScriptBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Public TEST bridge to the existing configured production factory and unchanged full population. */
public final class PhantomM1ProductionWorldSuite implements PhantomTestSuite
{
	public enum Mode { FIXTURE, WORLD, DEATH, CONTINUATION, GEOGRAPHY, RESTART, RESTORE_INTERRUPTED, HISTORICAL_NATIVE, BOOTSTRAP, HISTORICAL_CONTEXT, HISTORICAL_NATIVE_NORMAL, HISTORICAL_NATIVE_CLAIM_VERSION, HISTORICAL_NATIVE_CLAIM_PAYLOAD }
	private static final Path SETTINGS = PhantomM1PopulationFixture.SOURCE_CONFIG.resolveSibling("Custom/PhantomPlayers.ini");
	private static final long SCENE_MILLIS = 480_000L;
	private static final long OBSERVE_MILLIS = 180_000L;
	private static final long CLEANUP_MILLIS = 45_000L;
	private final Mode _mode;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomM1PopulationFixture _fixture;
	private PhantomPlayersConfig.Settings _settings;
	private PhantomMaterializationService _materialization;
	private PhantomM1NativeLoadObserver _loadObserver;
	private Player _human;
	private PhantomM1WorldContinuationChecks.StockScene _stock;
	private NativeSqlDiagnostic _sqlDiagnostic;
	private boolean _environmentAttempted;
	private long _sceneDeadline;
	private List<Long> _cohort = List.of();
	private int _sampleOrdinal;
	private PhantomGameKnowledgeService _historicalKnowledge;
	private org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery _historicalTopology;
	private L2jPhantomBackgroundAuthority _historicalAuthority;
	private final Map<PhantomM1HistoricalNativeRefreshChecks.Mode, PhantomM1HistoricalNativeRefreshChecks.Probe> _historicalProbes = new java.util.LinkedHashMap<>();
	private final java.util.ArrayList<HistoricalRuntime> _historicalRuntimes = new java.util.ArrayList<>();
	private boolean _historicalRetained;
	private PhantomM1InitialMetadataChecks _metadataChecks;
	private PhantomM1HistoricalNativeContextChecks.Probe _historicalContextProbe;
	private HistoricalContextRuntime _historicalContextRuntime;

	public PhantomM1ProductionWorldSuite() { this(Mode.WORLD); }
	public PhantomM1ProductionWorldSuite(Mode mode) { _mode = java.util.Objects.requireNonNull(mode); }
	@Override public String id() { return "phantom-m1-production-world-007-" + _mode.name().toLowerCase(java.util.Locale.ROOT); }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String config = System.getProperty("phantom.test.config");
		PhantomAssertions.assertTrue(config != null && !config.isBlank(), "W requires the existing explicit guarded TEST config.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance() || DatabaseFactory.isInitialized(), "W native environment is already running.");
		context.record("w.mode", _mode);
		context.record("w.requiredRemaining", "Only the selected native mode is exercised; other W modes and client rendering remain REQUIRED independently.");
		if (_mode == Mode.RESTORE_INTERRUPTED)
		{
			PhantomM1PopulationFixture.restoreInterrupted(context, Path.of(config));
			return;
		}
		_fixture = PhantomM1PopulationFixture.apply(context, PhantomM1PopulationFixture.SOURCE_CONFIG, Path.of(config));
		if (_mode == Mode.FIXTURE) { return; }
		if (isHistoricalNativeMode() || _mode == Mode.HISTORICAL_CONTEXT)
		{
			_environmentAttempted = true; _environment.initialize(context);
			prepareHistoricalNative(context);
			return;
		}
		_settings = PhantomPlayersConfig.read(SETTINGS);
		assertSettings();
		final var readyIdentities = new java.util.HashMap<Long, Integer>();
		for (var identity : _fixture.readyIdentities())
		{
			PhantomAssertions.assertTrue(readyIdentities.put(identity.profileId(), identity.characterObjectId()) == null, "W duplicate frozen READY profile identity.");
		}
		_loadObserver = new PhantomM1NativeLoadObserver(readyIdentities);
		context.record("w.settingsSourceSha256", HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(SETTINGS))));
		context.record("w.settings", "population=1280,active=64,materialized=128,scheduled=10000,pulse=100,profilesPerPulse=256,zone=UTC,preset=LIVING,worldAge=-1");
		_environmentAttempted = true;
		_environment.initialize(context);
		PhantomSupportedContentScriptBootstrap.loadGoal036Owners(context);
		context.record("w.bootstrap.worldNpcCount", World.getInstance().getVisibleObjects().stream().filter(org.l2jmobius.gameserver.model.actor.Npc.class::isInstance).count());
		context.record("w.bootstrap.worldMonsterCount", World.getInstance().getVisibleObjects().stream().filter(org.l2jmobius.gameserver.model.actor.instance.Monster.class::isInstance).count());
		context.record("w.bootstrap.path", "EXISTING_MASTER_HANDLER_FILE_TO_NATIVE_SPAWN_DATA_INIT; source NPC counts/stats/respawn unchanged");
		_sqlDiagnostic = new NativeSqlDiagnostic();
		context.record("w.nativeSql.originalMaximumPoolSize", _sqlDiagnostic.maximumPoolSize());
		if (_mode == Mode.BOOTSTRAP) { _metadataChecks = PhantomM1InitialMetadataChecks.beforeRuntime(context, _fixture, _settings.schedulerProfilesPerPulse()); }
		startRuntime();
		_materialization = PhantomSystem.configuredMaterializationService();
		PhantomAssertions.assertTrue(_materialization != null && PhantomSystem.configuredScheduler() != null, "W production composition omitted native owners.");
		assertPopulation(context, "startup");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.FIXTURE) { registry.add("01-guarded-full-population-apply-restore", this::fixtureRestore); }
		else if (_mode == Mode.RESTORE_INTERRUPTED) { registry.add("01-interrupted-private-journal-restore", context -> context.record("w.fixture.recoveryFinished", true)); }
		else if (_mode == Mode.WORLD) { registry.add("01-production-natural-cohort-native-cycles-W01-W03", this::naturalWorld); }
		else if (_mode == Mode.DEATH) { registry.add("01-production-native-death-recovery-next-cycle-W04", this::deathWorld); }
		else if (_mode == Mode.CONTINUATION) { registry.add("01-production-prewarm-soft-return-background-reentry-W05-W06", this::continuationWorld); }
		else if (_mode == Mode.GEOGRAPHY) { registry.add("01-production-elven-gremlin-land-water-populated-range-W07", this::geographyWorld); }
		else if (_mode == Mode.RESTART) { registry.add("01-production-save-restart-reconcile-resource-soak-W08", this::restartWorld); }
		else if (_mode == Mode.BOOTSTRAP) { registry.add("01-original-full10000-metadata-before-historical-model", context -> _metadataChecks.assertBootstrap(context)); }
		else if (_mode == Mode.HISTORICAL_CONTEXT) { registry.add("01-original-native-UNKNOWN-attestation-before-historical-RUNNING", this::historicalContext); }
		else if (isHistoricalNativeMode())
		{
			int ordinal = 0;
			for (var mode : historicalNativeModes())
			{
				registry.add(String.format(java.util.Locale.ROOT, "%02d-original-imported-historical-native-%s", ++ordinal, mode.name().toLowerCase(java.util.Locale.ROOT)), context -> historicalNative(context, mode));
			}
		}
	}

	private boolean isHistoricalNativeMode()
	{
		return _mode == Mode.HISTORICAL_NATIVE || _mode == Mode.HISTORICAL_NATIVE_NORMAL || _mode == Mode.HISTORICAL_NATIVE_CLAIM_VERSION || _mode == Mode.HISTORICAL_NATIVE_CLAIM_PAYLOAD;
	}

	private List<PhantomM1HistoricalNativeRefreshChecks.Mode> historicalNativeModes()
	{
		return switch (_mode)
		{
			case HISTORICAL_NATIVE -> List.of(PhantomM1HistoricalNativeRefreshChecks.Mode.POSITIVE, PhantomM1HistoricalNativeRefreshChecks.Mode.WRONG_REQUEST);
			case HISTORICAL_NATIVE_NORMAL -> List.of(PhantomM1HistoricalNativeRefreshChecks.Mode.NORMAL_OLD_HASH);
			case HISTORICAL_NATIVE_CLAIM_VERSION -> List.of(PhantomM1HistoricalNativeRefreshChecks.Mode.CLAIM_VERSION);
			case HISTORICAL_NATIVE_CLAIM_PAYLOAD -> List.of(PhantomM1HistoricalNativeRefreshChecks.Mode.CLAIM_PAYLOAD);
			default -> throw new IllegalStateException("Historical native mode is required.");
		};
	}

	/** Existing BackgroundSuite production loaders, then Goal033A native lifecycle composition. */
	private void prepareHistoricalNative(PhantomTestContext context)
	{
		org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance();
		org.l2jmobius.gameserver.data.xml.SpawnData.getInstance();
		org.l2jmobius.gameserver.data.xml.DoorData.getInstance();
		final var backend = new org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend();
		final var topology = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader(Path.of("data/phantoms/topology"), backend, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults()).load(1);
		_historicalTopology = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery(topology, backend, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics());
		final var policy = org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy.productionDefaults();
		final var knowledgeBackend = new org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend();
		_historicalKnowledge = new PhantomGameKnowledgeService(new org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder(knowledgeBackend,
			new org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy),
			new org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, policy), _historicalTopology, policy));
		PhantomAssertions.assertTrue(_historicalKnowledge.start(), "Original historical production Knowledge loader failed.");
		final var knowledge = _historicalKnowledge.query();
		final var progressionPolicy = org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy.productionDefaults();
		final var progression = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder().build(new org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend(null, Path.of("."), () -> knowledge).load(progressionPolicy), progressionPolicy);
		final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _historicalTopology);
		_historicalAuthority = new L2jPhantomBackgroundAuthority(() -> knowledge, () -> _historicalTopology, () -> progression, () -> commerce, travel);
		final var profiles = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(profiles); final var catchups = new PhantomBackgroundCatchupStore(profiles, goals); final var transaction = new PhantomBackgroundTransaction();
		if (_mode == Mode.HISTORICAL_CONTEXT)
		{
			try { _historicalContextProbe = PhantomM1HistoricalNativeContextChecks.probeImported(context, _fixture, profiles, goals, _historicalAuthority); }
			catch (Exception failure) { throw new IllegalStateException("Original historical context fixture setup failed.", failure); }
			return;
		}
		Long witness = null; Long wrongRequest = null;
		for (var identity : _fixture.readyIdentities())
		{
			final var state = transaction.load(identity.profileId()); final var claim = catchups.load(identity.profileId()).orElse(null); final var goal = goals.load(identity.profileId()).orElse(null);
			if (!state.successful() || state.state() == null || claim == null || goal == null) { continue; }
			if ((state.state().state() == PhantomBackgroundState.State.READY || state.state().state() == PhantomBackgroundState.State.DEAD) && !state.state().hashes().equals(_historicalAuthority.hashes())
				&& claim.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE && goal.goal().status() == PhantomGoalStatus.ACTIVE && goal.goal().goalId() == claim.state().goalId() && goal.goal().revision() == claim.state().goalRevision())
			{
				if (identity.characterObjectId() == 268487394) { witness = identity.profileId(); }
				else if (wrongRequest == null) { wrongRequest = identity.profileId(); }
				if (witness != null && (_mode != Mode.HISTORICAL_NATIVE || wrongRequest != null)) { break; }
			}
		}
		PhantomAssertions.assertTrue(witness != null, "INVALID historical-native: exact prior W10 native-maxima witness char268487394 is absent from factual imported eligible identities; no reroll.");
		final var candidates = new java.util.LinkedHashMap<PhantomM1HistoricalNativeRefreshChecks.Mode, Long>();
		for (var mode : historicalNativeModes())
		{
			final Long id = mode == PhantomM1HistoricalNativeRefreshChecks.Mode.WRONG_REQUEST ? wrongRequest : witness;
			PhantomAssertions.assertTrue(id != null, "INVALID historical-native: distinct original eligible WRONG_REQUEST identity is absent.");
			candidates.put(mode, id);
			_historicalProbes.put(mode, PhantomM1HistoricalNativeRefreshChecks.probeImported(context, id, mode, profiles, goals, _historicalAuthority, _fixture));
		}
		context.record("historicalNative.sourceWitness", "prior actual W10 original native load char268487394 DEAD maxHp176/160 maxMp157/145 maxCp88/80; exact unchanged import; each loaded control uses its own fresh fixture/JVM; no manufactured maxima or reroll");
		context.record("historicalNative.importedCandidates", candidates);
		context.record("historicalNative.requiredRemaining", "All five controls require actual native runs: historical_native POSITIVE/WRONG_REQUEST, historical_native_normal, historical_native_claim_version, historical_native_claim_payload; each mode has original full import/stop/CAS; no W cohort credit.");
		context.record("historicalNative.path", "original full population before-image before bootstrap; unchanged imported identities/components; local original production classes; strict native maxima mismatch checked for every actual afterPlayerLoad; no W cohort credit");
	}

	private void historicalNative(PhantomTestContext context, PhantomM1HistoricalNativeRefreshChecks.Mode mode) throws Exception
	{
		PhantomAssertions.assertFalse(_historicalRetained, "Earlier exact historical owner retained; refuse further native setup and population restore.");
		final var probe = _historicalProbes.get(mode); PhantomAssertions.assertTrue(probe != null, "Original factual historical probe is absent.");
		final var profiles = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(profiles); final var lifecycle = new PhantomMaterializationLifecycleBridge(); final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
			new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point -> { }, lifecycle, 5_000, 10_000);
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, PhantomBackgroundTransaction.ObjectIdAllocator.production(), probe.faults());
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long profileId, String sourceKey, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _historicalAuthority, new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
		final var historical = new PhantomHistoricalBackgroundService(profiles, goals, new PhantomHistoricalBackgroundPlanner(_historicalKnowledge.query(), _historicalTopology, _historicalAuthority), background, materialization);
		probe.bind(materialization, historical, background, transaction); lifecycle.install(probe);
		final var runtime = new HistoricalRuntime(probe, materialization, background); _historicalRuntimes.add(runtime);
		Throwable primary = null;
		try
		{
			PhantomAssertions.assertTrue(background.start() && materialization.start(), "Original focused historical services did not start.");
			context.record("historicalNative.receipt." + mode, probe.run());
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { stopHistoricalRuntime(runtime); }
			catch (Exception | Error failure) { _historicalRetained = true; context.record("historicalNative.retainedJournal", "original population journal retained; unsafe exact owner remains: " + failure); if (primary != null) { primary.addSuppressed(failure); } else { throw failure; } }
		}
	}

	private static void stopHistoricalRuntime(HistoricalRuntime runtime)
	{
		runtime.materialization().shutdown();
		PhantomAssertions.assertTrue(runtime.probe().selectedNativeOwnerStopped(), "Historical native owner retained World/lease/accounting; original population restore forbidden.");
		runtime.background().beginStop(); PhantomAssertions.assertTrue(runtime.background().finishStop(), "Original focused historical Background service did not stop.");
	}
	private record HistoricalRuntime(PhantomM1HistoricalNativeRefreshChecks.Probe probe, PhantomMaterializationService materialization, PhantomBackgroundService background) { }

	private void historicalContext(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertFalse(_historicalRetained, "Earlier native owner retained; refuse UNKNOWN fixture setup.");
		final var probe = _historicalContextProbe; PhantomAssertions.assertTrue(probe != null, "Original UNKNOWN probe is absent.");
		final var profiles = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(profiles); final var lifecycle = new PhantomMaterializationLifecycleBridge(); final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, point -> { }, lifecycle, 5_000, 10_000);
		final var transaction = new PhantomBackgroundTransaction(DatabaseFactory::getConnection, PhantomBackgroundTransaction.ObjectIdAllocator.production(), probe.faults());
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _historicalAuthority, new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
		final var historical = new PhantomHistoricalBackgroundService(profiles, goals, new PhantomHistoricalBackgroundPlanner(_historicalKnowledge.query(), _historicalTopology, _historicalAuthority), background, materialization);
		probe.bind(materialization, historical, background, transaction); lifecycle.install(probe);
		_historicalContextRuntime = new HistoricalContextRuntime(probe, materialization, background);
		Throwable primary = null;
		try
		{
			PhantomAssertions.assertTrue(background.start() && materialization.start(), "Original UNKNOWN native services did not start.");
			context.record("historicalContext.receipt", probe.run());
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally
		{
			try { stopHistoricalContextRuntime(); }
			catch (Exception | Error failure) { _historicalRetained = true; context.record("historicalContext.retainedJournal", "original full population journal retained; exact native owner/cleanup unknown"); if (primary != null) { if (failure != primary) { primary.addSuppressed(failure); } } else { throw failure; } }
		}
	}

	private void stopHistoricalContextRuntime()
	{
		final var runtime = _historicalContextRuntime;
		if (runtime == null) { return; }
		final var stopped = runtime.materialization().shutdown();
		PhantomAssertions.assertTrue(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED && stopped.failedProfileIds().isEmpty() && runtime.probe().selectedNativeOwnerStopped(), "UNKNOWN exact native owner retained/unknown; original complete restore forbidden.");
		runtime.background().beginStop(); PhantomAssertions.assertTrue(runtime.background().finishStop(), "Original UNKNOWN Background service did not stop.");
	}
	private record HistoricalContextRuntime(PhantomM1HistoricalNativeContextChecks.Probe probe, PhantomMaterializationService materialization, PhantomBackgroundService background) { }

	private void startRuntime()
	{
		if (_metadataChecks != null)
		{
			_metadataChecks.runtimeStarting();
			PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings, _loadObserver, _metadataChecks::observe), "Existing production factory did not start BOOTSTRAP.");
			return;
		}
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(_settings, _loadObserver), "Existing production factory did not start W.");
	}

	private void fixtureRestore(PhantomTestContext context) throws Exception
	{
		_fixture.close();
		_fixture.close();
		_fixture = null;
		PhantomAssertions.assertEquals(context.measurements().get("w.fixture.beforeHash"), context.measurements().get("w.fixture.restoredHash"), "Guarded TEST before-image parity was not restored.");
		context.record("w.fixture.idempotentClose", true);
	}

	private void naturalWorld(PhantomTestContext context) throws Exception
	{
		final Scene scene = prepareScene(context);
		PhantomM1WorldContinuationChecks.move(context, "WORLD.contact", _human, contactRoute(scene), _sceneDeadline);
		recordCensus(context, "before");
		final Map<Long, Sample> baseline = new HashMap<>();
		final Map<Long, Sample> latest = new HashMap<>();
		final Map<Long, Long> lastUseful = new HashMap<>();
		final Set<Long> useful = new HashSet<>();
		final Set<Long> complete = new HashSet<>();
		final Set<Boolean> completedTypes = new HashSet<>();
		long firstSelected = select(Set.of());
		long secondSelected = 0;
		context.record("w.selected.first", firstSelected);
		final long observeDeadline = Math.min(_sceneDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OBSERVE_MILLIS));
		boolean accepted = false;
		while (System.nanoTime() < observeDeadline)
		{
			for (long id : _cohort)
			{
				final var snapshot = PhantomSystem.operatorM1TargetSnapshot(id, _human).orElse(null);
				context.record("w.sample." + _sampleOrdinal + "." + id, snapshot == null ? "MISSING" : snapshot);
				if (snapshot == null || !snapshot.worldPresent()) { continue; }
				final Sample sample = sample(snapshot);
				final Sample previousSample = latest.put(id, sample);
				lastUseful.putIfAbsent(id, sample.sampledAtNanos());
				if (previousSample != null && (sample.damage() > previousSample.damage() || sample.reward() > previousSample.reward() && (sample.exp() > previousSample.exp() || sample.sp() > previousSample.sp()) || sample.cycles() > previousSample.cycles())) { lastUseful.put(id, sample.sampledAtNanos()); }
				final Sample previous = baseline.putIfAbsent(id, sample);
				if (previous == null) { continue; }
				PhantomAssertions.assertTrue(previous.objectId() == sample.objectId() && previous.epoch() == sample.epoch(), "W frozen participant churned during native observation: " + id);
				PhantomAssertions.assertTrue(sample.sequence() >= previous.sequence(), "W native sequence regressed: " + id);
				PhantomAssertions.assertTrue(sample.sampledAtNanos() - lastUseful.get(id) < TimeUnit.SECONDS.toNanos(90), "Natural W participant has90s useful farm progress debt despite activity/phase flags: " + id);
				if (sample.damage() > previous.damage()) { useful.add(id); }
				if (sample.cycles() > previous.cycles() && sample.damage() > previous.damage() && sample.kills() > previous.kills() && sample.reward() > previous.reward() && sample.exp() > previous.exp() && sample.sp() > previous.sp())
				{
					complete.add(id);
					final Player actor = World.getInstance().getPlayer(sample.objectId());
					PhantomAssertions.assertTrue(actor != null, "Completed W native actor is absent.");
					completedTypes.add(actor.isMageClass());
					context.record("w.complete." + id, "object=" + sample.objectId() + ",epoch=" + sample.epoch() + ",mage=" + actor.isMageClass() + ",baseline=" + previous + ",current=" + sample);
				}
			}
			if (secondSelected == 0 && twoCycles(baseline.get(firstSelected), latest.get(firstSelected)))
			{
				secondSelected = select(Set.of(firstSelected));
				final var secondSnapshot = PhantomSystem.operatorM1TargetSnapshot(secondSelected, _human).orElseThrow();
				PhantomAssertions.assertTrue(secondSnapshot.worldPresent(), "Second server-selected W actor lacks an actual post-selection native lifetime; no stale baseline credit.");
				final Sample secondBaseline = sample(secondSnapshot);
				baseline.put(secondSelected, secondBaseline); latest.put(secondSelected, secondBaseline);
				complete.remove(secondSelected);
				context.record("w.selected.second", secondSelected);
				context.record("w.selected.secondBaseline", baseline.get(secondSelected));
			}
			final Set<Long> otherUseful = new HashSet<>(useful); otherUseful.remove(firstSelected); otherUseful.remove(secondSelected);
			if (secondSelected > 0 && twoCycles(baseline.get(secondSelected), latest.get(secondSelected)) && otherUseful.size() >= 2 && completedTypes.size() == 2)
			{
				accepted = true;
				break;
			}
			_sampleOrdinal++;
			Thread.sleep(1000L);
		}
		recordCensus(context, "after");
		context.record("w.cohort.useful", useful);
		context.record("w.cohort.complete", complete);
		context.record("w.cohort.completedMageAndMelee", completedTypes.size() == 2);
		for (long id : _cohort)
		{
			context.record("w.cohort.finalReadiness." + id, PhantomSystem.operatorReadinessProgress(id));
			PhantomAssertions.assertTrue(latest.containsKey(id), "Natural W cohort participant never materialized; denominator retained: " + id);
			final var finalTarget = PhantomSystem.operatorM1TargetSnapshot(id, _human).orElseThrow();
			PhantomAssertions.assertTrue(finalTarget.worldPresent(), "Natural W participant disappeared from the retained denominator: " + id);
			final Sample finalSample = sample(finalTarget);
			PhantomAssertions.assertTrue(finalSample.objectId() == latest.get(id).objectId() && finalSample.epoch() == latest.get(id).epoch() && finalSample.sequence() >= latest.get(id).sequence(), "Natural W participant lost its exact native owner at final census: " + id);
			PhantomAssertions.assertTrue(finalSample.sampledAtNanos() - lastUseful.get(id) < TimeUnit.SECONDS.toNanos(90), "Natural W participant retained90s useful farm debt at final census: " + id);
			final var admission = PhantomSystem.operatorAdmissionProfile(id).orElseThrow();
			PhantomAssertions.assertTrue(admission.lastMaterializationFailure() == null, "Natural W participant retains a native materialization failure: " + id);
			if (!useful.contains(id))
			{
				final var target = PhantomSystem.operatorM1TargetSnapshot(id, _human).orElseThrow();
				final Map<String, String> evidence = target.nativeEvidence();
				final String phase = evidence.getOrDefault("nativePhase", "NONE");
				final long since = number(evidence, "nativePhaseSinceNanos"); final long deadline = number(evidence, "nativePhaseDeadlineNanos"); final long sampled = number(evidence, "nativeEvidenceSampleNanos");
				PhantomAssertions.assertTrue(Set.of("ROUTE", "REGEN", "DEATH_RECOVERY").contains(phase) && since >= target.materializedAtNanos() && since <= sampled && deadline > sampled && deadline - since <= 120_000_000_000L && sampled - baseline.get(id).sampledAtNanos() < 90_000_000_000L, "Natural W participant has expired/unbounded phase or90s useful progress debt: " + id);
			}
		}
		PhantomAssertions.assertTrue(accepted, "W01-W03 missing two consecutive cycles for each selected actor, mage/melee cycles or>=2 OTHER useful actors within OBS180; frozen cohort retained.");
		assertPopulation(context, "afterNativeCycles");
	}

	private Scene prepareScene(PhantomTestContext context) throws Exception
	{
		_sceneDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SCENE_MILLIS);
		final Scene scene = chooseScene(context);
		_stock = PhantomM1WorldContinuationChecks.stockScene(context, scene.contact());
		_cohort = scene.cohort();
		context.record("w.cohort.frozen", _cohort); context.record("w.cohort.count", _cohort.size());
		context.record("w.scene.destination", scene.contact()); context.record("w.scene.nativeHumanSource", scene.source());
		_human = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_human != null && !_human.hasHeadlessOutboundSession(), "W ordinary native human must retain CLIENT_BOUND output.");
		_human.setXYZInvisible(scene.source().x(), scene.source().y(), scene.source().z());
		_human.setOnlineStatus(true, false); _human.spawnMe(); _human.setRunning();
		context.record("w.scene.nativeHumanMovement", "ordinary public setRunning; no speed/config/Phantom pose override");
		recordCensus(context, "prewarm");
		return scene;
	}

	private void deathWorld(PhantomTestContext context) throws Exception
	{
		final Scene scene = prepareScene(context);
		PhantomM1WorldContinuationChecks.move(context, "W04.contact", _human, contactRoute(scene), _sceneDeadline);
		final long selected = select(Set.of());
		await(60_000, () -> PhantomSystem.operatorM1TargetSnapshot(selected).map(PhantomSystem.OperatorM1TargetSnapshot::worldPresent).orElse(false), "W04 native selected actor did not materialize.");
		final Player victim = World.getInstance().getPlayer(PhantomSystem.operatorM1TargetSnapshot(selected).orElseThrow().objectId());
		final var attacker = _stock.monsters().stream().filter(npc -> npc.getInstanceId() == victim.getInstanceId() && PhantomM1WorldContinuationChecks.drySegment(PhantomM1WorldContinuationChecks.point(victim), new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId())))
			.sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.instance.Monster::getLevel).reversed().thenComparingLong(npc -> new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()).distanceSquared2D(point(victim))))
			.findFirst().orElseThrow(() -> new AssertionError("INVALID W04: original source scene has no ordinary stock attacker with a lawful native corridor; no attacker/stat/gear substitute."));
		recordCensus(context, "W04.before");
		PhantomM1WorldContinuationChecks.nativeDeathRecoveryCycle(context, selected, attacker, _loadObserver, _sceneDeadline);
		recordCensus(context, "W04.after"); assertPopulation(context, "W04.after");
	}

	private void continuationWorld(PhantomTestContext context) throws Exception
	{
		final Scene scene = prepareScene(context);
		final long selected = selectAt(scene.contact(), Set.of());
		PhantomM1WorldContinuationChecks.prewarmContact(context, selected, _human, contactRoute(scene), _sceneDeadline);
		final PhantomTopologyPoint withdrawal = withdrawal(point(_human), PhantomSystem.operatorM1TargetSnapshot(selected).orElseThrow().observedPosition());
		PhantomAssertions.assertTrue(withdrawal != null, "INVALID W06: no original loaded dry native withdrawal route beyond prewarm; no teleport fallback.");
		final var returned = point(_human);
		PhantomM1WorldContinuationChecks.softReturnBackgroundReentry(context, selected, _human, List.of(midpoint(returned, withdrawal), withdrawal), List.of(midpoint(withdrawal, returned), returned), _loadObserver, _sceneDeadline);
		recordCensus(context, "W06.after"); assertPopulation(context, "W06.after");
	}

	private void geographyWorld(PhantomTestContext context) throws Exception
	{
		final Scene scene = prepareScene(context);
		PhantomM1WorldContinuationChecks.move(context, "W07.contact", _human, contactRoute(scene), _sceneDeadline);
		final var histogram = PhantomSystem.operatorStatus().levelHistogram(); context.record("w.W07.actualLevelHistogram", histogram);
		final Set<String> actualBuckets = _fixture.readyIdentities().stream().map(identity -> identity.level() <= 19 ? "01-19" : identity.level() <= 39 ? "20-39" : identity.level() <= 60 ? "40-60" : identity.level() <= 75 ? "61-75" : "76-85").collect(java.util.stream.Collectors.toSet());
		PhantomAssertions.assertEquals(Set.of("01-19"), actualBuckets, "W07 source population changed from factual levels3..12; new populated ranges require actual natural scenes.");
		context.record("w.W07.sourcePopulatedBuckets", actualBuckets);
		PhantomM1WorldContinuationChecks.geography(context, _human, _cohort, _stock, _sceneDeadline);
		recordCensus(context, "W07.after"); assertPopulation(context, "W07.after");
	}

	private void restartWorld(PhantomTestContext context) throws Exception
	{
		final Scene scene = prepareScene(context);
		PhantomM1WorldContinuationChecks.move(context, "W08.contact", _human, contactRoute(scene), _sceneDeadline);
		await(60_000, () -> _cohort.stream().allMatch(id -> PhantomSystem.operatorM1TargetSnapshot(id).map(PhantomSystem.OperatorM1TargetSnapshot::worldPresent).orElse(false)), "W08 frozen natural cohort did not materialize; missing participants retained.");
		final long observedProfile = _cohort.getFirst();
		final Player observedPlayer = World.getInstance().getPlayer(PhantomSystem.operatorM1TargetSnapshot(observedProfile).orElseThrow().objectId());
		PhantomAssertions.assertTrue(observedPlayer != null && observedPlayer.getNativeWorkOwner() != null && observedPlayer.getNativeWorkOwner().evidence() != null, "W native-load contract control lacks an actual owner/evidence.");
		_loadObserver.assertContractControls(context, observedProfile, observedPlayer, observedPlayer.getNativeWorkOwner().evidence().snapshot());
		PhantomM1WorldContinuationChecks.realAttackerDiagnostic(context, select(Set.of()), _human, _sceneDeadline);
		PhantomM1WorldContinuationChecks.restartAndSoak(context, _settings, _human, _cohort, _loadObserver, _sceneDeadline);
		_materialization = PhantomSystem.configuredMaterializationService();
		recordCensus(context, "W08.after"); assertPopulation(context, "W08.after");
	}

	private static List<PhantomTopologyPoint> contactRoute(Scene scene) { return List.of(midpoint(scene.source(), scene.contact()), scene.contact()); }
	private static PhantomTopologyPoint midpoint(PhantomTopologyPoint from, PhantomTopologyPoint to) { final int x = from.x() + (to.x() - from.x()) / 2; final int y = from.y() + (to.y() - from.y()) / 2; return new PhantomTopologyPoint(x, y, GeoEngine.getInstance().getHeight(x, y, from.z()), from.instanceId()); }
	private static PhantomTopologyPoint withdrawal(PhantomTopologyPoint human, PhantomTopologyPoint actor)
	{
		for (int distance = 3 << World.SHIFT_BY; distance <= 4 << World.SHIFT_BY; distance += 1 << World.SHIFT_BY)
		{
			for (int[] direction : List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}))
			{
				final int x = human.x() + direction[0] * distance; final int y = human.y() + direction[1] * distance;
				final var candidate = new PhantomTopologyPoint(x, y, GeoEngine.getInstance().getHeight(x, y, human.z()), human.instanceId());
				if (!PhantomNativeLocalityEnvelope.prewarm(candidate, actor) && lawfulHumanSegment(human, candidate)) { return candidate; }
			}
		}
		return null;
	}

	private Scene chooseScene(PhantomTestContext context) throws Exception
	{
		final long deadline = Math.min(_sceneDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(60));
		final long[] visits = new long[9];
		while (System.nanoTime() < deadline)
		{
			if (!PhantomSystem.operatorStatus().ecology().inventoryReady()) { visits[0]++; Thread.sleep(500L); continue; }
			for (var identity : _fixture.readyIdentities())
			{
				visits[1]++;
				final var admission = PhantomSystem.operatorAdmissionProfile(identity.profileId()).orElse(null);
				if (admission == null || !admission.admission().calendarOnline() || admission.admission().nextBoundary().isBefore(java.time.Instant.now().plusMillis(SCENE_MILLIS + CLEANUP_MILLIS))) { continue; }
				visits[2]++;
				final var target = PhantomSystem.operatorLocalityTarget(identity.profileId()).orElse(null);
				if (target == null || target.committedPosition() == null) { continue; }
				visits[3]++;
				if (_mode == Mode.GEOGRAPHY && World.getInstance().getVisibleObjects().stream().filter(org.l2jmobius.gameserver.model.actor.instance.Monster.class::isInstance).map(org.l2jmobius.gameserver.model.actor.instance.Monster.class::cast).noneMatch(npc -> npc.getId() == 18342 && npc.getInstanceId() == target.committedPosition().instanceId() && Math.abs((long) npc.getZ() - target.committedPosition().z()) <= 300 && new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()).distanceSquared2D(target.committedPosition()) <= 16_000_000L)) { continue; }
				final var cohort = PhantomSystem.operatorM1NaturalCohortProfileIds(target.committedPosition());
				final java.time.Instant stableUntil = java.time.Instant.now().plusMillis(SCENE_MILLIS + CLEANUP_MILLIS);
				if (cohort.size() < 4 || PhantomSystem.operatorNaturalCohortSize(target.committedPosition(), stableUntil) < 4) { continue; }
				visits[4]++;
				boolean stable = true;
				for (long id : cohort) { final var participant = PhantomSystem.operatorAdmissionProfile(id).orElse(null); if (participant == null || !participant.admission().calendarOnline() || !participant.admission().nextBoundary().isAfter(stableUntil)) { stable = false; break; } }
				if (!stable) { continue; }
				visits[5]++;
				final Set<Boolean> types = new HashSet<>();
				for (var candidate : _fixture.readyIdentities()) { if (cohort.contains(candidate.profileId())) { types.add(PlayerClass.getPlayerClass(candidate.classId()).isMage()); } }
				if (types.size() != 2) { continue; }
				visits[6]++;
				final PhantomTopologyPoint source = prewarmSource(target.committedPosition());
				if (source != null) { visits[7]++; context.record("w.scene.filterVisits", java.util.Arrays.toString(visits)); return new Scene(target.committedPosition(), source, List.copyOf(cohort)); }
				visits[8]++;
			}
			Thread.sleep(500L);
		}
		context.record("w.scene.filterVisits", "cumulative,inventoryNotReady/identities/calendarStable/localityPosition/cohort4/stableMembers/bothTypes/sourceAccepted/sourceAbsent=" + java.util.Arrays.toString(visits));
		context.record("w.scene.finalEcology", PhantomSystem.operatorStatus().ecology());
		throw new AssertionError("W_INITIAL_NO_NATURAL_COHORT_WITH_NATIVE_SOURCE: no>=4 stable calendar participants/mage+melee/lawful human approach; no profile/config override attempted.");
	}

	private long select(Set<Long> excluded)
	{
		return selectAt(point(_human), excluded);
	}

	private long selectAt(PhantomTopologyPoint source, Set<Long> excluded)
	{
		final var candidates = PhantomSystem.operatorM1CandidateSnapshots(source, 3_240_000L, excluded);
		PhantomAssertions.assertTrue(!candidates.isEmpty(), "Existing W server selector returned no READY candidate.");
		final long selected = candidates.getFirst().profileId();
		PhantomAssertions.assertTrue(_cohort.contains(selected), "Server-selected W actor is outside the frozen natural cohort.");
		return selected;
	}

	private static PhantomTopologyPoint prewarmSource(PhantomTopologyPoint target)
	{
		final int distance = 2 << World.SHIFT_BY;
		for (int[] direction : List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}, new int[] {1, 1}, new int[] {-1, -1}, new int[] {1, -1}, new int[] {-1, 1}))
		{
			final int x = target.x() + direction[0] * distance; final int y = target.y() + direction[1] * distance;
			final var preliminary = new PhantomTopologyPoint(x, y, target.z(), target.instanceId());
			if (!PhantomNativeLocalityEnvelope.prewarm(preliminary, target) || PhantomNativeLocalityEnvelope.couldKnow(preliminary, target) || !GeoEngine.getInstance().hasGeo(x, y)) { continue; }
			final var source = new PhantomTopologyPoint(x, y, GeoEngine.getInstance().getHeight(x, y, target.z()), target.instanceId());
			if (PhantomNativeLocalityEnvelope.prewarm(source, target) && !PhantomNativeLocalityEnvelope.couldKnow(source, target) && lawfulHumanSegment(source, target)) { return source; }
		}
		return null;
	}

	private static boolean lawfulHumanSegment(PhantomTopologyPoint from, PhantomTopologyPoint to)
	{
		final var geo = GeoEngine.getInstance();
		if (!geo.canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId())) { return false; }
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		int z = from.z();
		while (cells.next()) { if (!geo.hasGeoPos(cells.x(), cells.y())) { return false; } final int x = GeoEngine.getWorldX(cells.x()); final int y = GeoEngine.getWorldY(cells.y()); z = geo.getHeight(x, y, z); if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { return false; } }
		return true;
	}

	private void recordCensus(PhantomTestContext context, String phase)
	{
		long cursor = 0;
		for (int page = 0; page < 8; page++)
		{
			final var fields = PhantomSystem.operatorVisibleLifeCensus(_human, cursor);
			context.record("w.census." + phase + "." + page, fields);
			PhantomAssertions.assertFalse(Boolean.parseBoolean(fields.getOrDefault("cleanupEvidenceIncomplete", "false")), "W native cleanup evidence is incomplete.");
			PhantomAssertions.assertTrue(Long.parseLong(fields.getOrDefault("cleanupIncidentEvictions", "0")) == 0, "W native cleanup incident records were evicted.");
			final long next = Long.parseLong(fields.getOrDefault("censusNextProfileId", "0"));
			if (next == 0) { return; }
			PhantomAssertions.assertTrue(next > cursor, "W native census cursor did not advance."); cursor = next;
		}
		throw new AssertionError("W_CENSUS_PAGE_BOUND_EXCEEDED");
	}

	private static Sample sample(PhantomSystem.OperatorM1TargetSnapshot target)
	{
		final var evidence = target.nativeEvidence();
		PhantomAssertions.assertTrue("1".equals(evidence.get("nativeEvidenceVersion")) && "PHANTOM".equals(evidence.get("nativeEvidenceOwner")) && !Boolean.parseBoolean(evidence.getOrDefault("nativeEvidenceOverflow", "true")), "W native sensor is missing or overflowed.");
		PhantomAssertions.assertTrue(number(evidence, "nativeEvidenceObjectId") == target.objectId() && number(evidence, "nativeEvidenceEpoch") == target.materializedAtNanos(), "W native sensor identity is stale.");
		final long sampled = number(evidence, "nativeEvidenceSampleNanos");
		PhantomAssertions.assertTrue(sampled >= target.materializedAtNanos() && sampled >= target.sampledAtNanos() && sampled <= System.nanoTime(), "W native evidence sample time is outside the exact owner epoch/operator read interval.");
		final String phase = evidence.get("nativePhase");
		PhantomAssertions.assertTrue(phase != null, "W native phase scalar missing.");
		if (!"NONE".equals(phase))
		{
			final long since = number(evidence, "nativePhaseSinceNanos"); final long deadline = number(evidence, "nativePhaseDeadlineNanos");
			PhantomAssertions.assertTrue(Set.of("ROUTE", "REGEN", "DEATH_RECOVERY").contains(phase) && since >= target.materializedAtNanos() && since <= sampled && deadline > sampled && deadline - since <= TimeUnit.SECONDS.toNanos(120), "W native reasoned phase is stale, expired or unbounded: " + target.profileId());
		}
		return new Sample(target.objectId(), target.materializedAtNanos(), number(evidence, "nativeEvidenceSequence"), number(evidence, "nativeDamageSequence"), number(evidence, "nativeKillSequence"), number(evidence, "nativeRewardSequence"), number(evidence, "nativeFarmCycleSequence"), number(evidence, "nativeExpGained"), number(evidence, "nativeSpGained"), number(evidence, "nativeEvidenceSampleNanos"));
	}

	private static boolean twoCycles(Sample before, Sample after)
	{
		return before != null && after != null && before.objectId() == after.objectId() && before.epoch() == after.epoch() && after.cycles() - before.cycles() >= 2 && after.damage() - before.damage() >= 2 && after.kills() - before.kills() >= 2 && after.reward() - before.reward() >= 2 && after.exp() > before.exp() && after.sp() > before.sp();
	}

	private void assertSettings()
	{
		PhantomAssertions.assertTrue(_settings.enabled() && _settings.ecologyEnabled(), "Approved native source settings are disabled.");
		PhantomAssertions.assertEquals(1280, _settings.populationTarget(), "W population target changed.");
		PhantomAssertions.assertEquals(64, _settings.populationActiveTarget(), "W ACTIVE target changed.");
		PhantomAssertions.assertEquals(128, _settings.maxMaterializedPhantoms(), "W materialized cap changed.");
		PhantomAssertions.assertEquals(10000, _settings.maxScheduledPhantomProfiles(), "W MaxScheduled changed.");
		PhantomAssertions.assertEquals(100, _settings.schedulerPulseMillis(), "W scheduler pulse changed.");
		PhantomAssertions.assertEquals(256, _settings.schedulerProfilesPerPulse(), "W scheduler batch changed.");
		PhantomAssertions.assertEquals("UTC", _settings.populationTimeZone().getId(), "W calendar timezone changed.");
		PhantomAssertions.assertEquals("LIVING", _settings.ecologyPreset(), "W native preset changed.");
		PhantomAssertions.assertEquals(-1, _settings.ecologyWorldAgeDays(), "W native historical age changed.");
	}

	private static void assertPopulation(PhantomTestContext context, String phase)
	{
		final var repository = PhantomProfileRepository.open(); final var codec = new PhantomPopulationStateCodec(); long cursor = 0; int total = 0; int ready = 0; int retired = 0;
		while (true) { final var rows = repository.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, cursor, 256); if (rows.isEmpty()) { break; } for (var row : rows) { final var state = codec.decode(row.component().payload()); total++; if (state.state() == PhantomPopulationState.State.READY) { ready++; } else if (state.state() == PhantomPopulationState.State.RETIRED) { retired++; } cursor = row.profile().profileId(); } PhantomAssertions.assertTrue(total <= 10000, "W population expanded beyond the imported baseline."); }
		context.record("w.population." + phase, "total=" + total + ",READY=" + ready + ",RETIRED=" + retired);
		PhantomAssertions.assertTrue(total == 10000 && ready == 1280 && retired == 8720, "W production changed1280READY/8720RETIRED.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (isHistoricalNativeMode() || _mode == Mode.HISTORICAL_CONTEXT)
		{
			PhantomAssertions.assertFalse(_historicalRetained, "Historical native owner retained/unknown; original durable population journal kept and restore refused.");
			if (_mode == Mode.HISTORICAL_CONTEXT) { stopHistoricalContextRuntime(); }
			for (var runtime : _historicalRuntimes) { stopHistoricalRuntime(runtime); }
			if (_fixture != null && _environmentAttempted)
			{
				for (int id : _fixture.characterObjectIds()) { PhantomAssertions.assertEquals(null, World.getInstance().findObject(id), "Historical focus retained an imported World object; restore forbidden."); PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(id), "Historical focus retained an imported native Player; restore forbidden."); PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(id), "Historical focus retained an imported identity lease; restore forbidden."); }
			}
			if (_historicalKnowledge != null) { _historicalKnowledge.beginStop(); PhantomAssertions.assertTrue(_historicalKnowledge.finishStop(), "Historical production Knowledge service did not stop."); _historicalKnowledge = null; }
			if (_environmentAttempted) { _environment.shutdown(); _environmentAttempted = false; }
			PhantomAssertions.assertFalse(DatabaseFactory.isInitialized() || PhantomSystem.hasConfiguredInstance(), "Historical native infrastructure still running; full original population restore forbidden.");
			if (_fixture != null) { _fixture.close(); _fixture = null; }
			PhantomAssertions.assertEquals(context.measurements().get("w.fixture.beforeHash"), context.measurements().get("w.fixture.restoredHash"), "Historical focus full pre-import TEST image was not restored by original CAS.");
			for (var runtime : _historicalRuntimes) { runtime.probe().assertRestored(); }
			if (_mode == Mode.HISTORICAL_CONTEXT && _historicalContextProbe != null) { _historicalContextProbe.assertRestored(); }
			context.record("historicalNative.cleanup", "all original native owners/infrastructure stopped; original full pre-import TEST CAS restored");
			return;
		}
		try
		{
			final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLEANUP_MILLIS);
			final var currentService = PhantomSystem.configuredMaterializationService();
			final var stoppingService = currentService == null ? _materialization : currentService;
			if (PhantomSystem.hasConfiguredInstance()) { PhantomSystem.shutdownIfStarted(); }
			while (PhantomSystem.hasConfiguredInstance() && System.nanoTime() < deadline) { PhantomSystem.operatorDrain(); if (PhantomSystem.hasConfiguredInstance()) { Thread.sleep(50L); } }
			PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "W drain retained native runtime; journal retained, restore refused.");
			if (_fixture != null && _environmentAttempted)
			{
				for (int id : _fixture.characterObjectIds()) { PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(id), "W native World retained an imported identity after drain."); PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(id), "W native lease retained an imported identity after drain."); }
				if (_loadObserver != null && stoppingService != null) { _loadObserver.clearAfterStopped(stoppingService); }
				_environment.cleanupLoadedPlayer(_human); _human = null;
				if (_stock != null) { _stock.close(); _stock = null; }
				_environment.shutdown(); _environmentAttempted = false;
			}
			if (_fixture != null) { _fixture.close(); _fixture = null; }
			context.record("w.cleanup.nativeStopped", !DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance());
		}
		finally
		{
			if (_sqlDiagnostic != null) { _sqlDiagnostic.record(context); _sqlDiagnostic.restore(); _sqlDiagnostic = null; }
		}
	}

	/** Observes stock SQL failures without changing the original pool, parameters or transaction. */
	private static final class NativeSqlDiagnostic
	{
		private static final int MAX_TRACES = 8;
		private static final int MAX_FRAMES = 32;
		private final java.lang.reflect.Field _field;
		private final com.zaxxer.hikari.HikariDataSource _original;
		private final com.zaxxer.hikari.HikariDataSource _forwarding;
		private final java.util.concurrent.atomic.AtomicReference<SqlFailure> _first = new java.util.concurrent.atomic.AtomicReference<>();
		private final java.util.concurrent.atomic.AtomicReference<String> _firstPoolTimeout = new java.util.concurrent.atomic.AtomicReference<>();
		private final java.util.concurrent.atomic.AtomicLong _acquisitionSequence = new java.util.concurrent.atomic.AtomicLong();
		private final Object _acquisitionMonitor = new Object();
		private final Map<Long, Acquisition> _holders = new java.util.LinkedHashMap<>();
		private final Map<Long, Acquisition> _waiting = new java.util.LinkedHashMap<>();

		NativeSqlDiagnostic() throws Exception
		{
			_field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL"); _field.setAccessible(true);
			_original = (com.zaxxer.hikari.HikariDataSource) _field.get(null);
			PhantomAssertions.assertTrue(_original != null && !_original.isClosed(), "W SQL diagnostic requires the original initialized native pool.");
			_forwarding = new com.zaxxer.hikari.HikariDataSource()
			{
				@Override public java.sql.Connection getConnection() throws java.sql.SQLException
				{
					final var acquisition = new Acquisition(_acquisitionSequence.incrementAndGet(), Thread.currentThread().threadId(), System.nanoTime(), frames(Thread.currentThread().getStackTrace()));
					synchronized (_acquisitionMonitor) { if (_waiting.size() < MAX_TRACES) { _waiting.put(acquisition.sequence(), acquisition); } }
					try
					{
						final java.sql.Connection connection = _original.getConnection();
						synchronized (_acquisitionMonitor)
						{
							_waiting.remove(acquisition.sequence());
							if (_holders.size() < MAX_TRACES) { _holders.put(acquisition.sequence(), new Acquisition(acquisition.sequence(), acquisition.threadId(), System.nanoTime(), acquisition.frames())); }
						}
						return proxy(java.sql.Connection.class, connection, "GET_CONNECTION", acquisition);
					}
					catch (java.sql.SQLException failure) { capturePoolTimeout(failure, acquisition); capture(failure, "GET_CONNECTION"); throw failure; }
					finally { synchronized (_acquisitionMonitor) { _waiting.remove(acquisition.sequence()); } }
				}
				@Override public boolean isClosed() { return _original.isClosed(); }
				@Override public void close() { _original.close(); }
			};
			_field.set(null, _forwarding);
		}

		int maximumPoolSize() { return _original.getMaximumPoolSize(); }

		private <T> T proxy(Class<T> type, T delegate, String sql)
		{
			return proxy(type, delegate, sql, null);
		}

		@SuppressWarnings("unchecked")
		private <T> T proxy(Class<T> type, T delegate, String sql, Acquisition acquisition)
		{
			return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (_, method, args) ->
			{
				final String shape = method.getName().equals("prepareStatement") && args != null && args.length > 0 && args[0] instanceof String text ? sqlShape(text) : sql;
				try
				{
					final Object value = method.invoke(delegate, args);
					// Observe only an originally requested, successfully forwarded Connection.close.
					if ((acquisition != null) && method.getName().equals("close")) { synchronized (_acquisitionMonitor) { _holders.remove(acquisition.sequence()); } }
					if (value instanceof java.sql.PreparedStatement statement) { return proxy(java.sql.PreparedStatement.class, statement, shape); }
					if (value instanceof java.sql.ResultSet rows) { return proxy(java.sql.ResultSet.class, rows, shape); }
					return value;
				}
				catch (java.lang.reflect.InvocationTargetException wrapped)
				{
					final Throwable failure = wrapped.getCause();
					if (failure instanceof java.sql.SQLException nativeFailure) { capture(nativeFailure, shape + " operation=" + method.getName()); }
					throw failure;
				}
			});
		}

		private void capturePoolTimeout(java.sql.SQLException failure, Acquisition timedOut)
		{
			if (!String.valueOf(failure.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("connection is not available") || !_firstPoolTimeout.compareAndSet(null, "CAPTURE_IN_PROGRESS")) { return; }
			try
			{
				final List<Acquisition> holders;
				final List<Acquisition> waiting;
				synchronized (_acquisitionMonitor) { holders = List.copyOf(_holders.values()); waiting = List.copyOf(_waiting.values()); }
				final var pool = _original.getHikariPoolMXBean();
				final var trace = new StringBuilder("FIRST_ORIGINAL_POOL_TIMEOUT; boundedTraceLimit=" + MAX_TRACES + "; snapshotNotAtomic=true");
				trace.append("; pool=").append(pool == null ? "UNAVAILABLE" : "total=" + pool.getTotalConnections() + ",active=" + pool.getActiveConnections() + ",idle=" + pool.getIdleConnections() + ",waiting=" + pool.getThreadsAwaitingConnection());
				appendAcquisition(trace, "TIMED_OUT", timedOut);
				for (var holder : holders) { appendAcquisition(trace, "HOLDER", holder); }
				for (var waiter : waiting) { if (waiter.sequence() != timedOut.sequence()) { appendAcquisition(trace, "WAITING", waiter); } }
				_firstPoolTimeout.set(trace.toString());
				System.err.println("TEST W original pool acquisition trace " + trace);
			}
			catch (RuntimeException | Error diagnosticFailure)
			{
				// A diagnostic must not replace the original native SQLException.
				_firstPoolTimeout.set("DIAGNOSTIC_CAPTURE_FAILED; class=" + diagnosticFailure.getClass().getName());
			}
		}

		private static void appendAcquisition(StringBuilder trace, String role, Acquisition acquisition)
		{
			trace.append("\n").append(role).append(" sequence=").append(acquisition.sequence()).append(" threadId=").append(acquisition.threadId()).append(" elapsedMillis=").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - acquisition.atNanos())).append(" acquireFrames=").append(acquisition.frames());
			final var info = java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(acquisition.threadId(), MAX_FRAMES);
			if (info == null) { trace.append(" currentThread=ENDED"); return; }
			trace.append(" currentState=").append(info.getThreadState()).append(" lockOwnerId=").append(info.getLockOwnerId()).append(" currentFrames=").append(frames(info.getStackTrace()));
		}

		private static String frames(StackTraceElement[] frames)
		{
			final var safe = new StringBuilder();
			for (int i = 0; i < Math.min(MAX_FRAMES, frames.length); i++)
			{
				if (i != 0) { safe.append(" > "); }
				final var frame = frames[i];
				safe.append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
			}
			return safe.toString();
		}

		private void capture(java.sql.SQLException failure, String sql)
		{
			final String message = String.valueOf(failure.getMessage()).toLowerCase(java.util.Locale.ROOT);
			final String redacted = message.contains("data too long") ? "Data too long; names and values omitted" : message.contains("deadlock") ? "Deadlock detected; details omitted" : message.contains("lock wait timeout") ? "Lock wait timeout; details omitted" : message.contains("unknown column") ? "Unknown column; names and values omitted" : message.contains("duplicate entry") ? "Duplicate entry; values omitted" : message.contains("foreign key") ? "Foreign key constraint failure; values omitted" : message.contains("connection is not available") ? "Connection acquisition timed out; details omitted" : "Unclassified native SQL message redacted";
			final var observed = new SqlFailure(failure.getClass().getName(), String.valueOf(failure.getSQLState()), failure.getErrorCode(), redacted, sql);
			if (_first.compareAndSet(null, observed)) { System.err.println("TEST W first native SQL failure " + observed); }
		}

		private static String sqlShape(String sql)
		{
			final String shape = sql.replaceAll("(?s)'(?:''|[^'])*'", "?").replaceAll("(?s)\"(?:\"\"|[^\"])*\"", "?").replaceAll("\\b[0-9]+\\b", "?").replaceAll("\\s+", " ").trim();
			return shape.substring(0, Math.min(shape.length(), 512));
		}

		void record(PhantomTestContext context)
		{
			context.record("w.nativeSql.firstFailure", _first.get() == null ? "NONE_OBSERVED; model/capture failures are not SQL failures" : _first.get());
			context.record("w.nativeSql.firstPoolTimeout", _firstPoolTimeout.get() == null ? "NONE_OBSERVED; no pool timeout inferred" : _firstPoolTimeout.get());
		}
		void restore() throws Exception { if (_field.get(null) == _forwarding) { _field.set(null, _original); } }
		private record Acquisition(long sequence, long threadId, long atNanos, String frames) { }
		private record SqlFailure(String exceptionClass, String sqlState, int errorCode, String redactedMessage, String sqlShape) { }
	}

	private void await(long timeoutMillis, java.util.function.BooleanSupplier condition, String failure) throws Exception { final long deadline = Math.min(_sceneDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)); while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(100L); } PhantomAssertions.assertTrue(condition.getAsBoolean(), failure); }
	private static PhantomTopologyPoint point(Player player) { return new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()); }
	private static long number(Map<String, String> fields, String name) { final String value = fields.get(name); PhantomAssertions.assertTrue(value != null, "W native scalar is missing: " + name); return Long.parseLong(value); }
	private record Scene(PhantomTopologyPoint contact, PhantomTopologyPoint source, List<Long> cohort) { }
	private record Sample(int objectId, long epoch, long sequence, long damage, long kills, long reward, long cycles, long exp, long sp, long sampledAtNanos) { }
}
