/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.DemandFact;

/** One bounded human-local refresh stage in the shared scheduler pulse. */
public final class PhantomHumanLocalityControl implements PhantomSchedulerControlPort
{
	private static final String SOURCE = "human.local";
	private static final int MAXIMUM_HUMANS_PER_REFRESH = 256;
	private static final int MAXIMUM_PROFILES_PER_HUMAN = 1024;
	private static final long REFRESH_MILLIS = 1000;
	private static final long SIGNAL_TTL_MILLIS = 10_000;
	private final PhantomTopologyService _topology;
	private final PhantomRelevanceSignalPort _signals;
	private final Supplier<List<PhantomTopologyPoint>> _humans;
	private final LongSupplier _clock;
	private final LongPredicate _online;
	private final Supplier<Map<Long, PhantomTopologyPoint>> _livePlayers;
	private java.util.function.LongConsumer _physicalDemand = _ -> {};
	private java.util.function.BiConsumer<List<DemandFact>, Integer> _preparationDemand;
	private java.util.function.IntSupplier _preparationSlots;
	private volatile Set<Long> _local = Set.of();
	private volatile PhysicalDemand _physical = new PhysicalDemand(0, Map.of(), false);
	private volatile Map<Long, PhantomRelevanceSignalPort.SignalDelivery> _delivery = Map.of();
	private long _nextRefresh;
	private long _sequence;

	public PhantomHumanLocalityControl(PhantomTopologyService topology, PhantomRelevanceSignalPort signals, Supplier<List<PhantomTopologyPoint>> humans, LongSupplier clock)
	{
		this(topology, signals, humans, clock, profileId -> true);
	}

	public PhantomHumanLocalityControl(PhantomTopologyService topology, PhantomRelevanceSignalPort signals, Supplier<List<PhantomTopologyPoint>> humans, LongSupplier clock, LongPredicate online)
	{
		this(topology, signals, humans, clock, online, Map::of);
	}

	public PhantomHumanLocalityControl(PhantomTopologyService topology, PhantomRelevanceSignalPort signals, Supplier<List<PhantomTopologyPoint>> humans, LongSupplier clock, LongPredicate online, Supplier<Map<Long, PhantomTopologyPoint>> livePlayers)
	{
		_topology = Objects.requireNonNull(topology, "topology");
		_signals = Objects.requireNonNull(signals, "signals");
		_humans = Objects.requireNonNull(humans, "humans");
		_clock = Objects.requireNonNull(clock, "clock");
		_online = Objects.requireNonNull(online, "online");
		_livePlayers = Objects.requireNonNull(livePlayers, "livePlayers");
	}

	/** Installed before scheduler start; enqueue only, independent of signal delivery. */
	public void installPhysicalDemand(java.util.function.LongConsumer demand)
	{
		_physicalDemand = Objects.requireNonNull(demand);
	}

	public void installPreparationDemand(java.util.function.BiConsumer<List<DemandFact>, Integer> demand, java.util.function.IntSupplier slots)
	{
		_preparationDemand = Objects.requireNonNull(demand);
		_preparationSlots = Objects.requireNonNull(slots);
	}

