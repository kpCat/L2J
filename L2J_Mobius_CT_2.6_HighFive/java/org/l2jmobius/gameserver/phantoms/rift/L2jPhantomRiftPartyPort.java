/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.CommandOutcome;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ContentBindingRequest;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ContentBindingResult;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.BindingStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteObservation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyBinding;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyCommand;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteObservation;

/** Thin adapter to the accepted Goal 017 Party coordinator. */
public final class L2jPhantomRiftPartyPort implements PartyPort
{
	private static final Set<CommandOutcome> ACCEPTED = Set.of(CommandOutcome.ACCEPTED, CommandOutcome.IDEMPOTENT);
	private final PhantomPartyCoordinator _coordinator;

	public L2jPhantomRiftPartyPort(PhantomPartyCoordinator coordinator)
	{
		_coordinator = Objects.requireNonNull(coordinator);
	}

	@Override
	public PartyCommand ensureFormation(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements)
	{
		final CommandOutcome outcome = _coordinator.formForGoal(leaderProfileId, goalId, goalRevision, PhantomRiftService.GOAL_TYPE, ObjectiveMode.AREA_PVE, objective, requirements);
		return new PartyCommand(ACCEPTED.contains(outcome), "rift.party." + outcome.name().toLowerCase());
	}

	@Override
	public PartyBinding bind(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements, PhantomRiftModel.CanonicalRoster roster)
	{
		return binding(_coordinator.bindContentGoal(new ContentBindingRequest(leaderProfileId, goalId, goalRevision, PhantomRiftService.GOAL_TYPE, ObjectiveMode.AREA_PVE, objective, requirements, roster.leader(), roster.members(), roster.distribution(), roster.evidenceHash())));
	}

	@Override
	public PartyBinding observeBinding(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements, PhantomRiftModel.CanonicalRoster roster)
	{
		return binding(_coordinator.observeContentBinding(new ContentBindingRequest(leaderProfileId, goalId, goalRevision, PhantomRiftService.GOAL_TYPE, ObjectiveMode.AREA_PVE, objective, requirements, roster.leader(), roster.members(), roster.distribution(), roster.evidenceHash())));
	}

	private static PartyBinding binding(ContentBindingResult value)
	{
		final BindingStatus status = switch (value.stability())
		{
			case STABLE -> BindingStatus.STABLE;
			case PENDING -> BindingStatus.PENDING;
			case CONFLICT -> BindingStatus.CONFLICT;
		};
		return new PartyBinding(status, value.groupId(), value.groupGeneration(), value.membershipRevision(), value.leader(), value.rosterEvidenceHash(), value.manifestHash(), value.reasonKey());
	}

	@Override
	public boolean requiresExactBinding()
	{
		return true;
	}
	@Override
	public boolean candidateClaimAvailable(MemberRef candidate)
	{
		return candidate.kind() != org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind.PHANTOM || _coordinator.claim(candidate.profileId()).map(value -> value.state().status() == StateStatus.SOLO).orElse(true);
	}

	@Override
	public InviteObservation invite(long leaderProfileId, MemberRef candidate, PartyDistributionType distribution)
	{
		final CommandOutcome outcome = _coordinator.invite(leaderProfileId, candidate, distribution);
		if (!ACCEPTED.contains(outcome))
		{
			return new InviteObservation(InviteStatus.REJECTED, 0, 0, 0, 0, "rift.invite." + outcome.name().toLowerCase());
		}
		return operation(leaderProfileId, candidate, 0);
	}

	@Override
	public InviteObservation observeInvite(long leaderProfileId, MemberRef candidate, long expectedSequence)
	{
		return operation(leaderProfileId, candidate, expectedSequence);
	}

