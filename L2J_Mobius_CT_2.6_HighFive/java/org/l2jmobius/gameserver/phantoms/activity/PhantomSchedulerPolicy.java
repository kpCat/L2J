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

/**
 * Immutable technical scheduler policy. Gameplay/population tuning is
 * deliberately absent.
 */
public record PhantomSchedulerPolicy(int maximumSignalSources, long maximumSignalTtlMillis, long demotionGraceMillis, long transitionRetryBaseMillis, long transitionRetryMaximumMillis, long activeCadenceMillis, long nearbyCadenceMillis, long warmCadenceMillis, long backgroundCadenceMillis, long pulseWallBudgetMillis)
{
	public PhantomSchedulerPolicy
	{
		if ((maximumSignalSources < 1) || (maximumSignalSources > 16))
		{
			throw new IllegalArgumentException("maximumSignalSources must be between 1 and 16.");
		}
		if ((maximumSignalTtlMillis < 1) || (maximumSignalTtlMillis > PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS))
		{
			throw new IllegalArgumentException("maximumSignalTtlMillis is invalid.");
		}
		if ((demotionGraceMillis < 0) || (transitionRetryBaseMillis < 1) || (transitionRetryMaximumMillis < transitionRetryBaseMillis))
		{
			throw new IllegalArgumentException("Transition timing policy is invalid.");
		}
		if ((activeCadenceMillis < 1) || (nearbyCadenceMillis < 1) || (warmCadenceMillis < 1) || (backgroundCadenceMillis < 1) || (pulseWallBudgetMillis < 1))
		{
			throw new IllegalArgumentException("Cadence and pulse budgets must be positive.");
		}
	}

	public static PhantomSchedulerPolicy productionDefaults(int pulseMillis)
	{
		final long wallBudget = Math.max(1, Math.min(50, (pulseMillis * 3L) / 4L));
		return new PhantomSchedulerPolicy(16, PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS, 2000, 1000, 30000, 100, 250, 1000, 10000, wallBudget);
	}

	public long cadenceMillis(PhantomActivityState state)
	{
		return switch (state)
		{
			case ACTIVE -> activeCadenceMillis;
			case NEARBY_PERCEPTIBLE -> nearbyCadenceMillis;
			case WARM -> warmCadenceMillis;
			case BACKGROUND -> backgroundCadenceMillis;
			case SLEEPING -> 0;
		};
	}
}
