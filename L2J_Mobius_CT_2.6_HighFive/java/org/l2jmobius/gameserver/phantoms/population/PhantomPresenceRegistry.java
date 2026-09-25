/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;

/** Schedule-owned logical presence; no materialized Player or activity transition is retained. */
public final class PhantomPresenceRegistry
{
	public enum Presence
	{
		AVAILABLE,
		BUSY,
		OFFLINE
	}

	private final int _maximumProfiles;
	private final Map<Long, Slot> _slots = new HashMap<>();
	private volatile List<ExternalBusySource> _externalBusySources = List.of();

	public PhantomPresenceRegistry(int maximumProfiles)
	{
		if (maximumProfiles < 1)
		{
			throw new IllegalArgumentException("Presence capacity must be positive.");
		}
		_maximumProfiles = maximumProfiles;
	}

	public synchronized boolean schedule(long profileId, PhantomActivityState scheduledState)
	{
		Objects.requireNonNull(scheduledState, "Scheduled state must not be null.");
		if (profileId <= 0)
		{
			return false;
		}
		Slot slot = _slots.get(profileId);
		if (slot == null)
		{
			if (_slots.size() >= _maximumProfiles)
			{
				return false;
			}
			slot = new Slot();
			_slots.put(profileId, slot);
		}
		slot._online = scheduledState != PhantomActivityState.SLEEPING;
		return true;
	}

	public synchronized boolean claimBusy(long profileId, String owner)
	{
		if ((owner == null) || !owner.matches("[a-z][a-z0-9_.-]{0,63}"))
		{
			throw new IllegalArgumentException("BUSY owner must be a bounded decision key.");
		}
		final Slot slot = _slots.get(profileId);
		if ((slot == null) || !slot._online || ((slot._busyOwner != null) && !slot._busyOwner.equals(owner)))
		{
			return false;
		}
		slot._busyOwner = owner;
		return true;
	}

	public synchronized boolean releaseBusy(long profileId, String owner)
	{
		final Slot slot = _slots.get(profileId);
		if ((slot == null) || !Objects.equals(slot._busyOwner, owner) || (slot._busyOwner == null))
		{
			return false;
		}
		slot._busyOwner = null;
		return true;
	}

	public synchronized void remove(long profileId)
	{
		_slots.remove(profileId);
	}

	public synchronized void clear()
	{
		_slots.clear();
	}

	public synchronized void installExternalBusySource(String name, LongPredicate blocked)
	{
		if ((name == null) || !name.matches("[a-z][a-z0-9_.-]{0,63}"))
		{
			throw new IllegalArgumentException("External BUSY source requires a bounded name.");
		}
		Objects.requireNonNull(blocked, "External BUSY predicate must not be null.");
		if ((_externalBusySources.size() >= 8) || _externalBusySources.stream().anyMatch(source -> source.name().equals(name)))
		{
			throw new IllegalStateException("External BUSY source capacity or name conflict.");
		}
		final List<ExternalBusySource> sources = new ArrayList<>(_externalBusySources);
		sources.add(new ExternalBusySource(name, blocked));
		_externalBusySources = List.copyOf(sources);
	}

	public String busyReason(long profileId)
	{
		final String internalOwner;
		synchronized (this)
		{
			final Slot slot = _slots.get(profileId);
			if ((slot == null) || !slot._online)
			{
				return "offline";
			}
			internalOwner = slot._busyOwner;
		}
		if (internalOwner != null)
		{
			return "internal." + internalOwner;
		}
		for (ExternalBusySource source : _externalBusySources)
		{
			try
			{
				if (source.blocked().test(profileId))
				{
					return source.name();
				}
			}
			catch (RuntimeException exception)
			{
				return source.name() + ".unavailable";
			}
		}
		return "none";
	}

	public Presence state(long profileId)
	{
		final String reason = busyReason(profileId);
		return "offline".equals(reason) ? Presence.OFFLINE : "none".equals(reason) ? Presence.AVAILABLE : Presence.BUSY;
	}

	public boolean permitsOrdinaryFarm(long profileId)
	{
		return state(profileId) == Presence.AVAILABLE;
	}

	public Snapshot snapshot()
	{
		int available = 0;
		int busy = 0;
		int offline = 0;
		int externalBusy = 0;
		final List<Long> ids;
		synchronized (this)
		{
			ids = List.copyOf(_slots.keySet());
		}
		for (long profileId : ids)
		{
			final String reason = busyReason(profileId);
			if ("offline".equals(reason))
			{
				offline++;
			}
			else if ("none".equals(reason))
			{
				available++;
			}
			else
			{
				busy++;
				if (!reason.startsWith("internal."))
				{
					externalBusy++;
				}
			}
		}
		return new Snapshot(available, busy, offline, externalBusy);
	}

	public record Snapshot(int available, int busy, int offline, int externalBusy)
	{
		public Snapshot(int available, int busy, int offline)
		{
			this(available, busy, offline, 0);
		}
	}

	private record ExternalBusySource(String name, LongPredicate blocked)
	{
	}

	private static final class Slot
	{
		private boolean _online;
		private String _busyOwner;
	}
}
