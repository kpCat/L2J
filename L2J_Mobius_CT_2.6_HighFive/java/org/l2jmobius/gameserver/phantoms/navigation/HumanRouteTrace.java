package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.List;

final class HumanRouteTrace
{
	static final int SAMPLE_MILLIS = 250;
	static final int MAX_SAMPLES = 2400;
	private static final int MAX_WATER_EVENTS = 512;

	record Point(int x, int y, int z, int instance)
	{
		boolean within(Point other, int radius, int height)
		{
			return other != null && instance == other.instance && Math.abs((long) z - other.z) <= height && Math.hypot((long) x - other.x, (long) y - other.y) <= radius;
		}

		String encode()
		{
			return x + "," + y + "," + z + "," + instance;
		}
	}

	record Sample(long sequence, long utc, int segment, Point position, Integer surface, boolean water, String motion) { }
	record WaterEvent(long sequence, long utc, int zone, boolean entered) { }
	record Report(int samples, int drops, int breaks, boolean water, String outcome, String reason, String body) { }

	private final Point _start;
	private final Point _target;
	private final int _radius;
	private final int _height;
	private final List<Sample> _samples = new ArrayList<>();
	private final List<WaterEvent> _waterEvents = new ArrayList<>();
	private long _lastMillis;
	private long _sequence;
	private int _drops;
	private int _breaks;
	private Point _lastNativeMove;
	private boolean _wet;
	private boolean _closed;
	private String _interrupted;

	HumanRouteTrace(Point start, Point target, int radius, int height, long now)
	{
		_start = start;
		_target = target;
		_radius = radius;
		_height = height;
		_lastMillis = now;
	}

	synchronized boolean observe(long now, long utc, Point position, Integer surface, boolean wet, boolean moving)
	{
		final long elapsed = now - _lastMillis;
		if (_closed || _interrupted != null || elapsed < SAMPLE_MILLIS)
		{
			return false;
		}
		if (_samples.size() == MAX_SAMPLES)
		{
			_drops++;
			breakTrace("SAMPLE_LIMIT");
			return false;
		}
		if (elapsed >= SAMPLE_MILLIS * 2)
		{
			_drops += (int) Math.min(MAX_SAMPLES, (elapsed / SAMPLE_MILLIS) - 1);
			_breaks++;
		}
		final Sample previous = _samples.isEmpty() ? null : _samples.getLast();
		final Point prior = previous == null ? _start : previous.position();
		if (!position.within(prior, 512, 384)) { _breaks++; }
		_wet |= wet;
		final String motion = position.equals(prior) ? (moving ? "STATIONARY_WHILE_MOVING" : "PAUSE") : position.equals(_start) ? "RETURN_TO_START" : "MOVE";
		_samples.add(new Sample(++_sequence, utc, _breaks, position, surface, wet, motion));
		_lastMillis = now;
		return true;
	}

	synchronized void nativeMove(Point position)
	{
		if (!_closed && _interrupted == null) { _lastNativeMove = position; }
	}

	synchronized void water(long utc, int zone, boolean entered)
	{
		if (_closed || _interrupted != null)
		{
			return;
		}
		_wet = true;
		if (_waterEvents.size() == MAX_WATER_EVENTS)
		{
			_drops++;
			breakTrace("EVENT_LIMIT");
			return;
		}
		_waterEvents.add(new WaterEvent(++_sequence, utc, zone, entered));
	}

	synchronized void breakTrace(String reason)
	{
		if (!_closed && _interrupted == null)
		{
			_interrupted = reason;
			_breaks++;
		}
	}

	synchronized String interrupted()
	{
		return _interrupted;
	}

	synchronized boolean arrived(Point end)
	{
		return _interrupted == null && _lastNativeMove != null && !_lastNativeMove.equals(_start) && _lastNativeMove.within(_target, _radius, _height)
			&& end != null && end.within(_target, _radius, _height) && end.within(_lastNativeMove, 8, 16);
	}

	synchronized Report finish(Point end, String outcome, String reason)
	{
		_closed = true;
		if (_interrupted != null)
		{
			return report(_samples.isEmpty() ? _start : _samples.getLast().position(), "INTERRUPTED", _interrupted);
		}
		return report(end, outcome, reason);
	}

	synchronized Report report(Point end, String outcome, String reason)
	{
		final var body = new StringBuilder("type=HUMAN_DEMONSTRATED\nsource=OWNER_PLAYER\n")
			.append("landing=OPERATOR_TELEPORT\nstart=").append(_start.encode()).append("\nend=").append(end == null ? "UNKNOWN" : end.encode())
			.append("\ntarget=").append(_target.encode()).append("\narea_radius=").append(_radius).append("\narea_height=").append(_height)
			.append("\nresult=").append(outcome).append("\nreason=").append(reason).append("\nsamples=").append(_samples.size())
			.append("\ndrops=").append(_drops).append("\nbreaks=").append(_breaks).append("\nwater=").append(_wet).append('\n');
		for (int i = 0, j = 0; i < _samples.size() || j < _waterEvents.size();)
		{
			if (j < _waterEvents.size() && (i == _samples.size() || _waterEvents.get(j).sequence() < _samples.get(i).sequence()))
			{
				final WaterEvent event = _waterEvents.get(j++);
				body.append(event.entered() ? "WATER_ENTER" : "WATER_EXIT").append('\t').append(event.sequence()).append('\t').append(event.utc()).append('\t').append(event.zone()).append('\n');
				continue;
			}
			final Sample sample = _samples.get(i++);
			final Sample prior = i > 1 ? _samples.get(i - 2) : null;
			final boolean levelChange = prior != null && prior.segment() == sample.segment() && prior.surface() != null && sample.surface() != null && Math.abs((long) prior.surface() - sample.surface()) >= 64;
			body.append("SAMPLE\t").append(sample.sequence()).append('\t').append(sample.utc()).append('\t').append(sample.segment()).append('\t').append(sample.position().encode())
				.append('\t').append(sample.surface() == null ? "UNKNOWN" : sample.surface()).append('\t').append(sample.water()).append('\t').append(sample.motion()).append('\t').append(levelChange ? "SURFACE_LEVEL_CHANGE" : "-").append('\n');
		}
		return new Report(_samples.size(), _drops, _breaks, _wet, outcome, reason, body.toString());
	}
}
