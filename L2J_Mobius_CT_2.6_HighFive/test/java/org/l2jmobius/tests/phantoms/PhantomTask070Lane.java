/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;

/** Exact own070 authority; historical lanes and their guards remain unchanged. */
final class PhantomTask070Lane
{
	static final String OWNER = "TASK070_ECONOMY_QOL";
	static final String SCHEMA = "l2jmobiush5_phantom_test_070";
	static boolean enabled() { return Boolean.getBoolean("phantom.task070.native"); }
	static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("Own070 bounded physical input required."); }
		final Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}
	static void validate(Path module, Path config) throws Exception
	{
		final Path task = module.getParent().resolve(".phantom-local/task070").toRealPath();
		final Path runtime = task.resolve("native-test").toRealPath();
		final var owner = read(task.resolve("ownership070.properties"));
		if (!enabled() || !module.equals(module.toRealPath()) || !runtime.resolve("game").equals(Path.of("").toRealPath())
			|| !runtime.resolve("game/config/Database.ini").equals(config.toRealPath()) || !OWNER.equals(owner.getProperty("owner"))
			|| !SCHEMA.equals(owner.getProperty("testSchema")) || !task.equals(Path.of(owner.getProperty("taskRoot", "")).toRealPath())) { throw new IllegalArgumentException("Exact own070 native root/config/owner required."); }
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.task") && key.endsWith(".native") && !key.equals("phantom.task070.native") && Boolean.getBoolean(key)) { throw new IllegalArgumentException("Own070 native authority cannot mix with a historical native lane."); }
			if (key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") || key.endsWith(".control"))) { throw new IllegalArgumentException("Own070 native lane cannot inherit foreign runtime authority."); }
		}
		final var database = read(config);
		if (!database.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + SCHEMA + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(database.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Own070 exact TEST database required."); }
	}
	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final var database = read(config);
		Class.forName(database.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
		{
			if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalArgumentException("Own070 connected catalog differs."); }
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
			{ rows.next(); if (rows.getLong(1) != 0) { throw new IllegalArgumentException("Own070 native TEST requires no other connections."); } }
		}
		DatabaseFactory.initFromConfig(config.toString());
	}
}
