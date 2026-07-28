/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.buylist.BuyListHolder;
import org.l2jmobius.gameserver.model.buylist.Product;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.skill.holders.SkillHolder;
import org.l2jmobius.gameserver.model.teleporter.TeleportHolder;
import org.l2jmobius.gameserver.model.teleporter.TeleportLocation;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.BuyOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.ItemAmount;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.MultisellFlags;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.MultisellOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyFact;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.TeleportRoute;

/**
 * Builds the immutable catalog once from the current High Five datapack. Loader
 * APIs remain the authority for item, NPC, buy product and teleporter identity.
 */
public final class PhantomCommerceCatalogLoader
{
	private final Path _datapackRoot;

	public PhantomCommerceCatalogLoader(Path datapackRoot)
	{
		_datapackRoot = Objects.requireNonNull(datapackRoot).toAbsolutePath().normalize();
	}

	public LoadResult load()
	{
		try
		{
			final List<BuyOffer> buyOffers = loadBuyOffers(_datapackRoot.resolve("data/buylists"));
			final List<MultisellOffer> multisellOffers = loadMultisellOffers(_datapackRoot.resolve("data/multisell"));
			final List<TeleportRoute> teleportRoutes = loadTeleportRoutes(_datapackRoot.resolve("data/teleporters"));
			final List<SupplyFact> supplies = loadSupplies(_datapackRoot.resolve("data/stats/items"), _datapackRoot.resolve("data/stats/skills"));
			final PhantomCommerceCatalog catalog = new PhantomCommerceCatalog(buyOffers, multisellOffers, teleportRoutes, supplies);
			return new LoadResult(catalog, selectFixtures(catalog));
		}
		catch (IOException | ParserConfigurationException | SAXException e)
		{
			throw new IllegalStateException("Could not build Phantom commerce catalog.", e);
		}
	}

	private List<BuyOffer> loadBuyOffers(Path directory) throws IOException, ParserConfigurationException, SAXException
	{
		final List<BuyOffer> result = new ArrayList<>();
		for (Path file : xmlFiles(directory, false))
		{
			final int listId = numericFileId(file);
			final BuyListHolder loadedList = BuyListData.getInstance().getBuyList(listId);
			if (loadedList == null)
			{
				continue;
			}
			final Element root = document(file).getDocumentElement();
			final Set<Integer> npcIds = validatedNpcIds(childTextIds(root, "npcs", "npc"));
			for (Element item : children(root, "item"))
			{
				final int itemId = positiveInt(item, "id");
				final Product product = loadedList.getProductByItemId(itemId);
				if ((product == null) || (ItemData.getInstance().getTemplate(itemId) == null))
				{
					continue;
				}
				result.add(new BuyOffer(listId, itemId, npcIds, product.getPrice(), product.hasLimitedStock(), source(file)));
			}
		}
		return result;
	}

	private List<MultisellOffer> loadMultisellOffers(Path directory) throws IOException, ParserConfigurationException, SAXException
	{
		final List<MultisellOffer> result = new ArrayList<>();
		for (Path file : xmlFiles(directory, false))
		{
			final int listId = numericFileId(file);
			final Element root = document(file).getDocumentElement();
			final Set<Integer> npcIds = validatedNpcIds(childTextIds(root, "npcs", "npc"));
			final MultisellFlags flags = new MultisellFlags(booleanAttribute(root, "applyTaxes"), booleanAttribute(root, "maintainEnchantment"), doubleAttribute(root, "useRate", 1));
			int entryId = 0;
			for (Element item : children(root, "item"))
			{
				entryId++;
				final List<ItemAmount> ingredients = itemAmounts(item, "ingredient");
				final List<ItemAmount> products = itemAmounts(item, "production");
				if (products.isEmpty() || !validItemAmounts(ingredients) || !validItemAmounts(products))
				{
					continue;
				}
				result.add(new MultisellOffer(listId, entryId, npcIds, ingredients, products, flags, source(file)));
			}
		}
		return result;
	}

