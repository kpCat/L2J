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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.MethodStatus;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionRecipePlanner.CraftEvidence;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.MethodBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.acquisition.manor.PhantomAcquisitionManorAuthority;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaSummary;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Indexed, read-only source planning over immutable knowledge generations. */
public final class PhantomAcquisitionSourcePlanner
{
	private final PhantomAcquisitionCatalog _catalog;
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomTopologyQuery _topology;
	private final PhantomProgressionCatalog _progression;
	private final PhantomAcquisitionRecipePlanner _recipes;
	private final PhantomAcquisitionManorAuthority _manor;
	private final PhantomAcquisitionQuestCatalog _quests;

	public PhantomAcquisitionSourcePlanner(PhantomAcquisitionCatalog catalog, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression)
	{
		this(catalog, knowledge, topology, progression, null, null);
	}

	public PhantomAcquisitionSourcePlanner(PhantomAcquisitionCatalog catalog, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression, PhantomAcquisitionManorAuthority manor, PhantomAcquisitionQuestCatalog quests)
	{
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_progression = Objects.requireNonNull(progression, "progression");
		_recipes = new PhantomAcquisitionRecipePlanner(knowledge, catalog.limits());
		_manor = manor;
		_quests = quests;
	}

	public Result plan(Request request)
	{
		Objects.requireNonNull(request, "request");
		if (_knowledge.findItem(request.itemId()).isEmpty())
		{
			return Result.blocked("source.ineligible");
		}
		final List<RankedSource> ranked = new ArrayList<>();
		String recipeReason = "";
		String methodReason = "";
		if (request.allowedMethods().contains(Method.DEATH_DROP) && (_catalog.method(Method.DEATH_DROP).status() == MethodStatus.EXECUTABLE))
		{
			addDropPages(ranked, request, Method.DEATH_DROP, DropSourceKind.DEATH_DROP, null, null);
		}
		if (request.allowedMethods().contains(Method.SPOIL_SWEEP) && (_catalog.method(Method.SPOIL_SWEEP).status() == MethodStatus.EXECUTABLE))
		{
			final SkillRef spoil = capability(request, "profession.spoil", 254);
			final SkillRef sweep = capability(request, "profession.sweep", 42);
			if ((spoil != null) && (sweep != null))
			{
				addDropPages(ranked, request, Method.SPOIL_SWEEP, DropSourceKind.SPOIL, spoil, sweep);
			}
		}
		if (request.allowedMethods().contains(Method.RECIPE_PREPARATION) && (_catalog.method(Method.RECIPE_PREPARATION).status() == MethodStatus.PLANNING_ONLY))
		{
			final SkillRef craftSkill = capability(request, "profession.craft", 172);
			final CraftEvidence craft = craftSkill == null ? new CraftEvidence(0, 0, false) : new CraftEvidence(craftSkill.skillId(), craftSkill.skillLevel(), true);
			final Map<Integer, Long> ingredientInventory = new HashMap<>(request.inventory());
			ingredientInventory.remove(request.itemId());
			final PhantomAcquisitionRecipePlanner.Result recipe = _recipes.plan(request.itemId(), request.remainingAmount(), ingredientInventory, craft);
			if (recipe.planned())
			{
				final String factKey = "recipe:" + recipe.plan().recipeListId() + ':' + request.itemId();
				final String sourceId = sourceId(Method.RECIPE_PREPARATION, 0, request.itemId(), factKey, "planning", "planning", 0, 0, 0, 0, 0);
				final Source source = new Source(sourceId, Method.RECIPE_PREPARATION, 0, request.itemId(), factKey, "planning", "planning", 0, 0, 0, 0, 0);
				ranked.add(new RankedSource(source, score(request, Method.RECIPE_PREPARATION, 0, 0, 0, sourceId, source.anchorId(), recipe.plan()), recipe.plan(), null));
			}
			else
			{
				recipeReason = recipe.reasonKey();
			}
		}
		if (request.allowedMethods().contains(Method.MANOR_CROP) && (_catalog.method(Method.MANOR_CROP).status() == MethodStatus.EXECUTABLE))
		{
			if (_manor == null)
			{
				methodReason = "source.ineligible";
			}
			else
			{
				final var manor = _manor.candidates(request.itemId(), request.level(), request.inventory());
				methodReason = manor.reasonKey();
				for (var candidate : manor.candidates())
				{
					if (!retryEligible(request, candidate.sourceId()))
					{
						continue;
					}
					final var fact = candidate.fact();
					final Source source = new Source(candidate.sourceId(), Method.MANOR_CROP, candidate.npcId(), fact.cropItemId(), "manor:" + fact.stableKey(), candidate.topologyNodeId(), candidate.anchorId(), 0, 0, 0, 0, 0);
					final MethodBinding binding = _manor.binding(candidate, request.inventory().getOrDefault(fact.seedItemId(), 0L), request.inventory().getOrDefault(fact.cropItemId(), 0L));
					ranked.add(new RankedSource(source, score(request, Method.MANOR_CROP, candidate.npcLevel(), candidate.sowChance(), Math.max(1, candidate.harvestPayload()), source.sourceId(), source.anchorId(), null), null, binding));
				}
			}
		}
		if (request.allowedMethods().contains(Method.QUEST_COLLECTION) && (_catalog.method(Method.QUEST_COLLECTION).status() == MethodStatus.EXECUTABLE))
		{
			methodReason = addQuestSources(ranked, request, methodReason);
		}
		ranked.sort(RankedSource.ORDER);
		final List<RankedSource> bounded = ranked.stream().limit(_catalog.limits().sourceCandidates()).toList();
		if (bounded.isEmpty())
		{
			final boolean deferred = request.allowedMethods().stream().anyMatch(method -> _catalog.method(method).status() == MethodStatus.DEFERRED_CHECKPOINT_2);
			final String reason = !methodReason.isEmpty() ? methodReason : !recipeReason.isEmpty() ? recipeReason : "source.exhausted";
			return deferred ? Result.deferredCheckpoint() : Result.blocked(reason);
		}
		if ((bounded.size() > 1) && ((long) bounded.getFirst().score() - bounded.get(1).score() <= _catalog.sourceScoring().ambiguityThreshold()))
		{
			return new Result(List.copyOf(bounded), null, "source.ambiguous", false);
		}
		return new Result(List.copyOf(bounded), bounded.getFirst(), "source.ready", false);
	}

