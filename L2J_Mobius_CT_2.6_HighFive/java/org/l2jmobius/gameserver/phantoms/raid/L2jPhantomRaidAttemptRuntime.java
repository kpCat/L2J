/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator.AttemptStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator.RouteAttempt;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.PartySlot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.CandleEvidence;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.CandleInteraction;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.TargetEvidence;

/**
 * Shared-service implementation of raid actions. All maps are bounded by the
 * AttemptService live-attempt cap and are advanced only by the caller.
 */
public final class L2jPhantomRaidAttemptRuntime implements PhantomRaidAttemptRuntime
{
	private static final int MAXIMUM_MECHANIC_ATTACKERS = 8;
	private static final long COMBAT_TIMEOUT_MILLIS = 60_000;
	private static final Set<String> SUPPORT_CAPABILITIES = Set.of("combat.heal", "combat.resurrection", "combat.recharge");

	private final PhantomCombatService _combat;
	private final PhantomPartyTactics _tactics;
	private final PhantomPartyRouteCoordinator _routes;
	private final Supplier<String> _topologyHash;
	private final LongSupplier _logicalClock;
	private final Map<String, RuntimeAttempt> _attempts = new LinkedHashMap<>();
	private boolean _stopping;

	public L2jPhantomRaidAttemptRuntime(PhantomCombatService combat, PhantomPartyTactics tactics, PhantomPartyRouteCoordinator routes, Supplier<String> topologyHash, LongSupplier logicalClock)
	{
		_combat = Objects.requireNonNull(combat);
		_tactics = Objects.requireNonNull(tactics);
		_routes = Objects.requireNonNull(routes);
		_topologyHash = Objects.requireNonNull(topologyHash);
		_logicalClock = Objects.requireNonNull(logicalClock);
	}

	@Override
	public synchronized MechanicAdvance advanceMechanic(MechanicContext context, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		if (_stopping || context.token().isCancelled())
		{
			return new MechanicAdvance(RuntimeStatus.INVALID, null, "raid.mechanic.cancelled");
		}
		final RuntimeAttempt state = state(context.attemptAuthorityHash());
		final RuntimeStatus providers = reserveProviders(state, context.profile(), force);
		if (providers != RuntimeStatus.INTERMEDIATE)
		{
			return new MechanicAdvance(providers, null, "raid.mechanic.required_provider_unavailable");
		}
		final RuntimeStatus support = advanceSupport(state, context.profile(), force, context.logicalDeadlineNanos(), context.token());
		if (support != RuntimeStatus.INTERMEDIATE)
		{
			return new MechanicAdvance(support, null, "raid.mechanic.party_support_evidence_invalid");
		}
		final List<Integer> attackers = force.members().stream().flatMap(member -> member.attackerObjectIds().stream()).filter(id -> id > 0).distinct().sorted().limit(MAXIMUM_MECHANIC_ATTACKERS).toList();
		final boolean mechanicBusy = pollMechanicSessions(state, context.token());
		if (!attackers.isEmpty() || mechanicBusy)
		{
			final RuntimeStatus clear = advanceMechanicCombat(state, context, force, attackers);
			return new MechanicAdvance(clear, null, clear == RuntimeStatus.INTERMEDIATE ? "raid.mechanic.clearing_attackers" : "raid.mechanic.no_controllable_offense");
		}

		final Optional<TargetEvidence> revealed = context.registration().adapter().revealedTarget(state._instanceId == 0 ? force.members().stream().filter(member -> member.ref().equals(force.actor())).mapToInt(MemberSnapshot::instanceId).findFirst().orElse(0) : state._instanceId);
		if (revealed.isPresent())
		{
			final TargetEvidence target = revealed.orElseThrow();
			return new MechanicAdvance(RuntimeStatus.TARGET_REVEALED, new PhantomRaidTargetEvidence(context.profile().contentKind(), context.profile().npcKind(), target.objectId(), target.npcId(), target.instanceId(), false), "raid.mechanic.target_revealed");
		}
		if (state._instanceId == 0)
		{
			state._instanceId = force.members().stream().filter(member -> member.ref().equals(force.actor())).mapToInt(MemberSnapshot::instanceId).findFirst().orElse(0);
		}
		if (state._instanceId <= 0)
		{
			return new MechanicAdvance(RuntimeStatus.INVALID, null, "raid.mechanic.instance_missing");
		}
		final MemberSnapshot scout = scout(state, force);
		if (scout == null)
		{
			return new MechanicAdvance(force.members().stream().filter(member -> member.ref().kind() == MemberKind.PHANTOM).allMatch(MemberSnapshot::dead) ? RuntimeStatus.WIPED : RuntimeStatus.NO_CONTROLLABLE_OFFENSE, null, "raid.mechanic.scout_unavailable");
		}
		final CandleEvidence candle = context.registration().adapter().candles(state._instanceId).stream().filter(value -> !value.used()).sorted(Comparator.comparingInt(CandleEvidence::objectId)).findFirst().orElse(null);
		if (candle == null)
		{
			return new MechanicAdvance(RuntimeStatus.INVALID, null, "raid.mechanic.unused_candle_missing");
		}
		final RuntimeStatus routed = routeScout(state, context, force, scout, candle);
		if (routed != RuntimeStatus.COMPLETE)
		{
			return new MechanicAdvance(routed, null, routed == RuntimeStatus.INTERMEDIATE ? "raid.mechanic.scout_routing" : "raid.mechanic.scout_route_failed");
		}
		final CandleInteraction interaction = context.registration().adapter().interactCandle(state._instanceId, scout.ref().characterObjectId(), candle.objectId());
		if ((interaction == CandleInteraction.INTERACTED) || (interaction == CandleInteraction.ALREADY_USED))
		{
			resetMechanicRoute(state);
			return new MechanicAdvance(RuntimeStatus.INTERMEDIATE, null, "raid.mechanic.candle_interacted");
		}
		if (interaction == CandleInteraction.OUT_OF_RANGE)
		{
			resetMechanicRoute(state);
			state._interactionRetries++;
			return new MechanicAdvance(state._interactionRetries <= 2 ? RuntimeStatus.INTERMEDIATE : RuntimeStatus.INVALID, null, "raid.mechanic.candle_out_of_range");
		}
		return new MechanicAdvance(RuntimeStatus.INVALID, null, "raid.mechanic.candle_invalid");
	}

