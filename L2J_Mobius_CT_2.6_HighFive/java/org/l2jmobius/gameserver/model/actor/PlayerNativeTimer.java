package org.l2jmobius.gameserver.model.actor;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Cancellation;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Ticket;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.TimerRegistration;

/** Captures one lifetime before publication. Scheduler completion is never body completion. */
public final class PlayerNativeTimer
{
	private final boolean _managed;
	private final boolean _repeating;
	private final Runnable _remove;
	private final TimerRegistration _registration;
	private final PlayerNativeWork.Owner _owner;
	private volatile ScheduledFuture<?> _future;
	private volatile boolean _stopped;

	public PlayerNativeTimer(Player explicitPlayer, String kind, boolean repeating, Runnable remove)
	{
		this(explicitPlayer, null, kind, repeating, remove, false);
	}

	public PlayerNativeTimer(Player explicitPlayer, PlayerNativeWork.Owner expectedOwner, String kind, boolean repeating, Runnable remove)
	{
		this(explicitPlayer, expectedOwner, kind, repeating, remove, true);
	}

	private PlayerNativeTimer(Player explicitPlayer, PlayerNativeWork.Owner expectedOwner, String kind, boolean repeating, Runnable remove, boolean captured)
	{
		this(explicitPlayer, expectedOwner, kind, repeating, remove, captured, false);
	}

	/** Optional safety producer; it carries no earned reward obligation before its body starts. */
	public static PlayerNativeTimer ambient(Player player, String kind, Runnable remove)
	{
		return new PlayerNativeTimer(player, null, kind, false, remove, false, true);
	}

	private PlayerNativeTimer(Player explicitPlayer, PlayerNativeWork.Owner expectedOwner, String kind, boolean repeating, Runnable remove, boolean captured, boolean ambient)
	{
		final Player player = explicitPlayer == null ? PlayerNativeWork.inheritedPlayer() : explicitPlayer;
		_managed = (player != null) && player.isNativeWorkManaged();
		_repeating = repeating;
		_remove = remove;
		if (_managed)
		{
			final PlayerNativeWork.Owner owner = captured ? expectedOwner : player.getNativeWorkOwner();
			_owner = owner;
			_registration = ((owner != null) && (owner.player() == player) && (player.getNativeWorkOwner() == owner) && owner.isCurrent()) ? owner.registerTimer(ambient ? null : PlayerNativeWork.current(owner), kind, repeating, this::stopNative) : null;
			if (_registration == null) { _stopped = true; }
		}
		else { _registration = null; _owner = null; }
	}

	public boolean accepted() { return !_stopped; }
	public boolean managed() { return _managed; }
	public ScheduledFuture<?> future()
	{
		final ScheduledFuture<?> future = _future;
		return (future == null) || !_managed ? future : new TimerFuture(future);
	}
	public void reject(Throwable failure)
	{
		if (_registration != null) { _registration.rejected(failure); }
		stopPreserving(failure);
	}

	/** Called only after every native registry has published this timer. */
	public void submit(Runnable action, long delay)
	{
		submit(action, delay, delay);
	}

	public void submit(Runnable action, long initialDelay, long period)
	{
		if (_stopped) { stopNative(); return; }
		try
		{
			final Runnable invocation = () -> invoke(action);
			final ScheduledFuture<?> future = _managed
				? (_repeating ? ThreadPool.scheduleAtFixedRateOrThrow(invocation, initialDelay, period) : ThreadPool.scheduleOrThrow(invocation, initialDelay))
				: (_repeating ? ThreadPool.scheduleAtFixedRate(invocation, initialDelay, period) : ThreadPool.schedule(invocation, initialDelay));
			if (_managed && (future == null)) { throw new IllegalStateException("NATIVE_TIMER_SUBMIT_NULL"); }
			_future = future;
			// The body/cancel/cleanup may already have run before submit returned.
			if (_stopped && (future != null)) { future.cancel(false); }
		}
		catch (RuntimeException | Error failure)
		{
			reject(failure);
			throw failure;
		}
	}

