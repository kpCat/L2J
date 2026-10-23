/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;

/** Drain assertions must never prevent the last native persistence owner from running. No DB/runtime. */
public final class PhantomEarned073Checks
{
	public static void main(String[] args) throws Exception
	{
		final var calls = new ArrayList<String>();
		final var first = new AssertionError("combat drain failed");
		try
		{
			PhantomSupply073JourneyNative.closeAll(List.of(
				() -> { calls.add("materialization.store"); },
				() -> { calls.add("navigation"); throw new java.io.IOException("navigation drain failed"); },
				() -> { calls.add("combat"); throw first; }));
			throw new AssertionError("Drain error was hidden.");
		}
		catch (AssertionError error)
		{
			PhantomAssertions.assertTrue(error == first, "Original native drain assertion was replaced.");
			PhantomAssertions.assertEquals(List.of("combat", "navigation", "materialization.store"), calls, "Drain assertion skipped durable native store cleanup.");
			PhantomAssertions.assertEquals(1, error.getSuppressed().length, "Later cleanup failure was hidden.");
		}
		System.out.println("EARNED073_CLEANUP_PASS; original Error retained; all owners attempted; secondary failure suppressed; DB_WRITES=0");
	}
}
