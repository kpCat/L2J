/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyIdentity;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ParticipationOutcome;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ParticipationReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.PartySlot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ReadyReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.StagingCenter;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.StagingSource;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AttemptStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterCatalog;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CapabilityAssessment;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ContentSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAuthority;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidTargetEvidence;

public final class PhantomRaidAttemptGoal026Checkpoint5Suite implements PhantomTestSuite
{
	private static final long SEED = 26002652L;

	@Override
	public String id()
	{
		return "raid-attempt-goal026cp5";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Raid Attempt CP5 used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-attempt-only-authority-and-confirmed-victory", _ -> authorityAndVictory());
		registry.add("02-native-loot-and-terminal-idempotency", _ -> lootAndIdempotency());
		registry.add("03-queen-level48-inclusive-level49-blocked", _ -> queenLevelGate());
		registry.add("04-structural-drift-provider-loss-and-objective-retreat", _ -> retreatTriggers());
		registry.add("05-zaken-entry-target-bind-and-script-death", _ -> zakenScriptVictory());
		registry.add("06-participation-follows-exact-leader-terminal", _ -> participationTerminal());
		registry.add("07-production-runtime-reuses-existing-services-and-keeps-real-observational", PhantomRaidAttemptGoal026Checkpoint5Suite::productionRuntimeScope);
	}

	private static void authorityAndVictory()
	{
		final Fixture fixture = Fixture.queen();
		final var first = fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(AttemptStatus.FIGHTING, first.status(), "Exact Queen attempt did not enter fighting.");
		final var view = fixture.service.view(1).orElseThrow();
		PhantomAssertions.assertTrue(view.attemptAuthorityHash().matches("[0-9A-F]{64}"), "AttemptService did not mint canonical authority.");
		PhantomAssertions.assertTrue(fixture.service.ownsAuthority(view.attemptAuthorityHash(), view.identity(), fixture.authority.target), "Exact AttemptService evidence did not own authority.");
		PhantomAssertions.assertFalse(fixture.service.ownsAuthority("F".repeat(64), view.identity(), fixture.authority.target), "Arbitrary hash was accepted as AttemptService authority.");

		fixture.runtime.deathObserved = true;
		fixture.runtime.lootComplete = true;
		PhantomAssertions.assertEquals(AttemptStatus.FIGHTING, fixture.service.advance(1, 10, 0).status(), "Unconfirmed actual death predicted victory.");
		fixture.authority.deathConfirmed = true;
		final var victory = fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(AttemptStatus.VICTORY, victory.status(), "Actual death plus exact authority did not produce victory.");
		PhantomAssertions.assertTrue(victory.terminalReceipt().actualTargetDeathObserved() && victory.terminalReceipt().nativeLootComplete(), "Victory receipt lacks death/native-loot evidence.");
	}

	private static void lootAndIdempotency()
	{
		final Fixture fixture = Fixture.queen();
		fixture.service.advance(1, 10, 0);
		fixture.runtime.deathObserved = true;
		fixture.authority.deathConfirmed = true;
		fixture.runtime.lootComplete = false;
		PhantomAssertions.assertEquals(AttemptStatus.LOOT, fixture.service.advance(1, 10, 0).status(), "Canonical victory skipped native loot completion.");
		fixture.runtime.lootComplete = true;
		final var terminal = fixture.service.advance(1, 10, 0);
		final var repeated = fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(AttemptStatus.VICTORY, repeated.status(), "Exact terminal attempt was not idempotent.");
		PhantomAssertions.assertEquals(terminal.terminalReceipt(), repeated.terminalReceipt(), "Exact terminal receipt changed on replay.");
		PhantomAssertions.assertEquals(1, fixture.runtime.completeCalls, "Terminal replay repeated runtime settlement.");
	}

	private static void queenLevelGate()
	{
		final Fixture allowed = Fixture.queen();
		allowed.party.levels.put(allowed.members.getLast(), 48);
		PhantomAssertions.assertEquals(AttemptStatus.FIGHTING, allowed.service.advance(1, 10, 0).status(), "Queen level48 member was rejected.");

		final Fixture blocked = Fixture.queen();
		blocked.party.levels.put(blocked.members.getLast(), 49);
		PhantomAssertions.assertEquals(AttemptStatus.ABORTED, blocked.service.advance(1, 10, 0).status(), "Queen level49 member was not blocked while raid curse is enabled.");
		PhantomAssertions.assertTrue(blocked.service.view(1).isEmpty(), "Rejected Queen preflight minted a live attempt.");
	}

