/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

/** Installer entrypoint for the single fresh schema in the attested learning lane. */
public final class PhantomLearning001FixtureSql
{
	private static final int PORT = 33317;
	private static final String ADDRESS = "jdbc:mysql://127.0.0.1:" + PORT + '/';
	private static final String SCHEMA = "phantom_learning001_native";
	private static final String LOGIN = "learning001";

	public static void main(String[] args) throws Exception
	{
		if (args.length != 3 || !java.util.Set.of("prepare", "probe", "read-native").contains(args[0]))
		{
			throw new IllegalArgumentException("Expected prepare|probe|read-native, module path and lane");
		}
		final Path module = Path.of(args[1]).toRealPath();
		final Path lane = resolveLane(args[1], args[2]);
		final Path config = lane.resolve("Database.ini");
		if (!Files.readString(lane.resolve("owner.txt")).matches("LEARNING001:[0-9a-f]{32}"))
		{
			throw new IllegalStateException("Fixture ownership marker mismatch");
		}
		if (args[0].equals("prepare"))
		{
			prepare(module, lane, config);
		}
		probe(lane, config);
		if (args[0].equals("read-native")) { readNative(lane, config); }
	}

	static Path resolveLane(String module, String name) throws Exception
	{
		if (!java.util.Set.of("native", "cycle", "cycle2", "cycle3", "cycle4", "cycle5", "cycle6", "cycle7").contains(name)) { throw new IllegalArgumentException("Unknown learning lane"); }
		final Path lane = Path.of(module).toRealPath().getParent().resolve(".phantom-local/learning001").resolve(name);
		if (!lane.toRealPath().equals(lane)) { throw new IllegalStateException("Learning lane is not physical"); }
		return lane;
	}

