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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;

/**
 * BACKGROUND_MODEL_V1 is a deterministic bounded approximation. Reward and drop
 * policy inputs preserve the current server formulas, while combat duration and
 * attrition intentionally do not claim retail combat equivalence.
 */
public final class PhantomBackgroundModel
{
	public static final int MAX_ENCOUNTERS = 32;
	public static final long MAX_ELAPSED_MILLIS = 60_000;
	public static final int MAX_CHANGED_ITEM_OBJECTS = 16;
	public static final int MAX_NEW_NON_STACKABLE_OBJECTS = 8;
	public static final int MAX_GROUND_LOSS_ITEM_IDS = 96;
	private static final long MIN_ENCOUNTER_MILLIS = 500;
	private static final long MAX_ENCOUNTER_MILLIS = 20_000;

	public BatchResult evaluate(BatchRequest request)
	{
		Objects.requireNonNull(request, "request");
		final PhantomBackgroundState state = request.state();
		if (!state.acceptsBackgroundWork())
		{
			return BatchResult.retry(ResultReason.STATE_NOT_READY, state.clock().rngState());
		}
		if ((state.position().instanceId() != 0) || request.unsupportedContext())
		{
			return BatchResult.retry(ResultReason.UNSUPPORTED_CONTEXT, state.clock().rngState());
		}
		if ((request.mode() != BatchMode.ORDINARY_DEATH_DROP) && !request.acquisitionEligible())
		{
			return BatchResult.retry(ResultReason.ACQUISITION_INELIGIBLE, state.clock().rngState());
		}
		final Target target = request.target();
		if (!target.normalMonster())
		{
			return BatchResult.retry(ResultReason.UNSUPPORTED_TARGET, state.clock().rngState());
		}

		final DeterministicRandom random = new DeterministicRandom(state.clock().rngState());
		final Map<Integer, Long> counts = itemCounts(state.inventory());
		final Map<Integer, Long> deltas = new LinkedHashMap<>();
		final Map<Integer, Long> groundLosses = new LinkedHashMap<>();
		double hp = state.vitals().currentHp();
		double mp = state.vitals().currentMp();
		long experience = state.progress().experience();
		long skillPoints = state.progress().skillPoints();
		long experienceBeforeDeath = state.progress().experienceBeforeDeath();
		long elapsed = state.clock().residualEncounterMillis();
		long addedWeight = 0;
		int addedSlots = 0;
		int newNonStackable = 0;
		int encounters = 0;
		long acquisitionTargetDelta = 0;
		boolean dead = false;
		ResultReason reason = ResultReason.TIME_BUDGET;

		while ((encounters < MAX_ENCOUNTERS) && (elapsed < MAX_ELAPSED_MILLIS))
		{
			final long encounterMillis = encounterMillis(state.combat(), target, random);
			if ((elapsed + encounterMillis) > MAX_ELAPSED_MILLIS)
			{
				reason = ResultReason.TIME_BUDGET;
				break;
			}
			final Loadout loadout = state.loadout();
			if ((mp < loadout.skillMpPerEncounter()) || !has(counts, loadout.shotItemId(), loadout.shotsPerEncounter()) || !has(counts, loadout.summonResourceItemId(), loadout.summonResourcesPerEncounter()))
			{
				reason = ResultReason.RESOURCE_RESERVE;
				break;
			}

			final DropRoll roll;
			if (request.mode() == BatchMode.ACQUISITION_SPOIL_SWEEP)
			{
				final List<Drop> deathDrops = target.drops().stream().filter(drop -> drop.origin() != DropOrigin.ACQUISITION_TARGET).toList();
				final Target deathTarget = new Target(target.npcId(), target.level(), target.normalMonster(), target.maximumHp(), target.maximumMp(), target.physicalOffense(), target.magicOffense(), target.physicalDefense(), target.magicDefense(), target.attackSpeed(), target.castSpeed(), target.baseExperience(), target.baseSkillPoints(), deathDrops, target.maximumRandomDropOccurrences());
				roll = mergeRolls(rollDrops(deathTarget, random), rollSpoil(target.drops().stream().filter(drop -> drop.origin() == DropOrigin.ACQUISITION_TARGET).toList(), random));
			}
			else
			{
				roll = rollDrops(target, random);
			}
			final Map<Integer, Long> prospectiveDeltas = new LinkedHashMap<>(deltas);
			mergeDelta(prospectiveDeltas, loadout.shotItemId(), -loadout.shotsPerEncounter());
			mergeDelta(prospectiveDeltas, loadout.summonResourceItemId(), -loadout.summonResourcesPerEncounter());
			for (Map.Entry<Integer, Long> drop : roll.acquiredItemCounts().entrySet())
			{
				mergeDelta(prospectiveDeltas, drop.getKey(), drop.getValue());
			}
			final InventoryCheck inventoryCheck = inventoryCheck(state.inventory(), counts, roll, prospectiveDeltas, addedWeight, addedSlots, newNonStackable);
			if (!inventoryCheck.accepted())
			{
				reason = inventoryCheck.reason();
				break;
			}
			acquisitionTargetDelta = Math.addExact(acquisitionTargetDelta, roll.acquisitionCounts().getOrDefault(request.targetItemId(), 0L));

			consume(counts, deltas, loadout.shotItemId(), loadout.shotsPerEncounter());
			consume(counts, deltas, loadout.summonResourceItemId(), loadout.summonResourcesPerEncounter());
			mp -= loadout.skillMpPerEncounter();
			final double seconds = encounterMillis / 1000d;
			mp = Math.min(state.vitals().maximumMp(), mp + (state.combat().mpRegenPerSecond() * seconds));

			for (Map.Entry<Integer, Long> drop : roll.acquiredItemCounts().entrySet())
			{
				counts.merge(drop.getKey(), drop.getValue(), Math::addExact);
				deltas.merge(drop.getKey(), drop.getValue(), Math::addExact);
			}
			for (Map.Entry<Integer, Long> loss : roll.groundLosses().entrySet())
			{
				groundLosses.merge(loss.getKey(), loss.getValue(), Math::addExact);
			}
			addedWeight = inventoryCheck.addedWeight();
			addedSlots = inventoryCheck.addedSlots();
			newNonStackable = inventoryCheck.newNonStackableObjects();

			final Rewards rewards = calculateRewards(state.progress().level(), target, request.rewardPolicy(), state.combat());
			experience = Math.addExact(experience, rewards.experience());
			skillPoints = Math.addExact(skillPoints, rewards.skillPoints());
			final double incoming = incomingDamage(state.combat(), target, encounterMillis, random);
			hp = Math.min(state.vitals().maximumHp(), hp + (state.combat().hpRegenPerSecond() * seconds));
			hp = Math.max(0, hp - incoming);
			elapsed += encounterMillis;
			encounters++;
			if (hp == 0)
			{
				experienceBeforeDeath = experience;
				experience = Math.max(0, experience - calculateDeathExperienceLoss(request.deathPolicy(), request.experienceTable(), request.levelForExperience().levelFor(experience), experience));
				dead = true;
				reason = ResultReason.DEAD;
				break;
			}
			reason = ResultReason.COMPLETED;
			if ((request.targetItemId() > 0) && (acquisitionTargetDelta >= request.maximumTargetAmount()))
			{
				break;
			}
		}

		final int nextLevel = request.levelForExperience().levelFor(experience);
		final Progress progress = new Progress(nextLevel, experience, skillPoints, experienceBeforeDeath);
		final Vitals vitals = new Vitals(hp, state.vitals().maximumHp(), mp, state.vitals().maximumMp(), dead ? 0 : state.vitals().currentCp(), state.vitals().maximumCp());
		final InventoryDelta inventoryDelta = new InventoryDelta(Map.copyOf(deltas), addedWeight, addedSlots, newNonStackable);
		return new BatchResult(reason, encounters, elapsed, progress, vitals, inventoryDelta, Map.copyOf(groundLosses), random.state(), dead, acquisitionTargetDelta);
	}

