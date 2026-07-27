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
import java.util.Comparator;
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
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;

/**
 * Synchronous one-hop perception provider with exact event-token stop ownership.
 */
public final class PhantomPerceptionProvider
{
	public static final String LOCAL_CHAT_SOURCE = "topology.local_chat";
	public static final String COMBAT_SOURCE = "topology.combat";
	public static final String TARGETABILITY_SOURCE = "topology.targetability";

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
		NOT_RUNNING,
		BACKPRESSURE
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
	private final Supplier<PhantomTopologyQuery> _querySupplier;
	private final PhantomRelevanceSignalPort _signalPort;
	private final PhantomTopologyMetrics _metrics;
	private final Map<SequenceKey, Long> _sequences = new HashMap<>();
	private State _state = State.NEW;
	private long _eventGeneration;
	private int _eventsInFlight;

	public PhantomPerceptionProvider(PhantomTopologyPolicy policy, PhantomTopologyProfileRegistry registry, Supplier<PhantomTopologyQuery> querySupplier, PhantomRelevanceSignalPort signalPort, PhantomTopologyMetrics metrics)
	{
		_policy = Objects.requireNonNull(policy, "policy");
		_registry = Objects.requireNonNull(registry, "registry");
		_querySupplier = Objects.requireNonNull(querySupplier, "querySupplier");
		_signalPort = Objects.requireNonNull(signalPort, "signalPort");
		_metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if ((_state != State.NEW) || !_registry.start())
			{
				return false;
			}
			_state = State.RUNNING;
			return true;
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
		try
		{
			return fanout(token, event.sourcePoint(), event.sourceNodeId(), event.radius(), event.ttlMillis(), PhantomPerceptionChannel.LOCAL_CHAT, LOCAL_CHAT_SOURCE, Set.of());
		}
		finally
		{
			release();
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
		try
		{
			return fanout(token, event.sourcePoint(), event.sourceNodeId(), event.radius(), event.ttlMillis(), PhantomPerceptionChannel.COMBAT, COMBAT_SOURCE, Set.copyOf(event.participantProfileIds()));
		}
		finally
		{
			release();
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
		try
		{
			if (_registry.find(event.targetProfileId()).isEmpty())
			{
				_metrics.recordRecipientUnregistered();
				return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 1);
			}
			final SignalDelivery delivery = event.active() ? deliver(token, event.targetProfileId(), TARGETABILITY_SOURCE, PhantomActivityState.ACTIVE, event.ttlMillis()) : withdraw(token, event.targetProfileId(), TARGETABILITY_SOURCE);
			_metrics.recordTargetabilitySignal();
			return resultForSingle(delivery);
		}
		finally
		{
			release();
		}
	}

	private EventResult fanout(EventToken token, PhantomTopologyPoint sourcePoint, String sourceNodeId, int radius, long ttlMillis, PhantomPerceptionChannel channel, String sourceKey, Set<Long> participants)
	{
		final PhantomTopologyQuery query = _querySupplier.get();
		final Optional<PhantomTopologyNode> eventNode = sourceNodeId == null ? query.mostSpecificNode(sourcePoint) : query.findNode(sourceNodeId).filter(node -> node.area().contains(sourcePoint));
		final LinkedHashMap<Long, PhantomActivityState> recipients = new LinkedHashMap<>();
		participants.stream().sorted().forEach(profileId ->
		{
			if ((recipients.size() < _policy.maximumRecipientsPerEvent()) && _registry.find(profileId).isPresent())
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
			for (ProfileTopologySnapshot profile : _registry.listForNodes(perceptibleNodes, _policy.maximumRecipientsPerEvent()))
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
		for (Map.Entry<Long, PhantomActivityState> recipient : recipients.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
		{
			considered++;
			_metrics.recordRecipientConsidered();
			final SignalDelivery delivery = deliver(token, recipient.getKey(), sourceKey, recipient.getValue(), ttlMillis);
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
			if (channel == PhantomPerceptionChannel.LOCAL_CHAT)
			{
				_metrics.recordLocalChatSignal();
			}
			else
			{
				_metrics.recordCombatSignal();
			}
		}
		return new EventResult(EventStatus.ACCEPTED, considered, delivered, backpressured, unregistered);
	}

	private SignalDelivery deliver(EventToken token, long profileId, String sourceKey, PhantomActivityState requiredState, long ttlMillis)
	{
		if (requiredState.code() > PhantomActivityState.NEARBY_PERCEPTIBLE.code())
		{
			throw new IllegalStateException("Perceptible topology signal is below NEARBY_PERCEPTIBLE.");
		}
		synchronized (_deliveryGate)
		{
			final long sequence;
			synchronized (_monitor)
			{
				if ((_state != State.RUNNING) || (token._generation != _eventGeneration))
				{
					return SignalDelivery.NOT_RUNNING;
				}
				final SequenceKey key = new SequenceKey(profileId, sourceKey);
				sequence = _sequences.merge(key, 1L, Long::sum);
			}
			return _signalPort.submit(profileId, new PhantomRelevanceSignal(sourceKey, sequence, requiredState, ttlMillis));
		}
	}

	private SignalDelivery withdraw(EventToken token, long profileId, String sourceKey)
	{
		synchronized (_deliveryGate)
		{
			final long sequence;
			synchronized (_monitor)
			{
				if ((_state != State.RUNNING) || (token._generation != _eventGeneration))
				{
					return SignalDelivery.NOT_RUNNING;
				}
				final SequenceKey key = new SequenceKey(profileId, sourceKey);
				sequence = _sequences.merge(key, 1L, Long::sum);
			}
			return _signalPort.withdraw(profileId, sourceKey, sequence);
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
			if (_eventsInFlight >= _policy.maximumConcurrentEvents())
			{
				_metrics.recordEventRejected();
				return null;
			}
			_eventsInFlight++;
			_metrics.recordEventAccepted();
			return new EventToken(_eventGeneration);
		}
	}

	private EventResult rejectedStatus()
	{
		synchronized (_monitor)
		{
			return new EventResult(_state == State.RUNNING ? EventStatus.BACKPRESSURE : EventStatus.NOT_RUNNING, 0, 0, 0, 0);
		}
	}

	private void release()
	{
		synchronized (_monitor)
		{
			_eventsInFlight--;
			_metrics.recordEventFinished();
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
		return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 0);
	}

	public boolean beginStop()
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

	public boolean finishStop()
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
			if (_eventsInFlight != 0)
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
			_state = State.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new Snapshot(_state, _registry.size(), _eventsInFlight, _eventGeneration);
		}
	}

	public record Snapshot(State state, int registeredProfiles, int eventsInFlight, long eventGeneration)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, 0, 0, 0);
		}
	}

	private record EventToken(long _generation)
	{
	}

	private record SequenceKey(long _profileId, String _sourceKey)
	{
	}
}
