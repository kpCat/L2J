package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.geodata.IBlock;
import org.l2jmobius.gameserver.geoengine.geodata.IRegion;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomRouteLearning.Evidence;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

public final class PhantomRouteLearningChecks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length == 3 && "--snapshot010".equals(args[0]))
		{
			farmSnapshot010(Path.of(args[1]), Path.of(args[2]));
			return;
		}
		final Path root = Path.of(args[0]);
		physicalScoutArrival();
		physicalScoutExecutionAdmission();
		physicalScoutPreparationWait();
		physicalScoutLocalSession();
		roundtrip(root);
		physicalWitnessRoundtrip(root.resolve("physical"));
		capacityRoundtrip(root.resolve("capacity"));
		probeCadenceAndPriority();
		directedGraphAndProgress(root.resolve("routing"));
		regionalRecoveryWake(root.resolve("farm-recovery"));
		journeyOutcomes(root.resolve("journeys"));
		if (args.length == 2 && args[1].equals("--geo-fixture")) { geoFixture(); }
	}

	private static void farmSnapshot010(Path learnedCopy, Path geoCorpus) throws Exception
	{
		check(Files.isRegularFile(learnedCopy.toRealPath().getParent().resolve(".codex-owned.json")), "Snapshot must be copied to the owned scratch run.");
		final var origin = new PhantomNavigationPoint(-90856, 246584, -3704, 0);
		final var destination = new PhantomNavigationPoint(-84312, 252645, -3664, 0);
		final var policy = PhantomNavigationPolicy.productionDefaults();
		final String fingerprint = PhantomGeoTrainer.fingerprint(geoCorpus, () -> false);
		check(fingerprint.equals("46b8d2d574d0b6c48dcd84a5163c2953d227f9f36207bf769cbf26ead0b6a80c"), "Bartz snapshot and local GeoData fingerprint differ.");
		try (var memory = PhantomRouteLearning.open(learnedCopy, fingerprint))
		{
			final var totals = memory.legTotals();
			check(totals.regions() == 160 && totals.proven() == 52117 && totals.candidate() == 6626, "Bartz learned corpus failed to load completely: " + totals);
			final var knownIncoming = memory.find(new Edge(new PhantomNavigationPoint(-91064, 246808, -3600, 0), origin));
			check(knownIncoming != null && knownIncoming.successes() > 0, "Missing recorded incoming edge for profile 27.");
			final long now = System.nanoTime();
			final var request = new PhantomNavigationRequest(27, origin, destination, now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance());
			final var checks = new java.util.concurrent.atomic.AtomicInteger();
			final var route = memory.provenTravelWalk(request, policy, Set.of(), System.currentTimeMillis(),
				(_, _) -> { checks.incrementAndGet(); return true; }, () -> false);
			check(route.isEmpty() && checks.get() == 0, "Profile 27's snapshot unexpectedly has an outgoing PROVEN connector.");
			System.out.println("PASS Bartz learned snapshot " + totals + "; profile27 incoming PROVEN, zero eligible outgoing connectors");
		}
	}

	private static void regionalRecoveryWake(Path root) throws Exception
	{
		Files.createDirectory(root);
		try (var memory = PhantomRouteLearning.open(root, "a".repeat(64)))
		{
			final var origin = point(31_900);
			final var connector = point(32_720);
			final var destination = point(33_600);
			final int scope = PhantomNavigationPolicy.productionDefaults().maximumLocalStraightDistance();
			memory.propose(new Edge(origin, connector));
			memory.arrived(new Edge(point(200_000), point(200_820)));
			check(memory.recoveryProofGeneration(origin, destination, scope) == 0, "Unrelated region woke farm recovery");
			memory.arrived(new Edge(connector, destination));
			final long ready = memory.recoveryProofGeneration(origin, destination, scope);
			check(ready > 0 && memory.find(new Edge(origin, destination)) == null, "New directed connector did not wake without an exact edge");
			memory.arrived(new Edge(connector, destination));
			check(memory.recoveryProofGeneration(origin, destination, scope) == ready, "Repeated receipt opened another recovery wake-up");
		}
		System.out.println("PASS new regional learned connector wakes a bounded route without invented exact or reverse evidence");
	}

	private static void physicalScoutLocalSession()
	{
		final var settings = new PhantomPhysicalGeoScout.Settings(4, 8, ZoneId.of("Europe/Chisinau"), LocalTime.of(23, 0), 5);
		final var session = new PhantomPhysicalGeoScout.SessionSchedule(settings);
		check(session.mode(Instant.parse("2026-11-22T20:59:59Z")).equals("CLOSED"), "Night session opened before 23:00 local");
		check(session.mode(Instant.parse("2026-11-22T21:00:00Z")).equals("NIGHT"), "Night session missed its local start");
		check(session.mode(Instant.parse("2026-11-23T04:59:59Z")).equals("NIGHT"), "Night session did not cross midnight");
		check(session.mode(Instant.parse("2026-11-23T05:00:00Z")).equals("CLOSED"), "Night session exceeded the 07:00 local end");
		check(session.mode(Instant.parse("2026-03-29T03:59:59Z")).equals("NIGHT"), "Spring DST closed before 07:00 local");
		check(session.mode(Instant.parse("2026-03-29T04:00:00Z")).equals("CLOSED"), "Spring DST moved the local end");
		check(session.mode(Instant.parse("2026-10-25T04:59:59Z")).equals("NIGHT"), "Autumn DST closed before 07:00 local");
		check(session.mode(Instant.parse("2026-10-25T05:00:00Z")).equals("CLOSED"), "Autumn DST moved the local end");
		final Instant noon = Instant.parse("2026-11-22T10:00:00Z");
		session.startCommissioning(noon);
		check(session.mode(noon).equals("COMMISSIONING"), "Daytime commissioning did not open");
		session.startCommissioning(noon.plusSeconds(240));
		check(session.mode(noon.plusSeconds(300)).equals("CLOSED"), "Commissioning was extended after initial activation");
		check(session.mode(Instant.parse("2026-11-22T21:00:00Z")).equals("NIGHT"), "Commissioning disabled scheduled overnight training");
		System.out.println("PASS local across-midnight/DST schedule and bounded commissioning");
	}

	private static void physicalScoutPreparationWait()
	{
		final long second = TimeUnit.SECONDS.toNanos(1);
		final var wait = new PhantomPhysicalGeoScout.AdmissionWait(second);
		check(!wait.expired(second, 100), "Preparation expired on its first sample");
		check(!wait.expired(40 * second, 101), "Committed preparation progress did not renew the wait");
		check(!wait.expired(70 * second, 102), "A progressing reservation stopped at its original 45-second deadline");
		check(!wait.expired(100 * second, 101), "An older inner cursor replaced the observed high water mark");
		check(wait.expired(115 * second, 102), "A stalled cursor kept a reservation beyond the idle deadline");
		System.out.println("PASS admission remains bounded by measured preparation progress");
	}

	private static void physicalScoutArrival()
	{
		final var from = new PhantomNavigationPoint(32700, 100, -200, 0);
		final var to = new PhantomNavigationPoint(32840, 100, -152, 0);
		final var edge = new Edge(from, to);
		final var nativeArrived = new java.util.concurrent.atomic.AtomicBoolean();
		final var receipt = new PhantomPhysicalGeoScout.Arrival(edge, nativeArrived::get);
		check(!receipt.consume(true, 1, to), "Coordinates alone proved a cross-region climb");
		nativeArrived.set(true);
		check(!receipt.consume(true, 0, to), "A final snap without a native progress sample proved an entire leg");
		check(!receipt.consume(false, 1, to), "Stale native owner proved arrival");
		check(!receipt.consume(true, 1, new PhantomNavigationPoint(to.x(), to.y(), to.z(), 1)), "Instance arrival leaked to world0");
		check(!receipt.consume(true, 1, new PhantomNavigationPoint(to.x(), to.y(), from.z(), 0)), "Another floor proved arrival");
		check(receipt.consume(true, 1, to), "Exact current native receipt failed");
		check(!receipt.consume(true, 1, to), "Same receipt published twice");
		check(!new PhantomPhysicalGeoScout.Arrival(new Edge(from, from), () -> true).consume(true, 1, from), "Stationary receipt proved movement");
		check(!new PhantomPhysicalGeoScout.Arrival(new Edge(from, to), () -> true).consume(false, 1, to), "Released or dead scout proved a leg after STOP");
		System.out.println("PASS physical receipt identity, cross-region height, instance isolation and single consumption");
	}

	private static void physicalScoutExecutionAdmission() throws InterruptedException
	{
		final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1);
		final long profile = 7;
		final var owner = org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(profile);
		final var grant = org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.initial(owner);
		gate.publish(profile, 0, grant);
		final org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp ordinaryStamp;
		try (var ongoing = gate.tryAcquire(profile, owner))
		{
			check(ongoing != null && gate.tryAcquireExclusiveIdle(profile, owner) == null, "Scout preempted an admitted ordinary action");
			ordinaryStamp = ongoing.stamp();
		}
		final org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp walkStamp;
		try (var scout = gate.tryAcquireExclusiveIdle(profile, owner))
		{
			check(scout != null && scout.valid(), "Idle grant rejected the low-priority action");
			walkStamp = scout.stamp();
			check(gate.tryAcquire(profile, owner) == null, "A different controller on the same scheduler thread entered during WALK");
			check(gate.tryAcquireCompletionScoped(profile, ordinaryStamp) == null, "An ordinary receipt entered during WALK");
			final var competed = new java.util.concurrent.atomic.AtomicBoolean();
			final var continued = new java.util.concurrent.atomic.AtomicBoolean();
			Thread.startVirtualThread(() ->
			{
				try (var other = gate.tryAcquire(profile, owner)) { competed.set(other != null); }
				try (var nativeChild = gate.tryAcquireScoped(profile, walkStamp)) { continued.set(nativeChild != null && nativeChild.valid()); }
			}).join();
			check(!competed.get() && continued.get(), "A polling worker could not use the exact WALK grant without admitting ordinary work");
			gate.publish(profile, 1, grant.request(new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("test", "next"), "handoff-walk", 1, 100));
			check(!scout.valid() && scout.yieldRequested() && !gate.quiescentCurrent(profile, 1), "Ownership could move before pending WALK drained");
			check(gate.tryAcquireScoped(profile, walkStamp) == null, "Handoff permitted another WALK");
			try (var completion = gate.tryAcquireCompletionScoped(profile, walkStamp))
			{
				check(completion != null && completion.valid(), "Handoff rejected cancellation of the exact WALK");
			}
		}
		check(gate.quiescentCurrent(profile, 1), "WALK release retained the old owner");
		check(gate.tryAcquireCompletionScoped(profile, walkStamp) == null, "Released WALK could still cancel movement");
		gate.publish(profile, 2, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.initial(owner));
		try (var next = gate.tryAcquireExclusiveIdle(profile, owner);
			var child = gate.tryAcquireScoped(profile, next.stamp()))
		{
			check(child != null, "Next WALK could not enter its own action");
			check(gate.tryAcquireCompletionScoped(profile, walkStamp) == null, "Old WALK receipt borrowed the next grant");
			next.close();
			check(gate.tryAcquire(profile, owner) == null, "Owner released before the final action scope closed");
		}
		try (var ordinary = gate.tryAcquire(profile, owner)) { check(ordinary != null && ordinary.valid(), "Scout retained ownership after release"); }
		System.out.println("PASS idle admission, competing owner rejection, native continuation and release");
	}

	private static void physicalWitnessRoundtrip(Path root) throws Exception
	{
		Files.createDirectory(root);
		final String fingerprint = "a".repeat(64);
		final var from = new PhantomNavigationPoint(32720, 100, -200, 0);
		final var to = new PhantomNavigationPoint(32920, 100, -136, 0);
		final var edge = new Edge(from, to);
		try (var memory = PhantomRouteLearning.open(root, fingerprint))
		{
			memory.openPhysicalWitness();
			memory.propose(edge);
			check(edge.equals(memory.scoutFrontier(from, 100, _ -> true)), "Cross-region candidate was skipped");
			check(memory.recordPhysical(null, "test\tSTUCK"), "Blocked witness was rejected");
			memory.flush();
			check(memory.find(edge).successes() == 0, "Failure promoted a candidate");
			check(memory.recordPhysical(edge, "test\tARRIVED"), "Native receipt could not enter writer");
			check(memory.find(edge).successes() == 0, "Witness was published before the writer acknowledged it");
		}
		try (var reopened = PhantomRouteLearning.open(root, fingerprint))
		{
			check(reopened.find(edge).successes() == 1, "Shutdown failed to publish the accepted witness");
			check(reopened.scoutFrontier(from, 100, _ -> true) == null, "An already proven leg was offered again");
		}
		final var file = root.resolve("physical-scout-witness.tsv");
		final var content = Files.readString(file);
		check(content.endsWith("test\tSTUCK\ntest\tARRIVED\n"), "Witness order changed after STOP");
		Files.writeString(file, content + "corrupt\n");
		try (var reopened = PhantomRouteLearning.open(root, fingerprint))
		{
			reopened.openPhysicalWitness();
			check(!reopened.recordPhysical(edge, "test\tARRIVED"), "Corrupt journal admitted new proof");
		}
		check(Files.readString(file).equals(content + "corrupt\n"), "Corrupt witness bytes were replaced");
		System.out.println("PASS witnessed cross-region proof, candidate promotion, STOP flush and corrupted witness protection");
	}

	private static void probeCadenceAndPriority()
	{
		final var clock = new AtomicLong(1);
		final var workers = new ArrayDeque<Runnable>();
		final var backend = new PhantomNavigationBackend()
		{
			@Override public CapabilitySnapshot capability(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
			}
			@Override public boolean canMoveDirect(PhantomNavigationPoint from, PhantomNavigationPoint to) { return false; }
			@Override public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken token) { return List.of(); }
		};
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, workers::offer, clock::get, new PhantomMetrics());
		check(navigation.start(), "Fake navigation did not start");
		final var a = point(1008);
		final var b = point(1600);
		final long scout = Long.MAX_VALUE;
		final var cadence = new PhantomGeoTrainer.ProbePacing(100);
		final var accepted = new ArrayList<Long>();
		final long minute = TimeUnit.MINUTES.toNanos(1);
		final long tick = TimeUnit.MILLISECONDS.toNanos(PhantomGeoTrainer.TICK_MILLIS);
		for (long now = 1; now < 2 * minute; now += tick)
		{
			clock.set(now);
			if (cadence.take(now, navigation.canProbeGeo()))
			{
				final var submission = navigation.submitGeoProbe(request(scout, now, a, b));
				check(submission.status() == PhantomNavigationService.SubmissionStatus.ACCEPTED, "Ready probe rejected or throttled by gameplay cooldown");
				workers.remove().run();
				check(navigation.consume(submission.requestId()).isPresent(), "Accepted probe did not complete");
				accepted.add(now);
			}
		}
		check(accepted.stream().filter(now -> now <= minute).count() >= 90, "Scheduler cannot use the configured rate");
		for (int i = 100; i < accepted.size(); i++)
		{
			check(accepted.get(i) - accepted.get(i - 100) >= minute, "More than 100 probes in a sliding minute");
		}
		clock.set(3 * minute);
		check(cadence.take(clock.get(), navigation.canProbeGeo()), "One probe should resume after idle");
		check(!cadence.take(clock.get() + tick, true), "Paused probes were released in a burst");
		final var probe = navigation.submitGeoProbe(request(scout, clock.get(), a, b));
		final var foreground = navigation.submit(request(1, clock.get(), a, b));
		check(!navigation.canProbeGeo() && !cadence.take(clock.get() + minute, false), "Trainer competed for foreground service");
		check(navigation.consume(probe.requestId()).orElseThrow().status() == PhantomNavigationResult.Status.CANCELLED, "Foreground could not displace queued probe");
		while (!workers.isEmpty()) { workers.remove().run(); }
		check(navigation.consume(foreground.requestId()).isPresent(), "Foreground request did not receive service");
		navigation.beginStop();
		check(navigation.finishStop() && !navigation.canProbeGeo(), "Stop did not release navigation");
		System.out.println("PASS real admission at scheduler cadence, foreground priority and no catch-up: " + accepted.size() + " probes in two virtual minutes");
	}

	private static void directedGraphAndProgress(Path root) throws Exception
	{
		Files.createDirectory(root);
		final String geo = "a".repeat(64);
		final String anchors = "c".repeat(64);
		final var a = point(31900);
		final var b = point(32720);
		final var c = point(33600);
		final var d = point(34500);
		final var ab = new Edge(a, b);
		final var bc = new Edge(b, c);
		final var cd = new Edge(c, d);
		final var chain = Set.of(ab, bc, cd);
		final var proven = new ArrayList<Edge>();
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			chain.forEach(memory::propose);
			final var policy = PhantomNavigationPolicy.productionDefaults();
			final var request = request(1, 1, a, point(34600));
			final var connector = new Edge(d, request.destination());
			final var route = memory.route(request, policy, false, Set.of(), Set.of(), 1000, (from, to) ->
			{
				final var edge = new Edge(from, to);
				proven.add(edge);
				return chain.contains(edge) || edge.equals(connector);
			}, () -> false);
			check(route.equals(List.of(b, c, d, request.destination())) && proven.containsAll(chain) && proven.contains(connector), "Directed crossing was not validated end to end");
			check(memory.route(request(1, 1, d, a), policy, false, Set.of(), Set.of(), 1000, (from, to) -> true, () -> false).isEmpty(), "Graph invented reverse connectivity");
			check(memory.route(request, policy, false, Set.of(), Set.of(bc), 1000, (from, to) -> true, () -> false).isEmpty(), "Exact blacklist was bypassed");
			check(memory.route(request, policy, false, Set.of(), Set.of(), 1000, (from, to) -> !from.equals(b), () -> false).isEmpty(), "Geometry refusal was bypassed");
			final var limited = new PhantomNavigationPolicy(16, 1, 16, 16, 5000, 1000, 12000, 2, 100000, 1000, 3000, 20, 50, 120000);
			check(memory.route(request, limited, false, Set.of(), Set.of(), 1000, (from, to) -> true, () -> false).isEmpty(), "Graph exceeded its waypoint budget");
			final var wrongFloor = request(1, 1, a, new PhantomNavigationPoint(d.x(), d.y(), d.z() + 48, 0));
			check(memory.route(wrongFloor, policy, false, Set.of(), Set.of(), 1000, (from, to) -> true, () -> false).isEmpty(), "Connector crossed floors");
			check(memory.route(request, policy, false, Set.of(), Set.of(), 1000, (from, to) -> true, () -> true).isEmpty(), "Cancelled graph did not yield");
			memory.failed(bc, PhantomRouteLearning.Failure.TRANSIENT, 1000);
			check(memory.route(request, policy, false, Set.of(), Set.of(), 1001, (from, to) -> true, () -> false).isEmpty(), "Unexpired retry was ignored");
			check(!memory.route(request, policy, false, Set.of(), Set.of(), 901001, (from, to) -> true, () -> false).isEmpty(), "Expired retry was treated as permanent");
			check(memory.find(bc).successes() == 0, "Graph usage invented physical proof");
			memory.saveCursor(anchors, 7);
		}
		final Path shard = root.resolve("region-0_0.tsv");
		final var original = Files.readString(shard);
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(memory.loadCursor(anchors, 20) == 7, "Cursor did not resume at the next probe");
			check(memory.loadCursor("d".repeat(64), 20) == 0 && memory.loadCursor(anchors, 7) == 0, "Changed anchors or invalid cursor were trusted");
		}
		try (var memory = PhantomRouteLearning.open(root, "b".repeat(64)))
		{
			check(memory.loadCursor(anchors, 20) == 0, "Cursor reused under changed GeoData");
		}
		Files.writeString(root.resolve("trainer-progress.tsv"), "corrupt");
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(memory.loadCursor(anchors, 20) == 0, "Corrupt cursor was trusted");
		}
		check(Files.readString(shard).equals(original), "Bad cursor changed learned legs");
		System.out.println("PASS multi-hop across shards, fresh directed geometry, blocked and expired edges, cursor reload and reset");
	}

	private static void journeyOutcomes(Path root) throws Exception
	{
		Files.createDirectory(root);
		final String geo = "a".repeat(64);
		final var owner = new PhantomJourneyObservation.Identity(12345678, 700000001, 87654321, 3);
		final var stale = new PhantomJourneyObservation.Identity(12345678, 700000002, 87654321, 3);
		final var changed = new PhantomJourneyObservation.Identity(12345678, 700000001, 87654321, 4);
		final var a = point(32700);
		final var b = point(32900);
		final var c = point(33100);
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			final var journey = new PhantomJourneyObservation(owner, a, c, "final", 0);
			memory.propose(new Edge(a, b));
			check(journey.complete(owner, "final", c, 1) == null, "Candidate-only progress counted as success");
			journey.walked(new Edge(a, b));
			check(journey.complete(owner, "intermediate", b, 2) == null, "Intermediate checkpoint counted as success");
			check(journey.complete(stale, "final", b, 3) == null && journey.complete(changed, "final", b, 3) == null, "Foreign epoch or goal revision completed journey");
			journey.walked(new Edge(b, c));
			memory.recordJourney(journey.complete(owner, "final", c, 2_000_000_000L));
			memory.recordJourney(journey.complete(owner, "final", c, 2_000_000_000L));
			final var mixed = new PhantomJourneyObservation(owner, a, c, "final", 0);
			mixed.walked(new Edge(a, b));
			mixed.transported();
			check(mixed.complete(owner, "final", c, 3_000_000_000L) == null, "Teleport completed the last leg");
			mixed.walked(new Edge(point(33000), c));
			memory.recordJourney(mixed.complete(owner, "final", c, 3_000_000_000L));
			final var failed = new PhantomJourneyObservation(owner, a, c, "final", 0);
			memory.recordJourney(failed.fail(stale, PhantomJourneyObservation.Failure.NATIVE, 1));
			memory.recordJourney(failed.fail(owner, PhantomJourneyObservation.Failure.NATIVE, 4_000_000_000L));
			check(failed.complete(owner, "final", c, 5_000_000_000L) == null, "Terminal failure was reused");
			new PhantomJourneyObservation(owner, a, c, "final", 0).walked(new Edge(a, b)); // discarded ownership
		}
		final Path stats = root.resolve("journey-stats.tsv");
		final var payload = Files.readString(stats);
		check(!payload.contains("12345678") && !payload.contains("87654321") && !payload.contains("700000001") && !payload.contains("final"), "Runtime identity entered persisted aggregates");
		check(payload.contains("PHYSICAL\t0\t0\t1\t0\t2\t1\t0\t1\t0\t2000\t400")
			&& payload.contains("MIXED\t0\t0\t1\t0\t1\t1\t0\t0\t0\t3000\t300"), "Journey counts, native distance or duration are wrong");
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(memory.journeyTotals().equals(new PhantomRouteLearning.JourneyTotals(2, 1, 2)), "Reload lost or duplicated journey counters");
		}
		check(Files.readString(stats).equals(payload), "Reading stats rewrote their bytes");
		Files.writeString(stats, "damaged");
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			final var candidate = new Edge(a, b);
			check(memory.find(candidate).candidate(), "Damaged statistics disabled old route memory");
			final var failed = new PhantomJourneyObservation(owner, a, c, "final", 0);
			memory.recordJourney(failed.fail(owner, PhantomJourneyObservation.Failure.ROUTE, 1));
		}
		check(Files.readString(stats).equals("damaged"), "Damaged statistics were overwritten");
		System.out.println("PASS full native arrival, mixed and failed journeys, idempotence, reload and privacy");
	}

	private static PhantomNavigationPoint point(int x) { return new PhantomNavigationPoint(x, 1008, -32, 0); }

	private static PhantomNavigationRequest request(long profile, long now, PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return new PhantomNavigationRequest(profile, from, to, now, now + 1_000_000_000L, 20_000);
	}

	private static void roundtrip(Path root) throws Exception
	{
		Files.createDirectory(root);
		final var a = new PhantomNavigationPoint(1008, 1008, -32, 0);
		final var b = new PhantomNavigationPoint(1200, 1104, -32, 0);
		final var c = new PhantomNavigationPoint(1200, 912, -32, 0);
		final var d = new PhantomNavigationPoint(1300, 1008, -32, 0);
		final var ab = new Edge(a, b);
		final var ac = new Edge(a, c);
		final var ad = new Edge(a, d);
		final String geo = "a".repeat(64);
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			memory.propose(ab);
			memory.propose(ac);
			memory.propose(ad);
			check(memory.find(ab).successes() == 0, "GeoData must never create native proof");
			final Thread first = Thread.startVirtualThread(() -> memory.arrived(ab));
			final Thread second = Thread.startVirtualThread(() -> memory.arrived(ab));
			first.join();
			second.join();
			memory.failed(ab, PhantomRouteLearning.Failure.TRANSIENT, 1000);
			check(memory.find(ab).successes() == 2 && memory.find(ab).transientFailures() == 1, "Concurrent native proofs or failure lost");
			check(memory.find(new Edge(b, a)) == null, "Reverse edge was inferred");
			check(memory.find(new Edge(new PhantomNavigationPoint(a.x(), a.y(), 32, 0), b)) == null, "Altitude was ignored");
			check(memory.find(new Edge(new PhantomNavigationPoint(a.x(), a.y(), a.z(), 1), b)) == null, "Instance was ignored");
			check(memory.route(request(1, 1, a, b), PhantomNavigationPolicy.productionDefaults(), true, Set.of(), Set.of(ab, ac), 1000,
				(from, to) -> !from.equals(d), () -> false).isEmpty(), "Blacklisted routes were reused");
			memory.flush();
		}
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(memory.find(ab).successes() == 2 && memory.find(ab).transientFailures() == 1, "Native proof did not survive restart");
			check(memory.find(ac).candidate(), "Candidate did not survive restart");
		}
		try (var memory = PhantomRouteLearning.open(root, "b".repeat(64)))
		{
			check(memory.find(ab) == null, "Routes from another GeoData build were loaded");
		}
		final Path shard = root.resolve("region-0_0.tsv");
		Files.writeString(shard, "truncated");
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(memory.find(ab) == null, "Corruption was trusted");
			memory.arrived(ab);
			memory.flush();
			check(Files.readString(shard).equals("truncated"), "Corrupt evidence was overwritten");
		}
		System.out.println("PASS route learning direction, altitude, alternatives, concurrency, restart, stale and corruption");
	}

	private static void capacityRoundtrip(Path root) throws Exception
	{
		Files.createDirectory(root);
		final String geo = "a".repeat(64);
		final Path shard = root.resolve("region-0_0.tsv");
		final String legacy = "PHANTOM_ROUTE_LEARNING\t1\t" + geo + "\tc0e084a66007d0bf9ea06e588938db970029021ec5eb55c84d1149c0b967de21\n"
			+ "0\t1008\t1008\t-32\t1200\t1104\t-32\tPROVEN\tNATIVE_ARRIVED\t1\t2\t0\t1\t901000\n"
			+ "0\t1008\t1008\t-32\t1200\t912\t-32\tCANDIDATE\tGEODATA_PROPOSED\t1\t0\t0\t0\t0\n";
		Files.writeString(shard, legacy);
		final var origin = new PhantomNavigationPoint(1008, 1008, -32, 0);
		final var proven = new Edge(origin, new PhantomNavigationPoint(1200, 1104, -32, 0));
		final var candidate = new Edge(origin, new PhantomNavigationPoint(1200, 912, -32, 0));
		final var legacyProven = new Evidence(true, 2, 0, 1, 901000);
		final var legacyCandidate = new Evidence(true, 0, 0, 0, 0);
		final var newProven = new Evidence(false, 1, 0, 0, 0);
		final int newEdges = 4096 - 2;
		final var overflow = capacityEdge(origin, newEdges);
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(legacyProven.equals(memory.find(proven)), "Legacy PROVEN evidence did not load");
			check(legacyCandidate.equals(memory.find(candidate)), "Legacy CANDIDATE evidence did not load");
			memory.flush();
			check(Files.readString(shard).equals(legacy), "Reading a legacy shard changed its bytes");
			System.out.println("PASS legacy TSV v1 and unchanged bytes after load and flush");

			for (int i = 0; i < newEdges; i++)
			{
				final var edge = capacityEdge(origin, i);
				if (i % 2 == 0) { memory.arrived(edge); }
				else { memory.propose(edge); }
				check(memory.find(edge) != null, "Expected room for directed edge " + (i + 3));
			}
			memory.propose(overflow);
			check(memory.find(overflow) == null, "A 4097th candidate entered a full region");
		}
		final long bytes = Files.size(shard);
		check(bytes > 64 * 1024 && bytes <= 1024 * 1024, "Expected a full shard above the old byte limit");
		try (var memory = PhantomRouteLearning.open(root, geo))
		{
			check(legacyProven.equals(memory.find(proven)), "Legacy PROVEN evidence changed after expansion");
			check(legacyCandidate.equals(memory.find(candidate)), "Legacy CANDIDATE evidence changed after expansion");
			for (int i = 0; i < newEdges; i++)
			{
				final var expected = i % 2 == 0 ? newProven : legacyCandidate;
				check(expected.equals(memory.find(capacityEdge(origin, i))), "Evidence did not survive reload for directed edge " + (i + 3));
			}
			check(memory.find(overflow) == null, "A 4097th candidate was persisted");
		}
		System.out.println("PASS 4096 directed edges after reload (" + bytes + " bytes), including both legacy entries");
	}

	private static Edge capacityEdge(PhantomNavigationPoint origin, int index)
	{
		return new Edge(origin, new PhantomNavigationPoint(1208 + (index % 64) * 16, 1008 - (index / 64) * 12, -32, 0));
	}

	private static void geoFixture() throws Exception
	{
		final Path game = Path.of(".").toRealPath();
		final Path checkout = game.getParent().getParent().getParent();
		check(game.endsWith(Path.of("HighFive", "dist", "game")) && Files.isRegularFile(checkout.resolve(".geo-fixture")), "Expected an isolated GeoData fixture");
		Files.createDirectory(checkout.resolve(".git"));
		final Path learned = Files.createDirectories(game.resolve("data/phantoms/travel/learned"));
		Files.writeString(learned.resolve("manifest.json"), "{\"schema\":1,\"format\":\"phantom-directed-leg-tsv\"}\n");
		Files.createDirectories(game.resolve("config/Custom"));
		Files.writeString(game.resolve("config/GeoEngine.ini"), "GeoDataPath=data/geodata\nPathFinding=2\n");
		Files.writeString(game.resolve("config/Custom/PhantomGeoTrainer.ini"), "EnableSuperGeoTrainer=True\n");
		Files.createDirectories(game.resolve("data/zones"));
		Files.writeString(game.resolve("data/zones/fixture.xml"), """
			<list enabled="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="../xsd/zones.xsd">
			  <zone id="1001" name="geo_trainer_fixture" type="PeaceZone" shape="Cuboid" minZ="-96" maxZ="0">
			    <node X="900" Y="900" />
			    <node X="1600" Y="1200" />
			  </zone>
			</list>
			""");
		final Path geo = Files.createDirectories(game.resolve("data/geodata"));
		final var tile = ByteBuffer.allocate(3 * IRegion.REGION_BLOCKS).order(ByteOrder.LITTLE_ENDIAN);
		for (int block = 0; block < IRegion.REGION_BLOCKS; block++) { tile.put((byte) IBlock.TYPE_FLAT).putShort((short) -32); }
		Files.write(geo.resolve(World.TILE_ZERO_COORD_X + "_" + World.TILE_ZERO_COORD_Y + ".l2j"), tile.array());
		ServerConfig.DATAPACK_ROOT = game.toFile();
		GeoEngineConfig.load();

		final var a = new PhantomNavigationPoint(1008, 1008, -32, 0);
		final var b = new PhantomNavigationPoint(1536, 1008, -32, 0);
		final var goal = new PhantomNavigationPoint(5008, 1008, -32, 0);
		final var ab = new Edge(a, b);
		final var ba = new Edge(b, a);
		final var settings = PhantomTopologyPolicy.productionDefaults();
		final var topologyBackend = new L2jTopologyValidationBackend();
		final var aTopo = new PhantomTopologyPoint(a.x(), a.y(), a.z(), a.instanceId());
		final var bTopo = new PhantomTopologyPoint(b.x(), b.y(), b.z(), b.instanceId());
		final var node = new PhantomTopologyNode("training.area", PhantomTopologyNodeKind.ROUTE_AREA, 0, PhantomTopologyArea.pointRadius(aTopo, 800), null, List.of(), List.of());
		final var first = new PhantomTopologyAnchor("training.a", PhantomTopologyAnchorRole.ROUTE, node.id(), aTopo, null, null, 0, List.of(), List.of());
		final var second = new PhantomTopologyAnchor("training.b", PhantomTopologyAnchorRole.ROUTE, node.id(), bTopo, null, null, 0, List.of(), List.of());
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "training", 1, 1, List.of(node), List.of(first, second), List.of(), topologyBackend, settings), topologyBackend, new PhantomTopologyMetrics());
		final var navigationBackend = new L2jNavigationBackend();
		// As in GameServer boot, initialize GeoData, zones and world collision before timing the requests.
		check(navigationBackend.canWalkLearned(a, b), "Fixture native geometry did not initialize");
		check(!navigationBackend.canWalkLearned(new PhantomNavigationPoint(a.x(), a.y(), a.z() + 16, 0), b), "Wrong floor passed learned GeoData validation");
		final var landing = new PhantomNavigationPoint(a.x(), a.y(), a.z() + 5, 0);
		check(navigationBackend.canWalkLearnedFromStage(landing, b, landing), "Native landing offset prevented learned egress");
		check(!navigationBackend.canWalkLearnedFromStage(landing, b, a), "Native landing exception leaked into a later learned segment");
		final var navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), navigationBackend,
			work -> { work.run(); return true; }, System::nanoTime, new PhantomMetrics());
		check(navigation.start(), "Navigation did not start");
		try
		{
			navigation.startRouteLearning(game, topology);
			await(() -> navigation.routeLearning() != null && navigation.routeLearning().find(ab) != null, "No candidate from real GeoEngine");
			final var memory = navigation.routeLearning();
			check(memory.find(ab).successes() == 0 && memory.find(ba) == null, "Candidate proof or probe pacing incorrect");
			Thread.sleep(650);
			check(memory.find(ba) == null, "Second probe started inside the quota interval");

			final long now = Math.max(1, System.nanoTime());
			final var learnedResult = navigation.submitSoloWalk(new PhantomNavigationRequest(1, a, goal, now, now + 3_000_000_000L, 20_000), true, Set.of(a), Set.of()).immediateResult();
			check(learnedResult != null && learnedResult.route() != null && learnedResult.route().destination().equals(b), "Learned intermediate leg did not influence the normal Solo worker");
			final var fallback = navigation.submitSoloWalk(new PhantomNavigationRequest(2, a, goal, now, now + 3_000_000_000L, 20_000), true, Set.of(a), Set.of(ab)).immediateResult();
			check(fallback != null && fallback.route() != null && !fallback.route().waypoints().contains(b), "Blacklisted leg returned instead of stock corridor");

			final var egress = new PhantomNavigationPoint(b.x() + 528, b.y(), b.z(), b.instanceId());
			final var direct = new Edge(a, egress);
			final var learnedEgress = new Edge(b, egress);
			memory.arrived(ab);
			memory.arrived(learnedEgress);
			final var options = new PhantomTravelStrategy.Options(List.of(), 120, 0, 0, 0, 20, 0, false, "", null, 0, 120_000, Set.of(direct));
			final long travelNow = System.nanoTime();
			final var arrived = navigation.submitTravelPlan(new PhantomNavigationRequest(3, a, egress, travelNow, travelNow + 3_000_000_000L, 20_000), options);
			final var plan = navigation.consumeTravelPlan(arrived.requestId()).orElseThrow();
			check(plan.stages().getFirst().points().equals(List.of(b, egress)), "Ordinary travel did not select the verified directed learned detour");
			final var reverseOptions = new PhantomTravelStrategy.Options(List.of(), 120, 0, 0, 0, 20, 0, false, "", null, 0, 120_000, Set.of(new Edge(egress, a)));
			final var reverseRequest = new PhantomNavigationRequest(4, egress, a, travelNow, travelNow + 3_000_000_000L, 20_000);
			final var reversed = navigation.submitTravelPlan(reverseRequest, reverseOptions);
			final var reversePlan = navigation.consumeTravelPlan(reversed.requestId()).orElseThrow();
			final var stock = navigationBackend.planTravel(reverseRequest, new PhantomNavigationCancellationToken(), navigation.policy(), System::nanoTime, reverseOptions);
			check(reversePlan.equals(stock.candidate()), "Ordinary travel invented reverse learned edges or lost the stock fallback");
			final var noTime = new PhantomTravelStrategy.Options(List.of(), 120, 0, 0, 0, 20, 0, false, "", null, 0, 0, Set.of());
			final var timeRefusal = navigation.submitTravelPlan(new PhantomNavigationRequest(5, a, egress, travelNow, travelNow + 3_000_000_000L, 20_000), noTime).immediateResult();
			check(timeRefusal != null && timeRefusal.route() == null && timeRefusal.proof().kind() == PhantomNavigationResult.LimitKind.UNKNOWN,
				"Unusable travel time became a native search refusal");
		}
		finally
		{
			navigation.beginStop();
			await(navigation::finishStop, "Trainer or writer did not stop");
		}
		try (var reloaded = PhantomRouteLearning.open(learned, PhantomGeoTrainer.fingerprint(geo, () -> false)))
		{
			final var evidence = reloaded.find(ab);
			check(evidence != null && evidence.candidate() && evidence.successes() == 1, "STOP did not flush directed travel evidence");
			check(reloaded.find(ba) == null, "A cancelled future probe produced a reverse route");
		}
		System.out.println("PASS one stock GeoData probe, pacing, Solo reuse, blacklist fallback and STOP/readback");
	}

	private static void await(BooleanSupplier condition, String failure) throws InterruptedException
	{
		final long until = System.nanoTime() + 8_000_000_000L;
		while (!condition.getAsBoolean())
		{
			check(System.nanoTime() < until, failure);
			Thread.sleep(25);
		}
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw new AssertionError(reason); }
	}
}
