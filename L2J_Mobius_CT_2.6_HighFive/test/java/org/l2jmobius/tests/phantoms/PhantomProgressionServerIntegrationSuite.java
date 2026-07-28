/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.BabyPet;
import org.l2jmobius.gameserver.model.actor.instance.Cubic;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.instance.Trainer;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.AcquireSkillType;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFilter;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ProfessionStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SnapshotStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class PhantomProgressionServerIntegrationSuite implements PhantomTestSuite
{
	private static final int EQUIP_ITEM_ID = 6;
	private static final long WAIT_MILLIS = 5000;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<org.l2jmobius.gameserver.model.actor.Npc> _worldFixtures = new ArrayList<>();
	private PhantomProfileRepository _repository;
	private PhantomProfile _profile;
	private PhantomMaterializationService _materialization;
	private PhantomGameKnowledgeService _knowledge;
	private PhantomProgressionService _progression;
	private Player _player;
	private long _expBefore;
	private long _spBeforeReward;
	private int _levelBefore;
	private long _expAfter;
	private long _spAfterReward;
	private int _levelAfter;
	private ActorProgressionSnapshot _rewardObservation;
	private SkillLearn _learn;
	private int _learnSpCost;
	private long _learnSpBefore;
	private Map<Integer, Long> _learnItemsBefore;
	private OperationResult _learnResult;
	private OperationResult _idempotentLearnResult;
	private Item _equipment;
	private OperationResult _equipResult;
	private ActorProgressionSnapshot _finalObservation;
	private boolean _professionObserved;
	private final List<Item> _pagingEquipment = new ArrayList<>();
	private int _pagingEquipmentCount;
	private boolean _pagingAllReached;
	private boolean _pagingFilterExact;
	private boolean _pagingLowerGradeReached;
	private boolean _mainSubclassMainExact;
	private boolean _ordinarySkillsIsolated;
	private boolean _certificationSeparated;
	private boolean _servitorBodyExact;
	private boolean _babyPetBodyExact;
	private boolean _cubicBodyAbsent;
	private boolean _closed;

	@Override
	public String id()
	{
		return "progression-server-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		try
		{
			_knowledge = PhantomGameKnowledgeService.inertForTesting("0".repeat(64));
			PhantomAssertions.assertTrue(_knowledge.start(), "Inert knowledge service did not start.");
			_repository = PhantomProfileRepository.open();
			_profile = _repository.create(_environment.primary().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomAssertions.assertTrue(_materialization.start(), "Materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_profile.profileId()).status(), "Test actor did not materialize.");
			_player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(_player != null, "Materialized Player is absent.");
			relocatePlayer();
			_progression = new PhantomProgressionService(new L2jProgressionBackend(_materialization, Path.of("."), () -> _knowledge.query()), PhantomProgressionPolicy.productionDefaults());
			_progression.start();

			exerciseCanonicalReward();
			exerciseCanonicalLearning();
			exerciseCanonicalEquip();
			exerciseEquipmentPaging();
			exerciseControlledActors();
			exerciseSubclassSwitch();
			exerciseProfessionObservation();
			context.record("progressionIntegration.cases", 28);
			context.record("progressionIntegration.catalogHash", _progression.catalog().combinedHash());
			context.record("progressionIntegration.learnSkill", _learn.getSkillId() + ":" + _learn.getSkillLevel());
			context.record("progressionIntegration.rewardExp", _expAfter - _expBefore);
			context.record("progressionIntegration.rewardSp", _spAfterReward - _spBeforeReward);
		}
		catch (Throwable throwable)
		{
			cleanup();
			throw throwable;
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		cleanup();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-exact-materialized-player", _ -> PhantomAssertions.assertEquals(_player, World.getInstance().getPlayer(_player.getObjectId()), "Progression actor is not exact World Player."));
		registry.add("02-canonical-reward-exp-changed", _ -> PhantomAssertions.assertTrue(_expAfter > _expBefore, "Canonical monster reward did not change EXP."));
		registry.add("03-canonical-reward-sp-changed", _ -> PhantomAssertions.assertTrue(_spAfterReward > _spBeforeReward, "Canonical monster reward did not change SP."));
		registry.add("04-canonical-reward-level-changed", _ -> PhantomAssertions.assertTrue(_levelAfter > _levelBefore, "Canonical monster reward did not change level."));
		registry.add("05-observed-exp-exact", _ -> PhantomAssertions.assertEquals(_expAfter, _rewardObservation.exp(), "Progression observed different EXP."));
		registry.add("06-observed-sp-exact", _ -> PhantomAssertions.assertEquals(_spAfterReward, _rewardObservation.sp(), "Progression observed different SP."));
		registry.add("07-observed-level-exact", _ -> PhantomAssertions.assertEquals(_levelAfter, _rewardObservation.level(), "Progression observed different level."));
		registry.add("08-real-trainer-learning-success", _ -> PhantomAssertions.assertEquals(OperationStatus.SUCCESS, _learnResult.status(), "Real trainer CLASS learning failed."));
		registry.add("09-exact-sp-conservation", _ -> PhantomAssertions.assertEquals((long) _learnSpCost, _learnResult.spBefore() - _learnResult.spAfter(), "CLASS learning consumed wrong SP."));
		registry.add("10-exact-item-conservation", _ -> assertLearnItems());
		registry.add("11-learned-skill-observed", _ -> PhantomAssertions.assertTrue(_finalObservation.learnedSkills().getOrDefault(_learn.getSkillId(), 0) >= _learn.getSkillLevel(), "Learned skill is absent from observation."));
		registry.add("12-learning-idempotent", _ -> PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, _idempotentLearnResult.status(), "Repeated CLASS learning was not idempotent."));
		registry.add("13-owned-item-equip-success", _ -> PhantomAssertions.assertEquals(OperationStatus.SUCCESS, _equipResult.status(), "Canonical owned-item equip failed."));
		registry.add("14-exact-item-object-equipped", _ -> PhantomAssertions.assertTrue(_finalObservation.equippedItems().stream().anyMatch(item -> item.objectId() == _equipment.getObjectId()), "Exact equipment object was not observed."));
		registry.add("15-canonical-quest-required", _ -> PhantomAssertions.assertTrue(_progression.professionTargets(_profile.profileId()).stream().allMatch(target -> target.canonicalQuestRequired() && ((target.status() == ProfessionStatus.CANONICAL_QUEST_REQUIRED) || (target.status() == ProfessionStatus.LEVEL_PENDING))), "Profession target fabricated a quest completion."));
		registry.add("16-real-profession-change-observed", _ -> PhantomAssertions.assertTrue(_professionObserved, "Real Player class change was not observed."));
		registry.add("17-no-operation-leak", _ -> PhantomAssertions.assertEquals(0, _progression.snapshot().currentOperations(), "Progression operation slot leaked."));
		registry.add("18-no-actor-lease-leak", _ -> PhantomAssertions.assertEquals(0, _progression.snapshot().currentActorLeases(), "Progression actor lease leaked."));
		registry.add("19-over-64-equipment-objects-owned", _ -> PhantomAssertions.assertTrue(_pagingEquipmentCount > 64, "Real Player did not own more than 64 equipment objects."));
		registry.add("20-equipment-paging-reaches-each-object-once", _ -> PhantomAssertions.assertTrue(_pagingAllReached, "Real owned equipment paging lost or duplicated objects."));
		registry.add("21-equipment-family-filter-is-exact", _ -> PhantomAssertions.assertTrue(_pagingFilterExact, "Real owned equipment filter changed membership."));
		registry.add("22-lower-grade-equipment-remains-reachable", _ -> PhantomAssertions.assertTrue(_pagingLowerGradeReached, "Real lower-grade equipment was hidden by paging."));
		registry.add("23-main-subclass-main-is-exact", _ -> PhantomAssertions.assertTrue(_mainSubclassMainExact, "Canonical main to subclass to main snapshots were not exact."));
		registry.add("24-ordinary-skills-do-not-contaminate-subclass", _ -> PhantomAssertions.assertTrue(_ordinarySkillsIsolated, "Main-class ordinary skill contaminated the subclass snapshot."));
		registry.add("25-certification-is-represented-separately", _ -> PhantomAssertions.assertTrue(_certificationSeparated, "Certification skill was treated as ordinary wrong-class evidence."));
		registry.add("26-real-servitor-body-is-exact", _ -> PhantomAssertions.assertTrue(_servitorBodyExact, "Real Servitor body snapshot was absent or inexact."));
		registry.add("27-real-baby-pet-body-is-exact", _ -> PhantomAssertions.assertTrue(_babyPetBodyExact, "Real BabyPet body snapshot was absent or inexact."));
		registry.add("28-real-cubic-has-no-body", _ -> PhantomAssertions.assertTrue(_cubicBodyAbsent, "Real Cubic received a fabricated body."));
	}

	private void exerciseCanonicalReward() throws Exception
	{
		_player.getStat().setLevel((byte) 19);
		_player.getStat().setExp(ExperienceData.getInstance().getExpForLevel(20) - 1);
		_player.setSp(0);
		final NpcTemplate template = NpcData.getInstance().getTemplates(value -> value.isType("Monster") && (value.getLevel() >= 15) && (value.getLevel() <= 25) && (value.getExp() > 0)).stream().min(Comparator.comparingInt(NpcTemplate::getLevel).thenComparingInt(NpcTemplate::getId)).orElseThrow();
		final Monster monster = new Monster(template);
		monster.setInstanceId(0);
		monster.spawnMe(_player.getX() + 20, _player.getY(), _player.getZ());
		_worldFixtures.add(monster);
		_expBefore = _player.getExp();
		_spBeforeReward = _player.getSp();
		_levelBefore = _player.getLevel();
		monster.reduceCurrentHp(monster.getMaxHp() + 1, _player, null);
		await(() -> _player.getExp() > _expBefore, "Canonical reward did not reach Player.");
		_expAfter = _player.getExp();
		_spAfterReward = _player.getSp();
		_levelAfter = _player.getLevel();
		final var observation = _progression.observeActor(_profile.profileId()).result();
		PhantomAssertions.assertEquals(SnapshotStatus.FOUND, observation.status(), "Reward actor observation failed.");
		_rewardObservation = observation.snapshot();
	}

	private void exerciseCanonicalLearning()
	{
		final Trainer trainer = createTrainer();
		_player.setLastFolkNPC(trainer);
		_learn = SkillTreeData.getInstance().getAvailableSkills(_player, _player.getLearningClass(), false, false).stream().filter(value -> value.isLearnedByNpc() && (value.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass()) > 0)).min(Comparator.comparingInt(SkillLearn::getSkillId).thenComparingInt(SkillLearn::getSkillLevel)).orElseThrow();
		_learnSpCost = _learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
		_player.setSp(_learnSpCost + 123);
		final java.util.LinkedHashMap<Integer, Long> before = new java.util.LinkedHashMap<>();
		for (ItemHolder required : _learn.getRequiredItems())
		{
			PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, required.getId(), required.getCount() + 5, _player, this) != null, "Could not configure required learning item.");
			before.put(required.getId(), _player.getInventory().getInventoryItemCount(required.getId(), -1));
		}
		_learnSpBefore = _player.getSp();
		_learnItemsBefore = Map.copyOf(before);
		final LearnSkillRequest request = new LearnSkillRequest(_profile.profileId(), trainer.getObjectId(), AcquireKind.CLASS, _learn.getSkillId(), _learn.getSkillLevel(), () -> false);
		_learnResult = _progression.learnClassSkill(request);
		_idempotentLearnResult = _progression.learnClassSkill(request);
	}

	private Trainer createTrainer()
	{
		for (NpcTemplate template : NpcData.getInstance().getTemplates(value -> "Trainer".equals(value.getType())))
		{
			final Trainer trainer = new Trainer(template);
			if (template.canTeach(_player.getLearningClass()))
			{
				trainer.setInstanceId(0);
				trainer.spawnMe(_player.getX() + 10, _player.getY(), _player.getZ());
				_worldFixtures.add(trainer);
				return trainer;
			}
			trainer.deleteMe();
		}
		throw new AssertionError("No real Trainer can teach the test Player class.");
	}

	private void exerciseCanonicalEquip()
	{
		PhantomAssertions.assertTrue(ItemData.getInstance().getTemplate(EQUIP_ITEM_ID).isEquipable(), "Fixture item is not equippable.");
		_equipment = _player.getInventory().addItem(ItemProcessType.REWARD, EQUIP_ITEM_ID, 1, _player, this);
		PhantomAssertions.assertTrue(_equipment != null, "Could not create test-owned equipment.");
		_equipResult = _progression.equipOwnedItem(new EquipItemRequest(_profile.profileId(), _equipment.getObjectId(), () -> false));
		final var observation = _progression.observeActor(_profile.profileId()).result();
		PhantomAssertions.assertEquals(SnapshotStatus.FOUND, observation.status(), "Final actor observation failed.");
		_finalObservation = observation.snapshot();
	}

	private void exerciseEquipmentPaging()
	{
		final ArrayList<EquipmentFact> candidates = new ArrayList<>();
		String cursor = null;
		do
		{
			final var page = _progression.catalog().equipment(new PageRequest(cursor, 256));
			candidates.addAll(page.values().stream().filter(fact -> !fact.stackable()).toList());
			cursor = page.nextCursor();
		}
		while (cursor != null);
		final EquipmentFact lower = candidates.stream().filter(fact -> fact.crystalGrade().equals("NONE")).findFirst().orElseThrow();
		final ArrayList<EquipmentFact> selected = new ArrayList<>();
		selected.add(lower);
		for (EquipmentFact candidate : candidates)
		{
			if ((selected.size() >= 70) || selected.stream().anyMatch(value -> value.itemId() == candidate.itemId()))
			{
				continue;
			}
			selected.add(candidate);
		}
		PhantomAssertions.assertEquals(70, selected.size(), "Could not select 70 real equipment templates.");
		final Map<Integer, EquipmentFact> byObjectId = new java.util.LinkedHashMap<>();
		for (EquipmentFact fact : selected)
		{
			final Item item = _player.getInventory().addItem(ItemProcessType.REWARD, fact.itemId(), 1, _player, this);
			PhantomAssertions.assertTrue(item != null, "Could not create real paged equipment fixture.");
			_pagingEquipment.add(item);
			byObjectId.put(item.getObjectId(), fact);
		}
		_pagingEquipmentCount = byObjectId.size();
		final Set<Integer> reached = queryOwnedEquipment(OwnedEquipmentFilter.all());
		_pagingAllReached = reached.containsAll(byObjectId.keySet()) && (reached.stream().filter(byObjectId::containsKey).count() == byObjectId.size());
		final Item lowerItem = _pagingEquipment.getFirst();
		_pagingLowerGradeReached = reached.contains(lowerItem.getObjectId()) && byObjectId.get(lowerItem.getObjectId()).crystalGrade().equals("NONE");
		final String family = lower.family();
		final Set<Integer> filtered = queryOwnedEquipment(new OwnedEquipmentFilter(null, family, null));
		final Set<Integer> expectedFiltered = byObjectId.entrySet().stream().filter(entry -> entry.getValue().family().equals(family)).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final Set<Integer> actualFiltered = filtered.stream().filter(byObjectId::containsKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
		_pagingFilterExact = expectedFiltered.equals(actualFiltered);
		for (Item item : List.copyOf(_pagingEquipment))
		{
			if (_player.getInventory().getItemByObjectId(item.getObjectId()) != null)
			{
				PhantomAssertions.assertTrue(_player.getInventory().destroyItem(ItemProcessType.DESTROY, item, _player, this) != null, "Could not clean paged equipment fixture.");
			}
		}
		_pagingEquipment.clear();
	}

	private Set<Integer> queryOwnedEquipment(OwnedEquipmentFilter filter)
	{
		final HashSet<Integer> result = new HashSet<>();
		String cursor = null;
		do
		{
			final var page = _progression.equipmentCandidates(_profile.profileId(), filter, new PageRequest(cursor, 23));
			for (OwnedEquipmentFact fact : page.values())
			{
				PhantomAssertions.assertTrue(result.add(fact.objectId()), "Owned equipment paging repeated a real object.");
			}
			cursor = page.nextCursor();
		}
		while (cursor != null);
		return Set.copyOf(result);
	}

	private void exerciseControlledActors()
	{
		final List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact> facts = allSummonFacts();
		final var servitorFact = facts.stream().filter(fact -> fact.actorKind() == ActorKind.SERVITOR).findFirst().orElseThrow();
		final Servitor servitor = new Servitor(NpcData.getInstance().getTemplate(servitorFact.actorIdentity()), _player);
		servitor.setReferenceSkill(servitorFact.skillId());
		servitor.fullRestore();
		servitor.setXYZ(_player.getX() + 5, _player.getY(), _player.getZ());
		_player.setPet(servitor);
		final var servitorSnapshot = _progression.observeActor(_profile.profileId()).result().snapshot().controlledActors().getFirst();
		_servitorBodyExact = (servitorSnapshot.actorKind() == ActorKind.SERVITOR) && (servitorSnapshot.actorIdentity() == servitor.getId()) && (servitorSnapshot.referenceSkillId() == servitorFact.skillId()) && (servitorSnapshot.body() != null) && (servitorSnapshot.body().objectId() == servitor.getObjectId()) && (Double.compare(servitorSnapshot.body().currentHp(), servitor.getCurrentHp()) == 0) && (Double.compare(servitorSnapshot.body().currentMp(), servitor.getCurrentMp()) == 0);
		_player.setPet(null);
		servitor.deleteMe();

		final var babyFact = facts.stream().filter(fact -> fact.actorKind() == ActorKind.BABY_PET).findFirst().orElseThrow();
		final Item control = _player.getInventory().addItem(ItemProcessType.REWARD, babyFact.controlItemId(), 1, _player, this);
		PhantomAssertions.assertTrue(control != null, "Could not create real BabyPet control item.");
		final BabyPet baby = new BabyPet(NpcData.getInstance().getTemplate(babyFact.actorIdentity()), _player, control);
		baby.fullRestore();
		baby.setXYZ(_player.getX() + 6, _player.getY(), _player.getZ());
		_player.setPet(baby);
		final var babySnapshot = _progression.observeActor(_profile.profileId()).result().snapshot().controlledActors().getFirst();
		_babyPetBodyExact = (babySnapshot.actorKind() == ActorKind.BABY_PET) && (babySnapshot.actorIdentity() == baby.getId()) && (babySnapshot.body() != null) && (babySnapshot.body().objectId() == baby.getObjectId());
		_player.setPet(null);
		baby.deleteMe();
		PhantomAssertions.assertTrue(_player.getInventory().destroyItem(ItemProcessType.DESTROY, control, _player, this) != null, "Could not clean BabyPet control item.");

		_player.addCubic(Cubic.STORM_CUBIC, 1, 1, 10, 100, 1, 600, false);
		final var cubic = _progression.observeActor(_profile.profileId()).result().snapshot().controlledActors().stream().filter(fact -> fact.actorKind() == ActorKind.CUBIC).findFirst().orElseThrow();
		_cubicBodyAbsent = (cubic.actorIdentity() == Cubic.STORM_CUBIC) && (cubic.body() == null);
		_player.stopCubics();
	}

	private List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact> allSummonFacts()
	{
		final ArrayList<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact> result = new ArrayList<>();
		String cursor = null;
		do
		{
			final var page = _progression.catalog().summons(new PageRequest(cursor, 256));
			result.addAll(page.values());
			cursor = page.nextCursor();
		}
		while (cursor != null);
		return List.copyOf(result);
	}

	private void exerciseSubclassSwitch()
	{
		final int baseClassId = _player.getBaseClass();
		final int subclassId = 94;
		final Skill certification = SkillData.getInstance().getSkill(631, 1);
		PhantomAssertions.assertTrue(certification != null, "Certification skill fixture is unavailable.");
		_player.addSkill(certification, true);
		ActorProgressionSnapshot main = _progression.observeActor(_profile.profileId()).result().snapshot();
		final Set<Integer> subclassTreeSkills = _progression.catalog().classSkillLearns(subclassId).stream().map(value -> value.skillId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
		Integer mainExclusive = main.learnedSkills().keySet().stream().filter(skillId -> (skillId != certification.getId()) && !subclassTreeSkills.contains(skillId)).findFirst().orElse(null);
		if (mainExclusive == null)
		{
			final var learn = _progression.catalog().classSkillLearns(baseClassId).stream().filter(value -> !subclassTreeSkills.contains(value.skillId())).findFirst().orElseThrow();
			_player.addSkill(SkillData.getInstance().getSkill(learn.skillId(), learn.skillLevel()), true);
			mainExclusive = learn.skillId();
			main = _progression.observeActor(_profile.profileId()).result().snapshot();
		}
		PhantomAssertions.assertTrue(_player.addSubClass(subclassId, 1), "Canonical Player.addSubClass failed in test DB.");
		_player.setActiveClass(1);
		final ActorProgressionSnapshot subclass = _progression.observeActor(_profile.profileId()).result().snapshot();
		_player.setActiveClass(0);
		final ActorProgressionSnapshot restoredMain = _progression.observeActor(_profile.profileId()).result().snapshot();
		_mainSubclassMainExact = (main.activeClassId() == baseClassId) && (main.classIndex() == 0) && (subclass.activeClassId() == subclassId) && (subclass.classIndex() == 1) && (restoredMain.activeClassId() == baseClassId) && (restoredMain.classIndex() == 0);
		_ordinarySkillsIsolated = !subclass.learnedSkills().containsKey(mainExclusive) && restoredMain.learnedSkills().containsKey(mainExclusive);
		_certificationSeparated = main.certificationSkillIds().contains(certification.getId()) && !subclass.learnedSkills().containsKey(certification.getId()) && !subclass.certificationSkillIds().contains(certification.getId()) && restoredMain.certificationSkillIds().contains(certification.getId());
	}

	private void exerciseProfessionObservation()
	{
		_progression.observeActor(_profile.profileId());
		_player.setPlayerClass(1);
		_professionObserved = _progression.observeActor(_profile.profileId()).professionTransitionObserved();
	}

	private void assertLearnItems()
	{
		for (ItemHolder required : _learn.getRequiredItems())
		{
			final long expected = required.getCount();
			final long actual = _learnItemsBefore.get(required.getId()) - _learnResult.itemCountsAfter().get(required.getId());
			PhantomAssertions.assertEquals(expected, actual, "CLASS learning consumed wrong required item count.");
		}
		PhantomAssertions.assertEquals(_learnSpBefore, _learnResult.spBefore(), "Learning SP baseline changed under lease.");
	}

	private void relocatePlayer()
	{
		if (_player.isTeleporting())
		{
			_player.onTeleported();
		}
		if (_player.isSpawned())
		{
			_player.decayMe();
		}
		_player.setXYZInvisible(-71338, 258271, -3104);
		_player.spawnMe();
		_player.revalidateZone(true);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private void cleanup() throws Exception
	{
		if (_closed)
		{
			return;
		}
		_closed = true;
		Throwable failure = null;
		try
		{
			if (_progression != null)
			{
				_progression.beginStop();
				PhantomAssertions.assertTrue(_progression.finishStop(), "Progression integration service did not stop.");
			}
			for (var fixture : List.copyOf(_worldFixtures))
			{
				if (fixture.isSpawned())
				{
					fixture.deleteMe();
				}
			}
			_worldFixtures.clear();
			if (_materialization != null)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, _materialization.shutdown().state(), "Materialization service did not stop.");
			}
			if ((_repository != null) && (_profile != null))
			{
				_repository.find(_profile.profileId()).ifPresent(profile -> _repository.delete(profile.profileId(), profile.rowVersion()));
			}
			if (_knowledge != null)
			{
				_knowledge.beginStop();
				PhantomAssertions.assertTrue(_knowledge.finishStop(), "Knowledge service did not stop.");
			}
			if (_player != null)
			{
				_environment.assertClean(_environment.primary(), _player);
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
