/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.DeliveryRegistration;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
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

/**
 * Single-pulse party saga owner. It has no worker, timer or scheduled future.
 */
public final class PhantomPartyCoordinator implements PhantomSchedulerControlPort, PartyInvitationDelivery
{
	public static final String FORM_GOAL = "party.form";
	public static final String JOIN_GOAL = "party.join";
	public static final String LEAD_GOAL = "party.lead";
	public static final String MEMBER_GOAL = "party.member";
	public static final String TRAVEL_GOAL = "party.travel";
	public static final String LEAVE_GOAL = "party.leave";
	private static final String ZERO_HASH = "0".repeat(64);
	private static final int PAGE_SIZE = 256;
	private static final int MAX_INBOUND_INVITES = 4096;

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
		NOT_RUNNING,
		GOAL_MISMATCH,
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
	private final int _operationBudget;
	private final ArrayBlockingQueue<ManagedInvitation> _inbound = new ArrayBlockingQueue<>(MAX_INBOUND_INVITES);
	private final Map<Long, StoredPartyState> _claims = new ConcurrentHashMap<>();
	private final Map<String, GroupRuntime> _groups = new ConcurrentHashMap<>();
	private final Map<Long, ExternalActionLease> _tacticalActions = new ConcurrentHashMap<>();
	private final AtomicInteger _persistenceClaims = new AtomicInteger();
	private final PhantomPartyMetrics _metrics = new PhantomPartyMetrics();
	private volatile State _state = State.NEW;
	private volatile DeliveryRegistration _deliveryRegistration;

	public PhantomPartyCoordinator(PhantomPartyPersistencePort store, PhantomGoalStore goals, PhantomPartyBackend backend, PhantomPartyRoleCatalog roleCatalog, PhantomPartyRouteCoordinator routes, PhantomPartyTactics tactics, Supplier<String> topologyHash, LongSupplier clock, int operationBudget)
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
		if ((operationBudget < 1) || (operationBudget > 10000))
		{
			throw new IllegalArgumentException("Party operation budget must be between one and 10000.");
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
					_claims.put(stored.profileId(), recovered);
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
		if (_state != State.RUNNING)
		{
			return CommandOutcome.NOT_RUNNING;
		}
		if (!exactGoal(leaderProfileId, goalId, goalRevision, FORM_GOAL, null))
		{
			return CommandOutcome.GOAL_MISMATCH;
		}
		if (committedClaim(_claims.get(leaderProfileId)))
		{
			return CommandOutcome.CLAIM_EXISTS;
		}
		final MemberRef leader = _backend.currentMember(leaderProfileId).orElse(null);
		if (leader == null)
		{
			return CommandOutcome.TARGET_UNAVAILABLE;
		}
		final String groupId = PhantomPartyModel.stableGroupId(leaderProfileId, goalId, goalRevision);
		final PartyState provisional = state(groupId, 1, 0, StateStatus.FORMING, leader, List.of(leader), List.of(), objective, objectiveRef, requirements, List.of(), null, null, progressionHash(leader), "");
		final String manifest = provisional.canonicalManifestHash();
		final PartyOperation operation = new PartyOperation(PhantomPartyModel.stableOperationId(groupId, 1, 0, OperationKind.FORM, leader, null, goalId, goalRevision, manifest), OperationKind.FORM, OperationPhase.PREPARED, leader, null, goalId, goalRevision, manifest, 0, deadline(), "");
		final PartyState prepared = state(groupId, 1, 0, StateStatus.FORMING, leader, List.of(leader), List.of(), objective, objectiveRef, requirements, List.of(), null, operation, progressionHash(leader), "");
		try
		{
			final StoredPartyState stored = save(leaderProfileId, _claims.containsKey(leaderProfileId) ? _claims.get(leaderProfileId).rowVersion() : -1, prepared);
			_claims.put(leaderProfileId, stored);
			_groups.put(groupId, new GroupRuntime(groupId));
			return CommandOutcome.ACCEPTED;
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
			return CommandOutcome.PERSISTENCE_CONFLICT;
		}
	}

