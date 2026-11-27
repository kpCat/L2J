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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceMembershipProof;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipResult;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipSnapshot;
import org.l2jmobius.gameserver.model.clan.ClanDepartureService;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment;
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
 * Bounded caller-driven owner for explicit and autonomous clan work. Canonical Clan,
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
	private static final long AUTONOMOUS_ID_BASE = 1L << 60;
	private static final List<String> FOUNDER_NAMES = List.of("Rowan", "Ember", "Cedar", "Flint", "Willow", "Dawn", "Ash", "Raven");

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
		PERSISTENCE_PENDING,
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

		default boolean hasAutonomousInvitation(int objectId) { return true; }

		default Optional<AutonomousActor> autonomousActor(long profileId)
		{
			return currentMember(profileId).map(member -> new AutonomousActor(member, observe(member).orElse(null), 0, true, false, 0, 0));
		}

		default CreationResult createAutonomous(MemberRef actor, String name) { return new CreationResult(CreationOutcome.STALE, null); }
		default boolean atAutonomousMaster(MemberRef actor, int masterObjectId) { return false; }
		default CreationResult createAutonomous(MemberRef actor, String name, int masterObjectId) { return createAutonomous(actor, name); }
		default CreationResult createAutonomous(MemberRef actor, String name, int masterObjectId, BooleanSupplier current)
		{
			return current.getAsBoolean() ? createAutonomous(actor, name, masterObjectId) : new CreationResult(CreationOutcome.STALE, null);
		}

		default ClanFoundingDevelopment.Outcome developAutonomous(MemberRef leader, int expectedClanId) { return ClanFoundingDevelopment.Outcome.NOT_READY; }
		default ClanFoundingDevelopment.Outcome developAutonomous(MemberRef leader, int expectedClanId, int masterObjectId) { return developAutonomous(leader, expectedClanId); }
		default ClanFoundingDevelopment.Outcome developAutonomous(MemberRef leader, int expectedClanId, int expectedLevel, int masterObjectId, long expiresAt, BooleanSupplier current)
		{
			return expectedLevel == 0 && current.getAsBoolean() ? developAutonomous(leader, expectedClanId, masterObjectId) : ClanFoundingDevelopment.Outcome.NOT_READY;
		}
		default boolean mayStartAutonomousDevelopment(MemberRef leader) { return true; }

		default ClanDepartureService.Outcome leaveOrdinaryClan(MemberRef member, int expectedClanId, int expectedLeaderId) { return ClanDepartureService.Outcome.STALE; }

		default boolean inviteAutonomous(MemberRef leader, MemberRef target, int expectedClanId) { return false; }

		default ClanInvitationService.RespondResult respondAutonomous(MemberRef invitee, MemberRef requester, ClanInvitationService.Response response, InvitationIdentity identity)
		{
			return respond(invitee, response, identity);
		}

		RoleResult transferLeader(MemberRef requester, MemberRef newLeader, int expectedClanId);

		ContributionObservation observeContribution(MemberRef member, int expectedClanId, int inventoryObjectId);

		ContributionResult contribute(MemberRef member, int expectedClanId, int inventoryObjectId, long count);

		WithdrawalOutcome withdraw(MemberRef member, int expectedClanId, int warehouseObjectId, long count);

		ChatResult clanChat(MemberRef member, int expectedClanId, String text);

		default Optional<AllianceObservation> observeAlliance(MemberRef member)
		{
			return Optional.empty();
		}

		default Optional<AllianceActor> allianceActor(MemberRef actor) { return Optional.empty(); }
		default MembershipResult durableAlliance(int clanId) { return null; }
		default boolean acknowledgeAlliance(MembershipSnapshot expected) { return false; }
		default ClanAllianceService.Result createAutonomousAlliance(AllianceActor actor, String name) { return null; }
		default ClanAllianceService.Result createAutonomousAlliance(AllianceActor actor, String name, int masterObjectId) { return createAutonomousAlliance(actor, name); }
		default ClanAllianceService.Result joinAutonomousAlliance(AllianceActor source, AllianceActor actor, AllianceIdentity identity) { return null; }
		default ClanAllianceService.Result leaveAutonomousAlliance(AllianceActor actor, AllianceIdentity identity) { return null; }

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

	public record AutonomousActor(MemberRef member, ClanSnapshot clan, int level, boolean canJoin, boolean canFound, long adena, long sp, long developmentItems, boolean territory)
	{
		public AutonomousActor(MemberRef member, ClanSnapshot clan, int level, boolean canJoin, boolean canFound, long adena, long sp)
		{
			this(member, clan, level, canJoin, canFound, adena, sp, 0, false);
		}
	}

	public enum VisitPurpose { FOUND, DEVELOP, CREATE_ALLIANCE }
	public enum VisitOutcome { HANDLED, STALE, BUSY }

	public record VisitRequest(VisitPurpose purpose, MemberRef member, PhantomGoal root, int clanId, int currentLevel, MemberRef peer, int peerClanId, List<MemberRef> alternatePeers, int preference, long offeredAt, long deadline)
	{
		public VisitRequest { alternatePeers = List.copyOf(alternatePeers); }
	}

	public record AllianceActor(MemberRef member, long materializationEpoch, MembershipSnapshot membership)
	{
		public AllianceActor
		{
			Objects.requireNonNull(member);
			Objects.requireNonNull(membership);
			if (member.kind() != MemberKind.PHANTOM || materializationEpoch == 0 || membership.leaderObjectId() != member.characterObjectId()) { throw new IllegalArgumentException("Invalid autonomous alliance actor."); }
		}
	}

	private record DevelopmentPlan(int clanId, Clan.LevelRequirement requirement, long adenaDue, long spDue, long itemsDue, int reputationDue, int membersDue, boolean territoryDue, long expires)
	{
		int farmPreference()
		{
			if (adenaDue == 0 && spDue == 0) { return 0; }
			return adenaDue > 0 && spDue > 0 ? 8 : 4;
		}

		boolean ready()
		{
			return adenaDue == 0 && spDue == 0 && itemsDue == 0 && reputationDue == 0 && membersDue == 0 && !territoryDue;
		}
	}

	private record DeparturePreference(int clanId, int counterpartId, boolean alliance, long since)
	{
	}

	private final PhantomGoalStore _goals;
	private final PersistencePort _persistence;
	private final Backend _backend;
	private final LongSupplier _clock;
	private final Map<Long, Operation> _active = new ConcurrentHashMap<>();
	private final Set<Long> _mutating = ConcurrentHashMap.newKeySet();
	private final Object _autonomousMonitor = new Object();
	private final LinkedHashMap<Long, Long> _discoveryDue = new LinkedHashMap<>();
	private final LinkedHashMap<Long, Long> _diplomacyDue = new LinkedHashMap<>();
	private final LinkedHashMap<Long, DeparturePreference> _departurePreferences = new LinkedHashMap<>();
	private final LinkedHashMap<String, Long> _autonomousOutcomes = new LinkedHashMap<>();
	private final LinkedHashMap<Long, DevelopmentPlan> _development = new LinkedHashMap<>();
	private final AtomicLong _autonomousId = new AtomicLong();
	private final boolean _autonomousEnabled = Boolean.parseBoolean(System.getProperty("phantoms.clan.autonomous", "true"));
	private final long _discoveryMillis = TimeUnit.SECONDS.toMillis(Math.clamp(Long.getLong("phantoms.clan.discoverySeconds", 60), 30, 300));
	private volatile java.util.function.Consumer<VisitRequest> _autonomousVisits;
	private final long _diplomacyMillis = TimeUnit.SECONDS.toMillis(Math.clamp(Long.getLong("phantoms.clan.diplomacySeconds", 600), 600, 1500));
	private long _budgetSecond;
	private int _budgetUsed;
	private int _autonomousActions;
	private final LinkedHashMap<OperationIdentity, AdvanceResult> _terminal = new LinkedHashMap<>();
	private final LinkedHashMap<ChatIdentity, ChatResult> _chatReceipts = new LinkedHashMap<>();
	private final LinkedHashMap<ConsentKey, JoinOffer> _joinOffers = new LinkedHashMap<>();
	private final LinkedHashMap<ConsentKey, PeaceOffer> _peaceOffers = new LinkedHashMap<>();
	private long _advanceSequence;
	private volatile State _state = State.NEW;

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
		if (!_mutating.add(profileId)) { return result(OperationStatus.WAITING, "clan.operation.actor_busy", null); }
		try { return advanceExplicit(profileId, goalId, goalRevision); }
		finally { _mutating.remove(profileId); }
	}

	private AdvanceResult advanceExplicit(long profileId, long goalId, long goalRevision)
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
		if (diplomacyWrite(goal.goalType()) && _persistence.load(profileId).map(StoredMetadata::metadata).map(OrganizationMetadata::diplomacy).filter(PhantomClanService::unsettledAutonomousAlliance).isPresent())
		{
			return result(OperationStatus.WAITING, "clan.alliance.autonomous_reconciling", null);
		}
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

	/** One short turn for a materialized actor, under the caller's current execution grant. */
	public void advanceAutonomous(long profileId, int objectId, Supplier<List<Long>> peers, int preference)
	{
		if (_state != State.RUNNING) { return; }
		final long now = _clock.getAsLong();
		final boolean invited = _autonomousEnabled && _backend.hasAutonomousInvitation(objectId);
		final boolean expedited = invited || _autonomousEnabled && incomingAllianceOffer(profileId, objectId, now) != null;
		synchronized (_autonomousMonitor)
		{
			if (_state != State.RUNNING || !expedited && _discoveryDue.getOrDefault(profileId, 0L) > now) { return; }
			if (now / 1000 != _budgetSecond) { _budgetSecond = now / 1000; _budgetUsed = 0; }
			if (_budgetUsed >= (expedited ? 4 : 3) || _autonomousActions + _active.size() >= MAX_ACTIVE_OPERATIONS || !_mutating.add(profileId)) { return; }
			_budgetUsed++;
			_autonomousActions++;
			if (!expedited)
			{
				_discoveryDue.remove(profileId);
				_discoveryDue.put(profileId, now + _discoveryMillis + Math.floorMod(profileId, 31) * 1000);
				while (_discoveryDue.size() > 512) { _discoveryDue.pollFirstEntry(); }
			}
		}
		try
		{
			final var root = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			final var actor = _backend.autonomousActor(profileId).orElse(null);
			if (actor == null || actor.member().characterObjectId() != objectId || _state != State.RUNNING) { return; }
			final var current = _persistence.load(profileId).orElse(null);
			final var prior = current == null ? null : current.metadata();
			if (prior != null && unsettledAutonomousAlliance(prior.diplomacy()))
			{
				settleAutonomousAlliance(actor.member(), current, now);
				return;
			}
			if (!_autonomousEnabled || _active.containsKey(profileId) || validCommon(profileId, root, now)) { return; }
			if (prior != null && (prior.contributionState() == ContributionState.PREPARED || prior.diplomacy().phase() == DiplomacyPhase.PREPARED))
			{
				observeAutonomousOutcome("clan.autonomous.prepared_effect");
				return;
			}
			if (invited && actor.clan() == null)
			{
				answerAutonomous(actor, preference, now);
				return;
			}
			planAutonomous(actor, peers, preference, current, root, now);
		}
		catch (RuntimeException failure)
		{
			observeAutonomousOutcome("clan.autonomous.error." + failure.getClass().getSimpleName());
		}
		finally
		{
			synchronized (_autonomousMonitor) { _autonomousActions--; _mutating.remove(profileId); }
		}
	}

	private void answerAutonomous(AutonomousActor actor, int preference, long now)
	{
		final var invite = _backend.observeInvitation(actor.member()).orElse(null);
		if (invite == null) { return; }
		final var requester = _backend.resolve(new PhantomDomainRef("character.object", Integer.toString(invite.identity().requesterObjectId()))).orElse(null);
		final var relation = requester == null ? null : _backend.relationship(actor.member().profileId(), requester, now / 60_000);
		final boolean accepts = actor.canJoin() && requester != null && (relation == null || !relation.hostileForWar())
			&& (preference >= 0 || relation != null && relation.affinityScore() > 0);
		if (_state != State.RUNNING) { return; }
		final var accepted = _backend.respondAutonomous(actor.member(), requester, accepts ? ClanInvitationService.Response.ACCEPT : ClanInvitationService.Response.REFUSE, invite.identity());
		if (accepted.accepted())
		{
			final var joined = _backend.observe(actor.member()).orElse(null);
			if (joined != null && joined.clanId() == invite.identity().clanId())
			{
				final var goal = autonomousGoal(actor, JOIN_GOAL, clanRef(joined.clanId()), List.of(), now);
				reconcile(actor.member(), joined, goal);
			}
		}
		observeAutonomousOutcome("clan.autonomous.invitation." + accepted.outcome().name().toLowerCase(java.util.Locale.ROOT));
	}

	public void bindAutonomousVisits(java.util.function.Consumer<VisitRequest> visits)
	{
		_autonomousVisits = Objects.requireNonNull(visits);
	}

	private boolean offerVisit(VisitPurpose purpose, AutonomousActor actor, PhantomGoal root, int clanId, AutonomousActor peer, int preference, long now)
	{
		return offerVisit(purpose, actor, root, clanId, peer, List.of(), preference, now);
	}

	private boolean offerVisit(VisitPurpose purpose, AutonomousActor actor, PhantomGoal root, int clanId, AutonomousActor peer, List<MemberRef> alternatePeers, int preference, long now)
	{
		final var destination = _autonomousVisits;
		if (destination == null) { return false; }
		if (root != null && root.status() == PhantomGoalStatus.ACTIVE
			&& org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()))
		{
			destination.accept(new VisitRequest(purpose, actor.member(), root, clanId, actor.clan() == null ? -1 : actor.clan().level(), peer == null ? null : peer.member(),
				peer == null || peer.clan() == null ? 0 : peer.clan().clanId(), alternatePeers, preference, now, now + 4 * _discoveryMillis));
		}
		return true;
	}

	public VisitOutcome completeAutonomousVisit(VisitRequest visit, int masterObjectId)
	{
		return completeAutonomousVisit(visit, masterObjectId, visit.deadline(), () -> true);
	}

	boolean mayContinueVisit(VisitRequest visit)
	{
		return visit.purpose() != VisitPurpose.DEVELOP || _backend.mayStartAutonomousDevelopment(visit.member());
	}

	VisitOutcome completeAutonomousVisit(VisitRequest visit, int masterObjectId, long arrivalDeadline, BooleanSupplier currentExecution)
	{
		final long profileId = visit.member().profileId();
		final long now = _clock.getAsLong();
		final BooleanSupplier current = () -> _state == State.RUNNING && _clock.getAsLong() < arrivalDeadline && currentExecution.getAsBoolean();
		synchronized (_autonomousMonitor)
		{
			if (_state != State.RUNNING || !_autonomousEnabled || now >= arrivalDeadline) { return VisitOutcome.STALE; }
			if (_autonomousActions + _active.size() >= MAX_ACTIVE_OPERATIONS || !_mutating.add(profileId)) { return VisitOutcome.BUSY; }
			_autonomousActions++;
		}
		try
		{
			final var actor = _backend.autonomousActor(profileId).orElse(null);
			final var prior = _persistence.load(profileId).orElse(null);
			if (_state != State.RUNNING || actor == null || !actor.member().equals(visit.member())
				|| !visit.root().equals(_goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null))
				|| _backend.hasAutonomousInvitation(actor.member().characterObjectId()) || !_backend.atAutonomousMaster(actor.member(), masterObjectId)
				|| _active.containsKey(profileId) || prior != null && (prior.metadata().contributionState() == ContributionState.PREPARED
					|| unsettledAutonomousAlliance(prior.metadata().diplomacy()) || prior.metadata().diplomacy().phase() == DiplomacyPhase.PREPARED))
			{
				return VisitOutcome.STALE;
			}
			final var clan = actor.clan();
			final var peer = visit.peer() == null ? null : _backend.autonomousActor(visit.peer().profileId()).filter(value -> value.member().equals(visit.peer())).orElse(null);
			if (!current.getAsBoolean()) { return VisitOutcome.STALE; }
			switch (visit.purpose())
			{
				case FOUND ->
				{
					if (clan != null || !actor.canFound() || actor.level() < 10 || visit.preference() < 4
						|| !eligibleRecruit(actor, peer, now) && visit.alternatePeers().stream()
							.map(ref -> _backend.autonomousActor(ref.profileId()).filter(value -> value.member().equals(ref)).orElse(null))
							.noneMatch(candidate -> eligibleRecruit(actor, candidate, now))) { return VisitOutcome.STALE; }
					if (!foundAutonomously(actor, now, masterObjectId, current)) { return VisitOutcome.STALE; }
				}
				case DEVELOP ->
				{
					if (!isLeader(actor) || clan.clanId() != visit.clanId() || clan.level() != visit.currentLevel() || !developmentReady(developmentPlan(actor, now))
						|| !_backend.mayStartAutonomousDevelopment(actor.member())) { return VisitOutcome.STALE; }
					if (!developAutonomously(actor, masterObjectId, arrivalDeadline, current)) { return VisitOutcome.STALE; }
				}
				case CREATE_ALLIANCE ->
				{
					if (!isLeader(actor) || clan.clanId() != visit.clanId() || clan.level() < 5 || clan.allianceId() != 0 || prior == null
						|| prior.metadata().diplomacy().cooldownUntilEpochMillis() > now || peer == null || !isLeader(peer) || peer.clan().clanId() != visit.peerClanId()
						|| !eligibleAllianceFounder(clan, peer.clan()) || affinityForAlliance(actor, peer, visit.preference(), now) <= 0) { return VisitOutcome.STALE; }
					final var own = _backend.allianceActor(actor.member()).orElse(null);
					if (own == null || own.membership().epoch().clanId() != clan.clanId() || own.membership().epoch().allianceId() != 0 || !current.getAsBoolean()) { return VisitOutcome.STALE; }
					writeAutonomousAlliance(actor, prior, ALLIANCE_CREATE_GOAL, 0, null, own.membership().epoch(), now,
						() -> _backend.createAutonomousAlliance(own, autonomousAllianceName(clan.clanId()), masterObjectId));
				}
			}
			return VisitOutcome.HANDLED;
		}
		finally
		{
			synchronized (_autonomousMonitor) { _autonomousActions--; _mutating.remove(profileId); }
		}
	}

	private void planAutonomous(AutonomousActor actor, Supplier<List<Long>> peers, int preference, StoredMetadata current, PhantomGoal root, long now)
	{
		final var development = updateDevelopment(actor, now);
		final var member = actor.member();
		final var clan = actor.clan();
		if (clan == null)
		{
			if (!actor.canFound() || actor.level() < 10 || preference < 4) { return; }
		}
		else
		{
			if (clan.leaderObjectId() != member.characterObjectId())
			{
				considerOrdinaryDeparture(actor, now);
				return;
			}
			if (_state == State.RUNNING && developmentReady(development) && _backend.mayStartAutonomousDevelopment(member))
			{
				if (!offerVisit(VisitPurpose.DEVELOP, actor, root, clan.clanId(), null, preference, now) && developAutonomously(actor, 0, Long.MAX_VALUE, () -> _state == State.RUNNING)) { return; }
			}
			if (current == null)
			{
				final var goal = autonomousGoal(actor, BUILD_GOAL, clanRef(clan.clanId()), List.of(), now);
				reconcile(member, clan, goal);
				current = _persistence.load(member.profileId()).orElse(null);
			}
			final var incoming = incomingAllianceOffer(member.profileId(), member.characterObjectId(), now);
			if (incoming != null && acceptAutonomousAlliance(actor, current, incoming, preference, now)) { return; }
			if (clan.memberCount() >= clan.maxMembers() && !diplomacyDue(member.profileId(), now)) { return; }
		}
		final var available = peers.get().stream().filter(id -> id != member.profileId() && !_active.containsKey(id)).limit(8)
			.map(_backend::autonomousActor).flatMap(Optional::stream).toList();
		if (clan != null && planAutonomousAlliance(actor, available, preference, current, root, now)) { return; }
		if (clan != null && clan.memberCount() >= clan.maxMembers()) { return; }
		final var candidate = available.stream()
			.filter(peer -> eligibleRecruit(actor, peer, now))
			.findFirst().orElse(null);
		if (candidate == null || _state != State.RUNNING) { return; }
		if (clan == null)
		{
			final var alternatives = available.stream().filter(peer -> peer != candidate && eligibleRecruit(actor, peer, now))
				.limit(3).map(AutonomousActor::member).toList();
			if (!offerVisit(VisitPurpose.FOUND, actor, root, 0, candidate, alternatives, preference, now)) { foundAutonomously(actor, now, 0, () -> _state == State.RUNNING); }
			return;
		}
		if (!_mutating.add(candidate.member().profileId())) { return; }
		try
		{
			if (_backend.inviteAutonomous(member, candidate.member(), clan.clanId()))
			{
				observeAutonomousOutcome("clan.autonomous.invitation.delivered");
			}
		}
		finally { _mutating.remove(candidate.member().profileId()); }
	}

	private void considerOrdinaryDeparture(AutonomousActor actor, long now)
	{
		final var member = actor.member();
		final var clan = actor.clan();
		if (clan.level() >= 5 || clan.allianceId() != 0) { return; }
		final var leader = _backend.resolve(new PhantomDomainRef("character.object", Integer.toString(clan.leaderObjectId()))).orElse(null);
		if (leader == null) { return; }
		final var relationship = _backend.relationship(member.profileId(), leader, now / 60_000);
		if (departureReady(member.profileId(), clan.clanId(), leader.characterObjectId(), false, relationship, now) && _state == State.RUNNING)
		{
			final var outcome = _backend.leaveOrdinaryClan(member, clan.clanId(), clan.leaderObjectId());
			if (outcome == ClanDepartureService.Outcome.APPLIED || outcome == ClanDepartureService.Outcome.STALE)
			{
				synchronized (_autonomousMonitor) { _departurePreferences.remove(member.profileId()); }
			}
			observeAutonomousOutcome("clan.autonomous.departure." + outcome.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private boolean diplomacyDue(long profileId, long now)
	{
		synchronized (_autonomousMonitor) { return _diplomacyDue.getOrDefault(profileId, 0L) <= now; }
	}

	private void deferDiplomacy(long profileId, long due)
	{
		synchronized (_autonomousMonitor)
		{
			_diplomacyDue.remove(profileId);
			_diplomacyDue.put(profileId, due);
			while (_diplomacyDue.size() > 512) { _diplomacyDue.pollFirstEntry(); }
		}
	}

	private boolean eligibleRecruit(AutonomousActor actor, AutonomousActor peer, long now)
	{
		return peer != null && peer.clan() == null && peer.canJoin() && Math.abs(peer.level() - actor.level()) <= 15
			&& !_backend.relationship(actor.member().profileId(), peer.member(), now / 60_000).hostileForWar();
	}

	private static boolean developmentReady(DevelopmentPlan plan)
	{
		return plan != null && plan.ready() && ClanFoundingDevelopment.supportsLevel(plan.requirement().currentLevel());
	}

	private boolean developAutonomously(AutonomousActor actor, int masterObjectId, long expiresAt, BooleanSupplier current)
	{
		if (!current.getAsBoolean()) { return false; }
		final var outcome = _backend.developAutonomous(actor.member(), actor.clan().clanId(), actor.clan().level(), masterObjectId, expiresAt, current);
		if (outcome == ClanFoundingDevelopment.Outcome.APPLIED)
		{
			synchronized (_autonomousMonitor) { _development.remove(actor.member().profileId()); }
			observeAutonomousOutcome("clan.autonomous.development.level_applied");
		}
		if (outcome == ClanFoundingDevelopment.Outcome.UNCONFIRMED) { observeAutonomousOutcome("clan.autonomous.development.payment_unconfirmed"); }
		return outcome == ClanFoundingDevelopment.Outcome.APPLIED || outcome == ClanFoundingDevelopment.Outcome.UNCONFIRMED;
	}

	private boolean planAutonomousAlliance(AutonomousActor actor, List<AutonomousActor> available, int preference, StoredMetadata current, PhantomGoal root, long now)
	{
		final long profileId = actor.member().profileId();
		final var clan = actor.clan();
		if (_state != State.RUNNING || current == null || current.metadata().diplomacy().cooldownUntilEpochMillis() > now
			|| !diplomacyDue(profileId, now) || clan.allianceId() == 0 && clan.level() < 5) { return false; }
		deferDiplomacy(profileId, now + _diplomacyMillis + Math.floorMod(profileId, 301) * 1000);
		final var own = _backend.allianceActor(actor.member()).orElse(null);
		if (own == null || own.membership().epoch().clanId() != clan.clanId() || own.membership().epoch().allianceId() != clan.allianceId()) { return false; }
		if (clan.allianceId() != 0 && clan.allianceId() != clan.clanId())
		{
			final var leader = available.stream().filter(peer -> isLeader(peer) && peer.clan().clanId() == clan.allianceId()).findFirst().orElse(null);
			if (leader == null) { return false; }
			final var relationship = _backend.relationship(profileId, leader.member(), now / 60_000);
			if (!departureReady(profileId, clan.clanId(), leader.clan().clanId(), true, relationship, now)) { return false; }
			final var affiliation = affiliation(own.membership().epoch());
			writeAutonomousAlliance(actor, current, ALLIANCE_LEAVE_GOAL, leader.clan().clanId(), affiliation, own.membership().epoch(), now,
				() -> _backend.leaveAutonomousAlliance(own, affiliation));
			return true;
		}
		final var candidate = available.stream().filter(PhantomClanService::isLeader).filter(peer -> peer.clan().clanId() != clan.clanId())
			.filter(peer -> peer.clan().allianceId() == 0 || clan.allianceId() == 0 && peer.clan().allianceId() == peer.clan().clanId())
			.map(peer -> Map.entry(peer, affinityForAlliance(actor, peer, preference, now))).filter(entry -> entry.getValue() > 0)
			.max(Comparator.<Map.Entry<AutonomousActor, Integer>>comparingInt(Map.Entry::getValue).thenComparingInt(entry -> -entry.getKey().clan().clanId()))
			.map(Map.Entry::getKey).orElse(null);
		if (candidate == null) { return false; }
		if (clan.allianceId() == clan.clanId())
		{
			return offerAutonomousAlliance(own, candidate, now);
		}
		if (!eligibleAllianceFounder(clan, candidate.clan())) { return false; }
		if (offerVisit(VisitPurpose.CREATE_ALLIANCE, actor, root, clan.clanId(), candidate, preference, now)) { return true; }
		final String name = autonomousAllianceName(clan.clanId());
		writeAutonomousAlliance(actor, current, ALLIANCE_CREATE_GOAL, 0, null, own.membership().epoch(), now,
			() -> _backend.createAutonomousAlliance(own, name));
		return true;
	}

	private static boolean eligibleAllianceFounder(ClanSnapshot clan, ClanSnapshot peer)
	{
		return peer.allianceId() == 0 && (peer.level() < clan.level() || peer.level() == clan.level() && peer.clanId() >= clan.clanId());
	}

	private boolean departureReady(long profileId, int clanId, int counterpartId, boolean alliance, RelationshipEvidence relationship, long now)
	{
		synchronized (_autonomousMonitor)
		{
			final var prior = _departurePreferences.get(profileId);
			if (!relationship.hostileForWar())
			{
				_departurePreferences.remove(profileId);
				return false;
			}
			if (prior != null && prior.clanId() == clanId && prior.counterpartId() == counterpartId && prior.alliance() == alliance)
			{
				return now - prior.since() >= _diplomacyMillis;
			}
			_departurePreferences.remove(profileId);
			_departurePreferences.put(profileId, new DeparturePreference(clanId, counterpartId, alliance, now));
			while (_departurePreferences.size() > 512) { _departurePreferences.pollFirstEntry(); }
			return false;
		}
	}

	private static boolean isLeader(AutonomousActor actor)
	{
		return actor.clan() != null && actor.clan().leaderObjectId() == actor.member().characterObjectId();
	}

	private int affinityForAlliance(AutonomousActor actor, AutonomousActor peer, int preference, long now)
	{
		final var relation = _backend.relationship(actor.member().profileId(), peer.member(), now / 60_000);
		if (relation.available() && (!relation.peacefulEnough() || relation.affinityScore() < -100)) { return 0; }
		final int compatibility = 8 - 2 * Math.abs(actor.clan().level() - peer.clan().level());
		return preference * 5 + compatibility + Math.min(20, peer.clan().memberCount()) + relation.affinityScore();
	}

	private boolean offerAutonomousAlliance(AllianceActor source, AutonomousActor candidate, long now)
	{
		final var target = _backend.allianceActor(candidate.member()).orElse(null);
		if (target == null || target.membership().epoch().clanId() != candidate.clan().clanId() || target.membership().epoch().allianceId() != 0) { return false; }
		final var key = new ConsentKey(source.membership().epoch().clanId(), target.membership().epoch().clanId());
		final var existing = joinOffer(key);
		if (existing != null && existing.expiresEpochMillis() > now) { return false; }
		final var checked = _backend.checkAllianceJoin(source.member(), target.member());
		if (!checked.successful() || !target.membership().epoch().equals(checked.targetEpoch()) || !affiliation(source.membership().epoch()).equals(checked.identity())) { return false; }
		final long id = nextAutonomousId(now);
		final var offer = new JoinOffer(source.member(), target.member(), checked.identity(), checked.targetEpoch(), new OperationIdentity(source.member().profileId(), id, 0), 0,
			now + TimeUnit.MINUTES.toMillis(3), source, target);
		synchronized (_joinOffers)
		{
			final var current = _joinOffers.get(key);
			if (_state != State.RUNNING || current != null && current.expiresEpochMillis() > now) { return false; }
			putBoundedOffer(_joinOffers, key, offer);
		}
		observeAutonomousOutcome("clan.autonomous.alliance.offer_published");
		return true;
	}

	private JoinOffer incomingAllianceOffer(long profileId, int objectId, long now)
	{
		synchronized (_joinOffers)
		{
			return _joinOffers.values().stream().filter(offer -> offer.autonomous() && offer.expiresEpochMillis() > now
				&& offer.target().profileId() == profileId && offer.target().characterObjectId() == objectId).findFirst().orElse(null);
		}
	}

	private boolean acceptAutonomousAlliance(AutonomousActor actor, StoredMetadata current, JoinOffer offer, int preference, long now)
	{
		final long sourceId = offer.source().profileId();
		final var key = new ConsentKey(offer.identity().leaderClanId(), offer.targetEpoch().clanId());
		if (current == null) { return false; }
		if (current.metadata().diplomacy().cooldownUntilEpochMillis() > now)
		{
			removeJoinOffer(key, offer);
			return false;
		}
		if (!_mutating.add(sourceId)) { return false; }
		try
		{
			final var sourceRoot = _goals.load(sourceId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			final var source = _backend.autonomousActor(sourceId).orElse(null);
			final var targetProof = _backend.allianceActor(actor.member()).orElse(null);
			final var sourceProof = _backend.allianceActor(offer.source()).orElse(null);
			if (_active.containsKey(sourceId) || validCommon(sourceId, sourceRoot, now) || targetProof != null && !targetProof.equals(offer.targetSide())
				|| sourceProof != null && !sourceProof.equals(offer.sourceSide()) || source != null && (!isLeader(source) || affinityForAlliance(actor, source, preference, now) <= 0))
			{
				removeJoinOffer(key, offer);
				observeAutonomousOutcome("clan.autonomous.alliance.offer_declined");
				return false;
			}
			if (source == null || targetProof == null || sourceProof == null) { return false; }
			synchronized (_joinOffers)
			{
				if (_state != State.RUNNING || _clock.getAsLong() >= offer.expiresEpochMillis() || !_joinOffers.remove(key, offer)) { return false; }
			}
			writeAutonomousAlliance(actor, current, ALLIANCE_JOIN_GOAL, key.sourceClanId(), offer.identity(), offer.targetEpoch(), now,
				() -> _backend.joinAutonomousAlliance(sourceProof, targetProof, offer.identity()));
			return true;
		}
		finally { _mutating.remove(sourceId); }
	}

	private static String autonomousAllianceName(int clanId) { return "Union" + Integer.toUnsignedString(clanId, 36); }

	private static AllianceIdentity affiliation(MembershipEpoch epoch) { return new AllianceIdentity(epoch.allianceId(), epoch.generation()); }

	private static boolean diplomacyWrite(String goalType)
	{
		return switch (goalType)
		{
			case ALLIANCE_CREATE_GOAL, ALLIANCE_JOIN_GOAL, ALLIANCE_LEAVE_GOAL, ALLIANCE_DISSOLVE_GOAL, WAR_DECLARE_GOAL, WAR_STOP_GOAL, WAR_PEACE_GOAL, ALLIANCE_CHAT_GOAL -> true;
			default -> false;
		};
	}

	private static boolean unsettledAutonomousAlliance(DiplomacyState state)
	{
		return state.goalId() >= AUTONOMOUS_ID_BASE && state.phase() == DiplomacyPhase.PREPARED
			&& (state.action() == DiplomacyAction.ALLIANCE_CREATE || state.action() == DiplomacyAction.ALLIANCE_JOIN || state.action() == DiplomacyAction.ALLIANCE_LEAVE);
	}

	private void writeAutonomousAlliance(AutonomousActor actor, StoredMetadata current, String goalType, int counterpart, AllianceIdentity affiliation,
		MembershipEpoch before, long now, Supplier<ClanAllianceService.Result> write)
	{
		if (_state != State.RUNNING && !ALLIANCE_JOIN_GOAL.equals(goalType)) { return; }
		final var member = actor.member();
		final var clan = actor.clan();
		final var base = current.metadata();
		final long operationId = nextAutonomousId(now);
		final var action = switch (goalType)
		{
			case ALLIANCE_CREATE_GOAL -> DiplomacyAction.ALLIANCE_CREATE;
			case ALLIANCE_JOIN_GOAL -> DiplomacyAction.ALLIANCE_JOIN;
			case ALLIANCE_LEAVE_GOAL -> DiplomacyAction.ALLIANCE_LEAVE;
			default -> throw new IllegalArgumentException("Unsupported autonomous diplomacy action.");
		};
		final var prepared = new DiplomacyState(action, DiplomacyPhase.PREPARED, operationId, 0, counterpart, affiliation == null ? 0 : affiliation.leaderClanId(),
			affiliation == null ? 0 : affiliation.generation(), before.counter(), 0, Math.addExact(base.diplomacy().decisionEpoch(), 1),
			now + _diplomacyMillis, now / 60_000, sha256("clan.autonomous.diplomacy|" + operationId + "|" + before + "|" + affiliation + "|" + member.characterObjectId()));
		final var saved = _persistence.save(member.profileId(), current.rowVersion(), withAutonomousDiplomacy(base, clan, prepared, now));
		final var result = write.get();
		if (result != null && result.successful())
		{
			final var observed = _backend.allianceActor(member).map(AllianceActor::membership).orElse(null);
			if (autonomousEffectObserved(clan.clanId(), prepared, observed))
			{
				completeAutonomousAlliance(member, saved, observed.epoch(), now);
				return;
			}
		}
		else if (result != null && (result.status() == ClanAllianceService.Status.INELIGIBLE || result.status() == ClanAllianceService.Status.STALE))
		{
			_persistence.save(member.profileId(), saved.rowVersion(), base);
			observeAutonomousOutcome("clan.autonomous.alliance.rejected." + result.reason().name().toLowerCase(java.util.Locale.ROOT));
			return;
		}
		observeAutonomousOutcome("clan.autonomous.alliance.awaiting_confirmation");
	}

	private void settleAutonomousAlliance(MemberRef actor, StoredMetadata current, long now)
	{
		final var metadata = current.metadata();
		final var prepared = metadata.diplomacy();
		final var result = _backend.durableAlliance(metadata.canonicalClanId());
		if (result == null) { return; }
		if (result.status() == ClanAllianceService.Status.INELIGIBLE && result.reason() == ClanAllianceService.Reason.CLAN_NOT_FOUND)
		{
			finishUnappliedAutonomousAlliance(actor, current, now, "clan.autonomous.alliance.clan_retired");
			return;
		}
		if (!result.successful() || result.snapshot() == null) { return; }
		final var observed = result.snapshot();
		final var epoch = observed.epoch();
		if (autonomousEffectObserved(metadata.canonicalClanId(), prepared, observed))
		{
			if (_backend.acknowledgeAlliance(observed)) { completeAutonomousAlliance(actor, current, epoch, now); }
			return;
		}
		final boolean unchanged = epoch.clanId() == metadata.canonicalClanId() && epoch.counter() == prepared.membershipCounter()
			&& (prepared.action() == DiplomacyAction.ALLIANCE_LEAVE
				? epoch.allianceId() == prepared.allianceLeaderClanId() && epoch.generation() == prepared.allianceGeneration() : epoch.allianceId() == 0);
		if (unchanged)
		{
			finishUnappliedAutonomousAlliance(actor, current, now, "clan.autonomous.alliance.confirmed_no_effect");
		}
		else if (epoch.counter() > prepared.membershipCounter() && _backend.acknowledgeAlliance(observed))
		{
			finishUnappliedAutonomousAlliance(actor, current, now, "clan.autonomous.alliance.superseded_epoch");
		}
	}

	private void finishUnappliedAutonomousAlliance(MemberRef actor, StoredMetadata current, long now, String outcome)
	{
		_persistence.save(actor.profileId(), current.rowVersion(), withAutonomousDiplomacy(current.metadata(), null, DiplomacyState.empty(), now));
		observeAutonomousOutcome(outcome);
	}

	private static boolean autonomousEffectObserved(int clanId, DiplomacyState prepared, MembershipSnapshot observed)
	{
		if (observed == null || observed.epoch().clanId() != clanId || observed.epoch().counter() != prepared.membershipCounter() + 1) { return false; }
		final var epoch = observed.epoch();
		return switch (prepared.action())
		{
			case ALLIANCE_CREATE -> epoch.allianceId() == clanId && autonomousAllianceName(clanId).equalsIgnoreCase(observed.allianceName());
			case ALLIANCE_JOIN -> epoch.allianceId() == prepared.allianceLeaderClanId() && epoch.generation() == prepared.allianceGeneration();
			case ALLIANCE_LEAVE -> epoch.allianceId() == 0 && observed.penaltyType() == Clan.PENALTY_TYPE_CLAN_LEAVED;
			default -> false;
		};
	}

	private void completeAutonomousAlliance(MemberRef actor, StoredMetadata current, MembershipEpoch effect, long now)
	{
		final var prepared = current.metadata().diplomacy();
		final var completed = new DiplomacyState(prepared.action(), DiplomacyPhase.COMPLETED, prepared.goalId(), prepared.goalRevision(), prepared.counterpartClanId(),
			effect.allianceId() == 0 ? prepared.allianceLeaderClanId() : effect.allianceId(), effect.generation() == 0 ? prepared.allianceGeneration() : effect.generation(),
			effect.counter(), 0, prepared.decisionEpoch(), now + _diplomacyMillis, prepared.happenedEpochMinute(), prepared.evidenceHash());
		_persistence.save(actor.profileId(), current.rowVersion(), withAutonomousDiplomacy(current.metadata(), null, completed, now));
		observeAutonomousOutcome("clan.autonomous.alliance." + prepared.action().name().toLowerCase(java.util.Locale.ROOT) + ".completed");
	}

	private static OrganizationMetadata withAutonomousDiplomacy(OrganizationMetadata base, ClanSnapshot clan, DiplomacyState state, long now)
	{
		return new OrganizationMetadata(clan == null ? base.canonicalClanId() : clan.clanId(), clan == null ? base.clanName() : clan.clanName(),
			clan == null ? base.canonicalLeaderObjectId() : clan.leaderObjectId(), clan == null ? base.roleIntent() : RoleKey.LEADER, base.organizationGoalId(), base.goalRevision(),
			base.contributionBudget(), base.contributionItemObjectId(), base.contributionAmount(), base.contributionInventoryBefore(), base.contributionWarehouseBefore(),
			base.contributionState(), base.relationReferences(), clan == null ? base.canonicalEvidenceHash() : clan.evidenceHash(), base.intentEvidenceHash(), now, state);
	}

	private boolean foundAutonomously(AutonomousActor actor, long now, int masterObjectId, BooleanSupplier current)
	{
		final var member = actor.member();
		for (int alternate = 0; alternate < 3 && _state == State.RUNNING; alternate++)
		{
			if (!current.getAsBoolean()) { return false; }
			final String prefix = FOUNDER_NAMES.get(Math.floorMod(member.characterObjectId() + alternate, FOUNDER_NAMES.size()));
			final String name = prefix + Integer.toUnsignedString(member.characterObjectId(), 36);
			final var created = _backend.createAutonomous(member, name, masterObjectId, current);
			if (created.outcome() == CreationOutcome.NAME_TAKEN) { continue; }
			final var clan = created.clan();
			if ((created.outcome() == CreationOutcome.CREATED || created.outcome() == CreationOutcome.ALREADY_SATISFIED) && clan != null && clan.leaderObjectId() == member.characterObjectId() && clan.clanName().equalsIgnoreCase(name))
			{
				final var goal = autonomousGoal(actor, BUILD_GOAL, clanRef(clan.clanId()), List.of(), now);
				reconcile(member, clan, goal);
			}
			observeAutonomousOutcome("clan.autonomous.found." + created.outcome().name().toLowerCase(java.util.Locale.ROOT));
			return created.outcome() != CreationOutcome.STALE;
		}
		return false;
	}

	private DevelopmentPlan developmentPlan(AutonomousActor actor, long now)
	{
		final var clan = actor.clan();
		if (!isLeader(actor)) { return null; }
		final var requirement = Clan.levelRequirement(clan.level());
		if (requirement == null) { return null; }
		final int members = requirement.currentLevel() == 0 ? 2 : requirement.members();
		return new DevelopmentPlan(clan.clanId(), requirement, Math.max(0, requirement.adena() - actor.adena()),
			Math.max(0, requirement.sp() - actor.sp()), Math.max(0, requirement.itemCount() - actor.developmentItems()),
			clan.level() < 5 ? 0 : Math.max(0, requirement.reputation() - clan.reputation()),
			Math.max(0, members - clan.memberCount()), requirement.territory() && !actor.territory(), now + 2 * _discoveryMillis + 31_000);
	}

	private DevelopmentPlan updateDevelopment(AutonomousActor actor, long now)
	{
		final long id = actor.member().profileId();
		final var plan = developmentPlan(actor, now);
		synchronized (_autonomousMonitor)
		{
			final var old = _development.remove(id);
			if (plan == null) { return null; }
			_development.put(id, plan);
			while (_development.size() > MAX_ACTIVE_OPERATIONS) { _development.pollFirstEntry(); }
			if (old == null || old.clanId() != plan.clanId() || !old.requirement().equals(plan.requirement()))
			{
				if (!ClanFoundingDevelopment.supportsLevel(plan.requirement().currentLevel())) { observeAutonomousOutcome("clan.autonomous.development.writer_unavailable"); }
			}
			else if (plan.adenaDue() < old.adenaDue() || plan.spDue() < old.spDue() || plan.itemsDue() < old.itemsDue()
				|| plan.reputationDue() < old.reputationDue() || plan.membersDue() < old.membersDue() || old.territoryDue() && !plan.territoryDue())
			{
				observeAutonomousOutcome(plan.ready() ? "clan.autonomous.development.budget_ready" : "clan.autonomous.development.resources_observed");
			}
			return plan;
		}
	}

	public int developmentFarmPreference(long profileId)
	{
		if (_state != State.RUNNING) { return 0; }
		synchronized (_autonomousMonitor)
		{
			final var plan = _development.get(profileId);
			return plan != null && plan.expires() > _clock.getAsLong() ? plan.farmPreference() : 0;
		}
	}

	public int developmentDropNeed(long profileId, Player player)
	{
		if (_state != State.RUNNING || player.isInParty() || player.isAlikeDead()) { return 0; }
		final var clan = player.getClan();
		if (clan == null || clan.getLeaderId() != player.getObjectId()) { return 0; }
		final DevelopmentPlan plan;
		synchronized (_autonomousMonitor) { plan = _development.get(profileId); }
		if (plan == null || plan.expires() <= _clock.getAsLong() || plan.clanId() != clan.getId()
			|| plan.requirement().currentLevel() != clan.getLevel() || plan.itemsDue() <= 0) { return 0; }
		final int itemId = plan.requirement().itemId();
		return player.getInventory().getInventoryItemCount(itemId, -1) < plan.requirement().itemCount() ? itemId : 0;
	}

	private PhantomGoal autonomousGoal(AutonomousActor actor, String type, PhantomDomainRef target, List<PhantomDomainRef> sources, long now)
	{
		final long id = nextAutonomousId(now);
		return new PhantomGoal(id, type, PhantomGoalStatus.ACTIVE, profileRef(actor.member().profileId()), target, 1, 0, null, sources, null, "clan.organization", 0, 0, 0, now + TimeUnit.MINUTES.toMillis(3), Map.of(), "clan.autonomous", 0);
	}

	private long nextAutonomousId(long now) { return AUTONOMOUS_ID_BASE + _autonomousId.updateAndGet(last -> Math.max(last + 1, now)); }

	private static PhantomDomainRef profileRef(long id) { return new PhantomDomainRef("profile", Long.toString(id)); }
	private static PhantomDomainRef clanRef(int id) { return new PhantomDomainRef("clan.id", Integer.toString(id)); }

	private void observeAutonomousOutcome(String reason)
	{
		synchronized (_autonomousMonitor)
		{
			if (_autonomousOutcomes.size() < MAX_ACTIVE_OPERATIONS || _autonomousOutcomes.containsKey(reason)) { _autonomousOutcomes.merge(reason, 1L, Long::sum); }
		}
	}

	public Map<String, Long> autonomousOutcomes()
	{
		synchronized (_autonomousMonitor) { return Map.copyOf(_autonomousOutcomes); }
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
		if ((_state != State.RUNNING) || !PhantomGoal.validPayloadText(text))
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
		synchronized (_joinOffers) { _joinOffers.clear(); }
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
		synchronized (_autonomousMonitor) { if (_autonomousActions != 0) { return false; } }
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
			if (created.outcome() == CreationOutcome.PERSISTENCE_PENDING)
			{
				return result(OperationStatus.WAITING, "clan.build.persistence_pending", null);
			}
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
		final String text = chatText(goal);
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
			final JoinOffer existing = joinOffer(key);
			if ((existing != null) && existing.sourceGoal().equals(operation._identity) && (_clock.getAsLong() < existing.expiresEpochMillis()))
			{
				return result(OperationStatus.WAITING, "clan.alliance.join.offer_pending", receipt(operation, actorClan, actor, peer, existing.identity().generation()));
			}
			final ClanAllianceService.Result checked = _backend.checkAllianceJoin(actor, peer);
			if (!checked.successful() || (checked.identity() == null) || (checked.targetEpoch() == null))
			{
				return nativeAllianceFailure(operation, "clan.alliance.join.offer", checked);
			}
			synchronized (_joinOffers)
			{
				putBoundedOffer(_joinOffers, key, new JoinOffer(actor, peer, checked.identity(), checked.targetEpoch(), operation._identity, _advanceSequence, goal.deadlineEpochMillis()));
			}
			return result(OperationStatus.WAITING, "clan.alliance.join.offer_published", receipt(operation, actorClan, actor, peer, checked.identity().generation()));
		}
		if ((actorAlliance == null) && (peerAlliance != null) && (peerAlliance.identity().leaderClanId() == peerClan.clanId()))
		{
			final ConsentKey key = new ConsentKey(peerClan.clanId(), actorClan.clanId());
			final JoinOffer offer = joinOffer(key);
			if ((offer == null) || offer.autonomous() || (_advanceSequence <= offer.sequence()) || !offer.source().equals(peer) || !offer.target().equals(actor) || !offer.identity().equals(peerAlliance.identity()) || (_clock.getAsLong() >= offer.expiresEpochMillis()))
			{
				return result(OperationStatus.WAITING, "clan.alliance.join.waiting_exact_offer", null);
			}
			if (!currentSourceConsent(offer))
			{
				removeJoinOffer(key, offer);
				return result(OperationStatus.WAITING, "clan.alliance.join.source_consent_revoked", null);
			}
			if (inverseSuppressed(prior, DiplomacyAction.ALLIANCE_JOIN, peerClan.clanId()))
			{
				return result(OperationStatus.WAITING, "clan.diplomacy.hysteresis", null);
			}
			// The current source revision and this exact offer are consumed before PREPARED.
			// A later change of intent cannot revoke an already accepted write.
			if (!removeJoinOffer(key, offer)) { return result(OperationStatus.WAITING, "clan.alliance.join.waiting_exact_offer", null); }
			final DiplomacyState prepared = diplomacy(goal, DiplomacyAction.ALLIANCE_JOIN, DiplomacyPhase.PREPARED, peerClan.clanId(), offer.identity(), offer.targetEpoch().counter(), 0, prior, sha256("alliance.join|" + offer.sourceGoal() + "|" + goal.goalId() + "|" + goal.revision() + "|" + offer.identity() + "|" + offer.targetEpoch()));
			persistDiplomacy(actor.profileId(), metadata, prepared, goal.validSources());
			final ClanAllianceService.Result joined = _backend.joinAlliance(peer, actor, offer.identity(), offer.targetEpoch());
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

	private boolean currentSourceConsent(JoinOffer offer)
	{
		final var source = offer.sourceGoal();
		final var active = _active.get(source.profileId());
		if (active == null || !active._identity.equals(source)) { return false; }
		final var current = _goals.load(source.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		return validCommon(source.profileId(), current, _clock.getAsLong()) && ALLIANCE_JOIN_GOAL.equals(current.goalType())
			&& current.goalId() == source.goalId() && current.revision() == source.goalRevision();
	}

	private JoinOffer joinOffer(ConsentKey key)
	{
		synchronized (_joinOffers) { return _joinOffers.get(key); }
	}

	private boolean removeJoinOffer(ConsentKey key, JoinOffer offer)
	{
		synchronized (_joinOffers) { return _joinOffers.remove(key, offer); }
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
		synchronized (_joinOffers) { _joinOffers.entrySet().removeIf(entry -> now >= entry.getValue().expiresEpochMillis()); }
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
		if (stored.filter(row -> unsettledAutonomousAlliance(row.metadata().diplomacy()) && !row.metadata().diplomacy().equals(metadata.diplomacy())).isPresent())
		{
			throw new java.util.ConcurrentModificationException("Unconfirmed autonomous alliance mutation owns this component.");
		}
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
		if (ALLIANCE_JOIN_GOAL.equals(operation._goal.goalType()))
		{
			synchronized (_joinOffers) { _joinOffers.values().removeIf(offer -> offer.sourceGoal().equals(operation._identity)); }
		}
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
		return (goal.target() != null) && SetTarget.valid(goal.target()) && goal.validSources().isEmpty() && (goal.constraints().size() == 1) && PhantomGoal.validPayloadText(text) && (text.codePointCount(0, text.length()) <= MAX_CHAT_TEXT) && Objects.equals(goal.constraints().get(CHAT_TEXT_CONSTRAINT), (long) text.codePointCount(0, text.length())) && Objects.equals(chatText(goal), text);
	}

	private static String chatText(PhantomGoal goal)
	{
		return goal.payloadText() == null ? goal.acquisitionMethod() : goal.payloadText();
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

	private record JoinOffer(MemberRef source, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch, OperationIdentity sourceGoal, long sequence, long expiresEpochMillis,
		AllianceActor sourceSide, AllianceActor targetSide)
	{
		private JoinOffer(MemberRef source, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch, OperationIdentity sourceGoal, long sequence, long expiresEpochMillis)
		{
			this(source, target, identity, targetEpoch, sourceGoal, sequence, expiresEpochMillis, null, null);
		}

		boolean autonomous() { return sourceSide != null; }
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
