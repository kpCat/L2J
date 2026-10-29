/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger;
import org.l2jmobius.tests.phantoms.PhantomPopulation102Native.WorldWitness;

/** DB-free guards on the real108 harness path. Fixtures never attest native logout or exit. */
public final class PhantomStop108Checks
{
    static void registration(boolean running, boolean current, boolean cleanupPrepared, boolean stable)
    {
        require(running && current && !cleanupPrepared && stable, "OWN108 current running root required before cleanup");
    }

    static void terminal(int calls, int outstanding, String evidence)
    {
        require(calls == 1 && outstanding == 0 && evidence.startsWith("event=ON_PLAYER_LOGOUT;semantics=EARNED;"), "OWN108 actual EARNED terminal callback required");
    }

    static void restart(Path receipts, String jar) throws Exception
    {
        for (String name : List.of("phase6.complete", "stopped6.checkpoint", "stop6-world-leases.txt", "exit6.clean"))
        {
            require(Files.isRegularFile(receipts.resolve(name), java.nio.file.LinkOption.NOFOLLOW_LINKS), "OWN108 full clean A required before JDBC");
        }
        require(Files.readString(receipts.resolve("exit6.clean")).equals("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;natural=true;jarSha256=" + jar), "OWN108 natural sameJAR A required before JDBC");
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length == 3 && args[0].equals("market")) { market(Path.of(args[1]), Path.of(args[2])); return; }
        require(args.length == 0, "OWN108 exact DB-free command required");
        final var first = new WorldWitness(1, 10, 100, new Object(), 11);
        final var current = new WorldWitness(2, 20, 200, new Object(), 22);
        final var replaced = new WorldWitness(1, 10, 101, new Object(), 33);
        require(PhantomPopulation102Native.logoutWitness(List.of(first, current), id -> id == 1 ? replaced : current) == current, "Stale first displaced current witness");
        rejects(() -> PhantomPopulation102Native.logoutWitness(List.of(first), id -> replaced));
        rejects(() -> PhantomPopulation102Native.logoutWitness(List.of(current), id -> new WorldWitness(2, 20, 200, current.playerIdentity(), 23)));
        System.out.println("PASS stop108.staleFirstReplacedTokenAllStale");

        registration(true, true, false, true);
        rejects(() -> registration(false, true, false, true));
        rejects(() -> registration(true, false, false, true));
        rejects(() -> registration(true, true, true, true));
        rejects(() -> registration(true, true, false, false));
        final String earned = "event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true";
        terminal(1, 0, earned);
        rejects(() -> terminal(0, 0, earned));
        rejects(() -> terminal(2, 0, earned));
        rejects(() -> terminal(1, 1, earned));
        rejects(() -> terminal(1, 0, "NOT_OBSERVED"));
        rejects(() -> terminal(1, 0, earned.replace("EARNED", "CANCELLABLE")));
        System.out.println("PASS stop108.currentRootCleanupAndEarnedExactlyOnceOutstanding0");

        final Path fixture = Files.createTempDirectory("stop108-dbfree-");
        try
        {
            rejects(() -> restart(fixture, "fixtureJar"));
            for (String name : List.of("phase6.complete", "stopped6.checkpoint", "stop6-world-leases.txt")) { Files.writeString(fixture.resolve(name), "drained=true"); }
            rejects(() -> restart(fixture, "fixtureJar"));
            Files.writeString(fixture.resolve("exit6.clean"), "FORCED_UNCLEAN");
            rejects(() -> restart(fixture, "fixtureJar"));
            Files.writeString(fixture.resolve("exit6.clean"), "exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;natural=true;jarSha256=fixtureJar");
            restart(fixture, "fixtureJar");
            rejects(() -> restart(fixture, "differentJar"));
        }
        finally
        {
            for (String name : List.of("phase6.complete", "stopped6.checkpoint", "stop6-world-leases.txt", "exit6.clean")) { Files.deleteIfExists(fixture.resolve(name)); }
            Files.delete(fixture);
        }
        require(!DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "DB-free108 initialized runtime");
        System.out.println("PASS stop108.noRestartWithoutNaturalSameJarCleanA;JDBC=0;World=NOT_INITIALIZED;NATIVE=NOT_RUN");
    }

    private static java.util.Map<String, String> marketRows(Path file) throws Exception
    {
        require(Files.size(file) <= 64_000_000, "OWN108 bounded captured preimage required");
        final var result = new java.util.TreeMap<String, String>();
        boolean components = false;
        try (var lines = Files.lines(file))
        {
            for (var iterator = lines.iterator(); iterator.hasNext();)
            {
                final String line = iterator.next();
                if (line.startsWith("SLICE:")) { components = line.equals("SLICE:components"); continue; }
                if (!components) { continue; }
                final var fields = line.split("\t", -1);
                require(fields.length == 5, "OWN108 component frame differs");
                if (fields[1].equals(PhantomMerchantMarketLedger.COMPONENT) || fields[1].startsWith(PhantomMerchantMarketLedger.RECEIPT_PREFIX))
                { require(result.put(fields[0] + "\t" + fields[1], fields[4]) == null, "OWN108 duplicate market component"); }
            }
        }
        return result;
    }

    private static void market(Path before, Path after) throws Exception
    {
        final var baseline = marketRows(before); final var current = marketRows(after);
        require(baseline.size() == 72 && baseline.equals(current), "OWN108 market receipts/ledger changed; separate incremental-grant audit required");
        long funds = 0, stockValue = 0; int funding = 0, stock = 0, enchanted = 0, ledgers = 0;
        for (var entry : current.entrySet())
        {
            final var bytes = java.util.HexFormat.of().parseHex(entry.getValue());
            if (entry.getKey().endsWith("\t" + PhantomMerchantMarketLedger.COMPONENT))
            {
                final var ledger = PhantomMerchantMarketLedger.decodeLedger(bytes);
                require(ledger.sequence() == 71 && ledger.fundingIssued() == 3_150_000_000L && ledger.stockIssued() == 767112, "OWN108 original ledger totals differ");
                ledgers++; continue;
            }
            final var receipt = PhantomMerchantMarketLedger.decodeReceipt(bytes);
            require(!receipt.quarantined() && entry.getKey().endsWith("\t" + receipt.component()), "OWN108 inconsistent grant sequence/state");
            if (receipt.category().equals("FUNDING")) { require(receipt.value() == 50_000_000L, "OWN108 original funding grant differs"); funds += receipt.value(); funding++; }
            else { stock++; stockValue += receipt.value(); if (receipt.enchant() > 0) { enchanted++; } }
        }
        require(ledgers == 1 && funding == 63 && funds == 3_150_000_000L && stock == 8 && enchanted == 4 && stockValue == 767112, "OWN108 original grants missing or replayed");
        require(!DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "Offline market audit started runtime");
        System.out.println("DATA108_MARKET_PASS funding=63x50000000;funds=3150000000;stock=8;enchanted=4;stockValue=767112;receipts=71;ledgerAndReceipts=EXACT;incrementalGrants=0;JDBC=0");
    }

    private static void rejects(PhantomLease105Checks.Step action) throws Exception
    {
        boolean rejected = false;
        try { action.run(); } catch (AssertionError expected) { rejected = true; }
        require(rejected, "OWN108 negative guard accepted");
    }
    private static void require(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
}