	public PhantomAcquisitionRecipePlanner.Probe probeRecipeInventory(int itemId, long requested)
	{
		return _recipes.probe(itemId, requested);
	}

	public List<Integer> probeManorInventory(int itemId)
	{
		return _manor == null ? List.of() : _manor.probe(itemId).requiredItemIds();
	}

	private void addDropPages(List<RankedSource> output, Request request, Method method, DropSourceKind expectedKind, SkillRef spoil, SkillRef sweep)
	{
		String cursor = null;
		for (int page = 0; (page < _catalog.limits().operationsPerStep()) && (output.size() < _catalog.limits().sourceCandidates()); page++)
		{
			final var facts = expectedKind == DropSourceKind.DEATH_DROP ? _knowledge.dropSources(request.itemId(), new PageRequest(_catalog.limits().sourceCandidates(), cursor)) : _knowledge.spoilSources(request.itemId(), new PageRequest(_catalog.limits().sourceCandidates(), cursor));
			addDrops(output, request, method, facts.values(), expectedKind, spoil, sweep);
			if (!facts.hasMore())
			{
				break;
			}
			cursor = facts.nextCursor();
		}
	}

	private void addDrops(List<RankedSource> output, Request request, Method method, List<DropFact> facts, DropSourceKind expectedKind, SkillRef spoil, SkillRef sweep)
	{
		for (DropFact fact : facts)
		{
			if ((output.size() >= (_catalog.limits().sourceCandidates() * 2)) || (fact.sourceKind() != expectedKind) || !validChance(fact) || (_knowledge.findNpc(fact.npcId()).filter(npc -> (npc.kind() == NpcKind.MONSTER) && npc.attackable() && npc.targetable()).isEmpty()))
			{
				continue;
			}
			final var areasPage = _knowledge.spawnAreas(fact.npcId(), new PageRequest(_catalog.limits().areasPerSource(), null));
			for (SpawnAreaSummary area : areasPage.values().stream().filter(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && (area.topologyNodeId() != null)).sorted(Comparator.comparing(SpawnAreaSummary::stableKey)).toList())
			{
				final PhantomTopologyAnchor anchor = _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(value -> value.point().instanceId() == 0).min(Comparator.comparing(PhantomTopologyAnchor::id)).orElse(null);
				if ((anchor == null) || _topology.findNode(area.topologyNodeId()).filter(node -> node.instanceId() == 0).isEmpty())
				{
					continue;
				}
				final int spoilId = spoil == null ? 0 : spoil.skillId();
				final int spoilLevel = spoil == null ? 0 : spoil.skillLevel();
				final int sweepId = sweep == null ? 0 : sweep.skillId();
				final int sweepLevel = sweep == null ? 0 : sweep.skillLevel();
				final String id = sourceId(method, fact.npcId(), fact.itemId(), fact.stableKey(), area.topologyNodeId(), anchor.id(), area.instanceId(), spoilId, spoilLevel, sweepId, sweepLevel);
				final Candidate previous = request.previousCandidates().get(id);
				if ((previous != null) && (previous.failures() >= _catalog.switchPolicy().failureThreshold()) && ((request.logicalMinute() - previous.lastFailureMinute()) < _catalog.switchPolicy().cooldownMinutes()))
				{
					continue;
				}
				final Source source = new Source(id, method, fact.npcId(), fact.itemId(), fact.stableKey(), area.topologyNodeId(), anchor.id(), area.instanceId(), spoilId, spoilLevel, sweepId, sweepLevel);
				output.add(new RankedSource(source, score(request, method, _knowledge.findNpc(fact.npcId()).orElseThrow().level(), chanceUtility(fact), (int) Math.min(32, area.totalConfiguredAmount()), id, anchor.id(), null), null, null));
				break;
			}
		}
	}

