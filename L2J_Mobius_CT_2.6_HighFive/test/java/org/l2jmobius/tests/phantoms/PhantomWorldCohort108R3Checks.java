/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.tests.phantoms.PhantomPopulation102Native.WorldWitness;

/** Test-only cohort outcome. A failed World gate must not preempt an owned STOP witness. */
public final class PhantomWorldCohort108R3Checks
{
    record WorldResult(boolean pass, int first, int second, int stable, String reason) { }
    static WorldResult evaluate(List<WorldWitness> first, List<WorldWitness> second)
    {
        final var current = new TreeMap<Long, WorldWitness>();
        require(first.stream().map(WorldWitness::profileId).distinct().count() == first.size(), "duplicate first World identity");
        for (var value : second) { require(current.put(value.profileId(), value) == null, "duplicate World identity"); }
        int stable = 0;
        for (var before : first)
        {
            final var after = current.get(before.profileId());
            if (after != null && before.objectId() == after.objectId() && before.epoch() == after.epoch()
                && before.playerIdentity() == after.playerIdentity() && before.leaseToken() == after.leaseToken()) { stable++; }
        }
        final boolean pass = first.size() == 16 && second.size() == 16 && stable == 16;
        return new WorldResult(pass, first.size(), second.size(), stable, pass ? "STABLE16" : "SHORTFALL_OR_CHURN");
    }
    @FunctionalInterface
    interface WitnessReceipt { void write(boolean installed, String reason) throws Exception; }
    static void beforeGate(WorldResult result, PhantomLease105Checks.Step witness, WitnessReceipt attempt, PhantomLease105Checks.Step receipt) throws Exception
    {
        // Shortfall is durable evidence, not permission to bypass an earned native witness or STOP.
        try
        {
            try { witness.run(); }
            catch (Exception | AssertionError failure)
            {
                final String reason = failure.getClass().getName() + ":" + String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ');
                attempt.write(false, reason.substring(0, Math.min(1024, reason.length())));
                throw failure;
            }
            attempt.write(true, "PlayerNativeWork.run;currentOwned=true;installed=true");
        }
        finally { receipt.run(); }
    }
    static void restart(Path stage, String jar) throws Exception
    {
        final Path receipts = stage.resolve("private/receipts108R3");
        require(Files.readString(receipts.resolve("world6.pass")).startsWith("WORLD_GATE=PASS;first=16;second=16;stable=16;"), "full World16 A required before JDBC");
        require(Files.exists(receipts.resolve("stop6.natural")), "natural STOP A required before JDBC");
        PhantomStop108Checks.restart(receipts, jar);
        final var clean = PhantomTask102Lane.read(receipts.resolve("clean6.properties"));
        require(PhantomTask102Lane.hash(receipts.resolve("postflight6.pass.json")).equals(clean.getProperty("postflightSha256"))
            && PhantomTask102Lane.hash(stage.getParent().resolve("review6.private.json")).equals(clean.getProperty("reviewSha256"))
            && PhantomTask102Lane.hash(receipts.resolve("exit6.clean")).equals(clean.getProperty("exitSha256")), "independent accepted A hash chain required before JDBC");
    }
    static void census(Path stage, String classpath) throws Exception
    {
        final var smoke = PhantomTask102Lane.read(stage.resolve("private/smoke108R3.complete"));
        final Path pins = stage.resolve("private/census108R3-classes.tsv");
        require(classpath.equals(smoke.getProperty("classpath")) && PhantomTask102Lane.hash(pins).equals(smoke.getProperty("classesSha256")), "census classpath/pins drift");
        final var seen = new java.util.HashSet<String>();
        for (String line : Files.readAllLines(pins))
        {
            final var fields = line.split("\t", -1);
            require(fields.length == 2 && java.util.Set.of("Task104Census.class", "Task105Census.class").contains(fields[0]) && seen.add(fields[0]), "both exact census classes required");
            final Path file = stage.resolve("build/phantom-test/bin/" + fields[0]);
            PhantomTask102Authority.physical(file);
            require(Files.size(file) > 0 && PhantomTask102Lane.hash(file).equals(fields[1]), "census bytecode drift");
        }
        require(seen.size() == 2, "both nested census classes required");
        if (Files.exists(stage.resolve("private/native.properties")))
        {
            PhantomTask102Authority.verifyNativeInputs(stage, PhantomTask102Lane.read(stage.resolve("private/native.properties")).getProperty("nativeInputsSha256"));
        }
    }
    static void capture(Path receipts, int phase, String cut, List<Player> observers, List<WorldWitness> cohort, int best, long noProgressMillis, String reason) throws Exception
    {
        require(java.util.Set.of("start", "plateau", "final").contains(cut), "expanded snapshot budget");
        final Object configured = PhantomPopulation102Native.field105(null, PhantomSystem.class, "_configuredInstance");
        require(configured != null, "diagnostic runtime absent");
        final var locality = (PhantomHumanLocalityControl) PhantomPopulation102Native.field105(configured, PhantomSystem.class, "_humanLocality");
        final var physical = locality.physicalSnapshot();
        final var selected = new java.util.LinkedHashSet<Long>();
        for (var value : cohort) { if (selected.size() < 64) { selected.add(value.profileId()); } }
        for (long id : new java.util.TreeSet<>(physical.positionRevisions().keySet())) { if (selected.size() >= 64) { break; } selected.add(id); }
        final var lines = new ArrayList<String>();
        final var now = java.time.Instant.now();
        final var timezone = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().populationTimeZone();
        lines.add("SUMMARY\tutc=" + now + ";jvmZone=" + java.time.ZoneId.systemDefault() + ";populationZone=" + timezone + ";calendar=" + now.atZone(timezone)
            + ";best=" + best + ";actual=" + cohort.size() + ";noProgressMillis=" + noProgressMillis + ";cutoff=" + reason
            + ";physical=" + physical.positionRevisions().size() + ";facts=" + physical.facts().size() + ";demandAgeMillis=" + Math.max(0, now.toEpochMilli() - physical.timestampMillis()) + ";overflow=" + physical.overflow());
        lines.add("STATUS\t" + PhantomSystem.operatorStatus());
        lines.add("PREPARATION\t" + PhantomSystem.operatorReadinessProgress(selected.isEmpty() ? 0 : selected.iterator().next()));
        lines.add("HUMANS\t" + PhantomSystem.onlineHumanPoints());
        for (var observer : observers)
        {
            lines.add("OBSERVER\t" + observer.getObjectId() + ";reference=" + System.identityHashCode(observer) + ";worldCurrent=" + (World.getInstance().getPlayer(observer.getObjectId()) == observer)
                + ";online=" + observer.isOnline() + ";x=" + observer.getX() + ";y=" + observer.getY() + ";z=" + observer.getZ() + ";instance=" + observer.getInstanceId()
                + ";lease=" + org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(observer.getObjectId()));
        }
        for (long id : selected)
        {
            lines.add("PROFILE\t" + id + "\t" + PhantomSystem.operatorAdmissionProfile(id) + "\tlocality=" + PhantomSystem.operatorLocalityTarget(id)
                + "\tprogress=" + PhantomSystem.operatorReadinessProgress(id));
        }
        for (var value : cohort)
        {
            final var player = (Player) value.playerIdentity();
            lines.add("WORLD\t" + value.profileId() + ";object=" + value.objectId() + ";epoch=" + value.epoch() + ";token=" + value.leaseToken()
                + ";reference=" + System.identityHashCode(player) + ";worldCurrent=" + (World.getInstance().getPlayer(value.objectId()) == player)
                + ";headless=" + player.hasHeadlessOutboundSession() + ";visible=" + observers.stream().map(player::isVisibleFor).toList());
        }
        Files.write(receipts.resolve("cohort108R3-" + phase + "-" + cut + ".private.tsv"), lines, StandardOpenOption.CREATE_NEW);
    }
    private static List<WorldWitness> cohort(int size)
    {
        final var result = new ArrayList<WorldWitness>();
        for (int i = 1; i <= size; i++) { result.add(new WorldWitness(i, i, i, new Object(), i)); }
        return List.copyOf(result);
    }
    public static void main(String[] args) throws Exception
    {
        if (args.length == 3 && args[0].equals("census")) { census(Path.of(args[1]).toRealPath(), args[2]); System.out.println("COHORT108R3_CENSUS_PASS exactCP;bothHashes;JDBC=0"); return; }
        require(args.length == 0, "exact DB-free command required");
        final var first = cohort(16);
        require(evaluate(first, first).pass(), "actual stable16 rejected");
        final var nine = first.subList(0, 9);
        final var lateTen = first.subList(0, 10);
        require(!evaluate(nine, nine).pass() && !evaluate(nine, lateTen).pass(), "9/9 or late10 accepted");
        final var changed = new ArrayList<>(first);
        final var old = first.getFirst();
        changed.set(0, new WorldWitness(old.profileId(), old.objectId(), old.epoch(), old.playerIdentity(), old.leaseToken() + 1));
        require(!evaluate(first, changed).pass(), "replaced lease accepted");
        changed.set(0, new WorldWitness(old.profileId(), old.objectId(), old.epoch() + 1, new Object(), old.leaseToken()));
        require(!evaluate(first, changed).pass(), "replaced epoch/Player accepted");
        final var transcript = new ArrayList<String>();
        try
        {
            beforeGate(evaluate(nine, lateTen), () -> transcript.add("witness"), (installed, reason) -> transcript.add(installed ? "WITNESS_PASS" : "WITNESS_FAIL"), () -> transcript.add("WORLD_FAIL"));
        }
        finally
        {
            PhantomPopulation102Native.finishBoundary105(true,
                () -> require(transcript.contains("witness"), "shortfall skipped genuine witness attempt"),
                () -> transcript.add("leases"), () -> transcript.add("checkpoint"), () -> transcript.add("pools"),
                () -> transcript.add("database"), () -> transcript.add("blocked"));
        }
        require(transcript.equals(List.of("witness", "WITNESS_PASS", "WORLD_FAIL", "leases", "checkpoint", "pools", "database")), "shortfall did not preserve STOP ordering");
        final var refused = new ArrayList<String>();
        rejects(() -> beforeGate(evaluate(nine, nine), () -> { throw new AssertionError("stale owner"); },
            (installed, reason) -> { require(!installed && reason.equals("java.lang.AssertionError:stale owner"), "precise witness refusal lost"); refused.add("WITNESS_FAIL"); }, () -> refused.add("WORLD_FAIL")));
        require(refused.equals(List.of("WITNESS_FAIL", "WORLD_FAIL")), "refused witness lost World failure");
        authorityChecks();
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized()
            && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "DB-free checks started runtime");
        System.out.println("COHORT108R3_PASS stable16;shortfall9Late10;churn;witnessBeforeWorld;canonicalStop;JDBC=0;NATIVE=NOT_RUN");
    }
    private static void authorityChecks() throws Exception
    {
        final Path stage = Files.createTempDirectory("cohort108R3-guards-").toRealPath();
        final Path privateRoot = Files.createDirectory(stage.resolve("private"));
        final Path selected = privateRoot.resolve("recovery108R3.properties");
        final Path bin = Files.createDirectories(stage.resolve("build/phantom-test/bin"));
        try
        {
            require(!PhantomTask102Lane.recovery108R3(stage), "unselected authority");
            Files.writeString(selected, "fixture");
            require(PhantomTask102Lane.recovery108R3(stage), "108R3 namespace absent");
            for (String prior : List.of("103", "104", "105", "106", "107", "108", "108R", "108R2"))
            {
                final Path marker = privateRoot.resolve("recovery" + prior + ".properties");
                Files.writeString(marker, "fixture");
                try { rejects(() -> PhantomTask102Lane.recovery108R3(stage)); } finally { Files.delete(marker); }
            }
            rejects(() -> restart(stage, "fixture"));
            final String cp = "fixtureCP";
            final Path pins = privateRoot.resolve("census108R3-classes.tsv");
            final Path smoke = privateRoot.resolve("smoke108R3.complete");
            for (String name : List.of("Task104Census.class", "Task105Census.class")) { Files.writeString(bin.resolve(name), "fixture-" + name); }
            Files.writeString(pins, "Task104Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task104Census.class")) + "\nTask105Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task105Census.class")));
            Files.writeString(smoke, "classpath=" + cp + "\nclassesSha256=" + PhantomTask102Lane.hash(pins));
            census(stage, cp); rejects(() -> census(stage, "wrongCP"));
            for (String name : List.of("Task104Census.class", "Task105Census.class"))
            {
                final Path file = bin.resolve(name); final byte[] bytes = Files.readAllBytes(file);
                Files.delete(file); rejects(() -> census(stage, cp));
                Files.writeString(file, "wrongSHA"); rejects(() -> census(stage, cp));
                Files.write(file, bytes);
            }
            census(stage, cp);
        }
        finally
        {
            for (String name : List.of("smoke108R3.complete", "census108R3-classes.tsv", "recovery108R3.properties")) { Files.deleteIfExists(privateRoot.resolve(name)); }
            for (String name : List.of("Task104Census.class", "Task105Census.class")) { Files.deleteIfExists(bin.resolve(name)); }
            Files.delete(bin); Files.delete(bin.getParent()); Files.delete(bin.getParent().getParent()); Files.delete(privateRoot); Files.delete(stage);
        }
    }
    private static void rejects(PhantomLease105Checks.Step action) throws Exception
    {
        try { action.run(); } catch (AssertionError | IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError("OWN108R3 negative accepted");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError("OWN108R3 " + message); } }
}
