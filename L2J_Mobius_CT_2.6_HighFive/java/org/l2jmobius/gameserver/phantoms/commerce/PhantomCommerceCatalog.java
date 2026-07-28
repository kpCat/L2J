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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;

/**
 * Immutable, bounded query view of authoritative NPC commerce data.
 */
public final class PhantomCommerceCatalog
{
	public static final int MAX_PAGE_SIZE = 256;

	private static final Comparator<BuyOffer> BUY_ORDER = Comparator.comparingInt(BuyOffer::listId).thenComparingInt(BuyOffer::itemId).thenComparing(BuyOffer::source);
	private static final Comparator<MultisellOffer> MULTISELL_ORDER = Comparator.comparingInt(MultisellOffer::listId).thenComparingInt(MultisellOffer::entryId).thenComparing(MultisellOffer::source);
	private static final Comparator<TeleportRoute> TELEPORT_ORDER = Comparator.comparingInt(TeleportRoute::npcId).thenComparing(TeleportRoute::listName).thenComparingInt(TeleportRoute::ordinal).thenComparing(TeleportRoute::source);
	private static final Comparator<SupplyFact> SUPPLY_ORDER = Comparator.comparingInt(SupplyFact::itemId).thenComparing(SupplyFact::source);

	private final List<BuyOffer> _buyOffers;
	private final List<MultisellOffer> _multisellOffers;
	private final List<TeleportRoute> _teleportRoutes;
	private final List<SupplyFact> _supplies;
	private final Map<Integer, List<BuyOffer>> _buyByItem;
	private final Map<Integer, List<MultisellOffer>> _multisellByProduct;
	private final Map<Integer, List<TeleportRoute>> _teleportByNpc;
	private final Map<Integer, SupplyFact> _supplyByItem;
	private final CatalogHashes _hashes;

	public PhantomCommerceCatalog(Collection<BuyOffer> buyOffers, Collection<MultisellOffer> multisellOffers, Collection<TeleportRoute> teleportRoutes, Collection<SupplyFact> supplies)
	{
		_buyOffers = sortedCopy(buyOffers, BUY_ORDER);
		_multisellOffers = sortedCopy(multisellOffers, MULTISELL_ORDER);
		_teleportRoutes = sortedCopy(teleportRoutes, TELEPORT_ORDER);
		_supplies = sortedCopy(supplies, SUPPLY_ORDER);
		_buyByItem = index(_buyOffers, BuyOffer::itemId);
		final Map<Integer, List<MultisellOffer>> multisellByProduct = new HashMap<>();
		for (MultisellOffer offer : _multisellOffers)
		{
			for (ItemAmount product : offer.products())
			{
				multisellByProduct.computeIfAbsent(product.itemId(), _ -> new ArrayList<>()).add(offer);
			}
		}
		_multisellByProduct = immutableIndex(multisellByProduct);
		_teleportByNpc = index(_teleportRoutes, TeleportRoute::npcId);
		final Map<Integer, SupplyFact> supplyByItem = new TreeMap<>();
		for (SupplyFact supply : _supplies)
		{
			if (supplyByItem.putIfAbsent(supply.itemId(), supply) != null)
			{
				throw new IllegalArgumentException("Duplicate supply item " + supply.itemId() + ".");
			}
		}
		_supplyByItem = Collections.unmodifiableMap(supplyByItem);
		final String buyHash = hashLines(_buyOffers);
		final String multisellHash = hashLines(_multisellOffers);
		final String teleportHash = hashLines(_teleportRoutes);
		final String supplyHash = hashLines(_supplies);
		_hashes = new CatalogHashes(buyHash, multisellHash, teleportHash, supplyHash, hashLines(List.of(buyHash, multisellHash, teleportHash, supplyHash)));
	}

	public CatalogPage<BuyOffer> findBuyOffers(int itemId, int offset, int limit)
	{
		return page(_buyByItem.getOrDefault(itemId, List.of()), offset, limit);
	}

