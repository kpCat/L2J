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
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.GuardException;

public final class PhantomHarnessUnitSuite implements PhantomTestSuite
{
	private Path _unitDirectory;

	@Override
	public String id()
	{
		return "harness-unit";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_unitDirectory = context.moduleRoot().resolve(".phantom-local/unit-" + ProcessHandle.current().pid());
		Files.createDirectories(_unitDirectory);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		deleteTree(_unitDirectory);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("assertion-throws", _ -> PhantomAssertions.assertThrows(AssertionError.class, () -> PhantomAssertions.assertTrue(false, "expected"), "Assertion helper must fail."));
		registry.add("config-backup-enabled", context -> assertConfigRejected(context, "backup-enabled", property("BackupDatabase", "true")));
		registry.add("config-connection-fanout-enabled", context -> assertConfigRejected(context, "fanout-enabled", property("TestDatabaseConnections", "true")));
		registry.add("config-max-pool", context -> assertConfigRejected(context, "max-pool", property("MaximumDatabaseConnections", "5")));
		registry.add("config-missing-file", context -> PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validate(context.moduleRoot(), _unitDirectory.resolve("missing.ini")), "Missing config must be rejected."));
		registry.add("config-missing-login", context -> assertConfigRejected(context, "missing-login", property("Login", null)));
		registry.add("config-missing-url", context -> assertConfigRejected(context, "missing-url", property("URL", null)));
		registry.add("config-outside-local", context ->
		{
			final Path outside = Files.createTempFile("phantom-test-outside-", ".ini");
			try
			{
				writeProperties(outside, validProperties());
				PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validate(context.moduleRoot(), outside), "Config outside .phantom-local must be rejected.");
			}
			finally
			{
				Files.deleteIfExists(outside);
			}
		});
		registry.add("config-password-redacted", context ->
		{
			final String secret = "task002-secret-that-must-not-leak";
			final Properties properties = validProperties();
			properties.setProperty("Password", secret);
			properties.setProperty("Login", "wrong-user");
			final Path config = writeProperties(_unitDirectory.resolve("redaction.ini"), properties);
			final GuardException failure = PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validate(context.moduleRoot(), config), "Wrong user must be rejected.");
			PhantomAssertions.assertFalse(String.valueOf(failure.getMessage()).contains(secret), "Guard error leaked password.");
		});
		registry.add("config-production-path", context -> PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validate(context.moduleRoot(), context.moduleRoot().resolve("dist/game/config/Database.ini")), "Production config path must be rejected."));
		registry.add("config-valid", context ->
		{
			final Path config = writeProperties(_unitDirectory.resolve("valid.ini"), validProperties());
			final var settings = PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, settings.login(), "Dedicated user mismatch.");
			PhantomAssertions.assertEquals(4, settings.maximumConnections(), "Pool limit mismatch.");
			PhantomAssertions.assertFalse(settings.toString().contains(settings.password()), "Validated settings string leaked password.");
		});
		registry.add("config-wrong-user", context -> assertConfigRejected(context, "wrong-user", property("Login", "root")));
		registry.add("exit-code-contract", _ ->
		{
			PhantomAssertions.assertEquals(1, PhantomTestLauncher.exitCodeFor(new AssertionError("failure")), "Assertion exit code mismatch.");
			PhantomAssertions.assertEquals(2, PhantomTestLauncher.exitCodeFor(new PhantomTestConfigurationException("config")), "Configuration exit code mismatch.");
			PhantomAssertions.assertEquals(3, PhantomTestLauncher.exitCodeFor(new IllegalStateException("internal")), "Internal exit code mismatch.");
		});
		registry.add("registry-explicit-non-empty", _ ->
		{
			final PhantomTestRegistry local = new PhantomTestRegistry("local");
			local.add("one", _context -> { });
			PhantomAssertions.assertEquals(1, local.orderedTests().size(), "Explicit registry must not be empty.");
		});
		registry.add("registry-stable-ordinal", _ ->
		{
			final PhantomTestRegistry local = new PhantomTestRegistry("local");
			local.add("zeta", _context -> { });
			local.add("Alpha", _context -> { });
			local.add("beta", _context -> { });
			PhantomAssertions.assertEquals(List.of("local.Alpha", "local.beta", "local.zeta"), local.orderedTests().stream().map(PhantomTestRegistry.RegisteredTest::identity).toList(), "Test order must be ordinal.");
		});
		registry.add("schema-name-prefix-is-not-production", _ ->
		{
			final String testGrant = "GRANT ALL PRIVILEGES ON `l2jmobiush5_phantom_test`.* TO `l2j_phantom_test`@`localhost`";
			final String productionGrant = "GRANT ALL PRIVILEGES ON `l2jmobiush5`.* TO `l2j_phantom_test`@`localhost`";
			final java.util.regex.Pattern exactProduction = java.util.regex.Pattern.compile("(?i)\\bON\\s+`?l2jmobiush5`?\\.\\*");
			PhantomAssertions.assertFalse(exactProduction.matcher(testGrant).find(), "Test schema prefix was mistaken for production.");
			PhantomAssertions.assertTrue(exactProduction.matcher(productionGrant).find(), "Exact production schema grant was not detected.");
		});
		registry.add("scenario-checksum", context -> PhantomAssertions.assertEquals(PhantomScenarioSmokeSuite.EXPECTED_CHECKSUM, PhantomScenarioSmokeSuite.checksum(context.seed(), 64, 1000), "Scenario checksum mismatch."));
		registry.add("secret-redaction", _ ->
		{
			final String sanitized = PhantomTestLauncher.sanitize("Password=super-secret jdbc:mysql://user:secret@127.0.0.1:3308/db");
			PhantomAssertions.assertFalse(sanitized.contains("super-secret"), "Named password was not redacted.");
			PhantomAssertions.assertFalse(sanitized.contains("user:secret@"), "JDBC credentials were not redacted.");
		});
		registry.add("seed-different", context ->
		{
			final SplittableRandom first = new SplittableRandom(context.seed());
			final SplittableRandom second = new SplittableRandom(context.seed() + 1);
			PhantomAssertions.assertFalse(first.nextLong() == second.nextLong(), "Different seeds unexpectedly matched.");
		});
		registry.add("seed-first-ten", context ->
		{
			final int[] expected = { 841, 9, 973, 990, 258, 913, 774, 550, 98, 870 };
			final SplittableRandom random = new SplittableRandom(context.seed());
			for (int value : expected)
			{
				PhantomAssertions.assertEquals(value, random.nextInt(1000), "Scenario diagnostic sequence mismatch.");
			}
		});
		registry.add("seed-repeatable", context ->
		{
			final SplittableRandom first = new SplittableRandom(context.seed());
			final SplittableRandom second = new SplittableRandom(context.seed());
			for (int i = 0; i < 64; i++)
			{
				PhantomAssertions.assertEquals(first.nextLong(), second.nextLong(), "Seed sequence diverged.");
			}
		});
		registry.add("sql-comments-quotes-backticks", _ ->
		{
			final String sql = "# header\nCREATE TABLE `sample` (`value` VARCHAR(20)); -- inline\nINSERT INTO `sample` VALUES ('a;b');\n/* ordinary */ INSERT INTO `sample` VALUES (\"c;d\");";
			final List<String> statements = StrictSqlScriptRunner.splitStatements(Path.of("syntax.sql"), sql);
			PhantomAssertions.assertEquals(3, statements.size(), "SQL splitter statement count mismatch.");
			PhantomAssertions.assertTrue(statements.get(1).contains("'a;b'"), "Quoted semicolon was split.");
		});
		registry.add("sql-failure-stops", _ ->
		{
			final var script = new StrictSqlScriptRunner.ScriptInfo(Path.of("failure.sql"), "failure.sql", "HASH", List.of("ONE", "TWO", "THREE"));
			final AtomicInteger attempts = new AtomicInteger();
			final StrictSqlScriptRunner.SqlScriptException failure = PhantomAssertions.assertThrows(StrictSqlScriptRunner.SqlScriptException.class, () -> StrictSqlScriptRunner.executeStatements(script, sql ->
			{
				if (attempts.incrementAndGet() == 2)
				{
					throw new SQLException("intentional");
				}
			}), "SQL error must propagate.");
			PhantomAssertions.assertEquals(2, attempts.get(), "SQL runner continued after failure.");
			PhantomAssertions.assertTrue(failure.getMessage().contains("statement 2"), "SQL error lacks statement index.");
		});
		registry.add("sql-stable-file-order", context ->
		{
			final Path root = _unitDirectory.resolve("sql-order");
			Files.createDirectories(root);
			Files.writeString(root.resolve("zeta.sql"), "SELECT 2;", StandardCharsets.UTF_8);
			Files.writeString(root.resolve("Alpha.sql"), "SELECT 1;", StandardCharsets.UTF_8);
			final List<StrictSqlScriptRunner.ScriptInfo> scripts = StrictSqlScriptRunner.inventory(context.moduleRoot(), List.of(root));
			PhantomAssertions.assertEquals("Alpha.sql", scripts.get(0).path().getFileName().toString(), "SQL filename order mismatch.");
			PhantomAssertions.assertEquals("zeta.sql", scripts.get(1).path().getFileName().toString(), "SQL filename order mismatch.");
		});
		registry.add("sql-unsupported-syntax", _ -> PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> StrictSqlScriptRunner.splitStatements(Path.of("unsupported.sql"), "DELIMITER $$\nSELECT 1$$"), "Unsupported SQL syntax must fail."));
		registry.add("url-case", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/L2jmobiush5_phantom_test"));
		registry.add("url-credentials", _ -> assertUrlRejected("jdbc:mysql://user:password@127.0.0.1:3308/l2jmobiush5_phantom_test"));
		registry.add("url-empty", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/"));
		registry.add("url-encoded-production", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/%6c2jmobiush5"));
		registry.add("url-extra-path", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test/extra"));
		registry.add("url-fragment", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test#fragment"));
		registry.add("url-localhost-valid", _ -> PhantomAssertions.assertEquals("localhost", PhantomTestDatabaseGuard.validateJdbcUrl("jdbc:mysql://localhost:3308/l2jmobiush5_phantom_test").host(), "localhost URL rejected."));
		registry.add("url-missing-port", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1/l2jmobiush5_phantom_test"));
		registry.add("url-multihost", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308,localhost:3308/l2jmobiush5_phantom_test"));
		registry.add("url-mysql-valid", _ -> PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, PhantomTestDatabaseGuard.validateJdbcUrl("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test?useSSL=false").database(), "Valid URL rejected."));
		registry.add("url-production", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/l2jmobiush5"));
		registry.add("url-remote", _ -> assertUrlRejected("jdbc:mysql://192.0.2.1:3308/l2jmobiush5_phantom_test"));
		registry.add("url-trailing", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test/"));
		registry.add("url-unknown", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3308/other"));
		registry.add("url-wrong-port", _ -> assertUrlRejected("jdbc:mysql://127.0.0.1:3306/l2jmobiush5_phantom_test"));
		registry.add("xml-escaping", _ -> PhantomAssertions.assertEquals("&lt;&amp;&gt;&quot;&apos;", PhantomTestLauncher.escapeXml("<&>\"'"), "XML escaping mismatch."));
	}

	private void assertConfigRejected(PhantomTestContext context, String name, PropertyOverride override) throws IOException
	{
		final Properties properties = validProperties();
		if (override.value() == null)
		{
			properties.remove(override.key());
		}
		else
		{
			properties.setProperty(override.key(), override.value());
		}
		final Path config = writeProperties(_unitDirectory.resolve(name + ".ini"), properties);
		PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validate(context.moduleRoot(), config), "Unsafe config must be rejected.");
	}

	private static void assertUrlRejected(String url)
	{
		PhantomAssertions.assertThrows(GuardException.class, () -> PhantomTestDatabaseGuard.validateJdbcUrl(url), "Unsafe URL must be rejected.");
	}

	private static Properties validProperties()
	{
		final Properties properties = new Properties();
		properties.setProperty("Driver", "com.mysql.cj.jdbc.Driver");
		properties.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test?useSSL=false");
		properties.setProperty("Login", PhantomTestDatabaseGuard.TARGET_USER);
		properties.setProperty("Password", "unit-test-only");
		properties.setProperty("MaximumDatabaseConnections", "4");
		properties.setProperty("TestDatabaseConnections", "false");
		properties.setProperty("BackupDatabase", "false");
		return properties;
	}

	private static Path writeProperties(Path path, Properties properties) throws IOException
	{
		final StringBuilder content = new StringBuilder();
		for (String key : List.of("Driver", "URL", "Login", "Password", "MaximumDatabaseConnections", "TestDatabaseConnections", "BackupDatabase"))
		{
			if (properties.containsKey(key))
			{
				content.append(key).append(" = ").append(properties.getProperty(key)).append(System.lineSeparator());
			}
		}
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private static PropertyOverride property(String key, String value)
	{
		return new PropertyOverride(key, value);
	}

	private static void deleteTree(Path path) throws IOException
	{
		if ((path == null) || !Files.exists(path))
		{
			return;
		}

		try (var stream = Files.walk(path))
		{
			for (Path entry : stream.sorted((left, right) -> right.compareTo(left)).toList())
			{
				Files.deleteIfExists(entry);
			}
		}
	}

	private record PropertyOverride(String key, String value)
	{
	}
}