	@Override
	public synchronized EngagementAdvance advanceEngagement(EngagementContext context, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		if (_stopping || context.token().isCancelled())
		{
			return new EngagementAdvance(RuntimeStatus.INVALID, false, false, "raid.engagement.cancelled");
		}
		final RuntimeAttempt state = state(context.attemptAuthorityHash());
		state._targetObjectId = context.target().objectId();
		state._targetNpcId = context.target().npcId();
		state._targetInstanceId = context.target().instanceId();
		final RuntimeStatus providers = reserveProviders(state, context.profile(), force);
		if (providers != RuntimeStatus.INTERMEDIATE)
		{
			return new EngagementAdvance(providers, state._actualDeathObserved, nativeLootComplete(state), "raid.engagement.required_provider_unavailable");
		}
		final RuntimeStatus support = advanceSupport(state, context.profile(), force, context.logicalDeadlineNanos(), context.token());
		if (support != RuntimeStatus.INTERMEDIATE)
		{
			return new EngagementAdvance(support, state._actualDeathObserved, nativeLootComplete(state), "raid.engagement.party_support_evidence_invalid");
		}
		boolean targetLost = false;
		for (RaidClaim claim : new ArrayList<>(state._raidClaims.values()))
		{
			if (!_combat.matchesRaidSession(claim.profileId(), claim.generation(), context.target().objectId(), context.target().npcId(), context.target().instanceId(), context.attemptAuthorityHash()))
			{
				state._raidClaims.remove(claim.profileId());
				targetLost = true;
				continue;
			}
			final Optional<PhantomCombatSessionSnapshot> terminal = _combat.consumeTerminal(claim.profileId());
			if (terminal.isEmpty())
			{
				continue;
			}
			state._raidClaims.remove(claim.profileId());
			final PhantomCombatResult result = terminal.orElseThrow().result();
			if (result.victory())
			{
				state._actualDeathObserved = true;
			}
			else if (result == PhantomCombatResult.TARGET_LOST)
			{
				targetLost = true;
			}
			if (claim.collector() && result.victory())
			{
				state._collectorTerminal = true;
			}
			else if (claim.collector())
			{
				state._collectorProfileId = 0;
				state._collectorTerminal = false;
			}
		}
		if (state._actualDeathObserved)
		{
			return new EngagementAdvance(RuntimeStatus.INTERMEDIATE, true, nativeLootComplete(state), nativeLootComplete(state) ? "raid.engagement.native_loot_complete" : "raid.engagement.native_loot_pending");
		}
		if (targetLost)
		{
			return new EngagementAdvance(RuntimeStatus.TARGET_LOST, false, false, "raid.engagement.target_lost");
		}

		final List<MemberSnapshot> offense = offenseMembers(state, force);
		if (offense.isEmpty())
		{
			final boolean wiped = force.members().stream().filter(member -> member.ref().kind() == MemberKind.PHANTOM).allMatch(MemberSnapshot::dead);
			return new EngagementAdvance(wiped ? RuntimeStatus.WIPED : RuntimeStatus.NO_CONTROLLABLE_OFFENSE, false, false, wiped ? "raid.engagement.wiped" : "raid.engagement.no_controllable_offense");
		}
		boolean capacityLimited = false;
		for (MemberSnapshot member : offense)
		{
			if (state._raidClaims.containsKey(member.ref().profileId()))
			{
				continue;
			}
			final PhantomCombatMode mode = supportedMode(member);
			if (mode == null)
			{
				continue;
			}
			final boolean collector = state._collectorProfileId == 0;
			final PhantomRaidCombatRequest request = new PhantomRaidCombatRequest(member.ref().profileId(), context.target().objectId(), context.target().npcId(), context.target().instanceId(), context.profile().contentKind(), context.profile().npcKind(), context.attemptAuthorityHash(), mode, true, collector, context.maximumActorLevel(), COMBAT_TIMEOUT_MILLIS, context.token());
			final StartResult started = _combat.startRaidSession(request);
			if (started.accepted() && (started.session() != null))
			{
				if (collector)
				{
					state._collectorProfileId = member.ref().profileId();
				}
				state._raidClaims.put(member.ref().profileId(), new RaidClaim(member.ref().profileId(), started.session().generation(), collector));
			}
			else if (started.status() == StartStatus.REJECTED_CAPACITY)
			{
				capacityLimited = true;
			}
		}
		if (state._raidClaims.isEmpty())
		{
			return new EngagementAdvance(capacityLimited ? RuntimeStatus.INTERMEDIATE : RuntimeStatus.NO_CONTROLLABLE_OFFENSE, false, false, capacityLimited ? "raid.engagement.combat_capacity" : "raid.engagement.no_supported_session");
		}
		return new EngagementAdvance(RuntimeStatus.INTERMEDIATE, false, false, "raid.engagement.fighting");
	}

