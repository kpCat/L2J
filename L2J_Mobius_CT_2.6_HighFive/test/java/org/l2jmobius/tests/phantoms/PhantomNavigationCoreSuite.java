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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker.ProgressStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;

public final class PhantomNavigationCoreSuite implements PhantomTestSuite
{
	private static final PhantomNavigationPoint ORIGIN = point(10_000, 10_000);
	private static final PhantomNavigationPoint DESTINATION = point(10_500, 10_000);

	@Override
	public String id()
	{
		return "navigation-core";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-point-contract", _ -> testPointContract());
		registry.add("02-request-contract", _ -> testRequestContract());
		registry.add("03-route-immutable-and-exact", _ -> testRouteImmutable());
		registry.add("04-route-rejects-adjacent-duplicate", _ -> testRouteDuplicate());
		registry.add("05-production-policy-bounds", _ -> testProductionPolicy());
		registry.add("06-capability-snapshot-contract", _ -> testCapabilitySnapshot());
		registry.add("06-service-starts-empty-and-inert", _ -> testServiceStartsEmpty());
		registry.add("07-direct-validated", _ -> testDirect(PhantomNavigationCapability.GEODATA_PATHFINDING, Status.DIRECT_VALIDATED, Mode.DIRECT_VALIDATED));
		registry.add("08-direct-unverified-no-geodata", _ -> testDirect(PhantomNavigationCapability.NO_GEODATA, Status.DIRECT_UNVERIFIED_NO_GEODATA, Mode.DIRECT_UNVERIFIED_NO_GEODATA));
		registry.add("09-direct-unverified-partial-geodata", _ -> testDirect(PhantomNavigationCapability.PARTIAL_GEODATA, Status.DIRECT_UNVERIFIED_NO_GEODATA, Mode.DIRECT_UNVERIFIED_NO_GEODATA));
		registry.add("10-blocked-no-geodata", _ -> testBlocked(PhantomNavigationCapability.NO_GEODATA, Status.NO_GEODATA));
		registry.add("11-blocked-partial-geodata", _ -> testBlocked(PhantomNavigationCapability.PARTIAL_GEODATA, Status.NO_GEODATA));
		registry.add("12-blocked-pathfinding-disabled", _ -> testBlocked(PhantomNavigationCapability.GEODATA_DIRECT_ONLY, Status.PATHFINDING_DISABLED));
		registry.add("13-local-path-found", _ -> testPathFound());
		registry.add("14-no-path-has-no-direct-fallback", _ -> testNoPath());
		registry.add("14-short-path-is-no-path", _ -> testShortPath());
		registry.add("15-local-distance-budget", _ -> testLocalDistanceBudget());
		registry.add("16-waypoint-budget", _ -> testWaypointBudget());
		registry.add("17-route-distance-budget", _ -> testRouteDistanceBudget());
		registry.add("18-one-active-request-per-profile", _ -> testProfileBusy());
		registry.add("19-queue-backpressure-atomic", _ -> testQueueBackpressure());
		registry.add("20-two-shared-workers-maximum", _ -> testWorkerBound());
		registry.add("21-queued-cancellation", _ -> testQueuedCancellation());
		registry.add("22-inflight-cancellation-late-discard", _ -> testInflightCancellation());
		registry.add("23-deadline-before-worker-start", _ -> testQueuedDeadline());
		registry.add("24-deadline-during-backend-late-discard", _ -> testInflightDeadline());
		registry.add("25-backend-exception-isolated", _ -> testBackendFailure());
		registry.add("25-dispatcher-failure-does-not-strand-worker", _ -> testDispatcherFailure());
		registry.add("26-cache-hit-after-revalidation", _ -> testCacheHit());
		registry.add("27-cache-invalidated-by-obstacle", _ -> testCacheInvalidation());
		registry.add("28-bounded-lru-eviction", _ -> testLruEviction());
		registry.add("29-cooldown-does-not-block-direct", _ -> testCooldownDirectBypass());
		registry.add("30-stop-waits-for-inflight-worker", _ -> testStopQuiescence());
		registry.add("31-progress-and-arrival", _ -> testProgressArrival());
		registry.add("32-stuck-window", _ -> testStuck());
		registry.add("33-attempt-timeout-precedes-stuck", _ -> testAttemptTimeout());
		registry.add("34-stale-time-and-request", _ -> testStaleProgress());
		registry.add("35-metrics-fixed-aggregate-snapshot", _ -> testMetrics());
	}

