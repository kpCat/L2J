/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.enums.CategoryType;
import org.l2jmobius.gameserver.data.holders.PetData;
import org.l2jmobius.gameserver.data.xml.CategoryData;
import org.l2jmobius.gameserver.data.xml.ClassListData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PetDataTable;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.player.SubClassHolder;
import org.l2jmobius.gameserver.model.actor.instance.BabyPet;
import org.l2jmobius.gameserver.model.actor.instance.Folk;
import org.l2jmobius.gameserver.model.actor.instance.Pet;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerSkillLearn;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ArmorType;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.AcquireSkillType;
import org.l2jmobius.gameserver.model.skill.holders.SkillHolder;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
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
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PetSkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearnFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassEligibility;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.CapabilitySeed;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.CapabilitySemantics;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.RawPet;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.RawSummon;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.SourceData;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionSourceParser.TreeSkillKey;

/**
 * Read-only loader copying adapter plus the two explicitly allowed canonical
 * Player operations.
 */
public final class L2jProgressionBackend implements PhantomProgressionBackend
{
	private static final String PLAYER_CLASS_SOURCE = "java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java";
	private static final Comparator<SkillRef> SKILL_ORDER = Comparator.comparingInt(SkillRef::skillId).thenComparingInt(SkillRef::skillLevel);
	private static final Set<EffectType> HEAL_EFFECTS = Set.of(EffectType.HEAL, EffectType.CPHEAL, EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT, EffectType.REBALANCE_HP);
	private static final Set<EffectType> RESURRECTION_EFFECTS = Set.of(EffectType.RESURRECTION, EffectType.RESURRECTION_SPECIAL);
	private static final Set<EffectType> CONTROL_EFFECTS = Set.of(EffectType.FEAR, EffectType.MUTE, EffectType.PARALYZE, EffectType.ROOT, EffectType.SLEEP, EffectType.STUN);

	private final PhantomMaterializationService _materialization;
	private final Path _datapackRoot;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledgeQuery;

	public L2jProgressionBackend(PhantomMaterializationService materialization, Path datapackRoot, Supplier<PhantomGameKnowledgeQuery> knowledgeQuery)
	{
		_materialization = materialization;
		_datapackRoot = Objects.requireNonNull(datapackRoot, "datapackRoot");
		_knowledgeQuery = Objects.requireNonNull(knowledgeQuery, "knowledgeQuery");
	}

	@Override
	public BackendData load(PhantomProgressionPolicy policy)
	{
		final SourceData sources = new PhantomProgressionSourceParser(_datapackRoot, policy).parse();
		final List<ClassFact> classes = copyClasses(sources, policy);
		final List<SkillLearnFact> skillLearns = copySkillLearns(classes, sources, policy);
		final List<PetFact> pets = copyPets(sources, policy);
		final List<CapabilityRule> capabilityRules = copyCapabilityRules(classes, skillLearns, sources, policy);
		final List<SummonActorFact> summons = copySummons(skillLearns, pets, sources, policy);
		final List<SkillFact> skills = copySkills(skillLearns, summons, pets, capabilityRules, sources, policy);
		final List<EquipmentFact> equipment = copyEquipment(policy);
		return new BackendData(classes, skillLearns, skills, equipment, summons, pets, capabilityRules);
	}

	private static List<ClassFact> copyClasses(SourceData sources, PhantomProgressionPolicy policy)
	{
		final List<PlayerClass> values = Arrays.stream(PlayerClass.values()).sorted(Comparator.comparingInt(PlayerClass::getId)).toList();
		if (values.size() > policy.maximumClasses())
		{
			throw failure("count", "PlayerClass count exceeds policy.");
		}
		final HashMap<Integer, List<Integer>> children = new HashMap<>();
		for (PlayerClass value : values)
		{
			if (value.getParent() != null)
			{
				children.computeIfAbsent(value.getParent().getId(), _ -> new ArrayList<>()).add(value.getId());
			}
		}
		children.values().forEach(list -> list.sort(Integer::compareTo));
		final ArrayList<ClassFact> result = new ArrayList<>(values.size());
		for (PlayerClass value : values)
		{
			final ArrayList<String> sourcePaths = new ArrayList<>();
			sourcePaths.add(PLAYER_CLASS_SOURCE);
			sources.treeSkillSources().entrySet().stream().filter(entry -> entry.getKey().classId() == value.getId()).map(Map.Entry::getValue).distinct().sorted().findFirst().ifPresent(sourcePaths::add);
			result.add(new ClassFact(value.getId(), value.name(), value.getRace().name(), value.getParent() == null ? null : value.getParent().getId(), sources.skillTreeParents().get(value.getId()), value.getRootClass().getId(), value.level(), value.isMage(), value.isSummoner(), children.getOrDefault(value.getId(), List.of()).isEmpty(), children.getOrDefault(value.getId(), List.of()), Authority.SERVER_LOADER_FACT, sourcePaths));
		}
		return List.copyOf(result);
	}

