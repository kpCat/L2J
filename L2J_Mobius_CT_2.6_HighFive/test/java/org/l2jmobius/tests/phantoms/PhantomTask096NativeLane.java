/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.time.Instant;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Separate test-only 096N authority; never delegates to a historical test lane. */
public final class PhantomTask096NativeLane
{
	static final String SCHEMA = "l2jmobiush5_phantom_test_096n";
	private static final String ADMIN = "jdbc:mysql://127.0.0.1:3308/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
	private static final String TARGET = "jdbc:mysql://127.0.0.1:3308/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
	private PhantomTask096NativeLane() { }

	static void require(boolean condition, String reason)
	{
		if (!condition) { throw new IllegalArgumentException("TASK096N_" + reason); }
	}

	private static void ownerFields(Properties authority, String caller)
	{
		require("TASK096_NATIVE_ACCEPTANCE".equals(authority.getProperty("owner")) && SCHEMA.equals(authority.getProperty("schema")), "OWNER_SCHEMA");
		final String uuid = authority.getProperty("installationId", "");
		require(UUID.fromString(uuid).toString().equals(uuid) && uuid.equals(caller), "CALLER_UUID");
		require(Instant.now().isBefore(Instant.parse(authority.getProperty("expiresUtc", "2000-01-01T00:00:00Z"))), "EXPIRED");
	}

	static boolean enabled() { return Boolean.getBoolean("phantom.task096n.native"); }
	static Path root() { return Path.of(System.getProperty("phantom.task096n.root", "")); }

	private static Path physical(Path path) throws Exception
	{
		final Path absolute = path.toAbsolutePath().normalize();
		require(absolute.equals(path.toAbsolutePath()) && absolute.equals(path.toRealPath()), "PHYSICAL_PATH");
		for (Path part = absolute; part != null; part = part.getParent())
		{
			require(!Files.isSymbolicLink(part) && !Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther(), "REPARSE_PATH");
		}
		return absolute;
	}

