package org.l2jmobius.gameserver.geoengine.pathfinding;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;

/** Test-only capture of the existing NodeBuffer A* parent chain. */
public final class PhantomOversizedRawPathProof
{
	private record Point(int x, int y, int z)
	{
	}

	private record Node(int geoX, int geoY, int x, int y, int z)
	{
	}

	private static final Point RUINS = new Point(-19120, 136816, -3752);
	private static final Point SPLIT = new Point(-23089, 130525, -3664);
	private static final Point FARM = new Point(-33539, 137701, -3480);

	private static List<Node> capture(GeoEngine geo, Point from, Point to)
	{
		if (!geo.hasGeo(from.x(), from.y()) || !geo.hasGeo(to.x(), to.y()))
		{
			throw new IllegalStateException("MISSING_GEODATA");
		}
		final int gx = GeoEngine.getGeoX(from.x());
		final int gy = GeoEngine.getGeoY(from.y());
		final int tx = GeoEngine.getGeoX(to.x());
		final int ty = GeoEngine.getGeoY(to.y());
		final int required = 64 + (2 * Math.max(Math.abs(gx - tx), Math.abs(gy - ty)));
		if ((required <= 500) || (required > 2048))
		{
			throw new IllegalStateException("OVERSIZED_ASTAR_REQUIRED_BUFFER_TOO_LARGE required=" + required);
		}
		System.out.println("OVERSIZED_ATTEMPT from=" + from + " to=" + to + " required=" + required);
		final NodeBuffer buffer = new NodeBuffer(required);
		if (!buffer.lock())
		{
			throw new IllegalStateException("NODE_BUFFER_LOCK_FAILED");
		}
		try
		{
			final GeoNode result = buffer.findPath(gx, gy, geo.getHeight(from.x(), from.y(), from.z()), tx, ty, geo.getHeight(to.x(), to.y(), to.z()));
			if (result == null)
			{
				return null;
			}
			final List<Node> nodes = new ArrayList<>();
			for (GeoNode current = result; current != null; current = current.getParent())
			{
				final GeoLocation location = current.getLocation();
				nodes.add(new Node(location.getNodeX(), location.getNodeY(), location.getX(), location.getY(), location.getZ()));
			}
			Collections.reverse(nodes);
			return nodes;
		}
		finally
		{
			buffer.free();
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 1)
		{
			throw new IllegalArgumentException("Usage: output-tsv");
		}
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		List<Node> nodes = capture(geo, RUINS, FARM);
		int attempts = 1;
		if (nodes == null)
		{
			final List<Node> first = capture(geo, RUINS, SPLIT);
			attempts++;
			if (first != null)
			{
				final List<Node> second = capture(geo, SPLIT, FARM);
				attempts++;
				if (second != null)
				{
				first.addAll(second.subList(1, second.size()));
					nodes = first;
				}
			}
		}
		if (nodes == null)
		{
			throw new IllegalStateException("OVERSIZED_ASTAR_NO_PATH attempts=" + attempts);
		}
		final List<String> lines = new ArrayList<>();
		lines.add("ordinal\tgeo_x\tgeo_y\tworld_x\tworld_y\tgeo_z");
		for (int i = 0; i < nodes.size(); i++)
		{
			final Node node = nodes.get(i);
			lines.add(i + "\t" + node.geoX() + "\t" + node.geoY() + "\t" + node.x() + "\t" + node.y() + "\t" + node.z());
		}
		final Path output = Path.of(args[0]);
		Files.createDirectories(output.getParent());
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		System.out.println("OVERSIZED_ASTAR_PATH nodes=" + nodes.size() + " attempts=" + attempts);
	}
}
