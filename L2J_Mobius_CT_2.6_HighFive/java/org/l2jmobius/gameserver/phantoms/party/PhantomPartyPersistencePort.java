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
	default StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expectedGroupVersion, java.util.Map<Long, Long> expectedClaimVersions,
		List<org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent> guards)
	{
		if (!guards.isEmpty()) { throw new UnsupportedOperationException("Guarded logical group persistence is unavailable."); }
		return commitLogicalGroup(group, expectedGroupVersion, expectedClaimVersions);
	}
	default StoredLogicalGroup updateLogicalGroup(PhantomLogicalParty group, long expectedGroupVersion)
	{ throw new UnsupportedOperationException("Logical group CAS is unavailable."); }
	/** Terminal release or idle roster revision, including all departed and incoming identity claims. */
	default StoredLogicalGroup reviseLogicalGroup(StoredLogicalGroup expected, PhantomLogicalParty replacement)
	{ throw new UnsupportedOperationException("Atomic logical membership revision is unavailable."); }
	/** Observe settled member receipts without awarding or executing any interval. */
	default StoredLogicalGroup refreshLogicalBackground(StoredLogicalGroup expected, long now) { return expected; }
	default List<PhantomLogicalParty.ActivityRoot> activityRoots(StoredLogicalGroup expected) { return List.of(); }
	default StoredLogicalGroup beginActivityRevision(StoredLogicalGroup expected, List<PhantomLogicalParty.ActivityRoot> roots, long now)
	{ throw new UnsupportedOperationException("Guarded activity revision is unavailable."); }
	default StoredLogicalGroup beginActivityRevision(StoredLogicalGroup expected, List<PhantomLogicalParty.ActivityRoot> roots, long now, java.util.Map<Long, Long> backgroundVersions)
	{
		if (!backgroundVersions.isEmpty()) { throw new UnsupportedOperationException("Exact Background guards are unavailable."); }
		return beginActivityRevision(expected, roots, now);
	}
	/** Empty while an existing publication/owner still needs recovery; never grants partial execution. */
	default Optional<StoredLogicalGroup> finishActivityRevision(StoredLogicalGroup expected, long now, boolean abort)
	{ return Optional.empty(); }
	record StoredLogicalGroup(long rowVersion, PhantomLogicalParty group)
	{
		public StoredLogicalGroup { if (rowVersion < 0 || group == null) { throw new IllegalArgumentException("Invalid stored logical group."); } }
	}
	record StoredLogicalClaim(long profileId, long leaderProfileId, String groupId, long generation, long rowVersion, boolean active)
	{
		public StoredLogicalClaim(long profileId, long leaderProfileId, String groupId, long generation, long rowVersion)
		{ this(profileId, leaderProfileId, groupId, generation, rowVersion, true); }
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
