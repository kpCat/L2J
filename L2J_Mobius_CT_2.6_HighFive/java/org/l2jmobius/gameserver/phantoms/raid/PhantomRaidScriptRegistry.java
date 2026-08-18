/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reload-safe exact-key registry. Registration is inert until an attempt invokes the adapter.
 */
public final class PhantomRaidScriptRegistry
{
	private static final int MAXIMUM_ADAPTERS = 16;
	private static final PhantomRaidScriptRegistry INSTANCE = new PhantomRaidScriptRegistry();

	private final Map<String, Registration> _adapters = new LinkedHashMap<>();
	private long _revision;

	public static PhantomRaidScriptRegistry getInstance()
	{
		return INSTANCE;
	}

	public synchronized Registration install(PhantomRaidScriptAdapter adapter)
	{
		Objects.requireNonNull(adapter);
		if ((adapter.contentId() == null) || adapter.contentId().isBlank() || (adapter.entryNpcId() <= 0) || (adapter.templateId() <= 0))
		{
			throw new IllegalArgumentException("Invalid raid script adapter registration.");
		}
		if (!_adapters.containsKey(adapter.contentId()) && (_adapters.size() >= MAXIMUM_ADAPTERS))
		{
			throw new IllegalStateException("Raid script adapter registry capacity exceeded.");
		}
		final Registration registration = new Registration(adapter.contentId(), ++_revision, adapter);
		_adapters.put(adapter.contentId(), registration);
		return registration;
	}

	public synchronized Optional<Registration> find(String contentId)
	{
		return Optional.ofNullable(_adapters.get(contentId));
	}

	public synchronized boolean registered(String contentId, int entryNpcId, int templateId)
	{
		final Registration registration = _adapters.get(contentId);
		return (registration != null) && (registration.adapter().entryNpcId() == entryNpcId) && (registration.adapter().templateId() == templateId);
	}

	public synchronized int size()
	{
		return _adapters.size();
	}

	public record Registration(String contentId, long revision, PhantomRaidScriptAdapter adapter)
	{
		public Registration
		{
			if ((contentId == null) || contentId.isBlank() || (revision <= 0) || (adapter == null) || !contentId.equals(adapter.contentId()))
			{
				throw new IllegalArgumentException("Invalid raid script adapter registration receipt.");
			}
		}
	}
}