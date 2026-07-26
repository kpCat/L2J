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

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Player.OutboundSessionAttachment;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.ConfiguredShutdownSnapshot;
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
		registry.add("01-managed-classifier-fails-closed", _ -> testManagedClassifier());
		registry.add("02-two-phase-server-policy-and-source-order", this::testTwoPhasePolicy);
		registry.add("03-in-flight-drain-reused-before-thread-pool-phase", this::testInFlightDrain);
		registry.add("04-persistent-failure-retains-configured-ownership", _ -> testPersistentFailure());
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
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "In-flight handoff actor did not materialize.");
		final Player managed = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(_environment.primary().objectId());

		try
		{
			final long started = System.nanoTime();
			PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Blocked first server shutdown unexpectedly completed.");
			final long elapsed = System.nanoTime() - started;
			context.record("serverShutdownHandoff.blockedFirstElapsedNanos", elapsed);
			PhantomAssertions.assertTrue(elapsed < TimeUnit.SECONDS.toNanos(1), "First server shutdown exceeded its wall-clock gate: " + elapsed);
			PhantomAssertions.assertTrue(storeEntered.await(1, TimeUnit.SECONDS), "Tracked drain did not enter the blocked store operation.");
			final ConfiguredShutdownSnapshot retained = PhantomSystem.configuredShutdownSnapshot();
			PhantomAssertions.assertTrue(retained.configured(), "First timeout cleared the configured instance.");
			PhantomAssertions.assertEquals(1, retained.retainedEntries(), "First timeout did not retain the managed entry.");
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
		PhantomAssertions.assertFalse(PhantomSystem.isMaterializationManaged(managed), "Terminal second shutdown retained managed classification.");
		PhantomAssertions.assertEquals(new ConfiguredShutdownSnapshot(false, null, null, 0), PhantomSystem.configuredShutdownSnapshot(), "Absent configured snapshot is not bounded/empty.");
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
			PhantomAssertions.assertEquals(ServiceState.FAILED, retained.serviceState(), "Persistent failure lost service FAILED state.");
			PhantomAssertions.assertEquals(1, retained.retainedEntries(), "Persistent failure released the service entry.");
			PhantomAssertions.assertTrue(PhantomSystem.isMaterializationManaged(managed), "Persistent failure lost fail-closed ownership.");
			PhantomAssertions.assertEquals(4, cleanupInvocations.get(), "Two server opportunities did not preserve the accepted two-pass service contract.");
		}
		finally
		{
			fault.set(false);
		}

		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Explicit teardown cleanup did not stop the retained configured instance.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Explicit teardown cleanup retained the configured instance.");
		_environment.assertClean(_environment.primary(), managed);
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
