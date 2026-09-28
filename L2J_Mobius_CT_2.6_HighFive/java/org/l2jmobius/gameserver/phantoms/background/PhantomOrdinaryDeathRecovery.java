/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.listeners.AbstractEventListener;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;

/** Bounds the ordinary solo corpse window from the native death event, independently of decision scheduling. */
public final class PhantomOrdinaryDeathRecovery implements AutoCloseable
{
	private static final long CORPSE_WINDOW_NANOS = 45_000_000_000L;
	private final PhantomMaterializationService _materialization;
	private final PhantomGoalStateStore _goals;
	private final PhantomBackgroundService _background;
	private final LongSupplier _clock;
	private final Consumer<Runnable> _reconcile;
	private final ConcurrentHashMap<Long, Death> _deaths = new ConcurrentHashMap<>();
	private final AtomicInteger _runningReconciliations = new AtomicInteger();
	private volatile AbstractEventListener _listener;
	private ScheduledFuture<?> _pulse;

	public PhantomOrdinaryDeathRecovery(PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomBackgroundService background)
	{
		this(materialization, goals, background, System::nanoTime);
	}

	public PhantomOrdinaryDeathRecovery(PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomBackgroundService background, LongSupplier clock)
	{
		this(materialization, goals, background, clock, ThreadPool::execute);
	}

	public PhantomOrdinaryDeathRecovery(PhantomMaterializationService materialization, PhantomGoalStateStore goals, PhantomBackgroundService background, LongSupplier clock, Consumer<Runnable> reconcile)
	{
		_materialization = Objects.requireNonNull(materialization);
		_goals = Objects.requireNonNull(goals);
		_background = Objects.requireNonNull(background);
		_clock = Objects.requireNonNull(clock);
		_reconcile = Objects.requireNonNull(reconcile);
	}

	public synchronized boolean install()
	{
		if (_listener != null)
		{
			return false;
		}
		_listener = Containers.Global().addListener(new ConsumerEventListener(Containers.Global(), EventType.ON_CREATURE_DEATH, (java.util.function.Consumer<OnCreatureDeath>) this::onDeath, this));
		_pulse = ThreadPool.schedulePriorityTaskAtFixedRate(this::pulse, 1000, 1000);
		if (_pulse == null)
		{
			_listener.unregisterMe();
			_listener = null;
			return false;
		}
		return true;
	}

	public void onDeath(OnCreatureDeath event)
	{
		if ((_listener == null) || !(event.getTarget() instanceof Player player) || player.isDead() || !player.hasHeadlessOutboundSession() || (player.getParty() != null))
		{
			return;
		}
		_materialization.findByCharacterObjectId(player.getObjectId()).ifPresent(snapshot ->
		{
			if (snapshot.worldPresent())
			{
				_deaths.put(snapshot.profileId(), new Death(snapshot.characterObjectId(), _clock.getAsLong(), new AtomicBoolean(), new AtomicBoolean()));
			}
		});
	}

	public synchronized void pulse()
	{
		if (_listener == null)
		{
			return;
		}
		final long now = _clock.getAsLong();
		for (var entry : _deaths.entrySet())
		{
			try
			{
				recoverDue(entry.getKey(), entry.getValue(), now);
			}
			catch (RuntimeException exception)
			{
				// Keep the native death timestamp and retry on the next bounded pulse.
			}
		}
	}

	private void recoverDue(long profileId, Death death, long now)
	{
		// Playable emits the event immediately before publishing its dead flag.
		if ((now - death.atNanos()) < 100_000_000L)
		{
			return;
		}
		final var snapshot = _materialization.find(profileId).orElse(null);
		if ((snapshot == null) || (snapshot.characterObjectId() != death.characterObjectId()))
		{
			_deaths.remove(profileId, death);
			return;
		}
		if (!death.nativeRecovery().get())
		{
			try (ActionLease action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (action == null)
				{
					return;
				}
				if (!action.player().isDead() || (action.player().getParty() != null))
				{
					_deaths.remove(profileId, death);
					return;
				}
			}
			if ((now - death.atNanos()) < CORPSE_WINDOW_NANOS)
			{
				return;
			}
			final var nativeResult = _background.recoverOrdinaryNativeCorpse(profileId, death.characterObjectId());
			if (!nativeResult.successful())
			{
				if ("death.resurrected".equals(nativeResult.reason()))
				{
					_deaths.remove(profileId, death);
				}
				return;
			}
			death.nativeRecovery().set(true);
		}
		if (death.reconciliation().compareAndSet(false, true))
		{
			try
			{
				_reconcile.accept(() -> reconcile(profileId, death));
			}
			catch (RuntimeException exception)
			{
				death.reconciliation().set(false);
				throw exception;
			}
		}
	}

	private void reconcile(long profileId, Death death)
	{
		synchronized (this)
		{
			if ((_listener == null) || (_deaths.get(profileId) != death))
			{
				death.reconciliation().set(false);
				return;
			}
			_runningReconciliations.incrementAndGet();
		}
		try
		{
			final var goal = _goals.load(profileId).orElse(null);
			if ((goal != null) && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goal().goalType()) && (_background.recover(profileId, goal.goal(), PhantomActivityState.ACTIVE).status() == PhantomBackgroundService.OperationStatus.SUCCESS))
			{
				_deaths.remove(profileId, death);
			}
		}
		finally
		{
			death.reconciliation().set(false);
			_runningReconciliations.decrementAndGet();
		}
	}

	public boolean drained()
	{
		return _runningReconciliations.get() == 0;
	}

	@Override
	public synchronized void close()
	{
		if (_pulse != null)
		{
			_pulse.cancel(false);
			_pulse = null;
		}
		if (_listener != null)
		{
			_listener.unregisterMe();
			_listener = null;
		}
		_deaths.clear();
	}

	private record Death(int characterObjectId, long atNanos, AtomicBoolean nativeRecovery, AtomicBoolean reconciliation)
	{
	}
}
