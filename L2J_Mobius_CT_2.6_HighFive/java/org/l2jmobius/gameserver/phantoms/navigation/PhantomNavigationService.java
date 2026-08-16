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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode;

/**
 * Inert, bounded direct-first local route planner. It never owns or moves an actor.
 */
public final class PhantomNavigationService
{
	public enum ServiceState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum SubmissionStatus
	{
		ACCEPTED,
		COMPLETED,
		REJECTED
	}

	public enum RequestState
	{
		DIRECT_CHECK,
		QUEUED,
		IN_FLIGHT,
		TERMINAL
	}

	public enum CancelStatus
	{
		CANCELLED_QUEUED,
		CANCELLATION_REQUESTED,
		STALE,
		NOT_FOUND
	}

	public enum BeginStopResult
	{
		STARTED,
		ALREADY_STOPPING,
		ALREADY_STOPPED
	}

	@FunctionalInterface
	public interface Dispatcher
	{
		boolean dispatch(Runnable worker);
	}

	private final Object _monitor = new Object();
	private final Object _dispatchGate = new Object();
	private final PhantomNavigationPolicy _policy;
	private final PhantomNavigationBackend _backend;
	private final Dispatcher _dispatcher;
	private final LongSupplier _clock;
	private final PhantomMetrics _metrics;
	private final PhantomNavigationProgressTracker _progressTracker;
	private final ArrayBlockingQueue<RequestEntry> _queue;
	private final Map<Long, RequestEntry> _activeByRequest = new LinkedHashMap<>();
	private final Map<Long, RequestEntry> _activeByProfile = new LinkedHashMap<>();
	private final LinkedHashMap<Long, PhantomNavigationResult> _completed = new LinkedHashMap<>();
	private final LinkedHashMap<CacheKey, CacheEntry> _cache = new LinkedHashMap<>(16, 0.75f, true);
	private final LinkedHashMap<Long, Long> _cooldowns = new LinkedHashMap<>(16, 0.75f, true);
	private final List<WorkerClaim> _workerClaims = new ArrayList<>(2);
	private ServiceState _state = ServiceState.NEW;
	private long _requestSequence;
	private int _workers;
	private int _peakQueue;
	private int _peakWorkers;
	private int _peakCache;

	public PhantomNavigationService(PhantomMetrics metrics)
	{
		this(
			PhantomNavigationPolicy.productionDefaults(),
			new L2jNavigationBackend(),
			worker -> ThreadPool.schedule(worker, 0) != null,
			System::nanoTime,
			metrics);
	}

