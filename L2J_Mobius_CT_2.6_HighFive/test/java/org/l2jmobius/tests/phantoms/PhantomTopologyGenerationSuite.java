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
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.CombatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.EventStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.LocalChatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.TargetabilityEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.CleanupRetryResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.ReloadResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService.UnregisterResult;

public final class PhantomTopologyGenerationSuite implements PhantomTestSuite
{
	private static final PhantomTopologyPoint PROFILE_POINT = PhantomTopologyCoreSuite.point(300, 300);

	@Override
	public String id()
	{
		return "topology-generation";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-update-owns-generation-through-commit", _ -> testUpdateOwnsGeneration());
		registry.add("02-reload-reresolves-and-preserves-sequence", _ -> testReloadReresolves());
		registry.add("03-reload-true-unresolved", _ -> testReloadUnresolved());
		registry.add("04-event-owns-generation-through-delivery", _ -> testEventOwnsGeneration());
		registry.add("05-no-old-event-delivery-after-reload", _ -> testNoOldDeliveryAfterReload());
		registry.add("06-stale-generation-recipient-excluded", _ -> testStaleRecipientExcluded());
		registry.add("07-unregister-withdraws-owned-sources", _ -> testUnregisterWithdrawsSources());
		registry.add("08-inactive-targetability-after-unregister", _ -> testInactiveAfterUnregister());
		registry.add("09-precomputed-event-unregister-final-withdraw", _ -> testEventUnregisterOrdering());
		registry.add("10-unregister-cleanup-failure-explicit", _ -> testCleanupFailure());
		registry.add("11-cleanup-retry-monotonic", _ -> testCleanupRetry());
		registry.add("12-reload-invalidates-before-swap", _ -> testReloadInvalidatesBeforeSwap());
		registry.add("13-reload-invalidation-failure-retains-generation", _ -> testReloadInvalidationFailure());
		registry.add("14-rejected-reload-preserves-profile", _ -> testRejectedReloadPreservesProfile());
		registry.add("15-source-sequence-exhaustion", _ -> testSequenceExhaustion());
		registry.add("16-reload-update-event-stop-no-deadlock", _ -> testRaceQuiescence());
		registry.add("17-no-public-mutable-registry-exposure", _ -> testNoMutableRegistryExposure());
	}

