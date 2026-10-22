/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.topology.*;
import org.l2jmobius.tests.phantoms.*;

/** A real planner-entry regression; no native actor, database or target fabrication. */
public final class PhantomHistoricalIdleVisible068Checks implements PhantomTestSuite
{
	@Override public String id() { return "historical-idle-visible068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-historical-idle-enters-existing-local-planner", _ -> predecessor());
		registry.add("02-foreign-predecessor-rejected-and-calendar-retained", _ -> fences());
	}
	public static void main(String[] args)
	{
		predecessor(); fences();
		System.out.println("OWN068_HISTORICAL_IDLE_VISIBLE_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static PhantomGoal idle()
	{
		final var owner = new PhantomDomainRef("profile", "5");
		return new PhantomGoal(55, PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, owner, owner, 1, 0,
			"background.idle", List.of(), new PhantomDomainRef("topology.anchor", "anchor"), "background.history", 500, 0, 0, 0, Map.of(), "planner.target_or_route.absent", 7);
	}
	private static PhantomBackgroundState state()
	{
		return new PhantomBackgroundState(State.MATERIALIZED, new Identity(5, 50, 0, 44, 1), new Progress(3, 367, 14, 0),
			new Vitals(101, 186, 53, 53, 0, 0), new Position(0, 1, 2, 3, 0, "anchor"),
			new CombatFacts(ModelKind.MELEE, 10, 10, 10, 10, 300, 300, 1, 1, 1, 1, 0, 1, 1, 1, 1), Loadout.none(),
			new InventoryFacts(List.of(57), List.of(), "unchanged", 0, 1_000_000, 0, 100), List.of(), new Clock(123, 0, 0), Receipt.empty(), new Hashes("k", "t", "p", "c"));
	}
	private static PhantomHistoricalBackgroundPlanner planner()
	{
		final var knowledge = PhantomGameKnowledgeService.inertForTesting("t");
		PhantomAssertions.assertTrue(knowledge.start(), "Inert knowledge must start without a native runtime.");
		final var backend = new L2jTopologyValidationBackend();
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "idle-test", 1, 1, List.of(), List.of(), List.of(), backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public Hashes hashes() { return state().hashes(); }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new AssertionError("No capture."); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState value) { throw new AssertionError("No actor."); }
			@Override public FarmInput farmInput(PhantomBackgroundState value, PhantomBackgroundGoalSpec goal) { throw new AssertionError("No farming."); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState value, PhantomBackgroundGoalSpec goal, long budget) { throw new AssertionError("No travel."); }
			@Override public Optional<Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { throw new AssertionError("No recovery."); }
			@Override public List<AutoGetSkill> autoGetSkills(Identity identity, int level) { throw new AssertionError("No skill grant."); }
		};
		return new PhantomHistoricalBackgroundPlanner(knowledge.query(), topology, authority);
	}
	private static void predecessor()
	{
		final var before = state();
		final var result = planner().replanVisibleLocal(5, before, idle(), 123, 9, new PhantomTopologyPoint(1, 2, 3, 0), Set.of(), Set.of());
		PhantomAssertions.assertEquals("planner.ingress.absent", result.reasonKey(), "Canonical historical idle must reach the existing local planner, whose missing topology stays fail closed.");
		PhantomAssertions.assertEquals(state(), before, "Planning cannot mutate native/earned baseline.");
	}
	private static void fences()
	{
		final var planner = planner(); final var idle = idle();
		for (var foreign : List.of(idle.withStatus(PhantomGoalStatus.COMPLETED),
			new PhantomGoal(55, "idle.foreign", PhantomGoalStatus.ACTIVE, idle.subject(), idle.target(), 1, 0, "background.idle", List.of(), idle.selectedAnchor(), "background.history", 500, 0, 0, 0, Map.of(), "foreign", 7),
			new PhantomGoal(55, idle.goalType(), PhantomGoalStatus.ACTIVE, idle.subject(), idle.target(), 1, 0, "background.idle", List.of(), idle.selectedAnchor(), "background.history", 500, 0, 0, 0, Map.of("foreign", 1L), "foreign", 7)))
		{
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> planner.replanVisibleLocal(5, state(), foreign, 123, 9, new PhantomTopologyPoint(1, 2, 3, 0), Set.of(), Set.of()), "Foreign/inactive/malformed predecessor cannot publish visible farming.");
		}
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> planner.replanVisibleLocal(6, state(), idle, 123, 9, new PhantomTopologyPoint(1, 2, 3, 0), Set.of(), Set.of()), "A canonical idle belonging to another profile cannot become a predecessor.");
		final var complete = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "request", 123, 100, 102, 102, 8, 2, 1, 1, 1, idle.goalId(), idle.revision(), "plan", 1, state().hashes(), "");
		final var replanned = complete.withPlan(idle.goalId(), idle.revision() + 1, complete.planOrdinal() + 1, "replacement", 1, 1);
		PhantomAssertions.assertEquals(complete.cursorEpochMinute(), replanned.cursorEpochMinute(), "Visible publication cannot award a historical minute.");
		PhantomAssertions.assertEquals(complete.requestId(), replanned.requestId(), "Visible publication keeps the same calendar request.");
		PhantomAssertions.assertEquals(complete.status(), replanned.status(), "Completed history cannot reopen.");
		PhantomAssertions.assertEquals(complete.intervalOrdinal(), replanned.intervalOrdinal(), "Visible publication cannot replay earned intervals.");
		PhantomAssertions.assertEquals(complete.goalId(), replanned.goalId(), "The original root identity is retained.");
		PhantomAssertions.assertEquals(complete.goalRevision() + 1, replanned.goalRevision(), "Visible publication advances the same root revision once.");
		PhantomAssertions.assertEquals(complete.planOrdinal() + 1, replanned.planOrdinal(), "Visible publication advances the existing plan ordinal once.");
	}
}
