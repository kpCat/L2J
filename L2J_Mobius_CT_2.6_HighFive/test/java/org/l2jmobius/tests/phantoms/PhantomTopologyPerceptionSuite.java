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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.CombatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.EventStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.LocalChatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.TargetabilityEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyEdgeMode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;

public final class PhantomTopologyPerceptionSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "topology-perception";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-provider-starts-empty", _ -> testStartsEmpty());
		registry.add("02-explicit-registration", _ -> testRegistration());
		registry.add("03-duplicate-registration", _ -> testDuplicateRegistration());
		registry.add("04-registration-capacity", _ -> testCapacity());
		registry.add("05-position-update", _ -> testUpdate());
		registry.add("06-stale-position-sequence", _ -> testStale());
		registry.add("07-unresolved-position", _ -> testUnresolved());
		registry.add("08-most-specific-position", _ -> testMostSpecific());
		registry.add("09-explicit-unregister", _ -> testUnregister());
		registry.add("10-same-node-local-chat", _ -> testSameNodeChat());
		registry.add("11-neighbor-local-chat", _ -> testNeighborChat());
		registry.add("12-closed-door-blocks-neighbor", _ -> testClosedDoor());
		registry.add("13-radius-blocks-neighbor", _ -> testRadius());
		registry.add("14-channel-blocks-neighbor", _ -> testChannel());
		registry.add("15-combat-participant-active", _ -> testCombatParticipant());
		registry.add("16-combat-neighbor-nearby", _ -> testCombatNeighbor());
		registry.add("17-combat-participant-overrides-nearby", _ -> testParticipantOverride());
		registry.add("18-targetability-active", _ -> testTargetabilityActive());
		registry.add("19-targetability-withdraw", _ -> testTargetabilityWithdraw());
		registry.add("20-provider-sequence-monotonic", _ -> testSequence());
		registry.add("21-backpressure-isolated", _ -> testBackpressureIsolation());
		registry.add("22-not-registered-isolated", _ -> testNotRegisteredIsolation());
		registry.add("23-recipient-cap", _ -> testRecipientCap());
		registry.add("24-event-has-no-message-text", _ -> testNoMessageText());
		registry.add("25-perceptible-minimum-hard-gate", _ -> testMinimum());
		registry.add("26-stop-race-and-quiescence", _ -> testStopRace());
		registry.add("27-stopped-operations-rejected", _ -> testStopped());
		registry.add("28-no-materialization-navigation-reference", _ -> testNoDirectSubsystemReference());
	}

	private void testStartsEmpty()
	{
		final Fixture fixture = fixture();
		PhantomAssertions.assertEquals(PhantomPerceptionProvider.State.RUNNING, fixture.provider.snapshot().state(), "Perception provider did not start.");
		PhantomAssertions.assertEquals(0, fixture.provider.snapshot().registeredProfiles(), "Provider discovered profiles automatically.");
		PhantomAssertions.assertEquals(0, fixture.provider.snapshot().eventsInFlight(), "Provider created an automatic event.");
		stop(fixture);
	}

	private void testRegistration()
	{
		final Fixture fixture = fixture();
		PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.registry.register(1), "Explicit topology profile registration failed.");
		PhantomAssertions.assertEquals(1, fixture.registry.size(), "Registered topology profile count changed.");
		stop(fixture);
	}

	private void testDuplicateRegistration()
	{
		final Fixture fixture = fixture();
		fixture.registry.register(1);
		PhantomAssertions.assertEquals(RegistrationResult.ALREADY_REGISTERED, fixture.registry.register(1), "Duplicate topology profile was not isolated.");
		stop(fixture);
	}

	private void testCapacity()
	{
		final Fixture fixture = fixture(policyWith(2, 1024));
		fixture.registry.register(1);
		fixture.registry.register(2);
		PhantomAssertions.assertEquals(RegistrationResult.CAPACITY_REACHED, fixture.registry.register(3), "Topology profile capacity was not enforced.");
		stop(fixture);
	}

	private void testUpdate()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		final var profile = fixture.registry.find(1).orElseThrow();
		PhantomAssertions.assertEquals("dungeon.left", profile.nodeId(), "Topology profile did not resolve its node.");
		PhantomAssertions.assertEquals(1L, profile.sequence(), "Topology profile sequence changed.");
		stop(fixture);
	}

	private void testStale()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		PhantomAssertions.assertEquals(UpdateResult.STALE, fixture.registry.update(1, PhantomTopologyCoreSuite.RIGHT_POINT, 1), "Stale topology position update was accepted.");
		PhantomAssertions.assertEquals("dungeon.left", fixture.registry.find(1).orElseThrow().nodeId(), "Stale update changed topology membership.");
		stop(fixture);
	}

	private void testUnresolved()
	{
		final Fixture fixture = fixture();
		fixture.registry.register(1);
		PhantomAssertions.assertEquals(UpdateResult.UPDATED, fixture.registry.update(1, PhantomTopologyCoreSuite.point(1500, 1500), 1), "Unresolved position update was rejected.");
		PhantomAssertions.assertFalse(fixture.registry.find(1).orElseThrow().resolved(), "Unresolved topology position was hidden.");
		stop(fixture);
	}

	private void testMostSpecific()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		PhantomAssertions.assertEquals("dungeon.left", fixture.registry.find(1).orElseThrow().nodeId(), "Profile registry did not select deepest/smallest/ID node.");
		stop(fixture);
	}

	private void testUnregister()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		PhantomAssertions.assertEquals(PhantomTopologyProfileRegistry.UnregisterResult.UNREGISTERED, fixture.registry.unregister(1), "Explicit topology unregister failed.");
		PhantomAssertions.assertTrue(fixture.registry.find(1).isEmpty(), "Unregistered topology profile remained visible.");
		stop(fixture);
	}

	private void testSameNodeChat()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.point(320, 500));
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(1, result.delivered(), "Same-node local chat was not delivered.");
		assertLastState(fixture, 1, PhantomActivityState.NEARBY_PERCEPTIBLE);
		stop(fixture);
	}

	private void testNeighborChat()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(1, result.delivered(), "Allowed one-hop neighbor did not receive local chat relevance.");
		assertLastState(fixture, 1, PhantomActivityState.NEARBY_PERCEPTIBLE);
		stop(fixture);
	}

	private void testClosedDoor()
	{
		final Fixture fixture = fixture();
		fixture.backend._doorStates.put(500, DoorState.CLOSED);
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(0, result.considered(), "Closed door permitted neighbor perception.");
		stop(fixture);
	}

	private void testRadius()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 100));
		PhantomAssertions.assertEquals(0, result.considered(), "Out-of-radius topology neighbor was considered.");
		stop(fixture);
	}

	private void testChannel()
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		final List<PhantomTopologyAnchor> anchors = PhantomTopologyCoreSuite.baseDoorAnchors();
		final PhantomTopologyEdge combatOnly = new PhantomTopologyEdge("dungeon.door", "dungeon.left", "dungeon.right", PhantomTopologyEdgeMode.DOOR, true, 1, 1000, false, Set.of(PhantomPerceptionChannel.COMBAT), 500, anchors.get(0).id(), anchors.get(1).id(), List.of());
		final PhantomTopologySnapshot snapshot = PhantomTopologyCoreSuite.create(PhantomTopologyCoreSuite.baseNodes(), anchors, List.of(combatOnly), backend);
		final Fixture fixture = fixture(PhantomTopologyCoreSuite.POLICY, backend, snapshot);
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		PhantomAssertions.assertEquals(0, fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000)).considered(), "Missing LOCAL_CHAT channel permitted perception.");
		stop(fixture);
	}

	private void testCombatParticipant()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		fixture.provider.combat(combat(List.of(1L)));
		assertLastState(fixture, 1, PhantomActivityState.ACTIVE);
		stop(fixture);
	}

	private void testCombatNeighbor()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		fixture.provider.combat(combat(List.of()));
		assertLastState(fixture, 1, PhantomActivityState.NEARBY_PERCEPTIBLE);
		stop(fixture);
	}

	private void testParticipantOverride()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		fixture.provider.combat(combat(List.of(1L)));
		PhantomAssertions.assertEquals(1, fixture.port.signals().size(), "Combat participant received duplicate relevance signals.");
		assertLastState(fixture, 1, PhantomActivityState.ACTIVE);
		stop(fixture);
	}

	private void testTargetabilityActive()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.RIGHT_POINT);
		fixture.provider.targetability(new TargetabilityEvent("target.active", 1, 2, true, 1, 2000));
		assertLastState(fixture, 2, PhantomActivityState.ACTIVE);
		stop(fixture);
	}

	private void testTargetabilityWithdraw()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.RIGHT_POINT);
		fixture.provider.targetability(new TargetabilityEvent("target.active", 1, 2, true, 1, 2000));
		fixture.provider.targetability(new TargetabilityEvent("target.inactive", 1, 2, false, 2, 2000));
		final Delivery last = fixture.port.deliveries().getLast();
		PhantomAssertions.assertTrue(last.withdraw(), "Inactive targetability did not withdraw its fixed source.");
		PhantomAssertions.assertEquals(PhantomPerceptionProvider.TARGETABILITY_SOURCE, last.sourceKey(), "Targetability withdraw source changed.");
		stop(fixture);
	}

	private void testSequence()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		fixture.provider.localChat(new LocalChatEvent("chat.second", PhantomTopologyCoreSuite.LEFT_POINT, null, 2, 1000, 5000));
		final List<Long> sequences = fixture.port.signals().stream().map(signal -> signal.signal().sequence()).toList();
		PhantomAssertions.assertEquals(List.of(1L, 2L), sequences, "Provider-owned profile/source sequence is not monotonic.");
		stop(fixture);
	}

	private void testBackpressureIsolation()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.LEFT_POINT);
		fixture.port._statusByProfile.put(1L, SignalDelivery.BACKPRESSURE);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(1, result.backpressured(), "Backpressured topology recipient was not counted.");
		PhantomAssertions.assertEquals(1, result.delivered(), "Backpressure aborted remaining topology recipients.");
		stop(fixture);
	}

	private void testNotRegisteredIsolation()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.LEFT_POINT);
		fixture.port._statusByProfile.put(1L, SignalDelivery.NOT_REGISTERED);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(1, result.unregistered(), "Scheduler NOT_REGISTERED result was not isolated.");
		PhantomAssertions.assertEquals(1, result.delivered(), "Scheduler NOT_REGISTERED aborted event fanout.");
		stop(fixture);
	}

	private void testRecipientCap()
	{
		final Fixture fixture = fixture(policyWith(10, 2));
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 3, PhantomTopologyCoreSuite.LEFT_POINT);
		final var result = fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		PhantomAssertions.assertEquals(2, result.considered(), "Topology event recipient cap was not enforced.");
		stop(fixture);
	}

	private void testNoMessageText()
	{
		final Set<String> names = java.util.Arrays.stream(LocalChatEvent.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
		PhantomAssertions.assertFalse(names.contains("message") || names.contains("text"), "Local chat event stores message text.");
	}

	private void testMinimum()
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.LEFT_POINT);
		register(fixture, 2, PhantomTopologyCoreSuite.RIGHT_POINT);
		fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
		fixture.provider.combat(combat(List.of()));
		for (SubmittedSignal signal : fixture.port.signals())
		{
			PhantomAssertions.assertTrue(signal.signal().requiredState().code() <= PhantomActivityState.NEARBY_PERCEPTIBLE.code(), "Perceptible recipient received a state below NEARBY_PERCEPTIBLE.");
		}
		stop(fixture);
	}

	private void testStopRace() throws Exception
	{
		final Fixture fixture = fixture();
		register(fixture, 1, PhantomTopologyCoreSuite.RIGHT_POINT);
		final CountDownLatch enteredDoorCheck = new CountDownLatch(1);
		final CountDownLatch releaseDoorCheck = new CountDownLatch(1);
		fixture.backend._doorStateHook = () ->
		{
			enteredDoorCheck.countDown();
			try
			{
				if (!releaseDoorCheck.await(5, TimeUnit.SECONDS))
				{
					throw new AssertionError("Door-check release timed out.");
				}
			}
			catch (InterruptedException exception)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
		};
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread eventThread = Thread.ofPlatform().name("topology-stop-race-test").unstarted(() ->
		{
			try
			{
				fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000));
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		});
		eventThread.start();
		PhantomAssertions.assertTrue(enteredDoorCheck.await(5, TimeUnit.SECONDS), "Perception event did not enter live door check.");
		PhantomAssertions.assertTrue(fixture.provider.beginStop(), "Perception beginStop failed during in-flight event.");
		PhantomAssertions.assertFalse(fixture.provider.finishStop(), "Perception finishStop cleared an in-flight event token.");
		releaseDoorCheck.countDown();
		eventThread.join(5000);
		PhantomAssertions.assertFalse(eventThread.isAlive(), "Perception event thread remained blocked.");
		PhantomAssertions.assertEquals(null, failure.get(), "Perception event failed during stop race.");
		PhantomAssertions.assertTrue(fixture.provider.finishStop(), "Perception provider did not finish after exact event release.");
		PhantomAssertions.assertEquals(0, fixture.port.deliveries().size(), "Scheduler delivery began after topology STOPPING.");
	}

	private void testStopped()
	{
		final Fixture fixture = fixture();
		fixture.provider.beginStop();
		PhantomAssertions.assertTrue(fixture.provider.finishStop(), "Perception provider did not stop.");
		PhantomAssertions.assertEquals(RegistrationResult.NOT_RUNNING, fixture.registry.register(1), "Stopped profile registry accepted registration.");
		PhantomAssertions.assertEquals(EventStatus.NOT_RUNNING, fixture.provider.localChat(chat(PhantomTopologyCoreSuite.LEFT_POINT, 1000)).status(), "Stopped provider accepted an event.");
	}

	private void testNoDirectSubsystemReference()
	{
		for (java.lang.reflect.Field field : PhantomPerceptionProvider.class.getDeclaredFields())
		{
			final String type = field.getType().getName();
			PhantomAssertions.assertFalse(type.contains("Materialization") || type.contains("Navigation"), "Perception provider directly references materialization/navigation.");
		}
	}

	private static Fixture fixture()
	{
		return fixture(PhantomTopologyCoreSuite.POLICY);
	}

	private static Fixture fixture(PhantomTopologyPolicy policy)
	{
		final PhantomTopologyCoreSuite.TestBackend backend = new PhantomTopologyCoreSuite.TestBackend();
		return fixture(policy, backend, PhantomTopologyCoreSuite.snapshot(backend));
	}

	private static Fixture fixture(PhantomTopologyPolicy policy, PhantomTopologyCoreSuite.TestBackend backend, PhantomTopologySnapshot snapshot)
	{
		final PhantomTopologyMetrics metrics = new PhantomTopologyMetrics();
		final PhantomTopologyQuery query = new PhantomTopologyQuery(snapshot, backend, metrics);
		final RecordingSignalPort port = new RecordingSignalPort();
		final PhantomTopologyProfileRegistry registry = new PhantomTopologyProfileRegistry(policy.maximumRegisteredProfiles(), () -> query, metrics);
		final PhantomPerceptionProvider provider = new PhantomPerceptionProvider(policy, registry, () -> query, port, metrics);
		PhantomAssertions.assertTrue(provider.start(), "Perception fixture did not start.");
		return new Fixture(backend, registry, provider, port);
	}

	private static PhantomTopologyPolicy policyWith(int profiles, int recipients)
	{
		final PhantomTopologyPolicy policy = PhantomTopologyCoreSuite.POLICY;
		return new PhantomTopologyPolicy(policy.maximumFiles(), policy.maximumNodes(), policy.maximumAnchors(), policy.maximumEdges(), policy.maximumHierarchyDepth(), policy.maximumTags(), policy.maximumSourceReferences(), policy.maximumVertices(), profiles, policy.maximumConcurrentEvents(), recipients, policy.maximumNeighborNodesPerEvent(), policy.maximumEventRadius(), policy.maximumReturnedNodes(), policy.maximumReturnedEdges(), policy.maximumGraphNodes(), policy.spatialCellSize(), policy.maximumSpatialReferencesPerNode(), policy.maximumOversizedSpatialNodes(), policy.defaultLocalChatTtlMillis(), policy.defaultCombatTtlMillis(), policy.defaultTargetabilityTtlMillis());
	}

	private static void register(Fixture fixture, long profileId, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint point)
	{
		PhantomAssertions.assertEquals(RegistrationResult.REGISTERED, fixture.registry.register(profileId), "Topology test profile registration failed.");
		PhantomAssertions.assertEquals(UpdateResult.UPDATED, fixture.registry.update(profileId, point, 1), "Topology test profile position failed.");
	}

	private static LocalChatEvent chat(org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint point, int radius)
	{
		return new LocalChatEvent("chat.event", point, null, 1, radius, 5000);
	}

	private static CombatEvent combat(List<Long> participants)
	{
		return new CombatEvent("combat.event", PhantomTopologyCoreSuite.LEFT_POINT, null, participants, 1, 1000, 3000);
	}

	private static void assertLastState(Fixture fixture, long profileId, PhantomActivityState state)
	{
		final SubmittedSignal signal = fixture.port.signals().stream().filter(value -> value.profileId() == profileId).reduce((_, second) -> second).orElseThrow();
		PhantomAssertions.assertEquals(state, signal.signal().requiredState(), "Topology relevance state changed.");
	}

	private static void stop(Fixture fixture)
	{
		fixture.provider.beginStop();
		PhantomAssertions.assertTrue(fixture.provider.finishStop(), "Perception fixture did not stop.");
	}

	private record Fixture(PhantomTopologyCoreSuite.TestBackend backend, PhantomTopologyProfileRegistry registry, PhantomPerceptionProvider provider, RecordingSignalPort port)
	{
	}

	private record SubmittedSignal(long profileId, PhantomRelevanceSignal signal)
	{
	}

	private record Delivery(long profileId, String sourceKey, long sequence, boolean withdraw)
	{
	}

	private static final class RecordingSignalPort implements PhantomRelevanceSignalPort
	{
		private final List<SubmittedSignal> _signals = Collections.synchronizedList(new ArrayList<>());
		private final List<Delivery> _deliveries = Collections.synchronizedList(new ArrayList<>());
		private final Map<Long, SignalDelivery> _statusByProfile = new ConcurrentHashMap<>();

		@Override
		public SignalDelivery submit(long profileId, PhantomRelevanceSignal signal)
		{
			_signals.add(new SubmittedSignal(profileId, signal));
			_deliveries.add(new Delivery(profileId, signal.sourceKey(), signal.sequence(), false));
			return _statusByProfile.getOrDefault(profileId, SignalDelivery.ACCEPTED);
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			_deliveries.add(new Delivery(profileId, sourceKey, sequence, true));
			return _statusByProfile.getOrDefault(profileId, SignalDelivery.ACCEPTED);
		}

		List<SubmittedSignal> signals()
		{
			return List.copyOf(_signals);
		}

		List<Delivery> deliveries()
		{
			return List.copyOf(_deliveries);
		}
	}
}
