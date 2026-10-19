/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Set;

/** An accepted finite child can request the existing admission queue; it never registers Players or bypasses capacity. */
public interface PhantomNativeJourneyDemand
{
	Set<Long> acceptedProfiles();
	boolean requiresNativeContext(long profileId, long rootGoalId, long rootRevision);
}