	private String addQuestSources(List<RankedSource> output, Request request, String previousReason)
	{
		if ((_quests == null) || !_quests.current())
		{
			return "quest.script_stale";
		}
		String reason = previousReason;
		for (Rule rule : _quests.rulesForItem(request.itemId()))
		{
			final QuestEvidence evidence = request.questEvidence().get(rule.id());
			if (evidence == null || !"STARTED".equals(evidence.state()))
			{
				reason = "quest.not_started";
				continue;
			}
			if (!rule.allowedConds().contains(evidence.cond()) || !evidence.variables().keySet().equals(Set.copyOf(rule.expectedVars())))
			{
				reason = "quest.cond_ineligible";
				continue;
			}
			if (evidence.itemCount() >= rule.itemCap())
			{
				reason = "quest.item_cap";
				continue;
			}
			for (int npcId : rule.targetNpcIds())
			{
				final var npc = _knowledge.findNpc(npcId).filter(value -> (value.kind() == NpcKind.MONSTER) && value.attackable() && value.targetable()).orElse(null);
				if (npc == null)
				{
					reason = "quest.target_unavailable";
					continue;
				}
				final var areas = _knowledge.spawnAreas(npcId, PageRequest.first(_catalog.limits().areasPerSource()));
				for (SpawnAreaSummary area : areas.values().stream().filter(value -> (value.instanceId() == 0) && (value.totalConfiguredAmount() > 0) && (value.topologyNodeId() != null)).sorted(Comparator.comparing(SpawnAreaSummary::stableKey)).toList())
				{
					final PhantomTopologyAnchor anchor = _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(value -> value.point().instanceId() == 0).min(Comparator.comparing(PhantomTopologyAnchor::id)).orElse(null);
					if (anchor == null)
					{
						continue;
					}
					final String sourceId = digest("QUEST_COLLECTION_V1", rule.ruleHash(), rule.scriptHash(), rule.questId(), rule.questName(), npcId, rule.questItemId(), area.topologyNodeId(), anchor.id(), _quests.authorityHash(), _knowledge.snapshot().combinedHash(), _topology.snapshot().canonicalHash());
					if (!retryEligible(request, sourceId))
					{
						continue;
					}
					final Source source = new Source(sourceId, Method.QUEST_COLLECTION, npcId, rule.questItemId(), "quest:" + rule.id(), area.topologyNodeId(), anchor.id(), 0, 0, 0, 0, 0);
					final QuestBinding binding = new QuestBinding(rule.id(), rule.ruleHash(), rule.questId(), rule.questName(), rule.scriptHash(), rule.requiredState(), evidence.cond(), rule.questItemId(), rule.itemCap(), npcId, evidence.itemCount(), 0, _quests.authorityHash());
					final int chance = rule.chanceKind() == PhantomAcquisitionQuestCatalog.ChanceKind.NONE ? 100 : Math.max(1, (rule.rollThreshold() * 100) / rule.rollBound());
					output.add(new RankedSource(source, score(request, Method.QUEST_COLLECTION, npc.level(), chance, (int) Math.min(32, area.totalConfiguredAmount()), sourceId, anchor.id(), null), null, binding));
					break;
				}
			}
		}
		return output.stream().anyMatch(value -> value.source().method() == Method.QUEST_COLLECTION) ? "" : reason.isEmpty() ? "quest.rule_unsupported" : reason;
	}