	public CommandOutcome invite(long leaderProfileId, MemberRef target, PartyDistributionType distribution)
	{
		if (_state != State.RUNNING)
		{
			return CommandOutcome.NOT_RUNNING;
		}
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
		final long goalId = base == null ? 1 : base.leaderGoalId();
		final long goalRevision = base == null ? 0 : base.leaderGoalRevision();
		final String operationId = PhantomPartyModel.stableOperationId(current.groupId(), current.groupGeneration(), current.membershipRevision(), OperationKind.JOIN, current.leader(), target, goalId, goalRevision, current.leaderManifestHash());
		final PartyOperation operation = new PartyOperation(operationId, OperationKind.JOIN, OperationPhase.PREPARED, current.leader(), target, goalId, goalRevision, current.leaderManifestHash(), 0, deadline(), "");
		final StoredPartyState prepared;
		try
		{
			prepared = save(leaderProfileId, stored.rowVersion(), current.withOperation(StateStatus.INVITED_OUTBOUND, operation, ""));
			_claims.put(leaderProfileId, prepared);
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
				_claims.put(target.profileId(), preparedMember);
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
		final PartyOperation pending = operation.withPhase(OperationPhase.CANONICAL_PENDING, result.identity().sequence(), "");
		try
		{
			final StoredPartyState updated = save(leaderProfileId, prepared.rowVersion(), prepared.state().withOperation(StateStatus.INVITED_OUTBOUND, pending, ""));
			_claims.put(leaderProfileId, updated);
			_metrics.inviteDelivered();
			return CommandOutcome.ACCEPTED;
		}
		catch (RuntimeException e)
		{
			PartyInvitationService.getInstance().cancel(result.identity());
			rollbackPreparedMember(target, previousMemberClaim, preparedMember, "invite.leader_claim_conflict");
			return CommandOutcome.PERSISTENCE_CONFLICT;
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

	public RouteOutcome requestRoute(long leaderProfileId, PhantomDomainRef destinationRef, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination)
	{
		if (_state != State.RUNNING)
		{
			return RouteOutcome.NOT_RUNNING;
		}
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

	@Override
	public OptionalLong managedIdentity(int characterObjectId)
	{
		return _state == State.RUNNING ? _backend.managedProfileId(characterObjectId) : OptionalLong.empty();
	}

	@Override
	public DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity)
	{
		if (_state != State.RUNNING)
		{
			return DeliveryOutcome.STOPPING;
		}
		return _inbound.offer(new ManagedInvitation(invitation, managedIdentity)) ? DeliveryOutcome.ACCEPTED : DeliveryOutcome.BACKPRESSURE;
	}

	@Override
	public void cancelled(PartyInvitation invitation, long managedIdentity, String reasonKey)
	{
		_inbound.removeIf(entry -> entry.profileId() == managedIdentity && entry.invitation().identity().equals(invitation.identity()));
		if (!"party.invite.accepted".equals(reasonKey))
		{
			abortManagedInvitation(invitation, managedIdentity, reasonKey);
		}
	}

	@Override
	public void onPulse()
	{
		if (_state != State.RUNNING)
		{
			return;
		}
		_metrics.pulse();
		releaseTacticalActions();
		int used = 0;
		while (used < _operationBudget)
		{
			final ManagedInvitation invitation = _inbound.poll();
			if (invitation == null)
			{
				break;
			}
			processManagedInvitation(invitation);
			_metrics.operation();
			used++;
		}
		for (GroupRuntime group : _groups.values().stream().sorted(Comparator.comparing(GroupRuntime::groupId)).toList())
		{
			if (used >= _operationBudget)
			{
				_metrics.budgetExhausted();
				break;
			}
			reconcile(group);
			_metrics.operation();
			used++;
		}
	}

	public void beginStop()
	{
		if ((_state == State.STOPPING) || (_state == State.STOPPED))
		{
			return;
		}
		_state = State.STOPPING;
		final DeliveryRegistration registration = _deliveryRegistration;
		if (registration != null)
		{
			registration.close();
			_deliveryRegistration = null;
		}
		for (ManagedInvitation invitation : new ArrayList<>(_inbound))
		{
			PartyInvitationService.getInstance().cancel(invitation.invitation().identity());
		}
		_inbound.clear();
		_routes.beginStop();
		_tacticalActions.values().forEach(ExternalActionLease::close);
		_tacticalActions.clear();
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
		if ((_persistenceClaims.get() != 0) || !_inbound.isEmpty() || !_tacticalActions.isEmpty() || (route.navigationClaims() != 0) || (route.movementClaims() != 0))
		{
			return false;
		}
		_state = State.STOPPED;
		return true;
	}

	public Snapshot snapshot()
	{
		final PhantomPartyRouteCoordinator.Snapshot route = _routes.snapshot();
		return new Snapshot(_state, _claims.size(), _groups.size(), _inbound.size(), _persistenceClaims.get(), route.navigationClaims(), route.movementClaims(), _tacticalActions.size(), _operationBudget, _metrics.snapshot());
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
		final boolean recoveryConsent = recoveryClaimMatches(managed.profileId(), invitation.requesterObjectId());
		final boolean explicitConsent = (storedGoal != null) && JOIN_GOAL.equals(storedGoal.goal().goalType()) && goalTargets(storedGoal.goal(), invitation.requesterObjectId());
		if (!recoveryConsent && !explicitConsent)
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
		final Optional<PartySnapshot> observed = _backend.observe(invitee);
		if (observed.isEmpty())
		{
			return;
		}
		final MemberRef leader = observed.get().leader();
		if (leader.kind() == MemberKind.REAL)
		{
			commitRealLedMember(managed.profileId(), storedGoal, observed.get(), invitation.identity());
		}
		else
		{
			_groups.computeIfAbsent(groupForLeader(leader.profileId()), GroupRuntime::new);
		}
	}

	private void reconcile(GroupRuntime runtime)
	{
		final List<StoredPartyState> groupClaims = claims(runtime.groupId());
		if (groupClaims.isEmpty())
		{
			_groups.remove(runtime.groupId());
			return;
		}
		MemberRef leader = groupClaims.getFirst().state().leader();
		if (leader.kind() != MemberKind.PHANTOM)
		{
			return;
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
				return;
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
			return;
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
			return;
		}
		commitObserved(groupClaims, party);
		advanceRoute(runtime.groupId(), party);
		dispatchTactics(party);
	}

	private void advanceRoute(String groupId, PartySnapshot party)
	{
		final List<StoredPartyState> claims = claims(groupId);
		if (claims.isEmpty())
		{
			return;
		}
		RouteManifest route = claims.getFirst().state().route();
		if (route == null)
		{
			route = _routes.poll(groupId).orElse(null);
			if (route == null)
			{
				return;
			}
			persistRoute(groupId, route);
		}
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(party.members());
		final RouteManifest advanced = _routes.advance(groupId, route, party.leader(), party.members(), snapshots, Math.min(_operationBudget, party.members().size()), deadline(), () -> _state != State.RUNNING);
		if (!advanced.equals(route))
		{
			persistRoute(groupId, advanced);
		}
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
				_claims.put(claim.profileId(), save(claim.profileId(), claim.rowVersion(), next));
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
				_claims.put(member.profileId(), saved);
				transitionGoal(member.profileId(), member.equals(party.leader()) ? LEAD_GOAL : MEMBER_GOAL, member.equals(party.leader()) || (current == null) ? authority.operation() : current.state().operation());
			}
			catch (RuntimeException e)
			{
				_metrics.conflict();
				return;
			}
		}
		_metrics.commit();
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
			_claims.put(profileId, save(profileId, current == null ? -1 : current.rowVersion(), committed));
			transitionGoal(profileId, MEMBER_GOAL, operation);
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
		}
	}

