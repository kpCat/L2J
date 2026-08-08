/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;

/**
 * Packet-independent, read-only Rift facts. The only mutation owner used by
 * Goal 023 remains the accepted Goal 017 coordinator.
 */
public interface PhantomRiftBackend extends PhantomRiftCatalog.Authority
{
	Optional<MemberRef> currentMember(long profileId);

	Optional<PartySnapshot> canonicalParty(MemberRef member);

	Optional<MemberFacts> memberFacts(MemberRef member, Set<Integer> requestedItemIds);

	List<MemberFacts> nearbyCandidates(MemberRef observer, Set<Integer> requestedItemIds, int range, int limit);

	default Optional<MemberFacts> candidateFacts(MemberRef observer, MemberRef candidate, Set<Integer> requestedItemIds, int range)
	{
		return memberFacts(candidate, requestedItemIds);
	}

	default RelationshipEvidence relationship(long ownerProfileId, MemberRef candidate)
	{
		return RelationshipEvidence.neutral("social.unavailable");
	}

	record RelationshipEvidence(int modifierBasisPoints, String evidenceHash, String reasonKey, boolean available)
	{
		public RelationshipEvidence
		{
			if ((modifierBasisPoints < -3000) || (modifierBasisPoints > 3000))
			{
				throw new IllegalArgumentException("Rift relationship modifier is outside bounds.");
			}
			evidenceHash = PhantomRiftModel.requireHash(evidenceHash, "Rift relationship evidence");
			reasonKey = PhantomRiftModel.requireKey(reasonKey, "Rift relationship reason");
		}

		public static RelationshipEvidence neutral(String reasonKey)
		{
			return new RelationshipEvidence(0, "0".repeat(64), reasonKey, false);
		}
	}

	record EquipmentFact(int objectId, int itemId, int paperdollSlot, String family, String grade)
	{
		public EquipmentFact
		{
			if ((objectId <= 0) || (itemId <= 0) || (paperdollSlot < 0) || (family == null) || family.isBlank() || (grade == null) || grade.isBlank())
			{
				throw new IllegalArgumentException("Invalid Rift equipment fact.");
			}
		}
	}

	record ShotSupply(int itemId, long count, boolean magic)
	{
		public ShotSupply
		{
			if ((itemId <= 0) || (count < 0))
			{
				throw new IllegalArgumentException("Invalid factual Rift shot supply.");
			}
		}
	}

	record MemberFacts(MemberSnapshot member, int level, List<EquipmentFact> equipment, Map<Integer, Long> requestedItemCounts, List<ShotSupply> shotSupplies, int activeWeaponItemId, int soulshotsPerHit, int spiritshotsPerHit, int canonicalPartySize, String evidenceHash)
	{
		public MemberFacts
		{
			if ((member == null) || (level < 1) || (activeWeaponItemId < 0) || (soulshotsPerHit < 0) || (spiritshotsPerHit < 0) || (canonicalPartySize < 0) || (canonicalPartySize > 9))
			{
				throw new IllegalArgumentException("Invalid Rift member facts.");
			}
			equipment = List.copyOf(equipment);
			requestedItemCounts = Map.copyOf(requestedItemCounts);
			shotSupplies = List.copyOf(shotSupplies);
			evidenceHash = PhantomRiftModel.requireHash(evidenceHash, "Rift member fact evidence");
		}

		public boolean inAnotherParty(List<MemberRef> currentRoster)
		{
			return (canonicalPartySize > 0) && !currentRoster.contains(member.ref());
		}

		public long distanceSquared(int x, int y, int z)
		{
			final long dx = (long) member.x() - x;
			final long dy = (long) member.y() - y;
			final long dz = (long) member.z() - z;
			return (dx * dx) + (dy * dy) + (dz * dz);
		}
	}
}
