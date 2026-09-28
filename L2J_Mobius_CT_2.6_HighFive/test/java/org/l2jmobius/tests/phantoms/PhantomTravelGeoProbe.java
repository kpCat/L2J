package org.l2jmobius.tests.phantoms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.HermeticGeoMovement;
import org.l2jmobius.gameserver.geoengine.StaticXmlCollisionOracle;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFindingRawAccess;

/** Hermetic native GeoEngine connector proof with static XML collision closure. */
public final class PhantomTravelGeoProbe
{
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 2) && ((args.length != 3) || !"--heights".equals(args[2])))
		{
			throw new IllegalArgumentException("Usage: candidate-tsv proof-tsv [--heights]");
		}
		ServerConfig.DATAPACK_ROOT = new File(".").getCanonicalFile();
		GeoEngineConfig.load();
		final int maximumBuffer = PhantomGeoValidationRules.maximumBuffer(GeoEngineConfig.PATHFIND_BUFFERS);
		if (GeoEngineConfig.PATHFINDING != 2)
		{
			throw new IllegalStateException("PathFinding=2 required");
		}
		final GeoEngine geo = GeoEngine.getInstance();
		if (args.length == 3)
		{
			final List<String> points = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
			if (!points.getFirst().equals("point_id\tx\ty\tseed_z"))
			{
				throw new IllegalArgumentException("Unexpected height candidate header");
			}
			final List<String> heights = new ArrayList<>();
			heights.add("point_id\tx\ty\tz\tstable");
			for (int i = 1; i < points.size(); i++)
			{
				final String[] row = points.get(i).split("\t", -1);
				if (row.length != 4)
				{
					throw new IllegalArgumentException("Malformed height candidate: " + i);
				}
				final int x = Integer.parseInt(row[1]);
				final int y = Integer.parseInt(row[2]);
				final int seedZ = Integer.parseInt(row[3]);
				final int z = geo.getHeight(x, y, seedZ);
				final boolean stable = geo.hasGeo(x, y) && (z == geo.getHeight(x, y, z));
				heights.add(String.join("\t", row[0], row[1], row[2], Integer.toString(z), Boolean.toString(stable)));
			}
			Files.writeString(Path.of(args[1]), String.join("\n", heights) + "\n", StandardCharsets.UTF_8);
			System.out.println("TRAVEL_GEO_HEIGHTS candidates=" + (points.size() - 1));
			return;
		}
		final PathFinding finder = PathFinding.getInstance();
		final StaticXmlCollisionOracle collision = StaticXmlCollisionOracle.load(ServerConfig.DATAPACK_ROOT.toPath().resolve("data/Doors.xml"), ServerConfig.DATAPACK_ROOT.toPath().resolve("data/FenceData.xml"));
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
				collision.inspect(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId());
				return HermeticGeoMovement.canMove(geo, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId(), collision);
			}

			@Override
			public List<PhantomGeoValidationRules.Point> path(PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
			{
				final List<GeoLocation> found = PathFindingRawAccess.find(finder, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId(), true);
				return found == null ? null : found.stream().map(p -> new PhantomGeoValidationRules.Point(p.getX(), p.getY(), p.getZ(), from.instanceId())).toList();
			}
		};
		final List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
		if (!lines.getFirst().equals("connector_id\tfrom_x\tfrom_y\tfrom_z\tfrom_instance\tto_x\tto_y\tto_z\tto_instance"))
		{
			throw new IllegalArgumentException("Unexpected connector candidate header");
		}
		final List<String> output = new ArrayList<>();
		output.add("connector_id\tvalidation_status\tpath_length\tpath_segments\trequired_buffer\tmax_pathfind_buffer\tdoor_intersections\tfence_intersections\tdoor_ids\tfence_names\tcollision_proof");
		for (int i = 1; i < lines.size(); i++)
		{
			final String[] row = lines.get(i).split("\t", -1);
			if (row.length != 9)
			{
				throw new IllegalArgumentException("Malformed connector candidate: " + i);
			}
			final PhantomGeoValidationRules.Point from = new PhantomGeoValidationRules.Point(Integer.parseInt(row[1]), Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]));
			final PhantomGeoValidationRules.Point to = new PhantomGeoValidationRules.Point(Integer.parseInt(row[5]), Integer.parseInt(row[6]), Integer.parseInt(row[7]), Integer.parseInt(row[8]));
			collision.resetIntersections();
			final PhantomGeoValidationRules.RouteProof result = PhantomGeoValidationRules.route(from, to, probe, maximumBuffer);
			final String collisionProof = collision.doorIntersections() > 0 ? "STATIC_DOOR_INTERSECTION_UNRESOLVED" : collision.fenceIntersections() > 0 ? "STATIC_FENCE_INTERSECTION_UNRESOLVED" : "STATIC_XML_CLEAR";
			output.add(String.join("\t", row[0], "STATIC_XML_CLEAR".equals(collisionProof) ? result.reason() : collisionProof, "STATIC_XML_CLEAR".equals(collisionProof) ? Long.toString(result.length()) : "0", "STATIC_XML_CLEAR".equals(collisionProof) ? Integer.toString(result.segments()) : "0", Integer.toString(PhantomGeoValidationRules.requiredBuffer(from, to)), Integer.toString(maximumBuffer), Integer.toString(collision.doorIntersections()), Integer.toString(collision.fenceIntersections()), collision.doorIds().toString(), collision.fenceNames().toString(), collisionProof));
		}
		Files.writeString(Path.of(args[1]), String.join("\n", output) + "\n", StandardCharsets.UTF_8);
		System.out.println("TRAVEL_GEO_PROBE candidates=" + (lines.size() - 1) + " proven=" + output.stream().filter(s -> s.contains("VALID_DIRECT") || s.contains("VALID_PATH")).count() + " PathFindBuffers=" + GeoEngineConfig.PATHFIND_BUFFERS + " max=" + maximumBuffer);
	}
}
