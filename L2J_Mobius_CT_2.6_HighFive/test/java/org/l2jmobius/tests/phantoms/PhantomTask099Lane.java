/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;

/** Fresh OWN099 infrastructure only. No generic fixture reset or historical lane opt-in. */
final class PhantomTask099Lane
{
    static final String SCHEMA = "l2jmobiush5_phantom_test_099";
    static boolean enabled() { return Boolean.getBoolean("phantom.task099.native"); }
    static Path stage() { return Path.of(System.getProperty("phantom.task099.stage", "")).toAbsolutePath().normalize(); }
    static Properties read(Path file) throws Exception
    {
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 32768) { throw new IllegalArgumentException("OWN099 bounded physical input required."); }
        final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
    }
    static String hash(Path file) throws Exception
    {
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { final byte[] buffer = new byte[65536]; int length; while ((length = input.read(buffer)) >= 0) { digest.update(buffer, 0, length); } }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    static Properties validate(Path module, Path config) throws Exception
    {
        final Path stage = stage();
        final var owner = read(stage.resolve("private/owner.properties"));
        if (!enabled() || !stage.equals(stage.toRealPath()) || !module.equals(stage.resolve("L2J_Mobius_CT_2.6_HighFive"))
            || !module.resolve("dist/game").equals(Path.of("").toRealPath()) || !module.resolve("dist/game/config/Database.ini").equals(config.toRealPath())
            || !"TASK099_MERCHANT_WORLD".equals(owner.getProperty("owner")) || !SCHEMA.equals(owner.getProperty("schema"))
            || !stage.equals(Path.of(owner.getProperty("stage", "")).toRealPath())
            || !java.util.UUID.fromString(System.getProperty("phantom.task099.uuid", "")).toString().equals(owner.getProperty("installationId"))
            || !Instant.parse(owner.getProperty("expiresUtc", "")).isAfter(Instant.now())
            || !"feature/phantom-world".equals(owner.getProperty("branch"))
            || !hash(stage.resolve("inputs.tsv")).equals(owner.getProperty("inputsSha256"))) { throw new IllegalArgumentException("OWN099 exact live stage/cwd/UUID/expiry required."); }
        final Path source = Path.of(owner.getProperty("sourceModule", "")).toRealPath();
        if (stage.startsWith(source.getParent())) { throw new IllegalArgumentException("OWN099 external stage required."); }
        for (String key : System.getProperties().stringPropertyNames())
        {
            if (key.startsWith("phantom.") && !java.util.Set.of("phantom.task099.native", "phantom.task099.stage", "phantom.task099.uuid", "phantom.module.root", "phantom.test.config").contains(key)) { throw new IllegalArgumentException("OWN099 foreign test authority forbidden."); }
        }
        final var pins = read(stage.resolve("private/native.properties"));
        for (String relative : pins.stringPropertyNames())
        {
            final Path target = module.resolve(relative).normalize();
            if (!target.startsWith(module) || !target.equals(target.toRealPath()) || !hash(target).equals(pins.getProperty(relative))) { throw new IllegalArgumentException("OWN099 native artifact/config drift."); }
        }
        if (!pins.containsKey("dist/libs/GameServer.jar") || !pins.containsKey("dist/game/config/Database.ini") || !pins.containsKey("dist/game/config/Custom/PhantomPlayers.ini")) { throw new IllegalArgumentException("OWN099 required native pins missing."); }
        final var population = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.read(module.resolve("dist/game/config/Custom/PhantomPlayers.ini"));
        if (!population.enabled() || population.populationTarget() != 0 || population.populationActiveTarget() != 0) { throw new IllegalArgumentException("OWN099 validated merchant-only population required."); }
        final Path code = Path.of(org.l2jmobius.gameserver.model.actor.Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!code.equals(module.resolve("dist/libs/GameServer.jar").toRealPath())) { throw new IllegalArgumentException("OWN099 production codeSource differs."); }
        final var database = read(config);
        if (!database.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + SCHEMA + "\\?useSSL=false") || !"4".equals(database.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("OWN099 exact TEST099 JDBC/pool required."); }
        return owner;
    }
    static void initialize(Path module, Path config) throws Exception
    {
        final var owner = validate(module, config); final var database = read(config);
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalArgumentException("OWN099 connected catalog mismatch."); }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installation_id,source_head,inputs_sha256 FROM own099_authority"))
            {
                if (!rows.next() || !owner.getProperty("installationId").equals(rows.getString(1)) || !owner.getProperty("sourceHead").equals(rows.getString(2)) || !owner.getProperty("inputsSha256").equals(rows.getString(3)) || rows.next()) { throw new IllegalArgumentException("OWN099 durable origin mismatch."); }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
            { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("OWN099 other sessions/in-flight runtime."); } }
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("OWN099 authority SQL failure state=" + failure.getSQLState() + ";code=" + failure.getErrorCode()); }
        DatabaseFactory.initFromConfig(config.toString());
    }
}
