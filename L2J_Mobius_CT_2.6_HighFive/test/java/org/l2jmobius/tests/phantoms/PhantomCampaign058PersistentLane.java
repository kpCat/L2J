/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.math.BigInteger;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.regex.Pattern;
import java.sql.Connection;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.HexUtil;

/** Persistent058a infrastructure only; never grants mutable fixture cleanup authority. */
public final class PhantomCampaign058PersistentLane
{
	static final Path MODULE = PhantomCampaign058DatabaseLane.MODULE;
	static final Path LANE = MODULE.resolve(".phantom-local/contract058a");
	static final Path RUNTIME = LANE.resolve("runtime");
	static final String DATABASE = "l2jmobiush5_phantom_test_058a";

	private PhantomCampaign058PersistentLane() { }

	static void validateIdentity(Path module, Path config, Path manifest, Path cwd, Properties owner, Properties settings, boolean runtime)
	{
		final Path root = LANE.resolve(runtime ? "runtime" : "config-input");
		if (!MODULE.equals(module) || !root.resolve("game/config/Database.ini").equals(config) || !root.resolve("ownership058.properties").equals(manifest) || !(runtime ? RUNTIME.resolve("game") : MODULE).equals(cwd)
			|| !"1".equals(owner.getProperty("version")) || !"TASK058_CAMPAIGN".equals(owner.getProperty("owner")) || !"CUMULATIVE_INTEGRATION".equals(owner.getProperty("role")) || !DATABASE.equals(owner.getProperty("schema")) || !RUNTIME.equals(Path.of(owner.getProperty("runtimeRoot", "")).normalize())
			|| !"2138".equals(owner.getProperty("clientLoginPort")) || !"7838".equals(owner.getProperty("gamePort")) || !"9138".equals(owner.getProperty("controlPort")))
		{
			throw new IllegalArgumentException("Exact persistent058a OWN/root/schema/role/owner/ports required; fixture cleanup is forbidden.");
		}
		if (!settings.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + DATABASE + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"8".equals(settings.getProperty("MaximumDatabaseConnections")))
		{
			throw new IllegalArgumentException("Exact persistent058a catalog and bounded pool required.");
		}
	}

	public static boolean enabled()
	{
		return "true".equals(System.getProperty("phantom.campaign058.persistent"));
	}

	private static Properties read(Path path) throws Exception
	{
		final Properties value = new Properties();
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { value.load(reader); }
		return value;
	}

	static Properties validateFiles(Path module, Path config, boolean runtime) throws Exception
	{
		final Path root = LANE.resolve(runtime ? "runtime" : "config-input");
		final Path manifest = root.resolve("ownership058.properties");
		final Properties settings = read(config);
		validateIdentity(module.toRealPath(), config.toRealPath(), manifest.toRealPath(), Path.of("").toRealPath(), read(manifest), settings, runtime);
		for (String property : System.getProperties().stringPropertyNames())
		{
			if (property.startsWith("phantom.") && property.endsWith(".manifest")) { throw new IllegalArgumentException("Persistent058a cannot accompany another owned/fixture lane."); }
		}
		return settings;
	}

