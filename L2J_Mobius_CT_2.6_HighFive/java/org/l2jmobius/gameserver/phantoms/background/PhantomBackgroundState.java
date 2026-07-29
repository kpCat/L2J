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
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Durable, bounded projection used by the versioned background farming model.
 */
public record PhantomBackgroundState(State state, Identity identity, Progress progress, Vitals vitals, Position position, CombatFacts combat, Loadout loadout, InventoryFacts inventory, List<AutoGetSkill> autoGetSkills, Clock clock, Receipt receipt, Hashes hashes)
{
	public static final String COMPONENT_TYPE = "background.state";
	public static final int SCHEMA_VERSION = 1;
	public static final int MODEL_VERSION = 1;
	public static final String MODEL_NAME = "BACKGROUND_MODEL_V1";
	public static final int MAX_TRACKED_ITEMS = 64;

	public PhantomBackgroundState
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(progress, "progress");
		Objects.requireNonNull(vitals, "vitals");
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(combat, "combat");
		Objects.requireNonNull(loadout, "loadout");
		Objects.requireNonNull(inventory, "inventory");
		autoGetSkills = List.copyOf(autoGetSkills);
		Objects.requireNonNull(clock, "clock");
		Objects.requireNonNull(receipt, "receipt");
		Objects.requireNonNull(hashes, "hashes");
		if ((state == State.DEAD) && (vitals.currentHp() != 0))
		{
			throw new IllegalArgumentException("DEAD background state must have zero HP.");
		}
		if ((state == State.READY) && (vitals.currentHp() == 0))
		{
			throw new IllegalArgumentException("READY background state must have positive HP.");
		}
		if (autoGetSkills.size() > 64)
		{
			throw new IllegalArgumentException("Too many tracked auto-get skills.");
		}
		int previousSkillId = 0;
		for (AutoGetSkill skill : autoGetSkills)
		{
			if (skill.skillId() <= previousSkillId)
			{
				throw new IllegalArgumentException("Auto-get skills must be unique and sorted.");
			}
			previousSkillId = skill.skillId();
		}
	}

	public boolean acceptsBackgroundWork()
	{
		return state == State.READY;
	}

	public PhantomBackgroundState withState(State nextState)
	{
		return new PhantomBackgroundState(nextState, identity, progress, vitals, position, combat, loadout, inventory, autoGetSkills, clock, receipt, hashes);
	}

	public PhantomBackgroundState after(Progress nextProgress, Vitals nextVitals, Position nextPosition, InventoryFacts nextInventory, List<AutoGetSkill> nextAutoGetSkills, Clock nextClock, Receipt nextReceipt)
	{
		final State nextState = nextVitals.currentHp() == 0 ? State.DEAD : State.READY;
		return new PhantomBackgroundState(nextState, identity, nextProgress, nextVitals, nextPosition, combat, loadout, nextInventory, nextAutoGetSkills, nextClock, nextReceipt, hashes);
	}

	public enum State
	{
		MATERIALIZED,
		READY,
		VERIFY_PENDING,
		DEAD,
		INCONSISTENT
	}

	public enum ModelKind
	{
		MELEE,
		RANGED,
		MAGIC,
		SUMMON_PRIMARY
	}

	public enum ItemLocation
	{
		INVENTORY,
		PAPERDOLL
	}

	public record Identity(long profileId, int characterObjectId, int classIndex, int activeClassId, int raceOrdinal)
	{
		public Identity
		{
			if ((profileId <= 0) || (characterObjectId <= 0) || (classIndex < 0) || (activeClassId < 0) || (raceOrdinal < 0) || (raceOrdinal > 255))
			{
				throw new IllegalArgumentException("Invalid background identity.");
			}
		}
	}

	public record Progress(int level, long experience, long skillPoints, long experienceBeforeDeath)
	{
		public Progress
		{
			if ((level < 1) || (level > 255) || (experience < 0) || (skillPoints < 0) || (experienceBeforeDeath < 0))
			{
				throw new IllegalArgumentException("Invalid background progression.");
			}
		}
	}

	public record Vitals(double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp)
	{
		public Vitals
		{
			requireFiniteRange(currentHp, maximumHp, "HP");
			requireFiniteRange(currentMp, maximumMp, "MP");
			requireFiniteRange(currentCp, maximumCp, "CP");
		}

		private static void requireFiniteRange(double current, double maximum, String name)
		{
			if (!Double.isFinite(current) || !Double.isFinite(maximum) || (maximum < 0) || (current < 0) || (current > maximum))
			{
				throw new IllegalArgumentException("Invalid background " + name + " range.");
			}
		}
	}

	public record Position(int instanceId, int x, int y, int z, int heading, String committedAnchorId)
	{
		public Position
		{
			if ((instanceId < 0) || (heading < 0) || (committedAnchorId == null) || committedAnchorId.isBlank() || (committedAnchorId.length() > 128))
			{
				throw new IllegalArgumentException("Invalid background position.");
			}
		}
	}

	public record CombatFacts(ModelKind modelKind, double physicalOffense, double magicOffense, double physicalDefense, double magicDefense, double attackSpeed, double castSpeed, double hpRegenPerSecond, double mpRegenPerSecond, double experienceMultiplier, double skillPointMultiplier, double servitorExperienceMultiplier, double dropChanceMultiplier, double dropAmountMultiplier, double adenaAmountMultiplier, double normalMonsterExperienceLossMultiplier)
	{
		public CombatFacts
		{
			Objects.requireNonNull(modelKind, "modelKind");
			requirePositive(physicalOffense, "physicalOffense");
			requirePositive(magicOffense, "magicOffense");
			requirePositive(physicalDefense, "physicalDefense");
			requirePositive(magicDefense, "magicDefense");
			requirePositive(attackSpeed, "attackSpeed");
			requirePositive(castSpeed, "castSpeed");
			requireNonNegative(hpRegenPerSecond, "hpRegenPerSecond");
			requireNonNegative(mpRegenPerSecond, "mpRegenPerSecond");
			requirePositive(experienceMultiplier, "experienceMultiplier");
			requirePositive(skillPointMultiplier, "skillPointMultiplier");
			if (!Double.isFinite(servitorExperienceMultiplier) || (servitorExperienceMultiplier < 0) || (servitorExperienceMultiplier > 1))
			{
				throw new IllegalArgumentException("Invalid servitor experience multiplier.");
			}
			requirePositive(dropChanceMultiplier, "dropChanceMultiplier");
			requirePositive(dropAmountMultiplier, "dropAmountMultiplier");
			requirePositive(adenaAmountMultiplier, "adenaAmountMultiplier");
			if (!Double.isFinite(normalMonsterExperienceLossMultiplier) || (normalMonsterExperienceLossMultiplier < 0))
			{
				throw new IllegalArgumentException("Invalid normal-monster experience-loss multiplier.");
			}
		}

		private static void requirePositive(double value, String name)
		{
			if (!Double.isFinite(value) || (value <= 0))
			{
				throw new IllegalArgumentException("Invalid " + name + ".");
			}
		}

		private static void requireNonNegative(double value, String name)
		{
			if (!Double.isFinite(value) || (value < 0))
			{
				throw new IllegalArgumentException("Invalid " + name + ".");
			}
		}
	}

	public record Loadout(int selectedSkillId, int selectedSkillLevel, int summonNpcId, int skillMpPerEncounter, int shotItemId, int shotsPerEncounter, int summonResourceItemId, int summonResourcesPerEncounter)
	{
		public Loadout
		{
			if ((selectedSkillId < 0) || (selectedSkillLevel < 0) || (summonNpcId < 0) || (skillMpPerEncounter < 0) || (shotItemId < 0) || (shotsPerEncounter < 0) || (summonResourceItemId < 0) || (summonResourcesPerEncounter < 0))
			{
				throw new IllegalArgumentException("Invalid background loadout.");
			}
			if ((selectedSkillId == 0) != (selectedSkillLevel == 0))
			{
				throw new IllegalArgumentException("Selected skill identity is incomplete.");
			}
			if ((shotItemId == 0) != (shotsPerEncounter == 0))
			{
				throw new IllegalArgumentException("Shot resource identity is incomplete.");
			}
			if ((summonResourceItemId == 0) != (summonResourcesPerEncounter == 0))
			{
				throw new IllegalArgumentException("Summon resource identity is incomplete.");
			}
		}

		public static Loadout none()
		{
			return new Loadout(0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	public record ItemObject(int objectId, int itemId, long count, boolean stackable, ItemLocation location)
	{
		public ItemObject
		{
			if ((objectId <= 0) || (itemId <= 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Invalid tracked item object.");
			}
			Objects.requireNonNull(location, "location");
		}
	}

	public record AutoGetSkill(int skillId, int skillLevel)
	{
		public AutoGetSkill
		{
			if ((skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid auto-get skill identity.");
			}
		}
	}

	public record InventoryFacts(List<ItemObject> objects, long currentLoad, long maximumLoad, int usedSlots, int maximumSlots)
	{
		public InventoryFacts
		{
			objects = List.copyOf(objects);
			if ((objects.size() > MAX_TRACKED_ITEMS) || (currentLoad < 0) || (maximumLoad < currentLoad) || (usedSlots < 0) || (maximumSlots < usedSlots))
			{
				throw new IllegalArgumentException("Invalid bounded background inventory.");
			}
			final Set<Integer> objectIds = new HashSet<>();
			int previous = 0;
			for (ItemObject object : objects)
			{
				if (!objectIds.add(object.objectId()) || (object.objectId() <= previous))
				{
					throw new IllegalArgumentException("Tracked item objects must be unique and sorted.");
				}
				previous = object.objectId();
			}
		}

		public static InventoryFacts sorted(List<ItemObject> objects, long currentLoad, long maximumLoad, int usedSlots, int maximumSlots)
		{
			return new InventoryFacts(objects.stream().sorted(Comparator.comparingInt(ItemObject::objectId)).toList(), currentLoad, maximumLoad, usedSlots, maximumSlots);
		}

		public long itemCount(int itemId)
		{
			return objects.stream().filter(object -> object.itemId() == itemId).mapToLong(ItemObject::count).sum();
		}
	}

	public record Clock(long rngState, long residualTravelMillis, long residualEncounterMillis)
	{
		public Clock
		{
			if ((residualTravelMillis < 0) || (residualEncounterMillis < 0))
			{
				throw new IllegalArgumentException("Invalid background residual time.");
			}
		}
	}

	public record Receipt(String operationKey, long activityGeneration, long tickSequence, String expectedAfterHash)
	{
		public Receipt
		{
			operationKey = boundedHash(operationKey, "operationKey");
			expectedAfterHash = boundedHash(expectedAfterHash, "expectedAfterHash");
			if ((activityGeneration < 0) || (tickSequence < 0))
			{
				throw new IllegalArgumentException("Invalid background receipt identity.");
			}
		}

		public static Receipt empty()
		{
			return new Receipt("", 0, 0, "");
		}
	}

	public record Hashes(String knowledge, String topology, String progression, String commerce)
	{
		public Hashes
		{
			knowledge = boundedHash(knowledge, "knowledge hash");
			topology = boundedHash(topology, "topology hash");
			progression = boundedHash(progression, "progression hash");
			commerce = boundedHash(commerce, "commerce hash");
		}
	}

	private static String boundedHash(String value, String name)
	{
		Objects.requireNonNull(value, name);
		if (value.length() > 128)
		{
			throw new IllegalArgumentException(name + " exceeds 128 characters.");
		}
		return value;
	}
}
