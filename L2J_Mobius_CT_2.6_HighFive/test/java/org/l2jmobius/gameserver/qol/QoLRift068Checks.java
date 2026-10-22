/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.model.sevensigns.DimensionalRiftRoom;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** DB-free room ownership races and independent fail-closed personal configuration. */
public final class QoLRift068Checks
{
	private QoLRift068Checks() { }

	public static void main(String[] args) throws Exception
	{
		roomOwnership();
		configuration();
		System.out.println("OWN068_RIFT_FOCUSED_PASS cases=2; DB_WRITES=0");
	}

	private static void roomOwnership() throws Exception
	{
		final DimensionalRiftRoom room = new DimensionalRiftRoom((byte) 1, (byte) 1, 0, 1000, 0, 1000, -100, 100, 500, 500, 0, false);
		final Object first = new Object();
		final Object second = new Object();
		final CountDownLatch start = new CountDownLatch(1);
		final AtomicInteger acquired = new AtomicInteger();
		final Thread a = Thread.ofPlatform().unstarted(() -> acquire(room, first, start, acquired));
		final Thread b = Thread.ofPlatform().unstarted(() -> acquire(room, second, start, acquired));
		a.start(); b.start(); start.countDown(); a.join(); b.join();
		PhantomAssertions.assertEquals(1, acquired.get(), "Two sessions acquired the same native room.");
		final Object owner = room.isOwnedBy(first) ? first : second;
		final Object foreign = owner == first ? second : first;
		PhantomAssertions.assertFalse(room.release(foreign), "Foreign release freed the current room.");
		PhantomAssertions.assertTrue(room.release(owner), "Owner could not release its room.");
		PhantomAssertions.assertTrue(room.tryAcquire(foreign), "Released room could not be reused.");
		PhantomAssertions.assertFalse(room.release(owner), "Late old-session release freed a reused room.");
		PhantomAssertions.assertTrue(room.isOwnedBy(foreign), "New room owner was lost.");
		PhantomAssertions.assertTrue(room.release(foreign), "Final room cleanup failed.");
	}

	private static void acquire(DimensionalRiftRoom room, Object owner, CountDownLatch start, AtomicInteger acquired)
	{
		try { start.await(); if (room.tryAcquire(owner)) { acquired.incrementAndGet(); } }
		catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
	}

	private static void configuration() throws Exception
	{
		final Path path = Files.createTempFile("rift068-config-", ".ini");
		try
		{
			for (String value : new String[] {"True", "False", "invalid", ""})
			{
				Files.writeString(path, "EnablePersonalCharacterQoL=True\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nAllowedCharacterIds=123\nAllowedAccounts=\nEnablePersonalSoloInstanceEntry=True\nEnablePersonalSoloRift=" + value + "\n");
				final var settings = PersonalCharacterQoLConfig.read(path);
				PhantomAssertions.assertEquals("True".equals(value), settings.adventureSettings().soloRift(), "Rift switch did not fail closed independently.");
				PhantomAssertions.assertTrue(settings.adventureSettings().soloInstanceEntry(), "Rift child disabled the accepted Kamaloka switch.");
			}
		}
		finally { Files.deleteIfExists(path); }
	}
}
