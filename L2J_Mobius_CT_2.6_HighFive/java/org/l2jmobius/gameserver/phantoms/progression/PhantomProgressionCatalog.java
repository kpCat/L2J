/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ClassFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Page;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearnFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;

/**
 * Immutable High Five class/progression snapshot. All mutable loader state is
 * copied by the builder before this object is published.
 */
public final class PhantomProgressionCatalog
{
	private final List<ClassFact> _classes;
	private final List<SkillLearnFact> _skillLearns;
	private final List<SkillFact> _skills;
	private final List<EquipmentFact> _equipment;
	private final List<SummonActorFact> _summons;
	private final List<PetFact> _pets;
	private final List<CapabilityRule> _capabilityRules;
	private final Map<Integer, ClassFact> _classesById;
	private final Map<Integer, List<ClassFact>> _childrenByClass;
	private final List<ClassFact> _terminalClasses;
	private final Map<Integer, List<SkillLearnFact>> _classSkillLearns;
	private final Map<SkillRef, List<Integer>> _classesBySkill;
	private final Map<SkillRef, SkillFact> _skillsByIdentity;
	private final Map<Integer, EquipmentFact> _equipmentByItemId;
	private final Map<String, List<EquipmentFact>> _equipmentByBodyPart;
	private final Map<String, List<EquipmentFact>> _equipmentByFamily;
	private final Map<Integer, List<CapabilityRule>> _capabilitiesByClassId;
	private final Map<String, List<CapabilityRule>> _capabilitiesByKey;
	private final Map<Integer, List<SummonActorFact>> _summonsByClassId;
	private final Map<SkillRef, List<SummonActorFact>> _summonsBySkill;
	private final Map<Integer, List<SummonActorFact>> _summonsByNpc;
	private final Map<Integer, PetFact> _petsByNpc;
	private final Set<Integer> _referencedResourceItemIds;
	private final Set<Integer> _certificationSkillIds;
	private final Hashes _hashes;
	private final Counts _counts;

	PhantomProgressionCatalog(List<ClassFact> classes, List<SkillLearnFact> skillLearns, List<SkillFact> skills, List<EquipmentFact> equipment, List<SummonActorFact> summons, List<PetFact> pets, List<CapabilityRule> capabilityRules, Hashes hashes)
	{
		_classes = List.copyOf(classes);
		_skillLearns = List.copyOf(skillLearns);
		_skills = List.copyOf(skills);
		_equipment = List.copyOf(equipment);
		_summons = List.copyOf(summons);
		_pets = List.copyOf(pets);
		_capabilityRules = List.copyOf(capabilityRules);
		_hashes = Objects.requireNonNull(hashes);
		_classesById = uniqueIndex(_classes, ClassFact::classId, "class");
		_childrenByClass = groupChildren(_classes);
		_terminalClasses = _classes.stream().filter(ClassFact::terminal).toList();
		_skillsByIdentity = uniqueIndex(_skills, SkillFact::skill, "skill");
		_equipmentByItemId = uniqueIndex(_equipment, EquipmentFact::itemId, "equipment");
		_equipmentByBodyPart = group(_equipment, EquipmentFact::bodyPart);
		_equipmentByFamily = group(_equipment, EquipmentFact::family);
		_classSkillLearns = groupByClass(_skillLearns);
		_classesBySkill = groupClassesBySkill(_skillLearns);
		_capabilitiesByClassId = groupCapabilities(_capabilityRules);
		_capabilitiesByKey = group(_capabilityRules, CapabilityRule::capabilityKey);
		_summonsByClassId = groupSummons(_summons);
		_summonsBySkill = group(_summons, fact -> new SkillRef(fact.skillId(), fact.skillLevel()));
		_summonsByNpc = group(_summons, SummonActorFact::actorIdentity);
		_petsByNpc = uniqueIndex(_pets, PetFact::npcId, "pet");
		_referencedResourceItemIds = collectResourceItems(_skillLearns, _capabilityRules, _summons, _pets);
		_certificationSkillIds = collectCertificationSkills(_skillLearns);
		_counts = new Counts(_classes.size(), _skillLearns.size(), _skills.size(), _equipment.size(), _summons.size(), _pets.size(), _capabilityRules.size());
	}

	public ClassFact classFact(int classId)
	{
		return _classesById.get(classId);
	}

	public SkillFact skill(SkillRef skill)
	{
		return _skillsByIdentity.get(skill);
	}

	public EquipmentFact equipment(int itemId)
	{
		return _equipmentByItemId.get(itemId);
	}

	public List<SkillLearnFact> classSkillLearns(int classId)
	{
		return _classSkillLearns.getOrDefault(classId, List.of());
	}

