/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.LocalRuntimeShutdown;

/** Preparation authority for the new persistent candidate; never mutable fixture cleanup authority. */
final class PhantomTask064PersistentLane
{
	static boolean enabled() { return Boolean.getBoolean("phantom.task064.persistent"); }

	static void validate(Path module, Path config) throws Exception
	{
		final Path runtime = Path.of(System.getProperty("phantom.task064.runtime")).toAbsolutePath().normalize();
		if (!enabled() || !Boolean.getBoolean("phantom.task064.control") || !runtime.equals(runtime.toRealPath()) || !runtime.getFileName().toString().equals("World-Integrated-064-candidate")
			|| !runtime.resolve("game").equals(Path.of("").toRealPath()) || !runtime.resolve("game/config/Database.ini").equals(config.toRealPath())
			|| !module.equals(Path.of(System.getProperty("phantom.module.root")).toRealPath())) { throw new IllegalArgumentException("Exact TASK064 persistent root/config required."); }
		LocalRuntimeShutdown.validateLaunch("GameServer");
		final Properties owner = read(runtime.resolve("ownership064.properties"));
		if (!"RELEASE_CANDIDATE".equals(owner.getProperty("role")) || !"l2jmobiush5_phantom_release_064a".equals(owner.getProperty("schema"))) { throw new IllegalArgumentException("TASK064 persistent schema/role differs."); }
		for (String key : System.getProperties().stringPropertyNames()) { if (key.startsWith("phantom.") && key.endsWith(".manifest")) { throw new IllegalArgumentException("Persistent064 cannot inherit fixture/foreign manifest authority."); } }
	}

	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
		PhantomAssertions.assertEquals(aggregate, Files.readString(module.resolve(".phantom-local/task064/bootstrap-release.complete")).strip(), "TASK064 persistent bootstrap source differs.");
		final Properties settings = read(config);
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")); var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
		{
			query.setString(1, "l2jmobiush5_phantom_release_064a");
			try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "TASK064 preparation requires a quiescent own schema."); }
		}
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection()) { PhantomAssertions.assertEquals("l2jmobiush5_phantom_release_064a", connection.getCatalog(), "TASK064 persistent catalog differs."); }
	}

	private static Properties read(Path file) throws Exception
	{
		if (!file.equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("TASK064 persistent config alias/size rejected."); }
		final Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}
}
