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
package org.l2jmobius.gameserver.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestConfigurationException;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.JdbcTarget;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomReleaseBaselineGoal030Checkpoint1Suite implements PhantomTestSuite
{
	private static final long SEED = 30003001L;
	private static final String MATRIX_RELATIVE_PATH = "test/resources/phantoms/release/goal030-release-coverage.tsv";
	private static final String HEADER = "domain_id\tgoal_lineage\tproduction_owner_paths\tant_targets\tevidence_type\tcp1_status\tgoal030_checkpoint";
	private static final Set<String> REQUIRED_DOMAINS = Set.of(
		"fresh-bootstrap",
		"population",
		"progression",
		"activity-materialization",
		"topology-navigation-knowledge",
		"combat",
		"farming",
		"acquisition-spoil",
		"craft-trade-commerce-economy",
		"party",
		"rift",
		"pvp",
		"raid",
		"conversation-semantic-social",
		"clans-alliances-reputation-wars",
		"restart-failure-recovery",
		"operator-observability-replay",
		"scale-soak-overload",
		"disabled-regression",
		"rollback-release-control");
	private static final Map<String, String> FIXED_STATUSES = Map.ofEntries(
		Map.entry("fresh-bootstrap", "COVERED_CP1"),
		Map.entry("population", "COVERED_CP1"),
		Map.entry("progression", "COVERED_CP1"),
		Map.entry("topology-navigation-knowledge", "COVERED_PRIOR"),
		Map.entry("combat", "COVERED_PRIOR"),
		Map.entry("farming", "COVERED_PRIOR"),
		Map.entry("acquisition-spoil", "COVERED_PRIOR"),
		Map.entry("craft-trade-commerce-economy", "COVERED_PRIOR"),
		Map.entry("party", "COVERED_PRIOR"),
		Map.entry("rift", "COVERED_PRIOR"),
		Map.entry("pvp", "COVERED_PRIOR"),
		Map.entry("raid", "COVERED_PRIOR"),
		Map.entry("conversation-semantic-social", "COVERED_PRIOR"),
		Map.entry("clans-alliances-reputation-wars", "COVERED_PRIOR"),
		Map.entry("operator-observability-replay", "COVERED_CP1"),
		Map.entry("scale-soak-overload", "COVERED_CP1"),
		Map.entry("disabled-regression", "COVERED_CP1"));
	private static final Map<String, ReleaseProgression> RELEASE_PROGRESSIONS = Map.of(
		"activity-materialization", new ReleaseProgression("CP2", "COVERED_CP2", "phantom-cross-domain-autonomous-alpha-goal030cp2-test"),
		"restart-failure-recovery", new ReleaseProgression("CP3", "COVERED_CP3", "phantom-restart-failure-recovery-goal030cp3-test"),
		"rollback-release-control", new ReleaseProgression("CP3", "COVERED_CP3", "phantom-release-decision-rollback-goal030cp3-test"));
	private static final Set<String> STATUSES = Set.of("COVERED_PRIOR", "COVERED_CP1", "COVERED_CP2", "COVERED_CP3", "PENDING_GOAL030");
	private static final Pattern GOAL = Pattern.compile("Goal(\\d{3})");
	private static final Pattern BUILD_TARGET = Pattern.compile("<target\\s+name=\"([^\"]+)\"");

	private Path _moduleRoot;
	private Path _matrixPath;
	private String _buildXml;
	private long _profilesBefore;
	private long _componentsBefore;

	@Override
	public String id()
	{
		return "release-baseline-goal030cp1";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP1 requires the exact deterministic seed.");
		_moduleRoot = context.moduleRoot();
		final Path expectedWorkingDirectory = _moduleRoot.resolve("dist/game").normalize();
		PhantomAssertions.assertEquals(expectedWorkingDirectory, Path.of("").toAbsolutePath().normalize(), "Goal030 CP1 JVM must run from dist/game.");

		ConfigLoader.init();
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(_moduleRoot, Path.of(configProperty));
		final JdbcTarget target = PhantomTestDatabaseGuard.validateJdbcUrl(bootstrap.settings().url());
		PhantomAssertions.assertEquals("127.0.0.1", target.host(), "Goal030 CP1 test DB host is not canonical loopback.");
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_PORT, target.port(), "Goal030 CP1 test DB port drifted.");
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, target.database(), "Goal030 CP1 did not use the guarded test database.");
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, bootstrap.settings().login(), "Goal030 CP1 did not use the dedicated test DB user.");
		PhantomAssertions.assertFalse(PhantomTestDatabaseGuard.PRODUCTION_DATABASE.equals(target.database()), "Goal030 CP1 selected the production database.");

		_matrixPath = _moduleRoot.resolve(MATRIX_RELATIVE_PATH);
		_buildXml = Files.readString(_moduleRoot.resolve("build.xml"), StandardCharsets.UTF_8);
		_profilesBefore = scalar("SELECT COUNT(*) FROM phantom_profiles");
		_componentsBefore = scalar("SELECT COUNT(*) FROM phantom_profile_components");
		context.record("releaseBaseline.database", target.host() + ":" + target.port() + "/" + target.database());
		context.record("releaseBaseline.schemaVersion", bootstrap.schemaSnapshot().schemaVersion());
		context.record("releaseBaseline.schemaScripts", bootstrap.schemaSnapshot().scriptCount());
		context.record("releaseBaseline.schemaStatements", bootstrap.schemaSnapshot().statementCount());
		context.record("releaseBaseline.schemaAggregateSha256", bootstrap.schemaSnapshot().aggregateSha256());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			resetOperatorState();
			if (DatabaseFactory.isInitialized())
			{
				PhantomAssertions.assertEquals(_profilesBefore, scalar("SELECT COUNT(*) FROM phantom_profiles"), "Goal030 CP1 lifecycle changed Phantom profile count.");
				PhantomAssertions.assertEquals(_componentsBefore, scalar("SELECT COUNT(*) FROM phantom_profile_components"), "Goal030 CP1 lifecycle changed Phantom component count.");
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			DatabaseFactory.close();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-release-coverage-matrix", this::testCoverageMatrix);
		registry.add("02-shipped-disabled-settings", this::testShippedDisabledSettings);
		registry.add("03-disabled-lifecycle-operator-no-mutation", this::testDisabledLifecycleAndNoMutation);
	}

	private void testCoverageMatrix(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue(Files.isRegularFile(_matrixPath), "Goal030 release coverage matrix is missing.");
		final List<String> lines = Files.readAllLines(_matrixPath, StandardCharsets.UTF_8);
		PhantomAssertions.assertFalse(lines.isEmpty(), "Goal030 release coverage matrix is empty.");
		PhantomAssertions.assertEquals(HEADER, lines.getFirst(), "Goal030 release coverage matrix header drifted.");

		final Set<String> existingTargets = new HashSet<>();
		final Matcher targetMatcher = BUILD_TARGET.matcher(_buildXml);
		while (targetMatcher.find())
		{
			existingTargets.add(targetMatcher.group(1));
		}

		final Map<String, CoverageRow> rows = new HashMap<>();
		final Map<String, Integer> statusCounts = new HashMap<>();
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++)
		{
			final String line = lines.get(lineNumber - 1);
			PhantomAssertions.assertFalse(line.isBlank(), "Blank release coverage row at line " + lineNumber + ".");
			final String[] columns = line.split("\\t", -1);
			PhantomAssertions.assertEquals(7, columns.length, "Release coverage row does not have seven columns at line " + lineNumber + ".");
			final CoverageRow row = new CoverageRow(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6]);
			PhantomAssertions.assertTrue(REQUIRED_DOMAINS.contains(row.domainId()), "Unknown release domain: " + row.domainId());
			PhantomAssertions.assertEquals(null, rows.putIfAbsent(row.domainId(), row), "Duplicate release domain: " + row.domainId());
			validateLineage(row);
			validateOwners(row);
			validateEvidence(row, existingTargets);
			statusCounts.merge(row.status(), 1, Integer::sum);
		}

		PhantomAssertions.assertEquals(REQUIRED_DOMAINS, rows.keySet(), "Goal030 release coverage domains are incomplete.");
		FIXED_STATUSES.forEach((domainId, expectedStatus) -> PhantomAssertions.assertEquals(expectedStatus, rows.get(domainId).status(), "Accepted coverage class changed for release domain: " + domainId));
		RELEASE_PROGRESSIONS.forEach((domainId, progression) -> validateReleaseProgression(rows.get(domainId), progression));
		PhantomAssertions.assertEquals(11, statusCounts.getOrDefault("COVERED_PRIOR", 0), "Unexpected prior-coverage row count.");
		PhantomAssertions.assertEquals(6, statusCounts.getOrDefault("COVERED_CP1", 0), "Unexpected CP1-coverage row count.");
		final int expectedCoveredCp2 = "COVERED_CP2".equals(rows.get("activity-materialization").status()) ? 1 : 0;
		final int expectedCoveredCp3 = ("COVERED_CP3".equals(rows.get("restart-failure-recovery").status()) ? 1 : 0) + ("COVERED_CP3".equals(rows.get("rollback-release-control").status()) ? 1 : 0);
		final int expectedPending = 3 - expectedCoveredCp2 - expectedCoveredCp3;
		PhantomAssertions.assertEquals(expectedCoveredCp2, statusCounts.getOrDefault("COVERED_CP2", 0), "Unexpected CP2-coverage row count.");
		PhantomAssertions.assertEquals(expectedCoveredCp3, statusCounts.getOrDefault("COVERED_CP3", 0), "Unexpected CP3-coverage row count.");
		PhantomAssertions.assertEquals(expectedPending, statusCounts.getOrDefault("PENDING_GOAL030", 0), "Unexpected Goal030 pending row count.");
		final List<String> pendingDomains = RELEASE_PROGRESSIONS.entrySet().stream()
			.filter(entry -> "PENDING_GOAL030".equals(rows.get(entry.getKey()).status()))
			.map(entry -> entry.getKey() + ":" + entry.getValue().checkpoint())
			.sorted()
			.toList();
		context.record("releaseCoverage.rows", rows.size());
		context.record("releaseCoverage.coveredPrior", statusCounts.getOrDefault("COVERED_PRIOR", 0));
		context.record("releaseCoverage.coveredCp1", statusCounts.getOrDefault("COVERED_CP1", 0));
		context.record("releaseCoverage.coveredCp2", statusCounts.getOrDefault("COVERED_CP2", 0));
		context.record("releaseCoverage.coveredCp3", statusCounts.getOrDefault("COVERED_CP3", 0));
		context.record("releaseCoverage.pendingGoal030", statusCounts.getOrDefault("PENDING_GOAL030", 0));
		context.record("releaseCoverage.pendingDomains", String.join(",", pendingDomains));
	}

	private static void validateReleaseProgression(CoverageRow row, ReleaseProgression progression)
	{
		if ("PENDING_GOAL030".equals(row.status()))
		{
			PhantomAssertions.assertEquals(progression.checkpoint(), row.checkpoint(), "Pending release domain names the wrong checkpoint: " + row.domainId());
			PhantomAssertions.assertEquals("pending:" + progression.target(), row.antTargets(), "Pending release domain names the wrong planned target: " + row.domainId());
			return;
		}
		PhantomAssertions.assertEquals(progression.coveredStatus(), row.status(), "Release domain has an illegal coverage transition: " + row.domainId());
		PhantomAssertions.assertEquals("-", row.checkpoint(), "Covered release domain retained a checkpoint: " + row.domainId());
		PhantomAssertions.assertEquals(progression.target(), row.antTargets(), "Covered release domain names the wrong actual target: " + row.domainId());
	}

	private void validateLineage(CoverageRow row)
	{
		final String[] dependencies = row.goalLineage().split(";", -1);
		PhantomAssertions.assertTrue(dependencies.length > 0, "Release domain has no Goal lineage: " + row.domainId());
		for (String dependency : dependencies)
		{
			final Matcher matcher = GOAL.matcher(dependency);
			PhantomAssertions.assertTrue(matcher.matches(), "Release domain has malformed Goal lineage: " + row.domainId());
			final int goal = Integer.parseInt(matcher.group(1));
			PhantomAssertions.assertTrue((goal >= 1) && (goal <= 29), "Release domain names a Goal outside accepted 001-029: " + row.domainId());
		}
	}

	private void validateOwners(CoverageRow row)
	{
		final String[] owners = row.productionOwnerPaths().split(";", -1);
		PhantomAssertions.assertTrue(owners.length > 0, "Release domain has no production owner: " + row.domainId());
		for (String owner : owners)
		{
			PhantomAssertions.assertFalse(owner.isBlank(), "Release domain has a blank production owner: " + row.domainId());
			PhantomAssertions.assertFalse(owner.startsWith("test/"), "Release domain names a test owner as production: " + row.domainId());
			final Path ownerPath = _moduleRoot.resolve(owner).normalize();
			PhantomAssertions.assertTrue(ownerPath.startsWith(_moduleRoot) && Files.isRegularFile(ownerPath), "Release production owner path is missing: " + owner);
		}
	}

	private static void validateEvidence(CoverageRow row, Set<String> existingTargets)
	{
		PhantomAssertions.assertTrue(STATUSES.contains(row.status()), "Release domain has an unsupported status: " + row.domainId());
		PhantomAssertions.assertFalse(row.evidenceType().isBlank(), "Release domain has no evidence type: " + row.domainId());
		if ("PENDING_GOAL030".equals(row.status()))
		{
			PhantomAssertions.assertTrue(("CP2".equals(row.checkpoint()) || "CP3".equals(row.checkpoint())), "Pending release domain lacks CP2/CP3: " + row.domainId());
			PhantomAssertions.assertTrue(row.antTargets().startsWith("pending:"), "Pending release domain lacks an explicit planned target: " + row.domainId());
			final String plannedTarget = row.antTargets().substring("pending:".length());
			final String expectedToken = "goal030" + row.checkpoint().toLowerCase(Locale.ROOT);
			PhantomAssertions.assertTrue(plannedTarget.contains(expectedToken), "Pending target does not identify its Goal030 checkpoint: " + row.domainId());
			return;
		}

		PhantomAssertions.assertEquals("-", row.checkpoint(), "Covered release domain unexpectedly names a pending checkpoint: " + row.domainId());
		PhantomAssertions.assertFalse(row.antTargets().startsWith("pending:"), "Covered release domain names a pending target: " + row.domainId());
		final String[] targets = row.antTargets().split(";", -1);
		PhantomAssertions.assertFalse((targets.length == 1) && "verify".equals(targets[0]), "Generic verify is the only evidence for release domain: " + row.domainId());
		for (String target : targets)
		{
			PhantomAssertions.assertTrue(existingTargets.contains(target), "Release coverage target is absent from build.xml: " + target);
			final boolean oldScenario = target.equals("phantom-scenario-test") || target.contains("scenario-smoke");
			PhantomAssertions.assertFalse(oldScenario && row.evidenceType().contains("LIVING_WORLD_E2E"), "Old scenario-smoke evidence was claimed as living-world E2E: " + row.domainId());
		}
	}

	private void testShippedDisabledSettings(PhantomTestContext context)
	{
		final Path shippedConfig = _moduleRoot.resolve("dist/game/config/Custom/PhantomPlayers.ini");
		final PhantomPlayersConfig.Settings settings = PhantomPlayersConfig.read(shippedConfig);
		PhantomAssertions.assertFalse(settings.enabled(), "Shipped Phantom configuration is not disabled.");
		PhantomAssertions.assertFalse(settings.diagnosticsEnabled(), "Shipped disabled configuration retained diagnostics.");
		PhantomAssertions.assertEquals(0, settings.maxMaterializedPhantoms(), "Disabled settings expose materialization capacity.");
		PhantomAssertions.assertEquals(0, settings.maxScheduledPhantomProfiles(), "Disabled settings expose scheduler capacity.");
		PhantomAssertions.assertEquals(0, settings.schedulerPulseMillis(), "Disabled settings expose a scheduler pulse.");
		PhantomAssertions.assertEquals(0, settings.schedulerProfilesPerPulse(), "Disabled settings expose a scheduler pulse budget.");
		PhantomAssertions.assertEquals(0, settings.populationTarget(), "Disabled settings expose a population target.");
		PhantomAssertions.assertEquals(0, settings.populationActiveTarget(), "Disabled settings expose an ACTIVE population target.");
		PhantomAssertions.assertEquals(0, settings.populationCreationInFlight(), "Disabled settings expose population creation capacity.");
		PhantomAssertions.assertEquals(0, settings.populationBoundariesPerPulse(), "Disabled settings expose population boundary capacity.");
		PhantomAssertions.assertEquals(0, settings.partyOperationsPerPulse(), "Disabled settings expose party operation capacity.");
		PhantomAssertions.assertEquals(0, settings.socialCacheProfiles(), "Disabled settings expose social cache capacity.");
		PhantomPlayersConfig.load();
		PhantomAssertions.assertFalse(PhantomPlayersConfig.isEnabled(), "Static shipped Phantom configuration is not disabled.");
		context.record("releaseBaseline.shippedEnabled", settings.enabled());
		context.record("releaseBaseline.populationTargets", settings.populationTarget() + "/" + settings.populationActiveTarget());
	}

	private void testDisabledLifecycleAndNoMutation(PhantomTestContext context) throws Exception
	{
		resetOperatorState();
		final long profilesBefore = scalar("SELECT COUNT(*) FROM phantom_profiles");
		final long componentsBefore = scalar("SELECT COUNT(*) FROM phantom_profile_components");
		final PhantomSystem system = new PhantomSystem(PhantomPlayersConfig.Settings.disabled());
		PhantomAssertions.assertEquals(PhantomSystem.State.NEW, system.snapshot().state(), "Disabled PhantomSystem did not begin in NEW.");
		PhantomAssertions.assertFalse(system.start(), "Disabled PhantomSystem start returned true.");
		final PhantomSystem.Snapshot disabled = system.snapshot();
		PhantomAssertions.assertEquals(PhantomSystem.State.DISABLED, disabled.state(), "Disabled PhantomSystem did not enter DISABLED.");
		PhantomAssertions.assertFalse(disabled.settings().enabled(), "Disabled PhantomSystem snapshot exposes enabled settings.");

		PhantomAssertions.assertEquals("STOPPED", disabled.scheduler().state().name(), "Disabled scheduler snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.scheduler().registered(), "Disabled scheduler retained profiles.");
		PhantomAssertions.assertEquals(0, disabled.scheduler().ready(), "Disabled scheduler retained ready work.");
		PhantomAssertions.assertEquals(0, disabled.scheduler().due(), "Disabled scheduler retained due work.");
		PhantomAssertions.assertEquals(0, disabled.scheduler().capacity(), "Disabled scheduler allocated capacity.");
		PhantomAssertions.assertEquals(0, disabled.scheduler().scheduledTaskCount(), "Disabled scheduler owns a task.");
		PhantomAssertions.assertEquals("STOPPED", disabled.decision().state().name(), "Disabled Decision snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.decision().attached(), "Disabled Decision snapshot retained attachments.");
		PhantomAssertions.assertEquals(0, disabled.decision().capacity(), "Disabled Decision snapshot allocated capacity.");
		PhantomAssertions.assertEquals(0L, disabled.decision().inFlight(), "Disabled Decision snapshot retained in-flight work.");
		PhantomAssertions.assertEquals("STOPPED", disabled.navigation().state().name(), "Disabled Navigation snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.navigation().activeRequests(), "Disabled Navigation retained requests.");
		PhantomAssertions.assertEquals(0, disabled.navigation().queuedRequests(), "Disabled Navigation retained queued requests.");
		PhantomAssertions.assertEquals(0, disabled.navigation().queueCapacity(), "Disabled Navigation allocated a queue.");
		PhantomAssertions.assertEquals(0, disabled.navigation().currentWorkers(), "Disabled Navigation created workers.");
		PhantomAssertions.assertEquals(0, disabled.navigation().cacheCapacity(), "Disabled Navigation allocated a cache.");
		PhantomAssertions.assertEquals("STOPPED", disabled.topology().state().name(), "Disabled Topology snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.topology().registeredProfiles(), "Disabled Topology retained profiles.");
		PhantomAssertions.assertEquals(0, disabled.topology().eventsInFlight(), "Disabled Topology retained events.");
		PhantomAssertions.assertEquals("STOPPED", disabled.gameKnowledge().state().name(), "Disabled Game Knowledge snapshot is active.");
		PhantomAssertions.assertEquals(0L, disabled.gameKnowledge().metrics().buildsStarted(), "Disabled Game Knowledge started a build.");
		PhantomAssertions.assertEquals("STOPPED", disabled.semanticUnderstanding().state().name(), "Disabled semantic snapshot is active.");
		PhantomAssertions.assertEquals(0L, disabled.semanticUnderstanding().metrics().startsCompleted(), "Disabled semantic service started.");
		PhantomAssertions.assertEquals("STOPPED", disabled.progression().state().name(), "Disabled Progression snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.progression().currentOperations(), "Disabled Progression retained operations.");
		PhantomAssertions.assertEquals(0, disabled.progression().currentActorLeases(), "Disabled Progression retained actor leases.");
		PhantomAssertions.assertEquals("STOPPED", disabled.combat().state().name(), "Disabled Combat snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.combat().activeSessions(), "Disabled Combat retained sessions.");
		PhantomAssertions.assertEquals(0, disabled.combat().currentWorkers(), "Disabled Combat created workers.");
		PhantomAssertions.assertEquals(null, disabled.background(), "Disabled PhantomSystem created Background runtime.");
		PhantomAssertions.assertEquals("STOPPED", disabled.population().state().name(), "Disabled Population snapshot is active.");
		PhantomAssertions.assertEquals(0, disabled.population().target(), "Disabled Population snapshot has a target.");
		PhantomAssertions.assertEquals(0, disabled.population().activeTarget(), "Disabled Population snapshot has an ACTIVE target.");
		PhantomAssertions.assertEquals(0, disabled.population().managed(), "Disabled Population snapshot retained managed profiles.");
		PhantomAssertions.assertEquals("STOPPED", disabled.social().state().name(), "Disabled Social snapshot is active.");
		PhantomAssertions.assertEquals("STOPPED", disabled.conversation().state().name(), "Disabled Conversation snapshot is active.");
		PhantomAssertions.assertEquals("STOPPED", disabled.conversationExecution().state().name(), "Disabled Conversation execution snapshot is active.");
		PhantomAssertions.assertTrue(disabled.metrics().isZero(), "Disabled PhantomSystem changed lifecycle metrics.");
		PhantomAssertions.assertFalse(disabled.trace().enabled(), "Disabled PhantomSystem enabled diagnostics.");
		PhantomAssertions.assertEquals(0, disabled.trace().capacity(), "Disabled PhantomSystem allocated diagnostic capacity.");
		PhantomAssertions.assertFalse(disabled.selectedTrace().enabled(), "Disabled PhantomSystem enabled selected trace.");
		PhantomAssertions.assertEquals(0, disabled.selectedTrace().capacity(), "Disabled PhantomSystem allocated selected trace capacity.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Direct disabled start published a configured runtime.");

		final var enable = PhantomSystem.operatorEnable();
		PhantomAssertions.assertEquals(OperatorControlCode.CONFIG_DISABLED, enable.code(), "Operator enable bypassed shipped disabled configuration.");
		PhantomAssertions.assertFalse(enable.desiredRuntimeEnabled(), "Operator enable reported disabled static config as runnable.");
		PhantomAssertions.assertFalse(enable.runtimeConfigured(), "Operator enable published a disabled runtime.");
		PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "Canonical configured start bypassed shipped disabled configuration.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Disabled operator path retained configured runtime ownership.");
		PhantomAssertions.assertFalse(system.shutdown(), "Disabled PhantomSystem shutdown reported a running lifecycle.");
		PhantomAssertions.assertTrue(system.snapshot().metrics().isZero(), "Disabled shutdown changed lifecycle metrics.");

		final long profilesAfter = scalar("SELECT COUNT(*) FROM phantom_profiles");
		final long componentsAfter = scalar("SELECT COUNT(*) FROM phantom_profile_components");
		PhantomAssertions.assertEquals(profilesBefore, profilesAfter, "Disabled path changed Phantom profile count.");
		PhantomAssertions.assertEquals(componentsBefore, componentsAfter, "Disabled path changed Phantom component count.");
		context.record("releaseBaseline.disabledDbProfiles", profilesBefore + "/" + profilesAfter);
		context.record("releaseBaseline.disabledDbComponents", componentsBefore + "/" + componentsAfter);
		context.record("releaseBaseline.disabledRuntimeConfigured", PhantomSystem.hasConfiguredInstance());
		resetOperatorState();
	}

	private static long scalar(String sql) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql);
			ResultSet result = statement.executeQuery())
		{
			PhantomAssertions.assertTrue(result.next(), "Release baseline count returned no row.");
			return result.getLong(1);
		}
	}

	private static void resetOperatorState()
	{
		PhantomSystem.releaseOperatorShutdownFailureForTesting();
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		if (PhantomSystem.hasConfiguredInstance())
		{
			throw new AssertionError("Goal030 CP1 retained a configured PhantomSystem owner.");
		}
		PhantomSystem.resetOperatorModeForTesting();
	}

	private record CoverageRow(String domainId, String goalLineage, String productionOwnerPaths, String antTargets, String evidenceType, String status, String checkpoint)
	{
	}

	private record ReleaseProgression(String checkpoint, String coveredStatus, String target)
	{
	}
}
