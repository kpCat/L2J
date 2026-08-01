/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.chat;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.network.enums.ChatType;

/**
 * Generic actual-delivery observation seam. A client dispatch scope is visible
 * only while the final chat handler creates its recipient packets; each packet
 * then reports the concrete recipient after normal packet side effects ran.
 */
public final class ChatObservationService
{
	public enum Origin
	{
		CLIENT_CHAT,
		PHANTOM_GENERATED
	}

	public record DispatchDescriptor(long dispatchId, Origin origin, int speakerObjectId, String speakerName, ChatType chatType, String whisperTarget, String finalText, long epochMillis)
	{
		public DispatchDescriptor
		{
			if ((dispatchId <= 0) || (origin == null) || (speakerObjectId <= 0) || (speakerName == null) || speakerName.isBlank() || (speakerName.length() > 64) || (chatType == null) || (finalText == null) || finalText.isEmpty() || (finalText.length() > 1024) || (epochMillis < 0))
			{
				throw new IllegalArgumentException("Chat dispatch descriptor is invalid.");
			}
			whisperTarget = whisperTarget == null ? "" : whisperTarget;
			if (whisperTarget.length() > 64)
			{
				throw new IllegalArgumentException("Chat whisper target exceeds 64 characters.");
			}
		}
	}

	public record DeliveredObservation(DispatchDescriptor dispatch, int recipientObjectId, String recipientName)
	{
		public DeliveredObservation
		{
			Objects.requireNonNull(dispatch);
			if ((recipientObjectId <= 0) || (recipientName == null) || recipientName.isBlank() || (recipientName.length() > 64))
			{
				throw new IllegalArgumentException("Chat delivery recipient is invalid.");
			}
		}
	}

	@FunctionalInterface
	public interface DeliveryObserver
	{
		/** @return true when the bounded consumer accepted the observation. */
		boolean onDelivered(DeliveredObservation observation);

		/** @return true when the consumer accepted the synchronous dispatch boundary. */
		default boolean onDispatchClosed(DispatchDescriptor dispatch)
		{
			return true;
		}
	}

	public interface DispatchHandle extends AutoCloseable
	{
		DispatchDescriptor descriptor();

		int deliveries();

		@Override
		void close();
	}

	public record Snapshot(long scopes, long clientScopes, long generatedScopes, long nestedRejected, long rejections, long mismatches, long captures, long deliveries, long clientDeliveries, long generatedDeliveries, long dispatchesClosed, long backpressure, long callbackFailures, boolean observerRegistered)
	{
	}

	private static final int CLOSED_DISPATCHES = 2048;
	private static final ChatObservationService INSTANCE = new ChatObservationService();
	private final Object _registrationMonitor = new Object();
	private final Object _closedMonitor = new Object();
	private final ThreadLocal<DispatchScope> _scope = new ThreadLocal<>();
	private final ArrayDeque<Long> _closedOrder = new ArrayDeque<>(CLOSED_DISPATCHES);
	private final Set<Long> _closedDispatches = new HashSet<>(CLOSED_DISPATCHES);
	private final AtomicLong _dispatchIds = new AtomicLong();
	private final LongAdder _scopes = new LongAdder();
	private final LongAdder _clientScopes = new LongAdder();
	private final LongAdder _generatedScopes = new LongAdder();
	private final LongAdder _nestedRejected = new LongAdder();
	private final LongAdder _rejections = new LongAdder();
	private final LongAdder _mismatches = new LongAdder();
	private final LongAdder _captures = new LongAdder();
	private final LongAdder _deliveries = new LongAdder();
	private final LongAdder _clientDeliveries = new LongAdder();
	private final LongAdder _generatedDeliveries = new LongAdder();
	private final LongAdder _dispatchesClosed = new LongAdder();
	private final LongAdder _backpressure = new LongAdder();
	private final LongAdder _callbackFailures = new LongAdder();
	private volatile Registration _registration;

	private ChatObservationService()
	{
	}

	public static ChatObservationService getInstance()
	{
		return INSTANCE;
	}

	public DispatchHandle openClientDispatch(int speakerObjectId, String speakerName, ChatType chatType, String whisperTarget, String finalText, long epochMillis)
	{
		return openDispatch(Origin.CLIENT_CHAT, speakerObjectId, speakerName, chatType, whisperTarget, finalText, epochMillis);
	}

