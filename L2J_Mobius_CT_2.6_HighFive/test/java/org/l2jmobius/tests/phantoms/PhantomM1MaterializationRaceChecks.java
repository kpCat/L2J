/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailureInjector;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.FailurePoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** Actual native load/spawn/store races; no model lifecycle, synthetic Player, or failed-owner reset. */
public final class PhantomM1MaterializationRaceChecks
{
	private PhantomM1MaterializationRaceChecks() { }
	public enum Stage { AFTER_PLAYER_LOAD, BEFORE_WORLD }
	public enum AbortFailure { NONE, ERROR, RUNTIME }

	/** Chain FIRST before the actual background port; pass this same object as FailureInjector. */
	public static final class Hold implements PhantomMaterializationLifecyclePort, FailureInjector
	{
		private final Stage _stage;
		private final AbortFailure _abortFailure;
		private final CountDownLatch _entered = new CountDownLatch(1), _release = new CountDownLatch(1);
		private final AtomicReference<Player> _player = new AtomicReference<>();
		private final AtomicInteger _before = new AtomicInteger(), _success = new AtomicInteger(), _abort = new AtomicInteger();
		private final AtomicInteger _world = new AtomicInteger(), _admitted = new AtomicInteger(), _beforeStore = new AtomicInteger(), _afterStore = new AtomicInteger();
		private final AtomicInteger _primaryBodyAdmissions = new AtomicInteger(), _storeOperations = new AtomicInteger(), _nativeStores = new AtomicInteger();
		private final AtomicBoolean _discardClaimed = new AtomicBoolean();
		private final AssertionError _primary = new AssertionError("TEST_MATERIALIZATION_NATIVE_PRIMARY");
		private final Throwable _secondary;
		private volatile long _profileId;
		private volatile boolean _primaryVerified;
		public Hold(Stage stage, AbortFailure abortFailure)
		{
			_stage = java.util.Objects.requireNonNull(stage); _abortFailure = java.util.Objects.requireNonNull(abortFailure);
			_secondary = abortFailure == AbortFailure.ERROR ? new AssertionError("TEST_MATERIALIZATION_ABORT_SECONDARY") : new IllegalStateException("TEST_MATERIALIZATION_ABORT_SECONDARY");
		}
		public void release() { _release.countDown(); }
		public Player player() { return _player.get(); }
		@Override public void beforeMaterialize(long profileId, int characterObjectId) { _profileId = profileId; _before.incrementAndGet(); }
		@Override public void afterPlayerLoad(long profileId, Player player)
		{
			PhantomAssertions.assertEquals(_profileId, profileId, "INVALID materialization race: lifecycle profile changed.");
			PhantomAssertions.assertTrue(_player.compareAndSet(null, player), "INVALID materialization race: Hold is single-use.");
			if (_stage == Stage.AFTER_PLAYER_LOAD) { hold(); }
		}
		@Override public void after(FailurePoint point)
		{
			if (_stage == Stage.BEFORE_WORLD && point == FailurePoint.AFTER_ONLINE_ACTIVATION) { hold(); }
			if (point == FailurePoint.AFTER_WORLD_SPAWN)
			{
				final Player player = player();
				PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == player && World.getInstance().findObject(player.getObjectId()) == player, "INVALID native race: exact stock World spawn did not complete."); _world.incrementAndGet();
			}
			if (point == FailurePoint.AFTER_ACTION_ADMISSION) { _admitted.incrementAndGet(); }
			if (point == FailurePoint.BEFORE_STORE_OPERATION) { _storeOperations.incrementAndGet(); }
			if (point == FailurePoint.AFTER_NATIVE_STORE) { _nativeStores.incrementAndGet(); }
		}
		private void hold()
		{
			_entered.countDown();
			try { PhantomAssertions.assertTrue(_release.await(8, TimeUnit.SECONDS), "Native materialization TEST Hold timed out."); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
			if (_abortFailure != AbortFailure.NONE)
			{
				// Explicit TEST fault seam in the real native owner context, not a claim about a stock producer fault.
				PlayerNativeWork.run(player(), "TEST_MATERIALIZATION_PRIMARY_BODY", () -> { _primaryBodyAdmissions.incrementAndGet(); throw _primary; });
			}
		}
		@Override public void materializeSucceeded(long profileId, int characterObjectId) { _success.incrementAndGet(); }
		@Override public void materializeAborted(long profileId, int characterObjectId)
		{
			_abort.incrementAndGet();
			// chain(Hold, background) has already called background abort before this secondary is thrown.
			if (_abortFailure == AbortFailure.ERROR) { throw (Error) _secondary; }
			if (_abortFailure == AbortFailure.RUNTIME) { throw (RuntimeException) _secondary; }
		}
		@Override public void beforeStore(long profileId, Player player) { _beforeStore.incrementAndGet(); }
		@Override public void afterStore(long profileId, Player player) { _afterStore.incrementAndGet(); }
	}

