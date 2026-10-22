/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/** HomeBoard HTML reachability only; dynamic handlers are reported rather than guessed. */
public final class PhantomAltBPriceCatalog
{
	private static final Pattern PAGE = Pattern.compile("_bbstop;([A-Za-z0-9_/-]+\\.html)");
	private static final Pattern MULTISELL = Pattern.compile("_bbs(?:exc)?multisell;([0-9]+),([A-Za-z0-9_/-]+)");
	private static final Pattern SELL = Pattern.compile("_bbssell;([A-Za-z0-9_/-]+)");
	public record Offer(String path, String kind, int ordinal, long oldPrice, Map<Integer, Long> products, String reason)
	{
		public Offer { products = Map.copyOf(products); }
		public boolean supported() { return reason.equals("adena.stock"); }
	}
	private final List<Offer> _offers;
	private final Map<String, String> _hashes;
	private final List<String> _unknown;
	private PhantomAltBPriceCatalog(List<Offer> offers, Map<String, String> hashes, List<String> unknown) { _offers = List.copyOf(offers); _hashes = Map.copyOf(hashes); _unknown = List.copyOf(unknown); }
	public static PhantomAltBPriceCatalog empty() { return new PhantomAltBPriceCatalog(List.of(), Map.of(), List.of("Alt+B source unavailable")); }
	public List<Offer> offers() { return _offers; }
	public Map<String, String> hashes() { return _hashes; }
	public List<String> unknown() { return _unknown; }

	/** Equal conservative allocation bounds every supported bundle's total private bid by its paid Adena. */
	public long acquisitionCeiling(int itemId)
	{
		long minimum = Long.MAX_VALUE;
		for (Offer offer : _offers)
		{
			if (offer.supported() && offer.products().containsKey(itemId))
			{
				final long units = offer.products().values().stream().reduce(0L, Math::addExact);
				minimum = Math.min(minimum, offer.oldPrice() / units);
			}
		}
		return minimum == Long.MAX_VALUE ? -1 : minimum;
	}

	public static PhantomAltBPriceCatalog load(Path gameRoot) throws Exception
	{
		final Path root = gameRoot.toAbsolutePath().normalize().toRealPath();
		final Map<String, String> hashes = new TreeMap<>();
		final Set<String> pages = new TreeSet<>();
		final Set<Integer> multisells = new TreeSet<>();
		final List<String> unknown = new ArrayList<>();
		final ArrayDeque<String> pending = new ArrayDeque<>(List.of("home.html", "navigation.html"));
		boolean sell = false;
		while (!pending.isEmpty())
		{
			final String page = pending.removeFirst();
			if (page.contains("..") || !page.matches("[A-Za-z0-9_/-]+\\.html")) { throw new IllegalArgumentException("Unsafe HomeBoard page."); }
			if (!pages.add(page)) { continue; }
			if (pages.size() > 128) { throw new IllegalArgumentException("HomeBoard reachability budget exceeded."); }
			final String relative = "data/html/CommunityBoard/Custom/" + page;
			final Path file = PhantomEconomyXml.physical(root, root.resolve(relative));
			hashes.put(relative, PhantomEconomyXml.hash(file));
			final String html = Files.readString(file, StandardCharsets.UTF_8);
			var matcher = PAGE.matcher(html); while (matcher.find()) { pending.add(matcher.group(1)); }
			matcher = MULTISELL.matcher(html); while (matcher.find()) { multisells.add(Integer.parseInt(matcher.group(1))); pending.add(matcher.group(2) + ".html"); }
			matcher = SELL.matcher(html); while (matcher.find()) { sell = true; pending.add(matcher.group(1) + ".html"); }
			if (html.contains("_bbsqol")) { unknown.add("dynamic.PersonalPremiumQoLBoard: separate admission/catalog; not repriced"); }
		}
		final List<Offer> offers = new ArrayList<>();
		for (int id : multisells)
		{
			final List<Path> candidates;
			try (var paths = Files.walk(root.resolve("data/multisell"))) { candidates = paths.filter(path -> path.getFileName().toString().equals(id + ".xml")).sorted().toList(); }
			if (candidates.size() != 1) { throw new IllegalArgumentException("Reachable multisell is missing or ambiguous: " + id); }
			final Path file = PhantomEconomyXml.physical(root, candidates.getFirst());
			final String relative = root.relativize(file).toString().replace('\\', '/');
			hashes.put(relative, PhantomEconomyXml.hash(file));
			final Element list = PhantomEconomyXml.read(file);
			boolean customOptions = !PhantomEconomyXml.children(list, "config").isEmpty();
			for (int attribute = 0; attribute < list.getAttributes().getLength(); attribute++)
			{
				final String name = list.getAttributes().item(attribute).getNodeName();
				if (!name.startsWith("xmlns") && !name.equals("xsi:noNamespaceSchemaLocation")) { customOptions = true; }
			}
			final boolean cbAllowed = PhantomEconomyXml.children(list, "npcs").stream().flatMap(npcs -> PhantomEconomyXml.children(npcs, "npc").stream()).anyMatch(npc -> npc.getTextContent().trim().equals("-1"));
			int ordinal = 0;
			for (Element entry : PhantomEconomyXml.children(list, "item"))
			{
				ordinal++;
				final var ingredients = PhantomEconomyXml.children(entry, "ingredient");
				final Map<Integer, Long> products = new TreeMap<>();
				for (Element product : PhantomEconomyXml.children(entry, "production")) { products.merge(Integer.parseInt(product.getAttribute("id")), Long.parseLong(product.getAttribute("count")), Math::addExact); }
				final boolean supported = cbAllowed && !customOptions && !entry.hasAttributes() && ingredients.size() == 1 && "57".equals(ingredients.getFirst().getAttribute("id")) && ingredients.getFirst().getAttributes().getLength() == 2 && !products.isEmpty() && PhantomEconomyXml.children(entry, "production").stream().allMatch(product -> product.getAttributes().getLength() == 2);
				final long price = supported ? Long.parseLong(ingredients.getFirst().getAttribute("count")) : 0;
				if (products.values().stream().anyMatch(count -> count <= 0) || price < 0) { throw new IllegalArgumentException("Invalid reachable quantities."); }
				offers.add(new Offer(relative, "multisell", ordinal, price, products, supported ? "adena.stock" : "UNPRICED:non-Adena or restricted exchange"));
			}
		}
		if (sell)
		{
			final String relative = "data/buylists/0000423.xml";
			final Path file = PhantomEconomyXml.physical(root, root.resolve(relative)); hashes.put(relative, PhantomEconomyXml.hash(file));
			int ordinal = 0;
			for (Element entry : PhantomEconomyXml.children(PhantomEconomyXml.read(file), "item"))
			{
				final boolean supported = entry.getAttributes().getLength() == 2 && entry.hasAttribute("price");
				offers.add(new Offer(relative, "buylist", ++ordinal, supported ? Long.parseLong(entry.getAttribute("price")) : 0, Map.of(Integer.parseInt(entry.getAttribute("id")), 1L), supported ? "adena.stock" : "UNPRICED:limited or implicit buylist price"));
			}
		}
		offers.sort(Comparator.comparing(Offer::path).thenComparingInt(Offer::ordinal));
		return new PhantomAltBPriceCatalog(offers, hashes, unknown.stream().distinct().sorted().toList());
	}
}
