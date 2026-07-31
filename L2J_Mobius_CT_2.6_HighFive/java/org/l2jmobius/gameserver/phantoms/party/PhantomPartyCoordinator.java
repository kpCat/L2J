/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PreparationOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.DeliveryRegistration;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort.StoredPartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleAssignment;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleMatchResult;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/**
 * Single-pulse party saga owner. It has no worker, timer or scheduled future.
 */
public final class PhantomPartyCoordinator implements PhantomSchedulerControlPort, PartyInvitationDelivery, PhantomPartyParticipationPort
{
	public static final String FORM_GOAL = "party.form";
	public static final String JOIN_GOAL = "party.join";
	public static final String LEAD_GOAL = "party.lead";
	public static final String MEMBER_GOAL = "party.member";
	public static final String TRAVEL_GOAL = "party.travel";
	public static final String LEAVE_GOAL = "party.leave";
	public static final String EXPEL_GOAL = "party.expel_member";
	public static final String TRANSFER_LEADER_GOAL = "party.transfer_leader";
	private static final String ZERO_HASH = "0".repeat(64);
	private static final int PAGE_SIZE = 256;
	private static final int MAX_INBOUND_INVITES = 4096;
	private static final int MAX_TERMINAL_EVENTS = 4096;
	private static final int MAX_DUE_GROUPS = 4096;

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum CommandOutcome
	{
		ACCEPTED,
		IDEMPOTENT,
		NOT_RUNNING,
		GOAL_MISMATCH,
		STALE_GENERATION,
		CLAIM_EXISTS,
		NOT_LEADER,
		ROSTER_FULL,
		CANONICAL_REJECTED,
		PERSISTENCE_CONFLICT,
		TARGET_UNAVAILABLE
	}

	public enum RouteOutcome
	{
		ACCEPTED,
		PENDING,
		NOT_RUNNING,
		NOT_PHANTOM_LEADER,
		UNAVAILABLE
	}

	private final PhantomPartyPersistencePort _store;
	private final PhantomGoalStore _goals;
	private final PhantomPartyBackend _backend;
	private final PhantomPartyRoleMatcher _roles;
	private final PhantomPartyRoleCatalog _roleCatalog;
	private final PhantomPartyRouteCoordinator _routes;
	private final PhantomPartyTactics _tactics;
	private final Supplier<String> _topologyHash;
	private final LongSupplier _clock;
	private final PhantomSocialEventSink _socialEvents;
	private final LongSupplier _socialClock;
	private final int _operationBudget;
	private final ArrayBlockingQueue<ManagedInvitation> _inbound = new ArrayBlockingQueue<>(MAX_INBOUND_INVITES);
	private final ArrayBlockingQueue<TerminalEvent> _terminalEvents = new ArrayBlockingQueue<>(MAX_TERMINAL_EVENTS);
	private final ArrayBlockingQueue<Long> _tacticalReleases = new ArrayBlockingQueue<>(MAX_INBOUND_INVITES);
	private final Map<Long, StoredPartyState> _claims = new ConcurrentHashMap<>();
	private final Map<String, GroupRuntime> _groups = new ConcurrentHashMap<>();
	private final Map<Long, ExternalActionLease> _tacticalActions = new ConcurrentHashMap<>();
	private final Object _indexLock = new Object();
	private final Object _lifecycleLock = new Object();
	private final Map<String, NavigableMap<Long, StoredPartyState>> _claimsByGroup = new HashMap<>();
	private final ArrayDeque<String> _dueGroups = new ArrayDeque<>();
	private final Set<String> _dueGroupSet = new HashSet<>();
	private final AtomicInteger _operationClaims = new AtomicInteger();
	private final AtomicInteger _persistenceClaims = new AtomicInteger();
	private final AtomicLong _socialEventsRecorded = new AtomicLong();
	private final AtomicLong _socialEventFailures = new AtomicLong();
	private final PhantomPartyMetrics _metrics = new PhantomPartyMetrics();
	private volatile State _state = State.NEW;
	private volatile DeliveryRegistration _deliveryRegistration;
	private volatile int _lastPulseExamined;
	private volatile int _maximumPulseExamined;
	private int _pulseLane;

	public PhantomPartyCoordinator(PhantomPartyPersistencePort store, PhantomGoalStore goals, PhantomPartyBackend backend, PhantomPartyRoleCatalog roleCatalog, PhantomPartyRouteCoordinator routes, PhantomPartyTactics tactics, Supplier<String> topologyHash, LongSupplier clock, int operationBudget)
	{
		this(store, goals, backend, roleCatalog, routes, tactics, topologyHash, clock, operationBudget, PhantomSocialEventSink.noop(), () -> System.currentTimeMillis() / 60000L);
	}

	public PhantomPartyCoordinator(PhantomPartyPersistencePort store, PhantomGoalStore goals, PhantomPartyBackend backend, PhantomPartyRoleCatalog roleCatalog, PhantomPartyRouteCoordinator routes, PhantomPartyTactics tactics, Supplier<String> topologyHash, LongSupplier clock, int operationBudget, PhantomSocialEventSink socialEvents, LongSupplier socialClock)
	{
		_store = Objects.requireNonNull(store);
		_goals = Objects.requireNonNull(goals);
		_backend = Objects.requireNonNull(backend);
		_roleCatalog = Objects.requireNonNull(roleCatalog);
		_roles = new PhantomPartyRoleMatcher(roleCatalog);
		_routes = Objects.requireNonNull(routes);
		_tactics = Objects.requireNonNull(tactics);
		_topologyHash = Objects.requireNonNull(topologyHash);
		_clock = Objects.requireNonNull(clock);
		_socialEvents = Objects.requireNonNull(socialEvents);
		_socialClock = Objects.requireNonNull(socialClock);
		if ((operationBudget < 10) || (operationBudget > 10000))
		{
			throw new IllegalArgumentException("Party operation budget must be between 10 and 10000.");
		}
		_operationBudget = operationBudget;
	}

