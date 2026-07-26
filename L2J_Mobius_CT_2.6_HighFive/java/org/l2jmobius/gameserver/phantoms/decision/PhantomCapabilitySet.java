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

public record PhantomCapabilitySet(Map<String, Integer> ranks)
{
	public static final int MAX_CAPABILITIES = 128;
	private static final PhantomCapabilitySet EMPTY = new PhantomCapabilitySet(Map.of());

	public PhantomCapabilitySet
	{
		if (ranks == null)
		{
			throw new NullPointerException("Capability ranks must not be null.");
		}
		if (ranks.size() > MAX_CAPABILITIES)
		{
			throw new IllegalArgumentException("Capability set must not exceed 128 entries.");
		}
		final Map<String, Integer> sorted = new TreeMap<>();
		for (Map.Entry<String, Integer> entry : ranks.entrySet())
		{
			final String key = PhantomDecisionKey.require(entry.getKey(), "Capability key");
			final Integer rank = entry.getValue();
			if ((rank == null) || (rank < 1) || (rank > 1000))
			{
				throw new IllegalArgumentException("Capability rank must be between 1 and 1000.");
			}
			sorted.put(key, rank);
		}
		ranks = Collections.unmodifiableMap(sorted);
	}

	public static PhantomCapabilitySet empty()
	{
		return EMPTY;
	}

	public boolean satisfies(PhantomCapabilityRequirement requirement)
	{
		return ranks.getOrDefault(requirement.key(), 0) >= requirement.minimumRank();
	}
}
