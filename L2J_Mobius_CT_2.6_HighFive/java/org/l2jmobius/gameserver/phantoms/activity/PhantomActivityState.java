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
 * Stable activity detail order. The explicit code is the only value suitable
 * for snapshots or future wire/storage boundaries.
 */
public enum PhantomActivityState
{
	ACTIVE(10, true),
	NEARBY_PERCEPTIBLE(20, true),
	WARM(30, false),
	BACKGROUND(40, false),
	SLEEPING(50, false);

	private final int _code;
	private final boolean _materializationRequired;

	PhantomActivityState(int code, boolean materializationRequired)
	{
		_code = code;
		_materializationRequired = materializationRequired;
	}

	public int code()
	{
		return _code;
	}

	public boolean requiresMaterialization()
	{
		return _materializationRequired;
	}

	public boolean isHigherDetailThan(PhantomActivityState other)
	{
		return _code < other._code;
	}
}
