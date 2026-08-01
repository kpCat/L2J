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
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.ItemHandler;
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
	private static final String LOOT_POLICY_VERSION = "LOOT_POLICY_V1";

	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;
	private final Supplier<PhantomTopologyQuery> _topology;
	private final Supplier<PhantomProgressionCatalog> _progression;
	private final Supplier<PhantomCommerceCatalog> _commerce;

	public L2jPhantomBackgroundAuthority(Supplier<PhantomGameKnowledgeQuery> knowledge, Supplier<PhantomTopologyQuery> topology, Supplier<PhantomProgressionCatalog> progression, Supplier<PhantomCommerceCatalog> commerce)
	{
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_progression = Objects.requireNonNull(progression, "progression");
		_commerce = Objects.requireNonNull(commerce, "commerce");
	}

	@Override
	public Hashes hashes()
	{
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyQuery topology = _topology.get();
		final PhantomProgressionCatalog progression = _progression.get();
		final PhantomCommerceCatalog commerce = _commerce.get();
		return new Hashes(compositeKnowledgeHash(knowledge.combinedHash()), topology.snapshot().canonicalHash(), progression.combinedHash(), commerce.hashes().combined());
	}

	@Override
	public PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous)
	{
		Objects.requireNonNull(player, "player");
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		requireSupportedPlayer(player);
		final PhantomTopologyAnchor anchor = exactAnchor(player, previous);
		final Capability capability = capability(player, spec);
		final Tracking tracking = tracking(player, spec, capability);
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
		Objects.requireNonNull(player, "player");
		final PhantomAcquisitionGoalSpec spec = PhantomAcquisitionGoalSpec.parse(goal);
		if (spec.itemId() != targetItemId)
		{
			throw new IllegalArgumentException("Acquisition background target item changed.");
		}
		requireSupportedPlayer(player);
		final PhantomTopologyAnchor anchor = exactAnchor(player, previous);
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
		if (!state.hashes().equals(hashes()))
		{
			throw new IllegalStateException("Background authority generation changed.");
		}
		final PhantomGameKnowledgeSnapshot knowledge = _knowledge.get().snapshot();
		final PhantomTopologyAnchor anchor = _topology.get().findAnchor(goal.anchorId()).orElseThrow(() -> new IllegalArgumentException("Persisted farm anchor is absent."));
		if ((anchor.point().instanceId() != 0) || !anchor.id().equals(state.position().committedAnchorId()) || !atCanonicalAnchor(state.position(), anchor))
		{
			throw new IllegalArgumentException("Background farm requires the exact committed instance-zero anchor.");
		}
		final var npc = knowledge.npcById().get(goal.npcId());
		final NpcTemplate template = NpcData.getInstance().getTemplate(goal.npcId());
		if ((npc == null) || (template == null) || (npc.kind() != NpcKind.MONSTER) || !npc.attackable() || !npc.targetable() || (npc.level() != template.getLevel()))
		{
			throw new IllegalArgumentException("Persisted target is not an authoritative normal monster.");
		}
		final List<SpawnAreaFact> areas = knowledge.spawnAreasByNpc().getOrDefault(goal.npcId(), List.of()).stream().filter(area -> (area.instanceId() == 0) && anchor.nodeId().equals(area.topologyNodeId())).toList();
		final long configuredAmount = areas.stream().mapToLong(SpawnAreaFact::totalConfiguredAmount).sum();
		if (configuredAmount <= 0)
		{
			throw new IllegalArgumentException("Persisted target has no authoritative spawn capacity at the farm anchor.");
		}
		final List<Drop> drops = drops(state, npc.level(), knowledge.dropFactsByNpc().getOrDefault(goal.npcId(), List.of()));
		final Target target = new Target(goal.npcId(), npc.level(), true, template.getBaseHpMax(), template.getBaseMpMax(), template.getBasePAtk(), template.getBaseMAtk(), template.getBasePDef(), template.getBaseMDef(), template.getBasePAtkSpd(), template.getBaseMAtkSpd(), npc.exp(), npc.sp(), drops, RatesConfig.DROP_MAX_OCCURRENCES_NORMAL);
		final double expRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicExpRate(state.progress().level()) : RatesConfig.RATE_XP;
		final double spRate = DynamicExpRateData.getInstance().isEnabled() ? DynamicExpRateData.getInstance().getDynamicSpRate(state.progress().level()) : RatesConfig.RATE_SP;
		return new FarmInput(target, new RewardPolicy(RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, expRate, spRate), deathPolicy(state), experienceTable(), levelForExperience(), anchor.nodeId(), (int) Math.clamp(configuredAmount, 1, 32));
	}

	@Override
	public FarmInput acquisitionInput(PhantomBackgroundState state, Source source)
	{
		if (!state.hashes().equals(hashes()) || (source.instanceId() != 0) || (source.itemId() <= 0) || ((source.method() != Method.DEATH_DROP) && (source.method() != Method.SPOIL_SWEEP)))
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
		final List<DropFact> selectedFacts = source.method() == Method.DEATH_DROP ? knowledge.dropFactsByNpc().getOrDefault(source.npcId(), List.of()) : knowledge.spoilFactsByNpc().getOrDefault(source.npcId(), List.of());
		final DropFact selected = selectedFacts.stream().filter(fact -> (fact.itemId() == source.itemId()) && (fact.sourceKind() == expectedKind) && fact.stableKey().equals(source.factKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("Acquisition source fact is stale."));
		if ((source.method() == Method.SPOIL_SWEEP) && !durableSpoilEligible(state, source))
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

	@Override
	public TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis)
	{
		return advanceTravel(state, goal.anchorId(), elapsedBudgetMillis);
	}

	@Override
	public TravelAdvance advanceAcquisitionTravel(PhantomBackgroundState state, Source source, long elapsedBudgetMillis)
	{
		return advanceTravel(state, source.anchorId(), elapsedBudgetMillis);
	}

	private TravelAdvance advanceTravel(PhantomBackgroundState state, String destinationAnchorId, long elapsedBudgetMillis)
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
		final PhantomTopologyQuery.RouteHint route = topology.routeHint(state.position().committedAnchorId(), destinationAnchorId).orElse(null);
		if ((route == null) || route.edgeIds().isEmpty())
		{
			return unchanged(Status.NO_ROUTE, state, "");
		}
		final String edgeId = route.edgeIds().getFirst();
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
		throw new IllegalArgumentException("No exact supported combat capability is currently ready.");
	}

	private Tracking tracking(Player player, PhantomBackgroundGoalSpec goal, Capability capability)
	{
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
		if (mutableItemIds.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS)
		{
			throw new IllegalArgumentException("Exact farm projection has too many mutable item IDs.");
		}

		final Set<Integer> mutable = Set.copyOf(mutableItemIds);
		final List<ItemObject> objects = player.getInventory().getItems().stream()
			.filter(item -> ((item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY) && mutable.contains(item.getId())) || (item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.PAPERDOLL))
			.sorted(Comparator.comparingInt(Item::getObjectId))
			.map(item -> new ItemObject(item.getObjectId(), item.getId(), item.getCount(), item.isStackable(), ItemLocation.valueOf(item.getItemLocation().name())))
			.toList();
		return new Tracking(List.copyOf(mutableItemIds), objects);
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
			throw new IllegalArgumentException("Background target contains an unsupported drop.");
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

	private boolean durableSpoilEligible(PhantomBackgroundState state, Source source)
	{
		final Map<Integer, Integer> known = new HashMap<>();
		state.autoGetSkills().forEach(skill -> known.merge(skill.skillId(), skill.skillLevel(), Math::max));
		final PhantomProgressionCatalog catalog = _progression.get();
		return durableCapability(catalog, state.identity().activeClassId(), "profession.spoil", source.spoilSkillId(), source.spoilSkillLevel(), known) && durableCapability(catalog, state.identity().activeClassId(), "profession.sweep", source.sweepSkillId(), source.sweepSkillLevel(), known);
	}

	private static boolean durableCapability(PhantomProgressionCatalog catalog, int classId, String key, int skillId, int skillLevel, Map<Integer, Integer> known)
	{
		return catalog.capabilities(classId).stream().filter(rule -> key.equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == skillId) && (rule.actionSkill().skillLevel() == skillLevel) && rule.requiredItems().isEmpty() && rule.requiredEquipmentFamilies().isEmpty()).anyMatch(rule -> rule.evidenceSkills().stream().allMatch(skill -> known.getOrDefault(skill.skillId(), 0) >= skill.skillLevel()));
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
			throw new IllegalArgumentException("Background target contains an auto-acquired immediate or time-limited death drop.");
		}
		return DropDisposition.LEAVE_ON_GROUND;
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

	private PhantomTopologyAnchor exactAnchor(Player player, PhantomBackgroundState previous)
	{
		final PhantomTopologyQuery topology = _topology.get();
		if (previous != null)
		{
			final PhantomTopologyAnchor previousAnchor = topology.findAnchor(previous.position().committedAnchorId()).orElse(null);
			if ((previousAnchor != null) && atAnchor(player, previousAnchor))
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

	private static boolean atCanonicalAnchor(Position position, PhantomTopologyAnchor anchor)
	{
		final Optional<Position> canonical = canonicalCommittedAnchorPosition(anchor, position.heading());
		return canonical.isPresent() && (position.instanceId() == canonical.get().instanceId()) && position.committedAnchorId().equals(anchor.id()) && withinAnchorTolerance(position.x(), position.y(), position.z(), canonical.get(), anchor.validationTolerance());
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
		if ((player.getInstanceId() != 0) || player.isFlying() || player.isFlyingMounted() || player.isMounted() || player.isInParty() || player.isInCombat() || player.isGM() || player.hasPremiumStatus() || player.isOnEvent() || player.isFestivalParticipant() || (player.getKarma() != 0) || (player.getNevitHourglassMultiplier() != 1) || (player.getStat().getVitalityMultiplier() != 1))
		{
			throw new IllegalArgumentException("Canonical Player is in an unsupported background context.");
		}
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
