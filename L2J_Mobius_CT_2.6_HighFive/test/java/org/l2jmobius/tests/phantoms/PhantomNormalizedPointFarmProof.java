package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

/** DB-free proof for the Ruins source-centered, Geo-normalized point contract. */
public final class PhantomNormalizedPointFarmProof
{
	private static final class Backend implements PhantomTopologyValidationBackend
	{
		@Override
		public int mapRegionLocId(int x, int y)
		{
			return 0;
		}

		@Override
		public Optional<NpcFact> npc(int npcId)
		{
			return npcId == 20059 ? Optional.of(new NpcFact(20059, "Monster", true)) : Optional.empty();
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			return npcId == 20059 ? List.of(new SpawnFact(20059, new PhantomTopologyPoint(-33539, 137701, -3479, 0))) : List.of();
		}

		@Override
		public Optional<DoorFact> door(int doorId)
		{
			return Optional.empty();
		}

		@Override
		public DoorState doorState(int doorId)
		{
			return DoorState.MISSING;
		}

		@Override
		public boolean sourceExists(String relativeDatapackPath)
		{
			return Path.of(".").resolve(relativeDatapackPath).toFile().isFile();
		}
	}

	private static int radius(int nativeZ, int geoZ)
	{
		final long delta = Math.abs((long) nativeZ - geoZ);
		if (delta > 4)
		{
			throw new IllegalArgumentException("NO_BOUNDED_NORMALIZED_POINT_FARM");
		}
		return (int) Math.max(1, delta);
	}

	private static void selfTest()
	{
		final PhantomTopologyPoint nativePoint = new PhantomTopologyPoint(-33539, 137701, -3479, 0);
		for (int delta : new int[] {0, 1, 4})
		{
			final int boundedRadius = radius(nativePoint.z(), nativePoint.z() - delta);
			final PhantomTopologyArea area = PhantomTopologyArea.pointRadius(nativePoint, boundedRadius);
			if ((boundedRadius != Math.max(1, delta)) || !area.contains(nativePoint) || !area.contains(new PhantomTopologyPoint(nativePoint.x(), nativePoint.y(), nativePoint.z() - delta, 0)))
			{
				throw new AssertionError("NORMALIZED_POINT_CONTRACT");
			}
		}
		try
		{
			radius(nativePoint.z(), nativePoint.z() - 5);
			throw new AssertionError("DELTA_FIVE_ACCEPTED");
		}
		catch (IllegalArgumentException expected)
		{
			// Delta five is outside this Goal's publication policy.
		}
		if (PhantomTopologyArea.pointRadius(nativePoint, 1).contains(new PhantomTopologyPoint(nativePoint.x(), nativePoint.y(), nativePoint.z() - 2, 0)))
		{
			throw new AssertionError("OUTSIDE_ANCHOR_ACCEPTED");
		}
		System.out.println("NORMALIZED_POINT_SELF_TEST=GREEN");
	}

