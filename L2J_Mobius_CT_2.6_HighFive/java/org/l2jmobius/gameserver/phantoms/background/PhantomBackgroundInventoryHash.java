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
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;

/**
 * Deterministic digest over every locked canonical INVENTORY/PAPERDOLL row.
 */
public final class PhantomBackgroundInventoryHash
{
	private PhantomBackgroundInventoryHash()
	{
	}

	public static String compute(List<CanonicalItem> items)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (CanonicalItem item : items.stream().sorted(Comparator.comparingInt(CanonicalItem::objectId)).toList())
			{
				add(digest, item.objectId());
				add(digest, item.itemId());
				add(digest, item.count());
				add(digest, item.location().name());
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static void add(MessageDigest digest, Object value)
	{
		digest.update(value.toString().getBytes(StandardCharsets.US_ASCII));
		digest.update((byte) 0);
	}

	public record CanonicalItem(int objectId, int itemId, long count, ItemLocation location)
	{
		public CanonicalItem
		{
			if ((objectId <= 0) || (itemId <= 0) || (count <= 0) || (location == null))
			{
				throw new IllegalArgumentException("Invalid canonical inventory item.");
			}
		}
	}
}
