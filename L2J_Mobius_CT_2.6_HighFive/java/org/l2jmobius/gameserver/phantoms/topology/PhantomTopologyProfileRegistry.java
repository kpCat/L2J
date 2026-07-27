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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Explicit bounded profile-position ownership. It performs no discovery.
 */
public final class PhantomTopologyProfileRegistry
{
	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum RegistrationResult
	{
		REGISTERED,
		ALREADY_REGISTERED,
		CAPACITY_REACHED,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	public enum UpdateResult
	{
		UPDATED,
		STALE,
		NOT_REGISTERED,
		NOT_RUNNING,
		INVALID
	}

	public enum UnregisterResult
	{
		UNREGISTERED,
		NOT_REGISTERED,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	public record ProfileTopologySnapshot(long profileId, PhantomTopologyPoint point, long sequence, String nodeId, long topologyGeneration)
	{
		public boolean resolved()
		{
			return nodeId != null;
		}
	}

	private final Object _monitor = new Object();
	private final int _capacity;
	private final Supplier<PhantomTopologyQuery> _querySupplier;
	private final PhantomTopologyMetrics _metrics;
	private final Map<Long, Entry> _entries = new HashMap<>();
	private final Map<String, LinkedHashSet<Long>> _profilesByNode = new HashMap<>();
	private State _state = State.NEW;

	public PhantomTopologyProfileRegistry(int capacity, Supplier<PhantomTopologyQuery> querySupplier, PhantomTopologyMetrics metrics)
	{
		if ((capacity < 1) || (capacity > 10_000))
		{
			throw new IllegalArgumentException("Topology profile capacity must be between 1 and 10000.");
		}
		_capacity = capacity;
		_querySupplier = java.util.Objects.requireNonNull(querySupplier, "querySupplier");
		_metrics = java.util.Objects.requireNonNull(metrics, "metrics");
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
			_state = State.RUNNING;
			return true;
		}
	}

	public RegistrationResult register(long profileId)
	{
		synchronized (_monitor)
		{
			if (profileId <= 0)
			{
				return RegistrationResult.INVALID_PROFILE_ID;
			}
			if (_state != State.RUNNING)
			{
				return RegistrationResult.NOT_RUNNING;
			}
			if (_entries.containsKey(profileId))
			{
				return RegistrationResult.ALREADY_REGISTERED;
			}
			if (_entries.size() >= _capacity)
			{
				return RegistrationResult.CAPACITY_REACHED;
			}
			_entries.put(profileId, new Entry(profileId));
			_metrics.recordProfileRegistered();
			return RegistrationResult.REGISTERED;
		}
	}

	public UpdateResult update(long profileId, PhantomTopologyPoint point, long sequence)
	{
		if ((profileId <= 0) || (point == null) || (sequence < 0))
		{
			_metrics.recordProfileUpdateRejected();
			return UpdateResult.INVALID;
		}
		final PhantomTopologyQuery query = _querySupplier.get();
		final Optional<PhantomTopologyNode> resolved = query.mostSpecificNode(point);
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordProfileUpdateRejected();
				return UpdateResult.NOT_RUNNING;
			}
			final Entry entry = _entries.get(profileId);
			if (entry == null)
			{
				_metrics.recordProfileUpdateRejected();
				return UpdateResult.NOT_REGISTERED;
			}
			if (sequence <= entry._sequence)
			{
				_metrics.recordProfileUpdateRejected();
				return UpdateResult.STALE;
			}
			removeMembershipLocked(entry);
			entry._point = point;
			entry._sequence = sequence;
			entry._nodeId = resolved.map(PhantomTopologyNode::id).orElse(null);
			entry._topologyGeneration = query.snapshot().generation();
			addMembershipLocked(entry);
			return UpdateResult.UPDATED;
		}
	}

	public UnregisterResult unregister(long profileId)
	{
		synchronized (_monitor)
		{
			if (profileId <= 0)
			{
				return UnregisterResult.INVALID_PROFILE_ID;
			}
			if (_state != State.RUNNING)
			{
				return UnregisterResult.NOT_RUNNING;
			}
			final Entry entry = _entries.remove(profileId);
			if (entry == null)
			{
				return UnregisterResult.NOT_REGISTERED;
			}
			removeMembershipLocked(entry);
			_metrics.recordProfileUnregistered();
			return UnregisterResult.UNREGISTERED;
		}
	}

	public Optional<ProfileTopologySnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return entry == null ? Optional.empty() : Optional.of(snapshot(entry));
		}
	}

	public List<ProfileTopologySnapshot> list()
	{
		synchronized (_monitor)
		{
			return _entries.values().stream().map(PhantomTopologyProfileRegistry::snapshot).sorted(Comparator.comparingLong(ProfileTopologySnapshot::profileId)).toList();
		}
	}

	public List<ProfileTopologySnapshot> listForNodes(Set<String> nodeIds, int limit)
	{
		if ((limit < 1) || (limit > 1024))
		{
			throw new IllegalArgumentException("Invalid topology recipient limit.");
		}
		synchronized (_monitor)
		{
			final TreeSet<Long> profileIds = new TreeSet<>();
			nodeIds.stream().sorted().forEach(nodeId ->
			{
				for (Long profileId : _profilesByNode.getOrDefault(nodeId, new LinkedHashSet<>()))
				{
					profileIds.add(profileId);
				}
			});
			final ArrayList<ProfileTopologySnapshot> result = new ArrayList<>(Math.min(limit, profileIds.size()));
			profileIds.stream().limit(limit).forEach(profileId ->
			{
				final Entry entry = _entries.get(profileId);
				if (entry != null)
				{
					result.add(snapshot(entry));
				}
			});
			return List.copyOf(result);
		}
	}

	public void topologyChanged(long generation)
	{
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				return;
			}
			_profilesByNode.clear();
			for (Entry entry : _entries.values())
			{
				entry._nodeId = null;
				entry._topologyGeneration = generation;
			}
		}
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
				return false;
			}
			for (int index = 0; index < _entries.size(); index++)
			{
				_metrics.recordProfileUnregistered();
			}
			_entries.clear();
			_profilesByNode.clear();
			_state = State.STOPPED;
			return true;
		}
	}

	public State state()
	{
		synchronized (_monitor)
		{
			return _state;
		}
	}

	public int size()
	{
		synchronized (_monitor)
		{
			return _entries.size();
		}
	}

	private void addMembershipLocked(Entry entry)
	{
		if (entry._nodeId != null)
		{
			_profilesByNode.computeIfAbsent(entry._nodeId, _ -> new LinkedHashSet<>()).add(entry._profileId);
		}
	}

	private void removeMembershipLocked(Entry entry)
	{
		if (entry._nodeId == null)
		{
			return;
		}
		final LinkedHashSet<Long> profiles = _profilesByNode.get(entry._nodeId);
		if (profiles != null)
		{
			profiles.remove(entry._profileId);
			if (profiles.isEmpty())
			{
				_profilesByNode.remove(entry._nodeId);
			}
		}
	}

	private static ProfileTopologySnapshot snapshot(Entry entry)
	{
		return new ProfileTopologySnapshot(entry._profileId, entry._point, entry._sequence, entry._nodeId, entry._topologyGeneration);
	}

	private static final class Entry
	{
		private final long _profileId;
		private PhantomTopologyPoint _point;
		private long _sequence = -1;
		private String _nodeId;
		private long _topologyGeneration;

		private Entry(long profileId)
		{
			_profileId = profileId;
		}
	}
}
