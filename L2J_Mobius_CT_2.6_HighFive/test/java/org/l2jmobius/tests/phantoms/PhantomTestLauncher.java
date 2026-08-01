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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.PhantomServerShutdownHandoffSuite;
import org.l2jmobius.gameserver.phantoms.PhantomActivitySchedulerPerformanceSuite;
import org.l2jmobius.gameserver.phantoms.PhantomActivitySchedulerSuite;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationPerformanceSuite;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.GuardException;

public final class PhantomTestLauncher
{
	public static final int EXIT_SUCCESS = 0;
	public static final int EXIT_TEST_FAILURE = 1;
	public static final int EXIT_CONFIGURATION_REJECTED = 2;
	public static final int EXIT_INTERNAL_ERROR = 3;
	private static final Pattern NAMED_PASSWORD = Pattern.compile("(?i)(\\bpassword\\s*[=:]\\s*)([^\\s,;&]+)");
	private static final Pattern JDBC_USER_INFO = Pattern.compile("(jdbc:[a-z][a-z0-9+.-]*://)[^/@\\s]+@");
	private static final Pattern JDBC_QUERY_SECRET = Pattern.compile("(?i)([?&](?:user|password(?:[123])?)=)([^&#\\s]*)");
	private static final Pattern IDENTIFIED_BY_SINGLE_QUOTE = Pattern.compile("(?i)(\\bIDENTIFIED\\s+BY\\s+')([^']*)'");
	private static final Pattern IDENTIFIED_BY_DOUBLE_QUOTE = Pattern.compile("(?i)(\\bIDENTIFIED\\s+BY\\s+\")([^\"]*)\"");

	private PhantomTestLauncher()
	{
	}

	public static void main(String[] args)
	{
		System.exit(run(args));
	}

	static int run(String[] args)
	{
		if (args.length != 2)
		{
			System.err.println("Usage: PhantomTestLauncher <mode> <seed>");
			return EXIT_INTERNAL_ERROR;
		}

		final String mode = args[0];
		final long seed;
		try
		{
			seed = Long.parseLong(args[1]);
		}
		catch (NumberFormatException e)
		{
			System.err.println("Invalid deterministic seed.");
			return EXIT_CONFIGURATION_REJECTED;
		}

		final Path moduleRoot = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
		final Path reportsDirectory = Path.of(System.getProperty("phantom.test.reports", "../build/phantom-test/reports")).toAbsolutePath().normalize();
		final PhantomTestContext context = new PhantomTestContext(seed, moduleRoot, reportsDirectory);
		if ("guard-negative".equals(mode))
		{
			return runGuardNegative(context);
		}
		if ("schema-freshness-negative".equals(mode))
		{
			return runSchemaFreshnessNegative(context);
		}

		final PhantomTestSuite suite = suite(mode);
		if (suite == null)
		{
			System.err.println("Unknown Phantom test mode: " + mode);
			return EXIT_CONFIGURATION_REJECTED;
		}

		return runSuite(mode, suite, context);
	}

	static int runSuite(String mode, PhantomTestSuite suite, PhantomTestContext context)
	{
		final PhantomTestRegistry registry = new PhantomTestRegistry(suite.id());
		try
		{
			suite.register(registry);
		}
		catch (Throwable throwable)
		{
			System.err.println("Test registration failed: " + sanitize(throwable.getMessage()));
			return EXIT_INTERNAL_ERROR;
		}

		final List<PhantomTestRegistry.RegisteredTest> tests = registry.orderedTests();
		if (tests.isEmpty())
		{
			System.err.println("No tests registered for suite " + suite.id() + ".");
			return EXIT_INTERNAL_ERROR;
		}

		final List<PhantomTestResult> results = new ArrayList<>();
		int exitCode = EXIT_SUCCESS;
		boolean lifecycleStarted = false;
		try
		{
			lifecycleStarted = true;
			suite.beforeAll(context);
			for (PhantomTestRegistry.RegisteredTest test : tests)
			{
				final long start = System.nanoTime();
				try
				{
					test.testCase().run(context);
					results.add(PhantomTestResult.passed(test.identity(), System.nanoTime() - start));
				}
				catch (Throwable throwable)
				{
					results.add(PhantomTestResult.failed(test.identity(), System.nanoTime() - start, throwable));
					exitCode = Math.max(exitCode, (throwable instanceof PhantomTestConfigurationException) ? EXIT_CONFIGURATION_REJECTED : EXIT_TEST_FAILURE);
				}
			}
		}
		catch (Throwable throwable)
		{
			results.add(PhantomTestResult.failed(suite.id() + ".before-all", 0, throwable));
			exitCode = Math.max(exitCode, exitCodeFor(throwable));
		}
		finally
		{
			if (lifecycleStarted)
			{
				try
				{
					suite.afterAll(context);
				}
				catch (Throwable throwable)
				{
					results.add(PhantomTestResult.failed(suite.id() + ".after-all", 0, throwable));
					exitCode = Math.max(exitCode, exitCodeFor(throwable));
				}
			}
		}

		try
		{
			writeReports(mode, suite.id(), context, results);
		}
		catch (IOException e)
		{
			System.err.println("Test report write failed: " + sanitize(e.getMessage()));
			return EXIT_INTERNAL_ERROR;
		}

		printSummary(suite.id(), context.seed(), results);
		return exitCode;
	}