	private static Properties read(Path file) throws Exception
	{
		physical(file);
		require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 16384, "BOUNDED_FILE");
		final var result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}

	private static String hash(Path file) throws Exception
	{
		physical(file);
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(file))
		{
			final byte[] buffer = new byte[65536];
			for (int count; (count = input.read(buffer)) >= 0;) { digest.update(buffer, 0, count); }
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void privateAcl(Path stage, Path file) throws Exception
	{
		final var lookup = file.getFileSystem().getUserPrincipalLookupService();
		final var owner = lookup.lookupPrincipalByName(System.getProperty("user.name"));
		final var system = lookup.lookupPrincipalByName("SYSTEM");
		require(Files.getOwner(physical(stage)).equals(owner) && Files.getOwner(physical(file)).equals(owner), "ACL_OWNER");
		final var view = Files.getFileAttributeView(file, AclFileAttributeView.class);
		require(view != null, "ACL_UNAVAILABLE");
		for (var entry : view.getAcl()) { require(entry.type() != AclEntryType.ALLOW || entry.principal().equals(owner) || entry.principal().equals(system), "PRIVATE_ACL"); }
	}

	private static void lanes()
	{
		for (String key : System.getProperties().stringPropertyNames())
		{
			if (key.startsWith("phantom.") && !key.startsWith("phantom.task096n.") && (key.endsWith(".native") || key.endsWith(".persistent") || key.endsWith(".control") || key.endsWith(".manifest")))
			{
				require(!key.endsWith(".manifest") && !Boolean.getBoolean(key), "FOREIGN_LANE");
			}
		}
	}

	private static Properties authority() throws Exception
	{
		require(enabled(), "NATIVE_FLAG");
		lanes();
		final Path stage = physical(root());
		privateAcl(stage, stage); privateAcl(stage, stage.resolve("private/owner.properties"));
		require(hash(stage.resolve("private/owner.properties")).equals(System.getProperty("phantom.task096n.ownerSha256", "")), "OWNER_SEAL");
		final var owner = read(stage.resolve("private/owner.properties"));
		ownerFields(owner, System.getProperty("phantom.task096n.uuid", ""));
		final Path source = physical(Path.of(owner.getProperty("sourceModule", "")));
		require(source.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive") && !stage.startsWith(source.getParent()) && physical(Path.of(owner.getProperty("stage", ""))).equals(stage), "EXTERNAL_SOURCE_STAGE");
		require("feature/phantom-world".equals(owner.getProperty("branch")) && owner.getProperty("sourceHead", "").matches("[0-9a-f]{40}") && "private,build,receipts".equals(owner.getProperty("outputAllowlist")), "SOURCE_OUTPUT_AUTHORITY");
		require(hash(source.resolve("dist/game/config/Database.ini")).equals(owner.getProperty("sourceDatabaseSha256")) && hash(stage.resolve("inputs.tsv")).equals(owner.getProperty("inputsSha256")), "SOURCE_INPUT_DRIFT");
		require(owner.getProperty("installationId").equals(read(stage.resolve("prepare.begun")).getProperty("installationId")) && owner.getProperty("installationId").equals(read(stage.resolve("prepare.completed")).getProperty("installationId")), "PREPARE_RECEIPTS");
		return owner;
	}

	static void validate(Path module, Path config) throws Exception
	{
		require(enabled(), "NATIVE_FLAG");
		final var owner = authority();
		final Path shadow = physical(root().resolve("L2J_Mobius_CT_2.6_HighFive"));
		require(physical(module).equals(shadow) && physical(Path.of("")).equals(shadow.resolve("dist/game")) && physical(config).equals(shadow.resolve("dist/game/config/Database.ini")), "NATIVE_CWD_CONFIG");
		privateAcl(root(), config);
		inputs(owner, true);
		final var seal = read(root().resolve("pin.completed"));
		require(owner.getProperty("installationId").equals(seal.getProperty("installationId")) && hash(config).equals(seal.getProperty("configSha256")) && hash(shadow.resolve("dist/game/config/IdManager.ini")).equals(seal.getProperty("idSha256")) && PhantomTestSchemaManifest.current(shadow).aggregateSha256().equals(seal.getProperty("sqlAggregate")), "NATIVE_PIN_DRIFT");
		compiled(true);
		final var settings = read(config);
		require(TARGET.equals(settings.getProperty("URL")) && "com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver")) && "4".equals(settings.getProperty("MaximumDatabaseConnections")) && "False".equals(settings.getProperty("BackupDatabase")) && "False".equals(settings.getProperty("TestDatabaseConnections")), "EXACT_JDBC_SETTINGS");
		require("False".equals(read(shadow.resolve("dist/game/config/IdManager.ini")).getProperty("DatabaseCleanUp")), "CLEANUP_DISABLED");
		require(owner.getProperty("installationId").equals(read(root().resolve("bootstrap.completed")).getProperty("installationId")), "OWN_BOOTSTRAP_REQUIRED");
	}

	private static void inputs(Properties owner, boolean overlay) throws Exception
	{
		final Path source = physical(Path.of(owner.getProperty("sourceModule"))), shadow = physical(root().resolve("L2J_Mobius_CT_2.6_HighFive"));
		int count = 0, geo = 0;
		for (String line : Files.readAllLines(root().resolve("inputs.tsv"), StandardCharsets.UTF_8))
		{
			final String[] fields = line.split("\t", -1);
			require(fields.length == 2 && fields[1].matches("[0-9a-f]{64}") && !fields[0].contains("..") && !fields[0].contains(":") && !fields[0].startsWith("/") && !fields[0].contains("\\"), "INPUT_ROW");
			final Path copy = physical(shadow.resolve(fields[0]));
			require(copy.startsWith(shadow) && !fields[0].equals("dist/game/config/Database.ini"), "INPUT_ESCAPE");
			final boolean isGeo = fields[0].matches("dist/game/data/geodata/[0-9]{2}_[0-9]{2}\\.l2j");
			final Path original = isGeo ? source.getParent().resolve(".phantom-local/task074/runtime-test/game/data/geodata").resolve(copy.getFileName()) : source.resolve(fields[0]);
			require(hash(original).equals(fields[1]), "SOURCE_INPUT_DRIFT");
			if (overlay && fields[0].equals("dist/game/config/IdManager.ini"))
			{
				require(Files.readString(copy).equals(Files.readString(original).replace("DatabaseCleanUp = True", "DatabaseCleanUp = False")), "ID_OVERLAY_DRIFT");
			}
			else { require(hash(copy).equals(fields[1]), "SHADOW_INPUT_DRIFT"); }
			if (isGeo) { geo++; }
			count++;
		}
		require(count > 1000 && geo == 203 && hash(source.getParent().resolve(".phantom-local/task074/GEODATA074.tsv")).equals(owner.getProperty("geoPinSha256")), "INPUT_GEO_COUNT");
	}

	private static Connection connect(boolean admin) throws Exception
	{
		final var settings = read(root().resolve("L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini"));
		require(TARGET.equals(settings.getProperty("URL")) && "com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver")), "JDBC_EXACT");
		Class.forName(settings.getProperty("Driver"));
		DriverManager.setLoginTimeout(10);
		return DriverManager.getConnection(admin ? ADMIN : TARGET, settings.getProperty("Login"), settings.getProperty("Password"));
	}

	private static void databaseAuthority(Connection connection, boolean absent) throws Exception
	{
		boolean globalAll = false;
		try (var query = connection.createStatement(); var rows = query.executeQuery("SHOW GRANTS FOR CURRENT_USER"))
		{
			while (rows.next()) { globalAll |= rows.getString(1).matches("(?is)^GRANT ALL PRIVILEGES ON \\*\\.\\* TO .+$"); }
		}
		require(globalAll, "BLOCKED_DB_AUTHORITY");
		try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon'"))
		{
			require(rows.next() && rows.getLong(1) == 0, "FOREIGN_SESSIONS");
		}
		if (absent)
		{
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
			{
				query.setString(1, SCHEMA);
				try (var rows = query.executeQuery()) { require(rows.next() && rows.getLong(1) == 0, "DB_EXISTS"); }
			}
		}
	}

	private static String foreignMetadata(Connection connection) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		for (String sql : new String[] {"SELECT SCHEMA_NAME,DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME<>'" + SCHEMA + "' ORDER BY SCHEMA_NAME", "SELECT TABLE_SCHEMA,TABLE_NAME,TABLE_TYPE,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA NOT IN ('information_schema','performance_schema','" + SCHEMA + "') ORDER BY TABLE_SCHEMA,TABLE_NAME"})
		{
			try (var query = connection.createStatement(); var rows = query.executeQuery(sql))
			{
				while (rows.next())
				{
					for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { digest.update((String.valueOf(rows.getString(column)) + "\t").getBytes(StandardCharsets.UTF_8)); }
					digest.update((byte) '\n');
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void compiled(boolean verify) throws Exception
	{
		final Path stage = physical(root());
		final var rows = new java.util.ArrayList<String>();
		for (String directory : List.of("build/bin", "build/phantom-test/bin", "build/phantom-test/resources"))
		{
			final Path base = physical(stage.resolve(directory));
			try (var files = Files.walk(base))
			{
				for (Path file : files.filter(Files::isRegularFile).sorted().toList()) { rows.add(stage.relativize(file).toString().replace('\\', '/') + "\t" + hash(file)); }
			}
		}
		require(rows.size() > 2500, "COMPILED_COUNT");
		final String content = String.join("\n", rows) + "\n";
		final Path manifest = stage.resolve("compiled.tsv");
		if (verify)
		{
			require(content.equals(Files.readString(manifest, StandardCharsets.UTF_8)), "COMPILED_DRIFT");
			final var seal = read(stage.resolve("build.completed"));
			require(hash(manifest).equals(seal.getProperty("compiledSha256")) && authority().getProperty("installationId").equals(seal.getProperty("installationId")), "BUILD_SEAL");
		}
		else
		{
			writeNew(manifest, content);
			writeNew(stage.resolve("build.completed"), "installationId=" + authority().getProperty("installationId") + "\ncompiledSha256=" + hash(manifest) + "\n");
		}
		final Path tests = physical(Path.of(PhantomTask096NativeLane.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		final Path production = physical(Path.of(org.l2jmobius.gameserver.phantoms.PhantomSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		require(tests.equals(stage.resolve("build/phantom-test/bin")) && production.equals(stage.resolve("build/bin")), "EXECUTABLE_CODE_SOURCE");
		final Path shadow = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
		final var expected = new java.util.ArrayList<Path>();
		expected.add(tests); expected.add(stage.resolve("build/phantom-test/resources")); expected.add(production);
		try (var jars = Files.list(shadow.resolve("dist/libs"))) { expected.addAll(jars.filter(path -> path.toString().endsWith(".jar")).toList()); }
		final var actual = new java.util.ArrayList<Path>();
		for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) { actual.add(physical(Path.of(entry))); }
		require(actual.size() == expected.size() && new java.util.HashSet<>(actual).equals(new java.util.HashSet<>(expected)) && actual.get(0).equals(tests) && actual.get(1).equals(expected.get(1)) && actual.get(2).equals(production), "EXACT_CLASSPATH");
	}

	private static void writeNew(Path path, String value) throws Exception
	{
		Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
	}

	private static void pin() throws Exception
	{
		final var owner = authority();
		inputs(owner, false);
		compiled(true);
		final Path shadow = root().resolve("L2J_Mobius_CT_2.6_HighFive"), config = shadow.resolve("dist/game/config/Database.ini"), id = shadow.resolve("dist/game/config/IdManager.ini");
		require(!Files.exists(config) && !Files.exists(root().resolve("pin.begun")) && !Files.exists(root().resolve("bootstrap.begun")), "PIN_NO_RETRY");
		final var source = read(Path.of(owner.getProperty("sourceModule")).resolve("dist/game/config/Database.ini"));
		final String sourceUrl = source.getProperty("URL", "");
		final var uri = java.net.URI.create(sourceUrl.replaceFirst("^jdbc:", ""));
		require(sourceUrl.startsWith("jdbc:mysql://") && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == 3308 && "/l2jmobiush5".equals(uri.getPath()) && uri.getUserInfo() == null && uri.getFragment() == null && "com.mysql.cj.jdbc.Driver".equals(source.getProperty("Driver")), "SOURCE_JDBC_ORIGIN");
		require(!source.getProperty("Login", "").isBlank() && source.containsKey("Password"), "SOURCE_CREDENTIALS");
		final String preimage = Files.readString(id);
		require(preimage.contains("DatabaseCleanUp = True") && preimage.indexOf("DatabaseCleanUp = True") == preimage.lastIndexOf("DatabaseCleanUp = True"), "ID_PREIMAGE");
		writeNew(root().resolve("pin.begun"), "installationId=" + owner.getProperty("installationId"));
		final var settings = new Properties();
		for (String key : List.of("Driver", "Login", "Password")) { settings.setProperty(key, source.getProperty(key)); }
		settings.setProperty("URL", TARGET); settings.setProperty("MaximumDatabaseConnections", "4"); settings.setProperty("BackupDatabase", "False"); settings.setProperty("TestDatabaseConnections", "False");
		try (var writer = Files.newBufferedWriter(config, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { settings.store(writer, "Private own096N config"); }
		privateAcl(root(), config);
		Files.writeString(id, preimage.replace("DatabaseCleanUp = True", "DatabaseCleanUp = False"), StandardCharsets.UTF_8);
		writeNew(root().resolve("pin.completed"), "installationId=" + owner.getProperty("installationId") + "\nconfigSha256=" + hash(config) + "\nidSha256=" + hash(id) + "\nsqlAggregate=" + PhantomTestSchemaManifest.current(shadow).aggregateSha256() + "\n");
		System.out.println("TASK096N_PIN_PASS SQL_DDL_DML=0 World=NOT_STARTED");
	}

	private static final java.util.regex.Pattern ABSENT_DROP = java.util.regex.Pattern.compile("(?is)\\s*DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`?([A-Za-z0-9_]+)`?\\s*");
	private static void safeSql(String sql)
	{
		if (ABSENT_DROP.matcher(sql).matches()) { return; }
		require(sql.stripLeading().matches("(?is)(CREATE\\s+TABLE|CREATE\\s+(?:UNIQUE\\s+)?INDEX|INSERT\\s+(?:IGNORE\\s+)?INTO|REPLACE\\s+INTO)\\b.*"), "UNSAFE_SQL_VERB");
		final var identifiers = new StringBuilder();
		char quote = 0;
		for (int index = 0; index < sql.length(); index++)
		{
			final char value = sql.charAt(index);
			if (quote != 0)
			{
				if (value == '\\') { index++; }
				else if (value == quote) { if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) { index++; } else { quote = 0; } }
				identifiers.append(' ');
			}
			else if (value == '\'' || value == '"') { quote = value; identifiers.append(' '); }
			else { identifiers.append(value); }
		}
		require(!java.util.regex.Pattern.compile("[`A-Za-z_][`A-Za-z0-9_]*\\s*\\.\\s*[`A-Za-z_]").matcher(identifiers).find(), "EXTERNAL_SQL_REFERENCE");
	}

	/** On the pinned fresh schema, REPLACE may only insert globally unique canonical seed keys. */
	private static void auditReplaces(List<StrictSqlScriptRunner.ScriptInfo> scripts)
	{
		final var guardIds = new java.util.HashSet<String>();
		final var variableKeys = new java.util.HashSet<String>();
		int replaceStatements = 0;
		for (var script : scripts)
		{
			for (String sql : script.statements())
			{
				if (sql.stripLeading().matches("(?is)INSERT\\s+.*\\b(castle_siege_guards|global_variables)\\b.*")) { throw new IllegalArgumentException("TASK096N_REPLACE_PREEXISTING_SEED"); }
				if (!sql.stripLeading().matches("(?is)REPLACE\\s+.*")) { continue; }
				replaceStatements++;
				final String compact = sql.replaceAll("\\s+", "");
				final String prefix = "REPLACEINTO`castle_siege_guards`(`castleId`,`id`,`npcId`,`x`,`y`,`z`,`heading`,`respawnDelay`,`isHired`)VALUES";
				if (compact.startsWith(prefix) && script.relativePath().equals("dist/db_installer/sql/game/castle_siege_guards.sql"))
				{
					final String values = compact.substring(prefix.length());
					final var tuples = java.util.regex.Pattern.compile("\\((-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+)\\)").matcher(values);
					int end = 0, count = 0;
					while (tuples.find())
					{
						require(values.substring(end, tuples.start()).equals(count == 0 ? "" : ","), "REPLACE_TUPLE_SYNTAX");
						require(guardIds.add(Long.toString(Long.parseLong(tuples.group(2)))), "REPLACE_DUPLICATE_KEY");
						end = tuples.end(); count++;
					}
					require(count > 0 && end == values.length(), "REPLACE_TUPLE_TAIL");
				}
				else if (script.relativePath().equals("dist/db_installer/sql/game/global_variables.sql"))
				{
					require(compact.equals("REPLACEINTO`global_variables`VALUES('HBLevel','11')") || compact.equals("REPLACEINTO`global_variables`VALUES('HBTrust','4000000')"), "REPLACE_VARIABLE_SEED");
					require(variableKeys.add(compact), "REPLACE_DUPLICATE_KEY");
				}
				else { throw new IllegalArgumentException("TASK096N_REPLACE_UNAUDITED_TABLE"); }
			}
		}
		require(replaceStatements == 11 && variableKeys.size() == 2 && !guardIds.isEmpty(), "REPLACE_INVENTORY_DRIFT");
		System.out.println("TASK096N_SQL_SEEDS_AUDITED replaceStatements=" + replaceStatements + " uniqueGuardPrimaryKeys=" + guardIds.size() + " uniqueVariableKeys=2 freshSchemaRequired=true");
	}

	private static void bootstrap() throws Exception
	{
		final var owner = authority();
		inputs(owner, true);
		compiled(true);
		final Path shadow = root().resolve("L2J_Mobius_CT_2.6_HighFive");
		final var pin = read(root().resolve("pin.completed"));
		require(owner.getProperty("installationId").equals(pin.getProperty("installationId")) && hash(shadow.resolve("dist/game/config/Database.ini")).equals(pin.getProperty("configSha256")) && hash(shadow.resolve("dist/game/config/IdManager.ini")).equals(pin.getProperty("idSha256")), "PIN_DRIFT");
		final var manifest = PhantomTestSchemaManifest.current(shadow);
		require(manifest.aggregateSha256().equals(pin.getProperty("sqlAggregate")), "SQL_PIN");
		final var scripts = PhantomTestSchemaManifest.inventory(shadow);
		for (var script : scripts) { for (String sql : script.statements()) { safeSql(sql); } }
		auditReplaces(scripts);
		require(!Files.exists(root().resolve("bootstrap.begun")) && !Files.exists(root().resolve("bootstrap.completed")), "NO_BOOTSTRAP_RETRY");
		try (var connection = connect(true))
		{
			databaseAuthority(connection, true);
			writeNew(root().resolve("foreign.before.sha256"), foreignMetadata(connection));
			writeNew(root().resolve("bootstrap.begun"), "installationId=" + owner.getProperty("installationId"));
			try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE `l2jmobiush5_phantom_test_096n` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
		}
		int omitted = 0;
		try (var connection = connect(false))
		{
			require(SCHEMA.equals(connection.getCatalog()), "CATALOG");
			databaseAuthority(connection, false);
			for (var script : scripts)
			{
				for (String sql : script.statements())
				{
					final var drop = ABSENT_DROP.matcher(sql);
					if (drop.matches())
					{
						try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?"))
						{
							query.setString(1, SCHEMA); query.setString(2, drop.group(1));
							try (var rows = query.executeQuery()) { require(rows.next() && rows.getLong(1) == 0, "DROP_EXISTING_TABLE_REFUSED"); }
						}
						omitted++; continue;
					}
					safeSql(sql);
					try (var statement = connection.createStatement()) { statement.execute(sql); }
				}
			}
			try (var insert = connection.prepareStatement("INSERT INTO economy071_authority(schema_name,task_id,installation_id,schema_version,migration_sha256) VALUES(?,96,?,1,?)"))
			{
				insert.setString(1, SCHEMA); insert.setString(2, owner.getProperty("installationId")); insert.setString(3, hash(shadow.resolve("dist/db_installer/sql/game/economy071.sql"))); insert.executeUpdate();
			}
			PhantomTestSchemaManifest.writeDatabaseMetadata(connection, manifest);
			require(Files.readString(root().resolve("foreign.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
		}
		writeNew(root().resolve("bootstrap.completed"), "installationId=" + owner.getProperty("installationId") + "\nsqlAggregate=" + manifest.aggregateSha256() + "\n");
		System.out.println("TASK096N_BOOTSTRAP_PASS scripts=" + scripts.size() + " absentDropNoops=" + omitted + " DROP_DELETE_TRUNCATE=0 foreignMetadataUnchanged=true");
	}

	private static void owned(Connection connection, Properties owner) throws Exception
	{
		require(SCHEMA.equals(connection.getCatalog()), "OWN_CATALOG");
		try (var query = connection.prepareStatement("SELECT task_id,installation_id,schema_version,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
		{
			query.setString(1, SCHEMA);
			try (var rows = query.executeQuery())
			{
				require(rows.next() && rows.getInt(1) == 96 && owner.getProperty("installationId").equals(rows.getString(2)) && rows.getInt(3) == 1 && hash(root().resolve("L2J_Mobius_CT_2.6_HighFive/dist/db_installer/sql/game/economy071.sql")).equals(rows.getString(4)) && !rows.next(), "DB_OWNER_UUID");
			}
		}
		PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, PhantomTestSchemaManifest.current(root().resolve("L2J_Mobius_CT_2.6_HighFive")));
	}

	static void initialize(Path module, Path config) throws Exception
	{
		validate(module, config);
		final var owner = authority();
		inputs(owner, true);
		try (var connection = connect(false))
		{
			connection.setReadOnly(true); databaseAuthority(connection, false); owned(connection, owner);
			require(Files.readString(root().resolve("foreign.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
		}
		System.setProperty("phantom.economy.schema", SCHEMA);
		System.setProperty("phantom.economy.installation", owner.getProperty("installationId"));
		org.l2jmobius.commons.database.DatabaseFactory.initFromConfig(config.toString());
	}

	static void postflight() throws Exception
	{
		final var owner = authority();
		inputs(owner, true);
		try (var connection = connect(false))
		{
			connection.setReadOnly(true); databaseAuthority(connection, false); owned(connection, owner);
			require(Files.readString(root().resolve("foreign.before.sha256")).equals(foreignMetadata(connection)), "FOREIGN_METADATA_CHANGED");
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM characters WHERE online<>0")) { require(rows.next() && rows.getLong(1) == 0, "ONLINE_RESIDUE"); }
		}
		writeNew(root().resolve("postflight.completed"), "installationId=" + owner.getProperty("installationId") + "\nforeignMetadataUnchanged=true\nforeignSessions=0\nSQL_DDL_DML=0\n");
		System.out.println("TASK096N_POSTFLIGHT_PASS ownOnline=0 foreignSessions=0 foreignMetadataUnchanged=true sourceInputHashes=UNCHANGED");
	}

	private static void rejected(Runnable action)
	{
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError("TASK096N_INVALID_AUTHORITY_ACCEPTED");
	}

	private static void guards()
	{
		final String uuid = UUID.randomUUID().toString();
		final var authority = new Properties();
		authority.setProperty("owner", "TASK096_NATIVE_ACCEPTANCE");
		authority.setProperty("schema", SCHEMA);
		authority.setProperty("installationId", uuid);
		authority.setProperty("expiresUtc", Instant.now().plusSeconds(300).toString());
		ownerFields(authority, uuid);
		final var wrongSchema = new Properties(); wrongSchema.putAll(authority); wrongSchema.setProperty("schema", "l2jmobiush5");
		rejected(() -> ownerFields(wrongSchema, uuid));
		rejected(() -> ownerFields(authority, UUID.randomUUID().toString()));
		final var expired = new Properties(); expired.putAll(authority); expired.setProperty("expiresUtc", "2000-01-01T00:00:00Z");
		rejected(() -> ownerFields(expired, uuid));
		rejected(() -> safeSql("ALTER TABLE characters DROP COLUMN charId"));
		System.out.println("TASK096N_AUTHORITY_GUARDS_PASS 3/3 DB=NOT_OPENED World=NOT_STARTED");
		System.out.println("TASK096N_SQL_GUARD_PASS destructiveAlterRejected=true");
	}

	public static void main(String[] args)
	{
		int exit = 1;
		try
		{
			require(args.length == 1, "ACTION");
			switch (args[0])
			{
				case "guards" -> guards();
				case "pin" -> pin();
				case "seal-build" -> compiled(false);
				case "bootstrap" -> bootstrap();
				case "files" -> validate(Path.of(System.getProperty("phantom.module.root", "")), Path.of(System.getProperty("phantom.test.config", "")));
				case "postflight" -> postflight();
				default -> throw new IllegalArgumentException("TASK096N_ACTION");
			}
			exit = 0;
		}
		catch (Throwable failure)
		{
			final String message = failure.getMessage();
			System.out.println("TASK096N_FAIL type=" + failure.getClass().getName() + " key=" + (message != null && message.matches("TASK096N_[A-Z_]+") ? message : "WITHHELD"));
			for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("TASK096N_FRAME " + frame); } }
		}
		System.exit(exit);
	}
}
