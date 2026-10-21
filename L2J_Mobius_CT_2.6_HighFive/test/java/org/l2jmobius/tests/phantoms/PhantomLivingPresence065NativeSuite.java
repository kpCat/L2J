/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/** Native cap-pressure gate. Point observers exercise the production envelope, not a network client. */
public final class PhantomLivingPresence065NativeSuite implements PhantomTestSuite
{
	private static final long SEED = 65_006_501L;
	private static final Instant NOW = Instant.parse("2026-10-19T20:30:00Z");
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomBackgroundSuite.ProductionAuthorityFixture _production;
	private PhantomProfileRepository _repository;
	private PhantomHeadlessPlayerFixture _incoming;
	private final boolean _burst066;

	public PhantomLivingPresence065NativeSuite() { this(false); }
	public PhantomLivingPresence065NativeSuite(boolean burst066) { _burst066 = burst066; }

	@Override
	public String id() { return _burst066 ? "farm-economy-cap-native066" : "living-presence-native065"; }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(_burst066 ? 66006621L : SEED, context.seed(), "Native capacity episode seed changed.");
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		_incoming = _environment.createCampaign058Companion(context, 1);
		_repository = PhantomProfileRepository.open();
		_production = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
		context.record("task065.database", PhantomCampaign058DatabaseLane.databaseName());
		context.record("task065.identities", List.of(_environment.primary(), _incoming));
		context.record("task065.observer", "Explicit topology point; no GameClient, synthetic session, or actual client visibility claim.");
		context.record("task065.authority", _production.knowledge().snapshot().combinedHash() + "/" + _production.topology().snapshot().canonicalHash());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-native-town-envelope-and-actual-regions", this::testTownEnvelope);
		registry.add("02-native-hard-hold-then-old-idle-cap-reclamation", this::testCapPressure);
		registry.add("03-exact-epoch-and-native-work-soft-cleanup-guards", this::testSoftCleanupGuards);
		if (_burst066) { registry.add("04-burst-loading-and-failed-retained-admissions", this::testBurstAdmission066); }
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try { if (_production != null) { _production.close(); } }
		finally { if (_environment != null) { _environment.shutdown(); } }
	}

	private PhantomTopologyPoint anchor(String id)
	{
		final var point = _production.topology().findAnchor(id).orElseThrow().point();
		PhantomAssertions.assertTrue(GeoEngine.getInstance().hasGeo(point.x(), point.y()), "Required native anchor GeoData is absent: " + id);
		return new PhantomTopologyPoint(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()), point.instanceId());
	}

	private void testBurstAdmission066(PhantomTestContext context) throws Exception
	{
		final var fixtures = List.of(_environment.primary(), _environment.observer(), _incoming);
		for (var fixture : fixtures) { preparePosition(fixture, anchor("gludin.recovery.anchor")); }
		final var profiles = fixtures.stream().map(fixture -> _repository.create(fixture.objectId())).toList();
		final var entered = new CountDownLatch(2); final var release = new CountDownLatch(1); final var finished = new CountDownLatch(2);
		final var failure = new AtomicReference<Throwable>(); final var failStore = new AtomicBoolean(); final var stores = new AtomicLong();
		final var results = new java.util.concurrent.CopyOnWriteArrayList<PhantomMaterializationService.ResultStatus>();
		final var metrics = new PhantomMetrics();
		final var service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1, 1, point ->
		{
			if (point == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint.AFTER_PLAYER_LOAD)
			{
				entered.countDown();
				try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("066 declared loading barrier timeout"); } }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
			}
			if (point == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint.BEFORE_STORE_OPERATION && failStore.get()) { throw new IllegalStateException("066 declared native pre-store failure"); }
			if (point == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint.AFTER_NATIVE_STORE) { stores.incrementAndGet(); }
		}, PhantomMaterializationLifecyclePort.none(), 5000, 10000);
		try
		{
			PhantomAssertions.assertTrue(service.start(), "Burst admission service did not start.");
			for (var profile : profiles.subList(0, 2))
			{
				org.l2jmobius.commons.threads.ThreadPool.execute(() ->
				{
					try { results.add(service.materialize(profile.profileId()).status()); }
					catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
					finally { finished.countDown(); }
				});
			}
			PhantomAssertions.assertTrue(entered.await(8, TimeUnit.SECONDS), "Two concurrent native loads did not reach the declared barrier.");
			PhantomAssertions.assertEquals(2, service.snapshot().reservedEntries(), "Loading owners are absent from the reserved gauge.");
			PhantomAssertions.assertEquals(2, service.snapshot().occupiedSlots(), "Loading owners do not occupy physical admissions.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CAPACITY_REACHED, service.materialize(profiles.get(2).profileId()).status(), "Concurrent loading exceeded soft1+burst1.");
			release.countDown(); PhantomAssertions.assertTrue(finished.await(8, TimeUnit.SECONDS), "Concurrent native loads did not finish.");
			PhantomAssertions.assertEquals(null, failure.get(), "Concurrent native admission threw.");
			PhantomAssertions.assertTrue(results.size() == 2 && results.stream().allMatch(status -> status == PhantomMaterializationService.ResultStatus.SUCCESS), "Concurrent native admissions failed: " + results);
			failStore.set(true);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CLEANUP_FAILED_RETAINED, service.dematerialize(profiles.get(0).profileId()).status(), "Declared pre-store failure did not retain its owner.");
			PhantomAssertions.assertEquals(2, service.snapshot().occupiedSlots(), "Failed cleanup returned a fictitious permit.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CAPACITY_REACHED, service.materialize(profiles.get(2).profileId()).status(), "Failed-retained owner was ignored by physical ceiling.");
			failStore.set(false);
			await(() -> { service.retryCleanup(profiles.get(0).profileId()); return service.find(profiles.get(0).profileId()).isEmpty(); }, 5000, "Normal retry failed to finish the declared pre-store incident.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, service.materialize(profiles.get(2).profileId()).status(), "Actual cleanup failed to return exactly one admission.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, service.shutdown().state(), "Burst admission cleanup did not stop.");
			PhantomAssertions.assertEquals(3L, stores.get(), "Successful native store was skipped or duplicated across failed retry.");
			context.record("task066.physicalAdmission", "Concurrent loading reserved2/occupied2 at soft1+burst1; third refused; failed native pre-store retains permit; normal retry then third admission; three successful stores exactly once; shutdown0 owners");
		}
		finally
		{
			release.countDown(); failStore.set(false); finished.await(10, TimeUnit.SECONDS);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, service.shutdown().state(), "Burst fixture retained native owners.");
			for (var profile : profiles) { _repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion())); }
			for (var fixture : fixtures) { _environment.assertClean(fixture, null); }
		}
	}

	private void testTownEnvelope(PhantomTestContext context)
	{
		final var giran = anchor("giran.city.center");
		final var gatekeeper = anchor("giran.gatekeeper.30080");
		final var shop = anchor("giran.shop.30081");
		final var north = anchor("giran.route.north");
		final var gludin = anchor("gludin.recovery.anchor");
		final int edgeX = giran.x() + (north.x() - giran.x()) * 2 / 3;
		final int edgeY = giran.y() + (north.y() - giran.y()) * 2 / 3;
		final var edge = new PhantomTopologyPoint(edgeX, edgeY, GeoEngine.getInstance().getHeight(edgeX, edgeY, north.z()), 0);
		for (var point : List.of(giran, gatekeeper, shop, north, edge, gludin))
		{
			final var nativeRegion = World.getInstance().getRegion(point.x(), point.y(), point.z());
			PhantomAssertions.assertTrue(nativeRegion != null, "Anchor has no actual WorldRegion: " + point);
			PhantomAssertions.assertEquals(World.getInstance().getRegion(giran.x(), giran.y(), giran.z()).isSurroundingRegion(nativeRegion), PhantomNativeLocalityEnvelope.couldKnow(giran, point), "Point envelope differs from native surrounding regions.");
			context.record("task065.geometry." + point.x() + "." + point.y(), "point=" + point + " region=" + PhantomNativeLocalityEnvelope.region(point) + " prewarm=" + PhantomNativeLocalityEnvelope.prewarm(giran, point) + " couldKnow=" + PhantomNativeLocalityEnvelope.couldKnow(giran, point));
		}
		PhantomAssertions.assertTrue(PhantomNativeLocalityEnvelope.couldKnow(giran, gatekeeper), "Giran center lost its native gatekeeper neighborhood.");
		PhantomAssertions.assertTrue(PhantomNativeLocalityEnvelope.couldKnow(giran, shop), "Giran native merchant neighborhood was lost.");
		PhantomAssertions.assertTrue(PhantomNativeLocalityEnvelope.prewarm(giran, edge) && !PhantomNativeLocalityEnvelope.couldKnow(giran, edge), "Giran center-to-north approach must distinguish preparation from native surrounding visibility.");
		PhantomAssertions.assertFalse(PhantomNativeLocalityEnvelope.prewarm(giran, gludin), "Contrast town became global city demand.");
		PhantomAssertions.assertFalse(PhantomNativeLocalityEnvelope.prewarm(giran, new PhantomTopologyPoint(giran.x(), giran.y(), giran.z(), 1)), "Other instance leaked into demand.");
		final var deep = new PhantomTopologyPoint(giran.x(), giran.y(), giran.z() - 3 * World.Z_REGION_SIZE, 0);
		PhantomAssertions.assertFalse(PhantomNativeLocalityEnvelope.prewarm(giran, deep), "Unrelated native Z plane leaked into preparation.");
		PhantomAssertions.assertEquals(0L, World.getInstance().getPlayers().stream().filter(player -> player.getObjectId() == _environment.primary().objectId() || player.getObjectId() == _incoming.objectId()).count(), "Geometry measurement materialized a fixture.");
	}

	private void preparePosition(PhantomHeadlessPlayerFixture fixture, PhantomTopologyPoint point) throws Exception
	{
		PhantomAssertions.assertTrue(_repository.findByCharacterObjectId(fixture.objectId()).isEmpty(), "Fixture unexpectedly retains a prior profile.");
		final Player player = Player.load(fixture.objectId());
		PhantomAssertions.assertTrue(player != null, "Stored native fixture did not load.");
		try { player.setXYZInvisible(point.x(), point.y(), point.z()); player.storeMe(); }
		finally { _environment.cleanupLoadedPlayer(player); }
	}

	private void testCapPressure(PhantomTestContext context) throws Exception
	{
		final var oldPoint = anchor("gludin.recovery.anchor");
		final var newPoint = anchor("giran.city.center");
		preparePosition(_environment.primary(), oldPoint);
		preparePosition(_incoming, newPoint);
		if (_burst066) { preparePosition(_environment.observer(), oldPoint); }
		final var oldProfile = _repository.create(_environment.primary().objectId());
		final var newProfile = _repository.create(_incoming.objectId());
		final var secondOld = _burst066 ? _repository.create(_environment.observer().objectId()) : null;
		final var profiles = secondOld == null ? List.of(oldProfile, newProfile) : List.of(oldProfile, secondOld, newProfile);
		final long oldId = oldProfile.profileId(), newId = newProfile.profileId();
		final var metrics = new PhantomMetrics();
		final var trace = new PhantomDiagnosticTrace(false, 64, 16, metrics);
		final var materializationRef = new AtomicReference<PhantomMaterializationService>();
		final var lifecycle = new PhantomMaterializationLifecycleBridge();
		final var goals = new PhantomGoalStateStore(_repository);
		final var transaction = new PhantomBackgroundTransaction();
		final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
		final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, _burst066 ? 2 : 1, _burst066 ? 2 : 0, _ -> {}, lifecycle, 5000, 10000);
		materializationRef.set(materialization);
		final var historical = new PhantomHistoricalBackgroundService(_repository, goals, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), _production.authority()), background, materialization);
		lifecycle.install(PhantomMaterializationLifecyclePort.chain(historical, background));
		final var topology = PhantomTopologyService.fromSnapshotForTesting(_production.topology().snapshot(), _production.topologyBackend(), PhantomTopologyPolicy.productionDefaults(), noSignals());
		final var population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final var ecologyCatalog = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), population, PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
		final var ecologyStore = new PhantomPopulationEcologyStore(_repository);
		final long minute = NOW.toEpochMilli() / 60_000;
		final var cohort = new HashMap<Long, ManagedSnapshot>();
		final var presence = new PhantomPresenceRegistry(profiles.size());
		final var seedMetadata = new PhantomPopulationTestDoubles.MemoryStore(population.hash());
		for (var profile : profiles)
		{
			final var state = seedMetadata.seedReady(profile.profileId(), 1).state().initialized(profile.characterObjectId(), "2".repeat(64)).ready();
			final var component = _repository.insertComponent(profile.profileId(), PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, new PhantomPopulationStateCodec().encode(state));
			cohort.put(profile.profileId(), new ManagedSnapshot(profile, component, state));
			final var assignment = ecologyCatalog.assign(Preset.LIVING, 1, profile.profileId(), context.seed(), minute, 0, 0, state.scheduleTemplate());
			// A fresh current cursor has no elapsed historical work; the real readiness owner still verifies it.
			ecologyStore.insert(profile.profileId(), new PhantomPopulationEcologyState(assignment.catalogHash(), assignment.preset(), assignment.ecologyGeneration(), assignment.assignmentOrdinal(), minute, minute, minute, minute, assignment.pace(), assignment.productiveShareBasisPoints(), assignment.productiveBlockMinutes(), assignment.personality(), assignment.initialSocialTraits(), state.scheduleTemplate(), assignment.disposition(), minute + 10000, 0, "", 0, 0, 0, ""));
			presence.schedule(profile.profileId(), population.evaluate(state.scheduleTemplate(), NOW, ZoneOffset.UTC, state.schedulePhaseMinutes()).state());
			PhantomAssertions.assertTrue(presence.isOnline(profile.profileId()), "Native episode is not calendar eligible.");
		}
		final var ecology = new PhantomPopulationEcologyService(ecologyCatalog, population, ecologyStore, historical, id -> materialization.find(id).isPresent(), _ -> "", Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC, Preset.LIVING, 0, profiles.size());
		ecology.enablePeriodicDueMode();
		ecology.installRuntime(id -> Optional.ofNullable(cohort.get(id)), new PhantomPopulationEcologyService.PopulationEvents()
		{
			@Override public void requestArchive(long id) { throw new AssertionError("Native pressure cannot retire a profile."); }
			@Override public void reconcilePopulation() {}
			@Override public void ecologyFenceChanged(long id) {}
		});
		final var observer = new AtomicReference<>(oldPoint);
		final var schedulerRef = new AtomicReference<PhantomScheduler>();
		final var signalPort = new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return new PhantomSchedulerRelevanceSignalPort(schedulerRef.get()).submit(id, signal); }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return new PhantomSchedulerRelevanceSignalPort(schedulerRef.get()).withdraw(id, source, sequence); }
		};
		final var locality = new PhantomHumanLocalityControl(topology, signalPort, () -> List.of(observer.get()), () -> System.nanoTime() / 1_000_000L, presence::isOnline, () -> livePoints(materialization));
		final var retentionClock = new AtomicLong(System.nanoTime());
		final var retention = new PhantomMaterializationRetentionPolicy(id ->
		{
			final var snapshot = materialization.find(id).orElse(null);
			final Player player = snapshot == null ? null : World.getInstance().getPlayer(snapshot.characterObjectId());
			final var nativeOwner = player == null ? null : player.getNativeWorkOwner();
			return player == null || !snapshot.worldPresent() || player.getObjectId() != snapshot.characterObjectId() || nativeOwner == null || nativeOwner.epoch() != snapshot.materializedAtNanos() || !nativeOwner.isCurrent() ? new PhantomMaterializationRetentionPolicy.Facts(false, false, false, false, false) : PhantomMaterializationRetentionPolicy.Facts.nativeFacts(player, locality.isLocal(id), locality.isNativeVisible(id), snapshot.admittedActionCount());
		}, _burst066 ? System::nanoTime : retentionClock::get, 60000);
		if (_burst066) { materialization.installSoftCleanupAdmission(id -> !PhantomNativeLocalityEnvelope.prewarm(observer.get(), id == newId ? newPoint : oldPoint)); }
		ecology.installMaterializationDemand(locality::isCurrentLocal);
		locality.installPreparationDemand(ecology::updateMaterializationDemand, () -> materialization.snapshot().availablePermits() + (int)materialization.snapshot().materializations().stream().filter(entry -> entry.worldPresent() && retention.observe(entry.profileId()).reclaimable()).count());
		final var boundary = new PhantomMaterializationServiceActivityPort(materialization, true);
		final var gate = new PhantomReconcileFirstActivityPort(boundary);
		gate.installPopulationReadiness(presence, locality, ecology);
		gate.installRetention(id -> schedulerRef.get().snapshot().state() == PhantomScheduler.SchedulerState.RUNNING && retention.observe(id).retained());
		gate.installSoftReclamationEpochs(requester ->
		{
			if (!locality.isLocal(requester)) { return org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection.none(); }
			final var snapshot = materialization.snapshot();
			if (snapshot.availablePermits() != 0) { return org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection.none(); }
			final long selected = retention.oldestSoft(snapshot.materializations().stream().filter(entry -> entry.worldPresent()).map(entry -> entry.profileId()).toList());
			return snapshot.materializations().stream().filter(entry -> entry.profileId() == selected && entry.worldPresent()).findFirst()
				.map(entry -> new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection(entry.profileId(), entry.materializedAtNanos()))
				.orElseGet(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection::none);
		}, id -> retention.observe(id).reclaimable() && (!_burst066 || !retention.observe(id).retained()), locality::isCurrentLocal);
		final var scheduler = new PhantomScheduler(profiles.size(), 100, profiles.size(), metrics, trace, gate, _ -> {});
		schedulerRef.set(scheduler);
		scheduler.installLocalControlPort(() ->
		{
			locality.onPulse();
			if (_burst066)
			{
				retention.refresh(materialization.list().stream().filter(entry -> entry.worldPresent()).map(entry -> entry.profileId()).toList());
				for (var entry : materialization.list())
				{
					if (entry.worldPresent() && !retention.observe(entry.profileId()).retained()) { scheduler.requestIdleRetirement(entry.profileId(), entry.materializedAtNanos()); }
				}
			}
		});
		Throwable primaryFailure = null;
		try
		{
			PhantomAssertions.assertTrue(background.start() && materialization.start() && topology.start(), "Native owners did not start.");
			final var points = new HashMap<>(Map.of(oldId, oldPoint, newId, newPoint));
			if (secondOld != null) { points.put(secondOld.profileId(), oldPoint); }
			for (var entry : points.entrySet())
			{
				topology.registerProfile(entry.getKey()); topology.updateProfile(entry.getKey(), entry.getValue(), 1);
			}
			cohort.values().forEach(ecology::register);
			await(() -> { ecology.onPopulationPulse(); return ecology.inventoryReady() && ecology.dueSnapshot(oldId).complete() && ecology.dueSnapshot(newId).complete(); }, 10000, "Native ecology current-cursor readiness failed.");
			PhantomAssertions.assertTrue(scheduler.start(), "Native pressure scheduler did not start.");
			profiles.forEach(profile -> scheduler.register(profile.profileId()));
			// Existing autonomous activity keeps this old owner from naturally idling out during the bounded episode.
			scheduler.submitSignal(oldId, new PhantomRelevanceSignal("test.existing.life", 1, PhantomActivityState.ACTIVE, 120000));
			if (secondOld != null)
			{
				scheduler.submitSignal(secondOld.profileId(), new PhantomRelevanceSignal("test.existing.life", 1, PhantomActivityState.ACTIVE, 180000));
				await(() -> materialization.find(secondOld.profileId()).map(entry -> entry.worldPresent() && entry.actionAdmissionOpen()).orElse(false), 12000, "Second soft target slot did not admit its real Player.");
			}
			await(() -> materialization.find(oldId).map(entry ->
			{
				final Player current = World.getInstance().getPlayer(entry.characterObjectId());
				final var owner = current == null ? null : current.getNativeWorkOwner();
				return entry.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE && entry.worldPresent() && entry.actionAdmissionOpen()
					&& owner != null && owner.isCurrent() && owner.epoch() == entry.materializedAtNanos();
			}).orElse(false), 12000, "Initial old native actor failed complete production admission: " + boundary.diagnosticFailures());
			final var oldLifetime = materialization.find(oldId).orElseThrow();
			retention.observe(oldId);
			final var hardAction = materialization.tryAcquireAction(oldId).orElseThrow();
			try
			{
				final long arrivalStart = System.nanoTime();
				observer.set(newPoint);
				if (!_burst066) { retentionClock.addAndGet(TimeUnit.SECONDS.toNanos(61)); }
				await(() -> locality.isCurrentLocal(newId) && !locality.isLocal(oldId), 3000, "Observer demand failed to move between real towns.");
				context.record("task065.before", "old=" + oldLifetime + " incoming=" + newId + " preparation=" + ecology.preparationSnapshot() + " due=" + ecology.dueSnapshot(newId));
				if (_burst066)
				{
					await(() -> materialization.find(newId).map(entry -> entry.worldPresent() && entry.actionAdmissionOpen()).orElse(false), 10000, "Ready local actor waited for old cleanup despite burst reserve.");
					PhantomAssertions.assertTrue(materialization.find(oldId).orElseThrow().worldPresent() && materialization.find(secondOld.profileId()).orElseThrow().worldPresent(), "Burst admission evicted an old owner before grace.");
					PhantomAssertions.assertEquals(3, materialization.snapshot().occupiedSlots(), "Burst must count3 actual owners against separate soft2/ceiling4.");
					context.record("task066.arrivalMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - arrivalStart));
					context.record("task066.arrival", "soft2,burst2,ceiling4,occupied3,excess1; incoming admitted while admitted hard action and both old native epochs remain");
				}
				final long heldUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2200);
				while (System.nanoTime() < heldUntil)
				{
					PhantomAssertions.assertTrue(materialization.find(oldId).map(entry -> entry.worldPresent() && entry.materializedAtNanos() == oldLifetime.materializedAtNanos()).orElse(false), "Native admitted action was evicted.");
					if (!_burst066) { PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_incoming.objectId()), "Full hard-held cap admitted a duplicate owner."); }
					assertCap(materialization); Thread.sleep(25);
				}
				PhantomAssertions.assertTrue(retention.observe(oldId).hard(), "Admitted native action lacks hard hold.");
				if (!_burst066) { PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CAPACITY_REACHED, boundary.diagnosticFailures().get(newId), "Hard-held negative did not reach the actual native capacity boundary."); }
				context.record("task065.hardHeld", _burst066 ? "same old native epoch; incoming admitted in reserve; no eviction for admitted action" : "same native epoch; incoming remains pending; no eviction for admitted action");
			}
			finally { hardAction.close(); }
			if (_burst066)
			{
				observer.set(oldPoint);
				await(() -> locality.isCurrentLocal(oldId) && retention.observe(oldId).humanLocal(), 3000, "Return observation did not restore old local hold.");
				PhantomAssertions.assertEquals(oldLifetime.materializedAtNanos(), materialization.find(oldId).orElseThrow().materializedAtNanos(), "Return before seal duplicated the old identity.");
				retention.observe(secondOld.profileId());
				observer.set(newPoint);
				await(() -> !locality.isLocal(oldId) && locality.isCurrentLocal(newId), 3000, "Second departure did not reach actual local control.");
				final long departure = retention.observe(oldId).lastHumanNanos();
				context.record("task066.cleanupExpected", "Real monotonic human grace60s + native scheduler/drain, idle deadline120s; no further cap-pressure demand, occupied3<ceiling4");
				while (System.nanoTime() - departure < TimeUnit.SECONDS.toNanos(58))
				{
					PhantomAssertions.assertTrue(materialization.find(oldId).map(entry -> entry.worldPresent() && entry.materializedAtNanos() == oldLifetime.materializedAtNanos()).orElse(false), "Old lifetime was evicted within its returned human grace.");
					assertCap(materialization); Thread.sleep(50);
				}
				await(() -> materialization.find(oldId).isEmpty() && materialization.find(secondOld.profileId()).isEmpty(), 62000, "Expired idle native owners stayed forever without cap pressure.");
				PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_environment.primary().objectId()), "Lazy cleanup retained primary in actual World.");
				PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_environment.observer().objectId()), "Lazy cleanup retained second old Player.");
				PhantomAssertions.assertEquals(1, materialization.snapshot().occupiedSlots(), "Lazy cleanup did not release both physical permits.");
				PhantomAssertions.assertEquals(0, materialization.snapshot().excessSlots(), "Excess did not drain after grace.");
				context.record("task066.cleanupMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - departure));
				context.record("task066.cleanup", "Same old epoch survived observation return; two old identities stored/deleted/released; incoming remains native; occupied3->1,excess1->0; no accelerated native clock");
				return;
			}
			PhantomAssertions.assertFalse(retention.observe(oldId).hard() || retention.observe(oldId).humanLocal() || retention.observe(oldId).reasons().contains(PhantomMaterializationRetentionPolicy.Reason.RECENT_HUMAN), "Old idle fixture is not the expired human hold regression.");
			final long started = System.nanoTime();
			final long startPulse = scheduler.snapshot().pulseSequence();
			final long until = started + TimeUnit.SECONDS.toNanos(12);
			while (World.getInstance().getPlayer(_incoming.objectId()) == null && System.nanoTime() < until) { assertCap(materialization); Thread.sleep(25); }
			context.record("task065.pressureMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
			final long pressurePulses = scheduler.snapshot().pulseSequence() - startPulse;
			context.record("task065.pressurePulses", pressurePulses);
			PhantomAssertions.assertTrue(pressurePulses <= 130, "Native admission exceeded the bounded scheduler pulse horizon.");
			context.record("task065.after", "oldHold=" + retention.observe(oldId) + " capacity=" + materialization.snapshot() + " preparation=" + ecology.preparationSnapshot() + " due=" + ecology.dueSnapshot(newId) + " scheduler=" + scheduler.list() + " failures=" + boundary.diagnosticFailures());
			final Player admitted = World.getInstance().getPlayer(_incoming.objectId());
			PhantomAssertions.assertTrue(admitted != null && admitted.isOnline() && admitted.hasHeadlessOutboundSession(), "CAP065: expired invisible idle owner blocked real scheduler admission at full cap.");
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_environment.primary().objectId()), "Safe old owner did not leave actual World through cleanup.");
			PhantomAssertions.assertEquals(newPoint, new PhantomTopologyPoint(admitted.getX(), admitted.getY(), admitted.getZ(), admitted.getInstanceId()), "Admission altered the premeasurement canonical position.");
			PhantomAssertions.assertTrue(materialization.find(newId).orElseThrow().worldPresent() && materialization.find(oldId).isEmpty(), "World and materialization ownership disagree after pressure.");
			PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_environment.primary().objectId()), "Old native lease survived ordinary cleanup.");
			assertCap(materialization);
			final long stableUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2200);
			final long newEpoch = materialization.find(newId).orElseThrow().materializedAtNanos();
			while (System.nanoTime() < stableUntil)
			{
				PhantomAssertions.assertEquals(newEpoch, materialization.find(newId).orElseThrow().materializedAtNanos(), "Local admission ping-ponged during its native hold.");
				assertCap(materialization); Thread.sleep(25);
			}
			context.record("task065.nativeOutcome", "old=" + oldId + "/" + oldLifetime.materializedAtNanos() + " World0; new=" + newId + "/" + newEpoch + " World1; cap1; no manual materialize/XYZ/EXP in measurement");
		}
		catch (Exception | Error failure)
		{
			primaryFailure = failure;
			context.record("task065.primaryFailure", failure.getClass().getName() + ":" + failure.getMessage() + ":" + java.util.Arrays.toString(java.util.Arrays.copyOf(failure.getStackTrace(), Math.min(8, failure.getStackTrace().length))));
			throw failure;
		}
		finally
		{
			Throwable cleanupFailure = cleanupStep(null, scheduler::beginStop);
			cleanupFailure = cleanupStep(cleanupFailure, () ->
			{
				final var stopped = materialization.shutdown();
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, stopped.state(), "Native pressure materialization cleanup retained an owner: " + stopped);
			});
			cleanupFailure = cleanupStep(cleanupFailure, () -> await(scheduler::finishStop, 5000, "Native pressure scheduler did not drain after canonical cleanup."));
			cleanupFailure = cleanupStep(cleanupFailure, () -> { ecology.beginStop(); await(ecology::finishStop, 5000, "Native pressure ecology did not drain."); });
			cleanupFailure = cleanupStep(cleanupFailure, () -> { background.beginStop(); PhantomAssertions.assertTrue(background.finishStop(), "Native background owner did not stop."); });
			cleanupFailure = cleanupStep(cleanupFailure, () -> { topology.beginStop(); PhantomAssertions.assertTrue(topology.finishStop(), "Native topology owner did not stop."); });
			for (var profile : profiles)
			{
				cleanupFailure = cleanupStep(cleanupFailure, () ->
				{
					PhantomAssertions.assertTrue(materialization.find(profile.profileId()).isEmpty(), "Retained native owner forbids fixture profile deletion.");
					_repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion()));
				});
			}
			cleanupFailure = cleanupStep(cleanupFailure, () -> _environment.assertClean(_environment.primary(), null));
			cleanupFailure = cleanupStep(cleanupFailure, () -> _environment.assertClean(_incoming, null));
			if (_burst066) { cleanupFailure = cleanupStep(cleanupFailure, () -> _environment.assertClean(_environment.observer(), null)); }
			if (cleanupFailure != null)
			{
				context.record("task065.cleanupFailure", cleanupFailure.toString());
				if (primaryFailure != null) { primaryFailure.addSuppressed(cleanupFailure); }
				else if (cleanupFailure instanceof Exception exception) { throw exception; }
				else { throw (Error) cleanupFailure; }
			}
		}
	}

	@FunctionalInterface
	private interface CleanupStep { void run() throws Exception; }

	private static Throwable cleanupStep(Throwable first, CleanupStep step)
	{
		try { step.run(); }
		catch (Exception | Error failure)
		{
			if (first == null) { return failure; }
			if (first != failure) { first.addSuppressed(failure); }
		}
		return first;
	}

	private void testSoftCleanupGuards(PhantomTestContext context) throws Exception
	{
		preparePosition(_environment.primary(), anchor("gludin.recovery.anchor"));
		final var profile = _repository.create(_environment.primary().objectId());
		final long id = profile.profileId();
		final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1);
		final var boundary = new PhantomMaterializationServiceActivityPort(materialization);
		final var sealAdmission = new AtomicBoolean(true);
		if (_burst066) { materialization.installSoftCleanupAdmission(_ -> sealAdmission.get()); }
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Guard fixture service did not start.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Guard fixture requires a canonical native Player.");
			final var first = materialization.find(id).orElseThrow();
			final Player player = World.getInstance().getPlayer(first.characterObjectId());
			final var owner = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner();
			final long epoch = first.materializedAtNanos();
			if (_burst066)
			{
				sealAdmission.set(false);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "Return at the native seal did not cancel stale retirement.");
				assertSoftGuardRetained(materialization, id, player, epoch); sealAdmission.set(true);
			}
			final long requester = id + 1;
			final var currentDemand = new AtomicBoolean(true);
			final var demandGate = new PhantomReconcileFirstActivityPort(boundary);
			demandGate.installSoftReclamationEpochs(_ -> new org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.SoftReclaimSelection(id, epoch), _ -> true, requested -> requested == requester && currentDemand.get());
			final var selected = demandGate.softReclaimSelection(requester);
			PhantomAssertions.assertEquals(requester, selected.requestingProfileId(), "Soft selection lost the original requester.");
			currentDemand.set(false);
			final var withdrawn = demandGate.reclaimSoft(selected.profileId(), selected.materializedAtNanos(), selected.requestingProfileId());
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, withdrawn.outcome(), "Withdrawn incoming demand must preserve the idle victim.");
			PhantomAssertions.assertEquals("materialization.soft_request_demand_stale", withdrawn.reason(), "Withdrawn request must have an explicit deferral reason.");
			assertSoftGuardRetained(materialization, id, player, epoch);
			currentDemand.set(true);
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch - 1).outcome(), "Wrong incarnation must defer without a retained failure.");
			assertSoftGuardRetained(materialization, id, player, epoch);
			try (var action = materialization.tryAcquireAction(id).orElseThrow())
			{
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, demandGate.reclaimSoft(id, epoch, requester).outcome(), "Current demand must still preserve an admitted native action.");
				assertSoftGuardRetained(materialization, id, player, epoch);
			}
			testNativeHeldInputs(materialization, boundary, id, player, epoch);
			final var earned = owner.reserve(null, "TASK065_GUARD_EARNED", org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics.EARNED);
			PhantomAssertions.assertTrue(earned != null, "Exact native earned reservation was not admitted.");
			try
			{
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "Reserved earned native work must reject soft cleanup.");
				assertSoftGuardRetained(materialization, id, player, epoch);
			}
			finally
			{
				PhantomAssertions.assertTrue(earned.tryStart(), "Refusal cancelled the legitimate earned reservation.");
				earned.complete(null);
			}
			final var entered = new CountDownLatch(1);
			final var release = new CountDownLatch(1);
			final var finished = new CountDownLatch(1);
			final var failure = new AtomicReference<Throwable>();
			org.l2jmobius.commons.threads.ThreadPool.execute(() ->
			{
				try
				{
					owner.checkpoint(() ->
					{
						entered.countDown();
						try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("TASK065 checkpoint guard release timeout."); } }
						catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
						player.storeMe();
						return null;
					});
				}
				catch (Throwable thrown) { failure.set(thrown); }
				finally { finished.countDown(); }
			});
			try
			{
				PhantomAssertions.assertTrue(entered.await(5, TimeUnit.SECONDS), "Actual native checkpoint did not enter.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "Executing persistence checkpoint must reject soft cleanup.");
				assertSoftGuardRetained(materialization, id, player, epoch);
			}
			finally
			{
				release.countDown();
				PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "Original persistence checkpoint did not finish.");
			}
			PhantomAssertions.assertEquals(null, failure.get(), "Soft refusal damaged the admitted native checkpoint.");
			PhantomAssertions.assertTrue(owner.open(), "Original native owner did not reopen after its own successful checkpoint.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "Ordinary cleanup before a new incarnation.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Canonical rematerialization after ordinary cleanup.");
			final var second = materialization.find(id).orElseThrow();
			final Player replacement = World.getInstance().getPlayer(second.characterObjectId());
			PhantomAssertions.assertTrue(second.materializedAtNanos() != epoch && replacement != player, "Guard fixture reused the old native incarnation.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "A stale selection must not clean the replacement incarnation.");
			assertSoftGuardRetained(materialization, id, replacement, second.materializedAtNanos());
			await(() -> boundary.reclaimSoft(id, second.materializedAtNanos()).outcome() == org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.SUCCESS, 3000, "Current idle incarnation never completed ordinary guarded cleanup.");
			PhantomAssertions.assertTrue(materialization.find(id).isEmpty() && World.getInstance().getPlayer(second.characterObjectId()) == null && materialization.snapshot().availablePermits() == 1, "Current idle cleanup failed native ownership/permit conservation.");
			context.record("task065.softGuards", "withdrawnRequester,wrongEpoch,admittedAction,nativeRealParty,nativePrivateStore,reservedEarned,persistenceCheckpoint,replacementEpoch=DEFERRED; original owners retained; currentIdle=STORED; permit=1");
		}
		finally
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.shutdown().state(), "Guard fixture retained a native owner.");
			_repository.find(id).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion()));
			_environment.assertClean(_environment.primary(), null);
		}
	}

	private void testNativeHeldInputs(PhantomMaterializationService materialization, PhantomMaterializationServiceActivityPort boundary, long id, Player player, long epoch) throws Exception
	{
		final Player real = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(real != null && !real.hasHeadlessOutboundSession(), "Own ordinary native party fixture is unavailable.");
		final var party = new org.l2jmobius.gameserver.model.groups.Party(real, org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS);
		try
		{
			try (var action = materialization.tryAcquireAction(id).orElseThrow()) { real.setParty(party); player.joinParty(party); }
			PhantomAssertions.assertTrue(player.getParty() == party && real.getParty() == party && party.getMembers().containsAll(List.of(real, player)), "Stock Party join did not retain both exact native Players.");
			PhantomAssertions.assertTrue(PhantomMaterializationRetentionPolicy.Facts.nativeFacts(player, false, false, 0).realParty(), "Native mixed party lost its REAL_PARTY hold.");
			await(() -> materialization.find(id).orElseThrow().admittedActionCount() == 0, 3000, "Party setup native work did not finish before the guard measurement.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "Idle native party member must reject soft cleanup.");
			assertSoftGuardRetained(materialization, id, player, epoch);
			PhantomAssertions.assertTrue(player.getParty() == party && real.getParty() == party, "Soft refusal damaged stock Party membership.");
		}
		finally
		{
			try (var action = materialization.tryAcquireAction(id).orElseThrow())
			{
				org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(player);
				org.l2jmobius.gameserver.model.groups.PartyInvitationService.getInstance().leave(real);
			}
			finally { _environment.cleanupLoadedPlayer(real); }
		}
		PhantomAssertions.assertFalse(player.isInParty(), "Ordinary native party leave did not restore the original actor.");
		_environment.assertClean(_environment.observer(), real);
		final var stores = new org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService(_repository, materialization);
		final long now = System.currentTimeMillis();
		final var plan = new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan(org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Type.BUY, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.State.REQUESTED,
			"TASK065 guarded native BUY", List.of(new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Line(1865, 1865, 1, 0)), now + 60000);
		try
		{
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.Result.OPENED, stores.open(id, PhantomActivityState.ACTIVE, plan, now), "Existing store owner did not open a canonical BUY list.");
			PhantomAssertions.assertTrue(player.isInStoreMode() && player.getBuyList().getItemCount() == 1 && stores.blocksDecision(id), "Canonical private store/list/owner are absent.");
			await(() -> materialization.find(id).orElseThrow().admittedActionCount() == 0, 3000, "Store setup native work did not finish before the guard measurement.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, boundary.reclaimSoft(id, epoch).outcome(), "Idle native private store owner must reject soft cleanup.");
			assertSoftGuardRetained(materialization, id, player, epoch);
			PhantomAssertions.assertTrue(player.isInStoreMode() && player.getBuyList().getItemCount() == 1 && stores.blocksDecision(id), "Soft refusal damaged the canonical private store.");
		}
		finally { await(() -> stores.close(id) == org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.Result.CLOSED, 3000, "Existing private-store close did not release its exact owner."); }
		PhantomAssertions.assertTrue(!player.isInStoreMode() && player.getBuyList().getItemCount() == 0 && !stores.blocksDecision(id), "Canonical private-store close retained its listing or owner.");
	}

	private static void assertSoftGuardRetained(PhantomMaterializationService materialization, long id, Player expected, long epoch)
	{
		final var snapshot = materialization.find(id).orElseThrow();
		PhantomAssertions.assertTrue(snapshot.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE && snapshot.actionAdmissionOpen() && snapshot.worldPresent() && snapshot.materializedAtNanos() == epoch && World.getInstance().getPlayer(snapshot.characterObjectId()) == expected, "Soft refusal closed admission or replaced the exact native owner.");
		PhantomAssertions.assertEquals(0, materialization.snapshot().availablePermits(), "Soft refusal released the occupied permit.");
		PhantomAssertions.assertEquals(null, snapshot.firstCleanupIncident(), "Healthy soft refusal created a cleanup failure incident.");
	}

	private static Map<Long, PhantomTopologyPoint> livePoints(PhantomMaterializationService materialization)
	{
		final var result = new HashMap<Long, PhantomTopologyPoint>();
		for (var entry : materialization.snapshot().materializations())
		{
			final Player player = World.getInstance().getPlayer(entry.characterObjectId());
			if (entry.worldPresent() && player != null) { result.put(entry.profileId(), new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId())); }
		}
		return Map.copyOf(result);
	}

	private static void assertCap(PhantomMaterializationService materialization)
	{
		final var snapshot = materialization.snapshot();
		PhantomAssertions.assertTrue(snapshot.availablePermits() >= 0 && snapshot.occupiedSlots() <= snapshot.physicalCeiling() && snapshot.materializations().stream().filter(entry -> entry.worldPresent()).count() <= snapshot.physicalCeiling(), "Native physical ceiling was exceeded.");
	}

	private static void await(BooleanSupplier condition, long timeoutMillis, String failure) throws Exception
	{
		final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		boolean completed = condition.getAsBoolean();
		while (!completed && System.nanoTime() < until) { Thread.sleep(20); completed = condition.getAsBoolean(); }
		PhantomAssertions.assertTrue(completed, failure);
	}

	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
}
