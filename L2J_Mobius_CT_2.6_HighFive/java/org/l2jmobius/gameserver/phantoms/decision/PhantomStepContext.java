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

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;

public record PhantomStepContext(long profileId, PhantomGoal goal, PhantomPlan plan, PhantomPlanStep step, PhantomActivityState effectiveState, long activityGeneration, long tickSequence, long logicalNowNanos, int attempt, PhantomCancellationToken cancellationToken)
{
	public PhantomStepContext(long profileId, PhantomGoal goal, PhantomPlan plan, PhantomPlanStep step, PhantomActivityState activityState, long logicalNowNanos, int attempt, PhantomCancellationToken cancellationToken)
	{
		this(profileId, goal, plan, step, activityState, 0, 0, logicalNowNanos, attempt, cancellationToken);
	}

	public PhantomStepContext
	{
		if ((profileId <= 0) || (activityGeneration < 0) || (tickSequence < 0) || (logicalNowNanos < 0) || (attempt < 1))
		{
			throw new IllegalArgumentException("Step context identifiers, activity identity, time or attempt are invalid.");
		}
		Objects.requireNonNull(goal, "Goal must not be null.");
		Objects.requireNonNull(plan, "Plan must not be null.");
		Objects.requireNonNull(step, "Step must not be null.");
		Objects.requireNonNull(effectiveState, "Activity state must not be null.");
		Objects.requireNonNull(cancellationToken, "Cancellation token must not be null.");
	}

	public PhantomActivityState activityState()
	{
		return effectiveState;
	}
}
