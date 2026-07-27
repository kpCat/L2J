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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UnregisterResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.DoorState;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.NpcFact;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend.SpawnFact;

/**
 * Atomic topology snapshot owner and perception lifecycle boundary.
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
		REJECTED,
		NOT_RUNNING
	}

	private final Object _monitor = new Object();
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
		_profileRegistry = new PhantomTopologyProfileRegistry(policy.maximumRegisteredProfiles(), this::query, _metrics);
		_perceptionProvider = new PhantomPerceptionProvider(policy, _profileRegistry, this::query, signalPort, _metrics);
	}

	private PhantomTopologyService(PhantomTopologySnapshot initialSnapshot, PhantomTopologyValidationBackend backend, PhantomTopologyPolicy policy, PhantomRelevanceSignalPort signalPort)
	{
		_loader = null;
		_backend = backend;
		_policy = policy;
		_metrics = new PhantomTopologyMetrics();
		_initialSnapshot = initialSnapshot;
		_profileRegistry = new PhantomTopologyProfileRegistry(policy.maximumRegisteredProfiles(), this::query, _metrics);
		_perceptionProvider = new PhantomPerceptionProvider(policy, _profileRegistry, this::query, signalPort, _metrics);
	}

	public static PhantomTopologyService inertForTesting(PhantomRelevanceSignalPort signalPort)
	{
		return inertForTesting(signalPort, PhantomTopologyPolicy.productionDefaults().maximumRegisteredProfiles());
	}

	public static PhantomTopologyService inertForTesting(PhantomRelevanceSignalPort signalPort, int maximumProfiles)
	{
		final PhantomTopologyPolicy policy = PhantomTopologyPolicy.productionDefaults().withMaximumRegisteredProfiles(maximumProfiles);
		final EmptyBackend backend = new EmptyBackend();
		return new PhantomTopologyService(PhantomTopologySnapshot.empty(backend, policy), backend, policy, signalPort);
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
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
			_snapshot = candidate;
			_query = new PhantomTopologyQuery(candidate, _backend, _metrics);
			if (!_perceptionProvider.start())
			{
				throw new IllegalStateException("Unable to start topology perception provider.");
			}
			_state = State.RUNNING;
			_metrics.recordLoad();
			return true;
		}
	}

	public ReloadResult reload()
	{
		final long generation;
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || (_loader == null))
			{
				return ReloadResult.NOT_RUNNING;
			}
			generation = _snapshot.generation() + 1;
		}
		final PhantomTopologySnapshot candidate;
		try
		{
			candidate = _loader.load(generation);
		}
		catch (PhantomTopologyValidationException exception)
		{
			synchronized (_monitor)
			{
				_lastFailureCategory = exception.category();
			}
			_metrics.recordReloadFailure();
			_metrics.recordValidationFailure();
			return ReloadResult.REJECTED;
		}
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || (_snapshot.generation() + 1 != generation))
			{
				_metrics.recordReloadFailure();
				return ReloadResult.REJECTED;
			}
			_snapshot = candidate;
			_query = new PhantomTopologyQuery(candidate, _backend, _metrics);
			_profileRegistry.topologyChanged(generation);
			_lastFailureCategory = "none";
			_metrics.recordReload();
			return ReloadResult.RELOADED;
		}
	}

	public PhantomTopologyQuery query()
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

	public RegistrationResult register(long profileId)
	{
		return _profileRegistry.register(profileId);
	}

	public UpdateResult update(long profileId, PhantomTopologyPoint point, long sequence)
	{
		return _profileRegistry.update(profileId, point, sequence);
	}

	public UnregisterResult unregister(long profileId)
	{
		return _profileRegistry.unregister(profileId);
	}

	public PhantomTopologyProfileRegistry profiles()
	{
		return _profileRegistry;
	}

	public PhantomPerceptionProvider perception()
	{
		return _perceptionProvider;
	}

	public boolean beginStop()
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

	public ServiceSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			final PhantomPerceptionProvider.Snapshot perception = _perceptionProvider.snapshot();
			return new ServiceSnapshot(_state, _snapshot == null ? "none" : _snapshot.datasetId(), _snapshot == null ? 0 : _snapshot.datasetVersion(), _snapshot == null ? "none" : _snapshot.canonicalHash(), _snapshot == null ? 0 : _snapshot.generation(), _snapshot == null ? 0 : _snapshot.nodes().size(), _snapshot == null ? 0 : _snapshot.anchors().size(), _snapshot == null ? 0 : _snapshot.edges().size(), perception.registeredProfiles(), perception.eventsInFlight(), _lastFailureCategory, _metrics.snapshot());
		}
	}

	public record ServiceSnapshot(State state, String datasetId, int datasetVersion, String canonicalHash, long generation, int nodes, int anchors, int edges, int registeredProfiles, int eventsInFlight, String lastFailureCategory, PhantomTopologyMetrics.Snapshot metrics)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(State.STOPPED, "none", 0, "none", 0, 0, 0, 0, 0, 0, "none", new PhantomTopologyMetrics().snapshot());
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
