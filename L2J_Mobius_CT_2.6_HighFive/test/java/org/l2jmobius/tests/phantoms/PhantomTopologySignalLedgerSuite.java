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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.EventStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.LocalChatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.TargetabilityEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.CleanupRetryResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.ReloadResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.UnregisterResult;

public final class PhantomTopologySignalLedgerSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "topology-signal-ledger";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-never-owned-inactive-target-no-state-or-port-call", _ -> testNeverOwnedInactiveTarget());
		registry.add("02-one-profile-one-fixed-three-source-ledger", _ -> testFixedLedger());
		registry.add("03-scheduler-present-unregister-retains-ledger", _ -> testSchedulerPresentRetention());
		registry.add("04-retained-reregistration-sequences-monotonic", _ -> testRetainedReregistration());
		registry.add("05-all-not-registered-cleanup-releases-ledger", _ -> testSchedulerAbsentRelease());
		registry.add("06-high-identity-churn-remains-bounded", _ -> testHighIdentityChurn());
		registry.add("07-retained-identities-reach-exact-capacity", _ -> testRetainedCapacity());
		registry.add("08-failed-cleanup-counts-against-capacity", _ -> testFailedCleanupCapacity());
		registry.add("09-accepted-retry-retains-sequence-ledger", _ -> testAcceptedRetryRetention());
		registry.add("10-all-not-registered-retry-releases-ledger", _ -> testSchedulerAbsentRetryRelease());
		registry.add("11-inactive-retained-targetability-uses-newer-sequence", _ -> testRetainedInactiveTargetability());
		registry.add("12-stale-possibly-active-cleanup-fails-closed", _ -> testStalePossiblyActive());
		registry.add("13-stale-confirmed-inactive-cleanup-is-safe-retained", _ -> testStaleConfirmedInactive());
		registry.add("14-stale-submit-is-signal-failure", _ -> testStaleSubmit());
		registry.add("15-rejected-and-not-running-submit-fail-closed", _ -> testImpossibleSubmits());
		registry.add("16-ledger-current-peak-capacity-metrics-exact", _ -> testLedgerMetrics());
		registry.add("17-reload-uses-owned-ledger-and-rejects-uncertain-cleanup", _ -> testReloadOwnership());
		registry.add("18-final-stop-clears-all-ledgers", _ -> testStopClearsLedgers());
		registry.add("19-concurrent-registration-event-unregister-retry-is-bounded", _ -> testConcurrentCapacity());
		registry.add("20-no-dynamic-source-map-or-pending-cleanup-set", _ -> testFixedStorageShape());
	}

	private void testNeverOwnedInactiveTarget()
	{
		try (Fixture fixture = fixture(2))
		{
			final var result = fixture.service.targetability(target(false, 100));
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, result.status(), "Never-owned inactive targetability was not isolated.");
			PhantomAssertions.assertEquals(1, result.unregistered(), "Never-owned inactive targetability did not return an explicit unregistered outcome.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().signalLedgersCurrent(), "Never-owned inactive targetability allocated a ledger.");
			PhantomAssertions.assertEquals(0, fixture.port.calls(), "Never-owned inactive targetability called the scheduler port.");
		}
	}

	private void testFixedLedger() throws Exception
	{
		try (Fixture fixture = fixture(2))
		{
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(1), "Ledger fixture registration failed.");
			final Map<Long, Object> ledgers = ledgers(fixture.service);
			PhantomAssertions.assertEquals(1, ledgers.size(), "One registered profile did not create exactly one ledger.");
			final Object ledger = ledgers.get(1L);
			PhantomAssertions.assertTrue(ledger != null, "Registered profile ledger is missing.");
			final Set<String> names = java.util.Arrays.stream(ledger.getClass().getDeclaredFields()).map(Field::getName).collect(java.util.stream.Collectors.toSet());
			for (String field : List.of("_localChatSequence", "_combatSequence", "_targetabilitySequence", "_localChatState", "_combatState", "_targetabilityState"))
			{
				PhantomAssertions.assertTrue(names.contains(field), "Fixed ledger field is missing: " + field + ".");
			}
			PhantomAssertions.assertEquals(3, PhantomPerceptionProvider.OWNED_SOURCES.size(), "Topology source count is not fixed at three.");
		}
	}

	private void testSchedulerPresentRetention()
	{
		try (Fixture fixture = fixture(2))
		{
			fixture.service.registerProfile(1);
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(1), "Scheduler-present cleanup failed.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Accepted withdrawals released a required sequence tombstone.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Successful scheduler-present cleanup remained pending.");
		}
	}

	private void testRetainedReregistration()
	{
		try (Fixture fixture = fixture(2))
		{
			fixture.service.registerProfile(1);
			fixture.service.unregisterProfile(1);
			final Map<String, Long> first = fixture.port.lastSequences(1);
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(1), "Retained identity did not re-register.");
			fixture.service.unregisterProfile(1);
			final Map<String, Long> second = fixture.port.lastSequences(1);
			for (String source : PhantomPerceptionProvider.OWNED_SOURCES)
			{
				PhantomAssertions.assertTrue(second.get(source) > first.get(source), "Re-registration did not preserve monotonic " + source + " sequence.");
			}
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Retained re-registration created or lost ledger ownership.");
		}
	}

	private void testSchedulerAbsentRelease()
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.port._withdrawStatus = SignalDelivery.NOT_REGISTERED;
			fixture.service.registerProfile(1);
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(1), "Scheduler-absent cleanup failed.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().signalLedgersCurrent(), "All-NOT_REGISTERED cleanup retained a ledger.");
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(2), "Released ledger capacity was not reusable.");
		}
	}

	private void testHighIdentityChurn()
	{
		final int capacity = 4;
		try (Fixture fixture = fixture(capacity))
		{
			fixture.port._withdrawStatus = SignalDelivery.NOT_REGISTERED;
			for (long profileId = 1; profileId <= (100L * capacity); profileId++)
			{
				PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(profileId), "Bounded churn registration failed.");
				PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(profileId), "Bounded churn cleanup failed.");
				PhantomAssertions.assertTrue(fixture.service.snapshot().signalLedgersCurrent() <= capacity, "Identity churn exceeded ledger capacity.");
			}
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().signalLedgersCurrent(), "Scheduler-absent churn retained ledger state.");
			PhantomAssertions.assertEquals(1L, fixture.service.snapshot().signalLedgersPeak(), "Sequential scheduler-absent churn peak changed.");
		}
	}

	private void testRetainedCapacity()
	{
		try (Fixture fixture = fixture(3))
		{
			for (long profileId = 1; profileId <= 3; profileId++)
			{
				fixture.service.registerProfile(profileId);
				fixture.service.unregisterProfile(profileId);
			}
			PhantomAssertions.assertEquals(3, fixture.service.snapshot().signalLedgersCurrent(), "Retained identities did not occupy exact ledger capacity.");
			PhantomAssertions.assertEquals(RegistrationResult.SIGNAL_LEDGER_CAPACITY, fixture.service.registerProfile(4), "Retained identity capacity did not fail explicitly.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().registeredProfiles(), "Ledger reservation failure mutated the profile registry.");
		}
	}

	private void testFailedCleanupCapacity()
	{
		try (Fixture fixture = fixture(2))
		{
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			for (long profileId = 1; profileId <= 2; profileId++)
			{
				fixture.service.registerProfile(profileId);
				PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_WITH_SIGNAL_FAILURE, fixture.service.unregisterProfile(profileId), "Failed cleanup tombstone was not retained.");
			}
			PhantomAssertions.assertEquals(2, fixture.service.snapshot().pendingSignalCleanups(), "Failed cleanup tombstones did not share the ledger capacity.");
			PhantomAssertions.assertEquals(RegistrationResult.SIGNAL_LEDGER_CAPACITY, fixture.service.registerProfile(3), "Failed cleanup tombstones did not block new ledger reservation.");
		}
	}

	private void testAcceptedRetryRetention()
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			fixture.service.registerProfile(1);
			fixture.service.unregisterProfile(1);
			final Map<String, Long> failed = fixture.port.lastSequences(1);
			fixture.port._withdrawStatus = SignalDelivery.ACCEPTED;
			PhantomAssertions.assertEquals(CleanupRetryResult.CLEANUP_COMPLETED, fixture.service.retryProfileSignalCleanup(1), "Accepted cleanup retry failed.");
			final Map<String, Long> retried = fixture.port.lastSequences(1);
			for (String source : PhantomPerceptionProvider.OWNED_SOURCES)
			{
				PhantomAssertions.assertTrue(retried.get(source) > failed.get(source), "Accepted cleanup retry did not consume a newer sequence.");
			}
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Accepted withdrawals incorrectly proved scheduler absence.");
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(1), "Accepted cleanup retry did not reopen retained identity registration.");
		}
	}

	private void testSchedulerAbsentRetryRelease()
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			fixture.service.registerProfile(1);
			fixture.service.unregisterProfile(1);
			fixture.port._withdrawStatus = SignalDelivery.NOT_REGISTERED;
			PhantomAssertions.assertEquals(CleanupRetryResult.CLEANUP_COMPLETED, fixture.service.retryProfileSignalCleanup(1), "Scheduler-absent retry failed.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().signalLedgersCurrent(), "All-NOT_REGISTERED retry retained a ledger.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Released retry ledger remained pending.");
		}
	}

	private void testRetainedInactiveTargetability()
	{
		try (Fixture fixture = fixture(2))
		{
			fixture.service.registerProfile(1);
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, fixture.service.targetability(target(true, 1)).status(), "Active targetability setup failed.");
			fixture.service.unregisterProfile(1);
			final long cleanupSequence = fixture.port.last(1, PhantomPerceptionProvider.TARGETABILITY_SOURCE).sequence();
			final var result = fixture.service.targetability(target(false, 1));
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, result.status(), "Retained inactive targetability failed.");
			final Delivery inactive = fixture.port.last(1, PhantomPerceptionProvider.TARGETABILITY_SOURCE);
			PhantomAssertions.assertTrue(inactive.withdraw(), "Retained inactive targetability did not withdraw.");
			PhantomAssertions.assertTrue(inactive.sequence() > cleanupSequence, "Retained inactive targetability did not use a newer sequence.");
		}
	}

	private void testStalePossiblyActive() throws Exception
	{
		try (Fixture fixture = fixture(2))
		{
			fixture.service.registerProfile(1);
			fixture.service.targetability(target(true, 1));
			fixture.port._withdrawStatus = SignalDelivery.STALE;
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_WITH_SIGNAL_FAILURE, fixture.service.unregisterProfile(1), "STALE possibly-active cleanup was treated as success.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().pendingSignalCleanups(), "STALE ownership uncertainty was not retained.");
			PhantomAssertions.assertEquals("OWNERSHIP_UNCERTAIN", sourceState(fixture.service, 1, "_targetabilityState"), "STALE possibly-active source did not fail closed.");
		}
	}

	private void testStaleConfirmedInactive()
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.service.registerProfile(1);
			fixture.service.unregisterProfile(1);
			fixture.service.registerProfile(1);
			fixture.port._withdrawStatus = SignalDelivery.STALE;
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(1), "STALE locally inactive cleanup was not accepted.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Safe STALE cleanup remained pending.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "STALE cleanup incorrectly proved scheduler profile absence.");
		}
	}

	private void testStaleSubmit() throws Exception
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.service.registerProfile(1);
			fixture.port._submitStatus = SignalDelivery.STALE;
			PhantomAssertions.assertEquals(EventStatus.SIGNAL_FAILURE, fixture.service.targetability(target(true, 1)).status(), "STALE submit was hidden as success.");
			PhantomAssertions.assertEquals("OWNERSHIP_UNCERTAIN", sourceState(fixture.service, 1, "_targetabilityState"), "STALE submit did not mark ownership uncertain.");
		}
	}

	private void testImpossibleSubmits()
	{
		try (Fixture fixture = fixture(1))
		{
			fixture.service.registerProfile(1);
			for (SignalDelivery delivery : List.of(SignalDelivery.REJECTED, SignalDelivery.NOT_RUNNING))
			{
				fixture.port._submitStatus = delivery;
				PhantomAssertions.assertEquals(EventStatus.SIGNAL_FAILURE, fixture.service.targetability(target(true, 1)).status(), delivery + " submit was hidden as success.");
			}
		}
	}

	private void testLedgerMetrics()
	{
		try (Fixture fixture = fixture(2))
		{
			PhantomAssertions.assertEquals(2, fixture.service.snapshot().signalLedgerCapacity(), "Ledger capacity metric changed.");
			fixture.port._withdrawStatus = SignalDelivery.NOT_REGISTERED;
			fixture.service.registerProfile(1);
			fixture.service.unregisterProfile(1);
			fixture.port._withdrawStatus = SignalDelivery.ACCEPTED;
			fixture.service.registerProfile(2);
			fixture.service.registerProfile(3);
			final var snapshot = fixture.service.snapshot();
			PhantomAssertions.assertEquals(2, snapshot.signalLedgersCurrent(), "Ledger current gauge is not exact.");
			PhantomAssertions.assertEquals(2L, snapshot.signalLedgersPeak(), "Ledger peak gauge is not exact.");
			PhantomAssertions.assertEquals(2L, snapshot.metrics().signalLedgerCapacity(), "Ledger capacity aggregate metric is not exact.");
			PhantomAssertions.assertEquals((long) snapshot.signalLedgersCurrent(), snapshot.metrics().signalLedgersCurrent(), "Service and metric ledger current disagree.");
		}
	}

	private void testReloadOwnership() throws Exception
	{
		try (ReloadFixture fixture = new ReloadFixture())
		{
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(1), "Reload ledger profile registration failed.");
			PhantomAssertions.assertEquals(UpdateResult.UPDATED, fixture.service.updateProfile(1, PhantomTopologyCoreSuite.point(300, 300), 1), "Reload ledger profile update failed.");
			fixture.service.localChat(new LocalChatEvent("ledger.reload.chat", PhantomTopologyCoreSuite.point(300, 300), null, 1, 1000, 5000));
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Reload setup ledger count changed.");
			fixture.port._withdrawStatus = SignalDelivery.STALE;
			fixture.write(2);
			PhantomAssertions.assertEquals(ReloadResult.REJECTED_SIGNAL_INVALIDATION, fixture.service.reload(), "Uncertain reload cleanup did not reject the candidate.");
			PhantomAssertions.assertEquals(1L, fixture.service.snapshot().generation(), "Rejected reload changed generation.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Reload invalidation created or removed a ledger.");
		}
	}

	private void testStopClearsLedgers()
	{
		final Fixture fixture = fixture(2);
		fixture.service.registerProfile(1);
		fixture.service.unregisterProfile(1);
		PhantomAssertions.assertEquals(1, fixture.service.snapshot().signalLedgersCurrent(), "Stop fixture did not retain a ledger.");
		fixture.close();
		final var snapshot = fixture.service.snapshot();
		PhantomAssertions.assertEquals(0, snapshot.signalLedgersCurrent(), "Final stop did not clear signal ledgers.");
		PhantomAssertions.assertEquals(0L, snapshot.metrics().signalLedgersCurrent(), "Final stop did not clear the ledger gauge.");
		PhantomAssertions.assertEquals(1L, snapshot.signalLedgersPeak(), "Final stop erased the ledger peak metric.");
	}

	private void testConcurrentCapacity() throws Exception
	{
		final int capacity = 8;
		try (Fixture fixture = fixture(capacity))
		{
			final CountDownLatch ready = new CountDownLatch(64);
			final CountDownLatch start = new CountDownLatch(1);
			final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
			final ArrayList<Thread> threads = new ArrayList<>();
			for (long profileId = 1; profileId <= 64; profileId++)
			{
				final long id = profileId;
				final Thread thread = Thread.ofPlatform().name("topology-ledger-race-" + id).unstarted(() ->
				{
					ready.countDown();
					try
					{
						if (!start.await(5, TimeUnit.SECONDS))
						{
							throw new AssertionError("Concurrent ledger start timed out.");
						}
						if (fixture.service.registerProfile(id) == RegistrationResult.REGISTERED)
						{
							fixture.service.targetability(new TargetabilityEvent("ledger.race." + id, id + 1000, id, false, id, 2000));
							fixture.service.unregisterProfile(id);
							fixture.service.retryProfileSignalCleanup(id);
						}
					}
					catch (Throwable throwable)
					{
						failures.add(throwable);
					}
				});
				threads.add(thread);
				thread.start();
			}
			PhantomAssertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "Concurrent ledger workers did not become ready.");
			start.countDown();
			for (Thread thread : threads)
			{
				thread.join(5000);
				PhantomAssertions.assertFalse(thread.isAlive(), "Concurrent ledger worker did not finish.");
			}
			PhantomAssertions.assertEquals(List.of(), List.copyOf(failures), "Concurrent ledger operations failed.");
			PhantomAssertions.assertTrue(fixture.service.snapshot().signalLedgersCurrent() <= capacity, "Concurrent ledger current exceeded capacity.");
			PhantomAssertions.assertTrue(fixture.service.snapshot().signalLedgersPeak() <= capacity, "Concurrent ledger peak exceeded capacity.");
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().registeredProfiles(), "Concurrent unregister left topology profiles registered.");
		}
	}

	private void testFixedStorageShape() throws Exception
	{
		final Set<String> providerFields = java.util.Arrays.stream(PhantomPerceptionProvider.class.getDeclaredFields()).map(Field::getName).collect(java.util.stream.Collectors.toSet());
		PhantomAssertions.assertTrue(providerFields.contains("_signalLedgers"), "Bounded signal ledger map is missing.");
		PhantomAssertions.assertFalse(providerFields.contains("_sequences"), "Dynamic source sequence map remains.");
		PhantomAssertions.assertFalse(providerFields.contains("_pendingCleanup"), "Standalone pending-cleanup set remains.");
		final long mapFields = java.util.Arrays.stream(PhantomPerceptionProvider.class.getDeclaredFields()).filter(field -> Map.class.isAssignableFrom(field.getType())).count();
		PhantomAssertions.assertEquals(1L, mapFields, "Perception provider owns more than one dynamic map.");
		final Class<?> ledgerClass = Class.forName("org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySignalLedger");
		PhantomAssertions.assertEquals(0L, java.util.Arrays.stream(ledgerClass.getDeclaredFields()).filter(field -> Map.class.isAssignableFrom(field.getType()) || Set.class.isAssignableFrom(field.getType()) || List.class.isAssignableFrom(field.getType())).count(), "Fixed signal ledger contains a dynamic collection.");
	}

	private static Fixture fixture(int capacity)
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final PhantomTopologyPolicy policy = PhantomTopologyCoreSuite.POLICY.withMaximumRegisteredProfiles(capacity);
		final RecordingSignalPort port = new RecordingSignalPort();
		final PhantomTopologyService service = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, policy, port);
		PhantomAssertions.assertTrue(service.start(), "Signal ledger fixture did not start.");
		return new Fixture(service, port);
	}

	private static TargetabilityEvent target(boolean active, long targetProfileId)
	{
		return new TargetabilityEvent("ledger.target." + active + "." + targetProfileId, targetProfileId + 1000, targetProfileId, active, targetProfileId, 2000);
	}

	@SuppressWarnings("unchecked")
	private static Map<Long, Object> ledgers(PhantomTopologyService service) throws Exception
	{
		return (Map<Long, Object>) field(field(service, "_perceptionProvider"), "_signalLedgers");
	}

	private static String sourceState(PhantomTopologyService service, long profileId, String fieldName) throws Exception
	{
		final Object ledger = ledgers(service).get(profileId);
		return String.valueOf(field(ledger, fieldName));
	}

	private static Object field(Object target, String name) throws Exception
	{
		final Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private record Fixture(PhantomTopologyService service, RecordingSignalPort port) implements AutoCloseable
	{
		@Override
		public void close()
		{
			if (service.snapshot().state() == PhantomTopologyService.State.RUNNING)
			{
				service.beginStop();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "Signal ledger fixture did not stop.");
		}
	}

	private static final class ReloadFixture implements AutoCloseable
	{
		private final Path _directory;
		private final Path _xml;
		private final PhantomTopologyCoreSuite.TestBackend _backend = new PhantomTopologyCoreSuite.TestBackend();
		private final RecordingSignalPort port = new RecordingSignalPort();
		private final PhantomTopologyService service;

		private ReloadFixture() throws Exception
		{
			_directory = Files.createTempDirectory("phantom-topology-ledger-");
			_xml = _directory.resolve("ledger.xml");
			write(1);
			final PhantomTopologyPolicy policy = PhantomTopologyCoreSuite.POLICY.withMaximumRegisteredProfiles(4);
			service = new PhantomTopologyService(new PhantomTopologyLoader(_directory, _backend, policy), _backend, policy, port);
			PhantomAssertions.assertTrue(service.start(), "Signal ledger reload fixture did not start.");
		}

		private void write(int datasetVersion) throws Exception
		{
			Files.writeString(_xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><topology schemaVersion=\"1\" datasetId=\"ledger\" datasetVersion=\"" + datasetVersion + "\"><node id=\"ledger.area\" kind=\"OUTDOOR_AREA\" instanceId=\"0\" form=\"CUBOID\" minX=\"100\" maxX=\"500\" minY=\"100\" maxY=\"500\" minZ=\"0\" maxZ=\"100\" /></topology>");
		}

		@Override
		public void close() throws Exception
		{
			if (service.snapshot().state() == PhantomTopologyService.State.RUNNING)
			{
				service.beginStop();
			}
			PhantomAssertions.assertTrue(service.finishStop(), "Signal ledger reload fixture did not stop.");
			try (var paths = Files.walk(_directory))
			{
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	private record Delivery(long profileId, String sourceKey, long sequence, boolean withdraw)
	{
	}

	private static final class RecordingSignalPort implements PhantomRelevanceSignalPort
	{
		private final List<Delivery> _deliveries = Collections.synchronizedList(new ArrayList<>());
		private volatile SignalDelivery _submitStatus = SignalDelivery.ACCEPTED;
		private volatile SignalDelivery _withdrawStatus = SignalDelivery.ACCEPTED;

		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			_deliveries.add(new Delivery(profileId, signal.sourceKey(), signal.sequence(), false));
			return _submitStatus;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			_deliveries.add(new Delivery(profileId, sourceKey, sequence, true));
			return _withdrawStatus;
		}

		int calls()
		{
			return _deliveries.size();
		}

		Delivery last(long profileId, String sourceKey)
		{
			return List.copyOf(_deliveries).stream().filter(delivery -> (delivery.profileId() == profileId) && delivery.sourceKey().equals(sourceKey)).reduce((_, second) -> second).orElseThrow();
		}

		Map<String, Long> lastSequences(long profileId)
		{
			final java.util.HashMap<String, Long> result = new java.util.HashMap<>();
			List.copyOf(_deliveries).stream().filter(delivery -> delivery.profileId() == profileId).forEach(delivery -> result.put(delivery.sourceKey(), delivery.sequence()));
			return Map.copyOf(result);
		}
	}
}