	public void invoke(Runnable action)
	{
		if (!_managed) { if (!_stopped) { action.run(); } return; }
		if (_registration == null) { return; }
		final Ticket ticket;
		try { ticket = _registration.start(); }
		catch (RuntimeException | Error failure) { reject(failure); throw failure; }
		if (ticket == null) { if (_registration.stopped()) { stopNative(); } return; }
		Throwable failure = null;
		try (var context = PlayerNativeWork.enter(ticket)) { action.run(); }
		catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
		finally { _registration.completed(ticket, failure); }
	}

	/** Refuse pending earned cancellation before removing any native registry entry. */
	public boolean cancel(Runnable callback)
	{
		return cancel(callback, true);
	}

	public boolean cancel(Runnable callback, boolean remove)
	{
		if (!_managed)
		{
			final ScheduledFuture<?> future = _future;
			final boolean invoke = (future != null) && !future.isCancelled() && !future.isDone();
			if (remove) { stopNative(); } else { stopFuture(); }
			if (invoke && (callback != null)) { callback.run(); }
			return true;
		}
		if (_registration == null) { if (remove) { stopNative(); } else { stopFuture(); } return true; }
		final Cancellation cancellation = _registration.cancel(callback != null);
		if (!cancellation.accepted()) { return false; }
		final Ticket ticket = cancellation.ticket();
		if (ticket == null)
		{
			if (remove) { stopNative(); } else { stopFuture(); }
			if (cancellation.invokeCallback()) { callback.run(); }
		}
		else if (ticket.tryStart())
		{
			Throwable failure = null;
			try (var context = PlayerNativeWork.enter(ticket))
			{
				if (remove) { stopNative(); } else { stopFuture(); }
				callback.run();
			}
			catch (RuntimeException | Error thrown) { failure = thrown; throw thrown; }
			finally { ticket.complete(failure); }
		}
		else { if (remove) { stopNative(); } else { stopFuture(); } }
		return true;
	}

	private void stopNative()
	{
		stopFuture();
		_remove.run();
	}
	private void stopPreserving(Throwable primary)
	{
		try { stopNative(); }
		catch (RuntimeException | Error secondary)
		{
			if (primary != secondary) { primary.addSuppressed(secondary); }
			if (_owner != null) { _owner.recordFailure(secondary); }
		}
	}
	private void stopFuture()
	{
		_stopped = true;
		final ScheduledFuture<?> future = _future;
		if (future != null) { future.cancel(false); }
	}
	private boolean cancelFuture(boolean interrupt)
	{
		if ((_registration != null) && !_registration.cancel(false).accepted()) { return false; }
		_stopped = true;
		final ScheduledFuture<?> future = _future;
		return (future != null) && future.cancel(interrupt);
	}

	public boolean isActive() { final ScheduledFuture<?> future = _future; return !_stopped && ((future == null) || (!future.isCancelled() && !future.isDone())); }
	public long remaining() { final ScheduledFuture<?> future = _future; return (future == null) || _stopped || future.isCancelled() || future.isDone() ? -1 : future.getDelay(TimeUnit.MILLISECONDS); }

	private final class TimerFuture implements ScheduledFuture<Object>
	{
		private final ScheduledFuture<?> _delegate;
		private TimerFuture(ScheduledFuture<?> delegate) { _delegate = delegate; }
		@Override public boolean cancel(boolean interrupt)
		{
			return cancelFuture(interrupt);
		}
		@Override public boolean isCancelled() { return _delegate.isCancelled(); }
		@Override public boolean isDone() { return _delegate.isDone(); }
		@Override public Object get() throws InterruptedException, ExecutionException { return _delegate.get(); }
		@Override public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return _delegate.get(timeout, unit); }
		@Override public long getDelay(TimeUnit unit) { return _delegate.getDelay(unit); }
		@Override public int compareTo(Delayed other) { return _delegate.compareTo(other); }
	}
}