	public static Rewards calculateRewards(int actorLevel, Target target, RewardPolicy policy, CombatFacts combat)
	{
		final int levelDifference = actorLevel - target.level();
		double experience = 0;
		double skillPoints = 0;
		if ((levelDifference < policy.maximumLevelDifference()) && (levelDifference > -policy.maximumLevelDifference()))
		{
			experience = Math.max(0, (long) (target.baseExperience() * policy.experienceRate()));
			skillPoints = Math.max(0, (int) (target.baseSkillPoints() * policy.skillPointRate()));
			if ((actorLevel > 84) && (levelDifference <= -3))
			{
				final double multiplier = switch (levelDifference)
				{
					case -3 -> 0.97;
					case -4 -> 0.67;
					case -5 -> 0.42;
					case -6 -> 0.25;
					case -7 -> 0.15;
					case -8 -> 0.09;
					case -9 -> 0.05;
					case -10 -> 0.03;
					default -> 1;
				};
				experience *= multiplier;
				skillPoints *= multiplier;
			}
		}
		experience *= combat.experienceMultiplier() * (1 - combat.servitorExperienceMultiplier());
		skillPoints *= combat.skillPointMultiplier();
		return new Rewards(Math.max(0, Math.round(experience)), Math.max(0, (long) skillPoints));
	}