	public CatalogPage<MultisellOffer> findMultisellOffers(int productItemId, int offset, int limit)
	{
		return page(_multisellByProduct.getOrDefault(productItemId, List.of()), offset, limit);
	}

	public CatalogPage<TeleportRoute> findTeleportRoutes(int npcId, int offset, int limit)
	{
		return page(_teleportByNpc.getOrDefault(npcId, List.of()), offset, limit);
	}

	public SupplyFact findSupply(int itemId)
	{
		return _supplyByItem.get(itemId);
	}

	public List<BuyOffer> buyOffers()
	{
		return _buyOffers;
	}

	public List<MultisellOffer> multisellOffers()
	{
		return _multisellOffers;
	}

	public List<TeleportRoute> teleportRoutes()
	{
		return _teleportRoutes;
	}

	public List<SupplyFact> supplies()
	{
		return _supplies;
	}

	public CatalogHashes hashes()
	{
		return _hashes;
	}

	private static <T> List<T> sortedCopy(Collection<T> source, Comparator<T> comparator)
	{
		Objects.requireNonNull(source, "Catalog source must not be null.");
		return source.stream().map(value -> Objects.requireNonNull(value, "Catalog fact must not be null.")).sorted(comparator).toList();
	}

	private static <T> Map<Integer, List<T>> index(List<T> source, java.util.function.ToIntFunction<T> key)
	{
		final Map<Integer, List<T>> result = new HashMap<>();
		for (T value : source)
		{
			result.computeIfAbsent(key.applyAsInt(value), _ -> new ArrayList<>()).add(value);
		}
		return immutableIndex(result);
	}

	private static <T> Map<Integer, List<T>> immutableIndex(Map<Integer, List<T>> source)
	{
		final Map<Integer, List<T>> result = new TreeMap<>();
		source.forEach((key, values) -> result.put(key, List.copyOf(values)));
		return Collections.unmodifiableMap(result);
	}

	private static <T> CatalogPage<T> page(List<T> source, int offset, int limit)
	{
		if ((offset < 0) || (limit < 1) || (limit > MAX_PAGE_SIZE))
		{
			throw new IllegalArgumentException("Catalog query requires offset >= 0 and limit 1..256.");
		}
		if (offset >= source.size())
		{
			return new CatalogPage<>(List.of(), offset, source.size(), false);
		}
		final int end = Math.min(source.size(), Math.addExact(offset, limit));
		return new CatalogPage<>(source.subList(offset, end), offset, source.size(), end < source.size());
	}

