/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.PetDataTable;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;

public final class PhantomProgressionParitySuite implements PhantomTestSuite
{
	private final PhantomProgressionLoaderFixture _fixture = new PhantomProgressionLoaderFixture();
	private PhantomProgressionCatalog _catalog;

	@Override
	public String id()
	{
		return "progression-parity";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_catalog = _fixture.start(context);
		context.record("progressionParity.cases", 39);
		context.record("progressionParity.hash", _catalog.combinedHash());
		context.record("progressionParity.classGraphHash", _catalog.hashes().classGraphHash());
		context.record("progressionParity.skillLearningHash", _catalog.hashes().skillLearningHash());
		context.record("progressionParity.skillMechanicsHash", _catalog.hashes().skillMechanicsHash());
		context.record("progressionParity.equipmentHash", _catalog.hashes().equipmentHash());
		context.record("progressionParity.summonPetHash", _catalog.hashes().summonPetHash());
		context.record("progressionParity.capabilityRulesHash", _catalog.hashes().capabilityRulesHash());
		context.record("progressionParity.classes", _catalog.counts().classes());
		context.record("progressionParity.terminalClasses", _catalog.terminalClasses().size());
		context.record("progressionParity.skillLearns", _catalog.counts().skillLearns());
		context.record("progressionParity.skills", _catalog.counts().skills());
		context.record("progressionParity.equipment", _catalog.counts().equipment());
		context.record("progressionParity.summons", _catalog.counts().summons());
		context.record("progressionParity.pets", _catalog.counts().pets());
		context.record("progressionParity.capabilityRules", _catalog.counts().capabilityRules());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_fixture.close();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-all-player-classes", _ -> PhantomAssertions.assertEquals(PlayerClass.values().length, _catalog.counts().classes(), "PlayerClass catalog is incomplete."));
		registry.add("02-class-identities", _ -> assertClassIdentities());
		registry.add("03-enum-parents", _ -> assertEnumParents());
		registry.add("04-roots", _ -> assertRoots());
		registry.add("05-terminal-children", _ -> assertChildren());
		registry.add("06-male-soulhound", _ -> PhantomAssertions.assertTrue(_catalog.classFact(132) != null, "Male Soul Hound is absent."));
		registry.add("07-female-soulhound", _ -> PhantomAssertions.assertTrue(_catalog.classFact(133) != null, "Female Soul Hound is absent."));
		registry.add("08-inspector-parent", _ -> PhantomAssertions.assertEquals(Integer.valueOf(126), _catalog.classFact(135).enumParentClassId(), "Inspector enum parent changed."));
		registry.add("09-judicator-parent", _ -> PhantomAssertions.assertEquals(Integer.valueOf(135), _catalog.classFact(136).enumParentClassId(), "Judicator enum parent changed."));
		registry.add("10-class-learning-complete", _ -> assertClassLearning());
		registry.add("11-class-learning-loader-count", _ -> assertClassLearningCounts());
		registry.add("12-transfer-queryable", _ -> assertKind(AcquireKind.TRANSFER));
		registry.add("13-subclass-queryable", _ -> assertKind(AcquireKind.SUBCLASS));
		registry.add("14-noble-queryable", _ -> assertKind(AcquireKind.NOBLE));
		registry.add("15-common-loader-parity", _ -> assertCommonParity());
		registry.add("16-transform-queryable", _ -> assertKind(AcquireKind.TRANSFORM));
		registry.add("17-class-only-executable", _ -> Arrays.stream(AcquireKind.values()).forEach(kind -> PhantomAssertions.assertEquals(kind == AcquireKind.CLASS, kind.executable(), "Acquire execution boundary changed.")));
		registry.add("18-skill-mechanics-resolved", _ -> PhantomAssertions.assertTrue(_catalog.counts().skills() > 1000, "Referenced skill mechanics corpus is unexpectedly small."));
		registry.add("19-equipment-loader-count", _ -> assertEquipmentCount());
		registry.add("20-pet-loader-membership", _ -> assertPetMembership());
		registry.add("21-summon-effects-present", _ -> PhantomAssertions.assertTrue(_catalog.counts().summons() > 0, "Summon effect corpus is empty."));
		registry.add("22-capability-rules-present", _ -> assertCapabilityRuleCount());
		registry.add("23-page-bound", _ -> PhantomAssertions.assertTrue(_catalog.classes(PageRequest.first(256)).values().size() <= 256, "Catalog page exceeded 256."));
		registry.add("24-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.combinedHash().length(), "Combined hash is not SHA-256."));
		registry.add("25-class-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().classGraphHash().length(), "Class hash is not SHA-256."));
		registry.add("26-learn-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().skillLearningHash().length(), "Skill-learning hash is not SHA-256."));
		registry.add("27-mechanics-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().skillMechanicsHash().length(), "Mechanics hash is not SHA-256."));
		registry.add("28-equipment-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().equipmentHash().length(), "Equipment hash is not SHA-256."));
		registry.add("29-summon-pet-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().summonPetHash().length(), "Summon/pet hash is not SHA-256."));
		registry.add("30-capability-hash-width", _ -> PhantomAssertions.assertEquals(64, _catalog.hashes().capabilityRulesHash().length(), "Capability hash is not SHA-256."));
		registry.add("31-service-loader-build", _ -> PhantomAssertions.assertEquals(1L, _fixture.service().snapshot().metrics().catalogBuilds(), "Service did not publish one loader snapshot."));
		registry.add("32-no-loader-workers", _ -> PhantomAssertions.assertEquals(0, _fixture.service().snapshot().currentOperations(), "Loader build created an operation."));
		registry.add("33-learned-magic-defense-support", _ -> assertMagicDefenseSupport());
		registry.add("34-all-native-class-learning-records", this::assertLearningRecords);
		registry.add("35-all-native-career-race-sex-index-contracts", this::assertCareerEdges);
		registry.add("36-npc-learnable-class-trainer-templates", this::assertTrainerTemplates);
		registry.add("37-native-raid-self-defense-rejects-offensive-self-root", _ ->
		{
			final var skills = org.l2jmobius.gameserver.data.xml.SkillData.getInstance();
			final var defense = skills.getSkill(110, 2);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.supportsRaidSelfDefense(defense, false), "Native SELF Ultimate Defense is not admitted.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.supportsRaidSelfDefense(defense, true), "Transformed actor admitted native SELF defense.");
			for (int id : new int[] {313, 368, 18})
			{
				final var skill = skills.getSkill(id, 1);
				PhantomAssertions.assertTrue(skill != null, "Native safety negative skill is absent: " + id);
				PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.supportsRaidSelfDefense(skill, false), "Offensive self-root/area action was admitted as SELF defense: " + id);
			}
		});
		registry.add("38-learned-physical-critical-support", _ ->
		{
			for (int skillId : Set.of(1077, 1242, 1085))
			{
				final var rules = _catalog.capabilities(98).stream().filter(rule -> rule.capabilityKey().equals("combat.buff") && rule.actionSkill().skillId() == skillId).toList();
				PhantomAssertions.assertEquals(1, rules.size(), "Learned Hierophant raid support is unavailable to the existing support owner: " + skillId);
				PhantomAssertions.assertTrue(rules.getFirst().actionSkill().skillLevel() == 3 && rules.getFirst().targetScope() == org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope.PARTY
					&& _catalog.classSkillLearns(98).stream().anyMatch(learn -> learn.skillId() == skillId && learn.skillLevel() == 3 && learn.minimumCharacterLevel() <= 79), "Physical support lacks its inherited native level79 class-tree fact.");
			}
		});
		registry.add("39-learned-eva-saint-major-heal", _ ->
		{
			final var rules = _catalog.capabilities(105).stream().filter(rule -> rule.capabilityKey().equals("combat.heal") && rule.actionSkill().skillId() == 1401).toList();
			PhantomAssertions.assertEquals(1, rules.size(), "Learned Eva Saint Major Heal is unavailable to the existing support owner.");
			PhantomAssertions.assertTrue(rules.getFirst().actionSkill().skillLevel() == 1 && rules.getFirst().rank() == 1000
				&& _catalog.classSkillLearns(105).stream().anyMatch(learn -> learn.skillId() == 1401 && learn.skillLevel() == 11 && learn.minimumCharacterLevel() == 74), "Eva Saint Major Heal capability differs from its native inherited learning contract.");
		});
	}

	private void assertLearningRecords(PhantomTestContext context)
	{
		long checked = 0;
		for (PlayerClass playerClass : PlayerClass.values())
		{
			final var facts = _catalog.classSkillLearns(playerClass.getId()).stream().collect(java.util.stream.Collectors.toMap(fact -> fact.skillId() + ":" + fact.skillLevel(), java.util.function.Function.identity()));
			for (var learn : SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values())
			{
				final String identity = playerClass.getId() + "/" + learn.getSkillId() + ':' + learn.getSkillLevel();
				final var fact = facts.get(learn.getSkillId() + ":" + learn.getSkillLevel());
				PhantomAssertions.assertTrue(fact != null && fact.minimumCharacterLevel() == learn.getGetLevel() && fact.baseSpCost() == learn.getLevelUpSp()
					&& fact.learnedByNpc() == learn.isLearnedByNpc() && fact.learnedByForgottenScroll() == learn.isLearnedByFS(), "Native CLASS learning identity/cost/conditions differ: " + identity);
				final var items = learn.getRequiredItems().stream().map(item -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem(item.getId(), item.getCount())).collect(java.util.stream.Collectors.toSet());
				final var prerequisites = learn.getPreReqSkills().stream().map(skill -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(skill.getSkillId(), skill.getSkillLevel())).collect(java.util.stream.Collectors.toSet());
				PhantomAssertions.assertEquals(items, Set.copyOf(fact.requiredItems()), "Native required items differ: " + identity);
				PhantomAssertions.assertEquals(prerequisites, Set.copyOf(fact.prerequisiteSkills()), "Native prerequisite skills differ: " + identity);
				checked++;
			}
		}
		context.record("progressionParity.exhaustiveLearningRecords", checked);
	}

	private void assertCareerEdges(PhantomTestContext context)
	{
		long checked = 0;
		for (PlayerClass from : PlayerClass.values())
		{
			PhantomAssertions.assertEquals(from.getRace().name(), _catalog.classFact(from.getId()).race(), "Native class race differs: " + from);
			for (PlayerClass target : PlayerClass.values())
			{
				for (boolean female : new boolean[] {false, true})
				{
					final int root = female ? PlayerClass.FEMALE_SOLDIER.getId() : PlayerClass.MALE_SOLDIER.getId();
					final boolean nativeEdge = from.getNextClasses().contains(target) && target.getParent() == from && from.getRace() == target.getRace();
					final boolean sexCompatible = from.getRace() != org.l2jmobius.gameserver.model.actor.enums.creature.Race.KAMAEL || from.getRootClass().getId() == root && target.getRootClass().getId() == root;
					final boolean expected = nativeEdge && sexCompatible && target != PlayerClass.INSPECTOR;
					PhantomAssertions.assertEquals(expected, org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.legalEdge(from.getId(), target.getId(), female, 0), "Native career branch/race/sex differs: " + from + '/' + target + "/female=" + female);
					PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.legalEdge(from.getId(), target.getId(), female, 1), "Main-career transfer accepted a subclass index.");
					checked++;
				}
			}
		}
		context.record("progressionParity.exhaustiveCareerPairs", checked);
	}

	private void assertTrainerTemplates(PhantomTestContext context)
	{
		final var trainers = org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplates(template -> "Trainer".equals(template.getType()));
		final var missing = new java.util.ArrayList<Integer>();
		int classes = 0;
		for (PlayerClass playerClass : PlayerClass.values())
		{
			if (_catalog.classSkillLearns(playerClass.getId()).stream().noneMatch(fact -> fact.learnedByNpc())) { continue; }
			classes++;
			final var compatible = trainers.stream().filter(template -> template.canTeach(playerClass)).map(template -> template.getId()).sorted().toList();
			context.record("progressionParity.trainerTemplates." + playerClass.getId(), compatible);
			if (compatible.isEmpty()) { missing.add(playerClass.getId()); }
		}
		context.record("progressionParity.npcLearnableClasses", classes);
		context.record("progressionParity.trainerBoundary", "Native templates/canTeach only; runtime spawn, interaction and route require separate bounded native evidence.");
		PhantomAssertions.assertEquals(java.util.List.of(), missing, "NPC-learnable classes lack a compatible native Trainer template.");
	}

	private void assertMagicDefenseSupport()
	{
		for (int skillId : Set.of(1036, 1189))
		{
			final int level = skillId == 1036 ? 2 : 3;
			final var rules = _catalog.capabilities(98).stream().filter(rule -> rule.capabilityKey().equals("combat.buff") && rule.actionSkill().skillId() == skillId).toList();
			PhantomAssertions.assertEquals(1, rules.size(), "Learned Hierophant magic defense is unavailable to the existing support owner: " + skillId);
			final var rule = rules.getFirst();
			PhantomAssertions.assertTrue(rule.actionSkill().skillLevel() == level && rule.targetScope() == org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope.PARTY
				&& _catalog.classSkillLearns(98).stream().anyMatch(learn -> learn.skillId() == skillId && learn.skillLevel() == level && learn.minimumCharacterLevel() <= 79), "Magic-defense rule lacks the native inherited level79 class-tree fact.");
		}
	}

	private void assertClassIdentities()
	{
		for (PlayerClass value : PlayerClass.values())
		{
			PhantomAssertions.assertEquals(value.name(), _catalog.classFact(value.getId()).enumKey(), "PlayerClass identity changed.");
		}
	}

	private void assertEnumParents()
	{
		for (PlayerClass value : PlayerClass.values())
		{
			PhantomAssertions.assertEquals(value.getParent() == null ? null : Integer.valueOf(value.getParent().getId()), _catalog.classFact(value.getId()).enumParentClassId(), "Enum parent mismatch.");
		}
	}

	private void assertRoots()
	{
		for (PlayerClass value : PlayerClass.values())
		{
			PhantomAssertions.assertEquals(value.getRootClass().getId(), _catalog.classFact(value.getId()).rootClassId(), "Root class mismatch.");
		}
	}

	private void assertChildren()
	{
		int terminalCount = 0;
		for (PlayerClass value : PlayerClass.values())
		{
			final boolean hasChild = Arrays.stream(PlayerClass.values()).anyMatch(candidate -> candidate.getParent() == value);
			PhantomAssertions.assertEquals(!hasChild, _catalog.classFact(value.getId()).terminal(), "Terminal class mismatch.");
			PhantomAssertions.assertEquals((int) Arrays.stream(PlayerClass.values()).filter(candidate -> candidate.getParent() == value).count(), _catalog.children(value.getId()).size(), "Children reverse index mismatch.");
			if (!hasChild)
			{
				terminalCount++;
			}
		}
		PhantomAssertions.assertEquals(terminalCount, _catalog.terminalClasses().size(), "Terminal class reverse index mismatch.");
	}

	private void assertClassLearning()
	{
		for (PlayerClass value : PlayerClass.values())
		{
			final int expected = SkillTreeData.getInstance().getCompleteClassSkillTree(value).size();
			PhantomAssertions.assertEquals(expected, _catalog.classSkillLearns(value.getId()).size(), "Complete class learning tree mismatch.");
		}
	}

	private void assertClassLearningCounts()
	{
		final long expected = Arrays.stream(PlayerClass.values()).mapToLong(value -> SkillTreeData.getInstance().getCompleteClassSkillTree(value).size()).sum();
		final long actual = _catalog.skillLearns(PageRequest.first(256)).values().stream().filter(fact -> fact.acquireKind() == AcquireKind.CLASS).count();
		PhantomAssertions.assertTrue(expected > actual, "Class skill-learning corpus did not require paging.");
	}

	private void assertKind(AcquireKind kind)
	{
		String cursor = null;
		boolean found = false;
		do
		{
			final var page = _catalog.skillLearns(new PageRequest(cursor, 256));
			found |= page.values().stream().anyMatch(fact -> fact.acquireKind() == kind);
			cursor = page.nextCursor();
		}
		while (!found && (cursor != null));
		PhantomAssertions.assertTrue(found, "Acquire kind " + kind + " is absent.");
	}

	private void assertCommonParity()
	{
		final int expected = SkillTreeData.getInstance().getCommonSkillTree().size();
		int actual = 0;
		String cursor = null;
		do
		{
			final var page = _catalog.skillLearns(new PageRequest(cursor, 256));
			actual += (int) page.values().stream().filter(fact -> fact.acquireKind() == AcquireKind.COMMON).count();
			cursor = page.nextCursor();
		}
		while (cursor != null);
		PhantomAssertions.assertEquals(expected, actual, "Common skill-tree loader parity changed.");
	}

	private void assertEquipmentCount()
	{
		final Set<Integer> expected = new HashSet<>();
		for (var item : ItemData.getInstance().getAllItems())
		{
			if ((item != null) && item.isEquipable())
			{
				expected.add(item.getId());
			}
		}
		PhantomAssertions.assertEquals(expected.size(), _catalog.counts().equipment(), "Equippable item loader parity changed.");
	}

	private void assertCapabilityRuleCount() throws Exception
	{
		final Path source = Path.of(System.getProperty("phantom.module.root", "."), "dist", "game", "data", "phantoms", "progression", "high-five-capabilities-v1.xml").toAbsolutePath().normalize();
		try (var lines = Files.lines(source, StandardCharsets.UTF_8))
		{
			final long expected = lines.filter(line -> line.contains("<capabilityRule ")).count();
			PhantomAssertions.assertTrue(expected > 0, "Curated capability rule corpus is empty.");
			PhantomAssertions.assertEquals(expected, (long) _catalog.counts().capabilityRules(), "Curated capability parser parity changed.");
		}
	}

	private void assertPetMembership()
	{
		String cursor = null;
		int count = 0;
		do
		{
			final var page = _catalog.pets(new PageRequest(cursor, 256));
			for (var pet : page.values())
			{
				PhantomAssertions.assertTrue(PetDataTable.getInstance().getPetData(pet.npcId()) != null, "Catalog pet is absent from current loader.");
				count++;
			}
			cursor = page.nextCursor();
		}
		while (cursor != null);
		PhantomAssertions.assertEquals(_catalog.counts().pets(), count, "Pet catalog paging lost facts.");
	}
}

final class PhantomProgressionLoaderFixture implements AutoCloseable
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService _knowledge;
	private org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService _progression;
	private org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend _backend;

	org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog start(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_knowledge = org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService.inertForTesting("0".repeat(64));
		PhantomAssertions.assertTrue(_knowledge.start(), "Inert Game Knowledge fixture did not start.");
		_backend = new org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend(null, java.nio.file.Path.of("."), () -> _knowledge.query());
		_progression = new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService(_backend, org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy.productionDefaults());
		_progression.start();
		return _progression.catalog();
	}

	org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService service()
	{
		return _progression;
	}

	org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend backend()
	{
		return _backend;
	}

	@Override
	public void close() throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_progression != null)
			{
				_progression.beginStop();
				PhantomAssertions.assertTrue(_progression.finishStop(), "Progression loader fixture did not stop.");
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Knowledge loader fixture did not stop.");
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			_environment.shutdown();
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}
}