	public static long calculateDeathExperienceLoss(DeathPolicy policy, ExperienceTable experienceTable, int level, long currentExperience)
	{
		if ((level < 1) || (level > experienceTable.maximumLevel()))
		{
			return 0;
		}
		final boolean maximumLevel = level == experienceTable.maximumLevel();
		final long levelStart = experienceTable.experienceForLevel(maximumLevel ? level - 1 : level);
		final long nextLevel = experienceTable.experienceForLevel(maximumLevel ? level : level + 1);
		final double lossPercent = policy.lossPercent(level) * policy.normalMonsterReductionMultiplier();
		final long calculated = Math.round((nextLevel - levelStart) * lossPercent / 100d);
		return Math.min(Math.min(calculated, currentExperience), Math.round((nextLevel - levelStart) * 0.10d));
	}

	private static long encounterMillis(CombatFacts combat, Target target, DeterministicRandom random)
	{
		final boolean magic = combat.modelKind() == ModelKind.MAGIC;
		final double offense = magic ? combat.magicOffense() : combat.physicalOffense();
		final double targetDefense = magic ? target.magicDefense() : target.physicalDefense();
		final double speed = magic ? combat.castSpeed() : combat.attackSpeed();
		final double effectiveDamage = Math.max(1, offense * 100d / (targetDefense + 100d));
		final double cyclesPerSecond = Math.max(0.1, speed / 500d);
		final long base = Math.round((target.maximumHp() / (effectiveDamage * cyclesPerSecond)) * 1000d);
		return Math.clamp(Math.round(base * random.variance()), MIN_ENCOUNTER_MILLIS, MAX_ENCOUNTER_MILLIS);
	}

	private static double incomingDamage(CombatFacts combat, Target target, long encounterMillis, DeterministicRandom random)
	{
		final boolean targetMagic = target.magicOffense() > target.physicalOffense();
		final double offense = targetMagic ? target.magicOffense() : target.physicalOffense();
		final double defense = targetMagic ? combat.magicDefense() : combat.physicalDefense();
		final double speed = targetMagic ? target.castSpeed() : target.attackSpeed();
		final double perCycle = Math.max(1, offense * 100d / (defense + 100d));
		final double cycles = (encounterMillis / 1000d) * Math.max(0.1, speed / 500d);
		return perCycle * cycles * random.variance();
	}

