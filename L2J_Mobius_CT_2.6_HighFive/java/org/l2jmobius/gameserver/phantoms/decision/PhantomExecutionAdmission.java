/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.LongFunction;

/** Bounded admission barrier owned by the existing Decision/native lifetime, without a timer or worker. */
public final class PhantomExecutionAdmission
{
	private final Map<Long, Slot> _slots = new HashMap<>();
	private final int _limit;
	private final ThreadLocal<Scope> _context = new ThreadLocal<>();
	private long _publicationSequence;
	public record Stamp(long profileId, long generation, PhantomDomainRef owner)
	{
		public Stamp { if (profileId <= 0 || generation <= 0) { throw new IllegalArgumentException("Invalid captured execution grant."); } Objects.requireNonNull(owner); }
	}
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
			_slots.put(id, new Slot(rowVersion, state, nextPublication())); return;
		}
		if (rowVersion < slot.version || rowVersion == slot.version && !slot.state.equals(state) || state.generation() < slot.state.generation())
		{ throw new java.util.ConcurrentModificationException("Stale execution admission publication."); }
		if (rowVersion == slot.version) { return; }
		if (state.generation() != slot.state.generation() && slot.running != 0)
		{ slot.uncertain = true; throw new IllegalStateException("Execution transfer attempted before quiescence."); }
		if (state.generation() != slot.state.generation() || state.phase() == PhantomExecutionHandoff.Phase.ACTIVE && slot.state.phase() != PhantomExecutionHandoff.Phase.ACTIVE)
		{ slot.activatedAt = nextPublication(); }
		slot.version = rowVersion; slot.state = state;
	}
	private long nextPublication() { _publicationSequence = Math.incrementExact(_publicationSequence); return _publicationSequence; }
	public synchronized boolean permits(long id, PhantomDomainRef owner)
	{
		final var slot = _slots.get(id);
		return slot != null && !slot.uncertain && !slot.boundary && slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE && slot.state.owner().equals(owner);
	}
	public synchronized Permit tryAcquire(long id, PhantomDomainRef owner)
	{
		if (!permits(id, owner)) { return null; }
		final var slot = _slots.get(id); slot.running++;
		return new Permit(id, slot, slot.state.generation(), owner);
	}
	/** Explicit inherited authority never falls back to a fresh owner when stale. */
	public Permit tryAcquireScoped(long id, Stamp inherited)
	{
		if (inherited != null) { return acquireCaptured(id, inherited); }
		final var scope = _context.get();
		if (scope == null || scope.closed) { return null; }
		// Domain resolution may take another owner's monitor; never invoke it under the admission monitor.
		final PhantomDomainRef expected;
		try { expected = scope.stamp == null ? scope.owner.apply(id) : scope.stamp.owner(); }
		catch (RuntimeException unavailable) { return null; }
		synchronized (this)
		{
			final var slot = _slots.get(id);
			if (slot == null || scope.startedAt < slot.activatedAt) { return null; }
			return scope.stamp != null ? acquireCaptured(id, scope.stamp) : tryAcquire(id, expected);
		}
	}
	private synchronized Permit acquireCaptured(long id, Stamp stamp)
	{
		final var slot = _slots.get(id);
		return id == stamp.profileId() && slot != null && slot.state.generation() == stamp.generation() ? tryAcquire(id, stamp.owner()) : null;
	}
	public Scope enterOwner(LongFunction<PhantomDomainRef> owner) { return new Scope(Objects.requireNonNull(owner), null); }
	public Scope enterCaptured(Stamp stamp) { return new Scope(null, Objects.requireNonNull(stamp)); }
	public final class Scope implements AutoCloseable
	{
		private final LongFunction<PhantomDomainRef> owner;
		private final Stamp stamp;
		private final Thread thread = Thread.currentThread();
		private final Scope previous;
		private final long startedAt;
		private boolean closed;
		private Scope(LongFunction<PhantomDomainRef> owner, Stamp stamp)
		{
			this.owner = owner; this.stamp = stamp; previous = _context.get(); _context.set(this);
			synchronized (PhantomExecutionAdmission.this) { startedAt = _publicationSequence; }
		}
		@Override public void close()
		{
			if (closed) { return; }
			if (Thread.currentThread() != thread || _context.get() != this) { throw new IllegalStateException("Execution scope order/thread mismatch."); }
			closed = true;
			if (previous == null) { _context.remove(); } else { _context.set(previous); }
		}
	}
	public PhantomDecisionEngine.DecisionAdmission decisionAdmission(PhantomDomainRef owner)
	{
		Objects.requireNonNull(owner);
		return new PhantomDecisionEngine.DecisionAdmission()
		{
			@Override public boolean permits(long id) { return PhantomExecutionAdmission.this.permits(id, owner); }
			@Override public PhantomDecisionEngine.AdmissionLease tryAcquire(long id)
			{
				final var permit = PhantomExecutionAdmission.this.tryAcquire(id, owner);
				if (permit == null) { return null; }
				final var scope = enterCaptured(permit.stamp());
				return new PhantomDecisionEngine.AdmissionLease()
				{
					@Override public boolean valid() { return permit.valid(); }
					@Override public long executionGeneration() { return permit.executionGeneration(); }
					@Override public void close() { try { scope.close(); } finally { permit.close(); } }
				};
			}
		};
	}

	/** Close conflicting admission BEFORE issuing REQUEST SQL; earned work may still be in flight. */
	public <T> T requesting(long id, long expectedRowVersion, Supplier<T> writeRequest)
	{ return boundary(id, expectedRowVersion, writeRequest, false); }

	/** Enclose the existing native checkpoint here, after releasing the caller's own ACTION. */
	public <T> T quiescent(long id, long expectedRowVersion, Supplier<T> checkpointAndCas)
	{ return boundary(id, expectedRowVersion, checkpointAndCas, true); }

	private <T> T boundary(long id, long expectedRowVersion, Supplier<T> action, boolean quiescent)
	{
		final Slot slot;
		synchronized (this)
		{
			slot = _slots.get(id);
			if (slot == null || slot.version != expectedRowVersion || quiescent && slot.running != 0 || slot.boundary || slot.uncertain
				|| (quiescent ? slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE
					: slot.state.phase() != PhantomExecutionHandoff.Phase.ACTIVE && slot.state.phase() != PhantomExecutionHandoff.Phase.RECOVERING))
			{ throw new IllegalStateException("Execution boundary is not quiescent/current."); }
			slot.boundary = true;
		}
		boolean acknowledged = false;
		try
		{
			final T result = action.get();
			synchronized (this)
			{
				if (!quiescent && (slot.version <= expectedRowVersion || slot.state.phase() != PhantomExecutionHandoff.Phase.REQUESTED))
				{ throw new IllegalStateException("Execution REQUEST was not durably published."); }
			}
			acknowledged = true; return result;
		}
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
		_slots.get(id).activatedAt = nextPublication();
		_slots.get(id).uncertain = false;
	}
	public final class Permit implements PhantomDecisionEngine.AdmissionLease
	{
		private final Slot _slot;
		private final long _profileId;
		private final long _generation;
		private final PhantomDomainRef _owner;
		private boolean _closed;
		private Permit(long id, Slot slot, long generation, PhantomDomainRef owner) { _profileId = id; _slot = slot; _generation = generation; _owner = owner; }
		public Stamp stamp() { return new Stamp(_profileId, _generation, _owner); }
		@Override public boolean valid()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				return !_closed && !_slot.uncertain && !_slot.boundary && _slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE
					&& _slot.state.generation() == _generation && _slot.state.owner().equals(_owner);
			}
		}
		@Override public long executionGeneration() { return _generation; }
		@Override public void close()
		{
			synchronized (PhantomExecutionAdmission.this)
			{ if (!_closed) { _closed = true; _slot.running--; } }
		}
	}
	private static final class Slot
	{
		private long version; private PhantomExecutionHandoff state; private int running; private boolean boundary; private boolean uncertain;
		private long activatedAt;
		private Slot(long version, PhantomExecutionHandoff state, long activatedAt) { this.version = version; this.state = state; this.activatedAt = activatedAt; }
	}
}
