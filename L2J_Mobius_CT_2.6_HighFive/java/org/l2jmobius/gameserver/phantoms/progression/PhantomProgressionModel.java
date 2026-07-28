/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;

/**
 * Immutable, language-independent values exposed by the progression subsystem.
 */
public final class PhantomProgressionModel
{
	private PhantomProgressionModel()
	{
	}

	public enum Authority
	{
		SERVER_LOADER_FACT,
		STATIC_DATAPACK_FACT,
		CURRENT_SERVER_IMPLEMENTATION,
		CURATED_CAPABILITY_RULE
	}

	public enum AcquireKind
	{
		CLASS(true),
		TRANSFER(false),
		SUBCLASS(false),
		NOBLE(false),
		COMMON(false),
		TRANSFORM(false);

		private final boolean _executable;

		AcquireKind(boolean executable)
		{
			_executable = executable;
		}

		public boolean executable()
		{
			return _executable;
		}
	}

	public enum ActorKind
	{
		SERVITOR,
		PET,
		BABY_PET,
		CUBIC,
		SIEGE_SUMMON,
		QUEST_SUMMON,
		OTHER_CONTROLLED_ACTOR
	}

	public enum TargetScope
	{
		SELF,
		SINGLE_TARGET,
		PARTY,
		CLAN,
		ALLY,
		COMMAND_CHANNEL,
		AREA,
		SERVITOR,
		PET,
		NPC
	}

	public enum ConditionPresence
	{
		NONE,
		DYNAMIC_SERVER_CONDITION
	}

	public enum ReadinessReason
	{
		READY,
		WRONG_CLASS_STAGE,
		SKILL_NOT_LEARNED,
		PREVIOUS_SKILL_MISSING,
		LEVEL_TOO_LOW,
		SP_TOO_LOW,
		REQUIRED_ITEM_MISSING,
		WEAPON_OR_EQUIPMENT_MISMATCH,
		DYNAMIC_CONDITION_FAILED,
		INSUFFICIENT_MP_OR_HP,
		SKILL_DISABLED_OR_REUSE,
		TRANSFORMED,
		MOUNTED,
		DEAD,
		SUMMON_REQUIRED,
		SERVITOR_NOT_PRESENT,
		TARGET_REQUIRED,
		UNSUPPORTED_ACQUIRE_TYPE
	}

	public enum OperationStatus
	{
		SUCCESS,
		IDEMPOTENT,
		CANCELLED,
		SERVICE_NOT_RUNNING,
		OPERATION_IN_PROGRESS,
		ACTOR_NOT_MATERIALIZED,
		INVALID_REQUEST,
		UNSUPPORTED_ACQUIRE_TYPE,
		TRAINER_REQUIRED,
		TRAINER_MISMATCH,
		TRAINER_CANNOT_TEACH,
		ACTOR_STATE_REJECTED,
		SKILL_NOT_FOUND,
		SKILL_LEARN_NOT_FOUND,
		PREVIOUS_SKILL_MISSING,
		LEVEL_TOO_LOW,
		SP_TOO_LOW,
		PREREQUISITE_MISSING,
		REQUIRED_ITEM_MISSING,
		ITEM_NOT_OWNED,
		ITEM_NOT_EQUIPPABLE,
		ITEM_CONDITION_FAILED,
		RECONCILIATION_FAILED,
		BLOCKED_CANONICAL_SKILL_LEARNING,
		BLOCKED_CANONICAL_EQUIP_FACADE,
		BACKEND_FAILURE
	}

	public enum ProfessionStatus
	{
		ALREADY_CURRENT,
		STRUCTURALLY_INVALID,
		LEVEL_PENDING,
		CANONICAL_QUEST_REQUIRED,
		CANONICAL_ACTION_AVAILABLE,
		TRANSITION_OBSERVED
	}

	public enum SnapshotStatus
	{
		FOUND,
		ACTOR_NOT_MATERIALIZED,
		SERVICE_NOT_RUNNING,
		CANCELLED,
		BACKEND_FAILURE
	}

	public record SkillRef(int skillId, int skillLevel)
	{
		public SkillRef
		{
			if ((skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid skill identity.");
			}
		}

		public String stableKey()
		{
			return key(skillId) + ':' + key(skillLevel);
		}
	}