	private static void readNative(Path lane, Path config) throws Exception
	{
		try (var connection = DriverManager.getConnection(ADDRESS + SCHEMA, LOGIN, read(config).getProperty("Password")); var statement = connection.createStatement())
		{
			connection.setReadOnly(true);
			requireActorIdentity(connection, lane);
			try (var rows = statement.executeQuery("SELECT origin,evidence_kind,scope,skill_id,COUNT(*),COUNT(DISTINCT actor_group),COUNT(DISTINCT event_key),MIN(damage),MAX(damage) "
				+ "FROM phantom_ai_training_examples GROUP BY origin,evidence_kind,scope,skill_id ORDER BY 1,2,3,4"))
			{
				long total = 0;
				while (rows.next())
				{
					total += rows.getLong(5);
					System.out.printf(java.util.Locale.ROOT, "LEARNING001_SQL_EVIDENCE origin=%s kind=%s scope=%s skill=%d count=%d actors=%d unique=%d minHp=%.3f maxHp=%.3f%n",
						rows.getString(1), rows.getString(2), rows.getString(3), rows.getInt(4), rows.getLong(5), rows.getLong(6), rows.getLong(7), rows.getDouble(8), rows.getDouble(9));
				}
				System.out.println("LEARNING001_SQL_EVIDENCE_TOTAL " + total);
			}
			try (var rows = statement.executeQuery("SELECT model_id,status,origin,sha256,schema_fingerprint,training_count,holdout_count,initial_mse,holdout_mse FROM phantom_ai_model_versions ORDER BY model_id"))
			{
				long total = 0;
				while (rows.next())
				{
					total++;
					System.out.printf(java.util.Locale.ROOT, "LEARNING001_SQL_MODEL version=%d status=%s origin=%s sha256=%s schema=%s train=%d audit=%d initialMse=%.8f auditMse=%.8f%n",
						rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5), rows.getInt(6), rows.getInt(7), rows.getDouble(8), rows.getDouble(9));
				}
				System.out.println("LEARNING001_SQL_MODEL_TOTAL " + total);
			}
		}
	}

	private static void prepare(Path module, Path lane, Path config) throws Exception
	{
		final Properties admin = read(lane.resolve("sql/admin.jdbc.ini"));
		if (Files.exists(config) || !(ADDRESS + "mysql").equals(admin.getProperty("URL")) || !"root".equals(admin.getProperty("Login")))
		{
			throw new IllegalStateException("Expected a fresh lane with its exact administrator endpoint");
		}
		final var scripts = PhantomTestSchemaManifest.inventory(module);
		for (var script : scripts)
		{
			for (String sql : script.statements())
			{
				if (sql.matches("(?is).*\\b(?:USE|DATABASE|GRANT|USER)\\b.*"))
				{
					throw new IllegalStateException("Cross-schema SQL in " + script.relativePath());
				}
			}
		}
		final String password = UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(ADDRESS + "mysql", "root", admin.getProperty("Password")))
		{
			requireIdentity(connection, lane, "mysql", "root@");
			try (var statement = connection.createStatement())
			{
				statement.executeUpdate("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
				statement.executeUpdate("REVOKE ALL PRIVILEGES ON `test`.* FROM PUBLIC");
				statement.executeUpdate("REVOKE ALL PRIVILEGES ON `test\\_%`.* FROM PUBLIC");
				statement.executeUpdate("CREATE USER '" + LOGIN + "'@'127.0.0.1' IDENTIFIED BY '" + password + "'");
				statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + SCHEMA + "`.* TO '" + LOGIN + "'@'127.0.0.1'");
			}
		}
		try (var connection = DriverManager.getConnection(ADDRESS + SCHEMA, LOGIN, password))
		{
			requireActorIdentity(connection, lane);
			StrictSqlScriptRunner.execute(connection, scripts);
		}
		Files.writeString(config, "Driver=com.mysql.cj.jdbc.Driver\nURL=" + ADDRESS + SCHEMA + "\nLogin=" + LOGIN
			+ "\nPassword=" + password + "\nMaximumDatabaseConnections=4\nTestDatabaseConnections=true\nBackupDatabase=false\n", StandardOpenOption.CREATE_NEW);
		System.out.println("LEARNING001_SQL_SCHEMA_READY scripts=" + scripts.size() + " sha256=" + PhantomTestSchemaManifest.current(module).aggregateSha256());
	}

	private static void probe(Path lane, Path config) throws Exception
	{
		final Properties settings = read(config);
		if (!(ADDRESS + SCHEMA).equals(settings.getProperty("URL")) || !LOGIN.equals(settings.getProperty("Login")))
		{
			throw new IllegalStateException("Fixture game endpoint mismatch");
		}
		try (var connection = DriverManager.getConnection(ADDRESS + SCHEMA, LOGIN, settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			requireActorIdentity(connection, lane);
			try (var statement = connection.createStatement(); var grants = statement.executeQuery("SHOW GRANTS"))
			{
				boolean scoped = false;
				while (grants.next())
				{
					final String grant = grants.getString(1).replace("\\_", "_");
					if (grant.startsWith("GRANT ALL PRIVILEGES ON `" + SCHEMA + "`.* TO ")) { scoped = true; }
					else if (!grant.startsWith("GRANT USAGE ON *.* TO ")) { throw new IllegalStateException("Foreign fixture SQL grant"); }
				}
				if (!scoped) { throw new IllegalStateException("Missing scoped fixture grant"); }
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND DB=DATABASE()"))
			{
				if (!rows.next() || rows.getInt(1) != 0) { throw new IllegalStateException("Unexpected active SQL sessions"); }
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('accounts','characters','phantom_ai_model_versions','phantom_ai_training_examples')"))
			{
				if (!rows.next() || rows.getInt(1) != 4) { throw new IllegalStateException("Fixture schema is incomplete"); }
			}
		}
		System.out.println("LEARNING001_SQL_PROBE_PASS port=" + PORT + " schema=" + SCHEMA);
	}

	private static Properties read(Path path) throws Exception
	{
		final var props = new Properties();
		try (var reader = Files.newBufferedReader(path)) { props.load(reader); }
		return props;
	}

	static void requireActorIdentity(Connection connection, Path lane) throws Exception
	{
		requireIdentity(connection, lane, SCHEMA, LOGIN + "@127.0.0.1");
	}

	private static void requireIdentity(Connection connection, Path lane, String schema, String principal) throws Exception
	{
		final Path expectedData = lane.resolve("sql/data").normalize();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE(),@@port,VERSION(),CURRENT_USER(),@@session.time_zone,@@datadir"))
		{
			if (!rows.next() || !schema.equals(rows.getString(1)) || rows.getInt(2) != PORT || !rows.getString(3).startsWith("11.4.13-MariaDB")
				|| !("root@".equals(principal) ? rows.getString(4).startsWith(principal) : principal.equals(rows.getString(4)))
				|| !"+00:00".equals(rows.getString(5)) || !expectedData.equals(expectedData.toRealPath())
				|| !expectedData.equals(Path.of(rows.getString(6)).toRealPath()))
			{
				throw new IllegalStateException("Fixture SQL identity mismatch");
			}
		}
	}
}
