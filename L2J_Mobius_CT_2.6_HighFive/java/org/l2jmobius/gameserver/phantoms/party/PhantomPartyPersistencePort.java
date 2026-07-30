/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;

public interface PhantomPartyPersistencePort
{
	Optional<StoredPartyState> load(long profileId);

	StoredPartyState save(long profileId, long expectedRowVersion, PartyState state);

	List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize);

	record StoredPartyState(long profileId, long rowVersion, PartyState state)
	{
		public StoredPartyState
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Invalid stored party state.");
			}
		}
	}
}