	private static DropRoll rollDrops(Target target, DeterministicRandom random)
	{
		final Map<Integer, Long> items = new LinkedHashMap<>();
		final Map<Integer, Long> acquisitionCounts = new LinkedHashMap<>();
		final Map<Integer, Long> groundLosses = new LinkedHashMap<>();
		final Map<Integer, Drop> facts = new HashMap<>();
		final List<Drop> sorted = target.drops().stream().sorted(Comparator.comparingInt(Drop::groupOrdinal).thenComparingInt(Drop::itemOrdinal).thenComparingInt(Drop::itemId)).toList();
		final Map<Integer, List<Drop>> groups = new LinkedHashMap<>();
		for (Drop drop : sorted)
		{
			if (drop.groupOrdinal() >= 0)
			{
				groups.computeIfAbsent(drop.groupOrdinal(), _ -> new ArrayList<>()).add(drop);
			}
		}
		if (target.maximumRandomDropOccurrences() > 0)
		{
			int remainingOccurrences = target.maximumRandomDropOccurrences();
			final List<Award> calculated = new ArrayList<>();
			final List<Award> randomAwards = new ArrayList<>();
			Award cached = null;
			for (List<Drop> group : groups.values())
			{
				double cumulative = 0;
				for (Drop drop : group)
				{
					cumulative = drop.chanceMultiplier() == 1 ? cumulative + drop.rawItemChance() : drop.rawItemChance();
					final double chance = cumulative * (drop.rawGroupChance() / 100d) * drop.chanceMultiplier();
					if ((remainingOccurrences == 0) && (chance < 100) && !calculated.isEmpty())
					{
						if (drop.chanceMultiplier() == 1)
						{
							cached = randomAwards.removeFirst();
							calculated.remove(cached);
						}
						remainingOccurrences = 1;
					}
					if (!passesLevelGap(drop, random) || ((random.nextDouble() * 100) >= chance))
					{
						continue;
					}
					final long amount = scaledAmount(drop, random);
					if (amount > 0)
					{
						final Award award = new Award(drop, amount);
						calculated.add(award);
						if (isRandomOccurrence(drop, chance))
						{
							remainingOccurrences--;
							if (drop.chanceMultiplier() == 1)
							{
								randomAwards.add(award);
							}
						}
					}
					if (drop.chanceMultiplier() == 1)
					{
						break;
					}
				}
			}
			if ((remainingOccurrences > 0) && (cached != null))
			{
				calculated.add(cached);
			}
			addAwards(items, acquisitionCounts, groundLosses, facts, calculated);
		}

		if (target.maximumRandomDropOccurrences() > 0)
		{
			int remainingOccurrences = target.maximumRandomDropOccurrences();
			final List<Award> calculated = new ArrayList<>();
			final List<Award> randomAwards = new ArrayList<>();
			Award cached = null;
			for (Drop drop : sorted)
			{
				if (drop.groupOrdinal() >= 0)
				{
					continue;
				}
				if ((remainingOccurrences == 0) && (drop.rawItemChance() < 100) && !calculated.isEmpty())
				{
					cached = randomAwards.removeFirst();
					calculated.remove(cached);
					remainingOccurrences = 1;
				}
				if (!passesLevelGap(drop, random))
				{
					continue;
				}
				final double chance = drop.rawItemChance() * drop.chanceMultiplier();
				if ((random.nextDouble() * 100) >= chance)
				{
					continue;
				}
				final long amount = scaledAmount(drop, random);
				if (amount > 0)
				{
					final Award award = new Award(drop, amount);
					calculated.add(award);
					if (isRandomOccurrence(drop, drop.rawItemChance()))
					{
						remainingOccurrences--;
						randomAwards.add(award);
					}
				}
			}
			if ((remainingOccurrences > 0) && (cached != null))
			{
				calculated.add(cached);
			}
			addAwards(items, acquisitionCounts, groundLosses, facts, calculated);
		}
		return new DropRoll(Map.copyOf(items), Map.copyOf(acquisitionCounts), Map.copyOf(groundLosses), Map.copyOf(facts));
	}

	private static DropRoll rollSpoil(List<Drop> drops, DeterministicRandom random)
	{
		final Map<Integer, Long> items = new LinkedHashMap<>();
		final Map<Integer, Long> acquisition = new LinkedHashMap<>();
		final Map<Integer, Drop> facts = new LinkedHashMap<>();
		for (Drop drop : drops)
		{
			if (passesLevelGap(drop, random) && ((random.nextDouble() * 100) < (drop.rawItemChance() * drop.chanceMultiplier())))
			{
				final long amount = scaledAmount(drop, random);
				if (amount > 0)
				{
					items.merge(drop.itemId(), amount, Math::addExact);
					acquisition.merge(drop.itemId(), amount, Math::addExact);
					facts.put(drop.itemId(), drop);
				}
			}
		}
		return new DropRoll(Map.copyOf(items), Map.copyOf(acquisition), Map.of(), Map.copyOf(facts));
	}

