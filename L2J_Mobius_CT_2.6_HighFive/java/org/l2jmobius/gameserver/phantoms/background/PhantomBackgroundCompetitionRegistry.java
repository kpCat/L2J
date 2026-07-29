/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-local and operation-scoped spawn pressure bound. A reservation never
 * grants gameplay rewards.
 */
public final class PhantomBackgroundCompetitionRegistry
{
	private final ConcurrentHashMap<Key, Counter> _reservations = new ConcurrentHashMap<>();

	public Reservation tryReserve(String topologyNodeId, int npcId, long configuredAmount)
	{
		final Key key = new Key(topologyNodeId, npcId);
		final int capacity = (int) Math.clamp(configuredAmount, 1, 32);
		final Counter counter = _reservations.computeIfAbsent(key, _ -> new Counter(capacity));
		if ((counter._capacity != capacity) || !counter.tryAcquire())
		{
			if (counter._count.get() == 0)
			{
				_reservations.remove(key, counter);
			}
			return null;
		}
		return new Reservation(this, key, counter);
	}

	public int currentReservations()
	{
		return _reservations.values().stream().mapToInt(counter -> counter._count.get()).sum();
	}

	private void release(Key key, Counter counter)
	{
		if ((counter._count.decrementAndGet() == 0))
		{
			_reservations.remove(key, counter);
		}
	}

	private record Key(String topologyNodeId, int npcId)
	{
		private Key
		{
			if ((topologyNodeId == null) || topologyNodeId.isBlank() || (npcId <= 0))
			{
				throw new IllegalArgumentException("Invalid background competition identity.");
			}
		}
	}

	private static final class Counter
	{
		private final int _capacity;
		private final AtomicInteger _count = new AtomicInteger();

		private Counter(int capacity)
		{
			_capacity = capacity;
		}

		private boolean tryAcquire()
		{
			int current;
			do
			{
				current = _count.get();
				if (current >= _capacity)
				{
					return false;
				}
			}
			while (!_count.compareAndSet(current, current + 1));
			return true;
		}
	}

	public static final class Reservation implements AutoCloseable
	{
		private final PhantomBackgroundCompetitionRegistry _registry;
		private final Key _key;
		private final Counter _counter;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private Reservation(PhantomBackgroundCompetitionRegistry registry, Key key, Counter counter)
		{
			_registry = Objects.requireNonNull(registry);
			_key = Objects.requireNonNull(key);
			_counter = Objects.requireNonNull(counter);
		}

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_registry.release(_key, _counter);
			}
		}
	}
}
