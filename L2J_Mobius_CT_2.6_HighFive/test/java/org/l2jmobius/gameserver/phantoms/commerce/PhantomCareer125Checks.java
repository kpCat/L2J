/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;
import java.util.Map;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantGoalSpec;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

/** Child upgrade authority, not a replacement farm root or native payment claim. */
public final class PhantomCareer125Checks
{
	public static void main(String[] args) throws Exception
	{
		check(!DatabaseFactory.isInitialized(), "DB-free contract adopted JDBC");
		final var binding = new PhantomSupplyIntentStore.Binding(3, 1111, 2, 0, 111, new PhantomNavigationPoint(10, 20, -30, 0), 0);
		final var values = Map.of(PhantomEnchantGoalSpec.TARGET_CONSTRAINT, 111L, PhantomEnchantGoalSpec.DESIRED_CONSTRAINT, 1L,
			PhantomEnchantGoalSpec.MAXIMUM_ATTEMPTS_CONSTRAINT, 1L, PhantomEnchantGoalSpec.ATTEMPTS_USED_CONSTRAINT, 0L,
			PhantomEnchantGoalSpec.EXPENSE_USED_CONSTRAINT, 0L, PhantomEnchantGoalSpec.DESTRUCTION_CONSTRAINT, 1L, PhantomEnchantGoalSpec.REPLACEMENT_RESERVE_CONSTRAINT, 1_000_000L, PhantomSupplyIntentStore.ATTEMPT_REVISION, 7L);
		final var old = new PhantomGoal(125, "enchant.item", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("item.object", "111"), 1, 0,
			"native.enchant", List.of(new PhantomDomainRef("enchant.scroll", "951")), null, "enchant.item", 750, 1_000_000, 50_000, System.currentTimeMillis() + 60_000, values, "gear.native.enchant", 7, binding.encode());
		final var next = PhantomEnchantGoalSpec.parse(old).project(old, 1, true, 50_000, "result.success");
		check(old.payloadText().equals(next.payloadText()), "Native enchant projection discarded the current actor/class/root binding");
		final var started = PhantomJourneyLifetime.start(old, System.currentTimeMillis());
		check(PhantomEnchantGoalSpec.parse(started).maximumAttempts() == 1, "Canonical journey lifetime cannot coexist with exact enchant shape");
		final var validate = PhantomSupplyIntentStore.class.getDeclaredMethod("validUpgradeProjection", PhantomGoal.class, PhantomGoal.class);
		validate.setAccessible(true);
		check((boolean) validate.invoke(null, old, next), "Exact bounded native enchant projection was rejected");
		final var forged = new PhantomGoal(next.goalId(), next.goalType(), next.status(), next.subject(), new PhantomDomainRef("item.object", "222"), next.requiredAmount(), next.currentAmount(), next.acquisitionMethod(), next.validSources(), next.selectedAnchor(), next.purposeKey(), next.priority(), next.riskBudget(), next.expenseBudget(), next.deadlineEpochMillis(), next.constraints(), next.reasonKey(), next.revision(), next.payloadText());
		check(!(boolean) validate.invoke(null, old, forged), "Child completion can substitute another object");
		check(!(boolean) validate.invoke(null, next, next), "Terminal child can replay its native attempt");
		final var attemptRevision = PhantomSupplyIntentStore.class.getDeclaredMethod("attemptRevision", PhantomGoal.class);
		check((long) attemptRevision.invoke(null, old) == 7 && (long) attemptRevision.invoke(null, next) == 7, "Projected child lost original native attempt identity");
		final var settled = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyService.class.getDeclaredMethod("automaticAttemptSettled", org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State.class, boolean.class);
		settled.setAccessible(true);
		check(!(boolean) settled.invoke(null, org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State.INCONSISTENT, true), "Terminal projection hid uncertain payment");
		check(!(boolean) settled.invoke(null, org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State.COMMITTED, false), "Unprojected commit allowed a replacement");
		check((boolean) settled.invoke(null, org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State.ABORTED, false), "Safe unchanged abort cannot release the existing root");
		// The real stock C-SA entry, including native prepared-ID/enchant projection.
		final var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.nio.file.Path.of("data/multisell/1005.xml").toFile());
		final var first = (org.w3c.dom.Element) xml.getElementsByTagName("item").item(0);
		final var entry = new org.l2jmobius.gameserver.model.multisell.Entry(1);
		for (var node = first.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (!(node instanceof org.w3c.dom.Element element)) { continue; }
			final var ingredient = new org.l2jmobius.gameserver.model.multisell.Ingredient(Integer.parseInt(element.getAttribute("id")), Long.parseLong(element.getAttribute("count")), 0, false, false);
			if ("ingredient".equals(element.getTagName())) { entry.addIngredient(ingredient); } else if ("production".equals(element.getTagName())) { entry.addProduct(ingredient); }
		}
		final var source = new org.l2jmobius.gameserver.model.item.instance.Item(990125, org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(72));
		final var prepared = new org.l2jmobius.gameserver.model.multisell.PreparedEntry(entry, source, true, true, 0.05);
		check(prepared.getEntryId() == 100000 && prepared.getProducts().getFirst().getItemId() == 4681, "Stock prepared SA source changed");
		final var sa = Class.forName("org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa");
		final var exact = sa.getDeclaredMethod("ingredientCounts", org.l2jmobius.gameserver.model.multisell.Entry.class);
		check(exact.invoke(null, prepared).equals(Map.of(72, 1L, 4634, 1L, 2131, 97L, 57, 291000L)), "Native SA fee/material counts differ from stock prepared entry");
		entry.addIngredient(new org.l2jmobius.gameserver.model.multisell.Ingredient(57, 100, 0, true, false));
		final var taxed = new org.l2jmobius.gameserver.model.multisell.PreparedEntry(entry, source, true, true, 0.05);
		check(exact.invoke(null, taxed).equals(Map.of(72, 1L, 4634, 1L, 2131, 97L, 57, 291005L)), "Raw XML fee bypassed native prepared tax projection");
		final var before = Map.of(1, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(1, 72, 1, "INVENTORY", 0, 0),
			2, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(2, 4634, 1, "INVENTORY", 0, 0), 3, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(3, 2131, 100, "INVENTORY", 0, 0),
			4, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(4, 57, 1_000_000, "INVENTORY", 0, 0));
		final var inputs = Map.of(72, 1L, 4634, 1L, 2131, 97L, 57, 291000L);
		final var after = new java.util.HashMap<Integer, org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact>();
		after.put(3, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(3, 2131, 3, "INVENTORY", 0, 0));
		after.put(4, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(4, 57, 709000, "INVENTORY", 0, 0));
		after.put(5, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(5, 4681, 1, "INVENTORY", 0, 0));
		check(org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.conservedOutput(before, inputs, 4681, after) == 5, "Exact native compound payment/output was rejected");
		after.put(4, new org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.Fact(4, 57, 708999, "INVENTORY", 0, 0));
		check(org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.conservedOutput(before, inputs, 4681, after) == 0, "Extra debit was treated as committed SA");
		check(!DatabaseFactory.isInitialized(), "Upgrade validation initialized JDBC");
		System.out.println("TASK125_CHILD_PASS binding/lifetime/exact-projection/no-replay;DB=false;NATIVE_PAYMENT=NOT_RUN");
	}
	private static void check(boolean ok, String reason) { if (!ok) { throw new AssertionError(reason); } }
}
