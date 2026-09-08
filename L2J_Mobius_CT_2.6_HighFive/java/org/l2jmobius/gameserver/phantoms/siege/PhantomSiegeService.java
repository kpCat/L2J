/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSiegeCombatRequest;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeCatalog.Strategy;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.CastleSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.NativeSide;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Participant;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.RegistrationOutcome;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Role;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Stage;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Target;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.TargetKind;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeMovementCoordinator.Status;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Caller-driven bounded Giran siege lifecycle. Native Siege remains truth. */
public final class PhantomSiegeService
{
	public static final String PREPARE_GOAL_TYPE = "siege.prepare";
	public static final String PARTICIPATE_GOAL_TYPE = "siege.participate";
	public static final String TARGET_NAMESPACE = "siege.castle";
	private static final int MAXIMUM_OPERATIONS = 128;
	private static final String SIGNAL_SOURCE = "siege.giran";
	private static final Set<String> SUPPORT_CAPABILITIES = Set.of("combat.heal", "combat.recharge", "combat.resurrection", "combat.buff", "combat.song", "combat.dance");
	private static final Set<String> RANGED_CAPABILITIES = Set.of("combat.ranged_physical_damage", "combat.ranged_magic_damage");
	private final Object _monitor = new Object();
	private final PhantomGoalStore _goals;
	private final PhantomSiegeAuthority _authority;
	private final Strategy _strategy;
	private final PhantomSiegeMovementPort _movement;
	private final PhantomCombatService _combat;
	private final PhantomRelevanceSignalPort _signals;
	private final LongSupplier _wallClock;
	private final Map<Long, Operation> _operations = new HashMap<>();
	private boolean _stopping;

	public PhantomSiegeService(PhantomGoalStore goals, PhantomSiegeAuthority authority, PhantomSiegeCatalog catalog, PhantomSiegeMovementPort movement, PhantomCombatService combat, PhantomRelevanceSignalPort signals)
	{
		this(goals, authority, catalog, movement, combat, signals, System::currentTimeMillis);
	}

	public PhantomSiegeService(PhantomGoalStore goals, PhantomSiegeAuthority authority, PhantomSiegeCatalog catalog, PhantomSiegeMovementPort movement, PhantomCombatService combat, PhantomRelevanceSignalPort signals, LongSupplier wallClock)
	{
		_goals = Objects.requireNonNull(goals);
		_authority = Objects.requireNonNull(authority);
		_strategy = Objects.requireNonNull(catalog).strategy();
		_movement = Objects.requireNonNull(movement);
		_combat = Objects.requireNonNull(combat);
		_signals = Objects.requireNonNull(signals);
		_wallClock = Objects.requireNonNull(wallClock);
	}

