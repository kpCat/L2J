package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

/** Full stock installer for the one attested disposable Training Director installation. */
public final class PhantomTrainingSql
{
	private static final String SCHEMA = "phantom_learning001_train002";
	private static final String LOGIN = "learning001";

	private PhantomTrainingSql()
	{
	}

	public static void main(String[] args)
	{
		try
		{
			if (args.length != 3 || !java.util.Set.of("prepare", "probe").contains(args[0]))
			{
				throw new IllegalArgumentException("TRAIN002_SQL_ARGUMENTS");
			}
			final Path root = physical(Path.of(args[1]));
			final Path module = physical(Path.of(args[2]));
			if (!root.equals(module.getParent().resolve(".phantom-local/learning002/train").normalize())
				|| !Files.readString(root.resolve("owner.txt")).matches("LEARNING002:[0-9a-f]{32}"))
			{
				throw new IllegalStateException("TRAIN002_SQL_ROOT_OWNER");
			}
			final int port = Integer.parseInt(read(root.resolve("TRAIN_CONFIG.ini")).getProperty("sqlPort"));
			if (port < 33000 || port > 65535)
			{
				throw new IllegalStateException("TRAIN002_SQL_PORT");
			}
			final Path config = root.resolve("game/config/Database.ini");
			if (args[0].equals("prepare") && !Files.exists(config))
			{
				prepare(root, module, config, port);
			}
			probe(root, module, config, port);
			System.out.println("TRAIN002_SQL_PROBE_PASS port=" + port + " schema=" + SCHEMA);
		}
		catch (Exception failure)
		{
			// Installer/admin statements contain secrets. Never print a JDBC statement,
			// connection properties or arbitrary exception text, even to redirected logs.
			final String message = failure.getMessage();
			System.err.println(message != null && message.matches("TRAIN002_[A-Z0-9_]+") ? message : "TRAIN002_SQL_FAILED_" + failure.getClass().getSimpleName());
			System.exit(1);
		}
	}

