/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Properties;
import java.util.Set;

/** Shared pre-JDBC authority for the create-only probe and native101 entrypoint. */
public final class PhantomTask101Authority
{
    private PhantomTask101Authority() { }
    public static void verify(Path stage, Path source, Properties owner, boolean nativeRun) throws Exception
    {
        physical(stage); physical(source);
        final Path module = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
        if (!module.resolve("dist/game").equals(Path.of("").toRealPath())) { throw new IllegalArgumentException("OWN101 wrong cwd before JDBC."); }
        final Set<String> allowed = nativeRun ? Set.of("phantom.task101.native", "phantom.task101.stage", "phantom.task101.uuid", "phantom.module.root", "phantom.test.config") : Set.of();
        for (String key : System.getProperties().stringPropertyNames()) { if (key.startsWith("phantom.") && !allowed.contains(key)) { throw new IllegalArgumentException("OWN101 foreign authority before JDBC."); } }
        final Path whoami = Path.of(System.getenv("SystemRoot"), "System32", "whoami.exe").toRealPath();
        final var process = new ProcessBuilder(whoami.toString(), "/user", "/fo", "csv", "/nh").redirectErrorStream(true).start();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroy(); throw new IllegalArgumentException("OWN101 SID lookup unavailable."); }
        final var identity = java.util.regex.Pattern.compile("\"([^\"]+)\",\"(S-1-[0-9-]+)\"").matcher(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
        if (process.exitValue() != 0 || !identity.matches() || !identity.group(2).equals(owner.getProperty("ownerSid"))) { throw new IllegalArgumentException("OWN101 wrong execution SID before JDBC."); }
        final var lookup = stage.getFileSystem().getUserPrincipalLookupService();
        final var expected = lookup.lookupPrincipalByName(identity.group(1)); final var system = lookup.lookupPrincipalByName("NT AUTHORITY\\SYSTEM");
        final var acl = Files.getFileAttributeView(stage, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null || !expected.equals(acl.getOwner()) || !expected.equals(Files.getOwner(source))) { throw new IllegalArgumentException("OWN101 wrong stage/source owner before JDBC."); }
        boolean ownerRule = false, systemRule = false;
        for (var rule : acl.getAcl())
        {
            if (rule.type() != AclEntryType.ALLOW || !rule.principal().equals(expected) && !rule.principal().equals(system)) { throw new IllegalArgumentException("OWN101 nonprivate ACL before JDBC."); }
            ownerRule |= rule.principal().equals(expected); systemRule |= rule.principal().equals(system);
        }
        if (!ownerRule || !systemRule) { throw new IllegalArgumentException("OWN101 owner+SYSTEM ACL required."); }
        final Path code = Path.of(PhantomTask101Authority.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!code.equals(stage.resolve("build/phantom-test/bin").toRealPath())) { throw new IllegalArgumentException("OWN101 authority codeSource differs."); }
    }
    public static void physical(Path file) throws Exception
    {
        for (Path part = file; part != null; part = part.getParent())
        {
            final var attributes = Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || !part.equals(part.toRealPath())) { throw new IllegalArgumentException("OWN101 physical path required before JDBC."); }
        }
    }
    public static void verifyNativeInputs(Path stage, String expectedHash) throws Exception
    {
        final Path manifest = stage.resolve("private/native-inputs.tsv"); physical(manifest);
        if (!PhantomTask101Lane.hash(manifest).equals(expectedHash) || Files.size(manifest) > 16_000_000) { throw new IllegalArgumentException("OWN101 native input manifest drift."); }
        final var seen = new java.util.HashSet<String>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8))
        {
            final var fields = line.split("\\t", -1);
            if (fields.length != 2 || !seen.add(fields[0]) || seen.size() > 50000 || !fields[1].matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("OWN101 native input manifest invalid."); }
            final Path file = stage.resolve(fields[0]).normalize();
            if (!file.startsWith(stage.resolve("L2J_Mobius_CT_2.6_HighFive")) && !file.startsWith(stage.resolve("build/phantom-test/bin")) && !file.startsWith(stage.resolve("build/phantom-test/resources"))) { throw new IllegalArgumentException("OWN101 native input path outside scope."); }
            physical(file);
            if (!PhantomTask101Lane.hash(file).equals(fields[1])) { throw new IllegalArgumentException("OWN101 native input bytes drift: " + fields[0]); }
        }
        if (seen.size() < 29000 || !seen.contains("L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar") || !seen.contains("build/phantom-test/bin/org/l2jmobius/tests/phantoms/PhantomCraft101Native.class")) { throw new IllegalArgumentException("OWN101 incomplete native input inventory."); }
    }
}
