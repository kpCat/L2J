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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Player.OutboundSessionAttachment;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.ConfiguredShutdownSnapshot;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomServerShutdownHandoffSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final Set<Long> _ownedProfileIds = ConcurrentHashMap.newKeySet();
	private final List<PhantomMaterializationService> _services = new ArrayList<>();
	private PhantomProfileRepository _repository;

	@Override
	public String id()
	{
		return "server-shutdown-handoff";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_repository = PhantomProfileRepository.open();
		context.record("serverShutdownHandoff.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			reset();
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
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

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if ("contract024".equals(System.getProperty("phantom.m1.native.focus")))
		{
			registry.add("C01-real-topology-post-store-before-dependency-stop", this::testTopologyPostStore024);
			registry.add("C03-in-flight-drain-keeps-topology-running", this::testInFlightDrain);
			registry.add("C03-store-callback-inspects-configured-system-without-monitor-cycle", this::testStoreMonitor024);
			return;
		}
		if ("ecology".equals(System.getProperty("phantom.m1.native.focus"))) { registry.add("08-configured-stop-awaits-original-ecology-worker-finally", this::testEcologyDrain); return; }
		registry.add("01-managed-classifier-fails-closed", _ -> testManagedClassifier());
		registry.add("02-two-phase-server-policy-and-source-order", this::testTwoPhasePolicy);
		registry.add("03-in-flight-drain-reused-before-thread-pool-phase", this::testInFlightDrain);
		registry.add("04-persistent-failure-retains-configured-ownership", _ -> testPersistentFailure());
		registry.add("05-in-flight-scheduler-pulse-retains-configured-system", _ -> testInFlightSchedulerPulse());
		registry.add("06-navigation-only-blocker-snapshot", _ -> testNavigationOnlyBlockerSnapshot());
		registry.add("07-final-diagnostic-includes-navigation-state", this::testFinalDiagnosticNavigationState);
		registry.add("08-configured-stop-awaits-original-ecology-worker-finally", this::testEcologyDrain);
	}

	/** Single control advance permits the fixture to release its accepted store/pulse next. */
	private static boolean requestConfiguredStop() throws Exception
	{
		final var field = PhantomSystem.class.getDeclaredField("_configuredInstance"); field.setAccessible(true);
		return ((PhantomSystem) field.get(null)).shutdown();
	}

	private void testEcologyDrain(PhantomTestContext context) throws Exception
	{
		reset();
		final var population = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), java.time.ZoneOffset.UTC);
		final var social = org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		final var catalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), population, social);
		final var metadata = new org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore(population.hash()).seedReady(1, 1);
		final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1); final var finished = new CountDownLatch(1);
		final var ecology = new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService(catalog, population,
			new org.l2jmobius.tests.phantoms.PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore(null), new org.l2jmobius.tests.phantoms.PhantomPopulationEcologyGoal033Suite.HistoricalMemoryPort(),
			id -> false, id -> "", java.time.Clock.systemUTC(), java.time.ZoneOffset.UTC, org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset.LIVING, 0, 1,
			worker -> { org.l2jmobius.commons.threads.ThreadPool.execute(() -> { try { worker.run(); } finally { finished.countDown(); } }); return true; });
		ecology.installMetadataObserver(observation ->
		{
			entered.countDown();
			try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("TEST023_METADATA_RELEASE_TIMEOUT"); } }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
		});
		ecology.installRuntime(id -> id == 1 ? java.util.Optional.of(metadata) : java.util.Optional.empty(), new org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.PopulationEvents()
		{
			@Override public void requestArchive(long id) { }
			@Override public void reconcilePopulation() { }
			@Override public void ecologyFenceChanged(long id) { }
		});
		PhantomSystem.configureForTesting(service(1, PhantomMaterializedPlayer.FailureInjector.none(), 150));
		final var configuredField = PhantomSystem.class.getDeclaredField("_configuredInstance"); configuredField.setAccessible(true);
		final var configured = configuredField.get(null); final var ecologyField = PhantomSystem.class.getDeclaredField("_populationEcology"); ecologyField.setAccessible(true); ecologyField.set(configured, ecology);
		try
		{
			ecology.register(metadata); ecology.onPopulationPulse();
			PhantomAssertions.assertTrue(entered.await(3, TimeUnit.SECONDS), "INVALID: original ecology metadata worker did not enter its native finally boundary.");
			org.l2jmobius.commons.threads.ThreadPool.schedule(release::countDown, 100);
			final boolean stopped = PhantomSystem.shutdownIfStarted();
			context.record("TASK023.ecologyDrain", "stopped=" + stopped + ";finished=" + (finished.getCount() == 0) + ";configured=" + PhantomSystem.hasConfiguredInstance());
			PhantomAssertions.assertTrue(stopped && finished.getCount() == 0 && !PhantomSystem.hasConfiguredInstance(), "RED: stock shutdown must await the already running ecology worker before the shared pool phase.");
		}
		finally
		{
			release.countDown(); PhantomAssertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "Exact TEST ecology worker retained at teardown.");
			if (PhantomSystem.hasConfiguredInstance()) { PhantomSystem.shutdownIfStarted(); }
		}
	}

	private void testManagedClassifier() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final PhantomMaterializationService service = service(1, PhantomMaterializedPlayer.FailureInjector.none(), 150);
		PhantomSystem.configureForTesting(service);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "Configured production actor did not materialize.");
		final Player managed = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(_environment.primary().objectId());
		PhantomAssertions.assertTrue(PhantomSystem.isMaterializationManaged(managed), "Active configured Phantom Player was not classified as managed.");
		PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(null), "Null Player was classified as managed.");

		final Player ordinary = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(ordinary != null, "Could not load ordinary classifier fixture.");
		OutboundSessionAttachment attachment = null;
		Lease unownedCharacterLease = null;
		try
		{
			PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(ordinary), "Ordinary loaded Player was classified as managed.");
			ordinary.setOfflinePlay(true);
			PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(ordinary), "Detached offline real Player was classified as managed.");

			attachment = ordinary.attachOutboundSession(new HeadlessPlayerOutboundSession(4, 16));
			PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(ordinary), "Unowned headless Player was classified as managed.");

			unownedCharacterLease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(ordinary.getObjectId(), OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(unownedCharacterLease != null, "Could not create an unowned-character PHANTOM lease.");
			PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(ordinary), "PHANTOM lease without configured service ownership was classified as managed.");
			PhantomAssertions.assertTrue(selectedForGenericDisconnect(ordinary), "Fail-closed classifier removed an unowned Player from generic disconnect.");
		}
		finally
		{
			if (unownedCharacterLease != null)
			{
				unownedCharacterLease.close();
			}
			if (attachment != null)
			{
				attachment.close();
			}
			ordinary.setOfflinePlay(false);
			_environment.cleanupLoadedPlayer(ordinary);
		}

		PhantomAssertions.assertFalse(selectedForGenericDisconnect(managed), "Managed Phantom Player was selected for generic disconnect.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Configured classifier service did not stop.");
		PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(managed), "Cleaned Phantom Player remained classified as managed.");
		_environment.assertClean(_environment.primary(), managed);
	}

	private void testTwoPhasePolicy(PhantomTestContext context) throws Exception
	{
		reset();
		final var sourcePath = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/Shutdown.java");
		final String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
		final String shutdownCall = "PhantomSystem.shutdownIfStarted()";
		final int firstShutdown = source.indexOf(shutdownCall);
		final int disconnect = source.indexOf("disconnectAllCharacters();", firstShutdown + shutdownCall.length());
		final int secondShutdown = source.indexOf(shutdownCall, firstShutdown + shutdownCall.length());
		final int threadPool = source.indexOf("ThreadPool.shutdown();", secondShutdown + shutdownCall.length());
		PhantomAssertions.assertTrue((firstShutdown >= 0) && (firstShutdown < disconnect) && (disconnect < secondShutdown) && (secondShutdown < threadPool), "Actual Shutdown source does not preserve first-drain/disconnect/second-drain/ThreadPool order.");
		PhantomAssertions.assertEquals(-1, source.indexOf(shutdownCall, secondShutdown + shutdownCall.length()), "Actual Shutdown source contains more than two server-level Phantom shutdown calls.");

		final int loopGuard = source.indexOf("PhantomSystem.isMaterializationManaged(player)");
		final int disconnection = source.indexOf("Disconnection.of(player)", loopGuard);
		PhantomAssertions.assertTrue((loopGuard >= 0) && (loopGuard < disconnection), "Generic disconnect loop does not guard managed Players before Disconnection.");
		PhantomAssertions.assertTrue(source.substring(secondShutdown, threadPool).contains("LOGGER.severe"), "Persistent final failure has no severe diagnostic before ThreadPool shutdown.");
		PhantomAssertions.assertFalse(source.contains("Skeleton has been shut down"), "Legacy success wording remains reachable.");

		final List<String> recoveredEvents = new ArrayList<>();
		final PolicyResult recovered = simulateServerPolicy(recoveredEvents, false, true);
		PhantomAssertions.assertEquals(List.of("first", "disconnect", "second", "thread-pool"), recoveredEvents, "Two-phase recovery policy order is wrong.");
		PhantomAssertions.assertEquals(2, recovered.shutdownCalls(), "Two-phase recovery did not use exactly two opportunities.");
		PhantomAssertions.assertTrue(recovered.successful(), "Terminal second attempt was not reported as successful.");

		final List<String> failedEvents = new ArrayList<>();
		final PolicyResult failed = simulateServerPolicy(failedEvents, false, false);
		PhantomAssertions.assertEquals(List.of("first", "disconnect", "second", "thread-pool"), failedEvents, "Persistent-failure policy order is wrong.");
		PhantomAssertions.assertEquals(2, failed.shutdownCalls(), "Persistent failure exceeded or skipped the two server-level opportunities.");
		PhantomAssertions.assertFalse(failed.successful(), "Persistent failure was reported as successful.");
	}

	private void testInFlightDrain(PhantomTestContext context) throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final CountDownLatch storeEntered = new CountDownLatch(1);
		final CountDownLatch releaseStore = new CountDownLatch(1);
		final AtomicInteger cleanupInvocations = new AtomicInteger();
		final PhantomMaterializationService service = service(1, point ->
		{
			if (point == FailurePoint.BEFORE_STORE_OPERATION)
			{
				cleanupInvocations.incrementAndGet();
				storeEntered.countDown();
				try
				{
					if (!releaseStore.await(5, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to release the blocked shutdown store.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while blocking the shutdown store.", e);
				}
			}
		}, 150);
		PhantomSystem.configureForTesting(service);
		final PhantomScheduler scheduler = PhantomSystem.configuredScheduler();
		PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, scheduler.register(profile.profileId()).status(), "In-flight handoff scheduler profile was not registered.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "In-flight handoff actor did not materialize.");
		final Player managed = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(_environment.primary().objectId());

		try
		{
			final long started = System.nanoTime();
			PhantomAssertions.assertFalse(requestConfiguredStop(), "Blocked stop advance unexpectedly completed.");
			final long elapsed = System.nanoTime() - started;
			context.record("serverShutdownHandoff.blockedFirstElapsedNanos", elapsed);
			PhantomAssertions.assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "First server shutdown exceeded its wall-clock gate: " + elapsed);
			PhantomAssertions.assertTrue(storeEntered.await(1, TimeUnit.SECONDS), "Tracked drain did not enter the blocked store operation.");
			final ConfiguredShutdownSnapshot retained = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(retained.configured(), "First timeout cleared the configured instance.");
			PhantomAssertions.assertEquals(1, retained.retainedMaterializationEntries(), "First timeout did not retain the managed entry.");
			PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPING, scheduler.snapshot().state(), "Failed first drain did not retain scheduler STOPPING.");
			PhantomAssertions.assertEquals(1, scheduler.snapshot().registered(), "Failed first drain cleared retained scheduler slots.");
			PhantomAssertions.assertEquals(0, scheduler.snapshot().scheduledTaskCount(), "Failed first drain retained the recurring scheduler future.");
			PhantomAssertions.assertEquals(PhantomTopologyService.State.RUNNING, retained.topologyState(), "C03 topology must remain RUNNING while materialization/store still uses it.");
			PhantomAssertions.assertEquals(0, retained.topologyRegisteredProfiles(), "Inert shutdown topology discovered profiles.");
			PhantomAssertions.assertEquals(0, retained.topologyEventsInFlight(), "Inert shutdown topology created events.");
			PhantomAssertions.assertTrue(PhantomSystem.isMaterializationManaged(managed), "First timeout lost managed classification.");
			PhantomAssertions.assertEquals(0, simulatedGenericDisconnections(List.of(managed)), "Generic disconnect selected the in-flight managed actor.");
		}
		finally
		{
			releaseStore.countDown();
		}

		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Second server shutdown did not observe/reuse the released in-flight drain.");
		PhantomAssertions.assertEquals(1, cleanupInvocations.get(), "Second server shutdown duplicated in-flight cleanup.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Terminal second shutdown retained the configured instance.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Terminal second shutdown did not finish the scheduler.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Terminal second shutdown retained scheduler slots.");
		PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(managed), "Terminal second shutdown retained managed classification.");
		PhantomAssertions.assertEquals(new ConfiguredShutdownSnapshot(false, null, null, 0, null, 0, 0, 0, null, 0, 0, 0, null, null, 0, 0, 0, 0, 0), PhantomSystem.configuredShutdownSnapshot(), "Absent configured snapshot is not bounded/empty.");
		_environment.assertClean(_environment.primary(), managed);
	}

	private void testPersistentFailure() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final AtomicBoolean fault = new AtomicBoolean(true);
		final AtomicInteger cleanupInvocations = new AtomicInteger();
		final PhantomMaterializationService service = service(1, point ->
		{
			if ((point == FailurePoint.BEFORE_STORE_OPERATION) && fault.get())
			{
				cleanupInvocations.incrementAndGet();
				throw new PersistentFailure();
			}
		}, 150);
		PhantomSystem.configureForTesting(service);
		final PhantomScheduler scheduler = PhantomSystem.configuredScheduler();
		PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, scheduler.register(profile.profileId()).status(), "Persistent-failure scheduler profile was not registered.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "Persistent-failure actor did not materialize.");
		final Player managed = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(_environment.primary().objectId());

		try
		{
			PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "First persistent server shutdown reported success.");
			PhantomAssertions.assertTrue(PhantomSystem.isMaterializationManaged(managed), "First persistent failure lost managed classification.");
			PhantomAssertions.assertEquals(0, simulatedGenericDisconnections(List.of(managed)), "Generic disconnect selected the persistently retained actor.");
			PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Second persistent server shutdown reported success.");

			final ConfiguredShutdownSnapshot retained = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(retained.configured(), "Persistent failure cleared the configured instance.");
			PhantomAssertions.assertEquals(PhantomSystem.State.FAILED, retained.systemState(), "Persistent failure lost system FAILED state.");
			PhantomAssertions.assertEquals(ServiceState.FAILED, retained.materializationServiceState(), "Persistent failure lost service FAILED state.");
			PhantomAssertions.assertEquals(1, retained.retainedMaterializationEntries(), "Persistent failure released the service entry.");
			PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPING, scheduler.snapshot().state(), "Persistent service failure did not retain scheduler STOPPING.");
			PhantomAssertions.assertEquals(1, scheduler.snapshot().registered(), "Persistent service failure cleared scheduler slots.");
			PhantomAssertions.assertEquals(PhantomTopologyService.State.RUNNING, retained.topologyState(), "C05 retained materialization still needs its topology dependency.");
			PhantomAssertions.assertEquals(0, retained.topologyRegisteredProfiles(), "Persistent service failure topology discovered profiles.");
			PhantomAssertions.assertEquals(0, retained.topologyEventsInFlight(), "Persistent service failure topology created events.");
			PhantomAssertions.assertTrue(PhantomSystem.isMaterializationManaged(managed), "Persistent failure lost fail-closed ownership.");
			PhantomAssertions.assertEquals(3, cleanupInvocations.get(), "One original control attempt plus exact legacy two-pass failure cleanup must remain bounded.");
		}
		finally
		{
			fault.set(false);
		}

		PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Failure cleanup must not turn the original FAILED stop into healthy COMPLETE.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Explicit teardown cleanup retained the configured instance.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Successful explicit teardown did not finish the scheduler.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Successful explicit teardown retained scheduler slots.");
		_environment.assertClean(_environment.primary(), managed);
	}

	private void testInFlightSchedulerPulse() throws Exception
	{
		reset();
		final PhantomMaterializationService service = service(1, PhantomMaterializedPlayer.FailureInjector.none(), 150);
		final CountDownLatch workEntered = new CountDownLatch(1);
		final CountDownLatch releaseWork = new CountDownLatch(1);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomScheduler scheduler = new PhantomScheduler(
			2,
			10,
			2,
			new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50),
			System::nanoTime,
			(pulse, period) -> null,
			false,
			metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics),
			PhantomActivityMaterializationPort.noop(),
			item ->
			{
				workEntered.countDown();
				try
				{
					if (!releaseWork.await(2, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to release the scheduler work sink.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while blocking the scheduler work sink.", e);
				}
			});
		PhantomAssertions.assertTrue(scheduler.start(), "In-flight shutdown scheduler did not start.");
		PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, scheduler.register(1).status(), "In-flight shutdown profile was not registered.");
		PhantomAssertions.assertEquals(PhantomScheduler.SignalStatus.ACCEPTED, scheduler.submitSignal(1, new PhantomRelevanceSignal("shutdown.work", 1, PhantomActivityState.WARM, 1000)).status(), "In-flight shutdown work signal was not accepted.");
		PhantomSystem.configureForTesting(service, scheduler);
		final Thread pulse = new Thread(scheduler::pulse, "t007a-system-stop-work");
		pulse.start();
		PhantomAssertions.assertTrue(workEntered.await(2, TimeUnit.SECONDS), "In-flight shutdown pulse did not enter the work sink.");
		try
		{
			PhantomAssertions.assertFalse(requestConfiguredStop(), "PhantomSystem reported STOPPED while its scheduler pulse was in flight.");
			final ConfiguredShutdownSnapshot retained = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(retained.configured(), "In-flight scheduler pulse cleared the configured instance.");
			PhantomAssertions.assertEquals(PhantomSystem.State.STOPPING, retained.systemState(), "Accepted scheduler work must retain PENDING stop state.");
			PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPING, scheduler.snapshot().state(), "In-flight scheduler pulse did not retain STOPPING.");
			PhantomAssertions.assertTrue(scheduler.snapshot().pulseInFlight(), "In-flight scheduler pulse marker was cleared by failed finishStop.");
			PhantomAssertions.assertEquals(1, scheduler.snapshot().registered(), "Failed finishStop cleared scheduler slots.");
		}
		finally
		{
			releaseWork.countDown();
			pulse.join(TimeUnit.SECONDS.toMillis(2));
		}
		PhantomAssertions.assertFalse(pulse.isAlive(), "In-flight shutdown pulse did not quiesce.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Explicit shutdown after scheduler quiescence did not finish.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Terminal scheduler shutdown retained the configured instance.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Terminal scheduler shutdown did not reach STOPPED.");
		PhantomAssertions.assertEquals(0, scheduler.snapshot().registered(), "Terminal scheduler shutdown retained slots.");
	}

	private void testNavigationOnlyBlockerSnapshot() throws Exception
	{
		reset();
		final PhantomMaterializationService materializationService = service(1, PhantomMaterializedPlayer.FailureInjector.none(), 150);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomScheduler scheduler = new PhantomScheduler(
			2,
			10,
			2,
			new PhantomSchedulerPolicy(16, 1000, 5, 2, 8, 1, 2, 3, 4, 50),
			System::nanoTime,
			(pulse, period) -> null,
			false,
			metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics),
			PhantomActivityMaterializationPort.noop(),
			item ->
			{
			});
		PhantomAssertions.assertTrue(scheduler.start(), "Navigation-only shutdown scheduler did not start.");

		final PhantomNavigationPoint origin = new PhantomNavigationPoint(10_000, 10_000, 0, 0);
		final PhantomNavigationPoint midpoint = new PhantomNavigationPoint(10_250, 10_100, 0, 0);
		final PhantomNavigationPoint destination = new PhantomNavigationPoint(10_500, 10_000, 0, 0);
		final CountDownLatch pathEntered = new CountDownLatch(1);
		final CountDownLatch releasePath = new CountDownLatch(1);
		final AtomicInteger directCalls = new AtomicInteger();
		final AtomicReference<Thread> workerThread = new AtomicReference<>();
		final PhantomNavigationBackend backend = new PhantomNavigationBackend()
		{
			@Override
			public CapabilitySnapshot capability(PhantomNavigationPoint requestOrigin, PhantomNavigationPoint requestDestination)
			{
				return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
			}

			@Override
			public boolean canMoveDirect(PhantomNavigationPoint requestOrigin, PhantomNavigationPoint requestDestination)
			{
				return directCalls.getAndIncrement() > 0;
			}

			@Override
			public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
			{
				pathEntered.countDown();
				try
				{
					if (!releasePath.await(2, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to release navigation shutdown path.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
				return List.of(origin, midpoint, destination);
			}
		};
		final PhantomNavigationService navigationService = new PhantomNavigationService(
			new PhantomNavigationPolicy(4, 1, 32, 4, 5000, 1000, 12_000, 64, 100_000, 1000, 3000, 20, 50, 120_000),
			backend,
			worker ->
			{
				final Thread thread = new Thread(worker, "phantom-navigation-shutdown-worker");
				workerThread.set(thread);
				thread.start();
				return true;
			},
			() -> 0,
			metrics);
		PhantomAssertions.assertTrue(navigationService.start(), "Navigation-only shutdown service did not start.");
		final var submission = navigationService.submit(new PhantomNavigationRequest(1, origin, destination, 0, 100, 100_000));
		PhantomAssertions.assertTrue(pathEntered.await(1, TimeUnit.SECONDS), "Navigation-only shutdown request did not enter pathfinding.");
		PhantomSystem.configureForTesting(materializationService, scheduler, navigationService);

		try
		{
			PhantomAssertions.assertFalse(requestConfiguredStop(), "Navigation-only blocker was reported as stopped.");
			final ConfiguredShutdownSnapshot snapshot = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(snapshot.configured(), "Navigation-only blocker cleared configured ownership.");
			PhantomAssertions.assertEquals(PhantomSystem.State.STOPPING, snapshot.systemState(), "Accepted navigation work must retain PENDING stop state.");
			PhantomAssertions.assertEquals(ServiceState.STOPPED, snapshot.materializationServiceState(), "Navigation-only blocker left materialization incomplete.");
			PhantomAssertions.assertEquals(0, snapshot.retainedMaterializationEntries(), "Navigation-only blocker retained materialization entries.");
			PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPING, snapshot.navigationState(), "Navigation-only blocker lost navigation STOPPING state.");
			PhantomAssertions.assertEquals(1, snapshot.navigationActiveRequests(), "Navigation-only blocker lost active request ownership.");
			PhantomAssertions.assertEquals(0, snapshot.navigationQueuedRequests(), "Navigation-only blocker retained queued work.");
			PhantomAssertions.assertEquals(1, snapshot.navigationWorkers(), "Navigation-only blocker lost worker ownership.");
			PhantomAssertions.assertEquals(PhantomTopologyService.State.STOPPED, snapshot.topologyState(), "Navigation-only blocker did not finish quiescent topology first.");
			PhantomAssertions.assertEquals(0, snapshot.topologyRegisteredProfiles(), "Navigation-only blocker topology discovered profiles.");
			PhantomAssertions.assertEquals(0, snapshot.topologyEventsInFlight(), "Navigation-only blocker topology retained events.");
		}
		finally
		{
			releasePath.countDown();
			final Thread worker = workerThread.get();
			if (worker != null)
			{
				worker.join(TimeUnit.SECONDS.toMillis(2));
			}
		}
		PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.CANCELLED, navigationService.consume(submission.requestId()).orElseThrow().status(), "Navigation shutdown published a late route.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Navigation-only blocker did not finish after worker return.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Navigation-only blocker retained configured ownership after quiescence.");
	}

	private void testFinalDiagnosticNavigationState(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/Shutdown.java"), StandardCharsets.UTF_8);
		final String shutdownCall = "PhantomSystem.shutdownIfStarted()";
		final int firstShutdown = source.indexOf(shutdownCall);
		final int secondShutdown = source.indexOf(shutdownCall, firstShutdown + shutdownCall.length());
		final int threadPool = source.indexOf("ThreadPool.shutdown();", secondShutdown + shutdownCall.length());
		final String finalDiagnostic = source.substring(secondShutdown, threadPool);
		PhantomAssertions.assertTrue(finalDiagnostic.contains("LOGGER.severe"), "Final persistent Phantom failure is not severe.");
		PhantomAssertions.assertTrue(finalDiagnostic.contains("Final subsystem drain is incomplete"), "Final diagnostic still reports a materialization-only failure.");
		for (String field : List.of("systemState", "materializationServiceState", "retainedMaterializationEntries", "combatState", "combatActiveSessions", "combatTerminalSessions", "combatQueuedSessions", "combatWorkers", "combatActorLeases", "navigationState", "navigationActiveRequests", "navigationQueuedRequests", "navigationWorkers", "topologyState", "topologyRegisteredProfiles", "topologyEventsInFlight", "topologyGeneration"))
		{
			PhantomAssertions.assertTrue(finalDiagnostic.contains(field), "Final Phantom diagnostic omits " + field + ".");
		}
		PhantomAssertions.assertFalse(finalDiagnostic.contains("Final materialization drain completed"), "Final diagnostic can misreport navigation failure as materialization success.");
	}

	private void testTopologyPostStore024(PhantomTestContext context) throws Exception
	{
		reset();
		org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance();
		org.l2jmobius.gameserver.data.xml.SpawnData.getInstance();
		org.l2jmobius.gameserver.data.xml.DoorData.getInstance();
		final var backend = new org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend();
		final var policy = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy.productionDefaults();
		final var signals = new org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var topology = new PhantomTopologyService(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader(java.nio.file.Path.of("data/phantoms/topology"), backend, policy), backend, policy, signals);
		PhantomAssertions.assertTrue(topology.start(), "C01 actual catalog topology starts.");
		final var publication = new AtomicReference<String>();
		final var calls = new AtomicInteger();
		final var profile = createProfile(_environment.primary().objectId());
		final var lifecycle = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long id, int objectId) { }
			@Override public void afterPlayerLoad(long id, Player player) { }
			@Override public void materializeSucceeded(long id, int objectId) { }
			@Override public void materializeAborted(long id, int objectId) { }
			@Override public void beforeStore(long id, Player player) { }
			@Override public void afterStore(long id, Player player)
			{
				calls.incrementAndGet();
				final var registered = topology.registerProfile(id);
				if (registered != org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult.REGISTERED) { publication.set(registered.name()); return; }
				publication.set(topology.updateProfile(id, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()), 1).name());
				topology.unregisterProfile(id);
			}
		};
		final var metrics = new PhantomMetrics();
		final var service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1, lifecycle);
		PhantomAssertions.assertTrue(service.start(), "C01 materialization starts."); _services.add(service);
		PhantomSystem.configureForTesting(service);
		final var instanceField = PhantomSystem.class.getDeclaredField("_configuredInstance"); instanceField.setAccessible(true);
		final var configured = instanceField.get(null);
		final var topologyField = PhantomSystem.class.getDeclaredField("_topologyService"); topologyField.setAccessible(true);
		final var old = (PhantomTopologyService) topologyField.get(configured); old.beginStop(); PhantomAssertions.assertTrue(old.finishStop(), "Old empty test topology drains.");
		topologyField.set(configured, topology);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "C01 native actor materialized.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "C01 configured native store drains.");
		PhantomAssertions.assertEquals("UPDATED", publication.get(), "C01 actual POST_STORE index publication cannot see NOT_RUNNING.");
		PhantomAssertions.assertEquals(1, calls.get(), "C01 repeated stop cannot repeat native store.");
		PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "C01 absent configured instance remains a stock no-op.");
		PhantomAssertions.assertEquals(PhantomTopologyService.State.STOPPED, topology.snapshot().state(), "C01 dependency stops after lifetime release.");
		PhantomAssertions.assertEquals(0, service.snapshot().materializations().size(), "C01 no retained entries.");
		context.record("C01.postStorePublication", publication.get());
	}

	private void testStoreMonitor024(PhantomTestContext context) throws Exception
	{
		reset();
		final var observed = new AtomicInteger();
		final var instance = new AtomicReference<PhantomSystem>();
		final var service = service(1, point ->
		{
			if (point == FailurePoint.AFTER_NATIVE_STORE)
			{
				PhantomSystem.configuredShutdownSnapshot();
				instance.get().snapshot();
				PhantomAssertions.assertFalse(instance.get().shutdown(), "C03 concurrent shutdown cannot acquire a second drain claim.");
				observed.incrementAndGet();
			}
		}, 150);
		PhantomSystem.configureForTesting(service);
		final var instanceField = PhantomSystem.class.getDeclaredField("_configuredInstance"); instanceField.setAccessible(true);
		instance.set((PhantomSystem) instanceField.get(null));
		final var profile = createProfile(_environment.primary().objectId());
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "C03 monitor actor materialized.");
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "C03 native store callback must finish while system shutdown waits outside both monitors.");
		PhantomAssertions.assertEquals(1, observed.get(), "C03 exact native callback completed once.");
	}

	private PhantomMaterializationService service(int capacity, PhantomMaterializedPlayer.FailureInjector failureInjector, long shutdownTimeoutMillis)
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(true, 32, 1, metrics);
		final PhantomMaterializationService service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, capacity, failureInjector, 5000, shutdownTimeoutMillis);
		PhantomAssertions.assertTrue(service.start(), "Shutdown-handoff materialization service did not start.");
		_services.add(service);
		return service;
	}

	private PhantomProfile createProfile(int characterObjectId)
	{
		final PhantomProfile profile = _repository.create(characterObjectId);
		_ownedProfileIds.add(profile.profileId());
		return profile;
	}

	private void reset() throws Exception
	{
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.shutdownIfStarted();
			if (PhantomSystem.hasConfiguredInstance())
			{
				throw new AssertionError("Configured PhantomSystem retained failed cleanup between tests.");
			}
		}
		for (PhantomMaterializationService service : List.copyOf(_services))
		{
			PhantomMaterializationService.ShutdownResult result = service.shutdown();
			if (result.state() != ServiceState.STOPPED)
			{
				result = service.shutdown();
			}
			if (result.state() != ServiceState.STOPPED)
			{
				throw new AssertionError("Shutdown-handoff test service retained failed cleanup: " + result.failedProfileIds());
			}
		}
		_services.clear();
		deleteOwnedProfiles();
	}

	private void deleteOwnedProfiles() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
		{
			for (long profileId : List.copyOf(_ownedProfileIds))
			{
				statement.setLong(1, profileId);
				statement.executeUpdate();
				_ownedProfileIds.remove(profileId);
			}
		}
	}

	private static boolean selectedForGenericDisconnect(Player player)
	{
		return !PhantomSystem.isMaterializationManaged(player);
	}

	private static int simulatedGenericDisconnections(List<Player> players)
	{
		int disconnections = 0;
		for (Player player : players)
		{
			if (selectedForGenericDisconnect(player))
			{
				disconnections++;
			}
		}
		return disconnections;
	}

	private static PolicyResult simulateServerPolicy(List<String> events, boolean firstTerminal, boolean secondTerminal)
	{
		int shutdownCalls = 0;
		boolean configured = true;
		events.add("first");
		shutdownCalls++;
		configured = !firstTerminal;
		events.add("disconnect");
		if (configured)
		{
			events.add("second");
			shutdownCalls++;
			configured = !secondTerminal;
		}
		events.add("thread-pool");
		return new PolicyResult(shutdownCalls, !configured);
	}

	private record PolicyResult(int shutdownCalls, boolean successful)
	{
	}

	private static final class PersistentFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}
}
