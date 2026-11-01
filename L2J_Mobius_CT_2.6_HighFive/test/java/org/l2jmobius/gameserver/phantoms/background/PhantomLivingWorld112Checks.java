/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomLivingWorld112Knowledge;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** Production planner regressions without a database, native actors or rewards. */
public final class PhantomLivingWorld112Checks
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK112_" + reason); } }
	private static PhantomBackgroundState state()
	{
		return new PhantomBackgroundState(State.READY, new Identity(5, 50, 0, 44, 1), new Progress(3, 367, 14, 0),
			new Vitals(101, 186, 53, 53, 0, 0), new Position(0, 1, 2, 3, 0, "anchor"),
			new CombatFacts(ModelKind.MELEE, 10, 10, 10, 10, 300, 300, 1, 1, 1, 1, 0, 1, 1, 1, 1), Loadout.none(),
			new InventoryFacts(List.of(57), List.of(), "unchanged", 0, 1_000_000, 0, 100), List.of(), new Clock(123, 0, 0), Receipt.empty(), new Hashes("k", "t", "p", "c"));
	}
	private record Fixture(PhantomHistoricalBackgroundPlanner planner, java.util.concurrent.atomic.AtomicBoolean valid, java.util.concurrent.atomic.AtomicInteger probes) {}
	private static Fixture fixture()
	{
		final var knowledge = PhantomLivingWorld112Knowledge.query();
		final var valid = new java.util.concurrent.atomic.AtomicBoolean();
		final var probes = new java.util.concurrent.atomic.AtomicInteger();
		final var backend = new PhantomTopologyValidationBackend()
		{
			@Override public int mapRegionLocId(int x, int y) { return 0; }
			@Override public Optional<NpcFact> npc(int id) { return Optional.of(new NpcFact(id, "Monster", true)); }
			@Override public List<SpawnFact> spawns(int id, int limit) { return List.of(new SpawnFact(id, new PhantomTopologyPoint(1, 2, 3, 0))); }
			@Override public Optional<DoorFact> door(int id) { return Optional.empty(); }
			@Override public DoorState doorState(int id) { return DoorState.MISSING; }
			@Override public boolean sourceExists(String path) { return path.equals("fixture"); }
		};
		final var point = new PhantomTopologyPoint(1, 2, 3, 0);
		final var node = new PhantomTopologyNode("field", PhantomTopologyNodeKind.FARMING_AREA, 0, PhantomTopologyArea.pointRadius(point, 100), null, List.of(), List.of("fixture"));
		final var anchor = new PhantomTopologyAnchor("anchor", PhantomTopologyAnchorRole.FARMING, node.id(), point, 20001, null, 0, List.of(), List.of("fixture"));
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "living112", 1, 1, List.of(node), List.of(anchor), List.of(), backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var authority = new PhantomBackgroundAuthority()
		{
			@Override public Hashes hashes() { return state().hashes(); }
			@Override public boolean viableTarget(PlanningCombat combat, int npcId) { probes.incrementAndGet(); return valid.get(); }
			@Override public PhantomBackgroundState capture(long id, Player player, PhantomGoal goal, PhantomBackgroundState previous) { throw new AssertionError("No capture"); }
			@Override public boolean matchesRuntime(Player player, PhantomBackgroundState value) { throw new AssertionError("No actor"); }
			@Override public FarmInput farmInput(PhantomBackgroundState value, PhantomBackgroundGoalSpec goal) { throw new AssertionError("No rewards"); }
			@Override public TravelAdvance advanceTravel(PhantomBackgroundState value, PhantomBackgroundGoalSpec goal, long budget) { throw new AssertionError("No travel"); }
			@Override public Optional<Position> canonicalRecoveryPosition(int x, int y, int z, int instance, int heading) { throw new AssertionError("No recovery"); }
			@Override public List<AutoGetSkill> autoGetSkills(Identity identity, int level) { throw new AssertionError("No grants"); }
		};
		return new Fixture(new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority), valid, probes);
	}
	private static PhantomGoal idle()
	{
		return idle(5);
	}
	private static PhantomGoal idle(long profileId)
	{
		final var owner = new PhantomDomainRef("profile", Long.toString(profileId));
		return new PhantomGoal(55, PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, owner, owner, 1, 0,
			"background.idle", List.of(), new PhantomDomainRef("topology.anchor", "anchor"), "background.history", 500, 0, 0, 0, Map.of(), "planner.target_or_route.absent", 7);
	}
	private static PhantomBackgroundCatchupState catchup(Fixture f, long from, long to)
	{
		final var generation = f.planner().generation();
		return new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING, "request112", 11211201, from, to, from, 4, 0, 7,
			generation.knowledgeGeneration(), generation.topologyGeneration(), 55, 7, "stable", 1, state().hashes(), "");
	}
	private static PhantomHistoricalBackgroundPlanner.Result reconsider(Fixture f, PhantomBackgroundCatchupState cursor, boolean accepted)
	{
		return PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), 5, state(), idle(), cursor, () -> accepted);
	}
	private static void stableAndWake()
	{
		final var f = fixture();
		check(f.planner().remainsSuitable(state(), idle()), "CANONICAL_IDLE_REJECTED_AS_FARM");
		var cursor = catchup(f, 0, 1100);
		final var codec = new PhantomBackgroundCatchupStateCodec();
		for (int minute = 0; minute < 1000; minute++)
		{
			if (minute == 500) { cursor = codec.decode(codec.encode(cursor)); }
			final var result = reconsider(f, cursor, false);
			check(result.ready() && result.goal().equals(idle()) && result.planIdentity().equals("stable"), "IDLE_REVISION_OR_PLAN_CHURN");
			cursor = cursor.advanceTo(cursor.cursorEpochMinute() + 1);
		}
		check(cursor.cursorEpochMinute() == 1000 && cursor.planOrdinal() == 4 && cursor.goalRevision() == 7, "CURSOR_OR_ORDINAL_RESET");
		check(f.probes().get() > 0 && f.probes().get() <= 17, "UNBOUNDED_OR_PERMANENT_IDLE");
		final int probes = f.probes().get();
		f.valid().set(true);
		PhantomHistoricalBackgroundPlanner.Result wake = null;
		for (int minute = 0; minute < 60; minute++)
		{
			cursor = codec.decode(codec.encode(cursor));
			wake = reconsider(f, cursor, false);
			if (PhantomBackgroundGoalSpec.GOAL_TYPE.equals(wake.goal().goalType())) { break; }
			cursor = cursor.advanceTo(cursor.cursorEpochMinute() + 1);
		}
		check(wake != null && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(wake.goal().goalType()) && wake.goal().goalId() == 55 && wake.goal().revision() == 8, "IDLE_NEVER_WOKE");
		check(f.probes().get() == probes + 1 && cursor.cursorEpochMinute() <= 1060, "WAKE_SEARCH_BUDGET");
		System.out.println("PASS IDLE_1000_MIN_NO_TARGET cursor=0->1000 revision=7->7 ordinal=4->4 goalWrites=0 searches=" + probes + "; codecRestart=500; WAKE cursor=" + cursor.cursorEpochMinute() + " revision=8");
	}
	private static void fences()
	{
		final var f = fixture(); final var due = catchup(f, 5, 65);
		check(!reconsider(f, due, true).ready() && f.probes().get() == 0, "ACCEPTED_OWNER_REPLANNED");
		check(!PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), 6, state(), idle(), due, () -> false).ready(), "FOREIGN_PROFILE_HELD");
		check(!PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), 5, state(), idle(6), due, () -> false).ready(), "FOREIGN_IDLE_ROOT_ADOPTED");
		check(!PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), 5, state(), idle().withStatus(PhantomGoalStatus.COMPLETED), due, () -> false).ready(), "TERMINAL_GOAL_HELD");
		check(!reconsider(f, due.failed("STOP"), false).ready(), "STOP_OR_FAILED_CURSOR_HELD");
		check(!reconsider(f, due.withPlan(55, 8, 5, "new", 1, 1), false).ready(), "STALE_GOAL_REVISION_HELD");
		check(!reconsider(f, due.withPlan(55, 7, 4, "stable", 2, 1), false).ready(), "STALE_KNOWLEDGE_HELD");
		check(!PhantomHistoricalBackgroundService.canResumeGeneration(due, new PhantomHistoricalBackgroundPlanner.PlanningGeneration(1, 2, state().hashes())), "TOPOLOGY_ONLY_RECOVERY_FALSE_SUCCESS");
		check(!PhantomHistoricalBackgroundService.canResumeGeneration(due, new PhantomHistoricalBackgroundPlanner.PlanningGeneration(2, 1, state().hashes())), "KNOWLEDGE_ONLY_RECOVERY_FALSE_SUCCESS");
		check(!f.planner().remainsVisibleSuitable(state(), idle(), new PhantomTopologyPoint(1, 2, 3, 0)), "IDLE_BECAME_VISIBLE_FARM");
		for (String reason : List.of("catchup.replan.prerequisite_changed", "catchup.replan.generation_changed"))
		{
			check(PhantomHistoricalBackgroundService.historicalPlanningFailureStatus(reason) == PhantomHistoricalBackgroundService.ResultStatusCode.RETRY, "TRANSIENT_RELOAD_BECOMES_TERMINAL");
		}
		System.out.println("PASS stale goal/generation, foreign profile, terminal/STOP, accepted intent and visible farm fences; no interval is committed by planning");
	}
	private static void loadBound()
	{
		final var f = fixture(); final var base = state();
		final var states = new PhantomBackgroundState[5000]; final var goals = new PhantomGoal[5000]; final var cursors = new PhantomBackgroundCatchupState[5000];
		for (int index = 0; index < states.length; index++)
		{
			final long id = index + 1;
			states[index] = new PhantomBackgroundState(base.state(), new Identity(id, 1000000 + index, 0, 44, 1), base.progress(), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
			goals[index] = idle(id); cursors[index] = catchup(f, 0, 121);
		}
		final long start = System.nanoTime(); final int threads = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
		for (int minute = 0; minute < 121; minute++)
		{
			for (int index = 0; index < states.length; index++)
			{
				final var result = PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), index + 1, states[index], goals[index], cursors[index], () -> false);
				check(result.ready() && result.goal().equals(goals[index]), "LOAD_IDLE_CHURN");
				cursors[index] = cursors[index].advanceTo(minute + 1);
			}
		}
		check(f.probes().get() >= 10000 && f.probes().get() <= 15000, "LOAD_SEARCH_BOUND");
		System.out.println("PASS LOAD_BOUND identities=5000 virtualMinutes=121 planningCalls=605000 goalChanges=0 authorityProbes=" + f.probes().get() + " elapsedMs=" + (System.nanoTime() - start) / 1000000 + " threadDelta=" + (java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount() - threads) + "; planner-stage-only; no Player/DB");
	}
	public static void main(String[] args)
	{
		stableAndWake(); fences(); loadBound();
		System.out.println("TASK112_IDLE_PLANNING_PASS DB=0 ACTORS=0; transaction/native cursor persistence not simulated");
	}
}