	public static void concurrent(PhantomTestContext context, PhantomMaterializationService service, long profileId, Hold hold, boolean shutdown) throws Exception
	{
		guard(context, service, hold, false);
		final var baseline = service.snapshot();
		final String key = "Q15.materializationRace." + hold._stage + (shutdown ? ".shutdown" : ".dematerialize");
		final var loadResult = new AtomicReference<PhantomMaterializationService.MaterializeResult>(); final var loadFailure = new AtomicReference<Throwable>();
		final var cleanupResult = new AtomicReference<Object>(); final var cleanupFailure = new AtomicReference<Throwable>();
		final var cleanupStarted = new CountDownLatch(1); final var cleanupFinished = new CountDownLatch(1);
		final Thread loader = thread("m1-native-materialization-load", () -> { try { loadResult.set(service.materialize(profileId)); } catch (Throwable failure) { loadFailure.set(failure); } });
		final Thread cleanup = thread("m1-native-materialization-cleanup", () ->
		{
			cleanupStarted.countDown();
			try { cleanupResult.set(shutdown ? service.shutdown() : service.dematerialize(profileId)); }
			catch (Throwable failure) { cleanupFailure.set(failure); }
			finally { cleanupFinished.countDown(); }
		});
		MonitorProbe probe = null;
		Throwable primary = null;
		try
		{
			loader.start();
			PhantomAssertions.assertTrue(hold._entered.await(3, TimeUnit.SECONDS), "INVALID native materialization race: actual native load never reached Hold.");
			final Object entry = entry(service, profileId); final PhantomMaterializedPlayer actor = actor(entry); final Player player = hold.player();
			verifyHeldIdentity(service, profileId, baseline.availablePermits(), hold, entry, actor, player);
			final boolean loaderMonitorsFree = !ownsMonitor(loader, entry) && !ownsMonitor(loader, actor);
			cleanup.start(); PhantomAssertions.assertTrue(cleanupStarted.await(1, TimeUnit.SECONDS), "Native cleanup contender did not start.");
			await(() -> booleanField(entry, "_cleanupInProgress"), 1000, "INVALID native race: contender never entered actual exact Entry cleanup.");
			probe = probeMonitors(entry, actor);
			final boolean cleanupCrossed = cleanupFinished.await(200, TimeUnit.MILLISECONDS);
			final boolean bodyCrossed = hold._world.get() != 0 || hold._admitted.get() != 0 || hold._beforeStore.get() != 0 || hold._afterStore.get() != 0;
			final boolean ownershipRetained = mapsOwn(service, profileId, player.getObjectId(), entry) && service.snapshot().availablePermits() == baseline.availablePermits() - 1 && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.PHANTOM;
			context.record(key + ".held", "loaderMonitorsFree=" + loaderMonitorsFree + " contenderMonitorsFree=" + probe.free() + " cleanupCrossed=" + cleanupCrossed + " bodyCrossed=" + bodyCrossed + " mapsPermitIdentityRetained=" + ownershipRetained);
			hold.release(); loader.join(6000); cleanup.join(10000);
			PhantomAssertions.assertTrue(!loader.isAlive() && !cleanup.isAlive() && loadFailure.get() == null && cleanupFailure.get() == null, "INVALID actual native race completion: " + loadFailure.get() + " / " + cleanupFailure.get());
			PhantomAssertions.assertTrue(loadResult.get() != null && loadResult.get().status() == ResultStatus.SUCCESS && hold._world.get() == 1 && hold._admitted.get() == 1, "INVALID native race: actual stock materialization/spawn/admission did not complete once.");
			if (shutdown)
			{
				final var result = (PhantomMaterializationService.ShutdownResult) cleanupResult.get();
				PhantomAssertions.assertTrue(result != null && result.state() == ServiceState.STOPPED && result.failedProfileIds().isEmpty(), "Native concurrent shutdown did not finish successfully.");
			}
			else { PhantomAssertions.assertTrue(cleanupResult.get() instanceof PhantomMaterializationService.DematerializeResult result && result.status() == ResultStatus.SUCCESS, "Native concurrent dematerialization did not finish successfully."); }
			PhantomAssertions.assertTrue(hold._before.get() == 1 && hold._success.get() == 1 && hold._abort.get() == 0 && hold._beforeStore.get() == 1 && hold._afterStore.get() == 1, "Native race terminal/store callbacks did not occur exactly once.");
			PhantomAssertions.assertTrue(service.find(profileId).isEmpty() && entry(service, profileId) == null && map(service, "_activeByCharacter").get(player.getObjectId()) == null
				&& service.snapshot().retainedEntries() == 0 && service.snapshot().availablePermits() == baseline.availablePermits(), "Native race retained maps or lost/reused a permit after exact completion.");
			PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == null && World.getInstance().findObject(player.getObjectId()) == null && !PlayerAutoSaveTaskManager.getInstance().contains(player)
				&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == null, "Native race retained World/autosave/identity after successful cleanup.");
			context.record(key + ".fixture", "VALID_ACTUAL_PLAYER_LOAD_WORLD_SPAWN_AND_NATIVE_STORE");
			PhantomAssertions.assertTrue(loaderMonitorsFree && probe.free(), "Q15 materialization or cleanup awaited an external callback while holding exact Entry/actor monitor.");
			PhantomAssertions.assertFalse(cleanupCrossed || bodyCrossed || !ownershipRetained, "Q15 cleanup/shutdown crossed held native materialization or released exact maps/permit/identity before completion.");
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally { finish(primary, hold, loader, cleanup, probe == null ? null : probe.thread()); }
	}

