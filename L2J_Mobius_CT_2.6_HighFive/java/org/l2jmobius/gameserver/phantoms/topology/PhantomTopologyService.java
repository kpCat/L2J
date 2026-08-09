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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.CleanupStatus;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.CombatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.EventResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.LocalChatEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.TargetabilityEvent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionProvider.UnregisterAttempt;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyGenerationCoordinator.View;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.CandidateMembership;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RemovalResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.NpcFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.SpawnFact;

/**
 * Atomic topology snapshot owner and generation/signal lifecycle boundary.
 */
public final class PhantomTopologyService
{
	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum ReloadResult
	{
		RELOADED,
		REJECTED_VALIDATION,
		REJECTED_SIGNAL_INVALIDATION,
		NOT_RUNNING
	}

	public enum UnregisterResult
	{
		UNREGISTERED_AND_WITHDRAWN,
		UNREGISTERED_WITH_SIGNAL_FAILURE,
		NOT_REGISTERED,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	public enum CleanupRetryResult
	{
		CLEANUP_COMPLETED,
		SIGNAL_FAILURE,
		NOT_REQUIRED,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	private final Object _monitor = new Object();
	private final PhantomTopologyGenerationCoordinator _generationCoordinator = new PhantomTopologyGenerationCoordinator();
	private final PhantomTopologyLoader _loader;
	private final PhantomTopologyValidationBackend _backend;
	private final PhantomTopologyPolicy _policy;
	private final PhantomTopologyMetrics _metrics;
	private final PhantomTopologyProfileRegistry _profileRegistry;
	private final PhantomPerceptionProvider _perceptionProvider;
	private final PhantomTopologySnapshot _initialSnapshot;
	private State _state = State.NEW;
	private PhantomTopologySnapshot _snapshot;
	private PhantomTopologyQuery _query;
	private String _lastFailureCategory = "none";

	public PhantomTopologyService(PhantomTopologyLoader loader, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy, PhantomRelevanceSignalPort signalPort)
	{
		_loader = Objects.requireNonNull(loader, "loader");
		_backend = Objects.requireNonNull(backend, "backend");
		_policy = Objects.requireNonNull(policy, "policy");
		_metrics = new PhantomTopologyMetrics();
		_initialSnapshot = null;
		_profileRegistry = new PhantomTopologyProfileRegistry(policy.maximumRegisteredProfiles(), _metrics);
		_perceptionProvider = new PhantomPerceptionProvider(policy, _profileRegistry, _generationCoordinator, this::runningView, signalPort, _metrics);
	}

	private PhantomTopologyService(PhantomTopologySnapshot initialSnapshot, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy, PhantomRelevanceSignalPort signalPort)
	{
		_loader = null;
		_backend = Objects.requireNonNull(backend, "backend");
		_policy = Objects.requireNonNull(policy, "policy");
		_metrics = new PhantomTopologyMetrics();
		_initialSnapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
		_profileRegistry = new PhantomTopologyProfileRegistry(policy.maximumRegisteredProfiles(), _metrics);
		_perceptionProvider = new PhantomPerceptionProvider(policy, _profileRegistry, _generationCoordinator, this::runningView, signalPort, _metrics);
	}

	public static PhantomTopologyService inertForTesting(PhantomRelevanceSignalPort signalPort)
	{
		return inertForTesting(signalPort, PhantomTopologyPolicy.productionDefaults().maximumRegisteredProfiles());
	}

	public static PhantomTopologyService inertForTesting(PhantomRelevanceSignalPort signalPort, int maximumProfiles)
	{
		final PhantomTopologyPolicy policy = PhantomTopologyPolicy.productionDefaults().withMaximumRegisteredProfiles(maximumProfiles);
		final EmptyBackend backend = new EmptyBackend();
		return fromSnapshotForTesting(PhantomTopologySnapshot.empty(backend, policy), backend, policy, signalPort);
	}

	public static PhantomTopologyService fromSnapshotForTesting(PhantomTopologySnapshot snapshot, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy, PhantomRelevanceSignalPort signalPort)
	{
		return new PhantomTopologyService(snapshot, backend, policy, signalPort);
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
		}
		final PhantomTopologySnapshot candidate;
		try
		{
			candidate = _initialSnapshot != null ? _initialSnapshot : _loader.load(1);
		}
		catch (PhantomTopologyValidationException exception)
		{
			synchronized (_monitor)
			{
				_lastFailureCategory = exception.category();
			}
			_metrics.recordValidationFailure();
			throw exception;
		}
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.write())
		{
			synchronized (_monitor)
			{
				if (_state != State.NEW)
				{
					return false;
				}
				_snapshot = candidate;
				_query = new PhantomTopologyQuery(candidate, _backend, _metrics);
			}
			if (!_perceptionProvider.start(candidate.generation()))
			{
				throw new IllegalStateException("Unable to start topology perception provider.");
			}
			synchronized (_monitor)
			{
				_state = State.RUNNING;
				_metrics.recordLoad();
				return true;
			}
		}
	}

