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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record PhantomGoal(long goalId, String goalType, PhantomGoalStatus status, PhantomDomainRef subject, PhantomDomainRef target, long requiredAmount, long currentAmount, String acquisitionMethod, List<PhantomDomainRef> validSources, PhantomDomainRef selectedAnchor, String purposeKey, int priority, long riskBudget, long expenseBudget, long deadlineEpochMillis, Map<String, Long> constraints, String reasonKey, long revision)
{
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_VALID_SOURCES = 16;
	public static final int MAX_CONSTRAINTS = 16;

	public PhantomGoal
	{
		if (goalId <= 0)
		{
			throw new IllegalArgumentException("Goal ID must be positive.");
		}
		goalType = PhantomDecisionKey.require(goalType, "Goal type");
		Objects.requireNonNull(status, "Goal status must not be null.");
		if ((requiredAmount < 0) || (currentAmount < 0) || (currentAmount > requiredAmount))
		{
			throw new IllegalArgumentException("Goal amounts must satisfy 0 <= current <= required.");
		}
		acquisitionMethod = acquisitionMethod == null ? null : PhantomDecisionKey.require(acquisitionMethod, "Acquisition method");
		if (validSources == null)
		{
			throw new NullPointerException("Valid sources must not be null.");
		}
		if (validSources.size() > MAX_VALID_SOURCES)
		{
			throw new IllegalArgumentException("Goal valid sources must not exceed 16 entries.");
		}
		validSources = validSources.stream().map(source -> Objects.requireNonNull(source, "Valid source must not be null.")).sorted().distinct().toList();
		purposeKey = PhantomDecisionKey.require(purposeKey, "Purpose key");
		if ((priority < 0) || (priority > 1000))
		{
			throw new IllegalArgumentException("Goal priority must be between 0 and 1000.");
		}
		if ((riskBudget < 0) || (expenseBudget < 0))
		{
			throw new IllegalArgumentException("Goal budgets must not be negative.");
		}
		if (deadlineEpochMillis < 0)
		{
			throw new IllegalArgumentException("Goal deadline must be zero or positive.");
		}
		if (constraints == null)
		{
			throw new NullPointerException("Goal constraints must not be null.");
		}
		if (constraints.size() > MAX_CONSTRAINTS)
		{
			throw new IllegalArgumentException("Goal constraints must not exceed 16 entries.");
		}
		final Map<String, Long> sortedConstraints = new TreeMap<>();
		for (Map.Entry<String, Long> entry : constraints.entrySet())
		{
			final String key = PhantomDecisionKey.require(entry.getKey(), "Constraint key");
			final Long value = Objects.requireNonNull(entry.getValue(), "Constraint value must not be null.");
			sortedConstraints.put(key, value);
		}
		constraints = Collections.unmodifiableMap(sortedConstraints);
		reasonKey = PhantomDecisionKey.require(reasonKey, "Goal reason key");
		if (revision < 0)
		{
			throw new IllegalArgumentException("Goal revision must not be negative.");
		}
	}

	public PhantomGoal withStatus(PhantomGoalStatus replacementStatus)
	{
		return new PhantomGoal(goalId, goalType, replacementStatus, subject, target, requiredAmount, currentAmount, acquisitionMethod, validSources, selectedAnchor, purposeKey, priority, riskBudget, expenseBudget, deadlineEpochMillis, constraints, reasonKey, Math.addExact(revision, 1));
	}
}
