/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;

/** Revision-independent memory of inconclusive native WALK searches. */
final class PhantomFarmRouteBackoff
{
	private static final Logger LOGGER = Logger.getLogger(PhantomVisibleFarmTravel.class.getName());
	private static final long RETRY_NANOS = 120_000_000_000L;
	private static final long SUMMARY_NANOS = 30_000_000_000L;
	private static final int MAX_ROUTES_PER_PROFILE = 8;
	private static final int MAX_CHECKS_PER_PROFILE = 16;
	private static final int ORIGIN_RADIUS = 96;
	private static final int DESTINATION_RADIUS = 320;
	private static final int FLOOR_TOLERANCE = 32;

	record Site(long profileId, int objectId, long epoch, long topology, long zones, long geodata) { }
	record Probe(Site site, PhantomNavigationPoint origin, PhantomNavigationPoint destination) { }

	private final int _maximumProfiles;
	private final ToLongFunction<Probe> _provenTraversals;
	private final BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> _sameSurface;
	private final ConcurrentHashMap<Long, ProfileFailures> _failures = new ConcurrentHashMap<>();

	PhantomFarmRouteBackoff(int maximumProfiles, ToLongFunction<Probe> provenTraversals, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> sameSurface)
	{
		_maximumProfiles = maximumProfiles;
		_provenTraversals = provenTraversals;
		_sameSurface = sameSurface;
	}

	static boolean indeterminate(LimitKind kind)
	{
		return kind == LimitKind.NO_PATH_WITHIN_SEARCH || kind == LimitKind.SEARCH_LIMIT;
	}

	boolean hasFailures(long profileId, long now)
	{
		final var profile = _failures.get(profileId);
		return profile != null && profile.activeUntil - now > 0;
	}

	void clear(long profileId) { _failures.remove(profileId); }

	boolean blocked(Probe probe, long now)
	{
		final var profile = _failures.get(probe.site().profileId());
		if (profile == null || !profile.site.equals(probe.site()) || profile.activeUntil - now <= 0) { return false; }
		synchronized (profile)
		{
			final var remembered = profile.checks.get(probe);
			if (remembered != null && remembered.until() - now > 0
				&& (remembered.witness() == null || stillUnproven(remembered.witness())))
			{
				profile.skippedChecks++;
				profile.summarize(now);
				return remembered.witness() != null;
			}
			profile.checks.remove(probe);
			profile.backoffChecks++;
			profile.failures.removeIf(failure -> failure.expiresAt() - now <= 0);
			boolean equivalent = false;
			for (var failure : profile.failures)
			{
				if (!equivalent(failure.probe(), probe)) { continue; }
				equivalent = true;
				if (!stillUnproven(failure)) { continue; }
				profile.remember(probe, new Checked(failure, failure.expiresAt()));
				profile.summarize(now);
				return true;
			}
			// No matching failure can start blocking these coordinates until a new refusal arrives.
			if (!equivalent) { profile.remember(probe, new Checked(null, profile.activeUntil)); }
			profile.summarize(now);
			return false;
		}
	}

	private boolean stillUnproven(Failure failure)
	{
		return _provenTraversals.applyAsLong(failure.probe()) <= failure.provenTraversals();
	}

	void failed(Probe probe, LimitKind kind, int nativeQueries, long now, String diagnostic)
	{
		if (!indeterminate(kind)) { return; }
		final long profileId = probe.site().profileId();
		synchronized (_failures)
		{
			if (!_failures.containsKey(profileId) && _failures.size() >= _maximumProfiles)
			{
				_failures.entrySet().removeIf(entry -> entry.getValue().activeUntil - now <= 0);
				if (_failures.size() >= _maximumProfiles)
				{
					final var keys = _failures.keySet().iterator();
					if (keys.hasNext()) { _failures.remove(keys.next()); }
				}
			}
			final var profile = _failures.compute(profileId, (_, previous) -> previous != null && previous.site.equals(probe.site())
				? previous : new ProfileFailures(probe.site(), now));
			// An expired profile can be evicted at capacity. Refresh it before another admission can evict it.
			recordFailure(profile, probe, kind, nativeQueries, now, diagnostic);
		}
	}

