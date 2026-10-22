/*
 * This file is part of the L2J Mobius project.
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.data.xml.OptionData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.conditions.ConditionPlayerLevel;
import org.l2jmobius.gameserver.model.options.Augmentation;
import org.l2jmobius.gameserver.model.options.Options;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.model.stats.functions.FuncTemplate;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Native stock option checks for isolated Players without an already active augmentation. */
public final class QoLAdventure067AugmentationChecks
{
	private QoLAdventure067AugmentationChecks()
	{
	}

	public static void nativePositiveBonuses(Player player, double expectedMultiplier)
	{
		final double baselinePDef = player.getStat().calcStat(Stat.POWER_DEFENCE, 1000);
		final double baselineMDef = player.getStat().calcStat(Stat.MAGIC_DEFENCE, 1000);
		// Real stock options 1 and 2: additive P. Def. 15.45 and M. Def. 12.33.
		final Augmentation augmentation = new Augmentation(131073);
		try
		{
			augmentation.applyBonus(player);
			assertNear(baselinePDef + (15.45 * expectedMultiplier), player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Personal augmentation did not scale the native positive P. Def. bonus.");
			assertNear(baselineMDef + (12.33 * expectedMultiplier), player.getStat().calcStat(Stat.MAGIC_DEFENCE, 1000), "Personal augmentation did not scale the native positive M. Def. bonus.");
			augmentation.applyBonus(player);
			assertNear(baselinePDef + (15.45 * expectedMultiplier), player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Applying the same augmentation twice duplicated its bonus.");
			augmentation.removeBonus(player);
			assertNear(baselinePDef, player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Augmentation removal left a P. Def. bonus behind.");
			assertNear(baselineMDef, player.getStat().calcStat(Stat.MAGIC_DEFENCE, 1000), "Augmentation removal left an M. Def. bonus behind.");
			augmentation.removeBonus(player);
			assertNear(baselinePDef, player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Removing an inactive augmentation subtracted another bonus.");
			augmentation.applyBonus(player);
			assertNear(baselinePDef + (15.45 * expectedMultiplier), player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Reapplying the augmentation did not restore the same native bonus.");
			assertNear(15.45, OptionData.getInstance().getOptions(1).getStatFuncs(null, player).get(0).getValue(), "Personal augmentation mutated the shared P. Def. option template.");
			assertNear(12.33, OptionData.getInstance().getOptions(2).getStatFuncs(null, player).get(0).getValue(), "Personal augmentation mutated the shared M. Def. option template.");
		}
		finally
		{
			augmentation.removeBonus(player);
		}
		assertNear(baselinePDef, player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Final augmentation cleanup did not restore P. Def.");
		assertNear(baselineMDef, player.getStat().calcStat(Stat.MAGIC_DEFENCE, 1000), "Final augmentation cleanup did not restore M. Def.");
	}

	public static void ordinaryOptionsKeepStockBonuses(Player player)
	{
		final Options option = OptionData.getInstance().getOptions(1);
		final double baseline = player.getStat().calcStat(Stat.POWER_DEFENCE, 1000);
		try
		{
			option.apply(player);
			assertNear(baseline + 15.45, player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "An ordinary option application received the personal augmentation multiplier.");
		}
		finally
		{
			option.remove(player);
		}
		assertNear(baseline, player.getStat().calcStat(Stat.POWER_DEFENCE, 1000), "Ordinary option removal did not restore P. Def.");
	}

	public static void mixedFunctionsKeepNativeSemantics(Player player)
	{
		final Options mixed = new Options(670067);
		mixed.addFunc(new FuncTemplate(null, new ConditionPlayerLevel(1), "Add", 30, Stat.FIRE_POWER, 4));
		mixed.addFunc(new FuncTemplate(null, null, "Add", 30, Stat.FIRE_POWER, -3));
		mixed.addFunc(new FuncTemplate(null, null, "Mul", 20, Stat.FIRE_POWER, 1.5));
		mixed.addFunc(new FuncTemplate(null, null, "Sub", 30, Stat.FIRE_POWER, 2));
		mixed.addFunc(new FuncTemplate(null, new ConditionPlayerLevel(256), "Add", 30, Stat.FIRE_POWER, 100));
		assertNear(20, player.getStat().calcStat(Stat.FIRE_POWER, 20), "Mixed option check requires an isolated fire-power calculator.");
		final double[][] cases = {{2, 33}, {1, 29}, {5, 45}, {0, 29}, {5.01, 29}, {Double.NaN, 29}, {Double.POSITIVE_INFINITY, 29}};
		for (double[] row : cases)
		{
			try
			{
				mixed.apply(player, row[0]);
				assertNear(row[1], player.getStat().calcStat(Stat.FIRE_POWER, 20), "Mixed native functions changed a negative value, multiplier, condition or calculation order for factor " + row[0] + ".");
			}
			finally
			{
				mixed.remove(player);
			}
			assertNear(20, player.getStat().calcStat(Stat.FIRE_POWER, 20), "Mixed option removal lost the original function owner.");
		}
	}

	private static void assertNear(double expected, double actual, String message)
	{
		PhantomAssertions.assertTrue(Math.abs(expected - actual) < 0.000001, message + " Expected <" + expected + "> but was <" + actual + ">.");
	}
}
