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
		if ("TASK066_FARM_ECONOMY".equals(owner.getProperty("owner")) || "l2jmobiush5_phantom_test_066".equals(owner.getProperty("database")))
		{
			final Path currentModule = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
			final Path lane = currentModule.getParent().resolve(".phantom-local/task066");
			if (!currentModule.equals(module) || !lane.resolve("test/Database.test.ini").equals(config) || !lane.resolve("test/owned.properties").equals(manifest) || !lane.resolve("runtime/game").equals(runtime)
				|| !"TASK066_FARM_ECONOMY".equals(owner.getProperty("owner")) || !"MUTABLE_FIXTURE".equals(owner.getProperty("role")) || !"l2jmobiush5_phantom_test_066".equals(owner.getProperty("database"))
				|| !settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5_phantom_test_066\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections")))
			{
				throw new IllegalArgumentException("Exact TASK066 fixture root/schema/owner/config and bounded pool required.");
			}
			return;
		}
		if ("TASK065_LIVING_PRESENCE".equals(owner.getProperty("owner")) || "l2jmobiush5_phantom_test_065".equals(owner.getProperty("database")))
		{
			final Path currentModule = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
			final Path lane = currentModule.resolve(".phantom-local/task065");
			if (!currentModule.equals(module) || !lane.resolve("test/Database.test.ini").equals(config) || !lane.resolve("test/owned.properties").equals(manifest) || !lane.resolve("runtime/game").equals(runtime)
				|| !"TASK065_LIVING_PRESENCE".equals(owner.getProperty("owner")) || !"MUTABLE_FIXTURE".equals(owner.getProperty("role")) || !"l2jmobiush5_phantom_test_065".equals(owner.getProperty("database"))
				|| !settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5_phantom_test_065\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections")))
			{
				throw new IllegalArgumentException("Exact TASK065 fixture root/schema/owner/config and bounded pool required.");
			}
			return;
		}
		if ("TASK064_CAMPAIGN".equals(owner.getProperty("owner")) || "l2jmobiush5_phantom_test_064".equals(owner.getProperty("database")))
		{
			final Path currentModule = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
			final Path lane064 = currentModule.resolve(".phantom-local/task064");
			if (!currentModule.equals(module) || !lane064.resolve("test/Database.test.ini").equals(config) || !lane064.resolve("test/owned.properties").equals(manifest) || !lane064.resolve("runtime/game").equals(runtime)
				|| !"TASK064_CAMPAIGN".equals(owner.getProperty("owner")) || !"MUTABLE_FIXTURE".equals(owner.getProperty("role")) || !"l2jmobiush5_phantom_test_064".equals(owner.getProperty("database"))
				|| !settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5_phantom_test_064\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections")))
			{
				throw new IllegalArgumentException("Exact TASK064 fixture root/schema/owner/config and bounded pool required.");
			}
			return;
		}
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
		return task066Enabled() || task065Enabled() || task064Enabled() || System.getProperty("phantom.contract058.manifest") != null;
	}

	static boolean task066Enabled() { return System.getProperty("phantom.task066.manifest") != null; }
	static boolean task065Enabled() { return System.getProperty("phantom.task065.manifest") != null; }
	static boolean task064Enabled() { return System.getProperty("phantom.task064.manifest") != null; }
	static String databaseName() { return task066Enabled() ? "l2jmobiush5_phantom_test_066" : task065Enabled() ? "l2jmobiush5_phantom_test_065" : task064Enabled() ? "l2jmobiush5_phantom_test_064" : DATABASE; }
	private static String manifestProperty() { return task066Enabled() ? "phantom.task066.manifest" : task065Enabled() ? "phantom.task065.manifest" : task064Enabled() ? "phantom.task064.manifest" : "phantom.contract058.manifest"; }
	private static Path manifestPath() { return Path.of(System.getProperty(manifestProperty())); }

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
		if (task066Enabled() && !"TASK066_FARM_ECONOMY".equals(read(manifest).getProperty("owner"))) { throw new IllegalArgumentException("TASK066 cannot inherit a historical fixture owner."); }
		if (task065Enabled() && !"TASK065_LIVING_PRESENCE".equals(read(manifest).getProperty("owner"))) { throw new IllegalArgumentException("TASK065 cannot inherit a historical fixture owner."); }
		if (task064Enabled() && !"TASK064_CAMPAIGN".equals(read(manifest).getProperty("owner"))) { throw new IllegalArgumentException("TASK064 cannot inherit a historical fixture owner."); }
		validateIdentity(module.toRealPath(), config.toRealPath(), manifest.toRealPath(), Path.of("").toRealPath(), read(manifest), settings);
		for (String property : System.getProperties().stringPropertyNames())
		{
			if (property.startsWith("phantom.") && property.endsWith(".manifest") && !property.equals(manifestProperty()))
			{
				throw new IllegalArgumentException("A second owned lane cannot accompany TASK058.");
			}
		}
		return settings;
	}

	public static void initialize(Path module, Path config) throws Exception
	{
		final Path manifest = manifestPath();
		validateFiles(module, config, manifest);
		PhantomAssertions.assertEquals(PhantomTestSchemaManifest.current(module).aggregateSha256(), read(manifest).getProperty("schemaAggregateSha256"), "TASK058 bootstrap source schema differs.");
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(databaseName(), connection.getCatalog(), "Owned fixture connected catalog differs.");
		}
	}

	/** Recheck existing fixture authority without reinitializing or provisioning the database. */
	static void assertActiveFixture(PhantomTestContext context) throws Exception
	{
		if (!enabled() || !DatabaseFactory.isInitialized())
		{
			throw new IllegalArgumentException("An initialized exact058t fixture is required.");
		}
		validateFiles(context.moduleRoot(), Path.of(System.getProperty("phantom.test.config")), manifestPath());
		try (var connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(databaseName(), connection.getCatalog(), "Additional owned fixture connected catalog differs.");
		}
	}

	/** Reuses the strict stock SQL inventory runner; creates only an absent058t schema, never users or resets. */
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 1) || !(args[0].equals("guard") || args[0].equals("bootstrap") || args[0].equals("repair-known-m4-fixture") || args[0].equals("repair-known-m6-fixture") || args[0].equals("repair-known-m7-fixture") || args[0].equals("repair-known-m7-fixture2") || args[0].equals("repair-known-m7-fixture3") || args[0].equals("repair-normal-teleport-fixture")))
		{
			throw new IllegalArgumentException("Expected guard, one-time bootstrap or an exact receipt-bound058t fixture repair.");
		}
		final Path lane = MODULE.resolve(".phantom-local/contract058t");
		final Path config = lane.resolve("test/Database.test.ini");
		final Path manifest = lane.resolve("test/owned.properties");
		final Properties settings = validateFiles(MODULE, config, manifest);
		final var schema = PhantomTestSchemaManifest.current(MODULE);
		if (args[0].equals("repair-known-m7-fixture3") || args[0].equals("repair-normal-teleport-fixture"))
		{
			// AUTHORITY058 section3: only exact previously declared failed058t fixture rows; accounts stay intact.
			final boolean teleport = args[0].equals("repair-normal-teleport-fixture");
			final long profileId = teleport ? 367 : 366;
			final int primary = teleport ? 268435469 : 268435465, observer = teleport ? 268435471 : 268435467;
			final String suffix = teleport ? "05837" : "05833", account = teleport ? "phantom_t004_58005837" : "phantom_t004_58005833";
			final String report = Files.readString(lane.resolve(teleport ? "reports/normal-pending-teleport-cleanup-red1/teleport-cleanup-native058.txt" : "reports/m7-earned-terminal-receipt-native3/earned-learning-native058.txt"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(report.contains("measurement.combatIntegration.profileId=" + profileId) && report.contains("measurement.combatIntegration.actorObjectId=" + primary) && report.contains("measurement.headless.observerObjectId=" + observer) && report.contains(teleport ? "FAIL teleport-cleanup-native058.after-all" : "FAIL earned-learning-native058.after-all"), "Exact failed fixture receipt differs.");
			initialize(MODULE, config);
			try
			{
				final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
				final var profile = profiles.find(profileId).orElseThrow();
				PhantomAssertions.assertEquals(primary, profile.characterObjectId(), "Exact failed fixture profile changed.");
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId,account_name,char_name,online,accesslevel FROM characters WHERE charId IN (?,?) OR account_name=?"))
				{
					query.setInt(1, primary); query.setInt(2, observer); query.setString(3, account);
					try (var rows = query.executeQuery()) { while (rows.next()) { final int id = rows.getInt(1); PhantomAssertions.assertTrue(java.util.Set.of(primary, observer).contains(id) && account.equals(rows.getString(2)) && (id == primary ? "PhT004A" + suffix : "PhT004B" + suffix).equals(rows.getString(3)) && (rows.getInt(4) == 0) && (rows.getInt(5) == 0), "Exact failed058t identity/account/offline state changed."); } }
				}
				Files.createFile(lane.resolve(args[0] + ".started"));
				profiles.delete(profile.profileId(), profile.rowVersion());
				for (int id : java.util.List.of(primary, observer)) { org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(id); }
				PhantomAssertions.assertTrue(profiles.find(profileId).isEmpty(), "Exact fixture profile cleanup failed.");
				Files.writeString(lane.resolve(args[0] + ".complete"), "Exact failed058t profile=" + profileId + "; native cleanup IDs=" + primary + ',' + observer + "; account retained; failed receipt retained; no reset", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
				System.out.println("TASK058 exact failed fixture cleaned: profile=" + profileId + "; two receipt IDs; account retained.");
			}
			finally { DatabaseFactory.close(); }
			return;
		}
		if (args[0].equals("repair-known-m6-fixture"))
		{
			final String report = Files.readString(lane.resolve("reports/m6-kamaloka-stock-native1/instances-native058.txt"), StandardCharsets.UTF_8);
			if (!report.contains("measurement.combatIntegration.profileId=347") || !report.contains("[268435465, 268481637, 268481639, 268435467]") || !report.contains("FAIL instances-native058.after-all")) { throw new IllegalStateException("Exact failed M6 fixture receipt absent."); }
			initialize(MODULE, config);
			try
			{
				final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
				final var ids = java.util.List.of(268435465, 268481637, 268481639, 268435467);
				final var ownedProfiles = new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.profile.PhantomProfile>();
				for (int index = 0; index < 3; index++)
				{
					final var profile = profiles.findByCharacterObjectId(ids.get(index)).orElseThrow();
					PhantomAssertions.assertEquals(347L + index, profile.profileId(), "Exact failed M6 fixture profile changed."); ownedProfiles.add(profile);
				}
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId,account_name FROM characters WHERE charId IN (268435465,268481637,268481639,268435467) OR account_name=?"))
				{
					query.setString(1, "phantom_t004_58005831");
					try (var rows = query.executeQuery())
					{
						while (rows.next()) { PhantomAssertions.assertTrue(ids.contains(rows.getInt(1)) && "phantom_t004_58005831".equals(rows.getString(2)), "Failed M6 fixture character/account changed; repair refused."); }
					}
				}
				Files.createFile(lane.resolve("repair-known-m6-fixture.started"));
				for (var profile : ownedProfiles) { profiles.delete(profile.profileId(), profile.rowVersion()); }
				for (int id : ids) { org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(id); }
				try (var connection = DatabaseFactory.getConnection(); var delete = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
				{ delete.setString(1, "phantom_t004_58005831"); delete.executeUpdate(); }
				for (var profile : ownedProfiles) { PhantomAssertions.assertTrue(profiles.find(profile.profileId()).isEmpty(), "Exact failed M6 profile cleanup incomplete."); }
				Files.writeString(lane.resolve("repair-known-m6-fixture.complete"), "Only failed058t seed58005831/profile347..349/exact four receipt character IDs/native GameClient fixture cleanup; history retained", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
				System.out.println("TASK058 exact failed mutable M6 fixture cleaned: profiles347..349/four receipt IDs/one owned account; no reset or other lane.");
			}
			finally { DatabaseFactory.close(); }
			return;
		}
		if (args[0].equals("repair-known-m4-fixture") || args[0].equals("repair-known-m7-fixture") || args[0].equals("repair-known-m7-fixture2"))
		{
			// One known failed mutable058t episode, never a generic orphan sweep or persistent058a operation.
			final boolean secondM7 = args[0].equals("repair-known-m7-fixture2");
			final boolean m7 = secondM7 || args[0].equals("repair-known-m7-fixture");
			final long exactProfile = secondM7 ? 365 : m7 ? 364 : 327;
			final String marker = args[0];
			final String report = Files.readString(lane.resolve(secondM7 ? "reports/m7-earned-stock-spawn-native2/earned-learning-native058.txt" : m7 ? "reports/m7-earned-learning-native1/earned-learning-native058.txt" : "reports/m4-native-party-defense-green8/pvp-current-native058.txt"), StandardCharsets.UTF_8);
			if (!report.contains("measurement.combatIntegration.profileId=" + exactProfile) || !report.contains("measurement.combatIntegration.actorObjectId=268435465") || !report.contains(m7 ? "FAIL earned-learning-native058.after-all" : "FAIL pvp-current-native058.after-all")) { throw new IllegalStateException("Exact failed fixture receipt is absent."); }
			initialize(MODULE, config);
			try
			{
				final var profiles = org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open();
				final var profile = profiles.find(exactProfile).orElseThrow(() -> new IllegalStateException("Known orphan is absent; no repair or reset performed."));
				PhantomAssertions.assertEquals(268435465, profile.characterObjectId(), "Known fixture identity changed.");
				if (m7)
				{
					// AUTHORITY058 section3 permits CRUD/cleanup of previously declared058t fixtures.
					// Read-only audit: only PhT004A05833/PhT004B05833 belong to this exact generated account, both offline/access0.
					PhantomAssertions.assertTrue(report.contains("measurement.headless.observerObjectId=268435467"), "Exact M7 observer receipt changed.");
					try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId,account_name,char_name,online,accesslevel FROM characters WHERE charId IN (268435465,268435467) OR account_name=?"))
					{
						query.setString(1, "phantom_t004_58005833");
						try (var rows = query.executeQuery()) { while (rows.next()) { final int id = rows.getInt(1); PhantomAssertions.assertTrue(java.util.Set.of(268435465, 268435467).contains(id) && "phantom_t004_58005833".equals(rows.getString(2)) && (id == 268435465 ? "PhT004A05833" : "PhT004B05833").equals(rows.getString(3)) && (rows.getInt(4) == 0) && (rows.getInt(5) == 0), "Exact M7 fixture identity/account/offline state changed."); } }
					}
				}
				else try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId=?"))
				{
					query.setInt(1, 268435465);
					try (var rows = query.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && (rows.getLong(1) == 0), "Character still exists; fixture repair refuses deletion."); }
				}
				Files.createFile(lane.resolve(marker + ".started"));
				profiles.delete(profile.profileId(), profile.rowVersion());
				if (m7) { for (int id : java.util.List.of(268435465, 268435467)) { org.l2jmobius.gameserver.network.GameClient.deleteCharByObjId(id); } }
				PhantomAssertions.assertTrue(profiles.find(exactProfile).isEmpty(), "Exact orphan profile did not delete.");
				Files.writeString(lane.resolve(marker + ".complete"), "Exact owned058t failed fixture profile" + exactProfile + (m7 ? "; native cleanup PhT004A05833/PhT004B05833; generated account retained" : "/absentChar268435465") + "; historical report retained; no reset", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
				System.out.println("TASK058 known mutable fixture repaired: exact profile" + exactProfile + "; receipt preserved; no other rows requested; generated account retained.");
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
