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
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyGenerationCoordinator.View;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RemovalResult;

/**
 * Synchronous one-hop perception provider with exact generation and signal
 * delivery ownership.
 */
public final class PhantomPerceptionProvider
{
	public static final String LOCAL_CHAT_SOURCE = "topology.local_chat";
	public static final String COMBAT_SOURCE = "topology.combat";
	public static final String TARGETABILITY_SOURCE = "topology.targetability";
	public static final List<String> OWNED_SOURCES = List.of(LOCAL_CHAT_SOURCE, COMBAT_SOURCE, TARGETABILITY_SOURCE);

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum EventStatus
	{
		ACCEPTED,
		INVALID,
		SIGNAL_FAILURE,
		NOT_RUNNING,
		BACKPRESSURE
	}

	enum CleanupStatus
	{
		COMPLETE,
		FAILED,
		NOT_REQUIRED,
		NOT_RUNNING
	}

	record UnregisterAttempt(RemovalResult removal, CleanupStatus cleanup)
	{
	}

	public record LocalChatEvent(String eventId, PhantomTopologyPoint sourcePoint, String sourceNodeId, long logicalTime, int radius, long ttlMillis)
	{
		public LocalChatEvent
		{
			eventId = PhantomTopologyPolicy.requireId(eventId, "local chat event id");
			Objects.requireNonNull(sourcePoint, "sourcePoint");
			if (sourceNodeId != null)
			{
				sourceNodeId = PhantomTopologyPolicy.requireId(sourceNodeId, "local chat source node id");
			}
			if ((logicalTime < 0) || (radius < 0) || (radius > 100_000) || (ttlMillis < 1) || (ttlMillis > PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS))
			{
				throw new IllegalArgumentException("Invalid local chat event bounds.");
			}
		}
	}

	public record CombatEvent(String eventId, PhantomTopologyPoint sourcePoint, String sourceNodeId, List<Long> participantProfileIds, long logicalTime, int radius, long ttlMillis)
	{
		public CombatEvent
		{
			eventId = PhantomTopologyPolicy.requireId(eventId, "combat event id");
			Objects.requireNonNull(sourcePoint, "sourcePoint");
			if (sourceNodeId != null)
			{
				sourceNodeId = PhantomTopologyPolicy.requireId(sourceNodeId, "combat source node id");
			}
			participantProfileIds = List.copyOf(participantProfileIds);
			if ((participantProfileIds.size() > 32) || participantProfileIds.stream().anyMatch(id -> id == null || id <= 0) || (participantProfileIds.stream().distinct().count() != participantProfileIds.size()) || (logicalTime < 0) || (radius < 0) || (radius > 100_000) || (ttlMillis < 1) || (ttlMillis > PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS))
			{
				throw new IllegalArgumentException("Invalid combat event bounds.");
			}
		}
	}

	public record TargetabilityEvent(String eventId, long observerProfileId, long targetProfileId, boolean active, long logicalTime, long ttlMillis)
	{
		public TargetabilityEvent
		{
			eventId = PhantomTopologyPolicy.requireId(eventId, "targetability event id");
			if ((observerProfileId <= 0) || (targetProfileId <= 0) || (observerProfileId == targetProfileId) || (logicalTime < 0) || (ttlMillis < 1) || (ttlMillis > PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS))
			{
				throw new IllegalArgumentException("Invalid targetability event bounds.");
			}
		}
	}

	public record EventResult(EventStatus status, int considered, int delivered, int backpressured, int unregistered)
	{
	}

	private final Object _monitor = new Object();
	private final Object _deliveryGate = new Object();
	private final PhantomTopologyPolicy _policy;
	private final PhantomTopologyProfileRegistry _registry;
	private final PhantomTopologyGenerationCoordinator _generationCoordinator;
	private final Supplier<View> _viewSupplier;
	private final PhantomRelevanceSignalPort _signalPort;
	private final PhantomTopologyMetrics _metrics;
	private final Map<SequenceKey, Long> _sequences = new HashMap<>();
	private final Set<Long> _pendingCleanup = new HashSet<>();
	private final Set<Long> _activeEventTokens = new HashSet<>();
	private State _state = State.NEW;
	private long _eventGeneration;
	private long _nextEventToken;
	private int _cleanupInFlight;