	static void initialize(Path module, Path config) throws Exception
	{
		validateFiles(module, config, true);
		final String schema = PhantomTestSchemaManifest.current(module).aggregateSha256();
		PhantomAssertions.assertEquals(schema, read(RUNTIME.resolve("ownership058.properties")).getProperty("schemaAggregateSha256"), "Persistent058a schema source differs.");
		PhantomAssertions.assertEquals(schema, Files.readString(LANE.resolve("bootstrap.complete")).strip(), "Persistent058a has no completed one-time bootstrap.");
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection()) { PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "Persistent058a connected catalog differs."); }
	}

	static boolean stockRegistration(int id, String hex, String host)
	{
		return (id == 2) && "-2ad66b3f483c22be097019f55c8abdf0".equals(hex) && "".equals(host);
	}

	private static void verifyFreshInstalled(Connection connection, List<StrictSqlScriptRunner.ScriptInfo> scripts) throws Exception
	{
		final Set<String> expected = new TreeSet<>();
		final Pattern create = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?");
		for (var script : scripts) { for (String sql : script.statements()) { final var match = create.matcher(sql); if (match.find()) { expected.add(match.group(1)); } } }
		final Set<String> actual = new TreeSet<>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"))
		{
			while (rows.next()) { PhantomAssertions.assertEquals("BASE TABLE", rows.getString(2), "Fresh058a contains a non-table object."); actual.add(rows.getString(1)); }
		}
		PhantomAssertions.assertTrue(!expected.isEmpty(), "Stock schema table discovery is empty.");
		PhantomAssertions.assertEquals(expected, actual, "Fresh058a installed table inventory differs; no DDL replay permitted.");
		for (String table : List.of("accounts", "characters", "phantom_profiles", "phantom_profile_components"))
		{
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table))
			{ PhantomAssertions.assertTrue(rows.next() && rows.getLong(1) == 0, "Registration-only continuation refuses populated058a: " + table); }
		}
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT server_id, hexid, host FROM gameservers"))
		{
			PhantomAssertions.assertTrue(rows.next() && stockRegistration(rows.getInt(1), rows.getString(2), rows.getString(3)) && !rows.next(), "Fresh058a registration differs from the exact stock seed.");
		}
		System.out.println("TASK058 fresh installed tables verified=" + actual.size() + "; accounts/characters/profiles/components=0; stockRegistration2=preserved");
	}

	/** One absent-schema stock installation and its own registration; never reset/reimport/users/grants. */
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 1) || !List.of("guard", "bootstrap", "finish-bootstrap").contains(args[0])) { throw new IllegalArgumentException("Expected guard, bootstrap or registration-only finish-bootstrap."); }
		final Path input = LANE.resolve("config-input");
		final Path manifest = input.resolve("ownership058.properties");
		final Properties settings = validateFiles(MODULE, input.resolve("game/config/Database.ini"), false);
		if (args[0].equals("guard")) { System.out.println("TASK058 persistent input accepted; JDBC/WRITES/PROCESSES=0"); return; }
		final boolean registrationOnly = args[0].equals("finish-bootstrap");
		PhantomAssertions.assertFalse(Files.exists(RUNTIME) || Files.exists(LANE.resolve("bootstrap.complete")) || Files.exists(input.resolve("game/config/hexid.txt")) || read(manifest).containsKey("schemaAggregateSha256"), "Bootstrap/continuation refuses an initialized runtime or registration.");
		if (registrationOnly) { PhantomAssertions.assertTrue(Files.isRegularFile(LANE.resolve("bootstrap.started")), "Registration-only continuation has no own bootstrap start."); }
		else { Files.createFile(LANE.resolve("bootstrap.started")); }
		Class.forName(settings.getProperty("Driver"));
		final var scripts = PhantomTestSchemaManifest.inventory(MODULE);
		final String schema = PhantomTestSchemaManifest.current(MODULE).aggregateSha256();
		if (!registrationOnly)
		{
			try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password"));
				var check = admin.prepareStatement("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?"))
			{
				check.setString(1, DATABASE);
				try (var rows = check.executeQuery()) { if (rows.next()) { throw new IllegalStateException("Persistent058a already exists; any reimport is refused."); } }
				try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			}
		}
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			PhantomAssertions.assertEquals(DATABASE, connection.getCatalog(), "Persistent058a installer connected catalog differs.");
			if (!registrationOnly) { StrictSqlScriptRunner.execute(connection, scripts); }
			verifyFreshInstalled(connection, scripts);
			final String hex = new BigInteger(HexUtil.generateHexBytes(16)).toString(16); // Same stock GameServerRegister encoding.
			final Properties registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
			final Path registrationFile = input.resolve("game/config/hexid.txt");
			Files.createFile(registrationFile);
			try (var writer = Files.newBufferedWriter(registrationFile, StandardCharsets.UTF_8)) { registration.store(writer, "TASK058 own stock login registration"); }
			try (var insert = connection.prepareStatement("INSERT INTO gameservers (hexid,server_id,host) VALUES (?,?,?)"))
			{
				insert.setString(1, hex); insert.setInt(2, 1); insert.setString(3, "");
				PhantomAssertions.assertEquals(1, insert.executeUpdate(), "Own058a game registration was not stored.");
			}
		}
		PhantomAssertions.assertFalse(read(manifest).containsKey("schemaAggregateSha256"), "Persistent manifest already has a bootstrap hash.");
		Files.writeString(manifest, "schemaAggregateSha256=" + schema + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		Files.writeString(LANE.resolve("bootstrap.complete"), schema, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("TASK058 persistent bootstrap complete: scripts=" + scripts.size() + "; schema=" + schema + "; ownRegistration=1; SQL_REIMPORT=false; registrationOnly=" + registrationOnly);
	}
}
