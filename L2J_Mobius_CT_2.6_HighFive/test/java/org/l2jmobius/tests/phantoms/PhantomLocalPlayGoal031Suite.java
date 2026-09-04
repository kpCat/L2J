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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.Check;
import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.DatabaseSettings;
import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.Level;
import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.Request;
import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.Result;
import org.l2jmobius.tests.phantoms.PhantomLocalPlayPreflight.SchemaSnapshot;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard.ValidatedSettings;

public final class PhantomLocalPlayGoal031Suite implements PhantomTestSuite
{
	public enum Mode
	{
		PREFLIGHT,
		DOCUMENTATION
	}

	private final Mode _mode;

	public PhantomLocalPlayGoal031Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "phantom-local-play-goal031-" + _mode.name().toLowerCase();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.PREFLIGHT)
		{
			registry.add("safe-shipped-and-runnable-preset", this::testConfigs);
			registry.add("invalid-target-cap-fails", this::testInvalidPreset);
			registry.add("authoritative-data-completeness", this::testData);
			registry.add("guarded-schema-and-precise-missing-object", this::testSchema);
			registry.add("geodata-degraded-warning", this::testGeodata);
			registry.add("password-never-rendered", this::testSecretRedaction);
			registry.add("database-installer-discovers-canonical-schema", this::testInstaller);
			registry.add("gameserver-startup-order", this::testStartupOrder);
		}
		else
		{
			registry.add("quickstart-and-current-status", this::testDocumentsExist);
			registry.add("release-matrix-20-covered-zero-pending", this::testReleaseMatrix);
			registry.add("master-plan-reconciliation", this::testMasterPlan);
			registry.add("new-dialog-handoff", this::testHandoff);
		}
	}

	private void testConfigs(PhantomTestContext context)
	{
		final Path root = context.moduleRoot();
		final Path shipped = root.resolve("dist/game/config/Custom/PhantomPlayers.ini");
		final Path preset = root.resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini");
		assertNoFailures(PhantomLocalPlayPreflight.validateShippedConfig(shipped));
		assertNoFailures(PhantomLocalPlayPreflight.validateRuntimeConfig(shipped));
		assertNoFailures(PhantomLocalPlayPreflight.validatePresetConfig(preset));
		final List<Check> appliedPreset = PhantomLocalPlayPreflight.validateRuntimeConfig(preset);
		PhantomAssertions.assertTrue(appliedPreset.stream().anyMatch(check -> (check.level() == Level.WARNING) && "RUNTIME_CONFIG_LOCAL_PLAY".equals(check.code())), "Applied local-play preset is not accepted as an explicit runtime warning.");
		context.record("goal031.preset", "population=10,active=5,shipped=False/0/0,runtime-preset=warning");
	}

	private void testInvalidPreset(PhantomTestContext context) throws Exception
	{
		final Path file = Files.createTempFile("phantom-goal031-invalid-", ".ini");
		try
		{
			Files.writeString(file, "EnablePhantomSystem=True\nMaxMaterializedPhantoms=4\nMaxScheduledPhantomProfiles=10\nPhantomSchedulerPulseMillis=100\nPhantomSchedulerProfilesPerPulse=128\nPhantomPopulationTarget=10\nPhantomPopulationActiveTarget=5\n", StandardCharsets.UTF_8);
			final List<Check> checks = PhantomLocalPlayPreflight.validatePresetConfig(file);
			PhantomAssertions.assertTrue(checks.stream().anyMatch(check -> (check.level() == Level.FAIL) && "PRESET_INVALID".equals(check.code())), "Invalid target/cap relation did not fail precisely.");
		}
		finally
		{
			Files.deleteIfExists(file);
		}
	}

	private void testData(PhantomTestContext context) throws Exception
	{
		assertNoFailures(PhantomLocalPlayPreflight.validateData(context.moduleRoot().resolve("dist/game/data/phantoms")));
		final Path missingRoot = Files.createTempDirectory("phantom-goal031-data-");
		try
		{
			final List<Check> missing = PhantomLocalPlayPreflight.validateData(missingRoot);
			PhantomAssertions.assertTrue(missing.stream().anyMatch(check -> (check.level() == Level.FAIL) && "acquisition/high-five-acquisition-v1.xml".equals(check.detail())), "Missing authoritative data failure did not name the exact file.");
		}
		finally
		{
			Files.deleteIfExists(missingRoot);
		}
	}
	private void testSchema(PhantomTestContext context) throws Exception
	{
		final ValidatedSettings guarded = guarded(context);
		final DatabaseSettings database = database(guarded);
		final SchemaSnapshot actual = PhantomLocalPlayPreflight.inspectSchema(database);
		assertNoFailures(PhantomLocalPlayPreflight.validateSchema(actual));
		final Set<String> withoutProfiles = new java.util.HashSet<>(actual.tables());
		withoutProfiles.remove("phantom_profiles");
		final List<Check> missing = PhantomLocalPlayPreflight.validateSchema(new SchemaSnapshot(withoutProfiles, actual.indexes(), actual.foreignKeys()));
		PhantomAssertions.assertTrue(missing.stream().anyMatch(check -> (check.level() == Level.FAIL) && "SCHEMA_TABLE_MISSING".equals(check.code()) && "phantom_profiles".equals(check.detail())), "Missing schema fixture did not identify phantom_profiles exactly.");
		context.record("goal031.schema", "tables=6,indexes=12,foreignKeys=5");
	}

	private void testGeodata(PhantomTestContext context) throws Exception
	{
		final Path empty = Files.createTempDirectory("phantom-goal031-geodata-");
		try
		{
			final List<Check> checks = PhantomLocalPlayPreflight.validateGeodata(empty);
			PhantomAssertions.assertTrue(checks.stream().anyMatch(check -> (check.level() == Level.WARNING) && "GEODATA_DEGRADED".equals(check.code())), "Missing geodata was not classified as an explicit degraded warning.");
		}
		finally
		{
			Files.deleteIfExists(empty);
		}
	}

	private void testSecretRedaction(PhantomTestContext context) throws Exception
	{
		final ValidatedSettings guarded = guarded(context);
		final String sentinel = "goal031-secret-must-not-leak";
		final Result result = PhantomLocalPlayPreflight.run(new Request(context.moduleRoot(), new DatabaseSettings(guarded.driver(), guarded.url(), guarded.login(), sentinel)));
		PhantomAssertions.assertFalse(result.render().contains(sentinel), "Preflight rendered the database password sentinel.");
	}

	private void testInstaller(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String installer = Files.readString(root.resolve("java/org/l2jmobius/tools/DatabaseInstaller.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(installer.contains("name.endsWith(\".sql\")") && installer.contains("Arrays.sort(sqlFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))"), "DatabaseInstaller no longer discovers and sorts every game SQL file.");
		final Path sqlRoot = root.resolve("dist/db_installer/sql/game");
		final List<String> phantomScripts;
		try (var files = Files.list(sqlRoot))
		{
			phantomScripts = files.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("phantom_") && name.endsWith(".sql")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
		}
		PhantomAssertions.assertEquals(List.of("phantom_profiles.sql", "phantom_reservations.sql", "phantom_reservations_checkpoint2.sql"), phantomScripts, "Canonical Phantom SQL discovery/order changed.");
		final String profiles = Files.readString(sqlRoot.resolve(phantomScripts.get(0)), StandardCharsets.UTF_8);
		final String reservations = Files.readString(sqlRoot.resolve(phantomScripts.get(1)), StandardCharsets.UTF_8);
		final String checkpoint2 = Files.readString(sqlRoot.resolve(phantomScripts.get(2)), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(count(profiles, "CREATE TABLE IF NOT EXISTS") == 2, "Profile schema must create two idempotent tables.");
		PhantomAssertions.assertTrue(count(reservations, "CREATE TABLE IF NOT EXISTS") == 3, "Reservation schema must create three idempotent tables.");
		PhantomAssertions.assertTrue((count(checkpoint2, "CREATE TABLE IF NOT EXISTS") == 1) && checkpoint2.contains("CREATE INDEX IF NOT EXISTS idx_phantom_economy_reservations_profile_operation"), "Checkpoint2 schema must create its table and idempotent index.");
		context.record("goal031.installer.order", String.join(" -> ", phantomScripts));
	}

	private void testStartupOrder(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/GameServer.java"), StandardCharsets.UTF_8);
		final String startCall = "PhantomSystem.startConfigured()";
		final int start = source.indexOf(startCall);
		final int scripts = source.indexOf("ScriptEngine.getInstance().executeScriptList()");
		final int siege = source.indexOf("CastleManager.getInstance().activateInstances()");
		final int offline = source.indexOf("OfflinePlayTable.getInstance().restoreOfflinePlayers()");
		final int restart = source.indexOf("ServerRestartManager.getInstance()", start);
		PhantomAssertions.assertTrue((scripts >= 0) && (scripts < siege) && (siege < offline) && (offline < start) && (start < restart), "GameServer Phantom startup must follow data/scripts/managers/offline restoration and precede scheduled restart managers.");
		PhantomAssertions.assertEquals(1, count(source, startCall), "GameServer must contain exactly one configured Phantom startup call.");
		context.record("goal031.startup.order", "scripts<siege<offline-restore<phantom-start<restart-managers");
	}
	private void testDocumentsExist(PhantomTestContext context) throws Exception
	{
		final Path docs = context.moduleRoot().resolve("docs/phantoms");
		final String quickstart = Files.readString(docs.resolve("PHANTOM_QUICKSTART_RU.md"), StandardCharsets.UTF_8);
		final String status = Files.readString(docs.resolve("PHANTOM_CURRENT_STATUS.md"), StandardCharsets.UTF_8);
		for (int section = 1; section <= 16; section++)
		{
			PhantomAssertions.assertTrue(quickstart.contains("## " + section + "."), "Quick-start is missing required section " + section + ".");
		}
		PhantomAssertions.assertTrue(quickstart.contains("phantom-local-play-preflight") && quickstart.contains("//phantom status") && quickstart.contains("//phantom drain") && quickstart.contains("//phantom disable"), "Quick-start is missing canonical preflight/status/rollback commands.");
		PhantomAssertions.assertTrue(status.contains("Goal030 accepted 20-domain release slice") && status.contains("original master-plan full vision"), "Current status does not separate release scope from the original full vision.");
		PhantomAssertions.assertTrue(status.contains("DEFERRED_NOT_IMPLEMENTED"), "Current status does not expose deferred gameplay scope.");
	}

	private void testReleaseMatrix(PhantomTestContext context) throws Exception
	{
		final List<String> lines = Files.readAllLines(context.moduleRoot().resolve("test/resources/phantoms/release/goal030-release-coverage.tsv"), StandardCharsets.UTF_8);
		PhantomAssertions.assertEquals(21, lines.size(), "Goal030 release matrix must contain one header and 20 domains.");
		for (String row : lines.subList(1, lines.size()))
		{
			final String[] columns = row.split("\\t", -1);
			PhantomAssertions.assertTrue((columns.length == 7) && columns[5].startsWith("COVERED_"), "Release matrix contains a pending or malformed domain: " + row);
		}
	}

	private void testMasterPlan(PhantomTestContext context) throws Exception
	{
		final String plan = Files.readString(context.moduleRoot().resolve("PHANTOM_DEVELOPMENT_MASTER_PLAN.md"), StandardCharsets.UTF_8);
		final int goal027 = plan.lastIndexOf("### 027. Clans, alliances и wars");
		final int goal028 = plan.indexOf("### 028. Sieges", goal027);
		final String current027 = plan.substring(goal027, goal028);
		PhantomAssertions.assertTrue(current027.contains("Status: `ACCEPT`") && !current027.contains("Status: `IN_PROGRESS`"), "Current Goal027 status remains stale.");
		PhantomAssertions.assertTrue(plan.contains("Goal 031") && plan.contains("DEFERRED_NOT_IMPLEMENTED") && plan.contains("Goal028") && plan.contains("Goal029"), "Master-plan reconciliation is incomplete.");
		PhantomAssertions.assertFalse(plan.contains("Sieges — `IMPLEMENTED_AND_RELEASE_COVERED`") || plan.contains("Kamaloka — `IMPLEMENTED_AND_RELEASE_COVERED`") || plan.contains("Pailaka — `IMPLEMENTED_AND_RELEASE_COVERED`"), "Master plan claims deferred gameplay without evidence.");
	}

	private void testHandoff(PhantomTestContext context) throws Exception
	{
		final String handoff = Files.readString(context.moduleRoot().resolve("docs/phantoms/NEW_DIALOG_START_MESSAGE.txt"), StandardCharsets.UTF_8);
		PhantomAssertions.assertFalse(handoff.contains("Task001") || handoff.contains("Task 001"), "New-dialog handoff still directs work to Task001.");
		PhantomAssertions.assertTrue(handoff.contains("feature/phantom-world") && handoff.contains("Goal030") && handoff.contains("Goal031") && handoff.contains("PHANTOM_QUICKSTART_RU.md") && handoff.contains("PHANTOM_CURRENT_STATUS.md"), "New-dialog handoff lacks the current branch/release/readiness anchors.");
	}

	private static ValidatedSettings guarded(PhantomTestContext context) throws Exception
	{
		final Path config = Path.of(System.getProperty("phantom.test.config", "")).toAbsolutePath().normalize();
		return PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
	}

	private static DatabaseSettings database(ValidatedSettings settings)
	{
		return new DatabaseSettings(settings.driver(), settings.url(), settings.login(), settings.password());
	}

	private static void assertNoFailures(List<Check> checks)
	{
		final List<Check> failures = checks.stream().filter(check -> check.level() == Level.FAIL).toList();
		PhantomAssertions.assertTrue(failures.isEmpty(), "Unexpected preflight failures: " + failures);
	}

	private static int count(String source, String needle)
	{
		int result = 0;
		int from = 0;
		while ((from = source.indexOf(needle, from)) >= 0)
		{
			result++;
			from += needle.length();
		}
		return result;
	}
}