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
	public enum Purpose { NORMAL, COMPLETION }
	/** Accepted-operation services validate their receipt before using this completion-only entry. */
	public Permit tryAcquireCompletionScoped(long id, Stamp inherited) { return acquireScoped(id, inherited, Purpose.COMPLETION); }
	/** Only the native ACTION holder uses this continuation entry; a timer/queued controller remains fresh work. */
	public Permit tryAcquireContinuationScoped(long id, Stamp inherited) { return acquireScoped(id, inherited, Purpose.NORMAL, true); }
	/** Canonical effect owner validates its exact durable operation key before reacquiring completion rights. */
	public synchronized Permit tryAcquireOwnedCompletion(long id, PhantomDomainRef owner) { return tryAcquire(id, owner, Purpose.COMPLETION); }
	public synchronized Permit tryAcquirePreparation(long id, long rowVersion, PhantomDomainRef owner)
	{
		final var slot = _slots.get(id);
		return slot != null && slot.version == rowVersion ? tryAcquire(id, owner, Purpose.COMPLETION) : null;
	}
	public Scope enterCompletionOwner(LongFunction<PhantomDomainRef> owner) { return new Scope(Objects.requireNonNull(owner), null, Purpose.COMPLETION); }
	private final Map<Long, Slot> _slots = new HashMap<>();
	private final int _limit;
	private final ThreadLocal<Scope> _context = new ThreadLocal<>();
	private long _publicationSequence;
	public record Stamp(long profileId, long generation, PhantomDomainRef owner, long admissionEpoch, Purpose purpose)
	{
		public Stamp { if (profileId <= 0 || generation <= 0 || admissionEpoch <= 0) { throw new IllegalArgumentException("Invalid captured execution grant."); } Objects.requireNonNull(owner); Objects.requireNonNull(purpose); }
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
	{ return permits(id, owner, Purpose.NORMAL); }
	private boolean permits(long id, PhantomDomainRef owner, Purpose purpose)
	{ return permits(id, owner, purpose, false); }
	private boolean permits(long id, PhantomDomainRef owner, Purpose purpose, boolean continuation)
	{
		final var slot = _slots.get(id);
		return slot != null && !slot.uncertain && !slot.boundary && slot.state.owner().equals(owner)
			&& (purpose == Purpose.NORMAL ? slot.state.phase() == PhantomExecutionHandoff.Phase.ACTIVE && (continuation || slot.blocked == 0) : slot.state.ownerMayComplete(owner));
	}
	public synchronized Permit tryAcquire(long id, PhantomDomainRef owner)
	{ return tryAcquire(id, owner, Purpose.NORMAL); }
	private Permit tryAcquire(long id, PhantomDomainRef owner, Purpose purpose)
	{ return tryAcquire(id, owner, purpose, false); }
	private Permit tryAcquire(long id, PhantomDomainRef owner, Purpose purpose, boolean continuation)
	{
		if (!permits(id, owner, purpose, continuation)) { return null; }
		final var slot = _slots.get(id); slot.running++;
		return new Permit(id, slot, slot.state.generation(), owner, slot.activatedAt, purpose);
	}
	/** Explicit inherited authority never falls back to a fresh owner when stale. */
	public Permit tryAcquireScoped(long id, Stamp inherited)
	{ return acquireScoped(id, inherited, Purpose.NORMAL); }
	private Permit acquireScoped(long id, Stamp inherited, Purpose purpose)
	{ return acquireScoped(id, inherited, purpose, false); }
	private Permit acquireScoped(long id, Stamp inherited, Purpose purpose, boolean continuation)
	{
		if (inherited != null) { return acquireCaptured(id, inherited, purpose, continuation); }
		var scope = _context.get();
		while (scope != null && scope.stamp != null && scope.stamp.profileId() != id) { scope = scope.previous; }
		if (scope == null || scope.closed) { return null; }
		if (purpose == Purpose.NORMAL && scope.purpose != Purpose.NORMAL) { return null; }
		// Domain resolution may take another owner's monitor; never invoke it under the admission monitor.
		final PhantomDomainRef expected;
		try { expected = scope.stamp == null ? scope.owner.apply(id) : scope.stamp.owner(); }
		catch (RuntimeException unavailable) { return null; }
		synchronized (this)
		{
			final var slot = _slots.get(id);
			if (slot == null || scope.startedAt < slot.activatedAt) { return null; }
			return scope.stamp != null ? acquireCaptured(id, scope.stamp, purpose, continuation) : tryAcquire(id, expected, purpose, continuation);
		}
	}
	private synchronized Permit acquireCaptured(long id, Stamp stamp, Purpose purpose)
	{ return acquireCaptured(id, stamp, purpose, false); }
	private synchronized Permit acquireCaptured(long id, Stamp stamp, Purpose purpose, boolean continuation)
	{
		final var slot = _slots.get(id);
		return sameGrant(id, slot, stamp) && (purpose != Purpose.NORMAL || stamp.purpose() == Purpose.NORMAL) ? tryAcquire(id, stamp.owner(), purpose, continuation) : null;
	}
	/** Acknowledged durable obligation facts close fresh conflicting actions, preserving already issued completion rights. */
	public synchronized void blocker(long id, String component, long rowVersion, boolean blocked)
	{
		if (component == null || !component.matches("[a-z][a-z0-9_.-]{0,63}")) { throw new IllegalArgumentException("Invalid obligation component type."); }
		if (rowVersion < 0) { throw new IllegalArgumentException("Blocker requires an acknowledged row version."); }
		final var slot = _slots.get(id); if (slot == null) { return; }
		final var old = slot.blockers.get(component);
		if (old != null && old.version() > rowVersion) { return; }
		if (old != null && old.version() == rowVersion && old.blocked() != blocked) { throw new java.util.ConcurrentModificationException("Conflicting obligation fact for one durable row."); }
		if (old == null && slot.blockers.size() >= PhantomExecutionHandoff.MAX_DEPENDENCIES) { throw new IllegalStateException("Too many execution obligation facts."); }
		slot.blocked += (blocked ? 1 : 0) - (old != null && old.blocked() ? 1 : 0);
		slot.blockers.put(component, new Blocker(rowVersion, blocked));
	}
	private record Blocker(long version, boolean blocked) { }
	private static boolean sameGrant(long id, Slot slot, Stamp stamp)
	{ return id == stamp.profileId() && slot != null && slot.state.generation() == stamp.generation() && slot.activatedAt == stamp.admissionEpoch() && slot.state.owner().equals(stamp.owner()); }
	/** Native earned descendants/cancellation retain source rights until YIELD; no new admission is granted here. */
	public synchronized boolean acceptsContinuation(Stamp stamp)
	{
		final var slot = _slots.get(stamp.profileId());
		return sameGrant(stamp.profileId(), slot, stamp) && slot.state.ownerMayComplete(stamp.owner());
	}
	/** Metadata lookup only, safe inside the native monitor; never resolves domain callbacks. */
	public Stamp currentCapturedStamp(long id)
	{
		for (var scope = _context.get(); scope != null; scope = scope.previous)
		{ if (scope.stamp != null && scope.stamp.profileId() == id) { return scope.closed ? null : scope.stamp; } }
		return null;
	}
	public Scope enterOwner(LongFunction<PhantomDomainRef> owner) { return new Scope(Objects.requireNonNull(owner), null, Purpose.NORMAL); }
	public Scope enterCaptured(Stamp stamp) { return new Scope(null, Objects.requireNonNull(stamp), stamp.purpose()); }
	public final class Scope implements AutoCloseable
	{
		private final LongFunction<PhantomDomainRef> owner;
		private final Stamp stamp;
		private final Purpose purpose;
		private final Permit retained;
		private final Thread thread = Thread.currentThread();
		private final Scope previous;
		private final long startedAt;
		private boolean closed;
		private Scope(LongFunction<PhantomDomainRef> owner, Stamp stamp, Purpose purpose)
		{ this(owner, stamp, purpose, null); }
		private Scope(LongFunction<PhantomDomainRef> owner, Stamp stamp, Purpose purpose, Permit retained)
		{
			this.owner = owner; this.stamp = stamp; this.purpose = purpose; this.retained = retained; previous = _context.get(); _context.set(this);
			synchronized (PhantomExecutionAdmission.this) { startedAt = _publicationSequence; }
		}
		@Override public void close()
		{
			if (closed) { return; }
			if (Thread.currentThread() != thread || _context.get() != this) { throw new IllegalStateException("Execution scope order/thread mismatch."); }
			closed = true;
			if (previous == null) { _context.remove(); } else { _context.set(previous); }
			if (retained != null) { retained.close(); }
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
					@Override public boolean yieldRequested() { return permit.yieldRequested(); }
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
	public synchronized boolean quiescentCurrent(long id, long expectedRowVersion)
	{
		final var slot = _slots.get(id);
		return slot != null && slot.version == expectedRowVersion && slot.running == 0 && !slot.boundary && !slot.uncertain
			&& slot.state.phase() != PhantomExecutionHandoff.Phase.ACTIVE;
	}
	public synchronized boolean current(long id, long rowVersion, PhantomExecutionHandoff state)
	{
		final var slot = _slots.get(id);
		return slot != null && slot.version == rowVersion && slot.state.equals(state) && !slot.boundary && !slot.uncertain;
	}

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
	/** A durable REQUEST with the same owner/generation restores completion only, even with earned work in flight. */
	public synchronized boolean acknowledgeRequested(long id, long rowVersion, PhantomExecutionHandoff actual)
	{
		final var slot = _slots.get(id);
		if (slot == null || slot.boundary || rowVersion < slot.version || actual.phase() != PhantomExecutionHandoff.Phase.REQUESTED
			|| actual.generation() != slot.state.generation() || !actual.owner().equals(slot.state.owner()) || !slot.state.ownerMayComplete(actual.owner()))
		{ return false; }
		publish(id, rowVersion, actual);
		slot.uncertain = false;
		return true;
	}
	public synchronized void acknowledgeRecovery(long id, long rowVersion, PhantomExecutionHandoff state)
	{
		final var slot = _slots.get(id);
		if (slot != null && (slot.running != 0 || slot.boundary)) { throw new IllegalStateException("Execution recovery still has active work."); }
		publish(id, rowVersion, state);
		_slots.get(id).activatedAt = nextPublication();
		_slots.get(id).uncertain = false;
	}
	/** Fence before a restart/unknown-ACK write, including when the durable state still says ACTIVE. */
	public <T> T recovering(long id, long rowVersion, PhantomExecutionHandoff actual, Supplier<T> recovery)
	{
		final Slot slot;
		synchronized (this)
		{
			if (!_slots.containsKey(id)) { publish(id, rowVersion, actual); }
			slot = _slots.get(id);
			if (slot.version > rowVersion || slot.running != 0 || slot.boundary) { throw new IllegalStateException("Execution recovery still has work or a newer owner."); }
			slot.boundary = true; slot.uncertain = true;
		}
		try { return recovery.get(); }
		finally { synchronized (this) { slot.boundary = false; } }
	}
	public final class Permit implements PhantomDecisionEngine.AdmissionLease
	{
		private final Slot _slot;
		private final long _profileId;
		private final long _generation;
		private final PhantomDomainRef _owner;
		private final long _admissionEpoch;
		private final Purpose _purpose;
		private boolean _closed;
		private Permit(long id, Slot slot, long generation, PhantomDomainRef owner, long epoch, Purpose purpose)
		{ _profileId = id; _slot = slot; _generation = generation; _owner = owner; _admissionEpoch = epoch; _purpose = purpose; }
		public Stamp stamp() { return new Stamp(_profileId, _generation, _owner, _admissionEpoch, _purpose); }
		public Scope enterScope()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				if (_closed || !acceptsContinuation(stamp())) { throw new IllegalStateException("Execution continuation is no longer owned."); }
				return enterCaptured(stamp());
			}
		}
		/** A command retains admission independently of an asynchronously closed session/actor lease. */
		public Scope retainScope()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				if (_closed) { throw new IllegalStateException("Execution lease is closed."); }
				final var retained = acquireCaptured(_profileId, stamp(), _purpose, true);
				if (retained == null) { throw new IllegalStateException("Execution command admission was revoked."); }
				return new Scope(null, retained.stamp(), _purpose, retained);
			}
		}
		@Override public boolean valid()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				return !_closed && sameGrant(_profileId, _slot, stamp()) && permits(_profileId, _owner, _purpose, true);
			}
		}
		@Override public long executionGeneration() { return _generation; }
		@Override public boolean yieldRequested()
		{
			synchronized (PhantomExecutionAdmission.this)
			{
				return !_closed && sameGrant(_profileId, _slot, stamp()) && _slot.state.ownerMayComplete(_owner)
					&& (_slot.boundary || _slot.uncertain || _slot.state.phase() == PhantomExecutionHandoff.Phase.REQUESTED);
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
		private long activatedAt;
		private final Map<String, Blocker> blockers = new HashMap<>();
		private int blocked;
		private Slot(long version, PhantomExecutionHandoff state, long activatedAt) { this.version = version; this.state = state; this.activatedAt = activatedAt; }
	}
}
