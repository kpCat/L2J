/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.PlannedSnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService.OperationStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Synchronous bounded owner for causal historical Background catch-up. */
public final class PhantomHistoricalBackgroundService implements PhantomMaterializationLifecyclePort
{
	public static final int MAXIMUM_INTERVALS_PER_CALL = 64;
	public static final int MAXIMUM_SIMULATED_MINUTES_PER_CALL = 1440;
	private static final int IDLE_RECHECK_MINUTES = 60;
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomBackgroundCatchupStore _store;
	private final PhantomHistoricalBackgroundPlanner _planner;
	private final PhantomBackgroundService _background;
	private final PhantomMaterializationService _materialization;
	private volatile java.util.function.BiFunction<Long, java.util.function.Supplier<Result>, Result> _authorityRefreshBoundary = (_, work) -> work.get();
	private final ConcurrentHashMap<Long, Admission> _admissions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, ActiveForegroundHandoff> _foregroundHandoffs = new ConcurrentHashMap<>();
	private volatile boolean _foregroundDecisionsRevoked;
	private final ConcurrentHashMap<Long, RecoveryClaim> _recoveryClaims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, NativeRecoveryRefresh> _nativeRecoveryRefreshes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisibleFailures> _visibleFailures = new ConcurrentHashMap<>();
	private final LongSupplier _visibleClock;
	private final ConcurrentHashMap<Long, VisibleEpisode> _visibleEpisodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, String> _visiblePreparationStages = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisiblePublication> _visiblePublications = new ConcurrentHashMap<>();
	private volatile PhantomVisibleFarmTravel _visibleTravel;
	private volatile PhantomVisibleAutoPlay _visibleAutoPlay;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _visibleIntent = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _acceptedIntent = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _partyNativeLifetime = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _partyCommittedDecision = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _acceptedPartyRoot = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _rendezvousTravelDecision = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _supportRendezvousPreparation = (_, _) -> false;
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _supportRendezvousTravel = (_, _) -> false;

	/** Party owns current role/consent; these predicates permit visible shared travel only. */
	public void bindSupportRendezvous(java.util.function.BiPredicate<Long, PhantomGoal> preparation, java.util.function.BiPredicate<Long, PhantomGoal> travel)
	{
		_supportRendezvousPreparation = Objects.requireNonNull(preparation);
		_supportRendezvousTravel = Objects.requireNonNull(travel);
	}

	/** A child intent retains the exact farm root while its existing native owners travel. */
	public void bindVisibleIntent(java.util.function.BiPredicate<Long, PhantomGoal> intent)
	{
		_visibleIntent = Objects.requireNonNull(intent);
	}

	/** A finite accepted child retains its exact root; HOLD still blocks all native actions separately. */
	public void bindAcceptedIntent(java.util.function.BiPredicate<Long, PhantomGoal> intent)
	{
		_acceptedIntent = Objects.requireNonNull(intent);
	}

	/** Party owns lifetime retention only; it grants no ordinary intent or action permission. */
	public void bindPartyNativeLifetime(java.util.function.BiPredicate<Long, PhantomGoal> retained, java.util.function.BiPredicate<Long, PhantomGoal> committedDecision)
	{
		bindPartyNativeLifetime(retained, committedDecision, (_, _) -> false);
	}

	public void bindPartyNativeLifetime(java.util.function.BiPredicate<Long, PhantomGoal> retained, java.util.function.BiPredicate<Long, PhantomGoal> committedDecision,
		java.util.function.BiPredicate<Long, PhantomGoal> acceptedRoot)
	{
		bindPartyNativeLifetime(retained, committedDecision, acceptedRoot, (_, _) -> false);
	}

	public void bindPartyNativeLifetime(java.util.function.BiPredicate<Long, PhantomGoal> retained, java.util.function.BiPredicate<Long, PhantomGoal> committedDecision,
		java.util.function.BiPredicate<Long, PhantomGoal> acceptedRoot, java.util.function.BiPredicate<Long, PhantomGoal> adoptedTravelDecision)
	{
		_partyNativeLifetime = Objects.requireNonNull(retained);
		_partyCommittedDecision = Objects.requireNonNull(committedDecision);
		_acceptedPartyRoot = Objects.requireNonNull(acceptedRoot);
		_rendezvousTravelDecision = Objects.requireNonNull(adoptedTravelDecision);
	}
	private java.util.function.Function<Long, Optional<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty>> _logicalGroups = _ -> Optional.empty();
	private java.util.function.LongConsumer _refreshLogicalGroups = _ -> { };
	public void bindLogicalGroups(java.util.function.Function<Long, Optional<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty>> groups)
	{ _logicalGroups = Objects.requireNonNull(groups); }
	public void bindLogicalGroups(java.util.function.Function<Long, Optional<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty>> groups, java.util.function.LongConsumer refresh)
	{ _logicalGroups = Objects.requireNonNull(groups); _refreshLogicalGroups = Objects.requireNonNull(refresh); }
	private org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty logicalGroup(long profileId, PhantomGoal root)
	{
		if (root == null || !org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.ordinaryIntent(profileId, root)) { return null; }
		return _logicalGroups.apply(profileId).filter(group -> group.active() && !group.activityReplanRequired() && group.members().stream().anyMatch(member -> member.profileId() == profileId && member.goalId() == root.goalId() && root.revision() >= member.goalRevision())).orElse(null);
	}
	public boolean activityPending(long id) { return _logicalGroups.apply(id).map(group -> group.activityPending()).orElse(false); }
	/** All-member ordinary planning only; this neither publishes a root nor advances a receipt. */
	public List<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ActivityRoot> planLogicalActivity(
		org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty group, long now)
	{
		final var states = new java.util.LinkedHashMap<Long, PhantomBackgroundState>();
		final var goals = new java.util.LinkedHashMap<Long, StoredGoal>();
		final var histories = new java.util.LinkedHashMap<Long, Snapshot>();
		final var generation = _planner.generation();
		for (var member : group.members())
		{
			final long id = member.profileId(); final var state = _background.acquisitionSnapshot(id).orElse(null);
			final var goal = _goals.load(id).orElse(null); final var history = _store.load(id).orElse(null);
			if (state == null || goal == null || history == null || state.state() != PhantomBackgroundState.State.READY || !state.hashes().equals(generation.authorityHashes())
				|| !org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.compatibleRole(member.role(), potentialPartyRole(id, state)) || !org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.ordinaryIntent(id, goal.goal()))
			{ return List.of(); }
			states.put(id, state); goals.put(id, goal); histories.put(id, history);
		}
		if (org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.forRoster(states.values().stream().map(state -> state.progress().level()).toList()).isEmpty()) { return List.of(); }
		final long leader = group.leaderProfileId(); final var leaderHistory = histories.get(leader).state();
		final var common = _planner.rendezvousSources(leader, states.get(leader), goals.get(leader).goal(), leaderHistory.deterministicSeed(), leaderHistory.planOrdinal(), Set.of(), Set.of(),
			source -> !source.equals(group.objective()) && states.values().stream().allMatch(state -> _planner.partySourceAvailable(state, source, Set.of())));
		for (var source : common)
		{
			final var proposals = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ActivityRoot>();
			for (var member : group.members())
			{
				final long id = member.profileId(); final var before = histories.get(id);
				final var planned = _planner.proposeLogicalActivity(id, states.get(id), goals.get(id).goal(), before.state().deterministicSeed(), Math.addExact(before.state().planOrdinal(), 1), source);
				if (!planned.ready()) { break; }
				final var history = before.state().withPlan(planned.goal().goalId(), planned.goal().revision(), Math.addExact(before.state().planOrdinal(), 1), planned.planIdentity(), generation.knowledgeGeneration(), generation.topologyGeneration());
				proposals.add(new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ActivityRoot(group.groupId(), group.generation(), id, goals.get(id).rowVersion(), now + 60_000, planned.goal(),
					new PhantomProfileRepository.ComponentMutation(PhantomBackgroundCatchupState.COMPONENT_TYPE, before.rowVersion(), PhantomBackgroundCatchupState.SCHEMA_VERSION, new PhantomBackgroundCatchupStateCodec().encode(history))));
			}
			if (proposals.size() == group.members().size() && _planner.generation().equals(generation)) { return proposals; }
		}
		return List.of();
	}