	private static DropRoll mergeRolls(DropRoll left, DropRoll right)
	{
		final Map<Integer, Long> items = mergeCounts(left.acquiredItemCounts(), right.acquiredItemCounts());
		final Map<Integer, Long> acquisition = mergeCounts(left.acquisitionCounts(), right.acquisitionCounts());
		final Map<Integer, Long> losses = mergeCounts(left.groundLosses(), right.groundLosses());
		final Map<Integer, Drop> facts = new LinkedHashMap<>(left.facts());
		facts.putAll(right.facts());
		return new DropRoll(Map.copyOf(items), Map.copyOf(acquisition), Map.copyOf(losses), Map.copyOf(facts));
	}

	private static Map<Integer, Long> mergeCounts(Map<Integer, Long> left, Map<Integer, Long> right)
	{
		final Map<Integer, Long> result = new LinkedHashMap<>(left);
		right.forEach((itemId, count) -> result.merge(itemId, count, Math::addExact));
		return result;
	}

	private static boolean isRandomOccurrence(Drop drop, double effectiveChance)
	{
		return drop.configuredChanceMultiplier() == null ? effectiveChance < 100 : (effectiveChance * drop.configuredChanceMultiplier()) < 100;
	}

	private static void addAwards(Map<Integer, Long> items, Map<Integer, Long> acquisitionCounts, Map<Integer, Long> groundLosses, Map<Integer, Drop> facts, List<Award> awards)
	{
		for (Award award : awards)
		{
			if (award.drop().disposition() == DropDisposition.ACQUIRE)
			{
				items.merge(award.drop().itemId(), award.amount(), Math::addExact);
				if (award.drop().origin() == DropOrigin.ACQUISITION_TARGET)
				{
					acquisitionCounts.merge(award.drop().itemId(), award.amount(), Math::addExact);
				}
				facts.put(award.drop().itemId(), award.drop());
			}
			else
			{
				groundLosses.merge(award.drop().itemId(), award.amount(), Math::addExact);
			}
		}
	}

	private static boolean passesLevelGap(Drop drop, DeterministicRandom random)
	{
		return (random.nextDouble() * 100) <= drop.levelGapChance();
	}

	private static long scaledAmount(Drop drop, DeterministicRandom random)
	{
		final long raw = random.nextLong(drop.minimumCount(), drop.maximumCount());
		return (long) (raw * drop.amountMultiplier());
	}

	private static InventoryCheck inventoryCheck(InventoryFacts inventory, Map<Integer, Long> currentCounts, DropRoll roll, Map<Integer, Long> prospectiveDeltas, long priorWeight, int priorSlots, int priorNewNonStackable)
	{
		long addedWeight = priorWeight;
		int addedSlots = priorSlots;
		int newNonStackable = priorNewNonStackable;
		for (Map.Entry<Integer, Long> entry : roll.acquiredItemCounts().entrySet())
		{
			final Drop drop = roll.facts().get(entry.getKey());
			addedWeight = Math.addExact(addedWeight, Math.multiplyExact(entry.getValue(), drop.itemWeight()));
			if (!drop.stackable())
			{
				if (entry.getValue() > (MAX_NEW_NON_STACKABLE_OBJECTS - newNonStackable))
				{
					return InventoryCheck.rejected(ResultReason.OBJECT_CAP);
				}
				newNonStackable += (int) entry.getValue().longValue();
				addedSlots += (int) entry.getValue().longValue();
			}
			else if (!currentCounts.containsKey(entry.getKey()))
			{
				addedSlots++;
			}
		}
		if (changedObjectCount(inventory, prospectiveDeltas, roll.facts()) > MAX_CHANGED_ITEM_OBJECTS)
		{
			return InventoryCheck.rejected(ResultReason.OBJECT_CAP);
		}
		if ((inventory.currentLoad() + addedWeight) > inventory.maximumLoad())
		{
			return InventoryCheck.rejected(ResultReason.WEIGHT_CAPACITY);
		}
		if ((inventory.usedSlots() + addedSlots) > inventory.maximumSlots())
		{
			return InventoryCheck.rejected(ResultReason.SLOT_CAPACITY);
		}
		return new InventoryCheck(true, ResultReason.COMPLETED, addedWeight, addedSlots, newNonStackable);
	}

