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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StrictSqlScriptRunner
{
	private static final Pattern UNSUPPORTED_SYNTAX = Pattern.compile("(?im)^\\s*(DELIMITER|SOURCE)\\b|/\\*!|\\b(CREATE|DROP)\\s+(PROCEDURE|FUNCTION|TRIGGER|EVENT)\\b");
	private static final Comparator<Path> FILE_ORDER = Comparator.comparing((Path path) -> path.getFileName().toString().toLowerCase(Locale.ROOT)).thenComparing(path -> path.getFileName().toString());

	private StrictSqlScriptRunner()
	{
	}

	public static List<ScriptInfo> inventory(Path moduleRoot, List<Path> roots) throws IOException
	{
		final List<ScriptInfo> scripts = new ArrayList<>();
		for (Path root : roots)
		{
			if (!Files.isDirectory(root))
			{
				throw new IOException("SQL script directory is missing: " + moduleRoot.relativize(root));
			}

			final List<Path> files;
			try (var stream = Files.list(root))
			{
				files = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql")).sorted(FILE_ORDER).toList();
			}

			if (files.isEmpty())
			{
				throw new IOException("SQL script directory is empty: " + moduleRoot.relativize(root));
			}

			for (Path file : files)
			{
				final String content = Files.readString(file, StandardCharsets.UTF_8);
				preflight(file, content);
				scripts.add(new ScriptInfo(file, moduleRoot.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)), splitStatements(file, content)));
			}
		}
		return List.copyOf(scripts);
	}

	public static void execute(Connection connection, List<ScriptInfo> scripts) throws SQLException
	{
		for (ScriptInfo script : scripts)
		{
			try (Statement statement = connection.createStatement())
			{
				executeStatements(script, sql -> statement.execute(sql));
			}
		}
	}

	static void executeStatements(ScriptInfo script, SqlExecutor executor) throws SQLException
	{
		for (int index = 0; index < script.statements().size(); index++)
		{
			try
			{
				executor.execute(script.statements().get(index));
			}
			catch (SQLException e)
			{
				throw new SqlScriptException(script.relativePath(), index + 1, e);
			}
		}
	}

	public static List<String> splitStatements(Path file, String content)
	{
		preflight(file, content);
		final List<String> statements = new ArrayList<>();
		final StringBuilder statement = new StringBuilder();
		char quote = 0;
		boolean lineComment = false;
		boolean blockComment = false;

		for (int index = 0; index < content.length(); index++)
		{
			final char current = content.charAt(index);
			final char next = (index + 1) < content.length() ? content.charAt(index + 1) : 0;

			if (lineComment)
			{
				if ((current == '\n') || (current == '\r'))
				{
					lineComment = false;
					statement.append(' ');
				}
				continue;
			}

			if (blockComment)
			{
				if ((current == '*') && (next == '/'))
				{
					blockComment = false;
					index++;
					statement.append(' ');
				}
				continue;
			}

			if (quote != 0)
			{
				statement.append(current);
				if ((current == '\\') && (next != 0))
				{
					statement.append(next);
					index++;
				}
				else if (current == quote)
				{
					if (next == quote)
					{
						statement.append(next);
						index++;
					}
					else
					{
						quote = 0;
					}
				}
				continue;
			}

			if ((current == '\'') || (current == '"') || (current == '`'))
			{
				quote = current;
				statement.append(current);
			}
			else if ((current == '/') && (next == '*'))
			{
				blockComment = true;
				index++;
			}
			else if (current == '#')
			{
				lineComment = true;
			}
			else if ((current == '-') && (next == '-') && (((index + 2) >= content.length()) || Character.isWhitespace(content.charAt(index + 2))))
			{
				lineComment = true;
				index++;
			}
			else if (current == ';')
			{
				final String sql = statement.toString().trim();
				if (!sql.isEmpty())
				{
					statements.add(sql);
				}
				statement.setLength(0);
			}
			else
			{
				statement.append(current);
			}
		}

		if ((quote != 0) || blockComment)
		{
			throw new IllegalArgumentException("Unterminated SQL quote/comment in " + file.getFileName() + ".");
		}

		if (!statement.toString().trim().isEmpty())
		{
			throw new IllegalArgumentException("Unterminated SQL statement in " + file.getFileName() + ".");
		}

		return List.copyOf(statements);
	}

	private static void preflight(Path file, String content)
	{
		if (UNSUPPORTED_SYNTAX.matcher(content).find())
		{
			throw new IllegalArgumentException("Unsupported SQL syntax in " + file.getFileName() + ".");
		}
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	public record ScriptInfo(Path path, String relativePath, String sha256, List<String> statements)
	{
	}

	@FunctionalInterface
	interface SqlExecutor
	{
		void execute(String sql) throws SQLException;
	}

	public static final class SqlScriptException extends SQLException
	{
		private static final long serialVersionUID = 1L;

		public SqlScriptException(String relativePath, int statementIndex, SQLException cause)
		{
			super("SQL script failed at " + relativePath + " statement " + statementIndex + ".", cause);
		}
	}
}
