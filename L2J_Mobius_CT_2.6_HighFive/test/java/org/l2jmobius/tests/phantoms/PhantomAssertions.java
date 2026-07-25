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

import java.util.Objects;

public final class PhantomAssertions
{
	private PhantomAssertions()
	{
	}

	public static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	public static void assertFalse(boolean condition, String message)
	{
		assertTrue(!condition, message);
	}

	public static void assertEquals(Object expected, Object actual, String message)
	{
		if (!Objects.equals(expected, actual))
		{
			throw new AssertionError(message + " Expected <" + expected + "> but was <" + actual + ">.");
		}
	}

	public static <T extends Throwable> T assertThrows(Class<T> expectedType, ThrowingRunnable action, String message)
	{
		try
		{
			action.run();
		}
		catch (Throwable throwable)
		{
			if (expectedType.isInstance(throwable))
			{
				return expectedType.cast(throwable);
			}

			throw new AssertionError(message + " Expected " + expectedType.getName() + " but caught " + throwable.getClass().getName() + ".", throwable);
		}

		throw new AssertionError(message + " Expected " + expectedType.getName() + " but no exception was thrown.");
	}

	@FunctionalInterface
	public interface ThrowingRunnable
	{
		void run() throws Exception;
	}
}