	private List<TeleportRoute> loadTeleportRoutes(Path directory) throws IOException, ParserConfigurationException, SAXException
	{
		final List<TeleportRoute> result = new ArrayList<>();
		for (Path file : xmlFiles(directory, true))
		{
			final Element root = document(file).getDocumentElement();
			for (Element npcElement : children(root, "npc"))
			{
				final Set<Integer> npcIds = new TreeSet<>();
				npcIds.add(positiveInt(npcElement, "id"));
				for (Element aliases : children(npcElement, "npcs"))
				{
					for (Element alias : children(aliases, "npc"))
					{
						npcIds.add(positiveInt(alias, "id"));
					}
				}
				for (Element teleport : children(npcElement, "teleport"))
				{
					final TeleportType type = TeleportType.valueOf(requiredAttribute(teleport, "type"));
					final String listName = attribute(teleport, "name").orElse(type.name());
					for (int npcId : validatedNpcIds(npcIds))
					{
						final TeleportHolder loadedHolder = TeleporterData.getInstance().getHolder(npcId, listName);
						if ((loadedHolder == null) || (loadedHolder.getType() != type))
						{
							continue;
						}
						int ordinal = 0;
						for (Element location : children(teleport, "location"))
						{
							if (ordinal >= loadedHolder.getLocations().size())
							{
								break;
							}
							final TeleportLocation loaded = loadedHolder.getLocations().get(ordinal);
							final int x = requiredInt(location, "x");
							final int y = requiredInt(location, "y");
							final int z = requiredInt(location, "z");
							final int heading = intAttribute(location, "heading", 0);
							final int instanceId = intAttribute(location, "instanceId", 0);
							final int feeItemId = intAttribute(location, "feeId", 57);
							final long feeCount = longAttribute(location, "feeCount", 0);
							final Set<Integer> castleIds = semicolonIds(attribute(location, "castleId").orElse(""));
							if ((loaded.getX() == x) && (loaded.getY() == y) && (loaded.getZ() == z) && (loaded.getFeeId() == feeItemId) && (loaded.getFeeCount() == feeCount))
							{
								result.add(new TeleportRoute(npcId, listName, type, ordinal, new Location(x, y, z, heading, instanceId), feeItemId, feeCount, castleIds, source(file)));
							}
							ordinal++;
						}
					}
				}
			}
		}
		return result;
	}

	private List<SupplyFact> loadSupplies(Path itemDirectory, Path skillDirectory) throws IOException, ParserConfigurationException, SAXException
	{
		final Map<Integer, RawSupply> rawByItem = new HashMap<>();
		final Set<Integer> boundSkillIds = new HashSet<>();
		for (Path file : xmlFiles(itemDirectory, false))
		{
			final Element root = document(file).getDocumentElement();
			for (Element item : children(root, "item"))
			{
				final int itemId = positiveInt(item, "id");
				final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
				if (template == null)
				{
					continue;
				}
				final Map<String, String> settings = settings(item);
				final Set<Integer> skills = new TreeSet<>();
				final SkillHolder[] itemSkills = template.getSkills();
				if (itemSkills != null)
				{
					for (SkillHolder skill : itemSkills)
					{
						skills.add(skill.getSkillId());
					}
				}
				boundSkillIds.addAll(skills);
				final EnumSet<SupplyKind> kinds = mechanicalKinds(requiredAttribute(item, "type"), settings);
				if (!kinds.isEmpty() || !skills.isEmpty())
				{
					rawByItem.put(itemId, new RawSupply(itemId, kinds, skills, source(file)));
				}
			}
		}

		final Map<Integer, EnumSet<SupplyKind>> skillKinds = loadSkillKinds(skillDirectory, boundSkillIds);
		final List<SupplyFact> result = new ArrayList<>();
		for (RawSupply raw : rawByItem.values())
		{
			final EnumSet<SupplyKind> kinds = raw.kinds().clone();
			for (int skillId : raw.boundSkills())
			{
				kinds.addAll(skillKinds.getOrDefault(skillId, EnumSet.noneOf(SupplyKind.class)));
			}
			if (kinds.isEmpty())
			{
				continue;
			}
			final ItemTemplate template = ItemData.getInstance().getTemplate(raw.itemId());
			result.add(new SupplyFact(raw.itemId(), kinds, raw.boundSkills(), template.getReuseDelay(), template.isOlyRestrictedItem(), template.getWeight(), template.isStackable(), raw.source()));
		}
		return result;
	}

