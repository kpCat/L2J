/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;

import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.PvePreparation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.PvePreparationAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.PvePreparationVerdict;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomInventoryMaintenanceIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomInventoryMaintenanceIntent.Operation;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomInventoryMaintenanceIntent.Spec;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;

/** Synthetic facts exercise production policies; no Player, SQL transaction or native action is completed here. */
public final class PhantomLifeScenarios116Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "life-scenarios116";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		preparation(registry, "HP-LOW", fighter(5, 45, true), true, false, false, PvePreparationAction.RECOVER, "readiness.native_hp_mp");
		preparation(registry, "HP-READY", fighter(5, 80, true), true, false, false, PvePreparationAction.ALLOW, "readiness.native_lower_risk");
		preparation(registry, "MP-LOW", new PvePreparation(5, 100, 5, true, true, false, 20, 1, true, false, false, true), true, false, false, PvePreparationAction.RECOVER, "readiness.native_hp_mp");
		preparation(registry, "BUSY", fighter(5, 80, true), true, false, true, PvePreparationAction.WAIT_NATIVE, "readiness.native_inflight");
		preparation(registry, "HIGH-GEAR", fighter(76, 100, true), true, true, false, PvePreparationAction.MAINTAIN_SUPPLIES, "readiness.gear_or_shots.existing_supply_required");
		preparation(registry, "HIGH-POOR", fighter(76, 100, false), true, true, false, PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, "readiness.gear_or_shots.existing_supply_required");
		preparation(registry, "UNREACHABLE", fighter(5, 80, true), false, false, false, PvePreparationAction.SAFER_FARM_OR_OTHER_ACTIVITY, "readiness.native_target_or_facts_unavailable");

		registry.add("POTIONS-17", context -> verify(context, "POTIONS-17", "level=17; CP stock=0", "PhantomAutonomousSupplyPolicy.optionalPotionsAllowed", false, supply(context).optionalPotionsAllowed(17), "Optional potion threshold only; no full supply intent"));
		registry.add("POTIONS-18", context -> verify(context, "POTIONS-18", "level=18; CP stock=0", "PhantomAutonomousSupplyPolicy.optionalPotionsAllowed", true, supply(context).optionalPotionsAllowed(18), "Optional potion threshold only; no full supply intent"));
		registry.add("SHOTS-NEAR", context -> verify(context, "SHOTS-NEAR", "optional=true; stock=0; count=1; perHit=1; distance=150; speed=100", "PhantomAutonomousSupplyPolicy.usefulPurchase", true, supply(context).usefulPurchase(true, 0, 1, 1, 150, 100), "Nearby single charge is useful"));
		registry.add("SHOTS-FAR", context -> verify(context, "SHOTS-FAR", "optional=true; stock=0; count=1; perHit=1; distance=1000; speed=100", "PhantomAutonomousSupplyPolicy.usefulPurchase", false, supply(context).usefulPurchase(true, 0, 1, 1, 1000, 100), "One charge cannot justify the distant optional trip"));
		registry.add("SHOTS-FAR-STOCK", context -> verify(context, "SHOTS-FAR-STOCK", "optional=true; stock=9; count=1; perHit=1; distance=1000; speed=100", "PhantomAutonomousSupplyPolicy.usefulPurchase", true, supply(context).usefulPurchase(true, 9, 1, 1, 1000, 100), "Existing stock plus purchase covers the same trip threshold; no full supply intent"));
		registry.add("AMMO-MANDATORY", context -> verify(context, "AMMO-MANDATORY", "optional=false; stock=0; count=1; perHit=1; distance=1000; speed=100", "PhantomAutonomousSupplyPolicy.usefulPurchase", true, supply(context).usefulPurchase(false, 0, 1, 1, 1000, 100), "Mandatory ammunition is independent of optional travel savings"));
		registry.add("SHOTS-IMPOSSIBLE", context -> verify(context, "SHOTS-IMPOSSIBLE", "optional=false; stock=0; count=1; perHit=2; distance=150; speed=100", "PhantomAutonomousSupplyPolicy.usefulPurchase", false, supply(context).usefulPurchase(false, 0, 1, 2, 150, 100), "Even a mandatory purchase must supply one actual hit"));
		registry.add("BUDGET-PRODUCTIVE", context -> verify(context, "BUDGET-PRODUCTIVE", "balance=100; fees=20; earmarked=0; productive=true; reserve=500", "PhantomAutonomousSupplyPolicy.availableBudget", 60L, supply(context).availableBudget(100, 20, 0, true), "Savings retain productive ammunition funding"));
		registry.add("BUDGET-OVERDRAWN", context -> verify(context, "BUDGET-OVERDRAWN", "balance=100; fees=20; earmarked=81; productive=true", "PhantomAutonomousSupplyPolicy.availableBudget", 0L, supply(context).availableBudget(100, 20, 81, true), "Existing obligations cannot fund an impossible purchase"));

		grade(registry, "GRADE-19", 19, 7, CrystalType.NONE);
		grade(registry, "GRADE-20", 20, 7, CrystalType.D);
		grade(registry, "GRADE-40", 40, 7, CrystalType.C);
		grade(registry, "GRADE-76", 76, 5, CrystalType.S);
		grade(registry, "GRADE-76-NO-EXPERTISE", 76, 0, CrystalType.NONE);

		for (int count : new int[] {1, 3})
		{
			final String id = "SELL-COMMITTED-" + count;
			registry.add(id, context ->
			{
				final var fixture = sale(context.seed());
				final var next = PhantomInventoryMaintenanceIntent.continueSales(fixture.completed(), fixture.remaining().subList(0, count), fixture.paid(), 156);
				verify(context, id, fixture.summary(), "PhantomInventoryMaintenanceIntent.continueSales/parse", new Spec(fixture.remaining().subList(0, count), 0, false, false), PhantomInventoryMaintenanceIntent.parse(next), next.reasonKey());
				preserved(context, id, fixture, next);
			});
		}
		rejectSale(registry, "SELL-PENDING", fixture -> new SaleAttempt(fixture.completed(), fixture.remaining(), fixture.prepared()));
		rejectSale(registry, "SELL-FOREIGN-ACTOR", fixture -> new SaleAttempt(fixture.completed(), fixture.remaining(), paidReceipt(fixture, 2, fixture.root().goalId(), 4)));
		rejectSale(registry, "SELL-FOREIGN-ROOT", fixture -> new SaleAttempt(fixture.completed(), fixture.remaining(), paidReceipt(fixture, 1, fixture.root().goalId() + 1, 4)));
		rejectSale(registry, "SELL-STALE-REVISION", fixture -> new SaleAttempt(fixture.completed(), fixture.remaining(), paidReceipt(fixture, 1, fixture.root().goalId(), 3)));
		rejectSale(registry, "SELL-REPLAY-OBJECT", fixture -> new SaleAttempt(fixture.completed(), List.of(fixture.sold()), fixture.paid()));
		rejectSale(registry, "SELL-DUPLICATE", fixture -> new SaleAttempt(fixture.completed(), List.of(fixture.remaining().getFirst(), fixture.remaining().getFirst()), fixture.paid()));
		rejectSale(registry, "SELL-OTHER-COUNTER", fixture -> new SaleAttempt(fixture.completed(), List.of(new Operation(OperationKind.SELL, 30138, 334, 11, 462, fixture.sold().itemObjectId() + 1, 1)), fixture.paid()));
		rejectSale(registry, "SELL-FOUR-SLOTS", fixture ->
		{
			final var full = new ArrayList<>(fixture.remaining());
			full.add(new Operation(OperationKind.SELL, 30138, 333, 11, 426, fixture.sold().itemObjectId() + 4, 1));
			return new SaleAttempt(fixture.completed(), full, fixture.paid());
		});
		registry.add("RETURN-BUY", context ->
		{
			final var fixture = sale(context.seed());
			final var next = PhantomInventoryMaintenanceIntent.continueSales(fixture.completed(), fixture.remaining(), fixture.paid(), 156);
			var completed = next;
			// Supply only synthetic confirmation facts to the existing transition; no native sales occur here.
			for (int index = 1; index <= fixture.remaining().size(); index++)
			{
				completed = PhantomInventoryMaintenanceIntent.advance(completed, new Spec(fixture.remaining(), index, false, false), 156 + 56L * index, "maintenance.native.operation_confirmed");
			}
			final var completedSpec = PhantomInventoryMaintenanceIntent.parse(completed);
			PhantomAssertions.assertTrue(completedSpec.returning(), "RETURN-BUY: append BUY only after the synthetic SELL batch is complete.");
			final var operations = new ArrayList<>(completedSpec.operations());
			final var buy = new Operation(OperationKind.BUY, 30004, 555, 3000400, 3947, 0, 1);
			operations.add(buy);
			final var restock = PhantomInventoryMaintenanceIntent.advance(completed, new Spec(operations, completedSpec.index(), false, true), 324, "maintenance.supplies.rechecked_after_bank");
			verify(context, "RETURN-BUY", "synthetic confirmed sales; three SELL slots; append one BUY; " + fixture.summary(), "PhantomInventoryMaintenanceIntent.advance/parse", buy, PhantomInventoryMaintenanceIntent.parse(restock).current(), restock.reasonKey());
			preserved(context, "RETURN-BUY", fixture, restock);
		});
	}

	private static PvePreparation fighter(int level, double hp, boolean funds)
	{
		return new PvePreparation(level, hp, 100, true, false, true, 0, 1, true, false, false, funds);
	}

	private static void preparation(PhantomTestRegistry registry, String id, PvePreparation facts, boolean viable, boolean risky, boolean busy, PvePreparationAction action, String reason)
	{
		registry.add(id, context ->
		{
			final var actual = PhantomCombatPolicy.pvePreparation(facts, viable, risky, busy);
			verify(context, id, facts + "; viable=" + viable + "; risky=" + risky + "; busy=" + busy, "PhantomCombatPolicy.pvePreparation", new PvePreparationVerdict(action, reason), actual, actual.reason());
		});
	}

	private static PhantomAutonomousSupplyPolicy supply(PhantomTestContext context)
	{
		return PhantomAutonomousSupplyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/life/high-five-autonomous-supply-v1.xml"));
	}

	private static void grade(PhantomTestRegistry registry, String id, int level, int expertise, CrystalType expected)
	{
		registry.add(id, context -> verify(context, id, "level=" + level + "; native expertise=" + expertise, "PhantomNativeEquipmentPolicy.targetGrade", expected, PhantomNativeEquipmentPolicy.targetGrade(level, expertise), "Level preference capped by actual native expertise; no equipment grant"));
	}

	private static void verify(PhantomTestContext context, String id, String input, String method, Object expected, Object actual, String reason)
	{
		final String evidence = "input=" + input + " / method=" + method + " / actual=" + actual + " / expected=" + expected + " / reason=" + reason;
		context.record(id, evidence + " / evidenceTier=NOT_VERIFIED");
		PhantomAssertions.assertEquals(expected, actual, id + " / " + evidence);
		context.record(id, evidence + " / evidenceTier=POLICY_VERIFIED");
	}

	private static void preserved(PhantomTestContext context, String id, SaleFixture fixture, PhantomGoal next)
	{
		PhantomAssertions.assertEquals(fixture.completed().deadlineEpochMillis(), next.deadlineEpochMillis(), id + ": original deadline changed.");
		PhantomAssertions.assertEquals(fixture.completed().payloadText(), next.payloadText(), id + ": actor/class/weapon/field/root binding changed.");
		PhantomAssertions.assertTrue(PhantomSupplyIntentStore.bound(next, fixture.root()), id + ": original farm root lost.");
		final var codec = new PhantomGoalStateCodec();
		PhantomAssertions.assertEquals(next, codec.decode(codec.encode(next)), id + ": Goal codec roundtrip changed intent.");
		context.record(id + ".binding", "deadline=" + next.deadlineEpochMillis() + "; binding=" + next.payloadText() + "; root=" + next.goalId() + "; evidenceTier=POLICY_VERIFIED+CODEC_VERIFIED; native commerce=NATIVE_REQUIRED");
	}

	private static void rejectSale(PhantomTestRegistry registry, String id, Function<SaleFixture, SaleAttempt> input)
	{
		registry.add(id, context ->
		{
			final var attempt = input.apply(sale(context.seed()));
			context.record(id, "input=" + attempt.summary() + " / method=PhantomInventoryMaintenanceIntent.continueSales / actual=NOT_REJECTED / expected=IllegalArgumentException / evidenceTier=NOT_VERIFIED");
			final var rejected = PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(attempt.goal(), attempt.operations(), attempt.receipt(), 156), id + ": invalid receipt/batch authorized continuation.");
			verify(context, id, attempt.summary(), "PhantomInventoryMaintenanceIntent.continueSales", IllegalArgumentException.class.getSimpleName(), rejected.getClass().getSimpleName(), rejected.getMessage());
		});
	}

	private static SaleFixture sale(long seed)
	{
		final var random = new SplittableRandom(seed);
		final long now = 1_800_000_000_000L + random.nextInt(86_400_000);
		final int objectId = 10_000 + random.nextInt(1_000_000);
		final var root = new PhantomGoal(116, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20481"), 1, 0, "background.farm", List.of(), null, "farm.background", 500, 0, 0, 0, Map.of(), "fixture116.root", 1);
		final var binding = new PhantomSupplyIntentStore.Binding(1, 111, 10, 0, 222, new PhantomNavigationPoint(100, 200, -300, 0), 0);
		final var sold = new Operation(OperationKind.SELL, 30138, 333, 11, 426, objectId, 1);
		final var goal = PhantomInventoryMaintenanceIntent.create(root, binding, List.of(sold), 4, 100, now + 60_000, now, false);
		final var request = new PhantomCommerceReceipt.OperationRequest(sold.kind(), sold.npcId(), sold.npcObjectId(), sold.listId(), sold.itemId(), sold.itemObjectId(), 1, 56, 0, 1, 0, "", 0, 0, 0);
		// These receipt facts are synthetic validation inputs, never a SQL payment witness.
		final var prepared = PhantomCommerceReceipt.prepared(1, 116, 4, request, new PhantomCommerceReceipt.ConservationFacts(100, 22, 1, 0, 100, 200, -300), new PhantomCommerceReceipt.ConservationFacts(156, 21, 0, 0, 100, 200, -300));
		final var paid = prepared.withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED);
		final var completed = PhantomInventoryMaintenanceIntent.advance(goal, new Spec(List.of(sold), 1, false, false), 156, "maintenance.native.operation_confirmed");
		final var remaining = List.of(new Operation(OperationKind.SELL, 30138, 333, 11, 426, objectId + 1, 1), new Operation(OperationKind.SELL, 30138, 333, 11, 462, objectId + 2, 1), new Operation(OperationKind.SELL, 30138, 333, 11, 18, objectId + 3, 1));
		return new SaleFixture(root, completed, sold, remaining, prepared, paid);
	}

	private static PhantomCommerceReceipt paidReceipt(SaleFixture fixture, long profile, long root, long revision)
	{
		return PhantomCommerceReceipt.prepared(profile, root, revision, fixture.prepared().request(), fixture.prepared().before(), fixture.prepared().expectedAfter()).withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED);
	}

	private record SaleFixture(PhantomGoal root, PhantomGoal completed, Operation sold, List<Operation> remaining, PhantomCommerceReceipt prepared, PhantomCommerceReceipt paid)
	{
		String summary()
		{
			return "synthetic COMMITTED; profile=1; root=116; childRevision=" + completed.revision() + "; receiptRevision=" + paid.goalRevision() + "; sold=" + sold + "; deadline=" + completed.deadlineEpochMillis();
		}
	}

	private record SaleAttempt(PhantomGoal goal, List<Operation> operations, PhantomCommerceReceipt receipt)
	{
		String summary()
		{
			return "childRoot=" + goal.goalId() + "; childRevision=" + goal.revision() + "; receiptProfile=" + receipt.profileId() + "; receiptRoot=" + receipt.goalId() + "; receiptRevision=" + receipt.goalRevision() + "; state=" + receipt.state() + "; operations=" + operations;
		}
	}
}
