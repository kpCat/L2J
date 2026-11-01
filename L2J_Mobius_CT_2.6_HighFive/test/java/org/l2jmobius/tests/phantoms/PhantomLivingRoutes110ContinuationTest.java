/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer.RestPlanResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer.RestRoute;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RestJourney;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RetainedPlan;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg;
import org.l2jmobius.gameserver.config.PlayerConfig;

/** The producer's own ephemeral protocol plus durable codec; no Player/DB/World fixture. */
public final class PhantomLivingRoutes110ContinuationTest
{
	private static final long NOW = 1_000_000;
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK110_" + reason); } }
	private static PhantomNavigationPoint point(int x) { return new PhantomNavigationPoint(x, 0, 0, 0); }
	private static PhantomTravelStrategy.Candidate walk(int x)
	{
		return new PhantomTravelStrategy.Candidate("fixture.walk", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, point(x), point(1000), List.of(point(1000)), null, 10000, 0, 0)));
	}
	private static RestRoute route()
	{
		final var root = new PhantomGoal(7, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20001"), 1, 0,
			"background.farm", List.of(new PhantomDomainRef("background.farm", "20001@field")), new PhantomDomainRef("topology.anchor", "field"), "background.farm", 1, 0, 0, 0, Map.of(), "farm.ready", 4);
		final var quote = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(12, 1865, 8, 100)), NOW + 120000);
		final var child = RestJourney.cityIntent(root, 42, 0, 0, quote, "giran.city.center", point(1000), NOW);
		return new RestRoute(null, 9, new RetainedPlan(42, 0, quote, new RestJourney(0, child)), root);
	}
	private static void continuation()
	{
		final var route = route(); final var root = route.root; final var retained = route.binding;
		check(route.acceptPlan(Status.PATH_FOUND, walk(0), point(0), point(1000), 0, 10000, NOW) == RestPlanResult.READY, "INITIAL_PLAN");
		route.interrupted = true;
		check(!route.planningReady(NOW + 1000), "BUSY_STARTS_PLANNING");
		route.resetNavigation(NOW + 1000); // Production does this only after exact owned-walk/request cleanup.
		check(route.root == root && route.binding == retained && route.plan == null && route.requestId == 0, "INTERRUPTION_DESTROYS_DURABLE_OWNER");
		check(!route.planningReady(NOW + 15999) && route.planningReady(NOW + 16000), "REPLAN_EVERY_PULSE");
		check(route.acceptPlan(Status.PATH_FOUND, walk(0), point(200), point(1000), 0, 10000, NOW + 16000) == RestPlanResult.FAILED, "STALE_ORIGIN_ACCEPTED");
		check(route.acceptPlan(Status.PATH_FOUND, walk(200), point(200), point(1000), 0, 10000, NOW + 16000) == RestPlanResult.READY, "FACTUAL_REPLAN_REJECTED");
		final var codec = new PhantomGoalStateCodec();
		final var recovered = new RestJourney(retained.journey().rowVersion(), codec.decode(codec.encode(retained.journey().goal())));
		final var saved = new RetainedPlan(42, retained.rowVersion(), retained.plan(), recovered);
		check(recovered.bound(saved, root, NOW + 16000) && recovered.goal().deadlineEpochMillis() == retained.recoveryDeadline(), "RESTART_ROOT_OR_LIFETIME_CHANGED");
		check(!recovered.bound(saved, root, retained.recoveryDeadline()), "INTERRUPTION_EXTENDS_VISIT");
		route.paidArrival = true;
		try { route.resetNavigation(NOW + 17000); throw new AssertionError("TASK110_PENDING_PAYMENT_DISCARDED"); }
		catch (IllegalStateException expected) { check(route.paidArrival, "PAYMENT_FENCE_LOST"); }
		System.out.println("PASS living110.continuity sameRoot/revision/child;busy/replan-factual-XYZ;finite-codec;paid-checkpoint-priority native=NOT_RUN");
	}
	private static void backpressure()
	{
		for (var status : List.of(Status.QUEUE_BACKPRESSURE, Status.PROFILE_BUSY, Status.COOLDOWN, Status.SERVICE_NOT_RUNNING, Status.DEADLINE_EXPIRED, Status.CANCELLED, Status.BACKEND_FAILURE, Status.ROUTE_BUDGET_EXCEEDED))
		{
			final var route = route();
			for (int attempt = 0; attempt < 3; attempt++)
			{
				final long now = NOW + attempt * 15000;
				check(route.acceptPlan(status, null, point(0), point(1000), 0, 10000, now) == (attempt < 2 ? RestPlanResult.DEFERRED : RestPlanResult.FAILED), "UNBOUNDED_RETRY_" + status);
				check(!route.planningReady(now + 14999), "MISSING_BACKOFF_" + status);
			}
		}
		for (var status : List.of(Status.NO_PATH, Status.NO_GEODATA, Status.PATHFINDING_DISABLED, Status.ROUTE_OBSTRUCTED, Status.DIRECT_UNVERIFIED_NO_GEODATA))
		{
			check(route().acceptPlan(status, walk(0), point(0), point(1000), 0, 10000, NOW) == RestPlanResult.FAILED, "UNPROVEN_PLAN_EXECUTED_" + status);
		}
		check(route().acceptPlan(Status.PATH_FOUND, walk(0), point(0), point(1000), 0, 9999, NOW) == RestPlanResult.FAILED, "EXPIRED_WALK_EXECUTED");
		System.out.println("PASS living110.backpressure interval=15000 attempts=3;NO_PATH-not-SEARCH_LIMIT;deadline/noGeo/stale-origin fail-closed");
	}
	private static void levelChange()
	{
		final int old = PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			PlayerConfig.MAX_FREE_TELEPORT_LEVEL = 40;
			final var first = new Leg("first", "field", "hub", "connector.source", "transition.stock", "connector.target", 30080, "NORMAL", 0, 1000, 0, 0, 0, 0, 0, 57, 100, 2000, "FIXTURE_ONLY", List.of(3));
			final var second = new Leg("second", "hub", "city", "connector.source", "transition.stock", "connector.target", 30080, "NORMAL", 0, 2000, 0, 0, 1000, 0, 0, 57, 100, 2000, "FIXTURE_ONLY", List.of(3));
			final var free = new PhantomTravelStrategy.Candidate("fixture.level40", List.of(
				new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, point(0), point(1000), List.of(), first, 0, 0, 0),
				new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, point(1000), point(2000), List.of(), second, 0, 0, 0)));
			check(PhantomAutonomousMarketProducer.remainingRestTravelFee(free, 0, 0, 40) == 0, "FREE_LEVEL_CHANGED");
			check(PhantomAutonomousMarketProducer.remainingRestTravelFee(free, 0, 0, 41) == 200, "LEVEL_CHANGE_UNRESERVED_SECOND_GK");
			check(PhantomAutonomousMarketProducer.remainingRestTravelFee(free, 1, 0, 41) == 100, "COMPLETED_GK_RESERVED_AGAIN");
			final var buy = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(1865, 1865, 10, 100)), NOW + 120000);
			check(PhantomAutonomousMarketProducer.remainingRestTravelFee(free, 0, 0, 41) > PhantomAutonomousMarketProducer.restTravelBudget(buy, 1150), "FIRST_DEBIT_ALLOWED_WITHOUT_WHOLE_ROAD");
		}
		finally { PlayerConfig.MAX_FREE_TELEPORT_LEVEL = old; }
		System.out.println("PASS living110.review-level-change 40->41 full-remaining=200 BUY-reserve-protected no-first-debit");
	}
	private static void foreignMove()
	{
		final var route = route(); final var retained = route.binding;
		// Structural ownership witnesses only; captureWalk validates actual Player/World/owner in production.
		final var issued = new PhantomNativeTravelActions.OwnedWalk(null, null, point(100));
		final var foreign = new PhantomNativeTravelActions.OwnedWalk(null, null, point(200));
		final var calls = new java.util.concurrent.atomic.AtomicInteger();
		route.walk = issued;
		check(route.releaseWalk(true, foreign, () -> { calls.incrementAndGet(); return false; }), "FOREIGN_MOVE_BLOCKS_TERMINAL_CLEANUP");
		check(calls.get() == 0 && route.walk == null && route.binding == retained, "FOREIGN_MOVE_CANCELLED_OR_CHILD_CHANGED");
		route.walk = issued;
		check(!route.releaseWalk(true, issued, () -> { calls.incrementAndGet(); return false; }) && route.walk == issued, "LIVE_OWN_WALK_LOST_WITHOUT_LEASE");
		check(route.releaseWalk(true, issued, () -> { calls.incrementAndGet(); return true; }) && route.walk == null && calls.get() == 2, "OWN_WALK_CLEANUP_NOT_RETRIED");
		route.walk = issued;
		check(route.releaseWalk(true, null, () -> { throw new AssertionError("TASK110_STALE_OWNER_CANCELLED"); }), "STALE_OWNER_FENCE_RETAINED");
		System.out.println("PASS living110.review-foreign-move stale-witness-retired foreign-cancel=0 own-lease-failure-retained native=NOT_RUN");
	}
	public static void main(String[] args)
	{
		if (args.length == 1) { if ("level-change".equals(args[0])) { levelChange(); } else if ("foreign-move".equals(args[0])) { foreignMove(); } else { throw new IllegalArgumentException("Unknown case."); } return; }
		continuation(); backpressure(); levelChange(); foreignMove();
	}
}
