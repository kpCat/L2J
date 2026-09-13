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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Strict data-only mapping from existing item templates to level-gap tiers.
 */
public final class LevelGapItemCatalog
{
	private static final int MAXIMUM_BYTES = 64 * 1024;
	private static final int MAXIMUM_ENTRIES = 32;
	private static final List<Integer> REQUIRED_TIERS = List.of(5, 10, 20, 40);

	private final List<Tier> _tiers;

	private LevelGapItemCatalog(List<Tier> tiers)
	{
		_tiers = List.copyOf(tiers);
	}

	public static LevelGapItemCatalog load(Path path)
	{
		final Element root = StrictUtf8Xml.parse(path, MAXIMUM_BYTES, "level-gap item catalog");
		requireElement(root, "levelGapItems", Set.of("version"));
		if (!"1".equals(root.getAttribute("version")))
		{
			throw new IllegalArgumentException("Unknown level-gap item catalog version.");
		}

		final List<Element> rows = childElements(root);
		if ((rows.size() != REQUIRED_TIERS.size()) || (rows.size() > MAXIMUM_ENTRIES))
		{
			throw new IllegalArgumentException("Level-gap item catalog must contain exactly the 5/10/20/40 tiers.");
		}

		final List<Tier> tiers = new ArrayList<>(rows.size());
		final Set<Integer> itemIds = new HashSet<>();
		for (int index = 0; index < rows.size(); index++)
		{
			final Element row = rows.get(index);
			requireElement(row, "item", Set.of("itemId", "maximumGap", "label"));
			requireNoElementChildren(row);
			final int itemId = strictPositiveInt(row.getAttribute("itemId"), "itemId");
			final int maximumGap = strictPositiveInt(row.getAttribute("maximumGap"), "maximumGap");
			final String label = row.getAttribute("label").trim();
			if (!itemIds.add(itemId))
			{
				throw new IllegalArgumentException("Duplicate level-gap item id.");
			}
			if (maximumGap != REQUIRED_TIERS.get(index))
			{
				throw new IllegalArgumentException("Level-gap tiers must be unique and ordered as 5, 10, 20, 40.");
			}
			if (label.isEmpty() || (label.length() > 80))
			{
				throw new IllegalArgumentException("Level-gap tier label is empty or too long.");
			}
			tiers.add(new Tier(itemId, maximumGap, label));
		}
		return new LevelGapItemCatalog(tiers);
	}

	public List<Tier> tiers()
	{
		return _tiers;
	}

	private static int strictPositiveInt(String value, String field)
	{
		if ((value == null) || !value.matches("[1-9][0-9]*"))
		{
			throw new IllegalArgumentException(field + " must be a positive decimal integer.");
		}
		try
		{
			return Integer.parseInt(value, 10);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(field + " is outside integer bounds.", e);
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
				throw new IllegalArgumentException("Unexpected text in level-gap item catalog.");
			}
		}
		return result;
	}

	private static void requireNoElementChildren(Element element)
	{
		if (!childElements(element).isEmpty())
		{
			throw new IllegalArgumentException("Level-gap item rows must not contain child elements.");
		}
	}

	private static void requireElement(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unexpected element in level-gap item catalog: " + element.getTagName());
		}
		final NamedNodeMap actual = element.getAttributes();
		if (actual.getLength() != attributes.size())
		{
			throw new IllegalArgumentException("Unexpected attributes on level-gap element " + name + ".");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Missing level-gap attribute " + attribute + ".");
			}
		}
	}

	public record Tier(int itemId, int maximumGap, String label)
	{
	}
}
