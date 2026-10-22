/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.LocalRuntimeShutdown;

/** Premeasurement preparation of the one isolated R2 clone; never fixture cleanup or reimport. */
final class PhantomTask066R2PersistentLane
{
	private static final String DATABASE = "l2jmobiush5_phantom_release_066r2";
	static boolean enabled() { return Boolean.getBoolean("phantom.task066r2.persistent"); }
	static Path runtime() { return Path.of(System.getProperty("phantom.task066r2.runtime", "")).toAbsolutePath().normalize(); }

	static void validate(Path module, Path config) throws Exception
	{
		final Path root = runtime();
		if (!enabled() || !Boolean.getBoolean("phantom.task066r2.control") || !module.equals(module.toRealPath()) || !root.equals(root.toRealPath())
			|| !root.equals(module.getRoot().resolve("PhantomWorld/Farm-Economy-066-R2-candidate")) || !root.resolve("game").equals(Path.of("").toRealPath())
			|| !root.resolve("game/config/Database.ini").equals(config.toRealPath()) || root.startsWith(module.getParent())) { throw new IllegalArgumentException("Exact external R2 persistent root/config required."); }
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") && !key.equals("phantom.task066r2.persistent") && Boolean.parseBoolean(System.getProperty(key)))) { throw new IllegalArgumentException("R2 clone cannot inherit a mutable or historical lane."); }
		}
		LocalRuntimeShutdown.validateLaunch("GameServer");
		final var owner = read(root.resolve("ownership066r2.properties"));
		if (!"TASK066R2_FARM_ECONOMY".equals(owner.getProperty("owner")) || !"RELEASE_CANDIDATE".equals(owner.getProperty("role")) || !DATABASE.equals(owner.getProperty("schema"))
			|| !module.equals(Path.of(owner.getProperty("sourceModuleRoot", "")).toRealPath())) { throw new IllegalArgumentException("R2 clone owner differs."); }
		final var settings = read(config);
		if (!settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + DATABASE + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Exact R2 clone JDBC/pool required."); }
		final Path clone = module.getParent().resolve(".phantom-local/task066r2/release-input/clone.complete");
		if (!Files.isRegularFile(clone) || !clone.equals(clone.toRealPath()) || !Files.readString(clone, StandardCharsets.US_ASCII).contains("destination=" + DATABASE + "\nprofiles=48\ntables=124\n")) { throw new IllegalArgumentException("Completed one-time stopped066 clone receipt required."); }
	}

	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final var settings = read(config); Class.forName(settings.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
			var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
		{
			if (!DATABASE.equals(connection.getCatalog())) { throw new IllegalArgumentException("R2 clone connected catalog differs."); }
			query.setString(1, DATABASE);
			try (var rows = query.executeQuery()) { if (!rows.next() || rows.getLong(1) != 0) { throw new IllegalArgumentException("R2 premeasurement preparation requires an idle own clone."); } }
		}
		DatabaseFactory.initFromConfig(config.toString());
	}

	private static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file) || !file.equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("R2 bounded regular config required."); }
		final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
}
