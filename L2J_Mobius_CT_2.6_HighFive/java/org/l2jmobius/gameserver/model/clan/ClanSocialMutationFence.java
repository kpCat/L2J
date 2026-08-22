/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Shared bounded lock and retirement fence for native clan social aggregates.
 */
public final class ClanSocialMutationFence
{
	private static final int LOCK_COUNT = 256;
	private static final ClanSocialMutationFence INSTANCE = new ClanSocialMutationFence(LOCK_COUNT);
	private final ReentrantLock[] _locks;
	private final Map<Integer, Retirement> _retirements = new ConcurrentHashMap<>();

	public static final class Retirement
	{
		private final int _clanId;

		private Retirement(int clanId)
		{
			_clanId = clanId;
		}

		public int clanId()
		{
			return _clanId;
		}
	}

	ClanSocialMutationFence(int lockCount)
	{
		if ((lockCount <= 0) || ((lockCount & (lockCount - 1)) != 0))
		{
			throw new IllegalArgumentException("Clan social lock count must be a positive power of two.");
		}
		_locks = new ReentrantLock[lockCount];
		for (int i = 0; i < lockCount; i++)
		{
			_locks[i] = new ReentrantLock();
		}
	}

	public static ClanSocialMutationFence getInstance()
	{
		return INSTANCE;
	}

	public Retirement beginRetirement(int clanId)
	{
		if (clanId <= 0)
		{
			throw new IllegalArgumentException("A positive clan id is required for retirement.");
		}
		return execute(new long[]
		{
			clanKey(clanId)
		}, () ->
		{
			if (_retirements.containsKey(clanId))
			{
				return null;
			}
			final Retirement retirement = new Retirement(clanId);
			_retirements.put(clanId, retirement);
			return retirement;
		});
	}

	public boolean abortRetirement(Retirement retirement)
	{
		return finishRetirement(retirement);
	}

	public boolean completeRetirement(Retirement retirement)
	{
		return finishRetirement(retirement);
	}

	boolean isRetiring(int clanId)
	{
		return _retirements.containsKey(clanId);
	}

	boolean isCurrentRetirement(Retirement retirement)
	{
		return (retirement != null) && (_retirements.get(retirement.clanId()) == retirement);
	}

	static long clanKey(int clanId)
	{
		return Integer.toUnsignedLong(clanId) << 1;
	}

	static long allianceNameKey(String allianceName)
	{
		return (((long) allianceName.toLowerCase(Locale.ROOT).hashCode()) << 1) | 1L;
	}
	<T> T execute(long[] resourceKeys, Supplier<T> operation)
	{
		if ((resourceKeys == null) || (resourceKeys.length == 0))
		{
			throw new IllegalArgumentException("At least one clan social resource key is required.");
		}
		final int[] indexes = Arrays.stream(resourceKeys).mapToInt(this::lockIndex).distinct().sorted().toArray();
		for (int index : indexes)
		{
			_locks[index].lock();
		}
		try
		{
			return operation.get();
		}
		finally
		{
			for (int i = indexes.length - 1; i >= 0; i--)
			{
				_locks[indexes[i]].unlock();
			}
		}
	}

	private int lockIndex(long resourceKey)
	{
		return Long.hashCode(resourceKey) & (_locks.length - 1);
	}

	private boolean finishRetirement(Retirement retirement)
	{
		if (retirement == null)
		{
			return false;
		}
		return execute(new long[]
		{
			clanKey(retirement.clanId())
		}, () -> _retirements.remove(retirement.clanId(), retirement));
	}
}