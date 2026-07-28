/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog.Hashes;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ClassFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearnFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;

/**
 * Validates and canonicalizes loader copies before publication.
 */
public final class PhantomProgressionCatalogBuilder
{
	public PhantomProgressionCatalog build(BackendData data, PhantomProgressionPolicy policy)
	{
		Objects.requireNonNull(data);
		Objects.requireNonNull(policy);
		requireBound("classes", data.classes().size(), policy.maximumClasses());
		requireBound("skill learns", data.skillLearns().size(), policy.maximumSkillLearns());
		requireBound("skills", data.skills().size(), policy.maximumSkillFacts());
		requireBound("equipment", data.equipment().size(), policy.maximumEquipmentFacts());
		requireBound("summons", data.summons().size(), policy.maximumSummonFacts());
		requireBound("pets", data.pets().size(), policy.maximumPetFacts());
		requireBound("capabilities", data.capabilityRules().size(), policy.maximumCapabilityRules());

		final List<ClassFact> classes = sorted(data.classes(), ClassFact::stableKey);
		final List<SkillLearnFact> learns = sorted(data.skillLearns(), SkillLearnFact::stableKey);
		final List<SkillFact> skills = sorted(data.skills(), SkillFact::stableKey);
		final List<EquipmentFact> equipment = sorted(data.equipment(), EquipmentFact::stableKey);
		final List<SummonActorFact> summons = sorted(data.summons(), SummonActorFact::stableKey);
		final List<PetFact> pets = sorted(data.pets(), PetFact::stableKey);
		final List<CapabilityRule> capabilities = sorted(data.capabilityRules(), CapabilityRule::stableKey);
		validateClassGraph(classes);
		validateReferences(classes, learns, skills, summons, pets, capabilities, data.knownItemIds());

		final String classHash = hash(classes);
		final String learnHash = hash(learns);
		final String skillHash = hash(skills);
		final String equipmentHash = hash(equipment);
		final String summonPetHash = hash(List.of(summons, pets));
		final String capabilityHash = hash(capabilities);
		final String combinedHash = hash(List.of(classHash, learnHash, skillHash, equipmentHash, summonPetHash, capabilityHash));
		return new PhantomProgressionCatalog(classes, learns, skills, equipment, summons, pets, capabilities, new Hashes(classHash, learnHash, skillHash, equipmentHash, summonPetHash, capabilityHash, combinedHash));
	}

	private static void validateClassGraph(List<ClassFact> classes)
	{
		final Map<Integer, ClassFact> byId = new HashMap<>();
		for (ClassFact fact : classes)
		{
			if (byId.put(fact.classId(), fact) != null)
			{
				throw new IllegalStateException("Duplicate PlayerClass identity " + fact.classId() + '.');
			}
		}
		for (ClassFact fact : classes)
		{
			requireClass(byId, fact.enumParentClassId(), "enum parent", fact.classId());
			requireClass(byId, fact.skillTreeParentClassId(), "skill-tree parent", fact.classId());
			requireClass(byId, fact.rootClassId(), "root", fact.classId());
			for (int child : fact.nextClassIds())
			{
				requireClass(byId, child, "child", fact.classId());
			}
			final Set<Integer> visited = new HashSet<>();
			ClassFact current = fact;
			while (current.enumParentClassId() != null)
			{
				if (!visited.add(current.classId()))
				{
					throw new IllegalStateException("PlayerClass parent cycle at " + fact.classId() + '.');
				}
				current = byId.get(current.enumParentClassId());
			}
		}
	}

