/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.diagnostics;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;

/** Optional causal observation. Hooks never acquire gameplay ownership or perform I/O. */
public final class PhantomRuntimeFlightRecorder
{
	private static final long SESSION_NANOS = 120_000_000_000L;
	private static final PhantomRuntimeFlightRecorder INSTANCE = new PhantomRuntimeFlightRecorder(() -> PhantomPlayersConfig.settings().diagnosticsEnabled(), 8192);
	private final BooleanSupplier _diagnostics;
	private final int _capacity;
	private final AtomicReference<Session> _session = new AtomicReference<>();
	private final ThreadLocal<Context> _profile = new ThreadLocal<>();

	public PhantomRuntimeFlightRecorder(BooleanSupplier diagnostics, int capacity)
	{
		if ((capacity < 1) || (capacity > 8192)) { throw new IllegalArgumentException("Recorder capacity must be 1..8192."); }
		_diagnostics = Objects.requireNonNull(diagnostics);
		_capacity = capacity;
	}

	public static PhantomRuntimeFlightRecorder getInstance() { return INSTANCE; }
	public boolean isRecording()
	{
		try { return recordingSession() != null; }
		catch (Throwable ignored) { return false; }
	}

	/** Called only after the LocalPlay real-client consent/session admission. */
	public boolean begin(String consent)
	{
		try
		{
			if (!_diagnostics.getAsBoolean() || (consent == null) || consent.isBlank() || (consent.length() > 96)) { return false; }
			final Session previous = _session.get();
			if (active(previous)) { return false; }
			return _session.compareAndSet(previous, new Session(consent, _capacity));
		}
		catch (Throwable ignored) { return false; }
	}

	public void watch(long profileId)
	{
		try
		{
			final Session session = recordingSession();
			if ((session == null) || (profileId <= 0)) { return; }
			for (;;)
			{
				final List<Long> previous = session.watched.get();
				if (previous.contains(profileId) || (previous.size() >= 8)) { return; }
				final var next = new ArrayList<>(previous);
				next.add(profileId);
				if (session.watched.compareAndSet(previous, List.copyOf(next))) { return; }
			}
		}
		catch (Throwable ignored) { }
	}

	public void record(long profileId, String event, String stateA, String stateB, String reason, long value1, long value2, long value3)
	{
		try
		{
			final Session session = recordingSession();
			record(session, profileId, event, stateA, stateB, reason, value1, value2, value3);
		}
		catch (Throwable ignored) { }
	}

	private void record(Session session, long profileId, String event, String stateA, String stateB, String reason, long value1, long value2, long value3)
	{
		if ((session == null) || !active(session) || ((profileId != 0) && !session.watched.get().contains(profileId))) { return; }
		final long seq = session.sequence.incrementAndGet();
		final var value = new Event(seq, System.nanoTime(), bounded(Thread.currentThread().getName(), 96), profileId,
			bounded(event, 48), bounded(stateA, 48), bounded(stateB, 48), bounded(reason, 96), value1, value2, value3);
		final int index = (int) ((seq - 1) % _capacity);
		final Event previous = session.ring.get(index);
		// A delayed producer must never overwrite a newer wrapped sequence. One CAS; no waiting.
		if ((previous == null) || (previous.seq() < seq)) { session.ring.compareAndSet(index, previous, value); }
	}

	public void recordCurrent(String event, String state, String reason, long objectId)
	{
		try
		{
			final Context context = _profile.get();
			if ((context != null) && _diagnostics.getAsBoolean() && (_session.get() == context.session()))
			{
				record(context.session(), context.profileId(), event, state, "", reason, objectId, 0, 0);
			}
		}
		catch (Throwable ignored) { }
	}

