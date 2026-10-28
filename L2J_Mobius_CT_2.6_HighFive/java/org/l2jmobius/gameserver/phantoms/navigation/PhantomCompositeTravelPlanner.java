/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.Leg;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;

/** Bounded composite candidates; the existing native backend supplies every physical proof. */
public final class PhantomCompositeTravelPlanner
{
	private static final int MAX_GK = 4, MAX_LABELS = 512, MAX_EXPANSIONS = 4096, MAX_PROOFS = 32;
	public record Result(PhantomNavigationResult.Status status, PhantomTravelStrategy.Candidate candidate) { }
	@FunctionalInterface public interface Surface { PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint hint); }
	private record Label(PhantomNavigationPoint point, List<PhantomTravelStrategy.Stage> legs, Set<String> visited, double distance, long adena, long millis, String key) { }
	private PhantomCompositeTravelPlanner() { }
	public static Result plan(PhantomNavigationRequest request, PhantomNavigationPolicy policy, PhantomTravelStrategy.Options options,
		BiFunction<PhantomNavigationPoint, PhantomNavigationPoint, PhantomWalkCorridorPlanner.Result> walk, Surface surface,
		Function<Leg, PhantomTravelStrategy.Stage> teleport, BooleanSupplier interrupted)
	{
		if (interrupted.getAsBoolean()) { return new Result(PhantomNavigationResult.Status.CANCELLED, null); }
		if (request.origin().instanceId() != 0 || request.destination().instanceId() != 0) { return new Result(PhantomNavigationResult.Status.NO_PATH, null); }
		final double maximum = Math.min(request.maximumRouteDistance(), policy.maximumRouteDistance());
		final var nativeLegs = new ArrayList<PhantomTravelStrategy.Stage>();
		if (options.gatekeeperAllowed())
		{
			for (var leg : options.legs())
			{
				if (interrupted.getAsBoolean()) { return new Result(PhantomNavigationResult.Status.CANCELLED, null); }
				final var actual = teleport.apply(leg);
				if (actual == null) { continue; }
				final long reserve = PhantomNormalGatekeeperTravel.maximumFee(leg, options.classIndex(), options.level());
				if (reserve <= options.adena()) { nativeLegs.add(new PhantomTravelStrategy.Stage(actual.mode(), actual.from(), actual.to(), actual.points(), actual.gatekeeper(), actual.millis(), reserve, actual.scrolls())); }
			}
		}
		nativeLegs.sort(Comparator.comparing(stage -> stage.gatekeeper().id()));
		final var open = new PriorityQueue<Label>(Comparator.comparingLong(Label::millis).thenComparingLong(Label::adena).thenComparing(Label::key));
		open.add(new Label(request.origin(), List.of(), Set.of(), 0, 0, 0, "city.walk"));
		final var complete = new ArrayList<Label>();
		boolean limited = false; int labels = 1, expansions = 0;
		while (!open.isEmpty() && complete.size() < 7)
		{
			if (interrupted.getAsBoolean()) { return new Result(PhantomNavigationResult.Status.CANCELLED, null); }
			final var current = open.poll();
			final double tail = current.point().distanceTo(request.destination());
			if (tail <= policy.maximumLocalStraightDistance() && current.distance() + tail <= maximum
				&& current.millis() + Math.ceil(1000 * tail / options.speed()) <= options.remainingMillis()) { complete.add(current); }
			if (current.legs().size() >= MAX_GK) { limited = true; continue; }
			for (var stage : nativeLegs)
			{
				if (++expansions > MAX_EXPANSIONS) { limited = true; break; }
				final var leg = stage.gatekeeper();
				if (current.visited().contains(leg.toAnchorId())) { continue; }
				final double ingress = current.point().distanceTo(stage.from());
				if (!current.legs().isEmpty() && ingress > policy.maximumLocalStraightDistance()) { continue; }
				final double distance = current.distance() + ingress;
				if (distance > maximum || stage.adena() > options.adena() - current.adena()) { continue; }
				final long millis = current.millis() + (long)Math.ceil(1000 * ingress / options.speed()) + stage.millis();
				if (millis < 0 || millis > options.remainingMillis()) { continue; }
				if (labels >= MAX_LABELS) { limited = true; continue; }
				final var path = new ArrayList<>(current.legs()); path.add(stage);
				final var visited = new HashSet<>(current.visited()); visited.add(leg.fromAnchorId()); visited.add(leg.toAnchorId());
				open.add(new Label(stage.to(), List.copyOf(path), Set.copyOf(visited), distance, current.adena() + stage.adena(), millis, current.key() + "." + leg.id())); labels++;
			}
			if (expansions > MAX_EXPANSIONS) { break; }
		}
		limited |= !open.isEmpty(); // An unsearched candidate is not evidence that its corridor is impassable.
		final var proven = new ArrayList<PhantomTravelStrategy.Candidate>();
		int proofs = 0;
		for (var candidate : complete)
		{
			var from = request.origin(); final var stages = new ArrayList<PhantomTravelStrategy.Stage>(); boolean valid = true;
			final var transfers = new ArrayList<>(candidate.legs()); transfers.add(null); // Final city egress uses the same proof contract.
			for (var transfer : transfers)
			{
				final var to = transfer == null ? request.destination() : transfer.from();
				final int count = Math.max(1, (int)Math.ceil(from.distanceTo(to) / Math.min(10000, policy.maximumLocalStraightDistance())));
				final var origin = from;
				for (int part = 1; part <= count; part++)
				{
					if (interrupted.getAsBoolean()) { return new Result(PhantomNavigationResult.Status.CANCELLED, null); }
					if (++proofs > MAX_PROOFS) { limited = true; valid = false; break; }
					final var next = part == count ? to : surface.project((int)Math.round(origin.x() + ((long)to.x() - origin.x()) * part / (double)count), (int)Math.round(origin.y() + ((long)to.y() - origin.y()) * part / (double)count), from);
					if (next == null || from.distanceTo(next) > policy.maximumLocalStraightDistance()) { valid = false; break; }
					final var corridor = walk.apply(from, next);
					if (corridor.status() != PhantomWalkCorridorPlanner.Status.FOUND)
					{
						limited |= corridor.status() == PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT;
						if (corridor.status() == PhantomWalkCorridorPlanner.Status.CANCELLED || corridor.status() == PhantomWalkCorridorPlanner.Status.DEADLINE_EXPIRED) { return new Result(corridor.status() == PhantomWalkCorridorPlanner.Status.CANCELLED ? PhantomNavigationResult.Status.CANCELLED : PhantomNavigationResult.Status.DEADLINE_EXPIRED, null); }
						valid = false; break;
					}
					if (corridor.points().isEmpty() || !corridor.points().getLast().equals(next)) { valid = false; break; }
					var previous = from;
					for (var point : corridor.points()) { if (previous.distanceTo(point) > policy.maximumLocalStraightDistance()) { valid = false; break; } previous = point; }
					if (!valid) { break; }
					stages.add(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, from, next, corridor.points(), null, (long)Math.ceil(1000 * PhantomTravelStrategy.distance(from, corridor.points()) / options.speed()), 0, 0));
					from = next;
				}
				if (!valid) { break; }
				if (transfer != null) { stages.add(transfer); from = transfer.to(); }
			}
			if (!valid) { continue; }
			if (stages.size() > 64 || stages.stream().mapToInt(stage -> stage.points().size()).sum() > Math.min(64, policy.maximumWaypoints())
				|| stages.stream().filter(stage -> stage.mode() == PhantomTravelStrategy.Mode.WALK).mapToDouble(stage -> PhantomTravelStrategy.distance(stage.from(), stage.points())).sum() > maximum) { limited = true; continue; }
			proven.add(new PhantomTravelStrategy.Candidate(candidate.key(), stages));
		}
		final var selected = PhantomTravelStrategy.select(proven, options.adena(), options.scrolls(), options.remainingMillis());
		return new Result(selected.candidate() != null ? PhantomNavigationResult.Status.PATH_FOUND : limited ? PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED : PhantomNavigationResult.Status.NO_PATH, selected.candidate());
	}
}