	private Map<Integer, EnumSet<SupplyKind>> loadSkillKinds(Path directory, Set<Integer> relevantSkillIds) throws IOException, ParserConfigurationException, SAXException
	{
		final Map<Integer, EnumSet<SupplyKind>> result = new HashMap<>();
		for (Path file : xmlFiles(directory, false))
		{
			final Element root = document(file).getDocumentElement();
			for (Element skill : children(root, "skill"))
			{
				final int skillId = positiveInt(skill, "id");
				if (!relevantSkillIds.contains(skillId))
				{
					continue;
				}
				final EnumSet<SupplyKind> kinds = EnumSet.noneOf(SupplyKind.class);
				for (Element effects : children(skill, "effects"))
				{
					for (Element effect : children(effects, "effect"))
					{
						final String name = attribute(effect, "name").orElse("");
						if ("CpHeal".equals(name))
						{
							kinds.add(SupplyKind.CP_RESTORE);
						}
						else if ("HpHeal".equals(name) || "Heal".equals(name))
						{
							kinds.add(SupplyKind.HP_RESTORE);
						}
						else if ("MpHeal".equals(name) || "ManaHeal".equals(name))
						{
							kinds.add(SupplyKind.MP_RESTORE);
						}
					}
				}
				if (!kinds.isEmpty())
				{
					result.put(skillId, kinds);
				}
			}
		}
		return result;
	}

	private static EnumSet<SupplyKind> mechanicalKinds(String itemClass, Map<String, String> settings)
	{
		final EnumSet<SupplyKind> result = EnumSet.noneOf(SupplyKind.class);
		final String itemType = settings.getOrDefault("etcitem_type", "").toUpperCase(Locale.ROOT);
		final String handler = settings.getOrDefault("handler", "");
		if (itemType.contains("SHOT") || handler.contains("SoulShot") || handler.contains("SpiritShot"))
		{
			result.add(SupplyKind.SHOT);
		}
		if ("PetFood".equals(handler))
		{
			result.add(SupplyKind.PET_FOOD);
		}
		if (handler.contains("Beast") || handler.contains("Summon") || "PET_COLLAR".equals(itemType))
		{
			result.add(SupplyKind.SUMMON_RESOURCE);
		}
		if (!"EtcItem".equals(itemClass) && result.isEmpty())
		{
			return EnumSet.noneOf(SupplyKind.class);
		}
		return result;
	}

