/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.activity.*;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.population.*;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** One guarded native mechanism scene. Fixtures never claim ordinary city settlement or client QA. */
public final class PhantomLivingPresence096Native
{
	private static final long SEED = 96_009_601L;
	private static final Path ROOT = PhantomTask096NativeLane.root();
	private static void check(boolean valid, String reason) { PhantomTask096NativeLane.require(valid, reason); }
	private static void receipt(String name, String value) throws Exception { Files.writeString(ROOT.resolve(name), value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
	private static void await(BooleanSupplier condition, long millis, String reason) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		boolean completed = condition.getAsBoolean();
		while (!completed && System.nanoTime() < deadline) { Thread.sleep(20); completed = condition.getAsBoolean(); }
		check(completed, reason);
	}
	private static PhantomTopologyPoint savedPoint(int objectId) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT x,y,z FROM characters WHERE charId=?"))
		{
			query.setInt(1, objectId);
			try (var rows = query.executeQuery()) { check(rows.next(), "SAVED_CHARACTER"); return new PhantomTopologyPoint(rows.getInt(1), rows.getInt(2), rows.getInt(3), 0); }
		}
	}
	private static PhantomTopologyPoint anchor(PhantomBackgroundSuite.ProductionAuthorityFixture source, String id)
	{
		final var point = source.topology().findAnchor(id).orElseThrow().point();
		check(GeoEngine.getInstance().hasGeo(point.x(), point.y()), "ANCHOR_GEO");
		return new PhantomTopologyPoint(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()), point.instanceId());
	}
	private static void prepareFixture(int objectId, PhantomTopologyPoint point) throws Exception
	{
		check(!Files.exists(ROOT.resolve("measurement.begun")), "FIXTURE_BEFORE_MEASUREMENT_ONLY");
		final Player player = Player.load(objectId);
		check(player != null && World.getInstance().getPlayer(objectId) == null, "COLD_FIXTURE");
		try { player.setXYZInvisible(point.x(), point.y(), point.z()); player.storeMe(); }
		finally { new PhantomHeadlessPlayerTestEnvironment().cleanupLoadedPlayer(player); }
		check(savedPoint(objectId).equals(point), "FIXTURE_STORE_POSITION");
	}
	private static Map<Long, PhantomTopologyPoint> livePoints(PhantomMaterializationService service)
	{
		final var result = new HashMap<Long, PhantomTopologyPoint>();
		for (var entry : service.list())
		{
			final Player player = World.getInstance().getPlayer(entry.characterObjectId());
			if (entry.worldPresent() && player != null) { result.put(entry.profileId(), new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId())); }
		}
		return Map.copyOf(result);
	}
	private static boolean nativeOwner(PhantomMaterializationService service, long id)
	{
		final var entry = service.find(id).orElse(null);
		if (entry == null || !entry.worldPresent() || !entry.actionAdmissionOpen()) { return false; }
		final Player player = World.getInstance().getPlayer(entry.characterObjectId());
		final var owner = player == null ? null : player.getNativeWorkOwner();
		return owner != null && owner.isCurrent() && owner.epoch() == entry.materializedAtNanos();
	}
	private static void worldReceipt(String name, PhantomMaterializationService service, long id, PhantomTopologyPoint observer, long start) throws Exception
	{
		check(nativeOwner(service, id), "NATIVE_WORLD_IDENTITY");
		final var entry = service.find(id).orElseThrow();
		check(entry.profileId() == id, "MATERIALIZED_PROFILE_IDENTITY");
		final Player player = World.getInstance().getPlayer(entry.characterObjectId());
		final var point = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		check(PhantomNativeLocalityEnvelope.couldKnow(observer, point), "NATIVE_GEOMETRY");
		receipt(name, "source=FIXTURE_PREPARED\nobserver=OBSERVER_PROXY\nprofileId=" + id + "\nobjectId=" + player.getObjectId() + "\nepoch=" + entry.materializedAtNanos() + "\npoint=" + point + "\nWorldIdentity=true\ngeometryCouldKnow=true\nknownList=NOT_RUN\nlatencyMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "\n");
		System.out.println("TASK096N_WORLD profile=" + id + " object=" + player.getObjectId() + " epoch=" + entry.materializedAtNanos() + " point=" + point + " source=FIXTURE_PREPARED");
	}

	private static void run(Path module) throws Exception
	{
		PhantomTask096NativeLane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		receipt("native.begun", "pid=" + ProcessHandle.current().pid() + "\ncwd=" + Path.of("").toRealPath() + "\nseed=" + SEED + "\n");
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(SEED, module, ROOT.resolve("reports")));
		try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var repository = PhantomProfileRepository.open();
			final var populationCatalog = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), ZoneOffset.UTC);
			final var store = new PhantomPopulationStore(repository, populationCatalog, ZoneOffset.UTC);
			check(store.loadManagedAfter(0, 32).isEmpty(), "FRESH_POPULATION_REQUIRED");
			final Instant now = Instant.now();
			final var cohort = new HashMap<Long, ManagedSnapshot>();
			final var census = new StringBuilder("profile\tobject\tstate\tcalendar\tregion\tsavedPosition\tpopulationRevision\n");
			final var giran = anchor(source, "giran.city.center");
			final var gludin = anchor(source, "gludin.recovery.anchor");
			int giranResidents = 0, gludinResidents = 0;
			for (int ordinal = 1; ordinal <= 24; ordinal++)
			{
				var current = store.createShell(1, ordinal, SEED);
				for (int step = 0; step < 8 && current.state().state() != PhantomPopulationState.State.READY; step++) { current = store.advanceCreation(current).snapshot(); }
				check(current.state().state() == PhantomPopulationState.State.READY, "ORDINARY_CREATION_NOT_READY");
				final var point = savedPoint(current.profile().characterObjectId());
				final var calendar = populationCatalog.evaluate(current.state().scheduleTemplate(), now, ZoneOffset.UTC, current.state().schedulePhaseMinutes()).state();
				if (calendar == PhantomActivityState.ACTIVE && PhantomNativeLocalityEnvelope.couldKnow(giran, point)) { giranResidents++; }
				if (calendar == PhantomActivityState.ACTIVE && PhantomNativeLocalityEnvelope.couldKnow(gludin, point)) { gludinResidents++; }
				census.append(current.profile().profileId()).append('\t').append(current.profile().characterObjectId()).append('\t').append(current.state().state()).append('\t').append(calendar).append('\t').append(MapRegionData.getInstance().getMapRegionLocId(point.x(), point.y())).append('\t').append(point).append('\t').append(current.component().rowVersion()).append('\n');
				cohort.put(current.profile().profileId(), current);
			}
			receipt("ordinary-census.tsv", census + "ordinaryGiran=" + giranResidents + "\nordinaryGludin=" + gludinResidents + "\npositionRevision=UNPUBLISHED\n");
			System.out.println("TASK096N_ORDINARY_CENSUS ready=" + cohort.size() + " Giran=" + giranResidents + " Gludin=" + gludinResidents);
			final var selected = cohort.values().stream().filter(value -> populationCatalog.evaluate(value.state().scheduleTemplate(), now, ZoneOffset.UTC, value.state().schedulePhaseMinutes()).state() == PhantomActivityState.ACTIVE).sorted(java.util.Comparator.comparingLong(value -> value.profile().profileId())).limit(2).toList();
			check(selected.size() == 2, "INSUFFICIENT_ELIGIBLE_FIXTURES");
			final long oldId = selected.get(0).profile().profileId(), newId = selected.get(1).profile().profileId();
			prepareFixture(selected.get(0).profile().characterObjectId(), gludin); prepareFixture(selected.get(1).profile().characterObjectId(), giran);
			receipt("fixtures.prepared", "source=FIXTURE_PREPARED\nold=" + oldId + "\nnew=" + newId + "\nordinaryCityPass=false\n");
			scene(module, source, repository, populationCatalog, store, cohort, oldId, newId, gludin, giran, now);
		}
	}

	private static void scene(Path module, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository repository, PhantomPopulationCatalog catalog, PhantomPopulationStore store, Map<Long, ManagedSnapshot> cohort, long oldId, long newId, PhantomTopologyPoint gludin, PhantomTopologyPoint giran, Instant now) throws Exception
	{
		final var metrics = new PhantomMetrics(); final var trace = new PhantomDiagnosticTrace(false, 64, 16, metrics);
		final var serviceRef = new AtomicReference<PhantomMaterializationService>();
		final var lifecycle = new PhantomMaterializationLifecycleBridge();
		final var goals = new PhantomGoalStateStore(repository);
		final var background = new PhantomBackgroundService(repository, goals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), source.authority(), new PhantomBackgroundCompetitionRegistry(), noSignals(), serviceRef::get);
		final var service = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, 1, 0, _ -> {}, lifecycle, 5000, 10000); serviceRef.set(service);
		final var historical = new PhantomHistoricalBackgroundService(repository, goals, new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority()), background, service);
		lifecycle.install(PhantomMaterializationLifecyclePort.chain(historical, background));
		final var ecologyCatalog = PhantomPopulationEcologyCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), catalog, PhantomSocialCatalog.load(module.resolve("dist/game/data/phantoms/social/high-five-social-v1.xml")));
		final var ecologyStore = new PhantomPopulationEcologyStore(repository);
		final long minute = now.toEpochMilli() / 60000;
		for (var value : cohort.values())
		{
			final long id = value.profile().profileId(); final var assignment = ecologyCatalog.assign(Preset.LIVING, 1, id, SEED, minute, 0, 0, value.state().scheduleTemplate());
			ecologyStore.insert(id, new PhantomPopulationEcologyState(assignment.catalogHash(), assignment.preset(), assignment.ecologyGeneration(), assignment.assignmentOrdinal(), minute, minute, minute, minute, assignment.pace(), assignment.productiveShareBasisPoints(), assignment.productiveBlockMinutes(), assignment.personality(), assignment.initialSocialTraits(), value.state().scheduleTemplate(), assignment.disposition(), minute + 10000, 0, "", 0, 0, 0, ""));
		}
		final var ecology = new PhantomPopulationEcologyService(ecologyCatalog, catalog, ecologyStore, historical, id -> service.find(id).isPresent(), _ -> "", Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC, Preset.LIVING, 0, 24);
		ecology.enablePeriodicDueMode();
		ecology.installRuntime(id -> Optional.ofNullable(cohort.get(id)), new PhantomPopulationEcologyService.PopulationEvents() {
			@Override public void requestArchive(long id) { throw new AssertionError("TASK096N_RETIREMENT_FORBIDDEN"); }
			@Override public void reconcilePopulation() { }
			@Override public void ecologyFenceChanged(long id) { }
		});
		final var population = new PhantomPopulationManager(store, catalog, null, new PhantomPopulationTestDoubles.Ownership(), Clock.systemUTC(), ZoneOffset.UTC, 24, 1, 32, 1, 4, 64);
		final var topology = PhantomTopologyService.fromSnapshotForTesting(source.topology().snapshot(), source.topologyBackend(), PhantomTopologyPolicy.productionDefaults(), noSignals());
		final var observers = new AtomicReference<>(List.of(gludin));
		final var schedulerRef = new AtomicReference<PhantomScheduler>();
		final var signalPort = new PhantomRelevanceSignalPort() {
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return new PhantomSchedulerRelevanceSignalPort(schedulerRef.get()).submit(id, signal); }
			@Override public SignalDelivery withdraw(long id, String sourceKey, long sequence) { return new PhantomSchedulerRelevanceSignalPort(schedulerRef.get()).withdraw(id, sourceKey, sequence); }
		};
		final var locality = new PhantomHumanLocalityControl(topology, signalPort, observers::get, () -> System.nanoTime() / 1000000, population.presence()::isOnline, () -> livePoints(service));
		final var retention = new PhantomMaterializationRetentionPolicy(id -> {
			final var entry = service.find(id).orElse(null); final Player player = entry == null ? null : World.getInstance().getPlayer(entry.characterObjectId());
			final var owner = player == null ? null : player.getNativeWorkOwner();
			return player == null || !entry.worldPresent() || owner == null || !owner.isCurrent() || owner.epoch() != entry.materializedAtNanos() ? new PhantomMaterializationRetentionPolicy.Facts(false, false, false, false, false) : PhantomMaterializationRetentionPolicy.Facts.nativeFacts(player, locality.isLocal(id), locality.isNativeVisible(id), entry.admittedActionCount());
		}, System::nanoTime, 60000);
		service.installSoftCleanupAdmission(id -> retention.recentHumanObservation(id) == PhantomMaterializationRetentionPolicy.HumanObservation.CLEAR && retention.observe(id).reclaimable() && !locality.isCurrentLocal(id));
		locality.installPreparationDemand((facts, slots) -> { population.updateMaterializationDemand(facts); ecology.updateMaterializationDemand(facts.stream().filter(fact -> population.admissionProfile(fact.profileId()).map(value -> value.admitted()).orElse(false)).toList(), slots); }, () -> service.snapshot().availablePermits() + (int) service.list().stream().filter(value -> retention.observe(value.profileId()).reclaimable()).count());
		ecology.installMaterializationDemand(locality::isCurrentLocal);
		final var boundary = new PhantomMaterializationServiceActivityPort(service, true); final var gate = new PhantomReconcileFirstActivityPort(boundary);
		gate.installPopulationReadiness(population.presence(), locality, ecology, _ -> false, id -> population.admissionProfile(id).map(value -> value.admitted()).orElse(false));
		gate.installRetention(id -> schedulerRef.get().snapshot().state() == PhantomScheduler.SchedulerState.RUNNING && retention.observe(id).retained());
		gate.installSoftReclamationEpochs(requester -> {
			if (!locality.isCurrentLocal(requester) || service.snapshot().availablePermits() != 0) { return PhantomActivityMaterializationPort.SoftReclaimSelection.none(); }
			final long selected = retention.oldestSoft(service.list().stream().filter(value -> value.worldPresent()).map(value -> value.profileId()).toList());
			return service.find(selected).map(value -> new PhantomActivityMaterializationPort.SoftReclaimSelection(selected, value.materializedAtNanos())).orElseGet(PhantomActivityMaterializationPort.SoftReclaimSelection::none);
		}, id -> retention.observe(id).reclaimable(), locality::isCurrentLocal);
		final var scheduler = new PhantomScheduler(32, 100, 32, metrics, trace, gate, _ -> {}); schedulerRef.set(scheduler);
		scheduler.installLocalControlPort(() -> { locality.onPulse(); population.onPulse(); ecology.onPopulationPulse(); retention.refresh(service.list().stream().filter(value -> value.worldPresent()).map(value -> value.profileId()).toList()); });
		Throwable primaryFailure = null;
		try
		{
			check(background.start() && service.start() && topology.start() && population.start(), "SCENE_START");
			for (var entry : Map.of(oldId, gludin, newId, giran).entrySet()) { topology.registerProfile(entry.getKey()); topology.updateProfile(entry.getKey(), entry.getValue(), 1); }
			cohort.values().forEach(ecology::register);
			for (int i = 0; i < 32; i++) { population.onPulse(); ecology.onPopulationPulse(); }
			receipt("measurement.begun", "fixtureWritesAfterThis=0\nobserver=OBSERVER_PROXY\npopulationOwnership=TEST_ADAPTER\necologicalClock=FIXED_CURRENT_CURSOR\n");
			final long gludinStart = System.nanoTime();
			check(scheduler.start(), "SCHEDULER_START"); scheduler.register(oldId); scheduler.register(newId);
			await(() -> nativeOwner(service, oldId), 15000, "GLUDIN_WORLD_NOT_PROVEN");
			worldReceipt("gludin.world", service, oldId, gludin, gludinStart);
			final var old = service.find(oldId).orElseThrow();
			check(boundary.reclaimSoft(oldId, old.materializedAtNanos() - 1).outcome() == PhantomActivityMaterializationPort.Outcome.DEFERRED, "WRONG_EPOCH_NOT_REJECTED");
			observers.set(List.of(gludin, giran));
			await(() -> locality.isCurrentLocal(newId), 4000, "SECOND_TOWN_DEMAND");
			check(nativeOwner(service, oldId) && service.snapshot().occupiedSlots() == 1, "VISIBLE_OWNER_LOST");
			final long giranStart = System.nanoTime();
			try (var action = service.tryAcquireAction(oldId).orElseThrow())
			{
				observers.set(List.of(giran));
				await(() -> !locality.isLocal(oldId) && population.admissionProfile(newId).map(value -> value.admitted()).orElse(false), 35000, "INCOMING_ADMISSION");
				check(!retention.observe(oldId).reclaimable() && nativeOwner(service, oldId), "RECENT_HUMAN_OR_ACTION_HOLD_LOST");
				receipt("holds.negative", "recentHuman60s=RETAINED\nadmittedAction=RETAINED\nwrongEpoch=DEFERRED\nbothTownDemand=PROTECTED_DEFERRED\n");
				await(() -> retention.recentHumanObservation(oldId) == PhantomMaterializationRetentionPolicy.HumanObservation.CLEAR, 65000, "REAL_RECENT_GRACE_TIMEOUT");
				check(nativeOwner(service, oldId), "ADMITTED_ACTION_EVICTED");
			}
			await(() -> nativeOwner(service, newId) && service.find(oldId).isEmpty(), 15000, "CAP_PRESSURE_NOT_PROVEN");
			worldReceipt("giran.world", service, newId, giran, giranStart);
			check(service.snapshot().occupiedSlots() == 1 && World.getInstance().getPlayer(old.characterObjectId()) == null, "CAP_EXCHANGE_IDENTITY");
			receipt("measurement.completed", "FIXTURE_WORLD_PASS=true\ncap1Exchange=true\nordinaryCityPass=false\nclientQA=DEFERRED\nfairness=PROTECTED_DEFERRED_ONLY\n");
		}
		catch (Exception | Error failure) { primaryFailure = failure; throw failure; }
		finally
		{
			Throwable cleanup = cleanup(null, () -> { scheduler.beginStop(); population.beginStop(); });
			cleanup = cleanup(cleanup, () -> check(service.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "STORE_RETAINED"));
			cleanup = cleanup(cleanup, () -> await(scheduler::finishStop, 5000, "SCHEDULER_DRAIN"));
			cleanup = cleanup(cleanup, () -> { ecology.beginStop(); await(ecology::finishStop, 5000, "ECOLOGY_DRAIN"); });
			cleanup = cleanup(cleanup, () -> { background.beginStop(); check(background.finishStop(), "BACKGROUND_DRAIN"); });
			cleanup = cleanup(cleanup, () -> { topology.beginStop(); check(topology.finishStop() && population.finishStop(), "CONTROL_DRAIN"); });
			cleanup = cleanup(cleanup, () -> check(service.snapshot().occupiedSlots() == 0 && World.getInstance().getPlayer(cohort.get(oldId).profile().characterObjectId()) == null && World.getInstance().getPlayer(cohort.get(newId).profile().characterObjectId()) == null, "WORLD_RESIDUE"));
			if (cleanup == null) { receipt("store.completed", "nativePlayers=0\noccupiedSlots=0\nnormalStore=true\nprofilesRetained=24\n"); }
			else if (primaryFailure != null) { primaryFailure.addSuppressed(cleanup); }
			else if (cleanup instanceof Exception exception) { throw exception; }
			else { throw (Error) cleanup; }
		}
	}
	@FunctionalInterface private interface Cleanup { void run() throws Exception; }
	private static Throwable cleanup(Throwable first, Cleanup step)
	{
		try { step.run(); }
		catch (Exception | Error failure) { if (first == null) { return failure; } first.addSuppressed(failure); }
		return first;
	}
	private static PhantomRelevanceSignalPort noSignals()
	{
		return new PhantomRelevanceSignalPort() {
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			if (args.length == 1 && args[0].equals("guards"))
			{
				final var calls = new java.util.concurrent.atomic.AtomicInteger();
				await(() -> calls.incrementAndGet() == 1, 100, "ONE_SHOT_AWAIT");
				check(calls.get() == 1, "ONE_SHOT_AWAIT_REPEATED");
				System.out.println("TASK096N_ONE_SHOT_AWAIT_PASS callbacks=1 DB=NOT_OPENED World=NOT_STARTED");
			}
			else { check(args.length == 0, "ACTION"); run(Path.of(System.getProperty("phantom.module.root"))); }
			exit = 0;
		}
		catch (Throwable failure)
		{
			final String message = failure.getMessage();
			System.out.println("TASK096N_NATIVE_FAIL type=" + failure.getClass().getName() + " key=" + (message != null && message.matches("TASK096N_[A-Z_]+") ? message : "WITHHELD"));
			for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("TASK096N_FRAME " + frame); } }
		}
		finally { if (DatabaseFactory.isInitialized()) { try { ThreadPool.shutdown(); } finally { DatabaseFactory.close(); } } }
		System.exit(exit);
	}
}