	private static List<SkillLearnFact> copySkillLearns(List<ClassFact> classes, SourceData sources, PhantomProgressionPolicy policy)
	{
		final SkillTreeData trees = SkillTreeData.getInstance();
		final ArrayList<SkillLearnFact> result = new ArrayList<>();
		for (ClassFact classFact : classes)
		{
			final PlayerClass playerClass = PlayerClass.getPlayerClass(classFact.classId());
			for (SkillLearn learn : trees.getCompleteClassSkillTree(playerClass).values())
			{
				addLearn(result, classFact.classId(), AcquireKind.CLASS, learn, classSource(classFact.classId(), learn, sources), policy);
			}
			final Map<Integer, SkillLearn> transfer = trees.getTransferSkillTree(playerClass);
			if (transfer != null)
			{
				for (SkillLearn learn : transfer.values())
				{
					addLearn(result, classFact.classId(), AcquireKind.TRANSFER, learn, treeSource("transferSkillTree", classFact.classId(), learn, sources), policy);
				}
			}
		}
		for (SkillLearn learn : trees.getSubClassSkillTree().values())
		{
			addLearn(result, -1, AcquireKind.SUBCLASS, learn, treeSource("subClassSkillTree", -1, learn, sources), policy);
		}
		for (Skill learn : trees.getNobleSkillTree().values())
		{
			final String source = sources.treeSkillSources().getOrDefault(new TreeSkillKey("nobleSkillTree", -1, learn.getId(), learn.getLevel()), "data/stats/players/skillTrees/nobleSkillTree.xml");
			if (result.size() >= policy.maximumSkillLearns())
			{
				throw new IllegalStateException("Noble skill-learning facts exceed the catalog safety limit.");
			}
			result.add(new SkillLearnFact(-1, AcquireKind.NOBLE, learn.getId(), learn.getLevel(), 1, 0, List.of(), List.of(), true, false, false, false, Authority.SERVER_LOADER_FACT, source));
		}
		for (SkillLearn learn : trees.getCommonSkillTree().values())
		{
			addLearn(result, -1, AcquireKind.COMMON, learn, treeSource("classSkillTree", -1, learn, sources), policy);
		}
		for (SkillLearn learn : trees.getTransformSkillTree().values())
		{
			addLearn(result, -1, AcquireKind.TRANSFORM, learn, treeSource("transformSkillTree", -1, learn, sources), policy);
		}
		result.sort(Comparator.comparingInt(SkillLearnFact::classId).thenComparing(SkillLearnFact::acquireKind).thenComparingInt(SkillLearnFact::skillId).thenComparingInt(SkillLearnFact::skillLevel));
		final HashSet<String> identities = new HashSet<>();
		for (SkillLearnFact fact : result)
		{
			if (!identities.add(fact.stableKey()))
			{
				throw failure("duplicate", "Duplicate complete skill learn identity.");
			}
		}
		return List.copyOf(result);
	}

	private static void addLearn(List<SkillLearnFact> result, int classId, AcquireKind kind, SkillLearn learn, String source, PhantomProgressionPolicy policy)
	{
		if (result.size() >= policy.maximumSkillLearns())
		{
			throw failure("count", "Skill learn count exceeds policy.");
		}
		final List<RequiredItem> items = learn.getRequiredItems().stream().map(item -> new RequiredItem(item.getId(), item.getCount())).sorted(Comparator.comparingInt(RequiredItem::itemId).thenComparingLong(RequiredItem::count)).toList();
		final List<SkillRef> prerequisites = learn.getPreReqSkills().stream().map(skill -> new SkillRef(skill.getSkillId(), skill.getSkillLevel())).sorted(SKILL_ORDER).toList();
		result.add(new SkillLearnFact(classId, kind, learn.getSkillId(), learn.getSkillLevel(), learn.getGetLevel(), learn.getLevelUpSp(), items, prerequisites, learn.getSkillLevel() == 1, learn.getSkillLevel() > 1, learn.isLearnedByNpc(), learn.isLearnedByFS(), Authority.SERVER_LOADER_FACT, source));
	}

	private static String classSource(int classId, SkillLearn learn, SourceData sources)
	{
		int current = classId;
		final HashSet<Integer> visited = new HashSet<>();
		while ((current >= 0) && visited.add(current))
		{
			final String source = sources.treeSkillSources().get(new TreeSkillKey("classSkillTree", current, learn.getSkillId(), learn.getSkillLevel()));
			if (source != null)
			{
				return source;
			}
			current = sources.skillTreeParents().getOrDefault(current, -1);
		}
		final String common = sources.treeSkillSources().get(new TreeSkillKey("classSkillTree", -1, learn.getSkillId(), learn.getSkillLevel()));
		if (common != null)
		{
			return common;
		}
		throw failure("reference", "Complete class skill has no exact source identity.");
	}

	private static String treeSource(String type, int classId, SkillLearn learn, SourceData sources)
	{
		int current = classId;
		while (current >= -1)
		{
			final String source = sources.treeSkillSources().get(new TreeSkillKey(type, current, learn.getSkillId(), learn.getSkillLevel()));
			if (source != null)
			{
				return source;
			}
			if (current < 0)
			{
				break;
			}
			final PlayerClass playerClass = PlayerClass.getPlayerClass(current);
			current = (playerClass == null) || (playerClass.getParent() == null) ? -1 : playerClass.getParent().getId();
		}
		throw failure("reference", "Skill learn has no exact source identity.");
	}