	public AdvanceResult advance(long profileId, long goalId, long goalRevision)
	{
		if (stopping())
		{
			return result(Stage.ABANDONED, "siege.service.stopping", List.of(), zeroHash());
		}
		final PhantomGoal goal = validGoal(profileId, goalId, goalRevision);
		if (goal == null)
		{
			return terminal(profileId, Stage.ABANDONED, "siege.goal.stale", List.of(), zeroHash());
		}
		final long now = Math.max(0, _wallClock.getAsLong());
		if ((goal.deadlineEpochMillis() > 0) && (goal.deadlineEpochMillis() <= now))
		{
			return terminal(profileId, Stage.EXPIRED, "siege.goal.expired", List.of(), zeroHash());
		}
		CastleSnapshot castle = _authority.observeCastle(_strategy.castleId()).orElse(null);
		ActorSnapshot actor = _authority.observeActor(profileId, _strategy.castleId()).orElse(null);
		if ((castle == null) || (actor == null))
		{
			return terminal(profileId, Stage.ABANDONED, "siege.native.authority_missing", List.of(), zeroHash());
		}
		List<Participant> participants = assignRoles(_authority.managedClanMembers(actor.clanId(), 32), actor.leaderObjectId(), _strategy.maximumActiveParticipants());
		if (participants.stream().noneMatch(participant -> participant.member().profileId() == profileId))
		{
			return terminal(profileId, Stage.ABANDONED, "siege.force.actor_missing", participants, actor.authorityHash());
		}
		final boolean prepare = PREPARE_GOAL_TYPE.equals(goal.goalType());
		boolean attacker = castle.attacker(actor.clanId());
		final boolean defender = castle.defender(actor.clanId());
		if (!attacker && !defender)
		{
			if (!prepare)
			{
				return terminal(profileId, Stage.ABANDONED, "siege.native.side_absent", participants, actor.authorityHash());
			}
			final var registration = _authority.registerAttacker(profileId, _strategy.castleId());
			castle = registration.castle();
			actor = registration.actor();
			if ((registration.outcome() != RegistrationOutcome.REGISTERED) && (registration.outcome() != RegistrationOutcome.ALREADY_REGISTERED))
			{
				final Stage stage = registration.outcome() == RegistrationOutcome.CLOSED ? Stage.EXPIRED : Stage.ABANDONED;
				return terminal(profileId, stage, registration.reasonKey(), participants, actor == null ? zeroHash() : actor.authorityHash());
			}
			attacker = (castle != null) && (actor != null) && castle.attacker(actor.clanId());
			if (!attacker)
			{
				return terminal(profileId, Stage.ABANDONED, "siege.registration.reobserve_failed", participants, zeroHash());
			}
		}

		final String authorityHash = authorityHash(goal, actor);
		final Operation operation = operation(profileId, goalId, goalRevision, authorityHash, participants);
		if (operation == null)
		{
			return terminal(profileId, Stage.ABANDONED, "siege.native.authority_changed", participants, authorityHash);
		}
		publishRelevance(operation, now);
		if (!castle.inProgress())
		{
			if (operation._nativeSiegeObserved)
			{
				return terminal(profileId, Stage.COMPLETE, "siege.native.finished", participants, authorityHash);
			}
			final String anchor = attacker ? _strategy.attackerStagingAnchor() : _strategy.defenderStagingAnchor();
			boolean allArrived = true;
			for (Participant participant : participants.stream().filter(value -> value.role() != Role.RESERVE).toList())
			{
				if (!participant.member().materialized())
				{
					allArrived = false;
					continue;
				}
				final var route = _movement.advance(participant.member().profileId(), anchor, authorityHash, TimeUnit.SECONDS.toMillis(_strategy.routeTimeoutSeconds()));
				if ((route.status() == Status.FAILED) && (route.reasonKey().equals("siege.route.no_path") || route.reasonKey().equals("siege.route.movement_rejected")))
				{
					return terminal(profileId, Stage.ABANDONED, route.reasonKey(), participants, authorityHash);
				}
				allArrived &= route.status() == Status.ARRIVED;
			}
			operation._stage = allArrived ? Stage.WAITING_START : Stage.GATHERING;
			return result(operation._stage, allArrived ? "siege.native.waiting_start" : "siege.route.gathering", participants, authorityHash);
		}

		if ((actor.side() == NativeSide.NONE) || (attacker && (actor.side() != NativeSide.ATTACKER)) || (defender && (actor.side() != NativeSide.DEFENDER)))
		{
			return terminal(profileId, Stage.ABANDONED, "siege.native.side_stale", participants, authorityHash);
		}
		operation._nativeSiegeObserved = true;
		cancelRoutes(operation);
		if (actor.dead())
		{
			_combat.cancel(profileId);
			_combat.consumeTerminal(profileId);
			_combat.respawnTown(new PhantomRespawnRequest(profileId, operation._cancelled::get));
			operation._stage = Stage.RETREATING;
			return result(Stage.RETREATING, "siege.recovery.native_pending", participants, authorityHash);
		}
		if (actor.hpPercent() <= _strategy.retreatHpPercent())
		{
			_combat.cancel(profileId);
			_combat.consumeTerminal(profileId);
			final var retreat = _movement.advance(profileId, _strategy.retreatAnchor(), authorityHash, TimeUnit.SECONDS.toMillis(_strategy.routeTimeoutSeconds()));
			operation._stage = Stage.RETREATING;
			if (retreat.status() == Status.ARRIVED)
			{
				return terminal(profileId, Stage.ABANDONED, "siege.retreat.arrived", participants, authorityHash);
			}
			if ((retreat.status() == Status.FAILED) || (retreat.status() == Status.STALE))
			{
				return terminal(profileId, Stage.ABANDONED, retreat.reasonKey(), participants, authorityHash);
			}
			return result(Stage.RETREATING, retreat.reasonKey(), participants, authorityHash);
		}

		final Target target = selectTarget(profileId, actor.side());
		operation._stage = actor.side() == NativeSide.ATTACKER ? Stage.ATTACKING : Stage.DEFENDING;
		if (target == null)
		{
			clearCombat(profileId, null);
			return result(operation._stage, "siege.combat.target_pending", participants, authorityHash);
		}
		final PhantomCombatMode mode = combatMode(participants, profileId, target.kind());
		if (mode == null)
		{
			return result(operation._stage, "siege.combat.loadout_unavailable", participants, authorityHash);
		}
		clearCombat(profileId, target);
		if (!_combat.matchesSiegeSession(profileId, target.objectId(), _strategy.castleId(), target.authorityHash()))
		{
			_combat.startSiegeSession(new PhantomSiegeCombatRequest(profileId, target.objectId(), target.castleId(), target.kind(), target.nativeId(), target.authorityHash(), mode, true, TimeUnit.SECONDS.toMillis(_strategy.combatTimeoutSeconds()), operation._cancelled::get));
		}
		return result(operation._stage, actor.side() == NativeSide.ATTACKER ? "siege.combat.attacking" : "siege.combat.defending", participants, authorityHash);
	}