	@Override
	public synchronized RetreatAdvance advanceRetreat(RetreatContext context, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		final RuntimeAttempt state = state(context.attemptAuthorityHash());
		cancelActions(state);
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(force);
		boolean complete = true;
		for (PartySnapshot party : force.parties())
		{
			final List<MemberRef> controlled = party.members().stream().filter(member -> member.kind() == MemberKind.PHANTOM).filter(member ->
			{
				final MemberSnapshot snapshot = snapshots.get(member);
				return (snapshot != null) && !snapshot.dead();
			}).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
			if (controlled.isEmpty())
			{
				continue;
			}
			final PhantomNavigationPoint destination;
			if (context.registration() != null)
			{
				destination = context.registration().adapter().safeRetreatPoint(context.instanceId()).orElse(null);
			}
			else
			{
				destination = context.ready().slots().stream().filter(slot -> slot.partyLeader().equals(party.leader())).map(PartySlot::point).findFirst().orElse(null);
			}
			if (destination == null)
			{
				return new RetreatAdvance(RuntimeStatus.INVALID, "raid.retreat.destination_missing");
			}
			final MemberRef routeLeader = controlled.getFirst();
			final MemberSnapshot leaderSnapshot = snapshots.get(routeLeader);
			if ((leaderSnapshot == null) || (leaderSnapshot.instanceId() != destination.instanceId()))
			{
				return new RetreatAdvance(RuntimeStatus.INVALID, "raid.retreat.instance_mismatch");
			}
			final String groupId = PhantomPartyModel.sha256("raid.attempt.retreat|" + context.attemptAuthorityHash() + '|' + party.leader().stableKey());
			final RouteProgress progress = state._retreatRoutes.computeIfAbsent(groupId, ignored -> new RouteProgress());
			final RuntimeStatus route = advanceRoute(groupId, progress, routeLeader, controlled, snapshots, destination, new PhantomDomainRef("raid.retreat", context.profile().contentId()), context.logicalDeadlineNanos(), context.token());
			if (route == RuntimeStatus.INVALID)
			{
				return new RetreatAdvance(RuntimeStatus.INVALID, "raid.retreat.route_failed");
			}
			complete &= route == RuntimeStatus.COMPLETE;
		}
		return new RetreatAdvance(complete ? RuntimeStatus.COMPLETE : RuntimeStatus.INTERMEDIATE, complete ? "raid.retreat.complete" : "raid.retreat.routing");
	}

