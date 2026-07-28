/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Authority;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ClassFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ConditionPresence;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ControlledActorFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquippedItemFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearnFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassEligibility;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;

final class PhantomProgressionSyntheticBackend implements PhantomProgressionBackend
{
	private final BackendData _data = data();
	private ActorProgressionSnapshot _actor = actor(false, false, false, Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1), List.of(), Map.of(57, 5L));
	private SkillReadinessProbe _probe = new SkillReadinessProbe(true, true, true);
	private OperationResult _learnResult = OperationResult.rejected(OperationStatus.INVALID_REQUEST);
	private OperationResult _equipResult = OperationResult.rejected(OperationStatus.INVALID_REQUEST);
	private boolean _actorPresent = true;
	private int _learnCalls;
	private int _equipCalls;

	@Override
	public BackendData load(PhantomProgressionPolicy policy)
	{
		return _data;
	}

	@Override
	public Optional<ActorLease> tryAcquireActor(long profileId)
	{
		return _actorPresent && (profileId == 1) ? Optional.of(new Lease()) : Optional.empty();
	}

	void actor(ActorProgressionSnapshot actor)
	{
		_actor = actor;
	}

	void probe(SkillReadinessProbe probe)
	{
		_probe = probe;
	}

	void learnResult(OperationResult result)
	{
		_learnResult = result;
	}

	void equipResult(OperationResult result)
	{
		_equipResult = result;
	}

	void actorPresent(boolean present)
	{
		_actorPresent = present;
	}

	int learnCalls()
	{
		return _learnCalls;
	}

	int equipCalls()
	{
		return _equipCalls;
	}

	ActorLease lease()
	{
		return new Lease();
	}

	static BackendData data()
	{
		final List<ClassFact> classes = List.of(
			new ClassFact(0, "HUMAN_FIGHTER", "HUMAN", null, null, 0, 0, false, false, false, List.of(1), Authority.SERVER_LOADER_FACT, List.of("PlayerClass.java")),
			new ClassFact(1, "WARRIOR", "HUMAN", 0, 0, 0, 1, false, false, true, List.of(), Authority.SERVER_LOADER_FACT, List.of("PlayerClass.java", "classSkillTree.xml")));
		final List<SkillLearnFact> learns = List.of(
			new SkillLearnFact(0, AcquireKind.CLASS, 1, 1, 1, 0, List.of(), List.of(), true, false, true, false, Authority.SERVER_LOADER_FACT, "classSkillTree.xml"),
			new SkillLearnFact(0, AcquireKind.CLASS, 2, 1, 1, 10, List.of(), List.of(), true, false, true, false, Authority.SERVER_LOADER_FACT, "classSkillTree.xml"),
			new SkillLearnFact(0, AcquireKind.CLASS, 3, 1, 1, 20, List.of(), List.of(), true, false, true, false, Authority.SERVER_LOADER_FACT, "classSkillTree.xml"),
			new SkillLearnFact(0, AcquireKind.CLASS, 4, 1, 1, 30, List.of(), List.of(), true, false, true, false, Authority.SERVER_LOADER_FACT, "classSkillTree.xml"),
			new SkillLearnFact(0, AcquireKind.CLASS, 5, 1, 1, 40, List.of(new RequiredItem(57, 10)), List.of(), true, false, true, false, Authority.SERVER_LOADER_FACT, "classSkillTree.xml"),
			new SkillLearnFact(-1, AcquireKind.NOBLE, 6, 1, 76, 0, List.of(), List.of(), true, false, false, false, Authority.SERVER_LOADER_FACT, "nobleSkillTree.xml"));
		final List<SkillFact> skills = java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(id -> new SkillFact(id, 1, true, false, false, id == 1, id != 1, "ONE", id == 1, false, false, false, false, false, false, 0, 0, id, 0, 100, ConditionPresence.NONE, false, false, false, false, false, Authority.SERVER_LOADER_FACT, List.of("skills.xml"))).toList();
		final List<EquipmentFact> equipment = List.of(
			new EquipmentFact(100, "LR_HAND", "SWORD", "SWORD", "", "NONE", "EQUIP", false, 10, ConditionPresence.NONE, Authority.SERVER_LOADER_FACT, "items.xml"),
			new EquipmentFact(101, "LR_HAND", "BOW", "BOW", "", "NONE", "EQUIP", false, 20, ConditionPresence.NONE, Authority.SERVER_LOADER_FACT, "items.xml"));
		final List<SummonActorFact> summons = List.of(new SummonActorFact(List.of(0), 4, 1, 1000, ActorKind.SERVITOR, 60000, 0.1, 0, 57, 1, 60000, 0, Set.of(), 1, 1, false, false, false, false, false, false, true, true, true, true, Authority.STATIC_DATAPACK_FACT, List.of("summon.xml")));
		final List<PetFact> pets = List.of(new PetFact(1001, 200, Set.of(201), 1, 85, 1000, 50, false, false, true, true, List.of(), Authority.SERVER_LOADER_FACT, "pet.xml"));
		final List<CapabilityRule> capabilities = List.of(
			rule("melee_damage", 1, TargetScope.SELF, Set.of(), List.of(), false, false, 1),
			rule("ranged_damage", 2, TargetScope.SINGLE_TARGET, Set.of(), List.of(), true, false, 2),
			rule("bow_attack", 3, TargetScope.SINGLE_TARGET, Set.of("BOW"), List.of(), true, false, 3),
			rule("summon", 4, TargetScope.SERVITOR, Set.of(), List.of(), false, true, 4),
			rule("resource", 5, TargetScope.SELF, Set.of(), List.of(new RequiredItem(57, 10)), false, false, 5),
			rule("unlearned", 6, TargetScope.SELF, Set.of(), List.of(), false, false, 6));
		return new BackendData(classes, learns, skills, equipment, summons, pets, capabilities);
	}

	private static CapabilityRule rule(String key, int rank, TargetScope scope, Set<String> equipment, List<RequiredItem> items, boolean target, boolean servitor, int skill)
	{
		return new CapabilityRule(key, rank, List.of(0), List.of(new SkillRef(skill, 1)), scope, equipment, items, target, servitor, servitor, Authority.CURATED_CAPABILITY_RULE, List.of("capabilities.xml"));
	}

	static ActorProgressionSnapshot actor(boolean dead, boolean transformed, boolean mounted, Map<Integer, Integer> learned, List<ControlledActorFact> actors, Map<Integer, Long> resources)
	{
		return new ActorProgressionSnapshot(1, 2, 0, 0, 0, 0, 1, 0, 100, false, false, false, List.of(), learned, List.of(new EquippedItemFact(1000, 100, 7)), List.of(new OwnedEquipmentFact(1000, 100, "LR_HAND", "SWORD", "NONE", 0, true, true, List.of(), 100)), resources, actors, transformed, mounted, false, false, dead, false, 3, Set.of(), "A".repeat(64));
	}

	private final class Lease implements ActorLease
	{
		@Override
		public ActorProgressionSnapshot snapshot(String catalogHash, Set<Integer> referencedResourceItemIds, Set<Integer> certificationSkillIds, int maximumOwnedEquipmentCandidates)
		{
			final ActorProgressionSnapshot value = _actor;
			return new ActorProgressionSnapshot(value.profileId(), value.actorObjectId(), value.baseClassId(), value.activeClassId(), value.classIndex(), value.activeClassTier(), value.level(), value.exp(), value.sp(), value.noble(), value.hero(), value.subclassActive(), value.subclasses(), value.learnedSkills(), value.equippedItems(), value.ownedEquipment(), value.resourceItemCounts(), value.controlledActors(), value.transformed(), value.mounted(), value.inCombat(), value.casting(), value.dead(), value.subclassQuestSatisfied(), value.maximumSubclasses(), value.certificationSkillIds(), catalogHash);
		}

		@Override
		public SkillReadinessProbe canonicalSkillReadiness(SkillRef skill, Integer targetObjectId)
		{
			return _probe;
		}

		@Override
		public List<SubclassEligibility> subclassEligibility(List<ClassFact> classes)
		{
			return classes.stream().map(fact -> new SubclassEligibility(fact.classId(), true, true, true, false, true, true, false)).toList();
		}

		@Override
		public OperationResult learnClassSkill(LearnSkillRequest request, BooleanSupplier ownershipCurrent)
		{
			_learnCalls++;
			return ownershipCurrent.getAsBoolean() ? _learnResult : OperationResult.rejected(OperationStatus.CANCELLED);
		}

		@Override
		public OperationResult equipOwnedItem(EquipItemRequest request, BooleanSupplier ownershipCurrent)
		{
			_equipCalls++;
			return ownershipCurrent.getAsBoolean() ? _equipResult : OperationResult.rejected(OperationStatus.CANCELLED);
		}

		@Override
		public void close()
		{
		}
	}
}

