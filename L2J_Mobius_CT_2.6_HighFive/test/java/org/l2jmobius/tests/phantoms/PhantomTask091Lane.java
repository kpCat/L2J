package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

import org.l2jmobius.commons.database.DatabaseFactory;

/** Test-only exact091 authority. No historical lane or generic DB guard is widened. */
public final class PhantomTask091Lane
{
	private static final boolean OFFSCREEN092B = Boolean.getBoolean("phantom.task092b.native");
	private static final String FLAG = OFFSCREEN092B ? "phantom.task092b" : "phantom.task091";
	static final String SCHEMA = OFFSCREEN092B ? "l2jmobiush5_phantom_test_092b" : "l2jmobiush5_phantom_test_091";
	static final String BASELINE = OFFSCREEN092B ? "ac5931941f36fbdbb1a64e8d848b5fb57853b74c" : "627bca947fd565ce18aa5cc85c7e1aa1288ccaef";
	static final String SQL_HASH = "8699ACDD436D29F5666CCC501E6B8D9A09265F064FC16AB7A7166BE04C84AEA2";
	static final String MIGRATION = "e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3";
	static boolean enabled() { return Boolean.getBoolean(FLAG + ".native"); }
	static Path root() { return Path.of(System.getProperty(FLAG + ".root", "")); }
	static void require(boolean value, String key) { if (!value) { throw new IllegalArgumentException("TASK091_" + key); } }
	static Path plain(Path path) throws Exception
	{
		final Path absolute = path.toAbsolutePath();
		require(absolute.equals(absolute.normalize()) && absolute.equals(path.toRealPath()) && !Files.isSymbolicLink(path), "PHYSICAL_PATH");
		return absolute;
	}
	static Properties read(Path file) throws Exception
	{
		plain(file); require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 16384, "BOUNDED_FILE");
		final var result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}
	static String hash(Path file) throws Exception
	{
		plain(file); final var digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(file)) { final byte[] block = new byte[65536]; for (int count; (count = input.read(block)) >= 0;) { digest.update(block, 0, count); } }
		return HexFormat.of().formatHex(digest.digest());
	}
	static String hashText(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
	static void privateAcl(Path path) throws Exception
	{
		final var owner = Files.getOwner(plain(root()));
		final var lookup = path.getFileSystem().getUserPrincipalLookupService();
		require(owner.equals(lookup.lookupPrincipalByName(System.getProperty("user.name"))), "ACL_OWNER");
		final var system = lookup.lookupPrincipalByName("SYSTEM");
		final var acl = Files.getFileAttributeView(plain(path), AclFileAttributeView.class);
		require(acl != null, "ACL_UNAVAILABLE");
		for (var rule : acl.getAcl()) { require(rule.type() != AclEntryType.ALLOW || rule.principal().equals(owner) || rule.principal().equals(system), "PRIVATE_ACL"); }
	}
	static void ownerFields(Properties owner, Path module, String uuid) throws Exception
	{
		require((OFFSCREEN092B ? "TASK092B_NATIVE_TRAVEL" : "TASK091_NATIVE_FARM").equals(owner.getProperty("owner")) && SCHEMA.equals(owner.getProperty("schema")) && BASELINE.equals(owner.getProperty("baseline")), "OWNER_SCHEMA_BASELINE");
		require(UUID.fromString(uuid).toString().equals(uuid) && uuid.equals(owner.getProperty("installationId")), "OWNER_UUID");
		require(plain(module).equals(plain(Path.of(owner.getProperty("shadowModule", "")))) && plain(root()).equals(plain(Path.of(owner.getProperty("stage", "")))), "OWNER_ROOT");
		require(Instant.now().isBefore(Instant.parse(owner.getProperty("expiresUtc"))), "EXPIRED");
		final Path origin = plain(Path.of(owner.getProperty("sourceModule")));
		require(!plain(root()).startsWith(origin.getParent()) && module.startsWith(plain(root())), "EXTERNAL_ROOT");
	}
	static void settings(Properties settings, Properties owner) throws Exception
	{
		require(("jdbc:mysql://127.0.0.1:3308/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8").equals(settings.getProperty("URL")), "JDBC_EXACT");
		require(hashText(settings.getProperty("Login", "")).equals(owner.getProperty("loginSha256")) && !settings.getProperty("Login", "").isBlank() && settings.containsKey("Password"), "LOGIN_BINDING");
		require("com.mysql.cj.jdbc.Driver".equals(settings.getProperty("Driver")) && "4".equals(settings.getProperty("MaximumDatabaseConnections")) && "false".equalsIgnoreCase(settings.getProperty("TestDatabaseConnections")) && "false".equalsIgnoreCase(settings.getProperty("BackupDatabase")), "BOUNDED_CONFIG");
	}
	static void flags()
	{
		require(enabled(), "NATIVE_FLAG");
		for (String key : System.getProperties().stringPropertyNames())
		{
			require(!(key.startsWith("phantom.task") && key.endsWith(".native") && !key.equals(FLAG + ".native") && Boolean.getBoolean(key)), "FOREIGN_NATIVE");
			require(!(key.startsWith("phantom.") && (key.endsWith(".manifest") || key.endsWith(".persistent") || key.endsWith(".control"))), "FOREIGN_AUTHORITY");
		}
	}
	static void inputFile(Path file, String expected) throws Exception { require(hash(file).equals(expected), "INPUT_DRIFT"); }
	static void inputs(Properties owner, Path module) throws Exception
	{
		final Path manifest = plain(root()).resolve("inputs.tsv"); inputFile(manifest, owner.getProperty("inputsSha256"));
		final Path origin = plain(Path.of(owner.getProperty("sourceModule")));
		final Path geo = plain(Path.of(owner.getProperty("geoSource")));
		inputFile(origin.resolve("dist/game/config/Database.ini"), owner.getProperty("sourceDatabaseSha256"));
		int count = 0;
		for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8))
		{
			final String[] parts = line.split("\t", -1); require(parts.length == 3 && parts[2].matches("[0-9a-f]{64}"), "INPUT_MANIFEST");
			final Path source = plain(Path.of(parts[0])), copy = plain(Path.of(parts[1]));
			require((source.startsWith(origin) || source.startsWith(geo)) && copy.startsWith(module) && !copy.equals(module.resolve("dist/game/config/Database.ini")), "INPUT_ESCAPE");
			inputFile(source, parts[2]);
			if (OFFSCREEN092B && Boolean.getBoolean("phantom.task092b.safeCleanupOverlay") && copy.equals(module.resolve("dist/game/config/IdManager.ini")))
			{
				final String original = Files.readString(source, StandardCharsets.UTF_8);
				require(original.indexOf("DatabaseCleanUp = True") >= 0 && original.indexOf("DatabaseCleanUp = True") == original.lastIndexOf("DatabaseCleanUp = True"), "DIAGNOSTIC_CLEANUP_PREIMAGE");
				require(Files.readString(copy, StandardCharsets.UTF_8).equals(original.replace("DatabaseCleanUp = True", "DatabaseCleanUp = False")), "DIAGNOSTIC_CLEANUP_EXACT_OVERLAY");
			}
			else { inputFile(copy, parts[2]); }
			count++;
		}
		require(count > 1000 && SQL_HASH.equals(PhantomTestSchemaManifest.current(module).aggregateSha256()), "CANONICAL_SQL_DRIFT");
	}
	static Properties validateAt(Path module, Path config, Path cwd) throws Exception
	{
		flags(); plain(root()); privateAcl(root());
		final Path ownership = root().resolve("private/owner.properties"); privateAcl(ownership);
		final Properties owner = read(ownership); ownerFields(owner, module, System.getProperty(FLAG + ".uuid", ""));
		require(plain(cwd).equals(module.resolve("dist/game")) && plain(config).equals(module.resolve("dist/game/config/Database.ini")), "CWD_CONFIG");
		require(!Files.isSameFile(config, Path.of(owner.getProperty("sourceModule")).resolve("dist/game/config/Database.ini")), "PRODUCTION_CONFIG");
		privateAcl(config); settings(read(config), owner); inputs(owner, module); return owner;
	}
	static void validate(Path module, Path config) throws Exception { validateAt(module, config, Path.of("").toAbsolutePath()); }
	static Connection connect(Properties settings, boolean admin) throws Exception
	{
		Class.forName(settings.getProperty("Driver"));
		return DriverManager.getConnection(admin ? "jdbc:mysql://127.0.0.1:3308/?useSSL=false" : settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
	}
	static void absent(Connection connection) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
		{ query.setString(1, SCHEMA); try (var rows = query.executeQuery()) { rows.next(); require(rows.getLong(1) == 0, "EXISTING_SCHEMA"); } }
	}
	static void quiescent(Connection connection) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
		{ query.setString(1, SCHEMA); try (var rows = query.executeQuery()) { rows.next(); require(rows.getLong(1) == 0, "FOREIGN_CONNECTIONS"); } }
	}
	static void owned(Connection connection, Properties owner) throws Exception
	{
		require(SCHEMA.equals(connection.getCatalog()), "CATALOG");
		try (var query = connection.prepareStatement("SELECT task_id,installation_id,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
		{
			query.setString(1, SCHEMA);
			try (var rows = query.executeQuery()) { require(rows.next() && rows.getInt(1) == (OFFSCREEN092B ? 92 : 91) && owner.getProperty("installationId").equals(rows.getString(2)) && MIGRATION.equals(rows.getString(3)) && !rows.next(), "DATABASE_OWNER"); }
		}
		PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, PhantomTestSchemaManifest.current(Path.of(owner.getProperty("shadowModule"))));
	}
	static void initialize(Path module, Path config) throws Exception
	{
		final var owner = validateAt(module, config, Path.of("").toAbsolutePath());
		require(Files.isRegularFile(root().resolve("bootstrap.complete")), "BOOTSTRAP_INCOMPLETE");
		try (var connection = connect(read(config), false)) { owned(connection, owner); quiescent(connection); }
		System.setProperty("phantom.economy.schema", SCHEMA); System.setProperty("phantom.economy.installation", owner.getProperty("installationId"));
		DatabaseFactory.initFromConfig(config.toString());
	}
	static void environment(Path module, Path config, String action) throws Exception
	{
		final var owner = validateAt(module, config, Path.of("").toAbsolutePath());
		final var settings = read(config);
		try (var admin = connect(settings, true))
		{
			quiescent(admin);
			if (action.equals("connections")) { System.out.println("TASK091_CONNECTIONS_ZERO"); return; }
			absent(admin);
			if (action.equals("preflight")) { System.out.println("TASK091_ABSENT_EXACT"); return; }
			require(action.equals("bootstrap"), "ACTION");
			inputFile(module.resolve("dist/db_installer/sql/game/economy071.sql"), MIGRATION);
			final var scripts = PhantomTestSchemaManifest.inventory(module);
			for (var script : scripts) for (String sql : script.statements())
			{
				if (sql.stripLeading().matches("(?is)(DROP|TRUNCATE|DELETE|USE)\\b.*"))
				{ require(sql.matches("(?is)\\s*DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`?[A-Za-z0-9_]+`?\\s*"), "DESTRUCTIVE_INSTALLER"); }
			}
			Files.writeString(root().resolve("bootstrap.started"), owner.getProperty("installationId"), StandardOpenOption.CREATE_NEW);
			try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + SCHEMA + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			try (var connection = connect(settings, false))
			{
				require(SCHEMA.equals(connection.getCatalog()), "CATALOG");
				final int[] omittedNoops = {0};
				for (var script : scripts)
				{
					try (var statement = connection.createStatement())
					{
						StrictSqlScriptRunner.executeStatements(script, sql ->
						{
							final var drop = java.util.regex.Pattern.compile("(?is)\\s*DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`?([A-Za-z0-9_]+)`?\\s*").matcher(sql);
							if (drop.matches())
							{
								try (var tables = connection.getMetaData().getTables(SCHEMA, null, drop.group(1), new String[] {"TABLE"})) { require(!tables.next(), "FRESH_TABLE_ALREADY_EXISTS"); }
								omittedNoops[0]++; return;
							}
							require(!sql.stripLeading().matches("(?is)(DROP|TRUNCATE|DELETE|USE)\\b.*"), "DESTRUCTIVE_INSTALLER");
							statement.execute(sql);
						});
					}
				}
				System.out.println("TASK091_CANONICAL_INSTALL omittedAbsentDropNoops=" + omittedNoops[0] + "; DROP/TRUNCATE/DELETE=0");
				try (var insert = connection.prepareStatement("INSERT INTO economy071_authority(schema_name,task_id,installation_id,schema_version,migration_sha256) VALUES(?," + (OFFSCREEN092B ? 92 : 91) + ",?,1,?)"))
				{ insert.setString(1, SCHEMA); insert.setString(2, owner.getProperty("installationId")); insert.setString(3, MIGRATION); insert.executeUpdate(); }
				PhantomTestSchemaManifest.writeDatabaseMetadata(connection, PhantomTestSchemaManifest.current(module)); owned(connection, owner);
			}
			Files.writeString(root().resolve("bootstrap.complete"), SQL_HASH, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK091_BOOTSTRAP_PASS scripts=" + scripts.size() + "; ownerBound=true");
		}
	}
	@FunctionalInterface interface Check { void run() throws Exception; }
	static void rejected(String key, Check check) throws Exception
	{
		try { check.run(); } catch (IllegalArgumentException expected) { require(expected.getMessage().equals("TASK091_" + key), "WRONG_REJECTION"); return; }
		throw new AssertionError("TASK091 negative admitted: " + key);
	}
	public static void controls() throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root")), config = module.resolve("dist/game/config/Database.ini");
		final var owner = validateAt(module, config, Path.of("").toAbsolutePath()); final var valid = read(config);
		for (String suffix : new String[] {"074", "085", "078", "086"})
		{ final var wrong = new Properties(); wrong.putAll(valid); wrong.setProperty("URL", valid.getProperty("URL").replace("test_091", "test_" + suffix)); rejected("JDBC_EXACT", () -> settings(wrong, owner)); }
		final var port = new Properties(); port.putAll(valid); port.setProperty("URL", valid.getProperty("URL").replace(":3308/", ":3306/")); rejected("JDBC_EXACT", () -> settings(port, owner));
		rejected("OWNER_UUID", () -> ownerFields(owner, module, UUID.randomUUID().toString()));
		final var missing = new Properties(); missing.putAll(owner); missing.remove("owner"); rejected("OWNER_SCHEMA_BASELINE", () -> ownerFields(missing, module, owner.getProperty("installationId")));
		final var stale = new Properties(); stale.putAll(owner); stale.setProperty("baseline", "0".repeat(40)); rejected("OWNER_SCHEMA_BASELINE", () -> ownerFields(stale, module, owner.getProperty("installationId")));
		final var expired = new Properties(); expired.putAll(owner); expired.setProperty("expiresUtc", "2000-01-01T00:00:00Z"); rejected("EXPIRED", () -> ownerFields(expired, module, owner.getProperty("installationId")));
		rejected("OWNER_ROOT", () -> ownerFields(owner, root().resolve("private"), owner.getProperty("installationId")));
		rejected("CWD_CONFIG", () -> validateAt(module, config, root()));
		rejected("CWD_CONFIG", () -> validateAt(module, Path.of(owner.getProperty("sourceModule")).resolve("dist/game/config/Database.ini"), module.resolve("dist/game")));
		rejected("PHYSICAL_PATH", () -> plain(module.resolve("../source")));
		rejected("PHYSICAL_PATH", () -> plain(root().resolve("negative-junction")));
		System.setProperty("phantom.task074.native", "true"); try { rejected("FOREIGN_NATIVE", PhantomTask091Lane::flags); } finally { System.clearProperty("phantom.task074.native"); }
		final Path drift = Files.createTempFile(root(), "negative-drift", ".txt");
		try { Files.writeString(drift, "changed"); rejected("INPUT_DRIFT", () -> inputFile(drift, "0".repeat(64))); } finally { Files.delete(drift); }
		System.out.println("TASK091_GUARDS_PASS positiveActual=1; negatives=16; SQL=0");
	}
}