	private static int changedObjectCount(InventoryFacts inventory, Map<Integer, Long> deltas, Map<Integer, Drop> dropFacts)
	{
		int changed = 0;
		for (Map.Entry<Integer, Long> mutation : deltas.entrySet())
		{
			final long delta = mutation.getValue();
			if (delta == 0)
			{
				continue;
			}
			final List<ItemObject> existing = inventory.objects().stream().filter(object -> (object.itemId() == mutation.getKey()) && (object.location() == PhantomBackgroundState.ItemLocation.INVENTORY)).sorted(Comparator.comparingInt(ItemObject::objectId)).toList();
			if (delta < 0)
			{
				long remaining = -delta;
				for (ItemObject object : existing)
				{
					if (remaining == 0)
					{
						break;
					}
					remaining -= Math.min(remaining, object.count());
					changed++;
				}
			}
			else
			{
				final boolean stackable = !existing.isEmpty() ? existing.getFirst().stackable() : dropFacts.containsKey(mutation.getKey()) && dropFacts.get(mutation.getKey()).stackable();
				changed = Math.addExact(changed, stackable ? 1 : Math.toIntExact(delta));
			}
		}
		return changed;
	}

	private static Map<Integer, Long> itemCounts(InventoryFacts inventory)
	{
		final Map<Integer, Long> result = new HashMap<>();
		for (PhantomBackgroundState.ItemObject object : inventory.objects())
		{
			if (object.location() == PhantomBackgroundState.ItemLocation.INVENTORY)
			{
				result.merge(object.itemId(), object.count(), Math::addExact);
			}
		}
		return result;
	}

	private static boolean has(Map<Integer, Long> counts, int itemId, long amount)
	{
		return (itemId == 0) || (counts.getOrDefault(itemId, 0L) >= amount);
	}

	private static void consume(Map<Integer, Long> counts, Map<Integer, Long> deltas, int itemId, long amount)
	{
		if ((itemId == 0) || (amount == 0))
		{
			return;
		}
		counts.compute(itemId, (_, count) -> count - amount);
		deltas.merge(itemId, -amount, Math::addExact);
	}

	private static void mergeDelta(Map<Integer, Long> deltas, int itemId, long amount)
	{
		if ((itemId != 0) && (amount != 0))
		{
			deltas.merge(itemId, amount, Math::addExact);
		}
	}

	public enum ResultReason
	{
		COMPLETED,
		TIME_BUDGET,
		RESOURCE_RESERVE,
		WEIGHT_CAPACITY,
		SLOT_CAPACITY,
		OBJECT_CAP,
		DEAD,
		STATE_NOT_READY,
		ACQUISITION_INELIGIBLE,
		UNSUPPORTED_CONTEXT,
		UNSUPPORTED_TARGET
	}

	public enum DropDisposition
	{
		ACQUIRE,
		LEAVE_ON_GROUND
	}

	public enum DropOrigin
	{
		ORDINARY,
		INCIDENTAL_DEATH_DROP,
		ACQUISITION_TARGET
	}

	public enum BatchMode
	{
		ORDINARY_DEATH_DROP,
		ACQUISITION_DEATH_DROP,
		ACQUISITION_SPOIL_SWEEP
	}

	public record BatchRequest(PhantomBackgroundState state, Target target, RewardPolicy rewardPolicy, DeathPolicy deathPolicy, ExperienceTable experienceTable, LevelForExperience levelForExperience, boolean unsupportedContext, BatchMode mode, int targetItemId, long maximumTargetAmount, boolean acquisitionEligible)
	{
		public BatchRequest(PhantomBackgroundState state, Target target, RewardPolicy rewardPolicy, DeathPolicy deathPolicy, ExperienceTable experienceTable, LevelForExperience levelForExperience, boolean unsupportedContext)
		{
			this(state, target, rewardPolicy, deathPolicy, experienceTable, levelForExperience, unsupportedContext, BatchMode.ORDINARY_DEATH_DROP, 0, 0, true);
		}

