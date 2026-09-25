/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;

/** One bounded human-local refresh stage in the shared scheduler pulse. */
public final class PhantomHumanLocalityControl implements PhantomSchedulerControlPort
{
	private static final String SOURCE = "human.local";
	private static final int MAXIMUM_HUMANS_PER_REFRESH = 256;
	private static final int MAXIMUM_PROFILES_PER_HUMAN = 1024;
	private static final long REFRESH_MILLIS = 1000;
	private static final long SIGNAL_TTL_MILLIS = 3000;
	private final PhantomTopologyService _topology;
	private final PhantomRelevanceSignalPort _signals;
	private final Supplier<List<PhantomTopologyPoint>> _humans;
	private final LongSupplier _clock;
	private final LongPredicate _online;
	private volatile Set<Long> _local = Set.of();
	private long _nextRefresh;
	private long _sequence;

	public PhantomHumanLocalityControl(PhantomTopologyService topology, PhantomRelevanceSignalPort signals, Supplier<List<PhantomTopologyPoint>> humans, LongSupplier clock)
	{
		this(topology, signals, humans, clock, profileId -> true);
	}

	public PhantomHumanLocalityControl(PhantomTopologyService topology, PhantomRelevanceSignalPort signals, Supplier<List<PhantomTopologyPoint>> humans, LongSupplier clock, LongPredicate online)
	{
		_topology = Objects.requireNonNull(topology, "topology");
		_signals = Objects.requireNonNull(signals, "signals");
		_humans = Objects.requireNonNull(humans, "humans");
		_clock = Objects.requireNonNull(clock, "clock");
		_online = Objects.requireNonNull(online, "online");
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
		_local = Set.of();
		final TreeSet<Long> candidates = new TreeSet<>();
		for (PhantomTopologyPoint human : _humans.get().stream().limit(MAXIMUM_HUMANS_PER_REFRESH).toList())
		{
			for (var profile : _topology.perceptibleProfilesAt(human, PhantomPerceptionChannel.TARGETABILITY, MAXIMUM_PROFILES_PER_HUMAN))
			{
				candidates.add(profile.profileId());
			}
		}
		final long sequence = ++_sequence;
		final TreeSet<Long> signaled = new TreeSet<>();
		for (long profileId : candidates)
		{
			if (!_online.test(profileId))
			{
				continue;
			}
			final var delivered = _signals.submit(profileId, new PhantomRelevanceSignal(SOURCE, sequence, PhantomActivityState.WARM, SIGNAL_TTL_MILLIS));
			if ((delivered == PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED) || (delivered == PhantomRelevanceSignalPort.SignalDelivery.COALESCED))
			{
				signaled.add(profileId);
			}
		}
		_local = Set.copyOf(signaled);
	}

	public boolean isLocal(long profileId)
	{
		return _local.contains(profileId) && _online.test(profileId) && (_clock.getAsLong() < (_nextRefresh - REFRESH_MILLIS + SIGNAL_TTL_MILLIS));
	}

	public int localCount()
	{
		return _local.size();
	}
}
