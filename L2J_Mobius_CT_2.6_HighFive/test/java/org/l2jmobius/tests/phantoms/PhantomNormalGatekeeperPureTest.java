package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;

/** Pure destination castle checks; no TeleporterData, CastleManager or DB access. */
public final class PhantomNormalGatekeeperPureTest
{
	public static void main(String[] args) throws Exception
	{
		provenanceRejectsBeforeNativeInitialization();
		check(List.of(), PhantomNormalGatekeeperTravel.parseDestinationCastleIds(""));
		check(List.of(9), PhantomNormalGatekeeperTravel.parseDestinationCastleIds("9"));
		check(true, PhantomNormalGatekeeperTravel.nativeCastleIdsMatch(List.of(9), List.of(9)));
		check(false, PhantomNormalGatekeeperTravel.nativeCastleIdsMatch(List.of(9), List.of()));
		check(true, PhantomNormalGatekeeperTravel.destinationCastlesAvailable(List.of(), id -> false));
		check(true, PhantomNormalGatekeeperTravel.destinationCastlesAvailable(List.of(9), id -> id == 9));
		check(false, PhantomNormalGatekeeperTravel.destinationCastlesAvailable(List.of(9), id -> false));
		for (String malformed : List.of("0", "09", "9;9", "9;1", "9;", "a"))
		{
			try
			{
				PhantomNormalGatekeeperTravel.parseDestinationCastleIds(malformed);
				throw new AssertionError("Accepted malformed castle IDs: " + malformed);
			}
			catch (IllegalArgumentException expected)
			{
				// Fail closed.
			}
		}
		System.out.println("NORMAL GK PURE: PASS");
	}

	private static void provenanceRejectsBeforeNativeInitialization() throws Exception
	{
		final Path catalog = Path.of("data/phantoms/travel/high-five-normal-gk.xml");
		final String original = Files.readString(catalog);
		final Path tampered = Files.createTempFile("phantom-gk-provenance", ".xml");
		try
		{
			for (String stale : List.of("881c6be02318523aefaaf726c343004d0991273d6d8ee75f1162e35d62e97fad", "c436039da63f72a93fb6d8eafe7f1b76d2e7fe033302da76be5588d31fa330bd"))
			{
				Files.writeString(tampered, original.replaceFirst("targetedConnectorsSha256=\"[0-9a-f]{64}\"", "targetedConnectorsSha256=\"" + stale + "\""));
				try
				{
					PhantomNormalGatekeeperTravel.load(tampered, null);
					throw new AssertionError("Stale supplement hash was accepted.");
				}
				catch (IllegalArgumentException expected)
				{
					if ((expected.getCause() == null) || !expected.getCause().getMessage().contains("provenance changed"))
					{
						throw new AssertionError("Catalog was rejected for an unrelated reason.", expected);
					}
				}
			}
		}
		finally
		{
			Files.deleteIfExists(tampered);
		}
	}

	private static void check(Object expected, Object actual)
	{
		if (!expected.equals(actual))
		{
			throw new AssertionError("Expected " + expected + " but got " + actual);
		}
	}
}
