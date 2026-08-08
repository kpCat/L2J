/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.CommandOutcome;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteObservation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyCommand;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.PartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.RouteObservation;

/**
 * Thin adapter to the accepted Goal 017 Party coordinator.
 */
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
	public InviteObservation invite(long leaderProfileId, MemberRef candidate, PartyDistributionType distribution)
	{
		final CommandOutcome outcome = _coordinator.invite(leaderProfileId, candidate, distribution);
		if (!ACCEPTED.contains(outcome))
		{
			return new InviteObservation(InviteStatus.REJECTED, 0, "rift.invite." + outcome.name().toLowerCase());
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
			return new InviteObservation(InviteStatus.NONE, 0, "rift.invite.not_observed");
		}
		if ((expectedSequence > 0) && (operation.invitationSequence() > 0) && (expectedSequence != operation.invitationSequence()))
		{
			return new InviteObservation(InviteStatus.NONE, operation.invitationSequence(), "rift.invite.sequence_changed");
		}
		if (operation.phase() == OperationPhase.ABORTED)
		{
			final String failure = operation.failureKey().isEmpty() ? "rift.invite.refused" : operation.failureKey();
			return new InviteObservation(failure.contains("timeout") ? InviteStatus.TIMED_OUT : InviteStatus.REFUSED, operation.invitationSequence(), failure);
		}
		if (operation.phase() == OperationPhase.COMMITTED)
		{
			return new InviteObservation(InviteStatus.ACCEPTED, operation.invitationSequence(), "rift.invite.accepted");
		}
		return new InviteObservation(InviteStatus.PENDING, operation.invitationSequence(), "rift.invite.pending");
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
