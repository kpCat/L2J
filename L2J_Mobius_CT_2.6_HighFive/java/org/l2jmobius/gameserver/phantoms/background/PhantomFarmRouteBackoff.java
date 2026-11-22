/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
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
	private static final int ORIGIN_RADIUS = 96;
	private static final int DESTINATION_RADIUS = 320;
	private static final int FLOOR_TOLERANCE = 32;

	record Site(long profileId, int objectId, long epoch, long topology, long zones) { }
	record Probe(Site site, PhantomNavigationPoint origin, PhantomNavigationPoint destination) { }

	private final int _maximumProfiles;
	private final ToIntFunction<Probe> _provenTraversals;
	private final BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> _sameSurface;
	private final ConcurrentHashMap<Long, List<Failure>> _failures = new ConcurrentHashMap<>();

	PhantomFarmRouteBackoff(int maximumProfiles, ToIntFunction<Probe> provenTraversals, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> sameSurface)
	{
		_maximumProfiles = maximumProfiles;
		_provenTraversals = provenTraversals;
		_sameSurface = sameSurface;
	}

	static boolean indeterminate(LimitKind kind)
	{
		return kind == LimitKind.NO_PATH_WITHIN_SEARCH || kind == LimitKind.SEARCH_LIMIT;
	}

	boolean hasFailures(long profileId) { return _failures.containsKey(profileId); }

	void clear(long profileId) { _failures.remove(profileId); }

	boolean blocked(Probe probe, long now) { return blocked(probe, now, false); }

	boolean blocked(Probe probe, long now, boolean suppressingSearch)
	{
		final var failures = _failures.get(probe.site().profileId());
		if (failures == null) { return false; }
		synchronized (failures)
		{
			failures.removeIf(failure -> now - failure.recorded >= RETRY_NANOS);
			boolean blocked = false;
			for (var failure : failures)
			{
				if (!failure.probe.site().equals(probe.site()) || !equivalent(failure.probe, probe)
					|| _provenTraversals.applyAsInt(failure.probe) > failure.provenTraversals) { continue; }
				blocked = true;
				if (suppressingSearch)
				{
					failure.suppressed++;
					failure.summarize(now);
				}
			}
			return blocked;
		}
	}

	void failed(Probe probe, LimitKind kind, long now, String diagnostic)
	{
		if (!indeterminate(kind)) { return; }
		final long profileId = probe.site().profileId();
		final List<Failure> failures;
		synchronized (_failures)
		{
			if (!_failures.containsKey(profileId) && _failures.size() >= _maximumProfiles)
			{
				final var keys = _failures.keySet().iterator();
				if (keys.hasNext()) { _failures.remove(keys.next()); }
			}
			failures = _failures.computeIfAbsent(profileId, _ -> new ArrayList<>());
		}
		synchronized (failures)
		{
			failures.removeIf(failure -> !failure.probe.site().equals(probe.site()) || now - failure.recorded >= RETRY_NANOS);
			for (var failure : failures)
			{
				if (failure.kind == kind && equivalent(failure.probe, probe)
					&& _provenTraversals.applyAsInt(failure.probe) <= failure.provenTraversals)
				{
					failure.repeats++;
					failure.summarize(now);
					return;
				}
			}
			if (failures.size() >= MAX_ROUTES_PER_PROFILE) { failures.removeFirst(); }
			failures.add(new Failure(probe, kind, now, _provenTraversals.applyAsInt(probe), diagnostic));
			LOGGER.info(diagnostic + ";repeats=0;suppressed=0;nextRetryMs=" + RETRY_NANOS / 1_000_000L);
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

	private static final class Failure
	{
		final Probe probe;
		final LimitKind kind;
		final long recorded;
		final int provenTraversals;
		final String diagnostic;
		long lastSummary;
		int repeats;
		int suppressed;

		Failure(Probe probe, LimitKind kind, long recorded, int provenTraversals, String diagnostic)
		{
			this.probe = probe;
			this.kind = kind;
			this.recorded = recorded;
			this.provenTraversals = provenTraversals;
			this.diagnostic = diagnostic;
			lastSummary = recorded;
		}

		void summarize(long now)
		{
			if (now - lastSummary < SUMMARY_NANOS) { return; }
			lastSummary = now;
			LOGGER.info(diagnostic + ";summary=true;repeats=" + repeats + ";suppressed=" + suppressed
				+ ";nextRetryMs=" + (RETRY_NANOS - (now - recorded)) / 1_000_000L);
		}
	}
}
