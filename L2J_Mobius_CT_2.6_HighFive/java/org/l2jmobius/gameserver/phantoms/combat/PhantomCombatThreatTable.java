/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class PhantomCombatThreatTable
{
	public static final long EXPLICIT_TARGET_BASE_THREAT = 1000;
	private static final long DECAY_INTERVAL_NANOS = 1_000_000_000L;
	private static final long MAXIMUM_THREAT = Long.MAX_VALUE / 4;
	private final int _capacity;
	private final Map<Integer, MutableEntry> _entries = new HashMap<>();
	private long _evictions;

	public PhantomCombatThreatTable(int capacity)
	{
		if ((capacity < 1) || (capacity > 32))
		{
			throw new IllegalArgumentException("Threat capacity must be between 1 and 32.");
		}
		_capacity = capacity;
	}

	public void observe(int targetObjectId, long threatValue, long logicalNowNanos, boolean explicitTarget)
	{
		if ((targetObjectId <= 0) || (threatValue <= 0) || (logicalNowNanos < 0))
		{
			throw new IllegalArgumentException("Invalid threat observation.");
		}
		final MutableEntry current = _entries.get(targetObjectId);
		if (current != null)
		{
			current._threatValue = saturatingAdd(decayed(current, logicalNowNanos), threatValue);
			current._lastObservedLogicalNanos = logicalNowNanos;
			current._explicitTarget |= explicitTarget;
			return;
		}
		if (_entries.size() >= _capacity)
		{
			final MutableEntry evicted = _entries.values().stream().min(evictionOrder(logicalNowNanos)).orElseThrow();
			_entries.remove(evicted._targetObjectId);
			_evictions++;
		}
		_entries.put(targetObjectId, new MutableEntry(targetObjectId, Math.min(MAXIMUM_THREAT, threatValue), logicalNowNanos, explicitTarget));
	}

	public OptionalInt highest(long logicalNowNanos)
	{
		if (logicalNowNanos < 0)
		{
			throw new IllegalArgumentException("Logical time must be non-negative.");
		}
		final MutableEntry highest = _entries.values().stream().max(selectionOrder(logicalNowNanos)).orElse(null);
		return highest == null ? OptionalInt.empty() : OptionalInt.of(highest._targetObjectId);
	}

	public List<Entry> snapshot(long logicalNowNanos)
	{
		final List<Entry> result = new ArrayList<>(_entries.size());
		for (MutableEntry entry : _entries.values())
		{
			result.add(new Entry(entry._targetObjectId, decayed(entry, logicalNowNanos), entry._lastObservedLogicalNanos, entry._explicitTarget));
		}
		result.sort(Comparator.comparingLong(Entry::threatValue).reversed().thenComparing(Entry::explicitTarget, Comparator.reverseOrder()).thenComparingInt(Entry::targetObjectId));
		return List.copyOf(result);
	}

	public int size()
	{
		return _entries.size();
	}

	public long evictions()
	{
		return _evictions;
	}

	private static Comparator<MutableEntry> selectionOrder(long now)
	{
		return Comparator.comparingLong((MutableEntry entry) -> decayed(entry, now)).thenComparing(entry -> entry._explicitTarget).thenComparingInt(entry -> -entry._targetObjectId);
	}

	private static Comparator<MutableEntry> evictionOrder(long now)
	{
		return Comparator.comparingLong((MutableEntry entry) -> decayed(entry, now)).thenComparingLong(entry -> entry._lastObservedLogicalNanos).thenComparingInt(entry -> -entry._targetObjectId);
	}

	private static long decayed(MutableEntry entry, long now)
	{
		final long elapsed = Math.max(0, now - entry._lastObservedLogicalNanos);
		return Math.max(1, entry._threatValue - (elapsed / DECAY_INTERVAL_NANOS));
	}

	private static long saturatingAdd(long left, long right)
	{
		if (left >= (MAXIMUM_THREAT - right))
		{
			return MAXIMUM_THREAT;
		}
		return left + right;
	}

	public record Entry(int targetObjectId, long threatValue, long lastObservedLogicalNanos, boolean explicitTarget)
	{
	}

	private static final class MutableEntry
	{
		private final int _targetObjectId;
		private long _threatValue;
		private long _lastObservedLogicalNanos;
		private boolean _explicitTarget;

		private MutableEntry(int targetObjectId, long threatValue, long lastObservedLogicalNanos, boolean explicitTarget)
		{
			_targetObjectId = targetObjectId;
			_threatValue = threatValue;
			_lastObservedLogicalNanos = lastObservedLogicalNanos;
			_explicitTarget = explicitTarget;
		}
	}
}
