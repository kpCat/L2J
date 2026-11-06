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

	default Optional<StoredLogicalGroup> loadLogicalGroup(long leaderProfileId) { return Optional.empty(); }
	default Optional<StoredLogicalClaim> loadLogicalClaim(long profileId) { return Optional.empty(); }
	default List<StoredLogicalGroup> loadLogicalGroupsAfter(long afterLeaderId, int limit) { return List.of(); }
	/** Claims and the single aggregate are committed together or rolled back together. */
	default StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expectedGroupVersion, java.util.Map<Long, Long> expectedClaimVersions)
	{ throw new UnsupportedOperationException("Atomic logical group persistence is unavailable."); }
	default StoredLogicalGroup updateLogicalGroup(PhantomLogicalParty group, long expectedGroupVersion)
	{ throw new UnsupportedOperationException("Logical group CAS is unavailable."); }
	record StoredLogicalGroup(long rowVersion, PhantomLogicalParty group)
	{
		public StoredLogicalGroup { if (rowVersion < 0 || group == null) { throw new IllegalArgumentException("Invalid stored logical group."); } }
	}
	record StoredLogicalClaim(long profileId, long leaderProfileId, String groupId, long generation, long rowVersion)
	{
		public StoredLogicalClaim
		{
			org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.requireHash(groupId, "Logical claim");
			if (profileId <= 0 || leaderProfileId <= 0 || generation <= 0 || rowVersion < 0) { throw new IllegalArgumentException("Invalid logical member claim."); }
		}
	}

	default Optional<StoredRendezvous> loadRendezvous(long profileId) { return Optional.empty(); }
	default StoredRendezvous saveRendezvous(long profileId, long expectedRowVersion, PhantomPartyRendezvousState state)
	{ throw new UnsupportedOperationException("Durable rendezvous CAS is unavailable."); }
	record StoredRendezvous(long profileId, long rowVersion, PhantomPartyRendezvousState state)
	{
		public StoredRendezvous
		{
			if (profileId <= 0 || rowVersion < 0 || state == null || state.profileId() != profileId)
			{ throw new IllegalArgumentException("Foreign rendezvous component."); }
		}
	}

	default Optional<StoredRegroupClock> loadRegroupClock(long profileId) { return Optional.empty(); }

	default StoredRegroupClock saveRegroupClock(long profileId, long expectedRowVersion, PhantomPartyRegroupState state)
	{
		throw new UnsupportedOperationException("Durable regroup CAS is unavailable.");
	}

	record StoredRegroupClock(long profileId, long rowVersion, PhantomPartyRegroupState state)
	{
		public StoredRegroupClock
		{
			if (profileId <= 0 || rowVersion < 0 || state == null || state.actor().profileId() != profileId) { throw new IllegalArgumentException("Foreign regroup component."); }
		}
	}

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
