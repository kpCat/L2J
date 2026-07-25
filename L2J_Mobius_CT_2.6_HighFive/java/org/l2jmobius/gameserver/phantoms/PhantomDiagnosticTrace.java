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
package org.l2jmobius.gameserver.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Optional fixed-capacity sampled trace for short internal event names.
 */
public final class PhantomDiagnosticTrace
{
	private static final int MAX_EVENT_NAME_LENGTH = 48;

	private final boolean _enabled;
	private final int _capacity;
	private final int _sampleEvery;
	private final PhantomMetrics _metrics;
	private final String[] _events;
	private long _attempts;
	private int _start;
	private int _size;

	public PhantomDiagnosticTrace(boolean enabled, int capacity, int sampleEvery, PhantomMetrics metrics)
	{
		if (enabled && ((capacity <= 0) || (sampleEvery <= 0)))
		{
			throw new IllegalArgumentException("Enabled trace requires positive capacity and sample interval.");
		}
		_enabled = enabled;
		_capacity = enabled ? capacity : 0;
		_sampleEvery = enabled ? sampleEvery : 0;
		_metrics = Objects.requireNonNull(metrics);
		_events = enabled ? new String[capacity] : null;
	}

	public synchronized boolean record(String eventName)
	{
		if (!_enabled || !isInternalEventName(eventName))
		{
			return false;
		}

		_attempts++;
		if ((_attempts % _sampleEvery) != 0)
		{
			return false;
		}

		if (_size < _capacity)
		{
			_events[(_start + _size) % _capacity] = eventName;
			_size++;
		}
		else
		{
			_events[_start] = eventName;
			_start = (_start + 1) % _capacity;
			_metrics.recordTraceDropped();
		}
		_metrics.recordTraceRecorded();
		return true;
	}

	public synchronized Snapshot snapshot()
	{
		if (!_enabled)
		{
			return Snapshot.disabled();
		}

		final List<String> events = new ArrayList<>(_size);
		for (int i = 0; i < _size; i++)
		{
			events.add(_events[(_start + i) % _capacity]);
		}
		return new Snapshot(true, _capacity, _sampleEvery, _attempts, List.copyOf(events));
	}

	private static boolean isInternalEventName(String eventName)
	{
		if ((eventName == null) || eventName.isEmpty() || (eventName.length() > MAX_EVENT_NAME_LENGTH))
		{
			return false;
		}

		for (int i = 0; i < eventName.length(); i++)
		{
			final char character = eventName.charAt(i);
			final boolean asciiLetterOrDigit = ((character >= 'a') && (character <= 'z')) || ((character >= 'A') && (character <= 'Z')) || ((character >= '0') && (character <= '9'));
			if (!asciiLetterOrDigit && (character != '.') && (character != '_') && (character != '-'))
			{
				return false;
			}
		}
		return true;
	}

	public record Snapshot(boolean enabled, int capacity, int sampleEvery, long attempts, List<String> events)
	{
		public static Snapshot disabled()
		{
			return new Snapshot(false, 0, 0, 0, List.of());
		}
	}
}
