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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.DynamicExpRateData;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ExperienceLossData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.TownManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.FarmInput;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningSnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropDisposition;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DropOrigin;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyKind;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.util.MathUtil;

/**
 * Captures the exact current Player/loader facts admitted by
 * BACKGROUND_MODEL_V1. Unsupported dynamic reward or combat contexts fail
 * closed rather than being approximated silently.
 */
public final class L2jPhantomBackgroundAuthority implements PhantomBackgroundAuthority
{
	public static final long INITIAL_RNG_SEED = 15001501L;
	private static final long MAX_TRAVEL_BUDGET_MILLIS = 60_000;
	private static final int MAX_RECOVERY_ANCHOR_DISTANCE = 100_000;
	private static final String LOOT_POLICY_VERSION = "LOOT_POLICY_V1";

	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;
	private final Supplier<PhantomTopologyQuery> _topology;
	private final Supplier<PhantomProgressionCatalog> _progression;
	private final Supplier<PhantomCommerceCatalog> _commerce;
	private final PhantomNormalGatekeeperTravel _travel;
	private volatile PhantomNormalGatekeeperTravel _topologyTravel;

	public L2jPhantomBackgroundAuthority(Supplier<PhantomGameKnowledgeQuery> knowledge, Supplier<PhantomTopologyQuery> topology, Supplier<PhantomProgressionCatalog> progression, Supplier<PhantomCommerceCatalog> commerce)
	{
		this(knowledge, topology, progression, commerce, null);
	}

	public L2jPhantomBackgroundAuthority(Supplier<PhantomGameKnowledgeQuery> knowledge, Supplier<PhantomTopologyQuery> topology, Supplier<PhantomProgressionCatalog> progression, Supplier<PhantomCommerceCatalog> commerce, PhantomNormalGatekeeperTravel travel)
	{
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_progression = Objects.requireNonNull(progression, "progression");
		_commerce = Objects.requireNonNull(commerce, "commerce");
		_travel = travel;
	}

	@Override
	public PhantomNormalGatekeeperTravel travelQuery(PhantomTopologyQuery topology)
	{
		if (_travel != null)
		{
			return _travel.forTopology(topology);
		}
		PhantomNormalGatekeeperTravel current = _topologyTravel;
		if ((current == null) || (current.topology() != topology))
		{
			current = PhantomNormalGatekeeperTravel.empty(topology);
			_topologyTravel = current;
		}
		return current;
	}

	@Override
	public List<String> travelLegIds()
	{
		return _travel == null ? List.of() : _travel.legs().stream().map(PhantomNormalGatekeeperTravel.Leg::id).toList();
	}

	@Override
	public Hashes hashes()
	{
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyQuery topology = _topology.get();
		final PhantomProgressionCatalog progression = _progression.get();
		final PhantomCommerceCatalog commerce = _commerce.get();
		final String topologyHash = (_travel == null) || _travel.legs().isEmpty() ? topology.snapshot().canonicalHash() : digest("BACKGROUND_TRAVEL_V1", topology.snapshot().canonicalHash(), _travel.hash());
		return new Hashes(compositeKnowledgeHash(knowledge.combinedHash()), topologyHash, progression.combinedHash(), commerce.hashes().combined());
	}

	@Override
	public PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
	{
		return capture(profileId, player, goal, previous, false);
	}

