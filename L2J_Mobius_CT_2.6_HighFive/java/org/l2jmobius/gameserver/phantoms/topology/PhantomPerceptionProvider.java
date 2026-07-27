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
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySignalLedger.SourceState;

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

	private record SignalOperation(SignalDelivery delivery, boolean signalFailure, boolean schedulerAbsent)
	{
	}

	private record CleanupPass(boolean complete, boolean allNotRegistered)
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
	private final Map<Long, PhantomTopologySignalLedger> _signalLedgers = new HashMap<>();
	private final Set<Long> _activeEventTokens = new HashSet<>();
	private State _state = State.NEW;
	private long _eventGeneration;
	private long _nextEventToken;

	PhantomPerceptionProvider(PhantomTopologyPolicy policy, PhantomTopologyProfileRegistry registry, PhantomTopologyGenerationCoordinator generationCoordinator, Supplier<View> viewSupplier, PhantomRelevanceSignalPort signalPort, PhantomTopologyMetrics metrics)
	{
		_policy = Objects.requireNonNull(policy, "policy");
		_registry = Objects.requireNonNull(registry, "registry");
		_generationCoordinator = Objects.requireNonNull(generationCoordinator, "generationCoordinator");
		_viewSupplier = Objects.requireNonNull(viewSupplier, "viewSupplier");
		_signalPort = Objects.requireNonNull(signalPort, "signalPort");
		_metrics = Objects.requireNonNull(metrics, "metrics");
		_metrics.configureSignalLedgerCapacity(policy.maximumRegisteredProfiles());
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
			if (profileId <= 0)
			{
				return RegistrationResult.INVALID_PROFILE_ID;
			}
			final PhantomTopologySignalLedger ledger;
			final boolean created;
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return RegistrationResult.NOT_RUNNING;
				}
				final PhantomTopologySignalLedger existing = _signalLedgers.get(profileId);
				if ((existing != null) && (existing.cleanupPending() || existing.cleanupInFlight()))
				{
					return RegistrationResult.CLEANUP_PENDING;
				}
				if (existing != null)
				{
					ledger = existing;
					created = false;
				}
				else
				{
					if (_signalLedgers.size() >= _policy.maximumRegisteredProfiles())
					{
						return RegistrationResult.SIGNAL_LEDGER_CAPACITY;
					}
					ledger = new PhantomTopologySignalLedger(profileId);
					_signalLedgers.put(profileId, ledger);
					_metrics.recordSignalLedgerReserved();
					created = true;
				}
			}
			final RegistrationResult registration = _registry.register(profileId, generation);
			if (created && (registration != RegistrationResult.REGISTERED))
			{
				synchronized (_monitor)
				{
					if ((_signalLedgers.get(profileId) == ledger) && ledger.isEmptyReservation())
					{
						_signalLedgers.remove(profileId);
						_metrics.recordSignalLedgerReleased();
					}
				}
			}
			return registration;
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
			final CleanupStatus cleanup = cleanupSources(profileId, true);
			if (cleanup != CleanupStatus.COMPLETE)
			{
				_metrics.recordSignalCleanupFailure();
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
				final PhantomTopologySignalLedger ledger = _signalLedgers.get(profileId);
				if ((ledger == null) || !ledger.cleanupPending())
				{
					return CleanupStatus.NOT_REQUIRED;
				}
				_metrics.recordSignalCleanupRetry();
			}
			final CleanupStatus cleanup = cleanupSources(profileId, true);
			if (cleanup != CleanupStatus.COMPLETE)
			{
				_metrics.recordSignalCleanupFailure();
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
			}
			boolean complete = true;
			for (Long profileId : profileIds.stream().sorted().toList())
			{
				complete &= cleanupSources(profileId, false) == CleanupStatus.COMPLETE;
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
			final SignalOperation operation;
			if (event.active())
			{
				if (_registry.find(event.targetProfileId(), view.generation()).isEmpty())
				{
					_metrics.recordRecipientUnregistered();
					return new EventResult(EventStatus.ACCEPTED, 1, 0, 0, 1);
				}
				operation = deliver(token, event.targetProfileId(), TARGETABILITY_SOURCE, PhantomActivityState.ACTIVE, event.ttlMillis(), view.generation(), true);
			}
			else
			{
				operation = withdrawEvent(token, event.targetProfileId(), TARGETABILITY_SOURCE);
			}
			_metrics.recordTargetabilitySignal();
			return resultForSingle(operation);
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
			final SignalOperation operation = deliver(token, recipient.getKey(), sourceKey, recipient.getValue(), ttlMillis, view.generation(), true);
			final SignalDelivery delivery = operation.delivery();
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
			else if (operation.signalFailure())
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

	private SignalOperation deliver(EventToken token, long profileId, String sourceKey, PhantomActivityState requiredState, long ttlMillis, long topologyGeneration, boolean registrationRequired)
	{
		if (requiredState.code() > PhantomActivityState.NEARBY_PERCEPTIBLE.code())
		{
			throw new IllegalStateException("Perceptible topology signal is below NEARBY_PERCEPTIBLE.");
		}
		synchronized (_deliveryGate)
		{
			if (!isCurrent(token))
			{
				return failedOperation(SignalDelivery.NOT_RUNNING);
			}
			if (registrationRequired && _registry.find(profileId, topologyGeneration).isEmpty())
			{
				return new SignalOperation(SignalDelivery.NOT_REGISTERED, false, true);
			}
			final PhantomTopologySignalLedger ledger = signalLedger(profileId);
			if (ledger == null)
			{
				return failedOperation(SignalDelivery.REJECTED);
			}
			final Long sequence = allocateSequence(ledger, sourceKey);
			if (sequence == null)
			{
				return failedOperation(SignalDelivery.SEQUENCE_EXHAUSTED);
			}
			final SignalDelivery delivery = _signalPort.submit(profileId, new PhantomRelevanceSignal(sourceKey, sequence, requiredState, ttlMillis));
			applySubmitResult(ledger, sourceKey, delivery);
			return new SignalOperation(delivery, isImpossibleSubmit(delivery), false);
		}
	}

	private SignalOperation withdrawEvent(EventToken token, long profileId, String sourceKey)
	{
		synchronized (_deliveryGate)
		{
			if (!isCurrent(token))
			{
				return failedOperation(SignalDelivery.NOT_RUNNING);
			}
			final PhantomTopologySignalLedger ledger = signalLedger(profileId);
			if (ledger == null)
			{
				return new SignalOperation(SignalDelivery.NOT_REGISTERED, false, true);
			}
			return withdrawSource(ledger, sourceKey);
		}
	}

	private CleanupStatus cleanupSources(long profileId, boolean releaseEligible)
	{
		final PhantomTopologySignalLedger ledger;
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				return CleanupStatus.NOT_RUNNING;
			}
			ledger = _signalLedgers.get(profileId);
			if ((ledger == null) || ledger.cleanupInFlight())
			{
				return CleanupStatus.FAILED;
			}
			ledger.cleanupInFlight(true);
		}

		try
		{
			final CleanupPass pass = withdrawOwnedSources(ledger);
			final boolean profileRegistered = releaseEligible && _registry.find(profileId).isPresent();
			synchronized (_monitor)
			{
				if (_signalLedgers.get(profileId) != ledger)
				{
					return CleanupStatus.FAILED;
				}
				if (!pass.complete())
				{
					if (releaseEligible)
					{
						ledger.cleanupPending(true);
					}
					return CleanupStatus.FAILED;
				}
				ledger.cleanupPending(false);
				if (releaseEligible && pass.allNotRegistered() && !profileRegistered)
				{
					_signalLedgers.remove(profileId);
					_metrics.recordSignalLedgerReleased();
				}
				return CleanupStatus.COMPLETE;
			}
		}
		finally
		{
			synchronized (_monitor)
			{
				if (_signalLedgers.get(profileId) == ledger)
				{
					ledger.cleanupInFlight(false);
				}
			}
		}
	}

	private CleanupPass withdrawOwnedSources(PhantomTopologySignalLedger ledger)
	{
		boolean complete = true;
		boolean allNotRegistered = true;
		for (String sourceKey : OWNED_SOURCES)
		{
			final SignalOperation operation = withdrawSource(ledger, sourceKey);
			complete &= isSuccessfulWithdrawal(operation);
			allNotRegistered &= operation.schedulerAbsent();
		}
		return new CleanupPass(complete, allNotRegistered);
	}

	private SignalOperation withdrawSource(PhantomTopologySignalLedger ledger, String sourceKey)
	{
		final SourceState previousState;
		final Long sequence;
		synchronized (_monitor)
		{
			if (_signalLedgers.get(ledger.profileId()) != ledger)
			{
				return failedOperation(SignalDelivery.REJECTED);
			}
			previousState = ledger.sourceState(sourceKey);
			sequence = ledger.allocateSequence(sourceKey);
			if (sequence == null)
			{
				_metrics.recordSignalSequenceExhausted();
				return failedOperation(SignalDelivery.SEQUENCE_EXHAUSTED);
			}
		}
		final SignalDelivery delivery = _signalPort.withdraw(ledger.profileId(), sourceKey, sequence);
		synchronized (_monitor)
		{
			if (_signalLedgers.get(ledger.profileId()) != ledger)
			{
				return failedOperation(SignalDelivery.REJECTED);
			}
			if ((delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED) || (delivery == SignalDelivery.NOT_REGISTERED))
			{
				ledger.sourceState(sourceKey, SourceState.INACTIVE_CONFIRMED);
			}
			else if ((delivery == SignalDelivery.STALE) && (previousState != SourceState.INACTIVE_CONFIRMED))
			{
				ledger.sourceState(sourceKey, SourceState.OWNERSHIP_UNCERTAIN);
			}
		}
		final boolean staleSafe = (delivery == SignalDelivery.STALE) && (previousState == SourceState.INACTIVE_CONFIRMED);
		final boolean signalFailure = ((delivery == SignalDelivery.STALE) && !staleSafe) || (delivery == SignalDelivery.REJECTED) || (delivery == SignalDelivery.NOT_RUNNING) || (delivery == SignalDelivery.SEQUENCE_EXHAUSTED);
		return new SignalOperation(delivery, signalFailure, delivery == SignalDelivery.NOT_REGISTERED);
	}

	private static boolean isSuccessfulWithdrawal(SignalOperation operation)
	{
		final SignalDelivery delivery = operation.delivery();
		return (delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED) || (delivery == SignalDelivery.NOT_REGISTERED) || ((delivery == SignalDelivery.STALE) && !operation.signalFailure());
	}

	private PhantomTopologySignalLedger signalLedger(long profileId)
	{
		synchronized (_monitor)
		{
			return _signalLedgers.get(profileId);
		}
	}

	private Long allocateSequence(PhantomTopologySignalLedger ledger, String sourceKey)
	{
		synchronized (_monitor)
		{
			if (_signalLedgers.get(ledger.profileId()) != ledger)
			{
				return null;
			}
			final Long sequence = ledger.allocateSequence(sourceKey);
			if (sequence == null)
			{
				_metrics.recordSignalSequenceExhausted();
			}
			return sequence;
		}
	}

	private void applySubmitResult(PhantomTopologySignalLedger ledger, String sourceKey, SignalDelivery delivery)
	{
		synchronized (_monitor)
		{
			if (_signalLedgers.get(ledger.profileId()) != ledger)
			{
				return;
			}
			if ((delivery == SignalDelivery.ACCEPTED) || (delivery == SignalDelivery.COALESCED))
			{
				ledger.sourceState(sourceKey, SourceState.POSSIBLY_ACTIVE);
			}
			else if ((delivery == SignalDelivery.STALE) || (delivery == SignalDelivery.REJECTED) || (delivery == SignalDelivery.NOT_RUNNING))
			{
				ledger.sourceState(sourceKey, SourceState.OWNERSHIP_UNCERTAIN);
			}
		}
	}

	private static boolean isImpossibleSubmit(SignalDelivery delivery)
	{
		return (delivery == SignalDelivery.STALE) || (delivery == SignalDelivery.REJECTED) || (delivery == SignalDelivery.NOT_RUNNING) || (delivery == SignalDelivery.SEQUENCE_EXHAUSTED);
	}

	private static SignalOperation failedOperation(SignalDelivery delivery)
	{
		return new SignalOperation(delivery, true, false);
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

	private EventResult resultForSingle(SignalOperation operation)
	{
		final SignalDelivery delivery = operation.delivery();
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
		if (operation.signalFailure())
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
			if (!_activeEventTokens.isEmpty() || (_signalLedgers.values().stream().anyMatch(PhantomTopologySignalLedger::cleanupInFlight)))
			{
				_metrics.recordStopFailure();
				return false;
			}
			if (!_registry.finishStop())
			{
				_metrics.recordStopFailure();
				return false;
			}
			_signalLedgers.clear();
			_metrics.clearSignalLedgers();
			_state = State.STOPPED;
			return true;
		}
	}

	Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			final int cleanupInFlight = (int) _signalLedgers.values().stream().filter(PhantomTopologySignalLedger::cleanupInFlight).count();
			final int pendingCleanups = (int) _signalLedgers.values().stream().filter(PhantomTopologySignalLedger::cleanupPending).count();
			return new Snapshot(_state, _registry.size(), _activeEventTokens.size(), cleanupInFlight, pendingCleanups, _signalLedgers.size(), _policy.maximumRegisteredProfiles(), _eventGeneration);
		}
	}

	public record Snapshot(State state, int registeredProfiles, int eventsInFlight, int cleanupInFlight, int pendingCleanups, int signalLedgers, int signalLedgerCapacity, long eventGeneration)
	{
		public static Snapshot inactive()
		{
			return new Snapshot(State.STOPPED, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private record EventToken(long id, long eventGeneration)
	{
	}

}