	public static void main(String[] args) throws Exception
	{
		if ((args.length == 1) && args[0].equals("--self-test"))
		{
			selfTest();
			return;
		}
		if ((args.length == 3) && args[0].equals("--route-geo"))
		{
			ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
			GeoEngineConfig.load();
			final GeoEngine geo = GeoEngine.getInstance();
			final List<String> source = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
			if (!source.getFirst().equals("point_id\tsource_path\tsource_group\tnpc_id\tx\ty\tnative_z\tprojection\tcorridor_distance\tnearest_endpoint_distance"))
			{
				throw new IllegalArgumentException("Unexpected factual waypoint header");
			}
			final List<String> output = new ArrayList<>();
			output.add(source.getFirst() + "\tgeo_z\tgeo_height_second\tgeo_height_restored\tdelta_z\tgeo_status");
			for (int index = 1; index < source.size(); index++)
			{
				final String[] row = source.get(index).split("\t", -1);
				if (row.length != 10)
				{
					throw new IllegalArgumentException("Malformed factual waypoint");
				}
				final int x = Integer.parseInt(row[4]);
				final int y = Integer.parseInt(row[5]);
				final int nativeZ = Integer.parseInt(row[6]);
				final boolean hasGeo = geo.hasGeo(x, y);
				final int h1 = geo.getHeight(x, y, nativeZ);
				final int h2 = geo.getHeight(x, y, nativeZ);
				final int h3 = geo.getHeight(x, y, h1);
				final long delta = Math.abs((long) nativeZ - h1);
				final String status = hasGeo && (h1 == h2) && (h1 == h3) && (delta <= 16) ? "ROUTE_GEO_VALID" : "ROUTE_GEO_REJECTED";
				output.add(source.get(index) + "\t" + h1 + "\t" + h2 + "\t" + h3 + "\t" + delta + "\t" + status);
			}
			Files.writeString(Path.of(args[2]), String.join("\n", output) + "\n", StandardCharsets.UTF_8);
			System.out.println("ROUTE_GEO_POINTS=" + (output.size() - 1) + " VALID=" + output.stream().filter(line -> line.endsWith("ROUTE_GEO_VALID")).count());
			return;
		}
		final boolean geoOnly = (args.length == 5) && args[0].equals("--geo");
		if (!geoOnly && ((args.length != 8) || !args[0].equals("--prove")))
		{
			throw new IllegalArgumentException("Usage: --self-test | --geo x y nativeZ npcId | --prove x y nativeZ npcId nodeId anchorId active-node-directory");
		}
		final int x = Integer.parseInt(args[1]);
		final int y = Integer.parseInt(args[2]);
		final int nativeZ = Integer.parseInt(args[3]);
		final int npcId = Integer.parseInt(args[4]);
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		final GeoEngine geo = GeoEngine.getInstance();
		final int h1 = geo.getHeight(x, y, nativeZ);
		final int h2 = geo.getHeight(x, y, nativeZ);
		final int h3 = geo.getHeight(x, y, h1);
		final int boundedRadius = radius(nativeZ, h1);
		if (!geo.hasGeo(x, y) || (h1 != h2) || (h1 != h3))
		{
			throw new AssertionError("UNSTABLE_POINT_GEO");
		}
		System.out.println("POINT_GEO npc=" + npcId + " native=" + nativeZ + " hasGeo=true h1=" + h1 + " h2=" + h2 + " h3=" + h3 + " delta=" + Math.abs(nativeZ - h1) + " radius=" + boundedRadius);
		if (geoOnly)
		{
			return;
		}
		final Backend backend = new Backend();
		final var snapshot = new PhantomTopologyLoader(Path.of(args[7]), backend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final var query = new PhantomTopologyQuery(snapshot, backend, new PhantomTopologyMetrics());
		final PhantomTopologyPoint nativePoint = new PhantomTopologyPoint(x, y, nativeZ, 0);
		final var node = query.mostSpecificNode(nativePoint).orElseThrow();
		final var anchor = query.findAnchor(args[6]).orElseThrow();
		final PhantomTopologyPoint geoPoint = new PhantomTopologyPoint(x, y, h1, 0);
		if (!node.id().equals(args[5]) || (node.kind() != PhantomTopologyNodeKind.FARMING_AREA) || (node.area().form() != PhantomTopologyArea.Form.POINT_RADIUS) || (node.area().radius() != boundedRadius) || !node.area().representativePoint().equals(nativePoint) || !node.area().contains(nativePoint) || !node.area().contains(geoPoint) || (anchor.role() != PhantomTopologyAnchorRole.FARMING) || !anchor.nodeId().equals(node.id()) || !anchor.point().equals(geoPoint) || (anchor.validationTolerance() != 0))
		{
			throw new AssertionError("NORMALIZED_POINT_NODE_MAPPING_AMBIGUOUS");
		}
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0).orElseThrow(() -> new AssertionError("INVALID_COMMITTED_ANCHOR"));
		if ((canonical.x() != x) || (canonical.y() != y) || (canonical.z() != h1))
		{
			throw new AssertionError("COMMITTED_ANCHOR_DRIFT");
		}
		System.out.println("POINT_MAPPING node=" + node.id() + " anchor=" + anchor.id() + " canonical=" + canonical.x() + "," + canonical.y() + "," + canonical.z() + " activeNodes=" + snapshot.nodes().size());
	}
}
