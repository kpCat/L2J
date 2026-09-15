package org.l2jmobius.gameserver.phantoms.economy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic expected input cost for a finite set of validated enchant routes. */
public final class PhantomEnchantExpectedCost
{
	private static final int MAX_LEVEL = 32;
	private static final int MAX_POLICY_ROUNDS = 128;
	private static final double MAX_EXPECTATION = 1.0e15;

	private PhantomEnchantExpectedCost()
	{
	}

	public static Quote quote(double baseValue, List<Step> steps)
	{
		if (!Double.isFinite(baseValue) || (baseValue <= 0) || (baseValue > MAX_EXPECTATION) || (steps == null) || (steps.size() > MAX_LEVEL))
		{
			throw new IllegalArgumentException("Unsafe enchant base or target.");
		}
		if (steps.isEmpty())
		{
			return new Quote(baseValue, 1, 0, 0, Map.of(), Map.of(), List.of());
		}
		final int count = steps.size();
		final Route[] policy = new Route[count];
		for (int level = 0; level < count; level++)
		{
			final Step step = steps.get(level);
			if ((step == null) || (step.level() != level) || step.routes().isEmpty() || (step.routes().size() > 64))
			{
				throw new IllegalArgumentException("Missing legal enchant route.");
			}
			for (Route route : step.routes())
			{
				validate(route);
				if (route.crystalRecoveryValue() > baseValue)
				{
					throw new IllegalArgumentException("Unbacked enchant crystal recovery.");
				}
			}
			policy[level] = step.routes().get(0);
		}
		boolean optimal = false;
		for (int round = 0; round < MAX_POLICY_ROUNDS; round++)
		{
			final double[] values = solve(policy, baseValue, null);
			boolean changed = false;
			for (int level = 0; level < count; level++)
			{
				Route best = policy[level];
				double bestCost = actionCost(best, level, values, baseValue);
				for (Route route : steps.get(level).routes())
				{
					final double cost = actionCost(route, level, values, baseValue);
					if (cost < (bestCost - (1.0e-8 * Math.max(1, bestCost))))
					{
						best = route;
						bestCost = cost;
					}
				}
				if (best != policy[level])
				{
					policy[level] = best;
					changed = true;
				}
			}
			if (!changed)
			{
				optimal = true;
				break;
			}
		}
		if (!optimal)
		{
			throw new IllegalArgumentException("Enchant policy did not converge.");
		}
		final double enchantCost = solve(policy, baseValue, null)[0];
		final double destroyed = solve(policy, baseValue, route -> route.failure() == Failure.DESTROY ? 1 - route.chance() : 0)[0];
		final double resets = solve(policy, baseValue, route -> route.failure() == Failure.RESET_TO_ZERO ? 1 - route.chance() : 0)[0];
		final Map<Integer, Double> scrolls = new LinkedHashMap<>();
		final Map<Integer, Double> supports = new LinkedHashMap<>();
		for (Route route : policy)
		{
			scrolls.computeIfAbsent(route.scrollId(), id -> solve(policy, baseValue, candidate -> candidate.scrollId() == id ? 1 : 0)[0]);
			if (route.supportId() > 0)
			{
				supports.computeIfAbsent(route.supportId(), id -> solve(policy, baseValue, candidate -> candidate.supportId() == id ? 1 : 0)[0]);
			}
		}
		if ((baseValue + enchantCost) > MAX_EXPECTATION)
		{
			throw new IllegalArgumentException("Enchanted value exceeds safe bound.");
		}
		return new Quote(baseValue + enchantCost, 1 + destroyed, destroyed, resets, Map.copyOf(scrolls), Map.copyOf(supports), List.copyOf(Arrays.asList(policy)));
	}

