/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Pre-native census provenance and isolated108R authority. No server or JDBC initialization. */
public final class PhantomStop108RChecks
{
    static void earned(int calls, int outstanding, String evidence)
    {
        PhantomStop108Checks.terminal(calls, outstanding, evidence);
        require(evidence.contains(";ownerCurrent=true;"), "OWN108R actual logout owner was not current");
    }
    static void census(Path stage, String classpath) throws Exception
    {
        PhantomTask102Authority.physical(stage);
        final var smoke = PhantomTask102Lane.read(stage.resolve("private/smoke108R.complete"));
        final Path pins = stage.resolve("private/census108R-classes.tsv");
        PhantomTask102Authority.physical(pins);
        require(classpath.equals(smoke.getProperty("classpath")) && PhantomTask102Lane.hash(pins).equals(smoke.getProperty("classesSha256")), "OWN108R census classpath/pins drift");
        final var seen = new java.util.HashSet<String>();
        for (String line : Files.readAllLines(pins))
        {
            final var fields = line.split("\t", -1);
            require(fields.length == 2 && Set.of("Task104Census.class", "Task105Census.class").contains(fields[0]) && seen.add(fields[0]), "OWN108R exact census pin names required");
            final Path file = stage.resolve("build/phantom-test/bin/" + fields[0]);
            PhantomTask102Authority.physical(file);
            require(Files.size(file) > 0 && PhantomTask102Lane.hash(file).equals(fields[1]), "OWN108R census bytecode drift");
        }
        require(seen.size() == 2, "OWN108R both census classes required");
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length == 3 && args[0].equals("census")) { census(Path.of(args[1]).toRealPath(), args[2]); System.out.println("PASS census108R.exactClasspathAndBothClassHashes;JDBC=0"); return; }
        require(args.length == 0, "OWN108R exact DB-free command required");
        final Path stage = Files.createTempDirectory("stop108R-dbfree-").toRealPath();
        final Path privateRoot = Files.createDirectory(stage.resolve("private"));
        final Path bin = Files.createDirectories(stage.resolve("build/phantom-test/bin"));
        final var created = new java.util.ArrayList<Path>();
        try
        {
            require(!PhantomTask102Lane.recovery108R(stage), "Unselected108R authority");
            final Path authority = privateRoot.resolve("recovery108R.properties"); created.add(authority); Files.writeString(authority, "fixture");
            require(PhantomTask102Lane.recovery108R(stage), "Missing108R authority");
            for (int prior = 103; prior <= 108; prior++)
            {
                final Path conflict = privateRoot.resolve("recovery" + prior + ".properties"); created.add(conflict); Files.writeString(conflict, "fixture");
                rejects(() -> PhantomTask102Lane.recovery108R(stage)); Files.delete(conflict);
            }
            System.out.println("PASS stop108R.oldNamespaceConflicts103Through108;JDBC=0");
            final String earned = "event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;outstanding=1";
            earned(1, 0, earned);
            rejects(() -> earned(1, 0, earned.replace("ownerCurrent=true", "ownerCurrent=false")));
            rejects(() -> earned(0, 0, earned));
            rejects(() -> earned(1, 1, earned));
            for (String name : List.of("Task104Census.class", "Task105Census.class")) { final Path file = bin.resolve(name); created.add(file); Files.writeString(file, "fixture-" + name); }
            final Path pins = privateRoot.resolve("census108R-classes.tsv"); created.add(pins);
            final String rows = "Task104Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task104Census.class")) + "\nTask105Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task105Census.class"));
            Files.writeString(pins, rows);
            final Path smoke = privateRoot.resolve("smoke108R.complete"); created.add(smoke);
            final String classpath = "C:/fixture/bin;C:/fixture/resources;C:/fixture/libs/*";
            Files.writeString(smoke, "classpath=" + classpath.replace('/', '\\') + "\nclassesSha256=" + PhantomTask102Lane.hash(pins));
            rejects(() -> census(stage, classpath));
            Files.writeString(smoke, "classpath=" + classpath + "\nclassesSha256=" + PhantomTask102Lane.hash(pins));
            census(stage, classpath); rejects(() -> census(stage, "fallbackCP"));
            for (String name : List.of("Task104Census.class", "Task105Census.class"))
            {
                final Path file = bin.resolve(name); final byte[] bytes = Files.readAllBytes(file);
                Files.delete(file); rejects(() -> census(stage, classpath));
                Files.writeString(file, "wrongSHA"); rejects(() -> census(stage, classpath));
                Files.write(file, bytes); census(stage, classpath);
            }
            System.out.println("PASS stop108R.missing104Missing105WrongShaWrongClasspath;JDBC=0;NATIVE=NOT_RUN");
        }
        finally
        {
            for (Path file : created.reversed()) { Files.deleteIfExists(file); }
            Files.delete(bin); Files.delete(bin.getParent()); Files.delete(bin.getParent().getParent()); Files.delete(privateRoot); Files.delete(stage);
        }
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized() && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "DB-free108R initialized runtime");
    }
    private static void rejects(PhantomLease105Checks.Step action) throws Exception
    {
        try { action.run(); } catch (AssertionError | IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError("OWN108R negative accepted");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
