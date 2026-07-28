/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatThreatTable;

public final class PhantomCombatPerformanceSuite implements PhantomTestSuite
{
	private static final int SESSION_COMPLETIONS = 10_000;
	private static final int PULSES = 100_000;
	private static final int THREAT_OPERATIONS = 100_000;
	private static final int CANCELLATIONS = 10_000;

	@Override
	public String id()
	{
		return "combat-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-bounded-combat-structural-smoke", this::run);
	}

	private void run(PhantomTestContext context)
	{
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final Backend backend = new Backend();
		final PhantomCombatPolicy policy = PhantomCombatPolicy.productionDefaults(1);
		final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), 900, List.of())));
		final PhantomCombatService service = new PhantomCombatService(backend, resolver, policy, new PhantomCombatMetrics(), () -> 1, dispatcher);
		service.start();

		final AtomicBoolean cancelled = new AtomicBoolean();
		final PhantomCombatRequest pulseRequest = request(cancelled);
		PhantomAssertions.assertTrue(service.startSession(pulseRequest).accepted(), "Performance pulse session did not start.");
		for (int index = 0; index < PULSES; index++)
		{
			dispatcher.runNext();
		}
		service.cancel(1);
		service.consumeTerminal(1);
		if (dispatcher.next != null)
		{
			dispatcher.runNext();
		}

		for (int index = 0; index < SESSION_COMPLETIONS; index++)
		{
			backend.deadAfterStart = false;
			PhantomAssertions.assertTrue(service.startSession(request(cancelled)).accepted(), "Performance completion session did not start.");
			backend.lastLease.targetDead = true;
			dispatcher.runNext();
			PhantomAssertions.assertTrue(service.consumeTerminal(1).isPresent(), "Performance terminal session was not consumed.");
		}

		for (int index = 0; index < CANCELLATIONS; index++)
		{
			PhantomAssertions.assertTrue(service.startSession(request(cancelled)).accepted(), "Performance cancellation session did not start.");
			PhantomAssertions.assertTrue(service.cancel(1), "Performance cancellation was rejected.");
			PhantomAssertions.assertTrue(service.consumeTerminal(1).isPresent(), "Performance cancellation terminal was not consumed.");
		}
		if (dispatcher.next != null)
		{
			dispatcher.runNext();
		}

		final PhantomCombatThreatTable threat = new PhantomCombatThreatTable(policy.maximumThreatEntries());
		for (int index = 0; index < THREAT_OPERATIONS; index++)
		{
			threat.observe((index % policy.maximumThreatEntries()) + 1, 1, index, index == 0);
			PhantomAssertions.assertTrue(threat.highest(index).isPresent(), "Threat selection became empty.");
		}

		final var snapshot = service.snapshot();
		PhantomAssertions.assertTrue(snapshot.activeSessions() <= policy.maximumSessions(), "Session capacity was exceeded.");
		PhantomAssertions.assertTrue(snapshot.queuedSessions() <= policy.maximumSessions(), "Queue capacity was exceeded.");
		PhantomAssertions.assertTrue(snapshot.currentWorkers() <= 1, "Shared worker bound was exceeded.");
		PhantomAssertions.assertTrue(threat.size() <= policy.maximumThreatEntries(), "Threat capacity was exceeded.");
		PhantomAssertions.assertEquals(0, snapshot.actorLeases(), "Performance run leaked actor leases.");
		PhantomAssertions.assertEquals(0, snapshot.terminalSessions(), "Performance run retained consumed terminal slots.");

		service.beginStop();
		if (dispatcher.next != null)
		{
			dispatcher.runNext();
		}
		PhantomAssertions.assertTrue(service.finishStop(), "Performance combat service did not stop.");

		context.record("combat.sessionsCompleted", SESSION_COMPLETIONS);
		context.record("combat.pulses", PULSES);
		context.record("combat.threatOperations", THREAT_OPERATIONS);
		context.record("combat.cancellations", CANCELLATIONS);
		context.record("combat.maximumWorkers", 1);
		context.record("combat.actorLeasesAfterRun", 0);
		context.record("combat.terminalSlotsAfterConsume", 0);
		System.out.println("COMBAT_PERFORMANCE sessionsCompleted=10000 pulses=100000 threatOperations=100000 cancellations=10000 maximumWorkers=1 actorLeasesAfterRun=0 terminalSlotsAfterConsume=0");
	}

	private static PhantomCombatRequest request(AtomicBoolean cancelled)
	{
		return new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, false, false, 30_000, cancelled::get);
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable next;

		@Override
		public void dispatch(Runnable runnable, long delayMillis)
		{
			PhantomAssertions.assertTrue(next == null, "Performance run scheduled more than one worker.");
			next = runnable;
		}

		private void runNext()
		{
			final Runnable runnable = next;
			PhantomAssertions.assertTrue(runnable != null, "Performance worker was absent.");
			next = null;
			runnable.run();
		}
	}

	private static final class Backend implements PhantomCombatBackend
	{
		private Lease lastLease;
		private boolean deadAfterStart;

		@Override
		public PhantomCombatActorLease tryAcquireActor(long profileId)
		{
			lastLease = new Lease();
			lastLease.targetDead = deadAfterStart;
			return lastLease;
		}
	}

	private static final class Lease implements PhantomCombatActorLease
	{
		private boolean targetDead;
		private boolean closed;

		@Override
		public ActorSnapshot actorSnapshot()
		{
			return new ActorSnapshot(10, 88, 0, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			return new TargetSnapshot(100, 20001, 0, targetDead ? 0 : 100, 100, targetDead, targetDead, true, true, false, true, true, 10, false, true);
		}

		@Override
		public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return false;
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return List.of();
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return List.of();
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			return ActionOutcome.ALREADY_OWNED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public void cancelOwnedAction(int targetObjectId, SelectedSkill selectedSkill)
		{
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			return RespawnOutcome.REJECTED;
		}

		@Override
		public void close()
		{
			PhantomAssertions.assertFalse(closed, "Performance actor lease closed twice.");
			closed = true;
		}
	}
}