	/** Repeated queue scan facts are sampled once/second/profile; boundary outcomes are not sampled. */
	public void recordScan(long profileId, String effective, String requested, String status, long generation, long flags, long retryDue)
	{
		try
		{
			final Session session = recordingSession();
			if (session == null) { return; }
			final int index = session.watched.get().indexOf(profileId);
			if (index < 0) { return; }
			final long now = System.nanoTime();
			final long previous = session.scanNanos.get(index);
			if ((previous != 0) && (now - previous < 1_000_000_000L)) { return; }
			if (session.scanNanos.compareAndSet(index, previous, now)) { record(session, profileId, "SCHED_LOCAL_SCAN", effective, requested, status, generation, flags, retryDue); }
		}
		catch (Throwable ignored) { }
	}

	/** Observation context only. The delegate is invoked exactly once, preserving result/exception. */
	public <T> T withProfile(long profileId, Supplier<T> delegate)
	{
		Context previous = null;
		boolean installed = false;
		try
		{
			final Session session = recordingSession();
			if ((session != null) && session.watched.get().contains(profileId))
			{
				previous = _profile.get();
				_profile.set(new Context(session, profileId));
				installed = true;
			}
		}
		catch (Throwable ignored) { }
		try { return delegate.get(); }
		finally
		{
			if (installed)
			{
				try { if (previous == null) { _profile.remove(); } else { _profile.set(previous); } }
				catch (Throwable ignored) { }
			}
		}
	}

	public Snapshot snapshot(String consent)
	{
		try
		{
			final Session session = _session.get();
			if (!_diagnostics.getAsBoolean() || (session == null) || !session.consent.equals(consent)) { return Snapshot.empty(); }
			final long upper = session.sequence.get();
			final var events = new ArrayList<Event>(_capacity);
			for (int i = 0; i < _capacity; i++)
			{
				final Event event = session.ring.get(i);
				if ((event != null) && (event.seq() <= upper) && (event.seq() > upper - _capacity)) { events.add(event); }
			}
			events.sort(Comparator.comparingLong(Event::seq));
			// Overwritten, CAS-lost and still-in-flight sequences are all explicitly unavailable.
			return new Snapshot(session.watched.get(), List.copyOf(events), upper, upper - events.size(), active(session), session.startedNanos);
		}
		catch (Throwable ignored) { return Snapshot.empty(); }
	}

	public Snapshot end(String consent)
	{
		stop(consent);
		return snapshot(consent);
	}

	public void stop(String consent)
	{
		try
		{
			final Session session = _session.get();
			if ((session != null) && session.consent.equals(consent)) { session.active = false; }
		}
		catch (Throwable ignored) { }
	}

	private Session recordingSession()
	{
		if (!_diagnostics.getAsBoolean()) { return null; }
		final Session session = _session.get();
		return active(session) ? session : null;
	}

	private static boolean active(Session session)
	{
		return (session != null) && session.active && (System.nanoTime() - session.startedNanos < SESSION_NANOS);
	}

	private static String bounded(String value, int maximum)
	{
		if (value == null) { return ""; }
		return value.substring(0, Math.min(value.length(), maximum)).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}

	private static final class Session
	{
		private final String consent;
		private final long startedNanos = System.nanoTime();
		private final AtomicLong sequence = new AtomicLong();
		private final AtomicLongArray scanNanos = new AtomicLongArray(8);
		private final AtomicReference<List<Long>> watched = new AtomicReference<>(List.of());
		private final AtomicReferenceArray<Event> ring;
		private volatile boolean active = true;
		private Session(String consent, int capacity) { this.consent = consent; ring = new AtomicReferenceArray<>(capacity); }
	}

	private record Context(Session session, long profileId) { }

	public record Snapshot(List<Long> watched, List<Event> events, long attempts, long dropped, boolean active, long startedNanos)
	{
		private static Snapshot empty() { return new Snapshot(List.of(), List.of(), 0, 0, false, 0); }
	}

	public record Event(long seq, long nanoTime, String threadName, long profileId, String event, String stateA, String stateB, String reason, long value1, long value2, long value3)
	{
		public String tsv()
		{
			return seq + "\t" + nanoTime + "\t" + threadName + "\t" + profileId + "\t" + event + "\t" + stateA + "\t" + stateB + "\t" + reason + "\t" + value1 + "\t" + value2 + "\t" + value3;
		}
	}
}