	public DispatchHandle openGeneratedDispatch(int speakerObjectId, String speakerName, ChatType chatType, String whisperTarget, String finalText, long epochMillis)
	{
		return openDispatch(Origin.PHANTOM_GENERATED, speakerObjectId, speakerName, chatType, whisperTarget, finalText, epochMillis);
	}

	private DispatchHandle openDispatch(Origin origin, int speakerObjectId, String speakerName, ChatType chatType, String whisperTarget, String finalText, long epochMillis)
	{
		if (_scope.get() != null)
		{
			_nestedRejected.increment();
			return InertScope.INSTANCE;
		}
		if (!validDescriptorFields(speakerObjectId, speakerName, chatType, whisperTarget, finalText, epochMillis))
		{
			_rejections.increment();
			return InertScope.INSTANCE;
		}
		final long dispatchId = _dispatchIds.updateAndGet(value -> value == Long.MAX_VALUE ? 1 : value + 1);
		final DispatchScope scope;
		try
		{
			scope = new DispatchScope(new DispatchDescriptor(dispatchId, origin, speakerObjectId, speakerName, chatType, whisperTarget, finalText, epochMillis), Thread.currentThread());
		}
		catch (RuntimeException exception)
		{
			_rejections.increment();
			return InertScope.INSTANCE;
		}
		_scope.set(scope);
		_scopes.increment();
		if (origin == Origin.CLIENT_CHAT)
		{
			_clientScopes.increment();
		}
		else
		{
			_generatedScopes.increment();
		}
		return scope;
	}

	public DispatchDescriptor captureClientPacket(int senderObjectId, ChatType chatType, String text)
	{
		final DispatchDescriptor descriptor = capturePacket(senderObjectId, chatType, text);
		return (descriptor != null) && (descriptor.origin() == Origin.CLIENT_CHAT) ? descriptor : null;
	}

	public DispatchDescriptor capturePacket(int senderObjectId, ChatType chatType, String text)
	{
		final DispatchScope scope = _scope.get();
		if ((scope == null) || scope._closed || (scope._owner != Thread.currentThread()))
		{
			return null;
		}
		final DispatchDescriptor descriptor = scope._descriptor;
		if ((descriptor.speakerObjectId() != senderObjectId) || (descriptor.chatType() != chatType) || !descriptor.finalText().equals(text))
		{
			_mismatches.increment();
			return null;
		}
		_captures.increment();
		return descriptor;
	}

	public void publishDelivered(DispatchDescriptor descriptor, int senderObjectId, ChatType chatType, String text, int recipientObjectId, String recipientName)
	{
		if ((descriptor == null) || (descriptor.speakerObjectId() != senderObjectId) || (descriptor.chatType() != chatType) || !descriptor.finalText().equals(text) || !validRecipient(recipientObjectId, recipientName))
		{
			_rejections.increment();
			return;
		}
		if (isClosed(descriptor.dispatchId()))
		{
			_mismatches.increment();
			return;
		}
		final DispatchScope scope = _scope.get();
		if ((scope != null) && (scope._descriptor.dispatchId() == descriptor.dispatchId()) && !scope._closed)
		{
			scope._deliveries++;
		}
		if (descriptor.origin() == Origin.CLIENT_CHAT)
		{
			_clientDeliveries.increment();
		}
		else
		{
			_generatedDeliveries.increment();
		}
		final Registration registration = _registration;
		if ((registration == null) || !registration.claim())
		{
			return;
		}
		try
		{
			if (registration._observer.onDelivered(new DeliveredObservation(descriptor, recipientObjectId, recipientName)))
			{
				_deliveries.increment();
			}
			else
			{
				_backpressure.increment();
			}
		}
		catch (RuntimeException exception)
		{
			_callbackFailures.increment();
		}
		finally
		{
			registration.release();
		}
	}

