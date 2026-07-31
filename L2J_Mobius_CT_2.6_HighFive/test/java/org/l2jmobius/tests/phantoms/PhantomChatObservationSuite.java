/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchDescriptor;
import org.l2jmobius.gameserver.network.enums.ChatType;

public final class PhantomChatObservationSuite implements PhantomTestSuite
{
	private static final long SEED = 20002001L;

	@Override
	public String id()
	{
		return "chat-observation";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Chat observation focused mode used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-final-filtered-scope-captures-one-shared-dispatch", context ->
		{
			final ChatObservationService service = ChatObservationService.getInstance();
			final List<ChatObservationService.DeliveredObservation> delivered = new ArrayList<>();
			try (AutoCloseable registration = service.register(observation ->
			{
				delivered.add(observation);
				return true;
			}))
			{
				PhantomAssertions.assertEquals(null, service.captureClientPacket(11, ChatType.GENERAL, "до"), "Packet outside final dispatch scope was captured.");
				try (var scope = service.openClientDispatch(11, "Speaker", ChatType.GENERAL, null, "после фильтра", 1000))
				{
					final DispatchDescriptor first = service.captureClientPacket(11, ChatType.GENERAL, "после фильтра");
					final DispatchDescriptor second = service.captureClientPacket(11, ChatType.GENERAL, "после фильтра");
					PhantomAssertions.assertTrue((first != null) && (first == second), "Packets in one dispatch did not capture the same immutable descriptor.");
					PhantomAssertions.assertEquals(null, service.captureClientPacket(11, ChatType.GENERAL, "до фильтра"), "Mismatched packet text inherited a client dispatch.");
					try (var nested = service.openClientDispatch(12, "Nested", ChatType.GENERAL, null, "nested", 1001))
					{
						PhantomAssertions.assertEquals(null, service.captureClientPacket(12, ChatType.GENERAL, "nested"), "Nested dispatch was not rejected fail-closed.");
					}
					service.publishDelivered(first, 11, ChatType.GENERAL, "после фильтра", 21, "One");
					service.publishDelivered(second, 11, ChatType.GENERAL, "после фильтра", 22, "Two");
				}
				PhantomAssertions.assertEquals(2, delivered.size(), "Actual recipient publications were not observed exactly once.");
				PhantomAssertions.assertEquals(delivered.get(0).dispatch().dispatchId(), delivered.get(1).dispatch().dispatchId(), "Recipients did not share the dispatch ID.");
			}
		});

		registry.add("02-registration-close-backpressure-and-exceptions-are-isolated", context ->
		{
			final ChatObservationService service = ChatObservationService.getInstance();
			final AtomicInteger callbacks = new AtomicInteger();
			final AutoCloseable registration = service.register(observation ->
			{
				callbacks.incrementAndGet();
				if (observation.recipientObjectId() == 31)
				{
					throw new IllegalStateException("injected");
				}
				return false;
			});
			DispatchDescriptor descriptor;
			try (var scope = service.openClientDispatch(13, "Speaker", ChatType.WHISPER, "Target", "текст", 2000))
			{
				descriptor = service.captureClientPacket(13, ChatType.WHISPER, "текст");
			}
			service.publishDelivered(descriptor, 13, ChatType.WHISPER, "текст", 31, "One");
			service.publishDelivered(descriptor, 13, ChatType.WHISPER, "текст", 32, "Two");
			registration.close();
			service.publishDelivered(descriptor, 13, ChatType.WHISPER, "текст", 33, "Three");
			PhantomAssertions.assertEquals(2, callbacks.get(), "Closed registration received a later callback.");
			PhantomAssertions.assertEquals(1L, service.snapshot().callbackFailures(), "Callback exception was not isolated and counted.");
			PhantomAssertions.assertEquals(1L, service.snapshot().backpressure(), "Observer backpressure was not isolated and counted.");
		});
	}
}
