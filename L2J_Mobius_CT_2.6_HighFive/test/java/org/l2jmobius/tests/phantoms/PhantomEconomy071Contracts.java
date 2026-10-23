/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** JDK-only exact authored-price and immutable native alias contracts. */
public final class PhantomEconomy071Contracts
{
	public static void main(String[] args) throws Exception
	{
		final Path game = Path.of(args[0]);
		final Path prices = game.resolve("data/economy070/pricing070.tsv");
		final Map<Integer, Long> expected = Map.ofEntries(Map.entry(6656, 2_000_000_000L), Map.entry(6657, 2_000_000_000L), Map.entry(6658, 600_000_000L), Map.entry(6659, 600_000_000L), Map.entry(6660, 200_000_000L), Map.entry(6661, 200_000_000L), Map.entry(6662, 200_000_000L), Map.entry(8191, 600_000_000L), Map.entry(10314, 2_000_000_000L), Map.entry(16025, 600_000_000L), Map.entry(16026, 2_000_000_000L), Map.entry(21712, 11_111_111_111L));
		int epics = 0;
		for (String line : Files.readAllLines(prices, StandardCharsets.UTF_8))
		{
			final String[] row = line.split("\t");
			if (row[0].startsWith("epic.")) { check(expected.get(Integer.parseInt(row[1])) == Long.parseLong(row[3]), "Exact /100 epic price: " + row[0]); epics++; }
		}
		check(epics == 12, "All twelve epic quotes required.");
		check(hash(prices).equals(Files.readString(prices.resolveSibling("pricing070.sha256")).trim()), "Applied price SHA differs.");
		final var codex = xml(game.resolve("data/multisell/91005.xml"));
		final long[] costs = {11_000_000, 40_000_000, 50_000_000, 100_000_000};
		for (int i = 0; i < costs.length; i++) { check(Long.parseLong(((Element) ((Element) codex.getElementsByTagName("item").item(i)).getElementsByTagName("ingredient").item(0)).getAttribute("count")) == costs[i], "Codex price " + i); }
		for (int i = 0; i < 2; i++)
		{
			final var nativeList = xml(game.resolve("data/multisell/" + (102 + i) + ".xml"));
			final var alias = xml(game.resolve("data/multisell/" + (91071 + i) + ".xml"));
			final var nativeItems = nativeList.getElementsByTagName("item"); final var aliasItems = alias.getElementsByTagName("item");
			check(nativeItems.getLength() == (i == 0 ? 38 : 69) && nativeItems.getLength() == aliasItems.getLength(), "Native offer cardinality.");
			for (int offer = 0; offer < nativeItems.getLength(); offer++) { check(semantic(nativeItems.item(offer)).equals(semantic(aliasItems.item(offer))), "Alias semantic mismatch: " + i + ":" + offer); }
		}
		final var policy = new Properties();
		try (var reader = Files.newBufferedReader(game.resolve("data/economy070/price-policy070.properties"))) { policy.load(reader); }
		check(policy.getProperty("codex.6622").equals("11000000"), "Canonical Codex must retain new price.");
		for (var entry : expected.entrySet()) { check(policy.getProperty("epic.exact." + entry.getKey()).equals(entry.getValue().toString()), "Canonical exact epic " + entry.getKey()); }
		System.out.println("ECONOMY071_CONTRACTS_PASS prices12;codex4;nativeAliases38/69;canonicalExact;sha");
	}
	private static Element xml(Path path) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
	}
	private static String semantic(Node node)
	{
		if (node.getNodeType() != Node.ELEMENT_NODE) { return ""; }
		final var text = new StringBuilder(node.getNodeName());
		final var attributes = new java.util.TreeMap<String, String>();
		for (int i = 0; i < node.getAttributes().getLength(); i++) { final var attr = node.getAttributes().item(i); attributes.put(attr.getNodeName(), attr.getNodeValue()); }
		text.append(attributes);
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) { text.append(semantic(child)); }
		return text.append('/').toString();
	}
	private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
