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
		PHANTOM,
		BACKGROUND
	}

	public enum OwnerState
	{
		RESERVED,
		RETAINED
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
		return entry == null ? null : entry._ownerKind;
	}

	public OwnerState getOwnerState(int objectId)
	{
		final Entry entry = _owners.get(objectId);
		return entry == null ? null : entry._state;
	}

	public OwnerSnapshot getOwnerSnapshot(int objectId)
	{
		final Entry entry = _owners.get(objectId);
		if (entry == null)
		{
			return null;
		}
		synchronized (entry)
		{
			return _owners.get(objectId) == entry ? entry.snapshot() : null;
		}
	}

	public static boolean requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)
	{
		return phantomSystemEnabled || (currentOwner != null);
	}

	public int getActiveLeaseCount()
	{
		return _owners.size();
	}

	public boolean releaseRetained(OwnerSnapshot expected)
	{
		Objects.requireNonNull(expected, "expected");
		final Entry entry = _owners.get(expected.objectId());
		if (entry == null)
		{
			return false;
		}
		synchronized (entry)
		{
			if ((_owners.get(expected.objectId()) != entry) || !entry.matches(expected) || (entry._ownerKind != OwnerKind.REAL_LOGIN) || (entry._state != OwnerState.RETAINED))
			{
				return false;
			}
			return _owners.remove(expected.objectId(), entry);
		}
	}

	private boolean markRetained(Entry entry)
	{
		synchronized (entry)
		{
			if ((_owners.get(entry._objectId) != entry) || (entry._ownerKind != OwnerKind.REAL_LOGIN) || (entry._state != OwnerState.RESERVED))
			{
				return false;
			}
			entry._state = OwnerState.RETAINED;
			return true;
		}
	}

	private void release(Entry entry)
	{
		synchronized (entry)
		{
			_owners.remove(entry._objectId, entry);
		}
	}

	public record OwnerSnapshot(int objectId, OwnerKind ownerKind, OwnerState state, long token)
	{
	}

	private static final class Entry
	{
		private final int _objectId;
		private final OwnerKind _ownerKind;
		private final long _token;
		private volatile OwnerState _state = OwnerState.RESERVED;

		private Entry(int objectId, OwnerKind ownerKind, long token)
		{
			_objectId = objectId;
			_ownerKind = ownerKind;
			_token = token;
		}

		private OwnerSnapshot snapshot()
		{
			return new OwnerSnapshot(_objectId, _ownerKind, _state, _token);
		}

		private boolean matches(OwnerSnapshot snapshot)
		{
			return (_objectId == snapshot.objectId()) && (_ownerKind == snapshot.ownerKind()) && (_state == snapshot.state()) && (_token == snapshot.token());
		}
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
			return _entry._objectId;
		}

		public OwnerKind ownerKind()
		{
			return _entry._ownerKind;
		}

		public boolean matchesObjectId(int objectId)
		{
			return _entry._objectId == objectId;
		}

		public long token()
		{
			return _entry._token;
		}

		public OwnerState state()
		{
			return _entry._state;
		}

		public boolean markRetained()
		{
			return !_closed.get() && _registry.markRetained(_entry);
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