	private static void validateReferences(List<ClassFact> classes, List<SkillLearnFact> learns, List<SkillFact> skills, List<SummonActorFact> summons, List<PetFact> pets, List<CapabilityRule> capabilities, Set<Integer> knownItemIds)
	{
		final Set<Integer> classIds = classes.stream().map(ClassFact::classId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final Set<String> skillIds = skills.stream().map(SkillFact::stableKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
		for (SkillLearnFact fact : learns)
		{
			if ((fact.classId() >= 0) && !classIds.contains(fact.classId()))
			{
				throw new IllegalStateException("Skill learn references unknown class " + fact.classId() + '.');
			}
			if (!skillIds.contains(fact.skill().stableKey()))
			{
				throw new IllegalStateException("Skill learn references unknown skill " + fact.skill().stableKey() + '.');
			}
			fact.requiredItems().forEach(item -> requireItem(knownItemIds, item.itemId(), "skill learn"));
		}
		for (SkillFact fact : skills)
		{
			requireItem(knownItemIds, fact.itemConsumeId(), "skill consumption");
		}
		for (SummonActorFact fact : summons)
		{
			if (!skillIds.contains(fact.skill().stableKey()))
			{
				throw new IllegalStateException("Summon references unknown skill " + fact.skill().stableKey() + '.');
			}
			if (!classIds.containsAll(fact.ownerClassIds()))
			{
				throw new IllegalStateException("Summon references unknown owner class.");
			}
			fact.actorSkills().forEach(skill ->
			{
				if (!skillIds.contains(skill.stableKey()))
				{
					throw new IllegalStateException("Controlled actor references unknown skill " + skill.stableKey() + '.');
				}
			});
			requireItem(knownItemIds, fact.summonItemId(), "summon consumption");
			requireItem(knownItemIds, fact.upkeepItemId(), "summon upkeep");
			requireItem(knownItemIds, fact.controlItemId(), "controlled actor control item");
			fact.foodItemIds().forEach(itemId -> requireItem(knownItemIds, itemId, "controlled actor food"));
		}
		for (PetFact fact : pets)
		{
			for (var skill : fact.skills())
			{
				if ((skill.skillLevel() > 0) && !skillIds.contains(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(skill.skillId(), skill.skillLevel()).stableKey()))
				{
					throw new IllegalStateException("Pet references unknown skill.");
				}
			}
			requireItem(knownItemIds, fact.controlItemId(), "pet control item");
			fact.foodItemIds().forEach(itemId -> requireItem(knownItemIds, itemId, "pet food"));
		}
		for (CapabilityRule fact : capabilities)
		{
			if (!classIds.containsAll(fact.classIds()))
			{
				throw new IllegalStateException("Capability references unknown class.");
			}
			for (var skill : fact.evidenceSkills())
			{
				if (!skillIds.contains(skill.stableKey()))
				{
					throw new IllegalStateException("Capability references unknown skill " + skill.stableKey() + '.');
				}
			}
			fact.requiredItems().forEach(item -> requireItem(knownItemIds, item.itemId(), "capability resource"));
		}
	}

	private static void requireItem(Set<Integer> knownItemIds, int itemId, String relation)
	{
		if ((itemId > 0) && !knownItemIds.contains(itemId))
		{
			throw new IllegalStateException("Unknown item " + itemId + " referenced by " + relation + '.');
		}
	}

	private static void requireClass(Map<Integer, ClassFact> classes, Integer referencedId, String relation, int owner)
	{
		if ((referencedId != null) && !classes.containsKey(referencedId))
		{
			throw new IllegalStateException("PlayerClass " + owner + " has unknown " + relation + ' ' + referencedId + '.');
		}
	}

	private static void requireBound(String label, int actual, int maximum)
	{
		if (actual > maximum)
		{
			throw new IllegalStateException(label + " exceed safety bound " + maximum + '.');
		}
	}

	private static <T> List<T> sorted(Collection<T> values, Function<T, String> key)
	{
		final List<T> result = new ArrayList<>(values);
		result.sort(Comparator.comparing(key));
		for (int i = 1; i < result.size(); i++)
		{
			if (key.apply(result.get(i - 1)).equals(key.apply(result.get(i))))
			{
				throw new IllegalStateException("Duplicate catalog stable key " + key.apply(result.get(i)) + '.');
			}
		}
		return List.copyOf(result);
	}

	private static String hash(Object value)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			addCanonical(digest, value);
			return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	private static void addCanonical(MessageDigest digest, Object value)
	{
		if (value == null)
		{
			add(digest, "N");
		}
		else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)
		{
			add(digest, value.getClass().getName() + ':' + value);
		}
		else if (value instanceof List<?> list)
		{
			add(digest, "L" + list.size());
			list.forEach(element -> addCanonical(digest, element));
		}
		else if (value instanceof Set<?> set)
		{
			final List<String> elements = set.stream().map(PhantomProgressionCatalogBuilder::canonicalText).sorted().toList();
			addCanonical(digest, elements);
		}
		else if (value instanceof Map<?, ?> map)
		{
			final List<String> elements = map.entrySet().stream().map(entry -> canonicalText(entry.getKey()) + '=' + canonicalText(entry.getValue())).sorted().toList();
			addCanonical(digest, elements);
		}
		else if (value.getClass().isRecord())
		{
			add(digest, "R" + value.getClass().getName());
			for (RecordComponent component : value.getClass().getRecordComponents())
			{
				add(digest, component.getName());
				try
				{
					addCanonical(digest, component.getAccessor().invoke(value));
				}
				catch (ReflectiveOperationException e)
				{
					throw new IllegalStateException("Cannot hash record component " + component.getName() + '.', e);
				}
			}
		}
		else
		{
			throw new IllegalStateException("Unsupported catalog hash value " + value.getClass().getName() + '.');
		}
	}

	private static String canonicalText(Object value)
	{
		final MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
		addCanonical(digest, value);
		return java.util.HexFormat.of().formatHex(digest.digest());
	}

	private static void add(MessageDigest digest, String value)
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte) (bytes.length >>> 24));
		digest.update((byte) (bytes.length >>> 16));
		digest.update((byte) (bytes.length >>> 8));
		digest.update((byte) bytes.length);
		digest.update(bytes);
	}
}
