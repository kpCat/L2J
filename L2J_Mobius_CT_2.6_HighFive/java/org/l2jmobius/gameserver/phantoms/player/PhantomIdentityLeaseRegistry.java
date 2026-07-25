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
package org.l2jmobius.gameserver.phantoms.player;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local ownership arbitration for real-login and Phantom character
 * materialization.
 */
public final class PhantomIdentityLeaseRegistry
{
	public enum OwnerKind
	{
		REAL_LOGIN,
		PHANTOM
	}

	private final ConcurrentHashMap<Integer, Entry> _owners = new ConcurrentHashMap<>();
	private final AtomicLong _nextToken = new AtomicLong();

	private PhantomIdentityLeaseRegistry()
	{
	}

	public Lease tryAcquire(int objectId, OwnerKind ownerKind)
	{
		if (objectId <= 0)
		{
			throw new IllegalArgumentException("objectId must be positive");
		}

		final Entry entry = new Entry(objectId, Objects.requireNonNull(ownerKind, "ownerKind"), _nextToken.incrementAndGet());
		return _owners.putIfAbsent(objectId, entry) == null ? new Lease(this, entry) : null;
	}

	public OwnerKind getOwnerKind(int objectId)
	{
		final Entry entry = _owners.get(objectId);
		return entry == null ? null : entry.ownerKind();
	}

	public static boolean requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)
	{
		return phantomSystemEnabled || (currentOwner != null);
	}

	public int getActiveLeaseCount()
	{
		return _owners.size();
	}

	private void release(Entry entry)
	{
		_owners.remove(entry.objectId(), entry);
	}

	private record Entry(int objectId, OwnerKind ownerKind, long token)
	{
	}

	public static final class Lease implements AutoCloseable
	{
		private final PhantomIdentityLeaseRegistry _registry;
		private final Entry _entry;
		private final AtomicBoolean _closed = new AtomicBoolean();

		private Lease(PhantomIdentityLeaseRegistry registry, Entry entry)
		{
			_registry = registry;
			_entry = entry;
		}

		public int objectId()
		{
			return _entry.objectId();
		}

		public OwnerKind ownerKind()
		{
			return _entry.ownerKind();
		}

		public boolean matchesObjectId(int objectId)
		{
			return _entry.objectId() == objectId;
		}

		public long token()
		{
			return _entry.token();
		}

		public boolean isClosed()
		{
			return _closed.get();
		}

		@Override
		public void close()
		{
			if (_closed.compareAndSet(false, true))
			{
				_registry.release(_entry);
			}
		}
	}

	private static class SingletonHolder
	{
		private static final PhantomIdentityLeaseRegistry INSTANCE = new PhantomIdentityLeaseRegistry();
	}

	public static PhantomIdentityLeaseRegistry getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
}
