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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ServiceState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.Facts;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;

public final class PhantomCombatActionOwnershipSuite implements PhantomTestSuite
{
	private static final long WAIT_MILLIS = 5000;
	private static final SelectedSkill MAGIC_SKILL = new SelectedSkill(1339, 1);

	@Override
	public String id()
	{
		return "combat-action-ownership";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-explicit-dispatch-rejection", _ -> explicitDispatchRejection());
		registry.add("02-null-production-handle-rejection", _ -> nullProductionHandleRejection());
		registry.add("03-throwing-dispatch-rejection", _ -> throwingDispatchRejection());
		registry.add("04-stop-wins-claim-dispatch-boundary", _ -> stopWinsClaimDispatchBoundary());
		registry.add("05-scheduled-worker-cancelled-before-start", _ -> scheduledWorkerCancelledBeforeStart());
		registry.add("06-inline-dispatch-no-deadlock", _ -> inlineDispatchNoDeadlock());
		registry.add("07-stale-worker-cannot-release-new-claim", _ -> staleWorkerCannotReleaseNewClaim());
		registry.add("08-backend-error-releases-worker", _ -> backendErrorReleasesWorker());
		registry.add("09-no-dispatch-start-after-stopping", _ -> noDispatchStartAfterStopping());
		registry.add("10-cleanup-throw-retains-lease", _ -> cleanupThrowRetainsLease());
		registry.add("11-cleanup-retry-releases-once", _ -> cleanupRetryReleasesOnce());
		registry.add("12-cleanup-exhaustion-fails-visible", _ -> cleanupExhaustionFailsVisible());
		registry.add("13-stop-blocked-by-failed-cleanup", _ -> stopBlockedByFailedCleanup());
		registry.add("14-cancel-pickup-uses-exact-object", _ -> cancelPickupUsesExactObject());
		registry.add("15-victory-clears-exact-dead-target", _ -> victoryClearsExactDeadTarget());
		registry.add("16-stale-cleanup-preserves-foreign-actions", _ -> staleCleanupPreservesForeignActions());
		registry.add("17-explicit-cancel-wait-is-bounded", _ -> explicitCancelWaitIsBounded());
		registry.add("18-actor-acquisition-positive-proof", _ -> lootResult(LootObservation.ACQUIRED_BY_ACTOR, PhantomCombatResult.VICTORY_LOOTED));
		registry.add("19-other-player-pickup-not-acquired", _ -> lootResult(LootObservation.LOST_WITHOUT_ACQUISITION, PhantomCombatResult.VICTORY_LOOT_BLOCKED));
		registry.add("20-despawn-not-acquired", _ -> lootResult(LootObservation.LOST_WITHOUT_ACQUISITION, PhantomCombatResult.VICTORY_LOOT_BLOCKED));
		registry.add("21-out-of-radius-not-acquired", _ -> lootResult(LootObservation.INELIGIBLE, PhantomCombatResult.VICTORY_LOOT_BLOCKED));
		registry.add("22-partial-requires-positive-acquisition", _ -> partialRequiresPositiveAcquisition());
		registry.add("23-positive-one-target-skill-rejected", _ -> positiveOneTargetSkillRejected());
		registry.add("24-offensive-one-target-skill-accepted", _ -> offensiveOneTargetSkillAccepted());
		registry.add("25-exact-session-mode-revalidated", _ -> exactSessionModeRevalidated());
		registry.add("26-special-pvp-suicide-skills-rejected", _ -> specialPvpSuicideSkillsRejected());
		registry.add("27-cancelled-respawn-token-skips-actor", _ -> cancelledRespawnTokenSkipsActor());
		registry.add("28-token-cancelled-during-acquire", _ -> tokenCancelledDuringAcquire());
		registry.add("29-active-session-rejects-respawn", _ -> activeSessionRejectsRespawn());
		registry.add("30-cleanup-pending-retries-respawn", _ -> cleanupPendingRetriesRespawn());
		registry.add("31-stopping-rejects-new-respawn", _ -> stoppingRejectsNewRespawn());
		registry.add("32-in-flight-respawn-is-stop-barrier", _ -> inFlightRespawnIsStopBarrier());
		registry.add("33-current-token-idle-respawn-succeeds", _ -> currentTokenIdleRespawnSucceeds());
	}

	private static void explicitDispatchRejection()
	{
		final Fixture fixture = fixture((_, _) -> DispatchResult.rejected());
		fixture.start();
		assertDispatchFailureReleased(fixture);
	}

