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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed aggregate counters without dynamic topology or profile labels.
 */
public final class PhantomTopologyMetrics
{
	private final AtomicLong _loads = new AtomicLong();
	private final AtomicLong _reloads = new AtomicLong();
	private final AtomicLong _reloadFailures = new AtomicLong();
	private final AtomicLong _validationFailures = new AtomicLong();
	private final AtomicLong _spatialQueries = new AtomicLong();
	private final AtomicLong _nearestQueries = new AtomicLong();
	private final AtomicLong _edgeQueries = new AtomicLong();
	private final AtomicLong _doorChecks = new AtomicLong();
	private final AtomicLong _profilesRegistered = new AtomicLong();
	private final AtomicLong _profilesCurrent = new AtomicLong();
	private final AtomicLong _profilesPeak = new AtomicLong();
	private final AtomicLong _profileUpdatesRejected = new AtomicLong();
	private final AtomicLong _eventsAccepted = new AtomicLong();
	private final AtomicLong _eventsRejected = new AtomicLong();
	private final AtomicLong _eventsInFlight = new AtomicLong();
	private final AtomicLong _eventsPeak = new AtomicLong();
	private final AtomicLong _recipientsConsidered = new AtomicLong();
	private final AtomicLong _recipientsDelivered = new AtomicLong();
	private final AtomicLong _recipientsBackpressured = new AtomicLong();
	private final AtomicLong _recipientsUnregistered = new AtomicLong();
	private final AtomicLong _localChatSignals = new AtomicLong();
	private final AtomicLong _combatSignals = new AtomicLong();
	private final AtomicLong _targetabilitySignals = new AtomicLong();
	private final AtomicLong _stopFailures = new AtomicLong();

	public void recordLoad()
	{
		_loads.incrementAndGet();
	}

	public void recordReload()
	{
		_reloads.incrementAndGet();
	}

	public void recordReloadFailure()
	{
		_reloadFailures.incrementAndGet();
	}

	public void recordValidationFailure()
	{
		_validationFailures.incrementAndGet();
	}

	public void recordSpatialQuery()
	{
		_spatialQueries.incrementAndGet();
	}

	public void recordNearestQuery()
	{
		_nearestQueries.incrementAndGet();
	}

	public void recordEdgeQuery()
	{
		_edgeQueries.incrementAndGet();
	}

	public void recordDoorCheck()
	{
		_doorChecks.incrementAndGet();
	}

	public void recordProfileRegistered()
	{
		_profilesRegistered.incrementAndGet();
		final long current = _profilesCurrent.incrementAndGet();
		_profilesPeak.accumulateAndGet(current, Math::max);
	}

	public void recordProfileUnregistered()
	{
		_profilesCurrent.updateAndGet(value -> Math.max(0, value - 1));
	}

	public void recordProfileUpdateRejected()
	{
		_profileUpdatesRejected.incrementAndGet();
	}

	public void recordEventAccepted()
	{
		_eventsAccepted.incrementAndGet();
		final long current = _eventsInFlight.incrementAndGet();
		_eventsPeak.accumulateAndGet(current, Math::max);
	}

	public void recordEventFinished()
	{
		_eventsInFlight.updateAndGet(value -> Math.max(0, value - 1));
	}

	public void recordEventRejected()
	{
		_eventsRejected.incrementAndGet();
	}

	public void recordRecipientConsidered()
	{
		_recipientsConsidered.incrementAndGet();
	}

	public void recordRecipientDelivered()
	{
		_recipientsDelivered.incrementAndGet();
	}

	public void recordRecipientBackpressured()
	{
		_recipientsBackpressured.incrementAndGet();
	}

	public void recordRecipientUnregistered()
	{
		_recipientsUnregistered.incrementAndGet();
	}

	public void recordLocalChatSignal()
	{
		_localChatSignals.incrementAndGet();
	}

	public void recordCombatSignal()
	{
		_combatSignals.incrementAndGet();
	}

	public void recordTargetabilitySignal()
	{
		_targetabilitySignals.incrementAndGet();
	}

	public void recordStopFailure()
	{
		_stopFailures.incrementAndGet();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_loads.get(), _reloads.get(), _reloadFailures.get(), _validationFailures.get(), _spatialQueries.get(), _nearestQueries.get(), _edgeQueries.get(), _doorChecks.get(), _profilesRegistered.get(), _profilesCurrent.get(), _profilesPeak.get(), _profileUpdatesRejected.get(), _eventsAccepted.get(), _eventsRejected.get(), _eventsInFlight.get(), _eventsPeak.get(), _recipientsConsidered.get(), _recipientsDelivered.get(), _recipientsBackpressured.get(), _recipientsUnregistered.get(), _localChatSignals.get(), _combatSignals.get(), _targetabilitySignals.get(), _stopFailures.get());
	}

	public record Snapshot(long loads, long reloads, long reloadFailures, long validationFailures, long spatialQueries, long nearestQueries, long edgeQueries, long doorChecks, long profilesRegistered, long profilesCurrent, long profilesPeak, long profileUpdatesRejected, long eventsAccepted, long eventsRejected, long eventsInFlight, long eventsPeak, long recipientsConsidered, long recipientsDelivered, long recipientsBackpressured, long recipientsUnregistered, long localChatSignals, long combatSignals, long targetabilitySignals, long stopFailures)
	{
	}
}
