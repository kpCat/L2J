/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import org.l2jmobius.gameserver.qol.Economy070Pricing;

/** DB-free applied quote contract; native money/stock tests live in the separate OWN070 lane. */
public final class PhantomEconomy070Contracts
{
	public static void main(String[] args) throws Exception
	{
		final Path input = Path.of(args[0]);
		final var pricing = Economy070Pricing.load(input);
		check(pricing.price("premium.day") == 1_000_000L, "x1 premium fee");
		check(pricing.price("mana") == 1_000L, "x1 mana fee");
		check(pricing.quotes().stream().filter(q -> q.key().startsWith("epic.")).count() == 12, "12 source-backed epic quotes");
		check(pricing.quotes().stream().filter(q -> q.key().startsWith("epic.")).allMatch(q -> q.price() > 0 && java.util.Set.of(14, 30, 60).contains(q.restockDays())), "finite stock policies");
		final Path temp = Files.createTempDirectory("economy070-quote-contract-");
		try
		{
			Files.copy(input, temp.resolve("pricing070.tsv"));
			Files.writeString(temp.resolve("pricing070.sha256"), "0".repeat(64) + "\n");
			try { Economy070Pricing.load(temp.resolve("pricing070.tsv")); throw new AssertionError("bad hash accepted"); }
			catch (IllegalArgumentException expected) { }
		}
		finally { Files.deleteIfExists(temp.resolve("pricing070.tsv")); Files.deleteIfExists(temp.resolve("pricing070.sha256")); Files.delete(temp); }
		System.out.println("ECONOMY070_QUOTE_CONTRACTS_PASS");
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
