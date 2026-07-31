/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException.Category;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialReceiptLedger;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.StoredState;

final class PhantomSocialTestDoubles
{
	private PhantomSocialTestDoubles()
	{
	}

	static final class MemoryStore implements PersistencePort
	{
		private final Set<Long> _profiles = ConcurrentHashMap.newKeySet();
		private final Map<Long, StoredState> _states = new ConcurrentHashMap<>();
		private final AtomicInteger _writes = new AtomicInteger();
		private final AtomicInteger _conflicts = new AtomicInteger();
		private volatile boolean _insertCollision;
		private volatile boolean _alwaysConflict;

		void addProfile(long profileId)
		{
			_profiles.add(profileId);
		}

		void addProfiles(long first, long last)
		{
			for (long profileId = first; profileId <= last; profileId++)
			{
				addProfile(profileId);
			}
		}

		void seed(long profileId, long rowVersion, SocialState state)
		{
			addProfile(profileId);
			_states.put(profileId, new StoredState(profileId, rowVersion, rowVersion, state, PhantomSocialReceiptLedger.empty()));
		}

		void conflictNext(int count)
		{
			_conflicts.set(count);
		}

		void insertCollision()
		{
			_insertCollision = true;
		}

		void alwaysConflict()
		{
			_alwaysConflict = true;
		}

		int writes()
		{
			return _writes.get();
		}

		StoredState require(long profileId)
		{
			return _states.get(profileId);
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return _profiles.contains(profileId);
		}

		@Override
		public Optional<StoredState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public synchronized StoredState save(long profileId, long expectedStateRowVersion, long expectedReceiptRowVersion, SocialState state, PhantomSocialReceiptLedger receipts)
		{
			if (_alwaysConflict || (_conflicts.getAndUpdate(value -> Math.max(0, value - 1)) > 0))
			{
				throw new ConcurrentModificationException("Injected social optimistic conflict.");
			}
			final StoredState current = _states.get(profileId);
			if (_insertCollision && (expectedStateRowVersion < 0) && (expectedReceiptRowVersion < 0) && (current == null))
			{
				_insertCollision = false;
				_states.put(profileId, new StoredState(profileId, 0, 0, state, receipts));
				_writes.incrementAndGet();
				throw new PhantomProfilePersistenceException(Category.CONSTRAINT_VIOLATION, "Injected first-access insert collision.");
			}
			if (((current == null) && ((expectedStateRowVersion >= 0) || (expectedReceiptRowVersion >= 0))) || ((current != null) && ((current.stateRowVersion() != expectedStateRowVersion) || (current.receiptRowVersion() != expectedReceiptRowVersion))))
			{
				throw new ConcurrentModificationException("Injected stale social row version.");
			}
			final long nextVersion = current == null ? 0 : current.stateRowVersion() + 1;
			final StoredState saved = new StoredState(profileId, nextVersion, nextVersion, state, receipts);
			_states.put(profileId, saved);
			_writes.incrementAndGet();
			return saved;
		}
	}
}
