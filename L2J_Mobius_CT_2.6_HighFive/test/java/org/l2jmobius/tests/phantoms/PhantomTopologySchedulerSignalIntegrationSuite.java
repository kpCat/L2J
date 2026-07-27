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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.UnregisterStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.EventStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.TargetabilityEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.ReloadResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.UnregisterResult;

public final class PhantomTopologySchedulerSignalIntegrationSuite implements PhantomTestSuite
{
	private static final long PROFILE_ID = 1;

	@Override
	public String id()
	{
		return "topology-scheduler-signal-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		ThreadPool.init();
	}

	@Override
	public void afterAll(PhantomTestContext context)
	{
		ThreadPool.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-fresh-no-event-unregister-reconciles-absent-sources", _ -> testFreshNoEventUnregister());
		registry.add("02-partial-source-unregister-reconciles-never-submitted", _ -> testPartialSourceUnregister());
		registry.add("03-reregistration-submit-is-monotonic-and-accepted", _ -> testReregistrationMonotonicity());
		registry.add("04-reload-before-events-reconciles-absent-sources", _ -> testReloadBeforeEvents());
		registry.add("05-all-not-registered-releases-ledger", _ -> testSchedulerAbsentRelease());
	}

	private void testFreshNoEventUnregister() throws Exception
	{
		try (Fixture fixture = fixture())
		{
			registerBoth(fixture);
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(PROFILE_ID), "Fresh real-scheduler unregister failed.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Fresh real-scheduler unregister created pending cleanup.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Safe STALE incorrectly released the retained ledger.");
			assertAllSourceStates(fixture.service, PROFILE_ID, "INACTIVE_CONFIRMED");
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(PROFILE_ID), "Safe absent-source cleanup did not allow re-registration.");
		}
	}

	private void testPartialSourceUnregister() throws Exception
	{
		try (Fixture fixture = fixture())
		{
			registerBoth(fixture);
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, fixture.service.targetability(target(true)).status(), "Targetability submit did not reach the real scheduler.");
			PhantomAssertions.assertEquals("POSSIBLY_ACTIVE", sourceState(fixture.service, PROFILE_ID, "_targetabilityState"), "Active targetability did not establish local ownership.");
			PhantomAssertions.assertEquals("NEVER_SUBMITTED", sourceState(fixture.service, PROFILE_ID, "_localChatState"), "Local chat was unexpectedly submitted.");
			PhantomAssertions.assertEquals("NEVER_SUBMITTED", sourceState(fixture.service, PROFILE_ID, "_combatState"), "Combat was unexpectedly submitted.");
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(PROFILE_ID), "Partial-source real-scheduler unregister failed.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Partial-source unregister created pending cleanup.");
			PhantomAssertions.assertEquals(0, fixture.scheduler.find(PROFILE_ID).orElseThrow().activeSignalSources(), "Real scheduler retained the active targetability source.");
			assertAllSourceStates(fixture.service, PROFILE_ID, "INACTIVE_CONFIRMED");
		}
	}

	private void testReregistrationMonotonicity() throws Exception
	{
		try (Fixture fixture = fixture())
		{
			registerBoth(fixture);
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(PROFILE_ID), "Monotonicity setup unregister failed.");
			final long cleanupSequence = sourceSequence(fixture.service, PROFILE_ID, "_targetabilitySequence");
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(PROFILE_ID), "Retained topology identity did not re-register.");
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, fixture.service.targetability(target(true)).status(), "Newer re-registration submit was not accepted by the real scheduler.");
			final long submitSequence = sourceSequence(fixture.service, PROFILE_ID, "_targetabilitySequence");
			PhantomAssertions.assertTrue(submitSequence > cleanupSequence, "Re-registration did not allocate a newer provider sequence.");
			PhantomAssertions.assertEquals(1, fixture.scheduler.find(PROFILE_ID).orElseThrow().activeSignalSources(), "Accepted re-registration submit is absent from the real scheduler.");
			PhantomAssertions.assertEquals("POSSIBLY_ACTIVE", sourceState(fixture.service, PROFILE_ID, "_targetabilityState"), "Accepted re-registration submit became uncertain.");
		}
	}

	private void testReloadBeforeEvents() throws Exception
	{
		try (ReloadFixture fixture = new ReloadFixture())
		{
			registerBoth(fixture.fixture);
			PhantomAssertions.assertEquals(UpdateResult.UPDATED, fixture.fixture.service.updateProfile(PROFILE_ID, PhantomTopologyCoreSuite.point(300, 300), 1), "Reload fixture profile update failed.");
			final String initialHash = fixture.fixture.service.snapshot().canonicalHash();
			fixture.write("beta", 2);
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, fixture.fixture.service.reload(), "Real-adapter reload before events failed.");
			PhantomAssertions.assertEquals(2L, fixture.fixture.service.snapshot().generation(), "Reload did not install the candidate generation.");
			PhantomAssertions.assertFalse(initialHash.equals(fixture.fixture.service.snapshot().canonicalHash()), "Reload did not swap the candidate hash.");
			PhantomAssertions.assertEquals("beta", fixture.fixture.service.findProfile(PROFILE_ID).orElseThrow().nodeId(), "Reload did not swap profile membership.");
			PhantomAssertions.assertEquals(0, fixture.fixture.service.snapshot().pendingSignalCleanups(), "Reload before events created pending cleanup.");
			PhantomAssertions.assertEquals(1, fixture.fixture.service.snapshot().signalLedgersCurrent(), "Safe STALE reload invalidation released the ledger.");
			assertAllSourceStates(fixture.fixture.service, PROFILE_ID, "INACTIVE_CONFIRMED");
		}
	}

	private void testSchedulerAbsentRelease()
	{
		try (Fixture fixture = fixture())
		{
			registerBoth(fixture);
			PhantomAssertions.assertEquals(UnregisterStatus.UNREGISTERED, fixture.scheduler.unregister(PROFILE_ID).status(), "Dormant scheduler profile did not unregister.");
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(PROFILE_ID), "All-NOT_REGISTERED topology cleanup failed.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().signalLedgersCurrent(), "All-NOT_REGISTERED real adapter outcomes did not release the ledger.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Released ledger remained pending.");
		}
	}

	private static Fixture fixture()
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		return fixture(PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, signalPort(scheduler())));
	}

	private static PhantomScheduler scheduler()
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomScheduler scheduler = new PhantomScheduler(4, 1000, 4, metrics, new PhantomDiagnosticTrace(false, 1, 1, metrics), PhantomActivityMaterializationPort.noop(), _ ->
		{
		});
		PhantomAssertions.assertTrue(scheduler.start(), "Real scheduler did not start.");
		return scheduler;
	}

	private static PhantomSchedulerRelevanceSignalPort signalPort(PhantomScheduler scheduler)
	{
		return new PhantomSchedulerRelevanceSignalPort(scheduler);
	}

	private static Fixture fixture(PhantomTopologyService service)
	{
		final PhantomSchedulerRelevanceSignalPort port = (PhantomSchedulerRelevanceSignalPort) fieldUnchecked(service, "_perceptionProvider", "_signalPort");
		final PhantomScheduler scheduler = (PhantomScheduler) fieldUnchecked(port, "_scheduler");
		PhantomAssertions.assertTrue(service.start(), "Topology integration service did not start.");
		return new Fixture(scheduler, service);
	}

	private static void registerBoth(Fixture fixture)
	{
		PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, fixture.scheduler.register(PROFILE_ID).status(), "Real scheduler profile registration failed.");
		PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(PROFILE_ID), "Topology profile registration failed.");
	}

	private static TargetabilityEvent target(boolean active)
	{
		return new TargetabilityEvent("scheduler.target." + active, PROFILE_ID + 1000, PROFILE_ID, active, 1, 2000);
	}

	private static void assertAllSourceStates(PhantomTopologyService service, long profileId, String expected) throws Exception
	{
		for (String fieldName : new String[]
		{
			"_localChatState",
			"_combatState",
			"_targetabilityState"
		})
		{
			PhantomAssertions.assertEquals(expected, sourceState(service, profileId, fieldName), "Unexpected source state for " + fieldName + ".");
		}
	}

	private static String sourceState(PhantomTopologyService service, long profileId, String fieldName) throws Exception
	{
		return String.valueOf(field(ledger(service, profileId), fieldName));
	}

	private static long sourceSequence(PhantomTopologyService service, long profileId, String fieldName) throws Exception
	{
		return (long) field(ledger(service, profileId), fieldName);
	}

	@SuppressWarnings("unchecked")
	private static Object ledger(PhantomTopologyService service, long profileId) throws Exception
	{
		final Object provider = field(service, "_perceptionProvider");
		return ((Map<Long, Object>) field(provider, "_signalLedgers")).get(profileId);
	}

	private static Object field(Object target, String name) throws Exception
	{
		final Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static Object fieldUnchecked(Object target, String... names)
	{
		try
		{
			Object value = target;
			for (String name : names)
			{
				value = field(value, name);
			}
			return value;
		}
		catch (Exception exception)
		{
			throw new AssertionError(exception);
		}
	}

	private record Fixture(PhantomScheduler scheduler, PhantomTopologyService service) implements AutoCloseable
	{
		@Override
		public void close()
		{
			scheduler.beginStop();
			if (service.snapshot().state() == PhantomTopologyService.State.RUNNING)
			{
				service.beginStop();
			}
			PhantomAssertions.assertTrue(scheduler.finishStop(), "Real scheduler did not finish stop.");
			PhantomAssertions.assertTrue(service.finishStop(), "Topology integration service did not finish stop.");
		}
	}

	private static final class ReloadFixture implements AutoCloseable
	{
		private final Path _directory;
		private final Path _xml;
		private final Fixture fixture;

		private ReloadFixture() throws Exception
		{
			_directory = Files.createTempDirectory("phantom-topology-scheduler-signal-");
			_xml = _directory.resolve("scheduler-signal.xml");
			write("alpha", 1);
			final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
			final PhantomScheduler scheduler = scheduler();
			final PhantomTopologyPolicy policy = PhantomTopologyCoreSuite.POLICY.withMaximumRegisteredProfiles(4);
			final PhantomTopologyService service = new PhantomTopologyService(new PhantomTopologyLoader(_directory, backend, policy), backend, policy, signalPort(scheduler));
			PhantomAssertions.assertTrue(service.start(), "Reload integration service did not start.");
			fixture = new Fixture(scheduler, service);
		}

		private void write(String nodeId, int datasetVersion) throws Exception
		{
			Files.writeString(_xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><topology schemaVersion=\"1\" datasetId=\"scheduler-signal\" datasetVersion=\"" + datasetVersion + "\"><node id=\"" + nodeId + "\" kind=\"OUTDOOR_AREA\" instanceId=\"0\" form=\"CUBOID\" minX=\"100\" maxX=\"500\" minY=\"100\" maxY=\"500\" minZ=\"0\" maxZ=\"100\" /></topology>");
		}

		@Override
		public void close() throws Exception
		{
			fixture.close();
			try (var paths = Files.walk(_directory))
			{
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}
}
