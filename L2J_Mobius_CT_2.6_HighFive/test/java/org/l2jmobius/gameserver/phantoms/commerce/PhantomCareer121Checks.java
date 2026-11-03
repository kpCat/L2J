/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply.AcquisitionQuote;

/** Synthetic cost contrasts; these quotes never prove native stock, payments or equipment. */
public final class PhantomCareer121Checks
{
	public static void main(String[] args)
	{
		final var npc = new AcquisitionQuote(1200, 0, 0, 1, 100);
		check(PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(900, 100, 0, 1, 200), npc), "Cheaper legal offline equipment was hidden by an available NPC offer");
		check(!PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1100, 101, 0, 1, 50), npc)
			&& !PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(600, 0, 300, 1, 50, 60), npc), "Travel or native recipe risk/input value was ignored");
		check(PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1200, 0, 0, 1, 50), npc)
			&& !PhantomOfflineSupply.preferEquipment(new AcquisitionQuote(1200, 0, 0, 1, 200), npc)
			&& !PhantomOfflineSupply.preferEquipment(null, npc)
			&& PhantomOfflineSupply.preferEquipment(npc, null), "Distance tie or absent authoritative quote selected an invalid source");
		System.out.println("TASK121_EQUIPMENT_SELECTOR_PASS groups=3 SYNTHETIC=true NATIVE_PAID=NOT_RUN");
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK121 " + reason); } }
}