	private static void prepare(Path root, Path module, Path config, int port) throws Exception
	{
		final Properties admin = read(root.resolve("sql/admin.jdbc.ini"));
		final String endpoint = "jdbc:mysql://127.0.0.1:" + port + "/";
		if (!(endpoint + "mysql").equals(admin.getProperty("URL")) || !"root".equals(admin.getProperty("Login"))
			|| !admin.getProperty("Password", "").matches("[0-9a-f]{64}"))
		{
			throw new IllegalStateException("TRAIN002_SQL_ADMIN_ENDPOINT");
		}
		final var scripts = PhantomTestSchemaManifest.inventory(module);
		final var manifest = PhantomTestSchemaManifest.current(module);
		for (var script : scripts)
		{
			for (String sql : script.statements())
			{
				if (sql.matches("(?is).*\\b(?:USE|DATABASE|GRANT|USER)\\b.*"))
				{
					throw new IllegalStateException("TRAIN002_CROSS_SCHEMA_INSTALLER");
				}
			}
		}
		final Path preparing = root.resolve("sql/schema-preparing.txt");
		final String proof = Files.readString(root.resolve("owner.txt")) + ":" + port + ":" + manifest.aggregateSha256();
		final String password = UUID.randomUUID().toString().replace("-", "");
		try (var connection = DriverManager.getConnection(endpoint + "mysql", "root", admin.getProperty("Password")))
		{
			requireIdentity(connection, root, port, "mysql", "root@");
			try (var statement = connection.createStatement())
			{
				if (Files.exists(preparing))
				{
					physical(preparing);
					if (!proof.equals(Files.readString(preparing)) || Files.exists(config))
					{
						throw new IllegalStateException("TRAIN002_SQL_BOOTSTRAP_PROOF");
					}
					// No game configuration has ever been published: this own bootstrap
					// cannot contain a legal episode. Recover its interrupted stock DDL.
					statement.executeUpdate("DROP DATABASE IF EXISTS `" + SCHEMA + "`");
					statement.executeUpdate("DROP USER IF EXISTS '" + LOGIN + "'@'127.0.0.1'");
				}
				else
				{
					try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + SCHEMA + "'"))
					{
						if (!rows.next() || rows.getInt(1) != 0)
						{
							throw new IllegalStateException("TRAIN002_SQL_UNOWNED_EXISTING_SCHEMA");
						}
					}
					statement.executeUpdate("REVOKE ALL PRIVILEGES ON `test`.* FROM PUBLIC");
					statement.executeUpdate("REVOKE ALL PRIVILEGES ON `test\\_%`.* FROM PUBLIC");
					Files.writeString(preparing, proof, StandardOpenOption.CREATE_NEW);
				}
				statement.executeUpdate("CREATE DATABASE `" + SCHEMA + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
				statement.executeUpdate("CREATE USER '" + LOGIN + "'@'127.0.0.1' IDENTIFIED BY '" + password + "'");
				statement.executeUpdate("GRANT ALL PRIVILEGES ON `" + SCHEMA + "`.* TO '" + LOGIN + "'@'127.0.0.1'");
			}
		}
		try (var connection = DriverManager.getConnection(endpoint + SCHEMA, LOGIN, password))
		{
			requireIdentity(connection, root, port, SCHEMA, LOGIN + "@127.0.0.1");
			StrictSqlScriptRunner.execute(connection, scripts);
			PhantomTestSchemaManifest.writeDatabaseMetadata(connection, manifest);
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, manifest);
		}
		final Path pending = config.resolveSibling("Database.ini.pending");
		if (Files.exists(pending))
		{
			physical(pending);
			Files.move(pending, pending.resolveSibling("Database.ini.incomplete." + UUID.randomUUID()));
		}
		Files.writeString(pending, "Driver=com.mysql.cj.jdbc.Driver\nURL=" + endpoint + SCHEMA + "\nLogin=" + LOGIN + "\nPassword=" + password
			+ "\nMaximumDatabaseConnections=4\nTestDatabaseConnections=true\nBackupDatabase=false\n", StandardOpenOption.CREATE_NEW);
		Files.move(pending, config, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		PhantomTestSchemaManifest.writeAtomic(root.resolve("sql/schema-manifest.properties"), manifest);
		System.out.println("TRAIN002_SQL_SCHEMA_READY scripts=" + scripts.size() + " sha256=" + manifest.aggregateSha256());
	}

	private static void probe(Path root, Path module, Path config, int port) throws Exception
	{
		final Properties settings = read(config);
		final String endpoint = "jdbc:mysql://127.0.0.1:" + port + "/" + SCHEMA;
		if (!endpoint.equals(settings.getProperty("URL")) || !LOGIN.equals(settings.getProperty("Login")) || settings.getProperty("Password", "").isBlank())
		{
			throw new IllegalStateException("TRAIN002_SQL_GAME_ENDPOINT");
		}
		try (var connection = DriverManager.getConnection(endpoint, LOGIN, settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			requireIdentity(connection, root, port, SCHEMA, LOGIN + "@127.0.0.1");
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, PhantomTestSchemaManifest.current(module));
			boolean scoped = false;
			try (var statement = connection.createStatement(); var grants = statement.executeQuery("SHOW GRANTS"))
			{
				while (grants.next())
				{
					final String grant = grants.getString(1).replace("\\_", "_");
					if (grant.startsWith("GRANT ALL PRIVILEGES ON `" + SCHEMA + "`.* TO ")) { scoped = true; }
					else if (!grant.startsWith("GRANT USAGE ON *.* TO ")) { throw new IllegalStateException("TRAIN002_SQL_FOREIGN_GRANT"); }
				}
			}
			if (!scoped) { throw new IllegalStateException("TRAIN002_SQL_MISSING_GRANT"); }
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('accounts','characters','phantom_ai_model_versions','phantom_ai_training_examples','phantom_test_schema_manifest')"))
			{
				if (!rows.next() || rows.getInt(1) != 5) { throw new IllegalStateException("TRAIN002_SQL_INCOMPLETE_INSTALLER"); }
			}
		}
	}

	private static void requireIdentity(Connection connection, Path root, int port, String schema, String user) throws Exception
	{
		final Path data = physical(root.resolve("sql/data"));
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE(),@@port,VERSION(),CURRENT_USER(),@@session.time_zone,@@datadir"))
		{
			if (!rows.next() || !schema.equals(rows.getString(1)) || rows.getInt(2) != port || !rows.getString(3).startsWith("11.4.13-MariaDB")
				|| !(user.equals("root@") ? rows.getString(4).startsWith(user) : user.equals(rows.getString(4)))
				|| !"+00:00".equals(rows.getString(5)) || !data.equals(physical(Path.of(rows.getString(6)))))
			{
				throw new IllegalStateException("TRAIN002_SQL_RUNTIME_IDENTITY");
			}
		}
	}

	private static Properties read(Path file) throws Exception
	{
		physical(file);
		final Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
		return properties;
	}

	private static Path physical(Path path) throws Exception
	{
		if (!path.isAbsolute()) { throw new IllegalStateException("TRAIN002_SQL_ABSOLUTE_PATH"); }
		final Path normalized = path.normalize();
		if (!normalized.equals(normalized.toRealPath())) { throw new IllegalStateException("TRAIN002_SQL_PHYSICAL_PATH"); }
		for (Path part = normalized; part != null; part = part.getParent())
		{
			if (Files.isSymbolicLink(part) || !Files.exists(part, LinkOption.NOFOLLOW_LINKS)) { throw new IllegalStateException("TRAIN002_SQL_REPARSE_PATH"); }
		}
		return normalized;
	}
}
