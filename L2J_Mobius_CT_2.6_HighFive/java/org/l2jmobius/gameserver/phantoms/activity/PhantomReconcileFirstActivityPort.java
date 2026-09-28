/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

/** Gates scheduler materialization on the existing durable background cursor. */
public final class PhantomReconcileFirstActivityPort implements PhantomActivityMaterializationPort
{
	private final PhantomActivityMaterializationPort _delegate;
	private volatile LongPredicate _reconcile;
	private volatile LongPredicate _retainNativeVisible;
	private volatile LongPredicate _reclaimSoft;
	private volatile LongUnaryOperator _softCandidate;

	public PhantomReconcileFirstActivityPort(PhantomActivityMaterializationPort delegate)
	{
		_delegate = Objects.requireNonNull(delegate, "Materialization delegate must not be null.");
	}

	public synchronized void install(LongPredicate reconcile)
	{
		if (_reconcile != null)
		{
			throw new IllegalStateException("Materialization reconciliation can only be installed once.");
		}
		_reconcile = Objects.requireNonNull(reconcile, "Materialization reconciliation must not be null.");
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
