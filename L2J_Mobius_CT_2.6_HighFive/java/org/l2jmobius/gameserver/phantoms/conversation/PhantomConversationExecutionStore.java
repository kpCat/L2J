/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

/** Atomic persistence boundary for planner handoff and canonical goal submission. */
public final class PhantomConversationExecutionStore
{
	public enum HandoffStatus
	{
		SAVED,
		DUPLICATE,
		CAPACITY_REACHED
	}

	public record StoredExecution(long profileId, long rowVersion, ExecutionState state)
	{
		public StoredExecution
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Stored conversation execution metadata is invalid.");
			}
		}
	}

	public record HandoffResult(HandoffStatus status, PhantomConversationStore.StoredState conversation, StoredExecution execution)
	{
	}

	public record GoalMutationResult(StoredExecution execution, StoredGoal goal)
	{
	}

	private final PhantomProfileRepository _profiles;
	private final PhantomConversationExecutionCatalog _catalog;
	private final PhantomConversationExecutionCodec _codec;
	private final PhantomConversationStateCodec _conversationCodec = new PhantomConversationStateCodec();

	public PhantomConversationExecutionStore(PhantomProfileRepository profiles, PhantomConversationExecutionCatalog catalog)
	{
		_profiles = Objects.requireNonNull(profiles);
		_catalog = Objects.requireNonNull(catalog);
		_codec = new PhantomConversationExecutionCodec(catalog);
	}

	public Optional<StoredExecution> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomConversationExecutionModel.COMPONENT_TYPE).map(this::decode);
	}

	public List<StoredExecution> pageAfter(long exclusiveProfileId)
	{
		return _profiles.listManagedAfter(PhantomConversationExecutionModel.COMPONENT_TYPE, exclusiveProfileId, _catalog.limits().recoveryPage()).stream().map(item -> decode(item.component())).toList();
	}

	public StoredExecution save(long profileId, long expectedRowVersion, ExecutionState state)
	{
		validateAuthority(state);
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0 //
			? _profiles.insertComponent(profileId, PhantomConversationExecutionModel.COMPONENT_TYPE, PhantomConversationExecutionModel.SCHEMA_VERSION, payload) //
			: _profiles.updateComponent(profileId, PhantomConversationExecutionModel.COMPONENT_TYPE, expectedRowVersion, PhantomConversationExecutionModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	public HandoffResult handoff(long profileId, long expectedConversationVersion, ConversationState conversation, ExecutionEntry entry)
	{
		final StoredExecution current = load(profileId).orElse(null);
		final long replayFloor = Math.max(0, entry.createdMinute() - _catalog.limits().replayHorizonMinutes());
		final ExecutionState base = current == null ? ExecutionState.empty(_catalog.hash(), entry.createdMinute()) : current.state().pruneReceipts(replayFloor);
		validateAuthority(base);
		if (base.contains(entry.planId()))
		{
			return new HandoffResult(HandoffStatus.DUPLICATE, null, current);
		}
		if (base.entries().size() >= PhantomConversationExecutionModel.MAX_ENTRIES)
		{
			return new HandoffResult(HandoffStatus.CAPACITY_REACHED, null, current);
		}
		// Every accepted live entry reserves the receipt slot it will need at terminalization.
		if ((base.receipts().size() + base.entries().size() + 1) > PhantomConversationExecutionModel.MAX_RECEIPTS)
		{
			return new HandoffResult(HandoffStatus.CAPACITY_REACHED, null, current);
		}
		final ExecutionState next = base.add(entry);
		final List<PhantomProfileComponent> components = _profiles.mutateComponentsAtomically(profileId, List.of( //
			new ComponentMutation(PhantomConversationExecutionModel.COMPONENT_TYPE, current == null ? -1 : current.rowVersion(), PhantomConversationExecutionModel.SCHEMA_VERSION, _codec.encode(next)), //
			new ComponentMutation(PhantomConversationModel.COMPONENT_TYPE, expectedConversationVersion, PhantomConversationModel.SCHEMA_VERSION, _conversationCodec.encode(conversation))));
		return new HandoffResult(HandoffStatus.SAVED, decodeConversation(components.get(1)), decode(components.get(0)));
	}

	/**
	 * Durable Goal020-owned handoff for typed system outbound messages that do
	 * not originate from an inbound conversation state transition.
	 */
	public HandoffResult enqueueOutbound(long profileId, ExecutionEntry entry)
	{
		final StoredExecution current = load(profileId).orElse(null);
		final long replayFloor = Math.max(0, entry.createdMinute() - _catalog.limits().replayHorizonMinutes());
		final ExecutionState base = current == null ? ExecutionState.empty(_catalog.hash(), entry.createdMinute()) : current.state().pruneReceipts(replayFloor);
		validateAuthority(base);
		if (base.contains(entry.planId()))
		{
			return new HandoffResult(HandoffStatus.DUPLICATE, null, current);
		}
		if (base.entries().size() >= PhantomConversationExecutionModel.MAX_ENTRIES)
		{
			return new HandoffResult(HandoffStatus.CAPACITY_REACHED, null, current);
		}
		if ((base.receipts().size() + base.entries().size() + 1) > PhantomConversationExecutionModel.MAX_RECEIPTS)
		{
			return new HandoffResult(HandoffStatus.CAPACITY_REACHED, null, current);
		}
		final StoredExecution saved = save(profileId, current == null ? -1 : current.rowVersion(), base.add(entry));
		return new HandoffResult(HandoffStatus.SAVED, null, saved);
	}

	public GoalMutationResult mutateGoal(long profileId, long expectedExecutionVersion, ExecutionState execution, PhantomGoalStateStore goals, long expectedGoalVersion, PhantomGoal goal)
	{
		validateAuthority(execution);
		final List<PhantomProfileComponent> components = _profiles.mutateComponentsAtomically(profileId, List.of( //
			new ComponentMutation(PhantomConversationExecutionModel.COMPONENT_TYPE, expectedExecutionVersion, PhantomConversationExecutionModel.SCHEMA_VERSION, _codec.encode(execution)), //
			goals.componentMutation(expectedGoalVersion, goal)));
		return new GoalMutationResult(decode(components.get(0)), goals.decodeComponent(components.get(1)));
	}

	private StoredExecution decode(PhantomProfileComponent component)
	{
		if (!component.componentType().equals(PhantomConversationExecutionModel.COMPONENT_TYPE) || (component.componentSchemaVersion() != PhantomConversationExecutionModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown conversation.execution component or schema.");
		}
		final ExecutionState state = _codec.decode(component.payload());
		validateAuthority(state);
		return new StoredExecution(component.profileId(), component.rowVersion(), state);
	}

	private PhantomConversationStore.StoredState decodeConversation(PhantomProfileComponent component)
	{
		if (!component.componentType().equals(PhantomConversationModel.COMPONENT_TYPE) || (component.componentSchemaVersion() != PhantomConversationModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown conversation.state component or schema.");
		}
		return new PhantomConversationStore.StoredState(component.profileId(), component.rowVersion(), _conversationCodec.decode(component.payload()));
	}

	private void validateAuthority(ExecutionState state)
	{
		if (!state.catalogHash().equals(_catalog.hash()))
		{
			throw new IllegalArgumentException("conversation.execution catalog authority is stale.");
		}
	}
}
