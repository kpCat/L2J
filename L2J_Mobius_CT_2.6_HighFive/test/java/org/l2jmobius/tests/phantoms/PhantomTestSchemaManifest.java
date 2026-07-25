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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PhantomTestSchemaManifest
{
	public static final int SCHEMA_VERSION = 1;
	public static final String LOCAL_MANIFEST_FILE = "schema-manifest.properties";
	public static final String MANIFEST_KEY = "repository-schema";
	public static final String METADATA_TABLE = "phantom_test_schema_manifest";
	private static final Pattern POSITIVE_INTEGER = Pattern.compile("[1-9][0-9]*");
	private static final Pattern UPPERCASE_SHA256 = Pattern.compile("[0-9A-F]{64}");
	private static final List<String> PROPERTY_KEYS = List.of("schemaVersion", "scriptCount", "statementCount", "aggregateSha256");
	private static final List<String> SQL_ROOTS = List.of("dist/db_installer/sql/login", "dist/db_installer/sql/game", "test/resources/phantoms/db/migrations");

	private PhantomTestSchemaManifest()
	{
	}

	public static Path localPath(Path moduleRoot)
	{
		return moduleRoot.resolve(PhantomTestDatabaseGuard.LOCAL_CONFIG_DIRECTORY).resolve(LOCAL_MANIFEST_FILE);
	}

	public static List<StrictSqlScriptRunner.ScriptInfo> inventory(Path moduleRoot) throws IOException
	{
		final List<Path> roots = SQL_ROOTS.stream().map(moduleRoot::resolve).toList();
		return StrictSqlScriptRunner.inventory(moduleRoot, roots);
	}

	public static Snapshot current(Path moduleRoot) throws IOException
	{
		return fromScripts(inventory(moduleRoot));
	}

	static Snapshot fromScripts(List<StrictSqlScriptRunner.ScriptInfo> scripts)
	{
		final List<StrictSqlScriptRunner.ScriptInfo> ordered = new ArrayList<>(scripts);
		ordered.sort(Comparator.comparing(StrictSqlScriptRunner.ScriptInfo::relativePath));
		final List<String> lines = new ArrayList<>();
		int statementCount = 0;
		for (StrictSqlScriptRunner.ScriptInfo script : ordered)
		{
			statementCount += script.statements().size();
			lines.add(script.relativePath() + " sha256=" + script.sha256() + " statements=" + script.statements().size());
		}
		return new Snapshot(SCHEMA_VERSION, ordered.size(), statementCount, sha256(String.join("\n", lines) + "\n"));
	}

	public static Snapshot read(Path manifestFile) throws PhantomTestConfigurationException
	{
		if (!Files.isRegularFile(manifestFile))
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest is missing.");
		}

		final List<String> lines;
		try
		{
			lines = Files.readAllLines(manifestFile, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest is unreadable.", e);
		}

		final Map<String, String> properties = new HashMap<>();
		for (String line : lines)
		{
			if (line.isBlank())
			{
				throw new PhantomTestConfigurationException("Phantom test schema manifest contains a blank line.");
			}
			final int separator = line.indexOf('=');
			if ((separator <= 0) || (separator != line.lastIndexOf('=')) || (separator == (line.length() - 1)))
			{
				throw new PhantomTestConfigurationException("Phantom test schema manifest contains a malformed property.");
			}
			final String key = line.substring(0, separator);
			final String value = line.substring(separator + 1);
			if (!PROPERTY_KEYS.contains(key) || (properties.putIfAbsent(key, value) != null))
			{
				throw new PhantomTestConfigurationException("Phantom test schema manifest contains an unknown or duplicate property.");
			}
		}
		if ((properties.size() != PROPERTY_KEYS.size()) || !properties.keySet().containsAll(PROPERTY_KEYS))
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest properties are incomplete.");
		}

		final String version = properties.get("schemaVersion");
		final String scripts = properties.get("scriptCount");
		final String statements = properties.get("statementCount");
		final String aggregate = properties.get("aggregateSha256");
		if (!Integer.toString(SCHEMA_VERSION).equals(version))
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest version is unsupported.");
		}
		if (!POSITIVE_INTEGER.matcher(scripts).matches() || !POSITIVE_INTEGER.matcher(statements).matches())
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest counts are malformed.");
		}
		if (!UPPERCASE_SHA256.matcher(aggregate).matches())
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest hash is malformed.");
		}
		try
		{
			return new Snapshot(Integer.parseInt(version), Integer.parseInt(scripts), Integer.parseInt(statements), aggregate);
		}
		catch (NumberFormatException e)
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest count is out of range.", e);
		}
	}

	public static void writeAtomic(Path manifestFile, Snapshot snapshot) throws IOException
	{
		Files.createDirectories(manifestFile.toAbsolutePath().normalize().getParent());
		final Path temporary = Files.createTempFile(manifestFile.getParent(), manifestFile.getFileName().toString() + ".", ".tmp");
		boolean moved = false;
		try
		{
			Files.writeString(temporary, snapshot.content(), StandardCharsets.UTF_8);
			try
			{
				Files.move(temporary, manifestFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				moved = true;
			}
			catch (AtomicMoveNotSupportedException e)
			{
				throw new IOException("Atomic Phantom test schema manifest move is not supported.", e);
			}
		}
		finally
		{
			if (!moved)
			{
				Files.deleteIfExists(temporary);
			}
		}
	}

	public static void requireExact(Snapshot expected, Snapshot actual) throws PhantomTestConfigurationException
	{
		if (!expected.equals(actual))
		{
			throw new PhantomTestConfigurationException("Phantom test schema manifest is stale.");
		}
	}

	public static void writeDatabaseMetadata(Connection connection, Snapshot snapshot) throws SQLException
	{
		final String sql = "INSERT INTO " + METADATA_TABLE + " (manifest_key, schema_version, script_count, statement_count, aggregate_sha256) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE schema_version = VALUES(schema_version), script_count = VALUES(script_count), statement_count = VALUES(statement_count), aggregate_sha256 = VALUES(aggregate_sha256)";
		try (var statement = connection.prepareStatement(sql))
		{
			statement.setString(1, MANIFEST_KEY);
			statement.setInt(2, snapshot.schemaVersion());
			statement.setInt(3, snapshot.scriptCount());
			statement.setInt(4, snapshot.statementCount());
			statement.setString(5, snapshot.aggregateSha256());
			statement.executeUpdate();
		}
	}

	public static void requireExactDatabaseMetadata(Connection connection, Snapshot expected) throws SQLException, PhantomTestConfigurationException
	{
		final String sql = "SELECT schema_version, script_count, statement_count, aggregate_sha256 FROM " + METADATA_TABLE + " WHERE manifest_key = ?";
		try (var statement = connection.prepareStatement(sql))
		{
			statement.setString(1, MANIFEST_KEY);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new PhantomTestConfigurationException("Phantom test schema metadata row is missing.");
				}
				final Snapshot actual = new Snapshot(result.getInt(1), result.getInt(2), result.getInt(3), result.getString(4));
				if (result.next())
				{
					throw new PhantomTestConfigurationException("Phantom test schema metadata contains duplicate canonical rows.");
				}
				validateSnapshot(actual);
				requireExact(expected, actual);
			}
		}
	}

	private static void validateSnapshot(Snapshot snapshot) throws PhantomTestConfigurationException
	{
		if ((snapshot.schemaVersion() != SCHEMA_VERSION) || (snapshot.scriptCount() < 1) || (snapshot.statementCount() < 1) || (snapshot.aggregateSha256() == null) || !UPPERCASE_SHA256.matcher(snapshot.aggregateSha256()).matches())
		{
			throw new PhantomTestConfigurationException("Phantom test schema metadata row is malformed.");
		}
	}

	private static String sha256(String value)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	public record Snapshot(int schemaVersion, int scriptCount, int statementCount, String aggregateSha256)
	{
		String content()
		{
			return "schemaVersion=" + schemaVersion + "\n" +
				"scriptCount=" + scriptCount + "\n" +
				"statementCount=" + statementCount + "\n" +
				"aggregateSha256=" + aggregateSha256 + "\n";
		}
	}
}
