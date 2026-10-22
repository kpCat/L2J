/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.config.custom.ChampionSelectionPolicy;
import org.l2jmobius.gameserver.model.actor.enums.npc.ChampionType;

/** DB-free final probability and eligibility contracts. */
public final class ChampionMonsters068PolicySuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "champion068-policy";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-final-probabilities-boundaries-and-red-compatibility", this::selection);
		registry.add("02-boss-minion-quest-instance-and-level-fences", this::eligibility);
	}

	private void selection(PhantomTestContext context)
	{
		for (int red : new int[] {0, 1, 5, 50, 100})
		{
			for (int blue : new int[] {0, 1, 5, 50, 100})
			{
				int redCount = 0;
				int blueCount = 0;
				for (int roll = 0; roll < 100; roll++)
				{
					final ChampionType actual = ChampionSelectionPolicy.select(red, blue, true, true, roll);
					redCount += actual == ChampionType.RED ? 1 : 0;
					blueCount += actual == ChampionType.BLUE ? 1 : 0;
					if ((red + blue) > 100)
					{
						PhantomAssertions.assertEquals(ChampionType.NORMAL, actual, "Overcommitted probabilities did not fail closed.");
					}
				}
				PhantomAssertions.assertEquals((red + blue) <= 100 ? red : 0, redCount, "Red final probability changed.");
				PhantomAssertions.assertEquals((red + blue) <= 100 ? blue : 0, blueCount, "Blue final probability became conditional on red.");
			}
		}
		PhantomAssertions.assertEquals(ChampionType.RED, ChampionSelectionPolicy.select(5, 10, true, true, 4), "Last red bucket missing.");
		PhantomAssertions.assertEquals(ChampionType.BLUE, ChampionSelectionPolicy.select(5, 10, true, true, 5), "First blue bucket missing.");
		PhantomAssertions.assertEquals(ChampionType.BLUE, ChampionSelectionPolicy.select(5, 10, true, true, 14), "Last blue bucket missing.");
		PhantomAssertions.assertEquals(ChampionType.NORMAL, ChampionSelectionPolicy.select(5, 10, true, true, 15), "Normal bucket missing.");
		PhantomAssertions.assertEquals(ChampionType.BLUE, ChampionSelectionPolicy.select(5, 10, false, true, 0), "Red-ineligible spawn lost independent blue frequency.");
		PhantomAssertions.assertEquals(ChampionType.NORMAL, ChampionSelectionPolicy.select(5, 10, false, true, 10), "Blue was renormalized after red exclusion.");
		PhantomAssertions.assertFalse(ChampionSelectionPolicy.validFrequencies(-1, 1), "Negative frequency accepted.");
		PhantomAssertions.assertFalse(ChampionSelectionPolicy.validFrequencies(1, 101), "Frequency above 100 accepted.");
		context.record("champion068.policy", "25 distributions x 100 buckets; exact final frequency counts");
	}

	private void eligibility(PhantomTestContext context)
	{
		PhantomAssertions.assertTrue(ChampionSelectionPolicy.eligible(true, false, false, false, false, 20, 0, true, 20, 70, false), "Minimum level rejected.");
		PhantomAssertions.assertTrue(ChampionSelectionPolicy.eligible(true, false, false, false, false, 70, 0, true, 20, 70, false), "Maximum level rejected.");
		for (int flag = 0; flag < 6; flag++)
		{
			PhantomAssertions.assertFalse(ChampionSelectionPolicy.eligible(flag != 0, flag == 1, flag == 2, flag == 3, flag == 4, 40, 0, flag != 5, 20, 70, false), "Eligibility exclusion leaked: " + flag);
		}
		for (int level : new int[] {19, 71})
		{
			PhantomAssertions.assertFalse(ChampionSelectionPolicy.eligible(true, false, false, false, false, level, 0, true, 20, 70, false), "Out-of-range level accepted.");
		}
		PhantomAssertions.assertFalse(ChampionSelectionPolicy.eligible(true, false, false, false, false, 40, 7, true, 20, 70, false), "Instance exclusion leaked.");
		PhantomAssertions.assertTrue(ChampionSelectionPolicy.eligible(true, false, false, false, false, 40, 7, true, 20, 70, true), "Allowed instance rejected.");
		context.record("champion068.eligibility", "monster/quest/undying/raid/minion/enabled/levels/instances");
	}
}
