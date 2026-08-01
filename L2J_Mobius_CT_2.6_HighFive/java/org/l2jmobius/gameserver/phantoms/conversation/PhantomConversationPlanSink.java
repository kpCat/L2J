/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;

/** Bounded post-commit wake boundary; durable execution state remains authoritative. */
@FunctionalInterface
public interface PhantomConversationPlanSink
{
	void publish(ConversationResponsePlan plan);

	static ObserverOnly observerOnly()
	{
		return new ObserverOnly();
	}

	static Bridge bridge()
	{
		return new Bridge();
	}

	final class Bridge implements PhantomConversationPlanSink
	{
		private PhantomConversationPlanSink _delegate;

		public synchronized void install(PhantomConversationPlanSink delegate)
		{
			if ((_delegate != null) || (delegate == null) || (delegate == this))
			{
				throw new IllegalStateException("Conversation plan sink bridge installation is invalid.");
			}
			_delegate = delegate;
		}

		@Override
		public void publish(ConversationResponsePlan plan)
		{
			final PhantomConversationPlanSink delegate;
			synchronized (this)
			{
				delegate = _delegate;
			}
			if (delegate == null)
			{
				throw new IllegalStateException("Conversation plan sink is not installed.");
			}
			delegate.publish(plan);
		}
	}

	final class ObserverOnly implements PhantomConversationPlanSink
	{
		private final LongAdder _plans = new LongAdder();
		private final LongAdder _proposals = new LongAdder();

		private ObserverOnly()
		{
		}

		@Override
		public void publish(ConversationResponsePlan plan)
		{
			if (plan == null)
			{
				throw new IllegalArgumentException("Conversation plan must not be null.");
			}
			_plans.increment();
			if (plan.proposal() != null)
			{
				_proposals.increment();
			}
		}

		public long plans()
		{
			return _plans.sum();
		}

		public long proposals()
		{
			return _proposals.sum();
		}
	}
}
