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
import java.util.Map;
import java.util.TreeMap;

public record PhantomPlanStep(int index, String actionKey, PhantomDomainRef target, Map<String, Long> numericArguments, long timeoutMillis, int maximumAttempts, String reasonKey)
{
	public static final int MAX_ARGUMENTS = 16;

	public PhantomPlanStep
	{
		if (index < 0)
		{
			throw new IllegalArgumentException("Plan step index must not be negative.");
		}
		actionKey = PhantomDecisionKey.require(actionKey, "Action key");
		if (numericArguments == null)
		{
			throw new NullPointerException("Numeric arguments must not be null.");
		}
		if (numericArguments.size() > MAX_ARGUMENTS)
		{
			throw new IllegalArgumentException("Plan step numeric arguments must not exceed 16.");
		}
		final Map<String, Long> sortedArguments = new TreeMap<>();
		for (Map.Entry<String, Long> entry : numericArguments.entrySet())
		{
			sortedArguments.put(PhantomDecisionKey.require(entry.getKey(), "Numeric argument key"), java.util.Objects.requireNonNull(entry.getValue(), "Numeric argument value must not be null."));
		}
		numericArguments = Collections.unmodifiableMap(sortedArguments);
		if ((timeoutMillis < 1) || (timeoutMillis > 3_600_000))
		{
			throw new IllegalArgumentException("Plan step timeout must be between 1 and 3600000 milliseconds.");
		}
		if ((maximumAttempts < 1) || (maximumAttempts > 10))
		{
			throw new IllegalArgumentException("Plan step attempts must be between 1 and 10.");
		}
		reasonKey = PhantomDecisionKey.require(reasonKey, "Plan step reason key");
	}
}
