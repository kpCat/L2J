package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

/** DB-free production-loader proof for the five independently published closed shards. */
public final class PhantomClosedContentLoadProof
{
	private static final List<String> SHARDS = List.of("high-five-closed-ssq-01.xml", "high-five-closed-devils-isle.xml", "high-five-closed-toi.xml", "high-five-closed-ivory.xml", "high-five-closed-imperial.xml");

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
			return Files.isRegularFile(Path.of(relativeDatapackPath));
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 0)
		{
			throw new IllegalArgumentException("No arguments expected.");
		}
		final Path source = Path.of("data/phantoms/topology");
		final Path isolated = Files.createTempDirectory("phantom-closed-topology-");
		try
		{
			for (String name : SHARDS)
			{
				Files.copy(source.resolve(name), isolated.resolve(name));
			}
			final var snapshot = new PhantomTopologyLoader(isolated, new Backend(), PhantomTopologyPolicy.productionDefaults()).load(1);
			System.out.println("CLOSED TOPOLOGY LOADER: PASS nodes=" + snapshot.nodes().size() + " anchors=" + snapshot.anchors().size() + " edges=" + snapshot.edges().size());
		}
		finally
		{
			for (String name : SHARDS)
			{
				Files.deleteIfExists(isolated.resolve(name));
			}
			Files.deleteIfExists(isolated);
		}
	}
}
