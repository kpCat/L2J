/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly applied immutable price plan. Loading never rewrites prices or infers rates. */
public final class Economy070Pricing
{
	public record Quote(String key, int itemId, int currencyId, long price, int restockDays, String name) { }
	private final Map<String, Quote> _quotes;
	private final String _revision;
	private Economy070Pricing(Map<String, Quote> quotes, String revision) { _quotes = Map.copyOf(quotes); _revision = revision; }
	public static Economy070Pricing load(Path file) throws Exception
	{
		if (!Files.isRegularFile(file) || Files.size(file) > 262144) { throw new IllegalArgumentException("Applied070 price file missing/oversized."); }
		final byte[] bytes = Files.readAllBytes(file);
		final String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		if (!revision.equals(Files.readString(file.resolveSibling("pricing070.sha256"), StandardCharsets.UTF_8).trim()))
		{ throw new IllegalArgumentException("Applied070 price checksum differs."); }
		final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R");
		if (!lines[0].equals("key\titemId\tcurrencyId\tprice\trestockDays\tname")) { throw new IllegalArgumentException("Applied070 price columns differ."); }
		final Map<String, Quote> quotes = new LinkedHashMap<>();
		for (int i = 1; i < lines.length; i++)
		{
			final String[] row = lines[i].split("\t", -1);
			if (row.length != 6) { throw new IllegalArgumentException("Applied070 price row differs."); }
			final Quote quote = new Quote(row[0], Integer.parseInt(row[1]), Integer.parseInt(row[2]), Long.parseLong(row[3]), Integer.parseInt(row[4]), row[5]);
			if (!quote.key().matches("[a-z0-9.]+") || quote.itemId() < 0 || quote.currencyId() <= 0 || quote.price() <= 0 || quote.restockDays() < 0 || quote.name().isBlank() || quotes.put(quote.key(), quote) != null)
			{ throw new IllegalArgumentException("Invalid/duplicate applied070 price."); }
		}
		return new Economy070Pricing(quotes, revision);
	}
	public Quote quote(String key) { final Quote result = _quotes.get(key); if (result == null) { throw new IllegalArgumentException("Unpriced070 SKU: " + key); } return result; }
	public long price(String key) { return quote(key).price(); }
	public String revision() { return _revision; }
	public Collection<Quote> quotes() { return _quotes.values(); }
	public static Economy070Pricing getInstance() { return Holder.INSTANCE; }
	private static final class Holder
	{
		private static final Economy070Pricing INSTANCE = read();
		private static Economy070Pricing read()
		{
			try { return load(Path.of("data/economy070/pricing070.tsv")); }
			catch (Exception failure) { throw new IllegalStateException("Applied070 prices unavailable; services disabled.", failure); }
		}
	}
}
