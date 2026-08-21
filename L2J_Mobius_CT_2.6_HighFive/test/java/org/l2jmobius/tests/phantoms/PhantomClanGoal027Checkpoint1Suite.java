/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.gameserver.model.clan.ClanInvitationService;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDecision;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.Backend;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ClanSnapshot;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionObservation;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.MemberRef;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OperationStatus;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OrganizationMetadata;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleKey;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.StoredMetadata;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.WithdrawalOutcome;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;

public final class PhantomClanGoal027Checkpoint1Suite implements PhantomTestSuite
{
	public enum Mode
	{
		CREATION_RESTART,
		RECRUITMENT,
		ROLES,
		TREASURY,
		CHAT_DECISION
	}

	private static final long SEED = 27002701L;
	private static final long NOW = 1_000;
	private final Mode _mode;

	public PhantomClanGoal027Checkpoint1Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "clan-goal027cp1-" + _mode.name().toLowerCase();
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 027 CP1 used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CREATION_RESTART ->
			{
				registry.add("01-canonical-create-once-and-restart-reconcile", this::creationRestart);
				registry.add("02-production-seams-have-no-shadow-sql", this::canonicalSourceGuard);
			}
			case RECRUITMENT -> registry.add("01-bilateral-later-phantom-accept-and-real-manual", this::recruitment);
			case ROLES -> registry.add("01-leadership-and-external-drift-replan", this::roles);
			case TREASURY -> registry.add("01-prepared-completed-restart-no-dupe-and-withdraw-unsupported", this::treasury);
			case CHAT_DECISION -> registry.add("01-clan-chat-receipt-decision-and-lifecycle", this::chatDecision);
		}
	}

	private void creationRestart(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addPhantom(1, 100);
		final PhantomGoal build = goal(1, 10, PhantomClanService.BUILD_GOAL, new PhantomDomainRef("clan.name", "CodexClan"), null, List.of(), 1, 0, 0);
		fixture.goals.put(1, build);
		final PhantomClanService service = fixture.service();
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, service.advance(1, 10, 0).status(), "Canonical creation did not replan after creating.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(1, 10, 0).status(), "Created clan did not reconcile to complete.");
		PhantomAssertions.assertEquals(1, fixture.backend.createCalls, "ClanTable creation seam was called more than once.");
		PhantomAssertions.assertEquals(42, fixture.backend.clan(100).clanId(), "Canonical clan was not observed on the Player.");

		final PhantomClanService restarted = fixture.service();
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, restarted.advance(1, 10, 0).status(), "Restart did not complete from canonical membership.");
		PhantomAssertions.assertEquals(1, fixture.backend.createCalls, "Restart attempted a second clan creation.");
		PhantomAssertions.assertEquals(42, fixture.persistence.load(1).orElseThrow().metadata().canonicalClanId(), "Profile metadata lost canonical evidence.");
	}

	private void canonicalSourceGuard(PhantomTestContext context) throws Exception
	{
		final String backend = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java"));
		final String service = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/model/clan/ClanInvitationService.java"));
		final String request = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinPledge.java"));
		final String answer = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinPledge.java"));
		PhantomAssertions.assertTrue(backend.contains("ClanTable.getInstance().createClan"), "Production creation does not use ClanTable.createClan.");
		PhantomAssertions.assertTrue(backend.contains("clan.setNewLeader"), "Production leadership does not use Clan.setNewLeader.");
		PhantomAssertions.assertTrue(backend.contains("transferItem(ItemProcessType.TRANSFER"), "Production deposit does not use canonical inventory transfer.");
		PhantomAssertions.assertTrue(backend.contains("ChatType.CLAN") && backend.contains("openGeneratedDispatch"), "Clan chat bypasses canonical chat observation safety.");
		PhantomAssertions.assertTrue(service.contains("clan.addClanMember(player)"), "Shared consent service does not own the exact join mutation.");
		PhantomAssertions.assertTrue(request.contains("ClanInvitationService.getInstance().invite") && answer.contains("service.respond"), "Ordinary packets do not delegate the shared consent service.");
		for (String forbidden : List.of("INSERT INTO clan_data", "UPDATE clan_data", "UPDATE characters SET clan", "items SET"))
		{
			PhantomAssertions.assertTrue(!backend.contains(forbidden), "Phantom clan backend contains forbidden direct SQL: " + forbidden);
		}
	}

	private void recruitment(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addPhantom(1, 100);
		fixture.backend.addPhantom(2, 200);
		fixture.backend.putClan(100, 100);
		final PhantomGoal build = goal(1, 20, PhantomClanService.BUILD_GOAL, new PhantomDomainRef("clan.name", "CodexClan"), null, List.of(new PhantomDomainRef("profile", "2")), 1, 0, 0);
		final PhantomGoal join = goal(2, 21, PhantomClanService.JOIN_GOAL, new PhantomDomainRef("clan.id", "42"), null, List.of(), 1, 0, 0);
		fixture.goals.put(1, build);
		fixture.goals.put(2, join);
		final PhantomClanService service = fixture.service();

		PhantomAssertions.assertEquals(OperationStatus.WAITING, service.advance(1, 20, 0).status(), "Build did not create one pending invitation.");
		PhantomAssertions.assertTrue(fixture.backend.clan(200) == null, "Phantom joined before its later matching clan.join advance.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(2, 21, 0).status(), "Matching later clan.join did not accept.");
		PhantomAssertions.assertEquals(42, fixture.backend.clan(200).clanId(), "Accepted Phantom membership is not canonical.");
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, service.advance(1, 20, 0).status(), "Leader did not observe terminal invitation membership.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(1, 20, 0).status(), "Recruitment did not finish after bounded candidates.");
		PhantomAssertions.assertEquals(1, fixture.backend.inviteCalls, "Recruitment emitted duplicate invitations.");

		fixture.backend.addReal(300);
		final PhantomGoal realBuild = goal(1, 22, PhantomClanService.BUILD_GOAL, new PhantomDomainRef("clan.name", "CodexClan"), null, List.of(new PhantomDomainRef("character.object", "300")), 1, 0, 0);
		fixture.goals.put(1, realBuild);
		PhantomAssertions.assertEquals(OperationStatus.WAITING, service.advance(1, 22, 0).status(), "REAL candidate did not receive a manual invitation.");
		PhantomAssertions.assertTrue(fixture.backend.clan(300) == null, "REAL candidate was accepted autonomously.");
		PhantomAssertions.assertEquals(1, fixture.backend.respondCalls, "REAL candidate used the Phantom accept path.");
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Clan service did not stop after cancelling only pending operations.");
		PhantomAssertions.assertEquals(42, fixture.backend.clan(100).clanId(), "Shutdown destroyed canonical clan state.");
	}

	private void roles(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addPhantom(1, 100);
		fixture.backend.addPhantom(2, 200);
		fixture.backend.putClan(100, 100);
		fixture.backend.putClan(200, 100);
		final PhantomGoal leader = goal(1, 30, PhantomClanService.ROLE_GOAL, new PhantomDomainRef("clan.id", "42"), "leader", List.of(new PhantomDomainRef("profile", "2")), 1, 0, 0);
		fixture.goals.put(1, leader);
		final PhantomClanService service = fixture.service();
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(1, 30, 0).status(), "Explicit LEADER role did not complete.");
		PhantomAssertions.assertEquals(200, fixture.backend.clan(100).leaderObjectId(), "Canonical leader was not transferred.");
		PhantomAssertions.assertEquals(1, fixture.backend.transferCalls, "Clan.setNewLeader seam was not called exactly once.");
		PhantomAssertions.assertEquals(RoleKey.LEADER, fixture.persistence.load(2).orElseThrow().metadata().roleIntent(), "Phantom leader metadata was not persisted.");

		fixture.backend.externalLeader(42, 100);
		final PhantomGoal officer = goal(1, 31, PhantomClanService.ROLE_GOAL, new PhantomDomainRef("clan.id", "42"), "officer", List.of(new PhantomDomainRef("profile", "2")), 1, 0, 0);
		fixture.goals.put(1, officer);
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, service.advance(1, 31, 0).status(), "External canonical leader drift did not stale/replan metadata.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(1, 31, 0).status(), "Replanned OFFICER metadata did not complete.");
		PhantomAssertions.assertEquals(RoleKey.OFFICER, fixture.persistence.load(2).orElseThrow().metadata().roleIntent(), "OFFICER planning metadata was not persisted.");
		PhantomAssertions.assertEquals(1, fixture.backend.transferCalls, "Planning-only role mutated canonical privileges.");
	}

	private void treasury(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addPhantom(2, 200);
		fixture.backend.putClan(200, 100);
		fixture.backend.inventory.put(900, 1_000L);
		final PhantomGoal contribution = goal(2, 40, PhantomClanService.CONTRIBUTE_GOAL, new PhantomDomainRef("clan.id", "42"), null, List.of(new PhantomDomainRef("item.object", "900")), 100, 100, 0);
		fixture.goals.put(2, contribution);
		final PhantomClanService service = fixture.service();
		final AdvanceResult completed = service.advance(2, 40, 0);
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, completed.status(), "Canonical warehouse contribution did not complete.");
		PhantomAssertions.assertEquals(900L, fixture.backend.inventory.get(900), "Inventory did not decrease exactly once.");
		PhantomAssertions.assertEquals(100L, fixture.backend.warehouse, "Clan warehouse did not increase exactly once.");
		PhantomAssertions.assertEquals(1, fixture.backend.contributionCalls, "Contribution transfer call count is wrong.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(2, 40, 0).status(), "Terminal contribution receipt was not idempotent.");
		PhantomAssertions.assertEquals(1, fixture.backend.contributionCalls, "Terminal retry duplicated contribution.");

		final PhantomClanService restarted = fixture.service();
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, restarted.advance(2, 40, 0).status(), "Restart did not reconcile durable COMPLETED receipt.");
		PhantomAssertions.assertEquals(1, fixture.backend.contributionCalls, "Restart duplicated canonical contribution.");
		final OrganizationMetadata metadata = fixture.persistence.load(2).orElseThrow().metadata();
		PhantomAssertions.assertEquals(ContributionState.COMPLETED, metadata.contributionState(), "Durable contribution state is not COMPLETED.");
		PhantomAssertions.assertEquals(WithdrawalOutcome.UNSUPPORTED, restarted.withdraw(2, 42, 1, 1), "Unproven autonomous withdrawal was not typed unsupported.");
	}

	private void chatDecision(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addPhantom(1, 100);
		fixture.backend.putClan(100, 100);
		final PhantomGoal build = goal(1, 50, PhantomClanService.BUILD_GOAL, new PhantomDomainRef("clan.name", "CodexClan"), null, List.of(), 1, 0, 0);
		fixture.goals.put(1, build);
		final PhantomClanService service = fixture.service();
		final PhantomClanDecision decision = new PhantomClanDecision(service);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		decision.registerCandidates(candidates);
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		decision.registerHandlers(handlers);
		handlers.seal();
		final PhantomPlanStep step = new PhantomPlanStep(0, PhantomClanDecision.BUILD_ACTION, build.target(), Map.of(), 30_000, 1, "clan.test");
		final PhantomPlan plan = new PhantomPlan(1, build.goalId(), PhantomClanDecision.BUILD_CANDIDATE, List.of(step), 30_000, 1);
		final PhantomStepResult decisionResult = handlers.snapshot().get(PhantomClanDecision.BUILD_ACTION).execute(new PhantomStepContext(1, build, plan, step, PhantomActivityState.ACTIVE, 1, 1, () -> false));
		PhantomAssertions.assertEquals(PhantomStepResult.Type.COMPLETE_GOAL, decisionResult.type(), "PhantomClanDecision did not complete canonical build.");

		final ChatResult first = service.postClanChat(1, 50, 0, "Собираемся у склада.");
		final ChatResult repeated = service.postClanChat(1, 50, 0, "Собираемся у склада.");
		PhantomAssertions.assertEquals(ChatOutcome.DELIVERED, first.outcome(), "Explicit clan chat was not delivered.");
		PhantomAssertions.assertEquals(ChatOutcome.DELIVERED, repeated.outcome(), "Clan chat receipt was not idempotent.");
		PhantomAssertions.assertEquals(1, fixture.backend.chatCalls, "Repeated Decision/event spammed clan chat.");
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Caller-driven clan service did not stop.");
		PhantomAssertions.assertEquals(PhantomClanService.State.STOPPED, service.snapshot().state(), "Clan lifecycle did not reach STOPPED.");
	}

	private static PhantomGoal goal(long profileId, long goalId, String type, PhantomDomainRef target, String method, List<PhantomDomainRef> sources, long amount, long budget, long revision)
	{
		return new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), target, amount, 0, method, sources, null, "clan.organization", 700, 0, budget, NOW + 100_000, Map.of(), "clan.test", revision);
	}

	private static final class Fixture
	{
		private final FakeGoals goals = new FakeGoals();
		private final FakePersistence persistence = new FakePersistence();
		private final FakeBackend backend = new FakeBackend();

		private PhantomClanService service()
		{
			final PhantomClanService service = new PhantomClanService(goals, persistence, backend, () -> NOW);
			PhantomAssertions.assertTrue(service.start(), "Clan service did not start.");
			return service;
		}
	}

	private static final class FakeGoals implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> values = new HashMap<>();

		private void put(long profileId, PhantomGoal goal)
		{
			values.put(profileId, new StoredGoal(goal, values.containsKey(profileId) ? values.get(profileId).rowVersion() + 1 : 0));
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId > 0;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(values.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			put(profileId, goal);
			return values.get(profileId);
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			put(profileId, goal);
			return values.get(profileId);
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			values.remove(profileId);
		}
	}

	private static final class FakePersistence implements PersistencePort
	{
		private final Map<Long, StoredMetadata> values = new HashMap<>();

		@Override
		public Optional<StoredMetadata> load(long profileId)
		{
			return Optional.ofNullable(values.get(profileId));
		}

		@Override
		public StoredMetadata save(long profileId, long expectedRowVersion, OrganizationMetadata metadata)
		{
			final StoredMetadata current = values.get(profileId);
			if ((current == null) != (expectedRowVersion < 0))
			{
				throw new IllegalStateException("Unexpected metadata insert/update mode.");
			}
			if ((current != null) && (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Stale metadata write.");
			}
			final StoredMetadata stored = new StoredMetadata(current == null ? 0 : current.rowVersion() + 1, metadata);
			values.put(profileId, stored);
			return stored;
		}
	}

	private static final class FakeBackend implements Backend
	{
		private final Map<Long, MemberRef> profiles = new HashMap<>();
		private final Map<Integer, MemberRef> characters = new HashMap<>();
		private final Map<Integer, ClanSnapshot> clans = new HashMap<>();
		private final Map<Integer, ClanInvitationService.InvitationSnapshot> invitations = new HashMap<>();
		private final Map<Integer, Long> inventory = new HashMap<>();
		private long warehouse;
		private long invitationSequence;
		private int createCalls;
		private int inviteCalls;
		private int respondCalls;
		private int transferCalls;
		private int contributionCalls;
		private int chatCalls;

		private void addPhantom(long profileId, int objectId)
		{
			final MemberRef member = MemberRef.phantom(profileId, objectId);
			profiles.put(profileId, member);
			characters.put(objectId, member);
		}

		private void addReal(int objectId)
		{
			characters.put(objectId, MemberRef.real(objectId));
		}

		private void putClan(int objectId, int leaderObjectId)
		{
			clans.put(objectId, snapshot(objectId, leaderObjectId));
		}

		private ClanSnapshot clan(int objectId)
		{
			return clans.get(objectId);
		}

		private void externalLeader(int clanId, int leaderObjectId)
		{
			for (int objectId : List.copyOf(clans.keySet()))
			{
				if (clans.get(objectId).clanId() == clanId)
				{
					clans.put(objectId, snapshot(objectId, leaderObjectId));
				}
			}
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			return Optional.ofNullable(profiles.get(profileId));
		}

		@Override
		public Optional<MemberRef> resolve(PhantomDomainRef source)
		{
			try
			{
				return "profile".equals(source.namespace()) ? Optional.ofNullable(profiles.get(Long.parseLong(source.key()))) : "character.object".equals(source.namespace()) ? Optional.ofNullable(characters.get(Integer.parseInt(source.key()))) : Optional.empty();
			}
			catch (RuntimeException exception)
			{
				return Optional.empty();
			}
		}

		@Override
		public Optional<ClanSnapshot> observe(MemberRef member)
		{
			return member == null ? Optional.empty() : Optional.ofNullable(clans.get(member.characterObjectId()));
		}

		@Override
		public CreationResult create(MemberRef actor, String clanName)
		{
			createCalls++;
			if (clans.containsKey(actor.characterObjectId()))
			{
				return new CreationResult(CreationOutcome.ALREADY_SATISFIED, clans.get(actor.characterObjectId()));
			}
			putClan(actor.characterObjectId(), actor.characterObjectId());
			return new CreationResult(CreationOutcome.CREATED, clans.get(actor.characterObjectId()));
		}

		@Override
		public ClanInvitationService.InviteResult invite(MemberRef requester, MemberRef target)
		{
			inviteCalls++;
			final ClanSnapshot clan = clans.get(requester.characterObjectId());
			if ((clan == null) || clans.containsKey(target.characterObjectId()) || invitations.containsKey(target.characterObjectId()))
			{
				return new ClanInvitationService.InviteResult(ClanInvitationService.InviteOutcome.JOIN_CONDITION_FAILED, null);
			}
			final ClanInvitationService.InvitationIdentity identity = new ClanInvitationService.InvitationIdentity(++invitationSequence, requester.characterObjectId(), target.characterObjectId(), clan.clanId(), 0);
			invitations.put(target.characterObjectId(), new ClanInvitationService.InvitationSnapshot(identity, clan.clanName(), 10_000));
			return new ClanInvitationService.InviteResult(ClanInvitationService.InviteOutcome.DELIVERED, identity);
		}

		@Override
		public Optional<ClanInvitationService.InvitationSnapshot> observeInvitation(MemberRef invitee)
		{
			return Optional.ofNullable(invitations.get(invitee.characterObjectId()));
		}

		@Override
		public ClanInvitationService.RespondResult respond(MemberRef invitee, ClanInvitationService.Response response, ClanInvitationService.InvitationIdentity identity)
		{
			respondCalls++;
			final ClanInvitationService.InvitationSnapshot pending = invitations.get(invitee.characterObjectId());
			if ((pending == null) || !pending.identity().equals(identity))
			{
				return new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.STALE_INVITE, identity);
			}
			invitations.remove(invitee.characterObjectId());
			if (response == ClanInvitationService.Response.REFUSE)
			{
				return new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.REFUSED, identity);
			}
			clans.put(invitee.characterObjectId(), snapshot(invitee.characterObjectId(), clans.get(identity.requesterObjectId()).leaderObjectId()));
			return new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.ACCEPTED, identity);
		}

		@Override
		public ClanInvitationService.CancelResult cancel(ClanInvitationService.InvitationIdentity identity)
		{
			final ClanInvitationService.InvitationSnapshot pending = invitations.get(identity.inviteeObjectId());
			if ((pending == null) || !pending.identity().equals(identity))
			{
				return new ClanInvitationService.CancelResult(ClanInvitationService.CancelOutcome.NO_PENDING_INVITE, identity);
			}
			invitations.remove(identity.inviteeObjectId());
			return new ClanInvitationService.CancelResult(ClanInvitationService.CancelOutcome.CANCELLED, identity);
		}

		@Override
		public RoleResult transferLeader(MemberRef requester, MemberRef newLeader, int expectedClanId)
		{
			final ClanSnapshot requesterClan = clans.get(requester.characterObjectId());
			final ClanSnapshot targetClan = clans.get(newLeader.characterObjectId());
			if ((requesterClan == null) || (targetClan == null) || (requesterClan.clanId() != expectedClanId) || (requesterClan.leaderObjectId() != requester.characterObjectId()))
			{
				return new RoleResult(RoleOutcome.UNAUTHORIZED, requesterClan);
			}
			transferCalls++;
			externalLeader(expectedClanId, newLeader.characterObjectId());
			return new RoleResult(RoleOutcome.COMPLETED, clans.get(newLeader.characterObjectId()));
		}

		@Override
		public ContributionObservation observeContribution(MemberRef member, int expectedClanId, int inventoryObjectId)
		{
			final Long count = inventory.get(inventoryObjectId);
			return new ContributionObservation((count != null) || (warehouse > 0), 57, count == null ? 0 : count, warehouse, PhantomClanService.sha256(expectedClanId + "|" + inventoryObjectId + "|" + count + "|" + warehouse));
		}

		@Override
		public ContributionResult contribute(MemberRef member, int expectedClanId, int inventoryObjectId, long count)
		{
			contributionCalls++;
			final long before = inventory.getOrDefault(inventoryObjectId, 0L);
			if (before < count)
			{
				return new ContributionResult(ContributionOutcome.SOURCE_MISSING, 0, 0, "");
			}
			inventory.put(inventoryObjectId, before - count);
			warehouse += count;
			return new ContributionResult(ContributionOutcome.COMPLETED, count, count, PhantomClanService.sha256("transfer|" + contributionCalls));
		}

		@Override
		public WithdrawalOutcome withdraw(MemberRef member, int expectedClanId, int warehouseObjectId, long count)
		{
			return WithdrawalOutcome.UNSUPPORTED;
		}

		@Override
		public ChatResult clanChat(MemberRef member, int expectedClanId, String text)
		{
			chatCalls++;
			return new ChatResult(ChatOutcome.DELIVERED, 2);
		}

		private ClanSnapshot snapshot(int memberObjectId, int leaderObjectId)
		{
			return new ClanSnapshot(42, "CodexClan", leaderObjectId, 1, Math.max(1, clans.size()), 15, 0, 0, PhantomClanService.sha256("42|CodexClan|" + leaderObjectId + "|" + memberObjectId));
		}
	}
}
