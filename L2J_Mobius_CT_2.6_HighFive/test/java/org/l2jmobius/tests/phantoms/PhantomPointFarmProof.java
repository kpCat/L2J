package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

/** DB-free exact point Geo and active topology query proof. */
public final class PhantomPointFarmProof
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
			return Optional.empty();
		}

		@Override
		public List<SpawnFact> spawns(int npcId, int maximumResults)
		{
			return List.of();
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

	public static void main(String[] args) throws Exception
	{
		if ((args.length != 4) && (args.length != 6))
		{
			throw new IllegalArgumentException("Usage: x y z npcId [nodeId active-node-directory]");
		}
		final int x = Integer.parseInt(args[0]);
		final int y = Integer.parseInt(args[1]);
		final int z = Integer.parseInt(args[2]);
		final int npcId = Integer.parseInt(args[3]);
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		final GeoEngine geo = GeoEngine.getInstance();
		final int first = geo.getHeight(x, y, z);
		final int second = geo.getHeight(x, y, z);
		System.out.println("POINT_GEO npc=" + npcId + " source=" + z + " hasGeo=" + geo.hasGeo(x, y) + " first=" + first + " second=" + second);
		if (!geo.hasGeo(x, y) || (first != z) || (second != z))
		{
			throw new AssertionError("NO_EXACT_STABLE_POINT_FARM");
		}
		if (args.length == 6)
		{
			final Backend backend = new Backend();
			final var snapshot = new PhantomTopologyLoader(Path.of(args[5]), backend, PhantomTopologyPolicy.productionDefaults()).load(1);
			final var query = new PhantomTopologyQuery(snapshot, backend, new PhantomTopologyMetrics());
			final PhantomTopologyPoint point = new PhantomTopologyPoint(x, y, z, 0);
			final PhantomTopologyNode node = query.mostSpecificNode(point).orElseThrow();
			if (!node.id().equals(args[4]) || (node.kind() != PhantomTopologyNodeKind.FARMING_AREA) || (node.area().form() != PhantomTopologyArea.Form.POINT_RADIUS) || (node.area().radius() != 1))
			{
				throw new AssertionError("POINT_FARM_NODE_AMBIGUOUS");
			}
			System.out.println("POINT_MAPPING node=" + node.id() + " activeNodes=" + snapshot.nodes().size());
		}
	}
}
