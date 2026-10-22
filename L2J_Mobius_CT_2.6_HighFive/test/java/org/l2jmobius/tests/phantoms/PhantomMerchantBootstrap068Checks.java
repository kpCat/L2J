/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Map;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap;

/** Durable quota is independent of merchant lifetime and stock/capital source labels. */
public final class PhantomMerchantBootstrap068Checks
{
	public static void main(String[] args)
	{
		var ledger = PhantomMerchantBootstrap.Ledger.empty(3, 1000, 2000, 10);
		ledger = ledger.reserve(100, 250, Map.of(1835, 4L));
		final var restored = PhantomMerchantBootstrap.decodeLedger(PhantomMerchantBootstrap.encode(ledger));
		PhantomAssertions.assertEquals(ledger, restored, "Restart reset issued bootstrap quota.");
		PhantomAssertions.assertEquals(1, restored.nextOrdinal(), "Deleted/replaced identity could reuse ordinal.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(1, 1, Map.of(1835, 7L)), "Per-item server quota was reset.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(901, 1, Map.of()), "Global capital quota was bypassed.");
		var finalLedger = restored.reserve(400, 500, Map.of(1458, 3L)).reserve(500, 1000, Map.of(1458, 7L));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> finalLedger.reserve(0, 0, Map.of()), "Lifetime identity cap was reset by store closure.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(0, Long.MAX_VALUE, Map.of()), "Stock value overflow accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(-1, 0, Map.of()), "Negative bootstrap accepted.");
		final var adopted = restored.reserve(0, 0, Map.of());
		PhantomAssertions.assertEquals(restored.issuedCapital(), adopted.issuedCapital(), "Adoption minted new capital.");
		PhantomAssertions.assertEquals(restored.issuedStockValue(), adopted.issuedStockValue(), "Adoption minted new stock.");
		PhantomAssertions.assertEquals(restored.nextOrdinal() + 1, adopted.nextOrdinal(), "Adoption bypassed the lifetime identity cap.");
		final var role = new PhantomMerchantBootstrap.Role(0, 0, "merchant_a", "A".repeat(43), "MerchantA", 53, 0, 1, 2, 3, 100, Map.of(1835, 4L), PhantomMerchantBootstrap.Stage.RESERVED);
		PhantomAssertions.assertEquals(role, PhantomMerchantBootstrap.decodeRole(PhantomMerchantBootstrap.encode(role)), "Role provenance did not roundtrip.");
		PhantomAssertions.assertEquals(PhantomMerchantBootstrap.Stage.INCONSISTENT, role.withCharacter(42).pending().afterUncertainRestart().stage(), "Uncertain native subsidy was replayed.");
		System.out.println("OWN068_MERCHANT_BOOTSTRAP_PASS cases=3; DB_WRITES=0");
	}
}
