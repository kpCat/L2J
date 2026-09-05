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
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Bounded deterministic target/anchor planner over immutable production facts. */
public final class PhantomHistoricalBackgroundPlanner
{
	private static final int LEVEL_RADIUS = 2;
	private static final int MAXIMUM_TARGETS = 64;
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomTopologyQuery _topology;
	private final PhantomBackgroundAuthority _authority;

	public PhantomHistoricalBackgroundPlanner(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomBackgroundAuthority authority)
	{
		_knowledge = Objects.requireNonNull(knowledge, "knowledge");
		_topology = Objects.requireNonNull(topology, "topology");
		_authority = Objects.requireNonNull(authority, "authority");
	}

	public PlanningGeneration generation()
	{
		return new PlanningGeneration(_knowledge.snapshot().generation(), _topology.snapshot().generation(), _authority.hashes());
	}

	public Result planInitial(long profileId, Player player, long deterministicSeed, long planOrdinal)
	{
		final PlanningSnapshot facts = _authority.planningSnapshot(player);
		return plan(profileId, facts.level(), facts.activeClassId(), facts.currentAnchorId(), facts.shotItemId(), facts.shotsPerEncounter(), facts.summonNpcId(), facts.summonResourceItemId(), facts.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, 0, 0);
	}

	public Result replan(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal)
	{
		final PhantomBackgroundGoalSpec previous = PhantomBackgroundGoalSpec.parse(previousGoal);
		return plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), previous.shotItemId(), previous.shotsPerEncounter(), previous.summonNpcId(), previous.summonResourceItemId(), previous.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1));
	}

	public boolean remainsSuitable(PhantomBackgroundState state, PhantomGoal goal)
	{
		if (!state.hashes().equals(_authority.hashes()))
		{
			return false;
		}
		final PhantomBackgroundGoalSpec spec;
		try
		{
			spec = PhantomBackgroundGoalSpec.parse(goal);
		}
		catch (RuntimeException exception)
		{
			return false;
		}
		final int minimum = Math.max(1, state.progress().level() - LEVEL_RADIUS);
		final int maximum = state.progress().level() + LEVEL_RADIUS;
		final TargetFact target = _knowledge.suitableTargets(new TargetQuery(minimum, maximum, state.progress().level(), null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(MAXIMUM_TARGETS))).values().stream().filter(value -> value.npc().npcId() == spec.npcId()).findFirst().orElse(null);
		return (target != null) && (candidate(state.position().committedAnchorId(), target, spec.anchorId()) != null);
	}

	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision)
	{
		if ((profileId <= 0) || (level < 1) || (activeClassId < 0) || (currentAnchorId == null) || currentAnchorId.isBlank() || (planOrdinal < 0) || (revision < 0))
		{
			return Result.blocked("planner.request.invalid");
		}
		final PlanningGeneration generation = generation();
		if (_topology.findAnchor(currentAnchorId).isEmpty())
		{
			return Result.blocked("planner.ingress.absent");
		}
		final int minimum = Math.max(1, level - LEVEL_RADIUS);
		final int maximum = level + LEVEL_RADIUS;
		final List<Candidate> candidates = new ArrayList<>();
		for (TargetFact target : _knowledge.suitableTargets(new TargetQuery(minimum, maximum, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(MAXIMUM_TARGETS))).values())
		{
			for (var area : target.representativeAreas().stream().filter(value -> (value.instanceId() == 0) && (value.totalConfiguredAmount() > 0) && (value.topologyNodeId() != null)).toList())
			{
				for (PhantomTopologyAnchor anchor : _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(value -> (value.role() == PhantomTopologyAnchorRole.FARMING) && (value.point().instanceId() == 0) && ((value.npcId() == null) || (value.npcId() == target.npc().npcId()))).toList())
				{
					final Candidate candidate = candidate(currentAnchorId, target, anchor.id());
					if (candidate != null)
					{
						candidates.add(candidate);
					}
				}
			}
		}
		if (candidates.isEmpty())
		{
			return Result.blocked("planner.target_or_route.absent");
		}
		candidates.sort(Comparator.comparingInt((Candidate value) -> Math.abs(value.target().npc().level() - level)).thenComparingInt(value -> value.routeEdgeIds().size()).thenComparing(value -> tieBreak(deterministicSeed, planOrdinal, value)).thenComparingInt(value -> value.target().npc().npcId()).thenComparing(value -> value.anchor().id()));
		final Candidate selected = candidates.getFirst();
		final Map<String, Long> constraints = new LinkedHashMap<>();
		putPositivePair(constraints, PhantomBackgroundGoalSpec.SHOT_ITEM, shotItemId, PhantomBackgroundGoalSpec.SHOT_COUNT, shotsPerEncounter);
		if (summonNpcId > 0)
		{
			constraints.put(PhantomBackgroundGoalSpec.SUMMON_NPC, (long) summonNpcId);
		}
		putPositivePair(constraints, PhantomBackgroundGoalSpec.SUMMON_RESOURCE_ITEM, summonResourceItemId, PhantomBackgroundGoalSpec.SUMMON_RESOURCE_COUNT, summonResourcesPerEncounter);
		final long goalId = previousGoalId > 0 ? previousGoalId : positiveLong(digest("BACKGROUND_CATCHUP_GOAL_V1", profileId, deterministicSeed, generation.knowledgeGeneration(), generation.topologyGeneration()));
		final int npcId = selected.target().npc().npcId();
		final String anchorId = selected.anchor().id();
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, constraints, "background.catchup.plan", revision);
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		final String identity = digest("BACKGROUND_CATCHUP_PLAN_V1", profileId, level, activeClassId, currentAnchorId, deterministicSeed, planOrdinal, generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes(), npcId, anchorId, selected.routeEdgeIds(), constraints);
		return new Result(goal, spec, identity, generation, selected.routeEdgeIds(), "planner.ready");
	}
	private Candidate candidate(String currentAnchorId, TargetFact target, String anchorId)
	{
		final PhantomTopologyAnchor anchor = _topology.findAnchor(anchorId).orElse(null);
		if ((anchor == null) || (anchor.role() != PhantomTopologyAnchorRole.FARMING) || (anchor.point().instanceId() != 0) || ((anchor.npcId() != null) && (anchor.npcId() != target.npc().npcId())) || target.representativeAreas().stream().noneMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId())))
		{
			return null;
		}
		final List<String> route = _topology.routeHint(currentAnchorId, anchorId).map(PhantomTopologyQuery.RouteHint::edgeIds).orElse(null);
		if ((route == null) || (!currentAnchorId.equals(anchorId) && route.isEmpty()))
		{
			return null;
		}
		String expectedAnchor = currentAnchorId;
		for (String edgeId : route)
		{
			final PhantomTopologyEdge edge = _topology.snapshot().edgeById().get(edgeId);
			if ((edge == null) || !edge.backgroundEligible() || (edge.mode() != PhantomTopologyEdgeMode.BACKGROUND) || !edge.fromAnchorId().equals(expectedAnchor) || !_topology.isTraversable(edgeId))
			{
				return null;
			}
			expectedAnchor = edge.toAnchorId();
		}
		return expectedAnchor.equals(anchorId) ? new Candidate(target, anchor, route) : null;
	}

	private static void putPositivePair(Map<String, Long> constraints, String itemKey, int itemId, String countKey, int count)
	{
		if ((itemId == 0) != (count == 0))
		{
			throw new IllegalArgumentException("Incomplete Background resource contract.");
		}
		if (itemId > 0)
		{
			constraints.put(itemKey, (long) itemId);
			constraints.put(countKey, (long) count);
		}
	}

	private static String tieBreak(long seed, long ordinal, Candidate candidate)
	{
		return digest("BACKGROUND_CATCHUP_RANK_V1", seed, ordinal, candidate.target().npc().npcId(), candidate.anchor().id());
	}

	private static long positiveLong(String hexDigest)
	{
		final long value = ByteBuffer.wrap(HexFormat.of().parseHex(hexDigest), 0, Long.BYTES).getLong() & Long.MAX_VALUE;
		return value == 0 ? 1 : value;
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

	private record Candidate(TargetFact target, PhantomTopologyAnchor anchor, List<String> routeEdgeIds)
	{
		private Candidate
		{
			routeEdgeIds = List.copyOf(routeEdgeIds);
		}
	}

	public record PlanningGeneration(long knowledgeGeneration, long topologyGeneration, PhantomBackgroundState.Hashes authorityHashes)
	{
		public PlanningGeneration
		{
			if ((knowledgeGeneration < 1) || (topologyGeneration < 1))
			{
				throw new IllegalArgumentException("Planning generations must be positive.");
			}
			Objects.requireNonNull(authorityHashes, "authorityHashes");
		}
	}

	public record Result(PhantomGoal goal, PhantomBackgroundGoalSpec spec, String planIdentity, PlanningGeneration generation, List<String> routeEdgeIds, String reasonKey)
	{
		public Result
		{
			planIdentity = Objects.requireNonNullElse(planIdentity, "");
			routeEdgeIds = List.copyOf(routeEdgeIds);
			reasonKey = Objects.requireNonNull(reasonKey, "reasonKey");
			if ((goal == null) != (spec == null) || ((goal == null) != planIdentity.isEmpty()))
			{
				throw new IllegalArgumentException("Planner result is partially populated.");
			}
		}

		public static Result blocked(String reasonKey)
		{
			return new Result(null, null, "", null, List.of(), reasonKey);
		}

		public boolean ready()
		{
			return goal != null;
		}
	}
}