	private static void retreatTriggers()
	{
		final Fixture drift = Fixture.queen();
		drift.service.advance(1, 10, 0);
		drift.party.force = drift.forceWithReplacedMember();
		final var aborted = drift.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(AttemptStatus.ABORTED, aborted.status(), "Structural force drift did not terminate after objective retreat.");
		PhantomAssertions.assertTrue(drift.runtime.cancelCalls > 0 && drift.runtime.retreatCalls > 0, "Retreat did not cancel owned actions before routing.");

		final Fixture providers = Fixture.queen();
		providers.service.advance(1, 10, 0);
		providers.runtime.engagementStatus = PhantomRaidAttemptRuntime.RuntimeStatus.PROVIDER_UNAVAILABLE;
		PhantomAssertions.assertEquals(AttemptStatus.ABORTED, providers.service.advance(1, 10, 0).status(), "All required providers unavailable did not retreat/abort.");
	}

	private static void zakenScriptVictory()
	{
		final Fixture fixture = Fixture.zaken();
		final var first = fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(AttemptStatus.FIGHTING, first.status(), "Zaken entry/mechanic did not bind exact revealed target.");
		PhantomAssertions.assertEquals(1, fixture.adapter.enterCalls, "Zaken adapter entry did not use the single canonical entry path.");
		fixture.runtime.deathObserved = true;
		fixture.runtime.lootComplete = true;
		PhantomAssertions.assertEquals(AttemptStatus.FIGHTING, fixture.service.advance(1, 10, 0).status(), "Zaken won without script death evidence.");
		fixture.adapter.deathConfirmed = true;
		PhantomAssertions.assertEquals(AttemptStatus.VICTORY, fixture.service.advance(1, 10, 0).status(), "Zaken actual death plus script confirmation did not win.");
	}

	private static void participationTerminal()
	{
		final Fixture fixture = Fixture.queen();
		fixture.store.goals.put(2L, participationGoal());
		fixture.assembly.participation = new ParticipationReceipt(ParticipationOutcome.JOINED, fixture.assembly.identity);
		PhantomAssertions.assertEquals(ParticipationStatus.WAITING_FOR_LEADER, fixture.service.participation(2, 20, 0), "Participant did not wait before leader startup.");
		fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(ParticipationStatus.ACTIVE, fixture.service.participation(2, 20, 0), "Participant did not follow active leader attempt.");
		fixture.runtime.deathObserved = true;
		fixture.runtime.lootComplete = true;
		fixture.authority.deathConfirmed = true;
		fixture.service.advance(1, 10, 0);
		PhantomAssertions.assertEquals(ParticipationStatus.VICTORY, fixture.service.participation(2, 20, 0), "Participant did not inherit exact leader victory.");

		final Fixture rejected = Fixture.queen();
		rejected.store.goals.put(2L, participationGoal());
		rejected.assembly.participation = new ParticipationReceipt(ParticipationOutcome.JOINED, rejected.assembly.identity);
		rejected.party.levels.put(rejected.members.getLast(), 49);
		PhantomAssertions.assertEquals(AttemptStatus.ABORTED, rejected.service.advance(1, 10, 0).status(), "Rejected Queen preflight did not terminalize leader outcome.");
		PhantomAssertions.assertEquals(ParticipationStatus.FAILED, rejected.service.participation(2, 20, 0), "Participant waited after exact leader preflight abort.");
	}

