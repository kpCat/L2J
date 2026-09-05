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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.PlannedSnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
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

	public PhantomHistoricalBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomHistoricalBackgroundPlanner planner, PhantomBackgroundService background, PhantomMaterializationService materialization)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
		_goals = Objects.requireNonNull(goals, "goals");
		_store = new PhantomBackgroundCatchupStore(profiles, goals);
		_planner = Objects.requireNonNull(planner, "planner");
		_background = Objects.requireNonNull(background, "background");
		_materialization = Objects.requireNonNull(materialization, "materialization");
	}

	public Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
	{
		if ((profileId <= 0) || (fromEpochMinute < 0) || (targetEpochMinute <= fromEpochMinute) || (targetEpochMinute - fromEpochMinute > Integer.MAX_VALUE))
		{
			return Result.rejected(ResultStatusCode.INVALID_REQUEST, "catchup.request.invalid", null);
		}
		if (_profiles.find(profileId).filter(profile -> profile.characterObjectId() != null).isEmpty())
		{
			return Result.rejected(ResultStatusCode.PROFILE_UNAVAILABLE, "catchup.profile.unlinked", null);
		}
		final Snapshot existing = _store.load(profileId).orElse(null);
		if ((existing == null) && _materialization.find(profileId).isPresent())
		{
			return Result.rejected(ResultStatusCode.NORMAL_MATERIALIZED, "catchup.normal_materialized", null);
		}
		final var generation = _planner.generation();
		final String requestId = digest("BACKGROUND_CATCHUP_REQUEST_V1", profileId, fromEpochMinute, targetEpochMinute, deterministicSeed, generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes());
		final long catchupGeneration = positiveLong(digest("BACKGROUND_CATCHUP_GENERATION_V1", requestId));
		final PhantomBackgroundCatchupState initial = new PhantomBackgroundCatchupState(Status.PENDING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, fromEpochMinute, 0, 0, catchupGeneration, generation.knowledgeGeneration(), generation.topologyGeneration(), 0, 0, "", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
		final Snapshot claimed;
		try
		{
			claimed = _store.claim(profileId, initial);
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
			return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, claimed.state().failureReason(), claimed);
		}
		return ensureBaseline(profileId, claimed);
	}

	private Result ensureBaseline(long profileId, Snapshot claimed)
	{
		Snapshot current = claimed;
		StoredGoal storedGoal = _goals.load(profileId).orElse(null);
		final Optional<PhantomBackgroundState> existingBackground = _background.acquisitionSnapshot(profileId);
		if (existingBackground.isPresent())
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
						final var plan = _planner.planInitial(profileId, lease.player(), current.state().deterministicSeed(), current.state().planOrdinal());
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
		if (current.state().status() == Status.FAILED_REPLAN_REQUIRED)
		{
			return Result.rejected(ResultStatusCode.REPLAN_REQUIRED, current.state().failureReason(), current);
		}
		final var generation = _planner.generation();
		if (!current.state().authorityHashes().equals(generation.authorityHashes()) || (current.state().knowledgeGeneration() != generation.knowledgeGeneration()) || (current.state().topologyGeneration() != generation.topologyGeneration()))
		{
			return fail(profileId, current, "catchup.authority_hash_or_generation_stale");
		}
		int advanced = 0;
		final int limit = Math.min(maximumIntervals, maximumSimulatedMinutes);
		while ((advanced < limit) && (current.state().status() == Status.RUNNING))
		{
			final PhantomBackgroundState backgroundState = _background.acquisitionSnapshot(profileId).orElse(null);
			StoredGoal storedGoal = _goals.load(profileId).orElse(null);
			if ((backgroundState == null) || (storedGoal == null) || (storedGoal.goal().goalId() != current.state().goalId()) || (storedGoal.goal().revision() != current.state().goalRevision()))
			{
				return fail(profileId, current, "catchup.runtime_state_or_goal_conflict");
			}
			if (((backgroundState.state() != PhantomBackgroundState.State.READY) && (backgroundState.state() != PhantomBackgroundState.State.DEAD)) || !backgroundState.hashes().equals(current.state().authorityHashes()))
			{
				return fail(profileId, current, "catchup.background_state_stale");
			}
			if ((backgroundState.state() == PhantomBackgroundState.State.READY) && !_planner.remainsSuitable(backgroundState, storedGoal.goal()))
			{
				final long nextPlanOrdinal = Math.addExact(current.state().planOrdinal(), 1);
				final var replanned = _planner.replan(profileId, backgroundState, storedGoal.goal(), current.state().deterministicSeed(), nextPlanOrdinal);
				if (!replanned.ready())
				{
					return fail(profileId, current, replanned.reasonKey());
				}
				try
				{
					final PhantomBackgroundCatchupState replannedState = current.state().withPlan(replanned.goal().goalId(), replanned.goal().revision(), nextPlanOrdinal, replanned.planIdentity(), replanned.generation().knowledgeGeneration(), replanned.generation().topologyGeneration());
					final PlannedSnapshot persisted = _store.replacePlan(profileId, current, replannedState, storedGoal, replanned.goal());
					current = persisted.catchup();
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
				continue;
			}
			if (operation.status() == PhantomBackgroundService.OperationStatus.RETRY)
			{
				return Result.rejected(ResultStatusCode.RETRY, operation.reason(), observed);
			}
			return fail(profileId, observed, operation.reason());
		}
		return Result.success(current, advanced);
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
		else if ((catchup == null) || (catchup.state().status() != Status.PENDING) || !catchup.state().owns(ownerClaim))
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