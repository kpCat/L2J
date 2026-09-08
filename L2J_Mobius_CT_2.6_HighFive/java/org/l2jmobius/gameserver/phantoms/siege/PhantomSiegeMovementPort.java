/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

public interface PhantomSiegeMovementPort
{
	PhantomSiegeMovementCoordinator.Result advance(long profileId, String anchorId, String authorityHash, long maximumDurationMillis);

	boolean cancel(long profileId, String authorityHash);

	void finishStop();

	int activeCount();
}
