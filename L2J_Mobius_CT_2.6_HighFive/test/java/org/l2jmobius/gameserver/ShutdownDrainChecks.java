package org.l2jmobius.gameserver;

import java.util.ArrayList;
import java.util.List;

/** DB-free checks of the production shutdown sequencing and drain receipt. */
public final class ShutdownDrainChecks
{
	public static void main(String[] args)
	{
		completedDrainClosesOnce();
		incompleteDrainKeepsDependenciesForRetry();
		unconfirmedRetirementCannotPermitExit();
		System.out.println("SHUTDOWN_DRAIN_PASS 3/3 JDBC=0");
	}

	private static void completedDrainClosesOnce()
	{
		final var progress = new Shutdown.DrainProgress();
		final var events = new ArrayList<String>();
		check(!progress.started(), "Fresh server rejected Phantom startup");
		final boolean complete = progress.attempt(
			() ->
			{
				check(progress.started(), "Shutdown did not close Phantom admission before preparation");
				return events.add("prepare");
			},
			() -> events.add("drain") && progress.recordPhantomDrain(true, false),
			() -> events.add("close"));
		check(complete && progress.complete(), "Confirmed drain did not permit shutdown");
		check(progress.attempt(() -> { throw new AssertionError("Already prepared"); }, () -> false,
			() -> { throw new AssertionError("Already closed"); }), "Completed shutdown did not remain complete");
		check(events.equals(List.of("prepare", "drain", "close")), "Confirmed shutdown ran out of order");
	}

	private static void incompleteDrainKeepsDependenciesForRetry()
	{
		final var progress = new Shutdown.DrainProgress();
		final var events = new ArrayList<String>();
		final boolean first = progress.attempt(
			() -> events.add("prepare"),
			() -> events.add("incomplete") && progress.recordPhantomDrain(false, true),
			() -> events.add("close"));
		check(!first && progress.started() && !progress.complete() && events.equals(List.of("prepare", "incomplete")),
			"An incomplete drain closed resources or permitted JVM exit");
		final boolean second = progress.attempt(
			() -> events.add("repeat preparation"),
			() -> events.add("retry") && progress.recordPhantomDrain(true, false),
			() -> events.add("close"));
		check(second && progress.complete() && events.equals(List.of("prepare", "incomplete", "retry", "close")),
			"Retry did not resume after the original preparation");
	}

	private static void unconfirmedRetirementCannotPermitExit()
	{
		final var progress = new Shutdown.DrainProgress();
		final Runnable close = () -> { throw new AssertionError("Unconfirmed STORE closed resources"); };
		check(!progress.attempt(() -> true, () -> progress.recordPhantomDrain(false, false), close),
			"Failed cleanup of a configured Phantom system was treated as a successful STORE");
		check(!progress.attempt(() -> true, progress::phantomDrainConfirmed, close) && !progress.complete(),
			"Missing Phantom instance on retry hid the earlier unconfirmed STORE");
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}
