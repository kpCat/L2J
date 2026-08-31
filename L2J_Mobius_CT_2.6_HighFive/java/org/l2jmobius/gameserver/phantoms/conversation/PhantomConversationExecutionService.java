/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog.ProposalPolicy;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ActionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionReceipt;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationBinding;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.InvitationResponse;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.GoalPreparation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.PendingInvitation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.ResultStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.GoalMutationResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.StoredExecution;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationGoalRuntimePort.SyncStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;

/** One shared-scheduler execution owner; durable state is the only work truth. */
public final class PhantomConversationExecutionService implements PhantomSchedulerControlPort, PhantomConversationPlanSink
{
	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public enum Phase
	{
		RECOVERY_PAGE,
		RECOVERY_ENTRY,
		DELAY_PROMOTE,
		LOAD,
		AUTHORIZE,
		QUERY,
		GOAL_SUBMIT,
		GOAL_OBSERVE,
		PARTY_RESPONSE,
		OUTBOUND_PREPARE,
		OUTBOUND_DISPATCH,
		TERMINAL_STORE
	}

	@FunctionalInterface
	public interface PhaseObserver
	{
		PhaseObserver NONE = (phase, profileId) ->
		{
		};

		void beforeBoundary(Phase phase, long profileId);
	}

	public enum OutboundSubmissionStatus
	{
		ACCEPTED,
		IDEMPOTENT,
		CAPACITY_REACHED,
		NOT_RUNNING,
		INVALID,
		RETRY
	}

	public record OutboundSubmission(OutboundSubmissionStatus status, String planId)
	{
		public OutboundSubmission
		{
			Objects.requireNonNull(status);
			planId = Objects.requireNonNull(planId);
		}
	}

	public record Snapshot(State state, String catalogHash, int ready, int delayed, int membership, int claims, boolean pulseOwned, boolean recoveryDone, long recoveryCursor, long signals, long signalDrops, long pages, long entriesLoaded, long queries, long goalsSubmitted, long partyResponses, long outboundDispatches, long sent, long uncertain, long terminalReceipts, long conflicts, long failures, long maximumOperationsPerPulse)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, "none", 0, 0, 0, 0, false, true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private final PhantomConversationExecutionCatalog _catalog;
	private final PhantomConversationExecutionStore _store;
	private final PhantomGoalStateStore _goals;
	private final PhantomConversationExecutionPort _port;
	private final PhantomConversationGoalRuntimePort _goalRuntime;
	private final LongSupplier _clock;
	private final PhaseObserver _phaseObserver;
	private final ArrayBlockingQueue<Long> _ready;
	private final Set<Long> _membership = ConcurrentHashMap.newKeySet();
	private final Object _dueMonitor = new Object();
	private final PriorityQueue<DueProfile> _delayed = new PriorityQueue<>();
	private final AtomicBoolean _pulseOwner = new AtomicBoolean();
	private final AtomicBoolean _recoveryReset = new AtomicBoolean();
	private final AtomicInteger _claims = new AtomicInteger();
	private final LongAdder _signals = new LongAdder();
	private final LongAdder _signalDrops = new LongAdder();
	private final LongAdder _pages = new LongAdder();
	private final LongAdder _entriesLoaded = new LongAdder();
	private final LongAdder _queries = new LongAdder();
	private final LongAdder _goalsSubmitted = new LongAdder();
	private final LongAdder _partyResponses = new LongAdder();
	private final LongAdder _outboundDispatches = new LongAdder();
	private final LongAdder _sent = new LongAdder();
	private final LongAdder _uncertain = new LongAdder();
	private final LongAdder _terminalReceipts = new LongAdder();
	private final LongAdder _conflicts = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private final LongAccumulator _maximumOperationsPerPulse = new LongAccumulator(Long::max, 0);
	private volatile State _state = State.NEW;
	private volatile boolean _recoveryDone;
	private volatile long _recoveryCursor;
	private List<StoredExecution> _recoveryPage = List.of();
	private int _recoveryPageIndex;
	private long _pulse;
	private long _dueSequence;
	private int _pulseOperations;

	public PhantomConversationExecutionService(PhantomConversationExecutionCatalog catalog, PhantomConversationExecutionStore store, PhantomGoalStateStore goals, PhantomConversationExecutionPort port)
	{
		this(catalog, store, goals, port, PhantomConversationGoalRuntimePort.NOOP, () -> System.currentTimeMillis() / 60000L, PhaseObserver.NONE);
	}

	public PhantomConversationExecutionService(PhantomConversationExecutionCatalog catalog, PhantomConversationExecutionStore store, PhantomGoalStateStore goals, PhantomConversationExecutionPort port, PhantomConversationGoalRuntimePort goalRuntime)
	{
		this(catalog, store, goals, port, goalRuntime, () -> System.currentTimeMillis() / 60000L, PhaseObserver.NONE);
	}

	public PhantomConversationExecutionService(PhantomConversationExecutionCatalog catalog, PhantomConversationExecutionStore store, PhantomGoalStateStore goals, PhantomConversationExecutionPort port, LongSupplier clock, PhaseObserver phaseObserver)
	{
		this(catalog, store, goals, port, PhantomConversationGoalRuntimePort.NOOP, clock, phaseObserver);
	}

