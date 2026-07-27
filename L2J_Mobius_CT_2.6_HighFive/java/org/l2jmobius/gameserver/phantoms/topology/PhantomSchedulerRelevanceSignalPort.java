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
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;

/**
 * Narrow adapter; it deliberately has no scheduler registration operation.
 */
public final class PhantomSchedulerRelevanceSignalPort implements PhantomRelevanceSignalPort
{
	private final PhantomScheduler _scheduler;

	public PhantomSchedulerRelevanceSignalPort(PhantomScheduler scheduler)
	{
		_scheduler = Objects.requireNonNull(scheduler, "scheduler");
	}

	@Override
	public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
	{
		return map(_scheduler.submitSignal(profileId, signal).status());
	}

	@Override
	public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
	{
		return map(_scheduler.withdrawSignal(profileId, sourceKey, sequence).status());
	}

	private static SignalDelivery map(PhantomScheduler.SignalStatus status)
	{
		return switch (status)
		{
			case ACCEPTED -> SignalDelivery.ACCEPTED;
			case COALESCED -> SignalDelivery.COALESCED;
			case STALE -> SignalDelivery.STALE;
			case REJECTED -> SignalDelivery.REJECTED;
			case BACKPRESSURE -> SignalDelivery.BACKPRESSURE;
			case NOT_REGISTERED -> SignalDelivery.NOT_REGISTERED;
			case NOT_RUNNING -> SignalDelivery.NOT_RUNNING;
		};
	}
}
