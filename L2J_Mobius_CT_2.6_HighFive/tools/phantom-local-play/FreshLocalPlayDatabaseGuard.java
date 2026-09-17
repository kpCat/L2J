/*
 * Local-play provisioning helper. This is tooling, not GameServer runtime code.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FreshLocalPlayDatabaseGuard
{
	private static final Pattern SAFE_DATABASE = Pattern.compile("[A-Za-z0-9_]{1,48}");
	private static final Pattern CREATE_TABLE = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?");
	private static final Set<String> PROTECTED_DATABASES = Set.of("l2jmobiush5", "l2jmobiush5_phantom_test", "mysql", "information_schema", "performance_schema", "sys");
	private static final List<String> EMPTY_PLAYER_TABLES = List.of(
		"accounts",
		"characters",
		"items",
		"character_variables",
		"character_quests",
		"character_offline_play",
		"character_offline_play_group",
		"character_offline_trade",
		"character_offline_trade_items",
		"clan_data",
		"clan_subpledges",
		"clan_wars",
		"phantom_profiles",
		"phantom_profile_components",
		"phantom_economy_operations",
		"phantom_economy_reservations",
		"phantom_economy_audit",
		"phantom_economy_offers");

	private FreshLocalPlayDatabaseGuard()
	{
	}

	public static void main(String[] args)
	{
		if ((args.length < 1) || (args.length > 2))
		{
			fail("invalid helper arguments");
		}

		try
		{
			final SecretInput input = readInput();
			validateDatabase(input.database());
			switch (args[0])
			{
				case "assert-absent" -> assertAbsent(input);
				case "create-fresh" -> createFresh(input);
				case "verify-schema-empty" -> verifySchemaAndEmpty(input, requirePath(args));
				case "verify-registered" -> verifyRegistered(input, requirePath(args));
				case "verify-no-player-account" -> verifyNoPlayerAccount(input);
				case "verify-population-started" -> verifyPopulationStarted(input);
				default -> fail("unknown helper operation");
			}
		}
		catch (SQLException e)
		{
			fail("database operation failed (SQLState=" + safe(e.getSQLState()) + ", code=" + e.getErrorCode() + ")");
		}
		catch (Exception e)
		{
			fail("verification failed: " + safe(e.getMessage()));
		}
	}

	private static Path requirePath(String[] args)
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("required path argument is missing");
		}
		return Path.of(args[1]).toAbsolutePath().normalize();
	}

	private static SecretInput readInput() throws IOException
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)))
		{
			final String serverUrl = requireLine(reader, "server URL");
			final String login = requireLine(reader, "login");
			final String password = requireLine(reader, "password", true);
			final String database = requireLine(reader, "database");
			return new SecretInput(serverUrl, login, password, database);
		}
	}

	private static String requireLine(BufferedReader reader, String name) throws IOException
	{
		return requireLine(reader, name, false);
	}

	private static String requireLine(BufferedReader reader, String name, boolean allowEmpty) throws IOException
	{
		final String value = reader.readLine();
		if ((value == null) || (!allowEmpty && value.isBlank()))
		{
			throw new IllegalArgumentException(name + " is missing");
		}
		return value;
	}

	private static void validateDatabase(String database)
	{
		if (!SAFE_DATABASE.matcher(database).matches() || PROTECTED_DATABASES.contains(database.toLowerCase()))
		{
			throw new IllegalArgumentException("database name is not permitted");
		}
	}

	private static void assertAbsent(SecretInput input) throws SQLException
	{
		try (Connection connection = openServer(input))
		{
			if (databaseExists(connection, input.database()))
			{
				throw new IllegalStateException("target database already exists; it was not changed");
			}
		}
		System.out.println("ABSENT database=" + input.database());
	}

	private static void createFresh(SecretInput input) throws SQLException
	{
		try (Connection connection = openServer(input); Statement statement = connection.createStatement())
		{
			if (databaseExists(connection, input.database()))
			{
				throw new IllegalStateException("target database already exists; it was not changed");
			}
			statement.execute("CREATE DATABASE `" + input.database() + "`");
			if (!databaseExists(connection, input.database()))
			{
				throw new IllegalStateException("fresh database creation was not observable");
			}
		}
		System.out.println("CREATED database=" + input.database());
	}

	private static void verifySchemaAndEmpty(SecretInput input, Path sqlRoot) throws Exception
	{
		final Set<String> expectedTables = discoverCanonicalTables(sqlRoot);
		try (Connection connection = openDatabase(input))
		{
			assertCatalog(connection, input.database());
			for (String table : expectedTables)
			{
				if (!tableExists(connection, input.database(), table))
				{
					throw new IllegalStateException("canonical table is missing: " + table);
				}
			}
			for (String table : EMPTY_PLAYER_TABLES)
			{
				if (!tableExists(connection, input.database(), table))
				{
					throw new IllegalStateException("freshness table is missing: " + table);
				}
				if (count(connection, table) != 0)
				{
					throw new IllegalStateException("freshness table is not empty: " + table);
				}
			}
			if (countAccount(connection, "localplayer") != 0)
			{
				throw new IllegalStateException("localplayer account already exists");
			}
		}
		System.out.println("SCHEMA_EMPTY_PASS database=" + input.database() + " canonicalTables=" + expectedTables.size() + " playerTables=" + EMPTY_PLAYER_TABLES.size());
	}

	private static void verifyRegistered(SecretInput input, Path hexidPath) throws Exception
	{
		final Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(hexidPath, StandardCharsets.ISO_8859_1))
		{
			properties.load(reader);
		}
		final int serverId = Integer.parseInt(properties.getProperty("ServerID", ""));
		final String hexId = properties.getProperty("HexID", "");
		if ((serverId != 1) || hexId.isBlank())
		{
			throw new IllegalStateException("runtime hexid does not contain canonical server ID 1");
		}
		try (Connection connection = openDatabase(input))
		{
			assertCatalog(connection, input.database());
			try (PreparedStatement statement = connection.prepareStatement("SELECT server_id, hexid FROM gameservers"); ResultSet result = statement.executeQuery())
			{
				if (!result.next() || (result.getInt(1) != serverId) || !hexId.equals(result.getString(2)) || result.next())
				{
					throw new IllegalStateException("gameservers does not contain exactly the fresh runtime identity");
				}
			}
			if (countAccount(connection, "localplayer") != 0)
			{
				throw new IllegalStateException("localplayer account was created during provisioning");
			}
		}
		System.out.println("REGISTERED_PASS database=" + input.database() + " serverId=" + serverId);
	}

	private static void verifyNoPlayerAccount(SecretInput input) throws SQLException
	{
		try (Connection connection = openDatabase(input))
		{
			assertCatalog(connection, input.database());
			if (countAccount(connection, "localplayer") != 0)
			{
				throw new IllegalStateException("localplayer account exists before manual login");
			}
		}
		System.out.println("NO_PLAYER_ACCOUNT_PASS database=" + input.database());
	}

	private static void verifyPopulationStarted(SecretInput input) throws SQLException
	{
		try (Connection connection = openDatabase(input))
		{
			assertCatalog(connection, input.database());
			final long profiles = count(connection, "phantom_profiles");
			final long characters = count(connection, "characters");
			if ((profiles <= 0) || (characters <= 0))
			{
				throw new IllegalStateException("Phantom scheduler has not persisted population");
			}
			if (countAccount(connection, "localplayer") != 0)
			{
				throw new IllegalStateException("localplayer account exists before manual login");
			}
			System.out.println("POPULATION_STARTED_PASS database=" + input.database() + " phantomProfiles=" + profiles + " characters=" + characters);
		}
	}

	private static Set<String> discoverCanonicalTables(Path sqlRoot) throws IOException
	{
		final Set<String> tables = new LinkedHashSet<>();
		for (String type : List.of("login", "game"))
		{
			final Path directory = sqlRoot.resolve(type);
			final List<Path> scripts = new ArrayList<>();
			try (var stream = Files.list(directory))
			{
				stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".sql")).sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).forEach(scripts::add);
			}
			for (Path script : scripts)
			{
				final Matcher matcher = CREATE_TABLE.matcher(Files.readString(script, StandardCharsets.UTF_8));
				while (matcher.find())
				{
					tables.add(matcher.group(1));
				}
			}
		}
		if (tables.isEmpty())
		{
			throw new IllegalStateException("canonical SQL table inventory is empty");
		}
		return tables;
	}

	private static Connection openServer(SecretInput input) throws SQLException
	{
		return DriverManager.getConnection(input.serverUrl(), input.login(), input.password());
	}

	private static Connection openDatabase(SecretInput input) throws SQLException
	{
		final int query = input.serverUrl().indexOf('?');
		final String base = query >= 0 ? input.serverUrl().substring(0, query) : input.serverUrl();
		final String suffix = query >= 0 ? input.serverUrl().substring(query) : "";
		return DriverManager.getConnection(base.replaceAll("/+$", "") + "/" + input.database() + suffix, input.login(), input.password());
	}

	private static boolean databaseExists(Connection connection, String database) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?"))
		{
			statement.setString(1, database);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() && (result.getLong(1) == 1);
			}
		}
	}

	private static boolean tableExists(Connection connection, String database, String table) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?"))
		{
			statement.setString(1, database);
			statement.setString(2, table);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() && (result.getLong(1) == 1);
			}
		}
	}

	private static long count(Connection connection, String table) throws SQLException
	{
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`"))
		{
			return result.next() ? result.getLong(1) : -1;
		}
	}

	private static long countAccount(Connection connection, String account) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login = ?"))
		{
			statement.setString(1, account);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() ? result.getLong(1) : -1;
			}
		}
	}

	private static void assertCatalog(Connection connection, String database) throws SQLException
	{
		if (!database.equalsIgnoreCase(connection.getCatalog()))
		{
			throw new IllegalStateException("connection catalog is not the guarded target");
		}
	}

	private static String safe(String value)
	{
		if (value == null)
		{
			return "unknown";
		}
		return value.replaceAll("[^A-Za-z0-9_. -]", "?");
	}

	private static void fail(String message)
	{
		System.err.println("ERROR: " + message);
		System.exit(1);
	}

	private record SecretInput(String serverUrl, String login, String password, String database)
	{
	}
}
