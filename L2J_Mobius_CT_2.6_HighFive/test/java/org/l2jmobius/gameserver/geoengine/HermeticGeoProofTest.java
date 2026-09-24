package org.l2jmobius.gameserver.geoengine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFindingRawAccess;

/** Focused movement seam controls without loading server collision data. */
public final class HermeticGeoProofTest
{
	public static void run() throws Exception
	{
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		final GeoEngine geo = GeoEngine.getInstance();
		final int x = 0;
		final int y = 0;
		final int z = 0;
		final GeoEngine.MovementCollisionOracle clear = new GeoEngine.MovementCollisionOracle()
		{
			@Override
			public boolean doorBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				return false;
			}

			@Override
			public boolean fenceBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				return false;
			}
		};
		if (!geo.canMoveToTarget(x, y, z, x, y, z, 0, clear))
		{
			throw new AssertionError("Clear movement seam rejected its same-point fixture.");
		}
		final GeoEngine.MovementCollisionOracle blockedDoor = new GeoEngine.MovementCollisionOracle()
		{
			@Override
			public boolean doorBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				return true;
			}

			@Override
			public boolean fenceBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				throw new AssertionError("Fence check must short-circuit after a door block.");
			}
		};
		if (geo.canMoveToTarget(x, y, z, x, y, z, 0, blockedDoor))
		{
			throw new AssertionError("Door collision was ignored.");
		}
		final GeoEngine.MovementCollisionOracle blockedFence = new GeoEngine.MovementCollisionOracle()
		{
			@Override
			public boolean doorBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				return false;
			}

			@Override
			public boolean fenceBetween(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int instanceId)
			{
				return true;
			}
		};
		if (geo.canMoveToTarget(x, y, z, x, y, z, 0, blockedFence))
		{
			throw new AssertionError("Fence collision was ignored.");
		}
		PathFindingRawAccess.find(PathFinding.getInstance(), x, y, z, x, y, z, 0, true);
		staticCollisionControls();
		probeDependencyControls();
	}

	private static void probeDependencyControls() throws Exception
	{
		final Path module = Path.of(".").toAbsolutePath().normalize().getParent().getParent();
		final String source = Files.readString(module.resolve("test/java/org/l2jmobius/tests/phantoms/PhantomTravelGeoProbe.java"));
		for (String forbidden : new String[]
		{
			"DoorData.getInstance", "FenceData.getInstance", "import org.l2jmobius.gameserver.data.xml.DoorData", "import org.l2jmobius.gameserver.data.xml.FenceData", "DatabaseFactory", "Hikari", "World.getInstance", "finder.findPath(", "geo.canMoveToTarget("
		})
		{
			if (source.contains(forbidden))
			{
				throw new AssertionError("Hermetic probe references forbidden production path: " + forbidden);
			}
		}
		if (!source.contains("PathFindingRawAccess.find(") || !source.contains("HermeticGeoMovement.canMove("))
		{
			throw new AssertionError("Hermetic probe is not wired to both seams.");
		}
		final String movement = Files.readString(module.resolve("java/org/l2jmobius/gameserver/geoengine/GeoEngine.java"));
		if (!movement.contains("PRODUCTION_MOVEMENT_COLLISION") || !movement.contains("DoorData.getInstance().checkIfDoorsBetween") || !movement.contains("FenceData.getInstance().checkIfFenceBetween"))
		{
			throw new AssertionError("Public movement lost its native collision owner.");
		}
		final String finding = Files.readString(module.resolve("java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java"));
		if (!finding.contains("instanceId, playable, true);") || !finding.contains("instanceId, playable, false);") || !finding.contains("if (!postFilter ||"))
		{
			throw new AssertionError("Raw path must skip only the production post-filter.");
		}
	}

	private static void staticCollisionControls() throws Exception
	{
		final Path door = Files.createTempFile("phantom-door-fixture", ".xml");
		final Path fence = Files.createTempFile("phantom-fence-fixture", ".xml");
		try
		{
			Files.writeString(door, "<list><door id=\"1\" node1=\"0,0\" node2=\"10,0\" node3=\"10,10\" node4=\"0,10\" nodeZ=\"0\" height=\"20\" /></list>");
			Files.writeString(fence, "<list><fence name=\"fixture\" x=\"30\" y=\"0\" z=\"0\" width=\"10\" length=\"10\" height=\"3\" state=\"OPEN\" /></list>");
			final StaticXmlCollisionOracle oracle = StaticXmlCollisionOracle.load(door, fence);
			oracle.inspect(-5, 5, 10, 15, 5, 10, 0);
			if ((oracle.segmentRecords().size() != 1) || (oracle.segmentRecords().getFirst().doorIds().size() != 1) || !oracle.segmentRecords().getFirst().fenceNames().isEmpty())
			{
				throw new AssertionError("Per-segment static collision evidence is incomplete.");
			}
			if (!oracle.doorBetween(-5, 5, 10, 15, 5, 10, 0) || (oracle.doorIntersections() != 1))
			{
				throw new AssertionError("Potential static door must block despite default state.");
			}
			if (!oracle.fenceBetween(20, 0, 0, 40, 0, 0, 0) || (oracle.fenceIntersections() != 1))
			{
				throw new AssertionError("Potential static fence must block despite state.");
			}
			oracle.resetIntersections();
			if (oracle.doorBetween(-5, 5, 1000, 15, 5, 1000, 0) || oracle.fenceBetween(100, 0, 0, 110, 0, 0, 0) || (oracle.doorIntersections() != 0) || (oracle.fenceIntersections() != 0))
			{
				throw new AssertionError("Clear geometry was blocked.");
			}
			Files.writeString(door, "<!DOCTYPE list [<!ENTITY external SYSTEM \"file:///missing\">]><list>&external;</list>");
			try
			{
				StaticXmlCollisionOracle.load(door, fence);
				throw new AssertionError("DTD was accepted.");
			}
			catch (IllegalArgumentException expected)
			{
				// Secure parser rejected the DTD.
			}
		}
		finally
		{
			Files.deleteIfExists(door);
			Files.deleteIfExists(fence);
		}
	}
}