	@Override
	public NativeCapture captureOwnedNative(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
	{
		final var state = capture(profileId, player, goal, previous, true);
		return new NativeCapture(state, captureNativeContext(player, state));
	}

	private PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous, boolean nativePersistence)
	{
		Objects.requireNonNull(player, "player");
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parseLifecycle(goal);
		requireSupportedPlayer(player, nativePersistence);
		final PhantomTopologyAnchor anchor = nativePersistence ? nativeAnchor(player, goal, previous) : exactAnchor(player, previous);
		final Capability capability = capability(player, spec);
		final Tracking currentTracking = tracking(player, spec, capability);
		// Arrival attests the committed projection before a new goal projection can replace it.
		// Read actual native counts/locations; the caller still verifies objects and full inventory hash.
		final Tracking tracking = nativePersistence && (previous != null) && ((previous.state() == State.READY) || (previous.state() == State.DEAD))
			? tracking(player, previous.inventory().mutableItemIds()) : currentTracking;
		final Identity identity = new Identity(profileId, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
		final Progress progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
		final Vitals vitals = new Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp());
		final Position position = new Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), anchor.id());
		final CombatFacts combat = combatFacts(player, capability);
		final Loadout loadout = new Loadout(capability.skillId(), capability.skillLevel(), capability.summonNpcId(), capability.mpConsume(), spec.shotItemId(), spec.shotsPerEncounter(), spec.summonResourceItemId(), spec.summonResourcesPerEncounter());
		final InventoryFacts inventory = InventoryFacts.sorted(tracking.mutableItemIds(), tracking.objects(), "", player.getCurrentLoad(), player.getMaxLoad(), player.getInventory().getSize(), player.getInventoryLimit());
		final List<AutoGetSkill> autoSkills = autoGetSkills(identity, player.getLevel());
		final Clock clock = previous == null ? new Clock(INITIAL_RNG_SEED, 0, 0) : previous.clock();
		final Receipt receipt = previous == null ? Receipt.empty() : previous.receipt();
		return new PhantomBackgroundState(State.MATERIALIZED, identity, progress, vitals, position, combat, loadout, inventory, autoSkills, clock, receipt, hashes());
	}

	@Override
	public PhantomBackgroundState captureAcquisition(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous, int targetItemId)
	{
		return captureAcquisition(profileId, player, goal, previous, targetItemId, false);
	}

	@Override
	public NativeCapture captureOwnedNativeAcquisition(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous, int targetItemId)
	{
		final var state = captureAcquisition(profileId, player, goal, previous, targetItemId, true);
		return new NativeCapture(state, captureNativeContext(player, state));
	}

	private PhantomBackgroundState captureAcquisition(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous, int targetItemId, boolean nativePersistence)
	{
		Objects.requireNonNull(player, "player");
		final PhantomAcquisitionGoalSpec spec = PhantomAcquisitionGoalSpec.parse(goal);
		if (spec.itemId() != targetItemId)
		{
			throw new IllegalArgumentException("Acquisition background target item changed.");
		}
		requireSupportedPlayer(player, nativePersistence);
		final PhantomTopologyAnchor anchor = nativePersistence ? nativeAnchor(player, goal, previous) : exactAnchor(player, previous);
		final Capability capability = capability(player, null);
		final Identity identity = new Identity(profileId, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
		final Progress progress = new Progress(player.getLevel(), player.getExp(), player.getSp(), player.getExpBeforeDeath());
		final Vitals vitals = new Vitals(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), player.getMaxMp(), player.getCurrentCp(), player.getMaxCp());
		final Position position = new Position(player.getInstanceId(), player.getX(), player.getY(), player.getZ(), player.getHeading(), anchor.id());
		final CombatFacts combat = combatFacts(player, capability);
		final Loadout loadout = new Loadout(capability.skillId(), capability.skillLevel(), 0, capability.mpConsume(), 0, 0, 0, 0);
		final List<ItemObject> objects = player.getInventory().getItems().stream().filter(item -> ((item.getId() == targetItemId) && (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY)) || (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.PAPERDOLL)).sorted(Comparator.comparingInt(Item::getObjectId)).map(item -> new ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), ItemLocation.valueOf(item.getItemLocation().name()))).toList();
		final InventoryFacts inventory = InventoryFacts.sorted(List.of(targetItemId), objects, "", player.getCurrentLoad(), player.getMaxLoad(), player.getInventory().getSize(), player.getInventoryLimit());
		final List<AutoGetSkill> autoSkills = autoGetSkills(identity, player.getLevel());
		final Clock clock = previous == null ? new Clock(INITIAL_RNG_SEED, 0, 0) : previous.clock();
		final Receipt receipt = previous == null ? Receipt.empty() : previous.receipt();
		return new PhantomBackgroundState(State.MATERIALIZED, identity, progress, vitals, position, combat, loadout, inventory, autoSkills, clock, receipt, hashes());
	}

	/**
	 * Validates only the persisted resource contract against the current
	 * Player/loadout and current production catalogs. This narrow diagnostic is
	 * used by the focused gate without weakening the exact NPC/anchor checks in
	 * {@link #capture(long, Player, PhantomGoal, PhantomBackgroundState)}.
	 */
	public ShotContract validateShotContract(Player player, PhantomGoal goal)
	{
		Objects.requireNonNull(player, "player");
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		requireSupportedPlayer(player);
		final Capability capability = capability(player, spec);
		validateShot(player, spec, capability);
		validateSummonResource(player, spec, capability);
		return new ShotContract(capability.kind(), spec.shotItemId(), spec.shotsPerEncounter(), spec.summonResourceItemId(), spec.summonResourcesPerEncounter());
	}

	@Override
	public PlanningSnapshot planningSnapshot(Player player)
	{
		Objects.requireNonNull(player, "player");
		// Planning selects original native work too; simulation remains fenced separately.
		requireSupportedPlayer(player, true);
		final PhantomTopologyAnchor anchor = exactAnchor(player, null);
		final Capability capability = capability(player, null);
		final PhantomBackgroundGoalSpec zeroResourceContract = new PhantomBackgroundGoalSpec(1, anchor.id(), 0, 0, 0, 0, 0);
		validateShot(player, zeroResourceContract, capability);
		validateSummonResource(player, zeroResourceContract, capability);
		return new PlanningSnapshot(player.getLevel(), player.getActiveClass(), anchor.id(), 0, 0, 0, 0, 0);
	}

	@Override
	public boolean matchesRuntime(Player player, PhantomBackgroundState state)
	{
		if ((player == null) || (player.getObjectId() != state.identity().characterObjectId()) || (player.getClassIndex() != state.identity().classIndex()) || (player.getActiveClass() != state.identity().activeClassId()) || (player.getRace().ordinal() != state.identity().raceOrdinal()))
		{
			return false;
		}
		return (player.getLevel() == state.progress().level()) && (player.getExp() == state.progress().experience()) && (player.getSp() == state.progress().skillPoints()) && (player.getExpBeforeDeath() == state.progress().experienceBeforeDeath()) && close(player.getCurrentHp(), state.vitals().currentHp()) && close(player.getMaxHp(), state.vitals().maximumHp()) && close(player.getCurrentMp(), state.vitals().currentMp()) && close(player.getMaxMp(), state.vitals().maximumMp()) && close(player.getCurrentCp(), state.vitals().currentCp()) && close(player.getMaxCp(), state.vitals().maximumCp()) && (player.getInstanceId() == state.position().instanceId()) && (player.getX() == state.position().x()) && (player.getY() == state.position().y()) && (player.getZ() == state.position().z()) && (player.getHeading() == state.position().heading());
	}

	@Override
	public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal)
	{
		return farmInput(state, goal, Map.of());
	}

	@Override
	public List<Integer> ordinarySpoilSkillIds(int activeClassId)
	{
		return PhantomOrdinarySpoilEvidence.candidateSkillIds(_progression.get().capabilities(activeClassId));
	}

	@Override
	public FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, Map<Integer, Integer> learnedSkills)
	{
		final FarmInputAttempt attempt = tryFarmInput(state, goal, learnedSkills);
		if (attempt.successful()) { return attempt.input(); }
		if (attempt.failure() == FarmInputFailure.AUTHORITY_STALE) { throw new IllegalStateException(attempt.reason()); }
		throw new IllegalArgumentException(attempt.reason());
	}

	@Override
	public FarmInputAttempt tryFarmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, Map<Integer, Integer> learnedSkills)
	{
		try
		{
			return FarmInputAttempt.ready(currentFarmInput(state, goal, learnedSkills));
		}
		catch (FarmInputRejected exception)
		{
			return FarmInputAttempt.failed(exception._failure, exception.getMessage());
		}
		catch (RuntimeException exception)
		{
			return FarmInputAttempt.failed(FarmInputFailure.UNKNOWN, exception.getClass().getSimpleName());
		}
	}

	private FarmInput currentFarmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, Map<Integer, Integer> learnedSkills)
	{
		learnedSkills = Map.copyOf(learnedSkills);
		if (!state.hashes().equals(hashes()))
		{
			throw new FarmInputRejected(FarmInputFailure.AUTHORITY_STALE, "farm.generation_changed");
		}
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyAnchor anchor = _topology.get().findAnchor(goal.anchorId()).orElseThrow(() -> new FarmInputRejected(FarmInputFailure.POSITION_STALE, "farm.anchor_absent"));
		if ((anchor.point().instanceId() != 0) || !anchor.id().equals(state.position().committedAnchorId()) || !atCanonicalAnchor(state.position(), anchor))
		{
			throw new FarmInputRejected(FarmInputFailure.POSITION_STALE, "farm.position_not_canonical");
		}
		final Loadout loadout = state.loadout();
		if ((goal.shotItemId() != loadout.shotItemId()) || (goal.shotsPerEncounter() != loadout.shotsPerEncounter()) || (goal.summonNpcId() != loadout.summonNpcId()) || (goal.summonResourceItemId() != loadout.summonResourceItemId()) || (goal.summonResourcesPerEncounter() != loadout.summonResourcesPerEncounter()) || (!learnedSkills.isEmpty() && (loadout.selectedSkillId() > 0) && (learnedSkills.getOrDefault(loadout.selectedSkillId(), 0) < loadout.selectedSkillLevel())))
		{
			throw new FarmInputRejected(FarmInputFailure.RESOURCE_STALE, "farm.durable_loadout_changed");
		}
		final var npc = knowledge.npcById().get(goal.npcId());
		final NpcTemplate template = NpcData.getInstance().getTemplate(goal.npcId());
		if ((npc == null) || (template == null) || (npc.kind() != NpcKind.MONSTER) || !npc.attackable() || !npc.targetable() || (npc.level() != template.getLevel()))
		{
			throw new FarmInputRejected(FarmInputFailure.TARGET_STALE, "farm.target_not_authoritative");
		}
		final List<SpawnAreaFact> areas = knowledge.spawnAreasByNpc().getOrDefault(goal.npcId(), List.of()).stream().filter(area -> (area.instanceId() == 0) && anchor.nodeId().equals(area.topologyNodeId())).toList();
		final long configuredAmount = areas.stream().mapToLong(SpawnAreaFact::totalConfiguredAmount).sum();
		if (configuredAmount <= 0)
		{
			throw new FarmInputRejected(FarmInputFailure.TARGET_STALE, "farm.spawn_absent");
		}
		final List<Drop> drops = new ArrayList<>(drops(state, npc.level(), knowledge.dropFactsByNpc().getOrDefault(goal.npcId(), List.of())));
		if (PhantomOrdinarySpoilEvidence.eligible(_progression.get().capabilities(state.identity().activeClassId()), learnedSkills))
		{
			for (DropFact fact : knowledge.spoilFactsByNpc().getOrDefault(goal.npcId(), List.of()))
			{
				drops.add(drop(state, npc.level(), fact, DropOrigin.ORDINARY_SPOIL, true));
			}
		}
		if (drops.stream().map(Drop::itemId).distinct().count() > PhantomBackgroundModel.MAX_GROUND_LOSS_ITEM_IDS)
		{
			throw new FarmInputRejected(FarmInputFailure.UNSUPPORTED_LOOT, "farm.loot_evidence_bound");
		}
		final Target target = new Target(goal.npcId(), npc.level(), true, template.getBaseHpMax(), template.getBaseMpMax(), template.getBasePAtk(), template.getBaseMAtk(), template.getBasePDef(), template.getBaseMDef(), template.getBasePAtkSpd(), template.getBaseMAtkSpd(), npc.exp(), npc.sp(), drops, RatesConfig.DROP_MAX_OCCURRENCES_NORMAL);
		final double expRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicExpRate(state.progress().level()) : RatesConfig.RATE_XP;
		final double spRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicSpRate(state.progress().level()) : RatesConfig.RATE_SP;
		return new FarmInput(target, new RewardPolicy(RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, expRate, spRate), deathPolicy(state), experienceTable(), levelForExperience(), anchor.nodeId(), (int) Math.clamp(configuredAmount, 1, 32));
	}

	@Override
	public FarmInput acquisitionInput(PhantomBackgroundState state, Source source)
	{
		return acquisitionInput(state, source, Map.of());
	}

	@Override
	public FarmInput acquisitionInput(PhantomBackgroundState state, Source source, Map<Integer, Integer> learnedSkills)
	{
		learnedSkills = Map.copyOf(learnedSkills);
		final boolean ordinaryAcquisition = (source.method() == Method.DEATH_DROP) || (source.method() == Method.SPOIL_SWEEP);
		final boolean specializedAcquisition = (source.method() == Method.MANOR_CROP) || (source.method() == Method.QUEST_COLLECTION);
		if (!state.hashes().equals(hashes()) || (source.instanceId() != 0) || (source.itemId() <= 0) || (!ordinaryAcquisition && !specializedAcquisition))
		{
			throw new IllegalStateException("Acquisition background authority generation or source is invalid.");
		}
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyAnchor anchor = _topology.get().findAnchor(source.anchorId()).orElseThrow(() -> new IllegalArgumentException("Acquisition source anchor is absent."));
		if (!anchor.nodeId().equals(source.topologyNodeId()) || !anchor.id().equals(state.position().committedAnchorId()) || !atCanonicalAnchor(state.position(), anchor))
		{
			throw new IllegalArgumentException("Acquisition background source is not at the committed anchor.");
		}
		final var npc = knowledge.npcById().get(source.npcId());
		final NpcTemplate template = NpcData.getInstance().getTemplate(source.npcId());
		if ((npc == null) || (template == null) || (npc.kind() != NpcKind.MONSTER) || !npc.attackable() || !npc.targetable() || (npc.level() != template.getLevel()))
		{
			throw new IllegalArgumentException("Acquisition source is not an authoritative normal monster.");
		}
		final boolean spawned = knowledge.spawnAreasByNpc().getOrDefault(source.npcId(), List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && source.topologyNodeId().equals(area.topologyNodeId()) && (area.totalConfiguredAmount() > 0));
		if (!spawned)
		{
			throw new IllegalArgumentException("Acquisition source has no authoritative spawn at its anchor.");
		}
		final DropSourceKind expectedKind = source.method() == Method.DEATH_DROP ? DropSourceKind.DEATH_DROP : DropSourceKind.SPOIL;
		final List<DropFact> selectedFacts = source.method() == Method.DEATH_DROP ? knowledge.dropFactsByNpc().getOrDefault(source.npcId(), List.of()) : source.method() == Method.SPOIL_SWEEP ? knowledge.spoilFactsByNpc().getOrDefault(source.npcId(), List.of()) : List.of();
		final DropFact selected = ordinaryAcquisition ? selectedFacts.stream().filter(fact -> (fact.itemId() == source.itemId()) && (fact.sourceKind() == expectedKind) && fact.stableKey().equals(source.factKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("Acquisition source fact is stale.")) : null;
		if ((source.method() == Method.SPOIL_SWEEP) && !durableSpoilEligible(state, source, learnedSkills))
		{
			throw new IllegalArgumentException("Durable acquisition spoil capability evidence is absent.");
		}
		final List<Drop> result = new ArrayList<>();
		for (DropFact fact : knowledge.dropFactsByNpc().getOrDefault(source.npcId(), List.of()))
		{
			final DropOrigin origin = (source.method() == Method.DEATH_DROP) && fact.stableKey().equals(selected.stableKey()) ? DropOrigin.ACQUISITION_TARGET : DropOrigin.INCIDENTAL_DEATH_DROP;
			result.add(drop(state, npc.level(), fact, origin, false));
		}
		if (source.method() == Method.SPOIL_SWEEP)
		{
			result.add(drop(state, npc.level(), selected, DropOrigin.ACQUISITION_TARGET, true));
		}
		final Target target = new Target(source.npcId(), npc.level(), true, template.getBaseHpMax(), template.getBaseMpMax(), template.getBasePAtk(), template.getBaseMAtk(), template.getBasePDef(), template.getBaseMDef(), template.getBasePAtkSpd(), template.getBaseMAtkSpd(), npc.exp(), npc.sp(), List.copyOf(result), RatesConfig.DROP_MAX_OCCURRENCES_NORMAL);
		final double expRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicExpRate(state.progress().level()) : RatesConfig.RATE_XP;
		final double spRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicSpRate(state.progress().level()) : RatesConfig.RATE_SP;
		final long configuredAmount = knowledge.spawnAreasByNpc().getOrDefault(source.npcId(), List.of()).stream().filter(area -> source.topologyNodeId().equals(area.topologyNodeId())).mapToLong(SpawnAreaFact::totalConfiguredAmount).sum();
		return new FarmInput(target, new RewardPolicy(RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, expRate, spRate), deathPolicy(state), experienceTable(), levelForExperience(), source.topologyNodeId(), (int) Math.clamp(configuredAmount, 1, 32));
	}

    @Override public long topologyGeneration() { return _topology.get().snapshot().generation(); }
    @Override public boolean canFarmAt(Position position, PhantomBackgroundGoalSpec goal)
    {
        final var anchor = _topology.get().findAnchor(goal.anchorId()).orElse(null);
        return anchor != null && anchor.role() == PhantomTopologyAnchorRole.FARMING && atCanonicalAnchor(position, anchor);
    }

    @Override
    public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis, long logicalEpochMinute, PhantomBackgroundSimulationPolicy policy)
    {
        if (policy == null) { return advanceTravel(state, goal, elapsedBudgetMillis, logicalEpochMinute); }
        if (!policy.permits(PhantomBackgroundSimulationPolicy.Operation.TRAVEL, configuredSimulationFingerprint())
            || elapsedBudgetMillis < 1 || elapsedBudgetMillis > policy.maximumBatchMillis() || !state.hashes().equals(hashes()))
        { return unchanged(Status.NO_ROUTE, state, ""); }
        final var topology = _topology.get();
        final var current = topology.findAnchor(state.position().committedAnchorId()).orElse(null);
        final var arrival = topology.findAnchor(goal.anchorId()).orElse(null);
        if (arrival == null || current == null || state.position().instanceId() != 0 || arrival.role() != PhantomTopologyAnchorRole.FARMING)
        { return unchanged(Status.NO_ROUTE, state, ""); }
        if (atCanonicalAnchor(state.position(), arrival)) { return unchanged(Status.AT_DESTINATION, state, ""); }
        if (atCanonicalAnchor(state.position(), current) && !current.id().equals(arrival.id()))
        { return advanceTravel(state, goal, elapsedBudgetMillis, logicalEpochMinute); }
        final var target = canonicalCommittedAnchorPosition(arrival, state.position().heading()).orElse(null);
        if (target == null || !dryStoredPath(state.position(), target) || !inFarmingArea(target.x(), target.y(), target.z(), 0, arrival))
        { return unchanged(Status.NO_ROUTE, state, "local-return"); }
        final long total = Math.max(1, (long) Math.ceil(Math.hypot((long) target.x() - state.position().x(), (long) target.y() - state.position().y()) * 1000 / policy.runSpeed()));
        if (total > policy.maximumBatchMillis() || state.clock().residualTravelMillis() > total)
        { return unchanged(Status.NO_ROUTE, state, "local-return"); }
        final long remaining = state.clock().residualTravelMillis() == 0 ? total : state.clock().residualTravelMillis();
        if (remaining > elapsedBudgetMillis)
        { return new TravelAdvance(Status.PARTIAL, state.position(), new Clock(state.clock().rngState(), remaining - elapsedBudgetMillis, state.clock().residualEncounterMillis()), "local-return"); }
        return new TravelAdvance(Status.ARRIVED, target, new Clock(state.clock().rngState(), 0, state.clock().residualEncounterMillis()), "local-return");
    }
    /** The same stock height, water and bidirectional movement guards as native local travel. */
    private static boolean dryStoredPath(Position from, Position to)
    {
        final double distance = Math.hypot((long) to.x() - from.x(), (long) to.y() - from.y());
        if (from.instanceId() != 0 || to.instanceId() != 0 || distance > 4096) { return false; }
        final var geo = GeoEngine.getInstance();
        if (!geo.hasGeo(from.x(), from.y()) || geo.getHeight(from.x(), from.y(), from.z()) != from.z()) { return false; }
        final var cells = new org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
        int z = from.z();
        while (cells.next())
        {
            final int x = GeoEngine.getWorldX(cells.x()), y = GeoEngine.getWorldY(cells.y());
            if (!geo.hasGeo(x, y)) { return false; }
            z = geo.getHeight(x, y, z);
            if (z != geo.getHeight(x, y, z) || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null) { return false; }
        }
        final int count = Math.max(1, (int) Math.ceil(distance / 100));
        int px = from.x(), py = from.y(), pz = from.z();
        for (int i = 1; i <= count; i++)
        {
            final int x = from.x() + (int) Math.round(((long) to.x() - from.x()) * (double) i / count);
            final int y = from.y() + (int) Math.round(((long) to.y() - from.y()) * (double) i / count);
            if (!geo.hasGeo(x, y)) { return false; }
            z = geo.getHeight(x, y, pz);
            if (Math.abs((long) z - pz) > 200 || z != geo.getHeight(x, y, z)
                || ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null
                || !geo.canMoveToTarget(px, py, pz, x, y, z, 0) || !geo.canMoveToTarget(x, y, z, px, py, pz, 0)) { return false; }
            px = x; py = y; pz = z;
        }
        return pz == to.z() && ZoneManager.getInstance().getZone(from.x(), from.y(), from.z(), WaterZone.class) == null;
    }
	@Override
	public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis)
	{
		return advanceTravel(state, goal.anchorId(), elapsedBudgetMillis, -1, true);
	}

	@Override
	public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis, long logicalEpochMinute)
	{
		return advanceTravel(state, goal.anchorId(), elapsedBudgetMillis, logicalEpochMinute, true);
	}

	@Override
	public TravelAdvance advanceAcquisitionTravel(PhantomBackgroundState state, Source source, long elapsedBudgetMillis)
	{
		return advanceTravel(state, source.anchorId(), elapsedBudgetMillis, 0, false);
	}

	private TravelAdvance advanceTravel(PhantomBackgroundState state, String destinationAnchorId, long elapsedBudgetMillis, long logicalEpochMinute, boolean allowGatekeeper)
	{
		if ((elapsedBudgetMillis <= 0) || (elapsedBudgetMillis > MAX_TRAVEL_BUDGET_MILLIS))
		{
			throw new IllegalArgumentException("Invalid background travel budget.");
		}
		final PhantomTopologyQuery topology = _topology.get();
		if (!state.hashes().equals(hashes()))
		{
			return unchanged(Status.NO_ROUTE, state, "");
		}
		if (state.position().committedAnchorId().equals(destinationAnchorId))
		{
			return unchanged(Status.AT_DESTINATION, state, "");
		}
		final List<PhantomNormalGatekeeperTravel.Step> route = (allowGatekeeper ? travelQuery(topology) : PhantomNormalGatekeeperTravel.empty(topology)).route(state.position().committedAnchorId(), destinationAnchorId).orElse(null);
		if ((route == null) || route.isEmpty())
		{
			return unchanged(Status.NO_ROUTE, state, "");
		}
		final PhantomNormalGatekeeperTravel.Step first = route.getFirst();
		final String edgeId = first.id();
		if (first.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER)
		{
			return advanceGatekeeper(state, first, topology, elapsedBudgetMillis, logicalEpochMinute);
		}
		final PhantomTopologyEdge edge = topology.snapshot().edgeById().get(edgeId);
		if ((edge == null) || !edge.backgroundEligible())
		{
			return unchanged(Status.EDGE_NOT_ELIGIBLE, state, edgeId);
		}
		if (!topology.isTraversable(edgeId))
		{
			return unchanged(Status.EDGE_CLOSED, state, edgeId);
		}
		final PhantomTopologyAnchor current = topology.findAnchor(state.position().committedAnchorId()).orElse(null);
		if (current == null)
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		if (!atCanonicalAnchor(state.position(), current))
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		final String departureAnchor;
		final String arrivalAnchor;
		if (edge.fromNodeId().equals(current.nodeId()))
		{
			departureAnchor = edge.fromAnchorId();
			arrivalAnchor = edge.toAnchorId();
		}
		else if (edge.bidirectional() && edge.toNodeId().equals(current.nodeId()))
		{
			departureAnchor = edge.toAnchorId();
			arrivalAnchor = edge.fromAnchorId();
		}
		else
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		if (!current.id().equals(departureAnchor) || (arrivalAnchor == null))
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		final PhantomTopologyAnchor arrival = topology.findAnchor(arrivalAnchor).orElse(null);
		if ((arrival == null) || (arrival.point().instanceId() != 0))
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		final Optional<Position> canonicalArrival = canonicalCommittedAnchorPosition(arrival, state.position().heading());
		if (canonicalArrival.isEmpty())
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, edgeId);
		}
		final long remaining = state.clock().residualTravelMillis() == 0 ? edge.baseTravelMillis() : state.clock().residualTravelMillis();
		if (remaining > elapsedBudgetMillis)
		{
			return new TravelAdvance(Status.PARTIAL, state.position(), new Clock(state.clock().rngState(), remaining - elapsedBudgetMillis, state.clock().residualEncounterMillis()), edgeId);
		}
		return new TravelAdvance(Status.ARRIVED, canonicalArrival.get(), new Clock(state.clock().rngState(), 0, state.clock().residualEncounterMillis()), edgeId);
	}

	private TravelAdvance advanceGatekeeper(PhantomBackgroundState state, PhantomNormalGatekeeperTravel.Step step, PhantomTopologyQuery topology, long budgetMillis, long logicalEpochMinute)
	{
		final PhantomNormalGatekeeperTravel.Leg leg = step.gatekeeper();
		final PhantomTopologyAnchor current = topology.findAnchor(step.fromAnchorId()).orElse(null);
		final PhantomTopologyAnchor arrival = topology.findAnchor(step.toAnchorId()).orElse(null);
		if ((current == null) || (arrival == null) || !atCanonicalAnchor(state.position(), current) || (state.position().instanceId() != 0))
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, step.id());
		}
		if ((leg == null) || (logicalEpochMinute < 0) || !PhantomNormalGatekeeperTravel.matchesNative(leg) || !gatekeeperConditions(leg))
		{
			return unchanged(Status.UNSUPPORTED_CONDITION, state, step.id());
		}
		final Optional<Position> position = canonicalCommittedAnchorPosition(arrival, state.position().heading());
		if (position.isEmpty())
		{
			return unchanged(Status.ANCHOR_MISMATCH, state, step.id());
		}
		final long remaining = state.clock().residualTravelMillis() == 0 ? leg.travelMillis() : state.clock().residualTravelMillis();
		if (remaining > budgetMillis)
		{
			return new TravelAdvance(Status.PARTIAL, state.position(), new Clock(state.clock().rngState(), remaining - budgetMillis, state.clock().residualEncounterMillis()), step.id());
		}
		final long fee = PhantomNormalGatekeeperTravel.fee(leg, state.identity().classIndex(), state.progress().level(), logicalEpochMinute);
		if (fee > state.inventory().itemCount(Inventory.ADENA_ID))
		{
			return unchanged(Status.INSUFFICIENT_ADENA, state, step.id());
		}
		return new TravelAdvance(Status.ARRIVED, position.get(), new Clock(state.clock().rngState(), 0, state.clock().residualEncounterMillis()), step.id(), fee);
	}

	private static boolean gatekeeperConditions(PhantomNormalGatekeeperTravel.Leg leg)
	{
		if (PlayerConfig.TELEPORT_WHILE_SIEGE_IN_PROGRESS)
		{
			return true;
		}
		final var town = TownManager.getTown(leg.sourceX(), leg.sourceY(), leg.sourceZ());
		if (town == null)
		{
			return false;
		}
		final var castle = CastleManager.getInstance().getCastleById(town.getTaxById());
		return (castle != null) && !castle.getSiege().isInProgress() && PhantomNormalGatekeeperTravel.destinationCastlesAvailable(leg.destinationCastleIds(), castleId ->
		{
			final var destinationCastle = CastleManager.getInstance().getCastleById(castleId);
			return (destinationCastle != null) && (destinationCastle.getSiege() != null) && !destinationCastle.getSiege().isInProgress();
		});
	}

	@Override
	public Optional<Position> canonicalRecoveryPosition(int x, int y, int z, int instanceId, int heading)
	{
		if (instanceId != 0)
		{
			return Optional.empty();
		}
		final MapRegionData mapRegions = MapRegionData.getInstance();
		final int mapRegionLocId = mapRegions.getMapRegionLocId(x, y);
		if (mapRegionLocId == 0)
		{
			return Optional.empty();
		}
		final PhantomTopologyQuery topology = _topology.get();
		final PhantomTopologyPoint town = new PhantomTopologyPoint(x, y, z, instanceId);
		for (PhantomTopologyAnchorRole role : List.of(PhantomTopologyAnchorRole.RESPAWN, PhantomTopologyAnchorRole.CITY_CENTER, PhantomTopologyAnchorRole.ROUTE))
		{
			for (PhantomTopologyAnchor anchor : topology.nearestAnchors(town, role, 64, MAX_RECOVERY_ANCHOR_DISTANCE))
			{
				if (mapRegions.getMapRegionLocId(anchor.point().x(), anchor.point().y()) != mapRegionLocId)
				{
					continue;
				}
				final Optional<Position> canonical = canonicalCommittedAnchorPosition(anchor, heading);
				if (canonical.isPresent() && uniquelyIdentifiesAnchor(topology, canonical.get(), anchor.id()))
				{
					return canonical;
				}
			}
		}
		return Optional.empty();
	}

	private static boolean uniquelyIdentifiesAnchor(PhantomTopologyQuery topology, Position position, String expectedAnchorId)
	{
		String matchedAnchorId = null;
		for (PhantomTopologyAnchor anchor : topology.snapshot().anchors())
		{
			final Optional<Position> canonical = canonicalCommittedAnchorPosition(anchor, position.heading());
			if (canonical.isEmpty() || (position.instanceId() != canonical.get().instanceId()) || !withinAnchorTolerance(position.x(), position.y(), position.z(), canonical.get(), anchor.validationTolerance()))
			{
				continue;
			}
			if (matchedAnchorId != null)
			{
				return false;
			}
			matchedAnchorId = anchor.id();
		}
		return expectedAnchorId.equals(matchedAnchorId);
	}

	public static Optional<Position> canonicalCommittedAnchorPosition(PhantomTopologyAnchor anchor, int heading)
	{
		Objects.requireNonNull(anchor, "anchor");
		final PhantomTopologyPoint point = anchor.point();
		final GeoEngine geoEngine = GeoEngine.getInstance();
		return canonicalCommittedAnchorPosition(anchor, heading, z -> geoEngine.getHeight(point.x(), point.y(), z));
	}

	private static Optional<Position> canonicalCommittedAnchorPosition(PhantomTopologyAnchor anchor, int heading, IntUnaryOperator heightResolver)
	{
		Objects.requireNonNull(anchor, "anchor");
		Objects.requireNonNull(heightResolver, "heightResolver");
		final PhantomTopologyPoint point = anchor.point();
		if (point.instanceId() != 0)
		{
			return Optional.empty();
		}
		final int x = point.x();
		final int y = point.y();
		final int normalizedZ = heightResolver.applyAsInt(point.z());
		if (normalizedZ != heightResolver.applyAsInt(point.z()))
		{
			return Optional.empty();
		}
		if (Math.abs((long) normalizedZ - point.z()) > anchor.validationTolerance())
		{
			return Optional.empty();
		}
		final int restoredZ = heightResolver.applyAsInt(normalizedZ);
		if (restoredZ != normalizedZ)
		{
			return Optional.empty();
		}
		return Optional.of(new Position(0, x, y, normalizedZ, heading, anchor.id()));
	}

	@Override
	public List<AutoGetSkill> autoGetSkills(Identity identity, int level)
	{
		final PlayerClass playerClass = PlayerClass.getPlayerClass(identity.activeClassId());
		if (playerClass == null)
		{
			throw new IllegalArgumentException("Unknown active class for auto-get reconciliation.");
		}
		final Race race = Race.values()[identity.raceOrdinal()];
		final Map<Integer, Integer> levels = new HashMap<>();
		for (SkillLearn skill : SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values())
		{
			if (skill.isAutoGet() && (skill.getGetLevel() <= level) && (skill.getRaces().isEmpty() || skill.getRaces().contains(race)))
			{
				levels.merge(skill.getSkillId(), skill.getSkillLevel(), Math::max);
			}
		}
		return levels.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new AutoGetSkill(entry.getKey(), entry.getValue())).toList();
	}

	private Capability capability(Player player, PhantomBackgroundGoalSpec goal)
	{
		final PhantomProgressionCatalog catalog = _progression.get();
		final List<CapabilityRule> rules = catalog.capabilities(player.getActiveClass()).stream().filter(rule -> supportedCapability(rule.capabilityKey())).sorted(Comparator.comparingInt(CapabilityRule::rank).reversed().thenComparing(CapabilityRule::stableKey)).toList();
		final Map<String, Boolean> equippedFamilies = new HashMap<>();
		player.getInventory().getPaperdollItems().forEach(item ->
		{
			final var equipment = catalog.equipment(item.getId());
			if (equipment != null)
			{
				equippedFamilies.put(equipment.family(), true);
			}
		});
		for (CapabilityRule rule : rules)
		{
			final Skill known = player.getKnownSkill(rule.actionSkill().skillId());
			final SkillFact skill = catalog.skill(rule.actionSkill());
			if ((known == null) || (known.getLevel() < rule.actionSkill().skillLevel()) || (skill == null) || !skill.damage() || skill.pvpOnly() || skill.suicideAttack() || (skill.hpConsume() != 0) || (skill.itemConsumeId() != 0) || !rule.requiredItems().isEmpty() || !equippedFamilies.keySet().containsAll(rule.requiredEquipmentFamilies()))
			{
				continue;
			}
			if ((goal != null) && (goal.summonNpcId() > 0))
			{
				final Summon summon = player.getSummon();
				if ((summon == null) || (summon.getId() != goal.summonNpcId()))
				{
					continue;
				}
				final SummonActorFact summonFact = catalog.summonsByNpc(goal.summonNpcId()).stream().filter(fact -> fact.ownerClassIds().contains(player.getActiveClass()) && fact.attackSupported()).findFirst().orElse(null);
				if ((summonFact == null) || (summonFact.upkeepItemId() != goal.summonResourceItemId()) || ((summonFact.upkeepItemId() > 0) && (goal.summonResourcesPerEncounter() <= 0)))
				{
					continue;
				}
				return new Capability(ModelKind.SUMMON_PRIMARY, rule.actionSkill().skillId(), rule.actionSkill().skillLevel(), skill.mpConsume(), goal.summonNpcId(), summonFact.expMultiplier(), summon, summonFact);
			}
			final ModelKind kind = switch (rule.capabilityKey())
			{
				case "combat.melee_damage" -> ModelKind.MELEE;
				case "combat.ranged_physical_damage" -> ModelKind.RANGED;
				case "combat.ranged_magic_damage" -> ModelKind.MAGIC;
				default -> throw new IllegalStateException("Unsupported admitted capability.");
			};
			return new Capability(kind, rule.actionSkill().skillId(), rule.actionSkill().skillLevel(), skill.mpConsume(), 0, 0, null, null);
		}
		// Every canonical Player owns the ordinary physical attack represented by Loadout.none(); it consumes no skill MP or item resource.
		return new Capability(ModelKind.MELEE, 0, 0, 0, 0, 0, null, null);
	}

	private Tracking tracking(Player player, PhantomBackgroundGoalSpec goal, Capability capability)
	{
		if (goal.npcId() == 0)
		{
			final List<ItemObject> equipment = player.getInventory().getPaperdollItems().stream().sorted(Comparator.comparingInt(Item::getObjectId)).map(item -> new ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), ItemLocation.PAPERDOLL)).toList();
			return new Tracking(List.of(), equipment);
		}
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyAnchor farmAnchor = _topology.get().findAnchor(goal.anchorId()).orElseThrow(() -> new IllegalArgumentException("Persisted farm anchor is absent."));
		final var npc = knowledge.npcById().get(goal.npcId());
		final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(goal.npcId());
		if ((farmAnchor.point().instanceId() != 0) || (npc == null) || (npcTemplate == null) || (npc.kind() != NpcKind.MONSTER) || !npc.attackable() || !npc.targetable() || (npc.level() != npcTemplate.getLevel()))
		{
			throw new IllegalArgumentException("Persisted target is not an authoritative instance-zero normal monster.");
		}
		final boolean spawned = knowledge.spawnAreasByNpc().getOrDefault(goal.npcId(), List.of()).stream().anyMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && farmAnchor.nodeId().equals(area.topologyNodeId()));
		if (!spawned)
		{
			throw new IllegalArgumentException("Persisted target has no authoritative spawn at the exact farm anchor.");
		}

		final TreeSet<Integer> mutableItemIds = new TreeSet<>();
		final TreeSet<Integer> groundLossItemIds = new TreeSet<>();
		for (DropFact fact : knowledge.dropFactsByNpc().getOrDefault(goal.npcId(), List.of()))
		{
			final ItemTemplate dropTemplate = ItemData.getInstance().getTemplate(fact.itemId());
			if (dropTemplate == null)
			{
				throw new IllegalArgumentException("Persisted target contains an unsupported death drop.");
			}
			if (dropDisposition(dropTemplate) == DropDisposition.ACQUIRE)
			{
				mutableItemIds.add(fact.itemId());
			}
			else
			{
				groundLossItemIds.add(fact.itemId());
			}
		}
		if (!ordinarySpoilSkillIds(player.getActiveClass()).isEmpty())
		{
			for (DropFact fact : knowledge.spoilFactsByNpc().getOrDefault(goal.npcId(), List.of()))
			{
				if (ItemData.getInstance().getTemplate(fact.itemId()) == null)
				{
					throw new IllegalArgumentException("Persisted target contains an unsupported spoil item.");
				}
				mutableItemIds.add(fact.itemId());
			}
		}
		if (groundLossItemIds.size() > PhantomBackgroundModel.MAX_GROUND_LOSS_ITEM_IDS)
		{
			throw new IllegalArgumentException("Exact farm projection has too many ground-loss item IDs.");
		}
		validateShot(player, goal, capability);
		validateSummonResource(player, goal, capability);
		if (goal.shotItemId() > 0)
		{
			mutableItemIds.add(goal.shotItemId());
		}
		if (goal.summonResourceItemId() > 0)
		{
			mutableItemIds.add(goal.summonResourceItemId());
		}
		if ((_travel != null) && !_travel.legs().isEmpty())
		{
			mutableItemIds.add(Inventory.ADENA_ID);
		}
		if (mutableItemIds.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS)
		{
			throw new IllegalArgumentException("Exact farm projection has too many mutable item IDs.");
		}

		return tracking(player, List.copyOf(mutableItemIds));
	}

	private Tracking tracking(Player player, List<Integer> mutableItemIds)
	{
		final Set<Integer> mutable = Set.copyOf(mutableItemIds);
		final List<ItemObject> objects = player.getInventory().getItems().stream()
			.filter(item -> ((item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY) && mutable.contains(item.getId())) || (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.PAPERDOLL))
			.sorted(Comparator.comparingInt(Item::getObjectId))
			.map(item -> new ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), ItemLocation.valueOf(item.getItemLocation().name())))
			.toList();
		return new Tracking(mutableItemIds, objects);
	}

	private void validateShot(Player player, PhantomBackgroundGoalSpec goal, Capability capability)
	{
		if (goal.shotItemId() == 0)
		{
			return;
		}
		final ItemTemplate template = ItemData.getInstance().getTemplate(goal.shotItemId());
		final EtcItem etcItem = template instanceof EtcItem item ? item : null;
		final var supply = _commerce.get().findSupply(goal.shotItemId());
		if ((etcItem == null) || (supply == null) || !supply.kinds().contains(SupplyKind.SHOT) || (ItemHandler.getInstance().getHandler(etcItem) == null))
		{
			throw new IllegalArgumentException("Configured shot is not an authoritative handled commerce supply.");
		}
		final ActionType action = template.getDefaultAction();
		final int expectedCount;
		if (capability.kind() == ModelKind.SUMMON_PRIMARY)
		{
			if ((action != ActionType.SUMMON_SOULSHOT) && (action != ActionType.SUMMON_SPIRITSHOT))
			{
				throw new IllegalArgumentException("Summon-primary background model requires an authoritative summon shot.");
			}
			expectedCount = action == ActionType.SUMMON_SOULSHOT ? capability.summon().getSoulShotsPerHit() : capability.summon().getSpiritShotsPerHit();
		}
		else
		{
			final Weapon weapon = player.getActiveWeaponItem();
			final ActionType expectedAction = capability.kind() == ModelKind.MAGIC ? ActionType.SPIRITSHOT : ActionType.SOULSHOT;
			if ((weapon == null) || (action != expectedAction) || (template.getCrystalType() != weapon.getCrystalTypePlus()))
			{
				throw new IllegalArgumentException("Configured shot type or grade does not match the captured model and weapon.");
			}
			expectedCount = expectedAction == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		}
		if ((expectedCount <= 0) || (expectedCount > 100) || (goal.shotsPerEncounter() != expectedCount) || (player.getInventory().getAllItemsByItemId(goal.shotItemId(), false).stream().mapToLong(Item::getCount).sum() < expectedCount))
		{
			throw new IllegalArgumentException("Configured shot count does not match the bounded per-encounter model contract.");
		}
	}

	private static void validateSummonResource(Player player, PhantomBackgroundGoalSpec goal, Capability capability)
	{
		if (capability.kind() != ModelKind.SUMMON_PRIMARY)
		{
			if ((goal.summonResourceItemId() != 0) || (goal.summonResourcesPerEncounter() != 0))
			{
				throw new IllegalArgumentException("Non-summon background model cannot consume summon resources.");
			}
			return;
		}
		final SummonActorFact fact = capability.summonFact();
		final int expectedItemId = fact.upkeepItemId();
		final int expectedCount = fact.upkeepItemCount();
		if ((goal.summonResourceItemId() != expectedItemId) || (goal.summonResourcesPerEncounter() != expectedCount))
		{
			throw new IllegalArgumentException("Summon resource does not match the authoritative progression fact.");
		}
		if ((expectedItemId > 0) && (player.getInventory().getAllItemsByItemId(expectedItemId, false).stream().mapToLong(Item::getCount).sum() < expectedCount))
		{
			throw new IllegalArgumentException("Authoritative summon resource reserve is absent.");
		}
	}

	private static CombatFacts combatFacts(Player player, Capability capability)
	{
		final boolean summonPrimary = capability.kind() == ModelKind.SUMMON_PRIMARY;
		final double physicalOffense = summonPrimary ? capability.summon().getPAtk(null) : player.getPAtk(null);
		final double magicOffense = summonPrimary ? capability.summon().getMAtk(null, null) : player.getMAtk(null, null);
		final double attackSpeed = summonPrimary ? capability.summon().getPAtkSpd() : player.getPAtkSpd();
		final double castSpeed = summonPrimary ? capability.summon().getMAtkSpd() : player.getMAtkSpd();
		return new CombatFacts(capability.kind(), physicalOffense, magicOffense, player.getPDef(null), player.getMDef(null, null), attackSpeed, castSpeed, Formulas.calcHpRegen(player) / 3d, Formulas.calcMpRegen(player) / 3d, player.getStat().getExpBonusMultiplier(), player.getStat().getSpBonusMultiplier(), capability.servitorExperienceMultiplier(), player.getStat().getBonusDropRateMultiplier(), player.getStat().getBonusDropAmountMultiplier(), player.getStat().getBonusDropAdenaMultiplier(), player.getStat().calcStat(Stat.REDUCE_EXP_LOST_BY_MOB, 1));
	}

	private List<Drop> drops(PhantomBackgroundState state, int targetLevel, List<DropFact> facts)
	{
		final List<Drop> result = new ArrayList<>();
		for (DropFact fact : facts)
		{
			result.add(drop(state, targetLevel, fact, DropOrigin.ORDINARY, false));
		}
		return List.copyOf(result);
	}

	private Drop drop(PhantomBackgroundState state, int targetLevel, DropFact fact, DropOrigin origin, boolean spoil)
	{
		final ItemTemplate item = ItemData.getInstance().getTemplate(fact.itemId());
		if (item == null)
		{
			throw new FarmInputRejected(FarmInputFailure.UNSUPPORTED_LOOT, "farm.item_absent");
		}
		final DropDisposition disposition = spoil ? DropDisposition.ACQUIRE : dropDisposition(item);
		final Float configuredChance = spoil ? null : RatesConfig.RATE_DROP_CHANCE_BY_ID.get(fact.itemId());
		double chance = spoil ? RatesConfig.RATE_SPOIL_DROP_CHANCE_MULTIPLIER : configuredChance == null ? (item.hasExImmediateEffect() ? RatesConfig.RATE_HERB_DROP_CHANCE_MULTIPLIER : RatesConfig.RATE_DEATH_DROP_CHANCE_MULTIPLIER) : configuredChance;
		if (!spoil && (configuredChance != null) && (fact.itemId() == Inventory.ADENA_ID) && (chance > 100))
		{
			chance = 100;
		}
		if (!spoil)
		{
			chance *= state.combat().dropChanceMultiplier();
		}
		double amount = spoil ? RatesConfig.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER : RatesConfig.RATE_DROP_AMOUNT_BY_ID.getOrDefault(fact.itemId(), item.hasExImmediateEffect() ? RatesConfig.RATE_HERB_DROP_AMOUNT_MULTIPLIER : RatesConfig.RATE_DEATH_DROP_AMOUNT_MULTIPLIER) * state.combat().dropAmountMultiplier();
		if (!spoil && (fact.itemId() == Inventory.ADENA_ID))
		{
			amount *= state.combat().adenaAmountMultiplier();
		}
		final int levelDifference = targetLevel - state.progress().level();
		final double levelGapChance = spoil ? 100d : MathUtil.scaleToRange(levelDifference, fact.itemId() == Inventory.ADENA_ID ? -RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE : -RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE, fact.itemId() == Inventory.ADENA_ID ? -RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE : -RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE, fact.itemId() == Inventory.ADENA_ID ? RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE : RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE, 100d);
		return new Drop(fact.itemId(), fact.groupOrdinal(), fact.itemOrdinal(), fact.rawGroupChance(), fact.rawItemChance(), fact.minimumCount(), fact.maximumCount(), chance, configuredChance == null ? null : configuredChance.doubleValue(), amount, levelGapChance, item.isStackable(), item.getWeight(), disposition, origin);
	}

	private boolean durableSpoilEligible(PhantomBackgroundState state, Source source, Map<Integer, Integer> learnedSkills)
	{
		final PhantomProgressionCatalog catalog = _progression.get();
		return durableCapability(catalog, state.identity().activeClassId(), "profession.spoil", source.spoilSkillId(), source.spoilSkillLevel(), learnedSkills) && durableCapability(catalog, state.identity().activeClassId(), "profession.sweep", source.sweepSkillId(), source.sweepSkillLevel(), learnedSkills);
	}

	private static boolean durableCapability(PhantomProgressionCatalog catalog, int classId, String key, int skillId, int skillLevel, Map<Integer, Integer> known)
	{
		return (known.getOrDefault(skillId, 0) == skillLevel) && catalog.capabilities(classId).stream().filter(rule -> key.equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == skillId) && (skillLevel >= rule.actionSkill().skillLevel()) && rule.requiredItems().isEmpty() && rule.requiredEquipmentFamilies().isEmpty()).anyMatch(rule -> rule.evidenceSkills().stream().allMatch(skill -> known.getOrDefault(skill.skillId(), 0) >= skill.skillLevel()));
	}

	private static DropDisposition dropDisposition(ItemTemplate item)
	{
		if (!item.hasExImmediateEffect() && (item.getTime() == -1))
		{
			return DropDisposition.ACQUIRE;
		}
		final boolean specificAutoLoot = PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(item.getId());
		final boolean autoLoot = specificAutoLoot || (!item.hasExImmediateEffect() && PlayerConfig.AUTO_LOOT) || (item.hasExImmediateEffect() && PlayerConfig.AUTO_LOOT_HERBS);
		if (autoLoot)
		{
			throw new FarmInputRejected(FarmInputFailure.UNSUPPORTED_LOOT, "farm.immediate_or_timed_autoloot");
		}
		return DropDisposition.LEAVE_ON_GROUND;
	}

	private static final class FarmInputRejected extends IllegalArgumentException
	{
		private final FarmInputFailure _failure;
		private FarmInputRejected(FarmInputFailure failure, String reason)
		{
			super(reason);
			_failure = failure;
		}
	}

	private static String compositeKnowledgeHash(String knowledgeHash)
	{
		return digest("BACKGROUND_KNOWLEDGE_V1", knowledgeHash, lootPolicyFingerprint());
	}

	private static String lootPolicyFingerprint()
	{
		final List<Integer> itemIds = Objects.requireNonNull(PlayerConfig.AUTO_LOOT_ITEM_IDS, "Auto-loot item IDs are not loaded.").stream().sorted().toList();
		final List<Object> facts = new ArrayList<>(itemIds.size() + 4);
		facts.add(LOOT_POLICY_VERSION);
		facts.add(PlayerConfig.AUTO_LOOT);
		facts.add(PlayerConfig.AUTO_LOOT_HERBS);
		facts.add(PlayerConfig.AUTO_LOOT_SLOT_LIMIT);
		facts.addAll(itemIds);
		return digest(facts.toArray());
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(value.toString().getBytes(StandardCharsets.US_ASCII));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static DeathPolicy deathPolicy(PhantomBackgroundState state)
	{
		return new DeathPolicy()
		{
			@Override
			public double lossPercent(int level)
			{
				return ExperienceLossData.getInstance().getPercentLost(level);
			}

			@Override
			public double normalMonsterReductionMultiplier()
			{
				return state.combat().normalMonsterExperienceLossMultiplier();
			}
		};
	}

	private static ExperienceTable experienceTable()
	{
		return new ExperienceTable()
		{
			@Override
			public long experienceForLevel(int level)
			{
				return ExperienceData.getInstance().getExpForLevel(level);
			}

			@Override
			public int maximumLevel()
			{
				return ExperienceData.getInstance().getMaxLevel();
			}
		};
	}

	private static LevelForExperience levelForExperience()
	{
		return experience ->
		{
			final ExperienceData data = ExperienceData.getInstance();
			int low = 1;
			int high = data.getMaxLevel();
			while (low < high)
			{
				final int middle = (low + high + 1) >>> 1;
				if (data.getExpForLevel(middle) <= experience)
				{
					low = middle;
				}
				else
				{
					high = middle - 1;
				}
			}
			return low;
		};
	}

	/** Durable lineage never substitutes an anchor's coordinates for the native Player position. */
	private PhantomTopologyAnchor nativeAnchor(Player player, PhantomGoal goal, PhantomBackgroundState previous)
	{
		final var topology = _topology.get();
		final var prior = previous == null ? null : topology.findAnchor(previous.position().committedAnchorId()).orElse(null);
		final var intended = goal.selectedAnchor() == null ? null : topology.findAnchor(goal.selectedAnchor().key()).orElse(null);
		if ((prior != null) && livePositionAllowed(topology, player, prior)) { return prior; }
		if ((intended != null) && livePositionAllowed(topology, player, intended)) { return intended; }
		if (prior != null) { return prior; }
		if (intended != null) { return intended; }
		throw new IllegalArgumentException("Native position has no proven previous or goal anchor.");
	}

	private PhantomTopologyAnchor exactAnchor(Player player, PhantomBackgroundState previous)
	{
		final PhantomTopologyQuery topology = _topology.get();
		if (previous != null)
		{
			final PhantomTopologyAnchor previousAnchor = topology.findAnchor(previous.position().committedAnchorId()).orElse(null);
			if ((previousAnchor != null) && (atAnchor(player, previousAnchor) || inFarmingArea(player.getX(), player.getY(), player.getZ(), player.getInstanceId(), previousAnchor)))
			{
				return previousAnchor;
			}
		}
		final List<PhantomTopologyAnchor> matches = topology.snapshot().anchors().stream().filter(anchor -> atAnchor(player, anchor)).sorted(Comparator.comparing(PhantomTopologyAnchor::id)).toList();
		if (matches.size() != 1)
		{
			throw new IllegalArgumentException("Canonical Player position does not identify exactly one topology anchor.");
		}
		return matches.getFirst();
	}

	private static boolean atAnchor(Player player, PhantomTopologyAnchor anchor)
	{
		final Optional<Position> canonical = canonicalCommittedAnchorPosition(anchor, player.getHeading());
		if (canonical.isEmpty() || (player.getInstanceId() != canonical.get().instanceId()))
		{
			return false;
		}
		return withinAnchorTolerance(player.getX(), player.getY(), player.getZ(), canonical.get(), anchor.validationTolerance());
	}

	private boolean atCanonicalAnchor(Position position, PhantomTopologyAnchor anchor)
	{
		final Optional<Position> canonical = canonicalCommittedAnchorPosition(anchor, position.heading());
		return canonical.isPresent() && (position.instanceId() == canonical.get().instanceId()) && position.committedAnchorId().equals(anchor.id()) && (withinAnchorTolerance(position.x(), position.y(), position.z(), canonical.get(), anchor.validationTolerance()) || inFarmingArea(position.x(), position.y(), position.z(), position.instanceId(), anchor));
	}

	private boolean inFarmingArea(int x, int y, int z, int instanceId, PhantomTopologyAnchor anchor)
	{
		return inFarmingArea(_topology.get(), x, y, z, instanceId, anchor);
	}

	public static boolean livePositionAllowed(PhantomTopologyQuery topology, Player player, PhantomTopologyAnchor anchor)
	{
		return atAnchor(player, anchor) || inFarmingArea(topology, player.getX(), player.getY(), player.getZ(), player.getInstanceId(), anchor);
	}

	private static boolean inFarmingArea(PhantomTopologyQuery topology, int x, int y, int z, int instanceId, PhantomTopologyAnchor anchor)
	{
		return (anchor.role() == PhantomTopologyAnchorRole.FARMING) && topology.findNode(anchor.nodeId()).map(node -> node.area().contains(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(x, y, z, instanceId))).orElse(false);
	}

	private static boolean withinAnchorTolerance(int x, int y, int z, Position canonical, int tolerance)
	{
		final long dx = (long) x - canonical.x();
		final long dy = (long) y - canonical.y();
		if ((Math.abs(dx) > tolerance) || (Math.abs(dy) > tolerance))
		{
			return false;
		}
		return ((dx * dx) + (dy * dy) <= ((long) tolerance * tolerance)) && (Math.abs((long) z - canonical.z()) <= tolerance);
	}

	private static void requireSupportedPlayer(Player player)
	{
		requireSupportedPlayer(player, false);
	}

    public static String configuredSimulationFingerprint()
    {
        return digest("ORDINARY_SCALAR_V2", PlayerConfig.ENABLE_VITALITY, PlayerConfig.MAX_BONUS_EXP,
            PlayerConfig.MAX_BONUS_SP, RatesConfig.RATE_VITALITY_GAIN, RatesConfig.RATE_VITALITY_LOST,
            RatesConfig.RATE_VITALITY_LEVEL_1, RatesConfig.RATE_VITALITY_LEVEL_2,
            RatesConfig.RATE_VITALITY_LEVEL_3, RatesConfig.RATE_VITALITY_LEVEL_4,
            RatesConfig.RATE_XP, RatesConfig.RATE_SP, RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE,
            DynamicExpRateData.getInstance().isEnabled());
    }
	private static PhantomNativeContext.Capture nativeContext(Player player)
	{
		final int points = player.getVitalityPoints();
		final int consume = (int) player.getStat().calcStat(Stat.VITALITY_CONSUME_RATE, 1, player, null);
		final boolean ordinaryPolicy = !PlayerConfig.ENABLE_VITALITY || ((consume == 1) && !player.getNevitSystem().isAdventBlessingActive());
        final boolean staticEffects = player.getEffectList().getEffects().stream().allMatch(info -> info.getSkill().isPassive());
        final boolean rewards = !player.hasPremiumStatus() && !player.isInParty() && !player.hasSummon()
            && !player.getNevitSystem().isAdventBlessingActive() && player.getNevitHourglassMultiplier() == 1 && staticEffects
            && !DynamicExpRateData.getInstance().isEnabled() && player.getStat().calcStat(Stat.EXPSP_RATE, 1, null, null) == 1;
        final var policy = new PhantomBackgroundSimulationPolicy(PhantomBackgroundSimulationPolicy.ALGORITHM_VERSION, player.getLevel(), points, points,
            rewards, false, player.getInstanceId() == 0, PlayerConfig.ENABLE_VITALITY, player.isLucky(), consume,
            RatesConfig.RATE_VITALITY_GAIN, RatesConfig.RATE_VITALITY_LOST,
            RatesConfig.RATE_VITALITY_LEVEL_1, RatesConfig.RATE_VITALITY_LEVEL_2,
            RatesConfig.RATE_VITALITY_LEVEL_3, RatesConfig.RATE_VITALITY_LEVEL_4,
            player.getStat().calcStat(Stat.BONUS_EXP, 0, null, null), player.getStat().calcStat(Stat.BONUS_SP, 0, null, null),
            PlayerConfig.MAX_BONUS_EXP, PlayerConfig.MAX_BONUS_SP, configuredSimulationFingerprint(), player.getRunSpeed(), 60000,
            (player.hasPremiumStatus() ? 1 : 0) | (player.isInParty() ? 2 : 0) | (player.hasSummon() ? 4 : 0)
            | (player.getNevitSystem().isAdventBlessingActive() || player.getNevitHourglassMultiplier() != 1 ? 8 : 0)
            | (!staticEffects ? 16 : 0) | (DynamicExpRateData.getInstance().isEnabled() ? 32 : 0)
            | (player.getStat().calcStat(Stat.EXPSP_RATE, 1, null, null) != 1 ? 64 : 0));
        return new PhantomNativeContext.Capture(points, (points == 1) && ordinaryPolicy && rewards
            ? PhantomNativeContext.Eligibility.SUPPORTED : PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE, policy);
	}

	@Override
	public PhantomNativeContext.Capture captureNativeContext(Player player)
	{
		requireSupportedPlayer(Objects.requireNonNull(player), true);
		return nativeContext(player);
	}

	@Override
	public PhantomNativeContext.Capture captureNativeContext(Player player, PhantomBackgroundState captured)
	{
		requireSupportedPlayer(Objects.requireNonNull(player), true);
		if (!matchesRuntime(player, captured)) { throw new IllegalArgumentException("Native context snapshot changed."); }
		final var topology = _topology.get();
		final var anchor = topology.findAnchor(captured.position().committedAnchorId()).orElseThrow(() -> new IllegalArgumentException("Native context anchor is absent."));
		final var vitality = nativeContext(player);
        final boolean farmPosition = livePositionAllowed(topology, player, anchor);
        return new PhantomNativeContext.Capture(vitality.vitalityPoints(), farmPosition ? vitality.eligibility()
            : PhantomNativeContext.Eligibility.POSITION_REQUIRES_NATIVE,
            vitality.policy().withPosition(farmPosition, captured.position().instanceId() == 0));
	}

	private static void requireSupportedPlayer(Player player, boolean nativePersistence)
	{
		final String reason;
		if (player.getInstanceId() != 0) { reason = "instance"; }
		else if (player.isFlying()) { reason = "flying"; }
		else if (player.isFlyingMounted()) { reason = "flyingMounted"; }
		else if (player.isMounted()) { reason = "mounted"; }
		else if (player.isInParty() && !nativePersistence) { reason = "party"; }
		else if (player.isInCombat()) { reason = "combat"; }
		else if (player.isCombatFlagEquipped()) { reason = "combatFlag"; }
		else if (player.isGM()) { reason = "gm"; }
		else if (player.hasPremiumStatus()) { reason = "premium"; }
		else if (player.isOnEvent()) { reason = "event"; }
		else if (player.isFestivalParticipant()) { reason = "festival"; }
		else if (player.getKarma() != 0) { reason = "karma"; }
		else
		{
			final double nevit = player.getNevitHourglassMultiplier();
			if (nevit != 1) { reason = "nevit=" + nevit; }
			else
			{
				if (nativePersistence) { return; }
				final double vitality = player.getStat().getVitalityMultiplier();
				if (vitality == 1) { return; }
				reason = "vitality=" + vitality;
			}
		}
		throw new IllegalArgumentException("Canonical Player is in an unsupported background context. firstPredicate=" + reason);
	}

	private static boolean supportedCapability(String key)
	{
		return "combat.melee_damage".equals(key) || "combat.ranged_physical_damage".equals(key) || "combat.ranged_magic_damage".equals(key);
	}

	private static TravelAdvance unchanged(Status status, PhantomBackgroundState state, String edgeId)
	{
		return new TravelAdvance(status, state.position(), state.clock(), edgeId);
	}

	private static boolean close(double left, double right)
	{
		return Math.abs(left - right) <= 0.000001d;
	}

	private record Capability(ModelKind kind, int skillId, int skillLevel, int mpConsume, int summonNpcId, double servitorExperienceMultiplier, Summon summon, SummonActorFact summonFact)
	{
	}

	private record Tracking(List<Integer> mutableItemIds, List<ItemObject> objects)
	{
	}

	public record ShotContract(ModelKind modelKind, int shotItemId, int shotsPerEncounter, int summonResourceItemId, int summonResourcesPerEncounter)
	{
	}
}
