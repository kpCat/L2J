/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;

public final class PhantomPvpStore implements PhantomPvpPersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomPvpStateCodec _codec = new PhantomPvpStateCodec();

	public PhantomPvpStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
	}

	@Override
	public Optional<StoredEncounter> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPvpModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredEncounter save(long profileId, long expectedRowVersion, Encounter encounter)
	{
		final byte[] payload = _codec.encode(encounter);
		final PhantomProfileComponent component = expectedRowVersion < 0
			? _profiles.insertComponent(profileId, PhantomPvpModel.COMPONENT_TYPE, PhantomPvpModel.SCHEMA_VERSION, payload)
			: _profiles.updateComponent(profileId, PhantomPvpModel.COMPONENT_TYPE, expectedRowVersion, PhantomPvpModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	private StoredEncounter decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomPvpModel.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown pvp.threat schema version.");
		}
		return new StoredEncounter(component.profileId(), component.rowVersion(), _codec.decode(component.payload()));
	}
}
