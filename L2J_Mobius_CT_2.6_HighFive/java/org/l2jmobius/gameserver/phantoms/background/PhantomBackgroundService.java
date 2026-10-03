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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.FarmInput;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.FarmInputAttempt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.FarmInputFailure;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchMode;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropDisposition;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ManorFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.QuestFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.AcquisitionIdentity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.HistoricalIdentity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.AcquisitionEligibilitySnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Limits;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ManorBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyParticipationPort;
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
	private static final long RECOVERY_TELEPORT_TIMEOUT_NANOS = 250_000_000L;

	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomIdentityLeaseRegistry _identities;
	private final PhantomBackgroundTransaction _transactions;
	private final PhantomBackgroundAuthority _authority;
	private final PhantomBackgroundModel _model;
	private final PhantomBackgroundCompetitionRegistry _competition;
	private final PhantomRelevanceSignalPort _signals;
	private final Supplier<PhantomMaterializationService> _materialization;
	private final PhantomPartyParticipationPort _partyParticipation;
	private volatile PhantomAcquisitionManorAuthority _manor;
	private volatile PhantomAcquisitionQuestCatalog _quests;
	private volatile Limits _acquisitionLimits;
	private volatile LongPredicate _ordinaryPresence = profileId -> true;
	private volatile BiConsumer<Long, PhantomBackgroundState.Position> _committedPosition = (profileId, position) -> {};
	private boolean _positionPublisherInstalled;
	private boolean _presenceInstalled;
	private volatile LongFunction<OperationResult> _periodicFarm;
	private final ConcurrentHashMap<Long, Boolean> _operations = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, PhantomBackgroundState> _arrivalCaptures = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Boolean> _cleanupStores = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> _nativeTownReturns = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Boolean> _recoveries = new ConcurrentHashMap<>();
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
		this(profiles, goals, identities, transactions, authority, new PhantomBackgroundModel(), competition, signals, materialization, PhantomPartyParticipationPort.noop());
	}

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization, PhantomPartyParticipationPort partyParticipation)
	{
		this(profiles, goals, identities, transactions, authority, new PhantomBackgroundModel(), competition, signals, materialization, partyParticipation);
	}

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundModel model, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization)
	{
		this(profiles, goals, identities, transactions, authority, model, competition, signals, materialization, PhantomPartyParticipationPort.noop());
	}

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundModel model, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization, PhantomPartyParticipationPort partyParticipation)
	{
		this(profiles, goals, identities, transactions, authority, model, competition, signals, materialization, partyParticipation, null, null);
	}

	public PhantomBackgroundService(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomIdentityLeaseRegistry identities, PhantomBackgroundTransaction transactions, PhantomBackgroundAuthority authority, PhantomBackgroundModel model, PhantomBackgroundCompetitionRegistry competition, PhantomRelevanceSignalPort signals, Supplier<PhantomMaterializationService> materialization, PhantomPartyParticipationPort partyParticipation, PhantomAcquisitionManorAuthority manor, PhantomAcquisitionQuestCatalog quests)
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
		_partyParticipation = Objects.requireNonNull(partyParticipation, "partyParticipation");
		_manor = manor;
		_quests = quests;
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

	public synchronized boolean installAcquisitionAuthorities(PhantomAcquisitionManorAuthority manor, PhantomAcquisitionQuestCatalog quests, Limits acquisitionLimits)
	{
		Objects.requireNonNull(manor, "manor");
		Objects.requireNonNull(quests, "quests");
		Objects.requireNonNull(acquisitionLimits, "acquisitionLimits");
		if ((_state != ServiceState.RUNNING) || (_manor != null) || (_quests != null) || (_acquisitionLimits != null))
		{
			return false;
		}
		_manor = manor;
		_quests = quests;
		_acquisitionLimits = acquisitionLimits;
		return true;
	}

	public synchronized void installPresencePolicy(LongPredicate ordinaryPresence)
	{
		if (_presenceInstalled || (_state == ServiceState.STOPPING) || (_state == ServiceState.STOPPED))
		{
			throw new IllegalStateException("Ordinary presence policy can only be installed once before work starts.");
		}
		_ordinaryPresence = Objects.requireNonNull(ordinaryPresence, "Ordinary presence policy must not be null.");
		_presenceInstalled = true;
	}

	public synchronized void installCommittedPositionPublisher(BiConsumer<Long, PhantomBackgroundState.Position> publisher)
	{
		if (_positionPublisherInstalled || (_state == ServiceState.STOPPING) || (_state == ServiceState.STOPPED))
		{
			throw new IllegalStateException("Committed position publisher can only be installed once before work starts.");
		}
		_committedPosition = Objects.requireNonNull(publisher, "publisher");
		_positionPublisherInstalled = true;
	}

	public synchronized void installPeriodicFarm(LongFunction<OperationResult> periodicFarm)
	{
		if ((_periodicFarm != null) || (_state == ServiceState.STOPPING) || (_state == ServiceState.STOPPED))
		{
			throw new IllegalStateException("Periodic farm can only be installed once before work starts.");
		}
		_periodicFarm = Objects.requireNonNull(periodicFarm, "Periodic farm must not be null.");
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
		_nativeTownReturns.clear();
		_state = ServiceState.STOPPED;
		return true;
	}

	public Directive directive(long profileId, PhantomGoal goal, PhantomActivityState activityState)
	{
		if (_state != ServiceState.RUNNING)
		{
			return new Directive(DirectiveKind.RETRY, "service.not_running", "");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return new Directive(DirectiveKind.RETRY, "party.materialized_only", "");
		}
		if ((activityState == PhantomActivityState.BACKGROUND) && !_ordinaryPresence.test(profileId))
		{
			return new Directive(DirectiveKind.RETRY, "presence.not_available", "");
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
		if ((activityState == PhantomActivityState.WARM) || activityState.requiresMaterialization())
		{
			final boolean recoverable = (state.state() == State.DEAD) || ((state.state() == State.MATERIALIZED) && ((state.vitals().currentHp() == 0) || nativeDead(profileId) || Objects.equals(_nativeTownReturns.get(profileId), state.identity().characterObjectId())));
			if (recoverable)
			{
				return new Directive(DirectiveKind.RECOVER, "state.dead", spec.anchorId());
			}
			return new Directive(DirectiveKind.REPLAN, state.position().committedAnchorId().equals(spec.anchorId()) ? "recovery.not_dead" : "visible.travel_pending", state.position().committedAnchorId());
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
		if (!_ordinaryPresence.test(profileId))
		{
			return retry("presence.not_available");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return retry("party.materialized_only");
		}
		final LongFunction<OperationResult> periodicFarm = _periodicFarm;
		if (periodicFarm != null)
		{
			final Directive current = directive(profileId, goal, activityState);
			if (current.kind() != DirectiveKind.FARM)
			{
				return switch (current.kind())
				{
					case RETRY -> retry(current.reason());
					case INCONSISTENT -> OperationResult.inconsistent(current.reason());
					default -> OperationResult.replan(current.reason());
				};
			}
			return periodicFarm.apply(profileId);
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
			final FarmInputAttempt attempt = ordinaryFarmAttempt(profileId, claim.characterObjectId(), state, spec);
			if (!attempt.successful()) { return OperationResult.replan(farmFailureReason(attempt)); }
			final FarmInput input = attempt.input();
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

	public OperationResult advanceHistorical(long profileId, PhantomGoal goal, PhantomBackgroundCatchupStore.Snapshot catchup, PhantomBackgroundCatchupState nextCatchup)
	{
		Objects.requireNonNull(catchup, "catchup");
		Objects.requireNonNull(nextCatchup, "nextCatchup");
		final PhantomBackgroundCatchupState expectedCatchup = catchup.state();
		if ((expectedCatchup.status() != PhantomBackgroundCatchupState.Status.RUNNING) || !expectedCatchup.authorityHashes().equals(_authority.hashes()) || (nextCatchup.cursorEpochMinute() != Math.addExact(expectedCatchup.cursorEpochMinute(), 1)))
		{
			return OperationResult.replan("catchup.state_or_hash_stale");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return retry("party.materialized_only");
		}
		final OperationClaim claim = acquire(profileId, goal, expectedCatchup.generation(), Math.addExact(expectedCatchup.intervalOrdinal(), 1), true);
		if (!claim.acquired())
		{
			return claim.failure();
		}
		try (claim)
		{
			final PhantomBackgroundState state = claim.state();
			if ((state.state() != State.READY) && (state.state() != State.DEAD))
			{
				return retry("state.not_ready");
			}
			final PhantomBackgroundGoalSpec spec = claim.spec();
			final HistoricalIdentity historical = new HistoricalIdentity(expectedCatchup.requestId(), expectedCatchup.generation(), expectedCatchup.intervalOrdinal(), expectedCatchup.cursorEpochMinute(), nextCatchup.cursorEpochMinute(), expectedCatchup.planIdentity());
			final PhantomBackgroundTransaction.CatchupMutation mutation = new PhantomBackgroundTransaction.CatchupMutation(expectedCatchup, catchup.rowVersion(), nextCatchup);
			final OperationResult replay = reconcileHistoricalReplay(claim, goal, spec, historical, mutation);
			if (replay != null)
			{
				return replay;
			}
			if (state.state() == State.DEAD)
			{
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_DEAD_IDLE, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), state.position(), state.clock(), Map.of(), state.autoGetSkills(), List.of(), null, mutation);
				return commit(claim, command);
			}
			if (PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(goal.goalType()))
			{
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_IDLE, 0, state.position().committedAnchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical);
				return commit(claim, new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), state.position(), state.clock(), Map.of(), state.autoGetSkills(), List.of(), null, mutation));
			}
			if (!state.position().committedAnchorId().equals(spec.anchorId()))
			{
				final TravelAdvance advance;
				try
				{
					advance = _authority.advanceTravel(state, spec, FARM_TRAVEL_BUDGET_MILLIS, expectedCatchup.cursorEpochMinute());
				}
				catch (RuntimeException exception)
				{
					return OperationResult.replan("catchup.travel.unsupported");
				}
				if (!advance.mutated())
				{
					return switch (advance.status())
					{
						case EDGE_CLOSED, NO_ROUTE, INSUFFICIENT_ADENA -> retry("catchup.travel." + advance.status().name().toLowerCase());
						default -> OperationResult.replan("catchup.travel." + advance.status().name().toLowerCase());
					};
				}
				final String travelLegId = advance.edgeId().startsWith("leg.") ? advance.edgeId() : "";
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_TRAVEL, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical, travelLegId);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), advance.position(), advance.clock(), advance.feeAdena() == 0 ? Map.of() : Map.of(57, -advance.feeAdena()), state.autoGetSkills(), List.of(), null, mutation);
				return commit(claim, command);
			}
			final FarmInputAttempt attempt = ordinaryFarmAttempt(profileId, claim.characterObjectId(), state, spec);
			if (!attempt.successful()) { return OperationResult.replan(farmFailureReason(attempt)); }
			final FarmInput input = attempt.input();
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_FARM, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical);
			try (PhantomBackgroundCompetitionRegistry.Reservation reservation = _competition.tryReserve(input.topologyNodeId(), spec.npcId(), input.spawnCapacity()))
			{
				if (reservation == null)
				{
					return retry("competition.capacity");
				}
				final BatchResult batch = _model.evaluate(new BatchRequest(state, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false));
				if (!batch.mutated())
				{
					if (batch.indivisibleObjectCap())
					{
						return OperationResult.replan("model.object_cap_indivisible");
					}
					return batch.reason() == PhantomBackgroundModel.ResultReason.TIME_BUDGET ? retry("model.time_budget") : OperationResult.replan("model." + batch.reason().name().toLowerCase());
				}
				final List<PhantomBackgroundState.AutoGetSkill> autoSkills = _authority.autoGetSkills(state.identity(), batch.progress().level());
				final Clock clock = new Clock(batch.nextRngState(), 0, 0);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, batch.progress(), batch.vitals(), state.position(), clock, batch.inventoryDelta().itemDeltas(), autoSkills, List.of(), null, mutation);
				return commit(claim, command).withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
			}
		}
	}

	private OperationResult reconcileHistoricalReplay(OperationClaim claim, PhantomGoal goal, PhantomBackgroundGoalSpec spec, HistoricalIdentity historical, PhantomBackgroundTransaction.CatchupMutation catchup)
	{
		for (ActionKind actionKind : List.of(ActionKind.HISTORICAL_TRAVEL, ActionKind.HISTORICAL_FARM, ActionKind.HISTORICAL_DEAD_IDLE, ActionKind.HISTORICAL_IDLE))
		{
			for (String legId : actionKind == ActionKind.HISTORICAL_TRAVEL ? java.util.stream.Stream.concat(java.util.stream.Stream.of(""), _authority.travelLegIds().stream()).toList() : List.of(""))
			{
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(claim.profileId(), claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, actionKind, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical, legId);
				if (!claim.state().receipt().operationKey().equals(key.digest()))
				{
					continue;
				}
				final PhantomBackgroundTransaction.Result result = transaction(() -> _transactions.verifyCommittedHistoricalReplay(claim.profileId(), claim.characterObjectId(), goal, key, catchup));
				if (result.status() == PhantomBackgroundTransaction.Status.IDEMPOTENT)
				{
					publishPosition(claim.profileId(), result);
					_idempotentOperations.incrementAndGet();
					return OperationResult.idempotent("transaction.idempotent");
				}
				final OperationResult failure = mapTransactionFailure(result.status());
				if (failure.status() == OperationStatus.INCONSISTENT)
				{
					claim.retainIdentity();
					failStop();
					_failedOperations.incrementAndGet();
				}
				return failure;
			}
		}
		return null;
	}
	public OperationResult acquireItem(long profileId, PhantomGoal goal, long goalRowVersion, PhantomAcquisitionState acquisitionState, long acquisitionRowVersion, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos, long logicalMinute)
	{
		if ((activityState != PhantomActivityState.BACKGROUND) || (acquisitionState == null) || (acquisitionState.selectedSource() == null))
		{
			return OperationResult.replan("acquisition.background.invalid");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return retry("party.materialized_only");
		}
		final OperationClaim claim = acquireAcquisition(profileId, goal, goalRowVersion, acquisitionState, activityGeneration, tickSequence);
		if (!claim.acquired())
		{
			return claim.failure();
		}
		try (claim)
		{
			final PhantomBackgroundState background = claim.state();
			if (!background.acceptsBackgroundWork() || !background.position().committedAnchorId().equals(acquisitionState.selectedSource().anchorId()))
			{
				return OperationResult.replan("acquisition.travel.required");
			}
			final FarmInput input;
			Map<Integer, Integer> eligibilitySkills = Map.of();
			Map<String, String> expectedQuestRows = Map.of();
			ManorFormula manorFormula = null;
			QuestFormula questFormula = null;
			try
			{
				if (acquisitionState.selectedSource().method() == Method.SPOIL_SWEEP)
				{
					final var eligibility = transaction(() -> _transactions.readAcquisitionEligibility(profileId, claim.characterObjectId(), background.identity().classIndex(), background.identity().activeClassId(), List.of(acquisitionState.selectedSource().spoilSkillId(), acquisitionState.selectedSource().sweepSkillId()), acquisitionState.hashes().progression(), _authority.hashes()));
					if (!eligibility.successful())
					{
						return OperationResult.replan("acquisition.eligibility.stale");
					}
					eligibilitySkills = eligibility.snapshot().skillLevels();
				}
				input = _authority.acquisitionInput(background, acquisitionState.selectedSource(), eligibilitySkills);
				if (acquisitionState.methodBinding() instanceof ManorBinding manor)
				{
					if ((_manor == null) || (_acquisitionLimits == null) || !manor.authorityHash().equals(_manor.authorityHash()))
					{
						return OperationResult.replan("acquisition.manor.authority_stale");
					}
					final var inventory = transaction(() -> _transactions.readAcquisitionInventoryCounts(profileId, claim.characterObjectId(), background.identity().classIndex(), background.identity().activeClassId(), List.of(manor.seedItemId(), manor.cropItemId()).stream().distinct().sorted().toList(), _authority.hashes()));
					if (!inventory.successful() || (inventory.snapshot().counts().getOrDefault(manor.seedItemId(), 0L) != manor.seedCountBeforeDispatch()) || (inventory.snapshot().counts().getOrDefault(manor.cropItemId(), 0L) != manor.cropCountBeforeDispatch()))
					{
						return OperationResult.replan("acquisition.manor.inventory_stale");
					}
					final var projection = _manor.projection(manor, acquisitionState.selectedSource().npcId(), background.progress().level(), input.target().level());
					manorFormula = new ManorFormula(manor.seedItemId(), manor.cropItemId(), manor.seedCountBeforeDispatch(), projection.sowChance(), projection.harvestChance(), projection.harvestPayload(), _acquisitionLimits.manorAttemptsPerTarget(), _acquisitionLimits.harvestAttemptsPerCorpse());
				}
				else if (acquisitionState.methodBinding() instanceof QuestBinding quest)
				{
					if ((_quests == null) || !_quests.current() || !quest.authorityHash().equals(_quests.authorityHash()))
					{
						return OperationResult.replan("quest.script_stale");
					}
					final var rule = _quests.rule(quest.ruleId()).filter(value -> value.ruleHash().equals(quest.ruleHash()) && value.scriptHash().equals(quest.scriptHash()) && value.questId() == quest.questId() && value.questName().equals(quest.questName()) && value.questItemId() == quest.questItemId() && value.supports(quest.expectedCond(), quest.itemCountBeforeKill(), quest.targetNpcId(), false)).orElse(null);
					if (rule == null || (background.inventory().itemCount(quest.questItemId()) != quest.itemCountBeforeKill()))
					{
						return OperationResult.replan("quest.rule_unsupported");
					}
					final var questRowsResult = transaction(() -> _transactions.readAcquisitionQuestRows(profileId, claim.characterObjectId(), background.identity().classIndex(), background.identity().activeClassId(), List.of(quest.questName()), _authority.hashes()));
					expectedQuestRows = questRowsResult.successful() ? questRowsResult.snapshot().rows().getOrDefault(quest.questName(), Map.of()) : Map.of();
					if ((expectedQuestRows.size() != (2 + rule.expectedVars().size())) || !"Started".equals(expectedQuestRows.get("<state>")) || !Integer.toString(quest.expectedCond()).equals(expectedQuestRows.get("cond")) || !expectedQuestRows.keySet().equals(java.util.stream.Stream.concat(java.util.stream.Stream.of("<state>", "cond"), rule.expectedVars().stream()).collect(java.util.stream.Collectors.toSet())))
					{
						return OperationResult.replan("quest.cond_ineligible");
					}
					questFormula = new QuestFormula(rule.chanceKind() == PhantomAcquisitionQuestCatalog.ChanceKind.NONE ? 0 : rule.rollBound(), rule.chanceKind() == PhantomAcquisitionQuestCatalog.ChanceKind.NONE ? 0 : rule.rollThreshold(), (long) (rule.maximumCount() * RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER), quest.itemCountBeforeKill(), quest.itemCap());
				}
			}
			catch (RuntimeException exception)
			{
				return OperationResult.replan("acquisition.authority.unsupported");
			}
			final Method method = acquisitionState.selectedSource().method();
			final BatchMode mode = switch (method)
			{
				case DEATH_DROP -> BatchMode.ACQUISITION_DEATH_DROP;
				case SPOIL_SWEEP -> BatchMode.ACQUISITION_SPOIL_SWEEP;
				case MANOR_CROP -> BatchMode.ACQUISITION_MANOR_CROP;
				case QUEST_COLLECTION -> BatchMode.ACQUISITION_QUEST_COLLECTION;
				default -> throw new IllegalArgumentException("Planning-only acquisition method cannot execute in background.");
			};
			final ActionKind actionKind = switch (method)
			{
				case DEATH_DROP -> ActionKind.ACQUISITION_DEATH_DROP;
				case SPOIL_SWEEP -> ActionKind.ACQUISITION_SPOIL_SWEEP;
				case MANOR_CROP -> ActionKind.ACQUISITION_MANOR_CROP;
				case QUEST_COLLECTION -> ActionKind.ACQUISITION_QUEST_COLLECTION;
				default -> throw new IllegalArgumentException("Planning-only acquisition method cannot execute in background.");
			};
			final ReceiptKind receiptKind = switch (method)
			{
				case DEATH_DROP -> ReceiptKind.BACKGROUND_DEATH_DROP;
				case SPOIL_SWEEP -> ReceiptKind.BACKGROUND_SPOIL_SWEEP;
				case MANOR_CROP -> ReceiptKind.BACKGROUND_MANOR_CROP;
				case QUEST_COLLECTION -> ReceiptKind.BACKGROUND_QUEST_COLLECTION;
				default -> throw new IllegalArgumentException("Planning-only acquisition method cannot execute in background.");
			};
			final long resourceCount = acquisitionState.methodBinding() instanceof ManorBinding manor ? manor.seedCountBeforeDispatch() : acquisitionState.methodBinding() instanceof QuestBinding quest ? quest.itemCountBeforeKill() : 0;
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), activityGeneration, tickSequence, actionKind, acquisitionState.selectedSource().npcId(), acquisitionState.selectedSource().anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), new AcquisitionIdentity(acquisitionState.selectedSource().sourceId(), acquisitionRowVersion, acquisitionState.targetItemId(), acquisitionState.hashes().catalog(), acquisitionState.hashes().background(), bindingHash(acquisitionState.methodBinding()), resourceCount));
			try (PhantomBackgroundCompetitionRegistry.Reservation reservation = _competition.tryReserve(input.topologyNodeId(), acquisitionState.selectedSource().npcId(), input.spawnCapacity()))
			{
				if (reservation == null)
				{
					return retry("competition.capacity");
				}
				final long remaining = acquisitionState.requiredAmount() - acquisitionState.progress();
				final var item = ItemData.getInstance().getTemplate(acquisitionState.targetItemId());
				if (item == null)
				{
					return OperationResult.replan("acquisition.item_stale");
				}
				final BatchResult batch = _model.evaluate(new BatchRequest(background, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false, mode, acquisitionState.targetItemId(), remaining, true, manorFormula, questFormula, (method == Method.MANOR_CROP) || (method == Method.QUEST_COLLECTION) ? 1 : PhantomBackgroundModel.MAX_ENCOUNTERS, item.isStackable(), item.getWeight()));
				if (!batch.mutated())
				{
					return switch (batch.reason())
					{
						case STATE_NOT_READY, TIME_BUDGET -> retry("model." + batch.reason().name().toLowerCase());
						default -> OperationResult.replan("model." + batch.reason().name().toLowerCase());
					};
				}
				final List<PhantomBackgroundState.AutoGetSkill> autoSkills = _authority.autoGetSkills(background.identity(), batch.progress().level());
				final Clock clock = new Clock(batch.nextRngState(), 0, 0);
				final List<Integer> mutableItems = java.util.stream.Stream.concat(input.target().drops().stream().filter(drop -> drop.disposition() == DropDisposition.ACQUIRE).map(drop -> drop.itemId()), java.util.stream.Stream.of(acquisitionState.targetItemId(), acquisitionState.methodBinding() instanceof ManorBinding manor ? manor.seedItemId() : acquisitionState.targetItemId())).distinct().sorted().toList();
				final PhantomBackgroundTransaction.AcquisitionMutation acquisition = new PhantomBackgroundTransaction.AcquisitionMutation(acquisitionState, acquisitionRowVersion, goalRowVersion, receiptKind, logicalMinute, eligibilitySkills, expectedQuestRows);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(background, goal, key, batch.progress(), batch.vitals(), background.position(), clock, batch.inventoryDelta().itemDeltas(), autoSkills, mutableItems, acquisition);
				return commit(claim, command).withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
			}
		}
	}

	public OperationResult travelAcquisition(long profileId, PhantomGoal goal, long goalRowVersion, PhantomAcquisitionState acquisitionState, long acquisitionRowVersion, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos)
	{
		if ((activityState != PhantomActivityState.BACKGROUND) || (acquisitionState == null) || (acquisitionState.selectedSource() == null))
		{
			return OperationResult.replan("acquisition.background.invalid");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return retry("party.materialized_only");
		}
		final OperationClaim claim = acquireAcquisition(profileId, goal, goalRowVersion, acquisitionState, activityGeneration, tickSequence);
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
			final TravelAdvance advance;
			try
			{
				advance = _authority.advanceAcquisitionTravel(state, acquisitionState.selectedSource(), FARM_TRAVEL_BUDGET_MILLIS);
			}
			catch (RuntimeException exception)
			{
				return OperationResult.replan("acquisition.travel.unsupported");
			}
			if (!advance.mutated())
			{
				return switch (advance.status())
				{
					case AT_DESTINATION -> OperationResult.success("acquisition.travel.at_destination");
					case EDGE_CLOSED, NO_ROUTE -> retry("acquisition.travel." + advance.status().name().toLowerCase());
					default -> OperationResult.replan("acquisition.travel." + advance.status().name().toLowerCase());
				};
			}
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), activityGeneration, tickSequence, ActionKind.ACQUISITION_TRAVEL, acquisitionState.selectedSource().npcId(), acquisitionState.selectedSource().anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), new AcquisitionIdentity(acquisitionState.selectedSource().sourceId(), acquisitionRowVersion, acquisitionState.targetItemId(), acquisitionState.hashes().catalog(), acquisitionState.hashes().background()));
			final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), advance.position(), advance.clock(), Map.of(), state.autoGetSkills());
			return commit(claim, command);
		}
	}

	public Optional<PhantomBackgroundState> acquisitionSnapshot(long profileId)
	{
		final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		return loaded.successful() ? Optional.ofNullable(loaded.state()) : Optional.empty();
	}

	/** Uses the ordinary historical identity lease and transaction receipt to finish a pending commit. */
	public OperationResult reconcileHistoricalPending(long profileId, PhantomGoal goal, long generation, long nextOrdinal)
	{
		final OperationClaim claim = acquire(profileId, goal, generation, nextOrdinal, true);
		if (!claim.acquired()) { return claim.failure(); }
		try (claim)
		{
			return ((claim.state().state() == State.READY) || (claim.state().state() == State.DEAD)) ? OperationResult.success("background.verify_pending_reconciled") : OperationResult.replan("background.state_invalid");
		}
	}

	/** Reconcile a durable MATERIALIZED marker only after proving that no Player owns the character. */
	public OperationResult recoverAbandonedMaterialization(long profileId)
	{
		if (!claimTransition(profileId, TransitionKind.MATERIALIZING))
		{
			return retry("recovery.transition_busy");
		}
		Lease lease = null;
		try
		{
			final PhantomProfile profile = _profiles.find(profileId).orElse(null);
			if ((profile == null) || (profile.characterObjectId() == null))
			{
				return OperationResult.replan("recovery.profile_unlinked");
			}
			final int characterObjectId = profile.characterObjectId();
			lease = _identities.tryAcquire(characterObjectId, OwnerKind.BACKGROUND);
			if (lease == null)
			{
				return retry("recovery.identity_busy");
			}
			increment(_currentIdentityLeases, _peakIdentityLeases);
			if ((_materialization.get().find(profileId).isPresent()) || (World.getInstance().getPlayer(characterObjectId) != null) || (World.getInstance().findObject(characterObjectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId))
			{
				return retry("recovery.runtime_busy");
			}
			final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
			if (!loaded.successful() || (loaded.state() == null) || (loaded.state().state() != State.MATERIALIZED) || (loaded.state().identity().characterObjectId() != characterObjectId))
			{
				return OperationResult.replan("recovery.background_state_invalid");
			}
			final PhantomBackgroundTransaction.Result recovered = transaction(() -> _transactions.abortMaterialization(profileId, characterObjectId));
			if (recovered.successful() && (recovered.state() != null) && ((recovered.state().state() == State.READY) || (recovered.state().state() == State.DEAD)))
			{
				return OperationResult.success("recovery.abandoned_materialization_reconciled");
			}
			return mapTransactionFailure(recovered.status());
		}
		finally
		{
			closeLease(lease);
			releaseTransition(profileId, TransitionKind.MATERIALIZING);
		}
	}

	/** Apply one externally attested legacy repair only when the runtime no longer owns the Player. */
	public OperationResult recoverAttestedLegacyHeadlessDrift(PhantomBackgroundTransaction.LegacyHeadlessWitness witness)
	{
		Objects.requireNonNull(witness, "witness");
		return recoverAttestedLegacy(witness.profileId(), witness.characterObjectId(), () -> _transactions.recoverAttestedLegacyHeadlessDrift(witness));
	}

	public OperationResult recoverAttestedLegacyMaterializedDrift(PhantomBackgroundTransaction.LegacyMaterializedWitness witness)
	{
		Objects.requireNonNull(witness, "witness");
		return recoverAttestedLegacy(witness.profileId(), witness.characterObjectId(), () -> _transactions.recoverAttestedLegacyMaterializedDrift(witness));
	}

	private OperationResult recoverAttestedLegacy(long profileId, int characterObjectId, Supplier<PhantomBackgroundTransaction.Result> repair)
	{
		if (!claimTransition(profileId, TransitionKind.MATERIALIZING))
		{
			return retry("recovery.legacy.transition_busy");
		}
		Lease lease = null;
		try
		{
			final PhantomProfile profile = _profiles.find(profileId).orElse(null);
			if ((profile == null) || (profile.characterObjectId() == null) || (profile.characterObjectId() != characterObjectId))
			{
				return OperationResult.replan("recovery.legacy.profile_unlinked");
			}
			lease = _identities.tryAcquire(characterObjectId, OwnerKind.BACKGROUND);
			if (lease == null)
			{
				return retry("recovery.legacy.identity_busy");
			}
			increment(_currentIdentityLeases, _peakIdentityLeases);
			if ((_materialization.get().find(profileId).isPresent()) || (World.getInstance().getPlayer(characterObjectId) != null) || (World.getInstance().findObject(characterObjectId) != null) || PlayerAutoSaveTaskManager.getInstance().containsObjectId(characterObjectId))
			{
				return retry("recovery.legacy.runtime_busy");
			}
			final PhantomBackgroundTransaction.Result recovered = transaction(repair);
			if (recovered.successful() && (recovered.state() != null) && ((recovered.state().state() == State.READY) || (recovered.state().state() == State.DEAD)))
			{
				return OperationResult.success("recovery.legacy.attested_reconciled");
			}
			return OperationResult.inconsistent("recovery.legacy." + recovered.status().name().toLowerCase());
		}
		finally
		{
			closeLease(lease);
			releaseTransition(profileId, TransitionKind.MATERIALIZING);
		}
	}

	public Optional<PhantomGoal> ordinaryGoal(long profileId)
	{
		return _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).filter(goal -> PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()));
	}

	/** Called under the canonical Player action lease after native route arrival. */
	public enum VisibleStoreStatus { SUCCESS, RETRY, PROFILE_FENCED }
	public record VisibleStoreResult(VisibleStoreStatus status, String reason) { }

	public VisibleStoreResult resumeVisibleOwnedStore(long profileId, Player player, PhantomGoal goal)
	{
		if ((_state != ServiceState.RUNNING) || _transitions.containsKey(profileId)) { return new VisibleStoreResult(VisibleStoreStatus.RETRY, "owned_store.service_or_transition"); }
		try (var action = _materialization.get().tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || (action.player() != player) || !player.hasOwnedStoreBoundary(this) || !player.hasPendingOwnedStore()) { return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.live_owner_or_intent_missing"); }
			if (!Objects.equals(_goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null), goal)) { return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.goal_changed"); }
			return new VisibleStoreResult(player.resumePendingOwnedStore(this, goal.goalId(), goal.revision()) ? VisibleStoreStatus.SUCCESS : VisibleStoreStatus.RETRY, "owned_store.live_resume");
		}
		catch (RuntimeException failure)
		{
			return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.live_resume:" + failure.getMessage());
		}
	}

	public boolean captureVisibleArrival(long profileId, Player player, PhantomGoal goal, String anchorId)
	{
		if ((_state != ServiceState.RUNNING) || player.isDead() || player.isInParty() || !player.hasHeadlessOutboundSession() || _transitions.containsKey(profileId) || (_operations.putIfAbsent(profileId, Boolean.TRUE) != null))
		{
			return false;
		}
		try
		{
			final PhantomBackgroundState previous = acquisitionSnapshot(profileId).orElse(null);
			if ((previous == null) || (previous.state() != State.MATERIALIZED) || (previous.identity().characterObjectId() != player.getObjectId()))
			{
				return false;
			}
			final var position = new PhantomBackgroundState.Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), anchorId);
			final var hint = new PhantomBackgroundState(previous.state(), previous.identity(), previous.progress(), previous.vitals(), position, previous.combat(), previous.loadout(), previous.inventory(), previous.autoGetSkills(), previous.clock(), previous.receipt(), previous.hashes());
			final PhantomBackgroundState captured = _authority.capture(profileId, player, goal, hint);
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_CAPTURE);
			_arrivalCaptures.put(profileId, captured);
			player.storeMe();
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_STORE);
			final var stored = transaction(() -> _transactions.load(profileId));
			if (!stored.successful() || (stored.state() == null) || (stored.state().state() != State.MATERIALIZED))
			{
				return false;
			}
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_BASELINE);
			_committedPosition.accept(profileId, stored.state().position());
			return true;
		}
		catch (RuntimeException exception)
		{
			return false;
		}
		finally
		{
			_arrivalCaptures.remove(profileId);
			_operations.remove(profileId);
		}
	}

	/** Owned store integration explicitly authorized after the guarded protocol matrix passed. */
	private void installOwnedStoreBoundary(long profileId, Player player)
	{
		final var owner = _identities.getOwnerSnapshot(player.getObjectId());
		if ((owner == null) || (owner.ownerKind() != OwnerKind.PHANTOM)) { throw new IllegalStateException("OWNED_STORE_IDENTITY_MISSING"); }
		if (player.hasOwnedStoreBoundary(this)) { return; }
		player.attachOwnedStoreBoundary(new Player.OwnedStoreBoundary()
		{
			@Override public Object ownerKey() { return PhantomBackgroundService.this; }
			private volatile PhantomOwnedStoreIntent _intent;
			private PhantomGoal _intentGoal;
			private long _sequence;
			private String _before;
			private String _kind;
			private boolean _recaptureAfterResume;

			@Override public boolean hasPending() { return _intent != null; }

			@Override
			public Player.OwnedStoreSnapshot beforePendingStore(long goalId, long revision)
			{
				if (_intent == null) { return null; }
				if ((_intentGoal == null) || (_intentGoal.goalId() != goalId) || (_intentGoal.revision() != revision) || !Objects.equals(_goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null), _intentGoal)) { throw new IllegalStateException("OWNED_STORE_PENDING_GOAL_CHANGED"); }
				checkOwner();
				return resumeIntent();
			}

			@Override
			public Player.OwnedStoreSnapshot beforeStore()
			{
				try { return prepareStore(); }
				catch (RuntimeException failure) { org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.rejected(profileId, player, _intent, failure.getClass().getSimpleName() + ":" + failure.getMessage()); throw failure; }
			}

			private void checkOwner()
			{
				final int objectId = player.getObjectId();
				final var entry = _materialization.get().find(profileId).orElse(null);
				if (!owner.equals(_identities.getOwnerSnapshot(objectId)) || (entry == null) || (entry.characterObjectId() != objectId) || (player.getClient() != null) || ((World.getInstance().findObject(objectId) != null) && (World.getInstance().findObject(objectId) != player)) || PlayerAutoSaveTaskManager.getInstance().containsOtherObjectId(objectId, player)) { throw new IllegalStateException("OWNED_STORE_RUNTIME_OWNER_REJECTED"); }
				if ((_intent != null) && (_intent.materializedAtNanos() != entry.materializedAtNanos())) { throw new IllegalStateException("OWNED_STORE_PENDING_EPOCH_CHANGED"); }
			}

			private Player.OwnedStoreSnapshot resumeIntent()
			{
				if (_intent != null)
				{
					final var resumed = transaction(() -> _transactions.resumeOwnedStore(_intent));
					if (resumed.status() == PhantomBackgroundTransaction.Status.POST_COMMIT_VERIFICATION_FAILED)
					{
						_recaptureAfterResume = _cleanupStores.containsKey(profileId);
						if ((!_recaptureAfterResume && !ownedProgressMatches(player, _intent.after())) || !captureOwnedInventory(player, _intent.after()).inventory().equals(_intent.after().inventory())) { throw new IllegalStateException("OWNED_STORE_RETRY_RUNTIME_CHANGED"); }
						_before = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(profileId, _intent.materializedAtNanos()) ? org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.snapshot(player) : null;
						_sequence = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.begin(profileId, player, _kind, _intent);
						return ownedSnapshot(player, _intent.after());
					}
					if (!resumed.successful()) { throw new IllegalStateException("OWNED_STORE_RESUME:" + resumed.status()); }
					_intent = null;
				}
				return null;
			}

			private Player.OwnedStoreSnapshot prepareStore()
			{
				checkOwner();
				final var resumed = resumeIntent();
				if (resumed != null) { return resumed; }
				final var entry = _materialization.get().find(profileId).orElseThrow();
				final var loaded = transaction(() -> _transactions.load(profileId));
				final var previous = loaded.successful() ? loaded.state() : null;
				if (!loaded.successful() && (loaded.status() != PhantomBackgroundTransaction.Status.STATE_ABSENT)) { throw new IllegalStateException("OWNED_STORE_BACKGROUND_READ:" + loaded.status()); }
				final var goal = _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
				if ((goal == null) && (previous == null)) { return null; }
				if (goal == null) { throw new IllegalStateException("OWNED_STORE_GOAL_MISSING"); }
				if (!PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
				{
					try { PhantomBackgroundGoalSpec.parseLifecycle(goal); }
					catch (IllegalArgumentException unsupported) { if (previous == null) { return null; } throw new IllegalStateException("OWNED_STORE_UNSUPPORTED_GOAL", unsupported); }
				}
				final boolean cleanup = _cleanupStores.containsKey(profileId);
				final var arrival = _arrivalCaptures.get(profileId);
				player.getInventory().updateDatabase();
				final PhantomBackgroundState captured;
				synchronized (player.getStatus())
				{
					if (arrival != null)
					{
						if (!_authority.matchesRuntime(player, arrival)) { throw new IllegalStateException("OWNED_STORE_CAPTURE_STALE"); }
						captured = arrival;
					}
					else if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType())) { captured = _authority.captureAcquisition(profileId, player, goal, previous, PhantomAcquisitionGoalSpec.parse(goal).itemId()); }
					else { PhantomBackgroundGoalSpec.parseLifecycle(goal); captured = _authority.capture(profileId, player, goal, previous); }
				}
				final State target = cleanup || (previous == null) || (previous.state() != State.MATERIALIZED) ? (captured.vitals().currentHp() == 0 ? State.DEAD : State.READY) : State.MATERIALIZED;
				final var witnessed = captureOwnedInventory(player, captured);
				final var prepared = transaction(() -> _transactions.prepareOwnedStore(witnessed, goal, entry.materializedAtNanos(), target));
				_intentGoal = goal;
				_intent = prepared.intent();
				_kind = arrival != null ? "ARRIVAL_CAPTURE" : cleanup ? "CLEANUP_STORE" : "OTHER_OWNED_STORE";
				if (!prepared.successful()) { throw new IllegalStateException("OWNED_STORE_PREPARE:" + prepared.status()); }
				_before = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(profileId, _intent.materializedAtNanos()) ? org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.snapshot(player) : null;
				_sequence = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.begin(profileId, player, _kind, _intent);
				_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_PREPARE);
				return ownedSnapshot(player, _intent.after());
			}

			@Override
			public void afterStore(boolean nativeStoreCompleted)
			{
				if (_intent == null) { return; }
				String status = nativeStoreCompleted ? "FINALIZE_NOT_REACHED" : "NATIVE_STORE_THROW";
				boolean finalizedSuccessfully = false;
				final boolean recaptureCleanup = _recaptureAfterResume;
				_recaptureAfterResume = false;
				PhantomBackgroundState completedState = null;
				final String after = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(profileId, _intent.materializedAtNanos()) ? org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.snapshot(player) : null;
				try
				{
					if (!nativeStoreCompleted) { return; }
					_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_NATIVE_STORE);
					final var finalized = transaction(() -> _transactions.finalizeOwnedStore(profileId, player.getObjectId(), _intent.materializedAtNanos()));
					status = finalized.status().name();
					completedState = finalized.state();
					if (!finalized.successful()) { throw new IllegalStateException("OWNED_STORE_FINALIZE:" + status); }
					finalizedSuccessfully = true;
					if ((!recaptureCleanup && !ownedProgressMatches(player, _intent.after())) || !captureOwnedInventory(player, _intent.after()).inventory().equals(_intent.after().inventory())) { status = "RUNTIME_CHANGED_AFTER_FINALIZE"; throw new IllegalStateException("OWNED_STORE_RUNTIME_CHANGED_AFTER_FINALIZE"); }
				}
				finally
				{
					org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.end(profileId, player, _kind, _intent, _sequence, _before, after, status, completedState);
					if (finalizedSuccessfully) { _intent = null; }
				}
				// Finish the old receipt first, then take one fresh cleanup snapshot. The second call cannot recursively resume this completed receipt.
				if (recaptureCleanup && finalizedSuccessfully) { player.storeMe(); }
			}
		});
	}

	private static boolean ownedProgressMatches(Player player, PhantomBackgroundState state)
	{
		return (player.getClassIndex() == state.identity().classIndex()) && (player.getActiveClass() == state.identity().activeClassId()) && (player.getRace().ordinal() == state.identity().raceOrdinal()) && (player.getLevel() == state.progress().level()) && (player.getExp() == state.progress().experience()) && (player.getSp() == state.progress().skillPoints()) && (player.getExpBeforeDeath() == state.progress().experienceBeforeDeath());
	}

	private static Player.OwnedStoreSnapshot ownedSnapshot(Player player, PhantomBackgroundState state)
	{
		final var v = state.vitals(); final var p = state.position(); final var progress = state.progress(); final var identity = state.identity();
		return new Player.OwnedStoreSnapshot(v.currentHp(), (int) v.maximumHp(), v.currentMp(), (int) v.maximumMp(), v.currentCp(), (int) v.maximumCp(), p.x(), p.y(), p.z(), p.heading(), progress.level(), progress.experience(), progress.skillPoints(), progress.experienceBeforeDeath(), identity.activeClassId(), identity.raceOrdinal(), identity.classIndex(), identity.classIndex() == 0 ? progress.level() : player.getStat().getBaseLevel(), identity.classIndex() == 0 ? progress.experience() : player.getStat().getBaseExp(), identity.classIndex() == 0 ? progress.skillPoints() : player.getStat().getBaseSp());
	}

	/** One immutable enumeration; compare the same INVENTORY/PAPERDOLL facts as the durable transaction. */
	private static PhantomBackgroundState captureOwnedInventory(Player player, PhantomBackgroundState state)
	{
		final var items = player.getInventory().getItems().stream().filter(item -> (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY) || (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.PAPERDOLL)).map(item -> new PhantomBackgroundState.ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), PhantomBackgroundState.ItemLocation.valueOf(item.getItemLocation().name()))).sorted(java.util.Comparator.comparingInt(PhantomBackgroundState.ItemObject::objectId)).toList();
		final var mutable = java.util.Set.copyOf(state.inventory().mutableItemIds());
		final var equipped = state.inventory().objects().stream().filter(item -> item.location() == PhantomBackgroundState.ItemLocation.PAPERDOLL).map(PhantomBackgroundState.ItemObject::objectId).collect(java.util.stream.Collectors.toSet());
		final var tracked = items.stream().filter(item -> (item.location() == PhantomBackgroundState.ItemLocation.INVENTORY) ? mutable.contains(item.itemId()) : equipped.contains(item.objectId())).toList();
		if (!tracked.equals(state.inventory().objects())) { throw new IllegalStateException("OWNED_STORE_INVENTORY_CAPTURE_STALE"); }
		long load = 0; int slots = 0;
		for (var item : items) { load = Math.addExact(load, Math.multiplyExact(item.count(), org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(item.itemId()).getWeight())); if (item.location() == PhantomBackgroundState.ItemLocation.INVENTORY) { slots++; } }
		final String hash = PhantomBackgroundInventoryHash.compute(items.stream().map(item -> new PhantomBackgroundInventoryHash.CanonicalItem(item.objectId(), item.itemId(), item.count(), item.location())).toList());
		final var inventory = new PhantomBackgroundState.InventoryFacts(state.inventory().mutableItemIds(), tracked, hash, load, state.inventory().maximumLoad(), slots, state.inventory().maximumSlots());
		return new PhantomBackgroundState(state.state(), state.identity(), state.progress(), state.vitals(), state.position(), state.combat(), state.loadout(), inventory, state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
	}

	public Optional<AcquisitionEligibilitySnapshot> acquisitionEligibility(long profileId, PhantomBackgroundState state, List<Integer> requestedSkillIds, String progressionHash)
	{
		if ((state == null) || (state.identity().profileId() != profileId))
		{
			return Optional.empty();
		}
		final var result = transaction(() -> _transactions.readAcquisitionEligibility(profileId, state.identity().characterObjectId(), state.identity().classIndex(), state.identity().activeClassId(), requestedSkillIds, progressionHash, _authority.hashes()));
		return result.successful() ? Optional.of(result.snapshot()) : Optional.empty();
	}

	public Optional<Map<Integer, Long>> acquisitionInventoryCounts(long profileId, PhantomBackgroundState state, List<Integer> exactItemIds)
	{
		if ((state == null) || (state.identity().profileId() != profileId))
		{
			return Optional.empty();
		}
		final PhantomBackgroundState.Hashes authorityHashes = _authority.hashes();
		if (!state.hashes().equals(authorityHashes))
		{
			return Optional.empty();
		}
		final var result = transaction(() -> _transactions.readAcquisitionInventoryCounts(profileId, state.identity().characterObjectId(), state.identity().classIndex(), state.identity().activeClassId(), exactItemIds, authorityHashes));
		return result.successful() && result.snapshot().backgroundHashes().equals(authorityHashes) ? Optional.of(result.snapshot().counts()) : Optional.empty();
	}

	public Optional<Map<String, Map<String, String>>> acquisitionQuestRows(long profileId, PhantomBackgroundState state, List<String> exactQuestNames)
	{
		if ((state == null) || (state.identity().profileId() != profileId) || !state.hashes().equals(_authority.hashes()))
		{
			return Optional.empty();
		}
		final var result = transaction(() -> _transactions.readAcquisitionQuestRows(profileId, state.identity().characterObjectId(), state.identity().classIndex(), state.identity().activeClassId(), exactQuestNames, _authority.hashes()));
		return result.successful() && result.snapshot().backgroundHashes().equals(_authority.hashes()) ? Optional.of(result.snapshot().rows()) : Optional.empty();
	}

	public PhantomBackgroundState.Hashes authorityHashes()
	{
		return _authority.hashes();
	}

	public OperationResult travel(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos)
	{
		return travel(profileId, goal, activityGeneration, tickSequence, activityState, logicalNowNanos, 0);
	}

	public OperationResult travel(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence, PhantomActivityState activityState, long logicalNowNanos, long logicalEpochMinute)
	{
		if (activityState != PhantomActivityState.BACKGROUND)
		{
			return OperationResult.replan("activity.not_background");
		}
		if (!_ordinaryPresence.test(profileId))
		{
			return retry("presence.not_available");
		}
		if (_partyParticipation.blocksBackground(profileId))
		{
			return retry("party.materialized_only");
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
			final TravelAdvance advance = _authority.advanceTravel(state, claim.spec(), FARM_TRAVEL_BUDGET_MILLIS, logicalEpochMinute);
			if (!advance.mutated())
			{
				return switch (advance.status())
				{
					case AT_DESTINATION -> OperationResult.success("travel.at_destination");
					case EDGE_CLOSED, NO_ROUTE, INSUFFICIENT_ADENA -> retry("travel." + advance.status().name().toLowerCase());
					default -> OperationResult.replan("travel." + advance.status().name().toLowerCase());
				};
			}
			final String travelLegId = advance.edgeId().startsWith("leg.") ? advance.edgeId() : "";
			final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), activityGeneration, tickSequence, ActionKind.TRAVEL, claim.spec().npcId(), claim.spec().anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, null, travelLegId);
			final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), advance.position(), advance.clock(), advance.feeAdena() == 0 ? Map.of() : Map.of(57, -advance.feeAdena()), state.autoGetSkills());
			return commit(claim, command);
		}
	}

	public OperationResult recover(long profileId, PhantomGoal goal, PhantomActivityState activityState)
	{
		return recover(profileId, goal, activityState, () -> false);
	}

	public boolean normalResurrectionPending(long profileId)
	{
		final PhantomMaterializationService materialization = _materialization.get();
		if (materialization == null)
		{
			return false;
		}
		try (ActionLease action = materialization.tryAcquireAction(profileId).orElse(null))
		{
			return (action != null) && !action.player().isDead();
		}
	}

	private boolean nativeDead(long profileId)
	{
		final PhantomMaterializationService materialization = _materialization.get();
		if (materialization == null)
		{
			return false;
		}
		try (ActionLease action = materialization.tryAcquireAction(profileId).orElse(null))
		{
			return (action != null) && action.player().isDead();
		}
	}

	/** Starts the native corpse fallback without waiting for JDBC or background work. */
	public OperationResult recoverOrdinaryNativeCorpse(long profileId, int characterObjectId)
	{
		final PhantomMaterializationService materialization = _materialization.get();
		if ((materialization == null) || !_ordinaryPresence.test(profileId))
		{
			return retry("recovery.ordinary_presence");
		}
		try (ActionLease action = materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || (action.player().getObjectId() != characterObjectId) || (action.player().getParty() != null))
			{
				return retry("recovery.action_lease");
			}
			if (!action.player().isDead())
			{
				return OperationResult.replan("death.resurrected");
			}
			final OperationResult result = recoverNativeTown(action.player(), () -> false);
			if ("death.resurrected".equals(result.reason()))
			{
				return OperationResult.replan(result.reason());
			}
			if (result.successful())
			{
				_nativeTownReturns.put(profileId, characterObjectId);
			}
			return result;
		}
	}

	public OperationResult recover(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled)
	{
		synchronized (this)
		{
			if ((_state != ServiceState.RUNNING) || (_recoveries.putIfAbsent(profileId, Boolean.TRUE) != null))
			{
				return retry("recovery.busy_or_stopping");
			}
			increment(_currentOperations, _peakOperations);
		}
		try
		{
			return recoverOwned(profileId, goal, activityState, cancelled);
		}
		finally
		{
			_recoveries.remove(profileId);
			_currentOperations.decrementAndGet();
		}
	}

	private OperationResult recoverOwned(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled)
	{
		Objects.requireNonNull(cancelled, "cancelled");
		if ((activityState != PhantomActivityState.WARM) && !activityState.requiresMaterialization())
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
		if (loaded.successful() && (loaded.state() != null) && (loaded.state().state() == State.READY) && _nativeTownReturns.remove(profileId, loaded.state().identity().characterObjectId()))
		{
			return OperationResult.success("death.recovered_at_town");
		}
		if (!loaded.successful() || (loaded.state() == null) || ((loaded.state().state() != State.DEAD) && !((loaded.state().state() == State.MATERIALIZED) && ((loaded.state().vitals().currentHp() == 0) || nativeDead(profileId) || Objects.equals(_nativeTownReturns.get(profileId), loaded.state().identity().characterObjectId())))))
		{
			return OperationResult.replan("recovery.not_dead");
		}
		if (cancelled.getAsBoolean())
		{
			return retry("recovery.teleport_cancelled");
		}
		final PhantomMaterializationService materialization = _materialization.get();
		if (materialization == null)
		{
			return retry("recovery.materialization_absent");
		}
		final Optional<PhantomMaterializationService.MaterializationSnapshot> existingMaterialization = materialization.find(profileId);
		boolean restoreExistingMaterialization = existingMaterialization.isPresent() && (existingMaterialization.get().state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE);
		if (!restoreExistingMaterialization)
		{
			final PhantomMaterializationService.MaterializeResult materialized = materialization.materialize(profileId);
			if ((materialized.status() != ResultStatus.SUCCESS) && (materialized.status() != ResultStatus.ALREADY_ACTIVE))
			{
				return retry("recovery.materialization_" + materialized.status().name().toLowerCase());
			}
			restoreExistingMaterialization = materialized.status() == ResultStatus.ALREADY_ACTIVE;
		}
		final Optional<ActionLease> action = materialization.tryAcquireAction(profileId);
		if (action.isEmpty())
		{
			return retry("recovery.action_lease");
		}
		boolean resumedFromResurrection = false;
		try (ActionLease lease = action.get())
		{
			final Player player = lease.player();
			final boolean resumeBoundary = !player.isDead() && (loaded.state().state() == State.MATERIALIZED);
			resumedFromResurrection = resumeBoundary;
			if (!player.isDead() && !resumeBoundary)
			{
				return OperationResult.inconsistent("recovery.runtime_not_dead");
			}
			if (!resumeBoundary)
			{
				final OperationResult nativeRecovery = recoverNativeTown(player, cancelled);
				if (!nativeRecovery.successful())
				{
					return nativeRecovery;
				}
			}
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
		if (restoreExistingMaterialization)
		{
			final PhantomMaterializationService.MaterializeResult restored = materialization.materialize(profileId);
			if ((restored.status() != ResultStatus.SUCCESS) && (restored.status() != ResultStatus.ALREADY_ACTIVE))
			{
				return OperationResult.inconsistent("recovery.rematerialization_" + restored.status().name().toLowerCase());
			}
		}
		final boolean nativeTownReturn = _nativeTownReturns.remove(profileId, loaded.state().identity().characterObjectId());
		return OperationResult.success(resumedFromResurrection && !nativeTownReturn ? "death.resurrected" : "death.recovered_at_town");
	}

	private OperationResult recoverNativeTown(Player player, BooleanSupplier cancelled)
	{
		synchronized (player)
		{
			return recoverNativeTownLocked(player, cancelled);
		}
	}

	private OperationResult recoverNativeTownLocked(Player player, BooleanSupplier cancelled)
	{
		if (!player.isDead())
		{
			return OperationResult.success("death.resurrected");
		}
		final Location town = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
		if (town == null)
		{
			return retry("recovery.town_absent");
		}
		final var canonical = _authority.canonicalRecoveryPosition(town.getX(), town.getY(), town.getZ(), town.getInstanceId(), player.getHeading()).orElse(null);
		if (canonical == null)
		{
			return retry("recovery.canonical_town_absent");
		}
		final Location destination = new Location(canonical.x(), canonical.y(), canonical.z(), canonical.heading(), canonical.instanceId());
		player.doRevive();
		player.teleToLocation(destination, false);
		final int expectedZ = GeoEngine.getInstance().getHeight(destination.getX(), destination.getY(), destination.getZ());
		if (player.hasHeadlessOutboundSession() && player.isTeleporting())
		{
			player.onTeleported();
		}
		final long deadline = System.nanoTime() + RECOVERY_TELEPORT_TIMEOUT_NANOS;
		while (player.isTeleporting())
		{
			if (cancelled.getAsBoolean())
			{
				return retry("recovery.teleport_cancelled");
			}
			if (System.nanoTime() >= deadline)
			{
				return retry("recovery.teleport_timeout");
			}
			LockSupport.parkNanos(1_000_000L);
		}
		if ((player.getInstanceId() != destination.getInstanceId()) || (player.getX() != destination.getX()) || (player.getY() != destination.getY()) || ((player.getZ() != expectedZ + 5) && (player.getZ() != expectedZ)))
		{
			return OperationResult.inconsistent("recovery.teleport_destination_mismatch");
		}
		if (player.getZ() != expectedZ)
		{
			player.setXYZInvisible(destination.getX(), destination.getY(), expectedZ);
		}
		return OperationResult.success("death.native_town_return");
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
		installOwnedStoreBoundary(profileId, player);
		PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
		{
			return;
		}
		if (loaded.successful() && (loaded.state() != null) && !_authority.matchesRuntime(player, loaded.state()))
		{
			loaded = refreshNativeVitals(profileId, player, loaded);
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
	}

	/** Native maxima are derived on Player load after background level changes; preserve durable facts. */
	private PhantomBackgroundTransaction.Result refreshNativeVitals(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
	{
		// Match Player.store's player -> status order, including the pre-headless autosave window.
		synchronized (player)
		{
			synchronized (player.getStatus())
			{
				return refreshNativeVitalsLocked(profileId, player, loaded);
			}
		}
	}

	private PhantomBackgroundTransaction.Result refreshNativeVitalsLocked(long profileId, Player player, PhantomBackgroundTransaction.Result loaded)
	{
		final PhantomBackgroundState state = loaded.state();
		final var progress = state.progress();
		if (((state.state() != State.DEAD) && (state.state() != State.READY)) || !state.hashes().equals(_authority.hashes())) { return loaded; }
		if (state.vitals().currentCp() > player.getMaxCp()) { return loaded; }
		if ((state.state() == State.READY) && ((state.vitals().currentHp() > player.getMaxHp()) || (state.vitals().currentMp() > player.getMaxMp()))) { return loaded; }
		final var vitals = new PhantomBackgroundState.Vitals(state.vitals().currentHp(), player.getMaxHp(), state.state() == State.DEAD ? Math.min(state.vitals().currentMp(), player.getMaxMp()) : state.vitals().currentMp(), player.getMaxMp(), state.vitals().currentCp(), player.getMaxCp());
		final var normalized = new PhantomBackgroundState(state.state(), state.identity(), progress, vitals, state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
		if (!_authority.matchesRuntime(player, normalized)) { return loaded; }
		final var goal = _goals.load(profileId).orElse(null);
		if ((goal == null) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) || (goal.goal().status() != PhantomGoalStatus.ACTIVE)) { return loaded; }
		final var captured = _authority.capture(profileId, player, goal.goal(), normalized);
		if (!captured.vitals().equals(vitals)) { return loaded; }
		if (!captured.progress().equals(progress) || !captured.identity().equals(state.identity()) || !captured.position().equals(state.position()) || !captured.receipt().equals(state.receipt()) || !captured.clock().equals(state.clock()) || !captured.hashes().equals(state.hashes())) { return loaded; }
		if (!captured.inventory().objects().equals(state.inventory().objects()) || !captured.autoGetSkills().equals(state.autoGetSkills())) { return loaded; }
		// Existing native store/capture boundary under the materialization claim, with no historical replay.
		player.storeMe();
		return transaction(() -> _transactions.load(profileId));
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
		requireTransition(profileId, TransitionKind.MATERIALIZING);
		releaseTransition(profileId, TransitionKind.MATERIALIZING);
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		if (_transitions.get(profileId) != TransitionKind.MATERIALIZING)
		{
			return;
		}
		try
		{
			final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
			if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
			{
				return;
			}
			final PhantomBackgroundTransaction.Result recovered = transaction(() -> _transactions.abortMaterialization(profileId, characterObjectId));
			if (!recovered.successful() || (recovered.state() == null) || ((recovered.state().state() != State.READY) && (recovered.state().state() != State.DEAD)))
			{
				final boolean localFence = (recovered.state() != null) && (recovered.state().identity().profileId() == profileId) && (recovered.state().identity().characterObjectId() == characterObjectId) && (recovered.state().state() == State.INCONSISTENT) && ((recovered.status() == PhantomBackgroundTransaction.Status.INCONSISTENT) || (recovered.status() == PhantomBackgroundTransaction.Status.OWNED_STORE_CANONICAL_NEITHER));
				if (!localFence) { failStop(); }
			}
		}
		finally
		{
			releaseTransition(profileId, TransitionKind.MATERIALIZING);
		}
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		if (!claimStoreTransition(profileId))
		{
			throw new IllegalStateException("Background transition is already owned.");
		}
		_cleanupStores.put(profileId, Boolean.TRUE);
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
		try
		{
			afterStoreInternal(profileId, player);
		}
		finally
		{
			_cleanupStores.remove(profileId);
			releaseStoreTransition(profileId);
		}
	}

	private void afterStoreInternal(long profileId, Player player)
	{
		requireStoreTransition(profileId);
		if (player.hasOwnedStoreBoundary())
		{
			var completed = transaction(() -> _transactions.load(profileId));
			if (completed.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT) { return; }
			if (completed.successful() && (completed.state() != null) && (completed.state().state() == State.MATERIALIZED)) { completed = transaction(() -> _transactions.abortMaterialization(profileId, player.getObjectId())); }
			if (!completed.successful() || (completed.state() == null) || ((completed.state().state() != State.READY) && (completed.state().state() != State.DEAD))) { throw new IllegalStateException("OWNED_STORE_CLEANUP_NOT_FINALIZED"); }
			return;
		}
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
			releaseStoreTransition(profileId);
			return;
		}
		if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			final PhantomAcquisitionGoalSpec acquisition = PhantomAcquisitionGoalSpec.parse(goal);
			final PhantomBackgroundState captured = _authority.captureAcquisition(profileId, player, goal, previous, acquisition.itemId());
			final PhantomBackgroundTransaction.Result stored = transaction(() -> _transactions.captureBaseline(captured, goal));
			if (!stored.successful())
			{
				throw new IllegalStateException("Canonical acquisition background baseline capture failed.");
			}
			final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, player.getObjectId()));
			if (!verified.successful() || (verified.state() == null) || ((verified.state().state() != State.READY) && (verified.state().state() != State.DEAD)))
			{
				throw new IllegalStateException("Canonical acquisition background baseline verification failed.");
			}
			releaseStoreTransition(profileId);
			return;
		}
		try
		{
			PhantomBackgroundGoalSpec.parseLifecycle(goal);
		}
		catch (IllegalArgumentException exception)
		{
			if (previous != null)
			{
				throw new IllegalStateException("Existing background state no longer has an exact farm.background goal.", exception);
			}
			releaseStoreTransition(profileId);
			return;
		}
		final PhantomBackgroundState captured = _authority.capture(profileId, player, goal, previous);
		final PhantomBackgroundTransaction.Result stored = transaction(() -> goal.status() == PhantomGoalStatus.ACTIVE ? _transactions.captureBaseline(captured, goal) : _transactions.captureLifecycleBaseline(captured, goal));
		if (!stored.successful())
		{
			throw new IllegalStateException("Canonical background baseline capture failed: " + stored.status());
		}
		final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, player.getObjectId()));
		if (!verified.successful() || (verified.state() == null) || ((verified.state().state() != State.READY) && (verified.state().state() != State.DEAD)))
		{
			throw new IllegalStateException("Canonical background baseline verification failed.");
		}
		releaseStoreTransition(profileId);
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_state, _currentOperations.get(), _peakOperations.get(), _currentIdentityLeases.get(), _peakIdentityLeases.get(), _currentTransactions.get(), _peakTransactions.get(), _currentTransitionClaims.get(), _peakTransitionClaims.get(), _completedOperations.get(), _idempotentOperations.get(), _retryOperations.get(), _failedOperations.get(), _retainedIdentityLeases.size());
	}

	public QuiescenceSnapshot materializationQuiescence()
	{
		final int materializing = transitionClaimCount(TransitionKind.MATERIALIZING);
		return new QuiescenceSnapshot(_currentOperations.get(), _currentIdentityLeases.get(), _currentTransactions.get(), _retainedIdentityLeases.size(), materializing);
	}

	private OperationClaim acquire(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence)
	{
		return acquire(profileId, goal, activityGeneration, tickSequence, false);
	}

	private OperationClaim acquire(long profileId, PhantomGoal goal, long activityGeneration, long tickSequence, boolean historical)
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
		if (_partyParticipation.blocksBackground(profileId))
		{
			return releaseFailedOperation(profileId, retry("party.materialized_only"));
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
				spec = historical && (actual != null) && (actual.status() == PhantomGoalStatus.ACTIVE) && PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(actual.goalType()) ? PhantomBackgroundGoalSpec.parseLifecycle(actual) : PhantomBackgroundGoalSpec.parse(actual);
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

	private OperationClaim acquireAcquisition(long profileId, PhantomGoal goal, long goalRowVersion, PhantomAcquisitionState acquisitionState, long activityGeneration, long tickSequence)
	{
		if (_state != ServiceState.RUNNING)
		{
			return OperationClaim.failed(retry("service.not_running"));
		}
		if (_operations.putIfAbsent(profileId, Boolean.TRUE) != null)
		{
			return OperationClaim.failed(retry("background.busy"));
		}
		increment(_currentOperations, _peakOperations);
		if (_partyParticipation.blocksBackground(profileId))
		{
			return releaseFailedOperation(profileId, retry("party.materialized_only"));
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
			final PhantomGoalStateStore.StoredGoal storedGoal = _goals.load(profileId).orElse(null);
			if ((storedGoal == null) || (storedGoal.rowVersion() != goalRowVersion) || !storedGoal.goal().equals(goal))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, OperationResult.replan("goal.stale"));
			}
			final PhantomAcquisitionGoalSpec spec = PhantomAcquisitionGoalSpec.parse(storedGoal.goal());
			if ((acquisitionState.goalId() != goal.goalId()) || (acquisitionState.goalRevision() != goal.revision()) || (acquisitionState.targetItemId() != spec.itemId()))
			{
				closeLease(lease);
				return releaseFailedOperation(profileId, OperationResult.replan("acquisition.state.stale"));
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
			return OperationClaim.acquired(this, profileId, characterObjectId, lease, loaded.state(), null);
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

	public FarmInputAttempt historicalFarmAttempt(long profileId, PhantomGoal goal)
	{
		final PhantomBackgroundState state = acquisitionSnapshot(profileId).orElseThrow();
		return ordinaryFarmAttempt(profileId, state.identity().characterObjectId(), state, PhantomBackgroundGoalSpec.parse(goal));
	}

	private FarmInputAttempt ordinaryFarmAttempt(long profileId, int characterObjectId, PhantomBackgroundState state, PhantomBackgroundGoalSpec spec)
	{
		try
		{
			if (!state.hashes().equals(_authority.hashes())) { return FarmInputAttempt.failed(FarmInputFailure.AUTHORITY_STALE, "farm.generation_changed"); }
			final List<Integer> skillIds = java.util.stream.Stream.concat(_authority.ordinarySpoilSkillIds(state.identity().activeClassId()).stream(), java.util.stream.Stream.of(state.loadout().selectedSkillId())).filter(id -> id > 0).distinct().sorted().toList();
			Map<Integer, Integer> skills = Map.of();
			if (!skillIds.isEmpty())
			{
				final var eligibility = transaction(() -> _transactions.readAcquisitionEligibility(profileId, characterObjectId, state.identity().classIndex(), state.identity().activeClassId(), skillIds, state.hashes().progression(), _authority.hashes()));
				if (!eligibility.successful()) { return FarmInputAttempt.failed(FarmInputFailure.RESOURCE_STALE, "farm.durable_skill_evidence_unavailable:" + eligibility.status()); }
				skills = eligibility.snapshot().skillLevels();
				if ((state.loadout().selectedSkillId() > 0) && (skills.getOrDefault(state.loadout().selectedSkillId(), 0) < state.loadout().selectedSkillLevel())) { return FarmInputAttempt.failed(FarmInputFailure.RESOURCE_STALE, "farm.durable_skill_changed"); }
			}
			return _authority.tryFarmInput(state, spec, skills);
		}
		catch (RuntimeException exception)
		{
			return FarmInputAttempt.failed(FarmInputFailure.UNKNOWN, exception.getClass().getSimpleName());
		}
	}

	public static String farmFailureReason(FarmInputAttempt attempt)
	{
		return "catchup.authority." + attempt.failure().name().toLowerCase(java.util.Locale.ROOT) + (attempt.failure() == FarmInputFailure.UNKNOWN ? ":" + attempt.reason() : "");
	}

	private OperationResult commit(OperationClaim claim, PhantomBackgroundTransaction.Command command)
	{
		if (_partyParticipation.blocksBackground(claim.profileId()))
		{
			return retry("party.materialized_only");
		}
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
			publishPosition(claim.profileId(), verified);
			_idempotentOperations.incrementAndGet();
			return OperationResult.idempotent("transaction.idempotent");
		}
		if (result.status() == PhantomBackgroundTransaction.Status.SUCCESS)
		{
			publishPosition(claim.profileId(), result);
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

	private void publishPosition(long profileId, PhantomBackgroundTransaction.Result result)
	{
		if (result.state() != null)
		{
			_committedPosition.accept(profileId, result.state().position());
		}
	}

	private static String bindingHash(PhantomAcquisitionState.MethodBinding binding)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.toString(binding, "none").getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public OperationResult mapTransactionFailure(PhantomBackgroundTransaction.Status status)
	{
		return switch (status)
		{
			case STALE_OPERATION, GOAL_STALE, HASH_STALE, STATE_CONFLICT, STATE_ABSENT, PROFILE_LINK_STALE, CATCHUP_CONFLICT -> OperationResult.replan("transaction." + status.name().toLowerCase());
			case ITEM_CONFLICT -> OperationResult.inconsistent("transaction.item_conflict_canonical");
			case INCONSISTENT, OWNED_STORE_CANONICAL_NEITHER, CANONICAL_MISMATCH, ITEM_LIMIT, UNSUPPORTED_ITEM, UNSUPPORTED_INSTANCE, OBJECT_ID_EXHAUSTED, PROGRESSION_CONFLICT, ACQUISITION_CONFLICT -> OperationResult.inconsistent("transaction." + status.name().toLowerCase());
			case ITEM_BUSY, ITEM_EXPECTED_COUNT_STALE -> retry("transaction." + status.name().toLowerCase());
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

	private boolean claimStoreTransition(long profileId)
	{
		synchronized (this)
		{
			final TransitionKind existing = _transitions.get(profileId);
			if ((existing == TransitionKind.MATERIALIZING) || (existing == TransitionKind.DEMATERIALIZING))
			{
				return true;
			}
			final boolean lifecycleAllowed = (_state == ServiceState.RUNNING) || (_state == ServiceState.STOPPING);
			if (!lifecycleAllowed || (existing != null) || _operations.containsKey(profileId))
			{
				return false;
			}
			_transitions.put(profileId, TransitionKind.DEMATERIALIZING);
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

	private void requireStoreTransition(long profileId)
	{
		final TransitionKind kind = _transitions.get(profileId);
		if ((kind != TransitionKind.MATERIALIZING) && (kind != TransitionKind.DEMATERIALIZING))
		{
			throw new IllegalStateException("Background store transition identity mismatch.");
		}
	}

	private void releaseTransition(long profileId, TransitionKind kind)
	{
		if (_transitions.remove(profileId, kind))
		{
			_currentTransitionClaims.decrementAndGet();
		}
	}

	private void releaseStoreTransition(long profileId)
	{
		if (_transitions.get(profileId) == TransitionKind.DEMATERIALIZING)
		{
			releaseTransition(profileId, TransitionKind.DEMATERIALIZING);
		}
	}

	private int transitionClaimCount(TransitionKind kind)
	{
		return (int) _transitions.values().stream().filter(kind::equals).count();
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

	public record QuiescenceSnapshot(int operations, int identityLeases, int transactions, int retainedIdentityLeases, int materializingTransitionClaims)
	{
		public boolean ready()
		{
			return (operations == 0) && (identityLeases == 0) && (transactions == 0) && (retainedIdentityLeases == 0) && (materializingTransitionClaims == 0);
		}
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
