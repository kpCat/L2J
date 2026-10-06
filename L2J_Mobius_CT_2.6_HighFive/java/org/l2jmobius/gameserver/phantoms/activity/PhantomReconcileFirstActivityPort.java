/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.function.LongFunction;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;

/** Gates scheduler materialization on the existing durable background cursor. */
public final class PhantomReconcileFirstActivityPort implements PhantomActivityMaterializationPort
{
	private final PhantomActivityMaterializationPort _delegate;
	private volatile LongPredicate _reconcile;
	private volatile LongFunction<TransitionOutcome> _readiness;
	private volatile LongPredicate _retainNativeVisible;
	private volatile LongPredicate _reclaimSoft;
	private volatile LongUnaryOperator _softCandidate;

	public PhantomReconcileFirstActivityPort(PhantomActivityMaterializationPort delegate)
	{
		_delegate = Objects.requireNonNull(delegate, "Materialization delegate must not be null.");
	}

	public synchronized void install(LongPredicate reconcile)
	{
		if ((_reconcile != null) || (_readiness != null))
		{
			throw new IllegalStateException("Materialization reconciliation can only be installed once.");
		}
		_reconcile = Objects.requireNonNull(reconcile, "Materialization reconciliation must not be null.");
	}

	public synchronized void installReadiness(LongFunction<TransitionOutcome> readiness)
	{
		if ((_reconcile != null) || (_readiness != null))
		{
			throw new IllegalStateException("Materialization reconciliation can only be installed once.");
		}
		_readiness = Objects.requireNonNull(readiness);
	}

	public void installPopulationReadiness(PhantomPresenceRegistry presence, PhantomHumanLocalityControl locality, PhantomPopulationEcologyService ecology)
	{
		installReadiness(profileId ->
		{
			final var recorder = PhantomRuntimeFlightRecorder.getInstance();
			final boolean online = presence.isOnline(profileId);
			final boolean local = online && locality.isLocal(profileId);
			if (!online || !local)
			{
				recorder.record(profileId, online ? "READY_NOT_LOCAL" : "READY_PRESENCE_OFFLINE", online ? "ONLINE" : "OFFLINE", "DEFERRED", "presence.no_current_local_demand", 0, 0, 0);
				if (ecology != null) { ecology.withdrawMaterializationDue(profileId); }
				return TransitionOutcome.deferred("presence.no_current_local_demand");
			}
			MaterializationRequest request = MaterializationRequest.normal();
			if (ecology != null)
			{
				final var due = ecology.requestMaterializationDue(profileId);
				recorder.record(profileId, "READY_ECOLOGY_DUE", "LOCAL", due.complete() ? "COMPLETE" : "INCOMPLETE", due.reason(), 0, 0, 0);
				if (!due.complete()) { recorder.record(profileId, "READY_ECOLOGY_DEFER", "LOCAL", "DEFERRED", due.reason(), 0, 0, 0); }
				if (!due.complete()) { return TransitionOutcome.deferred(due.reason()); }
				request = due.request();
			}
			final boolean currentLocal = locality.isCurrentLocal(profileId);
			recorder.record(profileId, currentLocal ? "READY_PASS" : "READY_CURRENT_LOCAL_FALSE", "LOCAL", currentLocal ? "SUCCESS" : "DEFERRED", currentLocal ? "" : "presence.committed_position_not_local", 0, 0, 0);
			return currentLocal ? TransitionOutcome.success(request) : TransitionOutcome.deferred("presence.committed_position_not_local");
		});
	}

	public synchronized void installRetention(LongPredicate retainNativeVisible)
	{
		if (_retainNativeVisible != null)
		{
			throw new IllegalStateException("Native visibility retention can only be installed once.");
		}
		_retainNativeVisible = Objects.requireNonNull(retainNativeVisible, "Native visibility retention must not be null.");
	}

	public synchronized void installSoftReclamation(LongUnaryOperator candidate, LongPredicate reclaimable)
	{
		if (_softCandidate != null)
		{
			throw new IllegalStateException("Soft reclamation can only be installed once.");
		}
		_reclaimSoft = Objects.requireNonNull(reclaimable);
		_softCandidate = Objects.requireNonNull(candidate);
	}

	@Override
	public TransitionOutcome materialize(long profileId)
	{
		final var readiness = _readiness;
		if (readiness != null)
		{
			final var gate = readiness.apply(profileId);
			return gate.outcome() == Outcome.SUCCESS ? _delegate.materialize(profileId, gate.request()) : gate;
		}
		final LongPredicate reconcile = _reconcile;
		return ((reconcile != null) && reconcile.test(profileId)) ? _delegate.materialize(profileId) : TransitionOutcome.deferred();
	}

	@Override
	public TransitionOutcome dematerialize(long profileId)
	{
		final LongPredicate retainNativeVisible = _retainNativeVisible;
		return ((retainNativeVisible != null) && retainNativeVisible.test(profileId)) ? TransitionOutcome.deferred() : _delegate.dematerialize(profileId);
	}

	@Override
	public long softReclaimCandidate(long requestingProfileId)
	{
		final LongUnaryOperator candidate = _softCandidate;
		return candidate == null ? 0 : candidate.applyAsLong(requestingProfileId);
	}

	@Override
	public TransitionOutcome reclaimSoft(long profileId)
	{
		final LongPredicate reclaimable = _reclaimSoft;
		return ((reclaimable != null) && reclaimable.test(profileId)) ? _delegate.dematerialize(profileId) : TransitionOutcome.deferred();
	}

	@Override
	public TransitionOutcome retryCleanup(long profileId)
	{
		return _delegate.retryCleanup(profileId);
	}

	@Override
	public boolean isMaterialized(long profileId)
	{
		return _delegate.isMaterialized(profileId);
	}

	@Override
	public boolean hasLifecycleOwnership(long profileId)
	{
		return _delegate.hasLifecycleOwnership(profileId);
	}
}