	private boolean retryEligible(Request request, String sourceId)
	{
		final Candidate previous = request.previousCandidates().get(sourceId);
		return (previous == null) || (previous.failures() < _catalog.switchPolicy().failureThreshold()) || ((request.logicalMinute() - previous.lastFailureMinute()) >= _catalog.switchPolicy().cooldownMinutes());
	}

	private SkillRef capability(Request request, String key, int canonicalSkillId)
	{
		return _progression.capabilities(request.classId()).stream().filter(rule -> key.equals(rule.capabilityKey()) && (rule.actionSkill().skillId() == canonicalSkillId) && exactSkillsKnown(rule, request.knownSkills())).sorted(Comparator.comparingInt(CapabilityRule::rank).reversed().thenComparing(CapabilityRule::stableKey)).map(rule -> new SkillRef(rule.actionSkill().skillId(), request.knownSkills().get(rule.actionSkill().skillId()))).findFirst().orElse(null);
	}

	private static boolean exactSkillsKnown(CapabilityRule rule, Map<Integer, Integer> known)
	{
		return rule.requiredEquipmentFamilies().isEmpty() && rule.requiredItems().isEmpty() && (known.getOrDefault(rule.actionSkill().skillId(), 0) >= rule.actionSkill().skillLevel()) && rule.evidenceSkills().stream().allMatch(skill -> known.getOrDefault(skill.skillId(), 0) >= skill.skillLevel());
	}