	private void testUpdateOwnsGeneration() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 1);
			final Object registry = field(fixture.service, "_profileRegistry");
			final Object monitor = field(registry, "_monitor");
			final AtomicReference<UpdateResult> updateResult = new AtomicReference<>();
			final AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
			final Thread update;
			final Thread reload;
			synchronized (monitor)
			{
				update = Thread.ofPlatform().name("topology-generation-update").unstarted(() -> updateResult.set(fixture.service.updateProfile(1, PROFILE_POINT, 2)));
				update.start();
				awaitBlocked(update, "Profile update did not reach membership commit.");
				fixture.write(simpleTopology("beta", 2));
				reload = Thread.ofPlatform().name("topology-generation-reload").unstarted(() -> reloadResult.set(fixture.service.reload()));
				reload.start();
				reload.join(100);
				PhantomAssertions.assertTrue(reload.isAlive(), "Reload installed while an old-generation update still owned resolution.");
			}
			join(update, "profile update");
			join(reload, "topology reload");
			PhantomAssertions.assertEquals(UpdateResult.UPDATED, updateResult.get(), "Owned profile update did not commit.");
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, reloadResult.get(), "Reload did not complete after profile update released ownership.");
			final var profile = fixture.service.findProfile(1).orElseThrow();
			PhantomAssertions.assertEquals("beta", profile.nodeId(), "Reload did not replace the old-generation membership.");
			PhantomAssertions.assertEquals(2L, profile.sequence(), "Reload changed the committed profile sequence.");
			PhantomAssertions.assertEquals(2L, profile.topologyGeneration(), "Old topology generation survived successful reload.");
		}
	}

	private void testReloadReresolves() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 7);
			fixture.write(simpleTopology("beta", 2));
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, fixture.service.reload(), "Valid topology reload failed.");
			final var profile = fixture.service.findProfile(1).orElseThrow();
			PhantomAssertions.assertEquals("beta", profile.nodeId(), "Registered point was not re-resolved against the candidate topology.");
			PhantomAssertions.assertEquals(PROFILE_POINT, profile.point(), "Reload changed the registered immutable point.");
			PhantomAssertions.assertEquals(7L, profile.sequence(), "Reload changed the profile position sequence.");
			PhantomAssertions.assertEquals(2L, profile.topologyGeneration(), "Reload membership generation changed.");
		}
	}

	private void testReloadUnresolved() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 5);
			fixture.write(unresolvedTopology(2));
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, fixture.service.reload(), "Valid unresolved topology reload failed.");
			final var profile = fixture.service.findProfile(1).orElseThrow();
			PhantomAssertions.assertFalse(profile.resolved(), "Point outside the new snapshot retained an old node.");
			PhantomAssertions.assertEquals(PROFILE_POINT, profile.point(), "Unresolved reload discarded the registered point.");
			PhantomAssertions.assertEquals(5L, profile.sequence(), "Unresolved reload changed the profile sequence.");
		}
	}

	private void testEventOwnsGeneration() throws Exception
	{
		try (ReloadFixture fixture = doorFixture(1))
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.RIGHT_POINT, 1);
			final CountDownLatch enteredDoor = new CountDownLatch(1);
			final CountDownLatch releaseDoor = new CountDownLatch(1);
			blockDoor(fixture.backend, enteredDoor, releaseDoor);
			final AtomicReference<Throwable> eventFailure = new AtomicReference<>();
			final Thread event = Thread.ofPlatform().name("topology-owned-event").unstarted(() -> runEvent(fixture.service, eventFailure));
			event.start();
			PhantomAssertions.assertTrue(enteredDoor.await(5, TimeUnit.SECONDS), "Event did not capture generation before its door query.");
			fixture.write(doorTopology(2));
			final AtomicReference<ReloadResult> reloadResult = new AtomicReference<>();
			final Thread reload = Thread.ofPlatform().name("topology-owned-reload").unstarted(() -> reloadResult.set(fixture.service.reload()));
			reload.start();
			reload.join(100);
			PhantomAssertions.assertTrue(reload.isAlive(), "Reload installed a new generation before owned event delivery completed.");
			releaseDoor.countDown();
			join(event, "owned event");
			join(reload, "owned reload");
			PhantomAssertions.assertEquals(null, eventFailure.get(), "Owned event failed.");
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, reloadResult.get(), "Reload failed after event ownership completed.");
			PhantomAssertions.assertEquals(2L, fixture.service.snapshot().generation(), "Reload did not install generation 2.");
		}
	}

	private void testNoOldDeliveryAfterReload() throws Exception
	{
		try (ReloadFixture fixture = doorFixture(1))
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.RIGHT_POINT, 1);
			final CountDownLatch enteredDoor = new CountDownLatch(1);
			final CountDownLatch releaseDoor = new CountDownLatch(1);
			blockDoor(fixture.backend, enteredDoor, releaseDoor);
			final Thread event = Thread.ofPlatform().name("topology-old-event").unstarted(() -> fixture.service.localChat(chat()));
			event.start();
			PhantomAssertions.assertTrue(enteredDoor.await(5, TimeUnit.SECONDS), "Old-generation event did not start.");
			fixture.write(doorTopology(2));
			final Thread reload = Thread.ofPlatform().name("topology-new-reload").unstarted(fixture.service::reload);
			reload.start();
			releaseDoor.countDown();
			join(event, "old-generation event");
			join(reload, "new-generation reload");
			final int deliveriesAtReturn = fixture.port.deliveries().size();
			Thread.yield();
			PhantomAssertions.assertEquals(deliveriesAtReturn, fixture.port.deliveries().size(), "Old-generation event delivered after reload returned.");
			final Delivery lastLocal = fixture.port.last(1, PhantomPerceptionProvider.LOCAL_CHAT_SOURCE);
			PhantomAssertions.assertTrue(lastLocal.withdraw(), "Reload did not leave the old local-chat source withdrawn.");
		}
	}

	private void testStaleRecipientExcluded() throws Exception
	{
		final RecordingSignalPort port = new RecordingSignalPort();
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final PhantomTopologyService service = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, port);
		PhantomAssertions.assertTrue(service.start(), "Stale-recipient service did not start.");
		try
		{
			register(service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			setProfileGeneration(service, 1, 0);
			PhantomAssertions.assertEquals(0, service.localChat(chat()).considered(), "Stale-generation registry entry was selected as an event recipient.");
			PhantomAssertions.assertEquals(0, port.deliveries().size(), "Stale-generation recipient reached the scheduler port.");
		}
		finally
		{
			stop(service);
		}
	}

	private void testUnregisterWithdrawsSources() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			activateAllSources(fixture);
			fixture.port.clear();
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(1), "Topology unregister did not complete owned-source cleanup.");
			PhantomAssertions.assertEquals(Set.copyOf(PhantomPerceptionProvider.OWNED_SOURCES), fixture.port.withdrawnSources(1), "Topology unregister did not withdraw all fixed sources.");
			PhantomAssertions.assertTrue(fixture.service.findProfile(1).isEmpty(), "Topology unregister restored removed membership.");
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testInactiveAfterUnregister() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			register(fixture.service, 2, PhantomTopologyCoreSuite.RIGHT_POINT, 1);
			fixture.service.targetability(new TargetabilityEvent("target.active", 2, 1, true, 1, 2000));
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, fixture.service.unregisterProfile(1), "Target topology unregister failed.");
			final long unregisterSequence = fixture.port.last(1, PhantomPerceptionProvider.TARGETABILITY_SOURCE).sequence();
			fixture.port.clear();
			final var result = fixture.service.targetability(new TargetabilityEvent("target.inactive", 2, 1, false, 2, 2000));
			PhantomAssertions.assertEquals(EventStatus.ACCEPTED, result.status(), "Inactive targetability after unregister was rejected.");
			final Delivery inactive = fixture.port.last(1, PhantomPerceptionProvider.TARGETABILITY_SOURCE);
			PhantomAssertions.assertTrue(inactive.withdraw(), "Inactive targetability did not withdraw after topology unregister.");
			PhantomAssertions.assertTrue(inactive.sequence() > unregisterSequence, "Inactive targetability did not allocate a newer source sequence.");
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testEventUnregisterOrdering() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			activateAllSources(fixture);
			fixture.port.clear();
			final CountDownLatch enteredSubmit = new CountDownLatch(1);
			final CountDownLatch releaseSubmit = new CountDownLatch(1);
			fixture.port.blockNextSubmit(enteredSubmit, releaseSubmit);
			final Thread event = Thread.ofPlatform().name("topology-precomputed-event").unstarted(() -> fixture.service.localChat(chat()));
			event.start();
			PhantomAssertions.assertTrue(enteredSubmit.await(5, TimeUnit.SECONDS), "Precomputed event did not enter final scheduler delivery.");
			final AtomicReference<UnregisterResult> unregisterResult = new AtomicReference<>();
			final Thread unregister = Thread.ofPlatform().name("topology-final-unregister").unstarted(() -> unregisterResult.set(fixture.service.unregisterProfile(1)));
			unregister.start();
			unregister.join(100);
			PhantomAssertions.assertTrue(unregister.isAlive(), "Unregister bypassed the event delivery ordering gate.");
			releaseSubmit.countDown();
			join(event, "precomputed event");
			join(unregister, "ordered unregister");
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_AND_WITHDRAWN, unregisterResult.get(), "Ordered unregister cleanup failed.");
			for (String source : PhantomPerceptionProvider.OWNED_SOURCES)
			{
				PhantomAssertions.assertTrue(fixture.port.last(1, source).withdraw(), "Final provider operation was not withdraw for " + source + ".");
			}
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testCleanupFailure() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			PhantomAssertions.assertEquals(UnregisterResult.UNREGISTERED_WITH_SIGNAL_FAILURE, fixture.service.unregisterProfile(1), "Unregister cleanup backpressure was hidden.");
			PhantomAssertions.assertTrue(fixture.service.findProfile(1).isEmpty(), "Cleanup failure restored topology membership.");
			PhantomAssertions.assertEquals(1, fixture.service.snapshot().pendingSignalCleanups(), "Cleanup failure was not retained for explicit retry.");
			PhantomAssertions.assertEquals(RegistrationResult.CLEANUP_PENDING, fixture.service.registerProfile(1), "Pending cleanup allowed unsafe re-registration.");
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testCleanupRetry() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			fixture.service.unregisterProfile(1);
			final Map<String, Long> failedSequences = fixture.port.lastSequences(1);
			fixture.port._withdrawStatus = SignalDelivery.ACCEPTED;
			PhantomAssertions.assertEquals(CleanupRetryResult.CLEANUP_COMPLETED, fixture.service.retryProfileSignalCleanup(1), "Explicit signal cleanup retry did not complete.");
			final Map<String, Long> retriedSequences = fixture.port.lastSequences(1);
			for (String source : PhantomPerceptionProvider.OWNED_SOURCES)
			{
				PhantomAssertions.assertTrue(retriedSequences.get(source) > failedSequences.get(source), "Cleanup retry sequence did not remain monotonic for " + source + ".");
			}
			PhantomAssertions.assertEquals(0, fixture.service.snapshot().pendingSignalCleanups(), "Successful cleanup retry remained pending.");
			PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.service.registerProfile(1), "Successful cleanup retry did not reopen registration.");
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testReloadInvalidatesBeforeSwap() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 1);
			fixture.service.localChat(new LocalChatEvent("reload.chat", PROFILE_POINT, null, 1, 1000, 5000));
			fixture.port.clear();
			final List<Long> observedGenerations = Collections.synchronizedList(new ArrayList<>());
			fixture.port._withdrawObserver = () -> observedGenerations.add(activeGenerationUnsafe(fixture.service));
			fixture.write(simpleTopology("beta", 2));
			PhantomAssertions.assertEquals(ReloadResult.RELOADED, fixture.service.reload(), "Reload with successful invalidation failed.");
			PhantomAssertions.assertEquals(3, fixture.port.deliveries().size(), "Reload did not invalidate every provider-owned source.");
			PhantomAssertions.assertTrue(observedGenerations.stream().allMatch(generation -> generation == 1), "Reload exposed the candidate generation before source invalidation completed.");
			PhantomAssertions.assertEquals(2L, fixture.service.snapshot().generation(), "Reload did not swap after successful invalidation.");
		}
	}

	private void testReloadInvalidationFailure() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 9);
			final var before = fixture.service.snapshot();
			fixture.port._withdrawStatus = SignalDelivery.BACKPRESSURE;
			fixture.write(simpleTopology("beta", 2));
			PhantomAssertions.assertEquals(ReloadResult.REJECTED_SIGNAL_INVALIDATION, fixture.service.reload(), "Signal invalidation backpressure did not reject reload.");
			final var after = fixture.service.snapshot();
			PhantomAssertions.assertEquals(before.generation(), after.generation(), "Rejected reload changed topology generation.");
			PhantomAssertions.assertEquals(before.canonicalHash(), after.canonicalHash(), "Rejected reload changed topology hash.");
			final var profile = fixture.service.findProfile(1).orElseThrow();
			PhantomAssertions.assertEquals("alpha", profile.nodeId(), "Rejected reload changed profile membership.");
			PhantomAssertions.assertEquals(9L, profile.sequence(), "Rejected reload changed profile sequence.");
			PhantomAssertions.assertEquals("signal-invalidation", after.lastFailureCategory(), "Invalidation failure category was not explicit.");
		}
	}

	private void testRejectedReloadPreservesProfile() throws Exception
	{
		try (ReloadFixture fixture = simpleFixture("alpha", 1))
		{
			register(fixture.service, 1, PROFILE_POINT, 4);
			final var before = fixture.service.findProfile(1).orElseThrow();
			final long generation = fixture.service.snapshot().generation();
			fixture.write("<topology schemaVersion=\"2\" datasetId=\"generation\" datasetVersion=\"2\" />");
			PhantomAssertions.assertEquals(ReloadResult.REJECTED_VALIDATION, fixture.service.reload(), "Invalid candidate was not rejected.");
			PhantomAssertions.assertEquals(before, fixture.service.findProfile(1).orElseThrow(), "Validation-rejected reload changed point/sequence/membership.");
			PhantomAssertions.assertEquals(generation, fixture.service.snapshot().generation(), "Validation-rejected reload changed generation.");
		}
	}

	private void testSequenceExhaustion() throws Exception
	{
		final ServiceFixture fixture = serviceFixture();
		try
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
			fixture.service.localChat(chat());
			setSequencesToMaximum(fixture.service);
			final int before = fixture.port.deliveries().size();
			final var result = fixture.service.localChat(new LocalChatEvent("overflow.chat", PhantomTopologyCoreSuite.LEFT_POINT, null, 2, 1000, 5000));
			PhantomAssertions.assertEquals(EventStatus.SIGNAL_FAILURE, result.status(), "Source sequence exhaustion was not explicit.");
			PhantomAssertions.assertEquals(before, fixture.port.deliveries().size(), "Exhausted sequence reached the scheduler port.");
			PhantomAssertions.assertEquals(1L, fixture.service.snapshot().metrics().signalSequenceExhausted(), "Sequence exhaustion metric changed.");
			assertSequencesNonNegative(fixture.service);
		}
		finally
		{
			stop(fixture.service);
		}
	}

	private void testRaceQuiescence() throws Exception
	{
		try (ReloadFixture fixture = doorFixture(1))
		{
			register(fixture.service, 1, PhantomTopologyCoreSuite.RIGHT_POINT, 1);
			final CountDownLatch enteredDoor = new CountDownLatch(1);
			final CountDownLatch releaseDoor = new CountDownLatch(1);
			blockDoor(fixture.backend, enteredDoor, releaseDoor);
			final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
			final Thread event = thread("topology-race-event", () -> fixture.service.localChat(chat()), failures);
			event.start();
			PhantomAssertions.assertTrue(enteredDoor.await(5, TimeUnit.SECONDS), "Race event did not capture generation.");
			fixture.write(doorTopology(2));
			final Thread reload = thread("topology-race-reload", fixture.service::reload, failures);
			reload.start();
			final Thread update = thread("topology-race-update", () -> fixture.service.updateProfile(1, PhantomTopologyCoreSuite.RIGHT_POINT, 2), failures);
			update.start();
			final Thread stop = thread("topology-race-stop", fixture.service::beginStop, failures);
			stop.start();
			releaseDoor.countDown();
			for (Thread thread : List.of(event, reload, update, stop))
			{
				join(thread, thread.getName());
			}
			PhantomAssertions.assertEquals(List.of(), List.copyOf(failures), "Reload/update/event/stop race failed.");
			PhantomAssertions.assertTrue(fixture.service.finishStop(), "Race service did not reach quiescent stop.");
			PhantomAssertions.assertEquals(PhantomTopologyService.State.STOPPED, fixture.service.snapshot().state(), "Race service did not stop.");
		}
	}

	private void testNoMutableRegistryExposure()
	{
		for (var method : PhantomTopologyService.class.getMethods())
		{
			PhantomAssertions.assertFalse(method.getName().equals("profiles") || (method.getReturnType() == PhantomTopologyProfileRegistry.class), "Topology service publicly exposes its mutable registry.");
		}
		for (var constructor : PhantomTopologyProfileRegistry.class.getDeclaredConstructors())
		{
			PhantomAssertions.assertFalse(Modifier.isPublic(constructor.getModifiers()), "Topology registry has a public mutable constructor.");
		}
		for (var method : PhantomTopologyProfileRegistry.class.getDeclaredMethods())
		{
			if (Set.of("register", "update", "remove", "installCandidate").contains(method.getName()))
			{
				PhantomAssertions.assertFalse(Modifier.isPublic(method.getModifiers()), "Topology registry mutation is publicly exposed: " + method.getName() + ".");
			}
		}
	}

	private static ServiceFixture serviceFixture()
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final RecordingSignalPort port = new RecordingSignalPort();
		final PhantomTopologyService service = PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend), backend, PhantomTopologyCoreSuite.POLICY, port);
		PhantomAssertions.assertTrue(service.start(), "Generation service fixture did not start.");
		return new ServiceFixture(service, port);
	}

	private static ReloadFixture simpleFixture(String activeNode, int datasetVersion) throws Exception
	{
		return new ReloadFixture(simpleTopology(activeNode, datasetVersion));
	}

	private static ReloadFixture doorFixture(int datasetVersion) throws Exception
	{
		return new ReloadFixture(doorTopology(datasetVersion));
	}

	private static void activateAllSources(ServiceFixture fixture)
	{
		register(fixture.service, 1, PhantomTopologyCoreSuite.LEFT_POINT, 1);
		register(fixture.service, 2, PhantomTopologyCoreSuite.RIGHT_POINT, 1);
		fixture.service.localChat(chat());
		fixture.service.combat(new CombatEvent("owned.combat", PhantomTopologyCoreSuite.LEFT_POINT, null, List.of(1L), 1, 1000, 3000));
		fixture.service.targetability(new TargetabilityEvent("owned.target", 2, 1, true, 1, 2000));
	}

	private static void register(PhantomTopologyService service, long profileId, PhantomTopologyPoint point, long sequence)
	{
		PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, service.registerProfile(profileId), "Generation test profile registration failed.");
		PhantomAssertions.assertEquals(UpdateResult.UPDATED, service.updateProfile(profileId, point, sequence), "Generation test profile update failed.");
	}

	private static LocalChatEvent chat()
	{
		return new LocalChatEvent("generation.chat", PhantomTopologyCoreSuite.LEFT_POINT, null, 1, 1000, 5000);
	}

	private static void runEvent(PhantomTopologyService service, AtomicReference<Throwable> failure)
	{
		try
		{
			service.localChat(chat());
		}
		catch (Throwable throwable)
		{
			failure.set(throwable);
		}
	}

	private static void blockDoor(PhantomTopologyCoreSuite.TestBackend backend, CountDownLatch entered, CountDownLatch release)
	{
		backend._doorStateHook = () ->
		{
			entered.countDown();
			try
			{
				if (!release.await(5, TimeUnit.SECONDS))
				{
					throw new AssertionError("Generation door query release timed out.");
				}
			}
			catch (InterruptedException exception)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
		};
	}

	private static Thread thread(String name, Runnable operation, List<Throwable> failures)
	{
		return Thread.ofPlatform().name(name).unstarted(() ->
		{
			try
			{
				operation.run();
			}
			catch (Throwable throwable)
			{
				failures.add(throwable);
			}
		});
	}

	private static void join(Thread thread, String description) throws Exception
	{
		thread.join(5000);
		PhantomAssertions.assertFalse(thread.isAlive(), description + " thread did not finish.");
	}

	private static void awaitBlocked(Thread thread, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while ((thread.getState() != Thread.State.BLOCKED) && thread.isAlive() && (System.nanoTime() < deadline))
		{
			Thread.sleep(5);
		}
		PhantomAssertions.assertTrue(thread.isAlive() && (thread.getState() == Thread.State.BLOCKED), message);
	}

	private static void stop(PhantomTopologyService service)
	{
		if (service.snapshot().state() == PhantomTopologyService.State.RUNNING)
		{
			service.beginStop();
		}
		PhantomAssertions.assertTrue(service.finishStop(), "Generation service fixture did not stop.");
	}

	private static Object field(Object target, String name) throws Exception
	{
		final Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void setProfileGeneration(PhantomTopologyService service, long profileId, long generation) throws Exception
	{
		final Object registry = field(service, "_profileRegistry");
		@SuppressWarnings("unchecked")
		final Map<Long, Object> entries = (Map<Long, Object>) field(registry, "_entries");
		final Object entry = entries.get(profileId);
		final Field generationField = entry.getClass().getDeclaredField("_topologyGeneration");
		generationField.setAccessible(true);
		generationField.setLong(entry, generation);
	}

	private static long activeGenerationUnsafe(PhantomTopologyService service)
	{
		try
		{
			final Object snapshot = field(service, "_snapshot");
			return (long) snapshot.getClass().getMethod("generation").invoke(snapshot);
		}
		catch (Exception exception)
		{
			throw new AssertionError(exception);
		}
	}

	private static void setSequencesToMaximum(PhantomTopologyService service) throws Exception
	{
		final Object provider = field(service, "_perceptionProvider");
		@SuppressWarnings("unchecked")
		final Map<Long, Object> ledgers = (Map<Long, Object>) field(provider, "_signalLedgers");
		PhantomAssertions.assertFalse(ledgers.isEmpty(), "Sequence fixture did not create a provider ledger.");
		for (Object ledger : ledgers.values())
		{
			for (String fieldName : List.of("_localChatSequence", "_combatSequence", "_targetabilitySequence"))
			{
				final Field sequence = ledger.getClass().getDeclaredField(fieldName);
				sequence.setAccessible(true);
				sequence.setLong(ledger, Long.MAX_VALUE);
			}
		}
	}

	private static void assertSequencesNonNegative(PhantomTopologyService service) throws Exception
	{
		final Object provider = field(service, "_perceptionProvider");
		@SuppressWarnings("unchecked")
		final Map<Long, Object> ledgers = (Map<Long, Object>) field(provider, "_signalLedgers");
		for (Object ledger : ledgers.values())
		{
			for (String fieldName : List.of("_localChatSequence", "_combatSequence", "_targetabilitySequence"))
			{
				final Field sequence = ledger.getClass().getDeclaredField(fieldName);
				sequence.setAccessible(true);
				PhantomAssertions.assertTrue(sequence.getLong(ledger) >= 0, "Provider source sequence wrapped negative.");
			}
		}
	}

	private static String simpleTopology(String activeNode, int datasetVersion)
	{
		final String inactiveNode = activeNode.equals("alpha") ? "beta" : "alpha";
		return topology(datasetVersion,
			node(activeNode, 100, 500, 100, 500) +
				node(inactiveNode, 700, 900, 700, 900));
	}

	private static String unresolvedTopology(int datasetVersion)
	{
		return topology(datasetVersion, node("alpha", 1000, 1200, 1000, 1200) + node("beta", 1400, 1600, 1400, 1600));
	}

	private static String doorTopology(int datasetVersion)
	{
		return topology(datasetVersion,
			"<node id=\"dungeon\" kind=\"DUNGEON\" instanceId=\"0\" form=\"CUBOID\" minX=\"0\" maxX=\"1000\" minY=\"0\" maxY=\"1000\" minZ=\"0\" maxZ=\"100\" />" +
				"<node id=\"dungeon.left\" kind=\"ROOM\" instanceId=\"0\" form=\"CUBOID\" minX=\"100\" maxX=\"500\" minY=\"100\" maxY=\"900\" minZ=\"0\" maxZ=\"100\" parentId=\"dungeon\" />" +
				"<node id=\"dungeon.right\" kind=\"CORRIDOR\" instanceId=\"0\" form=\"CUBOID\" minX=\"501\" maxX=\"900\" minY=\"100\" maxY=\"900\" minZ=\"0\" maxZ=\"100\" parentId=\"dungeon\" />" +
				"<anchor id=\"door.left\" role=\"DOOR_SIDE\" nodeId=\"dungeon.left\" x=\"490\" y=\"500\" z=\"10\" instanceId=\"0\" tolerance=\"0\" />" +
				"<anchor id=\"door.right\" role=\"DOOR_SIDE\" nodeId=\"dungeon.right\" x=\"510\" y=\"500\" z=\"10\" instanceId=\"0\" tolerance=\"0\" />" +
				"<edge id=\"dungeon.door\" fromNodeId=\"dungeon.left\" toNodeId=\"dungeon.right\" mode=\"DOOR\" bidirectional=\"true\" baseCost=\"1\" baseTravelMillis=\"1000\" backgroundEligible=\"false\" channels=\"LOCAL_CHAT,COMBAT,TARGETABILITY\" doorId=\"500\" fromAnchorId=\"door.left\" toAnchorId=\"door.right\" />");
	}

	private static String topology(int datasetVersion, String entities)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><topology schemaVersion=\"1\" datasetId=\"generation\" datasetVersion=\"" + datasetVersion + "\">" + entities + "</topology>";
	}

	private static String node(String id, int minX, int maxX, int minY, int maxY)
	{
		return "<node id=\"" + id + "\" kind=\"OUTDOOR_AREA\" instanceId=\"0\" form=\"CUBOID\" minX=\"" + minX + "\" maxX=\"" + maxX + "\" minY=\"" + minY + "\" maxY=\"" + maxY + "\" minZ=\"0\" maxZ=\"100\" />";
	}

	private record ServiceFixture(PhantomTopologyService service, RecordingSignalPort port)
	{
	}

	private static final class ReloadFixture implements AutoCloseable
	{
		private final Path _directory;
		private final Path _xml;
		private final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		private final RecordingSignalPort port = new RecordingSignalPort();
		private final PhantomTopologyService service;

		private ReloadFixture(String initialXml) throws Exception
		{
			_directory = Files.createTempDirectory("phantom-topology-generation-");
			_xml = _directory.resolve("generation.xml");
			write(initialXml);
			final PhantomTopologyPolicy policy = PhantomTopologyCoreSuite.POLICY;
			service = new PhantomTopologyService(new PhantomTopologyLoader(_directory, backend, policy), backend, policy, port);
			PhantomAssertions.assertTrue(service.start(), "Reload fixture did not start.");
		}

		private void write(String xml) throws Exception
		{
			Files.writeString(_xml, xml);
		}

		@Override
		public void close() throws Exception
		{
			stop(service);
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
		private volatile Runnable _withdrawObserver;
		private volatile CountDownLatch _submitEntered;
		private volatile CountDownLatch _submitRelease;

		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			_deliveries.add(new Delivery(profileId, signal.sourceKey(), signal.sequence(), false));
			final CountDownLatch entered = _submitEntered;
			final CountDownLatch release = _submitRelease;
			if ((entered != null) && (release != null))
			{
				_submitEntered = null;
				_submitRelease = null;
				entered.countDown();
				try
				{
					if (!release.await(5, TimeUnit.SECONDS))
					{
						throw new AssertionError("Scheduler submit release timed out.");
					}
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
			}
			return _submitStatus;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			final Runnable observer = _withdrawObserver;
			if (observer != null)
			{
				observer.run();
			}
			_deliveries.add(new Delivery(profileId, sourceKey, sequence, true));
			return _withdrawStatus;
		}

		void blockNextSubmit(CountDownLatch entered, CountDownLatch release)
		{
			_submitEntered = entered;
			_submitRelease = release;
		}

		void clear()
		{
			_deliveries.clear();
		}

		List<Delivery> deliveries()
		{
			return List.copyOf(_deliveries);
		}

		Delivery last(long profileId, String sourceKey)
		{
			return deliveries().stream().filter(delivery -> (delivery.profileId() == profileId) && delivery.sourceKey().equals(sourceKey)).reduce((_, second) -> second).orElseThrow();
		}

		Set<String> withdrawnSources(long profileId)
		{
			return deliveries().stream().filter(delivery -> (delivery.profileId() == profileId) && delivery.withdraw()).map(Delivery::sourceKey).collect(java.util.stream.Collectors.toSet());
		}

		Map<String, Long> lastSequences(long profileId)
		{
			final HashMap<String, Long> result = new HashMap<>();
			deliveries().stream().filter(delivery -> delivery.profileId() == profileId).forEach(delivery -> result.put(delivery.sourceKey(), delivery.sequence()));
			return Map.copyOf(result);
		}
	}
}
