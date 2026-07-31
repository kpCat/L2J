/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Optimistic conversation.state adapter over the profile component table. */
public final class PhantomConversationStore
{
	public record StoredState(long profileId, long rowVersion, ConversationState state)
	{
		public StoredState
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Stored conversation state metadata is invalid.");
			}
		}
	}

	private final PhantomProfileRepository _profiles;
	private final PhantomConversationStateCodec _codec = new PhantomConversationStateCodec();

	public PhantomConversationStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
	}

	public Optional<StoredState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomConversationModel.COMPONENT_TYPE).map(this::decode);
	}

	public StoredState save(long profileId, long expectedRowVersion, ConversationState state)
	{
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0 //
			? _profiles.insertComponent(profileId, PhantomConversationModel.COMPONENT_TYPE, PhantomConversationModel.SCHEMA_VERSION, payload) //
			: _profiles.updateComponent(profileId, PhantomConversationModel.COMPONENT_TYPE, expectedRowVersion, PhantomConversationModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	private StoredState decode(PhantomProfileComponent component)
	{
		if (!component.componentType().equals(PhantomConversationModel.COMPONENT_TYPE) || (component.componentSchemaVersion() != PhantomConversationModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown conversation.state component or schema.");
		}
		return new StoredState(component.profileId(), component.rowVersion(), _codec.decode(component.payload()));
	}
}
