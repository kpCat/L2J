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
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningSnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.PlanningCombat;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnAreaSummary;
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
	public List<Integer> partyRoleSkillIds(PhantomBackgroundState state) { return _authority.partyRoleSkillIds(state); }
	public String potentialPartyRole(PhantomBackgroundState state, Map<Integer, Integer> learned) { return _authority.potentialPartyRole(state, learned); }

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
		return replaceFromState(profileId, state, previousGoal, deterministicSeed, planOrdinal, false);
	}

	/** An insufficient-funds recovery may select only an itinerary covered by the same factual wallet. */
	public Result replaceAffordableFromState(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal)
	{
		return replaceFromState(profileId, state, previousGoal, deterministicSeed, planOrdinal, true);
	}

	private Result replaceFromState(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal, boolean affordableTravel)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(previousGoal, "previousGoal");
		if (PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(previousGoal.goalType()) && !PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, previousGoal))
		{
			return Result.blocked("planner.previous_idle.invalid");
		}
		if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(previousGoal.goalType()) && !PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(previousGoal.goalType()))
		{
			return Result.blocked("planner.previous_goal.unsupported");
		}
		final PhantomBackgroundState.Loadout loadout = state.loadout();
		return plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(), loadout.shotItemId(), loadout.shotsPerEncounter(), loadout.summonNpcId(), loadout.summonResourceItemId(), loadout.summonResourcesPerEncounter(), deterministicSeed, planOrdinal, previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), Set.of(), Set.of(), null, PlanningCombat.from(state), offscreenSupplied(state), candidate -> !affordableTravel || affordableTravel(candidate, state));
	}

	private boolean affordableTravel(Candidate candidate, PhantomBackgroundState state)
	{
		final long wallet = state.inventory().itemCount(57);
		if (candidate.feeAdena() <= wallet) { return true; }
		// The immutable route query is cached; native fee policy includes base-class free travel.
		return _authority.travelQuery(_topology).route(state.position().committedAnchorId(), candidate.anchor().id())
			.map(route -> route.stream().filter(step -> step.gatekeeper() != null)
				.mapToLong(step -> PhantomNormalGatekeeperTravel.phantomMaximumFee(step.gatekeeper(), state.identity().classIndex(), state.progress().level())).reduce(0, Math::addExact) <= wallet).orElse(false);
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
	/** Select only the agreed farm source through the existing bounded target/route planner. */
	public Result replanVisibleRendezvous(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal,
		PhantomTopologyPoint live, PhantomDomainRef sharedFarmSource, Set<String> excludedSteps, java.util.function.Predicate<PhantomGoal> admitted)
	{
		return replanVisibleRendezvous(profileId, state, previousGoal, deterministicSeed, planOrdinal, live, sharedFarmSource, excludedSteps, false, admitted);
	}

	/** An exact accepted physical member proposes shared travel only; Solo and historical admission are unchanged. */
	public Result replanVisibleRendezvous(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal,
		PhantomTopologyPoint live, PhantomDomainRef sharedFarmSource, Set<String> excludedSteps, boolean supportTravelOnly, java.util.function.Predicate<PhantomGoal> admitted)
	{
		Objects.requireNonNull(admitted, "admitted");
		if (state == null || state.identity().profileId() != profileId || !new PhantomDomainRef("profile", Long.toString(profileId)).equals(previousGoal.subject())
			|| live == null || live.instanceId() != 0 || state.position().instanceId() != live.instanceId()) { return Result.blocked("planner.rendezvous.identity_mismatch"); }
		var candidate = rendezvousGraphPlan(profileId, state, previousGoal, deterministicSeed, planOrdinal, sharedFarmSource, excludedSteps, supportTravelOnly);
		if (!candidate.ready()) { candidate = rendezvousIngressPlan(profileId, state, previousGoal, deterministicSeed, planOrdinal, live, sharedFarmSource, excludedSteps, supportTravelOnly); }
		return !candidate.ready() || admitted.test(candidate.goal()) ? candidate : Result.blocked("planner.rendezvous.travel_unavailable");
	}
	/** Logical agreement constrains the ordinary planner; native-only WALK proposals never become offscreen edges. */
	public Result replanLogicalRendezvous(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long seed, long ordinal, PhantomDomainRef source)
	{ return replanLogicalRendezvous(profileId,state,previousGoal,seed,ordinal,source,Set.of(),Set.of(),false); }
	public Result replanLogicalRendezvous(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long seed, long ordinal, PhantomDomainRef source,
		Set<String> excludedTargets, Set<String> excludedSteps, boolean affordableTravel)
	{
		if (state == null || previousGoal == null || state.identity().profileId() != profileId) { return Result.blocked("planner.rendezvous.identity_mismatch"); }
		final var result = rendezvousGraphPlan(profileId, state, previousGoal, seed, ordinal, source, excludedTargets, excludedSteps, affordableTravel, false);
		return result.ready() ? result : Result.blocked("native_context.required:logical_party_preparation");
	}
	private Result rendezvousGraphPlan(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal,
		PhantomDomainRef sharedFarmSource, Set<String> excludedSteps, boolean supportTravelOnly)
	{ return rendezvousGraphPlan(profileId,state,previousGoal,deterministicSeed,planOrdinal,sharedFarmSource,Set.of(),excludedSteps,false,supportTravelOnly); }
	/** Metadata/travel proposal only; this grants no Background combat or reward permission. */
	public Result proposeLogicalActivity(long id, PhantomBackgroundState state, PhantomGoal previous, long seed, long ordinal, PhantomDomainRef source)
	{
		if (state == null || previous == null || state.identity().profileId() != id) { return Result.blocked("planner.rendezvous.identity_mismatch"); }
		return rendezvousGraphPlan(id, state, previous, seed, ordinal, source, Set.of(), Set.of(), true, false);
	}
	private Result rendezvousGraphPlan(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal,
		PhantomDomainRef sharedFarmSource, Set<String> excludedTargets, Set<String> excludedSteps, boolean affordableTravel, boolean supportTravelOnly)
	{
		final var source = rendezvousSource(sharedFarmSource); final var previous = visiblePredecessor(profileId, previousGoal);
		final var anchor = _topology.findAnchor(source.anchorId()).orElse(null);
		final var target = partyTarget(state.progress().level(), source, anchor);
		final var steps = _authority.travelQuery(_topology).route(state.position().committedAnchorId(), source.anchorId()).orElse(null);
		if (target == null || excludedTargets.contains(sharedFarmSource.key()) || !committedPartyRouteAvailable(state, steps, excludedSteps))
		{ return Result.blocked("planner.rendezvous.route_unconfirmed"); }
		final var candidate = new Candidate(target, anchor, steps.stream().map(PhantomNormalGatekeeperTravel.Step::id).toList(),
			steps.stream().mapToLong(PhantomNormalGatekeeperTravel.Step::travelMillis).sum(), 0);
		if (affordableTravel && !affordableTravel(candidate, state)) { return Result.blocked("planner.rendezvous.insufficient_adena"); }
		final var loadout = state.loadout();
		final boolean idle = PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE.equals(previousGoal.goalType());
		return plan(profileId, state.progress().level(), state.identity().activeClassId(), state.position().committedAnchorId(),
			idle ? loadout.shotItemId() : previous.shotItemId(), idle ? loadout.shotsPerEncounter() : previous.shotsPerEncounter(),
			idle ? loadout.summonNpcId() : previous.summonNpcId(), idle ? loadout.summonResourceItemId() : previous.summonResourceItemId(),
			idle ? loadout.summonResourcesPerEncounter() : previous.summonResourcesPerEncounter(), deterministicSeed, planOrdinal,
			previousGoal.goalId(), Math.addExact(previousGoal.revision(), 1), excludedTargets, excludedSteps, null, null, false,
			_ -> true, new java.util.HashMap<>(), candidate);
	}
	private Result rendezvousIngressPlan(long profileId, PhantomBackgroundState state, PhantomGoal previous, long seed, long ordinal,
		PhantomTopologyPoint live, PhantomDomainRef source, Set<String> excludedSteps, boolean supportTravelOnly)
	{
		final var spec = rendezvousSource(source); final var destination = _topology.findAnchor(spec.anchorId()).orElse(null);
		if (destination == null || destination.role() != PhantomTopologyAnchorRole.FARMING || destination.point().instanceId() != live.instanceId()) { return Result.blocked("planner.rendezvous.ingress_unknown"); }
		final var ingress = rendezvousIngress(state, destination, live, excludedSteps, anchor -> _authority.nativeIngressPositionKnown(anchor, state.position().heading()));
		if (ingress == null) { return Result.blocked("planner.rendezvous.ingress_unknown"); }
		if (partyTarget(state.progress().level(), spec, destination) == null) { return Result.blocked("planner.rendezvous.target_unavailable"); }
		final var route = ingress.id().equals(destination.id()) ? List.<PhantomNormalGatekeeperTravel.Step>of() : _authority.travelQuery(_topology).route(ingress.id(), destination.id()).orElseThrow();
		final var constraints = new LinkedHashMap<String, Long>(); final var loadout = state.loadout();
		putPositivePair(constraints, PhantomBackgroundGoalSpec.SHOT_ITEM, loadout.shotItemId(), PhantomBackgroundGoalSpec.SHOT_COUNT, loadout.shotsPerEncounter());
		if (loadout.summonNpcId() > 0) { constraints.put(PhantomBackgroundGoalSpec.SUMMON_NPC, (long) loadout.summonNpcId()); }
		putPositivePair(constraints, PhantomBackgroundGoalSpec.SUMMON_RESOURCE_ITEM, loadout.summonResourceItemId(), PhantomBackgroundGoalSpec.SUMMON_RESOURCE_COUNT, loadout.summonResourcesPerEncounter());
		final var generation = generation(); final var edges = route.stream().map(PhantomNormalGatekeeperTravel.Step::id).toList();
		final var goal = new PhantomGoal(previous.goalId(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, previous.subject(),
			new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(spec.npcId())), 1, 0, "background.farm", List.of(source),
			new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, spec.anchorId()), "farm.background", 500, 0, 0, 0, constraints, "background.rendezvous.native_ingress", Math.addExact(previous.revision(), 1));
		return new Result(goal, PhantomBackgroundGoalSpec.parse(goal), digest("RENDEZVOUS_NATIVE_INGRESS_V1", profileId, seed, ordinal, previous.goalId(), goal.revision(), generation, live, ingress.id(), source, edges), generation, edges, "planner.rendezvous.native_ingress");
	}
	private PhantomTopologyAnchor rendezvousIngress(PhantomBackgroundState state, PhantomTopologyAnchor destination, PhantomTopologyPoint live,
		Set<String> excludedSteps, java.util.function.Predicate<PhantomTopologyAnchor> ingressEvidence)
	{
		final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		if (destination.point().distance3D(live) <= policy.maximumLocalStraightDistance() && ingressEvidence.test(destination)) { return destination; }
		final var travel = _authority.travelQuery(_topology);
		return PhantomVisibleFarmTravel.idleIngress(_topology.snapshot().anchors(), live, destination.id(), policy.maximumRouteDistance(), anchor ->
		{
			if (!ingressEvidence.test(anchor)) { return false; }
			final var route = travel.route(anchor.id(), destination.id()).orElse(null);
			return route != null && !route.isEmpty() && partyItineraryAvailable(state, route, excludedSteps);
		});
	}

	/** Exact shared source is an intent, never native arrival or an arbitrary XYZ. */
	public List<PhantomDomainRef> rendezvousSources(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal, Set<String> excludedTargets, Set<String> excludedSteps)
	{
		return rendezvousSources(profileId, state, previousGoal, deterministicSeed, planOrdinal, excludedTargets, excludedSteps, _ -> true);
	}

	/** Admission precedes the result cap: peers can find a common source after 64 noncommon ones. */
	public List<PhantomDomainRef> rendezvousSources(long profileId, PhantomBackgroundState state, PhantomGoal previousGoal, long deterministicSeed, long planOrdinal, Set<String> excludedTargets, Set<String> excludedSteps, java.util.function.Predicate<PhantomDomainRef> admitted)
	{
		visiblePredecessor(profileId, previousGoal);
		if (state == null || state.identity().profileId() != profileId || state.position().instanceId() != 0) { return List.of(); }
		final var band = PhantomPartyFarmEligibility.forMember(state.progress().level());
		final var result = new java.util.LinkedHashSet<PhantomDomainRef>();
		String cursor = null;
		for (int pageIndex = 0; pageIndex < MAXIMUM_TARGET_PAGES; pageIndex++)
		{
			final var page = targets(band.minimum(), band.maximum(), state.progress().level(),
				cursor == null ? PageRequest.first(MAXIMUM_TARGETS) : new PageRequest(FALLBACK_TARGETS, cursor));
			for (var target : page.values())
			{
				for (var area : _knowledge.snapshot().spawnAreasByNpc().getOrDefault(target.npc().npcId(), List.of()))
				{
					if (area.instanceId() != 0 || area.totalConfiguredAmount() <= 0 || area.topologyNodeId() == null) { continue; }
					for (var anchor : _topology.snapshot().anchorsByNode().getOrDefault(area.topologyNodeId(), List.of()))
					{
						if (anchor.role() != PhantomTopologyAnchorRole.FARMING || anchor.point().instanceId() != 0 || anchor.npcId() != null && anchor.npcId() != target.npc().npcId()) { continue; }
						final var source = new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, target.npc().npcId() + "@" + anchor.id());
						if (!excludedTargets.contains(source.key()) && rendezvousRouteAvailable(state, source, excludedSteps) && admitted.test(source)) { result.add(source); }
						if (result.size() >= 64) { return List.copyOf(result); }
					}
				}
			}
			cursor = page.nextCursor(); if (cursor == null) { break; }
		}
		return List.copyOf(result);
	}
	/** Exact indexed peer proof, independent of the peer's first discovery page. No SQL or Player. */
	public boolean partySpoilSource(PhantomDomainRef source)
	{
		final var spec = rendezvousSource(source);
		return _knowledge.snapshot().spoilFactsByNpc().getOrDefault(spec.npcId(), List.of()).stream()
			.anyMatch(fact -> fact.rawGroupChance() > 0 && fact.rawItemChance() > 0 && fact.maximumCount() > 0);
	}
	public List<PhantomDomainRef> rendezvousSourcesForRole(long id, PhantomBackgroundState state, PhantomGoal root, long seed, long ordinal,
		Set<String> excludedTargets, Set<String> excludedSteps, String potentialRole, java.util.function.Predicate<PhantomDomainRef> admitted)
	{
		if ("utility.spoiler".equals(potentialRole))
		{
			final var preferred = rendezvousSources(id, state, root, seed, ordinal, excludedTargets, excludedSteps, source -> partySpoilSource(source) && admitted.test(source));
			if (!preferred.isEmpty()) { return preferred; }
		}
		return rendezvousSources(id, state, root, seed, ordinal, excludedTargets, excludedSteps, admitted);
	}
	public boolean partySourceAvailable(PhantomBackgroundState state, PhantomDomainRef source, Set<String> excludedSteps)
	{
		if (state == null || state.position().instanceId() != 0) { return false; }
		final var spec = rendezvousSource(source);
		return partyTarget(state.progress().level(), spec, _topology.findAnchor(spec.anchorId()).orElse(null)) != null
			&& rendezvousRouteAvailable(state, source, excludedSteps);
	}

	private TargetFact partyTarget(int level, PhantomBackgroundGoalSpec spec, PhantomTopologyAnchor anchor)
	{
		final var npc = _knowledge.findNpc(spec.npcId()).orElse(null);
		if (npc == null || npc.kind() != NpcKind.MONSTER || !npc.attackable() || !npc.targetable() || !PhantomPartyFarmEligibility.forMember(level).contains(npc.level())
			|| anchor == null || anchor.role() != PhantomTopologyAnchorRole.FARMING || anchor.point().instanceId() != 0
			|| anchor.npcId() != null && anchor.npcId() != npc.npcId()) { return null; }
		final var areas = _knowledge.snapshot().spawnAreasByNpc().getOrDefault(npc.npcId(), List.of());
		for (var area : areas)
		{
			if (area.instanceId() == 0 && area.totalConfiguredAmount() > 0 && anchor.nodeId().equals(area.topologyNodeId()))
			{ return new TargetFact(npc, areas.size(), List.of(SpawnAreaSummary.from(area)), areas.size() > 1); }
		}
		return null;
	}
	/** A World target must still belong to the agreed factual NPC/anchor, independently of actor level-up. */
	public boolean partyNativeTarget(PhantomDomainRef source, int npcId, PhantomTopologyPoint point)
	{
		if (source == null || point == null || point.instanceId() != 0) { return false; }
		final var spec = rendezvousSource(source); final var anchor = _topology.findAnchor(spec.anchorId()).orElse(null);
		final var npc = _knowledge.findNpc(npcId).orElse(null);
		return spec.npcId() == npcId && npc != null && npc.kind() == NpcKind.MONSTER && npc.attackable() && npc.targetable()
			&& anchor != null && anchor.role() == PhantomTopologyAnchorRole.FARMING && anchor.point().instanceId() == 0
			&& (anchor.npcId() == null || anchor.npcId() == npcId) && Math.abs((long) anchor.point().z() - point.z()) <= 200
			&& anchor.point().distance3D(point) <= org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE
			&& _knowledge.snapshot().spawnAreasByNpc().getOrDefault(npcId, List.of()).stream().anyMatch(area -> area.instanceId() == 0 && area.totalConfiguredAmount() > 0 && anchor.nodeId().equals(area.topologyNodeId()));
	}
	/** Legacy affinity for old diagnostic consumers only. Production uses durable learned evidence. */
	public static String logicalRole(PhantomBackgroundState state)
	{
		final var type = org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(state.identity().activeClassId());
		return type != null && java.util.stream.Stream.of(org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.BISHOP,
			org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.PROPHET, org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.ELDER,
			org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.SHILLIEN_ELDER).anyMatch(type::equalsOrChildOf) ? "support" : "damage";
	}
	public enum RendezvousRoute { REACHABLE, REACHABLE_VIA_INGRESS, TEMPORARILY_BLOCKED, DEFINITIVELY_UNREACHABLE, UNKNOWN }
	public record RendezvousItinerary(RendezvousRoute status, String departureAnchorId, List<PhantomNormalGatekeeperTravel.Step> steps)
	{
		public RendezvousItinerary { steps = List.copyOf(steps); }
		public boolean available() { return status == RendezvousRoute.REACHABLE || status == RendezvousRoute.REACHABLE_VIA_INGRESS; }
	}

	/** Structural WALK/GK itinerary only. Ingress and every native connector still need physical proof. */
	public RendezvousItinerary rendezvousItinerary(PhantomBackgroundState state, PhantomDomainRef source, Set<String> excludedSteps)
	{
		final var spec = rendezvousSource(source); final var anchor = _topology.findAnchor(spec.anchorId()).orElse(null);
		if (state == null || anchor == null || anchor.role() != PhantomTopologyAnchorRole.FARMING) { return new RendezvousItinerary(RendezvousRoute.UNKNOWN, "", List.of()); }
		if (anchor.point().instanceId() != 0 || state.position().instanceId() != 0) { return new RendezvousItinerary(RendezvousRoute.DEFINITIVELY_UNREACHABLE, "", List.of()); }
		final var travel = _authority.travelQuery(_topology);
		final String departure = state.position().committedAnchorId();
		final var position = state.position();
		final var live = new PhantomTopologyPoint(position.x(), position.y(), position.z(), position.instanceId());
		if (!departure.equals(anchor.id()) && structuralIngress(anchor, live)
			&& PhantomVisibleFarmTravel.localFarmApproach(anchor, live, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults().maximumLocalStraightDistance()) != null)
		{ return new RendezvousItinerary(RendezvousRoute.REACHABLE_VIA_INGRESS, anchor.id(), List.of()); }
		final var route = travel.route(departure, anchor.id()).orElse(null);
		if (committedPartyRouteAvailable(state, route, excludedSteps))
		{
			final var point = _topology.findAnchor(departure).orElseThrow().point();
			return new RendezvousItinerary(live.equals(point) ? RendezvousRoute.REACHABLE : RendezvousRoute.REACHABLE_VIA_INGRESS, departure, route);
		}
		final var localAnchors = _topology.locate(live).stream().flatMap(node -> _topology.snapshot().anchorsByNode().getOrDefault(node.id(), List.of()).stream())
			.filter(candidate -> structuralIngress(candidate, live))
			.sorted(Comparator.comparingDouble((PhantomTopologyAnchor candidate) -> candidate.point().distance3D(live)).thenComparing(PhantomTopologyAnchor::id))
			.limit(16).toList();
		for (var ingress : localAnchors)
		{
			final var onward = travel.route(ingress.id(), anchor.id()).orElse(null);
			if (onward != null && partyItineraryAvailable(state, onward, excludedSteps))
			{ return new RendezvousItinerary(RendezvousRoute.REACHABLE_VIA_INGRESS, ingress.id(), onward); }
		}
		// A missing graph-only route proves neither a missing native WALK ingress nor impossible Geo.
		return new RendezvousItinerary(route == null ? RendezvousRoute.UNKNOWN : RendezvousRoute.TEMPORARILY_BLOCKED, "", List.of());
	}

	private boolean structuralIngress(PhantomTopologyAnchor anchor, PhantomTopologyPoint live)
	{
		final var node = _topology.snapshot().nodeById().get(anchor.nodeId());
		return anchor.point().instanceId() == live.instanceId() && (anchor.point().equals(live)
			|| !anchor.sourceRefs().isEmpty() && node != null && node.area().contains(live)
				&& anchor.point().distance3D(live) <= org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults().maximumRouteDistance());
	}

	private boolean committedPartyRouteAvailable(PhantomBackgroundState state, List<PhantomNormalGatekeeperTravel.Step> route, Set<String> excludedSteps)
	{
		if (route == null) { return false; }
		final var position = state.position();
		final var departure = _topology.findAnchor(position.committedAnchorId()).orElse(null);
		return departure != null && structuralIngress(departure, new PhantomTopologyPoint(position.x(), position.y(), position.z(), position.instanceId()))
			&& partyItineraryAvailable(state, route, excludedSteps);
	}

	private boolean partyItineraryAvailable(PhantomBackgroundState state, List<PhantomNormalGatekeeperTravel.Step> route, Set<String> excludedSteps)
	{
		if (route.size() > 64 || !PhantomVisibleFarmTravel.itineraryAffordable(route, state.identity().classIndex(),
			state.progress().level(), java.time.Instant.now().toEpochMilli() / 60000, state.inventory().itemCount(57))) { return false; }
		final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		for (var step : route)
		{
			final var from = _topology.findAnchor(step.fromAnchorId()).orElse(null);
			final var to = _topology.findAnchor(step.toAnchorId()).orElse(null);
			if (excludedSteps.contains(step.id()) || from == null || to == null || from.point().instanceId() != 0 || to.point().instanceId() != 0) { return false; }
			if (step.type() == PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND && from.point().distance3D(to.point()) > policy.maximumRouteDistance()) { return false; }
		}
		return true;
	}

	public RendezvousRoute rendezvousRoutePreview(PhantomBackgroundState state, PhantomDomainRef source, Set<String> excludedSteps)
	{
		return rendezvousItinerary(state, source, excludedSteps).status();
	}

	/** Pure structural preflight over the same certified route catalog; no native usability or arrival claim. */
	public boolean rendezvousRouteAvailable(PhantomBackgroundState state, PhantomDomainRef source, Set<String> excludedSteps)
	{
		return rendezvousItinerary(state, source, excludedSteps).available();
	}
	/** Positive known-graph absence only. Unknown/invalid input is not terminal evidence. */
	public boolean rendezvousRouteAbsent(PhantomBackgroundState state, PhantomDomainRef source)
	{
		try
		{
			return rendezvousRoutePreview(state, source, Set.of()) == RendezvousRoute.DEFINITIVELY_UNREACHABLE;
		}
		catch (IllegalArgumentException invalid) { return false; }
	}

	static PhantomBackgroundGoalSpec rendezvousSource(PhantomDomainRef source)
	{
		if (source == null || !PhantomBackgroundGoalSpec.SOURCE_NAMESPACE.equals(source.namespace()) || !source.key().matches("[1-9][0-9]{0,9}@[^@]+"))
		{ throw new IllegalArgumentException("Invalid rendezvous farm source."); }
		final int separator = source.key().indexOf('@');
		return new PhantomBackgroundGoalSpec(Integer.parseInt(source.key().substring(0, separator)), source.key().substring(separator + 1), 0, 0, 0, 0, 0);
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
		if ((live == null) && PhantomBackgroundGoalSpec.canonicalHistoricalIdle(state.identity().profileId(), goal))
		{
			return goal.selectedAnchor().key().equals(state.position().committedAnchorId()) && _topology.findAnchor(goal.selectedAnchor().key()).isPresent();
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
		return plan(profileId, level, activeClassId, currentAnchorId, shotItemId, shotsPerEncounter, summonNpcId, summonResourceItemId, summonResourcesPerEncounter, deterministicSeed, planOrdinal, previousGoalId, revision, excludedTargets, excludedSteps, live, combat, allowOffscreen, _ -> true);
	}

	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision, Set<String> excludedTargets, Set<String> excludedSteps, PhantomTopologyPoint live, PlanningCombat combat, boolean allowOffscreen, java.util.function.Predicate<Candidate> admitted)
	{
		return plan(profileId, level, activeClassId, currentAnchorId, shotItemId, shotsPerEncounter, summonNpcId, summonResourceItemId, summonResourcesPerEncounter, deterministicSeed, planOrdinal, previousGoalId, revision, excludedTargets, excludedSteps, live, combat, allowOffscreen, admitted, new java.util.HashMap<>());
	}
	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision, Set<String> excludedTargets, Set<String> excludedSteps, PhantomTopologyPoint live, PlanningCombat combat, boolean allowOffscreen, java.util.function.Predicate<Candidate> admitted, Map<Integer, Boolean> viability)
	{
		return plan(profileId, level, activeClassId, currentAnchorId, shotItemId, shotsPerEncounter, summonNpcId, summonResourceItemId, summonResourcesPerEncounter, deterministicSeed, planOrdinal, previousGoalId, revision, excludedTargets, excludedSteps, live, combat, allowOffscreen, admitted, viability, null);
	}
	private Result plan(long profileId, int level, int activeClassId, String currentAnchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter, long deterministicSeed, long planOrdinal, long previousGoalId, long revision, Set<String> excludedTargets, Set<String> excludedSteps, PhantomTopologyPoint live, PlanningCombat combat, boolean allowOffscreen, java.util.function.Predicate<Candidate> admitted, Map<Integer, Boolean> viability, Candidate exactPartyCandidate)
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
		if (exactPartyCandidate != null) { candidates.add(exactPartyCandidate); }
		final List<String> rejections = new ArrayList<>();
		final Set<String> seenCandidates = new java.util.HashSet<>();
		final int[] offscreenAttempts = { 0 };
		for (int radius : exactPartyCandidate == null ? LOWER_LEVEL_TIERS : List.<Integer>of())
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
					if (denied) { reject(rejections, candidate.target().npc().npcId(), candidate.anchor().id(), "combat_or_current_exclusion"); return true; }
					if (!admitted.test(candidate)) { reject(rejections, candidate.target().npc().npcId(), candidate.anchor().id(), "travel.insufficient_adena"); return true; }
					return false;
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
			0);
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
