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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.Settings;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.ValidatedSettings;

/**
 * Read-only operator preflight for the versioned Phantom World local-play path.
 */
public final class PhantomLocalPlayPreflight
{
	public static final List<String> REQUIRED_PHANTOM_DATA = List.of(
		"acquisition/high-five-acquisition-v1.xml",
		"acquisition/high-five-quest-collection-v1.xml",
		"clan/high-five-clan-directives-v1.xml",
		"conversation/high-five-ru-conversation-corpus-v1.tsv",
		"conversation/high-five-ru-conversation-execution-v1.xml",
		"conversation/high-five-ru-conversation-v1.xml",
		"economy/high-five-economy-v1.xml",
		"farming/high-five-farming-conflict-v1.xml",
		"knowledge/high-five-core-v1.xml",
		"party/high-five-party-roles-v1.xml",
		"population/high-five-population-v1.xml",
		"progression/high-five-capabilities-v1.xml",
		"pvp/high-five-karma-recovery-v1.xml",
		"pvp/pvp-policy-v1.xml",
		"rift/high-five-rift-policy-v1.xml",
		"semantic/high-five-ru-corpus-v1.tsv",
		"semantic/high-five-ru-semantic-v1.xml",
		"social/high-five-social-v1.xml",
		"topology/high-five-core.xml");
	private static final Set<String> REQUIRED_TABLES = Set.of("phantom_profiles", "phantom_profile_components", "phantom_economy_operations", "phantom_economy_reservations", "phantom_economy_audit", "phantom_economy_offers");
	private static final Set<String> REQUIRED_INDEXES = Set.of("uq_phantom_profiles_character_object_id", "idx_phantom_economy_operations_profile_state", "idx_phantom_economy_operations_character_state", "uq_phantom_economy_reservation_operation_ordinal", "idx_phantom_economy_reservations_owner", "idx_phantom_economy_reservations_profile_operation", "uq_phantom_economy_audit_operation", "idx_phantom_economy_audit_profile_created", "idx_phantom_economy_audit_profile_audit", "idx_phantom_economy_offers_initiator_goal", "idx_phantom_economy_offers_counterparty_state", "idx_phantom_economy_offers_state_expiry");
	private static final Set<String> REQUIRED_FOREIGN_KEYS = Set.of("fk_phantom_profile_components_profile", "fk_phantom_economy_operations_profile", "fk_phantom_economy_reservations_operation", "fk_phantom_economy_audit_profile", "fk_phantom_economy_offers_profile");

	private PhantomLocalPlayPreflight()
	{
	}

	public static void main(String[] args)
	{
		final int exitCode;
		try
		{
			exitCode = runMain(args);
		}
		catch (Throwable throwable)
		{
			System.err.println("[FAIL] PREFLIGHT_INTERNAL - " + throwable.getClass().getSimpleName());
			System.err.println("PHANTOM_LOCAL_PLAY_PREFLIGHT=FAIL");
			System.exit(3);
			return;
		}
		if (exitCode != 0)
		{
			System.exit(exitCode);
		}
	}

	static int runMain(String[] args) throws Exception
	{
		if (args.length != 1)
		{
			System.err.println("Usage: PhantomLocalPlayPreflight <production|test>");
			return 2;
		}
		final Path moduleRoot = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
		final DatabaseSettings database;
		if ("test".equals(args[0]))
		{
			final Path config = Path.of(System.getProperty("phantom.test.config", "")).toAbsolutePath().normalize();
			final ValidatedSettings guarded = PhantomTestDatabaseGuard.validate(moduleRoot, config);
			database = new DatabaseSettings(guarded.driver(), guarded.url(), guarded.login(), guarded.password());
		}
		else if ("production".equals(args[0]))
		{
			database = readDatabaseSettings(moduleRoot.resolve("dist/game/config/Database.ini"));
		}
		else
		{
			System.err.println("Unknown preflight mode: " + args[0]);
			return 2;
		}
		final Result result = run(new Request(moduleRoot, database));
		System.out.print(result.render());
		return result.status() == Status.FAIL ? 1 : 0;
	}