	public PhantomHistoricalBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomHistoricalBackgroundPlanner planner, PhantomBackgroundService background, PhantomMaterializationService materialization)
	{
		this(profiles, goals, planner, background, materialization, System::nanoTime);
	}

	public PhantomHistoricalBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomHistoricalBackgroundPlanner planner, PhantomBackgroundService background, PhantomMaterializationService materialization, LongSupplier visibleClock)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
		_goals = Objects.requireNonNull(goals, "goals");
		_store = new PhantomBackgroundCatchupStore(profiles, goals);
		_planner = Objects.requireNonNull(planner, "planner");
		_background = Objects.requireNonNull(background, "background");
		_materialization = Objects.requireNonNull(materialization, "materialization");
		_visibleClock = Objects.requireNonNull(visibleClock);
		_background.bindNativeRecoveryHistory(this);
	}

	/** Immutable exact admission captured before native cleanup removes the foreground owner. */
	static final class NativeRecoveryHandoff
	{
		private final long profileId, epoch;
		private final Admission admission;
		private NativeRecoveryHandoff(long profileId, long epoch, Admission admission)
		{
			this.profileId = profileId; this.epoch = epoch; this.admission = admission;
		}
	}

	NativeRecoveryHandoff captureNativeRecoveryHandoff(long profileId, PhantomGoal expectedGoal, int objectId, long epoch)
	{
		final var handoff = _foregroundHandoffs.get(profileId);
		final var goal = _goals.load(profileId).orElse(null);
		return (handoff != null) && (handoff.materializedAtNanos() == epoch) && (handoff.admission().characterObjectId() == objectId)
			&& (goal != null) && goal.goal().equals(expectedGoal) && exactForegroundHandoff(profileId, handoff)
			? new NativeRecoveryHandoff(profileId, epoch, handoff.admission()) : null;
	}

	PhantomMaterializationService.MaterializeResult materializeColdNativeRecovery(long profileId, PhantomGoal expectedGoal)
	{
		final var claim = _store.load(profileId).orElse(null);
		if ((claim == null) || !claim.state().blocksNormalOperation()) { return _materialization.materialize(profileId); }
		final var goal = _goals.load(profileId).orElse(null);
		final var state = _background.acquisitionSnapshot(profileId).orElse(null);
		if (_foregroundDecisionsRevoked || _materialization.find(profileId).isPresent() || (state == null) || (state.state() != PhantomBackgroundState.State.DEAD)
			|| (state.identity().profileId() != profileId) || (goal == null) || !goal.goal().equals(expectedGoal)
			|| (claim.state().goalId() != expectedGoal.goalId()) || (claim.state().goalRevision() != expectedGoal.revision()) || !currentClaim(profileId, claim))
		{
			return new PhantomMaterializationService.MaterializeResult(ResultStatus.CATCHUP_FENCED, null);
		}
		// Existing native admission rechecks the exact claim/goal at load and store; NORMAL remains fenced.
		return _materialization.materialize(profileId, MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, claim.state().requestId());
	}

	PhantomMaterializationService.MaterializeResult resumeNativeRecoveryHandoff(NativeRecoveryHandoff captured, int objectId, long epoch)
	{
		final var admission = captured.admission;
		final var state = _background.acquisitionSnapshot(captured.profileId).orElse(null);
		if (_foregroundDecisionsRevoked || (captured.epoch != epoch) || (admission.characterObjectId() != objectId)
			|| _materialization.find(captured.profileId).isPresent() || (state == null) || (state.state() != PhantomBackgroundState.State.READY)
			|| (state.identity().characterObjectId() != objectId) || !currentClaim(captured.profileId, admission.catchup())
			|| !Objects.equals(admission.claim(), _profiles.findComponent(captured.profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null))
			|| !Objects.equals(admission.goal(), _profiles.findComponent(captured.profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)))
		{
			return new PhantomMaterializationService.MaterializeResult(ResultStatus.CATCHUP_FENCED, null);
		}
		return _materialization.materialize(captured.profileId, MaterializationPurpose.NATIVE_CONTEXT_HANDOFF, admission.ownerClaim());
	}

	/** Dynamic failures expire; they never alter the canonical topology or farm intention. */
	public void recordVisibleFailure(long profileId, PhantomGoal goal, String failedStep)
	{
		final var spec = PhantomBackgroundGoalSpec.parse(goal);
		final long now = _visibleClock.getAsLong();
		final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
		// Failures are rare; only admission/eviction takes this lock, never the decision hot path.
		synchronized (_visibleFailures)
		{
			_visibleFailures.entrySet().removeIf(entry -> (now - entry.getValue()._lastFailureNanos) >= VisibleFailures.TTL_NANOS);
			_visibleFailures.computeIfAbsent(profileId, _ -> new VisibleFailures()).record(spec.npcId() + "@" + spec.anchorId(), failedStep, epoch, now);
			if (_visibleFailures.size() > 1024)
			{
				_visibleFailures.entrySet().stream().min(java.util.Comparator.<java.util.Map.Entry<Long, VisibleFailures>>comparingLong(entry -> entry.getValue()._lastFailureNanos).thenComparingLong(java.util.Map.Entry::getKey)).ifPresent(entry -> _visibleFailures.remove(entry.getKey(), entry.getValue()));
			}
		}
	}

	/** A synchronous travel terminal belongs to its captured lifetime, never a replacement objectId. */
	public boolean recordVisibleTravelFailure(long profileId, PhantomVisibleFarmTravel.Failure failure)
	{
		if ((failure == null) || (failure.profileId() != profileId) || (failure.player() == null)) { return false; }
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.characterObjectId() != failure.player().getObjectId()) || (lifetime.materializedAtNanos() != failure.epoch())) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || (action.player() != failure.player()) || !Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), failure.goal())) { return false; }
			return switch (failure.disposition())
			{
				case ROUTE_UNUSABLE, NATIVE_ACTION_REJECTED, INDETERMINATE_ROUTE ->
				{
					if (failure.disposition() == PhantomVisibleFarmTravel.Disposition.INDETERMINATE_ROUTE || failure.stepId().startsWith("live.approach." + PhantomBackgroundGoalSpec.parse(failure.goal()).anchorId() + "@"))
					{
						final long now = _visibleClock.getAsLong();
						synchronized (_visibleFailures)
						{
							if (_visibleFailures.containsKey(profileId) || (_visibleFailures.size() < 1024))
							{
								_visibleFailures.computeIfAbsent(profileId, _ -> new VisibleFailures()).route(failure.goal(), failure.epoch(), failure.stepId(), now);
							}
						}
					}
					else { recordVisibleFailure(profileId, failure.goal(), failure.stepId()); }
					yield true;
				}
				case PROTOCOL_VIOLATION ->
				{
					final long now = _visibleClock.getAsLong();
					synchronized (_visibleFailures)
					{
						_visibleFailures.entrySet().removeIf(entry -> (now - entry.getValue()._lastFailureNanos) >= VisibleFailures.TTL_NANOS);
						if (_visibleFailures.containsKey(profileId) || (_visibleFailures.size() < 1024))
						{
							_visibleFailures.computeIfAbsent(profileId, _ -> new VisibleFailures()).protocol(failure.goal(), failure.epoch(), failure.reason(), now);
						}
					}
					yield true;
				}
				case TRANSIENT_SERVICE, STORE_PENDING -> false;
			};
		}
	}

	public Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
	{
		if ((profileId <= 0) || (fromEpochMinute < 0) || (targetEpochMinute <= fromEpochMinute) || (targetEpochMinute - fromEpochMinute > Integer.MAX_VALUE))
		{
			return Result.rejected(ResultStatusCode.INVALID_REQUEST, "catchup.request.invalid", null);
		}
		final Result logicalRefresh = refreshLogicalBeforeBackground(profileId, null);
		if (logicalRefresh != null) { return logicalRefresh; }
		final var linkedProfile = _profiles.find(profileId).filter(profile -> profile.characterObjectId() != null).orElse(null);
		if (linkedProfile == null)
		{
			return Result.rejected(ResultStatusCode.PROFILE_UNAVAILABLE, "catchup.profile.unlinked", null);
		}
		final Result lifecycle = recoverColdLifecycle(profileId, _store.load(profileId).orElse(null));
		if (!lifecycle.successful()) { return lifecycle; }
		final Snapshot existing = lifecycle.snapshot();
		if (((existing == null) || (existing.state().status() == Status.COMPLETE)) && _materialization.find(profileId).isPresent())
		{
			return Result.rejected(ResultStatusCode.NORMAL_MATERIALIZED, "catchup.normal_materialized", null);
		}
		final var generation = _planner.generation();
		final String requestId = digest("BACKGROUND_CATCHUP_REQUEST_V1", profileId, fromEpochMinute, targetEpochMinute, deterministicSeed, generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes());
		final long catchupGeneration = positiveLong(digest("BACKGROUND_CATCHUP_GENERATION_V1", requestId));
		PhantomBackgroundCatchupState initial = new PhantomBackgroundCatchupState(Status.PENDING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, fromEpochMinute, 0, 0, catchupGeneration, generation.knowledgeGeneration(), generation.topologyGeneration(), 0, 0, "", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
		final Snapshot claimed;
		String renewalRecovery = "";
		try
		{
			if ((existing != null) && !sameRequest(existing.state(), initial))
			{
				if (existing.state().status() != Status.COMPLETE)
				{
					return Result.rejected(ResultStatusCode.CONFLICT, "catchup.claim.stale", existing);
				}
				final StoredGoal currentGoal = _goals.load(profileId).orElse(null);
				final boolean currentAuthority = existing.state().authorityHashes().equals(generation.authorityHashes()) && (existing.state().knowledgeGeneration() == generation.knowledgeGeneration()) && (existing.state().topologyGeneration() == generation.topologyGeneration());
				PhantomBackgroundState backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
				if ((backgroundState != null) && (backgroundState.state() == PhantomBackgroundState.State.VERIFY_PENDING))
				{
					if ((currentGoal == null) || (currentGoal.goal().status() != PhantomGoalStatus.ACTIVE) || (currentGoal.goal().goalId() != existing.state().goalId()) || (currentGoal.goal().revision() != existing.state().goalRevision()) || (backgroundState.identity().profileId() != profileId) || (backgroundState.identity().characterObjectId() != linkedProfile.characterObjectId()) || !currentAuthority || !backgroundState.hashes().equals(generation.authorityHashes()))
					{
						return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.pending_prerequisite_invalid", existing);
					}
					final var reconciled = _background.reconcileHistoricalPending(profileId, currentGoal.goal(), existing.state().generation(), Math.addExact(existing.state().intervalOrdinal(), 1));
					if (!reconciled.successful())
					{
						return Result.rejected(reconciled.status() == OperationStatus.RETRY ? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.pending." + reconciled.reason(), existing);
					}
					backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
					renewalRecovery = "catchup.renewal.pending";
				}
				if ((backgroundState != null) && (backgroundState.state() == PhantomBackgroundState.State.MATERIALIZED))
				{
					final var recovery = _background.recoverAbandonedMaterialization(profileId);
					if (recovery.status() == OperationStatus.RETRY)
					{
						return Result.rejected(ResultStatusCode.RETRY, "catchup.renewal.background_recovery_busy", existing);
					}
					if (recovery.status() != OperationStatus.SUCCESS)
					{
						return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.background_state_invalid", existing);
					}
					backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
					renewalRecovery = "catchup.renewal.background_state_invalid";
				}
				if ((backgroundState != null) && (((backgroundState.state() != PhantomBackgroundState.State.READY) && (backgroundState.state() != PhantomBackgroundState.State.DEAD)) || (backgroundState.identity().profileId() != profileId) || (backgroundState.identity().characterObjectId() != linkedProfile.characterObjectId())))
				{
					return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.background_state_invalid", existing);
				}
				if (currentGoal == null)
				{
					if ((backgroundState != null) && ((backgroundState.state() == PhantomBackgroundState.State.DEAD) || !backgroundState.hashes().equals(generation.authorityHashes())))
					{
						return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.background_state_invalid", existing);
					}
					renewalRecovery = backgroundState == null ? "catchup.renewal.goal_missing.background_missing" : "catchup.renewal.goal_missing";
					initial = new PhantomBackgroundCatchupState(Status.PENDING, initial.requestId(), initial.deterministicSeed(), initial.fromEpochMinute(), initial.targetEpochMinute(), initial.cursorEpochMinute(), Math.addExact(existing.state().planOrdinal(), 1), 0, initial.generation(), initial.knowledgeGeneration(), initial.topologyGeneration(), 0, 0, "", initial.modelVersion(), initial.authorityHashes(), "");
					claimed = _store.renewCompletedUnplanned(profileId, existing, initial);
				}
				else
				{
					if ((currentGoal.goal().goalId() != existing.state().goalId()) || (currentGoal.goal().revision() < existing.state().goalRevision()) || (currentGoal.goal().status() != PhantomGoalStatus.ACTIVE))
					{
						return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.goal_mismatch", existing);
					}
					if (backgroundState == null)
					{
						renewalRecovery = "catchup.renewal.background_missing";
						if (currentGoal.goal().revision() != existing.state().goalRevision())
						{
							return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.goal_mismatch", existing);
						}
						initial = initial.withPlan(existing.state().goalId(), existing.state().goalRevision(), existing.state().planOrdinal(), existing.state().planIdentity(), generation.knowledgeGeneration(), generation.topologyGeneration());
						claimed = _store.renewCompleted(profileId, existing, initial);
					}
					else if (currentAuthority && backgroundState.hashes().equals(generation.authorityHashes()) && (currentGoal.goal().revision() == existing.state().goalRevision()))
					{
						initial = initial.withPlan(existing.state().goalId(), existing.state().goalRevision(), existing.state().planOrdinal(), existing.state().planIdentity(), generation.knowledgeGeneration(), generation.topologyGeneration());
						claimed = _store.renewCompleted(profileId, existing, initial);
					}
					else
					{
						if (currentGoal.goal().revision() != existing.state().goalRevision()) { renewalRecovery = "catchup.renewal.goal_mismatch"; }
						if (!backgroundState.hashes().equals(generation.authorityHashes()))
						{
							final Result refresh = refreshCanonicalBaseline(profileId, existing);
							if (!refresh.successful())
							{
								return refresh;
							}
							backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
							if ((backgroundState == null) || !backgroundState.hashes().equals(generation.authorityHashes()))
							{
								return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.renewal.canonical_refresh_unverified", existing);
							}
						}
						final long nextPlanOrdinal = Math.addExact(existing.state().planOrdinal(), 1);
						final var replacement = planOrIdle(profileId, backgroundState, currentGoal.goal(), deterministicSeed, nextPlanOrdinal, Set.of());
						if (!replacement.ready())
						{
							return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, replacement.reasonKey(), existing);
						}
						if (!_planner.generation().equals(generation))
						{
							return Result.rejected(ResultStatusCode.RETRY, "catchup.renewal.generation_changed", existing);
						}
						initial = initial.withPlan(replacement.goal().goalId(), replacement.goal().revision(), nextPlanOrdinal, replacement.planIdentity(), replacement.generation().knowledgeGeneration(), replacement.generation().topologyGeneration());
						claimed = _store.renewCompletedWithPlan(profileId, existing, initial, currentGoal, replacement.goal()).catchup();
					}
				}
			}
			else
			{
				claimed = _store.claim(profileId, initial);
			}
		}
		catch (PhantomGoalStateStore.PublicationPending pending)
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.claim.publication_pending", null);
		}
		catch (RuntimeException exception)
		{
			return Result.rejected(ResultStatusCode.CONFLICT, "catchup.claim.conflict", null);
		}
		if (!sameRequest(claimed.state(), initial))
		{
			return Result.rejected(ResultStatusCode.CONFLICT, "catchup.claim.stale", claimed);
		}
		if (claimed.state().status() == Status.COMPLETE)
		{
			return Result.success(claimed, 0);
		}
		if (claimed.state().status() == Status.FAILED_REPLAN_REQUIRED)
		{
			if (PhantomBackgroundService.paidTravelConflictCandidate(claimed.state())) { return paidTravelConflictHandoff(profileId, claimed); }
			if (requiresNativeMaterialization(claimed.state().failureReason())) { return recoverNativeContext(profileId, claimed); }
			if (isRecoverableFailure(claimed.state().failureReason())) { return Result.success(claimed, 0); }
			return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, claimed.state().failureReason(), claimed);
		}
		final Result baseline = ensureBaseline(profileId, claimed);
		if (renewalRecovery.isEmpty()) { return baseline; }
		final ResultStatusCode status = (baseline.status() == ResultStatusCode.REPLAN_REQUIRED) && isRecoverableFailure(baseline.reason()) ? ResultStatusCode.RETRY : baseline.status();
		return new Result(status, renewalRecovery + (baseline.successful() ? ".recovered" : "." + baseline.reason()), baseline.snapshot(), baseline.advancedIntervals());
	}

	private Result ensureBaseline(long profileId, Snapshot claimed)
	{
		final Result lifecycle = recoverColdLifecycle(profileId, claimed);
		if (!lifecycle.successful()) { return lifecycle; }
		Snapshot current = lifecycle.snapshot();
		StoredGoal storedGoal = _goals.load(profileId).orElse(null);
		final Optional<PhantomBackgroundState> existingBackground = _background.acquisitionSnapshot(profileId);
        if (current.state().status() == Status.PENDING && current.state().goalId() == 0 && current.state().planOrdinal() == 0 && current.state().intervalOrdinal() == 0
            && storedGoal != null && existingBackground.filter(state -> state.state() == PhantomBackgroundState.State.READY).isPresent()
            && org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.ordinaryIntent(profileId, storedGoal.goal())
            && _materialization.find(profileId).isEmpty())
        {
            final var goal = storedGoal.goal(); final var baseline = existingBackground.get();
            final var planned = current.state().withPlan(goal.goalId(), goal.revision(), 0,
                digest("BACKGROUND_CATCHUP_EXISTING_ROOT_V1", profileId, goal, baseline.hashes()), current.state().knowledgeGeneration(), current.state().topologyGeneration(), baseline.hashes());
            // Binding grants no interval/admission. The existing branch below refreshes stale
            // authority through canonical native STORE and verifies native context before RUNNING.
            try { current = _store.bindExistingInitialPlan(profileId, current, planned, storedGoal, existingBackground.get()); }
            catch (RuntimeException changed) { return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.initial_binding_changed", _store.load(profileId).orElse(current)); }
        }
		if (existingBackground.isPresent() && (existingBackground.get().state() == PhantomBackgroundState.State.VERIFY_PENDING) && (current.state().status() == Status.PENDING) && (current.state().goalId() > 0) && (storedGoal != null) && (storedGoal.goal().goalId() == current.state().goalId()) && (storedGoal.goal().revision() == current.state().goalRevision()) && _materialization.find(profileId).filter(owner -> owner.playerRetained() && owner.identityLeaseRetained() && !owner.actionAdmissionOpen()).isPresent())
		{
			// The retained Player resolves its exact owned-store receipt before a historical baseline can resume.
			final var cleaned = _materialization.retryCleanup(profileId);
			if (cleaned.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.cleanup_retry", current);
			}
			return ensureBaseline(profileId, _store.load(profileId).orElse(current));
		}
		if (existingBackground.isPresent() && (existingBackground.get().state() == PhantomBackgroundState.State.MATERIALIZED) && (current.state().status() == Status.PENDING) && (current.state().goalId() > 0) && (storedGoal != null) && (storedGoal.goal().goalId() == current.state().goalId()) && (storedGoal.goal().revision() == current.state().goalRevision()))
		{
			if (_materialization.find(profileId).isPresent())
			{
				final var cleaned = _materialization.retryCleanup(profileId);
				if (cleaned.status() != ResultStatus.SUCCESS)
				{
					return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.cleanup_retry", current);
				}
			}
			else
			{
				final var recovered = _background.recoverAbandonedMaterialization(profileId);
				if (recovered.status() == OperationStatus.RETRY)
				{
					return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.background_recovery_busy", current);
				}
				if (recovered.status() != OperationStatus.SUCCESS)
				{
					return fail(profileId, current, "catchup.baseline.materialized_unreconciled");
				}
			}
			return ensureBaseline(profileId, _store.load(profileId).orElse(current));
		}
		if (existingBackground.isPresent() && (current.state().goalId() > 0))
		{
			if ((current.state().goalId() <= 0) || (storedGoal == null) || (storedGoal.goal().goalId() != current.state().goalId()) || (storedGoal.goal().revision() != current.state().goalRevision()) || ((existingBackground.get().state() != PhantomBackgroundState.State.READY) && (existingBackground.get().state() != PhantomBackgroundState.State.DEAD)))
			{
				return fail(profileId, current, "catchup.baseline.conflict");
			}
			final var generation = _planner.generation();
			if ((current.state().status() == Status.PENDING) && (!current.state().authorityHashes().equals(generation.authorityHashes())
				|| (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration())
				|| !existingBackground.get().hashes().equals(generation.authorityHashes())))
			{
				// Exact stale recovery owns canonical native refresh and CAS; attestation cannot precede it.
				final Result recovered = recoverStale(profileId, current, generation);
				return recovered.successful() ? ensureBaseline(profileId, recovered.snapshot()) : recovered;
			}
			final Result attested = ensureNativeContext(profileId, current, existingBackground.get());
			if (!attested.successful()) { return attested; }
			if (current.state().status() == Status.PENDING)
			{
				try
				{
					current = _store.replace(profileId, current, current.state().running());
				}
				catch (RuntimeException exception)
				{
					return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.publish_retry", _store.load(profileId).orElse(current));
				}
			}
			return Result.success(current, 0);
		}
		if (existingBackground.isPresent() && ((existingBackground.get().state() != PhantomBackgroundState.State.READY) && (existingBackground.get().state() != PhantomBackgroundState.State.DEAD)))
		{
			return fail(profileId, current, "catchup.baseline.conflict");
		}

		if (_materialization.find(profileId).isPresent())
		{
			if ((current.state().status() != Status.PENDING) || (current.state().goalId() <= 0))
			{
				return Result.rejected(ResultStatusCode.NORMAL_MATERIALIZED, "catchup.materialization.busy", current);
			}
			final var retried = _materialization.retryCleanup(profileId);
			if (retried.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.cleanup_retry", current);
			}
			return ensureBaseline(profileId, _store.load(profileId).orElse(current));
		}

		final var materialized = _materialization.materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, current.state().requestId());
		if (materialized.status() != ResultStatus.SUCCESS)
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.materialize_" + materialized.status().name().toLowerCase(), current);
		}
		String deferredFailure = "";
		try
		{
			storedGoal = _goals.load(profileId).orElse(null);
			if (storedGoal == null)
			{
				final Optional<ActionLease> action = _materialization.tryAcquireAction(profileId);
				if (action.isEmpty())
				{
					deferredFailure = "catchup.baseline.action_lease";
				}
				else
				{
					try (ActionLease lease = action.get())
					{
						var plan = _planner.planInitial(profileId, lease.player(), current.state().deterministicSeed(), current.state().planOrdinal());
						if ("planner.target_or_route.absent".equals(plan.reasonKey())) { plan = _planner.idleInitial(profileId, lease.player(), current.state().deterministicSeed(), current.state().planOrdinal()); }
						if (!plan.ready())
						{
							deferredFailure = plan.reasonKey();
						}
						else
						{
							final PhantomBackgroundCatchupState plannedState = current.state().withPlan(plan.goal().goalId(), plan.goal().revision(), current.state().planOrdinal(), plan.planIdentity(), plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration());
							final PlannedSnapshot persisted = _store.persistInitialPlan(profileId, current, plannedState, plan.goal());
							current = persisted.catchup();
							storedGoal = persisted.goal();
						}
					}
				}
			}
			else if ((current.state().goalId() <= 0) || (storedGoal.goal().goalId() != current.state().goalId()) || (storedGoal.goal().revision() != current.state().goalRevision()))
			{
				deferredFailure = "catchup.goal.conflict";
			}
		}
		catch (RuntimeException exception)
		{
			deferredFailure = "catchup.baseline.plan_or_persist_retry";
		}
		finally
		{
			final var dematerialized = _materialization.dematerialize(profileId);
			if (dematerialized.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.baseline.store_retry", _store.load(profileId).orElse(current));
			}
		}
		if (!deferredFailure.isEmpty())
		{
			return deferredFailure.startsWith("planner.") ? fail(profileId, _store.load(profileId).orElse(current), deferredFailure) : Result.rejected(ResultStatusCode.RETRY, deferredFailure, _store.load(profileId).orElse(current));
		}
		return ensureBaseline(profileId, _store.load(profileId).orElse(current));
	}

	public void bindAuthorityRefreshBoundary(java.util.function.BiFunction<Long, java.util.function.Supplier<Result>, Result> boundary)
	{ _authorityRefreshBoundary = Objects.requireNonNull(boundary); }

	/** Unchanged child bytes are still an obligation, including compensation and paid return. */
	public boolean nativeIntentPending(long profileId)
	{
		for (String type : List.of("supply.intent", "learning.intent", "career.intent"))
		{
			final var row = _profiles.findComponent(profileId, type);
			if (row.isPresent() && nativeIntentPending(row.get())) { return true; }
		}
		return false;
	}
	public static boolean nativeIntentPending(PhantomProfileComponent row)
	{
		if (row.componentSchemaVersion() != 1 || !Set.of("supply.intent", "learning.intent", "career.intent").contains(row.componentType()))
		{ throw new IllegalArgumentException("Unsupported native intent component."); }
		final var child = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(row.payload());
		return org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.continuing(child)
			|| "supply.intent".equals(row.componentType()) && org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.returning(child);
	}

	/** Completed history retains its original authority; only the current Background must be fresh. */
	public static boolean authorityRefreshRequired(PhantomBackgroundState.Hashes background, PhantomBackgroundCatchupState history, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
	{
		return !Objects.equals(background, generation.authorityHashes()) || history != null && history.status() != Status.COMPLETE
			&& (!history.authorityHashes().equals(generation.authorityHashes()) || history.knowledgeGeneration() != generation.knowledgeGeneration() || history.topologyGeneration() != generation.topologyGeneration());
	}

	/** Preparation only: no historical interval is executed or manufactured by this route. */
	public Result prepareCurrentAuthority(long profileId)
	{ return prepareCurrentAuthority(profileId, false); }

	/** Startup recovery has closed native admission. Use the existing absent-owner transaction before reattestation. */
	public Result prepareColdStartupAuthority(long profileId)
	{
		return _authorityRefreshBoundary.apply(profileId, () ->
		{
			final var state = _background.acquisitionSnapshot(profileId).orElse(null);
			if (state != null && (state.state() == PhantomBackgroundState.State.MATERIALIZED || state.state() == PhantomBackgroundState.State.VERIFY_PENDING))
			{
				final var recovered = _background.recoverAbandonedMaterialization(profileId);
				if (!recovered.successful()) { return Result.rejected(ResultStatusCode.RETRY, recovered.reason(), _store.load(profileId).orElse(null)); }
			}
			return prepareCurrentAuthority(profileId, true);
		});
	}

	private Result prepareCurrentAuthority(long profileId, boolean owned)
	{
		try
		{
			final Snapshot current = _store.load(profileId).orElse(null);
			final var state = _background.acquisitionSnapshot(profileId).orElse(null);
			if (_recoveryClaims.containsKey(profileId)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.refresh_busy", current); }
			if (current == null && state == null)
			{ return _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).isEmpty() ? Result.success(null, 0) : Result.rejected(ResultStatusCode.RETRY, "catchup.authority.background_unavailable", null); }
			final var generation = _planner.generation();
			final boolean stale = authorityRefreshRequired(state == null ? null : state.hashes(), current == null ? null : current.state(), generation);
			if (state != null && (state.state() == PhantomBackgroundState.State.INCONSISTENT || state.state() == PhantomBackgroundState.State.VERIFY_PENDING))
			{ return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.authority.background_state_invalid", current); }
			if (current == null && stale) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.authority.claim_absent", null); }
			final Result logical = refreshLogicalBeforeBackground(profileId, current);
			if (logical != null) { return logical; }
			if (_profiles.findComponent(profileId, PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent()) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.store_pending", current); }
			final var commerce = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(_profiles).find(profileId);
			if (commerce.filter(row -> !row.receipt().state().terminal() || row.receipt().state() == org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.INCONSISTENT).isPresent())
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.commerce_pending", current); }
			if (_profiles.findComponent(profileId, PhantomNativeContext.COMPONENT_TYPE).map(row -> PhantomNativeContext.decode(row.payload()).phase() == PhantomNativeContext.Phase.PENDING).orElse(false))
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.context_pending", current); }
			// An untagged historical receipt survives ordinary native STORE successors. It cannot prove
			// their current vitals, but exact bound context may admit native reattestation, never offscreen replay.
			if (stale && state != null && !PhantomBackgroundTransaction.offscreenReceiptHashCurrent(state, PhantomBackgroundTransaction.expectedAfterHash(state)))
			{
				if (PhantomOffscreenOutdoorTravel.taggedReceipt(state)) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.authority.receipt_mismatch", current); }
				final var proof = _background.historicalNativeContext(profileId, state);
				if (proof.status() != PhantomBackgroundTransaction.Status.SUCCESS || !state.equals(proof.state()) || proof.context() == null || proof.context().phase() != PhantomNativeContext.Phase.COMPLETED)
				{ return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.authority.receipt_mismatch", current); }
			}
			if (!stale && (current == null || current.state().status() != Status.PENDING && current.state().status() != Status.FAILED_REPLAN_REQUIRED)) { return Result.success(current, 0); }
			if (nativeIntentPending(profileId)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.intent_pending", current); }
			if (current.state().status() == Status.FAILED_REPLAN_REQUIRED && !isRecoverableFailure(current.state().failureReason()) && !"catchup.authority_hash_or_generation_stale".equals(current.state().failureReason()))
			{ return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, current.state().failureReason(), current); }
			if (_materialization.find(profileId).isPresent()) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.native_owner_busy", current); }
			if (!owned) { return _authorityRefreshBoundary.apply(profileId, () -> prepareCurrentAuthority(profileId, true)); }
			if (current.state().status() == Status.PENDING) { return ensureBaseline(profileId, current); }
			if (current.state().status() != Status.COMPLETE) { return recoverStale(profileId, current, generation); }
			final var goal = _goals.load(profileId).orElse(null);
			final String invalid = recoveryPrerequisite(profileId, current, state, goal);
			if (!invalid.isEmpty() || state.state() != PhantomBackgroundState.State.READY)
			{ return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, invalid.isEmpty() ? "catchup.authority.ready_required" : invalid, current); }
			final Result refreshed = refreshCanonicalBaseline(profileId, current);
			if (!refreshed.successful()) { return refreshed; }
			final var after = _background.acquisitionSnapshot(profileId).orElse(null);
			if (after == null || after.state() != PhantomBackgroundState.State.READY || !after.hashes().equals(generation.authorityHashes()) || !_planner.generation().equals(generation)
				|| !currentClaim(profileId, current) || !Objects.equals(goal, _goals.load(profileId).orElse(null)) || _materialization.find(profileId).isPresent()
				|| !state.identity().equals(after.identity()) || !state.progress().equals(after.progress()) || !state.receipt().equals(after.receipt()) || !state.clock().equals(after.clock()))
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.refresh_unverified", current); }
			final var proof = _background.historicalNativeContext(profileId, after);
			if (proof.status() != PhantomBackgroundTransaction.Status.SUCCESS || !after.equals(proof.state()) || proof.context() == null || proof.context().phase() != PhantomNativeContext.Phase.COMPLETED)
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.store_readback_unverified", current); }
			return Result.success(current, 0);
		}
		catch (RuntimeException unavailable) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.persistence_retry", null); }
	}

	public Result advance(long profileId, int maximumIntervals, int maximumSimulatedMinutes)
	{
		if ((maximumIntervals < 1) || (maximumIntervals > MAXIMUM_INTERVALS_PER_CALL) || (maximumSimulatedMinutes < 1) || (maximumSimulatedMinutes > MAXIMUM_SIMULATED_MINUTES_PER_CALL))
		{
			return Result.rejected(ResultStatusCode.INVALID_REQUEST, "catchup.advance.bounds", status(profileId).orElse(null));
		}
		Snapshot current = status(profileId).orElse(null);
		if (current == null)
		{
			return Result.rejected(ResultStatusCode.NOT_FOUND, "catchup.absent", null);
		}
		final Result logicalRefresh = refreshLogicalBeforeBackground(profileId, current);
		if (logicalRefresh != null) { return logicalRefresh; }
		final Result lifecycle = recoverColdLifecycle(profileId, current);
		if (!lifecycle.successful()) { return lifecycle; }
		current = lifecycle.snapshot();
		if (current.state().status() == Status.PENDING)
		{
			final Result baseline = ensureBaseline(profileId, current);
			if (!baseline.successful())
			{
				return baseline;
			}
			current = baseline.snapshot();
		}
		if (current.state().status() == Status.COMPLETE)
		{
			return prepareCurrentAuthority(profileId);
		}
		final var generation = _planner.generation();
		final boolean stale = !current.state().authorityHashes().equals(generation.authorityHashes()) || (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration());
		if (PhantomBackgroundService.paidTravelConflictCandidate(current.state())) { return paidTravelConflictHandoff(profileId, current); }
		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) && requiresNativeMaterialization(current.state().failureReason()))
		{
			final Result recovered = recoverNativeContext(profileId, current);
			if (!recovered.successful()) { return recovered; }
			current = recovered.snapshot();
		}
		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) && !isRecoverableFailure(current.state().failureReason()) && !"catchup.authority_hash_or_generation_stale".equals(current.state().failureReason()))
		{
			return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, current.state().failureReason(), current);
		}
		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) || stale)
		{
			if (!stale)
			{
				final Result recovered = recoverKnown(profileId, current);
				if (!recovered.successful()) { return recovered; }
				current = recovered.snapshot();
			}
			else
			{
				final Result recovery = recoverStale(profileId, current, generation);
				if (!recovery.successful())
				{
					return recovery;
				}
				current = recovery.snapshot();
			}
		}
		int advanced = 0;
		boolean repairedInterval = false;
		final int limit = Math.min(maximumIntervals, maximumSimulatedMinutes);
		while ((advanced < limit) && (current.state().status() == Status.RUNNING))
		{
			PhantomBackgroundState backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
			StoredGoal storedGoal = _goals.load(profileId).orElse(null);
			if ((backgroundState == null) || (storedGoal == null) || (storedGoal.goal().goalId() != current.state().goalId()) || (storedGoal.goal().revision() != current.state().goalRevision()) || ((backgroundState.state() != PhantomBackgroundState.State.READY) && (backgroundState.state() != PhantomBackgroundState.State.DEAD)))
			{
				final Result restored = restorePrerequisite(profileId, current, backgroundState, storedGoal);
				if (!restored.successful()) { return restored; }
				current = restored.snapshot();
				backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
				storedGoal = _goals.load(profileId).orElse(null);
				final String remaining = recoveryPrerequisite(profileId, current, backgroundState, storedGoal);
				if (!remaining.isEmpty()) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, remaining, current); }
			}
			if (((backgroundState.state() != PhantomBackgroundState.State.READY) && (backgroundState.state() != PhantomBackgroundState.State.DEAD)) || !backgroundState.hashes().equals(current.state().authorityHashes()))
			{
				return fail(profileId, current, "catchup.background_state_stale");
			}
			final Result attested = ensureNativeContext(profileId, current, backgroundState);
			if (!attested.successful()) { return attested; }
			backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
			storedGoal = _goals.load(profileId).orElse(null);
			final String remaining = recoveryPrerequisite(profileId, current, backgroundState, storedGoal);
			if (!remaining.isEmpty()) { return Result.rejected(ResultStatusCode.RETRY, remaining, current); }
			if (backgroundState.state() == PhantomBackgroundState.State.READY)
			{
				final PhantomGoal previousGoal = storedGoal.goal();
				final var logical = logicalGroup(profileId, previousGoal);
				final var replanned = logical == null ? reconsiderHistorical(_planner, profileId, backgroundState, previousGoal, current.state(), () -> _acceptedIntent.test(profileId, previousGoal))
					: _acceptedIntent.test(profileId, previousGoal) ? PhantomHistoricalBackgroundPlanner.Result.blocked("native_context.required:accepted_journey")
					: previousGoal.validSources().contains(logical.objective()) && _planner.remainsSuitable(backgroundState, previousGoal)
						? new PhantomHistoricalBackgroundPlanner.Result(previousGoal, PhantomBackgroundGoalSpec.parseLifecycle(previousGoal), current.state().planIdentity(), _planner.generation(), List.of(), "planner.logical_hold")
						: _planner.replanLogicalRendezvous(profileId, backgroundState, previousGoal, current.state().deterministicSeed(), Math.addExact(current.state().planOrdinal(), 1), logical.objective());
				if (!replanned.ready())
				{
					if (historicalPlanningFailureStatus(replanned.reasonKey()) == ResultStatusCode.RETRY) { return Result.rejected(ResultStatusCode.RETRY, replanned.reasonKey(), current); }
					return fail(profileId, current, replanned.reasonKey());
				}
				if (!replanned.goal().equals(storedGoal.goal()))
				{
					try
					{
						if (!_planner.generation().equals(replanned.generation())) { return Result.rejected(ResultStatusCode.RETRY, "catchup.replan.generation_changed", current); }
						final long nextPlanOrdinal = Math.addExact(current.state().planOrdinal(), 1);
						final PhantomBackgroundCatchupState replannedState = current.state().withPlan(replanned.goal().goalId(), replanned.goal().revision(), nextPlanOrdinal, replanned.planIdentity(), replanned.generation().knowledgeGeneration(), replanned.generation().topologyGeneration());
						final PlannedSnapshot persisted = _store.replacePlan(profileId, current, replannedState, storedGoal, replanned.goal());
						current = persisted.catchup();
						if (farmProjectionChanged(storedGoal.goal(), replanned.goal()))
						{
							final Result refreshed = refreshCanonicalBaseline(profileId, current);
							if (!refreshed.successful()) { return refreshed; }
							backgroundState = _background.acquisitionSnapshot(profileId).orElseThrow();
						}
						storedGoal = persisted.goal();
					}
					catch (RuntimeException exception)
					{
						return Result.rejected(ResultStatusCode.RETRY, "catchup.replan.persistence_retry", _store.load(profileId).orElse(current));
					}
				}
			}
			final PhantomBackgroundCatchupState next = current.state().advanceTo(Math.addExact(current.state().cursorEpochMinute(), 1));
			final var operation = _background.advanceHistorical(profileId, storedGoal.goal(), current, next);
			final Snapshot observed = _store.load(profileId).orElse(current);
			if ((observed.state().cursorEpochMinute() == next.cursorEpochMinute()) && observed.state().requestId().equals(current.state().requestId()))
			{
				current = observed;
				advanced++;
				repairedInterval = false;
				continue;
			}
			if (operation.status() == PhantomBackgroundService.OperationStatus.RETRY)
			{
				return Result.rejected(ResultStatusCode.RETRY, operation.reason(), observed);
			}
			// A context race stays fenced without failing the original goal or advancing its interval.
			if (requiresNativeMaterialization(operation.reason())) { return Result.rejected(ResultStatusCode.RETRY, operation.reason(), observed); }
			final String failureReason = repairedInterval && operation.reason().startsWith("model.object_cap") ? "model.object_cap_internal" : operation.reason();
			final Result failed = fail(profileId, observed, failureReason);
			if (!repairedInterval && (failed.status() == ResultStatusCode.REPLAN_REQUIRED) && isRecoverableFailure(operation.reason()))
			{
				repairedInterval = true;
				final Result repaired = recoverKnown(profileId, failed.snapshot());
				if (repaired.successful()) { current = repaired.snapshot(); continue; }
				return repaired;
			}
			return failed;
		}
		return Result.success(current, advanced);
	}

	/** Native attestation is a prerequisite, never an awarded historical interval. */
	private Result ensureNativeContext(long profileId, Snapshot current, PhantomBackgroundState baseline)
	{
		try
		{
			if (!currentClaim(profileId, current)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.claim_changed", current); }
			final var goalComponent = _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
			final StoredGoal goal = goalComponent == null ? null : _goals.decodeComponent(goalComponent);
			final String missing = recoveryPrerequisite(profileId, current, baseline, goal);
			if (!missing.isEmpty()) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, missing, current); }
			final var generation = _planner.generation();
			if (!current.state().authorityHashes().equals(generation.authorityHashes()) || (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration()) || !baseline.hashes().equals(generation.authorityHashes()))
			{
				return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.authority_hash_or_generation_stale", current);
			}
			final var logical = logicalGroup(profileId, goal.goal());
			final boolean logicalPreparation = logicalPreparationRequired(profileId, goal.goal(), current.state(), logical, System.currentTimeMillis(),
				() -> goal.goal().validSources().contains(logical.objective()) && _planner.remainsSuitable(baseline, goal.goal())
					|| _planner.replanLogicalRendezvous(profileId, baseline, goal.goal(), current.state().deterministicSeed(), Math.addExact(current.state().planOrdinal(), 1), logical.objective()).ready());
			var proof = _background.historicalNativeContext(profileId, baseline, logicalPreparation);
			if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) && (proof.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context." + proof.status().name().toLowerCase(java.util.Locale.ROOT), current);
			}
			if (proof.context().phase() == PhantomNativeContext.Phase.PENDING) { return Result.rejected(ResultStatusCode.RETRY, "native_context.pending", current); }
			final int beforePoints = proof.canonicalPoints();
			final boolean unknown = proof.context().phase() == PhantomNativeContext.Phase.UNKNOWN;
			if (proof.context().requiresCanonicalRefresh())
			{
				final Result refreshed = refreshCanonicalBaseline(profileId, current);
				if (!refreshed.successful()) { return refreshed; }
				final var after = _background.acquisitionSnapshot(profileId).orElse(null);
				if (!nativeContextFactsPreserved(baseline, after)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.baseline_changed", current); }
				proof = _background.historicalNativeContext(profileId, after, logicalPreparation);
			}
			if (!currentClaim(profileId, current) || !Objects.equals(goalComponent, _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)) || !_planner.generation().equals(generation))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.claim_goal_or_generation_changed", current);
			}
			if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) || (proof.context() == null) || (proof.context().phase() != PhantomNativeContext.Phase.COMPLETED))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.unattested", current);
			}
			if (logicalPreparation || _background.historicalNativeContextRequired(profileId, proof.state(), proof.context()))
			{
				final var delivery = _background.nativeContextSignalDelivery(profileId).orElse(null);
				return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "native_context.required:" + (delivery == null ? "not_delivered" : delivery.name().toLowerCase(java.util.Locale.ROOT)), current);
			}
			// Stock canonical0 -> nativeMIN1 is the sole supported load normalization, not old-load parity.
			if (unknown && (beforePoints != proof.canonicalPoints()) && !((beforePoints == 0) && (proof.canonicalPoints() == 1)))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.canonical_points_changed", current);
			}
			return Result.success(current, 0);
		}
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.persistence_retry", current); }
	}

	static boolean logicalPreparationRequired(long profileId, PhantomGoal root, PhantomBackgroundCatchupState claim,
		org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty group, long now, java.util.function.BooleanSupplier ordinaryReady)
	{
		return group != null && group.active() && org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.ordinaryIntent(profileId, root)
			&& root.goalId() == claim.goalId() && root.revision() == claim.goalRevision()
			&& (claim.status() == Status.RUNNING || claim.status() == Status.FAILED_REPLAN_REQUIRED)
			&& group.assemblyPending(profileId, root.goalId(), root.revision(), now)
			&& (claim.status() == Status.FAILED_REPLAN_REQUIRED && Set.of("native_context.required:logical_party_preparation", "native_context.required:logical_party_support").contains(claim.failureReason())
				|| !ordinaryReady.getAsBoolean());
	}

	private static boolean nativeContextFactsPreserved(PhantomBackgroundState before, PhantomBackgroundState after)
	{
		return (after != null) && (before.state() == after.state()) && before.identity().equals(after.identity()) && before.progress().equals(after.progress())
			&& before.position().equals(after.position()) && before.clock().equals(after.clock()) && before.receipt().equals(after.receipt())
			&& before.inventory().objects().equals(after.inventory().objects()) && before.inventory().canonicalHash().equals(after.inventory().canonicalHash()) && before.autoGetSkills().equals(after.autoGetSkills());
	}

	private Result paidTravelConflictHandoff(long profileId, Snapshot current)
	{
		try
		{
			final var state = _background.acquisitionSnapshot(profileId).orElse(null);
			final var goal = _goals.load(profileId).orElse(null);
			final var generation = _planner.generation();
			if (!currentClaim(profileId, current) || !recoveryPrerequisite(profileId, current, state, goal).isEmpty()
				|| !(paidTravelConflictGenerationCurrent(current.state(), generation) || paidTravelKnowledgeRefreshEligible(current.state(), generation))
				|| !state.hashes().equals(generation.authorityHashes()) && !state.hashes().equals(current.state().authorityHashes()))
			{ return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, current.state().failureReason(), current); }
			final var operation = _background.handoffPaidHistoricalConflict(profileId, goal.goal(), current,
				() -> currentClaim(profileId, current) && Objects.equals(goal, _goals.load(profileId).orElse(null)) && generation.equals(_planner.generation()));
			if (operation.reason().equals("catchup.paid_travel.authority_refresh_required"))
			{
				if (!currentClaim(profileId, current) || !Objects.equals(goal, _goals.load(profileId).orElse(null)) || !generation.equals(_planner.generation()))
				{ return Result.rejected(ResultStatusCode.RETRY, "catchup.paid_travel.claim_or_generation_changed", current); }
				// The no-write paid proof has released its BACKGROUND lease. Existing native STORE/CAS recovery owns renewal.
				return recoverStale(profileId, current, generation, state);
			}
			return Result.rejected(operation.status() == OperationStatus.RETRY || requiresNativeMaterialization(operation.reason())
				? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED, operation.reason(), current);
		}
		catch (RuntimeException failure) { return Result.rejected(ResultStatusCode.RETRY, "catchup.paid_travel.proof_unavailable", current); }
	}

	static boolean paidTravelConflictGenerationCurrent(PhantomBackgroundCatchupState current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
	{
		return PhantomBackgroundService.paidTravelConflictCandidate(current) && current.authorityHashes().equals(generation.authorityHashes())
			&& current.knowledgeGeneration() == generation.knowledgeGeneration() && current.topologyGeneration() == generation.topologyGeneration();
	}

	static boolean paidTravelKnowledgeRefreshEligible(PhantomBackgroundCatchupState current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
	{
		return PhantomBackgroundService.paidTravelConflictCandidate(current) && !current.authorityHashes().equals(generation.authorityHashes())
			&& PhantomBackgroundService.paidTravelKnowledgeCompatible(current.authorityHashes(), generation.authorityHashes())
			&& current.knowledgeGeneration() == generation.knowledgeGeneration() && current.topologyGeneration() == generation.topologyGeneration();
	}

	public static boolean requiresNativeMaterialization(String reason)
	{
		// Stock full-inventory combat can continue in the existing demand/claim guarded native lane.
		return (reason != null) && (reason.startsWith("native_context.required:") || reason.equals("transaction.native_context_required") || reason.equals("model.slot_capacity"));
	}

	private Result recoverNativeContext(long profileId, Snapshot current)
	{
		if ((current.state().status() != Status.FAILED_REPLAN_REQUIRED) || !requiresNativeMaterialization(current.state().failureReason())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_context.failure_not_owned", current); }
		final var generation = _planner.generation();
		if (!current.state().authorityHashes().equals(generation.authorityHashes()) || (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration()))
		{
			// Attestation needs current authority; preserve the same request through its existing CAS recovery first.
			return recoverStale(profileId, current, generation);
		}
		final Result attested = ensureNativeContext(profileId, current, _background.acquisitionSnapshot(profileId).orElse(null));
		if (!attested.successful()) { return attested; }
		try { return Result.success(_store.replace(profileId, current, current.state().retryRunning()), 0); }
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.retry_publish", _store.load(profileId).orElse(current)); }
	}

	/** Only classified recoverable failures may re-enter the same optimistic history ownership. */
	public static boolean isRecoverableFailure(String reason)
	{
		return Set.of("transaction.item_conflict", "model.object_cap", "model.object_cap_indivisible", "catchup.authority.unsupported", "planner.target_or_route.absent", "catchup.travel.anchor_mismatch", "catchup.travel.insufficient_adena", "authority.hash_stale", "catchup.authority.authority_stale", "catchup.authority.position_stale", "catchup.authority.target_stale", "catchup.authority.resource_stale", "catchup.authority.unsupported_loot").contains(reason);
	}

	/** Resolve durable ownership before baseline/FAILED/policy gates, without granting simulation eligibility. */
	private Result recoverColdLifecycle(long profileId, Snapshot current)
	{
		final var state = _background.acquisitionSnapshot(profileId).orElse(null);
		if ((state == null) || ((state.state() != PhantomBackgroundState.State.VERIFY_PENDING) && (state.state() != PhantomBackgroundState.State.MATERIALIZED)) || _materialization.find(profileId).isPresent()) { return Result.success(current, 0); }
		final boolean ownedReceipt = _profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent();
		final var recovered = _background.recoverAbandonedMaterialization(profileId);
		if (!recovered.successful()) { return Result.rejected(recovered.status() == OperationStatus.RETRY ? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED, "catchup.cold." + recovered.reason(), current); }
		final Snapshot latest = _store.load(profileId).orElse(null);
		if (!Objects.equals(current, latest)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.cold.claim_changed", latest); }
		if (ownedReceipt && (latest != null) && (latest.state().status() == Status.FAILED_REPLAN_REQUIRED) && "catchup.baseline.conflict".equals(latest.state().failureReason()))
		{
			final String remaining = recoveryPrerequisite(profileId, latest, _background.acquisitionSnapshot(profileId).orElse(null), _goals.load(profileId).orElse(null));
			if (!remaining.isEmpty() || !currentClaim(profileId, latest)) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, remaining.isEmpty() ? "catchup.cold.claim_changed" : remaining, latest); }
			try { return Result.success(_store.replace(profileId, latest, latest.state().retryRunning()), 0); }
			catch (RuntimeException failure) { return Result.rejected(ResultStatusCode.RETRY, "catchup.cold.publish_retry", _store.load(profileId).orElse(latest)); }
		}
		return Result.success(latest, 0);
	}

	private String recoveryPrerequisite(long profileId, Snapshot current, PhantomBackgroundState state, StoredGoal goal)
	{
		if (state == null) { return "catchup.recovery.background_missing"; }
		final var profile = _profiles.find(profileId).orElse(null);
		if ((profile == null) || (profile.characterObjectId() == null) || (state.identity().profileId() != profileId) || (state.identity().characterObjectId() != profile.characterObjectId())) { return "catchup.recovery.background_state_invalid"; }
		if (goal == null) { return "catchup.recovery.goal_missing"; }
		if (goal.goal().goalId() != current.state().goalId()) { return "catchup.recovery.goal_id_mismatch"; }
		if (goal.goal().revision() != current.state().goalRevision()) { return "catchup.recovery.goal_revision_mismatch"; }
		if (goal.goal().status() != PhantomGoalStatus.ACTIVE) { return "catchup.recovery.goal_status_invalid"; }
		if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) && !PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(goal.goal().goalType())) { return "catchup.recovery.goal_type_invalid"; }
		if ((state.state() != PhantomBackgroundState.State.READY) && (state.state() != PhantomBackgroundState.State.DEAD)) { return "catchup.recovery.background_state_invalid"; }
		return "";
	}

	private Result restorePrerequisite(long profileId, Snapshot current, PhantomBackgroundState state, StoredGoal goal)
	{
		final String missing = recoveryPrerequisite(profileId, current, state, goal);
		if (missing.isEmpty()) { return Result.success(current, 0); }
		final Result restored;
		switch (missing)
		{
			case "catchup.recovery.background_missing" -> restored = refreshCanonicalBaseline(profileId, current);
			case "catchup.recovery.goal_missing" -> restored = restoreMissingGoal(profileId, current, state);
			case "catchup.recovery.goal_revision_mismatch" -> restored = replanStaleGoal(profileId, current, state, goal);
			case "catchup.recovery.background_state_invalid" ->
			{
				if ((state != null) && (state.state() == PhantomBackgroundState.State.VERIFY_PENDING) && (goal != null))
				{
					final var reconciled = _background.reconcileHistoricalPending(profileId, goal.goal(), current.state().generation(), Math.addExact(current.state().intervalOrdinal(), 1));
					restored = reconciled.successful() ? Result.success(current, 0) : Result.rejected(reconciled.status() == OperationStatus.RETRY ? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED, missing, current);
				}
				else if ((state != null) && (state.state() == PhantomBackgroundState.State.MATERIALIZED))
				{
					final var recovered = _background.recoverAbandonedMaterialization(profileId);
					restored = recovered.successful() ? Result.success(current, 0) : Result.rejected(recovered.status() == OperationStatus.RETRY ? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED, missing, current);
				}
				else { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, missing, current); }
			}
			default -> { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, missing, current); }
		}
		if (!restored.successful()) { return restored; }
		final Snapshot updated = _store.load(profileId).orElse(restored.snapshot());
		final String remaining = recoveryPrerequisite(profileId, updated, _background.acquisitionSnapshot(profileId).orElse(null), _goals.load(profileId).orElse(null));
		return remaining.isEmpty() ? Result.success(updated, 0) : Result.rejected(ResultStatusCode.REPLAN_REQUIRED, remaining, updated);
	}

	private Result restoreMissingGoal(long profileId, Snapshot current, PhantomBackgroundState state)
	{
		if ((state == null) || (state.state() != PhantomBackgroundState.State.READY)) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.recovery.background_state_invalid", current); }
		if ((current.state().cursorEpochMinute() != current.state().fromEpochMinute()) || (current.state().intervalOrdinal() != 0)) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.recovery.goal_missing", current); }
		try
		{
			final var generation = _planner.generation();
			final Snapshot pending = _store.replace(profileId, current, current.state().reopenUnplanned(generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes()));
			return ensureBaseline(profileId, pending);
		}
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.pending_publish_retry", _store.load(profileId).orElse(current)); }
	}

	private Result replanStaleGoal(long profileId, Snapshot current, PhantomBackgroundState state, StoredGoal goal)
	{
		if ((state == null) || (goal == null) || ((state.state() != PhantomBackgroundState.State.READY) && (state.state() != PhantomBackgroundState.State.DEAD))) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.recovery.background_state_invalid", current); }
		final long ordinal = Math.addExact(current.state().planOrdinal(), 1);
		final var plan = planOrIdle(profileId, state, goal.goal(), current.state().deterministicSeed(), ordinal, Set.of());
		if (!plan.ready()) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, plan.reasonKey(), current); }
		try
		{
			final PhantomBackgroundCatchupState replacement = current.state().withPlan(plan.goal().goalId(), plan.goal().revision(), ordinal, plan.planIdentity(), plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration(), plan.generation().authorityHashes());
			return Result.success(_store.replacePlan(profileId, current, replacement, goal, plan.goal()).catchup(), 0);
		}
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.persistence_retry", _store.load(profileId).orElse(current)); }
	}

	private Result recoverKnown(long profileId, Snapshot current)
	{
		String reason = current.state().failureReason();
		if (!isRecoverableFailure(reason)) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, reason, current); }
		if (current.state().goalId() == 0)
		{
			return recoverStale(profileId, current, _planner.generation());
		}
		PhantomBackgroundState state = _background.acquisitionSnapshot(profileId).orElse(null);
		StoredGoal goal = _goals.load(profileId).orElse(null);
		final Result prerequisite = restorePrerequisite(profileId, current, state, goal);
		if (!prerequisite.successful()) { return prerequisite; }
		current = prerequisite.snapshot();
		if (current.state().status() == Status.RUNNING) { return Result.success(current, 0); }
		state = _background.acquisitionSnapshot(profileId).orElse(null);
		goal = _goals.load(profileId).orElse(null);
		if ("catchup.authority.unsupported".equals(reason) && (state.state() == PhantomBackgroundState.State.READY))
		{
			final var attempt = _background.historicalFarmAttempt(profileId, goal.goal());
			reason = attempt.successful() ? "model.object_cap" : PhantomBackgroundService.farmFailureReason(attempt);
			if (!isRecoverableFailure(reason)) { return fail(profileId, current, reason); }
		}
		if ("authority.hash_stale".equals(reason) || "catchup.authority.authority_stale".equals(reason)) { return recoverStale(profileId, current, _planner.generation()); }
		if ("catchup.authority.position_stale".equals(reason))
		{
			final Result refreshed = refreshCanonicalBaseline(profileId, current);
			if (!refreshed.successful()) { return refreshed; }
			state = _background.acquisitionSnapshot(profileId).orElseThrow();
		}
		if ("transaction.item_conflict".equals(reason) || reason.startsWith("model.object_cap") || (state.state() == PhantomBackgroundState.State.DEAD))
		{
			try { return Result.success(_store.replace(profileId, current, current.state().retryRunning()), 0); }
			catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.retry_publish", _store.load(profileId).orElse(current)); }
		}
		Set<String> exclusions = Set.of();
		if ("catchup.authority.target_stale".equals(reason) || "catchup.authority.unsupported_loot".equals(reason))
		{
			final var spec = PhantomBackgroundGoalSpec.parse(goal.goal());
			exclusions = Set.of(spec.npcId() + "@" + spec.anchorId());
		}
		Set<String> excludedSteps = Set.of();
		if ("catchup.travel.anchor_mismatch".equals(reason))
		{
			if (!currentClaim(profileId, current) || !Objects.equals(goal, _goals.load(profileId).orElse(null))
				|| _materialization.find(profileId).isPresent() || _acceptedIntent.test(profileId, goal.goal()))
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.failed_step_owner_changed", current); }
			final String failedStep = _background.historicalFailedTravelStep(state, goal.goal(), current.state().cursorEpochMinute());
			if (failedStep.isBlank()) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.failed_step_unproved", current); }
			excludedSteps = Set.of(failedStep);
		}
		final long ordinal = Math.addExact(current.state().planOrdinal(), 1);
		final var plan = planOrIdle(profileId, state, goal.goal(), current.state().deterministicSeed(), ordinal, exclusions, excludedSteps, "catchup.travel.insufficient_adena".equals(reason));
		if (!plan.ready()) { return fail(profileId, current, plan.reasonKey()); }
		try
		{
			if (!excludedSteps.isEmpty() && (!currentClaim(profileId, current) || !Objects.equals(goal, _goals.load(profileId).orElse(null))
				|| !Objects.equals(state, _background.acquisitionSnapshot(profileId).orElse(null)) || _materialization.find(profileId).isPresent()
				|| _acceptedIntent.test(profileId, goal.goal()) || !_planner.generation().equals(plan.generation())))
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.failed_step_prerequisite_changed", current); }
			final var renewed = current.state().withPlan(plan.goal().goalId(), plan.goal().revision(), ordinal, plan.planIdentity(), plan.generation().knowledgeGeneration(), plan.generation().topologyGeneration(), plan.generation().authorityHashes());
			final Snapshot persisted = _store.replacePlan(profileId, current, renewed, goal, plan.goal()).catchup();
			if (farmProjectionChanged(goal.goal(), plan.goal()) || ("catchup.authority.resource_stale".equals(reason) && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(plan.goal().goalType()))) { return refreshCanonicalBaseline(profileId, persisted); }
			return Result.success(persisted, 0);
		}
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.persistence_retry", _store.load(profileId).orElse(current)); }
	}

	private static boolean farmProjectionChanged(PhantomGoal previous, PhantomGoal replacement)
	{
		return PhantomBackgroundGoalSpec.GOAL_TYPE.equals(replacement.goalType()) && (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(previous.goalType()) || !PhantomBackgroundGoalSpec.parse(previous).equals(PhantomBackgroundGoalSpec.parse(replacement)));
	}

	private PhantomHistoricalBackgroundPlanner.Result planOrIdle(long profileId, PhantomBackgroundState state, PhantomGoal previous, long seed, long ordinal, Set<String> excludedTargets)
	{
		return planOrIdle(profileId, state, previous, seed, ordinal, excludedTargets, false);
	}

	private PhantomHistoricalBackgroundPlanner.Result planOrIdle(long profileId, PhantomBackgroundState state, PhantomGoal previous, long seed, long ordinal, Set<String> excludedTargets, boolean affordableTravel)
	{
		return planOrIdle(profileId, state, previous, seed, ordinal, excludedTargets, Set.of(), affordableTravel);
	}

	private PhantomHistoricalBackgroundPlanner.Result planOrIdle(long profileId, PhantomBackgroundState state, PhantomGoal previous, long seed, long ordinal,
		Set<String> excludedTargets, Set<String> excludedSteps, boolean affordableTravel)
	{
		if (_acceptedIntent.test(profileId, previous)) { return PhantomHistoricalBackgroundPlanner.Result.blocked("native_context.required:accepted_journey"); }
		final var logical = logicalGroup(profileId, previous);
		if (logical != null) { return _planner.replanLogicalRendezvous(profileId, state, previous, seed, ordinal, logical.objective(), excludedTargets, excludedSteps, affordableTravel); }
		var plan = affordableTravel ? _planner.replaceAffordableFromState(profileId, state, previous, seed, ordinal)
			: excludedTargets.isEmpty() && excludedSteps.isEmpty() ? _planner.replaceFromState(profileId, state, previous, seed, ordinal) : _planner.replan(profileId, state, previous, seed, ordinal, excludedTargets, excludedSteps);
		if ("planner.target_or_route.absent".equals(plan.reasonKey())) { plan = _planner.idleFromState(profileId, state, previous, seed, ordinal); }
		return plan;
	}

	/** Shared planning stage of advance(); the interval transaction still owns time and all rewards.
	 * Absolute durable minutes stagger finite retries and survive both renewal and process restart. */
	static PhantomHistoricalBackgroundPlanner.Result reconsiderHistorical(PhantomHistoricalBackgroundPlanner planner, long profileId, PhantomBackgroundState state, PhantomGoal goal, PhantomBackgroundCatchupState catchup, java.util.function.BooleanSupplier acceptedIntent)
	{
		final var generation = planner.generation();
		if (state.identity().profileId() != profileId || catchup.status() != Status.RUNNING || goal.goalId() != catchup.goalId() || goal.revision() != catchup.goalRevision()
			|| goal.status() != PhantomGoalStatus.ACTIVE || !state.hashes().equals(catchup.authorityHashes()) || !generation.authorityHashes().equals(catchup.authorityHashes())
			|| generation.knowledgeGeneration() != catchup.knowledgeGeneration() || generation.topologyGeneration() != catchup.topologyGeneration())
		{ return PhantomHistoricalBackgroundPlanner.Result.blocked("catchup.replan.prerequisite_changed"); }
		final boolean idle = PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, goal);
		final boolean suitable = planner.remainsSuitable(state, goal);
		final boolean due = idle && Math.floorMod(catchup.cursorEpochMinute(), IDLE_RECHECK_MINUTES) == Math.floorMod(profileId, IDLE_RECHECK_MINUTES);
		if (!suitable || due)
		{
			if (acceptedIntent.getAsBoolean()) { return PhantomHistoricalBackgroundPlanner.Result.blocked("native_context.required:accepted_journey"); }
			final long ordinal = Math.addExact(catchup.planOrdinal(), 1);
			var replacement = planner.replaceFromState(profileId, state, goal, catchup.deterministicSeed(), ordinal);
			if ("planner.target_or_route.absent".equals(replacement.reasonKey()))
			{
				// An unchanged absence is not a new goal or plan. In particular do not reset
				// the cursor/cadence merely because another catch-up request was opened.
				if (!idle || !suitable) { replacement = planner.idleFromState(profileId, state, goal, catchup.deterministicSeed(), ordinal); }
				else { replacement = null; }
			}
			if (!planner.generation().equals(generation)) { return PhantomHistoricalBackgroundPlanner.Result.blocked("catchup.replan.generation_changed"); }
			if (replacement != null) { return replacement; }
		}
		return new PhantomHistoricalBackgroundPlanner.Result(goal, PhantomBackgroundGoalSpec.parseLifecycle(goal), catchup.planIdentity(), generation, List.of(), "planner.historical_hold");
	}

	static ResultStatusCode historicalPlanningFailureStatus(String reason)
	{
		return "catchup.replan.prerequisite_changed".equals(reason) || "catchup.replan.generation_changed".equals(reason) ? ResultStatusCode.RETRY : ResultStatusCode.REPLAN_REQUIRED;
	}


	private Result recoverStale(long profileId, Snapshot current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
	{
		return recoverStale(profileId, current, generation, null);
	}

	private Result recoverStale(long profileId, Snapshot current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation, PhantomBackgroundState exactBaseline)
	{
		if ((current.state().status() == Status.FAILED_REPLAN_REQUIRED) && (current.state().goalId() == 0) && (current.state().cursorEpochMinute() == current.state().fromEpochMinute()) && (current.state().intervalOrdinal() == 0))
		{
			try
			{
				final Snapshot pending = _store.replace(profileId, current, current.state().retryPending(generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes()));
				return ensureBaseline(profileId, pending);
			}
			catch (RuntimeException exception)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.pending_publish_retry", _store.load(profileId).orElse(current));
			}
		}
		PhantomBackgroundState backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
		StoredGoal storedGoal = _goals.load(profileId).orElse(null);
		final Result prerequisite;
		if (exactBaseline != null)
		{
			if (!exactBaseline.equals(backgroundState) || backgroundState.state() != PhantomBackgroundState.State.READY || !currentClaim(profileId, current)
				|| !_planner.generation().equals(generation) || !recoveryPrerequisite(profileId, current, backgroundState, storedGoal).isEmpty())
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.paid_travel.prerequisite_changed", current); }
			final var proof = _background.historicalNativeContext(profileId, backgroundState);
			if (proof.status() != PhantomBackgroundTransaction.Status.SUCCESS || !backgroundState.equals(proof.state()) || proof.context() == null
				|| proof.context().phase() != PhantomNativeContext.Phase.COMPLETED || proof.context().afterPolicy() == null
				|| !proof.context().permits(PhantomBackgroundSimulationPolicy.Operation.TRAVEL, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint()))
			{ return Result.rejected(ResultStatusCode.RETRY, "catchup.paid_travel.context_changed", current); }
			prerequisite = Result.success(current, 0);
		}
		else { prerequisite = restorePrerequisite(profileId, current, backgroundState, storedGoal); }
		if (!prerequisite.successful()) { return prerequisite; }
		current = prerequisite.snapshot();
		if (canResumeGeneration(current.state(), generation)) { return Result.success(current, 0); }
		backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
		storedGoal = _goals.load(profileId).orElse(null);
		if (!backgroundState.hashes().equals(generation.authorityHashes()))
		{
			final Result refresh = refreshCanonicalBaseline(profileId, current);
			if (!refresh.successful())
			{
				return refresh;
			}
			backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
			if ((backgroundState == null) || ((backgroundState.state() != PhantomBackgroundState.State.READY) && (backgroundState.state() != PhantomBackgroundState.State.DEAD)) || !backgroundState.hashes().equals(generation.authorityHashes()))
			{
				return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.recovery.canonical_refresh_unverified", current);
			}
		}
		final long nextPlanOrdinal = Math.addExact(current.state().planOrdinal(), 1);
		final var replanned = planOrIdle(profileId, backgroundState, storedGoal.goal(), current.state().deterministicSeed(), nextPlanOrdinal, Set.of());
		if (!replanned.ready())
		{
			try
			{
				final Snapshot blocked = _store.replace(profileId, current, current.state().blockedForGeneration(replanned.reasonKey(), generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes()));
				return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, replanned.reasonKey(), blocked);
			}
			catch (RuntimeException exception)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.block_publish_retry", _store.load(profileId).orElse(current));
			}
		}
		if (!_planner.generation().equals(generation))
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.generation_changed", current);
		}
		try
		{
			final PhantomBackgroundCatchupState renewed = current.state().withPlan(replanned.goal().goalId(), replanned.goal().revision(), nextPlanOrdinal, replanned.planIdentity(), generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes());
			return Result.success(_store.replacePlan(profileId, current, renewed, storedGoal, replanned.goal()).catchup(), 0);
		}
		catch (RuntimeException exception)
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.persistence_retry", _store.load(profileId).orElse(current));
		}
	}

	/** A hash-equivalent reload still has to renew knowledge/topology ownership before attestation. */
	static boolean canResumeGeneration(PhantomBackgroundCatchupState current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
	{
		return current.status() == Status.RUNNING && current.authorityHashes().equals(generation.authorityHashes())
			&& current.knowledgeGeneration() == generation.knowledgeGeneration() && current.topologyGeneration() == generation.topologyGeneration();
	}

	/** An offline DEAD return uses the existing exact claim-owned baseline before NORMAL recovery. */
	boolean nativeRecoveryControlPending(long profileId) { return _nativeRecoveryRefreshes.containsKey(profileId); }

	static boolean visibleIdleClaimAccepted(Status status, java.util.function.BooleanSupplier exactHandoff)
	{
		return status == Status.COMPLETE || status == Status.RUNNING && exactHandoff.getAsBoolean();
	}
	static boolean visibleIdleClaimAccepted(Status status, String failureReason, boolean acceptedSharedPreparation, java.util.function.BooleanSupplier exactHandoff)
	{
		if (status != Status.FAILED_REPLAN_REQUIRED) { return visibleIdleClaimAccepted(status, exactHandoff); }
		return acceptedSharedPreparation && Set.of("native_context.required:logical_party_preparation", "native_context.required:logical_party_support").contains(failureReason)
			&& exactHandoff.getAsBoolean();
	}

	static boolean nativeRecoveryGoalAccepted(long profileId, PhantomGoal expected, PhantomGoal actual)
	{
		return expected != null && expected.status() == PhantomGoalStatus.ACTIVE
			&& PhantomBackgroundService.recoveryGoalCurrent(profileId, expected, actual);
	}

	/** The accepted background control retains the existing baseline claim across nonblocking cleanup. */
	Result prepareNativeRecoveryControl(long profileId, PhantomGoal expectedGoal, long deadline)
	{
		var refresh = _nativeRecoveryRefreshes.get(profileId);
		if (refresh == null)
		{
			final var goal = _goals.load(profileId).orElse(null);
			if (!nativeRecoveryGoalAccepted(profileId, expectedGoal, goal == null ? null : goal.goal())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_recovery.goal_mismatch", null); }
			final var lifetime = _materialization.find(profileId).orElse(null);
			if (lifetime != null) { return lifetime.actionAdmissionOpen() ? Result.success(null, 0) : Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.owner_draining", null); }
			final var baseline = _background.acquisitionSnapshot(profileId).orElse(null);
			if (baseline == null) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.background_missing", null); }
			final var generation = _planner.generation();
			if ((baseline.state() != PhantomBackgroundState.State.DEAD) || baseline.hashes().equals(generation.authorityHashes())) { return Result.success(null, 0); }
			final var current = _store.load(profileId).orElse(null);
			if (!nativeRecoveryGoalAccepted(profileId, expectedGoal, goal == null ? null : goal.goal())
				|| (current == null) || (current.state().status() != Status.COMPLETE) || !currentClaim(profileId, current) || (current.state().goalId() != expectedGoal.goalId()) || (current.state().goalRevision() != expectedGoal.revision())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_recovery.claim_goal_mismatch", current); }
			final var recovery = new RecoveryClaim(current, _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null));
			if (_recoveryClaims.putIfAbsent(profileId, recovery) != null) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.baseline_busy", current); }
			refresh = new NativeRecoveryRefresh(recovery, generation);
			_nativeRecoveryRefreshes.put(profileId, refresh);
			final var loaded = _materialization.materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, current.state().requestId());
			if (loaded.status() != ResultStatus.SUCCESS)
			{
				if (_materialization.find(profileId).isEmpty()) { _nativeRecoveryRefreshes.remove(profileId, refresh); _recoveryClaims.remove(profileId, recovery); }
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.materialize_" + loaded.status(), current);
			}
			final var entry = _materialization.find(profileId).orElseThrow(); refresh.objectId = entry.characterObjectId(); refresh.epoch = entry.materializedAtNanos();
		}
		final var recovery = refresh.recovery;
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime != null) && ((lifetime.characterObjectId() != refresh.objectId) || (lifetime.materializedAtNanos() != refresh.epoch))) { throw new IllegalStateException("catchup.native_recovery.baseline_owner_changed"); }
		final var stored = _materialization.requestDematerialize(profileId, deadline);
		if (stored.status() == ResultStatus.CLEANUP_PENDING) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.control_pending", recovery.snapshot()); }
		if ((stored.status() != ResultStatus.SUCCESS) && (stored.status() != ResultStatus.NOT_ACTIVE)) { throw new IllegalStateException("catchup.native_recovery.baseline_store_" + stored.status()); }
		final var state = _background.acquisitionSnapshot(profileId).orElse(null);
		final boolean exact = _materialization.find(profileId).isEmpty() && (state != null) && (state.state() == PhantomBackgroundState.State.DEAD) && state.hashes().equals(refresh.generation.authorityHashes())
			&& _planner.generation().equals(refresh.generation) && currentClaim(profileId, recovery.snapshot())
			&& Objects.equals(recovery.claim(), _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null)) && Objects.equals(recovery.goal(), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null));
		_nativeRecoveryRefreshes.remove(profileId, refresh); _recoveryClaims.remove(profileId, recovery);
		if (!exact) { throw new IllegalStateException("catchup.native_recovery.baseline_unverified"); }
		return Result.success(recovery.snapshot(), 0);
	}

	/** Synchronous external compatibility route; scheduler/native requests use the control route above. */
	public Result prepareNativeRecovery(long profileId, PhantomGoal expectedGoal)
	{
		try
		{
			final var stored = _goals.load(profileId).orElse(null);
			if (!nativeRecoveryGoalAccepted(profileId, expectedGoal, stored == null ? null : stored.goal())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_recovery.goal_mismatch", null); }
			final var lifetime = _materialization.find(profileId).orElse(null);
			if (lifetime != null) { return lifetime.actionAdmissionOpen() ? Result.success(null, 0) : Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.owner_draining", null); }
			final var baseline = _background.acquisitionSnapshot(profileId).orElse(null);
			if (baseline == null) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.background_missing", null); }
			if (baseline.state() != PhantomBackgroundState.State.DEAD) { return Result.success(null, 0); }
			final var generation = _planner.generation();
			if (baseline.hashes().equals(generation.authorityHashes())) { return Result.success(null, 0); }
			final var current = _store.load(profileId).orElse(null);
			if ((current == null) || (current.state().status() != Status.COMPLETE) || !currentClaim(profileId, current)
				|| (current.state().goalId() != expectedGoal.goalId()) || (current.state().goalRevision() != expectedGoal.revision())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_recovery.claim_goal_mismatch", current); }
			final var goalComponent = _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
			final var refresh = refreshCanonicalBaseline(profileId, current);
			if (!refresh.successful()) { return refresh; }
			final var refreshed = _background.acquisitionSnapshot(profileId).orElse(null);
			final boolean exact = (refreshed != null) && (refreshed.state() == PhantomBackgroundState.State.DEAD) && refreshed.hashes().equals(generation.authorityHashes())
				&& _planner.generation().equals(generation) && currentClaim(profileId, current) && _materialization.find(profileId).isEmpty()
				&& Objects.equals(goalComponent, _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null));
			return exact ? Result.success(current, 0) : Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.refresh_unverified", current);
		}
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_recovery.persistence_retry", null); }
	}

	private Result refreshCanonicalBaseline(long profileId, Snapshot current)
	{
		if (nativeIntentPending(profileId)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.authority.intent_pending", current); }
		if (!currentClaim(profileId, current)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.claim_changed", current); }
		final var claimComponent = _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
		final var goalComponent = _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
		final var guards = new java.util.LinkedHashMap<String, Optional<PhantomProfileComponent>>();
		for (String type : List.of(PhantomOwnedStoreIntent.COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE,
			org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE, "learning.intent", "career.intent", "supply.intent"))
		{ guards.put(type, _profiles.findComponent(profileId, type)); }
		final var recovery = new RecoveryClaim(current, claimComponent, goalComponent, Map.copyOf(guards));
		if (_materialization.find(profileId).isPresent() || (_recoveryClaims.putIfAbsent(profileId, recovery) != null))
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialization_busy", current);
		}
		try
		{
			if (!currentRecoveryClaim(profileId, recovery)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.claim_changed", current); }
			final var materialized = _materialization.materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, current.state().requestId());
			if (materialized.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialize_" + materialized.status().name().toLowerCase(), current);
			}
			final var dematerialized = _materialization.dematerialize(profileId);
			if (dematerialized.status() != ResultStatus.SUCCESS) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.store_retry", current); }
			if (_materialization.find(profileId).isPresent() || !currentClaim(profileId, current)
				|| !currentRecoveryClaim(profileId, recovery)
				|| !Objects.equals(claimComponent, _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null))
				|| !Objects.equals(goalComponent, _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.claim_goal_or_owner_changed", current);
			}
			return Result.success(current, 0);
		}
		finally
		{
			_recoveryClaims.remove(profileId, recovery);
		}
	}

	private boolean currentClaim(long profileId, Snapshot expected)
	{
		final var component = _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
		return (component != null) && (component.profileId() == profileId) && (component.componentSchemaVersion() == PhantomBackgroundCatchupState.SCHEMA_VERSION)
			&& (component.rowVersion() == expected.rowVersion()) && MessageDigest.isEqual(component.payload(), new PhantomBackgroundCatchupStateCodec().encode(expected.state()));
	}

	private boolean currentRecoveryClaim(long id, RecoveryClaim recovery)
	{
		return currentClaim(id, recovery.snapshot()) && Objects.equals(recovery.goal(), _profiles.findComponent(id, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null))
			&& recovery.guards().entrySet().stream().allMatch(entry -> entry.getValue().equals(_profiles.findComponent(id, entry.getKey())));
	}

	public Optional<Snapshot> status(long profileId)
	{
		try
		{
			return _store.load(profileId);
		}
		catch (RuntimeException exception)
		{
			return Optional.empty();
		}
	}

	/** Reuses the catch-up planner when a materialized Player outgrows its exact farm target. */
	public boolean replanVisibleFarmIfOutgrown(long profileId, PhantomGoal goal, PhantomDecisionEngine decision)
	{
		return (decision != null) ? prepareVisibleDecision(profileId, decision) : visibleFarmReady(profileId, goal);
	}

	public void bindVisibleRecovery(PhantomVisibleFarmTravel travel, PhantomVisibleAutoPlay autoPlay)
	{
		_visibleTravel = Objects.requireNonNull(travel);
		_visibleAutoPlay = Objects.requireNonNull(autoPlay);
		travel.bindRouteExclusions(profileId ->
		{
			final var failures = _visibleFailures.get(profileId);
			final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
			return failures == null ? Set.of() : failures.exclusions(_visibleClock.getAsLong(), epoch).steps();
		});
	}

	/** Handler-side suitability is read-only: publication waits until accept releases inFlight. */
	public boolean visibleFarmReady(long profileId, PhantomGoal goal)
	{
		final var failures = _visibleFailures.get(profileId);
		final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
		final var episode = _visibleEpisodes.get(profileId);
		if ((episode != null) && (episode.epoch == epoch) && (_visibleClock.getAsLong() < episode.cooldownUntil)) { return visibleFarmResult(profileId, goal, epoch, false, "visible.cooldown"); }
		if (failures != null)
		{
			if (failures.protocolBlocked(goal, epoch)) { return visibleFarmResult(profileId, goal, epoch, false, "visible.protocol_blocked"); }
			if (failures.routeBlocked(goal, epoch) && !freshLocalAction(profileId, goal, epoch)) { return visibleFarmResult(profileId, goal, epoch, false, "visible.route_blocked"); }
			if (failures.exclusions(_visibleClock.getAsLong(), epoch).targets().contains(targetKey(goal))) { return visibleFarmResult(profileId, goal, epoch, false, "visible.target_excluded"); }
		}
		final boolean ready = Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), goal);
		return visibleFarmResult(profileId, goal, epoch, ready, ready ? "visible.ready" : "visible.stored_goal_mismatch");
	}

	/** Fresh trips start at a native town, local farm or accepted dependency; existing children keep their saved origin. */
	public boolean canStartNativeJourney(PhantomGoal root, Player player)
	{
		if (root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())
			|| player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.WATER)) { return false; }
		try
		{
			// A genuine life need may be selected while our exact farm session still approaches its current target.
			// Its owner must yield that target/loot before the journey executor can issue movement.
			if (player.isMoving() && (_visibleAutoPlay == null || root.subject() == null || !"profile".equals(root.subject().namespace())
				|| !_visibleAutoPlay.running(Long.parseLong(root.subject().key()), root))) { return false; }
			return org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(player)
				|| _planner.isVisibleLocal(root, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()))
				|| root.subject() != null && "profile".equals(root.subject().namespace()) && _acceptedIntent.test(Long.parseLong(root.subject().key()), root);
		}
		catch (IllegalArgumentException invalidRoot) { return false; }
	}

	private boolean freshLocalAction(long profileId, PhantomGoal goal, long epoch)
	{
		return _visibleTravel != null && Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), goal)
			&& _visibleTravel.hasFreshLocalAction(profileId, goal, epoch);
	}

	/** First result of the real evaluation; no second read, action, I/O or diagnostic exception. */
	private static boolean visibleFarmResult(long profileId, PhantomGoal goal, long epoch, boolean ready, String reason)
	{
		try
		{
			org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance().record(profileId, "VISIBLE_FARM_READINESS", ready ? "READY" : "DECLINED", "", reason, epoch, goal == null ? 0 : goal.goalId(), goal == null ? 0 : goal.revision());
		}
		catch (Throwable ignored) { }
		return ready;
	}

	/** The scheduler boundary owns sync and recovery, outside gameplay/decision locks. */
	public boolean prepareVisibleDecision(long profileId, PhantomDecisionEngine decision)
	{
		return prepareVisibleDecision(profileId, decision, null, null, 0, () -> true);
	}

	/** Capture one bounded control-plane preview. It never adopts a root, advances history or issues a native action. */
	/** Same durable evidence for production discovery, CAS revalidation and native preflight. */
	public String potentialPartyRole(long id, PhantomBackgroundState state)
	{
		if (state == null || state.identity().profileId() != id) { return "pending.native_role"; }
		final var ids = _planner.partyRoleSkillIds(state); final var learned = new java.util.HashMap<Integer, Integer>();
		for (int offset = 0; offset < ids.size(); offset += 8)
		{
			final var evidence = _background.acquisitionEligibility(id, state, ids.subList(offset, Math.min(offset + 8, ids.size())), state.hashes().progression()).orElse(null);
			if (evidence == null) { return "pending.native_role"; }
			learned.putAll(evidence.skillLevels());
		}
		return _planner.potentialPartyRole(state, learned);
	}
	/** Durable social discovery reads bounded background/profile facts and requires no World Player or native lease. */
	public org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator.Candidate previewLogicalRendezvous(long id,
		PhantomDecisionEngine decision, java.util.function.BooleanSupplier eligible, List<PhantomProfileComponent> populationGuards)
	{
		if (_foregroundDecisionsRevoked || !eligible.getAsBoolean()) { return null; }
		final var stored = _goals.load(id).orElse(null); final var state = _background.acquisitionSnapshot(id).orElse(null); final var history = _store.load(id).orElse(null);
		if (stored == null || state == null || history == null || stored.goal().status() != PhantomGoalStatus.ACTIVE || state.identity().profileId() != id
			|| state.progress().level() < 40 || state.position().instanceId() != 0 || state.state() != PhantomBackgroundState.State.READY && state.state() != PhantomBackgroundState.State.MATERIALIZED)
		{ return null; }
		final var root = stored.goal(); final var generation = _planner.generation();
		if (!state.hashes().equals(generation.authorityHashes()) || !(PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()) || PhantomBackgroundGoalSpec.canonicalHistoricalIdle(id, root))) { return null; }
		final String role = potentialPartyRole(id, state);
		if (role.equals("pending.native_role")) { previewStage(id, "PENDING_NATIVE_ROLE"); return null; }
		final java.util.function.BooleanSupplier current = () -> !_foregroundDecisionsRevoked && eligible.getAsBoolean() && _planner.generation().equals(generation)
			&& decision.currentGoal(id).map(root::equals).orElse(true);
		return new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator.Candidate(
			new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(id, role, root.goalId(), root.revision()), state.progress().level(), List.of(),
			source -> _planner.partySourceAvailable(state, source, Set.of()),
			current,
			// Re-read durable role evidence only for selected members, at preview and again before CAS.
			() -> current.getAsBoolean() && _goals.load(id).filter(stored::equals).isPresent() && _background.acquisitionSnapshot(id).filter(state::equals).isPresent()
				&& role.equals(potentialPartyRole(id, state)) && logicalOwnershipClear(id),
			admitted -> _planner.rendezvousSourcesForRole(id, state, root, history.state().deterministicSeed(), history.state().planOrdinal(), Set.of(), Set.of(), role, admitted),
			() -> logicalFormationGuards(id, stored, state, history, populationGuards),
			source -> _planner.partyRouteCost(state, source));
	}

	private boolean logicalOwnershipClear(long id)
	{
		return !nativeIntentPending(id) && _profiles.findComponent(id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty()
			&& new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(_profiles).find(id).filter(row ->
				!row.receipt().state().terminal() || row.receipt().state() == org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.INCONSISTENT).isEmpty();
	}

	private List<PhantomProfileComponent> logicalFormationGuards(long id, StoredGoal goal, PhantomBackgroundState state, Snapshot history,
		List<PhantomProfileComponent> populationGuards)
	{
		final var goalRow = _profiles.findComponent(id, PhantomGoalStateStore.COMPONENT_TYPE).filter(row -> row.rowVersion() == goal.rowVersion());
		final var backgroundRow = _profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE)
			.filter(row -> new PhantomBackgroundStateCodec().decode(row.payload()).equals(state));
		final var historyRow = _profiles.findComponent(id, PhantomBackgroundCatchupState.COMPONENT_TYPE).filter(row -> row.rowVersion() == history.rowVersion());
		if (goalRow.isEmpty() || backgroundRow.isEmpty() || historyRow.isEmpty()) { throw new java.util.ConcurrentModificationException("Logical formation inputs changed."); }
		final var guards = new java.util.ArrayList<>(List.of(goalRow.get(), backgroundRow.get(), historyRow.get()));
		guards.addAll(populationGuards);
		_profiles.findComponent(id, PhantomNativeContext.COMPONENT_TYPE).ifPresent(context ->
		{
			if (PhantomNativeContext.decode(context.payload()).phase() != PhantomNativeContext.Phase.COMPLETED)
			{ throw new java.util.ConcurrentModificationException("Logical formation waits for native context recovery."); }
			guards.add(context);
		});
		return List.copyOf(guards);
	}

	public org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.SourcePreview previewVisibleRendezvous(
		org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef actor, long epoch, PhantomGoal expectedGoal, PhantomDecisionEngine decision)
	{
		final long id = actor.profileId();
		String stage = "foreground";
		try
		{
			if (_foregroundDecisionsRevoked) { return previewUnavailable(id, "foreground_revoked"); }
			if (epoch <= 0 || expectedGoal == null || expectedGoal.status() != PhantomGoalStatus.ACTIVE) { return previewUnavailable(id, "expected_epoch_or_goal"); }
			if (!decision.currentGoal(id).filter(expectedGoal::equals).isPresent()) { return previewUnavailable(id, "runtime_goal"); }
			stage = "history_readiness";
			if (!permitsDecision(id)) { return previewUnavailable(id, "history_not_ready"); }
			stage = "snapshot";
			final var stored = _goals.load(id).orElse(null); final var catchup = _store.load(id).orElse(null);
			final var baseline = _background.acquisitionSnapshot(id).orElse(null); final var lifetime = _materialization.find(id).orElse(null);
			final var generation = _planner.generation();
			if (stored == null || !stored.goal().equals(expectedGoal)) { return previewUnavailable(id, "stored_goal"); }
			if (catchup == null) { return previewUnavailable(id, "catchup_absent"); }
			if (baseline == null || baseline.state() != PhantomBackgroundState.State.MATERIALIZED) { return previewUnavailable(id, "baseline_absent_or_not_materialized"); }
			if (baseline.identity().profileId() != id || baseline.identity().characterObjectId() != actor.characterObjectId()) { return previewUnavailable(id, "baseline_identity"); }
			if (!baseline.hashes().equals(generation.authorityHashes())) { return previewUnavailable(id, "authority_hash"); }
			if (lifetime == null || lifetime.characterObjectId() != actor.characterObjectId() || lifetime.materializedAtNanos() != epoch || !lifetime.worldPresent() || !lifetime.actionAdmissionOpen())
			{ return previewUnavailable(id, "owner_identity_epoch_world_or_admission"); }
			final PhantomBackgroundState projected;
			stage = "lease";
			try (var action = _materialization.tryAcquireAction(id).orElse(null))
			{
				if (action == null) { return previewUnavailable(id, "lease_absent"); }
				if (action.player().getObjectId() != actor.characterObjectId() || action.player().getActiveClass() != baseline.identity().activeClassId()) { return previewUnavailable(id, "native_identity_or_class"); }
				if (action.player().isDead()) { return previewUnavailable(id, "native_dead"); }
				if (action.player().hasPendingOwnedStore()) { return previewUnavailable(id, "native_pending_store"); }
				stage = "projection";
				final var player = action.player(); final var skill = org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.offensiveSkill(player, null);
				final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, null, skill); final var old = baseline.loadout();
				final var loadout = new PhantomBackgroundState.Loadout(skill == null ? 0 : skill.getId(), skill == null ? 0 : skill.getLevel(), old.summonNpcId(), (int) Math.ceil(combat.requiredMp()), old.shotItemId(), old.shotsPerEncounter(), old.summonResourceItemId(), old.summonResourcesPerEncounter());
				projected = new PhantomBackgroundState(baseline.state(), baseline.identity(), new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath()), combat.vitals(), baseline.position(), combat.combat(), loadout, baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
			}
			stage = "exclusions";
			final var failures = _visibleFailures.get(id);
			final var excluded = failures == null ? new Exclusions(Set.of(), Set.of()) : failures.exclusions(_visibleClock.getAsLong(), epoch);
			final String context = "goal=" + expectedGoal.goalId() + ":" + expectedGoal.revision() + ";object=" + actor.characterObjectId() + ";epoch=" + epoch
				+ ";from=" + projected.position().committedAnchorId() + ";knowledgeGeneration=" + generation.knowledgeGeneration() + ";topologyGeneration=" + generation.topologyGeneration();
			previewStage(id, "captured;" + context);
			final java.util.function.BooleanSupplier current = () ->
			{
				// Decision's global monitor is released before the per-actor materialization snapshot is read.
				if (_foregroundDecisionsRevoked || _visiblePublications.containsKey(id) || !decision.currentGoal(id).filter(expectedGoal::equals).isPresent() || !_planner.generation().equals(generation))
				{ previewStage(id, "stale_goal_generation_or_foreground;" + context); return false; }
				final var live = _materialization.find(id).orElse(null);
				final boolean valid = live != null && live.characterObjectId() == actor.characterObjectId() && live.materializedAtNanos() == epoch && live.worldPresent() && live.actionAdmissionOpen();
				if (!valid) { previewStage(id, "stale_owner;" + context); }
				return valid;
			};
			return new org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.SourcePreview(
				() ->
				{
					if (!current.getAsBoolean()) { return List.of(); }
					final var sources = _planner.rendezvousSources(id, projected, expectedGoal, catchup.state().deterministicSeed(), Math.addExact(catchup.state().planOrdinal(), 1), excluded.targets(), excluded.steps());
					previewStage(id, "sources;" + context + ";count=" + sources.size() + ";values=" + sources); return sources;
				},
				source ->
				{
					if (!current.getAsBoolean()) { return false; }
					final boolean available = _planner.partySourceAvailable(projected, source, excluded.steps());
					if (!available) { previewStage(id, "source_route_unavailable;" + context + ";source=" + source); }
					return available;
				}, current,
				source ->
				{
					if (!current.getAsBoolean()) { return false; }
					final boolean absent = _planner.rendezvousRouteAbsent(projected, source);
					if (absent) { previewStage(id, "source_route_absent;" + context + ";source=" + source); }
					return absent;
				}, admitted -> current.getAsBoolean() ? _planner.rendezvousSources(id, projected, expectedGoal, catchup.state().deterministicSeed(), Math.addExact(catchup.state().planOrdinal(), 1), excluded.targets(), excluded.steps(), admitted) : List.of());
		}
		catch (RuntimeException unavailable) { return previewUnavailable(id, "exception." + stage + "." + unavailable.getClass().getSimpleName()); }
	}
	private org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.SourcePreview previewUnavailable(long id, String reason)
	{
		previewStage(id, "unavailable." + reason);
		return org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.SourcePreview.unavailable();
	}
	private void previewStage(long id, String value)
	{
		synchronized (_visiblePreparationStages)
		{
			if (_visiblePreparationStages.containsKey(id) || _visiblePreparationStages.size() < 1024) { _visiblePreparationStages.put(id, "rendezvous.preview." + value); }
		}
	}

	/** Adopt an accepted shared source through the existing quiet Background publication. */
	public boolean prepareVisibleRendezvous(long profileId, PhantomDecisionEngine decision, PhantomGoal expectedGoal,
		PhantomDomainRef sharedFarmSource, long expiresEpochMillis, java.util.function.BooleanSupplier reservationCurrent)
	{
		Objects.requireNonNull(expectedGoal, "expectedGoal");
		PhantomHistoricalBackgroundPlanner.rendezvousSource(sharedFarmSource);
		Objects.requireNonNull(reservationCurrent, "reservationCurrent");
		if (expiresEpochMillis <= System.currentTimeMillis()) { return false; }
		return prepareVisibleDecision(profileId, decision, expectedGoal, sharedFarmSource, expiresEpochMillis,
			() -> System.currentTimeMillis() < expiresEpochMillis && reservationCurrent.getAsBoolean());
	}

	private boolean prepareVisibleDecision(long profileId, PhantomDecisionEngine decision, PhantomGoal expectedGoal,
		PhantomDomainRef sharedFarmSource, long expiresEpochMillis, java.util.function.BooleanSupplier reservationCurrent)
	{
		String stage = "runtime";
		boolean prepared = false;
		try
		{
			if (sharedFarmSource != null && !rendezvousCurrent(profileId, expectedGoal, reservationCurrent)) { return false; }
			final var runtime = decision.find(profileId).orElse(null);
			if ((runtime == null) || runtime.inFlight() || runtime.persistenceInFlight()) { return false; }
			final var receipt = _visiblePublications.get(profileId);
			stage = "pending_publication";
			if (receipt != null) { if (sharedFarmSource != null) { return false; } prepared = finishVisiblePublication(profileId, decision, receipt); return prepared; }
			stage = "goal";
			final StoredGoal stored = _goals.load(profileId).orElse(null);
			if (stored == null) { return false; }
			if (sharedFarmSource != null && !Objects.equals(stored.goal(), expectedGoal)) { return false; }
			if (_foregroundDecisionsRevoked) { return false; }
			stage = "checkpoint";
			final var checkpoint = _background.continueVisibleCheckpoint(profileId, stored.goal()).orElse(null);
			if (checkpoint != null && checkpoint.outcome() != org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome.RESUME
				&& checkpoint.outcome() != org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome.RETRY_BEFORE_WRITE) { return false; }
			if (_background.hasVisibleOwnedStorePending(profileId, stored.goal()))
			{
				if (sharedFarmSource != null) { return false; }
				final var pending = _materialization.find(profileId).orElse(null);
				final var player = pending == null ? null : org.l2jmobius.gameserver.model.World.getInstance().getPlayer(pending.characterObjectId());
				if ((player == null) || (_background.resumeVisibleOwnedStore(profileId, player, stored.goal()).status() != PhantomBackgroundService.VisibleStoreStatus.SUCCESS)) { return false; }
			}
			stage = "permits_decision";
			if (!permitsDecision(profileId)) { return false; }
			stage = "native_handoff_completion";
			if (finishNativeContextHandoff(profileId, stored.goal())) { return false; }
			stage = "materialization";
			final var admitted = _materialization.find(profileId).orElse(null);
			if ((admitted == null) || !admitted.worldPresent() || !admitted.actionAdmissionOpen() || (admitted.cleanupPhase() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE)) { return false; }
			stage = "action_lease";
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if ((action == null) || (action.player().getObjectId() != admitted.characterObjectId()) || action.player().hasPendingOwnedStore()) { return false; }
			}
			stage = "reload";
			if (!runtimeMatches(runtime, stored))
			{
				if (_visibleTravel != null) { _visibleTravel.cancelSuperseded(profileId, stored.goal()); }
				if (_visibleAutoPlay != null) { _visibleAutoPlay.stop(profileId); }
				if ((decision.reload(profileId) != PhantomDecisionEngine.ReloadResult.RELOADED) || !runtimeMatches(decision.find(profileId).orElse(null), stored)
					|| !Objects.equals(stored, _goals.load(profileId).orElse(null)) || !permitsDecision(profileId)) { return false; }
			}
			final boolean historicalIdle = PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(stored.goal().goalType());
			if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(stored.goal().goalType()) && !historicalIdle) { prepared = sharedFarmSource == null; return prepared; }
			if (historicalIdle) { PhantomHistoricalBackgroundPlanner.visiblePredecessor(profileId, stored.goal()); }
			stage = "visible_intent";
			final boolean visibleIntent = _visibleIntent.test(profileId, stored.goal());
			if (holdsOrdinaryPartyRoot(sharedFarmSource != null, _acceptedPartyRoot.test(profileId, stored.goal()), _acceptedIntent.test(profileId, stored.goal()), visibleIntent))
			{
				stage = "accepted_rendezvous_root";
				prepared = nativePartyDecisionPrepared(false, false, _rendezvousTravelDecision.test(profileId, stored.goal()));
				return prepared; // Keep the accepted root, while its exact adopted travel reaches the ordinary Decision owner.
			}
			if (nativePartyDecisionPrepared(sharedFarmSource != null, _partyCommittedDecision.test(profileId, stored.goal()))) { prepared = true; return true; }
			if (!historicalIdle && visibleIntent)
			{
				prepared = sharedFarmSource == null || stored.goal().validSources().contains(sharedFarmSource) && _supportRendezvousTravel.test(profileId, stored.goal());
				return prepared;
			}
			stage = "lifetime";
			final var lifetime = _materialization.find(profileId).orElse(null);
			if (lifetime == null) { return false; }
			stage = "protocol";
			final var failures = _visibleFailures.get(profileId);
			if ((failures != null) && failures.protocolBlocked(stored.goal(), lifetime.materializedAtNanos())) { return false; }
			stage = "cooldown";
			final var episode = _visibleEpisodes.get(profileId);
			if ((episode != null) && (episode.epoch == lifetime.materializedAtNanos()) && (_visibleClock.getAsLong() < episode.cooldownUntil)) { return false; }
			stage = "baseline";
			var baseline = _background.acquisitionSnapshot(profileId).orElse(null);
			if (baseline == null) { return false; }
			final Snapshot catchup = _store.load(profileId).orElse(null);
			final var generation = _planner.generation();
			if (sharedFarmSource != null)
			{
				if (_acceptedIntent.test(profileId, stored.goal()) || !rendezvousCurrent(profileId, expectedGoal, reservationCurrent)) { return false; }
				if (_visibleAutoPlay != null && _visibleAutoPlay.running(profileId, stored.goal())
					&& !_visibleAutoPlay.yieldAfterCurrentTarget(profileId, expiresEpochMillis)) { return false; }
				// Busy native owners keep their current action; recruitment cannot cancel a pull, cast or store.
				try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
				{
					if (action == null || action.player().isInCombat() || action.player().isAttackingNow() || action.player().isCastingNow()
						|| action.player().isCastingSimultaneouslyNow() || action.player().getQueuedSkill() != null || action.player().hasPendingOwnedStore()
						|| _visibleAutoPlay != null && _visibleAutoPlay.running(profileId, stored.goal())) { return false; }
				}
				if (!rendezvousCurrent(profileId, expectedGoal, reservationCurrent)) { return false; }
			}
			if (historicalIdle)
			{
				stage = "historical_idle_claim";
				if (catchup == null || !visibleIdleClaimAccepted(catchup.state().status(), catchup.state().failureReason(), rendezvousTravelPreparation(profileId, stored.goal(), sharedFarmSource != null), () ->
					{
						final var idleHandoff = _foregroundHandoffs.get(profileId);
						return idleHandoff != null && exactForegroundHandoff(profileId, idleHandoff);
					}) || catchup.state().goalId() != stored.goal().goalId()
					|| catchup.state().goalRevision() != stored.goal().revision() || !currentClaim(profileId, catchup)
					|| baseline.state() != PhantomBackgroundState.State.MATERIALIZED || baseline.identity().characterObjectId() != lifetime.characterObjectId()
					|| _acceptedIntent.test(profileId, stored.goal())) { return false; }
				if (_visibleAutoPlay != null)
				{
					_visibleAutoPlay.prepareHistoricalIdleRest(profileId, stored.goal(), lifetime.characterObjectId(), lifetime.materializedAtNanos(),
						() -> currentClaim(profileId, catchup) && Objects.equals(stored, _goals.load(profileId).orElse(null))
							&& runtimeMatches(decision.find(profileId).orElse(null), stored) && !_acceptedIntent.test(profileId, stored.goal()));
				}
			}
			final boolean authorityRenewal = (catchup != null) && (catchup.state().status() == Status.COMPLETE)
				&& ((catchup.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (catchup.state().topologyGeneration() != generation.topologyGeneration())
					|| !catchup.state().authorityHashes().equals(generation.authorityHashes()) || !baseline.hashes().equals(generation.authorityHashes()));
			if (authorityRenewal)
			{
				stage = "authority_renewal";
				if ((catchup.state().goalId() != stored.goal().goalId()) || (catchup.state().goalRevision() != stored.goal().revision()) || !currentClaim(profileId, catchup)) { return false; }
				final var nativePlayer = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(lifetime.characterObjectId());
				if (nativePlayer == null || ((_visibleTravel != null) && !_visibleTravel.stopForRecovery(profileId, stored.goal(), nativePlayer.getObjectId(), lifetime.materializedAtNanos()))) { return false; }
				if (_visibleAutoPlay != null) { _visibleAutoPlay.stop(profileId); }
				if (!visibleActorQuiet(profileId, nativePlayer, lifetime.materializedAtNanos())) { return false; }
				// RESUME is verified against the current live owner/key and full native/state/PNC facts.
				// COMPLETED PNC clears its pending epoch and cannot prove live ownership alone.
				if ((checkpoint == null || checkpoint.outcome() != org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome.RESUME)
					&& !_background.captureVisibleArrival(profileId, nativePlayer, stored.goal(), baseline.position().committedAnchorId())) { return false; }
				baseline = _background.acquisitionSnapshot(profileId).orElse(null);
				if (baseline == null || baseline.state() != PhantomBackgroundState.State.MATERIALIZED || !baseline.hashes().equals(generation.authorityHashes())) { return false; }
				final var nativeProof = _background.historicalNativeContext(profileId, baseline);
				if (nativeProof.status() != PhantomBackgroundTransaction.Status.SUCCESS || nativeProof.context() == null || nativeProof.context().phase() != PhantomNativeContext.Phase.COMPLETED
					|| !nativeProof.matchesNativeLoad(nativePlayer.getVitalityPoints()) || !generation.equals(_planner.generation()) || !currentClaim(profileId, catchup)
					|| !Objects.equals(stored, _goals.load(profileId).orElse(null)) || !visibleActorQuiet(profileId, nativePlayer, lifetime.materializedAtNanos())) { return false; }
			}
			final Player player;
			final PhantomBackgroundState projected;
			final org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint live;
			stage = "projection";
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (action == null) { return false; }
				player = action.player();
				if (player.hasPendingOwnedStore() || (player.getObjectId() != baseline.identity().characterObjectId()) || (player.getActiveClass() != baseline.identity().activeClassId())) { return false; }
				if (player.isDead()) { prepared = sharedFarmSource == null && !historicalIdle; return prepared; } // Existing native farm death/recovery remains the decision adapter's responsibility.
				final var progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
				// Planning reads current gear/resources; the persisted transaction baseline stays unchanged.
				final var skill = org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner.offensiveSkill(player, null);
				final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, null, skill);
				final var beforeLoadout = baseline.loadout();
				final var loadout = new PhantomBackgroundState.Loadout(skill == null ? 0 : skill.getId(), skill == null ? 0 : skill.getLevel(), beforeLoadout.summonNpcId(), (int) Math.ceil(combat.requiredMp()), beforeLoadout.shotItemId(), beforeLoadout.shotsPerEncounter(), beforeLoadout.summonResourceItemId(), beforeLoadout.summonResourcesPerEncounter());
				projected = new PhantomBackgroundState(baseline.state(), baseline.identity(), progress, combat.vitals(), baseline.position(), combat.combat(), loadout, baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
				live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			}
			final var exclusions = failures == null ? new Exclusions(Set.of(), Set.of()) : failures.exclusions(_visibleClock.getAsLong(), lifetime.materializedAtNanos());
			final boolean routeRecovery = !historicalIdle && (failures != null) && failures.routeBlocked(stored.goal(), lifetime.materializedAtNanos())
				&& !freshLocalAction(profileId, stored.goal(), lifetime.materializedAtNanos());
			stage = "logical_assembly";
			if (logicalAssemblyHoldsRoot(profileId, stored.goal(), sharedFarmSource != null)) { return false; }
			stage = "suitability_travel";
			if (!historicalIdle && !authorityRenewal && !routeRecovery && !exclusions.targets().contains(targetKey(stored.goal()))
				&& (sharedFarmSource == null || stored.goal().validSources().contains(sharedFarmSource)))
			{
				final boolean local = _planner.isVisibleLocal(stored.goal(), live);
				// A lawful assigned farm may differ from the last committed departure anchor.
				// Keep that goal for the existing native travel owner; never claim arrival here.
				if (local ? _planner.remainsVisibleSuitable(projected, stored.goal(), live)
					: (_visibleTravel != null) && _planner.remainsSuitable(projected, stored.goal())
						&& _visibleTravel.permitsPlannedFarmTravel(stored.goal(), live, player)) { prepared = true; return true; }
			}
			stage = "accepted_intent";
			if (_acceptedIntent.test(profileId, stored.goal()) || sharedFarmSource == null && _acceptedPartyRoot.test(profileId, stored.goal())
				|| logicalAssemblyHoldsRoot(profileId, stored.goal(), sharedFarmSource != null)) { return false; }
			stage = "stop_for_recovery";
			if (sharedFarmSource != null && !rendezvousCurrent(profileId, expectedGoal, reservationCurrent)) { return false; }
			if ((_visibleTravel != null) && !_visibleTravel.stopForRecovery(profileId, stored.goal(), player.getObjectId(), lifetime.materializedAtNanos())) { return false; }
			if (_visibleAutoPlay != null) { _visibleAutoPlay.stop(profileId); }
			stage = "actor_quiet";
			if (!visibleActorQuiet(profileId, player, lifetime.materializedAtNanos())) { return false; }
			stage = "catchup_handoff";
			final var handoff = _foregroundHandoffs.get(profileId);
			if ((catchup == null) || (catchup.state().goalId() != stored.goal().goalId()) || (catchup.state().goalRevision() != stored.goal().revision())
				|| ((catchup.state().status() != Status.COMPLETE) && ((handoff == null) || !exactForegroundHandoff(profileId, handoff)))) { return false; }
			final long now = _visibleClock.getAsLong();
			stage = "local_replan";
			final var recovery = _visibleEpisodes.compute(profileId, (_, current) -> (current != null) && (current.objectId == player.getObjectId()) && (current.epoch == lifetime.materializedAtNanos()) && (now - current.started < 60_000_000_000L) ? current : new VisibleEpisode(player.getObjectId(), lifetime.materializedAtNanos(), now));
			if (routeRecovery) { recovery.routes.add(stored.goal().revision() + ":" + failures.routeWitness(stored.goal(), lifetime.materializedAtNanos())); }
			else if (sharedFarmSource == null && !authorityRenewal && !historicalIdle) { recovery.targets.add(targetKey(stored.goal())); }
			if ((recovery.targets.size() + recovery.routes.size()) >= 3) { recovery.unavailable(now); return false; }
			final var excluded = new java.util.HashSet<>(exclusions.targets()); excluded.addAll(recovery.targets);
			if (routeRecovery) { excluded.add(targetKey(stored.goal())); }
			final long nextOrdinal = Math.addExact(catchup.state().planOrdinal(), 1);
			final java.util.function.Predicate<PhantomGoal> permitsTravel = candidate -> _planner.isVisibleLocal(candidate, live)
				|| (_visibleTravel != null && _visibleTravel.permitsReplannedFarmTravel(stored.goal(), candidate, live, player));
			final boolean travelOnly = rendezvousTravelPreparation(profileId, stored.goal(), sharedFarmSource != null);
			final var replacement = sharedFarmSource == null
				? _planner.replanVisibleLocal(profileId, projected, stored.goal(), catchup.state().deterministicSeed(), nextOrdinal, live, excluded, exclusions.steps(), permitsTravel)
				: _planner.replanVisibleRendezvous(profileId, projected, stored.goal(), catchup.state().deterministicSeed(), nextOrdinal, live, sharedFarmSource, exclusions.steps(), travelOnly,
					candidate -> !excluded.contains(targetKey(candidate)) && permitsTravel.test(candidate));
			if (!replacement.ready())
			{
				stage += ":" + replacement.reasonKey();
				final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
				if (recorder.isRecording())
				{
					recorder.record(profileId, "VISIBLE_REPLAN_REJECTED", replacement.reasonKey(), baseline.position().committedAnchorId(),
						"live=" + live.x() + "," + live.y() + "," + live.z() + ";reject=" + replacement.candidateRejections().stream().limit(2).toList(),
						stored.goal().goalId(), lifetime.materializedAtNanos(), stored.goal().revision());
				}
				recovery.unavailable(now); return false;
			}
			if (authorityRenewal || historicalIdle ? !replacement.generation().equals(generation) || !generation.equals(_planner.generation()) || !currentClaim(profileId, catchup) : (replacement.generation().knowledgeGeneration() != catchup.state().knowledgeGeneration()) || (replacement.generation().topologyGeneration() != catchup.state().topologyGeneration()) || !replacement.generation().authorityHashes().equals(catchup.state().authorityHashes())) { return false; }
			final var state = catchup.state();
			final var updated = historicalIdle && !travelOnly
				? state.withPlan(replacement.goal().goalId(), replacement.goal().revision(), nextOrdinal, replacement.planIdentity(), replacement.generation().knowledgeGeneration(), replacement.generation().topologyGeneration(), replacement.generation().authorityHashes())
				: new PhantomBackgroundCatchupState(state.status(), state.requestId(), state.deterministicSeed(), state.fromEpochMinute(), state.targetEpochMinute(), state.cursorEpochMinute(), nextOrdinal, state.intervalOrdinal(), state.generation(), replacement.generation().knowledgeGeneration(), replacement.generation().topologyGeneration(), replacement.goal().goalId(), replacement.goal().revision(), replacement.planIdentity(), state.modelVersion(), replacement.generation().authorityHashes(), state.failureReason());
			final var publication = new VisiblePublication(player, lifetime.materializedAtNanos(), handoff, catchup, stored, updated, replacement.goal());
			if (_visiblePublications.putIfAbsent(profileId, publication) != null) { return false; }
			if (sharedFarmSource != null && (!rendezvousCurrent(profileId, expectedGoal, reservationCurrent)
				|| travelOnly && !rendezvousTravelPreparation(profileId, stored.goal(), true)
				|| !visibleActorQuiet(profileId, player, lifetime.materializedAtNanos()) || player.isInCombat()))
			{ _visiblePublications.remove(profileId, publication); return false; }
			if (_acceptedIntent.test(profileId, stored.goal()) || sharedFarmSource == null && _acceptedPartyRoot.test(profileId, stored.goal())
				|| logicalAssemblyHoldsRoot(profileId, stored.goal(), sharedFarmSource != null))
			{ _visiblePublications.remove(profileId, publication); return false; }
			try { publication.committed = _store.replacePlan(profileId, catchup, updated, stored, replacement.goal()); }
			catch (RuntimeException exception)
			{
				// Resolve an ambiguous DB return only to the exact proposed components; never grant on drift.
				final var persisted = _store.load(profileId).orElse(null); final var persistedGoal = _goals.load(profileId).orElse(null);
				if ((persisted != null) && (persistedGoal != null) && persisted.state().equals(updated) && persistedGoal.goal().equals(replacement.goal()) && (persisted.rowVersion() == catchup.rowVersion() + 1) && (persistedGoal.rowVersion() == stored.rowVersion() + 1)) { publication.committed = new PlannedSnapshot(persisted, persistedGoal); }
				else { _visiblePublications.remove(profileId, publication); return false; }
			}
			if (sharedFarmSource == null && !historicalIdle && !authorityRenewal && !routeRecovery) { recovery.targets.add(targetKey(replacement.goal())); }
			stage = "finish_publication";
			prepared = finishVisiblePublication(profileId, decision, publication);
			return prepared;
		}
		catch (RuntimeException exception) { prepared = false; stage += ":thrown." + exception.getClass().getName(); return false; }
		finally
		{
			// Diagnostics never create recovery episodes or participate in admission.
			synchronized (_visiblePreparationStages)
			{
				if (prepared) { _visiblePreparationStages.remove(profileId); }
				else if (_visiblePreparationStages.containsKey(profileId) || _visiblePreparationStages.size() < 1024)
				{
					final var previous = _visiblePreparationStages.get(profileId);
					if ((!"accepted_rendezvous_root".equals(stage) && !"logical_assembly".equals(stage)) || previous == null || !previous.startsWith("rendezvous."))
					{ _visiblePreparationStages.put(profileId, sharedFarmSource == null ? "pre_visible." + stage : "rendezvous.prepare." + stage + ";goal=" + expectedGoal.goalId() + ":" + expectedGoal.revision()); }
				}
			}
		}
	}

	private boolean rendezvousCurrent(long profileId, PhantomGoal expectedGoal, java.util.function.BooleanSupplier reservationCurrent)
	{
		return !_foregroundDecisionsRevoked && reservationCurrent.getAsBoolean()
			&& Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), expectedGoal);
	}

	private boolean finishVisiblePublication(long profileId, PhantomDecisionEngine decision, VisiblePublication receipt)
	{
		if (receipt.committed == null)
		{
			final var state = _store.load(profileId).orElse(null); final var goal = _goals.load(profileId).orElse(null);
			if ((state != null) && (goal != null) && state.state().equals(receipt.proposed) && goal.goal().equals(receipt.proposedGoal) && (state.rowVersion() == receipt.before.rowVersion() + 1) && (goal.rowVersion() == receipt.oldGoal.rowVersion() + 1)) { receipt.committed = new PlannedSnapshot(state, goal); }
			else if (Objects.equals(state, receipt.before) && Objects.equals(goal, receipt.oldGoal)) { _visiblePublications.remove(profileId, receipt); return false; }
			else { return false; }
		}
		final var committed = receipt.committed;
		if ((committed == null) || (_visiblePublications.get(profileId) != receipt) || !visibleActorQuiet(profileId, receipt.player, receipt.epoch)
			|| !Objects.equals(committed.catchup(), _store.load(profileId).orElse(null)) || !Objects.equals(committed.goal(), _goals.load(profileId).orElse(null))) { return false; }
		if (receipt.handoff != null)
		{
			final var old = receipt.handoff.admission();
			final var next = new ActiveForegroundHandoff(new Admission(old.characterObjectId(), old.purpose(), old.ownerClaim(), old.recovery(), _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null), committed.catchup()), receipt.epoch);
			if (!exactForegroundHandoff(profileId, next)) { return false; }
			if (receipt.rebound == null)
			{
				if (!_foregroundHandoffs.replace(profileId, receipt.handoff, next)) { return false; }
				receipt.rebound = next;
			}
			else if ((_foregroundHandoffs.get(profileId) != receipt.rebound) || !exactForegroundHandoff(profileId, receipt.rebound)) { return false; }
		}
		if ((decision.reload(profileId) != PhantomDecisionEngine.ReloadResult.RELOADED) || !runtimeMatches(decision.find(profileId).orElse(null), committed.goal())
			|| (_visiblePublications.get(profileId) != receipt) || !visibleActorQuiet(profileId, receipt.player, receipt.epoch)) { return false; }
		_visiblePublications.remove(profileId, receipt);
		return permitsDecision(profileId);
	}

	/** Temporary native work returns its unchanged historical claim through the normal STORE boundary. */
	private boolean finishNativeContextHandoff(long profileId, PhantomGoal goal)
	{
		var handoff = _foregroundHandoffs.get(profileId);
		if (handoff == null || !exactForegroundHandoff(profileId, handoff) || _visibleAutoPlay == null || _visibleTravel == null
			|| _acceptedIntent.test(profileId, goal) || _partyNativeLifetime.test(profileId, goal)) { return false; }
		final var player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(handoff.admission().characterObjectId());
		final var owner = player == null ? null : player.getNativeWorkOwner();
		if (owner == null || owner.player() != player || !owner.isCurrent() || owner.epoch() != handoff.materializedAtNanos()) { return false; }
		final var proof = owner.evidence() == null ? null : owner.evidence().snapshot();
		if (!nativeHandoffRetirementRequired(proof, player.getObjectId(), handoff.materializedAtNanos(), _partyNativeLifetime.test(profileId, goal))) { return false; }
		if (handoff.completionDeadlineEpochMillis() == 0)
		{
			final var completing = new ActiveForegroundHandoff(handoff.admission(), handoff.materializedAtNanos(),
				Math.addExact(System.currentTimeMillis(), java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(_visibleTravel.navigation().policy().maximumAttemptDurationNanos())));
			if (!_foregroundHandoffs.replace(profileId, handoff, completing)) { return true; }
			handoff = completing;
		}
		if (System.currentTimeMillis() >= handoff.completionDeadlineEpochMillis()) { return true; }
		if (!_visibleAutoPlay.yieldAfterCurrentTarget(profileId, handoff.completionDeadlineEpochMillis())
			|| !_visibleTravel.stopForRecovery(profileId, goal, player.getObjectId(), handoff.materializedAtNanos())
			|| player.isInCombat() || !visibleActorQuiet(profileId, player, handoff.materializedAtNanos())
			|| !exactForegroundHandoff(profileId, handoff) || !Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), goal)) { return true; }
		if (_partyNativeLifetime.test(profileId, goal)) { return false; }
		_materialization.dematerialize(profileId);
		// No cursor or reward mutation here: the existing interval transaction resumes only after STORE.
		return true;
	}

	static boolean nativeHandoffWorkComplete(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot proof, int objectId, long epoch)
	{
		return proof != null && proof.objectId() == objectId && proof.epoch() == epoch && !proof.overflow() && proof.farmCycleSequence() > 0;
	}

	static boolean nativeHandoffRetirementRequired(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot proof, int objectId, long epoch, boolean partyRetained)
	{
		return !partyRetained && nativeHandoffWorkComplete(proof, objectId, epoch);
	}

	static boolean nativePartyDecisionPrepared(boolean sharedRendezvousPreparation, boolean committedNativeParty)
	{
		return nativePartyDecisionPrepared(sharedRendezvousPreparation, committedNativeParty, false);
	}
	static boolean nativePartyDecisionPrepared(boolean sharedRendezvousPreparation, boolean committedNativeParty, boolean adoptedRendezvousTravel)
	{
		return !sharedRendezvousPreparation && (committedNativeParty || adoptedRendezvousTravel);
	}

	private boolean visibleActorQuiet(long profileId, Player player, long epoch)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		if (_foregroundDecisionsRevoked || (lifetime == null) || (lifetime.characterObjectId() != player.getObjectId()) || (lifetime.materializedAtNanos() != epoch) || !lifetime.actionAdmissionOpen() || (lifetime.cleanupPhase() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE)) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			return (action != null) && (action.player() == player) && !player.isDead() && !player.isInParty() && !player.hasPendingOwnedStore() && !player.isAttackingNow() && !player.isCastingNow() && !player.isTeleporting() && !player.isMoving() && !player.isAutoPlaying();
		}
	}

	private static boolean runtimeMatches(PhantomDecisionEngine.RuntimeSnapshot runtime, StoredGoal goal)
	{
		return (runtime != null) && (runtime.goalId() == goal.goal().goalId()) && (runtime.goalRevision() == goal.goal().revision()) && (runtime.goalStatus() == goal.goal().status()) && (runtime.componentRowVersion() == goal.rowVersion());
	}

	private static String targetKey(PhantomGoal goal) { final var spec = PhantomBackgroundGoalSpec.parse(goal); return spec.npcId() + "@" + spec.anchorId(); }
	public String visibleRecoveryReason(long profileId)
	{
		final var episode = _visibleEpisodes.get(profileId);
		return episode != null && !episode.reason.isEmpty() ? episode.reason : _visiblePreparationStages.getOrDefault(profileId, "");
	}
	public String visiblePreparationStage(long profileId)
	{
		synchronized (_visiblePreparationStages) { return _visiblePreparationStages.getOrDefault(profileId, ""); }
	}
	static boolean holdsOrdinaryPartyRoot(boolean sharedPreparation, boolean exactNativePartyRoot, boolean acceptedExternalOwner)
	{
		return holdsOrdinaryPartyRoot(sharedPreparation, exactNativePartyRoot, acceptedExternalOwner, false);
	}
	static boolean holdsOrdinaryPartyRoot(boolean sharedPreparation, boolean exactNativePartyRoot, boolean acceptedExternalOwner, boolean necessaryVisibleIntent)
	{
		return !sharedPreparation && exactNativePartyRoot && !acceptedExternalOwner && !necessaryVisibleIntent;
	}
	static boolean holdsLogicalAssemblyRoot(boolean sharedPreparation, boolean finiteLogicalAssembly, boolean acceptedExternalOwner, boolean necessaryVisibleIntent)
	{
		return !sharedPreparation && finiteLogicalAssembly && !acceptedExternalOwner && !necessaryVisibleIntent;
	}
	static boolean rendezvousTravelOnly(boolean sharedPreparation, boolean exactAcceptedPhysicalRoot, boolean exactSupportPreparation)
	{
		return sharedPreparation && (exactAcceptedPhysicalRoot || exactSupportPreparation);
	}
	private boolean rendezvousTravelPreparation(long profileId, PhantomGoal root, boolean sharedPreparation)
	{
		return sharedPreparation && rendezvousTravelOnly(true, _acceptedPartyRoot.test(profileId, root), _supportRendezvousPreparation.test(profileId, root));
	}
	/** A bounded worker call resolves durable claims before any historical planning or interval work. */
	private Result refreshLogicalBeforeBackground(long profileId, Snapshot current)
	{
		try
		{
			_refreshLogicalGroups.accept(profileId);
			return activityPending(profileId) ? Result.rejected(ResultStatusCode.RETRY, "catchup.logical_activity.pending", current) : null;
		}
		catch (RuntimeException unavailable) { return Result.rejected(ResultStatusCode.RETRY, "catchup.logical_group.persistence_retry", current); }
	}
	private boolean logicalAssemblyHoldsRoot(long profileId, PhantomGoal root, boolean sharedPreparation)
	{
		if (sharedPreparation) { return false; }
		final var group = logicalGroup(profileId, root);
		return holdsLogicalAssemblyRoot(false, group != null && group.assemblyPending(profileId, root.goalId(), root.revision(), System.currentTimeMillis()),
			_acceptedIntent.test(profileId, root), _visibleIntent.test(profileId, root));
	}

	/** A successful exact foreground handoff owns decisions, never ordinary historical work. */
	public boolean permitsDecision(long profileId)
	{
		try
		{
			if (activityPending(profileId)) { return false; }
			if (_visiblePublications.containsKey(profileId)) { return false; }
			final Snapshot current = _store.load(profileId).orElse(null);
			if (_recoveryClaims.containsKey(profileId)) { return false; }
			if ((current == null) || !current.state().blocksNormalOperation())
			{
				final var baseline = _background.acquisitionSnapshot(profileId);
				if (baseline.isEmpty()) { return current == null && _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).isEmpty(); }
				final var state = baseline.get();
				return state.state() != PhantomBackgroundState.State.INCONSISTENT && state.state() != PhantomBackgroundState.State.VERIFY_PENDING
					&& state.hashes().equals(_planner.generation().authorityHashes());
			}
			final var handoff = _foregroundHandoffs.get(profileId);
			return (handoff != null) && exactForegroundHandoff(profileId, handoff) && (_foregroundHandoffs.get(profileId) == handoff);
		}
		catch (RuntimeException exception) { return false; }
	}

	private boolean exactForegroundHandoff(long profileId, ActiveForegroundHandoff handoff)
	{
		if (_foregroundDecisionsRevoked) { return false; }
		final Admission admission = handoff.admission();
		if ((admission.purpose() != MaterializationPurpose.NATIVE_CONTEXT_HANDOFF) || (admission.catchup() == null) || (admission.goal() == null)
			|| !admission.catchup().state().owns(admission.ownerClaim()) || !currentClaim(profileId, admission.catchup())
			|| !Objects.equals(admission.claim(), _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null))
			|| !Objects.equals(admission.goal(), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null))) { return false; }
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.profileId() != profileId) || (lifetime.state() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE)
			|| !lifetime.worldPresent() || !lifetime.actionAdmissionOpen() || (lifetime.characterObjectId() != admission.characterObjectId())
			|| (lifetime.materializedAtNanos() != handoff.materializedAtNanos()) || (lifetime.dematerializedAtNanos() != 0)
			|| (lifetime.cleanupPhase() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE)) { return false; }
		final var background = _background.acquisitionSnapshot(profileId).orElse(null);
		return (background != null) && (background.state() == PhantomBackgroundState.State.MATERIALIZED)
			&& (background.identity().profileId() == profileId) && (background.identity().characterObjectId() == admission.characterObjectId());
	}

	/** Stop revokes transient ownership even when native cleanup must be retried. */
	public void revokeForegroundDecisions()
	{
		_foregroundDecisionsRevoked = true;
		_foregroundHandoffs.clear();
		_visiblePublications.clear();
		_visibleEpisodes.clear();
	}

	public boolean permitsNormalOperation(long profileId)
	{
		try
		{
			return permitsDecision(profileId) && _store.load(profileId).map(snapshot -> !snapshot.state().blocksNormalOperation()).orElse(true);
		}
		catch (RuntimeException exception)
		{
			return false;
		}
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
		beforeMaterialize(profileId, characterObjectId, MaterializationPurpose.NORMAL, "");
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId, MaterializationPurpose purpose, String ownerClaim)
	{
		Objects.requireNonNull(purpose, "purpose");
		try { _refreshLogicalGroups.accept(profileId); }
		catch (RuntimeException unavailable) { throw new AdmissionRejectedException("catchup.logical_group.persistence_retry"); }
		final Snapshot catchup;
		try
		{
			catchup = _store.load(profileId).orElse(null);
		}
		catch (RuntimeException exception)
		{
			throw new AdmissionRejectedException("catchup.persistence_unavailable");
		}
		if (purpose == MaterializationPurpose.NORMAL)
		{
			if ((catchup != null) && catchup.state().blocksNormalOperation() || !permitsNormalOperation(profileId))
			{
				throw new AdmissionRejectedException("catchup.normal_fenced");
			}
		}
		final RecoveryClaim recovery = purpose == MaterializationPurpose.HISTORICAL_BASELINE ? _recoveryClaims.get(profileId) : null;
		if (purpose == MaterializationPurpose.HISTORICAL_BASELINE && nativeIntentPending(profileId)) { throw new AdmissionRejectedException("catchup.authority.intent_pending"); }
		if ((purpose == MaterializationPurpose.NATIVE_CONTEXT_HANDOFF) && ((ownerClaim == null) || ownerClaim.isBlank() || (catchup == null) || !catchup.state().owns(ownerClaim)
			|| (catchup.state().status() == Status.COMPLETE) || !currentClaim(profileId, catchup)))
		{
			throw new AdmissionRejectedException("catchup.native_handoff_claim_invalid");
		}
		if ((purpose == MaterializationPurpose.HISTORICAL_BASELINE) && ((catchup == null) || !catchup.state().owns(ownerClaim)
			|| ((catchup.state().status() != Status.PENDING) && ((recovery == null) || !catchup.equals(recovery.snapshot()))) || ((recovery != null) && !catchup.equals(recovery.snapshot())) || !currentClaim(profileId, catchup)))
		{
			throw new AdmissionRejectedException("catchup.historical_claim_invalid");
		}
		final var claimComponent = purpose != MaterializationPurpose.NORMAL ? _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null) : null;
		final var goalComponent = purpose != MaterializationPurpose.NORMAL ? _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null) : null;
		if ((purpose != MaterializationPurpose.NORMAL) && ((claimComponent == null) || (claimComponent.rowVersion() != catchup.rowVersion()) || !MessageDigest.isEqual(claimComponent.payload(), new PhantomBackgroundCatchupStateCodec().encode(catchup.state()))))
		{
			throw new AdmissionRejectedException("catchup.historical_claim_changed");
		}
		if ((recovery != null) && (!Objects.equals(recovery.claim(), claimComponent) || !Objects.equals(recovery.goal(), goalComponent))) { throw new AdmissionRejectedException("catchup.historical_claim_or_goal_changed"); }
		if (recovery != null && !currentRecoveryClaim(profileId, recovery)) { throw new AdmissionRejectedException("catchup.authority.dependencies_changed"); }
		final Admission admission = new Admission(characterObjectId, purpose, ownerClaim, recovery, claimComponent, goalComponent, catchup);
		if (_admissions.putIfAbsent(profileId, admission) != null)
		{
			throw new AdmissionRejectedException("catchup.materialization_transition_busy");
		}
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
		final var admission = _admissions.get(profileId);
		if ((admission == null) || (admission.characterObjectId() != player.getObjectId())) { throw new AdmissionRejectedException("catchup.native_admission_missing"); }
		if (admission.purpose() != MaterializationPurpose.NORMAL)
		{
			final var owner = player.getNativeWorkOwner();
			final var entry = _materialization.find(profileId).orElse(null);
			if ((owner == null) || !owner.isCurrent() || (owner.player() != player) || (entry == null) || (entry.characterObjectId() != player.getObjectId()) || (entry.materializedAtNanos() != owner.epoch())
				|| ((admission.recovery() != null) && (_recoveryClaims.get(profileId) != admission.recovery()))
				|| !Objects.equals(admission.claim(), _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null))
				|| !Objects.equals(admission.goal(), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)))
			{
				throw new AdmissionRejectedException("catchup.historical_native_claim_or_epoch_changed");
			}
		}
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
		try
		{
			_foregroundHandoffs.compute(profileId, (_, previous) ->
			{
				final Admission admission = _admissions.get(profileId);
				if ((admission == null) || (admission.characterObjectId() != characterObjectId) || (admission.purpose() != MaterializationPurpose.NATIVE_CONTEXT_HANDOFF)) { return previous; }
				final var lifetime = _materialization.find(profileId).orElse(null);
				if (lifetime == null) { return null; }
				final var handoff = new ActiveForegroundHandoff(admission, lifetime.materializedAtNanos());
				return exactForegroundHandoff(profileId, handoff) ? handoff : null;
			});
		}
		finally { releaseAdmission(profileId, characterObjectId); }
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		_visiblePreparationStages.remove(profileId);
		_visiblePublications.remove(profileId);
		_visibleEpisodes.remove(profileId);
		_foregroundHandoffs.computeIfPresent(profileId, (_, handoff) -> handoff.admission().characterObjectId() == characterObjectId ? null : handoff);
		releaseAdmission(profileId, characterObjectId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		_visiblePreparationStages.remove(profileId);
		_visiblePublications.remove(profileId);
		_visibleEpisodes.remove(profileId);
		_foregroundHandoffs.remove(profileId);
		final RecoveryClaim recovery = _recoveryClaims.get(profileId);
		if (recovery != null && !currentRecoveryClaim(profileId, recovery)) { throw new AdmissionRejectedException("catchup.authority.dependencies_changed"); }
		if ((recovery != null) && (!currentClaim(profileId, recovery.snapshot())
			|| !Objects.equals(recovery.claim(), _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null))
			|| !Objects.equals(recovery.goal(), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)))) { throw new AdmissionRejectedException("catchup.historical_store_claim_or_goal_changed"); }
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}

	private void releaseAdmission(long profileId, int characterObjectId)
	{
		final Admission admission = _admissions.get(profileId);
		if ((admission != null) && (admission.characterObjectId() == characterObjectId))
		{
			_admissions.remove(profileId, admission);
		}
	}

	private Result fail(long profileId, Snapshot expected, String reason)
	{
		try
		{
			final Snapshot failed = _store.replace(profileId, expected, expected.state().failed(reason));
			return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, reason, failed);
		}
		catch (RuntimeException exception)
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.failure_publish_retry", _store.load(profileId).orElse(expected));
		}
	}

	private static boolean sameRequest(PhantomBackgroundCatchupState left, PhantomBackgroundCatchupState right)
	{
		return left.requestId().equals(right.requestId()) && (left.deterministicSeed() == right.deterministicSeed()) && (left.fromEpochMinute() == right.fromEpochMinute()) && (left.targetEpochMinute() == right.targetEpochMinute()) && (left.generation() == right.generation()) && (left.knowledgeGeneration() == right.knowledgeGeneration()) && (left.topologyGeneration() == right.topologyGeneration()) && (left.modelVersion() == right.modelVersion()) && left.authorityHashes().equals(right.authorityHashes());
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static long positiveLong(String digest)
	{
		final long value = Long.parseUnsignedLong(digest.substring(0, 16), 16) & Long.MAX_VALUE;
		return value == 0 ? 1 : value;
	}

	private record RecoveryClaim(Snapshot snapshot, PhantomProfileComponent claim, PhantomProfileComponent goal, Map<String, Optional<PhantomProfileComponent>> guards)
	{
		RecoveryClaim(Snapshot snapshot, PhantomProfileComponent claim, PhantomProfileComponent goal) { this(snapshot, claim, goal, Map.of()); }
	}
	private static final class NativeRecoveryRefresh
	{
		final RecoveryClaim recovery;
		final PhantomHistoricalBackgroundPlanner.PlanningGeneration generation;
		int objectId; long epoch;
		NativeRecoveryRefresh(RecoveryClaim recovery, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation) { this.recovery = recovery; this.generation = generation; }
	}

	private record Admission(int characterObjectId, MaterializationPurpose purpose, String ownerClaim, RecoveryClaim recovery, PhantomProfileComponent claim, PhantomProfileComponent goal, Snapshot catchup)
	{
	}

	private record ActiveForegroundHandoff(Admission admission, long materializedAtNanos, long completionDeadlineEpochMillis)
	{
		ActiveForegroundHandoff(Admission admission, long materializedAtNanos) { this(admission, materializedAtNanos, 0); }
	}

	private static final class VisibleEpisode
	{
		final int objectId;
		final long epoch;
		final long started;
		final Set<String> targets = ConcurrentHashMap.newKeySet();
		final Set<String> routes = ConcurrentHashMap.newKeySet();
		volatile long cooldownUntil;
		volatile String reason = "";
		VisibleEpisode(int objectId, long epoch, long started) { this.objectId = objectId; this.epoch = epoch; this.started = started; }
		void unavailable(long now) { reason = "LOCAL_FARM_UNAVAILABLE"; cooldownUntil = Math.max(now + 10_000_000_000L, started + 60_000_000_000L); }
	}

	private static final class VisiblePublication
	{
		final Player player;
		final long epoch;
		final ActiveForegroundHandoff handoff;
		final Snapshot before;
		final StoredGoal oldGoal;
		final PhantomBackgroundCatchupState proposed;
		final PhantomGoal proposedGoal;
		volatile PlannedSnapshot committed;
		volatile ActiveForegroundHandoff rebound;
		VisiblePublication(Player player, long epoch, ActiveForegroundHandoff handoff, Snapshot before, StoredGoal oldGoal, PhantomBackgroundCatchupState proposed, PhantomGoal proposedGoal)
		{ this.player = player; this.epoch = epoch; this.handoff = handoff; this.before = before; this.oldGoal = oldGoal; this.proposed = proposed; this.proposedGoal = proposedGoal; }
	}

	private record Exclusions(Set<String> targets, Set<String> steps)
	{
	}

	private static final class VisibleFailures
	{
		private static final long TTL_NANOS = 120_000_000_000L;
		private final LinkedHashMap<String, VisibleExclusion> _targets = new LinkedHashMap<>();
		private final LinkedHashMap<String, VisibleExclusion> _steps = new LinkedHashMap<>();
		private volatile long _lastFailureNanos;
		private ProtocolFailure _protocol;
		private RouteFailure _route;

		private synchronized void route(PhantomGoal goal, long epoch, String witness, long now)
		{
			_lastFailureNanos = now;
			put(_steps, witness, epoch, now);
			_route = new RouteFailure(goal.goalId(), goal.revision(), epoch, witness);
		}

		private synchronized boolean routeBlocked(PhantomGoal goal, long epoch)
		{
			return (_route != null) && (_route.goalId == goal.goalId()) && (_route.revision == goal.revision()) && (_route.epoch == epoch);
		}

		private synchronized String routeWitness(PhantomGoal goal, long epoch)
		{
			return routeBlocked(goal, epoch) ? _route.witness : "";
		}

		private synchronized void protocol(PhantomGoal goal, long epoch, String reason, long now)
		{
			_lastFailureNanos = now;
			_protocol = new ProtocolFailure(goal.goalId(), goal.revision(), epoch, reason);
		}

		private synchronized boolean protocolBlocked(PhantomGoal goal, long epoch)
		{
			return (_protocol != null) && (_protocol.goalId == goal.goalId()) && (_protocol.revision == goal.revision()) && (_protocol.epoch == epoch);
		}

		private synchronized void record(String target, String step, long epoch, long now)
		{
			_lastFailureNanos = now;
			put(_targets, target, epoch, now);
			if (!step.isEmpty())
			{
				put(_steps, step, epoch, now);
			}
		}

		private static void put(LinkedHashMap<String, VisibleExclusion> values, String key, long epoch, long now)
		{
			values.remove(key);
			values.put(key, new VisibleExclusion(epoch, now));
			if (values.size() > 8)
			{
				values.remove(values.keySet().iterator().next());
			}
		}

		private synchronized Exclusions exclusions(long now, long epoch)
		{
			_targets.values().removeIf(value -> (now - value.recordedAtNanos()) >= TTL_NANOS);
			_steps.values().removeIf(value -> (now - value.recordedAtNanos()) >= TTL_NANOS);
			return new Exclusions(current(_targets, epoch), current(_steps, epoch));
		}
		private static Set<String> current(Map<String, VisibleExclusion> values, long epoch)
		{
			return values.entrySet().stream().filter(entry -> entry.getValue().epoch() == epoch).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
	}
	private record VisibleExclusion(long epoch, long recordedAtNanos) { }

	private record ProtocolFailure(long goalId, long revision, long epoch, String reason) { }
	private record RouteFailure(long goalId, long revision, long epoch, String witness) { }

	public enum ResultStatusCode
	{
		SUCCESS,
		INVALID_REQUEST,
		PROFILE_UNAVAILABLE,
		NORMAL_MATERIALIZED,
		NOT_FOUND,
		CONFLICT,
		RETRY,
		REPLAN_REQUIRED
	}

	public record Result(ResultStatusCode status, String reason, Snapshot snapshot, int advancedIntervals)
	{
		public Result
		{
			Objects.requireNonNull(status, "status");
			reason = Objects.requireNonNullElse(reason, "");
			if (advancedIntervals < 0)
			{
				throw new IllegalArgumentException("Advanced interval count cannot be negative.");
			}
		}

		public static Result success(Snapshot snapshot, int advancedIntervals)
		{
			return new Result(ResultStatusCode.SUCCESS, "catchup.ready", snapshot, advancedIntervals);
		}

		public static Result rejected(ResultStatusCode status, String reason, Snapshot snapshot)
		{
			return new Result(status, reason, snapshot, 0);
		}

		public boolean successful()
		{
			return status == ResultStatusCode.SUCCESS;
		}
	}
}