	@Override
	public synchronized void cancel(String attemptAuthorityHash)
	{
		final RuntimeAttempt state = _attempts.remove(attemptAuthorityHash);
		if (state != null)
		{
			cancelActions(state);
			cancelRoutes(state);
		}
	}

	@Override
	public synchronized void complete(String attemptAuthorityHash)
	{
		cancel(attemptAuthorityHash);
	}

	@Override
	public synchronized void beginStop()
	{
		if (_stopping)
		{
			return;
		}
		_stopping = true;
		for (String authority : List.copyOf(_attempts.keySet()))
		{
			cancel(authority);
		}
	}

	private RuntimeAttempt state(String authority)
	{
		RuntimeAttempt state = _attempts.get(authority);
		if (state == null)
		{
			if (_attempts.size() >= PhantomRaidAttemptService.MAXIMUM_LIVE_ATTEMPTS)
			{
				throw new IllegalStateException("Raid attempt runtime capacity exceeded.");
			}
			state = new RuntimeAttempt();
			state._authorityHash = authority;
			_attempts.put(authority, state);
		}
		return state;
	}

	private RuntimeStatus reserveProviders(RuntimeAttempt state, PhantomRaidEncounterProfile profile, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		for (var requirement : profile.requiredCapabilities())
		{
			final long providers = force.members().stream().filter(member -> !member.dead()).filter(member -> providesCapability(member, requirement.capabilityKey(), requirement.minimumRank())).count();
			if (providers < requirement.minimumCount())
			{
				state._providers.clear();
				return RuntimeStatus.PROVIDER_UNAVAILABLE;
			}
		}
		final Map<String, Set<Long>> selected = new LinkedHashMap<>();
		for (var requirement : profile.requiredCapabilities().stream().filter(requirement -> SUPPORT_CAPABILITIES.contains(requirement.capabilityKey())).toList())
		{
			final List<MemberSnapshot> candidates = force.members().stream().filter(member -> (member.ref().kind() == MemberKind.PHANTOM) && !member.dead()).filter(member -> usableCapability(member, requirement.capabilityKey(), requirement.minimumRank()).isPresent()).sorted(Comparator.comparing(member -> member.ref().stableKey())).toList();
			selected.put(requirement.capabilityKey(), candidates.stream().limit(requirement.minimumCount()).map(member -> member.ref().profileId()).collect(Collectors.toCollection(LinkedHashSet::new)));
		}
		state._providers.clear();
		state._providers.putAll(selected);
		return RuntimeStatus.INTERMEDIATE;
	}