	private void dispatchTactics(PartySnapshot party)
	{
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(party.members());
		final List<TacticalDirective> directives = _tactics.plan(party.leader(), party.members(), snapshots);
		final Set<Long> occupied = Set.copyOf(_tacticalActions.keySet());
		for (TacticalDirective directive : directives)
		{
			if (occupied.contains(directive.actor().profileId()) || _tacticalActions.containsKey(directive.actor().profileId()))
			{
				continue;
			}
			final String key = "party.tactic." + directive.kind().name().toLowerCase(java.util.Locale.ROOT) + '.' + directive.actor().profileId();
			_tactics.dispatch(directive, key, deadline(), () -> _state != State.RUNNING).ifPresent(lease -> _tacticalActions.put(directive.actor().profileId(), lease));
		}
	}

	private void releaseTacticalActions()
	{
		for (Map.Entry<Long, ExternalActionLease> entry : new ArrayList<>(_tacticalActions.entrySet()))
		{
			final ExternalActionLease lease = entry.getValue();
			final org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot actor = lease.actorSnapshot();
			if ((actor == null) || (!actor.attacking() && !actor.casting()))
			{
				lease.complete();
				_tacticalActions.remove(entry.getKey(), lease);
			}
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
				_groups.put(entry.getKey(), new GroupRuntime(entry.getKey()));
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
				_claims.put(claim.profileId(), save(claim.profileId(), claim.rowVersion(), next));
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

	private void abortManagedInvitation(PartyInvitation invitation, long managedIdentity, String reasonKey)
	{
		final OptionalLong leaderProfileId = _backend.managedProfileId(invitation.requesterObjectId());
		if (leaderProfileId.isPresent())
		{
			final StoredPartyState leader = _claims.get(leaderProfileId.getAsLong());
			if ((leader != null) && sameInvitation(leader.state().operation(), invitation.identity()))
			{
				abort(leader, reasonKey);
			}
		}
		final StoredPartyState member = _claims.get(managedIdentity);
		if ((member != null) && (member.state().status() == StateStatus.INVITED_INBOUND) && sameInvitation(member.state().operation(), invitation.identity()))
		{
			moveToSolo(member, reasonKey);
		}
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
			_claims.put(member.profileId(), save(member.profileId(), current.rowVersion(), previous.state()));
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
			_claims.put(stored.profileId(), save(stored.profileId(), stored.rowVersion(), solo));
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
			_claims.put(stored.profileId(), save(stored.profileId(), stored.rowVersion(), aborted));
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
			_claims.put(stored.profileId(), save(stored.profileId(), stored.rowVersion(), stored.state().withOperation(StateStatus.INCONSISTENT, stored.state().operation(), failure)));
		}
		catch (RuntimeException e)
		{
			_metrics.failure();
		}
	}

	private boolean exactGoal(long profileId, long goalId, long goalRevision, String type, Integer targetObjectId)
	{
		final Optional<StoredGoal> stored = _goals.load(profileId);
		return stored.isPresent() && (stored.get().goal().goalId() == goalId) && (stored.get().goal().revision() == goalRevision) && type.equals(stored.get().goal().goalType()) && ((targetObjectId == null) || goalTargets(stored.get().goal(), targetObjectId));
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

	private boolean recoveryClaimMatches(long profileId, int requesterObjectId)
	{
		final StoredPartyState claim = _claims.get(profileId);
		return (claim != null) && (claim.state().status() == StateStatus.RECOVERING) && (claim.state().leader().kind() == MemberKind.PHANTOM) && (claim.state().leader().characterObjectId() == requesterObjectId);
	}

	private String groupForLeader(long leaderProfileId)
	{
		final StoredPartyState claim = _claims.get(leaderProfileId);
		return claim == null ? PhantomPartyModel.sha256("party.unclaimed|" + leaderProfileId) : claim.state().groupId();
	}

	private List<StoredPartyState> claims(String groupId)
	{
		return _claims.values().stream().filter(claim -> claim.state().groupId().equals(groupId)).sorted(Comparator.comparingLong(StoredPartyState::profileId)).toList();
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

	public record Snapshot(State state, int partyClaims, int groups, int inboundInvites, int persistenceClaims, int navigationClaims, int routeActions, int tacticalActions, int operationBudget, PhantomPartyMetrics.Snapshot metrics)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, 0, 0, 0, 0, 0, 0, 0, 0, new PhantomPartyMetrics().snapshot());
		}
	}

	private record ManagedInvitation(PartyInvitation invitation, long profileId)
	{
	}

	private record GroupRuntime(String groupId)
	{
	}
}
