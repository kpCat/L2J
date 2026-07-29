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
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-time composition bridge that lets immutable scheduler construction
 * precede the decision engine without admitting work before installation.
 */
public final class PhantomActivityWorkSinkBridge implements PhantomActivityWorkSink
{
	private final PhantomActivityWorkSink _empty = PhantomActivityWorkSink.noop();
	private final AtomicReference<PhantomActivityWorkSink> _delegate = new AtomicReference<>(_empty);

	public void install(PhantomActivityWorkSink delegate)
	{
		if (!_delegate.compareAndSet(_empty, Objects.requireNonNull(delegate, "delegate")))
		{
			throw new IllegalStateException("Activity work sink delegate changed concurrently.");
		}
	}

	@Override
	public void accept(PhantomActivityWorkItem item)
	{
		_delegate.get().accept(item);
	}
}
