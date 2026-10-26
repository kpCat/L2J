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
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningCombat;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Bounded deterministic target/anchor planner over immutable production facts. */
public final class PhantomHistoricalBackgroundPlanner
{
	private static final int LEVEL_RADIUS = 2;
	private static final List<Integer> LOWER_LEVEL_TIERS = List.of(2, 5, 10);
	private static final int MAXIMUM_TARGETS = 64;
	private static final int FALLBACK_TARGETS = 256;
	private static final int MAXIMUM_TARGET_PAGES = 8;
	private static final int MAXIMUM_OFFSCREEN_NATIVE_PROOFS = 256;
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
		return plan(profileId, facts.level(), facts.activeClassId(), facts.currentAnchorId(), facts.shotItemId(), facts.shotsPerEncounter(), facts.summonNpcId(), facts.summonResourceItemId(), facts.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, 0, 0, Set.of(), Set.of(), null, _authority.planningCombat(player).orElse(null),
			(facts.shotItemId() == 0 || player.getInventory().getInventoryItemCount(facts.shotItemId(), -1) >= facts.shotsPerEncounter())
			&& (facts.summonResourceItemId() == 0 || player.getInventory().getInventoryItemCount(facts.summonResourceItemId(), -1) >= facts.summonResourcesPerEncounter()));
	}

	public Result replan(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal)
	{
		return replan(profileId, state, previousGoal, deterministicSeed, planOrdinal, Set.of(), Set.of());
	}

	public Result replan(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal, Set<String> excludedTargets, Set<String> excludedSteps)
	{
		final PhantomBackgroundGoalSpec previous = PhantomBackgroundGoalSpec.parse(previousGoal);
		return plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), previous.shotItemId(), previous.shotsPerEncounter(), previous.summonNpcId(), previous.summonResourceItemId(), previous.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), excludedTargets, excludedSteps, null, PlanningCombat.from(state), offscreenSupplied(state));
	}

	public Result replaceFromState(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(previousGoal, "previousGoal");
		if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(previousGoal.goalType()) && !PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(previousGoal.goalType()))
		{
			return Result.blocked("planner.previous_goal.unsupported");
		}
		final PhantomBackgroundState.Loadout loadout = state.loadout();
		return plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), loadout.shotItemId(), loadout.shotsPerEncounter(), loadout.summonNpcId(), loadout.summonResourceItemId(), loadout.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), Set.of(), Set.of(), null, PlanningCombat.from(state), offscreenSupplied(state));
	}

	private static boolean offscreenSupplied(PhantomBackgroundState state)
	{
		final var loadout = state.loadout();
		return (loadout.shotItemId() == 0 || state.inventory().itemCount(loadout.shotItemId()) >= loadout.shotsPerEncounter())
			&& (loadout.summonResourceItemId() == 0 || state.inventory().itemCount(loadout.summonResourceItemId()) >= loadout.summonResourcesPerEncounter());
	}

	/** Prefer factual local anchors; exhaustion may propose an existing topology itinerary, never movement. */
	public Result replanVisibleLocal(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal, PhantomTopologyPoint live, Set<String> excludedTargets, Set<String> excludedSteps)
	{
		final var previous = visiblePredecessor(profileId, previousGoal);
		final var loadout = state.loadout();
		final boolean idle = PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(previousGoal.goalType());
		final var local = plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), idle ? loadout.shotItemId() : previous.shotItemId(), idle ? loadout.shotsPerEncounter() : previous.shotsPerEncounter(), idle ? loadout.summonNpcId() : previous.summonNpcId(), idle ? loadout.summonResourceItemId() : previous.summonResourceItemId(), idle ? loadout.summonResourcesPerEncounter() : previous.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), excludedTargets, excludedSteps, Objects.requireNonNull(live), PlanningCombat.from(state));
		if (local.ready() || !"LOCAL_FARM_UNAVAILABLE".equals(local.reasonKey()) || live.instanceId() != 0 || state.position().instanceId() != live.instanceId()) { return local; }
		// Use the same finite knowledge pages, native viability and route exclusions as historical
		// planning. The foreground owner must separately admit the actual departure and paid legs.
		final var distant = plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), idle ? loadout.shotItemId() : previous.shotItemId(), idle ? loadout.shotsPerEncounter() : previous.shotsPerEncounter(), idle ? loadout.summonNpcId() : previous.summonNpcId(), idle ? loadout.summonResourceItemId() : previous.summonResourceItemId(), idle ? loadout.summonResourcesPerEncounter() : previous.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), excludedTargets, excludedSteps, null, PlanningCombat.from(state));
		return distant.ready() ? new Result(distant.goal(), distant.spec(), distant.planIdentity(), distant.generation(), distant.routeEdgeIds(), "planner.topology_fallback") : distant;
	}

	/** Native foreground admission may reject a ranked itinerary without hiding the remaining sources. */
	public Result replanVisibleLocal(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal,
		PhantomTopologyPoint live, Set<String> excludedTargets, Set<String> excludedSteps, java.util.function.Predicate<PhantomGoal> admitted)
	{
		Objects.requireNonNull(admitted, "admitted");
		final var excluded = new java.util.HashSet<>(excludedTargets);
		for (int attempt = 0; attempt < 3; attempt++)
		{
			final var candidate = replanVisibleLocal(profileId, state, previousGoal, deterministicSeed, planOrdinal, live, excluded, excludedSteps);
			if (!candidate.ready() || admitted.test(candidate.goal())) { return candidate; }
			excluded.add(candidate.spec().npcId() + "@" + candidate.spec().anchorId());
		}
		return Result.blocked("planner.visible_route.unavailable");
	}
	/** Historical idle is a predecessor only, never a visible farming candidate. */
	static PhantomBackgroundGoalSpec visiblePredecessor(long profileId, PhantomGoal goal)
	{
		if (goal == null || goal.status() != PhantomGoalStatus.ACTIVE) { throw new IllegalArgumentException("Visible predecessor must be ACTIVE."); }
		if (!PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(goal.goalType())) { return PhantomBackgroundGoalSpec.parse(goal); }
		if (!new PhantomDomainRef("profile", Long.toString(profileId)).equals(goal.subject()) || goal.requiredAmount() != 1 || goal.currentAmount() != 0
			|| !"background.history".equals(goal.purposeKey()) || !"background.idle".equals(goal.acquisitionMethod()))
		{ throw new IllegalArgumentException("Noncanonical historical idle predecessor."); }
		return PhantomBackgroundGoalSpec.parseLifecycle(goal);
	}

	public boolean isVisibleLocal(PhantomGoal goal, PhantomTopologyPoint live)
	{
		return _topology.findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).filter(anchor -> (anchor.role() == PhantomTopologyAnchorRole.FARMING) && (localFarmDistance(anchor, live) <= 4_000_000L)).isPresent();
	}

	/** Squared local distance to the factual area, not its representative point. */
	private long localFarmDistance(PhantomTopologyAnchor anchor, PhantomTopologyPoint live)
	{
		final var area = _topology.findNode(anchor.nodeId()).map(node -> node.area()).orElse(null);
		if ((area == null) || (area.instanceId() != live.instanceId()) || (live.z() < area.minZ()) || (live.z() > area.maxZ())) { return Long.MAX_VALUE; }
		if (area.contains(live)) { return 0; }
		if (area.form() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea.Form.POINT_RADIUS)
		{
			final double vertical = (long) live.z() - area.center().z();
			final double horizontalRadius = Math.sqrt(Math.max(0, (double) area.radius() * area.radius() - vertical * vertical));
			final double distance = Math.max(0, Math.sqrt(live.distanceSquared2D(area.center())) - horizontalRadius);
			return (long) Math.ceil(distance * distance);
		}
		if (area.form() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea.Form.CUBOID)
		{
			final long dx = Math.max(0L, Math.max((long) area.minX() - live.x(), (long) live.x() - area.maxX()));
			final long dy = Math.max(0L, Math.max((long) area.minY() - live.y(), (long) live.y() - area.maxY()));
			return dx * dx + dy * dy;
		}
		double closest = Double.POSITIVE_INFINITY;
		for (int index = 0; index < area.vertices().size(); index++)
		{
			final var a = area.vertices().get(index); final var b = area.vertices().get((index + 1) % area.vertices().size());
			final double dx = (long) b.x() - a.x(); final double dy = (long) b.y() - a.y();
			final double length = dx * dx + dy * dy;
			final double fraction = length == 0 ? 0 : Math.max(0, Math.min(1, (((long) live.x() - a.x()) * dx + ((long) live.y() - a.y()) * dy) / length));
			final double x = live.x() - (a.x() + fraction * dx); final double y = live.y() - (a.y() + fraction * dy);
			closest = Math.min(closest, x * x + y * y);
		}
		return (long) Math.ceil(closest);
	}

	public boolean remainsSuitable(PhantomBackgroundState state, PhantomGoal goal)
	{
		return remainsSuitable(state, goal, null);
	}

	public boolean remainsVisibleSuitable(PhantomBackgroundState state, PhantomGoal goal, PhantomTopologyPoint live)
	{
		return remainsSuitable(state, goal, Objects.requireNonNull(live));
	}

	private boolean remainsSuitable(PhantomBackgroundState state, PhantomGoal goal, PhantomTopologyPoint live)
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
		final int minimum = Math.max(1, state.progress().level() - LOWER_LEVEL_TIERS.getLast());
		final int maximum = state.progress().level() + LEVEL_RADIUS;
		String cursor = null;
		for (int pageIndex = 0; pageIndex < MAXIMUM_TARGET_PAGES; pageIndex++)
		{
			final KnowledgePage<TargetFact> page = targets(minimum, maximum, state.progress().level(), cursor == null ? PageRequest.first(MAXIMUM_TARGETS) : new PageRequest(FALLBACK_TARGETS, cursor));
			final TargetFact target = page.values().stream().filter(value -> value.npc().npcId() == spec.npcId()).findFirst().orElse(null);
			if (target != null)
			{
				if (!_authority.viableTarget(PlanningCombat.from(state), target.npc().npcId())) { return false; }
				if (live != null)
				{
					final List<Candidate> local = new ArrayList<>(); addLocalCandidates(local, live, List.of(target));
					return local.stream().anyMatch(value -> value.anchor().id().equals(spec.anchorId()));
				}
				return candidate(state.position().committedAnchorId(), target, spec.anchorId(), true) != null;
			}
			cursor = page.nextCursor();
			if (cursor == null)
			{
				break;
			}
		}
		return false;
	}

	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision, Set<String> excludedTargets, Set<String> excludedSteps, PhantomTopologyPoint live, PlanningCombat combat)
	{
		return plan(profileId, level, activeClassId, currentAnchorId, shotItemId, shotsPerEncounter, summonNpcId, summonResourceItemId, summonResourcesPerEncounter, deterministicSeed, planOrdinal, previousGoalId, revision, excludedTargets, excludedSteps, live, combat, false);
	}

	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision, Set<String> excludedTargets, Set<String> excludedSteps, PhantomTopologyPoint live, PlanningCombat combat, boolean allowOffscreen)
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
		final int maximum = level + LEVEL_RADIUS;
		final List<Candidate> candidates = new ArrayList<>();
		final List<String> rejections = new ArrayList<>();
		final Set<String> seenCandidates = new java.util.HashSet<>();
		final Map<Integer, Boolean> viability = new java.util.HashMap<>();
		final int[] offscreenAttempts = { 0 };
		for (int radius : LOWER_LEVEL_TIERS)
		{
			String cursor = null;
			for (int pageIndex = 0; pageIndex < MAXIMUM_TARGET_PAGES; pageIndex++)
			{
				final KnowledgePage<TargetFact> page = targets(Math.max(1, level - radius), maximum, level, cursor == null ? PageRequest.first(MAXIMUM_TARGETS) : new PageRequest(FALLBACK_TARGETS, cursor));
				if (live == null) { addCandidates(candidates, seenCandidates, currentAnchorId, page.values(), allowOffscreen, rejections, offscreenAttempts, combat, viability, excludedTargets); }
				else { addLocalCandidates(candidates, live, page.values()); }
				candidates.removeIf(candidate ->
				{
					final boolean denied = excludedTargets.contains(candidate.target().npc().npcId() + "@" + candidate.anchor().id())
						|| candidate.routeEdgeIds().stream().anyMatch(excludedSteps::contains)
						|| combat != null && !viability.computeIfAbsent(candidate.target().npc().npcId(), npcId -> _authority.viableTarget(combat, npcId));
					if (denied) { reject(rejections, candidate.target().npc().npcId(), candidate.anchor().id(), "combat_or_current_exclusion"); }
					return denied;
				});
				cursor = page.nextCursor();
				if (cursor == null) { break; }
			}
			if (candidates.stream().anyMatch(value -> value.mode() == PhantomOffscreenOutdoorTravel.Mode.CERTIFIED)) { break; }
		}
		if (candidates.isEmpty())
		{
			return Result.blocked(live == null ? "planner.target_or_route.absent" : "LOCAL_FARM_UNAVAILABLE").withRejections(rejections);
		}
		// A pending virtual option never displaces an existing suitable certified itinerary.
		if (candidates.stream().anyMatch(value -> value.mode() == PhantomOffscreenOutdoorTravel.Mode.CERTIFIED))
		{
			candidates.removeIf(value -> value.mode() != PhantomOffscreenOutdoorTravel.Mode.CERTIFIED);
		}
		if (live != null)
		{
			candidates.sort(Comparator.comparingLong((Candidate value) -> localFarmDistance(value.anchor(), live)).thenComparing(value -> tieBreak(deterministicSeed, planOrdinal, value)).thenComparingInt(value -> value.target().npc().npcId()).thenComparing(value -> value.anchor().id()));
			if (candidates.size() > 8) { candidates.subList(8, candidates.size()).clear(); }
		}
		if (live != null) { candidates.sort(Comparator.comparingLong((Candidate value) -> localFarmDistance(value.anchor(), live)).thenComparingInt(value -> Math.abs(value.target().npc().level() - level)).thenComparingInt(value -> value.routeEdgeIds().size()).thenComparing(value -> tieBreak(deterministicSeed, planOrdinal, value)).thenComparingInt(value -> value.target().npc().npcId()).thenComparing(value -> value.anchor().id())); }
		final Candidate selected = live == null ? selectGlobal(candidates, profileId, deterministicSeed, planOrdinal, level) : candidates.getFirst();
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
		final boolean offscreen = selected.mode() == PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING;
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, constraints, offscreen ? PhantomOffscreenOutdoorTravel.PENDING_REASON : "background.catchup.plan", revision);
		final PhantomBackgroundGoalSpec spec = PhantomBackgroundGoalSpec.parse(goal);
		final String identity = digest(offscreen ? "BACKGROUND_OFFSCREEN_PLAN_V1" : "BACKGROUND_CATCHUP_PLAN_V1", profileId, level, activeClassId, currentAnchorId, deterministicSeed, planOrdinal, generation.knowledgeGeneration(), generation.topologyGeneration(), generation.authorityHashes(), npcId, anchorId, selected.routeEdgeIds(), constraints);
		return new Result(goal, spec, identity, generation, selected.routeEdgeIds(), "planner.ready").withRejections(rejections);
	}

	public Result idleInitial(long profileId, Player player, long deterministicSeed, long planOrdinal)
	{
		final PlanningSnapshot facts = _authority.planningSnapshot(player);
		return idlePlan(profileId, facts.currentAnchorId(), deterministicSeed, planOrdinal, 0, 0);
	}

	public Result idleFromState(long profileId, PhantomBackgroundState state, PhantomGoal previous, long deterministicSeed, long planOrdinal)
	{
		return idlePlan(profileId, state.position().committedAnchorId(), deterministicSeed, planOrdinal, previous.goalId(), Math.addExact(previous.revision(), 1));
	}

	private Result idlePlan(long profileId, String currentAnchorId, long seed, long ordinal, long previousGoalId, long revision)
	{
		if (_topology.findAnchor(currentAnchorId).isEmpty()) { return Result.blocked("planner.ingress.absent"); }
		final PlanningGeneration generation = generation();
		final long goalId = previousGoalId > 0 ? previousGoalId : positiveLong(digest("BACKGROUND_CATCHUP_GOAL_V1", profileId, seed, generation.knowledgeGeneration(), generation.topologyGeneration()));
		final PhantomDomainRef owner = new PhantomDomainRef("profile", Long.toString(profileId));
		final PhantomGoal goal = new PhantomGoal(goalId, PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, owner, owner, 1, 0, "background.idle", List.of(), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, currentAnchorId), "background.history", 500, 0, 0, 0, Map.of(), "planner.target_or_route.absent", revision);
		return new Result(goal, PhantomBackgroundGoalSpec.parseLifecycle(goal), digest("BACKGROUND_CATCHUP_IDLE_V1", profileId, seed, ordinal, currentAnchorId, generation), generation, List.of(), "planner.historical_idle");
	}

	private KnowledgePage<TargetFact> targets(int minimum, int maximum, int level, PageRequest page)
	{
		return _knowledge.suitableTargets(new TargetQuery(minimum, maximum, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, page));
	}

	/** Comparable alternatives only; one geographic hash prevents NPC multiplicity from buying extra tickets. */
	private static Candidate selectGlobal(List<Candidate> candidates, long profileId, long seed, long ordinal, int level)
	{
		final Candidate reference = candidates.stream().min(Comparator.comparingInt((Candidate value) -> Math.abs(value.target().npc().level() - level))
			.thenComparingInt(value -> value.routeEdgeIds().size()).thenComparingLong(Candidate::travelMillis).thenComparingLong(Candidate::feeAdena)
			.thenComparingInt(value -> value.target().npc().npcId()).thenComparing(value -> value.anchor().id())).orElseThrow();
		final int delta = Math.abs(reference.target().npc().level() - level);
		// At most one extra leg/level delta, +30..120s and no higher quoted GK fee.
		final long maximumTravel = reference.travelMillis() + Math.clamp(reference.travelMillis() / 2, 30_000L, 120_000L);
		Candidate selected = reference;
		double bestRank = Double.POSITIVE_INFINITY;
		final Map<String, Double> geography = new java.util.HashMap<>();
		for (Candidate candidate : candidates)
		{
			final int levelDelta = Math.abs(candidate.target().npc().level() - level);
			if ((levelDelta > delta + 1) || (candidate.routeEdgeIds().size() > reference.routeEdgeIds().size() + 1)
				|| (candidate.travelMillis() > maximumTravel) || (candidate.feeAdena() > reference.feeAdena())
				|| (candidate.target().npc().exp() < reference.target().npc().exp() * 0.5) || (candidate.target().npc().sp() < reference.target().npc().sp() * 0.5)) { continue; }
			final double reward = reference.target().npc().exp() > 0 ? candidate.target().npc().exp() / reference.target().npc().exp()
				: reference.target().npc().sp() > 0 ? candidate.target().npc().sp() / reference.target().npc().sp() : 1;
			// Configured source population is a bounded preference, never a claim of live occupancy/density.
			final long amount = candidate.target().representativeAreas().stream().filter(area -> candidate.anchor().nodeId().equals(area.topologyNodeId()))
				.mapToLong(area -> area.totalConfiguredAmount()).max().orElse(1);
			final double weight = Math.clamp(reward, 0.5, 1.5) * (1 + 0.15 * StrictMath.log1p(Math.min(16, amount)) / StrictMath.log(2))
				/ (1 + 0.35 * (levelDelta - delta))
				/ (1 + Math.max(0, candidate.travelMillis() - reference.travelMillis()) / 60_000.0)
				/ (1 + 0.15 * Math.max(0, candidate.routeEdgeIds().size() - reference.routeEdgeIds().size()));
			final double exponential = geography.computeIfAbsent(candidate.anchor().id(), anchor ->
			{
				final long bits = positiveLong(digest("BACKGROUND_GEOGRAPHY_RANK_V1", profileId, seed, ordinal, anchor)) >>> 11;
				return -StrictMath.log((bits + 1.0) / ((1L << 52) + 1.0));
			});
			final double rank = exponential / weight;
			if ((rank < bestRank) || ((rank == bestRank) && (candidate.target().npc().npcId() < selected.target().npc().npcId()))
				|| ((rank == bestRank) && (candidate.target().npc().npcId() == selected.target().npc().npcId()) && candidate.anchor().id().compareTo(selected.anchor().id()) < 0))
			{
				selected = candidate; bestRank = rank;
			}
		}
		return selected;
	}

	private void addCandidates(List<Candidate> candidates, Set<String> seen, String currentAnchorId, List<TargetFact> targets, boolean allowOffscreen,
		List<String> rejections, int[] offscreenAttempts, PlanningCombat combat, Map<Integer, Boolean> viability, Set<String> excludedTargets)
	{
		for (TargetFact target : targets)
		{
			if (combat != null && !viability.computeIfAbsent(target.npc().npcId(), npcId -> _authority.viableTarget(combat, npcId)))
			{ reject(rejections, target.npc().npcId(), "", "combat_or_current_exclusion"); continue; }
			for (var area : target.representativeAreas().stream().filter(value -> (value.instanceId() == 0) && (value.totalConfiguredAmount() > 0) && (value.topologyNodeId() != null)).toList())
			{
				for (PhantomTopologyAnchor anchor : _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()).stream().filter(value -> (value.role() == PhantomTopologyAnchorRole.FARMING) && (value.point().instanceId() == 0) && ((value.npcId() == null) || (value.npcId() == target.npc().npcId()))).toList())
				{
					if (!seen.add(target.npc().npcId() + "@" + anchor.id())) { continue; }
					if (excludedTargets.contains(target.npc().npcId() + "@" + anchor.id()))
					{ reject(rejections, target.npc().npcId(), anchor.id(), "combat_or_current_exclusion"); continue; }
					final Candidate candidate = candidate(currentAnchorId, target, anchor.id(), allowOffscreen, rejections, offscreenAttempts);
					if (candidate != null)
					{
						candidates.add(candidate);
					}
				}
			}
		}
	}

	private Candidate candidate(String currentAnchorId, TargetFact target, String anchorId, boolean allowOffscreen)
	{
		return candidate(currentAnchorId, target, anchorId, allowOffscreen, new ArrayList<>(), new int[1]);
	}

	private static void reject(List<String> rejections, int npcId, String anchorId, String reason)
	{
		final String evidence = npcId + "@" + anchorId + ":" + reason;
		if (rejections.size() < 32) { rejections.add(evidence); return; }
		if (preliminaryRejection(reason)) { return; }
		// Preserve the first native/budget failures when cheap rejects filled the log.
		for (int index = 0; index < rejections.size(); index++)
		{
			if (preliminaryRejection(rejections.get(index))) { rejections.set(index, evidence); return; }
		}
	}

	private static boolean preliminaryRejection(String reason)
	{
		return reason.endsWith("topology.not_public_outdoor") || reason.endsWith("combat_or_current_exclusion");
	}

	private Candidate candidate(String currentAnchorId, TargetFact target, String anchorId, boolean allowOffscreen, List<String> rejections, int[] offscreenAttempts)
	{
		final PhantomTopologyAnchor anchor = _topology.findAnchor(anchorId).orElse(null);
		if ((anchor == null) || (anchor.role() != PhantomTopologyAnchorRole.FARMING) || (anchor.point().instanceId() != 0) || ((anchor.npcId() != null) && (anchor.npcId() != target.npc().npcId())) || target.representativeAreas().stream().noneMatch(area -> (area.instanceId() == 0) && (area.totalConfiguredAmount() > 0) && anchor.nodeId().equals(area.topologyNodeId())))
		{
			return null;
		}
		final PhantomNormalGatekeeperTravel travel = _authority.travelQuery(_topology);
		final List<PhantomNormalGatekeeperTravel.Step> route = travel.route(currentAnchorId, anchorId).orElse(null);
		if (route == null && allowOffscreen)
		{
			// Static topology/reward rejection is not a native proof attempt. Counting
			// every seen anchor lets early event/closed templates starve later sources.
			if (!PhantomOffscreenOutdoorTravel.sourceBacked(_topology, currentAnchorId, target, anchorId))
			{ reject(rejections, target.npc().npcId(), anchorId, "topology.not_public_outdoor"); return null; }
			if (offscreenAttempts[0] >= MAXIMUM_OFFSCREEN_NATIVE_PROOFS)
			{ reject(rejections, target.npc().npcId(), anchorId, "planner.offscreen_candidate_budget"); return null; }
			offscreenAttempts[0]++;
			final String denied = _authority.offscreenOutdoorRejection(_topology, currentAnchorId, target, anchorId);
			if (denied.isEmpty()) { return new Candidate(target, anchor, List.of(), 0, 0, PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING); }
			reject(rejections, target.npc().npcId(), anchorId, denied);
		}
		if ((route == null) || (!currentAnchorId.equals(anchorId) && route.isEmpty()))
		{
			return null;
		}
		return new Candidate(target, anchor, route.stream().map(PhantomNormalGatekeeperTravel.Step::id).toList(), route.stream().mapToLong(PhantomNormalGatekeeperTravel.Step::travelMillis).sum(),
			route.stream().filter(step -> step.gatekeeper() != null).mapToLong(step -> step.gatekeeper().feeCount()).sum());
	}

	private void addLocalCandidates(List<Candidate> candidates, PhantomTopologyPoint live, List<TargetFact> targets)
	{
		for (TargetFact target : targets)
		{
			for (var area : target.representativeAreas())
			{
				if ((area.instanceId() != live.instanceId()) || (area.totalConfiguredAmount() <= 0) || (area.topologyNodeId() == null)) { continue; }
				for (var anchor : _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()))
				{
					if ((anchor.role() == PhantomTopologyAnchorRole.FARMING) && (localFarmDistance(anchor, live) <= 4_000_000L)
						&& ((anchor.npcId() == null) || (anchor.npcId() == target.npc().npcId())))
					{
						final var candidate = new Candidate(target, anchor, List.of(), 0, 0);
						if (!candidates.contains(candidate)) { candidates.add(candidate); }
					}
				}
			}
		}
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

	private record Candidate(TargetFact target, PhantomTopologyAnchor anchor, List<String> routeEdgeIds, long travelMillis, long feeAdena, PhantomOffscreenOutdoorTravel.Mode mode)
	{
		private Candidate(TargetFact target, PhantomTopologyAnchor anchor, List<String> routeEdgeIds, long travelMillis, long feeAdena)
		{
			this(target, anchor, routeEdgeIds, travelMillis, feeAdena, PhantomOffscreenOutdoorTravel.Mode.CERTIFIED);
		}
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

	public record Result(PhantomGoal goal, PhantomBackgroundGoalSpec spec, String planIdentity, PlanningGeneration generation, List<String> routeEdgeIds, String reasonKey, List<String> candidateRejections)
	{
		public Result(PhantomGoal goal, PhantomBackgroundGoalSpec spec, String planIdentity, PlanningGeneration generation, List<String> routeEdgeIds, String reasonKey)
		{
			this(goal, spec, planIdentity, generation, routeEdgeIds, reasonKey, List.of());
		}

		public Result
		{
			candidateRejections = List.copyOf(candidateRejections);
			if (candidateRejections.size() > 32) { throw new IllegalArgumentException("Unbounded candidate diagnostics."); }
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

		private Result withRejections(List<String> rejections)
		{
			return new Result(goal, spec, planIdentity, generation, routeEdgeIds, reasonKey, rejections);
		}

		public PhantomOffscreenOutdoorTravel.Mode travelMode()
		{
			return goal != null && PhantomOffscreenOutdoorTravel.pending(goal) ? PhantomOffscreenOutdoorTravel.Mode.OFFSCREEN_OUTDOOR_PENDING : PhantomOffscreenOutdoorTravel.Mode.CERTIFIED;
		}
	}
}
