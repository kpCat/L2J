/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.HexUtil;

/** Exact OWN074 authority; only absent schemas can be installed. */
final class PhantomTask074Lane
{
	static final String OWNER = "TASK074_LIVING_SYSTEMS", TEST = "l2jmobiush5_phantom_test_074", RELEASE = "l2jmobiush5_phantom_release_074";
	static final String MIGRATION = "e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3";
	static boolean enabled() { return Boolean.getBoolean("phantom.task074.native"); }
	static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 16384) { throw new IllegalArgumentException("OWN074 physical bounded input required."); }
		final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
	static Path task(Path module) { return module.getParent().resolve(".phantom-local/task074"); }
	static Properties owner(Path module) throws Exception
	{
		final var task = task(module).toRealPath(); final var owner = read(task.resolve("ownership074.properties"));
		if (!enabled() || !module.equals(module.toRealPath()) || !module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive") || !OWNER.equals(owner.getProperty("owner")) || !TEST.equals(owner.getProperty("testSchema")) || !RELEASE.equals(owner.getProperty("releaseSchema")) || !task.equals(Path.of(owner.getProperty("taskRoot", "")).toRealPath()) || !java.util.UUID.fromString(owner.getProperty("installationId", "")).toString().equals(owner.getProperty("installationId"))) { throw new IllegalArgumentException("OWN074 root/schema/UUID authority differs."); }
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.task") && key.endsWith(".native") && !key.equals("phantom.task074.native") && Boolean.getBoolean(key)) { throw new IllegalArgumentException("OWN074 refuses mixed historical native authority."); }
			if (key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") || key.endsWith(".control"))) { throw new IllegalArgumentException("OWN074 native lane refuses foreign runtime authority."); }
		}
		return owner;
	}
	static Properties settings(Path config, String schema) throws Exception
	{
		final var settings = read(config);
		if (!settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("OWN074 exact JDBC input required."); }
		Class.forName(settings.getProperty("Driver")); return settings;
	}
	static void validate(Path module, Path config) throws Exception
	{
		final var owner = owner(module); final var runtime = task(module).resolve("native-test").toRealPath();
		if (!runtime.resolve("game").equals(Path.of("").toRealPath()) || !runtime.resolve("game/config/Database.ini").equals(config.toRealPath())) { throw new IllegalArgumentException("OWN074 native working directory/config differs."); }
		settings(config, TEST); System.setProperty("phantom.economy.schema", TEST); System.setProperty("phantom.economy.installation", owner.getProperty("installationId"));
	}
	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config); final var settings = settings(config, TEST);
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (!TEST.equals(connection.getCatalog())) { throw new IllegalArgumentException("OWN074 catalog differs."); }
			org.l2jmobius.gameserver.qol.Economy070Shop.verifySchema(connection); quiescent(connection);
		}
		DatabaseFactory.initFromConfig(config.toString());
	}
	static void environment(Path module, String action) throws Exception
	{
		final var owner = owner(module); final var task = task(module); final boolean release = action.endsWith("release"); final String schema = release ? RELEASE : TEST;
		final var settings = settings(task.resolve("private/Database." + (release ? "release" : "test") + ".ini"), schema);
		try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (action.equals("preflight")) { absent(admin, TEST); absent(admin, RELEASE); System.out.println("OWN074_PREFLIGHT schemasAbsent=true"); return; }
			if (!action.equals("bootstrap-test") && !action.equals("bootstrap-release")) { throw new IllegalArgumentException("OWN074 environment action differs."); }
			absent(admin, schema); final var scripts = PhantomTestSchemaManifest.inventory(module);
			final String migration = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(module.resolve("dist/db_installer/sql/game/economy071.sql"))));
			if (!MIGRATION.equals(migration)) { throw new IllegalArgumentException("OWN074 canonical Economy071 migration differs."); }
			Files.writeString(task.resolve(action + ".started"), PhantomTestSchemaManifest.current(module).aggregateSha256(), StandardOpenOption.CREATE_NEW);
			try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalArgumentException("OWN074 new catalog differs."); }
				StrictSqlScriptRunner.execute(connection, scripts);
				try (var insert = connection.prepareStatement("INSERT INTO economy071_authority(schema_name,task_id,installation_id,schema_version,migration_sha256) VALUES(?,74,?,1,?)")) { insert.setString(1, schema); insert.setString(2, owner.getProperty("installationId")); insert.setString(3, MIGRATION); insert.executeUpdate(); }
				final String hex = new java.math.BigInteger(HexUtil.generateHexBytes(16)).toString(16);
				try (var insert = connection.prepareStatement("INSERT INTO gameservers(server_id,hexid,host) VALUES(1,?,'')")) { insert.setString(1, hex); insert.executeUpdate(); }
				final var registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
				try (var writer = Files.newBufferedWriter(task.resolve("private/hexid-" + (release ? "release" : "test") + ".txt"), StandardOpenOption.CREATE_NEW)) { registration.store(writer, "OWN074 distinct registration"); }
			}
			Files.writeString(task.resolve(action + ".complete"), "OWN074; scripts=" + scripts.size(), StandardOpenOption.CREATE_NEW); System.out.println("OWN074_INSTALLED schema=" + schema + "; scripts=" + scripts.size());
		}
	}
	private static void absent(Connection connection, String schema) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?")) { query.setString(1, schema); try (var rows = query.executeQuery()) { rows.next(); if (rows.getInt(1) != 0) { throw new IllegalArgumentException("OWN074 refuses existing schema: " + schema); } } }
	}
	static void quiescent(Connection connection) throws Exception
	{
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()")) { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("OWN074 connections remain: " + rows.getLong(1)); } }
	}
}
