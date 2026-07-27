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

/**
 * Explicit bounded profile-position ownership. Mutation is service-owned.
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
		SIGNAL_LEDGER_CAPACITY,
		CLEANUP_PENDING,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	public enum UpdateResult
	{
		UPDATED,
		STALE,
		TOPOLOGY_CHANGED,
		NOT_REGISTERED,
		NOT_RUNNING,
		INVALID
	}

	enum RemovalResult
	{
		UNREGISTERED,
		NOT_REGISTERED,
		TOPOLOGY_CHANGED,
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

	record CandidateMembership(long generation, List<CandidateEntry> entries, Map<String, List<Long>> profilesByNode)
	{
		CandidateMembership
		{
			entries = List.copyOf(entries);
			final HashMap<String, List<Long>> immutable = new HashMap<>();
			profilesByNode.forEach((nodeId, profileIds) -> immutable.put(nodeId, List.copyOf(profileIds)));
			profilesByNode = Map.copyOf(immutable);
		}

		List<Long> profileIds()
		{
			return entries.stream().map(CandidateEntry::profileId).toList();
		}
	}

	private record CandidateEntry(long profileId, PhantomTopologyPoint point, long sequence, String nodeId)
	{
	}

	private final Object _monitor = new Object();
	private final int _capacity;
	private final PhantomTopologyMetrics _metrics;
	private final Map<Long, Entry> _entries = new HashMap<>();
	private Map<String, LinkedHashSet<Long>> _profilesByNode = new HashMap<>();
	private State _state = State.NEW;
	private long _generation = -1;

	PhantomTopologyProfileRegistry(int capacity, PhantomTopologyMetrics metrics)
	{
		if ((capacity < 1) || (capacity > 10_000))
		{
			throw new IllegalArgumentException("Topology profile capacity must be between 1 and 10000.");
		}
		_capacity = capacity;
		_metrics = java.util.Objects.requireNonNull(metrics, "metrics");
	}

	boolean start(long generation)
	{
		synchronized (_monitor)
		{
			if ((_state != State.NEW) || (generation < 0))
			{
				return false;
			}
			_generation = generation;
			_state = State.RUNNING;
			return true;
		}
	}

	RegistrationResult register(long profileId, long requiredGeneration)
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
			if (_generation != requiredGeneration)
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
			_entries.put(profileId, new Entry(profileId, requiredGeneration));
			_metrics.recordProfileRegistered();
			return RegistrationResult.REGISTERED;
		}
	}

	UpdateResult update(long profileId, PhantomTopologyPoint point, long sequence, PhantomTopologyQuery query, long requiredGeneration)
	{
		if ((profileId <= 0) || (point == null) || (sequence < 0) || (query == null) || (query.snapshot().generation() != requiredGeneration))
		{
			_metrics.recordProfileUpdateRejected();
			return UpdateResult.INVALID;
		}
		final Optional<PhantomTopologyNode> resolved = query.mostSpecificNode(point);
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				_metrics.recordProfileUpdateRejected();
				return UpdateResult.NOT_RUNNING;
			}
			if (_generation != requiredGeneration)
			{
				_metrics.recordProfileUpdateRejected();
				return UpdateResult.TOPOLOGY_CHANGED;
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
			entry._topologyGeneration = requiredGeneration;
			addMembershipLocked(entry);
			return UpdateResult.UPDATED;
		}
	}

	RemovalResult remove(long profileId, long requiredGeneration)
	{
		synchronized (_monitor)
		{
			if (profileId <= 0)
			{
				return RemovalResult.INVALID_PROFILE_ID;
			}
			if (_state != State.RUNNING)
			{
				return RemovalResult.NOT_RUNNING;
			}
			if (_generation != requiredGeneration)
			{
				return RemovalResult.TOPOLOGY_CHANGED;
			}
			final Entry entry = _entries.remove(profileId);
			if (entry == null)
			{
				return RemovalResult.NOT_REGISTERED;
			}
			removeMembershipLocked(entry);
			_metrics.recordProfileUnregistered();
			return RemovalResult.UNREGISTERED;
		}
	}

	Optional<ProfileTopologySnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return entry == null ? Optional.empty() : Optional.of(snapshot(entry));
		}
	}

	Optional<ProfileTopologySnapshot> find(long profileId, long requiredGeneration)
	{
		synchronized (_monitor)
		{
			final Entry entry = _entries.get(profileId);
			return (entry == null) || (entry._topologyGeneration != requiredGeneration) ? Optional.empty() : Optional.of(snapshot(entry));
		}
	}

	List<ProfileTopologySnapshot> list()
	{
		synchronized (_monitor)
		{
			return _entries.values().stream().map(PhantomTopologyProfileRegistry::snapshot).sorted(Comparator.comparingLong(ProfileTopologySnapshot::profileId)).toList();
		}
	}

	List<ProfileTopologySnapshot> listForNodes(Set<String> nodeIds, int limit, long requiredGeneration)
	{
		if ((limit < 1) || (limit > 1024))
		{
			throw new IllegalArgumentException("Invalid topology recipient limit.");
		}
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || (_generation != requiredGeneration))
			{
				return List.of();
			}
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
				if ((entry != null) && (entry._topologyGeneration == requiredGeneration))
				{
					result.add(snapshot(entry));
				}
			});
			return List.copyOf(result);
		}
	}

	CandidateMembership rebuildCandidate(PhantomTopologyQuery query, long generation)
	{
		if ((query == null) || (query.snapshot().generation() != generation))
		{
			throw new IllegalArgumentException("Candidate topology query generation mismatch.");
		}
		final List<ProfileTopologySnapshot> captured;
		synchronized (_monitor)
		{
			if (_state != State.RUNNING)
			{
				throw new IllegalStateException("Topology profile registry is not running.");
			}
			captured = _entries.values().stream().map(PhantomTopologyProfileRegistry::snapshot).sorted(Comparator.comparingLong(ProfileTopologySnapshot::profileId)).toList();
		}
		final ArrayList<CandidateEntry> candidates = new ArrayList<>(captured.size());
		final HashMap<String, ArrayList<Long>> memberships = new HashMap<>();
		for (ProfileTopologySnapshot profile : captured)
		{
			final String nodeId = profile.point() == null ? null : query.mostSpecificNode(profile.point()).map(PhantomTopologyNode::id).orElse(null);
			candidates.add(new CandidateEntry(profile.profileId(), profile.point(), profile.sequence(), nodeId));
			if (nodeId != null)
			{
				memberships.computeIfAbsent(nodeId, _ -> new ArrayList<>()).add(profile.profileId());
			}
		}
		final HashMap<String, List<Long>> immutableMemberships = new HashMap<>();
		memberships.forEach((nodeId, profileIds) -> immutableMemberships.put(nodeId, profileIds.stream().sorted().toList()));
		return new CandidateMembership(generation, candidates, immutableMemberships);
	}

	void installCandidate(CandidateMembership candidate)
	{
		java.util.Objects.requireNonNull(candidate, "candidate");
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || (candidate.entries().size() != _entries.size()))
			{
				throw new IllegalStateException("Topology candidate membership no longer matches the registry.");
			}
			for (CandidateEntry candidateEntry : candidate.entries())
			{
				final Entry entry = _entries.get(candidateEntry.profileId());
				if ((entry == null) || (entry._sequence != candidateEntry.sequence()) || !java.util.Objects.equals(entry._point, candidateEntry.point()))
				{
					throw new IllegalStateException("Topology candidate membership became stale.");
				}
			}
			final HashMap<String, LinkedHashSet<Long>> installedMemberships = new HashMap<>();
			candidate.profilesByNode().forEach((nodeId, profileIds) -> installedMemberships.put(nodeId, new LinkedHashSet<>(profileIds)));
			for (CandidateEntry candidateEntry : candidate.entries())
			{
				final Entry entry = _entries.get(candidateEntry.profileId());
				entry._nodeId = candidateEntry.nodeId();
				entry._topologyGeneration = candidate.generation();
			}
			_profilesByNode = installedMemberships;
			_generation = candidate.generation();
		}
	}

	boolean beginStop()
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
				return false;
			}
			for (int index = 0; index < _entries.size(); index++)
			{
				_metrics.recordProfileUnregistered();
			}
			_entries.clear();
			_profilesByNode = new HashMap<>();
			_state = State.STOPPED;
			return true;
		}
	}

	State state()
	{
		synchronized (_monitor)
		{
			return _state;
		}
	}

	int size()
	{
		synchronized (_monitor)
		{
			return _entries.size();
		}
	}

	long generation()
	{
		synchronized (_monitor)
		{
			return _generation;
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

		private Entry(long profileId, long topologyGeneration)
		{
			_profileId = profileId;
			_topologyGeneration = topologyGeneration;
		}
	}
}
