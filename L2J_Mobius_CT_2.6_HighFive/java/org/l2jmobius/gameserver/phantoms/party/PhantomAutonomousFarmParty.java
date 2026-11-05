/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.CommandOutcome;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationContext;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationDecision;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;

/** Ordinary farm orchestration under the existing shared scheduler. */
public final class PhantomAutonomousFarmParty implements PhantomSchedulerControlPort
{
	public static final int MAXIMUM_CANDIDATES = 16;
	public static final int MAXIMUM_TARGET_DISTANCE = org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.productionDefaults(64).maximumAcquisitionDistance();
	public static final String OBJECTIVE_NAMESPACE = "farm.party";
	private static final Set<String> DAMAGE = Set.of("combat.melee_damage", "combat.ranged_physical_damage", "combat.ranged_magic_damage");
	private static final Set<String> SUPPORT = Set.of("combat.heal", "combat.recharge", "combat.buff", "combat.song", "combat.dance");
	private final PhantomPartyCoordinator _coordinator;
	private final PhantomPartyBackend _backend;
	private final PhantomGoalStore _goals;
	private final PhantomPartyRoleMatcher _roles;
	private final LongSupplier _clock;
	private final java.util.function.BiPredicate<Long, PhantomGoal> _preparationRequired;
	private final java.util.function.BiFunction<Long, PhantomGoal, PhantomVisibleFarmTravel.ArrivalResult> _arrival;
	private final Map<Long, PhantomGoal> _travelContinuations = new LinkedHashMap<>();
	private long _nextPulse;
	private long _nextContinuationPulse;
	private long _cursor;

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock)
	{
		this(coordinator, backend, goals, roles, clock, null);
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock, PhantomVisibleFarmTravel travel)
	{
		this(coordinator, backend, goals, roles, clock, (_, _) -> false, travel == null ? null : travel::observeArrival);
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock,
		java.util.function.BiPredicate<Long, PhantomGoal> preparationRequired, java.util.function.BiFunction<Long, PhantomGoal, PhantomVisibleFarmTravel.ArrivalResult> arrival)
	{
		_coordinator = coordinator; _backend = backend; _goals = goals; _roles = new PhantomPartyRoleMatcher(roles); _clock = clock;
		_preparationRequired = java.util.Objects.requireNonNull(preparationRequired); _arrival = arrival;
		_coordinator.installManagedInvitationPolicy(PhantomBackgroundGoalSpec.GOAL_TYPE, this::evaluateInvitation);
		_coordinator.installManagedInvitationPolicy(PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, this::evaluateInvitation);
	}

	@Override
	public void onPulse()
	{
		if (_coordinator.snapshot().state() != PhantomPartyCoordinator.State.RUNNING) { return; }
		final long now = _clock.getAsLong();
		// An admitted route/store may temporarily close action admission and leave the ready page.
		// Like refreshFarmIntent, durable reads belong to a one-second control-plane window.
		// Continue at most two distinct owned journeys; native consent still reads preparation immediately.
		if (_arrival != null && now >= _nextContinuationPulse)
		{
			_nextContinuationPulse = now + 1_000_000_000L;
			int examined = 0;
			for (var entry : new ArrayList<>(_travelContinuations.entrySet()))
			{
				if (examined++ >= 2) { break; }
				final var member = _backend.currentMember(entry.getKey()).orElse(null);
				// Native Party takeover removes only this scheduler bookmark, never the travel owner's receipt.
				if (member != null && _backend.observe(member).isPresent()) { _travelContinuations.remove(entry.getKey()); continue; }
				final boolean current = _goals.load(entry.getKey()).map(PhantomGoalStore.StoredGoal::goal).filter(entry.getValue()::equals).isPresent();
				// Supply owns its exact yield/drain path; a farm continuation must not cancel it.
				if (current && _preparationRequired.test(entry.getKey(), entry.getValue())) { _travelContinuations.remove(entry.getKey()); _travelContinuations.put(entry.getKey(), entry.getValue()); continue; }
				_travelContinuations.remove(entry.getKey());
				final var arrival = _arrival.apply(entry.getKey(), entry.getValue());
				if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.PENDING) { _travelContinuations.put(entry.getKey(), entry.getValue()); }
			}
		}
		if (now < _nextPulse || _coordinator.snapshot().state() != PhantomPartyCoordinator.State.RUNNING) { return; }
		_nextPulse = now + 5_000_000_000L;
		final var observed = _backend.materializedFarmMembers(_cursor, MAXIMUM_CANDIDATES);
		final var page = observed.members();
		final long previousCursor = _cursor;
		_cursor = observed.lastExaminedProfileId();
		if (page.isEmpty()) { if (_cursor == previousCursor) { _cursor = 0; } return; }
		if (page.size() > MAXIMUM_CANDIDATES) { return; }
		final Map<Long, PhantomGoal> roots = new LinkedHashMap<>();
		for (var member : page)
		{
			final long id = member.ref().profileId();
			final var root = _goals.load(id).map(PhantomGoalStore.StoredGoal::goal).filter(goal -> ordinaryIntent(id, goal)).orElse(null);
			if (root == null) { continue; }
			if (_preparationRequired.test(id, root))
			{
				if (!_coordinator.releaseAutonomousFarmForPreparation(id, root.goalId(), root.revision())) { _coordinator.reconcileAutonomousFarmAbsence(id, root.goalId(), root.revision()); }
			}
			else { roots.put(id, root); }
		}
		for (var member : page)
		{
			final long id = member.ref().profileId(); final var goal = roots.get(id);
			final var claim = _coordinator.claim(id).orElse(null);
			final var state = claim == null ? null : claim.state();
			final boolean abortedFirstFormation = state != null && state.status() == StateStatus.LEADER && state.leader().profileId() == id
				&& OBJECTIVE_NAMESPACE.equals(state.objectiveRef().namespace()) && state.phantomMembers().equals(List.of(state.leader()))
				&& state.realMembers().isEmpty() && state.route() == null && state.operation() != null
				&& state.operation().kind() == OperationKind.JOIN && state.operation().phase() == OperationPhase.ABORTED;
			if (goal != null && claim != null && (claim.state().status() == StateStatus.INCONSISTENT && "restart.claim_conflict".equals(claim.state().lastFailureKey())
				|| abortedFirstFormation))
			{
				if (_coordinator.reconcileAutonomousFarmAbsence(id, goal.goalId(), goal.revision())) { return; }
				roots.remove(id);
			}
		}
		for (var leader : page)
		{
			final long profileId = leader.ref().profileId();
			final PhantomGoal goal = roots.get(profileId);
			if (goal == null || !damage(leader) || prefersSupport(leader) || leader.dead()) { continue; }
			final var claim = _coordinator.claim(profileId).orElse(null);
			if (claim != null && claim.state().status() != StateStatus.SOLO && (!claim.state().leader().equals(leader.ref()) || !OBJECTIVE_NAMESPACE.equals(claim.state().objectiveRef().namespace()))) { continue; }
			if ((claim == null || claim.state().status() == StateStatus.SOLO) && _backend.observe(leader.ref()).isPresent()) { continue; }
			if ((claim == null || claim.state().status() == StateStatus.SOLO) && !_backend.autonomousFarmAvailable(leader.ref())) { continue; }
			final int level = _backend.currentLevel(leader.ref()).orElse(0);
			if (level < 40) { continue; }
			final var candidates = page.stream().filter(member -> roots.containsKey(member.ref().profileId()) && Math.abs(_backend.currentLevel(member.ref()).orElse(0) - level) <= 8)
				.filter(member -> _backend.autonomousFarmAvailable(member.ref()))
				.filter(member -> _coordinator.claim(member.ref().profileId()).map(other -> other.state().status() == StateStatus.SOLO || other.state().leader().equals(leader.ref())).orElse(true))
				.filter(member -> _backend.observe(member.ref()).map(party -> party.leader().equals(leader.ref())).orElse(true)).toList();
			final List<MemberRef> selected;
			if (claim != null && claim.state().status() != StateStatus.SOLO)
			{
				final var planned = plannedProfiles(claim.state().objectiveRef());
				selected = planned.stream().map(_backend::currentMember).flatMap(java.util.Optional::stream).toList();
				if (planned.size() != selected.size() || selected.stream().anyMatch(ref -> _backend.memberSnapshot(ref).filter(member -> recruitmentCompatible(leader, member)).isEmpty())) { _coordinator.releaseAutonomousFarm(profileId); continue; }
			}
			else { selected = selectTeam(_roles, leader, candidates); }
			if (selected.isEmpty()) { if (_coordinator.committed(profileId)) { _coordinator.releaseAutonomousFarm(profileId); } continue; }
			if (selected.stream().anyMatch(ref -> _goals.load(ref.profileId()).map(PhantomGoalStore.StoredGoal::goal).filter(root -> _preparationRequired.test(ref.profileId(), root)).isPresent())) { continue; }
			final var roster = selected.stream().map(_backend::memberSnapshot).flatMap(java.util.Optional::stream).toList();
			if (roster.size() != selected.size() || roster.size() < 2 || !supportPresent(roster.get(1)) || selected.stream().anyMatch(ref -> _goals.load(ref.profileId()).map(PhantomGoalStore.StoredGoal::goal).filter(root -> ordinaryIntent(ref.profileId(), root)).isEmpty())) { _coordinator.releaseAutonomousFarm(profileId); continue; }
			if (leader.casting() || leader.attacking()) { continue; }
			if (_arrival != null)
			{
				boolean arrived = true;
				for (var member : selected)
				{
					final var memberClaim = _coordinator.claim(member.profileId()).orElse(null);
					if (memberClaim != null && memberClaim.state().status() != StateStatus.SOLO || _backend.observe(member).isPresent()) { continue; }
					if (!_travelContinuations.containsKey(member.profileId()) && _travelContinuations.size() >= MAXIMUM_CANDIDATES) { arrived = false; continue; }
					final var root = _goals.load(member.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
					if (!ordinaryIntent(member.profileId(), root) || _preparationRequired.test(member.profileId(), root)) { arrived = false; continue; }
					final var arrival = _arrival.apply(member.profileId(), root);
					if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.PENDING) { _travelContinuations.put(member.profileId(), root); }
					else { _travelContinuations.remove(member.profileId()); }
					arrived &= arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.ARRIVED;
				}
				if (!arrived) { return; }
			}
			final String plan = selected.stream().map(ref -> Long.toString(ref.profileId())).collect(java.util.stream.Collectors.joining(","));
			final var formed = _coordinator.formForGoal(profileId, goal.goalId(), goal.revision(), goal.goalType(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef(OBJECTIVE_NAMESPACE, plan), requirements(roster));
			if (formed != CommandOutcome.ACCEPTED && formed != CommandOutcome.IDEMPOTENT) { continue; }
			final var nativeParty = _backend.observe(leader.ref()).orElse(null);
			if (nativeParty != null && (nativeParty.members().size() > 5 || nativeParty.members().stream().anyMatch(member -> !selected.contains(member)))) { continue; }
			final var missing = selected.stream().filter(ref -> !ref.equals(leader.ref()) && (nativeParty == null || !nativeParty.members().contains(ref)))
				.sorted(Comparator.comparingDouble(ref -> _backend.memberSnapshot(ref).map(member -> distance(leader, member)).orElse(Double.MAX_VALUE))).findFirst();
			if (missing.isPresent()) { if (nativeParty == null || nativeParty.members().size() < 5) { _coordinator.invite(profileId, missing.orElseThrow(), PartyDistributionType.FINDERS_KEEPERS); } }
			else { _coordinator.requestAutonomousFarm(profileId); }
			// One form/invite/pull per shared pulse slice, including pending native consent.
			return;
		}
	}

	public ManagedInvitationDecision evaluateInvitation(ManagedInvitationContext context)
	{
		final var leader = context.leaderClaim();
		final var invitee = context.inviteeClaim();
		if (context.requesterProfileId() <= 0 || context.inviteeProfileId() <= 0 || leader == null || invitee == null || context.inviteeSnapshot() == null || !ordinaryIntent(context.inviteeProfileId(), context.inviteeGoal())
			|| !OBJECTIVE_NAMESPACE.equals(leader.objectiveRef().namespace()) || !leader.groupId().equals(invitee.groupId()) || leader.groupGeneration() != invitee.groupGeneration()
			|| leader.leader().profileId() != context.requesterProfileId() || leader.leader().characterObjectId() != context.invitation().requesterObjectId()
			|| context.inviteeSnapshot().ref().characterObjectId() != context.invitation().inviteeObjectId() || leader.operation() == null || invitee.operation() == null
			|| leader.operation().invitationSequence() != context.invitation().identity().sequence() || invitee.operation().invitationSequence() != context.invitation().identity().sequence()) { return ManagedInvitationDecision.REFUSE; }
		final PhantomGoal root = _goals.load(context.requesterProfileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		if (_preparationRequired.test(context.requesterProfileId(), root) || _preparationRequired.test(context.inviteeProfileId(), context.inviteeGoal())) { return ManagedInvitationDecision.REFUSE; }
		final var currentLeader = _backend.memberSnapshot(leader.leader()).orElse(null);
		final var currentInvitee = _backend.currentMember(context.inviteeProfileId()).orElse(null);
		if (!ordinaryIntent(context.requesterProfileId(), root) || root.goalId() != leader.operation().leaderGoalId() || root.revision() != leader.operation().leaderGoalRevision()
			|| currentLeader == null || !context.inviteeSnapshot().ref().equals(currentInvitee) || !compatible(currentLeader, context.inviteeSnapshot())
			|| Math.abs(_backend.currentLevel(leader.leader()).orElse(0) - _backend.currentLevel(currentInvitee).orElse(1000)) > 8
			|| _backend.observe(currentInvitee).isPresent() || (!damage(context.inviteeSnapshot()) && !support(context.inviteeSnapshot()) && !guardian(context.inviteeSnapshot()))) { return ManagedInvitationDecision.REFUSE; }
		if (!_backend.autonomousFarmConsentAvailable(leader.leader(), currentInvitee, context.invitation().identity())) { return ManagedInvitationDecision.DEFER; }
		return ManagedInvitationDecision.ACCEPT;
	}

	public static List<Long> plannedProfiles(PhantomDomainRef objective)
	{
		if (objective == null || !OBJECTIVE_NAMESPACE.equals(objective.namespace()) || !objective.key().matches("[1-9][0-9]{0,17}(,[1-9][0-9]{0,17}){1,4}")) { return List.of(); }
		final var profiles = java.util.Arrays.stream(objective.key().split(",")).map(Long::parseLong).toList();
		return profiles.stream().distinct().count() == profiles.size() ? profiles : List.of();
	}

	/** Existing historical fallback is ordinary unmet farm motivation, never an arbitrary idle command. */
	static boolean ordinaryIntent(long profileId, PhantomGoal goal)
	{
		if (goal == null || goal.status() != PhantomGoalStatus.ACTIVE) { return false; }
		if (PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType())) { return true; }
		return PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, goal);
	}

	public static List<MemberRef> selectTeam(PhantomPartyRoleMatcher roles, MemberSnapshot leader, List<MemberSnapshot> nearby)
	{
		if (leader == null || nearby == null || nearby.size() > MAXIMUM_CANDIDATES || !compatible(leader, leader) || !damage(leader)) { return List.of(); }
		final var candidates = nearby.stream().filter(member -> !member.ref().equals(leader.ref()) && recruitmentCompatible(leader, member)).sorted(Comparator.comparingDouble((MemberSnapshot member) -> distance(leader, member)).thenComparingLong(member -> member.ref().profileId())).toList();
		final var support = candidates.stream().filter(PhantomAutonomousFarmParty::support).sorted(Comparator.comparing((MemberSnapshot member) -> !compatible(leader, member)).thenComparing(Comparator.comparingInt((MemberSnapshot member) -> score(member, SUPPORT) - score(member, DAMAGE)).reversed()).thenComparingDouble(member -> distance(leader, member)).thenComparingLong(member -> member.ref().profileId())).findFirst().orElse(null);
		if (support == null) { return List.of(); }
		final var team = new ArrayList<MemberSnapshot>();
		team.add(leader); team.add(support);
		candidates.stream().filter(member -> compatible(leader, member) && !member.ref().equals(support.ref()) && damage(member) && !prefersSupport(member) && !guardian(member)).findFirst().ifPresent(team::add);
		if (team.size() < 3) { return List.of(); }
		candidates.stream().filter(member -> compatible(leader, member) && !team.contains(member) && guardian(member)).findFirst().ifPresent(team::add);
		final var requirements = requirements(team);
		if (roles.match(ObjectiveMode.GENERAL_PVE, requirements, team).vacancies().stream().anyMatch(vacancy -> vacancy.status() != VacancyStatus.FILLED)) { return List.of(); }
		return team.stream().map(MemberSnapshot::ref).toList();
	}

	private static List<RoleRequirement> requirements(List<MemberSnapshot> team)
	{
		final var requirements = new ArrayList<RoleRequirement>();
		for (int index = 0; index < team.size(); index++)
		{
			final var member = team.get(index);
			final String role = index == 1 ? supportRole(member) : index > 1 && guardian(member) ? "frontline.guardian" : damageRole(member);
			requirements.add(new RoleRequirement("farm.slot." + index, role, true, 1));
		}
		return List.copyOf(requirements);
	}

	/** Remaining native HP determines the next episode; the original risk deadline is unchanged. */
	public static double projectedFarmDuration(double remainingHp, double damagePerSecond, double regenerationPerSecond)
	{
		if (!Double.isFinite(remainingHp) || remainingHp <= 0 || !Double.isFinite(damagePerSecond) || !Double.isFinite(regenerationPerSecond) || regenerationPerSecond < 0) { return Double.POSITIVE_INFINITY; }
		final double effective = damagePerSecond - regenerationPerSecond;
		if (effective <= 0) { return Double.POSITIVE_INFINITY; }
		final double millis = remainingHp * 1000d / effective;
		return Double.isFinite(millis) && millis <= 60_000 ? millis : Double.POSITIVE_INFINITY;
	}

	/** Distance and observed-threat proof only; native Geo/door/dry checks remain mandatory. */
	public static boolean permitsFarmApproach(MemberSnapshot actor, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination, List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest.PveThreat> threats)
	{
		if (actor == null || destination == null || threats == null || threats.size() > 64 || actor.ref().kind() != MemberKind.PHANTOM || actor.instanceId() != destination.instanceId() || Math.abs((long) actor.z() - destination.z()) > 200) { return false; }
		final double distance = Math.hypot((long) actor.x() - destination.x(), (long) actor.y() - destination.y());
		if (distance > MAXIMUM_TARGET_DISTANCE) { return false; }
		if (distance <= 50) { return true; }
		final var origin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(actor.x(), actor.y(), actor.z(), actor.instanceId());
		return new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(actor.ref().profileId(), origin, destination, 1, 2, MAXIMUM_TARGET_DISTANCE, threats).permitsWalkSegment(origin, destination);
	}

	public static boolean compatible(MemberSnapshot leader, MemberSnapshot member)
	{
		return member.ref().kind() == MemberKind.PHANTOM && member.ref().characterObjectId() > 0 && !member.dead() && member.hpPercent() >= 50
			&& leader.instanceId() == member.instanceId() && Math.abs((long) leader.z() - member.z()) <= 200
			&& Math.hypot((long) leader.x() - member.x(), (long) leader.y() - member.y()) <= 1200;
	}

	/** Recruitment may confirm a remote JOIN; combat and native consent retain their local gates. */
	private static boolean recruitmentCompatible(MemberSnapshot leader, MemberSnapshot member)
	{
		return member.ref().kind() == MemberKind.PHANTOM && member.ref().characterObjectId() > 0 && !member.dead() && member.hpPercent() >= 50 && leader.instanceId() == member.instanceId();
	}

	private static double distance(MemberSnapshot leader, MemberSnapshot member)
	{
		return Math.hypot((long) leader.x() - member.x(), (long) leader.y() - member.y()) + Math.abs((long) leader.z() - member.z());
	}

	public static boolean support(MemberSnapshot member)
	{
		return member.mpPercent() >= 15 && member.capabilities().stream().anyMatch(capability -> SUPPORT.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0 && !"SELF".equals(capability.targetScope()));
	}

	public static boolean supportPresent(MemberSnapshot member)
	{
		return !member.dead() && member.mpPercent() >= 15 && member.capabilities().stream().anyMatch(capability -> SUPPORT.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0 && !"SELF".equals(capability.targetScope()));
	}

	public static boolean damageDealer(MemberSnapshot member) { return damage(member) && !prefersSupport(member); }

	public static boolean guardian(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> "combat.tank".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0);
	}

	public static boolean damage(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> DAMAGE.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow());
	}

	private static int score(MemberSnapshot member, Set<String> keys)
	{
		return member.capabilities().stream().filter(capability -> keys.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow()).mapToInt(capability -> capability.rank()).max().orElse(0);
	}

	private static boolean prefersSupport(MemberSnapshot member)
	{
		return support(member) && score(member, SUPPORT) > score(member, DAMAGE);
	}

	private static String damageRole(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> capability.capabilityKey().startsWith("combat.ranged_") && capability.intrinsic() && capability.learned() && capability.readyNow()) ? "damage.ranged" : "damage.melee";
	}

	private static String supportRole(MemberSnapshot member)
	{
		if (member.capabilities().stream().anyMatch(capability -> "combat.heal".equals(capability.capabilityKey()) && capability.learned() && capability.readyNow())) { return "support.healer"; }
		if (member.capabilities().stream().anyMatch(capability -> "combat.recharge".equals(capability.capabilityKey()) && capability.learned() && capability.readyNow())) { return "support.recharge"; }
		return "support.enhancement";
	}
}