	private static List<PetFact> copyPets(SourceData sources, PhantomProgressionPolicy policy)
	{
		final ArrayList<PetFact> result = new ArrayList<>();
		for (RawPet raw : sources.pets())
		{
			if (result.size() >= policy.maximumPetFacts())
			{
				throw failure("count", "Pet count exceeds policy.");
			}
			final PetData loaded = PetDataTable.getInstance().getPetData(raw.npcId());
			if ((loaded == null) || (loaded.getItemId() != raw.controlItemId()) || (loaded.getMinLevel() != raw.minimumLevel()) || (loaded.getMaxLevel() != raw.maximumLevel()) || (loaded.getLoad() != raw.load()) || (loaded.getHungryLimit() != raw.hungryLimit()) || (loaded.isSynchLevel() != raw.synchronizedLevel()) || !Set.copyOf(loaded.getFood()).equals(raw.foodItemIds()))
			{
				throw failure("parity", "Strict pet source and PetDataTable disagree.");
			}
			if ((NpcData.getInstance().getTemplate(raw.npcId()) == null) || ((raw.controlItemId() > 0) && (ItemData.getInstance().getTemplate(raw.controlItemId()) == null)))
			{
				throw failure("reference", "Pet NPC/control item reference is missing.");
			}
			for (Integer food : raw.foodItemIds())
			{
				if (ItemData.getInstance().getTemplate(food) == null)
				{
					throw failure("reference", "Pet food item reference is missing.");
				}
			}
			final boolean baby = CategoryData.getInstance().isInCategory(CategoryType.BABY_PET_GROUP, raw.npcId()) || CategoryData.getInstance().isInCategory(CategoryType.UPGRADE_BABY_PET_GROUP, raw.npcId());
			result.add(new PetFact(raw.npcId(), raw.controlItemId(), raw.foodItemIds(), raw.minimumLevel(), raw.maximumLevel(), raw.load(), raw.hungryLimit(), raw.synchronizedLevel(), PetDataTable.isMountable(raw.npcId()), true, true, raw.skills(), Authority.SERVER_LOADER_FACT, raw.sourcePath()));
			if (baby && raw.skills().isEmpty())
			{
				throw failure("parity", "Loaded BabyPet has no skill facts.");
			}
		}
		result.sort(Comparator.comparingInt(PetFact::npcId));
		return List.copyOf(result);
	}

	private List<CapabilityRule> copyCapabilityRules(List<ClassFact> classes, List<SkillLearnFact> learns, SourceData sources, PhantomProgressionPolicy policy)
	{
		final Map<Integer, Set<SkillRef>> skillsByClass = skillsByClass(learns);
		final HashSet<Integer> classIds = new HashSet<>();
		classes.forEach(value -> classIds.add(value.classId()));
		final ArrayList<CapabilityRule> result = new ArrayList<>();
		final PhantomGameKnowledgeQuery query = _knowledgeQuery.get();
		if (query == null)
		{
			throw failure("dependency", "Game Knowledge query is unavailable during progression build.");
		}
		for (ClassFact classFact : classes)
		{
			for (ClassCapabilityFact fact : query.classCapabilities(classFact.classId(), PageRequest.first(256)).values())
			{
				final CapabilitySemantics semantics = sources.capabilitySemantics().get(fact.capabilityKey());
				if (semantics == null)
				{
					throw failure("reference", "Accepted Game Knowledge capability lacks progression semantics.");
				}
				final List<SkillRef> evidence = fact.evidenceSkills().stream().map(value -> new SkillRef(value.skillId(), value.skillLevel())).sorted(SKILL_ORDER).toList();
				addCapability(result, fact.classId(), fact.capabilityKey(), fact.rank(), evidence, semantics, fact.sourceRefs(), classIds, skillsByClass, policy);
			}
		}
		for (CapabilitySeed seed : sources.capabilitySeeds())
		{
			final CapabilitySemantics semantics = sources.capabilitySemantics().get(seed.capabilityKey());
			if (semantics == null)
			{
				throw failure("reference", "Curated capability rule lacks semantics.");
			}
			addCapability(result, seed.classId(), seed.capabilityKey(), seed.rank(), List.of(seed.skill()), semantics, List.of(seed.sourcePath()), classIds, skillsByClass, policy);
		}
		result.sort(Comparator.comparing(CapabilityRule::capabilityKey).thenComparingInt(value -> value.classIds().getFirst()).thenComparingInt(CapabilityRule::rank));
		final HashSet<String> identities = new HashSet<>();
		for (CapabilityRule rule : result)
		{
			if (!identities.add(rule.classIds().getFirst() + ":" + rule.capabilityKey()))
			{
				throw failure("duplicate", "Duplicate class capability rule.");
			}
		}
		return List.copyOf(result);
	}

	private static void addCapability(List<CapabilityRule> result, int classId, String key, int rank, List<SkillRef> evidence, CapabilitySemantics semantics, List<String> sources, Set<Integer> classIds, Map<Integer, Set<SkillRef>> skillsByClass, PhantomProgressionPolicy policy)
	{
		if (result.size() >= policy.maximumCapabilityRules())
		{
			throw failure("count", "Capability rule count exceeds policy.");
		}
		if (!classIds.contains(classId) || !skillsByClass.getOrDefault(classId, Set.of()).containsAll(evidence))
		{
			throw failure("evidence", "Capability evidence is not in the complete class skill tree.");
		}
		final boolean targetRequired = switch (semantics.targetScope())
		{
			case SELF, SERVITOR, PET -> false;
			default -> true;
		};
		final boolean servitorRequired = (semantics.targetScope() == PhantomProgressionModel.TargetScope.SERVITOR) && !"combat.summon".equals(key);
		result.add(new CapabilityRule(key, rank, List.of(classId), evidence, semantics.targetScope(), semantics.equipmentFamilies(), List.of(), targetRequired, servitorRequired, servitorRequired, Authority.CURATED_CAPABILITY_RULE, sources));
	}

	private static Map<Integer, Set<SkillRef>> skillsByClass(List<SkillLearnFact> learns)
	{
		final HashMap<Integer, Set<SkillRef>> mutable = new HashMap<>();
		for (SkillLearnFact learn : learns)
		{
			if ((learn.classId() >= 0) && (learn.acquireKind() == AcquireKind.CLASS))
			{
				mutable.computeIfAbsent(learn.classId(), _ -> new HashSet<>()).add(learn.skill());
			}
		}
		final HashMap<Integer, Set<SkillRef>> result = new HashMap<>();
		mutable.forEach((key, value) -> result.put(key, Set.copyOf(value)));
		return Map.copyOf(result);
	}

