/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import org.l2jmobius.gameserver.phantoms.activity.PhantomNativeJourneyDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.Stored;

/** Bounded adapter on the existing Scheduler. It selects no Goal and owns no thread, clock or native effect. */
public final class PhantomExecutionControlPort implements PhantomSchedulerControlPort, PhantomNativeJourneyDemand
{
	private final PhantomDecisionEngine _engine;
	private final PhantomExecutionHandoffStore _store;
	private final PhantomExecutionAdmission _gate;
	private final int _limit;
	private final LongFunction<PhantomDomainRef> _desired;
	private final LongFunction<PhantomDomainRef> _recoveryDemand;
	private final LongFunction<PhantomGoal> _roots;
	private final BiPredicate<Long, PhantomDomainRef> _normal;
	private final BiPredicate<Long, String> _candidates;
	private final LongSupplier _clock;
	private final BiConsumer<Long, RuntimeException> _diagnostic;
	private final ConcurrentSkipListMap<Long, Slot> _profiles = new ConcurrentSkipListMap<>();
	private final Set<Long> _working = ConcurrentHashMap.newKeySet();
	private final Set<Long> _nativeNeeded = ConcurrentHashMap.newKeySet();
	private final java.util.concurrent.ConcurrentLinkedQueue<Long> _urgent = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private final Set<Long> _queued = ConcurrentHashMap.newKeySet();
	private PhantomGoalStateStore _publications;
	private TransitionTrace _transitionTrace = (_, _, _, _) -> { };

	@FunctionalInterface
	public interface TransitionTrace
	{
		void beforeRequest(long profileId, PhantomDomainRef previous, PhantomDomainRef requested, String reason);
	}