	public boolean cancel(long profileId, long goalId, long goalRevision, String reasonKey)
	{
		final Operation operation;
		synchronized (_monitor)
		{
			operation = _operations.get(profileId);
			if ((operation == null) || (operation._goalId != goalId) || (operation._goalRevision != goalRevision))
			{
				return false;
			}
		}
		cleanup(operation);
		return true;
	}

	public void beginStop()
	{
		final List<Operation> operations;
		synchronized (_monitor)
		{
			_stopping = true;
			operations = new ArrayList<>(_operations.values());
		}
		operations.forEach(this::cleanup);
		_movement.finishStop();
	}

	public boolean finishStop()
	{
		return stopping() && (_movement.activeCount() == 0) && activeCount() == 0;
	}

	public int activeCount()
	{
		synchronized (_monitor)
		{
			return _operations.size();
		}
	}

	public static List<Participant> assignRoles(List<MemberSnapshot> members, int leaderObjectId, int maximumActive)
	{
		if ((members == null) || (leaderObjectId <= 0) || (maximumActive < 1) || (maximumActive > 16))
		{
			return List.of();
		}
		final List<Participant> classified = members.stream().distinct().map(member -> new Participant(member, classify(member, leaderObjectId))).sorted(Comparator.comparingInt((Participant participant) -> roleOrder(participant.role())).thenComparingInt(participant -> participant.member().objectId())).toList();
		final List<Participant> result = new ArrayList<>();
		for (int index = 0; index < classified.size(); index++)
		{
			final Participant participant = classified.get(index);
			result.add(index < maximumActive ? participant : new Participant(participant.member(), Role.RESERVE));
		}
		return List.copyOf(result);
	}

	private Target selectTarget(long profileId, NativeSide side)
	{
		final List<Target> players = _authority.opposingPlayers(profileId, _strategy.castleId(), _strategy.maximumTargets(), _strategy.combatDistance());
		if (!players.isEmpty())
		{
			return players.stream().min(Comparator.comparingDouble(Target::distance).thenComparingInt(Target::objectId)).orElse(null);
		}
		if (side == NativeSide.ATTACKER)
		{
			return _authority.attackableDoors(profileId, _strategy.castleId(), _strategy.maximumTargets(), _strategy.combatDistance()).stream().min(Comparator.comparingDouble(Target::distance).thenComparingInt(Target::objectId)).orElse(null);
		}
		return null;
	}

	private PhantomGoal validGoal(long profileId, long goalId, long goalRevision)
	{
		final PhantomGoal goal = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		return (goal != null) && (goal.goalId() == goalId) && (goal.revision() == goalRevision) && (goal.status() == PhantomGoalStatus.ACTIVE) && (PREPARE_GOAL_TYPE.equals(goal.goalType()) || PARTICIPATE_GOAL_TYPE.equals(goal.goalType())) && (goal.target() != null) && TARGET_NAMESPACE.equals(goal.target().namespace()) && Integer.toString(_strategy.castleId()).equals(goal.target().key()) ? goal : null;
	}

	private Operation operation(long profileId, long goalId, long goalRevision, String authorityHash, List<Participant> participants)
	{
		synchronized (_monitor)
		{
			final Operation existing = _operations.get(profileId);
			if (existing != null)
			{
				if ((existing._goalId != goalId) || (existing._goalRevision != goalRevision) || !existing._authorityHash.equals(authorityHash))
				{
					return null;
				}
				return existing;
			}
			if (_operations.size() >= MAXIMUM_OPERATIONS)
			{
				return null;
			}
			final Operation created = new Operation(profileId, goalId, goalRevision, authorityHash, participants);
			_operations.put(profileId, created);
			return created;
		}
	}

	private void publishRelevance(Operation operation, long now)
	{
		operation._signalSequence = Math.max(Math.max(0, now), operation._signalSequence + 1);
		final long ttl = TimeUnit.SECONDS.toMillis(_strategy.relevanceTtlSeconds());
		for (Participant participant : operation._participants)
		{
			if (participant.role() != Role.RESERVE)
			{
				_signals.submit(participant.member().profileId(), new PhantomRelevanceSignal(SIGNAL_SOURCE, operation._signalSequence, PhantomActivityState.ACTIVE, ttl));
				operation._signaledProfiles.add(participant.member().profileId());
			}
		}
	}

