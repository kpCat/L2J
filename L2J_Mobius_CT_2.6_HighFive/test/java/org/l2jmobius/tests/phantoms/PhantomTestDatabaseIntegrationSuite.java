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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.ValidatedSettings;
import org.l2jmobius.tests.phantoms.PhantomTestSchemaManifest.Snapshot;

public final class PhantomTestDatabaseIntegrationSuite implements PhantomTestSuite
{
	private static final String FIXTURE_KEY = "task002-20260725001";
	private static final Pattern PRODUCTION_SCHEMA_GRANT = Pattern.compile("(?i)\\bON\\s+`?l2jmobiush5`?\\.\\*");
	private ValidatedSettings _settings;
	private Snapshot _schemaSnapshot;

	@Override
	public String id()
	{
		return "database-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final Path config = Path.of(configProperty);
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), config);
		_settings = bootstrap.settings();
		_schemaSnapshot = bootstrap.schemaSnapshot();
		cleanupFixture();
		context.record("database.name", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("database.user", PhantomTestDatabaseGuard.TARGET_USER);
		context.record("database.schemaAggregateSha256", _schemaSnapshot.aggregateSha256());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if (DatabaseFactory.isInitialized())
			{
				cleanupFixture();
			}
		}
		finally
		{
			DatabaseFactory.close();
		}

		final long deadline = System.nanoTime() + 2000000000L;
		while ((System.nanoTime() < deadline) && hasLivePoolThread())
		{
			Thread.sleep(25);
		}
		PhantomAssertions.assertFalse(hasLivePoolThread(), "Hikari left a live non-daemon pool thread.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("core-tables", _ ->
		{
			for (String table : List.of("accounts", "characters", "items"))
			{
				PhantomAssertions.assertEquals(1, tableCount(table), "Core schema table is missing: " + table);
			}
		});
		registry.add("current-database", _ ->
		{
			try (Connection connection = DatabaseFactory.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT DATABASE()"))
			{
				PhantomAssertions.assertTrue(result.next(), "Current database query returned no row.");
				PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, result.getString(1), "Connected database is not the exact test database.");
			}
		});
		registry.add("current-user", _ ->
		{
			try (Connection connection = DatabaseFactory.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT CURRENT_USER()"))
			{
				PhantomAssertions.assertTrue(result.next(), "Current user query returned no row.");
				PhantomAssertions.assertTrue(result.getString(1).startsWith(PhantomTestDatabaseGuard.TARGET_USER + "@"), "Connection does not use the dedicated test user.");
			}
		});
		registry.add("fixture-commit-double-cleanup", context ->
		{
			cleanupFixture();
			try (Connection connection = DatabaseFactory.getConnection();
				var statement = connection.prepareStatement("INSERT INTO phantom_test_harness (fixture_key, seed, fixture_value, created_marker) VALUES (?, ?, ?, ?)"))
			{
				statement.setString(1, FIXTURE_KEY);
				statement.setLong(2, context.seed());
				statement.setString(3, PhantomScenarioSmokeSuite.EXPECTED_CHECKSUM);
				statement.setString(4, "committed");
				PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Committed fixture insert failed.");
			}
			PhantomAssertions.assertEquals(1, fixtureCount(), "Committed fixture is missing.");
			cleanupFixture();
			cleanupFixture();
			PhantomAssertions.assertEquals(0, fixtureCount(), "Double cleanup left owned fixture residue.");
		});
		registry.add("fixture-rollback", context ->
		{
			cleanupFixture();
			try (Connection connection = DatabaseFactory.getConnection())
			{
				connection.setAutoCommit(false);
				try (var statement = connection.prepareStatement("INSERT INTO phantom_test_harness (fixture_key, seed, fixture_value, created_marker) VALUES (?, ?, ?, ?)"))
				{
					statement.setString(1, FIXTURE_KEY);
					statement.setLong(2, context.seed());
					statement.setString(3, "rollback");
					statement.setString(4, "transaction");
					PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Transactional fixture insert failed.");
				}
				PhantomAssertions.assertEquals(1, fixtureCount(connection), "Fixture is not visible inside transaction.");
				connection.rollback();
			}
			PhantomAssertions.assertEquals(0, fixtureCount(), "Rolled back fixture persisted.");
		});
		registry.add("grants-isolated", _ ->
		{
			boolean testGrant = false;
			try (Connection connection = DatabaseFactory.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SHOW GRANTS"))
			{
				while (result.next())
				{
					final String grant = result.getString(1);
					final String upper = grant.toUpperCase(Locale.ROOT);
					PhantomAssertions.assertFalse(PRODUCTION_SCHEMA_GRANT.matcher(grant).find(), "Dedicated user has a production database grant.");
					PhantomAssertions.assertFalse(upper.contains("ALL PRIVILEGES ON *.*"), "Dedicated user has global ALL privileges.");
					testGrant |= grant.contains(PhantomTestDatabaseGuard.TARGET_DATABASE);
				}
			}
			PhantomAssertions.assertTrue(testGrant, "Dedicated test database grant is missing.");
		});
		registry.add("harness-table", _ -> PhantomAssertions.assertEquals(1, tableCount("phantom_test_harness"), "Harness-owned migration table is missing."));
		registry.add("schema-manifest-metadata", _ ->
		{
			try (Connection connection = DatabaseFactory.getConnection())
			{
				PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, _schemaSnapshot);
				connection.setAutoCommit(false);
				try
				{
					try (var statement = connection.prepareStatement("UPDATE " + PhantomTestSchemaManifest.METADATA_TABLE + " SET aggregate_sha256 = ? WHERE manifest_key = ?"))
					{
						statement.setString(1, "0".repeat(64));
						statement.setString(2, PhantomTestSchemaManifest.MANIFEST_KEY);
						PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Schema metadata mismatch fixture update failed.");
					}
					PhantomAssertions.assertThrows(PhantomTestConfigurationException.class, () -> PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, _schemaSnapshot), "DB schema metadata mismatch must be rejected.");
				}
				finally
				{
					connection.rollback();
					connection.setAutoCommit(true);
				}
				PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, _schemaSnapshot);
			}
		});
		registry.add("pool-close-reopen", _ ->
		{
			PhantomAssertions.assertTrue(DatabaseFactory.isInitialized(), "Database pool was not initialized.");
			DatabaseFactory.close();
			PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(), "Database pool did not close.");
			DatabaseFactory.initFromConfig(_settings.configFile().toString());
			PhantomAssertions.assertTrue(DatabaseFactory.isInitialized(), "Database pool did not reopen.");
		});
	}

	private static int tableCount(String table) throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection();
			var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?"))
		{
			statement.setString(1, PhantomTestDatabaseGuard.TARGET_DATABASE);
			statement.setString(2, table);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Table inventory returned no row.");
				return result.getInt(1);
			}
		}
	}

	private static int fixtureCount() throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			return fixtureCount(connection);
		}
	}

	private static int fixtureCount(Connection connection) throws SQLException
	{
		try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_test_harness WHERE fixture_key = ?"))
		{
			statement.setString(1, FIXTURE_KEY);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Fixture count returned no row.");
				return result.getInt(1);
			}
		}
	}

	private static void cleanupFixture() throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection();
			var statement = connection.prepareStatement("DELETE FROM phantom_test_harness WHERE fixture_key = ?"))
		{
			statement.setString(1, FIXTURE_KEY);
			statement.executeUpdate();
		}
	}

	private static boolean hasLivePoolThread()
	{
		return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.isAlive() && !thread.isDaemon() && thread.getName().startsWith("L2JMobiusPool"));
	}
}