	private void recordFailure(ProfileFailures profile, Probe probe, LimitKind kind, int nativeQueries, long now, String diagnostic)
	{
		synchronized (profile)
		{
			profile.failedPathSearches++;
			profile.pathfindingQueries += Math.max(0, nativeQueries);
			profile.failures.removeIf(failure -> failure.expiresAt() - now <= 0);
			for (var failure : profile.failures)
			{
				if (failure.kind() == kind && equivalent(failure.probe(), probe) && stillUnproven(failure))
				{
					profile.summarize(now);
					return;
				}
			}
			if (profile.failures.size() >= MAX_ROUTES_PER_PROFILE) { profile.failures.removeFirst(); }
			profile.activeUntil = now + RETRY_NANOS;
			profile.failures.add(new Failure(probe, kind, profile.activeUntil, _provenTraversals.applyAsLong(probe)));
			profile.checks.clear();
			LOGGER.info(diagnostic + ";nextRetryMs=" + RETRY_NANOS / 1_000_000L);
			profile.summarize(now);
		}
	}

	boolean equivalent(Probe first, Probe next)
	{
		if (!near(first.origin(), next.origin(), ORIGIN_RADIUS)) { return false; }
		final var a = first.destination();
		final var b = next.destination();
		if (!near(a, b, DESTINATION_RADIUS)) { return false; }
		final double ax = (long) a.x() - first.origin().x(), ay = (long) a.y() - first.origin().y();
		final double bx = (long) b.x() - next.origin().x(), by = (long) b.y() - next.origin().y();
		return ax * bx + ay * by >= 0.5 * Math.hypot(ax, ay) * Math.hypot(bx, by);
	}

	private boolean near(PhantomNavigationPoint a, PhantomNavigationPoint b, int radius)
	{
		return a.instanceId() == b.instanceId() && Math.abs((long) a.z() - b.z()) <= FLOOR_TOLERANCE
			&& Math.hypot((long) a.x() - b.x(), (long) a.y() - b.y()) <= radius
			&& (a.z() == b.z() || _sameSurface.test(a, b));
	}

	private record Failure(Probe probe, LimitKind kind, long expiresAt, long provenTraversals) { }

	private record Checked(Failure witness, long until) { }

	private static final class ProfileFailures
	{
		final Site site;
		final List<Failure> failures = new ArrayList<>();
		final LinkedHashMap<Probe, Checked> checks = new LinkedHashMap<>(MAX_CHECKS_PER_PROFILE, 0.75f, true);
		volatile long activeUntil;
		long lastSummary;
		long failedPathSearches;
		long pathfindingQueries;
		long backoffChecks;
		long skippedChecks;

		ProfileFailures(Site site, long now)
		{
			this.site = site;
			activeUntil = now + RETRY_NANOS;
			lastSummary = now;
		}

		void remember(Probe probe, Checked checked)
		{
			checks.put(probe, checked);
			if (checks.size() > MAX_CHECKS_PER_PROFILE) { checks.pollFirstEntry(); }
		}

		void summarize(long now)
		{
			if (now - lastSummary < SUMMARY_NANOS) { return; }
			lastSummary = now;
			long until = activeUntil - now;
			for (var failure : failures)
			{
				final long remaining = failure.expiresAt() - now;
				if (remaining > 0) { until = Math.min(until, remaining); }
			}
			LOGGER.info("Farm route backoff profile=" + site.profileId() + ";object=" + site.objectId() + ";epoch=" + site.epoch()
				+ ";failedPathSearches=" + failedPathSearches + ";pathfindingQueries=" + pathfindingQueries
				+ ";backoffChecks=" + backoffChecks + ";skippedChecks=" + skippedChecks + ";nextRetryMs=" + Math.max(0, until) / 1_000_000L);
			failedPathSearches = 0;
			pathfindingQueries = 0;
			backoffChecks = 0;
			skippedChecks = 0;
		}
	}
}
