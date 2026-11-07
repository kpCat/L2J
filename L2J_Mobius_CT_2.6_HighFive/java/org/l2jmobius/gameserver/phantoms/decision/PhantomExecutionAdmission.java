/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded admission barrier owned by the existing Decision/native lifetime, without a timer or worker. */
public final class PhantomExecutionAdmission
{
	private final Map<Long, Slot> _slots = new HashMap<>();
	private final int _limit;
	public PhantomExecutionAdmission(int limit)
	{
		if (limit < 1) { throw new IllegalArgumentException("Execution admission limit must be positive."); }
		_limit = limit;
	}

	/** Publish only an acknowledged durable CAS/read. Pending phases never grant new execution. */
	public synchronized void publish(long id, long rowVersion, PhantomExecutionHandoff state)
	{
		if (id <= 0 || rowVersion < 0) { throw new IllegalArgumentException("Invalid execution snapshot."); }
		Objects.requireNonNull(state);
		final var slot = _slots.get(id);
		if (slot == null)
		{
			if (_slots.size() >= _limit) { throw new IllegalStateException("Execution admission capacity reached."); }
			_slots.put(id, new Slot(rowVersion, state)); return;
		}
		if (rowVersion < slot.version || rowVersion == slot.version && !slot.state.equals(state))
		{ throw new java.util.ConcurrentModificationException("Stale execution admission publication."); }
		if (state.generation() != slot.state.generation() && slot.running != 0)
		{ slot.uncertain = true; throw new IllegalStateException("Execution transfer attempted before quiescence."); }
		slot.version = rowVersion; slot.state = state;
	}
	public synchronized boolean permits(long id, PhantomDomainRef owner)
	{
		final var slot = _slots.get(id);
		return slot != null && !slot.uncertain && !slot.boundary && slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE && slot.state.owner().equals(owner);
	}
	public synchronized Permit tryAcquire(long id, PhantomDomainRef owner)
	{
		if (!permits(id, owner)) { return null; }
		final var slot = _slots.get(id); slot.running++;
		return new Permit(slot, slot.state.generation(), owner);
	}
	public PhantomDecisionEngine.DecisionAdmission decisionAdmission(PhantomDomainRef owner)
	{
		Objects.requireNonNull(owner);
		return new PhantomDecisionEngine.DecisionAdmission()
		{
			@Override public boolean permits(long id) { return PhantomExecutionAdmission.this.permits(id, owner); }
			@Override public PhantomDecisionEngine.AdmissionLease tryAcquire(long id) { return PhantomExecutionAdmission.this.tryAcquire(id, owner); }
		};
	}

	/** Caller wraps this in the existing native checkpoint, after releasing its own ACTION. */
	public <T> T quiescent(long id, long expectedRowVersion, Supplier<T> checkpointAndCas)
	{
		final Slot slot;
		synchronized (this)
		{
			slot = _slots.get(id);
			if (slot == null || slot.version != expectedRowVersion || slot.running != 0 || slot.boundary || slot.uncertain || slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE)
			{ throw new IllegalStateException("Execution boundary is not quiescent/current."); }
			slot.boundary = true;
		}
		boolean acknowledged = false;
		try { final T result = checkpointAndCas.get(); acknowledged = true; return result; }
		finally
		{
			synchronized (this)
			{
				// Unknown writes never automatically reopen either owner; durable read plus native recovery is required.
				if (!acknowledged) { slot.uncertain = true; }
				slot.boundary = false;
			}
		}
	}
	public synchronized void acknowledgeRecovery(long id, long rowVersion, PhantomExecutionHandoff state)
	{
		final var slot = _slots.get(id);
		if (slot != null && (slot.running != 0 || slot.boundary)) { throw new IllegalStateException("Execution recovery still has active work."); }
		publish(id, rowVersion, state);
		_slots.get(id).uncertain = false;
	}
	public final class Permit implements PhantomDecisionEngine.AdmissionLease
	{
		private final Slot _slot;
		private final long _generation;
		private final PhantomDomainRef _owner;
		private boolean _closed;
		private Permit(Slot slot, long generation, PhantomDomainRef owner) { _slot = slot; _generation = generation; _owner = owner; }
		@Override public boolean valid()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				return !_closed && !_slot.uncertain && !_slot.boundary && _slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE
					&& _slot.state.generation() == _generation && _slot.state.owner().equals(_owner);
			}
		}
		@Override public void close()
		{
			synchronized (PhantomExecutionAdmission.this)
			{ if (!_closed) { _closed = true; _slot.running--; } }
		}
	}
	private static final class Slot
	{
		private long version; private PhantomExecutionHandoff state; private int running; private boolean boundary; private boolean uncertain;
		private Slot(long version, PhantomExecutionHandoff state) { this.version = version; this.state = state; }
	}
}