	private static void nullProductionHandleRejection()
	{
		final Fixture fixture = fixture(PhantomCombatService.scheduledDispatcher((_, _) -> null));
		fixture.start();
		assertDispatchFailureReleased(fixture);
	}

	private static void throwingDispatchRejection()
	{
		final Fixture fixture = fixture((_, _) ->
		{
			throw new IllegalStateException("injected dispatch failure");
		});
		fixture.start();
		assertDispatchFailureReleased(fixture);
	}

	private static void assertDispatchFailureReleased(Fixture fixture)
	{
		PhantomAssertions.assertEquals(PhantomCombatResult.BACKEND_FAILURE, fixture.service.find(1).orElseThrow().result(), "Rejected dispatch did not terminalize its session.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Rejected dispatch retained worker ownership.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "Rejected dispatch retained actor ownership.");
		PhantomAssertions.assertEquals(1L, fixture.service.metrics().dispatchFailures(), "Rejected dispatch metric changed.");
		fixture.stop();
	}

	private static void stopWinsClaimDispatchBoundary() throws Exception
	{
		final BlockingDispatcher dispatcher = new BlockingDispatcher();
		final Fixture fixture = fixture(dispatcher);
		final AtomicReference<Throwable> startFailure = new AtomicReference<>();
		final Thread start = new Thread(() ->
		{
			try
			{
				fixture.start();
			}
			catch (Throwable throwable)
			{
				startFailure.set(throwable);
			}
		}, "Task012a-dispatch-start");
		final Thread stop = new Thread(fixture.service::beginStop, "Task012a-dispatch-stop");
		start.start();
		await(dispatcher.entered, "Dispatch did not enter the shared gate.");
		stop.start();
		dispatcher.release.countDown();
		start.join(WAIT_MILLIS);
		stop.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(start.isAlive() || stop.isAlive(), "Dispatch/STOPPING gate deadlocked.");
		if (startFailure.get() != null)
		{
			throw new AssertionError("Combat start failed.", startFailure.get());
		}
		PhantomAssertions.assertEquals(0, dispatcher.runs, "Worker began after STOPPING won the shared gate.");
		PhantomAssertions.assertEquals(DispatchState.CANCELLED, dispatcher.handle.state(), "Accepted scheduled worker was not cancelled.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Dispatch-gate fixture did not quiesce.");
	}

	private static void scheduledWorkerCancelledBeforeStart()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final Fixture fixture = fixture(dispatcher);
		fixture.start();
		final ManualHandle handle = dispatcher.pendingHandle;
		fixture.service.beginStop();
		PhantomAssertions.assertEquals(DispatchState.CANCELLED, handle.state(), "Scheduled worker handle was not cancelled.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Cancelled scheduled worker retained its claim.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Cancelled scheduled worker blocked stop.");
	}

