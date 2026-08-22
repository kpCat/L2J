/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceMembershipProof;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService.InvitationSnapshot;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.clan.ClanWarService.WarIdentity;
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
	public static final String ALLIANCE_CREATE_GOAL = "clan.alliance.create";
	public static final String ALLIANCE_JOIN_GOAL = "clan.alliance.join";
	public static final String ALLIANCE_LEAVE_GOAL = "clan.alliance.leave";
	public static final String ALLIANCE_DISSOLVE_GOAL = "clan.alliance.dissolve";
	public static final String WAR_DECLARE_GOAL = "clan.war.declare";
	public static final String WAR_STOP_GOAL = "clan.war.stop";
	public static final String WAR_PEACE_GOAL = "clan.war.peace";
	public static final String ALLIANCE_CHAT_GOAL = "clan.alliance.chat";
	public static final String CHAT_TEXT_CONSTRAINT = "text";
	public static final int MAX_ACTIVE_OPERATIONS = 64;
	public static final int MAX_TERMINAL_RECEIPTS = 256;
	public static final int MAX_RELATION_REFERENCES = 16;
	public static final long MAX_CONTRIBUTION_COUNT = 1_000_000_000L;
	public static final int MAX_CHAT_TEXT = 105;
	public static final int WAR_HOSTILITY_THRESHOLD = 600;
	public static final int PEACE_HOSTILITY_THRESHOLD = 250;

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

	public enum DiplomacyAction
	{
		NONE,
		ALLIANCE_CREATE,
		ALLIANCE_JOIN,
		ALLIANCE_LEAVE,
		ALLIANCE_DISSOLVE,
		WAR_DECLARE,
		WAR_STOP,
		WAR_PEACE,
		ALLIANCE_CHAT
	}

	public enum DiplomacyPhase
	{
		NONE,
		PREPARED,
		COMPLETED
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

	public record AllianceObservation(AllianceIdentity identity, String allianceName, int memberClanId)
	{
		public AllianceObservation
		{
			Objects.requireNonNull(identity);
			if ((allianceName == null) || allianceName.isBlank() || (memberClanId <= 0))
			{
				throw new IllegalArgumentException("Invalid alliance observation.");
			}
		}
	}

	public record RelationshipEvidence(boolean available, int hostilityScore, int affinityScore, List<String> hostileEventIds, String authorityHash)
	{
		public RelationshipEvidence
		{
			hostileEventIds = List.copyOf(Objects.requireNonNull(hostileEventIds));
			if ((hostileEventIds.size() > 8) || (authorityHash == null) || (available && !hash(authorityHash)))
			{
				throw new IllegalArgumentException("Invalid clan relationship evidence.");
			}
		}

		public boolean hostileForWar()
		{
			return available && (hostilityScore >= WAR_HOSTILITY_THRESHOLD) && !hostileEventIds.isEmpty();
		}

		public boolean peacefulEnough()
		{
			return available && (hostilityScore <= PEACE_HOSTILITY_THRESHOLD);
		}
	}

	public record DiplomacyState(DiplomacyAction action, DiplomacyPhase phase, long goalId, long goalRevision, int counterpartClanId, int allianceLeaderClanId, long allianceGeneration, long membershipCounter, long warId, long decisionEpoch, long cooldownUntilEpochMillis, long happenedEpochMinute, String evidenceHash)
	{
		public DiplomacyState
		{
			Objects.requireNonNull(action);
			Objects.requireNonNull(phase);
			if ((goalId < 0) || (goalRevision < 0) || (counterpartClanId < 0) || (allianceLeaderClanId < 0) || (allianceGeneration < 0) || (membershipCounter < 0) || (warId < 0) || (decisionEpoch < 0) || (cooldownUntilEpochMillis < 0) || (happenedEpochMinute < 0) || (evidenceHash == null) || (!evidenceHash.isEmpty() && !hash(evidenceHash)) || ((action == DiplomacyAction.NONE) != (phase == DiplomacyPhase.NONE)))
			{
				throw new IllegalArgumentException("Invalid Phantom clan diplomacy state.");
			}
		}

		public static DiplomacyState empty()
		{
			return new DiplomacyState(DiplomacyAction.NONE, DiplomacyPhase.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
		}

		public boolean sameGoal(PhantomGoal goal, DiplomacyAction expectedAction)
		{
			return (action == expectedAction) && (goalId == goal.goalId()) && (goalRevision == goal.revision());
		}
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

	public record OrganizationMetadata(int canonicalClanId, String clanName, int canonicalLeaderObjectId, RoleKey roleIntent, long organizationGoalId, long goalRevision, long contributionBudget, int contributionItemObjectId, long contributionAmount, long contributionInventoryBefore, long contributionWarehouseBefore, ContributionState contributionState, List<String> relationReferences, String canonicalEvidenceHash, String intentEvidenceHash, long updatedEpochMillis, DiplomacyState diplomacy)
	{
		public OrganizationMetadata(int canonicalClanId, String clanName, int canonicalLeaderObjectId, RoleKey roleIntent, long organizationGoalId, long goalRevision, long contributionBudget, int contributionItemObjectId, long contributionAmount, long contributionInventoryBefore, long contributionWarehouseBefore, ContributionState contributionState, List<String> relationReferences, String canonicalEvidenceHash, String intentEvidenceHash, long updatedEpochMillis)
		{
			this(canonicalClanId, clanName, canonicalLeaderObjectId, roleIntent, organizationGoalId, goalRevision, contributionBudget, contributionItemObjectId, contributionAmount, contributionInventoryBefore, contributionWarehouseBefore, contributionState, relationReferences, canonicalEvidenceHash, intentEvidenceHash, updatedEpochMillis, DiplomacyState.empty());
		}

		public OrganizationMetadata
		{
			if ((canonicalClanId <= 0) || (clanName == null) || clanName.isBlank() || (canonicalLeaderObjectId <= 0) || (roleIntent == null) || (organizationGoalId <= 0) || (goalRevision < 0) || (contributionBudget < 0) || (contributionItemObjectId < 0) || (contributionAmount < 0) || (contributionInventoryBefore < 0) || (contributionWarehouseBefore < 0) || (contributionState == null) || ((contributionState == ContributionState.NONE) && ((contributionItemObjectId != 0) || (contributionAmount != 0))) || ((contributionState != ContributionState.NONE) && ((contributionItemObjectId <= 0) || (contributionAmount <= 0))) || (relationReferences == null) || (relationReferences.size() > MAX_RELATION_REFERENCES) || !hash(canonicalEvidenceHash) || !hash(intentEvidenceHash) || (updatedEpochMillis < 0) || (diplomacy == null))
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

		default Optional<AllianceObservation> observeAlliance(MemberRef member)
		{
			return Optional.empty();
		}

		default ClanAllianceService.Result createAlliance(MemberRef actor, String allianceName)
		{
			return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.ACTOR_NOT_FOUND, null);
		}

		default ClanAllianceService.Result checkAllianceJoin(MemberRef inviter, MemberRef target)
		{
			return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.TARGET_NOT_FOUND, null);
		}

		default ClanAllianceService.Result joinAlliance(MemberRef inviter, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch)
		{
			return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.TARGET_NOT_FOUND, identity);
		}

		default ClanAllianceService.Result leaveAlliance(MemberRef actor, AllianceIdentity identity)
		{
			return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.ACTOR_NOT_FOUND, identity);
		}

		default ClanAllianceService.ProofResult captureAllianceMembership(AllianceIdentity identity)
		{
			return new ClanAllianceService.ProofResult(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.CLAN_NOT_FOUND, null);
		}

		default ClanAllianceService.Result dissolveAlliance(MemberRef actor, AllianceMembershipProof proof)
		{
			return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.ACTOR_NOT_FOUND, proof == null ? null : proof.identity());
		}

		default Optional<WarIdentity> currentWar(MemberRef first, MemberRef second)
		{
			return Optional.empty();
		}

		default ClanWarService.Result declareWar(MemberRef actor, MemberRef target)
		{
			return new ClanWarService.Result(ClanWarService.Status.INELIGIBLE, ClanWarService.Reason.ACTOR_NOT_FOUND, null);
		}

		default ClanWarService.Result stopWar(MemberRef actor, MemberRef target, long expectedWarId)
		{
			return new ClanWarService.Result(ClanWarService.Status.INELIGIBLE, ClanWarService.Reason.ACTOR_NOT_FOUND, null);
		}

		default ClanWarService.Result acceptPeace(MemberRef first, MemberRef second, WarIdentity identity)
		{
			return new ClanWarService.Result(ClanWarService.Status.INELIGIBLE, ClanWarService.Reason.ACTOR_NOT_FOUND, null);
		}

		default RelationshipEvidence relationship(long ownerProfileId, MemberRef subject, long nowEpochMinute)
		{
			return new RelationshipEvidence(false, 0, 0, List.of(), "");
		}

		default boolean recordRelation(long ownerProfileId, MemberRef subject, String eventKey, String operationId, String evidenceHash, long happenedEpochMinute)
		{
			return true;
		}

		default long pvpPairCooldownMillis()
		{
			return 1_000;
		}

		default ChatResult allianceChat(MemberRef member, AllianceIdentity expectedIdentity, String text)
		{
			return new ChatResult(ChatOutcome.FAILED, 0);
		}
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
	private final LinkedHashMap<ConsentKey, JoinOffer> _joinOffers = new LinkedHashMap<>();
	private final LinkedHashMap<ConsentKey, PeaceOffer> _peaceOffers = new LinkedHashMap<>();
	private long _advanceSequence;
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
		_advanceSequence = Math.addExact(_advanceSequence, 1);
		pruneConsentOffers(_clock.getAsLong());
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
			case ALLIANCE_CREATE_GOAL -> advanceAllianceCreate(operation);
			case ALLIANCE_JOIN_GOAL -> advanceAllianceJoin(operation);
			case ALLIANCE_LEAVE_GOAL -> advanceAllianceLeave(operation);
			case ALLIANCE_DISSOLVE_GOAL -> advanceAllianceDissolve(operation);
			case WAR_DECLARE_GOAL -> advanceWarDeclare(operation);
			case WAR_STOP_GOAL -> advanceWarStop(operation);
			case WAR_PEACE_GOAL -> advanceWarPeace(operation);
			case ALLIANCE_CHAT_GOAL -> advanceAllianceChat(operation);
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
		_joinOffers.clear();
		_peaceOffers.clear();
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

	private AdvanceResult advanceAllianceCreate(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if ((goal.target() == null) || !"alliance.name".equals(goal.target().namespace()) || !goal.validSources().isEmpty() || (goal.acquisitionMethod() != null) || !goal.constraints().isEmpty())
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.alliance.create.contract", null);
		}
		final MemberRef actor = managedActor(operation);
		final ClanSnapshot clan = actor == null ? null : _backend.observe(actor).orElse(null);
		if ((actor == null) || (clan == null) || (clan.leaderObjectId() != actor.characterObjectId()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.alliance.create.managed_leader_required", null);
		}
		final OrganizationMetadata metadata = organization(actor, clan, goal);
		final DiplomacyState prior = metadata.diplomacy();
		final AllianceObservation current = _backend.observeAlliance(actor).orElse(null);
		if (current != null)
		{
			if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_CREATE) && (prior.allianceGeneration() > 0) && !allianceIdentity(prior).equals(current.identity()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.create.incarnation_changed", null);
			}
			if (!goal.target().key().equalsIgnoreCase(current.allianceName()) || (current.identity().leaderClanId() != clan.clanId()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.create.other_incarnation", null);
			}
			final DiplomacyState completed = diplomacy(goal, DiplomacyAction.ALLIANCE_CREATE, DiplomacyPhase.COMPLETED, 0, current.identity(), 0, 0, prior, sha256("alliance.create|" + goal.goalId() + "|" + goal.revision() + "|" + current.identity()));
			persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
			return terminalize(operation, OperationStatus.COMPLETE, prior.phase() == DiplomacyPhase.PREPARED ? "clan.alliance.create.restart_reconciled" : "clan.alliance.create.complete", receipt(operation, clan, actor, actor, current.identity().generation()));
		}
		if (inverseSuppressed(prior, DiplomacyAction.ALLIANCE_CREATE, 0))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_CREATE, DiplomacyPhase.PREPARED, 0, null, 0, 0, prior, sha256("alliance.create.prepare|" + goal.goalId() + "|" + goal.revision() + "|" + goal.target().key()));
		persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
		final ClanAllianceService.Result created = _backend.createAlliance(actor, goal.target().key());
		if (!created.successful())
		{
			return nativeAllianceFailure(operation, "clan.alliance.create", created);
		}
		final AllianceObservation observed = _backend.observeAlliance(actor).orElse(null);
		if ((observed == null) || !created.identity().equals(observed.identity()) || !goal.target().key().equalsIgnoreCase(observed.allianceName()))
		{
			return result(OperationStatus.REPLAN, "clan.alliance.create.canonical_pending", null);
		}
		final DiplomacyState completed = diplomacy(goal, DiplomacyAction.ALLIANCE_CREATE, DiplomacyPhase.COMPLETED, 0, observed.identity(), 0, 0, prepared, prepared.evidenceHash());
		persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
		return terminalize(operation, OperationStatus.COMPLETE, "clan.alliance.create.complete", receipt(operation, clan, actor, actor, observed.identity().generation()));
	}

	private AdvanceResult advanceAllianceJoin(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if (!validPeerGoal(goal))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.alliance.join.contract", null);
		}
		final MemberRef actor = managedActor(operation);
		final MemberRef peer = actor == null ? null : managedPeer(goal, actor);
		final ClanSnapshot actorClan = actor == null ? null : _backend.observe(actor).orElse(null);
		final ClanSnapshot peerClan = peer == null ? null : _backend.observe(peer).orElse(null);
		if ((actor == null) || (peer == null) || (actorClan == null) || (peerClan == null) || (actorClan.clanId() == peerClan.clanId()) || !matches(goal.target(), peerClan) || (actorClan.leaderObjectId() != actor.characterObjectId()) || (peerClan.leaderObjectId() != peer.characterObjectId()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.alliance.join.managed_leaders_required", null);
		}
		final OrganizationMetadata metadata = organization(actor, actorClan, goal);
		final DiplomacyState prior = metadata.diplomacy();
		final AllianceObservation actorAlliance = _backend.observeAlliance(actor).orElse(null);
		final AllianceObservation peerAlliance = _backend.observeAlliance(peer).orElse(null);
		if ((actorAlliance != null) && (peerAlliance != null) && actorAlliance.identity().equals(peerAlliance.identity()))
		{
			if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_JOIN) && (prior.allianceGeneration() > 0) && !allianceIdentity(prior).equals(actorAlliance.identity()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.join.incarnation_changed", null);
			}
			if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_JOIN) && (prior.phase() == DiplomacyPhase.COMPLETED))
			{
				return completeRelation(operation, actor, peer, actorClan, metadata, prior, "agreement.fulfilled", "clan.alliance.join.restart_reconciled");
			}
			final DiplomacyState completed = diplomacy(goal, DiplomacyAction.ALLIANCE_JOIN, DiplomacyPhase.COMPLETED, peerClan.clanId(), actorAlliance.identity(), 0, 0, prior, sha256("alliance.join.observe|" + goal.goalId() + "|" + goal.revision() + "|" + actorAlliance.identity()));
			persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
			return terminalize(operation, OperationStatus.COMPLETE, "clan.alliance.join.complete", receipt(operation, actorClan, actor, peer, actorAlliance.identity().generation()));
		}
		if ((actorAlliance != null) && (actorAlliance.identity().leaderClanId() == actorClan.clanId()) && (peerAlliance == null))
		{
			final ConsentKey key = new ConsentKey(actorClan.clanId(), peerClan.clanId());
			final JoinOffer existing = _joinOffers.get(key);
			if ((existing != null) && existing.sourceGoal().equals(operation._identity) && (_clock.getAsLong() < existing.expiresEpochMillis()))
			{
				return result(OperationStatus.WAITING, "clan.alliance.join.offer_pending", receipt(operation, actorClan, actor, peer, existing.identity().generation()));
			}
			final ClanAllianceService.Result checked = _backend.checkAllianceJoin(actor, peer);
			if (!checked.successful() || (checked.identity() == null) || (checked.targetEpoch() == null))
			{
				return nativeAllianceFailure(operation, "clan.alliance.join.offer", checked);
			}
			putBoundedOffer(_joinOffers, key, new JoinOffer(actor, peer, checked.identity(), checked.targetEpoch(), operation._identity, _advanceSequence, goal.deadlineEpochMillis()));
			return result(OperationStatus.WAITING, "clan.alliance.join.offer_published", receipt(operation, actorClan, actor, peer, checked.identity().generation()));
		}
		if ((actorAlliance == null) && (peerAlliance != null) && (peerAlliance.identity().leaderClanId() == peerClan.clanId()))
		{
			final ConsentKey key = new ConsentKey(peerClan.clanId(), actorClan.clanId());
			final JoinOffer offer = _joinOffers.get(key);
			if ((offer == null) || (_advanceSequence <= offer.sequence()) || !offer.source().equals(peer) || !offer.target().equals(actor) || !offer.identity().equals(peerAlliance.identity()) || (_clock.getAsLong() >= offer.expiresEpochMillis()))
			{
				return result(OperationStatus.WAITING, "clan.alliance.join.waiting_exact_offer", null);
			}
			if (inverseSuppressed(prior, DiplomacyAction.ALLIANCE_JOIN, peerClan.clanId()))
			{
				return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
			}
			final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_JOIN, DiplomacyPhase.PREPARED, peerClan.clanId(), offer.identity(), offer.targetEpoch().counter(), 0, prior, sha256("alliance.join|" + offer.sourceGoal() + "|" + goal.goalId() + "|" + goal.revision() + "|" + offer.identity() + "|" + offer.targetEpoch()));
			persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
			final ClanAllianceService.Result joined = _backend.joinAlliance(peer, actor, offer.identity(), offer.targetEpoch());
			_joinOffers.remove(key, offer);
			if (!joined.successful())
			{
				return nativeAllianceFailure(operation, "clan.alliance.join.accept", joined);
			}
			final AllianceObservation observed = _backend.observeAlliance(actor).orElse(null);
			if ((observed == null) || !offer.identity().equals(observed.identity()))
			{
				return result(OperationStatus.REPLAN, "clan.alliance.join.canonical_pending", null);
			}
			final DiplomacyState completed = diplomacy(goal, DiplomacyAction.ALLIANCE_JOIN, DiplomacyPhase.COMPLETED, peerClan.clanId(), offer.identity(), offer.targetEpoch().counter(), 0, prepared, prepared.evidenceHash());
			persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
			return completeRelation(operation, actor, peer, actorClan, metadata, completed, "agreement.fulfilled", "clan.alliance.join.complete");
		}
		return terminalize(operation, OperationStatus.STALE, "clan.alliance.join.alliance_mismatch", null);
	}
	private AdvanceResult advanceAllianceLeave(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if (!validPeerGoal(goal))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.alliance.leave.contract", null);
		}
		final MemberRef actor = managedActor(operation);
		final MemberRef peer = actor == null ? null : managedPeer(goal, actor);
		final ClanSnapshot actorClan = actor == null ? null : _backend.observe(actor).orElse(null);
		final ClanSnapshot peerClan = peer == null ? null : _backend.observe(peer).orElse(null);
		if ((actor == null) || (peer == null) || (actorClan == null) || (peerClan == null) || !matches(goal.target(), peerClan) || (actorClan.leaderObjectId() != actor.characterObjectId()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.alliance.leave.managed_actor_required", null);
		}
		final OrganizationMetadata metadata = organization(actor, actorClan, goal);
		final DiplomacyState prior = metadata.diplomacy();
		final AllianceObservation current = _backend.observeAlliance(actor).orElse(null);
		if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_LEAVE))
		{
			final AllianceIdentity expected = allianceIdentity(prior);
			if ((current != null) && !expected.equals(current.identity()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.leave.incarnation_changed", null);
			}
			if ((prior.phase() == DiplomacyPhase.COMPLETED) && (current == null))
			{
				return completeRelation(operation, actor, peer, actorClan, metadata, prior, "agreement.broken", "clan.alliance.leave.restart_reconciled");
			}
			if ((prior.phase() == DiplomacyPhase.PREPARED) && (current == null))
			{
				return completePreparedTerminal(operation, actor, peer, actorClan, metadata, prior, "agreement.broken", "clan.alliance.leave.restart_reconciled");
			}
		}
		if ((current == null) || (current.identity().leaderClanId() == actorClan.clanId()))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.alliance.leave.not_exact_member", null);
		}
		final AllianceObservation peerAlliance = _backend.observeAlliance(peer).orElse(null);
		if ((peerAlliance == null) || !current.identity().equals(peerAlliance.identity()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.alliance.leave.managed_counterpart_required", null);
		}
		if (inverseSuppressed(prior, DiplomacyAction.ALLIANCE_LEAVE, peerClan.clanId()))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_LEAVE, DiplomacyPhase.PREPARED, peerClan.clanId(), current.identity(), 0, 0, prior, sha256("alliance.leave|" + goal.goalId() + "|" + goal.revision() + "|" + current.identity()));
		persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
		final ClanAllianceService.Result left = _backend.leaveAlliance(actor, current.identity());
		if (!left.successful())
		{
			return nativeAllianceFailure(operation, "clan.alliance.leave", left);
		}
		if (_backend.observeAlliance(actor).isPresent())
		{
			return result(OperationStatus.REPLAN, "clan.alliance.leave.canonical_pending", null);
		}
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
		return completeRelation(operation, actor, peer, actorClan, metadata, completed, "agreement.broken", "clan.alliance.leave.complete");
	}

	private AdvanceResult advanceAllianceDissolve(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if ((goal.target() == null) || !"alliance.name".equals(goal.target().namespace()) || !validManagedSources(goal.validSources()) || (goal.acquisitionMethod() != null) || !goal.constraints().isEmpty())
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.alliance.dissolve.contract", null);
		}
		final MemberRef actor = managedActor(operation);
		final ClanSnapshot actorClan = actor == null ? null : _backend.observe(actor).orElse(null);
		if ((actor == null) || (actorClan == null) || (actorClan.leaderObjectId() != actor.characterObjectId()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.alliance.dissolve.managed_leader_required", null);
		}
		final OrganizationMetadata metadata = organization(actor, actorClan, goal);
		final DiplomacyState prior = metadata.diplomacy();
		final AllianceObservation current = _backend.observeAlliance(actor).orElse(null);
		if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_DISSOLVE))
		{
			if ((prior.phase() == DiplomacyPhase.COMPLETED) && (current == null))
			{
				return completeRelations(operation, actor, actorClan, metadata, prior, "agreement.broken", "clan.alliance.dissolve.restart_reconciled");
			}
			if ((prior.phase() == DiplomacyPhase.PREPARED) && (current == null))
			{
				return completePreparedRelations(operation, actor, actorClan, metadata, prior, "agreement.broken", "clan.alliance.dissolve.restart_reconciled");
			}
			if ((current != null) && !allianceIdentity(prior).equals(current.identity()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.dissolve.incarnation_changed", null);
			}
		}
		if ((current == null) || (current.identity().leaderClanId() != actorClan.clanId()) || !goal.target().key().equalsIgnoreCase(current.allianceName()))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.alliance.dissolve.not_exact_leader", null);
		}
		if (inverseSuppressed(prior, DiplomacyAction.ALLIANCE_DISSOLVE, 0))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final ClanAllianceService.ProofResult captured = _backend.captureAllianceMembership(current.identity());
		if (!captured.successful() || (captured.proof() == null))
		{
			return nativeAllianceProofFailure(operation, captured);
		}
		final List<Integer> proofClanIds = captured.proof().memberEpochs().stream().map(MembershipEpoch::clanId).toList();
		final ManagedAllianceSet managed = managedAllianceSet(actor, actorClan, current.identity(), metadata, goal.validSources(), proofClanIds);
		if (!managed.complete() || !proofClanIds.equals(managed.clanIds()))
		{
			return result(OperationStatus.WAITING, "clan.alliance.dissolve.membership_proof_mismatch", null);
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_DISSOLVE, DiplomacyPhase.PREPARED, 0, current.identity(), 0, 0, prior, sha256("alliance.dissolve|" + goal.goalId() + "|" + goal.revision() + "|" + captured.proof().memberEpochs()));
		persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
		final ClanAllianceService.Result dissolved = _backend.dissolveAlliance(actor, captured.proof());
		if (!dissolved.successful())
		{
			return nativeAllianceFailure(operation, "clan.alliance.dissolve", dissolved);
		}
		if (_backend.observeAlliance(actor).isPresent())
		{
			return result(OperationStatus.REPLAN, "clan.alliance.dissolve.canonical_pending", null);
		}
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
		return completeRelations(operation, actor, actorClan, metadata, completed, "agreement.broken", "clan.alliance.dissolve.complete");
	}
	private AdvanceResult advanceWarDeclare(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if (!validPeerGoal(goal))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.war.declare.contract", null);
		}
		final PeerContext peers = peerContext(operation);
		if (!peers.validManagedLeaders() || !matches(goal.target(), peers.peerClan()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.war.declare.managed_leaders_required", null);
		}
		if (allied(peers.actor(), peers.peer()))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.war.declare.allied_target", null);
		}
		final OrganizationMetadata metadata = organization(peers.actor(), peers.actorClan(), goal);
		final DiplomacyState prior = metadata.diplomacy();
		final WarIdentity current = _backend.currentWar(peers.actor(), peers.peer()).orElse(null);
		if (current != null)
		{
			if (prior.sameGoal(goal, DiplomacyAction.WAR_DECLARE) && (prior.warId() > 0) && (prior.warId() != current.warId()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.war.declare.war_changed", null);
			}
			final DiplomacyState completed = diplomacy(goal, DiplomacyAction.WAR_DECLARE, DiplomacyPhase.COMPLETED, peers.peerClan().clanId(), null, 0, current.warId(), prior, prior.evidenceHash().isEmpty() ? sha256("war.reconcile|" + goal.goalId() + "|" + goal.revision() + "|" + current.warId()) : prior.evidenceHash());
			persistDiplomacy(peers.actor().profileId(), metadata, completed, goal.validSources());
			return completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, completed, "agreement.broken", "clan.war.declare.restart_reconciled");
		}
		if (prior.sameGoal(goal, DiplomacyAction.WAR_DECLARE) && (prior.phase() == DiplomacyPhase.COMPLETED))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.declare.completed_war_missing", null);
		}
		if (inverseSuppressed(prior, DiplomacyAction.WAR_DECLARE, peers.peerClan().clanId()))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final RelationshipEvidence evidence = _backend.relationship(peers.actor().profileId(), peers.peer(), epochMinute());
		if (!evidence.hostileForWar())
		{
			return result(OperationStatus.WAITING, evidence.available() ? "clan.war.declare.insufficient_hostility" : "clan.war.declare.evidence_unavailable", null);
		}
		final String evidenceHash = sha256("war.policy|" + evidence.authorityHash() + "|" + evidence.hostilityScore() + "|" + evidence.hostileEventIds());
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.WAR_DECLARE, DiplomacyPhase.PREPARED, peers.peerClan().clanId(), null, 0, 0, prior, evidenceHash);
		persistDiplomacy(peers.actor().profileId(), metadata, prepared, goal.validSources());
		final ClanWarService.Result declared = _backend.declareWar(peers.actor(), peers.peer());
		if (!declared.successful() || (declared.identity() == null))
		{
			return nativeWarFailure(operation, "clan.war.declare", declared);
		}
		final WarIdentity observed = _backend.currentWar(peers.actor(), peers.peer()).orElse(null);
		if ((observed == null) || (observed.warId() != declared.identity().warId()))
		{
			return result(OperationStatus.REPLAN, "clan.war.declare.canonical_pending", null);
		}
		final DiplomacyState completed = diplomacy(goal, DiplomacyAction.WAR_DECLARE, DiplomacyPhase.COMPLETED, peers.peerClan().clanId(), null, 0, observed.warId(), prepared, prepared.evidenceHash());
		persistDiplomacy(peers.actor().profileId(), metadata, completed, goal.validSources());
		return completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, completed, "agreement.broken", "clan.war.declare.complete");
	}

	private AdvanceResult advanceWarStop(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if (!validPeerGoal(goal))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.war.stop.contract", null);
		}
		final PeerContext peers = peerContext(operation);
		if (!peers.validManagedLeaders() || !matches(goal.target(), peers.peerClan()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.war.stop.managed_leaders_required", null);
		}
		final OrganizationMetadata metadata = organization(peers.actor(), peers.actorClan(), goal);
		final DiplomacyState prior = metadata.diplomacy();
		final WarIdentity current = _backend.currentWar(peers.actor(), peers.peer()).orElse(null);
		if (prior.sameGoal(goal, DiplomacyAction.WAR_STOP) && (current == null))
		{
			return prior.phase() == DiplomacyPhase.PREPARED ? completePreparedTerminal(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, prior, "agreement.fulfilled", "clan.war.stop.restart_reconciled") : completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, prior, "agreement.fulfilled", "clan.war.stop.restart_reconciled");
		}
		if (current == null)
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.stop.not_at_war", null);
		}
		if (prior.sameGoal(goal, DiplomacyAction.WAR_STOP) && (prior.warId() > 0) && (prior.warId() != current.warId()))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.stop.war_changed", null);
		}
		if ((prior.phase() == DiplomacyPhase.PREPARED) && prior.sameGoal(goal, DiplomacyAction.WAR_STOP) && (prior.warId() != current.warId()))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.stop.war_changed", null);
		}
		if (inverseSuppressed(prior, DiplomacyAction.WAR_STOP, peers.peerClan().clanId()))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.WAR_STOP, DiplomacyPhase.PREPARED, peers.peerClan().clanId(), null, 0, current.warId(), prior, sha256("war.stop|" + goal.goalId() + "|" + goal.revision() + "|" + current.warId()));
		persistDiplomacy(peers.actor().profileId(), metadata, prepared, goal.validSources());
		final ClanWarService.Result stopped = _backend.stopWar(peers.actor(), peers.peer(), current.warId());
		if (!stopped.successful())
		{
			return nativeWarFailure(operation, "clan.war.stop", stopped);
		}
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(peers.actor().profileId(), metadata, completed, goal.validSources());
		return completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, completed, "agreement.fulfilled", "clan.war.stop.complete");
	}

	private AdvanceResult advanceWarPeace(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		if (!validPeerGoal(goal))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.war.peace.contract", null);
		}
		final PeerContext peers = peerContext(operation);
		if (!peers.validManagedLeaders() || !matches(goal.target(), peers.peerClan()))
		{
			return terminalize(operation, OperationStatus.UNSUPPORTED, "clan.war.peace.managed_leaders_required", null);
		}
		final OrganizationMetadata metadata = organization(peers.actor(), peers.actorClan(), goal);
		final DiplomacyState prior = metadata.diplomacy();
		final WarIdentity current = _backend.currentWar(peers.actor(), peers.peer()).orElse(null);
		if (prior.sameGoal(goal, DiplomacyAction.WAR_PEACE) && (current == null))
		{
			if ((prior.phase() == DiplomacyPhase.PREPARED) && prior.evidenceHash().equals(sha256("war.peace.source|" + goal.goalId() + "|" + goal.revision() + "|" + prior.warId())))
			{
				final DiplomacyState completed = withPhase(prior, DiplomacyPhase.COMPLETED);
				persistDiplomacy(peers.actor().profileId(), metadata, completed, goal.validSources());
				return terminalize(operation, OperationStatus.COMPLETE, "clan.war.peace.source_reconciled", receipt(operation, peers.actorClan(), peers.actor(), peers.peer(), completed.warId()));
			}
			return prior.phase() == DiplomacyPhase.PREPARED ? completePreparedTerminal(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, prior, "agreement.fulfilled", "clan.war.peace.restart_reconciled") : completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, prior, "agreement.fulfilled", "clan.war.peace.restart_reconciled");
		}
		if (current == null)
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.peace.not_at_war", null);
		}
		if (prior.sameGoal(goal, DiplomacyAction.WAR_PEACE) && (prior.warId() > 0) && (prior.warId() != current.warId()))
		{
			return terminalize(operation, OperationStatus.STALE, "clan.war.peace.war_changed", null);
		}
		final RelationshipEvidence evidence = _backend.relationship(peers.actor().profileId(), peers.peer(), epochMinute());
		if (!evidence.peacefulEnough())
		{
			return result(OperationStatus.WAITING, evidence.available() ? "clan.war.peace.hostility_hold" : "clan.war.peace.evidence_unavailable", null);
		}
		if (inverseSuppressed(prior, DiplomacyAction.WAR_PEACE, peers.peerClan().clanId()))
		{
			return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
		}
		final ConsentKey reverseKey = new ConsentKey(peers.peerClan().clanId(), peers.actorClan().clanId());
		final PeaceOffer offer = _peaceOffers.get(reverseKey);
		if (offer == null)
		{
			final ConsentKey key = new ConsentKey(peers.actorClan().clanId(), peers.peerClan().clanId());
			final PeaceOffer existing = _peaceOffers.get(key);
			if ((existing != null) && existing.sourceGoal().equals(operation._identity) && (_clock.getAsLong() < existing.expiresEpochMillis()))
			{
				if (existing.identity().warId() != current.warId())
				{
					_peaceOffers.remove(key, existing);
					return result(OperationStatus.STALE, "clan.war.peace.source_offer_stale", null);
				}
				return result(OperationStatus.WAITING, "clan.war.peace.offer_pending", receipt(operation, peers.actorClan(), peers.actor(), peers.peer(), current.warId()));
			}
			final DiplomacyState sourcePrepared = diplomacy(goal, DiplomacyAction.WAR_PEACE, DiplomacyPhase.PREPARED, peers.peerClan().clanId(), null, 0, current.warId(), prior, sha256("war.peace.source|" + goal.goalId() + "|" + goal.revision() + "|" + current.warId()));
			persistDiplomacy(peers.actor().profileId(), metadata, sourcePrepared, goal.validSources());
			putBoundedOffer(_peaceOffers, key, new PeaceOffer(peers.actor(), peers.peer(), current, operation._identity, _advanceSequence, goal.deadlineEpochMillis()));
			return result(OperationStatus.WAITING, "clan.war.peace.offer_published", receipt(operation, peers.actorClan(), peers.actor(), peers.peer(), current.warId()));
		}
		if ((_advanceSequence <= offer.sequence()) || !offer.source().equals(peers.peer()) || !offer.target().equals(peers.actor()) || (offer.identity().warId() != current.warId()) || (_clock.getAsLong() >= offer.expiresEpochMillis()))
		{
			_peaceOffers.remove(reverseKey, offer);
			return result(OperationStatus.STALE, "clan.war.peace.offer_stale", null);
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.WAR_PEACE, DiplomacyPhase.PREPARED, peers.peerClan().clanId(), null, 0, current.warId(), prior, sha256("war.peace|" + offer.sourceGoal() + "|" + goal.goalId() + "|" + goal.revision() + "|" + current.warId()));
		persistDiplomacy(peers.actor().profileId(), metadata, prepared, goal.validSources());
		final ClanWarService.Result accepted = _backend.acceptPeace(peers.actor(), peers.peer(), current);
		_peaceOffers.remove(reverseKey, offer);
		if (!accepted.successful())
		{
			return nativeWarFailure(operation, "clan.war.peace", accepted);
		}
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(peers.actor().profileId(), metadata, completed, goal.validSources());
		return completeRelation(operation, peers.actor(), peers.peer(), peers.actorClan(), metadata, completed, "agreement.fulfilled", "clan.war.peace.complete");
	}

	private AdvanceResult advanceAllianceChat(Operation operation)
	{
		final PhantomGoal goal = operation._goal;
		final String text = goal.acquisitionMethod();
		if ((goal.target() == null) || !"alliance.name".equals(goal.target().namespace()) || !goal.validSources().isEmpty() || (text == null) || text.isBlank() || (text.length() > MAX_CHAT_TEXT) || (goal.constraints().size() != 1) || !Objects.equals(goal.constraints().get(CHAT_TEXT_CONSTRAINT), (long) text.length()))
		{
			return terminalize(operation, OperationStatus.FAILED, "clan.alliance.chat.contract", null);
		}
		final MemberRef actor = managedActor(operation);
		final ClanSnapshot clan = actor == null ? null : _backend.observe(actor).orElse(null);
		final AllianceObservation current = actor == null ? null : _backend.observeAlliance(actor).orElse(null);
		if ((actor == null) || (clan == null) || (current == null) || !goal.target().key().equalsIgnoreCase(current.allianceName()))
		{
			return result(OperationStatus.STALE, "clan.alliance.chat.identity_stale", null);
		}
		final OrganizationMetadata metadata = organization(actor, clan, goal);
		final DiplomacyState prior = metadata.diplomacy();
		if (prior.sameGoal(goal, DiplomacyAction.ALLIANCE_CHAT))
		{
			if (!allianceIdentity(prior).equals(current.identity()))
			{
				return terminalize(operation, OperationStatus.STALE, "clan.alliance.chat.generation_changed", null);
			}
			if (prior.phase() == DiplomacyPhase.PREPARED)
			{
				final DiplomacyState completed = withPhase(prior, DiplomacyPhase.COMPLETED);
				persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
				return terminalize(operation, OperationStatus.COMPLETE, "clan.alliance.chat.restart_uncertain_suppressed", receipt(operation, clan, actor, actor, current.identity().generation()));
			}
			return terminalize(operation, OperationStatus.COMPLETE, "clan.alliance.chat.idempotent", receipt(operation, clan, actor, actor, current.identity().generation()));
		}
		final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_CHAT, DiplomacyPhase.PREPARED, 0, current.identity(), 0, 0, prior, sha256("alliance.chat|" + goal.goalId() + "|" + goal.revision() + "|" + current.identity() + "|" + text));
		persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
		final ChatResult sent = _backend.allianceChat(actor, current.identity(), text);
		if (sent.outcome() != ChatOutcome.DELIVERED)
		{
			return sent.outcome() == ChatOutcome.STALE ? result(OperationStatus.STALE, "clan.alliance.chat.stale", null) : terminalize(operation, OperationStatus.FAILED, "clan.alliance.chat." + sent.outcome().name().toLowerCase(), null);
		}
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(actor.profileId(), metadata, completed, goal.validSources());
		return terminalize(operation, OperationStatus.COMPLETE, "clan.alliance.chat.delivered", receipt(operation, clan, actor, actor, current.identity().generation()));
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
		persist(target.profileId(), metadata(goal, target, canonical, desired, budget, goal.validSources(), previous == null ? DiplomacyState.empty() : previous.diplomacy()));
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
			previous = metadata(goal, actor, clan, role, goal.expenseBudget(), goal.validSources(), previous == null ? DiplomacyState.empty() : previous.diplomacy());
			previous = new OrganizationMetadata(previous.canonicalClanId(), previous.clanName(), previous.canonicalLeaderObjectId(), previous.roleIntent(), previous.organizationGoalId(), previous.goalRevision(), previous.contributionBudget(), sourceObjectId, goal.requiredAmount(), baseline.inventoryCount(), baseline.warehouseCount(), ContributionState.PREPARED, previous.relationReferences(), previous.canonicalEvidenceHash(), previous.intentEvidenceHash(), previous.updatedEpochMillis(), previous.diplomacy());
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
		persist(member.profileId(), new OrganizationMetadata(clan.clanId(), clan.clanName(), clan.leaderObjectId(), reconciledRole, goal.goalId(), goal.revision(), current.contributionBudget(), current.contributionItemObjectId(), current.contributionAmount(), current.contributionInventoryBefore(), current.contributionWarehouseBefore(), current.contributionState(), current.relationReferences(), clan.evidenceHash(), intentHash(goal, member, reconciledRole, current.contributionBudget()), _clock.getAsLong(), current.diplomacy()));
		return true;
	}

	private OrganizationMetadata metadata(PhantomGoal goal, MemberRef member, ClanSnapshot clan, RoleKey role, long budget, List<PhantomDomainRef> references)
	{
		return metadata(goal, member, clan, role, budget, references, DiplomacyState.empty());
	}

	private OrganizationMetadata metadata(PhantomGoal goal, MemberRef member, ClanSnapshot clan, RoleKey role, long budget, List<PhantomDomainRef> references, DiplomacyState diplomacy)
	{
		final List<String> relations = references.stream().map(reference -> reference.namespace() + ":" + reference.key()).toList();
		return new OrganizationMetadata(clan.clanId(), clan.clanName(), clan.leaderObjectId(), role, goal.goalId(), goal.revision(), budget, 0, 0, 0, 0, ContributionState.NONE, relations, clan.evidenceHash(), intentHash(goal, member, role, budget), _clock.getAsLong(), diplomacy);
	}

	private OrganizationMetadata contributionMetadata(OrganizationMetadata value, ContributionState state)
	{
		return new OrganizationMetadata(value.canonicalClanId(), value.clanName(), value.canonicalLeaderObjectId(), value.roleIntent(), value.organizationGoalId(), value.goalRevision(), value.contributionBudget(), value.contributionItemObjectId(), value.contributionAmount(), value.contributionInventoryBefore(), value.contributionWarehouseBefore(), state, value.relationReferences(), value.canonicalEvidenceHash(), value.intentEvidenceHash(), _clock.getAsLong(), value.diplomacy());
	}
	private MemberRef managedActor(Operation operation)
	{
		final MemberRef actor = _backend.currentMember(operation._identity.profileId()).orElse(null);
		return (actor != null) && (actor.kind() == MemberKind.PHANTOM) ? actor : null;
	}

	private MemberRef managedPeer(PhantomGoal goal, MemberRef actor)
	{
		if (goal.validSources().size() != 1)
		{
			return null;
		}
		final MemberRef peer = _backend.resolve(goal.validSources().getFirst()).orElse(null);
		return (peer != null) && (peer.kind() == MemberKind.PHANTOM) && !peer.equals(actor) ? peer : null;
	}

	private PeerContext peerContext(Operation operation)
	{
		final MemberRef actor = managedActor(operation);
		final MemberRef peer = actor == null ? null : managedPeer(operation._goal, actor);
		return new PeerContext(actor, peer, actor == null ? null : _backend.observe(actor).orElse(null), peer == null ? null : _backend.observe(peer).orElse(null));
	}

	private OrganizationMetadata organization(MemberRef actor, ClanSnapshot clan, PhantomGoal goal)
	{
		final OrganizationMetadata current = _persistence.load(actor.profileId()).map(StoredMetadata::metadata).orElse(null);
		if ((current != null) && (current.canonicalClanId() == clan.clanId()))
		{
			return current;
		}
		return metadata(goal, actor, clan, defaultRole(actor, clan), current == null ? 0 : current.contributionBudget(), goal.validSources(), current == null ? DiplomacyState.empty() : current.diplomacy());
	}

	private DiplomacyState diplomacy(PhantomGoal goal, DiplomacyAction action, DiplomacyPhase phase, int counterpartClanId, AllianceIdentity alliance, long membershipCounter, long warId, DiplomacyState prior, String evidenceHash)
	{
		final boolean same = prior.sameGoal(goal, action);
		final long decisionEpoch = same ? prior.decisionEpoch() : Math.addExact(prior.decisionEpoch(), 1);
		final long happenedMinute = same && (prior.happenedEpochMinute() > 0) ? prior.happenedEpochMinute() : epochMinute();
		final long cooldown = phase == DiplomacyPhase.COMPLETED ? cooldownUntil() : prior.cooldownUntilEpochMillis();
		return new DiplomacyState(action, phase, goal.goalId(), goal.revision(), counterpartClanId, alliance == null ? 0 : alliance.leaderClanId(), alliance == null ? 0 : alliance.generation(), membershipCounter, warId, decisionEpoch, cooldown, happenedMinute, evidenceHash);
	}

	private DiplomacyState withPhase(DiplomacyState state, DiplomacyPhase phase)
	{
		return new DiplomacyState(state.action(), phase, state.goalId(), state.goalRevision(), state.counterpartClanId(), state.allianceLeaderClanId(), state.allianceGeneration(), state.membershipCounter(), state.warId(), state.decisionEpoch(), phase == DiplomacyPhase.COMPLETED ? cooldownUntil() : state.cooldownUntilEpochMillis(), state.happenedEpochMinute(), state.evidenceHash());
	}

	private void persistDiplomacy(long profileId, OrganizationMetadata metadata, DiplomacyState diplomacy, List<PhantomDomainRef> references)
	{
		persist(profileId, new OrganizationMetadata(metadata.canonicalClanId(), metadata.clanName(), metadata.canonicalLeaderObjectId(), metadata.roleIntent(), metadata.organizationGoalId(), metadata.goalRevision(), metadata.contributionBudget(), metadata.contributionItemObjectId(), metadata.contributionAmount(), metadata.contributionInventoryBefore(), metadata.contributionWarehouseBefore(), metadata.contributionState(), mergeReferences(metadata.relationReferences(), references), metadata.canonicalEvidenceHash(), metadata.intentEvidenceHash(), _clock.getAsLong(), diplomacy));
	}

	private List<String> mergeReferences(List<String> existing, List<PhantomDomainRef> additions)
	{
		final List<String> values = new ArrayList<>(existing);
		for (PhantomDomainRef reference : additions)
		{
			values.add(reference.namespace() + ":" + reference.key());
		}
		return values.stream().sorted().distinct().limit(MAX_RELATION_REFERENCES).toList();
	}

	private boolean inverseSuppressed(DiplomacyState prior, DiplomacyAction requested, int counterpartClanId)
	{
		if ((prior.phase() != DiplomacyPhase.COMPLETED) || (_clock.getAsLong() >= prior.cooldownUntilEpochMillis()) || ((counterpartClanId > 0) && (prior.counterpartClanId() > 0) && (counterpartClanId != prior.counterpartClanId())))
		{
			return false;
		}
		return switch (requested)
		{
			case ALLIANCE_CREATE, ALLIANCE_JOIN -> (prior.action() == DiplomacyAction.ALLIANCE_LEAVE) || (prior.action() == DiplomacyAction.ALLIANCE_DISSOLVE);
			case ALLIANCE_LEAVE, ALLIANCE_DISSOLVE -> (prior.action() == DiplomacyAction.ALLIANCE_CREATE) || (prior.action() == DiplomacyAction.ALLIANCE_JOIN);
			case WAR_DECLARE -> (prior.action() == DiplomacyAction.WAR_STOP) || (prior.action() == DiplomacyAction.WAR_PEACE);
			case WAR_STOP, WAR_PEACE -> prior.action() == DiplomacyAction.WAR_DECLARE;
			default -> false;
		};
	}

	private long cooldownUntil()
	{
		return Math.addExact(_clock.getAsLong(), _backend.pvpPairCooldownMillis());
	}

	private long epochMinute()
	{
		return _clock.getAsLong() / 60_000L;
	}

	private AllianceIdentity allianceIdentity(DiplomacyState state)
	{
		return new AllianceIdentity(state.allianceLeaderClanId(), state.allianceGeneration());
	}

	private boolean allied(MemberRef first, MemberRef second)
	{
		final AllianceObservation firstAlliance = _backend.observeAlliance(first).orElse(null);
		final AllianceObservation secondAlliance = _backend.observeAlliance(second).orElse(null);
		return (firstAlliance != null) && (secondAlliance != null) && firstAlliance.identity().equals(secondAlliance.identity());
	}

	private AdvanceResult completePreparedTerminal(Operation operation, MemberRef actor, MemberRef peer, ClanSnapshot clan, OrganizationMetadata metadata, DiplomacyState prepared, String eventKey, String reason)
	{
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(actor.profileId(), metadata, completed, operation._goal.validSources());
		return completeRelation(operation, actor, peer, clan, metadata, completed, eventKey, reason);
	}

	private AdvanceResult completeRelation(Operation operation, MemberRef actor, MemberRef peer, ClanSnapshot clan, OrganizationMetadata metadata, DiplomacyState diplomacy, String eventKey, String reason)
	{
		final String operationId = diplomacyOperationId(diplomacy);
		final boolean first = _backend.recordRelation(actor.profileId(), peer, eventKey, operationId, diplomacy.evidenceHash(), diplomacy.happenedEpochMinute());
		final boolean second = _backend.recordRelation(peer.profileId(), actor, eventKey, operationId, diplomacy.evidenceHash(), diplomacy.happenedEpochMinute());
		if (!first || !second)
		{
			return result(OperationStatus.REPLAN, "clan.relation.retry", receipt(operation, clan, actor, peer, diplomacy.warId() > 0 ? diplomacy.warId() : diplomacy.allianceGeneration()));
		}
		return terminalize(operation, OperationStatus.COMPLETE, reason, receipt(operation, clan, actor, peer, diplomacy.warId() > 0 ? diplomacy.warId() : diplomacy.allianceGeneration()));
	}

	private String diplomacyOperationId(DiplomacyState diplomacy)
	{
		return diplomacy.action().name() + "|" + diplomacy.goalId() + "|" + diplomacy.goalRevision() + "|" + diplomacy.allianceLeaderClanId() + "|" + diplomacy.allianceGeneration() + "|" + diplomacy.warId();
	}
	private AdvanceResult completePreparedRelations(Operation operation, MemberRef actor, ClanSnapshot clan, OrganizationMetadata metadata, DiplomacyState prepared, String eventKey, String reason)
	{
		final DiplomacyState completed = withPhase(prepared, DiplomacyPhase.COMPLETED);
		persistDiplomacy(actor.profileId(), metadata, completed, operation._goal.validSources());
		return completeRelations(operation, actor, clan, metadata, completed, eventKey, reason);
	}

	private AdvanceResult completeRelations(Operation operation, MemberRef actor, ClanSnapshot clan, OrganizationMetadata metadata, DiplomacyState diplomacy, String eventKey, String reason)
	{
		final List<MemberRef> peers = managedRelations(actor, metadata, operation._goal.validSources());
		final String operationId = diplomacyOperationId(diplomacy);
		for (MemberRef peer : peers)
		{
			if (!_backend.recordRelation(actor.profileId(), peer, eventKey, operationId, diplomacy.evidenceHash(), diplomacy.happenedEpochMinute()) || !_backend.recordRelation(peer.profileId(), actor, eventKey, operationId, diplomacy.evidenceHash(), diplomacy.happenedEpochMinute()))
			{
				return result(OperationStatus.REPLAN, "clan.relation.retry", receipt(operation, clan, actor, peer, diplomacy.allianceGeneration()));
			}
		}
		return terminalize(operation, OperationStatus.COMPLETE, reason, receipt(operation, clan, actor, actor, diplomacy.allianceGeneration()));
	}

	private ManagedAllianceSet managedAllianceSet(MemberRef actor, ClanSnapshot actorClan, AllianceIdentity identity, OrganizationMetadata metadata, List<PhantomDomainRef> sources, List<Integer> proofClanIds)
	{
		final Set<Integer> proof = Set.copyOf(proofClanIds);
		final Map<Integer, MemberRef> members = new HashMap<>();
		if (proof.contains(actorClan.clanId()))
		{
			members.put(actorClan.clanId(), actor);
		}
		for (MemberRef member : managedRelations(actor, metadata, sources))
		{
			final ClanSnapshot clan = _backend.observe(member).orElse(null);
			if ((clan == null) || !proof.contains(clan.clanId()))
			{
				continue;
			}
			final AllianceObservation alliance = _backend.observeAlliance(member).orElse(null);
			if ((clan.leaderObjectId() == member.characterObjectId()) && (alliance != null) && identity.equals(alliance.identity()))
			{
				members.put(clan.clanId(), member);
			}
		}
		final List<Integer> clanIds = members.keySet().stream().sorted().toList();
		return new ManagedAllianceSet(members.keySet().equals(proof), clanIds, members);
	}

	private List<MemberRef> managedRelations(MemberRef actor, OrganizationMetadata metadata, List<PhantomDomainRef> sources)
	{
		final List<PhantomDomainRef> references = new ArrayList<>(sources);
		for (String stored : metadata.relationReferences())
		{
			final PhantomDomainRef reference = parseReference(stored);
			if (reference != null)
			{
				references.add(reference);
			}
		}
		final Map<Long, MemberRef> members = new HashMap<>();
		for (PhantomDomainRef reference : references.stream().sorted().distinct().limit(MAX_RELATION_REFERENCES).toList())
		{
			final MemberRef member = _backend.resolve(reference).orElse(null);
			if ((member != null) && (member.kind() == MemberKind.PHANTOM) && !member.equals(actor))
			{
				members.put(member.profileId(), member);
			}
		}
		return members.values().stream().sorted(Comparator.comparingLong(MemberRef::profileId)).toList();
	}

	private PhantomDomainRef parseReference(String value)
	{
		final int separator = value.indexOf(':');
		if ((separator <= 0) || (separator == (value.length() - 1)))
		{
			return null;
		}
		try
		{
			return new PhantomDomainRef(value.substring(0, separator), value.substring(separator + 1));
		}
		catch (RuntimeException exception)
		{
			return null;
		}
	}

	private AdvanceResult nativeAllianceFailure(Operation operation, String prefix, ClanAllianceService.Result result)
	{
		return switch (result.status())
		{
			case STALE -> result(OperationStatus.STALE, prefix + ".stale", null);
			case PERSISTENCE_FAILURE -> result(OperationStatus.REPLAN, prefix + ".persistence_failure", null);
			case INELIGIBLE -> result.reason() == ClanAllianceService.Reason.CLAN_RETIRING ? result(OperationStatus.WAITING, prefix + ".clan_retiring", null) : terminalize(operation, OperationStatus.FAILED, prefix + "." + result.reason().name().toLowerCase(), null);
			case SUCCESS -> result(OperationStatus.REPLAN, prefix + ".canonical_pending", null);
		};
	}

	private AdvanceResult nativeAllianceProofFailure(Operation operation, ClanAllianceService.ProofResult result)
	{
		return switch (result.status())
		{
			case STALE -> result(OperationStatus.STALE, "clan.alliance.dissolve.proof_stale", null);
			case PERSISTENCE_FAILURE -> result(OperationStatus.REPLAN, "clan.alliance.dissolve.proof_persistence_failure", null);
			case INELIGIBLE -> result.reason() == ClanAllianceService.Reason.CLAN_RETIRING ? result(OperationStatus.WAITING, "clan.alliance.dissolve.clan_retiring", null) : terminalize(operation, OperationStatus.FAILED, "clan.alliance.dissolve." + result.reason().name().toLowerCase(), null);
			case SUCCESS -> result(OperationStatus.REPLAN, "clan.alliance.dissolve.proof_pending", null);
		};
	}

	private AdvanceResult nativeWarFailure(Operation operation, String prefix, ClanWarService.Result result)
	{
		return switch (result.status())
		{
			case STALE -> result(OperationStatus.STALE, prefix + ".stale", null);
			case PERSISTENCE_FAILURE -> result(OperationStatus.REPLAN, prefix + ".persistence_failure", null);
			case INELIGIBLE -> result.reason() == ClanWarService.Reason.CLAN_RETIRING ? result(OperationStatus.WAITING, prefix + ".clan_retiring", null) : terminalize(operation, OperationStatus.FAILED, prefix + "." + result.reason().name().toLowerCase(), null);
			case SUCCESS -> result(OperationStatus.REPLAN, prefix + ".canonical_pending", null);
		};
	}

	private void pruneConsentOffers(long now)
	{
		_joinOffers.entrySet().removeIf(entry -> now >= entry.getValue().expiresEpochMillis());
		_peaceOffers.entrySet().removeIf(entry -> now >= entry.getValue().expiresEpochMillis());
	}

	private static <V> void putBoundedOffer(LinkedHashMap<ConsentKey, V> map, ConsentKey key, V value)
	{
		map.put(key, value);
		while (map.size() > MAX_ACTIVE_OPERATIONS)
		{
			map.remove(map.keySet().iterator().next());
		}
	}

	private static boolean validPeerGoal(PhantomGoal goal)
	{
		return (goal.target() != null) && SetTarget.valid(goal.target()) && (goal.validSources().size() == 1) && validManagedSources(goal.validSources()) && (goal.acquisitionMethod() == null) && goal.constraints().isEmpty();
	}

	private static boolean validManagedSources(List<PhantomDomainRef> sources)
	{
		return (sources.size() <= MAX_RELATION_REFERENCES) && sources.stream().allMatch(source -> ("profile".equals(source.namespace()) || "character.object".equals(source.namespace())) && (parsePositive(source.key()) > 0));
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
		if ((goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.deadlineEpochMillis() <= now) || !List.of(BUILD_GOAL, JOIN_GOAL, ROLE_GOAL, CONTRIBUTE_GOAL, CHAT_GOAL, ALLIANCE_CREATE_GOAL, ALLIANCE_JOIN_GOAL, ALLIANCE_LEAVE_GOAL, ALLIANCE_DISSOLVE_GOAL, WAR_DECLARE_GOAL, WAR_STOP_GOAL, WAR_PEACE_GOAL, ALLIANCE_CHAT_GOAL).contains(goal.goalType()))
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

	private record ConsentKey(int sourceClanId, int targetClanId)
	{
		private ConsentKey
		{
			if ((sourceClanId <= 0) || (targetClanId <= 0) || (sourceClanId == targetClanId))
			{
				throw new IllegalArgumentException("Invalid diplomacy consent pair.");
			}
		}
	}

	private record JoinOffer(MemberRef source, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch, OperationIdentity sourceGoal, long sequence, long expiresEpochMillis)
	{
	}

	private record PeaceOffer(MemberRef source, MemberRef target, WarIdentity identity, OperationIdentity sourceGoal, long sequence, long expiresEpochMillis)
	{
	}

	private record ManagedAllianceSet(boolean complete, List<Integer> clanIds, Map<Integer, MemberRef> members)
	{
		private ManagedAllianceSet
		{
			clanIds = List.copyOf(clanIds);
			members = Map.copyOf(members);
		}
	}

	private record PeerContext(MemberRef actor, MemberRef peer, ClanSnapshot actorClan, ClanSnapshot peerClan)
	{
		private boolean validManagedLeaders()
		{
			return (actor != null) && (peer != null) && (actor.kind() == MemberKind.PHANTOM) && (peer.kind() == MemberKind.PHANTOM) && (actorClan != null) && (peerClan != null) && (actorClan.clanId() != peerClan.clanId()) && (actorClan.leaderObjectId() == actor.characterObjectId()) && (peerClan.leaderObjectId() == peer.characterObjectId());
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
