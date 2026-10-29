/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.List;

/** DB-free checks of the allocator actually used by native merchant BUY plans. */
public final class PhantomLivingMarket107Checks
{
	public static void main(String[] args)
	{
		if (args.length == 2 && args[0].equals("grants")) { grants(java.nio.file.Path.of(args[1])); return; }
		if (args.length == 2 && args[0].equals("rotation")) { rotation(java.nio.file.Path.of(args[1])); return; }
		if (args.length == 2 && args[0].equals("epoch")) { epoch(java.nio.file.Path.of(args[1])); return; }
		if (args.length == 2 && args[0].equals("policy")) { policy(java.nio.file.Path.of(args[1])); return; }
		if (args.length == 1 && args[0].equals("circulation"))
		{
			final var demand = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Buying", List.of(new PhantomStorePlan.Line(1864, 1864, 1, 10)), Long.MAX_VALUE);
			check(PhantomOfflineMerchantService.selectPlan(0, () -> null, () -> null, () -> demand) == demand, "Sold-out SELL could not use an available safely funded BUY plan");
			check(PhantomOfflineMerchantService.selectPlan(0, () -> null, () -> null, () -> null) == null, "Empty stock/wallet synthesized an OPEN store");
			System.out.println("OWN107_CIRCULATION_PASS SELL_TO_BUY=PASS;NO_PLAN=IDLE;DB_WRITES=0;NATIVE=NOT_RUN"); return;
		}
		if (args.length == 1 && args[0].equals("catalog"))
		{
			final var first = new java.util.ArrayList<PhantomMerchantCatalog.Entry>();
			final var second = new java.util.ArrayList<PhantomMerchantCatalog.Entry>();
			for (int id = 1; id <= 100; id++)
			{
				first.add(new PhantomMerchantCatalog.Entry(null, quote(id, 10), "MATERIAL_RESOURCE.NONE"));
				second.add(new PhantomMerchantCatalog.Entry(null, quote(id + 1000, 10), "EQUIPMENT_PIECE.S84"));
			}
			final var all = PhantomMerchantCatalog.selectEntries(List.of(first, second));
			check(all.size() == 200, "CLASS107: eligible universe silently truncated to " + all.size());
			check(all.get(199).quote().itemId() == 1100, "Last eligible source ID lost");
			System.out.println("OWN107_CATALOG_UNIVERSE_PASS entries=200;DB_WRITES=0;NATIVE=NOT_RUN");
			return;
		}
		if (args.length != 0 && !(args.length == 1 && args[0].equals("buy"))) { throw new IllegalArgumentException("Exact buy/catalog/circulation or grants/rotation/epoch/policy plus policy path required."); }
		final var candidates = List.of(quote(9546, 12500), quote(9547, 12500), quote(9548, 12500));
		final var lines = PhantomOfflineMerchantService.allocateBuyLines(candidates, 100000, 20, 20, 100, 0);
		check(lines.size() == 3, "ROOT_BUY_63: three affordable native candidates must not be divided across twenty empty slots; actual=" + lines);
		check(lines.stream().allMatch(line -> line.count() >= 1), "Affordable candidate lost its first unit");
		check(reserve(lines) <= 100000, "BUY reserve exceeds actual wallet");
		check(PhantomOfflineMerchantService.allocateBuyLines(candidates, 0, 20, 20, 100, 0).isEmpty(), "Zero wallet minted BUY capacity");
		check(PhantomOfflineMerchantService.allocateBuyLines(candidates, 12499, 20, 20, 100, 0).isEmpty(), "Unaffordable unit accepted");
		check(reserve(PhantomOfflineMerchantService.allocateBuyLines(candidates, 12500, 20, 20, 100, 0)) == 12500, "Single affordable unit starved");
		check(PhantomOfflineMerchantService.allocateBuyLines(candidates, 100000, 20, 1, 100, 0).size() == 1, "Native BUY limit ignored");
		check(PhantomOfflineMerchantService.allocateBuyLines(candidates, 100000, 1, 20, 100, 1).getFirst().itemId() == 9547, "Owner cursor did not diversify candidates");
		check(PhantomOfflineMerchantService.allocateBuyLines(List.of(), 100000, 20, 20, 100, 0).isEmpty(), "Empty pool synthesized offers");
		final var duplicate = List.of(quote(9546, 12500), quote(9546, 12500), quote(9547, 12500));
		check(PhantomOfflineMerchantService.allocateBuyLines(duplicate, 100000, 20, 20, 1, 0).size() == 2, "Duplicate BUY item ID");
		final var invalid = List.of(new PhantomPrivateMarketPricingAuthority.ItemQuote(1, false, true, 1, 2, 1, "STALE"), quote(2, 0), quote(3, Long.MAX_VALUE));
		check(PhantomOfflineMerchantService.allocateBuyLines(invalid, 100000, 20, 20, 100, 0).isEmpty(), "Stale/zero/unaffordable quote accepted");
		check(reserve(PhantomOfflineMerchantService.allocateBuyLines(List.of(quote(1, Long.MAX_VALUE)), Long.MAX_VALUE, 20, 20, 1000, 0)) == Long.MAX_VALUE, "Safe large quote overflowed or starved");
		System.out.println("OWN107_BUY_PASS cases=11;ROOT_BUY_63=GREEN;reserve=" + reserve(lines) + ";DB_WRITES=0;NATIVE=NOT_RUN");
	}
	private static PhantomPrivateMarketPricingAuthority.ItemQuote quote(int id, long bid)
	{
		return new PhantomPrivateMarketPricingAuthority.ItemQuote(id, true, true, bid, bid, bid, "TEST_SOURCE_BOUNDARY");
	}
	private static long reserve(List<PhantomStorePlan.Line> lines)
	{
		long value = 0;
		for (var line : lines) { value = Math.addExact(value, Math.multiplyExact(line.count(), line.price())); }
		return value;
	}
	private static void check(boolean condition, String reason) { if (!condition) { throw new AssertionError(reason); } }
	private static void grants(java.nio.file.Path file)
	{
		final var policy = PhantomMerchantMarketPolicy.load(file);
		var ledger = PhantomMerchantMarketLedger.Ledger.empty(policy);
		long issued = 0;
		for (int index = 0; index < 5; index++)
		{
			final var intent = funding(index + 1, 50_000_000, 1_000_000L + index * 1_800_000L);
			ledger = ledger.reserve(intent, new PhantomMerchantMarketLedger.Usage(issued, 0, 0, 0, 0, index, index == 0 ? 0 : intent.createdAt() - 1_800_000));
			issued += 50_000_000;
			check(ledger.fundingIssued() == issued, "Finite operating grant was not reserved before delivery");
			check(intent.quarantined() && intent.withState(PhantomMerchantMarketLedger.State.INCONSISTENT, 0).quarantined(), "Unknown delivery remained tradeable");
			check(!intent.withState(PhantomMerchantMarketLedger.State.COMMITTED, 123).quarantined(), "Committed native proof remained uncertain");
			check(intent.equals(PhantomMerchantMarketLedger.decodeReceipt(PhantomMerchantMarketLedger.encode(intent))), "Receipt replay changed intent");
		}
		final var full = ledger;
		reject(() -> full.reserve(funding(6, 50_000_000, 20_000_000), new PhantomMerchantMarketLedger.Usage(250_000_000, 0, 0, 0, 0, 5, 0)), "Per-owner lifetime cap bypassed");
		reject(() -> full.reserve(funding(5, 1, 20_000_000), new PhantomMerchantMarketLedger.Usage(0, 0, 0, 0, 0, 0, 0)), "Receipt sequence replay accepted");
		reject(() -> full.reserve(funding(6, 50_000_000, 20_000_000), new PhantomMerchantMarketLedger.Usage(0, 0, 0, 0, 0, 0, 19_999_999)), "Funding cooldown bypassed");
		check(full.equals(PhantomMerchantMarketLedger.decodeLedger(PhantomMerchantMarketLedger.encode(full))), "SameDB market ledger reset");
		final var exhausted = new PhantomMerchantMarketLedger.Ledger(1, policy, 5, policy.fundingGlobal(), 0, 0, 0);
		reject(() -> exhausted.reserve(funding(6, 1, 20_000_000), new PhantomMerchantMarketLedger.Usage(0, 0, 0, 0, 0, 0, 0)), "Global lifetime cap bypassed");
		final var stock = new PhantomMerchantMarketLedger.Receipt(6, 0, 42, PhantomMerchantMarketLedger.State.PREPARED, 15634, 1, 3, 100_000_000, "FINISHED_GEAR", org.l2jmobius.gameserver.model.item.type.CrystalType.S84, 20_000_000, 100000, 0, 0, 0, "OWNER_PROVISIONED;NATIVE_EXPECTED_ENCHANT_COST");
		reject(() -> full.reserve(stock, new PhantomMerchantMarketLedger.Usage(0, 0, policy.stockPerItem(), 0, 0, 0, 0)), "Per-item stock cap bypassed");
		reject(() -> full.reserve(stock, new PhantomMerchantMarketLedger.Usage(0, policy.stockPerOwner(), 0, 0, 0, 0, 0)), "Per-owner stock value cap bypassed");
		System.out.println("OWN107_GRANTS_PASS fundingReserved=" + full.fundingIssued() + ";quarantineFlag=PASS;replay=DENIED;DB_WRITES=0;NATIVE=NOT_RUN");
	}
	private static PhantomMerchantMarketLedger.Receipt funding(long sequence, long quantity, long now)
	{
		return new PhantomMerchantMarketLedger.Receipt(sequence, 0, 42, PhantomMerchantMarketLedger.State.PREPARED, 57, quantity, 0, quantity, "FUNDING", org.l2jmobius.gameserver.model.item.type.CrystalType.NONE, now, 100000, 100000, 123, 0, "OWNER_OPERATING_CAPITAL");
	}
	private static void reject(Runnable action, String reason)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(reason);
	}
	private static void rotation(java.nio.file.Path file)
	{
		final var p = PhantomMerchantMarketPolicy.load(file);
		check(!PhantomOfflineMerchantService.assortmentDue(1000, 31000, 1800, false, false), "30-second recheck rotated an unsold assortment");
		check(PhantomOfflineMerchantService.assortmentDue(1000, 1801000, 1800, false, false), "1800-second assortment did not become due");
		check(!PhantomOfflineMerchantService.assortmentDue(1000, 1801000, 1800, true, false), "Observed store rotated");
		check(!PhantomOfflineMerchantService.assortmentDue(1000, 1801000, 1800, false, true), "Pending trade/craft rotated");
		check(!PhantomMerchantMarketProvision.stockRequired(true, true, true), "Unchanged adequate owned stock caused another grant on rotation");
		check(!PhantomMerchantMarketProvision.stockRequired(true, false, false), "Owned assortment ignored when gear was not requested");
		check(PhantomMerchantMarketProvision.stockRequired(false, false, false) && PhantomMerchantMarketProvision.stockRequired(true, true, false), "Empty stock or authorized missing gear category refused");
		System.out.println("OWN107_ROTATION_PASS poll30=UNCHANGED;refresh1800=PASS;observerPending=DEFER;adequateOwnedStock=GRANT0;DB_WRITES=0;NATIVE=NOT_RUN");
	}
	private static void epoch(java.nio.file.Path file)
	{
		final var p = PhantomMerchantMarketPolicy.load(file);
		final var changed = new PhantomMerchantMarketPolicy(3600, p.gearShare(), p.gearGrades(), p.fundingEnabled(), p.fundingGrant(), p.fundingCooldown(), p.fundingPerOwner(), p.fundingGlobal(), p.stockEnabled(), p.stockGlobal(), p.stockPerOwner(), p.stockPerItem(), p.stockPerGrade(), p.gearPerGrade(), p.stockStack(), p.enchantMax(), p.enchantedPerEpoch(), p.grantsPerOwner(), p.grantsGlobal());
		final long now = 1_800_000_100_000L;
		final var old = new PhantomMerchantMarketLedger.Ledger(1, p, 1, 0, 100_000_000, now / 1_800_000L * 1_800_000L, 1);
		final var pending = new PhantomMerchantMarketLedger.Receipt(1, 0, 42, PhantomMerchantMarketLedger.State.PREPARED, 15634, 1, 4, 100_000_000, "FINISHED_GEAR", org.l2jmobius.gameserver.model.item.type.CrystalType.S84, now, 100000, 0, 0, 0, "OWNER_PROVISIONED");
		final var shifted = old.withPolicy(changed, List.of(pending), now);
		final var intent = new PhantomMerchantMarketLedger.Receipt(2, 0, 42, PhantomMerchantMarketLedger.State.PREPARED, 15634, 1, 4, 100_000_000, "FINISHED_GEAR", org.l2jmobius.gameserver.model.item.type.CrystalType.S84, now, 100000, 0, 0, 0, "OWNER_PROVISIONED");
		final var next = shifted.reserve(intent, new PhantomMerchantMarketLedger.Usage(0, 100_000_000, 1, 1, 1, 1, 0));
		check(next.stockIssued() == 200_000_000 && next.epochEnchanted() == 2, "Policy interval changed or reset finite enchant usage");
		System.out.println("OWN107_EPOCH_PASS interval1800to3600=SAFE;priorReservedEnchant=COUNTED;lifetime=UNCHANGED;DB_WRITES=0;NATIVE=NOT_RUN");
	}
	private static void policy(java.nio.file.Path file)
	{
		try
		{
			final byte[] before = java.nio.file.Files.readAllBytes(file);
			final String text = java.nio.file.Files.readString(file);
			final var directory = java.nio.file.Files.createTempDirectory(file.toAbsolutePath().getParent(), "market107-policy-");
			for (String[] mutation : new String[][] {{"MerchantBuyFundingGrantAdena", "-1"}, {"MerchantAssortmentRefreshSeconds", "30"}, {"MerchantMarketStockMaxEnchant", "26"}, {"MerchantBuyFundingEnabled", "sometimes"}, {"MerchantMarketGearGrades", "B,UNKNOWN"}, {"MerchantBuyFundingGlobalLifetimeCap", "1000000000001"}})
			{
				final var invalid = directory.resolve(mutation[0] + ".ini");
				final String changed = text.replaceFirst("(?m)^" + mutation[0] + "[ \\t]*=[^\\r\\n]*", java.util.regex.Matcher.quoteReplacement(mutation[0] + " = " + mutation[1]));
				java.nio.file.Files.writeString(invalid, changed);
				reject(() -> PhantomMerchantMarketPolicy.load(invalid), "Unsafe market policy accepted: " + mutation[0]);
				java.nio.file.Files.delete(invalid);
			}
			java.nio.file.Files.delete(directory);
			check(java.util.Arrays.equals(before, java.nio.file.Files.readAllBytes(file)), "Policy negative checks changed owner configuration");
			System.out.println("OWN107_POLICY_PASS malformedOrUnsafe=REJECTED6;sourceBytes=UNCHANGED;DB_WRITES=0;NATIVE=NOT_RUN");
		}
		catch (java.io.IOException failure) { throw new IllegalStateException("Policy fixture IO failed", failure); }
	}
}
