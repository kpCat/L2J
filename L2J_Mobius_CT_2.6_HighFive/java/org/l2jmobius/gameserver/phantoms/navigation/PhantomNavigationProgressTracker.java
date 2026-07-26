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
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;

/**
 * Pure observation state machine. It owns no actor, timer or movement command.
 */
public final class PhantomNavigationProgressTracker
{
	public enum BeginStatus
	{
		TRACKING,
		PROFILE_BUSY,
		CAPACITY_REACHED,
		INVALID
	}

	public enum ProgressStatus
	{
		TRACKING,
		PROGRESS,
		ARRIVED,
		STUCK,
		TIMEOUT,
		CANCELLED,
		STALE
	}

	private final PhantomNavigationPolicy _policy;
	private final PhantomMetrics _metrics;
	private final Map<Long, Attempt> _active = new HashMap<>();
	private final LinkedHashMap<Long, ProgressSnapshot> _terminal = new LinkedHashMap<>();

	public PhantomNavigationProgressTracker(PhantomNavigationPolicy policy, PhantomMetrics metrics)
	{
		_policy = Objects.requireNonNull(policy, "policy");
		_metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public synchronized BeginResult begin(long profileId, long requestId, PhantomNavigationRoute route, long logicalNowNanos)
	{
		if ((profileId <= 0) || (requestId <= 0) || (route == null) || (logicalNowNanos < 0))
		{
			return new BeginResult(BeginStatus.INVALID, null);
		}
		final Attempt existing = _active.get(profileId);
		if (existing != null)
		{
			return new BeginResult(BeginStatus.PROFILE_BUSY, snapshot(existing, ProgressStatus.TRACKING, existing._lastObservedNanos));
		}
		if (_active.size() >= _policy.maximumTrackedProfiles())
		{
			return new BeginResult(BeginStatus.CAPACITY_REACHED, null);
		}
		_terminal.remove(profileId);
		final double distance = route.origin().distanceTo(route.destination());
		final Attempt attempt = new Attempt(profileId, requestId, route, logicalNowNanos, distance);
		_active.put(profileId, attempt);
		return new BeginResult(BeginStatus.TRACKING, snapshot(attempt, ProgressStatus.TRACKING, logicalNowNanos));
	}

	public synchronized ProgressResult observe(long profileId, long requestId, PhantomNavigationPoint current, long logicalNowNanos)
	{
		final Attempt attempt = _active.get(profileId);
		if ((attempt == null) || (requestId != attempt._requestId) || (current == null) || (current.instanceId() != attempt._route.destination().instanceId()) || (logicalNowNanos < attempt._lastObservedNanos))
		{
			return new ProgressResult(ProgressStatus.STALE, find(profileId).orElse(null));
		}
		attempt._lastObservedNanos = logicalNowNanos;
		if ((logicalNowNanos - attempt._startedNanos) >= _policy.maximumAttemptDurationNanos())
		{
			return terminal(attempt, ProgressStatus.TIMEOUT, logicalNowNanos);
		}

		final double distance = current.distanceTo(attempt._route.destination());
		attempt._currentDistance = distance;
		if (distance <= _policy.arrivalRadius())
		{
			return terminal(attempt, ProgressStatus.ARRIVED, logicalNowNanos);
		}
		if ((attempt._bestDistance - distance) >= _policy.minimumProgress())
		{
			attempt._bestDistance = distance;
			attempt._lastProgressNanos = logicalNowNanos;
			_metrics.recordNavigationProgress();
			return new ProgressResult(ProgressStatus.PROGRESS, snapshot(attempt, ProgressStatus.PROGRESS, logicalNowNanos));
		}
		if ((logicalNowNanos - attempt._lastProgressNanos) >= _policy.stuckWindowNanos())
		{
			return terminal(attempt, ProgressStatus.STUCK, logicalNowNanos);
		}
		return new ProgressResult(ProgressStatus.TRACKING, snapshot(attempt, ProgressStatus.TRACKING, logicalNowNanos));
	}

	public synchronized CancelResult cancel(long profileId, long requestId)
	{
		final Attempt attempt = _active.get(profileId);
		if ((attempt == null) || (attempt._requestId != requestId))
		{
			return new CancelResult(ProgressStatus.STALE, find(profileId).orElse(null));
		}
		final ProgressResult result = terminal(attempt, ProgressStatus.CANCELLED, attempt._lastObservedNanos);
		return new CancelResult(result.status(), result.snapshot());
	}

	public synchronized Optional<ProgressSnapshot> find(long profileId)
	{
		final Attempt active = _active.get(profileId);
		if (active != null)
		{
			return Optional.of(snapshot(active, ProgressStatus.TRACKING, active._lastObservedNanos));
		}
		return Optional.ofNullable(_terminal.get(profileId));
	}

	public synchronized int activeAttempts()
	{
		return _active.size();
	}

	public synchronized void cancelAll()
	{
		for (Attempt attempt : List.copyOf(_active.values()))
		{
			terminal(attempt, ProgressStatus.CANCELLED, attempt._lastObservedNanos);
		}
	}

	private ProgressResult terminal(Attempt attempt, ProgressStatus status, long logicalNowNanos)
	{
		_active.remove(attempt._profileId, attempt);
		final ProgressSnapshot snapshot = snapshot(attempt, status, logicalNowNanos);
		_terminal.put(attempt._profileId, snapshot);
		while (_terminal.size() > _policy.maximumTrackedProfiles())
		{
			_terminal.remove(_terminal.keySet().iterator().next());
		}
		switch (status)
		{
			case ARRIVED -> _metrics.recordNavigationArrived();
			case STUCK -> _metrics.recordNavigationStuck();
			case TIMEOUT -> _metrics.recordNavigationAttemptTimeout();
			case CANCELLED -> _metrics.recordNavigationProgressCancelled();
			default ->
			{
			}
		}
		return new ProgressResult(status, snapshot);
	}

	private static ProgressSnapshot snapshot(Attempt attempt, ProgressStatus status, long logicalNowNanos)
	{
		return new ProgressSnapshot(attempt._profileId, attempt._requestId, status, attempt._route.mode(), attempt._route.destination(), attempt._startedNanos, attempt._lastProgressNanos, logicalNowNanos, attempt._bestDistance, attempt._currentDistance);
	}

	public record BeginResult(BeginStatus status, ProgressSnapshot snapshot)
	{
	}

	public record ProgressResult(ProgressStatus status, ProgressSnapshot snapshot)
	{
	}

	public record CancelResult(ProgressStatus status, ProgressSnapshot snapshot)
	{
	}

	public record ProgressSnapshot(long profileId, long requestId, ProgressStatus status, PhantomNavigationRoute.Mode routeMode, PhantomNavigationPoint destination, long startedLogicalNanos, long lastProgressLogicalNanos, long observedLogicalNanos, double bestDistance, double currentDistance)
	{
	}

	private static final class Attempt
	{
		private final long _profileId;
		private final long _requestId;
		private final PhantomNavigationRoute _route;
		private final long _startedNanos;
		private long _lastProgressNanos;
		private long _lastObservedNanos;
		private double _bestDistance;
		private double _currentDistance;

		private Attempt(long profileId, long requestId, PhantomNavigationRoute route, long logicalNowNanos, double distance)
		{
			_profileId = profileId;
			_requestId = requestId;
			_route = route;
			_startedNanos = logicalNowNanos;
			_lastProgressNanos = logicalNowNanos;
			_lastObservedNanos = logicalNowNanos;
			_bestDistance = distance;
			_currentDistance = distance;
		}
	}
}
