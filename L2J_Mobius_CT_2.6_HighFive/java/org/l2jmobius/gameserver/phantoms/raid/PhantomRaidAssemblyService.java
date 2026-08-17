/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InvitationSnapshot;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator.AttemptStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator.RouteAttempt;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossLocation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentAttempt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentAttemptStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentPlan;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RecruitmentStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/**
 * Bounded process-local owner for explicit raid assembly and physical staging.
 * Canonical Party, CommandChannel, Navigation and Combat services retain mutation ownership.
 */
public final class PhantomRaidAssemblyService
{
	public static final String PREPARE_GOAL_TYPE = "raid.prepare";
	public static final String PARTICIPATE_GOAL_TYPE = "raid.participate";
	public static final int MAX_ACTIVE_ASSEMBLIES = 64;
	public static final int MAX_RECEIPTS = 256;
	public static final int ARRIVAL_RADIUS = 250;
	public static final int LIVE_STAND_OFF_RADIUS = 1800;
	public static final int ANCHOR_PARTY_RADIUS = 300;
	public static final int LIVE_CENTRE_DRIFT = 500;

	private final PhantomGoalStore _goals;
	private final PhantomRaidReadinessService _readiness;
	private final PhantomRaidRecruitmentService _recruitment;
	private final PhantomPartyBackend _party;
	private final PhantomRaidAuthority _authority;
	private final Supplier<PhantomTopologyQuery> _topology;
	private final PhantomPartyRouteCoordinator _routes;
	private final LongSupplier _clock;
	private final HeightResolver _height;
	private final Map<Long, Assembly> _active = new HashMap<>();
	private final LinkedHashMap<AssemblyIdentity, ReadyReceipt> _receipts = new LinkedHashMap<>();
	private boolean _stopping;

	public PhantomRaidAssemblyService(PhantomGoalStore goals, PhantomRaidReadinessService readiness, PhantomRaidRecruitmentService recruitment, PhantomPartyBackend party, PhantomRaidAuthority authority, Supplier<PhantomTopologyQuery> topology, PhantomPartyRouteCoordinator routes, LongSupplier clock)
	{
		this(goals, readiness, recruitment, party, authority, topology, routes, clock, (x, y, factualZ) -> GeoEngine.getInstance().hasGeo(x, y) ? GeoEngine.getInstance().getHeight(x, y, factualZ) : factualZ);
	}

	public PhantomRaidAssemblyService(PhantomGoalStore goals, PhantomRaidReadinessService readiness, PhantomRaidRecruitmentService recruitment, PhantomPartyBackend party, PhantomRaidAuthority authority, Supplier<PhantomTopologyQuery> topology, PhantomPartyRouteCoordinator routes, LongSupplier clock, HeightResolver height)
	{
		_goals = Objects.requireNonNull(goals);
		_readiness = Objects.requireNonNull(readiness);
		_recruitment = Objects.requireNonNull(recruitment);
		_party = Objects.requireNonNull(party);
		_authority = Objects.requireNonNull(authority);
		_topology = Objects.requireNonNull(topology);
		_routes = Objects.requireNonNull(routes);
		_clock = Objects.requireNonNull(clock);
		_height = Objects.requireNonNull(height);
	}