	private static void validate(Route route)
	{
		if ((route == null) || (route.scrollId() <= 0) || (route.supportId() < 0) || !Double.isFinite(route.chance()) || (route.chance() <= 1.0e-9) || (route.chance() > 1) || !Double.isFinite(route.scrollValue()) || (route.scrollValue() <= 0) || !Double.isFinite(route.supportValue()) || (route.supportValue() < 0) || (route.supportId() == 0 && route.supportValue() != 0) || !Double.isFinite(route.crystalRecoveryValue()) || (route.crystalRecoveryValue() < 0) || (route.failure() == null) || (route.failure() != Failure.DESTROY && route.crystalRecoveryValue() != 0))
		{
			throw new IllegalArgumentException("Unsafe enchant route.");
		}
	}

	private static double actionCost(Route route, int level, double[] values, double baseValue)
	{
		final double fail = 1 - route.chance();
		final double replacement = route.failure() == Failure.DESTROY ? baseValue - route.crystalRecoveryValue() : 0;
		final int failState = route.failure() == Failure.RETAIN_LEVEL ? level : 0;
		return route.scrollValue() + route.supportValue() + (fail * (replacement + values[failState])) + (route.chance() * (level + 1 == values.length ? 0 : values[level + 1]));
	}

	private static double[] solve(Route[] policy, double baseValue, Immediate immediate)
	{
		final int count = policy.length;
		final double[][] matrix = new double[count][count + 1];
		for (int level = 0; level < count; level++)
		{
			final Route route = policy[level];
			final double fail = 1 - route.chance();
			matrix[level][level] = 1;
			if (level + 1 < count)
			{
				matrix[level][level + 1] -= route.chance();
			}
			matrix[level][route.failure() == Failure.RETAIN_LEVEL ? level : 0] -= fail;
			if (immediate == null)
			{
				matrix[level][count] = route.scrollValue() + route.supportValue() + (route.failure() == Failure.DESTROY ? fail * (baseValue - route.crystalRecoveryValue()) : 0);
			}
			else
			{
				matrix[level][count] = immediate.count(route);
			}
		}
		for (int column = 0; column < count; column++)
		{
			int pivot = column;
			for (int row = column + 1; row < count; row++)
			{
				if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column]))
				{
					pivot = row;
				}
			}
			if (Math.abs(matrix[pivot][column]) < 1.0e-12)
			{
				throw new IllegalArgumentException("Unsafe enchant probability cycle.");
			}
			final double[] temp = matrix[column];
			matrix[column] = matrix[pivot];
			matrix[pivot] = temp;
			final double divisor = matrix[column][column];
			for (int at = column; at <= count; at++)
			{
				matrix[column][at] /= divisor;
			}
			for (int row = 0; row < count; row++)
			{
				if (row != column)
				{
					final double factor = matrix[row][column];
					for (int at = column; at <= count; at++)
					{
						matrix[row][at] -= factor * matrix[column][at];
					}
				}
			}
		}
		final double[] result = new double[count];
		for (int level = 0; level < count; level++)
		{
			result[level] = matrix[level][count];
			if (!Double.isFinite(result[level]) || (result[level] < -1.0e-7) || (result[level] > MAX_EXPECTATION))
			{
				throw new IllegalArgumentException("Enchant expectation exceeds safe bound.");
			}
		}
		return result;
	}

	private interface Immediate
	{
		double count(Route route);
	}

	public record Step(int level, List<Route> routes)
	{
		public Step
		{
			routes = routes == null ? List.of() : List.copyOf(routes);
		}
	}

	public record Route(int scrollId, int supportId, double chance, double scrollValue, double supportValue, double crystalRecoveryValue, Failure failure)
	{
	}

	public enum Failure
	{
		DESTROY,
		RESET_TO_ZERO,
		RETAIN_LEVEL
	}

	public record Quote(double fairValue, double baseItemsConsumed, double itemsDestroyed, double resetsToZero, Map<Integer, Double> scrollCounts, Map<Integer, Double> supportCounts, List<Route> chosenRoutes)
	{
	}
}
