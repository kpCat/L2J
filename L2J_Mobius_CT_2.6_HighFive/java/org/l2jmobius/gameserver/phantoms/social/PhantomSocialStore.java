/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.StoredState;

/**
 * Optimistic social.state adapter over the existing bounded profile component.
 */
public final class PhantomSocialStore implements PersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomSocialCatalog _catalog;
	private final PhantomSocialStateCodec _codec = new PhantomSocialStateCodec();

	public PhantomSocialStore(PhantomProfileRepository profiles, PhantomSocialCatalog catalog)
	{
		_profiles = Objects.requireNonNull(profiles);
		_catalog = Objects.requireNonNull(catalog);
	}

	@Override
	public boolean profileExists(long profileId)
	{
		return _profiles.find(profileId).isPresent();
	}

	@Override
	public Optional<StoredState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomSocialModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredState save(long profileId, long expectedRowVersion, SocialState state)
	{
		_catalog.validateState(state);
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0 //
			? _profiles.insertComponent(profileId, PhantomSocialModel.COMPONENT_TYPE, PhantomSocialModel.SCHEMA_VERSION, payload) //
			: _profiles.updateComponent(profileId, PhantomSocialModel.COMPONENT_TYPE, expectedRowVersion, PhantomSocialModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	private StoredState decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomSocialModel.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown social.state schema version.");
		}
		final SocialState state = _codec.decode(component.payload());
		_catalog.validateState(state);
		return new StoredState(component.profileId(), component.rowVersion(), state);
	}
}
