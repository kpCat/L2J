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
	private final AtomicLong _materializationRequested = new AtomicLong();
	private final AtomicLong _materializationSucceeded = new AtomicLong();
	private final AtomicLong _materializationRejected = new AtomicLong();
	private final AtomicLong _materializationFailuresRetained = new AtomicLong();
	private final AtomicLong _dematerializationSucceeded = new AtomicLong();
	private final AtomicLong _cleanupFailuresRetained = new AtomicLong();
	private final AtomicLong _retainedRecoverySucceeded = new AtomicLong();
	private final AtomicLong _retainedRecoveryRejected = new AtomicLong();
	private final AtomicLong _shutdownFailures = new AtomicLong();
	private final AtomicLong _activeCurrent = new AtomicLong();
	private final AtomicLong _activePeak = new AtomicLong();

	public void recordLifecycleStart()
	{
		_lifecycleStarts.incrementAndGet();
	}

	public void recordLifecycleStop()
	{
		_lifecycleStops.incrementAndGet();
	}

	public void recordQueueAccepted()
	{
		_queueAccepted.incrementAndGet();
	}

	public void recordQueueRejected()
	{
		_queueRejected.incrementAndGet();
	}

	public void recordTraceRecorded()
	{
		_traceRecorded.incrementAndGet();
	}

	public void recordTraceDropped()
	{
		_traceDropped.incrementAndGet();
	}

	public void recordMaterializationRequested()
	{
		_materializationRequested.incrementAndGet();
	}

	public void recordMaterializationSucceeded()
	{
		_materializationSucceeded.incrementAndGet();
		final long current = _activeCurrent.incrementAndGet();
		_activePeak.accumulateAndGet(current, Math::max);
	}

	public void recordMaterializationRejected()
	{
		_materializationRejected.incrementAndGet();
	}

	public void recordMaterializationFailureRetained()
	{
		_materializationFailuresRetained.incrementAndGet();
	}

	public void recordDematerializationSucceeded()
	{
		_dematerializationSucceeded.incrementAndGet();
		_activeCurrent.updateAndGet(current -> Math.max(0, current - 1));
	}

	public void recordCleanupFailureRetained()
	{
		_cleanupFailuresRetained.incrementAndGet();
	}

	public void recordRetainedRecoverySucceeded()
	{
		_retainedRecoverySucceeded.incrementAndGet();
	}

	public void recordRetainedRecoveryRejected()
	{
		_retainedRecoveryRejected.incrementAndGet();
	}

	public void recordShutdownFailure()
	{
		_shutdownFailures.incrementAndGet();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(
			_lifecycleStarts.get(),
			_lifecycleStops.get(),
			_queueAccepted.get(),
			_queueRejected.get(),
			_traceRecorded.get(),
			_traceDropped.get(),
			_materializationRequested.get(),
			_materializationSucceeded.get(),
			_materializationRejected.get(),
			_materializationFailuresRetained.get(),
			_dematerializationSucceeded.get(),
			_cleanupFailuresRetained.get(),
			_retainedRecoverySucceeded.get(),
			_retainedRecoveryRejected.get(),
			_shutdownFailures.get(),
			_activeCurrent.get(),
			_activePeak.get());
	}

	public record Snapshot(long lifecycleStarts, long lifecycleStops, long queueAccepted, long queueRejected, long traceRecorded, long traceDropped, long materializationRequested, long materializationSucceeded, long materializationRejected, long materializationFailuresRetained, long dematerializationSucceeded, long cleanupFailuresRetained, long retainedRecoverySucceeded, long retainedRecoveryRejected, long shutdownFailures, long activeCurrent, long activePeak)
	{
		public boolean isZero()
		{
			return (lifecycleStarts == 0) //
				&& (lifecycleStops == 0) //
				&& (queueAccepted == 0) //
				&& (queueRejected == 0) //
				&& (traceRecorded == 0) //
				&& (traceDropped == 0) //
				&& (materializationRequested == 0) //
				&& (materializationSucceeded == 0) //
				&& (materializationRejected == 0) //
				&& (materializationFailuresRetained == 0) //
				&& (dematerializationSucceeded == 0) //
				&& (cleanupFailuresRetained == 0) //
				&& (retainedRecoverySucceeded == 0) //
				&& (retainedRecoveryRejected == 0) //
				&& (shutdownFailures == 0) //
				&& (activeCurrent == 0) //
				&& (activePeak == 0);
		}
	}
}
