/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;
import java.util.function.LongPredicate;

/** Gates scheduler materialization on the existing durable background cursor. */
public final class PhantomReconcileFirstActivityPort implements PhantomActivityMaterializationPort
{
	private final PhantomActivityMaterializationPort _delegate;
	private volatile LongPredicate _reconcile;

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

	@Override
	public TransitionOutcome materialize(long profileId)
	{
		final LongPredicate reconcile = _reconcile;
		return ((reconcile != null) && reconcile.test(profileId)) ? _delegate.materialize(profileId) : TransitionOutcome.transientBlock();
	}

	@Override
	public TransitionOutcome dematerialize(long profileId)
	{
		return _delegate.dematerialize(profileId);
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