	private static List<SummonActorFact> copySummons(List<SkillLearnFact> learns, List<PetFact> pets, SourceData sources, PhantomProgressionPolicy policy)
	{
		final HashMap<SkillRef, Set<Integer>> classesBySkill = new HashMap<>();
		for (SkillLearnFact learn : learns)
		{
			if ((learn.classId() >= 0) && (learn.acquireKind() == AcquireKind.CLASS))
			{
				classesBySkill.computeIfAbsent(learn.skill(), _ -> new HashSet<>()).add(learn.classId());
			}
		}
		final ArrayList<SummonActorFact> result = new ArrayList<>();
		for (RawSummon raw : sources.summons())
		{
			final Skill skill = requireSkill(raw.skillId(), raw.skillLevel());
			final NpcTemplate template = raw.actorKind() == ActorKind.CUBIC ? null : NpcData.getInstance().getTemplate(raw.actorIdentity());
			if ((raw.actorKind() != ActorKind.CUBIC) && (template == null))
			{
				throw failure("reference", "Summon effect NPC reference is missing.");
			}
			final List<Integer> ownerClasses = classesBySkill.getOrDefault(new SkillRef(raw.skillId(), raw.skillLevel()), Set.of()).stream().sorted().toList();
			final ActorKind kind = raw.actorKind() == ActorKind.CUBIC ? ActorKind.CUBIC : template.getRace() == Race.SIEGE_WEAPON ? ActorKind.SIEGE_SUMMON : ownerClasses.isEmpty() ? ActorKind.QUEST_SUMMON : ActorKind.SERVITOR;
			final int interval = (raw.upkeepItemId() == 0) ? 0 : (raw.upkeepIntervalMillis() > 0 ? raw.upkeepIntervalMillis() : (template.getRace() == Race.SIEGE_WEAPON ? 60_000 : 240_000));
			result.add(new SummonActorFact(ownerClasses, raw.skillId(), raw.skillLevel(), raw.actorIdentity(), kind, raw.lifetimeMillis(), raw.expMultiplier(), skill.getItemConsumeId(), raw.upkeepItemId(), raw.upkeepItemCount(), interval, 0, Set.of(), template == null ? 0 : template.getSoulShot(), template == null ? 0 : template.getSpiritShot(), false, false, false, false, false, false, true, true, true, true, Authority.STATIC_DATAPACK_FACT, List.of(raw.sourcePath(), raw.actorKind() == ActorKind.CUBIC ? "dist/game/data/scripts/handlers/skill/effects/SummonCubic.java" : "dist/game/data/scripts/handlers/skill/effects/Summon.java")));
		}
		for (PetFact pet : pets)
		{
			// Wyvern is present in PetData with itemId=-1 and has no SummonPet
			// acquisition skill. Its complete actor data remains represented by
			// PetFact; do not fabricate a summon skill identity for it.
			if (pet.controlItemId() <= 0)
			{
				continue;
			}
			final ItemTemplate control = ItemData.getInstance().getTemplate(pet.controlItemId());
			final SkillRef summonSkill = Arrays.stream(control.getSkills()).map(holder -> new SkillRef(holder.getSkillId(), Math.max(1, holder.getSkillLevel()))).filter(ref -> requireSkill(ref.skillId(), ref.skillLevel()).hasEffectType(EffectType.SUMMON_PET)).findFirst().orElseThrow(() -> failure("reference", "Pet control item has no SummonPet skill."));
			boolean heal = false;
			boolean recharge = false;
			boolean buff = false;
			for (PetSkillFact petSkill : pet.skills())
			{
				final Skill loaded = SkillData.getInstance().getSkill(petSkill.skillId(), Math.max(1, petSkill.skillLevel()));
				if (loaded != null)
				{
					heal |= hasAny(loaded, HEAL_EFFECTS);
					recharge |= loaded.hasEffectType(EffectType.MANAHEAL_BY_LEVEL, EffectType.MANAHEAL_PERCENT);
					buff |= loaded.hasEffectType(EffectType.BUFF);
				}
			}
			final boolean baby = CategoryData.getInstance().isInCategory(CategoryType.BABY_PET_GROUP, pet.npcId()) || CategoryData.getInstance().isInCategory(CategoryType.UPGRADE_BABY_PET_GROUP, pet.npcId());
			final NpcTemplate template = NpcData.getInstance().getTemplate(pet.npcId());
			result.add(new SummonActorFact(List.of(), summonSkill.skillId(), summonSkill.skillLevel(), pet.npcId(), baby ? ActorKind.BABY_PET : ActorKind.PET, 0, 0, 0, 0, 0, 0, pet.controlItemId(), pet.foodItemIds(), template.getSoulShot(), template.getSpiritShot(), pet.mountable(), true, true, heal, recharge, buff, true, true, true, true, Authority.SERVER_LOADER_FACT, List.of(pet.sourcePath(), itemSource(pet.controlItemId()), "dist/game/data/scripts/handlers/skill/effects/SummonPet.java")));
		}
		if (result.size() > policy.maximumSummonFacts())
		{
			throw failure("count", "Controlled actor fact count exceeds policy.");
		}
		result.sort(Comparator.comparing(SummonActorFact::stableKey));
		return List.copyOf(result);
	}

