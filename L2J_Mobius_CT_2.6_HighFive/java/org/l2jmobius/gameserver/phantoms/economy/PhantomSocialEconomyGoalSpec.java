/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;

/** Closed-world parser for the four Goal 022 C2 social economy goals. */
public sealed interface PhantomSocialEconomyGoalSpec permits PhantomSocialEconomyGoalSpec.DirectTrade, PhantomSocialEconomyGoalSpec.StoreBuy, PhantomSocialEconomyGoalSpec.StoreSell, PhantomSocialEconomyGoalSpec.Manufacture
{
	String TARGET_NAMESPACE = "economy.offer";
	String DIRECT_TRADE_GOAL = "trade.exchange";
	String STORE_BUY_GOAL = "private.store.buy";
	String STORE_SELL_GOAL = "private.store.sell";
	String MANUFACTURE_GOAL = "manufacture.item";

	int counterpartyCharacterObjectId();

	long counterpartyProfileId();

	PhantomEconomyOperation.Kind operationKind();

	static PhantomSocialEconomyGoalSpec parse(PhantomGoal goal)
	{
		if ((goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.target() == null) || !TARGET_NAMESPACE.equals(goal.target().namespace()) || !goal.goalType().equals(goal.purposeKey()) || !goal.constraints().isEmpty() || !goal.validSources().isEmpty() || (goal.selectedAnchor() != null) || (goal.acquisitionMethod() != null) || (goal.requiredAmount() != 1) || (goal.currentAmount() != 0))
		{
			throw new IllegalArgumentException("Goal is not an exact active social economy Goal.");
		}
		final String[] tokens = goal.target().key().split(";", -1);
		return switch (goal.goalType())
		{
			case DIRECT_TRADE_GOAL -> direct(tokens);
			case STORE_BUY_GOAL -> storeBuy(tokens);
			case STORE_SELL_GOAL -> storeSell(tokens);
			case MANUFACTURE_GOAL -> manufacture(tokens);
			default -> throw new IllegalArgumentException("Unknown social economy Goal type.");
		};
	}

	private static DirectTrade direct(String[] tokens)
	{
		if (tokens.length < 8)
		{
			throw new IllegalArgumentException("Direct-trade Goal is incomplete.");
		}
		final List<Line> offered = new ArrayList<>();
		final List<Line> requested = new ArrayList<>();
		for (int i = 7; i < tokens.length; i++)
		{
			final String[] line = tokens[i].split(":", -1);
			if (line.length != 3)
			{
				throw new IllegalArgumentException("Invalid direct-trade line.");
			}
			final Line parsed = new Line(positiveInt(line[1]), 0, positiveLong(line[2]), 0);
			if ("O".equals(line[0]))
			{
				offered.add(parsed);
			}
			else if ("R".equals(line[0]))
			{
				requested.add(parsed);
			}
			else
			{
				throw new IllegalArgumentException("Unknown direct-trade line side.");
			}
		}
		return new DirectTrade(positiveInt(tokens[0]), nonNegativeLong(tokens[1]), positiveLong(tokens[2]), boundedInt(tokens[3], 1, 1500), nonNegativeLong(tokens[4]), nonNegativeLong(tokens[5]), tokens[6], offered, requested);
	}

	private static StoreBuy storeBuy(String[] tokens)
	{
		if (tokens.length < 6)
		{
			throw new IllegalArgumentException("Private-store buy Goal is incomplete.");
		}
		return new StoreBuy(positiveInt(tokens[0]), nonNegativeLong(tokens[1]), hash(tokens[2]), flag(tokens[3]), nonNegativeLong(tokens[4]), priceLines(tokens, 5, "B"));
	}

	private static StoreSell storeSell(String[] tokens)
	{
		if (tokens.length < 5)
		{
			throw new IllegalArgumentException("Private-store sell Goal is incomplete.");
		}
		return new StoreSell(positiveInt(tokens[0]), nonNegativeLong(tokens[1]), hash(tokens[2]), nonNegativeLong(tokens[3]), priceLines(tokens, 4, "S"));
	}

	private static Manufacture manufacture(String[] tokens)
	{
		if (tokens.length != 8)
		{
			throw new IllegalArgumentException("Manufacture Goal is not exact.");
		}
		return new Manufacture(positiveInt(tokens[0]), nonNegativeLong(tokens[1]), positiveInt(tokens[2]), nonNegativeLong(tokens[3]), positiveInt(tokens[4]), positiveLong(tokens[5]), boundedInt(tokens[6], 1, 32), nonNegativeLong(tokens[7]));
	}

