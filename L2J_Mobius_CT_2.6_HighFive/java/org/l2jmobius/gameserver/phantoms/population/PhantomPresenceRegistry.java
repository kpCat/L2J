/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

	public synchronized Presence state(long profileId)
	{
		final Slot slot = _slots.get(profileId);
		if ((slot == null) || !slot._online)
		{
			return Presence.OFFLINE;
		}
		return slot._busyOwner == null ? Presence.AVAILABLE : Presence.BUSY;
	}

	public synchronized boolean permitsOrdinaryFarm(long profileId)
	{
		return state(profileId) == Presence.AVAILABLE;
	}

	public synchronized Snapshot snapshot()
	{
		int available = 0;
		int busy = 0;
		int offline = 0;
		for (long profileId : _slots.keySet())
		{
			switch (state(profileId))
			{
				case AVAILABLE -> available++;
				case BUSY -> busy++;
				case OFFLINE -> offline++;
			}
		}
		return new Snapshot(available, busy, offline);
	}

	public record Snapshot(int available, int busy, int offline)
	{
	}

	private static final class Slot
	{
		private boolean _online;
		private String _busyOwner;
	}
}
