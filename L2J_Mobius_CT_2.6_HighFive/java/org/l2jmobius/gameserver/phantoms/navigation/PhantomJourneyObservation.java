package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;

/** In-memory evidence for one admitted Solo goal across native checkpoints. */
public final class PhantomJourneyObservation
{
	public enum Kind { PHYSICAL, MIXED }
	public enum Failure { ROUTE, NATIVE, BUDGET }
	public record Identity(int objectId, long epoch, long goalId, long revision) { }
	record Outcome(Kind kind, PhantomNavigationPoint from, PhantomNavigationPoint to, Failure failure, long elapsedMillis, long walkedDistance) { }

	private final Identity _identity;
	private final PhantomNavigationPoint _from;
	private final PhantomNavigationPoint _target;
	private final String _targetAnchor;
	private final long _started;
	private PhantomNavigationPoint _lastWalkArrival;
	private double _distance;
	private boolean _mixed;
	private boolean _terminal;

	public PhantomJourneyObservation(Identity identity, PhantomNavigationPoint from, PhantomNavigationPoint target, String targetAnchor, long started)
	{
		_identity = identity;
		_from = from;
		_target = target;
		_targetAnchor = targetAnchor;
		_started = started;
	}

	public boolean matches(Identity identity, String targetAnchor)
	{
		return _identity.equals(identity) && _targetAnchor.equals(targetAnchor);
	}

	public synchronized void walked(Edge edge)
	{
		if (!_terminal && !edge.from().equals(edge.to()))
		{
			_distance += edge.from().distanceTo(edge.to());
			_lastWalkArrival = edge.to();
		}
	}

	public synchronized void transported()
	{
		_mixed = true;
		_lastWalkArrival = null;
	}

	public synchronized void checkpoint()
	{
		_lastWalkArrival = null;
	}

	synchronized Outcome complete(Identity identity, String anchor, PhantomNavigationPoint captured, long now)
	{
		if (_terminal || !matches(identity, anchor) || !captured.equals(_lastWalkArrival)) { return null; }
		return outcome(captured, null, now);
	}

	synchronized Outcome fail(Identity identity, Failure failure, long now)
	{
		return _terminal || !_identity.equals(identity) ? null : outcome(_target, failure, now);
	}

	private Outcome outcome(PhantomNavigationPoint to, Failure failure, long now)
	{
		_terminal = true;
		return new Outcome(_mixed ? Kind.MIXED : Kind.PHYSICAL, _from, to, failure, Math.max(0, TimeUnit.NANOSECONDS.toMillis(now - _started)), Math.round(_distance));
	}
}