	public PhantomNavigationService(PhantomNavigationPolicy policy, PhantomNavigationBackend backend, Dispatcher dispatcher, LongSupplier clock, PhantomMetrics metrics)
	{
		_policy = Objects.requireNonNull(policy, "policy");
		_backend = Objects.requireNonNull(backend, "backend");
		_dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
		_clock = Objects.requireNonNull(clock, "clock");
		_metrics = Objects.requireNonNull(metrics, "metrics");
		_progressTracker = new PhantomNavigationProgressTracker(policy, metrics);
		_queue = new ArrayBlockingQueue<>(policy.maximumQueuedRequests());
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != ServiceState.NEW)
			{
				return false;
			}
			_state = ServiceState.RUNNING;
			return true;
		}
	}

	public Submission submit(PhantomNavigationRequest request)
	{
		Objects.requireNonNull(request, "request");
		final RequestEntry entry;
		synchronized (_monitor)
		{
			if (_state != ServiceState.RUNNING)
			{
				_metrics.recordNavigationSubmissionRejected();
				return rejected(request.profileId(), Status.SERVICE_NOT_RUNNING);
			}
			if (_activeByProfile.containsKey(request.profileId()))
			{
				_metrics.recordNavigationSubmissionRejected();
				return rejected(request.profileId(), Status.PROFILE_BUSY);
			}
			if (_activeByProfile.size() >= _policy.maximumTrackedProfiles())
			{
				_metrics.recordNavigationSubmissionRejected();
				return rejected(request.profileId(), Status.QUEUE_BACKPRESSURE);
			}
			final long requestId = nextRequestIdLocked();
			if (requestId == 0)
			{
				_metrics.recordNavigationSubmissionRejected();
				return rejected(request.profileId(), Status.BACKEND_FAILURE);
			}
			entry = new RequestEntry(requestId, request);
			_activeByRequest.put(requestId, entry);
			_activeByProfile.put(request.profileId(), entry);
		}
		return processDirect(entry);
	}

	public CancelResult cancel(long profileId, long requestId)
	{
		synchronized (_monitor)
		{
			final RequestEntry entry = _activeByRequest.get(requestId);
			if (entry == null)
			{
				return new CancelResult(CancelStatus.NOT_FOUND, null);
			}
			if (entry._request.profileId() != profileId)
			{
				return new CancelResult(CancelStatus.STALE, snapshotLocked(entry));
			}
			entry._cancellation.cancel();
			if ((entry._state == RequestState.QUEUED) && _queue.remove(entry))
			{
				_metrics.recordNavigationDequeued();
				final PhantomNavigationResult result = result(entry, Status.CANCELLED, null, false, _clock.getAsLong());
				completeLocked(entry, result, true);
				_metrics.recordNavigationPathCancelled();
				return new CancelResult(CancelStatus.CANCELLED_QUEUED, snapshot(result));
			}
			return new CancelResult(CancelStatus.CANCELLATION_REQUESTED, snapshotLocked(entry));
		}
	}

	public Optional<RequestSnapshot> find(long requestId)
	{
		synchronized (_monitor)
		{
			final RequestEntry active = _activeByRequest.get(requestId);
			if (active != null)
			{
				return Optional.of(snapshotLocked(active));
			}
			final PhantomNavigationResult terminal = _completed.get(requestId);
			return terminal == null ? Optional.empty() : Optional.of(snapshot(terminal));
		}
	}

	public Optional<PhantomNavigationResult> consume(long requestId)
	{
		synchronized (_monitor)
		{
			return Optional.ofNullable(_completed.remove(requestId));
		}
	}

	public int arrivalRadius()
	{
		return _policy.arrivalRadius();
	}

	public ServiceSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new ServiceSnapshot(
				_state,
				_activeByRequest.size(),
				_queue.size(),
				_policy.maximumQueuedRequests(),
				_peakQueue,
				_workers,
				_policy.maximumConcurrentPathfinders(),
				_peakWorkers,
				_cache.size(),
				_policy.maximumCacheEntries(),
				_peakCache,
				_completed.size(),
				_cooldowns.size(),
				_progressTracker.activeAttempts());
		}
	}

	public PhantomNavigationProgressTracker progressTracker()
	{
		return _progressTracker;
	}

	public BeginStopResult beginStop()
	{
		synchronized (_dispatchGate)
		{
			synchronized (_monitor)
			{
				if (_state == ServiceState.STOPPED)
				{
					return BeginStopResult.ALREADY_STOPPED;
				}
				if (_state == ServiceState.STOPPING)
				{
					return BeginStopResult.ALREADY_STOPPING;
				}
				_state = ServiceState.STOPPING;
				RequestEntry queued;
				while ((queued = _queue.poll()) != null)
				{
					_metrics.recordNavigationDequeued();
					queued._cancellation.cancel();
					completeLocked(queued, result(queued, Status.CANCELLED, null, false, _clock.getAsLong()), true);
					_metrics.recordNavigationPathCancelled();
				}
				for (RequestEntry active : List.copyOf(_activeByRequest.values()))
				{
					active._cancellation.cancel();
				}
				for (WorkerClaim claim : List.copyOf(_workerClaims))
				{
					if (!claim._accepted)
					{
						releaseWorkerClaimLocked(claim);
					}
				}
				_progressTracker.cancelAll();
				return BeginStopResult.STARTED;
			}
		}
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == ServiceState.STOPPED)
			{
				return false;
			}
			if ((_state != ServiceState.STOPPING) && (_state != ServiceState.NEW))
			{
				_metrics.recordNavigationFinishStopFailure();
				return false;
			}
			if ((_workers != 0) || !_workerClaims.isEmpty() || !_activeByRequest.isEmpty() || !_queue.isEmpty())
			{
				_metrics.recordNavigationFinishStopFailure();
				return false;
			}
			_cache.clear();
			_cooldowns.clear();
			_completed.clear();
			_state = ServiceState.STOPPED;
			return true;
		}
	}

	private Submission processDirect(RequestEntry entry)
	{
		final long cancellationGeneration = entry._initialCancellationGeneration;
		synchronized (_monitor)
		{
			if (!isCurrentLocked(entry))
			{
				return completedOrCancelledLocked(entry);
			}
			if ((_state != ServiceState.RUNNING) || entry._cancellation.changedSince(cancellationGeneration))
			{
				return completeSubmissionLocked(entry, Status.CANCELLED, null, false, true);
			}
		}
		final long preflightLogicalNow = _clock.getAsLong();
		if (entry._cancellation.changedSince(cancellationGeneration))
		{
			return completeSubmission(entry, Status.CANCELLED, null, false);
		}
		if (deadlineExpired(entry._request, preflightLogicalNow))
		{
			return completeSubmission(entry, Status.DEADLINE_EXPIRED, null, false);
		}
		final double directDistance = entry._request.origin().distanceTo(entry._request.destination());
		if (!Double.isFinite(directDistance) || (directDistance > routeDistanceBudget(entry._request)))
		{
			_metrics.recordNavigationRouteBudgetRejected();
			return completeSubmission(entry, Status.ROUTE_BUDGET_EXCEEDED, null, false);
		}

		final CapabilitySnapshot capability;
		final boolean direct;
		try
		{
			capability = Objects.requireNonNull(_backend.capability(entry._request.origin(), entry._request.destination()), "Backend capability must not be null.");
			entry._capability = capability;
			direct = _backend.canMoveDirect(entry._request.origin(), entry._request.destination());
		}
		catch (Throwable throwable)
		{
			return completeSubmission(entry, Status.BACKEND_FAILURE, null, false);
		}
		if (entry._cancellation.changedSince(cancellationGeneration))
		{
			return completeSubmission(entry, Status.CANCELLED, null, false);
		}
		final long logicalNow = _clock.getAsLong();
		if (deadlineExpired(entry._request, logicalNow))
		{
			return completeSubmission(entry, Status.DEADLINE_EXPIRED, null, false);
		}
		if (direct)
		{
			final Status status;
			final Mode mode;
			if ((capability.mode() == PhantomNavigationCapability.GEODATA_DIRECT_ONLY) || (capability.mode() == PhantomNavigationCapability.GEODATA_PATHFINDING))
			{
				status = Status.DIRECT_VALIDATED;
				mode = Mode.DIRECT_VALIDATED;
			}
			else
			{
				status = Status.DIRECT_UNVERIFIED_NO_GEODATA;
				mode = Mode.DIRECT_UNVERIFIED_NO_GEODATA;
			}
			try
			{
				final PhantomNavigationRoute route = new PhantomNavigationRoute(mode, entry._request.origin(), entry._request.destination(), List.of(entry._request.destination()), capability, logicalNow, false, 1, routeDistanceBudget(entry._request));
				if (status == Status.DIRECT_VALIDATED)
				{
					_metrics.recordNavigationDirectValidated();
				}
				else
				{
					_metrics.recordNavigationDirectUnverified();
				}
				return completeSubmission(entry, status, route, false);
			}
			catch (IllegalArgumentException e)
			{
				_metrics.recordNavigationRouteBudgetRejected();
				return completeSubmission(entry, Status.ROUTE_BUDGET_EXCEEDED, null, false);
			}
		}

		switch (capability.mode())
		{
			case NO_GEODATA, PARTIAL_GEODATA:
				return completeSubmission(entry, Status.NO_GEODATA, null, false);
			case GEODATA_DIRECT_ONLY:
				return completeSubmission(entry, Status.PATHFINDING_DISABLED, null, false);
			case GEODATA_PATHFINDING:
				break;
			default:
				return completeSubmission(entry, Status.BACKEND_FAILURE, null, false);
		}

		if (entry._request.origin().distanceTo(entry._request.destination()) > _policy.maximumLocalStraightDistance())
		{
			_metrics.recordNavigationRouteBudgetRejected();
			return completeSubmission(entry, Status.ROUTE_BUDGET_EXCEEDED, null, false);
		}

		final PhantomNavigationRoute cached = findRevalidatedCache(entry, logicalNow);
		if (cached != null)
		{
			return completeSubmission(entry, Status.PATH_FOUND, cached, true);
		}
		if (entry._cancellation.changedSince(cancellationGeneration))
		{
			return completeSubmission(entry, Status.CANCELLED, null, false);
		}
		final long afterCache = _clock.getAsLong();
		if (deadlineExpired(entry._request, afterCache))
		{
			return completeSubmission(entry, Status.DEADLINE_EXPIRED, null, false);
		}

		WorkerClaim workerClaim = null;
		synchronized (_monitor)
		{
			if (!isCurrentLocked(entry))
			{
				return completedOrCancelledLocked(entry);
			}
			if (_state != ServiceState.RUNNING)
			{
				return completeSubmissionLocked(entry, Status.CANCELLED, null, false, true);
			}
			final Long cooldownUntil = _cooldowns.get(entry._request.profileId());
			if ((cooldownUntil != null) && (cooldownUntil > afterCache))
			{
				_metrics.recordNavigationCooldownRejected();
				return completeSubmissionLocked(entry, Status.COOLDOWN, null, false, true);
			}
			if (cooldownUntil != null)
			{
				_cooldowns.remove(entry._request.profileId());
			}
			entry._state = RequestState.QUEUED;
			if (!_queue.offer(entry))
			{
				_metrics.recordNavigationSubmissionRejected();
				final PhantomNavigationResult result = result(entry, Status.QUEUE_BACKPRESSURE, null, false, afterCache);
				completeLocked(entry, result, false);
				return new Submission(SubmissionStatus.REJECTED, entry._requestId, entry._capability.mode(), result);
			}
			_metrics.recordNavigationQueued();
			markSubmissionAcceptedLocked(entry);
			_peakQueue = Math.max(_peakQueue, _queue.size());
			if (_workers < _policy.maximumConcurrentPathfinders())
			{
				workerClaim = claimWorkerLocked();
			}
		}

		if (workerClaim != null)
		{
			final Submission dispatchResult = dispatchClaimedWorker(entry, workerClaim);
			if (dispatchResult != null)
			{
				return dispatchResult;
			}
		}
		synchronized (_monitor)
		{
			final PhantomNavigationResult completed = _completed.get(entry._requestId);
			if (completed != null)
			{
				return new Submission(SubmissionStatus.COMPLETED, entry._requestId, entry._capability.mode(), completed);
			}
			return new Submission(SubmissionStatus.ACCEPTED, entry._requestId, entry._capability.mode(), null);
		}
	}

	private PhantomNavigationRoute findRevalidatedCache(RequestEntry entry, long logicalNow)
	{
		final CacheKey key = new CacheKey(entry._request.origin(), entry._request.destination(), entry._capability);
		final CacheEntry cached;
		synchronized (_monitor)
		{
			cached = _cache.get(key);
			if (cached == null)
			{
				_metrics.recordNavigationCacheMiss();
				return null;
			}
			if ((logicalNow - cached._createdLogicalNanos) >= _policy.cacheTtlNanos())
			{
				_cache.remove(key);
				_metrics.recordNavigationCacheMiss();
				_metrics.recordNavigationCacheInvalidated();
				return null;
			}
		}

		final Status validationStatus = validateSegments(entry, cached._route.waypoints(), entry._initialCancellationGeneration, true);
		if (validationStatus != Status.PATH_FOUND)
		{
			if ((validationStatus != Status.CANCELLED) && (validationStatus != Status.DEADLINE_EXPIRED))
			{
				synchronized (_monitor)
				{
					if (_cache.get(key) == cached)
					{
						_cache.remove(key);
					}
					_metrics.recordNavigationCacheInvalidated();
					_metrics.recordNavigationCacheMiss();
				}
			}
			return null;
		}
		synchronized (_monitor)
		{
			if ((_cache.get(key) != cached) || !entry._capability.equals(cached._route.geodataCapability()))
			{
				_metrics.recordNavigationCacheMiss();
				return null;
			}
			_metrics.recordNavigationCacheHit();
			return cached._route;
		}
	}

	private Submission dispatchClaimedWorker(RequestEntry entry, WorkerClaim claim)
	{
		synchronized (_dispatchGate)
		{
			synchronized (_monitor)
			{
				if (!claim._owned)
				{
					return currentSubmissionLocked(entry);
				}
				if ((_state != ServiceState.RUNNING) || _queue.isEmpty())
				{
					releaseWorkerClaimLocked(claim);
					return currentSubmissionLocked(entry);
				}
			}

			boolean dispatched = false;
			try
			{
				dispatched = _dispatcher.dispatch(() -> drainQueue(claim));
			}
			catch (Throwable throwable)
			{
				dispatched = false;
			}

			synchronized (_monitor)
			{
				if (dispatched)
				{
					if (claim._owned)
					{
						claim._accepted = true;
					}
					return null;
				}
				releaseWorkerClaimLocked(claim);
				if (isCurrentLocked(entry) && (entry._state == RequestState.QUEUED) && !hasAcceptedWorkerLocked() && _queue.remove(entry))
				{
					_metrics.recordNavigationDequeued();
					return completeSubmissionLocked(entry, Status.BACKEND_FAILURE, null, false, true);
				}
				return currentSubmissionLocked(entry);
			}
		}
	}

	private void drainQueue(WorkerClaim claim)
	{
		try
		{
			synchronized (_monitor)
			{
				if (!claim._owned)
				{
					return;
				}
				claim._accepted = true;
			}
			while (true)
			{
				final RequestEntry entry;
				final long cancellationGeneration;
				synchronized (_monitor)
				{
					entry = _queue.poll();
					if (entry == null)
					{
						releaseWorkerClaimLocked(claim);
						return;
					}
					_metrics.recordNavigationDequeued();
					if (!isCurrentLocked(entry) || (entry._state != RequestState.QUEUED))
					{
						continue;
					}
					final long logicalNow = _clock.getAsLong();
					if ((_state != ServiceState.RUNNING) || entry._cancellation.changedSince(0))
					{
						completeLocked(entry, result(entry, Status.CANCELLED, null, false, logicalNow), true);
						_metrics.recordNavigationPathCancelled();
						continue;
					}
					if (deadlineExpired(entry._request, logicalNow))
					{
						completeLocked(entry, result(entry, Status.DEADLINE_EXPIRED, null, false, logicalNow), true);
						_metrics.recordNavigationQueueWaitExpired();
						continue;
					}
					entry._state = RequestState.IN_FLIGHT;
					cancellationGeneration = entry._cancellation.generation();
					_metrics.recordNavigationPathAttempt();
				}

				List<PhantomNavigationPoint> backendPath = null;
				Throwable failure = null;
				try
				{
					backendPath = _backend.findPath(entry._request, entry._cancellation);
				}
				catch (Throwable throwable)
				{
					failure = throwable;
				}
				ValidatedPath validated = null;
				if (failure == null)
				{
					try
					{
						validated = validateBackendPath(entry, backendPath, cancellationGeneration);
					}
					catch (Throwable throwable)
					{
						failure = throwable;
					}
				}
				final long completedLogicalNanos = _clock.getAsLong();
				synchronized (_monitor)
				{
					if (!isCurrentLocked(entry))
					{
						continue;
					}
					if (entry._cancellation.changedSince(cancellationGeneration))
					{
						completeLocked(entry, result(entry, Status.CANCELLED, null, false, completedLogicalNanos), true);
						_metrics.recordNavigationPathCancelled();
						continue;
					}
					if (deadlineExpired(entry._request, completedLogicalNanos))
					{
						setCooldownLocked(entry._request.profileId(), completedLogicalNanos);
						completeLocked(entry, result(entry, Status.DEADLINE_EXPIRED, null, false, completedLogicalNanos), true);
						_metrics.recordNavigationPathTimedOut();
						continue;
					}
					if (failure != null)
					{
						setCooldownLocked(entry._request.profileId(), completedLogicalNanos);
						completeLocked(entry, result(entry, Status.BACKEND_FAILURE, null, false, completedLogicalNanos), true);
						_metrics.recordNavigationPathFailed();
						continue;
					}
					if (validated._status != Status.PATH_FOUND)
					{
						setCooldownLocked(entry._request.profileId(), completedLogicalNanos);
						completeLocked(entry, result(entry, validated._status, null, false, completedLogicalNanos), true);
						if (validated._status == Status.NO_PATH)
						{
							_metrics.recordNavigationPathNoPath();
						}
						else if (validated._status == Status.ROUTE_BUDGET_EXCEEDED)
						{
							_metrics.recordNavigationPathFailed();
							_metrics.recordNavigationRouteBudgetRejected();
						}
						else
						{
							_metrics.recordNavigationPathFailed();
						}
						continue;
					}
					putCacheLocked(entry, validated._route, completedLogicalNanos);
					completeLocked(entry, result(entry, Status.PATH_FOUND, validated._route, false, completedLogicalNanos), true);
					_metrics.recordNavigationPathSucceeded();
				}
			}
		}
		finally
		{
			synchronized (_monitor)
			{
				releaseWorkerClaimLocked(claim);
			}
		}
	}

	private ValidatedPath validateBackendPath(RequestEntry entry, List<PhantomNavigationPoint> backendPath, long cancellationGeneration)
	{
		if ((backendPath == null) || (backendPath.size() < 2))
		{
			return new ValidatedPath(Status.NO_PATH, null);
		}
		if (backendPath.size() > (_policy.maximumWaypoints() + 1))
		{
			return new ValidatedPath(Status.ROUTE_BUDGET_EXCEEDED, null);
		}
		final List<PhantomNavigationPoint> waypoints = new ArrayList<>(backendPath.size() + 1);
		PhantomNavigationPoint previousCandidate = entry._request.origin();
		for (int index = 0; index < backendPath.size(); index++)
		{
			final PhantomNavigationPoint point = backendPath.get(index);
			if ((point == null) || (point.instanceId() != entry._request.origin().instanceId()))
			{
				return new ValidatedPath(Status.BACKEND_FAILURE, null);
			}
			if ((index == 0) && point.equals(entry._request.origin()))
			{
				continue;
			}
			if (previousCandidate.equals(point))
			{
				return new ValidatedPath(Status.BACKEND_FAILURE, null);
			}
			waypoints.add(point);
			previousCandidate = point;
		}
		if (waypoints.isEmpty() || !waypoints.getLast().equals(entry._request.destination()))
		{
			waypoints.add(entry._request.destination());
		}
		if (waypoints.size() > _policy.maximumWaypoints())
		{
			return new ValidatedPath(Status.ROUTE_BUDGET_EXCEEDED, null);
		}
		final double maximumDistance = routeDistanceBudget(entry._request);
		double totalDistance = 0;
		PhantomNavigationPoint previous = entry._request.origin();
		for (PhantomNavigationPoint point : waypoints)
		{
			totalDistance += previous.distanceTo(point);
			if (!Double.isFinite(totalDistance) || (totalDistance > maximumDistance))
			{
				return new ValidatedPath(Status.ROUTE_BUDGET_EXCEEDED, null);
			}
			previous = point;
		}
		final Status validationStatus = validateSegments(entry, waypoints, cancellationGeneration, false);
		if (validationStatus != Status.PATH_FOUND)
		{
			return new ValidatedPath(validationStatus, null);
		}
		try
		{
			return new ValidatedPath(
				Status.PATH_FOUND,
				new PhantomNavigationRoute(Mode.COMPUTED, entry._request.origin(), entry._request.destination(), waypoints, entry._capability, _clock.getAsLong(), true, _policy.maximumWaypoints(), maximumDistance));
		}
		catch (IllegalArgumentException e)
		{
			return new ValidatedPath(Status.BACKEND_FAILURE, null);
		}
	}

	private Status validateSegments(RequestEntry entry, List<PhantomNavigationPoint> waypoints, long cancellationGeneration, boolean cacheRevalidation)
	{
		PhantomNavigationPoint previous = entry._request.origin();
		for (PhantomNavigationPoint waypoint : waypoints)
		{
			if (entry._cancellation.changedSince(cancellationGeneration))
			{
				return Status.CANCELLED;
			}
			if (deadlineExpired(entry._request, _clock.getAsLong()))
			{
				return Status.DEADLINE_EXPIRED;
			}
			final boolean valid;
			try
			{
				valid = _backend.canMoveDirect(previous, waypoint);
			}
			catch (Throwable throwable)
			{
				return Status.BACKEND_FAILURE;
			}
			if (!valid)
			{
				if (cacheRevalidation)
				{
					_metrics.recordNavigationCacheRouteObstructed();
				}
				else
				{
					_metrics.recordNavigationComputedRouteObstructed();
				}
				return Status.ROUTE_OBSTRUCTED;
			}
			previous = waypoint;
		}
		if (entry._cancellation.changedSince(cancellationGeneration))
		{
			return Status.CANCELLED;
		}
		return deadlineExpired(entry._request, _clock.getAsLong()) ? Status.DEADLINE_EXPIRED : Status.PATH_FOUND;
	}

	private WorkerClaim claimWorkerLocked()
	{
		final WorkerClaim claim = new WorkerClaim();
		_workerClaims.add(claim);
		_workers++;
		_peakWorkers = Math.max(_peakWorkers, _workers);
		_metrics.recordNavigationWorkerStarted();
		return claim;
	}

	private void releaseWorkerClaimLocked(WorkerClaim claim)
	{
		if (!claim._owned)
		{
			return;
		}
		claim._owned = false;
		_workerClaims.remove(claim);
		if (_workers <= 0)
		{
			throw new IllegalStateException("Navigation worker ownership became inconsistent.");
		}
		_workers--;
		_metrics.recordNavigationWorkerStopped();
	}

	private boolean hasAcceptedWorkerLocked()
	{
		for (WorkerClaim claim : _workerClaims)
		{
			if (claim._owned && claim._accepted)
			{
				return true;
			}
		}
		return false;
	}

	private Submission currentSubmissionLocked(RequestEntry entry)
	{
		final PhantomNavigationResult completed = _completed.get(entry._requestId);
		if (completed != null)
		{
			return new Submission(SubmissionStatus.COMPLETED, entry._requestId, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), completed);
		}
		if (isCurrentLocked(entry))
		{
			return new Submission(SubmissionStatus.ACCEPTED, entry._requestId, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), null);
		}
		return completedOrCancelledLocked(entry);
	}

	private void putCacheLocked(RequestEntry entry, PhantomNavigationRoute route, long logicalNow)
	{
		_cache.put(new CacheKey(entry._request.origin(), entry._request.destination(), entry._capability), new CacheEntry(route, logicalNow));
		while (_cache.size() > _policy.maximumCacheEntries())
		{
			_cache.remove(_cache.keySet().iterator().next());
			_metrics.recordNavigationCacheEvicted();
		}
		_peakCache = Math.max(_peakCache, _cache.size());
	}

	private void setCooldownLocked(long profileId, long logicalNow)
	{
		_cooldowns.put(profileId, saturatingAdd(logicalNow, _policy.pathfindingCooldownNanos()));
		while (_cooldowns.size() > _policy.maximumTrackedProfiles())
		{
			_cooldowns.remove(_cooldowns.keySet().iterator().next());
		}
	}

	private Submission completeSubmission(RequestEntry entry, Status status, PhantomNavigationRoute route, boolean fromCache)
	{
		synchronized (_monitor)
		{
			return completeSubmissionLocked(entry, status, route, fromCache, true);
		}
	}

	private Submission completeSubmissionLocked(RequestEntry entry, Status status, PhantomNavigationRoute route, boolean fromCache, boolean retain)
	{
		if (!isCurrentLocked(entry))
		{
			return completedOrCancelledLocked(entry);
		}
		markSubmissionAcceptedLocked(entry);
		final long completedLogicalNanos = _clock.getAsLong();
		final boolean successful = (status == Status.DIRECT_VALIDATED) || (status == Status.DIRECT_UNVERIFIED_NO_GEODATA) || (status == Status.PATH_FOUND);
		if (successful && ((_state != ServiceState.RUNNING) || entry._cancellation.changedSince(entry._initialCancellationGeneration)))
		{
			status = Status.CANCELLED;
			route = null;
			fromCache = false;
		}
		else if (successful && deadlineExpired(entry._request, completedLogicalNanos))
		{
			status = Status.DEADLINE_EXPIRED;
			route = null;
			fromCache = false;
		}
		final PhantomNavigationResult result = result(entry, status, route, fromCache, completedLogicalNanos);
		completeLocked(entry, result, retain);
		return new Submission(SubmissionStatus.COMPLETED, entry._requestId, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), result);
	}

	private void completeLocked(RequestEntry entry, PhantomNavigationResult result, boolean retain)
	{
		_activeByRequest.remove(entry._requestId, entry);
		_activeByProfile.remove(entry._request.profileId(), entry);
		entry._state = RequestState.TERMINAL;
		entry._terminalResult = result;
		if (retain)
		{
			_completed.put(entry._requestId, result);
			while (_completed.size() > _policy.maximumTrackedProfiles())
			{
				_completed.remove(_completed.keySet().iterator().next());
			}
		}
	}

	private Submission completedOrCancelledLocked(RequestEntry entry)
	{
		final PhantomNavigationResult completed = _completed.get(entry._requestId);
		if (completed != null)
		{
			return new Submission(SubmissionStatus.COMPLETED, entry._requestId, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), completed);
		}
		final PhantomNavigationResult cancelled = result(entry, Status.CANCELLED, null, false, _clock.getAsLong());
		return new Submission(SubmissionStatus.COMPLETED, entry._requestId, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), cancelled);
	}

	private PhantomNavigationResult result(RequestEntry entry, Status status, PhantomNavigationRoute route, boolean fromCache, long logicalNow)
	{
		return new PhantomNavigationResult(status, entry._request.profileId(), entry._requestId, entry._capability, route, fromCache, Math.max(0, logicalNow));
	}

	private Submission rejected(long profileId, Status status)
	{
		final PhantomNavigationResult result = new PhantomNavigationResult(status, profileId, 0, null, null, false, Math.max(0, _clock.getAsLong()));
		return new Submission(SubmissionStatus.REJECTED, 0, PhantomNavigationCapability.UNKNOWN, result);
	}

	private boolean isCurrentLocked(RequestEntry entry)
	{
		return (_activeByRequest.get(entry._requestId) == entry) && (_activeByProfile.get(entry._request.profileId()) == entry);
	}

	private void markSubmissionAcceptedLocked(RequestEntry entry)
	{
		if (!entry._submissionCounted)
		{
			entry._submissionCounted = true;
			_metrics.recordNavigationSubmissionAccepted();
		}
	}

	private long nextRequestIdLocked()
	{
		if (_requestSequence == Long.MAX_VALUE)
		{
			return 0;
		}
		return ++_requestSequence;
	}

	private double routeDistanceBudget(PhantomNavigationRequest request)
	{
		return Math.min(request.maximumRouteDistance(), _policy.maximumRouteDistance());
	}

	private static boolean deadlineExpired(PhantomNavigationRequest request, long logicalNow)
	{
		return logicalNow >= request.deadlineLogicalNanos();
	}

	private static long saturatingAdd(long left, long right)
	{
		return left > (Long.MAX_VALUE - right) ? Long.MAX_VALUE : left + right;
	}

	private static RequestSnapshot snapshotLocked(RequestEntry entry)
	{
		return new RequestSnapshot(entry._request.profileId(), entry._requestId, entry._state, entry._capability == null ? PhantomNavigationCapability.UNKNOWN : entry._capability.mode(), entry._terminalResult == null ? null : entry._terminalResult.status());
	}

	private static RequestSnapshot snapshot(PhantomNavigationResult result)
	{
		return new RequestSnapshot(result.profileId(), result.requestId(), RequestState.TERMINAL, result.capability() == null ? PhantomNavigationCapability.UNKNOWN : result.capability().mode(), result.status());
	}

	public record Submission(SubmissionStatus status, long requestId, PhantomNavigationCapability initialCapability, PhantomNavigationResult immediateResult)
	{
	}

	public record CancelResult(CancelStatus status, RequestSnapshot snapshot)
	{
	}

	public record RequestSnapshot(long profileId, long requestId, RequestState state, PhantomNavigationCapability capability, Status resultStatus)
	{
	}

	public record ServiceSnapshot(ServiceState state, int activeRequests, int queuedRequests, int queueCapacity, int peakQueuedRequests, int currentWorkers, int maximumWorkers, int peakWorkers, int cacheEntries, int cacheCapacity, int peakCacheEntries, int completedResults, int cooldownProfiles, int trackedProgressAttempts)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(ServiceState.STOPPED, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final class RequestEntry
	{
		private final long _requestId;
		private final PhantomNavigationRequest _request;
		private final PhantomNavigationCancellationToken _cancellation = new PhantomNavigationCancellationToken();
		private final long _initialCancellationGeneration = _cancellation.generation();
		private RequestState _state = RequestState.DIRECT_CHECK;
		private CapabilitySnapshot _capability;
		private PhantomNavigationResult _terminalResult;
		private boolean _submissionCounted;

		private RequestEntry(long requestId, PhantomNavigationRequest request)
		{
			_requestId = requestId;
			_request = request;
		}
	}

	private record CacheKey(PhantomNavigationPoint origin, PhantomNavigationPoint destination, CapabilitySnapshot capability)
	{
	}

	private static final class CacheEntry
	{
		private final PhantomNavigationRoute _route;
		private final long _createdLogicalNanos;

		private CacheEntry(PhantomNavigationRoute route, long createdLogicalNanos)
		{
			_route = route;
			_createdLogicalNanos = createdLogicalNanos;
		}
	}

	private static final class WorkerClaim
	{
		private boolean _owned = true;
		private boolean _accepted;
	}

	private record ValidatedPath(Status _status, PhantomNavigationRoute _route)
	{
	}
}
