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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class PhantomTestDatabaseGuard
{
	public static final String TARGET_DATABASE = "l2jmobiush5_phantom_test";
	public static final String PRODUCTION_DATABASE = "l2jmobiush5";
	public static final String TARGET_USER = "l2j_phantom_test";
	public static final int TARGET_PORT = 3308;
	public static final int MAX_TEST_POOL_SIZE = 4;
	public static final String LOCAL_CONFIG_DIRECTORY = ".phantom-local";
	public static final String LOCAL_CONFIG_FILE = "Database.test.ini";
	private static final Map<String, String> ALLOWED_QUERY = Map.of(
		"useSSL", "false",
		"allowPublicKeyRetrieval", "true",
		"serverTimezone", "UTC",
		"characterEncoding", "UTF-8");

	private PhantomTestDatabaseGuard()
	{
	}

	public static ValidatedSettings validate(Path moduleRoot, Path configFile) throws GuardException
	{
		if ((moduleRoot == null) || (configFile == null))
		{
			throw new GuardException("Test database module/config path is required.");
		}

		final Path canonicalModule;
		final Path canonicalConfig;
		try
		{
			canonicalModule = moduleRoot.toRealPath();
			canonicalConfig = configFile.toRealPath();
		}
		catch (IOException e)
		{
			throw new GuardException("Test database configuration file is missing or unreadable.", e);
		}

		final Path productionConfig = canonicalModule.resolve("dist/game/config/Database.ini").normalize();
		try
		{
			if (Files.exists(productionConfig) && canonicalConfig.equals(productionConfig.toRealPath()))
			{
				throw new GuardException("Production database configuration is forbidden.");
			}
		}
		catch (IOException e)
		{
			throw new GuardException("Production database configuration path could not be verified.", e);
		}

		final Path localRoot = canonicalModule.resolve(LOCAL_CONFIG_DIRECTORY);
		try
		{
			if (!canonicalConfig.startsWith(localRoot.toRealPath()))
			{
				throw new GuardException("Test database configuration must remain inside .phantom-local.");
			}
		}
		catch (IOException e)
		{
			throw new GuardException("Local test configuration directory could not be verified.", e);
		}

		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(canonicalConfig, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		catch (IOException e)
		{
			throw new GuardException("Test database configuration could not be read.", e);
		}

		final String driver = require(properties, "Driver");
		final String url = require(properties, "URL");
		final String login = require(properties, "Login");
		final String password = require(properties, "Password");
		if (!TARGET_USER.equals(login))
		{
			throw new GuardException("Dedicated test database username is required.");
		}

		final int maximumConnections;
		try
		{
			maximumConnections = Integer.parseInt(require(properties, "MaximumDatabaseConnections"));
		}
		catch (NumberFormatException e)
		{
			throw new GuardException("Test database maximum connections must be numeric.", e);
		}

		if ((maximumConnections < 1) || (maximumConnections > MAX_TEST_POOL_SIZE))
		{
			throw new GuardException("Test database maximum connections must be between 1 and 4.");
		}

		requireFalse(properties, "TestDatabaseConnections");
		requireFalse(properties, "BackupDatabase");
		validateJdbcUrl(url);
		return new ValidatedSettings(canonicalConfig, driver, url, login, password, maximumConnections);
	}

	public static JdbcTarget validateJdbcUrl(String jdbcUrl) throws GuardException
	{
		if ((jdbcUrl == null) || jdbcUrl.isBlank())
		{
			throw new GuardException("Test JDBC URL is required.");
		}

		final String transport;
		if (jdbcUrl.startsWith("jdbc:mysql://"))
		{
			transport = "mysql";
		}
		else if (jdbcUrl.startsWith("jdbc:mariadb://"))
		{
			transport = "mariadb";
		}
		else
		{
			throw new GuardException("Only mysql or mariadb JDBC URLs are allowed.");
		}

		final URI uri;
		try
		{
			uri = new URI(jdbcUrl.substring("jdbc:".length()));
		}
		catch (URISyntaxException e)
		{
			throw new GuardException("Test JDBC URL is malformed.", e);
		}

		if ((uri.getFragment() != null) || (uri.getUserInfo() != null))
		{
			throw new GuardException("JDBC URL fragments and embedded credentials are forbidden.");
		}

		final String authority = uri.getRawAuthority();
		if ((authority == null) || authority.contains(",") || authority.contains("@"))
		{
			throw new GuardException("JDBC URL must contain one credential-free host.");
		}

		final String host = uri.getHost();
		if (!"127.0.0.1".equals(host) && !"localhost".equals(host))
		{
			throw new GuardException("Test database host must be local.");
		}

		if (uri.getPort() != TARGET_PORT)
		{
			throw new GuardException("Test database port must be 3308.");
		}

		final String rawPath = uri.getRawPath();
		final String decodedPath;
		try
		{
			decodedPath = URLDecoder.decode(rawPath == null ? "" : rawPath, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException e)
		{
			throw new GuardException("Test database path encoding is invalid.", e);
		}

		final String expectedPath = "/" + TARGET_DATABASE;
		if ((rawPath == null) || rawPath.contains("%") || !expectedPath.equals(decodedPath) || !expectedPath.equals(rawPath))
		{
			throw new GuardException("JDBC URL must name the exact allowlisted test database.");
		}

		if (("/" + PRODUCTION_DATABASE).equals(decodedPath))
		{
			throw new GuardException("Production database is forbidden.");
		}

		validateQuery(uri.getRawQuery());
		return new JdbcTarget(transport, host, uri.getPort(), TARGET_DATABASE);
	}

	private static void validateQuery(String rawQuery) throws GuardException
	{
		if (rawQuery == null)
		{
			return;
		}
		if (rawQuery.isBlank())
		{
			throw new GuardException("Test JDBC URL query is empty.");
		}

		final Set<String> seen = new HashSet<>();
		for (String pair : rawQuery.split("&", -1))
		{
			final int separator = pair.indexOf('=');
			if ((separator <= 0) || (separator != pair.lastIndexOf('=')) || (separator == (pair.length() - 1)))
			{
				throw new GuardException("Test JDBC URL query contains an empty or malformed pair.");
			}

			final String key = decodeQueryComponent(pair.substring(0, separator));
			final String value = decodeQueryComponent(pair.substring(separator + 1));
			if (key.isBlank() || value.isBlank() || !key.equals(key.trim()) || !value.equals(value.trim()) || containsQuerySeparator(key) || containsQuerySeparator(value))
			{
				throw new GuardException("Test JDBC URL query contains an ambiguous key or value.");
			}
			if (!seen.add(key.toLowerCase(Locale.ROOT)))
			{
				throw new GuardException("Test JDBC URL query contains a duplicate property.");
			}
			final String expectedValue = ALLOWED_QUERY.get(key);
			if ((expectedValue == null) || !expectedValue.equals(value))
			{
				throw new GuardException("Test JDBC URL query contains a non-canonical property.");
			}
		}
	}

	private static String decodeQueryComponent(String value) throws GuardException
	{
		try
		{
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException e)
		{
			throw new GuardException("Test JDBC URL query encoding is invalid.", e);
		}
	}

	private static boolean containsQuerySeparator(String value)
	{
		for (int index = 0; index < value.length(); index++)
		{
			final char character = value.charAt(index);
			if ((character == '&') || (character == '=') || (character == '?') || (character == '#') || (character == ';') || Character.isISOControl(character))
			{
				return true;
			}
		}
		return false;
	}

	private static String require(Properties properties, String key) throws GuardException
	{
		final String value = properties.getProperty(key);
		if ((value == null) || value.isBlank())
		{
			throw new GuardException("Required test database setting is missing: " + key);
		}
		return value.trim();
	}

	private static void requireFalse(Properties properties, String key) throws GuardException
	{
		final String value = require(properties, key);
		if (!"false".equalsIgnoreCase(value))
		{
			throw new GuardException("Test database setting must be false: " + key);
		}
	}

	public static final class ValidatedSettings
	{
		private final Path _configFile;
		private final String _driver;
		private final String _url;
		private final String _login;
		private final String _password;
		private final int _maximumConnections;

		private ValidatedSettings(Path configFile, String driver, String url, String login, String password, int maximumConnections)
		{
			_configFile = configFile;
			_driver = driver;
			_url = url;
			_login = login;
			_password = password;
			_maximumConnections = maximumConnections;
		}

		public Path configFile()
		{
			return _configFile;
		}

		public String driver()
		{
			return _driver;
		}

		public String url()
		{
			return _url;
		}

		public String login()
		{
			return _login;
		}

		public String password()
		{
			return _password;
		}

		public int maximumConnections()
		{
			return _maximumConnections;
		}

		@Override
		public String toString()
		{
			return "ValidatedSettings[configFile=" + _configFile + ", driver=" + _driver + ", url=" + _url + ", login=" + _login + ", password=<redacted>, maximumConnections=" + _maximumConnections + "]";
		}
	}

	public record JdbcTarget(String transport, String host, int port, String database)
	{
	}

	public static class GuardException extends PhantomTestConfigurationException
	{
		private static final long serialVersionUID = 1L;

		public GuardException(String message)
		{
			super(message);
		}

		public GuardException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}
}