	public void installTransitionTrace(TransitionTrace trace) { _transitionTrace = Objects.requireNonNull(trace); }
	private long _cursor;
	public void installGoalPublications(PhantomGoalStateStore goals)
	{
		if (_publications != null) { throw new IllegalStateException("Goal publication control already installed."); }
		_publications = Objects.requireNonNull(goals); goals.installExecutionPublication(_store, _gate, _clock, this::wake);
	}
	public void wake(long id)
	{
		watch(id); final var slot = _profiles.get(id); slot.urgent = true; slot.nextDue = 0;
		if (_queued.add(id)) { _urgent.add(id); }
	}
	private final ThreadLocal<java.util.ArrayDeque<Materializing>> _materializing = ThreadLocal.withInitial(java.util.ArrayDeque::new);
	public void request(long id, PhantomDomainRef recipient, long until)
	{
		watch(id); final var slot = _profiles.get(id);
		if (until <= _clock.getAsLong()) { throw new IllegalArgumentException("Execution request has expired."); }
		slot.requestedOwner = Objects.requireNonNull(recipient); slot.requestedUntil = until; wake(id);
	}
	/** The role lease spans every existing lifecycle callback, including failure compensation. */
	public org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort materializationLifecycle(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort delegate)
	{ return materializationLifecycle(delegate, _ -> ""); }
	public org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort materializationLifecycle(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort delegate, java.util.function.LongFunction<String> prepareNormal)
	{
		Objects.requireNonNull(delegate);
		Objects.requireNonNull(prepareNormal);
		return new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long id, int objectId) { beforeMaterialize(id, objectId, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose.NORMAL, ""); }
			@Override public void beforeMaterialize(long id, int objectId, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose purpose, String claim)
			{
				final var attempt = new Materializing(id, objectId); _materializing.get().push(attempt);
				if (purpose == org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationPurpose.NORMAL)
				{
					final String reason = prepareNormal.apply(id);
					if (!reason.isEmpty()) { throw new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException(reason); }
				}
				final var row = ensure(id);
				if (row != null) { attempt.permit = _gate.tryAcquireOwnedCompletion(id, row.state().owner()); }
				if (attempt.permit == null) { throw new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException("execution.materialization_wait_owner"); }
				attempt.delegated = true; delegate.beforeMaterialize(id, objectId, purpose, claim);
			}
			@Override public void afterPlayerLoad(long id, org.l2jmobius.gameserver.model.actor.Player player) { delegate.afterPlayerLoad(id, player); }
			@Override public void beforeWorldSpawn(long id, org.l2jmobius.gameserver.model.actor.Player player) { delegate.beforeWorldSpawn(id, player); }
			@Override public void beforeStore(long id, org.l2jmobius.gameserver.model.actor.Player player) { delegate.beforeStore(id, player); }
			@Override public void afterStore(long id, org.l2jmobius.gameserver.model.actor.Player player) { delegate.afterStore(id, player); }
			@Override public void materializeSucceeded(long id, int objectId) { finish(id, objectId, true); }
			@Override public void materializeAborted(long id, int objectId) { finish(id, objectId, false); }
			private void finish(long id, int objectId, boolean success)
			{
				final var stack = _materializing.get(); final var attempt = stack.peek();
				if (attempt == null || attempt.id != id || attempt.objectId != objectId) { throw new IllegalStateException("Execution materialization callback does not match its attempt/thread."); }
				try { if (attempt.delegated) { if (success) { delegate.materializeSucceeded(id, objectId); } else { delegate.materializeAborted(id, objectId); } } }
				finally { stack.pop(); if (attempt.permit != null) { attempt.permit.close(); } if (stack.isEmpty()) { _materializing.remove(); } }
			}
		};
	}
	public PhantomExecutionControlPort(PhantomDecisionEngine engine, PhantomExecutionHandoffStore store, PhantomExecutionAdmission gate, int limit,
		LongFunction<PhantomDomainRef> desired, LongFunction<PhantomDomainRef> recoveryDemand, LongFunction<PhantomGoal> roots,
		BiPredicate<Long, PhantomDomainRef> normal, BiPredicate<Long, String> candidates, LongSupplier clock, BiConsumer<Long, RuntimeException> diagnostic)
	{
		_engine = Objects.requireNonNull(engine); _store = Objects.requireNonNull(store); _gate = Objects.requireNonNull(gate);
		if (limit < 1 || limit > 10000) { throw new IllegalArgumentException("Execution control cache exceeds the existing profile bound."); }
		_limit = limit; _desired = Objects.requireNonNull(desired); _recoveryDemand = Objects.requireNonNull(recoveryDemand); _roots = Objects.requireNonNull(roots);
		_normal = Objects.requireNonNull(normal); _candidates = Objects.requireNonNull(candidates); _clock = Objects.requireNonNull(clock); _diagnostic = Objects.requireNonNull(diagnostic);
	}
	public void watch(long id)
	{
		if (id <= 0) { throw new IllegalArgumentException("Execution profile must be positive."); }
		synchronized (_profiles)
		{
			if (!_profiles.containsKey(id))
			{
				if (_profiles.size() >= _limit) { throw new IllegalStateException("Execution control profile cache is full."); }
				_profiles.put(id, new Slot());
			}
		}
	}
	/** Materialization may need the accepted owner's completion rights before NORMAL recovery is ready. */
	public Stored ensure(long id)
	{
		watch(id); final var slot = _profiles.get(id);
		if (!_working.add(id)) { return null; }
		try { acknowledge(id, slot); wake(id); return slot.row; }
		catch (RuntimeException failure) { failed(id, slot, failure, _clock.getAsLong()); return null; }
		finally { _working.remove(id); }
	}
	private void acknowledge(long id, Slot slot)
	{
		if (slot.acknowledged && slot.row != null && _gate.current(id, slot.row.component().rowVersion(), slot.row.state())) { return; }
		var actual = _store.load(id).orElse(null);
		if (actual == null) { actual = _store.initialize(id, PhantomExecutionHandoff.solo(id)); }
		// A persisted ACTIVE row is never a fresh process grant. Existing owners recover under
		// their native/absence boundary before either controllers or materialization may use it.
		slot.row = _engine.recoverExecutionAcknowledgement(actual); slot.acknowledged = true;
	}
	public PhantomDomainRef owner(long id)
	{
		final var slot = _profiles.get(id); final var row = slot == null || !slot.acknowledged ? null : slot.row;
		return row == null ? null : row.state().owner();
	}
	public boolean permits(long id)
	{
		final var owner = owner(id);
		return owner != null && _normal.test(id, owner) && _gate.permits(id, owner);
	}
	public PhantomExecutionAdmission.Permit acquire(long id)
	{
		final var owner = owner(id);
		return owner == null || !_normal.test(id, owner) ? null : _gate.tryAcquire(id, owner);
	}
	public PhantomDecisionEngine.DecisionAdmission decisionAdmission()
	{
		return new PhantomDecisionEngine.DecisionAdmission()
		{
			@Override public boolean permits(long id) { return PhantomExecutionControlPort.this.permits(id); }
			@Override public boolean permitsCandidate(long id, String key) { return _candidates.test(id, key); }
			@Override public PhantomDecisionEngine.AdmissionLease tryAcquire(long id)
			{
				final var permit = acquire(id); if (permit == null) { return null; }
				final var scope = permit.enterScope();
				return new PhantomDecisionEngine.AdmissionLease()
				{
					@Override public boolean valid() { return permit.valid() && _normal.test(id, permit.stamp().owner()); }
					@Override public long executionGeneration() { return permit.executionGeneration(); }
					@Override public boolean yieldRequested() { return permit.yieldRequested(); }
					@Override public void close() { try { scope.close(); } finally { permit.close(); } }
				};
			}
		};
	}
	@Override public void onPulse()
	{
		final long now = _clock.getAsLong();
		final var selected = new java.util.LinkedHashMap<Long, Slot>();
		for (int i = 0; i < 4; i++) { final var id = _urgent.poll(); if (id == null) { break; } _queued.remove(id); selected.put(id, _profiles.get(id)); }
		var fair = _profiles.tailMap(_cursor, false).entrySet().stream().limit(8 - selected.size()).toList();
		if (fair.isEmpty()) { _cursor = 0; fair = _profiles.entrySet().stream().limit(8 - selected.size()).toList(); }
		for (var entry : fair) { selected.putIfAbsent(entry.getKey(), entry.getValue()); _cursor = entry.getKey(); }
		final var page = selected.entrySet();
		for (var entry : page)
		{
			final long id = entry.getKey(); final var slot = entry.getValue();
			if (slot.nextDue > now || !_working.add(id)) { if (slot.urgent && _queued.add(id)) { _urgent.add(id); } continue; }
			try
			{
				acknowledge(id, slot); final var row = slot.row;
				final var publication = _publications == null ? null : _publications.pendingPublication(id, now).orElse(null);
				final var debtOwner = _recoveryDemand.apply(id);
				final var requested = now < slot.requestedUntil ? slot.requestedOwner : null;
				final var wanted = Objects.requireNonNullElse(publication != null ? PhantomExecutionHandoff.solo(id) : debtOwner != null ? debtOwner : requested != null ? requested : _desired.apply(id), PhantomExecutionHandoff.solo(id));
				if ((row.state().phase() == Phase.ACTIVE && (!row.state().owner().equals(wanted) || debtOwner != null || publication != null))
					|| row.state().phase() == Phase.RECOVERING && !row.state().owner().equals(PhantomExecutionHandoff.solo(id)) && !row.state().owner().equals(wanted))
				{
					try
					{
						_transitionTrace.beforeRequest(id, row.state().owner(), wanted, publication != null ? "goal_publication:" + publication.state().requestId()
							: debtOwner != null ? "recovery_debt" : requested != null ? "explicit_owner_request" : "logical_owner_changed");
					}
					catch (RuntimeException ignored) { /* Observation cannot veto the execution boundary. */ }
					slot.row = _engine.requestExecution(row, wanted, publication == null ? "execution." + row.state().generation() + "." + row.component().rowVersion() : publication.state().requestId(), now, publication == null ? Math.addExact(now, 30_000) : publication.state().deadline());
					slot.nextDue = now;
				}
				else if (row.state().phase() == Phase.ACTIVE) { slot.nextDue = Math.addExact(now, 1000); }
				else
				{
					final boolean cancel = row.state().phase() != Phase.RECOVERING && (slot.cancelAfterConflict || !wanted.equals(row.state().recipient())
						|| row.state().requestId().startsWith("goal.") && (publication == null || !publication.state().requestId().equals(row.state().requestId())));
					final var next = _engine.advanceExecution(row, now, cancel); slot.row = next.stored(); slot.nextDue = next.nextDue();
					if (next.progressed()) { slot.cancelAfterConflict = false; }
				}
				if (slot.row.state().phase() == Phase.RECOVERING && slot.row.state().requestId().startsWith("goal.")) { _engine.reload(id); }
				slot.root = _roots.apply(id);
				slot.urgent = publication != null || slot.row.state().phase() != Phase.ACTIVE;
				if ((debtOwner != null && slot.row.state().owner().equals(debtOwner) || publication != null && slot.row.state().owner().equals(PhantomExecutionHandoff.solo(id))) && slot.row.state().phase() != Phase.ACTIVE) { _nativeNeeded.add(id); }
				else { _nativeNeeded.remove(id); }
			}
			catch (RuntimeException failure) { failed(id, slot, failure, now); }
			finally { _working.remove(id); if (slot.urgent && _queued.add(id)) { _urgent.add(id); } }
		}
	}
	private void failed(long id, Slot slot, RuntimeException failure, long now)
	{
		slot.acknowledged = slot.row != null && _gate.current(id, slot.row.component().rowVersion(), slot.row.state()); slot.nextDue = Math.addExact(now, 1000);
		if (failure instanceof java.util.ConcurrentModificationException) { slot.cancelAfterConflict = true; }
		if (!failure.getClass().getName().equals(slot.failure) || now >= slot.nextDiagnostic)
		{
			slot.failure = failure.getClass().getName(); slot.nextDiagnostic = Math.addExact(now, 30_000); _diagnostic.accept(id, failure);
		}
	}
	@Override public Set<Long> acceptedProfiles() { return Set.copyOf(_nativeNeeded.stream().limit(128).toList()); }
	@Override public boolean requiresNativeContext(long id, long rootId, long rootRevision)
	{
		final var slot = _profiles.get(id); final var root = slot == null ? null : slot.root;
		return _nativeNeeded.contains(id) && slot.acknowledged && root != null && root.status() == PhantomGoalStatus.ACTIVE
			&& (rootId == 0 || root.goalId() == rootId && root.revision() == rootRevision);
	}
	private static final class Slot
	{
		private volatile Stored row; private volatile PhantomGoal root; private volatile boolean acknowledged;
		private volatile PhantomDomainRef requestedOwner; private volatile long requestedUntil;
		private volatile long nextDue; private boolean cancelAfterConflict; private String failure = ""; private long nextDiagnostic;
		private volatile boolean urgent;
	}
	private static final class Materializing
	{
		private final long id; private final int objectId; private PhantomExecutionAdmission.Permit permit; private boolean delegated;
		private Materializing(long id, int objectId) { this.id = id; this.objectId = objectId; }
	}
}
