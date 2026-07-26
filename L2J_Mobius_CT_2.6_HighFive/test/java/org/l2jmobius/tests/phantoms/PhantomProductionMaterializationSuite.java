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
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.DematerializeResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializeResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomRetainedIdentityRecovery.Status;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

public final class PhantomProductionMaterializationSuite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final Set<Long> _ownedProfileIds = ConcurrentHashMap.newKeySet();
	private final List<PhantomMaterializationService> _services = new ArrayList<>();
	private PhantomProfileRepository _repository;

	@Override
	public String id()
	{
		return "production-materialization";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_repository = PhantomProfileRepository.open();
		context.record("productionMaterialization.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("productionMaterialization.primaryObjectId", _environment.primary().objectId());
		context.record("productionMaterialization.observerObjectId", _environment.observer().objectId());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			shutdownServices();
			deleteOwnedProfiles();
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
		registry.add("01-config-strict-cap-and-disabled-effective-zero", _ -> testConfig());
		registry.add("02-start-zero-missing-unlinked-no-auto", _ -> testStartMissingAndUnlinked());
		registry.add("03-linked-canonical-materialization", _ -> testCanonicalMaterialization());
		registry.add("04-concurrent-profile-and-character-uniqueness", _ -> testConcurrentAndCharacterUniqueness());
		registry.add("05-cap-release-and-readmission", _ -> testCapacity());
		registry.add("06-captured-link-and-ordered-immutable-snapshots", _ -> testCapturedLinkAndSnapshots());
		registry.add("07-phantom-and-reserved-real-owner-block", _ -> testReservedOwnersBlock());
		registry.add("08-clean-retained-real-owner-recovers", _ -> testCleanRetainedRecovery());
		registry.add("09-world-and-autosave-residue-reject-recovery", _ -> testRecoveryResidue());
		registry.add("10-database-evidence-and-token-race-fail-closed", _ -> testRecoveryDatabaseAndTokenRace());
		registry.add("11-action-token-drain-and-double-close", _ -> testActionDrain());
		registry.add("12-action-timeout-retains-cap-and-retries", _ -> testActionTimeout());
		registry.add("13-operation-failure-retains-and-retries", _ -> testCleanupOperationFailure());
		registry.add("14-stable-shutdown-and-one-immediate-retry", _ -> testShutdownOrderAndRetry());
		registry.add("15-persistent-shutdown-failure-second-retry-and-restart", _ -> testPersistentShutdownAndRestart());
		registry.add("16-fixed-metrics-and-bounded-trace", _ -> testMetricsAndTrace());
		registry.add("17-world-and-autosave-materialization-boundaries", _ -> testMaterializationIdentityBoundaries());
		registry.add("18-action-admission-atomic-with-stopping", _ -> testActionAdmissionAtomicWithStopping());
		registry.add("19-shutdown-caller-wall-clock-bound", this::testShutdownCallerWallClock);
	}

	private void testConfig() throws Exception
	{
		reset();
		PhantomAssertions.assertEquals(32, PhantomPlayersConfig.DEFAULT_MAX_MATERIALIZED_PHANTOMS, "Canonical configured cap default changed.");
		final PhantomPlayersConfig.Settings disabled = settings("EnablePhantomSystem = False\nEnablePhantomDiagnostics = False\nMaxMaterializedPhantoms = 32\n");
		PhantomAssertions.assertFalse(disabled.enabled() || disabled.diagnosticsEnabled(), "Canonical defaults did not remain disabled.");
		PhantomAssertions.assertEquals(0, disabled.maxMaterializedPhantoms(), "Disabled effective cap is not zero.");
		PhantomAssertions.assertEquals(1, settings("EnablePhantomSystem = True\nEnablePhantomDiagnostics = False\nMaxMaterializedPhantoms = 1\n").maxMaterializedPhantoms(), "Enabled lower cap bound was rejected.");
		PhantomAssertions.assertEquals(10000, settings("EnablePhantomSystem = True\nEnablePhantomDiagnostics = True\nMaxMaterializedPhantoms = 10000\n").maxMaterializedPhantoms(), "Enabled upper cap bound was rejected.");
		for (String invalid : List.of("", " ", "+1", "-1", "0", "10001", "one", "1.0"))
		{
			final PhantomPlayersConfig.Settings rejected = settings("EnablePhantomSystem = True\nEnablePhantomDiagnostics = True\nMaxMaterializedPhantoms = " + invalid + "\n");
			PhantomAssertions.assertFalse(rejected.enabled() || rejected.diagnosticsEnabled(), "Invalid enabled cap did not disable the subsystem: " + invalid);
			PhantomAssertions.assertEquals(0, rejected.maxMaterializedPhantoms(), "Invalid enabled cap left runtime capacity.");
		}
		final PhantomPlayersConfig.Settings missing = settings("EnablePhantomSystem = True\nEnablePhantomDiagnostics = True\n");
		PhantomAssertions.assertFalse(missing.enabled(), "Missing enabled cap did not fail closed.");
		final PhantomSystem system = new PhantomSystem(new PhantomPlayersConfig.Settings(false, true, 32));
		PhantomAssertions.assertFalse(system.start(), "Disabled system unexpectedly started.");
		PhantomAssertions.assertTrue(system.snapshot().metrics().isZero(), "Disabled start changed metrics.");
	}

	private void testStartMissingAndUnlinked() throws Exception
	{
		reset();
		final ServiceFixture fixture = service(2);
		PhantomAssertions.assertEquals(0, fixture.service().snapshot().retainedEntries(), "Service start materialized an actor.");
		PhantomAssertions.assertEquals(ResultStatus.PROFILE_NOT_FOUND, fixture.service().materialize(Long.MAX_VALUE).status(), "Missing profile result is wrong.");
		final PhantomProfile unlinked = createProfile(null);
		PhantomAssertions.assertEquals(ResultStatus.PROFILE_UNLINKED, fixture.service().materialize(unlinked.profileId()).status(), "Unlinked profile result is wrong.");
		PhantomAssertions.assertEquals(0, fixture.service().snapshot().retainedEntries(), "Rejected requests retained service entries.");
	}

	private void testCanonicalMaterialization() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(2);
		final MaterializeResult result = fixture.service().materialize(profile.profileId());
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, result.status(), "Linked profile did not materialize.");
		final Player player = World.getInstance().getPlayer(_environment.primary().objectId());
		PhantomAssertions.assertTrue(player != null && player.isOnline(), "Canonical Player is not online in World.");
		PhantomAssertions.assertEquals(player, World.getInstance().findObject(_environment.primary().objectId()), "Canonical Player is not the exact general World object.");
		PhantomAssertions.assertTrue(PlayerAutoSaveTaskManager.getInstance().contains(player), "Canonical Player is not the exact autosave owner.");
		PhantomAssertions.assertFalse(PlayerAutoSaveTaskManager.getInstance().containsOtherObjectId(player.getObjectId(), player), "Another autosave Player owns the canonical object ID.");
		PhantomAssertions.assertTrue(player.hasHeadlessOutboundSession(), "Canonical Player has no headless output.");
		PhantomAssertions.assertEquals(_environment.primary().objectId(), result.snapshot().characterObjectId(), "Materialization captured the wrong character.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().dematerialize(profile.profileId()).status(), "Canonical Player did not dematerialize.");
		_environment.assertClean(_environment.primary(), player);
	}

	private void testConcurrentAndCharacterUniqueness() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		final List<ResultStatus> results = new java.util.concurrent.CopyOnWriteArrayList<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread left = materializeContender(fixture.service(), profile.profileId(), ready, start, results, failure, "t006-same-profile-left");
		final Thread right = materializeContender(fixture.service(), profile.profileId(), ready, start, results, failure, "t006-same-profile-right");
		left.start();
		right.start();
		PhantomAssertions.assertTrue(ready.await(2, TimeUnit.SECONDS), "Concurrent materializers did not reach the barrier.");
		start.countDown();
		left.join(10000);
		right.join(10000);
		PhantomAssertions.assertFalse(left.isAlive() || right.isAlive(), "Concurrent materializers did not terminate.");
		PhantomAssertions.assertEquals(null, failure.get(), "Concurrent materialization failed unexpectedly.");
		PhantomAssertions.assertEquals(1L, results.stream().filter(ResultStatus.SUCCESS::equals).count(), "Concurrent same-profile materialization did not have exactly one winner.");
		PhantomAssertions.assertEquals(1L, results.stream().filter(ResultStatus.ALREADY_ACTIVE::equals).count(), "Concurrent same-profile loser result is wrong.");

		final PhantomProfile relinked = _repository.updateCharacterLink(profile.profileId(), profile.rowVersion(), _environment.observer().objectId());
		final PhantomProfile secondOwner = createProfile(_environment.primary().objectId());
		PhantomAssertions.assertEquals(ResultStatus.CHARACTER_ALREADY_ACTIVE, fixture.service().materialize(secondOwner.profileId()).status(), "Captured active character ownership was bypassed after profile relink.");
		PhantomAssertions.assertEquals(_environment.primary().objectId(), fixture.service().find(relinked.profileId()).orElseThrow().characterObjectId(), "Active actor retargeted after profile link change.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().dematerialize(profile.profileId()).status(), "Concurrent winner cleanup failed.");
	}

	private void testCapacity() throws Exception
	{
		reset();
		final PhantomProfile primary = createProfile(_environment.primary().objectId());
		final PhantomProfile observer = createProfile(_environment.observer().objectId());
		final ServiceFixture fixture = service(1);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(primary.profileId()).status(), "First capped actor did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.CAPACITY_REACHED, fixture.service().materialize(observer.profileId()).status(), "Cap did not reject the second actor.");
		PhantomAssertions.assertEquals(0, fixture.service().snapshot().availablePermits(), "Active actor did not retain its permit.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().dematerialize(primary.profileId()).status(), "First capped actor cleanup failed.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(observer.profileId()).status(), "Released permit did not admit another actor.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().dematerialize(observer.profileId()).status(), "Readmitted actor cleanup failed.");
	}

	private void testCapturedLinkAndSnapshots() throws Exception
	{
		reset();
		final PhantomProfile high = createProfile(_environment.primary().objectId());
		final PhantomProfile low = createProfile(_environment.observer().objectId());
		final ServiceFixture fixture = service(2);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(high.profileId()).status(), "First snapshot actor did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(low.profileId()).status(), "Second snapshot actor did not materialize.");
		final List<PhantomMaterializationService.MaterializationSnapshot> snapshots = fixture.service().list();
		PhantomAssertions.assertEquals(snapshots.stream().map(PhantomMaterializationService.MaterializationSnapshot::profileId).sorted().toList(), snapshots.stream().map(PhantomMaterializationService.MaterializationSnapshot::profileId).toList(), "Snapshots are not profile-ID ordered.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> snapshots.add(snapshots.getFirst()), "Snapshot list is mutable.");
		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().shutdown().state(), "Two-actor shutdown failed.");
	}

	private void testReservedOwnersBlock() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(1);
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		try (Lease phantom = registry.tryAcquire(_environment.primary().objectId(), OwnerKind.PHANTOM))
		{
			PhantomAssertions.assertTrue(phantom != null, "Could not create PHANTOM collision lease.");
			PhantomAssertions.assertEquals(ResultStatus.IDENTITY_BUSY, fixture.service().materialize(profile.profileId()).status(), "PHANTOM owner did not block materialization.");
		}
		try (Lease real = registry.tryAcquire(_environment.primary().objectId(), OwnerKind.REAL_LOGIN))
		{
			PhantomAssertions.assertTrue(real != null, "Could not create RESERVED REAL_LOGIN lease.");
			PhantomAssertions.assertEquals(ResultStatus.RETAINED_IDENTITY_NOT_RECOVERABLE, fixture.service().recoverRetainedIdentity(_environment.primary().objectId()).status(), "RESERVED REAL_LOGIN was recoverable.");
			PhantomAssertions.assertEquals(Status.RESERVED_OWNER, fixture.service().recoverRetainedIdentity(_environment.primary().objectId()).evidence().status(), "RESERVED recovery rejection reason is wrong.");
			PhantomAssertions.assertEquals(ResultStatus.IDENTITY_BUSY, fixture.service().materialize(profile.profileId()).status(), "RESERVED REAL_LOGIN did not block materialization.");
			PhantomAssertions.assertEquals(OwnerState.RESERVED, registry.getOwnerState(_environment.primary().objectId()), "RESERVED REAL_LOGIN state was changed.");
		}
	}

	private void testCleanRetainedRecovery() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(1);
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		final Lease retained = registry.tryAcquire(_environment.primary().objectId(), OwnerKind.REAL_LOGIN);
		PhantomAssertions.assertTrue((retained != null) && retained.markRetained(), "Could not create retained REAL_LOGIN owner.");
		try
		{
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(profile.profileId()).status(), "Clean retained REAL_LOGIN did not recover and materialize.");
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, registry.getOwnerKind(_environment.primary().objectId()), "Recovered identity was not claimed by PHANTOM.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().dematerialize(profile.profileId()).status(), "Recovered actor cleanup failed.");
		}
		finally
		{
			retained.close();
		}
	}

	private void testRecoveryResidue() throws Exception
	{
		reset();
		final ServiceFixture fixture = service(1);
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();

		Player player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(player != null, "Could not load World residue Player.");
		Lease retained = retainedReal(registry, _environment.primary().objectId());
		try
		{
			World.getInstance().addObject(player);
			PhantomAssertions.assertEquals(Status.WORLD_PLAYER_PRESENT, fixture.service().recoverRetainedIdentity(_environment.primary().objectId()).evidence().status(), "World Player residue did not reject recovery.");
		}
		finally
		{
			World.getInstance().removeObject(player);
			retained.close();
			_environment.cleanupLoadedPlayer(player);
		}

		player = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(player != null, "Could not load object-only residue Player.");
		retained = retainedReal(registry, _environment.observer().objectId());
		try
		{
			player.setTeleporting(true, false);
			World.getInstance().addObject(player);
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(player.getObjectId()), "Object-only residue unexpectedly entered the Player map.");
			PhantomAssertions.assertTrue(World.getInstance().findObject(player.getObjectId()) != null, "Object-only residue did not enter World.");
			PhantomAssertions.assertEquals(Status.WORLD_OBJECT_PRESENT, fixture.service().recoverRetainedIdentity(_environment.observer().objectId()).evidence().status(), "World object residue did not reject recovery.");
		}
		finally
		{
			World.getInstance().removeObject(player);
			player.setTeleporting(false, false);
			retained.close();
			_environment.cleanupLoadedPlayer(player);
		}

		player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(player != null, "Could not load autosave residue Player.");
		retained = retainedReal(registry, _environment.primary().objectId());
		try
		{
			PlayerAutoSaveTaskManager.getInstance().add(player);
			PhantomAssertions.assertEquals(Status.AUTOSAVE_PRESENT, fixture.service().recoverRetainedIdentity(_environment.primary().objectId()).evidence().status(), "Autosave residue did not reject recovery.");
		}
		finally
		{
			PlayerAutoSaveTaskManager.getInstance().remove(player);
			retained.close();
			_environment.cleanupLoadedPlayer(player);
		}
	}

	private void testRecoveryDatabaseAndTokenRace() throws Exception
	{
		reset();
		final ServiceFixture fixture = service(1);
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();
		for (int online : List.of(1, 2))
		{
			final Lease retained = retainedReal(registry, _environment.primary().objectId());
			try
			{
				setOnline(_environment.primary().objectId(), online);
				PhantomAssertions.assertEquals(Status.CHARACTER_ONLINE, fixture.service().recoverRetainedIdentity(_environment.primary().objectId()).evidence().status(), "Nonzero DB online evidence did not reject recovery.");
			}
			finally
			{
				setOnline(_environment.primary().objectId(), 0);
				retained.close();
			}
		}

		final int missingObjectId = 1999999999;
		final Lease missing = retainedReal(registry, missingObjectId);
		try
		{
			PhantomAssertions.assertEquals(Status.CHARACTER_NOT_FOUND, fixture.service().recoverRetainedIdentity(missingObjectId).evidence().status(), "Missing character row did not reject recovery.");
		}
		finally
		{
			missing.close();
		}

		final Lease original = retainedReal(registry, _environment.primary().objectId());
		final OwnerSnapshot stale = registry.getOwnerSnapshot(_environment.primary().objectId());
		original.close();
		final Lease replacement = retainedReal(registry, _environment.primary().objectId());
		try
		{
			PhantomAssertions.assertFalse(registry.releaseRetained(stale), "Stale recovery token removed a replacement owner.");
			PhantomAssertions.assertEquals(replacement.token(), registry.getOwnerSnapshot(_environment.primary().objectId()).token(), "Replacement retained owner changed after stale removal.");
		}
		finally
		{
			replacement.close();
		}
	}

	private void testActionDrain() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(1);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(profile.profileId()).status(), "Action actor did not materialize.");
		final ActionLease action = fixture.service().tryAcquireAction(profile.profileId()).orElseThrow();
		action.close();
		action.close();
		PhantomAssertions.assertEquals(0, fixture.service().find(profile.profileId()).orElseThrow().admittedActionCount(), "Double action close decremented incorrectly.");

		final ActionLease held = fixture.service().tryAcquireAction(profile.profileId()).orElseThrow();
		final AtomicReference<DematerializeResult> cleanup = new AtomicReference<>();
		final Thread thread = new Thread(() -> cleanup.set(fixture.service().dematerialize(profile.profileId())), "t006-action-drain");
		thread.start();
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (fixture.service().find(profile.profileId()).orElseThrow().actionAdmissionOpen() && (System.nanoTime() < deadline))
		{
			Thread.onSpinWait();
		}
		PhantomAssertions.assertTrue(fixture.service().tryAcquireAction(profile.profileId()).isEmpty(), "Cleanup admitted a new action.");
		held.close();
		thread.join(10000);
		PhantomAssertions.assertFalse(thread.isAlive(), "Cleanup did not finish after action drain.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanup.get().status(), "Action drain cleanup failed.");
	}

	private void testActionTimeout() throws Exception
	{
		reset();
		final PhantomProfile primary = createProfile(_environment.primary().objectId());
		final PhantomProfile observer = createProfile(_environment.observer().objectId());
		final ServiceFixture fixture = service(1, PhantomMaterializedPlayer.FailureInjector.none(), 50, 10000);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(primary.profileId()).status(), "Timeout actor did not materialize.");
		final ActionLease held = fixture.service().tryAcquireAction(primary.profileId()).orElseThrow();
		try
		{
			PhantomAssertions.assertEquals(ResultStatus.CLEANUP_FAILED_RETAINED, fixture.service().dematerialize(primary.profileId()).status(), "Action drain timeout did not retain the actor.");
			PhantomAssertions.assertEquals(ResultStatus.CAPACITY_REACHED, fixture.service().materialize(observer.profileId()).status(), "Timed-out actor released its permit.");
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_environment.primary().objectId()), "Timed-out actor released identity.");
		}
		finally
		{
			held.close();
		}
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().retryCleanup(primary.profileId()).status(), "Timed-out actor did not clean on explicit retry.");
	}

	private void testCleanupOperationFailure() throws Exception
	{
		reset();
		final AtomicInteger injected = new AtomicInteger();
		final PhantomProfile primary = createProfile(_environment.primary().objectId());
		final PhantomProfile observer = createProfile(_environment.observer().objectId());
		final ServiceFixture fixture = service(1, point ->
		{
			if ((point == FailurePoint.BEFORE_STORE_OPERATION) && injected.compareAndSet(0, 1))
			{
				throw new InjectedFailure();
			}
		}, 5000, 10000);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(primary.profileId()).status(), "Fault actor did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.CLEANUP_FAILED_RETAINED, fixture.service().dematerialize(primary.profileId()).status(), "Store failure did not retain cleanup state.");
		PhantomAssertions.assertTrue(fixture.service().find(primary.profileId()).orElseThrow().identityLeaseRetained(), "Store failure released identity.");
		PhantomAssertions.assertEquals(ResultStatus.CAPACITY_REACHED, fixture.service().materialize(observer.profileId()).status(), "Store failure released capacity.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().retryCleanup(primary.profileId()).status(), "Store failure did not clean on explicit retry.");
		PhantomAssertions.assertEquals(1, injected.get(), "Store failure was not injected exactly once.");
	}

	private void testShutdownOrderAndRetry() throws Exception
	{
		reset();
		final PhantomProfile primary = createProfile(_environment.primary().objectId());
		final PhantomProfile observer = createProfile(_environment.observer().objectId());
		final ServiceFixture fixture = service(2);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(observer.profileId()).status(), "Observer shutdown actor did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(primary.profileId()).status(), "Primary shutdown actor did not materialize.");
		PhantomAssertions.assertEquals(List.of(primary.profileId(), observer.profileId()).stream().sorted().toList(), fixture.service().list().stream().map(PhantomMaterializationService.MaterializationSnapshot::profileId).toList(), "Shutdown input snapshots are not stable ordered.");
		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().shutdown().state(), "Stable two-actor shutdown failed.");

		deleteOwnedProfiles();
		final PhantomProfile retryProfile = createProfile(_environment.primary().objectId());
		final AtomicInteger injected = new AtomicInteger();
		final ServiceFixture retryFixture = service(1, point ->
		{
			if ((point == FailurePoint.BEFORE_STORE_OPERATION) && injected.compareAndSet(0, 1))
			{
				throw new InjectedFailure();
			}
		}, 5000, 10000);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, retryFixture.service().materialize(retryProfile.profileId()).status(), "Shutdown retry actor did not materialize.");
		PhantomAssertions.assertEquals(ServiceState.STOPPED, retryFixture.service().shutdown().state(), "One-time shutdown failure did not succeed on its single immediate retry.");
		PhantomAssertions.assertEquals(1, injected.get(), "Shutdown retry failure was not injected once.");
	}

	private void testPersistentShutdownAndRestart() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final AtomicBoolean fault = new AtomicBoolean(true);
		final ServiceFixture fixture = service(1, point ->
		{
			if ((point == FailurePoint.BEFORE_STORE_OPERATION) && fault.get())
			{
				throw new InjectedFailure();
			}
		}, 5000, 10000);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(profile.profileId()).status(), "Persistent-fault actor did not materialize.");
		final PhantomMaterializationService.ShutdownResult failed = fixture.service().shutdown();
		PhantomAssertions.assertEquals(ServiceState.FAILED, failed.state(), "Persistent shutdown failure reported a terminal stop.");
		PhantomAssertions.assertEquals(List.of(profile.profileId()), failed.failedProfileIds(), "Persistent shutdown failure IDs are not exact.");
		PhantomAssertions.assertEquals(1, fixture.service().snapshot().retainedEntries(), "Persistent shutdown failure released its entry.");
		fault.set(false);
		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().shutdown().state(), "Second explicit shutdown did not retry retained cleanup.");

		final ServiceFixture restarted = service(1);
		PhantomAssertions.assertEquals(0, restarted.service().snapshot().retainedEntries(), "Fresh service restored runtime active state.");
		PhantomAssertions.assertTrue(_repository.find(profile.profileId()).isPresent(), "Fresh service lost the persisted profile.");
		PhantomAssertions.assertEquals(0L, componentCount(profile.profileId()), "Runtime lifecycle wrote a profile component.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, restarted.service().materialize(profile.profileId()).status(), "Fresh service could not explicitly rematerialize the profile.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, restarted.service().dematerialize(profile.profileId()).status(), "Fresh service cleanup failed.");
	}

	private void testMetricsAndTrace() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(true, 2, 1, metrics);
		final PhantomMaterializationService service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, 1);
		PhantomAssertions.assertTrue(service.start(), "Metrics service did not start.");
		_services.add(service);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.materialize(profile.profileId()).status(), "Metrics actor did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.ALREADY_ACTIVE, service.materialize(profile.profileId()).status(), "Metrics duplicate request was not rejected.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, service.dematerialize(profile.profileId()).status(), "Metrics actor cleanup failed.");
		final PhantomMetrics.Snapshot snapshot = metrics.snapshot();
		PhantomAssertions.assertEquals(2L, snapshot.materializationRequested(), "Materialization request metric is wrong.");
		PhantomAssertions.assertEquals(1L, snapshot.materializationSucceeded(), "Materialization success metric is wrong.");
		PhantomAssertions.assertEquals(1L, snapshot.materializationRejected(), "Materialization rejection metric is wrong.");
		PhantomAssertions.assertEquals(1L, snapshot.dematerializationSucceeded(), "Dematerialization metric is wrong.");
		PhantomAssertions.assertEquals(0L, snapshot.activeCurrent(), "Active-current metric did not return to zero.");
		PhantomAssertions.assertEquals(1L, snapshot.activePeak(), "Active-peak metric is wrong.");
		PhantomAssertions.assertEquals(2, trace.snapshot().events().size(), "Bounded trace did not retain exactly its capacity.");
		PhantomAssertions.assertTrue(snapshot.traceDropped() > 0, "Bounded trace did not account for overwritten events.");
	}

	private void testMaterializationIdentityBoundaries() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(1);
		final PhantomIdentityLeaseRegistry registry = PhantomIdentityLeaseRegistry.getInstance();

		final Player worldPlayer = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(worldPlayer != null, "Could not load World collision Player.");
		worldPlayer.spawnMe();
		try
		{
			PhantomAssertions.assertEquals(ResultStatus.WORLD_PLAYER_IDENTITY_BUSY, fixture.service().materialize(profile.profileId()).status(), "Existing World Player did not reject materialization distinctly.");
			PhantomAssertions.assertEquals(worldPlayer, World.getInstance().getPlayer(worldPlayer.getObjectId()), "World Player collision disturbed the Player map.");
			PhantomAssertions.assertEquals(worldPlayer, World.getInstance().findObject(worldPlayer.getObjectId()), "World Player collision disturbed the object map.");
			PhantomAssertions.assertTrue(PlayerAutoSaveTaskManager.getInstance().contains(worldPlayer), "World Player collision disturbed autosave.");
			PhantomAssertions.assertEquals(1, fixture.service().snapshot().availablePermits(), "World Player collision leaked capacity.");
			PhantomAssertions.assertEquals(null, registry.getOwnerKind(worldPlayer.getObjectId()), "World Player collision leaked PHANTOM ownership.");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(worldPlayer);
		}

		final WorldObject worldObject = new ObjectIdResidue(_environment.primary().objectId());
		World.getInstance().addObject(worldObject);
		try
		{
			PhantomAssertions.assertEquals(ResultStatus.WORLD_OBJECT_IDENTITY_BUSY, fixture.service().materialize(profile.profileId()).status(), "Existing non-Player World object did not reject materialization distinctly.");
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(worldObject.getObjectId()), "Non-Player collision created a split Player map.");
			PhantomAssertions.assertEquals(worldObject, World.getInstance().findObject(worldObject.getObjectId()), "Non-Player collision disturbed the existing World object.");
			PhantomAssertions.assertEquals(1, fixture.service().snapshot().availablePermits(), "World object collision leaked capacity.");
			PhantomAssertions.assertEquals(null, registry.getOwnerKind(worldObject.getObjectId()), "World object collision leaked PHANTOM ownership.");
		}
		finally
		{
			World.getInstance().removeObject(worldObject);
		}

		final Player autosavePlayer = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(autosavePlayer != null, "Could not load autosave collision Player.");
		try
		{
			PhantomAssertions.assertEquals(ResultStatus.AUTOSAVE_IDENTITY_BUSY, fixture.service().materialize(profile.profileId()).status(), "Existing autosave owner did not reject materialization distinctly.");
			PhantomAssertions.assertTrue(PlayerAutoSaveTaskManager.getInstance().contains(autosavePlayer), "Autosave collision disturbed the existing Player.");
			PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(autosavePlayer.getObjectId()), "Autosave collision published a World Player.");
			PhantomAssertions.assertEquals(null, World.getInstance().findObject(autosavePlayer.getObjectId()), "Autosave collision published a World object.");
			PhantomAssertions.assertEquals(1, fixture.service().snapshot().availablePermits(), "Autosave collision leaked capacity.");
			PhantomAssertions.assertEquals(null, registry.getOwnerKind(autosavePlayer.getObjectId()), "Autosave collision leaked PHANTOM ownership.");
		}
		finally
		{
			_environment.cleanupLoadedPlayer(autosavePlayer);
		}

		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().shutdown().state(), "Identity preflight service did not stop.");
		final AtomicReference<WorldObject> injectedObject = new AtomicReference<>();
		final ServiceFixture injectedFixture = service(1, point ->
		{
			if ((point == FailurePoint.AFTER_PLAYER_LOAD) && (injectedObject.get() == null))
			{
				final WorldObject object = new ObjectIdResidue(_environment.primary().objectId());
				if (injectedObject.compareAndSet(null, object))
				{
					World.getInstance().addObject(object);
				}
			}
		}, 5000, 10000);
		final MaterializeResult injected = injectedFixture.service().materialize(profile.profileId());
		PhantomAssertions.assertEquals(ResultStatus.WORLD_OBJECT_IDENTITY_BUSY, injected.status(), "World insertion after Player load was not detected before spawn.");
		final WorldObject residue = injectedObject.get();
		PhantomAssertions.assertTrue(residue != null, "Pre-spawn World residue was not injected.");
		PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(residue.getObjectId()), "Pre-spawn collision created a split World Player map.");
		PhantomAssertions.assertEquals(residue, World.getInstance().findObject(residue.getObjectId()), "Pre-spawn collision disturbed the injected World object.");
		PhantomAssertions.assertEquals(0, injectedFixture.service().snapshot().availablePermits(), "Pre-spawn collision released capacity before terminal STORED.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, registry.getOwnerKind(residue.getObjectId()), "Pre-spawn collision released identity before terminal STORED.");
		World.getInstance().removeObject(residue);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, injectedFixture.service().retryCleanup(profile.profileId()).status(), "Pre-spawn collision did not clean after residue removal.");
		PhantomAssertions.assertEquals(1, injectedFixture.service().snapshot().availablePermits(), "Pre-spawn collision cleanup leaked capacity.");
		PhantomAssertions.assertEquals(0, injectedFixture.service().snapshot().retainedEntries(), "Pre-spawn collision cleanup leaked service maps.");
		_environment.assertClean(_environment.primary(), null);
	}

	private void testActionAdmissionAtomicWithStopping() throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final ServiceFixture fixture = service(1, PhantomMaterializedPlayer.FailureInjector.none(), 2000, 2000);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(profile.profileId()).status(), "STOPPING action actor did not materialize.");
		final Player player = World.getInstance().getPlayer(_environment.primary().objectId());
		final ActionLease held = fixture.service().tryAcquireAction(profile.profileId()).orElseThrow();
		final AtomicReference<PhantomMaterializationService.ShutdownResult> shutdown = new AtomicReference<>();
		final Thread shutdownThread = new Thread(() -> shutdown.set(fixture.service().shutdown()), "t006a-action-stopping");
		shutdownThread.start();
		final long stoppingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while ((fixture.service().snapshot().state() != ServiceState.STOPPING) && (System.nanoTime() < stoppingDeadline))
		{
			Thread.onSpinWait();
		}
		PhantomAssertions.assertEquals(ServiceState.STOPPING, fixture.service().snapshot().state(), "Shutdown did not expose STOPPING while the admitted action was held.");
		for (int attempt = 0; attempt < 1000; attempt++)
		{
			PhantomAssertions.assertTrue(fixture.service().tryAcquireAction(profile.profileId()).isEmpty(), "Action was admitted after STOPPING at attempt " + attempt + ".");
		}
		held.close();
		shutdownThread.join(10000);
		PhantomAssertions.assertFalse(shutdownThread.isAlive(), "Shutdown did not finish after the pre-STOPPING action was released.");
		PhantomAssertions.assertEquals(ServiceState.STOPPED, shutdown.get().state(), "Action/STOPPING shutdown did not reach STOPPED.");
		_environment.assertClean(_environment.primary(), player);
	}

	private void testShutdownCallerWallClock(PhantomTestContext context) throws Exception
	{
		reset();
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final CountDownLatch storeEntered = new CountDownLatch(1);
		final CountDownLatch releaseStore = new CountDownLatch(1);
		final AtomicInteger cleanupInvocations = new AtomicInteger();
		final ServiceFixture fixture = service(1, point ->
		{
			if (point == FailurePoint.BEFORE_STORE_OPERATION)
			{
				cleanupInvocations.incrementAndGet();
				storeEntered.countDown();
				try
				{
					if (!releaseStore.await(5, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting to release blocked store operation");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while blocking store operation", e);
				}
			}
		}, 5000, 150);
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, fixture.service().materialize(profile.profileId()).status(), "Wall-clock shutdown actor did not materialize.");
		final Player player = World.getInstance().getPlayer(_environment.primary().objectId());

		try
		{
			final long firstStarted = System.nanoTime();
			final PhantomMaterializationService.ShutdownResult first = fixture.service().shutdown();
			final long firstElapsed = System.nanoTime() - firstStarted;
			context.record("productionMaterialization.blockedShutdownElapsedNanos", firstElapsed);
			PhantomAssertions.assertEquals(ServiceState.FAILED, first.state(), "Blocked shutdown did not return FAILED.");
			PhantomAssertions.assertEquals(List.of(profile.profileId()), first.failedProfileIds(), "Blocked shutdown did not return exact retained profile IDs.");
			PhantomAssertions.assertTrue(firstElapsed < TimeUnit.SECONDS.toNanos(1), "Blocked shutdown exceeded the one-second wall-clock gate: " + firstElapsed);
			PhantomAssertions.assertTrue(storeEntered.await(1, TimeUnit.SECONDS), "Drain command did not reach the blocked store operation.");
			PhantomAssertions.assertEquals(1, fixture.service().snapshot().retainedEntries(), "Caller timeout released the service entry.");
			PhantomAssertions.assertEquals(0, fixture.service().snapshot().availablePermits(), "Caller timeout released capacity.");
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_environment.primary().objectId()), "Caller timeout released identity.");

			final long secondStarted = System.nanoTime();
			final PhantomMaterializationService.ShutdownResult second = fixture.service().shutdown();
			final long secondElapsed = System.nanoTime() - secondStarted;
			context.record("productionMaterialization.secondBlockedShutdownElapsedNanos", secondElapsed);
			PhantomAssertions.assertEquals(ServiceState.FAILED, second.state(), "Second early shutdown did not reuse the failed in-flight attempt.");
			PhantomAssertions.assertEquals(List.of(profile.profileId()), second.failedProfileIds(), "Second early shutdown lost exact retained profile IDs.");
			PhantomAssertions.assertTrue(secondElapsed < TimeUnit.SECONDS.toNanos(1), "Second early shutdown exceeded the one-second wall-clock gate: " + secondElapsed);
			PhantomAssertions.assertEquals(1, cleanupInvocations.get(), "Second early shutdown invoked duplicate cleanup.");
		}
		finally
		{
			releaseStore.countDown();
		}
		final long completionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while ((fixture.service().snapshot().state() != ServiceState.STOPPED) && (System.nanoTime() < completionDeadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().snapshot().state(), "Tracked drain did not complete after the store block was released.");
		PhantomAssertions.assertEquals(ServiceState.STOPPED, fixture.service().shutdown().state(), "Later explicit shutdown did not observe terminal STOPPED.");
		PhantomAssertions.assertEquals(1, cleanupInvocations.get(), "Late completion invoked cleanup more than once.");
		_environment.assertClean(_environment.primary(), player);
	}

	private PhantomPlayersConfig.Settings settings(String content) throws Exception
	{
		final Path path = Files.createTempFile("phantom-task006-config-", ".ini");
		try
		{
			Files.writeString(path, content, StandardCharsets.UTF_8);
			return PhantomPlayersConfig.read(path);
		}
		finally
		{
			Files.deleteIfExists(path);
		}
	}

	private Thread materializeContender(PhantomMaterializationService service, long profileId, CountDownLatch ready, CountDownLatch start, List<ResultStatus> results, AtomicReference<Throwable> failure, String name)
	{
		return new Thread(() ->
		{
			ready.countDown();
			try
			{
				start.await();
				results.add(service.materialize(profileId).status());
			}
			catch (Throwable throwable)
			{
				failure.compareAndSet(null, throwable);
			}
		}, name);
	}

	private Lease retainedReal(PhantomIdentityLeaseRegistry registry, int objectId)
	{
		final Lease lease = registry.tryAcquire(objectId, OwnerKind.REAL_LOGIN);
		PhantomAssertions.assertTrue((lease != null) && lease.markRetained(), "Could not create retained REAL_LOGIN lease.");
		return lease;
	}

	private ServiceFixture service(int capacity)
	{
		return service(capacity, PhantomMaterializedPlayer.FailureInjector.none(), 5000, 10000);
	}

	private ServiceFixture service(int capacity, PhantomMaterializedPlayer.FailureInjector failureInjector, long actionDrainTimeoutMillis, long shutdownTimeoutMillis)
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(true, 32, 1, metrics);
		final PhantomMaterializationService service = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, capacity, failureInjector, actionDrainTimeoutMillis, shutdownTimeoutMillis);
		PhantomAssertions.assertTrue(service.start(), "Materialization service did not start.");
		_services.add(service);
		return new ServiceFixture(service, metrics, trace);
	}

	private PhantomProfile createProfile(Integer characterObjectId)
	{
		final PhantomProfile profile = _repository.create(characterObjectId);
		_ownedProfileIds.add(profile.profileId());
		return profile;
	}

	private void reset() throws Exception
	{
		shutdownServices();
		deleteOwnedProfiles();
	}

	private void shutdownServices()
	{
		for (PhantomMaterializationService service : List.copyOf(_services))
		{
			final PhantomMaterializationService.ShutdownResult result = service.shutdown();
			if (result.state() != ServiceState.STOPPED)
			{
				throw new AssertionError("Test service retained failed shutdown state: " + result.failedProfileIds());
			}
		}
		_services.clear();
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

	private static void setOnline(int objectId, int online) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET online=? WHERE charId=?"))
		{
			statement.setInt(1, online);
			statement.setInt(2, objectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Could not set fixture online evidence.");
		}
	}

	private static long componentCount(long profileId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id = ?"))
		{
			statement.setLong(1, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Component count query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private record ServiceFixture(PhantomMaterializationService service, PhantomMetrics metrics, PhantomDiagnosticTrace trace)
	{
	}

	private static final class InjectedFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
	}

	private static final class ObjectIdResidue extends WorldObject
	{
		private ObjectIdResidue(int objectId)
		{
			super(objectId);
		}

		@Override
		public boolean isAutoAttackable(Creature attacker)
		{
			return false;
		}

		@Override
		public void sendInfo(Player player)
		{
		}
	}
}
