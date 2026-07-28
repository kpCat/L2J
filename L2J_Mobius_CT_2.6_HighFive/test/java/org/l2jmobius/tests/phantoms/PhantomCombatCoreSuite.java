/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPhase;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatThreatTable;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;

public final class PhantomCombatCoreSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "combat-core";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-mode-code-mapping", _ -> testModeMapping());
		registry.add("02-mode-code-rejection", _ -> testModeRejection());
		registry.add("03-request-bounds", _ -> testRequestBounds());
		registry.add("04-policy-fixed-contract", _ -> testPolicy());
		registry.add("05-capability-key-mapping", _ -> testCapabilityMapping());
		registry.add("06-capability-selected-skill", _ -> testCapabilitySkill());
		registry.add("07-magic-unsupported-without-skill", _ -> testMagicUnsupported());
		registry.add("08-physical-normal-attack-fallback", _ -> testPhysicalFallback());
		registry.add("09-selected-skills-bounded-and-sorted", _ -> testSelectedSkillBound());
		registry.add("10-threat-addition", _ -> testThreatAddition());
		registry.add("11-threat-decay", _ -> testThreatDecay());
		registry.add("12-threat-explicit-tie", _ -> testThreatExplicitTie());
		registry.add("13-threat-object-id-tie", _ -> testThreatObjectIdTie());
		registry.add("14-threat-eviction", _ -> testThreatEviction());
		registry.add("15-threat-overflow-saturates", _ -> testThreatOverflow());
		registry.add("16-session-capacity", _ -> testSessionCapacity());
		registry.add("17-one-session-per-profile", _ -> testOneSession());
		registry.add("18-identical-start-idempotent", _ -> testIdempotentStart());
		registry.add("19-different-start-rejected", _ -> testDifferentStart());
		registry.add("20-actor-lease-rejection", _ -> testLeaseRejection());
		registry.add("21-target-rejection", _ -> testTargetRejection());
		registry.add("22-start-token-cancelled", _ -> testStartCancelled());
		registry.add("23-normal-attack-pulse", _ -> testAttackPulse());
		registry.add("24-skill-cast-pulse", _ -> testCastPulse());
		registry.add("25-action-coalescing", _ -> testActionCoalescing());
		registry.add("26-shot-activated", _ -> testShotActivated());
		registry.add("27-shot-unavailable", _ -> testShotUnavailable());
		registry.add("28-shot-failure", _ -> testShotFailure());
		registry.add("29-low-hp-terminal", _ -> testLowHp());
		registry.add("30-player-death-terminal", _ -> testPlayerDeath());
		registry.add("31-target-death-victory", _ -> testTargetDeath());
		registry.add("32-target-loss-terminal", _ -> testTargetLoss());
		registry.add("33-session-timeout", _ -> testTimeout());
		registry.add("34-plan-token-cancellation", _ -> testTokenCancellation());
		registry.add("35-loot-observed-victory", _ -> testLootVictory());
		registry.add("36-loot-blocked", _ -> testLootBlocked());
		registry.add("37-backend-exception-isolation", _ -> testBackendException());
		registry.add("38-dispatch-failure-terminal", _ -> testDispatchFailure());
		registry.add("39-one-shared-worker", _ -> testOneWorker());
		registry.add("40-stop-quiescence-and-release", _ -> testStop());
		registry.add("41-terminal-retention-and-consume", _ -> testTerminalConsume());
		registry.add("42-respawn-result", _ -> testRespawn());
		registry.add("43-backend-observation-bound", _ -> testObservationBound());
		registry.add("44-pickup-intention-waits-for-world-removal", _ -> testLootPickupInProgress());
		registry.add("45-cancel-waits-for-in-flight-pulse", _ -> testCancelWaitsForPulse());
		registry.add("46-dispatch-failure-reconciles-reserved-start", _ -> testDispatchFailureReservedStart());
		registry.add("47-cancel-waits-for-in-flight-start", _ -> testCancelWaitsForStart());
		registry.add("48-resolver-skips-unsupported-first-variant", _ -> testResolverVariants());
		registry.add("49-disabled-backend-remains-inert", _ -> PhantomAssertions.assertTrue(PhantomCombatBackend.inert().tryAcquireActor(1) == null, "Disabled combat backend created actor state."));
		registry.add("50-canonical-transient-cp-is-not-normalized", _ ->
		{
			final ActorSnapshot snapshot = new ActorSnapshot(10, 88, 0, 100, 100, 100, 100, 150, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
			PhantomAssertions.assertTrue(Double.compare(snapshot.currentCp(), 150) == 0, "Snapshot normalized exact canonical CP.");
		});
	}

	private static void testModeMapping()
	{
		PhantomAssertions.assertEquals(PhantomCombatMode.MELEE_PHYSICAL, PhantomCombatMode.fromCode(1), "Melee mode mapping changed.");
		PhantomAssertions.assertEquals("combat.ranged_magic_damage", PhantomCombatMode.RANGED_MAGIC.capabilityKey(), "Magic capability mapping changed.");
	}

	private static void testModeRejection()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomCombatMode.fromCode(4), "Unknown combat mode was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomCombatMode.fromCode(4_294_967_297L), "Wrapped long combat mode was accepted.");
	}

	private static void testRequestBounds()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCombatRequest(0, 1, PhantomCombatMode.MELEE_PHYSICAL, false, false, 1000, () -> false), "Zero profile was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCombatRequest(1, 1, PhantomCombatMode.MELEE_PHYSICAL, false, false, 999, () -> false), "Sub-minimum timeout was accepted.");
	}

	private static void testPolicy()
	{
		final PhantomCombatPolicy policy = PhantomCombatPolicy.productionDefaults(7);
		PhantomAssertions.assertEquals(64, policy.maximumSessionsPerPulse(), "Pulse bound changed.");
		PhantomAssertions.assertEquals(32, policy.maximumThreatEntries(), "Threat bound changed.");
		PhantomAssertions.assertEquals(250L, policy.pulseIntervalMillis(), "Pulse interval changed.");
	}

	private static void testCapabilityMapping()
	{
		final Fixture fixture = fixture(1);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "Mapped melee capability was rejected.");
		fixture.stop();
	}

	private static void testCapabilitySkill()
	{
		final Fixture fixture = fixture(1);
		fixture.capabilities = capabilities(PhantomCombatMode.RANGED_PHYSICAL, List.of(new SelectedSkill(10, 1)));
		fixture.lease.supportedSkills.add(10);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.RANGED_PHYSICAL).status(), "Known physical evidence skill was rejected.");
		PhantomAssertions.assertEquals(1, fixture.service.find(1).orElseThrow().selectedSkills(), "Selected skill was not retained.");
		fixture.stop();
	}

	private static void testMagicUnsupported()
	{
		final Fixture fixture = fixture(1);
		fixture.capabilities = capabilities(PhantomCombatMode.RANGED_MAGIC, List.of(new SelectedSkill(20, 1)));
		PhantomAssertions.assertEquals(StartStatus.UNSUPPORTED_LOADOUT, fixture.start(1, PhantomCombatMode.RANGED_MAGIC).status(), "Magic without a supported known skill was accepted.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Rejected loadout leaked its actor lease.");
	}

	private static void testPhysicalFallback()
	{
		final Fixture fixture = fixture(1);
		fixture.capabilities = capabilities(PhantomCombatMode.RANGED_PHYSICAL, List.of(new SelectedSkill(20, 1)));
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.RANGED_PHYSICAL).status(), "Physical normal-attack fallback was rejected.");
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.lease.attackCount, "Physical fallback did not issue a normal attack.");
		fixture.stop();
	}

	private static void testSelectedSkillBound()
	{
		final Fixture fixture = fixture(1);
		final List<SelectedSkill> skills = List.of(new SelectedSkill(9, 1), new SelectedSkill(7, 1), new SelectedSkill(5, 1), new SelectedSkill(3, 1), new SelectedSkill(1, 1));
		fixture.capabilities = capabilities(PhantomCombatMode.RANGED_PHYSICAL, skills);
		fixture.lease.supportedSkills.addAll(List.of(1, 3, 5, 7, 9));
		fixture.start(1, PhantomCombatMode.RANGED_PHYSICAL);
		PhantomAssertions.assertEquals(4, fixture.service.find(1).orElseThrow().selectedSkills(), "Loadout exceeded four selected skills.");
		fixture.stop();
	}

	private static void testResolverVariants()
	{
		final Fixture fixture = fixture(1);
		fixture.capabilities = List.of(
			new CapabilityEvidence(PhantomCombatMode.RANGED_MAGIC.capabilityKey(), "high-rank-unsupported", 1000, List.of(new SelectedSkill(20, 1))),
			new CapabilityEvidence(PhantomCombatMode.RANGED_MAGIC.capabilityKey(), "lower-rank-supported", 100, List.of(new SelectedSkill(21, 1))));
		fixture.lease.supportedSkills.add(21);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.RANGED_MAGIC).status(), "Resolver stopped at the first unsupported variant or treated rank as final suitability.");
		PhantomAssertions.assertEquals(1, fixture.service.find(1).orElseThrow().selectedSkills(), "Resolver did not retain the supported sibling variant.");
		fixture.stop();
	}

	private static void testThreatAddition()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(32);
		table.observe(1, 10, 0, false);
		table.observe(1, 5, 0, false);
		PhantomAssertions.assertEquals(15L, table.snapshot(0).get(0).threatValue(), "Threat addition changed.");
	}

	private static void testThreatDecay()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(32);
		table.observe(1, 10, 0, false);
		PhantomAssertions.assertEquals(8L, table.snapshot(2_000_000_000L).get(0).threatValue(), "Threat decay changed.");
	}

	private static void testThreatExplicitTie()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(32);
		table.observe(2, 10, 0, false);
		table.observe(3, 10, 0, true);
		PhantomAssertions.assertEquals(3, table.highest(0).orElseThrow(), "Explicit target did not win a threat tie.");
	}

	private static void testThreatObjectIdTie()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(32);
		table.observe(2, 10, 0, false);
		table.observe(1, 10, 0, false);
		PhantomAssertions.assertEquals(1, table.highest(0).orElseThrow(), "Lower object ID did not win a threat tie.");
	}

	private static void testThreatEviction()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(2);
		table.observe(1, 1, 0, false);
		table.observe(2, 2, 0, false);
		table.observe(3, 3, 0, false);
		PhantomAssertions.assertEquals(2, table.size(), "Threat table exceeded capacity.");
		PhantomAssertions.assertEquals(1L, table.evictions(), "Threat eviction was not recorded.");
	}

	private static void testThreatOverflow()
	{
		final PhantomCombatThreatTable table = new PhantomCombatThreatTable(2);
		table.observe(1, Long.MAX_VALUE, 0, false);
		table.observe(1, Long.MAX_VALUE, 0, false);
		PhantomAssertions.assertTrue(table.snapshot(0).get(0).threatValue() > 0, "Threat overflow wrapped negative.");
	}

	private static void testSessionCapacity()
	{
		final Fixture fixture = fixture(1);
		fixture.backend.leases.put(2L, new FakeLease());
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "First session failed.");
		PhantomAssertions.assertEquals(StartStatus.REJECTED_CAPACITY, fixture.start(2, PhantomCombatMode.MELEE_PHYSICAL).status(), "Session capacity was exceeded.");
		fixture.stop();
	}

	private static void testOneSession()
	{
		final Fixture fixture = fixture(2);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_EXISTING, fixture.start(1, PhantomCombatMode.RANGED_PHYSICAL).status(), "Different second session for one profile was accepted.");
		fixture.stop();
	}

	private static void testIdempotentStart()
	{
		final Fixture fixture = fixture(1);
		final PhantomCombatRequest request = fixture.request(1, PhantomCombatMode.MELEE_PHYSICAL);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.service.startSession(request).status(), "Initial session failed.");
		PhantomAssertions.assertEquals(StartStatus.IDEMPOTENT, fixture.service.startSession(request).status(), "Identical session was not idempotent.");
		PhantomAssertions.assertEquals(1, fixture.backend.acquireCount, "Idempotent start acquired a second lease.");
		fixture.stop();
	}

	private static void testDifferentStart()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_EXISTING, fixture.service.startSession(new PhantomCombatRequest(1, 101, PhantomCombatMode.MELEE_PHYSICAL, false, false, 30_000, fixture.token)).status(), "Different target replaced an active session.");
		fixture.stop();
	}

	private static void testLeaseRejection()
	{
		final Fixture fixture = fixture(1);
		fixture.backend.reject = true;
		PhantomAssertions.assertEquals(StartStatus.REJECTED_ACTOR, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "Missing actor lease was accepted.");
	}

	private static void testTargetRejection()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.target = target(false, false);
		PhantomAssertions.assertEquals(StartStatus.REJECTED_TARGET, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "Forbidden target was accepted.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Rejected target leaked its lease.");
	}

	private static void testStartCancelled()
	{
		final Fixture fixture = fixture(1);
		fixture.cancelled.set(true);
		PhantomAssertions.assertEquals(StartStatus.CANCELLED, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "Cancelled plan started combat.");
	}

	private static void testAttackPulse()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.lease.attackCount, "Combat pulse did not issue attack.");
		PhantomAssertions.assertEquals(PhantomCombatPhase.FIGHTING, fixture.service.find(1).orElseThrow().phase(), "Combat did not enter FIGHTING.");
		fixture.stop();
	}

	private static void testCastPulse()
	{
		final Fixture fixture = fixture(1);
		fixture.capabilities = capabilities(PhantomCombatMode.RANGED_MAGIC, List.of(new SelectedSkill(20, 1)));
		fixture.lease.supportedSkills.add(20);
		fixture.start(1, PhantomCombatMode.RANGED_MAGIC);
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.lease.castCount, "Magic pulse did not issue CAST.");
		fixture.stop();
	}

	private static void testActionCoalescing()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.attackOutcome = ActionOutcome.ALREADY_OWNED;
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.lease.attackCount, "Coalesced attack did not observe ownership.");
		PhantomAssertions.assertEquals(0L, fixture.service.metrics().normalAttacks(), "Already-owned attack was recorded as newly issued.");
		fixture.stop();
	}

	private static void testShotActivated()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.shotOutcome = ShotOutcome.ACTIVATED;
		fixture.startShots();
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1L, fixture.service.metrics().shotsActivated(), "Activated shot metric changed.");
		fixture.stop();
	}

	private static void testShotUnavailable()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.shotOutcome = ShotOutcome.UNAVAILABLE;
		fixture.startShots();
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1L, fixture.service.metrics().shotsUnavailable(), "Unavailable shot metric changed.");
		PhantomAssertions.assertEquals(1, fixture.lease.attackCount, "Unavailable shot prevented normal attack.");
		fixture.stop();
	}

	private static void testShotFailure()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.shotOutcome = ShotOutcome.FAILED;
		fixture.startShots();
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1L, fixture.service.metrics().shotsFailed(), "Shot failure metric changed.");
		fixture.stop();
	}

	private static void testLowHp()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.actor = actor(15, false);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.LOW_HP_STOPPED);
	}

	private static void testPlayerDeath()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.lease.actor = actor(0, true);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.PLAYER_DEAD);
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Player death leaked the actor lease.");
	}

	private static void testTargetDeath()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.lease.target = target(true, true);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.VICTORY);
	}

	private static void testTargetLoss()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.lease.target = null;
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.TARGET_LOST);
	}

	private static void testTimeout()
	{
		final Fixture fixture = fixture(1);
		fixture.service.startSession(new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, false, false, 1000, fixture.token));
		fixture.clock.set(1_000_000_002L);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.TIMEOUT);
	}

	private static void testTokenCancellation()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.cancelled.set(true);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.CANCELLED);
	}

	private static void testLootVictory()
	{
		final Fixture fixture = fixture(1);
		fixture.service.startSession(new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, false, true, 30_000, fixture.token));
		fixture.lease.target = target(true, true);
		fixture.lease.loot = List.of(new LootCandidate(500, 57, 1, 0));
		fixture.dispatcher.runNext();
		fixture.dispatcher.runNext();
		fixture.lease.lootObservation = LootObservation.ACQUIRED_BY_ACTOR;
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.VICTORY_LOOTED);
	}

	private static void testLootBlocked()
	{
		final Fixture fixture = fixture(1);
		fixture.service.startSession(new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, false, true, 30_000, fixture.token));
		fixture.lease.target = target(true, true);
		fixture.lease.loot = List.of(new LootCandidate(500, 57, 1, 0));
		fixture.lease.pickupOutcome = ActionOutcome.REJECTED;
		fixture.dispatcher.runNext();
		fixture.dispatcher.runNext();
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.VICTORY_LOOT_BLOCKED);
	}

	private static void testLootPickupInProgress()
	{
		final Fixture fixture = fixture(1);
		fixture.service.startSession(new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, false, true, 30_000, fixture.token));
		fixture.lease.target = target(true, true);
		fixture.lease.loot = List.of(new LootCandidate(500, 57, 1, 0));
		fixture.dispatcher.runNext();
		fixture.dispatcher.runNext();
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(PhantomCombatPhase.LOOTING, fixture.service.find(1).orElseThrow().phase(), "In-flight canonical pickup was completed before World removal.");
		fixture.lease.lootObservation = LootObservation.ACQUIRED_BY_ACTOR;
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.VICTORY_LOOTED);
	}

	private static void testBackendException()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.lease.throwOnSnapshot = true;
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.BACKEND_FAILURE);
	}

	private static void testDispatchFailure()
	{
		final Fixture fixture = fixture(1);
		fixture.dispatcher.reject = true;
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		assertTerminal(fixture, PhantomCombatResult.BACKEND_FAILURE);
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Dispatch failure retained worker ownership.");
	}

	private static void testOneWorker()
	{
		final Fixture fixture = fixture(4);
		fixture.backend.leases.put(2L, new FakeLease());
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.start(2, PhantomCombatMode.MELEE_PHYSICAL);
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().currentWorkers(), "More than one shared worker was claimed.");
		PhantomAssertions.assertEquals(1, fixture.dispatcher.dispatches, "A per-profile worker was scheduled.");
		fixture.stop();
	}

	private static void testStop()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.service.beginStop();
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Stop did not release the combat lease.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Stop did not cancel the scheduled shared worker.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Quiescent combat service did not stop.");
	}

	private static void testTerminalConsume()
	{
		final Fixture fixture = fixture(1);
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.lease.target = target(true, true);
		fixture.dispatcher.runNext();
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().terminalSessions(), "Terminal result was not retained.");
		PhantomAssertions.assertTrue(fixture.service.consumeTerminal(1).isPresent(), "Terminal result was not consumable.");
		PhantomAssertions.assertTrue(fixture.service.find(1).isEmpty(), "Consumed terminal slot remained.");
	}

	private static void testRespawn()
	{
		final Fixture fixture = fixture(1);
		fixture.lease.respawnOutcome = RespawnOutcome.COMPLETED;
		PhantomAssertions.assertEquals(RespawnOutcome.COMPLETED, fixture.service.respawnTown(new PhantomRespawnRequest(1, fixture.token)), "Respawn outcome was not propagated.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Respawn action lease leaked.");
		fixture.service.beginStop();
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Idle respawn fixture did not stop.");
	}

	private static void testObservationBound()
	{
		final Fixture fixture = fixture(1);
		final List<ThreatObservation> observations = new ArrayList<>();
		for (int index = 0; index < 17; index++)
		{
			observations.add(new ThreatObservation(index + 200, 1));
		}
		fixture.lease.attackers = observations;
		fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL);
		fixture.dispatcher.runNext();
		assertTerminal(fixture, PhantomCombatResult.BACKEND_FAILURE);
	}

	private static void testCancelWaitsForPulse() throws Exception
	{
		final Fixture fixture = fixture(1);
		PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "In-flight cancellation fixture did not start.");
		fixture.lease.snapshotEntered = new CountDownLatch(1);
		fixture.lease.snapshotRelease = new CountDownLatch(1);
		final AtomicReference<Throwable> pulseFailure = new AtomicReference<>();
		final Thread pulse = new Thread(() ->
		{
			try
			{
				fixture.dispatcher.runNext();
			}
			catch (Throwable throwable)
			{
				pulseFailure.set(throwable);
			}
		}, "Task012-combat-pulse");
		final AtomicBoolean cancelReturned = new AtomicBoolean();
		final AtomicReference<CancelStatus> cancelResult = new AtomicReference<>();
		final AtomicReference<Throwable> cancelFailure = new AtomicReference<>();
		final CountDownLatch cancelEntered = new CountDownLatch(1);
		final Thread cancel = new Thread(() ->
		{
			cancelEntered.countDown();
			try
			{
				cancelResult.set(fixture.service.cancel(1));
			}
			catch (Throwable throwable)
			{
				cancelFailure.set(throwable);
			}
			finally
			{
				cancelReturned.set(true);
			}
		}, "Task012-combat-cancel");
		try
		{
			pulse.start();
			await(fixture.lease.snapshotEntered, "Shared pulse did not enter the actor lease.");
			cancel.start();
			await(cancelEntered, "Cancellation thread did not start.");
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while ((fixture.service.find(1).orElseThrow().result() != PhantomCombatResult.CANCELLED) && (System.nanoTime() < deadline))
			{
				Thread.yield();
			}
			PhantomAssertions.assertEquals(PhantomCombatResult.CANCELLED, fixture.service.find(1).orElseThrow().result(), "Cancellation did not publish its terminal result.");
			PhantomAssertions.assertFalse(cancelReturned.get(), "Cancellation returned while the shared pulse still owned the actor.");
			PhantomAssertions.assertEquals(0, fixture.lease.closeCount, "Cancellation closed the actor lease during an in-flight pulse.");
		}
		finally
		{
			fixture.lease.snapshotRelease.countDown();
			pulse.join(TimeUnit.SECONDS.toMillis(5));
			cancel.join(TimeUnit.SECONDS.toMillis(5));
		}
		PhantomAssertions.assertFalse(pulse.isAlive() || cancel.isAlive(), "In-flight cancellation threads did not terminate.");
		if (pulseFailure.get() != null)
		{
			throw new AssertionError("Shared pulse failed.", pulseFailure.get());
		}
		if (cancelFailure.get() != null)
		{
			throw new AssertionError("Cancellation failed.", cancelFailure.get());
		}
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, cancelResult.get(), "Exact in-flight cancellation was not accepted.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "In-flight cancellation did not release the actor lease exactly once.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "In-flight cancellation retained actor ownership.");
		PhantomAssertions.assertTrue(fixture.service.consumeTerminal(1).isPresent(), "Cancelled terminal session was not consumable.");
		fixture.service.beginStop();
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "In-flight cancellation fixture did not stop.");
	}

	private static void testDispatchFailureReservedStart() throws Exception
	{
		final Fixture fixture = fixture(2);
		final FakeLease secondLease = new FakeLease();
		fixture.backend.leases.put(2L, secondLease);
		fixture.backend.blockingProfileId = 2;
		fixture.backend.acquireEntered = new CountDownLatch(1);
		fixture.backend.acquireRelease = new CountDownLatch(1);
		final AtomicReference<PhantomCombatService.StartResult> secondResult = new AtomicReference<>();
		final AtomicReference<Throwable> secondFailure = new AtomicReference<>();
		final Thread secondStart = new Thread(() ->
		{
			try
			{
				secondResult.set(fixture.start(2, PhantomCombatMode.MELEE_PHYSICAL));
			}
			catch (Throwable throwable)
			{
				secondFailure.set(throwable);
			}
		}, "Task012-combat-reserved-start");
		try
		{
			secondStart.start();
			await(fixture.backend.acquireEntered, "Second session did not reserve before dispatch failure.");
			fixture.dispatcher.reject = true;
			PhantomAssertions.assertEquals(StartStatus.ACCEPTED, fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL).status(), "Primary dispatch-failure session was not initially accepted.");
		}
		finally
		{
			fixture.backend.acquireRelease.countDown();
			secondStart.join(TimeUnit.SECONDS.toMillis(5));
		}
		PhantomAssertions.assertFalse(secondStart.isAlive(), "Reserved start did not reconcile after dispatch failure.");
		if (secondFailure.get() != null)
		{
			throw new AssertionError("Reserved start failed unexpectedly.", secondFailure.get());
		}
		PhantomAssertions.assertEquals(StartStatus.CANCELLED, secondResult.get().status(), "Terminalized reserved session was published.");
		PhantomAssertions.assertEquals(1, secondLease.closeCount, "Rejected reserved session did not release its unowned lease.");
		PhantomAssertions.assertTrue(fixture.service.find(2).isEmpty(), "Rejected reserved session remained retained.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "Dispatch failure retained actor ownership.");
		PhantomAssertions.assertTrue(fixture.service.consumeTerminal(1).isPresent(), "Primary dispatch-failure result was not consumable.");
		PhantomAssertions.assertEquals(0, fixture.service.metrics().currentSessions(), "Dispatch-failure reconciliation underflowed or retained current-session metrics.");
		fixture.service.beginStop();
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Dispatch-failure reconciliation fixture did not stop.");
	}

	private static void testCancelWaitsForStart() throws Exception
	{
		final Fixture fixture = fixture(1);
		fixture.backend.blockingProfileId = 1;
		fixture.backend.acquireEntered = new CountDownLatch(1);
		fixture.backend.acquireRelease = new CountDownLatch(1);
		final AtomicReference<PhantomCombatService.StartResult> startResult = new AtomicReference<>();
		final AtomicReference<Throwable> startFailure = new AtomicReference<>();
		final Thread start = new Thread(() ->
		{
			try
			{
				startResult.set(fixture.start(1, PhantomCombatMode.MELEE_PHYSICAL));
			}
			catch (Throwable throwable)
			{
				startFailure.set(throwable);
			}
		}, "Task012-combat-in-flight-start");
		final AtomicBoolean cancelReturned = new AtomicBoolean();
		final AtomicReference<CancelStatus> cancelResult = new AtomicReference<>();
		final Thread cancel = new Thread(() ->
		{
			cancelResult.set(fixture.service.cancel(1));
			cancelReturned.set(true);
		}, "Task012-combat-start-cancel");
		try
		{
			start.start();
			await(fixture.backend.acquireEntered, "Combat start did not reserve before cancellation.");
			cancel.start();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (fixture.service.find(1).map(snapshot -> snapshot.result() != PhantomCombatResult.CANCELLED).orElse(true) && (System.nanoTime() < deadline))
			{
				Thread.yield();
			}
			PhantomAssertions.assertEquals(PhantomCombatResult.CANCELLED, fixture.service.find(1).orElseThrow().result(), "Cancellation did not terminalize the reserved start.");
			PhantomAssertions.assertFalse(cancelReturned.get(), "Cancellation returned before in-flight start ownership reconciled.");
			PhantomAssertions.assertEquals(0, fixture.lease.closeCount, "Cancellation closed an actor lease before acquisition completed.");
		}
		finally
		{
			fixture.backend.acquireRelease.countDown();
			start.join(TimeUnit.SECONDS.toMillis(5));
			cancel.join(TimeUnit.SECONDS.toMillis(5));
		}
		PhantomAssertions.assertFalse(start.isAlive() || cancel.isAlive(), "In-flight start cancellation threads did not terminate.");
		if (startFailure.get() != null)
		{
			throw new AssertionError("In-flight start failed unexpectedly.", startFailure.get());
		}
		PhantomAssertions.assertEquals(StartStatus.CANCELLED, startResult.get().status(), "Cancelled reserved start was published.");
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_CLEAN, cancelResult.get(), "Reserved start cancellation was not accepted.");
		PhantomAssertions.assertEquals(1, fixture.lease.closeCount, "Cancelled reserved start did not release its acquired lease.");
		PhantomAssertions.assertTrue(fixture.service.find(1).isEmpty(), "Cancelled reserved start retained a terminal slot.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().actorLeases(), "Cancelled reserved start retained actor ownership.");
		PhantomAssertions.assertEquals(0, fixture.service.metrics().currentSessions(), "Cancelled reserved start changed current-session metrics.");
		fixture.service.beginStop();
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "In-flight start cancellation fixture did not stop.");
	}

	private static void await(CountDownLatch latch, String message) throws InterruptedException
	{
		PhantomAssertions.assertTrue(latch.await(5, TimeUnit.SECONDS), message);
	}

	private static void assertTerminal(Fixture fixture, PhantomCombatResult expected)
	{
		final var snapshot = fixture.service.find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomCombatPhase.TERMINAL, snapshot.phase(), "Session did not become terminal.");
		PhantomAssertions.assertEquals(expected, snapshot.result(), "Terminal result changed.");
	}

	private static Fixture fixture(int capacity)
	{
		return new Fixture(capacity);
	}

	private static List<CapabilityEvidence> capabilities(PhantomCombatMode mode, List<SelectedSkill> skills)
	{
		return List.of(new CapabilityEvidence(mode.capabilityKey(), 900, skills));
	}

	private static ActorSnapshot actor(double hp, boolean dead)
	{
		return new ActorSnapshot(10, 88, 0, hp, 100, 100, 100, 50, 100, dead, dead, false, false, false, 0, "IDLE", 0, 0);
	}

	private static TargetSnapshot target(boolean dead, boolean valid)
	{
		return new TargetSnapshot(100, 20001, 0, dead ? 0 : 100, 100, dead, dead, valid, valid, false, valid, valid, 10, false, valid);
	}

	private static final class Fixture
	{
		private final AtomicLong clock = new AtomicLong(1);
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token = cancelled::get;
		private final ManualDispatcher dispatcher = new ManualDispatcher();
		private final FakeBackend backend = new FakeBackend();
		private final FakeLease lease = new FakeLease();
		private List<CapabilityEvidence> capabilities = capabilities(PhantomCombatMode.MELEE_PHYSICAL, List.of());
		private final PhantomCombatService service;

		private Fixture(int capacity)
		{
			backend.leases.put(1L, lease);
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> capabilities);
			service = new PhantomCombatService(backend, resolver, PhantomCombatPolicy.productionDefaults(capacity), new PhantomCombatMetrics(), clock::get, dispatcher);
			service.start();
		}

		private PhantomCombatRequest request(long profileId, PhantomCombatMode mode)
		{
			return new PhantomCombatRequest(profileId, 100, mode, false, false, 30_000, token);
		}

		private PhantomCombatService.StartResult start(long profileId, PhantomCombatMode mode)
		{
			return service.startSession(request(profileId, mode));
		}

		private void startShots()
		{
			service.startSession(new PhantomCombatRequest(1, 100, PhantomCombatMode.MELEE_PHYSICAL, true, false, 30_000, token));
		}

		private void stop()
		{
			service.beginStop();
			if (dispatcher.next != null)
			{
				dispatcher.runNext();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "Combat fixture did not stop.");
			PhantomAssertions.assertEquals(ServiceState.STOPPED, service.snapshot().state(), "Combat fixture state changed.");
		}
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable next;
		private ManualHandle handle;
		private int dispatches;
		private boolean reject;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			if (reject)
			{
				throw new IllegalStateException("injected dispatch rejection");
			}
			PhantomAssertions.assertEquals(250L, delayMillis, "Combat worker delay changed.");
			PhantomAssertions.assertTrue(next == null, "More than one worker was scheduled.");
			next = runnable;
			handle = new ManualHandle(this);
			dispatches++;
			return DispatchResult.accepted(handle);
		}

		private void runNext()
		{
			final Runnable runnable = next;
			PhantomAssertions.assertTrue(runnable != null, "No combat worker was scheduled.");
			next = null;
			final ManualHandle exactHandle = handle;
			handle = null;
			exactHandle.run(runnable);
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

		private void run(Runnable runnable)
		{
			_state = DispatchState.RUNNING;
			try
			{
				runnable.run();
			}
			finally
			{
				_state = DispatchState.FINISHED;
			}
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

	private static final class FakeBackend implements PhantomCombatBackend
	{
		private final Map<Long, FakeLease> leases = new HashMap<>();
		private boolean reject;
		private long blockingProfileId;
		private CountDownLatch acquireEntered;
		private CountDownLatch acquireRelease;
		private int acquireCount;

		@Override
		public PhantomCombatActorLease tryAcquireActor(long profileId)
		{
			acquireCount++;
			if ((profileId == blockingProfileId) && (acquireEntered != null) && (acquireRelease != null))
			{
				acquireEntered.countDown();
				try
				{
					acquireRelease.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while acquiring the test actor lease.", e);
				}
			}
			return reject ? null : leases.get(profileId);
		}
	}

	private static final class FakeLease implements PhantomCombatActorLease
	{
		private ActorSnapshot actor = actor(100, false);
		private TargetSnapshot target = target(false, true);
		private final Set<Integer> supportedSkills = new HashSet<>();
		private List<ThreatObservation> attackers = List.of();
		private List<LootCandidate> loot = List.of();
		private LootObservation lootObservation = LootObservation.PENDING;
		private ShotOutcome shotOutcome = ShotOutcome.UNAVAILABLE;
		private ActionOutcome attackOutcome = ActionOutcome.ISSUED;
		private ActionOutcome castOutcome = ActionOutcome.ISSUED;
		private ActionOutcome pickupOutcome = ActionOutcome.ISSUED;
		private RespawnOutcome respawnOutcome = RespawnOutcome.REJECTED;
		private boolean throwOnSnapshot;
		private CountDownLatch snapshotEntered;
		private CountDownLatch snapshotRelease;
		private int attackCount;
		private int castCount;
		private int closeCount;

		@Override
		public ActorSnapshot actorSnapshot()
		{
			if (throwOnSnapshot)
			{
				throw new IllegalStateException("injected backend failure");
			}
			if ((snapshotEntered != null) && (snapshotRelease != null))
			{
				snapshotEntered.countDown();
				try
				{
					snapshotRelease.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted during the test actor snapshot.", e);
				}
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
			return supportedSkills.contains(skill.skillId());
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return attackers;
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return loot;
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return lootObservation;
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return shotOutcome;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			attackCount++;
			return attackOutcome;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			castCount++;
			return castOutcome;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return pickupOutcome;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			return respawnOutcome;
		}

		@Override
		public void close()
		{
			closeCount++;
		}
	}
}
