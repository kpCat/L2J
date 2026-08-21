/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyIdentity;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ParticipationOutcome;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ParticipationReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ReadyReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.EngagementAdvance;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.EngagementContext;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.MechanicAdvance;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.MechanicContext;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RetreatAdvance;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RetreatContext;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptRuntime.RuntimeStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile.EntryKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.EntryRequest;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.EntryResult;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.EntryStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter.TargetEvidence;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry.Registration;

/**
 * Bounded, caller-driven owner of the CP5 encounter lifecycle. It creates no
 * worker, thread or Future; Decision advances it explicitly.
 */
public final class PhantomRaidAttemptService implements PhantomRaidDecision.AttemptPort
{
	public static final int MAXIMUM_LIVE_ATTEMPTS = 64;
	public static final int MAXIMUM_TERMINAL_ATTEMPTS = 256;
	private static final long RETREAT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60);

	private final PhantomGoalStore _goals;
	private final AssemblyPort _assembly;
	private final ReadinessPort _readiness;
	private final PhantomPartyBackend _party;
	private final PhantomRaidAuthority _authority;
	private final PhantomRaidEncounterCatalog _catalog;
	private final PhantomRaidScriptRegistry _scripts;
	private final PhantomRaidAttemptRuntime _runtime;
	private final LongSupplier _wallClock;
	private final LongSupplier _logicalClock;
	private final BooleanSupplier _raidCurseDisabled;
	private final Map<Long, Attempt> _active = new LinkedHashMap<>();
	private final LinkedHashMap<AttemptIdentity, TerminalReceipt> _terminal = new LinkedHashMap<>();
	private final LinkedHashMap<AssemblyIdentity, TerminalOutcome> _outcomes = new LinkedHashMap<>();
	private boolean _stopping;

	public PhantomRaidAttemptService(PhantomGoalStore goals, AssemblyPort assembly, ReadinessPort readiness, PhantomPartyBackend party, PhantomRaidAuthority authority, PhantomRaidEncounterCatalog catalog, PhantomRaidScriptRegistry scripts, PhantomRaidAttemptRuntime runtime, LongSupplier wallClock, LongSupplier logicalClock, BooleanSupplier raidCurseDisabled)
	{
		_goals = Objects.requireNonNull(goals);
		_assembly = Objects.requireNonNull(assembly);
		_readiness = Objects.requireNonNull(readiness);
		_party = Objects.requireNonNull(party);
		_authority = Objects.requireNonNull(authority);
		_catalog = Objects.requireNonNull(catalog);
		_scripts = Objects.requireNonNull(scripts);
		_runtime = Objects.requireNonNull(runtime);
		_wallClock = Objects.requireNonNull(wallClock);
		_logicalClock = Objects.requireNonNull(logicalClock);
		_raidCurseDisabled = Objects.requireNonNull(raidCurseDisabled);
	}

	public synchronized AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision)
	{
		if (_stopping)
		{
			return result(AttemptStatus.CANCELLED, "raid.attempt.stopping", null);
		}
		final long wallNow = _wallClock.getAsLong();
		final GoalValidation goalValidation = validateGoal(leaderProfileId, goalId, goalRevision, wallNow);
		if (goalValidation.goal() == null)
		{
			return result(goalValidation.expired() ? AttemptStatus.EXPIRED : AttemptStatus.ABORTED, goalValidation.reasonKey(), null);
		}
		final AssemblyIdentity assemblyIdentity = new AssemblyIdentity(leaderProfileId, goalId, goalRevision, goalValidation.goal().target().key());
		Attempt attempt = _active.get(leaderProfileId);
		if ((attempt != null) && !attempt._identity.assemblyIdentity().equals(assemblyIdentity))
		{
			terminalize(attempt, AttemptStatus.CANCELLED, "raid.attempt.goal_replaced");
			attempt = null;
		}
		final TerminalOutcome prior = _outcomes.get(assemblyIdentity);
		if (prior != null)
		{
			return result(prior.status(), prior.reasonKey(), prior.receipt());
		}
		if (attempt != null)
		{
			return advance(attempt, wallNow, _logicalClock.getAsLong());
		}
		final ReadyReceipt ready = _assembly.readyReceipt(assemblyIdentity).orElse(null);
		if (ready == null)
		{
			return result(AttemptStatus.WAITING_FOR_READY, "raid.attempt.waiting_ready_receipt", null);
		}
		final AttemptIdentity identity = new AttemptIdentity(assemblyIdentity, ready.structuralHash());
		final Preflight preflight = preflight(goalValidation.goal(), ready, identity, wallNow);
		if (!preflight.valid())
		{
			return reject(identity.assemblyIdentity(), preflight.expired() ? AttemptStatus.EXPIRED : AttemptStatus.ABORTED, preflight.reasonKey());
		}
		if (_active.size() >= MAXIMUM_LIVE_ATTEMPTS)
		{
			return reject(identity.assemblyIdentity(), AttemptStatus.ABORTED, "raid.attempt.capacity");
		}
		attempt = new Attempt(identity, goalValidation.goal(), ready, preflight.profile(), preflight.registration(), preflight.leader(), preflight.force(), preflight.target(), preflight.maximumActorLevel(), mintAuthority(identity, ready, preflight), preflight.encounterEvidence());
		_active.put(leaderProfileId, attempt);
		return advance(attempt, wallNow, _logicalClock.getAsLong());
	}

	public synchronized boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey)
	{
		final Attempt attempt = _active.get(leaderProfileId);
		if ((attempt == null) || (attempt._identity.assemblyIdentity().goalId() != goalId) || (attempt._identity.assemblyIdentity().goalRevision() != goalRevision))
		{
			return false;
		}
		_runtime.cancel(attempt._authorityHash);
		terminalize(attempt, AttemptStatus.CANCELLED, ((reasonKey == null) || reasonKey.isBlank()) ? "raid.attempt.cancelled" : reasonKey);
		return true;
	}

	public synchronized ParticipationStatus participation(long profileId, long goalId, long goalRevision)
	{
		final long now = _wallClock.getAsLong();
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(profileId);
		if (stored.isEmpty() || (stored.get().goal().goalId() != goalId) || (stored.get().goal().revision() != goalRevision) || !PhantomRaidAssemblyService.PARTICIPATE_GOAL_TYPE.equals(stored.get().goal().goalType()) || (stored.get().goal().status() != PhantomGoalStatus.ACTIVE))
		{
			return ParticipationStatus.FAILED;
		}
		final PhantomGoal goal = stored.get().goal();
		if (now >= goal.deadlineEpochMillis())
		{
			return ParticipationStatus.EXPIRED;
		}
		final ParticipationReceipt participation = _assembly.participationReceipt(profileId, goalId, goalRevision);
		if (participation.outcome() == ParticipationOutcome.EXPIRED)
		{
			return ParticipationStatus.EXPIRED;
		}
		if ((participation.outcome() == ParticipationOutcome.WAITING) || (participation.assemblyIdentity() == null))
		{
			return ParticipationStatus.WAITING_FOR_LEADER;
		}
		final Attempt active = _active.get(participation.assemblyIdentity().leaderProfileId());
		if ((active != null) && active._identity.assemblyIdentity().equals(participation.assemblyIdentity()))
		{
			return active._status == AttemptStatus.RETREAT ? ParticipationStatus.RETREATING : ParticipationStatus.ACTIVE;
		}
		final TerminalOutcome outcome = _outcomes.get(participation.assemblyIdentity());
		if (outcome != null)
		{
			return switch (outcome.status())
			{
				case VICTORY -> ParticipationStatus.VICTORY;
				case EXPIRED -> ParticipationStatus.EXPIRED;
				case CANCELLED -> ParticipationStatus.CANCELLED;
				default -> ParticipationStatus.FAILED;
			};
		}
		return ParticipationStatus.WAITING_FOR_LEADER;
	}

	public synchronized boolean ownsAuthority(String attemptAuthorityHash, AttemptIdentity identity, PhantomRaidTargetEvidence target)
	{
		if ((attemptAuthorityHash == null) || (identity == null) || (target == null))
		{
			return false;
		}
		final Attempt active = _active.get(identity.assemblyIdentity().leaderProfileId());
		if ((active != null) && active._identity.equals(identity) && active._authorityHash.equalsIgnoreCase(attemptAuthorityHash) && (active._target != null) && active._target.sameIdentity(target))
		{
			return true;
		}
		final TerminalReceipt terminal = _terminal.get(identity);
		return (terminal != null) && terminal.attemptAuthorityHash().equalsIgnoreCase(attemptAuthorityHash) && (terminal.target() != null) && terminal.target().sameIdentity(target);
	}

	public synchronized Optional<AttemptView> view(long leaderProfileId)
	{
		final Attempt attempt = _active.get(leaderProfileId);
		return attempt == null ? Optional.empty() : Optional.of(attempt.view());
	}

	public synchronized void beginStop()
	{
		if (_stopping)
		{
			return;
		}
		_stopping = true;
		for (Attempt attempt : new ArrayList<>(_active.values()))
		{
			_runtime.cancel(attempt._authorityHash);
			terminalize(attempt, AttemptStatus.CANCELLED, "raid.attempt.stopping");
		}
		_runtime.beginStop();
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_active.size(), _outcomes.size(), _active.values().stream().filter(attempt -> attempt._status == AttemptStatus.RETREAT).count(), _stopping);
	}

	private AdvanceResult advance(Attempt attempt, long wallNow, long logicalNow)
	{
		if (wallNow >= attempt._goal.deadlineEpochMillis())
		{
			beginRetreat(attempt, AttemptStatus.EXPIRED, "raid.attempt.deadline", logicalNow);
		}
		final CurrentForceObservation observed = _party.currentForce(attempt._leader);
		final CurrentForceSnapshot force = (observed.status() == CurrentForceStatus.AVAILABLE) ? observed.snapshot() : null;
		if ((attempt._status != AttemptStatus.RETREAT) && (force == null))
		{
			beginRetreat(attempt, AttemptStatus.ABORTED, "raid.attempt.force_unavailable", logicalNow);
		}
		if ((attempt._status != AttemptStatus.RETREAT) && !attempt._identity.structuralHash().equals(PhantomRaidAssemblyService.structuralHash(force)))
		{
			beginRetreat(attempt, AttemptStatus.ABORTED, "raid.attempt.force_structural_drift", logicalNow);
		}
		if ((attempt._status != AttemptStatus.RETREAT) && (force.members().stream().filter(member -> !member.dead()).count() < attempt._profile.minimumMembers()))
		{
			beginRetreat(attempt, AttemptStatus.WIPED, "raid.attempt.alive_below_minimum", logicalNow);
		}
		if (attempt._status == AttemptStatus.RETREAT)
		{
			return advanceRetreat(attempt, force, logicalNow);
		}

		if (attempt._status == AttemptStatus.ENTRY)
		{
			final EntryResult entered = attempt._registration.adapter().enter(new EntryRequest(attempt._profile.contentId(), attempt._leader, attempt._identity.structuralHash()));
			if (entered.status() != EntryStatus.ENTERED)
			{
				return terminalize(attempt, AttemptStatus.ABORTED, entered.reasonKey());
			}
			attempt._instanceId = entered.instanceId();
			attempt._status = AttemptStatus.MECHANIC;
			attempt._reasonKey = "raid.attempt.mechanic";
		}

		if (attempt._status == AttemptStatus.MECHANIC)
		{
			final MechanicAdvance mechanic = _runtime.advanceMechanic(new MechanicContext(attempt._authorityHash, attempt._profile, attempt._registration, attempt._leader, attempt._logicalDeadline, attempt._token), force);
			if (mechanic.status() == RuntimeStatus.TARGET_REVEALED)
			{
				if (!bindScriptedTarget(attempt, mechanic.revealedTarget()))
				{
					beginRetreat(attempt, AttemptStatus.ABORTED, "raid.attempt.script_target_identity_mismatch", logicalNow);
					return advanceRetreat(attempt, force, logicalNow);
				}
				attempt._status = AttemptStatus.ENGAGING;
				attempt._reasonKey = mechanic.reasonKey();
			}
			else if (runtimeFailure(mechanic.status()))
			{
				beginRetreat(attempt, failureStatus(mechanic.status()), mechanic.reasonKey(), logicalNow);
				return advanceRetreat(attempt, force, logicalNow);
			}
			else
			{
				attempt._reasonKey = mechanic.reasonKey();
				return result(attempt._status, attempt._reasonKey, null);
			}
		}

		if ((attempt._status == AttemptStatus.ENGAGING) || (attempt._status == AttemptStatus.FIGHTING) || (attempt._status == AttemptStatus.LOOT))
		{
			final EngagementAdvance engagement = _runtime.advanceEngagement(new EngagementContext(attempt._authorityHash, attempt._profile, attempt._target, attempt._maximumActorLevel, attempt._logicalDeadline, attempt._token), force);
			attempt._actualDeathObserved |= engagement.actualTargetDeathObserved();
			attempt._nativeLootComplete |= engagement.nativeLootComplete();
			if (runtimeFailure(engagement.status()))
			{
				beginRetreat(attempt, failureStatus(engagement.status()), engagement.reasonKey(), logicalNow);
				return advanceRetreat(attempt, force, logicalNow);
			}
			if (attempt._actualDeathObserved)
			{
				if (canonicalDeathConfirmed(attempt))
				{
					if (attempt._nativeLootComplete)
					{
						return terminalize(attempt, AttemptStatus.VICTORY, "raid.attempt.victory_confirmed");
					}
					attempt._status = AttemptStatus.LOOT;
					attempt._reasonKey = "raid.attempt.native_loot";
					return result(attempt._status, attempt._reasonKey, null);
				}
				attempt._reasonKey = "raid.attempt.death_confirmation_pending";
				return result(attempt._status, attempt._reasonKey, null);
			}
			if (!currentTargetStillExact(attempt))
			{
				beginRetreat(attempt, AttemptStatus.ABORTED, "raid.attempt.target_lost", logicalNow);
				return advanceRetreat(attempt, force, logicalNow);
			}
			attempt._status = AttemptStatus.FIGHTING;
			attempt._reasonKey = engagement.reasonKey();
		}
		return result(attempt._status, attempt._reasonKey, null);
	}

	private AdvanceResult advanceRetreat(Attempt attempt, CurrentForceSnapshot force, long logicalNow)
	{
		if (force == null)
		{
			return terminalize(attempt, attempt._terminalAfterRetreat, attempt._reasonKey + ".force_unavailable");
		}
		final RetreatAdvance retreat = _runtime.advanceRetreat(new RetreatContext(attempt._authorityHash, attempt._profile, attempt._ready, attempt._registration, attempt._instanceId, attempt._retreatDeadline, attempt._token), force);
		if ((retreat.status() == RuntimeStatus.COMPLETE) || (retreat.status() == RuntimeStatus.INVALID) || (logicalNow >= attempt._retreatDeadline))
		{
			return terminalize(attempt, attempt._terminalAfterRetreat, retreat.reasonKey());
		}
		attempt._reasonKey = retreat.reasonKey();
		return result(AttemptStatus.RETREAT, attempt._reasonKey, null);
	}

	private Preflight preflight(PhantomGoal goal, ReadyReceipt ready, AttemptIdentity identity, long wallNow)
	{
		if (wallNow >= goal.deadlineEpochMillis())
		{
			return Preflight.failure("raid.attempt.deadline", true);
		}
		if (!ready.identity().equals(identity.assemblyIdentity()) || !ready.structuralHash().equals(identity.structuralHash()) || !ready.finalReadiness().groupReady())
		{
			return Preflight.failure("raid.attempt.ready_receipt_invalid", false);
		}
		final MemberRef leader = _party.currentMember(identity.assemblyIdentity().leaderProfileId()).orElse(null);
		if (leader == null)
		{
			return Preflight.failure("raid.attempt.leader_unavailable", false);
		}
		final CurrentForceObservation current = _party.currentForce(leader);
		if ((current.status() != CurrentForceStatus.AVAILABLE) || (current.snapshot() == null) || !identity.structuralHash().equals(PhantomRaidAssemblyService.structuralHash(current.snapshot())))
		{
			return Preflight.failure("raid.attempt.force_structural_drift", false);
		}
		final RaidReadiness fresh = _readiness.assess(leader, identity.assemblyIdentity().contentId());
		if (!fresh.groupReady() || ((fresh.targetAvailability() != TargetAvailability.AVAILABLE) && (fresh.targetAvailability() != TargetAvailability.ENTRY_GATED)) || (fresh.content() == null))
		{
			return Preflight.failure("raid.attempt.readiness_not_group_ready", false);
		}
		final PhantomRaidEncounterProfile profile = _catalog.resolve(fresh.content()).orElse(null);
		if ((profile == null) || !profile.contentId().equals(identity.assemblyIdentity().contentId()))
		{
			return Preflight.failure("raid.attempt.encounter_unsupported", false);
		}
		final CurrentForceSnapshot force = current.snapshot();
		if ((force.totalMemberCount() < profile.minimumMembers()) || (force.totalMemberCount() > profile.maximumMembers()))
		{
			return Preflight.failure("raid.attempt.profile_size_rejected", false);
		}
		final boolean enforceMaximum = (profile.maximumMemberLevelWhenCurseEnabled() > 0) && !_raidCurseDisabled.getAsBoolean();
		if ((profile.minimumMemberLevel() > 1) || enforceMaximum)
		{
			for (var member : force.members())
			{
				final OptionalInt level = _party.currentLevel(member.ref());
				if (level.isEmpty() || (level.getAsInt() < profile.minimumMemberLevel()) || (enforceMaximum && (level.getAsInt() > profile.maximumMemberLevelWhenCurseEnabled())))
				{
					return Preflight.failure(enforceMaximum && level.isPresent() && (level.getAsInt() > profile.maximumMemberLevelWhenCurseEnabled()) ? "raid.attempt.queen_curse_level" : "raid.attempt.member_level_rejected", false);
				}
			}
		}
		if (profile.entryKind() == EntryKind.SCRIPTED)
		{
			final Registration registration = _scripts.find(profile.contentId()).filter(value -> (value.adapter().entryNpcId() == profile.entryNpcId()) && (value.adapter().templateId() == profile.templateId())).orElse(null);
			if (registration == null)
			{
				return Preflight.failure("raid.attempt.script_adapter_missing", false);
			}
			final String evidence = "script|" + registration.contentId() + '|' + registration.revision() + '|' + registration.adapter().entryNpcId() + '|' + registration.adapter().templateId();
			return Preflight.success(profile, registration, leader, force, null, evidence, fresh, enforceMaximum ? profile.maximumMemberLevelWhenCurseEnabled() : 1000);
		}
		final PhantomRaidTargetEvidence target = _authority.observeTarget(profile.contentKind(), profile.npcId()).orElse(null);
		if ((target == null) || target.dead() || (target.contentKind() != profile.contentKind()) || (target.npcKind() != profile.npcKind()) || (target.npcId() != profile.npcId()))
		{
			return Preflight.failure("raid.attempt.live_target_missing", false);
		}
		return Preflight.success(profile, null, leader, force, target, "open|" + target, fresh, enforceMaximum ? profile.maximumMemberLevelWhenCurseEnabled() : 1000);
	}

	private GoalValidation validateGoal(long leaderProfileId, long goalId, long goalRevision, long wallNow)
	{
		final Optional<PhantomGoalStore.StoredGoal> stored = _goals.load(leaderProfileId);
		if (stored.isEmpty() || (stored.get().goal().goalId() != goalId) || (stored.get().goal().revision() != goalRevision))
		{
			return new GoalValidation(null, false, "raid.attempt.goal_stale");
		}
		final PhantomGoal goal = stored.get().goal();
		if (!PhantomRaidAssemblyService.PREPARE_GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.target() == null) || !"raid.content".equals(goal.target().namespace()))
		{
			return new GoalValidation(null, false, "raid.attempt.goal_invalid");
		}
		if (wallNow >= goal.deadlineEpochMillis())
		{
			return new GoalValidation(null, true, "raid.attempt.goal_expired");
		}
		return new GoalValidation(goal, false, "raid.attempt.goal_valid");
	}

	private static String mintAuthority(AttemptIdentity identity, ReadyReceipt ready, Preflight preflight)
	{
		final String canonical = "raid.attempt.authority|" + identity.stableKey() + '|' + ready.finalReadiness().content().recommendationHash() + '|' + ready.structuralHash() + '|' + preflight.profile().evidenceHash() + '|' + preflight.encounterEvidence();
		return PhantomPartyModel.sha256(canonical).toUpperCase(java.util.Locale.ROOT);
	}

	private boolean bindScriptedTarget(Attempt attempt, PhantomRaidTargetEvidence target)
	{
		if ((target == null) || target.dead() || (target.contentKind() != attempt._profile.contentKind()) || (target.npcKind() != attempt._profile.npcKind()) || (target.npcId() != attempt._profile.npcId()) || (target.instanceId() != attempt._instanceId))
		{
			return false;
		}
		attempt._target = target;
		return true;
	}

	private boolean canonicalDeathConfirmed(Attempt attempt)
	{
		if ((attempt._target == null) || !attempt._actualDeathObserved)
		{
			return false;
		}
		if (attempt._registration != null)
		{
			return attempt._registration.adapter().confirmsDeath(new TargetEvidence(attempt._target.objectId(), attempt._target.npcId(), attempt._target.instanceId()));
		}
		return _authority.confirmsDeath(attempt._target);
	}

	private boolean currentTargetStillExact(Attempt attempt)
	{
		if (attempt._registration != null)
		{
			return attempt._registration.adapter().revealedTarget(attempt._instanceId).map(target -> (target.objectId() == attempt._target.objectId()) && (target.npcId() == attempt._target.npcId()) && (target.instanceId() == attempt._target.instanceId())).orElse(false);
		}
		return _authority.observeTarget(attempt._profile.contentKind(), attempt._profile.npcId()).filter(current -> current.sameIdentity(attempt._target) && !current.dead()).isPresent();
	}

	private void beginRetreat(Attempt attempt, AttemptStatus terminalAfterRetreat, String reasonKey, long logicalNow)
	{
		if (attempt._status == AttemptStatus.RETREAT)
		{
			return;
		}
		_runtime.cancel(attempt._authorityHash);
		attempt._status = AttemptStatus.RETREAT;
		attempt._terminalAfterRetreat = terminalAfterRetreat;
		attempt._reasonKey = reasonKey;
		attempt._retreatDeadline = saturatingAdd(logicalNow, RETREAT_TIMEOUT_NANOS);
	}

	private AdvanceResult terminalize(Attempt attempt, AttemptStatus status, String reasonKey)
	{
		if (!status.terminal())
		{
			throw new IllegalArgumentException("Raid attempt terminalization requires a terminal status.");
		}
		_active.remove(attempt._identity.assemblyIdentity().leaderProfileId(), attempt);
		if (status == AttemptStatus.VICTORY)
		{
			_runtime.complete(attempt._authorityHash);
		}
		else
		{
			_runtime.cancel(attempt._authorityHash);
		}
		final TerminalReceipt receipt = new TerminalReceipt(attempt._identity, status, reasonKey, attempt._authorityHash, attempt._profile, attempt._target, attempt._actualDeathObserved, attempt._nativeLootComplete, _wallClock.getAsLong());
		_terminal.put(attempt._identity, receipt);
		rememberOutcome(attempt._identity.assemblyIdentity(), status, reasonKey, receipt);
		return result(status, reasonKey, receipt);
	}

	private AdvanceResult reject(AssemblyIdentity identity, AttemptStatus status, String reasonKey)
	{
		rememberOutcome(identity, status, reasonKey, null);
		return result(status, reasonKey, null);
	}

	private void rememberOutcome(AssemblyIdentity identity, AttemptStatus status, String reasonKey, TerminalReceipt receipt)
	{
		final TerminalOutcome replaced = _outcomes.put(identity, new TerminalOutcome(status, reasonKey, receipt));
		if ((replaced != null) && (replaced.receipt() != null))
		{
			_terminal.remove(replaced.receipt().identity());
		}
		while (_outcomes.size() > MAXIMUM_TERMINAL_ATTEMPTS)
		{
			final TerminalOutcome evicted = _outcomes.remove(_outcomes.keySet().iterator().next());
			if ((evicted != null) && (evicted.receipt() != null))
			{
				_terminal.remove(evicted.receipt().identity());
			}
		}
	}

	private static boolean runtimeFailure(RuntimeStatus status)
	{
		return (status == RuntimeStatus.TARGET_LOST) || (status == RuntimeStatus.NO_CONTROLLABLE_OFFENSE) || (status == RuntimeStatus.PROVIDER_UNAVAILABLE) || (status == RuntimeStatus.WIPED) || (status == RuntimeStatus.INVALID);
	}

	private static AttemptStatus failureStatus(RuntimeStatus status)
	{
		return status == RuntimeStatus.WIPED ? AttemptStatus.WIPED : AttemptStatus.ABORTED;
	}

	private static long saturatingAdd(long value, long increment)
	{
		try
		{
			return Math.addExact(value, increment);
		}
		catch (ArithmeticException ignored)
		{
			return Long.MAX_VALUE;
		}
	}

	private static AdvanceResult result(AttemptStatus status, String reasonKey, TerminalReceipt receipt)
	{
		return new AdvanceResult(status, reasonKey, receipt);
	}

	public interface AssemblyPort
	{
		Optional<ReadyReceipt> readyReceipt(AssemblyIdentity identity);

		ParticipationReceipt participationReceipt(long profileId, long goalId, long goalRevision);
	}

	@FunctionalInterface
	public interface ReadinessPort
	{
		RaidReadiness assess(MemberRef actor, String contentId);
	}

	public enum AttemptStatus
	{
		WAITING_FOR_READY,
		ENTRY,
		MECHANIC,
		ENGAGING,
		FIGHTING,
		LOOT,
		RETREAT,
		VICTORY,
		ABORTED,
		WIPED,
		EXPIRED,
		CANCELLED;

		public boolean terminal()
		{
			return (this == VICTORY) || (this == ABORTED) || (this == WIPED) || (this == EXPIRED) || (this == CANCELLED);
		}
	}

	public enum ParticipationStatus
	{
		WAITING_FOR_LEADER,
		ACTIVE,
		RETREATING,
		VICTORY,
		FAILED,
		EXPIRED,
		CANCELLED
	}

	public record AttemptIdentity(AssemblyIdentity assemblyIdentity, String structuralHash)
	{
		public AttemptIdentity
		{
			Objects.requireNonNull(assemblyIdentity);
			if ((structuralHash == null) || !structuralHash.matches("[0-9A-Fa-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid exact raid attempt identity.");
			}
			structuralHash = structuralHash.toUpperCase(java.util.Locale.ROOT);
		}

		public String stableKey()
		{
			return assemblyIdentity.stableKey() + '|' + structuralHash;
		}
	}

	public record AdvanceResult(AttemptStatus status, String reasonKey, TerminalReceipt terminalReceipt)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(status);
			if ((reasonKey == null) || reasonKey.isBlank() || (status.terminal() != (terminalReceipt != null) && (terminalReceipt != null)))
			{
				throw new IllegalArgumentException("Invalid raid attempt advance result.");
			}
		}
	}

	public record TerminalReceipt(AttemptIdentity identity, AttemptStatus status, String reasonKey, String attemptAuthorityHash, PhantomRaidEncounterProfile profile, PhantomRaidTargetEvidence target, boolean actualTargetDeathObserved, boolean nativeLootComplete, long completedAtMillis)
	{
		public TerminalReceipt
		{
			Objects.requireNonNull(identity);
			Objects.requireNonNull(status);
			Objects.requireNonNull(profile);
			if (!status.terminal() || (reasonKey == null) || reasonKey.isBlank() || (attemptAuthorityHash == null) || !attemptAuthorityHash.matches("[0-9A-Fa-f]{64}") || (completedAtMillis < 0) || ((status == AttemptStatus.VICTORY) && (!actualTargetDeathObserved || !nativeLootComplete || (target == null))))
			{
				throw new IllegalArgumentException("Invalid terminal raid attempt receipt.");
			}
			attemptAuthorityHash = attemptAuthorityHash.toUpperCase(java.util.Locale.ROOT);
		}
	}

	public record AttemptView(AttemptIdentity identity, AttemptStatus status, String reasonKey, String attemptAuthorityHash, PhantomRaidEncounterProfile profile, PhantomRaidTargetEvidence target, boolean actualTargetDeathObserved, boolean nativeLootComplete)
	{
	}

	public record Snapshot(int liveAttempts, int terminalAttempts, long retreatingAttempts, boolean stopping)
	{
	}

	private record TerminalOutcome(AttemptStatus status, String reasonKey, TerminalReceipt receipt)
	{
		private TerminalOutcome
		{
			Objects.requireNonNull(status);
			if (!status.terminal() || (reasonKey == null) || reasonKey.isBlank() || ((receipt != null) && ((receipt.status() != status) || !receipt.reasonKey().equals(reasonKey))))
			{
				throw new IllegalArgumentException("Invalid raid attempt terminal outcome.");
			}
		}
	}

	private record GoalValidation(PhantomGoal goal, boolean expired, String reasonKey)
	{
	}

	private record Preflight(boolean valid, boolean expired, String reasonKey, PhantomRaidEncounterProfile profile, Registration registration, MemberRef leader, CurrentForceSnapshot force, PhantomRaidTargetEvidence target, String encounterEvidence, RaidReadiness readiness, int maximumActorLevel)
	{
		private static Preflight failure(String reason, boolean expired)
		{
			return new Preflight(false, expired, reason, null, null, null, null, null, null, null, 0);
		}

		private static Preflight success(PhantomRaidEncounterProfile profile, Registration registration, MemberRef leader, CurrentForceSnapshot force, PhantomRaidTargetEvidence target, String encounterEvidence, RaidReadiness readiness, int maximumActorLevel)
		{
			return new Preflight(true, false, "raid.attempt.preflight_ready", profile, registration, leader, force, target, encounterEvidence, readiness, maximumActorLevel);
		}
	}

	private final class Attempt
	{
		private final AttemptIdentity _identity;
		private final PhantomGoal _goal;
		private final ReadyReceipt _ready;
		private final PhantomRaidEncounterProfile _profile;
		private final Registration _registration;
		private final MemberRef _leader;
		private final String _authorityHash;
		private final String _encounterEvidence;
		private final int _maximumActorLevel;
		private final long _logicalDeadline;
		private final org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken _token;
		private AttemptStatus _status;
		private AttemptStatus _terminalAfterRetreat = AttemptStatus.ABORTED;
		private String _reasonKey;
		private PhantomRaidTargetEvidence _target;
		private int _instanceId;
		private long _retreatDeadline;
		private boolean _cancelled;
		private boolean _actualDeathObserved;
		private boolean _nativeLootComplete;

		private Attempt(AttemptIdentity identity, PhantomGoal goal, ReadyReceipt ready, PhantomRaidEncounterProfile profile, Registration registration, MemberRef leader, CurrentForceSnapshot force, PhantomRaidTargetEvidence target, int maximumActorLevel, String authorityHash, String encounterEvidence)
		{
			_identity = identity;
			_goal = goal;
			_ready = ready;
			_profile = profile;
			_registration = registration;
			_leader = leader;
			_target = target;
			_instanceId = target == null ? 0 : target.instanceId();
			_authorityHash = authorityHash;
			_encounterEvidence = encounterEvidence;
			_maximumActorLevel = maximumActorLevel;
			_logicalDeadline = saturatingAdd(_logicalClock.getAsLong(), TimeUnit.MILLISECONDS.toNanos(Math.max(1, goal.deadlineEpochMillis() - _wallClock.getAsLong())));
			_token = () -> _cancelled || _stopping;
			_status = profile.entryGated() ? AttemptStatus.ENTRY : AttemptStatus.ENGAGING;
			_reasonKey = profile.entryGated() ? "raid.attempt.entry" : "raid.attempt.engaging";
		}

		private AttemptView view()
		{
			return new AttemptView(_identity, _status, _reasonKey, _authorityHash, _profile, _target, _actualDeathObserved, _nativeLootComplete);
		}
	}
}