	public PhantomConversationExecutionService(PhantomConversationExecutionCatalog catalog, PhantomConversationExecutionStore store, PhantomGoalStateStore goals, PhantomConversationExecutionPort port, PhantomConversationGoalRuntimePort goalRuntime, LongSupplier clock, PhaseObserver phaseObserver)
	{
		_catalog = Objects.requireNonNull(catalog);
		_store = Objects.requireNonNull(store);
		_goals = Objects.requireNonNull(goals);
		_port = Objects.requireNonNull(port);
		_goalRuntime = Objects.requireNonNull(goalRuntime);
		_clock = Objects.requireNonNull(clock);
		_phaseObserver = Objects.requireNonNull(phaseObserver);
		_ready = new ArrayBlockingQueue<>(catalog.limits().executionQueue());
	}

	public boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}
		_recoveryCursor = 0;
		_recoveryDone = false;
		_recoveryPage = List.of();
		_recoveryPageIndex = 0;
		_recoveryReset.set(false);
		_state = State.RUNNING;
		return true;
	}

	@Override
	public void publish(ConversationResponsePlan plan)
	{
		if ((plan == null) || (_state != State.RUNNING))
		{
			return;
		}
		_signals.increment();
		signal(plan.ownerProfileId());
	}

	/**
	 * Persists a typed, proposal-free outbound entry before it can be dispatched.
	 */
	public OutboundSubmission submitOutbound(long profileId, ExecutionEntry entry)
	{
		if ((profileId <= 0) || (entry == null) || (entry.proposalKey() != null) || (entry.actionState() != ActionState.NONE) || (entry.outboundState() != OutboundState.PREPARED))
		{
			return new OutboundSubmission(OutboundSubmissionStatus.INVALID, entry == null ? "" : entry.planId());
		}
		if (_state != State.RUNNING)
		{
			return new OutboundSubmission(OutboundSubmissionStatus.NOT_RUNNING, entry.planId());
		}
		_claims.incrementAndGet();
		try
		{
			if (_state != State.RUNNING)
			{
				return new OutboundSubmission(OutboundSubmissionStatus.NOT_RUNNING, entry.planId());
			}
			for (int attempt = 0; attempt < 3; attempt++)
			{
				try
				{
					final var result = _store.enqueueOutbound(profileId, entry);
					switch (result.status())
					{
						case SAVED:
							signal(profileId);
							return new OutboundSubmission(OutboundSubmissionStatus.ACCEPTED, entry.planId());
						case DUPLICATE:
							signal(profileId);
							return new OutboundSubmission(OutboundSubmissionStatus.IDEMPOTENT, entry.planId());
						case CAPACITY_REACHED:
							return new OutboundSubmission(OutboundSubmissionStatus.CAPACITY_REACHED, entry.planId());
					}
				}
				catch (java.util.ConcurrentModificationException exception)
				{
					_conflicts.increment();
				}
				catch (RuntimeException exception)
				{
					_failures.increment();
					return new OutboundSubmission(OutboundSubmissionStatus.RETRY, entry.planId());
				}
			}
			return new OutboundSubmission(OutboundSubmissionStatus.RETRY, entry.planId());
		}
		finally
		{
			_claims.decrementAndGet();
		}
	}

	public Optional<ExecutionReceipt> outboundReceipt(long profileId, String planId)
	{
		if ((profileId <= 0) || (planId == null) || (planId.length() != 64))
		{
			return Optional.empty();
		}
		try
		{
			return _store.load(profileId).flatMap(stored -> stored.state().receipts().stream().filter(receipt -> receipt.planId().equals(planId)).findFirst());
		}
		catch (RuntimeException exception)
		{
			return Optional.empty();
		}
	}

	@Override
	public void onPulse()
	{
		if ((_state != State.RUNNING) || !_pulseOwner.compareAndSet(false, true))
		{
			return;
		}
		_claims.incrementAndGet();
		try
		{
			_pulse++;
			_pulseOperations = 0;
			while (hasBudget())
			{
				resetRecoveryIfRequested();
				boolean progressed = false;
				if (!_recoveryDone && ((_ready.remainingCapacity() >= _catalog.limits().recoveryPage()) || _ready.isEmpty()))
				{
					progressed = recoverOne();
				}
				if (hasBudget())
				{
					final boolean promoted = promoteOneDue();
					progressed |= promoted;
				}
				if (hasBudget())
				{
					final Long profileId = _ready.poll();
					if (profileId != null)
					{
						_membership.remove(profileId);
						final long delay = process(profileId);
						if (delay != 0)
						{
							schedule(profileId, delay);
					}
						progressed = true;
					}
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
			_maximumOperationsPerPulse.accumulate(_pulseOperations);
			_claims.decrementAndGet();
			_pulseOwner.set(false);
		}
	}

	public void beginStop()
	{
		if (_state == State.RUNNING)
		{
			_state = State.STOPPING;
		}
		else if (_state == State.NEW)
		{
			_state = State.STOPPED;
		}
	}

	public boolean finishStop()
	{
		if (_state == State.RUNNING)
		{
			beginStop();
		}
		if (_pulseOwner.get() || (_claims.get() != 0))
		{
			return false;
		}
		_ready.clear();
		_membership.clear();
		synchronized (_dueMonitor)
		{
			_delayed.clear();
		}
		_state = State.STOPPED;
		return true;
	}

	public Snapshot snapshot()
	{
		final int delayed;
		synchronized (_dueMonitor)
		{
			delayed = _delayed.size();
		}
		return new Snapshot(_state, _catalog.hash(), _ready.size(), delayed, _membership.size(), _claims.get(), _pulseOwner.get(), _recoveryDone, _recoveryCursor, _signals.sum(), _signalDrops.sum(), _pages.sum(), _entriesLoaded.sum(), _queries.sum(), _goalsSubmitted.sum(), _partyResponses.sum(), _outboundDispatches.sum(), _sent.sum(), _uncertain.sum(), _terminalReceipts.sum(), _conflicts.sum(), _failures.sum(), _maximumOperationsPerPulse.get());
	}

	private boolean recoverOne()
	{
		if (_recoveryPageIndex >= _recoveryPage.size())
		{
			if (!spend(Phase.RECOVERY_PAGE, 0))
			{
				return false;
			}
			_recoveryPage = _store.pageAfter(_recoveryCursor);
			_recoveryPageIndex = 0;
			_pages.increment();
			if (_recoveryPage.isEmpty())
			{
				_recoveryDone = true;
				return true;
			}
		}
		if (!hasBudget() || (_ready.remainingCapacity() == 0) || !spend(Phase.RECOVERY_ENTRY, _recoveryPage.get(_recoveryPageIndex).profileId()))
		{
			return false;
		}
		final StoredExecution stored = _recoveryPage.get(_recoveryPageIndex++);
		_recoveryCursor = stored.profileId();
		if (!stored.state().entries().isEmpty())
		{
			signal(stored.profileId());
		}
		if (_recoveryPageIndex >= _recoveryPage.size())
		{
			_recoveryDone = _recoveryPage.size() < _catalog.limits().recoveryPage();
			_recoveryPage = List.of();
			_recoveryPageIndex = 0;
		}
		return true;
	}

	private long process(long profileId)
	{
		try
		{
			if (!spend(Phase.LOAD, profileId))
			{
				return 1;
			}
			StoredExecution stored = _store.load(profileId).orElse(null);
			if ((stored == null) || stored.state().entries().isEmpty())
			{
				return 0;
			}
			_entriesLoaded.increment();
			ExecutionEntry entry = selectEntry(stored.state());
			if (needsTerminalSynchronization(entry))
			{
				return processTerminalGoal(stored, entry);
			}
			if (entry.outboundState() == OutboundState.DISPATCHING)
			{
				entry = entry.withOutbound(OutboundState.UNCERTAIN, "execution.failed", now());
				final FinalStoreResult terminal = storeFinal(stored, entry);
				if ((terminal == null) || !terminal.compacted())
				{
					return terminal == null ? 1 : capacityRetry(stored.state());
				}
				_uncertain.increment();
				return entry.actionState() == ActionState.SUBMITTED ? 10 : 1;
			}
			if (now() >= entry.expiryMinute())
			{
				stored = expireOwnedGoal(stored, expire(entry));
				entry = stored.state().entry(entry.planId());
				if (needsTerminalSynchronization(entry))
				{
					return processTerminalGoal(stored, entry);
				}
				if (entry.terminal())
				{
					final FinalStoreResult terminal = storeFinal(stored, entry);
					return terminal == null ? 1 : terminal.compacted() ? 1 : capacityRetry(stored.state());
				}
				return 1;
			}
			final ProposalPolicy policy = entry.proposalKey() == null ? null : _catalog.proposal(entry.proposalKey());
			if ((entry.proposalKey() != null) && ((policy == null) || !policy.authorizes(entry)))
			{
				if (!spend(Phase.AUTHORIZE, profileId))
				{
					return 1;
				}
				entry = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withAction(ActionState.REJECTED, 0, 0, "execution.failed", now());
				stored = save(stored, entry);
				entry = stored.state().entry(entry.planId());
			}
			else if (entry.actionState() == ActionState.PREPARED)
			{
				if (!spend(Phase.AUTHORIZE, profileId))
				{
					return 1;
				}
				return processPrepared(stored, entry, policy);
			}
			else if (entry.actionState() == ActionState.SUBMITTED)
			{
				stored = observeSubmitted(stored, entry);
				entry = stored.state().entry(entry.planId());
				if (entry == null)
				{
					return 0;
				}
			}
			if (needsTerminalSynchronization(entry))
			{
				return processTerminalGoal(stored, entry);
			}
			if (entry.outboundState() == OutboundState.PREPARED)
			{
				return dispatch(stored, entry) ? (entry.actionState() == ActionState.SUBMITTED ? 10 : 1) : 1;
			}
			if (entry.terminal())
			{
				final FinalStoreResult terminal = storeFinal(stored, entry);
				return terminal == null ? 1 : terminal.compacted() ? 1 : capacityRetry(stored.state());
			}
			return entry.actionState() == ActionState.SUBMITTED ? 10 : 1;
		}
		catch (java.util.ConcurrentModificationException exception)
		{
			_conflicts.increment();
			return 1;
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
			return 10;
		}
	}

	private int processPrepared(StoredExecution stored, ExecutionEntry entry, ProposalPolicy policy)
	{
		return switch (policy.kind())
		{
			case QUERY ->
			{
				if (!spend(Phase.QUERY, stored.profileId()))
				{
					yield 1;
				}
				final QueryResult result = _port.query(stored.profileId(), entry);
				_queries.increment();
				final String reason = queryReason(result.status());
				final ExecutionEntry next = entry.withResult(_catalog.renderQuery(reason, entry.style(), result), reason).withAction(result.status() == ResultStatus.REJECTED || result.status() == ResultStatus.UNCERTAIN ? ActionState.REJECTED : ActionState.COMPLETED, 0, 0, reason, now());
				save(stored, next);
				yield 1;
			}
			case DEFERRED ->
			{
				final ExecutionEntry next = entry.withResult(_catalog.render("action.deferred", entry.style(), null), "action.deferred").withAction(ActionState.DEFERRED, 0, 0, "action.deferred", now());
				save(stored, next);
				yield 1;
			}
			case GOAL ->
			{
				if (!spend(Phase.GOAL_SUBMIT, stored.profileId()))
				{
					yield 1;
				}
				submitGoal(stored, entry);
				yield 1;
			}
			case PARTY_RESPONSE ->
			{
				if (entry.invitationBinding() == null)
				{
					if (!spend(Phase.PARTY_RESPONSE, stored.profileId()))
					{
						yield 1;
					}
					final PendingInvitation invitation = _port.pendingInvitation(stored.profileId()).orElse(null);
					if ((invitation == null) || !invitation.requester().equals(entry.counterpart()))
					{
						final ExecutionEntry next = entry.withResult(_catalog.render("party.stale", entry.style(), null), "party.stale").withAction(ActionState.REJECTED, 0, 0, "party.stale", now());
						save(stored, next);
						yield 1;
					}
					final InvitationResponse response = entry.proposalKey().equals("party.accept") ? InvitationResponse.ACCEPT : InvitationResponse.REFUSE;
					save(stored, entry.withInvitation(new InvitationBinding(invitation.sequence(), invitation.requesterObjectId(), invitation.inviteeObjectId(), response)));
					yield 1;
				}
				if (entry.proposalKey().equals("party.accept"))
				{
					if (exactInvitation(stored.profileId(), entry).isEmpty())
					{
						final ResultStatus reconciliation = _port.reconcileInvitation(stored.profileId(), entry);
						final boolean uncertain = reconciliation == ResultStatus.UNCERTAIN;
						final String reason = uncertain ? "execution.failed" : "party.stale";
						final ExecutionEntry next = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(uncertain ? ActionState.UNCERTAIN : ActionState.REJECTED, 0, 0, reason, now());
						save(stored, next);
						if (uncertain)
						{
							_uncertain.increment();
						}
						yield 1;
					}
					if (!spend(Phase.GOAL_SUBMIT, stored.profileId()))
					{
						yield 1;
					}
					submitGoal(stored, entry);
					yield 1;
				}
				if (!spend(Phase.PARTY_RESPONSE, stored.profileId()))
				{
					yield 1;
				}
				final PendingInvitation invitation = exactInvitation(stored.profileId(), entry).orElse(null);
				final ResultStatus result = invitation == null ? _port.reconcileInvitation(stored.profileId(), entry) : _port.respondToPending(stored.profileId(), invitation, false, entry.planId());
				_partyResponses.increment();
				final boolean completed = result == ResultStatus.COMPLETED || result == ResultStatus.IDEMPOTENT;
				final boolean uncertain = result == ResultStatus.UNCERTAIN;
				final String reason = completed ? "party.refused" : uncertain ? "execution.failed" : "party.stale";
				final ExecutionEntry next = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(completed ? ActionState.COMPLETED : uncertain ? ActionState.UNCERTAIN : ActionState.REJECTED, 0, 0, reason, now());
				if (uncertain)
				{
					_uncertain.increment();
				}
				save(stored, next);
				yield 1;
			}
		};
	}

	private StoredExecution observeSubmitted(StoredExecution stored, ExecutionEntry entry)
	{
		if (entry.proposalKey().equals("party.accept"))
		{
			if (!spend(Phase.PARTY_RESPONSE, stored.profileId()))
			{
				return stored;
			}
			final PendingInvitation invitation = exactInvitation(stored.profileId(), entry).orElse(null);
			if (invitation == null)
			{
				return resolveMissingSubmittedInvitation(stored, entry);
			}
			final ResultStatus result = _port.respondToPending(stored.profileId(), invitation, true, entry.planId());
			_partyResponses.increment();
			if ((result == ResultStatus.COMPLETED) || (result == ResultStatus.IDEMPOTENT))
			{
				final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
				if ((current == null) || !owned(entry, current.goal()))
				{
					return failGoalSynchronization(stored, entry);
				}
				final ExecutionEntry next = entry.withResult(_catalog.render("party.accepted", entry.style(), null), "party.accepted").withAction(ActionState.COMPLETED, entry.goalId(), current.goal().revision(), "party.accepted", now());
				return save(stored, next);
			}
			return rejectSubmittedGoal(stored, entry, "party.stale");
		}
		if (!spend(Phase.GOAL_OBSERVE, stored.profileId()))
		{
			return stored;
		}
		final StoredGoal goal = _goals.load(stored.profileId()).orElse(null);
		if ((goal == null) || !owned(entry, goal.goal()))
		{
			final ExecutionEntry next = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withAction(ActionState.UNCERTAIN, entry.goalId(), entry.goalRevision(), "execution.failed", now());
			_uncertain.increment();
			return save(stored, next);
		}
		if (goal.goal().status() != PhantomGoalStatus.ACTIVE)
		{
			final ExecutionEntry next = entry.withAction(goal.goal().status() == PhantomGoalStatus.COMPLETED ? ActionState.COMPLETED : ActionState.REJECTED, entry.goalId(), goal.goal().revision(), entry.reasonKey(), now());
			return save(stored, next);
		}
		return synchronizeSubmittedGoal(stored, entry, goal.goal());
	}

	private StoredExecution resolveMissingSubmittedInvitation(StoredExecution stored, ExecutionEntry entry)
	{
		if (!spend(Phase.GOAL_OBSERVE, stored.profileId()))
		{
			return stored;
		}
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		final ResultStatus reconciliation = _port.reconcileInvitation(stored.profileId(), entry);
		if ((reconciliation == ResultStatus.COMPLETED) && (current != null) && owned(entry, current.goal()))
		{
			final ExecutionEntry resolved = entry.withResult(_catalog.render("party.accepted", entry.style(), null), "party.accepted").withAction(ActionState.COMPLETED, entry.goalId(), current.goal().revision(), "party.accepted", now());
			return save(stored, resolved);
		}
		if ((reconciliation == ResultStatus.STALE) || (reconciliation == ResultStatus.REJECTED) || ((current != null) && owned(entry, current.goal()) && (current.goal().status() != PhantomGoalStatus.ACTIVE)))
		{
			final boolean completed = (reconciliation != ResultStatus.STALE) && (reconciliation != ResultStatus.REJECTED) && (current != null) && (current.goal().status() == PhantomGoalStatus.COMPLETED);
			final String reason = completed ? "party.accepted" : "party.stale";
			final ExecutionEntry resolved = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(completed ? ActionState.COMPLETED : ActionState.REJECTED, entry.goalId(), current == null ? entry.goalRevision() : current.goal().revision(), reason, now());
			if (!completed && (current != null) && (current.goal().status() == PhantomGoalStatus.ACTIVE) && owned(entry, current.goal()))
			{
				return abandonOwnedGoal(stored, resolved, current);
			}
			return save(stored, resolved);
		}
		if (!hasBudget())
		{
			return stored;
		}
		final ExecutionEntry uncertain = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withAction(ActionState.UNCERTAIN, entry.goalId(), entry.goalRevision(), "execution.failed", now());
		final StoredExecution saved = save(stored, uncertain);
		_uncertain.increment();
		return saved;
	}

	private StoredExecution rejectSubmittedGoal(StoredExecution stored, ExecutionEntry entry, String reason)
	{
		if (!spend(Phase.GOAL_OBSERVE, stored.profileId()))
		{
			return stored;
		}
		final ExecutionEntry rejected = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(ActionState.REJECTED, entry.goalId(), entry.goalRevision(), reason, now());
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		if ((current != null) && (current.goal().status() == PhantomGoalStatus.ACTIVE) && (current.goal().revision() == entry.goalRevision()) && owned(entry, current.goal()))
		{
			return abandonOwnedGoal(stored, rejected, current);
		}
		return save(stored, (current != null) && owned(entry, current.goal()) ? rejected.withGoalRevision(current.goal().revision()) : rejected);
	}

	private StoredExecution submitGoal(StoredExecution stored, ExecutionEntry entry)
	{
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		if ((current != null) && (current.goal().status() == PhantomGoalStatus.ACTIVE))
		{
			if (owned(entry, current.goal()))
			{
				final ExecutionEntry next = entry.withResult(_catalog.render("goal.submitted", entry.style(), null), "goal.submitted").withAction(ActionState.SUBMITTED, current.goal().goalId(), current.goal().revision(), "goal.submitted", now());
				return synchronizeSubmittedGoal(save(stored, next), next, current.goal());
			}
			if (!_port.allowsGoalSupersession(stored.profileId(), entry, current.goal()))
			{
				final ExecutionEntry next = entry.withResult(_catalog.render("goal.busy", entry.style(), null), "goal.busy").withAction(ActionState.REJECTED, 0, 0, "goal.busy", now());
				return save(stored, next);
			}
		}
		final long goalId = goalId(stored.profileId(), entry);
		final GoalPreparation prepared = _port.prepareGoal(stored.profileId(), entry, goalId, now());
		if ((prepared.status() != ResultStatus.COMPLETED) || (prepared.goal() == null))
		{
			final ExecutionEntry next = entry.withResult(_catalog.render("goal.invalid", entry.style(), null), "goal.invalid").withAction(ActionState.REJECTED, 0, 0, "goal.invalid", now());
			return save(stored, next);
		}
		final PhantomGoal goal = (current != null) && (current.goal().status() == PhantomGoalStatus.ACTIVE) ? withRevision(prepared.goal(), current.goal().revision() + 1) : prepared.goal();
		final ExecutionEntry submitted = entry.withResult(_catalog.render("goal.submitted", entry.style(), null), "goal.submitted").withAction(ActionState.SUBMITTED, goal.goalId(), goal.revision(), "goal.submitted", now());
		final ExecutionState nextState = stored.state().replace(submitted);
		final GoalMutationResult result = _store.mutateGoal(stored.profileId(), stored.rowVersion(), nextState, _goals, current == null ? -1 : current.rowVersion(), goal);
		_goalsSubmitted.increment();
		return synchronizeSubmittedGoal(result.execution(), submitted, result.goal().goal());
	}

	private StoredExecution synchronizeSubmittedGoal(StoredExecution stored, ExecutionEntry entry, PhantomGoal goal)
	{
		final SyncStatus status;
		try
		{
			status = Objects.requireNonNull(_goalRuntime.synchronize(stored.profileId(), goal.goalId(), goal.revision()));
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
			return failGoalSynchronization(stored, entry);
		}
		if (status == SyncStatus.FAILED)
		{
			_failures.increment();
			return failGoalSynchronization(stored, entry);
		}
		return stored;
	}

	private static boolean needsTerminalSynchronization(ExecutionEntry entry)
	{
		return (entry.goalId() != 0) && Set.of(ActionState.COMPLETED, ActionState.REJECTED, ActionState.EXPIRED).contains(entry.actionState());
	}

	// Terminal action + exact revision remains durable until the runtime acknowledges it.
	// BUSY/UNAVAILABLE use the existing delayed queue; recovery retries without mutating Goal.
	private long processTerminalGoal(StoredExecution stored, ExecutionEntry entry)
	{
		if (!spend(Phase.GOAL_OBSERVE, stored.profileId()))
		{
			return 1;
		}
		SyncStatus status;
		try
		{
			status = Objects.requireNonNull(_goalRuntime.synchronize(stored.profileId(), entry.goalId(), entry.goalRevision()));
		}
		catch (RuntimeException exception)
		{
			status = SyncStatus.FAILED;
		}
		if ((status == SyncStatus.BUSY) || (status == SyncStatus.UNAVAILABLE))
		{
			return 10;
		}
		if (status == SyncStatus.FAILED)
		{
			_failures.increment();
			stored = failTerminalGoalSynchronization(stored, entry);
			entry = stored.state().entry(entry.planId());
		}
		if (entry.outboundState() == OutboundState.DISPATCHING)
		{
			entry = entry.withOutbound(OutboundState.UNCERTAIN, "execution.failed", now());
			_uncertain.increment();
		}
		if (entry.outboundState() == OutboundState.PREPARED)
		{
			return dispatch(stored, entry) ? 1 : 10;
		}
		final FinalStoreResult terminal = storeFinal(stored, entry);
		return terminal == null ? 1 : terminal.compacted() ? 1 : capacityRetry(stored.state());
	}

	private StoredExecution abandonOwnedGoal(StoredExecution stored, ExecutionEntry terminal, StoredGoal current)
	{
		final PhantomGoal abandoned = current.goal().withStatus(PhantomGoalStatus.ABANDONED);
		final ExecutionEntry pending = terminal.withGoalRevision(abandoned.revision());
		return _store.mutateGoal(stored.profileId(), stored.rowVersion(), stored.state().replace(pending), _goals, current.rowVersion(), abandoned).execution();
	}

	private StoredExecution failTerminalGoalSynchronization(StoredExecution stored, ExecutionEntry entry)
	{
		_uncertain.increment();
		final ExecutionEntry uncertain = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withTerminalSynchronizationFailure("execution.failed");
		return save(stored, uncertain);
	}

	private StoredExecution failGoalSynchronization(StoredExecution stored, ExecutionEntry entry)
	{
		_uncertain.increment();
		final ExecutionEntry uncertain = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withAction(ActionState.UNCERTAIN, entry.goalId(), entry.goalRevision(), "execution.failed", now());
		return save(stored, uncertain);
	}

	private static PhantomGoal withRevision(PhantomGoal goal, long revision)
	{
		return new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), revision, goal.payloadText());
	}

	private boolean dispatch(StoredExecution stored, ExecutionEntry entry)
	{
		if (remainingBudget() < 3)
		{
			return false;
		}
		spend(Phase.OUTBOUND_PREPARE, stored.profileId());
		final ExecutionEntry dispatching = entry.withOutbound(OutboundState.DISPATCHING, entry.reasonKey(), now());
		stored = save(stored, dispatching);
		if (_state != State.RUNNING)
		{
			final ExecutionEntry uncertain = dispatching.withOutbound(OutboundState.UNCERTAIN, "execution.failed", now());
			spend(Phase.OUTBOUND_DISPATCH, stored.profileId());
			final FinalStoreResult storedFinal = storeFinal(stored, uncertain);
			_uncertain.increment();
			return (storedFinal != null) && storedFinal.compacted();
		}
		try
		{
			spend(Phase.OUTBOUND_DISPATCH, stored.profileId());
			final var result = _port.dispatch(stored.profileId(), dispatching);
			_outboundDispatches.increment();
			final boolean sent = (result.status() == ResultStatus.COMPLETED) && result.expectedCounterpartDelivered();
			final boolean uncertain = (result.status() == ResultStatus.UNCERTAIN) || ((result.deliveries() > 0) && !result.expectedCounterpartDelivered());
			final ExecutionEntry terminal = dispatching.withOutbound(sent ? OutboundState.SENT : uncertain ? OutboundState.UNCERTAIN : OutboundState.FAILED, sent ? dispatching.reasonKey() : "outbound.invalid", now());
			if (sent)
			{
				_sent.increment();
			}
			else if (uncertain)
			{
				_uncertain.increment();
			}
			final FinalStoreResult storedFinal = storeFinal(stored, terminal);
			return (storedFinal != null) && storedFinal.compacted();
		}
		catch (RuntimeException exception)
		{
			final ExecutionEntry uncertain = dispatching.withOutbound(OutboundState.UNCERTAIN, "execution.failed", now());
			_uncertain.increment();
			final FinalStoreResult storedFinal = storeFinal(stored, uncertain);
			return (storedFinal != null) && storedFinal.compacted();
		}
	}

	private StoredExecution save(StoredExecution stored, ExecutionEntry entry)
	{
		return _store.save(stored.profileId(), stored.rowVersion(), stored.state().replace(entry));
	}

	private FinalStoreResult storeFinal(StoredExecution stored, ExecutionEntry entry)
	{
		if (!spend(Phase.TERMINAL_STORE, stored.profileId()))
		{
			return null;
		}
		final long replayFloor = Math.max(0, now() - _catalog.limits().replayHorizonMinutes());
		ExecutionState next = stored.state().pruneReceipts(replayFloor).replace(entry);
		if (entry.terminal())
		{
			if (next.receipts().size() >= PhantomConversationExecutionModel.MAX_RECEIPTS)
			{
				StoredExecution retained = stored;
				if (!stored.state().entry(entry.planId()).terminal())
				{
					retained = _store.save(stored.profileId(), stored.rowVersion(), next);
				}
				return new FinalStoreResult(retained, false);
			}
			next = next.compact(entry.planId());
		}
		final StoredExecution saved = _store.save(stored.profileId(), stored.rowVersion(), next);
		if (entry.terminal())
		{
			_terminalReceipts.increment();
		}
		return new FinalStoreResult(saved, true);
	}

	private ExecutionEntry expire(ExecutionEntry entry)
	{
		ExecutionEntry next = entry;
		if (Set.of(ActionState.PREPARED, ActionState.SUBMITTED).contains(next.actionState()))
		{
			next = next.withAction(ActionState.EXPIRED, next.goalId(), next.goalRevision(), "execution.expired", now());
		}
		if (next.outboundState() == OutboundState.PREPARED)
		{
			next = next.withOutbound(OutboundState.EXPIRED, "execution.expired", now());
		}
		return next.withResult(_catalog.render("execution.expired", next.style(), null), "execution.expired");
	}

	private StoredExecution expireOwnedGoal(StoredExecution stored, ExecutionEntry expired)
	{
		if ((expired.goalId() == 0) || (expired.actionState() != ActionState.EXPIRED))
		{
			return save(stored, expired);
		}
		if (remainingBudget() < 2)
		{
			return stored;
		}
		spend(Phase.GOAL_OBSERVE, stored.profileId());
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		spend(Phase.TERMINAL_STORE, stored.profileId());
		if ((current != null) && owned(expired, current.goal()))
		{
			if ((current.goal().status() == PhantomGoalStatus.ACTIVE) && (current.goal().revision() == expired.goalRevision()))
			{
				return abandonOwnedGoal(stored, expired, current);
			}
			expired = expired.withGoalRevision(current.goal().revision());
		}
		return save(stored, expired);
	}

	private Optional<PendingInvitation> exactInvitation(long profileId, ExecutionEntry entry)
	{
		final InvitationBinding binding = entry.invitationBinding();
		if (binding == null)
		{
			return Optional.empty();
		}
		return _port.pendingInvitation(profileId).filter(invitation -> (invitation.sequence() == binding.sequence()) && (invitation.requesterObjectId() == binding.requesterObjectId()) && (invitation.inviteeObjectId() == binding.inviteeObjectId()));
	}

	private boolean owned(ExecutionEntry entry, PhantomGoal goal)
	{
		if ((goal.goalId() != entry.goalId()) && (entry.goalId() != 0))
		{
			return false;
		}
		final boolean membershipAccept = entry.proposalKey().equals("party.accept") && goal.reasonKey().equals("party.membership.committed");
		if (!goal.purposeKey().equals("conversation.action") || (!goal.reasonKey().equals("conversation." + entry.proposalKey()) && !membershipAccept))
		{
			return false;
		}
		for (int index = 0; index < 4; index++)
		{
			final long expected = Long.parseUnsignedLong(entry.planId().substring(index * 16, (index + 1) * 16), 16);
			if (!Objects.equals(goal.constraints().get("conversation.plan." + index), expected))
			{
				return false;
			}
		}
		if (entry.invitationBinding() != null)
		{
			final InvitationBinding binding = entry.invitationBinding();
			if (!Objects.equals(goal.constraints().get("party.invitation"), binding.sequence()) || !Objects.equals(goal.constraints().get("party.requester"), (long) binding.requesterObjectId()) || !Objects.equals(goal.constraints().get("party.invitee"), (long) binding.inviteeObjectId()))
			{
				return false;
			}
		}
		return true;
	}

	private long goalId(long profileId, ExecutionEntry entry)
	{
		final String hash = PhantomConversationModel.sha256(entry.planId() + '|' + profileId + '|' + entry.proposalKey());
		final long value = Long.parseUnsignedLong(hash.substring(0, 15), 16);
		return value == 0 ? 1 : value;
	}

	private void signal(long profileId)
	{
		if (!_membership.add(profileId))
		{
			return;
		}
		if (!_ready.offer(profileId))
		{
			_membership.remove(profileId);
			_signalDrops.increment();
			_recoveryReset.set(true);
		}
	}

	private void schedule(long profileId, long delay)
	{
		if (!_membership.add(profileId))
		{
			return;
		}
		synchronized (_dueMonitor)
		{
			final boolean capacityWait = delay < 0;
			final long dueMinute = capacityWait ? (-delay) - 1 : now();
			final long duePulse = capacityWait ? _pulse + 1 : _pulse + delay;
			_delayed.add(new DueProfile(dueMinute, duePulse, ++_dueSequence, profileId));
		}
	}

	private boolean promoteOneDue()
	{
		DueProfile due;
		synchronized (_dueMonitor)
		{
			if (_delayed.isEmpty() || (_delayed.peek().minute() > now()) || (_delayed.peek().pulse() > _pulse))
			{
				return false;
			}
			due = _delayed.peek();
		}
		if (!spend(Phase.DELAY_PROMOTE, due.profileId()))
		{
			return false;
		}
		synchronized (_dueMonitor)
		{
			if (!_delayed.remove(due))
			{
				return true;
			}
		}
		if (!_ready.offer(due.profileId()))
		{
			_membership.remove(due.profileId());
			_signalDrops.increment();
			_recoveryReset.set(true);
		}
		return true;
	}

	private void resetRecoveryIfRequested()
	{
		if (_recoveryReset.getAndSet(false))
		{
			_recoveryCursor = 0;
			_recoveryDone = false;
			_recoveryPage = List.of();
			_recoveryPageIndex = 0;
		}
	}

	private long now()
	{
		return Math.max(0, _clock.getAsLong());
	}

	private boolean hasBudget()
	{
		return _pulseOperations < _catalog.limits().operationsPerPulse();
	}

	private int remainingBudget()
	{
		return _catalog.limits().operationsPerPulse() - _pulseOperations;
	}

	private boolean spend(Phase phase, long profileId)
	{
		if (!hasBudget())
		{
			return false;
		}
		_pulseOperations++;
		_phaseObserver.beforeBoundary(phase, profileId);
		return true;
	}

	private static String queryReason(ResultStatus status)
	{
		return switch (status)
		{
			case COMPLETED, IDEMPOTENT -> "query.ok";
			case AMBIGUOUS -> "query.ambiguous";
			case NOT_FOUND, STALE -> "query.not_found";
			case REJECTED, UNCERTAIN -> "execution.failed";
		};
	}

	private ExecutionEntry selectEntry(ExecutionState state)
	{
		final long replayFloor = Math.max(0, now() - _catalog.limits().replayHorizonMinutes());
		final int liveReceipts = (int) state.receipts().stream().filter(receipt -> receipt.terminalMinute() >= replayFloor).count();
		return state.entries().stream().min(Comparator.comparingInt((ExecutionEntry entry) -> executionPriority(entry, liveReceipts)).thenComparingLong(ExecutionEntry::createdMinute).thenComparing(ExecutionEntry::planId)).orElseThrow();
	}

	private static int executionPriority(ExecutionEntry entry, int liveReceipts)
	{
		if (entry.outboundState() == OutboundState.DISPATCHING)
		{
			return 0;
		}
		if (entry.terminal() && !needsTerminalSynchronization(entry) && (liveReceipts < PhantomConversationExecutionModel.MAX_RECEIPTS))
		{
			return 1;
		}
		if (entry.actionState() == ActionState.PREPARED)
		{
			return 2;
		}
		if (needsTerminalSynchronization(entry))
		{
			return 4;
		}
		if (entry.outboundState() == OutboundState.PREPARED)
		{
			return 3;
		}
		if (entry.actionState() == ActionState.SUBMITTED)
		{
			return 4;
		}
		return entry.terminal() ? 5 : 6;
	}

	private long capacityRetry(ExecutionState state)
	{
		final long horizon = _catalog.limits().replayHorizonMinutes();
		final long earliest = state.receipts().stream().mapToLong(receipt -> receipt.terminalMinute() > (Long.MAX_VALUE - horizon) ? Long.MAX_VALUE : receipt.terminalMinute() + horizon).min().orElse(now() + 1);
		final long notBefore = Math.max(now() + 1, earliest);
		return notBefore == Long.MAX_VALUE ? -Long.MAX_VALUE : -(notBefore + 1);
	}

	private record DueProfile(long minute, long pulse, long sequence, long profileId) implements Comparable<DueProfile>
	{
		@Override
		public int compareTo(DueProfile other)
		{
			final int byMinute = Long.compare(minute, other.minute);
			if (byMinute != 0)
			{
				return byMinute;
			}
			final int byPulse = Long.compare(pulse, other.pulse);
			return byPulse != 0 ? byPulse : Long.compare(sequence, other.sequence);
		}
	}

	private record FinalStoreResult(StoredExecution stored, boolean compacted)
	{
	}
}
