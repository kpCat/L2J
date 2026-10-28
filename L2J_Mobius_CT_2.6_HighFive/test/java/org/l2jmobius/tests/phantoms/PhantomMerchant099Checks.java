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
        System.out.println("OWN099_DB_FREE_PASS groups=3;rawSlots=320;targetCapital=" + Math.multiplyExact(200L, policy.capitalPerIdentity()) + ";DB_WRITES=0;NATIVE_STORES=NOT_RUN");
    }
}
