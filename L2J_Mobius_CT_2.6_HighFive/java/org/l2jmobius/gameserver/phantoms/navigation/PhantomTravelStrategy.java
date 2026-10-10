/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.Comparator;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;

/** Minimum among complete proven candidates; no Euclidean heuristic across teleports. */
public final class PhantomTravelStrategy
{
	public enum Mode { WALK, GK, RETURN_GK }
	public enum Status { FOUND, NO_LAWFUL_PLAN, SEARCH_LIMIT }
	public record Stage(Mode mode, PhantomNavigationPoint from, PhantomNavigationPoint to, List<PhantomNavigationPoint> points, PhantomNormalGatekeeperTravel.Leg gatekeeper, long millis, long adena, int scrolls)
	{
		public Stage
		{
			points = List.copyOf(points);
			if (mode == null || from == null || to == null || from.instanceId() != to.instanceId() || millis < 0 || adena < 0 || scrolls < 0) { throw new IllegalArgumentException("Invalid native travel stage."); }
			if (mode == Mode.WALK && (points.isEmpty() || !points.getLast().equals(to) || gatekeeper != null || adena != 0 || scrolls != 0)) { throw new IllegalArgumentException("WALK must retain its complete proven corridor."); }
			if (mode == Mode.GK && (gatekeeper == null || !points.isEmpty() || scrolls != 0)) { throw new IllegalArgumentException("GK must retain its actual native catalog identity."); }
			if (mode == Mode.RETURN_GK && (gatekeeper != null || !points.isEmpty())) { throw new IllegalArgumentException("RETURN is a native action, not a WALK corridor."); }
		}
	}
	public record Candidate(String id, List<Stage> stages)
	{
		public Candidate
		{
			stages = List.copyOf(stages);
			if (id == null || id.isBlank() || stages.isEmpty() || stages.size() > 64) { throw new IllegalArgumentException("Invalid complete travel candidate."); }
			for (int i = 1; i < stages.size(); i++) { if (!stages.get(i-1).to().equals(stages.get(i).from())) { throw new IllegalArgumentException("Unknown gap in complete native itinerary."); } }
			if (stages.stream().mapToInt(s -> s.points().size()).sum() > 64) { throw new IllegalArgumentException("Complete itinerary exceeds unchanged waypoint bound."); }
		}
		public long millis() { long value = 0; for (var stage : stages) { value = Math.addExact(value, stage.millis()); } return value; }
		public long adena() { long value = 0; for (var stage : stages) { value = Math.addExact(value, stage.adena()); } return value; }
		public int scrolls() { int value = 0; for (var stage : stages) { value = Math.addExact(value, stage.scrolls()); } return value; }
		public Mode mode() { return stages.stream().anyMatch(s -> s.mode() == Mode.RETURN_GK) ? Mode.RETURN_GK : stages.stream().anyMatch(s -> s.mode() == Mode.GK) ? Mode.GK : Mode.WALK; }
	}
	public record Selection(Status status, Candidate candidate) { }
	public record Options(List<PhantomNormalGatekeeperTravel.Leg> legs, double speed, long adena, int scrolls, int classIndex, int level, long epochMinute, boolean gatekeeperAllowed, String returnUnavailableReason, PhantomNavigationPoint returnLanding, long returnMillis, long remainingMillis, java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges)
	{
		public Options
		{
			legs = List.copyOf(legs);
			blockedEdges = java.util.Set.copyOf(blockedEdges);
			if (blockedEdges.size() > 16) { throw new IllegalArgumentException("Too many exact lifetime edge witnesses."); }
			if (legs.size() > 128 || !Double.isFinite(speed) || speed <= 0 || adena < 0 || scrolls < 0 || classIndex < 0 || level < 1 || epochMinute < 0 || returnMillis < 0 || remainingMillis < 0) { throw new IllegalArgumentException("Invalid actual travel resources."); }
		}
	}
	private PhantomTravelStrategy() { }
	/** Complete candidates may contain several fees; resource checks apply to the entire itinerary. */
	public static Selection select(List<Candidate> candidates, long adena, int scrolls, long remainingMillis)
	{
		if (adena < 0 || scrolls < 0 || remainingMillis < 0) { throw new IllegalArgumentException("Invalid actual resource/time budget."); }
		if (candidates.size() > 8) { return new Selection(Status.SEARCH_LIMIT, null); }
		final var candidate = candidates.stream().filter(c -> c.adena() <= adena && c.scrolls() <= scrolls && c.millis() <= remainingMillis)
			.min(Comparator.comparingLong(Candidate::millis).thenComparingLong(Candidate::adena).thenComparingInt(Candidate::scrolls).thenComparing(Candidate::id)).orElse(null);
		return new Selection(candidate == null ? Status.NO_LAWFUL_PLAN : Status.FOUND, candidate);
	}
	public static double distance(PhantomNavigationPoint from, List<PhantomNavigationPoint> points)
	{
		double distance = 0; var previous = from;
		for (var point : points) { distance += previous.distanceTo(point); previous = point; }
		return distance;
	}
}
