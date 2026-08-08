/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;

public interface PhantomRiftPersistencePort
{
	Optional<StoredPreparation> load(long profileId);

	StoredPreparation save(long profileId, long expectedRowVersion, Preparation preparation);

	record StoredPreparation(long profileId, long rowVersion, Preparation preparation)
	{
		public StoredPreparation
		{
			if ((profileId <= 0) || (rowVersion < 0) || (preparation == null) || (preparation.leaderProfileId() != profileId))
			{
				throw new IllegalArgumentException("Invalid stored Rift preparation.");
			}
		}
	}
}