	public AutoCloseable register(DeliveryObserver observer)
	{
		Objects.requireNonNull(observer);
		synchronized (_registrationMonitor)
		{
			if (_registration != null)
			{
				throw new IllegalStateException("A chat delivery observer is already registered.");
			}
			final Registration registration = new Registration(observer);
			_registration = registration;
			return registration;
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_scopes.sum(), _clientScopes.sum(), _generatedScopes.sum(), _nestedRejected.sum(), _rejections.sum(), _mismatches.sum(), _captures.sum(), _deliveries.sum(), _clientDeliveries.sum(), _generatedDeliveries.sum(), _dispatchesClosed.sum(), _backpressure.sum(), _callbackFailures.sum(), _registration != null);
	}

	private static boolean validDescriptorFields(int speakerObjectId, String speakerName, ChatType chatType, String whisperTarget, String finalText, long epochMillis)
	{
		return (speakerObjectId > 0) && (speakerName != null) && !speakerName.isBlank() && (speakerName.length() <= 64) && (chatType != null) && (finalText != null) && !finalText.isEmpty() && (finalText.length() <= 1024) && (epochMillis >= 0) && ((whisperTarget == null) || (whisperTarget.length() <= 64));
	}

	private static boolean validRecipient(int recipientObjectId, String recipientName)
	{
		return (recipientObjectId > 0) && (recipientName != null) && !recipientName.isBlank() && (recipientName.length() <= 64);
	}

	private boolean isClosed(long dispatchId)
	{
		synchronized (_closedMonitor)
		{
			return _closedDispatches.contains(dispatchId);
		}
	}

	private void publishClosed(DispatchDescriptor descriptor)
	{
		synchronized (_closedMonitor)
		{
			if (!_closedDispatches.add(descriptor.dispatchId()))
			{
				return;
			}
			_closedOrder.addLast(descriptor.dispatchId());
			while (_closedOrder.size() > CLOSED_DISPATCHES)
			{
				_closedDispatches.remove(_closedOrder.removeFirst());
			}
		}
		_dispatchesClosed.increment();
		final Registration registration = _registration;
		if ((registration == null) || !registration.claim())
		{
			return;
		}
		try
		{
			if (!registration._observer.onDispatchClosed(descriptor))
			{
				_backpressure.increment();
			}
		}
		catch (RuntimeException exception)
		{
			_callbackFailures.increment();
		}
		finally
		{
			registration.release();
		}
	}

	private final class DispatchScope implements DispatchHandle
	{
		private final DispatchDescriptor _descriptor;
		private final Thread _owner;
		private boolean _closed;
		private int _deliveries;

		private DispatchScope(DispatchDescriptor descriptor, Thread owner)
		{
			_descriptor = descriptor;
			_owner = owner;
		}

		@Override
		public DispatchDescriptor descriptor()
		{
			return _descriptor;
		}

		@Override
		public int deliveries()
		{
			return _deliveries;
		}

		@Override
		public void close()
		{
			if (_closed)
			{
				if ((_owner == Thread.currentThread()) && (_scope.get() == this))
				{
					_scope.remove();
				}
				return;
			}
			_closed = true;
			if ((_owner == Thread.currentThread()) && (_scope.get() == this))
			{
				_scope.remove();
			}
			else
			{
				_mismatches.increment();
			}
			publishClosed(_descriptor);
		}
	}

	private final class Registration implements AutoCloseable
	{
		private final DeliveryObserver _observer;
		private boolean _closed;
		private int _claims;

		private Registration(DeliveryObserver observer)
		{
			_observer = observer;
		}

		private synchronized boolean claim()
		{
			if (_closed)
			{
				return false;
			}
			_claims++;
			return true;
		}

		private synchronized void release()
		{
			_claims--;
			notifyAll();
		}

		@Override
		public void close()
		{
			synchronized (_registrationMonitor)
			{
				if (_registration == this)
				{
					_registration = null;
				}
			}
			boolean interrupted = false;
			synchronized (this)
			{
				_closed = true;
				while (_claims > 0)
				{
					try
					{
						wait();
					}
					catch (InterruptedException exception)
					{
						interrupted = true;
					}
				}
			}
			if (interrupted)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private enum InertScope implements DispatchHandle
	{
		INSTANCE;

		@Override
		public DispatchDescriptor descriptor()
		{
			return null;
		}

		@Override
		public int deliveries()
		{
			return 0;
		}

		@Override
		public void close()
		{
		}
	}
}
