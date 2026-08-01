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
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;

/** Exact parser and projection helpers for the single {@code acquire.item} Goal. */
public record PhantomAcquisitionGoalSpec(int itemId, long requiredAmount, long baselineCount, int maximumSwitches, Method preferredMethod, Set<Method> allowedMethods)
{
	public static final String GOAL_TYPE = "acquire.item";
	public static final String PURPOSE_KEY = "acquisition.item";
	public static final String SOURCE_NAMESPACE = "acquisition.method";
	public static final String ANCHOR_NAMESPACE = "topology.anchor";
	public static final String BASELINE_CONSTRAINT = "acquisition.baseline_count";
	public static final String MAXIMUM_SWITCHES_CONSTRAINT = "acquisition.maximum_switches";
	public static final String PREFERRED_METHOD_CONSTRAINT = "acquisition.preferred_method_code";
	private static final Set<String> REQUIRED_CONSTRAINTS = Set.of(BASELINE_CONSTRAINT, MAXIMUM_SWITCHES_CONSTRAINT);

	public PhantomAcquisitionGoalSpec
	{
		allowedMethods = Set.copyOf(allowedMethods);
		if ((itemId <= 0) || (requiredAmount <= 0) || (baselineCount < 0) || (maximumSwitches < 0) || (maximumSwitches > PhantomAcquisitionState.MAX_SWITCHES) || allowedMethods.isEmpty())
		{
			throw new IllegalArgumentException("Invalid acquisition Goal specification.");
		}
	}

	public static PhantomAcquisitionGoalSpec parse(PhantomGoal goal)
	{
		if ((goal == null) || !GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE) || (goal.target() == null) || !"item".equals(goal.target().namespace()) || !PURPOSE_KEY.equals(goal.purposeKey()) || (goal.requiredAmount() <= 0) || (goal.currentAmount() < 0) || (goal.currentAmount() > goal.requiredAmount()))
		{
			throw new IllegalArgumentException("Goal is not an active acquire.item Goal.");
		}
		final int itemId;
		try
		{
			itemId = Integer.parseInt(goal.target().key());
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Acquisition Goal target is not an item ID.", exception);
		}
		final Set<String> actualConstraints = goal.constraints().keySet();
		if (!actualConstraints.containsAll(REQUIRED_CONSTRAINTS) || (actualConstraints.size() < 2) || (actualConstraints.size() > 3) || actualConstraints.stream().anyMatch(key -> !REQUIRED_CONSTRAINTS.contains(key) && !PREFERRED_METHOD_CONSTRAINT.equals(key)))
		{
			throw new IllegalArgumentException("Acquisition Goal constraints are not exact.");
		}
		final EnumSet<Method> allowed = EnumSet.noneOf(Method.class);
		if (goal.validSources().isEmpty())
		{
			allowed.addAll(Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP, Method.RECIPE_PREPARATION, Method.MANOR_CROP, Method.QUEST_COLLECTION));
		}
		else
		{
			for (PhantomDomainRef source : goal.validSources())
			{
				if (!SOURCE_NAMESPACE.equals(source.namespace()) || !allowed.add(Method.fromKey(source.key())))
				{
					throw new IllegalArgumentException("Acquisition Goal method allowlist is invalid.");
				}
			}
		}
		final Long preferredCode = goal.constraints().get(PREFERRED_METHOD_CONSTRAINT);
		final Method preferred = preferredCode == null ? null : Method.fromCode(Math.toIntExact(preferredCode));
		if ((preferred != null) && !allowed.contains(preferred))
		{
			throw new IllegalArgumentException("Preferred acquisition method is not allowed.");
		}
		if ((goal.acquisitionMethod() != null) && !allowed.contains(Method.fromKey(goal.acquisitionMethod())))
		{
			throw new IllegalArgumentException("Selected acquisition method is not allowed.");
		}
		if ((goal.selectedAnchor() != null) && !ANCHOR_NAMESPACE.equals(goal.selectedAnchor().namespace()))
		{
			throw new IllegalArgumentException("Acquisition Goal selected anchor is invalid.");
		}
		return new PhantomAcquisitionGoalSpec(itemId, goal.requiredAmount(), goal.constraints().get(BASELINE_CONSTRAINT), Math.toIntExact(goal.constraints().get(MAXIMUM_SWITCHES_CONSTRAINT)), preferred, allowed);
	}

	public static PhantomGoal project(PhantomGoal goal, long progress, PhantomGoalStatus status, Source source)
	{
		final PhantomAcquisitionGoalSpec spec = parseActiveOrTerminal(goal);
		if ((progress < 0) || (progress > spec.requiredAmount()) || ((status == PhantomGoalStatus.COMPLETED) != (progress == spec.requiredAmount())))
		{
			throw new IllegalArgumentException("Invalid acquisition Goal progress projection.");
		}
		final PhantomDomainRef anchor = (source == null) || (source.method() == Method.RECIPE_PREPARATION) ? goal.selectedAnchor() : new PhantomDomainRef(ANCHOR_NAMESPACE, source.anchorId());
		return new PhantomGoal(goal.goalId(), goal.goalType(), status, goal.subject(), goal.target(), goal.requiredAmount(), progress, source == null ? goal.acquisitionMethod() : source.method().key(), goal.validSources(), anchor, goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), goal.reasonKey(), goal.revision());
	}

	public boolean initialCountMatches(long authoritativeCount, PhantomGoal goal)
	{
		return (authoritativeCount == baselineCount) && (goal.currentAmount() == 0);
	}

	private static PhantomAcquisitionGoalSpec parseActiveOrTerminal(PhantomGoal goal)
	{
		if ((goal == null) || ((goal.status() != PhantomGoalStatus.ACTIVE) && (goal.status() != PhantomGoalStatus.COMPLETED)))
		{
			throw new IllegalArgumentException("Acquisition Goal is neither active nor completed.");
		}
		if (goal.status() == PhantomGoalStatus.ACTIVE)
		{
			return parse(goal);
		}
		final PhantomGoal active = new PhantomGoal(goal.goalId(), goal.goalType(), PhantomGoalStatus.ACTIVE, goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), Map.copyOf(goal.constraints()), goal.reasonKey(), goal.revision());
		return parse(active);
	}
}
