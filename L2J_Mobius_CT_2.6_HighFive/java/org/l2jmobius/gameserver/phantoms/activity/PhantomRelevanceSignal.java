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
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable abstract relevance requirement. It deliberately contains no world,
 * topology or actor reference.
 */
public record PhantomRelevanceSignal(String sourceKey, long sequence, PhantomActivityState requiredState, long ttlMillis)
{
	public static final long MAXIMUM_TTL_MILLIS = 86_400_000;
	private static final Pattern SOURCE_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");

	public PhantomRelevanceSignal
	{
		if ((sourceKey == null) || !SOURCE_KEY_PATTERN.matcher(sourceKey).matches())
		{
			throw new IllegalArgumentException("Invalid relevance signal sourceKey.");
		}
		if (sequence < 0)
		{
			throw new IllegalArgumentException("Relevance signal sequence must be non-negative.");
		}
		Objects.requireNonNull(requiredState, "requiredState");
		if ((ttlMillis < 1) || (ttlMillis > MAXIMUM_TTL_MILLIS))
		{
			throw new IllegalArgumentException("Relevance signal ttlMillis must be between 1 and 86400000.");
		}
	}

	public static boolean isValidSourceKey(String sourceKey)
	{
		return (sourceKey != null) && SOURCE_KEY_PATTERN.matcher(sourceKey).matches();
	}
}
