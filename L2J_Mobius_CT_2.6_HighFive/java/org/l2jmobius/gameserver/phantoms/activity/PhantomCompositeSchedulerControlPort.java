/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * One scheduler-installed chain with at most eight isolated control stages.
 */
public final class PhantomCompositeSchedulerControlPort implements PhantomSchedulerControlPort
{
	private static final int MAXIMUM_PORTS = 8;
	private final List<PhantomSchedulerControlPort> _ports;
	private final LongAdder _pulses = new LongAdder();
	private final LongAdder _stageFailures = new LongAdder();

	public PhantomCompositeSchedulerControlPort(List<PhantomSchedulerControlPort> ports)
	{
		if ((ports == null) || ports.isEmpty() || (ports.size() > MAXIMUM_PORTS) || ports.stream().anyMatch(port -> port == null))
		{
			throw new IllegalArgumentException("Scheduler control chain must contain one to eight ports.");
		}
		_ports = List.copyOf(ports);
	}

	@Override
	public void onPulse()
	{
		_pulses.increment();
		for (PhantomSchedulerControlPort port : _ports)
		{
			try
			{
				port.onPulse();
			}
			catch (RuntimeException e)
			{
				_stageFailures.increment();
			}
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_ports.size(), _pulses.sum(), _stageFailures.sum());
	}

	public record Snapshot(int stages, long pulses, long stageFailures)
	{
	}
}
