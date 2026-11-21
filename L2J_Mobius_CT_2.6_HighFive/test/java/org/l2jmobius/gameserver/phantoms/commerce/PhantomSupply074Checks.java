/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.List;
import java.util.stream.LongStream;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** The travel search already tried the full destination before switching to local checkpoints. */
public final class PhantomSupply074Checks
{
	public static void main(String[] args)
	{
		final var calls = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.function.Supplier<java.util.Optional<String>> gear = () -> { calls.incrementAndGet(); return java.util.Optional.of("new.equipment"); };
		PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.optionalPreparation(true, gear).isEmpty(), "An accepted physical rendezvous started another optional equipment journey.");
		PhantomAssertions.assertEquals(0, calls.get(), "Deferred equipment still invoked its native proposal producer.");
		PhantomAssertions.assertEquals(java.util.Optional.of("new.equipment"), L2jPhantomSupplyJourney.optionalPreparation(false, gear), "Stale or absent assembly suppressed ordinary equipment progression.");
		PhantomAssertions.assertEquals(1, calls.get(), "Ordinary proposal producer was duplicated.");
		PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.inventoryPressure(false, 80, 0), "Critical slot pressure was deferred as optional.");
		PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.inventoryPressure(false, 0, 80), "Critical weight pressure was deferred as optional.");
		PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.inventoryPressure(true, 66, 0), "Accepted inventory pressure lost its existing hysteresis.");
		PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.inventoryPressure(true, 65, 65), "Relieved inventory retained a mandatory maintenance fence.");
		PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.optionalPotionStockAllowed(true, false, false), "An optional potion trip displaced an assembling or replanning Party member.");
		PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.optionalPotionStockAllowed(false, true, false), "Optional potion stock had priority over ammunition.");
		PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.optionalPotionStockAllowed(false, false, true), "Optional potion stock had priority over shots.");
		PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.optionalPotionStockAllowed(false, false, false), "Ordinary optional potion stock was suppressed after release.");
		PhantomAssertions.assertEquals(4L, L2jPhantomSupplyJourney.usableShotStock(LongStream.of(5, 1), 2), "Residues from ordinary and blessed Spiritshots falsely met the three-hit low-water mark.");
		PhantomAssertions.assertEquals(6L, L2jPhantomSupplyJourney.usableShotStock(LongStream.of(4, 2), 2), "Enough complete charges across compatible stacks caused a redundant purchase journey.");
		PhantomAssertions.assertEquals(0L, L2jPhantomSupplyJourney.usableShotStock(LongStream.of(1, 1), 2), "Two partial stacks enabled a shot neither native handler can use.");
		System.out.println("SUPPLY131_WHOLE_NATIVE_CHARGES_PASS");
		System.out.println("SUPPLY130_OPTIONAL_PREPARATION_PASS;EXACT_ASSEMBLY=DEFERRED;PRODUCER_CALLS=0;NO_ASSEMBLY=ORDINARY;GAMEPLAY_WRITES=0");
		final var destination = new PhantomNavigationPoint(-61717, 75401, -3352, 0);
		final var checkpoint = new PhantomNavigationPoint(-63000, 76500, -3280, 0);
		final var points = List.of(destination, checkpoint);
		PhantomAssertions.assertEquals(1, L2jPhantomSupplyJourney.nextLocalCheckpoint(-1, points, destination), "A failed full search was replayed at identical checkpoint instead of a smaller native leg.");
		PhantomAssertions.assertEquals(-1, L2jPhantomSupplyJourney.nextLocalCheckpoint(1, points, checkpoint), "Exhausted checkpoints repeated the same failure.");
		PhantomAssertions.assertEquals(-1, L2jPhantomSupplyJourney.nextLocalCheckpoint(-1, List.of(destination), destination), "Only failed destination should defer or choose another source, not spin.");
		System.out.println("SUPPLY074_CHECKPOINT_PASS; failed full destination skipped; finite local alternatives; DB_WRITES=0");
	}
}
