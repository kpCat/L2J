/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.clan.ClanInvitationService;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService.InvitationSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;

/**
 * Bounded caller-driven owner for explicit Phantom clan goals. Canonical Clan,
 * Player, ClanTable, invitation and warehouse services retain game-state truth.
 */
public final class PhantomClanService
{
	public static final String BUILD_GOAL = "clan.build";
	public static final String JOIN_GOAL = "clan.join";
	public static final String ROLE_GOAL = "clan.role";
	public static final String CONTRIBUTE_GOAL = "clan.contribute";
	public static final String CHAT_GOAL = "clan.chat";
	public static final String CHAT_TEXT_CONSTRAINT = "text";
	public static final int MAX_ACTIVE_OPERATIONS = 64;
	public static final int MAX_TERMINAL_RECEIPTS = 256;
	public static final long MAX_CONTRIBUTION_COUNT = 1_000_000_000L;
	public static final int MAX_CHAT_TEXT = 105;

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum OperationStatus
	{
		WAITING,
		REPLAN,
		COMPLETE,
		FAILED,
		EXPIRED,
		CANCELLED,
		UNSUPPORTED,
		STALE
	}

	public enum RoleKey
	{
		LEADER,
		OFFICER,
		RECRUITER,
		TREASURER,
		MEMBER
	}

	public enum MemberKind
	{
		PHANTOM,
		REAL
	}

	public enum CreationOutcome
	{
		CREATED,
		ALREADY_SATISFIED,
		LEVEL_TOO_LOW,
		ALREADY_IN_CLAN,
		CREATE_COOLDOWN,
		INVALID_NAME,
		NAME_TAKEN,
		STALE,
		FAILED
	}

	public enum RoleOutcome
	{
		COMPLETED,
		ALREADY_SATISFIED,
		UNAUTHORIZED,
		STALE,
		FAILED
	}

	public enum ContributionOutcome
	{
		COMPLETED,
		SOURCE_MISSING,
		NOT_DEPOSITABLE,
		CAPACITY,
		STALE,
		INCONSISTENT,
		FAILED
	}

	public enum ContributionState
	{
		NONE,
		PREPARED,
		COMPLETED
	}

	public enum ChatOutcome
	{
		DELIVERED,
		REJECTED,
		STALE,
		FAILED
	}

	public enum WithdrawalOutcome
	{
		UNSUPPORTED
	}

	public record MemberRef(MemberKind kind, long profileId, int characterObjectId)
	{
		public MemberRef
		{
			Objects.requireNonNull(kind);
			if ((characterObjectId <= 0) || ((kind == MemberKind.PHANTOM) && (profileId <= 0)) || ((kind == MemberKind.REAL) && (profileId != 0)))
			{
				throw new IllegalArgumentException("Invalid clan member identity.");
			}
		}

		public static MemberRef phantom(long profileId, int characterObjectId)
		{
			return new MemberRef(MemberKind.PHANTOM, profileId, characterObjectId);
		}

		public static MemberRef real(int characterObjectId)
		{
			return new MemberRef(MemberKind.REAL, 0, characterObjectId);
		}

		public String stableKey()
		{
			return kind == MemberKind.PHANTOM ? "profile:" + profileId : "character.object:" + characterObjectId;
		}
	}

	public record ClanSnapshot(int clanId, String clanName, int leaderObjectId, int level, int memberCount, int maxMembers, int allianceId, int reputation, String evidenceHash)
	{
		public ClanSnapshot
		{
			if ((clanId <= 0) || (clanName == null) || clanName.isBlank() || (leaderObjectId <= 0) || (level < 0) || (memberCount < 1) || (maxMembers < memberCount) || (evidenceHash == null) || (evidenceHash.length() != 64))
			{
				throw new IllegalArgumentException("Invalid canonical clan snapshot.");
			}
		}
	}

	public record CreationResult(CreationOutcome outcome, ClanSnapshot clan)
	{
	}

	public record RoleResult(RoleOutcome outcome, ClanSnapshot clan)
	{
	}

	public record ContributionObservation(boolean available, int itemId, long inventoryCount, long warehouseCount, String evidenceHash)
	{
	}

	public record ContributionResult(ContributionOutcome outcome, long inventoryDecrease, long warehouseIncrease, String evidenceHash)
	{
		public boolean exact(long requested)
		{
			return (outcome == ContributionOutcome.COMPLETED) && (inventoryDecrease == requested) && (warehouseIncrease == requested);
		}
	}

	public record ChatResult(ChatOutcome outcome, int deliveries)
	{
	}

