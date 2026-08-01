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
	private final PhantomConversationExecutionStore _execution;

	public PhantomConversationStore(PhantomProfileRepository profiles)
	{
		this(profiles, null);
	}

	public PhantomConversationStore(PhantomProfileRepository profiles, PhantomConversationExecutionStore execution)
	{
		_profiles = Objects.requireNonNull(profiles);
		_execution = execution;
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

	public PhantomConversationExecutionStore.HandoffResult handoff(long profileId, long expectedRowVersion, ConversationState state, PhantomConversationExecutionModel.ExecutionEntry entry)
	{
		if (_execution == null)
		{
			throw new IllegalStateException("Durable conversation execution handoff is not configured.");
		}
		return _execution.handoff(profileId, expectedRowVersion, state, entry);
	}

	public boolean executionEnabled()
	{
		return _execution != null;
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
