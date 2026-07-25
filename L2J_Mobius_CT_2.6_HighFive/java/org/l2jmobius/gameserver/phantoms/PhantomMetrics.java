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
package org.l2jmobius.gameserver.phantoms;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed aggregate counters for the inert Phantom World skeleton.
 */
public final class PhantomMetrics
{
	private final AtomicLong _lifecycleStarts = new AtomicLong();
	private final AtomicLong _lifecycleStops = new AtomicLong();
	private final AtomicLong _queueAccepted = new AtomicLong();
	private final AtomicLong _queueRejected = new AtomicLong();
	private final AtomicLong _traceRecorded = new AtomicLong();
	private final AtomicLong _traceDropped = new AtomicLong();

	void recordLifecycleStart()
	{
		_lifecycleStarts.incrementAndGet();
	}

	void recordLifecycleStop()
	{
		_lifecycleStops.incrementAndGet();
	}

	void recordQueueAccepted()
	{
		_queueAccepted.incrementAndGet();
	}

	void recordQueueRejected()
	{
		_queueRejected.incrementAndGet();
	}

	void recordTraceRecorded()
	{
		_traceRecorded.incrementAndGet();
	}

	void recordTraceDropped()
	{
		_traceDropped.incrementAndGet();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_lifecycleStarts.get(), _lifecycleStops.get(), _queueAccepted.get(), _queueRejected.get(), _traceRecorded.get(), _traceDropped.get());
	}

	public record Snapshot(long lifecycleStarts, long lifecycleStops, long queueAccepted, long queueRejected, long traceRecorded, long traceDropped)
	{
		public boolean isZero()
		{
			return (lifecycleStarts == 0) && (lifecycleStops == 0) && (queueAccepted == 0) && (queueRejected == 0) && (traceRecorded == 0) && (traceDropped == 0);
		}
	}
}
