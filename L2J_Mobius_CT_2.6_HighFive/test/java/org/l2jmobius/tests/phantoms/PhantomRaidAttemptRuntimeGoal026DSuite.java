/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
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
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.L2jPhantomRaidAttemptRuntime;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.EngagementContext;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.MechanicContext;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RuntimeStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile.EntryKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidTargetEvidence;

public final class PhantomRaidAttemptRuntimeGoal026DSuite implements PhantomTestSuite
{
	private static final long SEED = 26002653L;
	private static final String AUTHORITY = "D6".repeat(32);

	@Override
	public String id()
	{
		return "raid-attempt-runtime-goal026d";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Raid Attempt Runtime Goal026D used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-multi-party-support-uses-exact-party-local-snapshots", _ -> partyLocalSnapshots());
		registry.add("02-required-tank-alive-ignores-ready-now", _ -> tankViability());
		registry.add("03-real-required-provider-counts-without-control", _ -> realProvider());
		registry.add("04-stable-phantom-support-reservation-excludes-offense", _ -> supportReservation());
	}

	private static void partyLocalSnapshots()
	{
		final RuntimeFixture fixture = new RuntimeFixture(true);
		try
		{
			final List<MemberRef> first = phantoms(1, 6);
			final List<MemberRef> second = phantoms(7, 12);
			final Map<MemberRef, MemberSnapshot> snapshots = snapshots(first, second);
			snapshots.put(first.getFirst(), member(first.getFirst(), false, 100, List.of(capability("combat.tank", true)), List.of()));
			final CurrentForceSnapshot force = force(List.of(first, second), snapshots);
			final RuntimeStatus status = fixture.runtime.advanceMechanic(fixture.mechanicContext(scriptedProfile(List.of(requirement("combat.tank")))), force).status();
			PhantomAssertions.assertEquals(RuntimeStatus.TARGET_REVEALED, status, "Two-Party runtime failed despite each exact Party staying within nine members.");
			assertPartyQueries(fixture.party, first);
			assertPartyQueries(fixture.party, second);
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void tankViability()
	{
		final RuntimeFixture fixture = new RuntimeFixture(true);
		try
		{
			final List<MemberRef> roster = phantoms(1, 9);
			final Map<MemberRef, MemberSnapshot> deadTank = snapshots(roster);
			deadTank.put(roster.getFirst(), member(roster.getFirst(), true, 0, List.of(capability("combat.tank", true)), List.of()));
			final CurrentForceSnapshot unavailable = force(List.of(roster), deadTank);
			PhantomAssertions.assertEquals(8L, unavailable.members().stream().filter(member -> !member.dead()).count(), "Tank-loss fixture dropped below encounter minimum.");
			PhantomAssertions.assertEquals(RuntimeStatus.PROVIDER_UNAVAILABLE, fixture.runtime.advanceMechanic(fixture.mechanicContext(scriptedProfile(List.of(requirement("combat.tank")))), unavailable).status(), "Dead last required tank did not fail hard viability.");

			final Map<MemberRef, MemberSnapshot> cooldownTank = snapshots(roster);
			cooldownTank.put(roster.getFirst(), member(roster.getFirst(), false, 100, List.of(capability("combat.tank", false)), List.of()));
			PhantomAssertions.assertEquals(RuntimeStatus.TARGET_REVEALED, fixture.runtime.advanceMechanic(fixture.mechanicContext(scriptedProfile(List.of(requirement("combat.tank")))), force(List.of(roster), cooldownTank)).status(), "readyNow=false alone failed attempt-time viability.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void realProvider()
	{
		final RuntimeFixture fixture = new RuntimeFixture(true);
		try
		{
			final List<MemberRef> roster = new ArrayList<>(phantoms(1, 8));
			final MemberRef real = MemberRef.real(2000);
			roster.add(real);
			final Map<MemberRef, MemberSnapshot> snapshots = snapshots(roster);
			snapshots.put(real, member(real, false, 100, List.of(capability("combat.tank", false)), List.of()));
			final RuntimeStatus status = fixture.runtime.advanceMechanic(fixture.mechanicContext(scriptedProfile(List.of(requirement("combat.tank")))), force(List.of(roster), snapshots)).status();
			PhantomAssertions.assertEquals(RuntimeStatus.TARGET_REVEALED, status, "Exact alive REAL provider did not preserve hard viability.");
			PhantomAssertions.assertFalse(fixture.party.queriedActors.contains(real), "PartyTactics attempted to control the REAL provider.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void supportReservation()
	{
		final RuntimeFixture fixture = new RuntimeFixture(false);
		try
		{
			final List<MemberRef> roster = phantoms(1, 4);
			final MemberCapability heal = capability("combat.heal", true);
			final MemberCapability offense = capability(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), true);
			final Map<MemberRef, MemberSnapshot> snapshots = new LinkedHashMap<>();
			snapshots.put(roster.get(0), member(roster.get(0), false, 100, List.of(heal, offense), List.of()));
			snapshots.put(roster.get(1), member(roster.get(1), false, 100, List.of(heal, offense), List.of()));
			snapshots.put(roster.get(2), member(roster.get(2), false, 100, List.of(offense), List.of()));
			snapshots.put(roster.get(3), member(roster.get(3), false, 40, List.of(offense), List.of()));
			final PhantomRaidEncounterProfile profile = openProfile(List.of(requirement("combat.heal")));
			final PhantomRaidTargetEvidence target = new PhantomRaidTargetEvidence(ContentKind.RAID, NpcKind.RAID_BOSS, 7001, profile.npcId(), 0, false);
			final RuntimeStatus status = fixture.runtime.advanceEngagement(new EngagementContext(AUTHORITY, profile, target, 1000, 10_000, () -> false), force(List.of(roster.reversed()), snapshots)).status();
			PhantomAssertions.assertEquals(RuntimeStatus.INTERMEDIATE, status, "Support-reservation fixture did not start engagement.");
			PhantomAssertions.assertTrue(fixture.lease(1).supportCasts > 0, "Stable first PHANTOM support provider was not reserved.");
			PhantomAssertions.assertEquals(0, fixture.lease(2).supportCasts, "Support reservation selected more than the deterministic minimum subset.");
			PhantomAssertions.assertEquals(0, fixture.lease(1).raidTargetReads, "Reserved support provider entered raid offense.");
			PhantomAssertions.assertTrue(fixture.lease(2).raidTargetReads > 0, "Unreserved PHANTOM support candidate was incorrectly excluded from offense.");
		}
		finally
		{
			fixture.stop();
		}
	}

	private static void assertPartyQueries(RecordingPartyBackend backend, List<MemberRef> roster)
	{
		final Set<Integer> exactTargets = roster.stream().map(MemberRef::characterObjectId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		for (MemberRef actor : roster)
		{
			PhantomAssertions.assertEquals(exactTargets, backend.targetsByActor.get(actor), "PartyTactics observed a foreign or missing Party member for " + actor.stableKey());
		}
	}

	@SafeVarargs
	private static Map<MemberRef, MemberSnapshot> snapshots(List<MemberRef>... rosters)
	{
		final Map<MemberRef, MemberSnapshot> result = new LinkedHashMap<>();
		for (List<MemberRef> roster : rosters)
		{
			for (MemberRef member : roster)
			{
				result.put(member, member(member, false, 100, List.of(), List.of()));
			}
		}
		return result;
	}

	private static CurrentForceSnapshot force(List<List<MemberRef>> rosters, Map<MemberRef, MemberSnapshot> snapshots)
	{
		final List<PartySnapshot> parties = rosters.stream().map(roster -> new PartySnapshot(roster.getFirst(), roster, PartyDistributionType.FINDERS_KEEPERS)).toList();
		final MemberRef leader = rosters.getFirst().getFirst();
		final boolean commandChannel = rosters.size() > 1;
		return new CurrentForceSnapshot(leader, leader, commandChannel ? "cc:goal026d" : "", commandChannel ? leader : null, commandChannel ? 1 : 0, snapshots.size(), parties, List.copyOf(snapshots.values()));
	}

	private static List<MemberRef> phantoms(long first, long last)
	{
		return java.util.stream.LongStream.rangeClosed(first, last).mapToObj(id -> MemberRef.phantom(id, 1000 + (int) id)).toList();
	}

	private static MemberSnapshot member(MemberRef member, boolean dead, int hp, List<MemberCapability> capabilities, List<Integer> attackers)
	{
		return new MemberSnapshot(member, 88, 10, 0, 0, 0, hp, 100, 100, dead, false, false, false, 0, attackers, capabilities, "B".repeat(64));
	}

	private static MemberCapability capability(String key, boolean readyNow)
	{
		return new MemberCapability(key, "test", 900, 100, 1, "SINGLE_TARGET", true, true, readyNow, readyNow ? "ready" : "cooldown", 100, "goal026d");
	}

	private static CapabilityRequirement requirement(String key)
	{
		return new CapabilityRequirement(key, 1, 850, true);
	}

	private static PhantomRaidEncounterProfile scriptedProfile(List<CapabilityRequirement> requirements)
	{
		return PhantomRaidEncounterProfile.create("epic.goal026d", ContentKind.EPIC, 29181, NpcKind.GRAND_BOSS, EntryKind.SCRIPTED, 32713, 135, 83, 8, 27, 1, 0, 0, requirements);
	}

	private static PhantomRaidEncounterProfile openProfile(List<CapabilityRequirement> requirements)
	{
		return PhantomRaidEncounterProfile.create("raid.goal026d", ContentKind.RAID, 29020, NpcKind.RAID_BOSS, EntryKind.OPEN_WORLD, 0, 0, 80, 1, 18, 1, 0, 0, requirements);
	}

	private static final class RuntimeFixture
	{
		private final AtomicLong clock = new AtomicLong(1);
		private final ManualDispatcher dispatcher = new ManualDispatcher();
		private final RecordingPartyBackend party = new RecordingPartyBackend();
		private final Map<Long, FakeLease> leases = new LinkedHashMap<>();
		private final PhantomCombatService combat;
		private final PhantomRaidScriptRegistry scripts = new PhantomRaidScriptRegistry();
		private final L2jPhantomRaidAttemptRuntime runtime;

		private RuntimeFixture(boolean recordingTactics)
		{
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(_ -> List.of(new CapabilityEvidence(PhantomCombatMode.MELEE_PHYSICAL.capabilityKey(), 900, List.of())));
			combat = new PhantomCombatService(profileId -> leases.computeIfAbsent(profileId, FakeLease::new), resolver, PhantomCombatPolicy.productionDefaults(16), new PhantomCombatMetrics(), clock::get, dispatcher);
			combat.start();
			final PhantomPartyTactics tactics = recordingTactics ? new PhantomPartyTactics(combat, party) : new PhantomPartyTactics(combat);
			runtime = new L2jPhantomRaidAttemptRuntime(combat, tactics, new PhantomPartyRouteCoordinator(null, null), () -> "A".repeat(64), clock::get);
			scripts.install(new FakeAdapter());
		}

		private MechanicContext mechanicContext(PhantomRaidEncounterProfile profile)
		{
			return new MechanicContext(AUTHORITY, profile, scripts.find(profile.contentId()).orElseThrow(), MemberRef.phantom(1, 1001), 10_000, () -> false);
		}

		private FakeLease lease(long profileId)
		{
			return leases.computeIfAbsent(profileId, FakeLease::new);
		}

		private void stop()
		{
			runtime.cancel(AUTHORITY);
			combat.beginStop();
			while (dispatcher.next != null)
			{
				dispatcher.runNext();
			}
			PhantomAssertions.assertTrue(combat.finishStop(), "Goal026D runtime fixture did not stop.");
		}
	}

	private static final class RecordingPartyBackend implements PhantomPartyBackend
	{
		private final Map<MemberRef, Set<Integer>> targetsByActor = new LinkedHashMap<>();
		private final Set<MemberRef> queriedActors = new LinkedHashSet<>();
		@Override public OptionalLong managedProfileId(int characterObjectId) { return OptionalLong.empty(); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.empty(); }
		@Override public CurrentForceObservation currentForce(MemberRef actor) { return CurrentForceObservation.unavailable("test.unused"); }
		@Override public OptionalInt currentLevel(MemberRef member) { return OptionalInt.empty(); }
		@Override public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { throw new UnsupportedOperationException(); }
		@Override public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome leave(MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome expel(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return Optional.empty(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return Optional.empty(); }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { queriedActors.add(actor); targetsByActor.computeIfAbsent(actor, _ -> new LinkedHashSet<>()).add(exactTargetObjectId); return List.of(); }
		@Override public boolean materialize(long profileId) { return false; }
	}

	private static final class FakeAdapter implements PhantomRaidScriptAdapter
	{
		@Override public String contentId() { return "epic.goal026d"; }
		@Override public int entryNpcId() { return 32713; }
		@Override public int templateId() { return 135; }
		@Override public EntryResult enter(EntryRequest request) { return EntryResult.entered(10); }
		@Override public List<CandleEvidence> candles(int instanceId) { return List.of(); }
		@Override public CandleInteraction interactCandle(int instanceId, int scoutObjectId, int candleObjectId) { return CandleInteraction.MISSING; }
		@Override public Optional<TargetEvidence> revealedTarget(int instanceId) { return Optional.of(new TargetEvidence(7001, 29181, 10)); }
		@Override public Optional<PhantomNavigationPoint> safeRetreatPoint(int instanceId) { return Optional.of(new PhantomNavigationPoint(0, 0, 0, instanceId)); }
		@Override public boolean confirmsDeath(TargetEvidence evidence) { return false; }
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable next;
		private ManualHandle handle;
		@Override public DispatchResult dispatch(Runnable runnable, long delayMillis) { if (next != null) { throw new AssertionError("More than one shared Combat pulse was scheduled."); } next = runnable; handle = new ManualHandle(this); return DispatchResult.accepted(handle); }
		private void runNext() { final Runnable runnable = next; if (runnable == null) { throw new AssertionError("No Combat pulse was scheduled."); } next = null; final ManualHandle exact = handle; handle = null; exact.state = DispatchState.RUNNING; try { runnable.run(); } finally { exact.state = DispatchState.FINISHED; } }
	}

	private static final class ManualHandle implements DispatchHandle
	{
		private final ManualDispatcher owner;
		private DispatchState state = DispatchState.SCHEDULED;
		private ManualHandle(ManualDispatcher owner) { this.owner = owner; }
		@Override public boolean cancelIfNotStarted() { if ((state != DispatchState.SCHEDULED) || (owner.handle != this)) { return false; } owner.next = null; owner.handle = null; state = DispatchState.CANCELLED; return true; }
		@Override public DispatchState state() { return state; }
	}

	private static final class FakeLease implements PhantomCombatActorLease
	{
		private final long profileId;
		private int supportCasts;
		private int raidTargetReads;
		private FakeLease(long profileId) { this.profileId = profileId; }
		@Override public ActorSnapshot actorSnapshot() { return new ActorSnapshot(1000 + (int) profileId, 88, 0, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0); }
		@Override public TargetSnapshot targetSnapshot(int targetObjectId) { return null; }
		@Override public RaidTargetSnapshot raidTargetSnapshot(int targetObjectId) { raidTargetReads++; return new RaidTargetSnapshot(7001, 29020, 0, 100, 100, false, false, true, true, false, true, NpcKind.RAID_BOSS, 100, false, true); }
		@Override public int raidActorLevel() { return 80; }
		@Override public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode) { return false; }
		@Override public List<ThreatObservation> observedAttackers(int limit) { return List.of(); }
		@Override public List<LootCandidate> lootCandidates(int limit, int maximumDistance) { return List.of(); }
		@Override public LootObservation observeLoot(LootCandidate candidate) { return LootObservation.PENDING; }
		@Override public ShotOutcome activateShot(PhantomCombatMode mode) { return ShotOutcome.UNAVAILABLE; }
		@Override public ActionOutcome attack(int targetObjectId) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome attackRaid(int targetObjectId, PhantomRaidCombatRequest request) { return ActionOutcome.ISSUED; }
		@Override public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome castSupport(PhantomPartySupportAction action) { supportCasts++; return ActionOutcome.ISSUED; }
		@Override public ActionOutcome pickUp(int objectId) { return ActionOutcome.REJECTED; }
		@Override public void cancelOwnedAction(PhantomOwnedAction action) { }
		@Override public RespawnOutcome respawnTown() { return RespawnOutcome.REJECTED; }
		@Override public void close() { }
	}
}
