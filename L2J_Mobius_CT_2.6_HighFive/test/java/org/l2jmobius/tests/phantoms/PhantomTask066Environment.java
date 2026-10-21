/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.commons.util.HexUtil;

/** Exact066 provisioning and read-only conservation receipts; never resets or imports an existing schema. */
public final class PhantomTask066Environment
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !List.of("preflight", "preservation-final", "bootstrap-test", "bootstrap-release", "inspect-release", "snapshot-release").contains(args[0])) { throw new IllegalArgumentException("Explicit TASK066 environment action required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task066");
		final boolean release = args[0].endsWith("-release");
		final String schema = release ? "l2jmobiush5_phantom_release_066a" : "l2jmobiush5_phantom_test_066";
		final Path config = task.resolve(release ? "release-input/Database.ini" : "test/Database.test.ini");
		final Properties settings = read(config);
		if (!settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?") || !"4".equals(settings.getProperty("MaximumDatabaseConnections"))) { throw new IllegalArgumentException("Exact066 JDBC and bounded pool required."); }
		Class.forName(settings.getProperty("Driver"));
		if (args[0].equals("inspect-release") || args[0].equals("snapshot-release"))
		{
			final var owner = read(task.resolve("release-input/owned.properties"));
			if (!"TASK066_FARM_ECONOMY".equals(owner.getProperty("owner")) || !schema.equals(owner.getProperty("database")) || !"RELEASE_CANDIDATE".equals(owner.getProperty("role"))) { throw new IllegalArgumentException("Exact own066 release read authority required."); }
			try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("Own066 read connected catalog differs."); }
				connection.setReadOnly(true); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
				inspectRelease(connection, task, args[0].equals("snapshot-release")); connection.rollback();
			}
			return;
		}
		try (var admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false", settings.getProperty("Login"), settings.getProperty("Password")))
		{
			if (args[0].equals("preflight") || args[0].equals("preservation-final"))
			{
				if (args[0].equals("preflight")) { requireAbsent(admin, "l2jmobiush5_phantom_test_066"); requireAbsent(admin, "l2jmobiush5_phantom_release_066a"); }
				preservation(admin, task.resolve(args[0] + "-db.tsv"));
				try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT VERSION()")) { rows.next(); System.out.println("TASK066_DB_READ_ONLY version=" + rows.getString(1)); }
				return;
			}
			final Path ownerPath = task.resolve(release ? "release-input/owned.properties" : "test/owned.properties");
			final Properties owner = read(ownerPath);
			if (!"TASK066_FARM_ECONOMY".equals(owner.getProperty("owner")) || !schema.equals(owner.getProperty("database")) || !(release ? "RELEASE_CANDIDATE" : "MUTABLE_FIXTURE").equals(owner.getProperty("role"))) { throw new IllegalArgumentException("Exact066 bootstrap authority required."); }
			final var scripts = PhantomTestSchemaManifest.inventory(module);
			final String aggregate = PhantomTestSchemaManifest.current(module).aggregateSha256();
			requireAbsent(admin, schema);
			Files.writeString(task.resolve(args[0] + ".started"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
			try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
			{
				if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("Own066 connected catalog differs."); }
				StrictSqlScriptRunner.execute(connection, scripts);
				final String hex = new BigInteger(HexUtil.generateHexBytes(16)).toString(16);
				try (var insert = connection.prepareStatement("INSERT INTO gameservers(server_id,hexid,host) VALUES(1,?,'')")) { insert.setString(1, hex); insert.executeUpdate(); }
				final Properties registration = new Properties(); registration.setProperty("ServerID", "1"); registration.setProperty("HexID", hex);
				try (var writer = Files.newBufferedWriter(task.resolve(release ? "hexid-release.txt" : "hexid-test.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { registration.store(writer, "Own066 registration"); }
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) { rows.next(); System.out.println("TASK066_TABLES=" + rows.getInt(1)); }
			}
			owner.setProperty("schemaAggregateSha256", aggregate);
			try (var writer = Files.newBufferedWriter(ownerPath, StandardCharsets.UTF_8)) { owner.store(writer, "Own066 schema authority"); }
			Files.writeString(task.resolve(args[0] + ".complete"), aggregate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK066_BOOTSTRAP_COMPLETE schema=" + schema + "; scripts=" + scripts.size() + "; aggregate=" + aggregate);
		}
	}

	private static void inspectRelease(Connection connection, Path task, boolean snapshot) throws Exception
	{
		long writers;
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()")) { rows.next(); writers = rows.getLong(1); }
		final var output = new ArrayList<String>(); output.add("TASK066_RELEASE_READ\totherConnections=" + writers + "\tconsistentRead=true");
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT p.profile_id,c.charId,c.char_name,c.classid,c.base_class,c.level,c.online,c.x,c.y,c.z FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id ORDER BY p.profile_id LIMIT 65"))
		{
			int count = 0;
			while (rows.next())
			{
				if (++count > 64) { throw new IllegalStateException("Own066 read exceeds bounded profile inventory."); }
				final var fields = new ArrayList<String>(); for (int column = 1; column <= 10; column++) { fields.add(rows.getString(column)); }
				output.add("IDENTITY\t" + String.join("\t", fields));
				if (snapshot && rows.getInt(7) != 0) { throw new IllegalStateException("Own066 snapshot requires all own identities offline."); }
			}
		}
		if (snapshot && writers != 0) { throw new IllegalStateException("Own066 snapshot requires stopped own schema with no other connections."); }
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT profile_id,payload FROM phantom_profile_components WHERE component_type='goal.runtime' ORDER BY profile_id LIMIT 65"))
		{
			int count = 0; final var codec = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec();
			while (rows.next()) { if (++count > 64) { throw new IllegalStateException("Own066 root inventory bound exceeded."); } final var goal = codec.decode(rows.getBytes(2)); output.add("ROOT\t" + rows.getLong(1) + '\t' + goal.goalId() + '\t' + goal.revision() + '\t' + goal.goalType() + '\t' + goal.target() + '\t' + goal.status()); }
		}
		for (String table : List.of("characters", "items", "character_skills", "phantom_profiles", "phantom_profile_components"))
		{
			final var hashes = new ArrayList<String>();
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM `" + table + "`"))
			{
				final int columns = rows.getMetaData().getColumnCount();
				while (rows.next())
				{
					if (hashes.size() >= 10000) { throw new IllegalStateException("Own066 snapshot row bound exceeded."); }
					final var digest = MessageDigest.getInstance("SHA-256");
					for (int column = 1; column <= columns; column++) { final byte[] value = rows.getBytes(column); digest.update((byte) (value == null ? 0 : 1)); if (value != null) { digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value); } }
					hashes.add(HexFormat.of().formatHex(digest.digest()));
				}
			}
			hashes.sort(String::compareTo); output.add("TABLE\t" + table + '\t' + hashes.size() + '\t' + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", hashes).getBytes(StandardCharsets.UTF_8))));
		}
		final String label = System.getProperty("phantom.task066.readLabel", "");
		if (!label.matches("[a-z0-9-]{1,48}")) { throw new IllegalArgumentException("Unique bounded own066 read evidence label required."); }
		Files.write(task.resolve(label + "-release-read.tsv"), output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("TASK066_RELEASE_READ snapshot=" + snapshot + "; otherConnections=" + writers + "; identities=" + output.stream().filter(line -> line.startsWith("IDENTITY")).count() + "; writes=0; label=" + label);
	}

	private static void requireAbsent(Connection connection, String schema) throws Exception
	{
		try (var query = connection.prepareStatement("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
		{
			query.setString(1, schema);
			try (var rows = query.executeQuery()) { if (rows.next()) { throw new IllegalStateException("Own066 schema already exists; no reset/reimport."); } }
		}
	}

	private static void preservation(Connection connection, Path receipt) throws Exception
	{
		final List<String> schemas = new ArrayList<>();
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('l2jmobiush5_phantom_release_065a','l2jmobiush5_phantom_test_065') ORDER BY SCHEMA_NAME"))
		{
			while (rows.next()) { final String name = rows.getString(1); if (!name.matches("[a-z0-9_]+")) { throw new IllegalStateException("Unexpected schema name."); } if (!name.equals("l2jmobiush5_phantom_test_066") && !name.equals("l2jmobiush5_phantom_release_066a")) { schemas.add(name); } }
		}
		final List<String> output = new ArrayList<>(List.of("schema\ttable\trows\tsha256"));
		for (String schema : schemas)
		{
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
			{
				query.setString(1, schema); try (var rows = query.executeQuery()) { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("Protected schema has an active writer/reader: " + schema); } }
			}
			for (String table : List.of("accounts", "characters", "items", "phantom_profiles", "phantom_profile_components", "phantom_commerce_receipts"))
			{
				try (var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?"))
				{
					query.setString(1, schema); query.setString(2, table); try (var rows = query.executeQuery()) { rows.next(); if (rows.getInt(1) == 0) { continue; } }
				}
				final List<String> hashes = new ArrayList<>();
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM `" + schema + "`.`" + table + "`"))
				{
					final int columns = rows.getMetaData().getColumnCount();
					while (rows.next())
					{
						final var digest = MessageDigest.getInstance("SHA-256");
						for (int column = 1; column <= columns; column++) { final byte[] value = rows.getBytes(column); digest.update((byte) (value == null ? 0 : 1)); if (value != null) { digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value); } }
						hashes.add(HexFormat.of().formatHex(digest.digest()));
					}
				}
				hashes.sort(String::compareTo);
				output.add(schema + "\t" + table + "\t" + hashes.size() + "\t" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", hashes).getBytes(StandardCharsets.UTF_8))));
			}
		}
		Files.write(receipt, output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("TASK066_PROTECTED_DB_RECEIPT schemas=" + schemas.size() + "; rows=" + (output.size() - 1));
	}

	private static Properties read(Path file) throws Exception
	{
		if (!file.equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("Own066 regular bounded config required."); }
		final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
}