	private static String hashLines(Collection<?> values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(value.toString().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	public record BuyOffer(int listId, int itemId, Set<Integer> npcIds, long price, boolean limitedStock, String source)
	{
		public BuyOffer
		{
			if ((listId <= 0) || (itemId <= 0) || (price < 0))
			{
				throw new IllegalArgumentException("Buy offer IDs must be positive and price must be nonnegative.");
			}
			npcIds = immutablePositiveSet(npcIds, "Buy offer NPC");
			source = requireSource(source);
		}
	}

	public record ItemAmount(int itemId, long count)
	{
		public ItemAmount
		{
			if ((itemId == 0) || (count <= 0))
			{
				throw new IllegalArgumentException("Item amount requires a nonzero item ID and positive count.");
			}
		}
	}

	public record MultisellFlags(boolean applyTaxes, boolean maintainEnchantment, double useRate)
	{
		public MultisellFlags
		{
			if (!Double.isFinite(useRate) || (useRate <= 0))
			{
				throw new IllegalArgumentException("Multisell use rate must be finite and positive.");
			}
		}
	}

	public record MultisellOffer(int listId, int entryId, Set<Integer> npcIds, List<ItemAmount> ingredients, List<ItemAmount> products, MultisellFlags flags, String source)
	{
		public MultisellOffer
		{
			if ((listId <= 0) || (entryId <= 0))
			{
				throw new IllegalArgumentException("Multisell IDs must be positive.");
			}
			npcIds = immutablePositiveSet(npcIds, "Multisell NPC");
			ingredients = immutableAmounts(ingredients);
			products = immutableAmounts(products);
			if (products.isEmpty())
			{
				throw new IllegalArgumentException("Multisell offer must contain a product.");
			}
			Objects.requireNonNull(flags, "Multisell flags must not be null.");
			source = requireSource(source);
		}
	}

	public record TeleportRoute(int npcId, String listName, TeleportType type, int ordinal, Location destination, int feeItemId, long feeCount, Set<Integer> castleIds, String source)
	{
		public TeleportRoute
		{
			if ((npcId <= 0) || (ordinal < 0) || (feeItemId < 0) || (feeCount < 0))
			{
				throw new IllegalArgumentException("Teleport route IDs, ordinal and fee must be nonnegative.");
			}
			listName = requireKey(listName, "Teleport list name");
			Objects.requireNonNull(type, "Teleport type must not be null.");
			Objects.requireNonNull(destination, "Teleport destination must not be null.");
			castleIds = immutablePositiveSet(castleIds, "Teleport castle");
			source = requireSource(source);
		}
	}

	public enum SupplyKind
	{
		SHOT,
		HP_RESTORE,
		MP_RESTORE,
		CP_RESTORE,
		PET_FOOD,
		SUMMON_RESOURCE
	}

	public record SupplyFact(int itemId, Set<SupplyKind> kinds, Set<Integer> boundSkillIds, long reuseDelay, boolean olympiadRestricted, long weight, boolean stackable, String source)
	{
		public SupplyFact
		{
			if ((itemId <= 0) || (reuseDelay < 0) || (weight < 0))
			{
				throw new IllegalArgumentException("Supply IDs must be positive and numeric facts nonnegative.");
			}
			Objects.requireNonNull(kinds, "Supply kinds must not be null.");
			if (kinds.isEmpty())
			{
				throw new IllegalArgumentException("Supply must have at least one mechanical kind.");
			}
			kinds = Collections.unmodifiableSet(EnumSet.copyOf(kinds));
			boundSkillIds = immutablePositiveSet(boundSkillIds, "Bound skill");
			source = requireSource(source);
		}
	}

	public record CatalogPage<T>(List<T> values, int offset, int total, boolean hasMore)
	{
		public CatalogPage
		{
			values = List.copyOf(values);
			if ((offset < 0) || (total < 0) || (values.size() > MAX_PAGE_SIZE))
			{
				throw new IllegalArgumentException("Invalid catalog page.");
			}
		}
	}

	public record CatalogHashes(String buy, String multisell, String teleport, String supply, String combined)
	{
		public CatalogHashes
		{
			buy = requireHash(buy);
			multisell = requireHash(multisell);
			teleport = requireHash(teleport);
			supply = requireHash(supply);
			combined = requireHash(combined);
		}
	}

	private static Set<Integer> immutablePositiveSet(Set<Integer> source, String label)
	{
		Objects.requireNonNull(source, label + " set must not be null.");
		final java.util.TreeSet<Integer> result = new java.util.TreeSet<>();
		for (Integer value : source)
		{
			if ((value == null) || (value <= 0))
			{
				throw new IllegalArgumentException(label + " ID must be positive.");
			}
			result.add(value);
		}
		return Collections.unmodifiableSet(result);
	}

	private static List<ItemAmount> immutableAmounts(List<ItemAmount> source)
	{
		Objects.requireNonNull(source, "Item amounts must not be null.");
		return source.stream().map(value -> Objects.requireNonNull(value, "Item amount must not be null.")).toList();
	}

	private static String requireKey(String value, String label)
	{
		if ((value == null) || value.isBlank() || (value.length() > 96))
		{
			throw new IllegalArgumentException(label + " must contain 1..96 characters.");
		}
		return value;
	}

	private static String requireSource(String value)
	{
		return requireKey(value, "Source");
	}

	private static String requireHash(String value)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Catalog hash must be lowercase SHA-256.");
		}
		return value;
	}
}
