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
package org.l2jmobius.tests.phantoms;

import java.util.SplittableRandom;

public final class PhantomPerformanceSmokeSuite implements PhantomTestSuite
{
	private static final int OPERATIONS = 250000;
	private static final long TIMEOUT_NANOS = 30000000000L;

	@Override
	public String id()
	{
		return "performance-smoke";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("bounded-deterministic-workload", context ->
		{
			final long start = System.nanoTime();
			final long first = workload(context.seed());
			final long second = workload(context.seed());
			final long elapsed = System.nanoTime() - start;
			PhantomAssertions.assertEquals(first, second, "Performance workload checksum is not deterministic.");
			PhantomAssertions.assertTrue(OPERATIONS >= 200000, "Performance workload is too small.");
			PhantomAssertions.assertTrue(elapsed < TIMEOUT_NANOS, "Performance workload exceeded 30 seconds.");
			context.record("performance.operations", OPERATIONS);
			context.record("performance.checksum", Long.toUnsignedString(first, 16).toUpperCase());
			context.record("performance.elapsedMs", elapsed / 1000000L);
		});
	}

	private static long workload(long seed)
	{
		final SplittableRandom random = new SplittableRandom(seed);
		long checksum = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < OPERATIONS; i++)
		{
			checksum = Long.rotateLeft(checksum ^ random.nextLong() ^ i, 13) * 0xBF58476D1CE4E5B9L;
		}
		return checksum;
	}
}
