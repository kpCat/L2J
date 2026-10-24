/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** The travel search already tried the full destination before switching to local checkpoints. */
public final class PhantomSupply074Checks
{
	public static void main(String[] args)
	{
		final var destination = new PhantomNavigationPoint(-61717, 75401, -3352, 0);
		final var checkpoint = new PhantomNavigationPoint(-63000, 76500, -3280, 0);
		final var points = List.of(destination, checkpoint);
		PhantomAssertions.assertEquals(1, L2jPhantomSupplyJourney.nextLocalCheckpoint(-1, points, destination), "A failed full search was replayed at identical checkpoint instead of a smaller native leg.");
		PhantomAssertions.assertEquals(-1, L2jPhantomSupplyJourney.nextLocalCheckpoint(1, points, checkpoint), "Exhausted checkpoints repeated the same failure.");
		PhantomAssertions.assertEquals(-1, L2jPhantomSupplyJourney.nextLocalCheckpoint(-1, List.of(destination), destination), "Only failed destination should defer or choose another source, not spin.");
		System.out.println("SUPPLY074_CHECKPOINT_PASS; failed full destination skipped; finite local alternatives; DB_WRITES=0");
	}
}