		public BatchRequest
		{
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(rewardPolicy, "rewardPolicy");
			Objects.requireNonNull(deathPolicy, "deathPolicy");
			Objects.requireNonNull(experienceTable, "experienceTable");
			Objects.requireNonNull(levelForExperience, "levelForExperience");
			Objects.requireNonNull(mode, "mode");
			if ((mode == BatchMode.ORDINARY_DEATH_DROP) != (targetItemId == 0) || (targetItemId < 0) || (maximumTargetAmount < 0) || ((targetItemId > 0) && (maximumTargetAmount == 0)))
			{
				throw new IllegalArgumentException("Invalid background acquisition batch contract.");
			}
		}
	}

	public record Target(int npcId, int level, boolean normalMonster, double maximumHp, double maximumMp, double physicalOffense, double magicOffense, double physicalDefense, double magicDefense, double attackSpeed, double castSpeed, double baseExperience, double baseSkillPoints, List<Drop> drops, int maximumRandomDropOccurrences)
	{
		public Target
		{
			if ((npcId <= 0) || (level < 1) || !Double.isFinite(maximumHp) || (maximumHp <= 0) || !Double.isFinite(maximumMp) || (maximumMp < 0) || !Double.isFinite(baseExperience) || (baseExperience < 0) || !Double.isFinite(baseSkillPoints) || (baseSkillPoints < 0) || (maximumRandomDropOccurrences < 0))
			{
				throw new IllegalArgumentException("Invalid background target.");
			}
			drops = List.copyOf(drops);
			if (drops.stream().filter(drop -> drop.disposition() == DropDisposition.LEAVE_ON_GROUND).map(Drop::itemId).distinct().count() > MAX_GROUND_LOSS_ITEM_IDS)
			{
				throw new IllegalArgumentException("Background target exceeds the ground-loss evidence bound.");
			}
		}
	}

	public record Drop(int itemId, int groupOrdinal, int itemOrdinal, double rawGroupChance, double rawItemChance, long minimumCount, long maximumCount, double chanceMultiplier, Double configuredChanceMultiplier, double amountMultiplier, double levelGapChance, boolean stackable, int itemWeight, DropDisposition disposition, DropOrigin origin)
	{
		public Drop(int itemId, int groupOrdinal, int itemOrdinal, double rawGroupChance, double rawItemChance, long minimumCount, long maximumCount, double chanceMultiplier, Double configuredChanceMultiplier, double amountMultiplier, double levelGapChance, boolean stackable, int itemWeight)
		{
			this(itemId, groupOrdinal, itemOrdinal, rawGroupChance, rawItemChance, minimumCount, maximumCount, chanceMultiplier, configuredChanceMultiplier, amountMultiplier, levelGapChance, stackable, itemWeight, DropDisposition.ACQUIRE, DropOrigin.ORDINARY);
		}

		public Drop(int itemId, int groupOrdinal, int itemOrdinal, double rawGroupChance, double rawItemChance, long minimumCount, long maximumCount, double chanceMultiplier, Double configuredChanceMultiplier, double amountMultiplier, double levelGapChance, boolean stackable, int itemWeight, DropDisposition disposition)
		{
			this(itemId, groupOrdinal, itemOrdinal, rawGroupChance, rawItemChance, minimumCount, maximumCount, chanceMultiplier, configuredChanceMultiplier, amountMultiplier, levelGapChance, stackable, itemWeight, disposition, DropOrigin.ORDINARY);
		}

		public Drop
		{
			if ((itemId <= 0) || (groupOrdinal < -1) || (itemOrdinal < 0) || !finiteNonNegative(rawGroupChance) || !finiteNonNegative(rawItemChance) || (minimumCount < 0) || (maximumCount < minimumCount) || !finiteNonNegative(chanceMultiplier) || ((configuredChanceMultiplier != null) && !finiteNonNegative(configuredChanceMultiplier)) || !finiteNonNegative(amountMultiplier) || !Double.isFinite(levelGapChance) || (levelGapChance < 0) || (levelGapChance > 100) || (itemWeight < 0) || (disposition == null) || (origin == null))
			{
				throw new IllegalArgumentException("Invalid background drop fact.");
			}
		}

