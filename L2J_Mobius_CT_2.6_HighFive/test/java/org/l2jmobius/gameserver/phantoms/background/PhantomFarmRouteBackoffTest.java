/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import org.l2jmobius.gameserver.phantoms.background.PhantomFarmRouteBackoff.Probe;
import org.l2jmobius.gameserver.phantoms.background.PhantomFarmRouteBackoff.Site;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** DB-free replay with an injected path backend and logical clock. */
public final class PhantomFarmRouteBackoffTest
{
	public static void main(String[] args)
	{
		final var failures = new java.util.ArrayList<AssertionError>();
		check(failures, () -> replay(24, 268486107, 345964398585800L, 143, point(-90710, 246545, -3728),
			List.of(point(-90439, 246804, -3592), point(-90682, 246919, -3592)), LimitKind.NO_PATH_WITHIN_SEARCH));
		check(failures, () -> replay(32, 268486156, 346380924728600L, 161, point(113965, -165718, 40),
			List.of(point(114053, -165883, 288), point(114007, -165900, 272)), LimitKind.SEARCH_LIMIT));
		check(failures, PhantomFarmRouteBackoffTest::expiryAndIsolation);
		check(failures, PhantomFarmRouteBackoffTest::stationaryPulses);
		check(failures, PhantomFarmRouteBackoffTest::compactCounters);
		if (!failures.isEmpty()) { throw new AssertionError(failures.size() + " focused cases failed"); }
	}

	private static void check(List<AssertionError> failures, Runnable test)
	{
		try { test.run(); }
		catch (AssertionError failure) { failures.add(failure); System.err.println(failure.getMessage()); }
	}

	private static void replay(long profileId, int objectId, long epoch, int revision, PhantomNavigationPoint origin,
		List<PhantomNavigationPoint> destinations, LimitKind refusal)
	{
		final var guard = new PhantomFarmRouteBackoff(32, _ -> 0, (_, _) -> true);
		final var site = new Site(profileId, objectId, epoch, 1, 1, 1);
		final var calls = new AtomicInteger();
		final java.util.function.Function<Probe, LimitKind> backend = probe ->
		{
			calls.incrementAndGet();
			return refusal;
		};
		for (int next = revision; next < revision + 5; next++)
		{
			final int step = next - revision;
			final var live = point(origin.x() + step * 3, origin.y() - step * 4, origin.z());
			final var probe = new Probe(site, live, destinations.get(step % 2));
			final long now = 1 + step * 20_000_000_000L;
			if (!guard.blocked(probe, now)) { guard.failed(probe, backend.apply(probe), 8, now, "profile=" + profileId + ";kind=" + refusal); }
		}
		System.out.println("profile=" + profileId + " proof=" + refusal + " requests=" + calls.get() + "/5");
		PhantomAssertions.assertEquals(1, calls.get(), "Equivalent revised routes must wait after the first search, profile " + profileId);
		final var alternative = new Probe(site, origin, point(origin.x() - 600, origin.y() + 600, origin.z()));
		PhantomAssertions.assertFalse(guard.blocked(alternative, 81_000_000_001L), "A spatially different route can enter ordinary validation.");
	}

	private static void expiryAndIsolation()
	{
		final var traversals = new AtomicInteger();
		final var guard = new PhantomFarmRouteBackoff(32, _ -> traversals.get(), (_, _) -> false);
		final var site = new Site(32, 100, 200, 1, 1, 1);
		final var origin = point(200, 200, 0);
		final var target = point(400, 400, 288);
		final var failure = new Probe(site, origin, target);
		guard.failed(failure, LimitKind.SEARCH_LIMIT, 32, 1, "isolation");
		PhantomAssertions.assertTrue(guard.blocked(failure, 2), "A SEARCH_LIMIT temporarily holds the same search.");
		PhantomAssertions.assertFalse(guard.blocked(new Probe(site, origin, point(400, 400, 304)), 2), "A nearby stacked floor is independent.");
		PhantomAssertions.assertFalse(guard.blocked(new Probe(site, point(200, 200, 64), target), 2), "A different origin floor is independent.");
		PhantomAssertions.assertFalse(guard.blocked(new Probe(site, point(400, 200, 0), target), 2), "Physical movement reopens search.");
		PhantomAssertions.assertFalse(guard.blocked(new Probe(site, new PhantomNavigationPoint(200, 200, 0, 1),
			new PhantomNavigationPoint(400, 400, 288, 1)), 2), "Instance isolation.");
		for (var changed : List.of(new Site(24, 100, 200, 1, 1, 1), new Site(32, 101, 200, 1, 1, 1),
			new Site(32, 100, 201, 1, 1, 1), new Site(32, 100, 200, 2, 1, 1), new Site(32, 100, 200, 1, 2, 1),
			new Site(32, 100, 200, 1, 1, 2)))
		{
			PhantomAssertions.assertFalse(guard.blocked(new Probe(changed, origin, target), 2), "Other native or topology context.");
		}
		traversals.incrementAndGet();
		PhantomAssertions.assertFalse(guard.blocked(failure, 2), "New native traversal evidence reopens search.");
		guard.failed(failure, LimitKind.SEARCH_LIMIT, 32, 3, "fresh validation");
		PhantomAssertions.assertTrue(guard.blocked(failure, 4), "A new refusal invalidates the preceding decision.");
		PhantomAssertions.assertFalse(guard.blocked(failure, 121_000_000_003L), "Time reopens search without a new goal.");
		PhantomAssertions.assertFalse(guard.hasFailures(site.profileId(), 121_000_000_003L), "Expired evidence leaves the hot path.");
		System.out.println("PASS farm-route expiry and spatial isolation");
	}