	private static PhantomTestSuite suite(String mode)
	{
		return switch (mode)
		{
			case "unit" -> new PhantomHarnessUnitSuite();
			case "negative" -> new PhantomNegativeControlSuite();
			case "db" -> new PhantomTestDatabaseIntegrationSuite();
			case "scenario" -> new PhantomScenarioSmokeSuite();
			case "performance" -> new PhantomPerformanceSmokeSuite();
			case "skeleton" -> new PhantomSkeletonSuite();
			case "headless-player" -> new PhantomHeadlessPlayerSuite();
			case "headless-player-performance" -> new PhantomHeadlessPlayerPerformanceSuite();
			case "profile-persistence" -> new PhantomProfilePersistenceSuite();
			case "production-materialization" -> new PhantomProductionMaterializationSuite();
			case "production-materialization-performance" -> new PhantomProductionMaterializationPerformanceSuite();
			case "server-shutdown-handoff" -> new PhantomServerShutdownHandoffSuite();
			case "activity-scheduler" -> new PhantomActivitySchedulerSuite();
			case "activity-scheduler-performance" -> new PhantomActivitySchedulerPerformanceSuite();
			case "decision-core" -> new PhantomDecisionCoreSuite();
			case "decision-persistence" -> new PhantomDecisionPersistenceSuite();
			case "decision-performance" -> new PhantomDecisionPerformanceSuite();
			case "navigation-core" -> new PhantomNavigationCoreSuite();
			case "navigation-performance" -> new PhantomNavigationPerformanceSuite();
			case "topology-core" -> new PhantomTopologyCoreSuite();
			case "topology-perception" -> new PhantomTopologyPerceptionSuite();
			case "topology-scheduler-signal-integration" -> new PhantomTopologySchedulerSignalIntegrationSuite();
			case "topology-signal-ledger" -> new PhantomTopologySignalLedgerSuite();
			case "topology-generation" -> new PhantomTopologyGenerationSuite();
			case "topology-corpus" -> new PhantomTopologyProductionCorpusSuite();
			case "topology-performance" -> new PhantomTopologyPerformanceSuite();
			case "knowledge-core" -> new PhantomGameKnowledgeCoreSuite();
			case "knowledge-query-truth" -> new PhantomGameKnowledgeQueryTruthSuite();
			case "knowledge-parity" -> new PhantomGameKnowledgeParitySuite();
			case "knowledge-content" -> new PhantomGameKnowledgeContentSuite();
			case "knowledge-performance" -> new PhantomGameKnowledgePerformanceSuite();
			case "combat-core" -> new PhantomCombatCoreSuite();
			case "combat-ownership" -> new PhantomCombatOwnershipSuite();
			case "combat-action-ownership" -> new PhantomCombatActionOwnershipSuite();
			case "combat-server-integration" -> new PhantomCombatServerIntegrationSuite();
			case "combat-performance" -> new PhantomCombatPerformanceSuite();
			case "progression-parity" -> new PhantomProgressionParitySuite();
			case "progression-catalog" -> new PhantomProgressionCatalogSuite();
			case "capability-runtime" -> new PhantomCapabilityRuntimeSuite();
			case "progression-operations" -> new PhantomProgressionOperationSuite();
			case "progression-server-integration" -> new PhantomProgressionServerIntegrationSuite();
			case "progression-durability" -> new PhantomProgressionDurabilitySuite();
			case "progression-performance" -> new PhantomProgressionPerformanceSuite();
			case "progression-extensibility" -> new PhantomProgressionExtensibilitySuite();
			case "progression-production-composition" -> new PhantomProgressionProductionCompositionSuite();
			case "commerce-catalog" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.CATALOG);
			case "commerce-supply" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.SUPPLY);
			case "commerce-quote" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.QUOTE);
			case "commerce-receipt" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.RECEIPT);
			case "commerce-decision" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.DECISION);
			case "commerce-server-integration" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.SERVER_INTEGRATION);
			case "commerce-hardening" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.HARDENING);
			case "commerce-performance" -> new PhantomCommerceSuite(PhantomCommerceSuite.Mode.PERFORMANCE);
			case "background-model" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.MODEL);
			case "background-transaction" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.TRANSACTION);
			case "background-lifecycle" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.LIFECYCLE);
			case "background-decision" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.DECISION);
			case "background-server-integration" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.SERVER_INTEGRATION);
			case "background-performance" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.PERFORMANCE);
			case "background-materialization-abort" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.MATERIALIZATION_ABORT);
			case "background-quiescence" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.QUIESCENCE);
			case "background-compact-inventory" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.COMPACT_INVENTORY);
			case "background-authoritative-shots" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.AUTHORITATIVE_SHOTS);
			case "background-production-audit" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.PRODUCTION_AUDIT);
			case "background-recovery-teleport" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.RECOVERY_TELEPORT);
			case "background-real-login" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.REAL_LOGIN);
			case "background-position-canonicalization" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.POSITION_CANONICALIZATION);
			case "background-production-loot-unblock" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.PRODUCTION_LOOT_UNBLOCK);
			case "acquisition-background-parity" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.ACQUISITION_PARITY);
			case "acquisition-atomic-restart" -> new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.ACQUISITION_ATOMIC_RESTART);
			case "population-catalog" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.CATALOG);
			case "population-schedule" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.SCHEDULE);
			case "population-creation" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.CREATION);
			case "population-reconciliation" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.RECONCILIATION);
			case "population-lifecycle" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.LIFECYCLE);
			case "population-server-integration" -> new PhantomPopulationSuite(PhantomPopulationSuite.Mode.SERVER_INTEGRATION);
			case "population-performance" -> new PhantomPopulationPerformanceSuite();
			case "party-canonical-invitation" -> new PhantomPartySuite(PhantomPartySuite.Mode.CANONICAL_INVITATION);
			case "party-state-recovery" -> new PhantomPartySuite(PhantomPartySuite.Mode.STATE_RECOVERY);
			case "party-role-vacancy" -> new PhantomPartySuite(PhantomPartySuite.Mode.ROLE_VACANCY);
			case "party-semantic-acts" -> new PhantomPartySuite(PhantomPartySuite.Mode.SEMANTIC_ACTS);
			case "party-route" -> new PhantomPartySuite(PhantomPartySuite.Mode.ROUTE);
			case "party-tactics" -> new PhantomPartySuite(PhantomPartySuite.Mode.TACTICS);
			case "party-lifecycle" -> new PhantomPartySuite(PhantomPartySuite.Mode.LIFECYCLE);
			case "party-server-integration" -> new PhantomPartyServerIntegrationSuite();
			case "party-performance" -> new PhantomPartySuite(PhantomPartySuite.Mode.PERFORMANCE);
			case "social-catalog" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.CATALOG);
			case "social-codec" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.CODEC);
			case "social-personality" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.PERSONALITY);
			case "social-decay" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.DECAY);
			case "social-events" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.EVENTS);
			case "social-modifiers" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.MODIFIERS);
			case "social-party-integration" -> new PhantomSocialPartyIntegrationSuite();
			case "social-lifecycle-performance" -> new PhantomSocialSuite(PhantomSocialSuite.Mode.LIFECYCLE_PERFORMANCE);
			case "semantic-pack" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.PACK);
			case "semantic-normalization" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.NORMALIZATION);
			case "semantic-intents" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.INTENTS);
			case "semantic-grounding" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.GROUNDING);
			case "semantic-context" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.CONTEXT);
			case "semantic-corpus" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.CORPUS);
			case "semantic-lifecycle-performance" -> new PhantomSemanticSuite(PhantomSemanticSuite.Mode.LIFECYCLE_PERFORMANCE);
			case "social-activation" -> new PhantomActivationGateSuite(PhantomActivationGateSuite.Mode.SOCIAL);
			case "semantic-activation" -> new PhantomActivationGateSuite(PhantomActivationGateSuite.Mode.SEMANTIC);
			case "chat-observation" -> new PhantomChatObservationSuite();
			case "conversation-catalog-codec" -> new PhantomConversationSuite(PhantomConversationSuite.Mode.CATALOG_CODEC);
			case "conversation-understanding" -> new PhantomConversationSuite(PhantomConversationSuite.Mode.UNDERSTANDING);
			case "conversation-social-style" -> new PhantomConversationSuite(PhantomConversationSuite.Mode.SOCIAL_STYLE);
			case "conversation-chat-integration" -> new PhantomConversationIntegrationSuite(PhantomConversationIntegrationSuite.Mode.CHAT_INTEGRATION);
			case "conversation-lifecycle-performance" -> new PhantomConversationIntegrationSuite(PhantomConversationIntegrationSuite.Mode.LIFECYCLE_PERFORMANCE);
			case "conversation-managed-ingress" -> new PhantomConversationIntegrationSuite(PhantomConversationIntegrationSuite.Mode.MANAGED_INGRESS);
			case "conversation-execution-catalog-codec" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.CATALOG_CODEC);
			case "conversation-handoff-durability" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.HANDOFF_DURABILITY);
			case "conversation-query-execution" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.QUERY_EXECUTION);
			case "conversation-party-actions" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.PARTY_ACTIONS);
			case "conversation-outbound-chat" -> new PhantomConversationIntegrationSuite(PhantomConversationIntegrationSuite.Mode.OUTBOUND_CHAT);
			case "conversation-restart-idempotency" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.RESTART_IDEMPOTENCY);
			case "conversation-execution-lifecycle-performance" -> new PhantomConversationExecutionSuite(PhantomConversationExecutionSuite.Mode.LIFECYCLE_PERFORMANCE);
			case "acquisition-catalog-codec" -> new PhantomAcquisitionSuite(PhantomAcquisitionSuite.Mode.CATALOG_CODEC);
			case "acquisition-source-planner" -> new PhantomAcquisitionSuite(PhantomAcquisitionSuite.Mode.SOURCE_PLANNER);
			case "acquisition-recipe-planning" -> new PhantomAcquisitionSuite(PhantomAcquisitionSuite.Mode.RECIPE_PLANNING);
			case "acquisition-active-spoil" -> new PhantomCombatServerIntegrationSuite(PhantomCombatServerIntegrationSuite.Mode.ACQUISITION);
			case "acquisition-source-switching" -> new PhantomAcquisitionSuite(PhantomAcquisitionSuite.Mode.SOURCE_SWITCHING);
			case "acquisition-lifecycle-performance" -> new PhantomAcquisitionSuite(PhantomAcquisitionSuite.Mode.LIFECYCLE_PERFORMANCE);
			case "lifecycle-control" -> new PhantomLifecycleFailureControlSuite();
			default -> null;
		};
	}

	private static int runGuardNegative(PhantomTestContext context)
	{
		final Path directory = context.moduleRoot().resolve(".phantom-local/guard-negative");
		final Path config = directory.resolve("Database.production.ini");
		final Path marker = Path.of(System.getProperty("java.io.tmpdir"), "phantom-sentinel-" + ProcessHandle.current().pid() + ".marker");
		System.setProperty("phantom.sentinel.marker", marker.toString());
		try
		{
			Files.createDirectories(directory);
			Files.deleteIfExists(marker);
			final String content = """
				Driver = org.l2jmobius.tests.phantoms.SentinelJdbcDriver
				URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5
				Login = l2j_phantom_test
				Password = sentinel-not-used
				MaximumDatabaseConnections = 4
				TestDatabaseConnections = false
				BackupDatabase = false
				""";
			Files.writeString(config, content, StandardCharsets.UTF_8);

			try
			{
				PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
				System.err.println("Production database guard unexpectedly accepted the config.");
				return EXIT_INTERNAL_ERROR;
			}
			catch (GuardException expected)
			{
				if (Files.exists(marker))
				{
					System.err.println("Sentinel JDBC driver was touched before guard rejection.");
					return EXIT_INTERNAL_ERROR;
				}

				context.record("guard.rejectedDatabase", PhantomTestDatabaseGuard.PRODUCTION_DATABASE);
				context.record("guard.driverLoads", 0);
				context.record("guard.connectionAttempts", 0);
				final List<PhantomTestResult> results = List.of(PhantomTestResult.passed("db-guard-negative.production-rejected-before-driver", 0));
				writeReports("guard-negative", "db-guard-negative", context, results);
				printSummary("db-guard-negative", context.seed(), results);
				System.out.println("Expected guard rejection exit=2; driverLoads=0; connectionAttempts=0.");
				return EXIT_CONFIGURATION_REJECTED;
			}
		}
		catch (IOException e)
		{
			System.err.println("Guard negative control failed: " + sanitize(e.getMessage()));
			return EXIT_INTERNAL_ERROR;
		}
		finally
		{
			try
			{
				Files.deleteIfExists(config);
				Files.deleteIfExists(directory);
				Files.deleteIfExists(marker);
			}
			catch (IOException e)
			{
				System.err.println("Guard negative cleanup failed: " + sanitize(e.getMessage()));
			}
			System.clearProperty("phantom.sentinel.marker");
		}
	}

	private static int runSchemaFreshnessNegative(PhantomTestContext context)
	{
		final Path directory = context.moduleRoot().resolve(".phantom-local/freshness-negative-" + ProcessHandle.current().pid());
		final Path config = directory.resolve("Database.stale.ini");
		final Path manifest = directory.resolve("schema-manifest.properties");
		final Path marker = Path.of(System.getProperty("java.io.tmpdir"), "phantom-freshness-sentinel-" + ProcessHandle.current().pid() + ".marker");
		System.setProperty("phantom.sentinel.marker", marker.toString());
		try
		{
			Files.createDirectories(directory);
			Files.deleteIfExists(marker);
			final String content = """
				Driver = org.l2jmobius.tests.phantoms.SentinelJdbcDriver
				URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test?useSSL=false
				Login = l2j_phantom_test
				Password = sentinel-not-used
				MaximumDatabaseConnections = 4
				TestDatabaseConnections = false
				BackupDatabase = false
				""";
			Files.writeString(config, content, StandardCharsets.UTF_8);
			final var current = PhantomTestSchemaManifest.current(context.moduleRoot());
			final String staleHash = current.aggregateSha256().charAt(0) == 'A' ? "B" + current.aggregateSha256().substring(1) : "A" + current.aggregateSha256().substring(1);
			PhantomTestSchemaManifest.writeAtomic(manifest, new PhantomTestSchemaManifest.Snapshot(current.schemaVersion(), current.scriptCount(), current.statementCount(), staleHash));

			try
			{
				PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), config, manifest);
				System.err.println("Stale schema manifest unexpectedly reached database initialization.");
				return EXIT_INTERNAL_ERROR;
			}
			catch (PhantomTestConfigurationException expected)
			{
				if (!String.valueOf(expected.getMessage()).contains("schema manifest is stale"))
				{
					System.err.println("Schema freshness control received an unexpected configuration rejection.");
					return EXIT_INTERNAL_ERROR;
				}
				if (Files.exists(marker))
				{
					System.err.println("Sentinel JDBC driver was touched before stale manifest rejection.");
					return EXIT_INTERNAL_ERROR;
				}
				context.record("freshness.driverLoads", 0);
				context.record("freshness.connectionAttempts", 0);
				context.record("freshness.sentinelMarker", "absent");
				final List<PhantomTestResult> results = List.of(PhantomTestResult.passed("schema-freshness-negative.stale-rejected-before-hikari", 0));
				writeReports("schema-freshness-negative", "schema-freshness-negative", context, results);
				printSummary("schema-freshness-negative", context.seed(), results);
				System.out.println("Expected stale manifest rejection exit=2; sentinel marker absent; driverLoads=0; connectionAttempts=0.");
				return EXIT_CONFIGURATION_REJECTED;
			}
		}
		catch (Exception e)
		{
			System.err.println("Schema freshness negative control failed: " + sanitize(e.getMessage()));
			return EXIT_INTERNAL_ERROR;
		}
		finally
		{
			try
			{
				deleteTree(directory);
				Files.deleteIfExists(marker);
			}
			catch (IOException e)
			{
				System.err.println("Schema freshness negative cleanup failed: " + sanitize(e.getMessage()));
			}
			System.clearProperty("phantom.sentinel.marker");
		}
	}

	static int exitCodeFor(Throwable throwable)
	{
		if (throwable instanceof PhantomTestConfigurationException)
		{
			return EXIT_CONFIGURATION_REJECTED;
		}
		if (throwable instanceof AssertionError)
		{
			return EXIT_TEST_FAILURE;
		}
		return EXIT_INTERNAL_ERROR;
	}

	static String sanitize(String message)
	{
		if (message == null)
		{
			return "";
		}
		String sanitized = NAMED_PASSWORD.matcher(message).replaceAll("$1<redacted>");
		sanitized = JDBC_USER_INFO.matcher(sanitized).replaceAll("$1<redacted>@");
		sanitized = JDBC_QUERY_SECRET.matcher(sanitized).replaceAll("$1<redacted>");
		sanitized = IDENTIFIED_BY_SINGLE_QUOTE.matcher(sanitized).replaceAll("$1<redacted>'");
		sanitized = IDENTIFIED_BY_DOUBLE_QUOTE.matcher(sanitized).replaceAll("$1<redacted>\"");
		return sanitized.replace('\r', ' ').replace('\n', ' ');
	}

	private static void deleteTree(Path path) throws IOException
	{
		if (!Files.exists(path))
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

	static String escapeXml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
	}

	private static void writeReports(String mode, String suiteId, PhantomTestContext context, List<PhantomTestResult> results) throws IOException
	{
		Files.createDirectories(context.reportsDirectory());
		int passed = 0;
		for (PhantomTestResult result : results)
		{
			if (result.passed())
			{
				passed++;
			}
		}
		final int failed = results.size() - passed;

		final StringBuilder text = new StringBuilder();
		text.append("suite=").append(suiteId).append(System.lineSeparator());
		text.append("seed=").append(context.seed()).append(System.lineSeparator());
		text.append("total=").append(results.size()).append(System.lineSeparator());
		text.append("passed=").append(passed).append(System.lineSeparator());
		text.append("failed=").append(failed).append(System.lineSeparator());
		for (var measurement : context.measurements().entrySet())
		{
			text.append("measurement.").append(measurement.getKey()).append('=').append(measurement.getValue()).append(System.lineSeparator());
		}
		for (PhantomTestResult result : results)
		{
			text.append(result.passed() ? "PASS " : "FAIL ").append(result.name()).append(" elapsedNanos=").append(result.elapsedNanos());
			if (!result.passed())
			{
				text.append(" type=").append(result.failureType()).append(" message=").append(result.failureMessage()).append(" seed=").append(context.seed());
			}
			text.append(System.lineSeparator());
		}
		Files.writeString(context.reportsDirectory().resolve(mode + ".txt"), text, StandardCharsets.UTF_8);

		final StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<testsuite name=\"").append(escapeXml(suiteId)).append("\" seed=\"").append(context.seed()).append("\" tests=\"").append(results.size()).append("\" passed=\"").append(passed).append("\" failures=\"").append(failed).append("\">\n");
		xml.append("  <properties>\n");
		for (var measurement : context.measurements().entrySet())
		{
			xml.append("    <property name=\"").append(escapeXml(measurement.getKey())).append("\" value=\"").append(escapeXml(measurement.getValue())).append("\"/>\n");
		}
		xml.append("  </properties>\n");
		for (PhantomTestResult result : results)
		{
			xml.append("  <testcase name=\"").append(escapeXml(result.name())).append("\" elapsedNanos=\"").append(result.elapsedNanos()).append("\">");
			if (!result.passed())
			{
				xml.append("<failure type=\"").append(escapeXml(result.failureType())).append("\" message=\"").append(escapeXml(result.failureMessage())).append("\" seed=\"").append(context.seed()).append("\"/>");
			}
			xml.append("</testcase>\n");
		}
		xml.append("</testsuite>\n");
		Files.writeString(context.reportsDirectory().resolve(mode + ".xml"), xml, StandardCharsets.UTF_8);
	}

	private static void printSummary(String suiteId, long seed, List<PhantomTestResult> results)
	{
		int failed = 0;
		for (PhantomTestResult result : results)
		{
			final String status = result.passed() ? "PASS" : "FAIL";
			if (!result.passed())
			{
				failed++;
			}
			System.out.println("[" + status + "] " + result.name() + (result.passed() ? "" : " - " + result.failureType() + ": " + result.failureMessage() + " seed=" + seed));
		}
		System.out.println(String.format(Locale.ROOT, "SUMMARY: suite=%s seed=%d total=%d passed=%d failed=%d", suiteId, seed, results.size(), results.size() - failed, failed));
	}
}