	private static void inlineDispatchNoDeadlock()
	{
		final FakeLease lease = new FakeLease();
		lease.actor = deadActor();
		final InlineDispatcher dispatcher = new InlineDispatcher();
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		PhantomAssertions.assertEquals(1, dispatcher.runs, "Inline worker did not run exactly once.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Inline worker double-owned its claim.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "Inline terminal cleanup retained a lease.");
		fixture.stop();
	}

	private static void staleWorkerCannotReleaseNewClaim()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final Fixture fixture = fixture(dispatcher);
		fixture.start();
		dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().currentWorkers(), "Active session did not publish its next exact claim.");
		dispatcher.runStale();
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().currentWorkers(), "Stale worker released the newer exact claim.");
		fixture.stop();
	}

	private static void backendErrorReleasesWorker()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		lease.actorFailure = new AssertionError("injected backend error");
		dispatcher.runNext();
		PhantomAssertions.assertEquals(PhantomCombatResult.BACKEND_FAILURE, fixture.service.find(1).orElseThrow().result(), "Backend Error did not terminalize the session.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Backend Error stranded the worker.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "Backend Error stranded the actor lease.");
		fixture.stop();
	}

	private static void noDispatchStartAfterStopping()
	{
		final RunningBeforePulseDispatcher dispatcher = new RunningBeforePulseDispatcher();
		final FakeLease lease = new FakeLease();
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		lease.actorSnapshotCalls = 0;
		fixture.service.beginStop();
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().currentWorkers(), "RUNNING callback disappeared before exact stop reconciliation.");
		dispatcher.runAfterStop();
		PhantomAssertions.assertEquals(0, lease.actorSnapshotCalls, "Callback invoked combat backend after STOPPING.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "STOPPING callback retained its exact worker claim.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "STOPPING fixture retained hidden ownership.");
	}

	private static void cleanupThrowRetainsLease()
	{
		final CleanupFixture cleanup = cleanupFixture(1);
		cleanup.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, cleanup.fixture.service.snapshot().actorLeases(), "Cleanup failure released the actor lease.");
		PhantomAssertions.assertEquals(0, cleanup.lease.closeCount, "Cleanup failure closed the actor lease.");
		PhantomAssertions.assertTrue(cleanup.fixture.service.consumeTerminal(1).isEmpty(), "Cleanup-pending terminal result was consumed.");
		PhantomAssertions.assertEquals(1L, cleanup.fixture.service.metrics().cleanupFailures(), "Cleanup failure was not recorded.");
		cleanup.dispatcher.runNext();
		cleanup.fixture.stop();
	}

	private static void cleanupRetryReleasesOnce()
	{
		final CleanupFixture cleanup = cleanupFixture(1);
		cleanup.dispatcher.runNext();
		cleanup.dispatcher.runNext();
		PhantomAssertions.assertEquals(2, cleanup.lease.cleanupCalls, "Cleanup retry count changed.");
		PhantomAssertions.assertEquals(1, cleanup.lease.closeCount, "Successful retry did not release exactly once.");
		PhantomAssertions.assertEquals(0, cleanup.fixture.service.snapshot().actorLeases(), "Successful retry retained ownership.");
		PhantomAssertions.assertTrue(cleanup.fixture.service.consumeTerminal(1).isPresent(), "Clean terminal result was not consumable.");
		cleanup.fixture.stop();
	}

	private static void cleanupExhaustionFailsVisible()
	{
		final CleanupFixture cleanup = cleanupFixture(Integer.MAX_VALUE);
		cleanup.dispatcher.runNext();
		cleanup.dispatcher.runNext();
		cleanup.dispatcher.runNext();
		PhantomAssertions.assertEquals(ServiceState.FAILED, cleanup.fixture.service.snapshot().state(), "Cleanup exhaustion did not fail the service.");
		PhantomAssertions.assertEquals(3, cleanup.lease.cleanupCalls, "Automatic cleanup retry bound changed.");
		PhantomAssertions.assertEquals(1, cleanup.fixture.service.snapshot().actorLeases(), "Exhausted cleanup hid the retained lease.");
		PhantomAssertions.assertEquals(0, cleanup.lease.closeCount, "Exhausted cleanup closed the retained lease.");
		cleanup.lease.cleanupFailuresRemaining = 0;
		PhantomAssertions.assertTrue(cleanup.fixture.service.retryFailedCleanup(), "Explicit cleanup retry did not recover the lease.");
		cleanup.fixture.stop();
	}

	private static void stopBlockedByFailedCleanup()
	{
		final CleanupFixture cleanup = cleanupFixture(Integer.MAX_VALUE);
		cleanup.dispatcher.runNext();
		cleanup.dispatcher.runNext();
		cleanup.dispatcher.runNext();
		cleanup.fixture.service.beginStop();
		PhantomAssertions.assertFalse(cleanup.fixture.service.finishStop(), "Stop completed while cleanup ownership remained.");
		cleanup.lease.cleanupFailuresRemaining = 0;
		PhantomAssertions.assertTrue(cleanup.fixture.service.retryFailedCleanup(), "Explicit stop cleanup retry failed.");
		PhantomAssertions.assertTrue(cleanup.fixture.service.finishStop(), "Stop did not complete after cleanup reconciliation.");
	}

	private static void cancelPickupUsesExactObject()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		lease.loot = List.of(new LootCandidate(500, 57, 1, 0));
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.startLoot();
		lease.target = deadTarget();
		dispatcher.runNext();
		dispatcher.runNext();
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, fixture.service.cancel(1), "PICK_UP cancellation did not complete.");
		PhantomAssertions.assertEquals(500, lease.cleanedAction.pickupObjectId(), "PICK_UP cleanup lost the exact world object.");
		PhantomAssertions.assertEquals(ActionKind.IDLE, lease.actionKind, "Exact owned PICK_UP survived cleanup.");
		fixture.stop();
	}

	private static void victoryClearsExactDeadTarget()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		lease.actionKind = ActionKind.ATTACK;
		lease.actionTargetId = 100;
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		lease.target = deadTarget();
		dispatcher.runNext();
		PhantomAssertions.assertEquals(100, lease.cleanedAction.combatTargetObjectId(), "Victory cleanup lost the exact dead target.");
		PhantomAssertions.assertEquals(ActionKind.IDLE, lease.actionKind, "Exact dead target action survived victory cleanup.");
		fixture.stop();
	}

	private static void staleCleanupPreservesForeignActions()
	{
		final FakeLease lease = new FakeLease();
		final PhantomOwnedAction stale = new PhantomOwnedAction(1, 100, MAGIC_SKILL, 500);
		lease.actionKind = ActionKind.ATTACK;
		lease.actionTargetId = 101;
		lease.cancelOwnedAction(stale);
		PhantomAssertions.assertEquals(ActionKind.ATTACK, lease.actionKind, "Stale cleanup cancelled a foreign ATTACK.");
		lease.actionKind = ActionKind.CAST;
		lease.actionTargetId = 100;
		lease.actionSkill = new SelectedSkill(1340, 1);
		lease.cancelOwnedAction(stale);
		PhantomAssertions.assertEquals(ActionKind.CAST, lease.actionKind, "Stale cleanup cancelled a foreign CAST.");
		lease.actionKind = ActionKind.PICK_UP;
		lease.actionTargetId = 501;
		lease.cancelOwnedAction(stale);
		PhantomAssertions.assertEquals(ActionKind.PICK_UP, lease.actionKind, "Stale cleanup cancelled a foreign PICK_UP.");
	}

	private static void explicitCancelWaitIsBounded() throws Exception
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		lease.actorEntered = new CountDownLatch(1);
		lease.actorRelease = new CountDownLatch(1);
		final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
		final Thread worker = new Thread(() ->
		{
			try
			{
				dispatcher.runNext();
			}
			catch (Throwable throwable)
			{
				workerFailure.set(throwable);
			}
		}, "Task012a-bounded-worker");
		worker.start();
		await(lease.actorEntered, "Worker did not enter the bounded cancellation fixture.");
		final AtomicReference<CancelStatus> status = new AtomicReference<>();
		final AtomicLong elapsedMillis = new AtomicLong();
		final Thread cancel = new Thread(() ->
		{
			final long started = System.nanoTime();
			status.set(fixture.service.cancel(1));
			elapsedMillis.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
		}, "Task012a-bounded-cancel");
		cancel.start();
		Thread.sleep(50);
		lease.actorRelease.countDown();
		worker.join(WAIT_MILLIS);
		cancel.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(worker.isAlive() || cancel.isAlive(), "Explicit cancellation exceeded its bounded wait.");
		if (workerFailure.get() != null)
		{
			throw new AssertionError("Bounded worker failed.", workerFailure.get());
		}
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, status.get(), "Bounded cancellation did not reconcile cleanup.");
		PhantomAssertions.assertTrue(elapsedMillis.get() < WAIT_MILLIS, "Explicit cancellation exceeded the fixed wait bound.");
		fixture.stop();
	}

	private static void lootResult(LootObservation observation, PhantomCombatResult expected)
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		lease.loot = List.of(new LootCandidate(500, 57, 1, 0));
		lease.lootObservations.put(500, observation);
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.startLoot();
		lease.target = deadTarget();
		runUntilTerminal(fixture, dispatcher, 5);
		PhantomAssertions.assertEquals(expected, fixture.service.find(1).orElseThrow().result(), "Loot evidence produced an incorrect result.");
		fixture.stop();
	}

	private static void partialRequiresPositiveAcquisition()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		lease.loot = List.of(new LootCandidate(500, 57, 1, 0), new LootCandidate(501, 57, 1, 0));
		lease.lootObservations.put(500, LootObservation.ACQUIRED_BY_ACTOR);
		lease.lootObservations.put(501, LootObservation.LOST_WITHOUT_ACQUISITION);
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.startLoot();
		lease.target = deadTarget();
		runUntilTerminal(fixture, dispatcher, 8);
		PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY_LOOT_PARTIAL, fixture.service.find(1).orElseThrow().result(), "Partial loot did not require positive actor acquisition.");
		fixture.stop();
	}

	private static void positiveOneTargetSkillRejected()
	{
		PhantomAssertions.assertFalse(PhantomCombatSkillSafety.supports(facts(false, true, false, false, false), PhantomCombatMode.RANGED_MAGIC), "Positive one-target skill was accepted.");
	}

	private static void offensiveOneTargetSkillAccepted()
	{
		PhantomAssertions.assertTrue(PhantomCombatSkillSafety.supports(facts(true, true, false, false, false), PhantomCombatMode.RANGED_MAGIC), "Hostile one-target magic skill was rejected.");
	}

	private static void exactSessionModeRevalidated()
	{
		final Facts physical = facts(true, false, false, false, false);
		PhantomAssertions.assertFalse(PhantomCombatSkillSafety.supports(physical, PhantomCombatMode.RANGED_MAGIC), "Physical skill passed magic mode validation.");
		PhantomAssertions.assertTrue(PhantomCombatSkillSafety.supports(physical, PhantomCombatMode.RANGED_PHYSICAL), "Physical skill failed its exact mode validation.");

		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.startMagic();
		dispatcher.runNext();
		PhantomAssertions.assertEquals(PhantomCombatMode.RANGED_MAGIC, lease.castMode, "CAST did not revalidate the exact session mode.");
		fixture.stop();
	}

	private static void specialPvpSuicideSkillsRejected()
	{
		PhantomAssertions.assertFalse(PhantomCombatSkillSafety.supports(facts(true, true, true, false, false), PhantomCombatMode.RANGED_MAGIC), "PvP-only skill was accepted.");
		PhantomAssertions.assertFalse(PhantomCombatSkillSafety.supports(facts(true, true, false, true, false), PhantomCombatMode.RANGED_MAGIC), "Suicide skill was accepted.");
		PhantomAssertions.assertFalse(PhantomCombatSkillSafety.supports(facts(true, true, false, false, true), PhantomCombatMode.RANGED_MAGIC), "Special skill was accepted.");
	}

	private static Facts facts(boolean negative, boolean magic, boolean pvpOnly, boolean suicide, boolean special)
	{
		return new Facts(true, false, false, true, negative, !magic, magic, pvpOnly, suicide, special, false, false, false, false);
	}

	private static void cancelledRespawnTokenSkipsActor()
	{
		final Fixture fixture = fixture(new ManualDispatcher());
		fixture.cancelled.set(true);
		PhantomAssertions.assertEquals(RespawnOutcome.CANCELLED, fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get)), "Cancelled respawn token was not rejected.");
		PhantomAssertions.assertEquals(0, fixture.backend.acquireCalls, "Cancelled respawn token acquired an actor.");
		fixture.stop();
	}

	private static void tokenCancelledDuringAcquire() throws Exception
	{
		final Fixture fixture = fixture(new ManualDispatcher());
		fixture.backend.acquireEntered = new CountDownLatch(1);
		fixture.backend.acquireRelease = new CountDownLatch(1);
		final AtomicReference<RespawnOutcome> outcome = new AtomicReference<>();
		final Thread respawn = new Thread(() -> outcome.set(fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get))), "Task012a-respawn-token");
		respawn.start();
		await(fixture.backend.acquireEntered, "Respawn did not enter actor acquisition.");
		fixture.cancelled.set(true);
		fixture.backend.acquireRelease.countDown();
		respawn.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(respawn.isAlive(), "Cancelled respawn acquisition did not reconcile.");
		PhantomAssertions.assertEquals(RespawnOutcome.CANCELLED, outcome.get(), "Mid-acquisition token cancellation changed.");
		PhantomAssertions.assertEquals(0, fixture.lease.respawnCalls, "Cancelled acquisition performed a respawn side effect.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Cancelled acquisition leaked its temporary actor lease.");
		fixture.stop();
	}

	private static void activeSessionRejectsRespawn()
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final Fixture fixture = fixture(dispatcher);
		fixture.start();
		PhantomAssertions.assertEquals(RespawnOutcome.RETRY, fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get)), "Active combat session allowed respawn.");
		PhantomAssertions.assertEquals(0, fixture.lease.respawnCalls, "Active combat session produced a respawn side effect.");
		fixture.stop();
	}

	private static void cleanupPendingRetriesRespawn()
	{
		final CleanupFixture cleanup = cleanupFixture(1);
		cleanup.dispatcher.runNext();
		PhantomAssertions.assertEquals(RespawnOutcome.RETRY, cleanup.fixture.service.respawnTown(new PhantomRespawnRequest(1, cleanup.fixture.cancelled::get)), "Cleanup-pending session allowed respawn.");
		PhantomAssertions.assertEquals(0, cleanup.lease.respawnCalls, "Cleanup-pending session produced a respawn side effect.");
		cleanup.dispatcher.runNext();
		cleanup.fixture.stop();
	}

	private static void stoppingRejectsNewRespawn()
	{
		final Fixture fixture = fixture(new ManualDispatcher());
		fixture.service.beginStop();
		PhantomAssertions.assertEquals(RespawnOutcome.REJECTED, fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get)), "STOPPING accepted a new respawn operation.");
		PhantomAssertions.assertEquals(0, fixture.backend.acquireCalls, "STOPPING respawn acquired an actor.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Idle STOPPING service did not quiesce.");
	}

	private static void inFlightRespawnIsStopBarrier() throws Exception
	{
		final Fixture fixture = fixture(new ManualDispatcher());
		fixture.backend.acquireEntered = new CountDownLatch(1);
		fixture.backend.acquireRelease = new CountDownLatch(1);
		fixture.lease.respawnEntered = new CountDownLatch(1);
		fixture.lease.respawnRelease = new CountDownLatch(1);
		final AtomicReference<RespawnOutcome> outcome = new AtomicReference<>();
		final Thread respawn = new Thread(() -> outcome.set(fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get))), "Task012a-respawn-stop");
		respawn.start();
		await(fixture.backend.acquireEntered, "Respawn operation was not claimed before STOPPING.");
		fixture.service.beginStop();
		PhantomAssertions.assertFalse(fixture.service.finishStop(), "Stop ignored a claimed respawn operation.");
		fixture.backend.acquireRelease.countDown();
		await(fixture.lease.respawnEntered, "Claimed respawn did not enter its bounded side effect.");
		PhantomAssertions.assertFalse(fixture.service.finishStop(), "Stop ignored an in-flight respawn side effect.");
		fixture.lease.respawnRelease.countDown();
		respawn.join(WAIT_MILLIS);
		PhantomAssertions.assertFalse(respawn.isAlive(), "In-flight respawn did not finish.");
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, outcome.get(), "Claimed pre-stop respawn did not complete.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Stop did not reconcile the completed respawn.");
	}

	private static void currentTokenIdleRespawnSucceeds()
	{
		final Fixture fixture = fixture(new ManualDispatcher());
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.cancelled::get)), "Current plan-owned respawn did not complete.");
		PhantomAssertions.assertEquals(1, fixture.lease.respawnCalls, "Current respawn did not execute exactly once.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Current respawn leaked its actor lease.");
		fixture.stop();
	}

	private static CleanupFixture cleanupFixture(int failures)
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeLease lease = new FakeLease();
		lease.actor = deadActor();
		lease.cleanupFailuresRemaining = failures;
		final Fixture fixture = fixture(dispatcher, lease);
		fixture.start();
		return new CleanupFixture(fixture, dispatcher, lease);
	}

	private static void runUntilTerminal(Fixture fixture, ManualDispatcher dispatcher, int maximumPulses)
	{
		for (int pulse = 0; pulse < maximumPulses; pulse++)
		{
			if (fixture.service.find(1).orElseThrow().result().terminal())
			{
				return;
			}
			dispatcher.runNext();
		}
		throw new AssertionError("Combat fixture did not become terminal.");
	}

	private static Fixture fixture(PhantomCombatService.Dispatcher dispatcher)
	{
		return fixture(dispatcher, new FakeLease());
	}

	private static Fixture fixture(PhantomCombatService.Dispatcher dispatcher, FakeLease lease)
	{
		return new Fixture(dispatcher, lease);
	}

	private static ActorSnapshot liveActor()
	{
		return new ActorSnapshot(10, 88, 0, 100, 100, 100, 100, 50, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
	}

	private static ActorSnapshot deadActor()
	{
		return new ActorSnapshot(10, 88, 0, 0, 100, 100, 100, 0, 100, true, true, false, false, false, 0, "DEAD", 0, 0);
	}

	private static TargetSnapshot liveTarget()
	{
		return new TargetSnapshot(100, 20001, 0, 100, 100, false, false, true, true, false, true, true, 10, false, true);
	}

	private static TargetSnapshot deadTarget()
	{
		return new TargetSnapshot(100, 20001, 0, 0, 100, true, true, true, true, false, true, true, 10, false, true);
	}

	private static void await(CountDownLatch latch, String message) throws InterruptedException
	{
		PhantomAssertions.assertTrue(latch.await(WAIT_MILLIS, TimeUnit.MILLISECONDS), message);
	}

	private record CleanupFixture(Fixture fixture, ManualDispatcher dispatcher, FakeLease lease)
	{
	}

	private static final class Fixture
	{
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final FakeBackend backend;
		private final FakeLease lease;
		private final PhantomCombatService service;

		private Fixture(PhantomCombatService.Dispatcher dispatcher, FakeLease lease)
		{
			this.lease = lease;
			backend = new FakeBackend(lease);
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), 900, List.of()), new CapabilityEvidence(PhantomCombatMode.RANGED_MAGIC.capabilityKey(), 900, List.of(MAGIC_SKILL))));
			service = new PhantomCombatService(backend, resolver, PhantomCombatPolicy.productionDefaults(2), new PhantomCombatMetrics(), () -> 1, dispatcher);
			service.start();
		}

		private void start()
		{
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, service.startSession(request(PhantomCombatMode.MELEE_PHYSICAL, false)).status(), "Combat fixture did not start.");
		}

		private void startLoot()
		{
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, service.startSession(request(PhantomCombatMode.MELEE_PHYSICAL, true)).status(), "Loot fixture did not start.");
		}

		private void startMagic()
		{
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, service.startSession(request(PhantomCombatMode.RANGED_MAGIC, false)).status(), "Magic fixture did not start.");
		}

		private PhantomCombatRequest request(PhantomCombatMode mode, boolean loot)
		{
			return new PhantomCombatRequest(1, 100, mode, false, loot, 30_000, cancelled::get);
		}

		private void stop()
		{
			service.beginStop();
			if (!service.finishStop())
			{
				lease.cleanupFailuresRemaining = 0;
				service.retryFailedCleanup();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "Combat fixture did not stop.");
		}
	}

	private static final class FakeBackend implements PhantomCombatBackend
	{
		private final FakeLease lease;
		private CountDownLatch acquireEntered;
		private CountDownLatch acquireRelease;
		private int acquireCalls;

		private FakeBackend(FakeLease lease)
		{
			this.lease = lease;
		}

		@Override
		public PhantomCombatActorLease tryAcquireActor(long profileId)
		{
			acquireCalls++;
			if (acquireEntered != null)
			{
				acquireEntered.countDown();
				try
				{
					if (!acquireRelease.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
					{
						throw new IllegalStateException("Timed out waiting for actor acquisition release.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
			return lease;
		}
	}

	private enum ActionKind
	{
		IDLE,
		ATTACK,
		CAST,
		PICK_UP
	}

	private static final class FakeLease implements PhantomCombatActorLease
	{
		private ActorSnapshot actor = liveActor();
		private TargetSnapshot target = liveTarget();
		private Throwable actorFailure;
		private int actorSnapshotCalls;
		private List<LootCandidate> loot = List.of();
		private final Map<Integer, LootObservation> lootObservations = new HashMap<>();
		private int cleanupFailuresRemaining;
		private int cleanupCalls;
		private int closeCount;
		private int respawnCalls;
		private CountDownLatch actorEntered;
		private CountDownLatch actorRelease;
		private CountDownLatch respawnEntered;
		private CountDownLatch respawnRelease;
		private PhantomOwnedAction cleanedAction;
		private ActionKind actionKind = ActionKind.IDLE;
		private int actionTargetId;
		private SelectedSkill actionSkill;
		private PhantomCombatMode castMode;

		@Override
		public ActorSnapshot actorSnapshot()
		{
			actorSnapshotCalls++;
			if (actorEntered != null)
			{
				actorEntered.countDown();
				try
				{
					if (!actorRelease.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
					{
						throw new IllegalStateException("Timed out waiting for actor snapshot release.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
			if (actorFailure instanceof Error error)
			{
				throw error;
			}
			if (actorFailure instanceof RuntimeException runtime)
			{
				throw runtime;
			}
			return actor;
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			return target;
		}

		@Override
		public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return MAGIC_SKILL.equals(skill) && (mode == PhantomCombatMode.RANGED_MAGIC);
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return List.of();
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return new ArrayList<>(loot);
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return lootObservations.getOrDefault(candidate.worldObjectId(), LootObservation.PENDING);
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			actionKind = ActionKind.ATTACK;
			actionTargetId = targetObjectId;
			actionSkill = null;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			actionKind = ActionKind.CAST;
			actionTargetId = targetObjectId;
			actionSkill = skill;
			castMode = mode;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			actionKind = ActionKind.PICK_UP;
			actionTargetId = objectId;
			actionSkill = null;
			return ActionOutcome.ISSUED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
			cleanupCalls++;
			cleanedAction = action;
			if (cleanupFailuresRemaining > 0)
			{
				cleanupFailuresRemaining--;
				throw new IllegalStateException("injected cleanup failure");
			}
			switch (actionKind)
			{
				case ATTACK ->
				{
					if (actionTargetId == action.combatTargetObjectId())
					{
						actionKind = ActionKind.IDLE;
					}
				}
				case CAST ->
				{
					if ((actionTargetId == action.combatTargetObjectId()) && (action.selectedSkill() != null) && action.selectedSkill().equals(actionSkill))
					{
						actionKind = ActionKind.IDLE;
					}
				}
				case PICK_UP ->
				{
					if ((action.pickupObjectId() > 0) && (actionTargetId == action.pickupObjectId()))
					{
						actionKind = ActionKind.IDLE;
					}
				}
				default ->
				{
				}
			}
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			respawnCalls++;
			if (respawnEntered != null)
			{
				respawnEntered.countDown();
				try
				{
					if (!respawnRelease.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
					{
						throw new IllegalStateException("Timed out waiting for respawn release.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			}
			return RespawnOutcome.COMPLETED;
		}

		@Override
		public void close()
		{
			closeCount++;
		}
	}

	private static class ManualHandle implements DispatchHandle
	{
		private DispatchState state = DispatchState.SCHEDULED;

		private void run(Runnable runnable)
		{
			if (state != DispatchState.SCHEDULED)
			{
				return;
			}
			state = DispatchState.RUNNING;
			try
			{
				runnable.run();
			}
			finally
			{
				state = DispatchState.FINISHED;
			}
		}

		@Override
		public boolean cancelIfNotStarted()
		{
			if (state != DispatchState.SCHEDULED)
			{
				return false;
			}
			state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public DispatchState state()
		{
			return state;
		}
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable pending;
		private ManualHandle pendingHandle;
		private Runnable stale;
		private int runs;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			PhantomAssertions.assertEquals(250L, delayMillis, "Shared worker cadence changed.");
			PhantomAssertions.assertTrue(pending == null, "More than one shared worker was scheduled.");
			pending = runnable;
			pendingHandle = new ManualHandle();
			return DispatchResult.accepted(pendingHandle);
		}

		private void runNext()
		{
			final Runnable runnable = pending;
			final ManualHandle handle = pendingHandle;
			PhantomAssertions.assertTrue(runnable != null, "No shared worker was scheduled.");
			pending = null;
			pendingHandle = null;
			stale = runnable;
			runs++;
			handle.run(runnable);
		}

		private void runStale()
		{
			PhantomAssertions.assertTrue(stale != null, "No stale worker callback was retained.");
			stale.run();
		}

		private void tryRunCancelled()
		{
			if ((pending != null) && (pendingHandle != null))
			{
				final Runnable runnable = pending;
				final ManualHandle handle = pendingHandle;
				pending = null;
				pendingHandle = null;
				if (handle.state() == DispatchState.SCHEDULED)
				{
					runs++;
				}
				handle.run(runnable);
			}
		}
	}

	private static final class BlockingDispatcher implements PhantomCombatService.Dispatcher
	{
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private ManualHandle handle;
		private int runs;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			entered.countDown();
			try
			{
				if (!release.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
				{
					throw new IllegalStateException("Timed out waiting for dispatch release.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			handle = new ManualHandle();
			return DispatchResult.accepted(handle);
		}
	}

	private static final class RunningBeforePulseDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable pending;
		private final DispatchHandle handle = new DispatchHandle()
		{
			@Override
			public boolean cancelIfNotStarted()
			{
				return false;
			}

			@Override
			public DispatchState state()
			{
				return DispatchState.RUNNING;
			}
		};

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			pending = runnable;
			return DispatchResult.accepted(handle);
		}

		private void runAfterStop()
		{
			final Runnable runnable = pending;
			PhantomAssertions.assertTrue(runnable != null, "No RUNNING callback was retained.");
			pending = null;
			runnable.run();
		}
	}

	private static final class InlineDispatcher implements PhantomCombatService.Dispatcher
	{
		private int runs;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			final ManualHandle handle = new ManualHandle();
			runs++;
			handle.run(runnable);
			return DispatchResult.accepted(handle);
		}
	}
}
