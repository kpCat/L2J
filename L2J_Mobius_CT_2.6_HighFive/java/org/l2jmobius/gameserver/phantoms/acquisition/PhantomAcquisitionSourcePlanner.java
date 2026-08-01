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
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
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

	public PhantomAcquisitionSourcePlanner(PhantomAcquisitionCatalog catalog, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression)
	{
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_progression = Objects.requireNonNull(progression, "progression");
		_recipes = new PhantomAcquisitionRecipePlanner(knowledge, catalog.limits());
	}

	public Result plan(Request request)
	{
		Objects.requireNonNull(request, "request");
		if (_knowledge.findItem(request.itemId()).isEmpty())
		{
			return Result.blocked("source.ineligible");
		}
		final List<RankedSource> ranked = new ArrayList<>();
		if (request.allowedMethods().contains(Method.DEATH_DROP) && (_catalog.method(Method.DEATH_DROP).status() == MethodStatus.EXECUTABLE))
		{
			addDropPages(ranked, request, Method.DEATH_DROP, DropSourceKind.DEATH_DROP, null, null);
		}
		if (request.allowedMethods().contains(Method.SPOIL_SWEEP) && (_catalog.method(Method.SPOIL_SWEEP).status() == MethodStatus.EXECUTABLE))
		{
			final SkillRef spoil = capability(request, "profession.spoil");
			final SkillRef sweep = capability(request, "profession.sweep");
			if ((spoil != null) && (sweep != null))
			{
				addDropPages(ranked, request, Method.SPOIL_SWEEP, DropSourceKind.SPOIL, spoil, sweep);
			}
		}
		if (request.allowedMethods().contains(Method.RECIPE_PREPARATION) && (_catalog.method(Method.RECIPE_PREPARATION).status() == MethodStatus.PLANNING_ONLY))
		{
			final SkillRef craftSkill = capability(request, "profession.craft");
			final CraftEvidence craft = craftSkill == null ? new CraftEvidence(0, 0, false) : new CraftEvidence(craftSkill.skillId(), craftSkill.skillLevel(), true);
			final Map<Integer, Long> ingredientInventory = new HashMap<>(request.inventory());
			ingredientInventory.remove(request.itemId());
			final PhantomAcquisitionRecipePlanner.Result recipe = _recipes.plan(request.itemId(), request.remainingAmount(), ingredientInventory, craft);
			if (recipe.planned())
			{
				final String factKey = "recipe:" + recipe.plan().recipeListId() + ':' + request.itemId();
				final String sourceId = sourceId(Method.RECIPE_PREPARATION, 0, request.itemId(), factKey, "planning", "planning", 0, 0, 0, 0, 0);
				final Source source = new Source(sourceId, Method.RECIPE_PREPARATION, 0, request.itemId(), factKey, "planning", "planning", 0, 0, 0, 0, 0);
				ranked.add(new RankedSource(source, score(request, Method.RECIPE_PREPARATION, 0, 0, 0, sourceId), recipe.plan()));
			}
		}
		ranked.sort(RankedSource.ORDER);
		final List<RankedSource> bounded = ranked.stream().limit(_catalog.limits().sourceCandidates()).toList();
		if (bounded.isEmpty())
		{
			final boolean deferred = request.allowedMethods().stream().anyMatch(method -> _catalog.method(method).status() == MethodStatus.DEFERRED_CHECKPOINT_2);
			return deferred ? Result.deferredCheckpoint() : Result.blocked("source.exhausted");
		}
		if ((bounded.size() > 1) && ((long) bounded.getFirst().score() - bounded.get(1).score() <= _catalog.sourceScoring().ambiguityThreshold()) && (bounded.getFirst().source().method() == bounded.get(1).source().method()))
		{
			return new Result(List.copyOf(bounded), null, "source.ambiguous", false);
		}
		return new Result(List.copyOf(bounded), bounded.getFirst(), "source.ready", false);
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
				output.add(new RankedSource(source, score(request, method, _knowledge.findNpc(fact.npcId()).orElseThrow().level(), chanceUtility(fact), (int) Math.min(32, area.totalConfiguredAmount()), id), null));
				break;
			}
		}
	}

	private SkillRef capability(Request request, String key)
	{
		return _progression.capabilities(request.classId()).stream().filter(rule -> key.equals(rule.capabilityKey()) && exactSkillsKnown(rule, request.knownSkills())).sorted(Comparator.comparingInt(CapabilityRule::rank).reversed().thenComparing(CapabilityRule::stableKey)).map(CapabilityRule::actionSkill).findFirst().orElse(null);
	}

	private static boolean exactSkillsKnown(CapabilityRule rule, Map<Integer, Integer> known)
	{
		return rule.requiredEquipmentFamilies().isEmpty() && rule.requiredItems().isEmpty() && rule.evidenceSkills().stream().allMatch(skill -> known.getOrDefault(skill.skillId(), 0) >= skill.skillLevel());
	}

	private int score(Request request, Method method, int npcLevel, int chance, int capacity, String sourceId)
	{
		final var weights = _catalog.sourceScoring();
		long score = (long) _catalog.method(method).preference() * weights.methodPreference();
		if (request.preferredMethod() == method)
		{
			score += weights.methodPreference() * 1000L;
		}
		if (request.currentAnchorId().equals(""))
		{
			score -= weights.topologyCost();
		}
		score -= (long) Math.abs(request.level() - npcLevel) * weights.levelGap();
		score += (long) chance * weights.chanceUtility();
		score += (long) capacity * weights.spawnCapacity();
		final Candidate previous = request.previousCandidates().get(sourceId);
		if (previous != null)
		{
			score -= (long) previous.failures() * weights.failurePenalty();
		}
		return (int) Math.clamp(score, Integer.MIN_VALUE + 1L, Integer.MAX_VALUE);
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

	public record Request(long profileId, int itemId, long remainingAmount, PhantomActivityState activityState, int classId, int level, Map<Integer, Long> inventory, Map<Integer, Integer> knownSkills, Set<Method> allowedMethods, Method preferredMethod, String currentAnchorId, Map<String, Candidate> previousCandidates, long logicalMinute)
	{
		public Request
		{
			inventory = Map.copyOf(inventory);
			knownSkills = Map.copyOf(knownSkills);
			allowedMethods = Set.copyOf(allowedMethods);
			currentAnchorId = Objects.requireNonNullElse(currentAnchorId, "");
			previousCandidates = Map.copyOf(previousCandidates);
			if ((profileId <= 0) || (itemId <= 0) || (remainingAmount <= 0) || (activityState == null) || (classId < 0) || (level < 1) || allowedMethods.isEmpty() || (logicalMinute < 0))
			{
				throw new IllegalArgumentException("Invalid acquisition source planning request.");
			}
		}
	}

	public record RankedSource(Source source, int score, RecipePlan recipePlan)
	{
		private static final Comparator<RankedSource> ORDER = Comparator.comparingInt(RankedSource::score).reversed().thenComparing(value -> value.source().sourceId());

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
