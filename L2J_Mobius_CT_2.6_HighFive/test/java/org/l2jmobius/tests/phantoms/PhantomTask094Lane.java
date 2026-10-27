/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

/** Exact create-only OWN094 authority; no historical lane or production utility is widened. */
public final class PhantomTask094Lane
{
	static final String SCHEMA = "l2jmobiush5_phantom_test_094";
	private static final String ADMIN_URL = "jdbc:mysql://127.0.0.1:3308/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
	private static final String TARGET_URL = "jdbc:mysql://127.0.0.1:3308/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
	private static final String BASELINE = "9a599e8bed06b6ea874d97e9f1d890a5297511a8";
	private static final String PACKAGE_SHA = "9af50eb807a8291e581c2ff424a52565f975ac76c9a98c0107bd3c17d66f6cb8";
	private static String phase = "PREFLIGHT";
	private static final java.util.regex.Pattern ABSENT_DROP = java.util.regex.Pattern.compile("(?is)\\s*DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`?([A-Za-z0-9_]+)`?\\s*");

	private PhantomTask094Lane()
	{
	}

	private static void require(boolean valid, String reason)
	{
		if (!valid)
		{
			throw new IllegalArgumentException("TASK094_" + reason);
		}
	}

	private static Path physical(Path path) throws Exception
	{
		final Path absolute = path.toAbsolutePath().normalize();
		require(absolute.equals(path.toAbsolutePath()) && absolute.equals(path.toRealPath()) && !Files.isSymbolicLink(path), "PHYSICAL_PATH");
		for (Path parent = absolute; parent != null; parent = parent.getParent())
		{
			require(!Files.isSymbolicLink(parent) && !Files.readAttributes(parent, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther(), "REPARSE_PATH");
		}
		return absolute;
	}

	private static Properties read(Path path) throws Exception
	{
		physical(path);
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 16384, "BOUNDED_CONFIG");
		final var properties = new Properties();
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		return properties;
	}

	private static void targetUrl(String url)
	{
		require(TARGET_URL.equals(url), "JDBC_EXACT");
	}

