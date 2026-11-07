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
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;

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
	private static RestJourney status(RestJourney journey, PhantomGoalStatus status)
	{
		final var old = journey.goal();
		final var changed = new PhantomGoal(old.goalId(), old.goalType(), status, old.subject(), old.target(), old.requiredAmount(), old.currentAmount(), old.acquisitionMethod(), old.validSources(), old.selectedAnchor(), old.purposeKey(), old.priority(), old.riskBudget(), old.expenseBudget(), old.deadlineEpochMillis(), old.constraints(), "market.rest.native_unknown", old.revision(), old.payloadText());
		return new RestJourney(journey.rowVersion(), new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(changed)));
	}
	private static void observedCityArrival()
	{
		final var proposed = retained();
		final var codec = new PhantomGoalStateCodec();
		final var admitted = new RestJourney(1, codec.decode(codec.encode(proposed.journey().admitted())));
		final var fresh = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Ресурсы", List.of(new PhantomStorePlan.Line(12, 1865, 6, 110)), NOW + 240000);
		final var arrived = new RestJourney(2, codec.decode(codec.encode(admitted.arrived(fresh))));
		final var completed = new RetainedPlan(42, 1, fresh, arrived);
		final var legacy = new RestJourney(1, RestJourney.intent(root(4), 42, 0, 0, fresh, "fixture.city", new PhantomNavigationPoint(1000, 0, 0, 0), NOW));
		check(PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 1, fresh, status(legacy, PhantomGoalStatus.COMPLETED)), root(4), NOW + 130000, true), "UNOBSERVED_LEGACY_RECOVERY_LOST");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(null, root(4), NOW + 130000, false), "OBSERVED_MISSING_RECOVERY_ALLOWED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 1, fresh, null), root(4), NOW + 130000, false), "OBSERVED_QUOTE_ONLY_ALLOWED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(proposed, root(4), NOW + 1, false), "OBSERVED_PROPOSAL_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 0, proposed.plan(), admitted), root(4), NOW + 1, false), "OBSERVED_ACTIVE_CHILD_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 1, fresh, status(arrived, PhantomGoalStatus.FAILED)), root(4), NOW + 130000, false), "OBSERVED_ARMED_PAYMENT_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 1, fresh, status(legacy, PhantomGoalStatus.COMPLETED)), root(4), NOW + 130000, false), "OBSERVED_LEGACY_COMPLETION_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(completed, root(5), NOW + 130000, false), "OBSERVED_STALE_ROOT_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(43, 1, fresh, arrived), root(4), NOW + 130000, false), "OBSERVED_FOREIGN_ACTOR_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(new RetainedPlan(42, 1, proposed.plan(), arrived), root(4), NOW + 130000, false), "OBSERVED_OLD_QUOTE_PUBLISHED");
		check(!PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(completed, root(4), fresh.expiresEpochMillis(), false), "OBSERVED_EXPIRED_QUOTE_PUBLISHED");
		check(PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(completed, root(4), NOW + 130000, true), "UNOBSERVED_CITY_ARRIVAL_LOST");
		check(PhantomAutonomousMarketProducer.recoveryVisibilityAllowed(completed, root(4), NOW + 130000, false), "TASK117_OBSERVED_BOUND_CITY_ARRIVAL_BLOCKED");
		System.out.println("PASS living117.observed-city-arrival bound completed fresh city quote; legacy/active/failed/stale/foreign/expired rejected; POLICY_CODEC_ONLY native=NOT_RUN");
	}
	private static void supplyWalkEpisodes()
	{
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final var origin = new PhantomNavigationPoint(0, 0, 0, 0);
		final var destination = new PhantomNavigationPoint(25000, 0, 0, 0);
		final var first = new PhantomNavigationPoint(1000, 0, 0, 0);
		final var capability = new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		final var route = new PhantomNavigationRoute(PhantomNavigationRoute.Mode.COMPUTED, origin, first, List.of(first), capability, 1, false, 1, policy.maximumRouteDistance());
		final var budget = new NativeWalkBudget(origin, destination, 1, 120, policy);
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 120, 1000000, null, 2) == null, "117_LOCAL_CORRIDOR_CLAIMED_FINAL_ARRIVAL");
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 0, 1000000, budget, 2) == null, "117_DISABLED_MOVEMENT_RELEASED_ROUTE");
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 120, 1, budget, 2) == null, "117_DURABLE_DEADLINE_EXTENDED");
		final var plan = L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 120, 1000000, budget, 2);
		check(plan != null, "117_SUPPLY_EPISODE_REJECTED");
		check(plan.stages().getLast().to().equals(first) && !plan.stages().getLast().to().equals(destination), "117_CHECKPOINT_BECAME_MERCHANT_ARRIVAL");
		check(!budget.checkpoint(origin, 3), "117_WRONG_FEET_RENEWED_EPISODE");
		check(budget.checkpoint(first, 4), "117_PHYSICAL_CHECKPOINT_REJECTED");
		check(budget.visited().contains(origin) && budget.visited().contains(first), "117_VISITED_CHECKPOINTS_LOST");
		try { budget.visited().clear(); throw new AssertionError("TASK117_MUTABLE_VISITED_ESCAPE"); }
		catch (UnsupportedOperationException expected) { }
		final var next = new PhantomNavigationPoint(2000, 500, 0, 0);
		final var second = new PhantomNavigationRoute(PhantomNavigationRoute.Mode.COMPUTED, first, next, List.of(next), capability, 4, false, 1, policy.maximumRouteDistance());
		check(L2jPhantomSupplyJourney.nativeWalkPlan(second, origin, destination, 120, 1000000, budget, 5) == null, "117_STALE_ORIGIN_RELEASED_ROUTE");
		check(L2jPhantomSupplyJourney.nativeWalkPlan(second, first, destination, 120, 1000000, budget, 5) != null, "117_NEXT_EPISODE_LOST_SAME_BUDGET");
		check(!budget.checkpoint(new PhantomNavigationPoint(next.x(), next.y(), 16, 0), 6), "117_OTHER_FLOOR_RENEWED_EPISODE");
		check(budget.checkpoint(next, 6), "117_SECOND_PHYSICAL_CHECKPOINT_REJECTED");
		final var checkpoint = budget.suspend(7);
		final var decoded = NativeWalkBudget.Snapshot.decode(checkpoint.encode());
		check(checkpoint.equals(decoded), "130_WALK_CHECKPOINT_CODEC_CHANGED_PROGRESS");
		final var resumed = NativeWalkBudget.resume(decoded, 10_000_000_000L, policy);
		check(resumed.visited().equals(budget.visited()), "130_WALK_CHECKPOINT_LOST_VISITED");
		check(resumed.suspend(10_000_000_000L).equals(checkpoint), "130_WALK_RESTART_REPLENISHED_SPENT_BUDGET");
		final var expired = budget.suspend(6 + policy.maximumAttemptDurationNanos());
		check(NativeWalkBudget.resume(expired, 20_000_000_000L, policy).expired(20_000_000_000L), "130_WALK_RESTART_REVIVED_EXPIRED_EPISODE");
		check(L2jPhantomSupplyJourney.nativeWalkPlan(route, origin, destination, 120, 1000000, budget, 6 + policy.maximumAttemptDurationNanos()) == null, "117_EXPIRED_EPISODE_RELEASED_ROUTE");
		System.out.println("PASS living117.supply-walk-episodes actual candidate/checkpoint/budget guards; STRUCTURAL_FIXTURE native=NOT_RUN");
	}
	private static void offlineIncomeContracts()
	{
		final var quote = new PhantomPrivateMarketPricingAuthority.ItemQuote(1868, true, true, 100, 130, 120, "fixture.native.market.quote");
		final var terms = PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 700, 80, 50, 80).orElse(null);
		check(terms != null && terms.count() == 7 && terms.proceeds() == 700 && terms.netPremium() == 270, "117_PRICED_SURPLUS_REJECTED");
		check(PhantomOfflineSupply.saleTerms(1868, 0, 10, 8, 100, quote, 700, 80, 50, 80).isEmpty(), "117_PROTECTED_QUANTITY_SOLD");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 0, 8, 100, quote, 700, 80, 50, 80).isEmpty(), "117_ABSENT_ITEM_SOLD");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 0, 100, quote, 700, 80, 50, 80).isEmpty(), "117_NO_BUY_DEMAND_CREDITED");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 99, quote, 700, 80, 50, 80).isEmpty(), "117_CANONICAL_BID_FLOOR_IGNORED");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 99, 80, 50, 80).isEmpty(), "117_EMPTY_BUYER_WALLET_CREDITED");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 700, 79, 50, 80).isEmpty(), "117_FUTURE_PROCEEDS_FUNDED_TRAVEL");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 700, 40, 50, 80, 40).isPresent(), "117_ALREADY_PAID_OUTBOUND_FEE_CHARGED_TWICE");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 700, 39, 50, 80, 40).isEmpty(), "117_UNFUNDED_RETURN_ACCEPTED");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, quote, 700, 80, 100, 0).isEmpty(), "117_NO_NET_PREMIUM_CREATED_INCOME");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, null, 700, 80, 50, 80).isEmpty(), "117_MISSING_QUOTE_CREATED_INCOME");
		final var blocked = new PhantomPrivateMarketPricingAuthority.ItemQuote(1868, true, false, 100, 130, 120, "fixture.bid.blocked");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, blocked, 700, 80, 50, 80).isEmpty(), "117_BLOCKED_BID_CREATED_INCOME");
		check(PhantomOfflineSupply.saleTerms(1865, 12, 10, 8, 100, quote, 700, 80, 50, 80).isEmpty(), "117_FOREIGN_ITEM_QUOTE_USED");
		final var unpriced = new PhantomPrivateMarketPricingAuthority.ItemQuote(1868, false, false, 0, 0, 0, "fixture.unpriced");
		check(PhantomOfflineSupply.saleTerms(1868, 12, 10, 8, 100, unpriced, 700, 80, 50, 80).isEmpty(), "117_UNPRICED_ITEM_SOLD");
		final var binding = new PhantomSupplyIntentStore.Binding(4, 42, 10, 0, 123, new PhantomNavigationPoint(1000, 0, 0, 0), 0);
		final String type = PhantomSocialEconomyGoalSpec.STORE_SELL_GOAL;
		final var sale = new PhantomGoal(7, type, PhantomGoalStatus.ACTIVE, root(4).subject(),
			new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, "77;0;" + "a".repeat(64) + ";700;S:456:7:100:1868"),
			1, 0, null, List.of(), null, type, 750, 0, 0, NOW + 480000, Map.of(), "income.native.profitable_surplus", 8, binding.encode());
		final var restored = new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(sale));
		check(PhantomSupplyIntentStore.social(restored) && PhantomSupplyIntentStore.bound(restored, root(4)), "117_STORE_SELL_CHILD_NOT_ROUTED");
		check(!PhantomSupplyIntentStore.bound(restored, root(5)), "117_STALE_INCOME_CHILD_BOUND");
		check(PhantomOfflineSupply.spec(restored) instanceof PhantomSocialEconomyGoalSpec.StoreSell, "117_INCOME_SPEC_CHANGED");
		check(PhantomOfflineSupply.returning(restored.withStatus(PhantomGoalStatus.COMPLETED)), "117_COMMITTED_INCOME_CANNOT_RETURN");
		check(!PhantomOfflineSupply.returning(restored.withStatus(PhantomGoalStatus.FAILED)), "117_FAILED_INCOME_CLAIMED_RETURN");
		check(PhantomSupplyIntentStore.Binding.parse(restored).equals(binding), "117_INCOME_FIELD_OR_WEAPON_BINDING_CHANGED");
		System.out.println("PASS living117.offline-income terms/stock/wallet/quote/root/codec guards; POLICY_CODEC_ONLY native=NOT_RUN");
	}
	private static void incomeTravelEstimate()
	{
		final var from = new PhantomNavigationPoint(0, 0, 0, 0);
		final var to = new PhantomNavigationPoint(200100, 0, 0, 0);
		final var first = new PhantomNormalGatekeeperTravel.Leg("first", "a", "b", "", "", "", 30256, "NORMAL", 0, 100000, 0, 0, 100, 0, 0, 57, 100, 1000, "fixture.stock", List.of());
		final var second = new PhantomNormalGatekeeperTravel.Leg("second", "c", "d", "", "", "", 30177, "NORMAL", 0, 200000, 0, 0, 100100, 0, 0, 57, 200, 1000, "fixture.stock", List.of());
		final var free = new PhantomNormalGatekeeperTravel.Leg("free", "a", "d", "", "", "", 30177, "NORMAL", 0, 200100, 0, 0, 0, 0, 0, 57, 0, 1000, "fixture.stock", List.of());
		final var options = new PhantomTravelStrategy.Options(List.of(first, second, free), 100, 10000, 0, 1, 80, 1, true, "fixture", null, 0, 100000, java.util.Set.of());
		final var paid = new PhantomTravelStrategy.Candidate("fast_paid", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.GK, from, to, List.of(), first, 1000, 100, 0)));
		final var walking = new PhantomTravelStrategy.Candidate("slow_walk", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, from, to, List.of(to), null, 10000, 0, 0)));
		check(PhantomTravelStrategy.select(List.of(paid, walking), options.adena(), 0, options.remainingMillis()).candidate() == paid, "117_NATIVE_TIME_FIRST_SELECTION_NOT_EXERCISED");
		final var income = L2jPhantomSupplyJourney.incomeTravelOptions(options);
		check(income.adena() == 0 && income.legs().equals(options.legs()) && income.legs().stream().allMatch(leg -> PhantomNormalGatekeeperTravel.phantomMaximumFee(leg, 1, 80) == 0), "130_INCOME_MANAGED_GK_TARIFF_OR_ZERO_WALLET_CHANGED");
		check(PhantomNormalGatekeeperTravel.maximumFee(first, 1, 80) + PhantomNormalGatekeeperTravel.maximumFee(second, 1, 80) == 300, "130_STOCK_HUMAN_PAID_TARIFF_CHANGED");
		check(PhantomTravelStrategy.select(List.of(paid, walking), income.adena(), 0, income.remainingMillis()).candidate() == walking, "117_FAST_PAID_GK_SPENT_INCOME_PREMIUM");
		check(options.adena() == 10000 && options.legs().size() == 3 && income.gatekeeperAllowed(), "117_LEGACY_WALLET_OR_FREE_GK_CHANGED");
		final var steps = List.of(new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "first", "a", "b", 1000, first),
			new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "connector", "b", "c", 1000, null),
			new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "second", "c", "d", 1000, second));
		final java.util.function.Function<String, PhantomNavigationPoint> anchor = id -> "c".equals(id) ? new PhantomNavigationPoint(100100, 0, 0, 0) : null;
		final var estimate = L2jPhantomSupplyJourney.itineraryEstimate(from, to, steps, 100, 1000, 5000, 1, 80, _ -> true, anchor).orElse(null);
		check(estimate != null && estimate.fees() == 0 && estimate.minimumMillis() == 5000, "130_TWO_MANAGED_GK_FEE_ESTIMATE_CHANGED");
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, to, steps, 100, 299, 5000, 1, 80, _ -> true, anchor).isEmpty(), "117_WALK_DISTANCE_BOUND_IGNORED");
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, to, steps, 100, 1000, 4999, 1, 80, _ -> true, anchor).isEmpty(), "117_TRAVEL_TIME_BOUND_IGNORED");
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, to, steps, 100, 1000, 5000, 1, 80, leg -> leg != second, anchor).isEmpty(), "117_UNAVAILABLE_GK_QUOTED");
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, to, steps, 100, 1000, 5000, 1, 80, _ -> true, _ -> null).isEmpty(), "117_MISSING_CONNECTOR_ANCHOR_ASSUMED");
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, new PhantomNavigationPoint(to.x(), to.y(), to.z(), 1), steps, 100, 1000, 5000, 1, 80, _ -> true, anchor).isEmpty(), "117_OTHER_INSTANCE_ESTIMATED");
		final var five = new java.util.ArrayList<PhantomNormalGatekeeperTravel.Step>();
		for (int index = 0; index < 5; index++)
		{
			final var leg = new PhantomNormalGatekeeperTravel.Leg("loop" + index, "a" + index, "b" + index, "", "", "", 30000 + index, "NORMAL", 0, 0, 0, 0, 0, 0, 0, 57, 1, 1000, "fixture.stock", List.of());
			five.add(new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, leg.id(), leg.fromAnchorId(), leg.toAnchorId(), 1000, leg));
		}
		check(L2jPhantomSupplyJourney.itineraryEstimate(from, from, five, 100, 1000, 1000000, 1, 80, _ -> true, anchor).isEmpty(), "117_COMPOSITE_FOUR_GK_BOUND_ESCAPED");
		System.out.println("PASS living117.income-travel-estimate native maximum fees/availability/walk/time/4GK and income-zero-fee guards; STRUCTURAL_ESTIMATE_ONLY actualGK=NOT_RUN");
	}
	public static void main(String[] args) throws Exception { restartProposal(); cause(); continuation(); paymentFence(); observedCityArrival(); supplyWalkEpisodes(); offlineIncomeContracts(); incomeTravelEstimate(); }
}
