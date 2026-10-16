/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

import org.l2jmobius.commons.database.DatabaseFactory;

/** Exact mutable fixture lane; cumulative058a and all historical lanes are excluded. */
public final class PhantomCampaign058DatabaseLane
{
	static final Path MODULE = Path.of("D:/Codex/worktrees/phantom-dev/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive");
	static final String DATABASE = "l2jmobiush5_phantom_test_058t";

	private PhantomCampaign058DatabaseLane()
	{
	}

	static void validateIdentity(Path module, Path config, Path manifest, Path runtime, Properties owner, Properties settings)
	{
		final Path lane = MODULE.resolve(".phantom-local/contract058t");
		if (!MODULE.equals(module) || !lane.resolve("test/Database.test.ini").equals(config) || !lane.resolve("test/owned.properties").equals(manifest) || !lane.resolve("runtime/game").equals(runtime))
		{
			throw new IllegalArgumentException("Exact OWN TASK058 mutable fixture paths required.");
		}
		if (!"TASK058_CAMPAIGN".equals(owner.getProperty("owner")) || !"MUTABLE_FIXTURE".equals(owner.getProperty("role")) || !DATABASE.equals(owner.getProperty("database")))
		{
			throw new IllegalArgumentException("Exact TASK058 mutable fixture authority required.");
		}
		final String url = settings.getProperty("URL", "");
		if (!url.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + DATABASE + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections")))
		{
			throw new IllegalArgumentException("Only the exact058t catalog and bounded pool are permitted.");
		}
	}

	public static boolean enabled()
	{
		return System.getProperty("phantom.contract058.manifest") != null;
	}

	private static Properties read(Path path) throws Exception
	{
		final Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		return properties;
	}

	private static Properties validateFiles(Path module, Path config, Path manifest) throws Exception
	{
		final Properties settings = read(config);
		validateIdentity(module.toRealPath(), config.toRealPath(), manifest.toRealPath(), Path.of("").toRealPath(), read(manifest), settings);
		for (String property : System.getProperties().stringPropertyNames())
		{
			if (property.startsWith("phantom.") && property.endsWith(".manifest") && !property.equals("phantom.contract058.manifest"))
			{
				throw new IllegalArgumentException("A second owned lane cannot accompany TASK058.");
			}
		}
		return settings;
	}

	public static void initialize(Path module, Path config) throws Exception
	{
		final Path manifest = Path.of(System.getProperty("phantom.contract058.manifest"));
		validateFiles(module, config, manifest);
		PhantomAssertions.assertEquals(PhantomTestSchemaManifest.current(module).aggregateSha256(), read(manifest).getProperty("schemaAggregateSha256"), "TASK058 bootstrap source schema differs.");
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "TASK058 connected catalog differs.");
		}
	}

	/** Recheck existing fixture authority without reinitializing or provisioning the database. */
	static void assertActiveFixture(PhantomTestContext context) throws Exception
	{
		if (!enabled() || !DatabaseFactory.isInitialized())
		{
			throw new IllegalArgumentException("An initialized exact058t fixture is required.");
		}
		validateFiles(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config")), Path.of(System.getProperty("phantom.contract058.manifest")));
		try (var connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "Additional058 fixture connected catalog differs.");
		}
	}

	/** Reuses the strict stock SQL inventory runner; creates only an absent058t schema, never users or resets. */
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 1) || !(args[0].equals("guard") || args[0].equals("bootstrap") || args[0].equals("repair-known-m4-fixture")))
		{
			throw new IllegalArgumentException("Expected guard, bootstrap or exact known M4 fixture repair.");
		}
		final Path lane = MODULE.resolve(".phantom-local/contract058t");
		final Path config = lane.resolve("test/Database.test.ini");
		final Path manifest = lane.resolve("test/owned.properties");
		final Properties settings = validateFiles(MODULE, config, manifest);
		final var schema = PhantomTestSchemaManifest.current(MODULE);
		if (args[0].equals("repair-known-m4-fixture"))
		{
			// One known failed mutable058t episode, never a generic orphan sweep or persistent058a operation.
			final String report = Files.readString(lane.resolve("reports/m4-native-party-defense-green8/pvp-current-native058.txt"), StandardCharsets.UTF_8);
			if (!report.contains("measurement.combatIntegration.profileId=327") || !report.contains("measurement.combatIntegration.actorObjectId=268435465") || !report.contains("FAIL pvp-current-native058.after-all")) { throw new IllegalStateException("Exact failed fixture receipt is absent."); }
			initialize(MODULE, config);
			try
			{
				final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
				final var profile = profiles.find(327).orElseThrow(() -> new IllegalStateException("Known orphan is absent; no repair or reset performed."));
				PhantomAssertions.assertEquals(268435465, profile.characterObjectId(), "Known fixture identity changed.");
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId=?"))
				{
					query.setInt(1, 268435465);
					try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && (rows.getLong(1) == 0), "Character still exists; fixture repair refuses deletion."); }
				}
				Files.createFile(lane.resolve("repair-known-m4-fixture.started"));
				profiles.delete(profile.profileId(), profile.rowVersion());
				PhantomAssertions.assertTrue(profiles.find(327).isEmpty(), "Exact orphan profile did not delete.");
				Files.writeString(lane.resolve("repair-known-m4-fixture.complete"), "Exact owned058t failed fixture profile327/absentChar268435465; historical report retained; no reset", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
				System.out.println("TASK058 known mutable fixture repaired: exact profile327; absent character268435465; receipt preserved; no other rows requested.");
			}
			finally { DatabaseFactory.close(); }
			return;
		}
		if (args[0].equals("guard"))
		{
			System.out.println("TASK058 fixture files accepted; no database connection or write.");
			return;
		}
		final Path marker = lane.resolve("bootstrap.started");
		Files.createFile(marker);
		Class.forName(settings.getProperty("Driver"));
		final var scripts = PhantomTestSchemaManifest.inventory(MODULE);
		try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password"));
			var check = admin.prepareStatement("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?"))
		{
			check.setString(1, DATABASE);
			try (var rows = check.executeQuery())
			{
				if (rows.next())
				{
					throw new IllegalStateException("TASK058 schema already exists; bootstrap refuses any reimport.");
				}
			}
			try (var statement = admin.createStatement())
			{
				statement.execute("CREATE DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
			}
		}
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "TASK058 installer connected catalog differs.");
			StrictSqlScriptRunner.execute(connection, scripts);
		}
		final Properties owner = read(manifest);
		owner.setProperty("schemaAggregateSha256", schema.aggregateSha256());
		try (var writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8))
		{
			owner.store(writer, "TASK058 mutable fixture; no secrets");
		}
		Files.writeString(lane.resolve("bootstrap.complete"), schema.aggregateSha256(), StandardCharsets.UTF_8);
		System.out.println("TASK058 bootstrap complete: scripts=" + scripts.size() + " schema=" + schema.aggregateSha256());
	}
}
