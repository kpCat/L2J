/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Strict curated catalog for canonically consumed clan progression items.
 */
public final class PersonalProgressionShopService
{
	private static final Logger LOGGER = Logger.getLogger(PersonalProgressionShopService.class.getName());
	private static final int MAXIMUM_MULTI_SELL_BYTES = 64 * 1024;
	public static final int SHOP_LIST_ID = 91002;

	private static final List<ExpectedOffer> EXPECTED_OFFERS = List.of(
		new ExpectedOffer(1419, 1, "Знак Крови"),
		new ExpectedOffer(3874, 1, "Манифест Альянса"),
		new ExpectedOffer(3870, 1, "Печать Устремления"),
		new ExpectedOffer(9910, 150, "Клятва Крови"),
		new ExpectedOffer(9911, 5, "Альянс Крови"));

	private volatile RuntimeState _state = RuntimeState.disabled("Service has not been initialized.");

	private PersonalProgressionShopService()
	{
	}

	public synchronized void initialize()
	{
		try
		{
			_state = build(Path.of("."), true);
			LOGGER.info("Personal progression shop catalog validated with " + _state.offers().size() + " curated offers; shop=" + isShopEnabled() + ".");
		}
		catch (RuntimeException e)
		{
			_state = RuntimeState.disabled("Progression shop catalog failed validation.");
			LOGGER.log(Level.SEVERE, "Personal progression shop failed closed.", e);
		}
	}

	public boolean isShopEnabled()
	{
		return _state.valid() && PersonalPremiumQoLService.getInstance().isShopEnabled();
	}

	public String diagnostic()
	{
		return _state.diagnostic();
	}

	public List<StorefrontOffer> storefrontOffers()
	{
		return _state.offers();
	}

	public static boolean isShopList(int listId)
	{
		return listId == SHOP_LIST_ID;
	}

	static RuntimeState build(Path workingDirectory, boolean requireLoadedMultisell)
	{
		final Path root = workingDirectory.toAbsolutePath().normalize();
		final Path multisellPath = root.resolve("data/multisell/" + SHOP_LIST_ID + ".xml").normalize();
		if (!multisellPath.startsWith(root))
		{
			throw new IllegalArgumentException("Progression multisell resolves outside the runtime root.");
		}
		final List<StorefrontOffer> offers = validateShop(multisellPath);
		if (requireLoadedMultisell && !MultisellData.getInstance().hasList(SHOP_LIST_ID))
		{
			throw new IllegalArgumentException("Native multisell " + SHOP_LIST_ID + " was not loaded.");
		}
		return new RuntimeState(true, offers, "Curated progression catalog validated.");
	}