	/** Caller uses the existing guarded zero-work TEST discard; this helper never clears or reopens the failed scope. */
	public static Player primaryAbort(PhantomTestContext context, PhantomMaterializationService service, long profileId, Hold hold) throws Exception
	{
		guard(context, service, hold, true);
		final int permits = service.snapshot().availablePermits();
		final var observed = new AtomicReference<Throwable>(); final var result = new AtomicReference<PhantomMaterializationService.MaterializeResult>();
		final Thread loader = thread("m1-native-materialization-primary-error", () -> { try { result.set(service.materialize(profileId)); } catch (Throwable failure) { observed.set(failure); } });
		Throwable primary = null;
		try
		{
			loader.start(); PhantomAssertions.assertTrue(hold._entered.await(3, TimeUnit.SECONDS), "INVALID E04: actual native materialization did not reach injected fault seam.");
			final Object entry = entry(service, profileId); final var actor = actor(entry); final Player player = hold.player();
			verifyHeldIdentity(service, profileId, permits, hold, entry, actor, player);
			hold.release(); loader.join(6000);
			PhantomAssertions.assertFalse(loader.isAlive(), "E04 native Error/abort did not complete.");
			PhantomAssertions.assertTrue(result.get() == null && observed.get() == hold._primary, "E04 native materialization swallowed/masked the original injected primary Error: " + observed.get());
			PhantomAssertions.assertTrue(Arrays.asList(hold._primary.getSuppressed()).contains(hold._secondary), "E04 original native primary Error lost secondary abort Error/RuntimeException.");
			final var snapshot = service.find(profileId).orElseThrow(); final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			PhantomAssertions.assertTrue(snapshot.firstCleanupIncident() != null && snapshot.latestCleanupIncident() != null && snapshot.firstCleanupIncident().exceptionClass().equals(AssertionError.class.getName())
				&& snapshot.firstCleanupIncident().message().equals(hold._primary.getMessage()) && scope.firstNativeIncident() != null && !scope.open(), "E04 failed native scope lost original scalar incident or reopened.");
			PhantomAssertions.assertTrue(snapshot.identityLeaseRetained() && snapshot.playerRetained() && !snapshot.actionAdmissionOpen() && mapsOwn(service, profileId, player.getObjectId(), entry)
				&& service.snapshot().availablePermits() == permits - 1 && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.PHANTOM, "E04 failed native load lost exact retained actor/maps/permit/identity.");
			PhantomAssertions.assertTrue(hold._before.get() == 1 && hold._success.get() == 0 && hold._abort.get() == 1 && hold._world.get() == 0 && hold._beforeStore.get() == 0 && hold._afterStore.get() == 0, "E04 reported success/spawn/store or replayed failed native callback.");
			PhantomAssertions.assertTrue(service.snapshot().cleanupIncidents().stream().anyMatch(incident -> incident.profileId() == profileId && !incident.finished() && incident.first().equals(snapshot.firstCleanupIncident())), "E04 service lost unfinished first/latest cleanup archive.");
			PhantomAssertions.assertTrue(scope.outstanding() == 0 && scope.pendingTimers() == 0, "E04 caller TEST discard requires zero native work/producers.");
			PhantomAssertions.assertEquals(1, hold._primaryBodyAdmissions.get(), "E04 injected primary body was not admitted exactly once.");
			context.record("E04.materializationPrimary." + hold._abortFailure, "injectedAdmittedBody=true originalErrorPreserved=true abortSecondary=" + hold._secondary.getClass().getName() + " first=" + snapshot.firstCleanupIncident() + " latest=" + snapshot.latestCleanupIncident());
			hold._primaryVerified = true;
			return player;
		}
		catch (Exception | Error failure) { primary = failure; throw failure; }
		finally { finish(primary, hold, loader); }
	}

