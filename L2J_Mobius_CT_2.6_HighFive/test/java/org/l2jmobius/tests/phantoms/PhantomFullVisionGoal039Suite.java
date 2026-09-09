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

public final class PhantomFullVisionGoal039Suite implements PhantomTestSuite
{
	public enum Mode
	{
		STATIC,
		DOCUMENTATION
	}

	private static final long SEED = 39003901L;
	private static final String REQUIRED_PARENT = "ba692bd0a5e86fbbfe5f87c9c851f3a629e4d5a1";
	private static final String FINAL_MARKER = "FEATURE_COMPLETE_FOR_DECLARED_SCOPE";
	private static final String HISTORICAL_MATRIX = "test/resources/phantoms/release/goal030-release-coverage.tsv";
	private static final String HISTORICAL_MATRIX_SHA256 = "fd891490e7bed44dba7d33f1b72d5c1de46ff67003190b31d22b7dd96206e64e";
	private static final String FINAL_MATRIX = "test/resources/phantoms/release/goal039-full-vision-coverage.tsv";
	private static final String FINAL_HEADER = "domain_id\tgoal_lineage\tauthoritative_owner_paths\tfresh_goal039_evidence\tpredecessor_evidence\tevidence_kind\tfinal_status\tclaim_boundary";
	private static final Set<String> FINAL_STATUSES = Set.of("PASS", "ACCEPT", "BLOCKED", "NOT_RUN_BLOCKED");
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
			PhantomAssertions.assertEquals("BLOCKED", rows.get("living-population-ecology").finalStatus(), "Goal039 blocked matrix does not identify the first failed domain.");
			for (String domain : POST_GOAL030_DOMAINS.subList(2, POST_GOAL030_DOMAINS.size()))
			{
				PhantomAssertions.assertEquals("NOT_RUN_BLOCKED", rows.get(domain).finalStatus(), "Goal039 blocked matrix overclaims a later domain: " + domain);
			}
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
}
