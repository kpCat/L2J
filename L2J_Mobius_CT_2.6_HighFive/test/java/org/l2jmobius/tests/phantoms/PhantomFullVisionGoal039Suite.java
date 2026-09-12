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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog;

public final class PhantomFullVisionGoal039Suite implements PhantomTestSuite
{
	public enum Mode
	{
		STATIC,
		DOCUMENTATION
	}

	private static final long SEED = 39003901L;
	private static final String REQUIRED_PARENT = "f01401d79d5f41aac87cd8425b78a2f00abbf417";
	private static final String FINAL_MARKER = "FEATURE_COMPLETE_FOR_DECLARED_SCOPE";
	private static final String OLD_ACQUISITION_CATALOG_HASH = "e9b5e5d0038414d892a64971425601807910526aeb073d19d59039072dc4247b";
	private static final Map<String, String> OLD_QUEST_SCRIPT_HASHES = Map.of(
		"q00102-dryads-tear", "ac2d5c6eb9082bb605df535cdd8c854b54ced6a4b5ebd4d59aaff38bbb8d137d",
		"q00152-golem-shard", "bfdde72c661d13106301d3421effb4e19d886e5db7f33fe7d4de6cf44b3e22c6");
	private static final String HISTORICAL_MATRIX = "test/resources/phantoms/release/goal030-release-coverage.tsv";
	private static final String HISTORICAL_MATRIX_SHA256 = "fd891490e7bed44dba7d33f1b72d5c1de46ff67003190b31d22b7dd96206e64e";
	private static final String FINAL_MATRIX = "test/resources/phantoms/release/goal039-full-vision-coverage.tsv";
	private static final String FULL_RUNTIME_START_CALL = "PhantomSystem." + "startConfiguredForTesting(";
	private static final String SUPPORTED_OWNER_BOOTSTRAP_CALL = "PhantomSupportedContentScriptBootstrap." + "loadGoal036Owners(context)";
	private static final String FINAL_HEADER = "domain_id\tgoal_lineage\tauthoritative_owner_paths\tfresh_goal039_evidence\tpredecessor_evidence\tevidence_kind\tfinal_status\tclaim_boundary";
	private static final Set<String> FINAL_STATUSES = Set.of("PASS", "ACCEPT", "BLOCKED", "NOT_RUN_BLOCKED");
	private static final Set<String> GOAL032_CONFIG_KEYS = Set.of(
		"EnablePhantomSystem",
		"EnablePhantomDiagnostics",
		"MaxMaterializedPhantoms",
		"MaxScheduledPhantomProfiles",
		"PhantomSchedulerPulseMillis",
		"PhantomSchedulerProfilesPerPulse",
		"PhantomPopulationTarget",
		"PhantomPopulationActiveTarget",
		"PhantomPopulationCreationInFlight",
		"PhantomPopulationBoundariesPerPulse",
		"PhantomPartyOperationsPerPulse",
		"PhantomSocialCacheProfiles",
		"PhantomPopulationTimeZone");
	private static final Set<String> GOAL033_CONFIG_KEYS = Set.of(
		"EnablePhantomEcology",
		"PhantomEcologyPreset",
		"PhantomEcologyWorldAgeDays",
		"PhantomEcologyArchiveLimit");
	private static final Set<String> GOAL038_CONFIG_KEYS = Set.of(
		"EnablePhantomHumanizedConversation",
		"EnablePhantomCustomConversationPack",
		"PhantomConversationRegister",
		"PhantomConversationProfanity",
		"PhantomConversationVariation",
		"EnablePhantomMatureConversation");
	private static final Set<String> CURRENT_SHIPPED_CONFIG_KEYS = configUnion(GOAL032_CONFIG_KEYS, GOAL033_CONFIG_KEYS, GOAL038_CONFIG_KEYS);
	private static final Set<String> LOCAL_PLAY_EXPLICIT_CONFIG_KEYS = configUnion(GOAL032_CONFIG_KEYS, GOAL033_CONFIG_KEYS);
	private static final Pattern CONFIG_KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9]+)\\s*=", Pattern.MULTILINE);
	private static final Pattern BUILD_TARGET = Pattern.compile("<target\\s+name=\"([^\"]+)\"");
	private static final Pattern TARGET_DEPENDS = Pattern.compile("<target\\s+name=\"([^\"]+)\"\\s+depends=\"([^\"]*)\"");
	private static final Pattern FINAL_JAR = Pattern.compile("(?i)final JAR SHA-256:\\s*`?[0-9a-f]{64}`?.*bytes:\\s*`?[0-9]+`?", Pattern.DOTALL);
	private static final Pattern REAL_RUN = Pattern.compile("202[0-9]{5}-[0-9]{6}-[0-9a-f]{8}");
	private static final Pattern GOAL039_NOT_STARTED = Pattern.compile("Goal039[^\\r\\n]{0,240}NOT_STARTED");
	private static final List<String> HISTORICAL_DOMAINS = List.of(
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
	private static final List<String> POST_GOAL030_DOMAINS = List.of(
		"safe-defaults-preflight",
		"living-population-ecology",
		"real-stack-black-box",
		"siege-gameplay",
		"bounded-quests-instances",
		"quest-rates-parity",
		"humanized-conversation-custom",
		"final-documentation-freeze");
	private static final List<String> REQUIRED_REPORTS = List.of(
		"docs/phantoms/reports/029d-driver-session-attribution-retained-memory-corrective.md",
		"docs/phantoms/reports/030-checkpoint-3-release-decision.md",
		"docs/phantoms/reports/033-living-population-ecology-resume.md",
		"docs/phantoms/reports/033A-causal-background-catchup-resume.md",
		"docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-closure8-final.md",
		"docs/phantoms/reports/035-siege-gameplay.md",
		"docs/phantoms/reports/036-bounded-quests-instances.md",
		"docs/phantoms/reports/037-full-quest-rates-audit.md",
		"docs/phantoms/reports/038-humanized-russian-semantic-pack.md");
	private static final Set<String> FULL_RUNTIME_BOOTSTRAP_SUITES = Set.of(
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomCrossDomainAutonomousAlphaGoal030Checkpoint2Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomLocalPlayReadinessGoal031Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomPopulationEcologyProductionGoal033Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomPopulationResetOwnershipGoal032Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomPopulationResetReseedGoal032Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomReleaseDecisionRollbackGoal030Checkpoint3Suite.java",
		"test/java/org/l2jmobius/gameserver/phantoms/PhantomRestartFailureRecoveryGoal030Checkpoint3Suite.java");

	private final Mode _mode;

	public PhantomFullVisionGoal039Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "full-vision-goal039-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.STATIC)
		{
			registry.add("01-historical-and-final-matrices", this::testMatrices);
			registry.add("02-safe-defaults-and-routes", this::testSafeDefaultsAndRoutes);
			registry.add("03-lineage-reports-and-no-goal040", this::testLineageReportsAndNoGoal040);
			registry.add("04-canonical-utf8-source-hash-controls", this::testCanonicalUtf8SourceHash);
			registry.add("05-canonical-active-pin-and-catalog-parity", this::testCanonicalActivePins);
			registry.add("06-stale-quest-binding-authority-migration", this::testStaleQuestBindingMigration);
			registry.add("07-headless-full-runtime-native-owner-bootstrap-census", this::testHeadlessFullRuntimeBootstrapCensus);
			registry.add("08-goal016-commit-backed-historical-verifier", this::testGoal016CommitBackedHistoricalVerifier);
		}
		else
		{
			registry.add("01-final-status-and-freeze", this::testFinalStatusAndFreeze);
			registry.add("02-final-report-provenance", this::testFinalReportProvenance);
		}
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal039 suite used the wrong deterministic seed.");
	}

	private void testMatrices(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final Path historicalPath = root.resolve(HISTORICAL_MATRIX);
		final byte[] historicalBytes = Files.readAllBytes(historicalPath);
		PhantomAssertions.assertEquals(HISTORICAL_MATRIX_SHA256, sha256(historicalBytes), "Historical Goal030 matrix bytes changed.");
		final List<String> historicalLines = Files.readAllLines(historicalPath, StandardCharsets.UTF_8);
		PhantomAssertions.assertEquals(21, historicalLines.size(), "Historical Goal030 matrix is not exactly 20 rows.");
		final Map<String, HistoricalRow> historical = parseHistorical(historicalLines);
		PhantomAssertions.assertEquals(new HashSet<>(HISTORICAL_DOMAINS), historical.keySet(), "Historical Goal030 domains drifted.");

		final String build = read(root, "build.xml");
		final Set<String> targets = buildTargets(build);
		final List<String> finalLines = Files.readAllLines(root.resolve(FINAL_MATRIX), StandardCharsets.UTF_8);
		PhantomAssertions.assertFalse(finalLines.isEmpty(), "Goal039 final matrix is empty.");
		PhantomAssertions.assertEquals(FINAL_HEADER, finalLines.getFirst(), "Goal039 final matrix header drifted.");
		final Map<String, FinalRow> rows = new LinkedHashMap<>();
		for (int lineNumber = 2; lineNumber <= finalLines.size(); lineNumber++)
		{
			final String line = finalLines.get(lineNumber - 1);
			PhantomAssertions.assertFalse(line.isBlank(), "Goal039 matrix has a blank row at line " + lineNumber + ".");
			final String[] columns = line.split("\\t", -1);
			PhantomAssertions.assertEquals(8, columns.length, "Goal039 matrix row does not have eight columns at line " + lineNumber + ".");
			final FinalRow row = new FinalRow(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6], columns[7]);
			PhantomAssertions.assertEquals(null, rows.putIfAbsent(row.domainId(), row), "Duplicate Goal039 domain: " + row.domainId());
			PhantomAssertions.assertTrue(FINAL_STATUSES.contains(row.finalStatus()), "Goal039 row has an unsupported final status: " + row.domainId());
			for (String forbidden : List.of("UNKNOWN", "PENDING", "DEFERRED_REQUIRES_EVIDENCE"))
			{
				PhantomAssertions.assertFalse(line.contains(forbidden), "Goal039 row retained non-final token " + forbidden + ": " + row.domainId());
			}
			validatePaths(root, row.domainId(), row.ownerPaths());
			validateFreshTargets(row, targets);
			validatePredecessorEvidence(root, row, targets);
			PhantomAssertions.assertFalse(row.claimBoundary().isBlank(), "Goal039 row has no claim boundary: " + row.domainId());
		}

		final List<String> expectedOrder = new ArrayList<>(HISTORICAL_DOMAINS);
		expectedOrder.addAll(POST_GOAL030_DOMAINS);
		PhantomAssertions.assertEquals(expectedOrder, List.copyOf(rows.keySet()), "Goal039 matrix ordering or domain set drifted.");
		for (String domain : HISTORICAL_DOMAINS)
		{
			final HistoricalRow source = historical.get(domain);
			final FinalRow inherited = rows.get(domain);
			PhantomAssertions.assertEquals("PASS", inherited.finalStatus(), "Goal039 changed an accepted historical status: " + domain);
			PhantomAssertions.assertEquals(source.goalLineage(), inherited.goalLineage(), "Goal039 changed inherited lineage: " + domain);
			PhantomAssertions.assertEquals(source.ownerPaths(), inherited.ownerPaths(), "Goal039 changed inherited production owners: " + domain);
			PhantomAssertions.assertEquals("INHERITED_GOAL030_PLUS_FRESH", inherited.evidenceKind(), "Goal039 historical row lacks strict inherited evidence kind: " + domain);
			PhantomAssertions.assertTrue(inherited.predecessorEvidence().contains(source.antTargets()) && inherited.predecessorEvidence().contains("030-checkpoint-3-release-decision.md"), "Goal039 historical row lost exact predecessor target/report evidence: " + domain);
		}
		for (String domain : POST_GOAL030_DOMAINS)
		{
			PhantomAssertions.assertEquals("POST_GOAL030_FRESH", rows.get(domain).evidenceKind(), "Goal039 post-release row has the wrong evidence kind: " + domain);
		}
		final boolean blocked = rows.values().stream().anyMatch(row -> Set.of("BLOCKED", "NOT_RUN_BLOCKED").contains(row.finalStatus()));
		if (blocked)
		{
			PhantomAssertions.assertEquals("PASS", rows.get("safe-defaults-preflight").finalStatus(), "Goal039 blocked matrix lost the completed static phase.");
			PhantomAssertions.assertEquals("NOT_RUN_BLOCKED", rows.get("final-documentation-freeze").finalStatus(), "Goal039 blocked matrix overclaims the final freeze.");
			int blockedRows = 0;
			int incompleteRows = 0;
			for (String domain : POST_GOAL030_DOMAINS)
			{
				final String status = rows.get(domain).finalStatus();
				PhantomAssertions.assertFalse("ACCEPT".equals(status), "Goal039 blocked matrix contains an ACCEPT row: " + domain);
				if (!"PASS".equals(status))
				{
					incompleteRows++;
					if ("BLOCKED".equals(status))
					{
						blockedRows++;
					}
					else
					{
						PhantomAssertions.assertEquals("NOT_RUN_BLOCKED", status, "Goal039 blocked matrix has an unsupported incomplete status: " + domain);
					}
				}
			}
			PhantomAssertions.assertTrue(incompleteRows > 0, "Goal039 blocked matrix has no incomplete row.");
			PhantomAssertions.assertTrue(blockedRows <= 1, "Goal039 blocked matrix identifies more than one failed domain.");
		}
		else
		{
			for (String domain : POST_GOAL030_DOMAINS.subList(0, POST_GOAL030_DOMAINS.size() - 1))
			{
				PhantomAssertions.assertEquals("PASS", rows.get(domain).finalStatus(), "Goal039 accepted matrix has an incomplete domain: " + domain);
			}
			PhantomAssertions.assertEquals("ACCEPT", rows.get("final-documentation-freeze").finalStatus(), "Goal039 accepted matrix lacks the final ACCEPT decision.");
		}
		context.record("goal039.historicalRows", historical.size());
		context.record("goal039.finalRows", rows.size());
		context.record("goal039.historicalMatrixSha256", sha256(historicalBytes));
	}

	private void testSafeDefaultsAndRoutes(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String shipped = read(root, "dist/game/config/Custom/PhantomPlayers.ini");
		final String preset = read(root, "docs/phantoms/examples/PhantomPlayers.local-play.ini");
		final String parser = read(root, "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java");
		final List<String> shippedKeyList = configKeys(shipped);
		final List<String> presetKeyList = configKeys(preset);
		PhantomAssertions.assertEquals(13, GOAL032_CONFIG_KEYS.size(), "Goal032 current config ownership cardinality drifted.");
		PhantomAssertions.assertEquals(4, GOAL033_CONFIG_KEYS.size(), "Goal033 current config ownership cardinality drifted.");
		PhantomAssertions.assertEquals(6, GOAL038_CONFIG_KEYS.size(), "Goal038 current config ownership cardinality drifted.");
		PhantomAssertions.assertEquals(23, shippedKeyList.size(), "Current shipped Phantom config assignment count drifted.");
		PhantomAssertions.assertEquals(CURRENT_SHIPPED_CONFIG_KEYS, new HashSet<>(shippedKeyList), "Current shipped Phantom config is not the exact Goal032+Goal033+Goal038 union.");
		PhantomAssertions.assertEquals(17, presetKeyList.size(), "Current local-play preset assignment count drifted.");
		PhantomAssertions.assertEquals(LOCAL_PLAY_EXPLICIT_CONFIG_KEYS, new HashSet<>(presetKeyList), "Current local-play preset is not the exact Goal032+Goal033 explicit union.");
		PhantomAssertions.assertTrue(GOAL038_CONFIG_KEYS.stream().noneMatch(new HashSet<>(presetKeyList)::contains), "Local-play preset unexpectedly makes a Goal038 optional setting explicit.");
		for (String key : CURRENT_SHIPPED_CONFIG_KEYS)
		{
			PhantomAssertions.assertTrue(parser.contains("\"" + key + "\""), "Current production parser does not reference shipped key " + key + ".");
		}
		for (String line : List.of(
			"EnablePhantomSystem = False",
			"PhantomPopulationTarget = 0",
			"PhantomPopulationActiveTarget = 0",
			"EnablePhantomDiagnostics = False",
			"EnablePhantomMatureConversation = False",
			"EnablePhantomHumanizedConversation = True",
			"EnablePhantomCustomConversationPack = True"))
		{
			PhantomAssertions.assertTrue(shipped.contains(line), "Goal039 shipped default drifted: " + line);
		}
		final PhantomPlayersConfig.Settings settings = PhantomPlayersConfig.read(root.resolve("dist/game/config/Custom/PhantomPlayers.ini"));
		PhantomAssertions.assertFalse(settings.enabled(), "Goal039 shipped system default is enabled.");
		PhantomAssertions.assertFalse(settings.diagnosticsEnabled(), "Goal039 shipped diagnostics default is enabled.");
		PhantomAssertions.assertEquals(0, settings.populationTarget(), "Goal039 shipped population target is nonzero.");
		PhantomAssertions.assertEquals(0, settings.populationActiveTarget(), "Goal039 shipped ACTIVE target is nonzero.");
		PhantomAssertions.assertFalse(settings.conversation().matureEnabled(), "Goal039 shipped mature conversation is reachable while disabled.");
		final PhantomPlayersConfig.Settings presetSettings = PhantomPlayersConfig.read(root.resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini"));
		PhantomAssertions.assertTrue(presetSettings.enabled(), "Goal039 local-play preset is not parser-runnable.");
		PhantomAssertions.assertTrue(presetSettings.conversation().humanizedEnabled(), "Omitted Goal038 humanized setting did not default to true.");
		PhantomAssertions.assertTrue(presetSettings.conversation().customPackEnabled(), "Omitted Goal038 custom-pack setting did not default to true.");
		PhantomAssertions.assertEquals(PhantomPlayersConfig.ConversationRegister.CASUAL, presetSettings.conversation().register(), "Omitted Goal038 register did not default to CASUAL.");
		PhantomAssertions.assertEquals(PhantomPlayersConfig.ConversationProfanity.CONTEXTUAL, presetSettings.conversation().profanity(), "Omitted Goal038 profanity did not default to CONTEXTUAL.");
		PhantomAssertions.assertEquals(PhantomPlayersConfig.ConversationVariation.HIGH, presetSettings.conversation().variation(), "Omitted Goal038 variation did not default to HIGH.");
		PhantomAssertions.assertFalse(presetSettings.conversation().matureEnabled(), "Omitted Goal038 mature setting did not default to false.");

		final String build = read(root, "build.xml");
		final Map<String, Set<String>> dependencies = targetDependencies(build);
		final Set<String> staticDependencies = dependencies.get("phantom-full-vision-goal039-static-test");
		final Set<String> domainDependencies = dependencies.get("phantom-full-vision-goal039-domain-test");
		PhantomAssertions.assertTrue(staticDependencies != null, "Goal039 static aggregate target is missing.");
		PhantomAssertions.assertTrue(domainDependencies != null, "Goal039 domain aggregate target is missing.");
		for (String target : List.of(
			"phantom-local-play-preflight-test",
			"phantom-release-baseline-goal030cp1-test",
			"phantom-humanized-goal038-catalog-test",
			"phantom-quest-rates-goal037-static-test",
			"phantom-db-guard-negative-control"))
		{
			PhantomAssertions.assertTrue(staticDependencies.contains(target), "Goal039 static aggregate omits " + target + ".");
		}
		for (String target : List.of(
			"phantom-historical-background-goal033a-test",
			"phantom-population-ecology-goal033-test",
			"phantom-population-ecology-production-goal033-test",
			"phantom-siege-goal035-test",
			"phantom-quest-instance-goal036-test",
			"phantom-quest-rates-goal037-test",
			"phantom-humanized-goal038-focused-test",
			"phantom-humanized-goal038-affected-test",
			"phantom-humanized-goal038-production-restart-test",
			"phantom-scale-envelope-goal029cp1-test",
			"phantom-scale-environment-goal029cp2-test",
			"phantom-scale-endurance-goal029cp3-test",
			"phantom-restart-failure-recovery-goal030cp3-test",
			"phantom-release-decision-rollback-goal030cp3-test"))
		{
			PhantomAssertions.assertTrue(domainDependencies.contains(target), "Goal039 domain aggregate omits " + target + ".");
		}
		PhantomAssertions.assertFalse(staticDependencies.contains("prepare-phantom-test-db") || domainDependencies.contains("prepare-phantom-test-db"), "Goal039 aggregate depends on destructive DB preparation.");
		context.record("goal039.shipped", "enabled=false,population=0,active=0,diagnostics=false,mature=false");
		context.record("goal039.configOwnership", "goal032=13,goal033=4,goal038=6,shipped=23,preset=17");
		context.record("goal039.presetConversationDefaults", "humanized=true,custom=true,register=CASUAL,profanity=CONTEXTUAL,variation=HIGH,mature=false");
		context.record("goal039.aggregateTargets", "static=" + staticDependencies.size() + ",domain=" + domainDependencies.size());
	}

	private void testLineageReportsAndNoGoal040(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		for (String report : REQUIRED_REPORTS)
		{
			PhantomAssertions.assertTrue(Files.isRegularFile(root.resolve(report)), "Required predecessor report is missing: " + report);
		}
		for (String path : List.of(
			"test/resources/phantoms/quest-rates/goal037-quest-inventory.tsv",
			"test/resources/phantoms/quest-rates/goal037-rate-sites.tsv",
			"dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v1.xml",
			"dist/game/data/phantoms/conversation/humanized/high-five-ru-humanized-conversation-v1.xml"))
		{
			PhantomAssertions.assertTrue(Files.isRegularFile(root.resolve(path)) && (Files.size(root.resolve(path)) > 0), "Required current release data is missing: " + path);
		}
		final String build = read(root, "build.xml");
		final String launcher = read(root, "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java");
		PhantomAssertions.assertFalse(build.toLowerCase(java.util.Locale.ROOT).contains("goal040"), "build.xml contains an automatic Goal040 route.");
		PhantomAssertions.assertFalse(launcher.toLowerCase(java.util.Locale.ROOT).contains("goal040"), "PhantomTestLauncher contains an automatic Goal040 route.");
		final Path tasks = root.resolve("docs/phantoms/tasks");
		if (Files.isDirectory(tasks))
		{
			try (Stream<Path> entries = Files.list(tasks))
			{
				PhantomAssertions.assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith("040-")), "An automatic Goal040 task package exists.");
			}
		}
		context.record("goal039.requiredParent", REQUIRED_PARENT);
		context.record("goal039.goal040", "absent");
	}

	private void testHeadlessFullRuntimeBootstrapCensus(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final Set<String> audited = new HashSet<>();
		int startupCalls = 0;
		try (Stream<Path> sources = Files.walk(root.resolve("test/java")))
		{
			for (Path path : sources.filter(Files::isRegularFile).filter(value -> value.getFileName().toString().endsWith(".java")).toList())
			{
				final String source = Files.readString(path, StandardCharsets.UTF_8);
				final int calls = occurrences(source, FULL_RUNTIME_START_CALL);
				if (calls == 0)
				{
					continue;
				}
				final String relative = root.relativize(path).toString().replace('\\', '/');
				audited.add(relative);
				startupCalls += calls;
				PhantomAssertions.assertTrue(source.contains("PhantomHeadlessPlayerTestEnvironment"), "Full PhantomSystem test startup is outside the audited headless family: " + relative);
				PhantomAssertions.assertEquals(1, occurrences(source, SUPPORTED_OWNER_BOOTSTRAP_CALL), "Supported-content bootstrap invocation count drifted: " + relative);
				final int initialize = source.indexOf("_environment.initialize(");
				final int bootstrap = source.indexOf(SUPPORTED_OWNER_BOOTSTRAP_CALL);
				final int startup = source.indexOf(FULL_RUNTIME_START_CALL);
				PhantomAssertions.assertTrue((initialize >= 0) && (initialize < bootstrap) && (bootstrap < startup), "Headless native-owner bootstrap order drifted: " + relative);
				final int startRuntime = source.indexOf("private void startRuntime(");
				PhantomAssertions.assertTrue((startRuntime < 0) || (bootstrap < startRuntime), "Supported-content bootstrap moved into startRuntime/restart: " + relative);
				PhantomAssertions.assertFalse(source.contains("executeScriptList()"), "Audited full-runtime suite uses the unbounded script-list shortcut: " + relative);
				if (relative.endsWith("PhantomCrossDomainAutonomousAlphaGoal030Checkpoint2Suite.java"))
				{
					PhantomAssertions.assertFalse(source.contains("ScriptEngine.MASTER_HANDLER_FILE"), "CrossDomain CP2 retained direct master-handler setup alongside the shared helper.");
				}
			}
		}
		PhantomAssertions.assertEquals(FULL_RUNTIME_BOOTSTRAP_SUITES, audited, "Full PhantomSystem test startup census drifted.");
		PhantomAssertions.assertEquals(9, startupCalls, "Full PhantomSystem test startup call-site count drifted.");
		context.record("goal039.headlessFullRuntimeCensus", "suites=7,startupCalls=9,helperInvocationsPerSuite=1");
	}

	private void testGoal016CommitBackedHistoricalVerifier(PhantomTestContext context) throws Exception
	{
		final String verifier = read(context.moduleRoot(), "tools/phantoms/verify-task-016.ps1");
		for (String token : List.of(
			"function Read-CommitBytes",
			"StandardOutput.BaseStream.CopyToAsync",
			"function Read-VerificationBytes",
			"$mode -eq \"working-completion\"",
			"return Read-CommitBytes $completionCommit $relativePath",
			"$encoding.GetString((Read-VerificationBytes $relativePath))",
			"$sha256.ComputeHash((Read-VerificationBytes $relativePath))",
			"Expected one unique ordinary Goal 016 completion direct child.",
			"merge-base\", \"--is-ancestor\", $completionCommit, $head",
			"Assert-True ($actual -eq ([string] $property.Value).ToUpperInvariant())"))
		{
			PhantomAssertions.assertTrue(verifier.contains(token), "Goal016 verifier lost commit-backed integrity token: " + token);
		}
		final int hashStart = verifier.indexOf("function Get-Sha256");
		final int hashEnd = verifier.indexOf("function Invoke-Git", hashStart);
		PhantomAssertions.assertTrue((hashStart >= 0) && (hashEnd > hashStart), "Goal016 SHA-256 byte-source function is not isolated.");
		final String hashFunction = verifier.substring(hashStart, hashEnd);
		PhantomAssertions.assertFalse(hashFunction.contains("ReadAllBytes") || hashFunction.contains("UTF8Encoding") || hashFunction.contains("GetString") || hashFunction.contains("GetBytes") || hashFunction.contains("Trim("), "Goal016 historical SHA-256 path normalizes or decodes authoritative bytes.");
		PhantomAssertions.assertFalse(verifier.toLowerCase(java.util.Locale.ROOT).contains("b44da3b3a90d8bce566e2cef6acbbe1d599952c456a365512231e2615c608b86"), "Goal016 verifier accepts the Windows CRLF checkout hash as an alternate.");
		context.record("goal039.goal016Verifier", "historical=completion-commit-raw-bytes,working=implementation-head,no-alternate-eol-hash");
	}

	private void testCanonicalUtf8SourceHash(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final Path source = root.resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml");
		final Path scripts = root.resolve("dist/game/data/scripts");
		final PhantomAcquisitionQuestCatalog canonical = PhantomAcquisitionQuestCatalog.load(source, scripts);
		final String lf = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
		final List<Path> temporary = new ArrayList<>();
		try
		{
			final List<Path> variants = eolVariants(context, source, "goal039-hash-eol-", temporary);
			for (Path variant : variants)
			{
				final PhantomAcquisitionQuestCatalog loaded = PhantomAcquisitionQuestCatalog.load(variant, scripts);
				PhantomAssertions.assertEquals(canonical.catalogHash(), loaded.catalogHash(), "LF/CRLF/CR catalogHash identity differs.");
				PhantomAssertions.assertEquals(canonical.authorityHash(), loaded.authorityHash(), "LF/CRLF/CR authorityHash identity differs.");
			}
			PhantomAssertions.assertFalse(sha256(Files.readAllBytes(variants.get(0))).equals(sha256(Files.readAllBytes(variants.get(1)))), "Raw LF and CRLF hashes unexpectedly agree.");

			final Path contentMutation = writeTemporary(context, "goal039-hash-content-", lf.replace("?>\n", "?>\n<!--content-mutation-->\n").getBytes(StandardCharsets.UTF_8), temporary);
			final Path whitespaceMutation = writeTemporary(context, "goal039-hash-whitespace-", lf.replace("\t<rule", "\t <rule").getBytes(StandardCharsets.UTF_8), temporary);
			final String withoutTrailing = lf.endsWith("\n") ? lf.substring(0, lf.length() - 1) : lf;
			final Path trailingMutation = writeTemporary(context, "goal039-hash-trailing-", withoutTrailing.getBytes(StandardCharsets.UTF_8), temporary);
			PhantomAssertions.assertFalse(canonical.catalogHash().equals(PhantomAcquisitionQuestCatalog.load(contentMutation, scripts).catalogHash()), "Content mutation retained the canonical hash.");
			PhantomAssertions.assertFalse(canonical.catalogHash().equals(PhantomAcquisitionQuestCatalog.load(whitespaceMutation, scripts).catalogHash()), "Non-EOL whitespace was ignored.");
			PhantomAssertions.assertFalse(canonical.catalogHash().equals(PhantomAcquisitionQuestCatalog.load(trailingMutation, scripts).catalogHash()), "Trailing newline add/remove was ignored.");

			final byte[] sourceBytes = Files.readAllBytes(source);
			final byte[] bom = new byte[sourceBytes.length + 3];
			bom[0] = (byte) 0xef;
			bom[1] = (byte) 0xbb;
			bom[2] = (byte) 0xbf;
			System.arraycopy(sourceBytes, 0, bom, 3, sourceBytes.length);
			final Path bomPath = writeTemporary(context, "goal039-hash-bom-", bom, temporary);
			PhantomAssertions.assertFalse(canonical.catalogHash().equals(PhantomAcquisitionQuestCatalog.load(bomPath, scripts).catalogHash()), "UTF-8 BOM was silently ignored.");
			final Path malformed = writeTemporary(context, "goal039-hash-malformed-", new byte[]
			{
				(byte) 0xc3,
				(byte) 0x28
			}, temporary);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomAcquisitionQuestCatalog.load(malformed, scripts), "Malformed UTF-8 was accepted.");
		}
		finally
		{
			for (Path path : temporary)
			{
				Files.deleteIfExists(path);
			}
		}
		context.record("goal039.canonicalHashControls", "LF=CRLF=CR;negative=content,whitespace,trailing-newline,malformed,bom;raw-diff=true");
	}

	private void testCanonicalActivePins(PhantomTestContext context) throws Exception
	{
		final Path moduleRoot = context.moduleRoot();
		final Path gameRoot = moduleRoot.resolve("dist/game");
		final Path acquisitionPath = gameRoot.resolve("data/phantoms/acquisition/high-five-quest-collection-v1.xml");
		final Path supportedPath = gameRoot.resolve("data/phantoms/quests/high-five-supported-content-v1.xml");
		final PhantomAcquisitionQuestCatalog acquisition = PhantomAcquisitionQuestCatalog.load(acquisitionPath, gameRoot.resolve("data/scripts"));
		final PhantomQuestInstanceCatalog supported = PhantomQuestInstanceCatalog.load(supportedPath, gameRoot);
		final List<PinRef> refs = new ArrayList<>();
		for (Rule rule : acquisition.rules())
		{
			refs.add(new PinRef("data/scripts/" + rule.scriptPath(), rule.scriptHash()));
		}
		for (PhantomQuestInstanceCatalog.Content content : supported.contents())
		{
			for (PhantomQuestInstanceCatalog.Owner owner : content.owners())
			{
				refs.add(new PinRef(owner.sourcePath(), owner.sourceHash()));
			}
			for (PhantomQuestInstanceCatalog.SourceRef source : content.sources())
			{
				refs.add(new PinRef(source.path(), source.sha256()));
			}
		}
		PhantomAssertions.assertEquals(13, refs.size(), "Active source-pin reference count changed.");
		final Map<String, String> unique = new LinkedHashMap<>();
		for (PinRef ref : refs)
		{
			final String prior = unique.putIfAbsent(ref.path(), ref.expectedHash());
			PhantomAssertions.assertTrue((prior == null) || prior.equals(ref.expectedHash()), "Active duplicate path has conflicting pins: " + ref.path());
			PhantomAssertions.assertTrue(Files.isRegularFile(gameRoot.resolve(ref.path())), "Active source path is absent: " + ref.path());
		}
		PhantomAssertions.assertEquals(10, unique.size(), "Active unique source-pin path count changed.");

		final List<Path> fixtureRoots = new ArrayList<>();
		try
		{
			final List<String> separators = List.of("\n", "\r\n", "\r");
			for (int index = 0; index < separators.size(); index++)
			{
				final Path fixtureRoot = Files.createTempDirectory(context.reportsDirectory(), "goal039-pin-fixture-" + index + '-');
				fixtureRoots.add(fixtureRoot);
				for (String relative : unique.keySet())
				{
					writeEolVariant(gameRoot.resolve(relative), fixtureRoot.resolve(relative), separators.get(index));
				}
				final Path supportedFixture = fixtureRoot.resolve("data/phantoms/quests/high-five-supported-content-v1.xml");
				writeEolVariant(supportedPath, supportedFixture, separators.get(index));
				final PhantomAcquisitionQuestCatalog acquisitionVariant = PhantomAcquisitionQuestCatalog.load(fixtureRoot.resolve("data/phantoms/acquisition/high-five-quest-collection-v1.xml"), fixtureRoot.resolve("data/scripts"));
				final PhantomQuestInstanceCatalog supportedVariant = PhantomQuestInstanceCatalog.load(supportedFixture, fixtureRoot);
				PhantomAssertions.assertEquals(acquisition.catalogHash(), acquisitionVariant.catalogHash(), "Goal021 catalogHash changed across EOL archive fixtures.");
				PhantomAssertions.assertEquals(acquisition.authorityHash(), acquisitionVariant.authorityHash(), "Goal021 authorityHash changed across EOL archive fixtures.");
				PhantomAssertions.assertEquals(supported.catalogHash(), supportedVariant.catalogHash(), "Goal036 catalogHash changed across EOL archive fixtures.");
				PhantomAssertions.assertEquals(supported.authorityHash(), supportedVariant.authorityHash(), "Goal036 authorityHash changed across EOL archive fixtures.");
			}
			PhantomAssertions.assertFalse(sha256(Files.readAllBytes(fixtureRoots.get(0).resolve("data/phantoms/acquisition/high-five-quest-collection-v1.xml"))).equals(sha256(Files.readAllBytes(fixtureRoots.get(1).resolve("data/phantoms/acquisition/high-five-quest-collection-v1.xml")))), "Goal021 LF/CRLF raw hashes unexpectedly agree.");
			PhantomAssertions.assertFalse(sha256(Files.readAllBytes(fixtureRoots.get(0).resolve("data/phantoms/quests/high-five-supported-content-v1.xml"))).equals(sha256(Files.readAllBytes(fixtureRoots.get(1).resolve("data/phantoms/quests/high-five-supported-content-v1.xml")))), "Goal036 LF/CRLF raw hashes unexpectedly agree.");
		}
		finally
		{
			for (Path path : fixtureRoots)
			{
				deleteTree(path);
			}
		}
		context.record("goal039.activePinParity", "references=13/13,unique=10/10,representations=working+LF+CRLF+CR");
		context.record("goal039.goal021CatalogHash", acquisition.catalogHash());
		context.record("goal039.goal021AuthorityHash", acquisition.authorityHash());
		context.record("goal039.goal036CatalogHash", supported.catalogHash());
		context.record("goal039.goal036AuthorityHash", supported.authorityHash());
	}

	private void testStaleQuestBindingMigration(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final PhantomAcquisitionQuestCatalog catalog = PhantomAcquisitionQuestCatalog.load(root.resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml"), root.resolve("dist/game/data/scripts"));
		final List<String> oldRuleHashes = catalog.rules().stream().map(rule -> legacyRuleHash(rule, OLD_QUEST_SCRIPT_HASHES.get(rule.id()))).toList();
		final String oldAuthorityHash = digest("QUEST_COLLECTION_V1", OLD_ACQUISITION_CATALOG_HASH, oldRuleHashes);
		final Rule currentRule = catalog.rule("q00102-dryads-tear").orElseThrow();
		final QuestBinding oldBinding = new QuestBinding(currentRule.id(), oldRuleHashes.getFirst(), currentRule.questId(), currentRule.questName(), OLD_QUEST_SCRIPT_HASHES.get(currentRule.id()), currentRule.requiredState(), currentRule.allowedConds().getFirst(), currentRule.questItemId(), currentRule.itemCap(), currentRule.targetNpcIds().getFirst(), 0, 0, oldAuthorityHash);
		final QuestBinding currentBinding = new QuestBinding(currentRule.id(), currentRule.ruleHash(), currentRule.questId(), currentRule.questName(), currentRule.scriptHash(), currentRule.requiredState(), currentRule.allowedConds().getFirst(), currentRule.questItemId(), currentRule.itemCap(), currentRule.targetNpcIds().getFirst(), 0, 0, catalog.authorityHash());
		PhantomAssertions.assertFalse(oldBinding.ruleHash().equals(currentBinding.ruleHash()), "Canonical migration preserved the old rule identity.");
		PhantomAssertions.assertFalse(oldBinding.scriptHash().equals(currentBinding.scriptHash()), "Canonical migration preserved the old script identity.");
		PhantomAssertions.assertFalse(oldBinding.authorityHash().equals(currentBinding.authorityHash()), "Canonical migration preserved the old authority identity.");
		final boolean oldAccepted = catalog.rule(oldBinding.ruleId()).filter(rule -> rule.ruleHash().equals(oldBinding.ruleHash()) && rule.scriptHash().equals(oldBinding.scriptHash()) && oldBinding.authorityHash().equals(catalog.authorityHash())).isPresent();
		final boolean currentAccepted = catalog.rule(currentBinding.ruleId()).filter(rule -> rule.ruleHash().equals(currentBinding.ruleHash()) && rule.scriptHash().equals(currentBinding.scriptHash()) && currentBinding.authorityHash().equals(catalog.authorityHash())).isPresent();
		PhantomAssertions.assertFalse(oldAccepted, "Pre-migration QuestBinding remained executable.");
		PhantomAssertions.assertTrue(currentAccepted, "Current planner identity is not executable.");

		final String background = read(root, "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java");
		final int authorityGuard = background.indexOf("!quest.authorityHash().equals(_quests.authorityHash())");
		final int staleReplan = background.indexOf("OperationResult.replan(\"quest.script_stale\")", authorityGuard);
		final int firstQuestRead = background.indexOf("readAcquisitionQuestRows", authorityGuard);
		PhantomAssertions.assertTrue((authorityGuard >= 0) && (staleReplan > authorityGuard) && (firstQuestRead > staleReplan), "Background stale QuestBinding is not rejected before quest-row/item mutation work.");
		final String planner = read(root, "java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionSourcePlanner.java");
		PhantomAssertions.assertTrue(planner.contains("rule.ruleHash(), rule.scriptHash()") && planner.contains("0, _quests.authorityHash()"), "Goal021 planner does not bind current rule/script/authority identity.");
		PhantomAssertions.assertFalse(background.contains(OLD_ACQUISITION_CATALOG_HASH) || planner.contains(OLD_ACQUISITION_CATALOG_HASH), "Production retained a pre-migration compatibility alias.");
		context.record("goal039.questBindingMigration", "old=fail-closed-before-read,current=replanned,no-alias,no-db-migration");
	}

	private static List<Path> eolVariants(PhantomTestContext context, Path source, String prefix, List<Path> temporary) throws Exception
	{
		final String lf = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
		final List<String> values = List.of(lf, lf.replace("\n", "\r\n"), lf.replace('\n', '\r'));
		final List<Path> result = new ArrayList<>();
		for (int index = 0; index < values.size(); index++)
		{
			final Path path = Files.createTempFile(context.reportsDirectory(), prefix + index + '-', ".xml");
			Files.writeString(path, values.get(index), StandardCharsets.UTF_8);
			temporary.add(path);
			result.add(path);
		}
		return List.copyOf(result);
	}

	private static Path writeTemporary(PhantomTestContext context, String prefix, byte[] bytes, List<Path> temporary) throws Exception
	{
		final Path path = Files.createTempFile(context.reportsDirectory(), prefix, ".xml");
		Files.write(path, bytes);
		temporary.add(path);
		return path;
	}

	private static void writeEolVariant(Path source, Path target, String separator) throws Exception
	{
		final String lf = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
		Files.createDirectories(target.getParent());
		Files.writeString(target, "\n".equals(separator) ? lf : lf.replace("\n", separator), StandardCharsets.UTF_8);
	}

	private static void deleteTree(Path root) throws Exception
	{
		if (Files.notExists(root))
		{
			return;
		}
		try (Stream<Path> paths = Files.walk(root))
		{
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
			{
				Files.delete(path);
			}
		}
	}

	private static String legacyRuleHash(Rule rule, String scriptHash)
	{
		final String identity = String.join("\u0000", rule.id(), Integer.toString(rule.questId()), rule.questName(), rule.scriptPath(), scriptHash, rule.requiredState(), rule.allowedConds().toString(), rule.targetNpcIds().toString(), Integer.toString(rule.questItemId()), rule.grantShape().name(), rule.chanceKind().name(), Integer.toString(rule.rollBound()), Integer.toString(rule.rollThreshold()), Integer.toString(rule.minimumCount()), Integer.toString(rule.maximumCount()), Integer.toString(rule.itemCap()), rule.summonPolicy().name(), rule.partyPolicy().name(), Boolean.toString(rule.registeredQuestItem()), rule.expectedVars().toString(), rule.sourceRefs().toString());
		return digest("QUEST_RULE_V1", identity);
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private void testFinalStatusAndFreeze(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		for (String path : List.of(
			"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
			"docs/PHANTOM_BOTS_ROADMAP.md",
			"docs/phantoms/PHANTOM_CURRENT_STATUS.md",
			"docs/phantoms/NEW_DIALOG_START_MESSAGE.txt"))
		{
			final String text = read(root, path);
			PhantomAssertions.assertTrue(text.contains(FINAL_MARKER), "Canonical Goal039 document lacks the final marker: " + path);
			PhantomAssertions.assertTrue(text.contains("Goal039") && text.contains("ACCEPT"), "Canonical Goal039 document lacks ACCEPT: " + path);
			PhantomAssertions.assertFalse(GOAL039_NOT_STARTED.matcher(text).find(), "Canonical Goal039 document retained NOT_STARTED: " + path);
			for (int goal = 34; goal <= 38; goal++)
			{
				PhantomAssertions.assertTrue(text.contains("Goal0" + goal) && text.contains("SUCCESS"), "Canonical Goal039 document lost Goal0" + goal + " SUCCESS: " + path);
			}
			PhantomAssertions.assertTrue(text.contains("Goal040"), "Canonical Goal039 document omits the no-Goal040 freeze boundary: " + path);
		}

		final String freeze = read(root, "docs/phantoms/PHANTOM_FEATURE_COMPLETE_FREEZE.md");
		for (String token : List.of(
			FINAL_MARKER,
			"Goal039",
			"ACCEPT",
			REQUIRED_PARENT,
			"goal039-full-vision-coverage.tsv",
			"039-final-full-vision-release-gate.md",
			"l2jmobiush5_phantom_test",
			"production DB",
			"No Goal040",
			"Giran",
			"Q102/Q152",
			"Kamaloka 57",
			"Pailaka",
			"open-domain",
			"geodata"))
		{
			PhantomAssertions.assertTrue(freeze.contains(token), "Goal039 freeze document omits required boundary/provenance: " + token);
		}
		context.record("goal039.finalMarker", FINAL_MARKER);
		context.record("goal039.nextGoal", "none");
	}

	private void testFinalReportProvenance(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String report = read(root, "docs/phantoms/reports/039-final-full-vision-release-gate.md");
		for (String token : List.of(
			"Status: SUCCESS / ACCEPT",
			FINAL_MARKER,
			REQUIRED_PARENT,
			"historical Goal030 matrix: 20/20",
			"final declared-scope matrix: 28/28",
			"Goal033/033A",
			"Goal035",
			"Goal036",
			"Goal037",
			"Goal038",
			"Goal029",
			"Goal030 rollback",
			"gen1",
			"gen2",
			"cleanup.forced=false",
			"orphans.none=true",
			"working.integrity=true",
			"l2jmobiush5_phantom_test",
			"production DB used: NO",
			"prepare-phantom-test-db: NOT RUN",
			"No automatic Goal040"))
		{
			PhantomAssertions.assertTrue(report.contains(token), "Goal039 report omits required evidence token: " + token);
		}
		PhantomAssertions.assertTrue(FINAL_JAR.matcher(report).find(), "Goal039 report lacks final JAR SHA-256/bytes.");
		PhantomAssertions.assertTrue(REAL_RUN.matcher(report).find(), "Goal039 report lacks a fresh Goal034 run ID.");
		final String freeze = read(root, "docs/phantoms/PHANTOM_FEATURE_COMPLETE_FREEZE.md");
		PhantomAssertions.assertTrue(FINAL_JAR.matcher(freeze).find(), "Goal039 freeze lacks final JAR SHA-256/bytes.");
		PhantomAssertions.assertTrue(REAL_RUN.matcher(freeze).find(), "Goal039 freeze lacks the fresh Goal034 run ID.");
		testMatrices(context);
		context.record("goal039.report", "SUCCESS/ACCEPT");
	}

	private static Map<String, HistoricalRow> parseHistorical(List<String> lines)
	{
		final Map<String, HistoricalRow> rows = new LinkedHashMap<>();
		for (String line : lines.subList(1, lines.size()))
		{
			final String[] columns = line.split("\\t", -1);
			PhantomAssertions.assertEquals(7, columns.length, "Historical Goal030 matrix row is malformed.");
			final HistoricalRow row = new HistoricalRow(columns[0], columns[1], columns[2], columns[3]);
			PhantomAssertions.assertEquals(null, rows.putIfAbsent(row.domainId(), row), "Historical Goal030 matrix contains a duplicate domain.");
		}
		return rows;
	}

	private static int occurrences(String text, String needle)
	{
		int count = 0;
		int offset = 0;
		while ((offset = text.indexOf(needle, offset)) >= 0)
		{
			count++;
			offset += needle.length();
		}
		return count;
	}

	@SafeVarargs
	private static Set<String> configUnion(Set<String>... owners)
	{
		final Set<String> result = new HashSet<>();
		for (Set<String> owner : owners)
		{
			for (String key : owner)
			{
				if (!result.add(key))
				{
					throw new IllegalStateException("Phantom config key has multiple declared owners: " + key);
				}
			}
		}
		return Set.copyOf(result);
	}

	private static List<String> configKeys(String text)
	{
		final List<String> result = new ArrayList<>();
		final Matcher matcher = CONFIG_KEY.matcher(text);
		while (matcher.find())
		{
			result.add(matcher.group(1));
		}
		return List.copyOf(result);
	}

	private static Set<String> buildTargets(String build)
	{
		final Set<String> targets = new HashSet<>();
		final Matcher matcher = BUILD_TARGET.matcher(build);
		while (matcher.find())
		{
			targets.add(matcher.group(1));
		}
		return targets;
	}

	private static Map<String, Set<String>> targetDependencies(String build)
	{
		final Map<String, Set<String>> result = new LinkedHashMap<>();
		final Matcher matcher = TARGET_DEPENDS.matcher(build);
		while (matcher.find())
		{
			result.put(matcher.group(1), Set.of(matcher.group(2).split(",", -1)));
		}
		return result;
	}

	private static void validatePaths(Path root, String domain, String paths)
	{
		for (String path : paths.split(";", -1))
		{
			PhantomAssertions.assertFalse(path.isBlank(), "Goal039 matrix has a blank owner path: " + domain);
			final Path resolved = root.resolve(path).normalize();
			PhantomAssertions.assertTrue(resolved.startsWith(root) && Files.exists(resolved), "Goal039 owner path is missing: " + path);
		}
	}

	private static void validateFreshTargets(FinalRow row, Set<String> targets)
	{
		final String[] evidence = row.freshEvidence().split(";", -1);
		PhantomAssertions.assertTrue(evidence.length > 0, "Goal039 row has no fresh evidence: " + row.domainId());
		for (String target : evidence)
		{
			PhantomAssertions.assertFalse(target.isBlank(), "Goal039 row has a blank fresh target: " + row.domainId());
			PhantomAssertions.assertFalse("prepare-phantom-test-db".equals(target), "Goal039 matrix schedules destructive DB preparation.");
			PhantomAssertions.assertTrue(targets.contains(target), "Goal039 fresh target is absent from build.xml: " + target);
		}
	}

	private static void validatePredecessorEvidence(Path root, FinalRow row, Set<String> targets)
	{
		final int separator = row.predecessorEvidence().indexOf('@');
		PhantomAssertions.assertTrue(separator > 0, "Goal039 predecessor evidence is malformed: " + row.domainId());
		final String targetPart = row.predecessorEvidence().substring(0, separator);
		final String reportPart = row.predecessorEvidence().substring(separator + 1);
		for (String target : targetPart.split(";", -1))
		{
			PhantomAssertions.assertTrue(targets.contains(target), "Goal039 predecessor target is absent from build.xml: " + target);
		}
		for (String report : reportPart.split(";", -1))
		{
			PhantomAssertions.assertTrue(Files.isRegularFile(root.resolve(report)), "Goal039 predecessor report is missing: " + report);
		}
	}

	private static String read(Path root, String relative) throws Exception
	{
		return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private record HistoricalRow(String domainId, String goalLineage, String ownerPaths, String antTargets)
	{
	}

	private record FinalRow(String domainId, String goalLineage, String ownerPaths, String freshEvidence, String predecessorEvidence, String evidenceKind, String finalStatus, String claimBoundary)
	{
	}

	private record PinRef(String path, String expectedHash)
	{
	}
}