	public List<ClassFact> children(int classId)
	{
		return _childrenByClass.getOrDefault(classId, List.of());
	}

	public List<ClassFact> terminalClasses()
	{
		return _terminalClasses;
	}

	public List<Integer> classesForSkill(SkillRef skill)
	{
		return _classesBySkill.getOrDefault(skill, List.of());
	}

	public List<EquipmentFact> equipmentByBodyPart(String bodyPart)
	{
		return _equipmentByBodyPart.getOrDefault(bodyPart, List.of());
	}

	public List<EquipmentFact> equipmentByFamily(String family)
	{
		return _equipmentByFamily.getOrDefault(family, List.of());
	}

	public List<CapabilityRule> capabilities(int classId)
	{
		return _capabilitiesByClassId.getOrDefault(classId, List.of());
	}

	public List<CapabilityRule> capabilityRules(String capabilityKey)
	{
		return _capabilitiesByKey.getOrDefault(capabilityKey, List.of());
	}

	public List<SummonActorFact> summons(int classId)
	{
		return _summonsByClassId.getOrDefault(classId, List.of());
	}

	public List<SummonActorFact> summons(SkillRef skill)
	{
		return _summonsBySkill.getOrDefault(skill, List.of());
	}

	public List<SummonActorFact> summonsByNpc(int npcId)
	{
		return _summonsByNpc.getOrDefault(npcId, List.of());
	}

	public PetFact pet(int npcId)
	{
		return _petsByNpc.get(npcId);
	}

	public Page<ClassFact> classes(PageRequest request)
	{
		return page(_classes, request, ClassFact::stableKey);
	}

	public Page<SkillLearnFact> skillLearns(PageRequest request)
	{
		return page(_skillLearns, request, SkillLearnFact::stableKey);
	}

	public Page<SkillFact> skills(PageRequest request)
	{
		return page(_skills, request, SkillFact::stableKey);
	}

	public Page<EquipmentFact> equipment(PageRequest request)
	{
		return page(_equipment, request, EquipmentFact::stableKey);
	}

	public Page<SummonActorFact> summons(PageRequest request)
	{
		return page(_summons, request, SummonActorFact::stableKey);
	}

	public Page<PetFact> pets(PageRequest request)
	{
		return page(_pets, request, PetFact::stableKey);
	}

	public Page<CapabilityRule> capabilityRules(PageRequest request)
	{
		return page(_capabilityRules, request, CapabilityRule::stableKey);
	}

	public Set<Integer> referencedResourceItemIds()
	{
		return _referencedResourceItemIds;
	}

	public Set<Integer> certificationSkillIds()
	{
		return _certificationSkillIds;
	}

	public Hashes hashes()
	{
		return _hashes;
	}

	public String combinedHash()
	{
		return _hashes.combinedHash();
	}

	public Counts counts()
	{
		return _counts;
	}

	private static <K, V> Map<K, V> uniqueIndex(List<V> values, java.util.function.Function<V, K> keyFunction, String label)
	{
		final Map<K, V> result = new HashMap<>();
		for (V value : values)
		{
			if (result.put(keyFunction.apply(value), value) != null)
			{
				throw new IllegalStateException("Duplicate " + label + " identity.");
			}
		}
		return Map.copyOf(result);
	}

	private static Map<Integer, List<SkillLearnFact>> groupByClass(List<SkillLearnFact> values)
	{
		final Map<Integer, List<SkillLearnFact>> result = new HashMap<>();
		for (SkillLearnFact value : values)
		{
			if ((value.classId() >= 0) && (value.acquireKind() == AcquireKind.CLASS))
			{
				result.computeIfAbsent(value.classId(), unused -> new ArrayList<>()).add(value);
			}
		}
		return immutableGroups(result);
	}

	private static Map<Integer, List<ClassFact>> groupChildren(List<ClassFact> values)
	{
		final Map<Integer, List<ClassFact>> result = new HashMap<>();
		for (ClassFact value : values)
		{
			if (value.enumParentClassId() != null)
			{
				result.computeIfAbsent(value.enumParentClassId(), unused -> new ArrayList<>()).add(value);
			}
		}
		return immutableGroups(result);
	}

	private static Map<SkillRef, List<Integer>> groupClassesBySkill(List<SkillLearnFact> values)
	{
		final Map<SkillRef, Set<Integer>> grouped = new HashMap<>();
		for (SkillLearnFact value : values)
		{
			if ((value.classId() >= 0) && (value.acquireKind() == AcquireKind.CLASS))
			{
				grouped.computeIfAbsent(value.skill(), unused -> new HashSet<>()).add(value.classId());
			}
		}
		final Map<SkillRef, List<Integer>> result = new HashMap<>();
		grouped.forEach((key, classIds) -> result.put(key, classIds.stream().sorted().toList()));
		return Map.copyOf(result);
	}