	private AdvanceResult terminal(long profileId, Stage stage, String reasonKey, List<Participant> participants, String authorityHash)
	{
		final Operation operation;
		synchronized (_monitor)
		{
			operation = _operations.get(profileId);
		}
		if (operation != null)
		{
			cleanup(operation);
		}
		return result(stage, reasonKey, participants, authorityHash);
	}

	private void cleanup(Operation operation)
	{
		synchronized (_monitor)
		{
			if (!_operations.remove(operation._profileId, operation))
			{
				return;
			}
			operation._cancelled.set(true);
		}
		_combat.cancel(operation._profileId);
		_combat.consumeTerminal(operation._profileId);
		cancelRoutes(operation);
		final long sequence = Math.max(Math.max(0, _wallClock.getAsLong()), operation._signalSequence + 1);
		for (long profileId : operation._signaledProfiles)
		{
			_signals.withdraw(profileId, SIGNAL_SOURCE, sequence);
		}
	}

	private void cancelRoutes(Operation operation)
	{
		for (Participant participant : operation._participants)
		{
			_movement.cancel(participant.member().profileId(), operation._authorityHash);
		}
	}

	private void clearCombat(long profileId, Target selected)
	{
		final PhantomCombatSessionSnapshot current = _combat.find(profileId).orElse(null);
		if ((current != null) && ((selected == null) || (current.targetObjectId() != selected.objectId()) || current.result().terminal()))
		{
			_combat.cancel(profileId);
			_combat.consumeTerminal(profileId);
		}
	}

	private boolean stopping()
	{
		synchronized (_monitor)
		{
			return _stopping;
		}
	}

	private static Role classify(MemberSnapshot member, int leaderObjectId)
	{
		if (member.objectId() == leaderObjectId)
		{
			return Role.COMMANDER;
		}
		if (member.capabilities().stream().anyMatch(SUPPORT_CAPABILITIES::contains))
		{
			return Role.SUPPORT;
		}
		if (member.capabilities().stream().anyMatch(RANGED_CAPABILITIES::contains))
		{
			return Role.RANGED;
		}
		return Role.FRONTLINE;
	}

	private static int roleOrder(Role role)
	{
		return switch (role)
		{
			case COMMANDER -> 0;
			case SUPPORT -> 1;
			case RANGED -> 2;
			case FRONTLINE -> 3;
			case RESERVE -> 4;
		};
	}

	private static PhantomCombatMode combatMode(List<Participant> participants, long profileId, TargetKind targetKind)
	{
		final MemberSnapshot member = participants.stream().map(Participant::member).filter(value -> value.profileId() == profileId).findFirst().orElse(null);
		if (member == null)
		{
			return null;
		}
		if (targetKind == TargetKind.DOOR)
		{
			return member.capabilities().contains("combat.melee_damage") ? PhantomCombatMode.MELEE_PHYSICAL : null;
		}
		if (member.capabilities().contains("combat.ranged_magic_damage"))
		{
			return PhantomCombatMode.RANGED_MAGIC;
		}
		if (member.capabilities().contains("combat.ranged_physical_damage"))
		{
			return PhantomCombatMode.RANGED_PHYSICAL;
		}
		return member.capabilities().contains("combat.melee_damage") ? PhantomCombatMode.MELEE_PHYSICAL : null;
	}

	private String authorityHash(PhantomGoal goal, ActorSnapshot actor)
	{
		return PhantomSiegeModel.sha256(goal.goalId() + "|" + goal.revision() + "|" + _strategy.evidenceHash() + "|" + _strategy.castleId() + "|" + actor.clanId() + "|" + actor.leaderObjectId());
	}

	private static AdvanceResult result(Stage stage, String reasonKey, List<Participant> participants, String authorityHash)
	{
		return new AdvanceResult(stage, reasonKey, participants, authorityHash);
	}

	private static String zeroHash()
	{
		return "0".repeat(64);
	}

	private static final class Operation
	{
		private final long _profileId;
		private final long _goalId;
		private final long _goalRevision;
		private final String _authorityHash;
		private final List<Participant> _participants;
		private final Set<Long> _signaledProfiles = new HashSet<>();
		private final AtomicBoolean _cancelled = new AtomicBoolean();
		private Stage _stage = Stage.OBSERVING;
		private long _signalSequence;
		private boolean _nativeSiegeObserved;

		private Operation(long profileId, long goalId, long goalRevision, String authorityHash, List<Participant> participants)
		{
			_profileId = profileId;
			_goalId = goalId;
			_goalRevision = goalRevision;
			_authorityHash = authorityHash;
			_participants = participants;
		}
	}
}