	/** Local negative TEST receipt. It cannot authorize production recovery or another fixture's cleanup. */
	public static final class DiscardReceipt
	{
		private final WeakReference<PhantomMaterializationService> _service;
		private final WeakReference<Object> _entry;
		private final WeakReference<PhantomMaterializedPlayer> _actor;
		private final WeakReference<Player> _player;
		private final WeakReference<PhantomNativeWorkScope> _scope;
		private final WeakReference<PhantomIdentityLeaseRegistry.Lease> _identity;
		private final WeakReference<Hold> _hold;
		private final long _profileId, _epoch, _identityToken, _firstNativeSequence, _firstCleanupSequence;
		private final int _objectId, _maximumMaterialized;
		private boolean _disposed, _verified;
		private DiscardReceipt(PhantomMaterializationService service, long profileId, Hold hold, Object entry, PhantomMaterializedPlayer actor, Player player,
			PhantomNativeWorkScope scope, PhantomIdentityLeaseRegistry.Lease identity)
		{
			_service = new WeakReference<>(service); _entry = new WeakReference<>(entry); _actor = new WeakReference<>(actor); _player = new WeakReference<>(player);
			_scope = new WeakReference<>(scope); _identity = new WeakReference<>(identity); _hold = new WeakReference<>(hold);
			_profileId = profileId; _objectId = player.getObjectId(); _epoch = scope.epoch(); _identityToken = identity.token();
			_maximumMaterialized = service.snapshot().maximumMaterialized();
			_firstNativeSequence = scope.firstNativeIncident().sequence(); _firstCleanupSequence = actor.snapshot().firstCleanupIncident().sequence();
		}
		public long profileId() { return _profileId; }
		public int objectId() { return _objectId; }
		public long epoch() { return _epoch; }
	}

