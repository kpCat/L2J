/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomInventoryMaintenanceIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

/** Focused progression contracts; no Player, database or fabricated equipment. */
public final class PhantomSupply071Suite implements PhantomTestSuite
{
	@Override public String id() { return "supply071"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("118-stock-horizon-keeps-poor-and-weight-bounds", context ->
		{
			final var policy = org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/life/high-five-autonomous-supply-v1.xml"));
			final long fighter = policy.plannedStock(2, 500), caster = policy.plannedStock(1, 4000);
			PhantomAssertions.assertEquals(1920L, fighter, "A funded fast weapon still plans only sixty hits or one600 batch.");
			PhantomAssertions.assertEquals(120L, caster, "Slow magic consumption was blindly scaled to the physical stock target.");
			PhantomAssertions.assertEquals(2400L, policy.plannedStock(100, 1), "Planning escaped the existing four-operation bound.");
			final long funded = policy.availableBudget(100000, 1000, 5000, true);
			PhantomAssertions.assertEquals(fighter, policy.purchaseCount(fighter, funded, 20, 1, 100, 10000, 4), "Funded mobile stock was truncated to one600 transaction.");
			final long poor = policy.availableBudget(500, 100, 100, true);
			PhantomAssertions.assertEquals(28L, policy.purchaseCount(fighter, poor, 8, 1, 100, 10000, 4), "Poor actor ignored travel/gear earmark and cash reserve.");
			PhantomAssertions.assertEquals(5L, policy.purchaseCount(fighter, funded, 8, 2, 590, 1000, 4), "Weight admission discarded the useful smaller affordable purchase.");
			PhantomAssertions.assertEquals(600L, policy.purchaseCount(Long.MAX_VALUE, Long.MAX_VALUE, 1, 0, 0, 1000, 1), "Single purchase escaped the native600 cap.");
			PhantomAssertions.assertEquals(0L, policy.purchaseCount(fighter, funded, 0, 1, 0, 1000, 4), "Unquoted stock created a free purchase.");
			PhantomAssertions.assertEquals(0L, policy.purchaseCount(fighter, funded, 8, 1, 601, 1000, 4), "Overloaded actor requested more stock.");
			PhantomAssertions.assertEquals(0L, policy.plannedStock(0, 500), "Unknown native consumption produced a target.");
			context.record("supply118.stock", "Actual production planning: weapon charges/native interval, finite configured horizon, wallet/return/earmark reserve, <=60% load and <=4x600; no paid trade simulated");
		});
		registry.add("118-paid-batches-keep-original-owner-and-codec", context ->
		{
			final long now = System.currentTimeMillis();
			final var root = new PhantomGoal(118, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20001"), 1, 0, "background.farm", java.util.List.of(), null, "farm.background", 500, 0, 0, 0, java.util.Map.of(), "fixture118.root", 1);
			final var binding = new PhantomSupplyIntentStore.Binding(1, 111, 0, 0, 222, new PhantomNavigationPoint(100, 200, -300, 0), 0);
			final var operations = PhantomInventoryMaintenanceIntent.purchases(30138, 333, 11, 1835, 1920, 4);
			PhantomAssertions.assertEquals(java.util.List.of(600L, 600L, 600L, 120L), operations.stream().map(PhantomInventoryMaintenanceIntent.Operation::count).toList(), "Bulk stock is not a bounded series of legal native purchases.");
			var intent = PhantomInventoryMaintenanceIntent.create(root, binding, operations, 2, 50000, now + 60000, now, false);
			long wallet = 50000, stock = 0;
			for (int index = 0; index < operations.size(); index++)
			{
				intent = new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(intent));
				final var spec = PhantomInventoryMaintenanceIntent.parse(intent);
				PhantomAssertions.assertEquals(index, spec.index(), "Restart repeated or skipped a batch index.");
				final var operation = spec.current();
				final long cost = Math.multiplyExact(8, operation.count());
				final var request = new PhantomCommerceReceipt.OperationRequest(operation.kind(), operation.npcId(), operation.npcObjectId(), operation.listId(), operation.itemId(), 0, operation.count(), cost, 0, 0, 0, "", 0, 0, 0);
				final var before = new PhantomCommerceReceipt.ConservationFacts(wallet, stock, 1, 0, 100, 200, -300);
				final var after = new PhantomCommerceReceipt.ConservationFacts(wallet - cost, stock + operation.count(), 1, 0, 100, 200, -300);
				final var receipt = PhantomCommerceReceipt.prepared(1, intent.goalId(), intent.revision(), request, before, after).withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED);
				PhantomAssertions.assertTrue(operation.matches(receipt.request()), "Batch changed its native counter/item/count authority.");
				wallet -= cost; stock += operation.count();
				intent = PhantomInventoryMaintenanceIntent.advance(intent, new PhantomInventoryMaintenanceIntent.Spec(operations, index + 1, false, true), wallet, "fixture118.committed");
				PhantomAssertions.assertTrue(receipt.goalRevision() != intent.revision() && intent.deadlineEpochMillis() == now + 60000 && intent.payloadText().equals(binding.encode()) && PhantomSupplyIntentStore.bound(intent, root), "Continuation reused payment authority or renewed the journey.");
			}
			PhantomAssertions.assertTrue(PhantomInventoryMaintenanceIntent.parse(intent).returning() && stock == 1920 && wallet == 34640, "Four synthetic conservation receipts did not finish the original bounded child.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.purchases(30138, 333, 11, 1835, 2401, 4), "More than four native purchases admitted.");
			context.record("supply118.batches", "Existing maintenance goal/codec/revisions/conservation contracts only; four distinct synthetic receipts; NATIVE_PAYMENT=NOT_RUN");
		});
		registry.add("115-optional-cp-stock-does-not-liquidate-low-level-farm-loot", context ->
		{
			final var policy = org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/life/high-five-autonomous-supply-v1.xml"));
			for (int level : new int[] {1, 3, 6, 7, 17}) { PhantomAssertions.assertTrue(!policy.optionalPotionsAllowed(level), "Optional CP stock can turn low-level loot into repeated maintenance trips at level " + level); }
			for (int level : new int[] {18, 20, 40, 85}) { PhantomAssertions.assertTrue(policy.optionalPotionsAllowed(level), "Existing higher-level potion restock was suppressed at level " + level); }
			PhantomAssertions.assertTrue(policy.availableBudget(996, 0, 0, true) > 0 && policy.usefulPurchase(true, 0, 60, 1), "Low-level potion gate changed productive ammunition/amplifier budgeting.");
			context.record("supply115.optionalPotions", "Native6 level6,17 spiritshots,pressure0,996A: optional CP shortage must not cause repeated liquidation; existing level18 potion boundary retained");
		});
		registry.add("115-paid-surplus-continues-at-counter-without-renewing-journey", context ->
		{
			final long now = System.currentTimeMillis();
			final var root = new PhantomGoal(115, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20481"), 1, 0, "background.farm", java.util.List.of(), null, "farm.background", 500, 0, 0, 0, java.util.Map.of(), "fixture115.root", 1);
			final var binding = new PhantomSupplyIntentStore.Binding(1, 111, 10, 0, 222, new PhantomNavigationPoint(100, 200, -300, 0), 0);
			final var sold = new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.SELL, 30138, 333, 11, 426, 444, 1);
			final var goal = PhantomInventoryMaintenanceIntent.create(root, binding, java.util.List.of(sold), 4, 100, now + 60_000, now, false);
			final var request = new PhantomCommerceReceipt.OperationRequest(sold.kind(), sold.npcId(), sold.npcObjectId(), sold.listId(), sold.itemId(), sold.itemObjectId(), 1, 56, 0, 1, 0, "", 0, 0, 0);
			final var prepared = PhantomCommerceReceipt.prepared(1, 115, 4, request, new PhantomCommerceReceipt.ConservationFacts(100, 22, 1, 0, 100, 200, -300), new PhantomCommerceReceipt.ConservationFacts(156, 21, 0, 0, 100, 200, -300));
			final var paid = prepared.withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED);
			final var completed = PhantomInventoryMaintenanceIntent.advance(goal, new PhantomInventoryMaintenanceIntent.Spec(java.util.List.of(sold), 1, false, false), 156, "maintenance.native.operation_confirmed");
			final var remaining = java.util.stream.IntStream.rangeClosed(445, 447).mapToObj(object -> new PhantomInventoryMaintenanceIntent.Operation(sold.kind(), 30138, 333, 11, 426, object, 1)).toList();
			final var full = new java.util.ArrayList<>(remaining); full.add(new PhantomInventoryMaintenanceIntent.Operation(sold.kind(), 30138, 333, 11, 426, 448, 1));
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, full, paid, 156), "Full sale continuation removed the reserved funded-supply BUY slot.");
			final var next = PhantomInventoryMaintenanceIntent.continueSales(completed, remaining, paid, 156);
			final var spec = PhantomInventoryMaintenanceIntent.parse(next);
			PhantomAssertions.assertTrue(!spec.returning() && spec.index() == 0 && spec.operations().size() == 3 && spec.current().itemObjectId() == 445, "Remaining paid-sale surplus returned to the field instead of using the same counter.");
			PhantomAssertions.assertTrue(next.revision() == 6 && next.deadlineEpochMillis() == goal.deadlineEpochMillis() && next.payloadText().equals(goal.payloadText()) && PhantomSupplyIntentStore.bound(next, root), "Sale continuation renewed lifetime or changed field/actor/root ownership.");
			final var codec = new PhantomGoalStateCodec();
			PhantomAssertions.assertEquals(next, codec.decode(codec.encode(next)), "Bounded sale continuation lost item/ordinal identity at restart.");
			final var withBuy = new java.util.ArrayList<>(remaining);
			withBuy.add(new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.BUY, 30004, 555, 3000400, 3947, 0, 1));
			final var restock = PhantomInventoryMaintenanceIntent.advance(next, new PhantomInventoryMaintenanceIntent.Spec(withBuy, 3, false, true), 324, "maintenance.supplies.rechecked_after_bank");
			PhantomAssertions.assertTrue(PhantomInventoryMaintenanceIntent.parse(restock).current().kind() == PhantomCommerceReceipt.OperationKind.BUY && restock.deadlineEpochMillis() == goal.deadlineEpochMillis() && restock.payloadText().equals(goal.payloadText()), "Confirmed sales could not fund a bounded supply BUY before the original field return.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(goal, remaining, paid, 156), "Unconfirmed operation was discarded.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, remaining, prepared, 156), "Prepared sale was treated as committed.");
			final var foreign = PhantomCommerceReceipt.prepared(2, 115, 4, request, prepared.before(), prepared.expectedAfter()).withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, remaining, foreign, 156), "Foreign profile receipt authorized another batch.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, java.util.List.of(sold), paid, 156), "Already sold exact object was proposed again.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, java.util.List.of(remaining.getFirst(), remaining.getFirst()), paid, 156), "Duplicate exact object entered a batch.");
			final var changedItem = new PhantomInventoryMaintenanceIntent.Operation(sold.kind(), 30138, 333, 11, 462, 449, 1);
			final var mixed = PhantomInventoryMaintenanceIntent.continueSales(completed, java.util.List.of(changedItem), paid, 156);
			PhantomAssertions.assertTrue(PhantomInventoryMaintenanceIntent.parse(mixed).current().itemId() == 462 && mixed.target().key().equals("462") && mixed.payloadText().equals(goal.payloadText()) && mixed.deadlineEpochMillis() == goal.deadlineEpochMillis(), "Another legal surplus item at the same counter forced another round trip or renewed ownership/lifetime.");
			final var changedCounter = new PhantomInventoryMaintenanceIntent.Operation(sold.kind(), 30138, 334, 11, 462, 449, 1);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomInventoryMaintenanceIntent.continueSales(completed, java.util.List.of(changedCounter), paid, 156), "Continuation changed the exact native counter.");
			context.record("supply115.sales", "Exact committed sale -> bounded mixed-item same-counter surplus -> codec/restart; original field/deadline retained; pending/replayed/duplicate/changed-counter requests rejected");
		});
		registry.add("01-level-thresholds-do-not-skip-next-grade", context ->
		{
			final int[] levels = {1, 19, 20, 39, 40, 51, 52, 60, 61, 75, 76, 79, 80, 83, 84, 85};
			final CrystalType[] grades = {CrystalType.NONE, CrystalType.NONE, CrystalType.D, CrystalType.D, CrystalType.C, CrystalType.C, CrystalType.B, CrystalType.B, CrystalType.A, CrystalType.A, CrystalType.S, CrystalType.S, CrystalType.S80, CrystalType.S80, CrystalType.S84, CrystalType.S84};
			for (int index = 0; index < levels.length; index++)
			{
				PhantomAssertions.assertEquals(grades[index], PhantomNativeEquipmentPolicy.targetGrade(levels[index], 7), "Progression target bypassed its H5 level boundary: " + levels[index]);
			}
			context.record("supply071.thresholds", "NG1-19/D20/C40/B52/A61/S76/S80at80/S84at84; no equipment grants");
		});
		registry.add("02-level-target-never-invents-native-expertise", context ->
		{
			for (CrystalType grade : CrystalType.values())
			{
				PhantomAssertions.assertEquals(grade, PhantomNativeEquipmentPolicy.targetGrade(85, grade.getLevel()), "Level target invented unavailable expertise.");
			}
			PhantomAssertions.assertEquals(CrystalType.NONE, PhantomNativeEquipmentPolicy.targetGrade(85, -1), "Missing expertise did not fail closed.");
			PhantomAssertions.assertEquals(CrystalType.S84, PhantomNativeEquipmentPolicy.targetGrade(85, 99), "Expertise target exceeded native crystal grades.");
			context.record("supply071.expertise", "Native expertise caps target independently of level; no Player mutation");
		});
		registry.add("03-restart-counter-rebind-cannot-repeat-prepared-or-paid-sale", context ->
		{
			final long now = System.currentTimeMillis();
			final var root = new PhantomGoal(71, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc", "20001"), 1, 0, "background.farm", java.util.List.of(), null, "farm.background", 500, 0, 0, 0, java.util.Map.of(), "fixture071.native_root", 1);
			final var binding = new PhantomSupplyIntentStore.Binding(1, 111, 0, 0, 222, new PhantomNavigationPoint(100, 200, -300, 0), 0);
			final var operation = new PhantomInventoryMaintenanceIntent.Operation(PhantomCommerceReceipt.OperationKind.SELL, 30138, 333, 11, 18, 444, 1);
			final var goal = PhantomInventoryMaintenanceIntent.create(root, binding, java.util.List.of(operation), 4, 100, now + 60_000, now, false);
			final var rebound = PhantomInventoryMaintenanceIntent.rebindCounter(goal, 555, null);
			final var changed = PhantomInventoryMaintenanceIntent.parse(rebound).current();
			PhantomAssertions.assertEquals(new PhantomInventoryMaintenanceIntent.Operation(operation.kind(), operation.npcId(), 555, operation.listId(), operation.itemId(), operation.itemObjectId(), operation.count()), changed, "Restart rebound changed economic authority beyond the ephemeral NPC object.");
			PhantomAssertions.assertTrue(rebound.revision() == goal.revision() + 1 && rebound.deadlineEpochMillis() == goal.deadlineEpochMillis() && rebound.constraints().equals(goal.constraints()) && rebound.payloadText().equals(goal.payloadText()), "Restart rebound reset lifetime, binding or progress.");
			PhantomAssertions.assertEquals(rebound, new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(rebound)), "Rebound source did not survive the existing durable codec.");
			final var request = new PhantomCommerceReceipt.OperationRequest(operation.kind(), operation.npcId(), operation.npcObjectId(), operation.listId(), operation.itemId(), operation.itemObjectId(), operation.count(), 100, 0, 0, 0, "", 0, 0, 0);
			final var before = new PhantomCommerceReceipt.ConservationFacts(0, 1, 1, 0, 100, 200, -300);
			final var after = new PhantomCommerceReceipt.ConservationFacts(100, 0, 0, 0, 100, 200, -300);
			final var prepared = PhantomCommerceReceipt.prepared(1, goal.goalId(), goal.revision(), request, before, after);
			for (var receipt : java.util.List.of(prepared, prepared.withState(PhantomCommerceReceipt.State.COMMITTING), prepared.withState(PhantomCommerceReceipt.State.COMMITTING).withState(PhantomCommerceReceipt.State.COMMITTED)))
			{
				PhantomAssertions.assertEquals(goal, PhantomInventoryMaintenanceIntent.rebindCounter(goal, 555, receipt), "Prepared or paid operation was rebound into a second sale.");
			}
			PhantomAssertions.assertTrue(PhantomSupplyIntentStore.bound(rebound, root), "Restart rebound detached the original farm root.");
			context.record("supply071.recovery", "Only unprepared ephemeral NPC object changes; durable deadline/item/count/root binding conserved; prepared/committing/committed source immutable");
		});
		registry.add("04-future-grade-offline-quote-preserves-current-weapon-and-earmark", context ->
		{
			final var field = new PhantomNavigationPoint(100, 200, -300, 0);
			final var old = new PhantomSupplyIntentStore.Binding(1, 111, 0, 0, 222, field, 0);
			PhantomAssertions.assertEquals("s1:1:111:0:0:222:100:200:-300:0", old.encode(), "Existing s1 binding bytes changed.");
			final var future = new PhantomSupplyIntentStore.Binding(1, 111, 0, 0, 222, field, 0, 129, 5000);
			final var goal = new PhantomGoal(71, "private.store.buy", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("economy.offer", "333;0;" + "a".repeat(64) + ";0;100;B:444:10:10:1463"), 1, 0, null, java.util.List.of(), null, "private.store.buy", 700, 0, 100, System.currentTimeMillis() + 60_000, java.util.Map.of(), "supplies.offline.low_stock", 4, future.encode());
			final var restored = new PhantomGoalStateCodec().decode(new PhantomGoalStateCodec().encode(goal));
			PhantomAssertions.assertEquals(future, PhantomSupplyIntentStore.Binding.parse(restored), "Future-grade earmark was lost across restart.");
			PhantomAssertions.assertTrue(future.weaponObjectId() == old.weaponObjectId() && future.prospectiveWeaponTemplateId() == 129 && future.equipmentReserve() == 5000, "Preparation replaced the currently equipped weapon authority.");
			org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.parse(restored);
			final var retained = org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.pause(restored, System.currentTimeMillis());
			PhantomAssertions.assertEquals(restored, retained, "Store/death added unsupported journey constraints to an exact social quote.");
			org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.parse(retained);
			final var committed = restored.withStatus(PhantomGoalStatus.COMPLETED);
			PhantomAssertions.assertEquals(10_000L, org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.remaining(committed, committed.deadlineEpochMillis() - 10_000), "Paid offline return lost its original remaining route budget after commit.");
			PhantomAssertions.assertEquals(0L, org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.remaining(committed, committed.deadlineEpochMillis()), "Paid offline return renewed an expired deadline.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomSupplyIntentStore.Binding(1, 111, 0, 0, 222, field, 0, 0, 5000), "Earmark without a future weapon accepted.");
			final var malformed = new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision(), old.encode().replace("s1:", "s2:"));
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSupplyIntentStore.Binding.parse(malformed), "Incomplete future binding was accepted.");
			context.record("supply071.futureQuote", "s1 byte-exact; s2 current weapon/root/field retained with future native template and reserved gear budget; exact social parser and durable Goal codec preserved");
		});
		registry.add("05-ordinary-paid-cp-source-keeps-native-price-and-npc-eligibility", context ->
		{
			final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			final var list = factory.newDocumentBuilder().parse(context.moduleRoot().resolve("dist/game/data/buylists/3008100.xml").toFile());
			PhantomAssertions.assertEquals("30081", list.getElementsByTagName("npc").item(0).getTextContent().trim(), "Paid CP source lost its ordinary Helvetia native NPC binding.");
			final var nativeItems = factory.newDocumentBuilder().parse(context.moduleRoot().resolve("dist/game/data/stats/items/05500-05599.xml").toFile());
			for (int id : new int[] {5591, 5592})
			{
				final long price = id == 5591 ? 200 : 500;
				int offers = 0; final var lines = list.getElementsByTagName("item");
				for (int index = 0; index < lines.getLength(); index++) { final var line = (org.w3c.dom.Element) lines.item(index); if (Integer.toString(id).equals(line.getAttribute("id"))) { offers++; PhantomAssertions.assertTrue(!line.hasAttribute("price"), "Ordinary CP shop bypassed native reference price/tax authority."); } }
				PhantomAssertions.assertEquals(1, offers, "Ordinary paid CP source absent or duplicated: " + id);
				final var templates = nativeItems.getElementsByTagName("item"); boolean matched = false;
				for (int index = 0; index < templates.getLength(); index++)
				{
					final var item = (org.w3c.dom.Element) templates.item(index); if (!Integer.toString(id).equals(item.getAttribute("id"))) { continue; }
					final var settings = item.getElementsByTagName("set");
					for (int setting = 0; setting < settings.getLength(); setting++) { final var value = (org.w3c.dom.Element) settings.item(setting); if ("price".equals(value.getAttribute("name"))) { PhantomAssertions.assertEquals(Long.toString(price), value.getAttribute("val"), "CP native reference price changed."); matched = true; } }
				}
				PhantomAssertions.assertTrue(matched, "CP source has no confirmed native reference price.");
			}
			context.record("supply071.cpSource", "NPC30081 ordinary consumables list3008100;5591base200/5592base500 before native tax; no conditional SevenSigns/CB/gift source");
		});
	}
}
