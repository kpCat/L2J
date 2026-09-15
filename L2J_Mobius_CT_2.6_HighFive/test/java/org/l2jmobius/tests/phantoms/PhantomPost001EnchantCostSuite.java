package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemGroupsData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.phantoms.economy.PhantomCanonicalEnchantRoutes;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Failure;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Quote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Route;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Step;

public final class PhantomPost001EnchantCostSuite implements PhantomTestSuite
{
	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		ConfigLoader.init();
		ItemData.getInstance();
		EnchantItemGroupsData.getInstance();
		EnchantItemData.getInstance();
	}

	@Override
	public String id()
	{
		return "post001-enchant-cost";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("guaranteed-safe-steps", context ->
		{
			near(100, PhantomEnchantExpectedCost.quote(100, List.of()).fairValue());
			final Quote safe = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, 1, 10, 0, Failure.DESTROY)), step(1, route(955, 1, 10, 0, Failure.DESTROY)), step(2, route(955, 1, 10, 0, Failure.DESTROY))));
			near(130, safe.fairValue());
			near(1, safe.baseItemsConsumed());
			near(0, safe.itemsDestroyed());
			near(3, safe.scrollCounts().get(955));
		});
		registry.add("canonical-failure-states-and-consumption", context ->
		{
			final Quote normal = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, .66, 10, 0, Failure.DESTROY))));
			near(100 + ((10 + (.34 * 100)) / .66), normal.fairValue());
			near(.34 / .66, normal.itemsDestroyed());
			near(1 / .66, normal.scrollCounts().get(955));
			final Quote blessed = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(6575, .66, 10, 0, Failure.RESET_TO_ZERO))));
			near(0, blessed.itemsDestroyed());
			near(.34 / .66, blessed.resetsToZero());
			near(100 + (10 / .66), blessed.fairValue());
			final Quote retain = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(22007, .66, 10, 0, Failure.RETAIN_LEVEL))));
			near(0, retain.resetsToZero());
			near(100 + (10 / .66), retain.fairValue());
		});
		registry.add("chance-scroll-value-support-and-unsafe-bound", context ->
		{
			final Quote base = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, .66, 10, 0, Failure.DESTROY))));
			final Quote betterChance = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, .9, 10, 0, Failure.DESTROY))));
			final Quote scarceScroll = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, .66, 20, 0, Failure.DESTROY))));
			if (!(betterChance.fairValue() < base.fairValue()) || !(scarceScroll.fairValue() > base.fairValue()))
			{
				throw new AssertionError("Chance and scarcity must affect expected inputs.");
			}
			final Route supported = new Route(955, 12345, 1, 10, 5, 0, Failure.DESTROY);
			final Quote optimal = PhantomEnchantExpectedCost.quote(100, List.of(new Step(0, List.of(route(955, .66, 10, 0, Failure.DESTROY), supported))));
			near(115, optimal.fairValue());
			near(1, optimal.supportCounts().get(12345));
			final Quote twoSteps = PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, .66, 10, 0, Failure.DESTROY)), step(1, route(955, .66, 10, 0, Failure.DESTROY))));
			if (!(twoSteps.fairValue() > base.fairValue()))
			{
				throw new AssertionError("Higher enchant must consume more expected inputs.");
			}
			try
			{
				PhantomEnchantExpectedCost.quote(100, List.of(step(0, route(955, 1.0e-12, 10, 0, Failure.RETAIN_LEVEL))));
				throw new AssertionError("Near-zero probability must fail closed.");
			}
			catch (IllegalArgumentException expected)
			{
			}
		});
		registry.add("loaded-stock-h5-scroll-and-group", context ->
		{
			final EnchantScroll scroll = EnchantItemData.getInstance().getScrolls().stream().filter(candidate -> candidate.getId() == 955).findFirst().orElseThrow();
			final List<ItemTemplate> candidates = new ArrayList<>();
			for (ItemTemplate item : ItemData.getInstance().getAllWeapons())
			{
				try
				{
					if (scroll.isValid(item, 3, null) && (scroll.getChance(item, 3) == 66))
					{
						candidates.add(item);
					}
				}
				catch (RuntimeException failure)
				{
					throw new AssertionError("Stock candidate check failed for item " + item.getId() + ": " + failure.getMessage(), failure);
				}
			}
			final ItemTemplate target = candidates.stream().min(Comparator.comparingInt(ItemTemplate::getId)).orElseThrow();
			final List<Step> steps = PhantomCanonicalEnchantRoutes.steps(target, 4, List.of(), id -> id == 955 ? 10 : -1);
			near(1, steps.get(0).routes().get(0).chance());
			near(1, steps.get(1).routes().get(0).chance());
			near(1, steps.get(2).routes().get(0).chance());
			near(.66, steps.get(3).routes().get(0).chance());
			final Quote quote = PhantomEnchantExpectedCost.quote(100, steps);
			if ((quote.itemsDestroyed() <= 0) || (quote.scrollCounts().get(955) <= 4))
			{
				throw new AssertionError("Stock fourth step must expose expected breakage and retry scrolls.");
			}
			try
			{
				PhantomCanonicalEnchantRoutes.steps(target, 4, List.of(), id -> -1);
				throw new AssertionError("Unanchored scroll cannot become a Phantom quote.");
			}
			catch (IllegalArgumentException expected)
			{
			}
		});
	}

	private static Step step(int level, Route route)
	{
		return new Step(level, List.of(route));
	}

	private static Route route(int scrollId, double chance, double scrollValue, double recovery, Failure failure)
	{
		return new Route(scrollId, 0, chance, scrollValue, 0, recovery, failure);
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || (Math.abs(expected - actual) > (1.0e-8 * Math.max(1, Math.abs(expected)))))
		{
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}
}