	private void testPointContract()
	{
		PhantomAssertions.assertEquals(500.0, ORIGIN.distanceTo(DESTINATION), "Point distance changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationPoint(Integer.MAX_VALUE, 0, 0, 0), "Out-of-world point was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationPoint(0, 0, 0, -1), "Negative instance was accepted.");
	}

	private void testRequestContract()
	{
		final PhantomNavigationRequest request = request(1, ORIGIN, DESTINATION, 0, 100);
		PhantomAssertions.assertEquals(1L, request.profileId(), "Request profile changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationRequest(0, ORIGIN, DESTINATION, 0, 1, 1000), "Zero profile was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationRequest(1, ORIGIN, new PhantomNavigationPoint(10_100, 10_100, 0, 1), 0, 1, 1000), "Cross-instance request was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationRequest(1, ORIGIN, DESTINATION, 2, 2, 1000), "Non-forward deadline was accepted.");
	}

	private void testRouteImmutable()
	{
		final ArrayList<PhantomNavigationPoint> source = new ArrayList<>(List.of(DESTINATION));
		final PhantomNavigationRoute route = route(source);
		source.clear();
		PhantomAssertions.assertEquals(List.of(DESTINATION), route.waypoints(), "Route retained mutable source state.");
		PhantomAssertions.assertEquals(DESTINATION, route.destination(), "Route lost exact destination.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> route.waypoints().add(ORIGIN), "Route list remained mutable.");
	}

	private void testRouteDuplicate()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationRoute(Mode.COMPUTED, ORIGIN, DESTINATION, List.of(point(10_250, 10_000), point(10_250, 10_000), DESTINATION), capability(PhantomNavigationCapability.GEODATA_PATHFINDING), 0, true, 64, 1000), "Adjacent duplicate route points were accepted.");
	}

	private void testProductionPolicy()
	{
		final PhantomNavigationPolicy policy = PhantomNavigationPolicy.productionDefaults();
		PhantomAssertions.assertEquals(256, policy.maximumQueuedRequests(), "Queue capacity changed.");
		PhantomAssertions.assertEquals(2, policy.maximumConcurrentPathfinders(), "Worker bound changed.");
		PhantomAssertions.assertEquals(10_000, policy.maximumTrackedProfiles(), "Tracked profile bound changed.");
		PhantomAssertions.assertEquals(1024, policy.maximumCacheEntries(), "Cache bound changed.");
		PhantomAssertions.assertEquals(64, policy.maximumWaypoints(), "Waypoint bound changed.");
	}

	private void testCapabilitySnapshot()
	{
		PhantomAssertions.assertEquals(PhantomNavigationCapability.NO_GEODATA, capability(PhantomNavigationCapability.NO_GEODATA).mode(), "Capability mode changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new CapabilitySnapshot(PhantomNavigationCapability.UNKNOWN, 0), "Unknown capability was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new CapabilitySnapshot(PhantomNavigationCapability.NO_GEODATA, -1), "Negative generation was accepted.");
	}

	private void testServiceStartsEmpty()
	{
		final Fixture fixture = pathFixture();
		final var snapshot = fixture.service.snapshot();
		PhantomAssertions.assertEquals(ServiceState.RUNNING, snapshot.state(), "Started service state changed.");
		PhantomAssertions.assertEquals(0, snapshot.activeRequests(), "Started service created a request.");
		PhantomAssertions.assertEquals(0, snapshot.queuedRequests(), "Started service queued work.");
		PhantomAssertions.assertEquals(0, snapshot.currentWorkers(), "Started service submitted a worker.");
		PhantomAssertions.assertEquals(0, snapshot.cacheEntries(), "Started service populated cache.");
		stop(fixture);
	}

