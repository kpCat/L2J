/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.commons.util.HexUtil;

/** One-time stock067 schemas. Existing schemas are never reset, copied or reimported. */
public final class PhantomTask067Environment
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !List.of("preflight", "bootstrap-test", "bootstrap-release").contains(args[0])) { throw new IllegalArgumentException("Explicit own067 environment action required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task067").toRealPath();
		final var owner = PhantomTask067Lane.read(task.resolve("ownership067.properties"));
		if (!PhantomTask067Lane.OWNER.equals(owner.getProperty("owner")) || !task.equals(Path.of(owner.getProperty("taskRoot", "")).toRealPath())) { throw new IllegalArgumentException("Own067 environment owner differs."); }
		final boolean release = args[0].endsWith("-release");
		final String schema = release ? "l2jmobiush5_personal_release_067" : PhantomTask067Lane.SCHEMA;
		final var settings = PhantomTask067Lane.read(task.resolve(release ? "private/Database.release.ini" : "private/Database.test.ini"));
		if (!settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Exact own067 bounded JDBC config required."); }
		Class.forName(settings.getProperty("Driver"));
		try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (args[0].equals("preflight"))
			{
				requireAbsent(admin, PhantomTask067Lane.SCHEMA); requireAbsent(admin, "l2jmobiush5_personal_release_067");
				try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT VERSION()")) { rows.next(); System.out.println("OWN067_PREFLIGHT schemasAbsent=true; databaseVersion=" + rows.getString(1)); }
				return;
			}
			requireAbsent(admin, schema);
			final var scripts = PhantomTestSchemaManifest.inventory(module);
			final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
			Files.writeString(task.resolve(args[0] + ".started"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("Own067 bootstrap connected catalog differs."); }
				StrictSqlScriptRunner.execute(connection, scripts);
				final String hex = new BigInteger(HexUtil.generateHexBytes(16)).toString(16);
				try (var insert = connection.prepareStatement("INSERT INTO gameservers(server_id,hexid,host) VALUES(1,?,'')")) { insert.setString(1, hex); insert.executeUpdate(); }
				final Properties registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
				try (var writer = Files.newBufferedWriter(task.resolve(release ? "private/hexid-release.txt" : "private/hexid-test.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { registration.store(writer, "Own067 registration"); }
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) { rows.next(); System.out.println("OWN067_TABLES=" + rows.getInt(1)); }
			}
			Files.writeString(task.resolve(args[0] + ".complete"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("OWN067_BOOTSTRAP_COMPLETE schema=" + schema + "; scripts=" + scripts.size() + "; aggregate=" + aggregate);
		}
	}

	private static void requireAbsent(java.sql.Connection connection, String schema) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
		{
			query.setString(1, schema); try (var rows = query.executeQuery()) { rows.next(); if (rows.getInt(1) != 0) { throw new IllegalArgumentException("Own067 refuses existing schema: " + schema); } }
		}
	}
}