	public record AdvanceResult(OperationStatus status, String reasonKey, Receipt receipt)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(status);
			reasonKey = requireKey(reasonKey);
		}
	}

	public record Receipt(long profileId, long goalId, long goalRevision, String goalType, int clanId, int actorObjectId, int subjectObjectId, long canonicalDelta, String evidenceHash)
	{
	}

	public record OrganizationMetadata(int canonicalClanId, String clanName, int canonicalLeaderObjectId, RoleKey roleIntent, long organizationGoalId, long goalRevision, long contributionBudget, int contributionItemObjectId, long contributionAmount, long contributionInventoryBefore, long contributionWarehouseBefore, ContributionState contributionState, List<String> relationReferences, String canonicalEvidenceHash, String intentEvidenceHash, long updatedEpochMillis)
	{
		public OrganizationMetadata
		{
			if ((canonicalClanId <= 0) || (clanName == null) || clanName.isBlank() || (canonicalLeaderObjectId <= 0) || (roleIntent == null) || (organizationGoalId <= 0) || (goalRevision < 0) || (contributionBudget < 0) || (contributionItemObjectId < 0) || (contributionAmount < 0) || (contributionInventoryBefore < 0) || (contributionWarehouseBefore < 0) || (contributionState == null) || ((contributionState == ContributionState.NONE) && ((contributionItemObjectId != 0) || (contributionAmount != 0))) || ((contributionState != ContributionState.NONE) && ((contributionItemObjectId <= 0) || (contributionAmount <= 0))) || (relationReferences == null) || (relationReferences.size() > 16) || !hash(canonicalEvidenceHash) || !hash(intentEvidenceHash) || (updatedEpochMillis < 0))
			{
				throw new IllegalArgumentException("Invalid Phantom clan organization metadata.");
			}
			relationReferences = relationReferences.stream().map(PhantomClanService::requireReference).sorted().distinct().toList();
		}
	}

	public interface PersistencePort
	{
		Optional<StoredMetadata> load(long profileId);

		StoredMetadata save(long profileId, long expectedRowVersion, OrganizationMetadata metadata);
	}

	public record StoredMetadata(long rowVersion, OrganizationMetadata metadata)
	{
		public StoredMetadata
		{
			if ((rowVersion < 0) || (metadata == null))
			{
				throw new IllegalArgumentException("Invalid stored clan metadata.");
			}
		}
	}

	public interface Backend
	{
		Optional<MemberRef> currentMember(long profileId);

		Optional<MemberRef> resolve(PhantomDomainRef source);

		Optional<ClanSnapshot> observe(MemberRef member);

		CreationResult create(MemberRef actor, String clanName);

		ClanInvitationService.InviteResult invite(MemberRef requester, MemberRef target);

		Optional<InvitationSnapshot> observeInvitation(MemberRef invitee);

		ClanInvitationService.RespondResult respond(MemberRef invitee, ClanInvitationService.Response response, InvitationIdentity identity);

		ClanInvitationService.CancelResult cancel(InvitationIdentity identity);

		RoleResult transferLeader(MemberRef requester, MemberRef newLeader, int expectedClanId);

		ContributionObservation observeContribution(MemberRef member, int expectedClanId, int inventoryObjectId);

		ContributionResult contribute(MemberRef member, int expectedClanId, int inventoryObjectId, long count);

		WithdrawalOutcome withdraw(MemberRef member, int expectedClanId, int warehouseObjectId, long count);

		ChatResult clanChat(MemberRef member, int expectedClanId, String text);
	}

	public record Snapshot(State state, int activeOperations, int terminalReceipts, int chatReceipts)
	{
	}

	private final PhantomGoalStore _goals;
	private final PersistencePort _persistence;
	private final Backend _backend;
	private final LongSupplier _clock;
	private final Map<Long, Operation> _active = new HashMap<>();
	private final LinkedHashMap<OperationIdentity, AdvanceResult> _terminal = new LinkedHashMap<>();
	private final LinkedHashMap<ChatIdentity, ChatResult> _chatReceipts = new LinkedHashMap<>();
	private State _state = State.NEW;

	public PhantomClanService(PhantomGoalStore goals, PersistencePort persistence, Backend backend, LongSupplier clock)
	{
		_goals = Objects.requireNonNull(goals);
		_persistence = Objects.requireNonNull(persistence);
		_backend = Objects.requireNonNull(backend);
		_clock = Objects.requireNonNull(clock);
	}

	public synchronized boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}
		_state = State.RUNNING;
		return true;
	}

	public synchronized AdvanceResult advance(long profileId, long goalId, long goalRevision)
	{
		if (_state != State.RUNNING)
		{
			return result(OperationStatus.CANCELLED, "clan.service.not_running", null);
		}
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(profileId);
		if (stored.isEmpty() || (stored.get().goal().goalId() != goalId) || (stored.get().goal().revision() != goalRevision))
		{
			return result(OperationStatus.STALE, "clan.goal.stale", null);
		}
		final PhantomGoal goal = stored.get().goal();
		final OperationIdentity identity = new OperationIdentity(profileId, goalId, goalRevision);
		final AdvanceResult prior = _terminal.get(identity);
		if (prior != null)
		{
			return prior;
		}
		final long now = _clock.getAsLong();
		final Operation previous = _active.get(profileId);
		if ((previous != null) && !previous._identity.equals(identity) && JOIN_GOAL.equals(previous._goal.goalType()))
		{
			refuseCurrentJoinInvitation(previous);
			terminalize(previous, OperationStatus.CANCELLED, "clan.goal.replaced", null);
		}
		if (!validCommon(profileId, goal, now))
		{
			if (JOIN_GOAL.equals(goal.goalType()) && (now >= goal.deadlineEpochMillis()))
			{
				final Operation active = _active.get(profileId);
				final Operation expired = ((active != null) && active._identity.equals(identity)) ? active : new Operation(identity, goal);
				refuseCurrentJoinInvitation(expired);
				return terminalize(expired, OperationStatus.EXPIRED, "clan.goal.invalid", null);
			}
			return result(now >= goal.deadlineEpochMillis() ? OperationStatus.EXPIRED : OperationStatus.FAILED, "clan.goal.invalid", null);
		}

		Operation operation = _active.get(profileId);
		if ((operation != null) && !operation._identity.equals(identity))
		{
			cancelPending(operation);
			terminalize(operation, OperationStatus.CANCELLED, "clan.goal.replaced", null);
			operation = null;
		}
		if (operation == null)
		{
			if (_active.size() >= MAX_ACTIVE_OPERATIONS)
			{
				return result(OperationStatus.FAILED, "clan.operation.capacity", null);
			}
			operation = new Operation(identity, goal);
			_active.put(profileId, operation);
		}
		return switch (goal.goalType())
		{
			case BUILD_GOAL -> advanceBuild(operation);
			case JOIN_GOAL -> advanceJoin(operation);
			case ROLE_GOAL -> advanceRole(operation);
			case CONTRIBUTE_GOAL -> advanceContribution(operation);
			case CHAT_GOAL -> advanceChat(operation);
			default -> terminalize(operation, OperationStatus.FAILED, "clan.goal.unsupported", null);
		};
	}

	public synchronized boolean cancel(long profileId, long goalId, long goalRevision, String reasonKey)
	{
		final Operation operation = _active.get(profileId);
		if ((operation == null) || (operation._identity.goalId() != goalId) || (operation._identity.goalRevision() != goalRevision))
		{
			return false;
		}
		refuseCurrentJoinInvitation(operation);
		cancelPending(operation);
		terminalize(operation, OperationStatus.CANCELLED, reasonKey == null ? "clan.operation.cancelled" : reasonKey, null);
		return true;
	}

	public synchronized ChatResult postClanChat(long profileId, long goalId, long goalRevision, String text)
	{
		if ((_state != State.RUNNING) || (text == null) || text.isBlank() || (text.length() > MAX_CHAT_TEXT))
		{
			return new ChatResult(ChatOutcome.REJECTED, 0);
		}
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(profileId);
		if (stored.isEmpty())
		{
			return new ChatResult(ChatOutcome.STALE, 0);
		}
		final PhantomGoal goal = stored.get().goal();
		if ((goal.goalId() != goalId) || (goal.revision() != goalRevision) || !validCommon(profileId, goal, _clock.getAsLong()) || !CHAT_GOAL.equals(goal.goalType()) || !validChatContract(goal, text))
		{
			return new ChatResult(ChatOutcome.STALE, 0);
		}
		final MemberRef actor = _backend.currentMember(profileId).orElse(null);
		final ClanSnapshot clan = actor == null ? null : _backend.observe(actor).orElse(null);
		if ((clan == null) || !matches(goal.target(), clan))
		{
			return new ChatResult(ChatOutcome.STALE, 0);
		}
		final ChatIdentity identity = new ChatIdentity(profileId, goalId, goalRevision, text);
		final ChatResult prior = _chatReceipts.get(identity);
		if (prior != null)
		{
			return prior;
		}
		final ChatResult sent = _backend.clanChat(actor, clan.clanId(), text);
		if (sent.outcome() == ChatOutcome.DELIVERED)
		{
			putBounded(_chatReceipts, identity, sent);
		}
		return sent;
	}

	public WithdrawalOutcome withdraw(long profileId, int expectedClanId, int warehouseObjectId, long count)
	{
		final MemberRef member = _backend.currentMember(profileId).orElse(null);
		return member == null ? WithdrawalOutcome.UNSUPPORTED : _backend.withdraw(member, expectedClanId, warehouseObjectId, count);
	}

	public synchronized void beginStop()
	{
		if ((_state == State.STOPPING) || (_state == State.STOPPED))
		{
			return;
		}
		_state = State.STOPPING;
		for (Operation operation : List.copyOf(_active.values()))
		{
			refuseCurrentJoinInvitation(operation);
			cancelPending(operation);
			terminalize(operation, OperationStatus.CANCELLED, "clan.service.stopping", null);
		}
	}

	public synchronized boolean finishStop()
	{
		if (_state == State.STOPPED)
		{
			return true;
		}
		if ((_state != State.STOPPING) || !_active.isEmpty())
		{
			return false;
		}
		_state = State.STOPPED;
		return true;
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_state, _active.size(), _terminal.size(), _chatReceipts.size());
	}

	private AdvanceResult advanceBuild(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if ((goal.target() == null) || !"clan.name".equals(goal.target().namespace()) || !validCandidateSources(goal.validSources()))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.build.contract", null);
		}
		final MemberRef actor = _backend.currentMember(operation._identity.profileId()).orElse(null);
		if ((actor == null) || (actor.kind() != MemberKind.PHANTOM))
		{
			return result(OperationStatus.STALE, "clan.build.actor.stale", null);
		}
		ClanSnapshot clan = _backend.observe(actor).orElse(null);
		if (clan == null)
		{
			final CreationResult created = _backend.create(actor, goal.target().key());
			if ((created.outcome() != CreationOutcome.CREATED) && (created.outcome() != CreationOutcome.ALREADY_SATISFIED))
			{
				return terminalize(operation, OperationStatus.FAILED, "clan.build." + created.outcome().name().toLowerCase(), null);
			}
			clan = created.clan();
			if ((clan == null) || !matches(goal.target(), clan))
			{
				return terminalize(operation, OperationStatus.FAILED, "clan.build.canonical_mismatch", null);
			}
			persist(actor.profileId(), metadata(goal, actor, clan, RoleKey.LEADER, 0, goal.validSources()));
			return result(OperationStatus.REPLAN, "clan.build.created", receipt(operation, clan, actor, actor, 0));
		}
		if (!matches(goal.target(), clan))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.build.actor_in_other_clan", null);
		}
		if (reconcile(actor, clan, goal))
		{
			return result(OperationStatus.REPLAN, "clan.metadata.stale", null);
		}

		if (operation._pendingIdentity != null)
		{
			final Optional<InvitationSnapshot> current = _backend.observeInvitation(operation._pendingCandidate);
			if (current.isPresent() && current.get().identity().equals(operation._pendingIdentity))
			{
				return result(OperationStatus.WAITING, "clan.build.waiting_consent", receipt(operation, clan, actor, operation._pendingCandidate, 0));
			}
			final ClanSnapshot candidateClan = _backend.observe(operation._pendingCandidate).orElse(null);
			operation._candidateIndex++;
			operation._pendingCandidate = null;
			operation._pendingIdentity = null;
			if ((candidateClan != null) && (candidateClan.clanId() == clan.clanId()))
			{
				return result(OperationStatus.REPLAN, "clan.build.member_joined", receipt(operation, clan, actor, actor, 0));
			}
		}

		while (operation._candidateIndex < goal.validSources().size())
		{
			final PhantomDomainRef source = goal.validSources().get(operation._candidateIndex);
			final MemberRef candidate = _backend.resolve(source).orElse(null);
			if ((candidate == null) || (candidate.characterObjectId() == actor.characterObjectId()))
			{
				operation._candidateIndex++;
				continue;
			}
			final ClanSnapshot candidateClan = _backend.observe(candidate).orElse(null);
			if (candidateClan != null)
			{
				operation._candidateIndex++;
				continue;
			}
			if (clan.memberCount() >= clan.maxMembers())
			{
				return terminalize(operation, OperationStatus.COMPLETE, "clan.build.capacity_reached", receipt(operation, clan, actor, actor, 0));
			}
			final ClanInvitationService.InviteResult invited = _backend.invite(actor, candidate);
			if (invited.delivered())
			{
				operation._pendingCandidate = candidate;
				operation._pendingIdentity = invited.identity();
				return result(OperationStatus.WAITING, "clan.build.invitation_delivered", receipt(operation, clan, actor, candidate, 0));
			}
			operation._candidateIndex++;
		}
		return terminalize(operation, OperationStatus.COMPLETE, "clan.build.complete", receipt(operation, clan, actor, actor, 0));
	}

	private AdvanceResult advanceJoin(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if ((goal.target() == null) || !SetTarget.valid(goal.target()) || !goal.validSources().isEmpty())
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.join.contract", null);
		}
		final MemberRef actor = _backend.currentMember(operation._identity.profileId()).orElse(null);
		if ((actor == null) || (actor.kind() != MemberKind.PHANTOM))
		{
			return result(OperationStatus.STALE, "clan.join.actor.stale", null);
		}
		final ClanSnapshot currentClan = _backend.observe(actor).orElse(null);
		if (currentClan != null)
		{
			if (!matches(goal.target(), currentClan))
			{
				return terminalize(operation, OperationStatus.FAILED, "clan.join.other_clan", null);
			}
			if (reconcile(actor, currentClan, goal))
			{
				return result(OperationStatus.REPLAN, "clan.metadata.stale", null);
			}
			return terminalize(operation, OperationStatus.COMPLETE, "clan.join.complete", receipt(operation, currentClan, actor, actor, 0));
		}
		final InvitationSnapshot invitation = _backend.observeInvitation(actor).orElse(null);
		if (invitation == null)
		{
			return result(OperationStatus.WAITING, "clan.join.waiting_matching_invite", null);
		}
		if (!matches(goal.target(), invitation))
		{
			final ClanInvitationService.RespondResult refused = _backend.respond(actor, ClanInvitationService.Response.REFUSE, invitation.identity());
			return result(OperationStatus.REPLAN, refused.outcome() == ClanInvitationService.RespondOutcome.REFUSED ? "clan.join.mismatched_invite_refused" : "clan.join.invite_stale", null);
		}
		final ClanInvitationService.RespondResult accepted = _backend.respond(actor, ClanInvitationService.Response.ACCEPT, invitation.identity());
		if (!accepted.accepted())
		{
			return result(OperationStatus.REPLAN, "clan.join." + accepted.outcome().name().toLowerCase(), null);
		}
		final ClanSnapshot joined = _backend.observe(actor).orElse(null);
		if ((joined == null) || !matches(goal.target(), joined))
		{
			return result(OperationStatus.REPLAN, "clan.join.canonical_pending", null);
		}
		persist(actor.profileId(), metadata(goal, actor, joined, defaultRole(actor, joined), 0, List.of()));
		return terminalize(operation, OperationStatus.COMPLETE, "clan.join.complete", receipt(operation, joined, actor, actor, 0));
	}

	private AdvanceResult advanceChat(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		final String text = goal.acquisitionMethod();
		if (!validChatContract(goal, text))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.chat.contract", null);
		}
		final MemberRef actor = _backend.currentMember(operation._identity.profileId()).orElse(null);
		if ((actor == null) || (actor.kind() != MemberKind.PHANTOM))
		{
			return result(OperationStatus.STALE, "clan.chat.actor.stale", null);
		}
		final ClanSnapshot clan = _backend.observe(actor).orElse(null);
		if ((clan == null) || !matches(goal.target(), clan))
		{
			return result(OperationStatus.STALE, "clan.chat.clan.stale", null);
		}
		final ChatResult chat = postClanChat(operation._identity.profileId(), operation._identity.goalId(), operation._identity.goalRevision(), text);
		return switch (chat.outcome())
		{
			case DELIVERED -> terminalize(operation, OperationStatus.COMPLETE, "clan.chat.delivered", receipt(operation, clan, actor, actor, chat.deliveries()));
			case REJECTED -> terminalize(operation, OperationStatus.FAILED, "clan.chat.rejected", null);
			case STALE -> result(OperationStatus.STALE, "clan.chat.stale", null);
			case FAILED -> terminalize(operation, OperationStatus.FAILED, "clan.chat.failed", null);
		};
	}

	private AdvanceResult advanceRole(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		final RoleKey desired = parseRole(goal.acquisitionMethod());
		if ((goal.target() == null) || !SetTarget.valid(goal.target()) || (desired == null) || (goal.validSources().size() > 1))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.role.contract", null);
		}
		final MemberRef requester = _backend.currentMember(operation._identity.profileId()).orElse(null);
		final MemberRef target = goal.validSources().isEmpty() ? requester : _backend.resolve(goal.validSources().getFirst()).orElse(null);
		if ((requester == null) || (target == null) || (target.kind() != MemberKind.PHANTOM))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.role.phantom_target_required", null);
		}
		final ClanSnapshot requesterClan = _backend.observe(requester).orElse(null);
		final ClanSnapshot targetClan = _backend.observe(target).orElse(null);
		if ((requesterClan == null) || (targetClan == null) || (requesterClan.clanId() != targetClan.clanId()) || !matches(goal.target(), requesterClan))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.role.membership_mismatch", null);
		}
		if (reconcile(target, targetClan, goal))
		{
			return result(OperationStatus.REPLAN, "clan.metadata.stale", null);
		}
		ClanSnapshot canonical = targetClan;
		if ((desired == RoleKey.LEADER) && (targetClan.leaderObjectId() != target.characterObjectId()))
		{
			final RoleResult changed = _backend.transferLeader(requester, target, requesterClan.clanId());
			if ((changed.outcome() != RoleOutcome.COMPLETED) && (changed.outcome() != RoleOutcome.ALREADY_SATISFIED))
			{
				return terminalize(operation, OperationStatus.FAILED, "clan.role." + changed.outcome().name().toLowerCase(), null);
			}
			canonical = _backend.observe(target).orElse(null);
			if ((canonical == null) || (canonical.leaderObjectId() != target.characterObjectId()))
			{
				return result(OperationStatus.REPLAN, "clan.role.canonical_pending", null);
			}
		}
		final OrganizationMetadata previous = _persistence.load(target.profileId()).map(StoredMetadata::metadata).orElse(null);
		final long budget = previous == null ? 0 : previous.contributionBudget();
		persist(target.profileId(), metadata(goal, target, canonical, desired, budget, goal.validSources()));
		return terminalize(operation, OperationStatus.COMPLETE, "clan.role.complete", receipt(operation, canonical, requester, target, 0));
	}

	private AdvanceResult advanceContribution(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if ((goal.target() == null) || !SetTarget.valid(goal.target()) || (goal.validSources().size() != 1) || !"item.object".equals(goal.validSources().getFirst().namespace()) || (goal.requiredAmount() <= 0) || (goal.requiredAmount() > MAX_CONTRIBUTION_COUNT) || (goal.expenseBudget() < goal.requiredAmount()))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.contribute.contract", null);
		}
		final long objectId = parsePositive(goal.validSources().getFirst().key());
		if (objectId > Integer.MAX_VALUE)
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.contribute.source_invalid", null);
		}
		final MemberRef actor = _backend.currentMember(operation._identity.profileId()).orElse(null);
		final ClanSnapshot clan = actor == null ? null : _backend.observe(actor).orElse(null);
		if ((actor == null) || (clan == null) || !matches(goal.target(), clan))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.contribute.membership_mismatch", null);
		}
		if (reconcile(actor, clan, goal))
		{
			return result(OperationStatus.REPLAN, "clan.metadata.stale", null);
		}
		OrganizationMetadata previous = _persistence.load(actor.profileId()).map(StoredMetadata::metadata).orElse(null);
		final int sourceObjectId = (int) objectId;
		if ((previous != null) && (previous.organizationGoalId() == goal.goalId()) && (previous.goalRevision() == goal.revision()) && (previous.contributionItemObjectId() == sourceObjectId) && (previous.contributionAmount() == goal.requiredAmount()))
		{
			if (previous.contributionState() == ContributionState.COMPLETED)
			{
				return terminalize(operation, OperationStatus.COMPLETE, "clan.contribute.restart_reconciled", receipt(operation, clan, actor, actor, 0));
			}
			if (previous.contributionState() == ContributionState.PREPARED)
			{
				final ContributionObservation observed = _backend.observeContribution(actor, clan.clanId(), sourceObjectId);
				if (!observed.available())
				{
					return terminalize(operation, OperationStatus.FAILED, "clan.contribute.prepared_source_stale", null);
				}
				final boolean transferred = (observed.inventoryCount() == (previous.contributionInventoryBefore() - goal.requiredAmount())) && (observed.warehouseCount() >= (previous.contributionWarehouseBefore() + goal.requiredAmount()));
				if (transferred)
				{
					persist(actor.profileId(), contributionMetadata(previous, ContributionState.COMPLETED));
					return terminalize(operation, OperationStatus.COMPLETE, "clan.contribute.restart_observed", receipt(operation, clan, actor, actor, 0));
				}
				if ((observed.inventoryCount() != previous.contributionInventoryBefore()) || (observed.warehouseCount() != previous.contributionWarehouseBefore()))
				{
					return terminalize(operation, OperationStatus.FAILED, "clan.contribute.prepared_inconsistent", null);
				}
			}
		}
		else
		{
			final ContributionObservation baseline = _backend.observeContribution(actor, clan.clanId(), sourceObjectId);
			if (!baseline.available() || (baseline.inventoryCount() < goal.requiredAmount()))
			{
				return terminalize(operation, OperationStatus.FAILED, "clan.contribute.source_missing", null);
			}
			final RoleKey role = previous == null ? defaultRole(actor, clan) : previous.roleIntent();
			previous = metadata(goal, actor, clan, role, goal.expenseBudget(), goal.validSources());
			previous = new OrganizationMetadata(previous.canonicalClanId(), previous.clanName(), previous.canonicalLeaderObjectId(), previous.roleIntent(), previous.organizationGoalId(), previous.goalRevision(), previous.contributionBudget(), sourceObjectId, goal.requiredAmount(), baseline.inventoryCount(), baseline.warehouseCount(), ContributionState.PREPARED, previous.relationReferences(), previous.canonicalEvidenceHash(), previous.intentEvidenceHash(), previous.updatedEpochMillis());
			persist(actor.profileId(), previous);
		}
		final ContributionResult contributed = _backend.contribute(actor, clan.clanId(), sourceObjectId, goal.requiredAmount());
		if (!contributed.exact(goal.requiredAmount()))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.contribute." + contributed.outcome().name().toLowerCase(), null);
		}
		persist(actor.profileId(), contributionMetadata(previous, ContributionState.COMPLETED));
		return terminalize(operation, OperationStatus.COMPLETE, "clan.contribute.complete", receipt(operation, clan, actor, actor, goal.requiredAmount()));
	}

	private boolean reconcile(MemberRef member, ClanSnapshot clan, PhantomGoal goal)
	{
		if (member.kind() != MemberKind.PHANTOM)
		{
			return false;
		}
		final Optional<StoredMetadata> stored = _persistence.load(member.profileId());
		if (stored.isEmpty())
		{
			persist(member.profileId(), metadata(goal, member, clan, defaultRole(member, clan), 0, goal.validSources()));
			return false;
		}
		final OrganizationMetadata current = stored.get().metadata();
		if ((current.canonicalClanId() == clan.clanId()) && current.canonicalEvidenceHash().equals(clan.evidenceHash()))
		{
			return false;
		}
		final RoleKey reconciledRole = clan.leaderObjectId() == member.characterObjectId() ? RoleKey.LEADER : current.roleIntent() == RoleKey.LEADER ? RoleKey.MEMBER : current.roleIntent();
		persist(member.profileId(), new OrganizationMetadata(clan.clanId(), clan.clanName(), clan.leaderObjectId(), reconciledRole, goal.goalId(), goal.revision(), current.contributionBudget(), current.contributionItemObjectId(), current.contributionAmount(), current.contributionInventoryBefore(), current.contributionWarehouseBefore(), current.contributionState(), current.relationReferences(), clan.evidenceHash(), intentHash(goal, member, reconciledRole, current.contributionBudget()), _clock.getAsLong()));
		return true;
	}

	private OrganizationMetadata metadata(PhantomGoal goal, MemberRef member, ClanSnapshot clan, RoleKey role, long budget, List<PhantomDomainRef> references)
	{
		final List<String> relations = references.stream().map(reference -> reference.namespace() + ":" + reference.key()).toList();
		return new OrganizationMetadata(clan.clanId(), clan.clanName(), clan.leaderObjectId(), role, goal.goalId(), goal.revision(), budget, 0, 0, 0, 0, ContributionState.NONE, relations, clan.evidenceHash(), intentHash(goal, member, role, budget), _clock.getAsLong());
	}

	private OrganizationMetadata contributionMetadata(OrganizationMetadata value, ContributionState state)
	{
		return new OrganizationMetadata(value.canonicalClanId(), value.clanName(), value.canonicalLeaderObjectId(), value.roleIntent(), value.organizationGoalId(), value.goalRevision(), value.contributionBudget(), value.contributionItemObjectId(), value.contributionAmount(), value.contributionInventoryBefore(), value.contributionWarehouseBefore(), state, value.relationReferences(), value.canonicalEvidenceHash(), value.intentEvidenceHash(), _clock.getAsLong());
	}
	private void persist(long profileId, OrganizationMetadata metadata)
	{
		final Optional<StoredMetadata> stored = _persistence.load(profileId);
		_persistence.save(profileId, stored.map(StoredMetadata::rowVersion).orElse(-1L), metadata);
	}

	private void refuseCurrentJoinInvitation(Operation operation)
	{
		if (JOIN_GOAL.equals(operation._goal.goalType()))
		{
			refuseCurrentJoinInvitation(operation._identity.profileId());
		}
	}

	private void refuseCurrentJoinInvitation(long profileId)
	{
		final MemberRef actor = _backend.currentMember(profileId).orElse(null);
		if ((actor == null) || (actor.kind() != MemberKind.PHANTOM))
		{
			return;
		}
		final InvitationSnapshot invitation = _backend.observeInvitation(actor).orElse(null);
		if (invitation != null)
		{
			_backend.respond(actor, ClanInvitationService.Response.REFUSE, invitation.identity());
		}
	}

	private void cancelPending(Operation operation)
	{
		if (operation._pendingIdentity != null)
		{
			_backend.cancel(operation._pendingIdentity);
			operation._pendingIdentity = null;
			operation._pendingCandidate = null;
		}
	}

	private AdvanceResult terminalize(Operation operation, OperationStatus status, String reasonKey, Receipt receipt)
	{
		final AdvanceResult terminal = result(status, reasonKey, receipt);
		_active.remove(operation._identity.profileId(), operation);
		putBounded(_terminal, operation._identity, terminal);
		return terminal;
	}

	private static AdvanceResult result(OperationStatus status, String reasonKey, Receipt receipt)
	{
		return new AdvanceResult(status, reasonKey, receipt);
	}

	private static Receipt receipt(Operation operation, ClanSnapshot clan, MemberRef actor, MemberRef subject, long delta)
	{
		return new Receipt(operation._identity.profileId(), operation._identity.goalId(), operation._identity.goalRevision(), operation._goal.goalType(), clan.clanId(), actor.characterObjectId(), subject.characterObjectId(), delta, clan.evidenceHash());
	}

	private static boolean validChatContract(PhantomGoal goal, String text)
	{
		return (goal.target() != null) && SetTarget.valid(goal.target()) && goal.validSources().isEmpty() && (goal.constraints().size() == 1) && (text != null) && !text.isBlank() && (text.length() <= MAX_CHAT_TEXT) && Objects.equals(goal.constraints().get(CHAT_TEXT_CONSTRAINT), (long) text.length()) && Objects.equals(goal.acquisitionMethod(), text);
	}

	private static boolean validCommon(long profileId, PhantomGoal goal, long now)
	{
		if ((goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.deadlineEpochMillis() <= now) || !List.of(BUILD_GOAL, JOIN_GOAL, ROLE_GOAL, CONTRIBUTE_GOAL, CHAT_GOAL).contains(goal.goalType()))
		{
			return false;
		}
		return (goal.subject() == null) || ("profile".equals(goal.subject().namespace()) && (parsePositive(goal.subject().key()) == profileId));
	}

	private static boolean validCandidateSources(List<PhantomDomainRef> sources)
	{
		return (sources.size() <= 16) && sources.stream().allMatch(source -> ("profile".equals(source.namespace()) || "character.object".equals(source.namespace())) && (parsePositive(source.key()) > 0));
	}

	private static boolean matches(PhantomDomainRef target, ClanSnapshot clan)
	{
		return (target != null) && (("clan.id".equals(target.namespace()) && (parsePositive(target.key()) == clan.clanId())) || ("clan.name".equals(target.namespace()) && target.key().equalsIgnoreCase(clan.clanName())));
	}

	private static boolean matches(PhantomDomainRef target, InvitationSnapshot invitation)
	{
		return (target != null) && (("clan.id".equals(target.namespace()) && (parsePositive(target.key()) == invitation.identity().clanId())) || ("clan.name".equals(target.namespace()) && target.key().equalsIgnoreCase(invitation.clanName())));
	}

	private static RoleKey defaultRole(MemberRef member, ClanSnapshot clan)
	{
		return clan.leaderObjectId() == member.characterObjectId() ? RoleKey.LEADER : RoleKey.MEMBER;
	}

	private static RoleKey parseRole(String value)
	{
		try
		{
			return value == null ? null : RoleKey.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException exception)
		{
			return null;
		}
	}

	private static long parsePositive(String value)
	{
		try
		{
			final long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : -1;
		}
		catch (RuntimeException exception)
		{
			return -1;
		}
	}

	private static String intentHash(PhantomGoal goal, MemberRef member, RoleKey role, long budget)
	{
		return sha256(goal.goalId() + "|" + goal.revision() + "|" + member.stableKey() + "|" + role + "|" + budget + "|" + goal.validSources());
	}

	public static String sha256(String value)
	{
		try
		{
			final byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().withUpperCase().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException(exception);
		}
	}

	private static boolean hash(String value)
	{
		return (value != null) && value.matches("[0-9A-F]{64}");
	}

	private static String requireKey(String value)
	{
		if ((value == null) || !value.matches("[a-z0-9_.-]{1,96}"))
		{
			throw new IllegalArgumentException("Invalid clan reason key.");
		}
		return value;
	}

	private static String requireReference(String value)
	{
		if ((value == null) || value.isBlank() || (value.length() > 160))
		{
			throw new IllegalArgumentException("Invalid clan relation reference.");
		}
		return value;
	}

	private static <K, V> void putBounded(LinkedHashMap<K, V> map, K key, V value)
	{
		map.put(key, value);
		while (map.size() > MAX_TERMINAL_RECEIPTS)
		{
			map.remove(map.keySet().iterator().next());
		}
	}

	private record OperationIdentity(long profileId, long goalId, long goalRevision)
	{
	}

	private record ChatIdentity(long profileId, long goalId, long goalRevision, String text)
	{
	}

	private static final class Operation
	{
		private final OperationIdentity _identity;
		private final PhantomGoal _goal;
		private int _candidateIndex;
		private MemberRef _pendingCandidate;
		private InvitationIdentity _pendingIdentity;

		private Operation(OperationIdentity identity, PhantomGoal goal)
		{
			_identity = identity;
			_goal = goal;
		}
	}

	private static final class SetTarget
	{
		private static boolean valid(PhantomDomainRef target)
		{
			return ("clan.id".equals(target.namespace()) && (parsePositive(target.key()) > 0)) || ("clan.name".equals(target.namespace()) && !target.key().isBlank());
		}
	}
}
