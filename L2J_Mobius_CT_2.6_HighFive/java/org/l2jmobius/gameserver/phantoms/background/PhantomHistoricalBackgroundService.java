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
import java.util.LinkedHashMap;
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
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomBackgroundCatchupStore _store;
	private final PhantomHistoricalBackgroundPlanner _planner;
	private final PhantomBackgroundService _background;
	private final PhantomMaterializationService _materialization;
	private final ConcurrentHashMap<Long, Admission> _admissions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, ActiveForegroundHandoff> _foregroundHandoffs = new ConcurrentHashMap<>();
	private volatile boolean _foregroundDecisionsRevoked;
	private final ConcurrentHashMap<Long, RecoveryClaim> _recoveryClaims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisibleFailures> _visibleFailures = new ConcurrentHashMap<>();
	private final LongSupplier _visibleClock;
	private final ConcurrentHashMap<Long, VisibleEpisode> _visibleEpisodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisiblePublication> _visiblePublications = new ConcurrentHashMap<>();
	private volatile PhantomVisibleFarmTravel _visibleTravel;
	private volatile PhantomVisibleAutoPlay _visibleAutoPlay;

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
	}

	/** Dynamic failures expire; they never alter the canonical topology or farm intention. */
	public void recordVisibleFailure(long profileId, PhantomGoal goal, String failedStep)
	{
		final var spec = PhantomBackgroundGoalSpec.parse(goal);
		final long now = _visibleClock.getAsLong();
		// Failures are rare; only admission/eviction takes this lock, never the decision hot path.
		synchronized (_visibleFailures)
		{
			_visibleFailures.entrySet().removeIf(entry -> (now - entry.getValue()._lastFailureNanos) >= VisibleFailures.TTL_NANOS);
			_visibleFailures.computeIfAbsent(profileId, _ -> new VisibleFailures()).record(spec.npcId() + "@" + spec.anchorId(), failedStep, now);
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
				case ROUTE_UNUSABLE, NATIVE_ACTION_REJECTED ->
				{
					if (failure.stepId().startsWith("live.approach." + PhantomBackgroundGoalSpec.parse(failure.goal()).anchorId() + "@"))
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
			return Result.success(current, 0);
		}
		final var generation = _planner.generation();
		final boolean stale = !current.state().authorityHashes().equals(generation.authorityHashes()) || (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration());
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
			if ((backgroundState.state() == PhantomBackgroundState.State.READY) && !_planner.remainsSuitable(backgroundState, storedGoal.goal()))
			{
				final long nextPlanOrdinal = Math.addExact(current.state().planOrdinal(), 1);
				final var replanned = planOrIdle(profileId, backgroundState, storedGoal.goal(), current.state().deterministicSeed(), nextPlanOrdinal, Set.of());
				if (!replanned.ready())
				{
					return fail(profileId, current, replanned.reasonKey());
				}
				try
				{
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
			var proof = _background.historicalNativeContext(profileId, baseline);
			if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) && (proof.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context." + proof.status().name().toLowerCase(java.util.Locale.ROOT), current);
			}
			if (proof.context().phase() == PhantomNativeContext.Phase.PENDING) { return Result.rejected(ResultStatusCode.RETRY, "native_context.pending", current); }
			final int beforePoints = proof.canonicalPoints();
			final boolean unknown = proof.context().phase() == PhantomNativeContext.Phase.UNKNOWN;
			if (unknown)
			{
				final Result refreshed = refreshCanonicalBaseline(profileId, current);
				if (!refreshed.successful()) { return refreshed; }
				final var after = _background.acquisitionSnapshot(profileId).orElse(null);
				if (!nativeContextFactsPreserved(baseline, after)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.baseline_changed", current); }
				proof = _background.historicalNativeContext(profileId, after);
			}
			if (!currentClaim(profileId, current) || !Objects.equals(goalComponent, _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null)) || !_planner.generation().equals(generation))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.claim_goal_or_generation_changed", current);
			}
			if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) || (proof.context() == null) || (proof.context().phase() != PhantomNativeContext.Phase.COMPLETED))
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.unattested", current);
			}
			if (!proof.context().simulationEligible())
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

	private static boolean nativeContextFactsPreserved(PhantomBackgroundState before, PhantomBackgroundState after)
	{
		return (after != null) && (before.state() == after.state()) && before.identity().equals(after.identity()) && before.progress().equals(after.progress())
			&& before.position().equals(after.position()) && before.clock().equals(after.clock()) && before.receipt().equals(after.receipt())
			&& before.inventory().objects().equals(after.inventory().objects()) && before.inventory().canonicalHash().equals(after.inventory().canonicalHash()) && before.autoGetSkills().equals(after.autoGetSkills());
	}

	public static boolean requiresNativeMaterialization(String reason)
	{
		return (reason != null) && (reason.startsWith("native_context.required:") || reason.equals("transaction.native_context_required"));
	}

	private Result recoverNativeContext(long profileId, Snapshot current)
	{
		if ((current.state().status() != Status.FAILED_REPLAN_REQUIRED) || !requiresNativeMaterialization(current.state().failureReason())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_context.failure_not_owned", current); }
		final Result attested = ensureNativeContext(profileId, current, _background.acquisitionSnapshot(profileId).orElse(null));
		if (!attested.successful()) { return attested; }
		try { return Result.success(_store.replace(profileId, current, current.state().retryRunning()), 0); }
		catch (RuntimeException exception) { return Result.rejected(ResultStatusCode.RETRY, "catchup.native_context.retry_publish", _store.load(profileId).orElse(current)); }
	}

	/** Only classified recoverable failures may re-enter the same optimistic history ownership. */
	public static boolean isRecoverableFailure(String reason)
	{
		return Set.of("transaction.item_conflict", "model.object_cap", "model.object_cap_indivisible", "catchup.authority.unsupported", "planner.target_or_route.absent", "authority.hash_stale", "catchup.authority.authority_stale", "catchup.authority.position_stale", "catchup.authority.target_stale", "catchup.authority.resource_stale", "catchup.authority.unsupported_loot").contains(reason);
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
		final long ordinal = Math.addExact(current.state().planOrdinal(), 1);
		final var plan = planOrIdle(profileId, state, goal.goal(), current.state().deterministicSeed(), ordinal, exclusions);
		if (!plan.ready()) { return fail(profileId, current, plan.reasonKey()); }
		try
		{
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
		var plan = excludedTargets.isEmpty() ? _planner.replaceFromState(profileId, state, previous, seed, ordinal) : _planner.replan(profileId, state, previous, seed, ordinal, excludedTargets, Set.of());
		if ("planner.target_or_route.absent".equals(plan.reasonKey())) { plan = _planner.idleFromState(profileId, state, previous, seed, ordinal); }
		return plan;
	}

	private Result recoverStale(long profileId, Snapshot current, PhantomHistoricalBackgroundPlanner.PlanningGeneration generation)
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
		final Result prerequisite = restorePrerequisite(profileId, current, backgroundState, storedGoal);
		if (!prerequisite.successful()) { return prerequisite; }
		current = prerequisite.snapshot();
		if ((current.state().status() == Status.RUNNING) && current.state().authorityHashes().equals(generation.authorityHashes())) { return Result.success(current, 0); }
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

	/** An offline DEAD return uses the existing exact claim-owned baseline before NORMAL recovery. */
	public Result prepareNativeRecovery(long profileId, PhantomGoal expectedGoal)
	{
		try
		{
			final var stored = _goals.load(profileId).orElse(null);
			if ((stored == null) || !stored.goal().equals(expectedGoal) || (expectedGoal.status() != PhantomGoalStatus.ACTIVE)
				|| !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(expectedGoal.goalType())) { return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, "catchup.native_recovery.goal_mismatch", null); }
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
		if (!currentClaim(profileId, current)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.claim_changed", current); }
		final var claimComponent = _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
		final var goalComponent = _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
		final var recovery = new RecoveryClaim(current, claimComponent, goalComponent);
		if (_materialization.find(profileId).isPresent() || (_recoveryClaims.putIfAbsent(profileId, recovery) != null))
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialization_busy", current);
		}
		try
		{
			if (!currentClaim(profileId, current)) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.claim_changed", current); }
			final var materialized = _materialization.materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, current.state().requestId());
			if (materialized.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialize_" + materialized.status().name().toLowerCase(), current);
			}
			final var dematerialized = _materialization.dematerialize(profileId);
			if (dematerialized.status() != ResultStatus.SUCCESS) { return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.store_retry", current); }
			if (_materialization.find(profileId).isPresent() || !currentClaim(profileId, current)
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
			return failures == null ? Set.of() : failures.exclusions(_visibleClock.getAsLong()).steps();
		});
	}

	/** Handler-side suitability is read-only: publication waits until accept releases inFlight. */
	public boolean visibleFarmReady(long profileId, PhantomGoal goal)
	{
		final var failures = _visibleFailures.get(profileId);
		final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
		final var episode = _visibleEpisodes.get(profileId);
		if ((episode != null) && (episode.epoch == epoch) && (_visibleClock.getAsLong() < episode.cooldownUntil)) { return false; }
		if ((failures != null) && (failures.protocolBlocked(goal, epoch) || failures.routeBlocked(goal, epoch) || failures.exclusions(_visibleClock.getAsLong()).targets().contains(targetKey(goal)))) { return false; }
		return Objects.equals(_goals.load(profileId).map(StoredGoal::goal).orElse(null), goal);
	}

	/** The scheduler boundary owns sync and recovery, outside gameplay/decision locks. */
	public boolean prepareVisibleDecision(long profileId, PhantomDecisionEngine decision)
	{
		try
		{
			final var runtime = decision.find(profileId).orElse(null);
			if ((runtime == null) || runtime.inFlight() || runtime.persistenceInFlight()) { return false; }
			final var receipt = _visiblePublications.get(profileId);
			if (receipt != null) { return finishVisiblePublication(profileId, decision, receipt); }
			final StoredGoal stored = _goals.load(profileId).orElse(null);
			if (stored == null) { return false; }
			if (_foregroundDecisionsRevoked) { return false; }
			final var checkpoint = _background.continueVisibleCheckpoint(profileId, stored.goal()).orElse(null);
			if (checkpoint != null && checkpoint.outcome() != org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome.RESUME
				&& checkpoint.outcome() != org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome.RETRY_BEFORE_WRITE) { return false; }
			if (_background.hasVisibleOwnedStorePending(profileId, stored.goal()))
			{
				final var pending = _materialization.find(profileId).orElse(null);
				final var player = pending == null ? null : org.l2jmobius.gameserver.model.World.getInstance().getPlayer(pending.characterObjectId());
				if ((player == null) || (_background.resumeVisibleOwnedStore(profileId, player, stored.goal()).status() != PhantomBackgroundService.VisibleStoreStatus.SUCCESS)) { return false; }
			}
			if (!permitsDecision(profileId)) { return false; }
			final var admitted = _materialization.find(profileId).orElse(null);
			if ((admitted == null) || !admitted.worldPresent() || !admitted.actionAdmissionOpen() || (admitted.cleanupPhase() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE)) { return false; }
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if ((action == null) || (action.player().getObjectId() != admitted.characterObjectId()) || action.player().hasPendingOwnedStore()) { return false; }
			}
			if (!runtimeMatches(runtime, stored))
			{
				if (_visibleTravel != null) { _visibleTravel.cancelSuperseded(profileId, stored.goal()); }
				if (_visibleAutoPlay != null) { _visibleAutoPlay.stop(profileId); }
				if ((decision.reload(profileId) != PhantomDecisionEngine.ReloadResult.RELOADED) || !runtimeMatches(decision.find(profileId).orElse(null), stored)
					|| !Objects.equals(stored, _goals.load(profileId).orElse(null)) || !permitsDecision(profileId)) { return false; }
			}
			if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(stored.goal().goalType())) { return true; }
			final var lifetime = _materialization.find(profileId).orElse(null);
			if (lifetime == null) { return false; }
			final var failures = _visibleFailures.get(profileId);
			if ((failures != null) && failures.protocolBlocked(stored.goal(), lifetime.materializedAtNanos())) { return false; }
			final var episode = _visibleEpisodes.get(profileId);
			if ((episode != null) && (episode.epoch == lifetime.materializedAtNanos()) && (_visibleClock.getAsLong() < episode.cooldownUntil)) { return false; }
			final var baseline = _background.acquisitionSnapshot(profileId).orElse(null);
			if (baseline == null) { return false; }
			final Player player;
			final PhantomBackgroundState projected;
			final org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint live;
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (action == null) { return false; }
				player = action.player();
				if (player.hasPendingOwnedStore() || (player.getObjectId() != baseline.identity().characterObjectId()) || (player.getActiveClass() != baseline.identity().activeClassId())) { return false; }
				if (player.isDead()) { return true; } // Existing native death/recovery remains the decision adapter's responsibility.
				final var progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
				projected = new PhantomBackgroundState(baseline.state(), baseline.identity(), progress, baseline.vitals(), baseline.position(), baseline.combat(), baseline.loadout(), baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
				live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
			}
			final var exclusions = failures == null ? new Exclusions(Set.of(), Set.of()) : failures.exclusions(_visibleClock.getAsLong());
			final boolean routeRecovery = (failures != null) && failures.routeBlocked(stored.goal(), lifetime.materializedAtNanos());
			if (!routeRecovery && !exclusions.targets().contains(targetKey(stored.goal())) && _planner.isVisibleLocal(stored.goal(), live) && _planner.remainsVisibleSuitable(projected, stored.goal(), live)) { return true; }
			if ((_visibleTravel != null) && !_visibleTravel.stopForRecovery(profileId, stored.goal(), player.getObjectId(), lifetime.materializedAtNanos())) { return false; }
			if (_visibleAutoPlay != null) { _visibleAutoPlay.stop(profileId); }
			if (!visibleActorQuiet(profileId, player, lifetime.materializedAtNanos())) { return false; }
			final Snapshot catchup = _store.load(profileId).orElse(null);
			final var handoff = _foregroundHandoffs.get(profileId);
			if ((catchup == null) || (catchup.state().goalId() != stored.goal().goalId()) || (catchup.state().goalRevision() != stored.goal().revision())
				|| ((catchup.state().status() != Status.COMPLETE) && ((handoff == null) || !exactForegroundHandoff(profileId, handoff)))) { return false; }
			final long now = _visibleClock.getAsLong();
			final var recovery = _visibleEpisodes.compute(profileId, (_, current) -> (current != null) && (current.objectId == player.getObjectId()) && (current.epoch == lifetime.materializedAtNanos()) && (now - current.started < 60_000_000_000L) ? current : new VisibleEpisode(player.getObjectId(), lifetime.materializedAtNanos(), now));
			if (routeRecovery) { recovery.routes.add(stored.goal().revision() + ":" + failures.routeWitness(stored.goal(), lifetime.materializedAtNanos())); }
			else { recovery.targets.add(targetKey(stored.goal())); }
			if ((recovery.targets.size() + recovery.routes.size()) >= 3) { recovery.unavailable(now); return false; }
			final var excluded = new java.util.HashSet<>(exclusions.targets()); excluded.addAll(recovery.targets);
			final long nextOrdinal = Math.addExact(catchup.state().planOrdinal(), 1);
			final var replacement = _planner.replanVisibleLocal(profileId, projected, stored.goal(), catchup.state().deterministicSeed(), nextOrdinal, live, excluded, exclusions.steps());
			if (!replacement.ready()) { recovery.unavailable(now); return false; }
			if ((replacement.generation().knowledgeGeneration() != catchup.state().knowledgeGeneration()) || (replacement.generation().topologyGeneration() != catchup.state().topologyGeneration()) || !replacement.generation().authorityHashes().equals(catchup.state().authorityHashes())) { return false; }
			final var state = catchup.state();
			final var updated = new PhantomBackgroundCatchupState(state.status(), state.requestId(), state.deterministicSeed(), state.fromEpochMinute(), state.targetEpochMinute(), state.cursorEpochMinute(), nextOrdinal, state.intervalOrdinal(), state.generation(), state.knowledgeGeneration(), state.topologyGeneration(), replacement.goal().goalId(), replacement.goal().revision(), replacement.planIdentity(), state.modelVersion(), state.authorityHashes(), state.failureReason());
			final var publication = new VisiblePublication(player, lifetime.materializedAtNanos(), handoff, catchup, stored, updated, replacement.goal());
			if (_visiblePublications.putIfAbsent(profileId, publication) != null) { return false; }
			try { publication.committed = _store.replacePlan(profileId, catchup, updated, stored, replacement.goal()); }
			catch (RuntimeException exception)
			{
				// Resolve an ambiguous DB return only to the exact proposed components; never grant on drift.
				final var persisted = _store.load(profileId).orElse(null); final var persistedGoal = _goals.load(profileId).orElse(null);
				if ((persisted != null) && (persistedGoal != null) && persisted.state().equals(updated) && persistedGoal.goal().equals(replacement.goal()) && (persisted.rowVersion() == catchup.rowVersion() + 1) && (persistedGoal.rowVersion() == stored.rowVersion() + 1)) { publication.committed = new PlannedSnapshot(persisted, persistedGoal); }
				else { _visiblePublications.remove(profileId, publication); return false; }
			}
			if (!routeRecovery) { recovery.targets.add(targetKey(replacement.goal())); }
			return finishVisiblePublication(profileId, decision, publication);
		}
		catch (RuntimeException exception) { return false; }
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
	public String visibleRecoveryReason(long profileId) { final var episode = _visibleEpisodes.get(profileId); return episode == null ? "" : episode.reason; }

	/** A successful exact foreground handoff owns decisions, never ordinary historical work. */
	public boolean permitsDecision(long profileId)
	{
		try
		{
			if (_visiblePublications.containsKey(profileId)) { return false; }
			final Snapshot current = _store.load(profileId).orElse(null);
			if ((current == null) || !current.state().blocksNormalOperation()) { return true; }
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
			return _store.load(profileId).map(snapshot -> !snapshot.state().blocksNormalOperation()).orElse(true);
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
			if ((catchup != null) && catchup.state().blocksNormalOperation())
			{
				throw new AdmissionRejectedException("catchup.normal_fenced");
			}
		}
		final RecoveryClaim recovery = purpose == MaterializationPurpose.HISTORICAL_BASELINE ? _recoveryClaims.get(profileId) : null;
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
		_visiblePublications.remove(profileId);
		_visibleEpisodes.remove(profileId);
		_foregroundHandoffs.computeIfPresent(profileId, (_, handoff) -> handoff.admission().characterObjectId() == characterObjectId ? null : handoff);
		releaseAdmission(profileId, characterObjectId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		_visiblePublications.remove(profileId);
		_visibleEpisodes.remove(profileId);
		_foregroundHandoffs.remove(profileId);
		final RecoveryClaim recovery = _recoveryClaims.get(profileId);
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

	private record RecoveryClaim(Snapshot snapshot, PhantomProfileComponent claim, PhantomProfileComponent goal) { }

	private record Admission(int characterObjectId, MaterializationPurpose purpose, String ownerClaim, RecoveryClaim recovery, PhantomProfileComponent claim, PhantomProfileComponent goal, Snapshot catchup)
	{
	}

	private record ActiveForegroundHandoff(Admission admission, long materializedAtNanos) { }

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
		private final LinkedHashMap<String, Long> _targets = new LinkedHashMap<>();
		private final LinkedHashMap<String, Long> _steps = new LinkedHashMap<>();
		private volatile long _lastFailureNanos;
		private ProtocolFailure _protocol;
		private RouteFailure _route;

		private synchronized void route(PhantomGoal goal, long epoch, String witness, long now)
		{
			_lastFailureNanos = now;
			put(_steps, witness, now);
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

		private synchronized void record(String target, String step, long now)
		{
			_lastFailureNanos = now;
			put(_targets, target, now);
			if (!step.isEmpty())
			{
				put(_steps, step, now);
			}
		}

		private static void put(LinkedHashMap<String, Long> values, String key, long now)
		{
			values.remove(key);
			values.put(key, now);
			if (values.size() > 8)
			{
				values.remove(values.keySet().iterator().next());
			}
		}

		private synchronized Exclusions exclusions(long now)
		{
			_targets.values().removeIf(time -> (now - time) >= TTL_NANOS);
			_steps.values().removeIf(time -> (now - time) >= TTL_NANOS);
			return new Exclusions(Set.copyOf(_targets.keySet()), Set.copyOf(_steps.keySet()));
		}
	}

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