	private static void stationaryPulses()
	{
		final var cases = List.of(
			new Probe(new Site(9, 268485111, 353236957052300L, 1, 1, 1), point(117962, -173188, -429), point(119336, -180368, -2368)),
			new Probe(new Site(17, 268486037, 353233254691100L, 1, 1, 1), point(121683, -181115, -1888), point(119336, -180368, -2368)),
			new Probe(new Site(24, 268486107, 353228855963000L, 1, 1, 1), point(-90710, 246545, -3728), point(-90610, 248293, -3568)),
			new Probe(new Site(32, 268486156, 353230363273800L, 1, 1, 1), point(113965, -165718, 40), point(116056, -175091, -1016)));
		final var geometryChecks = new AtomicInteger();
		final var guard = new PhantomFarmRouteBackoff(32, _ -> 0, (_, _) -> { geometryChecks.incrementAndGet(); return true; });
		for (var failure : cases)
		{
			final int before = geometryChecks.get();
			final boolean limited = failure.site().profileId() == 9 || failure.site().profileId() == 32;
			guard.failed(failure, limited ? LimitKind.SEARCH_LIMIT : LimitKind.NO_PATH_WITHIN_SEARCH,
				limited ? 32 : failure.site().profileId() == 17 ? 22 : 8, 1, "stationary profile=" + failure.site().profileId());
			final var to = failure.destination();
			final var sameFloor = new Probe(failure.site(), failure.origin(), point(to.x(), to.y(), to.z() + 16));
			for (int tick = 1; tick <= 180; tick++)
			{
				PhantomAssertions.assertTrue(guard.blocked(sameFloor, tick * 166_666_667L), "Every equivalent attempt stays held.");
			}
			System.out.println("profile=" + failure.site().profileId() + " geometryChecks=" + (geometryChecks.get() - before) + "/180");
		}
		PhantomAssertions.assertEquals(4, geometryChecks.get(), "Each profile should compare the unchanged surface once across 180 pulses.");
	}

	private static void compactCounters()
	{
		final var logger = Logger.getLogger(PhantomVisibleFarmTravel.class.getName());
		final var messages = new java.util.ArrayList<String>();
		final var observer = new Handler()
		{
			@Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
			@Override public void flush() { }
			@Override public void close() { }
		};
		final boolean forward = logger.getUseParentHandlers();
		logger.setUseParentHandlers(false);
		logger.addHandler(observer);
		try
		{
			final var guard = new PhantomFarmRouteBackoff(32, _ -> 0, (_, _) -> true);
			final var probe = new Probe(new Site(17, 100, 200, 1, 1, 1), point(100, 100, 0), point(200, 200, 0));
			final var other = new Probe(probe.site(), probe.origin(), point(-200, -200, 0));
			guard.failed(probe, LimitKind.NO_PATH_WITHIN_SEARCH, 8, 1, "Terminal farm route proof=BudgetProof[queries=8]");
			PhantomAssertions.assertFalse(guard.blocked(other, 2), "An independent destination is initially eligible.");
			guard.failed(other, LimitKind.SEARCH_LIMIT, 32, 3, "Terminal farm route proof=BudgetProof[queries=32]");
			for (int tick = 1; tick <= 36; tick++)
			{
				PhantomAssertions.assertTrue(guard.blocked(tick % 2 == 0 ? probe : other, 1 + tick * 1_000_000_000L),
					"A new refusal invalidates a cached eligible destination.");
			}
			PhantomAssertions.assertEquals(3, messages.size(), "Two real first refusals and one profile aggregate.");
			PhantomAssertions.assertTrue(messages.getFirst().contains("BudgetProof"), "First refusal keeps full proof.");
			final var summary = messages.getLast();
			PhantomAssertions.assertFalse(summary.contains("BudgetProof"), "Aggregate must not duplicate the proof.");
			PhantomAssertions.assertTrue(summary.contains("failedPathSearches=2") && summary.contains("pathfindingQueries=40") && summary.contains("backoffChecks=3")
				&& summary.contains("skippedChecks=28"), "Aggregate must count searches, real evaluations and memo hits separately.");
		}
		finally
		{
			logger.removeHandler(observer);
			logger.setUseParentHandlers(forward);
		}
	}

	private static PhantomNavigationPoint point(int x, int y, int z) { return new PhantomNavigationPoint(x, y, z, 0); }
}
