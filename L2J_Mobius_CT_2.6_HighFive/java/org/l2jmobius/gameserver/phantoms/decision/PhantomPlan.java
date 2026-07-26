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

import java.util.List;
import java.util.Objects;

public record PhantomPlan(long planId, long goalId, String candidateKey, List<PhantomPlanStep> steps, long totalTimeoutMillis, long createdAtLogicalNanos)
{
	public static final int MAX_STEPS = 32;

	public PhantomPlan
	{
		if ((planId <= 0) || (goalId <= 0))
		{
			throw new IllegalArgumentException("Plan and goal IDs must be positive.");
		}
		candidateKey = PhantomDecisionKey.require(candidateKey, "Plan candidate key");
		Objects.requireNonNull(steps, "Plan steps must not be null.");
		if (steps.isEmpty() || (steps.size() > MAX_STEPS))
		{
			throw new IllegalArgumentException("Plan must contain 1..32 steps.");
		}
		steps = steps.stream().map(step -> Objects.requireNonNull(step, "Plan step must not be null.")).toList();
		for (int index = 0; index < steps.size(); index++)
		{
			if (steps.get(index).index() != index)
			{
				throw new IllegalArgumentException("Plan step indexes must be contiguous from zero.");
			}
		}
		if ((totalTimeoutMillis < 1) || (totalTimeoutMillis > 86_400_000))
		{
			throw new IllegalArgumentException("Plan timeout must be between 1 and 86400000 milliseconds.");
		}
		if (createdAtLogicalNanos < 0)
		{
			throw new IllegalArgumentException("Plan logical creation time must not be negative.");
		}
	}
}
