package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority.TravelAdvance.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.TargetQuery;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

public class PhantomNormalGatekeeperTravelSuite implements PhantomTestSuite
{
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomTopologyQuery _topology;

	@Override
	public String id()
	{
		return "normal-gatekeeper-travel";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		final var backend = new L2jTopologyValidationBackend();
		_topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), backend, PhantomTopologyPolicy.productionDefaults()).load(1), backend, new PhantomTopologyMetrics());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("factual-dwarf-routes", this::factualDwarfRoutes);
		registry.add("native-logical-fee", this::nativeLogicalFee);
		registry.add("authority-free-gk-completion", this::authorityFreeCompletion);
		registry.add("authority-paid-and-insufficient", this::authorityPaidAndInsufficient);
		registry.add("planner-dwarf-band-witnesses", this::plannerDwarfWitnesses);
		registry.add("catalog-hash-and-non-adena-rejection", this::catalogHashAndNonAdenaRejection);
		registry.add("composite-route-and-epoch-required", this::compositeRouteAndEpochRequired);
	}

	private void catalogHashAndNonAdenaRejection(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var enabled = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var topologyOnly = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce);
			PhantomAssertions.assertFalse(enabled.hashes().topology().equals(topologyOnly.hashes().topology()), "GK catalog hash did not participate in route authority generation.");
			final Path invalid = Files.createTempFile("d2-non-adena-", ".xml");
			try
			{
				Files.writeString(invalid, Files.readString(Path.of("data/phantoms/travel/high-five-normal-gk.xml")).replace("feeId=\"57\"", "feeId=\"4037\""));
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomNormalGatekeeperTravel.load(invalid, fixture.topology()), "Non-Adena GK fee entered the admitted catalog.");
			}
			finally
			{
				Files.deleteIfExists(invalid);
			}
		}
	}

	private void compositeRouteAndEpochRequired(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var leg = travel.legs().stream().filter(value -> value.toAnchorId().equals("generated.farm.475038779aaee6a2e82aa06b.anchor")).findFirst().orElseThrow();
			final var composite = travel.route("population.ingress.dwarf.01", "generated.farm.77743197e238ebb0e1e3eac2.anchor").orElseThrow();
			final int index = java.util.stream.IntStream.range(0, composite.size()).filter(value -> composite.get(value).id().equals(leg.id())).findFirst().orElseThrow();
			PhantomAssertions.assertTrue((index > 0) && (index < composite.size() - 1) && (composite.getFirst().type() == PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND) && (composite.getLast().type() == PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND), "Shared route did not combine topology, GK, topology.");
			final var source = fixture.topology().findAnchor(leg.fromAnchorId()).orElseThrow();
			final var state = PhantomBackgroundSuite.productionState(source, authority.hashes());
			final var goal = new PhantomBackgroundGoalSpec(20533, leg.toAnchorId(), 0, 0, 0, 0, 0);
			PhantomAssertions.assertEquals(Status.UNSUPPORTED_CONDITION, authority.advanceTravel(state, goal, 60_000).status(), "NORMAL GK advanced without an explicit logical epoch minute.");
		}
	}

	private void nativeLogicalFee(PhantomTestContext context)
	{
		final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		final var leg = travel.legs().stream().filter(value -> value.feeCount() == 970).findFirst().orElseThrow();
		final long monday = ZonedDateTime.of(2026, 9, 21, 20, 0, 0, 0, ZoneId.systemDefault()).toEpochSecond() / 60;
		PhantomAssertions.assertEquals(0L, PhantomNormalGatekeeperTravel.fee(leg, 0, 19, monday), "Main class below free threshold paid GK fee.");
		PhantomAssertions.assertEquals(485L, PhantomNormalGatekeeperTravel.fee(leg, 1, 19, monday), "Subclass missed Monday discount.");
		PhantomAssertions.assertEquals(970L, PhantomNormalGatekeeperTravel.fee(leg, 0, 41, monday - 60), "Off-hour paid GK fee changed.");
	}

	private void authorityFreeCompletion(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var leg = travel.legs().stream().filter(value -> value.toAnchorId().equals("generated.farm.088e8bc59766f22991a08e5d.anchor")).findFirst().orElseThrow();
			PhantomAssertions.assertEquals(leg.id(), travel.route(leg.fromAnchorId(), leg.toAnchorId()).orElseThrow().getFirst().id(), "D1 GK source chose another first leg.");
			final var source = fixture.topology().findAnchor(leg.fromAnchorId()).orElseThrow();
			PhantomBackgroundState state = PhantomBackgroundSuite.productionState(source, authority.hashes());
			state = state.after(new PhantomBackgroundState.Progress(19, 0, 0, 0), state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt());
			final var goal = new PhantomBackgroundGoalSpec(20533, leg.toAnchorId(), 0, 0, 0, 0, 0);
			final var first = authority.advanceTravel(state, goal, 60_000, 1_000_000);
			PhantomAssertions.assertEquals(Status.PARTIAL, first.status(), "D1 GK partial phase did not retain departure anchor.");
			PhantomAssertions.assertEquals(0L, first.feeAdena(), "Partial GK charged fee.");
			final var next = state.after(state.progress(), state.vitals(), first.position(), state.inventory(), state.autoGetSkills(), first.clock(), state.receipt());
			final var finalStep = authority.advanceTravel(next, goal, 60_000, 1_000_000);
			PhantomAssertions.assertEquals(Status.ARRIVED, finalStep.status(), "D1 GK free completion did not arrive.");
			PhantomAssertions.assertEquals(leg.toAnchorId(), finalStep.position().committedAnchorId(), "D1 GK destination anchor changed.");
			PhantomAssertions.assertEquals(0L, finalStep.feeAdena(), "D1 GK free completion charged fee.");
		}
	}

	private void authorityPaidAndInsufficient(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var leg = travel.legs().stream().filter(value -> value.toAnchorId().equals("generated.farm.475038779aaee6a2e82aa06b.anchor")).findFirst().orElseThrow();
			final var source = fixture.topology().findAnchor(leg.fromAnchorId()).orElseThrow();
			final var base = PhantomBackgroundSuite.productionState(source, authority.hashes());
			final var identity = new PhantomBackgroundState.Identity(base.identity().profileId(), base.identity().characterObjectId(), 1, base.identity().activeClassId(), base.identity().raceOrdinal());
			final var adena = new PhantomBackgroundState.ItemObject(100, 57, 12000, true, PhantomBackgroundState.ItemLocation.INVENTORY);
			final var inventory = PhantomBackgroundState.InventoryFacts.sorted(List.of(57), List.of(adena), "", 0, 0, 1, 1);
			final var paid = new PhantomBackgroundState(base.state(), identity, new PhantomBackgroundState.Progress(20, 0, 0, 0), base.vitals(), base.position(), base.combat(), base.loadout(), inventory, base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
			final var goal = new PhantomBackgroundGoalSpec(20533, leg.toAnchorId(), 0, 0, 0, 0, 0);
			final long wednesday = ZonedDateTime.of(2026, 9, 23, 10, 0, 0, 0, ZoneId.systemDefault()).toEpochSecond() / 60;
			final var first = authority.advanceTravel(paid, goal, 60_000, wednesday);
			PhantomAssertions.assertEquals(Status.PARTIAL, first.status(), "Paid GK travel skipped its partial phase.");
			final var paidReady = paid.after(paid.progress(), paid.vitals(), first.position(), paid.inventory(), paid.autoGetSkills(), first.clock(), paid.receipt());
			final var completed = authority.advanceTravel(paidReady, goal, 60_000, wednesday);
			PhantomAssertions.assertEquals(Status.ARRIVED, completed.status(), "Paid D1 GK transition did not arrive.");
			PhantomAssertions.assertEquals(12000L, completed.feeAdena(), "Paid GK fee differed from exact native fee.");
			final var poorInventory = PhantomBackgroundState.InventoryFacts.sorted(List.of(57), List.of(new PhantomBackgroundState.ItemObject(100, 57, 11999, true, PhantomBackgroundState.ItemLocation.INVENTORY)), "", 0, 0, 1, 1);
			final var poor = new PhantomBackgroundState(paid.state(), paid.identity(), paid.progress(), paid.vitals(), paid.position(), paid.combat(), paid.loadout(), poorInventory, paid.autoGetSkills(), paid.clock(), paid.receipt(), paid.hashes());
			final var poorReady = poor.after(poor.progress(), poor.vitals(), first.position(), poor.inventory(), poor.autoGetSkills(), first.clock(), poor.receipt());
			final var rejected = authority.advanceTravel(poorReady, goal, 60_000, wednesday);
			PhantomAssertions.assertEquals(Status.INSUFFICIENT_ADENA, rejected.status(), "Insufficient Adena did not fail closed.");
			PhantomAssertions.assertEquals(poorReady.position(), rejected.position(), "Insufficient Adena moved canonical position.");
			PhantomAssertions.assertEquals(poorReady.clock(), rejected.clock(), "Insufficient Adena advanced travel clock.");
		}
	}

	private void plannerDwarfWitnesses(PhantomTestContext context) throws Exception
	{
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), fixture.topology());
			final var authority = new L2jPhantomBackgroundAuthority(fixture::knowledge, fixture::topology, fixture::progression, fixture::commerce, travel);
			final var planner = new PhantomHistoricalBackgroundPlanner(fixture.knowledge(), fixture.topology(), authority);
			final var ingress = fixture.topology().findAnchor("population.ingress.dwarf.01").orElseThrow();
			for (var witness : List.of(new Object[] {11, 19, "generated.farm.088e8bc59766f22991a08e5d.anchor"}, new Object[] {20, 39, "generated.farm.475038779aaee6a2e82aa06b.anchor"}))
			{
				final var anchor = fixture.topology().findAnchor((String) witness[2]).orElseThrow();
				final var base = PhantomBackgroundSuite.productionState(ingress, authority.hashes());
				boolean eligible = false;
				boolean selected = false;
				for (int level = (int) witness[0]; level <= (int) witness[1]; level++)
				{
					final var state = base.after(new PhantomBackgroundState.Progress(level, 0, 0, 0), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt());
					final var targets = fixture.knowledge().suitableTargets(new TargetQuery(Math.max(1, level - 2), level + 2, level, null, null, Set.of(NpcKind.MONSTER), true, true, null, null, null, PageRequest.first(64))).values();
					for (var target : targets)
					{
						if (target.representativeAreas().stream().anyMatch(area -> anchor.nodeId().equals(area.topologyNodeId())) && planner.remainsSuitable(state, goal(target.npc().npcId(), anchor.id())))
						{
							eligible = true;
							for (long seed = 0; seed < 16; seed++)
							{
								final var plan = planner.replan(state.identity().profileId(), state, goal(target.npc().npcId(), anchor.id()), seed, 1);
								if (plan.ready() && anchor.id().equals(plan.spec().anchorId()) && plan.routeEdgeIds().stream().anyMatch(id -> id.startsWith("leg.")))
								{
									selected = true;
									context.record("d2.plannerWitness." + witness[0] + ".level", level);
									context.record("d2.plannerWitness." + witness[0] + ".seed", seed);
									break;
								}
							}
							break;
						}
					}
					if (selected)
					{
						break;
					}
				}
				PhantomAssertions.assertTrue(eligible, "D1 Dwarf coverage witness was not eligible within band " + witness[0] + "-" + witness[1]);
				PhantomAssertions.assertTrue(selected, "D1 Dwarf coverage witness was planner-reachable but never selected within band " + witness[0] + "-" + witness[1]);
			}
		}
	}

	private static PhantomGoal goal(int npcId, String anchorId)
	{
		return new PhantomGoal(123, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "15001501"), new PhantomDomainRef(PhantomBackgroundGoalSpec.NPC_NAMESPACE, Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchorId)), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchorId), "farm.background", 500, 0, 0, 0, java.util.Map.of(), "background.catchup.plan", 0);
	}

	private void factualDwarfRoutes(PhantomTestContext context)
	{
		final PhantomNormalGatekeeperTravel travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), _topology);
		PhantomAssertions.assertEquals(5, travel.legs().size(), "D1 NORMAL GK catalog cardinality changed.");
		for (String transitionId : List.of("transition.e4b886ff65a767c9d77f3d3f", "transition.e904d3aed33a9cecd852c15c"))
		{
			final var leg = travel.legs().stream().filter(value -> value.transitionId().equals(transitionId)).findFirst().orElseThrow();
			final var route = travel.route("population.ingress.dwarf.01", leg.toAnchorId()).orElseThrow();
			PhantomAssertions.assertTrue(route.stream().anyMatch(step -> (step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER) && step.id().equals(leg.id())), "D1 Dwarf witness did not use its factual NORMAL transition.");
		}
	}
}
