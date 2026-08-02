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

	enum AcquisitionSkillKind
	{
		SPOIL,
		SWEEP
	}

	record ActorSnapshot(int objectId, int classId, int instanceId, double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp, boolean dead, boolean alikeDead, boolean attacking, boolean casting, boolean moving, int currentTargetObjectId, String intention, int currentSkillId, int currentSkillLevel)
	{
		public ActorSnapshot
		{
			if ((objectId <= 0) || (classId < 0) || (instanceId < 0) || !finite(currentHp, maximumHp, currentMp, maximumMp, currentCp, maximumCp) || (maximumHp <= 0) || (maximumMp < 0) || (maximumCp < 0) || (currentCp < 0) || (currentTargetObjectId < 0) || (currentSkillId < 0) || (currentSkillLevel < 0) || (intention == null))
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

	record AcquisitionTargetSnapshot(int objectId, int npcId, int instanceId, double distance, boolean dead, boolean alikeDead, boolean targetable, boolean attackable, boolean invulnerable, boolean normalMonster, boolean knowledgeMonster, boolean peaceRestricted, boolean surroundingRegion, boolean spoiled, int spoilerObjectId, boolean sweepActive, boolean sweepOwnerEligible, int level, boolean canBeSown, boolean raid, boolean chest, boolean seeded, int seederObjectId, int seedItemId, int onKillDelayMillis, int x, int y, int z, boolean spawnPresent, boolean spawnTerritoryPresent, boolean exactPointSpawn, String territoryName, String territorySourcePath, String territoryGeometryHash)
	{
		public AcquisitionTargetSnapshot(int objectId, int npcId, int instanceId, double distance, boolean dead, boolean alikeDead, boolean targetable, boolean attackable, boolean invulnerable, boolean normalMonster, boolean knowledgeMonster, boolean peaceRestricted, boolean surroundingRegion, boolean spoiled, int spoilerObjectId, boolean sweepActive, boolean sweepOwnerEligible)
		{
			this(objectId, npcId, instanceId, distance, dead, alikeDead, targetable, attackable, invulnerable, normalMonster, knowledgeMonster, peaceRestricted, surroundingRegion, spoiled, spoilerObjectId, sweepActive, sweepOwnerEligible, 1, false, false, false, false, 0, 0, 5000);
		}

		public AcquisitionTargetSnapshot(int objectId, int npcId, int instanceId, double distance, boolean dead, boolean alikeDead, boolean targetable, boolean attackable, boolean invulnerable, boolean normalMonster, boolean knowledgeMonster, boolean peaceRestricted, boolean surroundingRegion, boolean spoiled, int spoilerObjectId, boolean sweepActive, boolean sweepOwnerEligible, int level, boolean canBeSown, boolean raid, boolean chest, boolean seeded, int seederObjectId, int seedItemId, int onKillDelayMillis)
		{
			this(objectId, npcId, instanceId, distance, dead, alikeDead, targetable, attackable, invulnerable, normalMonster, knowledgeMonster, peaceRestricted, surroundingRegion, spoiled, spoilerObjectId, sweepActive, sweepOwnerEligible, level, canBeSown, raid, chest, seeded, seederObjectId, seedItemId, onKillDelayMillis, 0, 0, 0, false, false, false, "", "", "");
		}

		public AcquisitionTargetSnapshot
		{
			if ((objectId <= 0) || (npcId <= 0) || (instanceId < 0) || !Double.isFinite(distance) || (distance < 0) || (spoilerObjectId < 0) || (level < 0) || (seederObjectId < 0) || (seedItemId < 0) || (onKillDelayMillis < 0) || (onKillDelayMillis > 60000) || (seeded && ((seederObjectId <= 0) || (seedItemId <= 0))))
			{
				throw new IllegalArgumentException("Invalid acquisition target snapshot.");
			}
			territoryName = java.util.Objects.requireNonNull(territoryName, "territoryName");
			territorySourcePath = java.util.Objects.requireNonNull(territorySourcePath, "territorySourcePath");
			territoryGeometryHash = java.util.Objects.requireNonNull(territoryGeometryHash, "territoryGeometryHash");
			final boolean exactTerritoryIdentity = !territoryGeometryHash.isEmpty() && territoryGeometryHash.matches("[0-9a-f]{64}") && !territoryName.isBlank() && !territorySourcePath.isBlank();
			if ((spawnTerritoryPresent != exactTerritoryIdentity) || (spawnTerritoryPresent && (!spawnPresent || exactPointSpawn)) || (exactPointSpawn && (!spawnPresent || spawnTerritoryPresent)))
			{
				throw new IllegalArgumentException("Invalid acquisition target spawn identity.");
			}
		}

		public boolean liveValidFor(ActorSnapshot actor, int expectedNpcId, int maximumDistance)
		{
			return (npcId == expectedNpcId) && (instanceId == actor.instanceId()) && !dead && !alikeDead && targetable && attackable && !invulnerable && normalMonster && knowledgeMonster && !peaceRestricted && surroundingRegion && (distance <= maximumDistance);
		}

		public boolean sweepValidFor(ActorSnapshot actor, int expectedNpcId, int maximumDistance)
		{
			return (npcId == expectedNpcId) && (instanceId == actor.instanceId()) && (dead || alikeDead) && targetable && normalMonster && knowledgeMonster && !peaceRestricted && surroundingRegion && spoiled && sweepActive && sweepOwnerEligible && (distance <= maximumDistance);
		}

		public boolean manorLiveValidFor(ActorSnapshot actor, int expectedNpcId, int maximumDistance)
		{
			return liveValidFor(actor, expectedNpcId, maximumDistance) && canBeSown && !raid && !chest && !seeded;
		}

		public boolean harvestValidFor(ActorSnapshot actor, int expectedNpcId, int expectedSeedItemId, int maximumDistance)
		{
			return (npcId == expectedNpcId) && (instanceId == actor.instanceId()) && (dead || alikeDead) && targetable && normalMonster && knowledgeMonster && !peaceRestricted && surroundingRegion && seeded && (seederObjectId == actor.objectId()) && (seedItemId == expectedSeedItemId) && (distance <= maximumDistance);
		}
	}

	record ManorInventorySnapshot(int seedObjectId, long seedCount, int harvesterObjectId, long harvesterCount, long cropCount)
	{
		public ManorInventorySnapshot
		{
			if ((seedObjectId < 0) || (seedCount < 0) || (harvesterObjectId < 0) || (harvesterCount < 0) || (cropCount < 0) || ((seedObjectId == 0) != (seedCount == 0)) || ((harvesterObjectId == 0) != (harvesterCount == 0)))
			{
				throw new IllegalArgumentException("Invalid manor inventory snapshot.");
			}
		}
	}

	record QuestStateSnapshot(String questName, String state, int cond, java.util.Map<String, String> variables)
	{
		public QuestStateSnapshot
		{
			variables = java.util.Map.copyOf(variables);
			if ((questName == null) || questName.isBlank() || (state == null) || state.isBlank() || (cond < 0) || (cond > 255) || (variables.size() > 4))
			{
				throw new IllegalArgumentException("Invalid quest state snapshot.");
			}
		}
	}

	record AcquisitionActorPosition(int x, int y, int z, int instanceId)
	{
		public AcquisitionActorPosition
		{
			if (instanceId < 0)
			{
				throw new IllegalArgumentException("Invalid acquisition actor position.");
			}
		}
	}

	record PlayableSnapshot(int objectId, int classId, int instanceId, int x, int y, int z, double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp, boolean dead, boolean alikeDead, boolean casting, boolean moving, int currentTargetObjectId, List<Integer> attackerObjectIds)
	{
		public PlayableSnapshot
		{
			if ((objectId <= 0) || (classId < 0) || (instanceId < 0) || !finite(currentHp, maximumHp, currentMp, maximumMp, currentCp, maximumCp) || (maximumHp <= 0) || (maximumMp < 0) || (maximumCp < 0) || (currentCp < 0) || (currentTargetObjectId < 0) || (attackerObjectIds == null) || (attackerObjectIds.size() > 32) || attackerObjectIds.stream().anyMatch(id -> id == null || id <= 0))
			{
				throw new IllegalArgumentException("Invalid playable combat snapshot.");
			}
			attackerObjectIds = attackerObjectIds.stream().distinct().sorted().toList();
		}
	}

	record ExternalOwnedAction(PhantomCombatService.ExternalActionKind kind, int targetObjectId, SelectedSkill selectedSkill, int x, int y, int z, int instanceId)
	{
		public ExternalOwnedAction
		{
			if ((kind == null) || (targetObjectId < 0) || (instanceId < 0))
			{
				throw new IllegalArgumentException("Invalid external combat action.");
			}
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
