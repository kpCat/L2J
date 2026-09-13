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

import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.qol.LevelGapItemCatalog.Tier;
import org.l2jmobius.gameserver.qol.LevelGapProtectionPolicy.Decision;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Runtime composition for inventory-derived level-gap protection and its native shop admission.
 */
public final class PersonalPremiumQoLService
{
	private static final Logger LOGGER = Logger.getLogger(PersonalPremiumQoLService.class.getName());
	private static final int MAXIMUM_MULTI_SELL_BYTES = 64 * 1024;
	public static final int SHOP_LIST_ID = 91001;

	private volatile RuntimeState _state = RuntimeState.disabled("Service has not been initialized.");

	private PersonalPremiumQoLService()
	{
	}

	public synchronized void initialize()
	{
		try
		{
			_state = build(PersonalPremiumQoLConfig.settings(), Path.of("."));
			if (_state.enabled())
			{
				LOGGER.info("Personal/Premium QoL enabled with " + _state.catalog().tiers().size() + " level-gap tiers; shop=" + _state.shopEnabled() + ".");
			}
			else
			{
				LOGGER.info("Personal/Premium QoL disabled: " + _state.diagnostic());
			}
		}
		catch (RuntimeException e)
		{
			_state = RuntimeState.disabled("Active configuration failed validation.");
			LOGGER.log(Level.SEVERE, "Personal/Premium QoL failed closed; feature and shop are disabled.", e);
		}
	}

	public Decision snapshot(Creature victim, Creature rewardActor)
	{
		final RuntimeState state = _state;
		final Player player = rewardActor == null ? null : rewardActor.asPlayer();
		final boolean actorEligible = (player != null) && !player.hasHeadlessOutboundSession();
		final int maximumGap = state.enabled() && actorEligible ? maximumGap(state.catalog(), player) : 0;
		return LevelGapProtectionPolicy.decide(state.enabled(), actorEligible, maximumGap, victim.getLevel(), rewardActor != null ? rewardActor.getLevel() : 0);
	}

	public int maximumGap(Player player)
	{
		final RuntimeState state = _state;
		return (state.enabled() && (player != null) && !player.hasHeadlessOutboundSession()) ? maximumGap(state.catalog(), player) : 0;
	}

	public boolean isEnabled()
	{
		return _state.enabled();
	}

	public boolean isShopEnabled()
	{
		final RuntimeState state = _state;
		return state.enabled() && state.shopEnabled();
	}

	public String diagnostic()
	{
		return _state.diagnostic();
	}

	public static boolean isShopList(int listId)
	{
		return listId == SHOP_LIST_ID;
	}

	static int maximumGap(LevelGapItemCatalog catalog, Player player)
	{
		int maximumGap = 0;
		for (Tier tier : catalog.tiers())
		{
			if ((tier.maximumGap() > maximumGap) && (player.getInventory().getInventoryItemCount(tier.itemId(), -1) > 0))
			{
				maximumGap = tier.maximumGap();
			}
		}
		return maximumGap;
	}

	static RuntimeState build(Settings settings, Path workingDirectory)
	{
		if (!settings.enabled())
		{
			return RuntimeState.disabled(settings.diagnostic());
		}
		if (!settings.valid())
		{
			throw new IllegalArgumentException(settings.diagnostic());
		}

		final Path root = workingDirectory.toAbsolutePath().normalize();
		final Path catalogPath = root.resolve(settings.catalogPath()).normalize();
		if (!catalogPath.startsWith(root))
		{
			throw new IllegalArgumentException("Level-gap catalog resolves outside the runtime root.");
		}
		final LevelGapItemCatalog catalog = LevelGapItemCatalog.load(catalogPath);
		validateTemplates(catalog);

		Map<Integer, Long> prices = Map.of();
		if (settings.shopEnabled())
		{
			final Path multisellPath = root.resolve("data/multisell/" + SHOP_LIST_ID + ".xml").normalize();
			prices = validateShop(catalog, multisellPath);
			if (!MultisellData.getInstance().hasList(SHOP_LIST_ID))
			{
				throw new IllegalArgumentException("Native multisell " + SHOP_LIST_ID + " was not loaded.");
			}
		}
		return new RuntimeState(true, settings.shopEnabled(), catalog, prices, "Active configuration validated.");
	}

	static RuntimeState installForTests(RuntimeState state)
	{
		final PersonalPremiumQoLService service = getInstance();
		final RuntimeState previous = service._state;
		service._state = state;
		return previous;
	}

