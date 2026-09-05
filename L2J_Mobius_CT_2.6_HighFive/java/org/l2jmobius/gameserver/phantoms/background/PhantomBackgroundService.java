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
import java.util.function.Supplier;

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
		if (_partyParticipation.blocksBackground(profileId))
		{
			return new Directive(DirectiveKind.RETRY, "party.materialized_only", "");
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
			final boolean recoverable = (state.state() == State.DEAD) || ((state.state() == State.MATERIALIZED) && (state.vitals().currentHp() == 0));
			return recoverable ? new Directive(DirectiveKind.RECOVER, "state.dead", spec.anchorId()) : new Directive(DirectiveKind.REPLAN, "recovery.not_dead", state.position().committedAnchorId());
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
		final OperationClaim claim = acquire(profileId, goal, expectedCatchup.generation(), Math.addExact(expectedCatchup.intervalOrdinal(), 1));
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
			if (state.state() == State.DEAD)
			{
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_DEAD_IDLE, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), state.position(), state.clock(), Map.of(), state.autoGetSkills(), List.of(), null, mutation);
				return commit(claim, command);
			}
			if (!state.position().committedAnchorId().equals(spec.anchorId()))
			{
				final TravelAdvance advance;
				try
				{
					advance = _authority.advanceTravel(state, spec, FARM_TRAVEL_BUDGET_MILLIS);
				}
				catch (RuntimeException exception)
				{
					return OperationResult.replan("catchup.travel.unsupported");
				}
				if (!advance.mutated())
				{
					return switch (advance.status())
					{
						case EDGE_CLOSED, NO_ROUTE -> retry("catchup.travel." + advance.status().name().toLowerCase());
						default -> OperationResult.replan("catchup.travel." + advance.status().name().toLowerCase());
					};
				}
				final PhantomBackgroundOperationKey key = new PhantomBackgroundOperationKey(profileId, claim.characterObjectId(), goal.goalId(), goal.revision(), 0, 0, ActionKind.HISTORICAL_TRAVEL, spec.npcId(), spec.anchorId(), PhantomBackgroundState.MODEL_VERSION, _authority.hashes(), null, historical);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, state.progress(), state.vitals(), advance.position(), advance.clock(), Map.of(), state.autoGetSkills(), List.of(), null, mutation);
				return commit(claim, command);
			}
			final FarmInput input;
			try
			{
				input = _authority.farmInput(state, spec);
			}
			catch (RuntimeException exception)
			{
				return OperationResult.replan("catchup.authority.unsupported");
			}
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
					return batch.reason() == PhantomBackgroundModel.ResultReason.TIME_BUDGET ? retry("model.time_budget") : OperationResult.replan("model." + batch.reason().name().toLowerCase());
				}
				final List<PhantomBackgroundState.AutoGetSkill> autoSkills = _authority.autoGetSkills(state.identity(), batch.progress().level());
				final Clock clock = new Clock(batch.nextRngState(), 0, 0);
				final PhantomBackgroundTransaction.Command command = new PhantomBackgroundTransaction.Command(state, goal, key, batch.progress(), batch.vitals(), state.position(), clock, batch.inventoryDelta().itemDeltas(), autoSkills, List.of(), null, mutation);
				return commit(claim, command).withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
			}
		}
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
					questFormula = new QuestFormula(rule.chanceKind() == PhantomAcquisitionQuestCatalog.ChanceKind.NONE ? 0 : rule.rollBound(), rule.chanceKind() == PhantomAcquisitionQuestCatalog.ChanceKind.NONE ? 0 : rule.rollThreshold(), rule.maximumCount(), quest.itemCountBeforeKill(), quest.itemCap());
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
		if (activityState != PhantomActivityState.BACKGROUND)
		{
			return OperationResult.replan("activity.not_background");
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
		return recover(profileId, goal, activityState, () -> false);
	}

	public OperationResult recover(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled)
	{
		Objects.requireNonNull(cancelled, "cancelled");
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
		if (!loaded.successful() || (loaded.state() == null) || ((loaded.state().state() != State.DEAD) && !((loaded.state().state() == State.MATERIALIZED) && (loaded.state().vitals().currentHp() == 0))))
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
			final boolean resumeBoundary = !player.isDead() && (loaded.state().state() == State.MATERIALIZED);
			if (!player.isDead() && !resumeBoundary)
			{
				return OperationResult.inconsistent("recovery.runtime_not_dead");
			}
			final Location destination = MapRegionData.getInstance().getTeleToLocation(player, TeleportWhereType.TOWN);
			if (destination == null)
			{
				return retry("recovery.town_absent");
			}
			if (!resumeBoundary)
			{
				player.doRevive();
				player.teleToLocation(destination, false);
			}
			final int expectedInstanceId = destination.getInstanceId();
			final int expectedX = destination.getX();
			final int expectedY = destination.getY();
			final int expectedZ = GeoEngine.getInstance().getHeight(expectedX, expectedY, destination.getZ());
			final int teleportedZ = expectedZ + 5;
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
			if ((player.getInstanceId() != expectedInstanceId) || (player.getX() != expectedX) || (player.getY() != expectedY) || ((player.getZ() != teleportedZ) && (player.getZ() != expectedZ)))
			{
				return OperationResult.inconsistent("recovery.teleport_destination_mismatch");
			}
			if (player.getZ() != expectedZ)
			{
				player.setXYZInvisible(expectedX, expectedY, expectedZ);
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
				failStop();
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
			releaseStoreTransition(profileId);
		}
	}

	private void afterStoreInternal(long profileId, Player player)
	{
		requireStoreTransition(profileId);
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
			PhantomBackgroundGoalSpec.parse(goal);
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

	private OperationResult mapTransactionFailure(PhantomBackgroundTransaction.Status status)
	{
		return switch (status)
		{
			case STALE_OPERATION, GOAL_STALE, HASH_STALE, STATE_CONFLICT, STATE_ABSENT, PROFILE_LINK_STALE, CATCHUP_CONFLICT -> OperationResult.replan("transaction." + status.name().toLowerCase());
			case INCONSISTENT, CANONICAL_MISMATCH, ITEM_CONFLICT, ITEM_LIMIT, UNSUPPORTED_ITEM, UNSUPPORTED_INSTANCE, OBJECT_ID_EXHAUSTED, PROGRESSION_CONFLICT, ACQUISITION_CONFLICT -> OperationResult.inconsistent("transaction." + status.name().toLowerCase());
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
			if (existing == TransitionKind.MATERIALIZING)
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
