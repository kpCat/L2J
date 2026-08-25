/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveryObserver;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchDescriptor;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveIngressPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog.ProposalMapping;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.HandoffResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.HandoffStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationEvidence;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSession;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSubject;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveryPolicy;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.PendingClarification;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore.StoredState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.FragmentResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink.Status;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.ModifierSnapshot;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/**
 * Bounded, resumable and observer-only conversation planner. Every external
 * boundary is one shared-pulse operation and no index monitor is held across it.
 */
public final class PhantomConversationService implements DeliveryObserver, PhantomSchedulerControlPort
{
	public interface ContextPort
	{
		OptionalLong profileIdForObject(int characterObjectId);

		Optional<ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<SlotValue> previousSlots);
	}

	public record ContextSnapshot(long observerProfileId, String observerName, PhantomDomainRef speaker, PhantomDomainRef counterpart, long partyLeaderProfileId, InputContext input)
	{
		public ContextSnapshot
		{
			if ((observerProfileId <= 0) || (observerName == null) || observerName.isBlank() || (speaker == null) || (counterpart == null) || (partyLeaderProfileId < 0) || (input == null))
			{
				throw new IllegalArgumentException("Conversation context snapshot is invalid.");
			}
		}
	}

	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public enum BatchPhase
	{
		COLLECTING,
		RESOLVING_OBSERVERS,
		ELECTING,
		LOADING_STATE,
		BUILDING_CONTEXT,
		UNDERSTANDING,
		READING_SOCIAL,
		PERSISTING,
		PUBLISHING,
		DONE,
		FAILED
	}

	public enum PersistenceStatus
	{
		SAVED,
		DUPLICATE,
		FAILED,
		AUTHORITY_STALE,
		CAPACITY_REACHED
	}

	@FunctionalInterface
	public interface PhaseObserver
	{
		PhaseObserver NONE = (phase, dispatchId, indexMonitorHeld) ->
		{
		};

		void beforeOperation(BatchPhase phase, long dispatchId, boolean indexMonitorHeld);
	}

	public record Snapshot(ServiceState state, String catalogHash, int ingressSize, int openBatches, int dueBatches, int cacheEntries, int operationClaims, int persistenceClaims, boolean pulseOwned, long ingressAccepted, long ingressIgnored, long backpressure, long batchesProcessed, long plansPublished, long proposalsPlanned, long duplicates, long overflows, long unsupported, long authorityStale, long closedMismatches, long socialFailures, long planFailures, long failures, long indexTransitions, long maximumOperationsPerPulse)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(ServiceState.STOPPED, "none", 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private enum IngressKind
	{
		DELIVERED,
		CLOSED
	}

	private enum TerminalKind
	{
		DONE,
		DUPLICATE,
		OVERFLOW,
		UNSUPPORTED,
		AUTHORITY_STALE,
		FAILED
	}

	private static final int MAX_ATTEMPTS = 3;
	private static final int TERMINAL_TOMBSTONES = 512;
	private final PhantomConversationCatalog _catalog;
	private final PhantomConversationStore _store;
	private final ContextPort _context;
	private final PhantomSemanticUnderstandingService _semantic;
	private final PhantomSocialService _social;
	private final PhantomConversationPlanSink _plans;
	private final PhantomIdentityLeaseRegistry _identities;
	private final ChatObservationService _observation;
	private final PhantomClanDirectiveIngressPort _directiveIngress;
	private final PhaseObserver _phaseObserver;
	private final ArrayBlockingQueue<IngressEvent> _ingress;
	private final Object _lifecycle = new Object();
	private final Object _indexMonitor = new Object();
	private final Map<Long, BatchWork> _batches = new HashMap<>();
	private final PriorityQueue<DueEntry> _delayed = new PriorityQueue<>();
	private final ArrayDeque<Long> _due = new ArrayDeque<>();
	private final Set<Long> _dueMembership = new HashSet<>();
	private final Set<Long> _managedDispatches = new HashSet<>();
	private final LinkedHashMap<Long, TerminalKind> _terminal = new LinkedHashMap<>();
	private final Map<Long, StoredState> _cache;
	private final AtomicBoolean _pulseOwner = new AtomicBoolean();
	private final AtomicInteger _operationClaims = new AtomicInteger();
	private final AtomicInteger _persistenceClaims = new AtomicInteger();
	private final LongAdder _ingressAccepted = new LongAdder();
	private final LongAdder _ingressIgnored = new LongAdder();
	private final LongAdder _backpressure = new LongAdder();
	private final LongAdder _batchesProcessed = new LongAdder();
	private final LongAdder _plansPublished = new LongAdder();
	private final LongAdder _proposalsPlanned = new LongAdder();
	private final LongAdder _duplicates = new LongAdder();
	private final LongAdder _overflows = new LongAdder();
	private final LongAdder _unsupported = new LongAdder();
	private final LongAdder _authorityStale = new LongAdder();
	private final LongAdder _closedMismatches = new LongAdder();
	private final LongAdder _socialFailures = new LongAdder();
	private final LongAdder _planFailures = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private final LongAdder _indexTransitions = new LongAdder();
	private final LongAccumulator _maximumOperationsPerPulse = new LongAccumulator(Long::max, 0);
	private volatile ServiceState _state = ServiceState.NEW;
	private volatile AutoCloseable _registration;
	private volatile AuthorityGeneration _authority;
	private long _pulse;
	private long _dueSequence;

	public PhantomConversationService(PhantomConversationCatalog catalog, PhantomConversationStore store, ContextPort context, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomConversationPlanSink plans, PhantomIdentityLeaseRegistry identities, ChatObservationService observation)
	{
		this(catalog, store, context, semantic, social, plans, identities, observation, PhaseObserver.NONE, PhantomClanDirectiveIngressPort.noop());
	}

	public PhantomConversationService(PhantomConversationCatalog catalog, PhantomConversationStore store, ContextPort context, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomConversationPlanSink plans, PhantomIdentityLeaseRegistry identities, ChatObservationService observation, PhaseObserver phaseObserver)
	{
		this(catalog, store, context, semantic, social, plans, identities, observation, phaseObserver, PhantomClanDirectiveIngressPort.noop());
	}

	public PhantomConversationService(PhantomConversationCatalog catalog, PhantomConversationStore store, ContextPort context, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomConversationPlanSink plans, PhantomIdentityLeaseRegistry identities, ChatObservationService observation, PhantomClanDirectiveIngressPort directiveIngress)
	{
		this(catalog, store, context, semantic, social, plans, identities, observation, PhaseObserver.NONE, directiveIngress);
	}

	public PhantomConversationService(PhantomConversationCatalog catalog, PhantomConversationStore store, ContextPort context, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomConversationPlanSink plans, PhantomIdentityLeaseRegistry identities, ChatObservationService observation, PhaseObserver phaseObserver, PhantomClanDirectiveIngressPort directiveIngress)
	{
		_catalog = Objects.requireNonNull(catalog);
		_store = Objects.requireNonNull(store);
		_context = Objects.requireNonNull(context);
		_semantic = Objects.requireNonNull(semantic);
		_social = Objects.requireNonNull(social);
		_plans = Objects.requireNonNull(plans);
		_identities = Objects.requireNonNull(identities);
		_observation = Objects.requireNonNull(observation);
		_directiveIngress = Objects.requireNonNull(directiveIngress);
		_phaseObserver = Objects.requireNonNull(phaseObserver);
		_ingress = new ArrayBlockingQueue<>(catalog.limits().ingressQueue());
		_cache = new LinkedHashMap<>(Math.min(256, catalog.limits().cacheEntries()), 0.75f, true)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, StoredState> eldest)
			{
				return size() > _catalog.limits().cacheEntries();
			}
		};
	}

	public boolean start()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			final var semantic = _semantic.snapshot();
			final var social = _social.snapshot();
			if ((semantic.state() != PhantomSemanticUnderstandingService.State.RUNNING) || (social.state() != PhantomSocialService.ServiceState.RUNNING))
			{
				_state = ServiceState.FAILED;
				return false;
			}
			_authority = new AuthorityGeneration(semantic.packHash(), semantic.corpusHash(), semantic.knowledgeHash(), semantic.topologyHash(), semantic.partyRoleHash(), social.catalogHash());
			try
			{
				_registration = _observation.register(this);
			}
			catch (RuntimeException exception)
			{
				_state = ServiceState.FAILED;
				throw exception;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	@Override
	public boolean onDelivered(ChatObservationService.DeliveredObservation delivered)
	{
		final DispatchDescriptor dispatch = delivered.dispatch();
		if (dispatch.origin() == Origin.CLIENT_CHAT)
		{
			try
			{
				_directiveIngress.onDelivered(delivered);
			}
			catch (RuntimeException exception)
			{
				_failures.increment();
			}
		}
		if ((dispatch.origin() != Origin.CLIENT_CHAT) || !_catalog.supports(dispatch.chatType()) || (_identities.getOwnerKind(delivered.recipientObjectId()) != OwnerKind.PHANTOM))
		{
			_ingressIgnored.increment();
			return true;
		}
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			_ingressIgnored.increment();
			return true;
		}
		try (claim)
		{
			synchronized (_indexMonitor)
			{
				if (_terminal.containsKey(dispatch.dispatchId()))
				{
					_duplicates.increment();
					return true;
				}
				if (!_managedDispatches.contains(dispatch.dispatchId()) && (_managedDispatches.size() >= _catalog.limits().ingressQueue()))
				{
					completeWithoutBatchLocked(dispatch.dispatchId(), TerminalKind.OVERFLOW);
					return false;
				}
				_managedDispatches.add(dispatch.dispatchId());
			}
			final DeliveredObservation observation;
			try
			{
				observation = new DeliveredObservation(dispatch.dispatchId(), dispatch.origin(), dispatch.speakerObjectId(), dispatch.speakerName(), dispatch.chatType(), dispatch.whisperTarget(), dispatch.finalText(), dispatch.epochMillis(), delivered.recipientObjectId(), delivered.recipientName());
			}
			catch (RuntimeException exception)
			{
				_ingressIgnored.increment();
				return true;
			}
			if (!_ingress.offer(IngressEvent.delivered(observation)))
			{
				_backpressure.increment();
				forceOverflow(dispatch);
				return false;
			}
			_ingressAccepted.increment();
			return true;
		}
	}

	@Override
	public boolean onDispatchClosed(DispatchDescriptor dispatch)
	{
		if ((dispatch.origin() != Origin.CLIENT_CHAT) || !_catalog.supports(dispatch.chatType()))
		{
			_unsupported.increment();
			_ingressIgnored.increment();
			return true;
		}
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			_ingressIgnored.increment();
			return true;
		}
		try (claim)
		{
			synchronized (_indexMonitor)
			{
				if (!_managedDispatches.remove(dispatch.dispatchId()))
				{
					_ingressIgnored.increment();
					return true;
				}
			}
			if (!_ingress.offer(IngressEvent.closed(dispatch)))
			{
				_backpressure.increment();
				forceOverflow(dispatch);
				return false;
			}
			_ingressAccepted.increment();
			return true;
		}
	}

	@Override
	public void onPulse()
	{
		runPulse(_catalog.limits().operationsPerPulse());
	}

	private void runPulse(int maximumOperations)
	{
		if ((maximumOperations < 1) || (maximumOperations > _catalog.limits().operationsPerPulse()) || !_pulseOwner.compareAndSet(false, true))
		{
			return;
		}
		final OperationClaim claim = beginOperation();
		if (claim == null)
		{
			_pulseOwner.set(false);
			return;
		}
		final PulseBudget budget = new PulseBudget(maximumOperations);
		try (claim)
		{
			synchronized (_indexMonitor)
			{
				_pulse++;
			}
			while (budget.remaining() > 0)
			{
				boolean progressed = processOneDue(budget);
				if (budget.remaining() > 0)
				{
					progressed |= processOneIngress(budget);
				}
				if (budget.remaining() > 0)
				{
					progressed |= processOnePromotion(budget);
				}
				if (!progressed)
				{
					break;
				}
			}
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
		}
		finally
		{
			_maximumOperationsPerPulse.accumulate(budget.used());
			_pulseOwner.set(false);
		}
	}

	public void beginStop()
	{
		final AutoCloseable registration;
		synchronized (_lifecycle)
		{
			if (_state == ServiceState.NEW)
			{
				_state = ServiceState.STOPPED;
				return;
			}
			if (_state != ServiceState.RUNNING)
			{
				return;
			}
			_state = ServiceState.STOPPING;
			registration = _registration;
			_registration = null;
		}
		if (registration != null)
		{
			try
			{
				registration.close();
			}
			catch (Exception exception)
			{
				_failures.increment();
			}
		}
	}

	public boolean finishStop()
	{
		if (_state == ServiceState.RUNNING)
		{
			beginStop();
		}
		if (_pulseOwner.get() || (_operationClaims.get() != 0) || (_persistenceClaims.get() != 0))
		{
			return false;
		}
		synchronized (_lifecycle)
		{
			if (_pulseOwner.get() || (_operationClaims.get() != 0) || (_persistenceClaims.get() != 0))
			{
				return false;
			}
			synchronized (_indexMonitor)
			{
				_ingress.clear();
				_batches.clear();
				_delayed.clear();
				_due.clear();
				_dueMembership.clear();
				_managedDispatches.clear();
			}
			_state = ServiceState.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		final int batches;
		final int due;
		synchronized (_indexMonitor)
		{
			batches = _batches.size();
			due = _dueMembership.size();
		}
		final int cache;
		synchronized (_cache)
		{
			cache = _cache.size();
		}
		return new Snapshot(_state, _catalog.hash(), _ingress.size(), batches, due, cache, _operationClaims.get(), _persistenceClaims.get(), _pulseOwner.get(), _ingressAccepted.sum(), _ingressIgnored.sum(), _backpressure.sum(), _batchesProcessed.sum(), _plansPublished.sum(), _proposalsPlanned.sum(), _duplicates.sum(), _overflows.sum(), _unsupported.sum(), _authorityStale.sum(), _closedMismatches.sum(), _socialFailures.sum(), _planFailures.sum(), _failures.sum(), _indexTransitions.sum(), _maximumOperationsPerPulse.get());
	}

	private boolean processOneIngress(PulseBudget budget)
	{
		if (budget.remaining() == 0)
		{
			return false;
		}
		final IngressEvent event = _ingress.poll();
		if (event == null)
		{
			return false;
		}
		if (!budget.claim())
		{
			_ingress.offer(event);
			return false;
		}
		notifyPhase(BatchPhase.COLLECTING, event.dispatchId());
		synchronized (_indexMonitor)
		{
			applyIngressLocked(event);
			_indexTransitions.increment();
		}
		return true;
	}

	private void applyIngressLocked(IngressEvent event)
	{
		if (_terminal.containsKey(event.dispatchId()))
		{
			_ingressIgnored.increment();
			return;
		}
		if (event.kind() == IngressKind.DELIVERED)
		{
			BatchWork work = _batches.get(event.dispatchId());
			if (work == null)
			{
				if (_batches.size() >= _catalog.limits().openBatches())
				{
					completeWithoutBatchLocked(event.dispatchId(), TerminalKind.OVERFLOW);
					return;
				}
				work = new BatchWork(event.observation(), _pulse);
				_batches.put(event.dispatchId(), work);
			}
			work.add(event.observation(), _catalog.limits().observersPerMessage());
			return;
		}
		final BatchWork work = _batches.get(event.dispatchId());
		if (work == null)
		{
			_closedMismatches.increment();
			completeWithoutBatchLocked(event.dispatchId(), TerminalKind.FAILED);
			return;
		}
		if (work._closed)
		{
			_closedMismatches.increment();
			return;
		}
		work._closed = true;
		scheduleDelayedLocked(work, _pulse + _catalog.limits().aggregationPulses());
	}

	private boolean processOneDue(PulseBudget budget)
	{
		final WorkToken token;
		synchronized (_indexMonitor)
		{
			token = claimDueLocked();
		}
		if (token == null)
		{
			return false;
		}
		if (!budget.claim())
		{
			releaseUnchanged(token);
			return false;
		}
		notifyPhase(token.phase(), token.work()._dispatchId);
		final StepResult result = execute(token);
		synchronized (_indexMonitor)
		{
			applyStepLocked(token, result);
			_indexTransitions.increment();
		}
		return true;
	}

	private WorkToken claimDueLocked()
	{
		while (!_due.isEmpty())
		{
			final long dispatchId = _due.removeFirst();
			_dueMembership.remove(dispatchId);
			final BatchWork work = _batches.get(dispatchId);
			if ((work == null) || work._claimed)
			{
				continue;
			}
			if (work._phase == BatchPhase.COLLECTING)
			{
				if (!work._closed)
				{
					continue;
				}
				if (work._overflow)
				{
					completeLocked(work, TerminalKind.OVERFLOW);
					continue;
				}
				work.freezeObservers();
				work._phase = BatchPhase.RESOLVING_OBSERVERS;
			}
			if ((work._phase == BatchPhase.RESOLVING_OBSERVERS) && (work._observerCursor >= work._observers.size()))
			{
				work._phase = BatchPhase.ELECTING;
			}
			work._claimed = true;
			work._generation++;
			return new WorkToken(work, work._generation, work._phase, work._observerCursor, work._semanticStep, work._socialCursor, work._conflictReload);
		}
		return null;
	}

	private StepResult execute(WorkToken token)
	{
		final BatchWork work = token.work();
		try
		{
			return switch (token.phase())
			{
				case RESOLVING_OBSERVERS -> StepResult.value(resolveManagedProfile(work._observers.get(token.cursor()).recipientObjectId()));
				case ELECTING -> StepResult.value(elect(work._descriptor, work._resolved));
				case LOADING_STATE -> StepResult.value(token.conflictReload() ? exact(work._electedProfile) : load(work._electedProfile));
				case BUILDING_CONTEXT -> StepResult.value((_identities.getOwnerKind(work._electedObservation.recipientObjectId()) == OwnerKind.PHANTOM) ? _context.snapshot(work._electedProfile, work._electedObservation, null, List.of()) : Optional.empty());
				case UNDERSTANDING -> semanticStep(work, token.semanticStep());
				case READING_SOCIAL -> socialStep(work, token.socialCursor());
				case PERSISTING -> persistStep(work);
				case PUBLISHING -> publishStep(work);
				default -> StepResult.failure();
			};
		}
		catch (RuntimeException exception)
		{
			return StepResult.failure();
		}
	}

	private StepResult semanticStep(BatchWork work, int semanticStep)
	{
		if (semanticStep == 0)
		{
			final UnderstandingResult understanding = _semantic.understand(work._election.text(), work._semanticContext);
			final PendingClarification pending = livePending(work._previousSession, understanding, work._nowMinute);
			return StepResult.value(new SemanticRead(understanding, pending, (pending != null) && (understanding.status() != UnderstandingStatus.ACCEPTED)));
		}
		final FragmentResult fragment = _semantic.resolveFragment(work._election.text(), work._semanticContext, work._pending.missingSlots());
		return StepResult.value(new SemanticRead(continuePending(work._pending, fragment), work._pending, false));
	}

	private StepResult socialStep(BatchWork work, int socialCursor)
	{
		Integer value = null;
		boolean failed = false;
		try
		{
			final SubjectRef subject = work._snapshot.speaker().namespace().equals("profile") ? SubjectRef.phantom(Long.parseLong(work._snapshot.speaker().key())) : SubjectRef.character(Integer.parseInt(work._snapshot.speaker().key()));
			final String key = switch (socialCursor)
			{
				case 0 -> "conversation.warmth";
				case 1 -> "conflict.escalation";
				case 2 -> "party.invite.preference";
				default -> throw new IllegalStateException("Conversation social cursor is invalid.");
			};
			final var result = _social.modifier(work._electedProfile, subject, key, work._nowMinute);
			final ModifierSnapshot modifier = result.value();
			if (((result.status() == Status.READY) || (result.status() == Status.INITIALIZED)) && (modifier != null))
			{
				value = modifier.deltaBasisPoints();
			}
			else
			{
				failed = true;
			}
		}
		catch (RuntimeException exception)
		{
			failed = true;
		}
		final Integer[] values = work._socialValues.clone();
		values[socialCursor] = value;
		final Planned planned;
		if (socialCursor == 2)
		{
			final boolean neutral = failed || (values[0] == null) || (values[1] == null) || (values[2] == null);
			final String style = neutral ? "neutral" : _catalog.style(values[0], values[1], values[2]);
			planned = plan(work._electedProfile, work, work._snapshot, work._previousSession, work._understanding, new SocialStyle(style, !neutral && _catalog.suppresses(style)), work._nowMinute);
		}
		else
		{
			planned = null;
		}
		return StepResult.value(new SocialRead(value, failed, planned));
	}

	private StepResult persistStep(BatchWork work)
	{
		final ExecutionEntry executionEntry;
		try
		{
			executionEntry = (work._planned.response() != null) && _store.executionEnabled() ? ExecutionEntry.prepared(work._planned.response()) : null;
		}
		catch (IllegalArgumentException exception)
		{
			return StepResult.value(new SaveAttempt(false, null, true, PersistenceStatus.CAPACITY_REACHED));
		}
		final ConversationState next = mutate(work._baseState, work._planned);
		_persistenceClaims.incrementAndGet();
		try
		{
			final StoredState saved;
			if ((work._planned.response() != null) && _store.executionEnabled())
			{
				final HandoffResult handoff = _store.handoff(work._electedProfile, work._loaded == null ? -1 : work._loaded.rowVersion(), next, executionEntry);
				if (handoff.status() == HandoffStatus.DUPLICATE)
				{
					return StepResult.value(new SaveAttempt(true, null, false, PersistenceStatus.DUPLICATE));
				}
				if (handoff.status() == HandoffStatus.CAPACITY_REACHED)
				{
					return StepResult.value(new SaveAttempt(false, null, true, PersistenceStatus.CAPACITY_REACHED));
				}
				saved = handoff.conversation();
			}
			else
			{
				saved = _store.save(work._electedProfile, work._loaded == null ? -1 : work._loaded.rowVersion(), next);
			}
			cache(saved);
			return StepResult.value(new SaveAttempt(false, saved, false, PersistenceStatus.SAVED));
		}
		catch (ConcurrentModificationException exception)
		{
			return StepResult.value(new SaveAttempt(true, null, false, PersistenceStatus.FAILED));
		}
		catch (RuntimeException exception)
		{
			return StepResult.value(new SaveAttempt(false, null, true, PersistenceStatus.FAILED));
		}
		finally
		{
			_persistenceClaims.decrementAndGet();
		}
	}

	private StepResult publishStep(BatchWork work)
	{
		try
		{
			_plans.publish(work._planned.response());
			return StepResult.value(Boolean.TRUE);
		}
		catch (RuntimeException exception)
		{
			return StepResult.value(Boolean.FALSE);
		}
	}

	@SuppressWarnings("unchecked")
	private void applyStepLocked(WorkToken token, StepResult result)
	{
		final BatchWork work = token.work();
		if ((_batches.get(work._dispatchId) != work) || !work._claimed || (work._generation != token.generation()) || (work._phase != token.phase()))
		{
			_closedMismatches.increment();
			return;
		}
		work._claimed = false;
		if (result.failed())
		{
			completeLocked(work, TerminalKind.FAILED);
			return;
		}
		switch (token.phase())
		{
			case RESOLVING_OBSERVERS ->
			{
				final OptionalLong profile = (OptionalLong) result.value();
				if (profile.isPresent())
				{
					work._resolved.putIfAbsent(profile.getAsLong(), work._observers.get(token.cursor()));
				}
				work._observerCursor++;
			}
			case ELECTING ->
			{
				work._election = (Election) result.value();
				if (work._election == null)
				{
					completeLocked(work, TerminalKind.UNSUPPORTED);
					return;
				}
				work._electedProfile = work._election.profileId();
				work._electedObservation = work._resolved.get(work._electedProfile);
				work._phase = BatchPhase.LOADING_STATE;
			}
			case LOADING_STATE ->
			{
				final StoredState loaded = (StoredState) result.value();
				if ((loaded != null) && !matchesAuthority(loaded.state()))
				{
					work._persistenceStatus = PersistenceStatus.AUTHORITY_STALE;
					completeLocked(work, TerminalKind.AUTHORITY_STALE);
					return;
				}
				if ((loaded != null) && loaded.state().recentObservationHashes().contains(work._observationHash))
				{
					work._persistenceStatus = PersistenceStatus.DUPLICATE;
					completeLocked(work, TerminalKind.DUPLICATE);
					return;
				}
				work._loaded = loaded;
				work._baseState = loaded == null ? newState(work._nowMinute) : loaded.state();
				if (token.conflictReload())
				{
					work._conflictReload = false;
					work._phase = work._persistenceAttempts < MAX_ATTEMPTS ? BatchPhase.PERSISTING : BatchPhase.FAILED;
					if (work._phase == BatchPhase.FAILED)
					{
						work._persistenceStatus = PersistenceStatus.FAILED;
						completeLocked(work, TerminalKind.FAILED);
						return;
					}
				}
				else
				{
					work._phase = BatchPhase.BUILDING_CONTEXT;
				}
			}
			case BUILDING_CONTEXT ->
			{
				final Optional<ContextSnapshot> context = (Optional<ContextSnapshot>) result.value();
				if (context.isEmpty())
				{
					completeLocked(work, TerminalKind.UNSUPPORTED);
					return;
				}
				final ContextSnapshot snapshot = context.get();
				if ((work._descriptor.channel() == ChatType.PARTY) && !work._leaderRedirected && (snapshot.partyLeaderProfileId() > 0) && work._resolved.containsKey(snapshot.partyLeaderProfileId()) && (snapshot.partyLeaderProfileId() != work._electedProfile))
				{
					work._leaderRedirected = true;
					work._electedProfile = snapshot.partyLeaderProfileId();
					work._electedObservation = work._resolved.get(work._electedProfile);
					work._loaded = null;
					work._baseState = null;
					work._phase = BatchPhase.LOADING_STATE;
					return;
				}
				if (snapshot.speaker().namespace().equals("profile") && (Long.parseLong(snapshot.speaker().key()) == work._electedProfile))
				{
					completeLocked(work, TerminalKind.UNSUPPORTED);
					return;
				}
				work._snapshot = snapshot;
				work._previousSession = findSession(work._baseState, work._descriptor.channel(), snapshot.counterpart());
				work._semanticContext = withPrevious(snapshot.input(), work._previousSession);
				if ((work._previousSession != null) && (work._nowMinute < work._previousSession.cooldownUntilMinute()))
				{
					work._planned = noResponse(work._electedProfile, work, snapshot, work._previousSession, work._nowMinute, "no_response.cooldown");
					work._phase = BatchPhase.PERSISTING;
				}
				else
				{
					work._phase = BatchPhase.UNDERSTANDING;
				}
			}
			case UNDERSTANDING ->
			{
				final SemanticRead read = (SemanticRead) result.value();
				work._understanding = read.understanding();
				work._pending = read.pending();
				if (read.needsFragment())
				{
					work._semanticStep = 1;
				}
				else
				{
					work._phase = BatchPhase.READING_SOCIAL;
				}
			}
			case READING_SOCIAL ->
			{
				final SocialRead read = (SocialRead) result.value();
				work._socialValues[token.socialCursor()] = read.value();
				if (read.failed())
				{
					_socialFailures.increment();
				}
				work._socialCursor++;
				if (read.planned() != null)
				{
					work._planned = read.planned();
					work._phase = BatchPhase.PERSISTING;
				}
			}
			case PERSISTING ->
			{
				final SaveAttempt attempt = (SaveAttempt) result.value();
				if (attempt.conflict())
				{
					work._persistenceAttempts++;
					work._conflictReload = true;
					work._phase = BatchPhase.LOADING_STATE;
				}
				else if (attempt.failed())
				{
					work._persistenceStatus = attempt.status();
					completeLocked(work, TerminalKind.FAILED);
					return;
				}
				else
				{
					work._loaded = attempt.saved();
					work._persistenceStatus = attempt.status();
					if (work._planned.response() == null)
					{
						completeLocked(work, TerminalKind.DONE);
						return;
					}
					work._phase = BatchPhase.PUBLISHING;
				}
			}
			case PUBLISHING ->
			{
				if ((work._persistenceStatus != PersistenceStatus.SAVED) || !((Boolean) result.value()))
				{
					_planFailures.increment();
					completeLocked(work, TerminalKind.FAILED);
					return;
				}
				_plansPublished.increment();
				if (work._planned.response().proposal() != null)
				{
					_proposalsPlanned.increment();
				}
				completeLocked(work, TerminalKind.DONE);
				return;
			}
			default ->
			{
				completeLocked(work, TerminalKind.FAILED);
				return;
			}
		}
		if (_batches.get(work._dispatchId) == work)
		{
			enqueueDueLocked(work._dispatchId);
		}
	}

	private void releaseUnchanged(WorkToken token)
	{
		synchronized (_indexMonitor)
		{
			final BatchWork work = token.work();
			if ((_batches.get(work._dispatchId) == work) && work._claimed && (work._generation == token.generation()) && (work._phase == token.phase()))
			{
				work._claimed = false;
				enqueueDueLocked(work._dispatchId);
			}
		}
	}

	private void forceOverflow(DispatchDescriptor dispatch)
	{
		synchronized (_indexMonitor)
		{
			_managedDispatches.remove(dispatch.dispatchId());
			final BatchWork work = _batches.get(dispatch.dispatchId());
			if (work != null)
			{
				completeLocked(work, TerminalKind.OVERFLOW);
				return;
			}
			completeWithoutBatchLocked(dispatch.dispatchId(), TerminalKind.OVERFLOW);
		}
	}

	private boolean processOnePromotion(PulseBudget budget)
	{
		synchronized (_indexMonitor)
		{
			if (_delayed.isEmpty() || (_delayed.peek().duePulse() > _pulse) || !budget.claim())
			{
				return false;
			}
			final DueEntry entry = _delayed.remove();
			if (_dueMembership.contains(entry.dispatchId()) && _batches.containsKey(entry.dispatchId()))
			{
				_due.addLast(entry.dispatchId());
			}
			else
			{
				_dueMembership.remove(entry.dispatchId());
			}
			_indexTransitions.increment();
			return true;
		}
	}

	private void scheduleDelayedLocked(BatchWork work, long duePulse)
	{
		if (_dueMembership.add(work._dispatchId))
		{
			_delayed.add(new DueEntry(duePulse, ++_dueSequence, work._dispatchId));
		}
	}

	private void enqueueDueLocked(long dispatchId)
	{
		if (_dueMembership.add(dispatchId))
		{
			_due.addLast(dispatchId);
		}
	}

	private void completeLocked(BatchWork work, TerminalKind terminal)
	{
		work._phase = terminal == TerminalKind.DONE ? BatchPhase.DONE : BatchPhase.FAILED;
		work._claimed = false;
		_batches.remove(work._dispatchId);
		_dueMembership.remove(work._dispatchId);
		_managedDispatches.remove(work._dispatchId);
		rememberTerminalLocked(work._dispatchId, terminal);
		terminalMetric(terminal);
		_batchesProcessed.increment();
	}

	private void completeWithoutBatchLocked(long dispatchId, TerminalKind terminal)
	{
		_dueMembership.remove(dispatchId);
		_managedDispatches.remove(dispatchId);
		rememberTerminalLocked(dispatchId, terminal);
		terminalMetric(terminal);
	}

	private OptionalLong resolveManagedProfile(int recipientObjectId)
	{
		return (_identities.getOwnerKind(recipientObjectId) == OwnerKind.PHANTOM) ? _context.profileIdForObject(recipientObjectId) : OptionalLong.empty();
	}

	private void terminalMetric(TerminalKind terminal)
	{
		switch (terminal)
		{
			case DUPLICATE -> _duplicates.increment();
			case OVERFLOW -> _overflows.increment();
			case UNSUPPORTED -> _unsupported.increment();
			case AUTHORITY_STALE -> _authorityStale.increment();
			case FAILED -> _failures.increment();
			default ->
			{
			}
		}
	}

	private void rememberTerminalLocked(long dispatchId, TerminalKind terminal)
	{
		_terminal.put(dispatchId, terminal);
		while (_terminal.size() > TERMINAL_TOMBSTONES)
		{
			_terminal.remove(_terminal.keySet().iterator().next());
		}
	}

	private void notifyPhase(BatchPhase phase, long dispatchId)
	{
		try
		{
			_phaseObserver.beforeOperation(phase, dispatchId, Thread.holdsLock(_indexMonitor));
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
		}
	}

	private Election elect(DeliveredObservation descriptor, TreeMap<Long, DeliveredObservation> observers)
	{
		if (observers.isEmpty())
		{
			return null;
		}
		return switch (descriptor.channel())
		{
			case WHISPER -> exactWhisperTarget(descriptor, observers);
			case PARTY -> new Election(observers.firstKey(), descriptor.text());
			case GENERAL, TRADE -> exactAddress(descriptor.text(), observers);
			default -> null;
		};
	}

	private static Election exactWhisperTarget(DeliveredObservation descriptor, TreeMap<Long, DeliveredObservation> observers)
	{
		final List<Map.Entry<Long, DeliveredObservation>> targets = observers.entrySet().stream().filter(entry -> entry.getValue().recipientName().equals(descriptor.whisperTarget())).toList();
		if (targets.size() == 1)
		{
			return new Election(targets.getFirst().getKey(), descriptor.text());
		}
		return observers.size() == 1 ? new Election(observers.firstKey(), descriptor.text()) : null;
	}

	private static Election exactAddress(String text, TreeMap<Long, DeliveredObservation> observers)
	{
		final List<Election> matches = new ArrayList<>();
		for (var entry : observers.entrySet())
		{
			final String stripped = stripVocative(text, entry.getValue().recipientName());
			if (stripped != null)
			{
				matches.add(new Election(entry.getKey(), stripped));
			}
		}
		return matches.size() == 1 ? matches.getFirst() : null;
	}

	private static String stripVocative(String text, String exactName)
	{
		if (text.startsWith(exactName) && (text.length() > exactName.length()))
		{
			int index = exactName.length();
			if (isVocativePunctuation(text.charAt(index++)))
			{
				while ((index < text.length()) && Character.isWhitespace(text.charAt(index)))
				{
					index++;
				}
				return index < text.length() ? text.substring(index) : null;
			}
		}
		if (text.endsWith(exactName) && (text.length() > exactName.length()))
		{
			int index = text.length() - exactName.length() - 1;
			while ((index >= 0) && Character.isWhitespace(text.charAt(index)))
			{
				index--;
			}
			if ((index >= 0) && isVocativePunctuation(text.charAt(index)))
			{
				return text.substring(0, index).stripTrailing();
			}
		}
		return null;
	}

	private static boolean isVocativePunctuation(char value)
	{
		return (value == ',') || (value == ':') || (value == ';') || (value == '!') || (value == '?');
	}

	private Planned plan(long profileId, BatchWork batch, ContextSnapshot context, ConversationSession previous, UnderstandingResult understanding, SocialStyle social, long nowMinute)
	{
		final String semanticHash = PhantomConversationModel.semanticResultHash(understanding.canonicalEncoding());
		String act;
		ConversationActionProposal proposal = null;
		PendingClarification pending = null;
		if (understanding.status() == UnderstandingStatus.ACCEPTED)
		{
			final ProposalMapping mapping = _catalog.mapping(understanding.selectedIntent());
			if (mapping == null)
			{
				act = "no_response.unsupported";
			}
			else
			{
				act = understanding.selectedIntent().equals("party.accept") ? "ack.accepted" : understanding.selectedIntent().equals("party.refuse") ? "ack.refused" : mapping.query() ? "ack.query_proposed" : "ack.action_proposed";
				final PhantomDomainRef target = understanding.slots().stream().filter(slot -> slot.type() == SlotType.TARGET_PLAYER).map(SlotValue::domainReference).findFirst().orElse(null);
				proposal = new ConversationActionProposal(mapping.proposalKey(), new PhantomDomainRef("profile", Long.toString(profileId)), target, understanding.slots(), semanticHash, batch._observationHash, understanding.confidence(), nowMinute, nowMinute + mapping.ttlMinutes(), Authorization.CHECKPOINT_2_REQUIRED);
			}
		}
		else if (understanding.status() == UnderstandingStatus.CLARIFICATION_REQUIRED)
		{
			act = clarificationAct(understanding.reasonKey());
			final SlotType missing = missingSlot(understanding);
			if ((missing != null) && !understanding.selectedIntent().equals("unknown"))
			{
				pending = new PendingClarification(understanding.selectedIntent(), understanding.slots(), Set.of(missing), nowMinute + _catalog.limits().clarificationTtlMinutes(), understanding.packHash(), understanding.corpusHash(), understanding.knowledgeHash(), understanding.topologyHash(), understanding.partyRoleHash());
			}
		}
		else
		{
			act = "no_response.unsupported";
		}
		final String style = social.style();
		final boolean suppressed = (proposal != null) && !act.equals("ack.query_proposed") && act.startsWith("ack.") && social.suppressAcknowledgement();
		final String text = _catalog.template(act, style, selector(profileId, batch._observationHash, act, style));
		final long cooldown = nowMinute + _catalog.channel(batch._descriptor.channel()).cooldownMinutes();
		final List<ConversationEvidence> evidence = understanding.evidence().stream().limit(_catalog.limits().evidence()).map(item -> new ConversationEvidence(item.key(), item.authorityKey())).toList();
		final DeliveryPolicy deliveryPolicy = suppressed ? DeliveryPolicy.SUPPRESS_ACK : DeliveryPolicy.SEND;
		final ConversationResponsePlan response = new ConversationResponsePlan(profileId, batch._dispatchId, batch._observationHash, batch._descriptor.channel(), new ConversationSubject(context.speaker()), semanticHash, act, style, text, proposal, deliveryPolicy, cooldown, evidence);
		final ConversationSession session = new ConversationSession(batch._descriptor.channel(), context.counterpart(), nowMinute, cooldown, understanding.status() == UnderstandingStatus.ACCEPTED ? understanding.selectedIntent() : previous == null ? null : previous.previousIntent(), understanding.status() == UnderstandingStatus.ACCEPTED ? understanding.slots() : previous == null ? List.of() : previous.previousSlots(), pending, PhantomConversationModel.sha256(act), PhantomConversationModel.sha256(style), proposal == null ? "" : PhantomConversationModel.sha256(proposal.proposalKey() + '|' + semanticHash));
		return new Planned(profileId, session, response, batch._observationHash, nowMinute);
	}

	private Planned noResponse(long profileId, BatchWork batch, ContextSnapshot context, ConversationSession previous, long nowMinute, String act)
	{
		final String semanticHash = PhantomConversationModel.sha256("no-semantic|" + batch._observationHash + '|' + act);
		final String text = _catalog.template(act, "neutral", selector(profileId, batch._observationHash, act, "neutral"));
		final ConversationResponsePlan response = new ConversationResponsePlan(profileId, batch._dispatchId, batch._observationHash, batch._descriptor.channel(), new ConversationSubject(context.speaker()), semanticHash, act, "neutral", text, null, previous.cooldownUntilMinute(), List.of(new ConversationEvidence("conversation.cooldown", Long.toString(previous.cooldownUntilMinute()))));
		final ConversationSession session = new ConversationSession(previous.channel(), previous.counterpart(), nowMinute, previous.cooldownUntilMinute(), previous.previousIntent(), previous.previousSlots(), previous.pending(), PhantomConversationModel.sha256(act), PhantomConversationModel.sha256("neutral"), previous.lastProposalHash());
		return new Planned(profileId, session, response, batch._observationHash, nowMinute);
	}

	private ConversationState mutate(ConversationState base, Planned planned)
	{
		final List<ConversationSession> sessions = new ArrayList<>(base.sessions());
		sessions.removeIf(session -> session.key().equals(planned.session().key()));
		sessions.add(planned.session());
		while (sessions.size() > _catalog.limits().sessionsPerProfile())
		{
			sessions.remove(sessions.stream().min(Comparator.comparingLong(ConversationSession::lastObservedMinute).thenComparing(ConversationSession::key)).orElseThrow());
		}
		final List<String> recent = new ArrayList<>(base.recentObservationHashes());
		if (recent.contains(planned.observationHash()))
		{
			throw new ConcurrentModificationException("Conversation observation is already durable.");
		}
		while (recent.size() >= _catalog.limits().recentHashes())
		{
			recent.removeFirst();
		}
		recent.add(planned.observationHash());
		return new ConversationState(base.catalogHash(), base.packHash(), base.corpusHash(), base.knowledgeHash(), base.topologyHash(), base.roleHash(), base.socialHash(), Math.max(base.logicalMinute(), planned.nowMinute()), sessions, recent);
	}

	private ConversationState newState(long logicalMinute)
	{
		final AuthorityGeneration authority = _authority;
		return new ConversationState(_catalog.hash(), authority.packHash(), authority.corpusHash(), authority.knowledgeHash(), authority.topologyHash(), authority.roleHash(), authority.socialHash(), logicalMinute, List.of(), List.of());
	}

	private boolean matchesAuthority(ConversationState state)
	{
		return state.catalogHash().equals(_catalog.hash()) && _authority.matches(state);
	}

	private StoredState load(long profileId)
	{
		synchronized (_cache)
		{
			final StoredState cached = _cache.get(profileId);
			if (cached != null)
			{
				return cached;
			}
		}
		return exact(profileId);
	}

	private StoredState exact(long profileId)
	{
		final StoredState loaded = _store.load(profileId).orElse(null);
		if (loaded != null)
		{
			cache(loaded);
		}
		return loaded;
	}

	private void cache(StoredState state)
	{
		synchronized (_cache)
		{
			_cache.put(state.profileId(), state);
		}
	}

	private OperationClaim beginOperation()
	{
		synchronized (_lifecycle)
		{
			if (_state != ServiceState.RUNNING)
			{
				return null;
			}
			_operationClaims.incrementAndGet();
			return new OperationClaim();
		}
	}

	private static InputContext withPrevious(InputContext input, ConversationSession previous)
	{
		return new InputContext(input.speaker(), input.channel(), input.partyLeader(), input.partyMembers(), input.nearbyPlayers(), input.recentPlayers(), null, input.currentLocation(), input.currentTopology(), previous == null ? null : previous.previousIntent(), previous == null ? List.of() : previous.previousSlots());
	}

	private static ConversationSession findSession(ConversationState state, ChatType channel, PhantomDomainRef counterpart)
	{
		return state.sessions().stream().filter(session -> (session.channel() == channel) && session.counterpart().equals(counterpart)).findFirst().orElse(null);
	}

	private static PendingClarification livePending(ConversationSession previous, UnderstandingResult current, long nowMinute)
	{
		if ((previous == null) || (previous.pending() == null) || (nowMinute >= previous.pending().expiryMinute()))
		{
			return null;
		}
		final PendingClarification pending = previous.pending();
		return pending.packHash().equals(current.packHash()) && pending.corpusHash().equals(current.corpusHash()) && pending.knowledgeHash().equals(current.knowledgeHash()) && pending.topologyHash().equals(current.topologyHash()) && pending.roleHash().equals(current.partyRoleHash()) ? pending : null;
	}

	private static UnderstandingResult continuePending(PendingClarification pending, FragmentResult fragment)
	{
		final Map<SlotType, SlotValue> merged = new TreeMap<>();
		pending.knownSlots().forEach(slot -> merged.put(slot.type(), slot));
		fragment.slots().forEach(slot -> merged.putIfAbsent(slot.type(), slot));
		final Set<SlotType> remaining = new HashSet<>(pending.missingSlots());
		remaining.removeAll(merged.keySet());
		final boolean accepted = (fragment.status() == UnderstandingStatus.ACCEPTED) && remaining.isEmpty();
		final String reason = accepted ? "accept.matched" : remaining.isEmpty() ? fragment.reasonKey() : reasonFor(remaining.stream().sorted().findFirst().orElseThrow());
		return new UnderstandingResult(accepted ? UnderstandingStatus.ACCEPTED : UnderstandingStatus.CLARIFICATION_REQUIRED, fragment.normalizedHash(), pending.packHash(), pending.corpusHash(), pending.knowledgeHash(), pending.topologyHash(), pending.roleHash(), pending.intentKey(), accepted ? 8000 : 0, List.copyOf(merged.values()), List.of(), reason, fragment.evidence());
	}

	private static String reasonFor(SlotType type)
	{
		return switch (type)
		{
			case TARGET_PLAYER -> "clarify.target_player";
			case PARTY_ROLE -> "clarify.party_role";
			case TOPOLOGY_NODE, LOCATION -> "clarify.location";
			case QUANTITY -> "clarify.quantity";
			default -> "clarify.entity";
		};
	}

	private static String clarificationAct(String reason)
	{
		return Set.of("clarify.intent", "clarify.target_player", "clarify.entity", "clarify.party_role", "clarify.location", "clarify.quantity", "clarify.complexity").contains(reason) ? reason : "clarify.entity";
	}

	private static SlotType missingSlot(UnderstandingResult understanding)
	{
		return switch (understanding.reasonKey())
		{
			case "clarify.target_player" -> SlotType.TARGET_PLAYER;
			case "clarify.party_role" -> SlotType.PARTY_ROLE;
			case "clarify.location" -> SlotType.TOPOLOGY_NODE;
			case "clarify.quantity" -> SlotType.QUANTITY;
			case "clarify.entity" -> switch (understanding.selectedIntent())
			{
				case "item.acquire.query", "item.source.query" -> SlotType.ITEM;
				case "content.requirements.query" -> SlotType.CONTENT;
				case "party.support.request" -> SlotType.CAPABILITY;
				default -> null;
			};
			default -> null;
		};
	}

	private long selector(long profileId, String observationHash, String act, String style)
	{
		return Long.parseUnsignedLong(PhantomConversationModel.sha256(profileId + "|" + observationHash + '|' + act + '|' + style + '|' + _catalog.hash()).substring(0, 16), 16);
	}

	private record IngressEvent(IngressKind kind, DeliveredObservation observation, DispatchDescriptor dispatch)
	{
		private static IngressEvent delivered(DeliveredObservation observation)
		{
			return new IngressEvent(IngressKind.DELIVERED, observation, null);
		}

		private static IngressEvent closed(DispatchDescriptor dispatch)
		{
			return new IngressEvent(IngressKind.CLOSED, null, dispatch);
		}

		private long dispatchId()
		{
			return kind == IngressKind.DELIVERED ? observation.dispatchId() : dispatch.dispatchId();
		}
	}

	private record DueEntry(long duePulse, long sequence, long dispatchId) implements Comparable<DueEntry>
	{
		@Override
		public int compareTo(DueEntry other)
		{
			final int pulse = Long.compare(duePulse, other.duePulse);
			return pulse != 0 ? pulse : Long.compare(sequence, other.sequence);
		}
	}

	private record WorkToken(BatchWork work, long generation, BatchPhase phase, int cursor, int semanticStep, int socialCursor, boolean conflictReload)
	{
	}

	private record StepResult(Object value, boolean failed)
	{
		private static StepResult value(Object value)
		{
			return new StepResult(value, false);
		}

		private static StepResult failure()
		{
			return new StepResult(null, true);
		}
	}

	private record Election(long profileId, String text)
	{
	}

	private record Planned(long ownerProfileId, ConversationSession session, ConversationResponsePlan response, String observationHash, long nowMinute)
	{
	}

	private record SocialStyle(String style, boolean suppressAcknowledgement)
	{
	}

	private record SemanticRead(UnderstandingResult understanding, PendingClarification pending, boolean needsFragment)
	{
	}

	private record SocialRead(Integer value, boolean failed, Planned planned)
	{
	}

	private record SaveAttempt(boolean conflict, StoredState saved, boolean failed, PersistenceStatus status)
	{
	}

	private record AuthorityGeneration(String packHash, String corpusHash, String knowledgeHash, String topologyHash, String roleHash, String socialHash)
	{
		private boolean matches(ConversationState state)
		{
			return packHash.equals(state.packHash()) && corpusHash.equals(state.corpusHash()) && knowledgeHash.equals(state.knowledgeHash()) && topologyHash.equals(state.topologyHash()) && roleHash.equals(state.roleHash()) && socialHash.equals(state.socialHash());
		}
	}

	private static final class BatchWork
	{
		private final long _dispatchId;
		private final String _observationHash;
		private final DeliveredObservation _descriptor;
		private final long _firstPulse;
		private final TreeMap<Integer, DeliveredObservation> _collectingObservers = new TreeMap<>();
		private final TreeMap<Long, DeliveredObservation> _resolved = new TreeMap<>();
		private final Integer[] _socialValues = new Integer[3];
		private List<DeliveredObservation> _observers = List.of();
		private BatchPhase _phase = BatchPhase.COLLECTING;
		private boolean _closed;
		private boolean _overflow;
		private boolean _claimed;
		private boolean _leaderRedirected;
		private boolean _conflictReload;
		private long _generation;
		private int _observerCursor;
		private int _semanticStep;
		private int _socialCursor;
		private int _persistenceAttempts;
		private long _electedProfile;
		private final long _nowMinute;
		private Election _election;
		private DeliveredObservation _electedObservation;
		private StoredState _loaded;
		private ConversationState _baseState;
		private ContextSnapshot _snapshot;
		private ConversationSession _previousSession;
		private InputContext _semanticContext;
		private UnderstandingResult _understanding;
		private PendingClarification _pending;
		private Planned _planned;
		private PersistenceStatus _persistenceStatus;

		private BatchWork(DeliveredObservation descriptor, long firstPulse)
		{
			_dispatchId = descriptor.dispatchId();
			_observationHash = descriptor.observationHash();
			_descriptor = descriptor;
			_firstPulse = firstPulse;
			_nowMinute = Math.max(0, descriptor.epochMillis() / 60000L);
		}

		private void add(DeliveredObservation observation, int maximum)
		{
			if (_closed || !observation.observationHash().equals(_observationHash))
			{
				_overflow = true;
				return;
			}
			if (!_collectingObservers.containsKey(observation.recipientObjectId()) && (_collectingObservers.size() >= maximum))
			{
				_overflow = true;
				return;
			}
			_collectingObservers.putIfAbsent(observation.recipientObjectId(), observation);
		}

		private void freezeObservers()
		{
			_observers = List.copyOf(_collectingObservers.values());
			_collectingObservers.clear();
		}
	}

	private static final class PulseBudget
	{
		private final int _maximum;
		private int _used;

		private PulseBudget(int maximum)
		{
			_maximum = maximum;
		}

		private boolean claim()
		{
			if (_used >= _maximum)
			{
				return false;
			}
			_used++;
			return true;
		}

		private int used()
		{
			return _used;
		}

		private int remaining()
		{
			return _maximum - _used;
		}
	}

	private final class OperationClaim implements AutoCloseable
	{
		private final AtomicBoolean _closed = new AtomicBoolean();

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_operationClaims.decrementAndGet();
			}
		}
	}
}
