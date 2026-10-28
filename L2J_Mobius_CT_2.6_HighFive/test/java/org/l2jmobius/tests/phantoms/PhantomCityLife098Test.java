/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RestJourney;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService.RetainedPlan;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomCompositeTravelPlanner;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Real planner/codec/resource contracts with synthetic geometry; never a native city/Geo attestation. */
public final class PhantomCityLife098Test
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK098_" + reason); } }
	private static PhantomNavigationPoint point(int x) { return new PhantomNavigationPoint(x, 0, 0, 0); }
	private static Leg leg(String id, String from, String to, int source, int destination, long fee)
	{
		return new Leg(id, from, to, "connector.source", "transition.stock", "connector.target", 30080, "NORMAL", 0,
			destination, 0, 0, source, 0, 0, 57, fee, 2000, "FIXTURE_ONLY", List.of(3));
	}
	private static PhantomTravelStrategy.Options options(List<Leg> legs, long adena)
	{
		return new PhantomTravelStrategy.Options(legs, 100, adena, 0, 1, 85, 29885040, true, "city.no_return", null, 0, 3_600_000, Set.of());
	}
	private static PhantomCompositeTravelPlanner.Result route(List<Leg> legs, long adena, boolean broken, java.util.function.BooleanSupplier cancelled)
	{
		final var request = new PhantomNavigationRequest(1, point(0), point(201000), 1, 1_000_000_001, 100000);
		return PhantomCompositeTravelPlanner.plan(request, PhantomNavigationPolicy.productionDefaults(), options(legs, adena), (from, to) ->
		{
			check(from.distanceTo(to) <= 12000, "LOCAL_LIMIT_WIDENED");
			return new PhantomWalkCorridorPlanner.Result(broken && from.x() < 25000 ? PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET : PhantomWalkCorridorPlanner.Status.FOUND,
				broken && from.x() < 25000 ? List.of() : List.of(to), 2, 1, 0, 2);
		}, (x, y, surface) -> new PhantomNavigationPoint(x, y, surface.z(), surface.instanceId()),
			value -> new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, point(value.sourceX()), point(value.destinationX()), List.of(), value, 0, value.feeCount(), 0),
			cancelled);
	}
	private static void composite()
	{
		final var first = leg("first", "field", "hub", 25000, 100000, 300);
		final var second = leg("second", "hub", "city", 101000, 200000, 400);
		final var result = route(List.of(first, second), 700, false, () -> false);
		check(result.status() == PhantomNavigationResult.Status.PATH_FOUND, "MISSING_DISTANT_MULTI_GK");
		final var plan = result.candidate();
		check(plan.stages().stream().filter(stage -> stage.mode() == PhantomTravelStrategy.Mode.GK).count() == 2, "BOTH_NATIVE_LEGS_REQUIRED");
		check(plan.adena() == 700 && plan.millis() == 270000, "WHOLE_COST_AND_TIME");
		check(plan.stages().getFirst().from().equals(point(0)) && plan.stages().getLast().to().equals(point(201000)), "COMPLETE_ENDPOINTS");
		final var buy = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(1865, 1865, 10, 100)), 120001);
		check(PhantomAutonomousMarketProducer.completeRestRoute(plan, point(0), point(201000), PhantomAutonomousMarketProducer.restTravelBudget(buy, 1700), 270000), "EXACT_BUY_RESERVE");
		check(!PhantomAutonomousMarketProducer.completeRestRoute(plan, point(0), point(201000), PhantomAutonomousMarketProducer.restTravelBudget(buy, 1699), 270000), "BUY_RESERVE_SPENT_ON_TRAVEL");
		check(route(List.of(first), 700, false, () -> false).candidate() == null, "MISSING_EDGE_ACCEPTED");
		final var single = route(List.of(leg("legacy", "field", "city", 25000, 200000, 300)), 300, false, () -> false);
		check(single.candidate() != null && single.candidate().adena() == 300 && single.candidate().stages().stream().filter(stage -> stage.mode() == PhantomTravelStrategy.Mode.GK).count() == 1, "SINGLE_GK_REGRESSION");
		System.out.println("PASS city098.composite-distant-ingress cost=700 walkMillis=270000 GK=2 geometry=FIXTURE_ONLY");
	}
	private static void negatives()
	{
		final var legs = List.of(leg("first", "field", "hub", 25000, 100000, 300), leg("second", "hub", "city", 101000, 200000, 400));
		check(route(legs, 699, false, () -> false).candidate() == null, "PARTIAL_FUNDS_ACCEPTED");
		check(route(legs, 700, true, () -> false).candidate() == null, "TORN_CORRIDOR_ACCEPTED");
		check(route(legs, 700, false, () -> true).status() == PhantomNavigationResult.Status.CANCELLED, "CANCELLED_ROUTE_ACCEPTED");
		final var crowded = new java.util.ArrayList<Leg>();
		for (int index = 0; index < 128; index++) { crowded.add(leg("branch." + index, "field." + index, "hub." + index, 25000, 100000, 0)); }
		check(route(crowded, 0, false, () -> false).status() == PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED, "SEARCH_LIMIT_IS_NOT_PHYSICAL_NO_PATH");
		candidateCap();
		System.out.println("PASS city098.route-negatives funds/torn-corridor/cancel");
	}
	private static void candidateCap()
	{
		final var branches = new java.util.ArrayList<Leg>();
		for (int index = 1; index <= 8; index++) { branches.add(leg("cap0" + index, "field", "city." + index, 0, 200000 + index, 1)); }
		final var request = new PhantomNavigationRequest(1, point(0), point(201000), 1, 1_000_000_001, 100000);
		final var result = PhantomCompositeTravelPlanner.plan(request, PhantomNavigationPolicy.productionDefaults(), options(branches, 1), (from, to) ->
		{
			final boolean denied = from.x() >= 200001 && from.x() <= 200007;
			return new PhantomWalkCorridorPlanner.Result(denied ? PhantomWalkCorridorPlanner.Status.NO_ROUTE_WITHIN_BUDGET : PhantomWalkCorridorPlanner.Status.FOUND, denied ? List.of() : List.of(to), 2, 1, 0, 2);
		}, (x, y, hint) -> point(x), value -> new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, point(0), point(value.destinationX()), List.of(), value, 0, 1, 0), () -> false);
		check(result.candidate() == null && result.status() == PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED, "UNSEARCHED_EIGHTH_ROUTE_REPORTED_NO_PATH");
	}
	private static PhantomGoal root(long revision)
	{
		return new PhantomGoal(7, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20001"), 1, 0,
			"background.farm", List.of(new PhantomDomainRef("background.farm", "20001@field")), new PhantomDomainRef("topology.anchor", "field"), "background.farm", 1, 0, 0, 0, Map.of(), "farm.ready", revision);
	}
	private static void discountReserve()
	{
		final var legs = List.of(leg("first", "field", "hub", 25000, 100000, 300), leg("second", "hub", "city", 101000, 200000, 400));
		final var request = new PhantomNavigationRequest(1, point(0), point(201000), 1, 1_000_000_001, 100000);
		final var result = PhantomCompositeTravelPlanner.plan(request, PhantomNavigationPolicy.productionDefaults(), options(legs, 350),
			(from, to) -> new PhantomWalkCorridorPlanner.Result(PhantomWalkCorridorPlanner.Status.FOUND, List.of(to), 2, 1, 0, 2),
			(x, y, hint) -> point(x), value -> new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, point(value.sourceX()), point(value.destinationX()), List.of(), value, 0, value.feeCount() / 2, 0), () -> false);
		check(result.candidate() == null, "DISCOUNT_CAN_EXPIRE_BEFORE_SECOND_PAYMENT");
		final long discountedMinute = java.time.ZonedDateTime.of(2026, 10, 27, 21, 0, 0, 0, java.time.ZoneId.systemDefault()).toEpochSecond() / 60;
		check(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.fee(legs.getFirst(), 1, 85, discountedMinute) == 150, "ACTUAL_DISCOUNT_CHANGED");
		check(org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.maximumFee(legs.getFirst(), 0, 1) == 0, "FREE_LEVEL_RESERVE_CHANGED");
		System.out.println("PASS city098.discounted-quote-requires-full-itinerary-reserve actualDebit=NOT_RUN");
	}
	private static void lifetime()
	{
		final long now = 1_000_000;
		final var quote = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(12, 1865, 10, 100)), now + 120000);
		final var original = RestJourney.cityIntent(root(4), 42, 0, 0, quote, "giran.city.center", point(201000), now);
		final var codec = new PhantomGoalStateCodec();
		final var journey = new RestJourney(0, codec.decode(codec.encode(original)));
		final var retained = new RetainedPlan(42, 0, quote, journey);
		check(journey.bound(retained, root(4), now + 120001), "VISIT_EXPIRES_WITH_QUOTE");
		check(!journey.bound(retained, root(5), now + 120001), "STALE_ROOT_ACCEPTED");
		check(!journey.bound(new RetainedPlan(43, 0, quote, journey), root(4), now + 1), "WRONG_ACTOR_ACCEPTED");
		check(!journey.bound(retained, root(4), original.deadlineEpochMillis()), "UNBOUNDED_VISIT");
		check(retained.recoveryDeadline() == original.deadlineEpochMillis() && retained.plan().expiresEpochMillis() == now + 120000, "QUOTE_LIFETIME_MUTATED");
		final var recovery = new PhantomAutonomousMarketProducer.RecoveryRequest(retained, 7, 4, 1);
		check(recovery.validFor(retained, 7, 4, now + 120001) && !recovery.validFor(new RetainedPlan(42, 1, quote, journey), 7, 4, now + 120001), "RECOVERY_EXPIRY_OR_STALE_ROW");
		final var fresh = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(12, 1865, 8, 110)), now + 420000);
		final var arrived = new RestJourney(1, codec.decode(codec.encode(journey.arrived(fresh))));
		final var refreshed = new RetainedPlan(42, 1, fresh, arrived);
		check(arrived.bound(refreshed, root(4), now + 300000) && refreshed.recoveryDeadline() == now + 420000, "ARRIVAL_REQUIRES_FRESH_SHORT_QUOTE");
		check(!arrived.bound(new RetainedPlan(42, 1, quote, arrived), root(4), now + 300000) && !arrived.bound(refreshed, root(4), now + 420000), "EXPIRED_PURPOSE_QUOTE_PUBLISHED");
		final var legacy = new RestJourney(0, RestJourney.intent(root(4), 42, 0, 0, quote, "giran.city.center", point(201000), now));
		check(legacy.bound(new RetainedPlan(42, 0, quote, legacy), root(4), now + 1), "LEGACY_DECODE_OR_BIND");
		check(!legacy.bound(new RetainedPlan(42, 0, quote, legacy), root(4), now + 120000), "LEGACY_EXPIRY_EXTENDED");
		final var failed = new PhantomGoal(original.goalId(), original.goalType(), PhantomGoalStatus.FAILED, original.subject(), original.target(), original.requiredAmount(), original.currentAmount(), original.acquisitionMethod(), original.validSources(), original.selectedAnchor(), original.purposeKey(), original.priority(), original.riskBudget(), original.expenseBudget(), original.deadlineEpochMillis(), original.constraints(), "market.rest.native_unknown", original.revision(), original.payloadText());
		final var terminal = new RestJourney(1, codec.decode(codec.encode(failed)));
		check(!terminal.bound(new RetainedPlan(42, 0, quote, terminal), root(4), now + 1), "PREPARED_PAYMENT_REPLAYED");
		System.out.println("PASS city098.durable-visit codec/quote-split/legacy/stale-root/wrong-actor/expiry/prepared-terminal");
	}
	public static void main(String[] args)
	{
		final int previous = PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			PlayerConfig.MAX_FREE_TELEPORT_LEVEL = 40;
			if (args.length == 0 || "route".equals(args[0])) { composite(); negatives(); }
			if (args.length == 0 || "lifetime".equals(args[0])) { lifetime(); }
			if (args.length == 0 || "reserve".equals(args[0])) { discountReserve(); }
		}
		finally { PlayerConfig.MAX_FREE_TELEPORT_LEVEL = previous; }
		System.out.println("TASK098_DB_FREE_PASS nativeWorld=NOT_RUN STORE=NOT_RUN sameDBRestart=NOT_RUN");
	}
}