	private static List<Line> priceLines(String[] tokens, int start, String side)
	{
		final List<Line> result = new ArrayList<>();
		for (int i = start; i < tokens.length; i++)
		{
			final String[] line = tokens[i].split(":", -1);
			if ((line.length != 5) || !side.equals(line[0]))
			{
				throw new IllegalArgumentException("Invalid private-store line.");
			}
			result.add(new Line(positiveInt(line[1]), positiveInt(line[4]), positiveLong(line[2]), nonNegativeLong(line[3])));
		}
		return List.copyOf(result);
	}

	private static int positiveInt(String value)
	{
		return boundedInt(value, 1, Integer.MAX_VALUE);
	}

	private static int boundedInt(String value, int minimum, int maximum)
	{
		try
		{
			final int result = Integer.parseInt(value);
			if ((result < minimum) || (result > maximum))
			{
				throw new IllegalArgumentException("Social economy integer is outside its bound.");
			}
			return result;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid social economy integer.", exception);
		}
	}

	private static long positiveLong(String value)
	{
		final long result = nonNegativeLong(value);
		if (result == 0)
		{
			throw new IllegalArgumentException("Social economy value must be positive.");
		}
		return result;
	}

	private static long nonNegativeLong(String value)
	{
		try
		{
			final long result = Long.parseLong(value);
			if (result < 0)
			{
				throw new IllegalArgumentException("Social economy value must be non-negative.");
			}
			return result;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid social economy value.", exception);
		}
	}

	private static String hash(String value)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid social economy authority hash.");
		}
		return value;
	}

	private static boolean flag(String value)
	{
		if (!"0".equals(value) && !"1".equals(value))
		{
			throw new IllegalArgumentException("Invalid social economy flag.");
		}
		return "1".equals(value);
	}

	record Line(int objectId, int itemId, long count, long price)
	{
		public Line
		{
			if ((objectId <= 0) || (itemId < 0) || (count <= 0) || (price < 0))
			{
				throw new IllegalArgumentException("Invalid social economy line.");
			}
		}
	}

	record DirectTrade(int counterpartyCharacterObjectId, long counterpartyProfileId, long expiresEpochMillis, int maximumDistance, long offeredAdena, long requestedAdena, String purpose, List<Line> offeredLines, List<Line> requestedLines) implements PhantomSocialEconomyGoalSpec
	{
		public DirectTrade
		{
			purpose = Objects.requireNonNull(purpose);
			offeredLines = List.copyOf(offeredLines);
			requestedLines = List.copyOf(requestedLines);
			if ((counterpartyCharacterObjectId <= 0) || (counterpartyProfileId < 0) || (purpose.isEmpty()) || (purpose.length() > 64) || (offeredLines.size() > 16) || (requestedLines.size() > 16) || (offeredLines.isEmpty() && requestedLines.isEmpty() && (offeredAdena == 0) && (requestedAdena == 0)))
			{
				throw new IllegalArgumentException("Invalid direct-trade Goal.");
			}
		}

		@Override
		public PhantomEconomyOperation.Kind operationKind()
		{
			return PhantomEconomyOperation.Kind.DIRECT_TRADE;
		}
	}

	record StoreBuy(int counterpartyCharacterObjectId, long counterpartyProfileId, String listingHash, boolean packageExpected, long maximumTotalPrice, List<Line> lines) implements PhantomSocialEconomyGoalSpec
	{
		public StoreBuy
		{
			listingHash = hash(listingHash);
			lines = List.copyOf(lines);
			if (lines.isEmpty() || (lines.size() > 16))
			{
				throw new IllegalArgumentException("Invalid private-store buy Goal.");
			}
		}

		@Override
		public PhantomEconomyOperation.Kind operationKind()
		{
			return PhantomEconomyOperation.Kind.PRIVATE_STORE_BUY;
		}
	}

	record StoreSell(int counterpartyCharacterObjectId, long counterpartyProfileId, String listingHash, long minimumTotalProceeds, List<Line> lines) implements PhantomSocialEconomyGoalSpec
	{
		public StoreSell
		{
			listingHash = hash(listingHash);
			lines = List.copyOf(lines);
			if (lines.isEmpty() || (lines.size() > 16))
			{
				throw new IllegalArgumentException("Invalid private-store sell Goal.");
			}
		}

		@Override
		public PhantomEconomyOperation.Kind operationKind()
		{
			return PhantomEconomyOperation.Kind.PRIVATE_STORE_SELL;
		}
	}

	record Manufacture(int counterpartyCharacterObjectId, long counterpartyProfileId, int recipeListId, long listingPrice, int productItemId, long productCount, int maximumAttempts, long maximumTotalFee) implements PhantomSocialEconomyGoalSpec
	{
		@Override
		public PhantomEconomyOperation.Kind operationKind()
		{
			return PhantomEconomyOperation.Kind.PLAYER_MANUFACTURE;
		}
	}
}