	private CommerceFixtures selectFixtures(PhantomCommerceCatalog catalog)
	{
		final BuyOffer buy = catalog.buyOffers().stream().filter(offer -> !offer.limitedStock() && (offer.price() > 0) && isStackableSupply(catalog, offer.itemId()) && hasNpcType(offer.npcIds(), "Merchant")).findFirst().orElseGet(() -> catalog.buyOffers().stream().filter(offer -> !offer.limitedStock() && (offer.price() > 0) && isStackableSupply(catalog, offer.itemId())).findFirst().orElse(null));
		final TeleportRoute teleport = catalog.teleportRoutes().stream().filter(route -> (route.type() == TeleportType.NORMAL) && (route.destination().getInstanceId() == 0) && hasNpcType(Set.of(route.npcId()), "Teleporter")).findFirst().orElse(null);
		return new CommerceFixtures(buy, buy == null ? 0 : buy.itemId(), teleport, catalog.findBuyOffers(5591, 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values(), catalog.findBuyOffers(5592, 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values(), catalog.findMultisellOffers(5591, 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values(), catalog.findMultisellOffers(5592, 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values());
	}

	private static boolean isStackableSupply(PhantomCommerceCatalog catalog, int itemId)
	{
		final SupplyFact supply = catalog.findSupply(itemId);
		return (supply != null) && supply.stackable();
	}

	private static boolean hasNpcType(Set<Integer> npcIds, String expectedType)
	{
		for (int npcId : npcIds)
		{
			final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
			if ((template != null) && expectedType.equals(template.getType()))
			{
				return true;
			}
		}
		return false;
	}

	private Set<Integer> validatedNpcIds(Set<Integer> npcIds)
	{
		final Set<Integer> result = new TreeSet<>();
		for (int npcId : npcIds)
		{
			if (NpcData.getInstance().getTemplate(npcId) != null)
			{
				result.add(npcId);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static boolean validItemAmounts(List<ItemAmount> amounts)
	{
		for (ItemAmount amount : amounts)
		{
			if ((amount.itemId() > 0) && (ItemData.getInstance().getTemplate(amount.itemId()) == null))
			{
				return false;
			}
		}
		return true;
	}

	private static List<ItemAmount> itemAmounts(Element parent, String name)
	{
		final List<ItemAmount> result = new ArrayList<>();
		for (Element child : children(parent, name))
		{
			result.add(new ItemAmount(requiredInt(child, "id"), requiredLong(child, "count")));
		}
		return List.copyOf(result);
	}

	private Map<String, String> settings(Element item)
	{
		final Map<String, String> result = new HashMap<>();
		for (Element set : children(item, "set"))
		{
			result.put(requiredAttribute(set, "name"), requiredAttribute(set, "val"));
		}
		return Map.copyOf(result);
	}

	private Document document(Path file) throws ParserConfigurationException, IOException, SAXException
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		final DocumentBuilder builder = factory.newDocumentBuilder();
		try (InputStream input = Files.newInputStream(file))
		{
			return builder.parse(input);
		}
	}

	private static List<Path> xmlFiles(Path directory, boolean recursive) throws IOException
	{
		if (!Files.isDirectory(directory))
		{
			throw new IOException("Required commerce data directory does not exist: " + directory);
		}
		try (var stream = recursive ? Files.walk(directory) : Files.list(directory))
		{
			return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().matches("\\d+(?:-\\d+)?\\.xml")).sorted().toList();
		}
	}

	private String source(Path file)
	{
		return _datapackRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	private static int numericFileId(Path file)
	{
		final String name = file.getFileName().toString();
		return Integer.parseInt(name.substring(0, name.length() - 4));
	}

	private static List<Element> children(Element parent, String name)
	{
		final List<Element> result = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if ((child instanceof Element element) && name.equals(element.getTagName()))
			{
				result.add(element);
			}
		}
		return result;
	}

	private static Set<Integer> childTextIds(Element root, String containerName, String itemName)
	{
		final Set<Integer> result = new TreeSet<>();
		for (Element container : children(root, containerName))
		{
			for (Element item : children(container, itemName))
			{
				result.add(Integer.parseInt(item.getTextContent().trim()));
			}
		}
		return result;
	}

	private static Set<Integer> semicolonIds(String value)
	{
		if (value.isBlank())
		{
			return Set.of();
		}
		final Set<Integer> result = new TreeSet<>();
		for (String token : value.split(";"))
		{
			final int id = Integer.parseInt(token);
			if (id <= 0)
			{
				throw new IllegalArgumentException("Castle ID must be positive.");
			}
			result.add(id);
		}
		return result;
	}

	private static String requiredAttribute(Element element, String name)
	{
		return attribute(element, name).orElseThrow(() -> new IllegalArgumentException("Missing " + name + " on " + element.getTagName() + "."));
	}

	private static Optional<String> attribute(Element element, String name)
	{
		final String value = element.getAttribute(name);
		return value.isEmpty() ? Optional.empty() : Optional.of(value);
	}

	private static int positiveInt(Element element, String name)
	{
		final int value = requiredInt(element, name);
		if (value <= 0)
		{
			throw new IllegalArgumentException(name + " must be positive.");
		}
		return value;
	}

	private static int requiredInt(Element element, String name)
	{
		return Integer.parseInt(requiredAttribute(element, name));
	}

	private static long requiredLong(Element element, String name)
	{
		return Long.parseLong(requiredAttribute(element, name));
	}

	private static int intAttribute(Element element, String name, int defaultValue)
	{
		return attribute(element, name).map(Integer::parseInt).orElse(defaultValue);
	}

	private static long longAttribute(Element element, String name, long defaultValue)
	{
		return attribute(element, name).map(Long::parseLong).orElse(defaultValue);
	}

	private static double doubleAttribute(Element element, String name, double defaultValue)
	{
		return attribute(element, name).map(Double::parseDouble).orElse(defaultValue);
	}

	private static boolean booleanAttribute(Element element, String name)
	{
		return attribute(element, name).map(Boolean::parseBoolean).orElse(false);
	}

	private record RawSupply(int itemId, EnumSet<SupplyKind> kinds, Set<Integer> boundSkills, String source)
	{
	}

	public record CommerceFixtures(BuyOffer buy, int sellItemId, TeleportRoute teleport, List<BuyOffer> cp5591Buy, List<BuyOffer> cp5592Buy, List<MultisellOffer> cp5591Multisell, List<MultisellOffer> cp5592Multisell)
	{
		public CommerceFixtures
		{
			cp5591Buy = List.copyOf(cp5591Buy);
			cp5592Buy = List.copyOf(cp5592Buy);
			cp5591Multisell = List.copyOf(cp5591Multisell);
			cp5592Multisell = List.copyOf(cp5592Multisell);
		}
	}

	public record LoadResult(PhantomCommerceCatalog catalog, CommerceFixtures fixtures)
	{
		public LoadResult
		{
			Objects.requireNonNull(catalog);
			Objects.requireNonNull(fixtures);
		}
	}
}