	PhantomPerceptionProvider(PhantomTopologyPolicy policy, PhantomTopologyProfileRegistry registry, PhantomTopologyGenerationCoordinator generationCoordinator, Supplier<View> viewSupplier, PhantomRelevanceSignalPort signalPort, PhantomTopologyMetrics metrics)
	{
		_policy = Objects.requireNonNull(policy, "policy");
		_registry = Objects.requireNonNull(registry, "registry");
		_generationCoordinator = Objects.requireNonNull(generationCoordinator, "generationCoordinator");
		_viewSupplier = Objects.requireNonNull(viewSupplier, "viewSupplier");
		_signalPort = Objects.requireNonNull(signalPort, "signalPort");
		_metrics = Objects.requireNonNull(metrics, "metrics");
	}

	boolean start(long generation)
	{
		synchronized (_monitor)
		{
			if ((_state != State.NEW) || !_registry.start(generation))
			{
				return false;
			}
			_state = State.RUNNING;
			return true;
		}
	}

	RegistrationResult registerProfile(long profileId, long generation)
	{
		synchronized (_deliveryGate)
		{
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return RegistrationResult.NOT_RUNNING;
				}
				if (_pendingCleanup.contains(profileId))
				{
					return RegistrationResult.CLEANUP_PENDING;
				}
			}
			return _registry.register(profileId, generation);
		}
	}

	UnregisterAttempt unregisterProfile(long profileId, long generation)
	{
		synchronized (_deliveryGate)
		{
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return new UnregisterAttempt(RemovalResult.NOT_RUNNING, CleanupStatus.NOT_RUNNING);
				}
			}
			final RemovalResult removal = _registry.remove(profileId, generation);
			if (removal != RemovalResult.UNREGISTERED)
			{
				return new UnregisterAttempt(removal, CleanupStatus.NOT_REQUIRED);
			}
			final CleanupStatus cleanup = cleanupSources(profileId);
			synchronized (_monitor)
			{
				if (cleanup == CleanupStatus.COMPLETE)
				{
					_pendingCleanup.remove(profileId);
				}
				else
				{
					_pendingCleanup.add(profileId);
					_metrics.recordSignalCleanupFailure();
				}
			}
			return new UnregisterAttempt(removal, cleanup);
		}
	}

	CleanupStatus retryProfileSignalCleanup(long profileId)
	{
		synchronized (_deliveryGate)
		{
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return CleanupStatus.NOT_RUNNING;
				}
				if (!_pendingCleanup.contains(profileId))
				{
					return CleanupStatus.NOT_REQUIRED;
				}
				_metrics.recordSignalCleanupRetry();
			}
			final CleanupStatus cleanup = cleanupSources(profileId);
			synchronized (_monitor)
			{
				if (cleanup == CleanupStatus.COMPLETE)
				{
					_pendingCleanup.remove(profileId);
				}
				else
				{
					_metrics.recordSignalCleanupFailure();
				}
			}
			return cleanup;
		}
	}

	CleanupStatus invalidateForReload(List<Long> profileIds)
	{
		synchronized (_deliveryGate)
		{
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return CleanupStatus.NOT_RUNNING;
				}
				_cleanupInFlight++;
			}
			boolean complete = true;
			try
			{
				for (Long profileId : profileIds.stream().sorted().toList())
				{
					complete &= withdrawOwnedSources(profileId);
				}
			}
			finally
			{
				synchronized (_monitor)
				{
					_cleanupInFlight--;
				}
			}
			return complete ? CleanupStatus.COMPLETE : CleanupStatus.FAILED;
		}
	}

	public EventResult localChat(LocalChatEvent event)
	{
		Objects.requireNonNull(event, "event");
		final EventToken token = claim();
		if (token == null)
		{
			return rejectedStatus();
		}
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = _viewSupplier.get();
			if ((view == null) || !isCurrent(token))
			{
				return new EventResult(EventStatus.NOT_RUNNING, 0, 0, 0, 0);
			}
			return fanout(token, view, event.sourcePoint(), event.sourceNodeId(), event.radius(), event.ttlMillis(), PhantomPerceptionChannel.LOCAL_CHAT, LOCAL_CHAT_SOURCE, Set.of());
		}
		finally
		{
			release(token);
		}
	}

	public EventResult combat(CombatEvent event)
	{
		Objects.requireNonNull(event, "event");
		final EventToken token = claim();
		if (token == null)
		{
			return rejectedStatus();
		}
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = _viewSupplier.get();
			if ((view == null) || !isCurrent(token))
			{
				return new EventResult(EventStatus.NOT_RUNNING, 0, 0, 0, 0);
			}
			return fanout(token, view, event.sourcePoint(), event.sourceNodeId(), event.radius(), event.ttlMillis(), PhantomPerceptionChannel.COMBAT, COMBAT_SOURCE, Set.copyOf(event.participantProfileIds()));
		}
		finally
		{
			release(token);
		}
	}

	public EventResult targetability(TargetabilityEvent event)
	{
		Objects.requireNonNull(event, "event");
		final EventToken token = claim();
		if (token == null)
		{
			return rejectedStatus();
		}
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = _viewSupplier.get();
			if ((view == null) || !isCurrent(token))
			{
				return new EventResult(EventStatus.NOT_RUNNING, 0, 0, 0, 0);
			}
			final SignalDelivery delivery;
			if (event.active())
			{
				if (_registry.find(event.targetProfileId(), view.generation()).isEmpty())
				{
					_metrics.recordRecipientUnregistered();
					return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 1);
				}
				delivery = deliver(token, event.targetProfileId(), TARGETABILITY_SOURCE, PhantomActivityState.ACTIVE, event.ttlMillis(), view.generation(), true);
			}
			else
			{
				delivery = withdrawEvent(token, event.targetProfileId(), TARGETABILITY_SOURCE);
			}
			_metrics.recordTargetabilitySignal();
			return resultForSingle(delivery);
		}
		finally
		{
			release(token);
		}
	}

	private EventResult fanout(EventToken token, View view, PhantomTopologyPoint sourcePoint, String sourceNodeId, int radius, long ttlMillis, PhantomPerceptionChannel channel, String sourceKey, Set<Long> participants)
	{
		final PhantomTopologyQuery query = view.query();
		final Optional<PhantomTopologyNode> eventNode = sourceNodeId == null ? query.mostSpecificNode(sourcePoint) : query.findNode(sourceNodeId).filter(node -> node.area().contains(sourcePoint));
		final LinkedHashMap<Long, PhantomActivityState> recipients = new LinkedHashMap<>();
		participants.stream().sorted().forEach(profileId ->
		{
			if ((recipients.size() < _policy.maximumRecipientsPerEvent()) && _registry.find(profileId, view.generation()).isPresent())
			{
				recipients.put(profileId, PhantomActivityState.ACTIVE);
			}
		});
		if (eventNode.isPresent())
		{
			final LinkedHashSet<String> perceptibleNodes = new LinkedHashSet<>();
			perceptibleNodes.add(eventNode.get().id());
			for (PhantomTopologyEdge edge : query.edges(eventNode.get().id()))
			{
				if (perceptibleNodes.size() > _policy.maximumNeighborNodesPerEvent())
				{
					break;
				}
				final String neighbor = edge.otherNode(eventNode.get().id());
				if ((neighbor != null) && query.isPerceptible(edge.id(), channel))
				{
					perceptibleNodes.add(neighbor);
				}
			}
			final long radiusSquared = (long) radius * radius;
			for (ProfileTopologySnapshot profile : _registry.listForNodes(perceptibleNodes, _policy.maximumRecipientsPerEvent(), view.generation()))
			{
				if ((recipients.size() >= _policy.maximumRecipientsPerEvent()) && !recipients.containsKey(profile.profileId()))
				{
					break;
				}
				if ((profile.point() != null) && (profile.point().distanceSquared2D(sourcePoint) <= radiusSquared))
				{
					recipients.putIfAbsent(profile.profileId(), PhantomActivityState.NEARBY_PERCEPTIBLE);
				}
			}
		}
		int considered = 0;
		int delivered = 0;
		int backpressured = 0;
		int unregistered = 0;
		boolean signalFailure = false;
		for (Map.Entry<Long, PhantomActivityState> recipient : recipients.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
		{
			considered++;
			_metrics.recordRecipientConsidered();
			final SignalDelivery delivery = deliver(token, recipient.getKey(), sourceKey, recipient.getValue(), ttlMillis, view.generation(), true);
			if ((delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED))
			{
				delivered++;
				_metrics.recordRecipientDelivered();
			}
			else if (delivery == SignalDelivery.BACKPRESSURE)
			{
				backpressured++;
				_metrics.recordRecipientBackpressured();
			}
			else if (delivery == SignalDelivery.NOT_REGISTERED)
			{
				unregistered++;
				_metrics.recordRecipientUnregistered();
			}
			else if (delivery == SignalDelivery.SEQUENCE_EXHAUSTED)
			{
				signalFailure = true;
			}
			if (channel == PhantomPerceptionChannel.LOCAL_CHAT)
			{
				_metrics.recordLocalChatSignal();
			}
			else
			{
				_metrics.recordCombatSignal();
			}
		}
		return new EventResult(signalFailure ? EventStatus.SIGNAL_FAILURE : EventStatus.ACCEPTED, considered, delivered, backpressured, unregistered);
	}

	private SignalDelivery deliver(EventToken token, long profileId, String sourceKey, PhantomActivityState requiredState, long ttlMillis, long topologyGeneration, boolean registrationRequired)
	{
		if (requiredState.code() > PhantomActivityState.NEARBY_PERCEPTIBLE.code())
		{
			throw new IllegalStateException("Perceptible topology signal is below NEARBY_PERCEPTIBLE.");
		}
		synchronized (_deliveryGate)
		{
			if (!isCurrent(token))
			{
				return SignalDelivery.NOT_RUNNING;
			}
			if (registrationRequired && _registry.find(profileId, topologyGeneration).isEmpty())
			{
				return SignalDelivery.NOT_REGISTERED;
			}
			final Long sequence = allocateSequence(profileId, sourceKey);
			if (sequence == null)
			{
				return SignalDelivery.SEQUENCE_EXHAUSTED;
			}
			return _signalPort.submit(profileId, new PhantomRelevanceSignal(sourceKey, sequence, requiredState, ttlMillis));
		}
	}

	private SignalDelivery withdrawEvent(EventToken token, long profileId, String sourceKey)
	{
		synchronized (_deliveryGate)
		{
			if (!isCurrent(token))
			{
				return SignalDelivery.NOT_RUNNING;
			}
			final Long sequence = allocateSequence(profileId, sourceKey);
			if (sequence == null)
			{
				return SignalDelivery.SEQUENCE_EXHAUSTED;
			}
			return _signalPort.withdraw(profileId, sourceKey, sequence);
		}
	}

	private CleanupStatus cleanupSources(long profileId)
	{
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				return CleanupStatus.NOT_RUNNING;
			}
			_cleanupInFlight++;
		}
		try
		{
			return withdrawOwnedSources(profileId) ? CleanupStatus.COMPLETE : CleanupStatus.FAILED;
		}
		finally
		{
			synchronized (_monitor)
			{
				_cleanupInFlight--;
			}
		}
	}

	private boolean withdrawOwnedSources(long profileId)
	{
		boolean complete = true;
		for (String sourceKey : OWNED_SOURCES)
		{
			final Long sequence = allocateSequence(profileId, sourceKey);
			if (sequence == null)
			{
				complete = false;
				continue;
			}
			final SignalDelivery delivery = _signalPort.withdraw(profileId, sourceKey, sequence);
			complete &= isSuccessfulWithdrawal(delivery);
		}
		return complete;
	}

	private static boolean isSuccessfulWithdrawal(SignalDelivery delivery)
	{
		return (delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED) || (delivery == SignalDelivery.STALE) || (delivery == SignalDelivery.NOT_REGISTERED);
	}

	private Long allocateSequence(long profileId, String sourceKey)
	{
		synchronized (_monitor)
		{
			final SequenceKey key = new SequenceKey(profileId, sourceKey);
			final long current = _sequences.getOrDefault(key, 0L);
			if (current == Long.MAX_VALUE)
			{
				_metrics.recordSignalSequenceExhausted();
				return null;
			}
			final long next = Math.addExact(current, 1L);
			_sequences.put(key, next);
			return next;
		}
	}

	private EventToken claim()
	{
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordEventRejected();
				return null;
			}
			if (_activeEventTokens.size() >= _policy.maximumConcurrentEvents())
			{
				_metrics.recordEventRejected();
				return null;
			}
			if (_nextEventToken == Long.MAX_VALUE)
			{
				_metrics.recordEventRejected();
				return null;
			}
			final EventToken token = new EventToken(++_nextEventToken, _eventGeneration);
			_activeEventTokens.add(token.id());
			_metrics.recordEventAccepted();
			return token;
		}
	}

	private boolean isCurrent(EventToken token)
	{
		synchronized (_monitor)
		{
			return (_state == State.RUNNING) && (token.eventGeneration() == _eventGeneration) && _activeEventTokens.contains(token.id());
		}
	}

	private EventResult rejectedStatus()
	{
		synchronized (_monitor)
		{
			return new EventResult(_state == State.RUNNING ? EventStatus.BACKPRESSURE : EventStatus.NOT_RUNNING, 0, 0, 0, 0);
		}
	}

	private void release(EventToken token)
	{
		synchronized (_monitor)
		{
			if (_activeEventTokens.remove(token.id()))
			{
				_metrics.recordEventFinished();
			}
		}
	}

	private EventResult resultForSingle(SignalDelivery delivery)
	{
		_metrics.recordRecipientConsidered();
		if ((delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED))
		{
			_metrics.recordRecipientDelivered();
			return new EventResult(EventStatus.ACCEPTED, 1, 1, 0, 0);
		}
		if (delivery == SignalDelivery.BACKPRESSURE)
		{
			_metrics.recordRecipientBackpressured();
			return new EventResult(EventStatus.ACCEPTED, 1, 0, 1, 0);
		}
		if (delivery == SignalDelivery.NOT_REGISTERED)
		{
			_metrics.recordRecipientUnregistered();
			return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 1);
		}
		if (delivery == SignalDelivery.SEQUENCE_EXHAUSTED)
		{
			return new EventResult(EventStatus.SIGNAL_FAILURE, 1, 0, 0, 0);
		}
		return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 0);
	}

	boolean beginStop()
	{
		synchronized (_deliveryGate)
		{
			synchronized (_monitor)
			{
				if (_state == State.STOPPED)
				{
					return false;
				}
				if (_state == State.STOPPING)
				{
					return true;
				}
				_state = State.STOPPING;
				_eventGeneration++;
			}
			_registry.beginStop();
			return true;
		}
	}

	boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return true;
			}
			if ((_state != State.STOPPING) && (_state != State.NEW))
			{
				_metrics.recordStopFailure();
				return false;
			}
			if (!_activeEventTokens.isEmpty() || (_cleanupInFlight != 0))
			{
				_metrics.recordStopFailure();
				return false;
			}
			if (!_registry.finishStop())
			{
				_metrics.recordStopFailure();
				return false;
			}
			_sequences.clear();
			_pendingCleanup.clear();
			_state = State.STOPPED;
			return true;
		}
	}

	Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new Snapshot(_state, _registry.size(), _activeEventTokens.size(), _cleanupInFlight, _pendingCleanup.size(), _eventGeneration);
		}
	}

	public record Snapshot(State state, int registeredProfiles, int eventsInFlight, int cleanupInFlight, int pendingCleanups, long eventGeneration)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, 0, 0, 0, 0, 0);
		}
	}

	private record EventToken(long id, long eventGeneration)
	{
	}

	private record SequenceKey(long profileId, String sourceKey)
	{
	}
}
