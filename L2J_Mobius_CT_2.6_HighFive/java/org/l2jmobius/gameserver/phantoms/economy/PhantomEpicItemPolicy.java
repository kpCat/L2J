/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.Set;
import org.l2jmobius.gameserver.model.item.ItemTemplate;

/** Stock H5 unique boss accessories. Classification is independent of crystal grade and price. */
public final class PhantomEpicItemPolicy
{
	// Canonical item definitions; this list classifies identity, never assigns a price.
	private static final Set<Integer> IDS = Set.of(6656, 6657, 6658, 6659, 6660, 6661, 6662, 8191, 10314, 16025, 16026, 21712);
	private PhantomEpicItemPolicy() { }
	public static boolean isEpic(ItemTemplate item) { return item != null && isEpic(item.getId()); }
	public static boolean isEpic(int itemId) { return IDS.contains(itemId); }
}
