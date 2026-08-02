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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;

/** Strict parser and projection for the exact {@code enchant.item} Goal. */
public record PhantomEnchantGoalSpec(int targetObjectId, int desiredLevel, int maximumAttempts, int attemptsUsed, long expenseUsed, boolean destructionPermitted, long replacementReserve, Set<Integer> allowedScrollItemIds, Set<Integer> allowedSupportItemIds)
{
	public static final String GOAL_TYPE = "enchant.item";
	public static final String PURPOSE_KEY = "enchant.item";
	public static final String TARGET_NAMESPACE = "item.object";
	public static final String SCROLL_NAMESPACE = "enchant.scroll";
	public static final String SUPPORT_NAMESPACE = "enchant.support";
	public static final String TARGET_CONSTRAINT = "enchant.target_object_id";
	public static final String DESIRED_CONSTRAINT = "enchant.desired_level";
	public static final String MAXIMUM_ATTEMPTS_CONSTRAINT = "enchant.maximum_attempts";
	public static final String ATTEMPTS_USED_CONSTRAINT = "enchant.attempts_used";
	public static final String EXPENSE_USED_CONSTRAINT = "enchant.expense_used";
	public static final String DESTRUCTION_CONSTRAINT = "enchant.allow_destruction";
	public static final String REPLACEMENT_RESERVE_CONSTRAINT = "enchant.replacement_reserve";
	private static final Set<String> EXACT_CONSTRAINTS = Set.of(TARGET_CONSTRAINT, DESIRED_CONSTRAINT, MAXIMUM_ATTEMPTS_CONSTRAINT, ATTEMPTS_USED_CONSTRAINT, EXPENSE_USED_CONSTRAINT, DESTRUCTION_CONSTRAINT, REPLACEMENT_RESERVE_CONSTRAINT);

	public PhantomEnchantGoalSpec
	{
		allowedScrollItemIds = Set.copyOf(allowedScrollItemIds);
		allowedSupportItemIds = Set.copyOf(allowedSupportItemIds);
		if ((targetObjectId <= 0) || (desiredLevel <= 0) || (maximumAttempts < 1) || (maximumAttempts > 16) || (attemptsUsed < 0) || (attemptsUsed > maximumAttempts) || (expenseUsed < 0) || (replacementReserve < 0) || allowedScrollItemIds.isEmpty() || (allowedScrollItemIds.size() > 16) || (allowedSupportItemIds.size() > 8))
		{
			throw new IllegalArgumentException("Invalid enchant Goal specification.");
		}
	}

	public static PhantomEnchantGoalSpec parse(PhantomGoal goal)
	{
		if ((goal == null) || !GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.target() == null) || !TARGET_NAMESPACE.equals(goal.target().namespace()) || !PURPOSE_KEY.equals(goal.purposeKey()) || !goal.constraints().keySet().equals(EXACT_CONSTRAINTS))
		{
			throw new IllegalArgumentException("Goal is not an exact active enchant.item Goal.");
		}
		final int targetObjectId = positiveInt(goal.target().key(), "target object");
		if (targetObjectId != Math.toIntExact(goal.constraints().get(TARGET_CONSTRAINT)))
		{
			throw new IllegalArgumentException("Enchant Goal target identity is inconsistent.");
		}
		final Set<Integer> scrolls = new HashSet<>();
		final Set<Integer> supports = new HashSet<>();
		for (PhantomDomainRef source : goal.validSources())
		{
			final int itemId = positiveInt(source.key(), "allowed item");
			if (SCROLL_NAMESPACE.equals(source.namespace()))
			{
				if (!scrolls.add(itemId))
				{
					throw new IllegalArgumentException("Duplicate enchant scroll source.");
				}
			}
			else if (SUPPORT_NAMESPACE.equals(source.namespace()))
			{
				if (!supports.add(itemId))
				{
					throw new IllegalArgumentException("Duplicate enchant support source.");
				}
			}
			else
			{
				throw new IllegalArgumentException("Unknown enchant source namespace.");
			}
		}
		return new PhantomEnchantGoalSpec(targetObjectId, Math.toIntExact(goal.constraints().get(DESIRED_CONSTRAINT)), Math.toIntExact(goal.constraints().get(MAXIMUM_ATTEMPTS_CONSTRAINT)), Math.toIntExact(goal.constraints().get(ATTEMPTS_USED_CONSTRAINT)), goal.constraints().get(EXPENSE_USED_CONSTRAINT), goal.constraints().get(DESTRUCTION_CONSTRAINT) == 1, goal.constraints().get(REPLACEMENT_RESERVE_CONSTRAINT), scrolls, supports);
	}

	public PhantomGoal project(PhantomGoal goal, int enchantLevel, boolean targetSurvived, long attemptExpense, String reason)
	{
		final int nextAttempts = Math.addExact(attemptsUsed, 1);
		final long nextExpense = Math.addExact(expenseUsed, attemptExpense);
		final boolean completed = targetSurvived && (enchantLevel >= desiredLevel);
		final PhantomGoalStatus status = completed ? PhantomGoalStatus.COMPLETED : targetSurvived && (nextAttempts < maximumAttempts) ? PhantomGoalStatus.ACTIVE : PhantomGoalStatus.FAILED;
		final Map<String, Long> constraints = new java.util.TreeMap<>(goal.constraints());
		constraints.put(ATTEMPTS_USED_CONSTRAINT, (long) nextAttempts);
		constraints.put(EXPENSE_USED_CONSTRAINT, nextExpense);
		return new PhantomGoal(goal.goalId(), goal.goalType(), status, goal.subject(), goal.target(), goal.requiredAmount(), completed ? goal.requiredAmount() : Math.min(goal.currentAmount(), goal.requiredAmount()), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), constraints, reason, Math.addExact(goal.revision(), 1));
	}

	public boolean accepts(int currentEnchant, int scrollItemId, int supportItemId, long expense)
	{
		return (currentEnchant < desiredLevel) && (attemptsUsed < maximumAttempts) && allowedScrollItemIds.contains(scrollItemId) && ((supportItemId == 0) || allowedSupportItemIds.contains(supportItemId)) && (expenseUsed <= Long.MAX_VALUE - expense) && ((goalBudgetRemaining(expenseUsed, expense) >= 0));
	}

	private static long goalBudgetRemaining(long used, long expense)
	{
		return Long.MAX_VALUE - used - expense;
	}

	private static int positiveInt(String value, String name)
	{
		try
		{
			final int result = Integer.parseInt(value);
			if (result <= 0)
			{
				throw new IllegalArgumentException("Invalid enchant " + name + ".");
			}
			return result;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid enchant " + name + ".", exception);
		}
	}
}
