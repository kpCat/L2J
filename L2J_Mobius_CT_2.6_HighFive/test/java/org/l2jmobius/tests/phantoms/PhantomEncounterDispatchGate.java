/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;

/** TEST-only worker reservation; native requests, actor leases and clocks remain unchanged. */
final class PhantomEncounterDispatchGate implements PhantomCombatService.Dispatcher
{
	private final PhantomCombatService.Dispatcher delegate;
	private final List<Pending> pending = new ArrayList<>();
	private boolean released;
	private boolean aborted;

	PhantomEncounterDispatchGate(PhantomCombatService.Dispatcher delegate)
	{
		this.delegate = Objects.requireNonNull(delegate);
	}

	@Override
	public synchronized DispatchResult dispatch(Runnable runnable, long delayMillis)
	{
		Objects.requireNonNull(runnable);
		if (aborted) { return DispatchResult.rejected(); }
		final var work = new Pending(runnable, delayMillis); pending.add(work);
		if (released)
		{
			try
			{
				if (!submit(work)) { work.cancelIfNotStarted(); return DispatchResult.rejected(); }
			}
			catch (RuntimeException | Error failure) { work.cancelIfNotStarted(); throw failure; }
		}
		return DispatchResult.accepted(work);
	}

	synchronized void release()
	{
		if (aborted || released) { return; }
		released = true;
		try
		{
			for (var work : List.copyOf(pending))
			{
				if (work.state == DispatchState.SCHEDULED && !submit(work))
				{ throw new IllegalStateException("AUTONOMOUS_ENCOUNTER_DISPATCH_REFUSED"); }
			}
		}
		catch (RuntimeException | Error failure)
		{
			// The native owner must observe its first successful handle cancellation in beginStop.
			// Closing dispatch here prevents new work while leaving that reserved handle cancellable.
			aborted = true; throw failure;
		}
	}

	synchronized void abort()
	{
		aborted = true;
		for (var work : List.copyOf(pending)) { work.cancelIfNotStarted(); }
	}

	private boolean submit(Pending work)
	{
		final DispatchResult result = delegate.dispatch(work::run, work.delayMillis);
		if (result == null || !result.accepted()) { return false; }
		work.delegated = result.handle(); return true;
	}

	private final class Pending implements DispatchHandle
	{
		private final Runnable runnable;
		private final long delayMillis;
		private DispatchState state = DispatchState.SCHEDULED;
		private DispatchHandle delegated;
		Pending(Runnable runnable, long delayMillis) { this.runnable = runnable; this.delayMillis = delayMillis; }

		void run()
		{
			synchronized (PhantomEncounterDispatchGate.this)
			{
				if (state != DispatchState.SCHEDULED)
				{ state = DispatchState.CANCELLED; pending.remove(this); return; }
				state = DispatchState.RUNNING;
			}
			try { runnable.run(); }
			finally
			{
				synchronized (PhantomEncounterDispatchGate.this)
				{ state = DispatchState.FINISHED; pending.remove(this); }
			}
		}

		@Override
		public boolean cancelIfNotStarted()
		{
			synchronized (PhantomEncounterDispatchGate.this)
			{
				if (state != DispatchState.SCHEDULED || delegated != null && !delegated.cancelIfNotStarted()) { return false; }
				state = DispatchState.CANCELLED; pending.remove(this); return true;
			}
		}

		@Override
		public DispatchState state()
		{
			synchronized (PhantomEncounterDispatchGate.this)
			{ return state == DispatchState.SCHEDULED && delegated != null ? delegated.state() : state; }
		}
	}
}