	private static String hash(Path file) throws Exception
	{
		physical(file);
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(file))
		{
			final byte[] buffer = new byte[65536];
			for (int count; (count = input.read(buffer)) >= 0;)
			{
				digest.update(buffer, 0, count);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void privateAcl(Path stage, Path file) throws Exception
	{
		final var owner = Files.getOwner(physical(stage));
		require(owner.equals(file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"))), "ACL_OWNER");
		final var system = file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("SYSTEM");
		final var acl = Files.getFileAttributeView(physical(file), AclFileAttributeView.class);
		require(acl != null, "ACL_UNAVAILABLE");
		for (var rule : acl.getAcl())
		{
			require(rule.type() != AclEntryType.ALLOW || rule.principal().equals(owner) || rule.principal().equals(system), "PRIVATE_ACL");
		}
	}

	private static void ownerFields(Properties owner, Path source, Path stage, Path shadow) throws Exception
	{
		require("TASK094_PARTY_FARM".equals(owner.getProperty("owner")) && "94".equals(owner.getProperty("taskId")) && SCHEMA.equals(owner.getProperty("schema")), "OWNER_SCHEMA");
		require(BASELINE.equals(owner.getProperty("baseline")) && PACKAGE_SHA.equals(owner.getProperty("packageSha256")), "BASELINE_PACKAGE");
		final String uuid = owner.getProperty("installationId", "");
		require(UUID.fromString(uuid).toString().equals(uuid), "OWNER_UUID");
		require(physical(source).equals(physical(Path.of(owner.getProperty("sourceModule", "")))) && physical(stage).equals(physical(Path.of(owner.getProperty("stage", "")))) && physical(shadow).equals(physical(Path.of(owner.getProperty("shadowModule", "")))), "OWNER_PATH");
		require(!stage.startsWith(source.getParent()) && shadow.equals(stage.resolve("L2J_Mobius_CT_2.6_HighFive")), "EXTERNAL_SHADOW");
		require(Instant.now().isBefore(Instant.parse(owner.getProperty("expiresUtc"))), "EXPIRED");
	}

	private static Properties owner(Path source, Path stage) throws Exception
	{
		privateAcl(stage, stage);
		privateAcl(stage, stage.resolve("private/owner.properties"));
		final Properties owner = read(stage.resolve("private/owner.properties"));
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		ownerFields(owner, source, stage, shadow);
		require(owner.getProperty("installationId").equals(System.getProperty("phantom.task094.uuid", "")), "CALLER_UUID");
		require(hash(source.resolve("dist/game/config/Database.ini")).equals(owner.getProperty("sourceDatabaseSha256")), "SOURCE_DATABASE_DRIFT");
		require(hash(source.resolve("docs/phantoms/tasks/PHANTOM-AUTONOMOUS-PARTY-FARM-094/MANIFEST_SHA256.json")).equals(PACKAGE_SHA), "PACKAGE_DRIFT");
		require(hash(stage.resolve("inputs.tsv")).equals(owner.getProperty("inputsSha256")), "INPUT_MANIFEST_DRIFT");
		final Path geo = physical(Path.of(owner.getProperty("geoSource")));
		require(geo.equals(source.getParent().resolve(".phantom-local/task074/runtime-test/game/data/geodata")), "GEO_SOURCE");
		require(hash(source.getParent().resolve(".phantom-local/task074/GEODATA074.tsv")).equals(owner.getProperty("geoPinSha256")), "GEO_PIN_DRIFT");
		int count = 0, regions = 0;
		for (String line : Files.readAllLines(stage.resolve("inputs.tsv"), StandardCharsets.UTF_8))
		{
			final String[] fields = line.split("\t", -1);
			require(fields.length == 3 && fields[2].matches("[0-9a-f]{64}"), "INPUT_ROW");
			final Path origin = physical(Path.of(fields[0])), copy = physical(Path.of(fields[1]));
			require((origin.startsWith(source) || origin.startsWith(geo)) && copy.startsWith(shadow) && !copy.equals(shadow.resolve("dist/game/config/Database.ini")), "INPUT_ESCAPE");
			require(hash(origin).equals(fields[2]), "SOURCE_INPUT_DRIFT");
			if (Files.exists(stage.resolve("pin.complete")) && copy.equals(shadow.resolve("dist/game/config/IdManager.ini")))
			{
				require(Files.readString(copy, StandardCharsets.UTF_8).equals(Files.readString(origin, StandardCharsets.UTF_8).replace("DatabaseCleanUp = True", "DatabaseCleanUp = False")), "CLEANUP_OVERLAY_DRIFT");
			}
			else { require(hash(copy).equals(fields[2]), "SHADOW_INPUT_DRIFT"); }
			if (origin.startsWith(geo))
			{
				require(origin.getFileName().toString().matches("[0-9]{2}_[0-9]{2}\\.l2j") && copy.equals(shadow.resolve("dist/game/data/geodata").resolve(origin.getFileName())), "GEO_REGION");
				regions++;
			}
			count++;
		}
		require(count > 1000 && regions == 203 && "203".equals(owner.getProperty("geoRegions")), "INPUT_COUNT_GEO");
		require(hash(shadow.resolve("dist/db_installer/sql/game/economy071.sql")).equals(owner.getProperty("migrationSha256")), "MIGRATION_DRIFT");
		return owner;
	}

	private static void writeProperties(Path path, Properties properties) throws Exception
	{
		try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))
		{
			properties.store(writer, "TASK094 private authority");
		}
	}

	private static void pin(Path source, Path stage) throws Exception
	{
		final Properties authority = owner(source, stage);
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		require(!Files.exists(stage.resolve("pin.started")) && !Files.exists(stage.resolve("pin.complete")) && !Files.exists(stage.resolve("bootstrap.started")) && !Files.exists(shadow.resolve("dist/game/config/Database.ini")) && !Files.exists(stage.resolve("private/IdManager.safe.ini")), "PARTIAL_PIN_STOP");
		Files.writeString(stage.resolve("pin.started"), authority.getProperty("installationId"), StandardOpenOption.CREATE_NEW);
		final Properties origin = read(source.resolve("dist/game/config/Database.ini"));
		final Properties settings = new Properties();
		for (String key : new String[] {"Driver", "Login", "Password"})
		{
			require(origin.containsKey(key), "SOURCE_CREDENTIALS");
			settings.setProperty(key, origin.getProperty(key));
		}
		settings.setProperty("URL", TARGET_URL);
		settings.setProperty("MaximumDatabaseConnections", "4");
		settings.setProperty("TestDatabaseConnections", "False");
		settings.setProperty("BackupDatabase", "False");
		writeProperties(shadow.resolve("dist/game/config/Database.ini"), settings);
		privateAcl(stage, shadow.resolve("dist/game/config/Database.ini"));
		// The canonical source cleanup input remains part of the checksum inventory.
		final Path idManager = shadow.resolve("dist/game/config/IdManager.ini");
		final String idInput = Files.readString(idManager, StandardCharsets.UTF_8);
		require(idInput.contains("DatabaseCleanUp = True") && idInput.indexOf("DatabaseCleanUp = True") == idInput.lastIndexOf("DatabaseCleanUp = True"), "CLEANUP_PREIMAGE");
		Files.writeString(stage.resolve("private/IdManager.safe.ini"), idInput.replace("DatabaseCleanUp = True", "DatabaseCleanUp = False"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Files.writeString(idManager, idInput.replace("DatabaseCleanUp = True", "DatabaseCleanUp = False"), StandardCharsets.UTF_8);
		final var schema = PhantomTestSchemaManifest.current(shadow);
		final Properties seal = new Properties();
		seal.setProperty("installationId", authority.getProperty("installationId"));
		seal.setProperty("sqlAggregate", schema.aggregateSha256());
		seal.setProperty("databaseSha256", hash(shadow.resolve("dist/game/config/Database.ini")));
		seal.setProperty("safeCleanupSha256", hash(stage.resolve("private/IdManager.safe.ini")));
		writeProperties(stage.resolve("pin.complete"), seal);
		System.out.println("TASK094_PIN_PASS Geo=203 sqlScripts=" + schema.scriptCount() + " sqlAggregate=" + schema.aggregateSha256());
	}

	private static Properties settings(Path stage) throws Exception
	{
		final Path config = stage.resolve("L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini");
		privateAcl(stage, config);
		final Properties seal = read(stage.resolve("pin.complete"));
		require(read(stage.resolve("private/owner.properties")).getProperty("installationId").equals(seal.getProperty("installationId")) && seal.getProperty("installationId").equals(System.getProperty("phantom.task094.uuid", "")), "PIN_UUID");
		require(hash(config).equals(seal.getProperty("databaseSha256")), "PRIVATE_CONFIG_DRIFT");
		require(hash(stage.resolve("L2J_Mobius_CT_2.6_HighFive/dist/game/config/IdManager.ini")).equals(seal.getProperty("safeCleanupSha256")) && "False".equals(read(stage.resolve("L2J_Mobius_CT_2.6_HighFive/dist/game/config/IdManager.ini")).getProperty("DatabaseCleanUp")), "CLEANUP_DISABLED");
		final Properties settings = read(config);
		targetUrl(settings.getProperty("URL"));
		require("com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver")) && !settings.getProperty("Login", "").isBlank() && settings.containsKey("Password") && "4".equals(settings.getProperty("MaximumDatabaseConnections")) && "False".equals(settings.getProperty("TestDatabaseConnections")) && "False".equals(settings.getProperty("BackupDatabase")), "BOUNDED_SETTINGS");
		return settings;
	}

	private static Connection connect(Properties settings, boolean admin) throws Exception
	{
		Class.forName(settings.getProperty("Driver"));
		DriverManager.setLoginTimeout(10);
		return DriverManager.getConnection(admin ? ADMIN_URL : settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
	}

	private static void quiescent(Connection connection) throws Exception
	{
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon'"))
		{
			require(rows.next() && rows.getLong(1) == 0, "FOREIGN_CONNECTIONS_STOP");
		}
	}

	private static void absent(Connection connection) throws Exception
	{
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='l2jmobiush5_phantom_test_094'"))
		{
			require(rows.next() && rows.getLong(1) == 0, "EXISTING_SCHEMA_STOP");
		}
	}

	private static String foreignMetadata(Connection connection) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		for (String sql : new String[] {"SELECT SCHEMA_NAME,DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME<>'l2jmobiush5_phantom_test_094' ORDER BY SCHEMA_NAME", "SELECT TABLE_SCHEMA,TABLE_NAME,TABLE_TYPE,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA NOT IN ('information_schema','performance_schema','l2jmobiush5_phantom_test_094') ORDER BY TABLE_SCHEMA,TABLE_NAME"})
		{
			try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql))
			{
				while (rows.next())
				{
					for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++)
					{
						digest.update((String.valueOf(rows.getString(index)) + "\t").getBytes(StandardCharsets.UTF_8));
					}
					digest.update((byte) '\n');
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void safeSql(String sql)
	{
		if (ABSENT_DROP.matcher(sql).matches())
		{
			return;
		}
		// These additional seed/index forms occur in the pinned canonical installer.
		require(sql.stripLeading().matches("(?is)(CREATE\\s+TABLE|CREATE\\s+(?:UNIQUE\\s+)?INDEX|INSERT\\s+(?:IGNORE\\s+)?INTO|REPLACE\\s+INTO|ALTER\\s+TABLE|SET\\s+(NAMES|FOREIGN_KEY_CHECKS|CHARACTER_SET_CLIENT|CHARACTER_SET_RESULTS|COLLATION_CONNECTION))\\b.*"), "UNSAFE_SQL_VERB");
		final StringBuilder identifiers = new StringBuilder();
		char quote = 0;
		for (int index = 0; index < sql.length(); index++)
		{
			final char value = sql.charAt(index);
			if (quote != 0)
			{
				if (value == '\\') { index++; }
				else if (value == quote)
				{
					if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) { index++; }
					else { quote = 0; }
				}
				identifiers.append(' ');
			}
			else if (value == '\'' || value == '"') { quote = value; identifiers.append(' '); }
			else { identifiers.append(value); }
		}
		require(!java.util.regex.Pattern.compile("[`A-Za-z_][`A-Za-z0-9_]*\\s*\\.\\s*[`A-Za-z_]").matcher(identifiers).find(), "EXTERNAL_SQL_REFERENCE");
	}

	private static void owned(Connection connection, Properties authority, PhantomTestSchemaManifest.Snapshot schema) throws Exception
	{
		require(SCHEMA.equals(connection.getCatalog()), "CATALOG");
		try (var query = connection.prepareStatement("SELECT task_id,installation_id,schema_version,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
		{
			query.setString(1, SCHEMA);
			try (var rows = query.executeQuery())
			{
				require(rows.next() && rows.getInt(1) == 94 && authority.getProperty("installationId").equals(rows.getString(2)) && rows.getInt(3) == 1 && authority.getProperty("migrationSha256").equals(rows.getString(4)) && !rows.next(), "DATABASE_AUTHORITY");
			}
		}
		PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, schema);
	}

	private static void bootstrap(Path source, Path stage) throws Exception
	{
		final Properties authority = owner(source, stage), settings = settings(stage);
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		final var scripts = PhantomTestSchemaManifest.inventory(shadow);
		final var schema = PhantomTestSchemaManifest.current(shadow);
		require(schema.aggregateSha256().equals(read(stage.resolve("pin.complete")).getProperty("sqlAggregate")), "SQL_PIN");
		for (var script : scripts) { for (String sql : script.statements()) { safeSql(sql); } }
		require(!Files.exists(stage.resolve("bootstrap.started")) && !Files.exists(stage.resolve("bootstrap.complete")), "NO_BOOTSTRAP_RETRY");
		try (var admin = connect(settings, true))
		{
			absent(admin);
			quiescent(admin);
			final String before = foreignMetadata(admin);
			Files.writeString(stage.resolve("foreign-metadata.before.sha256"), before, StandardOpenOption.CREATE_NEW);
			Files.writeString(stage.resolve("bootstrap.started"), authority.getProperty("installationId"), StandardOpenOption.CREATE_NEW);
			phase = "BOOTSTRAP_MUTATING";
			try (var statement = admin.createStatement())
			{
				statement.execute("CREATE DATABASE `l2jmobiush5_phantom_test_094` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
			}
		}
		int omitted = 0;
		try (var connection = connect(settings, false))
		{
			require(SCHEMA.equals(connection.getCatalog()), "NEW_CATALOG");
			quiescent(connection);
			final int[] noops = {0};
			for (var script : scripts)
			{
				try (var statement = connection.createStatement())
				{
					StrictSqlScriptRunner.executeStatements(script, sql ->
					{
						final var drop = ABSENT_DROP.matcher(sql);
						if (drop.matches())
						{
							try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?"))
							{
								query.setString(1, SCHEMA); query.setString(2, drop.group(1));
								try (var rows = query.executeQuery())
								{
									if (!rows.next() || rows.getLong(1) != 0) { throw new SQLException("TASK094_DROP_EXISTING_TABLE_REFUSED"); }
								}
							}
							noops[0]++;
							return;
						}
						safeSql(sql);
						statement.execute(sql);
					});
				}
			}
			omitted = noops[0];
			try (var insert = connection.prepareStatement("INSERT INTO economy071_authority(schema_name,task_id,installation_id,schema_version,migration_sha256) VALUES(?,94,?,1,?)"))
			{
				insert.setString(1, SCHEMA); insert.setString(2, authority.getProperty("installationId")); insert.setString(3, authority.getProperty("migrationSha256")); insert.executeUpdate();
			}
			PhantomTestSchemaManifest.writeDatabaseMetadata(connection, schema);
			owned(connection, authority, schema);
			final String hex = UUID.randomUUID().toString().replace("-", "");
			try (var insert = connection.prepareStatement("INSERT INTO gameservers(server_id,hexid,host) VALUES(1,?,'')"))
			{
				insert.setString(1, hex); insert.executeUpdate();
			}
			final Properties registration = new Properties();
			registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
			writeProperties(stage.resolve("private/hexid-test.txt"), registration);
			quiescent(connection);
			require(Files.readString(stage.resolve("foreign-metadata.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
		}
		Files.writeString(stage.resolve("bootstrap.complete"), "owner=TASK094_PARTY_FARM\ninstallationId=" + authority.getProperty("installationId") + "\nsqlAggregate=" + schema.aggregateSha256() + "\nabsentDropNoops=" + omitted + "\n", StandardOpenOption.CREATE_NEW);
		System.out.println("TASK094_CREATE_ONLY_BOOTSTRAP_PASS scripts=" + scripts.size() + " absentDropNoops=" + omitted + " DROP_TRUNCATE_DELETE=0 foreignMetadataUnchanged=true");
	}

	private static void verifyPin(Path source, Path stage) throws Exception
	{
		final Properties authority = owner(source, stage);
		settings(stage);
		require(Files.readString(stage.resolve("pin.started")).equals(authority.getProperty("installationId")), "PIN_START_UUID");
		final var schema = PhantomTestSchemaManifest.current(stage.resolve("L2J_Mobius_CT_2.6_HighFive"));
		require(schema.aggregateSha256().equals(read(stage.resolve("pin.complete")).getProperty("sqlAggregate")), "SQL_PIN");
		for (var script : PhantomTestSchemaManifest.inventory(stage.resolve("L2J_Mobius_CT_2.6_HighFive")))
		{
			for (String sql : script.statements()) { safeSql(sql); }
		}
		System.out.println("TASK094_INDEPENDENT_PIN_VERIFY_PASS Geo=203 scripts=" + schema.scriptCount() + " SQL_DDL_DML=0");
	}

	private static void verify(Path source, Path stage) throws Exception
	{
		verifyPin(source, stage);
		final Properties authority = read(stage.resolve("private/owner.properties"));
		final Properties complete = read(stage.resolve("bootstrap.complete"));
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		final var schema = PhantomTestSchemaManifest.current(shadow);
		require(authority.getProperty("installationId").equals(complete.getProperty("installationId")) && authority.getProperty("installationId").equals(Files.readString(stage.resolve("bootstrap.started"))) && schema.aggregateSha256().equals(complete.getProperty("sqlAggregate")), "BOOTSTRAP_RECEIPTS");
		try (var connection = connect(settings(stage), false))
		{
			connection.setReadOnly(true);
			owned(connection, authority, schema);
			quiescent(connection);
			require(Files.readString(stage.resolve("foreign-metadata.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
			final Properties registration = read(stage.resolve("private/hexid-test.txt"));
			require("1".equals(registration.getProperty("ServerID")) && registration.getProperty("HexID", "").matches("[0-9a-f]{32}"), "REGISTRATION_FILE");
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT server_id,hexid,host FROM gameservers ORDER BY server_id"))
			{
				require(rows.next() && rows.getInt(1) == 1 && registration.getProperty("HexID").equals(rows.getString(2)) && "".equals(rows.getString(3)), "REGISTRATION_DATABASE");
				// Preserve the public canonical gameservers.sql seed; never DELETE it for a singleton assertion.
				require(rows.next() && rows.getInt(1) == 2 && "-2ad66b3f483c22be097019f55c8abdf0".equals(rows.getString(2)) && "".equals(rows.getString(3)) && !rows.next(), "CANONICAL_REGISTRATION_SEED");
			}
			final var expectedTables = new java.util.TreeSet<String>();
			for (var script : PhantomTestSchemaManifest.inventory(shadow))
			{
				for (String sql : script.statements())
				{
					final var table = java.util.regex.Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\(").matcher(sql);
					if (table.find()) { expectedTables.add(table.group(1).toLowerCase(java.util.Locale.ROOT)); }
				}
			}
			final var actualTables = new java.util.TreeSet<String>();
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='l2jmobiush5_phantom_test_094'"))
			{
				while (rows.next())
				{
					require("BASE TABLE".equals(rows.getString(2)) && rows.getString(3) != null, "TABLE_METADATA");
					actualTables.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT));
				}
			}
			require(!expectedTables.isEmpty() && expectedTables.equals(actualTables), "TABLE_INVENTORY");
			System.out.println("TASK094_READONLY_VERIFY_PASS tables=" + actualTables.size() + " authority=true registration=true foreignMetadataUnchanged=true DDL_DML=0");
		}
		try (var connection = connect(settings(stage), true))
		{
			connection.setReadOnly(true);
			quiescent(connection);
		}
		System.out.println("TASK094_POSTFLIGHT_PASS priorOwnConnections=0 foreignConnections=0");
	}

	@FunctionalInterface private interface Check { void run() throws Exception; }

	private static void noNativeFlags()
	{
		for (String key : System.getProperties().stringPropertyNames())
		{
			require(!(key.startsWith("phantom.task") && key.endsWith(".native") && Boolean.getBoolean(key)), "NATIVE_NOT_AUTHORIZED");
		}
	}

	private static void rejected(Check check) throws Exception
	{
		try { check.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError("TASK094_NEGATIVE_ACCEPTED");
	}

	private static void stageGuards(Path source, Path stage) throws Exception
	{
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		final Properties authority = read(stage.resolve("private/owner.properties"));
		ownerFields(authority, source, stage, shadow);
		int negatives = 0;
		rejected(() -> physical(source.resolve(".."))); negatives++;
		System.setProperty("phantom.task087.native", "true");
		try { rejected(PhantomTask094Lane::noNativeFlags); negatives++; }
		finally { System.clearProperty("phantom.task087.native"); }
		for (String[] invalid : new String[][] {{"owner", "OTHER"}, {"schema", "l2jmobiush5"}, {"baseline", "wrong"}, {"expiresUtc", "2000-01-01T00:00:00Z"}, {"installationId", "bad-uuid"}, {"sourceModule", shadow.toString()}})
		{
			final Properties copy = new Properties(); copy.putAll(authority); copy.setProperty(invalid[0], invalid[1]);
			rejected(() -> ownerFields(copy, source, stage, shadow)); negatives++;
		}
		rejected(() -> require(authority.getProperty("installationId").equals("wrong-caller"), "CALLER_UUID")); negatives++;
		for (String sql : new String[] {"DROP TABLE x", "TRUNCATE x", "DELETE FROM x", "USE other", "CREATE TABLE other.x(id INT)", "INSERT INTO `other`.`x` VALUES(1)", "INSERT IGNORE INTO other.x VALUES(1)", "REPLACE INTO other.x VALUES(1)", "CREATE INDEX idx ON other.x(id)"})
		{
			rejected(() -> safeSql(sql)); negatives++;
		}
		for (var script : PhantomTestSchemaManifest.inventory(shadow))
		{
			for (int index = 0; index < script.statements().size(); index++)
			{
				try { safeSql(script.statements().get(index)); }
				catch (IllegalArgumentException failure)
				{
					System.err.println("TASK094_SQL_PREFLIGHT script=" + script.relativePath() + " statement=" + (index + 1));
					throw failure;
				}
			}
		}
		guards();
		System.out.println("TASK094_STAGE_SQL_GUARDS_PASS negative=" + negatives + " canonicalSqlPreflight=true SQL_DDL_DML=0");
	}

	private static void guards()
	{
		targetUrl(TARGET_URL);
		for (String invalid : new String[] {TARGET_URL.replace(":3308/", ":3306/"), TARGET_URL.replace(SCHEMA, "l2jmobiush5"), TARGET_URL.replace(SCHEMA, "l2jmobiush5_phantom_test_091"), TARGET_URL.replace(SCHEMA, "l2jmobiush5_phantom_test_093"), TARGET_URL + "&allowMultiQueries=true", "dist/game/config/Database.ini"})
		{
			try
			{
				targetUrl(invalid);
			}
			catch (IllegalArgumentException expected)
			{
				continue;
			}
			throw new AssertionError("TASK094_JDBC_NEGATIVE_ACCEPTED");
		}
		System.out.println("TASK094_INGRESS_GUARDS_PASS positive=1 negative=6 SQL=0");
	}

	private static void databaseGuards(Path stage) throws Exception
	{
		final Properties config = settings(stage);
		try (var observer = connect(config, true))
		{
			observer.setReadOnly(true);
			absent(observer);
			quiescent(observer);
			// A second owned read-only session is foreign to the guarded connection.
			try (var other = connect(config, true))
			{
				other.setReadOnly(true);
				rejected(() -> quiescent(observer));
			}
			quiescent(observer);
		}
		System.out.println("TASK094_DATABASE_GUARDS_PASS actualSecondSessionRejected=true schemaAbsent=true DDL_DML=0");
	}

	private static void preflight(Path module, Path evidence) throws Exception
	{
		physical(module);
		physical(evidence);
		require(module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive") && (evidence.equals(module.getParent().resolve(".phantom-local/task094-recovery")) || evidence.equals(module.getParent().resolve(".phantom-local/task094-full-resume"))), "SOURCE_EVIDENCE_ROOT");
		final Path receipt = evidence.resolve("preflight.properties");
		require(!Files.exists(receipt) && !Files.exists(evidence.resolve("bootstrap.started")), "NO_AUTOMATIC_RETRY");
		final Properties settings = read(module.resolve("dist/game/config/Database.ini"));
		require("com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver")) && !settings.getProperty("Login", "").isBlank() && settings.containsKey("Password"), "PRIVATE_LOGIN_REQUIRED");
		Class.forName(settings.getProperty("Driver"));
		DriverManager.setLoginTimeout(10);
		try (var connection = DriverManager.getConnection(ADMIN_URL, settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			require(connection.getCatalog() == null || connection.getCatalog().isEmpty(), "ADMIN_CATALOG");
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='l2jmobiush5_phantom_test_094'"))
			{
				try (var rows = query.executeQuery())
				{
					require(rows.next() && rows.getLong(1) == 0, "EXISTING_SCHEMA_STOP");
				}
			}
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon'"))
			{
				try (var rows = query.executeQuery())
				{
					require(rows.next() && rows.getLong(1) == 0, "FOREIGN_CONNECTIONS_STOP");
				}
			}
		}
		Files.writeString(receipt, "task=TASK094\nphase=preflight\nschemaAbsent=true\nforeignConnections=0\nSQL_DDL_DML=0\nutc=" + Instant.now() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("TASK094_READONLY_PREFLIGHT_PASS schemaAbsent=true foreignConnections=0 DDL_DML=0");
	}

	static boolean nativeEnabled() { return Boolean.getBoolean("phantom.task094.native"); }
	static Path nativeRoot() { return Path.of(System.getProperty("phantom.task094.root", "")); }
	static Instant nativeAdmissionExpiry() throws Exception
	{
		final Path stage = physical(nativeRoot()), file = stage.resolve("private/owner.properties");
		privateAcl(stage, stage); privateAcl(stage, file);
		final var authority = read(file);
		require(authority.getProperty("installationId").equals(System.getProperty("phantom.task094.uuid")), "CALLER_UUID");
		final Instant expires = Instant.parse(authority.getProperty("expiresUtc"));
		require(Instant.now().isBefore(expires), "EXPIRED");
		return expires;
	}

	static void validateNative(Path module, Path config) throws Exception
	{
		require(nativeEnabled(), "NATIVE_FLAG");
		for (String key : System.getProperties().stringPropertyNames())
		{
			require(!(key.startsWith("phantom.task") && key.endsWith(".native") && !key.equals("phantom.task094.native") && Boolean.getBoolean(key)), "FOREIGN_NATIVE");
			require(!(key.startsWith("phantom.") && (key.endsWith(".persistent") || key.endsWith(".manifest") || key.endsWith(".control"))), "FOREIGN_AUTHORITY");
		}
		final Path stage = physical(nativeRoot()), shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		privateAcl(stage, stage); privateAcl(stage, stage.resolve("private/owner.properties"));
		final Properties authority = read(stage.resolve("private/owner.properties"));
		final Path source = physical(Path.of(authority.getProperty("sourceModule")));
		ownerFields(authority, source, stage, shadow);
		require(authority.getProperty("installationId").equals(System.getProperty("phantom.task094.uuid", "")), "CALLER_UUID");
		require(physical(module).equals(shadow) && physical(Path.of("").toAbsolutePath()).equals(shadow.resolve("dist/game")) && physical(config).equals(shadow.resolve("dist/game/config/Database.ini")), "NATIVE_CWD_CONFIG");
		require(hash(source.resolve("dist/game/config/Database.ini")).equals(authority.getProperty("sourceDatabaseSha256")) && hash(source.resolve("docs/phantoms/tasks/PHANTOM-AUTONOMOUS-PARTY-FARM-094/MANIFEST_SHA256.json")).equals(PACKAGE_SHA), "NATIVE_SOURCE_AUTHORITY");
		require(hash(stage.resolve("inputs.tsv")).equals(authority.getProperty("inputsSha256")), "INPUT_MANIFEST_DRIFT");
		settings(stage);
		final Path candidate = physical(Path.of(System.getProperty("phantom.task094.candidate", "")));
		require(candidate.getParent().equals(stage) && candidate.getFileName().toString().matches("candidate-v[1-9][0-9]*"), "CANDIDATE_PATH");
		final Properties seal = read(candidate.resolve("seal.properties"));
		require(BASELINE.equals(seal.getProperty("baseline")) && authority.getProperty("installationId").equals(seal.getProperty("installationId")) && authority.getProperty("inputsSha256").equals(seal.getProperty("inputsSha256")), "CANDIDATE_AUTHORITY");
		require(hash(candidate.resolve("overlay.tsv")).equals(seal.getProperty("overlaySha256")) && hash(stage.resolve("source-completion.tsv")).equals(seal.getProperty("sourceCompletionSha256")), "CANDIDATE_MANIFEST");
		final var allowed = java.util.Set.of(
			"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java",
			"java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRoleMatcher.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java",
			"java/org/l2jmobius/gameserver/phantoms/combat/PhantomSupportEffectAuthority.java",
			"java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java",
			"java/org/l2jmobius/gameserver/model/actor/Attackable.java",
			"java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java",
			"java/org/l2jmobius/gameserver/model/conditions/ConditionTargetRace.java",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundGoalSpec.java",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomOwnedStoreIntent.java",
			"java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java",
			"dist/game/data/phantoms/topology/high-five-career-recovery.xml",
			"java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java",
			"java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java",
			"java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationRequest.java",
			"java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java",
			"java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRegroupState.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyPersistencePort.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyStore.java",
			"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java",
			"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java",
			"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
		final var overlays = new java.util.HashMap<String, String[]>();
		for (String line : Files.readAllLines(candidate.resolve("overlay.tsv"), StandardCharsets.UTF_8))
		{
			final var fields = line.split("\t", -1);
			require(fields.length == 3 && allowed.contains(fields[0]) && (fields[1].equals("-") || fields[1].matches("[0-9a-f]{64}")) && fields[2].matches("[0-9a-f]{64}") && overlays.putIfAbsent(fields[0], fields) == null, "CANDIDATE_OVERLAY_ROW");
			require(hash(source.resolve(fields[0])).equals(fields[2]) && hash(shadow.resolve(fields[0])).equals(fields[2]), "CANDIDATE_OVERLAY_DRIFT");
		}
		require(!overlays.isEmpty(), "CANDIDATE_EMPTY");
		final Path geo = physical(Path.of(authority.getProperty("geoSource")));
		require(geo.equals(source.getParent().resolve(".phantom-local/task074/runtime-test/game/data/geodata")) && hash(source.getParent().resolve(".phantom-local/task074/GEODATA074.tsv")).equals(authority.getProperty("geoPinSha256")), "GEO_SOURCE_PIN");
		int count = 0, regions = 0;
		final var originalPaths = new java.util.HashSet<String>();
		for (String line : Files.readAllLines(stage.resolve("inputs.tsv"), StandardCharsets.UTF_8))
		{
			final var fields = line.split("\t", -1);
			require(fields.length == 3 && fields[2].matches("[0-9a-f]{64}"), "INPUT_ROW");
			final Path origin = physical(Path.of(fields[0])), copy = physical(Path.of(fields[1]));
			require((origin.startsWith(source) || origin.startsWith(geo)) && copy.startsWith(shadow) && !copy.equals(config), "INPUT_ESCAPE");
			final String relative = shadow.relativize(copy).toString().replace('\\', '/');
			originalPaths.add(relative);
			final var overlay = overlays.get(relative);
			if (overlay != null) { require(overlay[1].equals(fields[2]), "CANDIDATE_PREIMAGE"); }
			else
			{
				require(hash(origin).equals(fields[2]), "SOURCE_INPUT_DRIFT");
				if (relative.equals("dist/game/config/IdManager.ini")) { require(Files.readString(copy).equals(Files.readString(origin).replace("DatabaseCleanUp = True", "DatabaseCleanUp = False")), "CLEANUP_OVERLAY_DRIFT"); }
				else { require(hash(copy).equals(fields[2]), "SHADOW_INPUT_DRIFT"); }
			}
			if (origin.startsWith(geo)) { require(copy.equals(shadow.resolve("dist/game/data/geodata").resolve(origin.getFileName())), "GEO_REGION"); regions++; }
			count++;
		}
		for (var overlay : overlays.values()) { require(originalPaths.contains(overlay[0]) || java.util.Set.of("java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java", "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRegroupState.java").contains(overlay[0]) && overlay[1].equals("-"), "CANDIDATE_ADDITION"); }
		for (String line : Files.readAllLines(stage.resolve("source-completion.tsv"), StandardCharsets.UTF_8))
		{
			final var fields = line.split("\t", -1);
			require(fields.length == 2 && fields[0].matches("java/org/l2jmobius/(gameserver/cache|log)/.+\\.java") && fields[1].matches("[0-9a-f]{64}") && hash(source.resolve(fields[0])).equals(fields[1]) && hash(shadow.resolve(fields[0])).equals(fields[1]), "SOURCE_COMPLETION_DRIFT");
		}
		require(count > 1000 && regions == 203, "NATIVE_INPUTS_GEO");
		final var schema = PhantomTestSchemaManifest.current(shadow);
		require(schema.aggregateSha256().equals(seal.getProperty("sqlAggregate")) && schema.aggregateSha256().equals(read(stage.resolve("pin.complete")).getProperty("sqlAggregate")), "NATIVE_SQL_PIN");
		require(hash(shadow.resolve("dist/libs/GameServer.jar")).equals(seal.getProperty("gameJarSha256")) && hash(candidate.resolve("Test094.jar")).equals(seal.getProperty("testJarSha256")) && hash(candidate.resolve("test-sources.tsv")).equals(seal.getProperty("testSourcesSha256")), "NATIVE_JAR_SOURCE_PIN");
		require(hash(shadow.resolve("dist/libs/LoginServer.jar")).equals(seal.getProperty("loginJarSha256")), "NATIVE_LOGIN_JAR_PIN");
		try (var libraries = Files.list(shadow.resolve("dist/libs")))
		{
			for (Path library : libraries.filter(file -> file.getFileName().toString().endsWith(".jar")).toList())
			{
				final String name = library.getFileName().toString();
				require(name.equals("GameServer.jar") || name.equals("LoginServer.jar") || originalPaths.contains("dist/libs/" + name), "NATIVE_EXTRA_LIBRARY");
			}
		}
		for (String line : Files.readAllLines(candidate.resolve("test-sources.tsv"), StandardCharsets.UTF_8))
		{
			final var fields = line.split("\t", -1);
			require(fields.length == 2 && fields[0].startsWith("test/java/") && !fields[0].contains("..") && fields[0].endsWith(".java") && fields[1].matches("[0-9a-f]{64}") && hash(source.resolve(fields[0])).equals(fields[1]), "NATIVE_TEST_SOURCE_DRIFT");
		}
		require(physical(Path.of(org.l2jmobius.gameserver.model.actor.Player.class.getProtectionDomain().getCodeSource().getLocation().toURI())).equals(shadow.resolve("dist/libs/GameServer.jar")), "NATIVE_PRODUCTION_CODE_SOURCE");
		require(physical(Path.of(PhantomTask094Lane.class.getProtectionDomain().getCodeSource().getLocation().toURI())).equals(candidate.resolve("Test094.jar")), "NATIVE_TEST_CODE_SOURCE");
		require(read(stage.resolve("bootstrap.complete")).getProperty("installationId").equals(authority.getProperty("installationId")), "NATIVE_BOOTSTRAP_UUID");
		System.out.println("TASK094_NATIVE_FILES_PASS sourceOverlay=" + overlays.size() + " Geo=203 scripts=" + schema.scriptCount() + " sourceConfigUntouched=true");
	}

	static void initializeNative(Path module, Path config) throws Exception
	{
		validateNative(module, config);
		final Path stage = nativeRoot();
		final var authority = read(stage.resolve("private/owner.properties"));
		try (var connection = connect(settings(stage), false))
		{
			connection.setReadOnly(true); quiescent(connection);
			owned(connection, authority, PhantomTestSchemaManifest.current(module));
			require(Files.readString(stage.resolve("foreign-metadata.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
		}
		System.setProperty("phantom.economy.schema", SCHEMA);
		System.setProperty("phantom.economy.installation", authority.getProperty("installationId"));
		org.l2jmobius.commons.database.DatabaseFactory.initFromConfig(config.toString());
	}

	/** Exact original PIN topology, preserved before the additive Hunters recovery update. */
	static Path previousTopology(Path module) throws Exception
	{
		final Path stage = nativeRoot(), directory = physical(stage.resolve("topology-before-hunters-recovery"));
		require(directory.startsWith(stage) && directory.getParent().equals(stage), "PREVIOUS_TOPOLOGY_OWNER");
		final Path originalDirectory = module.resolve("dist/game/data/phantoms/topology").toAbsolutePath().normalize();
		final var expected = new java.util.TreeMap<String, String>();
		for (String line : Files.readAllLines(stage.resolve("inputs.tsv"), StandardCharsets.UTF_8))
		{
			final var fields = line.split("\t", -1);
			final Path original = Path.of(fields[1]).toAbsolutePath().normalize();
			if (originalDirectory.equals(original.getParent()) && original.getFileName().toString().endsWith(".xml")) { require(expected.putIfAbsent(original.getFileName().toString(), fields[2]) == null, "PREVIOUS_TOPOLOGY_DUPLICATE"); }
		}
		try (var files = Files.list(directory)) { require(!expected.isEmpty() && files.filter(path -> path.getFileName().toString().endsWith(".xml")).count() == expected.size(), "PREVIOUS_TOPOLOGY_FILE_SET"); }
		for (var entry : expected.entrySet()) { require(hash(physical(directory.resolve(entry.getKey()))).equals(entry.getValue()), "PREVIOUS_TOPOLOGY_INPUT_SHA"); }
		System.out.println("TASK094_PREVIOUS_TOPOLOGY_PASS originalPinFiles=" + expected.size() + " exactSha=true sourceWrites=0");
		return directory;
	}

	static void postflightNative(Path module, Path config) throws Exception
	{
		validateNative(module, config);
		final Path stage = nativeRoot();
		try (var connection = connect(settings(stage), false))
		{
			connection.setReadOnly(true); quiescent(connection);
			owned(connection, read(stage.resolve("private/owner.properties")), PhantomTestSchemaManifest.current(module));
			require(Files.readString(stage.resolve("foreign-metadata.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
			try (var query = connection.prepareStatement("SELECT charId,classid,level,curHp,maxHp,curMp,maxMp,curCp,maxCp,exp,expBeforeDeath,sp,x,y,z FROM characters WHERE account_name='task094farm' ORDER BY charId"); var rows = query.executeQuery())
			{
				while (rows.next())
				{
					final var facts = new StringBuilder("TASK094_DURABLE_ACTOR");
					for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) { facts.append(' ').append(rows.getMetaData().getColumnLabel(index)).append('=').append(rows.getString(index)); }
					System.out.println(facts);
				}
			}
			try (var query = connection.prepareStatement("SELECT c.profile_id,c.component_type,c.row_version,c.payload FROM phantom_profile_components c INNER JOIN phantom_profiles p ON p.profile_id=c.profile_id INNER JOIN characters ch ON ch.charId=p.character_object_id WHERE ch.account_name='task094farm' AND c.component_type IN ('background.state','background.native-context','background.owned-store','party.state') ORDER BY c.profile_id,c.component_type"); var rows = query.executeQuery())
			{
				while (rows.next())
				{
					final String type = rows.getString(2);
					String facts = "pendingIntent=true";
					if (type.equals("background.state"))
					{
						final var state = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(rows.getBytes(4));
						facts = "state=" + state.state() + " hashes=" + state.hashes();
					}
					else if (type.equals("background.native-context")) { facts = "phase=" + org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.decode(rows.getBytes(4)).phase(); }
					else if (type.equals("party.state"))
					{
						final byte[] payload = rows.getBytes(4);
						final var state = new org.l2jmobius.gameserver.phantoms.party.PhantomPartyStateCodec().decode(payload);
						final String reason = state.lastFailureKey();
						facts = "status=" + state.status() + " lastFailureKey=" + (reason.isEmpty() || reason.matches("[a-zA-Z0-9_.:-]{1,256}") ? reason : "WITHHELD")
							+ " groupId=" + state.groupId() + " groupGeneration=" + state.groupGeneration() + " membershipRevision=" + state.membershipRevision()
							+ " leader=" + state.leader().stableKey() + " leaderManifestHash=" + state.leaderManifestHash() + " computedManifestHash=" + state.canonicalManifestHash()
							+ " roster=" + state.phantomMembers().stream().map(member -> Long.toString(member.profileId())).collect(java.util.stream.Collectors.joining(",")) + " realMembers=" + state.realMembers().size()
							+ " operation=" + (state.operation() == null ? "none" : state.operation().kind() + ":" + state.operation().phase() + ":" + state.operation().manifestHash())
							+ " operationId=" + (state.operation() == null ? "none" : state.operation().operationId()) + " invitationSequence=" + (state.operation() == null ? 0 : state.operation().invitationSequence());
						final Path backup = Path.of(System.getProperty("phantom.task094.candidate")).resolve("party-claim-profile" + rows.getLong(1) + "-v" + rows.getLong(3) + ".payload");
						Files.write(backup, payload, StandardOpenOption.CREATE_NEW);
						require(java.util.Arrays.equals(payload, Files.readAllBytes(backup)), "PARTY_AUDIT_PREIMAGE_BYTES");
					}
					System.out.println("TASK094_DURABLE_BASELINE profile=" + rows.getLong(1) + " component=" + type + " version=" + rows.getLong(3) + " " + facts);
				}
			}
		}
		System.out.println("TASK094_NATIVE_POSTFLIGHT_PASS owner=true priorOwnConnections=0 foreignConnections=0 foreignMetadataUnchanged=true DDL_DML=0");
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && args[0].equals("guards"))
		{
			guards();
			return;
		}
		require(args.length == 3, "ACTION_ARGUMENTS");
		try
		{
			noNativeFlags();
			final Path source = physical(Path.of(args[1])), stage = physical(Path.of(args[2]));
			switch (args[0])
			{
				case "preflight" -> preflight(source, stage);
				case "pin" -> { phase = "PIN"; pin(source, stage); }
				case "verify-pin" -> { phase = "VERIFY"; verifyPin(source, stage); }
				case "verify" -> { phase = "VERIFY"; verify(source, stage); }
				case "guards-stage" -> stageGuards(source, stage);
				case "guards-db" -> databaseGuards(stage);
				case "bootstrap" -> bootstrap(source, stage);
				default -> throw new IllegalArgumentException("TASK094_UNKNOWN_ACTION");
			}
		}
		catch (SQLException failure)
		{
			// JDBC exception text can contain connection input. Emit only non-secret diagnostics.
			System.err.println("TASK094_" + phase + "_STOP SQLState=" + failure.getSQLState() + " vendorCode=" + failure.getErrorCode() + " exception=" + failure.getClass().getSimpleName());
			System.exit(2);
		}
		catch (Exception failure)
		{
			// Filesystem failures contain paths, not JDBC credentials; retain the causal stack.
			if (failure instanceof java.nio.file.FileSystemException)
			{
				failure.printStackTrace(System.err);
			}
			// Paths and private configuration must not leak through arbitrary exception messages.
			final String reason = failure instanceof IllegalArgumentException && failure.getMessage() != null && failure.getMessage().matches("TASK094_[A-Z_]+") ? failure.getMessage() : failure.getClass().getSimpleName();
			System.err.println("TASK094_" + phase + "_STOP reason=" + reason);
			System.exit(2);
		}
	}
}