	public synchronized AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision)
	{
		if (_stopping)
		{
			return result(AssemblyStatus.CANCELLED, "raid.assembly.stopping", null);
		}
		final long now = _clock.getAsLong();
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(leaderProfileId);
		if (stored.isEmpty() || (stored.get().goal().goalId() != goalId) || (stored.get().goal().revision() != goalRevision))
		{
			return result(AssemblyStatus.BLOCKED, "raid.assembly.goal.stale", null);
		}
		final GoalContext context = validateLeaderGoal(leaderProfileId, stored.get().goal(), now);
		if (context == null)
		{
			final Assembly current = _active.get(leaderProfileId);
			if ((current != null) && (current._identity.goalId() == goalId) && (current._identity.goalRevision() <= goalRevision))
			{
				cleanup(current);
				current._status = now >= stored.get().goal().deadlineEpochMillis() ? AssemblyStatus.EXPIRED : AssemblyStatus.BLOCKED;
				current._reason = "raid.assembly.goal.invalid";
			}
			return result(now >= stored.get().goal().deadlineEpochMillis() ? AssemblyStatus.EXPIRED : AssemblyStatus.BLOCKED, "raid.assembly.goal.invalid", null);
		}

		Assembly assembly = _active.get(leaderProfileId);
		if ((assembly != null) && !assembly._identity.equals(context.identity()))
		{
			cleanup(assembly);
			_active.remove(leaderProfileId);
			assembly = null;
		}
		if (assembly == null)
		{
			if (_active.size() >= MAX_ACTIVE_ASSEMBLIES)
			{
				return result(AssemblyStatus.BLOCKED, "raid.assembly.capacity", null);
			}
			assembly = new Assembly(context);
			_active.put(leaderProfileId, assembly);
		}
		if (now >= assembly._goal.deadlineEpochMillis())
		{
			cleanup(assembly);
			assembly._status = AssemblyStatus.EXPIRED;
			assembly._reason = "raid.assembly.deadline";
			return result(assembly);
		}
		if (assembly._status.terminal())
		{
			return result(assembly);
		}

		return switch (assembly._status)
		{
			case ASSEMBLING -> advanceAssembly(assembly, now);
			case WAITING_CONSENT -> advanceConsent(assembly);
			case GATHERING -> advanceGathering(assembly, now);
			case FINAL_PREPARATION -> advanceFinalPreparation(assembly, now);
			default -> result(assembly);
		};
	}

	public synchronized boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reason)
	{
		final Assembly assembly = _active.get(leaderProfileId);
		if ((assembly == null) || (assembly._identity.goalId() != goalId) || (assembly._identity.goalRevision() != goalRevision))
		{
			return false;
		}
		cleanup(assembly);
		assembly._status = AssemblyStatus.CANCELLED;
		assembly._reason = (reason == null) || reason.isBlank() ? "raid.assembly.cancelled" : reason;
		return true;
	}

	public synchronized ParticipationOutcome participation(long profileId, long goalId, long goalRevision)
	{
		final long now = _clock.getAsLong();
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(profileId);
		if (stored.isEmpty() || (stored.get().goal().goalId() != goalId) || (stored.get().goal().revision() != goalRevision))
		{
			return ParticipationOutcome.IMPOSSIBLE;
		}
		final PhantomGoal goal = stored.get().goal();
		if (!validParticipationGoal(profileId, goal, null, now))
		{
			return now >= goal.deadlineEpochMillis() ? ParticipationOutcome.EXPIRED : ParticipationOutcome.IMPOSSIBLE;
		}
		final Optional<MemberRef> participant = _party.currentMember(profileId);
		if (participant.isEmpty())
		{
			return ParticipationOutcome.IMPOSSIBLE;
		}
		final String contentId = goal.target().key();
		for (Assembly assembly : _active.values())
		{
			if (!assembly._identity.contentId().equals(contentId) || assembly._status.terminal() || !assembly._candidates.contains(participant.get()))
			{
				continue;
			}
			final CurrentForceObservation force = _party.currentForce(assembly._actor);
			if ((force.status() == CurrentForceStatus.AVAILABLE) && force.snapshot().members().stream().anyMatch(member -> member.ref().equals(participant.get())))
			{
				return ParticipationOutcome.JOINED;
			}
			return ParticipationOutcome.WAITING;
		}
		return ParticipationOutcome.IMPOSSIBLE;
	}

	public synchronized Optional<ReadyReceipt> readyReceipt(long leaderProfileId)
	{
		final Assembly assembly = _active.get(leaderProfileId);
		return (assembly == null) || (assembly._ready == null) ? Optional.empty() : Optional.of(assembly._ready);
	}

	public synchronized void beginStop()
	{
		if (_stopping)
		{
			return;
		}
		_stopping = true;
		for (Assembly assembly : _active.values())
		{
			cleanup(assembly);
			if (!assembly._status.terminal())
			{
				assembly._status = AssemblyStatus.CANCELLED;
				assembly._reason = "raid.assembly.shutdown";
			}
		}
	}

	public synchronized Snapshot snapshot()
	{
		final int pending = (int) _active.values().stream().filter(value -> value._pendingIdentity != null).count();
		final int routeGroups = _active.values().stream().mapToInt(value -> value._routeProgress.size()).sum();
		return new Snapshot(_active.size(), pending, routeGroups, _receipts.size(), _stopping);
	}

	private AdvanceResult advanceAssembly(Assembly assembly, long now)
	{
		final RaidReadiness readiness = _readiness.assess(assembly._actor, assembly._identity.contentId());
		if (readiness.targetAvailability() != TargetAvailability.AVAILABLE)
		{
			return block(assembly, readiness.targetAvailability() == TargetAvailability.UNAVAILABLE ? "raid.assembly.target.unavailable" : "raid.assembly.target.unknown");
		}
		if (readiness.groupReady())
		{
			final StagingCenter centre = selectCentre(assembly, readiness);
			if (centre == null)
			{
				return block(assembly, "raid.assembly.staging.authority_unavailable");
			}
			assembly._structuralHash = structuralHash(readiness.force().snapshot());
			assembly._centre = centre;
			assembly._topologyHash = _topology.get().snapshot().canonicalHash().toUpperCase(java.util.Locale.ROOT);
			assembly._slots = slots(assembly._identity, readiness.force().snapshot().parties(), centre);
			assembly._routeProgress.clear();
			assembly._status = AssemblyStatus.GATHERING;
			assembly._reason = "raid.assembly.gathering";
			return result(assembly);
		}
		if ((readiness.force().status() != CurrentForceStatus.AVAILABLE) || (readiness.force().snapshot() == null))
		{
			return block(assembly, "raid.assembly.force.unavailable");
		}
		final Set<MemberRef> current = readiness.force().snapshot().members().stream().map(MemberSnapshot::ref).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final List<MemberRef> candidates = assembly._candidates.stream().filter(candidate -> !current.contains(candidate) && !assembly._excluded.contains(candidate)).toList();
		final RecruitmentAttempt attempt = _recruitment.recruitNext(assembly._actor, assembly._identity.contentId(), candidates);
		if (attempt.status() == RecruitmentAttemptStatus.INVITE_DELIVERED)
		{
			assembly._pendingCandidate = attempt.plan().selectedCandidate();
			assembly._pendingIdentity = attempt.inviteResult().identity();
			assembly._status = AssemblyStatus.WAITING_CONSENT;
			assembly._reason = "raid.assembly.waiting_consent";
			return result(assembly);
		}
		if (attempt.status() == RecruitmentAttemptStatus.INVITE_REJECTED)
		{
			if (attempt.plan().selectedCandidate() != null)
			{
				assembly._excluded.add(attempt.plan().selectedCandidate());
			}
			assembly._reason = "raid.assembly.invite_rejected";
			return result(assembly);
		}
		final RecruitmentStatus status = attempt.plan().status();
		if (status == RecruitmentStatus.GROUP_READY)
		{
			assembly._reason = "raid.assembly.fresh_readiness_required";
			return result(assembly);
		}
		return block(assembly, "raid.assembly.recruitment." + status.name().toLowerCase(java.util.Locale.ROOT));
	}

	private AdvanceResult advanceConsent(Assembly assembly)
	{
		final MemberRef candidate = assembly._pendingCandidate;
		final InvitationIdentity identity = assembly._pendingIdentity;
		if ((candidate == null) || (identity == null))
		{
			return reassemble(assembly, "raid.assembly.consent.identity_missing");
		}
		final CurrentForceObservation leaderForce = _party.currentForce(assembly._actor);
		if ((leaderForce.status() == CurrentForceStatus.AVAILABLE) && leaderForce.snapshot().members().stream().anyMatch(member -> member.ref().equals(candidate)))
		{
			clearPending(assembly);
			assembly._status = AssemblyStatus.ASSEMBLING;
			assembly._reason = "raid.assembly.canonical_join_observed";
			return result(assembly);
		}
		final Optional<InvitationSnapshot> pending = _party.observeCommandChannelInvitation(candidate);
		if (pending.isEmpty() || !pending.get().identity().equals(identity))
		{
			assembly._excluded.add(candidate);
			clearPending(assembly);
			assembly._status = AssemblyStatus.ASSEMBLING;
			assembly._reason = "raid.assembly.consent.pending_lost";
			return result(assembly);
		}
		if (candidate.kind() == MemberKind.REAL)
		{
			assembly._reason = "raid.assembly.consent.real_manual";
			return result(assembly);
		}

		final boolean standaloneLeader = exactStandaloneLeader(candidate);
		final boolean willing = validParticipationGoal(candidate.profileId(), _goals.load(candidate.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null), assembly._identity.contentId(), _clock.getAsLong());
		final RecruitmentPlan freshPlan = _recruitment.plan(assembly._actor, assembly._identity.contentId(), List.of(candidate));
		final boolean useful = (freshPlan.status() == RecruitmentStatus.CANDIDATE_SELECTED) && candidate.equals(freshPlan.selectedCandidate());
		final boolean accept = standaloneLeader && willing && useful;
		final var response = _party.respondCommandChannel(candidate, accept ? Response.ACCEPT : Response.REFUSE, identity);
		if (!response.accepted())
		{
			assembly._excluded.add(candidate);
		}
		clearPending(assembly);
		assembly._status = AssemblyStatus.ASSEMBLING;
		assembly._reason = accept ? "raid.assembly.consent.accepted" : "raid.assembly.consent.refused";
		return result(assembly);
	}

	private AdvanceResult advanceGathering(Assembly assembly, long now)
	{
		final RaidReadiness readiness = _readiness.assess(assembly._actor, assembly._identity.contentId());
		final AdvanceResult authority = validateFrozenAuthority(assembly, readiness);
		if (authority != null)
		{
			return authority;
		}
		final CurrentForceSnapshot force = readiness.force().snapshot();
		final Map<MemberRef, MemberSnapshot> snapshots = force.members().stream().collect(java.util.stream.Collectors.toMap(MemberSnapshot::ref, value -> value));
		boolean allArrived = true;
		for (PartySlot slot : assembly._slots)
		{
			final PartySnapshot party = force.parties().stream().filter(value -> value.leader().equals(slot.partyLeader())).findFirst().orElse(null);
			if (party == null)
			{
				return reassemble(assembly, "raid.assembly.force.party_missing");
			}
			if (insideSlot(party.members(), snapshots, slot.point()))
			{
				cancelRoute(assembly, slot.groupId());
				continue;
			}
			allArrived = false;
			final List<MemberRef> phantoms = party.members().stream().filter(member -> member.kind() == MemberKind.PHANTOM).sorted(Comparator.comparing(MemberRef::stableKey)).toList();
			if (phantoms.isEmpty())
			{
				continue;
			}
			final MemberRef routeActor = phantoms.getFirst();
			final MemberSnapshot routeSnapshot = snapshots.get(routeActor);
			if (routeSnapshot == null)
			{
				return reassemble(assembly, "raid.assembly.force.route_actor_missing");
			}
			RouteProgress progress = assembly._routeProgress.get(slot.groupId());
			if (progress == null)
			{
				final RouteAttempt requested = _routes.request(slot.groupId(), Math.max(1, assembly._identity.goalRevision() + 1), routeSnapshot, slot.destination(), slot.point(), assembly._topologyHash, now, assembly._goal.deadlineEpochMillis());
				progress = new RouteProgress(requested.route());
				assembly._routeProgress.put(slot.groupId(), progress);
				if (terminalRouteFailure(requested))
				{
					return block(assembly, "raid.assembly.staging.entry_required");
				}
			}
			if (progress._manifest == null)
			{
				final RouteAttempt polled = _routes.poll(slot.groupId());
				if (terminalRouteFailure(polled))
				{
					return block(assembly, "raid.assembly.staging.entry_required");
				}
				if (polled.status() == AttemptStatus.READY)
				{
					progress._manifest = polled.route();
				}
			}
			if (progress._manifest != null)
			{
				final var advanced = _routes.advance(slot.groupId(), progress._manifest, routeActor, party.members(), snapshots, 2 + (party.members().size() * 2), now, assembly._topologyHash, () -> false);
				progress._manifest = advanced.route();
				if (advanced.route().status() == RouteStatus.ARRIVED)
				{
					cancelRoute(assembly, slot.groupId());
				}
				else if (advanced.route().status() == RouteStatus.FAILED)
				{
					return block(assembly, "raid.assembly.staging.entry_required");
				}
			}
		}
		if (allArrived)
		{
			cancelRoutes(assembly);
			assembly._status = AssemblyStatus.FINAL_PREPARATION;
			assembly._reason = "raid.assembly.final_preparation";
		}
		return result(assembly);
	}

	private AdvanceResult advanceFinalPreparation(Assembly assembly, long now)
	{
		final RaidReadiness readiness = _readiness.assess(assembly._actor, assembly._identity.contentId());
		final AdvanceResult authority = validateFrozenAuthority(assembly, readiness);
		if (authority != null)
		{
			return authority;
		}
		final CurrentForceSnapshot force = readiness.force().snapshot();
		final Map<MemberRef, MemberSnapshot> snapshots = force.members().stream().collect(java.util.stream.Collectors.toMap(MemberSnapshot::ref, value -> value));
		for (PartySlot slot : assembly._slots)
		{
			final PartySnapshot party = force.parties().stream().filter(value -> value.leader().equals(slot.partyLeader())).findFirst().orElse(null);
			if ((party == null) || !insideSlot(party.members(), snapshots, slot.point()))
			{
				assembly._routeProgress.clear();
				assembly._status = AssemblyStatus.GATHERING;
				assembly._reason = "raid.assembly.physical_staging_lost";
				return result(assembly);
			}
		}
		final boolean alive = force.members().stream().noneMatch(MemberSnapshot::dead);
		if (!alive || !readiness.groupReady())
		{
			assembly._reason = alive ? "raid.assembly.final_readiness_transient" : "raid.assembly.final_member_dead";
			return result(assembly);
		}
		assembly._ready = new ReadyReceipt(assembly._identity, assembly._structuralHash, assembly._centre, assembly._slots, readiness, now);
		assembly._status = AssemblyStatus.READY_AT_STAGING;
		assembly._reason = "raid.assembly.ready_at_staging";
		rememberReceipt(assembly._ready);
		return result(assembly);
	}

	private AdvanceResult validateFrozenAuthority(Assembly assembly, RaidReadiness readiness)
	{
		if (readiness.targetAvailability() != TargetAvailability.AVAILABLE)
		{
			return block(assembly, readiness.targetAvailability() == TargetAvailability.UNAVAILABLE ? "raid.assembly.target.unavailable" : "raid.assembly.target.unknown");
		}
		if ((readiness.force().status() != CurrentForceStatus.AVAILABLE) || (readiness.force().snapshot() == null))
		{
			return reassemble(assembly, "raid.assembly.force.unavailable");
		}
		if (!assembly._structuralHash.equals(structuralHash(readiness.force().snapshot())))
		{
			return reassemble(assembly, "raid.assembly.force.structural_drift");
		}
		final String currentTopology = _topology.get().snapshot().canonicalHash().toUpperCase(java.util.Locale.ROOT);
		if (!assembly._topologyHash.equals(currentTopology))
		{
			return reassemble(assembly, "raid.assembly.topology.drift");
		}
		if (assembly._centre.source() == StagingSource.LIVE_BOSS)
		{
			final ContentRequirementFact requirement = readiness.content().requirement();
			final Optional<BossLocation> current = _authority.observeLocation(requirement.contentKind(), requirement.npcId());
			if (current.isEmpty())
			{
				return block(assembly, "raid.assembly.staging.live_location_missing");
			}
			if (distance2d(assembly._centre.point(), point(current.get())) > LIVE_CENTRE_DRIFT)
			{
				return reassemble(assembly, "raid.assembly.staging.live_centre_drift");
			}
		}
		return null;
	}

	private GoalContext validateLeaderGoal(long profileId, PhantomGoal goal, long now)
	{
		if ((goal == null) || !PREPARE_GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.deadlineEpochMillis() <= now) || (goal.target() == null) || !"raid.content".equals(goal.target().namespace()))
		{
			return null;
		}
		if ((goal.subject() != null) && (!"profile".equals(goal.subject().namespace()) || (parsePositive(goal.subject().key()) != profileId)))
		{
			return null;
		}
		if ((goal.selectedAnchor() != null) && !"topology.anchor".equals(goal.selectedAnchor().namespace()))
		{
			return null;
		}
		final Optional<MemberRef> actor = _party.currentMember(profileId);
		if (actor.isEmpty() || (actor.get().kind() != MemberKind.PHANTOM) || (actor.get().profileId() != profileId))
		{
			return null;
		}
		final List<MemberRef> candidates = new ArrayList<>(goal.validSources().size());
		final Set<MemberRef> unique = new HashSet<>();
		for (PhantomDomainRef source : goal.validSources())
		{
			final MemberRef candidate = resolveSource(source);
			if ((candidate == null) || !unique.add(candidate))
			{
				return null;
			}
			candidates.add(candidate);
		}
		candidates.sort(Comparator.comparing(MemberRef::stableKey));
		return new GoalContext(new AssemblyIdentity(profileId, goal.goalId(), goal.revision(), goal.target().key()), goal, actor.get(), List.copyOf(candidates));
	}

	private MemberRef resolveSource(PhantomDomainRef source)
	{
		final long value = parsePositive(source.key());
		if (value <= 0)
		{
			return null;
		}
		if ("profile".equals(source.namespace()))
		{
			return _party.currentMember(value).orElse(null);
		}
		if (!"character.object".equals(source.namespace()) || (value > Integer.MAX_VALUE))
		{
			return null;
		}
		final int objectId = (int) value;
		final OptionalLong managed = _party.managedProfileId(objectId);
		if (managed.isPresent())
		{
			final MemberRef member = _party.currentMember(managed.getAsLong()).orElse(null);
			return (member != null) && (member.characterObjectId() == objectId) ? member : null;
		}
		return MemberRef.real(objectId);
	}

	private boolean exactStandaloneLeader(MemberRef candidate)
	{
		final CurrentForceObservation force = _party.currentForce(candidate);
		return (force.status() == CurrentForceStatus.AVAILABLE) && (force.snapshot() != null) && !force.snapshot().commandChannelPresent() && (force.snapshot().parties().size() == 1) && candidate.equals(force.snapshot().actor()) && candidate.equals(force.snapshot().partyLeader()) && candidate.equals(force.snapshot().parties().getFirst().leader());
	}

	private static boolean validParticipationGoal(long profileId, PhantomGoal goal, String contentId, long now)
	{
		if ((goal == null) || !PARTICIPATE_GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.deadlineEpochMillis() <= now) || (goal.target() == null) || !"raid.content".equals(goal.target().namespace()) || ((contentId != null) && !contentId.equals(goal.target().key())))
		{
			return false;
		}
		return (goal.subject() == null) || ("profile".equals(goal.subject().namespace()) && (parsePositive(goal.subject().key()) == profileId));
	}

	private StagingCenter selectCentre(Assembly assembly, RaidReadiness readiness)
	{
		final ContentRequirementFact content = readiness.content().requirement();
		final PhantomTopologyQuery topology = _topology.get();
		if ((content.topologyAnchorId() != null) && !content.topologyAnchorId().isBlank())
		{
			return topology.findAnchor(content.topologyAnchorId()).map(anchor -> anchorCentre(StagingSource.CONTENT_ANCHOR, anchor)).orElse(null);
		}
		if (assembly._goal.selectedAnchor() != null)
		{
			return topology.findAnchor(assembly._goal.selectedAnchor().key()).map(anchor -> anchorCentre(StagingSource.GOAL_ANCHOR, anchor)).orElse(null);
		}
		return _authority.observeLocation(content.contentKind(), content.npcId()).map(location -> new StagingCenter(StagingSource.LIVE_BOSS, point(location), PhantomPartyModel.sha256("live|" + location))).orElse(null);
	}

	private static StagingCenter anchorCentre(StagingSource source, PhantomTopologyAnchor anchor)
	{
		final PhantomTopologyPoint point = anchor.point();
		return new StagingCenter(source, new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId()), PhantomPartyModel.sha256(source + "|" + anchor.id() + "|" + point));
	}

	private List<PartySlot> slots(AssemblyIdentity identity, List<PartySnapshot> parties, StagingCenter centre)
	{
		final List<PartySnapshot> ordered = parties.stream().sorted(Comparator.comparing(party -> party.leader().stableKey())).toList();
		final int radius = centre.source() == StagingSource.LIVE_BOSS ? LIVE_STAND_OFF_RADIUS : ANCHOR_PARTY_RADIUS;
		final List<PartySlot> result = new ArrayList<>(ordered.size());
		for (int index = 0; index < ordered.size(); index++)
		{
			final double angle = (Math.PI * 2 * index) / ordered.size();
			final int x = centre.point().x() + (int) Math.round(Math.cos(angle) * radius);
			final int y = centre.point().y() + (int) Math.round(Math.sin(angle) * radius);
			final int z = _height.resolve(x, y, centre.point().z());
			final MemberRef leader = ordered.get(index).leader();
			final String groupId = PhantomPartyModel.sha256("raid.assembly.route|" + identity.stableKey() + "|" + leader.stableKey());
			final PhantomDomainRef destination = new PhantomDomainRef("raid.staging", identity.goalId() + "." + leader.stableKey());
			result.add(new PartySlot(leader, new PhantomNavigationPoint(x, y, z, centre.point().instanceId()), destination, groupId));
		}
		return List.copyOf(result);
	}

	public static String structuralHash(CurrentForceSnapshot force)
	{
		Objects.requireNonNull(force);
		final String identity = force.commandChannelPresent() ? force.commandChannelIdentity() : "party:" + force.partyLeader().stableKey();
		final List<String> parties = force.parties().stream().sorted(Comparator.comparing(party -> party.leader().stableKey())).map(party -> party.leader().stableKey() + "=" + party.members().stream().map(MemberRef::stableKey).sorted().toList()).toList();
		return PhantomPartyModel.sha256(identity + "|" + parties);
	}

	private static boolean insideSlot(List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots, PhantomNavigationPoint slot)
	{
		for (MemberRef member : roster)
		{
			final MemberSnapshot snapshot = snapshots.get(member);
			if ((snapshot == null) || (snapshot.instanceId() != slot.instanceId()) || (distance2d(snapshot.x(), snapshot.y(), slot.x(), slot.y()) > ARRIVAL_RADIUS))
			{
				return false;
			}
		}
		return true;
	}

	private static double distance2d(PhantomNavigationPoint one, PhantomNavigationPoint two)
	{
		return distance2d(one.x(), one.y(), two.x(), two.y());
	}

	private static double distance2d(int x1, int y1, int x2, int y2)
	{
		final long dx = (long) x1 - x2;
		final long dy = (long) y1 - y2;
		return Math.sqrt((dx * dx) + (dy * dy));
	}

	private static PhantomNavigationPoint point(BossLocation location)
	{
		return new PhantomNavigationPoint(location.x(), location.y(), location.z(), location.instanceId());
	}

	private static boolean terminalRouteFailure(RouteAttempt attempt)
	{
		return Set.of(AttemptStatus.FAILED, AttemptStatus.REJECTED, AttemptStatus.UNAVAILABLE).contains(attempt.status());
	}

	private AdvanceResult reassemble(Assembly assembly, String reason)
	{
		cancelRoutes(assembly);
		assembly._structuralHash = null;
		assembly._centre = null;
		assembly._slots = List.of();
		assembly._topologyHash = null;
		assembly._status = AssemblyStatus.ASSEMBLING;
		assembly._reason = reason;
		return result(assembly);
	}

	private AdvanceResult block(Assembly assembly, String reason)
	{
		cleanup(assembly);
		assembly._status = AssemblyStatus.BLOCKED;
		assembly._reason = reason;
		return result(assembly);
	}

	private void cleanup(Assembly assembly)
	{
		if (assembly._pendingIdentity != null)
		{
			_party.cancelCommandChannel(assembly._pendingIdentity);
			clearPending(assembly);
		}
		cancelRoutes(assembly);
	}

	private void cancelRoutes(Assembly assembly)
	{
		final Set<String> groups = new HashSet<>(assembly._routeProgress.keySet());
		for (PartySlot slot : assembly._slots)
		{
			groups.add(slot.groupId());
		}
		groups.forEach(_routes::cancel);
		assembly._routeProgress.clear();
	}

	private void cancelRoute(Assembly assembly, String groupId)
	{
		_routes.cancel(groupId);
		assembly._routeProgress.remove(groupId);
	}

	private static void clearPending(Assembly assembly)
	{
		assembly._pendingCandidate = null;
		assembly._pendingIdentity = null;
	}

	private void rememberReceipt(ReadyReceipt receipt)
	{
		_receipts.remove(receipt.identity());
		_receipts.put(receipt.identity(), receipt);
		while (_receipts.size() > MAX_RECEIPTS)
		{
			_receipts.remove(_receipts.keySet().iterator().next());
		}
	}

	private static AdvanceResult result(Assembly assembly)
	{
		return result(assembly._status, assembly._reason, assembly._ready);
	}

	private static AdvanceResult result(AssemblyStatus status, String reason, ReadyReceipt ready)
	{
		return new AdvanceResult(status, reason, ready);
	}

	private static long parsePositive(String value)
	{
		try
		{
			final long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : -1;
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}

	public enum AssemblyStatus
	{
		ASSEMBLING,
		WAITING_CONSENT,
		GATHERING,
		FINAL_PREPARATION,
		READY_AT_STAGING,
		BLOCKED,
		EXPIRED,
		CANCELLED;

		public boolean terminal()
		{
			return Set.of(READY_AT_STAGING, BLOCKED, EXPIRED, CANCELLED).contains(this);
		}
	}

	public enum StagingSource
	{
		CONTENT_ANCHOR,
		GOAL_ANCHOR,
		LIVE_BOSS
	}

	public enum ParticipationOutcome
	{
		WAITING,
		JOINED,
		EXPIRED,
		IMPOSSIBLE
	}

	@FunctionalInterface
	public interface HeightResolver
	{
		int resolve(int x, int y, int factualZ);
	}

	public record AssemblyIdentity(long leaderProfileId, long goalId, long goalRevision, String contentId)
	{
		public AssemblyIdentity
		{
			if ((leaderProfileId <= 0) || (goalId <= 0) || (goalRevision < 0) || (contentId == null) || contentId.isBlank())
			{
				throw new IllegalArgumentException("Invalid raid assembly identity.");
			}
		}

		public String stableKey()
		{
			return leaderProfileId + "|" + goalId + "|" + goalRevision + "|" + contentId;
		}
	}

	public record StagingCenter(StagingSource source, PhantomNavigationPoint point, String evidenceHash)
	{
		public StagingCenter
		{
			Objects.requireNonNull(source);
			Objects.requireNonNull(point);
			if ((evidenceHash == null) || !evidenceHash.matches("[0-9A-Fa-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid staging centre evidence.");
			}
		}
	}

	public record PartySlot(MemberRef partyLeader, PhantomNavigationPoint point, PhantomDomainRef destination, String groupId)
	{
		public PartySlot
		{
			Objects.requireNonNull(partyLeader);
			Objects.requireNonNull(point);
			Objects.requireNonNull(destination);
			if ((groupId == null) || !groupId.matches("[0-9A-Fa-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid raid Party staging slot.");
			}
		}
	}

	public record ReadyReceipt(AssemblyIdentity identity, String structuralHash, StagingCenter centre, List<PartySlot> slots, RaidReadiness finalReadiness, long completedAtMillis)
	{
		public ReadyReceipt
		{
			Objects.requireNonNull(identity);
			if ((structuralHash == null) || !structuralHash.matches("[0-9A-Fa-f]{64}") || (centre == null) || (slots == null) || slots.isEmpty() || (finalReadiness == null) || !finalReadiness.groupReady() || (completedAtMillis < 0))
			{
				throw new IllegalArgumentException("Invalid READY_AT_STAGING receipt.");
			}
			slots = List.copyOf(slots);
		}
	}

	public record AdvanceResult(AssemblyStatus status, String reasonKey, ReadyReceipt readyReceipt)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(status);
			if ((reasonKey == null) || reasonKey.isBlank() || ((status == AssemblyStatus.READY_AT_STAGING) != (readyReceipt != null)))
			{
				throw new IllegalArgumentException("Invalid raid assembly advance result.");
			}
		}
	}

	public record Snapshot(int activeAssemblies, int pendingInvitations, int routeGroups, int readyReceipts, boolean stopping)
	{
	}

	private record GoalContext(AssemblyIdentity identity, PhantomGoal goal, MemberRef actor, List<MemberRef> candidates)
	{
	}

	private static final class Assembly
	{
		private final AssemblyIdentity _identity;
		private final PhantomGoal _goal;
		private final MemberRef _actor;
		private final List<MemberRef> _candidates;
		private final Set<MemberRef> _excluded = new HashSet<>();
		private final Map<String, RouteProgress> _routeProgress = new HashMap<>();
		private AssemblyStatus _status = AssemblyStatus.ASSEMBLING;
		private String _reason = "raid.assembly.assembling";
		private MemberRef _pendingCandidate;
		private InvitationIdentity _pendingIdentity;
		private String _structuralHash;
		private StagingCenter _centre;
		private List<PartySlot> _slots = List.of();
		private String _topologyHash;
		private ReadyReceipt _ready;

		private Assembly(GoalContext context)
		{
			_identity = context.identity();
			_goal = context.goal();
			_actor = context.actor();
			_candidates = context.candidates();
		}
	}

	private static final class RouteProgress
	{
		private RouteManifest _manifest;

		private RouteProgress(RouteManifest manifest)
		{
			_manifest = manifest;
		}
	}
}
