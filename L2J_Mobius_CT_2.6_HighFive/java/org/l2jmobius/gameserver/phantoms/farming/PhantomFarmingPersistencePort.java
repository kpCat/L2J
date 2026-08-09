/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;

public interface PhantomFarmingPersistencePort
{
	Optional<StoredState> load(long profileId);

	StoredState save(long profileId, long expectedRowVersion, FarmingState state);

	record StoredState(long profileId, long rowVersion, FarmingState state)
	{
		public StoredState
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Invalid stored farming conflict state.");
			}
		}
	}
}
