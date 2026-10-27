/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;

/** Causal focused contracts only. This runner never claims Player movement or payment. */
public final class PhantomTravel091Test
{
	public static void main(String[] args)
	{
		final var from = new PhantomNavigationPoint(0, 0, -900, 0);
		final var goal = new PhantomNavigationPoint(19000, 0, 0, 0);
		final var flat = new PhantomNavigationPoint(3000, 0, -900, 0);
		final var ascending = new PhantomNavigationPoint(2598, -1500, -100, 0);
		final var planner = new PhantomWalkCorridorPlanner(new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
			{
				return x == flat.x() && y == flat.y() ? flat : x == ascending.x() && y == ascending.y() ? ascending : null;
			}
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b)
			{
				return a.equals(from) && (b.equals(flat) || b.equals(ascending));
			}
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		}, () -> 1);
		final var request = new PhantomNavigationRequest(91, from, goal, 1, 1000, 100000);
		final var result = planner.planEpisode(request, new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "A proven local ascent must remain admissible.");
		PhantomAssertions.assertEquals(ascending, result.points().getLast(), "A flat first success must not hide a proven connection towards the target surface.");
		PhantomAssertions.assertTrue(result.queries() <= 32 && result.nodes() <= 128 && result.edges() <= 512 && result.labels() <= 4096, "Surface choice retains existing local search caps.");
		System.out.println("PASS travel091.surface-choice; synthetic-only=true; native-player-actions=0");
		for (int scenario : List.of(0, 1, 2, 3))
		{
			final boolean newExit = scenario % 2 == 0; final int priorX = scenario < 2 ? -1024 : -512;
			final var origin = new PhantomNavigationPoint(0, 0, 0, 0);
			final var old = new PhantomNavigationPoint(priorX, 0, 0, 0);
			final var fresh = new PhantomNavigationPoint(priorX - 1024, 1024, 0, 0);
			final var deadEnd = new PhantomNavigationPoint(3000, 0, 0, 0);
			final var corridor = new PhantomWalkCorridorPlanner.Oracle()
			{
				@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface)
				{
					return List.of(old, deadEnd).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(newExit && fresh.x() == x && fresh.y() == y ? fresh : null);
				}
				@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(origin) && b.equals(old) || newExit && a.equals(old) && b.equals(fresh); }
				@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
			};
			final var escape = new PhantomWalkCorridorPlanner(corridor, () -> 1).planEpisode(new PhantomNavigationRequest(94, origin, new PhantomNavigationPoint(19000, 0, 0, 0), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of(origin, old));
			PhantomAssertions.assertTrue(escape.nodes() <= 128 && escape.edges() <= 512 && escape.queries() <= 32 && escape.labels() <= 4096, "A visited interior cannot renew any shared search budget.");
			if (newExit)
			{
				PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, escape.status(), "A proven new checkpoint via an old interior must not be hidden by an unreachable first fallback target; priorX=" + priorX);
				PhantomAssertions.assertEquals(List.of(old, fresh), escape.points(), "Backtracking must retain every native-proven segment and terminate at an unvisited checkpoint.");
				PhantomAssertions.assertTrue(escape.queries() > 0 && origin.distanceTo(escape.points().getLast()) <= 3000, "An escape must retain failed query costs and local endpoint bounds.");
			}
			else { PhantomAssertions.assertTrue(escape.status() != PhantomWalkCorridorPlanner.Status.FOUND && escape.points().isEmpty(), "An old checkpoint alone is not a new frontier or proof of progress."); }
		}
		System.out.println("PASS travel094.visited-interior-new-frontier-and-no-proof; synthetic-only=true; native-player-actions=0");
		final var start = new PhantomNavigationPoint(0, 0, 0, 0); final var prior = new PhantomNavigationPoint(-1024, 0, 0, 0);
		final var corner = new PhantomNavigationPoint(-1024, 512, 0, 0); final var frontier = new PhantomNavigationPoint(-1024, 1024, 0, 0);
		final var saturated = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return new PhantomNavigationPoint(x, y, surface.z(), 0); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(start) && b.equals(prior) || a.equals(prior) && b.equals(corner) || a.equals(corner) && b.equals(frontier); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(prior) && b.equals(frontier) ? List.of(corner, frontier) : null; }
		};
		final var reserved = new PhantomWalkCorridorPlanner(saturated, () -> 1).planEpisode(new PhantomNavigationRequest(94, start, new PhantomNavigationPoint(19000, 0, 1000, 0), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of(start, prior));
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, reserved.status(), "Failed straight probes must leave native queries for the proven detour behind a visited interior.");
		PhantomAssertions.assertEquals(List.of(prior, corner, frontier), reserved.points(), "The reserved native detour must retain its exact complete proof.");
		PhantomAssertions.assertTrue(reserved.queries() > 16 && reserved.queries() <= 32 && reserved.nodes() <= 128 && reserved.edges() <= 512 && reserved.labels() <= 4096, "Exploration must share the original cap, without budget renewal.");
		System.out.println("PASS travel094.probes-reserve-shared-detour-budget; synthetic-only=true; native-player-actions=0");
		dominatedProbes();
		checkpointNovelty();
		fallbackFinalAndSurface();
		pveSegment();
		triangleDominatedProbe();
	}
	private static void triangleDominatedProbe()
	{
		final var origin = new PhantomNavigationPoint(0, 0, 0, 0); final var b = new PhantomNavigationPoint(1024, 0, 0, 0); final var c = new PhantomNavigationPoint(1024, 1024, 0, 0);
		final var redundant = new java.util.concurrent.atomic.AtomicInteger();
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return List.of(b, c).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint target) { return a.equals(origin) && (target.equals(b) || target.equals(c)); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint target) { if (a.equals(b) && target.equals(c)) { redundant.incrementAndGet(); } return null; }
		};
		final var result = new PhantomWalkCorridorPlanner(oracle, () -> 1).plan(new PhantomNavigationRequest(94, origin, new PhantomNavigationPoint(19000, 0, 0, 0), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertTrue(result.status() != PhantomWalkCorridorPlanner.Status.FOUND && result.points().isEmpty(), "Pruning cannot manufacture a route to an unreachable goal.");
		PhantomAssertions.assertEquals(0, redundant.get(), "A native corridor cannot beat an already cheaper label when even its straight-line lower bound is longer.");
		System.out.println("PASS travel094.triangle-dominated-query-retains-no-proof; synthetic-only=true; native-player-actions=0");
	}
	private static void pveSegment()
	{
		final var from = new PhantomNavigationPoint(0, 0, 0, 0); final var to = new PhantomNavigationPoint(1000, 0, 0, 0);
		final var threats = new java.util.ArrayList<PhantomNavigationRequest.PveThreat>();
		threats.add(new PhantomNavigationRequest.PveThreat(new PhantomNavigationPoint(500, 0, 0, 0), 300));
		final var request = new PhantomNavigationRequest(94, from, to, 1, 1000, 100000, threats); threats.clear();
		PhantomAssertions.assertFalse(request.permitsWalkSegment(from, to), "A Geo-proven route must not cross a newly observed unowned PvE threat.");
		PhantomAssertions.assertTrue(request.permitsWalkSegment(from, new PhantomNavigationPoint(-500, 0, 0, 0)), "A native escape away from the same threat remains available.");
		PhantomAssertions.assertFalse(request.permitsWalkSegment(new PhantomNavigationPoint(300, 0, 0, 0), to), "An actor inside danger cannot cross closer through its attacker.");
		PhantomAssertions.assertTrue(request.permitsWalkSegment(new PhantomNavigationPoint(300, 0, 0, 0), from), "An actor inside danger may leave without coming closer.");
		PhantomAssertions.assertTrue(request.permitsWalkSegment(new PhantomNavigationPoint(0, 0, 1000, 0), new PhantomNavigationPoint(1000, 0, 1000, 0)), "A separate native floor is not this threat.");
		PhantomAssertions.assertTrue(new PhantomNavigationRequest(94, from, to, 1, 1000, 100000).permitsWalkSegment(from, to), "Existing requests retain their original geometry contract.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomNavigationRequest(94, from, to, 1, 1000, 100000, java.util.Collections.nCopies(65, request.pveThreats().getFirst())), "Threat overflow must fail closed rather than silently omit danger.");
		final var backend = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend()
		{
			@Override public CapabilitySnapshot capability(PhantomNavigationPoint a, PhantomNavigationPoint b) { return new CapabilitySnapshot(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1); }
			@Override public boolean canMoveDirect(PhantomNavigationPoint a, PhantomNavigationPoint b) { return true; }
			@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest value, PhantomNavigationCancellationToken token) { return null; }
		};
		final var service = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, _ -> false, () -> 1, new org.l2jmobius.gameserver.phantoms.PhantomMetrics());
		service.start(); final var submission = service.submit(request);
		PhantomAssertions.assertTrue(submission.immediateResult() == null || submission.immediateResult().status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.DIRECT_VALIDATED, "Direct-first service admission cannot bypass the observed native threat.");
		service.beginStop(); service.finishStop();
		System.out.println("PASS travel094.native-threat-crossing-escape-snapshot-and-bounds; synthetic-only=true; native-player-actions=0");
	}
	private static void fallbackFinalAndSurface()
	{
		final var origin = new PhantomNavigationPoint(0, 0, 0, 0); final var old = new PhantomNavigationPoint(1024, 0, 0, 0); final var goal = new PhantomNavigationPoint(2048, 0, 0, 0);
		final var detour = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return List.of(old, goal).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(origin) && b.equals(old) || a.equals(old) && b.equals(goal); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var arrived = new PhantomWalkCorridorPlanner(detour, () -> 1).planEpisode(new PhantomNavigationRequest(94, origin, goal, 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of(origin, old, new PhantomNavigationPoint(2000, 0, 0, 0)));
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, arrived.status(), "Final destination novelty cannot veto a completely proven local detour.");
		PhantomAssertions.assertEquals(goal, arrived.points().getLast(), "The final exception retains the exact destination and native intermediate proof.");
		System.out.println("PASS travel094.fallback-final-goal; synthetic-only=true; native-player-actions=0");
		final var start = new PhantomNavigationPoint(0, 0, -900, 0); final var prior = new PhantomNavigationPoint(-1024, 0, -900, 0);
		final var flat = new PhantomNavigationPoint(0, -1024, -900, 0); final var ascending = new PhantomNavigationPoint(-1024, 1024, -100, 0);
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return List.of(prior, flat, ascending).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(start) && b.equals(prior) || a.equals(prior) && (b.equals(flat) || b.equals(ascending)); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var result = new PhantomWalkCorridorPlanner(oracle, () -> 1).planEpisode(new PhantomNavigationRequest(94, start, new PhantomNavigationPoint(19000, 0, 0, 0), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), Set.of(start, prior));
		PhantomAssertions.assertEquals(ascending, result.points().getLast(), "Fallback must retain the same target-surface ordering as the initial native probes, including a proven non-monotone detour.");
		System.out.println("PASS travel094.fallback-surface-order; synthetic-only=true; native-player-actions=0");
	}
	private static void checkpointNovelty()
	{
		final var origin = new PhantomNavigationPoint(0, 0, 0, 0); final var near = new PhantomNavigationPoint(3000, 0, 0, 0);
		final var prior = new PhantomNavigationPoint(2700, 0, 0, 0); final var fresh = new PhantomNavigationPoint(2598, -1500, 0, 0);
		final var oracle = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return List.of(near, fresh).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.equals(origin) && (b.equals(near) || b.equals(fresh)); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { return null; }
		};
		final var planner = new PhantomWalkCorridorPlanner(oracle, () -> 1); final var policy = PhantomNavigationPolicy.productionDefaults();
		final var distant = new PhantomNavigationRequest(94, origin, new PhantomNavigationPoint(19000, 0, 0, 0), 1, 1000, 100000);
		final var novel = planner.planEpisode(distant, new PhantomNavigationCancellationToken(), policy, Set.of(origin, prior));
		PhantomAssertions.assertEquals(fresh, novel.points().getLast(), "A checkpoint only300 units from known same-floor space must not consume another episode instead of new terrain.");
		final var arrival = planner.planEpisode(new PhantomNavigationRequest(94, origin, near, 1, 1000, 100000), new PhantomNavigationCancellationToken(), policy, Set.of(origin, prior));
		PhantomAssertions.assertEquals(near, arrival.points().getLast(), "Progress novelty must never veto the actual fully proven final destination.");
		final var otherLayer = planner.planEpisode(distant, new PhantomNavigationCancellationToken(), policy, Set.of(origin, new PhantomNavigationPoint(prior.x(), prior.y(), 1000, 0)));
		PhantomAssertions.assertEquals(near, otherLayer.points().getLast(), "A separate native layer is not previously explored same-floor space.");
		System.out.println("PASS travel094.checkpoint-novelty-final-goal-and-layer; synthetic-only=true; native-player-actions=0");
	}
	private static void dominatedProbes()
	{
		final var nodes = java.util.stream.IntStream.rangeClosed(0, 5).mapToObj(index -> new PhantomNavigationPoint(index * 1024, 0, 0, 0)).toList();
		final var reverseQueries = new java.util.concurrent.atomic.AtomicInteger();
		final var chain = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return nodes.stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint a, PhantomNavigationPoint b) { return a.y() == 0 && b.y() == 0 && b.x() - a.x() == 1024; }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint a, PhantomNavigationPoint b) { if (b.x() < a.x()) { reverseQueries.incrementAndGet(); } return null; }
		};
		final var result = new PhantomWalkCorridorPlanner(chain, () -> 1).plan(new PhantomNavigationRequest(94, nodes.getFirst(), nodes.getLast(), 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(PhantomWalkCorridorPlanner.Status.FOUND, result.status(), "A directed chain must retain its complete native proof.");
		PhantomAssertions.assertEquals(0, reverseQueries.get(), "An already cheaper ancestor must not spend a new native path query.");

		final var origin = new PhantomNavigationPoint(0, 0, 0, 0); final var a = new PhantomNavigationPoint(1024, 0, 0, 0);
		final var b = new PhantomNavigationPoint(0, 1024, 0, 0); final var c = new PhantomNavigationPoint(1024, 1024, 0, 0); final var goal = new PhantomNavigationPoint(2048, 1024, 0, 0);
		final var p = new PhantomNavigationPoint(1024, 256, 0, 0); final var q = new PhantomNavigationPoint(1024, 1280, 0, 0);
		final var shorter = new PhantomWalkCorridorPlanner.Oracle()
		{
			@Override public PhantomNavigationPoint project(int x, int y, PhantomNavigationPoint surface) { return List.of(origin, a, b, c, goal).stream().filter(point -> point.x() == x && point.y() == y).findFirst().orElse(null); }
			@Override public boolean direct(PhantomNavigationPoint from, PhantomNavigationPoint to) { return from.equals(origin) && (to.equals(a) || to.equals(b)) || from.equals(a) && to.equals(p) || from.equals(p) && to.equals(q) || from.equals(q) && to.equals(c) || from.equals(b) && to.equals(c) || from.equals(c) && to.equals(goal); }
			@Override public List<PhantomNavigationPoint> path(PhantomNavigationPoint from, PhantomNavigationPoint to) { return from.equals(a) && to.equals(c) ? List.of(p, q, c) : null; }
		};
		final var improved = new PhantomWalkCorridorPlanner(shorter, () -> 1).plan(new PhantomNavigationRequest(94, origin, goal, 1, 1000, 100000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults());
		PhantomAssertions.assertEquals(List.of(b, c, goal), improved.points(), "A genuinely shorter freshly proven path must replace a longer bent native corridor.");
		PhantomAssertions.assertTrue(improved.nodes() <= 128 && improved.edges() <= 512 && improved.queries() <= 32 && improved.labels() <= 4096, "Pruning cannot renew any shared search budget.");
		System.out.println("PASS travel094.dominated-query-and-shorter-alternative; synthetic-only=true; native-player-actions=0");
	}
}
