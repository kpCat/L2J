/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

public interface PhantomCombatBackend
{
	PhantomCombatActorLease tryAcquireActor(long profileId);

	enum ActionOutcome
	{
		ISSUED,
		ALREADY_OWNED,
		UNAVAILABLE,
		REJECTED
	}

	enum ShotOutcome
	{
		ACTIVATED,
		UNAVAILABLE,
		FAILED
	}

	enum RespawnOutcome
	{
		COMPLETED,
		RETRY,
		REJECTED,
		CANCELLED
	}

	enum LootObservation
	{
		PENDING,
		ACQUIRED_BY_ACTOR,
		LOST_WITHOUT_ACQUISITION,
		INELIGIBLE
	}

	record ActorSnapshot(int objectId, int classId, int instanceId, double currentHp, double maximumHp, double currentMp, double maximumMp, boolean dead, boolean alikeDead, boolean attacking, boolean casting, boolean moving, int currentTargetObjectId, String intention, int currentSkillId, int currentSkillLevel)
	{
		public ActorSnapshot
		{
			if ((objectId <= 0) || (classId < 0) || (instanceId < 0) || !finite(currentHp, maximumHp, currentMp, maximumMp) || (maximumHp <= 0) || (maximumMp < 0) || (currentTargetObjectId < 0) || (currentSkillId < 0) || (currentSkillLevel < 0) || (intention == null))
			{
				throw new IllegalArgumentException("Invalid combat actor snapshot.");
			}
		}
	}

	record TargetSnapshot(int objectId, int npcId, int instanceId, double currentHp, double maximumHp, boolean dead, boolean alikeDead, boolean targetable, boolean attackable, boolean invulnerable, boolean normalMonster, boolean knowledgeMonster, double distance, boolean peaceRestricted, boolean surroundingRegion)
	{
		public TargetSnapshot
		{
			if ((objectId <= 0) || (npcId <= 0) || (instanceId < 0) || !finite(currentHp, maximumHp, distance) || (maximumHp <= 0) || (distance < 0))
			{
				throw new IllegalArgumentException("Invalid combat target snapshot.");
			}
		}

		public boolean validFor(ActorSnapshot actor, int maximumDistance)
		{
			return normalMonster && knowledgeMonster && targetable && attackable && !invulnerable && surroundingRegion && !peaceRestricted && !dead && !alikeDead && (instanceId == actor.instanceId()) && (distance <= maximumDistance);
		}
	}

	record ThreatObservation(int targetObjectId, long threatValue)
	{
		public ThreatObservation
		{
			if ((targetObjectId <= 0) || (threatValue <= 0))
			{
				throw new IllegalArgumentException("Invalid threat observation.");
			}
		}
	}

	record LootCandidate(int worldObjectId, int itemId, long groundCount, long actorInventoryCountBefore)
	{
		public LootCandidate
		{
			if ((worldObjectId <= 0) || (itemId <= 0) || (groundCount <= 0) || (actorInventoryCountBefore < 0))
			{
				throw new IllegalArgumentException("Invalid loot candidate.");
			}
		}
	}

	private static boolean finite(double... values)
	{
		for (double value : values)
		{
			if (!Double.isFinite(value))
			{
				return false;
			}
		}
		return true;
	}

	static PhantomCombatBackend inert()
	{
		return _ -> null;
	}
}
