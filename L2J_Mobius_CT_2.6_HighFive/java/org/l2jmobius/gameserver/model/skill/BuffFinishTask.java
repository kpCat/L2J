/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.model.skill;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;

/**
 * @author Mobius
 */
public class BuffFinishTask
{
	private final Map<BuffInfo, AtomicInteger> _buffInfos = new ConcurrentHashMap<>();
	private ScheduledFuture<?> _task = null;
	private boolean _stopped = false;
	private Object _publication;
	
	private class BuffFinishRunnable implements Runnable
	{
		@Override
		public void run()
		{
			for (Entry<BuffInfo, AtomicInteger> entry : _buffInfos.entrySet())
			{
				final BuffInfo info = entry.getKey();
				final Creature effected = info.getEffected();
				if (effected != null)
				{
					info.runNative("BUFF_EXPIRY_PULSE", () ->
					{
						if (entry.getValue().incrementAndGet() > info.getAbnormalTime())
						{
							info.executeNative("BUFF_EXPIRY", () -> effected.getEffectList().stopSkillEffects(SkillFinishType.NORMAL, info.getSkill().getId()));
						}
					});
				}
			}
		}
	}
	
	public synchronized void removeBuffInfo(BuffInfo info)
	{
		_buffInfos.remove(info);
		
		if (_buffInfos.isEmpty() && (_task != null))
		{
			_task.cancel(true);
			_task = null;
		}
	}
	
	public void addBuffInfo(BuffInfo info)
	{
		info.runNative("BUFF_FINISH_REGISTER", () -> addBuffInfoNative(info));
	}

	private void addBuffInfoNative(BuffInfo info)
	{
		if (!nativeManaged(info))
		{
			synchronized (this)
			{
				_buffInfos.put(info, new AtomicInteger());
				if ((_task == null) && !_stopped) { _task = ThreadPool.scheduleAtFixedRate(new BuffFinishRunnable(), 0, 1000); }
			}
			return;
		}
		synchronized (this) { _buffInfos.put(info, new AtomicInteger()); }
		publishPulse(info);
	}
	
	public void start()
	{
		final BuffInfo info;
		synchronized (this)
		{
			info = _buffInfos.keySet().stream().findFirst().orElse(null);
			if (info == null) { _stopped = false; return; }
		}
		info.runNative("BUFF_FINISH_START", () -> startNative(info));
	}

	private void startNative(BuffInfo info)
	{
		if (!nativeManaged(info))
		{
			synchronized (this)
			{
				_stopped = false;
				if (!_buffInfos.isEmpty() && (_task == null)) { _task = ThreadPool.scheduleAtFixedRate(new BuffFinishRunnable(), 0, 1000); }
			}
			return;
		}
		synchronized (this) { _stopped = false; }
		publishPulse(info);
	}

	private void publishPulse(BuffInfo info)
	{
		final Object publication = new Object();
		synchronized (this)
		{
			if (_stopped || _buffInfos.isEmpty() || (_task != null) || (_publication != null)) { return; }
			_publication = publication;
		}
		try
		{
			final ScheduledFuture<?> future = schedulePulse(info);
			final boolean retained;
			synchronized (this)
			{
				retained = (_publication == publication) && !_stopped && !_buffInfos.isEmpty();
				if (retained) { _task = future; }
				if (_publication == publication) { _publication = null; }
			}
			if (!retained && (future != null)) { future.cancel(false); }
		}
		catch (RuntimeException | Error failure)
		{
			synchronized (this) { if (_publication == publication) { _publication = null; } }
			throw failure;
		}
	}

	private static boolean nativeManaged(BuffInfo info)
	{
		final Creature effected = info.getEffected();
		final Player player = effected instanceof Player actor ? actor : effected != null && effected.isSummon() ? effected.asSummon().getOwner() : null;
		return player != null && player.isNativeWorkManaged();
	}

	private ScheduledFuture<?> schedulePulse(BuffInfo info)
	{
		final ScheduledFuture<?> future = ThreadPool.scheduleAtFixedRateOrThrow(new BuffFinishRunnable(), 0, 1000);
		if (future == null) { throw new RejectedExecutionException("NATIVE_BUFF_FINISH_SUBMIT_NULL"); }
		return future;
	}
	
	public synchronized void stop()
	{
		_stopped = true;
		
		if (_task != null)
		{
			_task.cancel(true);
		}
	}
}
