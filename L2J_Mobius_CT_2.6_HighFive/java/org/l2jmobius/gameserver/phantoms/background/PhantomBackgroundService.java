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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.FarmInput;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Synchronous coordinator for bounded background operations. It owns no thread,
 * timer, executor or per-profile scheduled task.
 */
public final class PhantomBackgroundService implements PhantomMaterializationLifecyclePort
{
	public static final long FARM_TRAVEL_BUDGET_MILLIS = 60_000;
	public static final long DEATH_SIGNAL_TTL_MILLIS = 60_000;
	public static final String DEATH_SIGNAL_SOURCE = "background.death";

	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomIdentityLeaseRegistry _identities;
	private final PhantomBackgroundTransaction _transactions;
	private final PhantomBackgroundAuthority _authority;
	private final PhantomBackgroundModel _model;
	private final PhantomBackgroundCompetitionRegistry _competition;
	private final PhantomRelevanceSignalPort _signals;
	private final Supplier<PhantomMaterializationService> _materialization;
	private final ConcurrentHashMap<Long, Boolean> _operations = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, TransitionKind> _transitions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Lease> _retainedIdentityLeases = new ConcurrentHashMap<>();
	private final AtomicInteger _currentOperations = new AtomicInteger();
	private final AtomicInteger _currentIdentityLeases = new AtomicInteger();
	private final AtomicInteger _currentTransactions = new AtomicInteger();
	private final AtomicInteger _currentTransitionClaims = new AtomicInteger();
	private final AtomicInteger _peakOperations = new AtomicInteger();
	private final AtomicInteger _peakIdentityLeases = new AtomicInteger();
	private final AtomicInteger _peakTransactions = new AtomicInteger();
	private final AtomicInteger _peakTransitionClaims = new AtomicInteger();
	private final AtomicLong _completedOperations = new AtomicLong();
	private final AtomicLong _idempotentOperations = new AtomicLong();
	private final AtomicLong _retryOperations = new AtomicLong();
	private final AtomicLong _failedOperations = new AtomicLong();
	private volatile ServiceState _state = ServiceState.NEW;

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization)
	{
		this(profiles, goals, identities, transactions, authority, new PhantomBackgroundModel(), competition, signals, materialization);
	}

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundModel model, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
		_goals = Objects.requireNonNull(goals, "goals");
		_identities = Objects.requireNonNull(identities, "identities");
		_transactions = Objects.requireNonNull(transactions, "transactions");
		_authority = Objects.requireNonNull(authority, "authority");
		_model = Objects.requireNonNull(model, "model");
		_competition = Objects.requireNonNull(competition, "competition");
		_signals = Objects.requireNonNull(signals, "signals");
		_materialization = Objects.requireNonNull(materialization, "materialization");
	}

	public synchronized boolean start()
	{
		if (_state == ServiceState.RUNNING)
		{
			return true;
		}
		if (_state != ServiceState.NEW)
		{
			return false;
		}
		_state = ServiceState.RUNNING;
		return true;
	}

	public synchronized boolean beginStop()
	{
		if (_state == ServiceState.STOPPED)
		{
			return false;
		}
		if (_state == ServiceState.NEW)
		{
			_state = ServiceState.STOPPED;
			return true;
		}
		if (_state == ServiceState.RUNNING)
		{
			_state = ServiceState.STOPPING;
		}
		return true;
	}

	public synchronized boolean finishStop()
	{
		if (_state == ServiceState.STOPPED)
		{
			return true;
		}
		if ((_state != ServiceState.STOPPING) && (_state != ServiceState.FAILED))
		{
			return false;
		}
		if ((_currentOperations.get() != 0) || (_currentIdentityLeases.get() != 0) || (_currentTransactions.get() != 0) || (_currentTransitionClaims.get() != 0) || !_retainedIdentityLeases.isEmpty())
		{
			return false;
		}
		_state = ServiceState.STOPPED;
		return true;
	}

	public Directive directive(long profileId, PhantomGoal goal, PhantomActivityState activityState)
	{
		if (_state != ServiceState.RUNNING)
		{
			return new Directive(DirectiveKind.RETRY, "service.not_running", "");
		}
		final PhantomBackgroundGoalSpec spec;
		try
		{
			spec = PhantomBackgroundGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			return new Directive(DirectiveKind.REPLAN, "goal.invalid", "");
		}
		final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
		{
			return new Directive(DirectiveKind.RETRY, "state.absent", "");
		}
		if (!loaded.successful() || (loaded.state() == null))
		{
			return new Directive(loaded.status() == PhantomBackgroundTransaction.Status.INCONSISTENT ? DirectiveKind.INCONSISTENT : DirectiveKind.RETRY, "state." + loaded.status().name().toLowerCase(), "");
		}
		final PhantomBackgroundState state = loaded.state();
		if ((activityState == PhantomActivityState.WARM) || (activityState == PhantomActivityState.ACTIVE))
		{
			return state.state() == State.DEAD ? new Directive(DirectiveKind.RECOVER, "state.dead", spec.anchorId()) : new Directive(DirectiveKind.REPLAN, "recovery.not_dead", state.position().committedAnchorId());
		}
		if (activityState != PhantomActivityState.BACKGROUND)
		{
			return new Directive(DirectiveKind.REPLAN, "activity.unsupported", state.position().committedAnchorId());
		}
		if (state.state() == State.DEAD)
		{
			return new Directive(DirectiveKind.RETRY, "state.dead", state.position().committedAnchorId());
		}
		if (state.state() != State.READY)
		{
			return new Directive(state.state() == State.INCONSISTENT ? DirectiveKind.INCONSISTENT : DirectiveKind.RETRY, "state." + state.state().name().toLowerCase(), state.position().committedAnchorId());
		}
		return state.position().committedAnchorId().equals(spec.anchorId()) ? new Directive(DirectiveKind.FARM, "farm.ready", spec.anchorId()) : new Directive(DirectiveKind.TRAVEL, "travel.required", spec.anchorId());
	}

	public OperationResult farm(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos)
	{
		if (activityState != PhantomActivityState.BACKGROUND)
		{
			return OperationResult.replan("activity.not_background");
		}
		final OperationClaim claim = acquire(profileId, goal, activityGeneration, tickSequence);
		if (!claim.acquired())
		{
			return claim.failure();
		}
		try (claim)
		{
			final PhantomBackgroundState state = claim.state();
			if (!state.acceptsBackgroundWork())
			{
				return retry("state.not_ready");
			}
			final PhantomBackgroundGoalSpec spec = claim.spec();
			if (!state.position().committedAnchorId().equals(spec.anchorId()))
			{
				return OperationResult.replan("travel.required");
			}
			final FarmInput input;
			try
			{
				input = _authority.farmInput(state, spec);
			}
			catch (RuntimeException exception)
			{
				return OperationResult.replan("authority.unsupported");
			}
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), activityGeneration, tickSequence, ActionKind.FARM, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes());
			try (PhantomBackgroundCompetitionRegistry.Reservation reservation = _competition.tryReserve(input.topologyNodeId(), spec.npcId(), input.spawnCapacity()))
			{
				if (reservation == null)
				{
					return retry("competition.capacity");
				}
				final BatchResult batch = _model.evaluate(new BatchRequest(state, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
				if (!batch.mutated())
				{
					return switch (batch.reason())
					{
						case STATE_NOT_READY, TIME_BUDGET -> retry("model." + batch.reason().name().toLowerCase());
						default -> OperationResult.replan("model." + batch.reason().name().toLowerCase());
					};
				}
				final List<PhantomBackgroundState.AutoGetSkill> autoSkills = _authority.autoGetSkills(state.identity(), batch.progress().level());
				final Clock clock = new Clock(batch.nextRngState(), 0, 0);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, batch.progress(), batch.vitals(), state.position(), clock, batch.inventoryDelta().itemDeltas(), autoSkills);
				final OperationResult result = commit(claim, command);
				if (result.successful() && batch.dead())
				{
					_signals.submit(profileId, new PhantomRelevanceSignal(DEATH_SIGNAL_SOURCE, tickSequence, PhantomActivityState.WARM, DEATH_SIGNAL_TTL_MILLIS));
				}
				return result.withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
			}
		}
	}

	public OperationResult travel(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos)
	{
		if (activityState != PhantomActivityState.BACKGROUND)
		{
			return OperationResult.replan("activity.not_background");
		}
		final OperationClaim claim = acquire(profileId, goal, activityGeneration, tickSequence);
		if (!claim.acquired())
		{
			return claim.failure();
		}
		try (claim)
		{
			final PhantomBackgroundState state = claim.state();
			if (!state.acceptsBackgroundWork())
			{
				return retry("state.not_ready");
			}
			final TravelAdvance advance = _authority.advanceTravel(state, claim.spec(), FARM_TRAVEL_BUDGET_MILLIS);
			if (!advance.mutated())
			{
				return switch (advance.status())
				{
					case AT_DESTINATION -> OperationResult.success("travel.at_destination");
					case EDGE_CLOSED, NO_ROUTE -> retry("travel." + advance.status().name().toLowerCase());
					default -> OperationResult.replan("travel." + advance.status().name().toLowerCase());
				};
			}
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), activityGeneration, tickSequence, ActionKind.TRAVEL, claim.spec().npcId(), claim.spec().anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes());
			final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), advance.position(), advance.clock(), Map.of(), state.autoGetSkills());
			return commit(claim, command);
		}
	}

	public OperationResult recover(long profileId, PhantomGoal goal, PhantomActivityState activityState)
	{
		if ((activityState != PhantomActivityState.WARM) && (activityState != PhantomActivityState.ACTIVE))
		{
			return OperationResult.replan("recovery.activity");
		}
		try
		{
			PhantomBackgroundGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			return OperationResult.replan("recovery.goal");
		}
		final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		if (!loaded.successful() || (loaded.state() == null) || (loaded.state().state() != State.DEAD))
		{
			return OperationResult.replan("recovery.not_dead");
		}
		final PhantomMaterializationService materialization = _materialization.get();
		if (materialization == null)
		{
			return retry("recovery.materialization_absent");
		}
		final PhantomMaterializationService.MaterializeResult materialized = materialization.materialize(profileId);
		if ((materialized.status() != ResultStatus.SUCCESS) && (materialized.status() != ResultStatus.ALREADY_ACTIVE))
		{
			return retry("recovery.materialization_" + materialized.status().name().toLowerCase());
		}
		final Optional<ActionLease> action = materialization.tryAcquireAction(profileId);
		if (action.isEmpty())
		{
			return retry("recovery.action_lease");
		}
		try (ActionLease lease = action.get())
		{
			final Player player = lease.player();
			if (!player.isDead())
			{
				return OperationResult.inconsistent("recovery.runtime_not_dead");
			}
			player.doRevive();
			player.teleToLocation(TeleportWhereType.TOWN);
		}
		final PhantomMaterializationService.DematerializeResult dematerialized = materialization.dematerialize(profileId);
		if (dematerialized.status() != ResultStatus.SUCCESS)
		{
			return OperationResult.inconsistent("recovery.store_failed");
		}
		final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, loaded.state().identity().characterObjectId()));
		if (!verified.successful() || (verified.state() == null) || (verified.state().state() != State.READY))
		{
			return OperationResult.inconsistent("recovery.verification_failed");
		}
		return OperationResult.failGoal("death.recovered_at_town");
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
		if (!claimTransition(profileId, TransitionKind.MATERIALIZING))
		{
			throw new IllegalStateException("Background transition is already owned.");
		}
		boolean retained = false;
		try
		{
			if (_operations.containsKey(profileId))
			{
				throw new IllegalStateException("Background operation has not drained.");
			}
			final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
			if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
			{
				retained = true;
				return;
			}
			if (!loaded.successful() || (loaded.state() == null) || (loaded.state().identity().characterObjectId() != characterObjectId) || (loaded.state().state() == State.MATERIALIZED) || (loaded.state().state() == State.INCONSISTENT))
			{
				throw new IllegalStateException("Background state cannot enter materialization.");
			}
			final PhantomBackgroundTransaction.Result reconciled = transaction(() -> _transactions.reconcileVerifyPending(profileId, characterObjectId));
			if (!reconciled.successful() || (reconciled.state() == null) || ((reconciled.state().state() != State.READY) && (reconciled.state().state() != State.DEAD)))
			{
				throw new IllegalStateException("Background state reconciliation failed.");
			}
			retained = true;
		}
		finally
		{
			if (!retained)
			{
				releaseTransition(profileId, TransitionKind.MATERIALIZING);
			}
		}
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
		requireTransition(profileId, TransitionKind.MATERIALIZING);
		final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
		{
			releaseTransition(profileId, TransitionKind.MATERIALIZING);
			return;
		}
		if (!loaded.successful() || (loaded.state() == null) || !_authority.matchesRuntime(player, loaded.state()))
		{
			throw new IllegalStateException("Loaded Player differs from committed background state.");
		}
		final PhantomBackgroundTransaction.Result marked = transaction(() -> _transactions.markMaterialized(profileId, player.getObjectId()));
		if (!marked.successful())
		{
			throw new IllegalStateException("Background state could not be marked MATERIALIZED.");
		}
		final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, player.getObjectId()));
		if (!verified.successful() || (verified.state() == null) || (verified.state().state() != State.MATERIALIZED))
		{
			throw new IllegalStateException("MATERIALIZED state verification failed.");
		}
		releaseTransition(profileId, TransitionKind.MATERIALIZING);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		if (!claimTransition(profileId, TransitionKind.DEMATERIALIZING))
		{
			throw new IllegalStateException("Background transition is already owned.");
		}
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
		requireTransition(profileId, TransitionKind.DEMATERIALIZING);
		final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		final PhantomBackgroundState previous = loaded.successful() ? loaded.state() : null;
		if ((loaded.status() != PhantomBackgroundTransaction.Status.STATE_ABSENT) && !loaded.successful())
		{
			throw new IllegalStateException("Existing background state could not be read after Player.storeMe().");
		}
		final PhantomGoal goal = _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
		if (goal == null)
		{
			if (previous != null)
			{
				throw new IllegalStateException("Existing background state lost its explicit goal.");
			}
			releaseTransition(profileId, TransitionKind.DEMATERIALIZING);
			return;
		}
		try
		{
			PhantomBackgroundGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			if (previous != null)
			{
				throw new IllegalStateException("Existing background state no longer has an exact farm.background goal.", exception);
			}
			releaseTransition(profileId, TransitionKind.DEMATERIALIZING);
			return;
		}
		final PhantomBackgroundState captured = _authority.capture(profileId, player, goal, previous);
		final PhantomBackgroundTransaction.Result stored = transaction(() -> _transactions.captureBaseline(captured, goal));
		if (!stored.successful())
		{
			throw new IllegalStateException("Canonical background baseline capture failed.");
		}
		final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, player.getObjectId()));
		if (!verified.successful() || (verified.state() == null) || ((verified.state().state() != State.READY) && (verified.state().state() != State.DEAD)))
		{
			throw new IllegalStateException("Canonical background baseline verification failed.");
		}
		releaseTransition(profileId, TransitionKind.DEMATERIALIZING);
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_state, _currentOperations.get(), _peakOperations.get(), _currentIdentityLeases.get(), _peakIdentityLeases.get(), _currentTransactions.get(), _peakTransactions.get(), _currentTransitionClaims.get(), _peakTransitionClaims.get(), _completedOperations.get(), _idempotentOperations.get(), _retryOperations.get(), _failedOperations.get(), _retainedIdentityLeases.size());
	}

	private OperationClaim acquire(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence)
	{
		if ((activityGeneration <= 0) || (tickSequence <= 0))
		{
			return OperationClaim.failed(OperationResult.replan("activity.identity_invalid"));
		}
		synchronized (this)
		{
			if (_state != ServiceState.RUNNING)
			{
				return OperationClaim.failed(retry("service.not_running"));
			}
			if (_transitions.containsKey(profileId) || (_operations.putIfAbsent(profileId, Boolean.TRUE) != null))
			{
				return OperationClaim.failed(retry("ownership.transition_or_operation"));
			}
			increment(_currentOperations, _peakOperations);
		}
		Lease lease = null;
		boolean leaseCounted = false;
		try
		{
			final PhantomProfile profile = _profiles.find(profileId).orElse(null);
			if ((profile == null) || (profile.characterObjectId() == null))
			{
				return releaseFailedOperation(profileId, OperationResult.replan("profile.unlinked"));
			}
			final int characterObjectId = profile.characterObjectId();
			lease = _identities.tryAcquire(characterObjectId, OwnerKind.BACKGROUND);
			if (lease == null)
			{
				return releaseFailedOperation(profileId, retry("identity.busy"));
			}
			increment(_currentIdentityLeases, _peakIdentityLeases);
			leaseCounted = true;
			if ((World.getInstance().getPlayer(characterObjectId) != null) || (World.getInstance().findObject(characterObjectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, retry("identity.runtime_busy"));
			}
			final PhantomGoal actual = _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
			final PhantomBackgroundGoalSpec spec;
			try
			{
				spec = PhantomBackgroundGoalSpec.parse(actual);
			}
			catch (IllegalArgumentException exception)
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, OperationResult.replan("goal.not_current"));
			}
			if (!Objects.equals(actual, goal))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, OperationResult.replan("goal.stale"));
			}
			PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
			if ((loaded.state() != null) && (loaded.state().state() == State.VERIFY_PENDING))
			{
				loaded = transaction(() -> _transactions.reconcileVerifyPending(profileId, characterObjectId));
			}
			if (!loaded.successful() || (loaded.state() == null))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, mapTransactionFailure(loaded.status()));
			}
			if (!loaded.state().hashes().equals(_authority.hashes()))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, OperationResult.replan("authority.hash_stale"));
			}
			return OperationClaim.acquired(this, profileId, characterObjectId, lease, loaded.state(), spec);
		}
		catch (RuntimeException exception)
		{
			if ((lease != null) && leaseCounted)
			{
				closeLease(lease);
			}
			return releaseFailedOperation(profileId, retry("background.acquire_failed"));
		}
	}

	private OperationResult commit(OperationClaim claim, PhantomBackgroundTransaction.Command command)
	{
		PhantomBackgroundTransaction.Result result = transaction(() -> _transactions.execute(command));
		if ((result.status() == PhantomBackgroundTransaction.Status.COMMIT_OUTCOME_UNKNOWN) || (result.status() == PhantomBackgroundTransaction.Status.POST_COMMIT_VERIFICATION_FAILED))
		{
			final PhantomBackgroundTransaction.Result retryVerification = transaction(() -> _transactions.reconcileVerifyPending(claim.profileId(), claim.characterObjectId()));
			if (retryVerification.successful())
			{
				result = retryVerification;
			}
			else
			{
				claim.retainIdentity();
				failStop();
				_failedOperations.incrementAndGet();
				return OperationResult.inconsistent("transaction.outcome_unverified");
			}
		}
		if (result.status() == PhantomBackgroundTransaction.Status.IDEMPOTENT)
		{
			final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(claim.profileId(), claim.characterObjectId()));
			if (!verified.successful())
			{
				claim.retainIdentity();
				failStop();
				_failedOperations.incrementAndGet();
				return OperationResult.inconsistent("transaction.idempotent_unverified");
			}
			_idempotentOperations.incrementAndGet();
			return OperationResult.idempotent("transaction.idempotent");
		}
		if (result.status() == PhantomBackgroundTransaction.Status.SUCCESS)
		{
			_completedOperations.incrementAndGet();
			return OperationResult.success("transaction.committed");
		}
		final OperationResult failure = mapTransactionFailure(result.status());
		if (failure.status() == OperationStatus.INCONSISTENT)
		{
			_failedOperations.incrementAndGet();
		}
		return failure;
	}

	private OperationResult mapTransactionFailure(PhantomBackgroundTransaction.Status status)
	{
		return switch (status)
		{
			case STALE_OPERATION, GOAL_STALE, HASH_STALE, STATE_CONFLICT, STATE_ABSENT, PROFILE_LINK_STALE -> OperationResult.replan("transaction." + status.name().toLowerCase());
			case INCONSISTENT, CANONICAL_MISMATCH, ITEM_CONFLICT, ITEM_LIMIT, UNSUPPORTED_ITEM, UNSUPPORTED_INSTANCE, OBJECT_ID_EXHAUSTED, PROGRESSION_CONFLICT -> OperationResult.inconsistent("transaction." + status.name().toLowerCase());
			default -> retry("transaction." + status.name().toLowerCase());
		};
	}

	private OperationResult retry(String reason)
	{
		_retryOperations.incrementAndGet();
		return OperationResult.retry(reason);
	}

	private OperationClaim releaseFailedOperation(long profileId, OperationResult result)
	{
		_operations.remove(profileId);
		_currentOperations.decrementAndGet();
		return OperationClaim.failed(result);
	}

	private <T> T transaction(Supplier<T> operation)
	{
		increment(_currentTransactions, _peakTransactions);
		try
		{
			return operation.get();
		}
		finally
		{
			_currentTransactions.decrementAndGet();
		}
	}

	private boolean claimTransition(long profileId, TransitionKind kind)
	{
		synchronized (this)
		{
			final boolean lifecycleAllowed = (_state == ServiceState.RUNNING) || ((kind == TransitionKind.DEMATERIALIZING) && (_state == ServiceState.STOPPING));
			if (!lifecycleAllowed || _operations.containsKey(profileId) || (_transitions.putIfAbsent(profileId, kind) != null))
			{
				return false;
			}
			increment(_currentTransitionClaims, _peakTransitionClaims);
			return true;
		}
	}

	private void requireTransition(long profileId, TransitionKind kind)
	{
		if (_transitions.get(profileId) != kind)
		{
			throw new IllegalStateException("Background lifecycle transition identity mismatch.");
		}
	}

	private void releaseTransition(long profileId, TransitionKind kind)
	{
		if (_transitions.remove(profileId, kind))
		{
			_currentTransitionClaims.decrementAndGet();
		}
	}

	private void closeLease(Lease lease)
	{
		if ((lease != null) && !lease.isClosed())
		{
			lease.close();
			_currentIdentityLeases.decrementAndGet();
		}
	}

	private synchronized void failStop()
	{
		_state = ServiceState.FAILED;
	}

	private static void increment(AtomicInteger current, AtomicInteger peak)
	{
		final int value = current.incrementAndGet();
		peak.accumulateAndGet(value, Math::max);
	}

	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public enum DirectiveKind
	{
		FARM,
		TRAVEL,
		RECOVER,
		RETRY,
		REPLAN,
		INCONSISTENT
	}

	public enum OperationStatus
	{
		SUCCESS,
		IDEMPOTENT,
		RETRY,
		REPLAN,
		INCONSISTENT,
		FAIL_GOAL
	}

	private enum TransitionKind
	{
		MATERIALIZING,
		DEMATERIALIZING
	}

	public record Directive(DirectiveKind kind, String reason, String anchorId)
	{
		public Directive
		{
			Objects.requireNonNull(kind, "kind");
			reason = Objects.requireNonNullElse(reason, "");
			anchorId = Objects.requireNonNullElse(anchorId, "");
		}
	}

	public record OperationResult(OperationStatus status, String reason, int encounters, long elapsedMillis, boolean dead)
	{
		public OperationResult
		{
			Objects.requireNonNull(status, "status");
			reason = Objects.requireNonNullElse(reason, "");
		}

		public static OperationResult success(String reason)
		{
			return new OperationResult(OperationStatus.SUCCESS, reason, 0, 0, false);
		}

		public static OperationResult idempotent(String reason)
		{
			return new OperationResult(OperationStatus.IDEMPOTENT, reason, 0, 0, false);
		}

		public static OperationResult retry(String reason)
		{
			return new OperationResult(OperationStatus.RETRY, reason, 0, 0, false);
		}

		public static OperationResult replan(String reason)
		{
			return new OperationResult(OperationStatus.REPLAN, reason, 0, 0, false);
		}

		public static OperationResult inconsistent(String reason)
		{
			return new OperationResult(OperationStatus.INCONSISTENT, reason, 0, 0, false);
		}

		public static OperationResult failGoal(String reason)
		{
			return new OperationResult(OperationStatus.FAIL_GOAL, reason, 0, 0, false);
		}

		public boolean successful()
		{
			return (status == OperationStatus.SUCCESS) || (status == OperationStatus.IDEMPOTENT);
		}

		private OperationResult withModel(int nextEncounters, long nextElapsedMillis, boolean nextDead)
		{
			return new OperationResult(status, reason, nextEncounters, nextElapsedMillis, nextDead);
		}
	}

	public record Snapshot(ServiceState state, int currentOperations, int peakOperations, int currentIdentityLeases, int peakIdentityLeases, int currentTransactions, int peakTransactions, int currentTransitionClaims, int peakTransitionClaims, long completedOperations, long idempotentOperations, long retryOperations, long failedOperations, int retainedIdentityLeases)
	{
	}

	private static final class OperationClaim implements AutoCloseable
	{
		private final PhantomBackgroundService _owner;
		private final long _profileId;
		private final int _characterObjectId;
		private final Lease _lease;
		private final PhantomBackgroundState _state;
		private final PhantomBackgroundGoalSpec _spec;
		private final OperationResult _failure;
		private boolean _identityRetained;
		private boolean _closed;

		private OperationClaim(PhantomBackgroundService owner, long profileId, int characterObjectId, Lease lease, PhantomBackgroundState state, PhantomBackgroundGoalSpec spec, OperationResult failure)
		{
			_owner = owner;
			_profileId = profileId;
			_characterObjectId = characterObjectId;
			_lease = lease;
			_state = state;
			_spec = spec;
			_failure = failure;
		}

		private static OperationClaim acquired(PhantomBackgroundService owner, long profileId, int characterObjectId, Lease lease, PhantomBackgroundState state, PhantomBackgroundGoalSpec spec)
		{
			return new OperationClaim(owner, profileId, characterObjectId, lease, state, spec, null);
		}

		private static OperationClaim failed(OperationResult failure)
		{
			return new OperationClaim(null, 0, 0, null, null, null, failure);
		}

		private boolean acquired()
		{
			return _owner != null;
		}

		private OperationResult failure()
		{
			return _failure;
		}

		private long profileId()
		{
			return _profileId;
		}

		private int characterObjectId()
		{
			return _characterObjectId;
		}

		private PhantomBackgroundState state()
		{
			return _state;
		}

		private PhantomBackgroundGoalSpec spec()
		{
			return _spec;
		}

		private void retainIdentity()
		{
			if (!_identityRetained)
			{
				_identityRetained = true;
				_owner._retainedIdentityLeases.put(_characterObjectId, _lease);
			}
		}

		@Override
		public void close()
		{
			if (_closed || (_owner == null))
			{
				return;
			}
			_closed = true;
			if (!_identityRetained)
			{
				_owner.closeLease(_lease);
			}
			_owner._operations.remove(_profileId);
			_owner._currentOperations.decrementAndGet();
		}
	}
}