	public record RequiredItem(int itemId, long count)
	{
		public RequiredItem
		{
			if ((itemId <= 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Invalid required item.");
			}
		}

		public String stableKey()
		{
			return key(itemId) + ':' + String.format("%020d", count);
		}
	}

	public record ClassFact(int classId, String enumKey, String race, Integer enumParentClassId, Integer skillTreeParentClassId, int rootClassId, int tier, boolean mage, boolean summoner, boolean terminal, List<Integer> nextClassIds, Authority authority, List<String> sourcePaths)
	{
		public ClassFact
		{
			if ((classId < 0) || (enumKey == null) || enumKey.isBlank() || (race == null) || race.isBlank() || (rootClassId < 0) || (tier < 0) || (authority != Authority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid class fact.");
			}
			nextClassIds = List.copyOf(nextClassIds);
			sourcePaths = List.copyOf(sourcePaths);
		}

		public String stableKey()
		{
			return key(classId);
		}
	}

	public record SkillLearnFact(int classId, AcquireKind acquireKind, int skillId, int skillLevel, int minimumCharacterLevel, int baseSpCost, List<RequiredItem> requiredItems, List<SkillRef> prerequisiteSkills, boolean learnedOnce, boolean upgradable, boolean learnedByNpc, boolean learnedByForgottenScroll, Authority authority, String sourcePath)
	{
		public SkillLearnFact
		{
			if ((classId < -1) || (skillId <= 0) || (skillLevel <= 0) || (minimumCharacterLevel < 0) || (baseSpCost < 0) || (sourcePath == null) || sourcePath.isBlank() || (authority != Authority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid skill learn fact.");
			}
			Objects.requireNonNull(acquireKind, "acquireKind");
			requiredItems = List.copyOf(requiredItems);
			prerequisiteSkills = List.copyOf(prerequisiteSkills);
		}

		public SkillRef skill()
		{
			return new SkillRef(skillId, skillLevel);
		}

		public String stableKey()
		{
			return key(classId + 1) + ':' + acquireKind.ordinal() + ':' + key(skillId) + ':' + key(skillLevel);
		}
	}

	public record SkillFact(int skillId, int skillLevel, boolean active, boolean passive, boolean toggle, boolean physical, boolean magic, String targetType, boolean damage, boolean negative, boolean heal, boolean resurrection, boolean buff, boolean debuff, boolean control, int itemConsumeId, int itemConsumeCount, int mpConsume, int hpConsume, int reuseDelay, ConditionPresence conditionPresence, boolean blockedInOlympiad, boolean pvpOnly, boolean suicideAttack, boolean removedOnActionExceptMove, boolean transformation, Authority authority, List<String> sourcePaths)
	{
		public SkillFact
		{
			if ((skillId <= 0) || (skillLevel <= 0) || (targetType == null) || targetType.isBlank() || (itemConsumeId < 0) || (itemConsumeCount < 0) || (mpConsume < 0) || (hpConsume < 0) || (reuseDelay < 0) || (authority != Authority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid skill fact.");
			}
			Objects.requireNonNull(conditionPresence, "conditionPresence");
			sourcePaths = List.copyOf(sourcePaths);
		}

		public SkillRef skill()
		{
			return new SkillRef(skillId, skillLevel);
		}

		public String stableKey()
		{
			return skill().stableKey();
		}
	}

	public record EquipmentFact(int itemId, String bodyPart, String family, String weaponType, String armorType, String crystalGrade, String defaultAction, boolean stackable, int weight, ConditionPresence conditionPresence, Authority authority, String sourcePath)
	{
		public EquipmentFact
		{
			if ((itemId <= 0) || (bodyPart == null) || bodyPart.isBlank() || (family == null) || family.isBlank() || (crystalGrade == null) || crystalGrade.isBlank() || (defaultAction == null) || defaultAction.isBlank() || (weight < 0) || (authority != Authority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid equipment fact.");
			}
			weaponType = Objects.requireNonNullElse(weaponType, "");
			armorType = Objects.requireNonNullElse(armorType, "");
			Objects.requireNonNull(conditionPresence, "conditionPresence");
		}

		public String stableKey()
		{
			return key(itemId);
		}
	}

