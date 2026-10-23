/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import org.l2jmobius.commons.util.HexUtil;

/** One-time stock071 schemas. Never imports over an existing schema. */
public final class PhantomTask071Environment
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !List.of("preflight", "bootstrap-test", "bootstrap-release").contains(args[0])) { throw new IllegalArgumentException("Explicit own071 action required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task071").toRealPath();
		final var owner = PhantomTask071Lane.read(task.resolve("ownership071.properties"));
		if (!PhantomTask071Lane.OWNER.equals(owner.getProperty("owner")) || !task.equals(Path.of(owner.getProperty("taskRoot", "")).toRealPath())) { throw new IllegalArgumentException("Own071 owner differs."); }
		final boolean release = args[0].endsWith("-release");
		final String schema = release ? "l2jmobiush5_phantom_release_071" : PhantomTask071Lane.SCHEMA;
		final var settings = PhantomTask071Lane.read(task.resolve(release ? "private/Database.release.ini" : "private/Database.test.ini"));
		if (!settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Exact bounded own071 JDBC input required."); }
		Class.forName(settings.getProperty("Driver"));
		try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (args[0].equals("preflight"))
			{
				requireAbsent(admin, PhantomTask071Lane.SCHEMA); requireAbsent(admin, "l2jmobiush5_phantom_release_071");
				try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT VERSION()")) { rows.next(); System.out.println("OWN071_PREFLIGHT schemasAbsent=true; version=" + rows.getString(1)); }
				return;
			}
			requireAbsent(admin, schema);
			final var scripts = PhantomTestSchemaManifest.inventory(module);
			final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
			final String installation = owner.getProperty("installationId", "");
			if (!java.util.UUID.fromString(installation).toString().equals(installation)) { throw new IllegalArgumentException("Canonical own071 installation UUID required."); }
			final Path migration = module.resolve("dist/db_installer/sql/game/economy071.sql");
			final String migrationSha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(migration)));
			if (!"e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3".equals(migrationSha)) { throw new IllegalArgumentException("Reviewed economy071 migration hash differs."); }
			Files.writeString(task.resolve(args[0] + ".started"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("Own071 catalog differs."); }
				StrictSqlScriptRunner.execute(connection, scripts);
				try (var insert = connection.prepareStatement("INSERT INTO economy071_authority(schema_name,task_id,installation_id,schema_version,migration_sha256) VALUES(?,71,?,1,?)"))
				{ insert.setString(1, schema); insert.setString(2, installation); insert.setString(3, migrationSha); insert.executeUpdate(); }
				final String hex = new BigInteger(HexUtil.generateHexBytes(16)).toString(16);
				try (var insert = connection.prepareStatement("INSERT INTO gameservers(server_id,hexid,host) VALUES(1,?,'')")) { insert.setString(1, hex); insert.executeUpdate(); }
				final Properties registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
				try (var writer = Files.newBufferedWriter(task.resolve(release ? "private/hexid-release.txt" : "private/hexid-test.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { registration.store(writer, "Own071 registration"); }
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) { rows.next(); System.out.println("OWN071_TABLES=" + rows.getInt(1)); }
			}
			Files.writeString(task.resolve(args[0] + ".complete"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("OWN071_BOOTSTRAP_COMPLETE schema=" + schema + "; scripts=" + scripts.size() + "; aggregate=" + aggregate);
		}
	}
	private static void requireAbsent(Connection connection, String schema) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
		{
			query.setString(1, schema); try (var rows = query.executeQuery()) { rows.next(); if (rows.getInt(1) != 0) { throw new IllegalArgumentException("Own071 refuses existing schema: " + schema); } }
		}
	}
}