	private static List<SkillFact> copySkills(List<SkillLearnFact> learns, List<SummonActorFact> summons, List<PetFact> pets, List<CapabilityRule> capabilities, SourceData sources, PhantomProgressionPolicy policy)
	{
		final HashSet<SkillRef> referenced = new HashSet<>();
		learns.forEach(value -> referenced.add(value.skill()));
		summons.forEach(value -> referenced.add(value.skill()));
		capabilities.forEach(value -> referenced.addAll(value.evidenceSkills()));
		for (PetFact pet : pets)
		{
			for (PetSkillFact skill : pet.skills())
			{
				referenced.add(new SkillRef(skill.skillId(), Math.max(1, skill.skillLevel())));
			}
		}
		if (referenced.size() > policy.maximumSkillFacts())
		{
			throw failure("count", "Referenced skill count exceeds policy.");
		}
		final ArrayList<SkillFact> result = new ArrayList<>(referenced.size());
		for (SkillRef reference : referenced.stream().sorted(SKILL_ORDER).toList())
		{
			final Skill skill = requireSkill(reference.skillId(), reference.skillLevel());
			final String source = sources.skillSources().get(reference.skillId());
			if (source == null)
			{
				throw failure("reference", "Referenced Skill has no exact datapack source.");
			}
			result.add(new SkillFact(skill.getId(), skill.getLevel(), skill.isActive(), skill.isPassive(), skill.isToggle(), skill.isPhysical(), skill.isMagic(), skill.getTargetType().name(), skill.isDamage(), skill.hasNegativeEffect(), hasAny(skill, HEAL_EFFECTS), hasAny(skill, RESURRECTION_EFFECTS), skill.hasEffectType(EffectType.BUFF), skill.isDebuff() || skill.hasEffectType(EffectType.DEBUFF), hasAny(skill, CONTROL_EFFECTS), skill.getItemConsumeId(), skill.getItemConsumeCount(), skill.getMpConsume() + skill.getMpInitialConsume(), skill.getHpConsume(), skill.getReuseDelay(), ConditionPresence.DYNAMIC_SERVER_CONDITION, skill.isBlockedInOlympiad(), skill.isPvPOnly(), skill.isSuicideAttack(), skill.isRemovedOnAnyActionExceptMove(), skill.isTransformation(), Authority.SERVER_LOADER_FACT, List.of(source)));
		}
		return List.copyOf(result);
	}

	private static boolean hasAny(Skill skill, Set<EffectType> types)
	{
		for (EffectType type : types)
		{
			if (skill.hasEffectType(type))
			{
				return true;
			}
		}
		return false;
	}

	private static List<EquipmentFact> copyEquipment(PhantomProgressionPolicy policy)
	{
		final ArrayList<EquipmentFact> result = new ArrayList<>();
		for (ItemTemplate template : ItemData.getInstance().getAllItems())
		{
			if ((template == null) || !template.isEquipable())
			{
				continue;
			}
			if (result.size() >= policy.maximumEquipmentFacts())
			{
				throw failure("count", "Equippable item count exceeds policy.");
			}
			final String weaponType = template instanceof Weapon weapon ? weapon.getItemType().name() : "";
			final String armorType = template instanceof Armor armor ? armor.getItemType().name() : "";
			final String family = equipmentFamily(template);
			result.add(new EquipmentFact(template.getId(), template.getBodyPart().name(), family, weaponType, armorType, template.getCrystalType().name(), template.getDefaultAction().name(), template.isStackable(), template.getWeight(), template.isConditionAttached() ? ConditionPresence.DYNAMIC_SERVER_CONDITION : ConditionPresence.NONE, Authority.SERVER_LOADER_FACT, itemSource(template.getId())));
		}
		result.sort(Comparator.comparingInt(EquipmentFact::itemId));
		return List.copyOf(result);
	}

	private static String equipmentFamily(ItemTemplate template)
	{
		if (template instanceof Weapon weapon)
		{
			return weapon.getItemType().name();
		}
		final BodyPart bodyPart = template.getBodyPart();
		if (Set.of(BodyPart.R_EAR, BodyPart.L_EAR, BodyPart.LR_EAR, BodyPart.NECK, BodyPart.R_FINGER, BodyPart.L_FINGER, BodyPart.LR_FINGER, BodyPart.R_BRACELET, BodyPart.L_BRACELET, BodyPart.DECO, BodyPart.BELT).contains(bodyPart))
		{
			return "ACCESSORY";
		}
		if (template instanceof Armor armor)
		{
			return armor.getItemType().name();
		}
		return "OTHER";
	}

	private static String itemSource(int itemId)
	{
		final int start = (itemId / 100) * 100;
		return "data/stats/items/" + String.format("%05d-%05d.xml", start, start + 99);
	}

	private static Skill requireSkill(int skillId, int skillLevel)
	{
		final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
		if (skill == null)
		{
			throw failure("reference", "Referenced Skill is missing.");
		}
		return skill;
	}

	@Override
	public Optional<ActorLease> tryAcquireActor(long profileId)
	{
		if (_materialization == null)
		{
			return Optional.empty();
		}
		return _materialization.tryAcquireAction(profileId).map(lease -> new L2jActorLease(profileId, lease));
	}

	private static IllegalStateException failure(String category, String message)
	{
		return new ProgressionBackendException(category, message);
	}

	public static final class ProgressionBackendException extends IllegalStateException
	{
		private static final long serialVersionUID = 1L;
		private final String _category;

		ProgressionBackendException(String category, String message)
		{
			super(message);
			_category = category;
		}

		public String category()
		{
			return _category;
		}
	}

