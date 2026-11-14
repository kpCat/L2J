/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;

/** DB-free checks of the TEST-only pre-CUT worker boundary. */
public final class PhantomEncounterDispatchGateChecks
{
	public static void main(String[] args)
	{
		holdsUntilReleasedExactlyOnce();
		abortCancelsPendingAndRejectsNewDispatch();
		rejectedReleaseDoesNotRetainServiceClaim();
		runningDelegateAcknowledgesNativeStop();
		System.out.println("ENCOUNTER_DISPATCH_GATE_GREEN checks=4");
	}

	private static void holdsUntilReleasedExactlyOnce()
	{
		final var delegate = new QueuedDispatcher();
		final var gate = new PhantomEncounterDispatchGate(delegate);
		final var effects = new AtomicInteger();
		final var accepted = gate.dispatch(effects::incrementAndGet, 250);
		check(accepted.accepted() && accepted.handle().state() == DispatchState.SCHEDULED, "Held worker was not reserved");
		check(delegate.tasks.isEmpty() && effects.get() == 0, "Worker escaped before durable CUT");
		gate.release(); gate.release();
		check(delegate.tasks.size() == 1 && effects.get() == 0, "Release dispatched a worker more than once");
		delegate.runNext();
		check(effects.get() == 1 && accepted.handle().state() == DispatchState.FINISHED, "Released worker did not finish exactly once");
		check(!accepted.handle().cancelIfNotStarted(), "Completed worker was reported cancelled");
		gate.abort();
	}

	private static void abortCancelsPendingAndRejectsNewDispatch()
	{
		final var delegate = new QueuedDispatcher();
		final var gate = new PhantomEncounterDispatchGate(delegate);
		final var effects = new AtomicInteger();
		final var cancelled = gate.dispatch(effects::incrementAndGet, 250);
		check(cancelled.handle().cancelIfNotStarted() && !cancelled.handle().cancelIfNotStarted(), "Held cancellation was not exact");
		final var pending = gate.dispatch(effects::incrementAndGet, 250);
		gate.release();
		check(delegate.tasks.size() == 1, "Cancelled held worker was dispatched");
		gate.abort(); gate.abort(); gate.release();
		check(pending.handle().state() == DispatchState.CANCELLED && delegate.tasks.isEmpty(), "Abort retained a queued worker");
		check(!gate.dispatch(effects::incrementAndGet, 250).accepted() && effects.get() == 0, "Aborted gate accepted or executed work");
	}

	private static final class QueuedDispatcher implements PhantomCombatService.Dispatcher
	{
		private final List<Task> tasks = new ArrayList<>();

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			check(delayMillis == 250, "Native worker delay changed");
			final var task = new Task(this, runnable); tasks.add(task);
			return DispatchResult.accepted(task);
		}

