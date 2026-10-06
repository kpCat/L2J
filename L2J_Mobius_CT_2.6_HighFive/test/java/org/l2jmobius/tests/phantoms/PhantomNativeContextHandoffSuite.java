/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;

/** Real ecology/readiness -> adapter -> composed historical/background lifecycle. */
public final class PhantomNativeContextHandoffSuite implements PhantomTestSuite
{

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomProfileRepository _profiles;
	private PhantomPopulationCatalog _population;
	private PhantomPopulationEcologyCatalog _catalog;
	private byte[] _configBefore;
	private final Path _config = Path.of(PhantomPlayersConfig.PHANTOM_PLAYERS_CONFIG_FILE);

	public static void main(String[] args)
	{
		final var context = new PhantomTestContext(18001801, Path.of(args[0]), Path.of(args[1]));
		System.exit(PhantomTestLauncher.runSuite("native-context-handoff", new PhantomNativeContextHandoffSuite(), context));
	}

	@Override
	public String id() { return "native-context-handoff"; }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
		_population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final var social = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		_catalog = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), _population, social);
		_configBefore = Files.readAllBytes(_config);
		Files.writeString(_config, new String(_configBefore, java.nio.charset.StandardCharsets.UTF_8).replace("EnablePhantomSystem = False", "EnablePhantomSystem = True").replace("EnablePhantomDiagnostics = False", "EnablePhantomDiagnostics = True"));
		PhantomPlayersConfig.load();
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try { if (_production != null) { _production.close(); } }
		finally
		{
			try { if (_configBefore != null) { Files.write(_config, _configBefore); PhantomPlayersConfig.load(); } }
			finally { _environment.shutdown(); }
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("H01-normal-incomplete-remains-fenced", _ ->
		{
			try (var f = new Fixture())
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CATCHUP_FENCED, f.materialization.materialize(f.id).status(), "NORMAL fence.");
				PhantomAssertions.assertEquals("catchup.normal_fenced", f.rejection.get(), "Exact NORMAL subreason.");
				PhantomAssertions.assertEquals(0L, f.loadedEpoch, "NORMAL fence must precede native load.");
			}
		});
		registry.add("H02-ordinary-complete-due-is-normal-empty", _ ->
		{
			try (var f = new Fixture())
			{
				f.removeCatchup();
				final var due = ecology(f, true, "ordinary").requestMaterializationDue(f.id);
				PhantomAssertions.assertTrue(due.complete(), "Ordinary cursor must be current.");
				PhantomAssertions.assertEquals(MaterializationRequest.normal(), due.request(), "Ordinary due must carry NORMAL with empty claim.");
			}
		});
		registry.add("H03-integrated-task011-exact-native-handoff", this::integrated);
		registry.add("H04-no-permit-outside-exact-native-branch", _ ->
		{
			try (var f = new Fixture())
			{
				for (String condition : List.of("no-demand", "request", "window", "from", "complete-history", "terminal", "not-ready", "plan-held"))
				{
					final var due = ecology(f, true, condition).requestMaterializationDue(f.id);
					PhantomAssertions.assertEquals(MaterializationRequest.normal(), due.request(), "No permit for " + condition);
				}
			}
		});
		registry.add("H05-blank-wrong-claim-before-load", _ ->
		{
			try (var f = new Fixture())
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, " "), "Blank service claim.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> MaterializationRequest.nativeContextHandoff(""), "Blank typed claim.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new MaterializationRequest(MaterializationRequest.Kind.NORMAL, f.claim), "NORMAL cannot own history.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CATCHUP_FENCED, f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, "d".repeat(64)).status(), "Wrong claim must be fenced.");
				PhantomAssertions.assertEquals(0L, f.loadedEpoch, "Invalid claim must not load.");
				PhantomAssertions.assertThrows(PhantomMaterializationLifecyclePort.AdmissionRejectedException.class, () -> f.background.beforeMaterialize(f.id, f.objectId, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, "d".repeat(64)), "Background must validate independently.");
				final var component = _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
				final var complete = f.catchups.load(f.id).orElseThrow().state().running().advanceTo(f.target);
				_profiles.updateComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE, component.rowVersion(), component.componentSchemaVersion(), new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().encode(complete));
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CATCHUP_FENCED, f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim).status(), "COMPLETE is not a handoff claim.");
				PhantomAssertions.assertEquals(0L, f.loadedEpoch, "COMPLETE handoff must not load.");
			}
		});
		registry.add("H06-exact-claim-both-lifecycle-admissions", _ ->
		{
			try (var f = new Fixture())
			{
				f.historical.beforeMaterialize(f.id, f.objectId, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim);
				f.background.beforeMaterialize(f.id, f.objectId, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim);
				PhantomAssertions.assertEquals(1, f.background.snapshot().currentTransitionClaims(), "Real Background admission retained.");
				f.background.materializeAborted(f.id, f.objectId); f.historical.materializeAborted(f.id, f.objectId);
			}
		});
		registry.add("H07-catchup-row-and-payload-races-fail-closed", _ -> race(false));
		registry.add("H08-goal-race-after-historical-load-fails-closed", _ -> race(true));
		registry.add("H09-current-native-owner-epoch-attests-no-reward", _ ->
		{
			try (var f = new Fixture())
			{
				PhantomAssertions.assertFalse(f.transactions.nativeContext(f.id, f.objectId).simulationEligible(), "Fixture must need native attestation.");
				f.handoff();
				final var entry = f.materialization.find(f.id).orElseThrow();
				PhantomAssertions.assertEquals(f.loadedEpoch, entry.materializedAtNanos(), "Native owner epoch must remain current.");
				PhantomAssertions.assertTrue(f.transactions.nativeContext(f.id, f.objectId).simulationEligible() == false, "MATERIALIZED remains ineligible for background simulation.");
				final var proof = f.transactions.nativeContext(f.id, f.objectId);
				PhantomAssertions.assertTrue(proof.context().simulationEligible() && proof.context().phase() == PhantomNativeContext.Phase.COMPLETED, "Existing native-context attestation did not complete.");
				final var after = f.transactions.load(f.id).state();
				PhantomAssertions.assertEquals(f.baseline.progress(), after.progress(), "Handoff fabricated EXP/SP.");
				PhantomAssertions.assertEquals(f.baseline.position(), after.position(), "Handoff moved historical position.");
				PhantomAssertions.assertEquals(f.baseline.clock(), after.clock(), "Handoff moved background clock.");
				PhantomAssertions.assertEquals(f.baseline.inventory().objects(), after.inventory().objects(), "Handoff fabricated inventory.");
				PhantomAssertions.assertEquals(f.baseline.autoGetSkills(), after.autoGetSkills(), "Handoff fabricated skills.");
			}
		});
		registry.add("H10-abort-releases-both-admissions", _ ->
		{
			try (var f = new Fixture())
			{
				f.beforeLoad = () -> { throw new IllegalStateException("focused native load abort"); };
				PhantomAssertions.assertFalse(f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Injected abort must fail.");
				PhantomAssertions.assertEquals(0, f.background.snapshot().currentTransitionClaims(), "Abort leaked Background admission.");
				f.historical.beforeMaterialize(f.id, f.objectId, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim);
				f.historical.materializeAborted(f.id, f.objectId);
			}
		});
		registry.add("H11-success-preserves-exact-history-and-ecology", _ ->
		{
			try (var f = new Fixture())
			{
				final var before = _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
				final var ecology = ecology(f, true);
				final var due = ecology.requestMaterializationDue(f.id);
				f.handoff();
				PhantomAssertions.assertEquals(before, _profiles.findComponent(f.id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow(), "Success changed exact history row/version/payload.");
				PhantomAssertions.assertEquals(due, ecology.requestMaterializationDue(f.id), "Handoff advanced ecology cursor/request.");
			}
		});
		registry.add("H12-normal-without-catchup-unchanged", _ ->
		{
			try (var f = new Fixture()) { f.removeCatchup(); PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Ordinary native materialization."); }
		});
		registry.add("H13-historical-baseline-pending-unchanged", _ ->
		{
			try (var f = new Fixture())
			{
				final var before = f.catchups.load(f.id).orElseThrow();
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.HISTORICAL_BASELINE, f.claim).status(), "Existing baseline admission.");
				PhantomAssertions.assertEquals(before, f.catchups.load(f.id).orElseThrow(), "Baseline changed catchup.");
			}
		});
		registry.add("H14-recorder-bounded-real-admission-subreason", _ ->
		{
			try (var f = new Fixture())
			{
				final var recorder = PhantomRuntimeFlightRecorder.getInstance();
				PhantomAssertions.assertTrue(recorder.begin("contract-h14"), "Recorder enable.");
				try
				{
					recorder.watch(f.id);
					new PhantomMaterializationServiceActivityPort(f.materialization).materialize(f.id);
					final var events = recorder.snapshot("contract-h14").events();
					PhantomAssertions.assertTrue(events.stream().anyMatch(event -> event.event().equals("MATERIALIZE_ADMISSION_REJECT") && event.reason().equals("catchup.normal_fenced") && event.reason().length() <= 192), "Real lifecycle admission reason must be recorded.");
				}
				finally { recorder.end("contract-h14"); }
			}
		});
	}

	private void race(boolean goal) throws Exception
	{
		for (boolean payload : goal ? List.of(false) : List.of(false, true))
		{
			try (var f = new Fixture())
			{
				final Runnable mutation = () ->
				{
					final String type = goal ? PhantomGoalStateStore.COMPONENT_TYPE : PhantomBackgroundCatchupState.COMPONENT_TYPE;
					final var component = _profiles.findComponent(f.id, type).orElseThrow();
					final byte[] replacement = payload ? new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().encode(f.catchups.load(f.id).orElseThrow().state().running()) : component.payload();
					_profiles.updateComponent(f.id, type, component.rowVersion(), component.componentSchemaVersion(), replacement);
				};
				if (goal) { f.afterHistoricalLoad = mutation; } else { f.beforeLoad = mutation; }
				final var result = f.materialization.materialize(f.id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.claim);
				PhantomAssertions.assertFalse(result.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Component race must fail closed.");
				PhantomAssertions.assertTrue(World.getInstance().getPlayer(f.objectId) == null, "Component race must prevent World spawn.");
				PhantomAssertions.assertEquals(0, f.background.snapshot().currentTransitionClaims(), "Race leaked Background transition.");
			}
		}
	}

	private void integrated(PhantomTestContext context) throws Exception
	{
		try (var f = new Fixture())
		{
			final var ecology = ecology(f, true);
			final var permission = ecology.requestMaterializationDue(f.id);
			PhantomAssertions.assertTrue(permission.complete(), "Task011 must grant exact native-required foreground readiness.");
			PhantomAssertions.assertEquals("ecology.native_materialization_required", permission.reason(), "Wrong task011 branch.");
			PhantomAssertions.assertEquals(MaterializationRequest.nativeContextHandoff(f.claim), permission.request(), "Permit must carry the original exact claim typed.");
			final var backend = new PhantomTopologyCoreSuite.TestBackend();
			final var topology = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signals());
			PhantomAssertions.assertTrue(topology.start(), "Topology start.");
			try
			{
				topology.registerProfile(f.id);
				topology.updateProfile(f.id, PhantomTopologyCoreSuite.LEFT_POINT, 1);
				final var presence = new PhantomPresenceRegistry(1);
				presence.schedule(f.id, PhantomActivityState.BACKGROUND);
				final var locality = new PhantomHumanLocalityControl(topology, signals(), () -> List.of(PhantomTopologyCoreSuite.LEFT_POINT), () -> 1000, presence::isOnline, Map::of);
				locality.onPulse();
				PhantomAssertions.assertTrue(locality.isCurrentLocal(f.id), "Fixture must reach real readiness PASS.");
				final var port = new PhantomReconcileFirstActivityPort(new PhantomMaterializationServiceActivityPort(f.materialization));
				port.installPopulationReadiness(presence, locality, ecology);
				final var outcome = port.materialize(f.id);
				PhantomAssertions.assertEquals(PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, f.loadedPurpose, "Bridge must propagate typed native purpose.");
				context.record("integrated.actual", "task011=" + permission.reason() + ";currentLocal=true;outcome=" + outcome + ";admission=" + f.rejection.get());
				System.out.println("INTEGRATED task011 COMPLETE/ecology.native_materialization_required -> READY_PASS -> real MaterializationService -> " + outcome + ";admission=" + f.rejection.get());
				PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, outcome.outcome(), "INTEGRATED_RED: exact native-required readiness still calls NORMAL and is catchup.normal_fenced.");
			}
			finally { topology.beginStop(); topology.finishStop(); }
		}
	}

	private PhantomPopulationEcologyService ecology(Fixture f, boolean nativeRequired)
	{
		return ecology(f, nativeRequired, "exact");
	}

	private PhantomPopulationEcologyService ecology(Fixture f, boolean nativeRequired, String condition)
	{
		final Instant now = Instant.parse("2026-01-05T20:30:00Z");
		final var population = new PhantomPopulationTestDoubles.MemoryStore(_population.hash()).seedReady(f.id, 1);
		final var store = new PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null);
		final boolean ordinary = condition.equals("ordinary");
		final long cursor = ordinary ? f.target : condition.equals("from") ? f.from - 1 : f.from;
		final var state = new PhantomPopulationEcologyState(_catalog.hash(), PhantomPopulationEcologyState.Preset.LIVING, 1, 1, cursor, cursor, cursor, cursor,
			PhantomPopulationEcologyState.Pace.OUTLIER, 10000, 15, PhantomPopulationEcologyState.Personality.values()[0], Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), population.state().scheduleTemplate(),
			PhantomPopulationEcologyState.Disposition.MANAGED, f.target + 10000, 0, ordinary ? "" : condition.equals("request") ? "d".repeat(64) : f.claim, ordinary ? 0 : condition.equals("window") ? f.target + 1 : f.target, 0, 0, "");
		store.insert(f.id, state);
		final var historical = new PhantomPopulationEcologyService.HistoricalPort()
		{
			@Override public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long id)
			{
				return f.catchups.load(id).map(snapshot -> condition.equals("complete-history") ? new PhantomBackgroundCatchupStore.Snapshot(snapshot.state().running().advanceTo(f.target), snapshot.rowVersion()) : snapshot);
			}
			@Override public PhantomHistoricalBackgroundService.Result begin(long id, long from, long target, long seed)
			{
				if (condition.equals("exact")) { throw new AssertionError("Exact handoff must retain the original request."); }
				return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, "synthetic.request_identity_conflict", status(id).orElseThrow());
			}
			@Override public PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes)
			{
				return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, nativeRequired && !condition.equals("terminal") ? "native_context.required:accepted" : "synthetic.permanent_failure", status(id).orElseThrow());
			}
		};
		final var ecology = new PhantomPopulationEcologyService(_catalog, _population, store, historical, id -> false, id -> "", new PhantomPopulationTestDoubles.MutableClock(now), ZoneOffset.UTC,
			PhantomPopulationEcologyState.Preset.LIVING, 0, 1, worker -> { worker.run(); return true; });
		ecology.enablePeriodicDueMode();
		ecology.installMaterializationDemand(id -> !condition.equals("no-demand"));
		if (condition.equals("plan-held")) { ecology.holdStartupPopulationPlan(); }
		ecology.installRuntime(id -> id == f.id ? Optional.of(population) : Optional.empty(), new PhantomPopulationEcologyService.PopulationEvents()
		{
			@Override public void requestArchive(long id) { }
			@Override public void reconcilePopulation() { }
			@Override public void ecologyFenceChanged(long id) { }
		});
		ecology.register(population);
		if (!condition.equals("not-ready")) { ecology.onPopulationPulse(); ecology.onPopulationPulse(); }
		return ecology;
	}

	private static PhantomRelevanceSignalPort signals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}

	final class Fixture implements AutoCloseable
	{
		final long from = Instant.parse("2026-01-05T20:29:00Z").toEpochMilli() / 60000;
		final long target = from + 1;
		final String claim = "e".repeat(64);
		final long id;
		final int objectId = _environment.primary().objectId();
		final AtomicReference<String> rejection = new AtomicReference<>("");
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		final PhantomBackgroundCatchupStore catchups = new PhantomBackgroundCatchupStore(_profiles, goals);
		final PhantomMaterializationService materialization;
		final PhantomBackgroundService background;
		final PhantomHistoricalBackgroundService historical;
		final PhantomBackgroundTransaction transactions = new PhantomBackgroundTransaction();
		Runnable beforeLoad = () -> { };
		Runnable afterHistoricalLoad = () -> { };
		PhantomBackgroundState baseline;
		long loadedEpoch;
		PhantomMaterializationService.MaterializationPurpose loadedPurpose;

		Fixture() throws Exception { this(false); }

		Fixture(boolean visibleFarm) throws Exception
		{
			id = _profiles.create(objectId).profileId();
			final var ref = new AtomicReference<PhantomMaterializationService>();
			final var delegate = new AtomicReference<PhantomMaterializationLifecyclePort>();
			background = new PhantomBackgroundService(_profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions, _production.authority(), new PhantomBackgroundCompetitionRegistry(), signals(), ref::get);
			final var lifecycle = new PhantomMaterializationLifecyclePort()
			{
				@Override public void beforeMaterialize(long id, int object) { beforeMaterialize(id, object, PhantomMaterializationService.MaterializationPurpose.NORMAL, ""); }
				@Override public void beforeMaterialize(long id, int object, PhantomMaterializationService.MaterializationPurpose purpose, String owner)
				{
					loadedPurpose = purpose;
					try { delegate.get().beforeMaterialize(id, object, purpose, owner); }
					catch (AdmissionRejectedException error) { rejection.set(error.getMessage()); throw error; }
				}
				@Override public void afterPlayerLoad(long id, Player player)
				{
					Fixture.this.historical.afterPlayerLoad(id, player);
					loadedEpoch = player.getNativeWorkOwner().epoch();
					afterHistoricalLoad.run();
					background.afterPlayerLoad(id, player);
				}
				@Override public void materializeSucceeded(long id, int object) { delegate.get().materializeSucceeded(id, object); }
				@Override public void materializeAborted(long id, int object) { delegate.get().materializeAborted(id, object); }
				@Override public void beforeStore(long id, Player player) { delegate.get().beforeStore(id, player); }
				@Override public void afterStore(long id, Player player) { delegate.get().afterStore(id, player); }
			};
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1,
				point -> { if (point == FailurePoint.AFTER_IDENTITY_CLAIM) { beforeLoad.run(); } }, lifecycle, 5000, 10000);
			ref.set(materialization);
			final var planner = new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), _production.authority());
			historical = new PhantomHistoricalBackgroundService(_profiles, goals, planner, background, materialization);
			delegate.set(PhantomMaterializationLifecyclePort.chain(historical, background));
			background.start(); materialization.start();
			final Player setup = Player.load(objectId);
			PhantomAssertions.assertTrue(setup != null, "Ordinary native setup load.");
			try
			{
				final var anchor = _production.topology().findAnchor("population.farming.human-fighter.20545").orElseThrow();
				setup.setXYZInvisible(anchor.point().x(), anchor.point().y(), anchor.point().z());
				final var plan = visibleFarm ? planner.planInitial(id, setup, 1, 0) : planner.idleInitial(id, setup, 1, 0);
				PhantomAssertions.assertTrue(plan.ready(), "Canonical fixture plan.");
				goals.insert(id, plan.goal());
				setup.storeMe();
				final var capture = _production.authority().captureOwnedNative(id, setup, plan.goal(), null);
				final var captured = transactions.captureBaseline(capture.state(), plan.goal());
				PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, captured.status(), "Native canonical fixture capture.");
				baseline = captured.state();
			}
			finally { _environment.cleanupLoadedPlayer(setup); }
			final var goal = goals.load(id).orElseThrow().goal();
			catchups.claim(id, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING, claim, 1, from, target, from, 0, 0, 1, 1, 1, goal.goalId(), goal.revision(), "f".repeat(64), 1, _production.authority().hashes(), ""));
		}

		void handoff()
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id, PhantomMaterializationService.MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, claim).status(), "Exact handoff success.");
		}

		void removeCatchup()
		{
			final var component = _profiles.findComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElseThrow();
			_profiles.deleteComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE, component.rowVersion());
		}

		@Override public void close() throws Exception
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.shutdown().state(), "Owned materialization stop.");
			background.beginStop();
			PhantomAssertions.assertTrue(background.finishStop(), "Owned background stop.");
			_profiles.find(id).ifPresent(profile -> _profiles.delete(id, profile.rowVersion()));
		}
	}
}
