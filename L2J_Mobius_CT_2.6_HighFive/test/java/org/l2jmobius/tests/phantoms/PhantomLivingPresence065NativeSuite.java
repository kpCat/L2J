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
import java.util.concurrent.TimeUnit;
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

	@Override
	public String id() { return "living-presence-native065"; }

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "TASK065 native seed changed.");
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
		final var oldProfile = _repository.create(_environment.primary().objectId());
		final var newProfile = _repository.create(_incoming.objectId());
		final long oldId = oldProfile.profileId(), newId = newProfile.profileId();
		final var metrics = new PhantomMetrics();
		final var trace = new PhantomDiagnosticTrace(false, 64, 16, metrics);
		final var materializationRef = new AtomicReference<PhantomMaterializationService>();
		final var lifecycle = new PhantomMaterializationLifecycleBridge();
		final var goals = new PhantomGoalStateStore(_repository);
		final var transaction = new PhantomBackgroundTransaction();
		final var background = new PhantomBackgroundService(_repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, _production.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), materializationRef::get);
		final var materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, 1, _ -> {}, lifecycle, 5000, 10000);
		materializationRef.set(materialization);
		final var historical = new PhantomHistoricalBackgroundService(_repository, goals, new PhantomHistoricalBackgroundPlanner(_production.knowledge(), _production.topology(), _production.authority()), background, materialization);
		lifecycle.install(PhantomMaterializationLifecyclePort.chain(historical, background));
		final var topology = PhantomTopologyService.fromSnapshotForTesting(_production.topology().snapshot(), _production.topologyBackend(), PhantomTopologyPolicy.productionDefaults(), noSignals());
		final var population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final var ecologyCatalog = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), population, PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
		final var ecologyStore = new PhantomPopulationEcologyStore(_repository);
		final long minute = NOW.toEpochMilli() / 60_000;
		final var cohort = new HashMap<Long, ManagedSnapshot>();
		final var presence = new PhantomPresenceRegistry(2);
		final var seedMetadata = new PhantomPopulationTestDoubles.MemoryStore(population.hash());
		for (var profile : List.of(oldProfile, newProfile))
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
		final var ecology = new PhantomPopulationEcologyService(ecologyCatalog, population, ecologyStore, historical, id -> materialization.find(id).isPresent(), _ -> "", Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC, Preset.LIVING, 0, 2);
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
		}, retentionClock::get, 60000);
		ecology.installMaterializationDemand(locality::isCurrentLocal);
		locality.installPreparationDemand(ecology::updateMaterializationDemand, () -> materialization.snapshot().availablePermits() + (int)materialization.snapshot().materializations().stream().filter(entry -> entry.worldPresent() && retention.observe(entry.profileId()).reclaimable()).count());
		final var boundary = new PhantomMaterializationServiceActivityPort(materialization, true);
		final var gate = new PhantomReconcileFirstActivityPort(boundary);
		gate.installPopulationReadiness(presence, locality, ecology);
		gate.installRetention(id -> schedulerRef.get().snapshot().state() == PhantomScheduler.SchedulerState.RUNNING && retention.observe(id).retained());
		gate.installSoftReclamation(requester -> locality.isLocal(requester) && materialization.snapshot().availablePermits() == 0 ? retention.oldestSoft(materialization.snapshot().materializations().stream().filter(entry -> entry.worldPresent()).map(entry -> entry.profileId()).toList()) : 0, id -> retention.observe(id).reclaimable());
		final var scheduler = new PhantomScheduler(2, 100, 2, metrics, trace, gate, _ -> {});
		schedulerRef.set(scheduler);
		scheduler.installLocalControlPort(locality);
		try
		{
			PhantomAssertions.assertTrue(background.start() && materialization.start() && topology.start(), "Native owners did not start.");
			for (var entry : Map.of(oldId, oldPoint, newId, newPoint).entrySet())
			{
				topology.registerProfile(entry.getKey()); topology.updateProfile(entry.getKey(), entry.getValue(), 1);
			}
			cohort.values().forEach(ecology::register);
			await(() -> { ecology.onPopulationPulse(); return ecology.inventoryReady() && ecology.dueSnapshot(oldId).complete() && ecology.dueSnapshot(newId).complete(); }, 10000, "Native ecology current-cursor readiness failed.");
			PhantomAssertions.assertTrue(scheduler.start(), "Native pressure scheduler did not start.");
			scheduler.register(oldId); scheduler.register(newId);
			// Existing autonomous activity keeps this old owner from naturally idling out during the bounded episode.
			scheduler.submitSignal(oldId, new PhantomRelevanceSignal("test.existing.life", 1, PhantomActivityState.ACTIVE, 120000));
			await(() -> materialization.find(oldId).map(entry -> entry.worldPresent()).orElse(false), 12000, "Initial old native actor failed production admission: " + boundary.diagnosticFailures());
			final var oldLifetime = materialization.find(oldId).orElseThrow();
			retention.observe(oldId);
			final var hardAction = materialization.tryAcquireAction(oldId).orElseThrow();
			try
			{
				observer.set(newPoint);
				retentionClock.addAndGet(TimeUnit.SECONDS.toNanos(61));
				await(() -> locality.isCurrentLocal(newId) && !locality.isLocal(oldId), 3000, "Observer demand failed to move between real towns.");
				context.record("task065.before", "old=" + oldLifetime + " incoming=" + newId + " preparation=" + ecology.preparationSnapshot() + " due=" + ecology.dueSnapshot(newId));
				final long heldUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2200);
				while (System.nanoTime() < heldUntil)
				{
					PhantomAssertions.assertTrue(materialization.find(oldId).map(entry -> entry.worldPresent() && entry.materializedAtNanos() == oldLifetime.materializedAtNanos()).orElse(false), "Native admitted action was evicted.");
					PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_incoming.objectId()), "Full hard-held cap admitted a duplicate owner.");
					assertCap(materialization); Thread.sleep(25);
				}
				PhantomAssertions.assertTrue(retention.observe(oldId).hard(), "Admitted native action lacks hard hold.");
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.CAPACITY_REACHED, boundary.diagnosticFailures().get(newId), "Hard-held negative did not reach the actual native capacity boundary.");
				context.record("task065.hardHeld", "same native epoch; incoming remains pending; no eviction for admitted action");
			}
			finally { hardAction.close(); }
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
		finally
		{
			scheduler.beginStop();
			await(scheduler::finishStop, 5000, "Native pressure scheduler did not drain.");
			ecology.beginStop(); await(ecology::finishStop, 5000, "Native pressure ecology did not drain.");
			final var stopped = materialization.shutdown();
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, stopped.state(), "Native pressure materialization cleanup retained an owner: " + stopped);
			background.beginStop(); PhantomAssertions.assertTrue(background.finishStop(), "Native background owner did not stop.");
			topology.beginStop(); PhantomAssertions.assertTrue(topology.finishStop(), "Native topology owner did not stop.");
			for (var profile : List.of(oldProfile, newProfile)) { _repository.find(profile.profileId()).ifPresent(current -> _repository.delete(current.profileId(), current.rowVersion())); }
			_environment.assertClean(_environment.primary(), null); _environment.assertClean(_incoming, null);
		}
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
		PhantomAssertions.assertTrue(snapshot.availablePermits() >= 0 && snapshot.materializations().stream().filter(entry -> entry.worldPresent()).count() <= 1, "Native hard cap was exceeded.");
	}

	private static void await(BooleanSupplier condition, long timeoutMillis, String failure) throws Exception
	{
		final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (!condition.getAsBoolean() && System.nanoTime() < until) { Thread.sleep(20); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
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