		void runNext()
		{
			final var task = tasks.removeFirst();
			task.state = DispatchState.RUNNING;
			try { task.runnable.run(); }
			finally { task.state = DispatchState.FINISHED; }
		}
	}

	private static void rejectedReleaseDoesNotRetainServiceClaim()
	{
		final var gate = new PhantomEncounterDispatchGate((_, _) -> DispatchResult.rejected());
		final var lease = new NoActionLease();
		final var service = new PhantomCombatService(_ -> lease, new PhantomCombatCapabilityResolver(_ -> List.of()),
			PhantomCombatPolicy.productionDefaults(1), new PhantomCombatMetrics(), () -> 1, gate);
		service.start();
		check(service.startSession(new PhantomCombatRequest(1, 2, PhantomCombatMode.MELEE_PHYSICAL, false, false, 60000, () -> false)).accepted(), "Native session was not reserved");
		try { gate.release(); throw new AssertionError("Rejected delegate was reported released"); }
		catch (IllegalStateException expected) { }
		service.beginStop(); gate.abort();
		check(service.finishStop() && lease.closed && lease.effects == 0, "Rejected release retained a native worker claim or actor lease");
		check(!gate.dispatch(() -> { throw new AssertionError("Aborted work ran"); }, 250).accepted(), "Failed release accepted subsequent work");
	}

	private static final class NoActionLease implements PhantomCombatActorLease
	{
		private boolean closed;
		private int effects;
		@Override public PhantomCombatBackend.ActorSnapshot actorSnapshot() { return new PhantomCombatBackend.ActorSnapshot(1, 0, 0, 100, 100, 100, 100, 0, 0, false, false, false, false, false, 0, "IDLE", 0, 0); }
		@Override public PhantomCombatBackend.TargetSnapshot targetSnapshot(int objectId) { return new PhantomCombatBackend.TargetSnapshot(2, 2, 0, 100, 100, false, false, true, true, false, true, true, 100, false, true); }
		@Override public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode) { return false; }
		@Override public List<PhantomCombatBackend.ThreatObservation> observedAttackers(int limit) { return List.of(); }
		@Override public List<PhantomCombatBackend.LootCandidate> lootCandidates(int limit, int maximumDistance) { return List.of(); }
		@Override public PhantomCombatBackend.LootObservation observeLoot(PhantomCombatBackend.LootCandidate candidate) { return PhantomCombatBackend.LootObservation.INELIGIBLE; }
		@Override public PhantomCombatBackend.ShotOutcome activateShot(PhantomCombatMode mode) { effects++; return PhantomCombatBackend.ShotOutcome.UNAVAILABLE; }
		@Override public PhantomCombatBackend.ActionOutcome attack(int objectId) { effects++; return PhantomCombatBackend.ActionOutcome.REJECTED; }
		@Override public PhantomCombatBackend.ActionOutcome cast(int objectId, SelectedSkill skill, PhantomCombatMode mode) { effects++; return PhantomCombatBackend.ActionOutcome.REJECTED; }
		@Override public PhantomCombatBackend.ActionOutcome pickUp(int objectId) { effects++; return PhantomCombatBackend.ActionOutcome.REJECTED; }
		@Override public void cancelOwnedAction(PhantomOwnedAction action) { }
		@Override public PhantomCombatBackend.RespawnOutcome respawnTown() { effects++; return PhantomCombatBackend.RespawnOutcome.REJECTED; }
		@Override public void close() { closed = true; }
	}

	private static void runningDelegateAcknowledgesNativeStop()
	{
		final var delegate = new QueuedDispatcher(); final var gate = new PhantomEncounterDispatchGate(delegate);
		final var lease = new NoActionLease();
		final var service = new PhantomCombatService(_ -> lease, new PhantomCombatCapabilityResolver(_ -> List.of()),
			PhantomCombatPolicy.productionDefaults(1), new PhantomCombatMetrics(), () -> 1, gate);
		service.start();
		check(service.startSession(new PhantomCombatRequest(1, 2, PhantomCombatMode.MELEE_PHYSICAL, false, false, 60000, () -> false)).accepted(), "Native session was not reserved");
		gate.release();
		final var started = delegate.tasks.removeFirst(); started.state = DispatchState.RUNNING;
		service.beginStop(); gate.abort();
		started.runnable.run(); started.state = DispatchState.FINISHED;
		check(service.finishStop() && lease.closed && lease.effects == 0, "Already-started delegate failed to acknowledge native stop");
	}

	private static final class Task implements DispatchHandle
	{
		private final QueuedDispatcher owner;
		private final Runnable runnable;
		private DispatchState state = DispatchState.SCHEDULED;
		Task(QueuedDispatcher owner, Runnable runnable) { this.owner = owner; this.runnable = runnable; }
		@Override public DispatchState state() { return state; }
		@Override public boolean cancelIfNotStarted()
		{
			if (state != DispatchState.SCHEDULED || !owner.tasks.remove(this)) { return false; }
			state = DispatchState.CANCELLED; return true;
		}
	}

	private static void check(boolean value, String failure)
	{
		if (!value) { throw new AssertionError(failure); }
	}

	private PhantomEncounterDispatchGateChecks() { }
}