	public record PetSkillFact(int skillId, int skillLevel, int minimumPetLevel)
	{
		public PetSkillFact
		{
			if ((skillId <= 0) || (skillLevel < 0) || (minimumPetLevel < 0))
			{
				throw new IllegalArgumentException("Invalid pet skill fact.");
			}
		}

		public String stableKey()
		{
			return key(skillId) + ':' + key(skillLevel) + ':' + key(minimumPetLevel);
		}
	}

	public record PetFact(int npcId, int controlItemId, Set<Integer> foodItemIds, int minimumLevel, int maximumLevel, int load, int hungryLimit, boolean synchronizedLevel, boolean mountable, boolean inventorySupported, boolean pickupSupported, List<PetSkillFact> skills, Authority authority, String sourcePath)
	{
		public PetFact
		{
			if ((npcId <= 0) || (controlItemId < -1) || (minimumLevel < 0) || (maximumLevel < minimumLevel) || (load < 0) || (hungryLimit < 0) || (authority != Authority.SERVER_LOADER_FACT))
			{
				throw new IllegalArgumentException("Invalid pet fact.");
			}
			foodItemIds = Set.copyOf(foodItemIds);
			skills = List.copyOf(skills);
		}

		public String stableKey()
		{
			return key(npcId);
		}
	}

	public record SummonActorFact(List<Integer> ownerClassIds, int skillId, int skillLevel, int actorIdentity, ActorKind actorKind, int lifetimeMillis, double expMultiplier, int summonItemId, int upkeepItemId, int upkeepItemCount, int upkeepIntervalMillis, int controlItemId, Set<Integer> foodItemIds, int soulshotsPerHit, int spiritshotsPerHit, boolean mountable, boolean inventorySupported, boolean pickupSupported, boolean healCapability, boolean rechargeCapability, boolean buffCapability, boolean followSupported, boolean holdSupported, boolean moveSupported, boolean attackSupported, Authority authority, List<String> sourcePaths)
	{
		public SummonActorFact
		{
			if ((skillId <= 0) || (skillLevel <= 0) || (actorIdentity < 0) || (lifetimeMillis < 0) || !Double.isFinite(expMultiplier) || (expMultiplier < 0) || (summonItemId < 0) || (upkeepItemId < 0) || (upkeepItemCount < 0) || (upkeepIntervalMillis < 0) || (controlItemId < 0) || (soulshotsPerHit < 0) || (spiritshotsPerHit < 0))
			{
				throw new IllegalArgumentException("Invalid controlled actor fact.");
			}
			ownerClassIds = List.copyOf(ownerClassIds);
			Objects.requireNonNull(actorKind, "actorKind");
			foodItemIds = Set.copyOf(foodItemIds);
			Objects.requireNonNull(authority, "authority");
			sourcePaths = List.copyOf(sourcePaths);
		}

		public SkillRef skill()
		{
			return new SkillRef(skillId, skillLevel);
		}

		public String stableKey()
		{
			return skill().stableKey() + ':' + actorKind.ordinal() + ':' + key(actorIdentity) + ':' + key(controlItemId);
		}
	}

	public record CapabilityRule(String capabilityKey, int rank, List<Integer> classIds, List<SkillRef> evidenceSkills, TargetScope targetScope, Set<String> requiredEquipmentFamilies, List<RequiredItem> requiredItems, boolean targetRequired, boolean summonRequired, boolean servitorRequired, Authority authority, List<String> sourcePaths)
	{
		public CapabilityRule
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (rank < 1) || (rank > 1000) || classIds.isEmpty() || evidenceSkills.isEmpty() || (authority != Authority.CURATED_CAPABILITY_RULE))
			{
				throw new IllegalArgumentException("Invalid capability rule.");
			}
			classIds = List.copyOf(classIds);
			evidenceSkills = List.copyOf(evidenceSkills);
			Objects.requireNonNull(targetScope, "targetScope");
			requiredEquipmentFamilies = Set.copyOf(requiredEquipmentFamilies);
			requiredItems = List.copyOf(requiredItems);
			sourcePaths = List.copyOf(sourcePaths);
		}

