package org.l2jmobius.gameserver.phantoms.navigation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.geodata.IBlock;
import org.l2jmobius.gameserver.geoengine.geodata.IRegion;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
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
		roundtrip(Path.of(args[0]));
		if (args.length == 2 && args[1].equals("--geo-fixture")) { geoFixture(); }
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
			check(memory.hints(a, b, true, Set.of(ab, ac), 1000).equals(List.of(ad)), "Exact episode blacklist hid the wrong alternative");
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
