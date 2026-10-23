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
