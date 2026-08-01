/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

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
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.GoalPreparation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.PendingInvitation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.ResultStatus;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.GoalMutationResult;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.StoredExecution;
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
		this(catalog, store, goals, port, () -> System.currentTimeMillis() / 60000L, PhaseObserver.NONE);
	}

	public PhantomConversationExecutionService(PhantomConversationExecutionCatalog catalog, PhantomConversationExecutionStore store, PhantomGoalStateStore goals, PhantomConversationExecutionPort port, LongSupplier clock, PhaseObserver phaseObserver)
	{
		_catalog = Objects.requireNonNull(catalog);
		_store = Objects.requireNonNull(store);
		_goals = Objects.requireNonNull(goals);
		_port = Objects.requireNonNull(port);
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
						final int delay = process(profileId);
						if (delay > 0)
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

	private int process(long profileId)
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
			ExecutionEntry entry = stored.state().entries().getFirst();
			if (entry.outboundState() == OutboundState.DISPATCHING)
			{
				entry = entry.withOutbound(OutboundState.UNCERTAIN, "execution.failed", now());
				final FinalStoreResult terminal = storeFinal(stored, entry);
				if ((terminal == null) || !terminal.compacted())
				{
					return 1;
				}
				_uncertain.increment();
				return entry.actionState() == ActionState.SUBMITTED ? 10 : 0;
			}
			if (now() >= entry.expiryMinute())
			{
				entry = expire(entry);
				final ExpiryOutcome expiry = expireOwnedGoal(stored, entry);
				if (expiry == ExpiryOutcome.BUDGET)
				{
					return 1;
				}
				if (expiry == ExpiryOutcome.NOT_OWNED)
				{
					final FinalStoreResult terminal = storeFinal(stored, entry);
					if ((terminal == null) || !terminal.compacted())
					{
						return 1;
					}
				}
				return 0;
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
			if (entry.outboundState() == OutboundState.PREPARED)
			{
				return dispatch(stored, entry) ? (entry.actionState() == ActionState.SUBMITTED ? 10 : 0) : 1;
			}
			if (entry.terminal())
			{
				final FinalStoreResult terminal = storeFinal(stored, entry);
				return (terminal != null) && terminal.compacted() ? 0 : 1;
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
				final ExecutionEntry next = entry.withResult(_catalog.render(reason, entry.style(), result.facts()), reason).withAction(result.status() == ResultStatus.REJECTED ? ActionState.REJECTED : ActionState.COMPLETED, 0, 0, reason, now());
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
				if (entry.proposalKey().equals("party.accept"))
				{
					if (exactInvitation(stored.profileId(), entry).isEmpty())
					{
						final ExecutionEntry next = entry.withResult(_catalog.render("party.stale", entry.style(), null), "party.stale").withAction(ActionState.REJECTED, 0, 0, "party.stale", now());
						save(stored, next);
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
				final ResultStatus result = invitation == null ? ResultStatus.STALE : _port.respondToPending(stored.profileId(), invitation, false, entry.planId());
				_partyResponses.increment();
				final String reason = result == ResultStatus.COMPLETED || result == ResultStatus.IDEMPOTENT ? "party.refused" : "party.stale";
				final ExecutionEntry next = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(result == ResultStatus.COMPLETED || result == ResultStatus.IDEMPOTENT ? ActionState.COMPLETED : ActionState.REJECTED, 0, 0, reason, now());
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
				final ExecutionEntry next = entry.withResult(_catalog.render("party.accepted", entry.style(), null), "party.accepted").withAction(ActionState.COMPLETED, entry.goalId(), entry.goalRevision(), "party.accepted", now());
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
		return stored;
	}

	private StoredExecution resolveMissingSubmittedInvitation(StoredExecution stored, ExecutionEntry entry)
	{
		if (!spend(Phase.GOAL_OBSERVE, stored.profileId()))
		{
			return stored;
		}
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		if ((current != null) && owned(entry, current.goal()) && (current.goal().status() != PhantomGoalStatus.ACTIVE))
		{
			final boolean completed = current.goal().status() == PhantomGoalStatus.COMPLETED;
			final String reason = completed ? "party.accepted" : "party.stale";
			final ExecutionEntry resolved = entry.withResult(_catalog.render(reason, entry.style(), null), reason).withAction(completed ? ActionState.COMPLETED : ActionState.REJECTED, entry.goalId(), current.goal().revision(), reason, now());
			return save(stored, resolved);
		}
		if (!hasBudget())
		{
			return stored;
		}
		final ExecutionEntry uncertain = entry.withResult(_catalog.render("execution.failed", entry.style(), null), "execution.failed").withAction(ActionState.UNCERTAIN, entry.goalId(), entry.goalRevision(), "execution.failed", now()).withOutbound(OutboundState.FAILED, "execution.failed", now());
		final FinalStoreResult terminal = storeFinal(stored, uncertain);
		_uncertain.increment();
		return terminal == null ? stored : terminal.stored();
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
			return _store.mutateGoal(stored.profileId(), stored.rowVersion(), stored.state().replace(rejected), _goals, current.rowVersion(), current.goal().withStatus(PhantomGoalStatus.ABANDONED)).execution();
		}
		return save(stored, rejected);
	}

	private StoredExecution submitGoal(StoredExecution stored, ExecutionEntry entry)
	{
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		if ((current != null) && (current.goal().status() == PhantomGoalStatus.ACTIVE))
		{
			if (owned(entry, current.goal()))
			{
				final ExecutionEntry next = entry.withResult(_catalog.render("goal.submitted", entry.style(), null), "goal.submitted").withAction(ActionState.SUBMITTED, current.goal().goalId(), current.goal().revision(), "goal.submitted", now());
				return save(stored, next);
			}
			final ExecutionEntry next = entry.withResult(_catalog.render("goal.busy", entry.style(), null), "goal.busy").withAction(ActionState.REJECTED, 0, 0, "goal.busy", now());
			return save(stored, next);
		}
		final long goalId = goalId(stored.profileId(), entry);
		final GoalPreparation prepared = _port.prepareGoal(stored.profileId(), entry, goalId, now());
		if ((prepared.status() != ResultStatus.COMPLETED) || (prepared.goal() == null))
		{
			final ExecutionEntry next = entry.withResult(_catalog.render("goal.invalid", entry.style(), null), "goal.invalid").withAction(ActionState.REJECTED, 0, 0, "goal.invalid", now());
			return save(stored, next);
		}
		final ExecutionEntry submitted = entry.withResult(_catalog.render("goal.submitted", entry.style(), null), "goal.submitted").withAction(ActionState.SUBMITTED, prepared.goal().goalId(), prepared.goal().revision(), "goal.submitted", now());
		final ExecutionState nextState = stored.state().replace(submitted);
		final GoalMutationResult result = _store.mutateGoal(stored.profileId(), stored.rowVersion(), nextState, _goals, current == null ? -1 : current.rowVersion(), prepared.goal());
		_goalsSubmitted.increment();
		return result.execution();
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
			final boolean sent = (result.status() == ResultStatus.COMPLETED) && (result.deliveries() > 0);
			final ExecutionEntry terminal = dispatching.withOutbound(sent ? OutboundState.SENT : OutboundState.FAILED, sent ? dispatching.reasonKey() : "outbound.invalid", now());
			if (sent)
			{
				_sent.increment();
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

	private ExpiryOutcome expireOwnedGoal(StoredExecution stored, ExecutionEntry expired)
	{
		if ((expired.goalId() == 0) || (expired.actionState() != ActionState.EXPIRED))
		{
			return ExpiryOutcome.NOT_OWNED;
		}
		if (remainingBudget() < 2)
		{
			return ExpiryOutcome.BUDGET;
		}
		spend(Phase.GOAL_OBSERVE, stored.profileId());
		final StoredGoal current = _goals.load(stored.profileId()).orElse(null);
		if ((current == null) || (current.goal().status() != PhantomGoalStatus.ACTIVE) || (current.goal().revision() != expired.goalRevision()) || !owned(expired, current.goal()))
		{
			return ExpiryOutcome.NOT_OWNED;
		}
		spend(Phase.TERMINAL_STORE, stored.profileId());
		final long replayFloor = Math.max(0, now() - _catalog.limits().replayHorizonMinutes());
		ExecutionState next = stored.state().pruneReceipts(replayFloor).replace(expired);
		final boolean compacted = expired.terminal() && (next.receipts().size() < PhantomConversationExecutionModel.MAX_RECEIPTS);
		if (compacted)
		{
			next = next.compact(expired.planId());
		}
		_store.mutateGoal(stored.profileId(), stored.rowVersion(), next, _goals, current.rowVersion(), current.goal().withStatus(PhantomGoalStatus.ABANDONED));
		if (compacted)
		{
			_terminalReceipts.increment();
		}
		return ExpiryOutcome.MUTATED;
	}

	private Optional<PendingInvitation> exactInvitation(long profileId, ExecutionEntry entry)
	{
		return _port.pendingInvitation(profileId).filter(invitation -> invitation.requester().equals(entry.counterpart()));
	}

	private boolean owned(ExecutionEntry entry, PhantomGoal goal)
	{
		if ((goal.goalId() != entry.goalId()) && (entry.goalId() != 0))
		{
			return false;
		}
		if (!goal.purposeKey().equals("conversation.action") || !goal.reasonKey().equals("conversation." + entry.proposalKey()))
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

	private void schedule(long profileId, int delay)
	{
		if (!_membership.add(profileId))
		{
			return;
		}
		synchronized (_dueMonitor)
		{
			_delayed.add(new DueProfile(_pulse + delay, ++_dueSequence, profileId));
		}
	}

	private boolean promoteOneDue()
	{
		DueProfile due;
		synchronized (_dueMonitor)
		{
			if (_delayed.isEmpty() || (_delayed.peek().pulse() > _pulse))
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
			case REJECTED -> "execution.failed";
		};
	}

	private record DueProfile(long pulse, long sequence, long profileId) implements Comparable<DueProfile>
	{
		@Override
		public int compareTo(DueProfile other)
		{
			final int byPulse = Long.compare(pulse, other.pulse);
			return byPulse != 0 ? byPulse : Long.compare(sequence, other.sequence);
		}
	}

	private record FinalStoreResult(StoredExecution stored, boolean compacted)
	{
	}

	private enum ExpiryOutcome
	{
		NOT_OWNED,
		MUTATED,
		BUDGET
	}
}
