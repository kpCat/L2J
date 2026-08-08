/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;

public final class PhantomRiftStore implements PhantomRiftPersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomRiftStateCodec _codec = new PhantomRiftStateCodec();

	public PhantomRiftStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
	}

	@Override
	public Optional<StoredPreparation> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomRiftModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredPreparation save(long profileId, long expectedRowVersion, Preparation preparation)
	{
		final byte[] payload = _codec.encode(preparation);
		final PhantomProfileComponent component = expectedRowVersion < 0
			? _profiles.insertComponent(profileId, PhantomRiftModel.COMPONENT_TYPE, PhantomRiftModel.SCHEMA_VERSION, payload)
			: _profiles.updateComponent(profileId, PhantomRiftModel.COMPONENT_TYPE, expectedRowVersion, PhantomRiftModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	private StoredPreparation decode(PhantomProfileComponent component)
	{
		if ((component.componentSchemaVersion() != 1) && (component.componentSchemaVersion() != PhantomRiftModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown rift.preparation schema version.");
		}
		return new StoredPreparation(component.profileId(), component.rowVersion(), _codec.decode(component.payload()));
	}
}