	private static Map<Integer, List<CapabilityRule>> groupCapabilities(List<CapabilityRule> values)
	{
		final Map<Integer, List<CapabilityRule>> result = new HashMap<>();
		for (CapabilityRule value : values)
		{
			for (int classId : value.classIds())
			{
				result.computeIfAbsent(classId, unused -> new ArrayList<>()).add(value);
			}
		}
		return immutableGroups(result);
	}

	private static Map<Integer, List<SummonActorFact>> groupSummons(List<SummonActorFact> values)
	{
		final Map<Integer, List<SummonActorFact>> result = new HashMap<>();
		for (SummonActorFact value : values)
		{
			for (int classId : value.ownerClassIds())
			{
				result.computeIfAbsent(classId, unused -> new ArrayList<>()).add(value);
			}
		}
		return immutableGroups(result);
	}

	private static <T> Map<Integer, List<T>> immutableGroups(Map<Integer, List<T>> values)
	{
		final Map<Integer, List<T>> result = new HashMap<>();
		values.forEach((key, value) -> result.put(key, List.copyOf(value)));
		return Map.copyOf(result);
	}

	private static <K, T> Map<K, List<T>> group(List<T> values, java.util.function.Function<T, K> keyFunction)
	{
		final Map<K, List<T>> result = new HashMap<>();
		for (T value : values)
		{
			result.computeIfAbsent(keyFunction.apply(value), unused -> new ArrayList<>()).add(value);
		}
		final Map<K, List<T>> immutable = new HashMap<>();
		result.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
		return Map.copyOf(immutable);
	}

	private static Set<Integer> collectResourceItems(List<SkillLearnFact> skillLearns, List<CapabilityRule> capabilityRules, List<SummonActorFact> summons, List<PetFact> pets)
	{
		final Set<Integer> result = new HashSet<>();
		skillLearns.forEach(fact -> fact.requiredItems().forEach(item -> result.add(item.itemId())));
		capabilityRules.forEach(fact -> fact.requiredItems().forEach(item -> result.add(item.itemId())));
		for (SummonActorFact fact : summons)
		{
			addPositive(result, fact.summonItemId());
			addPositive(result, fact.upkeepItemId());
			addPositive(result, fact.controlItemId());
			result.addAll(fact.foodItemIds());
		}
		pets.forEach(fact ->
		{
			addPositive(result, fact.controlItemId());
			result.addAll(fact.foodItemIds());
		});
		return Set.copyOf(result);
	}

	private static Set<Integer> collectCertificationSkills(List<SkillLearnFact> skillLearns)
	{
		final Set<Integer> result = new HashSet<>();
		skillLearns.stream().filter(fact -> fact.acquireKind() == AcquireKind.SUBCLASS).forEach(fact -> result.add(fact.skillId()));
		return Set.copyOf(result);
	}

	private static void addPositive(Set<Integer> values, int value)
	{
		if (value > 0)
		{
			values.add(value);
		}
	}

	private static <T> Page<T> page(List<T> values, PageRequest request, java.util.function.Function<T, String> keyFunction)
	{
		Objects.requireNonNull(request);
		int first = 0;
		if (request.afterKey() != null)
		{
			int low = 0;
			int high = values.size();
			while (low < high)
			{
				final int middle = (low + high) >>> 1;
				if (keyFunction.apply(values.get(middle)).compareTo(request.afterKey()) <= 0)
				{
					low = middle + 1;
				}
				else
				{
					high = middle;
				}
			}
			first = low;
		}
		final int last = Math.min(values.size(), first + request.limit());
		final boolean hasMore = last < values.size();
		final List<T> pageValues = values.subList(first, last);
		return new Page<>(pageValues, hasMore && !pageValues.isEmpty() ? keyFunction.apply(pageValues.getLast()) : null, hasMore);
	}

	public record Hashes(String classGraphHash, String skillLearningHash, String skillMechanicsHash, String equipmentHash, String summonPetHash, String capabilityRulesHash, String combinedHash)
	{
		public Hashes
		{
			Objects.requireNonNull(classGraphHash);
			Objects.requireNonNull(skillLearningHash);
			Objects.requireNonNull(skillMechanicsHash);
			Objects.requireNonNull(equipmentHash);
			Objects.requireNonNull(summonPetHash);
			Objects.requireNonNull(capabilityRulesHash);
			Objects.requireNonNull(combinedHash);
		}
	}

	public record Counts(int classes, int skillLearns, int skills, int equipment, int summons, int pets, int capabilityRules)
	{
	}
}
