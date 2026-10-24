/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;

/** Durable quota is independent of merchant lifetime and stock/capital source labels. */
public final class PhantomMerchantBootstrap068Checks
{
	public static void main(String[] args) throws Exception
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
		policy500();
		ledger500();
		leases500();
		System.out.println("TASK084_MERCHANT_CAPACITY_PASS groups=3; DB_WRITES=0; NATIVE_STORES=NOT_RUN");
	}

	private static void policy500() throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"));
		final Path shipped = module.resolve("dist/game/" + PhantomMerchantPolicy.FILE);
		PhantomAssertions.assertEquals(null, PhantomMerchantPolicy.load(shipped), "Shipped merchants must remain disabled.");
		final String enabled = Files.readString(shipped).replace("EnableOfflinePhantomMerchants = False", "EnableOfflinePhantomMerchants = True");
		final Path input = Files.createTempFile("merchant084-policy-", ".ini");
		try
		{
			Files.writeString(input, enabled);
			final var old = PhantomMerchantPolicy.load(input);
			PhantomAssertions.assertEquals(200, old.targetStores(), "Historical target drifted.");
			PhantomAssertions.assertEquals(250, old.hardCap(), "Historical hard cap drifted.");
			PhantomAssertions.assertEquals(300, old.lifetimeIdentities(), "Historical lifetime drifted.");
			PhantomAssertions.assertEquals(320, old.sites().stream().mapToInt(site -> site.columns() * site.rows()).sum(), "Raw slots are not legal slots.");
			final String scale = enabled.replace("MerchantTargetStores = 200", "MerchantTargetStores = 500").replace("MerchantHardCap = 250", "MerchantHardCap = 500").replace("MerchantLifetimeIdentityLimit = 300", "MerchantLifetimeIdentityLimit = 512");
			Files.writeString(input, scale);
			final var policy = PhantomMerchantPolicy.load(input);
			PhantomAssertions.assertEquals(500, policy.targetStores(), "Scale target was not parsed.");
			PhantomAssertions.assertEquals(500, policy.hardCap(), "Scale hard cap was not parsed.");
			PhantomAssertions.assertEquals(512, policy.lifetimeIdentities(), "Persistence bound changed.");
			PhantomAssertions.assertEquals(old.sites(), policy.sites(), "Policy loading reordered existing sites.");
			PhantomAssertions.assertEquals(30_000_000L, policy.globalCapital(), "Capacity must not boost subsidy automatically.");
			Files.writeString(input, scale.replace("MerchantLifetimeIdentityLimit = 512", "MerchantLifetimeIdentityLimit = 500"));
			PhantomAssertions.assertEquals(500, PhantomMerchantPolicy.load(input).lifetimeIdentities(), "Exact 500 lifetime refused.");
			final var invalid = Map.ofEntries(
				Map.entry("MerchantTargetStores", "501"), Map.entry("MerchantHardCap", "501"),
				Map.entry("MerchantLifetimeIdentityLimit", "513"), Map.entry("MerchantAdmissionBatch", "11"),
				Map.entry("MerchantRotationSeconds", "14"), Map.entry("MerchantLinesPerStore", "21"),
				Map.entry("MerchantBootstrapCapitalPerIdentity", "-1"), Map.entry("MerchantBootstrapGlobalCapital", "99999"),
				Map.entry("MerchantBootstrapGlobalStockValue", "1000000000001"), Map.entry("MerchantBootstrapPerItemQuota", "0"),
				Map.entry("MerchantBootstrapStackCount", "1001"), Map.entry("MerchantBuyPercent", "81"),
				Map.entry("MerchantPreparedOfflineCharacterIds", "1,1"), Map.entry("MerchantSites", "81800,148700,-3400,12,10"));
			for (var entry : invalid.entrySet())
			{
				Files.writeString(input, scale.replaceAll("(?m)^" + entry.getKey() + " =[^\\r\\n]*", entry.getKey() + " = " + entry.getValue()));
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantPolicy.load(input), "Invalid scale key accepted: " + entry.getKey());
			}
			Files.writeString(input, scale.replace("MerchantLifetimeIdentityLimit = 512", "MerchantLifetimeIdentityLimit = 499"));
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantPolicy.load(input), "Lifetime below hard cap accepted.");
			final String site = "81800,148700,-3400,10,10,80";
			final String thousand = "81800,148700,-3400,20,20,80;80550,148700,-3500,20,20,80;83700,149000,-3400,20,10,80";
			Files.writeString(input, scale.replaceAll("(?m)^MerchantSites =[^\\r\\n]*", "MerchantSites = " + thousand));
			PhantomAssertions.assertEquals(1000, PhantomMerchantPolicy.load(input).sites().stream().mapToInt(value -> value.columns() * value.rows()).sum(), "Raw boundary refused; this is not legal-slot proof.");
			for (String sites : new String[] {"", String.join(";", java.util.Collections.nCopies(9, site)), thousand + ";81800,148700,-3400,1,1,80"})
			{
				Files.writeString(input, scale.replaceAll("(?m)^MerchantSites =[^\\r\\n]*", "MerchantSites = " + sites));
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantPolicy.load(input), "Empty/excess sites or >1000 raw accepted.");
			}
			final String prepared = java.util.stream.IntStream.rangeClosed(1, 33).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(","));
			Files.writeString(input, scale.replace("MerchantPreparedOfflineCharacterIds =", "MerchantPreparedOfflineCharacterIds = " + prepared));
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantPolicy.load(input), "Prepared owner bound bypassed.");
		}
		finally { Files.deleteIfExists(input); }
	}

	private static void ledger500()
	{
		var funded = PhantomMerchantBootstrap.Ledger.empty(512, 50_000_000, 500_000_000, 10_000);
		for (int ordinal = 0; ordinal < 500; ordinal++) { funded = funded.reserve(100_000, 1_000_000, Map.of(1835, 20L)); }
		final byte[] bytes = PhantomMerchantBootstrap.encode(funded);
		PhantomAssertions.assertEquals((byte) 1, bytes[0], "Ledger schema version migrated.");
		final var restored = PhantomMerchantBootstrap.decodeLedger(bytes);
		PhantomAssertions.assertEquals(funded, restored, "500 reservations changed on restart.");
		PhantomAssertions.assertEquals(500, restored.nextOrdinal(), "Consumed ordinals were reused.");
		PhantomAssertions.assertEquals(50_000_000L, restored.issuedCapital(), "One-time capital differed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(1, 0, Map.of()), "Spent capital replayed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(0, 1, Map.of()), "Spent stock replayed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> restored.reserve(0, 0, Map.of(1835, 1L)), "Spent item quota replayed.");
		for (int ordinal = 500; ordinal < 512; ordinal++) { funded = funded.reserve(0, 0, Map.of()); }
		final var full = funded;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> full.reserve(0, 0, Map.of()), "Lifetime 512 bypassed.");
		var underfunded = PhantomMerchantBootstrap.Ledger.empty(512, 30_000_000, 500_000_000, 10_000);
		for (int ordinal = 0; ordinal < 300; ordinal++) { underfunded = underfunded.reserve(100_000, 0, Map.of()); }
		final var exhausted = underfunded;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> exhausted.reserve(100_000, 0, Map.of()), "Default 30m funded more than 300 grants.");
		final var role = new PhantomMerchantBootstrap.Role(499, 42, "merchant_084", "A".repeat(43), "Merchant084", 53, 2, 81800, 148700, -3400, 100_000, Map.of(1835, 20L), PhantomMerchantBootstrap.Stage.READY);
		final byte[] roleBytes = PhantomMerchantBootstrap.encode(role);
		PhantomAssertions.assertEquals((byte) 1, roleBytes[0], "Role schema version migrated.");
		PhantomAssertions.assertEquals(role, PhantomMerchantBootstrap.decodeRole(roleBytes).afterUncertainRestart(), "Ready role/position changed or bootstrap replayed on restart.");
		PhantomAssertions.assertEquals("merchant.owner.0499", role.component(), "Large ordinal lost its component key.");
		PhantomAssertions.assertEquals(PhantomMerchantBootstrap.Stage.INCONSISTENT, role.pending().afterUncertainRestart().stage(), "Uncertain large ordinal was promoted.");
		bytes[0] = 2;
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantBootstrap.decodeLedger(bytes), "Unknown ledger version accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomMerchantBootstrap.decodeRole(java.util.Arrays.copyOf(roleBytes, roleBytes.length + 1)), "Trailing role bytes accepted.");
	}

	private static void leases500()
	{
		final var registry = PhantomIdentityLeaseRegistry.getInstance();
		final int before = registry.getActiveLeaseCount();
		final var leases = new ArrayList<PhantomIdentityLeaseRegistry.Lease>();
		try
		{
			for (int id = 840001; id <= 840500; id++)
			{
				final var lease = registry.tryAcquire(id, OwnerKind.PHANTOM);
				PhantomAssertions.assertTrue(lease != null, "Unique capacity lease not acquired."); leases.add(lease);
				PhantomAssertions.assertEquals(null, registry.tryAcquire(id, OwnerKind.PHANTOM), "Duplicate phantom owner accepted.");
				PhantomAssertions.assertEquals(null, registry.tryAcquire(id, OwnerKind.REAL_LOGIN), "Concurrent real-login owner accepted.");
			}
			PhantomAssertions.assertEquals(before + 500, registry.getActiveLeaseCount(), "Capacity lease count differed.");
			final var old = leases.get(499); old.close();
			final var next = registry.tryAcquire(old.objectId(), OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(next != null, "Released lease not reacquired."); leases.add(next); old.close();
			PhantomAssertions.assertEquals(OwnerKind.PHANTOM, registry.getOwnerKind(next.objectId()), "Stale release removed the new owner.");
		}
		finally { leases.forEach(PhantomIdentityLeaseRegistry.Lease::close); }
		PhantomAssertions.assertEquals(before, registry.getActiveLeaseCount(), "Scale lease cleanup leaked ownership.");
	}
}
