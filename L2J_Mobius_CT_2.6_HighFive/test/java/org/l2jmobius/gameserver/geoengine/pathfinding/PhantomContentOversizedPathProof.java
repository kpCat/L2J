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

/** One proof-only oversized native Geo A* attempt; never changes production buffers. */
public final class PhantomContentOversizedPathProof
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 7)
		{
			throw new IllegalArgumentException("Usage: fromX fromY fromZ toX toY toZ output-tsv");
		}
		final int fromX = Integer.parseInt(args[0]);
		final int fromY = Integer.parseInt(args[1]);
		final int fromZ = Integer.parseInt(args[2]);
		final int toX = Integer.parseInt(args[3]);
		final int toY = Integer.parseInt(args[4]);
		final int toZ = Integer.parseInt(args[5]);
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		if (!geo.hasGeo(fromX, fromY) || !geo.hasGeo(toX, toY))
		{
			throw new IllegalStateException("MISSING_GEODATA");
		}
		final int sourceGX = GeoEngine.getGeoX(fromX);
		final int sourceGY = GeoEngine.getGeoY(fromY);
		final int targetGX = GeoEngine.getGeoX(toX);
		final int targetGY = GeoEngine.getGeoY(toY);
		final int required = 64 + (2 * Math.max(Math.abs(sourceGX - targetGX), Math.abs(sourceGY - targetGY)));
		if ((required <= 500) || (required > 2048))
		{
			throw new IllegalStateException("OVERSIZED_ASTAR_REQUIRED_BUFFER_OUTSIDE_PROOF_BOUND required=" + required);
		}
		final NodeBuffer buffer = new NodeBuffer(required);
		if (!buffer.lock())
		{
			throw new IllegalStateException("NODE_BUFFER_LOCK_FAILED");
		}
		final List<GeoLocation> nodes = new ArrayList<>();
		try
		{
			final GeoNode result = buffer.findPath(sourceGX, sourceGY, geo.getHeight(fromX, fromY, fromZ), targetGX, targetGY, geo.getHeight(toX, toY, toZ));
			if (result == null)
			{
				System.out.println("OVERSIZED_CONTENT_NO_PATH required=" + required);
				return;
			}
			for (GeoNode current = result; current != null; current = current.getParent())
			{
				nodes.add(current.getLocation());
			}
			Collections.reverse(nodes);
		}
		finally
		{
			buffer.free();
		}
		final StringBuilder output = new StringBuilder("ordinal\tgeo_x\tgeo_y\tworld_x\tworld_y\tgeo_z\n");
		for (int index = 0; index < nodes.size(); index++)
		{
			final GeoLocation point = nodes.get(index);
			output.append(index).append('\t').append(point.getNodeX()).append('\t').append(point.getNodeY()).append('\t').append(point.getX()).append('\t').append(point.getY()).append('\t').append(point.getZ()).append('\n');
		}
		Files.writeString(Path.of(args[6]), output.toString(), StandardCharsets.UTF_8);
		System.out.println("OVERSIZED_CONTENT_PATH nodes=" + nodes.size() + " required=" + required);
	}
}
