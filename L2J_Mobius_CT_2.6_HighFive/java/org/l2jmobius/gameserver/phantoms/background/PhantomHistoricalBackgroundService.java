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
	private final ConcurrentHashMap<Long, String> _recoveryClaims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisibleFailures> _visibleFailures = new ConcurrentHashMap<>();
	private final LongSupplier _visibleClock;

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
		final Snapshot existing = _store.load(profileId).orElse(null);
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
		Snapshot current = claimed;
		StoredGoal storedGoal = _goals.load(profileId).orElse(null);
		final Optional<PhantomBackgroundState> existingBackground = _background.acquisitionSnapshot(profileId);
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

	/** Only classified recoverable failures may re-enter the same optimistic history ownership. */
	public static boolean isRecoverableFailure(String reason)
	{
		return Set.of("transaction.item_conflict", "model.object_cap", "model.object_cap_indivisible", "catchup.authority.unsupported", "planner.target_or_route.absent", "authority.hash_stale", "catchup.authority.authority_stale", "catchup.authority.position_stale", "catchup.authority.target_stale", "catchup.authority.resource_stale", "catchup.authority.unsupported_loot").contains(reason);
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

	private Result refreshCanonicalBaseline(long profileId, Snapshot current)
	{
		if (_materialization.find(profileId).isPresent() || (_recoveryClaims.putIfAbsent(profileId, current.state().requestId()) != null))
		{
			return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialization_busy", current);
		}
		try
		{
			final var materialized = _materialization.materialize(profileId, MaterializationPurpose.HISTORICAL_BASELINE, current.state().requestId());
			if (materialized.status() != ResultStatus.SUCCESS)
			{
				return Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.materialize_" + materialized.status().name().toLowerCase(), current);
			}
			final var dematerialized = _materialization.dematerialize(profileId);
			return dematerialized.status() == ResultStatus.SUCCESS ? Result.success(current, 0) : Result.rejected(ResultStatusCode.RETRY, "catchup.recovery.store_retry", current);
		}
		finally
		{
			_recoveryClaims.remove(profileId, current.state().requestId());
		}
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
		final var failures = _visibleFailures.get(profileId);
		final var exclusions = failures == null ? new Exclusions(Set.of(), Set.of()) : failures.exclusions(_visibleClock.getAsLong());
		final var spec = PhantomBackgroundGoalSpec.parse(goal);
		final boolean failedTarget = exclusions.targets().contains(spec.npcId() + "@" + spec.anchorId());
		final Snapshot catchup = status(profileId).orElse(null);
		if ((catchup == null) || (catchup.state().status() != Status.COMPLETE) || (catchup.state().goalId() != goal.goalId()))
		{
			return !failedTarget;
		}
		final PhantomBackgroundState baseline = _background.acquisitionSnapshot(profileId).orElse(null);
		if (baseline == null)
		{
			return !failedTarget;
		}
		try (ActionLease action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || action.player().isDead())
			{
				return !failedTarget;
			}
			final Player player = action.player();
			if ((player.getObjectId() != baseline.identity().characterObjectId()) || (player.getActiveClass() != baseline.identity().activeClassId()))
			{
				return !failedTarget;
			}
			final Progress progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
			final PhantomBackgroundState projected = new PhantomBackgroundState(baseline.state(), baseline.identity(), progress, baseline.vitals(), baseline.position(), baseline.combat(), baseline.loadout(), baseline.inventory(), baseline.autoGetSkills(), baseline.clock(), baseline.receipt(), baseline.hashes());
			if (!failedTarget && _planner.remainsSuitable(projected, goal))
			{
				return true;
			}
			final long nextOrdinal = Math.addExact(catchup.state().planOrdinal(), 1);
			final var replacement = _planner.replan(profileId, projected, goal, catchup.state().deterministicSeed(), nextOrdinal, exclusions.targets(), exclusions.steps());
			if (!replacement.ready() || (decision.setGoal(profileId, replacement.goal()) != PhantomDecisionEngine.MutationResult.APPLIED))
			{
				return false;
			}
			try
			{
				final PhantomBackgroundCatchupState updated = catchup.state().withPlan(replacement.goal().goalId(), replacement.goal().revision(), nextOrdinal, replacement.planIdentity(), replacement.generation().knowledgeGeneration(), replacement.generation().topologyGeneration(), replacement.generation().authorityHashes());
				_store.replace(profileId, catchup, updated);
			}
			catch (RuntimeException exception)
			{
				// The next completed catch-up renewal resolves a stale plan revision.
			}
			return false;
		}
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
		else if ((catchup == null) || !catchup.state().owns(ownerClaim) || ((catchup.state().status() != Status.PENDING) && !ownerClaim.equals(_recoveryClaims.get(profileId))))
		{
			throw new AdmissionRejectedException("catchup.historical_claim_invalid");
		}
		final Admission admission = new Admission(characterObjectId, purpose, ownerClaim);
		if (_admissions.putIfAbsent(profileId, admission) != null)
		{
			throw new AdmissionRejectedException("catchup.materialization_transition_busy");
		}
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
		releaseAdmission(profileId, characterObjectId);
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		releaseAdmission(profileId, characterObjectId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
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

	private record Admission(int characterObjectId, MaterializationPurpose purpose, String ownerClaim)
	{
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
