/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayDeque;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;

/** Deterministic boundary, time reserve and interruption controls for a shore crossing. */
public final class PhantomFarmSwimRouteTest
{
	private static final PhantomNavigationPoint ORIGIN = new PhantomNavigationPoint(0, 0, 0, 0);
	private static final PhantomNavigationPoint DESTINATION = new PhantomNavigationPoint(1300, 0, 0, 0);

	public static void main(String[] args)
	{
		final var geometry = strip(true);
		final var crossing = PhantomFarmSwimRoute.plan(ORIGIN, DESTINATION, geometry, 50, 60_000, () -> false);
		check(crossing != null, "a safe wet crossing must be proposed after the dry shore");
		int waterEntry = 0;
		int swim = 0;
		int waterExit = 0;
		var current = ORIGIN;
		for (var leg : crossing.legs())
		{
			check(leg.from().equals(current), "physical segments must be contiguous");
			check(leg.from().distanceTo(leg.to()) <= 700, "stock swimming truncates long moves");
			switch (leg.kind())
			{
				case WALK_TO_SHORE -> check(geometry.water(leg.to()) == 0 && waterEntry == 0, "approach remains dry");
				case ENTER_WATER -> { check(geometry.water(leg.from()) == 0 && geometry.water(leg.to()) > 0, "native entry crosses one boundary"); waterEntry++; }
				case SWIM -> { check(geometry.water(leg.from()) > 0 && geometry.water(leg.to()) > 0 && waterExit == 0, "swim remains in water"); swim++; }
				case EXIT_WATER -> { check(geometry.water(leg.from()) > 0 && geometry.water(leg.to()) == 0, "safe exit ends dry"); waterExit++; }
			}
			current = leg.to();
		}
		check(waterEntry == 1 && swim >= 1 && waterExit == 1, "each boundary is crossed once");
		final int swimming = java.util.stream.IntStream.range(0, crossing.legs().size())
			.filter(i -> crossing.legs().get(i).kind() == PhantomFarmSwimRoute.Kind.SWIM).findFirst().orElseThrow();
		final var nearEntry = new PhantomNavigationPoint(96, 0, 0, 0);
		final var escape = PhantomFarmSwimRoute.egress(crossing, swimming, nearEntry, geometry, 50, 8_000);
		check(escape != null && escape.legs().size() == 1 && geometry.water(escape.legs().getLast().to()) == 0
			&& escape.legs().getLast().to().x() < 32, "an interrupted swimmer near the entry must use the verified near bank");
		check(PhantomFarmSwimRoute.egress(crossing, swimming, nearEntry, geometry, 50, 1000) == null, "an unsafe late exit must remain denied");
		check(PhantomFarmSwimRoute.plan(ORIGIN, DESTINATION, geometry, 50, 18_000, () -> false) == null, "budget reserves enough breath to exit");
		check(PhantomFarmSwimRoute.plan(ORIGIN, DESTINATION, strip(false), 50, 60_000, () -> false) == null, "no known dry exit means no entry");
		check(PhantomFarmSwimRoute.plan(ORIGIN, DESTINATION, strip(true, false), 50, 60_000, () -> false) == null, "a crossing with an unsafe landing must never be offered");
		check(PhantomFarmSwimRoute.plan(ORIGIN, DESTINATION, geometry, 50, 60_000, () -> true) == null, "cancelled search releases no crossing");
		queue();
		System.out.println("PASS shore crossing, breath reserve, blocked exit, typed queue and cancellation");
	}

	private static void queue()
	{
		final var workers = new ArrayDeque<Runnable>();
		final var backend = new PhantomNavigationBackend()
		{
			@Override public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
			{ return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1); }
			@Override public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
			{ return true; }
			@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token)
			{ throw new AssertionError("a SWIM plan cannot fall through to a stock WALK query"); }
		};
		final var service = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, workers::offer, () -> 1, new PhantomMetrics());
		service.start();
		final var first = service.submitFarmSwim(new PhantomNavigationRequest(6, ORIGIN, DESTINATION, 1, 100, 100_000), strip(true), 50, 60_000);
		check(first.status() == PhantomNavigationService.SubmissionStatus.ACCEPTED && service.consume(first.requestId()).isEmpty(), "pending search must stay pending until the worker runs");
		workers.remove().run();
		final var ready = service.consume(first.requestId()).orElseThrow();
		check(ready.status() == PhantomNavigationResult.Status.SWIM_FOUND && ready.swim() != null && ready.route() == null, "the queue must not promote wet proof to dry route");
		check(service.consume(first.requestId()).isEmpty(), "the completion is consumed once");
		final var second = service.submitFarmSwim(new PhantomNavigationRequest(7, ORIGIN, DESTINATION, 1, 100, 100_000), strip(true), 50, 60_000);
		service.cancel(7, second.requestId());
		workers.remove().run();
		final var cancelled = service.consume(second.requestId()).orElseThrow();
		check(cancelled.status() == PhantomNavigationResult.Status.CANCELLED && cancelled.swim() == null, "late worker cannot revive a cancelled crossing");
	}

	private static PhantomFarmSwimRoute.Geometry strip(boolean safeExit)
	{
		return strip(safeExit, true);
	}

	private static PhantomFarmSwimRoute.Geometry strip(boolean safeExit, boolean permitsLanding)
	{
		return new PhantomFarmSwimRoute.Geometry()
		{
			@Override public PhantomNavigationPoint ground(int x, int y, PhantomNavigationPoint surface)
			{
				return Math.abs(y) <= 8 && x >= 0 && x <= 1500 ? new PhantomNavigationPoint(x, y, 0, 0) : null;
			}
			@Override public int water(PhantomNavigationPoint point)
			{
				return point.x() >= 32 && point.x() < 944 ? 123 : 0;
			}
			@Override public boolean line(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return safeExit || water(from) == 0 || water(to) != 0;
			}
			@Override public boolean permitsCrossing(PhantomFarmSwimRoute.Plan plan) { return permitsLanding; }
		};
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw new AssertionError(reason); }
	}
}
