/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/** Exact additive upgrade of this task's previously attested own TEST schema, never an arbitrary stale manifest repair. */
final class PhantomLearning004Schema
{
	private static final String SCRIPT = "dist/db_installer/sql/game/phantom_tactical_v3.sql";
	private static final String OLD_SCRIPT = "771923AC49ABAA60BA08D0149F7A9842A6E3AA174D2EED3CFD336FC791ADAFE6";
	private static final String OLD_ALL = "DB97B49B23193D4A3A75528F6378D12FEDEC0600D9E01276FC3E872FF4A5B0AA";
	private static final Set<String> ADDED = Set.of("failures","event_episodes","context_replay");
	static List<String> delta(List<String> before, List<String> after)
	{
		final var remaining = new ArrayList<>(after);
		for (String sql : before) { if (!remaining.remove(sql)) { throw new IllegalArgumentException("004 migration changed an existing statement"); } }
		final var names = new HashSet<String>();
		for (String sql : remaining)
		{
			final var match = java.util.regex.Pattern.compile("(?s)^CREATE TABLE IF NOT EXISTS `phantom_tactical_v3_(failures|event_episodes|context_replay)` \\(.*").matcher(sql.strip());
			if (!match.matches() || !names.add(match.group(1)) || sql.matches("(?is).*\\b(?:ALTER|DROP|DELETE|UPDATE|INSERT|DATABASE|USE|GRANT|USER)\\b.*"))
			{ throw new IllegalArgumentException("004 migration is not the exact additive table allowlist"); }
		}
		if (!names.equals(ADDED)) { throw new IllegalArgumentException("004 migration table set changed"); }
		return List.copyOf(remaining);
	}
	static void upgrade(Path root, Path module, Path config, int port) throws Exception
	{
		final var inventory = PhantomTestSchemaManifest.inventory(module);
		final var current = PhantomTestSchemaManifest.fromScripts(inventory);
		final Path local = root.resolve("sql/schema-manifest.properties");
		final var localState = PhantomTestSchemaManifest.read(local);
		if (localState.equals(current)) { return; } // Canonical probe below still validates DB identity and manifest.
		final Path fixture = module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-004/fixtures/schema-before-failure-ledger.sql");
		if (!fixture.equals(fixture.toRealPath()) || !sha(Files.readAllBytes(fixture)).equals(OLD_SCRIPT)) { throw new IllegalStateException("TRAIN002_004_MIGRATION_FIXTURE"); }
		final var oldStatements = StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
		final var oldInventory = new ArrayList<StrictSqlScriptRunner.ScriptInfo>();
		List<String> additions = null;
		for (var script : inventory)
		{
			if (script.relativePath().equals(SCRIPT))
			{
				additions = delta(oldStatements,script.statements());
				oldInventory.add(new StrictSqlScriptRunner.ScriptInfo(fixture,SCRIPT,OLD_SCRIPT,oldStatements));
			}
			else { oldInventory.add(script); }
		}
		final var previous = PhantomTestSchemaManifest.fromScripts(oldInventory);
		if (additions == null || !previous.aggregateSha256().equals(OLD_ALL) || !localState.equals(previous))
		{ throw new IllegalStateException("TRAIN002_004_MIGRATION_UNRECOGNIZED_SCOPE"); }
		final var settings = PhantomTrainingSql.read(config);
		final String endpoint = "jdbc:mysql://127.0.0.1:" + port + "/phantom_learning001_train002";
		if (!endpoint.equals(settings.getProperty("URL")) || !"learning001".equals(settings.getProperty("Login")))
		{ throw new IllegalStateException("TRAIN002_004_MIGRATION_ENDPOINT"); }
		try (var connection = DriverManager.getConnection(endpoint,"learning001",settings.getProperty("Password")))
		{
			PhantomTrainingSql.requireIdentity(connection,root,port,"phantom_learning001_train002","learning001@127.0.0.1");
			boolean alreadyPublished = false;
			try { PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,previous); }
			catch (PhantomTestConfigurationException changed)
			{ PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current); alreadyPublished = true; }
			final String before = retainedSha(connection);
			if (!alreadyPublished)
			{
				try (var statement = connection.createStatement()) { for (String sql : additions) { statement.executeUpdate(sql); } }
				if (!before.equals(retainedSha(connection))) { throw new IllegalStateException("TRAIN002_004_MIGRATION_RETAINED_DATA"); }
				PhantomTestSchemaManifest.writeDatabaseMetadata(connection,current);
			}
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection,current);
			final byte[] prior = Files.readAllBytes(local);
			final Path backup = Path.of(System.getProperty("java.io.tmpdir")).resolve("phantom-learning004-manifest-" + sha(prior));
			if (Files.exists(backup)) { if (!Arrays.equals(prior,Files.readAllBytes(backup))) { throw new IllegalStateException("TRAIN002_004_MIGRATION_BACKUP"); } }
			else { Files.write(backup,prior,StandardOpenOption.CREATE_NEW); }
			PhantomTestSchemaManifest.writeAtomic(local,current);
			System.out.println("LEARNING004_SCHEMA_UPGRADE tables=3 retainedSha=" + before + " previous=" + OLD_ALL + " current=" + current.aggregateSha256() + " recoveredPublication=" + alreadyPublished);
		}
	}
	private static String retainedSha(Connection connection) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		for (String table : List.of("events","optimizers","episodes","splits","runs","audit_used","models"))
		{
			digest.update(table.getBytes(StandardCharsets.US_ASCII));
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM phantom_tactical_v3_" + table + " ORDER BY 1"))
			{
				while (rows.next())
				{
					for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++)
					{
						final byte[] value = rows.getBytes(i); digest.update(java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
						if (value != null) { digest.update(value); }
					}
				}
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}
	private static String sha(byte[] bytes) throws Exception { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(args[0]).toRealPath();
		final var before = StrictSqlScriptRunner.splitStatements(module.resolve(SCRIPT),Files.readString(module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-004/fixtures/schema-before-failure-ledger.sql")));
		final var after = StrictSqlScriptRunner.splitStatements(module.resolve(SCRIPT),Files.readString(module.resolve(SCRIPT)));
		if (delta(before,after).size() != 3) { throw new AssertionError("Missing additive migration"); }
		final var changed = new ArrayList<>(after); changed.set(0,changed.getFirst().replace("MEDIUMBLOB","LONGBLOB")); reject(() -> delta(before,changed));
		final var dropped = new ArrayList<>(after); dropped.add("DROP TABLE phantom_tactical_v3_events"); reject(() -> delta(before,dropped));
		System.out.println("LEARNING004_SCHEMA_CONTRACT exactAdditive=PASS changedOld=REJECTED extraDDL=REJECTED DB_CONNECTIONS=0");
	}
	private static void reject(Runnable work) { try { work.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Unsafe migration accepted"); }
}
