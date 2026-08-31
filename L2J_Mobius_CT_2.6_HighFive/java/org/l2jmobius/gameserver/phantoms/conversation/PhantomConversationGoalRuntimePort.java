/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;

/** Explicit post-commit synchronization boundary from Conversation durable goals to Decision runtime. */
@FunctionalInterface
public interface PhantomConversationGoalRuntimePort
{
	enum SyncStatus
	{
		SYNCHRONIZED,
		BUSY,
		UNAVAILABLE,
		FAILED
	}

	PhantomConversationGoalRuntimePort NOOP = (profileId, goalId, exactRevision) -> SyncStatus.SYNCHRONIZED;

	SyncStatus synchronize(long profileId, long goalId, long exactRevision);

	static Bridge bridge()
	{
		return new Bridge();
	}

	static PhantomConversationGoalRuntimePort decisionEngine(PhantomDecisionEngine engine)
	{
		Objects.requireNonNull(engine);
		return (profileId, goalId, exactRevision) ->
		{
			final PhantomDecisionEngine.RuntimeSnapshot current = engine.find(profileId).orElse(null);
			if ((current != null) && (current.goalId() == goalId) && (current.goalRevision() == exactRevision))
			{
				return SyncStatus.SYNCHRONIZED;
			}
			return switch (engine.reload(profileId))
			{
				case RELOADED ->
				{
					final PhantomDecisionEngine.RuntimeSnapshot reloaded = engine.find(profileId).orElse(null);
					yield (reloaded != null) && (reloaded.goalId() == goalId) && (reloaded.goalRevision() == exactRevision) ? SyncStatus.SYNCHRONIZED : SyncStatus.FAILED;
				}
				case BUSY -> SyncStatus.BUSY;
				case REJECTED, NOT_RUNNING -> SyncStatus.UNAVAILABLE;
				case PERSISTENCE_CONFLICT, PERSISTENCE_FAILED -> SyncStatus.FAILED;
			};
		};
	}

	final class Bridge implements PhantomConversationGoalRuntimePort
	{
		private PhantomConversationGoalRuntimePort _delegate;

		public synchronized void install(PhantomConversationGoalRuntimePort delegate)
		{
			if ((_delegate != null) || (delegate == null) || (delegate == this))
			{
				throw new IllegalStateException("Conversation goal runtime bridge installation is invalid.");
			}
			_delegate = delegate;
		}

		@Override
		public SyncStatus synchronize(long profileId, long goalId, long exactRevision)
		{
			final PhantomConversationGoalRuntimePort delegate;
			synchronized (this)
			{
				delegate = _delegate;
			}
			return delegate == null ? SyncStatus.UNAVAILABLE : Objects.requireNonNull(delegate.synchronize(profileId, goalId, exactRevision));
		}
	}
}