	@Override
	public void onPulse()
	{
		final long now = _clock.getAsLong();
		if (now < _nextRefresh)
		{
			return;
		}
		_nextRefresh = now + REFRESH_MILLIS;
		final var recorder = PhantomRuntimeFlightRecorder.getInstance();
		recorder.record(0, "HUMAN_REFRESH_BEGIN", "", "", "local.pulse", now, _local.size(), 0);
		final TreeSet<Long> candidates = new TreeSet<>();
		final Map<Long, Long> revisions = new HashMap<>();
		boolean overflow = false;
		final Map<Long, PhantomTopologyPoint> livePlayers = _livePlayers.get();
		final List<PhantomTopologyPoint> humans = _humans.get().stream().limit(MAXIMUM_HUMANS_PER_REFRESH).toList();
		for (PhantomTopologyPoint human : humans)
		{
			for (var entry : livePlayers.entrySet())
			{
				if (_online.test(entry.getKey()) && PhantomNativeLocalityEnvelope.prewarm(human, entry.getValue()))
				{
					candidates.add(entry.getKey());
				}
			}
			final var query = _topology.nativeProfilesAt(human, MAXIMUM_PROFILES_PER_HUMAN, profileId ->
			{
				final boolean online = _online.test(profileId);
				if (online && recorder.isRecording())
				{
					recorder.watch(profileId);
					recorder.record(profileId, "LOCAL_CANDIDATE", "ONLINE", "", "topology.eligible", human.x(), human.y(), human.z());
				}
				return online;
			});
			overflow |= query.overflow();
			for (var profile : query.candidates())
			{
				final var live = livePlayers.get(profile.profileId());
				if ((live == null) || PhantomNativeLocalityEnvelope.prewarm(human, live))
				{
					candidates.add(profile.profileId());
					revisions.put(profile.profileId(), profile.sequence());
				}
			}
		}
		// A newly capped query must not withdraw already serviced physical demand.
		for (long id : _local)
		{
			if (humans.stream().anyMatch(human -> canPrewarmAt(id, human, livePlayers))) { candidates.add(id); }
		}
		candidates.removeIf(id -> !humans.stream().anyMatch(human -> canPrewarmAt(id, human, livePlayers)));
		for (long id : candidates) { _topology.findProfile(id).ifPresent(profile -> revisions.put(id, profile.sequence())); }
		final Map<Long, Long> ages = new HashMap<>();
		for (DemandFact fact : _physical.facts()) { ages.put(fact.profileId(), fact.firstDemandNanos()); }
		final var facts = new java.util.ArrayList<DemandFact>();
		for (long id : candidates)
		{
			final var profile = _topology.findProfile(id).orElse(null);
			final PhantomTopologyPoint point = livePlayers.containsKey(id) ? livePlayers.get(id) : profile == null ? null : profile.point();
			if (point == null) { continue; }
			boolean couldKnow = false;
			long distance = Long.MAX_VALUE;
			for (var human : humans)
			{
				if (!PhantomNativeLocalityEnvelope.prewarm(human, point)) { continue; }
				couldKnow |= PhantomNativeLocalityEnvelope.couldKnow(human, point);
				distance = Math.min(distance, point.distanceSquared2D(human));
			}
			facts.add(new DemandFact(id, revisions.getOrDefault(id, 0L), couldKnow, distance, ages.getOrDefault(id, System.nanoTime())));
		}
		_physical = new PhysicalDemand(now, Map.copyOf(revisions), overflow, List.copyOf(facts));
		if (recorder.isRecording())
		{
			for (long id : _local)
			{
				if (!candidates.contains(id)) { recorder.record(id, "LOCAL_CANDIDATE_REMOVED", "LOCAL", "REMOVED", "current.prewarm", 0, 0, 0); }
			}
		}
		_local = Set.copyOf(candidates);
		recorder.record(0, "HUMAN_REFRESH_SUMMARY", "", "", overflow ? "overflow" : "bounded", humans.size(), candidates.size(), livePlayers.size());
		if (recorder.isRecording()) { for (long profileId : candidates)
		{
			recorder.watch(profileId);
			recorder.record(profileId, "LOCAL_CANDIDATE", "LOCAL", "", "physical.demand", revisions.getOrDefault(profileId, 0L), 0, 0);
		} }
		if (_preparationDemand != null) { _preparationDemand.accept(_physical.facts(), _preparationSlots.getAsInt()); }
		else { for (long profileId : candidates) { _physicalDemand.accept(profileId); } }
		final long sequence = ++_sequence;
		final Map<Long, PhantomRelevanceSignalPort.SignalDelivery> delivery = new HashMap<>();
		for (long profileId : candidates)
		{
			if (!_online.test(profileId))
			{
				recorder.record(profileId, "LOCAL_SIGNAL_RESULT", "OFFLINE", "SKIPPED", "presence.offline", sequence, 0, 0);
				continue;
			}
			final var delivered = _signals.submit(profileId, new PhantomRelevanceSignal(SOURCE, sequence, PhantomActivityState.NEARBY_PERCEPTIBLE, SIGNAL_TTL_MILLIS));
			delivery.put(profileId, delivered);
			recorder.record(profileId, "LOCAL_SIGNAL_RESULT", "NEARBY_PERCEPTIBLE", delivered == null ? "NULL" : delivered.name(), SOURCE, sequence, SIGNAL_TTL_MILLIS, 0);
		}
		_delivery = Map.copyOf(delivery);
	}

	public boolean isLocal(long profileId)
	{
		return _local.contains(profileId) && (_clock.getAsLong() < (_physical.timestampMillis() + SIGNAL_TTL_MILLIS)) && _humans.get().stream().limit(MAXIMUM_HUMANS_PER_REFRESH).anyMatch(human -> canPrewarmAt(profileId, human));
	}

	/** Read-only use of the same prewarm gates when preparing a consented human route. */
	public boolean canPrewarmAt(long profileId, PhantomTopologyPoint human)
	{
		return canPrewarmAt(profileId, human, _livePlayers.get());
	}

	/** Native context may itself be a readiness prerequisite; this is physical demand, never online admission. */
	public boolean hasPhysicalDemand(long profileId)
	{
		final var live = _livePlayers.get().get(profileId);
		final var profile = _topology.findProfile(profileId).orElse(null);
		final var point = live != null ? live : profile == null ? null : profile.point();
		return (point != null) && _humans.get().stream().limit(MAXIMUM_HUMANS_PER_REFRESH).anyMatch(human -> PhantomNativeLocalityEnvelope.prewarm(human, point));
	}

	private boolean canPrewarmAt(long profileId, PhantomTopologyPoint human, Map<Long, PhantomTopologyPoint> livePlayers)
	{
		final var live = livePlayers.get(profileId);
		final var profile = _topology.findProfile(profileId).orElse(null);
		final PhantomTopologyPoint point = live != null ? live : profile == null ? null : profile.point();
		return _online.test(profileId) && PhantomNativeLocalityEnvelope.prewarm(human, point);
	}

	/** Recheck committed topology after readiness; a previous local set is only demand. */
	public boolean isCurrentLocal(long profileId)
	{
		return isLocal(profileId);
	}

	public boolean isNativeVisible(long profileId)
	{
		if (!_online.test(profileId))
		{
			return false;
		}
		final PhantomTopologyPoint live = _livePlayers.get().get(profileId);
		return (live != null) && _humans.get().stream().limit(MAXIMUM_HUMANS_PER_REFRESH).anyMatch(human -> PhantomNativeLocalityEnvelope.couldKnow(human, live));
	}

	public record PhysicalDemand(long timestampMillis, Map<Long, Long> positionRevisions, boolean overflow, List<DemandFact> facts)
	{
		public PhysicalDemand(long timestampMillis, Map<Long, Long> positionRevisions, boolean overflow) { this(timestampMillis, positionRevisions, overflow, List.of()); }
	}
	public PhysicalDemand physicalSnapshot() { return _physical; }
	public Map<Long, PhantomRelevanceSignalPort.SignalDelivery> deliverySnapshot() { return _delivery; }

	public int localCount()
	{
		return _local.size();
	}
}
