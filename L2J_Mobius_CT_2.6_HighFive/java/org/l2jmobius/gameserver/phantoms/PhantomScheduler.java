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

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Bounded placeholder queue with no consumer.
 */
public final class PhantomScheduler
{
	private final ArrayBlockingQueue<Runnable> _queue;
	private final PhantomMetrics _metrics;
	private boolean _running;
	private boolean _stopped;

	public PhantomScheduler(int capacity, PhantomMetrics metrics)
	{
		if (capacity <= 0)
		{
			throw new IllegalArgumentException("Queue capacity must be positive.");
		}
		_queue = new ArrayBlockingQueue<>(capacity);
		_metrics = Objects.requireNonNull(metrics);
	}

	public synchronized boolean start()
	{
		if (_running || _stopped)
		{
			return false;
		}
		_running = true;
		return true;
	}

	public synchronized boolean offer(Runnable work)
	{
		Objects.requireNonNull(work);
		if (!_running)
		{
			_metrics.recordQueueRejected();
			return false;
		}

		final boolean accepted = _queue.offer(work);
		if (accepted)
		{
			_metrics.recordQueueAccepted();
		}
		else
		{
			_metrics.recordQueueRejected();
		}
		return accepted;
	}

	public synchronized boolean stop()
	{
		if (_stopped)
		{
			return false;
		}
		_running = false;
		_stopped = true;
		_queue.clear();
		return true;
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_running, _queue.size(), _queue.remainingCapacity() + _queue.size(), 0);
	}

	public record Snapshot(boolean running, int queued, int capacity, int scheduledTaskCount)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(false, 0, 0, 0);
		}
	}
}