	public boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}
		_deliveryRegistration = PartyInvitationService.getInstance().installManagedDelivery(this);
		try
		{
			long cursor = 0;
			while (true)
			{
				final List<StoredPartyState> page = readPage(cursor);
				if (page.isEmpty())
				{
					break;
				}
				for (StoredPartyState stored : page)
				{
					final StoredPartyState recovered = sanitizeAfterRestart(stored);
					putClaim(recovered);
					cursor = stored.profileId();
				}
				if (page.size() < PAGE_SIZE)
				{
					break;
				}
			}
			rebuildGroups();
			_state = State.RUNNING;
			return true;
		}
		catch (RuntimeException e)
		{
			_deliveryRegistration.close();
			_deliveryRegistration = null;
			_state = State.STOPPED;
			throw e;
		}
	}

	public CommandOutcome form(long leaderProfileId, long goalId, long goalRevision, ObjectiveMode objective, PhantomDomainRef objectiveRef, List<RoleRequirement> requirements)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return CommandOutcome.NOT_RUNNING;
		}
		try (control)
		{
			if (!exactGoal(leaderProfileId, goalId, goalRevision, FORM_GOAL, null))
			{
				return CommandOutcome.GOAL_MISMATCH;
			}
			final String groupId = PhantomPartyModel.stableGroupId(leaderProfileId, goalId, goalRevision);
			final StoredPartyState existing = _claims.get(leaderProfileId);
			long expectedRowVersion = -1;
			long groupGeneration = 1;
			long membershipRevision = 0;
			if (existing != null)
			{
				final PartyOperation live = existing.state().operation();
				if (existing.state().groupId().equals(groupId) && (live != null) && (live.leaderGoalId() == goalId) && (live.leaderGoalRevision() == goalRevision) && (live.phase() != OperationPhase.ABORTED))
				{
					return CommandOutcome.IDEMPOTENT;
				}
				if ((existing.state().status() != StateStatus.SOLO) || ((live != null) && (live.leaderGoalId() == goalId) && (live.leaderGoalRevision() == goalRevision)))
				{
					return CommandOutcome.CLAIM_EXISTS;
				}
				expectedRowVersion = existing.rowVersion();
				groupGeneration = existing.state().groupGeneration() + 1;
				membershipRevision = existing.state().membershipRevision() + 1;
			}
			final MemberRef leader = _backend.currentMember(leaderProfileId).orElse(null);
			if (leader == null)
			{
				return CommandOutcome.TARGET_UNAVAILABLE;
			}
			final PartyState provisional = state(groupId, groupGeneration, membershipRevision, StateStatus.FORMING, leader, List.of(leader), List.of(), objective, objectiveRef, requirements, List.of(), null, null, progressionHash(leader), "");
			final String manifest = provisional.canonicalManifestHash();
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.stableOperationId(groupId, groupGeneration, membershipRevision, OperationKind.FORM, leader, null, goalId, goalRevision, manifest), OperationKind.FORM, OperationPhase.PREPARED, leader, null, goalId, goalRevision, manifest, 0, deadline(), "");
			final PartyState prepared = state(groupId, groupGeneration, membershipRevision, StateStatus.FORMING, leader, List.of(leader), List.of(), objective, objectiveRef, requirements, List.of(), null, operation, progressionHash(leader), "");
			try
			{
				final StoredPartyState stored = save(leaderProfileId, expectedRowVersion, prepared);
				putClaim(stored);
				if ((existing != null) && !existing.state().groupId().equals(groupId))
				{
					removeGroup(existing.state().groupId());
				}
				ensureGroup(groupId);
				return CommandOutcome.ACCEPTED;
			}
			catch (RuntimeException e)
			{
				_metrics.conflict();
				return CommandOutcome.PERSISTENCE_CONFLICT;
			}
		}
	}

	public CommandOutcome invite(long leaderProfileId, MemberRef target, PartyDistributionType distribution)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return CommandOutcome.NOT_RUNNING;
		}
		try (control)
		{
		final StoredPartyState stored = _claims.get(leaderProfileId);
		if ((stored == null) || (stored.state().leader().profileId() != leaderProfileId))
		{
			return CommandOutcome.NOT_LEADER;
		}
		if ((stored.state().phantomMembers().size() + stored.state().realMembers().size()) >= PhantomPartyModel.MAX_ROSTER)
		{
			return CommandOutcome.ROSTER_FULL;
		}
		final StoredPartyState previousMemberClaim;
		if (target.kind() == MemberKind.PHANTOM)
		{
			if ((target.profileId() == leaderProfileId) || committedClaim(_claims.get(target.profileId())))
			{
				return CommandOutcome.CLAIM_EXISTS;
			}
			previousMemberClaim = _claims.get(target.profileId());
		}
		else
		{
			previousMemberClaim = null;
		}
		final PartyState current = stored.state();
		final PartyOperation base = current.operation();
		if ((base != null) && (base.kind() == OperationKind.JOIN) && (base.phase() != OperationPhase.ABORTED))
		{
			return target.equals(base.member()) ? CommandOutcome.IDEMPOTENT : CommandOutcome.CLAIM_EXISTS;
		}
		final long goalId = base == null ? 1 : base.leaderGoalId();
		final long goalRevision = base == null ? 0 : base.leaderGoalRevision();
		final String operationId = PhantomPartyModel.stableOperationId(current.groupId(), current.groupGeneration(), current.membershipRevision(), OperationKind.JOIN, current.leader(), target, goalId, goalRevision, current.leaderManifestHash());
		final PartyOperation operation = new PartyOperation(operationId, OperationKind.JOIN, OperationPhase.PREPARED, current.leader(), target, goalId, goalRevision, current.leaderManifestHash(), 0, deadline(), "");
		final StoredPartyState prepared;
		try
		{
			prepared = save(leaderProfileId, stored.rowVersion(), current.withOperation(StateStatus.INVITED_OUTBOUND, operation, ""));
			putClaim(prepared);
		}
		catch (RuntimeException e)
		{
			return CommandOutcome.PERSISTENCE_CONFLICT;
		}
		final StoredPartyState preparedMember;
		if (target.kind() == MemberKind.PHANTOM)
		{
			try
			{
				final List<MemberRef> phantomMembers = java.util.stream.Stream.concat(current.phantomMembers().stream(), java.util.stream.Stream.of(target)).distinct().sorted(Comparator.comparing(MemberRef::stableKey)).toList();
				final PartyState memberClaim = new PartyState(current.groupId(), current.groupGeneration(), current.membershipRevision(), StateStatus.INVITED_INBOUND, current.leader(), "", current.leaderManifestHash(), phantomMembers, current.realMembers(), current.objectiveMode(), current.objectiveRef(), current.requirements(), current.assignments(), current.route(), operation, progressionHash(target), current.topologyHash(), "");
				preparedMember = save(target.profileId(), previousMemberClaim == null ? -1 : previousMemberClaim.rowVersion(), memberClaim);
				putClaim(preparedMember);
			}
			catch (RuntimeException e)
			{
				abort(prepared, "invite.member_claim_conflict");
				return CommandOutcome.PERSISTENCE_CONFLICT;
			}
		}
		else
		{
			preparedMember = null;
		}
		final InviteResult result = _backend.invite(current.leader(), target, distribution);
		if (!result.delivered())
		{
			rollbackPreparedMember(target, previousMemberClaim, preparedMember, "invite.canonical_rejected");
			abort(prepared, "invite.canonical_rejected");
			return CommandOutcome.CANONICAL_REJECTED;
		}
		final StoredPartyState exactLeader = _claims.get(leaderProfileId);
		if ((exactLeader == null) || !sameInvitation(exactLeader.state().operation(), result.identity()))
		{
			PartyInvitationService.getInstance().cancel(result.identity());
			rollbackPreparedMember(target, previousMemberClaim, preparedMember, "invite.leader_claim_conflict");
			return CommandOutcome.PERSISTENCE_CONFLICT;
		}
		_metrics.inviteDelivered();
		return CommandOutcome.ACCEPTED;
		}
	}

	public CommandOutcome inviteTarget(long leaderProfileId, PhantomDomainRef target, PartyDistributionType distribution)
	{
		if (target == null)
		{
			return CommandOutcome.TARGET_UNAVAILABLE;
		}
		try
		{
			if ("profile".equals(target.namespace()))
			{
				final MemberRef member = _backend.currentMember(Long.parseLong(target.key())).orElse(null);
				return member == null ? CommandOutcome.TARGET_UNAVAILABLE : invite(leaderProfileId, member, distribution);
			}
			if ("character.object".equals(target.namespace()))
			{
				final int objectId = Integer.parseInt(target.key());
				final OptionalLong managed = _backend.managedProfileId(objectId);
				return invite(leaderProfileId, managed.isPresent() ? MemberRef.phantom(managed.getAsLong(), objectId) : MemberRef.real(objectId), distribution);
			}
		}
		catch (NumberFormatException e)
		{
			return CommandOutcome.TARGET_UNAVAILABLE;
		}
		return CommandOutcome.TARGET_UNAVAILABLE;
	}

	public boolean committed(long profileId)
	{
		return committedClaim(_claims.get(profileId));
	}

	@Override
	public boolean blocksBackground(long profileId)
	{
		final StoredPartyState claim = _claims.get(profileId);
		if (claim == null)
		{
			return false;
		}
		if (Set.of(StateStatus.LEADER, StateStatus.MEMBER, StateStatus.RECOVERING).contains(claim.state().status()))
		{
			return true;
		}
		final PartyOperation operation = claim.state().operation();
		return (operation != null) && Set.of(OperationPhase.CANONICAL_PENDING, OperationPhase.CANONICAL_OBSERVED).contains(operation.phase());
	}

	public RouteOutcome requestRoute(long leaderProfileId, PhantomDomainRef destinationRef, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return RouteOutcome.NOT_RUNNING;
		}
		try (control)
		{
			final StoredPartyState claim = _claims.get(leaderProfileId);
			if ((claim == null) || (claim.state().status() != StateStatus.LEADER) || (claim.state().leader().kind() != MemberKind.PHANTOM) || (claim.state().leader().profileId() != leaderProfileId))
			{
				return RouteOutcome.NOT_PHANTOM_LEADER;
			}
			final MemberSnapshot leader = _backend.memberSnapshot(claim.state().leader()).orElse(null);
			if (leader == null)
			{
				return RouteOutcome.UNAVAILABLE;
			}
			final Optional<RouteManifest> immediate = _routes.request(claim.state().groupId(), claim.state().groupGeneration(), leader, destinationRef, destination, claim.state().topologyHash(), Math.max(0, _clock.getAsLong()), deadline());
			if (immediate.isPresent())
			{
				persistRoute(claim.state().groupId(), immediate.get());
				return RouteOutcome.ACCEPTED;
			}
			return RouteOutcome.PENDING;
		}
	}

	public CommandOutcome leave(long profileId, long goalId, long goalRevision, long expectedGeneration)
	{
		return membership(profileId, goalId, goalRevision, expectedGeneration, LEAVE_GOAL, OperationKind.LEAVE, null);
	}

	public CommandOutcome expelTarget(long profileId, long goalId, long goalRevision, long expectedGeneration, PhantomDomainRef target)
	{
		final MemberRef member = resolveMember(target);
		return member == null ? CommandOutcome.TARGET_UNAVAILABLE : membership(profileId, goalId, goalRevision, expectedGeneration, EXPEL_GOAL, OperationKind.EXPEL, member);
	}

	public CommandOutcome transferLeaderTarget(long profileId, long goalId, long goalRevision, long expectedGeneration, PhantomDomainRef target)
	{
		final MemberRef member = resolveMember(target);
		return member == null ? CommandOutcome.TARGET_UNAVAILABLE : membership(profileId, goalId, goalRevision, expectedGeneration, TRANSFER_LEADER_GOAL, OperationKind.TRANSFER_LEADER, member);
	}

	public CommandOutcome travel(long profileId, long goalId, long goalRevision, long expectedGeneration, PhantomDomainRef destinationRef, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return CommandOutcome.NOT_RUNNING;
		}
		try (control)
		{
			final StoredPartyState claim = exactCommandClaim(profileId, goalId, goalRevision, expectedGeneration, TRAVEL_GOAL, null);
			if (claim == null)
			{
				return commandMismatch(profileId, goalId, goalRevision, expectedGeneration, TRAVEL_GOAL, null);
			}
			final PartyOperation live = claim.state().operation();
			if ((live != null) && (live.kind() == OperationKind.ROUTE) && (live.leaderGoalId() == goalId) && (live.leaderGoalRevision() == goalRevision) && (live.phase() == OperationPhase.COMMITTED))
			{
				return CommandOutcome.IDEMPOTENT;
			}
			final PartyOperation operation = operation(claim.state(), OperationKind.ROUTE, null, goalId, goalRevision);
			try
			{
				putClaim(save(profileId, claim.rowVersion(), claim.state().withOperation(claim.state().status(), operation, "")));
			}
			catch (RuntimeException e)
			{
				return CommandOutcome.PERSISTENCE_CONFLICT;
			}
			final RouteOutcome route = requestRoute(profileId, destinationRef, destination);
			return Set.of(RouteOutcome.ACCEPTED, RouteOutcome.PENDING).contains(route) ? CommandOutcome.ACCEPTED : CommandOutcome.CANONICAL_REJECTED;
		}
	}

	@Override
	public OptionalLong managedIdentity(int characterObjectId)
	{
		return _state == State.RUNNING ? _backend.managedProfileId(characterObjectId) : OptionalLong.empty();
	}

	@Override
	public PreparationOutcome prepare(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return PreparationOutcome.STOPPING;
		}
		try (control)
		{
			StoredPartyState previousLeader = null;
			StoredPartyState pendingLeader = null;
			StoredPartyState previousMember = null;
			StoredPartyState pendingMember = null;
			try
			{
				if (managedRequester.isPresent())
				{
					previousLeader = _claims.get(managedRequester.getAsLong());
					if ((previousLeader == null) || !sameParticipants(previousLeader.state().operation(), invitation.identity()) || (previousLeader.state().operation().phase() != OperationPhase.PREPARED))
					{
						return PreparationOutcome.REJECTED;
					}
					final PartyOperation exact = previousLeader.state().operation().withPhase(OperationPhase.CANONICAL_PENDING, invitation.identity().sequence(), "");
					pendingLeader = save(previousLeader.profileId(), previousLeader.rowVersion(), previousLeader.state().withOperation(StateStatus.INVITED_OUTBOUND, exact, ""));
					putClaim(pendingLeader);
				}
				if (managedInvitee.isPresent())
				{
					previousMember = _claims.get(managedInvitee.getAsLong());
					final PartyState memberState;
					if ((previousMember != null) && sameParticipants(previousMember.state().operation(), invitation.identity()) && (previousMember.state().operation().phase() == OperationPhase.PREPARED))
					{
						final PartyOperation exact = previousMember.state().operation().withPhase(OperationPhase.CANONICAL_PENDING, invitation.identity().sequence(), "");
						memberState = previousMember.state().withOperation(StateStatus.INVITED_INBOUND, exact, "");
					}
					else if (managedRequester.isEmpty())
					{
						final MemberRef member = _backend.currentMember(managedInvitee.getAsLong()).orElse(null);
						final StoredGoal goal = _goals.load(managedInvitee.getAsLong()).orElse(null);
						if ((member == null) || (goal == null) || (goal.goal().status() != PhantomGoalStatus.ACTIVE) || !JOIN_GOAL.equals(goal.goal().goalType()) || !goalTargets(goal.goal(), invitation.requesterObjectId()))
						{
							return PreparationOutcome.REJECTED;
						}
						final MemberRef leader = MemberRef.real(invitation.requesterObjectId());
						final String groupId = PhantomPartyModel.sha256("party.real.pending|" + invitation.identity().sequence());
						final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256(groupId + "|join|" + member.profileId()), OperationKind.JOIN, OperationPhase.CANONICAL_PENDING, leader, member, goal.goal().goalId(), goal.goal().revision(), ZERO_HASH, invitation.identity().sequence(), deadline(), "");
						memberState = state(groupId, 1, 0, StateStatus.INVITED_INBOUND, leader, List.of(member), List.of(leader), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "real-led"), List.of(), List.of(), null, operation, progressionHash(member), "");
					}
					else
					{
						throw new IllegalStateException("Managed invitation member preparation is not exact.");
					}
					pendingMember = save(managedInvitee.getAsLong(), previousMember == null ? -1 : previousMember.rowVersion(), memberState);
					putClaim(pendingMember);
				}
				return PreparationOutcome.ACCEPTED;
			}
			catch (RuntimeException e)
			{
				rollbackPreparation(previousLeader, pendingLeader, previousMember, pendingMember);
				return PreparationOutcome.REJECTED;
			}
		}
	}

	@Override
	public DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return DeliveryOutcome.STOPPING;
		}
		try (control)
		{
			return _inbound.offer(new ManagedInvitation(invitation, managedIdentity)) ? DeliveryOutcome.ACCEPTED : DeliveryOutcome.BACKPRESSURE;
		}
	}

	@Override
	public void terminal(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, TerminalOutcome outcome, String reasonKey)
	{
		final OperationClaim control = beginTerminalOperation();
		if (control == null)
		{
			return;
		}
		try (control)
		{
			managedInvitee.ifPresent(profileId -> _inbound.removeIf(entry -> (entry.profileId() == profileId) && entry.invitation().identity().equals(invitation.identity())));
			final TerminalEvent event = new TerminalEvent(invitation, managedRequester, managedInvitee, outcome, reasonKey);
			if (_state == State.STOPPING)
			{
				processTerminal(event);
			}
			else if (!_terminalEvents.offer(event))
			{
				processTerminal(event);
			}
		}
	}

	@Override
	public void onPulse()
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return;
		}
		try (control)
		{
			_metrics.pulse();
			int used = 0;
			int emptyLanes = 0;
			while ((used < _operationBudget) && (emptyLanes < 4))
			{
				final int lane = Math.floorMod(_pulseLane++, 4);
				int examined = 0;
				if (lane == 0)
				{
					final TerminalEvent event = _terminalEvents.poll();
					if (event != null)
					{
						examined = 1 + (event.managedRequester().isPresent() ? 1 : 0) + (event.managedInvitee().isPresent() ? 1 : 0);
						if ((used + examined) <= _operationBudget)
						{
							processTerminal(event);
						}
						else
						{
							_terminalEvents.offer(event);
							examined = 0;
						}
					}
				}
				else if (lane == 1)
				{
					final ManagedInvitation invitation = _inbound.poll();
					if (invitation != null)
					{
						processManagedInvitation(invitation);
						examined = 1;
					}
				}
				else if (lane == 2)
				{
					final Long profileId = _tacticalReleases.poll();
					if (profileId != null)
					{
						releaseTacticalAction(profileId);
						examined = 1;
					}
				}
				else
				{
					final GroupRuntime group = pollDueGroup();
					if (group != null)
					{
						final int required = 1 + claimCount(group.groupId());
						if ((used + required) <= _operationBudget)
						{
							examined = reconcile(group, _operationBudget - used);
							if (_groups.containsKey(group.groupId()))
							{
								scheduleGroup(group.groupId());
							}
						}
						else
						{
							scheduleGroup(group.groupId());
						}
					}
				}
				if (examined == 0)
				{
					emptyLanes++;
					continue;
				}
				emptyLanes = 0;
				used += examined;
				for (int operation = 0; operation < examined; operation++)
				{
					_metrics.operation();
				}
			}
			if (used >= _operationBudget)
			{
				_metrics.budgetExhausted();
			}
			_lastPulseExamined = used;
			_maximumPulseExamined = Math.max(_maximumPulseExamined, used);
		}
	}

	public void beginStop()
	{
		synchronized (_lifecycleLock)
		{
			if ((_state == State.STOPPING) || (_state == State.STOPPED))
			{
				return;
			}
			_state = State.STOPPING;
		}
		final DeliveryRegistration registration = _deliveryRegistration;
		if (registration != null)
		{
			registration.close();
			_deliveryRegistration = null;
		}
		_inbound.clear();
		_routes.beginStop();
		_tacticalActions.values().forEach(ExternalActionLease::close);
		_tacticalActions.clear();
		_tacticalReleases.clear();
	}

	public boolean finishStop()
	{
		if (_state == State.NEW)
		{
			_state = State.STOPPED;
			return true;
		}
		if (_state == State.RUNNING)
		{
			beginStop();
		}
		final PhantomPartyRouteCoordinator.Snapshot route = _routes.snapshot();
		final DeliveryRegistration registration = _deliveryRegistration;
		if ((_operationClaims.get() != 0) || (_persistenceClaims.get() != 0) || !_terminalEvents.isEmpty() || !_inbound.isEmpty() || !_tacticalReleases.isEmpty() || !_tacticalActions.isEmpty() || ((registration != null) && (registration.pendingInvitations() != 0)) || (route.navigationClaims() != 0) || (route.movementClaims() != 0))
		{
			return false;
		}
		synchronized (_lifecycleLock)
		{
			if (_operationClaims.get() != 0)
			{
				return false;
			}
			_state = State.STOPPED;
		}
		return true;
	}

	public Snapshot snapshot()
	{
		final PhantomPartyRouteCoordinator.Snapshot route = _routes.snapshot();
		return new Snapshot(_state, _claims.size(), _groups.size(), _terminalEvents.size(), _inbound.size(), _operationClaims.get(), _persistenceClaims.get(), route.navigationClaims(), route.movementClaims(), _tacticalActions.size(), _operationBudget, _lastPulseExamined, _maximumPulseExamined, _socialEventsRecorded.get(), _socialEventFailures.get(), _metrics.snapshot());
	}

	public Optional<StoredPartyState> claim(long profileId)
	{
		return Optional.ofNullable(_claims.get(profileId));
	}

	private void processManagedInvitation(ManagedInvitation managed)
	{
		final PartyInvitation invitation = managed.invitation();
		final MemberRef invitee = _backend.currentMember(managed.profileId()).orElse(null);
		if (invitee == null)
		{
			PartyInvitationService.getInstance().cancel(invitation.identity());
			return;
		}
		final StoredGoal storedGoal = _goals.load(managed.profileId()).orElse(null);
		final boolean explicitConsent = (storedGoal != null) && (storedGoal.goal().status() == PhantomGoalStatus.ACTIVE) && JOIN_GOAL.equals(storedGoal.goal().goalType()) && goalTargets(storedGoal.goal(), invitation.requesterObjectId());
		if (!explicitConsent)
		{
			_backend.respond(invitee, Response.REFUSE, invitation.identity());
			_metrics.inviteRefused();
			return;
		}
		final PartyInvitationService.RespondResult response = _backend.respond(invitee, Response.ACCEPT, invitation.identity());
		if (!response.accepted())
		{
			_metrics.inviteRefused();
			return;
		}
		_metrics.inviteAccepted();
	}

	private void processTerminal(TerminalEvent event)
	{
		final PartyInvitation invitation = event.invitation();
		if (event.outcome() != TerminalOutcome.ACCEPTED)
		{
			abortManagedInvitation(invitation, event.managedRequester(), event.managedInvitee(), event.reasonKey());
			emitInvitationTerminal(event);
			return;
		}
		event.managedRequester().ifPresent(profileId -> markObservedExact(profileId, invitation.identity()));
		event.managedInvitee().ifPresent(profileId -> markObservedExact(profileId, invitation.identity()));
		if (event.managedRequester().isPresent())
		{
			ensureGroup(groupForLeader(event.managedRequester().getAsLong()));
		}
		if (event.managedInvitee().isPresent())
		{
			final long profileId = event.managedInvitee().getAsLong();
			final MemberRef invitee = _backend.currentMember(profileId).orElse(null);
			final Optional<PartySnapshot> observed = invitee == null ? Optional.empty() : _backend.observe(invitee);
			if (observed.isPresent() && (observed.get().leader().kind() == MemberKind.REAL))
			{
				commitRealLedMember(profileId, _goals.load(profileId).orElse(null), observed.get(), invitation.identity());
			}
			else if (observed.isPresent())
			{
				ensureGroup(groupForLeader(observed.get().leader().profileId()));
			}
		}
		emitInvitationTerminal(event);
	}

	private void markObservedExact(long profileId, InvitationIdentity identity)
	{
		final StoredPartyState current = _claims.get(profileId);
		if ((current == null) || !sameInvitation(current.state().operation(), identity))
		{
			return;
		}
		try
		{
			final PartyOperation observed = current.state().operation().withPhase(OperationPhase.CANONICAL_OBSERVED, identity.sequence(), "");
			putClaim(save(profileId, current.rowVersion(), current.state().withOperation(current.state().status(), observed, "")));
		}
		catch (RuntimeException e)
		{
			markInconsistent(current, "invite.observation_conflict");
		}
	}

	private void rollbackPreparation(StoredPartyState previousLeader, StoredPartyState pendingLeader, StoredPartyState previousMember, StoredPartyState pendingMember)
	{
		rollbackPreparationSide(previousMember, pendingMember, "invite.member_prepare_rollback_conflict");
		rollbackPreparationSide(previousLeader, pendingLeader, "invite.leader_prepare_rollback_conflict");
	}

	private void rollbackPreparationSide(StoredPartyState previous, StoredPartyState pending, String failure)
	{
		if (pending == null)
		{
			return;
		}
		final StoredPartyState current = _claims.get(pending.profileId());
		if ((current == null) || (current.rowVersion() != pending.rowVersion()))
		{
			return;
		}
		if (previous == null)
		{
			moveToSolo(current, failure);
			return;
		}
		try
		{
			putClaim(save(current.profileId(), current.rowVersion(), previous.state()));
		}
		catch (RuntimeException e)
		{
			markInconsistent(current, failure);
		}
	}

	private int reconcile(GroupRuntime runtime, int remainingBudget)
	{
		final List<StoredPartyState> groupClaims = claims(runtime.groupId());
		int examined = 1 + groupClaims.size();
		if (examined > remainingBudget)
		{
			return 0;
		}
		if (groupClaims.isEmpty())
		{
			removeGroup(runtime.groupId());
			return examined;
		}
		for (StoredPartyState claim : groupClaims)
		{
			final PartyOperation operation = claim.state().operation();
			if ((operation != null) && (operation.phase() == OperationPhase.CANONICAL_PENDING) && (operation.invitationSequence() > 0) && (_clock.getAsLong() >= operation.deadlineLogicalNanos()) && (operation.member() != null))
			{
				final InvitationIdentity identity = new InvitationIdentity(operation.invitationSequence(), operation.leader().characterObjectId(), operation.member().characterObjectId());
				if (!PartyInvitationService.getInstance().expire(identity))
				{
					PartyInvitationService.getInstance().cancel(identity);
				}
				return examined;
			}
			if ((operation != null) && (operation.phase() == OperationPhase.ABORTED))
			{
				return examined;
			}
		}
		MemberRef leader = groupClaims.getFirst().state().leader();
		if (leader.kind() != MemberKind.PHANTOM)
		{
			return examined;
		}
		final List<MemberRef> expected = groupClaims.stream().flatMap(claim -> claim.state().phantomMembers().stream()).distinct().sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		if (!_backend.materialize(leader.profileId()))
		{
			final MemberRef elected = expected.stream().min(Comparator.comparingLong(MemberRef::profileId)).orElse(leader);
			if (!elected.equals(leader) && _backend.materialize(elected.profileId()))
			{
				electLeader(groupClaims, elected);
				leader = elected;
			}
			else
			{
				return examined;
			}
		}
		final Optional<PartySnapshot> observed = _backend.observe(leader);
		if (observed.isEmpty())
		{
			final MemberRef expectedLeader = leader;
			final MemberRef missing = expected.stream().filter(member -> !member.equals(expectedLeader)).filter(member -> _backend.materialize(member.profileId())).findFirst().orElse(null);
			if (missing != null)
			{
				_backend.invite(leader, missing, PartyDistributionType.FINDERS_KEEPERS);
			}
			return examined;
		}
		final PartySnapshot party = observed.get();
		if (!party.leader().equals(leader) && (party.leader().kind() == MemberKind.PHANTOM))
		{
			electLeader(groupClaims, party.leader());
			leader = party.leader();
		}
		final MemberRef missing = expected.stream().filter(member -> !party.members().contains(member)).filter(member -> _backend.materialize(member.profileId())).findFirst().orElse(null);
		if (missing != null)
		{
			_backend.invite(leader, missing, party.distribution());
			return examined;
		}
		commitObserved(groupClaims, party);
		examined += advanceRoute(runtime.groupId(), party, remainingBudget - examined);
		if (examined < remainingBudget)
		{
			examined += dispatchTactics(party, remainingBudget - examined);
		}
		return Math.min(remainingBudget, examined);
	}

	private int advanceRoute(String groupId, PartySnapshot party, int remainingBudget)
	{
		final List<StoredPartyState> claims = claims(groupId);
		if (claims.isEmpty())
		{
			return 0;
		}
		RouteManifest route = claims.getFirst().state().route();
		if (route == null)
		{
			route = _routes.poll(groupId).orElse(null);
			if (route == null)
			{
				return 0;
			}
			persistRoute(groupId, route);
		}
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(party.members());
		final PhantomPartyRouteCoordinator.AdvanceResult result = _routes.advance(groupId, route, party.leader(), party.members(), snapshots, Math.max(1, remainingBudget), Math.max(0, _clock.getAsLong()), _topologyHash.get(), () -> _state != State.RUNNING);
		final RouteManifest advanced = result.route();
		if (!advanced.equals(route))
		{
			persistRoute(groupId, advanced);
		}
		return result.examinedOperations();
	}

	private void persistRoute(String groupId, RouteManifest route)
	{
		for (StoredPartyState claim : claims(groupId))
		{
			final PartyState state = claim.state();
			final PartyOperation base = state.operation();
			final long goalId = base == null ? 1 : base.leaderGoalId();
			final long goalRevision = base == null ? 0 : base.leaderGoalRevision();
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.stableOperationId(state.groupId(), state.groupGeneration(), state.membershipRevision(), OperationKind.ROUTE, state.leader(), null, goalId, goalRevision, state.leaderManifestHash()), OperationKind.ROUTE, OperationPhase.COMMITTED, state.leader(), null, goalId, goalRevision, state.leaderManifestHash(), 0, deadline(), "");
			final PartyState draft = new PartyState(state.groupId(), state.groupGeneration(), state.membershipRevision(), state.status(), state.leader(), state.ownRoleKey(), ZERO_HASH, state.phantomMembers(), state.realMembers(), state.objectiveMode(), route.destination(), state.requirements(), state.assignments(), route, operation, state.progressionHash(), state.topologyHash(), "");
			final PartyState next = new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), draft.route(), draft.operation(), draft.progressionHash(), draft.topologyHash(), draft.lastFailureKey());
			try
			{
				putClaim(save(claim.profileId(), claim.rowVersion(), next));
			}
			catch (RuntimeException e)
			{
				_metrics.conflict();
				return;
			}
		}
	}

	private void commitObserved(List<StoredPartyState> existing, PartySnapshot party)
	{
		final PartyState authority = existing.stream().filter(claim -> claim.state().leader().equals(party.leader())).findFirst().orElse(existing.getFirst()).state();
		final List<MemberRef> phantoms = party.members().stream().filter(member -> member.kind() == MemberKind.PHANTOM).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		final List<MemberRef> reals = party.members().stream().filter(member -> member.kind() == MemberKind.REAL).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(party.members());
		final RoleMatchResult roles = _roles.match(authority.objectiveMode(), authority.requirements(), new ArrayList<>(snapshots.values()));
		final boolean stable = authority.leader().equals(party.leader()) //
			&& authority.phantomMembers().equals(phantoms) //
			&& authority.realMembers().equals(reals) //
			&& authority.assignments().equals(roles.assignments()) //
			&& (existing.size() == phantoms.size()) //
			&& existing.stream().allMatch(claim -> Set.of(StateStatus.LEADER, StateStatus.MEMBER).contains(claim.state().status()) && claim.state().leaderManifestHash().equals(authority.leaderManifestHash()));
		if (stable)
		{
			ensureCommittedGoals(existing);
			return;
		}
		final PartyState draft = state(authority.groupId(), authority.groupGeneration(), authority.membershipRevision() + 1, StateStatus.LEADER, party.leader(), phantoms, reals, authority.objectiveMode(), authority.objectiveRef(), authority.requirements(), roles.assignments(), authority.route(), authority.operation(), progressionEvidence(snapshots), "");
		final String manifest = draft.canonicalManifestHash();
		for (MemberRef member : phantoms)
		{
			final StoredPartyState current = _claims.get(member.profileId());
			final String ownRole = roles.assignments().stream().filter(assignment -> assignment.member().equals(member)).map(RoleAssignment::roleKey).findFirst().orElse("");
			final PartyOperation operation = authority.operation() == null ? null : authority.operation().withPhase(OperationPhase.COMMITTED, authority.operation().invitationSequence(), "");
			final PartyState next = new PartyState(authority.groupId(), authority.groupGeneration(), draft.membershipRevision(), member.equals(party.leader()) ? StateStatus.LEADER : StateStatus.MEMBER, party.leader(), ownRole, manifest, phantoms, reals, authority.objectiveMode(), authority.objectiveRef(), authority.requirements(), roles.assignments(), authority.route(), operation, draft.progressionHash(), draft.topologyHash(), "");
			try
			{
				final StoredPartyState saved = save(member.profileId(), current == null ? -1 : current.rowVersion(), next);
				putClaim(saved);
				transitionGoal(member.profileId(), member.equals(party.leader()) ? LEAD_GOAL : MEMBER_GOAL, member.equals(party.leader()) || (current == null) ? authority.operation() : current.state().operation());
			}
			catch (RuntimeException e)
			{
				_metrics.conflict();
				return;
			}
		}
		_metrics.commit();
		emitJoined(phantoms, authority.operation());
	}

	private void commitRealLedMember(long profileId, StoredGoal joinGoal, PartySnapshot party, InvitationIdentity identity)
	{
		final MemberRef member = _backend.currentMember(profileId).orElse(null);
		if (member == null)
		{
			return;
		}
		final String groupId = PhantomPartyModel.sha256("party.real|" + party.leader().characterObjectId() + '|' + identity.sequence());
		final List<MemberRef> phantoms = party.members().stream().filter(value -> value.kind() == MemberKind.PHANTOM).toList();
		final List<MemberRef> reals = party.members().stream().filter(value -> value.kind() == MemberKind.REAL).toList();
		final long goalId = joinGoal == null ? identity.sequence() : joinGoal.goal().goalId();
		final long goalRevision = joinGoal == null ? 0 : joinGoal.goal().revision();
		final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256(groupId + "|join|" + profileId), OperationKind.JOIN, OperationPhase.COMMITTED, party.leader(), member, goalId, goalRevision, ZERO_HASH, identity.sequence(), deadline(), "");
		final PartyState draft = state(groupId, 1, 1, StateStatus.MEMBER, party.leader(), phantoms, reals, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "real-led"), List.of(), List.of(), null, operation, progressionHash(member), "");
		final PartyState committed = new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, operation, draft.progressionHash(), draft.topologyHash(), "");
		final StoredPartyState current = _claims.get(profileId);
		try
		{
			putClaim(save(profileId, current == null ? -1 : current.rowVersion(), committed));
			transitionGoal(profileId, MEMBER_GOAL, operation);
			emitJoined(phantoms, operation);
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
		}
	}

	private int dispatchTactics(PartySnapshot party, int remainingBudget)
	{
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(party.members());
		final List<TacticalDirective> directives = _tactics.plan(party.leader(), party.members(), snapshots);
		final Set<Long> occupied = Set.copyOf(_tacticalActions.keySet());
		int examined = 0;
		for (TacticalDirective directive : directives)
		{
			if (examined >= remainingBudget)
			{
				break;
			}
			examined++;
			if (occupied.contains(directive.actor().profileId()) || _tacticalActions.containsKey(directive.actor().profileId()))
			{
				continue;
			}
			final String key = "party.tactic." + directive.kind().name().toLowerCase(java.util.Locale.ROOT) + '.' + directive.actor().profileId();
			_tactics.dispatch(directive, key, deadline(), () -> _state != State.RUNNING).ifPresent(lease ->
			{
				_tacticalActions.put(directive.actor().profileId(), lease);
				_tacticalReleases.offer(directive.actor().profileId());
			});
		}
		return examined;
	}

	private void releaseTacticalAction(long profileId)
	{
		final ExternalActionLease lease = _tacticalActions.get(profileId);
		if (lease == null)
		{
			return;
		}
		final org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot actor = lease.actorSnapshot();
		if ((actor == null) || (!actor.attacking() && !actor.casting()))
		{
			lease.complete();
			_tacticalActions.remove(profileId, lease);
		}
		else
		{
			_tacticalReleases.offer(profileId);
		}
	}

	private StoredPartyState sanitizeAfterRestart(StoredPartyState stored)
	{
		final PartyState state = stored.state();
		if (Set.of(StateStatus.SOLO, StateStatus.RETIRED, StateStatus.INCONSISTENT).contains(state.status()))
		{
			return stored;
		}
		if ((state.leader().kind() == MemberKind.REAL) || state.phantomMembers().isEmpty())
		{
			final MemberRef self = _backend.currentMember(stored.profileId()).orElse(MemberRef.phantom(stored.profileId(), 0));
			final PartyState solo = state(PhantomPartyModel.sha256("party.solo|" + stored.profileId() + '|' + (state.groupGeneration() + 1)), state.groupGeneration() + 1, state.membershipRevision() + 1, StateStatus.SOLO, self, List.of(self), List.of(), state.objectiveMode(), state.objectiveRef(), List.of(), List.of(), null, null, state.progressionHash(), "restart.real_consent_not_restored");
			return save(stored.profileId(), stored.rowVersion(), solo);
		}
		if (!Set.of(StateStatus.LEADER, StateStatus.MEMBER, StateStatus.RECOVERING).contains(state.status()) && ((state.operation() == null) || (state.operation().phase().ordinal() < OperationPhase.CANONICAL_OBSERVED.ordinal())))
		{
			final MemberRef self = _backend.currentMember(stored.profileId()).orElse(MemberRef.phantom(stored.profileId(), 0));
			final PartyState solo = state(PhantomPartyModel.sha256("party.solo|" + stored.profileId() + '|' + (state.groupGeneration() + 1)), state.groupGeneration() + 1, state.membershipRevision() + 1, StateStatus.SOLO, self, List.of(self), List.of(), state.objectiveMode(), state.objectiveRef(), List.of(), List.of(), null, null, state.progressionHash(), "restart.uncommitted_operation_aborted");
			return save(stored.profileId(), stored.rowVersion(), solo);
		}
		final PartyState recovered = state(state.groupId(), state.groupGeneration(), state.membershipRevision(), StateStatus.RECOVERING, state.leader(), state.phantomMembers(), List.of(), state.objectiveMode(), state.objectiveRef(), state.requirements(), state.assignments(), state.route(), state.operation(), state.progressionHash(), "");
		return save(stored.profileId(), stored.rowVersion(), recovered);
	}

	private void rebuildGroups()
	{
		final Map<String, List<StoredPartyState>> grouped = new HashMap<>();
		for (StoredPartyState claim : _claims.values())
		{
			if (committedClaim(claim))
			{
				grouped.computeIfAbsent(claim.state().groupId(), ignored -> new ArrayList<>()).add(claim);
			}
		}
		for (Map.Entry<String, List<StoredPartyState>> entry : grouped.entrySet())
		{
			final Set<String> manifests = entry.getValue().stream().map(claim -> claim.state().leaderManifestHash()).collect(java.util.stream.Collectors.toSet());
			final Set<Long> generations = entry.getValue().stream().map(claim -> claim.state().groupGeneration()).collect(java.util.stream.Collectors.toSet());
			if ((manifests.size() != 1) || (generations.size() != 1))
			{
				for (StoredPartyState claim : entry.getValue())
				{
					markInconsistent(claim, "restart.claim_conflict");
				}
			}
			else
			{
				ensureGroup(entry.getKey());
				_metrics.recovery();
			}
		}
	}

	private void electLeader(List<StoredPartyState> claims, MemberRef elected)
	{
		final long generation = claims.stream().mapToLong(claim -> claim.state().groupGeneration()).max().orElse(0) + 1;
		for (StoredPartyState claim : claims)
		{
			final PartyState current = claim.state();
			final PartyState draft = state(current.groupId(), generation, current.membershipRevision() + 1, claim.profileId() == elected.profileId() ? StateStatus.LEADER : StateStatus.MEMBER, elected, current.phantomMembers(), List.of(), current.objectiveMode(), current.objectiveRef(), current.requirements(), current.assignments(), current.route(), current.operation(), current.progressionHash(), "");
			final PartyState next = new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), draft.route(), draft.operation(), draft.progressionHash(), draft.topologyHash(), "");
			try
			{
				putClaim(save(claim.profileId(), claim.rowVersion(), next));
			}
			catch (RuntimeException e)
			{
				markInconsistent(claim, "leader.election_conflict");
			}
		}
	}

	private void transitionGoal(long profileId, String nextType, PartyOperation operation)
	{
		if (operation == null)
		{
			return;
		}
		final Optional<StoredGoal> found = _goals.load(profileId);
		if (found.isEmpty())
		{
			return;
		}
		final PhantomGoal goal = found.get().goal();
		if (goal.status() != PhantomGoalStatus.ACTIVE)
		{
			return;
		}
		if (nextType.equals(LEAD_GOAL))
		{
			if ((goal.goalId() != operation.leaderGoalId()) || (goal.revision() != operation.leaderGoalRevision()) || !FORM_GOAL.equals(goal.goalType()))
			{
				return;
			}
		}
		else if (nextType.equals(MEMBER_GOAL))
		{
			if (!JOIN_GOAL.equals(goal.goalType()) || !goalTargets(goal, operation.leader().characterObjectId()))
			{
				return;
			}
		}
		else
		{
			return;
		}
		final PhantomGoal replacement = new PhantomGoal(goal.goalId(), nextType, PhantomGoalStatus.ACTIVE, goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), "party.membership.committed", goal.revision() + 1);
		try
		{
			_goals.replace(profileId, found.get().rowVersion(), replacement);
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
		}
	}

	private void ensureCommittedGoals(List<StoredPartyState> claims)
	{
		for (StoredPartyState claim : claims)
		{
			final PartyState state = claim.state();
			if (state.status() == StateStatus.LEADER)
			{
				transitionGoal(claim.profileId(), LEAD_GOAL, state.operation());
			}
			else if (state.status() == StateStatus.MEMBER)
			{
				transitionGoal(claim.profileId(), MEMBER_GOAL, state.operation());
			}
		}
	}

	private void abortManagedInvitation(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, String reasonKey)
	{
		final OptionalLong leaderProfileId = managedRequester.isPresent() ? managedRequester : _backend.managedProfileId(invitation.requesterObjectId());
		if (leaderProfileId.isPresent())
		{
			final StoredPartyState leader = _claims.get(leaderProfileId.getAsLong());
			if ((leader != null) && sameInvitation(leader.state().operation(), invitation.identity()))
			{
				abortFormation(leader, invitation.identity(), reasonKey);
			}
		}
		if (managedInvitee.isEmpty())
		{
			return;
		}
		final StoredPartyState member = _claims.get(managedInvitee.getAsLong());
		if ((member != null) && Set.of(StateStatus.INVITED_INBOUND, StateStatus.JOINING).contains(member.state().status()) && sameInvitation(member.state().operation(), invitation.identity()))
		{
			moveToSolo(member, reasonKey);
		}
	}

	private void abortFormation(StoredPartyState stored, InvitationIdentity identity, String failure)
	{
		final StoredPartyState current = _claims.get(stored.profileId());
		if ((current == null) || (current.rowVersion() != stored.rowVersion()) || (current.state().status() != StateStatus.INVITED_OUTBOUND) || !sameInvitation(current.state().operation(), identity))
		{
			return;
		}
		final PartyState before = current.state();
		final PartyOperation aborted = before.operation().withPhase(OperationPhase.ABORTED, identity.sequence(), failure);
		final MemberRef self = before.phantomMembers().stream().filter(member -> (member.kind() == MemberKind.PHANTOM) && (member.profileId() == current.profileId())).findFirst().orElseGet(() -> _backend.currentMember(current.profileId()).orElse(MemberRef.phantom(current.profileId(), 0)));
		final PartyState solo = state(PhantomPartyModel.sha256("party.solo|" + current.profileId() + '|' + (before.groupGeneration() + 1)), before.groupGeneration() + 1, before.membershipRevision() + 1, StateStatus.SOLO, self, List.of(self), List.of(), before.objectiveMode(), before.objectiveRef(), List.of(), List.of(), null, aborted, before.progressionHash(), failure);
		try
		{
			putClaim(save(current.profileId(), current.rowVersion(), solo));
			failFormGoal(current.profileId(), aborted, failure);
			removeGroup(before.groupId());
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
		}
	}

	private void failFormGoal(long profileId, PartyOperation operation, String failure)
	{
		final Optional<StoredGoal> found = _goals.load(profileId);
		if (found.isEmpty())
		{
			return;
		}
		final PhantomGoal goal = found.get().goal();
		if ((goal.status() != PhantomGoalStatus.ACTIVE) || (goal.goalId() != operation.leaderGoalId()) || (goal.revision() != operation.leaderGoalRevision()) || !FORM_GOAL.equals(goal.goalType()))
		{
			return;
		}
		final PhantomGoal failed = new PhantomGoal(goal.goalId(), goal.goalType(), PhantomGoalStatus.FAILED, goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), failure, goal.revision() + 1);
		_goals.replace(profileId, found.get().rowVersion(), failed);
	}

	private void rollbackPreparedMember(MemberRef member, StoredPartyState previous, StoredPartyState prepared, String failure)
	{
		if ((member.kind() != MemberKind.PHANTOM) || (prepared == null))
		{
			return;
		}
		final StoredPartyState current = _claims.get(member.profileId());
		if ((current == null) || (current.rowVersion() != prepared.rowVersion()))
		{
			return;
		}
		if (previous == null)
		{
			moveToSolo(current, failure);
			return;
		}
		try
		{
			putClaim(save(member.profileId(), current.rowVersion(), previous.state()));
		}
		catch (RuntimeException e)
		{
			markInconsistent(current, "invite.member_rollback_conflict");
		}
	}

	private void moveToSolo(StoredPartyState stored, String failure)
	{
		final MemberRef self = stored.state().phantomMembers().stream().filter(member -> (member.kind() == MemberKind.PHANTOM) && (member.profileId() == stored.profileId())).findFirst().orElseGet(() -> _backend.currentMember(stored.profileId()).orElse(MemberRef.phantom(stored.profileId(), 0)));
		final PartyState current = stored.state();
		final PartyState solo = state(PhantomPartyModel.sha256("party.solo|" + stored.profileId() + '|' + (current.groupGeneration() + 1)), current.groupGeneration() + 1, current.membershipRevision() + 1, StateStatus.SOLO, self, List.of(self), List.of(), current.objectiveMode(), current.objectiveRef(), List.of(), List.of(), null, null, current.progressionHash(), failure);
		try
		{
			putClaim(save(stored.profileId(), stored.rowVersion(), solo));
		}
		catch (RuntimeException e)
		{
			markInconsistent(stored, "invite.member_abort_conflict");
		}
	}

	private static boolean sameInvitation(PartyOperation operation, InvitationIdentity identity)
	{
		return (operation != null) && (operation.invitationSequence() == identity.sequence()) && (operation.leader().characterObjectId() == identity.requesterObjectId()) && (operation.member() != null) && (operation.member().characterObjectId() == identity.inviteeObjectId());
	}

	private static boolean sameParticipants(PartyOperation operation, InvitationIdentity identity)
	{
		return (operation != null) && (operation.leader().characterObjectId() == identity.requesterObjectId()) && (operation.member() != null) && (operation.member().characterObjectId() == identity.inviteeObjectId());
	}

	private void abort(StoredPartyState stored, String failure)
	{
		final StoredPartyState current = _claims.get(stored.profileId());
		if ((current == null) || (current.rowVersion() != stored.rowVersion()))
		{
			return;
		}
		try
		{
			final PartyOperation operation = stored.state().operation();
			final PartyState aborted = stored.state().withOperation(StateStatus.FORMING, operation == null ? null : operation.withPhase(OperationPhase.ABORTED, operation.invitationSequence(), failure), failure);
			putClaim(save(stored.profileId(), stored.rowVersion(), aborted));
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
		}
	}

	private void markInconsistent(StoredPartyState stored, String failure)
	{
		try
		{
			putClaim(save(stored.profileId(), stored.rowVersion(), stored.state().withOperation(StateStatus.INCONSISTENT, stored.state().operation(), failure)));
		}
		catch (RuntimeException e)
		{
			_metrics.failure();
		}
	}

	private CommandOutcome membership(long profileId, long goalId, long goalRevision, long expectedGeneration, String goalType, OperationKind kind, MemberRef target)
	{
		final OperationClaim control = beginOperation();
		if (control == null)
		{
			return CommandOutcome.NOT_RUNNING;
		}
		try (control)
		{
			final StoredPartyState retry = _claims.get(profileId);
			final PartyOperation retryOperation = retry == null ? null : retry.state().operation();
			if ((retryOperation != null) && (retryOperation.kind() == kind) && (retryOperation.leaderGoalId() == goalId) && (retryOperation.leaderGoalRevision() == goalRevision) && (retryOperation.phase() == OperationPhase.COMMITTED) && exactCommandGoal(profileId, goalId, goalRevision, goalType, target))
			{
				return CommandOutcome.IDEMPOTENT;
			}
			final StoredPartyState claim = exactCommandClaim(profileId, goalId, goalRevision, expectedGeneration, goalType, target);
			if (claim == null)
			{
				return commandMismatch(profileId, goalId, goalRevision, expectedGeneration, goalType, target);
			}
			final PartyState current = claim.state();
			if ((kind != OperationKind.LEAVE) && ((current.status() != StateStatus.LEADER) || (current.leader().profileId() != profileId)))
			{
				return CommandOutcome.NOT_LEADER;
			}
			final MemberRef actor = current.phantomMembers().stream().filter(member -> member.profileId() == profileId).findFirst().orElse(null);
			if (actor == null)
			{
				return CommandOutcome.TARGET_UNAVAILABLE;
			}
			final PartyOperation preparedOperation = operation(current, kind, target, goalId, goalRevision);
			final StoredPartyState prepared;
			try
			{
				prepared = save(profileId, claim.rowVersion(), current.withOperation(kind == OperationKind.LEAVE ? StateStatus.LEAVING : current.status(), preparedOperation, ""));
				putClaim(prepared);
			}
			catch (RuntimeException e)
			{
				return CommandOutcome.PERSISTENCE_CONFLICT;
			}
			final MembershipOutcome canonical = switch (kind)
			{
				case LEAVE -> _backend.leave(actor);
				case EXPEL -> _backend.expel(actor, target);
				case TRANSFER_LEADER -> _backend.transferLeader(actor, target);
				default -> MembershipOutcome.INVALID_TARGET;
			};
			final PartySnapshot observed = observeAfterMembership(current, actor, target).orElse(null);
			if ((canonical != MembershipOutcome.COMPLETED) && !membershipPostcondition(kind, actor, target, observed, canonical))
			{
				abort(prepared, "party.membership.canonical_rejected");
				return CommandOutcome.CANONICAL_REJECTED;
			}
			if (!membershipPostcondition(kind, actor, target, observed, canonical))
			{
				markInconsistent(prepared, "party.membership.postcondition_failed");
				return CommandOutcome.CANONICAL_REJECTED;
			}
			if (!commitMembership(current, observed, preparedOperation))
			{
				return CommandOutcome.PERSISTENCE_CONFLICT;
			}
			emitMembership(current, actor, target, preparedOperation);
			return canonical == MembershipOutcome.COMPLETED ? CommandOutcome.ACCEPTED : CommandOutcome.IDEMPOTENT;
		}
	}

	private StoredPartyState exactCommandClaim(long profileId, long goalId, long goalRevision, long expectedGeneration, String goalType, MemberRef target)
	{
		final StoredPartyState claim = _claims.get(profileId);
		if ((claim == null) || (claim.state().groupGeneration() != expectedGeneration) || !exactCommandGoal(profileId, goalId, goalRevision, goalType, target))
		{
			return null;
		}
		return claim;
	}

	private CommandOutcome commandMismatch(long profileId, long goalId, long goalRevision, long expectedGeneration, String goalType, MemberRef target)
	{
		final StoredPartyState claim = _claims.get(profileId);
		if ((claim != null) && (claim.state().groupGeneration() != expectedGeneration))
		{
			return CommandOutcome.STALE_GENERATION;
		}
		return exactCommandGoal(profileId, goalId, goalRevision, goalType, target) ? CommandOutcome.CLAIM_EXISTS : CommandOutcome.GOAL_MISMATCH;
	}

	private boolean exactCommandGoal(long profileId, long goalId, long goalRevision, String type, MemberRef target)
	{
		final Optional<StoredGoal> stored = _goals.load(profileId);
		if (stored.isEmpty())
		{
			return false;
		}
		final PhantomGoal goal = stored.get().goal();
		if ((goal.status() != PhantomGoalStatus.ACTIVE) || (goal.goalId() != goalId) || (goal.revision() != goalRevision) || !type.equals(goal.goalType()))
		{
			return false;
		}
		if (target == null)
		{
			return true;
		}
		final PhantomDomainRef goalTarget = goal.target();
		return (goalTarget != null) && (("profile".equals(goalTarget.namespace()) && (target.kind() == MemberKind.PHANTOM) && Long.toString(target.profileId()).equals(goalTarget.key())) || ("character.object".equals(goalTarget.namespace()) && Integer.toString(target.characterObjectId()).equals(goalTarget.key())));
	}

	private PartyOperation operation(PartyState state, OperationKind kind, MemberRef member, long goalId, long goalRevision)
	{
		return new PartyOperation(PhantomPartyModel.stableOperationId(state.groupId(), state.groupGeneration(), state.membershipRevision(), kind, state.leader(), member, goalId, goalRevision, state.leaderManifestHash()), kind, OperationPhase.PREPARED, state.leader(), member, goalId, goalRevision, state.leaderManifestHash(), 0, deadline(), "");
	}

	private Optional<PartySnapshot> observeAfterMembership(PartyState before, MemberRef actor, MemberRef target)
	{
		for (MemberRef member : before.phantomMembers())
		{
			if (!member.equals(actor) && !member.equals(target))
			{
				final Optional<PartySnapshot> observed = _backend.observe(member);
				if (observed.isPresent())
				{
					return observed;
				}
			}
		}
		for (MemberRef member : before.realMembers())
		{
			if (!member.equals(target))
			{
				final Optional<PartySnapshot> observed = _backend.observe(member);
				if (observed.isPresent())
				{
					return observed;
				}
			}
		}
		return _backend.observe(actor);
	}

	private static boolean membershipPostcondition(OperationKind kind, MemberRef actor, MemberRef target, PartySnapshot observed, MembershipOutcome canonical)
	{
		return switch (kind)
		{
			case LEAVE -> (observed == null) || !observed.members().contains(actor);
			case EXPEL -> ((observed == null) && (canonical == MembershipOutcome.COMPLETED)) || ((observed != null) && !observed.members().contains(target));
			case TRANSFER_LEADER -> (observed != null) && observed.leader().equals(target);
			default -> false;
		};
	}

	private boolean commitMembership(PartyState before, PartySnapshot observed, PartyOperation prepared)
	{
		final List<StoredPartyState> previous = claims(before.groupId());
		final Set<Long> remainingProfiles = observed == null ? Set.of() : observed.members().stream().filter(member -> member.kind() == MemberKind.PHANTOM).map(MemberRef::profileId).collect(java.util.stream.Collectors.toSet());
		for (StoredPartyState claim : previous)
		{
			if (!remainingProfiles.contains(claim.profileId()))
			{
				moveToSolo(claim, "");
			}
		}
		if (observed == null)
		{
			removeGroup(before.groupId());
			return true;
		}
		final List<StoredPartyState> remaining = claims(before.groupId());
		if (remaining.isEmpty())
		{
			removeGroup(before.groupId());
			return true;
		}
		final long generation = observed.leader().equals(before.leader()) ? before.groupGeneration() : before.groupGeneration() + 1;
		final List<MemberRef> phantoms = observed.members().stream().filter(member -> member.kind() == MemberKind.PHANTOM).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		final List<MemberRef> reals = observed.members().stream().filter(member -> member.kind() == MemberKind.REAL).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(observed.members());
		final RoleMatchResult roles = _roles.match(before.objectiveMode(), before.requirements(), new ArrayList<>(snapshots.values()));
		final PartyOperation committed = prepared.withPhase(OperationPhase.COMMITTED, 0, "");
		final PartyState authority = state(before.groupId(), generation, before.membershipRevision() + 1, StateStatus.MEMBER, observed.leader(), phantoms, reals, before.objectiveMode(), before.objectiveRef(), before.requirements(), roles.assignments(), null, committed, progressionEvidence(snapshots), "");
		final String manifest = authority.canonicalManifestHash();
		for (MemberRef member : phantoms)
		{
			final StoredPartyState current = _claims.get(member.profileId());
			if (current == null)
			{
				continue;
			}
			final StateStatus status = member.equals(observed.leader()) ? StateStatus.LEADER : StateStatus.MEMBER;
			final String ownRole = roles.assignments().stream().filter(assignment -> assignment.member().equals(member)).map(RoleAssignment::roleKey).findFirst().orElse("");
			final PartyState next = new PartyState(before.groupId(), generation, authority.membershipRevision(), status, observed.leader(), ownRole, manifest, phantoms, reals, before.objectiveMode(), before.objectiveRef(), before.requirements(), roles.assignments(), null, committed, authority.progressionHash(), authority.topologyHash(), "");
			try
			{
				putClaim(save(member.profileId(), current.rowVersion(), next));
			}
			catch (RuntimeException e)
			{
				markInconsistent(current, "party.membership.commit_conflict");
				return false;
			}
		}
		_routes.cancel(before.groupId());
		ensureGroup(before.groupId());
		return true;
	}

	private MemberRef resolveMember(PhantomDomainRef target)
	{
		if (target == null)
		{
			return null;
		}
		try
		{
			if ("profile".equals(target.namespace()))
			{
				return _backend.currentMember(Long.parseLong(target.key())).orElse(null);
			}
			if ("character.object".equals(target.namespace()))
			{
				final int objectId = Integer.parseInt(target.key());
				final OptionalLong managed = _backend.managedProfileId(objectId);
				return managed.isPresent() ? MemberRef.phantom(managed.getAsLong(), objectId) : MemberRef.real(objectId);
			}
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		return null;
	}

	private boolean exactGoal(long profileId, long goalId, long goalRevision, String type, Integer targetObjectId)
	{
		final Optional<StoredGoal> stored = _goals.load(profileId);
		return stored.isPresent() && (stored.get().goal().status() == PhantomGoalStatus.ACTIVE) && (stored.get().goal().goalId() == goalId) && (stored.get().goal().revision() == goalRevision) && type.equals(stored.get().goal().goalType()) && ((targetObjectId == null) || goalTargets(stored.get().goal(), targetObjectId));
	}

	private static boolean goalTargets(PhantomGoal goal, int requesterObjectId)
	{
		final PhantomDomainRef target = goal.target();
		if (target == null)
		{
			return false;
		}
		return ("character.object".equals(target.namespace()) && Integer.toString(requesterObjectId).equals(target.key())) || ("profile".equals(target.namespace()) && goal.validSources().stream().anyMatch(source -> "character.object".equals(source.namespace()) && Integer.toString(requesterObjectId).equals(source.key())));
	}

	private void emitInvitationTerminal(TerminalEvent terminal)
	{
		final String outbound;
		final String inbound;
		switch (terminal.outcome())
		{
			case ACCEPTED:
				outbound = "party.invite.accepted.outbound";
				inbound = "party.invite.accepted.inbound";
				break;
			case REFUSED:
				outbound = "party.invite.refused.outbound";
				inbound = "party.invite.refused.inbound";
				break;
			case EXPIRED:
				outbound = "party.invite.expired.outbound";
				inbound = "party.invite.expired.inbound";
				break;
			default:
				return;
		}
		final PartyInvitation invitation = terminal.invitation();
		final String source = "invitation|" + invitation.identity().sequence() + '|' + invitation.identity().requesterObjectId() + '|' + invitation.identity().inviteeObjectId();
		if (terminal.managedRequester().isPresent())
		{
			emitSocial(terminal.managedRequester().getAsLong(), outbound, subject(terminal.managedInvitee(), invitation.identity().inviteeObjectId()), source, "outbound");
		}
		if (terminal.managedInvitee().isPresent())
		{
			emitSocial(terminal.managedInvitee().getAsLong(), inbound, subject(terminal.managedRequester(), invitation.identity().requesterObjectId()), source, "inbound");
		}
	}

	private void emitJoined(List<MemberRef> managedMembers, PartyOperation operation)
	{
		if ((operation == null) || (operation.kind() != OperationKind.JOIN) || (operation.member() == null))
		{
			return;
		}
		for (MemberRef owner : managedMembers)
		{
			if (owner.kind() != MemberKind.PHANTOM)
			{
				continue;
			}
			final MemberRef counterpart = owner.equals(operation.member()) ? operation.leader() : operation.member();
			if (!owner.equals(counterpart))
			{
				emitSocial(owner.profileId(), "party.member.joined", subject(counterpart), operation.operationId(), "membership");
			}
		}
	}

	private void emitMembership(PartyState before, MemberRef actor, MemberRef target, PartyOperation operation)
	{
		final String eventKey = switch (operation.kind())
		{
			case LEAVE -> "party.member.left";
			case EXPEL -> "party.member.expelled";
			case TRANSFER_LEADER -> "party.leader.transferred";
			default -> null;
		};
		if (eventKey == null)
		{
			return;
		}
		for (MemberRef owner : before.phantomMembers())
		{
			final MemberRef counterpart;
			if (operation.kind() == OperationKind.LEAVE)
			{
				counterpart = owner.equals(actor) ? leaveCounterpart(before, actor) : actor;
			}
			else
			{
				counterpart = owner.equals(target) ? actor : target;
			}
			if ((counterpart != null) && !owner.equals(counterpart))
			{
				emitSocial(owner.profileId(), eventKey, subject(counterpart), operation.operationId(), "membership");
			}
		}
	}

	private static MemberRef leaveCounterpart(PartyState before, MemberRef actor)
	{
		if (!before.leader().equals(actor))
		{
			return before.leader();
		}
		return java.util.stream.Stream.concat(before.phantomMembers().stream(), before.realMembers().stream()).filter(member -> !member.equals(actor)).findFirst().orElse(null);
	}

	private void emitSocial(long ownerProfileId, String eventKey, SubjectRef subject, String sourceIdentity, String perspective)
	{
		final String eventId = PhantomSocialModel.sha256("social.event|party|" + sourceIdentity + '|' + ownerProfileId + '|' + eventKey + '|' + subject.stableKey() + '|' + perspective);
		final String evidence = PhantomSocialModel.sha256("party.evidence|" + sourceIdentity);
		try
		{
			final PhantomSocialEventSink.Result result = _socialEvents.record(new SocialEvent(ownerProfileId, eventId, eventKey, subject, Math.max(0, _socialClock.getAsLong()), 1000, evidence));
			if (result.status() == PhantomSocialEventSink.Status.RECORDED)
			{
				_socialEventsRecorded.incrementAndGet();
			}
			else if ((result.status() != PhantomSocialEventSink.Status.IDEMPOTENT) && (result.status() != PhantomSocialEventSink.Status.DISABLED))
			{
				_socialEventFailures.incrementAndGet();
			}
		}
		catch (RuntimeException e)
		{
			_socialEventFailures.incrementAndGet();
		}
	}

	private static SubjectRef subject(OptionalLong managedProfileId, int characterObjectId)
	{
		return managedProfileId.isPresent() ? SubjectRef.phantom(managedProfileId.getAsLong()) : SubjectRef.character(characterObjectId);
	}

	private static SubjectRef subject(MemberRef member)
	{
		return member.kind() == MemberKind.PHANTOM ? SubjectRef.phantom(member.profileId()) : SubjectRef.character(member.characterObjectId());
	}

	private String groupForLeader(long leaderProfileId)
	{
		final StoredPartyState claim = _claims.get(leaderProfileId);
		return claim == null ? PhantomPartyModel.sha256("party.unclaimed|" + leaderProfileId) : claim.state().groupId();
	}

	private List<StoredPartyState> claims(String groupId)
	{
		synchronized (_indexLock)
		{
			final NavigableMap<Long, StoredPartyState> indexed = _claimsByGroup.get(groupId);
			return indexed == null ? List.of() : List.copyOf(indexed.values());
		}
	}

	private int claimCount(String groupId)
	{
		synchronized (_indexLock)
		{
			final NavigableMap<Long, StoredPartyState> indexed = _claimsByGroup.get(groupId);
			return indexed == null ? 0 : indexed.size();
		}
	}

	private void putClaim(StoredPartyState replacement)
	{
		final StoredPartyState previous = _claims.put(replacement.profileId(), replacement);
		synchronized (_indexLock)
		{
			if (previous != null)
			{
				final NavigableMap<Long, StoredPartyState> old = _claimsByGroup.get(previous.state().groupId());
				if (old != null)
				{
					old.remove(previous.profileId());
					if (old.isEmpty())
					{
						_claimsByGroup.remove(previous.state().groupId());
					}
				}
			}
			_claimsByGroup.computeIfAbsent(replacement.state().groupId(), ignored -> new TreeMap<>()).put(replacement.profileId(), replacement);
		}
		if (committedClaim(replacement) || !Set.of(StateStatus.SOLO, StateStatus.RETIRED, StateStatus.INCONSISTENT).contains(replacement.state().status()))
		{
			ensureGroup(replacement.state().groupId());
		}
	}

	private void ensureGroup(String groupId)
	{
		_groups.computeIfAbsent(groupId, GroupRuntime::new);
		scheduleGroup(groupId);
	}

	private void scheduleGroup(String groupId)
	{
		synchronized (_indexLock)
		{
			if (_dueGroupSet.add(groupId))
			{
				if (_dueGroups.size() >= MAX_DUE_GROUPS)
				{
					final String dropped = _dueGroups.removeFirst();
					_dueGroupSet.remove(dropped);
				}
				_dueGroups.addLast(groupId);
			}
		}
	}

	private GroupRuntime pollDueGroup()
	{
		synchronized (_indexLock)
		{
			final String groupId = _dueGroups.pollFirst();
			if (groupId == null)
			{
				return null;
			}
			_dueGroupSet.remove(groupId);
			return _groups.get(groupId);
		}
	}

	private void removeGroup(String groupId)
	{
		_groups.remove(groupId);
		_routes.cancel(groupId);
		synchronized (_indexLock)
		{
			_dueGroupSet.remove(groupId);
			_dueGroups.remove(groupId);
		}
	}

	private OperationClaim beginOperation()
	{
		synchronized (_lifecycleLock)
		{
			if (_state != State.RUNNING)
			{
				return null;
			}
			_operationClaims.incrementAndGet();
			return new OperationClaim();
		}
	}

	private OperationClaim beginTerminalOperation()
	{
		synchronized (_lifecycleLock)
		{
			if ((_state != State.RUNNING) && (_state != State.STOPPING))
			{
				return null;
			}
			_operationClaims.incrementAndGet();
			return new OperationClaim();
		}
	}

	private Map<MemberRef, MemberSnapshot> snapshots(List<MemberRef> roster)
	{
		final Map<MemberRef, MemberSnapshot> result = new LinkedHashMap<>();
		for (MemberRef member : roster)
		{
			_backend.memberSnapshot(member).ifPresent(snapshot -> result.put(member, snapshot));
		}
		return result;
	}

	private PartyState state(String groupId, long generation, long revision, StateStatus status, MemberRef leader, List<MemberRef> phantoms, List<MemberRef> reals, ObjectiveMode objective, PhantomDomainRef objectiveRef, List<RoleRequirement> requirements, List<RoleAssignment> assignments, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest route, PartyOperation operation, String progressionHash, String failure)
	{
		final PartyState draft = new PartyState(groupId, generation, revision, status, leader, "", ZERO_HASH, phantoms, reals, objective, objectiveRef, requirements, assignments, route, operation, progressionHash, _topologyHash.get(), failure);
		return new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), draft.route(), draft.operation(), draft.progressionHash(), draft.topologyHash(), draft.lastFailureKey());
	}

	private String progressionHash(MemberRef member)
	{
		return _backend.memberSnapshot(member).map(MemberSnapshot::progressionHash).orElse(ZERO_HASH);
	}

	private static String progressionEvidence(Map<MemberRef, MemberSnapshot> snapshots)
	{
		return PhantomPartyModel.sha256(snapshots.values().stream().sorted(Comparator.comparing(snapshot -> snapshot.ref().stableKey())).map(snapshot -> snapshot.ref().stableKey() + ':' + snapshot.progressionHash()).reduce("", (left, right) -> left + '|' + right));
	}

	private long deadline()
	{
		return Math.addExact(Math.max(1, _clock.getAsLong()), java.util.concurrent.TimeUnit.SECONDS.toNanos(30));
	}

	private List<StoredPartyState> readPage(long cursor)
	{
		_persistenceClaims.incrementAndGet();
		try
		{
			return _store.loadManagedAfter(cursor, PAGE_SIZE);
		}
		finally
		{
			_persistenceClaims.decrementAndGet();
		}
	}

	private StoredPartyState save(long profileId, long expectedRowVersion, PartyState state)
	{
		_persistenceClaims.incrementAndGet();
		try
		{
			return _store.save(profileId, expectedRowVersion, state);
		}
		finally
		{
			_persistenceClaims.decrementAndGet();
		}
	}

	private static boolean committedClaim(StoredPartyState claim)
	{
		return (claim != null) && Set.of(StateStatus.LEADER, StateStatus.MEMBER, StateStatus.RECOVERING).contains(claim.state().status());
	}

	public record Snapshot(State state, int partyClaims, int groups, int terminalEvents, int inboundInvites, int operationClaims, int persistenceClaims, int navigationClaims, int routeActions, int tacticalActions, int operationBudget, int lastPulseExamined, int maximumPulseExamined, long socialEventsRecorded, long socialEventFailures, PhantomPartyMetrics.Snapshot metrics)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new PhantomPartyMetrics().snapshot());
		}
	}

	private record ManagedInvitation(PartyInvitation invitation, long profileId)
	{
	}

	private record TerminalEvent(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, TerminalOutcome outcome, String reasonKey)
	{
		private TerminalEvent
		{
			Objects.requireNonNull(invitation);
			Objects.requireNonNull(managedRequester);
			Objects.requireNonNull(managedInvitee);
			Objects.requireNonNull(outcome);
			Objects.requireNonNull(reasonKey);
		}
	}

	private record GroupRuntime(String groupId)
	{
	}

	private final class OperationClaim implements AutoCloseable
	{
		private final AtomicBoolean _closed = new AtomicBoolean();

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_operationClaims.decrementAndGet();
			}
		}
	}
}