public final class PhantomProgressionCatalogSuite implements PhantomTestSuite
{
	private static final int CASES = 60;
	private org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog _catalog;

	@Override
	public String id()
	{
		return "progression-catalog";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		_catalog = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder().build(PhantomProgressionSyntheticBackend.data(), PhantomProgressionPolicy.productionDefaults());
		context.record("progressionCatalog.cases", CASES);
		context.record("progressionCatalog.hash", _catalog.combinedHash());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		for (int i = 0; i < CASES; i++)
		{
			final int test = i;
			registry.add(String.format("%02d-catalog-contract", i + 1), _ -> assertCase(test));
		}
	}

	private void assertCase(int test)
	{
		switch (test % 20)
		{
			case 0 -> PhantomAssertions.assertEquals(2, _catalog.counts().classes(), "Synthetic class count changed.");
			case 1 -> PhantomAssertions.assertEquals(6, _catalog.counts().skillLearns(), "Synthetic learn count changed.");
			case 2 -> PhantomAssertions.assertEquals(6, _catalog.counts().skills(), "Synthetic skill count changed.");
			case 3 -> PhantomAssertions.assertEquals(2, _catalog.counts().equipment(), "Synthetic equipment count changed.");
			case 4 -> PhantomAssertions.assertEquals(1, _catalog.counts().summons(), "Synthetic summon count changed.");
			case 5 -> PhantomAssertions.assertEquals(1, _catalog.counts().pets(), "Synthetic pet count changed.");
			case 6 -> PhantomAssertions.assertEquals(6, _catalog.counts().capabilityRules(), "Synthetic capability count changed.");
			case 7 ->
			{
				PhantomAssertions.assertEquals("HUMAN_FIGHTER", _catalog.classFact(0).enumKey(), "Class index changed.");
				PhantomAssertions.assertEquals(1, _catalog.children(0).size(), "Children reverse index changed.");
				PhantomAssertions.assertEquals(1, _catalog.terminalClasses().size(), "Terminal class index changed.");
			}
			case 8 -> PhantomAssertions.assertEquals(Integer.valueOf(0), _catalog.classFact(1).enumParentClassId(), "Enum parent index changed.");
			case 9 ->
			{
				PhantomAssertions.assertEquals(5, _catalog.classSkillLearns(0).size(), "Complete class learn index changed.");
				PhantomAssertions.assertEquals(List.of(0), _catalog.classesForSkill(new SkillRef(1, 1)), "Classes-by-skill reverse index changed.");
			}
			case 10 ->
			{
				PhantomAssertions.assertEquals("SWORD", _catalog.equipment(100).family(), "Equipment index changed.");
				PhantomAssertions.assertEquals(2, _catalog.equipmentByBodyPart("LR_HAND").size(), "Equipment body-part index changed.");
				PhantomAssertions.assertEquals(1, _catalog.equipmentByFamily("BOW").size(), "Equipment family index changed.");
			}
			case 11 ->
			{
				PhantomAssertions.assertEquals(6, _catalog.capabilities(0).size(), "Capability class index changed.");
				PhantomAssertions.assertEquals(1, _catalog.capabilityRules("summon").size(), "Capability key index changed.");
			}
			case 12 ->
			{
				PhantomAssertions.assertEquals(1, _catalog.summons(0).size(), "Summon owner index changed.");
				PhantomAssertions.assertEquals(1, _catalog.summons(new SkillRef(4, 1)).size(), "Summon skill index changed.");
				PhantomAssertions.assertEquals(1, _catalog.summonsByNpc(1000).size(), "Summon NPC index changed.");
				PhantomAssertions.assertEquals(1001, _catalog.pet(1001).npcId(), "Pet NPC index changed.");
			}
			case 13 -> PhantomAssertions.assertTrue(_catalog.referencedResourceItemIds().contains(57), "Resource reverse set lost item 57.");
			case 14 -> PhantomAssertions.assertTrue(_catalog.certificationSkillIds().isEmpty(), "Synthetic certification set invented facts.");
			case 15 -> PhantomAssertions.assertEquals(1, _catalog.classes(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest.first(1)).values().size(), "Bounded class page changed.");
			case 16 -> PhantomAssertions.assertTrue(_catalog.skillLearns(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest.first(2)).hasMore(), "Skill-learn page cursor disappeared.");
			case 17 -> PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> _catalog.classes(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest.first(2)).values().clear(), "Catalog page remained mutable.");
			case 18 -> PhantomAssertions.assertEquals(64, _catalog.combinedHash().length(), "Combined hash width changed.");
			case 19 ->
			{
				final var rebuilt = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder().build(PhantomProgressionSyntheticBackend.data(), PhantomProgressionPolicy.productionDefaults());
				PhantomAssertions.assertEquals(_catalog.combinedHash(), rebuilt.combinedHash(), "Canonical build hash is nondeterministic.");
			}
		}
	}
}
