/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RaidTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;

public final class PhantomRaidCombatDynamicGoal026Checkpoint5Suite implements PhantomTestSuite
{
	private static final long SEED = 26002652L;
	private static final String AUTHORITY = "A5".repeat(32);

	@Override
	public String id()
	{
		return "raid-combat-dynamic-goal026cp5";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Dynamic raid Combat used the wrong deterministic seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-exact-live-start-and-death-victory", _ -> exactLiveDeathVictory());
		registry.add("02-wrong-live-authority-rejected", _ -> wrongLiveAuthorityRejected());
		registry.add("03-replaced-dead-identity-target-lost", _ -> replacedDeadIdentityLost());
		registry.add("04-collector-only-native-loot-phase", _ -> collectorOnlyLootPhase());
	}

	private static void exactLiveDeathVictory()
	{
		final Fixture fixture = new Fixture();
		try
		{
			fixture.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
			final var started = fixture.service.startRaidSession(fixture.request(1, false, 48));
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, started.status(), "Exact live Queen raid session was not accepted.");
			fixture.dispatcher.runNext();
			fixture.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, true);
			fixture.dispatcher.runNext();
			final var terminal = fixture.service.consumeTerminal(1).orElseThrow();
			PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, terminal.result(), "Exact live-to-dead identity did not produce VICTORY.");
			PhantomAssertions.assertTrue(fixture.lease.raidAttacks > 0, "Accepted raid session never used the additive raid action path.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void wrongLiveAuthorityRejected()
	{
		final Fixture fixture = new Fixture();
		try
		{
			fixture.lease.target = target(7002, 29001, 0, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.service.startRaidSession(fixture.request(2, false, 48)).status(), "Wrong live object was accepted.");
			fixture.lease.target = target(7001, 29002, 0, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.service.startRaidSession(fixture.request(2, false, 48)).status(), "Wrong live NPC was accepted.");
			fixture.lease.target = target(7001, 29001, 0, NpcKind.RAID_BOSS, false);
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.service.startRaidSession(fixture.request(2, false, 48)).status(), "Wrong live NPC kind was accepted.");
			fixture.lease.target = target(7001, 29001, 1, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.service.startRaidSession(fixture.request(2, false, 48)).status(), "Wrong live instance was accepted.");
			fixture.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
			fixture.lease.level = 49;
			PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.service.startRaidSession(fixture.request(2, false, 48)).status(), "Actor above exact Queen curse ceiling was accepted.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void replacedDeadIdentityLost()
	{
		final Fixture fixture = new Fixture();
		try
		{
			fixture.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertTrue(fixture.service.startRaidSession(fixture.request(3, false, 48)).accepted(), "Replacement fixture did not start.");
			fixture.lease.target = target(7002, 29001, 0, NpcKind.GRAND_BOSS, true);
			fixture.dispatcher.runNext();
			PhantomAssertions.assertEquals(PhantomCombatResult.TARGET_LOST, fixture.service.consumeTerminal(3).orElseThrow().result(), "Wrong/replaced dead object produced victory.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void collectorOnlyLootPhase()
	{
		final Fixture collector = new Fixture();
		try
		{
			collector.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertTrue(collector.service.startRaidSession(collector.request(4, true, 48)).accepted(), "Collector fixture did not start.");
			collector.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, true);
			collector.dispatcher.runNext();
			PhantomAssertions.assertTrue(collector.service.consumeTerminal(4).isEmpty(), "Collector skipped the existing loot phase.");
			collector.dispatcher.runNext();
			PhantomAssertions.assertTrue(collector.service.consumeTerminal(4).orElseThrow().result().victory(), "Collector did not finish through native loot processing.");
		}
		finally
		{
			collector.stop();
		}

		final Fixture ordinary = new Fixture();
		try
		{
			ordinary.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
			PhantomAssertions.assertTrue(ordinary.service.startRaidSession(ordinary.request(5, false, 48)).accepted(), "Non-collector fixture did not start.");
			ordinary.lease.target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, true);
			ordinary.dispatcher.runNext();
			PhantomAssertions.assertEquals(PhantomCombatResult.VICTORY, ordinary.service.consumeTerminal(5).orElseThrow().result(), "Non-collector entered the loot phase.");
		}
		finally
		{
			ordinary.stop();
		}
	}

	private static RaidTargetSnapshot target(int objectId, int npcId, int instanceId, NpcKind kind, boolean dead)
	{
		return new RaidTargetSnapshot(objectId, npcId, instanceId, dead ? 0 : 100, 100, dead, dead, !dead, !dead, false, true, kind, 100, false, true);
	}

	private static final class Fixture
	{
		private final AtomicLong clock = new AtomicLong(1);
		private final ManualDispatcher dispatcher = new ManualDispatcher();
		private final FakeLease lease = new FakeLease();
		private final PhantomCombatService service;

		private Fixture()
		{
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), 900, List.of())));
			service = new PhantomCombatService(_ -> lease, resolver, PhantomCombatPolicy.productionDefaults(8), new PhantomCombatMetrics(), clock::get, dispatcher);
			service.start();
		}

		private PhantomRaidCombatRequest request(long profileId, boolean loot, int maximumLevel)
		{
			return new PhantomRaidCombatRequest(profileId, 7001, 29001, 0, ContentKind.EPIC, NpcKind.GRAND_BOSS, AUTHORITY, PhantomCombatMode.MELEE_PHYSICAL, true, loot, maximumLevel, 30_000, () -> false);
		}

		private void stop()
		{
			service.beginStop();
			while (dispatcher.next != null)
			{
				dispatcher.runNext();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "Dynamic raid Combat fixture did not stop.");
		}
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable next;
		private ManualHandle handle;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			PhantomAssertions.assertTrue(next == null, "Raid Combat scheduled more than one shared worker.");
			next = runnable;
			handle = new ManualHandle(this);
			return DispatchResult.accepted(handle);
		}

		private void runNext()
		{
			final Runnable runnable = next;
			PhantomAssertions.assertTrue(runnable != null, "No raid Combat pulse was scheduled.");
			next = null;
			final ManualHandle exact = handle;
			handle = null;
			exact._state = DispatchState.RUNNING;
			try
			{
				runnable.run();
			}
			finally
			{
				exact._state = DispatchState.FINISHED;
			}
		}
	}

	private static final class ManualHandle implements DispatchHandle
	{
		private final ManualDispatcher _owner;
		private DispatchState _state = DispatchState.SCHEDULED;

		private ManualHandle(ManualDispatcher owner)
		{
			_owner = owner;
		}

		@Override
		public boolean cancelIfNotStarted()
		{
			if ((_state != DispatchState.SCHEDULED) || (_owner.handle != this))
			{
				return false;
			}
			_owner.next = null;
			_owner.handle = null;
			_state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public DispatchState state()
		{
			return _state;
		}
	}

	private static final class FakeLease implements PhantomCombatActorLease
	{
		private RaidTargetSnapshot target = target(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
		private int level = 48;
		private int raidAttacks;

		@Override
		public ActorSnapshot actorSnapshot()
		{
			return new ActorSnapshot(9001, 88, 0, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			return null;
		}

		@Override
		public RaidTargetSnapshot raidTargetSnapshot(int targetObjectId)
		{
			return target;
		}

		@Override
		public int raidActorLevel()
		{
			return level;
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
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return LootObservation.PENDING;
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome attackRaid(int targetObjectId, PhantomRaidCombatRequest request)
		{
			raidAttacks++;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
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
		}
	}
}
