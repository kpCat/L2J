/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;

/**
 * Bounded exact-key bridge to canonical instance-script actions.
 */
public interface PhantomRaidScriptAdapter
{
	String contentId();

	int entryNpcId();

	int templateId();

	EntryResult enter(EntryRequest request);

	List<CandleEvidence> candles(int instanceId);

	CandleInteraction interactCandle(int instanceId, int scoutObjectId, int candleObjectId);

	Optional<TargetEvidence> revealedTarget(int instanceId);

	Optional<PhantomNavigationPoint> safeRetreatPoint(int instanceId);

	boolean confirmsDeath(TargetEvidence target);

	record EntryRequest(String contentId, MemberRef leader, String structuralHash)
	{
		public EntryRequest
		{
			if ((contentId == null) || contentId.isBlank() || (leader == null) || (structuralHash == null) || !structuralHash.matches("[0-9A-Fa-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid exact raid script entry request.");
			}
			structuralHash = structuralHash.toUpperCase(java.util.Locale.ROOT);
		}
	}

	record EntryResult(EntryStatus status, int instanceId, String reasonKey)
	{
		public EntryResult
		{
			if ((status == null) || (instanceId < 0) || (reasonKey == null) || reasonKey.isBlank() || ((status == EntryStatus.ENTERED) != (instanceId > 0)))
			{
				throw new IllegalArgumentException("Invalid raid script entry result.");
			}
		}

		public static EntryResult entered(int instanceId)
		{
			return new EntryResult(EntryStatus.ENTERED, instanceId, "raid.entry.entered");
		}

		public static EntryResult rejected(String reason)
		{
			return new EntryResult(EntryStatus.REJECTED, 0, reason);
		}
	}

	enum EntryStatus
	{
		ENTERED,
		REJECTED
	}

	record CandleEvidence(int objectId, PhantomNavigationPoint point, boolean used)
	{
		public CandleEvidence
		{
			if ((objectId <= 0) || (point == null))
			{
				throw new IllegalArgumentException("Invalid public candle evidence.");
			}
		}
	}

	enum CandleInteraction
	{
		INTERACTED,
		ALREADY_USED,
		OUT_OF_RANGE,
		WRONG_INSTANCE,
		MISSING,
		INVALID_ACTOR
	}

	record TargetEvidence(int objectId, int npcId, int instanceId)
	{
		public TargetEvidence
		{
			if ((objectId <= 0) || (npcId <= 0) || (instanceId <= 0))
			{
				throw new IllegalArgumentException("Invalid exact scripted raid target evidence.");
			}
		}
	}
}