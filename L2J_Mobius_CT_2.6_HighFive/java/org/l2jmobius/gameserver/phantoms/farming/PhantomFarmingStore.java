/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Goal 024 adapter over the existing versioned profile-component table. */
public final class PhantomFarmingStore implements PhantomFarmingPersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomFarmingStateCodec _codec = new PhantomFarmingStateCodec();

	public PhantomFarmingStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
	}

	@Override
	public Optional<StoredState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomFarmingModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredState save(long profileId, long expectedRowVersion, FarmingState state)
	{
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0
			? _profiles.insertComponent(profileId, PhantomFarmingModel.COMPONENT_TYPE, PhantomFarmingModel.SCHEMA_VERSION, payload)
			: _profiles.updateComponent(profileId, PhantomFarmingModel.COMPONENT_TYPE, expectedRowVersion, PhantomFarmingModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	private StoredState decode(PhantomProfileComponent component)
	{
		if ((component.componentSchemaVersion() != 1) && (component.componentSchemaVersion() != PhantomFarmingModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown farming.conflict schema version.");
		}
		return new StoredState(component.profileId(), component.rowVersion(), _codec.decode(component.payload()));
	}
}
