/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Optimistic party.state adapter over the existing profile component table.
 */
public final class PhantomPartyStore implements PhantomPartyPersistencePort
{
	private static final int MAX_PAGE_SIZE = 256;
	private final PhantomProfileRepository _profiles;
	private final PhantomPartyStateCodec _codec;

	public PhantomPartyStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
		_codec = new PhantomPartyStateCodec();
	}

	@Override
	public Optional<StoredPartyState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPartyModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredPartyState save(long profileId, long expectedRowVersion, PhantomPartyModel.PartyState state)
	{
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0 //
			? _profiles.insertComponent(profileId, PhantomPartyModel.COMPONENT_TYPE, PhantomPartyModel.SCHEMA_VERSION, payload) //
			: _profiles.updateComponent(profileId, PhantomPartyModel.COMPONENT_TYPE, expectedRowVersion, PhantomPartyModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	@Override
	public List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize)
	{
		if ((exclusiveProfileId < 0) || (pageSize < 1) || (pageSize > MAX_PAGE_SIZE))
		{
			throw new IllegalArgumentException("Party state page request is outside bounds.");
		}
		return _profiles.listManagedAfter(PhantomPartyModel.COMPONENT_TYPE, exclusiveProfileId, pageSize).stream().map(managed -> decode(managed.component())).toList();
	}

	private StoredPartyState decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomPartyModel.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown party.state schema version.");
		}
		return new StoredPartyState(component.profileId(), component.rowVersion(), _codec.decode(component.payload()));
	}
}
