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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;

public final class PhantomNavigationPerformanceSuite implements PhantomTestSuite
{
	private static final int DIRECT_REQUESTS = 10_000;
	private static final int PATH_REQUESTS = 1_000;
	private static final PhantomNavigationPoint ORIGIN = new PhantomNavigationPoint(10_000, 10_000, 0, 0);
	private static final PhantomNavigationPoint MIDPOINT = new PhantomNavigationPoint(10_250, 10_250, 0, 0);
	private static final PhantomNavigationPoint DESTINATION = new PhantomNavigationPoint(10_500, 10_000, 0, 0);

	@Override
	public String id()
	{
		return "navigation-performance";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-direct-and-cache-structural-bounds", this::runPerformance);
	}

	private void runPerformance(PhantomTestContext context)
	{
		final long started = System.nanoTime();
		final PhantomNavigationPolicy policy = PhantomNavigationPolicy.productionDefaults();
		final PhantomMetrics directMetrics = new PhantomMetrics();
		final PerformanceBackend directBackend = new PerformanceBackend(true);
		final ImmediateDispatcher directDispatcher = new ImmediateDispatcher();
		final PhantomNavigationService directService = new PhantomNavigationService(policy, directBackend, directDispatcher, () -> 0, directMetrics);
		PhantomAssertions.assertTrue(directService.start(), "Direct performance service did not start.");
		for (int index = 1; index <= DIRECT_REQUESTS; index++)
		{
			final var result = directService.submit(request(index)).immediateResult();
			PhantomAssertions.assertEquals(Status.DIRECT_VALIDATED, result.status(), "Direct performance request did not remain direct.");
		}
		directService.beginStop();
		PhantomAssertions.assertTrue(directService.finishStop(), "Direct performance service did not stop.");

		final PhantomMetrics pathMetrics = new PhantomMetrics();
		final PerformanceBackend pathBackend = new PerformanceBackend(false);
		final ImmediateDispatcher pathDispatcher = new ImmediateDispatcher();
		final PhantomNavigationService pathService = new PhantomNavigationService(policy, pathBackend, pathDispatcher, () -> 0, pathMetrics);
		PhantomAssertions.assertTrue(pathService.start(), "Path performance service did not start.");
		for (int index = 1; index <= PATH_REQUESTS; index++)
		{
			final var submission = pathService.submit(request(index));
			final var result = submission.immediateResult() != null ? submission.immediateResult() : pathService.consume(submission.requestId()).orElseThrow();
			PhantomAssertions.assertEquals(Status.PATH_FOUND, result.status(), "Repeated path request failed.");
		}
		final var serviceSnapshot = pathService.snapshot();
		final var navigation = pathMetrics.snapshot().navigation();
		final double cacheHitRate = navigation.cacheHits() / (double) PATH_REQUESTS;
		PhantomAssertions.assertTrue(cacheHitRate >= 0.90, "Computed-route cache hit rate fell below 90%.");
		PhantomAssertions.assertTrue(serviceSnapshot.peakQueuedRequests() <= 256, "Queue bound was exceeded.");
		PhantomAssertions.assertTrue(serviceSnapshot.peakWorkers() <= 2, "Worker bound was exceeded.");
		PhantomAssertions.assertTrue(serviceSnapshot.peakCacheEntries() <= 1024, "Cache bound was exceeded.");
		PhantomAssertions.assertTrue(pathBackend._maximumWaypoints <= 64, "Waypoint bound was exceeded.");
		pathService.beginStop();
		PhantomAssertions.assertTrue(pathService.finishStop(), "Path performance service did not stop.");

		final long elapsedNanos = System.nanoTime() - started;
		final String canonical = "directRequests=" + DIRECT_REQUESTS //
			+ " pathRequests=" + PATH_REQUESTS //
			+ " directResults=" + directMetrics.snapshot().navigation().directValidated() //
			+ " pathResults=" + (navigation.pathSucceeded() + navigation.cacheHits()) //
			+ " cacheHits=" + navigation.cacheHits() //
			+ " cacheMisses=" + navigation.cacheMisses() //
			+ " backendPathCalls=" + pathBackend._pathCalls //
			+ " peakQueue=" + serviceSnapshot.peakQueuedRequests() //
			+ " peakWorkers=" + serviceSnapshot.peakWorkers() //
			+ " peakCache=" + serviceSnapshot.peakCacheEntries() //
			+ " maximumWaypoints=" + pathBackend._maximumWaypoints //
			+ " cancelled=" + navigation.pathCancelled() //
			+ " timedOut=" + navigation.pathTimedOut();
		System.out.println("NAVIGATION_PERFORMANCE_CANONICAL " + canonical);
		System.out.println("NAVIGATION_PERFORMANCE_ELAPSED_NANOS " + elapsedNanos);
		context.record("navigation.performance.canonical", canonical);
		context.record("navigation.performance.elapsedNanos", elapsedNanos);
	}

	private static PhantomNavigationRequest request(long profileId)
	{
		return new PhantomNavigationRequest(profileId, ORIGIN, DESTINATION, 0, 1_000_000, 100_000);
	}

	private static final class ImmediateDispatcher implements PhantomNavigationService.Dispatcher
	{
		private final Deque<Runnable> _pending = new ArrayDeque<>();
		private boolean _running;

		@Override
		public boolean dispatch(Runnable worker)
		{
			_pending.addLast(worker);
			if (!_running)
			{
				_running = true;
				try
				{
					while (!_pending.isEmpty())
					{
						_pending.removeFirst().run();
					}
				}
				finally
				{
					_running = false;
				}
			}
			return true;
		}
	}

	private static final class PerformanceBackend implements PhantomNavigationBackend
	{
		private final boolean _direct;
		private int _pathCalls;
		private int _maximumWaypoints;

		private PerformanceBackend(boolean direct)
		{
			_direct = direct;
		}

		@Override
		public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		}

		@Override
		public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			return _direct || !origin.equals(ORIGIN) || !destination.equals(DESTINATION);
		}

		@Override
		public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
		{
			_pathCalls++;
			final List<PhantomNavigationPoint> path = List.of(request.origin(), MIDPOINT, request.destination());
			_maximumWaypoints = Math.max(_maximumWaypoints, path.size());
			return path;
		}
	}
}
