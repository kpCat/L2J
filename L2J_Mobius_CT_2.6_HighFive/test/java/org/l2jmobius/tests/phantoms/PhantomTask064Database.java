/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.util.Properties;
import java.math.BigInteger;

import org.l2jmobius.commons.util.HexUtil;

/** One-time installation into an absent TASK064 schema using the existing strict stock SQL runner. */
public final class PhantomTask064Database
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !(args[0].equals("bootstrap-test") || args[0].equals("bootstrap-release"))) { throw new IllegalArgumentException("Explicit TASK064 one-time bootstrap required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.resolve(".phantom-local/task064");
		final boolean fixture = args[0].equals("bootstrap-test");
		final String schema = fixture ? "l2jmobiush5_phantom_test_064" : "l2jmobiush5_phantom_release_064a";
		final Path config = task.resolve(fixture ? "test/Database.test.ini" : "release-input/Database.ini");
		final Path ownerPath = task.resolve(fixture ? "test/owned.properties" : "release-input/owned.properties");
		final Properties settings = read(config), owner = read(ownerPath);
		if (!"TASK064_CAMPAIGN".equals(owner.getProperty("owner")) || !schema.equals(owner.getProperty("database")) || !(fixture ? "MUTABLE_FIXTURE" : "RELEASE_CANDIDATE").equals(owner.getProperty("role"))
			|| !settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?")
			|| !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Exact TASK064 bootstrap authority/config required."); }
		final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
		final var scripts = PhantomTestSchemaManifest.inventory(module);
		Files.writeString(task.resolve(args[0] + ".started"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Class.forName(settings.getProperty("Driver"));
		try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password"));
			var check = connection.prepareStatement("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
		{
			check.setString(1, schema);
			try (var rows = check.executeQuery()) { if (rows.next()) { throw new IllegalStateException("TASK064 schema exists: no reset/reimport permitted."); } }
			try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
		}
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("TASK064 connected catalog differs."); }
			StrictSqlScriptRunner.execute(connection, scripts);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) { rows.next(); System.out.println("TASK064_TABLES=" + rows.getInt(1)); }
			final String hex = new BigInteger(HexUtil.generateHexBytes(16)).toString(16);
			try (var registration = connection.prepareStatement("INSERT INTO gameservers (server_id,hexid,host) VALUES (1,?,'')")) { registration.setString(1, hex); registration.executeUpdate(); }
			final Properties registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
			try (var writer = Files.newBufferedWriter(task.resolve(fixture ? "hexid-test.txt" : "hexid-release.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { registration.store(writer, "TASK064 own registration"); }
		}
		owner.setProperty("schemaAggregateSha256", aggregate);
		try (var writer = Files.newBufferedWriter(ownerPath, StandardCharsets.UTF_8)) { owner.store(writer, "TASK064 own schema; no secrets"); }
		Files.writeString(task.resolve(args[0] + ".complete"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("TASK064_BOOTSTRAP_COMPLETE schema=" + schema + "; scripts=" + scripts.size() + "; aggregate=" + aggregate);
	}

	private static Properties read(Path path) throws Exception
	{
		if (!path.equals(path.toRealPath()) || Files.size(path) > 8192) { throw new IllegalArgumentException("TASK064 config alias/size rejected."); }
		final Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
		return properties;
	}
}
