/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;

/** OWN102 authority: canonical staged population, no historical lane or generic fixture reset. */
final class PhantomTask102Lane
{
    private static boolean _inputsVerified;
    static final String SCHEMA = "l2jmobiush5_phantom_test_102";
    static boolean enabled() { return Boolean.getBoolean("phantom.task102.native"); }
    static Path stage() { return Path.of(System.getProperty("phantom.task102.stage", "")).toAbsolutePath().normalize(); }
    static Properties read(Path file) throws Exception
    {
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 32768) { throw new IllegalArgumentException("OWN102 bounded physical input required."); }
        final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
    }
    static String hash(Path file) throws Exception
    {
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { final byte[] buffer = new byte[65536]; int length; while ((length = input.read(buffer)) >= 0) { digest.update(buffer, 0, length); } }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    static boolean recovery108R(Path stage)
    {
        final boolean selected = Files.exists(stage.resolve("private/recovery108R.properties"));
        if (selected)
        {
            for (int prior = 103; prior <= 108; prior++)
            {
                if (Files.exists(stage.resolve("private/recovery" + prior + ".properties"))) { throw new IllegalArgumentException("OWN108R fresh authority namespace required before JDBC."); }
            }
        }
        return selected;
    }
    static Properties validate(Path module, Path config) throws Exception
    {
        final Path stage = stage();
        final var owner = read(stage.resolve("private/owner.properties"));
        if (!enabled() || !stage.equals(stage.toRealPath()) || !module.equals(stage.resolve("L2J_Mobius_CT_2.6_HighFive"))
            || !module.resolve("dist/game").equals(Path.of("").toRealPath()) || !module.resolve("dist/game/config/Database.ini").equals(config.toRealPath())
            || !"TASK102_POPULATION_SCALE".equals(owner.getProperty("owner")) || !SCHEMA.equals(owner.getProperty("schema"))
            || !stage.equals(Path.of(owner.getProperty("stage", "")).toRealPath())
            || !java.util.UUID.fromString(System.getProperty("phantom.task102.uuid", "")).toString().equals(owner.getProperty("installationId"))
            || !Instant.parse(owner.getProperty("expiresUtc", "")).isAfter(Instant.now())
            || !"feature/phantom-world".equals(owner.getProperty("branch"))
            || !hash(stage.resolve("inputs.tsv")).equals(owner.getProperty("inputsSha256"))) { throw new IllegalArgumentException("OWN102 exact live stage/cwd/UUID/expiry required."); }
        final Path source = Path.of(owner.getProperty("sourceModule", "")).toRealPath();
        if (stage.startsWith(source.getParent())) { throw new IllegalArgumentException("OWN102 external stage required."); }
        PhantomTask102Authority.verify(stage, source, owner, true);
        for (String key : System.getProperties().stringPropertyNames())
        {
            if (key.startsWith("phantom.") && !java.util.Set.of("phantom.task102.native", "phantom.task102.stage", "phantom.task102.uuid", "phantom.module.root", "phantom.test.config").contains(key)) { throw new IllegalArgumentException("OWN102 foreign test authority forbidden."); }
        }
        final var pins = read(stage.resolve("private/native.properties"));
        for (String relative : pins.stringPropertyNames())
        {
            if (relative.equals("nativeInputsSha256")) { continue; }
            final Path target = module.resolve(relative).normalize();
            if (!target.startsWith(module) || !target.equals(target.toRealPath()) || !hash(target).equals(pins.getProperty(relative))) { throw new IllegalArgumentException("OWN102 native artifact/config drift."); }
        }
        if (!_inputsVerified) { PhantomTask102Authority.verifyNativeInputs(stage, pins.getProperty("nativeInputsSha256")); _inputsVerified = true; }
        if (!pins.containsKey("dist/libs/GameServer.jar") || !pins.containsKey("dist/game/config/Database.ini") || !pins.containsKey("dist/game/config/Custom/PhantomPlayers.ini")) { throw new IllegalArgumentException("OWN102 required native pins missing."); }
        final var population = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.read(module.resolve("dist/game/config/Custom/PhantomPlayers.ini"));
        final java.util.Map<Integer,Integer> phases = java.util.Map.of(500,16,1000,32,3000,64,5000,128);
        final boolean recovery104 = Files.exists(stage.resolve("private/recovery104.properties"));
        final boolean recovery105 = Files.exists(stage.resolve("private/recovery105.properties"));
        final boolean recovery106 = Files.exists(stage.resolve("private/recovery106.properties"));
        final boolean recovery108 = Files.exists(stage.resolve("private/recovery108.properties")) || recovery108R(stage);
        final boolean recovery107 = Files.exists(stage.resolve("private/recovery107.properties")) || recovery108;
        if (recovery108 && Files.exists(stage.resolve("private/recovery107.properties"))) { throw new IllegalArgumentException("OWN108 fresh authority namespace required."); }
        final boolean recovery = Files.exists(stage.resolve("private/recovery103.properties")) || recovery104 || recovery105 || recovery106 || recovery107;
        if (recovery107 && (recovery104 || recovery105 || recovery106 || Files.exists(stage.resolve("private/recovery103.properties")))) { throw new IllegalArgumentException("OWN107 fresh authority namespace required."); }
        if (recovery106 && (recovery104 || recovery105 || Files.exists(stage.resolve("private/recovery103.properties")))) { throw new IllegalArgumentException("OWN106 fresh authority namespace required."); }
        if (recovery105 && (recovery104 || Files.exists(stage.resolve("private/recovery103.properties")))) { throw new IllegalArgumentException("OWN105 fresh authority namespace required."); }
        final boolean policy = recovery ? population.populationTarget() == 5000 && (recovery106 || recovery107 ? java.util.Set.of(16) : recovery104 || recovery105 ? java.util.Set.of(16,128) : java.util.Set.of(16,32,128)).contains(population.populationActiveTarget())
            : phases.containsKey(population.populationTarget()) && population.populationActiveTarget() == phases.get(population.populationTarget());
        if (!population.enabled() || !policy
            || population.maxMaterializedPhantoms() != population.populationActiveTarget()
            || population.materializationBurstSlots() != 0 || population.maxScheduledPhantomProfiles() != 10000
            || population.populationCreationInFlight() != 2 || population.populationBoundariesPerPulse() != 64
            || population.schedulerPulseMillis() != 100 || population.schedulerProfilesPerPulse() != 128)
        { throw new IllegalArgumentException("OWN102 exact staged owner population policy required."); }
        final Path code = Path.of(org.l2jmobius.gameserver.model.actor.Player.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!code.equals(module.resolve("dist/libs/GameServer.jar").toRealPath())) { throw new IllegalArgumentException("OWN102 production codeSource differs."); }
        final var database = read(config);
        if (!database.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + SCHEMA + "\\?useSSL=false") || !"4".equals(database.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("OWN102 exact TEST102 JDBC/pool required."); }
        return owner;
    }
    static void initialize(Path module, Path config) throws Exception
    {
        final var owner = validate(module, config); final var database = read(config);
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalArgumentException("OWN102 connected catalog mismatch."); }
            if (Files.exists(stage().resolve("private/recovery103.properties")) || Files.exists(stage().resolve("private/recovery104.properties")) || Files.exists(stage().resolve("private/recovery105.properties")) || Files.exists(stage().resolve("private/recovery106.properties")) || Files.exists(stage().resolve("private/recovery107.properties")) || Files.exists(stage().resolve("private/recovery108.properties")) || recovery108R(stage()))
            {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID()"))
                { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("OWN103 foreign or own sessions remain; no native start."); } }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installation_id,source_head,inputs_sha256 FROM own102_authority"))
            {
                if (!rows.next() || !owner.getProperty("installationId").equals(rows.getString(1)) || !owner.getProperty("sourceHead").equals(rows.getString(2)) || !owner.getProperty("inputsSha256").equals(rows.getString(3)) || rows.next()) { throw new IllegalArgumentException("OWN102 durable origin mismatch."); }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
            { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("OWN102 other sessions/in-flight runtime."); } }
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("OWN102 authority SQL failure state=" + failure.getSQLState() + ";code=" + failure.getErrorCode()); }
        DatabaseFactory.initFromConfig(config.toString());
    }
}