	static void validateTemplates(LevelGapItemCatalog catalog)
	{
		for (Tier tier : catalog.tiers())
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(tier.itemId());
			if (!(template instanceof EtcItem item) || (item.getItemType() != EtcItemType.NONE) || !item.isStackable() || item.isEquipable() || item.isQuestItem() || item.isConditionAttached() || item.hasSkills() || item.hasImmediateEffect() || item.hasExImmediateEffect() || (item.getDefaultAction() != ActionType.NONE) || (item.getHandlerName() != null) || (item.getDuration() > 0) || (item.getTime() > 0) || (item.getAutoDestroyTime() > 0) || (item.getReferencePrice() != 0))
			{
				throw new IllegalArgumentException("Configured level-gap item " + tier.itemId() + " is not an inert, stackable H5 carrier template.");
			}
		}
	}

	static Map<Integer, Long> validateShop(LevelGapItemCatalog catalog, Path path)
	{
		final Element root = StrictUtf8Xml.parse(path, MAXIMUM_MULTI_SELL_BYTES, "Personal/Premium QoL multisell");
		requireElement(root, "list", Set.of("xmlns:xsi", "xsi:noNamespaceSchemaLocation"));
		if (!"http://www.w3.org/2001/XMLSchema-instance".equals(root.getAttribute("xmlns:xsi")) || !"../xsd/multisell.xsd".equals(root.getAttribute("xsi:noNamespaceSchemaLocation")))
		{
			throw new IllegalArgumentException("QoL multisell schema declaration is invalid.");
		}
		final List<Element> rootChildren = childElements(root);
		if (rootChildren.isEmpty() || !"npcs".equals(rootChildren.getFirst().getTagName()))
		{
			throw new IllegalArgumentException("QoL multisell must explicitly allow the Community Board actor.");
		}
		final Element npcs = rootChildren.getFirst();
		requireElement(npcs, "npcs", Set.of());
		final List<Element> allowedNpcs = childElements(npcs);
		if ((allowedNpcs.size() != 1) || !"npc".equals(allowedNpcs.getFirst().getTagName()))
		{
			throw new IllegalArgumentException("QoL multisell must contain only the Community Board NPC marker.");
		}
		final Element allowedNpc = allowedNpcs.getFirst();
		requireElement(allowedNpc, "npc", Set.of());
		if (!"-1".equals(allowedNpc.getTextContent().trim()))
		{
			throw new IllegalArgumentException("QoL multisell Community Board NPC marker is invalid.");
		}
		final List<Element> entries = rootChildren.subList(1, rootChildren.size());
		if (entries.size() != catalog.tiers().size())
		{
			throw new IllegalArgumentException("QoL multisell must contain one native entry per configured tier.");
		}

		final Set<Integer> configured = new HashSet<>();
		catalog.tiers().forEach(tier -> configured.add(tier.itemId()));
		if (configured.contains(Inventory.ADENA_ID))
		{
			throw new IllegalArgumentException("QoL carrier items must not collide with shop currency.");
		}

		final Map<Integer, Long> prices = new HashMap<>();
		for (Element entry : entries)
		{
			requireElement(entry, "item", Set.of());
			final List<Element> parts = childElements(entry);
			if ((parts.size() != 2) || !"ingredient".equals(parts.get(0).getTagName()) || !"production".equals(parts.get(1).getTagName()))
			{
				throw new IllegalArgumentException("Each QoL multisell entry must have exactly one ingredient followed by one production.");
			}
			final Element ingredient = parts.get(0);
			final Element product = parts.get(1);
			requireElement(ingredient, "ingredient", Set.of("id", "count"));
			requireElement(product, "production", Set.of("id", "count"));
			if (!childElements(ingredient).isEmpty() || !childElements(product).isEmpty())
			{
				throw new IllegalArgumentException("QoL multisell ingredient and production must not contain child elements.");
			}

			final int currencyId = strictPositiveInt(ingredient.getAttribute("id"), "multisell currency id");
			final long price = strictPositiveLong(ingredient.getAttribute("count"), "multisell price");
			final int itemId = strictPositiveInt(product.getAttribute("id"), "multisell product id");
			final long productCount = strictPositiveLong(product.getAttribute("count"), "multisell product count");
			if ((currencyId != Inventory.ADENA_ID) || (productCount != 1) || !configured.contains(itemId) || (prices.put(itemId, price) != null))
			{
				throw new IllegalArgumentException("QoL multisell currency, product, count or uniqueness is invalid.");
			}
		}
		if (!prices.keySet().equals(configured))
		{
			throw new IllegalArgumentException("QoL multisell products do not exactly cover the configured tiers.");
		}
		return Map.copyOf(prices);
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
				throw new IllegalArgumentException("Unexpected text in QoL multisell.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()))
		{
			throw new IllegalArgumentException("Unexpected QoL multisell element: " + element.getTagName());
		}
		final NamedNodeMap actual = element.getAttributes();
		if (actual.getLength() != attributes.size())
		{
			throw new IllegalArgumentException("Unexpected attributes on QoL multisell element " + name + ".");
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Missing QoL multisell attribute " + attribute + ".");
			}
		}
	}

	static record RuntimeState(boolean enabled, boolean shopEnabled, LevelGapItemCatalog catalog, Map<Integer, Long> prices, String diagnostic)
	{
		static RuntimeState disabled(String diagnostic)
		{
			return new RuntimeState(false, false, null, Map.of(), diagnostic);
		}
	}

	public static PersonalPremiumQoLService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalPremiumQoLService INSTANCE = new PersonalPremiumQoLService();
	}
}
