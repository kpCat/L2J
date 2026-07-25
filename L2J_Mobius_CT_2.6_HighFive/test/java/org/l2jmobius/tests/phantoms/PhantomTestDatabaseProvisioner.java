/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.l2jmobius.tests.phantoms.PhantomTestSchemaManifest.Snapshot;

public final class PhantomTestDatabaseProvisioner
{
	private static final String ADMIN_URL_ENV = "PHANTOM_DB_ADMIN_URL";
	private static final String ADMIN_USER_ENV = "PHANTOM_DB_ADMIN_USER";
	private static final String ADMIN_PASSWORD_ENV = "PHANTOM_DB_ADMIN_PASSWORD";
	private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
	private static final List<String> USER_HOSTS = List.of("127.0.0.1", "localhost");
	private static final Pattern PRODUCTION_SCHEMA_GRANT = Pattern.compile("(?i)\\bON\\s+`?l2jmobiush5`?\\.\\*");

	private PhantomTestDatabaseProvisioner()
	{
	}

	public static void main(String[] args)
	{
		final Path moduleRoot = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
		final Path reportsDirectory = Path.of(System.getProperty("phantom.test.reports", "../build/phantom-test/reports")).toAbsolutePath().normalize();
		final Path localDirectory = moduleRoot.resolve(PhantomTestDatabaseGuard.LOCAL_CONFIG_DIRECTORY);
		final Path configFile = localDirectory.resolve(PhantomTestDatabaseGuard.LOCAL_CONFIG_FILE);
		final Path configTemp = localDirectory.resolve(PhantomTestDatabaseGuard.LOCAL_CONFIG_FILE + ".tmp");
		final Path manifestFile = PhantomTestSchemaManifest.localPath(moduleRoot);
		final Path lockFile = localDirectory.resolve("test-db.lock");
		int exitCode = 0;
		try
		{
			validateConstants();
			final List<StrictSqlScriptRunner.ScriptInfo> scripts = PhantomTestSchemaManifest.inventory(moduleRoot);
			final Snapshot schemaSnapshot = PhantomTestSchemaManifest.fromScripts(scripts);
			Files.createDirectories(localDirectory);
			try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))
			{
				AdminSettings admin = null;
				boolean destructiveStarted = false;
				boolean success = false;
				try
				{
					admin = readAdminSettings();
					Class.forName(JDBC_DRIVER);
					final PasswordHolder passwordHolder;
					try (Connection connection = DriverManager.getConnection(admin.url(), admin.user(), admin.password()))
					{
						destructiveStarted = true;
						dropTarget(connection);
						passwordHolder = new PasswordHolder(randomPassword());
						createTarget(connection, passwordHolder);
					}

					final String testUrl = testJdbcUrl();
					try (Connection connection = DriverManager.getConnection(testUrl, PhantomTestDatabaseGuard.TARGET_USER, passwordHolder.value()))
					{
						StrictSqlScriptRunner.execute(connection, scripts);
						StrictSqlScriptRunner.execute(connection, scripts.stream().filter(script -> script.relativePath().startsWith("test/resources/phantoms/db/migrations/")).toList());
						verifyCoreTables(connection);
						verifyGrants(connection);
						PhantomTestSchemaManifest.writeDatabaseMetadata(connection, schemaSnapshot);
						PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, schemaSnapshot);
					}

					writeManifest(reportsDirectory, scripts, schemaSnapshot);
					PhantomTestSchemaManifest.writeAtomic(manifestFile, schemaSnapshot);
					writeConfig(configTemp, configFile, testUrl, passwordHolder.value());
					PhantomTestDatabaseGuard.validate(moduleRoot, configFile);
					success = true;

					final long loginCount = scripts.stream().filter(script -> script.relativePath().startsWith("dist/db_installer/sql/login/")).count();
					final long gameCount = scripts.stream().filter(script -> script.relativePath().startsWith("dist/db_installer/sql/game/")).count();
					final long migrationCount = scripts.stream().filter(script -> script.relativePath().startsWith("test/resources/phantoms/db/migrations/")).count();
					System.out.println("Phantom test DB provisioned: host=127.0.0.1 port=3308 database=" + PhantomTestDatabaseGuard.TARGET_DATABASE + " user=" + PhantomTestDatabaseGuard.TARGET_USER + ".");
					System.out.println("Schema scripts: login=" + loginCount + " game=" + gameCount + " migrations=" + migrationCount + " total=" + scripts.size() + ".");
					System.out.println("Schema manifest: version=" + schemaSnapshot.schemaVersion() + " scripts=" + schemaSnapshot.scriptCount() + " statements=" + schemaSnapshot.statementCount() + " aggregateSha256=" + schemaSnapshot.aggregateSha256() + ".");
					System.out.println("Admin credentials supplied through environment: yes");
					System.out.println("Credentials recorded: no");
				}
				catch (Throwable throwable)
				{
					if (destructiveStarted && (admin != null))
					{
						safeCleanup(admin);
					}
					throw throwable;
				}
				finally
				{
					if (!success)
					{
						try
						{
							Files.deleteIfExists(configTemp);
							Files.deleteIfExists(configFile);
							Files.deleteIfExists(manifestFile);
						}
						catch (IOException e)
						{
							System.err.println("Local test provisioning artifact cleanup failed.");
						}
					}
				}
			}
		}
		catch (Throwable throwable)
		{
			System.err.println("Phantom test DB provisioning failed: " + sanitize(throwable.getMessage()));
			exitCode = 1;
		}

		if (exitCode != 0)
		{
			System.exit(exitCode);
		}
	}

	private static void validateConstants()
	{
		if (!"l2jmobiush5_phantom_test".equals(PhantomTestDatabaseGuard.TARGET_DATABASE) || "l2jmobiush5".equals(PhantomTestDatabaseGuard.TARGET_DATABASE) || !"l2j_phantom_test".equals(PhantomTestDatabaseGuard.TARGET_USER) || (PhantomTestDatabaseGuard.TARGET_PORT != 3308))
		{
			throw new IllegalStateException("Test database destructive constants failed closed.");
		}
	}

	private static AdminSettings readAdminSettings() throws PhantomTestConfigurationException
	{
		final String url = requireEnvironment(ADMIN_URL_ENV);
		final String user = requireEnvironment(ADMIN_USER_ENV);
		final String password = requireEnvironment(ADMIN_PASSWORD_ENV);
		validateAdminUrl(url);
		return new AdminSettings(url, user, password);
	}

	private static String requireEnvironment(String name) throws PhantomTestConfigurationException
	{
		final String value = System.getenv(name);
		if ((value == null) || value.isBlank())
		{
			throw new PhantomTestConfigurationException("Required provisioning environment variable is missing: " + name);
		}
		return value;
	}

	private static void validateAdminUrl(String url) throws PhantomTestConfigurationException
	{
		if (!url.startsWith("jdbc:mysql://"))
		{
			throw new PhantomTestConfigurationException("Admin JDBC URL must use the local mysql transport.");
		}

		final URI uri;
		try
		{
			uri = new URI(url.substring("jdbc:".length()));
		}
		catch (URISyntaxException e)
		{
			throw new PhantomTestConfigurationException("Admin JDBC URL is malformed.", e);
		}

		final String path = uri.getRawPath();
		if ((uri.getUserInfo() != null) || (uri.getFragment() != null) || (uri.getQuery() != null) || (!"127.0.0.1".equals(uri.getHost()) && !"localhost".equals(uri.getHost())) || (uri.getPort() != PhantomTestDatabaseGuard.TARGET_PORT) || ((path != null) && !path.isEmpty() && !"/".equals(path)))
		{
			throw new PhantomTestConfigurationException("Admin JDBC URL must identify only local server 127.0.0.1/localhost:3308 without a schema or credentials.");
		}
	}

	private static void dropTarget(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("DROP DATABASE IF EXISTS `" + PhantomTestDatabaseGuard.TARGET_DATABASE + "`");
			for (String host : USER_HOSTS)
			{
				statement.execute("DROP USER IF EXISTS '" + PhantomTestDatabaseGuard.TARGET_USER + "'@'" + host + "'");
			}
		}
	}

	private static void createTarget(Connection connection, PasswordHolder password) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("CREATE DATABASE `" + PhantomTestDatabaseGuard.TARGET_DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
			for (String host : USER_HOSTS)
			{
				statement.execute("CREATE USER '" + PhantomTestDatabaseGuard.TARGET_USER + "'@'" + host + "' IDENTIFIED BY '" + password.value() + "'");
				statement.execute("GRANT ALL PRIVILEGES ON `" + PhantomTestDatabaseGuard.TARGET_DATABASE + "`.* TO '" + PhantomTestDatabaseGuard.TARGET_USER + "'@'" + host + "'");
			}
		}
	}

	private static void verifyCoreTables(Connection connection) throws SQLException
	{
		for (String table : List.of("accounts", "characters", "items", "phantom_test_harness", PhantomTestSchemaManifest.METADATA_TABLE))
		{
			try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?"))
			{
				statement.setString(1, PhantomTestDatabaseGuard.TARGET_DATABASE);
				statement.setString(2, table);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next() || (result.getInt(1) != 1))
					{
						throw new SQLException("Required test schema table is missing: " + table);
					}
				}
			}
		}
	}

	private static void verifyGrants(Connection connection) throws SQLException
	{
		boolean testGrant = false;
		try (Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SHOW GRANTS"))
		{
			while (result.next())
			{
				final String grant = result.getString(1);
				final String upper = grant.toUpperCase(Locale.ROOT);
				if (PRODUCTION_SCHEMA_GRANT.matcher(grant).find() || upper.contains("ALL PRIVILEGES ON *.*"))
				{
					throw new SQLException("Dedicated test user has a forbidden grant.");
				}
				testGrant |= grant.contains(PhantomTestDatabaseGuard.TARGET_DATABASE);
			}
		}
		if (!testGrant)
		{
			throw new SQLException("Dedicated test database grant is missing.");
		}
	}

	private static void writeManifest(Path reportsDirectory, List<StrictSqlScriptRunner.ScriptInfo> scripts, Snapshot snapshot) throws IOException
	{
		Files.createDirectories(reportsDirectory);
		final StringBuilder content = new StringBuilder();
		content.append("scripts=").append(snapshot.scriptCount()).append(System.lineSeparator());
		content.append("statements=").append(snapshot.statementCount()).append(System.lineSeparator());
		content.append("aggregateSha256=").append(snapshot.aggregateSha256()).append(System.lineSeparator());
		for (StrictSqlScriptRunner.ScriptInfo script : scripts)
		{
			content.append(script.relativePath()).append(" sha256=").append(script.sha256()).append(" statements=").append(script.statements().size()).append(System.lineSeparator());
		}
		Files.writeString(reportsDirectory.resolve("schema-manifest.txt"), content, StandardCharsets.UTF_8);
	}

	private static void writeConfig(Path temp, Path target, String testUrl, String password) throws IOException
	{
		final String content = """
			Driver = com.mysql.cj.jdbc.Driver
			URL = %s
			Login = %s
			Password = %s
			MaximumDatabaseConnections = 4
			TestDatabaseConnections = false
			BackupDatabase = false
			MySqlBinLocation =
			BackupPath =
			BackupDays = 0
			""".formatted(testUrl, PhantomTestDatabaseGuard.TARGET_USER, password);
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		try
		{
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			throw new IOException("Atomic local test configuration move is not supported.", e);
		}
	}

	private static String testJdbcUrl()
	{
		return "jdbc:mysql://127.0.0.1:" + PhantomTestDatabaseGuard.TARGET_PORT + "/" + PhantomTestDatabaseGuard.TARGET_DATABASE + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
	}

	private static String randomPassword()
	{
		final byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static void safeCleanup(AdminSettings admin)
	{
		try (Connection connection = DriverManager.getConnection(admin.url(), admin.user(), admin.password()))
		{
			dropTarget(connection);
		}
		catch (SQLException e)
		{
			System.err.println("Partial test database cleanup failed; production database was not targeted.");
		}
	}

	private static String sanitize(String message)
	{
		return PhantomTestLauncher.sanitize(message == null ? "unknown failure" : message);
	}

	private record AdminSettings(String url, String user, String password)
	{
		@Override
		public String toString()
		{
			return "AdminSettings[url=<redacted>, user=<redacted>, password=<redacted>]";
		}
	}

	private record PasswordHolder(String value)
	{
		@Override
		public String toString()
		{
			return "PasswordHolder[<redacted>]";
		}
	}
}