	public ReloadResult reload()
	{
		final long expectedGeneration;
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			synchronized (_monitor)
			{
				if ((_state != State.RUNNING) || (_loader == null) || (_snapshot.generation() == Long.MAX_VALUE))
				{
					return ReloadResult.NOT_RUNNING;
				}
				expectedGeneration = _snapshot.generation();
			}
		}
		final long candidateGeneration = Math.addExact(expectedGeneration, 1L);
		final PhantomTopologySnapshot candidate;
		try
		{
			candidate = _loader.load(candidateGeneration);
		}
		catch (PhantomTopologyValidationException exception)
		{
			synchronized (_monitor)
			{
				_lastFailureCategory = exception.category();
			}
			_metrics.recordReloadFailure();
			_metrics.recordValidationFailure();
			return ReloadResult.REJECTED_VALIDATION;
		}
		final PhantomTopologyQuery candidateQuery = new PhantomTopologyQuery(candidate, _backend, _metrics);
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.write())
		{
			synchronized (_monitor)
			{
				if (_state != State.RUNNING)
				{
					return ReloadResult.NOT_RUNNING;
				}
				if (_snapshot.generation() != expectedGeneration)
				{
					_metrics.recordReloadFailure();
					_lastFailureCategory = "topology-changed";
					return ReloadResult.REJECTED_VALIDATION;
				}
			}
			final CandidateMembership membership = _profileRegistry.rebuildCandidate(candidateQuery, candidateGeneration);
			final CleanupStatus invalidation = _perceptionProvider.invalidateForReload(membership.profileIds());
			if (invalidation != CleanupStatus.COMPLETE)
			{
				synchronized (_monitor)
				{
					_lastFailureCategory = "signal-invalidation";
				}
				_metrics.recordReloadFailure();
				_metrics.recordReloadSignalInvalidationFailure();
				return ReloadResult.REJECTED_SIGNAL_INVALIDATION;
			}
			_profileRegistry.installCandidate(membership);
			synchronized (_monitor)
			{
				_snapshot = candidate;
				_query = candidateQuery;
				_lastFailureCategory = "none";
			}
			_metrics.recordReload();
			return ReloadResult.RELOADED;
		}
	}

	public PhantomTopologyQuery query()
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			synchronized (_monitor)
			{
				if (_query == null)
				{
					throw new IllegalStateException("Topology service has no active snapshot.");
				}
				return _query;
			}
		}
	}

	public RegistrationResult registerProfile(long profileId)
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = runningView();
			return view == null ? RegistrationResult.NOT_RUNNING : _perceptionProvider.registerProfile(profileId, view.generation());
		}
	}

	public UpdateResult updateProfile(long profileId, PhantomTopologyPoint point, long sequence)
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = runningView();
			return view == null ? UpdateResult.NOT_RUNNING : _profileRegistry.update(profileId, point, sequence, view.query(), view.generation());
		}
	}

	public UnregisterResult unregisterProfile(long profileId)
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = runningView();
			if (view == null)
			{
				return UnregisterResult.NOT_RUNNING;
			}
			final UnregisterAttempt attempt = _perceptionProvider.unregisterProfile(profileId, view.generation());
			if (attempt.removal() == RemovalResult.INVALID_PROFILE_ID)
			{
				return UnregisterResult.INVALID_PROFILE_ID;
			}
			if (attempt.removal() == RemovalResult.NOT_REGISTERED)
			{
				return UnregisterResult.NOT_REGISTERED;
			}
			if ((attempt.removal() == RemovalResult.NOT_RUNNING) || (attempt.removal() == RemovalResult.TOPOLOGY_CHANGED))
			{
				return UnregisterResult.NOT_RUNNING;
			}
			return attempt.cleanup() == CleanupStatus.COMPLETE ? UnregisterResult.UNREGISTERED_AND_WITHDRAWN : UnregisterResult.UNREGISTERED_WITH_SIGNAL_FAILURE;
		}
	}

	public CleanupRetryResult retryProfileSignalCleanup(long profileId)
	{
		if (profileId <= 0)
		{
			return CleanupRetryResult.INVALID_PROFILE_ID;
		}
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			if (runningView() == null)
			{
				return CleanupRetryResult.NOT_RUNNING;
			}
			return switch (_perceptionProvider.retryProfileSignalCleanup(profileId))
			{
				case COMPLETE -> CleanupRetryResult.CLEANUP_COMPLETED;
				case FAILED -> CleanupRetryResult.SIGNAL_FAILURE;
				case NOT_REQUIRED -> CleanupRetryResult.NOT_REQUIRED;
				case NOT_RUNNING -> CleanupRetryResult.NOT_RUNNING;
			};
		}
	}

	public Optional<ProfileTopologySnapshot> findProfile(long profileId)
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			return _profileRegistry.find(profileId);
		}
	}

	public List<ProfileTopologySnapshot> perceptibleProfiles(long observerProfileId, PhantomPerceptionChannel channel, int limit)
	{
		Objects.requireNonNull(channel, "Perception channel must not be null.");
		if ((observerProfileId <= 0) || (limit < 1) || (limit > 1023))
		{
			throw new IllegalArgumentException("Invalid bounded perceptible-profile query.");
		}
		return perceptibleProfilesUnderLease(observerProfileId, channel, limit);
	}

	private List<ProfileTopologySnapshot> perceptibleProfilesUnderLease(long observerProfileId, PhantomPerceptionChannel channel, int limit)
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final View view = runningView();
			if (view == null)
			{
				return List.of();
			}
			final ProfileTopologySnapshot observer = _profileRegistry.find(observerProfileId, view.generation()).filter(ProfileTopologySnapshot::resolved).orElse(null);
			if (observer == null)
			{
				return List.of();
			}
			final Set<String> nodes = new HashSet<>();
			nodes.add(observer.nodeId());
			for (PhantomTopologyEdge edge : view.query().edges(observer.nodeId()))
			{
				if (view.query().isPerceptible(edge.id(), channel))
				{
					final String other = edge.otherNode(observer.nodeId());
					if (other != null)
					{
						nodes.add(other);
					}
				}
			}
			return _profileRegistry.listForNodes(nodes, limit + 1, view.generation()).stream().filter(profile -> profile.profileId() != observerProfileId).limit(limit).toList();
		}
	}

	public List<ProfileTopologySnapshot> listProfiles()
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			return _profileRegistry.list();
		}
	}

	public EventResult localChat(LocalChatEvent event)
	{
		return _perceptionProvider.localChat(event);
	}

	public EventResult combat(CombatEvent event)
	{
		return _perceptionProvider.combat(event);
	}

	public EventResult targetability(TargetabilityEvent event)
	{
		return _perceptionProvider.targetability(event);
	}

	public boolean beginStop()
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.write())
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
			}
			return _perceptionProvider.beginStop();
		}
	}

	public boolean finishStop()
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.write())
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
			}
			if (!_perceptionProvider.finishStop())
			{
				_metrics.recordStopFailure();
				return false;
			}
			synchronized (_monitor)
			{
				_state = State.STOPPED;
				return true;
			}
		}
	}

	public ServiceSnapshot snapshot()
	{
		try (PhantomTopologyGenerationCoordinator.Lease ignored = _generationCoordinator.read())
		{
			final State state;
			final PhantomTopologySnapshot topology;
			final String lastFailureCategory;
			synchronized (_monitor)
			{
				state = _state;
				topology = _snapshot;
				lastFailureCategory = _lastFailureCategory;
			}
			final PhantomPerceptionProvider.Snapshot perception = _perceptionProvider.snapshot();
			final PhantomTopologyMetrics.Snapshot metrics = _metrics.snapshot();
			return new ServiceSnapshot(state, topology == null ? "none" : topology.datasetId(), topology == null ? 0 : topology.datasetVersion(), topology == null ? "none" : topology.canonicalHash(), topology == null ? 0 : topology.generation(), topology == null ? 0 : topology.nodes().size(), topology == null ? 0 : topology.anchors().size(), topology == null ? 0 : topology.edges().size(), perception.registeredProfiles(), perception.eventsInFlight(), perception.cleanupInFlight(), perception.pendingCleanups(), perception.signalLedgers(), metrics.signalLedgersPeak(), perception.signalLedgerCapacity(), lastFailureCategory, metrics);
		}
	}

	private View runningView()
	{
		synchronized (_monitor)
		{
			return (_state == State.RUNNING) && (_query != null) ? new View(_query, _snapshot.generation()) : null;
		}
	}

	public record ServiceSnapshot(State state, String datasetId, int datasetVersion, String canonicalHash, long generation, int nodes, int anchors, int edges, int registeredProfiles, int eventsInFlight, int cleanupInFlight, int pendingSignalCleanups, int signalLedgersCurrent, long signalLedgersPeak, int signalLedgerCapacity, String lastFailureCategory, PhantomTopologyMetrics.Snapshot metrics)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(State.STOPPED, "none", 0, "none", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "none", new PhantomTopologyMetrics().snapshot());
		}
	}

	private static final class EmptyBackend implements PhantomTopologyValidationBackend
	{
		@Override
		public int mapRegionLocId(int x, int y)
		{
			return 0;
		}

		@Override
		public Optional<NpcFact> npc(int npcId)
		{
			return Optional.empty();
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			return List.of();
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return Optional.empty();
		}

		@Override
		public DoorState doorState(int doorId)
		{
			return DoorState.MISSING;
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return false;
		}
	}
}
