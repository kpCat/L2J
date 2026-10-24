/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy.Disposition;

/** Actual recipe matrix: unknown market value cannot become low-grade NPC liquidation. */
public final class PhantomLootDisposition068Checks
{
	public static void main(String[] args)
	{
		ConfigLoader.init(); ItemData.getInstance(); RecipeData.getInstance();
		final var policy = PhantomLootDispositionPolicy.getInstance();
		final var ordinaryJunk = ItemData.getInstance().getTemplate(1); // Stock no-grade Short Sword; evaluate() still protects equipped/needed items.
		PhantomAssertions.assertTrue(ordinaryJunk != null && ordinaryJunk.getCrystalType().getLevel() == 0, "Ordinary junk control is not stock no-grade equipment.");
		PhantomAssertions.assertEquals(Disposition.SELL_NPC, policy.category(ordinaryJunk), "Unneeded ordinary equipment lost its NPC liquidation category.");
		final var commonB = ItemData.getInstance().getTemplate(12200);
		PhantomAssertions.assertTrue(commonB != null && commonB.isCommon() && commonB.isCrystallizable() && commonB.getCrystalCount() == 5 && commonB.getCrystalItemId() == 1460, "Common12200 native crystal facts changed; the item name is not eligibility evidence.");
		PhantomAssertions.assertEquals(Disposition.CRYSTALLIZE, policy.category(commonB), "Unused native Common B crystal-value candidate was hidden behind warehouse categorization; actual ownership/skill/inspection still gate evaluate/apply.");
		final var lowParts = policy.recipeRoles().entrySet().stream().filter(entry -> entry.getValue().keyPart() && entry.getValue().highestProductGrade() >= 1 && entry.getValue().highestProductGrade() <= 2).toList();
		PhantomAssertions.assertTrue(!lowParts.isEmpty(), "Actual D/C recipe matrix is empty.");
		for (var entry : lowParts)
		{
			PhantomAssertions.assertTrue(policy.category(ItemData.getInstance().getTemplate(entry.getKey())) != Disposition.SELL_NPC, "Unknown D/C crafting resource was sold from grade/NPC-price only: " + entry.getKey());
		}
		final var dc = new PhantomLootDispositionPolicy.RecipeRole(2, false, false, 2, 1);
		final var high = new PhantomLootDispositionPolicy.RecipeRole(3, false, false, 2, 1);
		final var shared = new PhantomLootDispositionPolicy.RecipeRole(1, true, false, 20, 8);
		final var valuable = new PhantomLootDispositionPolicy.MarketEvidence(true, 1000, true);
		final var cheap = new PhantomLootDispositionPolicy.MarketEvidence(true, 5, false);
		final Object[][] matrix = {
			{"valuable-dc-spoil-resource", dc, 10L, valuable, false, Disposition.MARKET_RESERVE},
			{"confirmed-low-value-surplus", dc, 10L, cheap, false, Disposition.SELL_NPC},
			{"demanded-b-component", high, 10L, valuable, false, Disposition.MARKET_RESERVE},
			{"shared-multiple-recipe-material", shared, 10L, cheap, false, Disposition.MARKET_RESERVE},
			{"full-bank-preserves-valuable-stock", dc, 10L, valuable, true, Disposition.MARKET_RESERVE},
			{"unknown-value-preserved", dc, 10L, PhantomLootDispositionPolicy.MarketEvidence.unknown(), false, Disposition.MARKET_RESERVE}
		};
		for (var row : matrix) { PhantomAssertions.assertEquals(row[5], PhantomLootDispositionPolicy.recipeSurplus((PhantomLootDispositionPolicy.RecipeRole) row[1], (long) row[2], (PhantomLootDispositionPolicy.MarketEvidence) row[3], (boolean) row[4]), "Surplus matrix: " + row[0]); }
		System.out.println("OWN068_LOOT_UNKNOWN_MATRIX_PASS dcParts=" + lowParts.size() + "; ordinaryJunk=1; surplusCases=" + matrix.length + "; DB_WRITES=0");
	}
}
