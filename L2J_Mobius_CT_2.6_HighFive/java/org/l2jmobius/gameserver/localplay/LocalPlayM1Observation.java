package org.l2jmobius.gameserver.localplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Pure decisions for one consented M1 observation; no runtime ownership or I/O. */
public final class LocalPlayM1Observation
{
	public enum PositionSource
	{
		LIVE,
		COMMITTED,
		TRANSITION,
		UNAVAILABLE
	}

	public enum Purpose
	{
		LEAVE,
		RETURN
	}

	public enum RouteReason
	{
		CACHED_ENVELOPE,
		DIRECT_FORWARD,
		PATHFIND_FORWARD,
		PATHFIND_NULL,
		PATH_TOO_LONG,
		DISTANCE_LIMIT,
		FORWARD_SEGMENT_REJECTED,
		TARGET_TRANSITION,
		TARGET_IDENTITY_CHANGED,
		ENVELOPE_STALE,
		ENVELOPE_OFF_ROUTE
	}

	public interface ApproachNavigation
	{
		boolean forward(PhantomTopologyPoint first, PhantomTopologyPoint last);

		List<PhantomTopologyPoint> findPath(PhantomTopologyPoint first, PhantomTopologyPoint last);
	}

	public record ApproachEnvelope(String runId, long profileId, long committedSequence, PhantomTopologyPoint outside, PhantomTopologyPoint prewarm, List<PhantomTopologyPoint> insidePath)
	{
		public ApproachEnvelope
		{
			Objects.requireNonNull(runId);
			Objects.requireNonNull(outside);
			Objects.requireNonNull(prewarm);
			insidePath = List.copyOf(insidePath);
			if ((insidePath.size() < 2) || !insidePath.get(0).equals(prewarm))
			{
				throw new IllegalArgumentException("M1 envelope must begin at its proven prewarm point.");
			}
		}

		public List<PhantomTopologyPoint> fullPath()
		{
			final List<PhantomTopologyPoint> points = new ArrayList<>(insidePath.size() + 1);
			points.add(outside);
			points.addAll(insidePath);
			return List.copyOf(points);
		}
	}

	public record RoutePlan(RouteReason reason, List<PhantomTopologyPoint> points, int cursor)
	{
		public RoutePlan
		{
			points = List.copyOf(points);
		}

		public boolean accepted()
		{
			return (reason == RouteReason.CACHED_ENVELOPE) || (reason == RouteReason.DIRECT_FORWARD) || (reason == RouteReason.PATHFIND_FORWARD);
		}
	}

	public record PositionChoice(PositionSource source, PhantomTopologyPoint observed)
	{
	}

	public record Ticket(String token, String runId, int actorObjectId, long profileId, int objectId, long materializedAtNanos, Purpose purpose, PhantomTopologyPoint destination, long expiresAtNanos)
	{
		public boolean valid(String suppliedToken, String suppliedRunId, int suppliedActorObjectId, long suppliedProfileId, int suppliedObjectId, long suppliedEpoch, Purpose suppliedPurpose, PhantomTopologyPoint suppliedDestination, long nowNanos)
		{
			return (nowNanos < expiresAtNanos) && Objects.equals(token, suppliedToken) && Objects.equals(runId, suppliedRunId) && (actorObjectId == suppliedActorObjectId) && (profileId == suppliedProfileId) && (objectId == suppliedObjectId) && (materializedAtNanos == suppliedEpoch) && (purpose == suppliedPurpose) && Objects.equals(destination, suppliedDestination);
		}
	}

	private LocalPlayM1Observation()
	{
	}

	public static PositionChoice select(PhantomTopologyPoint committed, State state, boolean verifiedLive, PhantomTopologyPoint live, boolean storedOwnershipAbsent)
	{
		if ((state == State.ACTIVE) && verifiedLive && (live != null))
		{
			return new PositionChoice(PositionSource.LIVE, live);
		}
		if ((state == State.STORED) && storedOwnershipAbsent)
		{
			return committed == null ? new PositionChoice(PositionSource.UNAVAILABLE, null) : new PositionChoice(PositionSource.COMMITTED, committed);
		}
		if (state == State.STORED) { return new PositionChoice(PositionSource.TRANSITION, null); }
		return new PositionChoice(state == null ? PositionSource.UNAVAILABLE : PositionSource.TRANSITION, null);
	}

	public static boolean contact(boolean worldPresent, boolean clientVisible, long distance2D)
	{
		return worldPresent && clientVisible && (distance2D <= 900);
	}