	private static final class L2jActorLease implements ActorLease
	{
		private final long _profileId;
		private final ActionLease _lease;
		private final Player _player;
		private boolean _closed;

		L2jActorLease(long profileId, ActionLease lease)
		{
			_profileId = profileId;
			_lease = lease;
			_player = lease.player();
		}

		@Override
		public ActorProgressionSnapshot snapshot(String catalogHash, Set<Integer> referencedResourceItemIds, Set<Integer> certificationSkillIds, int maximumOwnedEquipmentCandidates)
		{
			requireOpen();
			final Map<Integer, Integer> learnedSkills = _player.getSkills().values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Skill::getId, Skill::getLevel, Math::max));
			final ArrayList<SubclassFact> subclasses = new ArrayList<>();
			_player.getSubClasses().values().stream().sorted(Comparator.comparingInt(SubClassHolder::getClassIndex)).limit(PlayerConfig.MAX_SUBCLASS).forEach(value -> subclasses.add(new SubclassFact(value.getClassIndex(), value.getId(), value.getLevel(), value.getExp(), value.getSp())));
			final ArrayList<EquippedItemFact> equipped = new ArrayList<>();
			for (int slot = 0; slot < Inventory.PAPERDOLL_TOTALSLOTS; slot++)
			{
				final Item item = _player.getInventory().getPaperdollItem(slot);
				if (item != null)
				{
					equipped.add(new EquippedItemFact(item.getObjectId(), item.getId(), slot));
				}
			}
			final List<OwnedEquipmentFact> owned = ownedEquipment(maximumOwnedEquipmentCandidates);
			final HashMap<Integer, Long> resources = new HashMap<>();
			for (Integer itemId : referencedResourceItemIds)
			{
				resources.put(itemId, _player.getInventory().getInventoryItemCount(itemId, -1));
			}
			final ArrayList<ControlledActorFact> controlled = new ArrayList<>();
			final Summon summon = _player.getSummon();
			if (summon != null)
			{
				final ActorKind kind = summon instanceof BabyPet ? ActorKind.BABY_PET : summon instanceof Pet ? ActorKind.PET : summon.getTemplate().getRace() == Race.SIEGE_WEAPON ? ActorKind.SIEGE_SUMMON : ActorKind.SERVITOR;
				final int referenceSkill = summon instanceof Servitor servitor ? servitor.getReferenceSkill() : 0;
				controlled.add(new ControlledActorFact(summon.getObjectId(), summon.getId(), kind, referenceSkill));
			}
			_player.getCubics().values().stream().sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.instance.Cubic::getId)).forEach(cubic -> controlled.add(new ControlledActorFact(0, cubic.getId(), ActorKind.CUBIC, 0)));
			final HashSet<Integer> certifications = new HashSet<>();
			for (Integer skillId : certificationSkillIds)
			{
				if (learnedSkills.containsKey(skillId))
				{
					certifications.add(skillId);
				}
			}
			return new ActorProgressionSnapshot(_profileId, _player.getObjectId(), _player.getBaseClass(), _player.getPlayerClass().getId(), _player.getClassIndex(), _player.getPlayerClass().level(), _player.getLevel(), _player.getExp(), _player.getSp(), _player.isNoble(), _player.isHero(), _player.isSubClassActive(), subclasses, learnedSkills, equipped, owned, resources, controlled, _player.isTransformed(), _player.isMounted(), _player.isInCombat(), _player.isCastingNow() || _player.isCastingSimultaneouslyNow(), _player.isAlikeDead(), subclassQuestSatisfied(), PlayerConfig.MAX_SUBCLASS, certifications, catalogHash);
		}

		private List<OwnedEquipmentFact> ownedEquipment(int maximum)
		{
			final Comparator<OwnedEquipmentFact> order = Comparator.comparing(OwnedEquipmentFact::stableKey);
			final PriorityQueue<OwnedEquipmentFact> bounded = new PriorityQueue<>(maximum, order.reversed());
			for (Item item : _player.getInventory().getItems())
			{
				if (!item.isEquipable() || ((item.getItemLocation() != ItemLocation.INVENTORY) && (item.getItemLocation() != ItemLocation.PAPERDOLL)))
				{
					continue;
				}
				final ItemTemplate template = item.getTemplate();
				final boolean compatible = item.isEquipped() || template.checkCondition(_player, _player, false);
				final ArrayList<String> reasons = new ArrayList<>();
				if (!compatible)
				{
					reasons.add("DYNAMIC_SERVER_CONDITION");
				}
				final long score = (compatible ? 1_000_000L : 0) + (template.getCrystalType().getLevel() * 10_000L) + (item.getEnchantLevel() * 100L) + Math.max(0, 99 - (item.getId() % 100));
				final OwnedEquipmentFact fact = new OwnedEquipmentFact(item.getObjectId(), item.getId(), template.getBodyPart().name(), equipmentFamily(template), template.getCrystalType().name(), item.getEnchantLevel(), item.isEquipped(), compatible, reasons, score);
				if (bounded.size() < maximum)
				{
					bounded.add(fact);
				}
				else if (order.compare(fact, bounded.peek()) < 0)
				{
					bounded.poll();
					bounded.add(fact);
				}
			}
			return bounded.stream().sorted(order).toList();
		}

		private boolean subclassQuestSatisfied()
		{
			if (PlayerConfig.ALT_GAME_SUBCLASS_WITHOUT_QUESTS || _player.isNoble())
			{
				return true;
			}
			final QuestState fate = _player.getQuestState("Q00234_FatesWhisper");
			final QuestState mimir = _player.getQuestState("Q00235_MimirsElixir");
			return (fate != null) && fate.isCompleted() && (mimir != null) && mimir.isCompleted();
		}

		@Override
		public SkillReadinessProbe canonicalSkillReadiness(SkillRef reference, Integer targetObjectId)
		{
			requireOpen();
			final Skill skill = SkillData.getInstance().getSkill(reference.skillId(), reference.skillLevel());
			if (skill == null)
			{
				return new SkillReadinessProbe(false, false, false);
			}
			final WorldObject target = targetObjectId == null ? _player : World.getInstance().findObject(targetObjectId);
			final boolean condition = (target != null) && skill.checkCondition(_player, target, false);
			final boolean resources = (_player.getCurrentMp() >= (skill.getMpConsume() + skill.getMpInitialConsume())) && (_player.getCurrentHp() > skill.getHpConsume());
			return new SkillReadinessProbe(condition, resources, !_player.isSkillDisabled(skill));
		}

		@Override
		public List<SubclassEligibility> subclassEligibility(List<ClassFact> classes)
		{
			requireOpen();
			final Npc last = _player.getLastFolkNPC();
			final VillageMaster master = last instanceof VillageMaster value ? value : null;
			final Set<PlayerClass> available;
			if (master == null)
			{
				available = Set.of();
			}
			else
			{
				final PlayerClass base = PlayerClass.getPlayerClass(_player.getBaseClass());
				final int secondClassId = base.level() > 2 ? base.getParent().getId() : base.getId();
				final Set<PlayerClass> found = master.getSubclasses(_player, secondClassId);
				available = found == null ? Set.of() : Set.copyOf(found);
			}
			final HashSet<Integer> used = new HashSet<>();
			_player.getSubClasses().values().forEach(value -> used.add(value.getId()));
			final boolean allLevelReady = (_player.getLevel() >= 75) && _player.getSubClasses().values().stream().allMatch(value -> value.getLevel() >= 75);
			final boolean capacity = _player.getTotalSubClasses() < PlayerConfig.MAX_SUBCLASS;
			final boolean quest = subclassQuestSatisfied();
			final ArrayList<SubclassEligibility> result = new ArrayList<>();
			for (ClassFact fact : classes)
			{
				final boolean category = CategoryData.getInstance().isInCategory(CategoryType.THIRD_CLASS_GROUP, fact.classId());
				if (!category)
				{
					continue;
				}
				final PlayerClass playerClass = PlayerClass.getPlayerClass(fact.classId());
				result.add(new SubclassEligibility(fact.classId(), true, (master != null) && master.checkVillageMaster(playerClass), available.contains(playerClass), used.contains(fact.classId()), allLevelReady, capacity, quest));
			}
			result.sort(Comparator.comparingInt(SubclassEligibility::classId));
			return List.copyOf(result);
		}

		@Override
		public OperationResult learnClassSkill(LearnSkillRequest request, BooleanSupplier ownershipCurrent)
		{
			requireOpen();
			if (request.acquireKind() != AcquireKind.CLASS)
			{
				return OperationResult.rejected(OperationStatus.UNSUPPORTED_ACQUIRE_TYPE);
			}
			if (request.planOwnershipToken().isCancelled() || !ownershipCurrent.getAsBoolean())
			{
				return OperationResult.rejected(OperationStatus.CANCELLED);
			}
			if (_player.isAlikeDead() || _player.isTransformed() || _player.isMounted() || _player.isInCombat() || _player.isCastingNow() || _player.isCastingSimultaneouslyNow())
			{
				return OperationResult.rejected(OperationStatus.ACTOR_STATE_REJECTED);
			}
			final Npc trainer = _player.getLastFolkNPC();
			if (trainer == null)
			{
				return OperationResult.rejected(OperationStatus.TRAINER_REQUIRED);
			}
			if ((trainer.getObjectId() != request.trainerObjectId()) || !(trainer instanceof Folk) || !trainer.canInteract(_player))
			{
				return OperationResult.rejected(OperationStatus.TRAINER_MISMATCH);
			}
			if (!trainer.getTemplate().canTeach(_player.getLearningClass()))
			{
				return OperationResult.rejected(OperationStatus.TRAINER_CANNOT_TEACH);
			}
			final Skill skill = SkillData.getInstance().getSkill(request.skillId(), request.skillLevel());
			if (skill == null)
			{
				return OperationResult.rejected(OperationStatus.SKILL_NOT_FOUND);
			}
			final int knownLevel = _player.getSkillLevel(request.skillId());
			if (knownLevel >= request.skillLevel())
			{
				return new OperationResult(OperationStatus.IDEMPOTENT, _player.getSp(), _player.getSp(), Map.of(), Map.of(), knownLevel, false);
			}
			if ((request.skillLevel() > 1) && (knownLevel != (request.skillLevel() - 1)))
			{
				return OperationResult.rejected(OperationStatus.PREVIOUS_SKILL_MISSING);
			}
			final SkillLearn learn = SkillTreeData.getInstance().getSkillLearn(AcquireSkillType.CLASS, request.skillId(), request.skillLevel(), _player);
			if (learn == null)
			{
				return OperationResult.rejected(OperationStatus.SKILL_LEARN_NOT_FOUND);
			}
			if (_player.getLevel() < learn.getGetLevel())
			{
				return OperationResult.rejected(OperationStatus.LEVEL_TOO_LOW);
			}
			final int spCost = learn.getCalculatedLevelUpSp(_player.getPlayerClass(), _player.getLearningClass());
			if (_player.getSp() < spCost)
			{
				return OperationResult.rejected(OperationStatus.SP_TOO_LOW);
			}
			for (SkillHolder prerequisite : learn.getPreReqSkills())
			{
				if (_player.getSkillLevel(prerequisite.getSkillId()) < prerequisite.getSkillLevel())
				{
					return OperationResult.rejected(OperationStatus.PREREQUISITE_MISSING);
				}
			}
			final LinkedHashMap<Integer, Long> beforeItems = new LinkedHashMap<>();
			for (ItemHolder required : learn.getRequiredItems().stream().sorted(Comparator.comparingInt(ItemHolder::getId)).toList())
			{
				final long count = _player.getInventory().getInventoryItemCount(required.getId(), -1);
				beforeItems.put(required.getId(), count);
				if (count < required.getCount())
				{
					return OperationResult.rejected(OperationStatus.REQUIRED_ITEM_MISSING);
				}
			}
			if (request.planOwnershipToken().isCancelled() || !ownershipCurrent.getAsBoolean())
			{
				return OperationResult.rejected(OperationStatus.CANCELLED);
			}
			final long spBefore = _player.getSp();
			for (ItemHolder required : learn.getRequiredItems().stream().sorted(Comparator.comparingInt(ItemHolder::getId)).toList())
			{
				if (!_player.destroyItemByItemId(ItemProcessType.FEE, required.getId(), required.getCount(), trainer, false))
				{
					return OperationResult.rejected(OperationStatus.BLOCKED_CANONICAL_SKILL_LEARNING);
				}
			}
			if (spCost > 0)
			{
				_player.setSp(spBefore - spCost);
			}
			_player.addSkill(skill, true);
			_player.updateShortcuts(skill.getId(), skill.getLevel());
			if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_SKILL_LEARN, trainer))
			{
				EventDispatcher.getInstance().notifyEventAsync(new OnPlayerSkillLearn(trainer, _player, skill, AcquireSkillType.CLASS), trainer);
			}
			final LinkedHashMap<Integer, Long> afterItems = new LinkedHashMap<>();
			beforeItems.keySet().forEach(itemId -> afterItems.put(itemId, _player.getInventory().getInventoryItemCount(itemId, -1)));
			final long spAfter = _player.getSp();
			if ((_player.getSkillLevel(skill.getId()) < skill.getLevel()) || (spAfter != (spBefore - spCost)))
			{
				return new OperationResult(OperationStatus.RECONCILIATION_FAILED, spBefore, spAfter, beforeItems, afterItems, _player.getSkillLevel(skill.getId()), false);
			}
			for (ItemHolder required : learn.getRequiredItems())
			{
				if ((beforeItems.get(required.getId()) - afterItems.get(required.getId())) != required.getCount())
				{
					return new OperationResult(OperationStatus.RECONCILIATION_FAILED, spBefore, spAfter, beforeItems, afterItems, _player.getSkillLevel(skill.getId()), false);
				}
			}
			return new OperationResult(OperationStatus.SUCCESS, spBefore, spAfter, beforeItems, afterItems, _player.getSkillLevel(skill.getId()), false);
		}

		@Override
		public OperationResult equipOwnedItem(EquipItemRequest request, BooleanSupplier ownershipCurrent)
		{
			requireOpen();
			if (request.planOwnershipToken().isCancelled() || !ownershipCurrent.getAsBoolean())
			{
				return OperationResult.rejected(OperationStatus.CANCELLED);
			}
			if (_player.isAlikeDead() || _player.isTransformed() || _player.isMounted() || _player.isInCombat() || _player.isCastingNow() || _player.isCastingSimultaneouslyNow() || _player.isAttackingNow())
			{
				return OperationResult.rejected(OperationStatus.ACTOR_STATE_REJECTED);
			}
			final Item item = _player.getInventory().getItemByObjectId(request.itemObjectId());
			if ((item == null) || (item.getOwnerId() != _player.getObjectId()) || ((item.getItemLocation() != ItemLocation.INVENTORY) && (item.getItemLocation() != ItemLocation.PAPERDOLL)))
			{
				return OperationResult.rejected(OperationStatus.ITEM_NOT_OWNED);
			}
			if (item.isEquipped())
			{
				return new OperationResult(OperationStatus.IDEMPOTENT, _player.getSp(), _player.getSp(), Map.of(), Map.of(), 0, true);
			}
			if (!item.isEquipable() || !_player.getInventory().canManipulateWithItemId(item.getId()))
			{
				return OperationResult.rejected(OperationStatus.ITEM_NOT_EQUIPPABLE);
			}
			if (!item.getTemplate().checkCondition(_player, _player, false))
			{
				return OperationResult.rejected(OperationStatus.ITEM_CONDITION_FAILED);
			}
			if (request.planOwnershipToken().isCancelled() || !ownershipCurrent.getAsBoolean())
			{
				return OperationResult.rejected(OperationStatus.CANCELLED);
			}
			_player.useEquippableItem(item, false);
			if ((_player.getInventory().getItemByObjectId(item.getObjectId()) != item) || !item.isEquipped())
			{
				return new OperationResult(OperationStatus.RECONCILIATION_FAILED, _player.getSp(), _player.getSp(), Map.of(), Map.of(), 0, false);
			}
			return new OperationResult(OperationStatus.SUCCESS, _player.getSp(), _player.getSp(), Map.of(), Map.of(), 0, true);
		}

		private void requireOpen()
		{
			if (_closed || _lease.isClosed())
			{
				throw new IllegalStateException("Progression actor lease is closed.");
			}
		}

		@Override
		public void close()
		{
			if (!_closed)
			{
				_closed = true;
				_lease.close();
			}
		}
	}
}
