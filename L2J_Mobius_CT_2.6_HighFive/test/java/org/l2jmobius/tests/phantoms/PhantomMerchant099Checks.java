/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;

/** Current owner configuration is an input, never replaced by a historical disabled preset. */
public final class PhantomMerchant099Checks
{
    public static void main(String[] args)
    {
        if (args.length != 1) { throw new IllegalArgumentException("Exact read-only merchant policy path required."); }
        final var policy = PhantomMerchantPolicy.load(Path.of(args[0]));
        PhantomAssertions.assertTrue(policy != null, "TASK099 owner merchants are disabled.");
        PhantomAssertions.assertEquals(200, policy.targetStores(), "TASK099 target changed.");
        PhantomAssertions.assertEquals(250, policy.hardCap(), "TASK099 hard cap changed.");
        PhantomAssertions.assertEquals(300, policy.lifetimeIdentities(), "TASK099 lifetime limit changed.");
        PhantomAssertions.assertEquals(320, policy.sites().stream().mapToInt(site -> site.columns() * site.rows()).sum(), "Configured raw slots changed; this is not legal/native proof.");
        PhantomAssertions.assertTrue(Math.multiplyExact(200L, policy.capitalPerIdentity()) <= policy.globalCapital(), "Target capital exceeds finite lifetime budget.");

        var ledger = PhantomMerchantBootstrap.Ledger.empty(policy.lifetimeIdentities(), policy.globalCapital(), policy.globalStockValue(), policy.perItemQuota());
        ledger = ledger.reserve(policy.capitalPerIdentity(), 250, Map.of(1835, 4L));
        final var restored = PhantomMerchantBootstrap.decodeLedger(PhantomMerchantBootstrap.encode(ledger));
        PhantomAssertions.assertEquals(ledger, restored, "SameDB codec reset finite bootstrap.");
        final var adopted = restored.reserve(0, 0, Map.of());
        PhantomAssertions.assertEquals(restored.issuedCapital(), adopted.issuedCapital(), "Adoption added capital.");
        PhantomAssertions.assertEquals(restored.issuedStockValue(), adopted.issuedStockValue(), "Adoption added stock value.");
        PhantomAssertions.assertEquals(restored.issuedItems(), adopted.issuedItems(), "Adoption added physical item entitlement.");
        PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(0, 0, Map.of(1835, policy.perItemQuota())), "Per-item remaining quota bypassed.");
        PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(policy.globalCapital(), 0, Map.of()), "Capital remaining quota bypassed.");

        final var role = new PhantomMerchantBootstrap.Role(0, 42, "merchant099", "A".repeat(43), "Trader099", 53, 0, 81800, 148700, -3400, 100000, Map.of(1835, 4L), PhantomMerchantBootstrap.Stage.READY);
        final var uncertain = PhantomMerchantBootstrap.decodeRole(PhantomMerchantBootstrap.encode(role.pending())).afterUncertainRestart();
        PhantomAssertions.assertEquals(PhantomMerchantBootstrap.Stage.INCONSISTENT, uncertain.stage(), "Uncertain seed was eligible for replay.");
        PhantomAssertions.assertEquals(uncertain, uncertain.afterUncertainRestart(), "Repeated recovery changed the uncertainty fence.");
        PhantomAssertions.assertEquals(role, PhantomMerchantBootstrap.decodeRole(PhantomMerchantBootstrap.encode(role)).afterUncertainRestart(), "READY changed during recovery.");
        stockKindCapacity();
        System.out.println("OWN099_DB_FREE_PASS groups=3;rawSlots=320;targetCapital=" + Math.multiplyExact(200L, policy.capitalPerIdentity()) + ";DB_WRITES=0;NATIVE_STORES=NOT_RUN");
    }

    private static void stockKindCapacity()
    {
        final var issued = new java.util.TreeMap<Integer, Long>();
        for (int item = 1000; item < 1128; item++) { issued.put(item, 1L); }
        final var full = new PhantomMerchantBootstrap.Ledger(32, 1000, 1000, 10, 16, 160, 128, issued);
        final var selected = new java.util.TreeMap<Integer, Long>();
        for (int item : new int[] {1128, 1000})
        {
            if (full.canReserveStockKind(selected.keySet(), item)) { selected.put(item, 1L); break; }
        }
        final var next = full.reserve(10, 1, selected);
        PhantomAssertions.assertEquals(Map.of(1000, 1L), selected, "A full ledger selected an unrepresentable new stock kind.");
        PhantomAssertions.assertEquals(128, next.issuedItems().size(), "Birth expanded the existing ledger kind bound.");
        PhantomAssertions.assertEquals(2L, next.issuedItems().get(1000).longValue(), "Existing-kind birth reset its consumed quantity.");
        PhantomAssertions.assertEquals(17, next.nextOrdinal(), "Existing-kind birth reused its ordinal.");
        PhantomAssertions.assertEquals(170L, next.issuedCapital(), "Existing-kind birth skipped finite capital accounting.");
        PhantomAssertions.assertEquals(129L, next.issuedStockValue(), "Existing-kind birth skipped finite stock accounting.");
        final byte[] encoded = PhantomMerchantBootstrap.encode(next);
        PhantomAssertions.assertTrue(encoded.length <= org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent.MAX_PAYLOAD_BYTES, "Birth exceeded the existing component envelope.");
        PhantomAssertions.assertEquals(next, PhantomMerchantBootstrap.decodeLedger(encoded), "Restart lost the full kind ledger.");
        PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> full.reserve(0, 1, Map.of(1128, 1L)), "Direct reserve bypassed the hard 128-kind validator.");
        PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> full.reserve(0, 1, Map.of(1000, 10L)), "Existing-kind birth bypassed its per-item quota.");
        issued.remove(1127);
        final var almostFull = new PhantomMerchantBootstrap.Ledger(32, 1000, 1000, 10, 16, 160, 127, issued);
        PhantomAssertions.assertTrue(almostFull.canReserveStockKind(java.util.Set.of(), 1127), "The final free kind was lost.");
        PhantomAssertions.assertFalse(almostFull.canReserveStockKind(java.util.Set.of(1127), 1128), "Pending kind128 admitted a distinct kind129.");
        PhantomAssertions.assertTrue(almostFull.canReserveStockKind(java.util.Set.of(1127), 1000), "Pending kind128 blocked an issued kind.");
        PhantomAssertions.assertTrue(almostFull.canReserveStockKind(java.util.Set.of(1000), 1127), "Issued and pending kinds were double counted.");
        PhantomAssertions.assertFalse(full.canReserveStockKind(java.util.Set.of(1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007), 1008), "A ninth birth kind bypassed the role bound.");
        System.out.println("PASS merchant117.stock-kind-capacity finite128/role8/quotas/codec; DB_WRITES=0;NATIVE_STORES=NOT_RUN");
    }
}
