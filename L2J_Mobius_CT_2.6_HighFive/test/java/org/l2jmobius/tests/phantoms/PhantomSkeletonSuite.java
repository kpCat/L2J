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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.ServiceState;

public final class PhantomSkeletonSuite implements PhantomTestSuite
{
	private Path _testDirectory;

	@Override
	public String id()
	{
		return "phantom-skeleton";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_testDirectory = context.moduleRoot().resolve(".phantom-local/skeleton-" + ProcessHandle.current().pid());
		Files.createDirectories(_testDirectory);
		ThreadPool.init();
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			deleteTree(_testDirectory);
		}
		finally
		{
			ThreadPool.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("config-canonical-disabled", context ->
		{
			final Path canonical = context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini");
			final var settings = PhantomPlayersConfig.read(canonical);
			PhantomAssertions.assertFalse(settings.enabled(), "Canonical Phantom system flag must be false.");
			PhantomAssertions.assertFalse(settings.diagnosticsEnabled(), "Canonical Phantom diagnostics flag must be false.");
		});
		registry.add("config-diagnostics-fail-closed", _ ->
		{
			final var malformed = readConfig("diagnostics-malformed.ini", enabledConfig("EnablePhantomDiagnostics = sometimes\n"));
			PhantomAssertions.assertTrue(malformed.enabled(), "Valid system flag was not recognized.");
			PhantomAssertions.assertFalse(malformed.diagnosticsEnabled(), "Malformed diagnostics flag enabled tracing.");
			final var systemDisabled = readConfig("diagnostics-with-system-disabled.ini", "EnablePhantomSystem = false\nEnablePhantomDiagnostics = true\n");
			PhantomAssertions.assertFalse(systemDisabled.diagnosticsEnabled(), "Diagnostics became effective while the system was disabled.");
		});
		registry.add("config-malformed-and-blank-disabled", _ ->
		{
			final var blank = readConfig("blank.ini", "EnablePhantomSystem =   \nEnablePhantomDiagnostics = true\n");
			PhantomAssertions.assertFalse(blank.enabled(), "Blank system flag enabled Phantom World.");
			final var malformed = readConfig("malformed.ini", "EnablePhantomSystem = yes\nEnablePhantomDiagnostics = true\n");
			PhantomAssertions.assertFalse(malformed.enabled(), "Malformed system flag enabled Phantom World.");
			PhantomAssertions.assertFalse(malformed.diagnosticsEnabled(), "Diagnostics bypassed the malformed system flag.");
		});
		registry.add("config-missing-disabled", _ ->
		{
			final var settings = PhantomPlayersConfig.read(_testDirectory.resolve("missing.ini"));
			PhantomAssertions.assertFalse(settings.enabled(), "Missing config enabled Phantom World.");
			PhantomAssertions.assertFalse(settings.diagnosticsEnabled(), "Missing config enabled Phantom diagnostics.");
		});
		registry.add("config-misspelled-key-disabled", _ ->
		{
			final var settings = readConfig("misspelled.ini", "EnablePhantomSystems = true\nEnablePhantomDiagnostics = true\n");
			PhantomAssertions.assertFalse(settings.enabled(), "Misspelled system key enabled Phantom World.");
			PhantomAssertions.assertFalse(settings.diagnosticsEnabled(), "Diagnostics bypassed a missing canonical system key.");
		});
		registry.add("config-strict-case-insensitive-booleans", _ ->
		{
			final var enabled = readConfig("true.ini", enabledConfig("EnablePhantomSystem = TrUe\nEnablePhantomDiagnostics = TRUE\n"));
			PhantomAssertions.assertTrue(enabled.enabled(), "Case-insensitive true was not recognized.");
			PhantomAssertions.assertTrue(enabled.diagnosticsEnabled(), "Case-insensitive diagnostics true was not recognized.");
			final var disabled = readConfig("false.ini", "EnablePhantomSystem = FaLsE\nEnablePhantomDiagnostics = FALSE\n");
			PhantomAssertions.assertFalse(disabled.enabled(), "Case-insensitive false was not recognized.");
		});
		registry.add("configured-disabled-no-instance", _ ->
		{
			PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Configured shutdown created or found an unexpected instance.");
			PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Configured instance existed before disabled start.");
			PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "Disabled configured start returned true.");
			PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Disabled configured start created an instance.");
			PhantomAssertions.assertFalse(PhantomSystem.shutdownIfStarted(), "Disabled configured shutdown created an instance.");
		});
		registry.add("disabled-lifecycle-inert", _ ->
		{
			final Set<Long> before = nonDaemonThreadIds();
			final PhantomSystem system = new PhantomSystem(new PhantomPlayersConfig.Settings(false, true));
			PhantomAssertions.assertEquals(PhantomSystem.State.NEW, system.snapshot().state(), "Direct disabled system did not begin in NEW.");
			PhantomAssertions.assertFalse(system.start(), "Direct disabled system start returned true.");
			final var disabled = system.snapshot();
			PhantomAssertions.assertEquals(PhantomSystem.State.DISABLED, disabled.state(), "Direct disabled system did not enter DISABLED.");
			PhantomAssertions.assertFalse(disabled.settings().diagnosticsEnabled(), "Disabled settings retained effective diagnostics.");
			PhantomAssertions.assertFalse(disabled.scheduler().running(), "Disabled scheduler became active.");
			PhantomAssertions.assertEquals(0, disabled.scheduler().ready(), "Disabled scheduler contains ready work.");
			PhantomAssertions.assertEquals(0, disabled.scheduler().due(), "Disabled scheduler contains due work.");
			PhantomAssertions.assertEquals(0, disabled.scheduler().capacity(), "Disabled system allocated scheduler capacity.");
			PhantomAssertions.assertEquals(0, disabled.scheduler().scheduledTaskCount(), "Disabled system reports scheduled work.");
			PhantomAssertions.assertEquals(ServiceState.STOPPED, disabled.navigation().state(), "Disabled system created a navigation service.");
			PhantomAssertions.assertEquals(0, disabled.navigation().queueCapacity(), "Disabled system allocated a navigation queue.");
			PhantomAssertions.assertEquals(0, disabled.navigation().cacheCapacity(), "Disabled system allocated a navigation cache.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService.State.STOPPED, disabled.gameKnowledge().state(), "Disabled system constructed Game Knowledge.");
			PhantomAssertions.assertEquals(0L, disabled.gameKnowledge().metrics().buildsStarted(), "Disabled system scanned Game Knowledge sources.");
			PhantomAssertions.assertEquals(0, disabled.gameKnowledge().counts().items(), "Disabled system retained Game Knowledge facts.");
			PhantomAssertions.assertTrue(disabled.metrics().isZero(), "Disabled system changed metrics.");
			PhantomAssertions.assertEquals(List.of(), disabled.trace().events(), "Disabled system trace is not empty.");
			PhantomAssertions.assertEquals(0, disabled.trace().capacity(), "Disabled system allocated trace capacity.");
			PhantomAssertions.assertFalse(system.shutdown(), "Disabled shutdown reported a running instance.");
			PhantomAssertions.assertEquals(PhantomSystem.State.STOPPED, system.snapshot().state(), "Disabled system did not reach STOPPED.");
			PhantomAssertions.assertFalse(system.shutdown(), "Repeated disabled shutdown changed state.");
			PhantomAssertions.assertTrue(system.snapshot().metrics().isZero(), "Disabled lifecycle changed metrics.");
			assertNoNewNonDaemonThreads(before);
		});
		registry.add("enabled-lifecycle-inert", _ ->
		{
			final PhantomSystem system = new PhantomSystem(new PhantomPlayersConfig.Settings(true, false));
			PhantomAssertions.assertTrue(system.start(), "Enabled skeleton did not start.");
			final var running = system.snapshot();
			PhantomAssertions.assertEquals(PhantomSystem.State.RUNNING, running.state(), "Enabled skeleton did not enter RUNNING.");
			PhantomAssertions.assertEquals(1L, running.metrics().lifecycleStarts(), "Enabled skeleton start count mismatch.");
			PhantomAssertions.assertEquals(0L, running.metrics().lifecycleStops(), "Enabled skeleton stop count changed before shutdown.");
			PhantomAssertions.assertTrue(running.scheduler().running(), "Enabled scheduler is not running.");
			PhantomAssertions.assertEquals(0, running.scheduler().registered(), "Enabled scheduler auto-registered profiles.");
			PhantomAssertions.assertEquals(0, running.scheduler().ready(), "Enabled scheduler ready queue was not empty.");
			PhantomAssertions.assertEquals(0, running.scheduler().due(), "Enabled scheduler due set was not empty.");
			PhantomAssertions.assertEquals(PhantomPlayersConfig.DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES, running.scheduler().capacity(), "Enabled scheduler capacity mismatch.");
			PhantomAssertions.assertEquals(1, running.scheduler().scheduledTaskCount(), "Enabled scheduler did not own exactly one recurring pulse.");
			PhantomAssertions.assertEquals(ServiceState.RUNNING, running.navigation().state(), "Enabled navigation service is not running.");
			PhantomAssertions.assertEquals(0, running.navigation().activeRequests(), "Enabled navigation service created a request.");
			PhantomAssertions.assertEquals(0, running.navigation().currentWorkers(), "Enabled navigation service submitted a worker.");
			PhantomAssertions.assertEquals(0, running.navigation().cacheEntries(), "Enabled navigation service populated its cache.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService.State.RUNNING, running.gameKnowledge().state(), "Enabled inert Game Knowledge is not running.");
			PhantomAssertions.assertEquals(1L, running.gameKnowledge().metrics().buildsStarted(), "Enabled inert Game Knowledge did not build exactly once.");
			PhantomAssertions.assertEquals(1L, running.gameKnowledge().metrics().buildsCompleted(), "Enabled inert Game Knowledge did not publish exactly once.");
			PhantomAssertions.assertEquals(0, running.gameKnowledge().counts().items(), "Enabled inert Game Knowledge loaded production facts.");
			PhantomAssertions.assertEquals(0L, java.util.Arrays.stream(running.gameKnowledge().metrics().queriesByCategory()).sum(), "Enabled Game Knowledge issued an automatic query.");
			PhantomAssertions.assertFalse(system.start(), "Repeated enabled start was not a no-op.");
			PhantomAssertions.assertEquals(1L, system.snapshot().metrics().lifecycleStarts(), "Repeated start changed metrics.");
			PhantomAssertions.assertTrue(system.shutdown(), "Enabled skeleton did not stop.");
			final var stopped = system.snapshot();
			PhantomAssertions.assertEquals(PhantomSystem.State.STOPPED, stopped.state(), "Enabled skeleton did not reach STOPPED.");
			PhantomAssertions.assertEquals(1L, stopped.metrics().lifecycleStops(), "Enabled skeleton stop count mismatch.");
			PhantomAssertions.assertFalse(stopped.scheduler().running(), "Scheduler remained running after stop.");
			PhantomAssertions.assertEquals(ServiceState.STOPPED, stopped.navigation().state(), "Navigation service remained running after stop.");
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService.State.STOPPED, stopped.gameKnowledge().state(), "Game Knowledge remained running after stop.");
			PhantomAssertions.assertEquals(0, stopped.scheduler().ready(), "Ready queue was not cleared on stop.");
			PhantomAssertions.assertEquals(0, stopped.scheduler().due(), "Due set was not cleared on stop.");
			PhantomAssertions.assertFalse(system.shutdown(), "Repeated enabled shutdown was not a no-op.");
			PhantomAssertions.assertFalse(system.start(), "STOPPED system restarted.");
			PhantomAssertions.assertEquals(1L, system.snapshot().metrics().lifecycleStops(), "Repeated stop changed metrics.");
		});
		registry.add("config-scheduler-guards-fail-closed", _ ->
		{
			final var missing = readConfig("scheduler-missing.ini", "EnablePhantomSystem = true\nEnablePhantomDiagnostics = false\nMaxMaterializedPhantoms = 32\n");
			PhantomAssertions.assertFalse(missing.enabled(), "Missing scheduler settings did not fail closed.");
			final var tooSmall = readConfig("scheduler-capacity.ini", enabledConfig("MaxMaterializedPhantoms = 33\nMaxScheduledPhantomProfiles = 32\n"));
			PhantomAssertions.assertFalse(tooSmall.enabled(), "Scheduled capacity below materialization cap did not fail closed.");
			final var malformed = readConfig("scheduler-malformed.ini", enabledConfig("PhantomSchedulerPulseMillis = +100\n"));
			PhantomAssertions.assertFalse(malformed.enabled(), "Signed scheduler pulse did not fail closed.");
		});
		registry.add("trace-disabled-no-storage", _ ->
		{
			final PhantomMetrics metrics = new PhantomMetrics();
			final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(false, 4, 1, metrics);
			PhantomAssertions.assertFalse(trace.record("disabled.event"), "Disabled trace recorded an event.");
			final var snapshot = trace.snapshot();
			PhantomAssertions.assertFalse(snapshot.enabled(), "Disabled trace reports enabled.");
			PhantomAssertions.assertEquals(0, snapshot.capacity(), "Disabled trace allocated capacity.");
			PhantomAssertions.assertEquals(List.of(), snapshot.events(), "Disabled trace contains entries.");
			PhantomAssertions.assertTrue(metrics.snapshot().isZero(), "Disabled trace changed metrics.");
		});
		registry.add("trace-sampled-bounded-overwrite", _ ->
		{
			final PhantomMetrics metrics = new PhantomMetrics();
			final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(true, 2, 2, metrics);
			PhantomAssertions.assertFalse(trace.record("trace.1"), "First unsampled event was recorded.");
			PhantomAssertions.assertTrue(trace.record("trace.2"), "Second sampled event was not recorded.");
			PhantomAssertions.assertFalse(trace.record("trace.3"), "Third unsampled event was recorded.");
			PhantomAssertions.assertTrue(trace.record("trace.4"), "Fourth sampled event was not recorded.");
			PhantomAssertions.assertFalse(trace.record("trace.5"), "Fifth unsampled event was recorded.");
			PhantomAssertions.assertTrue(trace.record("trace.6"), "Sixth sampled event was not recorded.");
			final var snapshot = trace.snapshot();
			PhantomAssertions.assertEquals(2, snapshot.capacity(), "Trace capacity mismatch.");
			PhantomAssertions.assertEquals(2, snapshot.sampleEvery(), "Trace sample interval mismatch.");
			PhantomAssertions.assertEquals(6L, snapshot.attempts(), "Trace attempt count mismatch.");
			PhantomAssertions.assertEquals(List.of("trace.4", "trace.6"), snapshot.events(), "Trace did not retain the newest bounded events.");
			PhantomAssertions.assertEquals(3L, metrics.snapshot().traceRecorded(), "Trace recorded metric mismatch.");
			PhantomAssertions.assertEquals(1L, metrics.snapshot().traceDropped(), "Trace overwrite metric mismatch.");
		});
	}