	private InviteObservation operation(long leaderProfileId, MemberRef candidate, long expectedSequence)
	{
		final PartyOperation operation = _coordinator.claim(leaderProfileId).map(value -> value.state().operation()).orElse(null);
		if ((operation == null) || (operation.kind() != OperationKind.JOIN) || !candidate.equals(operation.member()))
		{
			return new InviteObservation(InviteStatus.NONE, 0, 0, 0, 0, "rift.invite.not_observed");
		}
		final long sequence = operation.invitationSequence();
		if ((expectedSequence > 0) && (sequence > 0) && (expectedSequence != sequence))
		{
			return new InviteObservation(InviteStatus.STALE, sequence, operation.leader().characterObjectId(), candidate.characterObjectId(), 1, "rift.invite.sequence_changed");
		}
		final var invitee = World.getInstance().getPlayer(candidate.characterObjectId());
		final InvitationSnapshot invitation = invitee == null ? null : PartyInvitationService.getInstance().observe(invitee).orElse(null);
		if (invitation != null)
		{
			if ((sequence > 0) && !invitation.identity().equals(new org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity(sequence, operation.leader().characterObjectId(), candidate.characterObjectId())))
			{
				return new InviteObservation(InviteStatus.STALE, invitation.identity().sequence(), invitation.identity().requesterObjectId(), invitation.identity().inviteeObjectId(), invitation.expiresAtGameTick(), "rift.invite.identity_changed");
			}
			return new InviteObservation(InviteStatus.PENDING, invitation.identity().sequence(), invitation.identity().requesterObjectId(), invitation.identity().inviteeObjectId(), invitation.expiresAtGameTick(), "rift.invite.pending");
		}
		if (operation.phase() == OperationPhase.ABORTED)
		{
			return terminal(operation);
		}
		if ((operation.phase() == OperationPhase.COMMITTED) || (operation.phase() == OperationPhase.CANONICAL_OBSERVED))
		{
			return new InviteObservation(InviteStatus.ACCEPTED, sequence, operation.leader().characterObjectId(), candidate.characterObjectId(), 1, "rift.invite.accepted");
		}
		return new InviteObservation(InviteStatus.NONE, sequence, operation.leader().characterObjectId(), candidate.characterObjectId(), 1, "rift.invite.not_observed");
	}

	private static InviteObservation terminal(PartyOperation operation)
	{
		final String reason = operation.failureKey().isEmpty() ? "party.invite.revalidation_failed" : operation.failureKey();
		final InviteStatus status = switch (reason)
		{
			case "party.invite.refused" -> InviteStatus.REFUSED;
			case "party.invite.expired" -> InviteStatus.EXPIRED;
			case "party.invite.cancelled", "party.invite.delivery_closed" -> InviteStatus.CANCELLED;
			default -> InviteStatus.REJECTED;
		};
		return new InviteObservation(status, operation.invitationSequence(), operation.leader().characterObjectId(), operation.member().characterObjectId(), 1, reason);
	}

	@Override
	public RouteObservation requestRoute(long leaderProfileId, PhantomDomainRef destination, PhantomNavigationPoint point)
	{
		final PhantomPartyCoordinator.RouteOutcome outcome = _coordinator.requestRoute(leaderProfileId, destination, point);
		if ((outcome != PhantomPartyCoordinator.RouteOutcome.ACCEPTED) && (outcome != PhantomPartyCoordinator.RouteOutcome.PENDING))
		{
			return new RouteObservation(PhantomRiftService.RouteStatus.REJECTED, "0".repeat(64), "rift.route." + outcome.name().toLowerCase());
		}
		return route(leaderProfileId);
	}

	@Override
	public RouteObservation observeRoute(long leaderProfileId, String expectedRouteHash)
	{
		final RouteObservation route = route(leaderProfileId);
		if (!"0".repeat(64).equals(expectedRouteHash) && !"0".repeat(64).equals(route.routeHash()) && !expectedRouteHash.equals(route.routeHash()))
		{
			return new RouteObservation(PhantomRiftService.RouteStatus.FAILED, route.routeHash(), "rift.route.identity_changed");
		}
		return route;
	}

	private RouteObservation route(long leaderProfileId)
	{
		final RouteManifest route = _coordinator.claim(leaderProfileId).map(value -> value.state().route()).orElse(null);
		if (route == null)
		{
			return new RouteObservation(PhantomRiftService.RouteStatus.PENDING, "0".repeat(64), "rift.route.pending");
		}
		return switch (route.status())
		{
			case ARRIVED -> new RouteObservation(PhantomRiftService.RouteStatus.ARRIVED, route.routeId(), "rift.route.arrived");
			case FAILED -> new RouteObservation(PhantomRiftService.RouteStatus.FAILED, route.routeId(), "rift.route.failed");
			case PLANNING, MOVING, REGROUPING -> new RouteObservation(PhantomRiftService.RouteStatus.PENDING, route.routeId(), "rift.route.pending");
		};
	}
}