	private static PhantomGoal participationGoal()
	{
		return new PhantomGoal(20, "raid.participate", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "2"), new PhantomDomainRef("raid.content", PhantomRaidEncounterCatalog.QUEEN_ANT), 1, 0, null, List.of(), null, "raid.participate", 500, 0, 0, 20_000, Map.of(), "test.participate", 0);
	}

	private static void productionRuntimeScope(PhantomTestContext context) throws Exception
	{
		final String service = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAttemptService.java"), java.nio.charset.StandardCharsets.UTF_8);
		final String runtime = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAttemptRuntime.java"), java.nio.charset.StandardCharsets.UTF_8);
		final String production = service + runtime;
		for (String forbidden : List.of("new Thread", "ThreadPool", "ScheduledFuture", "setCurrentHp(", "doDie(", "setStatus(", "deleteMe(", "finishInstance(", "giveItems(", "addItem(", "teleToLocation("))
		{
			PhantomAssertions.assertFalse(production.contains(forbidden), "Raid Attempt crossed a forbidden production boundary: " + forbidden);
		}
		PhantomAssertions.assertTrue(runtime.contains("_tactics.plan") && runtime.contains("_tactics.dispatch"), "Production raid support did not reuse PhantomPartyTactics.");
		PhantomAssertions.assertTrue(runtime.contains("_combat.startRaidSession") && runtime.contains("MemberKind.PHANTOM") && !runtime.contains("MemberKind.REAL"), "Raid offense does not remain PHANTOM-only through the additive Combat path.");
		PhantomAssertions.assertTrue(runtime.contains("_routes.request") && runtime.contains("_routes.advance") && runtime.contains("raid.attempt.retreat"), "Objective retreat does not reuse bounded Party routes.");
		PhantomAssertions.assertTrue(runtime.contains("claim.collector() && result.victory()") && runtime.contains("nativeLootComplete(state)"), "Native loot completion is not guarded by the collector's canonical victory terminal.");
	}

	private static final class Fixture
	{
		private final AtomicLong wall = new AtomicLong(1_000);
		private final AtomicLong logical = new AtomicLong(1_000);
		private final MemoryGoalStore store = new MemoryGoalStore();
		private final MemoryPartyBackend party = new MemoryPartyBackend();
		private final FakeAuthority authority = new FakeAuthority();
		private final PhantomRaidEncounterCatalog catalog = new PhantomRaidEncounterCatalog();
		private final PhantomRaidScriptRegistry scripts = new PhantomRaidScriptRegistry();
		private final FakeRuntime runtime = new FakeRuntime();
		private final FakeAssembly assembly = new FakeAssembly();
		private final List<MemberRef> members;
		private final ContentSnapshot content;
		private final RaidReadiness readiness;
		private final FakeAdapter adapter;
		private final PhantomRaidAttemptService service;

		private Fixture(boolean zaken)
		{
			members = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(id -> MemberRef.phantom(id, 1000 + (int) id)).toList();
			party.members = members;
			party.levels.putAll(members.stream().collect(java.util.stream.Collectors.toMap(member -> member, member -> zaken ? 83 : 48)));
			party.force = force(members, zaken ? 0 : 0);
			content = zaken ? zakenContent() : queenContent();
			final TargetAvailability availability = zaken ? TargetAvailability.ENTRY_GATED : TargetAvailability.AVAILABLE;
			final BossObservation boss = new BossObservation(ContentKind.EPIC, content.npc().npcId(), true, zaken ? "ENTRY_GATED" : "0", !zaken, !zaken, false, null, wall.get(), "test");
			readiness = new RaidReadiness(content.requirement().contentId(), content, boss, availability, CurrentForceObservation.available(party.force), List.<CapabilityAssessment>of(), ReadinessStatus.GROUP_READY, "raid.group.ready");
			assembly.identity = new AssemblyIdentity(1, 10, 0, content.requirement().contentId());
			assembly.ready = ready(assembly.identity, party.force, readiness);
			store.goals.put(1L, prepareGoal(content.requirement().contentId(), 0));
			authority.target = new PhantomRaidTargetEvidence(ContentKind.EPIC, NpcKind.GRAND_BOSS, zaken ? 8001 : 7001, content.npc().npcId(), zaken ? 10 : 0, false);
			adapter = zaken ? new FakeAdapter(authority.target) : null;
			if (adapter != null)
			{
				scripts.install(adapter);
				runtime.mechanicTarget = authority.target;
			}
			service = new PhantomRaidAttemptService(store, assembly, (actor, contentId) -> readiness, party, authority, catalog, scripts, runtime, wall::get, logical::get, () -> false);
		}

		private static Fixture queen()
		{
			return new Fixture(false);
		}

		private static Fixture zaken()
		{
			return new Fixture(true);
		}

		private CurrentForceSnapshot forceWithReplacedMember()
		{
			final List<MemberRef> replaced = new java.util.ArrayList<>(members);
			replaced.set(replaced.size() - 1, MemberRef.phantom(99, 1099));
			party.levels.put(replaced.getLast(), 48);
			return force(replaced, 0);
		}
	}

	private static PhantomGoal prepareGoal(String contentId, long revision)
	{
		return new PhantomGoal(10, "raid.prepare", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("raid.content", contentId), 1, 0, null, List.of(), null, "raid.prepare", 500, 0, 0, 20_000, Map.of(), "test.prepare", revision);
	}

	private static ReadyReceipt ready(AssemblyIdentity identity, CurrentForceSnapshot force, RaidReadiness readiness)
	{
		final String structural = org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.structuralHash(force);
		final StagingCenter centre = new StagingCenter(StagingSource.CONTENT_ANCHOR, new PhantomNavigationPoint(0, 0, 0, 0), "C".repeat(64));
		final PartySlot slot = new PartySlot(force.partyLeader(), centre.point(), new PhantomDomainRef("raid.staging", "test"), "D".repeat(64));
		return new ReadyReceipt(identity, structural, centre, List.of(slot), readiness, 900);
	}

	private static CurrentForceSnapshot force(List<MemberRef> members, int instanceId)
	{
		final MemberRef leader = members.getFirst();
		final List<MemberSnapshot> snapshots = members.stream().map(member -> new MemberSnapshot(member, 88, instanceId, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.<MemberCapability>of(), "B".repeat(64))).toList();
		return new CurrentForceSnapshot(leader, leader, "", null, 0, members.size(), List.of(new PartySnapshot(leader, members, PartyDistributionType.FINDERS_KEEPERS)), snapshots);
	}

	private static ContentSnapshot queenContent()
	{
		return content(PhantomRaidEncounterCatalog.QUEEN_ANT, 29001, 40, 9, 45, List.of(new CapabilityRequirement("combat.heal", 1, 900, true)));
	}

	private static ContentSnapshot zakenContent()
	{
		return content(PhantomRaidEncounterCatalog.ZAKEN_83, 29181, 83, 9, 27, List.of(new CapabilityRequirement("combat.tank", 1, 850, true), new CapabilityRequirement("combat.heal", 1, 900, true), new CapabilityRequirement("combat.resurrection", 1, 900, true)));
	}

	private static ContentSnapshot content(String id, int npcId, int level, int minimum, int maximum, List<CapabilityRequirement> requirements)
	{
		final ContentRequirementFact requirement = new ContentRequirementFact(id, ContentKind.EPIC, npcId, null, null, minimum, maximum, requirements, List.of("test"), PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION);
		final NpcFact npc = new NpcFact(npcId, level, NpcKind.GRAND_BOSS, true, true, false, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		return new ContentSnapshot(requirement, npc, "a".repeat(64));
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private final Map<Long, PhantomGoal> goals = new HashMap<>();
		@Override public boolean profileExists(long profileId) { return true; }
		@Override public Optional<StoredGoal> load(long profileId) { return Optional.ofNullable(goals.get(profileId)).map(goal -> new StoredGoal(goal, 0)); }
		@Override public StoredGoal insert(long profileId, PhantomGoal goal) { goals.put(profileId, goal); return new StoredGoal(goal, 0); }
		@Override public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal) { goals.put(profileId, goal); return new StoredGoal(goal, expectedRowVersion + 1); }
		@Override public void delete(long profileId, long expectedRowVersion) { goals.remove(profileId); }
	}

	private static final class FakeAssembly implements PhantomRaidAttemptService.AssemblyPort
	{
		private AssemblyIdentity identity;
		private ReadyReceipt ready;
		private ParticipationReceipt participation = new ParticipationReceipt(ParticipationOutcome.IMPOSSIBLE, null);
		@Override public Optional<ReadyReceipt> readyReceipt(AssemblyIdentity requested) { return requested.equals(identity) ? Optional.ofNullable(ready) : Optional.empty(); }
		@Override public ParticipationReceipt participationReceipt(long profileId, long goalId, long goalRevision) { return participation; }
	}

	private static final class FakeAuthority implements PhantomRaidAuthority
	{
		private PhantomRaidTargetEvidence target;
		private boolean deathConfirmed;
		@Override public BossObservation observe(ContentKind contentKind, int npcId) { return new BossObservation(contentKind, npcId, true, "0", true, true, false, null, 1, "test"); }
		@Override public Optional<PhantomRaidTargetEvidence> observeTarget(ContentKind contentKind, int npcId) { return Optional.ofNullable(target); }
		@Override public boolean confirmsDeath(PhantomRaidTargetEvidence expected) { return deathConfirmed && expected.sameIdentity(target); }
	}

	private static final class FakeRuntime implements PhantomRaidAttemptRuntime
	{
		private RuntimeStatus engagementStatus = RuntimeStatus.INTERMEDIATE;
		private PhantomRaidTargetEvidence mechanicTarget;
		private boolean deathObserved;
		private boolean lootComplete;
		private int cancelCalls;
		private int retreatCalls;
		private int completeCalls;
		@Override public MechanicAdvance advanceMechanic(MechanicContext context, CurrentForceSnapshot force) { return mechanicTarget == null ? new MechanicAdvance(RuntimeStatus.INTERMEDIATE, null, "test.mechanic") : new MechanicAdvance(RuntimeStatus.TARGET_REVEALED, mechanicTarget, "test.revealed"); }
		@Override public EngagementAdvance advanceEngagement(EngagementContext context, CurrentForceSnapshot force) { return new EngagementAdvance(engagementStatus, deathObserved, lootComplete, "test.engagement"); }
		@Override public RetreatAdvance advanceRetreat(RetreatContext context, CurrentForceSnapshot force) { retreatCalls++; return new RetreatAdvance(RuntimeStatus.COMPLETE, "test.retreat.complete"); }
		@Override public void cancel(String attemptAuthorityHash) { cancelCalls++; }
		@Override public void complete(String attemptAuthorityHash) { completeCalls++; }
		@Override public void beginStop() { }
	}

	private static final class FakeAdapter implements PhantomRaidScriptAdapter
	{
		private final PhantomRaidTargetEvidence target;
		private int enterCalls;
		private boolean deathConfirmed;
		private FakeAdapter(PhantomRaidTargetEvidence target) { this.target = target; }
		@Override public String contentId() { return PhantomRaidEncounterCatalog.ZAKEN_83; }
		@Override public int entryNpcId() { return 32713; }
		@Override public int templateId() { return 135; }
		@Override public EntryResult enter(EntryRequest request) { enterCalls++; return EntryResult.entered(10); }
		@Override public List<CandleEvidence> candles(int instanceId) { return List.of(); }
		@Override public CandleInteraction interactCandle(int instanceId, int scoutObjectId, int candleObjectId) { return CandleInteraction.MISSING; }
		@Override public Optional<TargetEvidence> revealedTarget(int instanceId) { return Optional.of(new TargetEvidence(target.objectId(), target.npcId(), target.instanceId())); }
		@Override public Optional<PhantomNavigationPoint> safeRetreatPoint(int instanceId) { return Optional.of(new PhantomNavigationPoint(0, 0, 0, instanceId)); }
		@Override public boolean confirmsDeath(TargetEvidence evidence) { return deathConfirmed && (evidence.objectId() == target.objectId()) && (evidence.npcId() == target.npcId()) && (evidence.instanceId() == target.instanceId()); }
	}

	private static final class MemoryPartyBackend implements PhantomPartyBackend
	{
		private List<MemberRef> members = List.of();
		private CurrentForceSnapshot force;
		private final Map<MemberRef, Integer> levels = new HashMap<>();
		@Override public OptionalLong managedProfileId(int characterObjectId) { return members.stream().filter(member -> member.characterObjectId() == characterObjectId).findFirst().map(member -> OptionalLong.of(member.profileId())).orElseGet(OptionalLong::empty); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return members.stream().filter(member -> member.profileId() == profileId).findFirst(); }
		@Override public CurrentForceObservation currentForce(MemberRef actor) { return (force != null) && force.members().stream().anyMatch(member -> member.ref().equals(actor)) ? CurrentForceObservation.available(force) : CurrentForceObservation.unavailable("test.force.missing"); }
		@Override public OptionalInt currentLevel(MemberRef member) { final Integer value = levels.get(member); return value == null ? OptionalInt.empty() : OptionalInt.of(value); }
		@Override public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { throw new UnsupportedOperationException(); }
		@Override public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome leave(MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome expel(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return Optional.empty(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return force.members().stream().filter(value -> value.ref().equals(member)).findFirst(); }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return List.of(); }
		@Override public boolean materialize(long profileId) { return false; }
	}
}
