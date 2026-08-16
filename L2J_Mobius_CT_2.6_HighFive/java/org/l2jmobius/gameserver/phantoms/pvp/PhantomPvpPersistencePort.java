/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;

public interface PhantomPvpPersistencePort
{
	Optional<StoredEncounter> load(long profileId);

	StoredEncounter save(long profileId, long expectedRowVersion, Encounter encounter);

	record StoredEncounter(long profileId, long rowVersion, Encounter encounter)
	{
		public StoredEncounter
		{
			if ((profileId <= 0) || (rowVersion < 0) || (encounter == null) || (profileId != encounter.profileId()))
			{
				throw new IllegalArgumentException("Invalid stored PvP encounter.");
			}
		}
	}
}
