package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.FenceData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;

/** DB-free native GeoEngine connector probe for LIVE-002-D1. */
public final class PhantomTravelGeoProbe
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("Usage: candidate-tsv proof-tsv");
		}
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		final int maximumBuffer = PhantomGeoValidationRules.maximumBuffer(GeoEngineConfig.PATHFIND_BUFFERS);
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		final PathFinding finder = PathFinding.getInstance();
		DoorData.getInstance();
		FenceData.getInstance();
		final PhantomGeoValidationRules.Probe probe = new PhantomGeoValidationRules.Probe()
		{
			@Override
			public boolean hasGeo(int x, int y)
			{
				return geo.hasGeo(x, y);
			}

			@Override
			public int height(int x, int y, int z)
			{
				return geo.getHeight(x, y, z);
			}

			@Override
			public boolean canMove(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
			{
				return geo.canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId());
			}

			@Override
			public List<PhantomGeoValidationRules.Point> path(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
			{
				final List<GeoLocation> found = finder.findPath(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId(), true);
				return found == null ? null : found.stream().map(p -> new PhantomGeoValidationRules.Point(p.getX(), p.getY(), p.getZ(), from.instanceId())).toList();
			}
		};
		final List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
		if (!lines.getFirst().equals("connector_id\tfrom_x\tfrom_y\tfrom_z\tfrom_instance\tto_x\tto_y\tto_z\tto_instance"))
		{
			throw new IllegalArgumentException("Unexpected connector candidate header");
		}
		final List<String> output = new ArrayList<>();
		output.add("connector_id\tvalidation_status\tpath_length\tpath_segments\trequired_buffer\tmax_pathfind_buffer");
		for (int i = 1; i < lines.size(); i++)
		{
			final String[] row = lines.get(i).split("\t", -1);
			if (row.length != 9)
			{
				throw new IllegalArgumentException("Malformed connector candidate: " + i);
			}
			final PhantomGeoValidationRules.Point from = new PhantomGeoValidationRules.Point(Integer.parseInt(row[1]), Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]));
			final PhantomGeoValidationRules.Point to = new PhantomGeoValidationRules.Point(Integer.parseInt(row[5]), Integer.parseInt(row[6]), Integer.parseInt(row[7]), Integer.parseInt(row[8]));
			final PhantomGeoValidationRules.RouteProof result = PhantomGeoValidationRules.route(from, to, probe, maximumBuffer);
			output.add(String.join("\t", row[0], result.reason(), Long.toString(result.length()), Integer.toString(result.segments()), Integer.toString(PhantomGeoValidationRules.requiredBuffer(from, to)), Integer.toString(maximumBuffer)));
		}
		Files.writeString(Path.of(args[1]), String.join("\n", output) + "\n", StandardCharsets.UTF_8);
		System.out.println("TRAVEL_GEO_PROBE candidates=" + (lines.size() - 1) + " proven=" + output.stream().filter(s -> s.contains("VALID_DIRECT") || s.contains("VALID_PATH")).count() + " PathFindBuffers=" + GeoEngineConfig.PATHFIND_BUFFERS + " max=" + maximumBuffer);
	}
}