	private RuntimeStatus advanceSupport(RuntimeAttempt state, PhantomRaidEncounterProfile profile, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force, long deadline, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
	{
		for (var entry : new ArrayList<>(state._supportLeases.entrySet()))
		{
			final var actor = entry.getValue().actorSnapshot();
			if ((actor == null) || (!actor.attacking() && !actor.casting()))
			{
				entry.getValue().complete();
				state._supportLeases.remove(entry.getKey());
			}
		}
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(force);
		final Set<MemberRef> assignedMembers = new LinkedHashSet<>();
		for (PartySnapshot party : force.parties())
		{
			final Map<MemberRef, MemberSnapshot> partySnapshots = exactPartySnapshots(party, snapshots, assignedMembers);
			if (partySnapshots == null)
			{
				return RuntimeStatus.INVALID;
			}
			for (TacticalDirective directive : _tactics.plan(party.leader(), party.members(), partySnapshots))
			{
				if (!supportDirective(directive.kind()) || !state._providers.getOrDefault(directive.capabilityKey(), Set.of()).contains(directive.actor().profileId()) || state._supportLeases.containsKey(directive.actor().profileId()))
				{
					continue;
				}
				final String operation = "raid.support." + directive.actor().profileId() + '.' + profile.contentId();
				_tactics.dispatch(directive, operation, deadline, token).ifPresent(lease -> state._supportLeases.put(directive.actor().profileId(), lease));
			}
		}
		return assignedMembers.size() == snapshots.size() ? RuntimeStatus.INTERMEDIATE : RuntimeStatus.INVALID;
	}

	private RuntimeStatus advanceMechanicCombat(RuntimeAttempt state, MechanicContext context, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force, List<Integer> attackers)
	{
		final List<MemberSnapshot> offense = offenseMembers(state, force);
		if (offense.isEmpty())
		{
			return RuntimeStatus.NO_CONTROLLABLE_OFFENSE;
		}
		boolean capacityLimited = false;
		int attackerIndex = 0;
		for (MemberSnapshot member : offense)
		{
			if (state._mechanicClaims.containsKey(member.ref().profileId()) || (attackerIndex >= attackers.size()))
			{
				continue;
			}
			final PhantomCombatMode mode = supportedMode(member);
			if (mode == null)
			{
				continue;
			}
			final int targetObjectId = attackers.get(attackerIndex++);
			final StartResult started = _combat.startSession(new PhantomCombatRequest(member.ref().profileId(), targetObjectId, mode, true, false, COMBAT_TIMEOUT_MILLIS, context.token()));
			if (started.accepted() && (started.session() != null))
			{
				state._mechanicClaims.put(member.ref().profileId(), new MechanicClaim(member.ref().profileId(), started.session().generation(), targetObjectId, context.token()));
			}
			else if (started.status() == StartStatus.REJECTED_CAPACITY)
			{
				capacityLimited = true;
			}
		}
		return !state._mechanicClaims.isEmpty() || capacityLimited ? RuntimeStatus.INTERMEDIATE : RuntimeStatus.NO_CONTROLLABLE_OFFENSE;
	}

	private boolean pollMechanicSessions(RuntimeAttempt state, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
	{
		for (MechanicClaim claim : new ArrayList<>(state._mechanicClaims.values()))
		{
			if (!_combat.matchesOwnedSession(claim.profileId(), claim.generation(), claim.targetObjectId(), token))
			{
				if (!_combat.hasClaim(claim.profileId()))
				{
					state._mechanicClaims.remove(claim.profileId());
				}
				continue;
			}
			if (_combat.consumeTerminal(claim.profileId()).isPresent())
			{
				state._mechanicClaims.remove(claim.profileId());
			}
		}
		return !state._mechanicClaims.isEmpty();
	}

	private MemberSnapshot scout(RuntimeAttempt state, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		if (state._scoutProfileId > 0)
		{
			final MemberSnapshot current = force.members().stream().filter(member -> member.ref().profileId() == state._scoutProfileId).findFirst().orElse(null);
			if ((current != null) && !current.dead())
			{
				return current;
			}
		}
		final MemberSnapshot selected = offenseMembers(state, force).stream().findFirst().orElseGet(() -> force.members().stream().filter(member -> (member.ref().kind() == MemberKind.PHANTOM) && !member.dead()).sorted(Comparator.comparing(member -> member.ref().stableKey())).findFirst().orElse(null));
		if (selected != null)
		{
			state._scoutProfileId = selected.ref().profileId();
		}
		return selected;
	}

	private RuntimeStatus routeScout(RuntimeAttempt state, MechanicContext context, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force, MemberSnapshot scout, CandleEvidence candle)
	{
		if ((state._mechanicCandleObjectId != 0) && (state._mechanicCandleObjectId != candle.objectId()))
		{
			resetMechanicRoute(state);
		}
		state._mechanicCandleObjectId = candle.objectId();
		final Map<MemberRef, MemberSnapshot> snapshots = snapshots(force);
		final String groupId = PhantomPartyModel.sha256("raid.attempt.mechanic|" + context.attemptAuthorityHash());
		state._mechanicGroupId = groupId;
		return advanceRoute(groupId, state._mechanicRoute, scout.ref(), List.of(scout.ref()), snapshots, candle.point(), new PhantomDomainRef("raid.candle", Integer.toString(candle.objectId())), context.logicalDeadlineNanos(), context.token());
	}

	private RuntimeStatus advanceRoute(String groupId, RouteProgress progress, MemberRef leader, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, PhantomNavigationPoint destination, PhantomDomainRef destinationRef, long deadline, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
	{
		if (progress._complete)
		{
			return RuntimeStatus.COMPLETE;
		}
		final long now = _logicalClock.getAsLong();
		if (progress._manifest == null)
		{
			if (!progress._requested)
			{
				progress._generation++;
				final RouteAttempt requested = _routes.request(groupId, progress._generation, snapshots.get(leader), destinationRef, destination, _topologyHash.get(), now, deadline);
				progress._requested = true;
				if (requested.status() == AttemptStatus.READY)
				{
					progress._manifest = requested.route();
				}
				else if ((requested.status() != AttemptStatus.PENDING) && (requested.status() != AttemptStatus.NONE))
				{
					return RuntimeStatus.INVALID;
				}
			}
			if (progress._manifest == null)
			{
				final RouteAttempt polled = _routes.poll(groupId);
				if (polled.status() == AttemptStatus.READY)
				{
					progress._manifest = polled.route();
				}
				else if ((polled.status() != AttemptStatus.PENDING) && (polled.status() != AttemptStatus.NONE))
				{
					return RuntimeStatus.INVALID;
				}
			}
		}
		if (progress._manifest == null)
		{
			return RuntimeStatus.INTERMEDIATE;
		}
		final var advanced = _routes.advance(groupId, progress._manifest, leader, roster, snapshots, 2 + (roster.size() * 2), now, _topologyHash.get(), token);
		progress._manifest = advanced.route();
		if (progress._manifest.status() == RouteStatus.ARRIVED)
		{
			_routes.cancel(groupId);
			progress._complete = true;
			return RuntimeStatus.COMPLETE;
		}
		return progress._manifest.status() == RouteStatus.FAILED ? RuntimeStatus.INVALID : RuntimeStatus.INTERMEDIATE;
	}

	private List<MemberSnapshot> offenseMembers(RuntimeAttempt state, org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		final Set<Long> reserved = state._providers.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		return force.members().stream().filter(member -> (member.ref().kind() == MemberKind.PHANTOM) && !member.dead() && !reserved.contains(member.ref().profileId()) && (supportedMode(member) != null)).sorted(Comparator.comparing(member -> member.ref().stableKey())).toList();
	}

	private static Optional<MemberCapability> usableCapability(MemberSnapshot member, String capabilityKey, int minimumRank)
	{
		return member.capabilities().stream().filter(capability -> capability.capabilityKey().equals(capabilityKey) && (capability.rank() >= minimumRank) && capability.intrinsic() && capability.learned() && (capability.actionSkillId() > 0) && (capability.actionSkillLevel() > 0)).sorted(Comparator.comparingInt(MemberCapability::rank).reversed().thenComparing(MemberCapability::identity)).findFirst();
	}

	private static boolean providesCapability(MemberSnapshot member, String capabilityKey, int minimumRank)
	{
		return member.capabilities().stream().anyMatch(capability -> capability.capabilityKey().equals(capabilityKey) && (capability.rank() >= minimumRank) && capability.intrinsic() && capability.learned());
	}

	private static PhantomCombatMode supportedMode(MemberSnapshot member)
	{
		return List.of(PhantomCombatMode.RANGED_MAGIC, PhantomCombatMode.RANGED_PHYSICAL, PhantomCombatMode.MELEE_PHYSICAL).stream().filter(mode -> usableCapability(member, mode.capabilityKey(), 1).isPresent()).findFirst().orElse(null);
	}

	private static boolean supportDirective(DirectiveKind kind)
	{
		return (kind == DirectiveKind.HEAL_MEMBER) || (kind == DirectiveKind.RESURRECT_MEMBER) || (kind == DirectiveKind.RECHARGE_MEMBER) || (kind == DirectiveKind.PARTY_SUPPORT);
	}

	private static Map<MemberRef, MemberSnapshot> snapshots(org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot force)
	{
		return force.members().stream().collect(Collectors.toMap(MemberSnapshot::ref, value -> value));
	}

	private static Map<MemberRef, MemberSnapshot> exactPartySnapshots(PartySnapshot party, Map<MemberRef, MemberSnapshot> forceSnapshots, Set<MemberRef> assignedMembers)
	{
		if ((party.members().size() > 9) || (party.members().size() != new LinkedHashSet<>(party.members()).size()))
		{
			return null;
		}
		final Map<MemberRef, MemberSnapshot> result = new LinkedHashMap<>();
		for (MemberRef member : party.members())
		{
			final MemberSnapshot snapshot = forceSnapshots.get(member);
			if ((snapshot == null) || !snapshot.ref().equals(member) || !assignedMembers.add(member))
			{
				return null;
			}
			result.put(member, snapshot);
		}
		return result.size() == party.members().size() ? Map.copyOf(result) : null;
	}

	private static boolean nativeLootComplete(RuntimeAttempt state)
	{
		return state._actualDeathObserved && ((state._collectorProfileId == 0) || state._collectorTerminal);
	}

	private void resetMechanicRoute(RuntimeAttempt state)
	{
		if (state._mechanicGroupId != null)
		{
			_routes.cancel(state._mechanicGroupId);
		}
		state._mechanicGroupId = null;
		state._mechanicCandleObjectId = 0;
		state._mechanicRoute = new RouteProgress();
	}

	private void cancelActions(RuntimeAttempt state)
	{
		for (RaidClaim claim : new ArrayList<>(state._raidClaims.values()))
		{
			if (_combat.matchesRaidSession(claim.profileId(), claim.generation(), state._targetObjectId, state._targetNpcId, state._targetInstanceId, state._authorityHash))
			{
				_combat.cancel(claim.profileId());
			}
		}
		for (MechanicClaim claim : new ArrayList<>(state._mechanicClaims.values()))
		{
			if (_combat.matchesOwnedSession(claim.profileId(), claim.generation(), claim.targetObjectId(), claim.token()))
			{
				_combat.cancel(claim.profileId());
			}
		}
		state._raidClaims.clear();
		state._mechanicClaims.clear();
		state._supportLeases.values().forEach(ExternalActionLease::close);
		state._supportLeases.clear();
		resetMechanicRoute(state);
	}

	private void cancelRoutes(RuntimeAttempt state)
	{
		for (String groupId : state._retreatRoutes.keySet())
		{
			_routes.cancel(groupId);
		}
		state._retreatRoutes.clear();
	}

	private record RaidClaim(long profileId, long generation, boolean collector)
	{
	}

	private record MechanicClaim(long profileId, long generation, int targetObjectId, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
	{
	}

	private static final class RouteProgress
	{
		private long _generation;
		private boolean _requested;
		private boolean _complete;
		private RouteManifest _manifest;
	}

	private static final class RuntimeAttempt
	{
		private final Map<String, Set<Long>> _providers = new LinkedHashMap<>();
		private final Map<Long, ExternalActionLease> _supportLeases = new LinkedHashMap<>();
		private final Map<Long, RaidClaim> _raidClaims = new LinkedHashMap<>();
		private final Map<Long, MechanicClaim> _mechanicClaims = new LinkedHashMap<>();
		private final Map<String, RouteProgress> _retreatRoutes = new LinkedHashMap<>();
		private RouteProgress _mechanicRoute = new RouteProgress();
		private String _mechanicGroupId;
		private String _authorityHash = "";
		private long _scoutProfileId;
		private long _collectorProfileId;
		private int _instanceId;
		private int _mechanicCandleObjectId;
		private int _interactionRetries;
		private int _targetObjectId;
		private int _targetNpcId;
		private int _targetInstanceId;
		private boolean _actualDeathObserved;
		private boolean _collectorTerminal;
	}
}
