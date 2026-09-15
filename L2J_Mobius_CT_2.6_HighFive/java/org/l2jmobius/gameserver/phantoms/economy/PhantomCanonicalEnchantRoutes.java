package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Failure;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Route;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost.Step;

/** Resolves legal steps from loaded stock H5 enchant owners; missing fair input anchors fail closed. */
public final class PhantomCanonicalEnchantRoutes
{
	private PhantomCanonicalEnchantRoutes()
	{
	}

	public static List<Step> steps(ItemTemplate target, int targetLevel, Collection<Integer> supportIds, IntToDoubleFunction fairInputValue)
	{
		if ((target == null) || !target.isEnchantable() || (targetLevel < 0) || (targetLevel > 32) || (supportIds == null) || (fairInputValue == null))
		{
			throw new IllegalArgumentException("Unsafe enchant route request.");
		}
		final List<EnchantScroll> scrolls = EnchantItemData.getInstance().getScrolls().stream().sorted(Comparator.comparingInt(EnchantScroll::getId)).toList();
		final List<Integer> sortedSupports = supportIds.stream().distinct().sorted().toList();
		final Map<Integer, Double> fair = new HashMap<>();
		final List<Step> steps = new ArrayList<>();
		for (int level = 0; level < targetLevel; level++)
		{
			final List<Route> routes = new ArrayList<>();
			for (EnchantScroll scroll : scrolls)
			{
				if (!scroll.isValid(target, level, null))
				{
					continue;
				}
				final double scrollValue = fair.computeIfAbsent(scroll.getId(), fairInputValue::applyAsDouble);
				if (!safeValue(scrollValue))
				{
					continue;
				}
				final double chance = scroll.getChance(target, level);
				add(routes, scroll, null, chance, scrollValue, 0);
				for (int supportId : sortedSupports)
				{
					final EnchantSupportItem support = EnchantItemData.getInstance().getSupportItemById(supportId);
					if ((support == null) || !scroll.isValid(target, level, support))
					{
						continue;
					}
					final double supportValue = fair.computeIfAbsent(supportId, fairInputValue::applyAsDouble);
					if (safeValue(supportValue))
					{
						add(routes, scroll, support, chance, scrollValue, supportValue);
					}
				}
			}
			if (routes.isEmpty() || (routes.size() > 64))
			{
				throw new IllegalArgumentException("No priced legal enchant route for level " + level + ".");
			}
			steps.add(new Step(level, routes));
		}
		return List.copyOf(steps);
	}

	private static boolean safeValue(double value)
	{
		return Double.isFinite(value) && (value > 0) && (value <= 1.0e15);
	}

	private static void add(List<Route> routes, EnchantScroll scroll, EnchantSupportItem support, double groupChance, double scrollValue, double supportValue)
	{
		if (!Double.isFinite(groupChance) || (groupChance < 0))
		{
			return;
		}
		final double chance = Math.min(100, groupChance + scroll.getBonusRate() + (support == null ? 0 : support.getBonusRate())) / 100;
		if ((chance <= 1.0e-9) || (chance > 1))
		{
			return;
		}
		final Failure failure = scroll.isSafe() ? Failure.RETAIN_LEVEL : scroll.isBlessed() ? Failure.RESET_TO_ZERO : Failure.DESTROY;
		routes.add(new Route(scroll.getId(), support == null ? 0 : support.getId(), chance, scrollValue, supportValue, 0, failure));
	}
}
