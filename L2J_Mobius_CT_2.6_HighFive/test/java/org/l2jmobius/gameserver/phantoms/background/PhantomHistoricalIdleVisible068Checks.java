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
		registry.add("03-recovery-control-accepts-only-exact-current-root", _ -> recoveryControl());
		registry.add("04-idle-travel-keeps-solo-farm-closed", _ -> travelAdmission());
		registry.add("05-native-walk-checkpoints-retain-bounds", _ -> walkBudget());
		registry.add("06-idle-native-ingress-uses-existing-connected-anchor", _ -> idleIngress());
	}
	public static void main(String[] args)
	{
		predecessor(); fences(); recoveryControl(); travelAdmission(); walkBudget(); idleIngress();
		System.out.println("OWN068_HISTORICAL_IDLE_VISIBLE_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static PhantomGoal idle()
	{
		final var owner = new PhantomDomainRef("profile", "5");
		return new PhantomGoal(55, PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, owner, owner, 1, 0,
			"background.idle", List.of(), new PhantomDomainRef("topology.anchor", "anchor"), "background.history", 500, 0, 0, 0, Map.of(), "planner.target_or_route.absent", 7);
	}
	private static PhantomTopologyAnchor ingressAnchor(String id, int x, int instance)
	{
		return new PhantomTopologyAnchor(id, PhantomTopologyAnchorRole.FARMING, id + ".node", new PhantomTopologyPoint(x, 0, 0, instance), null, null, 0, List.of(), List.of("fixture"));
	}
	private static void idleIngress()
	{
		final var live = new PhantomTopologyPoint(0, 0, 0, 0); final var goal = ingressAnchor("goal", 10000, 0);
		final var disconnected = ingressAnchor("disconnected", 10, 0); final var foreign = ingressAnchor("foreign", 20, 1);
		final var entry = ingressAnchor("entry", 2000, 0); final var farther = ingressAnchor("farther", 3000, 0);
		final var anchors = List.of(goal, disconnected, foreign, farther, entry);
		PhantomAssertions.assertEquals(entry, PhantomVisibleFarmTravel.idleIngress(anchors, live, goal.id(), 12000, anchor -> !anchor.id().equals(disconnected.id())), "Idle native travel must enter the nearest same-instance existing itinerary instead of greedily aiming at its distant final anchor.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.idleIngress(anchors, live, goal.id(), 1000, anchor -> !anchor.id().equals(disconnected.id())), "An ingress cannot enlarge the existing local-distance bound or borrow another instance.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.idleIngress(anchors, live, goal.id(), 12000, _ -> false), "A disconnected anchor cannot become an implicit topology edge.");
		final var calls = new java.util.concurrent.atomic.AtomicInteger();
		final var many = java.util.stream.IntStream.rangeClosed(1, 32).mapToObj(index -> ingressAnchor("candidate." + index, index * 100, 0)).toList();
		PhantomVisibleFarmTravel.idleIngress(many, live, goal.id(), 12000, _ -> { calls.incrementAndGet(); return false; });
		PhantomAssertions.assertEquals(16, calls.get(), "A missing entry cannot trigger unbounded itinerary queries in a shared scheduler slice.");
	}
	private static void travelAdmission()
	{
		PhantomAssertions.assertEquals("anchor", PhantomVisibleFarmTravel.travelSpec(5, idle()).anchorId(), "Canonical idle may physically return to its exact source anchor.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomBackgroundGoalSpec.parse(idle()), "Travel must not admit idle to solo farming.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomVisibleFarmTravel.travelSpec(6, idle()), "A different profile cannot borrow idle travel.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomVisibleFarmTravel.travelSpec(5, idle().withStatus(PhantomGoalStatus.COMPLETED)), "Terminal idle cannot start movement.");
	}
	private static void walkBudget()
	{
		final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		final var origin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 0, 0, 0);
		final var checkpoint = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(1000, 0, 0, 0);
		final var destination = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(25_000, 0, 0, 0);
		final var capability = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		final var route = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.COMPUTED, origin, checkpoint, List.of(checkpoint), capability, 1, false, 1, 100_000);
		final var budget = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, destination, 1, 120, policy);
		PhantomAssertions.assertTrue(budget.admit(route, 2), "A proven local route fits the unchanged corridor bounds.");
		PhantomAssertions.assertFalse(budget.checkpoint(origin, 3), "PATH_FOUND or wrong feet cannot renew the native phase.");
		PhantomAssertions.assertTrue(budget.checkpoint(checkpoint, 4), "Only physical released checkpoint arrival admits another episode.");
		PhantomAssertions.assertFalse(budget.admit(route, 5), "A previously reached checkpoint cannot cause a loop.");
		PhantomAssertions.assertTrue(budget.expired(4 + policy.maximumAttemptDurationNanos()), "A retry cannot renew the 120 second episode.");
		final var charged = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, destination, 1, 120, policy);
		for (int index = 0; index < 64; index++) { PhantomAssertions.assertTrue(charged.admit(route, 2), "Each retry charges its whole proven corridor."); }
		PhantomAssertions.assertFalse(charged.admit(route, 2), "Route retries remain bounded even before reaching a checkpoint.");
		final var detour = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.COMPUTED, origin, checkpoint, List.of(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 40_000, 0, 0), checkpoint), capability, 1, false, 2, 100_000);
		final var distanceBudget = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, destination, 1, 120, policy);
		PhantomAssertions.assertTrue(distanceBudget.admit(detour, 2), "A proven detour fits the actual distance cap.");
		PhantomAssertions.assertFalse(distanceBudget.admit(detour, 3), "A retry charges all detour segments, not the short checkpoint displacement.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomVisibleFarmTravel.NativeWalkBudget(origin, destination, 1, 0, policy), "Native speed cannot be fabricated.");
		final var shortGoal = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(1000, 0, 0, 0);
		final var provenDetour = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.COMPUTED, origin, shortGoal, List.of(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 4000, 0, 0), shortGoal), capability, 1, false, 2, 100_000);
		final var timedDetour = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, shortGoal, 1, 100, policy);
		PhantomAssertions.assertTrue(timedDetour.admit(provenDetour, 2), "A fully proven detour is admitted under the existing distance and waypoint bounds.");
		PhantomAssertions.assertTrue(timedDetour.checkpoint(shortGoal, java.util.concurrent.TimeUnit.SECONDS.toNanos(90)), "A paid-for proven detour must budget its actual native corridor length, while the original 120 second phase and 30 minute total ceiling remain closed.");
		final var rejectedDetour = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, shortGoal, 1, 100, policy);
		final var repeatedDestination = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.COMPUTED, origin, origin, List.of(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 4000, 0, 0), origin), capability, 1, false, 2, 100_000);
		PhantomAssertions.assertFalse(rejectedDetour.admit(repeatedDestination, 2), "A proven loop to a visited endpoint is never admitted or allowed to extend time.");
		PhantomAssertions.assertTrue(rejectedDetour.expired(java.util.concurrent.TimeUnit.SECONDS.toNanos(60)), "Rejected routes cannot extend the original work deadline.");
		final var hardBound = new PhantomVisibleFarmTravel.NativeWalkBudget(origin, destination, 1, 1, policy);
		var prior = origin;
		for (int index = 1; index <= 18; index++)
		{
			final var next = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(index * 100, 0, 0, 0);
			final var leg = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.COMPUTED, prior, next, List.of(next), capability, 1, false, index, 100_000);
			PhantomAssertions.assertTrue(hardBound.admit(leg, 2 + java.util.concurrent.TimeUnit.SECONDS.toNanos((index - 1) * 100L)), "A new fully proven phase is admitted before its original hard deadline.");
			PhantomAssertions.assertEquals(index < 18, hardBound.checkpoint(next, 1 + java.util.concurrent.TimeUnit.SECONDS.toNanos(index * 100L)), "Physical checkpoints cannot reset the original 30 minute ceiling.");
			prior = next;
		}
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
	private static void recoveryControl()
	{
		final var current = idle();
		PhantomAssertions.assertTrue(PhantomBackgroundService.recoveryGoalCurrent(5, current, current), "Canonical current historical idle must be stoppable for normal publication.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.recoveryGoalCurrent(6, current, current), "A foreign profile cannot authorize recovery control.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.recoveryGoalCurrent(5, current, current.withStatus(PhantomGoalStatus.ACTIVE)), "An otherwise identical newer revision cannot authorize a stale caller.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.recoveryGoalCurrent(5, current, null), "An absent persisted goal cannot authorize recovery control.");
		final var inactive = current.withStatus(PhantomGoalStatus.COMPLETED);
		PhantomAssertions.assertFalse(PhantomBackgroundService.recoveryGoalCurrent(5, inactive, inactive), "Inactive idle remains ineligible for recovery publication.");
		for (String type : List.of(current.goalType(), "idle.foreign"))
		{
			final var malformed = new PhantomGoal(55, type, PhantomGoalStatus.ACTIVE, current.subject(), current.target(), 1, 0, "background.idle", List.of(), current.selectedAnchor(), "background.history", 500, 0, 0, 0, Map.of("foreign", 1L), "foreign", 7);
			PhantomAssertions.assertFalse(PhantomBackgroundService.recoveryGoalCurrent(5, malformed, malformed), "Malformed or foreign idle cannot bypass the canonical predecessor validator.");
		}
		final var farm = new PhantomGoal(56, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, current.subject(), current.target(), 1, 0, "background.farm", List.of(), current.selectedAnchor(), "farm.background", 500, 0, 0, 0, Map.of(), "legacy", 1);
		PhantomAssertions.assertTrue(PhantomBackgroundService.recoveryGoalCurrent(5, farm, farm), "Existing exact farm type-filter control semantics remain unchanged.");
	}
}
