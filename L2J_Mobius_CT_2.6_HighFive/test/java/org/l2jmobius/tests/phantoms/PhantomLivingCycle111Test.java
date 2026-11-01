/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RestJourney;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RetainedPlan;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

/** Durable cause must precede cancellation of native farm work. DB/Player/World are not fixtures here. */
public final class PhantomLivingCycle111Test
{
	private static final long NOW = 1_000_000;
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK111_" + reason); } }
	private static PhantomGoal root(long revision)
	{
		return new PhantomGoal(7, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20001"), 1, 0,
			"background.farm", List.of(new PhantomDomainRef("background.farm", "20001@field")), new PhantomDomainRef("topology.anchor", "field"), "background.farm", 1, 0, 0, 0, Map.of(), "farm.ready", revision);
	}
	private static RetainedPlan retained()
	{
		final var quote = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(12, 1865, 8, 100)), NOW + 120000);
		return new RetainedPlan(42, 0, quote, new RestJourney(0, RestJourney.cityIntent(root(4), 42, 0, 0, quote, "fixture.city", new PhantomNavigationPoint(1000, 0, 0, 0), NOW)));
	}
	private static boolean attemptYield(RetainedPlan retained, PhantomGoal root, long now, LongPredicate finishTarget) throws Exception
	{
		return PhantomAutonomousMarketProducer.yieldRestJourney(retained, root, now, finishTarget);
	}
	private static void cause() throws Exception
	{
		final var calls = new AtomicInteger(); final LongPredicate action = _ -> { calls.incrementAndGet(); return true; };
		check(!attemptYield(null, root(4), NOW, action), "NO_CAUSE_YIELDS");
		final var saved = retained();
		check(!attemptYield(new RetainedPlan(42, 0, saved.plan(), null), root(4), NOW, action), "QUOTE_ONLY_YIELDS");
		check(!attemptYield(saved, root(5), NOW, action), "STALE_ROOT_YIELDS");
		check(!attemptYield(new RetainedPlan(43, 0, saved.plan(), saved.journey()), root(4), NOW, action), "FOREIGN_ACTOR_YIELDS");
		check(!attemptYield(saved, root(4), saved.recoveryDeadline(), action), "EXPIRED_CHILD_YIELDS");
		check(calls.get() == 0, "INVALID_CAUSE_TOUCHED_FARM");
		System.out.println("PASS living111.cause no retained child/stale root/foreign actor/expiry => no farm cancellation");
	}
	private static void continuation() throws Exception
	{
		final var saved = retained(); final long deadline = saved.recoveryDeadline(); final var calls = new AtomicInteger();
		check(!attemptYield(saved, root(4), NOW + 1, due -> { check(due == deadline, "DEADLINE_RENEWED"); calls.incrementAndGet(); return false; }), "UNFINISHED_TARGET_IGNORED");
		final var codec = new PhantomGoalStateCodec();
		final var restored = new RetainedPlan(42, saved.rowVersion(), saved.plan(), new RestJourney(saved.journey().rowVersion(), codec.decode(codec.encode(saved.journey().goal()))));
		check(attemptYield(restored, root(4), NOW + 16000, due -> { check(due == deadline, "RESTART_DEADLINE_RENEWED"); calls.incrementAndGet(); return true; }), "FINISHED_TARGET_NOT_RELEASED");
		check(calls.get() == 2 && restored.journey().bound(restored, root(4), NOW + 16000), "ROOT_OR_CHILD_CHANGED");
		System.out.println("PASS living111.yield target/loot may defer; fixed persisted deadline survives codec; unchanged root/revision");
	}
	private static void paymentFence() throws Exception
	{
		final var saved = retained(); final var old = saved.journey().goal();
		for (var status : List.of(PhantomGoalStatus.FAILED, PhantomGoalStatus.COMPLETED))
		{
			final var terminal = new PhantomGoal(old.goalId(), old.goalType(), status, old.subject(), old.target(), old.requiredAmount(), old.currentAmount(), old.acquisitionMethod(), old.validSources(), old.selectedAnchor(), old.purposeKey(), old.priority(), old.riskBudget(), old.expenseBudget(), old.deadlineEpochMillis(), old.constraints(), "market.rest.native_unknown", old.revision(), old.payloadText());
			final var restored = new RestJourney(1, new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(terminal)));
			check(!attemptYield(new RetainedPlan(42, 0, saved.plan(), restored), root(4), NOW + 1, _ -> { throw new AssertionError("TASK111_TERMINAL_REPLAYED"); }), "TERMINAL_YIELD_ALLOWED");
			try { restored.admitted(); throw new AssertionError("TASK111_TERMINAL_READMITTED"); }
			catch (IllegalArgumentException expected) { }
		}
		System.out.println("PASS living111.restart terminal unknown-payment/completed child cannot restart farm-yield/travel");
	}
	private static void restartProposal() throws Exception
	{
		final var saved = retained();
		check("market.rest.proposed".equals(saved.journey().goal().reasonKey()), "COLD_PROPOSAL_MASQUERADES_AS_ACCEPTED_TRAVEL");
		final var codec = new PhantomGoalStateCodec();
		final var restored = new RestJourney(saved.journey().rowVersion(), codec.decode(codec.encode(saved.journey().goal())));
		check("market.rest.proposed".equals(restored.goal().reasonKey()) && restored.bound(saved, root(4), NOW + 1), "PROPOSAL_PHASE_LOST_ON_RESTART");
		final var accepted = new RestJourney(1, codec.decode(codec.encode(restored.admitted())));
		final var admitted = new RetainedPlan(42, saved.rowVersion(), saved.plan(), accepted);
		check(!accepted.proposed() && accepted.travelling() && accepted.bound(admitted, root(4), NOW + 1) && admitted.recoveryDeadline() == saved.recoveryDeadline(), "ADMISSION_CHANGED_ROOT_OR_DEADLINE");
		check(!attemptYield(admitted, root(4), NOW + 1, _ -> { throw new AssertionError("TASK111_ACCEPTED_TRAVEL_YIELDED_FARM_AGAIN"); }), "ACCEPTED_TRAVEL_REYIELDED");
		System.out.println("PASS living111.restart proposed/admitted codec distinct; accepted root/TTL unchanged; farm-yield not replayed");
	}
	public static void main(String[] args) throws Exception { restartProposal(); cause(); continuation(); paymentFence(); }
}
