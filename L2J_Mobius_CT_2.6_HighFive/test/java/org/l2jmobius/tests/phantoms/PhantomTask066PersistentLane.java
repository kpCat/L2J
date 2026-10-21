/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.LocalRuntimeShutdown;

/** One-time ordinary066 preparation authority; never mutable fixture cleanup authority. */
final class PhantomTask066PersistentLane
{
	private static final String DATABASE = "l2jmobiush5_phantom_release_066a";

	static boolean enabled() { return Boolean.getBoolean("phantom.task066.persistent"); }

	static void validate(Path module, Path config) throws Exception
	{
		final Path runtime = Path.of(System.getProperty("phantom.task066.runtime", "")).toAbsolutePath().normalize();
		if (!enabled() || !Boolean.getBoolean("phantom.task066.control") || !runtime.equals(runtime.toRealPath()) || !runtime.equals(module.getRoot().resolve("PhantomWorld/Farm-Economy-066-candidate"))
			|| !runtime.resolve("game").equals(Path.of("").toRealPath()) || !runtime.resolve("game/config/Database.ini").equals(config.toRealPath())
			|| !module.equals(module.toRealPath()) || !module.equals(Path.of(System.getProperty("phantom.module.root")).toRealPath())
			|| runtime.startsWith(module.getParent())) { throw new IllegalArgumentException("Exact external TASK066 persistent root/config required."); }
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") && !key.equals("phantom.task066.persistent") && Boolean.parseBoolean(System.getProperty(key))))
			{
				throw new IllegalArgumentException("Persistent066 cannot inherit fixture/foreign lane authority.");
			}
		}
		// The loaded production utility verifies the exact whole GameServer JAR and runtime owner.
		LocalRuntimeShutdown.validateLaunch("GameServer");
		final Properties owner = read(runtime.resolve("ownership066.properties"));
		if (!"1".equals(owner.getProperty("version")) || !"TASK066_FARM_ECONOMY".equals(owner.getProperty("owner")) || !"RELEASE_CANDIDATE".equals(owner.getProperty("role")) || !DATABASE.equals(owner.getProperty("schema"))
			|| !runtime.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !module.equals(Path.of(owner.getProperty("sourceModuleRoot", "")).toRealPath()))
		{
			throw new IllegalArgumentException("TASK066 persistent owner/schema/source differs.");
		}
		final Properties settings = read(config);
		if (!settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + DATABASE + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections")))
		{
			throw new IllegalArgumentException("Exact TASK066 persistent JDBC and bounded pool required.");
		}
	}

	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final Path runtime = Path.of(System.getProperty("phantom.task066.runtime")).toRealPath();
		final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
		final Path bootstrap = module.getParent().resolve(".phantom-local/task066/bootstrap-release.complete");
		if (!Files.isRegularFile(bootstrap, LinkOption.NOFOLLOW_LINKS) || !bootstrap.equals(bootstrap.toRealPath()) || Files.size(bootstrap) > 128) { throw new IllegalArgumentException("Own066 completed one-time bootstrap receipt required."); }
		PhantomAssertions.assertEquals(aggregate, Files.readString(bootstrap, StandardCharsets.UTF_8).strip(), "TASK066 persistent bootstrap source differs.");
		final Properties settings = read(config);
		Class.forName(settings.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "TASK066 preparation connected catalog differs.");
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
			{
				query.setString(1, DATABASE);
				try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "TASK066 preparation requires a quiescent own schema."); }
			}
			// Existing own population is allowed only for the two named premeasurement066 preparations.
			final boolean preparation = Boolean.getBoolean("phantom.task066.viewpoint") || Boolean.getBoolean("phantom.task066.prepareDwarf");
			if (preparation)
			{
				final Path observer = runtime.resolve("private/observer066.properties"); final var receipt = read(observer);
				if (!"Task066View".equals(receipt.getProperty("name")) || !"false".equals(receipt.getProperty("actualNetworkSession")) || !"false".equals(receipt.getProperty("synthetic"))) { throw new IllegalArgumentException("Own066 original passive preparation receipt required."); }
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*),SUM(online<>0),SUM(accesslevel<>0) FROM characters")) { rows.next(); if (rows.getLong(1) < 2 || rows.getLong(1) > 65 || rows.getLong(2) != 0 || rows.getLong(3) != 0) { throw new IllegalArgumentException("Own066 preparation requires bounded offline access0 identities."); } }
				try (var query = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId=? AND char_name='Task066View' AND account_name='task066player' AND online=0 AND accesslevel=0 AND level=1")) { query.setInt(1, Integer.parseInt(receipt.getProperty("objectId"))); try (var rows = query.executeQuery()) { rows.next(); if (rows.getLong(1) != 1) { throw new IllegalArgumentException("Own066 original observer identity differs."); } } }
			}
			for (String table : preparation ? List.<String>of() : List.of("accounts", "characters", "phantom_profiles", "phantom_profile_components"))
			{
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "TASK066 preparation refuses existing data: " + table); }
			}
		}
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection()) { PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "TASK066 persistent catalog differs."); }
	}

	private static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("TASK066 bounded regular persistent config required."); }
		final Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}
}
