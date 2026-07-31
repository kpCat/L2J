/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;

/** Observer-only plan boundary. Checkpoint 1 has no execution implementation. */
@FunctionalInterface
public interface PhantomConversationPlanSink
{
	void publish(ConversationResponsePlan plan);

	static ObserverOnly observerOnly()
	{
		return new ObserverOnly();
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
