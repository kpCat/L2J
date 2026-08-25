/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Snapshot;

@FunctionalInterface
public interface PhantomKarmaRecoveryContextPort
{
	Snapshot observe(long profileId, int counterpartObjectId);

	static PhantomKarmaRecoveryContextPort noop()
	{
		return (profileId, counterpartObjectId) -> Snapshot.UNAVAILABLE;
	}
}