	public static Result run(Request request)
	{
		final List<Check> checks = new ArrayList<>();
		checks.addAll(validateRuntimeConfig(request.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini")));
		checks.addAll(validatePresetConfig(request.moduleRoot().resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini")));
		checks.addAll(validateData(request.moduleRoot().resolve("dist/game/data/phantoms")));
		checks.addAll(validateArtifacts(request.moduleRoot().resolve("dist")));
		checks.addAll(validateGeodata(request.moduleRoot().resolve("dist/game/data/geodata")));
		try
		{
			checks.add(Check.pass("DB_AVAILABLE", "Database metadata connection succeeded in read-only mode."));
			checks.addAll(validateSchema(inspectSchema(request.database())));
		}
		catch (ClassNotFoundException e)
		{
			checks.add(Check.fail("DB_DRIVER_UNAVAILABLE", "Configured JDBC driver class is unavailable."));
		}
		catch (SQLException e)
		{
			checks.add(Check.fail("DB_UNAVAILABLE", "Read-only metadata connection failed (SQLState=" + safeSqlState(e) + ", errorCode=" + e.getErrorCode() + ")."));
		}
		return new Result(List.copyOf(checks));
	}

	public static List<Check> validateRuntimeConfig(Path path)
	{
		final List<Check> shippedChecks = validateShippedConfig(path);
		if (shippedChecks.stream().noneMatch(check -> check.level() == Level.FAIL))
		{
			return shippedChecks;
		}
		final List<Check> presetChecks = validatePresetConfig(path);
		if (presetChecks.stream().noneMatch(check -> check.level() == Level.FAIL))
		{
			return List.of(Check.warning("RUNTIME_CONFIG_LOCAL_PLAY", "Runtime Phantom config matches the validated 10/5 local-play preset; restore the safe backup after testing."));
		}
		return List.of(Check.fail("RUNTIME_CONFIG_INVALID", "Runtime Phantom config matches neither the shipped safe defaults nor the validated 10/5 local-play preset."));
	}

	public static List<Check> validateShippedConfig(Path path)
	{
		final List<Check> checks = new ArrayList<>();
		final Properties properties;
		try
		{
			properties = readProperties(path);
		}
		catch (IOException e)
		{
			return List.of(Check.fail("SHIPPED_CONFIG_MISSING", "Shipped PhantomPlayers.ini is missing or unreadable."));
		}
		final boolean rawSafe = "false".equalsIgnoreCase(value(properties, "EnablePhantomSystem")) && "0".equals(value(properties, "PhantomPopulationTarget")) && "0".equals(value(properties, "PhantomPopulationActiveTarget"));
		final Settings parsed = PhantomPlayersConfig.read(path);
		if (rawSafe && !parsed.enabled() && (parsed.populationTarget() == 0) && (parsed.populationActiveTarget() == 0))
		{
			checks.add(Check.pass("SHIPPED_CONFIG_SAFE", "Committed Phantom config is fail-closed: False/0/0."));
		}
		else
		{
			checks.add(Check.fail("SHIPPED_CONFIG_UNSAFE", "Committed Phantom config must remain False/0/0."));
		}
		return checks;
	}

	public static List<Check> validatePresetConfig(Path path)
	{
		if (!Files.isRegularFile(path))
		{
			return List.of(Check.fail("PRESET_MISSING", "Local-play preset is missing."));
		}
		final Settings settings = PhantomPlayersConfig.read(path);
		if (!settings.enabled())
		{
			return List.of(Check.fail("PRESET_INVALID", "Local-play preset is disabled or has an invalid target/cap relation."));
		}
		final boolean expected = (settings.maxMaterializedPhantoms() == 32) && (settings.maxScheduledPhantomProfiles() == 10000) && (settings.schedulerPulseMillis() == 100) && (settings.schedulerProfilesPerPulse() == 128) && (settings.populationTarget() == 10) && (settings.populationActiveTarget() == 5) && (settings.populationCreationInFlight() == 2) && (settings.populationBoundariesPerPulse() == 64) && (settings.partyOperationsPerPulse() == 64) && (settings.socialCacheProfiles() == 1024) && "UTC".equals(settings.populationTimeZone().getId());
		return expected ? List.of(Check.pass("PRESET_RUNNABLE", "Local-play preset is enabled and conservative (population=10, active=5).")) : List.of(Check.fail("PRESET_UNEXPECTED", "Local-play preset does not match the validated 10/5 limits."));
	}

	public static List<Check> validateData(Path phantomDataRoot)
	{
		final List<Check> checks = new ArrayList<>();
		for (String relative : REQUIRED_PHANTOM_DATA)
		{
			final Path file = phantomDataRoot.resolve(relative);
			try
			{
				if (!Files.isRegularFile(file) || (Files.size(file) == 0))
				{
					checks.add(Check.fail("DATA_MISSING", relative));
				}
			}
			catch (IOException e)
			{
				checks.add(Check.fail("DATA_UNREADABLE", relative));
			}
		}
		if (checks.isEmpty())
		{
			checks.add(Check.pass("DATA_PACKS_READY", REQUIRED_PHANTOM_DATA.size() + " authoritative Phantom XML/TSV files are present and non-empty."));
		}
		return checks;
	}

	public static List<Check> validateArtifacts(Path distRoot)
	{
		final List<String> missing = new ArrayList<>();
		for (String relative : List.of("libs/GameServer.jar", "libs/LoginServer.jar"))
		{
			if (!Files.isRegularFile(distRoot.resolve(relative)))
			{
				missing.add(relative);
			}
		}
		return missing.isEmpty() ? List.of(Check.pass("RUNTIME_ARTIFACTS_READY", "GameServer.jar and LoginServer.jar are present.")) : List.of(Check.warning("RUNTIME_ARTIFACTS_MISSING", String.join(", ", missing) + "; run ant jar."));
	}

	public static List<Check> validateGeodata(Path geodataRoot)
	{
		long count = 0;
		if (Files.isDirectory(geodataRoot))
		{
			try (var files = Files.walk(geodataRoot))
			{
				count = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".l2j")).count();
			}
			catch (IOException e)
			{
				return List.of(Check.warning("GEODATA_DEGRADED", "Geodata cannot be inspected; navigation capability is degraded."));
			}
		}
		return count > 0 ? List.of(Check.pass("GEODATA_READY", count + " geodata files are available.")) : List.of(Check.warning("GEODATA_DEGRADED", "No .l2j geodata files found; server fallback is supported, but navigation fidelity is degraded."));
	}
	public static SchemaSnapshot inspectSchema(DatabaseSettings settings) throws SQLException, ClassNotFoundException
	{
		Class.forName(settings.driver());
		try (Connection connection = DriverManager.getConnection(settings.url(), settings.login(), settings.password()))
		{
			connection.setReadOnly(true);
			final DatabaseMetaData metadata = connection.getMetaData();
			final String catalog = connection.getCatalog();
			final Set<String> tables = new TreeSet<>();
			try (ResultSet rows = metadata.getTables(catalog, null, "%", new String[]
			{
				"TABLE"
			}))
			{
				while (rows.next())
				{
					tables.add(normalize(rows.getString("TABLE_NAME")));
				}
			}
			final Set<String> indexes = new TreeSet<>();
			final Set<String> foreignKeys = new TreeSet<>();
			for (String table : REQUIRED_TABLES)
			{
				try (ResultSet rows = metadata.getIndexInfo(catalog, null, table, false, false))
				{
					while (rows.next())
					{
						indexes.add(normalize(rows.getString("INDEX_NAME")));
					}
				}
				try (ResultSet rows = metadata.getImportedKeys(catalog, null, table))
				{
					while (rows.next())
					{
						foreignKeys.add(normalize(rows.getString("FK_NAME")));
					}
				}
			}
			return new SchemaSnapshot(Set.copyOf(tables), Set.copyOf(indexes), Set.copyOf(foreignKeys));
		}
	}

	public static List<Check> validateSchema(SchemaSnapshot schema)
	{
		final List<Check> checks = new ArrayList<>();
		for (String table : REQUIRED_TABLES)
		{
			if (!schema.tables().contains(table))
			{
				checks.add(Check.fail("SCHEMA_TABLE_MISSING", table));
			}
		}
		for (String index : REQUIRED_INDEXES)
		{
			if (!schema.indexes().contains(index))
			{
				checks.add(Check.fail("SCHEMA_INDEX_MISSING", index));
			}
		}
		for (String foreignKey : REQUIRED_FOREIGN_KEYS)
		{
			if (!schema.foreignKeys().contains(foreignKey))
			{
				checks.add(Check.fail("SCHEMA_CONSTRAINT_MISSING", foreignKey));
			}
		}
		if (checks.isEmpty())
		{
			checks.add(Check.pass("PHANTOM_SCHEMA_READY", REQUIRED_TABLES.size() + " tables, " + REQUIRED_INDEXES.size() + " indexes and " + REQUIRED_FOREIGN_KEYS.size() + " foreign keys are present."));
		}
		return checks;
	}

	private static DatabaseSettings readDatabaseSettings(Path path) throws IOException
	{
		final Properties properties = readProperties(path);
		return new DatabaseSettings(require(properties, "Driver"), require(properties, "URL"), require(properties, "Login"), require(properties, "Password"));
	}

	private static Properties readProperties(Path path) throws IOException
	{
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		return properties;
	}

	private static String require(Properties properties, String key) throws IOException
	{
		final String value = value(properties, key);
		if (value.isEmpty())
		{
			throw new IOException("Missing required database setting: " + key);
		}
		return value;
	}

	private static String value(Properties properties, String key)
	{
		return properties.getProperty(key, "").trim();
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static String safeSqlState(SQLException exception)
	{
		final String sqlState = exception.getSQLState();
		return (sqlState == null) || !sqlState.matches("[A-Za-z0-9]+") ? "unknown" : sqlState;
	}

	public enum Status
	{
		PASS,
		PASS_WITH_WARNINGS,
		FAIL
	}

	public enum Level
	{
		PASS,
		WARNING,
		FAIL
	}

	public record DatabaseSettings(String driver, String url, String login, String password)
	{
		public DatabaseSettings
		{
			if ((driver == null) || driver.isBlank() || (url == null) || url.isBlank() || (login == null) || login.isBlank() || (password == null))
			{
				throw new IllegalArgumentException("Complete database settings are required.");
			}
		}
	}

	public record Request(Path moduleRoot, DatabaseSettings database)
	{
		public Request
		{
			moduleRoot = moduleRoot.toAbsolutePath().normalize();
		}
	}

	public record SchemaSnapshot(Set<String> tables, Set<String> indexes, Set<String> foreignKeys)
	{
		public SchemaSnapshot
		{
			tables = normalizedCopy(tables);
			indexes = normalizedCopy(indexes);
			foreignKeys = normalizedCopy(foreignKeys);
		}

		private static Set<String> normalizedCopy(Set<String> values)
		{
			final Set<String> normalized = new LinkedHashSet<>();
			for (String value : values)
			{
				normalized.add(normalize(value));
			}
			return Set.copyOf(normalized);
		}
	}
	public record Check(Level level, String code, String detail)
	{
		public Check
		{
			if ((level == null) || (code == null) || code.isBlank() || (detail == null))
			{
				throw new IllegalArgumentException("Complete preflight check data is required.");
			}
		}

		public static Check pass(String code, String detail)
		{
			return new Check(Level.PASS, code, detail);
		}

		public static Check warning(String code, String detail)
		{
			return new Check(Level.WARNING, code, detail);
		}

		public static Check fail(String code, String detail)
		{
			return new Check(Level.FAIL, code, detail);
		}
	}

	public record Result(List<Check> checks)
	{
		public Result
		{
			checks = List.copyOf(checks);
		}

		public Status status()
		{
			if (checks.stream().anyMatch(check -> check.level() == Level.FAIL))
			{
				return Status.FAIL;
			}
			return checks.stream().anyMatch(check -> check.level() == Level.WARNING) ? Status.PASS_WITH_WARNINGS : Status.PASS;
		}

		public String render()
		{
			final StringBuilder output = new StringBuilder();
			for (Check check : checks)
			{
				output.append('[').append(check.level()).append("] ").append(check.code()).append(" - ").append(check.detail()).append(System.lineSeparator());
			}
			output.append("PHANTOM_LOCAL_PLAY_PREFLIGHT=").append(status()).append(System.lineSeparator());
			return output.toString();
		}
	}
}