	static List<StorefrontOffer> validateShop(Path path)
	{
		final Element root = StrictUtf8Xml.parse(path, MAXIMUM_MULTI_SELL_BYTES, "Personal progression multisell");
		requireElement(root, "list", Set.of("xmlns:xsi", "xsi:noNamespaceSchemaLocation"));
		if (!"http://www.w3.org/2001/XMLSchema-instance".equals(root.getAttribute("xmlns:xsi")) || !"../xsd/multisell.xsd".equals(root.getAttribute("xsi:noNamespaceSchemaLocation")))
		{
			throw new IllegalArgumentException("Progression multisell schema declaration is invalid.");
		}

		final List<Element> children = childElements(root);
		if (children.isEmpty() || !"npcs".equals(children.getFirst().getTagName()))
		{
			throw new IllegalArgumentException("Progression multisell must explicitly allow the Community Board actor.");
		}
		final Element npcs = children.getFirst();
		requireElement(npcs, "npcs", Set.of());
		final List<Element> allowedNpcs = childElements(npcs);
		if ((allowedNpcs.size() != 1) || !"npc".equals(allowedNpcs.getFirst().getTagName()))
		{
			throw new IllegalArgumentException("Progression multisell must contain only the Community Board NPC marker.");
		}
		final Element allowedNpc = allowedNpcs.getFirst();
		requireElement(allowedNpc, "npc", Set.of());
		if (!"-1".equals(allowedNpc.getTextContent().trim()))
		{
			throw new IllegalArgumentException("Progression multisell Community Board NPC marker is invalid.");
		}

		final List<Element> entries = children.subList(1, children.size());
		if (entries.size() != EXPECTED_OFFERS.size())
		{
			throw new IllegalArgumentException("Progression multisell must contain exactly the curated offers.");
		}

		final Map<Integer, ExpectedOffer> expectedById = new HashMap<>();
		for (ExpectedOffer expected : EXPECTED_OFFERS)
		{
			expectedById.put(expected.itemId(), expected);
			final ItemTemplate template = ItemData.getInstance().getTemplate(expected.itemId());
			if ((template == null) || !template.isStackable())
			{
				throw new IllegalArgumentException("Curated progression item " + expected.itemId() + " is not a stackable H5 item template.");
			}
		}

		final Set<Integer> seen = new HashSet<>();
		final List<StorefrontOffer> offers = new ArrayList<>();
		for (Element entry : entries)
		{
			requireElement(entry, "item", Set.of());
			final List<Element> parts = childElements(entry);
			if ((parts.size() != 2) || !"ingredient".equals(parts.get(0).getTagName()) || !"production".equals(parts.get(1).getTagName()))
			{
				throw new IllegalArgumentException("Each progression entry must have one ingredient followed by one production.");
			}

			final Element ingredient = parts.get(0);
			final Element product = parts.get(1);
			requireElement(ingredient, "ingredient", Set.of("id", "count"));
			requireElement(product, "production", Set.of("id", "count"));
			if (!childElements(ingredient).isEmpty() || !childElements(product).isEmpty())
			{
				throw new IllegalArgumentException("Progression ingredient and production must not contain child elements.");
			}

			final int currencyId = strictPositiveInt(ingredient.getAttribute("id"), "progression currency id");
			final long price = strictPositiveLong(ingredient.getAttribute("count"), "progression price");
			final int itemId = strictPositiveInt(product.getAttribute("id"), "progression product id");
			final long count = strictPositiveLong(product.getAttribute("count"), "progression product count");
			final ExpectedOffer expected = expectedById.get(itemId);
			if ((currencyId != Inventory.ADENA_ID) || (expected == null) || (count != expected.count()) || !seen.add(itemId))
			{
				throw new IllegalArgumentException("Progression currency, product, count or uniqueness is invalid.");
			}
			offers.add(new StorefrontOffer(itemId, count, expected.label(), price));
		}
		if (seen.size() != EXPECTED_OFFERS.size())
		{
			throw new IllegalArgumentException("Progression products do not exactly cover the curated allowlist.");
		}
		return List.copyOf(offers);
	}

	private static int strictPositiveInt(String value, String field)
	{
		final long parsed = strictPositiveLong(value, field);
		if (parsed > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException(field + " is outside integer bounds.");
		}
		return (int) parsed;
	}

	private static long strictPositiveLong(String value, String field)
	{
		if ((value == null) || !value.matches("[1-9][0-9]*"))
		{
			throw new IllegalArgumentException(field + " must be a positive decimal integer.");
		}
		try
		{
			return Long.parseLong(value, 10);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(field + " is outside long bounds.", e);
		}
	}

	private static List<Element> childElements(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				result.add((Element) child);
			}
			else if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Unexpected text in progression multisell.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unexpected progression multisell element: " + element.getTagName());
		}
		final NamedNodeMap actual = element.getAttributes();
		if (actual.getLength() != attributes.size())
		{
			throw new IllegalArgumentException("Unexpected attributes on progression multisell element " + name + ".");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Missing progression multisell attribute " + attribute + ".");
			}
		}
	}

	static record ExpectedOffer(int itemId, long count, String label)
	{
	}

	static record RuntimeState(boolean valid, List<StorefrontOffer> offers, String diagnostic)
	{
		RuntimeState
		{
			offers = List.copyOf(offers == null ? List.of() : offers);
		}

		static RuntimeState disabled(String diagnostic)
		{
			return new RuntimeState(false, List.of(), diagnostic);
		}
	}

	public record StorefrontOffer(int itemId, long count, String label, long price)
	{
	}

	public static PersonalProgressionShopService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalProgressionShopService INSTANCE = new PersonalProgressionShopService();
	}
}
