package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
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
		final Path root = Path.of(args[0]);
		roundtrip(root);
		capacityRoundtrip(root.resolve("capacity"));
		probeCadenceAndPriority();
		directedGraphAndProgress(root.resolve("routing"));
		journeyOutcomes(root.resolve("journeys"));
		if (args.length == 2 && args[1].equals("--geo-fixture")) { geoFixture(); }
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
		}
		finally
		{
			navigation.beginStop();
			await(navigation::finishStop, "Trainer or writer did not stop");
		}
		try (var reloaded = PhantomRouteLearning.open(learned, PhantomGeoTrainer.fingerprint(geo, () -> false)))
		{
			final var evidence = reloaded.find(ab);
			check(evidence != null && evidence.candidate() && evidence.successes() == 0, "STOP did not flush GeoData candidate");
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
