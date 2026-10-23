/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;

/** Exact own071 authority; historical lanes and their guards remain unchanged. */
final class PhantomTask071Lane
{
	static final String OWNER = "TASK071_LIVING_COMBAT";
	static final String SCHEMA = "l2jmobiush5_phantom_test_071";
	static boolean enabled() { return Boolean.getBoolean("phantom.task071.native"); }
	static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("Own071 bounded physical input required."); }
		final Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}
	static void validate(Path module, Path config) throws Exception
	{
		final Path task = module.getParent().resolve(".phantom-local/task071").toRealPath();
		final Path runtime = task.resolve("native-test").toRealPath();
		final var owner = read(task.resolve("ownership071.properties"));
		if (!enabled() || !module.equals(module.toRealPath()) || !runtime.resolve("game").equals(Path.of("").toRealPath())
			|| !runtime.resolve("game/config/Database.ini").equals(config.toRealPath()) || !OWNER.equals(owner.getProperty("owner"))
			|| !SCHEMA.equals(owner.getProperty("testSchema")) || !task.equals(Path.of(owner.getProperty("taskRoot", "")).toRealPath())) { throw new IllegalArgumentException("Exact own071 native root/config/owner required."); }
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.task") && key.endsWith(".native") && !key.equals("phantom.task071.native") && Boolean.getBoolean(key)) { throw new IllegalArgumentException("Own071 native authority cannot mix with a historical native lane."); }
			if (key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") || key.endsWith(".control"))) { throw new IllegalArgumentException("Own071 native lane cannot inherit foreign runtime authority."); }
		}
		final var database = read(config);
		if (!database.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + SCHEMA + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(database.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Own071 exact TEST database required."); }
		final String installation = owner.getProperty("installationId", "");
		if (!java.util.UUID.fromString(installation).toString().equals(installation)) { throw new IllegalArgumentException("Own071 canonical installation UUID required."); }
		System.setProperty("phantom.economy.schema", SCHEMA);
		System.setProperty("phantom.economy.installation", installation);
	}
	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final var database = read(config);
		Class.forName(database.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
		{
			if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalArgumentException("Own071 connected catalog differs."); }
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
			{ rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("Own071 native TEST requires no other connections."); } }
		}
		DatabaseFactory.initFromConfig(config.toString());
	}
}