		public String stableKey()
		{
			return capabilityKey + ':' + key(classIds.getFirst()) + ':' + key(rank);
		}
	}

	public record PageRequest(String afterKey, int limit)
	{
		public PageRequest
		{
			if ((afterKey != null) && afterKey.isBlank())
			{
				throw new IllegalArgumentException("Invalid page cursor.");
			}
			if ((limit < 1) || (limit > 256))
			{
				throw new IllegalArgumentException("Page limit must be between 1 and 256.");
			}
		}

		public static PageRequest first(int limit)
		{
			return new PageRequest(null, limit);
		}
	}

	public record Page<T>(List<T> values, String nextCursor, boolean hasMore)
	{
		public Page
		{
			values = List.copyOf(values);
			if (!hasMore && (nextCursor != null))
			{
				throw new IllegalArgumentException("Terminal page has a cursor.");
			}
		}
	}

	public record SubclassFact(int classIndex, int classId, int level, long exp, long sp)
	{
		public SubclassFact
		{
			if ((classIndex < 1) || (classId < 0) || (level < 1) || (exp < 0) || (sp < 0))
			{
				throw new IllegalArgumentException("Invalid subclass fact.");
			}
		}
	}

	public record EquippedItemFact(int objectId, int itemId, int paperdollSlot)
	{
		public EquippedItemFact
		{
			if ((objectId <= 0) || (itemId <= 0) || (paperdollSlot < 0))
			{
				throw new IllegalArgumentException("Invalid equipped item fact.");
			}
		}
	}

	public record OwnedEquipmentFact(int objectId, int itemId, String bodyPart, String family, String grade, int enchant, boolean equipped, boolean canonicalCompatibility, List<String> compatibilityReasons, long deterministicPreferenceScore)
	{
		public OwnedEquipmentFact
		{
			if ((objectId <= 0) || (itemId <= 0) || (bodyPart == null) || bodyPart.isBlank() || (family == null) || family.isBlank() || (grade == null) || grade.isBlank() || (enchant < 0))
			{
				throw new IllegalArgumentException("Invalid owned equipment fact.");
			}
			compatibilityReasons = List.copyOf(compatibilityReasons);
		}

		public String stableKey()
		{
			return String.format("%020d", Long.MAX_VALUE - deterministicPreferenceScore) + ':' + key(itemId) + ':' + key(objectId);
		}
	}

	public record ControlledActorFact(int objectId, int actorIdentity, ActorKind actorKind, int referenceSkillId)
	{
		public ControlledActorFact
		{
			if ((objectId < 0) || (actorIdentity < 0) || (referenceSkillId < 0))
			{
				throw new IllegalArgumentException("Invalid active controlled actor.");
			}
			Objects.requireNonNull(actorKind, "actorKind");
		}
	}

	public record ActorProgressionSnapshot(long profileId, int actorObjectId, int baseClassId, int activeClassId, int classIndex, int activeClassTier, int level, long exp, long sp, boolean noble, boolean hero, boolean subclassActive, List<SubclassFact> subclasses, Map<Integer, Integer> learnedSkills, List<EquippedItemFact> equippedItems, List<OwnedEquipmentFact> ownedEquipment, Map<Integer, Long> resourceItemCounts, List<ControlledActorFact> controlledActors, boolean transformed, boolean mounted, boolean inCombat, boolean casting, boolean dead, boolean subclassQuestSatisfied, int maximumSubclasses, Set<Integer> certificationSkillIds, String catalogCombinedHash)
	{
		public ActorProgressionSnapshot
		{
			if ((profileId <= 0) || (actorObjectId <= 0) || (baseClassId < 0) || (activeClassId < 0) || (classIndex < 0) || (activeClassTier < 0) || (level < 1) || (exp < 0) || (sp < 0) || (maximumSubclasses < 0) || (catalogCombinedHash == null) || catalogCombinedHash.isBlank())
			{
				throw new IllegalArgumentException("Invalid actor progression snapshot.");
			}
			subclasses = List.copyOf(subclasses);
			learnedSkills = Map.copyOf(learnedSkills);
			equippedItems = List.copyOf(equippedItems);
			ownedEquipment = List.copyOf(ownedEquipment);
			resourceItemCounts = Map.copyOf(resourceItemCounts);
			controlledActors = List.copyOf(controlledActors);
			certificationSkillIds = Set.copyOf(certificationSkillIds);
		}

