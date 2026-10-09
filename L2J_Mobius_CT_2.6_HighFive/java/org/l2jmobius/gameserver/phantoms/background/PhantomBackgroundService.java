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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
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
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointOutcome;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointKey;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope.CheckpointResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyParticipationPort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
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
	public static final String NATIVE_CONTEXT_SIGNAL_SOURCE = "background.native_context";
	public static final long NATIVE_CONTEXT_SIGNAL_TTL_MILLIS = 60_000;
	// Optional exact-argument test observer. Transactions have returned; absent-Player ownership still fences admission.
	private static volatile BiConsumer<Long, PhantomBackgroundState> _recoveryObserver;
    private static volatile BiConsumer<PhantomBackgroundTransaction.Command, PhantomBackgroundTransaction.Result> _commitObserver;

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
	private volatile LongPredicate _nativeContextDemand = profileId -> true;
	private volatile BiConsumer<Long, PhantomBackgroundState.Position> _committedPosition = (profileId, position) -> {};
	private boolean _positionPublisherInstalled;
	private boolean _presenceInstalled;
	private boolean _nativeContextDemandInstalled;
	private volatile LongFunction<OperationResult> _periodicFarm;
	private volatile PhantomHistoricalBackgroundService _nativeRecoveryHistory;
	private final ConcurrentHashMap<Long, Boolean> _operations = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, PhantomBackgroundState> _arrivalCaptures = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, VisibleCheckpoint> _visibleCheckpoints = new ConcurrentHashMap<>();
	private static final class VisibleCheckpoint
	{
		final Player player;
		final PhantomNativeWorkScope scope;
		final PhantomGoal goal;
		final String anchor;
		final CheckpointKey key;
		final AtomicBoolean executing = new AtomicBoolean();
		final AtomicBoolean queued = new AtomicBoolean();
		volatile CheckpointResult result;
		volatile PhantomBackgroundState runtimeWitness;
		volatile long retryAfter;
		int recoveryAttempts;
		VisibleCheckpoint(long id, Player player, PhantomNativeWorkScope scope, PhantomGoal goal, String anchor)
		{
			this.player = player; this.scope = scope; this.goal = goal; this.anchor = anchor;
			key = new CheckpointKey(id, player.getObjectId(), scope.epoch(), goal.goalId(), goal.revision(), id + ":" + scope.epoch() + ":" + goal.goalId() + ":" + goal.revision() + ":" + anchor);
		}
	}
	private final ConcurrentHashMap<Long, Boolean> _cleanupStores = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> _nativeTownReturns = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Boolean> _recoveries = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, RecoveryControl> _recoveryControls = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicReference<String> _firstRecoveryFailure = new java.util.concurrent.atomic.AtomicReference<>("");
	private static final class RecoveryControl
	{
		final long profileId, deadline = System.nanoTime() + 10_000_000_000L;
		final PhantomGoal goal; final PhantomActivityState activity; final BooleanSupplier cancelled;
		final AtomicBoolean queued = new AtomicBoolean(), finished = new AtomicBoolean();
		volatile OperationResult result;
		int objectId; long epoch; boolean storeRequested, restoreExisting, resumedFromResurrection, baselinePending;
		PhantomHistoricalBackgroundService.NativeRecoveryHandoff handoff;
		RecoveryControl(long profileId, PhantomGoal goal, PhantomActivityState activity, BooleanSupplier cancelled, PhantomMaterializationService.MaterializationSnapshot entry)
		{
			this.profileId = profileId; this.goal = goal; this.activity = activity; this.cancelled = cancelled;
			objectId = entry == null ? 0 : entry.characterObjectId(); epoch = entry == null ? 0 : entry.materializedAtNanos();
		}
	}
	private final ConcurrentHashMap<Long, TransitionKind> _transitions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, HistoricalAdmission> _historicalAdmissions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, NativeContextSignal> _nativeContextSignals = new ConcurrentHashMap<>();
	private int _currentContextSignals;
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

	/** Relevance demand is independent of the scalar gate that prohibits unsupported simulation. */
	public synchronized void installNativeContextDemand(LongPredicate demand)
	{
		if (_nativeContextDemandInstalled || (_state == ServiceState.STOPPING) || (_state == ServiceState.STOPPED))
		{
			throw new IllegalStateException("Native context demand can only be installed once before work starts.");
		}
		_nativeContextDemand = Objects.requireNonNull(demand, "Native context demand must not be null.");
		_nativeContextDemandInstalled = true;
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

	synchronized void bindNativeRecoveryHistory(PhantomHistoricalBackgroundService history)
	{
		if ((_nativeRecoveryHistory != null) || (_state == ServiceState.STOPPING) || (_state == ServiceState.STOPPED)) { throw new IllegalStateException("Native recovery history is already bound or stopping."); }
		_nativeRecoveryHistory = Objects.requireNonNull(history);
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
		if ((_currentOperations.get() != 0) || (_currentIdentityLeases.get() != 0) || (_currentTransactions.get() != 0) || (_currentTransitionClaims.get() != 0) || (_currentContextSignals != 0) || !_retainedIdentityLeases.isEmpty())
		{
			return false;
		}
		_nativeTownReturns.clear();
		_nativeContextSignals.clear();
		_visibleCheckpoints.clear();
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
		final var context = transaction(() -> _transactions.nativeContext(profileId, state.identity().characterObjectId()));
		if ((context.status() != PhantomBackgroundTransaction.Status.SUCCESS) && (context.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED))
		{
			final var failure = mapTransactionFailure(context.status());
			return new Directive(failure.status() == OperationStatus.INCONSISTENT ? DirectiveKind.INCONSISTENT : DirectiveKind.RETRY, "native_context." + context.status().name().toLowerCase(java.util.Locale.ROOT), state.position().committedAnchorId());
		}
		if (!state.equals(context.state())) { return new Directive(DirectiveKind.RETRY, "native_context.state_changed", state.position().committedAnchorId()); }
		if ((state.state() == State.VERIFY_PENDING) || (context.context().phase() == PhantomNativeContext.Phase.PENDING))
		{
			final boolean visiblePending = ((activityState == PhantomActivityState.WARM) || activityState.requiresMaterialization()) && hasVisibleOwnedStorePending(profileId, goal);
			return new Directive(visiblePending ? DirectiveKind.REPLAN : DirectiveKind.RETRY, visiblePending ? "visible.owned_store_pending" : "native_context.pending", state.position().committedAnchorId());
		}
		final boolean farmPosition = context.context().afterPolicy() == null || context.context().afterPolicy().farmPosition();
        final var operation = state.position().committedAnchorId().equals(spec.anchorId()) && farmPosition
            ? PhantomBackgroundSimulationPolicy.Operation.FARM : PhantomBackgroundSimulationPolicy.Operation.TRAVEL;
        final boolean nativeRequired = !context.context().permits(operation, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint());
		final var delivery = updateNativeContextSignal(profileId, context, nativeRequired);
		if (nativeRequired && (activityState == PhantomActivityState.BACKGROUND))
		{
			return new Directive(DirectiveKind.NATIVE_REQUIRED, nativeContextReason(delivery), state.position().committedAnchorId());
		}
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
		return state.position().committedAnchorId().equals(spec.anchorId()) && farmPosition ? new Directive(DirectiveKind.FARM, "farm.ready", spec.anchorId()) : new Directive(DirectiveKind.TRAVEL, "travel.required", spec.anchorId());
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
			final var nativeGate = ordinaryContextGate(claim, PhantomBackgroundSimulationPolicy.Operation.FARM);
			if (nativeGate != null) { return nativeGate; }
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
				final BatchResult batch = _model.evaluate(new BatchRequest(state, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false), claim._nativeContext.afterPolicy());
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
				final OperationResult result = commit(claim, ordinaryCommand(claim, command, batch.policy()));
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
			final var nativeGate = ordinaryContextGate(claim, null);
			if (nativeGate != null) { return nativeGate; }
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
			if (!state.position().committedAnchorId().equals(spec.anchorId()) || claim._nativeContext.afterPolicy() != null && !claim._nativeContext.afterPolicy().farmPosition())
			{
				final TravelAdvance advance;
				try
				{
					advance = _authority.advanceTravel(state, spec, FARM_TRAVEL_BUDGET_MILLIS, expectedCatchup.cursorEpochMinute(), claim._nativeContext.afterPolicy());
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
				return commit(claim, travelCommand(claim, command, FARM_TRAVEL_BUDGET_MILLIS, expectedCatchup.cursorEpochMinute()));
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
				final BatchResult batch = _model.evaluate(new BatchRequest(state, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false), claim._nativeContext.afterPolicy());
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
				return commit(claim, ordinaryCommand(claim, command, batch.policy())).withModel(batch.encounters(), batch.elapsedMillis(), batch.dead());
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
			final var nativeGate = nativeContextGate(profileId, background);
			if (nativeGate != null) { return nativeGate; }
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
				final var nativeGate = nativeContextGate(profileId, state);
				if (nativeGate != null) { return nativeGate; }
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

	/** Exact historical prerequisite; signal delivery follows the completed scalar read outside DB locks. */
	PhantomBackgroundTransaction.NativeContextResult historicalNativeContext(long profileId, PhantomBackgroundState expected)
	{
		Objects.requireNonNull(expected, "expected");
		if (_state != ServiceState.RUNNING) { return PhantomBackgroundTransaction.NativeContextResult.rejected(PhantomBackgroundTransaction.Status.BACKEND_FAILURE); }
		final var proof = transaction(() -> _transactions.nativeContext(profileId, expected.identity().characterObjectId()));
		if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) && (proof.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED)) { return proof; }
		if (!expected.equals(proof.state())) { return PhantomBackgroundTransaction.NativeContextResult.rejected(PhantomBackgroundTransaction.Status.STATE_CONFLICT); }
		if ((_state != ServiceState.RUNNING) || (proof.context() == null)) { return PhantomBackgroundTransaction.NativeContextResult.rejected(PhantomBackgroundTransaction.Status.BACKEND_FAILURE); }
		if ((expected.state() != State.VERIFY_PENDING) && (proof.context().phase() != PhantomNativeContext.Phase.PENDING))
		{
			updateNativeContextSignal(profileId, proof, !proof.context().permits(proof.context().afterPolicy() != null && !proof.context().afterPolicy().farmPosition()
                ? PhantomBackgroundSimulationPolicy.Operation.TRAVEL : PhantomBackgroundSimulationPolicy.Operation.FARM, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint()));
		}
		return _state == ServiceState.RUNNING ? proof : PhantomBackgroundTransaction.NativeContextResult.rejected(PhantomBackgroundTransaction.Status.BACKEND_FAILURE);
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

	/** Durable recovery precedes historical eligibility, under the same exclusive absent-Player lease. */
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
			if (!loaded.successful() || (loaded.state() == null) || ((loaded.state().state() != State.MATERIALIZED) && (loaded.state().state() != State.VERIFY_PENDING)) || (loaded.state().identity().profileId() != profileId) || (loaded.state().identity().characterObjectId() != characterObjectId))
			{
				return OperationResult.replan("recovery.background_state_invalid");
			}
			PhantomBackgroundTransaction.Result recovered = loaded.state().state() == State.VERIFY_PENDING
				? transaction(() -> _transactions.reconcileVerifyPending(profileId, characterObjectId))
				: transaction(() -> _transactions.abortMaterialization(profileId, characterObjectId));
			if (recovered.successful() && (recovered.state() != null) && (recovered.state().state() == State.MATERIALIZED))
			{
				// Keep the lease/transition through a bounded abandoned transition; no actor may enter between steps.
				recovered = transaction(() -> _transactions.abortMaterialization(profileId, characterObjectId));
			}
			if (recovered.successful() && (recovered.state() != null) && ((recovered.state().state() == State.READY) || (recovered.state().state() == State.DEAD)))
			{
				final var observer = _recoveryObserver;
				if (observer != null)
				{
					try { observer.accept(profileId, recovered.state()); }
					catch (Throwable ignored) { /* An observational failure cannot change a confirmed durable recovery. */ }
				}
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

	/** Pending native store is a control operation, never ordinary gameplay admission. */
	public boolean hasVisibleOwnedStorePending(long profileId, PhantomGoal goal)
	{
		final var entry = _materialization.get().find(profileId).orElse(null);
		if ((entry == null) || !entry.worldPresent() || !entry.actionAdmissionOpen() || (entry.cleanupPhase() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE)) { return false; }
		final Player player = World.getInstance().getPlayer(entry.characterObjectId());
		return (player != null) && (player.getClient() == null) && !player.isDead() && !player.isInParty() && pendingStoreOwnerCurrent(profileId, player, goal);
	}

	public VisibleStoreResult resumeVisibleOwnedStore(long profileId, Player player, PhantomGoal goal)
	{
		final var checkpoint = _visibleCheckpoints.get(profileId);
		if (checkpoint != null && checkpoint.player == player && checkpoint.goal.equals(goal))
		{
			final var result = runVisibleCheckpoint(checkpoint);
			return new VisibleStoreResult(result.outcome() == CheckpointOutcome.RESUME ? VisibleStoreStatus.SUCCESS : VisibleStoreStatus.RETRY, result.outcome() + ":" + result.reason());
		}
		if ((_state != ServiceState.RUNNING) || _transitions.containsKey(profileId)) { return new VisibleStoreResult(VisibleStoreStatus.RETRY, "owned_store.service_or_transition"); }
		// A failed checkpoint has no ordinary ActionLease. Validate its exact control lifetime.
		if (!pendingStoreOwnerCurrent(profileId, player, goal)) { return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.live_owner_or_intent_missing"); }
		try
		{
			return org.l2jmobius.gameserver.model.actor.PlayerNativeWork.pendingStoreCheckpoint(player, this, () ->
			{
				if (!pendingStoreOwnerCurrent(profileId, player, goal)) { return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.checkpoint_owner_changed"); }
				return new VisibleStoreResult(player.resumePendingOwnedStore(this, goal.goalId(), goal.revision()) ? VisibleStoreStatus.SUCCESS : VisibleStoreStatus.RETRY, "owned_store.live_resume");
			});
		}
		catch (RuntimeException failure)
		{
			return new VisibleStoreResult(VisibleStoreStatus.PROFILE_FENCED, "owned_store.live_resume:" + failure.getMessage());
		}
	}

	private boolean pendingStoreOwnerCurrent(long profileId, Player player, PhantomGoal goal)
	{
		final var entry = _materialization.get().find(profileId).orElse(null);
		final var owner = player.getNativeWorkOwner();
		return (_state == ServiceState.RUNNING) && !_transitions.containsKey(profileId) && (entry != null)
			&& (entry.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE)
			&& (entry.characterObjectId() == player.getObjectId()) && entry.identityLeaseRetained() && entry.outboundAttached()
			&& (owner != null) && (owner.player() == player) && owner.isCurrent() && (owner.epoch() == entry.materializedAtNanos())
			&& (World.getInstance().getPlayer(player.getObjectId()) == player) && (World.getInstance().findObject(player.getObjectId()) == player)
			&& player.hasHeadlessOutboundSession() && player.hasOwnedStoreBoundary(this) && player.hasPendingOwnedStore()
			&& Objects.equals(_goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null), goal);
	}

	public boolean captureVisibleArrival(long profileId, Player player, PhantomGoal goal, String anchorId)
	{
		final var owner = player.getNativeWorkOwner();
		if (owner instanceof PhantomNativeWorkScope scope)
		{
			final var candidate = new VisibleCheckpoint(profileId, player, scope, goal, anchorId);
			if (!visibleCheckpointOwnerCurrent(candidate)) { return false; }
			final var existing = _visibleCheckpoints.putIfAbsent(profileId, candidate);
			final var request = existing == null ? candidate : existing;
			if (request.player != player || request.scope != scope || !request.key.equals(candidate.key)) { return false; }
			final var result = runVisibleCheckpoint(request);
			if (result.outcome() == CheckpointOutcome.RESUME) { _visibleCheckpoints.remove(profileId, request); return true; }
			return false;
		}
		try
		{
			return org.l2jmobius.gameserver.model.actor.PlayerNativeWork.checkpoint(player, () ->
			{
				checkpointStage(player, "CAPTURE");
				return captureVisibleArrivalQuiescent(profileId, player, goal, anchorId);
			});
		}
		catch (RuntimeException failure) { if (owner instanceof org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope scope) { scope.recordCheckpointFailure(failure); } return false; }
	}

	/** Scheduler preflight finishes the admitted store before ordinary admission; no ActionLease is required. */
	public Optional<CheckpointResult> continueVisibleCheckpoint(long profileId, PhantomGoal goal)
	{
		final var request = _visibleCheckpoints.get(profileId);
		if (request == null) { return Optional.empty(); }
		if (!request.scope.isCurrent()) { _visibleCheckpoints.remove(profileId, request); return Optional.empty(); }
		if (!request.goal.equals(goal))
		{
			if (request.executing.compareAndSet(false, true))
			{
				try
				{
					final var result = request.result;
					if (result != null && (result.outcome() == CheckpointOutcome.RETRY_BEFORE_WRITE || result.outcome() == CheckpointOutcome.RESUME)
						&& request.scope.open() && !request.player.hasPendingOwnedStore() && visibleCheckpointOwnerCurrent(request, false)
						&& _visibleCheckpoints.remove(profileId, request)) { return Optional.empty(); }
				}
				finally { request.executing.set(false); }
			}
			return Optional.of(new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, "", "owned_checkpoint.goal_changed"));
		}
		return Optional.of(runVisibleCheckpoint(request));
	}

	private boolean visibleCheckpointOwnerCurrent(VisibleCheckpoint request)
	{
		return visibleCheckpointOwnerCurrent(request, true);
	}

	private boolean visibleCheckpointOwnerCurrent(VisibleCheckpoint request, boolean requireGoal)
	{
		final long id = request.key.profileId();
		final var entry = _materialization.get().find(id).orElse(null);
		return _state == ServiceState.RUNNING && !_transitions.containsKey(id) && entry != null
			&& entry.state() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.ACTIVE
			&& entry.cleanupPhase() == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.CleanupPhase.NONE
			&& entry.characterObjectId() == request.key.objectId() && entry.materializedAtNanos() == request.key.epoch()
			&& entry.identityLeaseRetained() && entry.outboundAttached() && request.scope.isCurrent()
			&& request.player.getNativeWorkOwner() == request.scope && request.player.getClient() == null
			&& World.getInstance().getPlayer(request.key.objectId()) == request.player && World.getInstance().findObject(request.key.objectId()) == request.player
			&& request.player.hasHeadlessOutboundSession() && request.player.hasOwnedStoreBoundary(this)
			&& (!requireGoal || Objects.equals(_goals.load(id).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null), request.goal));
	}

	private CheckpointResult runVisibleCheckpoint(VisibleCheckpoint request)
	{
		if (!visibleCheckpointOwnerCurrent(request)) { return new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, "", "owned_checkpoint.owner_changed"); }
		final var last = request.result;
		if (last != null && (last.outcome() == CheckpointOutcome.RESUME || last.outcome() == CheckpointOutcome.VERIFY_WRITE_OUTCOME || last.outcome() == CheckpointOutcome.TERMINAL_RETAIN)) { return last; }
		if (last != null && last.outcome() == CheckpointOutcome.RETRY_BEFORE_WRITE && (System.nanoTime() < request.retryAfter || request.player.isInCombat())) { return last; }
		if (!request.executing.compareAndSet(false, true)) { return new CheckpointResult(CheckpointOutcome.WAIT_EARNED, "", "owned_checkpoint.executing"); }
		try
		{
			request.result = request.scope.ownedCheckpoint(request.key, () ->
			{
				if (!visibleCheckpointOwnerCurrent(request)) { throw new IllegalStateException("OWNED_CHECKPOINT_OWNER_CHANGED"); }
				if (last != null && (last.outcome() == CheckpointOutcome.RESOLVE_RECEIPT || last.outcome() == CheckpointOutcome.PUBLISH_COMMITTED) && ++request.recoveryAttempts > 3)
				{
					request.scope.retainOwnedCheckpoint(request.key, "owned_checkpoint.recovery_attempts_exhausted:" + last.reason());
					return false;
				}
				if (request.scope.ownedCheckpointNeedsPublication(request.key)) { return publishVisibleCheckpoint(request); }
				if (request.player.hasPendingOwnedStore())
				{
					if (!pendingStoreOwnerCurrent(request.key.profileId(), request.player, request.goal) || !request.player.resumePendingOwnedStore(this, request.goal.goalId(), request.goal.revision())) { return false; }
					return publishVisibleCheckpoint(request);
				}
				checkpointStage(request.player, "CAPTURE");
				return captureVisibleArrivalQuiescent(request.key.profileId(), request.player, request.goal, request.anchor);
			}, () -> enqueueVisibleCheckpoint(request));
			if (request.result.outcome() == CheckpointOutcome.RETRY_BEFORE_WRITE) { request.retryAfter = System.nanoTime() + 500_000_000L; }
			return request.result;
		}
		finally { request.executing.set(false); }
	}

	private void enqueueVisibleCheckpoint(VisibleCheckpoint request)
	{
		if (!request.queued.compareAndSet(false, true)) { return; }
		try
		{
			org.l2jmobius.commons.threads.ThreadPool.executeOrThrow(() ->
			{
				request.queued.set(false);
				if (_visibleCheckpoints.get(request.key.profileId()) == request) { runVisibleCheckpoint(request); }
			});
		}
		catch (RuntimeException failure)
		{
			request.queued.set(false);
			request.scope.recordCheckpointFailure(failure);
			request.result = new CheckpointResult(CheckpointOutcome.TERMINAL_RETAIN, "WAIT_EARNED", "owned_checkpoint.control_rejected:" + failure.getClass().getName());
		}
	}

	private boolean publishVisibleCheckpoint(VisibleCheckpoint request)
	{
		if (!visibleCheckpointOwnerCurrent(request) || request.player.hasPendingOwnedStore()) { return false; }
		final var stored = transaction(() -> _transactions.load(request.key.profileId()));
		if (!stored.successful() || stored.state() == null || stored.state().state() != State.MATERIALIZED) { request.scope.retainOwnedCheckpoint(request.key, "owned_checkpoint.finalized_state_not_exact:" + stored.status()); return false; }
		final var context = transaction(() -> _transactions.nativeContext(request.key.profileId(), request.key.objectId()));
		final var witness = request.runtimeWitness;
		if (context.status() != PhantomBackgroundTransaction.Status.SUCCESS || context.context() == null || context.context().phase() != PhantomNativeContext.Phase.COMPLETED
			|| !stored.state().equals(context.state()) || !context.matchesNativeLoad(request.player.getVitalityPoints())
			|| witness == null || !_authority.matchesRuntime(request.player, witness) || !ownedProjectionMatches(witness, stored.state())
			|| !captureOwnedInventory(request.player, stored.state()).inventory().equals(stored.state().inventory()))
		{
			request.scope.retainOwnedCheckpoint(request.key, "owned_checkpoint.finalized_runtime_not_exact");
			return false;
		}
		checkpointStage(request.player, "INDEX_PENDING");
		_committedPosition.accept(request.key.profileId(), stored.state().position());
		checkpointStage(request.player, "COMPLETED");
		return true;
	}

	/** Same MEDIUMINT projection as the existing transaction; raw witness remains separate and unchanged. */
	private static boolean ownedProjectionMatches(PhantomBackgroundState raw, PhantomBackgroundState durable)
	{
		final var v = raw.vitals();
		final var canonical = new PhantomBackgroundState.Vitals(Math.min(v.maximumHp(), Math.round(v.currentHp())), v.maximumHp(), Math.min(v.maximumMp(), Math.round(v.currentMp())), v.maximumMp(), Math.min(v.maximumCp(), Math.round(v.currentCp())), v.maximumCp());
		return raw.identity().equals(durable.identity()) && raw.progress().equals(durable.progress()) && raw.position().equals(durable.position()) && canonical.equals(durable.vitals());
	}

	private static void checkpointStage(Player player, String stage)
	{
		if (player.getNativeWorkOwner() instanceof org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope scope) { scope.checkpointStage(stage); }
	}

	private boolean captureVisibleArrivalQuiescent(long profileId, Player player, PhantomGoal goal, String anchorId)
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
			final PhantomBackgroundState captured = _authority.captureOwnedNative(profileId, player, goal, hint).state();
			final var checkpoint = _visibleCheckpoints.get(profileId);
			if (checkpoint != null && checkpoint.player == player && checkpoint.goal.equals(goal)) { checkpoint.runtimeWitness = captured; }
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_CAPTURE);
			_arrivalCaptures.put(profileId, captured);
			checkpointStage(player, "INVENTORY_FLUSH");
			player.storeMe();
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_STORE);
			final var stored = transaction(() -> _transactions.load(profileId));
			if (!stored.successful() || (stored.state() == null) || (stored.state().state() != State.MATERIALIZED))
			{
				return false;
			}
			_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.ARRIVAL_AFTER_BASELINE);
			checkpointStage(player, "INDEX_PENDING");
			_committedPosition.accept(profileId, stored.state().position());
			checkpointStage(player, "COMPLETED");
			return true;
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
			private PhantomNativeContext.Capture _nativeCapture;
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
				if (player.isNativeWorkManaged() && ((player.getNativeWorkOwner() == null) || !player.getNativeWorkOwner().sealed())) { throw new IllegalStateException("OWNED_STORE_NATIVE_NOT_SEALED"); }
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
						if ((!_recaptureAfterResume && (!ownedProgressMatches(player, _intent.after()) || ((_nativeCapture != null) && (player.getVitalityPoints() != _nativeCapture.vitalityPoints())))) || !captureOwnedInventory(player, _intent.after()).inventory().equals(_intent.after().inventory())) { throw new IllegalStateException("OWNED_STORE_RETRY_RUNTIME_CHANGED"); }
						_before = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(profileId, _intent.materializedAtNanos()) ? org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.snapshot(player) : null;
						_sequence = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.begin(profileId, player, _kind, _intent);
						checkpointStage(player, "NATIVE_ATTEMPTED");
						return ownedSnapshot(player, _intent.after(), _nativeCapture == null ? -1 : _nativeCapture.vitalityPoints());
					}
					if (!resumed.successful()) { throw new IllegalStateException("OWNED_STORE_RESUME:" + resumed.status()); }
					checkpointStage(player, "FINALIZED");
					_intent = null;
					_nativeCapture = null;
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
				checkpointStage(player, "INVENTORY_FLUSH");
				player.getInventory().updateDatabase();
				final PhantomBackgroundState captured;
				final PhantomNativeContext.Capture nativeCapture;
				synchronized (player.getStatus())
				{
					if (arrival != null)
					{
						if (!_authority.matchesRuntime(player, arrival)) { throw new IllegalStateException("OWNED_STORE_CAPTURE_STALE"); }
						captured = arrival;
						nativeCapture = _authority.captureNativeContext(player, captured);
					}
					else
					{
						final var capture = PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType())
							? _authority.captureOwnedNativeAcquisition(profileId, player, goal, previous, PhantomAcquisitionGoalSpec.parse(goal).itemId())
							: _authority.captureOwnedNative(profileId, player, goal, previous);
						captured = capture.state(); nativeCapture = capture.context();
					}
				}
				final State target = cleanup || (previous == null) || (previous.state() != State.MATERIALIZED) ? (captured.vitals().currentHp() == 0 ? State.DEAD : State.READY) : State.MATERIALIZED;
				final var witnessed = captureOwnedInventory(player, captured);
				checkpointStage(player, "PREPARE_ATTEMPTED");
				final var prepared = transaction(() -> _transactions.prepareOwnedStore(witnessed, goal, entry.materializedAtNanos(), target, nativeCapture));
				_intentGoal = goal;
				_intent = prepared.intent();
				_nativeCapture = _intent == null ? null : nativeCapture;
				_kind = arrival != null ? "ARRIVAL_CAPTURE" : cleanup ? "CLEANUP_STORE" : "OTHER_OWNED_STORE";
				if (!prepared.successful()) { throw new IllegalStateException("OWNED_STORE_PREPARE:" + prepared.status()); }
				checkpointStage(player, "PREPARED");
				_before = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.enabledFor(profileId, _intent.materializedAtNanos()) ? org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.snapshot(player) : null;
				_sequence = org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.begin(profileId, player, _kind, _intent);
				_transactions.lifecycleCheckpoint(PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_PREPARE);
				checkpointStage(player, "NATIVE_ATTEMPTED");
				return ownedSnapshot(player, _intent.after(), _nativeCapture.vitalityPoints());
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
					checkpointStage(player, "FINALIZED");
					if ((!recaptureCleanup && (!ownedProgressMatches(player, _intent.after()) || ((_nativeCapture != null) && (player.getVitalityPoints() != _nativeCapture.vitalityPoints())))) || !captureOwnedInventory(player, _intent.after()).inventory().equals(_intent.after().inventory())) { status = "RUNTIME_CHANGED_AFTER_FINALIZE"; throw new IllegalStateException("OWNED_STORE_RUNTIME_CHANGED_AFTER_FINALIZE"); }
				}
				finally
				{
					org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal.end(profileId, player, _kind, _intent, _sequence, _before, after, status, completedState);
					if (finalizedSuccessfully) { _intent = null; _nativeCapture = null; }
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

	private static Player.OwnedStoreSnapshot ownedSnapshot(Player player, PhantomBackgroundState state, int vitalityPoints)
	{
		final var v = state.vitals(); final var p = state.position(); final var progress = state.progress(); final var identity = state.identity();
		return new Player.OwnedStoreSnapshot(v.currentHp(), (int) v.maximumHp(), v.currentMp(), (int) v.maximumMp(), v.currentCp(), (int) v.maximumCp(), p.x(), p.y(), p.z(), p.heading(), progress.level(), progress.experience(), progress.skillPoints(), progress.experienceBeforeDeath(), identity.activeClassId(), identity.raceOrdinal(), identity.classIndex(), identity.classIndex() == 0 ? progress.level() : player.getStat().getBaseLevel(), identity.classIndex() == 0 ? progress.experience() : player.getStat().getBaseExp(), identity.classIndex() == 0 ? progress.skillPoints() : player.getStat().getBaseSp(), vitalityPoints);
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
			final var nativeGate = ordinaryContextGate(claim, PhantomBackgroundSimulationPolicy.Operation.TRAVEL);
			if (nativeGate != null) { return nativeGate; }
			final TravelAdvance advance = _authority.advanceTravel(state, claim.spec(), FARM_TRAVEL_BUDGET_MILLIS, logicalEpochMinute, claim._nativeContext.afterPolicy());
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
			return commit(claim, travelCommand(claim, command, FARM_TRAVEL_BUDGET_MILLIS, logicalEpochMinute));
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
		if ((PlayerNativeWork.inheritedPlayer() != null) || Thread.currentThread().getName().startsWith("L2jMobius "))
		{
			return requestRecovery(profileId, goal, activityState, cancelled);
		}
		return recoverClaimed(profileId, goal, activityState, cancelled, false);
	}

	/** A scheduler/native caller publishes control, never waits for its accepted callback. */
	public OperationResult requestRecovery(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled)
	{
		return requestRecovery(profileId, goal, activityState, cancelled, false);
	}

	private OperationResult requestRecovery(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled, boolean acceptedForStop)
	{
		RecoveryControl control;
		synchronized (this)
		{
			control = _recoveryControls.get(profileId);
			if (control != null)
			{
				if (control.result == null) { return retry("recovery.control_pending"); }
				_recoveryControls.remove(profileId, control);
				if (control.goal.equals(goal)) { return control.result; }
			}
			if (((_state != ServiceState.RUNNING) && !(acceptedForStop && (_state == ServiceState.STOPPING))) || (_recoveries.putIfAbsent(profileId, Boolean.TRUE) != null)) { return retry("recovery.busy_or_stopping"); }
			final var materialization = _materialization.get();
			control = new RecoveryControl(profileId, goal, activityState, cancelled, materialization == null ? null : materialization.find(profileId).orElse(null));
			_recoveryControls.put(profileId, control); increment(_currentOperations, _peakOperations);
		}
		queueRecovery(control);
		return retry("recovery.control_pending");
	}

	private void queueRecovery(RecoveryControl control)
	{
		if (!control.queued.compareAndSet(false, true) || control.finished.get()) { return; }
		final var future = org.l2jmobius.commons.threads.ThreadPool.schedule(() ->
		{
			try { org.l2jmobius.commons.threads.ThreadPool.executeOrThrow(() -> advanceRecovery(control)); }
			catch (RuntimeException failure) { finishRecovery(control, OperationResult.inconsistent("recovery.control_submission:" + failure.getClass().getName() + ":" + failure.getMessage())); }
		}, 10);
		if (future == null) { finishRecovery(control, OperationResult.inconsistent("recovery.control_scheduler_rejected")); }
	}

	private void advanceRecovery(RecoveryControl control)
	{
		try
		{
			if (_recoveryControls.get(control.profileId) != control) { finishRecovery(control, OperationResult.inconsistent("recovery.control_replaced")); return; }
			if (System.nanoTime() >= control.deadline) { finishRecovery(control, OperationResult.inconsistent("recovery.control_deadline:object=" + control.objectId + ":epoch=" + control.epoch)); return; }
			final var materialization = _materialization.get();
			final var entry = materialization == null ? null : materialization.find(control.profileId).orElse(null);
			if ((entry != null) && (control.epoch != 0) && ((entry.characterObjectId() != control.objectId) || (entry.materializedAtNanos() != control.epoch)))
			{ finishRecovery(control, OperationResult.inconsistent("recovery.control_owner_changed")); return; }
			final OperationResult result;
			if (control.storeRequested)
			{
				final var stored = materialization.requestDematerialize(control.profileId, control.deadline);
				result = stored.status() == ResultStatus.CLEANUP_PENDING ? retry("recovery.cleanup_pending")
					: ((stored.status() == ResultStatus.SUCCESS) || (stored.status() == ResultStatus.NOT_ACTIVE))
						? finishRecoveryStore(control.profileId, control.objectId, control.restoreExisting, control.resumedFromResurrection, _state != ServiceState.RUNNING, control.handoff, control.epoch)
						: OperationResult.inconsistent("recovery.store_" + stored.status());
			}
			else
			{
				final var goal = _goals.load(control.profileId).orElse(null);
				if (control.cancelled.getAsBoolean() || (goal == null) || !goal.goal().equals(control.goal)) { finishRecovery(control, OperationResult.replan("recovery.control_goal_changed")); return; }
				if (_nativeRecoveryHistory != null)
				{
					final var prepared = _nativeRecoveryHistory.prepareNativeRecoveryControl(control.profileId, control.goal, control.deadline);
					control.baselinePending = _nativeRecoveryHistory.nativeRecoveryControlPending(control.profileId);
					if (!prepared.successful())
					{
						if (!control.baselinePending) { finishRecovery(control, prepared.status() == PhantomHistoricalBackgroundService.ResultStatusCode.RETRY ? retry("recovery.control_baseline:" + prepared.reason()) : OperationResult.replan("recovery.control_baseline:" + prepared.reason())); }
						return;
					}
				}
				result = recoverOwned(control.profileId, control.goal, control.activity, control.cancelled, _state != ServiceState.RUNNING, control);
			}
			if ((result.status() != OperationStatus.RETRY) || !control.storeRequested) { finishRecovery(control, result); }
		}
		catch (RuntimeException failure) { finishRecovery(control, OperationResult.inconsistent("recovery.control_failure:" + failure.getClass().getName() + ":" + failure.getMessage())); }
		finally { control.queued.set(false); if (!control.finished.get()) { queueRecovery(control); } }
	}

	private void finishRecovery(RecoveryControl control, OperationResult result)
	{
		if (control.finished.compareAndSet(false, true))
		{
			if (result.status() == OperationStatus.INCONSISTENT) { _firstRecoveryFailure.compareAndSet("", "profile=" + control.profileId + ":object=" + control.objectId + ":epoch=" + control.epoch + ":" + result); }
			control.result = result; _recoveries.remove(control.profileId); _currentOperations.decrementAndGet();
		}
	}

	public String firstRecoveryFailure() { return _firstRecoveryFailure.get(); }

	/** The already returned exact corpse may finish persistence after producer closure. */
	public OperationResult recoverAcceptedForStop(long profileId, PhantomGoal goal, int objectId, long epoch)
	{
		final var control = _recoveryControls.get(profileId);
		if ((control != null) && (control.objectId == objectId) && (control.epoch == epoch) && control.goal.equals(goal))
		{
			return requestRecovery(profileId, goal, PhantomActivityState.ACTIVE, () -> false, true);
		}
		final var materialization = _materialization.get();
		final var entry = materialization == null ? null : materialization.find(profileId).orElse(null);
		if ((entry == null) || (entry.characterObjectId() != objectId) || (entry.materializedAtNanos() != epoch)
			|| !Objects.equals(_nativeTownReturns.get(profileId), objectId)) { return retry("recovery.stop_owner_changed"); }
		return requestRecovery(profileId, goal, PhantomActivityState.ACTIVE, () -> false, true);
	}

	private OperationResult recoverClaimed(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled, boolean stopping)
	{
		synchronized (this)
		{
			if (((_state != ServiceState.RUNNING) && !(stopping && (_state == ServiceState.STOPPING))) || (_recoveries.putIfAbsent(profileId, Boolean.TRUE) != null))
			{
				return retry("recovery.busy_or_stopping");
			}
			increment(_currentOperations, _peakOperations);
		}
		try
		{
			return recoverOwned(profileId, goal, activityState, cancelled, stopping, null);
		}
		finally
		{
			_recoveries.remove(profileId);
			_currentOperations.decrementAndGet();
		}
	}

	private OperationResult recoverOwned(long profileId, PhantomGoal goal, PhantomActivityState activityState, BooleanSupplier cancelled, boolean stopping, RecoveryControl control)
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
		final long originalEpoch = existingMaterialization.map(value -> value.materializedAtNanos()).orElse(0L);
		final var handoff = restoreExistingMaterialization && (_nativeRecoveryHistory != null)
			? _nativeRecoveryHistory.captureNativeRecoveryHandoff(profileId, goal, loaded.state().identity().characterObjectId(), originalEpoch) : null;
		if (!restoreExistingMaterialization)
		{
			if (stopping && (control == null)) { return retry("recovery.stop_no_new_materialization"); }
			final PhantomMaterializationService.MaterializeResult materialized = _nativeRecoveryHistory == null ? materialization.materialize(profileId)
				: _nativeRecoveryHistory.materializeColdNativeRecovery(profileId, goal);
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
				if (stopping && (control == null)) { return retry("recovery.stop_no_new_return"); }
				final OperationResult nativeRecovery = recoverNativeTown(player, cancelled);
				if (!nativeRecovery.successful())
				{
					return nativeRecovery;
				}
			}
		}
		if (control != null)
		{
			final var owned = materialization.find(profileId).orElseThrow();
			control.objectId = owned.characterObjectId(); control.epoch = owned.materializedAtNanos();
			control.storeRequested = true; control.restoreExisting = restoreExistingMaterialization; control.resumedFromResurrection = resumedFromResurrection;
			control.handoff = handoff;
		}
		final PhantomMaterializationService.DematerializeResult dematerialized = control == null ? materialization.dematerialize(profileId) : materialization.requestDematerialize(profileId, control.deadline);
		if ((control != null) && (dematerialized.status() == ResultStatus.CLEANUP_PENDING)) { return retry("recovery.cleanup_pending"); }
		if (dematerialized.status() != ResultStatus.SUCCESS)
		{
			return OperationResult.inconsistent("recovery.store_failed");
		}
		return finishRecoveryStore(profileId, loaded.state().identity().characterObjectId(), restoreExistingMaterialization, resumedFromResurrection, stopping, handoff, originalEpoch);
	}

	private OperationResult finishRecoveryStore(long profileId, int characterObjectId, boolean restoreExistingMaterialization, boolean resumedFromResurrection, boolean stopping, PhantomHistoricalBackgroundService.NativeRecoveryHandoff handoff, long originalEpoch)
	{
		final var materialization = _materialization.get();
		final PhantomBackgroundTransaction.Result verified = transaction(() -> _transactions.reconcileVerifyPending(profileId, characterObjectId));
		if (!verified.successful() || (verified.state() == null) || (verified.state().state() != State.READY))
		{
			return OperationResult.inconsistent("recovery.verification_failed");
		}
		if (restoreExistingMaterialization && !stopping && (_state == ServiceState.RUNNING))
		{
			final PhantomMaterializationService.MaterializeResult restored = handoff == null ? materialization.materialize(profileId)
				: _nativeRecoveryHistory.resumeNativeRecoveryHandoff(handoff, characterObjectId, originalEpoch);
			if ((restored.status() != ResultStatus.SUCCESS) && (restored.status() != ResultStatus.ALREADY_ACTIVE))
			{
				return OperationResult.inconsistent("recovery.rematerialization_" + restored.status().name().toLowerCase());
			}
		}
		final boolean nativeTownReturn = _nativeTownReturns.remove(profileId, characterObjectId);
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
		// Existing headless completion is synchronous; never await its callback under Player.
		if (player.isTeleporting()) { return retry("recovery.teleport_pending"); }
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
		beforeMaterialize(profileId, characterObjectId, MaterializationPurpose.NORMAL, "");
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId, MaterializationPurpose purpose, String ownerClaim)
	{
		Objects.requireNonNull(purpose, "purpose");
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
			if (purpose != MaterializationPurpose.NORMAL)
			{
				final var component = _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
				if ((component == null) || (component.profileId() != profileId) || (component.componentSchemaVersion() != PhantomBackgroundCatchupState.SCHEMA_VERSION) || (ownerClaim == null) || ownerClaim.isBlank()) { throw new AdmissionRejectedException("background.historical_claim_invalid"); }
				final var catchup = new PhantomBackgroundCatchupStateCodec().decode(component.payload());
				if (!catchup.owns(ownerClaim) || (catchup.modelVersion() != PhantomBackgroundState.MODEL_VERSION) || ((purpose == MaterializationPurpose.NATIVE_CONTEXT_HANDOFF) && (catchup.status() == PhantomBackgroundCatchupState.Status.COMPLETE))) { throw new AdmissionRejectedException("background.historical_claim_invalid"); }
				final var admission = new HistoricalAdmission(characterObjectId, ownerClaim, component.rowVersion(), PhantomBackgroundTransaction.payloadDigest(component.payload()), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null));
				if (_historicalAdmissions.putIfAbsent(profileId, admission) != null) { throw new AdmissionRejectedException("background.historical_claim_busy"); }
			}
			final PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
			if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
			{
				if ((purpose == MaterializationPurpose.NATIVE_CONTEXT_HANDOFF) && !currentHistoricalComponents(profileId, _historicalAdmissions.get(profileId))) { throw new AdmissionRejectedException("background.historical_claim_or_goal_changed"); }
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
			if ((purpose != MaterializationPurpose.NORMAL) && !currentHistoricalComponents(profileId, _historicalAdmissions.get(profileId))) { throw new AdmissionRejectedException("background.historical_claim_or_goal_changed"); }
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
		final var historicalAdmission = _historicalAdmissions.get(profileId);
		if ((historicalAdmission != null) && !currentHistoricalAdmission(profileId, player, player.getNativeWorkOwner(), historicalAdmission)) { throw new AdmissionRejectedException("background.historical_native_claim_or_epoch_changed"); }
		PhantomBackgroundTransaction.Result loaded = transaction(() -> _transactions.load(profileId));
		if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
		{
			return;
		}
		final var nativeContext = transaction(() -> _transactions.nativeContext(profileId, player.getObjectId()));
		if (!nativeContext.matchesNativeLoad(player.getVitalityPoints())) { throw new IllegalStateException("Loaded Player differs from canonical native context: " + nativeContext.status()); }
		restoreCommittedNativeElevation(profileId, player, loaded.state(), nativeContext.state());
		// The original unplanned/goal-less native baseline first creates its plan, then attests in cleanup STORE.
		final boolean attestHistoricalContext = (historicalAdmission != null) && (historicalAdmission.goal() != null) && (nativeContext.context() != null) && (nativeContext.context().phase() == PhantomNativeContext.Phase.UNKNOWN);
		if ((historicalAdmission != null) && !Objects.equals(loaded.state(), nativeContext.state())) { throw new IllegalStateException("Historical background state changed before native attestation."); }
		final StringBuilder refreshReason = new StringBuilder();
		if (loaded.successful() && (loaded.state() != null) && (!_authority.matchesRuntime(player, loaded.state()) || attestHistoricalContext))
		{
			loaded = refreshNativeVitals(profileId, player, loaded, refreshReason);
		}
		if (!loaded.successful() || (loaded.state() == null) || !_authority.matchesRuntime(player, loaded.state()))
		{
			throw new IllegalStateException("Loaded Player differs from committed background state. " + runtimeMismatch(player, loaded.state()) + ";refresh=" + refreshReason + ";status=" + loaded.status() + ";nonAtomic");
		}
		if (attestHistoricalContext)
		{
			final var attested = transaction(() -> _transactions.nativeContext(profileId, player.getObjectId()));
			if ((attested.status() != PhantomBackgroundTransaction.Status.SUCCESS) || (attested.context() == null) || (attested.context().phase() != PhantomNativeContext.Phase.COMPLETED)
				|| !loaded.state().equals(attested.state()) || !attested.matchesNativeLoad(player.getVitalityPoints()) || !currentHistoricalAdmission(profileId, player, player.getNativeWorkOwner(), historicalAdmission))
			{
				throw new IllegalStateException("Historical native context attestation did not complete under the exact claim: " + attested.status() + ";refresh=" + refreshReason);
			}
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
		if ((historicalAdmission != null) && !currentHistoricalAdmission(profileId, player, player.getNativeWorkOwner(), historicalAdmission)) { throw new AdmissionRejectedException("background.historical_native_claim_or_epoch_changed"); }
	}

	/** Stock Player.load grounds Z; restore only that known pre-World transform of verified native SQL. */
	private void restoreCommittedNativeElevation(long profileId, Player player, PhantomBackgroundState state, PhantomBackgroundState canonical)
	{
		if ((state == null) || !state.equals(canonical) || ((state.state() != State.READY) && (state.state() != State.DEAD))) { return; }
		final var position = state.position();
		if (player.getZ() == position.z()) { return; }
		PlayerNativeWork.checkpoint(player, () ->
		{
			synchronized (player)
			{
				final var owner = player.getNativeWorkOwner();
				final var entry = _materialization.get().find(profileId).orElse(null);
				if ((_transitions.get(profileId) != TransitionKind.MATERIALIZING) || (owner == null) || !owner.isCurrent() || (owner.player() != player)
					|| (entry == null) || entry.worldPresent() || entry.actionAdmissionOpen() || (entry.characterObjectId() != player.getObjectId()) || (entry.materializedAtNanos() != owner.epoch())
					|| (World.getInstance().findObject(player.getObjectId()) != null) || (World.getInstance().getPlayer(player.getObjectId()) != null)
					|| (player.getObjectId() != state.identity().characterObjectId()) || (player.getClassIndex() != state.identity().classIndex()) || (player.getActiveClass() != state.identity().activeClassId()) || (player.getRace().ordinal() != state.identity().raceOrdinal())
					|| (player.getLevel() != state.progress().level()) || (player.getExp() != state.progress().experience()) || (player.getSp() != state.progress().skillPoints()) || (player.getExpBeforeDeath() != state.progress().experienceBeforeDeath())
					|| (player.getInstanceId() != position.instanceId()) || (player.getX() != position.x()) || (player.getY() != position.y()) || (player.getHeading() != position.heading())
					|| (player.getZ() != GeoEngine.getInstance().getHeight(position.x(), position.y(), position.z()))) { return null; }
				player.setXYZInvisible(position.x(), position.y(), position.z());
				player.setLastServerPosition(position.x(), position.y(), position.z());
				return null;
			}
		});
	}

	private static String runtimeMismatch(Player player, PhantomBackgroundState state)
	{
		if (state == null) { return "ABSENT"; }
		final StringBuilder result = new StringBuilder();
		final String[] labels = { "object", "classIndex", "class", "race", "level", "exp", "sp", "expBeforeDeath", "instance", "x", "y", "z", "heading" };
		final long[] runtime = { player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal(), player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading() };
		final long[] committed = { state.identity().characterObjectId(), state.identity().classIndex(), state.identity().activeClassId(), state.identity().raceOrdinal(), state.progress().level(), state.progress().experience(), state.progress().skillPoints(), state.progress().experienceBeforeDeath(), state.position().instanceId(), state.position().x(), state.position().y(), state.position().z(), state.position().heading() };
		for (int index = 0; index < labels.length; index++) { if (runtime[index] != committed[index]) { result.append(labels[index]).append('=').append(runtime[index]).append('/').append(committed[index]).append(';'); } }
		final String[] vitalLabels = { "hp", "maxHp", "mp", "maxMp", "cp", "maxCp" };
		final double[] runtimeVitals = { player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp() };
		final double[] committedVitals = { state.vitals().currentHp(), state.vitals().maximumHp(), state.vitals().currentMp(), state.vitals().maximumMp(), state.vitals().currentCp(), state.vitals().maximumCp() };
		for (int index = 0; index < vitalLabels.length; index++) { if (Math.abs(runtimeVitals[index] - committedVitals[index]) > 0.000001d) { result.append(vitalLabels[index]).append('=').append(runtimeVitals[index]).append('/').append(committedVitals[index]).append(';'); } }
		return result.toString();
	}

	/** Native maxima are derived on Player load after background level changes; preserve durable facts. */
	private PhantomBackgroundTransaction.Result refreshNativeVitals(long profileId, Player player, PhantomBackgroundTransaction.Result loaded, StringBuilder reason)
	{
		return PlayerNativeWork.checkpoint(player, () ->
		{
			final var admission = _historicalAdmissions.get(profileId);
			final var owner = player.getNativeWorkOwner();
			final var hashes = _authority.hashes();
			final boolean historical = currentHistoricalAdmission(profileId, player, owner, admission);
			if ((admission != null) && !historical) { reason.append("HISTORICAL_ADMISSION_STALE"); return loaded; }
			final PhantomBackgroundState captured;
			// Match the native player's original player -> status order after exclusive admission/drain.
			synchronized (player)
			{
				synchronized (player.getStatus())
				{
					captured = refreshNativeVitalsLocked(profileId, player, loaded, reason, hashes, historical);
				}
			}
			if (captured == null) { return loaded; }
			if (!hashes.equals(_authority.hashes()) || (historical && !currentHistoricalAdmission(profileId, player, owner, admission))) { reason.append("REFRESH_AUTHORITY_OR_CLAIM_CHANGED"); return loaded; }
			if (_arrivalCaptures.putIfAbsent(profileId, captured) != null) { throw new IllegalStateException("NATIVE_VITALS_CAPTURE_ALREADY_OWNED"); }
			try
			{
				// Reuse this exact validated capture; native STORE runs after both monitors have been released.
				player.storeMe();
				return transaction(() -> _transactions.load(profileId));
			}
			finally { _arrivalCaptures.remove(profileId, captured); }
		});
	}

	private boolean currentHistoricalAdmission(long profileId, Player player, PlayerNativeWork.Owner owner, HistoricalAdmission admission)
	{
		if ((admission == null) || (_historicalAdmissions.get(profileId) != admission) || (_transitions.get(profileId) != TransitionKind.MATERIALIZING) || (admission.characterObjectId() != player.getObjectId()) || (owner == null) || (player.getNativeWorkOwner() != owner) || (owner.player() != player) || !owner.isCurrent()) { return false; }
		final var entry = _materialization.get().find(profileId).orElse(null);
		if ((entry == null) || (entry.characterObjectId() != player.getObjectId()) || (entry.materializedAtNanos() != owner.epoch())) { return false; }
		return currentHistoricalComponents(profileId, admission);
	}

	private boolean currentHistoricalComponents(long profileId, HistoricalAdmission admission)
	{
		if ((admission == null) || (_historicalAdmissions.get(profileId) != admission)) { return false; }
		final var component = _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
		if ((component == null) || (component.profileId() != profileId) || (component.componentSchemaVersion() != PhantomBackgroundCatchupState.SCHEMA_VERSION) || (component.rowVersion() != admission.rowVersion()) || !PhantomBackgroundTransaction.payloadDigest(component.payload()).equals(admission.payloadDigest())) { return false; }
		final var catchup = new PhantomBackgroundCatchupStateCodec().decode(component.payload());
		return !admission.requestId().isBlank() && catchup.owns(admission.requestId()) && (catchup.modelVersion() == PhantomBackgroundState.MODEL_VERSION)
			&& Objects.equals(admission.goal(), _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null));
	}

	private PhantomBackgroundState refreshNativeVitalsLocked(long profileId, Player player, PhantomBackgroundTransaction.Result loaded, StringBuilder reason, PhantomBackgroundState.Hashes hashes, boolean historical)
	{
		final PhantomBackgroundState state = loaded.state();
		final var progress = state.progress();
		if (((state.state() != State.DEAD) && (state.state() != State.READY)) || (!state.hashes().equals(hashes) && !historical)) { reason.append("state=").append(state.state()).append(",hashMatch=").append(state.hashes().equals(hashes)); return null; }
		if (state.vitals().currentCp() > player.getMaxCp()) { reason.append("CP_ABOVE_MAX"); return null; }
		if ((state.state() == State.READY) && ((state.vitals().currentHp() > player.getMaxHp()) || (state.vitals().currentMp() > player.getMaxMp()))) { reason.append("HP_MP_ABOVE_MAX"); return null; }
		final var vitals = new PhantomBackgroundState.Vitals(state.vitals().currentHp(), player.getMaxHp(), state.state() == State.DEAD ? Math.min(state.vitals().currentMp(), player.getMaxMp()) : state.vitals().currentMp(), player.getMaxMp(), state.vitals().currentCp(), player.getMaxCp());
		final var normalized = new PhantomBackgroundState(state.state(), state.identity(), progress, vitals, state.position(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
		if (!_authority.matchesRuntime(player, normalized)) { reason.append("OTHER_RUNTIME_FIELDS"); return null; }
		final var goal = _goals.load(profileId).orElse(null);
		if ((goal == null) || (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) && !(historical && PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(goal.goal().goalType()))) || (goal.goal().status() != PhantomGoalStatus.ACTIVE)) { reason.append("GOAL:").append(goal == null ? "ABSENT" : goal.goal().goalType() + "/" + goal.goal().status()); return null; }
		final var captured = _authority.captureOwnedNative(profileId, player, goal.goal(), normalized).state();
		if (!captured.vitals().equals(vitals)) { reason.append("CAPTURE_VITALS"); return null; }
		if (!captured.progress().equals(progress) || !captured.identity().equals(state.identity()) || !captured.position().equals(state.position()) || !captured.receipt().equals(state.receipt()) || !captured.clock().equals(state.clock()) || !captured.hashes().equals(hashes)) { reason.append("CAPTURE_DURABLE_FIELDS"); return null; }
		if (!captured.inventory().objects().equals(state.inventory().objects()) || !captured.autoGetSkills().equals(state.autoGetSkills())) { reason.append("CAPTURE_INVENTORY_OR_AUTOGET"); return null; }
		final var witnessed = captureOwnedInventory(player, captured);
		if (!witnessed.inventory().canonicalHash().equals(state.inventory().canonicalHash())) { reason.append("CAPTURE_FULL_INVENTORY_HASH"); return null; }
		return witnessed;
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
		_visibleCheckpoints.remove(profileId);
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
			final var capture = _authority.captureOwnedNativeAcquisition(profileId, player, goal, previous, acquisition.itemId());
			final PhantomBackgroundTransaction.Result stored = transaction(() -> _transactions.captureBaseline(capture.state(), goal, capture.context()));
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
		final var capture = _authority.captureOwnedNative(profileId, player, goal, previous);
		final PhantomBackgroundTransaction.Result stored = transaction(() -> goal.status() == PhantomGoalStatus.ACTIVE ? _transactions.captureBaseline(capture.state(), goal, capture.context()) : _transactions.captureLifecycleBaseline(capture.state(), goal, capture.context()));
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
			if (exactOperationVerified(command, retryVerification))
			{
				result = new PhantomBackgroundTransaction.Result(PhantomBackgroundTransaction.Status.IDEMPOTENT, retryVerification.state());
			}
			else if (retryVerification.successful() && command.expectedState().equals(retryVerification.state()))
			{
				return retry("transaction.outcome_uncommitted");
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
			if (!exactOperationVerified(command, verified))
			{
				claim.retainIdentity();
				failStop();
				_failedOperations.incrementAndGet();
				return OperationResult.inconsistent("transaction.idempotent_unverified");
			}
			observeCommittedOperation(command, verified);
			publishPosition(claim.profileId(), verified);
			_idempotentOperations.incrementAndGet();
			return OperationResult.idempotent("transaction.idempotent");
		}
		if (result.status() == PhantomBackgroundTransaction.Status.SUCCESS)
		{
			observeCommittedOperation(command, result);
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

    private static void observeCommittedOperation(PhantomBackgroundTransaction.Command command, PhantomBackgroundTransaction.Result result)
    {
        final var observer = _commitObserver;
        if (observer != null && command.policy() != null && exactOperationVerified(command, result))
        {
            try { observer.accept(command, result); }
            catch (Throwable ignored) { /* Observation cannot alter an already verified durable operation. */ }
        }
    }

	private static boolean exactOperationVerified(PhantomBackgroundTransaction.Command command, PhantomBackgroundTransaction.Result result)
	{
		return result.successful() && (result.state() != null)
			&& result.state().identity().equals(command.expectedState().identity())
			&& result.state().receipt().operationKey().equals(command.operationKey().digest())
			&& !result.state().receipt().expectedAfterHash().isEmpty();
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
			case STALE_OPERATION, GOAL_STALE, HASH_STALE, STATE_CONFLICT, STATE_ABSENT, PROFILE_LINK_STALE, CATCHUP_CONFLICT, NATIVE_CONTEXT_REQUIRED -> OperationResult.replan("transaction." + status.name().toLowerCase());
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

	private synchronized void releaseTransition(long profileId, TransitionKind kind)
	{
		if (_transitions.remove(profileId, kind))
		{
			if (kind == TransitionKind.MATERIALIZING)
			{
				final var admission = _historicalAdmissions.get(profileId);
				if (admission != null) { _historicalAdmissions.remove(profileId, admission); }
			}
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
		INCONSISTENT,
		NATIVE_REQUIRED
	}

	    private OperationResult ordinaryContextGate(OperationClaim claim, PhantomBackgroundSimulationPolicy.Operation operation)
    {
        final var proof = transaction(() -> _transactions.nativeContext(claim.profileId(), claim.characterObjectId()));
        if (proof.status() != PhantomBackgroundTransaction.Status.SUCCESS && proof.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED) { return mapTransactionFailure(proof.status()); }
        if (!claim.state().equals(proof.state())) { return OperationResult.replan("native_context.state_changed"); }
        if (proof.context().phase() == PhantomNativeContext.Phase.PENDING) { return retry("native_context.pending"); }
        final var effective = operation != null ? operation : claim.state().position().committedAnchorId().equals(claim.spec().anchorId())
            && (proof.context().afterPolicy() == null || proof.context().afterPolicy().farmPosition())
            ? PhantomBackgroundSimulationPolicy.Operation.FARM : PhantomBackgroundSimulationPolicy.Operation.TRAVEL;
        final boolean required = !proof.context().permits(effective, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint());
        final var delivery = updateNativeContextSignal(claim.profileId(), proof, required);
        if (required) { return OperationResult.replan(nativeContextReason(delivery)); }
        claim._nativeContext = proof.context();
        return null;
    }
        private PhantomBackgroundTransaction.Command travelCommand(OperationClaim claim, PhantomBackgroundTransaction.Command command, long budgetMillis, long epochMinute)
    {
        if (claim._nativeContext.afterPolicy() == null) { return command; }
        final var policy = claim._nativeContext.afterPolicy().withPosition(_authority.canFarmAt(command.position(), claim.spec()), claim._nativeContext.afterPolicy().travelPosition());
        return command.withPolicy(new PhantomBackgroundTransaction.PolicyMutation(claim._nativeContext, PhantomBackgroundSimulationPolicy.Operation.TRAVEL, policy, claim._lease,
            new PhantomBackgroundTransaction.TravelProof(_authority, _authority.topologyGeneration(), budgetMillis, epochMinute)));
    }
    private static PhantomBackgroundTransaction.Command ordinaryCommand(OperationClaim claim, PhantomBackgroundTransaction.Command command, PhantomBackgroundSimulationPolicy proposed)
    {
        if (proposed == null) { return command; }
        return command.withPolicy(new PhantomBackgroundTransaction.PolicyMutation(claim._nativeContext, PhantomBackgroundSimulationPolicy.Operation.FARM, proposed, claim._lease));
    }
	private OperationResult nativeContextGate(long profileId, PhantomBackgroundState state)
	{
		final var proof = transaction(() -> _transactions.nativeContext(profileId, state.identity().characterObjectId()));
		if ((proof.status() != PhantomBackgroundTransaction.Status.SUCCESS) && (proof.status() != PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED)) { return mapTransactionFailure(proof.status()); }
		if (!state.equals(proof.state())) { return OperationResult.replan("native_context.state_changed"); }
		if ((state.state() == State.VERIFY_PENDING) || (proof.context().phase() == PhantomNativeContext.Phase.PENDING)) { return retry("native_context.pending"); }
		final boolean required = !proof.context().simulationEligible();
		final var delivery = updateNativeContextSignal(profileId, proof, required);
		return required ? OperationResult.replan(nativeContextReason(delivery)) : null;
	}

	private static String nativeContextReason(PhantomRelevanceSignalPort.SignalDelivery delivery)
	{
		return "native_context.required:" + (delivery == null ? "not_delivered" : delivery.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Claim only sequence/binding under a short monitor; deliver outside all native and DB monitors. */
	private PhantomRelevanceSignalPort.SignalDelivery updateNativeContextSignal(long profileId, PhantomBackgroundTransaction.NativeContextResult proof, boolean required)
	{
		// Evaluate existing native/locality facts outside service, native and DB monitors.
		final boolean requested = required && _nativeContextDemand.test(profileId);
		final boolean retiring = required && !requested;
		final long sequence;
		final long version = proof.context().stateRowVersion();
		synchronized (this)
		{
			if (_state != ServiceState.RUNNING) { return PhantomRelevanceSignalPort.SignalDelivery.NOT_RUNNING; }
			final var previous = _nativeContextSignals.get(profileId);
			if ((previous != null) && (previous.stateRowVersion() > version)) { return PhantomRelevanceSignalPort.SignalDelivery.STALE; }
			if (!requested && ((previous == null) || !previous.requested()))
			{
				if ((previous != null) && (previous.retiring() != retiring))
				{
					_nativeContextSignals.put(profileId, new NativeContextSignal(previous.sequence(), version, false, retiring, previous.delivery()));
				}
				return null;
			}
			if ((previous != null) && (previous.sequence() == Long.MAX_VALUE)) { return PhantomRelevanceSignalPort.SignalDelivery.SEQUENCE_EXHAUSTED; }
			sequence = Math.max(Math.max(1, System.nanoTime()), previous == null ? 1 : previous.sequence() + 1);
			_nativeContextSignals.put(profileId, new NativeContextSignal(sequence, version, requested, retiring, null));
			_currentContextSignals++;
		}
		try
		{
			final var delivery = requested
				? _signals.submit(profileId, new PhantomRelevanceSignal(NATIVE_CONTEXT_SIGNAL_SOURCE, sequence, PhantomActivityState.ACTIVE, NATIVE_CONTEXT_SIGNAL_TTL_MILLIS))
				: _signals.withdraw(profileId, NATIVE_CONTEXT_SIGNAL_SOURCE, sequence);
			_nativeContextSignals.computeIfPresent(profileId, (id, current) -> current.sequence() == sequence
				? new NativeContextSignal(sequence, version, requested || ((delivery != PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED) && (delivery != PhantomRelevanceSignalPort.SignalDelivery.COALESCED)), retiring, delivery) : current);
			return delivery;
		}
		finally { synchronized (this) { _currentContextSignals--; } }
	}

	/** Passive delivery fact for bounded native acceptance; it is not a liveness assertion. */
	public Optional<PhantomRelevanceSignalPort.SignalDelivery> nativeContextSignalDelivery(long profileId)
	{
		final var signal = _nativeContextSignals.get(profileId);
		return signal == null ? Optional.empty() : Optional.ofNullable(signal.delivery());
	}

	/** Existing signal binding keeps retire intent after the native entry is removed; no ownership is changed. */
	public boolean nativeContextRetiring(long profileId)
	{
		final var signal = _nativeContextSignals.get(profileId);
		return (signal != null) && signal.retiring();
	}

	private record NativeContextSignal(long sequence, long stateRowVersion, boolean requested, boolean retiring, PhantomRelevanceSignalPort.SignalDelivery delivery) {}

	public enum OperationStatus
	{
		SUCCESS,
		IDEMPOTENT,
		RETRY,
		REPLAN,
		INCONSISTENT,
		FAIL_GOAL
	}

	private record HistoricalAdmission(int characterObjectId, String requestId, long rowVersion, String payloadDigest, PhantomProfileComponent goal) { }

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
        private PhantomNativeContext _nativeContext;
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