	private void testDirect(PhantomNavigationCapability capability, Status expectedStatus, Mode expectedMode)
	{
		final Fixture fixture = fixture(policy(4, 2, 32, 4), capability);
		fixture.backend._directDefault = true;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(SubmissionStatus.COMPLETED, submission.status(), "Direct request was not synchronous.");
		PhantomAssertions.assertEquals(expectedStatus, submission.immediateResult().status(), "Direct result status changed.");
		PhantomAssertions.assertEquals(expectedMode, submission.immediateResult().route().mode(), "Direct route mode changed.");
		PhantomAssertions.assertEquals(1, fixture.backend._directCalls, "Initial direct check count changed.");
		PhantomAssertions.assertEquals(0, fixture.backend._pathCalls, "Direct route invoked pathfinding.");
		stop(fixture);
	}

	private void testBlocked(PhantomNavigationCapability capability, Status expected)
	{
		final Fixture fixture = fixture(policy(4, 2, 32, 4), capability);
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(expected, submission.immediateResult().status(), "Blocked capability result changed.");
		PhantomAssertions.assertEquals(0, fixture.backend._pathCalls, "Blocked capability invoked pathfinding.");
		stop(fixture);
	}

	private void testPathFound()
	{
		final Fixture fixture = pathFixture();
		final ArrayList<PhantomNavigationPoint> backendPath = new ArrayList<>(List.of(ORIGIN, point(10_250, 10_100)));
		fixture.backend._path = backendPath;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, submission.status(), "Local path request was not queued.");
		fixture.dispatcher.runAll();
		final var result = fixture.service.consume(submission.requestId()).orElseThrow();
		backendPath.clear();
		PhantomAssertions.assertEquals(Status.PATH_FOUND, result.status(), "Valid backend path was rejected.");
		PhantomAssertions.assertEquals(Mode.COMPUTED, result.route().mode(), "Computed route mode changed.");
		PhantomAssertions.assertEquals(DESTINATION, result.route().waypoints().getLast(), "Exact destination was not appended.");
		PhantomAssertions.assertEquals(2, result.route().waypoints().size(), "Route retained mutable backend points.");
		stop(fixture);
	}

	private void testNoPath()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._path = null;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.NO_PATH, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Null path was not typed NO_PATH.");
		PhantomAssertions.assertEquals(1, fixture.backend._directCalls, "A failed A* triggered a direct fallback.");
		stop(fixture);
	}

	private void testShortPath()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._path = List.of(ORIGIN);
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.NO_PATH, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Short path was accepted.");
		stop(fixture);
	}

	private void testLocalDistanceBudget()
	{
		final Fixture fixture = pathFixture();
		final PhantomNavigationPoint far = point(30_000, 10_000);
		final var result = fixture.service.submit(request(1, ORIGIN, far, 0, 100_000)).immediateResult();
		PhantomAssertions.assertEquals(Status.ROUTE_BUDGET_EXCEEDED, result.status(), "Non-local path reached the queue.");
		PhantomAssertions.assertEquals(0, fixture.dispatcher.size(), "Non-local path submitted a worker.");
		stop(fixture);
	}

	private void testWaypointBudget()
	{
		final Fixture fixture = pathFixture();
		final ArrayList<PhantomNavigationPoint> path = new ArrayList<>();
		path.add(ORIGIN);
		for (int index = 1; index <= 65; index++)
		{
			path.add(point(10_000 + index, 10_000));
		}
		path.add(DESTINATION);
		fixture.backend._path = path;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 1000));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.ROUTE_BUDGET_EXCEEDED, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Oversized waypoint path was accepted.");
		stop(fixture);
	}

	private void testRouteDistanceBudget()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._path = List.of(ORIGIN, point(10_000, 10_500), DESTINATION);
		final var submission = fixture.service.submit(requestWithBudget(1, ORIGIN, DESTINATION, 0, 100, 600));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.ROUTE_BUDGET_EXCEEDED, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Over-budget detour was accepted.");
		stop(fixture);
	}

	private void testProfileBusy()
	{
		final Fixture fixture = pathFixture();
		final var first = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		final var second = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(SubmissionStatus.REJECTED, second.status(), "Second active profile request was accepted.");
		PhantomAssertions.assertEquals(Status.PROFILE_BUSY, second.immediateResult().status(), "Profile contention was not typed.");
		PhantomAssertions.assertEquals(first.requestId(), fixture.service.find(first.requestId()).orElseThrow().requestId(), "First request ownership changed.");
		fixture.dispatcher.runAll();
		stop(fixture);
	}

	private void testQueueBackpressure()
	{
		final Fixture fixture = fixture(policy(1, 1, 32, 4), PhantomNavigationCapability.GEODATA_PATHFINDING);
		fixture.backend._path = List.of(ORIGIN, DESTINATION);
		final var first = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		final var rejected = fixture.service.submit(request(2, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(Status.QUEUE_BACKPRESSURE, rejected.immediateResult().status(), "Full queue was not rejected.");
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().activeRequests(), "Rejected request retained profile ownership.");
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.PATH_FOUND, fixture.service.consume(first.requestId()).orElseThrow().status(), "Accepted queued request changed.");
		final var retry = fixture.service.submit(request(2, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, retry.status(), "Rejected profile ownership was stranded.");
		fixture.dispatcher.runAll();
		stop(fixture);
	}

	private void testWorkerBound() throws Exception
	{
		final Fixture fixture = pathFixture();
		final CountDownLatch entered = new CountDownLatch(2);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicInteger active = new AtomicInteger();
		final AtomicInteger peak = new AtomicInteger();
		fixture.backend._duringPath = _ ->
		{
			final int current = active.incrementAndGet();
			peak.accumulateAndGet(current, Math::max);
			entered.countDown();
			try
			{
				if (!release.await(1, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Timed out waiting for the worker concurrency gate.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			finally
			{
				active.decrementAndGet();
			}
		};
		for (int profile = 1; profile <= 8; profile++)
		{
			fixture.service.submit(request(profile, ORIGIN, DESTINATION, 0, 100));
		}
		PhantomAssertions.assertEquals(2, fixture.dispatcher.size(), "More or fewer than two drain workers were claimed.");
		PhantomAssertions.assertEquals(2, fixture.service.snapshot().peakWorkers(), "Worker peak changed.");
		final Thread first = new Thread(fixture.dispatcher.take(), "phantom-navigation-test-worker-1");
		final Thread second = new Thread(fixture.dispatcher.take(), "phantom-navigation-test-worker-2");
		first.start();
		second.start();
		try
		{
			PhantomAssertions.assertTrue(entered.await(1, TimeUnit.SECONDS), "Two backend calls did not run concurrently.");
			PhantomAssertions.assertEquals(2, peak.get(), "Backend concurrency did not reach its structural bound.");
		}
		finally
		{
			release.countDown();
			first.join(TimeUnit.SECONDS.toMillis(2));
			second.join(TimeUnit.SECONDS.toMillis(2));
		}
		PhantomAssertions.assertFalse(first.isAlive() || second.isAlive(), "Drain worker test threads did not terminate.");
		PhantomAssertions.assertTrue(peak.get() <= 2, "Backend concurrency exceeded two workers.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Workers did not exit after drain.");
		stop(fixture);
	}

	private void testQueuedCancellation()
	{
		final Fixture fixture = pathFixture();
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		final var cancellation = fixture.service.cancel(1, submission.requestId());
		PhantomAssertions.assertEquals(CancelStatus.CANCELLED_QUEUED, cancellation.status(), "Queued cancellation changed.");
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.CANCELLED, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Queued cancellation result changed.");
		PhantomAssertions.assertEquals(0, fixture.backend._pathCalls, "Cancelled queued request invoked A*.");
		stop(fixture);
	}

	private void testInflightCancellation()
	{
		final Fixture fixture = pathFixture();
		final PhantomNavigationService[] service = new PhantomNavigationService[1];
		final long[] requestId = new long[1];
		fixture.backend._duringPath = _ -> fixture.service.cancel(1, requestId[0]);
		service[0] = fixture.service;
		final var submission = service[0].submit(request(1, ORIGIN, DESTINATION, 0, 100));
		requestId[0] = submission.requestId();
		fixture.dispatcher.runAll();
		final var result = fixture.service.consume(submission.requestId()).orElseThrow();
		PhantomAssertions.assertEquals(Status.CANCELLED, result.status(), "Cancelled late A* result was published.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().cacheEntries(), "Cancelled route entered cache.");
		stop(fixture);
	}

	private void testQueuedDeadline()
	{
		final Fixture fixture = pathFixture();
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 10));
		fixture.clock._now = 10;
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.DEADLINE_EXPIRED, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Queue deadline did not expire.");
		PhantomAssertions.assertEquals(0, fixture.backend._pathCalls, "Expired queued request invoked A*.");
		stop(fixture);
	}

	private void testInflightDeadline()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._duringPath = _ -> fixture.clock._now = 10;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 10));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.DEADLINE_EXPIRED, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Late A* result was published.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().cacheEntries(), "Late route entered cache.");
		stop(fixture);
	}

	private void testBackendFailure()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._pathFailure = new IllegalStateException("expected");
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(Status.BACKEND_FAILURE, fixture.service.consume(submission.requestId()).orElseThrow().status(), "Backend exception escaped or changed type.");
		stop(fixture);
	}

	private void testDispatcherFailure()
	{
		final Fixture fixture = pathFixture();
		fixture.dispatcher._accept = false;
		final var submission = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(Status.BACKEND_FAILURE, submission.immediateResult().status(), "Dispatcher failure did not return stable failure.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().currentWorkers(), "Dispatcher failure stranded worker ownership.");
		PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeRequests(), "Dispatcher failure stranded request ownership.");
		stop(fixture);
	}

	private void testCacheHit()
	{
		final Fixture fixture = pathFixture();
		final var first = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		fixture.service.consume(first.requestId()).orElseThrow();
		fixture.backend.answers(false, true);
		final var second = fixture.service.submit(request(2, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(Status.PATH_FOUND, second.immediateResult().status(), "Revalidated cache did not complete synchronously.");
		PhantomAssertions.assertTrue(second.immediateResult().fromCache(), "Cache hit was not marked.");
		PhantomAssertions.assertEquals(1, fixture.backend._pathCalls, "Cache hit invoked A*.");
		stop(fixture);
	}

	private void testCacheInvalidation()
	{
		final Fixture fixture = pathFixture();
		final var first = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		fixture.service.consume(first.requestId()).orElseThrow();
		fixture.backend.answers(false, false);
		final var second = fixture.service.submit(request(2, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, second.status(), "Invalidated cache incorrectly returned a route.");
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(2, fixture.backend._pathCalls, "Dynamic obstacle did not force A*.");
		stop(fixture);
	}

	private void testLruEviction()
	{
		final Fixture fixture = fixture(policy(8, 1, 32, 2), PhantomNavigationCapability.GEODATA_PATHFINDING);
		for (int index = 0; index < 3; index++)
		{
			final PhantomNavigationPoint destination = point(10_300 + (index * 100), 10_000);
			fixture.backend._path = List.of(ORIGIN, destination);
			final var submission = fixture.service.submit(request(index + 1, ORIGIN, destination, 0, 1000));
			fixture.dispatcher.runAll();
			fixture.service.consume(submission.requestId()).orElseThrow();
		}
		PhantomAssertions.assertEquals(2, fixture.service.snapshot().cacheEntries(), "Cache exceeded its capacity.");
		fixture.backend._path = List.of(ORIGIN, point(10_300, 10_000));
		final var retry = fixture.service.submit(request(10, ORIGIN, point(10_300, 10_000), 0, 1000));
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, retry.status(), "Least-recently-used entry was not evicted.");
		fixture.dispatcher.runAll();
		PhantomAssertions.assertEquals(4, fixture.backend._pathCalls, "Evicted route did not invoke A*.");
		stop(fixture);
	}

	private void testCooldownDirectBypass()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._path = null;
		final var failed = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		fixture.service.consume(failed.requestId()).orElseThrow();
		final var blocked = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(Status.COOLDOWN, blocked.immediateResult().status(), "Cooldown did not block a repeated A* request.");
		PhantomAssertions.assertEquals(1, fixture.backend._pathCalls, "Cooldown invoked A*.");
		fixture.backend._directDefault = true;
		final var direct = fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		PhantomAssertions.assertEquals(Status.DIRECT_VALIDATED, direct.immediateResult().status(), "Cooldown blocked a new direct route.");
		PhantomAssertions.assertEquals(1, fixture.backend._pathCalls, "Direct cooldown bypass invoked A*.");
		stop(fixture);
	}

	private void testStopQuiescence()
	{
		final Fixture fixture = pathFixture();
		final boolean[] observed = new boolean[1];
		fixture.backend._duringPath = _ ->
		{
			fixture.service.beginStop();
			observed[0] = !fixture.service.finishStop();
		};
		fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		fixture.dispatcher.runAll();
		PhantomAssertions.assertTrue(observed[0], "Stop did not retain in-flight ownership.");
		PhantomAssertions.assertEquals(ServiceState.STOPPING, fixture.service.snapshot().state(), "Service left STOPPING before worker return.");
		PhantomAssertions.assertTrue(fixture.service.finishStop(), "Service did not finish after worker return.");
		PhantomAssertions.assertEquals(Status.SERVICE_NOT_RUNNING, fixture.service.submit(request(2, ORIGIN, DESTINATION, 0, 100)).immediateResult().status(), "Stopped service accepted work.");
	}

	private void testProgressArrival()
	{
		final Fixture fixture = pathFixture();
		final PhantomNavigationProgressTracker tracker = fixture.service.progressTracker();
		PhantomAssertions.assertEquals(PhantomNavigationProgressTracker.BeginStatus.TRACKING, tracker.begin(1, 1, route(List.of(DESTINATION)), 0).status(), "Progress tracker did not start.");
		PhantomAssertions.assertEquals(ProgressStatus.PROGRESS, tracker.observe(1, 1, point(10_100, 10_000), 1).status(), "Forward movement was not progress.");
		PhantomAssertions.assertEquals(ProgressStatus.ARRIVED, tracker.observe(1, 1, point(10_480, 10_000), 2).status(), "Arrival radius changed.");
		stop(fixture);
	}

	private void testStuck()
	{
		final Fixture fixture = fixture(policy(4, 1, 32, 4), PhantomNavigationCapability.GEODATA_PATHFINDING);
		final PhantomNavigationProgressTracker tracker = fixture.service.progressTracker();
		tracker.begin(1, 1, route(List.of(DESTINATION)), 0);
		PhantomAssertions.assertEquals(ProgressStatus.STUCK, tracker.observe(1, 1, ORIGIN, 3_000_000_000L).status(), "Stuck window did not terminate attempt.");
		stop(fixture);
	}

	private void testAttemptTimeout()
	{
		final PhantomNavigationPolicy policy = new PhantomNavigationPolicy(4, 1, 32, 4, 5000, 1000, 12_000, 64, 100_000, 1000, 3000, 20, 50, 3000);
		final Fixture fixture = fixture(policy, PhantomNavigationCapability.GEODATA_PATHFINDING);
		final PhantomNavigationProgressTracker tracker = fixture.service.progressTracker();
		tracker.begin(1, 1, route(List.of(DESTINATION)), 0);
		PhantomAssertions.assertEquals(ProgressStatus.TIMEOUT, tracker.observe(1, 1, ORIGIN, 3_000_000_000L).status(), "Attempt timeout did not precede stuck.");
		stop(fixture);
	}

	private void testStaleProgress()
	{
		final Fixture fixture = pathFixture();
		final PhantomNavigationProgressTracker tracker = fixture.service.progressTracker();
		tracker.begin(1, 1, route(List.of(DESTINATION)), 10);
		PhantomAssertions.assertEquals(ProgressStatus.STALE, tracker.observe(1, 2, ORIGIN, 11).status(), "Wrong request was accepted.");
		PhantomAssertions.assertEquals(ProgressStatus.STALE, tracker.observe(1, 1, ORIGIN, 9).status(), "Regressed logical time was accepted.");
		stop(fixture);
	}

	private void testMetrics()
	{
		final Fixture fixture = pathFixture();
		fixture.backend._directDefault = true;
		fixture.service.submit(request(1, ORIGIN, DESTINATION, 0, 100));
		final var navigation = fixture.metrics.snapshot().navigation();
		PhantomAssertions.assertEquals(1L, navigation.submissionsAccepted(), "Accepted counter changed.");
		PhantomAssertions.assertEquals(1L, navigation.directValidated(), "Direct counter changed.");
		PhantomAssertions.assertEquals(0L, navigation.queuedCurrent(), "Direct request affected queue gauge.");
		stop(fixture);
	}

	private static Fixture pathFixture()
	{
		final Fixture fixture = fixture(policy(16, 2, 64, 8), PhantomNavigationCapability.GEODATA_PATHFINDING);
		fixture.backend._path = List.of(ORIGIN, DESTINATION);
		return fixture;
	}

	private static Fixture fixture(PhantomNavigationPolicy policy, PhantomNavigationCapability capability)
	{
		final ManualClock clock = new ManualClock();
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final FakeBackend backend = new FakeBackend(capability);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomNavigationService service = new PhantomNavigationService(policy, backend, dispatcher, clock, metrics);
		PhantomAssertions.assertTrue(service.start(), "Navigation service did not start.");
		return new Fixture(clock, dispatcher, backend, metrics, service);
	}

	private static PhantomNavigationPolicy policy(int queue, int workers, int profiles, int cache)
	{
		return new PhantomNavigationPolicy(queue, workers, profiles, cache, 5000, 1000, 12_000, 64, 100_000, 1000, 3000, 20, 50, 120_000);
	}

	private static PhantomNavigationRequest request(long profileId, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long now, long deadlineDelay)
	{
		return requestWithBudget(profileId, origin, destination, now, deadlineDelay, 100_000);
	}

	private static PhantomNavigationRequest requestWithBudget(long profileId, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long now, long deadlineDelay, int maximumDistance)
	{
		return new PhantomNavigationRequest(profileId, origin, destination, now, now + deadlineDelay, maximumDistance);
	}

	private static PhantomNavigationRoute route(List<PhantomNavigationPoint> waypoints)
	{
		return new PhantomNavigationRoute(Mode.COMPUTED, ORIGIN, DESTINATION, waypoints, capability(PhantomNavigationCapability.GEODATA_PATHFINDING), 0, true, 64, 100_000);
	}

	private static CapabilitySnapshot capability(PhantomNavigationCapability capability)
	{
		return new CapabilitySnapshot(capability, capability.ordinal());
	}

	private static PhantomNavigationPoint point(int x, int y)
	{
		return new PhantomNavigationPoint(x, y, 0, 0);
	}

	private static void stop(Fixture fixture)
	{
		if (fixture.service.snapshot().state() == ServiceState.RUNNING)
		{
			fixture.service.beginStop();
		}
		fixture.dispatcher.runAll();
		if (fixture.service.snapshot().state() != ServiceState.STOPPED)
		{
			PhantomAssertions.assertTrue(fixture.service.finishStop(), "Navigation fixture did not stop.");
		}
	}

	private record Fixture(ManualClock clock, ManualDispatcher dispatcher, FakeBackend backend, PhantomMetrics metrics, PhantomNavigationService service)
	{
	}

	private static final class ManualClock implements LongSupplier
	{
		private long _now;

		@Override
		public long getAsLong()
		{
			return _now;
		}
	}

	private static final class ManualDispatcher implements PhantomNavigationService.Dispatcher
	{
		private final Deque<Runnable> _workers = new ArrayDeque<>();
		private boolean _accept = true;

		@Override
		public boolean dispatch(Runnable worker)
		{
			if (!_accept)
			{
				return false;
			}
			_workers.addLast(worker);
			return true;
		}

		private int size()
		{
			return _workers.size();
		}

		private Runnable take()
		{
			return _workers.removeFirst();
		}

		private void runAll()
		{
			while (!_workers.isEmpty())
			{
				_workers.removeFirst().run();
			}
		}
	}

	private static final class FakeBackend implements PhantomNavigationBackend
	{
		private final CapabilitySnapshot _capability;
		private final Deque<Boolean> _directAnswers = new ArrayDeque<>();
		private boolean _directDefault;
		private int _directCalls;
		private int _pathCalls;
		private List<PhantomNavigationPoint> _path;
		private RuntimeException _pathFailure;
		private Consumer<PhantomNavigationCancellationToken> _duringPath = _ ->
		{
		};

		private FakeBackend(PhantomNavigationCapability capability)
		{
			_capability = PhantomNavigationCoreSuite.capability(capability);
		}

		private void answers(boolean... answers)
		{
			for (boolean answer : answers)
			{
				_directAnswers.addLast(answer);
			}
		}

		@Override
		public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			return _capability;
		}

		@Override
		public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			_directCalls++;
			return _directAnswers.isEmpty() ? _directDefault : _directAnswers.removeFirst();
		}

		@Override
		public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
		{
			_pathCalls++;
			_duringPath.accept(cancellationToken);
			if (_pathFailure != null)
			{
				throw _pathFailure;
			}
			return _path;
		}
	}
}
