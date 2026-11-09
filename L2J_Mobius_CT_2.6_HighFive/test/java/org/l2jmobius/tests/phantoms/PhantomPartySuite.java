/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ExternalOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;

import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ContentBindingRequest;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ContentBindingResult;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStateCodec;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleDefinition;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PreparationOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.phantoms.rift.L2jPhantomRiftPartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CanonicalRoster;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.BindingStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteObservation;
import org.l2jmobius.gameserver.phantoms.semantic.PhantomPartySemanticActs;
import org.l2jmobius.gameserver.phantoms.semantic.PhantomSemanticAct;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomPartySuite implements PhantomTestSuite
{
	public enum Mode
	{
		CANONICAL_INVITATION,
		STATE_RECOVERY,
		ROLE_VACANCY,
		SEMANTIC_ACTS,
		ROUTE,
		ROUTE_CLOSURE,
		ROUTE_FAILURE_CLOSURE,
		TACTICS,
		LIFECYCLE,
		SERVER_INTEGRATION,
		PERFORMANCE
	}

	private static final String ZERO = "0".repeat(64);
	private final Mode _mode;

	public PhantomPartySuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "party-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CANONICAL_INVITATION -> canonical(registry);
			case STATE_RECOVERY -> state(registry);
			case ROLE_VACANCY -> roles(registry);
			case SEMANTIC_ACTS -> semantics(registry);
			case ROUTE -> route(registry);
			case ROUTE_CLOSURE -> routeClosure(registry);
			case ROUTE_FAILURE_CLOSURE -> routeFailureClosure(registry);
			case TACTICS -> tactics(registry);
			case LIFECYCLE -> lifecycle(registry);
			case SERVER_INTEGRATION -> integration(registry);
			case PERFORMANCE -> performance(registry);
		}
	}

	static void registerCoordination130(PhantomTestRegistry registry)
	{
		registry.add("15-live-page-priority-preserves-cold-recovery-fairness", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true; backend._farmLevel = 78;
			final var logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
			for (long leader = 1; leader < 301; leader += 3) { logical.agree(PhantomWorldCoordination130Suite.group(leader, 3)).orElseThrow(); }
			final var live = java.util.stream.LongStream.iterate(1000, id -> id + 3).limit(16).boxed().toList();
			for (long id : live)
			{
				logical.agree(PhantomWorldCoordination130Suite.group(id, 3)).orElseThrow();
				final var actor = backend.add(id, 10000 + (int) id); goals.put(id, supportTravelRoot(id, 0, "farm.fixture"));
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var millis = new AtomicLong(1000);
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Priority fixture did not start.");
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> true, null,
					org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults(), (_, _, _, _, _) -> false, millis::get);
				adapter.installLogicalParty(logical, (_, _) -> new PhantomAutonomousFarmParty.LogicalPage(List.of(), 0), (_, _) -> { }, _ -> true);
				for (int slice = 0; slice < 3; slice++) { adapter.onPulse(); clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); }
				for (long id : live)
				{ PhantomAssertions.assertEquals("WAIT_PREPARATION_" + id, logical.groupFor(id).orElseThrow().group().waitReason(), "A later live group waited for the cold inventory turn."); }
				for (int slice = 0; slice < 16; slice++) { adapter.onPulse(); clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); }
				PhantomAssertions.assertTrue(states._logical.groups.entrySet().stream().filter(entry -> entry.getKey() < 301).allMatch(entry -> entry.getValue().group().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.BACKGROUND), "Live groups starved cold recovery.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Priority bypassed mandatory preparation.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Priority fixture did not stop."); }
		});
		registry.add("distant-logical-roster-invites-without-solo-farm-travel", context ->
		{
			for (int size : List.of(2, 9))
			{
				final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
				backend._farmEnabled = true;
				final var members = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member>();
				for (long id = 1; id <= size; id++)
				{
					goals.put(id, supportTravelRoot(id, 0, "farm.fixture")); final var actor = backend.add(id, 100 + (int) id);
					final var before = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0)));
					backend.snapshot(actor, new MemberSnapshot(actor, before.classId(), 0, (int) id * 2000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(901), before.capabilities(), ZERO));
					members.add(new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(id, "damage", 129000 + id, 0));
				}
				final var logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
				final var group = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty("A".repeat(64), 1, 1, members,
					new PhantomDomainRef("background.farm", "20101@farm.fixture"), "farm.fixture", org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.AGREED, 123, 1000, "");
				logical.agree(group).orElseThrow();
				final var clock = new AtomicLong(1_000_000_000L); final var millis = new AtomicLong(1000);
				final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
				try
				{
					final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> false,
						(_, _) -> { throw new AssertionError("Distant actors were sent individually to FARM."); },
						new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true),
						(_, _, _, _, _) -> { throw new AssertionError("Distant actors adopted a FARM root before meeting."); }, millis::get);
					adapter.installLogicalParty(logical, (_, _) -> new PhantomAutonomousFarmParty.LogicalPage(List.of(), 0), (_, _) -> { }, _ -> true);
					for (int pulse = 0; pulse < 30; pulse++)
					{
						adapter.onPulse();
						for (int step = 0; step < 4; step++) { coordinator.onPulse(); }
						clock.addAndGet(6_000_000_000L); millis.addAndGet(6000);
					}
					final var physical = backend.observe(backend._members.get(1L)).orElseThrow();
					PhantomAssertions.assertEquals(size, physical.members().size(), "Stock-permitted invitation was restricted by distance.");
					PhantomAssertions.assertEquals(size - 1, backend.invitationCount(), "Distant stock formation duplicated invitations.");
					final var retained = logical.groupFor(1).orElseThrow().group();
					PhantomAssertions.assertEquals(123L, retained.backgroundCursor(), "Distant invitation changed confirmed group time.");
					PhantomAssertions.assertEquals(group.generation(), retained.generation(), "Distant invitation replaced the logical generation.");
				}
				finally { coordinator.beginStop(); coordinator.finishStop(); }
			}
		});
		registry.add("execution-released-source-actor-is-not-an-old-group-projection", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true; final var actor = backend.add(1, 101);
			final var logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
			var row = logical.agree(PhantomWorldCoordination130Suite.group(1, 3)).orElseThrow();
			for (var phase : List.of(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.WAKE_REQUESTED,
				org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.ASSEMBLING, org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.NATIVE_ACTIVE,
				org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.DRAINING))
			{ row = logical.transition(row, phase, 0, 1000, ""); }
			final var clock = new AtomicLong(1_000_000_000L); final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> true, null,
					org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults(), (_, _, _, _, _) -> false, () -> 1000);
				adapter.installLogicalParty(logical, (_, _) -> new PhantomAutonomousFarmParty.LogicalPage(List.of(), 0), (_, _) -> { }, _ -> true);
				adapter.installExecutionOwnership((_, _) -> false);
				adapter.onPulse();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.BACKGROUND, logical.groupFor(1).orElseThrow().group().phase(), "A released Source actor permanently blocked old group draining.");
				PhantomAssertions.assertTrue(backend.materializationEpoch(actor) > 0, "Releasing group custody retired the Source actor.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Old group borrowed Source execution after release.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Released custody fixture did not stop."); }
		});
		registry.add("05-zero-world-group-partial-projection-drain-and-recreation", context ->
		{
			for (boolean pendingArrival : List.of(false, true))
			{
			for (boolean absentLeader : List.of(false, true))
			{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true; backend._farmLevel = 78;
			final var clock = new AtomicLong(1_000_000_000L); final var millis = new AtomicLong(1000);
			final var seeking = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults();
			final var source = new PhantomDomainRef("background.farm", "20101@farm.fixture");
			for (long id : List.of(1L, 3L, 8L)) { goals.put(id, supportTravelRoot(id, 0, "farm.fixture")); }
			for (long id : List.of(1L, 3L, 8L)) { backend.add(id, 100 + (int) id); backend._restartAbsent.add(id); }
			final var group = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty("A".repeat(64), 1, 1,
				List.of(1L, 3L, 8L).stream().map(id -> new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(id, "damage", 129000 + id, 0)).toList(),
				source, "farm.fixture", org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
			var logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
			PhantomAssertions.assertTrue(logical.agree(group).isPresent(), "Three absent durable profiles did not agree.");
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Native projection fixture did not start.");
			final var keep = new java.util.concurrent.atomic.AtomicBoolean(true); final var wake = new java.util.concurrent.atomic.AtomicInteger();
			final var preparing = new java.util.concurrent.atomic.AtomicBoolean(false); final var nativeRouteCalls = new java.util.concurrent.atomic.AtomicInteger();
			final var publicationPending = new java.util.concurrent.atomic.AtomicBoolean(pendingArrival);
			final var handoffAllowed = new java.util.concurrent.atomic.AtomicBoolean(true);
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> preparing.get(),
					(id, root) -> new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(pendingArrival ? org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.PENDING : org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.ARRIVED,
						root.goalId(), root.revision(), backend._members.get(id).characterObjectId(), backend._nativeEpoch, 0, "fixture.model_arrived"), seeking,
					new PhantomAutonomousFarmParty.RendezvousPreparation()
					{
						@Override public boolean prepare(long id, PhantomGoal root, PhantomDomainRef sourceRef, long expiry, java.util.function.BooleanSupplier consent)
						{
							throw new AssertionError("Logical rendezvous delegated to Solo FARM preparation.");
						}
						@Override public java.util.Optional<PhantomNavigationPoint> nativePartyDestination(PhantomDomainRef anchor)
						{
							nativeRouteCalls.incrementAndGet();
							return java.util.Optional.of(new PhantomNavigationPoint(0, 0, 0, 0));
						}
						@Override public boolean yieldForNativeParty(MemberRef actor, long epoch, PhantomGoal root, java.util.function.BooleanSupplier current)
						{
							if (publicationPending.get() || !handoffAllowed.get() || !current.getAsBoolean()) { return false; }
							final var before = backend.memberSnapshot(actor).orElseThrow();
							backend.snapshot(actor, new MemberSnapshot(actor, before.classId(), before.instanceId(), before.x(), before.y(), before.z(), before.hpPercent(), before.mpPercent(), before.cpPercent(), before.dead(), before.casting(), before.attacking(), false, before.targetObjectId(), before.attackerObjectIds(), before.capabilities(), before.progressionHash()));
							return true; // Modeled native movement leaf; canonical handoff orchestration is real.
						}
					}, millis::get);
				adapter.installLogicalParty(logical, (_, _) -> new PhantomAutonomousFarmParty.LogicalPage(List.of(), 0), (_, _) -> wake.incrementAndGet(), _ -> keep.get());
				coordinator.installLogicalFarmAdmission(id -> states.loadLogicalClaim(id).isEmpty() || states.loadLogicalGroup(states.loadLogicalClaim(id).orElseThrow().leaderProfileId()).orElseThrow().group().phase() != org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.DRAINING);
				adapter.onPulse();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.BACKGROUND, logical.groupFor(1).orElseThrow().group().phase(), "Zero-World group did not enter Background.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Zero-World agreement fabricated native membership.");
				// A live group must not wait for a complete durable inventory turn before its next transition.
				for (long leader = 1000; leader < 1300; leader += 3)
				{
					final var absent = PhantomWorldCoordination130Suite.group(leader, 3);
					logical.agree(absent).orElseThrow();
				}
				logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
				PhantomAssertions.assertEquals(group.groupId(), logical.groupFor(8).orElseThrow().group().groupId(), "Zero-World recovery lost the group.");
				final long physicalLeader = absentLeader ? 8 : 1;
				for (long id : List.of(physicalLeader, 3L))
				{
					backend._restartAbsent.remove(id); final var actor = backend.currentMember(id).orElseThrow();
					backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(absentLeader && id == 3 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0))));
				}
				if (pendingArrival)
				{
					preparing.set(true);
					for (int slice = 0; slice < 4; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Encounter stole an accepted Supply/preparation owner.");
					PhantomAssertions.assertTrue(logical.groupFor(physicalLeader).orElseThrow().group().waitReason().startsWith("WAIT_PREPARATION_"), "Logical wait did not identify its actual preparation owner.");
					preparing.set(false);
					for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Pending publication did not fence the native invite.");
					handoffAllowed.set(false);
					publicationPending.set(false);
					final var near = backend.memberSnapshot(backend.currentMember(3).orElseThrow()).orElseThrow();
					backend.snapshot(near.ref(), new MemberSnapshot(near.ref(), near.classId(), 0, 5000, 0, 0, 100, 100, 100, false, false, false, false, 0, near.attackerObjectIds(), near.capabilities(), near.progressionHash()));
					for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Distant consent bypassed the unreleased owner.");
					backend.snapshot(near.ref(), new MemberSnapshot(near.ref(), near.classId(), near.instanceId(), near.x(), near.y(), near.z(), 100, 100, 100, false, false, false, true, 0, near.attackerObjectIds(), near.capabilities(), near.progressionHash()));
					for (int slice = 0; slice < 2; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Encounter ignored an unreleased native travel owner.");
					final String obsoleteOffer = coordinator.rendezvous(3).orElseThrow().state().offerId();
					backend._nativeEpochs.put(physicalLeader, backend._nativeEpoch + 1);
					for (int slice = 0; slice < 6; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					PhantomAssertions.assertFalse(obsoleteOffer.equals(coordinator.rendezvous(3).orElseThrow().state().offerId()), "A peer's obsolete epoch imposed its cancelled-offer cooldown on the unchanged actor.");
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Fresh agreement bypassed the unreleased movement owner.");
					handoffAllowed.set(true);
				}
				for (int slice = 0; slice < 32; slice++)
				{
					clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
					if (backend.observe(backend._members.get(physicalLeader)).isPresent() && nativeRouteCalls.get() > 0
						&& logical.groupFor(1).orElseThrow().group().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.NATIVE_ACTIVE) { break; }
				}
				PhantomAssertions.assertTrue(wake.get() > 0, "A materialized member did not issue bounded wake demand.");
				PhantomAssertions.assertTrue(backend.observe(backend._members.get(physicalLeader)).isPresent(), "A physically met logical group could not form before its route checkpoint became quiet; pendingArrival=" + pendingArrival);
				PhantomAssertions.assertEquals(2, backend.observe(backend._members.get(physicalLeader)).orElseThrow().members().size(), "Partial admission could not form a legal pair; absent logical leader=" + absentLeader);
				PhantomAssertions.assertEquals(1, backend.invitationCount(), "Projection repeated the native invitation.");
				PhantomAssertions.assertTrue(nativeRouteCalls.get() > 0, "Committed native Party never reached its own route handoff.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.NATIVE_ACTIVE, logical.groupFor(1).orElseThrow().group().phase(), "Observed Party did not advance the logical group.");
				if (!pendingArrival && !absentLeader)
				{
					// This fixture has no lawful combat target: waiting must yield to the existing drain/replan owner.
					for (int slice = 0; slice < 12; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); }
					final var waiting = logical.groupFor(1).orElseThrow().group();
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.DRAINING, waiting.phase(), "No-target wait never reached its finite safe replan.");
					PhantomAssertions.assertEquals("REPLAN_GROUP_TARGET", waiting.waitReason(), "No-target wait lost its concrete reason.");
				}
				keep.set(false);
				for (int slice = 0; slice < 16; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); coordinator.onPulse(); if (backend._parties.isEmpty()) { break; } }
				PhantomAssertions.assertTrue(backend._parties.isEmpty(), "Drain retained native Party membership; pendingArrival=" + pendingArrival + ";absentLeader=" + absentLeader + ";group=" + logical.groupFor(1).orElseThrow().group().phase() + ";reason=" + logical.groupFor(1).orElseThrow().group().waitReason());
				backend._restartAbsent.addAll(List.of(1L, 3L, 8L)); backend._snapshots.clear(); backend._nativeEpoch++;
				for (int slice = 0; slice < 16; slice++) { clock.addAndGet(6_000_000_000L); millis.addAndGet(6000); adapter.onPulse(); }
				final var restored = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states).groupFor(8).orElseThrow().group();
				PhantomAssertions.assertEquals(group.groupId(), restored.groupId(), "Drain/recreation replaced logical identity.");
				PhantomAssertions.assertEquals(3, restored.members().size(), "Partial native admission truncated the durable roster.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.BACKGROUND, restored.phase(), "Drained zero-World group failed to return to Background.");
				PhantomAssertions.assertTrue(states._logical.groups.values().stream().allMatch(row -> row.group().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.BACKGROUND), "Live priority starved the independent durable recovery cursor.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Projection fixture did not stop."); }
			}
			}
		});
		registry.add("nearby-logical-roster-invites-before-farm-adoption", context ->
		{
			final var states = new MemoryPartyStore();
			final var goals = new MemoryGoalStore();
			final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true;
			backend._farmLevel = 76;
			final var leaderRoot = historicalIdle(1, 129001, 0);
			final var peerRoot = supportTravelRoot(3, 0, "farm.solo");
			goals.put(1, leaderRoot);
			goals.put(3, peerRoot);
			for (long id : List.of(1L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
			}
			final var logical = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator(states);
			final var group = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty("A".repeat(64), 1, 1,
				List.of(new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(1, "damage", 129001, 0),
					new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(3, "damage", 129003, 0)),
				new PhantomDomainRef("background.farm", "20102@farm.shared"), "farm.shared",
				org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
			logical.agree(group).orElseThrow();
			final var clock = new AtomicLong(1_000_000_000L);
			final var millis = new AtomicLong(1000);
			final var action = new PartyActionLease();
			final var combat = cooperativeCombat(action, clock);
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context),
				new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			backend.connect(coordinator);
			coordinator.start();
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> false, null,
					new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true),
					(_, _, _, _, _) -> { throw new AssertionError("Nearby members were sent to FARM before being invited."); }, millis::get);
				adapter.installLogicalParty(logical, (_, _) -> new PhantomAutonomousFarmParty.LogicalPage(List.of(), 0), (_, _) -> { }, _ -> true);
				for (int pulse = 0; pulse < 8; pulse++)
				{
					adapter.onPulse();
					for (int step = 0; step < 4; step++) { coordinator.onPulse(); }
					clock.addAndGet(6_000_000_000L);
					millis.addAndGet(6000);
				}
				PhantomAssertions.assertEquals(1, backend.invitationCount(), "A lawful nearby roster required personal FARM adoption before invite.");
				PhantomAssertions.assertEquals(2, backend.observe(backend._members.get(1L)).orElseThrow().members().size(), "The invite did not complete stock membership.");
				PhantomAssertions.assertEquals(leaderRoot, goals.load(1).orElseThrow().goal(), "Stock membership changed the leader's personal root.");
				PhantomAssertions.assertEquals(peerRoot, goals.load(3).orElseThrow().goal(), "Stock membership changed the peer's personal root.");
				PhantomAssertions.assertEquals(group.groupId(), logical.groupFor(3).orElseThrow().group().groupId(), "Stock membership replaced the logical claim.");
				coordinator.onPulse();
				final var destination = new PhantomNavigationPoint(1000, 0, 0, 0);
				final var wrong = coordinator.requestAutonomousFarmRoute(1, leaderRoot.selectedAnchor(), destination);
				PhantomAssertions.assertEquals("party.route.farm_authority_unavailable", wrong.reasonKey(), "The personal Goal replaced the exact LogicalParty route.");
				final var shared = coordinator.requestAutonomousFarmRoute(1, new PhantomDomainRef("topology.anchor", "farm.shared"), destination);
				PhantomAssertions.assertEquals("party.route.unavailable", shared.reasonKey(), "The exact LogicalParty anchor did not reach the navigation owner.");
				goals.put(1, supportTravelRoot(1, 1, "farm.solo.new"));
				clock.addAndGet(6_000_000_000L);
				for (int pulse = 0; pulse < 5; pulse++) { coordinator.onPulse(); }
				final var retained = backend.observe(backend._members.get(1L)).orElse(null);
				PhantomAssertions.assertTrue(retained != null && retained.members().size() == 2, "A newer personal revision dissolved the same committed LogicalParty.");
				PhantomAssertions.assertEquals(1, backend.invitationCount(), "A lawful personal revision replayed the stock invite.");

				final var topologyBackend = new L2jTopologyValidationBackend();
				final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.empty(topologyBackend, PhantomTopologyPolicy.productionDefaults()),
					topologyBackend, new PhantomTopologyMetrics());
				coordinator.installAutonomousFarmTravel(PhantomNormalGatekeeperTravel.empty(topology), _ -> Optional.of(destination));
				final var leader = backend._members.get(1L);
				final var peer = backend._members.get(3L);
				final var buff = cap("combat.buff", "maintenance", 450, 1040);
				final var heal = cap("combat.heal", "urgent", 450, 1011);
				final var weapon = List.of(cap("combat.melee_damage", "weapon", 300, 0));
				backend.snapshot(leader, new MemberSnapshot(leader, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), weapon, ZERO));
				backend.snapshot(peer, new MemberSnapshot(peer, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), weapon, ZERO));
				backend._capabilities.put(leader, List.of(buff, heal));
				coordinator.onPulse();
				PhantomAssertions.assertEquals(0, action.casts, "Maintenance can repeatedly block completion of the stock Party WALK/GK itinerary.");
				backend.snapshot(peer, new MemberSnapshot(peer, 0, 0, 0, 0, 0, 40, 100, 100, false, false, false, false, 0, List.of(), weapon, ZERO));
				coordinator.onPulse();
				PhantomAssertions.assertEquals(1, action.casts, "Travel prevented a Party member from receiving urgent Heal.");
				PhantomAssertions.assertEquals("combat.heal", action.support.capabilityKey(), "Maintenance took the urgent support owner.");
			}
			finally
			{
				coordinator.beginStop();
				coordinator.finishStop();
				combat.beginStop();
				PhantomAssertions.assertTrue(combat.finishStop(), "Party support owner did not drain.");
			}
		});
		registry.add("06-six-member-native-projection-can-request-next-farm-pull", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			final var newPull = new java.util.concurrent.atomic.AtomicBoolean(true); final var cancelledRequests = new java.util.concurrent.atomic.AtomicInteger();
			final var clock = new AtomicLong(1_000_000_000L); final var lease = new PartyActionLease();
			lease.target = new TargetSnapshot(900, 20001, 0, 1000, 1000, false, false, true, true, false, true, true, 50, false, true);
			final var combat = new PhantomCombatService(id -> id == 1 ? lease : null, new PhantomCombatCapabilityResolver(_ -> List.of()),
				PhantomCombatPolicy.productionDefaults(8), new PhantomCombatMetrics(), clock::get, (_, _) -> PhantomCombatService.DispatchResult.accepted(new PhantomCombatService.DispatchHandle()
			{
				private boolean cancelled;
				@Override public boolean cancelIfNotStarted() { if (cancelled) { return false; } cancelled = true; cancelledRequests.incrementAndGet(); return true; }
				@Override public PhantomCombatService.DispatchState state() { return cancelled ? PhantomCombatService.DispatchState.CANCELLED : PhantomCombatService.DispatchState.SCHEDULED; }
			}));
			combat.start();
			final var routeSubmissions = new java.util.concurrent.atomic.AtomicInteger();
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), new PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(PhantomNavigationPoint from, PhantomNavigationPoint to) { routeSubmissions.incrementAndGet(); return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1); }
				@Override public boolean canMoveDirect(PhantomNavigationPoint from, PhantomNavigationPoint to) { return true; }
				@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token) { return List.of(request.origin(), request.destination()); }
			}, worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
			navigation.start();
			final var ids = java.util.stream.LongStream.rangeClosed(1, 6).boxed().toList();
			for (long id : ids) { final var actor = backend.add(id, 100 + (int) id); goals.put(id, supportTravelRoot(id, 0, "farm.fixture")); backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)))); backend._capabilities.put(actor, List.of(cap("combat.melee_damage", "weapon", 100, 0))); }
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(navigation, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			coordinator.installLogicalFarmAdmission(_ -> newPull.get());
			new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				coordinator.formForGoal(1, 129001, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,2,3,4,5,6"), ids.stream().map(id -> new RoleRequirement("dd." + id, "damage.melee", true, 1)).toList());
				for (long id : ids.subList(1, ids.size())) { coordinator.invite(1, backend.currentMember(id).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS); for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(6, backend.observe(backend.currentMember(1).orElseThrow()).orElseThrow().members().size(), "Six-member invitation preparation failed.");
				backend._farmEnabled = false;
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.PENDING, coordinator.requestAutonomousFarmRoute(1, new PhantomDomainRef("topology.anchor", "farm.fixture"), new PhantomNavigationPoint(1000, 0, 0, 0)).outcome(), "A new Party was retired before its readiness/source view could publish.");
				PhantomAssertions.assertEquals(0, routeSubmissions.get(), "Unready new native Party issued a movement action.");
				backend._farmEnabled = true;
				final var route = coordinator.requestAutonomousFarmRoute(1, new PhantomDomainRef("topology.anchor", "farm.fixture"), new PhantomNavigationPoint(1000, 0, 0, 0));
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.READY, route.outcome(), "Native farm did not acquire the existing Party route owner.");
				PhantomAssertions.assertEquals(new PhantomDomainRef("farm.party", "1,2,3,4,5,6"), coordinator.claim(1).orElseThrow().state().objectiveRef(), "Route persistence replaced the farm objective/roster with its destination.");
				clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(31)); coordinator.onPulse();
				backend._farmEnabled = false;
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.PENDING, coordinator.requestAutonomousFarmRoute(1, new PhantomDomainRef("topology.anchor", "farm.fixture"), new PhantomNavigationPoint(1000, 0, 0, 0)).outcome(), "A transient readiness change stole an accepted native Party route.");
				backend._farmEnabled = true;
				PhantomAssertions.assertTrue(coordinator.claim(1).orElseThrow().state().route().status() != org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus.FAILED, "Autonomous journey inherited the ordinary30-second route deadline.");
				for (long id : ids) { final var before = backend.memberSnapshot(backend.currentMember(id).orElseThrow()).orElseThrow(); backend.snapshot(before.ref(), new MemberSnapshot(before.ref(), before.classId(), 0, 1000, 0, 0, before.hpPercent(), before.mpPercent(), before.cpPercent(), false, false, false, false, 0, before.attackerObjectIds(), before.capabilities(), before.progressionHash())); }
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus.ARRIVED, coordinator.claim(1).orElseThrow().state().route().status(), "Modeled native route did not finish under the Party owner.");
				PhantomAssertions.assertEquals(1, routeSubmissions.get(), "A live group route was resubmitted per pulse.");
				backend._farmTarget = java.util.OptionalInt.of(900);
				PhantomAssertions.assertTrue(coordinator.requestAutonomousFarm(1), "A six-member Party was prevented from pulling by the obsolete cap.");
				final long generation = combat.find(1).orElseThrow().generation();
				newPull.set(false);
				PhantomAssertions.assertFalse(coordinator.drainAutonomousFarm(1), "DRAIN released an accepted native episode.");
				PhantomAssertions.assertEquals(0, cancelledRequests.get(), "DRAIN cancelled accepted native work.");
				PhantomAssertions.assertEquals(generation, combat.find(1).orElseThrow().generation(), "DRAIN replaced the accepted episode.");
				PhantomAssertions.assertFalse(coordinator.requestAutonomousFarm(1), "DRAIN admitted another pull.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Six-member owner did not stop."); combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Six-member combat did not stop."); navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Native farm navigation did not stop."); }
		});
	}

	private static void canonical(PhantomTestRegistry registry)
	{
		registry.add("01-both-packet-handlers-delegate", context ->
		{
			final String request = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java");
			final String answer = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java");
			PhantomAssertions.assertTrue(request.contains("PartyInvitationService.getInstance().invite"), "Join handler does not delegate to the canonical service.");
			PhantomAssertions.assertTrue(answer.contains("PartyInvitationService.getInstance()") && answer.contains("service.respond"), "Answer handler does not delegate to the canonical service.");
		});
		registry.add("02-core-is-transport-neutral", context ->
		{
			final String service = source(context, "java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java");
			PhantomAssertions.assertFalse(service.contains("gameserver.phantoms"), "Core invitation service imports Phantom implementation.");
			PhantomAssertions.assertFalse(service.contains("new GameClient"), "Core invitation service creates a fake client.");
			PhantomAssertions.assertTrue(service.contains("DELIVERED_CLIENT") && service.contains("DELIVERED_MANAGED"), "Canonical delivery outcomes are incomplete.");
		});
		registry.add("03-managed-delivery-carries-exact-identity", _ ->
		{
			final var identity = new org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity(7, 101, 202);
			PhantomAssertions.assertEquals(7L, identity.sequence(), "Invitation sequence changed.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity(0, 101, 202), "Zero invitation sequence was accepted.");
		});
		registry.add("04-conversation-response-requires-exact-invitation-and-plan-goal", context ->
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			backend.add(2, 202);
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 16);
			backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Conversation Party fixture did not start.");
			try
			{
				final String refusePlan = PhantomPartyModel.sha256("conversation.refuse");
				final InvitationIdentity refuseIdentity = new InvitationIdentity(101, 777, 202);
				final PartyInvitation refuseInvitation = new PartyInvitation(refuseIdentity, 777, "Requester", 202, "Invitee", PartyDistributionType.FINDERS_KEEPERS, 777, Long.MAX_VALUE);
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(refuseInvitation, OptionalLong.empty(), OptionalLong.of(2)), "Exact real-to-managed invitation was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(refuseInvitation, 2), "Exact invitation was not retained as pending.");
				PhantomAssertions.assertEquals(refuseIdentity, coordinator.pendingInvitation(2).orElseThrow().identity(), "Pending invitation identity changed.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.COMPLETED, coordinator.respondToPending(2, refuseIdentity, PhantomPartyCoordinator.PendingResponse.REFUSE, refusePlan), "Exact refusal did not reach the canonical response path.");
				PhantomAssertions.assertEquals(PartyInvitationService.Response.REFUSE, backend.lastResponse(), "Canonical refusal response kind changed.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.IDEMPOTENT, coordinator.respondToPending(2, refuseIdentity, PhantomPartyCoordinator.PendingResponse.REFUSE, refusePlan), "Same refusal plan was not idempotent.");
				coordinator.terminal(refuseInvitation, OptionalLong.empty(), OptionalLong.of(2), TerminalOutcome.REFUSED, "party.invite.refused");
				coordinator.onPulse();

				final String acceptPlan = PhantomPartyModel.sha256("conversation.accept");
				final InvitationIdentity acceptIdentity = new InvitationIdentity(102, 778, 202);
				final PartyInvitation acceptInvitation = new PartyInvitation(acceptIdentity, 778, "Requester2", 202, "Invitee", PartyDistributionType.FINDERS_KEEPERS, 778, Long.MAX_VALUE);
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(acceptInvitation, OptionalLong.empty(), OptionalLong.of(2)), "Second exact invitation was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(acceptInvitation, 2), "Second exact invitation was not retained.");
				goals.put(2, conversationJoinGoal(acceptPlan, acceptIdentity));
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.COMPLETED, coordinator.respondToPending(2, acceptIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT, acceptPlan, true), "Exact conversation-owned join Goal did not authorize ACCEPT.");
				PhantomAssertions.assertEquals(PartyInvitationService.Response.ACCEPT, backend.lastResponse(), "Canonical accept response kind changed.");
				coordinator.terminal(acceptInvitation, OptionalLong.empty(), OptionalLong.of(2), TerminalOutcome.ACCEPTED, "party.invite.accepted");
				coordinator.onPulse();
				goals.remove(2);

				final String forgedPlan = PhantomPartyModel.sha256("conversation.forged");
				final InvitationIdentity forgedIdentity = new InvitationIdentity(103, 779, 202);
				final PartyInvitation forgedInvitation = new PartyInvitation(forgedIdentity, 779, "Requester3", 202, "Invitee", PartyDistributionType.FINDERS_KEEPERS, 779, Long.MAX_VALUE);
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(forgedInvitation, OptionalLong.empty(), OptionalLong.of(2)), "Forged-control invitation fixture was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(forgedInvitation, 2), "Forged-control invitation was not retained.");
				goals.put(2, conversationJoinGoal(acceptPlan, forgedIdentity));
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.respondToPending(2, forgedIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT, forgedPlan), "A different plan reused another conversation Goal consent.");
				coordinator.terminal(forgedInvitation, OptionalLong.empty(), OptionalLong.of(2), TerminalOutcome.CANCELLED, "party.invite.cancelled");
				coordinator.onPulse();
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Conversation Party fixture did not stop.");
			}
		});
		registry.add("05-managed-pulse-reserves-conversation-owned-accept", context ->
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef invitee = backend.add(2, 202);
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 16);
			PhantomAssertions.assertTrue(coordinator.start(), "Invitation ownership fixture did not start.");
			try
			{
				final InvitationIdentity ordinaryIdentity = new InvitationIdentity(201, 700, invitee.characterObjectId());
				final PartyInvitation ordinary = invitation(ordinaryIdentity, invitee);
				goals.put(2, goal(7001, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "700"), 0));
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(ordinary, OptionalLong.empty(), OptionalLong.of(2)), "Ordinary join invitation was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(ordinary, 2), "Ordinary join invitation was not delivered.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(1, backend.responseCount(), "Ordinary explicit party.join no longer auto-accepts.");
				PhantomAssertions.assertEquals(PartyInvitationService.Response.ACCEPT, backend.lastResponse(), "Ordinary explicit party.join changed response kind.");
				coordinator.terminal(ordinary, OptionalLong.empty(), OptionalLong.of(2), TerminalOutcome.CANCELLED, "party.invite.cancelled");
				coordinator.onPulse();

				final String planId = PhantomPartyModel.sha256("conversation.ownership");
				final InvitationIdentity exactIdentity = new InvitationIdentity(202, 701, invitee.characterObjectId());
				final PartyInvitation exact = invitation(exactIdentity, invitee);
				goals.put(2, conversationJoinGoal(planId, exactIdentity));
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(exact, OptionalLong.empty(), OptionalLong.of(2)), "Conversation-owned invitation was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(exact, 2), "Conversation-owned invitation was not delivered.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(1, backend.responseCount(), "Generic Party pulse consumed conversation-owned consent.");
				PhantomAssertions.assertEquals(exactIdentity, coordinator.pendingInvitation(2).orElseThrow().identity(), "Generic Party pulse removed the conversation-owned invitation.");
				PhantomAssertions.assertTrue(coordinator.conversationResponseOutcome(planId, exactIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT).isEmpty(), "Generic Party pulse fabricated replay proof.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.COMPLETED, coordinator.respondToPending(2, exactIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT, planId, true), "Execution-owned exact response did not complete.");
				PhantomAssertions.assertEquals(2, backend.responseCount(), "Execution-owned exact response was not sent exactly once.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.COMPLETED, coordinator.conversationResponseOutcome(planId, exactIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT).orElseThrow(), "Exact completed replay outcome changed.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.IDEMPOTENT, coordinator.respondToPending(2, exactIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT, planId, true), "Exact response retry was not idempotent.");
				PhantomAssertions.assertEquals(2, backend.responseCount(), "Exact response retry crossed the backend twice.");
				final InvitationIdentity replacement = new InvitationIdentity(203, exactIdentity.requesterObjectId(), exactIdentity.inviteeObjectId());
				PhantomAssertions.assertTrue(coordinator.conversationResponseOutcome(planId, replacement, PhantomPartyCoordinator.PendingResponse.ACCEPT).isEmpty(), "Replacement identity inherited exact replay proof.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.respondToPending(2, replacement, PhantomPartyCoordinator.PendingResponse.ACCEPT, planId, true), "Same plan accepted a replacement invitation.");
				final InvitationIdentity otherRequester = new InvitationIdentity(204, 702, exactIdentity.inviteeObjectId());
				PhantomAssertions.assertTrue(coordinator.conversationResponseOutcome(planId, otherRequester, PhantomPartyCoordinator.PendingResponse.ACCEPT).isEmpty(), "A new requester inherited old conversation consent.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.respondToPending(2, otherRequester, PhantomPartyCoordinator.PendingResponse.ACCEPT, planId, true), "Same plan accepted a new requester.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.respondToPending(2, exactIdentity, PhantomPartyCoordinator.PendingResponse.REFUSE, planId, true), "Same plan accepted a different response kind.");
				PhantomAssertions.assertEquals(2, backend.responseCount(), "Mismatched replay attempt crossed the backend.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Invitation ownership fixture did not stop.");
			}
		});
		registry.add("06-exact-response-outcomes-are-read-only-and-process-local", context ->
		{
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef invitee = backend.add(2, 202);
			final PhantomPartyCoordinator coordinator = coordinator(context, new MemoryPartyStore(), goals, backend, 16);
			PhantomAssertions.assertTrue(coordinator.start(), "Replay outcome fixture did not start.");
			final InvitationIdentity currentIdentity = new InvitationIdentity(301, 800, invitee.characterObjectId());
			final PartyInvitation current = invitation(currentIdentity, invitee);
			final String stalePlan = PhantomPartyModel.sha256("conversation.stale");
			final String rejectedPlan = PhantomPartyModel.sha256("conversation.rejected");
			try
			{
				PhantomAssertions.assertEquals(PreparationOutcome.ACCEPTED, coordinator.prepare(current, OptionalLong.empty(), OptionalLong.of(2)), "Replay fixture invitation was not prepared.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(current, 2), "Replay fixture invitation was not delivered.");
				final InvitationIdentity missing = new InvitationIdentity(300, currentIdentity.requesterObjectId(), currentIdentity.inviteeObjectId());
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.STALE, coordinator.respondToPending(2, missing, PhantomPartyCoordinator.PendingResponse.REFUSE, stalePlan, true), "Missing exact invitation did not become STALE.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.STALE, coordinator.conversationResponseOutcome(stalePlan, missing, PhantomPartyCoordinator.PendingResponse.REFUSE).orElseThrow(), "STALE replay outcome changed.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.respondToPending(2, currentIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT, rejectedPlan, true), "Missing exact Goal consent was not REJECTED.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.PendingResponseOutcome.REJECTED, coordinator.conversationResponseOutcome(rejectedPlan, currentIdentity, PhantomPartyCoordinator.PendingResponse.ACCEPT).orElseThrow(), "REJECTED replay outcome changed.");
				PhantomAssertions.assertEquals(0, backend.responseCount(), "Read-only replay outcomes performed gameplay mutation.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Replay outcome fixture did not stop.");
			}
			final PhantomPartyCoordinator restarted = coordinator(context, new MemoryPartyStore(), goals, backend, 16);
			PhantomAssertions.assertTrue(restarted.start(), "Restarted replay fixture did not start.");
			try
			{
				PhantomAssertions.assertTrue(restarted.conversationResponseOutcome(stalePlan, new InvitationIdentity(300, 800, 202), PhantomPartyCoordinator.PendingResponse.REFUSE).isEmpty(), "Process-local replay proof survived coordinator restart.");
			}
			finally
			{
				restarted.beginStop();
				PhantomAssertions.assertTrue(restarted.finishStop(), "Restarted replay fixture did not stop.");
			}
		});
	}

	private static void state(PhantomTestRegistry registry)
	{
		registry.add("129-rendezvous-codec-expiry-and-current-goal", _ ->
		{
			final var actor = MemberRef.phantom(1, 101);
			final var peer = MemberRef.phantom(2, 202);
			final var source = new PhantomDomainRef("background.farm", "20101@farm.test");
			final var roster = List.of(new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Participant(actor, 10, 11, 0),
				new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Participant(peer, 20, 22, 3));
			final var offer = new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState(1, ZERO, 1, roster, source, 1000, 10000,
				org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.PROPOSED, 0);
			PhantomAssertions.assertEquals(offer, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.decode(offer.encode()), "Restart changed exact offer identities.");
			PhantomAssertions.assertTrue(offer.current(actor, 10, 11, 0, source, 9999), "Exact pre-adoption goal was rejected.");
			PhantomAssertions.assertFalse(offer.current(actor, 11, 11, 0, source, 9999), "New lifetime reused old consent.");
			PhantomAssertions.assertFalse(offer.current(actor, 10, 11, 0, source, 10000), "Expired consent remained usable.");
			PhantomAssertions.assertFalse(offer.current(actor, 10, 11, 2, source, 2000), "Unrelated goal revision reused consent.");
			final var assembling = offer.withPhase(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ASSEMBLING, 1);
			PhantomAssertions.assertTrue(assembling.current(actor, 10, 11, 1, source, 2000), "Adopted shared root was not current.");
			PhantomAssertions.assertFalse(assembling.current(actor, 10, 11, 1, new PhantomDomainRef("background.farm", "20102@other"), 2000), "Unrelated shared source was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.decode(java.util.Arrays.copyOf(offer.encode(), offer.encode().length - 1)), "Truncated durable consent was accepted.");
		});
		registry.add("094-native-restart-receipt-preserves-windows-candidate", _ ->
		{
			final Path candidate = Path.of("C:\\native stage\\candidate-v100");
			final var receipt = new java.util.Properties();
			receipt.load(new java.io.StringReader(PhantomAutonomousParty094Native.completionReceipt(candidate, 56488)));
			PhantomAssertions.assertEquals(candidate.toString(), receipt.getProperty("candidate"), "Properties decoding changed the sealed candidate path.");
			PhantomAssertions.assertEquals("56488", receipt.getProperty("pid"), "Receipt lost the prior JVM identity.");
			PhantomAssertions.assertEquals("PASS", receipt.getProperty("scene"), "Receipt lost the native result.");
		});
		registry.add("094-restart-pending-member-rejects-different-core-operation", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			final var leader = backend.add(10, 110); final var peer = backend.add(11, 111); final var target = backend.add(1, 101);
			goals.put(10, goal(94010, "farm.background", null, 0)); goals.put(1, goal(94001, "farm.background", null, 0));
			final var draft = rawState(leader, List.of(leader, peer), List.of()); final var objective = new PhantomDomainRef("farm.party", "10,1,11");
			final var core = new PartyState(draft.groupId(), 1, 1, StateStatus.LEADER, leader, "", ZERO, List.of(leader, peer), List.of(), ObjectiveMode.GENERAL_PVE, objective, List.of(), List.of(), null, null, ZERO, ZERO, "");
			final String manifest = core.canonicalManifestHash();
			final var pending = new PartyOperation(PhantomPartyModel.sha256("pending.exact"), OperationKind.JOIN, OperationPhase.PREPARED, leader, target, 94010, 0, manifest, 0, 1000, "");
			final var foreign = new PartyOperation(PhantomPartyModel.sha256("pending.different"), OperationKind.JOIN, OperationPhase.PREPARED, leader, target, 94010, 0, manifest, 0, 1000, "");
			states.seed(10, new PartyState(core.groupId(), 1, 1, StateStatus.INVITED_OUTBOUND, leader, "", manifest, core.phantomMembers(), List.of(), core.objectiveMode(), objective, List.of(), List.of(), null, pending, ZERO, ZERO, ""));
			states.seed(11, new PartyState(core.groupId(), 1, 1, StateStatus.MEMBER, leader, "", manifest, core.phantomMembers(), List.of(), core.objectiveMode(), objective, List.of(), List.of(), null, pending.withPhase(OperationPhase.COMMITTED, 1, ""), ZERO, ZERO, ""));
			states.seed(1, new PartyState(core.groupId(), 1, 1, StateStatus.INVITED_INBOUND, leader, "", manifest, List.of(target, leader, peer), List.of(), core.objectiveMode(), objective, List.of(), List.of(), null, foreign, ZERO, ZERO, ""));
			final var coordinator = coordinator(context, states, goals, backend, 16); PhantomAssertions.assertTrue(coordinator.start(), "Partial saga fixture did not start.");
			try { PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(1).orElseThrow().state().status(), "A different pending operation retained consent against another core saga."); }
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Partial saga fixture did not drain."); }
		});
		registry.add("094-regroup-clock-retry-restart-and-cooldown", context ->
		{
			final var actor = MemberRef.phantom(3, 103);
			final var first = org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(null, actor, ZERO, true, 10_000);
			final var retry = org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(first, actor, ZERO, true, 60_000);
			PhantomAssertions.assertEquals(10_000L, retry.farSinceMillis(), "Retry reset the confirmed JOIN distance clock.");
			PhantomAssertions.assertTrue(!retry.due(69_999) && !retry.due(70_000) && retry.due(70_001), "Fallback must require more than 60 seconds.");
			final var reserved = retry.reserve(70_001);
			PhantomAssertions.assertEquals(370_001L, reserved.cooldownUntilMillis(), "Cooldown was not reserved before native work.");
			final var restored = org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.decode(reserved.encode());
			PhantomAssertions.assertEquals(reserved, restored, "Restart lost the distance/cooldown clock.");
			final var changed = org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(restored, actor, "B".repeat(64), true, 90_000);
			PhantomAssertions.assertEquals(90_000L, changed.farSinceMillis(), "New intent retained an old distance clock.");
			PhantomAssertions.assertEquals(reserved.cooldownUntilMillis(), changed.cooldownUntilMillis(), "New intent bypassed cooldown.");
			PhantomAssertions.assertTrue(!changed.due(80_000) && !changed.due(370_000), "Clock rollback or cooldown admitted teleport.");
		});
		registry.add("094-regroup-clock-owner-payload-and-majority-guards", context ->
		{
			final var type = org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(null, MemberRef.phantom(3, 103), ZERO, true, 1000);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(type, MemberRef.phantom(4, 104), ZERO, true, 2000), "Clock changed actor ownership.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(null, MemberRef.real(103), ZERO, true, 2000), "Human acquired teleport clock.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.decode(java.util.Arrays.copyOf(type.encode(), 113)), "Trailing clock data was accepted.");
			PhantomAssertions.assertEquals(type, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(type, type.actor(), ZERO, true, 999), "Clock rollback rewrote the timer.");
			final var first = member(1, 101, 100, 100, false, 0, List.of()); final var second = member(2, 102, 100, 100, false, 0, List.of());
			final var roster = List.of(first.ref(), second.ref(), type.actor());
			PhantomAssertions.assertEquals(2, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.majority(type.actor(), roster, List.of(first, second)).size(), "Local native majority was lost.");
			final var spread = new MemberSnapshot(second.ref(), 0, 0, 1000, 0, 150, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			PhantomAssertions.assertEquals(2, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.majority(type.actor(), roster, List.of(first, spread)).size(), "A valid 1200/200 local native core did not start its regroup clock.");
			for (var outside : List.of(new MemberSnapshot(second.ref(), 0, 0, 1201, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO), new MemberSnapshot(second.ref(), 0, 0, 1000, 0, 201, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO), new MemberSnapshot(second.ref(), 0, 1, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO)))
			{ PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.majority(type.actor(), roster, List.of(first, outside)).isEmpty(), "Incompatible core bypassed locality/instance guards."); }
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.majority(type.actor(), List.of(first.ref(), second.ref(), type.actor(), MemberRef.phantom(4, 104)), List.of(first, second)).isEmpty(), "A tie moved the meeting point.");
			PhantomAssertions.assertTrue(!org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState.observe(type, type.actor(), ZERO, false, 100000).due(200000), "Observed reunion did not clear the old far interval.");
		});
		registry.add("094-restart-preserves-genuine-shared-hash-and-generation-conflicts", context ->
		{
			for (boolean differentGeneration : List.of(false, true))
			{
				final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
				final var leader = backend.add(1, 101); final var peer = backend.add(2, 102);
				final var draft = rawState(leader, List.of(leader, peer), List.of()); final String manifest = draft.canonicalManifestHash();
				final var first = new PartyState(draft.groupId(), 1, draft.membershipRevision(), StateStatus.LEADER, leader, "", manifest, draft.phantomMembers(), List.of(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, draft.operation(), ZERO, ZERO, "");
				final var second = new PartyState(draft.groupId(), differentGeneration ? 2 : 1, draft.membershipRevision(), StateStatus.MEMBER, leader, "", differentGeneration ? manifest : "B".repeat(64), draft.phantomMembers(), List.of(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, draft.operation(), ZERO, ZERO, "");
				states.seed(1, first); states.seed(2, second);
				final var coordinator = coordinator(context, states, goals, backend, 16);
				PhantomAssertions.assertTrue(coordinator.start(), "Genuine conflict fixture did not start.");
				try
				{
					for (long id : List.of(1L, 2L)) { PhantomAssertions.assertEquals(StateStatus.INCONSISTENT, coordinator.claim(id).orElseThrow().state().status(), "Startup normalized a genuine durable " + (differentGeneration ? "generation" : "hash") + " conflict."); }
					PhantomAssertions.assertEquals(first.leaderManifestHash(), coordinator.claim(1).orElseThrow().state().leaderManifestHash(), "Startup rewrote stored shared authority.");
					PhantomAssertions.assertEquals(second.leaderManifestHash(), coordinator.claim(2).orElseThrow().state().leaderManifestHash(), "Startup erased conflicting evidence.");
				}
				finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Genuine conflict fixture did not drain."); }
			}
		});
		registry.add("01-binary-roundtrip-and-bound", context ->
		{
			final PartyState state = stateFixture();
			final PhantomPartyStateCodec codec = new PhantomPartyStateCodec();
			final byte[] payload = codec.encode(state);
			PhantomAssertions.assertTrue(payload.length <= 4096, "Party state exceeds profile component envelope.");
			PhantomAssertions.assertEquals(state, codec.decode(payload), "Party state binary roundtrip changed the claim.");
			context.record("partyState.payloadBytes", payload.length);
		});
		registry.add("02-every-saga-phase-is-representable", _ ->
		{
			for (OperationPhase phase : OperationPhase.values())
			{
				final PartyOperation operation = operationFixture().withPhase(phase, phase == OperationPhase.CANONICAL_PENDING ? 9 : 0, phase == OperationPhase.ABORTED ? "invite.refused" : "");
				PhantomAssertions.assertEquals(phase, operation.phase(), "Party saga phase was not retained.");
			}
		});
		registry.add("03-roster-is-capped-at-nine", _ ->
		{
			final MemberRef leader = MemberRef.phantom(1, 101);
			final List<MemberRef> ten = java.util.stream.LongStream.rangeClosed(1, 10).mapToObj(value -> MemberRef.phantom(value, 100 + (int) value)).toList();
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> rawState(leader, ten, List.of()), "Ten-member Phantom roster was accepted.");
		});
		registry.add("04-restart-contract-does-not-replay-real-consent", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			PhantomAssertions.assertTrue(source.contains("restart.real_consent_not_restored") && source.contains("List.of(), state.objectiveMode()"), "Restart real-consent stripping is absent.");
		});
		registry.add("05-member-claim-precedes-canonical-invite", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			final int prepared = source.indexOf("preparedMember = save");
			final int canonical = source.indexOf("_backend.invite(current.leader(), target, distribution)", prepared);
			PhantomAssertions.assertTrue((prepared >= 0) && (canonical > prepared), "Canonical invite precedes durable Phantom member claim.");
			PhantomAssertions.assertTrue(source.contains("abortManagedInvitation") && source.contains("moveToSolo"), "Terminal invitation rollback is missing.");
		});
		registry.add("06-stale-leader-goal-revision-cannot-transition", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			PhantomAssertions.assertTrue(source.contains("goal.revision() != operation.leaderGoalRevision()"), "Leader goal transition does not reject a stale revision.");
			PhantomAssertions.assertTrue(source.contains("goalTargets(goal, operation.leader().characterObjectId())"), "Member goal transition does not retain exact inviter consent.");
		});
		registry.add("07-legacy-v1-payload-remains-readable", context ->
		{
			final byte[] legacy = java.util.Base64.getDecoder().decode(Files.readString(context.moduleRoot().resolve("test/resources/phantoms/party-state-v1.base64")).trim());
			PhantomAssertions.assertEquals(stateFixture(), new PhantomPartyStateCodec().decode(legacy), "Captured pre059 durable party payload no longer decodes.");
		});
		registry.add("08-command-payload-preserves-exact-authority", context ->
		{
			final PartyState base = stateFixture();
			for (var mode : org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.values())
			{
				final var command = new org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionCommand(mode, MemberRef.phantom(2, 102), base.leader(), 7, base.groupGeneration(), new PhantomNavigationPoint(10, 20, -30, 0), 128, "A".repeat(64));
				final PartyState held = base.withCommand(command);
				final byte[] encoded = new PhantomPartyStateCodec().encode(held);
				PhantomAssertions.assertTrue(encoded.length <= 4096, "Companion state exceeds durable envelope.");
				PhantomAssertions.assertEquals(held, new PhantomPartyStateCodec().decode(encoded), "Command identity/anchor/mode was lost during durable roundtrip.");
				PhantomAssertions.assertEquals(base.canonicalManifestHash(), held.canonicalManifestHash(), "Per-member command changed the shared roster manifest.");
			}
		});
		registry.add("09-command-rejects-foreign-leader-and-stale-generation", context ->
		{
			final PartyState base = stateFixture();
			final var mode = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD;
			final var point = new PhantomNavigationPoint(10, 20, -30, 0);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> base.withCommand(new org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionCommand(mode, MemberRef.phantom(2, 102), MemberRef.real(999), 7, base.groupGeneration(), point, 128, "A".repeat(64))), "Foreign speaker acquired durable authority.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> base.withCommand(new org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionCommand(mode, MemberRef.phantom(2, 102), base.leader(), 7, base.groupGeneration() + 1, point, 128, "A".repeat(64))), "Stale generation acquired durable authority.");
		});
	}

	private static void roles(PhantomTestRegistry registry)
	{
		registry.add("094-assigned-support-must-serve-another-native-member", context ->
		{
			final var damage = cap("combat.melee_damage", "weapon", 300, 0);
			final var self = new MemberCapability("combat.buff", "self.restore", 900, 121, 1, "SELF", true, true, true, "ready", 9000, "test.native.self");
			final var dd1 = member(1, 101, 100, 100, false, 0, List.of(damage, self)); final var dd2 = member(2, 102, 100, 100, false, 0, List.of(damage));
			final var support = member(3, 103, 100, 100, false, 0, List.of(damage, cap("combat.heal", "learned.heal", 100, 1011)));
			final var result = new PhantomPartyRoleMatcher(currentCatalog(context)).match(ObjectiveMode.GENERAL_PVE, List.of(new RoleRequirement("slot.0", "damage.melee", true, 1), new RoleRequirement("slot.1", "damage.melee", true, 1), new RoleRequirement("slot.2", "support.healer", true, 1)), List.of(dd1, dd2, support));
			PhantomAssertions.assertEquals(support.ref(), result.assignments().stream().filter(assignment -> assignment.roleKey().equals("support.healer")).findFirst().orElseThrow().member(), "SELF-only DD displaced the actual learned group support and permanently closed farm readiness.");
		});
		registry.add("094-farm-alternative-keeps-native-range-and-threat-guards", _ ->
		{
			final var actor = member(1, 101, 100, 100, false, 0, List.of());
			final var target = new PhantomNavigationPoint(1500, 0, 0, 0);
			PhantomAssertions.assertTrue(PhantomAutonomousFarmParty.permitsFarmApproach(actor, target, List.of()), "Safe alternative inside native acquisition was excluded by the old local window.");
			final var blocking = List.of(new PhantomNavigationRequest.PveThreat(new PhantomNavigationPoint(750, 0, 0, 0), 300));
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.permitsFarmApproach(actor, target, blocking), "An intervening rejected champion disappeared from path danger.");
			final var peer = new MemberSnapshot(MemberRef.phantom(2, 102), 0, 0, -600, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.permitsFarmApproach(peer, target, List.of()), "A party peer beyond native2000 was admitted by the leader's range alone.");
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.permitsFarmApproach(actor, new PhantomNavigationPoint(2500, 0, 0, 0), List.of()), "A selected far target bypassed native acquisition.");
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.permitsFarmApproach(actor, new PhantomNavigationPoint(1500, 0, 0, 1), List.of()), "Alternative search crossed an instance.");
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.permitsFarmApproach(actor, target, java.util.Collections.nCopies(65, blocking.getFirst())), "Threat overflow was silently truncated.");
		});
		registry.add("094-local-majority-can-recruit-remote-support", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var leader = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			final var damage = member(2, 102, 100, 100, false, 0, leader.capabilities());
			final var template = member(3, 103, 100, 100, false, 0, List.of(cap("combat.buff", "learned.might", 100, 1068)));
			final var remote = new MemberSnapshot(template.ref(), template.classId(), 0, 25000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO);
			PhantomAssertions.assertEquals(Set.of(leader.ref(), damage.ref(), remote.ref()), Set.copyOf(PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, remote))), "A confirmed local majority could not plan a remote support JOIN.");
			PhantomAssertions.assertTrue(!PhantomAutonomousFarmParty.compatible(leader, remote), "Recruitment loosened native combat/consent range.");
			final var local = member(4, 104, 100, 100, false, 0, template.capabilities());
			PhantomAssertions.assertTrue(PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, remote, local)).contains(local.ref()) && !PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, remote, local)).contains(remote.ref()), "Local support lost to a distant candidate.");
		});
		registry.add("129-remote-consent-selection-preserves-native-locality", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var leader = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			final var template = member(2, 102, 100, 100, false, 0, leader.capabilities());
			final var damage = new MemberSnapshot(template.ref(), template.classId(), 0, 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO);
			final var support = new MemberSnapshot(MemberRef.phantom(3, 103), 16, 0, -15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(cap("combat.heal", "learned.heal", 100, 1011)), ZERO);
			PhantomAssertions.assertEquals(3, PhantomAutonomousFarmParty.selectRendezvousTeam(matcher, leader, List.of(leader, damage, support)).size(), "Distant consenting DD core could not plan a rendezvous.");
			PhantomAssertions.assertEquals(2, PhantomAutonomousFarmParty.selectRendezvousTeam(matcher, leader, List.of(leader, support)).size(), "TASK130: a consenting damage/support pair must be possible.");
			PhantomAssertions.assertEquals(2, PhantomAutonomousFarmParty.selectRendezvousTeam(matcher, leader, List.of(leader, damage)).size(), "TASK130: general PvE must not require a healer.");
			final var nine = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(id -> member(id, 100 + (int) id, 100, 100, false, 0, leader.capabilities())).toList();
			PhantomAssertions.assertEquals(9, PhantomAutonomousFarmParty.selectRendezvousTeam(matcher, leader, nine).size(), "TASK130: the native projection truncated a legal nine-member roster.");
			PhantomAssertions.assertFalse(PhantomAutonomousFarmParty.compatible(leader, damage), "Preparty negotiation loosened native locality.");
			PhantomAssertions.assertTrue(PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, support)).isEmpty(), "Legacy selection silently bypassed rendezvous.");
		});
		registry.add("130-native-two-member-team-keeps-guardian-or-healer-administrative-leader", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var dd = member(2, 102, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			for (var role : List.of(cap("combat.tank", "native.aggro", 800, 28), cap("combat.heal", "native.heal", 450, 1011)))
			{
				final var leader = member(1, 101, 100, 100, false, 0, List.of(role, cap("combat.melee_damage", "weapon", 300, 0)));
				PhantomAssertions.assertEquals(List.of(leader.ref(), dd.ref()), PhantomAutonomousFarmParty.selectRendezvousTeam(matcher, leader, List.of(dd)), "Administrative leader was forced into a DD slot.");
				PhantomAssertions.assertEquals(List.of(leader.ref(), dd.ref()), PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(dd)), "Local two-person Party required support or a third DD.");
			}
		});
		registry.add("130-spoiler-needs-two-learned-skills-and-hybrids-contribute-once", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var weapon = cap("combat.melee_damage", "weapon", 300, 0);
			final var spoiler = member(1, 101, 100, 100, false, 0, List.of(cap("profession.spoil", "spoil", 500, 254), cap("profession.sweep", "sweep", 500, 42), weapon));
			final var requirement = List.of(new RoleRequirement("spoiler", "utility.spoiler", true, 1));
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, matcher.match(ObjectiveMode.GENERAL_PVE, requirement, List.of(spoiler)).vacancies().getFirst().status(), "Learned Spoil/Sweep role is missing.");
			final var incomplete = member(1, 101, 100, 100, false, 0, List.of(cap("profession.spoil", "spoil", 500, 254), weapon));
			PhantomAssertions.assertEquals(VacancyStatus.MISSING, matcher.match(ObjectiveMode.GENERAL_PVE, requirement, List.of(incomplete)).vacancies().getFirst().status(), "Missing Sweep was invented.");
			final var twoSlots = matcher.match(ObjectiveMode.GENERAL_PVE, List.of(requirement.getFirst(), new RoleRequirement("damage", "damage.melee", true, 1)), List.of(spoiler));
			PhantomAssertions.assertEquals(1, twoSlots.assignments().size(), "One hybrid filled two required slots.");
			final var guardian = member(2, 102, 100, 100, false, 0, List.of(cap("combat.tank", "native.aggro", 800, 28), cap("combat.heal", "native.sacrifice", 450, 69), weapon));
			final var healer = member(3, 103, 100, 100, false, 0, List.of(cap("combat.heal", "heal", 450, 1011), weapon));
			final var dd = member(4, 104, 100, 100, false, 0, List.of(weapon));
			PhantomAssertions.assertEquals(3L, List.of(spoiler, guardian, healer, dd).stream().filter(PhantomAutonomousFarmParty::farmContributor).count(), "Guardian/spoiler damage was lost, doubled, or healer lost its separate support owner.");
			PhantomAssertions.assertEquals(guardian.ref(), PhantomAutonomousFarmParty.farmPuller(List.of(healer, dd, guardian, spoiler)).orElseThrow(), "Puller follows administrative order instead of real guardian capability.");
		});
		registry.add("094-natural-team-requires-ready-learned-support", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var leader = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			final var damage = member(2, 102, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			final var support = member(3, 103, 100, 100, false, 0, List.of(cap("combat.buff", "learned.might", 100, 1068)));
			PhantomAssertions.assertEquals(Set.of(leader.ref(), damage.ref(), support.ref()), Set.copyOf(PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, support))), "Ordinary team did not retain two damage dealers and actual support.");
			PhantomAssertions.assertEquals(List.of(leader.ref(), support.ref()), PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, support)), "Approved two-member composition required a third DD.");
			final var empty = member(3, 103, 100, 0, false, 0, support.capabilities());
			PhantomAssertions.assertEquals(List.of(leader.ref(), damage.ref()), PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, empty)), "MP-empty support was counted as prepared.");
			final var missing = member(3, 103, 100, 100, false, 0, List.of(new MemberCapability("combat.buff", "class.description", 100, 1068, 1, "PARTY", true, false, false, "skill.missing", 0, "fixture.class.only")));
			PhantomAssertions.assertEquals(List.of(leader.ref(), damage.ref()), PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, missing)), "Class description substituted for learned support.");
			final var human = new MemberSnapshot(MemberRef.real(201), 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), support.capabilities(), ZERO);
			PhantomAssertions.assertEquals(List.of(leader.ref(), damage.ref()), PhantomAutonomousFarmParty.selectTeam(matcher, leader, List.of(leader, damage, human)), "A real player was selected for automatic consent.");
		});
		registry.add("094-guardian-requires-learned-aggro-not-melee-description", context ->
		{
			final var matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var requirement = List.of(new RoleRequirement("slot.guardian", "frontline.guardian", true, 1));
			final var melee = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			PhantomAssertions.assertEquals(VacancyStatus.MISSING, matcher.match(ObjectiveMode.GENERAL_PVE, requirement, List.of(melee)).vacancies().getFirst().status(), "A melee weapon alone fabricated a guardian.");
			final var descriptive = member(1, 101, 100, 100, false, 0, List.of(cap("combat.tank", "description", 100, 0)));
			PhantomAssertions.assertEquals(VacancyStatus.MISSING, matcher.match(ObjectiveMode.GENERAL_PVE, requirement, List.of(descriptive)).vacancies().getFirst().status(), "A tank label without learned native action fabricated a guardian.");
			final var guardian = member(1, 101, 100, 100, false, 0, List.of(cap("combat.tank", "native.hate", 100, 28)));
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, matcher.match(ObjectiveMode.GENERAL_PVE, requirement, List.of(guardian)).vacancies().getFirst().status(), "A learned guardian was rejected.");
		});
		registry.add("01-current-role-catalog-is-strict-and-hashed", context ->
		{
			final PhantomPartyRoleCatalog catalog = currentCatalog(context);
			PhantomAssertions.assertEquals(64, catalog.hash().length(), "Role catalog is not content addressed.");
			PhantomAssertions.assertTrue(catalog.contains("support.healer") && catalog.contains("frontline.guardian"), "Required generic role mappings are absent.");
		});
		registry.add("02-multi-capability-evidence-is-preserved", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final MemberSnapshot support = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "major", 50, 1001), cap("combat.resurrection", "revive", 45, 1002)));
			final var result = matcher.match(ObjectiveMode.RECOVERY, List.of(new RoleRequirement("slot.heal", "support.healer", true, 1)), List.of(support));
			PhantomAssertions.assertEquals(2, support.capabilities().size(), "Secondary capability fact was lost.");
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, result.vacancies().getFirst().status(), "Contextual healer vacancy was not filled.");
		});
		registry.add("03-missing-optional-unsupported-are-distinct", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var result = matcher.match(ObjectiveMode.GENERAL_PVE, List.of(new RoleRequirement("slot.required", "support.healer", true, 9000), new RoleRequirement("slot.optional", "support.recharge", false, 9000), new RoleRequirement("slot.future", "future.unknown", true, 1)), List.of(member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", 1, 1)))));
			PhantomAssertions.assertEquals(List.of(VacancyStatus.MISSING, VacancyStatus.OPTIONAL, VacancyStatus.UNSUPPORTED), result.vacancies().stream().map(value -> value.status()).sorted().toList(), "Vacancy states collapsed.");
		});
		registry.add("04-objective-context-affects-threshold", _ ->
		{
			final EnumMap<ObjectiveMode, Integer> weights = new EnumMap<>(ObjectiveMode.class);
			weights.put(ObjectiveMode.RECOVERY, 1000);
			final PhantomPartyRoleCatalog catalog = new PhantomPartyRoleCatalog(Map.of("context.role", new RoleDefinition("context.role", Map.of("combat.heal", 1), weights, true)), PhantomPartyModel.sha256("context.catalog"));
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(catalog);
			final RoleRequirement requirement = new RoleRequirement("slot.context", "context.role", true, 900);
			final MemberSnapshot member = member(1, 101, 100, 100, false, 0, List.of(cap("combat.heal", "small", 1, 1)));
			PhantomAssertions.assertEquals(VacancyStatus.MISSING, matcher.match(ObjectiveMode.GENERAL_PVE, List.of(requirement), List.of(member)).vacancies().getFirst().status(), "General context crossed recovery threshold.");
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, matcher.match(ObjectiveMode.RECOVERY, List.of(requirement), List.of(member)).vacancies().getFirst().status(), "Recovery context did not affect role suitability.");
		});
		registry.add("05-required-vacancy-precedes-optional", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final MemberSnapshot member = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", 10, 1)));
			final var result = matcher.match(ObjectiveMode.GENERAL_PVE, List.of(new RoleRequirement("slot.a.optional", "damage.melee", false, 1), new RoleRequirement("slot.z.required", "damage.melee", true, 1)), List.of(member));
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, result.vacancies().stream().filter(value -> value.vacancyKey().equals("slot.z.required")).findFirst().orElseThrow().status(), "Required vacancy did not receive assignment priority.");
			PhantomAssertions.assertEquals(VacancyStatus.OPTIONAL, result.vacancies().stream().filter(value -> value.vacancyKey().equals("slot.a.optional")).findFirst().orElseThrow().status(), "Optional vacancy consumed the only eligible member.");
		});
		registry.add("06-maximum-matching-beats-greedy-counterexample", _ ->
		{
			final PhantomPartyRoleCatalog catalog = new PhantomPartyRoleCatalog(Map.of(
				"support.heal", new RoleDefinition("support.heal", Map.of("combat.heal", 10), Map.of(), true),
				"support.recharge", new RoleDefinition("support.recharge", Map.of("combat.recharge", 10), Map.of(), true)),
				PhantomPartyModel.sha256("maximum.matching.catalog"));
			final MemberSnapshot flexible = member(1, 101, 100, 100, false, 0, List.of(cap("combat.heal", "strong", 100, 1001), cap("combat.recharge", "only", 50, 1002)));
			final MemberSnapshot healerOnly = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "only", 50, 1001)));
			final var result = new PhantomPartyRoleMatcher(catalog).match(ObjectiveMode.RECOVERY, List.of(new RoleRequirement("slot.heal", "support.heal", true, 1), new RoleRequirement("slot.recharge", "support.recharge", true, 1)), List.of(flexible, healerOnly));
			PhantomAssertions.assertEquals(2, result.assignments().size(), "Global role assignment left a required vacancy open.");
			PhantomAssertions.assertEquals(flexible.ref(), result.assignments().stream().filter(value -> value.vacancyKey().equals("slot.recharge")).findFirst().orElseThrow().member(), "Flexible member was greedily consumed by HEAL.");
		});
	}

	private static void semantics(PhantomTestRegistry registry)
	{
		registry.add("01-all-acts-are-string-keyed-and-stable", _ ->
		{
			final PhantomSemanticAct act = semantic(1);
			PhantomAssertions.assertEquals(act.canonicalHash(), semantic(1).canonicalHash(), "Semantic act hash is not deterministic.");
			PhantomAssertions.assertTrue(PhantomPartySemanticActs.KEYS.size() >= 12, "Party semantic act vocabulary is incomplete.");
		});
		registry.add("02-stale-generation-cannot-dispatch", _ ->
		{
			final AtomicInteger mutations = new AtomicInteger();
			PhantomAssertions.assertFalse(PhantomPartySemanticActs.dispatchIfCurrent(semantic(1), generation -> generation == 2 ? semantic(1).groupId() : ZERO, ignored -> mutations.incrementAndGet()), "Stale semantic act dispatched.");
			PhantomAssertions.assertEquals(0, mutations.get(), "A semantic act mutated state by itself.");
		});
		registry.add("03-current-generation-dispatches-once", _ ->
		{
			final PhantomSemanticAct act = semantic(1);
			final AtomicInteger accepted = new AtomicInteger();
			PhantomAssertions.assertTrue(PhantomPartySemanticActs.dispatchIfCurrent(act, generation -> act.groupId(), ignored -> accepted.incrementAndGet()), "Current semantic act was rejected.");
			PhantomAssertions.assertEquals(1, accepted.get(), "Current semantic act did not dispatch exactly once.");
		});
	}

	private static void route(PhantomTestRegistry registry)
	{
		registry.add("129-phantom-led-native-group-dispatches-human-follow", context ->
		{
			final var clock = new AtomicLong(1);
			final var navigationBackend = new PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination) { return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1); }
				@Override public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination) { return true; }
				@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token) { throw new AssertionError("Direct follow invoked pathfinding."); }
			};
			final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), navigationBackend, worker -> { worker.run(); return true; }, clock::get, new PhantomMetrics());
			navigation.start(); final var lease = new PartyActionLease(); final var combat = cooperativeCombat(lease, clock);
			final var routes = new PhantomPartyRouteCoordinator(navigation, combat);
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			final var leader = backend.add(1, 101); final var human = MemberRef.real(201);
			backend.snapshot(leader, member(1, 101, 100, 100, false, 0, List.of()));
			backend.snapshot(human, new MemberSnapshot(human, 0, 0, 1000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
			goals.put(1, goal(129100, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, human), PartyDistributionType.FINDERS_KEEPERS));
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), routes, new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			backend.connect(coordinator); coordinator.start();
			try
			{
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(1, 129100, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "mixed.fixture"), List.of()), "Mixed fixture could not bind native group.");
				for (int pulse = 0; pulse < 8; pulse++) { clock.incrementAndGet(); coordinator.onPulse(); }
				PhantomAssertions.assertTrue(lease.moves > 0, "Phantom-led reconcile never dispatched follow toward its single human member.");
				PhantomAssertions.assertEquals(leader, backend.observe(leader).orElseThrow().leader(), "Following transferred official leadership.");
				backend._parties.clear(); for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertFalse(combat.hasClaim(1), "Departed human retained a follow movement owner.");
			}
			finally
			{
				coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Mixed follow coordinator did not drain.");
				navigation.beginStop(); PhantomAssertions.assertTrue(navigation.finishStop(), "Mixed follow navigation did not drain.");
				combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Mixed follow combat did not drain.");
			}
		});
		registry.add("129-human-anchor-keeps-native-leader-authority", _ ->
		{
			final var leader = MemberRef.phantom(1, 101); final var peer = MemberRef.phantom(2, 102);
			final var human = MemberRef.real(201); final var otherHuman = MemberRef.real(202);
			final var mixed = new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, peer, human), PartyDistributionType.FINDERS_KEEPERS);
			PhantomAssertions.assertEquals(human, PhantomPartyCoordinator.navigationAnchor(mixed, true), "A single human member was ignored behind a Phantom leader.");
			PhantomAssertions.assertEquals(leader, mixed.leader(), "Selecting a navigation anchor transferred native authority.");
			PhantomAssertions.assertEquals(leader, PhantomPartyCoordinator.navigationAnchor(mixed, false), "Disabled preference replaced native leader.");
			PhantomAssertions.assertEquals(leader, PhantomPartyCoordinator.navigationAnchor(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, human, otherHuman), PartyDistributionType.FINDERS_KEEPERS), true), "Ambiguous owner identity guessed a human.");
			PhantomAssertions.assertEquals(leader, PhantomPartyCoordinator.navigationAnchor(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, peer), PartyDistributionType.FINDERS_KEEPERS), true), "Phantom-only follow lost the native leader.");
		});
		registry.add("076-transient-follow-deadline-renews-but-geometric-reject-stays-blocked", _ -> cooperativeFollow());
		registry.add("094-phantom-regroup-reuses-bounded-follow-and-owned-cancellation", _ -> cooperativeFollow(true));
		registry.add("01-one-manifest-shares-waypoints", _ ->
		{
			final RouteManifest route = routeFixture();
			final PartyState state = stateFixture();
			final PartyState withRoute = new PartyState(state.groupId(), state.groupGeneration(), state.membershipRevision(), state.status(), state.leader(), state.ownRoleKey(), state.leaderManifestHash(), state.phantomMembers(), state.realMembers(), ObjectiveMode.TRAVEL, route.destination(), state.requirements(), state.assignments(), route, state.operation(), state.progressionHash(), state.topologyHash(), "");
			PhantomAssertions.assertTrue(withRoute.route().waypoints() == route.waypoints(), "Route manifest was expanded per member.");
			PhantomAssertions.assertEquals(2, route.waypoints().size(), "Shared route fixture changed.");
		});
		registry.add("02-regroup-status-is-durable", _ -> PhantomAssertions.assertEquals(RouteStatus.REGROUPING, routeFixture().withProgress(0, RouteStatus.REGROUPING).status(), "Regroup state was not retained."));
		registry.add("03-route-owner-has-no-snap-or-background-travel", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertFalse(source.contains("teleToLocation") || source.contains("setXYZ") || source.contains("onBypassFeedback"), "Party route issues direct relocation outside the native travel owner.");
			PhantomAssertions.assertEquals(1L, source.lines().filter(line -> line.contains("_navigation.submit")).count(), "Route coordinator has more than one navigation submission site.");
		});
		registry.add("04-route-cancel-is-group-scoped-and-lock-free-at-boundaries", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertFalse(source.contains("public synchronized") || source.contains("private synchronized"), "Route coordinator holds its monitor across navigation/combat boundaries.");
			PhantomAssertions.assertTrue(source.contains("_routeByGroup.remove(groupId)") && source.contains("routeId.equals(entry.getValue()._routeId)"), "Route cancellation is not scoped to the selected group route.");
			PhantomAssertions.assertFalse(source.contains("new ArrayList<>(_movement.values())"), "Route cancellation still closes every group's movement lease.");
		});
		registry.add("05-missing-member-cannot-advance-or-arrive", _ ->
		{
			final PhantomNavigationBackend backend = new PhantomNavigationBackend()
			{
				@Override
				public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
				{
					return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1);
				}

				@Override
				public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
				{
					return true;
				}

				@Override
				public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
				{
					throw new AssertionError("Direct route fixture invoked pathfinding.");
				}
			};
			final PhantomNavigationService navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, worker ->
			{
				worker.run();
				return true;
			}, System::nanoTime, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "Route navigation fixture did not start.");
			final PhantomPartyRouteCoordinator routes = new PhantomPartyRouteCoordinator(navigation, null);
			final MemberRef leader = MemberRef.phantom(1, 101);
			final MemberRef missing = MemberRef.phantom(2, 102);
			final MemberSnapshot leaderSnapshot = new MemberSnapshot(leader, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			final String groupId = PhantomPartyModel.sha256("route.missing.member");
			final long now = Math.max(1, System.nanoTime());
			final PhantomPartyRouteCoordinator.RouteAttempt routeAttempt = routes.request(groupId, 1, leaderSnapshot, new PhantomDomainRef("location", "missing"), new PhantomNavigationPoint(100, 100, 0, 0), ZERO, now, now + 1_000_000_000L);
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.AttemptStatus.READY, routeAttempt.status(), "Immediate route fixture did not produce READY.");
			final RouteManifest route = routeAttempt.route();
			final var result = routes.advance(groupId, route, leader, List.of(leader, missing), Map.of(leader, leaderSnapshot), 10, now + 1, ZERO, () -> false);
			PhantomAssertions.assertEquals(RouteStatus.REGROUPING, result.route().status(), "Missing canonical member did not force REGROUPING.");
			PhantomAssertions.assertEquals(route.currentWaypoint(), result.route().currentWaypoint(), "Missing canonical member advanced a waypoint.");
			PhantomAssertions.assertFalse(result.route().status() == RouteStatus.ARRIVED, "Missing canonical member allowed ARRIVED.");
			routes.cancel(groupId, 2);
			PhantomAssertions.assertTrue(routes.observe(groupId, route, List.of(leader)).routeOwned(), "A foreign generation cancelled the current route.");
			routes.cancel(groupId, 1);
			final var replacement = routes.request(groupId, 2, leaderSnapshot, new PhantomDomainRef("location", "new-owner"), new PhantomNavigationPoint(200, 100, 0, 0), ZERO, now, now + 1_000_000_000L);
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.AttemptStatus.READY, replacement.status(), "Replacement generation did not acquire a route.");
			routes.cancel(groupId, 1);
			PhantomAssertions.assertTrue(routes.observe(groupId, replacement.route(), List.of(leader)).routeOwned(), "Stale cleanup cancelled the replacement generation.");
			routes.beginStop();
			navigation.beginStop();
			PhantomAssertions.assertTrue(navigation.finishStop(), "Route navigation fixture did not drain.");
		});
	}

	private static void routeClosure(PhantomTestRegistry registry)
	{
		registry.add("06-held-member-remains-in-shared-route-arrival-denominator", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, true))
			{
				final MemberRef member = fixture._backend.add(2, 102);
				final List<MemberRef> roster = List.of(fixture.leader(), member);
				fixture._backend.party(new PhantomPartyBackend.PartySnapshot(fixture.leader(), roster, PartyDistributionType.FINDERS_KEEPERS));
				fixture._backend.snapshot(member, new MemberSnapshot(member, 0, 0, 2000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
				final var bound = fixture.coordinator().bindContentGoal(new ContentBindingRequest(fixture.leader().profileId(), RouteClosureFixture.GOAL_ID, 0, PhantomRiftService.GOAL_TYPE, ObjectiveMode.AREA_PVE, fixture.objective(), List.of(), fixture.leader(), roster, PartyDistributionType.FINDERS_KEEPERS, PhantomPartyModel.sha256("route.held.roster")));
				PhantomAssertions.assertTrue(bound.bound(), "Held-member shared route binding failed.");
				final long generation = fixture.coordinator().claim(member.profileId()).orElseThrow().state().groupGeneration();
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CompanionCommandOutcome.HOLDING, fixture.coordinator().commandCompanion(member.profileId(), fixture.leader(), generation, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, PhantomPartyModel.sha256("route.held.request")), "Exact leader HOLD was not admitted.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.READY, fixture.request(fixture.routeDestination()).outcome(), "Held-member route was not ready.");
				fixture.snapshot(100, 0, 0, false);
				fixture.pulse(2);
				PhantomAssertions.assertTrue(fixture.state().route() != null && fixture.state().route().status() != RouteStatus.ARRIVED, "A held companion was omitted from shared route arrival.");
				PhantomAssertions.assertEquals(0, fixture.routeSnapshot().movementClaims(), "Paused shared route acquired new movement.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CompanionCommandOutcome.FOLLOWING, fixture.coordinator().commandCompanion(member.profileId(), fixture.leader(), generation, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.FOLLOW, PhantomPartyModel.sha256("route.resume.request")), "Shared route RESUME was not admitted.");
				fixture._backend.snapshot(member, new MemberSnapshot(member, 0, 0, 100, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
				fixture.pulse(2);
				PhantomAssertions.assertEquals(RouteStatus.ARRIVED, fixture.state().route().status(), "Full roster arrival failed after RESUME.");
			}
		});
		registry.add("01-planner-pending-binding-preserves-ownership-and-submits-once", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, false))
			{
				PhantomAssertions.assertTrue(fixture.bind().bound(), "Initial Rift content binding was not stable.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.PENDING, fixture.request(new PhantomDomainRef("location", "unrelated")).outcome(), "Planner-pending route did not expose PENDING.");
				final var activity = fixture.coordinator().observeRouteActivity(fixture.leader().profileId());
				PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.PLANNING, activity.status(), "Planner ownership was not visible before manifest persistence.");
				PhantomAssertions.assertTrue(activity.plannerOwned() && !ZERO.equals(activity.routeId()), "Planner-pending activity lost exact route identity.");
				PhantomAssertions.assertEquals(BindingStatus.PENDING, fixture.portBinding().status(), "Content binding ignored planner-pending route ownership.");
				PhantomAssertions.assertEquals(null, fixture.state().route(), "Planner-pending binding fabricated or cleared a route manifest.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.PENDING, fixture.request(new PhantomDomainRef("location", "second")).outcome(), "Second request over planner ownership did not stay PENDING.");
				PhantomAssertions.assertEquals(1, fixture.submissions(), "Planner-pending retry submitted a second shared route.");
			}
		});
		registry.add("02-moving-and-regrouping-binding-preserves-live-route", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, true))
			{
				PhantomAssertions.assertTrue(fixture.bind().bound(), "Initial moving-route binding was not stable.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.READY, fixture.request(new PhantomDomainRef("location", "unrelated")).outcome(), "Immediate shared route was not ready.");
				final RouteManifest moving = fixture.state().route();
				final var movingActivity = fixture.coordinator().observeRouteActivity(fixture.leader().profileId());
				PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.MOVING, movingActivity.status(), "Persisted MOVING route was not visible.");
				PhantomAssertions.assertTrue(movingActivity.routeOwned(), "MOVING route lost Goal017 ownership.");
				PhantomAssertions.assertEquals(BindingStatus.PENDING, fixture.portBinding().status(), "Content binding treated MOVING route as stable.");
				PhantomAssertions.assertEquals(moving, fixture.state().route(), "Content binding overwrote the live MOVING manifest.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.PENDING, fixture.request(new PhantomDomainRef("location", "duplicate")).outcome(), "MOVING route allowed a second request.");
				PhantomAssertions.assertEquals(1, fixture.submissions(), "MOVING retry submitted a second shared route.");
				fixture.snapshot(0, 0, 0, true);
				fixture.pulse(2);
				final RouteManifest regrouping = fixture.state().route();
				PhantomAssertions.assertEquals(RouteStatus.REGROUPING, regrouping.status(), "Goal017 did not persist REGROUPING.");
				PhantomAssertions.assertEquals(BindingStatus.PENDING, fixture.portBinding().status(), "Content binding treated REGROUPING as stable.");
				PhantomAssertions.assertEquals(regrouping, fixture.state().route(), "Content binding overwrote the live REGROUPING manifest.");
			}
		});
		registry.add("03-arrived-terminal-cleanup-precedes-stable-binding", context -> terminalRoute(context, RouteStatus.ARRIVED));
		registry.add("04-failed-terminal-cleanup-precedes-stable-binding", context -> terminalRoute(context, RouteStatus.FAILED));
		registry.add("05-rift-route-observed-once-and-ready-only-after-clean-binding", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, true))
			{
				final var initial = fixture.port().bind(fixture.leader().profileId(), RouteClosureFixture.GOAL_ID, 0, fixture.objective(), List.of(), fixture.roster());
				PhantomAssertions.assertTrue(initial.stable(), "Rift production port did not create the initial exact binding.");
				final RouteObservation requested = fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination());
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteStatus.PENDING, requested.status(), "Rift route was not pending after one request.");
				final RouteObservation retry = fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination());
				PhantomAssertions.assertEquals(requested.routeHash(), retry.routeHash(), "Rift retry changed exact route identity.");
				PhantomAssertions.assertEquals(1, fixture.submissions(), "Rift retry submitted a second shared route.");
				PhantomAssertions.assertEquals(BindingStatus.PENDING, fixture.portBinding().status(), "READY binding was stable while Rift route was MOVING.");
				fixture.snapshot(100, 0, 0, false);
				fixture.pulse(2);
				final RouteObservation arrived = fixture.port().observeRoute(fixture.leader().profileId(), requested.routeHash());
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteStatus.ARRIVED, arrived.status(), "Rift route was not observed to ARRIVED.");
				PhantomAssertions.assertEquals(BindingStatus.PENDING, fixture.port().observeBinding(fixture.leader().profileId(), RouteClosureFixture.GOAL_ID, 0, fixture.objective(), List.of(), fixture.roster()).status(), "Terminal manifest became READY without binding refresh.");
				final var refreshed = fixture.port().bind(fixture.leader().profileId(), RouteClosureFixture.GOAL_ID, 0, fixture.objective(), List.of(), fixture.roster());
				PhantomAssertions.assertTrue(refreshed.stable(), "Stable Rift binding did not recover after Goal017 terminal cleanup.");
				PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.NONE, fixture.coordinator().observeRouteActivity(fixture.leader().profileId()).status(), "READY binding retained route ownership.");
			}
		});
	}

	private static void routeFailureClosure(PhantomTestRegistry registry)
	{
		registry.add("00-required-seed", context -> PhantomAssertions.assertEquals(23002313L, context.seed(), "Goal 023C must use seed 23002313."));
		registry.add("01-none-is-real-absence-not-pending", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, RouteNavigationCase.IMMEDIATE_SUCCESS))
			{
				PhantomAssertions.assertTrue(fixture.bind().bound(), "NONE fixture binding was not stable.");
				final RouteObservation observation = fixture.port().observeRoute(fixture.leader().profileId(), ZERO);
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.NONE, observation.status(), "RouteActivity.NONE was collapsed into PENDING.");
				assertNoRouteOwnership(fixture, "NONE observation");
			}
		});
		registry.add("02-sync-rejected-is-terminal-and-replannable", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, RouteNavigationCase.SYNC_REJECTED))
			{
				PhantomAssertions.assertTrue(fixture.bind().bound(), "Rejected fixture binding was not stable.");
				final RouteObservation rejected = fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination());
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.REJECTED, rejected.status(), "Navigation REJECTED was collapsed into PENDING.");
				PhantomAssertions.assertEquals("rift.route.service_not_running", rejected.reasonKey(), "Rejected Navigation status was not preserved upward.");
				assertNoRouteOwnership(fixture, "sync REJECTED");
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.REJECTED, fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination()).status(), "Later ordinary replan could not resubmit after REJECTED.");
			}
		});
		registry.add("03-sync-completed-no-route-is-failed", context ->
		{
			try (RouteClosureFixture fixture = new RouteClosureFixture(context, RouteNavigationCase.SYNC_NO_ROUTE))
			{
				PhantomAssertions.assertTrue(fixture.bind().bound(), "No-route fixture binding was not stable.");
				final RouteObservation failed = fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination());
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.FAILED, failed.status(), "COMPLETED no-route was collapsed into PENDING.");
				PhantomAssertions.assertEquals("rift.route.no_geodata", failed.reasonKey(), "Completed no-route status was not preserved upward.");
				assertNoRouteOwnership(fixture, "sync completed no-route");
			}
		});
		registry.add("04-async-accepted-no-path-closes-ownership", context -> asyncRouteFailure(context, RouteNavigationCase.ASYNC_NO_PATH, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.NO_PATH, "rift.route.no_path"));
		registry.add("05-async-accepted-backend-failure-closes-ownership", context -> asyncRouteFailure(context, RouteNavigationCase.ASYNC_BACKEND_FAILURE, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.BACKEND_FAILURE, "rift.route.backend_failure"));
		registry.add("06-immediate-and-async-success-remain-ready", context ->
		{
			try (RouteClosureFixture immediate = new RouteClosureFixture(context, RouteNavigationCase.IMMEDIATE_SUCCESS))
			{
				PhantomAssertions.assertTrue(immediate.bind().bound(), "Immediate success binding was not stable.");
				final RouteObservation requested = immediate.port().requestRoute(immediate.leader().profileId(), immediate.routeDestination(), immediate.destination());
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.PENDING, requested.status(), "Immediate usable route did not enter MOVING/PENDING.");
				PhantomAssertions.assertEquals(RouteStatus.MOVING, immediate.state().route().status(), "Immediate usable route did not persist MOVING.");
				PhantomAssertions.assertEquals(1, immediate.routeSnapshot().routeClaims(), "Immediate usable route lost route ownership.");
				PhantomAssertions.assertEquals(1, immediate.routeSnapshot().deadlineClaims(), "Immediate usable route lost its deadline.");
			}
			try (RouteClosureFixture async = new RouteClosureFixture(context, RouteNavigationCase.ASYNC_SUCCESS))
			{
				PhantomAssertions.assertTrue(async.bind().bound(), "Async success binding was not stable.");
				final RouteObservation pending = async.port().requestRoute(async.leader().profileId(), async.routeDestination(), async.destination());
				PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.PENDING, pending.status(), "Accepted async route did not expose PENDING.");
				async.snapshot(0, 0, 0, true);
				async.completeAsync();
				PhantomAssertions.assertTrue(Set.of(RouteStatus.MOVING, RouteStatus.REGROUPING).contains(async.state().route().status()), "Async PATH_FOUND did not persist a usable live route.");
				PhantomAssertions.assertEquals(pending.routeHash(), async.coordinator().observeRouteActivity(async.leader().profileId()).routeId(), "Async success changed exact route identity.");
				PhantomAssertions.assertEquals(1, async.routeSnapshot().routeClaims(), "Async usable route lost route ownership.");
				PhantomAssertions.assertEquals(0, async.routeSnapshot().terminalReceipts(), "Async usable route fabricated terminal failure evidence.");
			}
		});
	}

	private static void asyncRouteFailure(PhantomTestContext context, RouteNavigationCase navigationCase, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status expectedStatus, String expectedReason) throws Exception
	{
		try (RouteClosureFixture fixture = new RouteClosureFixture(context, navigationCase))
		{
			PhantomAssertions.assertTrue(fixture.bind().bound(), "Async failure binding was not stable.");
			final RouteObservation pending = fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination());
			PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.PENDING, pending.status(), "Accepted async route was not PENDING before completion.");
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.PLANNING, fixture.coordinator().observeRouteActivity(fixture.leader().profileId()).status(), "Accepted async route lost planner ownership.");
			fixture.completeAsync();
			final var terminal = fixture.coordinator().observeRouteActivity(fixture.leader().profileId());
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.FAILED, terminal.status(), "Async terminal no-route was not observable as FAILED.");
			PhantomAssertions.assertEquals(expectedStatus, terminal.navigationStatus(), "Async terminal Navigation status changed across Goal017.");
			PhantomAssertions.assertEquals(null, fixture.state().route(), "Async terminal no-route fabricated a RouteManifest.");
			PhantomAssertions.assertEquals(0, fixture.routeSnapshot().routeClaims(), "Async terminal no-route polluted route ownership.");
			PhantomAssertions.assertEquals(0, fixture.routeSnapshot().deadlineClaims(), "Async terminal no-route polluted deadline ownership.");
			PhantomAssertions.assertEquals(1, fixture.routeSnapshot().terminalReceipts(), "Async terminal evidence was not bounded to one receipt.");
			final RouteObservation failed = fixture.port().observeRoute(fixture.leader().profileId(), pending.routeHash());
			PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.FAILED, failed.status(), "Async terminal failure did not reach Rift.");
			PhantomAssertions.assertEquals(expectedReason, failed.reasonKey(), "Async terminal failure reason changed across Rift port.");
			assertNoRouteOwnership(fixture, "async " + expectedStatus);
			fixture.advanceClock(60_000_000_000L);
			PhantomAssertions.assertEquals(PhantomRiftService.RouteStatus.PENDING, fixture.port().requestRoute(fixture.leader().profileId(), fixture.routeDestination(), fixture.destination()).status(), "Later ordinary replan did not resubmit after async failure.");
			PhantomAssertions.assertEquals(2, fixture.submissions(), "Async failure replan did not perform exactly one later submission.");
		}
	}

	private static void assertNoRouteOwnership(RouteClosureFixture fixture, String label)
	{
		final PhantomPartyRouteCoordinator.Snapshot route = fixture.routeSnapshot();
		PhantomAssertions.assertEquals(0, route.navigationClaims(), label + " retained navigation ownership.");
		PhantomAssertions.assertEquals(0, route.routeClaims(), label + " retained route ownership.");
		PhantomAssertions.assertEquals(0, route.deadlineClaims(), label + " retained deadline ownership.");
		PhantomAssertions.assertEquals(0, route.movementClaims(), label + " retained movement ownership.");
		PhantomAssertions.assertEquals(0, route.terminalReceipts(), label + " retained terminal evidence after reconciliation.");
		PhantomAssertions.assertEquals(0, fixture.navigationSnapshot().activeRequests(), label + " retained Navigation active request.");
		PhantomAssertions.assertEquals(0, fixture.navigationSnapshot().completedResults(), label + " retained Navigation completed result.");
		PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.NONE, fixture.coordinator().observeRouteActivity(fixture.leader().profileId()).status(), label + " did not reconcile to NONE.");
	}

	private static void terminalRoute(PhantomTestContext context, RouteStatus terminal) throws Exception
	{
		try (RouteClosureFixture fixture = new RouteClosureFixture(context, true))
		{
			final ContentBindingResult initial = fixture.bind();
			PhantomAssertions.assertTrue(initial.bound(), "Initial terminal-route binding was not stable.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.RouteOutcome.READY, fixture.request(fixture.routeDestination()).outcome(), "Terminal-route setup did not persist MOVING.");
			final PartyState before = fixture.state();
			if (terminal == RouteStatus.ARRIVED)
			{
				fixture.snapshot(100, 0, 0, false);
			}
			else
			{
				fixture.snapshot(0, 0, 1, false);
			}
			fixture.pulse(2);
			PhantomAssertions.assertEquals(terminal, fixture.state().route().status(), "Goal017 did not persist the expected terminal route status.");
			final var terminalActivity = fixture.coordinator().observeRouteActivity(fixture.leader().profileId());
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.valueOf(terminal.name()), terminalActivity.status(), "Terminal route activity was not typed.");
			final ContentBindingResult reconciled = fixture.bind();
			PhantomAssertions.assertTrue(reconciled.bound(), "Content binding did not recover after terminal route cleanup.");
			final PartyState after = fixture.state();
			PhantomAssertions.assertEquals(null, after.route(), "Terminal route manifest was not cleared by Goal017 reconciliation.");
			PhantomAssertions.assertEquals(before.groupId(), after.groupId(), "Terminal cleanup changed group identity.");
			PhantomAssertions.assertEquals(before.groupGeneration(), after.groupGeneration(), "Terminal cleanup changed group generation.");
			PhantomAssertions.assertEquals(before.membershipRevision(), after.membershipRevision(), "Terminal cleanup changed membership revision.");
			PhantomAssertions.assertEquals(before.phantomMembers(), after.phantomMembers(), "Terminal cleanup changed canonical membership.");
			PhantomAssertions.assertEquals(PhantomPartyRouteCoordinator.ActivityStatus.NONE, fixture.coordinator().observeRouteActivity(fixture.leader().profileId()).status(), "Terminal cleanup orphaned route ownership.");
		}
	}

	private static void tactics(PhantomTestRegistry registry)	{
		registry.add("126-farm-healer-keeps-support-owner-while-damage-members-fight", context ->
		{
			for (long administrator : List.of(1L, 3L))
			{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			final var clock = new AtomicLong(1_000_000_000L); final Map<Long, PartyActionLease> leases = new LinkedHashMap<>();
			final var combat = new PhantomCombatService(leases::get, new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(4), new PhantomCombatMetrics(), clock::get, (_, _) -> PhantomCombatService.DispatchResult.accepted(new PhantomCombatService.DispatchHandle()
			{
				private boolean cancelled;
				@Override public boolean cancelIfNotStarted() { if (cancelled) { return false; } cancelled = true; return true; }
				@Override public PhantomCombatService.DispatchState state() { return cancelled ? PhantomCombatService.DispatchState.CANCELLED : PhantomCombatService.DispatchState.SCHEDULED; }
			}));
			combat.start();
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id); goals.put(id, goal(126000 + id, "farm.background", null, 0));
				final var caps = id == 3 ? List.of(cap("combat.heal", "heal", 450, 1001), cap("combat.melee_damage", "native.weapon", 300, 0)) : List.of(cap("combat.melee_damage", "native.weapon", 300, 0));
				backend.snapshot(ref, member(id, ref.characterObjectId(), 100, 100, false, 0, caps)); backend._capabilities.put(ref, caps);
				final var lease = new PartyActionLease(); lease.objectId = ref.characterObjectId(); lease.target = new TargetSnapshot(900, 20001, 0, 1000, 1000, false, false, true, true, false, true, true, 50, false, true); leases.put(id, lease);
			}
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				coordinator.formForGoal(administrator, 126000 + administrator, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", administrator == 1 ? "1,3,2" : "3,1,2"), List.of(new RoleRequirement("dd.1", "damage.melee", true, 1), new RoleRequirement("dd.2", "damage.melee", true, 1), new RoleRequirement("healer", "support.healer", true, 1)));
				for (long id : List.of(1L, 2L, 3L)) { if (id == administrator) { continue; } coordinator.invite(administrator, backend.currentMember(id).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS); for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); } }
				final var leader = backend.currentMember(1).orElseThrow();
				final var support = backend.currentMember(3).orElseThrow();
				final var supportCapabilities = backend._capabilities.get(support);
				backend.snapshot(support, member(3, 103, 100, 100, false, 0, List.of(cap("combat.melee_damage", "native.weapon", 300, 0))));
				backend._farmTarget = java.util.OptionalInt.of(900);
				PhantomAssertions.assertFalse(coordinator.requestAutonomousFarm(administrator), "Required healer capability disappeared, but DD-only backend readiness still permitted a pull.");
				backend.snapshot(support, member(3, 103, 100, 100, false, 0, supportCapabilities));
				PhantomAssertions.assertTrue(coordinator.requestAutonomousFarm(administrator), "Healer administrative leader could not start the independent DD puller.");
				backend._farmTarget = java.util.OptionalInt.empty();
				backend.snapshot(leader, member(1, 101, 100, 100, false, 900, backend._capabilities.get(leader)));
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(combat.hasClaim(1) && combat.hasClaim(2), "Damage members did not retain cooperative combat.");
				PhantomAssertions.assertFalse(combat.hasClaim(3), "Assigned healer acquired continuous melee assist instead of remaining available for support.");
				final long generation = combat.find(1).orElseThrow().generation();
				backend.snapshot(leader, member(1, 101, 40, 100, false, 900, backend._capabilities.get(leader)));
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, leases.get(3L).casts, "Wounded Party member did not receive one owned heal cast.");
				PhantomAssertions.assertEquals(101, leases.get(3L).support.targetObjectId(), "Heal selected a different Party member.");
				PhantomAssertions.assertTrue(combat.hasClaim(3) && leases.get(3L).casting, "Pending support cast lost its exact action owner.");
				PhantomAssertions.assertEquals(generation, combat.find(1).orElseThrow().generation(), "Heal cancelled the damage member's combat generation.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Party support fixture did not drain."); combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Combat fixture did not drain."); }
			}
		});
		registry.add("094-low-mp-castable-support-retains-role-without-maintenance", context ->
		{
			final var backend = new MemoryPartyBackend(); final var snapshots = new LinkedHashMap<MemberRef, MemberSnapshot>();
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id);
				final var capabilities = List.of(id == 3 ? cap("combat.buff", "native.learned", 450, 1040) : cap("combat.melee_damage", "native.weapon", 300, 0));
				snapshots.put(ref, new MemberSnapshot(ref, 0, 0, 0, 0, 0, 100, id == 3 ? 20 : 100, 100, false, false, false, false, 0, List.of(), capabilities, ZERO));
				backend._capabilities.put(ref, capabilities);
			}
			final var leader = backend.currentMember(1).orElseThrow();
			PhantomAssertions.assertEquals(3, PhantomAutonomousFarmParty.selectTeam(new PhantomPartyRoleMatcher(currentCatalog(context)), snapshots.get(leader), List.copyOf(snapshots.values())).size(), "Maintenance policy erased a physically castable learned support role.");
			final var plan = new PhantomPartyTactics(null, backend).plan(leader, List.copyOf(snapshots.keySet()), snapshots);
			PhantomAssertions.assertTrue(plan.stream().noneMatch(directive -> directive.kind() == DirectiveKind.PARTY_SUPPORT), "Low MP admitted nonurgent maintenance before recovery.");
		});
		registry.add("094-empty-next-pull-keeps-current-combat-owner", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			final var clock = new AtomicLong(1_000_000_000L); final var lease = new PartyActionLease();
			lease.target = new TargetSnapshot(900, 20001, 0, 1000, 1000, false, false, true, true, false, true, true, 50, false, true);
			final var combat = cooperativeCombat(lease, clock, (_, _) -> PhantomCombatService.DispatchResult.accepted(new PhantomCombatService.DispatchHandle()
			{
				private boolean cancelled;
				@Override public boolean cancelIfNotStarted() { if (cancelled) { return false; } cancelled = true; return true; }
				@Override public PhantomCombatService.DispatchState state() { return cancelled ? PhantomCombatService.DispatchState.CANCELLED : PhantomCombatService.DispatchState.SCHEDULED; }
			}));
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id); goals.put(id, goal(94000 + id, "farm.background", null, 0));
				backend.snapshot(ref, member(id, 100 + (int) id, 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "heal", 100, 1001) : cap("combat.melee_damage", "weapon", 100, 0))));
			}
			backend._capabilities.put(backend.currentMember(1).orElseThrow(), List.of(cap("combat.melee_damage", "weapon", 100, 0)));
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				coordinator.formForGoal(1, 94001, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,3,2"), List.of(new RoleRequirement("dd.1", "damage.melee", true, 1), new RoleRequirement("dd.2", "damage.melee", true, 1), new RoleRequirement("healer", "support.healer", true, 1))); coordinator.invite(1, backend.currentMember(2).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); }
				coordinator.invite(1, backend.currentMember(3).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); }
				backend._farmTarget = java.util.OptionalInt.of(900);
				PhantomAssertions.assertTrue(coordinator.requestAutonomousFarm(1), "The public farm coordinator did not own a combat session.");
				PhantomAssertions.assertTrue(coordinator.hasOwnedFarmEncounter(1), "The logical retention check lost an accepted exact Party encounter.");
				PhantomAssertions.assertFalse(coordinator.hasOwnedFarmEncounter(2), "A peer without a native encounter falsely retained the group.");
				final long generation = combat.find(1).orElseThrow().generation();
				backend._farmTarget = java.util.OptionalInt.empty();
				PhantomAssertions.assertTrue(coordinator.requestAutonomousFarm(1), "Absence of a next pull cancelled the current native episode.");
				PhantomAssertions.assertTrue(combat.hasClaim(1) && combat.find(1).orElseThrow().generation() == generation, "An empty next target replaced or cancelled existing combat ownership.");
				backend._capabilities.put(backend.currentMember(1).orElseThrow(), List.of(cap("combat.melee_damage", "weapon", 100, 0), cap("combat.buff", "maintenance", 450, 1040)));
				coordinator.onPulse();
				PhantomAssertions.assertTrue(combat.hasClaim(1) && combat.find(1).orElseThrow().generation() == generation && lease.casts == 0, "Maintenance interrupted an existing native combat episode.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Owned combat fixture did not drain."); combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Owned combat worker did not drain."); }
		});
		registry.add("094-idle-maintenance-serves-peers-before-self", _ ->
		{
			final var backend = new MemoryPartyBackend(); final var snapshots = new LinkedHashMap<MemberRef, MemberSnapshot>();
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id);
				snapshots.put(ref, new MemberSnapshot(ref, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
			}
			final var support = backend.currentMember(3).orElseThrow();
			backend._capabilities.put(support, List.of(cap("combat.buff", "learned.buff", 450, 1040)));
			final var planned = new PhantomPartyTactics(null, backend).plan(backend.currentMember(1).orElseThrow(), List.copyOf(snapshots.keySet()), snapshots);
			PhantomAssertions.assertEquals(2, planned.size(), "The existing maintenance pulse bound changed.");
			PhantomAssertions.assertTrue(planned.stream().allMatch(directive -> !directive.actor().equals(directive.targetMember())), "Self maintenance consumed the pulse before ready peers received support.");
		});
		registry.add("094-idle-protection-prepares-caster-before-extra-peer-buffs", _ ->
		{
			final var backend = new MemoryPartyBackend(); final var snapshots = new LinkedHashMap<MemberRef, MemberSnapshot>();
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id);
				snapshots.put(ref, new MemberSnapshot(ref, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
			}
			final var support = backend.currentMember(3).orElseThrow();
			backend._capabilities.put(support, List.of(cap("combat.buff", "native.extra", 450, 1032), cap("combat.buff", "native.shield", 450, 1040)));
			backend._protectiveSkills.add(1040);
			final var tactics = new PhantomPartyTactics(null, backend);
			final var first = tactics.plan(backend.currentMember(1).orElseThrow(), List.copyOf(snapshots.keySet()), snapshots);
			PhantomAssertions.assertEquals(2, first.size(), "Protection changed the maintenance bound.");
			PhantomAssertions.assertTrue(first.stream().allMatch(directive -> directive.actionSkillId() == 1040), "Extra buffs displaced missing native protection.");
			backend._healthyMaintenance.addAll(Set.of("101:1040", "102:1040"));
			final var next = tactics.plan(backend.currentMember(1).orElseThrow(), List.copyOf(snapshots.keySet()), snapshots);
			PhantomAssertions.assertEquals(support, next.getFirst().targetMember(), "Caster protection starved behind extra peer buffs.");
			PhantomAssertions.assertEquals(1040, next.getFirst().actionSkillId(), "Caster did not receive its learned protection.");
		});
		registry.add("076-pending-support-retains-owner-only-for-current-native-party", context -> cooperativeSupport(context));
		registry.add("094-farm-source-refresh-does-not-cancel-unchanged-native-cast", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			final var clock = new AtomicLong(1_000_000_000L); final var lease = new PartyActionLease(); final var combat = cooperativeCombat(lease, clock);
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id); goals.put(id, goal(94000 + id, "farm.background", null, 0));
				backend.snapshot(ref, member(id, 100 + (int) id, id == 1 ? 20 : 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "heal", 100, 1001) : cap("combat.melee_damage", "weapon", 100, 0))));
			}
			backend._capabilities.put(backend.currentMember(1).orElseThrow(), List.of(cap("combat.heal", "heal", 100, 1001)));
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Farm source fixture did not start.");
			try
			{
				coordinator.formForGoal(1, 94001, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,3,2"), List.of()); coordinator.invite(1, backend.currentMember(2).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, lease.casts, "Native support cast was not admitted.");
				for (int pulse = 0; pulse < 15; pulse++) { clock.addAndGet(100_000_000L); coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, lease.casts, "Unchanged roots retried a still-running native cast across source refresh.");
				PhantomAssertions.assertEquals(0, lease.cancels, "A cache refresh gap cancelled protected native work.");
				clock.addAndGet(1_100_000_000L); coordinator.onPulse();
				PhantomAssertions.assertEquals(0, lease.cancels, "A delayed control pulse cancelled a cast before rechecking unchanged source roots.");
				final var delayedSnapshot = new AtomicBoolean(true);
				backend._afterSnapshot = member -> { if (member.profileId() == 2 && delayedSnapshot.compareAndSet(true, false)) { clock.addAndGet(1_100_000_000L); } };
				coordinator.onPulse(); backend._afterSnapshot = null;
				PhantomAssertions.assertFalse(delayedSnapshot.get(), "Delayed peer snapshot did not exercise the control slice.");
				PhantomAssertions.assertEquals(0, lease.cancels, "Mid-slice cache expiry cancelled a cast before fresh source reconciliation.");
				PhantomAssertions.assertEquals(1, lease.casts, "A slow unchanged peer observation replaced the pending native cast.");
				goals.put(3, goal(94003, "life.supply", null, 1));
				for (int pulse = 0; pulse < 11; pulse++) { clock.addAndGet(100_000_000L); coordinator.onPulse(); }
				PhantomAssertions.assertTrue(lease.cancels > 0, "Changed source root retained native authority beyond the hard source window.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Farm source fixture did not drain."); combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Farm source combat did not drain."); }
		});
		registry.add("094-source-read-failure-drains-finished-native-work", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			final var clock = new AtomicLong(1_000_000_000L); final var lease = new PartyActionLease(); final var combat = cooperativeCombat(lease, clock);
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id); goals.put(id, goal(94000 + id, "farm.background", null, 0));
				backend.snapshot(ref, member(id, 100 + (int) id, id == 1 ? 20 : 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "heal", 100, 1001) : cap("combat.melee_damage", "weapon", 100, 0))));
			}
			backend._capabilities.put(backend.currentMember(1).orElseThrow(), List.of(cap("combat.heal", "heal", 100, 1001)));
			final var coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Failed refresh fixture did not start.");
			try
			{
				coordinator.formForGoal(1, 94001, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,3,2"), List.of()); coordinator.invite(1, backend.currentMember(2).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, lease.casts, "Failed refresh fixture has no pending cast.");
				final var delayed = new AtomicBoolean(true);
				backend._afterSnapshot = member -> { if (member.profileId() == 2 && delayed.compareAndSet(true, false)) { clock.addAndGet(1_100_000_000L); goals._failNextLoad = true; } };
				boolean failed = false;
				try { coordinator.onPulse(); } catch (IllegalStateException expected) { if (!"TEST_GOAL_READ".equals(expected.getMessage())) { throw expected; } failed = true; }
				backend._afterSnapshot = null;
				PhantomAssertions.assertTrue(failed, "The control-plane source read failure was not exercised.");
				lease.casting = false; backend._capabilities.clear();
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertFalse(combat.hasClaim(1), "Failed source refresh lost the completed cast release retry.");
				PhantomAssertions.assertEquals(1, lease.casts, "Failed source refresh duplicated a cast.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Failed refresh fixture did not drain."); combat.beginStop(); PhantomAssertions.assertTrue(combat.finishStop(), "Failed refresh combat did not drain."); }
		});
		registry.add("01-assist-protect-heal-recharge-resurrect-support-plan", _ ->
		{
			final MemberRef leader = MemberRef.phantom(1, 101);
			final MemberRef support = MemberRef.phantom(2, 102);
			final MemberRef dead = MemberRef.real(103);
			final MemberSnapshot leaderSnapshot = member(1, 101, 40, 20, false, 900, List.of());
			final MemberSnapshot supportSnapshot = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "heal", 50, 1001), cap("combat.recharge", "recharge", 50, 1002), cap("combat.resurrection", "resurrection", 50, 1003), cap("combat.song", "song", 50, 1004)));
			final MemberSnapshot deadSnapshot = new MemberSnapshot(dead, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, false, 0, List.of(901), List.of(), ZERO);
			final List<DirectiveKind> kinds = new PhantomPartyTactics(null).plan(leader, List.of(leader, support, dead), Map.of(leader, leaderSnapshot, support, supportSnapshot, dead, deadSnapshot)).stream().map(value -> value.kind()).distinct().toList();
			PhantomAssertions.assertTrue(kinds.containsAll(List.of(DirectiveKind.ASSIST_TARGET, DirectiveKind.PROTECT_MEMBER, DirectiveKind.HEAL_MEMBER, DirectiveKind.RECHARGE_MEMBER, DirectiveKind.RESURRECT_MEMBER)), "Tactical capability coverage is incomplete.");
			PhantomAssertions.assertFalse(kinds.contains(DirectiveKind.PARTY_SUPPORT), "Planner fabricated a use-all support action without an exact target need.");
		});
		registry.add("130-farm-heal-ranks-only-exact-recipient-capabilities", _ ->
		{
			final var backend = new MemoryPartyBackend();
			final var victim = member(1, 101, 40, 100, false, 0, List.of());
			final var healer = member(2, 102, 100, 100, false, 0, List.of());
			final var unavailable = cap("combat.heal", "preferred.short", 100, 1402);
			final var available = cap("combat.heal", "reachable", 90, 1217);
			backend._capabilities.put(healer.ref(), List.of(unavailable, available));
			backend._farmHealCapabilities.put(healer.ref(), List.of(available));
			final var roster = List.of(victim.ref(), healer.ref());
			final var members = Map.of(victim.ref(), victim, healer.ref(), healer);
			final var tactics = new PhantomPartyTactics(null, backend);
			final var ordinary = tactics.plan(victim.ref(), roster, members).stream().filter(value -> value.kind() == DirectiveKind.HEAL_MEMBER).findFirst().orElseThrow();
			final var farm = tactics.plan(victim.ref(), roster, members, _ -> true, true).stream().filter(value -> value.kind() == DirectiveKind.HEAL_MEMBER).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(unavailable.actionSkillId(), ordinary.actionSkillId(), "The existing non-farm support order changed.");
			PhantomAssertions.assertEquals(available.actionSkillId(), farm.actionSkillId(), "An unavailable Heal blocked the exact farm recipient's legal fallback.");
			PhantomAssertions.assertEquals(victim.ref(), farm.targetMember(), "Fallback changed the intended Heal recipient.");
		});
		registry.add("02-external-combat-ownership-is-mandatory", context ->
		{
			final String tactics = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java");
			final String route = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertTrue(tactics.contains("acquireExternalAction") && route.contains("acquireExternalAction"), "Party mutation bypasses combat external ownership.");
		});
	}

	private static void lifecycle(PhantomTestRegistry registry)
	{
		registry.add("129-typed-handoff-consent-lifetime-and-native-party-restart", PhantomPartySuite::nativeHandoffPartyChain);
		registry.add("129-shared-source-preflight-before-consent-and-reachable-alternative", PhantomPartySuite::sharedSourcePreflight129);
		registry.add("129-support-travel-permission-is-exact-and-finite", context ->
		{
			for (var rosterIds : List.of(List.of(1L, 3L), List.of(1L, 2L, 3L)))
			{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(id, supportTravelRoot(id, 0, "farm.fixture"));
			}
			final var coordinator = coordinator(context, states, goals, backend, 64); backend.connect(coordinator); coordinator.start();
			try
			{
				final long now = System.currentTimeMillis(); final var source = new PhantomDomainRef("background.farm", "20101@farm.fixture");
				final var roster = rosterIds.stream().map(id -> new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Participant(backend.currentMember(id).orElseThrow(), 1, 129000 + id, 0)).toList();
				for (long id : rosterIds)
				{ coordinator.saveRendezvous(-1, new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState(id, "A".repeat(64), 1, roster, source, now - 1, now + 120_000, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ACCEPTED, 0)).orElseThrow(); }
				states._rejectRendezvousReads = true;
				final var original = goals.load(3).orElseThrow().goal();
				PhantomAssertions.assertTrue(coordinator.retainsNativeLifetime(1, goals.load(1).orElseThrow().goal(), 101, 1), "Accepted DD root lost its exact native lifetime in a pair.");
				PhantomAssertions.assertTrue(supportPermission(coordinator, backend, "supportRendezvousPreparation", 3, original), "RED129: a current accepted learned support needs its own travel-only preparation permission.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, original), "An unchanged old root cannot borrow the adopted travel exception.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousPreparation", 1, goals.load(1).orElseThrow().goal()), "A damage slot cannot borrow support-only admission.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, goals.load(1).orElseThrow().goal()), "An unchanged DD root acquired adopted travel permission.");
				final var adoptedDamage = supportTravelRoot(1, 1, "farm.fixture"); goals.put(1, adoptedDamage);
				PhantomAssertions.assertTrue(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, adoptedDamage), "The exact adopted DD root is held but cannot reach Decision/Travel.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, supportTravelRoot(1, 1, "farm.foreign")), "Adopted DD travel ignored shared source identity.");
				backend._nativeEpoch = 2; PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, adoptedDamage), "A new native epoch inherited adopted DD travel."); backend._nativeEpoch = 1;
				PhantomAssertions.assertTrue(coordinator.assemblyDeadline(3) > now, "ACCEPTED must hold new Solo starts through the root-CAS to ASSEMBLING receipt gap.");
				final var adopted = supportTravelRoot(3, 1, "farm.fixture"); goals.put(3, adopted);
				PhantomAssertions.assertTrue(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "The exact ACCEPTED+1 publication gap must preserve only the adopted shared root.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, supportTravelRoot(3, 1, "farm.foreign")), "A different source cannot reuse the reservation.");
				final var saved = states._rendezvous.get(3L);
				final var assembling = coordinator.saveRendezvous(saved.rowVersion(), saved.state().withPhase(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ASSEMBLING, 1)).orElseThrow();
				PhantomAssertions.assertTrue(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "ASSEMBLING retains the exact adopted support root.");
				backend._nativeEpoch = 2; PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "A new native lifetime cannot inherit travel permission."); backend._nativeEpoch = 1;
				final var actor = backend.currentMember(3).orElseThrow(); final var learned = backend.memberSnapshot(actor).orElseThrow();
				backend.snapshot(actor, member(3, 103, 100, 100, false, 0, List.of()));
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "Missing learned non-self support cannot retain the exception."); backend.snapshot(actor, learned);
				// Replace only the fixture's persisted snapshot, as restart reads do; no runtime permission writes are issued.
				final var prior = assembling.state();
				states._rendezvous.put(3L, new PhantomPartyPersistencePort.StoredRendezvous(3, assembling.rowVersion() + 1, new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState(3, prior.offerId(), 1, roster, source, now - 120_000, now - 1, prior.phase(), 1)));
				states._rejectRendezvousReads = false; coordinator.rendezvous(3); states._rejectRendezvousReads = true;
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "Expired consent cannot retain a travel exception.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, adoptedDamage), "Expired peer consent retained adopted DD Decision/Travel.");
				states._rendezvous.put(3L, assembling); states._rejectRendezvousReads = false; coordinator.rendezvous(3); states._rejectRendezvousReads = true;
				final var peer = states._rendezvous.get(1L); coordinator.saveRendezvous(peer.rowVersion(), peer.state().withPhase(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED, 0)).orElseThrow();
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "A cancelled roster participant revokes shared support travel.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, adoptedDamage), "Cancelled consent retained adopted DD Decision/Travel.");
				coordinator.beginStop();
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousPreparation", 3, original), "STOPPING cannot admit preparation.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "supportRendezvousTravel", 3, adopted), "STOPPING cannot preserve travel even though durable reservations remain.");
				PhantomAssertions.assertFalse(supportPermission(coordinator, backend, "rendezvousTravelDecision", 1, adoptedDamage), "STOPPING retained adopted DD Decision/Travel.");
				PhantomAssertions.assertEquals(rosterIds.size(), states._rendezvous.size(), "Stopping must not be represented as clearing durable consent.");
			}
			finally { states._rejectRendezvousReads = false; coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Support permission fixture did not drain."); }
			}
		});
		registry.add("129-bilateral-arrivals-precede-canonical-invitations", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				final var template = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0)));
				backend.snapshot(actor, new MemberSnapshot(actor, template.classId(), 0, (int) (id - 1) * 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO));
				goals.put(id, new PhantomGoal(129000 + id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "20101@farm.fixture")), new PhantomDomainRef("topology.anchor", "farm.fixture"), "background.history", 500, 0, 0, 0, Map.of(), "background.farm", 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var arrivals = new java.util.HashSet<Long>();
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> false,
					(id, root) ->
					{
						if (arrivals.size() < 3) { PhantomAssertions.assertEquals(0, backend.invitationCount(), "An invitation preceded all accepted travel arrivals."); }
						// Model-only route completion; the native campaign must independently prove actual travel.
						arrivals.add(id); final var actor = backend.currentMember(id).orElseThrow(); final var before = backend.memberSnapshot(actor).orElseThrow();
						backend.snapshot(actor, new MemberSnapshot(actor, before.classId(), 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), before.capabilities(), ZERO));
						return new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.ARRIVED, root.goalId(), root.revision(), actor.characterObjectId(), 1, 0, "fixture.arrived");
					}, new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true), (_, _, _, _, _) -> true, () -> 1000 + clock.get() / 1_000_000);
				for (int pulse = 0; pulse < 30; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); for (int control = 0; control < 8; control++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(3, arrivals.size(), "A member bypassed its arrival owner.");
				PhantomAssertions.assertEquals(3, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "Bilateral local arrivals did not reach the canonical invitation boundary.");
				PhantomAssertions.assertEquals(2, backend.invitationCount(), "Idempotent rendezvous replayed canonical invitations.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.COMPLETE, states.loadRendezvous(1).orElseThrow().state().phase(), "Native confirmation did not finish its preparty reservation.");
				// Crash window: canonical native roster committed, but the independent reservation completion was not stored.
				for (long id : List.of(1L, 2L, 3L))
				{
					final var receipt = states.loadRendezvous(id).orElseThrow(); final var prior = receipt.state();
					states._rendezvous.put(id, new PhantomPartyPersistencePort.StoredRendezvous(id, receipt.rowVersion() + 1,
						new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState(id, prior.offerId(), prior.leaderProfileId(), prior.roster(), prior.source(), prior.createdMillis(), prior.expiresMillis(), org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ARRIVED, prior.adoptedRevision())));
				}
				coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Initial canonical group did not stop.");
				backend._parties.clear(); backend._nativeEpoch = 2;
				final var resumed = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(resumed);
				final var resumedAdapter = new PhantomAutonomousFarmParty(resumed, backend, goals, currentCatalog(context), clock::get, (_, _) -> false, null,
					new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true), (_, _, _, _, _) -> false, () -> 1000 + clock.get() / 1_000_000);
				resumed.start();
				try
				{
					for (int pulse = 0; pulse < 24; pulse++) { resumed.onPulse(); }
					PhantomAssertions.assertEquals(3, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "Stale preparty epoch blocked the previously committed native roster after restart.");
					for (long id : List.of(4L, 5L, 6L))
					{
						final var actor = backend.add(id, 100 + (int) id);
						final var template = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 6 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0)));
						backend.snapshot(actor, new MemberSnapshot(actor, template.classId(), 0, (int) (id - 4) * 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO));
						goals.put(id, new PhantomGoal(129000 + id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "20101@farm.fixture")), new PhantomDomainRef("topology.anchor", "farm.fixture"), "background.history", 500, 0, 0, 0, Map.of(), "background.farm", 0));
					}
					for (int pulse = 0; pulse < 10; pulse++) { clock.addAndGet(6_000_000_000L); resumedAdapter.onPulse(); resumed.onPulse(); }
					PhantomAssertions.assertEquals(Set.of(4L, 5L, 6L), states.loadRendezvous(4).orElseThrow().state().roster().stream().map(member -> member.actor().profileId()).collect(java.util.stream.Collectors.toSet()), "An already grouped healer displaced an available support candidate.");
				}
				finally { resumed.beginStop(); PhantomAssertions.assertTrue(resumed.finishStop(), "Recovered canonical group did not drain."); }
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Arrival fixture did not drain."); }
		});
		registry.add("129-rendezvous-rotates-pending-members-without-native-membership", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				final var template = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0)));
				backend.snapshot(actor, new MemberSnapshot(actor, template.classId(), 0, (int) (id - 1) * 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO));
				goals.put(id, new PhantomGoal(129000 + id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "20101@farm.fixture")), new PhantomDomainRef("topology.anchor", "farm.fixture"), "background.history", 500, 0, 0, 0, Map.of(), "background.farm", 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var observed = new java.util.HashSet<Long>(); final var terminal = new java.util.concurrent.atomic.AtomicBoolean();
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> false,
					(id, root) -> { observed.add(id); return new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(terminal.get() ? org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.TERMINAL : org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.PENDING, root.goalId(), root.revision(), 100 + id.intValue(), 1, 0, "fixture.pending"); },
					new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true), (_, _, _, _, _) -> true, () -> 1000 + clock.get() / 1_000_000);
				for (int pulse = 0; pulse < 25; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); coordinator.onPulse(); }
				PhantomAssertions.assertEquals(Set.of(1L, 2L, 3L), observed, "A pending first traveler starved the other accepted participants.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Negotiation or pending travel invented a native Party.");
				final var saved = states.loadRendezvous(1).orElseThrow();
				PhantomAssertions.assertTrue(coordinator.saveRendezvous(saved.rowVersion() - 1, saved.state()).isEmpty(), "A competing stale reservation replaced its current owner.");
				terminal.set(true); clock.addAndGet(6_000_000_000L); adapter.onPulse();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED, states.loadRendezvous(1).orElseThrow().state().phase(), "A terminal route kept consent active instead of returning to Solo.");
				for (int pulse = 0; pulse < 3; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); coordinator.onPulse(); }
				PhantomAssertions.assertEquals(saved.state().offerId(), states.loadRendezvous(1).orElseThrow().state().offerId(), "Unchanged epochs repeated the cancelled offer before its cooldown.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Rendezvous fixture did not drain."); }
		});
		registry.add("129-terminal-current-source-cancels-before-adoption", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				final var template = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0)));
				backend.snapshot(actor, new MemberSnapshot(actor, template.classId(), 0, (int) (id - 1) * 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO));
				goals.put(id, new PhantomGoal(129000 + id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "20101@farm.fixture")), new PhantomDomainRef("topology.anchor", "farm.fixture"), "background.history", 500, 0, 0, 0, Map.of(), "background.farm", 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var preparationCalls = new java.util.concurrent.atomic.AtomicInteger();
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (_, _) -> false,
					(_, _) -> { throw new AssertionError("An unadopted reservation advanced native travel."); }, new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings(true, 100, 100, 100, 100, 100, true),
					new PhantomAutonomousFarmParty.RendezvousPreparation()
					{
						@Override
						public boolean prepare(long id, PhantomGoal root, PhantomDomainRef source, long expiry, java.util.function.BooleanSupplier current)
						{ preparationCalls.incrementAndGet(); return false; }
						@Override
						public boolean unusable(MemberRef actor, long epoch, PhantomGoal root)
						{
							PhantomAssertions.assertEquals(goals.load(actor.profileId()).orElseThrow().goal(), root, "Pre-adoption failure observation used a fabricated shared root.");
							PhantomAssertions.assertEquals(1L, epoch, "Cached route proof used a different actor epoch.");
							return true;
						}
					}, () -> 1000 + clock.get() / 1_000_000);
				for (int pulse = 0; pulse < 12; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); coordinator.onPulse(); }
				for (long id : List.of(1L, 2L, 3L))
					{ PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED, states.loadRendezvous(id).orElseThrow().state().phase(), "An already unusable current source kept preparty consent ACCEPTED."); }
				PhantomAssertions.assertEquals(0, preparationCalls.get(), "Terminal route attempted a new shared-root handoff.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "An unusable source fabricated native membership.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Terminal source fixture did not drain."); }
		});
		registry.add("128-obsolete-restart-core-releases-only-proven-absent-orphan", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			final var leader = backend.add(1, 101); final var peer = backend.add(4, 104); final var target = backend.add(2, 102);
			for (long id : List.of(1L, 2L, 4L)) { goals.put(id, goal(94000 + id, "farm.background", null, id == 1 ? 1 : 0)); }
			final var objective = new PhantomDomainRef("farm.party", "1,2,4"); final String group = PhantomPartyModel.sha256("128.obsolete.core");
			final var draft = new PartyState(group, 1, 1, StateStatus.LEADER, leader, "", ZERO, List.of(leader, peer), List.of(), ObjectiveMode.GENERAL_PVE, objective, List.of(), List.of(), null, null, ZERO, ZERO, "");
			final String manifest = draft.canonicalManifestHash();
			final var pending = new PartyOperation(PhantomPartyModel.sha256("128.pending"), OperationKind.JOIN, OperationPhase.PREPARED, leader, target, 94001, 0, manifest, 0, Long.MAX_VALUE, "");
			states.seed(1, new PartyState(group, 1, 1, StateStatus.INVITED_OUTBOUND, leader, "", manifest, draft.phantomMembers(), List.of(), draft.objectiveMode(), objective, List.of(), List.of(), null, pending, ZERO, ZERO, ""));
			states.seed(4, new PartyState(group, 1, 1, StateStatus.MEMBER, leader, "", manifest, draft.phantomMembers(), List.of(), draft.objectiveMode(), objective, List.of(), List.of(), null, pending.withPhase(OperationPhase.COMMITTED, 1, ""), ZERO, ZERO, ""));
			backend._unavailable.add(4L);
			final var orphanClock = new AtomicLong(1);
			final var coordinator = coordinator(context, states, goals, backend, 64, orphanClock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(1).orElseThrow().state().status(), "Changed prepared leader was not reconciled through existing startup owner.");
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(coordinator.blocksBackground(4), "Unknown unmaterialized absence released a recovery fence.");
				final int cachedLoads = goals._loads;
				for (int pulse = 0; pulse < 64; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(cachedLoads, goals._loads, "Cached obsolete source performed per-pulse goal reads.");
				backend._restartAbsent.add(4L);
				orphanClock.addAndGet(1_000_000_001L);
				final var preservedLeader = coordinator.claim(1).orElseThrow();
				final var protectedPublications = new java.util.concurrent.atomic.AtomicInteger();
				states._afterSave = saved ->
				{
					if (saved.profileId() == 4 && saved.state().status() == StateStatus.SOLO)
					{
						try (var admission = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(peer.characterObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
						{
							PhantomAssertions.assertTrue(admission == null, "Native admission raced between SOLO publication and absence postcheck.");
							protectedPublications.incrementAndGet();
						}
					}
				};
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, protectedPublications.get(), "Own orphan release was not protected exactly once.");
				PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(4).orElseThrow().state().status(), "Obsolete orphan kept historical preparation blocked without a native actor.");
				PhantomAssertions.assertFalse(coordinator.blocksBackground(4), "Recovered absent orphan retained Party ownership.");
				PhantomAssertions.assertEquals(preservedLeader, coordinator.claim(1).orElseThrow(), "Orphan recovery rewrote the leader's newer row.");
				for (long id : List.of(1L, 2L, 4L)) { PhantomAssertions.assertEquals(goal(94000 + id, "farm.background", null, id == 1 ? 1 : 0), goals.load(id).orElseThrow().goal(), "Orphan recovery rewrote a root."); }
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Orphan fixture did not drain."); }
		});
		registry.add("094-negative-phase-and-runtime-close-share-finished-scheduler", _ ->
		{
			final var metrics = new org.l2jmobius.gameserver.phantoms.PhantomMetrics();
			final var scheduler = new org.l2jmobius.gameserver.phantoms.PhantomScheduler(4, 100, 1, metrics,
				new org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace(false, 0, 0, metrics),
				org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.noop(), org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink.noop());
			scheduler.beginStop();
			PhantomAssertions.assertTrue(PhantomAutonomousParty094Native.schedulerDrained(scheduler), "Negative phase did not finish the scheduler.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.PhantomScheduler.BeginStopResult.ALREADY_STOPPED, scheduler.beginStop(), "Runtime close restarted an already finished stop.");
			PhantomAssertions.assertTrue(PhantomAutonomousParty094Native.schedulerDrained(scheduler), "Runtime close waited forever for an already stopped scheduler.");
			PhantomAssertions.assertEquals(0, scheduler.snapshot().scheduledTaskCount(), "Repeated close rearmed scheduling.");
		});
		registry.add("094-generic-refused-expired-join-preserves-committed-core", context ->
		{
			for (var outcome : List.of(TerminalOutcome.REFUSED, TerminalOutcome.EXPIRED, TerminalOutcome.CANCELLED))
			{
				final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
				final var leader = backend.add(1, 101); final var peer = backend.add(2, 102); final var remote = backend.add(3, 103);
				goals.put(1, goal(94001, PhantomPartyCoordinator.FORM_GOAL, null, 0));
				for (long id : List.of(2L, 3L)) { goals.put(id, goal(94000 + id, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0)); }
				final var coordinator = coordinator(context, states, goals, backend, 64); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Generic failure fixture did not start.");
				try
				{
					coordinator.form(1, 94001, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "failure.regroup"), List.of());
					coordinator.invite(1, peer, PartyDistributionType.FINDERS_KEEPERS); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
					final var root = goals.load(1).orElseThrow(); PhantomAssertions.assertEquals(PhantomPartyCoordinator.LEAD_GOAL, root.goal().goalType(), "Generic source transition was not exercised.");
					backend._farmEnabled = false; coordinator.invite(1, remote, PartyDistributionType.FINDERS_KEEPERS);
					coordinator.terminal(backend.lastInvitation(), OptionalLong.of(1), OptionalLong.of(3), outcome, "party.invite.failed"); coordinator.onPulse();
					PhantomAssertions.assertEquals(StateStatus.LEADER, coordinator.claim(1).orElseThrow().state().status(), "Third JOIN " + outcome + " discarded the existing leader/core.");
					PhantomAssertions.assertEquals(2, backend.observe(leader).orElseThrow().members().size(), "Third JOIN failure changed the existing native core.");
					PhantomAssertions.assertEquals(root, goals.load(1).orElseThrow(), "Third JOIN failure overwrote the active LEAD root.");
				}
				finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Generic failure fixture did not drain."); }
			}
		});
		registry.add("109-generic-three-member-cold-restart-retains-recovery-authority", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true;
			final var leader = backend.add(1, 101); final var damage = backend.add(2, 102); final var support = backend.add(3, 103);
			goals.put(1, goal(109001, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			for (long id : List.of(2L, 3L)) { goals.put(id, goal(109000 + id, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0)); }
			var coordinator = coordinator(context, states, goals, backend, 16); backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Generic cold restart fixture did not start.");
			try
			{
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(1, 109001, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "task109.gameplay"), List.of()), "Generic three-member form failed.");
				for (var member : List.of(damage, support))
				{
					if (!coordinator.committed(member.profileId())) { PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(1, member, PartyDistributionType.FINDERS_KEEPERS), "Generic initial invitation failed."); }
					for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				}
				PhantomAssertions.assertEquals(3, backend.observe(leader).orElseThrow().members().size(), "Generic fixture never formed three real backend members.");
				final var before = coordinator.claim(1).orElseThrow().state();
				for (long id : List.of(1L, 2L, 3L)) { PhantomAssertions.assertTrue(coordinator.committed(id) && coordinator.claim(id).orElseThrow().state().leaderManifestHash().equals(before.leaderManifestHash()), "Initial generic shared authority is not committed."); }
				coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Generic party did not stop before cold restart.");
				backend._parties.clear(); backend._lastInvitation = null;
				coordinator = coordinator(context, states, goals, backend, 16); backend.connect(coordinator);
				PhantomAssertions.assertTrue(coordinator.start(), "Generic cold party did not restart.");
				for (int pulse = 0; pulse < 32; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(3, backend.observe(leader).map(party -> party.members().size()).orElse(0), "Generic recovery stranded the third member after the first canonical JOIN.");
				final var restored = coordinator.claim(1).orElseThrow().state();
				for (long id : List.of(1L, 2L, 3L))
				{
					final var claim = coordinator.claim(id).orElseThrow().state();
					PhantomAssertions.assertTrue(coordinator.committed(id) && claim.groupId().equals(before.groupId()) && claim.groupGeneration() == restored.groupGeneration() && claim.leaderManifestHash().equals(restored.leaderManifestHash()), "Generic restored member lost exact shared authority.");
				}
				PhantomAssertions.assertEquals(4, backend.invitationCount(), "Generic recovery duplicated or bypassed native invitations.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Generic cold restart fixture did not drain."); }
		});
		registry.add("094-pending-remote-join-defers-and-resumes-exact-operation", context ->
		{
			for (boolean farm : List.of(true, false))
			{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var ref = backend.add(id, 100 + (int) id); goals.put(id, goal(94000 + id, farm ? "farm.background" : id == 1 ? PhantomPartyCoordinator.FORM_GOAL : PhantomPartyCoordinator.JOIN_GOAL, farm || id == 1 ? null : new PhantomDomainRef("character.object", "101"), 0));
				final var near = member(id, 100 + (int) id, 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0)));
				backend.snapshot(ref, id == 3 ? new MemberSnapshot(ref, near.classId(), 0, 25000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), near.capabilities(), ZERO) : near);
			}
			final var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
			var coordinator = coordinator(context, states, goals, backend, 16, clock::get); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Pending JOIN fixture did not start.");
			try
			{
				final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get);
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.formForGoal(1, 94001, 0, farm ? "farm.background" : PhantomPartyCoordinator.FORM_GOAL, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef(farm ? "farm.party" : "party", farm ? "1,3,2" : "pending.regroup"), List.of()), "Could not prepare the confirmed roster.");
				coordinator.invite(1, backend.currentMember(2).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				coordinator.invite(1, backend.currentMember(3).orElseThrow(), PartyDistributionType.FINDERS_KEEPERS);
				clock.addAndGet(6_000_000_000L); adapter.onPulse(); coordinator.onPulse();
				final var prepared = coordinator.claim(3).orElseThrow().state().operation();
				PhantomAssertions.assertEquals(OperationPhase.PREPARED, prepared.phase(), "Remote JOIN was published before safe regroup.");
				PhantomAssertions.assertEquals(1, backend.invitationCount(), "Remote pending JOIN consumed a native invitation.");
				for (int retry = 0; retry < 4; retry++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); coordinator.onPulse(); }
				PhantomAssertions.assertEquals(prepared, coordinator.claim(3).orElseThrow().state().operation(), "Retry replaced confirmed JOIN identity/deadline.");
				PhantomAssertions.assertTrue(coordinator.blocksBackground(3), "Pending JOIN still launched a competing solo journey.");
				final long firstFar = states.loadRegroupClock(3).orElseThrow().state().farSinceMillis();
				coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Pending JOIN did not stop.");
				backend._parties.clear(); backend._lastInvitation = null;
				coordinator = coordinator(context, states, goals, backend, 16, clock::get); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Pending JOIN did not restart.");
				final var resumedAdapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get);
				for (int pulse = 0; pulse < 16; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(2, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "Pending JOIN restart stranded the previously committed local core. farm=" + farm + " leader=" + coordinator.claim(1).map(value -> value.state().status() + ":" + value.state().operation().phase()) + " member=" + coordinator.claim(2).map(value -> value.state().status() + ":" + value.state().operation().phase()) + " root=" + goals.load(2).orElseThrow().goal().goalType() + " responses=" + backend.responseCount());
				PhantomAssertions.assertEquals(firstFar, states.loadRegroupClock(3).orElseThrow().state().farSinceMillis(), "Native core reconciliation reset the durable pending JOIN timer.");
				final int afterCoreRestart = backend.invitationCount();
				final var support = backend.currentMember(3).orElseThrow(); final var before = backend.memberSnapshot(support).orElseThrow();
				backend.snapshot(support, new MemberSnapshot(support, before.classId(), 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), before.capabilities(), ZERO));
				clock.addAndGet(100_000_000_000L); resumedAdapter.onPulse(); coordinator.onPulse(); coordinator.onPulse();
				PhantomAssertions.assertEquals(afterCoreRestart + 1, backend.invitationCount(), "Exact prepared JOIN did not resume through the native invitation service.");
				PhantomAssertions.assertTrue(coordinator.committed(3), "Resumed JOIN did not become committed native membership.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Pending JOIN fixture did not drain."); }
			}
		});
		registry.add("094-accepted-join-stop-restart-retains-shared-manifest", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			final var leader = backend.add(1, 101); final var target = backend.add(2, 102);
			goals.put(1, goal(940094, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			var coordinator = coordinator(context, states, goals, backend, 16); backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Accepted stop fixture did not start.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(1, 940094, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "accepted.stop"), List.of()), "Accepted stop form failed.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(1, target, PartyDistributionType.FINDERS_KEEPERS), "Accepted stop invite failed.");
			backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, target), PartyDistributionType.FINDERS_KEEPERS));
			coordinator.terminal(backend.lastInvitation(), OptionalLong.of(1), OptionalLong.of(2), TerminalOutcome.ACCEPTED, "party.invite.accepted");
			coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Accepted terminal did not drain.");
			final var beforeLeader = states.load(1).orElseThrow().state(); final var beforeMember = states.load(2).orElseThrow().state();
			PhantomAssertions.assertEquals(OperationPhase.CANONICAL_OBSERVED, beforeLeader.operation().phase(), "Stop fixture missed observed JOIN boundary.");
			PhantomAssertions.assertEquals(beforeLeader.leaderManifestHash(), beforeMember.leaderManifestHash(), "Prepared native sides did not share their admitted authority.");
			PhantomAssertions.assertFalse(beforeLeader.phantomMembers().equals(beforeMember.phantomMembers()), "Regression requires genuine leader/invitee local roster difference.");
			coordinator = coordinator(context, states, goals, backend, 16); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Observed JOIN did not restart.");
			try
			{
				for (long id : List.of(1L, 2L))
				{
					final var recovered = coordinator.claim(id).orElseThrow().state();
					PhantomAssertions.assertEquals(StateStatus.RECOVERING, recovered.status(), "Startup itself fabricated restart.claim_conflict from local roster views.");
					PhantomAssertions.assertEquals(beforeLeader.leaderManifestHash(), recovered.leaderManifestHash(), "Startup recomputed shared authority per member.");
					PhantomAssertions.assertEquals(beforeLeader.groupGeneration(), recovered.groupGeneration(), "Startup changed admitted generation.");
				}
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Observed JOIN restart did not drain."); }
		});
		registry.add("094-unrepairable-restart-member-does-not-starve-mixed-page", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L, 4L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, 100 + (int) id, 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(id, goal(94000 + id, "farm.background", null, 0));
			}
			final var stale = backend.currentMember(1).orElseThrow();
			final var operation = new PartyOperation(PhantomPartyModel.sha256("094.unsupported"), OperationKind.FORM, OperationPhase.COMMITTED, stale, null, 94001, 0, ZERO, 0, 1, "");
			states.seed(1, new PartyState(PhantomPartyModel.sha256("094.unsupported.group"), 1, 0, StateStatus.INCONSISTENT, stale, "", ZERO, List.of(stale), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,2,3"), List.of(), List.of(), null, operation, ZERO, ZERO, "restart.claim_conflict"));
			final var coordinator = coordinator(context, states, goals, backend, 16); backend.connect(coordinator); PhantomAssertions.assertTrue(coordinator.start(), "Mixed page fixture did not start.");
			try
			{
				new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), () -> 1_000_000_000L).onPulse();
				PhantomAssertions.assertEquals(1, backend.invitationCount(), "An ineligible restart fence starved healthy ordinary actors on the same page.");
				PhantomAssertions.assertEquals(StateStatus.INCONSISTENT, coordinator.claim(1).orElseThrow().state().status(), "Mixed-page progress removed an unsupported fence.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Mixed page fixture did not drain."); }
		});
		registry.add("094-restart-absence-races-preserve-fences-new-rows-and-peers", context ->
		{
			for (String race : List.of("root", "presence", "newer-row"))
			{
				final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
				backend._farmEnabled = true; backend._observeFarmAbsence = true;
				final var leader = backend.add(1, 101); final var peer = backend.add(2, 102);
				final var operation = new PartyOperation(PhantomPartyModel.sha256("094.absence.race"), OperationKind.JOIN, OperationPhase.CANONICAL_OBSERVED, leader, peer, 94001, 0, ZERO, 1, 1, "");
				final var fence = new PartyState(PhantomPartyModel.sha256("094.fenced.race"), 1, 0, StateStatus.INCONSISTENT, leader, "", ZERO, List.of(leader, peer), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,2,3"), List.of(), List.of(), null, operation, ZERO, ZERO, "restart.claim_conflict");
				states.seed(1, fence); states.seed(2, fence); goals.put(1, goal(94001, "farm.background", null, 0));
				final var coordinator = coordinator(context, states, goals, backend, 16); PhantomAssertions.assertTrue(coordinator.start(), "Race fixture did not start.");
				final var peerBefore = states.load(2).orElseThrow();
				try
				{
					if (race.equals("root")) { backend._afterAbsence = () -> goals.put(1, goal(94002, "life.supply", null, 1)); }
					else
					{
						states._afterSave = saved ->
						{
							if (saved.state().status() != StateStatus.SOLO) { return; }
							backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, MemberRef.real(900)), PartyDistributionType.FINDERS_KEEPERS));
							if (race.equals("newer-row")) { states._states.put(1L, new PhantomPartyPersistencePort.StoredPartyState(1, saved.rowVersion() + 1, fence.withOperation(StateStatus.INCONSISTENT, operation, "newer.claim.must_survive"))); }
						};
					}
					PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "A changed source was reported repaired: " + race);
					PhantomAssertions.assertEquals(StateStatus.INCONSISTENT, states.load(1).orElseThrow().state().status(), "Changed native/source authority lost its fence: " + race);
					if (race.equals("newer-row")) { PhantomAssertions.assertEquals("newer.claim.must_survive", states.load(1).orElseThrow().state().lastFailureKey(), "Post-check overwrote a newer claim."); }
					PhantomAssertions.assertEquals(peerBefore, states.load(2).orElseThrow(), "Race changed an unobserved peer.");
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Race replayed native JOIN.");
				}
				finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Race fixture did not drain."); }
			}
		});
		registry.add("094-restart-conflict-reconciles-only-proven-absent-own-member", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true;
			final var leader = backend.add(1, 101); final var peer = backend.add(2, 102);
			final var operation = new PartyOperation(PhantomPartyModel.sha256("094.absence"), OperationKind.JOIN, OperationPhase.CANONICAL_OBSERVED, leader, peer, 94001, 0, ZERO, 1, 1, "");
			final var fenced = new PartyState(PhantomPartyModel.sha256("094.fenced"), 1, 0, StateStatus.INCONSISTENT, leader, "", ZERO, List.of(leader, peer), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("farm.party", "1,2,3"), List.of(), List.of(), null, operation, ZERO, ZERO, "restart.claim_conflict");
			states.seed(1, fenced); states.seed(2, fenced);
			goals.put(1, goal(94001, "farm.background", null, 0));
			final var coordinator = coordinator(context, states, goals, backend, 16);
			PhantomAssertions.assertTrue(coordinator.start(), "Absence fixture did not start.");
			try
			{
				PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "Unavailable native observation cleared a conflict.");
				backend._observeFarmAbsence = true;
				backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, MemberRef.real(900)), PartyDistributionType.FINDERS_KEEPERS));
				PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "Existing real Party was cleared or left.");
				PhantomAssertions.assertTrue(backend.observe(leader).isPresent(), "Reconciliation mutated native membership.");
				backend._parties.clear(); backend._busy.add(1L);
				PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "Busy actor cleared a conflict.");
				backend._busy.clear();
				PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 1), "Stale root revision cleared a conflict.");
				final var priorPeer = coordinator.claim(2).orElseThrow(); final var priorRoot = goals.load(1).orElseThrow();
				PhantomAssertions.assertTrue(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "Exact native absence did not release the own restart fence.");
				PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(1).orElseThrow().state().status(), "Own absent claim was not SOLO.");
				PhantomAssertions.assertEquals(priorPeer, coordinator.claim(2).orElseThrow(), "One absence cleared an unobserved peer.");
				PhantomAssertions.assertEquals(priorRoot, goals.load(1).orElseThrow(), "Reconciliation rewrote ordinary motivation.");
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Absence reconciliation replayed JOIN.");
				PhantomAssertions.assertFalse(coordinator.reconcileAutonomousFarmAbsence(1, 94001, 0), "Already SOLO claim was rewritten.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Absence fixture did not drain."); }
		});
		registry.add("127-supply-preparation-precedes-native-formation", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(id, goal(94000 + id, "farm.background", null, 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var blocked = new java.util.HashSet<Long>(Set.of(3L));
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> blocked.contains(id), null);
				autonomous.onPulse();
				PhantomAssertions.assertFalse(coordinator.blocksBackground(3), "An unsettled supply child was captured by native JOIN.");
				PhantomAssertions.assertEquals(102, backend.lastInvitation().inviteeObjectId(), "The lawful damage pair was not admitted independently of its preparing peer.");
				blocked.clear();
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(2, backend.observe(backend.currentMember(1).orElseThrow()).orElseThrow().members().size(), "A later page expanded the sealed lawful pair.");
				PhantomAssertions.assertTrue(coordinator.releaseAutonomousFarm(1), "The quiet lawful pair could not finish before new recruitment.");
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(3, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "A settled supply obligation permanently disabled ordinary Party3.");
				for (long id : List.of(1L, 2L, 3L)) { PhantomAssertions.assertEquals(goal(94000 + id, "farm.background", null, 0), goals.load(id).orElseThrow().goal(), "Preparation admission rewrote its durable root."); }
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Preparation fixture did not drain."); }
		});
		registry.add("128-formed-farm-yields-quiet-members-for-production-preparation", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(id, goal(94000 + id, "farm.background", null, 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var blocked = new java.util.HashSet<Long>();
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> blocked.contains(id), null);
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				final var leader = backend.currentMember(1).orElseThrow();
				PhantomAssertions.assertEquals(3, backend.observe(leader).orElseThrow().members().size(), "Preparation handoff fixture did not form Party3.");
				final var formerObjective = coordinator.claim(1).orElseThrow().state().objectiveRef();
				blocked.add(3L); clock.addAndGet(6_000_000_000L); autonomous.onPulse();
				PhantomAssertions.assertTrue(backend.observe(leader).isEmpty(), "Formed farm retained native membership and permanently blocked solo supply.");
				PhantomAssertions.assertFalse(coordinator.blocksBackground(3), "Preparation retained the preparing member's Party owner.");
				for (long id : List.of(1L, 2L, 3L)) { PhantomAssertions.assertFalse(coordinator.claim(id).filter(row -> row.state().status() != StateStatus.SOLO && row.state().objectiveRef().equals(formerObjective)).isPresent(), "Preparation retained the former roster: " + id + "; claim=" + coordinator.claim(id)); PhantomAssertions.assertEquals(goal(94000 + id, "farm.background", null, 0), goals.load(id).orElseThrow().goal(), "Preparation replaced an ordinary root."); }
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(coordinator.releaseAutonomousFarm(1), "Remaining lawful pair did not release before new recruitment.");
				blocked.clear();
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(3, backend.observe(leader).orElseThrow().members().size(), "Settled preparation could not reform the original Party3.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Preparation handoff did not drain."); }
		});
		registry.add("127-late-supply-refuses-requester-and-invitee-consent", context ->
		{
			for (long changed : List.of(1L, 3L, 2L))
			{
				final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true; backend._observeFarmAbsence = true;
				for (long id : List.of(1L, 2L, 3L))
				{
					final var actor = backend.add(id, 100 + (int) id);
					backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0))));
					goals.put(id, goal(94000 + id, "farm.background", null, 0));
				}
				final var clock = new AtomicLong(1_000_000_000L); final var blocked = new java.util.HashSet<Long>();
				final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
				try
				{
					final var autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> blocked.contains(id), null);
					autonomous.onPulse(); PhantomAssertions.assertEquals(1, backend.invitationCount(), "Late-conflict fixture never issued its native invitation.");
					if (changed == 3)
					{
						blocked.add(changed);
						for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
						PhantomAssertions.assertEquals(2, backend.observe(backend.currentMember(1).orElseThrow()).orElseThrow().members().size(), "The retained-core fixture never formed native Party2.");
						PhantomAssertions.assertEquals(2, backend.invitationCount(), "The retained-core fixture did not invite its third member.");
					}
					blocked.add(changed);
					final var refused = backend.lastInvitation();
					for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
					PhantomAssertions.assertEquals(PartyInvitationService.Response.REFUSE, backend.lastResponse(), "Late supply was accepted or left JOIN pending: " + changed);
					for (int slice = 0; slice < 2; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); }
					final long invitee = refused.inviteeObjectId() - 100L;
					PhantomAssertions.assertFalse(coordinator.claim(invitee).filter(row -> row.state().status() != StateStatus.SOLO && row.state().operation() != null && row.state().operation().invitationSequence() == refused.identity().sequence()).isPresent(), "Refused consent retained its exact invitation owner; changed=" + changed);
					PhantomAssertions.assertFalse(coordinator.blocksBackground(changed), "A refused first invitation retained the changed actor's supply fence: " + changed + "; claim=" + coordinator.claim(changed));
					for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
					for (long id : List.of(1L, 2L, 3L)) { if (coordinator.claim(id).filter(row -> row.state().status() != StateStatus.SOLO && row.state().leader().profileId() == id).isPresent()) { PhantomAssertions.assertTrue(coordinator.releaseAutonomousFarm(id), "Quiet lawful remainder could not release: " + id); } }
					blocked.clear();
					for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
					PhantomAssertions.assertEquals(3, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "A settled late preparation could not form a fresh native Party; changed=" + changed + "; invites=" + backend.invitationCount() + "; responses=" + backend.responseCount() + "; leader=" + coordinator.claim(1) + "; support=" + coordinator.claim(3));
				}
				finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Late supply fixture did not drain."); }
			}
		});
		registry.add("127-solo-arrival-continuation-yields-to-supply", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
			for (long id : List.of(1L, 2L, 3L))
			{
				final var actor = backend.add(id, 100 + (int) id);
				backend.snapshot(actor, member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 3 ? cap("combat.buff", "learned.might", 450, 1068) : cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(id, goal(94000 + id, "farm.background", null, 0));
			}
			final var clock = new AtomicLong(1_000_000_000L); final var blocked = new java.util.HashSet<Long>(); final Map<Long, Integer> observed = new LinkedHashMap<>();
			final var preparationReads = new java.util.concurrent.atomic.AtomicInteger();
			final var kind = new java.util.concurrent.atomic.AtomicReference<>(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.PENDING);
			final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
			try
			{
				final var autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> { preparationReads.incrementAndGet(); return blocked.contains(id); }, (id, root) ->
				{
					observed.merge(id, 1, Integer::sum);
					return new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(kind.get(), root.goalId(), root.revision(), 100 + id.intValue(), 1, 0, "fixture127.arrival");
				});
				autonomous.onPulse();
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "A pending farm return was joined before native ARRIVED.");
				PhantomAssertions.assertEquals(3, observed.size(), "The selected solo cohort did not enter its existing travel owner.");
				backend._farmEnabled = false; blocked.add(1L); final int held = observed.get(1L);
				final int initialLoads = goals._loads, initialPreparationReads = preparationReads.get(); final var initialObserved = Map.copyOf(observed);
				for (int pulse = 0; pulse < 32; pulse++) { autonomous.onPulse(); }
				PhantomAssertions.assertEquals(initialLoads, goals._loads, "Sub-window shared pulses repeated durable goal reads.");
				PhantomAssertions.assertEquals(initialPreparationReads, preparationReads.get(), "Sub-window shared pulses repeated supply receipt reads.");
				PhantomAssertions.assertEquals(initialObserved, observed, "Sub-window shared pulses advanced an owned journey.");
				for (int pulse = 0; pulse < 4; pulse++)
				{
					final int before = observed.values().stream().mapToInt(Integer::intValue).sum();
					clock.addAndGet(1_000_000_000L); autonomous.onPulse();
					PhantomAssertions.assertTrue(observed.values().stream().mapToInt(Integer::intValue).sum() - before <= 2, "Continuation exceeded two journeys per control-plane window.");
				}
				PhantomAssertions.assertEquals(held, observed.get(1L), "A newly accepted supply child lost the movement handoff to a farm continuation.");
				PhantomAssertions.assertTrue(observed.get(2L) > 1 && observed.get(3L) > 1, "Pending journeys vanished outside the materialized-ready page.");
				final int groupedSecond = observed.get(2L), groupedThird = observed.get(3L);
				final var second = backend.currentMember(2).orElseThrow(); final var third = backend.currentMember(3).orElseThrow();
				backend.party(new PhantomPartyBackend.PartySnapshot(second, List.of(second, third), PartyDistributionType.FINDERS_KEEPERS));
				for (int pulse = 0; pulse < 4; pulse++) { clock.addAndGet(1_000_000_000L); autonomous.onPulse(); }
				backend._parties.clear();
				for (int pulse = 0; pulse < 4; pulse++) { clock.addAndGet(1_000_000_000L); autonomous.onPulse(); }
				PhantomAssertions.assertEquals(groupedSecond, observed.get(2L), "A native Party takeover retained obsolete solo queue bookkeeping.");
				PhantomAssertions.assertEquals(groupedThird, observed.get(3L), "Grouped solo continuation resumed without a new preparation selection.");
				kind.set(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.TERMINAL);
				blocked.clear(); for (int pulse = 0; pulse < 3; pulse++) { clock.addAndGet(1_000_000_000L); autonomous.onPulse(); }
				final var terminalCounts = Map.copyOf(observed); for (int pulse = 0; pulse < 4; pulse++) { clock.addAndGet(1_000_000_000L); autonomous.onPulse(); }
				PhantomAssertions.assertEquals(terminalCounts, observed, "Terminal travel was requeued without a new goal/epoch witness.");
				for (long id : List.of(1L, 2L, 3L)) { goals.put(id, goal(94000 + id, "farm.background", null, 1)); }
				kind.set(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.ARRIVED); backend._farmEnabled = true;
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(3, backend.observe(backend.currentMember(1).orElseThrow()).map(party -> party.members().size()).orElse(0), "Fresh ARRIVED for the current roots did not permit ordinary Party3.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Solo preparation fixture did not drain."); }
		});
		registry.add("094-ordinary-consent-fixed-roster-and-native-preparation-after-restart", context ->
		{
			final var states = new MemoryPartyStore();
			final var goals = new MemoryGoalStore();
			final var backend = new MemoryPartyBackend();
			backend._farmEnabled = true;
			final var leader = backend.add(1, 101);
			final var damage = backend.add(2, 102);
			final var support = backend.add(3, 103);
			backend.snapshot(leader, member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
			backend.snapshot(damage, member(2, 102, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
			backend.snapshot(support, member(3, 103, 100, 100, false, 0, List.of(cap("combat.buff", "learned.might", 450, 1068))));
			for (long profile : List.of(1L, 2L, 3L)) { goals.put(profile, goal(94000 + profile, "farm.background", null, 0)); }
			final var clock = new AtomicLong(1_000_000_000L);
			PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 64, clock::get);
			backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Ordinary farm owner did not start.");
			try
			{
				final var autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get);
				backend._busy.add(2L); // Isolate the invalid/busy actor; another lawful pair may otherwise form.
				goals.put(3, goal(94003, "idle.background.history", null, 0)); autonomous.onPulse();
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Arbitrary idle label bypassed canonical background motivation.");
				goals.put(3, historicalIdle(3, 94003, 0)); clock.addAndGet(6_000_000_000L);
				PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(3, goals.load(3).orElseThrow().goal()), "Canonical ordinary idle was not reusable by native reload.");
				PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.canonicalHistoricalIdle(1, goals.load(3).orElseThrow().goal()), "Native idle admission accepted a different profile owner.");
				backend._busy.add(3L); autonomous.onPulse();
				PhantomAssertions.assertEquals(0, backend.invitationCount(), "Initial selection invited an already busy support.");
				backend._busy.clear(); clock.addAndGet(6_000_000_000L);
				autonomous.onPulse();
				backend._unavailable.add(2L);
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(0, backend.responseCount(), "Consent accepted a participant that became busy after selection.");
				backend._unavailable.clear();
				for (int slice = 0; slice < 8; slice++)
				{
					clock.addAndGet(6_000_000_000L); autonomous.onPulse();
					for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				}
				PhantomAssertions.assertEquals(3, backend.observe(leader).map(party -> party.members().size()).orElse(0), "Ordinary current farm intents did not form three members; invites=" + backend.invitationCount() + "; responses=" + backend.responseCount() + "; claim=" + coordinator.claim(1));
				PhantomAssertions.assertEquals(2, backend.invitationCount(), "Formation duplicated its native invitations.");
				final var stale = backend.lastInvitation();
				PhantomAssertions.assertEquals(PreparationOutcome.REJECTED, coordinator.prepare(stale, OptionalLong.of(1), OptionalLong.of(stale.inviteeObjectId() - 100)), "Committed stale invitation was prepared again.");
				final var extra = backend.add(4, 104);
				backend.snapshot(extra, member(4, 104, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
				goals.put(4, goal(94004, "farm.background", null, 0));
				for (int slice = 0; slice < 4; slice++) { clock.addAndGet(6_000_000_000L); autonomous.onPulse(); coordinator.onPulse(); }
				PhantomAssertions.assertEquals(3, backend.observe(leader).orElseThrow().members().size(), "A later page grew the sealed ordinary roster.");
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Ordinary farm owner did not drain before restart.");
				backend._parties.clear(); backend._lastInvitation = null;
				coordinator = coordinator(context, states, goals, backend, 64, clock::get);
				backend.connect(coordinator);
				PhantomAssertions.assertTrue(coordinator.start(), "Same-state farm owner did not restart.");
				final var resumedAutonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get);
				for (int pulse = 0; pulse < 32; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(3, backend.observe(leader).map(party -> party.members().size()).orElse(0), "Restart bypassed PREPARED native invitation authority or lost a missing member.");
				PhantomAssertions.assertEquals(4, backend.invitationCount(), "Restart replayed or duplicated invitation delivery.");
				final int sourceReads = goals._loads;
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(goals._loads - sourceReads <= 4 * coordinator.snapshot().operationBudget(), "Goal reads exceeded the shared operation budget in an unchanged source window.");
				final int sourceWindowReads = goals._loads - sourceReads;
				backend.snapshot(damage, member(2, 102, 90, 60, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
				final int beforeTacticalChange = goals._loads;
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(sourceWindowReads, goals._loads - beforeTacticalChange, "HP/MP-only tactical score change invalidated unchanged source authority.");
				backend.snapshot(damage, member(2, 102, 100, 100, false, 0, List.of(cap("combat.melee_damage", "weapon", 300, 0))));
				context.record("task094.sourceWindowGoalReads", sourceWindowReads);
				final var supplyRoot = goal(94013, "life.supply", null, 1);
				goals.put(3, supplyRoot);
				clock.addAndGet(6_000_000_000L); resumedAutonomous.onPulse();
				PhantomAssertions.assertTrue(backend.observe(leader).isEmpty(), "Changed support root retained old native farm membership.");
				PhantomAssertions.assertEquals(supplyRoot, goals.load(3).orElseThrow().goal(), "Farm release overwrote the replacement root.");
				goals.put(3, historicalIdle(3, 94003, 0));
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); resumedAutonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				PhantomAssertions.assertEquals(3, backend.observe(leader).map(party -> party.members().size()).orElse(0), "Quiet SOLO farm claims could not reform from the same ordinary root.");
				backend.leave(support); final var offlineSupport = backend._snapshots.remove(support);
				for (int slice = 0; slice < 2; slice++) { clock.addAndGet(6_000_000_000L); resumedAutonomous.onPulse(); }
				PhantomAssertions.assertFalse(coordinator.claim(1).filter(row -> row.state().status() != StateStatus.SOLO && row.state().objectiveRef().key().equals("1,2,3")).isPresent(), "Native-absent support retained the former roster: " + coordinator.claim(1));
				PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(3).orElseThrow().state().status(), "Absent native membership retained a committed claim.");
				backend.snapshot(support, offlineSupport);
				for (int slice = 0; slice < 8; slice++) { clock.addAndGet(6_000_000_000L); resumedAutonomous.onPulse(); for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); } }
				final int resumedSize = backend.observe(leader).map(party -> party.members().size()).orElse(0);
				PhantomAssertions.assertTrue(resumedSize >= 2 && resumedSize <= 3, "A lawful remainder could not resume after its absent peer returned.");
				final var leaderSupply = goal(94011, "life.supply", null, 1); goals.put(1, leaderSupply);
				clock.addAndGet(1_100_000_000L);
				coordinator.onPulse();
				PhantomAssertions.assertTrue(backend.observe(leader).isEmpty(), "Changed leader root kept farm membership during reconcile.");
				PhantomAssertions.assertEquals(leaderSupply, goals.load(1).orElseThrow().goal(), "Reconcile replaced the leader's new root.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Ordinary farm fixture did not drain."); }
		});
		registry.add("094-committed-join-allows-third-member-without-new-goal", context ->
		{
			final var states = new MemoryPartyStore();
			final var goals = new MemoryGoalStore();
			final var backend = new MemoryPartyBackend();
			final var leader = backend.add(1, 101);
			final var second = backend.add(2, 102);
			final var third = backend.add(3, 103);
			goals.put(1, goal(94001, "farm.background", null, 0));
			final var coordinator = coordinator(context, states, goals, backend, 64);
			backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Farm party coordinator did not start.");
			try
			{
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.formForGoal(1, 94001, 0, "farm.background", ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "third-member"), List.of()), "Ordinary farm formation rejected.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(1, second, PartyDistributionType.FINDERS_KEEPERS), "First farm invitation rejected.");
				final var invitation = backend.lastInvitation();
				backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, second), PartyDistributionType.FINDERS_KEEPERS));
				coordinator.terminal(invitation, OptionalLong.of(1), OptionalLong.of(2), TerminalOutcome.ACCEPTED, "party.invite.accepted");
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(coordinator.committed(1), "Native observed first join did not commit.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(1, third, PartyDistributionType.FINDERS_KEEPERS), "Committed JOIN blocked a distinct third member.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.IDEMPOTENT, coordinator.invite(1, third, PartyDistributionType.FINDERS_KEEPERS), "Pending third-member retry was not idempotent.");
				PhantomAssertions.assertEquals(2, backend.invitationCount(), "Third member emitted duplicate invitations.");
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Farm party coordinator did not drain."); }
		});
		registry.add("12-real-roster-reconcile-retains-each-members-owned-goal-binding", context ->
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef first = backend.add(1, 101), second = backend.add(2, 102), human = MemberRef.real(201);
			goals.put(1, goal(59005911, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "201"), 0));
			goals.put(2, goal(59005912, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "201"), 0));
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 64);
			PhantomAssertions.assertTrue(coordinator.start(), "Per-member real-party lifecycle did not start.");
			try
			{
				backend.party(new PhantomPartyBackend.PartySnapshot(human, List.of(human, first), PartyDistributionType.FINDERS_KEEPERS));
				coordinator.terminal(invitation(new InvitationIdentity(59005911, 201, 101), first), OptionalLong.empty(), OptionalLong.of(1), TerminalOutcome.ACCEPTED, "");
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				final long generation = coordinator.claim(1).orElseThrow().state().groupGeneration();
				backend.party(new PhantomPartyBackend.PartySnapshot(human, List.of(human, first, second), PartyDistributionType.FINDERS_KEEPERS));
				coordinator.terminal(invitation(new InvitationIdentity(59005912, 201, 102), second), OptionalLong.empty(), OptionalLong.of(2), TerminalOutcome.ACCEPTED, "");
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertTrue(coordinator.claim(1).orElseThrow().state().groupGeneration() > generation, "A later native roster reused an old authority epoch.");
				for (long profile : List.of(1L, 2L))
				{
					final var owned = goals.load(profile).orElseThrow().goal();
					final var claim = coordinator.claim(profile).orElseThrow().state();
					PhantomAssertions.assertEquals(owned.goalId(), claim.operation().leaderGoalId(), "Roster reconciliation replaced a member's goal binding: profile=" + profile + "; claim=" + claim + "; goal=" + owned);
				}
				PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, backend.leave(human), "Canonical TEST human leave failed.");
				for (int pulse = 0; pulse < 8; pulse++) { coordinator.onPulse(); }
				for (long profile : List.of(1L, 2L)) { PhantomAssertions.assertEquals(PhantomGoalStatus.ABANDONED, goals.load(profile).orElseThrow().goal().status(), "Human leave orphaned a member's active party goal: " + profile + "; claim=" + coordinator.claim(profile) + "; goal=" + goals.load(profile)); }
			}
			finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Per-member real-party lifecycle did not drain."); }
		});
		registry.add("01-composite-is-bounded-and-isolates-failure", _ ->
		{
			final AtomicInteger first = new AtomicInteger();
			final AtomicInteger last = new AtomicInteger();
			final PhantomCompositeSchedulerControlPort composite = new PhantomCompositeSchedulerControlPort(List.of(first::incrementAndGet, () ->
			{
				throw new IllegalStateException("injected");
			}, last::incrementAndGet));
			composite.onPulse();
			PhantomAssertions.assertEquals(1, first.get(), "First composite stage did not run.");
			PhantomAssertions.assertEquals(1, last.get(), "Failure prevented later composite stage.");
			PhantomAssertions.assertEquals(1L, composite.snapshot().stageFailures(), "Composite failure was not counted.");
		});
		registry.add("02-more-than-eight-control-stages-rejected", _ -> PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCompositeSchedulerControlPort(java.util.stream.IntStream.range(0, 9).mapToObj(ignored -> (org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort) () ->
		{
		}).toList()), "Nine control stages were accepted."));
		registry.add("03-no-party-worker-or-future", context ->
		{
			final Path directory = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/party");
			final String all;
			try (var files = Files.walk(directory))
			{
				all = files.filter(path -> path.toString().endsWith(".java")).map(path ->
				{
					try
					{
						return Files.readString(path);
					}
					catch (Exception e)
					{
						throw new IllegalStateException(e);
					}
				}).reduce("", String::concat);
			}
			PhantomAssertions.assertFalse(java.util.regex.Pattern.compile("\\bnew\\s+Thread\\s*\\(").matcher(all).find() || all.contains("ExecutorService") || all.contains("ScheduledFuture"), "Party subsystem owns a worker/future.");
		});
		registry.add("04-transfer-and-leave-commit-exact-canonical-postconditions", PhantomPartySuite::membershipLifecycle);
		registry.add("05-coordinator-pulse-count-never-exceeds-budget", PhantomPartySuite::boundedPulse);
		registry.add("06-operation-budget-below-canonical-boundary-rejected", context ->
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> coordinator(context, states, goals, backend, 9), "Nine-operation party pulse budget was accepted.");
			final PhantomPartyCoordinator boundary = coordinator(context, states, goals, backend, 10);
			PhantomAssertions.assertEquals(10, boundary.snapshot().operationBudget(), "Ten-operation party pulse budget was not retained.");
		});
		registry.add("07-nine-member-budget-boundary-makes-progress", PhantomPartySuite::nineMemberBudget);
		registry.add("08-refusal-makes-form-goal-terminal-and-reusable", context -> terminalFormationReuse(context, TerminalOutcome.REFUSED, "party.invite.refused"));
		registry.add("09-timeout-makes-form-goal-terminal-and-reusable", context -> terminalFormationReuse(context, TerminalOutcome.EXPIRED, "party.invite.expired"));
		registry.add("10-inactive-join-goals-cannot-prepare-or-accept", PhantomPartySuite::inactiveJoinConsent);
		registry.add("11-inactive-membership-goals-cannot-transition", PhantomPartySuite::inactiveMembershipTransition);
		registry.add("094-stop-drains-already-queued-terminal-through-canonical-owner", context ->
		{
			final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend();
			final var leader = backend.add(1, 101); final var target = backend.add(2, 102);
			goals.put(1, goal(940094, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			final var coordinator = coordinator(context, states, goals, backend, 10); backend.connect(coordinator);
			PhantomAssertions.assertTrue(coordinator.start(), "Stop terminal fixture did not start.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(1, 940094, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "stop"), List.of()), "Stop fixture form failed.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(1, target, PartyDistributionType.FINDERS_KEEPERS), "Stop fixture invite failed.");
			coordinator.terminal(backend.lastInvitation(), OptionalLong.of(1), OptionalLong.of(2), TerminalOutcome.CANCELLED, "party.invite.cancelled");
			PhantomAssertions.assertEquals(1, coordinator.snapshot().terminalEvents(), "Stop regression did not queue its terminal event.");
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Already queued terminal blocked canonical stop.");
			PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(leader.profileId()).orElseThrow().state().status(), "Stop dropped terminal instead of processing its claim.");
		});
	}

	private static void inactiveJoinConsent(PhantomTestContext context)
	{
		for (PhantomGoalStatus status : List.of(PhantomGoalStatus.FAILED, PhantomGoalStatus.COMPLETED, PhantomGoalStatus.ABANDONED))
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef invitee = backend.add(2, 102);
			goals.put(invitee.profileId(), goal(2, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "999"), 0, status));
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
			try
			{
				PhantomAssertions.assertTrue(coordinator.start(), "Inactive-consent coordinator did not start.");
				final InvitationIdentity identity = new InvitationIdentity(status.ordinal() + 1L, 999, invitee.characterObjectId());
				final PartyInvitation invitation = new PartyInvitation(identity, 999, MemberRef.real(999).stableKey(), invitee.characterObjectId(), invitee.stableKey(), PartyDistributionType.FINDERS_KEEPERS, 999, Long.MAX_VALUE);
				PhantomAssertions.assertEquals(PreparationOutcome.REJECTED, coordinator.prepare(invitation, OptionalLong.empty(), OptionalLong.of(invitee.profileId())), status + " join goal prepared a real-to-Phantom invitation.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(invitation, invitee.profileId()), "Inactive-consent fixture could not enqueue managed delivery.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(PartyInvitationService.Response.REFUSE, backend.lastResponse(), status + " join goal accepted a managed invitation.");
				PhantomAssertions.assertTrue(coordinator.claim(invitee.profileId()).isEmpty(), status + " join goal created a membership claim.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Inactive-consent coordinator did not drain.");
			}
		}
	}

	private static void inactiveMembershipTransition(PhantomTestContext context)
	{
		for (PhantomGoalStatus status : List.of(PhantomGoalStatus.FAILED, PhantomGoalStatus.COMPLETED, PhantomGoalStatus.ABANDONED))
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef leader = backend.add(1, 101);
			final MemberRef member = backend.add(2, 102);
			backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, member), PartyDistributionType.FINDERS_KEEPERS));
			final String groupId = PhantomPartyModel.sha256("inactive.transition." + status);
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256(groupId + ".operation"), OperationKind.FORM, OperationPhase.COMMITTED, leader, member, 1, 0, ZERO, 0, 1, "");
			final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, List.of(leader, member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "inactive-transition"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
			final String manifest = draft.canonicalManifestHash();
			states.seed(leader.profileId(), new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			states.seed(member.profileId(), new PartyState(groupId, 1, 0, StateStatus.MEMBER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			goals.put(leader.profileId(), goal(1, PhantomPartyCoordinator.FORM_GOAL, null, 0, status));
			goals.put(member.profileId(), goal(2, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0, status));
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
			try
			{
				PhantomAssertions.assertTrue(coordinator.start(), "Inactive-transition coordinator did not start.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(status, goals.load(leader.profileId()).orElseThrow().goal().status(), status + " form goal was revived during leader transition.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.FORM_GOAL, goals.load(leader.profileId()).orElseThrow().goal().goalType(), status + " form goal changed to party.lead.");
				PhantomAssertions.assertEquals(status, goals.load(member.profileId()).orElseThrow().goal().status(), status + " join goal was revived during member transition.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.JOIN_GOAL, goals.load(member.profileId()).orElseThrow().goal().goalType(), status + " join goal changed to party.member.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Inactive-transition coordinator did not drain.");
			}
		}
	}

	private static void membershipLifecycle(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final MemberRef leader = backend.add(1, 101);
		final MemberRef member = backend.add(2, 102);
		backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, member), PartyDistributionType.FINDERS_KEEPERS));
		final String groupId = PhantomPartyModel.sha256("membership.lifecycle");
		final PartyOperation form = new PartyOperation(PhantomPartyModel.sha256("membership.form"), OperationKind.FORM, OperationPhase.COMMITTED, leader, null, 1, 0, ZERO, 0, 1, "");
		final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, List.of(leader, member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "lifecycle"), List.of(), List.of(), null, form, ZERO, ZERO, "");
		final String manifest = draft.canonicalManifestHash();
		states.seed(1, new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, form, ZERO, ZERO, ""));
		states.seed(2, new PartyState(groupId, 1, 0, StateStatus.MEMBER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, form, ZERO, ZERO, ""));
		goals.put(1, goal(1, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		goals.put(2, goal(1, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0));
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 32);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Lifecycle coordinator did not start.");
			coordinator.onPulse();
			final long generation = coordinator.claim(1).orElseThrow().state().groupGeneration();
			goals.put(1, goal(2, PhantomPartyCoordinator.TRANSFER_LEADER_GOAL, new PhantomDomainRef("profile", "2"), 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.transferLeaderTarget(1, 2, 0, generation, new PhantomDomainRef("profile", "2")), "Exact canonical leader transfer was rejected.");
			final PartyState transferred = coordinator.claim(2).orElseThrow().state();
			PhantomAssertions.assertEquals(member, transferred.leader(), "Transferred claim did not observe the canonical leader.");
			PhantomAssertions.assertEquals(generation + 1, transferred.groupGeneration(), "Leader transfer did not advance generation exactly once.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.IDEMPOTENT, coordinator.transferLeaderTarget(1, 2, 0, generation, new PhantomDomainRef("profile", "2")), "Exact transfer retry was not idempotent.");

			goals.put(1, goal(3, PhantomPartyCoordinator.LEAVE_GOAL, null, 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.leave(1, 3, 0, transferred.groupGeneration()), "Exact member leave was rejected.");
			PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(1).orElseThrow().state().status(), "Departed Phantom did not move to SOLO.");
			PhantomAssertions.assertFalse(coordinator.claim(2).orElseThrow().state().phantomMembers().contains(leader), "Remaining claim retained departed Phantom.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Lifecycle coordinator did not drain.");
		}
	}

	private static void boundedPulse(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		for (int index = 1; index <= 64; index++)
		{
			final MemberRef member = backend.add(index, 1000 + index);
			backend.party(new PhantomPartyBackend.PartySnapshot(member, List.of(member), PartyDistributionType.FINDERS_KEEPERS));
			final String groupId = PhantomPartyModel.sha256("bounded.group." + index);
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256("bounded.operation." + index), OperationKind.FORM, OperationPhase.COMMITTED, member, null, index, 0, ZERO, 0, 1, "");
			final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, member, "", ZERO, List.of(member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "bounded"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
			states.seed(index, new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), "", draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, operation, ZERO, ZERO, ""));
			goals.put(index, goal(index, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		}
		final int budget = 10;
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, budget);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Bounded coordinator did not start.");
			for (int pulse = 0; pulse < 100; pulse++)
			{
				coordinator.onPulse();
				PhantomAssertions.assertTrue(coordinator.snapshot().lastPulseExamined() <= budget, "Pulse examined work beyond its configured budget.");
			}
			PhantomAssertions.assertTrue(coordinator.snapshot().maximumPulseExamined() <= budget, "Historical pulse accounting exceeded the configured budget.");
			PhantomAssertions.assertEquals(64, coordinator.snapshot().partyClaims(), "Bounded pulse dropped a managed claim.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Bounded coordinator did not drain.");
		}
	}

	private static void nineMemberBudget(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final List<MemberRef> members = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(profileId -> backend.add(profileId, 2000 + (int) profileId)).toList();
		final MemberRef leader = members.getFirst();
		backend.party(new PhantomPartyBackend.PartySnapshot(leader, members, PartyDistributionType.FINDERS_KEEPERS));
		final String groupId = PhantomPartyModel.sha256("budget.boundary.group");
		final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256("budget.boundary.operation"), OperationKind.FORM, OperationPhase.COMMITTED, leader, null, 100, 0, ZERO, 0, 1, "");
		final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, members, List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "budget-boundary"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
		final String manifest = draft.canonicalManifestHash();
		for (MemberRef member : members)
		{
			final StateStatus status = member.equals(leader) ? StateStatus.LEADER : StateStatus.MEMBER;
			states.seed(member.profileId(), new PartyState(groupId, 1, 0, status, leader, "", manifest, members, List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			goals.put(member.profileId(), goal(100, member.equals(leader) ? PhantomPartyCoordinator.FORM_GOAL : PhantomPartyCoordinator.JOIN_GOAL, member.equals(leader) ? null : new PhantomDomainRef("character.object", Integer.toString(leader.characterObjectId())), 0));
		}
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Nine-member boundary coordinator did not start.");
			PhantomAssertions.assertTrue(members.stream().allMatch(member -> coordinator.claim(member.profileId()).orElseThrow().state().status() == StateStatus.RECOVERING), "Restart fixture did not begin at the reconcile boundary.");
			coordinator.onPulse();
			PhantomAssertions.assertEquals(StateStatus.LEADER, coordinator.claim(leader.profileId()).orElseThrow().state().status(), "Nine-member group remained in the due queue without canonical progress.");
			PhantomAssertions.assertTrue(members.stream().skip(1).allMatch(member -> coordinator.claim(member.profileId()).orElseThrow().state().status() == StateStatus.MEMBER), "Nine-member reconcile did not publish all canonical member claims.");
			PhantomAssertions.assertEquals(10, coordinator.snapshot().lastPulseExamined(), "Nine-member boundary did not consume the exact group plus claim budget.");
			PhantomAssertions.assertTrue(coordinator.snapshot().maximumPulseExamined() <= 10, "Nine-member boundary exceeded its configured pulse budget.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Nine-member boundary coordinator did not drain.");
		}
	}

	private static void terminalFormationReuse(PhantomTestContext context, TerminalOutcome terminalOutcome, String reasonKey)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final MemberRef leader = backend.add(1, 101);
		final MemberRef firstTarget = backend.add(2, 102);
		final MemberRef secondTarget = backend.add(3, 103);
		final long oldGoalId = 700;
		final long newGoalId = 701;
		goals.put(leader.profileId(), goal(oldGoalId, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
		backend.connect(coordinator);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Terminal formation coordinator did not start.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(leader.profileId(), oldGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal"), List.of()), "Initial form operation was rejected.");
			final String oldGroupId = coordinator.claim(leader.profileId()).orElseThrow().state().groupId();
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(leader.profileId(), firstTarget, PartyDistributionType.FINDERS_KEEPERS), "Initial invitation was rejected.");
			final PartyInvitation firstInvitation = backend.lastInvitation();
			PhantomAssertions.assertEquals(1, backend.invitationCount(), "Initial form emitted more than one invitation sequence.");

			coordinator.terminal(firstInvitation, OptionalLong.of(leader.profileId()), OptionalLong.of(firstTarget.profileId()), terminalOutcome, reasonKey);
			coordinator.onPulse();
			final PartyState reusable = coordinator.claim(leader.profileId()).orElseThrow().state();
			PhantomAssertions.assertEquals(StateStatus.SOLO, reusable.status(), "Terminal formation did not make the leader reusable.");
			PhantomAssertions.assertEquals(OperationPhase.ABORTED, reusable.operation().phase(), "Terminal formation lost exact ABORTED evidence.");
			PhantomAssertions.assertEquals(firstInvitation.identity().sequence(), reusable.operation().invitationSequence(), "Terminal formation lost the exact invitation sequence.");
			PhantomAssertions.assertEquals(reasonKey, reusable.operation().failureKey(), "Terminal formation lost the typed operation failure.");
			PhantomAssertions.assertEquals(reasonKey, reusable.lastFailureKey(), "Terminal formation lost the typed state failure.");
			PhantomAssertions.assertFalse(reusable.groupId().equals(oldGroupId), "Terminal formation retained the old group identity.");
			PhantomAssertions.assertEquals(0, coordinator.snapshot().groups(), "Terminal formation retained the old GroupRuntime/due entry.");
			final PhantomGoal failedGoal = goals.load(leader.profileId()).orElseThrow().goal();
			PhantomAssertions.assertEquals(PhantomGoalStatus.FAILED, failedGoal.status(), "Terminal form goal did not become FAILED.");
			PhantomAssertions.assertEquals(1L, failedGoal.revision(), "Terminal form goal revision did not advance exactly once.");
			PhantomAssertions.assertEquals(reasonKey, failedGoal.reasonKey(), "Terminal form goal lost the terminal reason.");

			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.GOAL_MISMATCH, coordinator.form(leader.profileId(), oldGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal"), List.of()), "Stale form decision step was accepted.");
			PhantomAssertions.assertEquals(1, backend.invitationCount(), "Stale form decision emitted an automatic retry invitation.");

			goals.put(leader.profileId(), goal(newGoalId, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(leader.profileId(), newGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal-retry"), List.of()), "New exact form goal could not reuse SOLO.");
			final PartyState newFormation = coordinator.claim(leader.profileId()).orElseThrow().state();
			PhantomAssertions.assertFalse(newFormation.groupId().equals(oldGroupId), "New exact form goal reused the terminal group identity.");
			PhantomAssertions.assertTrue(newFormation.groupGeneration() > reusable.groupGeneration(), "New exact form goal did not advance generation.");
			PhantomAssertions.assertEquals(OperationKind.FORM, newFormation.operation().kind(), "New exact form goal did not create FORM evidence.");
			PhantomAssertions.assertEquals(OperationPhase.PREPARED, newFormation.operation().phase(), "New exact form goal did not create PREPARED evidence.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(leader.profileId(), secondTarget, PartyDistributionType.FINDERS_KEEPERS), "New exact form goal could not invite another target.");
			final PartyInvitation secondInvitation = backend.lastInvitation();
			PhantomAssertions.assertEquals(2, backend.invitationCount(), "New exact form goal did not create exactly one new invitation.");

			coordinator.terminal(firstInvitation, OptionalLong.of(leader.profileId()), OptionalLong.of(firstTarget.profileId()), terminalOutcome, reasonKey);
			coordinator.onPulse();
			final PartyOperation stillPending = coordinator.claim(leader.profileId()).orElseThrow().state().operation();
			PhantomAssertions.assertEquals(secondInvitation.identity().sequence(), stillPending.invitationSequence(), "Stale terminal callback replaced the new invitation identity.");
			PhantomAssertions.assertFalse(stillPending.phase() == OperationPhase.ABORTED, "Stale terminal callback aborted the new operation.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goals.load(leader.profileId()).orElseThrow().goal().status(), "Stale terminal callback failed the new form goal.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Terminal formation coordinator did not drain.");
		}
	}

	private static void integration(PhantomTestRegistry registry)
	{
		registry.add("00-test-database-is-allowlisted", context ->
		{
			final Path config = Path.of(System.getProperty("phantom.test.config", context.moduleRoot().resolve(".phantom-local/Database.test.ini").toString()));
			PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
			PhantomAssertions.assertTrue(Files.readString(config).contains(PhantomTestDatabaseGuard.TARGET_DATABASE), "Party integration is not pinned to the Phantom test database.");
		});
		registry.add("01-production-composition-is-complete", context ->
		{
			final String system = source(context, "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java");
			PhantomAssertions.assertTrue(system.contains("new L2jPhantomPartyBackend") && system.contains("new PhantomPartyStore") && system.contains("new PhantomCompositeSchedulerControlPort"), "Production party composition is incomplete.");
		});
		registry.add("02-current-catalog-covers-support-and-damage", context ->
		{
			final PhantomPartyRoleCatalog catalog = currentCatalog(context);
			for (String role : List.of("frontline.guardian", "damage.melee", "damage.ranged", "support.healer", "support.recharge", "support.enhancement", "control.specialist"))
			{
				PhantomAssertions.assertTrue(catalog.contains(role), "Current catalog misses role " + role);
			}
		});
		registry.add("03-production-backend-copies-canonical-party-state", context ->
		{
			final String backend = source(context, "java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java");
			PhantomAssertions.assertTrue(backend.contains("party.getMembers().stream()") && backend.contains("party.getLeader()") && backend.contains("getDistributionType()"), "Production backend does not copy exact canonical Party state.");
			PhantomAssertions.assertFalse(backend.contains("getMembers().add") || backend.contains("getMembers().remove"), "Production backend mutates Party member list directly.");
		});
	}

	private static void performance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-pulses-use-one-control-chain", context ->
		{
			final AtomicInteger population = new AtomicInteger();
			final AtomicInteger party = new AtomicInteger();
			final PhantomCompositeSchedulerControlPort composite = new PhantomCompositeSchedulerControlPort(List.of(population::incrementAndGet, party::incrementAndGet));
			final long started = System.nanoTime();
			for (int pulse = 0; pulse < 100_000; pulse++)
			{
				composite.onPulse();
			}
			final long elapsed = System.nanoTime() - started;
			PhantomAssertions.assertEquals(100_000, population.get(), "Population stage lost pulses.");
			PhantomAssertions.assertEquals(100_000, party.get(), "Party stage lost pulses.");
			context.record("partyPerformance.pulses", 100000);
			context.record("partyPerformance.syntheticProfiles", 10000);
			context.record("partyPerformance.syntheticGroups", 1000);
			context.record("partyPerformance.elapsedNanos", elapsed);
		});
		registry.add("02-nine-member-matching-remains-bounded", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final List<MemberSnapshot> members = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(value -> member(value, 100 + (int) value, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", (int) value, 1)))).toList();
			final List<RoleRequirement> requirements = java.util.stream.IntStream.range(0, 12).mapToObj(value -> new RoleRequirement("slot.damage." + value, "damage.melee", true, 1)).toList();
			for (int group = 0; group < 1000; group++)
			{
				PhantomAssertions.assertTrue(matcher.match(ObjectiveMode.GENERAL_PVE, requirements, members).assignments().size() <= 9, "Matcher assigned more members than the roster contains.");
			}
			PhantomAssertions.assertEquals(9, members.size(), "Bounded party matcher fixture changed.");
			context.record("partyPerformance.roleRequirementsPerGroup", requirements.size());
		});
	}

	private static PhantomPartyRoleCatalog currentCatalog(PhantomTestContext context)
	{
		return PhantomPartyRoleCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
	}

	private static String source(PhantomTestContext context, String relative) throws Exception
	{
		return Files.readString(context.moduleRoot().resolve(relative));
	}

	private static MemberCapability cap(String key, String variant, int rank, int skillId)
	{
		return new MemberCapability(key, variant, rank, skillId, 1, "PARTY_MEMBER", true, true, true, "ready", rank * 10, "test.progression.fixture");
	}

	private static MemberSnapshot member(long profileId, int objectId, int hp, int mp, boolean dead, int target, List<MemberCapability> capabilities)
	{
		return new MemberSnapshot(MemberRef.phantom(profileId, objectId), 0, 0, 0, 0, 0, hp, mp, 100, dead, false, false, false, target, List.of(901), capabilities, ZERO);
	}

	private static PartyOperation operationFixture()
	{
		final MemberRef leader = MemberRef.phantom(1, 101);
		return new PartyOperation(PhantomPartyModel.sha256("operation"), OperationKind.JOIN, OperationPhase.PREPARED, leader, MemberRef.phantom(2, 102), 1, 0, ZERO, 0, 1, "");
	}

	private static PartyState stateFixture()
	{
		final MemberRef leader = MemberRef.phantom(1, 101);
		final PartyState draft = rawState(leader, List.of(leader, MemberRef.phantom(2, 102)), List.of(MemberRef.real(201)));
		return new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), draft.route(), draft.operation(), draft.progressionHash(), draft.topologyHash(), draft.lastFailureKey());
	}

	private static PartyState rawState(MemberRef leader, List<MemberRef> phantoms, List<MemberRef> reals)
	{
		return new PartyState(PhantomPartyModel.sha256("group"), 1, 2, StateStatus.LEADER, leader, "frontline.guardian", ZERO, phantoms, reals, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "fixture"), List.of(new RoleRequirement("slot.guardian", "frontline.guardian", true, 1)), List.of(), null, operationFixture(), ZERO, ZERO, "");
	}

	private static RouteManifest routeFixture()
	{
		return new RouteManifest(PhantomPartyModel.sha256("route"), 1, new PhantomDomainRef("location", "fixture"), List.of(new PhantomNavigationPoint(0, 0, 0, 0), new PhantomNavigationPoint(100, 100, 0, 0)), 0, 250, 1500, RouteStatus.MOVING, ZERO, ZERO);
	}

	private static PhantomSemanticAct semantic(long generation)
	{
		return PhantomPartySemanticActs.create(PhantomPartySemanticActs.ASSIST_REQUESTED, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc.object", "900"), PhantomPartyModel.sha256("semantic.group"), generation, "leader.target", 9000, Map.of(), Map.of("target.object", 900L), "party.tactics.test");
	}

	private static PhantomPartyCoordinator coordinator(PhantomTestContext context, MemoryPartyStore states, MemoryGoalStore goals, MemoryPartyBackend backend, int budget)
	{
		return coordinator(context, states, goals, backend, budget, System::nanoTime);
	}

	private static PhantomPartyCoordinator coordinator(PhantomTestContext context, MemoryPartyStore states, MemoryGoalStore goals, MemoryPartyBackend backend, int budget, java.util.function.LongSupplier clock)
	{
		return new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> ZERO, clock, budget);
	}

	private static PhantomGoal goal(long goalId, String type, PhantomDomainRef target, long revision)
	{
		return goal(goalId, type, target, revision, PhantomGoalStatus.ACTIVE);
	}

	private static boolean supportPermission(PhantomPartyCoordinator coordinator, MemoryPartyBackend backend, String method, long profileId, PhantomGoal root) throws Exception
	{
		backend._rejectCurrentMemberReads = true;
		try { return (boolean) PhantomPartyCoordinator.class.getMethod(method, long.class, PhantomGoal.class).invoke(coordinator, profileId, root); }
		catch (NoSuchMethodException oldProduction) { return false; }
		catch (java.lang.reflect.InvocationTargetException failure) { if (failure.getCause() instanceof Error error) { throw error; } throw failure; }
		finally { backend._rejectCurrentMemberReads = false; }
	}
	private static PhantomGoal supportTravelRoot(long profileId, long revision, String anchor)
	{
		return new PhantomGoal(129000 + profileId, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(new PhantomDomainRef("background.farm", "20101@" + anchor)), new PhantomDomainRef("topology.anchor", anchor), "farm.background", 500, 0, 0, 0, Map.of(), "background.catchup.plan", revision);
	}

	private static void sharedSourcePreflight129(PhantomTestContext context) throws Exception
	{
		final var travel = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.sourceRoutes129();
		final String local = "generated.farm.4c99bd428d6bae74750f6eec.anchor", closed = "generated.farm.72007cc387f17e963e4fcf2c.anchor", reachable = "generated.farm.c34d4845871d5a78bc5d3cd1.anchor";
		final var rejectedSource = new PhantomDomainRef("background.farm", "21798@" + closed);
		final var sharedSource = new PhantomDomainRef("background.farm", "21315@" + reachable);
		PhantomAssertions.assertTrue(travel.route(local, closed).isEmpty() && travel.route(reachable, closed).isEmpty(), "Generated Imperial source unexpectedly acquired an admitted edge.");
		PhantomAssertions.assertTrue(travel.route(local, reachable).isPresent(), "Actual source graph has no connected alternative for the two DD origins.");
		final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true; backend._farmLevel = 78;
		final var clock = new AtomicLong(1_000_000_000L); final var busy = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var structuralLoss = new java.util.concurrent.atomic.AtomicBoolean(false);
		final var previewCounts = new java.util.HashMap<Long, Integer>(); final var arrivals = new HashSet<Long>();
		final var origins = Map.of(1L, local, 7L, local, 4L, reachable, 5L, closed, 23L, reachable, 24L, local);
		final var seeking = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults();
		PhantomAssertions.assertTrue(List.of(1L, 7L, 4L, 5L, 23L).stream().allMatch(id -> seeking.seeks(id, 78)) && !seeking.seeks(24, 78), "The default SEEK/SOLO controls changed.");
		for (long id : List.of(1L, 7L, 4L, 5L, 23L, 24L))
		{
			final var actor = backend.add(id, 129000 + (int) id);
			final boolean support = id == 4 || id == 23;
			final var caps = List.of(support ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0));
			// Model-only current positions deliberately put the inaccessible DD nearer than the reachable DD.
			final int x = id == 1 ? 0 : id == 5 ? 100 : id == 7 ? 30000 : 60000;
			backend.snapshot(actor, new MemberSnapshot(actor, support ? 17 : id == 1 ? 88 : 2, 0, x, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), caps, ZERO));
			goals.put(id, sourceRoot129(id, 0, id == 1 || id == 5 ? rejectedSource : sharedSource));
		}
		final var coordinator = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(coordinator); coordinator.start();
		try
		{
			final var preparation = new PhantomAutonomousFarmParty.RendezvousPreparation()
			{
				@Override public PhantomAutonomousFarmParty.SourcePreview preview(MemberRef actor, long epoch, PhantomGoal root)
				{
					previewCounts.merge(actor.profileId(), 1, Integer::sum);
					final java.util.function.Predicate<PhantomDomainRef> route = source -> travel.route(origins.get(actor.profileId()), source.key().substring(source.key().indexOf('@') + 1)).isPresent();
					return new PhantomAutonomousFarmParty.SourcePreview(() -> List.of(rejectedSource, sharedSource), route,
						() -> backend._nativeEpoch == epoch && goals.load(actor.profileId()).map(stored -> stored.goal().equals(root)).orElse(false),
						source -> !route.test(source) || structuralLoss.get() && actor.profileId() == 7 && source.equals(sharedSource));
				}
				@Override public boolean prepare(long id, PhantomGoal root, PhantomDomainRef source, long expires, java.util.function.BooleanSupplier current)
				{
					PhantomAssertions.assertTrue(current.getAsBoolean(), "Source preview acquired adoption rights before bilateral consent.");
					if (busy.get()) { return false; }
					PhantomAssertions.assertEquals(sharedSource, source, "An inaccessible source reached the modeled native preparation leaf.");
					if (!root.validSources().contains(source)) { goals.put(id, sourceRoot129(id, root.revision() + 1, source)); }
					return true;
				}
			};
			final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> id == 23,
				(id, root) ->
				{
					if (!arrivals.contains(id)) { PhantomAssertions.assertEquals(0, backend.invitationCount(), "An invitation preceded final bilateral modeled arrival."); }
					arrivals.add(id); final var actor = backend._members.get(id); final var before = backend._snapshots.get(actor);
					backend.snapshot(actor, new MemberSnapshot(actor, before.classId(), 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), before.capabilities(), ZERO));
					return new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.ARRIVED, root.goalId(), root.revision(), actor.characterObjectId(), backend._nativeEpoch, 0, "modeled.arrived");
				}, seeking, preparation, () -> 1000 + clock.get() / 1_000_000);
			clock.addAndGet(6_000_000_000L); adapter.onPulse();
			PhantomAssertions.assertFalse(states._rendezvous.values().stream().anyMatch(row -> row.state().source().equals(rejectedSource)), "RED129: an unreachable generated72007 source was durably offered before whole-roster route admission.");
			PhantomAssertions.assertEquals(Set.of(1L, 7L, 4L), states._rendezvous.keySet(), "An unreachable nearest DD, supply actor or nonseeker displaced the reachable core.");
			PhantomAssertions.assertTrue(previewCounts.values().stream().allMatch(count -> count == 1), "Source/member pairs reread the preview rather than reusing one pulse-local input.");
			for (int pulse = 0; pulse < 6; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); }
			PhantomAssertions.assertTrue(states._rendezvous.values().stream().noneMatch(row -> row.state().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED), "Transient native busy state became structural route loss.");
			PhantomAssertions.assertEquals(0, backend.invitationCount(), "Dry preview or transient preparation granted native membership.");
			// Model a newly observed exact structural refusal while this actor still owns a different predecessor source.
			structuralLoss.set(true);
			for (int pulse = 0; pulse < 3; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); }
			PhantomAssertions.assertTrue(states._rendezvous.values().stream().allMatch(row -> row.state().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED), "Known exact source loss was held ACCEPTED merely because the predecessor source differed.");
			PhantomAssertions.assertEquals(0L, goals.load(1).orElseThrow().goal().revision(), "Structural refusal adopted or rewrote a predecessor.");
			PhantomAssertions.assertEquals(0, backend.invitationCount(), "Structural refusal granted a native invitation.");
			structuralLoss.set(false);
			clock.addAndGet((org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.MAXIMUM_LIFETIME_MILLIS + 1) * 1_000_000L);
			busy.set(false);
			for (int pulse = 0; pulse < 35; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); for (int step = 0; step < 8; step++) { coordinator.onPulse(); } }
			PhantomAssertions.assertEquals(Set.of(1L, 7L, 4L), arrivals, "A member bypassed ordinary accepted preparation/arrival ownership.");
			PhantomAssertions.assertEquals(2, backend.invitationCount(), "Reachable alternative failed canonical native invitation dispatch.");
			PhantomAssertions.assertTrue(backend.observe(backend._members.get(5L)).isEmpty() && backend.observe(backend._members.get(23L)).isEmpty() && backend.observe(backend._members.get(24L)).isEmpty(), "Unavailable/supply/nonseek controls joined the group.");
		}
		finally { coordinator.beginStop(); coordinator.finishStop(); }
	}
	private static PhantomGoal sourceRoot129(long id, long revision, PhantomDomainRef source)
	{
		final int split = source.key().indexOf('@');
		return new PhantomGoal(129000 + id, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", source.key().substring(0, split)), 1, 0, "background.farm", List.of(source), new PhantomDomainRef("topology.anchor", source.key().substring(split + 1)), "farm.background", 500, 0, 0, 0, Map.of(), "background.catchup.plan", revision);
	}

	private static void nativeHandoffPartyChain(PhantomTestContext context) throws Exception
	{
		final var states = new MemoryPartyStore(); final var goals = new MemoryGoalStore(); final var backend = new MemoryPartyBackend(); backend._farmEnabled = true;
		backend._farmLevel = 78;
		final var clock = new AtomicLong(1_000_000_000L);
		final var seeking = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings.defaults();
		final var ids = List.of(1L, 3L, 8L, 23L, 24L);
		PhantomAssertions.assertTrue(List.of(1L, 3L, 8L, 23L).stream().allMatch(id -> seeking.seeks(id, 78)) && !seeking.seeks(24, 78), "The mixed policy controls are not independent SEEK/SOLO profiles.");
		final var admissions = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.NativeRequestPresenceFixture>();
		backend._requiresTypedAdmission = true;
		final var coordinator = coordinator(context, states, goals, backend, 64, clock::get);
		backend.connect(coordinator);
		try
		{
			for (long id : ids)
			{
				final var admission = new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.NativeRequestPresenceFixture(context.moduleRoot(), id);
				admissions.add(admission); admission.prepare(0);
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, admission.materializeCurrent().outcome(), "The chain bypassed preparation capacity0.");
				admission.prepare(1); final var loaded = admission.materializeCurrent();
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.SUCCESS, loaded.outcome(), "Current calendar/request did not reach real Ecology/Presence admission.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest.Kind.NATIVE_CONTEXT_HANDOFF, loaded.request().kind(), "The exact pending history claim became NORMAL materialization.");
				// Model-only native load leaf, reached only after the actual typed control-plane gate.
				final var actor = backend.add(id, admission.characterObjectId());
				backend._typedAdmitted.add(id);
				final var template = member(id, actor.characterObjectId(), 100, 100, false, 0, List.of(id == 8 || id == 23 ? cap("combat.heal", "learned.heal", 450, 1011) : cap("combat.melee_damage", "weapon", 300, 0)));
				backend.snapshot(actor, new MemberSnapshot(actor, template.classId(), 0, ids.indexOf(id) * 15000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), template.capabilities(), ZERO));
				goals.put(id, supportTravelRoot(id, 0, id == 1 ? "farm.fixture" : "farm.origin." + id));
			}
			PhantomAssertions.assertTrue(coordinator.start(), "Typed handoff Party owner did not start.");
			final var arrivals = new HashSet<Long>();
			final var adapter = new PhantomAutonomousFarmParty(coordinator, backend, goals, currentCatalog(context), clock::get, (id, _) -> id == 23,
				(id, root) ->
				{
					if (!arrivals.contains(id)) { PhantomAssertions.assertEquals(0, backend.invitationCount(), "An invitation preceded the final bilateral arrival."); }
					arrivals.add(id); final var actor = backend._members.get(id); final var before = backend._snapshots.get(actor);
					// Model-only movement leaf; no native Geo/travel or gameplay outcome is claimed.
					backend.snapshot(actor, new MemberSnapshot(actor, before.classId(), 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), before.capabilities(), ZERO));
					return new org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalResult(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.ArrivalKind.ARRIVED, root.goalId(), root.revision(), actor.characterObjectId(), backend._nativeEpoch, 0, "fixture.arrived");
				}, seeking, (id, root, source, _, current) ->
				{
					PhantomAssertions.assertTrue(current.getAsBoolean(), "Shared-root CAS preceded bilateral consent.");
					PhantomAssertions.assertEquals(0, backend.invitationCount(), "Shared-root adoption followed an early invitation.");
					if (!root.validSources().contains(source)) { final var prior = goals.load(id).orElseThrow(); goals.replace(id, prior.rowVersion(), supportTravelRoot(id, root.revision() + 1, "farm.fixture")); }
					return true;
				}, System::currentTimeMillis);
			for (int pulse = 0; pulse < 6 && states._rendezvous.values().stream().noneMatch(value -> value.state().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ACCEPTED); pulse++)
			{ clock.addAndGet(6_000_000_000L); adapter.onPulse(); }
			final var accepted = states._rendezvous.values().stream().filter(value -> value.state().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.ACCEPTED).findFirst().orElseThrow();
			final long acceptedId = accepted.profileId(); final var root = goals.load(acceptedId).orElseThrow().goal();
			PhantomAssertions.assertEquals(Set.of(1L, 3L, 8L), accepted.state().roster().stream().map(value -> value.actor().profileId()).collect(java.util.stream.Collectors.toSet()), "SOLO or Supply-required control entered the offer.");
			PhantomAssertions.assertTrue(states._rendezvous.values().stream().anyMatch(value -> value.state().phase() == org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.PROPOSED), "The consent-to-consent lifetime gap was not exercised.");
			PhantomAssertions.assertFalse(lifetimeHeld(coordinator, states, goals, backend, acceptedId, supportTravelRoot(acceptedId, 2, "farm.fixture")), "A stale root retained a lifetime.");
			PhantomAssertions.assertFalse(coordinator.retainsNativeLifetime(acceptedId, root, 999, 1), "A foreign object retained a lifetime.");
			PhantomAssertions.assertFalse(coordinator.retainsNativeLifetime(acceptedId, root, backend._members.get(acceptedId).characterObjectId(), 2), "A foreign epoch retained a lifetime.");
			final var proof = new org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot(backend._members.get(acceptedId).characterObjectId(), 1, clock.get(), false, 1, 1, 1, 1, 1, 1, 1, 1, 0, clock.get(), org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.NONE, clock.get(), 0, clock.get(), 0, 0, org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.UnprovenReason.NONE, 0);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativeRetirementRequired(proof, proof.objectId(), proof.epoch(), false), "The counterexample lacks a completed current-epoch native farm cycle.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativePartyDecisionPrepared(false, lifetimePermission(coordinator, states, goals, backend, acceptedId, root, true)), "Accepted-only consent bypassed ordinary Solo preparation.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativeRetirementRequired(proof, proof.objectId(), proof.epoch(), lifetimeHeld(coordinator, states, goals, backend, acceptedId, root)), "RED129: current accepted consent must retain the native handoff while peers are still PROPOSED.");
			PhantomAssertions.assertTrue(coordinator.assemblyDeadline(acceptedId) > System.currentTimeMillis(), "Final own-actor soft cleanup has no finite veto.");
			final var prior = accepted.state(); final long now = System.currentTimeMillis();
			states._rendezvous.put(acceptedId, new PhantomPartyPersistencePort.StoredRendezvous(acceptedId, accepted.rowVersion() + 1, new org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState(acceptedId, prior.offerId(), prior.leaderProfileId(), prior.roster(), prior.source(), now - 120000, now - 1, prior.phase(), prior.adoptedRevision())));
			coordinator.rendezvous(acceptedId);
			PhantomAssertions.assertFalse(lifetimeHeld(coordinator, states, goals, backend, acceptedId, root), "Expired consent retained the handoff.");
			PhantomAssertions.assertEquals(0L, coordinator.assemblyDeadline(acceptedId), "Expired consent retained the final soft-cleanup veto.");
			states._rendezvous.put(acceptedId, accepted); coordinator.rendezvous(acceptedId);
			final long peerId = prior.roster().stream().map(value -> value.actor().profileId()).filter(id -> id != acceptedId).findFirst().orElseThrow(); final var peer = states._rendezvous.get(peerId);
			coordinator.saveRendezvous(peer.rowVersion(), peer.state().withPhase(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.CANCELLED, peer.state().adoptedRevision())).orElseThrow();
			PhantomAssertions.assertFalse(lifetimeHeld(coordinator, states, goals, backend, acceptedId, root), "Cancelled peer consent retained the handoff.");
			states._rendezvous.put(peerId, peer); coordinator.rendezvous(peerId);
			for (int pulse = 0; pulse < 30; pulse++) { clock.addAndGet(6_000_000_000L); adapter.onPulse(); for (int control = 0; control < 8; control++) { coordinator.onPulse(); } }
			PhantomAssertions.assertEquals(Set.of(1L, 3L, 8L), arrivals, "The exact consent roster did not reach every modeled arrival.");
			PhantomAssertions.assertEquals(2, backend.invitationCount(), "Canonical invitation dispatch was skipped or duplicated.");
			for (long id : List.of(1L, 3L, 8L))
			{
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState.Phase.COMPLETE, states._rendezvous.get(id).state().phase(), "Native roster did not complete consent.");
				PhantomAssertions.assertTrue(lifetimeHeld(coordinator, states, goals, backend, id, goals.load(id).orElseThrow().goal()), "COMPLETE failed to transfer lifetime to the current committed native Party.");
				final var currentRoot = goals.load(id).orElseThrow().goal();
				final boolean committed = lifetimePermission(coordinator, states, goals, backend, id, currentRoot, true);
				PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativePartyDecisionPrepared(false, committed), "Native Party still fell into the Solo suitability gate before ordinary Decision/Supply.");
				PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativePartyDecisionPrepared(true, committed), "Native decision retention bypassed bilateral shared-source preparation.");
			}
			clock.addAndGet(1_100_000_000L);
			PhantomAssertions.assertFalse(lifetimePermission(coordinator, states, goals, backend, 1, goals.load(1).orElseThrow().goal(), true), "Expired native observation granted ordinary decisions.");
			coordinator.onPulse();
			PhantomAssertions.assertTrue(lifetimePermission(coordinator, states, goals, backend, 1, goals.load(1).orElseThrow().goal(), true), "Stable native reconciliation did not republish its observation.");
			backend._nativeEpoch = 2;
			PhantomAssertions.assertFalse(lifetimeHeld(coordinator, states, goals, backend, 1, goals.load(1).orElseThrow().goal()), "A replaced lifetime borrowed the old native observation."); backend._nativeEpoch = 1;
			coordinator.beginStop(); PhantomAssertions.assertFalse(lifetimeHeld(coordinator, states, goals, backend, acceptedId, root), "STOPPING retained new lifetime authority.");
			PhantomAssertions.assertEquals(0L, coordinator.assemblyDeadline(acceptedId), "STOPPING retained the final soft-cleanup veto.");
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Initial Party chain did not drain."); backend._parties.clear(); backend._typedAdmitted.clear(); backend._nativeEpoch = 2;
			final var resumed = coordinator(context, states, goals, backend, 64, clock::get); backend.connect(resumed);
			new PhantomAutonomousFarmParty(resumed, backend, goals, currentCatalog(context), clock::get, (id, _) -> id == 23, null, seeking,
			    (id, goal, source, deadline, consent) -> { throw new AssertionError("Restart attempted fresh rendezvous adoption."); }, System::currentTimeMillis);
			resumed.start();
			try
			{
				for (long id : List.of(1L, 3L, 8L)) { resumed.rendezvous(id); PhantomAssertions.assertFalse(lifetimeHeld(resumed, states, goals, backend, id, goals.load(id).orElseThrow().goal()), "Old consent/RECOVERING claim granted an unconfirmed native lifetime."); }
				PhantomAssertions.assertFalse(backend.materialize(1), "The modeled NORMAL native leaf bypassed its pending-history fence.");
				resumed.onPulse(); PhantomAssertions.assertEquals(2, backend.invitationCount(), "Cold RECOVERING claims bypassed typed materialization before invitation.");
				for (int index = 0; index < admissions.size(); index++)
				{
					final var admission = admissions.get(index); final long id = ids.get(index); admission.expireRequest();
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, admission.materializeCurrent().outcome(), "Expired cache admitted the next native lifetime.");
					// The real RECOVERING claim drives the same production verdict as native signal publication and History.
					admission.bindNativeRequirement(() -> org.l2jmobius.gameserver.phantoms.background.PhantomVisibleItinerary068Checks.nativeOnlyRestartRequired(resumed.blocksBackground(id)));
					admission.prepare(1); final var loaded = admission.materializeCurrent();
					if (id == 23 || id == 24)
					{
						PhantomAssertions.assertFalse(admission.backgroundDemand(id) || admission.hasTypedDue(), "An unowned FARM-permitted control retained native demand or typed Due.");
						PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.DEFERRED, loaded.outcome(), "An unowned control reached the modeled native leaf.");
						PhantomAssertions.assertFalse(backend._typedAdmitted.contains(id), "A deferred control acquired a native epoch."); continue;
					}
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome.SUCCESS, loaded.outcome(), "Fresh request could not reuse its unchanged historical claim.");
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest.Kind.NATIVE_CONTEXT_HANDOFF, loaded.request().kind(), "Restart lost the typed materialization claim.");
					backend._typedAdmitted.add(id);
				}
				for (int pulse = 0; pulse < 24; pulse++) { resumed.onPulse(); }
				PhantomAssertions.assertEquals(3, backend.observe(backend._members.get(1L)).orElseThrow().members().size(), "Restart stranded the durable Phantom roster.");
				PhantomAssertions.assertEquals(4, backend.invitationCount(), "Restart replayed consent or bypassed the ordinary canonical invitation owner.");
				for (long id : List.of(1L, 3L, 8L)) { PhantomAssertions.assertTrue(lifetimeHeld(resumed, states, goals, backend, id, goals.load(id).orElseThrow().goal()), "Current recovered native Party lost lifetime retention."); }
			}
			finally { resumed.beginStop(); PhantomAssertions.assertTrue(resumed.finishStop(), "Recovered Party chain did not drain."); }
		}
		finally { coordinator.beginStop(); PhantomAssertions.assertTrue(coordinator.finishStop(), "Party chain did not drain."); for (var admission : admissions) { admission.close(); } }
	}

	private static boolean lifetimeHeld(PhantomPartyCoordinator coordinator, MemoryPartyStore states, MemoryGoalStore goals, MemoryPartyBackend backend, long profileId, PhantomGoal root)
	{
		return lifetimePermission(coordinator, states, goals, backend, profileId, root, false);
	}

	private static boolean lifetimePermission(PhantomPartyCoordinator coordinator, MemoryPartyStore states, MemoryGoalStore goals, MemoryPartyBackend backend, long profileId, PhantomGoal root, boolean committedDecision)
	{
		states._rejectRendezvousReads = true; goals._failNextLoad = true; backend._rejectCurrentMemberReads = true; backend._rejectLifetimeReads = true;
		try { return committedDecision ? coordinator.permitsCommittedNativeDecision(profileId, root, backend._members.get(profileId).characterObjectId(), backend._nativeEpoch) : coordinator.retainsNativeLifetime(profileId, root, backend._members.get(profileId).characterObjectId(), backend._nativeEpoch); }
		finally { states._rejectRendezvousReads = false; goals._failNextLoad = false; backend._rejectCurrentMemberReads = false; backend._rejectLifetimeReads = false; }
	}

	private static PhantomGoal historicalIdle(long profileId, long goalId, long revision)
	{
		final var owner = new PhantomDomainRef("profile", Long.toString(profileId));
		return new PhantomGoal(goalId, "idle.background.history", PhantomGoalStatus.ACTIVE, owner, owner, 1, 0, "background.idle", List.of(), new PhantomDomainRef("topology.anchor", "farm.fixture"), "background.history", 500, 0, 0, 0, Map.of(), "planner.target_or_route.absent", revision);
	}

	private static PhantomGoal goal(long goalId, String type, PhantomDomainRef target, long revision, PhantomGoalStatus status)
	{
		return new PhantomGoal(goalId, type, status, new PhantomDomainRef("profile", "1"), target, 1, 0, null, List.of(), null, "party.lifecycle", 500, 0, 0, 0, Map.of(), "party.lifecycle", revision);
	}

	private static PartyInvitation invitation(InvitationIdentity identity, MemberRef invitee)
	{
		return new PartyInvitation(identity, identity.requesterObjectId(), MemberRef.real(identity.requesterObjectId()).stableKey(), invitee.characterObjectId(), invitee.stableKey(), PartyDistributionType.FINDERS_KEEPERS, identity.requesterObjectId(), Long.MAX_VALUE);
	}

	private static PhantomGoal conversationJoinGoal(String planId, InvitationIdentity identity)
	{
		final Map<String, Long> constraints = new TreeMap<>();
		for (int index = 0; index < 4; index++)
		{
			constraints.put("conversation.plan." + index, Long.parseUnsignedLong(planId.substring(index * 16, (index + 1) * 16), 16));
		}
		constraints.put("party.invitation", identity.sequence());
		constraints.put("party.requester", (long) identity.requesterObjectId());
		constraints.put("party.invitee", (long) identity.inviteeObjectId());
		return new PhantomGoal(9001, PhantomPartyCoordinator.JOIN_GOAL, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("party", "general"), new PhantomDomainRef("character.object", Integer.toString(identity.requesterObjectId())), 1, 0, null, List.of(), null, "conversation.action", 600, 0, 0, 0, constraints, "conversation.party.accept", 0);
	}

	private enum RouteNavigationCase
	{
		SYNC_REJECTED,
		SYNC_NO_ROUTE,
		IMMEDIATE_SUCCESS,
		ASYNC_SUCCESS,
		ASYNC_NO_PATH,
		ASYNC_BACKEND_FAILURE
	}

	private static final class RouteClosureFixture implements AutoCloseable
	{
		private static final long GOAL_ID = 23002312L;
		private final MemoryPartyStore _states = new MemoryPartyStore();
		private final MemoryGoalStore _goals = new MemoryGoalStore();
		private final MemoryPartyBackend _backend = new MemoryPartyBackend();
		private final AtomicInteger _submissions = new AtomicInteger();
		private final AtomicInteger _directChecks = new AtomicInteger();
		private final AtomicLong _clock = new AtomicLong(1_000_000_000L);
		private Runnable _deferredWorker;
		private final PhantomNavigationService _navigation;
		private final PhantomPartyRouteCoordinator _routes;
		private final PhantomPartyCoordinator _coordinator;
		private final L2jPhantomRiftPartyPort _port;
		private final MemberRef _leader;
		private final PhantomDomainRef _objective = new PhantomDomainRef("rift.tier", "1");
		private final PhantomDomainRef _routeDestination = new PhantomDomainRef("rift.entry", "1");
		private final PhantomNavigationPoint _destination = new PhantomNavigationPoint(100, 0, 0, 0);

		private RouteClosureFixture(PhantomTestContext context, boolean immediate)
		{
			this(context, immediate ? RouteNavigationCase.IMMEDIATE_SUCCESS : RouteNavigationCase.ASYNC_SUCCESS);
		}

		private RouteClosureFixture(PhantomTestContext context, RouteNavigationCase navigationCase)
		{
			final PhantomNavigationBackend navigationBackend = new PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
				{
					_submissions.incrementAndGet();
					final PhantomNavigationCapability capability = switch (navigationCase)
					{
						case SYNC_NO_ROUTE -> PhantomNavigationCapability.NO_GEODATA;
						case IMMEDIATE_SUCCESS -> PhantomNavigationCapability.GEODATA_DIRECT_ONLY;
						default -> PhantomNavigationCapability.GEODATA_PATHFINDING;
					};
					return new CapabilitySnapshot(capability, 1);
				}
				@Override public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
				{
					final int check = _directChecks.incrementAndGet();
					return (navigationCase == RouteNavigationCase.IMMEDIATE_SUCCESS) || ((navigationCase == RouteNavigationCase.ASYNC_SUCCESS) && (check > 1));
				}
				@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
				{
					return switch (navigationCase)
					{
						case ASYNC_SUCCESS -> List.of(request.origin(), request.destination());
						case ASYNC_NO_PATH -> null;
						case ASYNC_BACKEND_FAILURE -> throw new IllegalStateException("Expected Goal 023C backend failure.");
						default -> throw new AssertionError("Synchronous route fixture invoked pathfinding.");
					};
				}
			};
			_navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), navigationBackend, worker ->
			{
				if (Set.of(RouteNavigationCase.ASYNC_SUCCESS, RouteNavigationCase.ASYNC_NO_PATH, RouteNavigationCase.ASYNC_BACKEND_FAILURE).contains(navigationCase))
				{
					_deferredWorker = worker;
				}
				else
				{
					worker.run();
				}
				return true;
			}, _clock::get, new PhantomMetrics());
			if (navigationCase != RouteNavigationCase.SYNC_REJECTED)
			{
				PhantomAssertions.assertTrue(_navigation.start(), "Route closure navigation did not start.");
			}
			_leader = _backend.add(1, 101);
			_backend.party(new PhantomPartyBackend.PartySnapshot(_leader, List.of(_leader), PartyDistributionType.FINDERS_KEEPERS));
			_goals.put(_leader.profileId(), goal(GOAL_ID, PhantomRiftService.GOAL_TYPE, _objective, 0));
			_routes = new PhantomPartyRouteCoordinator(_navigation, null);
			_coordinator = new PhantomPartyCoordinator(_states, _goals, _backend, currentCatalog(context), _routes, new PhantomPartyTactics(null, _backend), () -> ZERO, _clock::get, 64);
			PhantomAssertions.assertTrue(_coordinator.start(), "Route closure coordinator did not start.");
			_port = new L2jPhantomRiftPartyPort(_coordinator);
		}

		private ContentBindingResult bind()
		{
			return _coordinator.bindContentGoal(new ContentBindingRequest(_leader.profileId(), GOAL_ID, 0, PhantomRiftService.GOAL_TYPE, ObjectiveMode.AREA_PVE, _objective, List.of(), _leader, List.of(_leader), PartyDistributionType.FINDERS_KEEPERS, roster().evidenceHash()));
		}

		private org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyBinding portBinding()
		{
			return _port.bind(_leader.profileId(), GOAL_ID, 0, _objective, List.of(), roster());
		}

		private PhantomPartyCoordinator.RouteRequestResult request(PhantomDomainRef destination)
		{
			return _coordinator.requestRoute(_leader.profileId(), destination, _destination);
		}

		private void snapshot(int x, int y, int instanceId, boolean dead)
		{
			_backend.snapshot(_leader, new MemberSnapshot(_leader, 0, instanceId, x, y, 0, 100, 100, 100, dead, false, false, false, 0, List.of(), List.of(), ZERO));
		}

		private void pulse(int count)
		{
			for (int pulse = 0; pulse < count; pulse++)
			{
				_coordinator.onPulse();
			}
		}

		private void completeAsync()
		{
			PhantomAssertions.assertTrue(_deferredWorker != null, "Async route fixture has no accepted worker.");
			final Runnable worker = _deferredWorker;
			_deferredWorker = null;
			worker.run();
			pulse(2);
		}

		private void advanceClock(long nanos)
		{
			_clock.addAndGet(nanos);
		}

		private PartyState state()
		{
			return _coordinator.claim(_leader.profileId()).orElseThrow().state();
		}

		private CanonicalRoster roster()
		{
			return new CanonicalRoster(_leader, List.of(_leader), PartyDistributionType.FINDERS_KEEPERS, false, false, PhantomPartyModel.sha256("rift023b.route.roster|" + _leader.stableKey()));
		}

		private PhantomPartyCoordinator coordinator() { return _coordinator; }
		private L2jPhantomRiftPartyPort port() { return _port; }
		private MemberRef leader() { return _leader; }
		private PhantomDomainRef objective() { return _objective; }
		private PhantomDomainRef routeDestination() { return _routeDestination; }
		private PhantomNavigationPoint destination() { return _destination; }
		private int submissions() { return _submissions.get(); }
		private PhantomPartyRouteCoordinator.Snapshot routeSnapshot() { return _routes.snapshot(); }
		private PhantomNavigationService.ServiceSnapshot navigationSnapshot() { return _navigation.snapshot(); }

		@Override
		public void close()
		{
			_coordinator.beginStop();
			_coordinator.finishStop();
			if (_deferredWorker != null)
			{
				_deferredWorker.run();
				_deferredWorker = null;
			}
			_navigation.beginStop();
			PhantomAssertions.assertTrue(_navigation.finishStop(), "Route closure navigation did not drain.");
		}
	}

	private static final class MemoryPartyStore implements PhantomPartyPersistencePort	{
		private final PhantomWorldCoordination130Suite.MemoryStore _logical = new PhantomWorldCoordination130Suite.MemoryStore();
		@Override public Optional<StoredLogicalGroup> loadLogicalGroup(long leader) { return _logical.loadLogicalGroup(leader); }
		@Override public Optional<StoredLogicalClaim> loadLogicalClaim(long id) { return _logical.loadLogicalClaim(id); }
		@Override public List<StoredLogicalGroup> loadLogicalGroupsAfter(long cursor, int limit) { return _logical.loadLogicalGroupsAfter(cursor, limit); }
		@Override public StoredLogicalGroup commitLogicalGroup(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty group, long expected, Map<Long, Long> versions) { return _logical.commitLogicalGroup(group, expected, versions); }
		@Override public StoredLogicalGroup updateLogicalGroup(org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty group, long expected) { return _logical.updateLogicalGroup(group, expected); }
		@Override public StoredLogicalGroup reviseLogicalGroup(StoredLogicalGroup expected, org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty replacement) { return _logical.reviseLogicalGroup(expected, replacement); }
		private boolean _rejectRendezvousReads;
		private final Map<Long, StoredRendezvous> _rendezvous = new LinkedHashMap<>();
		@Override public Optional<StoredRendezvous> loadRendezvous(long profileId) { if (_rejectRendezvousReads) { throw new AssertionError("Memory-only support permission read persistence."); } return Optional.ofNullable(_rendezvous.get(profileId)); }
		@Override public StoredRendezvous saveRendezvous(long profileId, long expectedRowVersion, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRendezvousState state)
		{
			final var prior = _rendezvous.get(profileId);
			if (prior == null ? expectedRowVersion != -1 : prior.rowVersion() != expectedRowVersion) { throw new IllegalStateException("Injected rendezvous CAS conflict."); }
			final var saved = new StoredRendezvous(profileId, prior == null ? 0 : prior.rowVersion() + 1, state); _rendezvous.put(profileId, saved); return saved;
		}
		private final TreeMap<Long, StoredPartyState> _states = new TreeMap<>();
		private final Map<Long, StoredRegroupClock> _clocks = new LinkedHashMap<>();
		private java.util.function.Consumer<StoredPartyState> _afterSave;

		private void seed(long profileId, PartyState state)
		{
			_states.put(profileId, new StoredPartyState(profileId, 0, state));
		}

		@Override
		public Optional<StoredPartyState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public StoredPartyState save(long profileId, long expectedRowVersion, PartyState state)
		{
			final StoredPartyState current = _states.get(profileId);
			if (((current == null) && (expectedRowVersion >= 0)) || ((current != null) && (current.rowVersion() != expectedRowVersion)))
			{
				throw new IllegalStateException("Injected optimistic conflict.");
			}
			final StoredPartyState stored = new StoredPartyState(profileId, current == null ? 0 : current.rowVersion() + 1, state);
			_states.put(profileId, stored);
			if (_afterSave != null) { _afterSave.accept(stored); }
			return stored;
		}

		@Override
		public List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize)
		{
			return _states.tailMap(exclusiveProfileId, false).values().stream().limit(pageSize).toList();
		}

		@Override public Optional<StoredRegroupClock> loadRegroupClock(long profileId) { return Optional.ofNullable(_clocks.get(profileId)); }
		@Override public StoredRegroupClock saveRegroupClock(long profileId, long expectedRowVersion, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRegroupState state)
		{
			final var prior = _clocks.get(profileId);
			if (prior == null ? expectedRowVersion >= 0 : prior.rowVersion() != expectedRowVersion) { throw new IllegalStateException("Injected clock CAS conflict."); }
			final var saved = new StoredRegroupClock(profileId, prior == null ? 0 : prior.rowVersion() + 1, state); _clocks.put(profileId, saved); return saved;
		}
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> _goals = new LinkedHashMap<>();
		private int _loads;
		private boolean _failNextLoad;

		private void put(long profileId, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			_goals.put(profileId, new StoredGoal(goal, current == null ? 0 : current.rowVersion() + 1));
		}

		private void remove(long profileId)
		{
			_goals.remove(profileId);
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId > 0;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			_loads++;
			if (_failNextLoad) { _failNextLoad = false; throw new IllegalStateException("TEST_GOAL_READ"); }
			return Optional.ofNullable(_goals.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (_goals.containsKey(profileId))
			{
				throw new IllegalStateException("Goal exists.");
			}
			put(profileId, goal);
			return _goals.get(profileId);
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Goal conflict.");
			}
			put(profileId, goal);
			return _goals.get(profileId);
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Goal conflict.");
			}
			_goals.remove(profileId);
		}
	}

	private static PhantomCombatService cooperativeCombat(PartyActionLease lease, AtomicLong clock)
	{
		return cooperativeCombat(lease, clock, (_, _) -> PhantomCombatService.DispatchResult.rejected());
	}

	private static PhantomCombatService cooperativeCombat(PartyActionLease lease, AtomicLong clock, PhantomCombatService.Dispatcher dispatcher)
	{
		final PhantomCombatService combat = new PhantomCombatService(profileId ->
		{
			PhantomAssertions.assertEquals(1L, profileId, "A human acquired a Phantom action lease.");
			return lease;
		}, new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(4), new PhantomCombatMetrics(), clock::get, dispatcher);
		combat.start();
		return combat;
	}

	private static void cooperativeFollow()
	{
		cooperativeFollow(false);
	}

	private static void cooperativeFollow(boolean phantom)
	{
		for (boolean busy : List.of(true, false))
		{
			final AtomicLong clock = new AtomicLong(1);
			final AtomicBoolean direct = new AtomicBoolean(false);
			final List<Runnable> workers = new java.util.ArrayList<>();
			final PhantomNavigationBackend backend = new PhantomNavigationBackend()
			{
				@Override public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination) { return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1); }
				@Override public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination) { return direct.get(); }
				@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token) { return List.of(); }
			};
			final PhantomNavigationService navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, worker -> { workers.add(worker); return true; }, clock::get, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "Cooperative navigation did not start.");
			final PartyActionLease lease = new PartyActionLease();
			final PhantomCombatService combat = cooperativeCombat(lease, clock);
			final PhantomPartyRouteCoordinator routes = new PhantomPartyRouteCoordinator(navigation, combat);
			final MemberRef actor = MemberRef.phantom(1, 101), human = phantom ? MemberRef.phantom(2, 201) : MemberRef.real(201);
			final MemberSnapshot actorState = new MemberSnapshot(actor, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			final MemberSnapshot leaderState = new MemberSnapshot(human, 0, 0, 1000, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			final String group = PhantomPartyModel.sha256("cooperative.follow076");
			try
			{
				final var occupied = busy ? navigation.submit(new PhantomNavigationRequest(1, new PhantomNavigationPoint(0, 0, 0, 0), new PhantomNavigationPoint(500, 0, 0, 0), clock.get(), 120_000_000_001L, 100000)) : null;
				routes.follow(group, 1, actorState, leaderState, ZERO, clock.get(), 8, () -> false);
				if (!busy)
				{
					workers.forEach(Runnable::run);
					workers.clear();
					routes.follow(group, 1, actorState, leaderState, ZERO, clock.get(), 8, () -> false);
				}
				clock.set(31_000_000_001L);
				routes.follow(group, 1, actorState, leaderState, ZERO, clock.get(), 8, () -> false);
				if (busy)
				{
					navigation.cancel(1, occupied.requestId());
					workers.forEach(Runnable::run);
					workers.clear();
				}
				direct.set(true);
				for (int pulse = 0; pulse < 3; pulse++)
				{
					final MemberSnapshot currentActor = new MemberSnapshot(actor, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, lease.moving, 0, List.of(), List.of(), ZERO);
					routes.follow(group, 1, currentActor, leaderState, ZERO, clock.incrementAndGet(), 8, () -> false);
				}
				PhantomAssertions.assertEquals(busy ? 1 : 0, lease.moves, "Transient capacity became permanent or geometric rejection retried without changed evidence.");
				if (busy)
				{
					PhantomAssertions.assertTrue(combat.hasClaim(1), "Resumed FOLLOW did not retain movement ownership.");
					routes.cancel(group, 2);
					PhantomAssertions.assertTrue(combat.hasClaim(1), "Foreign generation cancelled FOLLOW.");
					routes.cancelMemberMovement(group, 1, 1);
					PhantomAssertions.assertFalse(combat.hasClaim(1), "HOLD/leave cancellation retained FOLLOW ownership.");
					PhantomAssertions.assertEquals(1, lease.cancels, "Movement did not cancel exactly once.");
				}
			}
			finally
			{
				routes.beginStop();
				navigation.beginStop();
				workers.forEach(Runnable::run);
				PhantomAssertions.assertTrue(navigation.finishStop(), "Cooperative navigation did not drain.");
				combat.beginStop();
				PhantomAssertions.assertTrue(combat.finishStop(), "Cooperative movement owner did not drain.");
			}
		}
	}

	private static void cooperativeSupport(PhantomTestContext context)
	{
		for (String change : List.of("roster", "leader", "leave", "death", "session", "instance", "stop"))
		{
			final AtomicLong clock = new AtomicLong(1);
			final PartyActionLease lease = new PartyActionLease();
			final PhantomCombatService combat = cooperativeCombat(lease, clock);
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef actor = backend.add(1, 101), human = MemberRef.real(201), other = MemberRef.real(202);
			backend.snapshot(human, new MemberSnapshot(human, 0, 0, 0, 0, 0, 20, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
			backend._capabilities.put(actor, List.of(cap("combat.heal", "heal", 50, 1001)));
			final PhantomPartyCoordinator coordinator = new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, combat), new PhantomPartyTactics(combat, backend), () -> ZERO, clock::get, 64);
			PhantomAssertions.assertTrue(coordinator.start(), "Cooperative support coordinator did not start.");
			try
			{
				backend.party(new PhantomPartyBackend.PartySnapshot(human, List.of(human, other, actor), PartyDistributionType.FINDERS_KEEPERS));
				coordinator.terminal(invitation(new InvitationIdentity(760076, 201, 101), actor), OptionalLong.empty(), OptionalLong.of(1), TerminalOutcome.ACCEPTED, "");
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertEquals(1, lease.casts, "Current human-led support was missing or duplicated during CAST.");
				PhantomAssertions.assertTrue(combat.hasClaim(1), "Pending support lost the shared actor claim.");
				PhantomAssertions.assertEquals(201, lease.support.targetObjectId(), "Healer did not support the actual human member.");
				backend._capabilities.clear();
				switch (change)
				{
					case "roster" -> backend.party(new PhantomPartyBackend.PartySnapshot(human, List.of(human, actor), PartyDistributionType.FINDERS_KEEPERS));
					case "leader" -> backend.transferLeader(human, other);
					case "leave" -> backend.leave(actor);
					case "death" -> backend.snapshot(actor, new MemberSnapshot(actor, 0, 0, 0, 0, 0, 0, 100, 100, true, false, false, false, 0, List.of(), List.of(), ZERO));
					case "session" -> backend.add(1, 102);
					case "instance" -> backend.snapshot(human, new MemberSnapshot(human, 0, 1, 0, 0, 0, 20, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
					case "stop" -> coordinator.beginStop();
				}
				for (int pulse = 0; pulse < 4; pulse++) { coordinator.onPulse(); }
				PhantomAssertions.assertFalse(combat.hasClaim(1), "Pending support retained stale authority after " + change);
				PhantomAssertions.assertEquals(1, lease.cancels, "Support cancellation was not exact after " + change);
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Cooperative support coordinator did not drain.");
				combat.beginStop();
				PhantomAssertions.assertTrue(combat.finishStop(), "Cooperative support owner did not drain.");
			}
		}
	}

	/** Only the native boundary is substituted; Party, routes, tactics and action ownership are production. */
	private static final class PartyActionLease implements PhantomCombatActorLease
	{
		private int objectId = 101;
		@Override public boolean partyWalkSafe(int x, int y, int z, int instanceId) { return true; }
		private int moves, casts, cancels;
		private boolean moving, casting;
		private PhantomPartySupportAction support;
		private TargetSnapshot target;
		@Override public ActorSnapshot actorSnapshot() { return new ActorSnapshot(objectId, 0, 0, 100, 100, 100, 100, 100, 100, false, false, false, casting, moving, 0, casting ? "CAST" : moving ? "MOVE_TO" : "IDLE", 0, 0); }
		@Override public TargetSnapshot targetSnapshot(int objectId) { return target != null && target.objectId() == objectId ? target : null; }
		@Override public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode) { return false; }
		@Override public List<ThreatObservation> observedAttackers(int limit) { return List.of(); }
		@Override public List<LootCandidate> lootCandidates(int limit, int distance) { return List.of(); }
		@Override public LootObservation observeLoot(LootCandidate candidate) { return LootObservation.PENDING; }
		@Override public ShotOutcome activateShot(PhantomCombatMode mode) { return ShotOutcome.UNAVAILABLE; }
		@Override public ActionOutcome attack(int objectId) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome cast(int objectId, SelectedSkill skill, PhantomCombatMode mode) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome castSupport(PhantomPartySupportAction action) { support = action; casts++; casting = true; return ActionOutcome.ISSUED; }
		@Override public ActionOutcome moveTo(int x, int y, int z, int instanceId) { moves++; moving = true; return ActionOutcome.ISSUED; }
		@Override public ActionOutcome pickUp(int objectId) { return ActionOutcome.REJECTED; }
		@Override public void cancelOwnedAction(PhantomOwnedAction action) { }
		@Override public void cancelExternalAction(ExternalOwnedAction action) { cancels++; casting = false; moving = false; }
		@Override public RespawnOutcome respawnTown() { return RespawnOutcome.REJECTED; }
		@Override public void close() { }
	}

	private static final class MemoryPartyBackend implements PhantomPartyBackend
	{
		private long _nativeEpoch = 1;
		private final Map<Long, Long> _nativeEpochs = new LinkedHashMap<>();
		private final Set<Long> _restartAbsent = new HashSet<>();
		@Override
		public boolean withUnmaterializedNativeAbsence(MemberRef actor, java.util.function.BooleanSupplier reconcile)
		{
			if (!_restartAbsent.contains(actor.profileId()) || _parties.values().stream().anyMatch(party -> party.members().contains(actor))) { return false; }
			try (var lease = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(actor.characterObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
			{
				return lease != null && reconcile.getAsBoolean();
			}
		}
		private boolean _rejectCurrentMemberReads;
		private boolean _rejectLifetimeReads;
		private boolean _requiresTypedAdmission;
		private final Set<Long> _typedAdmitted = new HashSet<>();
		private boolean _farmEnabled;
		private int _farmLevel = 50;
		private java.util.OptionalInt _farmTarget = java.util.OptionalInt.empty();
		private java.util.function.Consumer<MemberRef> _afterSnapshot;
		private boolean _observeFarmAbsence;
		private Runnable _afterAbsence;
		private final Set<Long> _unavailable = new java.util.HashSet<>();
		private final Set<Long> _busy = new java.util.HashSet<>();
		private final Map<Long, MemberRef> _members = new LinkedHashMap<>();
		private final Map<MemberRef, PartySnapshot> _parties = new LinkedHashMap<>();
		private final Map<MemberRef, MemberSnapshot> _snapshots = new LinkedHashMap<>();
		private final Map<MemberRef, List<MemberCapability>> _capabilities = new LinkedHashMap<>();
		private final Map<MemberRef, List<MemberCapability>> _farmHealCapabilities = new LinkedHashMap<>();
		private final Set<Integer> _protectiveSkills = new java.util.HashSet<>();
		private final Set<String> _healthyMaintenance = new java.util.HashSet<>();
		private PhantomPartyCoordinator _coordinator;
		private PartyInvitation _lastInvitation;
		private InvitationIdentity _nativePendingIdentity;
		private PartyInvitationService.Response _lastResponse;
		private long _invitationSequence;
		private int _invitationCount;
		private int _responseCount;

		private void connect(PhantomPartyCoordinator coordinator)
		{
			_coordinator = coordinator;
		}

		private PartyInvitation lastInvitation()
		{
			return _lastInvitation;
		}

		private int invitationCount()
		{
			return _invitationCount;
		}

		private PartyInvitationService.Response lastResponse()
		{
			return _lastResponse;
		}

		private int responseCount()
		{
			return _responseCount;
		}

		private MemberRef add(long profileId, int objectId)
		{
			final MemberRef member = MemberRef.phantom(profileId, objectId);
			_members.put(profileId, member);
			return member;
		}

		private void snapshot(MemberRef member, MemberSnapshot snapshot)
		{
			_snapshots.put(member, snapshot);
		}

		private void party(PartySnapshot snapshot)
		{
			for (MemberRef member : snapshot.members())
			{
				_parties.put(member, snapshot);
			}
		}

		@Override
		public OptionalLong managedProfileId(int characterObjectId)
		{
			return _members.values().stream().filter(member -> member.characterObjectId() == characterObjectId).mapToLong(MemberRef::profileId).findFirst();
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			if (_rejectCurrentMemberReads) { throw new AssertionError("Support predicate called JDBC-backed currentMember."); }
			return Optional.ofNullable(_members.get(profileId));
		}

		@Override
		public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution)
		{
			if (_coordinator == null)
			{
				throw new AssertionError("Bounded lifecycle fixture must not fabricate an invitation.");
			}
			if ((_lastInvitation != null) && _lastInvitation.identity().equals(_nativePendingIdentity) && (_lastInvitation.requesterObjectId() == requester.characterObjectId()) && (_lastInvitation.inviteeObjectId() == target.characterObjectId()))
			{
				return new InviteResult(InviteOutcome.DELIVERED_MANAGED, _lastInvitation.identity());
			}
			final InvitationIdentity identity = new InvitationIdentity(++_invitationSequence, requester.characterObjectId(), target.characterObjectId());
			final PartyInvitation invitation = new PartyInvitation(identity, requester.characterObjectId(), requester.stableKey(), target.characterObjectId(), target.stableKey(), distribution, requester.characterObjectId(), Long.MAX_VALUE);
			final OptionalLong managedRequester = requester.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind.PHANTOM ? OptionalLong.of(requester.profileId()) : OptionalLong.empty();
			final OptionalLong managedInvitee = target.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind.PHANTOM ? OptionalLong.of(target.profileId()) : OptionalLong.empty();
			if (_coordinator.prepare(invitation, managedRequester, managedInvitee) != PreparationOutcome.ACCEPTED)
			{
				return new InviteResult(InviteOutcome.MANAGED_BACKPRESSURE, identity);
			}
			_lastInvitation = invitation;
			_nativePendingIdentity = identity;
			_invitationCount++;
			if (_farmEnabled) { _coordinator.deliver(invitation, target.profileId()); }
			return new InviteResult(InviteOutcome.DELIVERED_MANAGED, identity);
		}

		@Override
		public PartyInvitationService.RespondResult respond(MemberRef invitee, PartyInvitationService.Response response, PartyInvitationService.InvitationIdentity identity)
		{
			_responseCount++;
			_nativePendingIdentity = null;
			_lastResponse = response;
			if (_farmEnabled && response == PartyInvitationService.Response.ACCEPT && _lastInvitation != null && _lastInvitation.identity().equals(identity))
			{
				final var requester = _members.values().stream().filter(member -> member.characterObjectId() == identity.requesterObjectId()).findFirst().orElseThrow();
				final var current = _parties.get(requester);
				final var members = new java.util.ArrayList<MemberRef>(current == null ? List.of(requester) : current.members());
				if (!members.contains(invitee)) { members.add(invitee); }
				party(new PartySnapshot(requester, members, PartyDistributionType.FINDERS_KEEPERS));
				_coordinator.terminal(_lastInvitation, OptionalLong.of(requester.profileId()), OptionalLong.of(invitee.profileId()), TerminalOutcome.ACCEPTED, "party.invite.accepted");
			}
			else if (_farmEnabled && response == PartyInvitationService.Response.REFUSE && _lastInvitation != null && _lastInvitation.identity().equals(identity))
			{
				final var requester = _members.values().stream().filter(member -> member.characterObjectId() == identity.requesterObjectId()).findFirst().orElseThrow();
				_coordinator.terminal(_lastInvitation, OptionalLong.of(requester.profileId()), OptionalLong.of(invitee.profileId()), TerminalOutcome.REFUSED, "party.invite.refused");
			}
			final PartyInvitationService.RespondOutcome outcome = response == PartyInvitationService.Response.ACCEPT ? PartyInvitationService.RespondOutcome.ACCEPTED : response == PartyInvitationService.Response.REFUSE ? PartyInvitationService.RespondOutcome.REFUSED : PartyInvitationService.RespondOutcome.DISABLED;
			return new PartyInvitationService.RespondResult(outcome, identity, null);
		}

		@Override
		public PartyInvitationService.MembershipOutcome leave(MemberRef member)
		{
			final PartySnapshot current = _parties.remove(member);
			if (current == null)
			{
				return PartyInvitationService.MembershipOutcome.NOT_IN_PARTY;
			}
			final List<MemberRef> remaining = current.members().stream().filter(candidate -> !candidate.equals(member)).toList();
			if (!remaining.isEmpty())
			{
				party(new PartySnapshot(current.leader().equals(member) ? remaining.getFirst() : current.leader(), remaining, current.distribution()));
			}
			return PartyInvitationService.MembershipOutcome.COMPLETED;
		}

		@Override
		public PartyInvitationService.MembershipOutcome expel(MemberRef requester, MemberRef member)
		{
			return leave(member);
		}

		@Override
		public PartyInvitationService.MembershipOutcome transferLeader(MemberRef requester, MemberRef member)
		{
			final PartySnapshot current = _parties.get(requester);
			if ((current == null) || !current.leader().equals(requester) || !current.members().contains(member))
			{
				return PartyInvitationService.MembershipOutcome.NOT_LEADER;
			}
			party(new PartySnapshot(member, current.members(), current.distribution()));
			return PartyInvitationService.MembershipOutcome.COMPLETED;
		}

		@Override
		public Optional<PartySnapshot> observe(MemberRef member)
		{
			if (_rejectLifetimeReads) { throw new AssertionError("Lifetime retention performed native/repository observation."); }
			return Optional.ofNullable(_parties.get(member));
		}

		@Override
		public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
		{
			if (_rejectLifetimeReads) { throw new AssertionError("Lifetime retention performed capability/role observation."); }
			if (_requiresTypedAdmission && !_typedAdmitted.contains(member.profileId())) { return Optional.empty(); }
			if (_afterSnapshot != null) { _afterSnapshot.accept(member); }
			return Optional.ofNullable(_snapshots.getOrDefault(member, new MemberSnapshot(member, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO)));
		}

		@Override
		public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId)
		{
			return _capabilities.getOrDefault(actor, List.of());
		}

		@Override
		public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId, boolean exactFarmHeal)
		{
			return exactFarmHeal && _farmHealCapabilities.containsKey(actor) ? _farmHealCapabilities.get(actor) : capabilities(actor, exactTargetObjectId);
		}

		@Override public boolean protectiveMaintenance(MemberRef actor, MemberCapability capability) { return _protectiveSkills.contains(capability.actionSkillId()); }
		@Override public org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status supportEffectStatus(MemberRef actor, int target, MemberCapability capability, int remaining)
		{
			return _healthyMaintenance.contains(target + ":" + capability.actionSkillId()) ? org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status.HEALTHY : org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status.MISSING;
		}

		@Override
		public boolean materialize(long profileId)
		{
			return _members.containsKey(profileId) && (!_requiresTypedAdmission || _typedAdmitted.contains(profileId));
		}

		@Override public java.util.OptionalInt currentLevel(MemberRef member) { return _farmEnabled ? java.util.OptionalInt.of(_farmLevel) : java.util.OptionalInt.empty(); }
		@Override public long materializationEpoch(MemberRef member) { return _members.containsValue(member) && !_restartAbsent.contains(member.profileId()) && (!_requiresTypedAdmission || _typedAdmitted.contains(member.profileId())) ? _nativeEpochs.getOrDefault(member.profileId(), _nativeEpoch) : 0; }
		@Override public CurrentForceObservation currentForce(MemberRef member)
		{
			final boolean absent = _observeFarmAbsence && _members.containsValue(member) && !_unavailable.contains(member.profileId()) && !_parties.containsKey(member);
			if (absent && _afterAbsence != null) { final var action = _afterAbsence; _afterAbsence = null; action.run(); }
			return absent ? CurrentForceObservation.partyAbsent() : CurrentForceObservation.unavailable("test.native_unavailable");
		}
		@Override public boolean autonomousFarmAvailable(MemberRef member) { return _farmEnabled && !_unavailable.contains(member.profileId()) && !_busy.contains(member.profileId()) && !ownPending(member); }
		private boolean ownPending(MemberRef member) { return _nativePendingIdentity != null && (_nativePendingIdentity.requesterObjectId() == member.characterObjectId() || _nativePendingIdentity.inviteeObjectId() == member.characterObjectId()); }
		@Override public boolean autonomousFarmConsentAvailable(MemberRef requester, MemberRef invitee, InvitationIdentity identity)
		{
			return _farmEnabled && _lastInvitation != null && _lastInvitation.identity().equals(identity) && identity.requesterObjectId() == requester.characterObjectId() && identity.inviteeObjectId() == invitee.characterObjectId()
				&& !_unavailable.contains(requester.profileId()) && !_unavailable.contains(invitee.profileId()) && !_busy.contains(requester.profileId()) && !_busy.contains(invitee.profileId());
		}
		@Override public boolean autonomousFarmReady(PartySnapshot party) { return _farmEnabled && party.members().stream().allMatch(_snapshots::containsKey) && party.members().stream().map(_snapshots::get).anyMatch(PhantomAutonomousFarmParty::damageDealer); }
		@Override public java.util.OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers) { return _farmTarget; }
		@Override public FarmPage materializedFarmMembers(long cursor, int limit)
		{
			if (!_farmEnabled) { return new FarmPage(List.of(), cursor); }
			final var examined = _members.values().stream().filter(member -> member.profileId() > cursor).sorted(java.util.Comparator.comparingLong(MemberRef::profileId)).limit(limit).toList();
			final var observed = examined.stream().filter(member -> !_unavailable.contains(member.profileId())).map(_snapshots::get).filter(java.util.Objects::nonNull).toList();
			return new FarmPage(observed, examined.isEmpty() ? cursor : examined.getLast().profileId());
		}
	}
}