	private PhantomPlayersConfig.Settings readConfig(String name, String content) throws IOException
	{
		final Path config = _testDirectory.resolve(name);
		Files.writeString(config, content, StandardCharsets.UTF_8);
		return PhantomPlayersConfig.read(config);
	}

	private static String enabledConfig(String overrides)
	{
		final StringBuilder config = new StringBuilder();
		config.append("EnablePhantomSystem = true\n");
		config.append("EnablePhantomDiagnostics = false\n");
		config.append("MaxMaterializedPhantoms = 32\n");
		config.append("MaxScheduledPhantomProfiles = 10000\n");
		config.append("PhantomSchedulerPulseMillis = 100\n");
		config.append("PhantomSchedulerProfilesPerPulse = 128\n");
		config.append(overrides);
		return config.toString();
	}

	private static Set<Long> nonDaemonThreadIds()
	{
		final Set<Long> identifiers = new TreeSet<>();
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (thread.isAlive() && !thread.isDaemon())
			{
				identifiers.add(thread.threadId());
			}
		}
		return identifiers;
	}

	private static void assertNoNewNonDaemonThreads(Set<Long> before)
	{
		final Set<Long> after = nonDaemonThreadIds();
		after.removeAll(before);
		PhantomAssertions.assertEquals(Set.of(), after, "Skeleton created a non-daemon thread.");
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
}
