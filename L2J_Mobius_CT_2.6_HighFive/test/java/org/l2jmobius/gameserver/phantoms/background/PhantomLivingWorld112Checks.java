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
	private static void loadBound(int profiles, int minutes)
	{
		final var f = fixture(); final var base = state();
		final var states = new PhantomBackgroundState[profiles]; final var goals = new PhantomGoal[profiles]; final var cursors = new PhantomBackgroundCatchupState[profiles];
		for (int index = 0; index < states.length; index++)
		{
			final long id = index + 1;
			states[index] = new PhantomBackgroundState(base.state(), new Identity(id, 1000000 + index, 0, 44, 1), base.progress(), base.vitals(), base.position(), base.combat(), base.loadout(), base.inventory(), base.autoGetSkills(), base.clock(), base.receipt(), base.hashes());
			goals[index] = idle(id); cursors[index] = catchup(f, 0, minutes);
		}
		final long start = System.nanoTime(); final int threads = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
		for (int minute = 0; minute < minutes; minute++)
		{
			for (int index = 0; index < states.length; index++)
			{
				final var result = PhantomHistoricalBackgroundService.reconsiderHistorical(f.planner(), index + 1, states[index], goals[index], cursors[index], () -> false);
				check(result.ready() && result.goal().equals(goals[index]), "LOAD_IDLE_CHURN");
				cursors[index] = cursors[index].advanceTo(minute + 1);
				if (minute == minutes / 2) { final var codec = new PhantomBackgroundCatchupStateCodec(); cursors[index] = codec.decode(codec.encode(cursors[index])); }
			}
		}
		check(f.probes().get() >= profiles * (minutes / 60) && f.probes().get() <= profiles * ((minutes / 60) + 1), "LOAD_SEARCH_BOUND");
		System.out.println("PASS LOAD_BOUND identities=" + profiles + " virtualMinutes=" + minutes + " planningCalls=" + profiles * minutes + " goalChanges=0 authorityProbes=" + f.probes().get() + " elapsedMs=" + (System.nanoTime() - start) / 1000000 + " threadDelta=" + (java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount() - threads) + "; planner-stage-only; no Player/DB");
	}
	public static void main(String[] args)
	{
		if (args.length == 1 && args[0].equals("--paid-travel121")) { paidTravel121(); return; }
		if (args.length == 1 && args[0].equals("--recovery121")) { recoveryAdmission121(); return; }
		if (args.length == 1 && args[0].equals("--idle-handoff121")) { idleHandoff121(); return; }
		stableAndWake(); fences(); loadBound(5000, 121); loadBound(32, 1441);
		System.out.println("TASK112_IDLE_PLANNING_PASS DB=0 ACTORS=0; transaction/native cursor persistence not simulated");
	}
	private static void paidTravel121()
	{
		final var base = state();
		final var paid = new PhantomBackgroundAuthority.TravelAdvance(PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED,
			base.position(), base.clock(), "leg.508c6f61d235328d874037cf", 29000);
		check(PhantomBackgroundService.travelRequiresNative(paid, null), "121_PAID_TRAVEL_SUBMITTED_TO_ZERO_DELTA_POLICY");
		check(!PhantomBackgroundService.travelRequiresNative(new PhantomBackgroundAuthority.TravelAdvance(
			PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, base.position(), base.clock(), "walk", 0), null), "121_FREE_WALK_REQUIRES_NATIVE");
		for (var status : PhantomBackgroundAuthority.TravelAdvance.Status.values())
		{
			if (status == PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED || status == PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL) { continue; }
			check(!PhantomBackgroundService.travelRequiresNative(new PhantomBackgroundAuthority.TravelAdvance(status, base.position(), base.clock(), "leg.closed", 29000), null), "121_REFUSED_TRAVEL_REQUIRES_NATIVE");
		}
		final var running = catchup(fixture(), 500, 515);
		final var failed = running.failed("transaction.state_conflict");
		final var codec = new PhantomBackgroundCatchupStateCodec();
		final var before = codec.encode(failed);
		check(PhantomBackgroundService.paidTravelConflictCandidate(failed), "121_PAID_CONFLICT_NOT_PROBED");
		final var generation = fixture().planner().generation();
		check(PhantomHistoricalBackgroundService.paidTravelConflictGenerationCurrent(failed, generation), "121_FAILED_PAID_CONFLICT_BLOCKED_BY_RUNNING_GENERATION_GATE");
		check(!PhantomHistoricalBackgroundService.paidTravelConflictGenerationCurrent(running, generation)
			&& !PhantomHistoricalBackgroundService.paidTravelConflictGenerationCurrent(failed, new PhantomHistoricalBackgroundPlanner.PlanningGeneration(2, 1, base.hashes()))
			&& !PhantomHistoricalBackgroundService.paidTravelConflictGenerationCurrent(failed, new PhantomHistoricalBackgroundPlanner.PlanningGeneration(1, 2, base.hashes()))
			&& !PhantomHistoricalBackgroundService.paidTravelConflictGenerationCurrent(failed, new PhantomHistoricalBackgroundPlanner.PlanningGeneration(1, 1, new Hashes("changed", "t", "p", "c"))), "121_LEGACY_RUNNING_OR_STALE_GENERATION_ADMITTED");
		check(!PhantomBackgroundService.paidTravelConflictCandidate(running), "121_RUNNING_CLASSIFIED_LEGACY_FAILURE");
		for (String reason : List.of("transaction.item_conflict", "transaction.canonical_mismatch", "STOP", "catchup.authority.unsupported"))
		{
			check(!PhantomBackgroundService.paidTravelConflictCandidate(running.failed(reason)), "121_FOREIGN_FAILURE_PROBED");
		}
		check(!PhantomHistoricalBackgroundService.isRecoverableFailure(failed.failureReason())
			&& !PhantomHistoricalBackgroundService.requiresNativeMaterialization(failed.failureReason()), "121_GENERIC_STATE_CONFLICT_RELAXED");
		check(java.util.Arrays.equals(before, codec.encode(failed)), "121_FAILED_PAYLOAD_OR_DEBT_CHANGED");
		System.out.println("TASK121_PAID_TRAVEL_GATE_PASS native paid leg requires existing handoff; free/refused paths unchanged; DB0 World0");
	}

	private static void idleHandoff121()
	{
		final var before = catchup(fixture(), 500, 515);
		check(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(before.status(), () -> true), "121_RUNNING_IDLE_HANDOFF_REJECTED");
		check(!PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(before.status(), () -> false), "121_RUNNING_IDLE_WITHOUT_OWNER_ADMITTED");
		for (var status : PhantomBackgroundCatchupState.Status.values())
		{
			if (status == PhantomBackgroundCatchupState.Status.RUNNING) { continue; }
			check(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(status, () -> { throw new AssertionError("Unneeded owner/SQL read"); }) == (status == PhantomBackgroundCatchupState.Status.COMPLETE), "121_PENDING_OR_FAILED_IDLE_ADMITTED");
		}
		final var replacement = before.withPlan(before.goalId(), before.goalRevision() + 1, before.planOrdinal() + 1, "visible.replan", before.knowledgeGeneration(), before.topologyGeneration());
		check(replacement.status() == before.status() && replacement.requestId().equals(before.requestId())
			&& replacement.fromEpochMinute() == before.fromEpochMinute() && replacement.targetEpochMinute() == before.targetEpochMinute()
			&& replacement.cursorEpochMinute() == before.cursorEpochMinute() && replacement.intervalOrdinal() == before.intervalOrdinal()
			&& replacement.deterministicSeed() == before.deterministicSeed() && replacement.generation() == before.generation(), "121_VISIBLE_REPLAN_ERASED_HISTORICAL_DEBT");
		System.out.println("TASK121_IDLE_HANDOFF_GATE_PASS running requires exact handoff; pending/failed rejected; COMPLETE skips owner query; real withPlan preserves debt; DB0 World0 NATIVE_NOT_PROVEN");
	}

	private static void recoveryAdmission121()
	{
		final var current = idle();
		final var revised = new PhantomGoal(current.goalId(), current.goalType(), current.status(), current.subject(), current.target(), current.requiredAmount(), current.currentAmount(), current.acquisitionMethod(), current.validSources(), current.selectedAnchor(), current.purposeKey(), current.priority(), current.riskBudget(), current.expenseBudget(), current.deadlineEpochMillis(), current.constraints(), current.reasonKey(), current.revision() + 1);
		check(PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, current, current), "121_CANONICAL_IDLE_DEATH_RECOVERY_REJECTED");
		check(!PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, idle(6), idle(6))
			&& !PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, current, idle(6))
			&& !PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, current, revised)
			&& !PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, current.withStatus(PhantomGoalStatus.COMPLETED), current.withStatus(PhantomGoalStatus.COMPLETED))
			&& !PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, null, null), "121_FOREIGN_STALE_TERMINAL_RECOVERY_ADMITTED");
		final var farm = new PhantomGoal(55, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "5"), new PhantomDomainRef("npc", "20001"), 1, 0,
			"background.farm", List.of(new PhantomDomainRef("background.farm", "20001@anchor")), new PhantomDomainRef("topology.anchor", "anchor"), "background.farm", 500, 0, 0, 0, Map.of(), "farm.ready", 7);
		check(PhantomHistoricalBackgroundService.nativeRecoveryGoalAccepted(5, farm, farm), "121_FARM_RECOVERY_CHANGED");
		System.out.println("TASK121_RECOVERY_GATE_PASS canonical idle/farm; foreign/stale/terminal denied; DB0 ACTORS0 NATIVE_RESTART_NOT_PROVEN");
	}
}