	private int score(Request request, Method method, int npcLevel, int chance, int capacity, String sourceId, String sourceAnchorId, RecipePlan recipePlan)
	{
		final var weights = _catalog.sourceScoring();
		long score = (long) _catalog.method(method).preference() * weights.methodPreference();
		if (request.preferredMethod() == method)
		{
			score += weights.preferredMethodBonus();
		}
		score -= (long) topologyCost(request.currentAnchorId(), sourceAnchorId, method) * weights.topologyCost();
		score -= (long) Math.abs(request.level() - npcLevel) * weights.levelGap();
		score += (long) chance * weights.chanceUtility();
		score += (long) capacity * weights.spawnCapacity();
		score -= (long) request.resources().pressurePermille() * weights.resourceReserve();
		if (!request.currentSourceId().isEmpty() && !request.currentSourceId().equals(sourceId))
		{
			score -= weights.switchPenalty();
		}
		if (recipePlan != null)
		{
			final long reused = recipePlan.nodes().stream().mapToLong(PhantomAcquisitionState.RecipeNode::inventoryUsed).reduce(0, (left, right) -> (left >= 1000) || (right >= 1000) ? 1000 : left + right);
			score += reused * weights.recipeLeafReuse();
		}
		final Candidate previous = request.previousCandidates().get(sourceId);
		if (previous != null)
		{
			score -= (long) previous.failures() * weights.failurePenalty();
		}
		return (int) Math.clamp(score, Integer.MIN_VALUE + 1L, Integer.MAX_VALUE);
	}

	private int topologyCost(String currentAnchorId, String sourceAnchorId, Method method)
	{
		if (method == Method.RECIPE_PREPARATION)
		{
			return 0;
		}
		if (currentAnchorId.isEmpty() || sourceAnchorId.isEmpty())
		{
			return 1000;
		}
		if (currentAnchorId.equals(sourceAnchorId))
		{
			return 0;
		}
		final PhantomTopologyAnchor current = _topology.findAnchor(currentAnchorId).orElse(null);
		final PhantomTopologyAnchor source = _topology.findAnchor(sourceAnchorId).orElse(null);
		return (current != null) && (source != null) && current.nodeId().equals(source.nodeId()) ? 1 : 1000;
	}

	private String sourceId(Method method, int npcId, int itemId, String factKey, String nodeId, String anchorId, int instanceId, int spoilId, int spoilLevel, int sweepId, int sweepLevel)
	{
		return digest("ACQUISITION_SOURCE_V1", method.key(), npcId, itemId, factKey, nodeId, anchorId, instanceId, spoilId, spoilLevel, sweepId, sweepLevel, _knowledge.snapshot().combinedHash(), _topology.snapshot().canonicalHash(), method == Method.SPOIL_SWEEP ? _progression.combinedHash() : "none");
	}

	private static int chanceUtility(DropFact fact)
	{
		final double chance = fact.chanceModel() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel.GROUP_CUMULATIVE ? Math.min(100d, fact.rawGroupChance()) * Math.min(100d, fact.rawItemChance()) / 100d : Math.min(100d, fact.rawItemChance());
		return (int) Math.clamp(Math.round(chance * Math.max(1, fact.minimumCount())), 1, 10000);
	}

