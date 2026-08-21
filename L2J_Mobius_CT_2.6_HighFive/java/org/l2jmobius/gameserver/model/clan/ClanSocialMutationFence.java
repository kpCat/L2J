/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Shared bounded lock fence for native clan social aggregates.
 */
final class ClanSocialMutationFence
{
	private static final int LOCK_COUNT = 256;
	private static final ClanSocialMutationFence INSTANCE = new ClanSocialMutationFence(LOCK_COUNT);
	private final ReentrantLock[] _locks;

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

	static ClanSocialMutationFence getInstance()
	{
		return INSTANCE;
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
}