	/** Only after primaryAbort attests the exact early admitted fault; leaves all service maps/permits untouched. */
	public static DiscardReceipt discardExpectedFailure(PhantomTestContext context, PhantomMaterializationService service, long profileId, Hold hold) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "E04 discard requires the original guarded TEST environment.");
		PhantomAssertions.assertTrue(hold != null && hold._stage == Stage.AFTER_PLAYER_LOAD && hold._abortFailure != AbortFailure.NONE && hold._primaryVerified
			&& hold._profileId == profileId && hold._entered.getCount() == 0 && hold._release.getCount() == 0, "E04 discard lacks the completed exact primaryAbort proof.");
		final Object entry = entry(service, profileId); final PhantomMaterializedPlayer actor = actor(entry); final Player player = hold.player();
		PhantomAssertions.assertTrue(player != null && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope, "E04 discard lost the original native Player/owner.");
		final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
		final var identity = (PhantomIdentityLeaseRegistry.Lease) field(actor, "_identityLease");
		PhantomAssertions.assertTrue(scope.firstNativeIncident() != null && actor.snapshot().firstCleanupIncident() != null && identity != null, "E04 discard lost its original failure/identity evidence.");
		final var receipt = new DiscardReceipt(service, profileId, hold, entry, actor, player, scope, identity);
		assertExpectedRetention(context, service, profileId, receipt, false);
		PhantomAssertions.assertTrue(service.snapshot().state() == ServiceState.RUNNING && hold._discardClaimed.compareAndSet(false, true), "E04 discard is single-use on the original running TEST service.");
		// Existing negative native TEST seam: dispose the body, never reset the failed scope or service Entry.
		player.stopAllTasks();
		PhantomAssertions.assertTrue(player.deleteMe(), "E04 exact native TEST Player.deleteMe did not finish.");
		player.stopAllTasks(); player.detachNativeWorkOwner(scope); identity.close();
		receipt._disposed = true;
		assertExpectedRetention(context, service, profileId, receipt, true);
		context.record("E04.expectedDiscard.body." + hold._abortFailure, "profile=" + profileId + " object=" + receipt.objectId() + " epoch=" + receipt.epoch()
			+ " nativeBodyDisposed=true serviceEntryQuarantined=true permitStillHeld=true noSuccessfulStore=true");
		return receipt;
	}

	/** Caller performs original shutdown first. Only this exact expected negative may bypass normal fixture restoration refusal. */
	public static void verifyExpectedDiscard(PhantomTestContext context, PhantomMaterializationService service, long profileId, DiscardReceipt receipt,
		PhantomMaterializationService.ShutdownResult shutdown) throws Exception
	{
		PhantomAssertions.assertTrue(receipt != null && receipt._disposed, "E04 expected-negative receipt was not completed by its native TEST discard.");
		synchronized (receipt)
		{
			PhantomAssertions.assertFalse(receipt._verified, "E04 expected-negative receipt cannot be reused.");
			PhantomAssertions.assertTrue(shutdown != null && shutdown.state() == ServiceState.FAILED && shutdown.failedProfileIds().equals(List.of(profileId))
				&& service.snapshot().state() == ServiceState.FAILED, "E04 quarantine requires exact FAILED shutdown/profile retention, never SUCCESS or another failure.");
			assertExpectedRetention(context, service, profileId, receipt, true);
			receipt._verified = true;
		}
		context.record("E04.expectedDiscard.verified", "EXPECTED_FAIL_CLOSED_TEST_DISCARD profile=" + profileId + " object=" + receipt.objectId() + " epoch=" + receipt.epoch()
			+ " shutdown=FAILED failedIds=[" + profileId + "] retainedEntries=1 permitStillHeld=true worldAutosaveIdentityAbsent=true originalIncidentRetained=true noNewLifetime=true");
	}

	private static void assertExpectedRetention(PhantomTestContext context, PhantomMaterializationService service, long profileId, DiscardReceipt receipt, boolean disposed) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "E04 quarantine is guarded TEST-only.");
		PhantomAssertions.assertTrue(receipt._service.get() == service && receipt._profileId == profileId, "E04 quarantine receipt belongs to another exact service/profile.");
		final Object entry = required(receipt._entry, "Entry"); final var actor = required(receipt._actor, "actor"); final var player = required(receipt._player, "Player");
		final var scope = required(receipt._scope, "scope"); final var identity = required(receipt._identity, "identity"); final var hold = required(receipt._hold, "Hold");
		final var serviceState = service.snapshot(); final var snapshot = service.find(profileId).orElseThrow(); final var actorState = actor.snapshot();
		PhantomAssertions.assertTrue(entry(service, profileId) == entry && actor(entry) == actor && actor.getPlayer() == player && hold.player() == player
			&& mapsOwn(service, profileId, receipt._objectId, entry) && map(service, "_activeByProfile").size() == 1 && map(service, "_activeByCharacter").size() == 1
			&& booleanField(entry, "_permitHeld") && !booleanField(entry, "_released") && !booleanField(entry, "_countedActive") && !booleanField(entry, "_cleanupInProgress")
			&& !booleanField(entry, "_retryRegistered") && (int) field(entry, "_automaticCleanupAttempts") == 0, "E04 quarantine changed exact Entry/maps/permit or left cleanup/retry running.");
		PhantomAssertions.assertTrue(field(entry, "_materializationThread") == null && ((CountDownLatch) field(entry, "_materializationCompletion")).getCount() == 0
			&& field(actor, "_materializationThread") == null && ((CountDownLatch) field(actor, "_materializationCompletion")).getCount() == 0
			&& !booleanField(actor, "_cleanupStarted") && !booleanField(actor, "_cleanupFinished"), "E04 materialization/cleanup body is unfinished or incorrectly reported complete.");
		PhantomAssertions.assertTrue(serviceState.maximumMaterialized() == receipt._maximumMaterialized && serviceState.retainedEntries() == 1
			&& serviceState.availablePermits() == receipt._maximumMaterialized - 1 && serviceState.materializations().size() == 1
			&& snapshot.profileId() == profileId && snapshot.characterObjectId() == receipt._objectId && snapshot.materializedAtNanos() == receipt._epoch && snapshot.dematerializedAtNanos() == 0
			&& snapshot.state() == PhantomMaterializedPlayer.State.FAILED && actorState.state() == PhantomMaterializedPlayer.State.FAILED
			&& snapshot.playerRetained() && snapshot.identityLeaseRetained() && !snapshot.actionAdmissionOpen() && !snapshot.worldPresent() && snapshot.admittedActionCount() == 0,
			"E04 quarantine lost failure retention, changed epoch/body or released/reused the original permit.");
		PhantomAssertions.assertTrue(player.getObjectId() == receipt._objectId && scope.player() == player && scope.epoch() == receipt._epoch
			&& scope.evidence() != null && scope.evidence().matches(receipt._objectId, receipt._epoch)
			&& field(actor, "_nativeWork") == scope && field(actor, "_identityLease") == identity && field(scope, "_identity") == identity
			&& identity.objectId() == receipt._objectId && identity.ownerKind() == OwnerKind.PHANTOM && identity.token() == receipt._identityToken,
			"E04 quarantine changed captured native owner/identity token.");
		PhantomAssertions.assertTrue(!scope.open() && booleanField(scope, "_permanentSeal") && !((String) field(scope, "_failure")).isEmpty()
			&& scope.outstanding() == 0 && scope.pendingTimers() == 0 && PlayerNativeWork.current(scope) == null,
			"E04 quarantine reset/reopened the failed scope or retained native work/producers.");
		PhantomAssertions.assertTrue(hold._primaryVerified && hold._stage == Stage.AFTER_PLAYER_LOAD && hold._abortFailure != AbortFailure.NONE && hold._profileId == profileId
			&& hold._before.get() == 1 && hold._abort.get() == 1 && hold._primaryBodyAdmissions.get() == 1 && hold._success.get() == 0 && hold._world.get() == 0 && hold._admitted.get() == 0
			&& hold._beforeStore.get() == 0 && hold._afterStore.get() == 0 && hold._storeOperations.get() == 0 && hold._nativeStores.get() == 0
			&& Arrays.asList(hold._primary.getSuppressed()).contains(hold._secondary), "E04 quarantine lacks the exact admitted primary/secondary or crossed original spawn/store/success.");
		final var nativeIncident = scope.firstNativeIncident(); final var first = snapshot.firstCleanupIncident();
		PhantomAssertions.assertTrue(nativeIncident != null && nativeIncident.sequence() == receipt._firstNativeSequence && nativeIncident.exceptionClass().equals(AssertionError.class.getName())
			&& nativeIncident.message().equals("TEST_MATERIALIZATION_NATIVE_PRIMARY") && first != null && first.sequence() == receipt._firstCleanupSequence
			&& first.exceptionClass().equals(AssertionError.class.getName()) && first.message().equals("TEST_MATERIALIZATION_NATIVE_PRIMARY") && snapshot.latestCleanupIncident() != null
			&& serviceState.cleanupIncidents().stream().anyMatch(incident -> incident.profileId() == profileId && !incident.finished() && incident.first().equals(first)),
			"E04 quarantine erased/changed original native scalar failure or unfinished service archive.");
		PhantomAssertions.assertTrue(player.getClient() == null && !player.hasHeadlessOutboundSession() && !player.hasOwnedStoreBoundary() && !player.hasPendingOwnedStore()
			&& field(player, "_phantomNativeActionAdmission") == null && field(player, "_nativeLoadTicket") == null && field(player, "_nativeLoadContext") == null
			&& !actorState.identityAttached() && !actorState.outboundAttached() && field(actor, "_nativeActionAdmission") == null && field(actor, "_outboundAttachment") == null,
			"E04 discard is restricted to the early lifetime with no outbound/admission/owned STORE boundary.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(receipt._objectId) == null && World.getInstance().findObject(receipt._objectId) == null,
			"E04 quarantine found an original/new/foreign native World actor.");
		try (var action = service.tryAcquireAction(profileId).orElse(null)) { PhantomAssertions.assertTrue(action == null, "E04 failed/quarantined Entry reopened action admission."); }
		final var registry = PhantomIdentityLeaseRegistry.getInstance(); final var owner = registry.getOwnerSnapshot(receipt._objectId);
		if (disposed)
		{
			PhantomAssertions.assertTrue(identity.isClosed() && owner == null && registry.getOwnerKind(receipt._objectId) == null && player.getNativeWorkOwner() == null && !scope.isCurrent()
				&& !PlayerAutoSaveTaskManager.getInstance().contains(player) && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(receipt._objectId),
				"E04 stock TEST discard retained autosave/identity/owner or allowed another lifetime.");
		}
		else
		{
			PhantomAssertions.assertTrue(!identity.isClosed() && owner != null && owner.ownerKind() == OwnerKind.PHANTOM && owner.token() == receipt._identityToken
				&& player.getNativeWorkOwner() == scope && scope.isCurrent() && PlayerAutoSaveTaskManager.getInstance().contains(player)
				&& !PlayerAutoSaveTaskManager.getInstance().containsOtherObjectId(receipt._objectId, player), "E04 pre-discard exact identity/autosave owner is missing or foreign.");
		}
	}

	private static <T> T required(WeakReference<T> reference, String name)
	{
		final T value = reference.get(); PhantomAssertions.assertTrue(value != null, "E04 local quarantine receipt lost its exact " + name + " witness."); return value;
	}

	private static void guard(PhantomTestContext context, PhantomMaterializationService service, Hold hold, boolean failure)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Materialization races require guarded headless TEST.");
		PhantomAssertions.assertTrue(service.snapshot().state() == ServiceState.RUNNING && service.snapshot().retainedEntries() == 0 && service.snapshot().availablePermits() == service.snapshot().maximumMaterialized(), "INVALID native materialization race: fresh running empty service required.");
		PhantomAssertions.assertTrue(hold._before.get() == 0 && (hold._abortFailure != AbortFailure.NONE) == failure, "INVALID native materialization race: fresh Hold fault mode differs.");
	}
	private static void verifyHeldIdentity(PhantomMaterializationService service, long profileId, int permits, Hold hold, Object entry, PhantomMaterializedPlayer actor, Player player) throws Exception
	{
		PhantomAssertions.assertTrue(entry != null && player != null && actor.getPlayer() == player && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope && player.getNativeWorkOwner().isCurrent(), "INVALID race: actual Player.load/exact captured native owner missing.");
		PhantomAssertions.assertTrue(mapsOwn(service, profileId, player.getObjectId(), entry) && service.snapshot().availablePermits() == permits - 1 && service.find(profileId).orElseThrow().identityLeaseRetained()
			&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.PHANTOM && PlayerAutoSaveTaskManager.getInstance().contains(player), "INVALID race: held actual Player load lacks exact maps/permit/identity/autosave.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(player.getObjectId()) == null && World.getInstance().findObject(player.getObjectId()) == null && !service.find(profileId).orElseThrow().actionAdmissionOpen()
			&& hold._world.get() == 0 && hold._beforeStore.get() == 0, "INVALID race: requested pre-World Hold was reached after stock spawn/admission/store.");
		PhantomAssertions.assertEquals(1L, ((CountDownLatch) field(entry, "_materializationCompletion")).getCount(), "INVALID race: exact materialization Entry completion was already published.");
	}
	private static MonitorProbe probeMonitors(Object entry, Object actor) throws InterruptedException
	{
		final var acquired = new CountDownLatch(1);
		final Thread probe = thread("m1-native-materialization-monitor-probe", () -> { synchronized (entry) { } synchronized (actor) { } acquired.countDown(); });
		probe.start(); return new MonitorProbe(probe, acquired.await(300, TimeUnit.MILLISECONDS));
	}
	private static boolean ownsMonitor(Thread thread, Object monitor)
	{
		final var info = ManagementFactory.getThreadMXBean().getThreadInfo(new long[] { thread.threadId() }, true, false)[0];
		return info != null && Arrays.stream(info.getLockedMonitors()).anyMatch(held -> held.getIdentityHashCode() == System.identityHashCode(monitor) && held.getClassName().equals(monitor.getClass().getName()));
	}
	private static Object entry(PhantomMaterializationService service, long profileId) throws Exception { return map(service, "_activeByProfile").get(profileId); }
	private static PhantomMaterializedPlayer actor(Object entry) throws Exception { return (PhantomMaterializedPlayer) field(entry, "_materializedPlayer"); }
	private static Map<?, ?> map(PhantomMaterializationService service, String name) throws Exception { return (Map<?, ?>) field(service, name); }
	private static boolean mapsOwn(PhantomMaterializationService service, long profileId, int objectId, Object entry) throws Exception { return map(service, "_activeByProfile").get(profileId) == entry && map(service, "_activeByCharacter").get(objectId) == entry; }
	private static Object field(Object target, String name) throws Exception { final Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
	private static boolean booleanField(Object target, String name) { try { return (boolean) field(target, name); } catch (Exception failure) { throw new AssertionError(failure); } }
	private static Thread thread(String name, Runnable action) { return Thread.ofPlatform().daemon().name(name).unstarted(action); }
	private static void await(BooleanSupplier condition, long milliseconds, String message) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); }
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}
	private static void finish(Throwable primary, Hold hold, Thread... threads) throws Exception
	{
		hold.release();
		try
		{
			for (Thread thread : threads) { if (thread != null && thread.getState() != Thread.State.NEW) { thread.join(10000); } }
			PhantomAssertions.assertFalse(Arrays.stream(threads).anyMatch(thread -> thread != null && thread.isAlive()), "Native materialization fixture retained load/cleanup/probe thread.");
		}
		catch (Exception | Error failure) { if (primary == null) { throw failure; } primary.addSuppressed(failure); }
	}
	private record MonitorProbe(Thread thread, boolean free) { }
}