	private static boolean validChance(DropFact fact)
	{
		return Double.isFinite(fact.rawGroupChance()) && Double.isFinite(fact.rawItemChance()) && (fact.rawItemChance() > 0) && ((fact.chanceModel() != org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ChanceModel.GROUP_CUMULATIVE) || (fact.rawGroupChance() > 0)) && (fact.minimumCount() > 0) && (fact.maximumCount() >= fact.minimumCount());
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public record Request(long profileId, int itemId, long remainingAmount, PhantomActivityState activityState, int classId, int level, Map<Integer, Long> inventory, Map<Integer, Integer> knownSkills, Set<Method> allowedMethods, Method preferredMethod, String currentAnchorId, String currentSourceId, ResourceEvidence resources, Map<String, Candidate> previousCandidates, long logicalMinute, Map<String, QuestEvidence> questEvidence)
	{
		public Request(long profileId, int itemId, long remainingAmount, PhantomActivityState activityState, int classId, int level, Map<Integer, Long> inventory, Map<Integer, Integer> knownSkills, Set<Method> allowedMethods, Method preferredMethod, String currentAnchorId, String currentSourceId, ResourceEvidence resources, Map<String, Candidate> previousCandidates, long logicalMinute)
		{
			this(profileId, itemId, remainingAmount, activityState, classId, level, inventory, knownSkills, allowedMethods, preferredMethod, currentAnchorId, currentSourceId, resources, previousCandidates, logicalMinute, Map.of());
		}

		public Request(long profileId, int itemId, long remainingAmount, PhantomActivityState activityState, int classId, int level, Map<Integer, Long> inventory, Map<Integer, Integer> knownSkills, Set<Method> allowedMethods, Method preferredMethod, String currentAnchorId, Map<String, Candidate> previousCandidates, long logicalMinute)
		{
			this(profileId, itemId, remainingAmount, activityState, classId, level, inventory, knownSkills, allowedMethods, preferredMethod, currentAnchorId, "", ResourceEvidence.unavailable(), previousCandidates, logicalMinute, Map.of());
		}

		public Request
		{
			inventory = Map.copyOf(inventory);
			knownSkills = Map.copyOf(knownSkills);
			allowedMethods = Set.copyOf(allowedMethods);
			currentAnchorId = Objects.requireNonNullElse(currentAnchorId, "");
			currentSourceId = Objects.requireNonNullElse(currentSourceId, "");
			Objects.requireNonNull(resources, "resources");
			previousCandidates = Map.copyOf(previousCandidates);
			questEvidence = Map.copyOf(questEvidence);
			if ((profileId <= 0) || (itemId <= 0) || (remainingAmount <= 0) || (activityState == null) || (classId < 0) || (level < 1) || allowedMethods.isEmpty() || (logicalMinute < 0))
			{
				throw new IllegalArgumentException("Invalid acquisition source planning request.");
			}
		}
	}

	public record QuestEvidence(String state, int cond, Map<String, String> variables, long itemCount)
	{
		public QuestEvidence
		{
			state = Objects.requireNonNullElse(state, "");
			variables = Map.copyOf(variables);
			if ((state.length() > 16) || (cond < 0) || (cond > 255) || (variables.size() > 4) || (itemCount < 0))
			{
				throw new IllegalArgumentException("Invalid quest acquisition evidence.");
			}
		}
	}

	public record ResourceEvidence(long currentLoad, long maximumLoad, int usedSlots, int maximumSlots, boolean available)
	{
		public ResourceEvidence
		{
			if ((currentLoad < 0) || (maximumLoad < 0) || (usedSlots < 0) || (maximumSlots < 0) || (available && ((maximumLoad < 1) || (maximumSlots < 1) || (currentLoad > maximumLoad) || (usedSlots > maximumSlots))))
			{
				throw new IllegalArgumentException("Invalid acquisition resource evidence.");
			}
		}

		public static ResourceEvidence unavailable()
		{
			return new ResourceEvidence(0, 0, 0, 0, false);
		}

		private int pressurePermille()
		{
			if (!available)
			{
				return 1000;
			}
			return (int) Math.max(Math.floor((double) currentLoad * 1000d / maximumLoad), ((long) usedSlots * 1000) / maximumSlots);
		}
	}

	public record RankedSource(Source source, int score, RecipePlan recipePlan, MethodBinding methodBinding)
	{
		private static final Comparator<RankedSource> ORDER = Comparator.comparingInt(RankedSource::score).reversed().thenComparing(value -> value.source().sourceId());

		public RankedSource(Source source, int score, RecipePlan recipePlan)
		{
			this(source, score, recipePlan, null);
		}

		public Candidate candidate()
		{
			return new Candidate(source.sourceId(), source.method(), score, 0, 0, "");
		}
	}

	public record Result(List<RankedSource> ranked, RankedSource selected, String reasonKey, boolean deferred)
	{
		public Result
		{
			ranked = List.copyOf(ranked);
			Objects.requireNonNull(reasonKey, "reasonKey");
		}

		public static Result blocked(String reason)
		{
			return new Result(List.of(), null, reason, false);
		}

		public static Result deferredCheckpoint()
		{
			return new Result(List.of(), null, "source.deferred_checkpoint_2", true);
		}

		public boolean ready()
		{
			return selected != null;
		}
	}
}