		private static boolean finiteNonNegative(double value)
		{
			return Double.isFinite(value) && (value >= 0);
		}
	}

	public record RewardPolicy(int maximumLevelDifference, double experienceRate, double skillPointRate)
	{
		public RewardPolicy
		{
			if ((maximumLevelDifference < 1) || !Double.isFinite(experienceRate) || (experienceRate < 0) || !Double.isFinite(skillPointRate) || (skillPointRate < 0))
			{
				throw new IllegalArgumentException("Invalid background reward policy.");
			}
		}
	}

	public interface DeathPolicy
	{
		double lossPercent(int level);

		double normalMonsterReductionMultiplier();
	}

	public interface ExperienceTable
	{
		long experienceForLevel(int level);

		int maximumLevel();
	}

	@FunctionalInterface
	public interface LevelForExperience
	{
		int levelFor(long experience);
	}

	public record Rewards(long experience, long skillPoints)
	{
	}

	public record InventoryDelta(Map<Integer, Long> itemDeltas, long addedWeight, int addedSlots, int newNonStackableObjects)
	{
		public InventoryDelta
		{
			itemDeltas = Map.copyOf(itemDeltas);
		}
	}

	public record BatchResult(ResultReason reason, int encounters, long elapsedMillis, Progress progress, Vitals vitals, InventoryDelta inventoryDelta, Map<Integer, Long> groundLosses, long nextRngState, boolean dead, long acquisitionTargetDelta)
	{
		public BatchResult(ResultReason reason, int encounters, long elapsedMillis, Progress progress, Vitals vitals, InventoryDelta inventoryDelta, Map<Integer, Long> groundLosses, long nextRngState, boolean dead)
		{
			this(reason, encounters, elapsedMillis, progress, vitals, inventoryDelta, groundLosses, nextRngState, dead, 0);
		}

		public BatchResult
		{
			groundLosses = Map.copyOf(groundLosses);
			if ((groundLosses.size() > MAX_GROUND_LOSS_ITEM_IDS) || groundLosses.entrySet().stream().anyMatch(entry -> (entry.getKey() <= 0) || (entry.getValue() <= 0)) || (acquisitionTargetDelta < 0))
			{
				throw new IllegalArgumentException("Invalid bounded ground-loss evidence.");
			}
		}

		public static BatchResult retry(ResultReason reason, long rngState)
		{
			return new BatchResult(reason, 0, 0, null, null, new InventoryDelta(Map.of(), 0, 0, 0), Map.of(), rngState, false, 0);
		}

		public boolean mutated()
		{
			return encounters > 0;
		}
	}

	private record DropRoll(Map<Integer, Long> acquiredItemCounts, Map<Integer, Long> acquisitionCounts, Map<Integer, Long> groundLosses, Map<Integer, Drop> facts)
	{
	}

	private record Award(Drop drop, long amount)
	{
	}

	private record InventoryCheck(boolean accepted, ResultReason reason, long addedWeight, int addedSlots, int newNonStackableObjects)
	{
		private static InventoryCheck rejected(ResultReason reason)
		{
			return new InventoryCheck(false, reason, 0, 0, 0);
		}
	}

	static final class DeterministicRandom
	{
		private long _state;

		DeterministicRandom(long seed)
		{
			_state = seed;
		}

		long state()
		{
			return _state;
		}

		double nextDouble()
		{
			return (nextLong() >>> 11) * 0x1.0p-53;
		}

		double variance()
		{
			return 0.9d + (nextDouble() * 0.2d);
		}

		long nextLong(long minimum, long maximum)
		{
			if (maximum <= minimum)
			{
				return minimum;
			}
			final long bound = Math.addExact(Math.subtractExact(maximum, minimum), 1);
			return minimum + Math.floorMod(nextLong(), bound);
		}

		private long nextLong()
		{
			long value = (_state += 0x9E3779B97F4A7C15L);
			value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
			value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
			return value ^ (value >>> 31);
		}
	}
}