		public boolean knows(SkillRef skill)
		{
			return learnedSkills.getOrDefault(skill.skillId(), 0) >= skill.skillLevel();
		}
	}

	public record ActorSnapshotResult(SnapshotStatus status, ActorProgressionSnapshot snapshot)
	{
		public ActorSnapshotResult
		{
			Objects.requireNonNull(status, "status");
			if ((status == SnapshotStatus.FOUND) != (snapshot != null))
			{
				throw new IllegalArgumentException("Actor snapshot status and value disagree.");
			}
		}
	}

	public record CapabilityEvaluation(String capabilityKey, int rank, TargetScope targetScope, boolean intrinsic, boolean learned, boolean readyNow, ReadinessReason reason, List<SkillRef> evidenceSkills)
	{
		public CapabilityEvaluation
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (rank < 1) || (rank > 1000))
			{
				throw new IllegalArgumentException("Invalid capability evaluation.");
			}
			Objects.requireNonNull(targetScope, "targetScope");
			Objects.requireNonNull(reason, "reason");
			evidenceSkills = List.copyOf(evidenceSkills);
			if (readyNow && (!intrinsic || !learned || (reason != ReadinessReason.READY)))
			{
				throw new IllegalArgumentException("Ready capability lacks intrinsic/learned truth.");
			}
		}

		public String stableKey()
		{
			return capabilityKey + ':' + key(rank);
		}
	}

	public record SkillReadinessProbe(boolean dynamicConditionSatisfied, boolean sufficientMpAndHp, boolean enabled)
	{
	}

	public record ProfessionTarget(int currentClassId, int targetClassId, int targetTier, int minimumLevelFact, boolean structurallyValid, boolean canonicalQuestRequired, ProfessionStatus status)
	{
		public ProfessionTarget
		{
			if ((currentClassId < 0) || (targetClassId < 0) || (targetTier < 0) || (minimumLevelFact < 1))
			{
				throw new IllegalArgumentException("Invalid profession target.");
			}
			Objects.requireNonNull(status, "status");
		}

		public String stableKey()
		{
			return key(targetClassId);
		}
	}

	public record SubclassEligibility(int classId, boolean categoryEligible, boolean trainerEligible, boolean raceEligible, boolean alreadyUsed, boolean levelReady, boolean capacityReady, boolean questReady)
	{
		public String stableKey()
		{
			return key(classId);
		}
	}

	public record LearnSkillRequest(long profileId, int trainerObjectId, AcquireKind acquireKind, int skillId, int skillLevel, PhantomCancellationToken planOwnershipToken)
	{
		public LearnSkillRequest
		{
			if ((profileId <= 0) || (trainerObjectId <= 0) || (skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid learn skill request.");
			}
			Objects.requireNonNull(acquireKind, "acquireKind");
			Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
		}
	}

	public record EquipItemRequest(long profileId, int itemObjectId, PhantomCancellationToken planOwnershipToken)
	{
		public EquipItemRequest
		{
			if ((profileId <= 0) || (itemObjectId <= 0))
			{
				throw new IllegalArgumentException("Invalid equip item request.");
			}
			Objects.requireNonNull(planOwnershipToken, "planOwnershipToken");
		}
	}

	public record OperationResult(OperationStatus status, long spBefore, long spAfter, Map<Integer, Long> itemCountsBefore, Map<Integer, Long> itemCountsAfter, int resultingSkillLevel, boolean equipped)
	{
		public OperationResult
		{
			Objects.requireNonNull(status, "status");
			itemCountsBefore = Map.copyOf(itemCountsBefore);
			itemCountsAfter = Map.copyOf(itemCountsAfter);
		}

		public static OperationResult rejected(OperationStatus status)
		{
			return new OperationResult(status, 0, 0, Map.of(), Map.of(), 0, false);
		}
	}

	private static String key(int value)
	{
		return String.format("%010d", value);
	}
}