	public static RoutePlan planApproach(ApproachEnvelope envelope, String runId, long profileId, long committedSequence, PositionSource source, int objectId, long materializedAtNanos, int lockedObjectId, long lockedEpoch, PhantomTopologyPoint actor, PhantomTopologyPoint observed, int cursor, ApproachNavigation navigation)
	{
		if ((envelope == null) || !envelope.runId().equals(runId) || (envelope.profileId() != profileId))
		{
			return rejected(RouteReason.ENVELOPE_STALE);
		}
		if ((observed == null) || (source == null) || (source == PositionSource.TRANSITION) || (source == PositionSource.UNAVAILABLE) || (actor.instanceId() != observed.instanceId()))
		{
			return rejected(RouteReason.TARGET_TRANSITION);
		}
		if (source == PositionSource.COMMITTED)
		{
			return (envelope.committedSequence() == committedSequence) ? cachedRemainder(envelope, actor, cursor, navigation) : rejected(RouteReason.ENVELOPE_STALE);
		}
		if ((objectId <= 0) || (materializedAtNanos <= 0))
		{
			return rejected(RouteReason.TARGET_TRANSITION);
		}
		if ((lockedObjectId > 0) && ((lockedObjectId != objectId) || (lockedEpoch != materializedAtNanos)))
		{
			return rejected(RouteReason.TARGET_IDENTITY_CHANGED);
		}
		return liveRoute(actor, observed, navigation);
	}

	private static RoutePlan cachedRemainder(ApproachEnvelope envelope, PhantomTopologyPoint actor, int cursor, ApproachNavigation navigation)
	{
		final List<PhantomTopologyPoint> full = envelope.fullPath();
		final int firstSegment = Math.max(0, Math.min(cursor, full.size() - 2));
		int nearestSegment = -1;
		double nearest = Double.MAX_VALUE;
		for (int segment = firstSegment; segment < (full.size() - 1); segment++)
		{
			final PhantomTopologyPoint first = full.get(segment);
			final PhantomTopologyPoint last = full.get(segment + 1);
			if ((actor.instanceId() != first.instanceId()) || (actor.instanceId() != last.instanceId()))
			{
				continue;
			}
			final double dx = last.x() - first.x();
			final double dy = last.y() - first.y();
			final double lengthSquared = (dx * dx) + (dy * dy);
			final double progress = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, (((actor.x() - first.x()) * dx) + ((actor.y() - first.y()) * dy)) / lengthSquared));
			final double horizontal = Math.hypot(actor.x() - first.x() - (progress * dx), actor.y() - first.y() - (progress * dy));
			final double vertical = Math.abs(actor.z() - first.z() - (progress * (last.z() - first.z())));
			final double distance = Math.max(horizontal, vertical);
			if ((distance < nearest) || ((distance == nearest) && (segment > nearestSegment)))
			{
				nearest = distance;
				nearestSegment = segment;
			}
		}
		if ((nearestSegment < 0) || (nearest > 256))
		{
			return rejected(RouteReason.ENVELOPE_OFF_ROUTE);
		}
		int next = nearestSegment + 1;
		if ((next < (full.size() - 1)) && (Math.hypot(actor.x() - full.get(next).x(), actor.y() - full.get(next).y()) <= 32) && (Math.abs(actor.z() - full.get(next).z()) <= 100))
		{
			next++;
		}
		if (!navigation.forward(actor, full.get(next)))
		{
			return rejected(RouteReason.FORWARD_SEGMENT_REJECTED);
		}
		final List<PhantomTopologyPoint> remainder = new ArrayList<>();
		remainder.add(actor);
		for (int index = next; (index < full.size()) && (remainder.size() < 64); index++)
		{
			remainder.add(full.get(index));
		}
		return new RoutePlan(RouteReason.CACHED_ENVELOPE, remainder, nearestSegment);
	}

	private static RoutePlan liveRoute(PhantomTopologyPoint actor, PhantomTopologyPoint observed, ApproachNavigation navigation)
	{
		final double directDistance = Math.hypot((long) actor.x() - observed.x(), (long) actor.y() - observed.y());
		if (navigation.forward(actor, observed))
		{
			return directDistance > 10_000 ? rejected(RouteReason.DISTANCE_LIMIT) : new RoutePlan(RouteReason.DIRECT_FORWARD, List.of(actor, observed), 0);
		}
		final List<PhantomTopologyPoint> found = navigation.findPath(actor, observed);
		if (found == null) { return rejected(RouteReason.PATHFIND_NULL); }
		if (found.size() > 62) { return rejected(RouteReason.PATH_TOO_LONG); }
		final List<PhantomTopologyPoint> points = new ArrayList<>(found.size() + 2);
		points.add(actor);
		points.addAll(found);
		points.add(observed);
		double distance = 0;
		for (int index = 1; index < points.size(); index++)
		{
			final PhantomTopologyPoint first = points.get(index - 1);
			final PhantomTopologyPoint last = points.get(index);
			distance += Math.hypot((long) first.x() - last.x(), (long) first.y() - last.y());
			if (distance > 10_000) { return rejected(RouteReason.DISTANCE_LIMIT); }
			if (!navigation.forward(first, last)) { return rejected(RouteReason.FORWARD_SEGMENT_REJECTED); }
		}
		return new RoutePlan(RouteReason.PATHFIND_FORWARD, points, 0);
	}

	private static RoutePlan rejected(RouteReason reason)
	{
		return new RoutePlan(reason, List.of(), 0);
	}
}
